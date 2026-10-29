package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import io.hizhengfu.cloud.collection.service.CollectionCareEvidence as C
import io.hizhengfu.cloud.collection.service.CollectionJointSafetyEvidence as J

internal object RequisitionRecallEvidence {
    const val PREFIX="EARLY_RETURN_"
    val reasons=setOf("NO_PROTECTION","UNSAFE_SITE")
    fun validate(v:RequisitionRecallVo) {
        if(v.collectionIds.isEmpty() || v.reason !in reasons || listOf(v.safetyFindings,v.protectiveMeasures,v.notification,v.fileIds).any(String::isBlank) || v.returnBy.isBefore(v.issuedAt))
            C.fail("请完整填写逐件提前归库范围、安全原因、防护措施、实际通知及原件，期限不能早于发出时间")
    }
    fun entries(r:RequisitionDao):List<RequisitionRecallRecord> = try {
        val rows=CollectionPermanentArchiveDao.find {(CollectionPermanentArchives.bizType eq CollectionConst.BIZ_REQUISITION) and (CollectionPermanentArchives.bizId eq r.id.value)}
            .filter {it.phase.startsWith(PREFIX)}.sortedWith(compareBy({it.createTime},{it.id.value}))
        if(rows.isEmpty()) emptyList() else {
            val base=J.baseSource(r);val custody=RequisitionCustodyEvidence.entries(r);val result=mutableListOf<RequisitionRecallRecord>()
            rows.forEach {a->
                val e=JsonUtils.fromJson(a.snapshot,RequisitionRecallRecord::class.java) ?: C.fail("提前归库原件无法解析")
                val v=e.request;validate(v)
                val inherited=e.custodyId?.let {id->custody.singleOrNull {it.record.id==id && it.complete} ?: C.fail("提前归库缺少责任承接依据")}
                val actors=RequisitionCustodyEvidence.actors(r,inherited)
                val out=CollectionPermanentArchiveDao.findById(e.outboundArchiveId) ?: C.fail("提前归库缺少实际出库原件")
                if(a.orgId!=r.orgId || e.orgId!=r.orgId || e.requisitionId!=r.id.value || a.phase!=PREFIX+e.id || a.digest!=C.digest(a.snapshot) ||
                    v.previousId!=result.lastOrNull()?.id || e.sourceSnapshot!=base.snapshot || v.sourceRevision!=C.digest(RequisitionCustodyEvidence.snapshot(base.snapshot,inherited)) ||
                    !base.collectionIds.containsAll(v.collectionIds) || e.keeperUserId!=actors["KEEPER"] || e.useUserId!=actors["USE"] ||
                    e.registeredAt.isBefore(v.issuedAt) || base.handedAt==null || v.issuedAt.isBefore(base.handedAt) ||
                    !v.returnBy.isBefore(base.returnAt) || inherited?.effectiveAt?.let {v.issuedAt.isBefore(it)}==true ||
                    out.orgId!=r.orgId || out.bizType!=CollectionConst.BIZ_REQUISITION || out.bizId!=r.id.value || out.phase!="OUT_EXECUTED" || out.digest!=e.outboundDigest || out.digest!=C.digest(out.snapshot))
                    C.fail("提前归库所属馆、来源、责任、时间或原件依据不一致")
                CollectionResponsibilityArchiveEvidence.references(a,v.fileIds)
                result.add(e)
            }
            result
        }
    } catch(_:tools.jackson.core.JacksonException) {C.fail("提前归库原件无法解析，请核查历史记录")}
}
