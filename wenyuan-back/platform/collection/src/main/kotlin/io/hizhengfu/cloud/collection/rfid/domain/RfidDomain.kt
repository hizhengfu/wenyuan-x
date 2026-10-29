package io.hizhengfu.cloud.collection.rfid.domain

/** 工作站可以同时具备多项业务能力。 */
enum class EnumRfidWorkstationCapability(val description: String) {
    BORROW_RETURN("借还核验"),
    INVENTORY("藏品盘点"),
    TAG_ISSUE("标签发放"),
    TAG_PRINT("标签打印"),
    DOCUMENT_PRINT("单据打印"),
    PASSAGE_MONITOR("通道监控"),
}

/** 工作站外设在业务中的用途。同一物理设备可以通过多条绑定承担多个用途。 */
enum class EnumRfidWorkstationDevicePurpose(val description: String) {
    RFID_READ("RFID 读取"),
    RFID_WRITE("RFID 写入"),
    CARD_READ("人员卡读取"),
    TAG_PRINT("标签打印"),
    DOCUMENT_PRINT("单据打印"),
    PASSAGE_READ("通道读取"),
}

enum class EnumRfidOperatorCardStatus(val description: String) {
    ACTIVE("有效"),
    LOST("挂失"),
    REVOKED("已注销"),
    EXPIRED("已过期"),
}

enum class EnumRfidTagStatus(val description: String) {
    PROVISIONING("待写入"),
    ACTIVE("使用中"),
    UNBOUND("已解绑"),
    REPLACED("已换标"),
    RETIRED("已报废"),
    ERROR("写入或校验失败"),
}

enum class EnumRfidTagOperationType(val description: String) {
    MIGRATED("历史标签迁移"),
    CREATED("创建发标任务"),
    WRITE_REQUESTED("请求写入"),
    WRITE_SUCCEEDED("写入成功"),
    WRITE_FAILED("写入失败"),
    VERIFIED("读回校验"),
    ACTIVATED("激活绑定"),
    UNBOUND("解绑"),
    REPLACED("换标"),
    RETIRED("报废"),
}

enum class EnumRfidTagJobStatus(val description: String) {
    CREATED("已创建"),
    WRITE_PENDING("等待写入"),
    WRITING("写入中"),
    VERIFY_PENDING("等待校验"),
    VERIFIED("校验通过"),
    ACTIVATED("已激活"),
    FAILED("失败"),
    CANCELLED("已取消"),
}

enum class EnumRfidScanSessionType(val description: String) {
    LOAN_OUT("借展出库"),
    REQUISITION_OUT("提用出库"),
    RETURN_IN("归还入库"),
    INVENTORY("藏品盘点"),
    TAG_VERIFY("标签校验"),
    PASSAGE_DIAG("通道诊断"),
}

enum class EnumRfidScanSessionState(val description: String) {
    CREATED("已创建"),
    STARTING("设备启动中"),
    READING("读取中"),
    STOPPING("设备停止中"),
    PREVIEW_READY("可预览确认"),
    FINALIZING("业务确认中"),
    FINALIZED("已确认"),
    CANCELLED("已取消"),
    EXPIRED("已过期"),
    FAILED("失败"),
}

enum class EnumRfidDirection(val description: String) {
    IN("进入"),
    OUT("离开"),
    UNKNOWN("未知"),
}

enum class EnumRfidReadClassification(val description: String) {
    EXPECTED("符合预期"),
    MISSING("应读未读"),
    EXTRA("范围外藏品"),
    UNKNOWN("未知标签"),
    CONFLICT("标签冲突"),
}

enum class EnumRfidPassageGrantStatus(val description: String) {
    PENDING("待生效"),
    ACTIVE("有效"),
    USED("已使用"),
    EXPIRED("已过期"),
    REVOKED("已撤销"),
}

enum class EnumRfidWhitelistSyncStatus(val description: String) {
    NOT_REQUIRED("不需要下发"),
    PENDING("等待同步"),
    SYNCED("已同步"),
    FAILED("同步失败"),
}

enum class EnumRfidPassageDecision(val description: String) {
    AUTHORIZED("已授权"),
    UNAUTHORIZED("未授权"),
    UNKNOWN("未知标签"),
    LATE("离线迟到事件"),
}

enum class EnumRfidAlarmStatus(val description: String) {
    NOT_REQUIRED("无需报警"),
    QUEUED("等待执行"),
    SUCCEEDED("执行成功"),
    FAILED("执行失败"),
}

enum class EnumRfidAnomalyType(val description: String) {
    UNAUTHORIZED_EXIT("未授权带出"),
    UNAUTHORIZED_PASSAGE("未授权通行"),
    EXPIRED_GRANT("授权已过期"),
    WRONG_DIRECTION("方向不符"),
    UNKNOWN_TAG("未知标签"),
    TAG_CONFLICT("标签冲突"),
    LATE_OFFLINE_EVENT("设备离线迟到事件"),
    DUPLICATE_PASSAGE("疑似重复通行"),
    STATE_MISMATCH("藏品状态不一致"),
    DEVICE_OFFLINE_RISK("设备离线风险"),
}

enum class EnumRfidAnomalyStatus(val description: String) {
    OPEN("待处理"),
    ACKNOWLEDGED("已确认"),
    INVESTIGATING("处理中"),
    RESOLVED("已解决"),
    FALSE_POSITIVE("误报"),
    ESCALATED("已升级"),
}

enum class EnumRfidRealtimeEventType {
    DEVICE_STATUS,
    OPERATOR_CARD_READ,
    AUTH_STATE,
    SESSION_STATE,
    TAG_BATCH,
    COMMAND_RESULT,
    GATE_EVENT,
    BUSINESS_ANOMALY,
    RESYNC_REQUIRED,
}

data class RfidWorkstationSaveVo(
    val id: String? = null,
    val name: String? = null,
    val defaultCapability: EnumRfidWorkstationCapability? = null,
    val locationId: String? = null,
    val capabilities: Set<EnumRfidWorkstationCapability> = emptySet(),
    val deviceBindings: List<RfidWorkstationDeviceBindingSaveVo> = emptyList(),
    val antennaDirections: Map<Int, EnumRfidDirection> = emptyMap(),
    val passageDebounceSeconds: Int = 3,
    val alarmOutputIndex: Int = 1,
    val alarmDurationMillis: Long = 3_000,
    val enabled: Boolean? = null,
)

data class RfidWorkstationDeviceBindingSaveVo(
    val deviceId: String? = null,
    val purpose: EnumRfidWorkstationDevicePurpose? = null,
    val isDefault: Boolean = false,
    val priority: Int = 0,
    val enabled: Boolean = true,
)

data class RfidWorkstationDeviceBindingVo(
    val id: String,
    val deviceId: String,
    val purpose: EnumRfidWorkstationDevicePurpose,
    val isDefault: Boolean,
    val priority: Int,
    val enabled: Boolean,
)

data class RfidWorkstationCreatedVo(
    val id: String,
    val code: String,
    /** 只在新建或管理员重置时返回一次，数据库只保存 SHA-256 摘要。 */
    val enrollmentToken: String?,
)

data class RfidWorkstationVo(
    val id: String,
    val orgId: String,
    val code: String,
    val name: String,
    val defaultCapability: EnumRfidWorkstationCapability?,
    val locationId: String?,
    val capabilities: Set<EnumRfidWorkstationCapability>,
    val deviceBindings: List<RfidWorkstationDeviceBindingVo>,
    val antennaDirections: Map<Int, EnumRfidDirection>,
    val passageDebounceSeconds: Int,
    val alarmOutputIndex: Int,
    val alarmDurationMillis: Long,
    val enabled: Boolean,
    val lastLoginAt: Long?,
)

data class RfidOperatorCardSaveVo(
    val id: String? = null,
    val cardUid: String? = null,
    val userId: String? = null,
    val allowedCapabilities: Set<EnumRfidWorkstationCapability> = emptySet(),
    val validFrom: Long? = null,
    val validUntil: Long? = null,
)

data class RfidCardLoginVo(
    val stationCode: String? = null,
    val cardUid: String? = null,
)

data class RfidWorkstationEnrollVo(
    val stationCode: String? = null,
    val enrollmentToken: String? = null,
)

data class RfidWorkstationEnrollResultVo(
    val workstationId: String,
    val workstationName: String,
)

data class RfidWorkstationLeaseVo(
    val workstationId: String,
    val leaseUntil: Long,
)

/**
 * 当前浏览器凭证绑定的工作站。客户端以此为唯一可信绑定，URL 参数只用于首次部署定位。
 */
data class RfidWorkstationContextVo(
    val workstationId: String,
    val workstationCode: String,
    val workstationName: String,
    val defaultCapability: EnumRfidWorkstationCapability?,
    val capabilities: Set<EnumRfidWorkstationCapability>,
    val enabled: Boolean,
)

data class RfidCardLoginResultVo(
    val token: String,
    val expirationMillis: Long,
    val userId: String,
    val userName: String?,
    val workstationId: String,
    val workstationName: String,
)

data class RfidTagVo(
    val id: String,
    val orgId: String,
    val epc: String,
    val tid: String?,
    val collectionId: String?,
    val collectionNo: String?,
    val collectionName: String?,
    val status: EnumRfidTagStatus,
    val issuedAt: Long?,
    val activatedAt: Long?,
    val retiredAt: Long?,
    val retiredReason: String?,
)

data class RfidTagJobCreateVo(
    val collectionId: String? = null,
    val workstationId: String? = null,
    val targetEpc: String? = null,
    val expectedTid: String? = null,
    val clientRequestId: String? = null,
)

data class RfidTagJobVerifyVo(
    /** 写后由同一发标工作站完成的 TAG_VERIFY 扫描会话；服务端从会话证据取 EPC/TID。 */
    val scanSessionId: String? = null,
)

data class RfidTagJobWriteVo(
    val tid: String? = null,
)

data class RfidTagRetireVo(
    val reason: String? = null,
)

data class RfidTagJobVo(
    val id: String,
    val collectionId: String,
    val workstationId: String,
    val tagId: String,
    val targetEpc: String,
    val expectedTid: String?,
    val verifiedEpc: String?,
    val verifiedTid: String?,
    val commandId: String?,
    val status: EnumRfidTagJobStatus,
    val errorMessage: String?,
)

data class RfidTagOperationVo(
    val id: String,
    val tagId: String,
    val operationType: EnumRfidTagOperationType,
    val workstationId: String?,
    val operatorId: String?,
    val commandId: String?,
    val beforeEpc: String?,
    val afterEpc: String?,
    val tid: String?,
    val success: Boolean,
    val resultCode: String?,
    val detail: String?,
    val occurredAt: Long,
)

data class RfidScanSessionCreateVo(
    val workstationId: String? = null,
    /** 手持机直传时从工作站已绑定设备中选择；固定设备会话由服务端按用途和在线状态解析。 */
    val deviceId: String? = null,
    val type: EnumRfidScanSessionType? = null,
    val businessType: String? = null,
    val businessId: String? = null,
    val collectionIds: List<String> = emptyList(),
    val clientRequestId: String? = null,
    /** 手持机 SDK 自己控制射频时为 true；借还台固定读写器由服务端命令队列控制。 */
    val directInput: Boolean = false,
)

/** 手持端只展示已启用、已绑定手持 RFID 设备且当前用户可写的工作站。 */
data class RfidHandheldWorkstationVo(
    val id: String,
    val code: String,
    val name: String,
    val taskTypes: Set<EnumRfidScanSessionType>,
    val devices: List<RfidHandheldDeviceVo>,
)

data class RfidHandheldDeviceVo(
    val id: String,
    val isDefault: Boolean,
    val name: String? = null,
    val orgId: String? = null,
)

/** 手持端领取任务使用的轻量业务摘要；businessId 可直接提交给扫描会话创建接口。 */
data class RfidHandheldTaskVo(
    val type: EnumRfidScanSessionType,
    val businessId: String,
    val title: String,
    val detail: String?,
    val collectionCount: Int?,
    val createdAt: Long,
)

data class RfidHandheldReadVo(
    /** 手持机上传时必填且必须与服务端会话锁定设备一致。 */
    val deviceId: String? = null,
    val epc: String? = null,
    val tid: String? = null,
    val antenna: Int? = null,
    val direction: EnumRfidDirection? = null,
    val rssi: Int? = null,
    val occurredAt: Long? = null,
    val sourceEventId: String? = null,
)

data class RfidHandheldReadBatchVo(
    val deviceId: String? = null,
    val localSessionId: String? = null,
    val batchSequence: Long? = null,
    val reads: List<RfidHandheldReadVo> = emptyList(),
)

data class RfidHandheldReadBatchResultVo(
    val sessionId: String,
    val localSessionId: String,
    val batchSequence: Long,
    val accepted: Int,
    val duplicate: Boolean,
    val preview: RfidScanPreviewVo,
)

data class RfidHandheldSealVo(val localSessionId: String, val batchCount: Long)

data class RfidReadVo(
    val epc: String,
    val tid: String?,
    val antenna: Int?,
    val direction: EnumRfidDirection,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
    val readCount: Long,
    val peakRssi: Int?,
    val collectionId: String?,
    val collectionNo: String?,
    val collectionName: String?,
    val classification: EnumRfidReadClassification,
)

data class RfidMissingVo(
    val collectionId: String,
    val collectionNo: String?,
    val collectionName: String?,
    val expectedEpc: String?,
)

data class RfidScanPreviewVo(
    val sessionId: String,
    val state: EnumRfidScanSessionState,
    val expected: Int,
    val matched: Int,
    val missing: Int,
    val extra: Int,
    val unknown: Int,
    val conflict: Int,
    val stopAcknowledged: Boolean,
    val settled: Boolean,
    val canFinalize: Boolean,
    val blockReasons: List<String>,
    val reads: List<RfidReadVo>,
    val missingItems: List<RfidMissingVo>,
)

data class RfidScanSessionVo(
    val id: String,
    val sessionNo: String,
    val orgId: String,
    val workstationId: String?,
    val deviceId: String,
    val type: EnumRfidScanSessionType,
    val businessType: String?,
    val businessId: String?,
    val operatorId: String,
    val directInput: Boolean,
    val state: EnumRfidScanSessionState,
    val startCommandId: String?,
    val stopCommandId: String?,
    val expectedCount: Int,
    val readCount: Int,
    val createdAt: Long,
    val startedAt: Long?,
    val stoppedAt: Long?,
    val finalizedAt: Long?,
    val errorMessage: String?,
)

data class RfidScanFinalizeVo(
    val force: Boolean = false,
    val reason: String? = null,
    val conditionNote: String? = null,
    val conditionPhotoIds: String? = null,
    val signedVoucherAttachmentIds: String? = null,
)

data class RfidPassageGrantVo(
    val id: String,
    val collectionId: String,
    val epc: String,
    val sourceType: String,
    val sourceId: String,
    val gateId: String,
    val direction: EnumRfidDirection,
    val validFrom: Long,
    val validUntil: Long,
    val status: EnumRfidPassageGrantStatus,
    val whitelistSyncStatus: EnumRfidWhitelistSyncStatus,
)

data class RfidPassageGrantCreateVo(
    val collectionId: String? = null,
    val sourceType: String? = null,
    val sourceId: String? = null,
    val gateId: String? = null,
    val direction: EnumRfidDirection? = null,
    val validFrom: Long? = null,
    val validUntil: Long? = null,
    val syncDeviceAllowlist: Boolean = false,
)

data class RfidPassageGrantRevokeVo(
    val reason: String? = null,
)

data class RfidPassageEventCreateVo(
    val deviceId: String? = null,
    val gateId: String? = null,
    val epc: String? = null,
    val tid: String? = null,
    val antenna: Int? = null,
    val direction: EnumRfidDirection? = null,
    val occurredAt: Long? = null,
    val sourceEventId: String? = null,
)

data class RfidPassageEventVo(
    val id: String,
    val deviceId: String,
    val gateId: String,
    val antenna: Int?,
    val epc: String,
    val tid: String?,
    val direction: EnumRfidDirection,
    val occurredAt: Long,
    val receivedAt: Long,
    val collectionId: String?,
    val grantId: String?,
    val decision: EnumRfidPassageDecision,
    val anomalyId: String?,
    val alarmCommandId: String?,
    val alarmStatus: EnumRfidAlarmStatus,
    val alarmMessage: String?,
    val sourceSessionId: String? = null,
    val sourceType: String? = null,
    val sourceId: String? = null,
    val reason: String? = null,
    val businessDirection: EnumRfidDirection? = null,
)

data class RfidPassageDeviceVo(val id: String, val name: String, val code: String)

data class RfidAnomalyProcessVo(
    val status: EnumRfidAnomalyStatus? = null,
    val note: String? = null,
)

data class RfidAnomalyVo(
    val id: String,
    val eventId: String?,
    val collectionId: String?,
    val epc: String?,
    val type: EnumRfidAnomalyType,
    val status: EnumRfidAnomalyStatus,
    val title: String,
    val detail: String?,
    val openedAt: Long,
    val acknowledgedAt: Long?,
    val resolvedAt: Long?,
    val handlerId: String?,
    val resolutionNote: String?,
    val evidence: RfidPassageEventVo? = null,
)

data class RfidRealtimeEventVo(
    val id: String,
    val type: EnumRfidRealtimeEventType,
    val workstationId: String?,
    val sessionId: String?,
    val sequence: Long,
    val occurredAt: Long,
    val payload: Map<String, Any?>,
)

/** 设备模块适配口，避免 collection 编译期依赖某个厂商协议或 Socket 实现。 */
interface RfidDeviceCommandPort {
    fun startInventory(deviceId: String, sessionId: String, antennaMask: Long = 1L): String
    fun stopInventory(deviceId: String, sessionId: String): String
    fun writeEpc(deviceId: String, jobId: String, epc: String, tidFilter: String): String
    fun syncAllowlist(deviceId: String, businessId: String, epcs: List<String>): String
    fun supportsAllowlist(deviceId: String): Boolean
    fun alarmOutput(deviceId:   String, businessId: String, outputIndex: Int = 1, durationMillis: Long = 3_000): String
    fun outcome(commandId: String): RfidDeviceCommandOutcome?
    fun allowsDirectInput(deviceId: String): Boolean
    /** 固定设备候选项运行前必须有有效在线租约；手持机直传不使用此判断。 */
    fun isOnline(deviceId: String): Boolean = true
    fun isWorkstationLocalDevice(deviceId: String): Boolean = false
    /** 默认实现兼容未接入统一设备模块的平台；collection 生产平台会覆写为严格校验。 */
    fun supportsAnyRole(deviceId: String, orgId: String, roleCodes: Set<String>): Boolean = true
}

data class RfidDeviceCommandOutcome(
    val terminal: Boolean,
    val successful: Boolean,
    val message: String?,
)

/** collection 内的人员卡令牌签发接口，不依赖平台的 JWT 登录或 OAuth2 模块。 */
interface RfidCardTokenIssuer {
    fun issue(userId: String, workstationOrgId: String, requiredAuthorities: Set<String>): RfidIssuedUserToken
}

data class RfidIssuedUserToken(
    val token: String,
    val expirationMillis: Long,
    val userName: String?,
)
