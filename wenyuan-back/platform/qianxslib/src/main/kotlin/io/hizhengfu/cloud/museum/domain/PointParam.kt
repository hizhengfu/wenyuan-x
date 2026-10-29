package io.hizhengfu.cloud.museum.domain

import EnumDeviceType
import jakarta.validation.constraints.NotBlank

/**
 * 新建坐标点
 * */
data class AddPointPosition(
        @field:NotBlank
        val layerId: String,
        @field:NotBlank
        val deviceId: String,
        val scene: String?,
        val x: Double,
        val y: Double,
        val z: Double? = null)

/**
 * 更新坐标点
 * */
data class UpdatePointPosition(
        @field:NotBlank
        val id: String,
        val x: Double,
        val y: Double,
        val z: Double? = null)


data class Point(
        val id: String?,
        val name: String? = null,
        val deviceId: String? = null,
        val addr: String? = null,
        val serialNo: String? = null,
        val scene: String? = null,
        val type: EnumDeviceType? = null,
        val x: Double?,
        val y: Double?,
        val z: Double?,
)

data class PositionPoint(
        val x: Double?,
        val y: Double?,
)