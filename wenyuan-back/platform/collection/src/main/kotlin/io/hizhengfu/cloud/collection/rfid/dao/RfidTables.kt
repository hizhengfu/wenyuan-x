package io.hizhengfu.cloud.collection.rfid.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortCheckConstraintName

import io.hizhengfu.cloud.autoconfigure.exposed.UIDTable
import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidAnomalyStatus
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidAnomalyType
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidAlarmStatus
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidDirection
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidOperatorCardStatus
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidPassageDecision
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidPassageGrantStatus
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidReadClassification
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidRealtimeEventType
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidScanSessionState
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidScanSessionType
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidTagJobStatus
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidTagOperationType
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidTagStatus
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidWhitelistSyncStatus
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidWorkstationDevicePurpose
import io.hizhengfu.cloud.collection.rfid.domain.EnumRfidWorkstationCapability
import org.jetbrains.exposed.v1.javatime.datetime
import java.time.LocalDateTime

/**
 * RFID 业务审计基表。每个数据库列都带注释，便于 MySQL、PostgreSQL/OpenGauss 和 DM 运维核查。
 */
open class CollectionRfidAuditUIDTable(name: String) : UIDTable(name) {
    val createTime = datetime("create_time").clientDefault { LocalDateTime.now() }.comment("创建时间")
    val createBy = varchar("create_by", 64).nullable().comment("创建用户 ID")
    val updateTime = datetime("update_time").nullable().comment("最后更新时间")
    val updateBy = varchar("update_by", 64).nullable().comment("最后更新用户 ID")
}

/** 可信 RFID 现场终端。浏览器令牌摘要和用户身份必须同时成立。 */
object RfidWorkstations : CollectionRfidAuditUIDTable("col_rfid_workstation") {
    val orgId = varchar("org_id", 64).index(shortIndexName("org_id")).comment("所属收藏单位 ID")
    val code = varchar("code", 64).comment("馆内唯一工作站编码")
    val name = varchar("name", 100).comment("工作站名称")
    val defaultCapability = enumerationByName<EnumRfidWorkstationCapability>("default_capability", 32).nullable()
        .comment("独立工作台打开后的默认业务能力；空表示由客户端选择")
    val locationId = varchar("location_id", 64).nullable().comment("安装位置 ID")
    val capabilitiesJson = text("capabilities_json").default("[]").comment("工作站业务能力编码 JSON")
    val antennaDirectionsJson = text("antenna_directions_json").default("{}")
        .comment("通道天线到 IN/OUT 方向的一次性映射 JSON；非通道工作站为空")
    val passageDebounceSeconds = integer("passage_debounce_seconds", checkConstraintName = shortCheckConstraintName("passage_debounce_seconds")).default(3).comment("通道同标签同方向去抖秒数")
    val alarmOutputIndex = integer("alarm_output_index", checkConstraintName = shortCheckConstraintName("alarm_output_index")).default(1).comment("异常联动的设备 GPO 输出编号")
    val alarmDurationMillis = long("alarm_duration_millis", checkConstraintName = shortCheckConstraintName("alarm_duration_millis", typeCode = "l")).default(3_000).comment("异常声光或继电器输出持续毫秒数")
    val enrollmentTokenHash = varchar("enrollment_token_hash", 64).comment("浏览器注册令牌 SHA-256 摘要")
    val browserCredentialHash = varchar("browser_credential_hash", 64).nullable()
        .comment("一次注册后签发给借还台浏览器的 HttpOnly 凭证 SHA-256 摘要")
    val activeLeaseInstanceHash = varchar("active_lease_instance_hash", 64).nullable()
        .comment("当前允许操作设备的浏览器窗口实例摘要")
    val activeLeaseUntil = datetime("active_lease_until").nullable()
        .comment("浏览器窗口前台操作租约到期时间")
    val enabled = bool("enabled").default(true).comment("是否允许工作站登录和发起作业")
    val lastLoginAt = datetime("last_login_at").nullable().comment("最近成功登录时间")

    init {
        uniqueIndex(shortIndexName("org_id_code", unique = true), orgId, code)
        index(shortIndexName("org_default_enabled"), false, orgId, defaultCapability, enabled)
    }
}

/** 工作站与场馆设备的用途绑定；设备可以多角色，同一工作站也可以配置多台候选设备。 */
object RfidWorkstationDeviceBindings : CollectionRfidAuditUIDTable("col_rfid_workstation_device") {
    val workstationId = varchar("workstation_id", 64).index(shortIndexName("workstation_id")).comment("工作站 ID")
    val deviceId = varchar("device_id", 64).index(shortIndexName("device_id")).comment("场馆设备 ing_device.id")
    val purpose = enumerationByName<EnumRfidWorkstationDevicePurpose>("purpose", 32).comment("外设用途")
    val isDefault = bool("is_default").default(false).comment("是否为该用途默认设备")
    val priority = integer("priority", checkConstraintName = shortCheckConstraintName("priority")).default(0).comment("同用途设备选择优先级，数值越小越优先")
    val enabled = bool("enabled").default(true).comment("是否参与设备路由")

    init {
        uniqueIndex(shortIndexName("station_purpose_device", unique = true), workstationId, purpose, deviceId)
        index(shortIndexName("station_purpose_enabled"), false, workstationId, purpose, enabled)
    }
}

/** RFID 工作站人员卡，仅保存规范卡号摘要，避免日志或数据库泄露完整卡号。 */
object RfidOperatorCards : CollectionRfidAuditUIDTable("col_rfid_operator_card") {
    val orgId = varchar("org_id", 64).index(shortIndexName("org_id")).comment("所属收藏单位 ID")
    val cardUidHash = varchar("card_uid_hash", 64).comment("规范卡号 SHA-256 摘要")
    val cardLast4 = varchar("card_last4", 4).comment("管理端识别用卡号末四位")
    val userId = varchar("user_id", 64).index(shortIndexName("user_id")).comment("关联 sys_user.id")
    val allowedCapabilities = varchar("allowed_capabilities", 500).comment("允许登录的工作站业务能力，逗号分隔")
    val validFrom = datetime("valid_from").comment("凭证生效时间")
    val validUntil = datetime("valid_until").nullable().comment("凭证失效时间；空表示长期有效")
    val cardStatus = enumerationByName<EnumRfidOperatorCardStatus>("card_status", 16).comment("人员卡状态")
    val issuedAt = datetime("issued_at").comment("发卡时间")
    val issuedBy = varchar("issued_by", 64).nullable().comment("发卡用户 ID")
    val revokedAt = datetime("revoked_at").nullable().comment("挂失或注销时间")
    val revokedBy = varchar("revoked_by", 64).nullable().comment("挂失或注销用户 ID")
    val revokedReason = varchar("revoked_reason", 500).nullable().comment("挂失或注销原因")

    init {
        uniqueIndex(shortIndexName("org_card_uid_hash", unique = true), orgId, cardUidHash)
        index(shortIndexName("org_user_card_status"), false, orgId, userId, cardStatus)
    }
}

/** RFID 标签全生命周期档案；Collections.rfidTag 在迁移期保留为当前 EPC 投影。 */
object RfidTags : CollectionRfidAuditUIDTable("col_rfid_tag") {
    val orgId = varchar("org_id", 64).index(shortIndexName("org_id")).comment("所属收藏单位 ID")
    val epc = varchar("epc", 128).comment("大写十六进制 EPC")
    val tid = varchar("tid", 128).nullable().comment("大写十六进制 TID，发标校验后写入")
    val collectionId = varchar("collection_id", 64).nullable().index(shortIndexName("collection_id"))
        .comment("当前绑定藏品 ID")
    val tagStatus = enumerationByName<EnumRfidTagStatus>("tag_status", 16).comment("标签生命周期状态")
    val issuedAt = datetime("issued_at").nullable().comment("发标任务创建时间")
    val issuedBy = varchar("issued_by", 64).nullable().comment("发标用户 ID")
    val activatedAt = datetime("activated_at").nullable().comment("写后读回一致并激活的时间")
    val replacedById = varchar("replaced_by_id", 64).nullable().comment("替换本标签的新标签 ID")
    val retiredAt = datetime("retired_at").nullable().comment("解绑或报废时间")
    val retiredBy = varchar("retired_by", 64).nullable().comment("解绑或报废用户 ID")
    val retiredReason = varchar("retired_reason", 500).nullable().comment("解绑、换标或报废原因")
    val version = integer("version", checkConstraintName = shortCheckConstraintName("version")).default(0).comment("乐观锁版本")

    init {
        uniqueIndex(shortIndexName("org_epc", unique = true), orgId, epc)
        uniqueIndex(shortIndexName("org_tid", unique = true), orgId, tid)
        index(shortIndexName("org_tag_status"), false, orgId, tagStatus)
    }
}

/** 标签硬件和业务操作时间线。 */
object RfidTagOperations : UIDTable("col_rfid_tag_operation") {
    val orgId = varchar("org_id", 64).index(shortIndexName("org_id")).comment("所属收藏单位 ID")
    val tagId = varchar("tag_id", 64).index(shortIndexName("tag_id")).comment("关联 col_rfid_tag.id")
    val operationType = enumerationByName<EnumRfidTagOperationType>("operation_type", 32).comment("标签操作类型")
    val workstationId = varchar("workstation_id", 64).nullable().comment("执行操作的工作站 ID")
    val operatorId = varchar("operator_id", 64).nullable().comment("执行用户 ID")
    val commandId = varchar("command_id", 64).nullable().comment("关联 ing_device_command.id")
    val beforeEpc = varchar("before_epc", 128).nullable().comment("操作前 EPC")
    val afterEpc = varchar("after_epc", 128).nullable().comment("操作后或读回 EPC")
    val tid = varchar("tid", 128).nullable().comment("操作时读取的 TID")
    val success = bool("success").comment("操作是否成功")
    val resultCode = varchar("result_code", 64).nullable().comment("稳定结果编码")
    val detail = varchar("detail", 500).nullable().comment("脱敏后的结果说明")
    val occurredAt = datetime("occurred_at").comment("操作发生时间")

    init {
        index(shortIndexName("tag_occurred_at"), false, tagId, occurredAt)
        index(shortIndexName("command_id"), false, commandId)
    }
}

/** 发标/换标作业，写入、读回和激活分阶段推进。 */
object RfidTagJobs : CollectionRfidAuditUIDTable("col_rfid_tag_job") {
    val orgId = varchar("org_id", 64).index(shortIndexName("org_id")).comment("所属收藏单位 ID")
    val collectionId = varchar("collection_id", 64).index(shortIndexName("collection_id")).comment("目标藏品 ID")
    val workstationId = varchar("workstation_id", 64).index(shortIndexName("workstation_id")).comment("发标工作站 ID")
    val tagId = varchar("tag_id", 64).index(shortIndexName("tag_id")).comment("待激活标签 ID")
    val targetEpc = varchar("target_epc", 128).comment("服务端生成或审核后的目标 EPC")
    val expectedTid = varchar("expected_tid", 128).nullable().comment("写入前锁定的目标 TID")
    val verifiedEpc = varchar("verified_epc", 128).nullable().comment("写入后读回 EPC")
    val verifiedTid = varchar("verified_tid", 128).nullable().comment("写入后读回 TID")
    val commandId = varchar("command_id", 64).nullable().comment("关联写入设备命令 ID")
    val jobStatus = enumerationByName<EnumRfidTagJobStatus>("job_status", 16).comment("发标任务状态")
    val clientRequestId = varchar("client_request_id", 100).nullable().comment("调用方幂等键")
    val errorMessage = varchar("error_message", 500).nullable().comment("失败摘要")
    val version = integer("version", checkConstraintName = shortCheckConstraintName("version")).default(0).comment("状态机乐观锁版本")

    init {
        uniqueIndex(shortIndexName("org_client_request", unique = true), orgId, clientRequestId)
        index(shortIndexName("org_job_status"), false, orgId, jobStatus)
    }
}

/** 一次完整读卡作业；开始、停止、收敛、预览和最终确认均在服务端推进。 */
object RfidScanSessions : CollectionRfidAuditUIDTable("col_rfid_scan_session") {
    val orgId = varchar("org_id", 64).index(shortIndexName("org_id")).comment("所属收藏单位 ID")
    val sessionNo = varchar("session_no", 64).uniqueIndex(shortIndexName("session_no", unique = true))
        .comment("全局唯一扫描会话号")
    val workstationId = varchar("workstation_id", 64).nullable().index(shortIndexName("workstation_id")).comment("固定工作站 ID；手持任务为空")
    val deviceId = varchar("device_id", 64).index(shortIndexName("device_id")).comment("执行读取的 ing_device.id")
    val sessionType = enumerationByName<EnumRfidScanSessionType>("session_type", 32).comment("扫描业务类型")
    val businessType = varchar("business_type", 64).nullable().comment("关联业务类型")
    val businessId = varchar("business_id", 64).nullable().comment("关联业务单据 ID")
    val operatorId = varchar("operator_id", 64).comment("发起会话的用户 ID")
    val directInput = bool("direct_input").default(false).comment("是否由手持机 SDK 直接控制射频并上报读取结果")
    val sessionState = enumerationByName<EnumRfidScanSessionState>("session_state", 16).comment("扫描会话状态")
    val startCommandId = varchar("start_command_id", 64).nullable().comment("开始盘点命令 ID")
    val stopCommandId = varchar("stop_command_id", 64).nullable().comment("停止盘点命令 ID")
    val startedAt = datetime("started_at").nullable().comment("进入读取状态时间")
    val stopRequestedAt = datetime("stop_requested_at").nullable().comment("请求停止时间")
    val stoppedAt = datetime("stopped_at").nullable().comment("设备确认停止时间")
    val settledAt = datetime("settled_at").nullable().comment("最后事件收敛完成时间")
    val finalizedAt = datetime("finalized_at").nullable().comment("业务最终确认时间")
    val lastEventSequence = long("last_event_sequence", checkConstraintName = shortCheckConstraintName("last_event_sequence", typeCode = "l")).default(0).comment("会话已处理的最后设备事件序号")
    val expectedCount = integer("expected_count", checkConstraintName = shortCheckConstraintName("expected_count")).default(0).comment("冻结的应读藏品数量")
    val readCount = integer("read_count", checkConstraintName = shortCheckConstraintName("read_count")).default(0).comment("会话去重后的 EPC 数量")
    val clientRequestId = varchar("client_request_id", 100).nullable().comment("调用方幂等键")
    val finalResultJson = text("final_result_json").nullable().comment("最终确认结果摘要 JSON")
    val errorMessage = varchar("error_message", 500).nullable().comment("失败摘要")
    val version = integer("version", checkConstraintName = shortCheckConstraintName("version")).default(0).comment("状态机乐观锁版本")

    init {
        uniqueIndex(shortIndexName("org_client_request", unique = true), orgId, clientRequestId)
        index(shortIndexName("workstation_state"), false, workstationId, sessionState)
        index(shortIndexName("business_type_id"), false, businessType, businessId)
    }
}

/** 会话创建时冻结的应读藏品清单。 */
object RfidScanExpectedItems : UIDTable("col_rfid_scan_expected") {
    val sessionId = varchar("session_id", 64).index(shortIndexName("session_id")).comment("关联扫描会话 ID")
    val collectionId = varchar("collection_id", 64).comment("应读藏品 ID")
    val expectedEpc = varchar("expected_epc", 128).nullable().comment("创建会话时的有效 EPC 快照")
    val expectedTid = varchar("expected_tid", 128).nullable().comment("创建会话时的有效 TID 快照，用于识别 EPC 克隆冲突")
    val collectionNo = varchar("collection_no", 100).nullable().comment("总登记号快照")
    val collectionName = varchar("collection_name", 255).nullable().comment("藏品名称快照")

    init {
        uniqueIndex(shortIndexName("session_collection", unique = true), sessionId, collectionId)
        index(shortIndexName("collection_session"), false, collectionId, sessionId)
        index(shortIndexName("session_expected_epc"), false, sessionId, expectedEpc)
    }
}

/** 会话内按 EPC 去重的读取投影，不逐次保存每个射频重复包。 */
object RfidScanReads : UIDTable("col_rfid_scan_read") {
    val sessionId = varchar("session_id", 64).index(shortIndexName("session_id")).comment("关联扫描会话 ID")
    val epc = varchar("epc", 128).comment("大写十六进制 EPC")
    val tid = varchar("tid", 128).nullable().comment("最近一次读取的 TID")
    val antenna = integer("antenna", checkConstraintName = shortCheckConstraintName("antenna")).nullable().comment("最近一次读取天线编号")
    val direction = enumerationByName<EnumRfidDirection>("direction", 16).comment("设备物理方向")
    val firstSeenAt = datetime("first_seen_at").comment("首次读取时间")
    val lastSeenAt = datetime("last_seen_at").comment("最近读取时间")
    val readCount = long("read_count", checkConstraintName = shortCheckConstraintName("read_count", typeCode = "l")).default(1).comment("会话内重复读取次数")
    val peakRssi = integer("peak_rssi", checkConstraintName = shortCheckConstraintName("peak_rssi")).nullable().comment("会话内最高 RSSI")
    val lastRssi = integer("last_rssi", checkConstraintName = shortCheckConstraintName("last_rssi")).nullable().comment("最近一次 RSSI")
    val sourceEventId = varchar("source_event_id", 100).nullable().comment("最近来源采集事实 ID")
    val resolvedCollectionId = varchar("resolved_collection_id", 64).nullable().comment("解析到的藏品 ID")
    val classification = enumerationByName<EnumRfidReadClassification>("classification", 16)
        .comment("相对冻结清单的分类")

    init {
        uniqueIndex(shortIndexName("session_epc", unique = true), sessionId, epc)
        index(shortIndexName("session_classification"), false, sessionId, classification)
        index(shortIndexName("source_event_id"), false, sourceEventId)
    }
}

/** 手持机或工作站本机服务的读取批次幂等凭据；批次行与读数在同一事务提交。 */
object RfidHandheldReadBatches : UIDTable("col_rfid_handheld_batch") {
    val sessionId = varchar("session_id", 64).index(shortIndexName("session_id"))
    val deviceId = varchar("device_id", 64).index(shortIndexName("device_id"))
    val localSessionId = varchar("local_session_id", 64).comment("本地读取流 UUID")
    val batchSequence = long("batch_sequence", checkConstraintName = shortCheckConstraintName("batch_sequence", typeCode = "l")).comment("本地单调批次序号")
    val readCount = integer("read_count", checkConstraintName = shortCheckConstraintName("read_count")).comment("本批读取条数")
    val receivedAt = datetime("received_at").comment("服务端接收时间")

    init {
        uniqueIndex(shortIndexName("device_local_session_batch", unique = true), deviceId, localSessionId, batchSequence)
    }
}

/** 由已审批业务生成的动态通行授权。 */
object RfidPassageGrants : CollectionRfidAuditUIDTable("col_rfid_passage_grant") {
    val orgId = varchar("org_id", 64).index(shortIndexName("org_id")).comment("所属收藏单位 ID")
    val collectionId = varchar("collection_id", 64).index(shortIndexName("collection_id")).comment("授权藏品 ID")
    val epc = varchar("epc", 128).comment("授权生成时的 EPC 快照")
    val sourceType = varchar("source_type", 64).comment("授权来源业务类型")
    val sourceId = varchar("source_id", 64).comment("授权来源业务 ID")
    val gateId = varchar("gate_id", 64).index(shortIndexName("gate_id")).comment("允许通行的门口 RFID 设备 ID")
    val direction = enumerationByName<EnumRfidDirection>("direction", 16).comment("允许方向")
    val validFrom = datetime("valid_from").comment("授权生效时间")
    val validUntil = datetime("valid_until").comment("授权失效时间")
    val grantStatus = enumerationByName<EnumRfidPassageGrantStatus>("grant_status", 16).comment("授权状态")
    val approvedBy = varchar("approved_by", 64).nullable().comment("业务审批或执行用户 ID")
    val revokedAt = datetime("revoked_at").nullable().comment("授权撤销时间")
    val revokedBy = varchar("revoked_by", 64).nullable().comment("撤销用户 ID")
    val revokeReason = varchar("revoke_reason", 500).nullable().comment("授权撤销原因")
    val whitelistSyncStatus = enumerationByName<EnumRfidWhitelistSyncStatus>("whitelist_sync_status", 16)
        .comment("设备动态白名单同步状态")
    val whitelistCommandId = varchar("whitelist_command_id", 64).nullable().comment("白名单同步命令 ID")

    init {
        uniqueIndex(shortIndexName("source_collection_gate_direction", unique = true), sourceType, sourceId, collectionId, gateId, direction)
        index(shortIndexName("gate_status_valid"), false, gateId, grantStatus, validFrom, validUntil)
        index(shortIndexName("source_type_id"), false, sourceType, sourceId)
    }
}

/** 门口设备上报并经业务授权判定后的不可变通行事实。 */
object RfidPassageEvents : UIDTable("col_rfid_passage_event") {
    val orgId = varchar("org_id", 64).index(shortIndexName("org_id")).comment("所属收藏单位 ID")
    val deviceId = varchar("device_id", 64).index(shortIndexName("device_id")).comment("上报设备 ID")
    val gateId = varchar("gate_id", 64).index(shortIndexName("gate_id")).comment("上报门禁设备 ID；旧记录保留历史逻辑通道 ID")
    val antenna = integer("antenna", checkConstraintName = shortCheckConstraintName("antenna")).nullable().comment("上报天线编号")
    val epc = varchar("epc", 128).comment("大写十六进制 EPC")
    val tid = varchar("tid", 128).nullable().comment("大写十六进制 TID")
    val direction = enumerationByName<EnumRfidDirection>("direction", 16).comment("物理通行方向")
    val occurredAt = datetime("occurred_at").comment("设备事件发生时间")
    val receivedAt = datetime("received_at").comment("服务端接收时间")
    val collectionId = varchar("collection_id", 64).nullable().comment("事件发生时解析到的藏品 ID")
    val grantId = varchar("grant_id", 64).nullable().comment("命中的通行授权 ID")
    val decision = enumerationByName<EnumRfidPassageDecision>("decision", 16).comment("授权判定结果")
    val sourceEventId = varchar("source_event_id", 100).uniqueIndex(shortIndexName("source_event_id", unique = true))
        .comment("采集事实幂等 ID")
    val anomalyId = varchar("anomaly_id", 64).nullable().comment("关联业务异常 ID")
    val alarmCommandId = varchar("alarm_command_id", 64).nullable().index(shortIndexName("alarm_command_id"))
        .comment("关联声光或继电器输出命令 ID")
    val alarmStatus = enumerationByName<EnumRfidAlarmStatus>("alarm_status", 16)
        .default(EnumRfidAlarmStatus.NOT_REQUIRED).comment("异常报警命令执行状态")
    val alarmMessage = varchar("alarm_message", 500).nullable().comment("报警执行失败或回执摘要")
    val contextJson = text("context_json").default("{}").comment("判定时藏品状态和授权上下文 JSON")

    init {
        index(shortIndexName("gate_occurred_at"), false, gateId, occurredAt)
        index(shortIndexName("collection_occurred_at"), false, collectionId, occurredAt)
    }
}

/** RFID 业务风险事件；通用设备离线告警仍由 museum-device 负责。 */
object RfidAnomalies : CollectionRfidAuditUIDTable("col_rfid_anomaly") {
    val orgId = varchar("org_id", 64).index(shortIndexName("org_id")).comment("所属收藏单位 ID")
    val eventId = varchar("event_id", 64).nullable().index(shortIndexName("event_id")).comment("关联通行事件 ID")
    val collectionId = varchar("collection_id", 64).nullable().comment("关联藏品 ID")
    val epc = varchar("epc", 128).nullable().comment("异常相关 EPC")
    val anomalyType = enumerationByName<EnumRfidAnomalyType>("anomaly_type", 32).comment("业务异常类型")
    val anomalyStatus = enumerationByName<EnumRfidAnomalyStatus>("anomaly_status", 16).comment("异常处置状态")
    val title = varchar("title", 200).comment("异常标题")
    val detail = varchar("detail", 1000).nullable().comment("异常事实摘要")
    val openedAt = datetime("opened_at").comment("异常开启时间")
    val acknowledgedAt = datetime("acknowledged_at").nullable().comment("首次确认时间")
    val resolvedAt = datetime("resolved_at").nullable().comment("解决或误报关闭时间")
    val handlerId = varchar("handler_id", 64).nullable().comment("当前处理用户 ID")
    val resolutionNote = varchar("resolution_note", 1000).nullable().comment("处理说明")

    init {
        index(shortIndexName("org_status_opened"), false, orgId, anomalyStatus, openedAt)
        index(shortIndexName("collection_status"), false, collectionId, anomalyStatus)
    }
}

/** 异常状态时间线，保留确认、调查、升级和关闭的全部人工证据。 */
object RfidAnomalyTransitions : UIDTable("col_rfid_anomaly_transition") {
    val anomalyId = varchar("anomaly_id", 64).index(shortIndexName("anomaly_id")).comment("关联 RFID 异常 ID")
    val fromStatus = enumerationByName<EnumRfidAnomalyStatus>("from_status", 16).nullable().comment("变更前状态")
    val toStatus = enumerationByName<EnumRfidAnomalyStatus>("to_status", 16).comment("变更后状态")
    val operatorId = varchar("operator_id", 64).comment("处理用户 ID")
    val note = varchar("note", 1000).nullable().comment("处理说明")
    val occurredAt = datetime("occurred_at").comment("状态变化时间")

    init {
        index(shortIndexName("anomaly_occurred_at"), false, anomalyId, occurredAt)
    }
}

/** 可重放的工作站实时事件；SSE 断线后按事件 ID/序号恢复。 */
object RfidRealtimeEvents : UIDTable("col_rfid_realtime_event") {
    val orgId = varchar("org_id", 64).index(shortIndexName("org_id")).comment("所属收藏单位 ID")
    val workstationId = varchar("workstation_id", 64).nullable().index(shortIndexName("workstation_id"))
        .comment("目标工作站 ID；空表示馆级事件")
    val sessionId = varchar("session_id", 64).nullable().index(shortIndexName("session_id")).comment("关联扫描会话 ID")
    val eventType = enumerationByName<EnumRfidRealtimeEventType>("event_type", 32).comment("实时事件类型")
    val sequence = long("event_sequence", checkConstraintName = shortCheckConstraintName("event_sequence", typeCode = "l")).comment("工作站或会话内单调递增序号")
    val occurredAt = datetime("occurred_at").comment("事件发生时间")
    val payloadJson = text("payload_json").default("{}").comment("前端所需的最小化事件 JSON")
    val expiresAt = datetime("expires_at").comment("可重放窗口截止时间")

    init {
        uniqueIndex(shortIndexName("workstation_sequence", unique = true), workstationId, sequence)
        index(shortIndexName("expires_at"), false, expiresAt)
    }
}
