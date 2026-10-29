package io.hizhengfu.cloud.collection.rfid.domain

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.common.exception.BadRequestException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object RfidHex {
    private val HEX = Regex("[0-9A-F]+")

    /** EPC/TID 只接受偶数字节十六进制，避免不同端把同一标签规范化成多个值。 */
    fun normalize(value: String?, field: String, required: Boolean = true, maxLength: Int = 128): String? {
        val normalized = value?.replace(" ", "")?.replace(":", "")?.replace("-", "")
            ?.uppercase()?.takeIf(String::isNotBlank)
        if (required && normalized == null) throw BadRequestException("$field 必填")
        if (normalized == null) return null
        if (normalized.length !in 2..maxLength || normalized.length % 2 != 0 || !HEX.matches(normalized)) {
            throw BadRequestException("$field 必须是 2 至 $maxLength 位偶数长度十六进制")
        }
        return normalized
    }

    /** 人员卡允许厂商以十六进制文本附带常见分隔符上报。 */
    fun normalizeCardUid(value: String?): String {
        val normalized = value?.replace(" ", "")?.replace(":", "")?.replace("-", "")
            ?.uppercase()?.takeIf(String::isNotBlank) ?: throw BadRequestException("cardUid 必填")
        if (normalized.length !in 4..64 || !HEX.matches(normalized)) {
            throw BadRequestException("cardUid 必须是 4 至 64 位十六进制")
        }
        return normalized
    }

    fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

/**
 * 独立工作台的人员卡登录和账号密码登录必须使用同一套权限规则。
 * 人员卡能力只约束卡片本身，不能替代系统功能权限。
 */
object RfidWorkstationAccessPolicy {
    fun requiredAuthorities(capabilities: Set<EnumRfidWorkstationCapability>): Set<String> = buildSet {
        add(CollectionConst.RFID_DESK)
        if (
            EnumRfidWorkstationCapability.BORROW_RETURN in capabilities ||
            EnumRfidWorkstationCapability.INVENTORY in capabilities
        ) {
            add(CollectionConst.SELECT)
            add(CollectionConst.RFID_SCAN)
            add(CollectionConst.RFID_FINALIZE)
        }
        if (EnumRfidWorkstationCapability.TAG_ISSUE in capabilities) {
            // 发标前读取唯一 TID、写后读回都复用固定读写器扫描会话。
            add(CollectionConst.SELECT)
            add(CollectionConst.RFID_SCAN)
            add(CollectionConst.RFID_TAG_WRITE)
        }
        if (EnumRfidWorkstationCapability.PASSAGE_MONITOR in capabilities) add(CollectionConst.RFID_PASSAGE)
    }

    fun isAllowed(
        grantedAuthorities: Set<String>,
        requiredAuthorities: Set<String>,
        administrative: Boolean = false,
    ): Boolean = administrative ||
        "ADMIN" in grantedAuthorities ||
        CollectionConst.ADMIN in grantedAuthorities ||
        grantedAuthorities.containsAll(requiredAuthorities)

    fun requireAllowed(
        grantedAuthorities: Set<String>,
        requiredAuthorities: Set<String>,
        administrative: Boolean = false,
    ) {
        if (!isAllowed(grantedAuthorities, requiredAuthorities, administrative)) {
            throw BadRequestException("当前用户缺少该 RFID 工作站所需权限")
        }
    }
}

/**
 * 扫描状态机的唯一入口。禁止 Service 各自直接赋值，防止出现 READING 直接 FINALIZED 等无证据跳转。
 */
object RfidScanSessionStateMachine {
    private val transitions = mapOf(
        EnumRfidScanSessionState.CREATED to setOf(
            EnumRfidScanSessionState.STARTING,
            EnumRfidScanSessionState.CANCELLED,
            EnumRfidScanSessionState.EXPIRED,
        ),
        EnumRfidScanSessionState.STARTING to setOf(
            EnumRfidScanSessionState.READING,
            EnumRfidScanSessionState.FAILED,
            EnumRfidScanSessionState.CANCELLED,
            EnumRfidScanSessionState.EXPIRED,
        ),
        EnumRfidScanSessionState.READING to setOf(
            EnumRfidScanSessionState.STOPPING,
            EnumRfidScanSessionState.FAILED,
            EnumRfidScanSessionState.EXPIRED,
        ),
        EnumRfidScanSessionState.STOPPING to setOf(
            EnumRfidScanSessionState.PREVIEW_READY,
            EnumRfidScanSessionState.FAILED,
            EnumRfidScanSessionState.EXPIRED,
        ),
        EnumRfidScanSessionState.PREVIEW_READY to setOf(
            EnumRfidScanSessionState.FINALIZING,
            EnumRfidScanSessionState.CANCELLED,
            EnumRfidScanSessionState.STARTING,
            EnumRfidScanSessionState.EXPIRED,
        ),
        EnumRfidScanSessionState.FINALIZING to setOf(
            EnumRfidScanSessionState.FINALIZED,
            EnumRfidScanSessionState.PREVIEW_READY,
            EnumRfidScanSessionState.FAILED,
            EnumRfidScanSessionState.EXPIRED,
        ),
    )

    fun requireTransition(from: EnumRfidScanSessionState, to: EnumRfidScanSessionState) {
        if (to !in transitions[from].orEmpty()) {
            throw BadRequestException("扫描会话不能从" + from.description + "变为" + to.description)
        }
    }
}

data class RfidExpectedSnapshot(
    val collectionId: String,
    val epc: String?,
    val tid: String?,
)

data class RfidObservedSnapshot(
    val epc: String,
    val tid: String?,
    val resolvedCollectionId: String?,
)

data class RfidClassificationResult(
    val reads: Map<String, EnumRfidReadClassification>,
    val missingCollectionIds: Set<String>,
)

object RfidScanClassifier {
    /**
     * TID 不一致优先判为冲突；已知但不在冻结清单内的藏品判为 EXTRA；完全未知标签判为 UNKNOWN。
     */
    fun classify(
        expected: List<RfidExpectedSnapshot>,
        observed: List<RfidObservedSnapshot>,
    ): RfidClassificationResult {
        val expectedByEpc = expected.mapNotNull { item -> item.epc?.let { it to item } }.toMap()
        val matchedCollections = mutableSetOf<String>()
        val classifications = observed.groupBy(RfidObservedSnapshot::epc).mapValues { (epc, reads) ->
            val item = expectedByEpc[epc]
            val hasTidConflict = item?.tid != null && reads.any { read ->
                read.tid != null && item.tid != read.tid
            }
            when {
                // 同一 EPC 在一次会话中只要出现过 TID 冲突，整组均视为冲突，不能被更早的正常读数掩盖。
                hasTidConflict -> EnumRfidReadClassification.CONFLICT
                item != null -> {
                    matchedCollections += item.collectionId
                    EnumRfidReadClassification.EXPECTED
                }
                reads.any { it.resolvedCollectionId != null } -> EnumRfidReadClassification.EXTRA
                else -> EnumRfidReadClassification.UNKNOWN
            }
        }
        return RfidClassificationResult(
            reads = classifications,
            missingCollectionIds = expected.mapTo(linkedSetOf()) { it.collectionId } - matchedCollections,
        )
    }
}
