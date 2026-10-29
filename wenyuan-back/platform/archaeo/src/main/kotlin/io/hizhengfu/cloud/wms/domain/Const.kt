package io.hizhengfu.cloud.wms.domain


/**
 * 库房安防等级
 * */
enum class EnumStorageSecurityLevel(val description: String) {
    LEVEL1("一级"),
    LEVEL2("二级"),
    LEVEL3("三级"),
}

/**
 * 库房可用状态
 * */
enum class EnumStorageStatus(val description: String) {
    USABLE("可用"),
    SERVICE("维护中"),
    FULL("已满"),
}

/**
 * 操作流程状态
 * */
enum class EnumOperationFlowStatus(val description: String) {
    DOING("进行中"),
    COMPLETE("已完成"),
}

/**
 * 文物状态
 * */
enum class EnumArtifactStatus(val description: String) {
    REGISTER("登记"),
    INSIDE("在库"),
    OUTSIDE("出库"),
    LOSE("遗失"),
}

/**
 * 文物操作类型
 * */
enum class EnumStorageOperationType(val description: String) {
    INBOUND("入库"),
    OUTBOUND("出库"),
    TRANSFER("移库"),
    RETURN("归库"),
}

/**
 * 文物出土状态
 * */
enum class EnumArtifactPreserveStatus(val description: String) {
    INTACT("完整"),
    DAMAGED("残"),
    COMPLETE("基本完整"),
}

/**
 * 文物保存状态
 * */
enum class EnumArtifactSaveStatus(val description: String) {
    INTACT("完整"),
    DAMAGED("残"),
    COMPLETE("基本完整"),
    REPAIR_COMPLETELY("修复完整"),
}


/**
 * 文物材质类型
 * */
enum class EnumArtifactMaterialType(val description: String) {
    SHIQI("石器"),
    YUQI("玉器"),
    TAOQI("陶器"),
    GUQI("骨器"),
    MUQI("木器"),
    TONGQI("铜器"),
    TIEQI("铁器"),
    JINYINQI("金银器"),
    QIAN("铅"),
    XINQI("锌器"),
    CIQI("瓷器"),
    XIQI("漆器"),
    BOLIQI("玻璃器"),
    FANGZHIPIN("纺织品"),
    ZHILEI("纸类文物"),
}


/**
 * 遗迹代号
 * */
enum class EnumArtifactCellType(val description: String) {
    H("灰坑或窖穴"),
    G("壕沟"),
    F("房址"),
    Y("窑址"),
    J("井址"),
    Z("灶址"),
    D("柱洞"),
    L("道路"),
    M("墓葬"),
    W("瓮棺"),
    Q("墙址"),
    S("烧土"),
}


/**
 * 数量单位类型
 * */
enum class EnumArtifactUnitType(val description: String) {
    PIECE("件"),
    SET("套"),
    BATCH("批"),
}


/**
 * 出库用途类型
 * */
enum class EnumArtifactOutboundType(val description: String) {
    T1("修复"),
    T2("绘图"),
    T3("拓片"),
    T4("拍照"),
    T5("检测"),
    T6("借展"),
    T7("移交"),
    T8("其他"),
}

/**
 * 出库用途类型
 * */
enum class EnumArtifactTransferType(val description: String) {
    T1("整理"),
    T2("修复"),
    T3("布展"),
    T4("安全转移"),
}



