/**
 * 设备类型
 * */
enum class EnumDeviceType(val description: String) {
    CAMERA("相机"), RASTER("光栅"), PEOPLE("人流"),
}

/**
 * 任务类型
 * */
enum class EnumTaskProcessType(val description: String) {
    AUTO("自动"), MANUAL("手动")
}

enum class EnumTaskType(val description: String) {
    EVENT("异常任务"), USUAL("日常任务")
}

/**
 * 任务类型
 * */
enum class EnumTaskDetailType(val description: String) {
    FORCE("强制"), POSITION("位置"), SPEECH("语音播报"), GUIDE("导览")
}

/**
 * 图层类型
 * */
enum class EnumLayerType(val description: String) {
    PLANE("平面"), PANORAMA("全景"), POSITION("全景拍摄位置")
}


/**
 * 任务类型
 * */
enum class EnumCommandType(val description: String) {
    DATA("数据"), ALARM("预警")
}

/**
 * 规则类型
 * */
enum class EnumAlarmRuleType(val description: String) {
    PEOPLE("人流")
}

/**
 * 预警处置状态
 * */
enum class EnumAlarmStatus(val description: String) {
    WAIT("待处理"),
    AUTO("自动处置"),
    TIMEOUT("超时处置"),
    MANUAL("人工处置"),
    FINISH("处置完成"),
}

/**
 * 机器人订阅地址
 * */
const val TOPIC_ROBOT = "/topic/robot"

/**
 * 超时自动关闭事件 单位分钟
 * */
const val CONFIG_EVENT_TIMEOUT = "CONFIG_EVENT_TIMEOUT"