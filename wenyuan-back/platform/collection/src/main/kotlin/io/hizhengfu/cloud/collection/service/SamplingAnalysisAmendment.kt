package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.*
import java.security.MessageDigest

/** 原件版本只追加；当前岗位在写入时检查，历史读取不改写当时责任人。 */
internal object SamplingAnalysisAmendment {
    private fun digest(v:Any)=MessageDigest.getInstance("SHA-256").digest(JsonUtils.toJson(v)!!.toByteArray()).joinToString(""){"%02x".format(it)}
    fun prefix(sampleId:String)="ANALYSISPLAN${digest(sampleId).take(32)}V"
    fun phase(sampleId:String,sequence:Int)="${prefix(sampleId)}$sequence"
    private fun fail():Nothing=throw BadRequestException("旧样品分析方案顺序、本人确认或启用原件不一致")
    fun read(p:CollectionSamplingPlanDao,sampleId:String,verify:(String,Any)->Unit):SamplingAnalysisAmendmentVo {
        val events=CollectionPermanentArchiveDao.find {(CollectionPermanentArchives.bizType eq CollectionConst.BIZ_SAMPLING) and
            (CollectionPermanentArchives.bizId eq p.id.value) and (CollectionPermanentArchives.phase like "${prefix(sampleId)}%")}.map {a->
            val e=try {JsonUtils.fromJson(a.snapshot,SamplingAnalysisAmendmentEventVo::class.java)!!} catch(_:Exception){fail()}
            if(a.phase!=phase(sampleId,e.sequence))fail()
            verify(a.phase,e);e
        }.sortedBy {it.sequence}
        return events.fold(SamplingAnalysisAmendmentVo(digest(mapOf("planId" to p.id.value,"sampleId" to sampleId)),emptyList()),::advance)
    }
    fun proposal(state:SamplingAnalysisAmendmentVo)=state.events.singleOrNull {it.sequence==state.proposalSequence}?.proposal ?: fail()
    fun advance(s:SamplingAnalysisAmendmentVo,e:SamplingAnalysisAmendmentEventVo):SamplingAnalysisAmendmentVo {
        if(e.sequence!=s.events.size+1 || listOfNotNull(e.proposal,e.confirmation,e.administrative,e.activation,e.closure).size!=1)fail()
        val revision=e.proposal?.previousRevision ?: e.confirmation?.previousRevision ?: e.administrative?.previousRevision ?: e.activation?.previousRevision ?: e.closure!!.previousRevision
        if(revision!=s.revision)fail()
        fun occurred(v:SamplingAnalysisAmendmentEventVo)=v.proposal?.preparedAt ?: v.confirmation?.confirmedAt ?: v.activation?.activatedAt ?: v.closure?.reviewedAt
        val at=occurred(e)
        if(at!=null && s.events.mapNotNull(::occurred).any {at.isBefore(it)})fail()
        val next=s.copy(revision=digest(e),events=s.events+e)
        e.proposal?.let {p->
            if(s.activationSequence!=null && s.closedAt==null || s.closedAt!=null && p.preparedAt.isBefore(s.closedAt) || p.conservationUserId==p.keeperUserId || e.operatorUserId!=p.recorderUserId)fail()
            return SamplingAnalysisAmendmentVo(next.revision,next.events,proposalSequence=e.sequence)
        }
        val p=proposal(s)
        e.confirmation?.let {c->
            if(c.opinion.isBlank() || c.signedFileIds.isBlank() || s.events.mapNotNull {it.confirmation}.filter {it.subjectSequence==c.subjectSequence}.any {c.confirmedAt.isBefore(it.confirmedAt)})fail()
            if(c.subjectSequence==s.proposalSequence && s.activationSequence==null) {
                val people=setOf(p.conservationUserId,p.keeperUserId)
                if(e.operatorUserId !in people || e.operatorUserId in s.confirmedUserIds || c.confirmedAt.isBefore(p.preparedAt))fail()
                return next.copy(confirmedUserIds=s.confirmedUserIds+e.operatorUserId)
            }
            val close=s.events.singleOrNull {it.sequence==s.closureSequence}?.closure ?: fail()
            val people=setOf(close.conservationUserId,close.keeperUserId)
            if(c.subjectSequence!=s.closureSequence || s.closedAt!=null || e.operatorUserId !in people || e.operatorUserId in s.closureConfirmedUserIds || c.confirmedAt.isBefore(close.reviewedAt))fail()
            val confirmed=s.closureConfirmedUserIds+e.operatorUserId
            return next.copy(closureConfirmedUserIds=confirmed,closedAt=if(confirmed==people)c.confirmedAt else null)
        }
        e.administrative?.let {a->
            if(a.proposalSequence!=s.proposalSequence || p.approvalTreatment!=EnumExistingSampleApproval.NEW_APPROVAL || s.confirmedUserIds.size!=2 || s.activationSequence!=null)fail()
            return next.copy(administrativeSequence=e.sequence)
        }
        e.activation?.let {a->
            if(a.proposalSequence!=s.proposalSequence || s.confirmedUserIds.size!=2 || s.activationSequence!=null || a.sampleRevision!=p.sampleRevision ||
                p.approvalTreatment==EnumExistingSampleApproval.NEW_APPROVAL && s.administrativeSequence==null)fail()
            val confirms=s.events.filter {it.confirmation?.subjectSequence==s.proposalSequence}.map {it.confirmation!!.confirmedAt}
            if(confirms.any {a.activatedAt.isBefore(it)} || a.activatedAt.isBefore(p.preparedAt))fail()
            return next.copy(activationSequence=e.sequence)
        }
        e.closure?.let {c->
            if(c.proposalSequence!=s.proposalSequence || s.activationSequence==null || s.closedAt!=null || c.conservationUserId==c.keeperUserId || c.recorderUserId!=e.operatorUserId || c.reviewedAt.isBefore(c.endedAt))fail()
            val activated=s.events.single {it.sequence==s.activationSequence}.activation!!
            if(c.endedAt.isBefore(activated.activatedAt))fail()
            return next.copy(closureSequence=e.sequence,closureConfirmedUserIds=emptySet())
        }
        fail()
    }
    /** 以当时启用记录核对顺序账；后来换方案或结案不撤销已发生分析的历史依据。 */
    fun authorized(state:SamplingAnalysisAmendmentVo,authorization:SamplingAnalysisAuthorizationVo):SamplingAnalysisProposalVo {
        val activated=state.events.singleOrNull {it.sequence==authorization.activationSequence}?.activation ?: fail()
        val p=state.events.singleOrNull {it.sequence==authorization.proposalSequence}?.proposal ?: fail()
        if(activated.proposalSequence!=authorization.proposalSequence || activated.activatedAt!=authorization.activatedAt || activated.sampleRevision!=authorization.previousRevision)fail()
        return p
    }
}
