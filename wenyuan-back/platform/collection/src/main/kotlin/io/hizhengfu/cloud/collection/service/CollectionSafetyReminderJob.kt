package io.hizhengfu.cloud.collection.service

import org.slf4j.LoggerFactory
import io.hizhengfu.cloud.common.task.CommitDrivenTask
import io.hizhengfu.cloud.collection.dao.*
import java.time.Instant
import java.time.ZoneId
import org.springframework.stereotype.Component

/** 调度本身不持有事务；各馆巡检和各批投递经服务代理单独提交/回滚。 */
@Component
class CollectionSafetyReminderJob(private val service:CollectionSafetyReminderService) : CommitDrivenTask(
    "collection-safety-reminders", setOf(
        CollectionOrgs,CollectionSafetyReminderSettings,CollectionSafetyReminderDeliveries,
        Collections,CollectionCarePlans,CollectionCareChecks,CollectionCareWorks,
        Requisitions,CollectionJointSafetyPlans,CollectionJointSafetyRounds,Loans,LoanComplianceRecords,
        CollectionEmergencyPlans,CollectionEmergencyDrills,CollectionEmergencyImprovements,
        CollectionEmergencyDuties,CollectionEmergencyDrillRevisions,
        CollectionFragilityAssessments,CollectionFragileUsePlans,CollectionFragileUseRecords,
        CollectionFragileUseAdjustments,CollectionFragileMonitorings,CollectionFragileAutomaticChecks,
        CollectionIncidents,CollectionIncidentRecords,CollectionIncidentItems,
        IncidentWorks,IncidentWorkRecords,IncidentRelocations,IncidentRelocationRecords,
    ),
) {
    private val log=LoggerFactory.getLogger(javaClass)
    fun run() {
        runBatch()
    }
    override fun runBatch():Instant? {
        val orgs=service.enabledOrgs()
        var failed=false
        orgs.forEach {org->try {service.refresh(org)} catch(e:Exception) {failed=true;log.warn("收藏单位 {} 安全待办巡检失败",org,e)}}
        val batches=service.dueDeliveries()
        batches.forEach {id->try {service.deliver(id)} catch(e:Exception) {
            log.warn("安全待办批次 {} 站内提醒投递失败",id,e)
            try {service.failed(id)} catch(failure:Exception) {failed=true;log.warn("安全待办批次 {} 重试记录写入失败",id,failure)}
        }}
        if(failed)return Instant.now().plusSeconds(60)
        if(batches.size>=200)return Instant.now()
        return service.nextWakeAt(orgs)?.atZone(ZoneId.systemDefault())?.toInstant()
    }
}
