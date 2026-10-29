package io.hizhengfu.cloud.collection.common.event

/**
 * 预防性保护 → 修复：环境异常/巡查/病害触发派单事件。
 * 由 [io.hizhengfu.cloud.collection.preservation] 发布，修复侧监听后为每个受影响藏品建修复草稿。
 * 事件字段全用字符串常量避免跨模块依赖枚举类型。
 */
data class PreservationRestorationDispatchEvent(
    /** 预警事件 id，回写锚点；修复侧建单后写回 alarm.restoration_project_id */
    val alarmEventId: String,
    val orgId: String,
    /** 受影响藏品 id 列表；修复侧为每个 id 建独立草稿项目 */
    val affectedCollectionIds: List<String>,
    /** RED / ORANGE / YELLOW / BLUE / NORMAL —— 对齐 EnumAlarmLevel 名字，避免类型耦合 */
    val alarmLevel: String,
    /** EMERGENCY 抢救性 / NORMAL 一般 —— 对齐 restoration.EnumRestorationUrgency */
    val urgency: String,
    /** PRESERVATION_AUTO / PRESERVATION_MANUAL —— 对齐 restoration.EnumRestorationTriggerSource */
    val triggerSource: String,
    /** 12 项环境值快照（JSON），近 30 天曲线可选嵌入；供修复方案参考 */
    val envSnapshot: String? = null,
    /** 禁用材料清单（如 VOC/含硫材料）；从材质敏感度反推 */
    val materialConstraints: String? = null,
    /** 派单说明，写入 col_restoration.plan 前置备注 */
    val dispatchReason: String? = null,
    /** 同步同事务建单回执；必须覆盖全部受影响藏品，不能只回填首个项目。 */
    val createdProjectIds: MutableMap<String, String> = linkedMapOf(),
)

/**
 * 修复 → 预防性保护：草稿项目已从预警派单事件建立，回填预警的 restoration_project_id。
 * 由 [io.hizhengfu.cloud.collection.service.RestorationDispatchListener] 建单后发布；
 * 预防性保护侧 [io.hizhengfu.cloud.collection.preservation.service.PreservationAlarmService] 监听后
 * 若预警未绑定则写入 [restorationId]。
 */
data class RestorationCreatedFromAlarmEvent(
    val alarmEventId: String,
    val restorationId: String,
)

/**
 * 修复 → 预防性保护：修复完成回写事件。
 * 由 [io.hizhengfu.cloud.collection.service.RestorationService.complete] 发布，
 * 预防性保护侧仅提示重新核查；实际竣工不能代替告警安全复核。
 */
data class RestorationCompletedEvent(
    val restorationId: String,
    val orgId: String,
    val collectionId: String,
    /** 派单来源预警 id；非空则提示告警处置重新核查 */
    val preservationAlarmId: String? = null,
    /** 回库目标微环境值（JSON） */
    val targetEnvironment: String? = null,
    /** 病害控制情况说明，追加到 alarm.handleReason */
    val diseaseControlNote: String? = null,
)
