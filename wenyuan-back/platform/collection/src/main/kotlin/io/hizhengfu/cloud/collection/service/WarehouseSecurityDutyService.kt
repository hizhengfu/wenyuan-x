package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

/** 每个库房/库区独立排班。原件追加保存，不把应急联系人或巡库日记计作值守。 */
@Component
@Transactional
class WarehouseSecurityDutyService(
    private val scope: CollectionScopeResolver,
    private val users: CollectionResponsibleUserService,
    private val archives: CollectionPermanentArchiveService,
) {
    companion object { const val BIZ = "COLLECTION_SECURITY_DUTY" }
    private fun fail(message: String): Nothing = throw BadRequestException(message)
    private fun access(id: String, write: Boolean, managing: Boolean = false): Pair<WarehouseDao, SecurityUser> {
        var warehouse = WarehouseDao.findById(id) ?: fail("库房不存在")
        fun authorize() = CollectionCurrentOperationAccess.require(scope, warehouse.orgId, write,
            if (write) setOf(CollectionConst.WAREHOUSE_MANAGE) else setOf(CollectionConst.SELECT, CollectionConst.WAREHOUSE_MANAGE),
            "库区安全值班", if (managing) emptySet() else setOf(CollectionConst.ROLE_SECURITY))
        var actor = authorize()
        if (write) {
            warehouse = CollectionLocationMutation.reference(warehouse.orgId, id) ?: fail("库房不存在")
            actor = authorize()
        }
        if (warehouse.sceneType != EnumLocationScene.STORAGE || warehouse.type !in setOf(EnumWarehouseType.WAREHOUSE, EnumWarehouseType.AREA))
            fail("安全值班须对应实际库房或库区")
        return warehouse to actor
    }
    private fun rows(warehouse: WarehouseDao) = CollectionPermanentArchiveDao.find {
        (CollectionPermanentArchives.bizType eq BIZ) and (CollectionPermanentArchives.bizId eq warehouse.id.value)
    }.toList().onEach {
        if (it.orgId != warehouse.orgId || it.digest != CollectionCareEvidence.digest(it.snapshot)) fail("值班原件所属馆或摘要异常")
    }.sortedWith(compareBy({ it.createTime }, { it.id.value }))
    private fun <T: Any> record(row: CollectionPermanentArchiveDao, type: Class<T>,verifyContent:Boolean=true): WarehouseSecurityDutyRecord<T> {
        val value = JsonUtils.fromJson(row.snapshot, type) ?: fail("值班原件无法解析")
        val files = when (value) {
            is WarehouseSecurityShiftRequest -> value.fileIds
            is WarehouseSecurityAttendanceRequest -> value.fileIds
            is WarehouseSecurityCancellationRequest -> value.fileIds
            is WarehouseSecurityHandoverRequest -> value.fileIds
            else -> fail("未知值班原件类型")
        }
        archives.verifyExisting(row, row.orgId, BIZ, row.bizId, row.phase, value, files,verifyContent=verifyContent)
        return WarehouseSecurityDutyRecord(row.id.value, row.createBy ?: fail("值班原件缺少办理人员"), row.createTime, value)
    }
    private fun views(warehouse: WarehouseDao,verifyContent:Boolean=true): List<WarehouseSecurityShiftView> {
        val records = rows(warehouse)
        return records.filter { it.phase.startsWith("S_") }.map { shift ->
            val id = shift.id.value
            WarehouseSecurityShiftView(record(shift, WarehouseSecurityShiftRequest::class.java,verifyContent),
                records.singleOrNull { it.phase.startsWith("C_${id}_") }?.let { record(it, WarehouseSecurityCancellationRequest::class.java,verifyContent) },
                records.filter { it.phase.startsWith("A_${id}_") }.map { record(it, WarehouseSecurityAttendanceRequest::class.java,verifyContent) },
                records.filter { it.phase.startsWith("H_${id}_") }.map { record(it, WarehouseSecurityHandoverRequest::class.java,verifyContent) })
        }
    }
    fun get(id: String, day: LocalDate): Map<String, Any> {
        val (warehouse, _) = access(id, false)
        val shifts = views(warehouse,verifyContent=false)
        val evidence = archives.readForDisplay(BIZ,id,warehouse.orgId)
        val coverage = shifts.filter { it.cancellation == null }.map { shift ->
            val actual = shift.attendance.lastOrNull()?.value
            WarehouseSecurityDutyCoverageSource(shift.shift.value.planned, actual?.startedAt, actual?.endedAt)
        }
        val currentHandovers = shifts.flatMap { incoming -> incoming.handovers.filter { receipt ->
            val outgoing = shifts.singleOrNull { it.shift.id == receipt.value.outgoingShiftId }
            receipt.value.incomingAttendanceId == incoming.attendance.lastOrNull()?.id &&
                receipt.value.outgoingAttendanceId == outgoing?.attendance?.lastOrNull()?.id
        } }.map { it.id }
        return mapOf("evidenceErrors" to evidence.issues,"shifts" to shifts, "currentHandoverIds" to currentHandovers,
            "coverage" to WarehouseSecurityDutyCoveragePolicy.assess(day, coverage, LocalDateTime.now()))
    }
    /** 只允许从本库区已核实的值班原件进入下载，不能将任意文件ID当作业务材料。 */
    fun requireOriginal(id: String, fileId: String) {
        val (warehouse, _) = access(id, false)
        views(warehouse) // 核对全部值班原件的快照和附件身份。
        val archiveIds = rows(warehouse).map { it.id.value }.toSet()
        val linked = CollectionPermanentArchiveFileDao.find {
            (CollectionPermanentArchiveFiles.fileId eq fileId) and (CollectionPermanentArchiveFiles.bizId eq id)
        }.any { it.orgId == warehouse.orgId && it.archiveId in archiveIds }
        if (!linked) fail("该文件不是本库区已归档的值班原件")
    }
    private fun key(value: String) {
        // 阶段字段64字符：前缀、雪花ID和请求ID合计必须在范围内。
        if (!Regex("[A-Za-z0-9-]{8,36}").matches(value)) fail("请使用8至36位唯一请求编号")
    }
    private fun save(warehouse: WarehouseDao, actor: SecurityUser, phase: String, value: Any, files: String, check: () -> Unit): String {
        if (files.isBlank()) fail("请上传实际签署的值班原件")
        check()
        val original = rows(warehouse).singleOrNull { it.phase == phase }
        if (original != null) {
            if (original.createBy != actor.id) fail("请求编号已由其他人员使用")
            archives.verifyExisting(original, warehouse.orgId, BIZ, warehouse.id.value, phase, value, files)
            return original.id.value
        }
        val result = archives.seal(warehouse.orgId, BIZ, warehouse.id.value, phase, value, files)
        // 归档涉及文件访问，结束前再次读取当前身份及职责。
        check()
        return result
    }
    fun schedule(id: String, value: WarehouseSecurityShiftRequest): String {
        key(value.requestId)
        val (warehouse, actor) = access(id, true, managing = true)
        if (value.responsibility.isBlank()) fail("请明确本班次库区安全责任范围")
        WarehouseSecurityDutyCoveragePolicy.assess(value.planned.from.toLocalDate(), listOf(WarehouseSecurityDutyCoverageSource(value.planned)), LocalDateTime.now())
        return save(warehouse, actor, "S_${value.requestId}", value, value.fileIds) {
            access(id, false, managing = true)
            CollectionCurrentOperationAccess.require(scope, warehouse.orgId, true, setOf(CollectionConst.WAREHOUSE_MANAGE), "值班排班")
            users.requireRole(value.officerId, warehouse.orgId, CollectionConst.ROLE_SECURITY)
        }
    }
    fun cancel(id: String, shiftId: String, value: WarehouseSecurityCancellationRequest): String {
        key(value.requestId)
        val (warehouse, actor) = access(id, true, managing = true)
        val shift = views(warehouse).singleOrNull { it.shift.id == shiftId } ?: fail("班次不存在")
        if (shift.attendance.isNotEmpty()) fail("已有实际值守记录，不能撤销班次或隐藏值守历史")
        if (value.reason.isBlank()) fail("请说明取消排班的原因")
        if (shift.cancellation != null && shift.cancellation.value != value) fail("班次已经取消")
        return save(warehouse, actor, "C_${shiftId}_${value.requestId}", value, value.fileIds) {
            CollectionCurrentOperationAccess.require(scope, warehouse.orgId, true, setOf(CollectionConst.WAREHOUSE_MANAGE), "取消排班")
        }
    }
    fun attend(id: String, shiftId: String, value: WarehouseSecurityAttendanceRequest): String {
        key(value.requestId)
        val (warehouse, actor) = access(id, true)
        val shift = views(warehouse).singleOrNull { it.shift.id == shiftId } ?: fail("班次不存在")
        if (shift.cancellation != null) fail("已取消的班次不能登记值守")
        if (value.situation.isBlank() || value.handoverNotes.isBlank()) fail("请如实填写安全情况与交接事项，无异常也须明确记录")
        WarehouseSecurityDutyCoveragePolicy.assess(value.startedAt.toLocalDate(), listOf(WarehouseSecurityDutyCoverageSource(shift.shift.value.planned, value.startedAt, value.endedAt)), LocalDateTime.now())
        val phase = "A_${shiftId}_${value.requestId}"
        if (rows(warehouse).none { it.phase == phase }) {
            val previous = shift.attendance.lastOrNull()
            if (value.previousId != previous?.id) fail("值守记录已更新，请重新核对最新记录")
            if (previous != null && value.correctionReason.isNullOrBlank()) fail("补充或更正值守须说明原因，原件将保留")
            if (previous?.value?.endedAt != null && value.endedAt == null) fail("已结束值守不能更正为仍在值守")
        }
        return save(warehouse, actor, phase, value, value.fileIds) {
            val (_, current) = access(id, false)
            if (current.id != actor.id || current.id != shift.shift.value.officerId) fail("须由本班实际值守人员本人登记")
            users.requireRole(current.id, warehouse.orgId, CollectionConst.ROLE_SECURITY)
            CollectionCurrentOperationAccess.require(scope, warehouse.orgId, true, emptySet(), "值守登记", setOf(CollectionConst.ROLE_SECURITY))
        }
    }
    fun handover(id: String, incomingShiftId: String, value: WarehouseSecurityHandoverRequest): String {
        key(value.requestId)
        val (warehouse, actor) = access(id, true)
        val shifts = views(warehouse)
        val incoming = shifts.singleOrNull { it.shift.id == incomingShiftId } ?: fail("接班班次不存在")
        val outgoing = shifts.singleOrNull { it.shift.id == value.outgoingShiftId } ?: fail("交班班次不存在")
        if (incomingShiftId == value.outgoingShiftId || incoming.shift.value.officerId == outgoing.shift.value.officerId) fail("交接须由不同班次的实际交班和接班人员办理")
        val from = outgoing.attendance.lastOrNull() ?: fail("缺少交班值守记录")
        val to = incoming.attendance.lastOrNull() ?: fail("缺少接班值守记录")
        if (from.id != value.outgoingAttendanceId || to.id != value.incomingAttendanceId) fail("交接依据已经更新，请重新核对双方最新值守原件")
        if (from.value.endedAt == null || value.handedOverAt.isBefore(from.value.startedAt) || value.handedOverAt.isAfter(from.value.endedAt) ||
            value.handedOverAt.isBefore(to.value.startedAt) || value.handedOverAt.isAfter(to.value.endedAt ?: LocalDateTime.now())) fail("交接时间须处于双方实际值守时段内；无法当面交接的空档须如实补查")
        if (value.opinion.isBlank()) fail("请记录接班人实际核对事项及遗留问题")
        return save(warehouse, actor, "H_${incomingShiftId}_${value.requestId}", value, value.fileIds) {
            val (_, current) = access(id, false)
            if (current.id != actor.id || current.id != incoming.shift.value.officerId) fail("须由接班人员本人核对交班原件并登记")
            users.requireRole(current.id, warehouse.orgId, CollectionConst.ROLE_SECURITY)
            CollectionCurrentOperationAccess.require(scope, warehouse.orgId, true, emptySet(), "值班交接", setOf(CollectionConst.ROLE_SECURITY))
        }
    }
}
