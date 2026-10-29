package io.hizhengfu.cloud.museum.task

import io.hizhengfu.cloud.common.const.g_logger
import io.hizhengfu.cloud.museum.service.MuAlarmEventService
import io.hizhengfu.cloud.museum.service.ProxyMKXService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
@Transactional
class TaskStatusService(
        val proxyMKXService: ProxyMKXService,
        val muAlarmEventService: MuAlarmEventService,
) {

    var lastDateTime: LocalDateTime = LocalDateTime.now().plusMinutes(5)

    /**
     * 通知订阅地址
     * */
    fun run() {
        proxyMKXService.fetchInfo()

        //处理超时事件
        if (lastDateTime.isBefore(LocalDateTime.now())) {
            g_logger.info("5分钟定时关闭超时任务")
            lastDateTime = LocalDateTime.now().plusMinutes(5)
            muAlarmEventService.timeoutDisposeEvent()
        }
    }
}