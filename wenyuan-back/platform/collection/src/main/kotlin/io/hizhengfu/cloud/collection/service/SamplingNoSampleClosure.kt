package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.*
import java.security.MessageDigest

internal object SamplingNoSampleClosure {
    private const val PREFIX = "NOSAMPLEV"
    fun phase(sequence: Int) = "$PREFIX$sequence"
    private fun digest(value: Any) = MessageDigest.getInstance("SHA-256")
        .digest(JsonUtils.toJson(value)!!.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun fail(): Nothing = throw BadRequestException("未取得样品结案记录或原件不一致，请核实原始材料")

    fun read(p: CollectionSamplingPlanDao, verify: (String, Any) -> Unit): SamplingNoSampleClosureVo {
        p.started?.let { verify("STARTED", it) }
        p.ended?.let { verify("EXTRACTION_ENDED", it) }
        p.incidentClearance?.let { verify("EXCEPTION_CLEARED", it) }
        val samples = CollectionSampleDao.find { CollectionSamples.planId eq p.id.value }.map { it.id.value }.sorted()
        val basis = digest(mapOf("planId" to p.id.value, "orgId" to p.orgId, "plan" to p.content,
            "started" to p.started, "ended" to p.ended, "incidentClearance" to p.incidentClearance, "sampleIds" to samples))
        val events = CollectionPermanentArchiveDao.find {
            (CollectionPermanentArchives.bizType eq CollectionConst.BIZ_SAMPLING) and
                (CollectionPermanentArchives.bizId eq p.id.value) and (CollectionPermanentArchives.phase like "$PREFIX%")
        }.map { archive ->
            val event = try { JsonUtils.fromJson(archive.snapshot, SamplingNoSampleEventVo::class.java)!! }
                catch (_: Exception) { fail() }
            if (archive.phase != phase(event.sequence)) fail()
            verify(archive.phase, event)
            event
        }.sortedBy { it.sequence }
        var state = SamplingNoSampleClosureVo(digest(mapOf("planId" to p.id.value, "orgId" to p.orgId)), basis,
            p.started != null && p.ended != null && samples.isEmpty() && p.completed == null, emptyList())
        for (event in events) {
            if (event.review != null && event.review.planId != p.id.value) fail()
            state = advance(state, event)
        }
        if (events.isNotEmpty() && !state.eligible) fail()
        return state
    }

    fun advance(state: SamplingNoSampleClosureVo, event: SamplingNoSampleEventVo): SamplingNoSampleClosureVo {
        val r = event.review
        val c = event.confirmation
        if (state.closedAt != null || event.sequence != state.events.size + 1 || (r == null) == (c == null) ||
            (r?.previousRevision ?: c!!.previousRevision) != state.revision) fail()
        val occurredAt = r?.reviewedAt ?: c!!.confirmedAt
        state.events.lastOrNull()?.let { previous ->
            if (occurredAt.isBefore(previous.review?.reviewedAt ?: previous.confirmation!!.confirmedAt)) fail()
        }
        var next = state.copy(revision = digest(event), events = state.events + event)
        if (r != null) {
            if (r.conservationUserId == r.keeperUserId || event.operatorUserId != r.recorderUserId) fail()
            next = next.copy(reviewSequence = event.sequence, confirmedUserIds = emptySet(),
                reviewCurrent = r.basisRevision == state.basisRevision)
        }
        if (c != null) {
            val proposal = state.events.singleOrNull { it.sequence == c.reviewSequence }?.review ?: fail()
            val people = setOf(proposal.conservationUserId, proposal.keeperUserId)
            if (state.reviewSequence != c.reviewSequence || event.operatorUserId !in people ||
                event.operatorUserId in state.confirmedUserIds) fail()
            val confirmed = state.confirmedUserIds + event.operatorUserId
            next = next.copy(confirmedUserIds = confirmed, closedAt = if (confirmed == people) c.confirmedAt else null)
        }
        return next
    }
}
