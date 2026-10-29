package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortCheckConstraintName
import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import org.jetbrains.exposed.v1.json.json
import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntity
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntityClass
import io.hizhengfu.cloud.collection.common.OrgAuditUIDTable
import io.hizhengfu.cloud.collection.domain.EnumCollectionNature
import io.hizhengfu.cloud.collection.domain.DimensionVo
import io.hizhengfu.cloud.collection.domain.EnumCatalogStatus
import io.hizhengfu.cloud.collection.domain.EnumCollectionStatus
import io.hizhengfu.cloud.collection.domain.EnumCompleteness
import io.hizhengfu.cloud.collection.domain.EnumPreserve
import io.hizhengfu.cloud.collection.domain.EnumRelicLevel
import io.hizhengfu.cloud.collection.domain.EnumSource
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.datetime
import io.hizhengfu.cloud.collection.domain.CollectionRegistrationDetailsVo
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 藏品档案主表（总登记账 + 分类账 + 一物一档）。
 * 红线：总登记号按馆唯一、不可重用；主表不提供物理删除；关键字段更正走留痕。
 */
object Collections : OrgAuditUIDTable("col_collection"), io.hizhengfu.cloud.autoconfigure.exposed.StableBusinessAuditTable {
    val totalRegNo = varchar("total_reg_no", 64).comment("总登记号")
    val classNo = varchar("class_no", 64).nullable().comment("分类号")
    val auxNo = varchar("aux_no", 64).nullable().comment("辅助编号/原藏号")

    val name = varchar("name", 500).comment("名称")
    val originalName = varchar("original_name", 500).nullable().comment("原名")
    val categoryId = varchar("category_id", 64).nullable().index(shortIndexName("category_id"))
        .comment("文物分类")
    val era = varchar("era", 100).nullable().comment("年代/断代")
    val texture = varchar("texture", 100).nullable().comment("质地")
    val textureCategory1 = varchar("texture_category_1", 100).nullable().comment("质地类别一")
    val textureCategory2 = varchar("texture_category_2", 100).nullable().comment("质地类别二")
    val nature = enumeration<EnumCollectionNature>("collection_nature").nullable().comment("文物/第61条科学价值古脊椎动物或古人类化石/其他藏品；历史记录须据原件核实")
    val level = enumeration<EnumRelicLevel>("level").default(EnumRelicLevel.UNRATED).comment("藏品级别")
    val quantity = integer("quantity", checkConstraintName = shortCheckConstraintName("quantity"))
        .default(1).comment("数量")
    val unit = varchar("unit", 16).default("件").comment("计量单位")
    val actualQuantity = integer("actual_quantity", checkConstraintName = shortCheckConstraintName("actual_quantity")).nullable().comment("整件套的实际件数；历史未核实为空")
    val actualQuantityUnit = varchar("actual_quantity_unit", 16).nullable().comment("实际件数单位")
    val dimensionUnit = varchar("dimension_unit", 16).nullable().comment("尺寸原始计量单位；不推断历史单位")
    val entryDate = date("entry_date").nullable().comment("本件套实际接收入馆日期")
    val receiptCompletedAt = datetime("receipt_completed_at").nullable().comment("实际完成核收时间")
    val registrationDetails = json("registration_details",
        { JsonUtils.toJson(it) ?: "{}" },
        { JsonUtils.fromJson(it, CollectionRegistrationDetailsVo::class.java) ?: CollectionRegistrationDetailsVo() }
    ).nullable().comment("登录规范测量说明及流传考古著录细目")
    val sourceType = enumeration<EnumSource>("source").nullable().comment("来源方式")
    val sourceDesc = varchar("source_desc", 500).nullable().comment("来源说明")
    val acquireDate = date("acquire_date").nullable().comment("取得日期；历史取得日期口径须据原件核实")
    val completeness = enumeration<EnumCompleteness>("completeness").nullable().comment("完残程度")
    val completeStatus = varchar("complete_status", 1000).nullable().comment("完残状况")

    val dimensions = json("dimensions",
        { JsonUtils.toJson(it) ?: "{}" },
        { JsonUtils.fromJson(it, DimensionVo::class.java) ?: DimensionVo() }
    ).nullable().comment("尺寸")
    val weight = varchar("weight", 64).nullable().comment("质量/重量")
    val weightRange = varchar("weight_range", 64).nullable().comment("质量范围")
    val weightUnit = varchar("weight_unit", 16).nullable().comment("质量单位")
    val region = varchar("region", 100).nullable().comment("地域")
    val volume = varchar("volume", 64).nullable().comment("容积")
    val volumeUnit = varchar("volume_unit", 16).nullable().comment("容积单位")
    val color = varchar("color", 100).nullable().comment("色泽")
    val sourceNo = varchar("source_no", 64).nullable().comment("来源号")
    val country = varchar("country", 64).nullable().comment("国别")
    val culture = varchar("culture", 100).nullable().comment("人文类型")
    val rfidTag = varchar("rfid_tag", 64).nullable().comment("RFID 标签号（馆内唯一）")
    val entryVoucherNo = varchar("entry_voucher_no", 64).nullable().comment("入馆凭证号")
    val deregVoucherNo = varchar("dereg_voucher_no", 128).nullable().comment("注销凭证号/批文号")
    val inscription = text("inscription").nullable().comment("铭文款识")
    val feature = text("feature").nullable().comment("特征描述")
    val preserveState = enumeration<EnumPreserve>("preserve_state").nullable().comment("保存状态")
    val acquireDateRange = varchar("acquire_date_range", 128).nullable().comment("取得时间范围")
    val acquireYear = varchar("acquire_year", 16).nullable().comment("取得年度")
    val author = varchar("author", 255).nullable().comment("著者")
    val edition = varchar("edition", 100).nullable().comment("版本")
    val volumeNo = varchar("volume_no", 100).nullable().comment("存卷")
    val valuation = long(
        "valuation",
        checkConstraintName = shortCheckConstraintName("valuation", typeCode = "l"),
    ).nullable().comment("估价（分）")
    val coverImage = varchar("cover_image", 512).nullable().comment("封面影像")

    val warehouseId = varchar("warehouse_id", 64).nullable().comment("当前库位")
    val catalogStatus = enumeration<EnumCatalogStatus>("catalog_status")
        .default(EnumCatalogStatus.PENDING).comment("编目审核状态")
    val catalogAuditOpinion = varchar("catalog_audit_opinion", 1000).nullable().comment("编目审核意见")
    val catalogAuditBy = varchar("catalog_audit_by", 64).nullable().comment("编目审核人")
    val catalogAuditTime = datetime("catalog_audit_time").nullable().comment("编目审核时间")
    val lifeStatus = enumeration<EnumCollectionStatus>("life_status")
        .default(EnumCollectionStatus.PENDING).comment("藏品业务状态")
    val inventoryHold = bool("inventory_hold").default(false).comment("清点异常未核实处理，不得继续调取利用")
    val registeredAt = datetime("registered_at").nullable().comment("总账原登记时间；历史未载时留空")
    val historicalConfirmedAt = datetime("historical_confirmed_at").nullable().comment("历史登记独立复核通过时间，不是原登记日期")
    fun isRegistered() = registeredAt.isNotNull() or historicalConfirmedAt.isNotNull()
    fun isUnregistered() = registeredAt.isNull() and historicalConfirmedAt.isNull()
    val remark = varchar("remark", 2000).nullable()
    val published = bool("published").default(false).comment("是否曾启用CMS供稿，实际公开及当前内容须另核查")
    val publishedAt = datetime("published_at").nullable()

    init {
        uniqueIndex(shortIndexName("org_id_total_reg_no", unique = true), orgId, totalRegNo)
        uniqueIndex(shortIndexName("org_id_rfid_tag", unique = true), orgId, rfidTag)
        index(shortIndexName("org_id_level_life_status"), false, orgId, level, lifeStatus)
        index(shortIndexName("org_id_catalog_status"), false, orgId, catalogStatus)
        index(shortIndexName("org_id_category_id"), false, orgId, categoryId)
    }
}

class CollectionDao(id: EntityID<String>) : OrgAuditUIDEntity(id, Collections) {
    companion object : OrgAuditUIDEntityClass<CollectionDao>(Collections)

    var totalRegNo by Collections.totalRegNo
    var classNo by Collections.classNo
    var auxNo by Collections.auxNo
    var name by Collections.name
    var originalName by Collections.originalName
    var categoryId by Collections.categoryId
    var era by Collections.era
    var texture by Collections.texture
    var textureCategory1 by Collections.textureCategory1
    var textureCategory2 by Collections.textureCategory2
    var nature by Collections.nature
    var level by Collections.level
    var quantity by Collections.quantity
    var unit by Collections.unit
    var actualQuantity by Collections.actualQuantity
    var actualQuantityUnit by Collections.actualQuantityUnit
    var dimensionUnit by Collections.dimensionUnit
    var entryDate by Collections.entryDate
    var receiptCompletedAt by Collections.receiptCompletedAt
    var registrationDetails by Collections.registrationDetails
    var source by Collections.sourceType
    var sourceDesc by Collections.sourceDesc
    var acquireDate by Collections.acquireDate
    var completeness by Collections.completeness
    var completeStatus by Collections.completeStatus
    var dimensions by Collections.dimensions
    var weight by Collections.weight
    var weightRange by Collections.weightRange
    var weightUnit by Collections.weightUnit
    var region by Collections.region
    var volume by Collections.volume
    var volumeUnit by Collections.volumeUnit
    var color by Collections.color
    var sourceNo by Collections.sourceNo
    var country by Collections.country
    var culture by Collections.culture
    var rfidTag by Collections.rfidTag
    var entryVoucherNo by Collections.entryVoucherNo
    var deregVoucherNo by Collections.deregVoucherNo
    var inscription by Collections.inscription
    var feature by Collections.feature
    var preserveState by Collections.preserveState
    var acquireDateRange by Collections.acquireDateRange
    var acquireYear by Collections.acquireYear
    var author by Collections.author
    var edition by Collections.edition
    var volumeNo by Collections.volumeNo
    var valuation by Collections.valuation
    var coverImage by Collections.coverImage
    var warehouseId by Collections.warehouseId
    var catalogStatus by Collections.catalogStatus
    var catalogAuditOpinion by Collections.catalogAuditOpinion
    var catalogAuditBy by Collections.catalogAuditBy
    var catalogAuditTime by Collections.catalogAuditTime
    var lifeStatus by Collections.lifeStatus
    var inventoryHold by Collections.inventoryHold
    var registeredAt by Collections.registeredAt
    var historicalConfirmedAt by Collections.historicalConfirmedAt
    val registered: Boolean get() = registeredAt != null || historicalConfirmedAt != null
    var remark by Collections.remark
    var published by Collections.published
    var publishedAt by Collections.publishedAt

    fun toModel() = CollectionVo(
        id = id.value,
        orgId = orgId,
        totalRegNo = totalRegNo,
        classNo = classNo,
        auxNo = auxNo,
        name = name,
        originalName = originalName,
        categoryId = categoryId,
        era = era,
        texture = texture,
        textureCategory1 = textureCategory1,
        textureCategory2 = textureCategory2,
        nature = nature,
        level = level,
        quantity = quantity,
        unit = unit,
        actualQuantity = actualQuantity,
        actualQuantityUnit = actualQuantityUnit,
        dimensionUnit = dimensionUnit,
        entryDate = entryDate,
        receiptCompletedAt = receiptCompletedAt,
        registrationDetails = registrationDetails,
        source = source,
        sourceDesc = sourceDesc,
        acquireDate = acquireDate,
        completeness = completeness,
        completeStatus = completeStatus,
        dimensions = dimensions,
        weight = weight,
        weightRange = weightRange,
        weightUnit = weightUnit,
        region = region,
        volume = volume,
        volumeUnit = volumeUnit,
        color = color,
        sourceNo = sourceNo,
        country = country,
        culture = culture,
        rfidTag = rfidTag,
        entryVoucherNo = entryVoucherNo,
        deregVoucherNo = deregVoucherNo,
        inscription = inscription,
        feature = feature,
        preserveState = preserveState,
        acquireDateRange = acquireDateRange,
        acquireYear = acquireYear,
        author = author,
        edition = edition,
        volumeNo = volumeNo,
        valuation = valuation,
        coverImage = coverImage,
        warehouseId = warehouseId,
        catalogStatus = catalogStatus,
        catalogAuditOpinion = catalogAuditOpinion,
        catalogAuditBy = catalogAuditBy,
        catalogAuditTime = catalogAuditTime,
        lifeStatus = lifeStatus,
        inventoryHold = inventoryHold,
        registeredAt = registeredAt, historicalConfirmedAt = historicalConfirmedAt,
        remark = remark,
        published = published,
        publishedAt = publishedAt,
        createTime = createTime,
        createBy = createBy,
    )
}

data class CollectionVo(
    val id: String? = null,
    val orgId: String? = null,
    val totalRegNo: String? = null,
    val classNo: String? = null,
    val auxNo: String? = null,
    @field:NotBlank val name: String? = null,
    val originalName: String? = null,
    @field:NotBlank val categoryId: String? = null,
    var categoryName: String? = null,
    val era: String? = null,
    val texture: String? = null,
    val textureCategory1: String? = null,
    val textureCategory2: String? = null,
    val nature: EnumCollectionNature? = null,
    @field:NotNull val level: EnumRelicLevel? = null,
    @field:NotNull @field:Positive val quantity: Int? = null,
    @field:NotBlank val unit: String? = null,
    @field:NotNull val source: EnumSource? = null,
    val sourceDesc: String? = null,
    val acquireDate: LocalDate? = null,
    val completeness: EnumCompleteness? = null,
    val completeStatus: String? = null,
    val dimensions: DimensionVo? = null,
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
    val rfidTag: String? = null,
    val entryVoucherNo: String? = null,
    val deregVoucherNo: String? = null,
    val inscription: String? = null,
    val feature: String? = null,
    val preserveState: EnumPreserve? = null,
    val acquireDateRange: String? = null,
    val acquireYear: String? = null,
    val author: String? = null,
    val edition: String? = null,
    val volumeNo: String? = null,
    val valuation: Long? = null,
    val coverImage: String? = null,
    val warehouseId: String? = null,
    var warehouseName: String? = null,
    val catalogStatus: EnumCatalogStatus? = null,
    val catalogAuditOpinion: String? = null,
    val catalogAuditBy: String? = null,
    val catalogAuditTime: LocalDateTime? = null,
    val lifeStatus: EnumCollectionStatus? = null,
    val inventoryHold: Boolean = false,
    val registeredAt: LocalDateTime? = null,
    val historicalConfirmedAt: LocalDateTime? = null,
    val remark: String? = null,
    val published: Boolean? = null,
    val publishedAt: LocalDateTime? = null,
    val createTime: LocalDateTime? = null,
    val createBy: String? = null,
    var previewImage: String? = null,
    var mediaCount: Long = 0,
    var attachmentCount: Long = 0,
    val actualQuantity: Int? = null,
    val actualQuantityUnit: String? = null,
    val dimensionUnit: String? = null,
    val entryDate: LocalDate? = null,
    val receiptCompletedAt: LocalDateTime? = null,
    val registrationDetails: CollectionRegistrationDetailsVo? = null,
) {
    val registered: Boolean get() = registeredAt != null || historicalConfirmedAt != null
}
