package io.hizhengfu.cloud.museum.domain


data class YsWrap<T>(
        val code: String, // 200
        val data: T?,
        val msg: String // 操作成功!
)

data class TokenData(
        val accessToken: String, // at.7jrcjmna8qnqg8d3dgnzs87m4v2dme3l-32enpqgusd-1jvdfe4-uxo15ik0s
        val expireTime: Long // 1470810222045
)


data class Addressv2Data(
        val expireTime: String?, // 2022-10-10 00:01:55
        val id: String, // 499008575453007872
        val url: String // https://rtmp01open.ys7.com:9188/v3/openlive/G78393042_1_2.flv?expire=1665331315&id=499008575453007872&t=a1b245a51273db6275498ce67ecfc6c2e0f855adcc197023496550ef7f0da52a&ev=100
)