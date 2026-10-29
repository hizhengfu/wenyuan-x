package io.hizhengfu.cloud.wms.dao

import io.hizhengfu.cloud.autoconfigure.exposed.UIDEntity
import io.hizhengfu.cloud.autoconfigure.exposed.UIDEntityClass
import io.hizhengfu.cloud.autoconfigure.exposed.UIDTable

import io.hizhengfu.cloud.wms.domain.EnumArtifactStatus
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.javatime.datetime


/**
 * 文物库存状态表
 * 本表id为文物登记时候的ID
 * */
object WmsArtifactStocks : UIDTable("wms_artifact_stock", columnName = "artifact_id") {
    val quantity = integer("quantity").comment("入库数量")
    val stockQuantity = integer("stock_quantity").comment("库存数量")

    val status = enumeration<EnumArtifactStatus>("status").default(EnumArtifactStatus.REGISTER).comment("状态")

    //库房操作后更新
    val storageType = varchar("storage_type", 255).nullable().comment("操作类型")
    val locationId = varchar("locationId", 64).nullable().comment("库房位置")
    val storageTime = datetime("storage_time").nullable().comment("库房操作时间")
    val storageRemarks = varchar("storage_remarks", 2000).nullable().comment("库房操作原因")
    val saveStatus = varchar("save_status", 100).nullable().comment("保存状态")
}

class WmsArtifactStockDao(id: EntityID<String>) : UIDEntity(id) {
    companion object : UIDEntityClass<WmsArtifactStockDao>(WmsArtifactStocks)

    var quantity by WmsArtifactStocks.quantity
    var stockQuantity by WmsArtifactStocks.stockQuantity
    var status by WmsArtifactStocks.status
    var storageType by WmsArtifactStocks.storageType
    var locationId by WmsArtifactStocks.locationId
    var storageTime by WmsArtifactStocks.storageTime
    var storageRemarks by WmsArtifactStocks.storageRemarks
    var saveStatus by WmsArtifactStocks.saveStatus
}






