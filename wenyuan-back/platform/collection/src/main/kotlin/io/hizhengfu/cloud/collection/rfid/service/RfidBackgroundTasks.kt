package io.hizhengfu.cloud.collection.rfid.service

import io.hizhengfu.cloud.common.task.CommitDrivenTask
import io.hizhengfu.cloud.collection.rfid.dao.RfidPassageGrants
import io.hizhengfu.cloud.collection.rfid.dao.RfidScanSessions
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.ZoneId

@Component
class RfidGrantBoundaryWorker(private val service:RfidPassageService) : CommitDrivenTask(
    "rfid-grant-boundaries",setOf(RfidPassageGrants),
) {
    override fun runBatch():Instant? {
        service.refreshGrantStatuses()
        return service.nextGrantBoundary()?.atZone(ZoneId.systemDefault())?.toInstant()
    }
}

@Component
class RfidSessionRecoveryWorker(
    private val service:RfidScanSessionService,
) : CommitDrivenTask("rfid-session-recovery",setOf(RfidScanSessions)) {
    override fun runBatch():Instant? {
        service.recoverStaleSessions()
        // 设备命令状态由外部通道提供，仅有活动会话时才需要看门狗检查。
        return if(service.hasActiveSessions())Instant.now().plusSeconds(30) else null
    }
}
