package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortCheckConstraintName
import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import org.jetbrains.exposed.v1.json.json
import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.autoconfigure.exposed.FlowUIDEntity
import io.hizhengfu.cloud.autoconfigure.exposed.FlowUIDEntityClass
import io.hizhengfu.cloud.autoconfigure.exposed.FlowUIDTable
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.collection.domain.EnumRelicLevel
import io.hizhengfu.cloud.collection.domain.EnumSource
import io.hizhengfu.cloud.collection.domain.DimensionVo
import io.hizhengfu.cloud.collection.domain.EnumCompleteness
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.javatime.date
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 征集入藏（流程驱动）。征集申请→论证/评估→鉴定→审批，审批通过(onFlowCompleted)自动登账生成藏品。
 * 继承 FlowUIDTable（flowStatus/flowInstanceId + 审计）；多馆隔离列 orgId 手动添加。
 */
object Acquisitions : FlowUIDTable("col_acquisition") {
    val orgId = varchar("org_id", 64).index(shortIndexName("org_id")).comment("收藏单位")
    val planId = varchar("plan_id", 64).nullable().index(shortIndexName("plan_id")).comment("所属征集计划")
    val name = varchar("name", 500).comment("拟征集名称")
    val categoryId = varchar("category_id", 64).nullable().comment("文物分类")
    val era = varchar("era", 100).nullable().comment("年代")
    val texture = varchar("texture", 100).nullable().comment("质地")
    val proposedLevel = enumeration<EnumRelicLevel>("proposed_level").nullable().comment("拟定级别")
    val sourceType = enumeration<EnumSource>("source").nullable().comment("征集方式")
    val quantity = integer("quantity", checkConstraintName = shortCheckConstraintName("quantity")).default(1)
    val unit = varchar("unit", 16).default("件")
    val acquireDate = date("acquire_date").nullable().comment("取得日期")
    val dimensions = json(
        "dimensions",
        { JsonUtils.toJson(it) ?: "{}" },
        { JsonUtils.fromJson(it, DimensionVo::class.java) ?: DimensionVo() },
    ).nullable().comment("尺寸")
    val weight = varchar("weight", 64).nullable().comment("质量/重量")
    val completeness = enumeration<EnumCompleteness>("completeness").nullable().comment("完残程度")
    val completeStatus = varchar("complete_status", 1000).nullable().comment("完残状况")
    val holder = varchar("holder", 200).nullable().comment("持有人/卖方/捐赠人")
    val proposedPrice = long(
        "proposed_price",
        checkConstraintName = shortCheckConstraintName("proposed_price", typeCode = "l"),
    ).nullable().comment("拟征集价（分）")
    val appraisedValue = long(
        "appraised_value",
        checkConstraintName = shortCheckConstraintName("appraised_value", typeCode = "l"),
    ).nullable().comment("评估价（分）")
    val reason = text("reason").nullable().comment("征集理由/价值论证")
    val images = text("images").nullable().comment("影像 fileId")
    val expertOpinionAttachmentIds = text("expert_opinion_attachment_ids").nullable().comment("专家鉴定意见表附件")
    val approvalAttachmentIds = text("approval_attachment_ids").nullable().comment("征集审批表/清单附件")
    val contractAttachmentIds = text("contract_attachment_ids").nullable().comment("征集合同附件")
    val invoiceAttachmentIds = text("invoice_attachment_ids").nullable().comment("发票附件")
    val designatedReceptionId = varchar("designated_reception_id",64).nullable().index(shortIndexName("designated_reception_id"))
    val collectionId = varchar("collection_id", 64).nullable().comment("登账后生成的藏品 ID")
}

class AcquisitionDao(id: EntityID<String>) : FlowUIDEntity(id, Acquisitions) {
    companion object : FlowUIDEntityClass<AcquisitionDao>(Acquisitions)

    var orgId by Acquisitions.orgId
    var planId by Acquisitions.planId
    var name by Acquisitions.name
    var categoryId by Acquisitions.categoryId
    var era by Acquisitions.era
    var texture by Acquisitions.texture
    var proposedLevel by Acquisitions.proposedLevel
    var source by Acquisitions.sourceType
    var quantity by Acquisitions.quantity
    var unit by Acquisitions.unit
    var acquireDate by Acquisitions.acquireDate
    var dimensions by Acquisitions.dimensions
    var weight by Acquisitions.weight
    var completeness by Acquisitions.completeness
    var completeStatus by Acquisitions.completeStatus
    var holder by Acquisitions.holder
    var proposedPrice by Acquisitions.proposedPrice
    var appraisedValue by Acquisitions.appraisedValue
    var reason by Acquisitions.reason
    var images by Acquisitions.images
    var expertOpinionAttachmentIds by Acquisitions.expertOpinionAttachmentIds
    var approvalAttachmentIds by Acquisitions.approvalAttachmentIds
    var contractAttachmentIds by Acquisitions.contractAttachmentIds
    var invoiceAttachmentIds by Acquisitions.invoiceAttachmentIds
    var designatedReceptionId by Acquisitions.designatedReceptionId
    var collectionId by Acquisitions.collectionId

    fun toModel() = AcquisitionVo(
        id = id.value,
        orgId = orgId,
        planId = planId,
        name = name,
        categoryId = categoryId,
        era = era,
        texture = texture,
        proposedLevel = proposedLevel,
        source = source,
        quantity = quantity,
        unit = unit,
        acquireDate = acquireDate,
        dimensions = dimensions,
        weight = weight,
        completeness = completeness,
        completeStatus = completeStatus,
        holder = holder,
        proposedPrice = proposedPrice,
        appraisedValue = appraisedValue,
        reason = reason,
        images = images,
        expertOpinionAttachmentIds = expertOpinionAttachmentIds,
        approvalAttachmentIds = approvalAttachmentIds,
        contractAttachmentIds = contractAttachmentIds,
        invoiceAttachmentIds = invoiceAttachmentIds,
        designatedReceptionId = designatedReceptionId,
        collectionId = collectionId,
        flowStatus = flowStatus,
        flowInstanceId = flowInstanceId,
        createTime = createTime,
        createBy = createBy,
    )
}

data class AcquisitionVo(
    val id: String? = null,
    val orgId: String? = null,
    val planId: String? = null,
    var planTitle: String? = null,
    @field:NotBlank(groups = [jakarta.validation.groups.Default::class, io.hizhengfu.cloud.collection.domain.AcquisitionDraftValidation::class]) val name: String? = null,
    val categoryId: String? = null,
    val era: String? = null,
    val texture: String? = null,
    val proposedLevel: EnumRelicLevel? = null,
    @field:NotNull val source: EnumSource? = null,
    @field:NotNull @field:Positive(groups = [jakarta.validation.groups.Default::class, io.hizhengfu.cloud.collection.domain.AcquisitionDraftValidation::class]) val quantity: Int? = null,
    @field:NotBlank val unit: String? = null,
    @field:NotNull val acquireDate: LocalDate? = null,
    val dimensions: DimensionVo? = null,
    val weight: String? = null,
    val completeness: EnumCompleteness? = null,
    val completeStatus: String? = null,
    @field:NotBlank val holder: String? = null,
    val proposedPrice: Long? = null,
    val appraisedValue: Long? = null,
    @field:NotBlank val reason: String? = null,
    val images: String? = null,
    val expertOpinionAttachmentIds: String? = null,
    val approvalAttachmentIds: String? = null,
    val contractAttachmentIds: String? = null,
    val invoiceAttachmentIds: String? = null,
    val designatedReceptionId: String? = null,
    val collectionId: String? = null,
    val collectionTotalRegNo: String? = null,
    val collectionName: String? = null,
    val flowStatus: EnumFlowStatus? = null,
    val flowInstanceId: String? = null,
    val createTime: LocalDateTime? = null,
    val createBy: String? = null,
    /** 列表定位关联原馆依据的异常，不替代原馆快照及原件核验。 */
    var collectionEvidenceIssue: String? = null,
)
