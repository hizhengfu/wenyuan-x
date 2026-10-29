package io.hizhengfu.cloud.wms.dao

import io.hizhengfu.cloud.autoconfigure.exposed.UIDEntity
import io.hizhengfu.cloud.autoconfigure.exposed.UIDEntityClass
import io.hizhengfu.cloud.autoconfigure.exposed.UIDTable

import io.hizhengfu.cloud.wms.domain.EnumArtifactStatus
import io.hizhengfu.cloud.wms.domain.EnumOperationFlowStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.javatime.datetime
import java.math.BigDecimal
import java.time.LocalDateTime


/**
 * 文物基本信息表
 * */
object WmsArtifacts : UIDTable("wms_artifact") {
    val image = varchar("image", 255).nullable().comment("缩略图")//缩略图
    val name = varchar("name", 250).comment("文物名称")//文物名称
    val projectId = varchar("project_id", 64).comment("所属考古项目")//所属考古项目
    val code = varchar("code", 50).nullable().comment("文物编号(例:K2023-G1-A001)")//文物编号(例:K2023-G1-A001)
    val categoryId = varchar("category_id", 20).nullable().comment("文物分类")//文物分类
    val material = varchar("material", 255).comment("材质")//材质、多选

    //    val shape = varchar("shape", 255).nullable().comment("器形")
    val quantity = integer("quantity").comment("数量")//数量
    val quantityUnit = varchar("quantity_unit", 10).comment("数量单位（件、套、批），字典选择")//数量单位（件、套、批），字典选择

    val preservation = varchar("preservation", 1024).nullable().comment("出土状况")
    val unearthDate = datetime("unearth_date").nullable().comment("出土日期")//出土日期
    val location = varchar("location", 255).nullable().comment("出土位置")//出土位置
    val projectCellId = varchar("project_cell_id", 255).nullable().comment("出土单位")//出土位置
    val coordinate = varchar("coordinate", 1024).nullable().comment("出土坐标")//出土坐标
    val lat = double("lat").nullable().comment("经度")//经度
    val lng = double("lng").nullable().comment("纬度")//纬度
    val remarks = text("remarks").nullable().comment("备注")//备注

    val recorderBy = varchar("recorder_by", 50).nullable().comment("记录人")//记录人
    val createBy = varchar("create_by", 64).comment("创建人")//创建人
    val createTime = datetime("create_time").comment("创建时间")//创建时间
    val updateBy = varchar("update_by", 64).nullable().comment("更新人")//更新人

    val updateTime = datetime("update_time").nullable().comment("更新时间")//更新时间

    val status = enumeration<EnumArtifactStatus>("status").default(EnumArtifactStatus.REGISTER).comment("状态")

    init {
        //为名称和code增加不能重复的索引
        index(true, projectId, name, code)
    }

    fun toModel(row: ResultRow): WmsArtifactVo {
        return WmsArtifactVo(
            id = row[id]?.value,
            image = row[image],
            name = row[name],
            projectId = row[projectId],
            project = WmsProjectVo(id = row[projectId], name = row[WmsProjects.name]),
            code = row[code],
            categoryId = row[categoryId],
            material = row[material],
            quantity = row[quantity],
            quantityUnit = row[quantityUnit],
            preservation = row[preservation],
            unearthDate = row[unearthDate],
            location = row[location],
            projectCellId = row[projectCellId],
            projectCellName = row[WmsProjectCells.name],
            coordinate = row[coordinate],
            lat = row[lat],
            lng = row[lng],
            remarks = row[remarks],
            recorderBy = row[recorderBy],
            status = row[status],
            createBy = row[createBy],
            createTime = row[createTime],
            updateBy = row[updateBy],
            updateTime = row[updateTime]
        )
    }

    fun toModelStock(row: ResultRow): WmsArtifactVo {
        return toModel(row).copy(
            saveStatus = row[WmsArtifactStocks.saveStatus],
            storageTime = row[WmsArtifactStocks.storageTime],
            stockQuantity = row[WmsArtifactStocks.stockQuantity],
            storageRoom = WmsStorageRoomVo(
                id = row[WmsStorageRooms.id]?.value,
                name = row[WmsStorageRooms.name]
            ),
            storageLocation = WmsStorageLocationVo(
                id = row[WmsArtifactStocks.locationId],
                name = row[WmsStorageLocations.name]
            ),
        )
    }

    fun toModelOperation(row: ResultRow): WmsArtifactVo {
        return toModel(row).copy(
            saveStatus = row[WmsArtifactOperateRecords.saveStatus],
            quantity = row[WmsArtifactOperateRecords.quantity],
            flowStatus = row[WmsArtifactOperateRecords.flowStatus],
            stockQuantity = row[WmsArtifactStocks.stockQuantity],
            storageLocation = WmsStorageLocationVo(
                id = row[WmsArtifactStocks.locationId],
                name = row[WmsStorageLocations.name]
            ),
        )
    }

}

class WmsArtifactDao(id: EntityID<String>) : UIDEntity(id) {
    companion object : UIDEntityClass<WmsArtifactDao>(WmsArtifacts)

    var image by WmsArtifacts.image
    var name by WmsArtifacts.name
    var projectId by WmsArtifacts.projectId
    var code by WmsArtifacts.code
    var categoryId by WmsArtifacts.categoryId
    var material by WmsArtifacts.material

    //    var shape by WmsArtifacts.shape
    var quantity by WmsArtifacts.quantity
    var quantityUnit by WmsArtifacts.quantityUnit
    var preservation by WmsArtifacts.preservation
    var unearthDate by WmsArtifacts.unearthDate
    var location by WmsArtifacts.location
    var projectCellId by WmsArtifacts.projectCellId
    var coordinate by WmsArtifacts.coordinate
    var lat by WmsArtifacts.lat
    var lng by WmsArtifacts.lng
    var remarks by WmsArtifacts.remarks
    var recorderBy by WmsArtifacts.recorderBy
    var status by WmsArtifacts.status
    var createBy by WmsArtifacts.createBy
    var createTime by WmsArtifacts.createTime
    var updateBy by WmsArtifacts.updateBy
    var updateTime by WmsArtifacts.updateTime

    fun toModel(): WmsArtifactVo {
        return WmsArtifactVo(
            id = id.value,
            image = image,
            name = name,
            projectId = projectId,
            code = code,
            categoryId = categoryId,
            material = material,
//            shape = shape,
            quantity = quantity,
            quantityUnit = quantityUnit,
            preservation = preservation,
            unearthDate = unearthDate,
            projectCellId = projectCellId,
            location = location,
            coordinate = coordinate,
            lat = lat,
            lng = lng,
            remarks = remarks,
            recorderBy = recorderBy,
            status = status,
//            locationId = locationId,
//            storageTime = storageTime,
//            saveStatus = saveStatus,
            createBy = createBy,
            createTime = createTime,
            updateBy = updateBy,
            updateTime = updateTime
        )
    }
}

data class WmsArtifactVo(
    val id: String? = null,
    val image: String? = null,
    @field:NotBlank
    val name: String? = null,
    @field:NotBlank
    val projectId: String? = null,
    val storageLocation: WmsStorageLocationVo? = null,
    val storageRoom: WmsStorageRoomVo? = null,
    val project: WmsProjectVo? = null,
    val code: String? = null,
    val categoryId: String? = null,
    @field:NotBlank
    val material: String? = null,
    @field:NotNull
    val quantity: Int? = null,

    @field:NotBlank
    val quantityUnit: String? = null,

    val preservation: String? = null,
    val unearthDate: LocalDateTime? = null,
    val unearthDateList: List<LocalDateTime>? = null,

    val location: String? = null,
    val projectCellId: String? = null,
    val projectCellType: String? = null,
    val projectCellName: String? = null,

    val projectPlotId: String? = null,//探方id

    val coordinate: String? = null,
    var lat: Double? = null,
    var lng: Double? = null,
    val remarks: String? = null,
    val recorderBy: String? = null,
    val status: EnumArtifactStatus? = null,

    val stockQuantity: Int? = null,//在库数量
    val flowStatus: EnumOperationFlowStatus? = null,//流程状态
    val storageType: String? = null,
    val roomId: String? = null,//库房id
    val locationId: String? = null,//存放位置id
    val storageTime: LocalDateTime? = null,
    val storageTimeList: List<LocalDateTime>? = null,
    val storageRemarks: String? = null,
    val saveStatus: String? = null,

    val createBy: String? = null,
    val createTime: LocalDateTime? = null,
    val updateBy: String? = null,
    val updateTime: LocalDateTime? = null,
    val updateTimeList: List<LocalDateTime>? = null,
    val files: List<String>? = null
) {
    var latlng: String?
        get() {
            return if (lng != null && lat != null) {
                "${lat},${lng}"
            } else {
                null
            }
        }
        set(value) {
            if (!value.isNullOrBlank()) {
                val temp = value.split(",")
                if (temp.size == 2) {
                    lng = temp[1].toDouble()
                    lat = temp[0].toDouble()
                }
            }
        }
}


/**
 * 文物信息扩展数据
 *
 * 字段覆盖 xmind 中各质地（陶/瓷/铜/玉/石/骨/漆/铁/化石/甲骨等）的研究字段，
 * 在 DB 层使用扁平结构 + 可空，避免每种材质一张子表；
 * 业务层根据 material 决定哪些字段必填、哪些隐藏。
 * */
object WmsArtifactExtends : UIDTable("wms_artifact_extend", columnName = "artifact_id") {
    //基础字段
    val timePeriod = varchar("time_period", 50).nullable().comment("时代")
    val culturalAttribute = varchar("cultural_attribute", 50).nullable().comment("文化属性")

    // 用途三级分类：例如 "生活用具" / "生活用具/炊具" / "生活用具/炊具/鼎"
    val purpose = varchar("purpose", 80).nullable().comment("用途（支持多级 /）")
    val purposeL1 = varchar("purpose_l1", 50).nullable().comment("用途一级")
    val purposeL2 = varchar("purpose_l2", 50).nullable().comment("用途二级")
    val purposeL3 = varchar("purpose_l3", 50).nullable().comment("用途三级")

    val shape = varchar("shape", 500).nullable().comment("形制")
    val decoration = varchar("decoration", 500).nullable().comment("纹饰")

    val craftingTechnique = varchar("crafting_technique", 500).nullable().comment("制作工艺")
    val lineDiagram = varchar("line_diagram", 50).nullable().comment("文物线图")

    val artifactType = varchar("artifact_type", 50).nullable().comment("器型")

    val dimensions = varchar("dimensions", 100).nullable().comment("尺寸（长×宽×高/cm）")
    val weight = decimal("weight", 8, 2).nullable().comment("重量（kg）")

    // ===== 陶器：陶系 =====
    val potteryQuality = varchar("pottery_quality", 50).nullable().comment("陶质（泥质/夹砂/夹蚌等）")
    val potteryColor = varchar("pottery_color", 50).nullable().comment("陶色（红/灰/黑/白等）")

    // ===== 瓷器 =====
    val kilnSite = varchar("kiln_site", 50).nullable().comment("窑口")
    val kilnSystem = varchar("kiln_system", 50).nullable().comment("窑系")
    val glazeColor = varchar("glaze_color", 50).nullable().comment("釉色")
    val markText = varchar("mark_text", 200).nullable().comment("款识")

    // ===== 石器/玉器/骨器 =====
    val lithology = varchar("lithology", 50).nullable().comment("岩性/材质细分")

    // ===== 铜器/金银器/铁器：铭文 =====
    val inscription = text("inscription").nullable().comment("铭文")

    // ===== 化石 =====
    val species = varchar("species", 100).nullable().comment("物种")

    // ===== 型式分期 =====
    val artifactStyle = varchar("artifact_style", 30).nullable().comment("型，如：Ⅰ型 / Ⅱ型")
    val artifactSubStyle = varchar("artifact_sub_style", 30).nullable().comment("亚型")
    val artifactStage = varchar("artifact_stage", 30).nullable().comment("式，如：1式 / 2式")
    val phaseName = varchar("phase_name", 30).nullable().comment("期")
    val sectionName = varchar("section_name", 30).nullable().comment("段")

    // ===== 自定义扩展字段（JSON） =====
    val extra = text("extra").nullable().comment("自定义扩展字段（JSON）")


    fun toModel(row: ResultRow): WmsArtifactExtendVo {
        return WmsArtifactExtendVo(
            artifactId = row[id].value,
            timePeriod = row[timePeriod],
            culturalAttribute = row[culturalAttribute],
            purpose = row[purpose],
            purposeL1 = row[purposeL1],
            purposeL2 = row[purposeL2],
            purposeL3 = row[purposeL3],
            shape = row[shape],
            decoration = row[decoration],
            craftingTechnique = row[craftingTechnique],
            lineDiagram = row[lineDiagram],
            artifactType = row[artifactType],
            dimensions = row[dimensions],
            weight = row[weight],
            potteryQuality = row[potteryQuality],
            potteryColor = row[potteryColor],
            kilnSite = row[kilnSite],
            kilnSystem = row[kilnSystem],
            glazeColor = row[glazeColor],
            markText = row[markText],
            lithology = row[lithology],
            inscription = row[inscription],
            species = row[species],
            artifactStyle = row[artifactStyle],
            artifactSubStyle = row[artifactSubStyle],
            artifactStage = row[artifactStage],
            phaseName = row[phaseName],
            sectionName = row[sectionName],
            extra = row[extra],
        )
    }
}

data class WmsArtifactExtendVo(
    val artifactId: String,
    val timePeriod: String? = null,
    val culturalAttribute: String? = null,
    val purpose: String? = null,
    val purposeL1: String? = null,
    val purposeL2: String? = null,
    val purposeL3: String? = null,
    val shape: String? = null,
    val decoration: String? = null,
    val craftingTechnique: String? = null,
    val lineDiagram: String? = null,
    val artifactType: String? = null,
    val dimensions: String? = null,
    val weight: BigDecimal? = null,
    val potteryQuality: String? = null,
    val potteryColor: String? = null,
    val kilnSite: String? = null,
    val kilnSystem: String? = null,
    val glazeColor: String? = null,
    val markText: String? = null,
    val lithology: String? = null,
    val inscription: String? = null,
    val species: String? = null,
    val artifactStyle: String? = null,
    val artifactSubStyle: String? = null,
    val artifactStage: String? = null,
    val phaseName: String? = null,
    val sectionName: String? = null,
    val extra: String? = null,
)






















