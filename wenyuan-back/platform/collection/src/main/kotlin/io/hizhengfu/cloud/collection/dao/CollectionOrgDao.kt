package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortCheckConstraintName
import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.autoconfigure.exposed.ContentAuditTreeUIDEntity
import io.hizhengfu.cloud.autoconfigure.exposed.ContentAuditTreeUIDEntityClass
import io.hizhengfu.cloud.autoconfigure.exposed.ContentAuditTreeUIDTable
import io.hizhengfu.cloud.collection.domain.EnumOrgLevel
import io.hizhengfu.cloud.collection.domain.EnumOrgStatus
import io.hizhengfu.cloud.collection.domain.EnumOwnership
import jakarta.validation.constraints.NotBlank
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import java.time.LocalDateTime

/** 收藏单位（多馆基础，支持总分馆树） */
object CollectionOrgs : ContentAuditTreeUIDTable("col_org") {
    val code = varchar("code", 32).uniqueIndex(shortIndexName("code", unique = true)).comment("收藏单位代码")
    val exchangeCode = varchar("exchange_code", 14).nullable().uniqueIndex(shortIndexName("exchange_code", unique = true)).comment("WW/T0017规范交换机构代码，按原赋码资料填写")
    val exchangeCodeBasis = varchar("exchange_code_basis", 500).nullable().comment("规范交换机构代码赋码依据")
    val name = varchar("name", 200).comment("名称")
    val shortName = varchar("short_name", 64).nullable().comment("简称")
    val level = enumeration<EnumOrgLevel>("level").nullable().comment("单位级别")
    val ownership = enumeration<EnumOwnership>("ownership").nullable().comment("举办主体")
    val legalPerson = varchar("legal_person", 64).nullable().comment("法定代表人")
    val areaId = varchar("area_id", 64).nullable().comment("所在行政区划")
    val address = varchar("address", 255).nullable()
    val contact = varchar("contact", 64).nullable()
    val phone = varchar("phone", 32).nullable()
    val sourceId = varchar("source_id", 64).nullable().comment("一普机构编码（互通 archive 名录）")
    val systemGroupId = varchar("system_group_id", 64).nullable().index(shortIndexName("system_group_id"))
        .comment("授权机构 ID（关联 sys_group.id），仅用于用户场馆可见范围")
    val locationRootId = varchar("location_root_id", 64).nullable().index(shortIndexName("location_root_id"))
        .comment("物理安装位置根（逻辑关联 col_warehouse.id），为空时使用本收藏单位自己的位置树")
    val orgStatus = enumeration<EnumOrgStatus>("org_status").default(EnumOrgStatus.ENABLED).comment("启用/停用")
    /** 位置结构与引用写入的事务协调版本，不属于可编辑的单位资料。 */
    val locationMutationVersion = long("location_mutation_version", checkConstraintName = shortCheckConstraintName("location_mutation_version", typeCode = "l")).default(0)
    val sort = long("sort", checkConstraintName = shortCheckConstraintName("sort", typeCode = "l"))
        .default(0).comment("排序")
}

class CollectionOrgDao(id: EntityID<String>) : ContentAuditTreeUIDEntity(id, CollectionOrgs) {
    companion object : ContentAuditTreeUIDEntityClass<CollectionOrgDao>(CollectionOrgs)

    var code by CollectionOrgs.code
    var exchangeCode by CollectionOrgs.exchangeCode
    var exchangeCodeBasis by CollectionOrgs.exchangeCodeBasis
    var name by CollectionOrgs.name
    var shortName by CollectionOrgs.shortName
    var level by CollectionOrgs.level
    var ownership by CollectionOrgs.ownership
    var legalPerson by CollectionOrgs.legalPerson
    var areaId by CollectionOrgs.areaId
    var address by CollectionOrgs.address
    var contact by CollectionOrgs.contact
    var phone by CollectionOrgs.phone
    var sourceId by CollectionOrgs.sourceId
    var systemGroupId by CollectionOrgs.systemGroupId
    var locationRootId by CollectionOrgs.locationRootId
    var orgStatus by CollectionOrgs.orgStatus
    var sort by CollectionOrgs.sort

    fun toModel(locationRootName: String? = null) = CollectionOrgVo(
        id = id.value,
        parentId = parentId,
        code = code,
        exchangeCode = exchangeCode,
        exchangeCodeBasis = exchangeCodeBasis,
        name = name,
        shortName = shortName,
        level = level,
        ownership = ownership,
        legalPerson = legalPerson,
        areaId = areaId,
        address = address,
        contact = contact,
        phone = phone,
        sourceId = sourceId,
        systemGroupId = systemGroupId,
        locationRootId = locationRootId,
        locationRootName = locationRootName,
        status = orgStatus,
        sort = sort,
        createTime = createTime,
    )
}

data class CollectionOrgVo(
    val id: String? = null,
    val parentId: String? = null,
    val parentName: String? = null,
    @field:NotBlank val code: String? = null,
    @field:NotBlank val name: String? = null,
    val shortName: String? = null,
    val level: EnumOrgLevel? = null,
    val ownership: EnumOwnership? = null,
    val legalPerson: String? = null,
    val areaId: String? = null,
    val address: String? = null,
    val contact: String? = null,
    val phone: String? = null,
    val sourceId: String? = null,
    val systemGroupId: String? = null,
    val locationRootId: String? = null,
    val locationRootName: String? = null,
    val status: EnumOrgStatus? = null,
    val sort: Long? = null,
    val createTime: LocalDateTime? = null,
    val exchangeCode: String? = null,
    val exchangeCodeBasis: String? = null,
    val codeStateHash: String? = null,
)
