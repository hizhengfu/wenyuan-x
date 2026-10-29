package io.hizhengfu.cloud.collection.rfid.diagnostic

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.rfid.dao.RfidWorkstationDeviceBindings
import io.hizhengfu.cloud.collection.rfid.dao.RfidWorkstations
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidWorkstationCapability
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidWorkstationDevicePurpose
import io.hizhengfu.cloud.collection.rfid.service.UNSUPPORTED_RFID_WORKSTATION_CAPABILITIES
import io.hizhengfu.cloud.collection.rfid.service.expectedRoles
import io.hizhengfu.cloud.collection.rfid.service.requiredPurpose
import io.hizhengfu.cloud.collection.rfid.service.workstationCapabilities
import io.hizhengfu.cloud.common.domain.EnumContentStatus
import io.hizhengfu.cloud.museum.device.common.museumUtcNow
import io.hizhengfu.cloud.museum.device.dao.MuseumDeviceConnections
import io.hizhengfu.cloud.museum.device.dao.MuseumIngestConnectors
import io.hizhengfu.cloud.museum.device.dao.MuseumRfidDeviceProfiles
import io.hizhengfu.cloud.museum.device.domain.EnumIngestConnectorProbeStatus
import io.hizhengfu.cloud.museum.device.domain.COLLECTION_RFID_CALLBACK_CONNECTOR_CODE
import io.hizhengfu.cloud.museum.device.domain.EnumRfidConnectionState
import io.hizhengfu.cloud.museum.device.domain.EnumRfidDeviceRole
import io.hizhengfu.cloud.museum.device.service.MuseumIngestPushTargetService
import io.hizhengfu.cloud.sys.diagnostic.domain.DeploymentCheckDescriptor
import io.hizhengfu.cloud.sys.diagnostic.domain.DeploymentCheckOutcome
import io.hizhengfu.cloud.sys.diagnostic.domain.DeploymentRemediation
import io.hizhengfu.cloud.sys.diagnostic.domain.DeploymentSafeFact
import io.hizhengfu.cloud.sys.diagnostic.domain.EnumDeploymentCheckSeverity
import io.hizhengfu.cloud.sys.diagnostic.domain.EnumDeploymentCheckState
import io.hizhengfu.cloud.sys.diagnostic.domain.EnumDeploymentRemediationType
import io.hizhengfu.cloud.sys.diagnostic.service.DeploymentDiagnosticCheck
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * RFID 业务配置闭环检查。仅返回数量和布尔结论，不返回地址、密钥、设备 ID 或硬件序列号。
 */
// 检查仅查询；SQLite 建连后不能切换 readOnly，保留事务以覆盖实际后台扫描。
@Component
@Transactional
class RfidIntegrationConfigurationDiagnosticCheck(
    private val pushTargetService: MuseumIngestPushTargetService,
    @param:Value("\${collection.rfid.readiness.enabled:false}")
    private val readinessEnabled: Boolean,
    @param:Value("\${collection.rfid.readiness.fixed-devices-required:false}")
    private val fixedDevicesRequired: Boolean,
    @param:Value("\${collection.rfid.readiness.callback-probe-fresh-seconds:300}")
    private val callbackProbeFreshSeconds: Long = 300,
) : DeploymentDiagnosticCheck {
    override val descriptor = DeploymentCheckDescriptor(
        id = "collection.rfid.integration-configuration",
        category = "CONFIGURATION",
        feature = "藏品 RFID",
        name = "RFID 接入配置",
        severity = EnumDeploymentCheckSeverity.CRITICAL,
        timeoutMillis = 10_000,
        order = 116,
    )

    override fun check(): DeploymentCheckOutcome {
        if (!readinessEnabled) return disabledOutcome()

        val callbackConfigured = runCatching {
            pushTargetService.find(COLLECTION_RFID_CALLBACK_CONNECTOR_CODE) != null
        }.getOrDefault(false)
        val callbackRow = MuseumIngestConnectors.select(
            MuseumIngestConnectors.probeStatus,
            MuseumIngestConnectors.probeCompletedAt,
            MuseumIngestConnectors.updateTime,
        ).where {
            MuseumIngestConnectors.code eq COLLECTION_RFID_CALLBACK_CONNECTOR_CODE
        }.limit(1).firstOrNull()
        val callbackProbeCompletedAt = callbackRow?.get(MuseumIngestConnectors.probeCompletedAt)
        val callbackConfigUpdatedAt = callbackRow?.get(MuseumIngestConnectors.updateTime)
        val callbackAuthenticated = callbackConfigured &&
            callbackRow?.get(MuseumIngestConnectors.probeStatus) == EnumIngestConnectorProbeStatus.SUCCESS &&
            callbackProbeCompletedAt != null &&
            callbackProbeCompletedAt >= museumUtcNow().minusSeconds(callbackProbeFreshSeconds.coerceAtLeast(30)) &&
            (callbackConfigUpdatedAt == null || callbackProbeCompletedAt >= callbackConfigUpdatedAt)

        val profiles = MuseumRfidDeviceProfiles.select(
            MuseumRfidDeviceProfiles.orgId,
            MuseumRfidDeviceProfiles.deviceId,
            MuseumRfidDeviceProfiles.role,
            MuseumRfidDeviceProfiles.rolesJson,
            MuseumRfidDeviceProfiles.hardwareSerial,
            MuseumRfidDeviceProfiles.lastVerifiedAt,
        ).where {
            (MuseumRfidDeviceProfiles.enabled eq true) and
                (MuseumRfidDeviceProfiles.status eq EnumContentStatus.NORMAL)
        }.toList()
        val profileByDeviceId = profiles.associate { row ->
            row[MuseumRfidDeviceProfiles.deviceId] to RfidReadyProfile(
                orgId = row[MuseumRfidDeviceProfiles.orgId],
                roles = profileRoles(
                    row[MuseumRfidDeviceProfiles.role],
                    row[MuseumRfidDeviceProfiles.rolesJson],
                ),
            )
        }
        val fixedProfiles = profiles.filter { row ->
            profileRoles(
                row[MuseumRfidDeviceProfiles.role],
                row[MuseumRfidDeviceProfiles.rolesJson],
            ).any(FIXED_DEVICE_ROLES::contains)
        }
        val fixedMissingSerialCount = fixedProfiles.count {
            it[MuseumRfidDeviceProfiles.hardwareSerial].isNullOrBlank()
        }
        val verifiedFixedProfileCount = fixedProfiles.count {
            it[MuseumRfidDeviceProfiles.lastVerifiedAt] != null
        }

        val workstationRows = RfidWorkstations.select(
            RfidWorkstations.id,
            RfidWorkstations.orgId,
            RfidWorkstations.enabled,
            RfidWorkstations.capabilitiesJson,
            RfidWorkstations.antennaDirectionsJson,
        ).toList()
        val enabledWorkstations = workstationRows.filter {
            it[RfidWorkstations.enabled] && workstationCapabilities(it) != setOf(EnumRfidWorkstationCapability.PASSAGE_MONITOR)
        }
        val workstationRequired = profiles.isEmpty() || profileByDeviceId.values.any { profile ->
            profile.roles.any { it !in setOf(EnumRfidDeviceRole.WAREHOUSE_GATE, EnumRfidDeviceRole.MUSEUM_GATE) }
        }
        val enabledWorkstationIds = enabledWorkstations.mapTo(linkedSetOf()) { it[RfidWorkstations.id].value }
        val bindingRows = if (enabledWorkstationIds.isEmpty()) {
            emptyList()
        } else {
            RfidWorkstationDeviceBindings.select(
                RfidWorkstationDeviceBindings.workstationId,
                RfidWorkstationDeviceBindings.deviceId,
                RfidWorkstationDeviceBindings.purpose,
                RfidWorkstationDeviceBindings.isDefault,
            ).where {
                (RfidWorkstationDeviceBindings.enabled eq true) and
                    (RfidWorkstationDeviceBindings.workstationId inList enabledWorkstationIds)
            }.toList().filter { it[RfidWorkstationDeviceBindings.purpose] != EnumRfidWorkstationDevicePurpose.PASSAGE_READ }
        }
        val bindingsByWorkstation = bindingRows.groupBy { it[RfidWorkstationDeviceBindings.workstationId] }
        var missingRequiredPurposeCount = 0
        var invalidBindingCount = 0
        var duplicateDefaultPurposeCount = 0
        val usableBindingCount = enabledWorkstations.sumOf { station ->
            val stationId = station[RfidWorkstations.id].value
            val stationOrgId = station[RfidWorkstations.orgId]
            val stationBindings = bindingsByWorkstation[stationId].orEmpty()
            val capabilities = workstationCapabilities(station)
            val validBindings = stationBindings.filter { binding ->
                val profile = profileByDeviceId[binding[RfidWorkstationDeviceBindings.deviceId]]
                val purpose = binding[RfidWorkstationDeviceBindings.purpose]
                profile != null && profile.orgId == stationOrgId &&
                    profile.roles.any { it.name in expectedRoles(purpose) }
            }
            invalidBindingCount += stationBindings.size - validBindings.size
            duplicateDefaultPurposeCount += stationBindings
                .filter { it[RfidWorkstationDeviceBindings.isDefault] }
                .groupBy { it[RfidWorkstationDeviceBindings.purpose] }
                .count { (_, values) -> values.size > 1 }
            if (capabilities.isEmpty()) missingRequiredPurposeCount++
            capabilities.filter { it != EnumRfidWorkstationCapability.PASSAGE_MONITOR }.forEach { capability ->
                val purpose = requiredPurpose(capability)
                if (validBindings.none { it[RfidWorkstationDeviceBindings.purpose] == purpose }) {
                    missingRequiredPurposeCount++
                }
            }
            validBindings.size
        }
        val unsupportedCapabilityWorkstationCount = enabledWorkstations.count { station ->
            workstationCapabilities(station).any(UNSUPPORTED_RFID_WORKSTATION_CAPABILITIES::contains)
        }

        val problems = buildList {
            if (profiles.isEmpty()) add("没有启用且有效的 RFID 设备档案")
            if (fixedDevicesRequired && fixedProfiles.isEmpty()) add("没有启用的固定式 RFID 设备档案")
            if (fixedDevicesRequired && !callbackConfigured) add("固定设备业务回调通道未正确配置")
            if (fixedDevicesRequired && callbackConfigured && !callbackAuthenticated) {
                add("固定设备业务回调尚未通过 museum-ingest 正向鉴权自检")
            }
            if (fixedMissingSerialCount > 0) add("有固定设备未登记硬件序列号")
            if (fixedProfiles.isNotEmpty() && verifiedFixedProfileCount < fixedProfiles.size) {
                add("有固定设备尚未完成现场配置核验")
            }
            if (workstationRequired && enabledWorkstationIds.isEmpty()) add("没有启用的 RFID 工作站（借还/发标等作业使用）")
            if (workstationRequired && usableBindingCount == 0) add("没有启用工作站到有效 RFID 作业设备的用途绑定")
            if (missingRequiredPurposeCount > 0) add("启用工作站的能力缺少匹配用途设备")
            if (invalidBindingCount > 0) add("工作站绑定存在停用、跨馆或角色不匹配设备")
            if (duplicateDefaultPurposeCount > 0) add("同一工作站用途配置了多个默认设备")
            if (unsupportedCapabilityWorkstationCount > 0) add("启用工作站包含尚未实现的打印能力")
        }
        val facts = listOf(
            DeploymentSafeFact("readinessEnabled", readinessEnabled.toString()),
            DeploymentSafeFact("fixedDevicesRequired", fixedDevicesRequired.toString()),
            DeploymentSafeFact("callbackConfigured", callbackConfigured.toString()),
            DeploymentSafeFact("callbackAuthenticated", callbackAuthenticated.toString()),
            DeploymentSafeFact("enabledProfileCount", profiles.size.toString()),
            DeploymentSafeFact("fixedProfileCount", fixedProfiles.size.toString()),
            DeploymentSafeFact("fixedProfileMissingSerialCount", fixedMissingSerialCount.toString()),
            DeploymentSafeFact("verifiedFixedProfileCount", verifiedFixedProfileCount.toString()),
            DeploymentSafeFact("workstationCount", workstationRows.size.toString()),
            DeploymentSafeFact("enabledWorkstationCount", enabledWorkstationIds.size.toString()),
            DeploymentSafeFact("usableBindingCount", usableBindingCount.toString()),
            DeploymentSafeFact("missingRequiredPurposeCount", missingRequiredPurposeCount.toString()),
            DeploymentSafeFact("invalidBindingCount", invalidBindingCount.toString()),
            DeploymentSafeFact("duplicateDefaultPurposeCount", duplicateDefaultPurposeCount.toString()),
            DeploymentSafeFact(
                "unsupportedCapabilityWorkstationCount",
                unsupportedCapabilityWorkstationCount.toString(),
            ),
            DeploymentSafeFact("workstationRequired", workstationRequired.toString()),
            DeploymentSafeFact("problemCount", problems.size.toString()),
            DeploymentSafeFact("problems", problems.joinToString()),
        )
        if (problems.isEmpty()) {
            return DeploymentCheckOutcome(
                state = EnumDeploymentCheckState.PASS,
                summary = "RFID 回调、设备档案和现场核验已就绪；作业设备检查工作站绑定，门禁独立接入",
                safeFacts = facts,
            )
        }
        return DeploymentCheckOutcome(
            state = EnumDeploymentCheckState.FAIL,
            summary = "RFID 接入配置尚有 ${problems.size} 项未完成",
            reasonCode = "COLLECTION_RFID_INTEGRATION_CONFIGURATION_INCOMPLETE",
            safeFacts = facts,
            remediations = listOf(
                DeploymentRemediation(
                    type = EnumDeploymentRemediationType.CONFIGURATION,
                    summary = "按设备接入顺序补齐 RFID 配置",
                    steps = listOf(
                        "先在设备管理中登记真实 RFID 设备、业务角色、硬件序列号并完成现场核验",
                        "固定设备场景配置并启用 COLLECTION_RFID_CALLBACK HTTP PUSH 通道及加密凭据，并在采集配置中执行回调测试",
                        "借还、发标等作业设备按 RFID_READ 或 RFID_WRITE 用途绑定工作站；门禁无需绑定工作站",
                        "门禁按已完成的 RFID 出入库流程核验通行，未完成时记录并报警",
                    ),
                ),
            ),
        )
    }

    private fun disabledOutcome() = DeploymentCheckOutcome(
        state = EnumDeploymentCheckState.SKIPPED,
        summary = "当前部署未声明启用 RFID 接入就绪检查",
        safeFacts = listOf(DeploymentSafeFact("readinessEnabled", "false")),
    )
}

/** 固定 RFID 设备在线态检查。配置缺失由上一个 CRITICAL 检查负责，本项只反映运行状态。 */
// 检查仅查询；SQLite 建连后不能切换 readOnly，保留事务以覆盖实际后台扫描。
@Component
@Transactional
class RfidFixedDeviceRuntimeDiagnosticCheck(
    @param:Value("\${collection.rfid.readiness.enabled:false}")
    private val readinessEnabled: Boolean,
    @param:Value("\${collection.rfid.readiness.fixed-devices-required:false}")
    private val fixedDevicesRequired: Boolean,
) : DeploymentDiagnosticCheck {
    override val descriptor = DeploymentCheckDescriptor(
        id = "collection.rfid.fixed-device-runtime",
        category = "RUNTIME",
        feature = "藏品 RFID",
        name = "固定设备在线状态",
        severity = EnumDeploymentCheckSeverity.WARNING,
        timeoutMillis = 10_000,
        order = 117,
    )

    override fun check(): DeploymentCheckOutcome {
        if (!readinessEnabled || !fixedDevicesRequired) {
            return DeploymentCheckOutcome(
                state = EnumDeploymentCheckState.SKIPPED,
                summary = "当前部署未要求固定式 RFID 设备运行检查",
                safeFacts = listOf(
                    DeploymentSafeFact("readinessEnabled", readinessEnabled.toString()),
                    DeploymentSafeFact("fixedDevicesRequired", fixedDevicesRequired.toString()),
                ),
            )
        }
        val fixedDeviceIds = MuseumRfidDeviceProfiles.select(
            MuseumRfidDeviceProfiles.deviceId,
            MuseumRfidDeviceProfiles.role,
            MuseumRfidDeviceProfiles.rolesJson,
        ).where {
            (MuseumRfidDeviceProfiles.enabled eq true) and
                (MuseumRfidDeviceProfiles.status eq EnumContentStatus.NORMAL)
        }.filter { row ->
            profileRoles(
                row[MuseumRfidDeviceProfiles.role],
                row[MuseumRfidDeviceProfiles.rolesJson],
            ).any(FIXED_DEVICE_ROLES::contains)
        }.mapTo(linkedSetOf()) { it[MuseumRfidDeviceProfiles.deviceId] }
        if (fixedDeviceIds.isEmpty()) {
            return DeploymentCheckOutcome(
                state = EnumDeploymentCheckState.SKIPPED,
                summary = "尚未配置固定式 RFID 设备，无法检查在线状态",
                reasonCode = "COLLECTION_RFID_FIXED_DEVICE_NOT_CONFIGURED",
                safeFacts = listOf(DeploymentSafeFact("fixedProfileCount", "0")),
            )
        }

        val onlineCount = MuseumDeviceConnections.select(MuseumDeviceConnections.deviceId).where {
            (MuseumDeviceConnections.state eq EnumRfidConnectionState.ONLINE) and
                (MuseumDeviceConnections.leaseUntil greater museumUtcNow()) and
                (MuseumDeviceConnections.deviceId inList fixedDeviceIds)
        }.count().toInt()
        val facts = listOf(
            DeploymentSafeFact("fixedProfileCount", fixedDeviceIds.size.toString()),
            DeploymentSafeFact("onlineFixedDeviceCount", onlineCount.toString()),
            DeploymentSafeFact("offlineOrExpiredFixedDeviceCount", (fixedDeviceIds.size - onlineCount).toString()),
        )
        if (onlineCount == fixedDeviceIds.size) {
            return DeploymentCheckOutcome(
                state = EnumDeploymentCheckState.PASS,
                summary = "全部 $onlineCount 台固定式 RFID 设备在线且连接租约有效",
                safeFacts = facts,
            )
        }
        return DeploymentCheckOutcome(
            state = EnumDeploymentCheckState.FAIL,
            summary = "${fixedDeviceIds.size - onlineCount} 台固定式 RFID 设备未在线或连接租约已过期",
            reasonCode = "COLLECTION_RFID_FIXED_DEVICE_OFFLINE",
            safeFacts = facts,
            remediations = listOf(
                DeploymentRemediation(
                    type = EnumDeploymentRemediationType.SERVICE,
                    summary = "恢复固定设备到 museum-ingest 的 TCP 连接",
                    steps = listOf(
                        "确认 museum-ingest 的 TCP_MAIN 端口正在监听且设备网络可达",
                        "核对厂商管理软件中一次性配置的服务器地址、端口、自动重连和设备序列号",
                        "查看采集问题消息中的未登记设备、协议错误或心跳超时记录",
                        "连接恢复后重新运行部署扫描，并用真实读数完成业务链路核验",
                    ),
                ),
            ),
        )
    }
}

private val FIXED_DEVICE_ROLES = setOf(
    EnumRfidDeviceRole.DESK_READER,
    EnumRfidDeviceRole.TAG_WRITER,
    EnumRfidDeviceRole.WAREHOUSE_GATE,
    EnumRfidDeviceRole.MUSEUM_GATE,
)

private data class RfidReadyProfile(
    val orgId: String,
    val roles: Set<EnumRfidDeviceRole>,
)

private fun profileRoles(
    legacyRole: EnumRfidDeviceRole,
    rolesJson: String?,
): Set<EnumRfidDeviceRole> {
    val parsed = runCatching {
        JsonUtils.mapper.readValue(rolesJson, Array<String>::class.java)
            .mapNotNull { value -> runCatching { EnumRfidDeviceRole.valueOf(value) }.getOrNull() }
            .toSet()
    }.getOrDefault(emptySet())
    return parsed.ifEmpty { setOf(legacyRole) }
}
