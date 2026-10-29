package io.hizhengfu.cloud.museum.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.common.cache.GenericCache
import io.hizhengfu.cloud.common.utils.HttpClientUtils
import io.hizhengfu.cloud.museum.domain.Addressv2Data
import io.hizhengfu.cloud.museum.domain.TokenData
import io.hizhengfu.cloud.museum.domain.YsWrap
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit


/**

 * 萤石摄像头设备获取
 * */
@Service
class YsService {
    val appKey = "7fce8605b4ce4f888ebb3e7f0d489153"
    val appSecret = "56e201150db0591d3f99696e26c0a4d9"
    val ysAccessTokenKey = "YS_KEY"

    val cache = GenericCache<String>(7L, TimeUnit.DAYS)
    val videoCache = GenericCache<String>(24L, TimeUnit.HOURS)
    val logger = LoggerFactory.getLogger("YsService")

    fun getAccessToken(): String? {
        val params = mapOf(Pair("appKey", appKey), Pair("appSecret", appSecret))
        val headers = mapOf(Pair("Content-Type", "application/x-www-form-urlencoded"))

        if (cache.get(ysAccessTokenKey) == null) {
            HttpClientUtils.post("https://open.ys7.com/api/lapp/token/get", params = params, headers = headers) {
                val data = JsonUtils.fromJson<YsWrap<TokenData>>(it)
                if (data?.code == "200" && data.data != null) {
                    //一周有效期
                    cache.put(ysAccessTokenKey, data.data.accessToken)
                } else {
                    logger.info("获取萤石视频失败:{}", it)
                }
                println(data)
            }
        }
        return cache.get(ysAccessTokenKey)
    }


    /**
     * 获取视频流
     * @param deviceSerial 直播源，例如427734222，均采用英文符号，限制50个
     * @param protocol 流播放协议，1-ezopen、2-hls、3-rtmp、4-flv，默认为1
     * @param quality 视频清晰度，1-高清（主码流）、2-流畅（子码流）
     * view https://open.ys7.com/doc/zh/book/index/address_v2.html
     * */
    fun getStream(deviceSerial: String, protocol: Int = 4, quality: Int = 1): String? {
        val videoKey = "video-${deviceSerial}"

        //已经获取过摄像头数据的
        if (videoCache.get(videoKey) != null) {
            return videoCache.get(videoKey)
        }

        val accessToken = getAccessToken()

        val params = mapOf(
            Pair("accessToken", accessToken),
            Pair("deviceSerial", deviceSerial),
            Pair("protocol", protocol),
            Pair("quality", quality),
            Pair("expireTime", 86400),
        )
        var videoUrl: String? = null
        val headers = mapOf(Pair("Content-Type", "application/x-www-form-urlencoded"))
        HttpClientUtils.post("https://open.ys7.com/api/lapp/v2/live/address/get", params = params, headers = headers) {
            val data = JsonUtils.fromJson<YsWrap<Addressv2Data>>(it)
            if (data?.code == "200") {
                videoUrl = data.data?.url

                videoUrl?.let { url ->
                    videoCache.put(videoKey, url)
                }

            } else {
                logger.info("获取萤石视频失败:{}", it)
            }
        }
        return videoUrl
    }
}