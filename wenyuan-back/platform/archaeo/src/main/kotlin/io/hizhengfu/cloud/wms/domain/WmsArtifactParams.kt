package io.hizhengfu.cloud.wms.domain

import java.math.BigDecimal


/**
 * 文物扩展数据参数 — 与 WmsArtifactExtendVo 字段对齐
 * */
data class WmsArtifactParams(
    val artifactId: String,
    val timePeriod: String? = null,
    val culturalAttribute: String? = null,
    val purpose: String? = null,
    val purposeL1: String? = null,
    val purposeL2: String? = null,
    val purposeL3: String? = null,
    val shape: String? = null,
    val decoration: String? = null,
    val craftingTechnique: String? = null,
    val lineDiagram: String? = null,
    val artifactType: String? = null,
    val dimensions: String? = null,
    val weight: BigDecimal? = null,

    val potteryQuality: String? = null,
    val potteryColor: String? = null,
    val kilnSite: String? = null,
    val kilnSystem: String? = null,
    val glazeColor: String? = null,
    val markText: String? = null,
    val lithology: String? = null,
    val inscription: String? = null,
    val species: String? = null,
    val artifactStyle: String? = null,
    val artifactSubStyle: String? = null,
    val artifactStage: String? = null,
    val phaseName: String? = null,
    val sectionName: String? = null,
    val extra: String? = null,
) {
    /** 自动从 purpose 推导三级。purpose 形如 "生活用具/炊具/鼎" */
    fun normalized(): WmsArtifactParams {
        if (purpose.isNullOrBlank()) return this
        val parts = purpose.split("/")
        return copy(
            purposeL1 = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: purposeL1,
            purposeL2 = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: purposeL2,
            purposeL3 = parts.getOrNull(2)?.takeIf { it.isNotBlank() } ?: purposeL3,
        )
    }
}
