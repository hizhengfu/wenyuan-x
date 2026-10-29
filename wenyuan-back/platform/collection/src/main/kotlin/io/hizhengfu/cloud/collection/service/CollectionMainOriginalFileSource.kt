package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.or
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode

/** 主档当前文件；主记录及影像/附件子记录的关联均须保持同一馆别。 */
@Component
class CollectionMainOriginalFileSource : CollectionOriginalFileSource {
    companion object { const val BIZ = "COLLECTION_MAIN_FILES" }
    override val businessLabels = listOf(
        CollectionOriginalBusinessLabel(Collections, Collections.orgId, BIZ, listOf(Collections.totalRegNo, Collections.name)),
    )
    override val tables = listOf(
        CollectionOriginalFileTable(Collections, Collections.orgId, BIZ, listOf(Collections.coverImage)),
        CollectionOriginalFileTable(CollectionMedias, CollectionMedias.orgId, BIZ, listOf(CollectionMedias.fileId), owner = CollectionMedias.collectionId),
        CollectionOriginalFileTable(CollectionAttachments, CollectionAttachments.orgId, BIZ, listOf(CollectionAttachments.fileId), owner = CollectionAttachments.collectionId),
        CollectionOriginalFileTable(CollectionCatalogs, CollectionCatalogs.orgId, BIZ, json = listOf(CollectionCatalogs.content), owner = CollectionCatalogs.collectionId),
        CollectionOriginalFileTable(CollectionCatalogVersions, CollectionCatalogVersions.orgId, BIZ, json = listOf(CollectionCatalogVersions.content), owner = CollectionCatalogVersions.collectionId),
    )

    override fun references(businessType: String?, businessId: String?): List<CollectionOriginalFileReference> {
        val references = tables.filter { businessType == null || it.businessType == businessType }.flatMap { table ->
            table.references(businessId).map { it to (table.table == CollectionCatalogVersions) }
        }
        val parents = references.map { it.first.businessId }.distinct().chunked(200)
            .flatMap { ids -> CollectionDao.find { Collections.id inList ids }.toList() }.associateBy { it.id.value }
        val versions = references.filter { it.second }.map { it.first.recordId }.distinct().chunked(200)
            .flatMap { ids -> CollectionCatalogVersionDao.find { CollectionCatalogVersions.id inList ids }.toList() }
            .associateBy { it.id.value }
        val catalogs = versions.values.map { it.catalogId }.distinct().chunked(200)
            .flatMap { ids -> CollectionCatalogDao.find { CollectionCatalogs.id inList ids }.toList() }
            .associateBy { it.id.value }
        val retainedKeys = references.filter { (ref, historical) ->
            val parent = parents[ref.businessId]
            val version = if (historical) versions[ref.recordId] else null
            val catalog = version?.let { catalogs[it.catalogId] }
            parent != null && parent.orgId != ref.orgId && version != null &&
                version.collectionId == ref.businessId && version.orgId == ref.orgId &&
                catalog?.collectionId == ref.businessId && catalog.orgId == parent.orgId
        }.map { (ref, _) -> ref.businessId to ref.orgId }.distinct()
        val retained = retainedKeys.chunked(200).flatMap { keys ->
            val condition = keys.map { (id, org) ->
                (CollectionRetainedLedgers.collectionId eq id) and (CollectionRetainedLedgers.orgId eq org)
            }.reduce { left, right -> left or right }
            CollectionRetainedLedgerDao.find(condition).toList()
        }.groupBy { it.collectionId to it.orgId }
        val archives = retained.values.flatten().map { it.archiveId }.distinct().chunked(200)
            .flatMap { ids -> CollectionPermanentArchiveDao.find { CollectionPermanentArchives.id inList ids }.toList() }
            .associateBy { it.id.value }

        // 只复用本次扫描的内容；授权及下一次来源读取仍重新查询，不跨请求缓存 DAO 或文件身份。
        val expectedVersions = mutableMapOf<String, JsonNode>()
        val parsedArchives = mutableMapOf<String, JsonNode?>()
        fun retainedVersion(ref: CollectionOriginalFileReference, version: CollectionCatalogVersionDao): Boolean {
            if (version.collectionId != ref.businessId || version.orgId != ref.orgId) return false
            val expected = expectedVersions.getOrPut(version.id.value) { JsonUtils.mapper.readTree(JsonUtils.toJson(version.toModel())) }
            return retained[ref.businessId to ref.orgId].orEmpty().any { row ->
                val archive = archives[row.archiveId] ?: return@any false
                if (archive.orgId != row.orgId || archive.bizType != CollectionMainFileReadPolicy.RETAINED ||
                    archive.bizId != row.id.value || archive.phase != "TRANSFERRED") return@any false
                if (!parsedArchives.containsKey(archive.id.value)) {
                    parsedArchives[archive.id.value] = if (archive.digest == CollectionCareEvidence.digest(archive.snapshot))
                        JsonUtils.mapper.readTree(archive.snapshot) else null
                }
                val snapshot = parsedArchives[archive.id.value] ?: return@any false
                snapshot.path("collection").path("id").asString() == ref.businessId &&
                    snapshot.path("collection").path("orgId").asString() == ref.orgId &&
                    snapshot.path("catalogVersions").any { it == expected }
            }
        }
        return references.map { (ref, historical) ->
            val parent = parents[ref.businessId]
            val version = if (historical) versions[ref.recordId] else null
            val catalog = version?.let { catalogs[it.catalogId] }
            val catalogMatches = version == null || (catalog?.collectionId == ref.businessId && catalog.orgId == parent?.orgId)
            if (parent != null && (!historical || version != null) && catalogMatches &&
                (parent.orgId == ref.orgId || (version != null && retainedVersion(ref, version)))) ref
            else ref.copy(businessType = "COLLECTION_UNRESOLVED_MAIN_FILES")
        }.distinct()
    }

    /** 旧版本保留原馆归属，须与该馆已冻结的版本内容相符；读取授权仍核对逐文件实际移交。 */
    fun isRetainedVersion(reference: CollectionOriginalFileReference): Boolean {
        val version = CollectionCatalogVersionDao.findById(reference.recordId) ?: return false
        if (version.collectionId != reference.businessId || version.orgId != reference.orgId) return false
        val expected = JsonUtils.mapper.readTree(JsonUtils.toJson(version.toModel()))
        return CollectionRetainedLedgerDao.find {
            (CollectionRetainedLedgers.collectionId eq reference.businessId) and (CollectionRetainedLedgers.orgId eq reference.orgId)
        }.any { row ->
            val archive = CollectionPermanentArchiveDao.findById(row.archiveId) ?: return@any false
            if (archive.orgId != row.orgId || archive.bizType != CollectionMainFileReadPolicy.RETAINED || archive.bizId != row.id.value ||
                archive.phase != "TRANSFERRED" || archive.digest != CollectionCareEvidence.digest(archive.snapshot)) return@any false
            val snapshot = JsonUtils.mapper.readTree(archive.snapshot)
            snapshot.path("collection").path("id").asString() == reference.businessId &&
                snapshot.path("collection").path("orgId").asString() == reference.orgId &&
                snapshot.path("catalogVersions").any { it == expected }
        }
    }

    /** 已从当前主档移除的原件也属于待交接档案；只采用已保存且自洽的主档留存记录。 */
    fun historicalFileIds(collectionId: String): List<String> =
        CollectionRetainedOriginalFiles.ids(BIZ, collectionId).toList()
}
