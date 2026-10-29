package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.FlowUIDEntity
import io.hizhengfu.cloud.autoconfigure.exposed.FlowUIDEntityClass
import io.hizhengfu.cloud.autoconfigure.exposed.FlowUIDTable
import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.json.json
import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.domain.*
import org.jetbrains.exposed.v1.javatime.datetime
import java.time.LocalDateTime

/** 人员进入藏品库房前的申请、审批及实际进出登记。 */
object WarehouseEntryApplications : FlowUIDTable("col_warehouse_entry_apply"), io.hizhengfu.cloud.autoconfigure.exposed.StableBusinessAuditTable {
    val orgId = varchar("org_id", 64).index(shortIndexName("org_id")).comment("收藏单位")
    val applicationNo = varchar("application_no", 64).comment("申请编号")
    val applicantId = varchar("applicant_id", 64).index(shortIndexName("applicant_id")).comment("申请人")
    val entrantIds = text("entrant_ids").comment("入库人员系统用户ID，逗号分隔")
    val externalEntrants = varchar("external_entrants", 1000).nullable().comment("外部人员姓名及单位")
    val warehouseIds = text("warehouse_ids").comment("拟进入库房ID，逗号分隔")
    val purpose = varchar("purpose", 1000).comment("入库事由")
    val plannedStart = datetime("planned_start").comment("计划进入时间")
    val plannedEnd = datetime("planned_end").comment("计划离开时间")
    val escortUserId = varchar("escort_user_id", 64).nullable().comment("陪同人员")
    val attachmentIds = text("attachment_ids").nullable().comment("申请材料fileId，逗号分隔")
    val approvedAt = datetime("approved_at").nullable()
    val actualEntryAt = datetime("actual_entry_at").nullable()
    val actualExitAt = datetime("actual_exit_at").nullable()
    val checkInBy = varchar("check_in_by", 64).nullable()
    val checkOutBy = varchar("check_out_by", 64).nullable()
    val accessRemark = varchar("access_remark", 1000).nullable().comment("门禁核验备注")

    val dossier = json("statutory_dossier", { JsonUtils.toJson(it)!! }, { JsonUtils.fromJson(it, WarehouseEntryDossierVo::class.java)!! }).nullable()
    val baseline = json("staffing_baseline", { JsonUtils.toJson(it)!! }, { JsonUtils.fromJson(it, WarehouseEntryBaselineVo::class.java)!! }).nullable()
    val entryRecord = json("actual_entry_record", { JsonUtils.toJson(it)!! }, { JsonUtils.fromJson(it, WarehouseAttendanceVo::class.java)!! }).nullable()
    val exitRecord = json("actual_exit_record", { JsonUtils.toJson(it)!! }, { JsonUtils.fromJson(it, WarehouseAttendanceVo::class.java)!! }).nullable()

    init {
        uniqueIndex(shortIndexName("org_id_application_no", unique = true), orgId, applicationNo)
        index(shortIndexName("org_id_planned_start"), false, orgId, plannedStart)
    }
}

class WarehouseEntryApplicationDao(id: EntityID<String>) : FlowUIDEntity(id, WarehouseEntryApplications) {
    companion object : FlowUIDEntityClass<WarehouseEntryApplicationDao>(WarehouseEntryApplications)

    var orgId by WarehouseEntryApplications.orgId
    var applicationNo by WarehouseEntryApplications.applicationNo
    var applicantId by WarehouseEntryApplications.applicantId
    var entrantIds by WarehouseEntryApplications.entrantIds
    var externalEntrants by WarehouseEntryApplications.externalEntrants
    var warehouseIds by WarehouseEntryApplications.warehouseIds
    var purpose by WarehouseEntryApplications.purpose
    var plannedStart by WarehouseEntryApplications.plannedStart
    var plannedEnd by WarehouseEntryApplications.plannedEnd
    var escortUserId by WarehouseEntryApplications.escortUserId
    var attachmentIds by WarehouseEntryApplications.attachmentIds
    var approvedAt by WarehouseEntryApplications.approvedAt
    var actualEntryAt by WarehouseEntryApplications.actualEntryAt
    var actualExitAt by WarehouseEntryApplications.actualExitAt
    var checkInBy by WarehouseEntryApplications.checkInBy
    var checkOutBy by WarehouseEntryApplications.checkOutBy
    var accessRemark by WarehouseEntryApplications.accessRemark

    var dossier by WarehouseEntryApplications.dossier
    var baseline by WarehouseEntryApplications.baseline
    var entryRecord by WarehouseEntryApplications.entryRecord
    var exitRecord by WarehouseEntryApplications.exitRecord

    fun toModel() = WarehouseEntryApplicationVo(
        id = id.value,
        dossier = dossier,
        baseline = baseline,
        entryRecord = entryRecord,
        exitRecord = exitRecord,

        orgId = orgId,
        applicationNo = applicationNo,
        applicantId = applicantId,
        entrantIds = entrantIds,
        externalEntrants = externalEntrants,
        warehouseIds = warehouseIds,
        purpose = purpose,
        plannedStart = plannedStart,
        plannedEnd = plannedEnd,
        escortUserId = escortUserId,
        attachmentIds = attachmentIds,
        approvedAt = approvedAt,
        actualEntryAt = actualEntryAt,
        actualExitAt = actualExitAt,
        checkInBy = checkInBy,
        checkOutBy = checkOutBy,
        accessRemark = accessRemark,
        flowStatus = flowStatus,
        flowInstanceId = flowInstanceId,
        createBy = createBy,
        createTime = createTime,
    )
}

data class WarehouseEntryApplicationVo(
    val id: String? = null,
    val dossier: WarehouseEntryDossierVo? = null,
    val baseline: WarehouseEntryBaselineVo? = null,
    val entryRecord: WarehouseAttendanceVo? = null,
    val exitRecord: WarehouseAttendanceVo? = null,
    val archives: List<Map<String, Any?>> = emptyList(),
    @get:com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY)
    val evidenceErrors: List<String> = emptyList(),
    val orgId: String? = null,
    val applicationNo: String? = null,
    val applicantId: String? = null,
    var applicantName: String? = null,
    val entrantIds: String? = null,
    var entrantNames: List<String> = emptyList(),
    val externalEntrants: String? = null,
    @field:NotBlank val warehouseIds: String? = null,
    var warehouseNames: List<String> = emptyList(),
    @field:NotBlank val purpose: String? = null,
    @field:NotNull val plannedStart: LocalDateTime? = null,
    @field:NotNull val plannedEnd: LocalDateTime? = null,
    val escortUserId: String? = null,
    var escortUserName: String? = null,
    val attachmentIds: String? = null,
    val approvedAt: LocalDateTime? = null,
    val actualEntryAt: LocalDateTime? = null,
    val actualExitAt: LocalDateTime? = null,
    val checkInBy: String? = null,
    val checkOutBy: String? = null,
    val accessRemark: String? = null,
    val flowStatus: EnumFlowStatus? = null,
    val flowInstanceId: String? = null,
    val createBy: String? = null,
    val createTime: LocalDateTime? = null,
)

data class WarehouseEntryCheckVo(
    val occurredAt: LocalDateTime? = null,
    val remark: String? = null,
)
