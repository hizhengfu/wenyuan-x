package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import java.time.LocalDateTime

/** 人工核验和自动巡检使用同一原方案、测量来源及证据覆盖规则。 */
internal object CollectionFragilePeriodEvidence {
    fun evaluate(use:CollectionFragileUseRecordDao,from:LocalDateTime,to:LocalDateTime,
                 originals:List<Pair<FragileEnvironmentReadingVo,LocalDateTime>>,
                 providers:List<CollectionFragileEnvironmentProvider>):Pair<List<FragileMetricCoverage>,List<String>> {
        if(to==from)return emptyList<FragileMetricCoverage>() to emptyList()
        val start=use.started!!
        val plan=CollectionFragileUsePlanDao[use.planId!!]
        val criteria=CollectionFragilityAssessmentDao[plan.content.assessmentId].content.environmentCriteria.filter {it.kind==start.kind}
        val sources=plan.content.environmentSources.filter {it.kind==start.kind && it.segmentIndex==start.segmentIndex}
        val issues=buildList {
            if(criteria.isEmpty() || sources.map {it.metricCode}.toSet()!=criteria.map {it.metricCode}.toSet() || sources.size!=criteria.size)
                add("原使用缺少完整的专业数值要求或逐项测量来源，不能认定监测证据完整")
        }
        val metrics=criteria.map {criterion->
            val source=sources.singleOrNull {it.metricCode==criterion.metricCode}
            val evidence=when(source?.type) {
                null->FragilePeriodSourceEvidence(emptyList(),listOf("缺少本段唯一对应的测量来源"))
                EnumFragileEnvironmentSource.REGISTERED_DEVICE->try {
                    providers.singleOrNull()?.inspectPeriod(use.orgId,source,criterion,from,to)
                        ?: FragilePeriodSourceEvidence(emptyList(),listOf("联网区间核验服务不可用，不能推定监测完整"))
                } catch(e:BadRequestException) {FragilePeriodSourceEvidence(emptyList(),listOf(e.message ?: "联网区间证据无法核实"))}
                EnumFragileEnvironmentSource.SIGNED_ON_SITE->FragilePeriodSourceEvidence(
                    (start.environmentReadings.map {it to start.startedAt}+originals)
                        .filter {it.first.metricCode==criterion.metricCode}.distinctBy {it.first}.map {(r,registered)->
                            val reasons=buildList {
                                if(r.context!=source.context || r.instrumentAndMethod!=source.instrumentAndMethod)add("实际测量场所、仪器或方法与批准来源不一致")
                                if(r.signedFileIds.isBlank() || r.measuredBy.isBlank())add("缺少真实测量人或签署原件")
                                if(r.recordedAt?.let {it>registered || it<r.measuredAt}==true)add("原始记录形成时间与采样或本次核验矛盾")
                            }
                            FragilePeriodSample(r.measuredAt,r.recordedAt ?: registered,r.value,r.unit,reasons.isEmpty(),reasons,
                                mapOf("signedOnSite" to r,"availabilityBasis" to if(r.recordedAt!=null)"签署原件载明的记录形成时间" else "首次实际核对该原件的使用开始或检查时间"))
                        })
            }
            FragileEnvironmentCoverage.evaluate(criterion,from,to,evidence)
        }
        return metrics to issues
    }
}
