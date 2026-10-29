package io.hizhengfu.cloud.museum.service

import org.junit.jupiter.api.Test


internal class YsServiceTest {

    @Test
    fun getAccessToken() {
        val ysService = YsService()
//        ysService.getAccessToken()
        ysService.getStream("G78393042")
    }
}