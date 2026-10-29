package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.*

internal object DesignatedReceptionEvidence {
    const val BIZ="COLLECTION_DESIGNATED_RECEPTION"
    fun fail(message:String):Nothing=throw BadRequestException(message)
    fun json(value:Any)=JsonUtils.toJson(value)!!
    inline fun <reified T> value(r:DesignatedReceptionRecordDao)=JsonUtils.fromJson(r.content,T::class.java)!!
    fun rows(id:String)=DesignatedReceptionRecordDao.find {DesignatedReceptionRecords.caseId eq id}.sortedBy {it.id.value}
    fun last(id:String,kind:EnumDesignatedRecord)=rows(id).lastOrNull {it.kind==kind}
    fun recordValue(r:DesignatedReceptionRecordDao)=mapOf("caseId" to r.caseId,"kind" to r.kind,"actorUserId" to r.actorUserId,"recordedAt" to r.recordedAt,"content" to JsonUtils.mapper.readTree(r.content))
    fun noticeValue(r:DesignatedReceptionDao)=mapOf("orgId" to r.orgId,"sourceKey" to r.sourceKey,"content" to r.content,"orgSnapshot" to JsonUtils.mapper.readTree(r.orgSnapshot))
    fun exact(org:String,id:String,phase:String,body:Any) {
        val a=CollectionPermanentArchiveDao.find {(CollectionPermanentArchives.bizType eq BIZ) and (CollectionPermanentArchives.bizId eq id) and (CollectionPermanentArchives.phase eq phase)}.singleOrNull() ?: fail("指定接收阶段永久原件缺失")
        if(a.orgId!=org || a.digest!=CollectionCareEvidence.digest(a.snapshot) || JsonUtils.mapper.readTree(a.snapshot)!=JsonUtils.mapper.readTree(json(body)))fail("指定接收永久原件与记录不一致")
    }
    fun verify(r:DesignatedReceptionDao) {
        exact(r.orgId,r.id.value,"NOTICE",noticeValue(r))
        rows(r.id.value).forEach {row->
            if(row.orgId!=r.orgId)fail("指定接收原件所属馆不一致")
            exact(r.orgId,row.id.value,row.kind.name,recordValue(row))
        }
    }
    fun acquisitionValue(a:AcquisitionDao)=mapOf("id" to a.id.value,"orgId" to a.orgId,"designatedReceptionId" to a.designatedReceptionId,
        "name" to a.name,"categoryId" to a.categoryId,"era" to a.era,"texture" to a.texture,"quantity" to a.quantity,"unit" to a.unit,
        "dimensions" to a.dimensions,"weight" to a.weight,"completeness" to a.completeness,"completeStatus" to a.completeStatus,
        "source" to a.source,"holder" to a.holder,"acquireDate" to a.acquireDate,"images" to a.images)
    fun readyValue(r:DesignatedReceptionDao,a:AcquisitionDao)=mapOf("caseId" to r.id.value,"acquisition" to acquisitionValue(a),
        "assessmentId" to last(r.id.value,EnumDesignatedRecord.ASSESSMENT)?.id?.value,"custodyId" to last(r.id.value,EnumDesignatedRecord.CUSTODY)?.id?.value,
        "acceptanceId" to last(r.id.value,EnumDesignatedRecord.ACCEPTANCE)?.id?.value)
    fun requireReceipt(a:AcquisitionDao,receipt:AccessionReceiptVo) {
        if(a.source!=EnumSource.DESIGNATED || receipt.accepted!=true)return
        prepared(a)
        val custody=last(a.designatedReceptionId!!,EnumDesignatedRecord.CUSTODY)?.let {value<DesignatedCustodyVo>(it)} ?: fail("指定接收缺少实际保管原件")
        val actual=custody.pieces.associateBy {it.pieceNo.trim()}
        if(receipt.warehouseId!=custody.warehouseId || receipt.pieces.map {it.pieceNo.trim()}.toSet()!=actual.keys || receipt.pieces.any {actual[it.pieceNo.trim()]?.receivedAt!=it.receivedAt})fail("入馆核收须对应指定接收的原逐件标识、实际收到时间和最后保管库位；差异应如实记录并先行核实")
    }
    fun prepared(a:AcquisitionDao):AcquisitionPreparedSource {
        if(a.source!=EnumSource.DESIGNATED)fail("指定接收来源方式不一致")
        val r=a.designatedReceptionId?.let(DesignatedReceptionDao::findById) ?: fail("指定接收缺少行政来源原件，不能通过普通征集绕过")
        if(r.orgId!=a.orgId || r.acquisitionId!=a.id.value)fail("指定接收与待入馆对象归属不一致")
        verify(r);exact(r.orgId,r.id.value,"READY",readyValue(r,a))
        if(last(r.id.value,EnumDesignatedRecord.REFERRAL)!=null || last(r.id.value,EnumDesignatedRecord.SETTLEMENT)!=null)fail("已报请另行指定的对象不能转为本馆接收")
        val assessment=last(r.id.value,EnumDesignatedRecord.ASSESSMENT)?.let {value<DesignatedAssessmentVo>(it)} ?: fail("指定接收缺少本馆入藏标准鉴定评估")
        if(!assessment.meetsStandards || assessment.opinions.isEmpty() || assessment.opinions.any {!it.meetsStandards})fail("不符合本馆入藏标准，须报请原指定部门另行指定")
        val accepted=last(r.id.value,EnumDesignatedRecord.ACCEPTANCE)?.let {value<DesignatedAcceptanceVo>(it)} ?: fail("尚未实际核对接收及取得依据")
        if(a.acquireDate!=accepted.actualAcquiredOn)fail("指定接收实际取得日期与原件不一致")
        return AcquisitionPreparedSource(r.content.item.nature,accepted.actualAcquiredOn)
    }
}
