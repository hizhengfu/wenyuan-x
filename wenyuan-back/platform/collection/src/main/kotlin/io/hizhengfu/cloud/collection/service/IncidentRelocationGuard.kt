package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.*
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.security.MessageDigest

/** 守卫只依赖持久化事实和永久原件，避免事故、专项作业与来源业务形成服务循环。 */
internal object IncidentRelocationEvidence {
    fun requireOriginal(root:IncidentRelocationDao,row:IncidentRelocationRecordDao) {
        val a=CollectionPermanentArchiveDao.find {(CollectionPermanentArchives.bizType eq CollectionConst.BIZ_INCIDENT_RELOCATION) and (CollectionPermanentArchives.bizId eq root.id.value) and (CollectionPermanentArchives.phase eq "${row.kind}_${row.id.value}")}.singleOrNull() ?: fail("应急搬移永久原件缺失")
        val hash=MessageDigest.getInstance("SHA-256").digest(a.snapshot.toByteArray()).joinToString(""){"%02x".format(it)}
        if(row.orgId!=root.orgId||a.orgId!=root.orgId||a.digest!=hash||JsonUtils.mapper.readTree(a.snapshot)!=JsonUtils.mapper.readTree(row.content))fail("应急搬移记录与永久原件不一致")
    }
    private fun row(root:IncidentRelocationDao,kind:EnumIncidentRelocationKind):IncidentRelocationRecordDao =
        (IncidentRelocationRecordDao.find {(IncidentRelocationRecords.relocationId eq root.id.value) and (IncidentRelocationRecords.kind eq kind)}.singleOrNull() ?: fail("实际搬移原件尚未完整登记")).also {requireOriginal(root,it)}
    fun receipt(root:IncidentRelocationDao,archives:CollectionPermanentArchiveService):IncidentRelocationReceiptVo {
        CollectionOperationSafetyOriginals(archives).relocation(root)
        return JsonUtils.fromJson(row(root,EnumIncidentRelocationKind.RECEIPT).content,IncidentRelocationReceiptSnapshot::class.java)!!.receipt
    }
    fun caseIds(root:IncidentRelocationDao):Set<String> = if(root.mode==EnumIncidentRelocationMode.PLANNED)
        JsonUtils.fromJson(row(root,EnumIncidentRelocationKind.PLAN).content,IncidentRelocationPlanSnapshot::class.java)!!.plan.incidentIds
        else JsonUtils.fromJson(row(root,EnumIncidentRelocationKind.EMERGENCY).content,IncidentEmergencyRelocationSnapshot::class.java)!!.event.incidentIds
    private fun fail(message:String):Nothing=throw BadRequestException(message)
}
@Component
class IncidentRelocationGuard(private val archives:CollectionPermanentArchiveService) : CollectionOperationSafetyGuard, IncidentResolutionGuard {
    private val originals=CollectionOperationSafetyOriginals(archives)
    private fun relocations(collectionId:String)=IncidentRelocationDao.find {IncidentRelocations.collectionId eq collectionId}.toList().onEach(originals::relocation)
    private val active=listOf(EnumIncidentRelocationState.APPROVED,EnumIncidentRelocationState.DEPARTED,EnumIncidentRelocationState.RECEIVED)
    override fun requireExclusiveWork(collectionId:String,bizType:String,bizId:String) {
        if(relocations(collectionId).filter {it.state in active}.any {bizType!=CollectionConst.BIZ_INCIDENT_RELOCATION||it.id.value!=bizId})fail("该实物仍有已批准或未共同确认接收的应急搬移，不能并行批准其他专项作业")
    }
    override fun requireAllowed(collectionId:String,bizType:String?,bizId:String?) {
        if(bizType in setOf(CollectionConst.BIZ_RETURN,CollectionConst.BIZ_INVENTORY,CollectionConst.BIZ_CHANGE))return
        requireSafeResolution(collectionId)
    }
    override fun requireFactsEvidence(orgId:String,facts:IncidentFactsVo) {
        facts.relatedRelocationIds.forEach {id ->
            val r=IncidentRelocationDao.findById(id) ?: fail("关联应急搬移不存在")
            val item=facts.items.singleOrNull {it.collectionId==r.collectionId} ?: fail("应急搬移须属于本版事实中的实物")
            if(r.orgId!=orgId)fail("不得关联外馆搬移原件")
            val end=IncidentRelocationEvidence.receipt(r,archives)
            if(end.unexpectedDamage&&!item.damaged)fail("搬移已经记录额外损伤，事故事实不得省略")
        }
    }
    override fun requireHandlingEvidence(facts:IncidentFactsVo,completedAt:LocalDateTime) {
        facts.relatedRelocationIds.forEach {if(IncidentRelocationEvidence.receipt(IncidentRelocationDao[it],archives).receivedAt.isAfter(completedAt))fail("事故处理完成时间不能早于所关联的实际搬移接收")}
    }
    override fun requireSafeResolution(collectionId:String) {
        val relocations=relocations(collectionId)
        if(relocations.any {it.state in active})fail("该实物仍有已批准或未共同确认接收的应急搬移，不能并行批准其他专项作业")
        relocations.filter {it.state==EnumIncidentRelocationState.COMPLETED}.forEach {r ->
            val end=IncidentRelocationEvidence.receipt(r,archives)
            IncidentRelocationEvidence.caseIds(r).forEach {id ->
                val latest=CollectionIncidentRecordDao.find {(CollectionIncidentRecords.incidentId eq id) and (CollectionIncidentRecords.kind eq EnumIncidentRecordKind.FACTS)}.maxBy {it.sequence}
                val facts=JsonUtils.fromJson(latest.content,IncidentFactsSnapshot::class.java)!!.facts
                if(r.id.value !in facts.relatedRelocationIds || (end.unexpectedDamage&&facts.items.none {it.collectionId==collectionId&&it.damaged}))fail("请将实际搬移接收结果关联各事故的新事实版本并完成相应报告处理；不得省略额外损伤")
            }
        }
    }
    private fun fail(message:String):Nothing=throw BadRequestException(message)
}
