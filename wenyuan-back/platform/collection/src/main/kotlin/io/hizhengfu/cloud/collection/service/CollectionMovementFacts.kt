package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import java.time.LocalDateTime

data class CollectionMovementFactsVo(
    val movement: MovementVo,
    val collection: CollectionVo,
    val fromWarehouseName: String?,
    val toWarehouseName: String?,
    val capturedAt: LocalDateTime,
)

/** 出入库执行时冻结完整身份与著录。业务原件仍由原提用、借用、归还、调拨案卷核验。 */
internal object CollectionMovementFacts {
    const val PHASE = "MOVEMENT_FACTS"
    fun capture(row: StorageMovementDao, archives: CollectionPermanentArchiveService) {
        val c=CollectionDao.findById(row.collectionId) ?: throw BadRequestException("出入库藏品不存在")
        if(c.orgId != row.orgId || c.totalRegNo != row.totalRegNoAtMovement || c.name != row.collectionNameAtMovement)
            throw BadRequestException("出入库身份与执行时主档不一致")
        val model=c.toModel()
        model.categoryName=c.categoryId?.let(CollectionCategoryDao::findById)?.name
        val facts=CollectionMovementFactsVo(row.toModel(),model.copy(coverImage=null,
            warehouseId=if(row.type==io.hizhengfu.cloud.collection.domain.EnumMoveType.IN) row.toWarehouseId else row.fromWarehouseId),
            row.fromWarehouseId?.let(WarehouseDao::findById)?.name,row.toWarehouseId?.let(WarehouseDao::findById)?.name,LocalDateTime.now())
        archives.seal(row.orgId,CollectionBusinessOriginalFileSource.MOVEMENT,row.id.value,PHASE,facts,null)
    }

    fun read(row: MovementVo, archives: CollectionPermanentArchiveService?): CollectionMovementFactsVo? {
        val id=row.id ?: throw BadRequestException("出入库记录标识缺失")
        val org=row.orgId ?: throw BadRequestException("出入库记录所属馆缺失")
        val service=archives ?: return null
        val archive=service.phaseForAuthorizedOrg(CollectionBusinessOriginalFileSource.MOVEMENT,id,org,PHASE) ?: return null
        val original=archive["snapshot"] as? String ?: throw BadRequestException("出入库冻结事实缺失")
        val facts=JsonUtils.fromJson(original,CollectionMovementFactsVo::class.java) ?: throw BadRequestException("出入库冻结事实无法解析")
        if(facts.movement != row || facts.collection.id != row.collectionId || facts.collection.orgId != org ||
            facts.collection.totalRegNo != row.totalRegNo || facts.collection.name != row.collectionName)
            throw BadRequestException("出入库流水与执行时冻结事实不一致，请核对原件")
        return facts
    }
}
