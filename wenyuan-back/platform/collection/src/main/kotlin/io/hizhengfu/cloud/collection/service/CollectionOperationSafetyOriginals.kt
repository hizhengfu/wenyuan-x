package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.*

/** Validate every consumed phase before mutable state can exclude it from a safety decision. */
internal class CollectionOperationSafetyOriginals(private val archives: CollectionPermanentArchiveService,private val verifyContent:Boolean=true) {
    private fun verify(org: String, biz: String, id: String, phases: Map<String, String?>) {
        val rows=CollectionPermanentArchiveDao.find {
            (CollectionPermanentArchives.bizType eq biz) and (CollectionPermanentArchives.bizId eq id)
        }.toList()
        if(rows.size!=phases.size || rows.map {it.phase}.toSet()!=phases.keys)
            fail("安全作业永久原件的阶段清单缺失或不一致")
        rows.forEach { archive ->
            // The caller first compares the sealed body with the actual business record.
            // verifyExisting then validates declared references, frozen identity and actual bytes.
            if(!archives.verifyExisting(archive,org,biz,id,archive.phase,JsonUtils.mapper.readTree(archive.snapshot),phases[archive.phase],verifyContent=verifyContent))
                fail("安全作业永久原件缺少内容基线，须核对原件与备份后再办理")
        }
    }
    private fun signatures(a: CollectionCareSignature?, b: CollectionCareSignature?) = buildMap<String,String?> {
        a?.let {put("CONSERVATION",it.record.fileIds)}
        b?.let {put("KEEPER",it.record.fileIds)}
    }
    fun carePlan(p: CollectionCarePlanDao) {
        CollectionCareEvidence.plan(p)
        verify(p.orgId,CollectionConst.BIZ_CARE,p.id.value,
            mapOf("PLAN" to p.content.planFileIds)+signatures(p.conservation,p.keeper))
    }
    fun careCheck(c: CollectionCareCheckDao) {
        CollectionCareEvidence.check(c)
        val phases=mutableMapOf<String,String?>("CHECKED" to "${c.content.conditionFileIds},${c.content.signedFileIds}")
        CollectionCareEvidence.clearances(c.orgId,c.id.value,c.clearance).forEach {phases["CLEARED_${it.version}"]=it.record.signedFileIds}
        verify(c.orgId,CollectionConst.BIZ_CARE,c.id.value,phases)
    }
    fun careWork(w: CollectionCareWorkDao) {
        CollectionCareEvidence.work(w)
        carePlan(CollectionCarePlanDao[w.planId]);careCheck(CollectionCareCheckDao[w.started.checkId])
        val phases=mutableMapOf<String,String?>("STARTED" to w.started.signedFileIds)
        w.ended?.let {phases["ENDED"]="${it.conditionFileIds},${it.signedFileIds}"}
        CollectionCareEvidence.clearances(w.orgId,w.id.value,w.clearance).forEach {phases["CLEARED_${it.version}"]=it.record.signedFileIds}
        verify(w.orgId,CollectionConst.BIZ_CARE,w.id.value,phases)
    }
    private fun fragileAssessment(a: CollectionFragilityAssessmentDao) {
        CollectionFragilityEvidence.assessment(a)
        verify(a.orgId,CollectionConst.BIZ_FRAGILITY,a.id.value,
            mapOf("ASSESSMENT" to "${a.content.assessmentFileIds},${a.content.historyReviewFileIds}")+signatures(a.conservation,a.keeper))
    }
    private fun fragilePlan(p: CollectionFragileUsePlanDao) {
        CollectionFragilityEvidence.plan(p)
        fragileAssessment(CollectionFragilityAssessmentDao[p.content.assessmentId])
        verify(p.orgId,CollectionConst.BIZ_FRAGILITY,p.id.value,
            mapOf("USE_PLAN" to (listOf(p.content.signedPlanFileIds)+p.content.environmentSources.map {it.qualificationFileIds}).joinToString(","))+signatures(p.conservation,p.keeper))
    }
    private fun fragileAdjustment(a: CollectionFragileUseAdjustmentDao) {
        CollectionFragileAdjustmentEvidence.verify(a)
        val check=CollectionCareCheckDao.findById(a.content.checkId) ?: fail("使用变更缺少现状检查原件")
        if(check.orgId!=a.orgId || check.collectionId!=a.collectionId)fail("使用变更的现状检查原件归属不一致")
        careCheck(check)
        val phases=mutableMapOf<String,String?>("ADJUSTMENT" to listOfNotNull(a.content.signedFileIds,a.content.borrowerAgreementFileIds,
            "${check.content.conditionFileIds},${check.content.signedFileIds}").joinToString(","))
        CollectionFragileAdjustmentEvidence.signatures(a).forEach {(role,_,signature)->signature?.let {phases["ADJUSTMENT_$role"]=it.record.fileIds}}
        a.withdrawal?.let {phases["ADJUSTMENT_WITHDRAWN"]=it.signedFileIds}
        verify(a.orgId,CollectionConst.BIZ_FRAGILITY,a.id.value,phases)
    }
    fun fragileRecord(r: CollectionFragileUseRecordDao) {
        CollectionFragilityEvidence.record(r)
        val phases=mutableMapOf<String,String?>()
        r.history?.let {phases["HISTORY"]=it.fileIds} ?: run {
            val plan=r.planId?.let(CollectionFragileUsePlanDao::findById) ?: fail("实际使用缺少原方案")
            fragilePlan(plan)
            CollectionFragileAdjustmentEvidence.all(plan.id.value).forEach(::fragileAdjustment)
            r.started?.let {start ->
                phases["STARTED"]=start.signedFileIds
                if(r.environmentArchiveId!=null)phases["ENVIRONMENT_VERIFIED"]=(listOf(start.signedFileIds)+start.environmentReadings.map {it.signedFileIds}).joinToString(",")
            }
            r.ended?.let {phases["ENDED"]=it.signedFileIds}
            r.review?.let {careCheck(CollectionCareCheckDao[it.checkId]);phases["REVIEWED"]=it.signedFileIds}
        }
        verify(r.orgId,CollectionConst.BIZ_FRAGILITY,r.id.value,phases)
    }
    fun fragileMonitoring(r: CollectionFragileMonitoringDao) {
        CollectionFragileMonitoringEvidence.verify(r)
        fragileRecord(CollectionFragileUseRecordDao[r.useRecordId])
        verify(r.orgId,CollectionConst.BIZ_FRAGILITY,r.id.value,
            mapOf("MONITORED" to (listOf(r.request.signedFileIds)+r.request.readings.map {it.signedFileIds}).joinToString(","))+
                CollectionFragileMonitoringEvidence.clearances(r).associate {"MONITOR_CLEARED_${it.version}" to it.record.signedFileIds})
    }
    fun fragileAutomatic(r: CollectionFragileAutomaticCheckDao) {
        CollectionFragileAutomaticEvidence.verify(r)
        fragileRecord(CollectionFragileUseRecordDao[r.useRecordId])
        verify(r.orgId,CollectionConst.BIZ_FRAGILITY,r.id.value,
            mapOf("AUTOMATIC_MONITORED" to null)+CollectionFragileAutomaticEvidence.clearances(r).associate {"AUTO_CLEARED_${it.version}" to it.record.signedFileIds})
    }
    private inline fun <reified T> read(content:String):T = JsonUtils.fromJson(content,T::class.java) ?: fail("安全作业原件无法解析")
    fun work(root: IncidentWorkDao) {
        IncidentSafetyLifecycleEvidence.work(root)
        val phases=IncidentWorkRecordDao.find {IncidentWorkRecords.workId eq root.id.value}.associate {row ->
            val files=when(row.kind) {
                EnumIncidentWorkRecordKind.PLAN -> read<IncidentWorkPlanSnapshot>(row.content).plan.let {"${it.siteAndAssignmentFileIds},${it.signedPlanFileIds}"}
                EnumIncidentWorkRecordKind.CONFIRMATION -> read<IncidentWorkConfirmationSnapshot>(row.content).content.fileIds
                EnumIncidentWorkRecordKind.STARTED -> read<IncidentWorkStartVo>(row.content).signedStartFileIds
                EnumIncidentWorkRecordKind.OBSERVATION -> read<IncidentWorkObservationVo>(row.content).fileIds
                EnumIncidentWorkRecordKind.ENDED -> read<IncidentWorkEndVo>(row.content).signedEndFileIds
                EnumIncidentWorkRecordKind.CANCELED -> read<IncidentWorkCancelVo>(row.content).fileIds
            }
            "${row.kind}_${row.id.value}" to files
        }
        verify(root.orgId,CollectionConst.BIZ_INCIDENT_WORK,root.id.value,phases)
    }
    fun relocation(root: IncidentRelocationDao) {
        IncidentSafetyLifecycleEvidence.relocation(root)
        var departureFiles:String?=null
        val phases=IncidentRelocationRecordDao.find {IncidentRelocationRecords.relocationId eq root.id.value}.associate {row ->
            val files=when(row.kind) {
                EnumIncidentRelocationKind.PLAN -> read<IncidentRelocationPlanSnapshot>(row.content).plan.let {"${it.sourceLocationFileIds},${it.destinationSafetyFileIds},${it.carrierCapabilityFileIds},${it.signedPlanFileIds}"}
                EnumIncidentRelocationKind.EMERGENCY -> read<IncidentEmergencyRelocationSnapshot>(row.content).event.instructionFileIds
                EnumIncidentRelocationKind.CONFIRMATION -> read<IncidentWorkConfirmationSnapshot>(row.content).content.fileIds
                EnumIncidentRelocationKind.DEPARTURE -> read<IncidentRelocationDepartureVo>(row.content).let {
                    listOfNotNull(it.accessRecordFileIds,it.signedFileIds,it.packingCheckFileIds).joinToString(",").also {files->departureFiles=files}
                }
                EnumIncidentRelocationKind.OBSERVATION -> read<IncidentWorkObservationVo>(row.content).fileIds
                EnumIncidentRelocationKind.RECEIPT -> read<IncidentRelocationReceiptSnapshot>(row.content).receipt.let {"${it.accessRecordFileIds},${it.signedFileIds}"}
                EnumIncidentRelocationKind.CANCELED -> read<IncidentWorkCancelVo>(row.content).fileIds
            }
            "${row.kind}_${row.id.value}" to files
        }.toMutableMap()
        if(CollectionPermanentArchiveDao.find {
            (CollectionPermanentArchives.bizType eq CollectionConst.BIZ_INCIDENT_RELOCATION) and
                (CollectionPermanentArchives.bizId eq root.id.value) and (CollectionPermanentArchives.phase eq "LOCATIONS_LEFT")
        }.any())phases["LOCATIONS_LEFT"]=departureFiles ?: fail("离开展陈位置原件缺少实际搬出依据")
        verify(root.orgId,CollectionConst.BIZ_INCIDENT_RELOCATION,root.id.value,phases)
    }
    private fun fail(message:String):Nothing=throw BadRequestException(message)
}
