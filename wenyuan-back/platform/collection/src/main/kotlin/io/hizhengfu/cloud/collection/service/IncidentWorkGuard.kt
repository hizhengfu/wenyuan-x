package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.springframework.stereotype.Component

interface IncidentResolutionGuard {
    fun requireExclusiveWork(collectionId: String, bizType: String, bizId: String) {}
    fun requireSafeResolution(collectionId: String)
    fun requireFactsEvidence(orgId: String, facts: IncidentFactsVo) {}
    fun requireHandlingEvidence(facts: IncidentFactsVo, completedAt: java.time.LocalDateTime) {}
}

@Component
class IncidentWorkGuard(private val archives:CollectionPermanentArchiveService) : CollectionOperationSafetyGuard, IncidentResolutionGuard {
    private val originals=CollectionOperationSafetyOriginals(archives)
    private fun works(collectionId:String)=IncidentWorkDao.find {IncidentWorks.collectionId eq collectionId}.toList().onEach(originals::work)
    override fun requireExclusiveWork(collectionId: String, bizType: String, bizId: String) {
        if (works(collectionId).filter {it.state in setOf(EnumIncidentWorkState.APPROVED,EnumIncidentWorkState.STARTED,EnumIncidentWorkState.ENDED)}.any { bizType != CollectionConst.BIZ_INCIDENT_WORK || it.id.value != bizId }) fail("该实物仍有现场防护未取消或未共同确认结束，不能并行批准其他专项作业")
    }
    override fun requireHandlingEvidence(facts: IncidentFactsVo, completedAt: java.time.LocalDateTime) {
        facts.relatedWorkIds.forEach { id ->
            if (evidence(IncidentWorkDao[id]).endedAt.isAfter(completedAt)) fail("事故核查处理完成时间不得早于所关联现场防护的实际结束")
        }
    }
    override fun requireFactsEvidence(orgId: String, facts: IncidentFactsVo) {
        facts.relatedWorkIds.forEach { id ->
            val work = IncidentWorkDao.findById(id) ?: fail("关联现场防护不存在")
            val item = facts.items.singleOrNull { it.collectionId == work.collectionId } ?: fail("关联现场防护须属于本版事实中的实物")
            if (work.orgId != orgId) fail("不得关联外馆现场防护原件")
            val end = evidence(work)
            if (end.unexpectedDamage && !item.damaged) fail("现场防护已记录额外损伤，事故事实不得省略")
        }
    }
    private fun evidence(work: IncidentWorkDao): IncidentWorkEndVo {
        originals.work(work)
        val row = IncidentWorkRecordDao.find { (IncidentWorkRecords.workId eq work.id.value) and (IncidentWorkRecords.kind eq EnumIncidentWorkRecordKind.ENDED) }.singleOrNull() ?: fail("请先如实记录现场防护实际结束，再将结果关联事故事实")
        requireOriginal(work, row)
        return JsonUtils.fromJson(row.content, IncidentWorkEndVo::class.java)!!
    }
    private fun requireOriginal(work: IncidentWorkDao, row: IncidentWorkRecordDao) {
        val a = CollectionPermanentArchiveDao.find { (CollectionPermanentArchives.bizType eq CollectionConst.BIZ_INCIDENT_WORK) and (CollectionPermanentArchives.bizId eq work.id.value) and (CollectionPermanentArchives.phase eq "${row.kind}_${row.id.value}") }.singleOrNull() ?: fail("缺少现场防护永久原件")
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(a.snapshot.toByteArray()).joinToString("") { "%02x".format(it) }
        if (row.orgId != work.orgId || a.orgId != work.orgId || a.digest != hash || JsonUtils.mapper.readTree(a.snapshot) != JsonUtils.mapper.readTree(row.content)) fail("现场防护原件不一致")
    }
    override fun requireAllowed(collectionId: String, bizType: String?, bizId: String?) {
        // 实际接收、清点及信息核实继续按原流程办理；不表示现场防护已结束。
        if (bizType in setOf(CollectionConst.BIZ_RETURN, CollectionConst.BIZ_INVENTORY, CollectionConst.BIZ_CHANGE)) return
        requireSafeResolution(collectionId)
    }
    override fun requireSafeResolution(collectionId: String) {
        val works=works(collectionId)
        if (works.any {it.state in setOf(EnumIncidentWorkState.APPROVED,EnumIncidentWorkState.STARTED,EnumIncidentWorkState.ENDED)}) {
            fail("该藏品现场防护尚未取消或由责任人员确认实际结束，不能解除安全限制或开始其他作业")
        }
        works.filter {it.state==EnumIncidentWorkState.COMPLETED}.forEach { work ->
            val end = evidence(work)
            run {
                val p = IncidentWorkRecordDao.find { (IncidentWorkRecords.workId eq work.id.value) and (IncidentWorkRecords.kind eq EnumIncidentWorkRecordKind.PLAN) }.single()
                requireOriginal(work, p)
                val plan = JsonUtils.fromJson(p.content, IncidentWorkPlanSnapshot::class.java)!!
                plan.plan.incidentIds.forEach { caseId ->
                    val facts = CollectionIncidentRecordDao.find { (CollectionIncidentRecords.incidentId eq caseId) and (CollectionIncidentRecords.kind eq EnumIncidentRecordKind.FACTS) }.maxBy { it.sequence }
                    val value = JsonUtils.fromJson(facts.content, IncidentFactsSnapshot::class.java)!!
                    if (work.id.value !in value.facts.relatedWorkIds || (end.unexpectedDamage && value.facts.items.none { it.collectionId == collectionId && it.damaged })) fail("须先将现场防护实际结束结果关联各事故的新事实版本并完成相应处理；额外损伤不得省略")
                }
            }
        }
    }
    private fun fail(message: String): Nothing = throw BadRequestException(message)
}
