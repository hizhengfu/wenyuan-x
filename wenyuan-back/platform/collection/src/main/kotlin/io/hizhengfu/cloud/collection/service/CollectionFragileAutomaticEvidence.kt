package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.service.CollectionFragilityEvidence as E
import org.jetbrains.exposed.v1.core.*
import java.time.LocalDateTime

internal object CollectionFragileAutomaticEvidence {
    fun all(collectionId:String)=CollectionFragileAutomaticCheckDao.find {CollectionFragileAutomaticChecks.collectionId eq collectionId}.sortedBy {it.id.value}
    fun forUse(useId:String)=CollectionFragileAutomaticCheckDao.find {CollectionFragileAutomaticChecks.useRecordId eq useId}.sortedBy {it.id.value}
    fun evidence(r:CollectionFragileAutomaticCheckDao)=JsonUtils.mapper.readTree(r.evidenceSnapshot)
    fun value(r:CollectionFragileAutomaticCheckDao)=mapOf("collectionId" to r.collectionId,"useRecordId" to r.useRecordId,"observedAt" to r.observedAt,"from" to r.fromAt,"to" to r.toAt,"needsHandling" to r.needsHandling,"evidence" to evidence(r))
    fun clearances(r:CollectionFragileAutomaticCheckDao)=CollectionFragileClearanceEvidence.versions(r.orgId,r.id.value,"AUTO_CLEARED_",r.clearance)
    fun verify(r:CollectionFragileAutomaticCheckDao) {
        E.exact(r.orgId,r.id.value,"AUTOMATIC_MONITORED",value(r))
        val use=CollectionFragileUseRecordDao.findById(r.useRecordId) ?: E.fail("自动核验缺少实际使用来源")
        if(use.orgId!=r.orgId || use.collectionId!=r.collectionId)E.fail("自动核验与实际使用归属不一致")
        E.record(use)
        r.clearance?.let {E.exact(r.orgId,r.id.value,"AUTO_CLEARED_${it.version}",it)}
        clearances(r)
    }
    fun summary(r:CollectionFragileAutomaticCheckDao)=mapOf("id" to r.id.value,"observedAt" to r.observedAt,"from" to r.fromAt,"to" to r.toAt,"needsHandling" to r.needsHandling,"clearance" to r.clearance,"clearances" to clearances(r),
        "evidence" to CollectionFragileMonitoringEvidence.summary(evidence(r)))
    fun runtime(use:CollectionFragileUseRecordDao,at:LocalDateTime):Map<String,Any?> {
        val state=CollectionFragileMonitorRuntimeDao.findById(use.id)
        if(state!=null && (state.orgId!=use.orgId || state.collectionId!=use.collectionId))E.fail("自动核验运行状态与实际使用归属不一致")
        val checks=forUse(use.id.value)
        val last=checks.lastOrNull()?.also(::verify)
        val target=use.ended?.endedAt ?: at
        val legacy=state==null && checks.isEmpty() && use.ended!=null
        val overdue=!legacy && use.started!=null && (last?.toAt ?: use.started!!.startedAt)<target.minusMinutes(2)
        return mapOf("legacy" to legacy,"lastAttemptAt" to state?.lastAttemptAt,"lastError" to state?.lastError,"checkedThrough" to last?.toAt,"lastObservedAt" to last?.observedAt,
            "overdue" to overdue,"total" to checks.size,"problemCount" to checks.count {it.needsHandling},"unclearedCount" to checks.count {it.needsHandling && it.clearance==null},
            "completed" to (state?.completed==true),"scope" to "系统核验已知采样证据，不代表人工已检查、已停止实物使用或已完成处置。")
    }
}
