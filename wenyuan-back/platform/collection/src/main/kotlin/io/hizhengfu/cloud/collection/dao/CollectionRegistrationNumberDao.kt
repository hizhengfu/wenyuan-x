package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntity
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntityClass
import io.hizhengfu.cloud.collection.common.OrgAuditUIDTable
import io.hizhengfu.cloud.collection.domain.EnumRegistrationNoState
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import java.time.LocalDateTime

/** 总登记号占号表：用过的号永久保留，防止更正/注销后的号码被重新分配。 */
object CollectionRegistrationNumbers : OrgAuditUIDTable("col_registration_no") {
    val totalRegNo = varchar("total_reg_no", 64).comment("总登记号")
    val collectionId = varchar("collection_id", 64).nullable().index(shortIndexName("collection_id"))
        .comment("关联藏品")
    val state = enumeration<EnumRegistrationNoState>("state").default(EnumRegistrationNoState.USED).comment("占用状态")
    val reason = varchar("reason", 500).nullable().comment("占号原因")
    val refBizId = varchar("ref_biz_id", 64).nullable().comment("关联业务")

    init {
        uniqueIndex(shortIndexName("org_id_total_reg_no", unique = true), orgId, totalRegNo)
        index(shortIndexName("org_id_collection_id"), false, orgId, collectionId)
    }
}

class CollectionRegistrationNumberDao(id: EntityID<String>) :
    OrgAuditUIDEntity(id, CollectionRegistrationNumbers) {
    companion object : OrgAuditUIDEntityClass<CollectionRegistrationNumberDao>(CollectionRegistrationNumbers)

    var totalRegNo by CollectionRegistrationNumbers.totalRegNo
    var collectionId by CollectionRegistrationNumbers.collectionId
    var state by CollectionRegistrationNumbers.state
    var reason by CollectionRegistrationNumbers.reason
    var refBizId by CollectionRegistrationNumbers.refBizId

    fun toModel() = CollectionRegistrationNumberVo(
        id = id.value,
        orgId = orgId,
        totalRegNo = totalRegNo,
        collectionId = collectionId,
        state = state,
        reason = reason,
        refBizId = refBizId,
        createTime = createTime,
    )
}

data class CollectionRegistrationNumberVo(
    val id: String? = null,
    val orgId: String? = null,
    val totalRegNo: String? = null,
    val collectionId: String? = null,
    val state: EnumRegistrationNoState? = null,
    val reason: String? = null,
    val refBizId: String? = null,
    val createTime: LocalDateTime? = null,
)
