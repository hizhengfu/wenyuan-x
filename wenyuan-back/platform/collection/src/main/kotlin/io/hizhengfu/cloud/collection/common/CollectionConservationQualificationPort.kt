package io.hizhengfu.cloud.collection.common

import java.time.LocalDate

data class ConservationQualificationEvidence(
    val unitId: String,
    val unitName: String,
    val certificateId: String,
    val certificateNo: String,
    val issuer: String,
    val scope: String,
    val issuedOn: LocalDate,
    val expiresOn: LocalDate?,
    val fileId: String,
)

/** 复制拓印复用修复机构及真实资质；核心藏品模块不反向依赖修复模块。 */
interface CollectionConservationQualificationPort {
    fun units(orgId: String): List<Map<String,String>>
    fun requireQualified(orgId: String, unitId: String, at: LocalDate): ConservationQualificationEvidence
}
