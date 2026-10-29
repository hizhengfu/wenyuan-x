package io.hizhengfu.cloud.museum.domain

/**
 * 点位信息
 * */
data class Coordinate(
        val x: Double?,
        val y: Double?,
        val z: Double? = null,
) {

    fun isEmpty(): Boolean {
        return x == null || y == null
    }
}