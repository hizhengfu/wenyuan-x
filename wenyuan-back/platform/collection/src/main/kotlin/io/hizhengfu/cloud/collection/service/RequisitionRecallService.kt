package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.utils.SecurityUtils
import io.hizhengfu.cloud.common.utils.IdGenerate
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.dao.entityCache
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import io.hizhengfu.cloud.collection.service.CollectionCareEvidence as C
import io.hizhengfu.cloud.collection.service.CollectionJointSafetyEvidence as J

@Component
@Transactional
class RequisitionRecallService(private val scope:CollectionScopeResolver,private val users:CollectionResponsibleUserService,private val archives:CollectionPermanentArchiveService) {
    fun register(id:String,v:RequisitionRecallVo,expectedOrgId:String):String {
        TransactionManager.current().entityCache.flush()
        val r=RequisitionDao.findById(id) ?: C.fail("提用单不存在")
        fun access() {
            CollectionCurrentOperationAccess.require(scope,r.orgId,true,setOf(CollectionConst.CARE),"提用提前归库")
            if(expectedOrgId!=r.orgId)C.fail("提用所属馆已变化，请重新核对")
        }
        access()
        val ids=splitCollectionIds(r.collectionIds).sorted()
        ids.forEach {CollectionDao.find {Collections.id eq it}.forUpdate().toList()}
        RequisitionDao.find {Requisitions.id eq id}.forUpdate().single().refresh()
        access()
        if(splitCollectionIds(r.collectionIds).sorted()!=ids)C.fail("提用清单已变化，请重新核对")
        val actor=SecurityUtils.getUserId() ?: C.fail("请登录后办理提前归库")
        fun responsibility() {
            access()
            val current=RequisitionCustodyEvidence.entries(r).lastOrNull {it.complete}
            val people=RequisitionCustodyEvidence.actors(r,current)
            if(SecurityUtils.getUserId()!=actor || actor!=people["KEEPER"])C.fail("须由现任保管部门负责人本人登记提前归库要求")
            users.requireRole(actor,r.orgId,CollectionConst.ROLE_STORAGE_HEAD)
        }
        responsibility()
        val existing=RequisitionRecallEvidence.entries(r)
        existing.lastOrNull()?.takeIf {it.request==v && it.keeperUserId==actor}?.let {return it.id}
        val source=J.source(r);J.outbound(r)
        val received=J.receivedIds(r)
        if(!r.outExecuted || r.returned || source.cancelled || v.collectionIds.any {it in received})C.fail("只能要求实际出库且尚未接收的提用藏品提前归库")
        RequisitionRecallEvidence.validate(v)
        val now=LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS)
        val custody=RequisitionCustodyEvidence.entries(r).lastOrNull {it.complete}
        if(v.sourceRevision!=C.digest(source.snapshot) || v.previousId!=existing.lastOrNull()?.id)C.fail("提前归库或责任来源版本已变化，请重新读取")
        if(!source.collectionIds.containsAll(v.collectionIds) || source.handedAt==null || v.issuedAt.isBefore(source.handedAt) || v.issuedAt.isAfter(now) ||
            !v.returnBy.isBefore(source.returnAt) || custody?.effectiveAt?.let {v.issuedAt.isBefore(it)}==true)
            C.fail("提前归库须属于本次实际出库范围，在本人责任生效后据实登记，期限须早于原计划")
        val actors=RequisitionCustodyEvidence.actors(r,custody)
        val out=CollectionPermanentArchiveDao.find {(CollectionPermanentArchives.bizType eq CollectionConst.BIZ_REQUISITION) and (CollectionPermanentArchives.bizId eq id) and (CollectionPermanentArchives.phase eq "OUT_EXECUTED")}.single()
        val record=RequisitionRecallRecord(IdGenerate.nextId(),r.orgId,id,v,J.baseSource(r).snapshot,custody?.record?.id,out.id.value,out.digest,actor,actors.getValue("USE"),now)
        val original=C.json(r.toModel())
        val archiveId=archives.seal(r.orgId,CollectionConst.BIZ_REQUISITION,id,RequisitionRecallEvidence.PREFIX+record.id,record,v.fileIds)
        TransactionManager.current().entityCache.flush();r.refresh(flush=false)
        if(C.json(r.toModel())!=original || J.source(r).snapshot!=source.snapshot || J.receivedIds(r)!=received)C.fail("归档期间提用、责任或实际归还发生变化，请重新核对")
        CollectionOrgDao.findById(r.orgId)?.refresh(flush=false)
        responsibility()
        archives.verifyExisting(CollectionPermanentArchiveDao[archiveId],r.orgId,CollectionConst.BIZ_REQUISITION,id,RequisitionRecallEvidence.PREFIX+record.id,record,v.fileIds)
        if(RequisitionRecallEvidence.entries(r).lastOrNull()!=record)C.fail("提前归库归档结果不一致")
        return record.id
    }
}
