package io.hizhengfu.cloud.collection.domain

import java.time.LocalDateTime

/** 本件专业评估值；不提供法规并未规定的统一阈值或有效期。 */
data class FragileEnvironmentCriterionVo(
    val kind: EnumFragileUseKind,
    val metricCode: String,
    val metricName: String,
    val unit: String,
    val minimum: Double? = null,
    val maximum: Double? = null,
    val maximumSampleAgeSeconds: Long,
    val basis: String,
)
enum class EnumFragileEnvironmentSource { REGISTERED_DEVICE, SIGNED_ON_SITE }
data class FragileEnvironmentSourceVo(
    val kind: EnumFragileUseKind,
    val segmentIndex: Int,
    val metricCode: String,
    val type: EnumFragileEnvironmentSource,
    val context: String,
    val instrumentAndMethod: String,
    val representativeness: String,
    val qualificationFileIds: String,
    val deviceId: String? = null,
    val locationId: String? = null,
)
/** 现场签署测量须来自方案指定仪器、方法和场所；联网来源由服务器读取，不能混填手输数值。 */
data class FragileEnvironmentReadingVo(
    val metricCode: String,
    val value: Double,
    val unit: String,
    val measuredAt: LocalDateTime,
    val instrumentAndMethod: String,
    val context: String,
    val measuredBy: String,
    val signedFileIds: String,
    val recordedAt: LocalDateTime? = null,
)
/** collection 定义契约，由预防性保护模块读取统一采集事实，避免模块反向依赖。 */
data class FragileEnvironmentDeviceOption(
    val deviceId: String, val deviceName: String, val locationId: String, val locationName: String,
    val metricCode: String, val metricName: String, val unit: String,
)
data class FragileEnvironmentDeviceEvidence(
    val value: Double, val unit: String, val measuredAt: LocalDateTime,
    val snapshot: Map<String, Any?>,
)

data class FragilePeriodSample(
    val measuredAt: LocalDateTime, val receivedAt: LocalDateTime,
    val value: Double?, val unit: String?, val usable: Boolean,
    val reasons: List<String> = emptyList(), val evidence: Map<String, Any?> = emptyMap(),
    val validUntil: LocalDateTime? = null,
)
data class FragilePeriodSourceEvidence(val samples: List<FragilePeriodSample>, val issues: List<String> = emptyList())
data class FragileEvidenceSpan(val from: LocalDateTime, val to: LocalDateTime, val state: String, val reason: String)
data class FragileMetricCoverage(
    val metricCode: String, val metricName: String, val unit: String,
    val from: LocalDateTime, val to: LocalDateTime, val spans: List<FragileEvidenceSpan>,
    val issues: List<String>, val samples: List<FragilePeriodSample>,
) {
    val needsHandling: Boolean get() = issues.isNotEmpty() || spans.any { it.state != "AVAILABLE" }
}

data class FragileEnvironmentMonitoringVo(
    val checkedAt: LocalDateTime, val operatorUserId: String,
    val physicallyPresent: Boolean, val custodianUserId: String? = null,
    val actualCustodyLocation: String, val condition: String, val damaged: Boolean,
    val actualMeasures: String, val followUp: String, val signedFileIds: String,
    val readings: List<FragileEnvironmentReadingVo> = emptyList(),
)
