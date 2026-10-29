package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.CollectionDutySeparationPolicy
import io.hizhengfu.cloud.collection.domain.EnumCollectionStatus
import io.hizhengfu.cloud.collection.domain.EnumExitDestination
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SecurityUtils
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Component
@Transactional
class CollectionAuxiliaryService(private val scope: CollectionScopeResolver,private val binder: BusinessAttachmentBinder,private val archives: CollectionPermanentArchiveService) {
    fun list(orgId: String): List<CollectionAuxiliaryVo> {
        CollectionCurrentOperationAccess.require(scope,orgId,false,setOf(CollectionConst.SELECT,CollectionConst.LEDGER_CORRECT),"辅助账册")
        val rows=CollectionAuxiliaryDao.find { CollectionAuxiliaries.orgId eq orgId }.sortedByDescending { it.createTime }
        rows.forEach { verify(it,verifyContent=false) }
        val handed=InstitutionItemHandoverEvidence.auxiliarySummaries(orgId,rows.map {it.id.value}.toSet())
        return rows.map {it.toModel().copy(institutionHandover=handed[it.id.value])}
    }

    fun detail(orgId:String,id:String):Map<String,Any?> {
        if(listOf(orgId,id).any {it.isBlank() || it.length>100})failOriginal()
        CollectionCurrentOperationAccess.require(scope,orgId,false,setOf(CollectionConst.SELECT,CollectionConst.LEDGER_CORRECT),"辅助账册")
        val row=CollectionAuxiliaryDao.findById(id) ?: failOriginal()
        if(row.orgId!=orgId)failOriginal()
        val archive=verify(row,verifyContent=false)
        val handed=InstitutionItemHandoverEvidence.auxiliarySummaries(orgId,setOf(id))[id]
        return mapOf("record" to row.toModel().copy(institutionHandover=handed),"archiveId" to archive["id"],"files" to archive["files"],"evidenceIssue" to archive["evidenceIssue"])
    }

    fun requireArchivedOriginal(orgId:String,id:String,archiveId:String,fileId:String):String {
        if(listOf(orgId,id,archiveId,fileId).any {it.isBlank() || it.length>100})failOriginal()
        val verified=detail(orgId,id)
        val files=verified["files"] as? List<*> ?: failOriginal()
        if(verified["archiveId"]!=archiveId || files.none {(it as? Map<*,*>)?.get("fileId")==fileId})failOriginal()
        return archiveId
    }

    fun register(vo: CollectionAuxiliaryVo): String {
        requireRegistrar(vo.orgId)
        if (vo.id != null || vo.kind == EnumAuxiliaryKind.LAWFUL_EXIT || vo.originCollectionId != null || vo.originTotalRegNo != null || vo.exitId != null || vo.institutionHandover!=null) {
            throw BadRequestException("正式藏品不得直接改入辅助账，须先依法完成退出；已登记辅助账不允许覆盖")
        }
        return create(vo)
    }

    fun registerExit(entity: DeregistrationDao,receiptFileIds: String): String? {
        val dossier=entity.dossier ?: return null
        if (dossier.destinationType != EnumExitDestination.AUXILIARY_ACCOUNT) return null
        requireRegistrar(entity.orgId)
        val collection=CollectionDao[entity.collectionId]
        if(collection.orgId!=entity.orgId)throw BadRequestException("退出藏品与申请不属于同一收藏单位")
        if (entity.executedAt == null || collection.lifeStatus != EnumCollectionStatus.DEREGISTERED) throw BadRequestException("法定退出实际执行后才能纳入辅助账")
        CollectionAuxiliaryDao.find { CollectionAuxiliaries.exitId eq entity.id.value }.singleOrNull()?.let {
            if(it.orgId!=entity.orgId || it.content.originCollectionId!=entity.collectionId)throw BadRequestException("辅助账与退出申请关联不一致")
            verify(it)
            return it.id.value
        }
        return create(CollectionAuxiliaryVo(orgId=entity.orgId,recordNo=dossier.auxiliaryRecordNo.orEmpty(),kind=EnumAuxiliaryKind.LAWFUL_EXIT,
            name=collection.name,quantity=collection.quantity,unit=collection.unit,location=dossier.auxiliaryLocation.orEmpty(),registeredOn=LocalDate.now(),
            reason=entity.detail.orEmpty(),evidenceFileIds=receiptFileIds,originCollectionId=collection.id.value,originTotalRegNo=collection.totalRegNo,exitId=entity.id.value))
    }

    private fun requireRegistrar(orgId:String) {
        CollectionCurrentOperationAccess.require(scope,orgId,true,setOf(CollectionConst.LEDGER_CREATE,CollectionConst.LEDGER_CORRECT),"辅助账登记",setOf(CollectionConst.ROLE_LEDGER_CUSTODIAN))
        val current=CollectionActorDirectory(scope).requireActive(SecurityUtils.getUserId()!!)
        CollectionDutySeparationPolicy.requireLedgerOperation(current.permissions,current.roleCodes)
    }

    private fun verify(row:CollectionAuxiliaryDao,verifyContent:Boolean=true):Map<String,Any?> {
        fun fail():Nothing=throw BadRequestException("辅助账与封存登记原件或依据文件不一致，请核对原账")
        val content=row.content
        if(content.orgId!=row.orgId || content.recordNo!=row.recordNo || content.exitId!=row.exitId ||
            (content.id!=null && content.id!=row.id.value) || content.institutionHandover!=null)fail()
        val saved=(if(verifyContent) archives.listForAuthorizedOrg("COLLECTION_AUXILIARY",row.id.value,row.orgId) else archives.readForDisplay("COLLECTION_AUXILIARY",row.id.value,row.orgId).archives).singleOrNull {it["phase"]=="REGISTERED"} ?: fail()
        if(JsonUtils.mapper.readTree(saved["snapshot"] as String)!=JsonUtils.mapper.readTree(JsonUtils.toJson(row.toModel())))fail()
        archives.requireFileReferences(CollectionPermanentArchiveDao[saved["id"] as String],content.evidenceFileIds)
        return saved
    }

    private fun failOriginal():Nothing=throw BadRequestException("辅助账原件与当前账册、档案或读取权限不一致")

    private fun create(vo: CollectionAuxiliaryVo): String {
        if (CollectionOrgDao.findById(vo.orgId) == null) throw BadRequestException("收藏单位不存在")
        if (listOf(vo.recordNo,vo.name,vo.unit,vo.location,vo.reason,vo.evidenceFileIds).any(String::isBlank) || vo.quantity<1 || vo.recordNo.length>100) throw BadRequestException("辅助账编号、名称、数量、位置、登记依据和原件必填，编号不超过100字")
        if (vo.registeredOn.isAfter(LocalDate.now())) throw BadRequestException("辅助账登记日期不能晚于今天")
        if (CollectionAuxiliaryDao.find { (CollectionAuxiliaries.orgId eq vo.orgId) and (CollectionAuxiliaries.recordNo eq vo.recordNo.trim()) }.any()) throw BadRequestException("辅助账编号已使用，不能重复")
        val files=BusinessAttachmentBindingPlanner.plan("","",vo.evidenceFileIds).map { binder.resolveSingle(it.fileId) ?: throw BadRequestException("辅助账依据文件不存在") }.distinct().joinToString(",")
        if (files.isBlank()) throw BadRequestException("辅助账依据原件必填")
        val row=CollectionAuxiliaryDao.new {
            orgId=vo.orgId; recordNo=vo.recordNo.trim(); exitId=vo.exitId; content=vo.copy(recordNo=recordNo,evidenceFileIds=files)
        }
        archives.seal(vo.orgId,"COLLECTION_AUXILIARY",row.id.value,"REGISTERED",row.toModel(),files)
        return row.id.value
    }
}
