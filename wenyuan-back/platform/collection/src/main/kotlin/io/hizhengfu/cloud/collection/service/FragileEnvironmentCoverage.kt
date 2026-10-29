package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.domain.*
import java.time.LocalDateTime

/** 评估采样证据的可用时间，绝不将采样有效期表述为实物环境每一时刻持续合格。 */
object FragileEnvironmentCoverage {
    fun evaluate(criterion: FragileEnvironmentCriterionVo, from: LocalDateTime, to: LocalDateTime,
                 evidence: FragilePeriodSourceEvidence): FragileMetricCoverage {
        require(to > from && criterion.maximumSampleAgeSeconds > 0)
        val samples = evidence.samples.sortedWith(compareBy<FragilePeriodSample> { it.receivedAt }.thenBy { it.measuredAt })
        fun problem(s: FragilePeriodSample): String? = when {
            s.measuredAt > s.receivedAt -> "采样与接收时间矛盾"
            !s.usable || s.reasons.isNotEmpty() -> s.reasons.joinToString("；").ifBlank { "采样证据无效" }
            s.value == null || !s.value.isFinite() || s.unit != criterion.unit -> "数值或单位无效"
            criterion.minimum?.let { s.value < it } == true || criterion.maximum?.let { s.value > it } == true -> "超出本件评估范围"
            else -> null
        }
        val issues = evidence.issues.toMutableList()
        samples.filter { it.measuredAt >= from && it.measuredAt < to || it.receivedAt >= from && it.receivedAt < to }.forEach { s ->
            problem(s)?.let { issues += "${s.measuredAt}：$it" }
            if (s.measuredAt < to && s.receivedAt >= to) issues += "${s.measuredAt} 的采样在核验区间结束后才接收，不能回填当时的可用证据"
        }
        val spans = mutableListOf<FragileEvidenceSpan>()
        fun add(a: LocalDateTime, b: LocalDateTime, state: String, reason: String) {
            if (b <= a) return
            val last = spans.lastOrNull()
            if (last?.to == a && last.state == state && last.reason == reason) spans[spans.lastIndex] = last.copy(to=b)
            else spans += FragileEvidenceSpan(a,b,state,reason)
        }
        fun span(s: FragilePeriodSample?, a: LocalDateTime, b: LocalDateTime) {
            if (s == null) { add(a,b,"GAP","尚无当时已经接收的有效采样"); return }
            val bad = problem(s)
            if (bad != null) { add(a,b,if (bad == "超出本件评估范围") "EXCEEDED" else "UNVERIFIED",bad); return }
            // 超长有效期也不得使 LocalDateTime 溢出。数值来自真实评估，不生成默认年龄。
            val expires = minOf(s.validUntil ?: LocalDateTime.MAX,runCatching { s.measuredAt.plusSeconds(criterion.maximumSampleAgeSeconds) }.getOrElse { LocalDateTime.MAX })
            add(a,minOf(b,expires),"AVAILABLE","当时已有未过期且符合本件数值要求的采样")
            add(maxOf(a,expires),b,"GAP","此前采样已超过本件评估有效期")
        }
        var current: FragilePeriodSample? = null
        var cursor = from
        samples.filter { it.receivedAt < to }.groupBy { it.receivedAt }.forEach { (received, group) ->
            val boundary = maxOf(from,received)
            if (boundary > cursor) { span(current,cursor,boundary); cursor = boundary }
            // 迟到旧值不能覆盖当时较新的值；同一采样时刻矛盾值按未知处理。
            group.sortedBy { it.measuredAt }.forEach { sample ->
                val old = current
                if (old == null || sample.measuredAt > old.measuredAt) current = sample
                else if (sample.measuredAt == old.measuredAt && sample != old && (sample.value != old.value || sample.unit != old.unit || sample.usable != old.usable || sample.reasons != old.reasons)) {
                    current = sample.copy(usable=false,reasons=listOf("同一采样时刻存在相互矛盾的证据"))
                    if (sample.measuredAt >= from || received >= from) issues += "${sample.measuredAt} 存在相互矛盾的采样证据"
                }
            }
        }
        span(current,cursor,to)
        return FragileMetricCoverage(criterion.metricCode,criterion.metricName,criterion.unit,from,to,spans,issues.distinct(),samples)
    }
}
