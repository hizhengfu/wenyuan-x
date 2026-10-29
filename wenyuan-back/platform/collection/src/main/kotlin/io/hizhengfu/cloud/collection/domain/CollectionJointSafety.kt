package io.hizhengfu.cloud.collection.domain

import java.time.LocalDateTime

/** 定期频率按本次使用的实际风险确定，不虚构统一法定天数。 */
data class CollectionJointSafetyPlanVo(
    val previousPlanId:String?=null,
    val sourceRevision:String,
    val preparedAt:LocalDateTime,
    val inspectionIntervalDays:Int,
    val firstDueAt:LocalDateTime,
    val keeperUserId:String,
    val useUserId:String,
    val securityUserId:String,
    val directorUserId:String,
    val scopeAndMethod:String,
    val environmentalAndSecurityChecks:String,
    val measuresAndReporting:String,
    val planFileIds:String,
)
/** 每轮只计算实际检查且共同确认的件，未查件继续独立显示到期。 */
data class CollectionJointSafetyRoundVo(
    val planId:String,
    val startedAt:LocalDateTime,
    val endedAt:LocalDateTime,
    val checkIds:Set<String>,
    val actualAreas:String,
    val jointFindings:String,
    val actualMeasures:String,
    val signedFileIds:String,
)
data class CollectionJointSafetyReportVo(
    val reportedAt:LocalDateTime,
    val reporterUserId:String,
    val directorUserId:String,
    val content:String,
    val immediateMeasures:String,
    val signedFileIds:String,
)
