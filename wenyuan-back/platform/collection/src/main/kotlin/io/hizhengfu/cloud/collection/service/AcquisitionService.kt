package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.AcquisitionDao
import io.hizhengfu.cloud.collection.dao.AcquisitionPlanDao
import io.hizhengfu.cloud.collection.dao.AcquisitionPlans
import io.hizhengfu.cloud.collection.dao.AcquisitionVo
import io.hizhengfu.cloud.collection.dao.Acquisitions
import io.hizhengfu.cloud.collection.dao.CollectionCategoryDao
import io.hizhengfu.cloud.collection.dao.CollectionRetainedLedgerDao
import io.hizhengfu.cloud.collection.dao.CollectionRetainedLedgers
import io.hizhengfu.cloud.collection.dao.Collections
import io.hizhengfu.cloud.collection.dao.EntryVoucherDao
import io.hizhengfu.cloud.collection.dao.EntryVouchers
import io.hizhengfu.cloud.collection.domain.AcquisitionImportCommitResultVo
import io.hizhengfu.cloud.collection.domain.AcquisitionImportRowVo
import io.hizhengfu.cloud.collection.domain.AcquisitionImportValidateResultVo
import io.hizhengfu.cloud.collection.domain.CollectionImportErrorVo
import io.hizhengfu.cloud.collection.domain.CollectionImportTemplateColumnVo
import io.hizhengfu.cloud.collection.domain.DimensionVo
import io.hizhengfu.cloud.collection.domain.EnumCompleteness
import io.hizhengfu.cloud.collection.domain.EnumRelicLevel
import io.hizhengfu.cloud.collection.domain.EnumSource
import io.hizhengfu.cloud.collection.domain.EnumVoucherStatus
import io.hizhengfu.cloud.common.domain.EnumContentStatus
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.common.domain.GenericPage
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.flow.domain.FlowCompletedEvent
import io.hizhengfu.cloud.flow.service.FlowInstanceService
import io.hizhengfu.cloud.flow.service.FlowService
import io.hizhengfu.cloud.flow.service.FlowUserOperation
import jakarta.servlet.http.HttpServletRequest
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Op
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
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.cache.annotation.CacheConfig
import org.springframework.cache.annotation.CacheEvict
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

internal data class AcquisitionRecordValues(
    val planId: String?,
    val name: String?,
    val categoryId: String?,
    val era: String?,
    val texture: String?,
    val proposedLevel: EnumRelicLevel?,
    val source: EnumSource?,
    val quantity: Int?,
    val unit: String?,
    val acquireDate: LocalDate?,
    val dimensions: DimensionVo?,
    val weight: String?,
    val completeness: EnumCompleteness?,
    val completeStatus: String?,
    val holder: String?,
    val proposedPrice: Long?,
    val appraisedValue: Long?,
    val reason: String?,
    val images: String?,
    val expertOpinionAttachmentIds: String?,
    val approvalAttachmentIds: String?,
    val contractAttachmentIds: String?,
    val invoiceAttachmentIds: String?,
) {
    companion object {
        fun fromSave(vo: AcquisitionVo) = AcquisitionRecordValues(
            planId = vo.planId,
            name = vo.name,
            categoryId = vo.categoryId,
            era = vo.era,
            texture = vo.texture,
            proposedLevel = vo.proposedLevel,
            source = vo.source,
            quantity = vo.quantity,
            unit = vo.unit,
            acquireDate = vo.acquireDate,
            dimensions = vo.dimensions,
            weight = vo.weight,
            completeness = vo.completeness,
            completeStatus = vo.completeStatus,
            holder = vo.holder,
            proposedPrice = vo.proposedPrice,
            appraisedValue = vo.appraisedValue,
            reason = vo.reason,
            images = vo.images,
            expertOpinionAttachmentIds = vo.expertOpinionAttachmentIds,
            approvalAttachmentIds = vo.approvalAttachmentIds,
            contractAttachmentIds = vo.contractAttachmentIds,
            invoiceAttachmentIds = vo.invoiceAttachmentIds,
        )
    }
}

data class AcquisitionCollectionInfo(
    val totalRegNo: String?,
    val name: String?,
)

/** 入馆凭证已成为征集登账的强制入口；保留参数仅用于升级期契约兼容。 */
internal object AcquisitionRegistrationPolicy {
    @Suppress("UNUSED_PARAMETER")
    fun shouldAutoRegister(autoEnabled: Boolean, collectionId: String?): Boolean = false
}

object AcquisitionCollectionInfoEnricher {
    fun enrich(items: List<AcquisitionVo>, collectionInfoByAcquisitionId: Map<String, AcquisitionCollectionInfo>): List<AcquisitionVo> =
        items.map { item ->
            val info = item.id?.let { collectionInfoByAcquisitionId[it] }
            item.copy(
                collectionTotalRegNo = info?.totalRegNo,
                collectionName = info?.name,
            )
        }
}

/**
 * 征集入藏（流程驱动）。征集申请→论证/评估→审批，审批通过后进入待入馆池。
 */
@Component
@Transactional
@CacheConfig(cacheNames = [CollectionConst.CACHE_ACQUISITION])
class AcquisitionService(
    flowInstanceService: FlowInstanceService,
    private val scope: CollectionScopeResolver,
    private val attachmentBinder: BusinessAttachmentBinder,
    private val compliance: AcquisitionComplianceService,
    private val retainedLedgers: CollectionRetainedLedgerService,
) : FlowService<AcquisitionDao>(AcquisitionDao, flowInstanceService, CollectionConst.CACHE_ACQUISITION) {

    override fun businessType() = CollectionConst.BIZ_ACQUISITION
    override fun definitionKey() = CollectionConst.FLOW_ACQUISITION
    override fun businessTypeName() = "征集入藏"
    override fun authorizeUserOperation(entity: AcquisitionDao, operation: FlowUserOperation) {
        scope.requireFlowAccess(entity.orgId, operation, "征集申请")
        if(entity.designatedReceptionId!=null && operation!=FlowUserOperation.FLOW_INFO)throw BadRequestException("指定接收通过原业务记录办理，不能提交、撤销或重新激活为普通征集审批")
        if (operation == FlowUserOperation.FLOW_INFO) AcquisitionComplianceService.requireConfidentialAccess(scope,entity.orgId)
        if (operation == FlowUserOperation.SAVE_DRAFT) compliance.requireEditable(entity)
    }
    override fun buildTitle(entity: AcquisitionDao) = "征集入藏：${entity.name}"
    override fun buildFormData(entity: AcquisitionDao) = mapOf(
        "name" to entity.name,
        "proposedLevel" to entity.proposedLevel?.name,
        "proposedPrice" to entity.proposedPrice,
        "appraisedValue" to entity.appraisedValue,
        "dossier" to compliance.case(entity.id.value)?.dossier,
        "experts" to compliance.experts(entity.id.value),
        "completion" to compliance.case(entity.id.value)?.completion,
        "source" to entity.source?.name,
        "reason" to entity.reason,
    ).let { form -> if (AcquisitionComplianceService.canReadConfidential(scope,entity.orgId)) form else mapOf("name" to entity.name,"source" to entity.source?.name) }

    protected override fun beforeSubmit(entity: AcquisitionDao) {
        requireSubmissionMaterials(entity)
        compliance.sealPhase(entity,"SUBMITTED")
    }

    private fun requireSubmissionMaterials(entity: AcquisitionDao) {
        if (entity.name.isBlank() || entity.source == null || entity.quantity <= 0 || entity.unit.isBlank() ||
            entity.acquireDate == null || entity.holder.isNullOrBlank() || entity.reason.isNullOrBlank())
            throw BadRequestException("提交征集确认前请补齐对象名称、来源方式、数量单位、实际取得日期、来源人及征集理由")
        compliance.requirePrepared(entity)
        if (entity.completeness == null || entity.completeStatus.isNullOrBlank()) {
            throw BadRequestException("完残程度和完残状况必填")
        }
        if (entity.expertOpinionAttachmentIds.isNullOrBlank()) throw BadRequestException("专家鉴定意见材料必填")
        if (entity.approvalAttachmentIds.isNullOrBlank()) throw BadRequestException("征集审批材料必填")
        if (entity.source == EnumSource.PURCHASE) {
            if (entity.contractAttachmentIds.isNullOrBlank()) throw BadRequestException("收购征集合同必填")
            if (entity.invoiceAttachmentIds.isNullOrBlank()) throw BadRequestException("收购发票或支付凭证必填")
        }
    }

    fun findPage(
        request: HttpServletRequest,
        keyword: String? = null,
        proposedLevel: EnumRelicLevel? = null,
        source: EnumSource? = null,
        flowStatus: EnumFlowStatus? = null,
    ): GenericPage<AcquisitionVo> {
        val page = GenericPage<AcquisitionVo>().build(request)
        val access = CollectionSearchReadAccess.require(scope, acquisition = true)
        val q = Acquisitions.selectAll()
        q.andWhere { Acquisitions.orgId inList access.orgIds }
        keyword?.takeIf { it.isNotBlank() }?.let { kw ->
            val collectionMatch = collectionsMatchingKeyword(kw, access.orgIds)
                .groupBy({ it.second }, { it.first })
                .map { (orgId, ids) -> (Acquisitions.orgId eq orgId) and (Acquisitions.collectionId inList ids) }
                .reduceOrNull { left, right -> left or right } ?: Op.FALSE
            val historicalIds = historicalAcquisitionIdsMatchingKeyword(kw, access.orgIds)
            val historicalMatch = if (historicalIds.isEmpty()) Op.FALSE else Acquisitions.id inList historicalIds
            q.andWhere {
                (Acquisitions.name like "%$kw%") or
                    collectionMatch or historicalMatch or
                    (Acquisitions.era like "%$kw%") or
                    (Acquisitions.texture like "%$kw%") or
                    (Acquisitions.holder like "%$kw%") or
                    (if (access.acquisitionConfidential) (Acquisitions.reason like "%$kw%") or
                        (Acquisitions.images like "%$kw%") else Op.FALSE)
            }
        }
        proposedLevel?.let { lv -> q.andWhere { Acquisitions.proposedLevel eq lv } }
        source?.let { s -> q.andWhere { Acquisitions.sourceType eq s } }
        flowStatus?.let { st -> q.andWhere { Acquisitions.flowStatus eq st } }
        request.getParameter("planId")?.trim()?.takeIf { it.isNotBlank() }?.let { q.andWhere { Acquisitions.planId eq it } }
        request.getParameter("categoryId")?.trim()?.takeIf { it.isNotBlank() }?.let { q.andWhere { Acquisitions.categoryId eq it } }
        request.getParameter("holder")?.trim()?.takeIf { it.isNotBlank() }?.let { q.andWhere { Acquisitions.holder like "%$it%" } }
        request.getParameter("hasImage")?.trim()?.takeIf { it.isNotBlank() }?.toBooleanStrictOrNull()?.let { has ->
            if (!access.acquisitionConfidential) throw BadRequestException("征集附件保密，当前账号不能按附件有无筛选")
            if (has) q.andWhere { Acquisitions.images.isNotNull() and Acquisitions.images.neq("") }
            else q.andWhere { Acquisitions.images.isNull() or Acquisitions.images.eq("") }
        }
        request.getParameter("registered")?.trim()?.takeIf { it.isNotBlank() }?.toBooleanStrictOrNull()?.let { registered ->
            if (registered) q.andWhere { Acquisitions.collectionId.isNotNull() and Acquisitions.collectionId.neq("") }
            else q.andWhere { Acquisitions.collectionId.isNull() or Acquisitions.collectionId.eq("") }
        }
        request.getParameter("createTimeStart")?.trim()?.takeIf { it.isNotBlank() }?.let { value ->
            runCatching { LocalDate.parse(value).atStartOfDay() }.getOrNull()?.let { q.andWhere { Acquisitions.createTime greaterEq it } }
        }
        request.getParameter("createTimeEnd")?.trim()?.takeIf { it.isNotBlank() }?.let { value ->
            runCatching { LocalDate.parse(value).plusDays(1).atStartOfDay().minusNanos(1) }.getOrNull()
                ?.let { q.andWhere { Acquisitions.createTime lessEq it } }
        }
        q.orderBy(Acquisitions.createTime to SortOrder.DESC)
        @Suppress("UNCHECKED_CAST")
        val daoPage = findPage(q, page as GenericPage<AcquisitionDao>)
        page.list = daoPage.data?.map { AcquisitionComplianceService.visibleRecord(scope,it.toModel()) }
            ?.let { enrichCollectionInfo(it, access.orgIds, inspectHistoricalEvidence=true) }
        @Suppress("UNCHECKED_CAST")
        return page as GenericPage<AcquisitionVo>
    }

    fun get(id: String): AcquisitionVo? {
        val access = CollectionSearchReadAccess.require(scope, acquisition = true)
        return findById(id)
            ?.also { OrgScopedReadPolicy.requireVisible(access.orgIds, it.orgId, "征集申请") }
            ?.toModel()
            ?.let { AcquisitionComplianceService.visibleRecord(scope,it) }
            ?.let { enrichCollectionInfo(listOf(it), access.orgIds).first() }
    }

    fun importTemplate(): List<CollectionImportTemplateColumnVo> = listOf(
        CollectionImportTemplateColumnVo("rowNo", "行号", sample = "2"),
        CollectionImportTemplateColumnVo("name", "拟征集名称", required = true, sample = "青花瓷碗"),
        CollectionImportTemplateColumnVo("categoryName", "文物分类名称", sample = "瓷器"),
        CollectionImportTemplateColumnVo("era", "年代", sample = "明"),
        CollectionImportTemplateColumnVo("texture", "质地", dictType = CollectionConst.DICT_TEXTURE, sample = "瓷"),
        CollectionImportTemplateColumnVo("proposedLevel", "拟定级别", dictType = CollectionConst.DICT_RELIC_LEVEL, sample = "未定级"),
        CollectionImportTemplateColumnVo("source", "征集方式", dictType = CollectionConst.DICT_SOURCE, sample = "捐赠"),
        CollectionImportTemplateColumnVo("quantity", "数量", sample = "1"),
        CollectionImportTemplateColumnVo("unit", "计量单位", sample = "件"),
        CollectionImportTemplateColumnVo("acquireDate", "取得日期", sample = "2026-07-02"),
        CollectionImportTemplateColumnVo("height", "通高"),
        CollectionImportTemplateColumnVo("mouth", "口径"),
        CollectionImportTemplateColumnVo("bottom", "底径"),
        CollectionImportTemplateColumnVo("belly", "腹径"),
        CollectionImportTemplateColumnVo("length", "长"),
        CollectionImportTemplateColumnVo("width", "宽"),
        CollectionImportTemplateColumnVo("diameter", "直径"),
        CollectionImportTemplateColumnVo("weight", "质量/重量"),
        CollectionImportTemplateColumnVo("completeness", "完残程度", dictType = CollectionConst.DICT_COMPLETENESS, sample = "完整"),
        CollectionImportTemplateColumnVo("completeStatus", "完残状况"),
        CollectionImportTemplateColumnVo("holder", "持有人/卖方/捐赠人", sample = "张三"),
        CollectionImportTemplateColumnVo("proposedPrice", "拟征集价（分）", sample = "120000"),
        CollectionImportTemplateColumnVo("appraisedValue", "评估价（分）", sample = "150000"),
        CollectionImportTemplateColumnVo("reason", "征集理由/价值论证", sample = "符合本馆征集方向"),
        CollectionImportTemplateColumnVo("imageNames", "附件/影像文件名", sample = "已上传文件名，分号分隔"),
        CollectionImportTemplateColumnVo("expertOpinionAttachmentNames", "专家鉴定意见表附件文件名", sample = "已上传文件名，分号分隔"),
        CollectionImportTemplateColumnVo("approvalAttachmentNames", "征集审批表/清单附件文件名", sample = "已上传文件名，分号分隔"),
        CollectionImportTemplateColumnVo("contractAttachmentNames", "征集合同附件文件名", sample = "已上传文件名，分号分隔"),
        CollectionImportTemplateColumnVo("invoiceAttachmentNames", "发票附件文件名", sample = "已上传文件名，分号分隔"),
    )

    fun validateImport(rows: List<AcquisitionImportRowVo>): AcquisitionImportValidateResultVo {
        val org = scope.currentOrgId()
        val categories = CollectionCategoryDao.all().filter(CollectionCategoryPolicy::isStandard)
        val names = CollectionImportNameResolver(categories.map { CollectionImportNameResolver.Entry(it.id.value, it.name, it.parentId) })
        val errors = mutableListOf<CollectionImportErrorVo>()
        val normalized = rows.mapIndexed { index, row ->
            val refs = CollectionImportRowReferences(row.rowNo.takeIf { it > 0 } ?: index + 2, errors)
            row.copy(
                categoryId = refs.read("categoryName") { names.resolve(row.categoryName, row.categoryId, "文物分类") },
                images = refs.read("imageNames") { attachmentBinder.resolveImportNames(row.imageNames, row.images) },
                expertOpinionAttachmentIds = refs.read("expertOpinionAttachmentNames") { attachmentBinder.resolveImportNames(row.expertOpinionAttachmentNames, row.expertOpinionAttachmentIds) },
                approvalAttachmentIds = refs.read("approvalAttachmentNames") { attachmentBinder.resolveImportNames(row.approvalAttachmentNames, row.approvalAttachmentIds) },
                contractAttachmentIds = refs.read("contractAttachmentNames") { attachmentBinder.resolveImportNames(row.contractAttachmentNames, row.contractAttachmentIds) },
                invoiceAttachmentIds = refs.read("invoiceAttachmentNames") { attachmentBinder.resolveImportNames(row.invoiceAttachmentNames, row.invoiceAttachmentIds) },
            )
        }
        val result = AcquisitionImportValidator.validateRows(normalized, categories.map { it.id.value }.toSet())
        return if (errors.isEmpty()) result else result.copy(valid = false, errors = result.errors + errors, items = emptyList())
    }

    @CacheEvict(allEntries = true)
    fun commitImport(rows: List<AcquisitionImportRowVo>): AcquisitionImportCommitResultVo {
        val result = validateImport(rows)
        if (!result.valid) return AcquisitionImportCommitResultVo(valid = false, errors = result.errors)
        val ids = result.items.map { save(it) }
        return AcquisitionImportCommitResultVo(valid = true, createdIds = ids)
    }

    /** 新建/编辑征集申请（草稿）。 */
    @CacheEvict(allEntries = true)
    fun save(vo: AcquisitionVo): String {
        val started = System.nanoTime()
        if(vo.source==EnumSource.DESIGNATED || vo.designatedReceptionId!=null)throw BadRequestException("行政指定接收请使用指定接收登记与承接入口")
        val name = vo.name?.takeIf { it.isNotBlank() } ?: throw BadRequestException("拟征集名称必填")
        CollectionCategoryPolicy.requireOptional(vo.categoryId)
        val values = AcquisitionRecordValues.fromSave(vo)
        val entity = saveDraft(vo.id, "征集申请不存在", createEntity = {
            dao.new { orgId = scope.currentOrgId() }
        }) {
            OrgScopedReadPolicy.requireVisible(scope.writableOrgIds(), orgId, "征集申请")
            val selectedPlanId = values.planId?.trim()?.takeIf { it.isNotBlank() }?.also { pid ->
                val plan = AcquisitionPlanDao.findById(pid) ?: throw BadRequestException("征集计划不存在")
                OrgScopedReadPolicy.requireVisible(scope.writableOrgIds(), plan.orgId, "征集计划")
                if(plan.orgId!=orgId)throw BadRequestException("征集计划必须属于申请所在收藏单位")
            }
            if (!vo.id.isNullOrBlank()) compliance.rememberExistingOriginals(id.value)
            planId = selectedPlanId
            this.name = name
            categoryId = values.categoryId
            era = values.era
            texture = values.texture
            proposedLevel = values.proposedLevel
            source = values.source
            values.quantity?.let { quantity = it }
            values.unit?.let { unit = it }
            acquireDate = values.acquireDate
            dimensions = values.dimensions
            weight = values.weight
            completeness = values.completeness
            completeStatus = values.completeStatus
            holder = values.holder
            proposedPrice = values.proposedPrice
            appraisedValue = values.appraisedValue
            reason = values.reason
            images = values.images
            expertOpinionAttachmentIds = values.expertOpinionAttachmentIds
            approvalAttachmentIds = values.approvalAttachmentIds
            contractAttachmentIds = values.contractAttachmentIds
            invoiceAttachmentIds = values.invoiceAttachmentIds
        }
        val persisted = System.nanoTime()
        compliance.bindOriginals(
            entity,
            listOf(
                entity.images,
                entity.expertOpinionAttachmentIds,
                entity.approvalAttachmentIds,
                entity.contractAttachmentIds,
                entity.invoiceAttachmentIds,
            ).flatMap(::splitCollectionIds).distinct().joinToString(",").ifBlank { null },
        )
        val bound = System.nanoTime()
        compliance.rememberOriginals(entity.id.value)
        val retained = System.nanoTime()
        val totalMs = (retained - started) / 1_000_000
        // 不记录征集表单、估价及文件名称；分段耗时用于定位附件保存慢请求。
        if (totalMs >= 3000) io.hizhengfu.cloud.common.const.g_logger.warn(
            "征集草稿保存耗时 acquisitionId={} totalMs={} draftMs={} bindMs={} retainMs={}",
            entity.id.value, totalMs, (persisted-started)/1_000_000, (bound-persisted)/1_000_000, (retained-bound)/1_000_000)
        else io.hizhengfu.cloud.common.const.g_logger.debug(
            "征集草稿保存完成 acquisitionId={} totalMs={} draftMs={} bindMs={} retainMs={}",
            entity.id.value, totalMs, (persisted-started)/1_000_000, (bound-persisted)/1_000_000, (retained-bound)/1_000_000)
        return entity.id.value
    }

    /** 审批通过只进入待入馆池，必须由签署后的入馆凭证完成登账及入库流水。 */
    override fun onFlowCompleted(entity: AcquisitionDao, event: FlowCompletedEvent) {
        if (entity.flowInstanceId != event.instanceId) throw BadRequestException("征集申请与审批实例不一致")
        compliance.requirePrepared(entity)
        compliance.sealPhase(entity,"APPROVED")
        super.onFlowCompleted(entity, event)
    }

    override fun beforeFlowReentry(entity: AcquisitionDao, targetNodeKey: String) {
        if (targetNodeKey != "appraise") throw BadRequestException("征集重新审批须从鉴定复核开始")
        if(entity.designatedReceptionId!=null || entity.source==EnumSource.DESIGNATED)throw BadRequestException("指定接收没有普通征集审批实例，不能通过流程事件重新激活")
        if (entity.collectionId != null) throw BadRequestException("已经办理入馆的征集单不能重新激活")
        requireSubmissionMaterials(entity)
    }

    private fun enrichCollectionInfo(items: List<AcquisitionVo>, orgIds: Set<String>, inspectHistoricalEvidence:Boolean=false): List<AcquisitionVo> {
        val ids = items.mapNotNull { it.collectionId }.toSet()
        val enriched = if (ids.isEmpty()) items else {
            val currentInfo = Collections
                .selectAll()
                .where { Collections.id inList ids }
                .andWhere { Collections.orgId inList orgIds }
                .associate {
                    (it[Collections.id].value to it[Collections.orgId]) to AcquisitionCollectionInfo(
                        totalRegNo = it[Collections.totalRegNo],
                        name = it[Collections.name],
                    )
                }
            val infoByAcquisitionId = items.mapNotNull { item ->
                val info = currentInfo[item.collectionId to item.orgId]
                item.id?.let { id -> info?.let { id to it } }
            }.toMap().toMutableMap()
            val historical = items.filter { it.collectionId != null && it.id !in infoByAcquisitionId }
            infoByAcquisitionId.putAll(historicalCollectionInfo(historical,inspectHistoricalEvidence))
            AcquisitionCollectionInfoEnricher.enrich(items, infoByAcquisitionId)
        }
        return enrichPlanInfo(enriched, orgIds)
    }

    /** 移馆后只读取与本征集单原凭证绑定、且已核验永久原件的原馆留存账。 */
    private fun historicalCollectionInfo(items: List<AcquisitionVo>, inspectEvidence:Boolean=false): Map<String, AcquisitionCollectionInfo> {
        val orgs = items.mapNotNull { it.orgId }.distinct()
        val collectionIds = items.mapNotNull { it.collectionId }.distinct()
        val acquisitionIds = items.mapNotNull { it.id }.toSet()
        if (orgs.isEmpty() || collectionIds.isEmpty() || acquisitionIds.isEmpty()) return emptyMap()
        val vouchersByAcquisition = mutableMapOf<String, MutableList<EntryVoucherDao>>()
        EntryVoucherDao.find {
            (EntryVouchers.orgId inList orgs) and (EntryVouchers.voucherStatus eq EnumVoucherStatus.REGISTERED)
        }.forEach { voucher ->
            splitCollectionIds(voucher.acquisitionIds).filter { it in acquisitionIds }.forEach { id ->
                vouchersByAcquisition.getOrPut(id) { mutableListOf() }.add(voucher)
            }
        }
        val retainedByIdentity = CollectionRetainedLedgerDao.find {
            (CollectionRetainedLedgers.orgId inList orgs) and (CollectionRetainedLedgers.collectionId inList collectionIds)
        }.groupBy { Triple(it.orgId, it.collectionId, it.snapshot.collection.entryVoucherNo) }
        return items.mapNotNull { item ->
            val id = item.id ?: return@mapNotNull null
            val orgId = item.orgId ?: return@mapNotNull null
            val collectionId = item.collectionId ?: return@mapNotNull null
            val voucher = vouchersByAcquisition[id]?.filter { it.orgId == orgId }?.singleOrNull()
                ?: return@mapNotNull null
            val retained = retainedByIdentity[Triple(orgId, collectionId, voucher.voucherNo)]?.singleOrNull()
                ?: return@mapNotNull null
            val inspection = if(inspectEvidence) retainedLedgers.inspectBoundSnapshot(retained.id.value,orgId)
                else CollectionRetainedLedgerService.BoundSnapshotInspection(snapshot=retainedLedgers.verifiedBoundSnapshot(retained.id.value,orgId))
            inspection.issue?.let { issue ->
                item.collectionEvidenceIssue=issue
                return@mapNotNull null
            }
            val collection = inspection.snapshot!!.collection
            if (collection.id != collectionId || collection.orgId != orgId)
                throw BadRequestException("征集关联的原馆留存总账身份不一致")
            id to AcquisitionCollectionInfo(collection.totalRegNo, collection.name)
        }.toMap()
    }

    private fun enrichPlanInfo(items: List<AcquisitionVo>, orgIds: Set<String>): List<AcquisitionVo> {
        val pids = items.mapNotNull { it.planId }.toSet()
        if (pids.isEmpty()) return items
        val titles = AcquisitionPlans.selectAll().where { AcquisitionPlans.id inList pids }
            .andWhere { AcquisitionPlans.orgId inList orgIds }
            .associate { it[AcquisitionPlans.id].value to it[AcquisitionPlans.title] }
        items.forEach { it.planTitle = it.planId?.let { p -> titles[p] } }
        return items
    }

    private fun collectionsMatchingKeyword(keyword: String, orgIds: Set<String>): List<Pair<String, String>> {
        val q = Collections.selectAll()
        q.andWhere { Collections.orgId inList orgIds }
        q.andWhere {
            (Collections.totalRegNo like "%$keyword%") or
                (Collections.name like "%$keyword%")
        }
        return q.map { it[Collections.id].value to it[Collections.orgId] }
    }

    private fun historicalAcquisitionIdsMatchingKeyword(keyword: String, orgIds: Set<String>): List<String> {
        val retainedCollectionIds = CollectionRetainedLedgerDao.find {
            (CollectionRetainedLedgers.orgId inList orgIds) and
                ((CollectionRetainedLedgers.totalRegNo like "%$keyword%") or (CollectionRetainedLedgers.name like "%$keyword%"))
        }.map { it.collectionId }.distinct()
        if (retainedCollectionIds.isEmpty()) return emptyList()
        val candidates = AcquisitionDao.find {
            (Acquisitions.orgId inList orgIds) and (Acquisitions.collectionId inList retainedCollectionIds)
        }.map { it.toModel() }
        val verified = historicalCollectionInfo(candidates,inspectEvidence=true)
        return candidates.mapNotNull { item ->
            val info = verified[item.id]
            // 原馆检索索引只能定位待核查条目；不把索引中的历史名称当作已核实的正文返回。
            item.id?.takeIf { item.collectionEvidenceIssue!=null || info?.totalRegNo?.contains(keyword) == true || info?.name?.contains(keyword) == true }
        }
    }
}
