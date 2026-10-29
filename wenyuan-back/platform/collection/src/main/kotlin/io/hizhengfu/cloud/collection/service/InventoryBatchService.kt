package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.InventoryBatchDao
import io.hizhengfu.cloud.collection.dao.InventoryBatches
import io.hizhengfu.cloud.collection.dao.InventoryBatchVo
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

data class InventoryBatchDecision(
    val orgId: String,
    val batchName: String,
    val batchId: String?,
    val reuseExisting: Boolean,
)

object InventoryBatchPlanner {
    fun resolve(
        orgId: String,
        batchName: String?,
        existingBatchIdsByName: Map<String, String>,
    ): InventoryBatchDecision {
        val clean = batchName?.trim()?.takeIf { it.isNotBlank() }
            ?: throw BadRequestException("盘点批次必填")
        val existingId = existingBatchIdsByName[clean]
        return InventoryBatchDecision(
            orgId = orgId,
            batchName = clean,
            batchId = existingId,
            reuseExisting = existingId != null,
        )
    }
}

@Component
@Transactional
class InventoryBatchService(
    private val scope: CollectionScopeResolver,
    private val attachmentBinder: BusinessAttachmentBinder,
    private val archives: CollectionPermanentArchiveService,
    private val originalFiles: io.hizhengfu.cloud.collection.service.CollectionBusinessOriginalFiles? = null,
) {
    private val evidence = InventoryArchiveEvidence(archives)

    fun list(): List<InventoryBatchVo> {
        val ids = scope.inventoryReadAccess().orgIds
        val rows = InventoryBatchDao.find { InventoryBatches.orgId inList ids }.toList()
        return rows.sortedByDescending { it.startedAt }.map {
            val errors=evidence.errors(it).toMutableList()
            archives.inventoryOriginals(it.orgId,InventoryComplianceService.BIZ,it.id.value,errors)
            it.toModel().copy(evidenceErrors=errors)
        }
    }

    fun findConfig(orgId: String, batchName: String): InventoryBatchVo? =
        InventoryBatchDao.find {
            (InventoryBatches.orgId eq orgId) and (InventoryBatches.batchName eq batchName)
        }.firstOrNull()?.toModel()

    fun save(vo: InventoryBatchVo): String {
        val name = vo.batchName?.trim()?.takeIf { it.isNotBlank() } ?: throw BadRequestException("盘点批次必填")
        if (vo.cycleMonths != null && vo.cycleMonths <= 0) throw BadRequestException("盘点周期必须大于 0 个月")
        val owningOrgId = vo.id?.let { InventoryBatchDao.findById(it)?.orgId ?: throw BadRequestException("盘点批次不存在") } ?: scope.currentOrgId()
        CollectionCurrentOperationAccess.require(scope, owningOrgId, true, setOf(CollectionConst.STORAGE), "盘点批次")
        if (name.length > 100) throw BadRequestException("盘点批次名称不得超过100字")
        CollectionLocationMutation.acquire(owningOrgId)
        val targetWarehouseId = CollectionLocationMutation.storageReference(owningOrgId, vo.warehouseId)
        val lockedRow = vo.id?.let { id -> InventoryBatches.selectAll().where { InventoryBatches.id eq id }.forUpdate().singleOrNull() ?: throw BadRequestException("盘点批次不存在") }
        if (lockedRow?.get(InventoryBatches.plan) != null) throw BadRequestException("已开始清点的批次范围、报告和时间须保留，请通过实际核验及签署入口办理")
        val duplicate = InventoryBatchDao.find { (InventoryBatches.orgId eq owningOrgId) and (InventoryBatches.batchName eq name) }
            .any { it.id.value != vo.id }
        if (duplicate) throw BadRequestException("盘点批次名称已存在")
        val entity = lockedRow?.let(InventoryBatchDao::wrapRow) ?: InventoryBatchDao.new { orgId = owningOrgId; batchName = name }
        if (entity.plan != null) throw BadRequestException("已开始清点的批次范围、报告和时间须保留，请通过实际核验及签署入口办理")
        CollectionCurrentOperationAccess.require(scope, entity.orgId, true, setOf(CollectionConst.STORAGE), "盘点批次")
        evidence.verify(entity)
        if (vo.id != null) originalFiles?.rememberExistingBusiness(InventoryComplianceService.BIZ, entity.id.value)
        entity.apply {
            batchName = name
            scopeDesc = vo.scopeDesc
            cycleMonths = vo.cycleMonths
            warehouseId = targetWarehouseId
            collectionIds = vo.collectionIds?.split(',')?.map { it.trim() }?.filter { it.isNotBlank() }?.distinct()?.joinToString(",")
            startedAt = vo.startedAt
            finishedAt = vo.finishedAt
            reportAttachmentIds = vo.reportAttachmentIds
            remark = vo.remark
        }
        originalFiles?.remember(InventoryComplianceService.BIZ, entity.id.value)
        BusinessAttachmentBindingPlanner.plan(entity.id.value, "盘点批次报告", entity.reportAttachmentIds).forEach { item ->
            if (originalFiles?.bindRetainedBusinessReference(InventoryComplianceService.BIZ, entity.id.value, entity.orgId, item.fileId, item.aliasName) != true)
                attachmentBinder.bindSingle(entity.id.value, item.aliasName, item.fileId)
        }
        return entity.id.value
    }

    fun ensure(orgId: String, batchName: String?): InventoryBatchDecision {
        CollectionLocationMutation.acquire(orgId)
        val existing = InventoryBatchDao.find { InventoryBatches.orgId eq orgId }
            .associate { it.batchName to it.id.value }
        val decision = InventoryBatchPlanner.resolve(orgId, batchName, existing)
        if (decision.batchName.length > 100) throw BadRequestException("盘点批次名称不得超过100字")
        if (decision.reuseExisting) return decision
        val batch = InventoryBatchDao.new {
            this.orgId = orgId
            this.batchName = decision.batchName
        }
        return decision.copy(batchId = batch.id.value)
    }
}
