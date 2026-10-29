package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.domain.CollectionDescriptionPolicy
import io.hizhengfu.cloud.collection.domain.CollectionCategoryReasonBalances
import io.hizhengfu.cloud.collection.domain.EnumDeregReason
import io.hizhengfu.cloud.collection.domain.CollectionPublicFeedVo
import io.hizhengfu.cloud.collection.domain.EnumDisclosureSubject

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.CollectionCategoryDao
import io.hizhengfu.cloud.collection.dao.CollectionCatalogDao
import io.hizhengfu.cloud.collection.dao.CollectionCatalogs
import io.hizhengfu.cloud.collection.dao.CollectionAttachments
import io.hizhengfu.cloud.collection.dao.CollectionDao
import io.hizhengfu.cloud.collection.dao.CollectionLedgerLogDao
import io.hizhengfu.cloud.collection.dao.CollectionLedgerLogs
import io.hizhengfu.cloud.collection.dao.CollectionMedias
import io.hizhengfu.cloud.collection.dao.CollectionOrgDao
import io.hizhengfu.cloud.collection.dao.CollectionVo
import io.hizhengfu.cloud.collection.dao.Collections
import io.hizhengfu.cloud.collection.dao.LedgerLogVo
import io.hizhengfu.cloud.collection.dao.WarehouseDao
import io.hizhengfu.cloud.collection.domain.CollectionCatalogAuditVo
import io.hizhengfu.cloud.collection.domain.CollectionCatalogPolicy
import io.hizhengfu.cloud.collection.domain.CollectionCatalogReviewVo
import io.hizhengfu.cloud.collection.domain.CollectionIdentityPolicy
import io.hizhengfu.cloud.collection.domain.CollectionCorrectVo
import io.hizhengfu.cloud.collection.domain.CollectionLifecyclePolicy
import io.hizhengfu.cloud.collection.domain.CollectionOutboundPolicy
import io.hizhengfu.cloud.collection.domain.CollectionOrgFilterPolicy
import io.hizhengfu.cloud.collection.domain.CollectionQueryVo
import io.hizhengfu.cloud.collection.domain.CollectionRecordPolicy
import io.hizhengfu.cloud.collection.domain.CollectionDutySeparationPolicy
import io.hizhengfu.cloud.collection.domain.CollectionSurveyExportVo
import io.hizhengfu.cloud.collection.domain.CollectionStatVo
import io.hizhengfu.cloud.collection.domain.DimensionVo
import io.hizhengfu.cloud.collection.domain.EnumCatalogStatus
import io.hizhengfu.cloud.collection.domain.EnumCollectionNature
import io.hizhengfu.cloud.collection.domain.EnumCollectionStatus
import io.hizhengfu.cloud.collection.domain.EnumCollectionMediaType
import io.hizhengfu.cloud.collection.domain.EnumLedgerAction
import io.hizhengfu.cloud.collection.domain.EnumRelicLevel
import io.hizhengfu.cloud.collection.domain.EnumSource
import io.hizhengfu.cloud.collection.domain.RfidBatchResultVo
import io.hizhengfu.cloud.common.domain.GenericPage
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.service.CrudService
import io.hizhengfu.cloud.common.utils.SecurityUtils
import jakarta.servlet.http.HttpServletRequest
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Case
import org.jetbrains.exposed.v1.core.intLiteral
import org.jetbrains.exposed.v1.core.exists
import org.jetbrains.exposed.v1.core.NotOp
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.notInList
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.select
import org.springframework.cache.annotation.CacheConfig
import org.springframework.cache.annotation.CacheEvict
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.roundToInt

object CollectionStatBuilder {
    fun build(records: List<CollectionVo>, today: LocalDate = LocalDate.now()): CollectionStatVo {
        val items=records.filter { it.registered && it.lifeStatus!=EnumCollectionStatus.DEREGISTERED }
        val imageCount = items.count { CollectionImagePresence.hasImage(it.coverImage, it.mediaCount) }
        val attachmentCount = items.count { it.attachmentCount > 0 }
        val relics = items.filter { it.nature == EnumCollectionNature.RELIC }
        val quantified = items.filter { (it.quantity ?: 0) > 0 && !it.unit.isNullOrBlank() }
        return CollectionStatVo(
            total = items.size,
            byLevel = items.mapNotNull { it.level?.name }.groupingBy { it }.eachCount(),
            byNature = items.groupingBy { it.nature?.name ?: "性质待核实" }.eachCount(),
            byRelicLevel = relics.groupingBy { it.level?.name ?: "未定级" }.eachCount(),
            byStatus = items.mapNotNull { it.lifeStatus?.name }.groupingBy { it }.eachCount(),
            byCategory = items.groupingBy { it.categoryName ?: it.categoryId ?: "未分类" }.eachCount(),
            byEra = items.groupingBy { it.era?.trim()?.ifBlank { null } ?: "未标注" }.eachCount(),
            bySource = items.groupingBy { it.source?.name ?: "未设置" }.eachCount(),
            byWarehouse = items.groupingBy { it.warehouseName ?: it.warehouseId ?: "未登记库位" }.eachCount(),
            quantityByUnit = quantified.groupBy { it.unit!!.trim() }
                .mapValues { (_, rows) -> rows.sumOf { it.quantity!!.toLong() } },
            quantityUnverifiedTotal = items.size - quantified.size,
            imageCount = imageCount,
            attachmentCount = attachmentCount,
            imageRate = percent(imageCount, items.size),
            attachmentRate = percent(attachmentCount, items.size),
            pendingRegistrationTotal = records.count { !it.registered && it.lifeStatus==EnumCollectionStatus.PENDING && it.catalogStatus!=EnumCatalogStatus.APPROVED },
            unverifiedRegistrationTotal = records.count { !it.registered && it.lifeStatus!=EnumCollectionStatus.DEREGISTERED && (it.lifeStatus!=EnumCollectionStatus.PENDING || it.catalogStatus==EnumCatalogStatus.APPROVED) },
            retiredTotal = records.count { it.lifeStatus==EnumCollectionStatus.DEREGISTERED },
            ungradedRelicTotal = records.count {
                it.lifeStatus != EnumCollectionStatus.DEREGISTERED && it.nature == EnumCollectionNature.RELIC &&
                    (it.level == null || it.level == EnumRelicLevel.UNRATED)
            },
            registeredThisYear = items.count { it.registeredAt?.year == today.year },
            registeredThisMonth = items.count { registeredAt ->
                registeredAt.registeredAt?.let { it.year == today.year && it.month == today.month } == true
            },
        )
    }

    private fun percent(value: Int, total: Int): Int =
        if (total <= 0) 0 else ((value.toDouble() / total) * 100).roundToInt()
}

internal object CollectionImagePresence {
    fun hasImage(coverImage: String?, mediaCount: Long): Boolean =
        !coverImage.isNullOrBlank() || mediaCount > 0

    fun displayCount(coverImage: String?, mediaCount: Long): Long =
        if (mediaCount > 0) mediaCount else if (!coverImage.isNullOrBlank()) 1 else 0
}

data class LedgerLogCollectionInfo(
    val name: String?,
)

object LedgerLogCollectionInfoEnricher {
    fun enrich(items: List<LedgerLogVo>, collectionInfoById: Map<String, LedgerLogCollectionInfo>): List<LedgerLogVo> =
        items.map { item ->
            val info = item.collectionId?.let { collectionInfoById[it] }
            item.copy(collectionName = info?.name)
        }
}

internal data class CollectionCatalogAuditDecision(
    val status: EnumCatalogStatus,
    val targetLifeStatus: EnumCollectionStatus,
    val opinion: String?,
) {
    companion object {
        fun from(vo: CollectionCatalogAuditVo): CollectionCatalogAuditDecision {
            val status = vo.status ?: throw BadRequestException("审核结果必填")
            val opinion = vo.opinion?.trim()?.takeIf { it.isNotBlank() }
            return when (status) {
                EnumCatalogStatus.APPROVED -> CollectionCatalogAuditDecision(status, EnumCollectionStatus.IN_STORE, opinion)
                EnumCatalogStatus.REJECTED -> CollectionCatalogAuditDecision(
                    status = status,
                    targetLifeStatus = EnumCollectionStatus.PENDING,
                    opinion = opinion ?: throw BadRequestException("驳回时必须填写审核意见"),
                )
                EnumCatalogStatus.PENDING,
                EnumCatalogStatus.DRAFT,
                EnumCatalogStatus.SUBMITTED -> throw BadRequestException("请选择通过或驳回")
            }
        }
    }
}

internal object CollectionSurveyExportBuilder {
    fun build(collection: CollectionVo, orgName: String?, exchangeCode: String? = null): CollectionSurveyExportVo = CollectionSurveyExportVo(
        totalRegNo = collection.totalRegNo,
        classNo = collection.classNo,
        name = collection.name,
        era = collection.era,
        categoryName = collection.categoryName ?: collection.categoryId,
        texture = collection.texture,
        dimensions = formatDimensions(collection.dimensions),
        weight = collection.weight,
        completeness = collection.completeness?.name,
        completeStatus = collection.completeStatus,
        source = collection.source?.name,
        acquireDate = collection.acquireDate?.toString(),
        level = collection.level?.name,
        sourceDesc = collection.sourceDesc,
        orgName = orgName ?: collection.orgId,
        image = collection.coverImage,
        originalName = collection.originalName, originalNo = collection.auxNo,
        quantity = collection.quantity, unit = collection.unit,
        actualQuantity = collection.actualQuantity, actualQuantityUnit = collection.actualQuantityUnit,
        dimensionUnit = collection.dimensionUnit, weightUnit = collection.weightUnit,
        preserveState = collection.preserveState?.name, entryDate = collection.entryDate?.toString(),
        receiptCompletedAt = collection.receiptCompletedAt?.toString(), registeredAt = collection.registeredAt?.toString(),
        entryVoucherNo = collection.entryVoucherNo, exchangeCode = exchangeCode,
        standardDimensions = CollectionRegistrationPrintFields.dimensions(CollectionCatalogMapper.fromCollection(collection)),
        standardWeight = CollectionRegistrationPrintFields.mass(CollectionCatalogMapper.fromCollection(collection)),
        registrationDetails = collection.registrationDetails,
        qualityIssues = CollectionCatalogPolicy.completeness(CollectionCatalogMapper.fromCollection(collection)).missingFields,

    )

    private fun formatDimensions(dimensions: DimensionVo?): String? {
        val value = dimensions ?: return null
        return (listOf(
            "通高" to value.height,
            "口径" to value.mouth,
            "底径" to value.bottom,
            "腹径" to value.belly,
            "长" to value.length,
            "宽" to value.width,
            "直径" to value.diameter,
        ) + value.extra.orEmpty().toList())
            .filter { !it.second.isNullOrBlank() }
            .joinToString("；") { "${it.first}：${it.second}" }
            .ifBlank { null }
    }
}

internal object CollectionAcquireRangePolicy {
    fun resolve(range: String?, exactDate: LocalDate?): String =
        range?.trim()?.ifBlank { null }
            ?: exactDate?.toString()
            ?: throw BadRequestException("取得时间范围必填")
}

/** Ordinary edits must preserve ledger-critical identity and acquisition fields. */
internal object CollectionLedgerEditPolicy {
    fun requirePreserved(existing: CollectionVo, requested: CollectionVo) {
        if (existing.published == true) {
            throw BadRequestException("已发布藏品请通过信息变更申请维护，审核通过前不会影响对外数据")
        }
        CollectionIdentityPolicy.requireUnchanged(existing.totalRegNo.orEmpty(), requested.totalRegNo)
        val changed = buildList {
            if (requested.name?.trim() != existing.name) add("名称")
            if (requested.level != existing.level) add("藏品级别")
            if (requested.source != existing.source) add("来源方式")
            if (requested.acquireDate != existing.acquireDate) add("取得日期")
            if (effectiveRange(requested) != effectiveRange(existing)) add("取得时间范围")
            if (effectiveYear(requested) != effectiveYear(existing)) add("取得年度")
        }
        if (changed.isNotEmpty()) {
            throw BadRequestException("普通编辑不能修改关键字段（${changed.joinToString("、")}），请使用信息变更申请")
        }
    }

    private fun effectiveRange(value: CollectionVo): String? =
        value.acquireDateRange?.trim()?.ifBlank { null } ?: value.acquireDate?.toString()

    private fun effectiveYear(value: CollectionVo): String? =
        value.acquireYear?.trim()?.ifBlank { null } ?: value.acquireDate?.year?.toString()
}

internal object CollectionRegistrationNumberGenerator {
    fun nextAvailable(existing: Set<String>, width: Int = 6): String {
        val used = existing.mapNotNull { it.toIntOrNull() }.toSet()
        var next = 1
        while (used.contains(next)) next += 1
        return next.toString().padStart(width, '0')
    }
}

internal object CollectionPublishPolicy {
    fun requirePublishable(catalogStatus: EnumCatalogStatus, lifeStatus: EnumCollectionStatus) {
        if (catalogStatus != EnumCatalogStatus.APPROVED) {
            throw BadRequestException("藏品编目审核通过后才能发布数字藏品")
        }
        if (lifeStatus == EnumCollectionStatus.DEREGISTERED) {
            throw BadRequestException("已退出馆藏藏品不能发布数字藏品")
        }
    }
}

internal data class LedgerLogPlan(
    val orgId: String,
    val action: EnumLedgerAction,
    val field: String?,
    val before: String?,
    val after: String?,
    val reason: String?,
    val refBizId: String? = null,
)

internal object CollectionOrgChangeLedgerPlanner {
    fun plan(fromOrgId: String, toOrgId: String, reason: String?, refBizId: String?): List<LedgerLogPlan> =
        listOf(fromOrgId, toOrgId).distinct().map { orgId ->
            LedgerLogPlan(
                orgId = orgId,
                action = EnumLedgerAction.CORRECT,
                field = "orgId",
                before = fromOrgId,
                after = toOrgId,
                reason = reason ?: "跨馆调拨",
                refBizId = refBizId,
            )
        }
}

/**
 * 藏品档案（总登记账/分类账/一物一档）核心服务。
 * 红线：总账无物理删除；关键字段更正走 [correct] 留痕；总登记号按馆唯一连续。
 */
@Component
@Transactional
@CacheConfig(cacheNames = [CollectionConst.CACHE])
class CollectionService(
    private val scope: CollectionScopeResolver,
    private val attachmentBinder: BusinessAttachmentBinder,
    private val registrationNumberService: CollectionRegistrationNumberService,
    private val operationLockService: CollectionOperationLockService,
    private val catalogService: CollectionCatalogService,
    private val changeService: CollectionChangeService,
    private val retainedLedgerService: CollectionRetainedLedgerService,
    private val disclosureService: CollectionDisclosureService,
    private val restrictedOperations: CollectionRestrictedOperationService = CollectionRestrictedOperationService(),
    private val originalFiles: CollectionBusinessOriginalFiles? = null,
) : CrudService<CollectionDao>(CollectionDao, CollectionConst.CACHE) {

    fun findPage(query: CollectionQueryVo, request: HttpServletRequest): GenericPage<CollectionVo> {
        val page = GenericPage<CollectionVo>().build(request)
        val q = buildQuery(query)
        when (query.searchSort) {
            io.hizhengfu.cloud.collection.domain.EnumCollectionSearchSort.REG_NO_ASC -> q.orderBy(Collections.totalRegNo to SortOrder.ASC)
            io.hizhengfu.cloud.collection.domain.EnumCollectionSearchSort.REG_NO_DESC -> q.orderBy(Collections.totalRegNo to SortOrder.DESC)
            io.hizhengfu.cloud.collection.domain.EnumCollectionSearchSort.NAME_ASC -> q.orderBy(Collections.name to SortOrder.ASC)
            io.hizhengfu.cloud.collection.domain.EnumCollectionSearchSort.REGISTERED_DESC -> q.orderBy(Collections.registeredAt to SortOrder.DESC)
            io.hizhengfu.cloud.collection.domain.EnumCollectionSearchSort.ACQUIRE_DESC -> q.orderBy(Collections.acquireDate to SortOrder.DESC)
            io.hizhengfu.cloud.collection.domain.EnumCollectionSearchSort.RELEVANCE -> query.keyword?.trim()?.takeIf(String::isNotBlank)?.let { kw ->
                q.orderBy(Case().When(Collections.totalRegNo eq kw, intLiteral(0))
                    .When(Collections.auxNo eq kw, intLiteral(1)).When(Collections.name eq kw, intLiteral(2))
                    .Else(intLiteral(3)) to SortOrder.ASC)
            }
            null -> Unit
        }
        q.orderBy(Collections.createTime to SortOrder.DESC, Collections.id to SortOrder.DESC)
        @Suppress("UNCHECKED_CAST")
        val daoPage = findPage(q, page as GenericPage<CollectionDao>)
        page.list = daoPage.data?.map { it.toModel() }?.let { enrich(it) }
        @Suppress("UNCHECKED_CAST")
        return page as GenericPage<CollectionVo>
    }

    fun get(id: String): CollectionVo? {
        val vo = scopedFind(id).toModel()
        return enrich(listOf(vo)).first()
    }

    /** 仅补充本件所属馆的参考名称，历史错馆关联保留编号供核查。 */
    private fun enrich(list: List<CollectionVo>): List<CollectionVo> {
        if (list.isEmpty()) return list
        val catNames = CollectionCategoryDao.all().associate { it.id.value to (it.orgId to it.name) }
        val whNames = WarehouseDao.all().associate { it.id.value to (it.orgId to it.name) }
        val ids = list.mapNotNull { it.id }.toSet()
        val owningOrgs = list.associate { it.id to it.orgId }
        val mediaRows = if (ids.isEmpty()) emptyList() else CollectionMedias.selectAll()
            .where { CollectionMedias.collectionId inList ids }
            .orderBy(CollectionMedias.isCover to SortOrder.DESC, CollectionMedias.sort to SortOrder.ASC, CollectionMedias.id to SortOrder.ASC)
            .filter { row -> row[CollectionMedias.orgId] == owningOrgs[row[CollectionMedias.collectionId]] }
        val mediaCounts = mediaRows.groupingBy { it[CollectionMedias.collectionId] }.eachCount().mapValues { it.value.toLong() }
        val previews = mediaRows.filter { it[CollectionMedias.mediaType] !in setOf(EnumCollectionMediaType.VIDEO, EnumCollectionMediaType.THREE_D) }
            .groupBy { it[CollectionMedias.collectionId] }.mapValues { it.value.first()[CollectionMedias.fileId] }
        val attachmentCounts = if (ids.isEmpty()) emptyMap() else CollectionAttachments.selectAll()
            .where { CollectionAttachments.collectionId inList ids }
            .filter { row -> row[CollectionAttachments.orgId] == owningOrgs[row[CollectionAttachments.collectionId]] }
            .groupingBy { it[CollectionAttachments.collectionId] }.eachCount().mapValues { it.value.toLong() }
        list.forEach {
            it.categoryName = it.categoryId?.let { c -> catNames[c] }
                ?.takeIf { category -> category.first == null || category.first == it.orgId }?.second
            it.warehouseName = it.warehouseId?.let { w -> whNames[w] }
                ?.takeIf { warehouse -> warehouse.first == it.orgId }?.second
            it.previewImage = it.coverImage?.takeIf(String::isNotBlank) ?: previews[it.id]
            it.mediaCount = CollectionImagePresence.displayCount(it.coverImage, it.id?.let { id -> mediaCounts[id] } ?: 0)
            it.attachmentCount = it.id?.let { id -> attachmentCounts[id] } ?: 0
        }
        return list
    }

    @CacheEvict(allEntries = true)
    fun create(vo: CollectionVo): String {
        val actor = CollectionActorDirectory(scope).requireActive(SecurityUtils.getUserDetail().id)
        val orgId = scope.currentOrgId(actor)
        requireLedgerWrite(orgId, setOf(CollectionConst.LEDGER_CREATE))
        return createForOrg(orgId, vo.copy(lifeStatus = EnumCollectionStatus.PENDING))
    }

    /** 指定收藏单位登账（供征集流程审批通过后调用，绕过当前用户作用域）。 */
    @CacheEvict(allEntries = true)
    fun createForOrg(org: String, vo: CollectionVo): String = createRecord(org, vo)

    /** 凭证核收通过后，将对应清册照片沿用到本次新建的待登记主档。 */
    internal fun createForAccession(org: String, vo: CollectionVo, acquisitionId: String): String =
        createRecord(org, vo, acquisitionId)

    internal fun accessionTargets(org: String, facts: CollectionVo): List<io.hizhengfu.cloud.collection.domain.EntryVoucherRegistrationTargetVo> =
        dao.find { (Collections.orgId eq org) and (Collections.lifeStatus eq EnumCollectionStatus.PENDING) and
            (Collections.name eq facts.name.orEmpty()) }.filter { accessionTargetMatches(it, facts) }.map {
            io.hizhengfu.cloud.collection.domain.EntryVoucherRegistrationTargetVo(it.id.value, it.totalRegNo, it.name, accessionTargetHash(it))
        }

    private fun accessionTargetMatches(node: CollectionDao, facts: CollectionVo): Boolean =
        !node.registered && !node.published && node.lifeStatus == EnumCollectionStatus.PENDING &&
            node.catalogStatus in setOf(EnumCatalogStatus.DRAFT, EnumCatalogStatus.REJECTED) && node.entryVoucherNo.isNullOrBlank() &&
            node.name == facts.name && node.nature == facts.nature && node.source == facts.source &&
            node.quantity == facts.quantity && node.unit == facts.unit && CollectionMigrationHistory.source(node) == null &&
            !io.hizhengfu.cloud.collection.dao.AcquisitionDao.find { io.hizhengfu.cloud.collection.dao.Acquisitions.collectionId eq node.id.value }.any() &&
            !io.hizhengfu.cloud.collection.dao.AccessionReceiptDao.find { io.hizhengfu.cloud.collection.dao.AccessionReceipts.collectionId eq node.id.value }.any()

    private fun accessionTargetHash(node: CollectionDao): String = CollectionImportConfirmation.hash(
        listOf("accession-existing-collection/v1", node.toModel(), CollectionCatalogConfirmation.hash(catalogService.ensureCatalogFor(node), node)))

    /** 仅接续经明确选择且未登记的同一实物；保留原号和资料，实收事实来自已经核验的清册。 */
    internal fun linkForAccession(org: String, facts: CollectionVo, acquisitionId: String, target: io.hizhengfu.cloud.collection.domain.EntryVoucherExistingCollectionVo): String {
        val node = scopedLedgerWrite(target.collectionId, setOf(CollectionConst.LEDGER_CREATE))
        node.refresh(flush = true)
        if (node.orgId != org || !accessionTargetMatches(node, facts))
            throw BadRequestException("只能关联同馆、同名、同性质、同来源及件套数一致且尚无核收关联的待登记藏品；历史已登账资料请走迁移核实")
        operationLockService.requireUnlocked(node.id.value)
        catalogService.requireInitialCatalog(node)
        if (target.stateHash.isBlank() || accessionTargetHash(node) != target.stateHash)
            throw BadRequestException("所选待登记藏品资料已变化，请重新读取并核对")
        originalFiles?.rememberExistingMain(node.id.value)
        val before = importSnapshot(node.toModel())
        node.acquireDate = facts.acquireDate
        node.acquireDateRange = facts.acquireDate?.toString()
        node.acquireYear = facts.acquireDate?.year?.toString()
        node.entryDate = facts.entryDate
        node.receiptCompletedAt = facts.receiptCompletedAt
        node.actualQuantity = facts.actualQuantity
        node.actualQuantityUnit = facts.actualQuantityUnit
        node.entryVoucherNo = facts.entryVoucherNo
        node.warehouseId = CollectionLocationMutation.storageReference(org, facts.warehouseId)
        if (node.coverImage.isNullOrBlank()) {
            val cover = attachmentBinder.resolveSingle(facts.coverImage)
            node.coverImage = cover
            if (cover != null && originalFiles != null) originalFiles.bindAccessionCover(acquisitionId, node.id.value, cover)
            else attachmentBinder.bindSingle(node.id.value, "藏品封面影像", cover)
        }
        syncCatalogDraft(node)
        originalFiles?.rememberExistingMain(node.id.value)
        writeLog(node, EnumLedgerAction.CORRECT, "accessionReceipt", JsonUtils.toJson(before), JsonUtils.toJson(importSnapshot(node.toModel())), "依据真实入馆核收关联已有待登记记录")
        return node.id.value
    }

    private fun createRecord(org: String, vo: CollectionVo, acquisitionId: String? = null): String {
        CollectionCategoryPolicy.requireOptional(vo.categoryId)
        CollectionDescriptionPolicy.requireValid(CollectionCatalogMapper.fromCollection(vo))
        val name = vo.name?.takeIf { it.isNotBlank() } ?: throw BadRequestException("名称必填")
        val warehouseId = CollectionLocationMutation.storageReference(org, vo.warehouseId)
        val acquireRange = CollectionAcquireRangePolicy.resolve(vo.acquireDateRange, vo.acquireDate)
        val regNo = vo.totalRegNo?.trim()?.takeIf { it.isNotBlank() } ?: registrationNumberService.nextRegNo(org)
        val coverFileId = attachmentBinder.resolveSingle(vo.coverImage)
        registrationNumberService.requireAvailable(org, regNo)
        val node = dao.new {
            orgId = org
            nature = vo.nature
            totalRegNo = regNo
            classNo = vo.classNo
            auxNo = vo.auxNo
            this.name = name
            originalName = vo.originalName
            categoryId = vo.categoryId
            era = vo.era
            texture = vo.texture
            textureCategory1 = vo.textureCategory1
            textureCategory2 = vo.textureCategory2
            vo.level?.let { level = it }
            vo.quantity?.let { quantity = it }
            vo.unit?.let { unit = it }
            source = vo.source
            sourceDesc = vo.sourceDesc
            acquireDate = vo.acquireDate
            actualQuantity = vo.actualQuantity
            actualQuantityUnit = vo.actualQuantityUnit
            dimensionUnit = vo.dimensionUnit
            entryDate = vo.entryDate
            receiptCompletedAt = vo.receiptCompletedAt
            registrationDetails = vo.registrationDetails
            completeness = vo.completeness
            completeStatus = vo.completeStatus
            dimensions = vo.dimensions
            weight = vo.weight
            weightRange = vo.weightRange
            weightUnit = vo.weightUnit
            region = vo.region
            volume = vo.volume
            volumeUnit = vo.volumeUnit
            color = vo.color
            sourceNo = vo.sourceNo
            country = vo.country
            culture = vo.culture
            entryVoucherNo = vo.entryVoucherNo
            inscription = vo.inscription
            feature = vo.feature
            preserveState = vo.preserveState
            acquireDateRange = acquireRange
            acquireYear = vo.acquireYear?.trim()?.ifBlank { null } ?: vo.acquireDate?.year?.toString()
            author = vo.author
            edition = vo.edition
            volumeNo = vo.volumeNo
            valuation = vo.valuation
            coverImage = coverFileId
            this.warehouseId = warehouseId
            catalogStatus = EnumCatalogStatus.DRAFT
            lifeStatus = vo.lifeStatus ?: EnumCollectionStatus.PENDING
            remark = vo.remark
        }
        registrationNumberService.markUsed(org, regNo, node.id.value, "预留登记号")
        writeLog(node, EnumLedgerAction.RESERVE_REG_NO, null, null, regNo, "预留登记号，正式登记须完成核收及法定审核")
        if (acquisitionId != null && coverFileId != null && originalFiles != null) {
            originalFiles.bindAccessionCover(acquisitionId, node.id.value, coverFileId)
        } else {
            attachmentBinder.bindSingle(node.id.value, "藏品封面影像", coverFileId)
        }
        catalogService.ensureCatalogFor(node)
        if (acquisitionId != null) originalFiles?.rememberExistingMain(node.id.value)
        else originalFiles?.remember(CollectionMainOriginalFileSource.BIZ, node.id.value)
        return node.id.value
    }

    /** 编辑非关键字段。 */
    @CacheEvict(allEntries = true)
    fun update(id: String, vo: CollectionVo): String {
        val node = scopedLedgerWrite(id, setOf(CollectionConst.LEDGER_CREATE))
        CollectionLocationMutation.acquire(node.orgId)
        node.refresh(flush = true)
        requireLedgerWrite(node.orgId, setOf(CollectionConst.LEDGER_CREATE))
        operationLockService.requireUnlocked(node.id.value)
        CollectionRecordPolicy.requireEditable(node.catalogStatus, node.lifeStatus)
        catalogService.requireInitialCatalog(node)
        CollectionLedgerEditPolicy.requirePreserved(node.toModel(), vo)
        val coverFileId = attachmentBinder.resolveSingle(vo.coverImage)
        val before = importSnapshot(node.toModel())
        originalFiles?.rememberExistingMain(id)
        if (originalFiles?.bindExistingMainReference(id, id, coverFileId, id, "藏品封面影像") != true)
            attachmentBinder.bindSingle(id, "藏品封面影像", coverFileId)
        applyEditableFields(node, vo, coverFileId)
        syncCatalogDraft(node)
        val after = importSnapshot(node.toModel())
        if (before != after) writeLog(node, EnumLedgerAction.CORRECT, "initialDraft", JsonUtils.toJson(before), JsonUtils.toJson(after), "初次登记资料补录")
        originalFiles?.remember(CollectionMainOriginalFileSource.BIZ, id)
        return id
    }

    private fun applyEditableFields(node: CollectionDao, vo: CollectionVo, coverFileId: String?) {
        CollectionCategoryPolicy.requireOptional(vo.categoryId)
        CollectionDescriptionPolicy.requireValid(CollectionCatalogMapper.fromCollection(vo))
        node.nature = vo.nature
        node.actualQuantity = vo.actualQuantity
        node.actualQuantityUnit = vo.actualQuantityUnit
        node.dimensionUnit = vo.dimensionUnit
        node.registrationDetails = vo.registrationDetails
        node.classNo = vo.classNo
        node.auxNo = vo.auxNo
        node.originalName = vo.originalName
        node.categoryId = vo.categoryId
        node.era = vo.era
        node.texture = vo.texture
        node.textureCategory1 = vo.textureCategory1
        node.textureCategory2 = vo.textureCategory2
        vo.quantity?.let { node.quantity = it }
        vo.unit?.let { node.unit = it }
        node.sourceDesc = vo.sourceDesc
        node.completeness = vo.completeness
        node.completeStatus = vo.completeStatus
        node.dimensions = vo.dimensions
        node.weight = vo.weight
        node.weightRange = vo.weightRange
        node.weightUnit = vo.weightUnit
        node.region = vo.region
        node.volume = vo.volume
        node.volumeUnit = vo.volumeUnit
        node.color = vo.color
        node.sourceNo = vo.sourceNo
        node.country = vo.country
        node.culture = vo.culture
        node.inscription = vo.inscription
        node.feature = vo.feature
        node.preserveState = vo.preserveState
        node.author = vo.author
        node.edition = vo.edition
        node.volumeNo = vo.volumeNo
        node.valuation = vo.valuation
        node.coverImage = coverFileId
        node.remark = vo.remark
    }

    private fun syncCatalogDraft(node: CollectionDao) {
        val catalog = catalogService.ensureCatalogFor(node)
        CollectionCatalogPolicy.requireEditable(catalog.catalogStatus)
        catalog.content = CollectionCatalogMapper.mergeCollection(catalog.content, node.toModel())
        catalog.catalogStatus = EnumCatalogStatus.DRAFT
        catalog.submittedVersion = null
        catalog.submittedBy = null
        catalog.submittedAt = null
        node.catalogStatus = EnumCatalogStatus.DRAFT
    }

    /**
     * 批量回导更新：总登记号仅作为馆内稳定匹配键，不在本操作中改号。
     * 更新复用普通编辑的状态/锁定保护，并额外把级别、来源、库位变化写入一条总账更正留痕。
     */
    @CacheEvict(allEntries = true)
    fun updateFromImport(id: String, vo: CollectionVo, reason: String): Boolean {
        val cleanReason = reason.trim().takeIf { it.isNotBlank() }
            ?: throw BadRequestException("批量更新时必须填写更正原因")
        val node = scopedLedgerWrite(id)
        val expectedRegNo = vo.totalRegNo?.takeIf(String::isNotBlank)
            ?: throw BadRequestException("批量更新必须提供总登记号")
        if (node.totalRegNo != expectedRegNo && node.totalRegNo != expectedRegNo.trim()) throw BadRequestException("总登记号匹配结果已变化，请重新校验")
        operationLockService.requireUnlocked(node.id.value)
        CollectionRecordPolicy.requireEditable(node.catalogStatus, node.lifeStatus)
        catalogService.requireInitialCatalog(node)
        val warehouseId = CollectionLocationMutation.storageReference(node.orgId, vo.warehouseId)
        val before = importSnapshot(node.toModel())
        val coverFileId = attachmentBinder.resolveSingle(vo.coverImage)

        originalFiles?.rememberExistingMain(id)
        if (originalFiles?.bindExistingMainReference(id, id, coverFileId, id, "藏品封面影像") != true)
            attachmentBinder.bindSingle(id, "藏品封面影像", coverFileId)
        applyEditableFields(node, vo, coverFileId)
        vo.name?.trim()?.takeIf(String::isNotBlank)?.let { node.name = it }
        vo.level?.let { node.level = it }
        node.source = vo.source
        node.acquireDate = vo.acquireDate
        node.actualQuantity = vo.actualQuantity
        node.actualQuantityUnit = vo.actualQuantityUnit
        node.dimensionUnit = vo.dimensionUnit
        node.entryDate = vo.entryDate
        node.receiptCompletedAt = vo.receiptCompletedAt
        node.registrationDetails = vo.registrationDetails
        node.acquireDateRange = CollectionAcquireRangePolicy.resolve(vo.acquireDateRange, vo.acquireDate)
        node.acquireYear = vo.acquireYear?.trim()?.ifBlank { null } ?: vo.acquireDate?.year?.toString()
        node.warehouseId = warehouseId
        syncCatalogDraft(node)
        originalFiles?.remember(CollectionMainOriginalFileSource.BIZ, id)
        val after = importSnapshot(node.toModel())
        if (before == after) return false
        writeLog(
            node = node,
            action = EnumLedgerAction.CORRECT,
            field = "batchImport",
            before = JsonUtils.toJson(before),
            after = JsonUtils.toJson(after),
            reason = cleanReason,
        )
        return true
    }

    /** 编目审核：通过后作为在库藏品进入后续业务，驳回后保留待修改状态；结果写账册留痕（审核记录报表）。 */
    @CacheEvict(allEntries = true)
    fun auditCatalog(id: String, vo: CollectionCatalogAuditVo, expectedStateHash: String? = null): String {
        return catalogService.review(
            id,
            CollectionCatalogReviewVo(status = vo.status, opinion = vo.opinion),
            expectedStateHash,
        )
    }

    /** 快捷更正只创建变更申请；经法定审批、签署归档后方可生效。 */
    fun correct(id: String, vo: CollectionCorrectVo): String = changeService.requestCorrection(id, vo)

    // ── RFID：绑定/解绑/识别（馆内唯一，绑定解绑写账册留痕） ──

    @CacheEvict(allEntries = true)
    fun bindRfid(id: String, tag: String?): String {
        val clean = tag?.trim()?.takeIf { it.isNotBlank() } ?: throw BadRequestException("标签号不能为空")
        val node = scopedLedgerWrite(id)
        operationLockService.requireUnlocked(id)
        CollectionOutboundPolicy.requireInStore(node.lifeStatus, "绑定RFID")
        val occupied = CollectionDao.find { (Collections.orgId eq node.orgId) and (Collections.rfidTag eq clean) }
            .firstOrNull { it.id.value != node.id.value }
        if (occupied != null) throw BadRequestException("标签已绑定藏品「${occupied.name}」（总登记号 ${occupied.totalRegNo}）")
        val before = node.rfidTag
        node.rfidTag = clean
        writeLog(node, EnumLedgerAction.CORRECT, "rfidTag", before, clean, "绑定RFID标签")
        return id
    }

    @CacheEvict(allEntries = true)
    fun unbindRfid(id: String): String {
        val node = scopedLedgerWrite(id)
        operationLockService.requireUnlocked(id)
        CollectionOutboundPolicy.requireInStore(node.lifeStatus, "解绑RFID")
        val before = node.rfidTag ?: return id
        node.rfidTag = null
        writeLog(node, EnumLedgerAction.CORRECT, "rfidTag", before, null, "解绑RFID标签")
        return id
    }

    /** 单标签解析（手持机单扫）。 */
    fun findByRfid(tag: String): CollectionVo {
        val clean = tag.trim().takeIf { it.isNotBlank() } ?: throw BadRequestException("标签号不能为空")
        val q = Collections.selectAll().where { Collections.rfidTag eq clean }
        q.andWhere { Collections.orgId inList readOrgIds() }
        val node = CollectionDao.wrapRows(q).firstOrNull() ?: throw BadRequestException("标签未绑定任何藏品：$clean")
        return enrich(listOf(node.toModel())).first()
    }

    /** 批量扫描解析：返回命中的藏品与未知标签。 */
    fun batchByRfid(tags: List<String>): RfidBatchResultVo {
        val orgIds = readOrgIds()
        val clean = tags.mapNotNull { it.trim().takeIf(String::isNotEmpty) }.distinct()
        if (clean.isEmpty()) return RfidBatchResultVo()
        val q = Collections.selectAll().where { Collections.rfidTag inList clean }
        q.andWhere { Collections.orgId inList orgIds }
        val matched = enrich(CollectionDao.wrapRows(q).map { it.toModel() })
        val matchedTags = matched.mapNotNull { it.rfidTag }.toSet()
        return RfidBatchResultVo(matched = matched, unknown = clean.filterNot { matchedTags.contains(it) })
    }

    /** 当前正式在册快照（按可见收藏单位聚合）；年度账面增减由年度统计服务计算。 */
    fun summary(): CollectionStatVo {
        val q = Collections.selectAll()
        q.andWhere { Collections.orgId inList readOrgIds(setOf(CollectionConst.STAT)) }
        val all = CollectionDao.wrapRows(q).map { it.toModel() }.toList()
        return CollectionStatBuilder.build(enrich(all))
    }

    fun ledgerLogs(id: String): List<LedgerLogVo> {
        val orgIds = readOrgIds()
        scopedFind(id, orgIds)
        return enrichLedgerLogCollectionInfo(CollectionLedgerLogDao.find { (CollectionLedgerLogs.collectionId eq id) and (CollectionLedgerLogs.orgId inList orgIds) }
            .sortedWith(compareByDescending<CollectionLedgerLogDao> { it.createTime }.thenByDescending { it.id.value })
            .map { it.toModel() }, orgIds)
    }

    fun ledgerLogPage(request: HttpServletRequest): GenericPage<LedgerLogVo> {
        val page = GenericPage<LedgerLogVo>().build(request)
        val orgIds = readOrgIds(setOf(CollectionConst.LEDGER_CORRECT))
        val filters = LedgerLogQueryFilters.from { request.getParameter(it) }
        val q = CollectionLedgerLogs.selectAll()
        q.andWhere { CollectionLedgerLogs.orgId inList orgIds }
        request.getParameter("recordId")?.trim()?.takeIf(String::isNotBlank)?.let { id -> q.andWhere { CollectionLedgerLogs.id eq id } }
        filters.keyword?.let { keyword ->
            val matchedCollectionIds = collectionIdsMatchingLedgerKeyword(keyword, orgIds)
            val collectionMatch =
                if (matchedCollectionIds.isNotEmpty()) CollectionLedgerLogs.collectionId inList matchedCollectionIds
                else CollectionLedgerLogs.collectionId eq "__NO_COLLECTION_MATCH__"
            q.andWhere {
                (CollectionLedgerLogs.totalRegNo like "%$keyword%") or
                        (CollectionLedgerLogs.collectionId like "%$keyword%") or
                        (CollectionLedgerLogs.refBizId like "%$keyword%") or
                        collectionMatch or
                        (CollectionLedgerLogs.field like "%$keyword%") or
                        (CollectionLedgerLogs.before like "%$keyword%") or
                        (CollectionLedgerLogs.after like "%$keyword%") or
                        (CollectionLedgerLogs.reason like "%$keyword%")
            }
        }
        filters.collectionId?.let { q.andWhere { CollectionLedgerLogs.collectionId eq it } }
        filters.totalRegNo?.let { q.andWhere { CollectionLedgerLogs.totalRegNo like "%$it%" } }
        filters.refBizId?.let { q.andWhere { CollectionLedgerLogs.refBizId eq it } }
        filters.action?.let { q.andWhere { CollectionLedgerLogs.action eq it } }
        filters.field?.let { q.andWhere { CollectionLedgerLogs.field like "%$it%" } }
        filters.createTimeStart?.let { q.andWhere { CollectionLedgerLogs.createTime greaterEq it } }
        filters.createTimeEnd?.let { q.andWhere { CollectionLedgerLogs.createTime lessEq it } }
        q.orderBy(CollectionLedgerLogs.createTime to SortOrder.DESC, CollectionLedgerLogs.id to SortOrder.DESC)

        val rows = if (page.pageSize > 0) {
            page.total = q.count()
            q.limit(page.pageSize).offset(page.startRow)
            CollectionLedgerLogDao.wrapRows(q).toList()
        } else {
            val all = CollectionLedgerLogDao.wrapRows(q).toList()
            page.total = all.size.toLong()
            all
        }
        page.list = enrichLedgerLogCollectionInfo(rows.map { it.toModel() }, orgIds)
        return page
    }

    private fun enrichLedgerLogCollectionInfo(items: List<LedgerLogVo>, orgIds: Set<String>): List<LedgerLogVo> {
        val ids = items.mapNotNull { it.collectionId }.toSet()
        if (ids.isEmpty()) return items
        val infoById = Collections
            .selectAll()
            .where { (Collections.id inList ids) and (Collections.orgId inList orgIds) }
            .associate {
                it[Collections.id].value to LedgerLogCollectionInfo(name = it[Collections.name])
            }
        return LedgerLogCollectionInfoEnricher.enrich(items, infoById)
    }

    private fun collectionIdsMatchingLedgerKeyword(keyword: String, orgIds: Set<String>): List<String> {
        val q = Collections.selectAll()
        q.andWhere { Collections.orgId inList orgIds }
        q.andWhere { Collections.name like "%$keyword%" }
        return q.map { it[Collections.id].value }
    }

    /** 向 CMS 提供当前已核查公开稿；此动作不表示官网或现场已经公开。 */
    @CacheEvict(allEntries = true)
    fun publishCms(id: String): String {
        val node = findById(id) ?: throw BadRequestException("藏品不存在")
        CollectionCurrentOperationAccess.require(scope, node.orgId, true, setOf(CollectionConst.PUBLISH), "藏品公开")
        CollectionPublishPolicy.requirePublishable(node.catalogStatus, node.lifeStatus)
        return disclosureService.enableLatestResource(id)
    }

    /** 仅输出核查后的公开内容；available=false 要求 CMS 移除旧内容。 */
    fun publishedFeed(): List<CollectionPublicFeedVo> = disclosureService.feed(EnumDisclosureSubject.RESOURCE)

    /** 普查导出：按可见馆导出藏品 14 项基本指标（前端转 CSV/Excel）。 */
    fun surveyStatus(query: CollectionQueryVo): Map<String,Any?> {
        val rows=CollectionDao.wrapRows(buildQuery(query, readOrgIds(setOf(CollectionConst.STAT)))).map { it.toModel() }
        return mapOf("exportableCount" to rows.count { it.registered && it.lifeStatus!=EnumCollectionStatus.DEREGISTERED },
            "excluded" to rows.filter { !it.registered || it.lifeStatus==EnumCollectionStatus.DEREGISTERED }.map {
                mapOf("collectionId" to it.id,"totalRegNo" to it.totalRegNo,"name" to it.name,"reason" to
                    if(it.lifeStatus==EnumCollectionStatus.DEREGISTERED) "已退出，原总账继续保留" else if(it.lifeStatus==EnumCollectionStatus.PENDING && it.catalogStatus!=EnumCatalogStatus.APPROVED) "尚未正式登记" else "历史正式登记依据待核实")
            })
    }

    internal fun exchangeCollections(query: CollectionQueryVo): List<CollectionVo> {
        val q = buildQuery(query, readOrgIds(setOf(CollectionConst.STAT)))
        q.andWhere { Collections.isRegistered() and (Collections.catalogStatus eq EnumCatalogStatus.APPROVED) and (Collections.lifeStatus neq EnumCollectionStatus.DEREGISTERED) }
        q.orderBy(Collections.totalRegNo to SortOrder.ASC)
        return enrich(CollectionDao.wrapRows(q).map { it.toModel() })
    }

    fun exportSurvey(query: CollectionQueryVo): List<CollectionSurveyExportVo> {
        val rows = exchangeCollections(query)
        val orgs = CollectionOrgDao.all().associateBy { it.id.value }
        return rows.map { row -> val org=row.orgId?.let(orgs::get); CollectionSurveyExportBuilder.build(row, org?.name, org?.exchangeCode) }
    }

    // ── 供过程服务（鉴定/修复/借展/盘点/调拨）调用的内部钩子 ──

    /** 变更藏品生命周期状态。 */
    fun changeLifeStatus(
        collectionId: String,
        status: EnumCollectionStatus,
        authorizedBizType: String? = null,
        authorizedBizId: String? = null,
        sourceBizType: String? = authorizedBizType,
        sourceBizId: String? = authorizedBizId,
    ) {
        val node = CollectionDao.find { Collections.id eq collectionId }.forUpdate().singleOrNull() ?: throw BadRequestException("藏品不存在")
        if (node.inventoryHold && (status in outboundStatuses || status == EnumCollectionStatus.PRE_OUT) && !restrictedOperations.permits(collectionId,authorizedBizType,authorizedBizId)) throw BadRequestException("藏品存在清点异常，须核实处理并签署安全结论后才能再次调取")
        if (status in outboundStatuses && node.lifeStatus != EnumCollectionStatus.PRE_OUT) {
            CollectionOutboundPolicy.requireInStore(node.lifeStatus, outboundActionName(status))
        }
        val before = node.lifeStatus
        CollectionLifecyclePolicy.requireTransition(before, status)
        if (before == status) return
        node.lifeStatus = status
        CollectionLedgerLogDao.new {
            orgId = node.orgId
            this.collectionId = node.id.value
            totalRegNo = node.totalRegNo
            action = EnumLedgerAction.CORRECT
            field = "lifeStatus"
            this.before = before.name
            after = status.name
            reason = "生命周期状态变更" + (sourceBizType?.let { "（$it）" } ?: "")
            refBizId = sourceBizId
        }
    }

    private val outboundStatuses = setOf(
        EnumCollectionStatus.IN_USE,
        EnumCollectionStatus.RESTORING,
        EnumCollectionStatus.ON_LOAN,
    )

    private fun outboundActionName(status: EnumCollectionStatus): String = when (status) {
        EnumCollectionStatus.RESTORING -> "修复"
        EnumCollectionStatus.ON_LOAN -> "借展"
        else -> "提用"
    }

    /** 跨馆调拨过户：改 orgId + 留痕。 */
    fun changeOrg(collectionId: String, toOrgId: String, reason: String?, refBizId: String? = null) {
        val node = findById(collectionId) ?: throw BadRequestException("藏品不存在")
        val before = node.orgId
        registrationNumberService.requireTransferAvailable(toOrgId, node.totalRegNo, node.id.value)
        retainedLedgerService.retain(node,toOrgId,refBizId ?: throw BadRequestException("跨馆转移必须关联已办理的调拨申请"))
        registrationNumberService.retire(before, node.totalRegNo, node.id.value, reason ?: "跨馆调拨", refBizId)
        node.orgId = toOrgId
        CollectionCatalogDao.find { CollectionCatalogs.collectionId eq collectionId }.forEach { it.orgId=toOrgId }
        registrationNumberService.markTransferred(toOrgId, node.totalRegNo, node.id.value, reason ?: "跨馆调拨", refBizId)
        CollectionOrgChangeLedgerPlanner.plan(before, toOrgId, reason, refBizId).forEach { log ->
            writeLog(node, log)
        }
    }

    /** 退出馆藏执行（由 DeregistrationService 调用）：置 DEREGISTERED + 回写退出馆藏凭证号 + 写留痕，总登记号不回收。 */
    fun executeDeregister(collectionId: String, reason: String?, refBizId: String?, deregDocNo: String? = null, statutoryReason: EnumDeregReason? = null) {
        val node = findById(collectionId) ?: throw BadRequestException("藏品不存在")
        val executionId = refBizId ?: throw BadRequestException("退出须有关联执行凭证")
        val beforeLifeStatus = node.lifeStatus
        CollectionLifecyclePolicy.requireTransition(beforeLifeStatus, EnumCollectionStatus.DEREGISTERED)
        node.lifeStatus = EnumCollectionStatus.DEREGISTERED
        CollectionLedgerFieldChangeRecorder.deregVoucher(node,deregDocNo?.takeIf { it.isNotBlank() } ?: executionId,
            "退出馆藏执行凭证",executionId)
        CollectionLedgerStatRecorder.record(node, io.hizhengfu.cloud.collection.domain.EnumLedgerStatEvent.DEREGISTER, executionId, reason=reason, statReason=CollectionCategoryReasonBalances.deregistrationReason(statutoryReason))
        CollectionLedgerLogDao.new {
            orgId = node.orgId
            this.collectionId = node.id.value
            totalRegNo = node.totalRegNo
            action = EnumLedgerAction.DEREGISTER
            field = "lifeStatus"
            before = beforeLifeStatus.name
            after = EnumCollectionStatus.DEREGISTERED.name
            this.reason = reason
            this.refBizId = executionId
        }
    }

    private fun buildQuery(query: CollectionQueryVo, orgIds: Set<String> = readOrgIds()): Query {
        val q = Collections.selectAll()
        CollectionOrgFilterPolicy.resolve(orgIds, query.orgId)
            ?.let { ids -> q.andWhere { Collections.orgId inList ids } }
        query.collectionId?.takeIf(String::isNotBlank)?.let { id -> q.andWhere { Collections.id eq id } }
        query.collectionIds?.let { raw ->
            val ids = raw.split(',').map(String::trim).filter(String::isNotBlank).distinct()
            if (ids.isEmpty() || ids.size > 200) throw BadRequestException("选择清单应包含 1 至 200 条藏品记录")
            q.andWhere { Collections.id inList ids }
        }
        query.nature?.let { value -> q.andWhere { Collections.nature eq value } }
        when (query.relicLevelScope) {
            io.hizhengfu.cloud.collection.domain.EnumCollectionRelicLevelScope.UNGRADED -> q.andWhere {
                (Collections.nature eq EnumCollectionNature.RELIC) and
                    (Collections.lifeStatus neq EnumCollectionStatus.DEREGISTERED) and
                    (Collections.level.isNull() or (Collections.level eq EnumRelicLevel.UNRATED))
            }
            io.hizhengfu.cloud.collection.domain.EnumCollectionRelicLevelScope.PRECIOUS -> q.andWhere {
                (Collections.nature eq EnumCollectionNature.RELIC) and
                    (Collections.level inList listOf(EnumRelicLevel.FIRST, EnumRelicLevel.SECOND, EnumRelicLevel.THIRD))
            }
            else -> Unit
        }
        when (query.registrationScope) {
            io.hizhengfu.cloud.collection.domain.EnumCollectionRegistrationScope.REGISTERED -> q.andWhere {
                Collections.isRegistered() and (Collections.lifeStatus neq EnumCollectionStatus.DEREGISTERED)
            }
            io.hizhengfu.cloud.collection.domain.EnumCollectionRegistrationScope.PENDING -> q.andWhere {
                Collections.isUnregistered() and (Collections.lifeStatus eq EnumCollectionStatus.PENDING) and
                    (Collections.catalogStatus neq EnumCatalogStatus.APPROVED)
            }
            io.hizhengfu.cloud.collection.domain.EnumCollectionRegistrationScope.UNVERIFIED -> q.andWhere {
                Collections.isUnregistered() and (Collections.lifeStatus neq EnumCollectionStatus.DEREGISTERED) and
                    ((Collections.lifeStatus neq EnumCollectionStatus.PENDING) or (Collections.catalogStatus eq EnumCatalogStatus.APPROVED))
            }
            io.hizhengfu.cloud.collection.domain.EnumCollectionRegistrationScope.DEREGISTERED -> q.andWhere { Collections.lifeStatus eq EnumCollectionStatus.DEREGISTERED }
            else -> Unit
        }
        query.keyword?.takeIf { it.isNotBlank() }?.let { kw ->
            for (term in kw.trim().split(Regex("\\s+")).filter(String::isNotBlank)) q.andWhere {
                (Collections.name like "%$term%") or (Collections.originalName like "%$term%") or
                    (Collections.totalRegNo like "%$term%") or (Collections.classNo like "%$term%") or
                    (Collections.auxNo like "%$term%") or (Collections.rfidTag like "%$term%") or
                    (Collections.era like "%$term%") or (Collections.texture like "%$term%") or
                    (Collections.inscription like "%$term%") or (Collections.feature like "%$term%")
            }
        }
        query.totalRegNo?.takeIf { it.isNotBlank() }?.let { number ->
            q.andWhere { if (query.numberMatch == io.hizhengfu.cloud.collection.domain.EnumCollectionNumberMatch.EXACT)
                Collections.totalRegNo eq number else Collections.totalRegNo like "%$number%" }
        }
        query.classNo?.takeIf { it.isNotBlank() }?.let { q.andWhere { Collections.classNo like "%$it%" } }
        query.era?.takeIf { it.isNotBlank() }?.let { q.andWhere { Collections.era like "%$it%" } }
        query.texture?.takeIf { it.isNotBlank() }?.let { q.andWhere { Collections.texture like "%$it%" } }
        query.level?.let { lv -> q.andWhere { Collections.level eq lv } }
        query.source?.let { s -> q.andWhere { Collections.sourceType eq s } }
        query.lifeStatus?.let { st -> q.andWhere { Collections.lifeStatus eq st } }
        query.lifeStatuses?.takeIf { it.isNotEmpty() }?.let { statuses ->
            q.andWhere { Collections.lifeStatus inList statuses }
        }
        query.catalogStatus?.let { st -> q.andWhere { Collections.catalogStatus eq st } }
        query.completeness?.let { st -> q.andWhere { Collections.completeness eq st } }
        query.categoryId?.let { c -> q.andWhere { Collections.categoryId eq c } }
        query.warehouseId?.let { w -> q.andWhere { Collections.warehouseId eq w } }
        query.registeredDateStart?.let { q.andWhere { Collections.registeredAt greaterEq it.atStartOfDay() } }
        query.registeredDateEnd?.let { q.andWhere { Collections.registeredAt less it.plusDays(1).atStartOfDay() } }
        query.acquireDateStart?.let { q.andWhere { Collections.acquireDate greaterEq it } }
        query.acquireDateEnd?.let { q.andWhere { Collections.acquireDate lessEq it } }
        query.createTimeStart?.let { q.andWhere { Collections.createTime greaterEq it } }
        query.createTimeEnd?.let { q.andWhere { Collections.createTime lessEq it } }

        query.hasImage?.let { has ->
            val linked = exists(CollectionMedias.select(CollectionMedias.id).where {
                (CollectionMedias.collectionId eq Collections.id) and (CollectionMedias.orgId eq Collections.orgId)
            })
            if (has) {
                q.andWhere {
                    ((Collections.coverImage.isNotNull()) and (Collections.coverImage neq "")) or
                        linked
                }
            } else {
                val noCover = Collections.coverImage.isNull() or (Collections.coverImage eq "")
                q.andWhere { noCover and NotOp(linked) }
            }
        }
        query.hasAttachment?.let { has ->
            val linked = exists(CollectionAttachments.select(CollectionAttachments.id).where {
                (CollectionAttachments.collectionId eq Collections.id) and (CollectionAttachments.orgId eq Collections.orgId)
            })
            q.andWhere { if (has) linked else NotOp(linked) }
        }
        return q
    }

    private fun scopedFind(id: String, orgIds: Set<String> = readOrgIds()): CollectionDao {
        val node = findById(id) ?: throw BadRequestException("藏品不存在")
        OrgScopedReadPolicy.requireVisible(orgIds, node.orgId, "藏品")
        return node
    }

    private fun readOrgIds(additionalPermissions: Set<String> = emptySet()): Set<String> =
        CollectionSearchReadAccess.require(scope, additionalPermissions = additionalPermissions, allowHistoricalOrgs = true).orgIds

    private fun requireLedgerWrite(orgId: String, permissions: Set<String>) {
        val actor = CollectionCurrentOperationAccess.require(scope, orgId, true, permissions, "藏品登账")
        CollectionDutySeparationPolicy.requireLedgerOperation(actor.permissions, actor.roleCodes)
    }

    private fun scopedLedgerWrite(id: String, permissions: Set<String> = setOf(CollectionConst.LEDGER_CREATE, CollectionConst.LEDGER_CORRECT)): CollectionDao {
        val node = findById(id) ?: throw BadRequestException("藏品不存在")
        requireLedgerWrite(node.orgId, permissions)
        return node
    }

    private fun writeLog(
        node: CollectionDao,
        action: EnumLedgerAction,
        field: String?,
        before: String?,
        after: String?,
        reason: String?,
    ) {
        CollectionLedgerLogDao.new {
            orgId = node.orgId
            collectionId = node.id.value
            totalRegNo = node.totalRegNo
            this.action = action
            this.field = field
            this.before = before
            this.after = after
            this.reason = reason
        }
    }

    private fun writeLog(node: CollectionDao, plan: LedgerLogPlan) {
        CollectionLedgerLogDao.new {
            orgId = plan.orgId
            collectionId = node.id.value
            totalRegNo = node.totalRegNo
            action = plan.action
            field = plan.field
            before = plan.before
            after = plan.after
            reason = plan.reason
            refBizId = plan.refBizId
        }
    }

    private fun importSnapshot(vo: CollectionVo): Map<String, Any?> = linkedMapOf(
        "totalRegNo" to vo.totalRegNo,
        "catalogStatus" to vo.catalogStatus?.name,
        "nature" to vo.nature,
        "classNo" to vo.classNo,
        "auxNo" to vo.auxNo,
        "name" to vo.name,
        "originalName" to vo.originalName,
        "categoryId" to vo.categoryId,
        "era" to vo.era,
        "texture" to vo.texture,
        "textureCategory1" to vo.textureCategory1,
        "textureCategory2" to vo.textureCategory2,
        "level" to vo.level?.name,
        "quantity" to vo.quantity,
        "actualQuantity" to vo.actualQuantity,
        "actualQuantityUnit" to vo.actualQuantityUnit,
        "dimensionUnit" to vo.dimensionUnit,
        "entryDate" to vo.entryDate,
        "receiptCompletedAt" to vo.receiptCompletedAt,
        "registrationDetails" to vo.registrationDetails,
        "unit" to vo.unit,
        "source" to vo.source?.name,
        "sourceDesc" to vo.sourceDesc,
        "acquireDate" to vo.acquireDate?.toString(),
        "acquireDateRange" to vo.acquireDateRange,
        "acquireYear" to vo.acquireYear,
        "completeness" to vo.completeness?.name,
        "completeStatus" to vo.completeStatus,
        "dimensions" to vo.dimensions,
        "weight" to vo.weight,
        "weightRange" to vo.weightRange,
        "weightUnit" to vo.weightUnit,
        "region" to vo.region,
        "volume" to vo.volume,
        "volumeUnit" to vo.volumeUnit,
        "color" to vo.color,
        "sourceNo" to vo.sourceNo,
        "country" to vo.country,
        "culture" to vo.culture,
        "inscription" to vo.inscription,
        "feature" to vo.feature,
        "preserveState" to vo.preserveState?.name,
        "author" to vo.author,
        "edition" to vo.edition,
        "volumeNo" to vo.volumeNo,
        "valuation" to vo.valuation,
        "coverImage" to vo.coverImage,
        "warehouseId" to vo.warehouseId,
        "remark" to vo.remark,
    )
}
