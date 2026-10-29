package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.CollectionDao
import io.hizhengfu.cloud.collection.dao.Collections
import io.hizhengfu.cloud.collection.dao.InventoryBatchDao
import io.hizhengfu.cloud.collection.dao.InventoryBatches
import io.hizhengfu.cloud.collection.dao.InventoryDao
import io.hizhengfu.cloud.collection.dao.InventoryVo
import io.hizhengfu.cloud.collection.dao.Inventories
import io.hizhengfu.cloud.collection.domain.CollectionImportErrorVo
import io.hizhengfu.cloud.collection.domain.CollectionImportTemplateColumnVo
import io.hizhengfu.cloud.collection.domain.EnumCollectionStatus
import io.hizhengfu.cloud.collection.domain.EnumInventoryResult
import io.hizhengfu.cloud.collection.domain.InventoryBatchSummaryVo
import io.hizhengfu.cloud.collection.domain.InventoryRfidCheckResultVo
import io.hizhengfu.cloud.collection.domain.InventoryRfidCheckVo
import io.hizhengfu.cloud.collection.domain.InventoryImportCommitResultVo
import io.hizhengfu.cloud.collection.domain.InventoryImportRowVo
import io.hizhengfu.cloud.collection.domain.InventoryImportValidateResultVo
import io.hizhengfu.cloud.common.domain.GenericPage
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.flow.domain.FlowCompletedEvent
import io.hizhengfu.cloud.flow.service.FlowInstanceService
import io.hizhengfu.cloud.flow.service.FlowService
import io.hizhengfu.cloud.flow.service.FlowUserOperation
import jakarta.servlet.http.HttpServletRequest
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

data class InventoryCollectionInfo(
    val totalRegNo: String?,
    val name: String?,
)

/** RFID 盘点比对：应盘（已绑标签）∩ 扫到 → 相符；应盘 ∖ 扫到 → 盘亏；扫到 ∖ 应盘 → 状态异常。 */
internal object RfidCheckPlanner {
    fun plan(expectedTagged: Set<String>, scanned: Set<String>): Map<String, EnumInventoryResult> {
        val results = LinkedHashMap<String, EnumInventoryResult>()
        expectedTagged.forEach { cid ->
            results[cid] = if (scanned.contains(cid)) EnumInventoryResult.MATCH else EnumInventoryResult.MISSING
        }
        scanned.filterNot { expectedTagged.contains(it) }.forEach { cid ->
            results[cid] = EnumInventoryResult.ABNORMAL
        }
        return results
    }
}

object InventoryCollectionInfoEnricher {
    fun enrich(items: List<InventoryVo>, collectionInfoById: Map<String, InventoryCollectionInfo>): List<InventoryVo> =
        items.map { item ->
            val info = item.collectionId?.let { collectionInfoById[it] }
            item.copy(
                collectionTotalRegNo = info?.totalRegNo,
                collectionName = info?.name,
            )
        }
}

/** 盘点清查（账物核对，盘亏→藏品 MISSING）。 */
@Component
@Transactional
class InventoryService(
    flowInstanceService: FlowInstanceService,
    private val scope: CollectionScopeResolver,
    private val collectionService: CollectionService,
    private val attachmentBinder: BusinessAttachmentBinder,
    private val batchService: InventoryBatchService,
    private val operationLockService: CollectionOperationLockService,
    private val compliance: InventoryComplianceService,
    private val originalFiles: io.hizhengfu.cloud.collection.service.CollectionBusinessOriginalFiles? = null,
) : FlowService<InventoryDao>(InventoryDao, flowInstanceService, CollectionConst.CACHE) {

    override fun businessType() = CollectionConst.BIZ_INVENTORY
    override fun definitionKey() = CollectionConst.FLOW_INVENTORY
    override fun businessTypeName() = "藏品盘点"
    override fun authorizeUserOperation(entity: InventoryDao, operation: FlowUserOperation) {
        if(operation==FlowUserOperation.FLOW_INFO) OrgScopedReadPolicy.requireVisible(scope.inventoryReadAccess().orgIds,entity.orgId,"盘点流程资料")
        else CollectionCurrentOperationAccess.require(scope, entity.orgId, true, setOf(CollectionConst.STORAGE), "盘点记录")
    }
    override fun buildTitle(entity: InventoryDao) = "藏品盘点：${entity.batchName ?: entity.collectionId ?: entity.id.value}"
    override fun buildFormData(entity: InventoryDao) = mapOf(
        "batchName" to entity.batchName,
        "collectionId" to entity.collectionId,
        "result" to entity.result?.name,
        "note" to entity.note,
    )

    protected override fun acquireApprovalResources(entity: InventoryDao) =
        operationLockService.acquire(entity.orgId, businessType(), entity.id.value, listOfNotNull(entity.collectionId))

    protected override fun releaseApprovalResources(entity: InventoryDao) =
        operationLockService.release(businessType(), entity.id.value)

    override fun beforeSubmit(entity: InventoryDao) = compliance.requireActualCheck(entity)

    override fun beforeFlowReentry(entity: InventoryDao, targetNodeKey: String) {
        if (targetNodeKey != "approve") throw BadRequestException("盘点重新审批须从保管部门审核开始")
        compliance.requireActualCheck(entity)
    }

    fun findPage(request: HttpServletRequest): GenericPage<InventoryVo> {
        val page = GenericPage<InventoryVo>().build(request)
        val access=scope.inventoryReadAccess()
        val q = scopedQuery(access)
        val filters = InventoryQueryFilters.from { request.getParameter(it) }
        applyFilters(q, filters, access)
        q.orderBy(Inventories.createTime to SortOrder.DESC)
        @Suppress("UNCHECKED_CAST")
        val daoPage = findPage(q, page as GenericPage<InventoryDao>)
        page.list = daoPage.data?.map { it.toModel() }?.let { enrichCollectionInfo(it) }
        @Suppress("UNCHECKED_CAST")
        return page as GenericPage<InventoryVo>
    }

    fun batchSummaries(request: HttpServletRequest): List<InventoryBatchSummaryVo> {
        val access=scope.inventoryReadAccess()
        val q = scopedQuery(access)
        applyFilters(q, InventoryQueryFilters.from { request.getParameter(it) }, access)
        q.orderBy(Inventories.checkedAt to SortOrder.DESC)
        return InventoryBatchSummaryBuilder.build(enrichCollectionInfo(InventoryDao.wrapRows(q).map { it.toModel() }))
    }

    fun save(vo: InventoryVo): String {
        val result = vo.result ?: throw BadRequestException("盘点结果必填")
        val collectionId = vo.collectionId?.takeIf { it.isNotBlank() }
        val coll = collectionId?.let { CollectionDao.findById(it) ?: throw BadRequestException("藏品不存在") }
        if (result != EnumInventoryResult.SURPLUS && coll == null) throw BadRequestException("藏品必填")
        val note = vo.note?.trim()?.ifBlank { null }
        if (result == EnumInventoryResult.SURPLUS && coll == null && note == null) {
            throw BadRequestException("盘盈记录请填写实物说明")
        }
        val orgId = coll?.orgId ?: scope.currentOrgId()
        CollectionCurrentOperationAccess.require(scope, orgId, true, setOf(CollectionConst.STORAGE), "盘点记录")
        val name = vo.batchName?.trim()?.takeIf(String::isNotBlank) ?: throw BadRequestException("盘点批次必填")
        val existing = vo.id?.takeIf(String::isNotBlank)?.let { id ->
            InventoryDao.find { Inventories.id eq id }.forUpdate().singleOrNull()
                ?.also { it.refresh(flush = false) } ?: throw BadRequestException("盘点记录不存在")
        }
        if (existing != null) {
            CollectionCurrentOperationAccess.require(scope, existing.orgId, true, setOf(CollectionConst.STORAGE), "盘点记录")
            if (existing.orgId != orgId) throw BadRequestException("不能更改盘点记录的收藏单位，请在实际所属单位另建记录")
        }
        val configuredBatch = lockObservationBatches(existing, orgId, name)
        if (coll != null && configuredBatch != null) {
            val configuredIds = splitConfiguredIds(configuredBatch.collectionIds)
            if (configuredIds.isNotEmpty() && collectionId !in configuredIds) {
                throw BadRequestException("该藏品不在盘点批次指定的应盘范围内")
            }
            if (!configuredBatch.warehouseId.isNullOrBlank() && coll.warehouseId != configuredBatch.warehouseId) {
                throw BadRequestException("该藏品不在盘点批次指定的目标库位内")
            }
        }
        val d = saveDraft(vo.id, "盘点记录不存在", createEntity = {
            dao.new { this.orgId = orgId }
        }) {
            existing?.let { originalFiles?.rememberExistingBusiness(CollectionConst.BIZ_INVENTORY, it.id.value) }
            val batch = configuredBatch ?: InventoryBatchDao.new {
                this.orgId = orgId
                batchName = name
            }
            batchId = batch.id.value
            batchName = batch.batchName
            this.collectionId = collectionId
            this.result = result
            this.note = note
            attachmentIds = vo.attachmentIds
            checkedAt = vo.checkedAt ?: checkedAt ?: LocalDateTime.now()
        }
        originalFiles?.remember(CollectionConst.BIZ_INVENTORY, d.id.value)
        BusinessAttachmentBindingPlanner.plan(d.id.value, "盘点材料附件", d.attachmentIds).forEach { item ->
            if (originalFiles?.bindRetainedBusinessReference(CollectionConst.BIZ_INVENTORY, d.id.value, d.orgId, item.fileId, item.aliasName) != true)
                attachmentBinder.bindSingle(d.id.value, item.aliasName, item.fileId)
        }
        return d.id.value
    }

    /** 与签署共用批次行锁；按固定顺序锁定原、目标批次，不能只检查请求中的新名称。 */
    private fun lockObservationBatches(existing: InventoryDao?, orgId: String, name: String): InventoryBatchDao? {
        fun named(value: String?): String? {
            if (value.isNullOrBlank()) return null
            val matches = InventoryBatchDao.find {
                (InventoryBatches.orgId eq orgId) and (InventoryBatches.batchName eq value)
            }.map { it.id.value }
            if (matches.size > 1) throw BadRequestException("盘点批次名称存在歧义，请核对历史记录")
            return matches.singleOrNull()
        }
        val sourceId = existing?.let {
            it.batchId?.takeIf(String::isNotBlank) ?: named(it.batchName)
                ?: throw BadRequestException("原盘点批次无法确认，请核对历史记录")
        }
        val targetId = named(name)
        val ids = listOfNotNull(sourceId, targetId).distinct().sorted()
        val locked = if (ids.isEmpty()) emptyMap() else InventoryBatchDao.find { InventoryBatches.id inList ids }
            .orderBy(InventoryBatches.id to SortOrder.ASC).forUpdate().toList()
            .onEach { it.refresh(flush = false) }.associateBy { it.id.value }
        if (locked.size != ids.size) throw BadRequestException("盘点批次不存在，请核对历史记录")
        locked.values.forEach {
            if (it.orgId != orgId) throw BadRequestException("盘点记录与批次所属收藏单位不一致")
            compliance.requireObservationWritable(it)
        }
        if (existing != null && existing.batchId.isNullOrBlank() && locked[sourceId]?.batchName != existing.batchName) {
            throw BadRequestException("原盘点批次名称已变化，请核对历史记录")
        }
        return targetId?.let { locked.getValue(it).also { batch ->
            if (batch.batchName != name) throw BadRequestException("目标盘点批次已变化，请重新选择")
        } }
    }

    fun create(vo: InventoryVo): String = save(vo)

    /**
     * RFID 批量盘点：以「本馆在库且已绑标签」为应盘清单（可选限定库位），与扫描标签比对后
     * 批量生成盘点草稿行（相符/盘亏/状态异常）；未绑标签藏品与未知标签只进摘要不建行。
     */
    fun rfidCheck(vo: InventoryRfidCheckVo): InventoryRfidCheckResultVo {
        val batchName = vo.batchName?.trim()?.takeIf { it.isNotBlank() } ?: throw BadRequestException("盘点批次必填")
        val tags = vo.tags.mapNotNull { it.trim().takeIf(String::isNotEmpty) }.distinct()
        if (tags.isEmpty()) throw BadRequestException("请提供扫描到的标签")
        val orgId = scope.currentOrgId()
        val configuredBatch = batchService.findConfig(orgId, batchName)
        val configuredIds = splitConfiguredIds(configuredBatch?.collectionIds)

        val expectedQ = Collections.selectAll()
            .where { (Collections.orgId eq orgId) and (Collections.lifeStatus eq EnumCollectionStatus.IN_STORE) }
        if (configuredIds.isNotEmpty()) {
            expectedQ.andWhere { Collections.id inList configuredIds }
        } else {
            (vo.warehouseId?.trim()?.takeIf { it.isNotBlank() } ?: configuredBatch?.warehouseId)
                ?.let { expectedQ.andWhere { Collections.warehouseId eq it } }
        }
        var untagged = 0
        val expectedTagged = HashSet<String>()
        expectedQ.forEach {
            if (it[Collections.rfidTag].isNullOrBlank()) untagged += 1
            else expectedTagged += it[Collections.id].value
        }

        val scannedByCollection = Collections.selectAll()
            .where { (Collections.orgId eq orgId) and (Collections.rfidTag inList tags) }
            .associate { it[Collections.id].value to it[Collections.rfidTag] }
        val knownTags = scannedByCollection.values.filterNotNull().toSet()

        val plan = RfidCheckPlanner.plan(expectedTagged, scannedByCollection.keys)
        plan.forEach { (cid, result) ->
            val note = when (result) {
                EnumInventoryResult.MISSING -> "RFID盘点：应在库未扫描到"
                EnumInventoryResult.ABNORMAL -> "RFID盘点：扫描到但不在应盘范围（状态/库位异常）"
                else -> "RFID盘点：账物相符"
            }
            save(InventoryVo(batchName = batchName, collectionId = cid, result = result, note = note))
        }
        return InventoryRfidCheckResultVo(
            batchName = batchName,
            createdCount = plan.size,
            matchCount = plan.values.count { it == EnumInventoryResult.MATCH },
            missingCount = plan.values.count { it == EnumInventoryResult.MISSING },
            abnormalCount = plan.values.count { it == EnumInventoryResult.ABNORMAL },
            untaggedCount = untagged,
            unknownTags = tags.filterNot { knownTags.contains(it) },
        )
    }

    override fun onFlowCompleted(entity: InventoryDao, event: FlowCompletedEvent) {
        compliance.requireActualCheck(entity)
        super.onFlowCompleted(entity, event)
        // 清点结果是调查线索；生命周期更改应凭独立事故调查、批准和处理材料办理。
    }

    fun importTemplate(): List<CollectionImportTemplateColumnVo> = listOf(
        CollectionImportTemplateColumnVo("rowNo", "行号", sample = "2"),
        CollectionImportTemplateColumnVo("batchName", "盘点批次", sample = "2026年度库房清查"),
        CollectionImportTemplateColumnVo("totalRegNo", "总登记号", sample = "000001，盘盈无台账藏品时可留空"),
        CollectionImportTemplateColumnVo("collectionId", "藏品ID", sample = "可选，优先于总登记号；盘盈无台账藏品时可留空"),
        CollectionImportTemplateColumnVo("result", "盘点结果", required = true, dictType = "col_inventory_result", sample = "账物相符"),
        CollectionImportTemplateColumnVo("note", "备注", sample = "盘盈未绑定藏品时填写实物说明"),
        CollectionImportTemplateColumnVo("attachmentNames", "附件文件名", sample = "盘点照片.jpg；盘点报告.pdf"),
    )

    fun validateImport(rows: List<InventoryImportRowVo>): InventoryImportValidateResultVo {
        val (ids, byRegNo) = visibleCollectionIndex()
        val errors = mutableListOf<CollectionImportErrorVo>()
        val normalized = rows.mapIndexed { index, row ->
            try { row.copy(attachmentIds = attachmentBinder.resolveImportNames(row.attachmentNames, row.attachmentIds)) }
            catch (e: BadRequestException) {
                errors += CollectionImportErrorVo(row.rowNo.takeIf { it > 0 } ?: index + 2, "attachmentNames", e.message ?: "附件无效")
                row
            }
        }
        val result = InventoryImportValidator.validateRows(normalized, ids, byRegNo)
        return if (errors.isEmpty()) result else result.copy(valid = false, errors = result.errors + errors, items = emptyList())
    }

    @Transactional
    fun commitImport(rows: List<InventoryImportRowVo>): InventoryImportCommitResultVo {
        val result = validateImport(rows)
        if (!result.valid) return InventoryImportCommitResultVo(valid = false, errors = result.errors)
        val ids = result.items.map { create(it) }
        return InventoryImportCommitResultVo(valid = true, createdIds = ids)
    }

    private fun visibleCollectionIndex(): Pair<Set<String>, Map<String, String>> {
        val q = Collections.selectAll()
        val orgId = scope.inventoryReadAccess().currentOrgId(scope)
        q.andWhere { Collections.orgId eq orgId }
        val ids = HashSet<String>()
        val byRegNo = HashMap<String, String>()
        q.forEach {
            val id = it[Collections.id].value
            ids += id
            it[Collections.totalRegNo].takeIf { regNo -> regNo.isNotBlank() }?.let { regNo ->
                if (byRegNo.put(regNo, id) != null) throw BadRequestException("当前收藏单位总登记号「$regNo」不唯一，请先核实原账")
            }
        }
        return ids to byRegNo
    }

    private fun splitConfiguredIds(value: String?): Set<String> =
        value.orEmpty().split(',').map { it.trim() }.filter { it.isNotBlank() }.toSet()

    private fun enrichCollectionInfo(items: List<InventoryVo>): List<InventoryVo> {
        val ids = items.mapNotNull { it.collectionId }.toSet()
        if (ids.isEmpty()) return items
        val infoByOrg = Collections.selectAll().where { Collections.id inList ids }
            .groupBy { it[Collections.orgId] }.mapValues { (_,rows) -> rows.associate {
                it[Collections.id].value to InventoryCollectionInfo(totalRegNo=it[Collections.totalRegNo],name=it[Collections.name])
            } }
        return items.map { InventoryCollectionInfoEnricher.enrich(listOf(it),infoByOrg[it.orgId].orEmpty()).single() }
    }

    private fun scopedQuery(access:CollectionSearchReadAccess): Query {
        val q = Inventories.selectAll()
        q.andWhere { Inventories.orgId inList access.orgIds }
        return q
    }

    private fun applyFilters(q: Query, filters: InventoryQueryFilters, access:CollectionSearchReadAccess) {
        filters.keyword?.let { kw ->
            val collectionMatch = collectionIdsMatchingKeyword(kw, access)
            q.andWhere {
                (Inventories.batchName like "%$kw%") or
                    (Inventories.collectionId like "%$kw%") or
                    collectionMatch or
                    (Inventories.note like "%$kw%") or
                    (Inventories.attachmentIds like "%$kw%")
            }
        }
        filters.batchName?.let { q.andWhere { Inventories.batchName like "%$it%" } }
        filters.collectionId?.let { q.andWhere { Inventories.collectionId eq it } }
        filters.result?.let { q.andWhere { Inventories.result eq it } }
        filters.flowStatus?.let { q.andWhere { Inventories.flowStatus eq it } }
        filters.hasAttachment?.let { has ->
            if (has) q.andWhere { Inventories.attachmentIds.isNotNull() and Inventories.attachmentIds.neq("") }
            else q.andWhere { Inventories.attachmentIds.isNull() or Inventories.attachmentIds.eq("") }
        }
        filters.checkedAtStart?.let { q.andWhere { Inventories.checkedAt greaterEq it } }
        filters.checkedAtEnd?.let { q.andWhere { Inventories.checkedAt lessEq it } }
    }

    private fun collectionIdsMatchingKeyword(keyword: String, access:CollectionSearchReadAccess): Op<Boolean> {
        val q = Collections.selectAll().where { Collections.orgId inList access.orgIds }
        q.andWhere { (Collections.totalRegNo like "%$keyword%") or (Collections.name like "%$keyword%") }
        var condition:Op<Boolean> = Op.FALSE
        q.groupBy { it[Collections.orgId] }.forEach { (org,rows) ->
            condition = condition or ((Inventories.orgId eq org) and (Inventories.collectionId inList rows.map { it[Collections.id].value }))
        }
        return condition
    }
}
