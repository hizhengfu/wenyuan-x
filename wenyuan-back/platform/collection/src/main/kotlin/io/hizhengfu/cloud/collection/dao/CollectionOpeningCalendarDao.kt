package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortCheckConstraintName
import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntity
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntityClass
import io.hizhengfu.cloud.collection.common.OrgAuditUIDTable
import io.hizhengfu.cloud.collection.domain.CollectionOpeningCalendarVo
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.json.json

object CollectionOpeningCalendarVersions : OrgAuditUIDTable("col_open_calendar_version") {
    val year = integer("calendar_year", checkConstraintName = shortCheckConstraintName("calendar_year"))
    val revision = integer("revision", checkConstraintName = shortCheckConstraintName("revision"))
    val content = json("content", { JsonUtils.toJson(it)!! }, { JsonUtils.fromJson(it, CollectionOpeningCalendarVo::class.java)!! })
    init { uniqueIndex(shortIndexName("org_id_year_revision", unique = true), orgId, year, revision) }
}
class CollectionOpeningCalendarVersionDao(id: EntityID<String>) : OrgAuditUIDEntity(id, CollectionOpeningCalendarVersions) {
    companion object : OrgAuditUIDEntityClass<CollectionOpeningCalendarVersionDao>(CollectionOpeningCalendarVersions)
    var year by CollectionOpeningCalendarVersions.year
    var revision by CollectionOpeningCalendarVersions.revision
    var content by CollectionOpeningCalendarVersions.content
}
