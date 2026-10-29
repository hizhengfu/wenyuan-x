package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.IdGenerate
import io.hizhengfu.cloud.common.utils.SecurityUtils
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.dao.entityCache
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import io.hizhengfu.cloud.collection.service.ReturnResponsibilityEvidence as E
import io.hizhengfu.cloud.collection.service.CollectionCareEvidence as C

@Component
@Transactional
class ReturnResponsibilityService(
    private val scope:CollectionScopeResolver,
    private val users:CollectionResponsibleUserService,
    private val statutory:ReturnStatutoryService,
    private val archives:CollectionPermanentArchiveService,
) {
    private fun access(e:CollectionReturnDao,write:Boolean,expectedOrgId:String) {
        if(expectedOrgId.isBlank() || expectedOrgId!=e.orgId)C.fail("归还单所属馆与打开表单时不一致，请重新读取")
        CollectionCurrentOperationAccess.require(scope,e.orgId,write,if(write)setOf(CollectionConst.STORAGE)else setOf(CollectionConst.STORAGE,CollectionConst.SELECT),"归还异常责任承接")
    }
    private fun request(id:String,write:Boolean,expectedOrgId:String):CollectionReturnDao {
        val q=CollectionReturnDao.find {CollectionReturns.id eq id}
        val e=(if(write)q.forUpdate() else q).singleOrNull() ?: C.fail("归还单不存在")
        access(e,write,expectedOrgId);return e
    }
    private fun pending(e:CollectionReturnDao):Set<String> {
        val accepted=statutory.acceptedIds(e)
        if(splitCollectionIds(e.acceptedCollectionIds).toSet()!=accepted)C.fail("归还验收状态与已归档处理结果不一致，请先核对原件")
        return e.inspection!!.items.filter {it.abnormal() && it.collectionId !in accepted}.map {it.collectionId}.toSet()
    }
    private fun current(e:CollectionReturnDao,v:ReturnResponsibilityVo) {
        val original=E.received(e)
        if(v.receivedDigest!=original.digest || v.collectionIds.isEmpty() || v.collectionIds!=pending(e))C.fail("原接收依据或当前未结异常清单已变化，请重新核对完整清单")
        if(v.handedAt.isBefore(e.inspection!!.inspectedAt) || v.handedAt.isAfter(LocalDateTime.now()))C.fail("须如实登记接收后已完成的清点移交时间")
        if(v.keeperUserId==v.directorUserId)C.fail("保管负责人和主要负责人须由不同实际人员履职")
        users.requireRole(v.keeperUserId,e.orgId,CollectionConst.ROLE_STORAGE_HEAD)
        users.requireRole(v.directorUserId,e.orgId,CollectionConst.ROLE_DIRECTOR)
    }
    fun detail(id:String,expectedOrgId:String):Map<String,Any?> {
        val e=request(id,false,expectedOrgId);val original=E.received(e);val entries=E.entries(e);val ids=pending(e)
        val canOperate=try {access(e,true,expectedOrgId);true}catch(_:BadRequestException){false}
        val result=mapOf("id" to id,"orgId" to e.orgId,"receivedDigest" to original.digest,"pendingCollectionIds" to ids,
            "canOperate" to canOperate,"previousId" to entries.lastOrNull()?.record?.id,
            "effectiveId" to entries.lastOrNull {it.complete}?.record?.id,
            "responsibleItems" to ids.map {cid->val effective=entries.lastOrNull {it.complete && cid in it.record.request.collectionIds};mapOf("collectionId" to cid,"responsibilityId" to effective?.record?.id,"keeperUserId" to (effective?.record?.request?.keeperUserId ?: e.keeperUserId),"directorUserId" to effective?.record?.request?.directorUserId)},
            "records" to entries.map {mapOf("id" to it.record.id,"content" to it.record.request,"registeredAt" to it.record.registeredAt,"complete" to it.complete,"signatures" to it.signatures)})
        access(e,false,expectedOrgId);return result
    }
    private fun originalRows(id:String)=CollectionPermanentArchiveDao.find {
        (CollectionPermanentArchives.bizType eq CollectionConst.BIZ_RETURN) and (CollectionPermanentArchives.bizId eq id)
    }.toList()
    private fun originalValues(a:CollectionPermanentArchiveDao)=listOf(a.orgId,a.bizType,a.bizId,a.phase,a.snapshot,a.digest)
    private fun originalFiles(ids:Set<String>)=CollectionPermanentArchiveFileDao.find {CollectionPermanentArchiveFiles.archiveId inList ids}.toList()
    private fun fileValues(f:CollectionPermanentArchiveFileDao)=listOf(f.orgId,f.archiveId,f.bizId,f.fileId,f.metadata)
    private fun persisted(e:CollectionReturnDao)=JsonUtils.fromJson(JsonUtils.toJson(e.toModel()),CollectionReturnVo::class.java)!!
    private fun seal(e:CollectionReturnDao,request:ReturnResponsibilityVo,phase:String,snapshot:Any,fileIds:String,expectedOrgId:String) {
        val id=e.id.value;val actor=SecurityUtils.getUserId();val expected=persisted(e)
        val originals=originalRows(id).associate {it.id.value to originalValues(it)}
        val previousFiles=originalFiles(originals.keys).associate {it.id.value to fileValues(it)}
        val archiveId=archives.seal(expectedOrgId,CollectionConst.BIZ_RETURN,id,phase,snapshot,fileIds)
        TransactionManager.current().entityCache.flush()
        val current=CollectionReturnDao.find {CollectionReturns.id eq id}.singleOrNull() ?: C.fail("归还异常承接写入后原归还单缺失")
        current.refresh(flush=false)
        if(persisted(current)!=expected)C.fail("归还异常承接期间原归还单、实际接收或验收状态变化，请重新核对")
        val written=originalRows(id).onEach {it.refresh(flush=false)}
        if(written.map {it.id.value}.toSet()!=originals.keys+archiveId || written.any {a->originals[a.id.value]?.let {it!=originalValues(a)}==true})C.fail("归还异常承接新旧原件不完整或已变化，请核对历史记录")
        val files=originalFiles(written.map {it.id.value}.toSet()).onEach {it.refresh(flush=false)}
        if(files.filter {it.archiveId in originals}.associate {it.id.value to fileValues(it)}!=previousFiles)C.fail("归还异常承接期间历史原件引用变化，请核实原件")
        archives.verifyExisting(written.single {it.id.value==archiveId},expectedOrgId,CollectionConst.BIZ_RETURN,id,phase,snapshot,fileIds)
        ReturnComplianceRecordDao.find {ReturnComplianceRecords.returnId eq id}.forEach {it.refresh(flush=false)}
        current(current,request);E.entries(current)
        CollectionOrgDao.findById(expectedOrgId)?.refresh(flush=false)
        if(SecurityUtils.getNullUserDetail()?.id!=actor)C.fail("归还异常承接期间办理账号变化，请重新核实本人职责")
        access(current,true,expectedOrgId)
    }
    fun register(id:String,v:ReturnResponsibilityVo,expectedOrgId:String):String {
        val e=request(id,true,expectedOrgId);val entries=E.entries(e);current(e,v)
        if(SecurityUtils.getUserId()!=v.keeperUserId)C.fail("须由承接的保管负责人本人登记实际清点移交")
        entries.lastOrNull()?.takeIf {JsonUtils.toJson(it.record.request)==JsonUtils.toJson(v)}?.let {return it.record.id}
        val effective=entries.lastOrNull {it.complete}
        if(v.previousId!=entries.lastOrNull()?.record?.id || v.expectedEffectiveId!=effective?.record?.id)C.fail("归还异常责任承接版本已变化，请重新读取")
        if(listOf(v.reason,v.actualInventory,v.outstandingMatters,v.appointmentFileIds,v.handoverFileIds).any(String::isBlank))C.fail("须提供任职依据、实际清点移交、未结事项及签署原件")
        if(effective?.effectiveAt?.let {v.handedAt.isBefore(it)}==true)C.fail("后续承接须在上次责任生效后重新清点移交")
        if((effective!=null && E.actors(effective)==mapOf("KEEPER" to v.keeperUserId,"DIRECTOR" to v.directorUserId)) ||
            (effective==null && v.keeperUserId==e.keeperUserId))C.fail("请明确实际变更的异常处理保管责任；原归还负责人及签署继续保留")
        val record=ReturnResponsibilityRecord(IdGenerate.nextId(),id,e.orgId,E.received(e).id.value,v,v.keeperUserId,LocalDateTime.now())
        seal(e,v,"${E.PREFIX}P_${record.id}",record,E.files(v),expectedOrgId)
        return record.id
    }
    fun sign(id:String,recordId:String,role:String,v:CollectionCareSignatureVo,expectedOrgId:String):String {
        val e=request(id,true,expectedOrgId);val entries=E.entries(e)
        val entry=entries.lastOrNull()?.takeIf {it.record.id==recordId} ?: C.fail("须确认最新的归还异常责任承接版本")
        current(e,entry.record.request)
        val actor=E.actors(entry)[role] ?: C.fail("归还异常承接确认职责不存在")
        if(SecurityUtils.getUserId()!=actor)C.fail("须由指定责任人员本人确认，管理员不能代签")
        if(v.opinion.isBlank() || v.fileIds.isBlank())C.fail("须填写本人核实意见和实际签署原件")
        entry.signatures[role]?.let {if(it.record==v)return recordId;C.fail("本人确认不能覆盖，请追加新的承接版本")}
        if(role=="DIRECTOR" && "KEEPER" !in entry.signatures)C.fail("须先由承接保管负责人确认实际清点移交，再由主要负责人确认")
        seal(e,entry.record.request,"${E.PREFIX}${role}_$recordId",CollectionCareSignature(actor,LocalDateTime.now(),v),v.fileIds,expectedOrgId)
        return recordId
    }
}
