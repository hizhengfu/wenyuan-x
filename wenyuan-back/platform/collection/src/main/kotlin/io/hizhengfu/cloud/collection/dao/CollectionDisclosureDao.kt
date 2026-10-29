package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortCheckConstraintName

import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.domain.*
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.javatime.datetime
import org.jetbrains.exposed.v1.json.json

object CollectionDisclosureReviews : OrgAuditUIDTable("col_disclosure_review") {
    val subject = enumeration<EnumDisclosureSubject>("subject")
    val subjectId = varchar("subject_id", 64)
    val version = integer("version", checkConstraintName = shortCheckConstraintName("version"))
    val content = json("content", { JsonUtils.toJson(it)!! }, { JsonUtils.fromJson(it, CollectionDisclosureReviewVo::class.java)!! })
    val feedEnabledAt = datetime("feed_enabled_at").nullable()
    val feedStoppedAt = datetime("feed_stopped_at").nullable()
    init { uniqueIndex(shortIndexName("org_subject_version", unique = true), orgId, subject, subjectId, version) }
}
class CollectionDisclosureReviewDao(id: EntityID<String>) : OrgAuditUIDEntity(id, CollectionDisclosureReviews) {
    companion object : OrgAuditUIDEntityClass<CollectionDisclosureReviewDao>(CollectionDisclosureReviews)
    var subject by CollectionDisclosureReviews.subject
    var subjectId by CollectionDisclosureReviews.subjectId
    var version by CollectionDisclosureReviews.version
    var content by CollectionDisclosureReviews.content
    var feedEnabledAt by CollectionDisclosureReviews.feedEnabledAt
    var feedStoppedAt by CollectionDisclosureReviews.feedStoppedAt
}
object CollectionDisclosureEvents : OrgAuditUIDTable("col_disclosure_event") {
    val reviewId = varchar("review_id", 64)
    val content = json("content", { JsonUtils.toJson(it)!! }, { JsonUtils.fromJson(it, CollectionDisclosureEventVo::class.java)!! })
}
class CollectionDisclosureEventDao(id: EntityID<String>) : OrgAuditUIDEntity(id, CollectionDisclosureEvents) {
    companion object : OrgAuditUIDEntityClass<CollectionDisclosureEventDao>(CollectionDisclosureEvents)
    var reviewId by CollectionDisclosureEvents.reviewId
    var content by CollectionDisclosureEvents.content
}
