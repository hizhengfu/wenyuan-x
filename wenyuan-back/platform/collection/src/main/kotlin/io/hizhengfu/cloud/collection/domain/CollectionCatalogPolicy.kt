package io.hizhengfu.cloud.collection.domain

import io.hizhengfu.cloud.common.exception.BadRequestException

object CollectionCatalogPolicy {
    private data class RequiredField(
        val label: String,
        val present: (CollectionCatalogContentVo) -> Boolean,
    )

    private fun text(value: String?): Boolean = !value.isNullOrBlank()

    private val legacyFields = listOf(
        RequiredField("总品名") { text(it.totalName) || text(it.formalName) },
        RequiredField("正式定名") { text(it.formalName) },
        RequiredField("文物分类") { text(it.categoryId) },
        RequiredField("年代") { text(it.era) },
        RequiredField("质地") { text(it.texture) },
        RequiredField("藏品级别") { it.level != null },
        RequiredField("原登记数量") { (it.actualQuantity ?: it.quantity ?: 0) > 0 },
        RequiredField("原数量单位") { text(it.actualQuantityUnit) || text(it.unit) },
        RequiredField("来源方式") { it.source != null },
        RequiredField("原取得时间") { it.acquireDate != null || text(it.acquireDateRange) },
        RequiredField("完残程度") { it.completeness != null },
    )
    private val requiredFields = legacyFields.filterNot { it.label.startsWith("原") } + listOf(
        RequiredField("藏品性质") { it.nature != null },
        RequiredField("年代类型") { it.registrationDetails?.eraMeaning != null },
        RequiredField("年代表示方式") { it.registrationDetails?.eraNotation != null },
        RequiredField("质地类别") { text(it.textureCategory1) },
        RequiredField("质地细类") { text(it.textureCategory2) },
        RequiredField("分类号") { text(it.classificationNo) },
        RequiredField("登记件套数") { (it.quantity ?: 0) > 0 },
        RequiredField("登记数量单位") { text(it.unit) },
        RequiredField("实际数量") { (it.actualQuantity ?: 0) > 0 },
        RequiredField("实际数量单位") { text(it.actualQuantityUnit) && it.actualQuantityUnit?.trim() != "套" },
        RequiredField("取得日期或经核实的时间范围") { it.acquireDate != null || text(it.acquireDateRange) },
        RequiredField("实收入馆日期") { it.entryDate != null },
        RequiredField("尺寸或无法测量原因") { CollectionMeasurementPolicy.dimensions(it.dimensions).isNotEmpty() || text(it.registrationDetails?.dimensionUnknownReason) },
        RequiredField("质量或无法测量原因") { text(it.weight) || text(it.registrationDetails?.weightUnknownReason) },
        RequiredField("保存状态") { it.preserveState != null },
        RequiredField("完残状况描述") { text(it.completeStatus) },
        RequiredField("基本特征") { text(it.feature) },
        RequiredField("正面照片及确认") { text(it.coverImage) && it.registrationDetails?.coverIsFrontView == true },
        RequiredField("存放位置") { text(it.warehouseId) },
    )

    fun completeness(content: CollectionCatalogContentVo, historical: Boolean = false): CatalogCompletenessVo {
        // 原账允许留空的描述字段不得在首次补录时反过来要求修改已经锁定的正式主档。
        val fields = (if (historical) legacyFields.filterNot { it.label in historicalOptionalFields } else requiredFields).filterNot {
            (content.nature == EnumCollectionNature.OTHER && it.label in relicFields) ||
                (content.unit?.trim() != "套" && content.registrationDetails?.parts.orEmpty().size < 2 && it.label in actualQuantityFields)
        }
        val missing = fields.filterNot { it.present(content) }.map { it.label }
        val invalid = if (historical) CollectionDescriptionPolicy.natureIssues(content) else CollectionMeasurementPolicy.issues(content) + CollectionDescriptionPolicy.issues(content)
        val completed = (fields.size - missing.size - invalid.size).coerceAtLeast(0)
        return CatalogCompletenessVo(
            percent = completed * 100 / fields.size,
            missingFields = missing + invalid,
            complete = missing.isEmpty() && invalid.isEmpty(),
        )
    }

    private val relicFields = setOf("藏品级别", "年代类型", "年代表示方式", "质地类别", "质地细类")
    private val actualQuantityFields = setOf("实际数量", "实际数量单位")
    private val historicalOptionalFields = setOf("年代", "质地", "完残程度")

    fun requireComplete(content: CollectionCatalogContentVo, historical: Boolean = false) {
        val result = completeness(content, historical)
        if (!result.complete) throw BadRequestException("编目信息不完整：${result.missingFields.joinToString("、")}")
    }

    fun requireEditable(status: EnumCatalogStatus) {
        if (status == EnumCatalogStatus.SUBMITTED || status == EnumCatalogStatus.PENDING) {
            throw BadRequestException("编目已提交审核，不能继续修改")
        }
        if (status == EnumCatalogStatus.APPROVED) {
            throw BadRequestException("编目已审核通过，请通过信息变更申请修改")
        }
    }

    fun requireSubmittable(status: EnumCatalogStatus, content: CollectionCatalogContentVo, historical: Boolean = false) {
        if (status != EnumCatalogStatus.DRAFT && status != EnumCatalogStatus.REJECTED) {
            throw BadRequestException("当前编目状态[$status]不能提交审核")
        }
        requireComplete(content, historical)
    }

    fun requireReviewable(status: EnumCatalogStatus) {
        if (status != EnumCatalogStatus.SUBMITTED && status != EnumCatalogStatus.PENDING) {
            throw BadRequestException("仅已提交的编目可以审核")
        }
    }

    fun requireDecision(status: EnumCatalogStatus, opinion: String?): String? = when (status) {
        EnumCatalogStatus.APPROVED -> opinion?.trim()?.takeIf { it.isNotBlank() }
        EnumCatalogStatus.REJECTED -> opinion?.trim()?.takeIf { it.isNotBlank() }
            ?: throw BadRequestException("驳回时必须填写审核意见")
        else -> throw BadRequestException("请选择通过或驳回")
    }
}
