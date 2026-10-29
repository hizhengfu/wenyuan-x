package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException

/** 保管交接只追加事实；原产物登记及已经发生的使用、归还保持原文。 */
internal object ReproductionOutputCustody {
    private fun fail():Nothing=throw BadRequestException("产物保管交接与原登记、使用归还或前次交接不一致，请核对永久原件")
    fun ordered(uses:List<ReproductionOutputUseDao>)=uses.sortedWith(compareBy({it.content.occurredAt},{it.id.value}))
    fun revision(out:ReproductionOutputDao,uses:List<ReproductionOutputUseDao>,records:List<ReproductionOutputCustodyRecord>):String =
        CollectionCareEvidence.digest(JsonUtils.toJson(mapOf("orgId" to out.orgId,"planId" to out.planId,"outputId" to out.id.value,
            "output" to out.content,"uses" to uses.map {mapOf("id" to it.id.value,"content" to it.content,"returned" to it.returned)},"custody" to records))!!)

    fun position(out:ReproductionOutputDao,uses:List<ReproductionOutputUseDao>,records:List<ReproductionOutputCustodyRecord>):ReproductionOutputCustodyPosition {
        val last=uses.lastOrNull();val handover=records.lastOrNull()
        if(handover!=null && (last==null || last.id.value in handover.observedUseIds))return handover.record.let {ReproductionOutputCustodyPosition(it.toCustodianUserId,it.toLocation,it.handedOverAt)}
        if(last!=null)return last.returned?.let {ReproductionOutputCustodyPosition(it.custodianUserId,it.location,it.returnedAt)}
            ?: last.content.let {ReproductionOutputCustodyPosition(it.responsibleUserId,it.useLocation,it.occurredAt,true)}
        return out.content.let {ReproductionOutputCustodyPosition(it.custodianUserId,it.location,it.registeredAt)}
    }

    fun verify(out:ReproductionOutputDao,usage:List<ReproductionOutputUseDao>,records:List<ReproductionOutputCustodyRecord>) {
        val uses=ordered(usage)
        if(records.map {it.id}.toSet().size!=records.size)fail()
        records.forEachIndexed {index,saved ->
            val v=saved.record;val subset=uses.take(saved.observedUseIds.size);val previous=records.take(index)
            if(saved.id.isBlank() || saved.id.length>40 || saved.outputId!=out.id.value || saved.version!=index+1 || v.expectedOrgId!=out.orgId ||
                saved.observedUseIds!=subset.map {it.id.value} || subset.any {it.returned==null} ||
                (index>0 && saved.observedUseIds.size<records[index-1].observedUseIds.size) ||
                v.expectedRevision!=revision(out,subset,previous) || saved.previous!=position(out,subset,previous) ||
                v.fromCustodianUserId!=saved.previous.custodianUserId || v.fromLocation!=saved.previous.location ||
                saved.previous.inUse || v.handedOverAt.isBefore(saved.previous.since) ||
                listOf(v.toCustodianUserId,v.toLocation,v.condition,v.reason,v.signedFileIds).any(String::isBlank))fail()
            // 后来追加的使用不能插到已确认交接之前；相同时刻按已保存的交接先后关系核验。
            if(uses.drop(subset.size).any {it.content.occurredAt.isBefore(v.handedOverAt)})fail()
        }
    }
}
