package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.dao.*
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.and
import io.hizhengfu.cloud.collection.service.CollectionFragilityEvidence as E

internal object CollectionFragileMonitoringEvidence {
    fun all(collectionId:String,orgId:String?=null)=CollectionFragileMonitoringDao.find {(CollectionFragileMonitorings.collectionId eq collectionId) and (orgId?.let {CollectionFragileMonitorings.orgId eq it} ?: org.jetbrains.exposed.v1.core.Op.TRUE)}.sortedBy {it.id.value}
    fun evidence(r:CollectionFragileMonitoringDao)=JsonUtils.mapper.readTree(r.evidenceSnapshot)
    fun summary(r:CollectionFragileMonitoringDao):Map<String,Any?> = summary(evidence(r))
    fun summary(root:tools.jackson.databind.JsonNode):Map<String,Any?> {
        return mapOf("from" to root.get("from"),"to" to root.get("to"),"scope" to root.get("scope"),"issues" to root.get("issues"),
            "needsHandling" to root.get("needsHandling"),"previousMonitoringId" to root.get("previousMonitoringId"),
            "metrics" to root.path("metrics").asSequence().map {m->mapOf("metricCode" to m.get("metricCode"),"metricName" to m.get("metricName"),"unit" to m.get("unit"),
                "issues" to m.get("issues"),"spans" to m.get("spans"),"sampleCount" to (m.get("samples")?.size() ?: 0))}.toList())
    }
    fun value(r:CollectionFragileMonitoringDao)=mapOf("collectionId" to r.collectionId,"useRecordId" to r.useRecordId,"request" to r.request,"evidence" to evidence(r))
    fun clearances(r:CollectionFragileMonitoringDao)=CollectionFragileClearanceEvidence.versions(r.orgId,r.id.value,"MONITOR_CLEARED_",r.clearance)
    fun verify(r:CollectionFragileMonitoringDao) {
        E.exact(r.orgId,r.id.value,"MONITORED",value(r))
        val use=CollectionFragileUseRecordDao.findById(r.useRecordId) ?: E.fail("区间监测缺少实际使用来源")
        if(use.orgId!=r.orgId || use.collectionId!=r.collectionId)E.fail("区间监测与实际使用归属不一致")
        E.record(use)
        r.clearance?.let {E.exact(r.orgId,r.id.value,"MONITOR_CLEARED_${it.version}",it)}
        clearances(r)
    }
    fun unsafe(r:CollectionFragileMonitoringDao)=evidence(r).get("needsHandling")?.booleanValue()!=false
}
