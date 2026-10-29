package io.hizhengfu.cloud.collection.domain

import io.hizhengfu.cloud.collection.dao.AcquisitionVo
import io.hizhengfu.cloud.collection.dao.CollectionOrgVo
import io.hizhengfu.cloud.collection.dao.CollectionVo
import io.hizhengfu.cloud.collection.dao.InventoryVo
import io.hizhengfu.cloud.collection.dao.MovementVo
import io.hizhengfu.cloud.collection.dao.WarehouseVo
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.LocalDate
import java.time.LocalDateTime

/** 藏品核心摘要，用于展览、借展、提用等多件藏品流程的列表与详情展示。 */
data class CollectionSummaryVo(
    val id: String,
    val totalRegNo: String? = null,
    val name: String? = null,
)

/** 尺寸（通高/口径/底径/腹径/重量…） */
data class DimensionVo(
    val height: String? = null,
    val mouth: String? = null,
    val bottom: String? = null,
    val belly: String? = null,
    val length: String? = null,
    val width: String? = null,
    val diameter: String? = null,
    val extra: Map<String, String>? = null,
)

/** 藏品台账查询条件（@ModelAttribute 绑定 query string） */
class CollectionQueryVo {
    var collectionId: String? = null
    /** 跨页选择清单重新读取；逐次按当前授权过滤，不信任浏览器缓存。 */
    var collectionIds: String? = null
    var registrationScope: EnumCollectionRegistrationScope? = null
    var relicLevelScope: EnumCollectionRelicLevelScope? = null
    var nature: EnumCollectionNature? = null
    var searchSort: EnumCollectionSearchSort? = null
    var numberMatch: EnumCollectionNumberMatch? = null
    var orgId: String? = null
    var keyword: String? = null
    var totalRegNo: String? = null
    var classNo: String? = null
    var era: String? = null
    var texture: String? = null
    var level: EnumRelicLevel? = null
    var source: EnumSource? = null
    var lifeStatus: EnumCollectionStatus? = null
    var lifeStatuses: List<EnumCollectionStatus>? = null
    var catalogStatus: EnumCatalogStatus? = null
    var completeness: EnumCompleteness? = null
    var categoryId: String? = null
    var warehouseId: String? = null
    var registeredDateStart: LocalDate? = null
    var registeredDateEnd: LocalDate? = null
    var acquireDateStart: LocalDate? = null
    var acquireDateEnd: LocalDate? = null
    var createTimeStart: LocalDateTime? = null
    var createTimeEnd: LocalDateTime? = null
    var hasImage: Boolean? = null
    var hasAttachment: Boolean? = null
}

enum class EnumCollectionRegistrationScope { REGISTERED, PENDING, UNVERIFIED, DEREGISTERED, ALL }
enum class EnumCollectionRelicLevelScope { UNGRADED, PRECIOUS }
enum class EnumCollectionSearchSort { RELEVANCE, REG_NO_ASC, REG_NO_DESC, NAME_ASC, REGISTERED_DESC, ACQUIRE_DESC }
enum class EnumCollectionNumberMatch { CONTAINS, EXACT }

/** 关键字段更正请求 */
data class CollectionCorrectVo(
    @field:NotBlank val field: String? = null,
    @field:NotBlank val value: String? = null,
    @field:NotBlank val reason: String? = null,
)

/** 编目审核请求 */
data class CollectionCatalogAuditVo(
    @field:NotNull val status: EnumCatalogStatus? = null,
    val opinion: String? = null,
)

/** 藏品统计概览（按可见收藏单位聚合） */
data class CollectionStatVo(
    val total: Int = 0,
    val byLevel: Map<String, Int> = emptyMap(),
    val byNature: Map<String, Int> = emptyMap(),
    val byRelicLevel: Map<String, Int> = emptyMap(),
    val byStatus: Map<String, Int> = emptyMap(),
    val byCategory: Map<String, Int> = emptyMap(),
    val byEra: Map<String, Int> = emptyMap(),
    val bySource: Map<String, Int> = emptyMap(),
    val byWarehouse: Map<String, Int> = emptyMap(),
    val quantityByUnit: Map<String, Long> = emptyMap(),
    val quantityUnverifiedTotal: Int = 0,
    val imageCount: Int = 0,
    val attachmentCount: Int = 0,
    val imageRate: Int = 0,
    val attachmentRate: Int = 0,
    val registeredThisYear: Int = 0,
    val registeredThisMonth: Int = 0,
    val pendingRegistrationTotal: Int = 0,
    val unverifiedRegistrationTotal: Int = 0,
    val retiredTotal: Int = 0,
    /** 未退出的文物，包含待登记及历史登记待核实；不是行政定级流程完成情况。 */
    val ungradedRelicTotal: Int = 0,
)

/** 一物一档影像资源统计。 */
data class CollectionMediaStatVo(
    val total: Int = 0,
    val collectionCount: Int = 0,
    val byType: Map<String, Int> = emptyMap(),
    /** 组合统计用的可见藏品影像数量，不返回原件身份和文件内容。 */
    val byCollection: Map<String, Map<String, Int>> = emptyMap(),
)

/** 批量文件关联类型：影像进入 col_media，其余业务材料进入 col_attachment。 */
enum class EnumCollectionFileImportKind { MEDIA, ATTACHMENT }

/**
 * 一普/台账批量文件关联行。
 * totalRegNo 可显式提供；为空时从文件名 `<总登记号>__<类型>__<序号>.<扩展名>` 解析。
 */
data class CollectionFileImportItemVo(
    val rowNo: Int = 0,
    val totalRegNo: String? = null,
    @field:NotBlank val fileId: String? = null,
    val fileName: String? = null,
    val kind: EnumCollectionFileImportKind = EnumCollectionFileImportKind.MEDIA,
    val mediaType: EnumCollectionMediaType? = null,
    val attachmentType: EnumCollectionAttachmentType? = null,
    val title: String? = null,
    val isCover: Boolean? = null,
    val sort: Int? = null,
    val remark: String? = null,
)

data class CollectionFileImportMatchVo(
    val rowNo: Int, val collectionId: String, val totalRegNo: String, val collectionName: String,
    val orgId: String, val fileId: String, val fileName: String?, val kind: EnumCollectionFileImportKind,
    val mediaType: EnumCollectionMediaType?, val attachmentType: EnumCollectionAttachmentType?,
    val contentSha256: String, val size: Long, val existingId: String? = null, val bindingId: String? = null,
)

data class CollectionFileImportResultVo(
    val valid: Boolean,
    val matchedCount: Int = 0,
    val mediaCount: Int = 0,
    val attachmentCount: Int = 0,
    val createdIds: List<String> = emptyList(),
    val unchangedIds: List<String> = emptyList(),
    val errors: List<CollectionImportErrorVo> = emptyList(),
    val confirmation: CollectionImportConfirmationVo? = null,
    val matches: List<CollectionFileImportMatchVo> = emptyList(),
)

/** 工作台：出入库按月趋势点 */
data class MovementTrendVo(
    val month: String,
    val inCount: Int = 0,
    val outCount: Int = 0,
)

/** 工作台：编目审核汇总（通过率口径 = approved/(approved+rejected)） */
data class CatalogAuditSummaryVo(
    val draft: Int = 0,
    val pending: Int = 0,
    val approved: Int = 0,
    val rejected: Int = 0,
    val passRate: Int = 0,
)

/** 工作台：进行中的流程单据（最新动态） */
data class OngoingFlowVo(
    val bizType: String,
    val bizName: String,
    val id: String,
    val title: String?,
    val applicant: String? = null,
    var applicantName: String? = null,
    /** 当前审批节点名称；并行节点以“ / ”连接。 */
    var stage: String? = null,
    /** 仅供工作台批量解析当前节点。 */
    val flowInstanceId: String? = null,
    val createTime: java.time.LocalDateTime? = null,
)

/** RFID 绑定请求 */
data class RfidBindVo(
    @field:NotBlank val tag: String? = null,
)

/** RFID 批量扫描请求 */
data class RfidBatchQueryVo(
    val tags: List<String> = emptyList(),
)

/** RFID 批量扫描结果 */
data class RfidBatchResultVo(
    val matched: List<io.hizhengfu.cloud.collection.dao.CollectionVo> = emptyList(),
    val unknown: List<String> = emptyList(),
)

/** RFID 盘点比对请求 */
data class InventoryRfidCheckVo(
    @field:NotBlank val batchName: String? = null,
    val warehouseId: String? = null,
    val tags: List<String> = emptyList(),
)

/** RFID 盘点比对结果摘要 */
data class InventoryRfidCheckResultVo(
    val batchName: String? = null,
    val createdCount: Int = 0,
    val matchCount: Int = 0,
    val missingCount: Int = 0,
    val abnormalCount: Int = 0,
    /** 应盘但未绑定标签、无法参与 RFID 比对的藏品数 */
    val untaggedCount: Int = 0,
    val unknownTags: List<String> = emptyList(),
)

/** 年度统计（藏品年终上报口径：期初/新增/退出馆藏/期末/流动量；级别分类为当前在册快照） */
data class AnnualStatVo(
    val year: Int,
    val openingTotal: Int = 0,
    val newTotal: Int = 0,
    val newBySource: Map<String, Int> = emptyMap(),
    val deregTotal: Int = 0,
    val deregByReason: Map<String, Int> = emptyMap(),
    val closingTotal: Int = 0,
    val inCount: Int = 0,
    val outCount: Int = 0,
    val closingByLevel: Map<String, Int> = emptyMap(),
    val closingByCategory: Map<String, Int> = emptyMap(),
    val orgId: String? = null,
    val cutoff: LocalDateTime? = null,
    val completeYear: Boolean = false,
    val transferInTotal: Int = 0,
    val transferOutTotal: Int = 0,
    val quantityBalances: List<AnnualUnitBalanceVo> = emptyList(),
    val issues: List<AnnualLedgerIssueVo> = emptyList(),
    val events: List<AnnualLedgerEventVo> = emptyList(),
)

/** 普查导出行：总登记号/分类号 + WW/T 0017 14 项基本指标。 */
data class CollectionSurveyExportVo(
    val totalRegNo: String? = null,
    val classNo: String? = null,
    val name: String? = null,
    val era: String? = null,
    val categoryName: String? = null,
    val texture: String? = null,
    val dimensions: String? = null,
    val weight: String? = null,
    val completeness: String? = null,
    val completeStatus: String? = null,
    val source: String? = null,
    val acquireDate: String? = null,
    val level: String? = null,
    val sourceDesc: String? = null,
    val orgName: String? = null,
    val image: String? = null,
    val originalName: String? = null,
    val originalNo: String? = null,
    val quantity: Int? = null,
    val unit: String? = null,
    val actualQuantity: Int? = null,
    val actualQuantityUnit: String? = null,
    val dimensionUnit: String? = null,
    val weightUnit: String? = null,
    val preserveState: String? = null,
    val entryDate: String? = null,
    val receiptCompletedAt: String? = null,
    val registeredAt: String? = null,
    val entryVoucherNo: String? = null,
    val exchangeCode: String? = null,
    val standardDimensions: String? = null,
    val standardWeight: String? = null,
    val registrationDetails: CollectionRegistrationDetailsVo? = null,
    val qualityIssues: List<String> = emptyList(),

)

/** 批量导入行，既可由 Excel 解析得到，也可由前端 JSON 直接提交。 */
data class CollectionImportRowVo(
    val rowNo: Int = 0,
    val nature: String? = null,
    val totalRegNo: String? = null,
    val classNo: String? = null,
    val auxNo: String? = null,
    val name: String? = null,
    val originalName: String? = null,
    val categoryId: String? = null,
    val categoryName: String? = null,
    val eraMeaning: String? = null,
    val eraNotation: String? = null,
    val era: String? = null,
    val texture: String? = null,
    val textureCategory1: String? = null,
    val textureCategory2: String? = null,
    val level: String? = null,
    val quantity: Int? = null,
    val unit: String? = null,
    val source: String? = null,
    val sourceDesc: String? = null,
    val acquireDate: LocalDate? = null,
    val acquireDateRange: String? = null,
    val acquireYear: String? = null,
    val completeness: String? = null,
    val completeStatus: String? = null,
    val height: String? = null,
    val mouth: String? = null,
    val bottom: String? = null,
    val belly: String? = null,
    val length: String? = null,
    val width: String? = null,
    val diameter: String? = null,
    val weight: String? = null,
    val weightRange: String? = null,
    val weightUnit: String? = null,
    val region: String? = null,
    val volume: String? = null,
    val volumeUnit: String? = null,
    val color: String? = null,
    val sourceNo: String? = null,
    val country: String? = null,
    val culture: String? = null,
    val inscription: String? = null,
    val feature: String? = null,
    val author: String? = null,
    val edition: String? = null,
    val volumeNo: String? = null,
    val preserveState: String? = null,
    val valuation: Long? = null,
    val coverImage: String? = null,
    val coverImageName: String? = null,
    val warehouseId: String? = null,
    val warehouseName: String? = null,
    val remark: String? = null,
    val actualQuantity: Int? = null,
    val actualQuantityUnit: String? = null,
    val dimensionUnit: String? = null,
    val entryDate: LocalDate? = null,

)

data class CollectionImportErrorVo(
    val rowNo: Int,
    val field: String,
    val message: String,
)

/** 台账导入策略：默认仅新增；显式选择 UPSERT 时按“当前收藏单位 + 总登记号”更新。 */
enum class EnumCollectionImportMode { CREATE_ONLY, UPSERT }

data class CollectionImportConfirmationVo(val orgId: String, val orgName: String, val validationHash: String)

data class CollectionImportValidateResultVo(
    val valid: Boolean,
    val errors: List<CollectionImportErrorVo> = emptyList(),
    val items: List<CollectionVo> = emptyList(),
    val createCount: Int = 0,
    val updateCount: Int = 0,
    val confirmation: CollectionImportConfirmationVo? = null,
)

data class CollectionImportTemplateColumnVo(
    val field: String,
    val title: String,
    val required: Boolean = false,
    val dictType: String? = null,
    val sample: String? = null,
)

data class CollectionImportCommitResultVo(
    val valid: Boolean,
    val createdIds: List<String> = emptyList(),
    val updatedIds: List<String> = emptyList(),
    val unchangedIds: List<String> = emptyList(),
    val errors: List<CollectionImportErrorVo> = emptyList(),
)

data class InventoryImportRowVo(
    val rowNo: Int = 0,
    val batchName: String? = null,
    val collectionId: String? = null,
    val totalRegNo: String? = null,
    val result: String? = null,
    val note: String? = null,
    val attachmentIds: String? = null,
    val attachmentNames: String? = null,
)

data class InventoryImportValidateResultVo(
    val valid: Boolean,
    val errors: List<CollectionImportErrorVo> = emptyList(),
    val items: List<InventoryVo> = emptyList(),
)

data class InventoryImportCommitResultVo(
    val valid: Boolean,
    val createdIds: List<String> = emptyList(),
    val errors: List<CollectionImportErrorVo> = emptyList(),
)

data class MovementImportRowVo(
    val rowNo: Int = 0,
    val documentNo: String? = null,
    val collectionId: String? = null,
    val totalRegNo: String? = null,
    val type: String? = null,
    val reason: String? = null,
    val basis: String? = null,
    val department: String? = null,
    val fromWarehouseId: String? = null,
    val toWarehouseId: String? = null,
    val operator: String? = null,
    val reviewer: String? = null,
    val custodian: String? = null,
    val handoverUser: String? = null,
    val reviewOpinion: String? = null,
    val attachmentIds: String? = null,
    val refBizId: String? = null,
)

data class MovementImportValidateResultVo(
    val valid: Boolean,
    val errors: List<CollectionImportErrorVo> = emptyList(),
    val items: List<MovementVo> = emptyList(),
)

data class MovementImportCommitResultVo(
    val valid: Boolean,
    val createdIds: List<String> = emptyList(),
    val errors: List<CollectionImportErrorVo> = emptyList(),
)

data class WarehouseImportRowVo(
    val rowNo: Int = 0,
    val parentId: String? = null,
    val parentName: String? = null,
    val parentCode: String? = null,
    val code: String? = null,
    val name: String? = null,
    val thumbnail: String? = null,
    val thumbnailName: String? = null,
    val sceneType: String? = null,
    val type: String? = null,
    val capacity: Int? = null,
    val temperature: String? = null,
    val custodian: String? = null,
    val sort: Int? = null,
)

data class WarehouseImportValidateResultVo(
    val valid: Boolean,
    val errors: List<CollectionImportErrorVo> = emptyList(),
    val items: List<WarehouseVo> = emptyList(),
)

data class WarehouseImportCommitResultVo(
    val valid: Boolean,
    val createdIds: List<String> = emptyList(),
    val errors: List<CollectionImportErrorVo> = emptyList(),
)

data class CollectionOrgImportRowVo(
    val rowNo: Int = 0,
    val parentId: String? = null,
    val parentName: String? = null,
    val parentCode: String? = null,
    val code: String? = null,
    val name: String? = null,
    val shortName: String? = null,
    val level: String? = null,
    val ownership: String? = null,
    val legalPerson: String? = null,
    val areaId: String? = null,
    val areaName: String? = null,
    val areaCode: String? = null,
    val address: String? = null,
    val contact: String? = null,
    val phone: String? = null,
    val sourceId: String? = null,
    val locationRootId: String? = null,
    val locationRootName: String? = null,
    val status: String? = null,
    val sort: Long? = null,
)

data class CollectionOrgImportValidateResultVo(
    val valid: Boolean,
    val errors: List<CollectionImportErrorVo> = emptyList(),
    val items: List<CollectionOrgVo> = emptyList(),
)

data class CollectionOrgImportCommitResultVo(
    val valid: Boolean,
    val createdIds: List<String> = emptyList(),
    val errors: List<CollectionImportErrorVo> = emptyList(),
)

data class AcquisitionImportRowVo(
    val rowNo: Int = 0,
    val name: String? = null,
    val categoryId: String? = null,
    val categoryName: String? = null,
    val era: String? = null,
    val texture: String? = null,
    val proposedLevel: String? = null,
    val source: String? = null,
    val quantity: Int? = null,
    val unit: String? = null,
    val acquireDate: LocalDate? = null,
    val height: String? = null,
    val mouth: String? = null,
    val bottom: String? = null,
    val belly: String? = null,
    val length: String? = null,
    val width: String? = null,
    val diameter: String? = null,
    val weight: String? = null,
    val completeness: String? = null,
    val completeStatus: String? = null,
    val holder: String? = null,
    val proposedPrice: Long? = null,
    val appraisedValue: Long? = null,
    val reason: String? = null,
    val images: String? = null,
    val imageNames: String? = null,
    val expertOpinionAttachmentIds: String? = null,
    val expertOpinionAttachmentNames: String? = null,
    val approvalAttachmentIds: String? = null,
    val approvalAttachmentNames: String? = null,
    val contractAttachmentIds: String? = null,
    val contractAttachmentNames: String? = null,
    val invoiceAttachmentIds: String? = null,
    val invoiceAttachmentNames: String? = null,
)

data class AcquisitionImportValidateResultVo(
    val valid: Boolean,
    val errors: List<CollectionImportErrorVo> = emptyList(),
    val items: List<AcquisitionVo> = emptyList(),
)

data class AcquisitionImportCommitResultVo(
    val valid: Boolean,
    val createdIds: List<String> = emptyList(),
    val errors: List<CollectionImportErrorVo> = emptyList(),
)

data class InventoryBatchSummaryVo(
    val batchName: String,
    val firstInventoryId: String? = null,
    val total: Int = 0,
    val matchCount: Int = 0,
    val surplusCount: Int = 0,
    val missingCount: Int = 0,
    val abnormalCount: Int = 0,
    val attachmentCount: Int = 0,
    val firstCheckedAt: LocalDateTime? = null,
    val lastCheckedAt: LocalDateTime? = null,
)

data class CollectionPrintFieldVo(
    val label: String,
    val value: String?,
    val dictType: String? = null,
    /** 仅服务端已核对业务字段显式声明的附件；value 保留兼容原模板。 */
    val fileReferences: List<String> = emptyList(),
)

data class CollectionPrintTemplateVo(
    val code: String,
    val type: EnumCollectionPrintType,
    val name: String,
    val title: String,
    val documentLabel: String,
    val collectionTitle: String,
    val templatePath: String = "",
    val signatures: List<String>,
    val sort: Int,
    val enabled: Boolean = true,
    val format: String = "DOCX",
    val orgId: String? = null,
    val remark: String? = null,
    val custom: Boolean = false,
    val createTime: LocalDateTime? = null,
)

data class CollectionPrintVo(
    val type: EnumCollectionPrintType,
    val title: String,
    val businessId: String? = null,
    val documentNo: String?,
    val orgId: String? = null,
    val orgName: String?,
    val generatedAt: LocalDateTime = LocalDateTime.now(),
    val fields: List<CollectionPrintFieldVo> = emptyList(),
    val collections: List<CollectionVo> = emptyList(),
    val template: CollectionPrintTemplateVo? = null,
)

data class CollectionPrintRenderRequest(
    val type: EnumCollectionPrintType? = null,
    val id: String? = null,
    val ids: List<String> = emptyList(),
    val data: CollectionPrintVo? = null,
    val items: List<CollectionPrintVo> = emptyList(),
)

data class CollectionPrintFieldUsageVo(
    val group: String,
    val label: String,
    val placeholder: String,
    val example: String,
    val usage: String,
)

data class CollectionPrintTemplateUsageVo(
    val type: EnumCollectionPrintType,
    val fields: List<CollectionPrintFieldUsageVo>,
)
