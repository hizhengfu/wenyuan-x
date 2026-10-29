package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.CollectionRetainedLedgerDao
import io.hizhengfu.cloud.collection.dao.CollectionRetainedLedgers
import io.hizhengfu.cloud.collection.dao.CollectionTransferDao
import io.hizhengfu.cloud.collection.dao.CollectionTransfers
import io.hizhengfu.cloud.collection.dao.CollectionVo
import io.hizhengfu.cloud.collection.dao.StorageMovementDao
import io.hizhengfu.cloud.collection.dao.StorageMovements
import io.hizhengfu.cloud.collection.dao.WarehouseDao
import io.hizhengfu.cloud.collection.domain.EnumMoveType
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.extend.toMillis
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import java.time.LocalDateTime

/** 历史业务仍属原馆时，只从本馆确实转出的封存总账恢复参考属性。 */
internal class CollectionHistoricalPrintSource(
    private val scope: CollectionScopeResolver,
    private val archives: CollectionPermanentArchiveService?,
    private val current: CollectionPrintSourceReader,
) {
    data class Resolved(val collection: CollectionVo, val source: String? = null, val transferId: String? = null)

    fun resolve(collectionId: String, orgId: String, occurredAt: LocalDateTime?,
        executedBusinessType: String? = null, executedBusinessId: String? = null): Resolved {
        if(executedBusinessId != null) {
            val movement=StorageMovementDao.find {
                (StorageMovements.orgId eq orgId) and (StorageMovements.collectionId eq collectionId) and
                    (StorageMovements.refBizId eq executedBusinessId) and (StorageMovements.type eq EnumMoveType.OUT)
            }.singleOrNull()
            movement?.let { CollectionMovementFacts.read(it.toModel(),archives) }?.let { facts ->
                if(occurredAt != facts.movement.operatedAt) throw BadRequestException("实际出库时间与逐件冻结事实不一致")
                return Resolved(facts.collection,"藏品 $collectionId 的属性取本单出库执行登记时冻结事实")
            }
        }
        val transfers = CollectionTransferDao.find { CollectionTransfers.collectionId eq collectionId }
            .filter { it.executedAt != null }
            .sortedWith(compareBy<CollectionTransferDao> { it.executedAt }.thenBy { it.id.value })
        if (occurredAt != null) {
            val prior = transfers.filter { !it.executedAt!!.isAfter(occurredAt) }
            if (prior.isNotEmpty()) {
                val last = prior.last()
                if (last.executedAt == occurredAt ||
                    (prior.size > 1 && prior[prior.lastIndex - 1].executedAt == last.executedAt))
                    throw BadRequestException("历史业务发生时存在无法区分的转馆时序")
                if (last.toOrgId != orgId)
                    throw BadRequestException("历史业务发生时藏品不属于本业务馆，不能套用后续原馆账")
            }
        }
        val departures = transfers.filter { it.orgId == orgId && occurredAt != null && !it.executedAt!!.isBefore(occurredAt) }
        if (departures.isEmpty()) {
            val owner = current.collection(collectionId, orgId)
                ?: throw BadRequestException("打印关联藏品不存在，请核实原记录")
            return Resolved(owner,"旧业务未保存完整的执行时著录快照；以下属性来自当前本馆主档，仅供参考，须对照原件确认")
        }
        if (departures.size > 1 && departures[0].executedAt == departures[1].executedAt)
            throw BadRequestException("历史业务之后存在同时执行的多笔转馆，不能推定唯一原馆账")
        val transfer = departures.first()
        if (transfer.executedAt == occurredAt)
            throw BadRequestException("历史业务与转馆同时发生，不能推定原馆账时序")
        val retained = CollectionRetainedLedgerDao.find {
            (CollectionRetainedLedgers.transferId eq transfer.id.value) and
                (CollectionRetainedLedgers.orgId eq orgId) and
                (CollectionRetainedLedgers.collectionId eq collectionId)
        }.singleOrNull() ?: throw BadRequestException("历史业务缺少对应本次转馆的原馆留存总账")
        if (transfer.fromOrgId != orgId || transfer.toOrgId != retained.targetOrgId)
            throw BadRequestException("历史业务转馆案卷与原馆留存总账不一致")
        val archiveService = archives ?: throw BadRequestException("历史原账核验服务未配置")
        val saved = CollectionRetainedLedgerService(scope, archiveService)
            .verifiedBoundSnapshotForReader(retained.id.value, orgId,
                setOf(CollectionConst.STORAGE), CollectionConst.CROSS_ORG).collection
        if (saved.id != collectionId || saved.orgId != orgId)
            throw BadRequestException("历史原馆留存账与业务藏品不一致")

        if (executedBusinessType == null || executedBusinessId == null)
            return Resolved(saved.copy(warehouseId = null, warehouseName = null),
                "藏品 $collectionId 的登记号及其他属性取随后转馆时的原馆冻结留存账 ${transfer.id.value}；不代表本业务发生时状态，须对照本业务原件", transfer.id.value)

        val execution = archiveService.phaseForAuthorizedOrg(executedBusinessType, executedBusinessId, orgId, "OUT_EXECUTED")
            ?: throw BadRequestException("历史提用或借用缺少本单已执行出库永久档案")
        val root = try { JsonUtils.mapper.readTree(execution["snapshot"] as String) }
            catch (_: tools.jackson.core.JacksonException) { throw BadRequestException("历史出库永久档案内容无法解析") }
        val request = root.path("request")
        if (request.path("id").asString() != executedBusinessId || request.path("orgId").asString() != orgId ||
            collectionId !in splitCollectionIds(request.path("collectionIds").asString()))
            throw BadRequestException("历史出库永久档案与本单藏品关联不一致")
        val sealedTime = request.path("outExecutedTime")
        if (!request.path("outExecuted").asBoolean() || occurredAt == null ||
            !sealedTime.isIntegralNumber || !sealedTime.canConvertToLong() || sealedTime.longValue() != occurredAt.toMillis())
            throw BadRequestException("历史出库永久档案与本单实际出库时间不一致")
        val movements = StorageMovementDao.find {
            (StorageMovements.orgId eq orgId) and (StorageMovements.collectionId eq collectionId) and
                (StorageMovements.refBizId eq executedBusinessId) and (StorageMovements.type eq EnumMoveType.OUT)
        }.toList()
        val movement = movements.singleOrNull() ?: throw BadRequestException("历史提用或借用缺少唯一逐件出库流水")
        if (movement.operatedAt != occurredAt)
            throw BadRequestException("历史出库流水与本单实际出库时间不一致")
        if (movement.operatedAt.isAfter(transfer.executedAt))
            throw BadRequestException("历史出库流水晚于原馆转馆，时间顺序不一致")
        val locationId = movement.fromWarehouseId
        val location = locationId?.let(WarehouseDao::findById)
        if (location != null && location.orgId != orgId)
            throw BadRequestException("历史出库库位不属于原馆")
        val numberAtMovement = movement.totalRegNoAtMovement?.takeIf(String::isNotBlank)
        val nameAtMovement = movement.collectionNameAtMovement?.takeIf(String::isNotBlank)
        val identitySource = if (numberAtMovement != null && nameAtMovement != null)
            "总登记号、名称取本单逐件流水"
        else
            "旧流水未完整留存当时身份，缺失的登记号或名称取随后转馆时的原馆冻结留存账，不能认定为本单发生时值"
        return Resolved(saved.copy(
            totalRegNo = numberAtMovement ?: saved.totalRegNo,
            name = nameAtMovement ?: saved.name,
            warehouseId = locationId,
            warehouseName = location?.let { "${it.name}（$locationId）" } ?: locationId,
        ), "藏品 $collectionId 的$identitySource；可识别的出库位取本单流水，其他属性取随后转馆时的原馆冻结留存账 ${transfer.id.value}，须对照本单原件", transfer.id.value)
    }
}
