package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.CollectionCatalogDao
import io.hizhengfu.cloud.collection.dao.CollectionCatalogVersionDao
import io.hizhengfu.cloud.collection.dao.CollectionCatalogVersions
import io.hizhengfu.cloud.collection.dao.CollectionDao
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq

/** 固定页面实际核对过的工作副本、提交快照与藏品身份，不代替当前授权或原件签署。 */
internal object CollectionCatalogConfirmation {
    fun hash(catalog: CollectionCatalogDao, collection: CollectionDao): String {
        val version = (catalog.submittedVersion ?: catalog.version.takeIf { it > 0 })?.let { number ->
            CollectionCatalogVersionDao.find {
                (CollectionCatalogVersions.catalogId eq catalog.id.value) and (CollectionCatalogVersions.version eq number)
            }.singleOrNull()?.let { mapOf("orgId" to it.orgId, "snapshot" to it.toModel()) }
        }
        return CollectionImportConfirmation.hash(linkedMapOf(
            "schema" to "collection-catalog/v1", "orgId" to collection.orgId, "collectionId" to collection.id.value,
            "totalRegNo" to collection.totalRegNo, "lifeStatus" to collection.lifeStatus,
            "registeredAt" to collection.registeredAt, "historicalConfirmedAt" to collection.historicalConfirmedAt, "published" to collection.published,
            "mainCatalogStatus" to collection.catalogStatus, "catalogId" to catalog.id.value,
            "catalogOrgId" to catalog.orgId, "status" to catalog.catalogStatus, "version" to catalog.version,
            "submittedVersion" to catalog.submittedVersion, "submittedBy" to catalog.submittedBy,
            "submittedAt" to catalog.submittedAt, "auditBy" to catalog.auditBy, "auditTime" to catalog.auditTime,
            "auditOpinion" to catalog.auditOpinion, "content" to catalog.content, "submittedSnapshot" to version,
        ))
    }
    fun requireSame(catalog: CollectionCatalogDao, collection: CollectionDao, expected: String?) {
        if (expected != null && (expected.isBlank() || hash(catalog, collection) != expected))
            throw BadRequestException("编目对象、版本或内容已变化，请重新读取并核对后办理")
    }
}
