package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntity
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntityClass
import io.hizhengfu.cloud.collection.common.OrgAuditUIDTable
import io.hizhengfu.cloud.collection.domain.EnumVoucherStatus
import jakarta.validation.constraints.NotBlank
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.javatime.date
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 入馆凭证：审批通过的征集单按批次组凭证，execute 批量登账（登账时机的唯一入口）。
 * 仅 DRAFT（未入藏）可改可删；REGISTERED 后凭证号回写藏品总账 entryVoucherNo。
 */
object EntryVouchers : OrgAuditUIDTable("col_entry_voucher") {
    val voucherNo = varchar("voucher_no", 64).comment("入馆凭证号")
    val acquisitionIds = text("acquisition_ids").comment("征集单 id，逗号分隔")
    val verifiedAcquisitionIds = text("verified_acquisition_ids").nullable().comment("已逐件逐项核对的征集单 id，逗号分隔")
    val basis = varchar("basis", 500).nullable().comment("入藏依据")
    val entryDate = date("entry_date").nullable().comment("入馆日期")
    val handoverUser = varchar("handover_user", 64).nullable().comment("移交人")
    val receiverUser = varchar("receiver_user", 64).nullable().comment("接收人")
    val custodian = varchar("custodian", 64).nullable().comment("保管员")
    val warehouseId = varchar("warehouse_id", 64).nullable().index(shortIndexName("warehouse_id")).comment("入馆目标库房/库位")
    val voucherStatus = enumeration<EnumVoucherStatus>("voucher_status")
        .default(EnumVoucherStatus.DRAFT).comment("未入藏/已入藏")
    val attachmentIds = text("attachment_ids").nullable().comment("签字扫描件等附件 fileId，逗号分隔")
    val remark = varchar("remark", 2000).nullable()

    init {
        uniqueIndex(shortIndexName("org_id_voucher_no", unique = true), orgId, voucherNo)
    }
}

class EntryVoucherDao(id: EntityID<String>) : OrgAuditUIDEntity(id, EntryVouchers) {
    companion object : OrgAuditUIDEntityClass<EntryVoucherDao>(EntryVouchers)

    var voucherNo by EntryVouchers.voucherNo
    var acquisitionIds by EntryVouchers.acquisitionIds
    var verifiedAcquisitionIds by EntryVouchers.verifiedAcquisitionIds
    var basis by EntryVouchers.basis
    var entryDate by EntryVouchers.entryDate
    var handoverUser by EntryVouchers.handoverUser
    var receiverUser by EntryVouchers.receiverUser
    var custodian by EntryVouchers.custodian
    var warehouseId by EntryVouchers.warehouseId
    var voucherStatus by EntryVouchers.voucherStatus
    var attachmentIds by EntryVouchers.attachmentIds
    var remark by EntryVouchers.remark

    fun toModel() = EntryVoucherVo(
        id = id.value,
        orgId = orgId,
        voucherNo = voucherNo,
        acquisitionIds = acquisitionIds,
        verifiedAcquisitionIds = verifiedAcquisitionIds,
        basis = basis,
        entryDate = entryDate,
        handoverUser = handoverUser,
        receiverUser = receiverUser,
        custodian = custodian,
        warehouseId = warehouseId,
        voucherStatus = voucherStatus,
        attachmentIds = attachmentIds,
        remark = remark,
        createBy = createBy,
        createTime = createTime,
    )
}

data class EntryVoucherVo(
    val id: String? = null,
    val orgId: String? = null,
    val voucherNo: String? = null,
    @field:NotBlank val acquisitionIds: String? = null,
    val verifiedAcquisitionIds: String? = null,
    val basis: String? = null,
    val entryDate: LocalDate? = null,
    val handoverUser: String? = null,
    val receiverUser: String? = null,
    val custodian: String? = null,
    val warehouseId: String? = null,
    var warehouseName: String? = null,
    val voucherStatus: EnumVoucherStatus? = null,
    val attachmentIds: String? = null,
    val remark: String? = null,
    val createBy: String? = null,
    val createTime: LocalDateTime? = null,
    /** 凭证内征集单摘要（enrich 注入） */
    var acquisitionSummaries: List<AcquisitionVo> = emptyList(),
    var movementIds: List<String> = emptyList(),
)
