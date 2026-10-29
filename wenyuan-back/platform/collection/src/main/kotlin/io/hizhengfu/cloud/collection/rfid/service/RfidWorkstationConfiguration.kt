package io.hizhengfu.cloud.collection.rfid.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.rfid.dao.RfidWorkstationDeviceBindings
import io.hizhengfu.cloud.collection.rfid.dao.RfidWorkstations
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidScanSessionType
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidWorkstationCapability
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidWorkstationDevicePurpose
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll

internal fun workstationCapabilities(station: ResultRow): Set<EnumRfidWorkstationCapability> {
    val stored = station[RfidWorkstations.capabilitiesJson]
    if (stored.isNotBlank()) {
        runCatching {
            JsonUtils.fromJson<List<String>>(stored).orEmpty()
                .map(EnumRfidWorkstationCapability::valueOf)
                .toSet()
        }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }
    }
    return emptySet()
}

internal fun requiredCapability(type: EnumRfidScanSessionType) = when (type) {
    EnumRfidScanSessionType.LOAN_OUT,
    EnumRfidScanSessionType.REQUISITION_OUT,
    EnumRfidScanSessionType.RETURN_IN,
    -> EnumRfidWorkstationCapability.BORROW_RETURN
    EnumRfidScanSessionType.INVENTORY -> EnumRfidWorkstationCapability.INVENTORY
    EnumRfidScanSessionType.TAG_VERIFY -> EnumRfidWorkstationCapability.TAG_ISSUE
    EnumRfidScanSessionType.PASSAGE_DIAG -> EnumRfidWorkstationCapability.PASSAGE_MONITOR
}

internal fun requiredDevicePurpose(type: EnumRfidScanSessionType) = when (type) {
    EnumRfidScanSessionType.TAG_VERIFY -> EnumRfidWorkstationDevicePurpose.RFID_WRITE
    EnumRfidScanSessionType.PASSAGE_DIAG -> EnumRfidWorkstationDevicePurpose.PASSAGE_READ
    else -> EnumRfidWorkstationDevicePurpose.RFID_READ
}

internal fun requiredPurpose(capability: EnumRfidWorkstationCapability) = when (capability) {
    EnumRfidWorkstationCapability.BORROW_RETURN,
    EnumRfidWorkstationCapability.INVENTORY,
    -> EnumRfidWorkstationDevicePurpose.RFID_READ
    EnumRfidWorkstationCapability.TAG_ISSUE -> EnumRfidWorkstationDevicePurpose.RFID_WRITE
    EnumRfidWorkstationCapability.TAG_PRINT -> EnumRfidWorkstationDevicePurpose.TAG_PRINT
    EnumRfidWorkstationCapability.DOCUMENT_PRINT -> EnumRfidWorkstationDevicePurpose.DOCUMENT_PRINT
    EnumRfidWorkstationCapability.PASSAGE_MONITOR -> EnumRfidWorkstationDevicePurpose.PASSAGE_READ
}

internal fun expectedRoles(purpose: EnumRfidWorkstationDevicePurpose): Set<String> = when (purpose) {
    EnumRfidWorkstationDevicePurpose.RFID_READ -> setOf("DESK_READER", "HANDHELD", "TAG_WRITER")
    EnumRfidWorkstationDevicePurpose.RFID_WRITE -> setOf("TAG_WRITER")
    EnumRfidWorkstationDevicePurpose.CARD_READ -> setOf("OPERATOR_CARD_READER")
    EnumRfidWorkstationDevicePurpose.TAG_PRINT -> setOf("TAG_PRINTER")
    EnumRfidWorkstationDevicePurpose.DOCUMENT_PRINT -> setOf("DOCUMENT_PRINTER")
    EnumRfidWorkstationDevicePurpose.PASSAGE_READ -> setOf("WAREHOUSE_GATE", "MUSEUM_GATE")
}

internal val UNSUPPORTED_RFID_WORKSTATION_CAPABILITIES = setOf(
    EnumRfidWorkstationCapability.TAG_PRINT,
    EnumRfidWorkstationCapability.DOCUMENT_PRINT,
)

internal fun workstationDeviceBindings(workstationId: String): List<ResultRow> =
    RfidWorkstationDeviceBindings.selectAll().where {
        (RfidWorkstationDeviceBindings.workstationId eq workstationId) and
            (RfidWorkstationDeviceBindings.enabled eq true)
    }.orderBy(
        RfidWorkstationDeviceBindings.isDefault to SortOrder.DESC,
        RfidWorkstationDeviceBindings.priority to SortOrder.ASC,
        RfidWorkstationDeviceBindings.createTime to SortOrder.ASC,
    ).toList()

internal fun resolveWorkstationDevice(
    station: ResultRow,
    purpose: EnumRfidWorkstationDevicePurpose,
    available: (String) -> Boolean = { true },
): String? {
    val workstationId = station[RfidWorkstations.id].value
    workstationDeviceBindings(workstationId)
        .firstOrNull {
            it[RfidWorkstationDeviceBindings.purpose] == purpose && available(it[RfidWorkstationDeviceBindings.deviceId])
        }
        ?.let { return it[RfidWorkstationDeviceBindings.deviceId] }
    return null
}

internal fun encodeCapabilities(capabilities: Set<EnumRfidWorkstationCapability>): String =
    JsonUtils.toJson(capabilities.sortedBy(EnumRfidWorkstationCapability::ordinal).map(EnumRfidWorkstationCapability::name))
        ?: "[]"
