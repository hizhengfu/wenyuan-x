package io.hizhengfu.cloud.collection.domain

import java.time.LocalDateTime

/** 摄影测量、三维扫描的操作与资源安全方案，由实际信息化部门会同保管部门制定。 */
data class DigitalAcquisitionPlanVo(
    val sourceRevision: String,
    val preparedAt: LocalDateTime,
    val informationDepartmentId: String,
    val informationUserId: String,
    val keeperUserId: String,
    val acquisitionMethod: String,
    val workLocation: String,
    val plannedStart: LocalDateTime,
    val plannedEnd: LocalDateTime,
    val environmentAndExposureControls: String,
    val handlingControls: String,
    val processingStandards: String,
    val organizationRules: String,
    val storageAndBackupPlan: String,
    val resourceSecurityPlan: String,
    val rightsAndConfidentiality: String,
    val conditionFileIds: String,
    val operationPlanFileIds: String,
    val safetyPlanFileIds: String,
    val securityEvidenceFileIds: String,
    val signedJointPlanFileIds: String,
    val replacementReason: String? = null,
    val replacementFileIds: String? = null,
)

data class DigitalAcquisitionStartVo(
    val planId: String,
    val startedAt: LocalDateTime,
    val operatorUserId: String,
    val requisitionId: String?,
    val onSiteWarehouseLogId: String?,
    val conditionSafe: Boolean,
    val siteSafe: Boolean,
    val actualControls: String,
    val safetyCheckFileIds: String,
    val signedStartFileIds: String,
)

/** 实物采集结束与数字资源处理完毕分开，不因文件整理尚未完成而虚构实物仍在采集。 */
data class DigitalCaptureVo(
    val finishedAt: LocalDateTime,
    val operatorUserId: String,
    val acquisitionParameters: String,
    val originalUndamaged: Boolean,
    val conditionAfter: String,
    val conditionFileIds: String,
    val rawFileIds: Set<String>,
    val signedCaptureFileIds: String,
    @get:com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    val rawPackageFiles: Map<String, String>? = null,
)

enum class EnumDigitalResourceStage { RAW, PROCESSED, ORGANIZED, STORED }

data class DigitalResourceStageVo(
    val stage: EnumDigitalResourceStage,
    val occurredAt: LocalDateTime,
    val operatorUserId: String,
    val parentResourceIds: Set<String> = emptySet(),
    val fileIds: Set<String>,
    val description: String,
    val technicalMetadata: String,
    val signedRecordFileIds: String,
    val storageLocation: String? = null,
    val backupLocation: String? = null,
    val restorationVerifiedAt: LocalDateTime? = null,
    val accessControlsVerified: Boolean? = null,
    val securityCheckFileIds: String? = null,
    /** 包内相对路径到文件 ID；允许相同字节的一个存储对象用于多个包内路径。 */
    @get:com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    val packageFiles: Map<String, String>? = null,
)

/** SHA-256 从实际存储字节计算；不把上传请求提交的摘要当作校验结果。 */
data class DigitalFileSnapshot(
    val fileId: String,
    val name: String,
    val path: String,
    val storageType: String,
    val size: Long,
    val sha256: String,
)

data class DigitalAcquisitionCompletionVo(
    val completedAt: LocalDateTime,
    val informationUserId: String,
    val keeperUserId: String,
    val storedResourceIds: Set<String>,
    val qualityAndSecurityReview: String,
    val signedJointReviewFileIds: String,
)
