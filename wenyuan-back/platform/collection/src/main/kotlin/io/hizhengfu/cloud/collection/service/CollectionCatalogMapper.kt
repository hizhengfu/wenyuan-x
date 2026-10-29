package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.CollectionDao
import io.hizhengfu.cloud.collection.dao.CollectionVo
import io.hizhengfu.cloud.collection.domain.CollectionCatalogContentVo

/** 台账与编目工作副本之间的唯一字段映射，避免录入、审核和卡片各自维护一套规则。 */
object CollectionCatalogMapper {
    fun fromCollection(vo: CollectionVo): CollectionCatalogContentVo = CollectionCatalogContentVo(
        classificationNo = vo.classNo,
        originalNo = vo.auxNo,
        totalName = vo.name,
        formalName = vo.name,
        originalName = vo.originalName,
        categoryId = vo.categoryId,
        era = vo.era,
        region = vo.region,
        country = vo.country,
        culture = vo.culture,
        textureCategory1 = vo.textureCategory1,
        textureCategory2 = vo.textureCategory2,
        texture = vo.texture,
        color = vo.color,
        nature = vo.nature,
        level = vo.level,
        quantity = vo.quantity,
        unit = vo.unit,
        actualQuantity = vo.actualQuantity,
        actualQuantityUnit = vo.actualQuantityUnit,
        dimensions = vo.dimensions,
        dimensionUnit = vo.dimensionUnit,
        weightRange = vo.weightRange,
        weight = vo.weight,
        weightUnit = vo.weightUnit,
        volume = vo.volume,
        volumeUnit = vo.volumeUnit,
        completeness = vo.completeness,
        completeStatus = vo.completeStatus,
        preserveState = vo.preserveState,
        source = vo.source,
        sourceNo = vo.sourceNo,
        sourceDesc = vo.sourceDesc,
        acquireDate = vo.acquireDate,
        entryDate = vo.entryDate,
        receiptCompletedAt = vo.receiptCompletedAt,
        registrationDetails = vo.registrationDetails,
        acquireDateRange = vo.acquireDateRange,
        acquireYear = vo.acquireYear,
        feature = vo.feature,
        inscription = vo.inscription,
        author = vo.author,
        edition = vo.edition,
        volumeNo = vo.volumeNo,
        valuation = vo.valuation,
        coverImage = vo.coverImage,
        warehouseId = vo.warehouseId,
        remark = vo.remark,
    )

    /** 只用于核对旧审批基线的兼容表示；不得据此回填主档或作为规范输出。 */
    internal fun legacyUnknownDefaults(vo: CollectionVo): CollectionCatalogContentVo = fromCollection(vo).copy(
        actualQuantity = vo.actualQuantity ?: vo.quantity,
        actualQuantityUnit = vo.actualQuantityUnit ?: vo.unit,
        dimensionUnit = vo.dimensionUnit ?: "cm",
    )

    /** 原系统曾隐式产生数量和cm默认值；仅这三个确切旧默认值可兼容，真实新事实仍参与比较。 */
    fun matchesSnapshot(snapshot: CollectionCatalogContentVo?, vo: CollectionVo): Boolean =
        snapshot != null && (snapshot == fromCollection(vo) || snapshot == legacyUnknownDefaults(vo))

    /** 台账修改时只同步双方共有字段，保留编目专属的题跋、附件说明和附属物等内容。 */
    fun mergeCollection(content: CollectionCatalogContentVo, vo: CollectionVo): CollectionCatalogContentVo =
        content.copy(
            classificationNo = vo.classNo,
            originalNo = vo.auxNo,
            totalName = vo.name,
            formalName = vo.name,
            originalName = vo.originalName,
            categoryId = vo.categoryId,
            era = vo.era,
            region = vo.region,
            country = vo.country,
            culture = vo.culture,
            textureCategory1 = vo.textureCategory1,
            textureCategory2 = vo.textureCategory2,
            texture = vo.texture,
            color = vo.color,
            nature = vo.nature,
            level = vo.level,
            quantity = vo.quantity,
            unit = vo.unit,
            actualQuantity = vo.actualQuantity,
            actualQuantityUnit = vo.actualQuantityUnit,
            dimensions = vo.dimensions,
            dimensionUnit = vo.dimensionUnit,
            weightRange = vo.weightRange,
            weight = vo.weight,
            weightUnit = vo.weightUnit,
            volume = vo.volume,
            volumeUnit = vo.volumeUnit,
            completeness = vo.completeness,
            completeStatus = vo.completeStatus,
            preserveState = vo.preserveState,
            source = vo.source,
            sourceNo = vo.sourceNo,
            sourceDesc = vo.sourceDesc,
            acquireDate = vo.acquireDate,
            entryDate = vo.entryDate,
            receiptCompletedAt = vo.receiptCompletedAt,
            registrationDetails = vo.registrationDetails,
            acquireDateRange = vo.acquireDateRange,
            acquireYear = vo.acquireYear,
            feature = vo.feature,
            inscription = vo.inscription,
            author = vo.author,
            edition = vo.edition,
            volumeNo = vo.volumeNo,
            valuation = vo.valuation,
            coverImage = vo.coverImage,
            warehouseId = vo.warehouseId,
            remark = vo.remark,
        )

    /** 审核通过后物化到主档的公共字段；总登记号仍不允许被编目修改。 */
    fun applyApproved(node: CollectionDao, content: CollectionCatalogContentVo) {
        content.formalName?.trim()?.takeIf { it.isNotBlank() }
            ?.let { node.name = it }
            ?: content.totalName?.trim()?.takeIf { it.isNotBlank() }?.let { node.name = it }
        node.classNo = content.classificationNo
        node.auxNo = content.originalNo
        node.originalName = content.originalName
        node.categoryId = content.categoryId
        node.era = content.era
        node.region = content.region
        node.country = content.country
        node.culture = content.culture
        node.textureCategory1 = content.textureCategory1
        node.textureCategory2 = content.textureCategory2
        node.texture = content.texture
        node.color = content.color
        node.nature = content.nature
        content.level?.let { node.level = it }
        content.quantity?.let { node.quantity = it }
        node.actualQuantity = content.actualQuantity
        node.actualQuantityUnit = content.actualQuantityUnit
        content.unit?.let { node.unit = it }
        node.dimensions = content.dimensions
        node.dimensionUnit = content.dimensionUnit
        node.entryDate = content.entryDate
        node.receiptCompletedAt = content.receiptCompletedAt
        node.registrationDetails = content.registrationDetails
        node.weightRange = content.weightRange
        node.weight = content.weight
        node.weightUnit = content.weightUnit
        node.volume = content.volume
        node.volumeUnit = content.volumeUnit
        node.completeness = content.completeness
        node.completeStatus = content.completeStatus
        node.preserveState = content.preserveState
        node.source = content.source
        node.sourceNo = content.sourceNo
        node.sourceDesc = content.sourceDesc
        node.acquireDate = content.acquireDate
        node.acquireDateRange = content.acquireDateRange
        node.acquireYear = content.acquireYear
        node.feature = content.feature
        node.inscription = content.inscription
        node.author = content.author
        node.edition = content.edition
        node.volumeNo = content.volumeNo
        node.valuation = content.valuation
        node.coverImage = content.coverImage
        node.remark = content.remark
    }
}
