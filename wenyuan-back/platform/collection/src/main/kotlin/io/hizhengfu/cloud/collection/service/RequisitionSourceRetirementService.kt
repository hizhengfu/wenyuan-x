package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SecurityUtils
import io.hizhengfu.cloud.flow.dao.FlowInstanceDao
import io.hizhengfu.cloud.flow.service.FlowInstanceService
import org.jetbrains.exposed.v1.core.*
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** 跨模块只共享来源重建原件格式，不向普通提用HTTP入口开放来源修改。 */
class RequisitionSourceRetirementEvidence(private val archives: CollectionPermanentArchiveService? = null) {
    companion object {
        const val ROOT_BIZ = "COLLECTION_SOURCE_RESPONSIBILITY_2026"
        const val PHASE = "SOURCE_RETIRED"
    }
    private fun fail(): Nothing = throw BadRequestException("提用终止与来源业务重建原件不一致，请核对原件及新旧项目")
    private fun json(v: Any) = JsonUtils.toJson(v)!!
    private fun references(a:CollectionPermanentArchiveDao,raw:String?) {
        if(archives!=null) { archives.requireFileReferences(a,raw);return }
        val expected=BusinessAttachmentBindingPlanner.plan(a.bizId,"原件",raw).map {it.fileId}.toSet()
        val rows=CollectionPermanentArchiveFileDao.find {CollectionPermanentArchiveFiles.archiveId eq a.id.value}.toList()
        if(rows.map {it.fileId}.toSet()!=expected || rows.any {it.orgId!=a.orgId || it.bizId!=a.bizId || io.hizhengfu.cloud.sys.file.dao.FileDao.findById(it.fileId)==null})fail()
    }
    fun root(value: RequisitionSourceRetirementRecord): CollectionPermanentArchiveDao {
        val c=value.context
        val root=CollectionPermanentArchiveDao.findById(value.rootArchiveId) ?: fail()
        if(root.bizType!=ROOT_BIZ || root.bizId!=c.sourceId || root.phase!="REPLACED" || root.digest!=value.rootArchiveDigest || root.digest!=CollectionCareEvidence.digest(root.snapshot))fail()
        val context=JsonUtils.mapper.treeToValue(JsonUtils.mapper.readTree(root.snapshot).path("requisitionRetirement"),RequisitionRetirementContext::class.java)
        if(context!=c || c.sourceId==c.replacementSourceId || c.sourceBizType.isBlank())fail()
        val peer=CollectionPermanentArchiveDao.find {(CollectionPermanentArchives.bizType eq ROOT_BIZ) and (CollectionPermanentArchives.bizId eq c.replacementSourceId) and (CollectionPermanentArchives.phase eq "CREATED")}.singleOrNull() ?: fail()
        if(peer.orgId!=root.orgId || peer.snapshot!=root.snapshot || peer.digest!=root.digest)fail()
        listOf(root,peer).forEach {references(it,"${c.appointmentFileIds},${c.withdrawalFileIds},${c.originalFileIds.joinToString(",")}")}
        return root
    }
    fun verify(e: RequisitionDao) {
        try {
            val row=CollectionPermanentArchiveDao.find {(CollectionPermanentArchives.bizType eq CollectionConst.BIZ_REQUISITION) and (CollectionPermanentArchives.bizId eq e.id.value) and (CollectionPermanentArchives.phase eq PHASE)}.singleOrNull()
            val value=e.sourceRetirement
            if((row==null)!=(value==null))fail()
            if(value==null)return
            val root=root(value);val c=value.context
            if(root.orgId!=e.orgId || e.sourceBizType!=c.sourceBizType || e.sourceBizId!=c.sourceId || row!!.orgId!=e.orgId || row.digest!=CollectionCareEvidence.digest(row.snapshot) || JsonUtils.mapper.readTree(row.snapshot)!=JsonUtils.mapper.readTree(json(value)))fail()
            val original=c.requisitions[e.id.value] ?: fail()
            if(original.orgId!=e.orgId || original.requisitionId!=e.id.value || original.originalInstanceId!=e.flowInstanceId || JsonUtils.mapper.readTree(original.proposal)!=JsonUtils.mapper.readTree(json(requisitionProposal(e))))fail()
            if(e.outExecuted || e.outExecutedTime!=null || e.handover!=null || e.returned || e.returnDate!=null ||
                e.flowStatus!=if(original.originalFlowStatus==EnumFlowStatus.AUDIT)EnumFlowStatus.CANCEL else original.originalFlowStatus)fail()
            e.flowInstanceId?.let {id ->
                val flow=FlowInstanceDao.findById(id) ?: fail()
                val expected=when(original.originalFlowStatus){EnumFlowStatus.AUDIT,EnumFlowStatus.CANCEL->"CANCELED";EnumFlowStatus.PASS->"COMPLETED";EnumFlowStatus.REJECT->"REJECTED";else->fail()}
                if(flow.businessType!=CollectionConst.BIZ_REQUISITION || flow.businessKey!=e.id.value || flow.status!=expected)fail()
            }
            for((id,digest) in original.archiveDigests) {
                val a=CollectionPermanentArchiveDao.findById(id) ?: fail()
                if(a.orgId!=e.orgId || a.bizType!=CollectionConst.BIZ_REQUISITION || a.bizId!=e.id.value || a.digest!=digest || digest!=CollectionCareEvidence.digest(a.snapshot))fail()
                references(a,original.archiveFileIds[id] ?: fail())
            }
            references(row,"${c.appointmentFileIds},${c.withdrawalFileIds}")
        } catch (_: tools.jackson.core.JacksonException) {fail()}
    }
    fun requireActive(e: RequisitionDao) {
        verify(e)
        if(e.sourceRetirement!=null)throw BadRequestException("来源业务已重建，本提用单后续执行已终止；请在新来源重新办理提用审批")
    }
}

@Component
@Transactional
class RequisitionSourceRetirementService(
    private val archives: CollectionPermanentArchiveService,
    private val directory: CollectionActorDirectory,
    private val users: CollectionResponsibleUserService,
    private val scope: CollectionScopeResolver,
    private val requisitions: RequisitionService,
    private val statutory: RequisitionStatutoryService,
    private val instances: FlowInstanceService,
) {
    private fun fail(message:String):Nothing=throw BadRequestException(message)
    private fun instance(e:RequisitionDao):FlowInstanceDao? {
        val id=e.flowInstanceId
        if(id==null) {
            if(e.flowStatus !in setOf(EnumFlowStatus.DRAFT,EnumFlowStatus.VOID))fail("提用状态与审批实例不一致")
            return null
        }
        val expected=when(e.flowStatus) {EnumFlowStatus.AUDIT->"RUNNING";EnumFlowStatus.PASS->"COMPLETED";EnumFlowStatus.CANCEL->"CANCELED";EnumFlowStatus.REJECT->"REJECTED";else->fail("本提用审批状态不能认定为未实施")}
        return (FlowInstanceDao.findById(id) ?: fail("原提用审批实例不存在")).also {
            if(it.businessType!=CollectionConst.BIZ_REQUISITION || it.businessKey!=e.id.value || it.parentInstanceId!=null || it.parentTaskId!=null || it.status!=expected)fail("原提用审批与来源、状态不一致")
        }
    }
    @Transactional(noRollbackFor = [BadRequestException::class])
    fun snapshot(e:RequisitionDao):RequisitionSourceSnapshot {
        RequisitionReplacementEvidence(archives).requireActive(e)
        if(e.outExecuted || e.outExecutedTime!=null || e.handover!=null || e.returned || e.returnDate!=null ||
            CollectionPermanentArchiveDao.find {(CollectionPermanentArchives.bizType eq CollectionConst.BIZ_REQUISITION) and (CollectionPermanentArchives.bizId eq e.id.value)}.any {it.phase.startsWith("HANDOVER_") || it.phase=="OUT_EXECUTED"} ||
            StorageMovementDao.find {(StorageMovements.refBizId eq e.id.value) and (StorageMovements.type eq EnumMoveType.OUT)}.any())fail("旧送修单已有实际点交、出库或归还事实，须如实核实实物及后续责任")
        instance(e)
        val rows=if(e.flowInstanceId!=null)RequisitionReplacementEvidence(archives).source(e) else RequisitionReplacementEvidence(archives).rows(e.id.value)
        if(e.flowStatus==EnumFlowStatus.PASS)statutory.requireCurrentApproval(e)
        val fileIds=rows.associate {a->
            if(a.orgId!=e.orgId || a.digest!=CollectionCareEvidence.digest(a.snapshot))fail("原提用归档不完整")
            val ids=CollectionPermanentArchiveFileDao.find {CollectionPermanentArchiveFiles.archiveId eq a.id.value}.map {it.fileId}.sorted().joinToString(",")
            archives.requireFileReferences(a,ids);a.id.value to ids
        }
        return RequisitionSourceSnapshot(e.id.value,e.orgId,e.flowStatus,e.flowInstanceId,JsonUtils.toJson(requisitionProposal(e))!!,rows.associate {it.id.value to it.digest},fileIds)
    }
    fun retire(id:String,value:RequisitionSourceRetirementRecord) {
        val e=RequisitionDao.find {Requisitions.id eq id}.forUpdate().singleOrNull() ?: fail("原提用单不存在")
        val actor=directory.requireActive(SecurityUtils.getUserDetail().id)
        if(actor.permissions.none {it in setOf("ADMIN",CollectionConst.ADMIN,CollectionConst.STORAGE)})fail("须有当前提用业务权限")
        OrgScopedReadPolicy.requireVisible(scope.currentWritableOrgIds(actor),e.orgId,"来源提用终止")
        users.requireRole(actor.id,e.orgId,CollectionConst.ROLE_DIRECTOR)
        val evidence=RequisitionSourceRetirementEvidence(archives)
        if(e.sourceRetirement!=null) {evidence.verify(e);if(e.sourceRetirement==value)return;fail("旧提用终止原件已保存，不能覆盖")}
        val root=evidence.root(value);val c=value.context
        if(c.authorizedBy!=actor.id || root.orgId!=e.orgId || e.sourceBizType!=c.sourceBizType || e.sourceBizId!=c.sourceId || c.requisitions[id]!=snapshot(e))fail("原送修单或来源重建依据已变化")
        if(e.flowStatus==EnumFlowStatus.AUDIT)instances.cancel(e.flowInstanceId!!,actor.id,actor.name,skipPermissionCheck=true)
        requisitions.releaseForResponsibilityReplacement(e)
        e.sourceRetirement=value
        archives.seal(e.orgId,CollectionConst.BIZ_REQUISITION,id,RequisitionSourceRetirementEvidence.PHASE,value,"${c.appointmentFileIds},${c.withdrawalFileIds}")
    }
}
