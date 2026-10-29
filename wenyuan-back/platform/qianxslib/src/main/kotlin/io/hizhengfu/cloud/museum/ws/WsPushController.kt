package io.hizhengfu.cloud.museum.ws

import io.hizhengfu.cloud.museum.service.ProxyMKXService
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.web.bind.annotation.RestController


/**
 * 订阅推送信息
 * */
@RestController
class WsPushController(val proxyMKXService: ProxyMKXService) {

    @MessageMapping("info")
    fun info(value: String?) {
        proxyMKXService.fetchInfo(true)
    }
}