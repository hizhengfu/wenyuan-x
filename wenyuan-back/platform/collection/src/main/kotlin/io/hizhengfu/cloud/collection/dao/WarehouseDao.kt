package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortCheckConstraintName
import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.autoconfigure.exposed.ContentAuditTreeUIDEntity
import io.hizhengfu.cloud.autoconfigure.exposed.ContentAuditTreeUIDEntityClass
import io.hizhengfu.cloud.autoconfigure.exposed.ContentAuditTreeUIDTable
import io.hizhengfu.cloud.collection.domain.EnumLocationScene
import io.hizhengfu.cloud.collection.domain.EnumWarehouseType
import jakarta.validation.constraints.NotBlank
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.json.json
import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.domain.*

/** 藏品位置树：库房库位与展厅/展区/展柜共用稳定的位置 ID。 */
object Warehouses : ContentAuditTreeUIDTable("col_warehouse") {
    val orgId = varchar("org_id", 64).index(shortIndexName("org_id")).comment("收藏单位")
    val code = varchar("code", 64).nullable().comment("库位编码")
    val name = varchar("name", 100).comment("名称")
    val thumbnail = varchar("thumbnail", 512).nullable().comment("位置缩略图 fileId/路径")
    val sceneType = enumerationByName<EnumLocationScene>("scene_type", 20)
        .default(EnumLocationScene.STORAGE).comment("位置场景：保管/展陈")
    val type = enumeration<EnumWarehouseType>("type").default(EnumWarehouseType.WAREHOUSE).comment("库位类型")
    val capacity = integer("capacity", checkConstraintName = shortCheckConstraintName("capacity"))
        .nullable().comment("容量")
    val temperature = varchar("temperature", 32).nullable().comment("温湿度要求")
    val custodian = varchar("custodian", 100).nullable().comment("保管责任人")
    val sort = integer("sort", checkConstraintName = shortCheckConstraintName("sort")).default(0)

    val staffing = json("statutory_staffing", { JsonUtils.toJson(it)!! }, { JsonUtils.fromJson(it, WarehouseStaffingVo::class.java)!! }).nullable()

    init {
        index(shortIndexName("org_id_scene_type_type"), false, orgId, sceneType, type)
    }
}

class WarehouseDao(id: EntityID<String>) : ContentAuditTreeUIDEntity(id, Warehouses) {
    companion object : ContentAuditTreeUIDEntityClass<WarehouseDao>(Warehouses)

    var orgId by Warehouses.orgId
    var code by Warehouses.code
    var name by Warehouses.name
    var thumbnail by Warehouses.thumbnail
    var sceneType by Warehouses.sceneType
    var type by Warehouses.type
    var capacity by Warehouses.capacity
    var temperature by Warehouses.temperature
    var custodian by Warehouses.custodian
    var sort by Warehouses.sort

    var staffing by Warehouses.staffing

    fun toModel() = WarehouseVo(
        id = id.value,
        staffing = staffing,

        parentId = parentId,
        orgId = orgId,
        code = code,
        name = name,
        thumbnail = thumbnail,
        sceneType = sceneType,
        type = type,
        capacity = capacity,
        temperature = temperature,
        custodian = custodian,
        sort = sort,
    )
}

data class WarehouseVo(
    val id: String? = null,
    val staffing: WarehouseStaffingVo? = null,
    val archives: List<Map<String, Any?>> = emptyList(),
    val parentId: String? = null,
    val orgId: String? = null,
    val code: String? = null,
    @field:NotBlank val name: String? = null,
    val thumbnail: String? = null,
    val sceneType: EnumLocationScene? = null,
    val type: EnumWarehouseType? = null,
    val capacity: Int? = null,
    val temperature: String? = null,
    val custodian: String? = null,
    val sort: Int? = null,
)
