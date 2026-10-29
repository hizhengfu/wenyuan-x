package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.domain.CollectionJointSafetyPlanVo
import io.hizhengfu.cloud.collection.domain.CollectionJointSafetyRoundVo
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.service.CollectionCareEvidence as C
import io.hizhengfu.cloud.collection.service.CollectionJointSafetyEvidence as E
import io.hizhengfu.cloud.common.utils.SecurityUtils
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.dao.entityCache
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager

/** 共同检查方案、轮次、报告及本人确认完整落库后统一复核，保留历史原件。 */
internal object CollectionJointReportWriteVerification {
    // 使用与落库相同的时间编码，但保留Set按成员比较的语义，不把集合顺序当作业务变更。
    private fun roundValue(row:CollectionJointSafetyRoundDao)=listOf(row.orgId,
        E.roundValue(row)+("content" to JsonUtils.fromJson(C.json(row.content),CollectionJointSafetyRoundVo::class.java)),
        row.keeper?.let(C::json),row.use?.let(C::json),row.security?.let(C::json),row.report?.let(C::json),row.director?.let(C::json))
    private fun planValue(row:CollectionJointSafetyPlanDao)=listOf(row.orgId,
        E.planValue(row)+("content" to JsonUtils.fromJson(C.json(row.content),CollectionJointSafetyPlanVo::class.java)),
        row.keeper?.let(C::json),row.use?.let(C::json),row.security?.let(C::json))
    private fun archiveValue(a:CollectionPermanentArchiveDao)=listOf(a.orgId,a.bizType,a.bizId,a.phase,a.snapshot,a.digest)
    private fun fileValue(a:CollectionPermanentArchiveFileDao)=listOf(a.orgId,a.archiveId,a.bizId,a.fileId,a.metadata)

    data class Original(val phase:String,val snapshot:Any,val fileIds:String)

    fun seal(r:RequisitionDao,row:CollectionJointSafetyRoundDao,phase:String,snapshot:Any,fileIds:String,
             archives:CollectionPermanentArchiveService,verifyCurrent:()->Unit) =
        seal(r,row,listOf(Original(phase,snapshot,fileIds)),archives,verifyCurrent)

    fun seal(r:RequisitionDao,row:CollectionJointSafetyRoundDao,originalsToWrite:List<Original>,
             archives:CollectionPermanentArchiveService,verifyCurrent:()->Unit) =
        write(r,CollectionJointSafetyPlanDao[row.planId],row,originalsToWrite,archives,verifyCurrent)

    fun sealPlan(r:RequisitionDao,plan:CollectionJointSafetyPlanDao,phase:String,snapshot:Any,fileIds:String,
                 archives:CollectionPermanentArchiveService,verifyCurrent:()->Unit) =
        write(r,plan,null,listOf(Original(phase,snapshot,fileIds)),archives,verifyCurrent)

    private fun write(r:RequisitionDao,plan:CollectionJointSafetyPlanDao,row:CollectionJointSafetyRoundDao?,
                      originalsToWrite:List<Original>,archives:CollectionPermanentArchiveService,verifyCurrent:()->Unit) {
        require(originalsToWrite.isNotEmpty() && originalsToWrite.map {it.phase}.toSet().size==originalsToWrite.size)
        val actor=SecurityUtils.getUserId();val org=r.orgId;val requestId=r.id.value;val recordId=row?.id?.value ?: plan.id.value
        val expectedRequest=C.json(r.toModel());val expectedRound=row?.let(::roundValue);val expectedPlan=planValue(plan)
        val checkIds=row?.content?.checkIds.orEmpty()
        val sourceIds=setOf(requestId,recordId,plan.id.value)+checkIds
        fun originals()=CollectionPermanentArchiveDao.find {
            (CollectionPermanentArchives.bizType inList setOf(CollectionConst.BIZ_REQUISITION,E.BIZ,C.BIZ)) and
                (CollectionPermanentArchives.bizId inList sourceIds)
        }.toList()
        fun files(ids:Set<String>)=CollectionPermanentArchiveFileDao.find {CollectionPermanentArchiveFiles.archiveId inList ids}.toList()
        TransactionManager.current().entityCache.flush()
        val before=originals().associate {it.id.value to archiveValue(it)}
        val beforeFiles=files(before.keys).associate {it.id.value to fileValue(it)}
        val archiveIds=originalsToWrite.map {archives.seal(org,E.BIZ,recordId,it.phase,it.snapshot,it.fileIds)}
        TransactionManager.current().entityCache.flush()
        val savedRequest=RequisitionDao.find {Requisitions.id eq requestId}.singleOrNull() ?: C.fail("共同检查保存后原提用单缺失，请核查")
        val savedRound=if(row!=null)CollectionJointSafetyRoundDao.find {CollectionJointSafetyRounds.id eq recordId}.singleOrNull() ?: C.fail("共同检查保存后实际检查记录缺失，请核查") else null
        val savedPlan=CollectionJointSafetyPlanDao.find {CollectionJointSafetyPlans.id eq plan.id.value}.singleOrNull() ?: C.fail("共同检查保存后原检查方案缺失，请核查")
        savedRequest.refresh(flush=false);savedRound?.refresh(flush=false);savedPlan.refresh(flush=false)
        if(C.json(savedRequest.toModel())!=expectedRequest || savedRound?.let(::roundValue)!=expectedRound || planValue(savedPlan)!=expectedPlan)
            C.fail("共同检查保存期间原提用、检查或报告已变化，请重新核实")
        val written=originals().onEach {it.refresh(flush=false)}
        if(written.map {it.id.value}.toSet()!=before.keys+archiveIds || written.any {a->before[a.id.value]?.let {it!=archiveValue(a)}==true})
            C.fail("共同检查保存期间新旧原件不完整或已变化，请核查历史记录")
        val savedFiles=files(written.map {it.id.value}.toSet()).onEach {it.refresh(flush=false)}
        if(savedFiles.filter {it.archiveId in before}.associate {it.id.value to fileValue(it)}!=beforeFiles)
            C.fail("共同检查保存期间历史原件引用已变化，请核查")
        originalsToWrite.zip(archiveIds).forEach {(original,id) ->
            archives.verifyExisting(written.single {it.id.value==id},org,E.BIZ,recordId,original.phase,original.snapshot,original.fileIds)
        }
        CollectionCareCheckDao.find {CollectionCareChecks.id inList checkIds}.forEach {it.refresh(flush=false)}
        if(savedRound!=null)E.round(savedRound) else E.plan(savedPlan)
        CollectionOrgDao.findById(org)?.refresh(flush=false)
        if(SecurityUtils.getNullUserDetail()?.id!=actor)C.fail("共同检查保存期间办理账号变化，请重新核对本人职责")
        verifyCurrent()
    }
}
