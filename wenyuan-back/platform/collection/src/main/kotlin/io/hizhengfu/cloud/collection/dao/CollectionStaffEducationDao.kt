package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortCheckConstraintName

import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.domain.*
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.json.json

object CollectionStaffEducations : OrgAuditUIDTable("col_staff_education") {
    val userId = varchar("user_id",64).index(shortIndexName("user_id"))
    val requestKey = varchar("request_key",64)
    val original = json("original",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,StaffEducationOriginal::class.java)!!})
    init {uniqueIndex(shortIndexName("org_request",unique=true),orgId,requestKey)}
}
class CollectionStaffEducationDao(id:EntityID<String>):OrgAuditUIDEntity(id,CollectionStaffEducations) {
    companion object:OrgAuditUIDEntityClass<CollectionStaffEducationDao>(CollectionStaffEducations)
    var userId by CollectionStaffEducations.userId
    var requestKey by CollectionStaffEducations.requestKey
    var original by CollectionStaffEducations.original
}
object CollectionStaffEducationVersions : OrgAuditUIDTable("col_staff_education_version") {
    val educationId = varchar("education_id",64).index(shortIndexName("education_id"))
    val version = integer("version", checkConstraintName = shortCheckConstraintName("version"))
    val content = json("content",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,StaffEducationVersionRecord::class.java)!!})
    val confirmation = json("confirmation",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,StaffEducationConfirmation::class.java)!!}).nullable()
    init {uniqueIndex(shortIndexName("education_version",unique=true),educationId,version)}
}
class CollectionStaffEducationVersionDao(id:EntityID<String>):OrgAuditUIDEntity(id,CollectionStaffEducationVersions) {
    companion object:OrgAuditUIDEntityClass<CollectionStaffEducationVersionDao>(CollectionStaffEducationVersions)
    var educationId by CollectionStaffEducationVersions.educationId
    var version by CollectionStaffEducationVersions.version
    var content by CollectionStaffEducationVersions.content
    var confirmation by CollectionStaffEducationVersions.confirmation
}
object CollectionStaffEducationFileAccesses : OrgAuditUIDTable("col_staff_education_file_access") {
    val educationId = varchar("education_id",64).index(shortIndexName("education_id"))
    val fileId = varchar("file_id",64).index(shortIndexName("file_id"))
    val pathKey = varchar("path_key",64).index(shortIndexName("path_key"))
    init {uniqueIndex(shortIndexName("education_file_path",unique=true),educationId,fileId,pathKey)}
}
class CollectionStaffEducationFileAccessDao(id:EntityID<String>):OrgAuditUIDEntity(id,CollectionStaffEducationFileAccesses) {
    companion object:OrgAuditUIDEntityClass<CollectionStaffEducationFileAccessDao>(CollectionStaffEducationFileAccesses)
    var educationId by CollectionStaffEducationFileAccesses.educationId
    var fileId by CollectionStaffEducationFileAccesses.fileId
    var pathKey by CollectionStaffEducationFileAccesses.pathKey
}
