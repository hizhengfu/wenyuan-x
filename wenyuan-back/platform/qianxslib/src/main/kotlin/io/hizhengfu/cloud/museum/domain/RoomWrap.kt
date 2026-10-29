package io.hizhengfu.cloud.museum.domain


/**
 * 输出机器人说
 * */
data class RoomStatus(
        val people: List<PeoplePosition>?,
        val robot: List<RobotWrap>?,
        val raster: List<RasterWrap>?
)