package io.hizhengfu.cloud.collection.service

import org.slf4j.LoggerFactory
import io.hizhengfu.cloud.common.task.CommitDrivenTask
import io.hizhengfu.cloud.collection.dao.CollectionFragileUseRecords
import io.hizhengfu.cloud.collection.dao.CollectionFragileMonitorRuntimes
import io.hizhengfu.cloud.collection.dao.CollectionFragileMonitorings
import java.time.Instant
import java.time.ZoneId
import org.springframework.stereotype.Component

/** 调度不持有事务；单件失败不回滚其他藏品，也不伪造已成功核验。 */
@Component
class CollectionFragileAutomaticMonitoringJob(private val service:CollectionFragileAutomaticMonitoringService) : CommitDrivenTask(
    "collection-fragile-monitoring",setOf(CollectionFragileUseRecords,CollectionFragileMonitorRuntimes,CollectionFragileMonitorings),
) {
    private val log=LoggerFactory.getLogger(javaClass)
    fun run() {
        runBatch()
    }
    override fun runBatch():Instant? {
        val ids=service.due()
        var retryFailed=false
        ids.forEach {id->try {service.scan(id)} catch(e:Exception) {
            log.warn("实际使用 {} 自动监测失败",id,e)
            try {service.failed(id)} catch(f:Exception) {retryFailed=true;log.warn("实际使用 {} 自动监测失败状态未保存",id,f)}
        }}
        if(retryFailed)return Instant.now().plusSeconds(60)
        return service.nextWakeAt()?.atZone(ZoneId.systemDefault())?.toInstant()
    }
}
