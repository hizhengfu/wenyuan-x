package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortCheckConstraintName
import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.autoconfigure.exposed.ContentAuditTreeUIDEntity
import io.hizhengfu.cloud.autoconfigure.exposed.ContentAuditTreeUIDEntityClass
import io.hizhengfu.cloud.autoconfigure.exposed.ContentAuditTreeUIDTable
import jakarta.validation.constraints.NotBlank
import org.jetbrains.exposed.v1.core.dao.id.EntityID

/** 文物分类（WW/T 0017—2013 附录D固定35类）。 */
object CollectionCategories : ContentAuditTreeUIDTable("col_category") {
    val orgId = varchar("org_id", 64).nullable().index(shortIndexName("org_id"))
        .comment("规范分类统一为null")
    val code = varchar("code", 32).nullable().comment("附录D表D.1序号，两位表示")
    val name = varchar("name", 100).comment("类别名")
    val sort = integer("sort", checkConstraintName = shortCheckConstraintName("sort")).default(0)
}

class CollectionCategoryDao(id: EntityID<String>) : ContentAuditTreeUIDEntity(id, CollectionCategories) {
    companion object : ContentAuditTreeUIDEntityClass<CollectionCategoryDao>(CollectionCategories)

    var orgId by CollectionCategories.orgId
    var code by CollectionCategories.code
    var name by CollectionCategories.name
    var sort by CollectionCategories.sort

    fun toModel() = CollectionCategoryVo(
        id = id.value,
        parentId = parentId,
        orgId = orgId,
        code = code,
        name = name,
        sort = sort,
    )
}

data class CollectionCategoryVo(
    val id: String? = null,
    val parentId: String? = null,
    val parentName: String? = null,
    val orgId: String? = null,
    val code: String? = null,
    @field:NotBlank val name: String? = null,
    val sort: Int? = null,
)
