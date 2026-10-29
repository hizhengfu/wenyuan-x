package io.hizhengfu.cloud.collection.rfid.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.rfid.dao.RfidOperatorCards
import io.hizhengfu.cloud.collection.rfid.dao.RfidWorkstationDeviceBindings
import io.hizhengfu.cloud.collection.rfid.dao.RfidWorkstations
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidOperatorCardStatus
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidWorkstationCapability
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidWorkstationDevicePurpose
import io.hizhengfu.cloud.collection.rfid.domain.RfidCardLoginResultVo
import io.hizhengfu.cloud.collection.rfid.domain.RfidCardLoginVo
import io.hizhengfu.cloud.collection.rfid.domain.RfidCardTokenIssuer
import io.hizhengfu.cloud.collection.rfid.domain.RfidDeviceCommandPort
import io.hizhengfu.cloud.collection.rfid.domain.RfidHex
import io.hizhengfu.cloud.collection.rfid.domain.RfidOperatorCardSaveVo
import io.hizhengfu.cloud.collection.rfid.domain.RfidWorkstationCreatedVo
import io.hizhengfu.cloud.collection.rfid.domain.RfidWorkstationContextVo
import io.hizhengfu.cloud.collection.rfid.domain.RfidWorkstationDeviceBindingSaveVo
import io.hizhengfu.cloud.collection.rfid.domain.RfidWorkstationDeviceBindingVo
import io.hizhengfu.cloud.collection.rfid.domain.RfidWorkstationEnrollResultVo
import io.hizhengfu.cloud.collection.rfid.domain.RfidWorkstationEnrollVo
import io.hizhengfu.cloud.collection.rfid.domain.RfidWorkstationLeaseVo
import io.hizhengfu.cloud.collection.rfid.domain.RfidWorkstationSaveVo
import io.hizhengfu.cloud.collection.rfid.domain.RfidWorkstationVo
import io.hizhengfu.cloud.collection.rfid.domain.RfidWorkstationAccessPolicy
import io.hizhengfu.cloud.collection.service.OrgScopedReadPolicy
import io.hizhengfu.cloud.museum.device.dao.MuseumLocations
import io.hizhengfu.cloud.museum.device.dao.MuseumDevices
import io.hizhengfu.cloud.museum.device.dao.MuseumDeviceConnections
import io.hizhengfu.cloud.museum.device.domain.EnumRfidConnectionState
import io.hizhengfu.cloud.museum.device.common.museumUtcNow
import io.hizhengfu.cloud.common.domain.EnumContentStatus
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.IdGenerate
import io.hizhengfu.cloud.common.utils.SecurityUtils
import io.hizhengfu.cloud.sys.system.dao.Users
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Base64

@Component
@Transactional
class RfidWorkstationService(
    private val scope: CollectionScopeResolver,
    private val tokenIssuerProvider: ObjectProvider<RfidCardTokenIssuer>,
    private val devicePortProvider: ObjectProvider<RfidDeviceCommandPort>,
    @param:Value("\${collection.rfid.workstation-lease-seconds:45}")
    private val workstationLeaseSeconds: Long,
) {
    private val secureRandom = SecureRandom()

    @Transactional(readOnly = true)
    fun workstations(): List<RfidWorkstationVo> {
        val query = RfidWorkstations.selectAll()
        scope.readableOrgIds()?.let { query.andWhere { RfidWorkstations.orgId inList it } }
        return query.orderBy(RfidWorkstations.createTime to SortOrder.DESC).map(::workstationVo)
    }

    fun saveWorkstation(vo: RfidWorkstationSaveVo): RfidWorkstationCreatedVo {
        val now = LocalDateTime.now()
        val userId = SecurityUtils.getUserId()
        val name = vo.name.clean(100) ?: throw BadRequestException("工作站名称必填")
        val id = vo.id.clean(64)
        val existing = id?.let(::findWorkstation)
        val capabilities = vo.capabilities
        val defaultCapability = vo.defaultCapability?.also {
            if (it !in capabilities) throw BadRequestException("默认能力必须包含在工作站业务能力中")
        }
        val enabled = vo.enabled ?: existing?.get(RfidWorkstations.enabled) ?: false
        val bindings = vo.deviceBindings.mapIndexed(::normalizeBinding)
        if (bindings.distinctBy { it.purpose to it.deviceId }.size != bindings.size) {
            throw BadRequestException("同一设备不能重复绑定相同用途")
        }
        val directionsJson = validatedDirections(vo.antennaDirections, capabilities, enabled)
        validatePassagePolicy(vo.passageDebounceSeconds, vo.alarmOutputIndex, vo.alarmDurationMillis)
        if (id == null) {
            val createdId = IdGenerate.nextId()
            val code = generatedWorkstationCode(createdId)
            val orgId = scope.currentOrgId()
            validateConfiguration(orgId, createdId, capabilities, bindings, enabled)
            val installationLocationId = locationForWrite(orgId, vo.locationId)
            requireUniqueCode(orgId, code, null)
            val token = newEnrollmentToken()
            RfidWorkstations.insert {
                it[RfidWorkstations.id] = createdId
                it[RfidWorkstations.orgId] = orgId
                it[RfidWorkstations.code] = code
                it[RfidWorkstations.name] = name
                it[RfidWorkstations.defaultCapability] = defaultCapability
                it[locationId] = installationLocationId
                it[capabilitiesJson] = encodeCapabilities(capabilities)
                it[antennaDirectionsJson] = directionsJson
                it[passageDebounceSeconds] = vo.passageDebounceSeconds
                it[alarmOutputIndex] = vo.alarmOutputIndex
                it[alarmDurationMillis] = vo.alarmDurationMillis
                it[enrollmentTokenHash] = RfidHex.sha256(token)
                it[browserCredentialHash] = null
                it[activeLeaseInstanceHash] = null
                it[activeLeaseUntil] = null
                it[RfidWorkstations.enabled] = enabled
                it[createTime] = now
                it[createBy] = userId
                it[updateTime] = now
                it[updateBy] = userId
            }
            replaceBindings(createdId, bindings, now, userId)
            return RfidWorkstationCreatedVo(createdId, code, token)
        }
        val row = existing ?: throw BadRequestException("RFID 工作站不存在")
        val orgId = row[RfidWorkstations.orgId]
        OrgScopedReadPolicy.requireVisible(scope.writableOrgIds(), orgId, "RFID 工作站")
        validateConfiguration(orgId, id, capabilities, bindings, enabled)
        val installationLocationId = locationForWrite(orgId, vo.locationId)
        RfidWorkstations.update({ RfidWorkstations.id eq id }) {
            it[RfidWorkstations.name] = name
            it[RfidWorkstations.defaultCapability] = defaultCapability
            it[locationId] = installationLocationId
            it[capabilitiesJson] = encodeCapabilities(capabilities)
            it[antennaDirectionsJson] = directionsJson
            it[passageDebounceSeconds] = vo.passageDebounceSeconds
            it[alarmOutputIndex] = vo.alarmOutputIndex
            it[alarmDurationMillis] = vo.alarmDurationMillis
            it[RfidWorkstations.enabled] = enabled
            if (!enabled) {
                it[activeLeaseInstanceHash] = null
                it[activeLeaseUntil] = null
            }
            it[updateTime] = now
            it[updateBy] = userId
        }
        replaceBindings(id, bindings, now, userId)
        return RfidWorkstationCreatedVo(id, row[RfidWorkstations.code], null)
    }

    /** 工作站使用ing_location身份，与统一位置停用共用行锁，不能按col_warehouse ID解释。 */
    private fun locationForWrite(orgId: String, value: String?): String? {
        val id = value?.trim()?.takeIf(String::isNotBlank) ?: return null
        // 无业务字段变化；实际UPDATE取得跨进程写锁，SQLite也无需先读后升级锁。
        MuseumLocations.update({ MuseumLocations.id eq id }) { it[depth] = MuseumLocations.depth }
        val row = MuseumLocations.selectAll().where { MuseumLocations.id eq id }.singleOrNull()
        if (row == null || row[MuseumLocations.orgId] != orgId || row[MuseumLocations.status] != EnumContentStatus.NORMAL)
            throw BadRequestException("安装位置不存在、已停用或不属于当前收藏单位")
        return id
    }

    private fun validateConfiguration(
        orgId: String,
        workstationId: String,
        capabilities: Set<EnumRfidWorkstationCapability>,
        bindings: List<NormalizedBinding>,
        enabled: Boolean,
    ) {
        if (enabled && capabilities.isEmpty()) throw BadRequestException("启用工作站前请至少选择一项业务能力")
        if (enabled && capabilities.any { it in UNSUPPORTED_RFID_WORKSTATION_CAPABILITIES }) {
            throw BadRequestException("标签打印和单据打印运行链路尚未实现，不能作为生产能力启用")
        }
        if (bindings.groupBy { it.purpose }.any { (_, values) -> values.count { it.isDefault && it.enabled } > 1 }) {
            throw BadRequestException("同一外设用途只能设置一台默认设备")
        }
        if (enabled) {
            capabilities.forEach { capability ->
                val purpose = requiredPurpose(capability)
                if (bindings.none { it.purpose == purpose && it.enabled }) {
                    throw BadRequestException("启用工作站前请配置${purpose.description}设备")
                }
            }
            bindings.filter { it.enabled && it.purpose == EnumRfidWorkstationDevicePurpose.PASSAGE_READ }.forEach { binding ->
                val conflictingStationIds = RfidWorkstationDeviceBindings.selectAll().where {
                    (RfidWorkstationDeviceBindings.deviceId eq binding.deviceId) and
                        (RfidWorkstationDeviceBindings.purpose eq EnumRfidWorkstationDevicePurpose.PASSAGE_READ) and
                        (RfidWorkstationDeviceBindings.enabled eq true)
                }.map { it[RfidWorkstationDeviceBindings.workstationId] }.filter { it != workstationId }
                val occupied = conflictingStationIds.any { otherId ->
                    RfidWorkstations.selectAll().where {
                        (RfidWorkstations.id eq otherId) and (RfidWorkstations.enabled eq true)
                    }.limit(1).any()
                }
                if (occupied) throw BadRequestException("同一通道读取设备不能绑定多个已启用工作站")
            }
        }
        val port = devicePortProvider.ifAvailable ?: return
        bindings.filter(NormalizedBinding::enabled).forEach { binding ->
            val expected = expectedRoles(binding.purpose)
            if (!port.supportsAnyRole(binding.deviceId, orgId, expected)) {
                throw BadRequestException("${binding.purpose.description}设备未启用、机构不一致或设备角色不匹配")
            }
        }
    }

    /** 重置后旧浏览器令牌立即失效，新令牌仅在本次响应中出现。 */
    fun resetEnrollmentToken(id: String): RfidWorkstationCreatedVo {
        val row = findWorkstation(id)
        OrgScopedReadPolicy.requireVisible(scope.writableOrgIds(), row[RfidWorkstations.orgId], "RFID 工作站")
        val token = newEnrollmentToken()
        RfidWorkstations.update({ RfidWorkstations.id eq id }) {
            it[enrollmentTokenHash] = RfidHex.sha256(token)
            it[browserCredentialHash] = null
            it[activeLeaseInstanceHash] = null
            it[activeLeaseUntil] = null
            it[updateTime] = LocalDateTime.now()
            it[updateBy] = SecurityUtils.getUserId()
        }
        return RfidWorkstationCreatedVo(id, row[RfidWorkstations.code], token)
    }

    fun saveOperatorCard(vo: RfidOperatorCardSaveVo): String {
        val orgId = scope.currentOrgId()
        val userId = vo.userId.clean(64) ?: throw BadRequestException("用户必填")
        if (vo.allowedCapabilities.isEmpty()) throw BadRequestException("至少选择一个允许使用的业务能力")
        requireEnabledUser(userId)
        val capabilities = vo.allowedCapabilities
        val validFrom = vo.validFrom.toLocalDateTime() ?: LocalDateTime.now()
        val validUntil = vo.validUntil.toLocalDateTime()
        if (validUntil != null && !validUntil.isAfter(validFrom)) throw BadRequestException("失效时间必须晚于生效时间")
        val id = vo.id.clean(64)
        val now = LocalDateTime.now()
        if (id == null) {
            val cardUid = RfidHex.normalizeCardUid(vo.cardUid)
            val hash = RfidHex.sha256(cardUid)
            if (RfidOperatorCards.selectAll().where {
                    (RfidOperatorCards.orgId eq orgId) and (RfidOperatorCards.cardUidHash eq hash)
                }.limit(1).any()
            ) throw BadRequestException("该人员卡已登记")
            val newId = IdGenerate.nextId()
            RfidOperatorCards.insert {
                it[RfidOperatorCards.id] = newId
                it[RfidOperatorCards.orgId] = orgId
                it[cardUidHash] = hash
                it[cardLast4] = cardUid.takeLast(4)
                it[RfidOperatorCards.userId] = userId
                it[allowedCapabilities] = capabilities.joinToString(",") { capability -> capability.name }
                it[RfidOperatorCards.validFrom] = validFrom
                it[RfidOperatorCards.validUntil] = validUntil
                it[cardStatus] = EnumRfidOperatorCardStatus.ACTIVE
                it[issuedAt] = now
                it[issuedBy] = SecurityUtils.getUserId()
                it[createTime] = now
                it[createBy] = SecurityUtils.getUserId()
                it[updateTime] = now
                it[updateBy] = SecurityUtils.getUserId()
            }
            return newId
        }
        val row = RfidOperatorCards.selectAll().where { RfidOperatorCards.id eq id }.limit(1).firstOrNull()
            ?: throw BadRequestException("人员卡不存在")
        OrgScopedReadPolicy.requireVisible(scope.writableOrgIds(), row[RfidOperatorCards.orgId], "人员卡")
        RfidOperatorCards.update({ RfidOperatorCards.id eq id }) {
            it[RfidOperatorCards.userId] = userId
            it[allowedCapabilities] = capabilities.joinToString(",") { capability -> capability.name }
            it[RfidOperatorCards.validFrom] = validFrom
            it[RfidOperatorCards.validUntil] = validUntil
            it[updateTime] = now
            it[updateBy] = SecurityUtils.getUserId()
        }
        return id
    }

    @Transactional(readOnly = true)
    fun operatorCards(): List<Map<String, Any?>> {
        val query = RfidOperatorCards.selectAll()
        scope.readableOrgIds()?.let { query.andWhere { RfidOperatorCards.orgId inList it } }
        return query.orderBy(RfidOperatorCards.issuedAt to SortOrder.DESC).map {
            mapOf(
                "id" to it[RfidOperatorCards.id].value,
                "orgId" to it[RfidOperatorCards.orgId],
                "cardLast4" to it[RfidOperatorCards.cardLast4],
                "userId" to it[RfidOperatorCards.userId],
                "allowedCapabilities" to it[RfidOperatorCards.allowedCapabilities].split(',').filter(String::isNotBlank),
                "validFrom" to it[RfidOperatorCards.validFrom].atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                "validUntil" to it[RfidOperatorCards.validUntil]?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli(),
                "status" to it[RfidOperatorCards.cardStatus],
            )
        }
    }

    fun updateCardStatus(id: String, status: EnumRfidOperatorCardStatus, reason: String?): String {
        val row = RfidOperatorCards.selectAll().where { RfidOperatorCards.id eq id }.limit(1).firstOrNull()
            ?: throw BadRequestException("人员卡不存在")
        OrgScopedReadPolicy.requireVisible(scope.writableOrgIds(), row[RfidOperatorCards.orgId], "人员卡")
        val now = LocalDateTime.now()
        RfidOperatorCards.update({ RfidOperatorCards.id eq id }) {
            it[cardStatus] = status
            it[revokedAt] = if (status == EnumRfidOperatorCardStatus.ACTIVE) null else now
            it[revokedBy] = if (status == EnumRfidOperatorCardStatus.ACTIVE) null else SecurityUtils.getUserId()
            it[revokedReason] = reason.clean(500)
            it[updateTime] = now
            it[updateBy] = SecurityUtils.getUserId()
        }
        return id
    }

    /** 一次性注册完成后返回浏览器凭证原文；Controller 只把它写入 HttpOnly Cookie，不放进 JSON。 */
    fun enrollBrowser(vo: RfidWorkstationEnrollVo): Pair<RfidWorkstationEnrollResultVo, String> {
        val stationCode = vo.stationCode.clean(64)?.uppercase() ?: throw BadRequestException("工作站编码必填")
        val enrollmentToken = vo.enrollmentToken.clean(200) ?: throw BadRequestException("工作站注册令牌必填")
        val station = RfidWorkstations.selectAll().where {
            RfidWorkstations.code eq stationCode
        }.toList().firstOrNull { row -> constantTimeHashEquals(row[RfidWorkstations.enrollmentTokenHash], enrollmentToken) }
            ?: throw BadRequestException("工作站注册信息无效")
        val browserCredential = newEnrollmentToken()
        RfidWorkstations.update({ RfidWorkstations.id eq station[RfidWorkstations.id].value }) {
            it[browserCredentialHash] = RfidHex.sha256(browserCredential)
            it[activeLeaseInstanceHash] = null
            it[activeLeaseUntil] = null
            // 登记码仅允许使用一次：保留非空随机摘要满足表约束，但原登记码立即失效。
            it[enrollmentTokenHash] = RfidHex.sha256(newEnrollmentToken())
            it[updateTime] = LocalDateTime.now()
        }
        return RfidWorkstationEnrollResultVo(station[RfidWorkstations.id].value, station[RfidWorkstations.name]) to browserCredential
    }

    /**
     * 根据 HttpOnly 浏览器凭证恢复本机绑定。没有凭证或凭证已被管理员重置时返回 null，
     * 不接受工作站编码作为查询条件，避免 URL 参数改变已绑定工作站。
     */
    @Transactional(readOnly = true)
    fun browserContext(browserCredential: String?): RfidWorkstationContextVo? {
        val credential = browserCredential.clean(200) ?: return null
        val credentialHash = RfidHex.sha256(credential)
        val station = RfidWorkstations.selectAll().where {
            RfidWorkstations.browserCredentialHash eq credentialHash
        }.limit(1).firstOrNull() ?: return null
        return RfidWorkstationContextVo(
            workstationId = station[RfidWorkstations.id].value,
            workstationCode = station[RfidWorkstations.code],
            workstationName = station[RfidWorkstations.name],
            defaultCapability = station[RfidWorkstations.defaultCapability],
            capabilities = workstationCapabilities(station),
            enabled = station[RfidWorkstations.enabled],
        )
    }

    /** 平台认证完成后，在进入独立工作台前校验本机绑定和工作站全部能力权限。 */
    @Transactional(readOnly = true)
    fun authorizeCurrentOperator(browserCredential: String?): RfidWorkstationContextVo {
        val context = browserContext(browserCredential) ?: throw BadRequestException("工作站注册信息无效")
        if (!context.enabled) throw BadRequestException("RFID 工作站已停用")
        val user = SecurityUtils.getUserDetail()
        val station = findWorkstation(context.workstationId)
        scope.writableOrgIds()?.let { writableOrgIds ->
            if (station[RfidWorkstations.orgId] !in writableOrgIds) {
                throw BadRequestException("无权操作该 RFID 工作站")
            }
        }
        RfidWorkstationAccessPolicy.requireAllowed(
            grantedAuthorities = user.permissions.toSet(),
            requiredAuthorities = RfidWorkstationAccessPolicy.requiredAuthorities(context.capabilities),
            administrative = SecurityUtils.isAdminRole(),
        )
        return context
    }

    @Transactional(readOnly = true)
    fun workstationForCurrentOperator(browserCredential: String?): RfidWorkstationVo {
        val context = authorizeCurrentOperator(browserCredential)
        return workstationVo(findWorkstation(context.workstationId))
    }

    /**
     * 工作站 URL 只是入口提示，真正的主机身份来自首次注册后保存的 HttpOnly Cookie。
     * 所有固定式读写器业务在创建或操作会话时都必须重新校验，不能只依赖前端锁定选择框。
     */
    @Transactional(readOnly = true)
    fun requireBrowserBinding(browserCredential: String?, workstationId: String): RfidWorkstationContextVo {
        val normalized = workstationId.clean(64) ?: throw BadRequestException("工作站必填")
        val context = browserContext(browserCredential) ?: throw BadRequestException("工作站注册信息无效")
        if (context.workstationId != normalized) throw BadRequestException("当前电脑未绑定该 RFID 工作站")
        if (!context.enabled) throw BadRequestException("RFID 工作站已停用")
        return context
    }

    /**
     * 同一工作站只允许一个浏览器窗口持有短租约。普通获取不会抢占仍有效的租约；
     * 操作员在页面上明确选择“接管”时才允许 force=true 覆盖旧窗口。
     */
    fun acquireBrowserLease(
        browserCredential: String?,
        clientInstance: String?,
        force: Boolean = false,
    ): RfidWorkstationLeaseVo {
        val credential = browserCredential.clean(200) ?: throw BadRequestException("工作站注册信息无效")
        val instance = clientInstance.clean(128) ?: throw BadRequestException("缺少工作站窗口标识")
        val credentialHash = RfidHex.sha256(credential)
        val instanceHash = RfidHex.sha256(instance)
        val query = RfidWorkstations.selectAll().where {
            RfidWorkstations.browserCredentialHash eq credentialHash
        }.limit(1)
        query.forUpdate()
        val station = query.firstOrNull() ?: throw BadRequestException("工作站注册信息无效")
        if (!station[RfidWorkstations.enabled]) throw BadRequestException("RFID 工作站已停用")
        val now = LocalDateTime.now()
        val currentInstanceHash = station[RfidWorkstations.activeLeaseInstanceHash]
        val currentLeaseUntil = station[RfidWorkstations.activeLeaseUntil]
        val currentRuntimeId = rfidWorkstationRuntimeId(credential, instance)
        if (currentInstanceHash != instanceHash &&
            hasLiveSikeConnection(station[RfidWorkstations.id].value, currentRuntimeId)
        ) {
            throw BadRequestException("斯科读写器仍由其他窗口连接，请等待连接释放后再接管")
        }
        if (!force && currentInstanceHash != null && currentInstanceHash != instanceHash && currentLeaseUntil?.isAfter(now) == true) {
            throw BadRequestException("工作站正在另一窗口操作，请关闭其他窗口或选择接管")
        }
        val leaseUntil = now.plusSeconds(workstationLeaseSeconds.coerceIn(15, 300))
        RfidWorkstations.update({ RfidWorkstations.id eq station[RfidWorkstations.id].value }) {
            it[activeLeaseInstanceHash] = instanceHash
            it[activeLeaseUntil] = leaseUntil
            it[updateTime] = now
        }
        return RfidWorkstationLeaseVo(
            workstationId = station[RfidWorkstations.id].value,
            leaseUntil = leaseUntil.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        )
    }

    private fun hasLiveSikeConnection(workstationId: String, currentRuntimeId: String): Boolean {
        val boundIds = RfidWorkstationDeviceBindings.selectAll().where {
            (RfidWorkstationDeviceBindings.workstationId eq workstationId) and
                (RfidWorkstationDeviceBindings.enabled eq true)
        }.map { it[RfidWorkstationDeviceBindings.deviceId] }.distinct()
        if (boundIds.isEmpty()) return false
        val sikeIds = MuseumDevices.selectAll().where {
            (MuseumDevices.id inList boundIds) and (MuseumDevices.protocolCode eq "SIKE_RFID_WS_V1")
        }.map { it[MuseumDevices.id].value }
        if (sikeIds.isEmpty()) return false
        return MuseumDeviceConnections.selectAll().where {
            (MuseumDeviceConnections.deviceId inList sikeIds) and
                (MuseumDeviceConnections.state eq EnumRfidConnectionState.ONLINE)
        }.any {
            it[MuseumDeviceConnections.leaseUntil].isAfter(museumUtcNow()) &&
                it[MuseumDeviceConnections.runtimeInstanceId] != currentRuntimeId
        }
    }

    fun renewBrowserLease(browserCredential: String?, clientInstance: String?): RfidWorkstationLeaseVo =
        acquireBrowserLease(browserCredential, clientInstance, force = false)

    fun releaseBrowserLease(browserCredential: String?, clientInstance: String?): Boolean {
        val credential = browserCredential.clean(200) ?: return false
        val instance = clientInstance.clean(128) ?: return false
        val credentialHash = RfidHex.sha256(credential)
        val instanceHash = RfidHex.sha256(instance)
        return RfidWorkstations.update({
            (RfidWorkstations.browserCredentialHash eq credentialHash) and
                (RfidWorkstations.activeLeaseInstanceHash eq instanceHash)
        }) {
            it[activeLeaseInstanceHash] = null
            it[activeLeaseUntil] = null
            it[updateTime] = LocalDateTime.now()
        } == 1
    }

    /** 所有固定设备变更命令都必须同时通过主机绑定和当前浏览器窗口租约校验。 */
    @Transactional(readOnly = true)
    fun requireBrowserLease(
        browserCredential: String?,
        clientInstance: String?,
        workstationId: String,
    ): RfidWorkstationContextVo {
        val context = requireBrowserBinding(browserCredential, workstationId)
        val instance = clientInstance.clean(128) ?: throw BadRequestException("当前窗口未取得 RFID 工作站操作权")
        val station = RfidWorkstations.selectAll().where { RfidWorkstations.id eq context.workstationId }
            .limit(1).firstOrNull() ?: throw BadRequestException("RFID 工作站不存在")
        val leaseValid = station[RfidWorkstations.activeLeaseInstanceHash]?.let { expected ->
            constantTimeHashEquals(expected, instance)
        } == true && station[RfidWorkstations.activeLeaseUntil]?.isAfter(LocalDateTime.now()) == true
        if (!leaseValid) throw BadRequestException("当前窗口未取得 RFID 工作站操作权，请刷新或重新接管")
        return context
    }

    /**
     * 公开卡登录必须同时校验“已登记浏览器 + 有效人员卡 + 有效用户”。
     * 只知道卡号或只复制工作站 URL 都不能登录。
     */
    fun cardLogin(vo: RfidCardLoginVo, browserCredential: String? = null): RfidCardLoginResultVo {
        val stationCode = vo.stationCode.clean(64)?.uppercase() ?: throw BadRequestException("工作站编码必填")
        val cardUidHash = RfidHex.sha256(RfidHex.normalizeCardUid(vo.cardUid))
        val stations = RfidWorkstations.selectAll().where {
            (RfidWorkstations.code eq stationCode) and (RfidWorkstations.enabled eq true)
        }.toList()
        val station = stations.firstOrNull { row ->
            browserCredential.clean(200)?.let { credential ->
                row[RfidWorkstations.browserCredentialHash]?.let { constantTimeHashEquals(it, credential) }
            } == true
        } ?: throw BadRequestException("工作站凭证无效")
        val now = LocalDateTime.now()
        val card = RfidOperatorCards.selectAll().where {
            (RfidOperatorCards.orgId eq station[RfidWorkstations.orgId]) and
                (RfidOperatorCards.cardUidHash eq cardUidHash) and
                (RfidOperatorCards.cardStatus eq EnumRfidOperatorCardStatus.ACTIVE)
        }.limit(1).firstOrNull() ?: throw BadRequestException("人员卡无效")
        if (card[RfidOperatorCards.validFrom].isAfter(now) || card[RfidOperatorCards.validUntil]?.isBefore(now) == true) {
            throw BadRequestException("人员卡不在有效期内")
        }
        val allowedCapabilities = card[RfidOperatorCards.allowedCapabilities].split(',').toSet()
        val stationCapabilities = workstationCapabilities(station)
        if (stationCapabilities.any { it.name !in allowedCapabilities }) {
            throw BadRequestException("人员卡无权登录包含这些业务能力的工作站")
        }
        val userId = card[RfidOperatorCards.userId]
        requireEnabledUser(userId)
        val issuer = tokenIssuerProvider.ifAvailable ?: throw BadRequestException("当前系统未配置 RFID 卡登录令牌签发器")
        val issued = issuer.issue(
            userId,
            station[RfidWorkstations.orgId],
            RfidWorkstationAccessPolicy.requiredAuthorities(stationCapabilities),
        )
        RfidWorkstations.update({ RfidWorkstations.id eq station[RfidWorkstations.id].value }) { it[lastLoginAt] = now }
        return RfidCardLoginResultVo(
            token = issued.token,
            expirationMillis = issued.expirationMillis,
            userId = userId,
            userName = issued.userName,
            workstationId = station[RfidWorkstations.id].value,
            workstationName = station[RfidWorkstations.name],
        )
    }

    private fun findWorkstation(id: String) = RfidWorkstations.selectAll()
        .where { RfidWorkstations.id eq id }.limit(1).firstOrNull()
        ?: throw BadRequestException("RFID 工作站不存在")

    private fun requireUniqueCode(orgId: String, code: String, excludingId: String?) {
        val duplicate = RfidWorkstations.selectAll().where {
            (RfidWorkstations.orgId eq orgId) and (RfidWorkstations.code eq code)
        }.limit(1).firstOrNull()
        if (duplicate != null && duplicate[RfidWorkstations.id].value != excludingId) throw BadRequestException("工作站编码已存在")
    }

    private fun requireEnabledUser(userId: String) {
        val user = Users.selectAll().where { Users.id eq userId }.limit(1).firstOrNull()
            ?: throw BadRequestException("用户不存在")
        if (user[Users.status] != EnumContentStatus.NORMAL || user[Users.enabled] != true) throw BadRequestException("用户已停用")
        user[Users.expireTime]?.let { if (it.isBefore(LocalDateTime.now())) throw BadRequestException("用户已过期") }
    }

    private fun workstationVo(row: ResultRow): RfidWorkstationVo {
        val bindings = workstationDeviceBindings(row[RfidWorkstations.id].value).map {
            RfidWorkstationDeviceBindingVo(
                id = it[RfidWorkstationDeviceBindings.id].value,
                deviceId = it[RfidWorkstationDeviceBindings.deviceId],
                purpose = it[RfidWorkstationDeviceBindings.purpose],
                isDefault = it[RfidWorkstationDeviceBindings.isDefault],
                priority = it[RfidWorkstationDeviceBindings.priority],
                enabled = it[RfidWorkstationDeviceBindings.enabled],
            )
        }
        return RfidWorkstationVo(
            id = row[RfidWorkstations.id].value,
            orgId = row[RfidWorkstations.orgId],
            code = row[RfidWorkstations.code],
            name = row[RfidWorkstations.name],
            defaultCapability = row[RfidWorkstations.defaultCapability],
            locationId = row[RfidWorkstations.locationId],
            capabilities = workstationCapabilities(row),
            deviceBindings = bindings,
            antennaDirections = JsonUtils.fromJson<Map<String, String>>(row[RfidWorkstations.antennaDirectionsJson])
                ?.mapNotNull { (antenna, direction) ->
                    val number = antenna.toIntOrNull()
                    val value = runCatching {
                        io.hizhengfu.cloud.collection.rfid.domain.EnumRfidDirection.valueOf(direction)
                    }.getOrNull()
                    if (number == null || value == null) null else number to value
                }?.toMap().orEmpty(),
            passageDebounceSeconds = row[RfidWorkstations.passageDebounceSeconds],
            alarmOutputIndex = row[RfidWorkstations.alarmOutputIndex],
            alarmDurationMillis = row[RfidWorkstations.alarmDurationMillis],
            enabled = row[RfidWorkstations.enabled],
            lastLoginAt = row[RfidWorkstations.lastLoginAt]?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli(),
        )
    }

    private data class NormalizedBinding(
        val deviceId: String,
        val purpose: EnumRfidWorkstationDevicePurpose,
        val isDefault: Boolean,
        val priority: Int,
        val enabled: Boolean,
    )

    private fun normalizeBinding(index: Int, vo: RfidWorkstationDeviceBindingSaveVo): NormalizedBinding {
        val deviceId = vo.deviceId.clean(64) ?: throw BadRequestException("第 ${index + 1} 个外设未选择设备")
        val purpose = vo.purpose ?: throw BadRequestException("第 ${index + 1} 个外设未选择用途")
        if (vo.priority !in 0..10_000) throw BadRequestException("外设优先级必须在 0 至 10000 之间")
        return NormalizedBinding(deviceId, purpose, vo.isDefault, vo.priority, vo.enabled)
    }

    private fun replaceBindings(
        workstationId: String,
        bindings: List<NormalizedBinding>,
        now: LocalDateTime,
        userId: String?,
    ) {
        RfidWorkstationDeviceBindings.deleteWhere { RfidWorkstationDeviceBindings.workstationId eq workstationId }
        bindings.forEach { binding ->
            RfidWorkstationDeviceBindings.insert {
                it[id] = IdGenerate.nextId()
                it[RfidWorkstationDeviceBindings.workstationId] = workstationId
                it[deviceId] = binding.deviceId
                it[purpose] = binding.purpose
                it[isDefault] = binding.isDefault
                it[priority] = binding.priority
                it[enabled] = binding.enabled
                it[createTime] = now
                it[createBy] = userId
                it[updateTime] = now
                it[updateBy] = userId
            }
        }
    }

    private fun newEnrollmentToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun generatedWorkstationCode(id: String) = "RFID-WS-$id"

    private fun constantTimeHashEquals(expectedHash: String, candidate: String): Boolean = MessageDigest.isEqual(
        expectedHash.toByteArray(),
        RfidHex.sha256(candidate).toByteArray(),
    )

    private fun validatedDirections(
        directions: Map<Int, io.hizhengfu.cloud.collection.rfid.domain.EnumRfidDirection>,
        capabilities: Set<EnumRfidWorkstationCapability>,
        enabled: Boolean,
    ): String {
        if (directions.keys.any { it !in 1..255 }) throw BadRequestException("天线编号必须在 1 至 255 之间")
        if (directions.values.any { it == io.hizhengfu.cloud.collection.rfid.domain.EnumRfidDirection.UNKNOWN }) {
            throw BadRequestException("天线方向只能配置为进入或离开")
        }
        if (enabled && EnumRfidWorkstationCapability.PASSAGE_MONITOR in capabilities && directions.isEmpty()) {
            throw BadRequestException("启用通道监控能力前必须配置天线方向")
        }
        return JsonUtils.toJson(directions.mapKeys { it.key.toString() }.mapValues { it.value.name }) ?: "{}"
    }

    private fun validatePassagePolicy(debounceSeconds: Int, outputIndex: Int, durationMillis: Long) {
        if (debounceSeconds !in 1..60) throw BadRequestException("通道去抖时间必须在 1 至 60 秒之间")
        if (outputIndex !in 1..32) throw BadRequestException("报警输出编号必须在 1 至 32 之间")
        if (durationMillis !in 100..60_000) throw BadRequestException("报警持续时间必须在 100 至 60000 毫秒之间")
    }

    private fun String?.clean(max: Int) = this?.trim()?.takeIf(String::isNotBlank)?.take(max)
    private fun Long?.toLocalDateTime() = this?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDateTime() }

}

internal fun rfidWorkstationRuntimeId(credential: String, instance: String): String =
    "RFID_WS:" + RfidHex.sha256("$credential:$instance").take(56)
