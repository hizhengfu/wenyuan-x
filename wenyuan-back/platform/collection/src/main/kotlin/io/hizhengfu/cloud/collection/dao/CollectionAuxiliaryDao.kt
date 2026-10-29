package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntity
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntityClass
import io.hizhengfu.cloud.collection.common.OrgAuditUIDTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.json.json
import java.time.LocalDate

enum class EnumAuxiliaryKind { REPLICA, MODEL, TEACHING_SPECIMEN, CONTEMPORARY_BOOK, LAWFUL_EXIT, OTHER_NON_COLLECTION }

data class CollectionAuxiliaryVo(
    val id: String? = null,
    val orgId: String,
    val recordNo: String,
    val kind: EnumAuxiliaryKind,
    val name: String,
    val quantity: Int,
    val unit: String,
    val location: String,
    val registeredOn: LocalDate,
    val reason: String,
    val evidenceFileIds: String,
    val originCollectionId: String? = null,
    val originTotalRegNo: String? = null,
    val exitId: String? = null,
    @get:com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    val institutionHandover: Map<String,Any?>? = null,
)

/** 辅助账独立于正式藏品和总登记号体系。正式藏品仅在法定退出执行后产生关联条目。 */
object CollectionAuxiliaries : OrgAuditUIDTable("col_auxiliary") {
    val recordNo = varchar("record_no",100)
    val exitId = varchar("exit_id",64).nullable()
    val content = json("content", { JsonUtils.toJson(it)!! }, { JsonUtils.fromJson(it,CollectionAuxiliaryVo::class.java)!! })
    init {
        uniqueIndex(shortIndexName("org_id_record_no",unique=true),orgId,recordNo)
        uniqueIndex(shortIndexName("exit_id",unique=true),exitId)
    }
}
class CollectionAuxiliaryDao(id: EntityID<String>) : OrgAuditUIDEntity(id,CollectionAuxiliaries) {
    companion object : OrgAuditUIDEntityClass<CollectionAuxiliaryDao>(CollectionAuxiliaries)
    var recordNo by CollectionAuxiliaries.recordNo
    var exitId by CollectionAuxiliaries.exitId
    var content by CollectionAuxiliaries.content
    fun toModel() = content.copy(id=id.value,orgId=orgId,recordNo=recordNo,exitId=exitId)
}
