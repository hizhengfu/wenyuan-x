package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.collection.service.CollectionFragilityEvidence as E
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

internal object CollectionFragileEnvironmentEvidence {
    fun criteria(v: FragilityAssessmentVo) {
        val all = v.environmentCriteria
        if (!v.fragileMaterial) {
            if (all.isNotEmpty()) E.fail("非脆弱材质评估不能混填脆弱材质使用环境限值")
            return
        }
        val kinds = buildSet {
            if ((v.limits?.maximumDisplayHours ?: 0) > 0) add(EnumFragileUseKind.DISPLAY)
            if ((v.limits?.maximumTransports ?: 0) > 0) add(EnumFragileUseKind.TRANSPORT)
        }
        if (!all.map { it.kind }.containsAll(kinds)) E.fail("允许展出或运输的脆弱材质藏品，须逐类实际评估数值环境要求及采样有效期；旧文字评估须另立版本")
        if (all.map { it.kind to it.metricCode.uppercase() }.distinct().size != all.size) E.fail("同类使用的环境指标不能重复")
        all.forEach {
            if (listOf(it.metricCode,it.metricName,it.unit,it.basis).any(String::isBlank) || it.metricCode != it.metricCode.trim().uppercase() || it.maximumSampleAgeSeconds <= 0 ||
                (it.minimum == null && it.maximum == null) || listOfNotNull(it.minimum,it.maximum).any { n -> !n.isFinite() } ||
                (it.minimum != null && it.maximum != null && it.minimum > it.maximum)) E.fail("须按本件专业评估填写指标、明确单位、有效数值范围、正数采样有效期和依据")
        }
    }

    fun sources(a: FragilityAssessmentVo, p: FragileUsePlanVo) {
        criteria(a)
        val segments = buildList {
            if (p.displayFrom != null) add(EnumFragileUseKind.DISPLAY to 0)
            p.transports.indices.forEach { add(EnumFragileUseKind.TRANSPORT to it) }
        }
        val expected = segments.flatMap { (kind,index) ->
            val limits = a.environmentCriteria.filter { it.kind == kind }
            if (limits.isEmpty()) E.fail("本段缺少经专业评估的数值环境要求，不能仅凭文字说明授权")
            limits.map { Triple(kind,index,it.metricCode) }
        }.toSet()
        val actual = p.environmentSources.map { Triple(it.kind,it.segmentIndex,it.metricCode) }
        if (actual.size != actual.distinct().size || actual.toSet() != expected) E.fail("须为每个实际展出、运输段逐项指定评估要求对应的唯一测量来源")
        p.environmentSources.forEach {
            if (listOf(it.context,it.instrumentAndMethod,it.representativeness,it.qualificationFileIds).any(String::isBlank)) E.fail("请核实测量场所或路线、仪器方法、对本件环境的代表性及校准资质原件")
            if (it.type == EnumFragileEnvironmentSource.REGISTERED_DEVICE) {
                if (it.kind != EnumFragileUseKind.DISPLAY || it.deviceId.isNullOrBlank() || it.locationId.isNullOrBlank()) E.fail("固定位置联网监测仅适用于本段实际展出，运输须使用对应包装或运输环境的现场签署测量")
            } else if (it.deviceId != null || it.locationId != null) E.fail("现场签署测量不能混用固定位置设备标识")
            if (it.kind == EnumFragileUseKind.TRANSPORT && p.transports[it.segmentIndex].route != it.context) E.fail("运输测量来源须明确对应本段已批准路线及包装环境")
        }
        p.environmentSources.groupBy { it.kind to it.segmentIndex }.values.forEach { rows ->
            if (rows.map { it.context }.distinct().size != 1) E.fail("同一使用段的各项测量须共同对应同一实际场所或路线")
        }
    }

    fun verify(org: String, assessmentId: String, assessment: FragilityAssessmentVo, plan: FragileUsePlanVo,
               start: FragileUseStartVo, provider: CollectionFragileEnvironmentProvider?): Map<String, Any?> {
        sources(assessment,plan)
        val now = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS)
        val required = assessment.environmentCriteria.filter { it.kind == start.kind }
        val sources = plan.environmentSources.filter { it.kind == start.kind && it.segmentIndex == start.segmentIndex }
        val manual = sources.filter { it.type == EnumFragileEnvironmentSource.SIGNED_ON_SITE }.map { it.metricCode }.toSet()
        if (start.environmentReadings.map { it.metricCode }.toSet() != manual || start.environmentReadings.size != manual.size) E.fail("现场测量须完整对应本段批准的现场来源；联网指标由服务器读取，不能遗漏、重复或混填")
        val evidence = sources.map { source ->
            if (source.context != start.actualLocationOrRoute) E.fail("实际场所或路线与本段批准的测量环境不一致，须重新办理适用方案")
            val limit = required.single { it.metricCode == source.metricCode }
            val reading = if (source.type == EnumFragileEnvironmentSource.REGISTERED_DEVICE) {
                (provider ?: E.fail("联网监测核验服务不可用，不能放行，也不能临时改填手工值")).verify(org,source,limit,start.startedAt,now)
            } else {
                val r = start.environmentReadings.single { it.metricCode == source.metricCode }
                if (r.context != source.context || r.instrumentAndMethod != source.instrumentAndMethod || r.measuredBy.isBlank() || r.signedFileIds.isBlank()) E.fail("须逐项核对方案指定的测量场所、仪器方法、实际测量人及签署原件")
                if(r.recordedAt?.let {it<r.measuredAt || it>start.startedAt}==true)E.fail("测量原始记录须在采样之后、实际开始之前形成，不能用事后原件补造当时条件")
                FragileEnvironmentDeviceEvidence(r.value,r.unit,r.measuredAt,mapOf("signedOnSite" to r))
            }
            if (reading.unit != limit.unit || !reading.value.isFinite()) E.fail("${limit.metricName} 测值无效或单位与本件评估不一致；不自动换算")
            if (reading.measuredAt > start.startedAt || start.startedAt > now || reading.measuredAt > now ||
                Duration.between(reading.measuredAt,start.startedAt) > Duration.ofSeconds(limit.maximumSampleAgeSeconds) ||
                Duration.between(reading.measuredAt,now) > Duration.ofSeconds(limit.maximumSampleAgeSeconds)) E.fail("${limit.metricName} 缺少开始前且登记核验时仍有效的实际测量，不能用事后测量补造当时条件；既有事实请据实补录")
            if (limit.minimum?.let { reading.value < it } == true || limit.maximum?.let { reading.value > it } == true) E.fail("${limit.metricName} 不满足本件已确认的数值环境要求，不能开始普通展出或运输")
            mapOf("criterion" to limit,"source" to source,"value" to reading.value,"unit" to reading.unit,"measuredAt" to reading.measuredAt,"evidence" to reading.snapshot)
        }
        return mapOf("assessmentId" to assessmentId,"kind" to start.kind,"segmentIndex" to start.segmentIndex,
            "startedAt" to start.startedAt,"evaluatedAt" to now,"operatorUserId" to start.operatorUserId,
            "actualLocationOrRoute" to start.actualLocationOrRoute,"metrics" to evidence,
            "scope" to "本次开始前采样核验；不证明整个展出或运输期间持续符合要求")
    }
}
