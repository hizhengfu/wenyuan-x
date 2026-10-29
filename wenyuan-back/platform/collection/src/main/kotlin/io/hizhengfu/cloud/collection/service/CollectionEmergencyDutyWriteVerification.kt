package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.dao.entityCache
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import io.hizhengfu.cloud.collection.service.CollectionCareEvidence as C
import io.hizhengfu.cloud.collection.service.CollectionEmergencyEvidence as E
import io.hizhengfu.cloud.collection.service.CollectionEmergencyDutyEvidence as D

/** 指定、原任交接与接任确认落库后核对本次记录、原依据和历史原件；不改写旧人员。 */
internal object CollectionEmergencyDutyWriteVerification {
    private fun duty(r:CollectionEmergencyDutyDao)=C.json(listOf(r.orgId,D.value(r),r.handover,r.acceptance))
    private fun archive(a:CollectionPermanentArchiveDao)=listOf(a.orgId,a.bizType,a.bizId,a.phase,a.snapshot,a.digest)
    private fun reference(a:CollectionPermanentArchiveFileDao)=listOf(a.orgId,a.archiveId,a.bizId,a.fileId,a.metadata)
    private fun index(a:CollectionEmergencyFileAccessDao)=listOf(a.orgId,a.archiveId,a.fileId,a.pathKey)

    fun write(row:CollectionEmergencyDutyDao,seal:()->String,verifyCurrent:()->Unit) {
        val expected=duty(row);val org=row.orgId;val id=row.id.value;val target=row.appointment.request.target
        TransactionManager.current().entityCache.flush()
        val drillId=when(target.type) {
            EnumEmergencyDutyTarget.PLAN->null
            EnumEmergencyDutyTarget.IMPROVEMENT->CollectionEmergencyImprovementDao[target.targetId].drillId
            else->target.targetId
        }
        val drill=drillId?.let {CollectionEmergencyDrillDao[it]}
        val planIds=if(drill==null)setOf(target.targetId) else E.reports(drill).map {it.content.planId}.toSet()
        val revisionIds=drillId?.let {E.revisions(it).map {r->r.id.value}.toSet()}.orEmpty()
        val improvementIds=drillId?.let {E.improvements(it).map {r->r.id.value}.toSet()}.orEmpty()
        val dutyIds=CollectionEmergencyDutyDao.find {(CollectionEmergencyDuties.orgId eq org) and (CollectionEmergencyDuties.targetKey eq row.targetKey)}.map {it.id.value}.toSet()
        val ids=planIds+revisionIds+improvementIds+dutyIds+listOfNotNull(drillId)
        fun sources():Map<String,String> = buildMap {
            CollectionEmergencyPlanDao.find {CollectionEmergencyPlans.id inList planIds}.forEach {p->
                p.refresh(flush=false);put("plan:${p.id}",C.json(listOf(p.orgId,E.planValue(p),p.keeper,p.security,p.director,p.keeperDutyId,p.securityDutyId,p.directorDutyId)))
            }
            if(drillId!=null)CollectionEmergencyDrillDao.find {CollectionEmergencyDrills.id eq drillId}.forEach {d->
                d.refresh(flush=false);put("drill:${d.id}",C.json(listOf(d.orgId,E.drillValue(d),d.evaluator,d.director,d.evaluatorDutyId,d.directorDutyId)))
            }
            CollectionEmergencyDrillRevisionDao.find {CollectionEmergencyDrillRevisions.id inList revisionIds}.forEach {r->
                r.refresh(flush=false);put("revision:${r.id}",C.json(listOf(r.orgId,E.revisionValue(r),r.evaluator,r.director,r.evaluatorDutyId,r.directorDutyId)))
            }
            CollectionEmergencyImprovementDao.find {CollectionEmergencyImprovements.id inList improvementIds}.forEach {r->
                r.refresh(flush=false);put("improvement:${r.id}",C.json(listOf(r.orgId,E.improvementValue(r),r.review,r.reviewDutyId)))
            }
            CollectionEmergencyDutyDao.find {CollectionEmergencyDuties.id inList (dutyIds-id)}.forEach {r->r.refresh(flush=false);put("duty:${r.id}",duty(r))}
        }
        fun originals()=CollectionPermanentArchiveDao.find {(CollectionPermanentArchives.bizType eq E.BIZ) and (CollectionPermanentArchives.bizId inList ids)}.toList()
        fun refs(archiveIds:Set<String>)=CollectionPermanentArchiveFileDao.find {CollectionPermanentArchiveFiles.archiveId inList archiveIds}.toList()
        fun indexes(archiveIds:Set<String>)=CollectionEmergencyFileAccessDao.find {CollectionEmergencyFileAccesses.archiveId inList archiveIds}.toList()
        val originalSources=sources()
        val originalArchives=originals().associate {it.id.value to archive(it)}
        val originalReferences=refs(originalArchives.keys).associate {it.id.value to reference(it)}
        val originalIndexes=indexes(originalArchives.keys).associate {it.id.value to index(it)}
        val created=seal()
        TransactionManager.current().entityCache.flush()
        val saved=CollectionEmergencyDutyDao.find {CollectionEmergencyDuties.id eq id}.singleOrNull() ?: C.fail("应急责任承接保存后记录缺失")
        saved.refresh(flush=false)
        if(duty(saved)!=expected || sources()!=originalSources)C.fail("应急责任承接保存期间记录或依据发生变化，请重新核对")
        val currentArchives=originals().onEach {it.refresh(flush=false)}
        if(currentArchives.map {it.id.value}.toSet()!=originalArchives.keys+created || currentArchives.any {a->originalArchives[a.id.value]?.let {it!=archive(a)}==true})
            C.fail("应急责任承接保存期间新旧原件缺失或发生变化")
        if(refs(originalArchives.keys).onEach {it.refresh(flush=false)}.associate {it.id.value to reference(it)}!=originalReferences ||
            indexes(originalArchives.keys).onEach {it.refresh(flush=false)}.associate {it.id.value to index(it)}!=originalIndexes)
            C.fail("应急责任承接保存期间历史原件引用或访问索引发生变化")
        val rows=D.records(org,target)
        if(rows.lastOrNull()?.id?.value!=id)C.fail("应急责任指定已有后续版本，请重新核对")
        verifyCurrent()
    }
}
