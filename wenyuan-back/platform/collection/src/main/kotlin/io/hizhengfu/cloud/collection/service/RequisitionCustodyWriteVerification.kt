package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.RequisitionCustodyRequest
import io.hizhengfu.cloud.collection.service.CollectionCareEvidence as C
import io.hizhengfu.cloud.common.utils.SecurityUtils
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.dao.entityCache
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager

/** 在用承接只改变后续责任；实际写入后再核对原提用、实物、归还与新旧原件。 */
internal object RequisitionCustodyWriteVerification {
    private fun archiveValue(a:CollectionPermanentArchiveDao)=listOf(a.orgId,a.bizType,a.bizId,a.phase,a.snapshot,a.digest)
    private fun fileValue(a:CollectionPermanentArchiveFileDao)=listOf(a.orgId,a.archiveId,a.bizId,a.fileId,a.metadata)
    private fun checkValue(a:CollectionCareCheckDao)=C.json(listOf(a.orgId,a.collectionId,a.catalog,a.content))
    fun seal(r:RequisitionDao,v:RequisitionCustodyRequest,phase:String,snapshot:Any,fileIds:String,
             archives:CollectionPermanentArchiveService,verifyCurrent:()->Unit) {
        val id=r.id.value;val org=r.orgId;val actor=SecurityUtils.getUserId();val expected=C.json(r.toModel())
        val checkIds=v.checkIds+RequisitionCustodyEvidence.entries(r).flatMap {it.record.request.checkIds}
        val pieceIds=splitCollectionIds(r.collectionIds).toSet()
        fun returns()=CollectionReturnDao.find {(CollectionReturns.sourceBizType eq CollectionConst.BIZ_REQUISITION) and (CollectionReturns.sourceBizId eq id)}.toList()
        fun checks()=CollectionCareCheckDao.find {CollectionCareChecks.id inList checkIds}.toList()
        fun pieces()=CollectionDao.find {Collections.id inList pieceIds}.toList()
        TransactionManager.current().entityCache.flush()
        val beforeReturns=returns().associate {it.id.value to C.json(it.toModel())}
        val beforeChecks=checks().associate {it.id.value to checkValue(it)}
        val beforePieces=pieces().associate {it.id.value to C.json(it.toModel())}
        val sourceIds=setOf(id)+checkIds+beforeReturns.keys
        fun originals()=CollectionPermanentArchiveDao.find {
            (CollectionPermanentArchives.bizType inList setOf(CollectionConst.BIZ_REQUISITION,CollectionConst.BIZ_RETURN,C.BIZ)) and
                (CollectionPermanentArchives.bizId inList sourceIds)
        }.toList()
        fun files(ids:Set<String>)=CollectionPermanentArchiveFileDao.find {CollectionPermanentArchiveFiles.archiveId inList ids}.toList()
        val before=originals().associate {it.id.value to archiveValue(it)}
        val beforeFiles=files(before.keys).associate {it.id.value to fileValue(it)}
        val archiveId=archives.seal(org,CollectionConst.BIZ_REQUISITION,id,phase,snapshot,fileIds)
        TransactionManager.current().entityCache.flush()
        val current=RequisitionDao.find {Requisitions.id eq id}.singleOrNull() ?: C.fail("在用承接写入后原提用单缺失")
        current.refresh(flush=false)
        if(C.json(current.toModel())!=expected)C.fail("在用承接期间原提用、馆别或实际点交已变化，请重新核对")
        if(returns().onEach {it.refresh(flush=false)}.associate {it.id.value to C.json(it.toModel())}!=beforeReturns ||
            checks().onEach {it.refresh(flush=false)}.associate {it.id.value to checkValue(it)}!=beforeChecks ||
            pieces().onEach {it.refresh(flush=false)}.associate {it.id.value to C.json(it.toModel())}!=beforePieces)
            C.fail("在用承接期间实际归还、逐件现状或藏品已变化，请重新核对完整清单")
        val written=originals().onEach {it.refresh(flush=false)}
        if(written.map {it.id.value}.toSet()!=before.keys+archiveId || written.any {a->before[a.id.value]?.let {it!=archiveValue(a)}==true})
            C.fail("在用承接新旧原件不完整或已变化，请核对历史记录")
        val writtenFiles=files(written.map {it.id.value}.toSet()).onEach {it.refresh(flush=false)}
        if(writtenFiles.filter {it.archiveId in before}.associate {it.id.value to fileValue(it)}!=beforeFiles)
            C.fail("在用承接期间历史原件引用变化，请核实原件")
        archives.verifyExisting(written.single {it.id.value==archiveId},org,CollectionConst.BIZ_REQUISITION,id,phase,snapshot,fileIds)
        RequisitionCustodyEvidence.entries(current)
        CollectionOrgDao.findById(org)?.refresh(flush=false)
        if(SecurityUtils.getNullUserDetail()?.id!=actor)C.fail("在用承接期间经办账号变化，请重新核实本人职责")
        verifyCurrent()
    }
}
