package io.hizhengfu.cloud.museum.domain


data class TouristSpotToken(
        val id: Int, // 1
        val token: String, // c4cf1d5102c6528507c6e6bf51f4f119972e08ec
        val username: String // test
)

data class TouristSpotWrap(
        val count: Int, // 50
        val results: List<TouristSpotData>,
        val next: String? = null
)

data class TouristSpotData(
        val alias: String,
        val commentary: String, // 123
        val created: String, // 2022-05-30T11:37:05.167317
        val final_hint: String, // 123
        val id: Int, // 60
        val in_the_way: String,
        val is_active: Boolean, // true
        val mp3: String,
        val mp4: String, // /storage/emulated/0/qgrobot/home.mp4
        val name: String, // 222
        val `operator`: String, // test
        val owner: Int, // 3
        val pre_hint: String, // 123
        val selected: TouristSpotSelected,
        val updated: String, // 2022-05-30T11:37:05.167347
        val url: String, // http://47.100.46.95:25586/tourist_spots/60/
        val wait: String
)

data class TouristSpotSelected(
        val id: Int, // 212
        val spot: Int, // 59
        val src: String, // http://47.100.46.95:25586/media/spot/20220530/img/123_6.jpg
        val x_position: Double, // 1.095
        val y_position: Double, // -0.003
        val z_position: Double // -4.728
)