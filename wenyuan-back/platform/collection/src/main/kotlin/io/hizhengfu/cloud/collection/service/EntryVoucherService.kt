package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.AcquisitionDao
import io.hizhengfu.cloud.collection.dao.Acquisitions
import io.hizhengfu.cloud.collection.dao.CollectionVo
import io.hizhengfu.cloud.collection.dao.EntryVoucherDao
import io.hizhengfu.cloud.collection.dao.EntryVoucherVo
import io.hizhengfu.cloud.collection.dao.EntryVouchers
import io.hizhengfu.cloud.collection.dao.StorageMovementDao
import io.hizhengfu.cloud.collection.dao.StorageMovements
import io.hizhengfu.cloud.collection.dao.WarehouseDao
import io.hizhengfu.cloud.collection.domain.AccessionRegistrationFacts
import io.hizhengfu.cloud.collection.domain.EntryVoucherExecuteVo
import io.hizhengfu.cloud.collection.domain.EnumCollectionStatus
import io.hizhengfu.cloud.collection.domain.EnumLocationScene
import io.hizhengfu.cloud.collection.domain.EnumVoucherStatus
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.common.domain.GenericPage
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.service.CrudService
import io.hizhengfu.cloud.common.utils.SecurityUtils
import jakarta.servlet.http.HttpServletRequest
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.dao.entityCache
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.springframework.cache.annotation.CacheConfig
import org.springframework.cache.annotation.CacheEvict
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** 入馆凭证号：RJ-yyyyMMdd-#### 当日馆内连续。 */
internal object EntryVoucherNumberGenerator {
    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE

    fun prefix(today: LocalDate): String = "RJ-${today.format(dateFormatter)}-"

    fun resolve(input: String?, today: LocalDate, existingForDay: Set<String>): String {
        input?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        val prefix = prefix(today)
        val next = (existingForDay.asSequence()
            .mapNotNull { it.trim().takeIf { no -> no.startsWith(prefix) }?.removePrefix(prefix)?.toIntOrNull() }
            .maxOrNull() ?: 0) + 1
        return prefix + next.toString().padStart(4, '0')
    }
}

/** 入馆凭证条目校验：仅「审批通过且未登账、未被其他凭证占用」的征集单可入凭证。 */
internal object EntryVoucherItemPolicy {
    fun requireDistinctIds(value: String?): List<String> {
        val ids = splitCollectionIds(value)
        if (ids.isEmpty()) throw BadRequestException("请选择入凭证的征集单")
        if (ids.distinct().size != ids.size) throw BadRequestException("同一入馆凭证中的征集单不能重复")
        return ids
    }

    fun requireRegistrable(
        acquisitionId: String,
        flowStatus: EnumFlowStatus?,
        collectionId: String?,
        heldByOtherVoucher: Boolean,
    ) {
        if (flowStatus != EnumFlowStatus.PASS) throw BadRequestException("征集单[$acquisitionId]未审批通过，不能入凭证")
        if (!collectionId.isNullOrBlank()) throw BadRequestException("征集单[$acquisitionId]已登账，不能重复入凭证")
        if (heldByOtherVoucher) throw BadRequestException("征集单[$acquisitionId]已在其他入馆凭证中")
    }
}

internal object EntryVoucherExecutionPolicy {
    fun requireExecutable(vo: io.hizhengfu.cloud.collection.dao.EntryVoucherVo) {
        if (vo.basis.isNullOrBlank()) throw BadRequestException("入藏依据必填")
        if (vo.entryDate == null) throw BadRequestException("入馆日期必填")
        if (vo.handoverUser.isNullOrBlank()) throw BadRequestException("移交人必填")
        if (vo.receiverUser.isNullOrBlank()) throw BadRequestException("接收人必填")
        if (vo.custodian.isNullOrBlank()) throw BadRequestException("保管员必填")
        if (vo.warehouseId.isNullOrBlank()) throw BadRequestException("入馆目标库房/库位必填")
        if (vo.attachmentIds.isNullOrBlank()) {
            throw BadRequestException("请先上传移交人、接收人签字后的入馆凭证扫描件")
        }
        val itemIds = splitIds(vo.acquisitionIds)
        val verifiedIds = splitIds(vo.verifiedAcquisitionIds)
        if (verifiedIds != itemIds) {
            throw BadRequestException("请对凭证内藏品逐件逐项核对并全部勾选确认")
        }
    }

    private fun splitIds(value: String?): Set<String> =
        value.orEmpty().split(',').map { it.trim() }.filter { it.isNotBlank() }.toSet()
}

/**
 * 入馆凭证：审批通过的征集单按批次组凭证，execute 依据已签署的逐件核收原件生成待登记记录。
 * 尚无实际核收原件时可改可删；打印《入馆凭证》一式多份线下签字后可回传附件存档。
 */
@Component
@Transactional
@CacheConfig(cacheNames = [CollectionConst.CACHE_ACQUISITION])
class EntryVoucherService(
    private val scope: CollectionScopeResolver,
    private val collectionService: CollectionService,
    private val attachmentBinder: BusinessAttachmentBinder,
    private val printService: CollectionPrintService,
    private val compliance: AcquisitionComplianceService,
    private val receipts: AccessionReceiptService,
    private val originalFiles: io.hizhengfu.cloud.collection.service.CollectionBusinessOriginalFiles? = null,
) : CrudService<EntryVoucherDao>(EntryVoucherDao, CollectionConst.CACHE_ACQUISITION) {

    fun findPage(request: HttpServletRequest): GenericPage<EntryVoucherVo> {
        val page = GenericPage<EntryVoucherVo>().build(request)
        val access = CollectionSearchReadAccess.require(scope, acquisition = true)
        val q = EntryVouchers.selectAll()
        q.andWhere { EntryVouchers.orgId inList access.orgIds }
        request.getParameter("keyword")?.trim()?.takeIf { it.isNotBlank() }?.let { kw ->
            q.andWhere {
                (EntryVouchers.voucherNo like "%$kw%") or
                    (EntryVouchers.basis like "%$kw%") or
                    (EntryVouchers.remark like "%$kw%")
            }
        }
        request.getParameter("voucherStatus")?.trim()?.takeIf { it.isNotBlank() }?.let { value ->
            runCatching { EnumVoucherStatus.valueOf(value) }.getOrNull()
                ?.let { q.andWhere { EntryVouchers.voucherStatus eq it } }
        }
        q.orderBy(EntryVouchers.createTime to SortOrder.DESC)
        @Suppress("UNCHECKED_CAST")
        val daoPage = findPage(q, page as GenericPage<EntryVoucherDao>)
        page.list = daoPage.data?.map { it.toModel() }?.let(::enrich)
        @Suppress("UNCHECKED_CAST")
        return page as GenericPage<EntryVoucherVo>
    }

    fun get(id: String): EntryVoucherVo? = scopedFind(id).toModel().let { enrich(listOf(it)).first() }

    fun print(id: String) = printService.entryVoucherPrint(id)

    @CacheEvict(allEntries = true)
    fun save(vo: EntryVoucherVo): String {
        val itemIds = EntryVoucherItemPolicy.requireDistinctIds(vo.acquisitionIds)
        val node = vo.id?.takeIf { it.isNotBlank() }?.let { scopedFind(it, writable = true) }
        if (node != null && (node.voucherStatus != EnumVoucherStatus.DRAFT || receipts.hasRecords(node.id.value))) {
            throw BadRequestException("凭证已有实际核收记录，不能覆盖；请在核收清册中留存补充或订正原件")
        }
        val acquisitions = itemIds.map {
            AcquisitionDao.findById(it) ?: throw BadRequestException("征集单不存在：$it")
        }
        val org = acquisitions.first().orgId
        if (acquisitions.any { it.orgId != org }) throw BadRequestException("同一凭证的征集单必须属于同一收藏单位")
        CollectionCurrentOperationAccess.require(scope,org,true,setOf(CollectionConst.ACQUIRE),"入馆凭证")
        if (node != null && node.orgId != org) throw BadRequestException("已有入馆凭证不能改换收藏单位")
        // 位置维护、凭证组单和馆内编号在同一事务顺序内核验；等待后不能沿用旧占用或征集状态。
        CollectionLocationMutation.acquire(org)
        // 指定接收可在同一事务中新建征集单；统一落库后直接读取SQL行，避免审计待写实体刷新误报不存在。
        acquisitions.forEach { it.flush() }
        TransactionManager.current().entityCache.flush()
        val currentAcquisitions = itemIds.chunked(200).flatMap { batch ->
            Acquisitions.selectAll().where { Acquisitions.id inList batch }.toList()
        }
        if (currentAcquisitions.size != itemIds.size) throw BadRequestException("征集单已被删除，请重新选择入凭证条目")
        if (currentAcquisitions.any { it[Acquisitions.orgId] != org }) throw BadRequestException("征集单所属收藏单位已变化，请重新核对入馆凭证")
        CollectionCurrentOperationAccess.require(scope,org,true,setOf(CollectionConst.ACQUIRE),"入馆凭证")
        val heldByOthers = heldAcquisitionIds(org, excludeVoucherId = node?.id?.value)
        currentAcquisitions.forEach {
            EntryVoucherItemPolicy.requireRegistrable(
                acquisitionId = it[Acquisitions.id].value,
                flowStatus = it[Acquisitions.flowStatus],
                collectionId = it[Acquisitions.collectionId],
                heldByOtherVoucher = heldByOthers.contains(it[Acquisitions.id].value),
            )
        }
        val warehouseId = vo.warehouseId?.trim()?.takeIf(String::isNotBlank)?.also { warehouseId ->
            val warehouse = CollectionLocationMutation.reference(org, warehouseId) ?: throw BadRequestException("入馆目标库房/库位不存在")
            if (warehouse.orgId != org) throw BadRequestException("入馆目标库房/库位不属于当前收藏单位")
            if (warehouse.sceneType != EnumLocationScene.STORAGE) throw BadRequestException("入馆目标必须是保管库房/库位")
        }
        node?.let { originalFiles?.rememberExistingBusiness(AccessionReceiptService.BIZ,it.id.value) }
        val target = node ?: dao.new {
            orgId = org
            voucherNo = nextVoucherNo(org, vo.voucherNo)
        }
        target.acquisitionIds = itemIds.joinToString(",")
        target.verifiedAcquisitionIds = splitCollectionIds(vo.verifiedAcquisitionIds)
            .filter { it in itemIds }
            .joinToString(",")
            .takeIf { it.isNotBlank() }
        target.basis = vo.basis
        target.entryDate = vo.entryDate
        target.handoverUser = vo.handoverUser
        target.receiverUser = vo.receiverUser
        target.custodian = vo.custodian
        target.warehouseId = warehouseId
        target.attachmentIds = vo.attachmentIds
        target.remark = vo.remark
        BusinessAttachmentBindingPlanner.plan(target.id.value,"入馆凭证附件",target.attachmentIds).forEach { item ->
            if(originalFiles?.bindRetainedBusinessReference(AccessionReceiptService.BIZ,target.id.value,target.orgId,item.fileId,item.aliasName)!=true)
                attachmentBinder.bindSingle(target.id.value,item.aliasName,item.fileId)
        }
        originalFiles?.remember(AccessionReceiptService.BIZ, target.id.value)
        return target.id.value
    }

    @CacheEvict(allEntries = true)
    fun remove(id: String): String {
        val node = scopedFind(id, writable = true)
        CollectionCurrentOperationAccess.require(scope,node.orgId,true,setOf(CollectionConst.ACQUIRE),"入馆凭证")
        if (node.voucherStatus != EnumVoucherStatus.DRAFT || receipts.hasRecords(id)) throw BadRequestException("已有实际核收原件的凭证不能删除")
        node.delete()
        return id
    }

    /** 实际核收完成后生成待登记记录；正式级别和登账由法定审核完成。 */
    @CacheEvict(allEntries = true)
    fun execute(id: String, request: EntryVoucherExecuteVo = EntryVoucherExecuteVo()): EntryVoucherVo {
        val node = scopedFind(id, writable = true)
        CollectionCurrentOperationAccess.require(scope,node.orgId,true,setOf(CollectionConst.LEDGER_CREATE),"入馆登记")
        val itemIds = if (node.voucherStatus == EnumVoucherStatus.DRAFT) EntryVoucherItemPolicy.requireDistinctIds(node.acquisitionIds)
            else splitCollectionIds(node.acquisitionIds)
        if (request.existingCollections.keys.any { it !in itemIds } || request.existingCollections.values.map { it.collectionId }.distinct().size != request.existingCollections.size)
            throw BadRequestException("待登记关联须逐项对应本凭证，不能重复选择同一藏品")
        if (node.voucherStatus != EnumVoucherStatus.DRAFT) {
            if (request.existingCollections.any { (acquisitionId, target) -> AcquisitionDao.findById(acquisitionId)?.collectionId != target.collectionId })
                throw BadRequestException("凭证已生成登记记录，不能改换关联对象")
            return node.toModel().let { enrich(listOf(it)).first() }
        }
        CollectionLocationMutation.acquire(node.orgId)
        CollectionCurrentOperationAccess.require(scope,node.orgId,true,setOf(CollectionConst.LEDGER_CREATE),"入馆登记")
        val confirmed = receipts.requireCompleted(node)
        val acquisitions = itemIds.map {
            AcquisitionDao.findById(it) ?: throw BadRequestException("征集单不存在：$it")
        }
        acquisitions.forEach {
            EntryVoucherItemPolicy.requireRegistrable(it.id.value, it.flowStatus, it.collectionId, heldByOtherVoucher = false)
            compliance.requirePrepared(it)
        }
        acquisitions.forEach { acq ->
            val dossier=compliance.requirePrepared(acq)
            val receipt=confirmed.getValue(acq.id.value).content
            val actual=AccessionRegistrationFacts.requireActualQuantity(receipt)
            val facts = CollectionVo(
                    name = acq.name,
                    nature = dossier.nature,
                    categoryId = acq.categoryId,
                    era = acq.era,
                    texture = acq.texture,
                    // 征集建议级别留在原申请；主档正式级别须凭行政定级原件经编目或信息变更采用。
                    level = io.hizhengfu.cloud.collection.domain.EnumRelicLevel.UNRATED,
                    source = acq.source,
                    quantity = acq.quantity,
                    unit = acq.unit,
                    acquireDate = dossier.actualAcquiredOn,
                    entryDate = AccessionRegistrationFacts.entryDate(receipt),
                    receiptCompletedAt = receipt.checkedAt,
                    actualQuantity = actual.first,
                    actualQuantityUnit = actual.second,
                    dimensions = acq.dimensions,
                    weight = acq.weight,
                    completeness = acq.completeness,
                    completeStatus = acq.completeStatus,
                    coverImage = splitCollectionIds(receipt.pieces.first().photoFileIds).firstOrNull(),
                    warehouseId = confirmed.getValue(acq.id.value).content.warehouseId,
                    lifeStatus = EnumCollectionStatus.PENDING,
                    entryVoucherNo = node.voucherNo,
                    remark = if(acq.designatedReceptionId!=null) "指定接收入藏（指定记录 ${acq.designatedReceptionId}，入馆凭证 ${node.voucherNo}）" else "征集入藏（征集单 ${acq.id.value}，入馆凭证 ${node.voucherNo}）",
                )
            val target = request.existingCollections[acq.id.value]
            val collId = if (target == null) collectionService.createForAccession(node.orgId, facts, acq.id.value)
                else collectionService.linkForAccession(node.orgId, facts, acq.id.value, target)
            acq.collectionId = collId
            receipts.bindCreated(confirmed.getValue(acq.id.value), collId)
            compliance.sealPhase(acq,"ENTERED")
        }
        CollectionCurrentOperationAccess.require(scope,node.orgId,true,setOf(CollectionConst.LEDGER_CREATE),"入馆登记")
        node.voucherStatus = EnumVoucherStatus.RECEIVED
        node.entryDate = confirmed.values.maxOf { AccessionRegistrationFacts.entryDate(it.content) }
        return node.toModel().let { enrich(listOf(it)).first() }
    }

    fun registrationTargets(id: String): Map<String, List<io.hizhengfu.cloud.collection.domain.EntryVoucherRegistrationTargetVo>> {
        val node = scopedFind(id, writable = true)
        CollectionCurrentOperationAccess.require(scope, node.orgId, true, setOf(CollectionConst.LEDGER_CREATE), "入馆登记")
        if (node.voucherStatus != EnumVoucherStatus.DRAFT) return emptyMap()
        return EntryVoucherItemPolicy.requireDistinctIds(node.acquisitionIds).associateWith { acquisitionId ->
            val acq = AcquisitionDao.findById(acquisitionId) ?: throw BadRequestException("征集单不存在")
            if (acq.orgId != node.orgId || acq.flowStatus != EnumFlowStatus.PASS || !acq.collectionId.isNullOrBlank())
                throw BadRequestException("凭证条目的馆别或登记状态已变化")
            collectionService.accessionTargets(node.orgId, CollectionVo(name = acq.name, nature = compliance.requirePrepared(acq).nature,
                source = acq.source, quantity = acq.quantity, unit = acq.unit))
        }
    }

    /** 可入凭证的征集单（审批通过 + 未登账 + 未被凭证占用）。 */
    fun registrableAcquisitions(): List<io.hizhengfu.cloud.collection.dao.AcquisitionVo> {
        val org = CollectionSearchReadAccess.require(scope, acquisition = true).currentOrgId(scope)
        val held = heldAcquisitionIds(org, excludeVoucherId = null)
        return AcquisitionDao.find {
            (io.hizhengfu.cloud.collection.dao.Acquisitions.orgId eq org) and
                (io.hizhengfu.cloud.collection.dao.Acquisitions.flowStatus eq EnumFlowStatus.PASS)
        }
            .filter { it.collectionId.isNullOrBlank() && !held.contains(it.id.value) }
            .sortedByDescending { it.createTime }
            .map { AcquisitionComplianceService.visibleRecord(scope,it.toModel()) }
    }

    private fun heldAcquisitionIds(orgId: String, excludeVoucherId: String?): Set<String> =
        EntryVoucherDao.find { EntryVouchers.orgId eq orgId }
            .filterNot { it.id.value == excludeVoucherId }
            .flatMap { splitCollectionIds(it.acquisitionIds) }
            .toSet()

    private fun nextVoucherNo(orgId: String, input: String?): String {
        val today = LocalDate.now()
        val prefix = EntryVoucherNumberGenerator.prefix(today)
        val existing = EntryVoucherDao.find {
            (EntryVouchers.orgId eq orgId) and (EntryVouchers.voucherNo like "$prefix%")
        }.map { it.voucherNo }.toSet()
        val resolved = EntryVoucherNumberGenerator.resolve(input, today, existing)
        if (EntryVoucherDao.find { (EntryVouchers.orgId eq orgId) and (EntryVouchers.voucherNo eq resolved) }.any()) {
            throw BadRequestException("入馆凭证号已存在：$resolved")
        }
        return resolved
    }

    private fun scopedFind(id: String, writable: Boolean = false): EntryVoucherDao {
        val orgIds = if (writable) scope.writableOrgIds() else CollectionSearchReadAccess.require(scope, acquisition = true).orgIds
        val node = (if (writable) EntryVoucherDao.find { EntryVouchers.id eq id }.forUpdate().singleOrNull() else findById(id)) ?: throw BadRequestException("入馆凭证不存在")
        OrgScopedReadPolicy.requireVisible(orgIds, node.orgId, "入馆凭证")
        return node
    }

    private fun enrich(items: List<EntryVoucherVo>): List<EntryVoucherVo> {
        items.forEach { vo ->
            vo.acquisitionSummaries = splitCollectionIds(vo.acquisitionIds)
                .mapNotNull { AcquisitionDao.findById(it)?.takeIf { a -> a.orgId == vo.orgId }?.toModel()?.let { AcquisitionComplianceService.visibleRecord(scope,it) } }
            vo.warehouseName = vo.warehouseId?.let { WarehouseDao.findById(it)?.takeIf { w -> w.orgId == vo.orgId }?.name }
            vo.movementIds = vo.id?.let { voucherId ->
                StorageMovementDao.find { (StorageMovements.refBizId eq voucherId) and (StorageMovements.orgId eq vo.orgId!!) }.map { it.id.value }
            }.orEmpty()
        }
        return items
    }
}
