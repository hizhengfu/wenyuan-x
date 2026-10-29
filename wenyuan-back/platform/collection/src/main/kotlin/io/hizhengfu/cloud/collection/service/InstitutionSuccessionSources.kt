package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import org.jetbrains.exposed.v1.core.*

internal data class InstitutionSource(val kind:EnumSuccessionSource,val id:String,val snapshot:Map<String,Any?>) {
    val key get()="${kind.name}:$id"
    fun value()=mapOf("key" to key,"kind" to kind,"id" to id,"snapshot" to snapshot)
}

/** 不合并不同单位的实物数量；同一对象的来源记录仅在存在有效派生记录时折叠。 */
internal object InstitutionSuccessionSources {
    fun sameEvent(plan:InstitutionSuccessionCaseVo,s:CollectionInstitutionSuccessionVo?) = s!=null &&
        s.sourceRegistrationNo==plan.sourceRegistrationNo && s.eventDocumentNo==plan.eventDocumentNo

    fun scan(case:InstitutionSuccessionCaseDao):List<InstitutionSource> {
        val org=case.orgId
        val result=linkedMapOf<String,InstitutionSource>()
        fun add(s:InstitutionSource) {result.putIfAbsent(s.key,s)}
        CollectionDao.find {Collections.orgId eq org}.forEach {c->add(InstitutionSource(EnumSuccessionSource.COLLECTION,c.id.value,mapOf(
            "name" to c.name,"recordNo" to c.totalRegNo,"quantity" to c.quantity,"unit" to c.unit,
            "nature" to c.nature,"state" to c.lifeStatus,"warehouseId" to c.warehouseId,
            "location" to c.warehouseId?.let(WarehouseDao::findById)?.takeIf {it.orgId==org}?.name,
            "registeredAt" to c.registeredAt,"historicalConfirmedAt" to c.historicalConfirmedAt,"inventoryHold" to c.inventoryHold)))}
        CollectionAuxiliaryDao.find {CollectionAuxiliaries.orgId eq org}.forEach {a->add(InstitutionSource(EnumSuccessionSource.AUXILIARY,a.id.value,mapOf(
            "name" to a.content.name,"recordNo" to a.recordNo,"quantity" to a.content.quantity,"unit" to a.content.unit,
            "state" to "AUXILIARY","location" to a.content.location,"originCollectionId" to a.content.originCollectionId,"exitId" to a.exitId)))}
        AcquisitionDao.find {Acquisitions.orgId eq org}.forEach {a->
            if(a.collectionId?.let(CollectionDao::findById)==null)add(InstitutionSource(EnumSuccessionSource.ACQUISITION,a.id.value,mapOf(
                "name" to a.name,"quantity" to a.quantity,"unit" to a.unit,"state" to a.flowStatus,
                "acquireDate" to a.acquireDate,"source" to a.source,"linkedCollectionId" to a.collectionId,
                "designatedReceptionId" to a.designatedReceptionId,"receiptCount" to AccessionReceiptDao.find {AccessionReceipts.acquisitionId eq a.id.value}.count())))
        }
        DesignatedReceptionDao.find {DesignatedReceptions.orgId eq org}.forEach {d->
            DesignatedReceptionEvidence.verify(d)
            if(d.acquisitionId?.let(AcquisitionDao::findById)==null && DesignatedReceptionEvidence.last(d.id.value,EnumDesignatedRecord.SETTLEMENT)==null) {
                val records=DesignatedReceptionEvidence.rows(d.id.value)
                add(InstitutionSource(EnumSuccessionSource.DESIGNATED,d.id.value,mapOf("name" to d.content.item.name,
                    "recordNo" to d.content.item.sourceItemNo,"quantity" to d.content.item.quantity,"unit" to d.content.item.unit,
                    "state" to (records.lastOrNull()?.kind?.name ?: "AWAIT_ASSESSMENT"),"documentNo" to d.content.documentNo,
                    "recordIds" to records.map {it.id.value})))
            }
        }
        // 先交接、后归集到统筹案卷的真实记录仍列入范围，避免当前归属变化造成遗漏。
        CollectionTransferDao.find {CollectionTransfers.orgId eq org}.filter {it.executedAt!=null && sameEvent(case.content,it.dossier?.succession)}.groupBy {it.collectionId}.forEach {(_,transfers)->
                val t=transfers.maxWith(compareBy<CollectionTransferDao> {it.executedAt}.thenBy {it.id.value})
                val old=CollectionRetainedLedgerDao.find {CollectionRetainedLedgers.transferId eq t.id.value}.singleOrNull()
                val key="COLLECTION:${t.collectionId}"
                val entry=result[key] ?: InstitutionSource(EnumSuccessionSource.COLLECTION,t.collectionId,mapOf("name" to t.baseline?.formalName,
                    "recordNo" to old?.totalRegNo,"quantity" to t.baseline?.quantity,"unit" to t.baseline?.unit,
                    "state" to "TRANSFER_RECORDED","transferId" to t.id.value,"targetOrgId" to t.toOrgId))
                result[key]=entry.copy(snapshot=entry.snapshot+mapOf("transferIds" to transfers.map {it.id.value}.sorted()))
        }
        return result.values.sortedBy {it.key}
    }
    fun json(value:Any)=JsonUtils.toJson(value)!!
    fun digest(value:Any)=CollectionCareEvidence.digest(json(value))
}
