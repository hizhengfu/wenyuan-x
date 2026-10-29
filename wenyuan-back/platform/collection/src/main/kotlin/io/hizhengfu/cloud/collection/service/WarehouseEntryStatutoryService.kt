package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.flow.dao.FlowInstanceLogDao
import io.hizhengfu.cloud.flow.dao.FlowInstanceLogs
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
@Transactional
class WarehouseEntryStatutoryService(
    private val staff: WarehouseStaffingService,
    private val users: CollectionResponsibleUserService,
    private val archives: CollectionPermanentArchiveService,
) {
    fun requirePrepared(e: WarehouseEntryApplicationDao) {
        val d=e.dossier ?: fail("请补齐本次实际入库类别、人员及陪同方案")
        val baseline=staff.baselineForWrite(e.orgId,splitCollectionIds(e.warehouseIds))
        if(e.baseline!=baseline)fail("库管任职名单已经变化或尚未核实，请重新核对并提交入库方案")
        if(d.identityFileIds.isBlank())fail("入库须核对馆内和外来人员身份材料")
        if(d.keeperUserId.isBlank() || baseline.items.any { it.staffing.keeperUserId!=d.keeperUserId })fail("须由本次库房实际保管部门负责人确认")
        users.requireRole(d.keeperUserId,e.orgId,CollectionConst.ROLE_STORAGE_HEAD)
        val entrants=splitCollectionIds(e.entrantIds).toSet()
        if(entrants.isEmpty() && d.externalNames.isEmpty())fail("入库人员不能为空")
        if(d.externalNames.any(String::isBlank) || d.externalNames.map(String::trim).distinct().size!=d.externalNames.size)fail("外来人员须逐人填写真实姓名与单位，不能重复或留空")
        if(entrants.intersect(d.escortUserIds).isNotEmpty())fail("被陪同人与陪同人员不能重复计数")
        if(baseline.items.any { it.staffing.assignedAt.isAfter(e.plannedStart) })fail("批准时段不能早于实际库管任职")
        when(d.kind) {
            EnumWarehouseEntryKind.KEEPERS -> {
                if(entrants.size<2 || d.externalNames.isNotEmpty() || d.escortUserIds.isNotEmpty())fail("库管共同入库须至少两名实际库管，不得混入未受陪同的非库管")
                entrants.forEach { staff.requireKeeper(it,e.orgId,splitCollectionIds(e.warehouseIds)) }
            }
            EnumWarehouseEntryKind.VISITORS -> {
                if(d.escortUserIds.size<2)fail("非库房管理人员须由两名实际库管陪同入库")
                d.escortUserIds.forEach { staff.requireKeeper(it,e.orgId,splitCollectionIds(e.warehouseIds)) }
                entrants.forEach { users.requireMuseumMember(it,e.orgId) }
            }
            EnumWarehouseEntryKind.STAFF_SHORTAGE -> {
                if(entrants.size!=1 || d.externalNames.isNotEmpty() || d.escortUserIds.isEmpty())fail("人员不足例外只适用于一名实际库管与主要负责人指定的馆内其他人员共同入库")
                if(baseline.items.any { it.staffing.keeperUserIds.size>=2 })fail("该库房现有库管不少于两人，不能使用人员不足例外")
                entrants.forEach { staff.requireKeeper(it,e.orgId,splitCollectionIds(e.warehouseIds)) }
                d.escortUserIds.forEach { person ->
                    users.requireMuseumMember(person,e.orgId)
                    if(baseline.items.any { person in it.staffing.keeperUserIds })fail("特殊陪同须为本馆其他人员，不能重复计算库管")
                }
                if(d.shortageReason.isNullOrBlank() || d.shortageFileIds.isNullOrBlank() || d.leaderUserId.isNullOrBlank())fail("人员不足须说明真实情况、提供依据并由主要负责人本人指定陪同")
                if(d.leaderUserId==d.keeperUserId)fail("特殊陪同指定与保管确认须分别由实际负责人完成")
                users.requireRole(d.leaderUserId,e.orgId,CollectionConst.ROLE_DIRECTOR)
            }
        }
    }
    fun actors(e: WarehouseEntryApplicationDao): Map<String,String> {
        val d=e.dossier ?: fail("缺少入库方案")
        return linkedMapOf("keeper" to d.keeperUserId).also { if(d.kind==EnumWarehouseEntryKind.STAFF_SHORTAGE)it["leader"]=d.leaderUserId ?: fail("缺少主要负责人") }
    }
    fun requireActor(e: WarehouseEntryApplicationDao,node: String?,operator: String?,comment: String?) {
        val expected=actors(e)[node] ?: return
        if(operator!=expected || comment.isNullOrBlank())fail("入库确认和特殊指定须由方案中的实际责任人本人填写意见")
        users.requireRole(expected,e.orgId,if(node=="leader")CollectionConst.ROLE_DIRECTOR else CollectionConst.ROLE_STORAGE_HEAD)
    }
    fun approvals(e: WarehouseEntryApplicationDao): List<FlowInstanceLogDao> {
        val instance=e.flowInstanceId ?: fail("缺少入库审批实例")
        val logs=io.hizhengfu.cloud.flow.service.FlowApprovalHistory.current(instance)
        val selected=actors(e).map { (node,user) -> logs.filter { it.nodeKey==node && it.operator==user && !it.taskId.isNullOrBlank() && !it.comment.isNullOrBlank() }.lastOrNull() ?: fail("缺少本轮实际保管确认或主要负责人指定；旧流程 PASS 不能代替") }
        io.hizhengfu.cloud.flow.service.FlowApprovalHistory.requireOrdered(selected)
        return selected
    }
    fun confirm(e: WarehouseEntryApplicationDao) {
        requirePrepared(e)
        val logs=approvals(e)
        archives.seal(e.orgId,CollectionConst.BIZ_WAREHOUSE_ENTRY,e.id.value,phase(logs.last().taskId!!),mapOf("proposal" to proposal(e),"approvals" to logs.map { it.toVo() }),files(e))
        e.approvedAt=logs.last().createTime
    }
    fun requireApproved(e: WarehouseEntryApplicationDao) {
        requirePrepared(e)
        val last=approvals(e).last()
        if(CollectionPermanentArchiveDao.find { (CollectionPermanentArchives.bizType eq CollectionConst.BIZ_WAREHOUSE_ENTRY) and (CollectionPermanentArchives.bizId eq e.id.value) and (CollectionPermanentArchives.phase eq phase(last.taskId!!)) }.empty())fail("缺少本轮入库批准的永久档案")
        archives.seal(e.orgId,CollectionConst.BIZ_WAREHOUSE_ENTRY,e.id.value,phase(last.taskId!!),mapOf("proposal" to proposal(e),"approvals" to approvals(e).map { it.toVo() }),files(e))
    }
    fun attendance(e: WarehouseEntryApplicationDao, value: WarehouseAttendanceVo, exit: Boolean) {
        val d=e.dossier ?: fail("旧记录须先核实真实入库人员与陪同依据")
        if(value.occurredAt.isAfter(LocalDateTime.now()))fail("实际进出时间不得填写未来时间")
        if(value.entrantUserIds!=splitCollectionIds(e.entrantIds).toSet() || value.escortUserIds!=d.escortUserIds || value.externalNames!=d.externalNames)fail("实际核验须逐人覆盖批准名单及全部陪同人员；人员变化应重新办理申请")
        if(value.identityChecked!=true || value.signedRecordFileIds.isBlank())fail("须核实实际人员身份并提供真实签署的人员出入库登记表")
        if(value.continuousAccompaniment==null)fail("请如实确认共同进入或全程陪同情况")
        if(!exit && value.continuousAccompaniment!=true)fail("人员未实际共同到场，不得登记为符合入库条件")
        if(exit && value.continuousAccompaniment==false && (value.remark.isNullOrBlank() || value.exceptionMeasures.isNullOrBlank() || value.exceptionReportFileIds.isNullOrBlank()))fail("陪同中断须如实记载异常、即时措施及报告，不得隐藏异常离库")
        if(!exit && (e.approvedAt==null || value.occurredAt.isBefore(e.approvedAt)))fail("实际入库须在本轮批准之后")
        archives.seal(e.orgId,CollectionConst.BIZ_WAREHOUSE_ENTRY,e.id.value,if(exit)"CHECKED_OUT" else "CHECKED_IN",value,listOfNotNull(value.signedRecordFileIds,value.exceptionReportFileIds).joinToString(","))
    }
    fun proposal(e: WarehouseEntryApplicationDao): Map<String,Any?> = mapOf("id" to e.id.value,"applicationNo" to e.applicationNo,"entrantIds" to e.entrantIds,"externalEntrants" to e.externalEntrants,"warehouseIds" to e.warehouseIds,"purpose" to e.purpose,"plannedStart" to e.plannedStart,"plannedEnd" to e.plannedEnd,"dossier" to e.dossier,"baseline" to e.baseline,"requiresDirector" to (e.dossier?.kind==EnumWarehouseEntryKind.STAFF_SHORTAGE))
    fun files(e: WarehouseEntryApplicationDao): String? = listOfNotNull(e.attachmentIds,e.dossier?.identityFileIds,e.dossier?.shortageFileIds).joinToString(",").ifBlank { null }
    private fun phase(task: String)="APPROVED_${task.takeLast(45)}"
    private fun fail(message: String): Nothing = throw BadRequestException(message)
}
