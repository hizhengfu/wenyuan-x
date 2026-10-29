package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.*
import java.math.BigDecimal
import java.security.MessageDigest

/** 以永久原件为顺序账。没有将历史单次分析迁移成新事实，读取旧档也不检查人员现在是否仍任职。 */
internal object SamplingSampleJournal {
    private fun json(v: Any) = JsonUtils.toJson(v)!!
    private fun digest(v: String) = MessageDigest.getInstance("SHA-256").digest(v.toByteArray()).joinToString("") { "%02x".format(it) }
    fun prefix(sampleId: String) = "SAMPLEEVENT${digest(sampleId).take(32)}V"
    fun custodyPrefix(sampleId: String) = "SAMPLECUSTODY${digest(sampleId).take(32)}V"
    private fun fail(): Nothing = throw BadRequestException("样品顺序记录、数量或永久原件不一致，请核实原始材料")

    fun read(p: CollectionSamplingPlanDao, sample: CollectionSampleDao, verify: (String, Any) -> Unit): SamplingSampleJournalVo {
        if (sample.orgId != p.orgId || sample.planId != p.id.value) fail()
        verify("SAMPLE_${sample.id.value}", sample.content)
        val original = sample.content
        val legacy = sample.analysis
        legacy?.let { verify("ANALYSIS_${sample.id.value}", it) }
        if (original.quantity <= BigDecimal.ZERO || legacy != null &&
            (legacy.consumedQuantity < BigDecimal.ZERO || legacy.remainingQuantity < BigDecimal.ZERO ||
                (legacy.consumedQuantity + legacy.remainingQuantity).compareTo(original.quantity) != 0 ||
                legacy.remainingQuantity > BigDecimal.ZERO && (legacy.remainingLocation.isNullOrBlank() || legacy.remainingCustodianUserId.isNullOrBlank()))) fail()
        var state = SamplingSampleJournalVo(
            revision = digest(json(mapOf("sampleId" to sample.id.value, "planId" to p.id.value, "orgId" to p.orgId,
                "sample" to original, "legacyAnalysis" to legacy))),
            remainingQuantity = legacy?.remainingQuantity ?: original.quantity,
            unit = original.unit,
            custody = SamplingSampleCustodyVo(EnumSampleCustody.INTERNAL,
                legacy?.remainingLocation ?: original.sampleLocation,
                legacy?.remainingCustodianUserId ?: original.custodianUserId),
            lastOccurredAt = legacy?.analyzedAt ?: original.takenAt,
            analysisCount = if (legacy == null) 0 else 1,
            events = emptyList(),
        )
        return readEvents(p, sample.id.value, state, prefix(sample.id.value), false, verify)
    }

    fun readCustody(p: CollectionSamplingPlanDao, sampleId: String, completedState: SamplingSampleJournalVo,
        stopped: SamplingStoppedClosureVo? = null, verify: (String, Any) -> Unit): SamplingSampleJournalVo {
        val completed = p.completed
        val closedAt = completed?.completedAt ?: stopped?.closedAt ?: fail()
        if (completed != null && !completedState.readyForCompletion || completed == null && stopped?.reviewCurrent != true ||
            completedState.lastOccurredAt.isAfter(closedAt)) fail()
        val basis = if (completed != null) mapOf("sampleId" to sampleId, "planId" to p.id.value,
            "completed" to completed, "sampleRevision" to completedState.revision)
        else mapOf("sampleId" to sampleId, "planId" to p.id.value, "stoppedRevision" to stopped!!.revision,
            "closedAt" to closedAt, "sampleRevision" to completedState.revision)
        val initial = completedState.copy(
            revision = digest(json(basis)),
            lastOccurredAt = closedAt, events = emptyList(),
            pendingResolutionSequence = null, confirmedResolutionUserIds = emptySet(),
            portions = completedState.portions.mapValues { (_, v) -> v.copy(lastOccurredAt = closedAt) },
        )
        return readEvents(p, sampleId, initial, custodyPrefix(sampleId), true, verify)
    }

    private fun readEvents(p: CollectionSamplingPlanDao, sampleId: String, initial: SamplingSampleJournalVo,
        prefix: String, custodyOnly: Boolean, verify: (String, Any) -> Unit): SamplingSampleJournalVo {
        val events = CollectionPermanentArchiveDao.find {
            (CollectionPermanentArchives.bizType eq CollectionConst.BIZ_SAMPLING) and
                (CollectionPermanentArchives.bizId eq p.id.value) and (CollectionPermanentArchives.phase like "$prefix%")
        }.map { archive ->
            val event = try { JsonUtils.fromJson(archive.snapshot, SamplingSampleEventVo::class.java)!! }
                catch (_: Exception) { fail() }
            if (archive.phase != "$prefix${event.sequence}" || event.sampleId != sampleId || !custodyOnly && (event.analysisAuthorization != null || event.analysis?.analysisPlanSequence != null)) fail()
            verify(archive.phase, event)
            event
        }.sortedBy { it.sequence }
        var state = initial
        val amendments=if(custodyOnly || events.any {it.analysisAuthorization!=null})SamplingAnalysisAmendment.read(p,sampleId,verify) else null
        for (event in events) {
            event.analysisAuthorization?.let {SamplingAnalysisAmendment.authorized(amendments ?: fail(),it)}
            if(custodyOnly && event.analysis!=null) {
                val authorization=state.events.lastOrNull {it.analysisAuthorization!=null}?.analysisAuthorization ?: fail()
                val policy=SamplingAnalysisAmendment.authorized(amendments ?: fail(),authorization)
                val a=event.analysis
                val target=policy.targets.singleOrNull {it.portionKey==a.portionKey} ?: fail()
                val consumed=state.events.mapNotNull {it.analysis}.filter {it.analysisPlanSequence==a.analysisPlanSequence && it.portionKey==a.portionKey}.fold(BigDecimal.ZERO) {sum,v->sum+v.consumedQuantity}
                if(a.analysisPlanSequence!=authorization.proposalSequence || a.laboratoryName!=target.laboratoryName ||
                    a.analyzedAt.isBefore(authorization.activatedAt) || a.analyzedAt.isBefore(policy.plannedStart) || a.analyzedAt.isAfter(policy.plannedEnd) || consumed+a.consumedQuantity>target.maximumConsumption)fail()
            }
            state = advance(state, event)
        }
        amendments?.events?.filter {it.activation!=null}?.forEach {e->
            val a=e.activation!!
            val expected=SamplingAnalysisAuthorizationVo(a.sampleRevision,a.proposalSequence,e.sequence,a.activatedAt)
            if(events.count {it.analysisAuthorization==expected}!=1)fail()
        }
        return state
    }

    fun portionKey(event: SamplingSampleEventVo): String? = event.analysis?.portionKey ?: event.handover?.portionKey ?:
        event.discrepancy?.portionKey ?: event.resolution?.portionKey ?: event.confirmation?.record?.portionKey ?: event.split?.portionKey ?:
        event.disposition?.portionKey ?: event.dispositionConfirmation?.record?.portionKey

    /** 所有分份共用原样品的顺序与版本；本地数量、保管和核查状态独立。 */
    fun target(state: SamplingSampleJournalVo, key: String?): SamplingSampleJournalVo {
        if (state.portions.isEmpty()) { if (key != null) fail(); return state }
        val p = key?.let { state.portions[it] } ?: fail()
        return state.copy(remainingQuantity = p.remainingQuantity, custody = p.custody, lastOccurredAt = p.lastOccurredAt,
            analysisCount = p.analysisCount, openDiscrepancySequences = p.openDiscrepancySequences,
            pendingResolutionSequence = p.pendingResolutionSequence, confirmedResolutionUserIds = p.confirmedResolutionUserIds,
            observedQuantity = p.observedQuantity, portions = emptyMap(), pendingDispositionSequence = p.pendingDispositionSequence,
            confirmedDispositionUserIds = p.confirmedDispositionUserIds, nonAnalysisDisposedQuantity = p.nonAnalysisDisposedQuantity,
            accountedShortageQuantity = p.accountedShortageQuantity, accountedExcessQuantity = p.accountedExcessQuantity, finalAccountingCount = p.finalAccountingCount)
    }

    fun advance(state: SamplingSampleJournalVo, event: SamplingSampleEventVo): SamplingSampleJournalVo {
        event.analysisAuthorization?.let {a->
            if(listOfNotNull(event.analysis,event.handover,event.discrepancy,event.resolution,event.confirmation,event.split,event.disposition,event.dispositionConfirmation).isNotEmpty() ||
                event.sequence!=state.events.size+1 || event.from!=state.custody || a.previousRevision!=state.revision || a.activatedAt.isBefore(state.lastOccurredAt) ||
                state.openDiscrepancySequences.isNotEmpty() || state.dispositionPending)fail()
            return state.copy(revision=digest(json(event)),events=state.events+event,lastOccurredAt=a.activatedAt,
                portions=state.portions.mapValues {(_,v)->v.copy(lastOccurredAt=a.activatedAt)})
        }
        val key = portionKey(event)
        val target = target(state, key)
        if (listOfNotNull(event.analysis, event.handover, event.discrepancy, event.resolution, event.confirmation, event.split, event.disposition, event.dispositionConfirmation).size != 1) fail()
        if ((event.analysis != null || event.handover != null || event.split != null) &&
            (state.openDiscrepancySequences.isNotEmpty() || state.dispositionPending)) fail()
        if ((event.disposition != null || event.dispositionConfirmation != null) && state.openDiscrepancySequences.isNotEmpty()) fail()
        val split = event.split
        if (split != null) {
            if (event.sequence != state.events.size + 1 || event.from != target.custody || split.previousRevision != state.revision ||
                split.splitAt.isBefore(target.lastOccurredAt) || target.remainingQuantity <= BigDecimal.ZERO ||
                split.portions.size < 2 || split.portions.map { it.key }.distinct().size != split.portions.size ||
                split.portions.any { !it.key.matches(Regex("[A-Za-z0-9_-]{1,64}")) || it.key in state.usedPortionKeys || it.quantity <= BigDecimal.ZERO || it.location.isBlank() } ||
                split.portions.fold(BigDecimal.ZERO) { sum, p -> sum + p.quantity }.compareTo(target.remainingQuantity) != 0) fail()
            val children = split.portions.associate { p -> p.key to SamplingPortionStateVo(p.key, key, split.splitAt,
                p.quantity, p.quantity, target.custody.copy(location = p.location), split.splitAt) }
            val portions = state.portions.filterKeys { it != key } + children
            return aggregate(state.copy(revision = digest(json(event)), events = state.events + event,
                usedPortionKeys = state.usedPortionKeys + children.keys), portions)
        }
        val next = advanceLocal(target, event)
        if (key == null) return next
        val old = state.portions.getValue(key)
        val changed = old.copy(remainingQuantity = next.remainingQuantity, custody = next.custody,
            lastOccurredAt = next.lastOccurredAt, analysisCount = next.analysisCount,
            openDiscrepancySequences = next.openDiscrepancySequences, pendingResolutionSequence = next.pendingResolutionSequence,
            confirmedResolutionUserIds = next.confirmedResolutionUserIds, observedQuantity = next.observedQuantity,
            pendingDispositionSequence = next.pendingDispositionSequence, confirmedDispositionUserIds = next.confirmedDispositionUserIds,
            nonAnalysisDisposedQuantity = next.nonAnalysisDisposedQuantity, accountedShortageQuantity = next.accountedShortageQuantity,
            accountedExcessQuantity = next.accountedExcessQuantity, finalAccountingCount = next.finalAccountingCount)
        return aggregate(state.copy(revision = next.revision, events = next.events,
            analysisCount = state.analysisCount + if (event.analysis == null) 0 else 1,
            nonAnalysisDisposedQuantity = state.nonAnalysisDisposedQuantity + next.nonAnalysisDisposedQuantity - target.nonAnalysisDisposedQuantity,
            accountedShortageQuantity = state.accountedShortageQuantity + next.accountedShortageQuantity - target.accountedShortageQuantity,
            accountedExcessQuantity = state.accountedExcessQuantity + next.accountedExcessQuantity - target.accountedExcessQuantity,
            finalAccountingCount = state.finalAccountingCount + next.finalAccountingCount - target.finalAccountingCount), state.portions + (key to changed))
    }

    private fun aggregate(state: SamplingSampleJournalVo, portions: Map<String, SamplingPortionStateVo>): SamplingSampleJournalVo = state.copy(
        portions = portions,
        remainingQuantity = portions.values.fold(BigDecimal.ZERO) { sum, p -> sum + p.remainingQuantity },
        lastOccurredAt = maxOf(state.lastOccurredAt, portions.values.maxOf { it.lastOccurredAt }),
        openDiscrepancySequences = portions.values.flatMap { it.openDiscrepancySequences }.toSet(),
        pendingResolutionSequence = null, confirmedResolutionUserIds = emptySet(), observedQuantity = null,
        pendingDispositionSequence = null, confirmedDispositionUserIds = emptySet(),
    )

    private fun advanceLocal(state: SamplingSampleJournalVo, event: SamplingSampleEventVo): SamplingSampleJournalVo {
        val a = event.analysis
        val h = event.handover
        val d = event.discrepancy
        val r = event.resolution
        val c = event.confirmation
        val disposal = event.disposition
        val dc = event.dispositionConfirmation
        if (event.sequence != state.events.size + 1 || event.from != state.custody ||
            listOfNotNull(a, h, d, r, c, disposal, dc).size != 1) fail()
        val previous = a?.previousRevision ?: h?.previousRevision ?: d?.previousRevision ?: r?.previousRevision ?: c?.record?.previousRevision ?: disposal?.previousRevision ?: dc!!.record.previousRevision
        val occurredAt = a?.analyzedAt ?: h?.handedAt ?: d?.observedAt ?: r?.reviewedAt ?: c?.record?.confirmedAt ?: disposal?.disposedAt ?: dc!!.record.confirmedAt
        // 差异可后补发现时间，不能为满足顺序而倒改既有分析或交接事实；恢复办理仍采用最新核实终点。
        if (previous != state.revision || d == null && occurredAt.isBefore(state.lastOccurredAt)) fail()
        var next = state.copy(revision = digest(json(event)),
            lastOccurredAt = maxOf(occurredAt, state.lastOccurredAt), events = state.events + event)
        if (a != null || h != null) {
            if (state.remainingQuantity <= BigDecimal.ZERO || state.openDiscrepancySequences.isNotEmpty()) fail()
            if (a != null && (a.consumedQuantity < BigDecimal.ZERO || a.remainingQuantity < BigDecimal.ZERO ||
                    (a.consumedQuantity + a.remainingQuantity).compareTo(state.remainingQuantity) != 0 ||
                    state.events.any { it.analysis?.batchNumber == a.batchNumber && portionKey(it) == a.portionKey })) fail()
            if (h != null && h.quantity.compareTo(state.remainingQuantity) != 0) fail()
            next = next.copy(remainingQuantity = a?.remainingQuantity ?: state.remainingQuantity,
                custody = h?.destination ?: state.custody.copy(location = a?.remainingLocation ?: state.custody.location),
                analysisCount = state.analysisCount + if (a == null) 0 else 1)
        }
        if (d != null) {
            if (d.observedQuantity < BigDecimal.ZERO || state.openDiscrepancySequences.isEmpty() &&
                d.observedQuantity.compareTo(state.remainingQuantity) == 0 && d.identityConfirmed && d.conditionSafe && d.observedCustody == state.custody) fail()
            next = next.copy(openDiscrepancySequences = state.openDiscrepancySequences + event.sequence,
                pendingResolutionSequence = null, confirmedResolutionUserIds = emptySet(),
                pendingDispositionSequence = null, confirmedDispositionUserIds = emptySet(),
                observedQuantity = d.observedQuantity, custody = d.observedCustody ?: state.custody)
        }
        if (r != null) {
            if (state.openDiscrepancySequences.isEmpty() || r.discrepancySequences != state.openDiscrepancySequences ||
                r.actualQuantity < BigDecimal.ZERO || r.conservationUserId == r.keeperUserId) fail()
            r.finalAccounting?.let { validateFinalAccounting(state, r) }
            next = next.copy(pendingResolutionSequence = event.sequence, confirmedResolutionUserIds = emptySet(),
                observedQuantity = r.actualQuantity, custody = r.actualCustody ?: state.custody)
        }
        if (c != null) {
            val proposal = state.events.singleOrNull { it.sequence == c.record.resolutionSequence }?.resolution ?: fail()
            if (state.pendingResolutionSequence != c.record.resolutionSequence || state.openDiscrepancySequences.isEmpty() ||
                c.operatorUserId !in setOf(proposal.conservationUserId, proposal.keeperUserId) ||
                c.operatorUserId in state.confirmedResolutionUserIds) fail()
            val confirmed = state.confirmedResolutionUserIds + c.operatorUserId
            val finalized = confirmed == setOf(proposal.conservationUserId, proposal.keeperUserId) && proposal.finalAccounting != null
            if (proposal.finalAccounting != null) validateFinalAccounting(state, proposal)
            val resolved = finalized || confirmed == setOf(proposal.conservationUserId, proposal.keeperUserId) &&
                proposal.actualQuantity.compareTo(state.remainingQuantity) == 0 && proposal.identityConfirmed && proposal.safeToContinue &&
                (proposal.actualQuantity.compareTo(BigDecimal.ZERO) == 0 || proposal.actualCustody != null)
            next = next.copy(confirmedResolutionUserIds = confirmed,
                openDiscrepancySequences = if (resolved) emptySet() else state.openDiscrepancySequences,
                pendingResolutionSequence = if (resolved) null else state.pendingResolutionSequence,
                observedQuantity = if (resolved) null else state.observedQuantity)
            if (finalized) {
                val delta = proposal.actualQuantity - state.remainingQuantity
                next = next.copy(remainingQuantity = proposal.actualQuantity, finalAccountingCount = state.finalAccountingCount + 1,
                    accountedShortageQuantity = state.accountedShortageQuantity + if (proposal.finalAccounting!!.kind == EnumSampleFinalAccounting.SHORTAGE) -delta else BigDecimal.ZERO,
                    accountedExcessQuantity = state.accountedExcessQuantity + if (proposal.finalAccounting!!.kind == EnumSampleFinalAccounting.EXCESS) delta else BigDecimal.ZERO,
                    nonAnalysisDisposedQuantity = state.nonAnalysisDisposedQuantity + if (proposal.finalAccounting!!.kind == EnumSampleFinalAccounting.NON_ANALYSIS_DISPOSITION) -delta else BigDecimal.ZERO)
            }
        }
        if (disposal != null) {
            if (state.pendingDispositionSequence != null || state.remainingQuantity <= BigDecimal.ZERO || disposal.disposedQuantity <= BigDecimal.ZERO || disposal.remainingQuantity < BigDecimal.ZERO ||
                (disposal.disposedQuantity + disposal.remainingQuantity).compareTo(state.remainingQuantity) != 0 ||
                disposal.conservationUserId == disposal.keeperUserId ||
                disposal.remainingQuantity > BigDecimal.ZERO && disposal.remainingLocation.isNullOrBlank()) fail()
            next = next.copy(pendingDispositionSequence = event.sequence, confirmedDispositionUserIds = emptySet())
        }
        if (dc != null) {
            val proposal = state.events.singleOrNull { it.sequence == dc.record.dispositionSequence }?.disposition ?: fail()
            if (state.pendingDispositionSequence != dc.record.dispositionSequence || proposal.portionKey != dc.record.portionKey ||
                dc.operatorUserId !in setOf(proposal.conservationUserId, proposal.keeperUserId) || dc.operatorUserId in state.confirmedDispositionUserIds) fail()
            val confirmed = state.confirmedDispositionUserIds + dc.operatorUserId
            val complete = confirmed == setOf(proposal.conservationUserId, proposal.keeperUserId)
            next = next.copy(confirmedDispositionUserIds = confirmed, pendingDispositionSequence = if (complete) null else state.pendingDispositionSequence,
                remainingQuantity = if (complete) proposal.remainingQuantity else state.remainingQuantity,
                nonAnalysisDisposedQuantity = state.nonAnalysisDisposedQuantity + if (complete) proposal.disposedQuantity else BigDecimal.ZERO,
                custody = if (complete && proposal.remainingQuantity > BigDecimal.ZERO) state.custody.copy(location = proposal.remainingLocation!!) else state.custody)
        }
        return next
    }

    fun validateFinalAccounting(state: SamplingSampleJournalVo, value: SamplingSampleResolutionVo) {
        val final = value.finalAccounting ?: return
        val delta = value.actualQuantity - state.remainingQuantity
        if (value.actualQuantity < BigDecimal.ZERO ||
            (if (final.kind == EnumSampleFinalAccounting.EXCESS) delta <= BigDecimal.ZERO else delta >= BigDecimal.ZERO) ||
            value.actualQuantity > BigDecimal.ZERO && (!value.identityConfirmed || !value.safeToContinue || value.actualCustody == null)) fail()
    }
}
