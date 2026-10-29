package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortCheckConstraintName
import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import org.jetbrains.exposed.v1.json.json
import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntity
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntityClass
import io.hizhengfu.cloud.collection.common.OrgAuditUIDTable
import io.hizhengfu.cloud.collection.domain.CollectionCatalogContentVo
import io.hizhengfu.cloud.collection.domain.CollectionCatalogVersionVo
import io.hizhengfu.cloud.collection.domain.EnumCatalogStatus
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.javatime.datetime

/** 一件藏品一条当前编目工作副本；审核历史保存在 [CollectionCatalogVersions]。 */
object CollectionCatalogs : OrgAuditUIDTable("col_catalog") {
    val collectionId = varchar("collection_id", 64)
        .uniqueIndex(shortIndexName("collection_id", unique = true)).comment("藏品")
    val catalogStatus = enumeration<EnumCatalogStatus>("catalog_status")
        .default(EnumCatalogStatus.DRAFT).index(shortIndexName("catalog_status")).comment("编目工作状态")
    val version = integer("version", checkConstraintName = shortCheckConstraintName("version"))
        .default(0).comment("最后提交版本")
    val submittedVersion = integer(
        "submitted_version",
        checkConstraintName = shortCheckConstraintName("submitted_version"),
    ).nullable().comment("当前待审版本")
    val content = json(
        "content",
        { JsonUtils.toJson(it) ?: "{}" },
        { JsonUtils.fromJson(it, CollectionCatalogContentVo::class.java) ?: CollectionCatalogContentVo() },
    ).comment("编目工作副本")
    val submittedBy = varchar("submitted_by", 64).nullable()
    val submittedAt = datetime("submitted_at").nullable()
    val auditOpinion = varchar("audit_opinion", 2000).nullable()
    val auditBy = varchar("audit_by", 64).nullable()
    val auditTime = datetime("audit_time").nullable()

    init {
        index(shortIndexName("org_id_catalog_status"), false, orgId, catalogStatus)
    }
}

class CollectionCatalogDao(id: EntityID<String>) : OrgAuditUIDEntity(id, CollectionCatalogs) {
    companion object : OrgAuditUIDEntityClass<CollectionCatalogDao>(CollectionCatalogs)

    var collectionId by CollectionCatalogs.collectionId
    var catalogStatus by CollectionCatalogs.catalogStatus
    var version by CollectionCatalogs.version
    var submittedVersion by CollectionCatalogs.submittedVersion
    var content by CollectionCatalogs.content
    var submittedBy by CollectionCatalogs.submittedBy
    var submittedAt by CollectionCatalogs.submittedAt
    var auditOpinion by CollectionCatalogs.auditOpinion
    var auditBy by CollectionCatalogs.auditBy
    var auditTime by CollectionCatalogs.auditTime
}

/** 每次提交生成一条内容快照；审核只补充决定信息，不再改动快照内容。 */
object CollectionCatalogVersions : OrgAuditUIDTable("col_catalog_version") {
    val catalogId = varchar("catalog_id", 64).index(shortIndexName("catalog_id")).comment("编目主记录")
    val collectionId = varchar("collection_id", 64).index(shortIndexName("collection_id")).comment("藏品")
    val version = integer("version", checkConstraintName = shortCheckConstraintName("version")).comment("版本号")
    val catalogStatus = enumeration<EnumCatalogStatus>("catalog_status").comment("版本审核状态")
    val content = json(
        "content",
        { JsonUtils.toJson(it) ?: "{}" },
        { JsonUtils.fromJson(it, CollectionCatalogContentVo::class.java) ?: CollectionCatalogContentVo() },
    ).comment("提交内容快照")
    val submittedBy = varchar("submitted_by", 64).nullable()
    val submittedAt = datetime("submitted_at").nullable()
    val auditOpinion = varchar("audit_opinion", 2000).nullable()
    val auditBy = varchar("audit_by", 64).nullable()
    val auditTime = datetime("audit_time").nullable()

    init {
        uniqueIndex(shortIndexName("catalog_id_version", unique = true), catalogId, version)
        index(shortIndexName("org_id_collection_id_version"), false, orgId, collectionId, version)
    }
}

class CollectionCatalogVersionDao(id: EntityID<String>) : OrgAuditUIDEntity(id, CollectionCatalogVersions) {
    companion object : OrgAuditUIDEntityClass<CollectionCatalogVersionDao>(CollectionCatalogVersions)

    var catalogId by CollectionCatalogVersions.catalogId
    var collectionId by CollectionCatalogVersions.collectionId
    var version by CollectionCatalogVersions.version
    var catalogStatus by CollectionCatalogVersions.catalogStatus
    var content by CollectionCatalogVersions.content
    var submittedBy by CollectionCatalogVersions.submittedBy
    var submittedAt by CollectionCatalogVersions.submittedAt
    var auditOpinion by CollectionCatalogVersions.auditOpinion
    var auditBy by CollectionCatalogVersions.auditBy
    var auditTime by CollectionCatalogVersions.auditTime

    fun toModel() = CollectionCatalogVersionVo(
        id = id.value,
        catalogId = catalogId,
        collectionId = collectionId,
        version = version,
        status = catalogStatus,
        content = content,
        submittedBy = submittedBy,
        submittedAt = submittedAt,
        auditOpinion = auditOpinion,
        auditBy = auditBy,
        auditTime = auditTime,
    )
}
