package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.domain.CollectionDescriptionPolicy
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.CollectionAttachments
import io.hizhengfu.cloud.collection.dao.CollectionCatalogDao
import io.hizhengfu.cloud.collection.dao.CollectionCatalogVersionDao
import io.hizhengfu.cloud.collection.dao.CollectionCatalogVersions
import io.hizhengfu.cloud.collection.dao.CollectionCatalogs
import io.hizhengfu.cloud.collection.dao.CollectionCategoryDao
import io.hizhengfu.cloud.collection.dao.CollectionDao
import io.hizhengfu.cloud.collection.dao.CollectionLedgerLogDao
import io.hizhengfu.cloud.collection.dao.CollectionMedias
import io.hizhengfu.cloud.collection.dao.Collections
import io.hizhengfu.cloud.collection.dao.WarehouseDao
import io.hizhengfu.cloud.collection.domain.CollectionCatalogCardVo
import io.hizhengfu.cloud.collection.domain.CollectionCatalogContentVo
import io.hizhengfu.cloud.collection.domain.CollectionCatalogPolicy
import io.hizhengfu.cloud.collection.domain.CollectionCatalogQueryVo
import io.hizhengfu.cloud.collection.domain.CollectionCatalogReviewVo
import io.hizhengfu.cloud.collection.domain.CollectionCatalogVersionVo
import io.hizhengfu.cloud.collection.domain.CollectionCatalogVo
import io.hizhengfu.cloud.collection.domain.CollectionDutySeparationPolicy
import io.hizhengfu.cloud.collection.domain.CollectionRecordPolicy
import io.hizhengfu.cloud.collection.domain.CollectionOrgFilterPolicy
import io.hizhengfu.cloud.collection.domain.EnumCatalogStatus
import io.hizhengfu.cloud.collection.domain.EnumCollectionNature
import io.hizhengfu.cloud.collection.domain.EnumRelicLevel
import io.hizhengfu.cloud.collection.domain.EnumCollectionStatus
import io.hizhengfu.cloud.collection.domain.EnumLocationScene
import io.hizhengfu.cloud.collection.domain.EnumLedgerStatEvent
import io.hizhengfu.cloud.collection.domain.EnumLedgerAction
import io.hizhengfu.cloud.common.domain.GenericPage
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SecurityUtils
import jakarta.servlet.http.HttpServletRequest
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.dao.entityCache
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.exists
import org.springframework.cache.annotation.CacheConfig
import org.springframework.cache.annotation.CacheEvict
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 编目全流程：工作副本 -> 完整性校验 -> 提交快照 -> 审核 -> 主档物化。
 * 版本表保存每次提交内容，驳回后修改不会覆盖历史审核证据。
 */
@Component
@Transactional
@CacheConfig(cacheNames = [CollectionConst.CACHE_CATALOG, CollectionConst.CACHE])
class CollectionCatalogService(
    private val scope: CollectionScopeResolver,
    private val operationLockService: CollectionOperationLockService,
    private val gradingService: CollectionGradingService,
    private val accessionService: AccessionReceiptService,
    private val attachmentBinder: BusinessAttachmentBinder,
    private val originalFiles: CollectionBusinessOriginalFiles? = null,
) {
    fun findPage(query: CollectionCatalogQueryVo, request: HttpServletRequest): GenericPage<CollectionCatalogVo> {
        val orgIds = readOrgIds()
        ensureAccessibleCatalogs(orgIds)
        val page = GenericPage<CollectionCatalogVo>().build(request)
        val q = buildQuery(query, orgIds)
        q.orderBy(CollectionCatalogs.updateTime to SortOrder.DESC)
        page.total = q.count()
        val rows = if (page.pageSize > 0) {
            CollectionCatalogDao.wrapRows(q.limit(page.pageSize).offset(page.startRow)).toList()
        } else {
            CollectionCatalogDao.wrapRows(q).toList()
        }
        page.list = enrich(rows, approvedOnly = query.approvedOnly == true, orgIds = orgIds)
        return page
    }

    fun get(collectionId: String): CollectionCatalogVo {
        val orgIds = readOrgIds()
        return enrich(listOf(catalogForRead(scopedCollection(collectionId, orgIds = orgIds), orgIds)), orgIds = orgIds).first()
    }

    @CacheEvict(allEntries = true)
    fun save(collectionId: String, content: CollectionCatalogContentVo, expectedStateHash: String? = null): String {
        val collection = scopedCollection(collectionId, permission = CollectionConst.CATALOG_EDIT)
        CollectionCatalogConfirmation.requireSame(ensureCatalog(collection), collection, expectedStateHash)
        return saveContent(collection, content) { requireWrite(collection, CollectionConst.CATALOG_EDIT) }
    }

    @CacheEvict(allEntries = true)
    fun saveConfirmed(collectionId: String, content: CollectionCatalogContentVo, expectedStateHash: String, submit: Boolean = false): CollectionCatalogVo {
        save(collectionId, content, expectedStateHash)
        if (submit) submit(collectionId)
        return get(collectionId)
    }

    @CacheEvict(allEntries = true)
    fun submitConfirmed(collectionId: String, expectedStateHash: String): CollectionCatalogVo {
        submit(collectionId, expectedStateHash)
        return get(collectionId)
    }

    @CacheEvict(allEntries = true)
    fun reviewConfirmed(collectionId: String, review: CollectionCatalogReviewVo, expectedStateHash: String): CollectionCatalogVo {
        review(collectionId, review, expectedStateHash)
        return get(collectionId)
    }

    /** Word 已获登账授权的内部调用仍固定本次馆，不扩展人工编目编辑权限。 */
    @CacheEvict(allEntries = true)
    internal fun saveFromImport(collectionId: String, content: CollectionCatalogContentVo, expectedOrgId: String): String {
        val collection = scopedCollection(collectionId, permission = CollectionConst.LEDGER_CREATE)
        fun requireImport() {
            if (collection.orgId != expectedOrgId) throw BadRequestException("Word 编目收藏单位已变化，请重新核对")
            val actor = requireWrite(collection, CollectionConst.LEDGER_CREATE)
            CollectionDutySeparationPolicy.requireLedgerOperation(actor.permissions, actor.roleCodes)
        }
        requireImport()
        return saveContent(collection, content, ::requireImport)
    }

    private fun saveContent(collection: CollectionDao, draft: CollectionCatalogContentVo, recheck: () -> Unit): String {
        val content = if (draft.nature == EnumCollectionNature.OTHER && draft.level == null) draft.copy(level = EnumRelicLevel.UNRATED) else draft
        val collectionId = collection.id.value
        CollectionCategoryPolicy.requireOptional(content.categoryId)
        CollectionDescriptionPolicy.requireValid(content)
        operationLockService.requireUnlocked(collectionId)
        requireInitialOrMigratedCatalog(collection)
        if(CollectionMigrationCatalogPolicy.initial(collection)) CollectionMigrationCatalogPolicy.requireUnchanged(collection,content)
        val catalog = ensureCatalog(collection)
        CollectionCatalogPolicy.requireEditable(catalog.catalogStatus)
        val warehouseId = catalogWarehouse(collection.orgId, content.warehouseId)
        val coverFileId = attachmentBinder.resolveSingle(content.coverImage)
        originalFiles?.rememberExistingMain(collectionId)
        // 先核对当前记录是否已经引用该原件；新封面仍须通过上传引用校验。
        if (originalFiles?.bindExistingMainReference(collectionId, catalog.id.value, coverFileId, catalog.id.value, "编目封面影像") != true)
            attachmentBinder.bindSingle(catalog.id.value, "编目封面影像", coverFileId)
        recheck()
        catalog.content = content.copy(coverImage = coverFileId, warehouseId = warehouseId)
        catalog.catalogStatus = EnumCatalogStatus.DRAFT
        catalog.submittedVersion = null
        catalog.submittedBy = null
        catalog.submittedAt = null
        CollectionLedgerFieldChangeRecorder.catalogStatus(collection, EnumCatalogStatus.DRAFT, "初次编目保存草稿", catalog.id.value)
        originalFiles?.remember(CollectionMainOriginalFileSource.BIZ, collectionId)
        return collectionId
    }

    @CacheEvict(allEntries = true)
    fun submit(collectionId: String, expectedStateHash: String? = null): Int {
        val collection = scopedCollection(collectionId, permission = CollectionConst.CATALOG_EDIT)
        CollectionMigrationHistory.requireVerifiedForCatalog(collection)
        operationLockService.requireUnlocked(collectionId)
        requireInitialOrMigratedCatalog(collection)
        val catalog = ensureCatalog(collection)
        CollectionCatalogConfirmation.requireSame(catalog, collection, expectedStateHash)
        if(CollectionMigrationCatalogPolicy.initial(collection)) CollectionMigrationCatalogPolicy.requireUnchanged(collection,catalog.content)
        CollectionCategoryPolicy.requireSelected(catalog.content.categoryId)
        CollectionCatalogPolicy.requireSubmittable(catalog.catalogStatus, catalog.content, historical = CollectionMigrationCatalogPolicy.initial(collection))
        requireSnapshotWarehouse(collection.orgId, catalog.content.warehouseId)
        originalFiles?.rememberExistingMain(collectionId)
        val now = LocalDateTime.now()
        val userId = requireWrite(collection, CollectionConst.CATALOG_EDIT).id
        val nextVersion = catalog.version + 1
        CollectionCatalogVersionDao.new {
            orgId = catalog.orgId
            catalogId = catalog.id.value
            this.collectionId = collectionId
            version = nextVersion
            catalogStatus = EnumCatalogStatus.SUBMITTED
            content = catalog.content
            submittedBy = userId
            submittedAt = now
        }
        catalog.version = nextVersion
        catalog.submittedVersion = nextVersion
        catalog.catalogStatus = EnumCatalogStatus.SUBMITTED
        catalog.submittedBy = userId
        catalog.submittedAt = now
        catalog.auditOpinion = null
        catalog.auditBy = null
        catalog.auditTime = null
        CollectionLedgerFieldChangeRecorder.catalogStatus(collection, EnumCatalogStatus.SUBMITTED, "初次编目提交审核", catalog.id.value)
        collection.catalogAuditOpinion = null
        collection.catalogAuditBy = null
        collection.catalogAuditTime = null
        originalFiles?.remember(CollectionMainOriginalFileSource.BIZ, collectionId)
        return nextVersion
    }

    @CacheEvict(allEntries = true)
    fun review(collectionId: String, review: CollectionCatalogReviewVo, expectedStateHash: String? = null): String {
        val collection = scopedCollection(collectionId, permission = CollectionConst.CATALOG_AUDIT)
        operationLockService.requireUnlocked(collectionId)
        val catalog = ensureCatalog(collection)
        CollectionCatalogConfirmation.requireSame(catalog, collection, expectedStateHash)
        CollectionCatalogPolicy.requireReviewable(catalog.catalogStatus)
        CollectionRecordPolicy.requireCatalogAuditable(catalog.catalogStatus, collection.lifeStatus)
        val decision = review.status ?: throw BadRequestException("审核结果必填")
        val migratedInitial = CollectionMigrationCatalogPolicy.initial(collection)
        val opinion = CollectionCatalogPolicy.requireDecision(decision, review.opinion)
        if (decision == EnumCatalogStatus.APPROVED) {
            CollectionMigrationHistory.requireVerifiedForCatalog(collection)
            requireInitialOrMigratedCatalog(collection)
            val completeness = CollectionCatalogPolicy.completeness(catalog.content, historical = CollectionMigrationCatalogPolicy.initial(collection))
            if (!completeness.complete) {
                throw BadRequestException("编目信息不完整，不能审核通过：${completeness.missingFields.joinToString("、")}")
            }
        }
        val reviewer = requireWrite(collection, CollectionConst.CATALOG_AUDIT)
        if (catalog.submittedBy == reviewer.id) {
            throw BadRequestException("编目录入人与审核人不能为同一人")
        }
        val submittedVersion = catalog.submittedVersion ?: catalog.version.takeIf { it > 0 }
            ?: throw BadRequestException("编目尚未生成提交快照")
        val version = CollectionCatalogVersionDao.find {
            (CollectionCatalogVersions.catalogId eq catalog.id.value) and
                (CollectionCatalogVersions.version eq submittedVersion)
        }.singleOrNull() ?: throw BadRequestException("编目提交版本不存在")
        requireVersionOwnership(version, catalog)
        if (version.content != catalog.content || catalog.version != submittedVersion || version.submittedBy != catalog.submittedBy)
            throw BadRequestException("待审工作副本与提交快照不一致，请核对原记录")
        if (version.orgId != collection.orgId || version.catalogStatus !in setOf(EnumCatalogStatus.SUBMITTED, EnumCatalogStatus.PENDING))
            throw BadRequestException("待审版本馆别或状态已变化，请重新核对")
        if (decision == EnumCatalogStatus.APPROVED) {
            CollectionCategoryPolicy.requireSelected(version.content.categoryId)
            requireSnapshotWarehouse(collection.orgId, version.content.warehouseId)
            if(CollectionMigrationCatalogPolicy.initial(collection)) CollectionMigrationCatalogPolicy.requireUnchanged(collection,version.content)
            else accessionService.requireForRegistration(collection, version.content)
        }
        val gradingId = if (decision == EnumCatalogStatus.APPROVED && !CollectionMigrationCatalogPolicy.initial(collection)) gradingService.requireContent(collection, version.content, initial = true) else null
        originalFiles?.rememberExistingMain(collectionId)
        val now = LocalDateTime.now()
        val before = collection.catalogStatus
        requireWrite(collection, CollectionConst.CATALOG_AUDIT)
        version.catalogStatus = decision
        version.auditOpinion = opinion
        version.auditBy = reviewer.id
        version.auditTime = now
        catalog.content = version.content
        catalog.catalogStatus = decision
        catalog.submittedVersion = null
        catalog.auditOpinion = opinion
        catalog.auditBy = reviewer.id
        catalog.auditTime = now
        collection.catalogStatus = decision
        collection.catalogAuditOpinion = opinion
        collection.catalogAuditBy = reviewer.id
        collection.catalogAuditTime = now
        if (decision == EnumCatalogStatus.APPROVED) {
            if(!collection.registered) gradingService.recordUse(collection, gradingId, version.id.value, version.content.level)
            if (!migratedInitial) CollectionCatalogMapper.applyApproved(collection, version.content)
            if (collection.lifeStatus == EnumCollectionStatus.PENDING) {
                val beforeLifeStatus = collection.lifeStatus
                collection.lifeStatus = EnumCollectionStatus.IN_STORE
                CollectionLedgerLogDao.new {
                    orgId = collection.orgId
                    this.collectionId = collection.id.value
                    totalRegNo = collection.totalRegNo
                    action = EnumLedgerAction.CORRECT
                    field = "lifeStatus"
                    this.before = beforeLifeStatus.name
                    after = EnumCollectionStatus.IN_STORE.name
                    reason = "首次编目审核通过，藏品入库"
                    refBizId = version.id.value
                }
            }
            if (!collection.registered) {
                collection.registeredAt = now
                recordInitialRegistration(collection, version.id.value)
            }
        }
        CollectionLedgerLogDao.new {
            orgId = collection.orgId
            this.collectionId = collection.id.value
            totalRegNo = collection.totalRegNo
            action = EnumLedgerAction.AUDIT
            field = "catalogStatus"
            this.before = before.name
            after = decision.name
            reason = opinion
            refBizId = version.id.value
        }
        originalFiles?.remember(CollectionMainOriginalFileSource.BIZ, collectionId)
        return collectionId
    }

    private fun catalogWarehouse(orgId: String, value: String?): String? {
        val warehouse = CollectionLocationMutation.reference(orgId, value) ?: return null
        if (warehouse.sceneType != EnumLocationScene.STORAGE) throw BadRequestException("编目库位须属于本馆保管空间")
        return warehouse.id.value
    }

    /** 提交和审核只核实原内容；需要订正的编号须先退回草稿，不能静默改写版本。 */
    private fun requireSnapshotWarehouse(orgId: String, value: String?) {
        val warehouseId = catalogWarehouse(orgId, value)
        if (!value.isNullOrBlank() && value != warehouseId) throw BadRequestException("编目库位编号不一致，请在草稿中重新选择库位")
    }

    @CacheEvict(allEntries = true)
    fun startRevision(collectionId: String): String {
        scopedCollection(collectionId, permission = CollectionConst.CATALOG_EDIT)
        throw BadRequestException("正式编目修订请到信息变更发起申请，由保管负责人确认、主要负责人批准后签署归档生效")
    }

    private fun requireInitialOrMigratedCatalog(collection: CollectionDao) {
        if(!CollectionMigrationCatalogPolicy.initial(collection)) requireInitialCatalog(collection)
    }

    /** 不能通过旧修订、导入或状态复位覆盖已经登记/审核的正式版本。 */
    internal fun requireInitialCatalog(collection: CollectionDao) {
        val hasApproved = CollectionCatalogVersionDao.find {
            (CollectionCatalogVersions.collectionId eq collection.id.value) and
                (CollectionCatalogVersions.catalogStatus eq EnumCatalogStatus.APPROVED)
        }.any()
        if (collection.registered || collection.published || collection.catalogStatus == EnumCatalogStatus.APPROVED || hasApproved) {
            throw BadRequestException("已登记或审核的藏品请通过信息变更申请修改，不能直接覆盖正式记录")
        }
    }

    fun versions(collectionId: String): List<CollectionCatalogVersionVo> {
        val orgIds = readOrgIds()
        val catalog = catalogForRead(scopedCollection(collectionId, orgIds = orgIds), orgIds)
        return CollectionCatalogVersionDao.find {
            (CollectionCatalogVersions.catalogId eq catalog.id.value) and (CollectionCatalogVersions.orgId inList orgIds)
        }.sortedByDescending { it.version }.map { requireVersionOwnership(it, catalog); it.toModel() }
    }

    fun card(collectionId: String): CollectionCatalogCardVo {
        val orgIds = readOrgIds()
        val collection = scopedCollection(collectionId, orgIds = orgIds)
        val catalog = catalogForRead(collection, orgIds)
        val approved = CollectionCatalogVersionDao.find {
            (CollectionCatalogVersions.catalogId eq catalog.id.value) and
                (CollectionCatalogVersions.orgId eq collection.orgId) and
                (CollectionCatalogVersions.catalogStatus eq EnumCatalogStatus.APPROVED)
        }.maxByOrNull { it.version } ?: throw BadRequestException("编目审核通过后才能生成编目卡片")
        requireVersionOwnership(approved, catalog)
        val view = enrich(listOf(catalog), approvedOnly = true, orgIds = orgIds).first().copy(
            status = EnumCatalogStatus.APPROVED,
            submittedVersion = approved.version,
            submittedBy = approved.submittedBy,
            submittedAt = approved.submittedAt,
            auditOpinion = approved.auditOpinion,
            auditBy = approved.auditBy,
            auditTime = approved.auditTime,
            content = approved.content,
            completeness = CollectionCatalogPolicy.completeness(approved.content, historical = CollectionMigrationCatalogPolicy.confirmed(collection)),
        )
        return CollectionCatalogCardVo(view, entryVoucherNo=collection.entryVoucherNo, registrationFields=CollectionRegistrationPrintFields.fields(approved.content))
    }

    /** 藏品登记和旧接口调用，用同一入口建立/同步工作副本。 */
    fun ensureCatalogFor(collection: CollectionDao): CollectionCatalogDao = ensureCatalog(collection)

    /** 仅供法定信息变更执行使用；总账、正式编目卡和公开 feed 共用同一次提交的内容。 */
    internal fun applyStatutoryChange(
        collection: CollectionDao,
        content: CollectionCatalogContentVo,
        changeId: String,
        reason: String,
        applicant: String?,
        approver: String,
        approvedAt: LocalDateTime,
        operationName: String = "法定信息变更",
        recordLedgerChange: Boolean = true,
        recordOriginals: Boolean = true,
    ) {
        if (!collection.registered || collection.catalogStatus != EnumCatalogStatus.APPROVED) {
            throw BadRequestException("请先完成总账正式登记，再办理法定信息变更")
        }
        CollectionCategoryPolicy.requireSelected(content.categoryId)
        // 调拨调用方在过户前留存，并在双方接收档案完整后补记，避免在半完成阶段查阅原件。
        if (recordOriginals) originalFiles?.rememberExistingMain(collection.id.value)
        val gradingId = gradingService.requireContent(collection, content, initial = false)
        gradingService.recordUse(collection, gradingId, changeId, content.level)
        val catalog = ensureCatalog(collection)
        val now = LocalDateTime.now()
        val nextVersion = catalog.version + 1
        CollectionCatalogVersionDao.new {
            orgId = collection.orgId
            catalogId = catalog.id.value
            collectionId = collection.id.value
            version = nextVersion
            catalogStatus = EnumCatalogStatus.APPROVED
            this.content = content
            submittedBy = applicant
            submittedAt = now
            auditBy = approver
            auditTime = approvedAt
            auditOpinion = "$operationName $changeId：$reason（审批人及签署材料见对应申请）"
        }
        catalog.content = content
        catalog.version = nextVersion
        catalog.submittedVersion = null
        catalog.catalogStatus = EnumCatalogStatus.APPROVED
        catalog.auditOpinion = "$operationName $changeId：$reason"
        catalog.auditBy = approver
        catalog.auditTime = approvedAt
        collection.catalogStatus = EnumCatalogStatus.APPROVED
        collection.catalogAuditBy = approver
        collection.catalogAuditTime = approvedAt
        collection.catalogAuditOpinion = catalog.auditOpinion
        CollectionCatalogMapper.applyApproved(collection, content)
        if (recordLedgerChange) CollectionLedgerStatRecorder.record(collection, EnumLedgerStatEvent.CHANGE, changeId, now)
        // Public copy is a separately reviewed version; a formal change invalidates its source revision.
        // Do not manufacture a new public release timestamp here.
        if (recordOriginals) originalFiles?.remember(CollectionMainOriginalFileSource.BIZ, collection.id.value)
    }

    private fun recordInitialRegistration(collection: CollectionDao, sourceId: String) {
        accessionService.recordRegistration(collection)
        CollectionLedgerStatRecorder.record(collection, EnumLedgerStatEvent.REGISTER, sourceId, collection.registeredAt!!)
        CollectionLedgerLogDao.new {
            orgId = collection.orgId; collectionId = collection.id.value; totalRegNo = collection.totalRegNo
            action = EnumLedgerAction.REGISTER; after = collection.totalRegNo
            reason = "实际核收、定级及初始审核完成，正式登记"; refBizId = sourceId
        }
    }

    private fun ensureAccessibleCatalogs(orgIds: Set<String>) {
        val writable = initializationOrgIds(orgIds)
        if (writable.isEmpty()) return
        val q = Collections.selectAll().where { Collections.orgId inList writable }.orderBy(Collections.id)
        val existing = CollectionCatalogs.selectAll().map { it[CollectionCatalogs.collectionId] }.toSet()
        CollectionDao.wrapRows(q).filterNot { existing.contains(it.id.value) }.forEach { candidate ->
            val collection = lockCollection(candidate.id.value)
            OrgScopedReadPolicy.requireVisible(initializationOrgIds(orgIds), collection.orgId, "历史编目初始化")
            ensureCatalog(collection)
        }
    }

    private fun ensureCatalog(collection: CollectionDao): CollectionCatalogDao {
        CollectionCatalogDao.find { CollectionCatalogs.collectionId eq collection.id.value }.singleOrNull()?.let {
            requireCatalogOwnership(it, collection)
            return it
        }
        val legacyStatus = collection.catalogStatus
        val normalizedStatus = if (legacyStatus == EnumCatalogStatus.PENDING) EnumCatalogStatus.SUBMITTED else legacyStatus
        val content = CollectionCatalogMapper.fromCollection(collection.toModel())
        val catalog = CollectionCatalogDao.new {
            orgId = collection.orgId
            collectionId = collection.id.value
            catalogStatus = normalizedStatus
            version = if (normalizedStatus == EnumCatalogStatus.DRAFT) 0 else 1
            submittedVersion = if (normalizedStatus == EnumCatalogStatus.SUBMITTED) 1 else null
            this.content = content
            submittedBy = collection.createBy.takeUnless { normalizedStatus == EnumCatalogStatus.DRAFT }
            submittedAt = collection.createTime.takeUnless { normalizedStatus == EnumCatalogStatus.DRAFT }
            auditOpinion = collection.catalogAuditOpinion
            auditBy = collection.catalogAuditBy
            auditTime = collection.catalogAuditTime
        }
        if (normalizedStatus != EnumCatalogStatus.DRAFT) {
            CollectionCatalogVersionDao.new {
                orgId = collection.orgId
                catalogId = catalog.id.value
                collectionId = collection.id.value
                version = 1
                catalogStatus = normalizedStatus
                this.content = content
                submittedBy = collection.createBy
                submittedAt = collection.createTime
                auditOpinion = collection.catalogAuditOpinion
                auditBy = collection.catalogAuditBy
                auditTime = collection.catalogAuditTime
            }
        }
        if (legacyStatus == EnumCatalogStatus.PENDING) {
            CollectionLedgerFieldChangeRecorder.catalogStatus(collection, EnumCatalogStatus.SUBMITTED, "历史编目待审状态归一", catalog.id.value)
        }
        return catalog
    }

    private fun readOrgIds(): Set<String> = CollectionSearchReadAccess.require(scope,
        additionalPermissions = setOf(CollectionConst.CATALOG_EDIT, CollectionConst.CATALOG_AUDIT), allowHistoricalOrgs = true).orgIds

    /** 历史兼容初始化沿用本馆可写范围；跨馆只读不创建或改写外馆编目。 */
    private fun initializationOrgIds(orgIds: Set<String>): Set<String> {
        val user = CollectionActorDirectory(scope).requireActive(SecurityUtils.getUserId() ?: throw BadRequestException("请登录后查阅编目"))
        val writable = scope.currentWritableOrgIds(user)
        return readOrgIds().intersect(orgIds).filter { orgId ->
            val org = io.hizhengfu.cloud.collection.dao.CollectionOrgDao.findById(orgId)
            (writable == null || orgId in writable) && org?.status == io.hizhengfu.cloud.common.domain.EnumContentStatus.NORMAL &&
                org.orgStatus == io.hizhengfu.cloud.collection.domain.EnumOrgStatus.ENABLED
        }.toSet()
    }

    private fun catalogForRead(collection: CollectionDao, orgIds: Set<String>): CollectionCatalogDao {
        CollectionCatalogDao.find { CollectionCatalogs.collectionId eq collection.id.value }.singleOrNull()?.let {
            requireCatalogOwnership(it, collection)
            return it
        }
        val locked = lockCollection(collection.id.value)
        OrgScopedReadPolicy.requireVisible(initializationOrgIds(orgIds), locked.orgId, "历史编目初始化")
        return ensureCatalog(locked)
    }

    private fun requireWrite(collection: CollectionDao, permission: String) =
        CollectionCurrentOperationAccess.require(scope, collection.orgId, true, setOf(permission), "藏品编目")

    private fun scopedCollection(collectionId: String, permission: String? = null, orgIds: Set<String>? = null): CollectionDao {
        val readable = if (permission == null) orgIds ?: readOrgIds() else null
        val collection = (if (permission != null) lockCollection(collectionId)
            else CollectionDao.findById(collectionId)) ?: throw BadRequestException("藏品不存在")
        if (permission != null) requireWrite(collection, permission)
        else OrgScopedReadPolicy.requireVisible(readable, collection.orgId, "藏品编目")
        return collection
    }

    private fun lockCollection(collectionId: String): CollectionDao {
        // 同一事务的实际核收可能刚创建主档，先写出待插入实体再锁定并刷新。
        TransactionManager.current().entityCache.flush()
        val collection = CollectionDao.find { Collections.id eq collectionId }.forUpdate().singleOrNull()
            ?: throw BadRequestException("藏品不存在")
        collection.refresh()
        return collection
    }

    private fun requireCatalogOwnership(catalog: CollectionCatalogDao, collection: CollectionDao) {
        if (catalog.collectionId != collection.id.value || catalog.orgId != collection.orgId)
            throw BadRequestException("编目与藏品所属馆不一致，请核对原记录")
    }

    private fun requireVersionOwnership(version: CollectionCatalogVersionDao, catalog: CollectionCatalogDao) {
        if (version.catalogId != catalog.id.value || version.collectionId != catalog.collectionId)
            throw BadRequestException("编目版本与藏品不一致，请核对原记录")
    }

    private fun buildQuery(query: CollectionCatalogQueryVo, orgIds: Set<String>): Query {
        val q = CollectionCatalogs.selectAll()
        query.auditBy?.trim()?.takeIf(String::isNotBlank)?.let { value -> q.andWhere { CollectionCatalogs.auditBy eq value } }
        query.submittedBy?.trim()?.takeIf(String::isNotBlank)?.let { value -> q.andWhere { CollectionCatalogs.submittedBy eq value } }
        query.createBy?.trim()?.takeIf(String::isNotBlank)?.let { value -> q.andWhere { CollectionCatalogs.createBy eq value } }
        query.updateBy?.trim()?.takeIf(String::isNotBlank)?.let { value -> q.andWhere { CollectionCatalogs.updateBy eq value } }
        if (query.auditedByMe == true) q.andWhere { CollectionCatalogs.auditBy eq SecurityUtils.getUserDetail().id }
        if (query.submittedByMe == true) q.andWhere { CollectionCatalogs.submittedBy eq SecurityUtils.getUserDetail().id }
        CollectionOrgFilterPolicy.resolve(orgIds, query.orgId)
            ?.let { q.andWhere { CollectionCatalogs.orgId inList it } }
        query.status?.let { status ->
            if (status == EnumCatalogStatus.SUBMITTED) {
                q.andWhere {
                    (CollectionCatalogs.catalogStatus eq EnumCatalogStatus.SUBMITTED) or
                        (CollectionCatalogs.catalogStatus eq EnumCatalogStatus.PENDING)
                }
            } else {
                q.andWhere { CollectionCatalogs.catalogStatus eq status }
            }
        }
        val ids = matchingCollectionIds(query, orgIds)
        if (ids != null) q.andWhere { CollectionCatalogs.collectionId inList ids.ifEmpty { setOf("__none__") } }
        if (query.approvedOnly == true) {
            val catalogs = CollectionCatalogDao.find { CollectionCatalogs.orgId inList orgIds }.associateBy { it.id.value }
            val approvedIds = CollectionCatalogVersions.selectAll()
                .where { CollectionCatalogVersions.catalogStatus eq EnumCatalogStatus.APPROVED }
                .filter { row -> catalogs[row[CollectionCatalogVersions.catalogId]]?.let {
                    it.orgId == row[CollectionCatalogVersions.orgId] && it.collectionId == row[CollectionCatalogVersions.collectionId]
                } == true }
                .map { it[CollectionCatalogVersions.collectionId] }
                .toSet()
            q.andWhere { CollectionCatalogs.collectionId inList approvedIds.ifEmpty { setOf("__none__") } }
        }
        return q
    }

    private fun matchingCollectionIds(query: CollectionCatalogQueryVo, orgIds: Set<String>): Set<String>? {
        if (query.keyword.isNullOrBlank() && query.level == null && query.categoryId.isNullOrBlank()) return null
        val q = Collections.selectAll()
        CollectionOrgFilterPolicy.resolve(orgIds, query.orgId)
            ?.let { q.andWhere { Collections.orgId inList it } }
        query.keyword?.trim()?.takeIf { it.isNotBlank() }?.let { keyword ->
            q.andWhere {
                (Collections.name like "%$keyword%") or
                    (Collections.totalRegNo like "%$keyword%") or
                    (Collections.classNo like "%$keyword%") or
                    (Collections.auxNo like "%$keyword%")
            }
        }
        query.level?.let { q.andWhere { Collections.level eq it } }
        query.categoryId?.takeIf { it.isNotBlank() }?.let { q.andWhere { Collections.categoryId eq it } }
        return q.map { it[Collections.id].value }.toSet()
    }

    private fun enrich(rows: List<CollectionCatalogDao>, approvedOnly: Boolean = false, orgIds: Set<String>): List<CollectionCatalogVo> {
        if (rows.isEmpty()) return emptyList()
        val collectionIds = rows.map { it.collectionId }.toSet()
        val collections = CollectionDao.find { Collections.id inList collectionIds }.associateBy { it.id.value }
        val receipts = if (io.hizhengfu.cloud.collection.dao.AccessionReceipts.exists())
            io.hizhengfu.cloud.collection.dao.AccessionReceiptDao.find {
                io.hizhengfu.cloud.collection.dao.AccessionReceipts.collectionId inList collectionIds
            }.groupBy { it.collectionId }.mapValues { (_, values) -> values.maxBy { it.version } }
        else emptyMap()
        rows.forEach { catalog ->
            val collection = collections[catalog.collectionId] ?: throw BadRequestException("编目对应藏品不存在，请核对原记录")
            OrgScopedReadPolicy.requireVisible(orgIds, collection.orgId, "藏品编目")
            requireCatalogOwnership(catalog, collection)
        }
        val actor = CollectionActorDirectory(scope).requireActive(SecurityUtils.getUserId() ?: throw BadRequestException("请登录后查阅编目"))
        val writable = scope.currentWritableOrgIds(actor)
        val museums = io.hizhengfu.cloud.collection.dao.CollectionOrgDao.all().associateBy { it.id.value }
        fun has(permission: String) = io.hizhengfu.cloud.common.domain.EnumRole.ADMIN.value in actor.roles ||
            actor.permissions.any { it in setOf("ADMIN", CollectionConst.ADMIN, permission) }
        val categories = CollectionCategoryDao.all().associateBy { it.id.value }
        val warehouses = WarehouseDao.all().associateBy { it.id.value }
        val mediaCounts = CollectionMedias.selectAll()
            .where { (CollectionMedias.collectionId inList collectionIds) and (CollectionMedias.orgId inList orgIds) }
            .filter { collections[it[CollectionMedias.collectionId]]?.orgId == it[CollectionMedias.orgId] }
            .groupingBy { it[CollectionMedias.collectionId] }.eachCount()
        val attachmentCounts = CollectionAttachments.selectAll()
            .where { (CollectionAttachments.collectionId inList collectionIds) and (CollectionAttachments.orgId inList orgIds) }
            .filter { collections[it[CollectionAttachments.collectionId]]?.orgId == it[CollectionAttachments.orgId] }
            .groupingBy { it[CollectionAttachments.collectionId] }.eachCount()
        val approvedVersionRows = CollectionCatalogVersions.selectAll()
            .where {
                (CollectionCatalogVersions.collectionId inList collectionIds) and
                    (CollectionCatalogVersions.catalogStatus eq EnumCatalogStatus.APPROVED)
            }
            .filter { row ->
                val catalog = rows.single { it.collectionId == row[CollectionCatalogVersions.collectionId] }
                if (row[CollectionCatalogVersions.catalogId] != catalog.id.value) throw BadRequestException("编目版本与工作副本不一致，请核对原记录")
                row[CollectionCatalogVersions.orgId] == catalog.orgId
            }
            .groupBy { it[CollectionCatalogVersions.collectionId] }
            .mapValues { (_, items) -> items.maxBy { it[CollectionCatalogVersions.version] } }
        return rows.map { catalog ->
            val collection = collections.getValue(catalog.collectionId)
            val museum = museums[catalog.orgId]
            val canWrite = (writable == null || catalog.orgId in writable) &&
                museum?.status == io.hizhengfu.cloud.common.domain.EnumContentStatus.NORMAL && museum.orgStatus == io.hizhengfu.cloud.collection.domain.EnumOrgStatus.ENABLED
            val approved = approvedVersionRows[catalog.collectionId]
            val content = if (approvedOnly) approved?.get(CollectionCatalogVersions.content) ?: catalog.content else catalog.content
            val historicalMigration = CollectionMigrationCatalogPolicy.confirmed(collection)
            CollectionCatalogVo(
                id = catalog.id.value,
                orgId = catalog.orgId,
                collectionId = catalog.collectionId,
                totalRegNo = collection?.totalRegNo,
                collectionName = collection?.name,
                categoryName = content.categoryId?.let(categories::get)?.takeIf { it.orgId == null || it.orgId == catalog.orgId }?.name,
                warehouseName = content.warehouseId?.let(warehouses::get)?.takeIf { it.orgId == catalog.orgId }?.name,
                status = if (approvedOnly && approved != null) EnumCatalogStatus.APPROVED else catalog.catalogStatus,
                version = catalog.version,
                approvedVersion = approved?.get(CollectionCatalogVersions.version),
                submittedVersion = if (approvedOnly) approved?.get(CollectionCatalogVersions.version) else catalog.submittedVersion,
                submittedBy = if (approvedOnly) approved?.get(CollectionCatalogVersions.submittedBy) else catalog.submittedBy.takeUnless { catalog.catalogStatus == EnumCatalogStatus.DRAFT },
                submittedAt = if (approvedOnly) approved?.get(CollectionCatalogVersions.submittedAt) else catalog.submittedAt.takeUnless { catalog.catalogStatus == EnumCatalogStatus.DRAFT },
                auditOpinion = if (approvedOnly) approved?.get(CollectionCatalogVersions.auditOpinion) else catalog.auditOpinion,
                auditBy = if (approvedOnly) approved?.get(CollectionCatalogVersions.auditBy) else catalog.auditBy,
                auditTime = if (approvedOnly) approved?.get(CollectionCatalogVersions.auditTime) else catalog.auditTime,
                content = content,
                completeness = CollectionCatalogPolicy.completeness(content, historical = historicalMigration),
                historicalMigration = historicalMigration,
                migrationVerificationRequired = CollectionMigrationHistory.source(collection)?.verification == "UNVERIFIED",
                receiptLinked = receipts[catalog.collectionId]?.let { it.orgId == collection.orgId && it.content.accepted == true } == true,
                mediaCount = CollectionImagePresence.displayCount(collection.coverImage, mediaCounts[catalog.collectionId]?.toLong() ?: 0),
                attachmentCount = attachmentCounts[catalog.collectionId]?.toLong() ?: 0,
                createTime = catalog.createTime,
                updateTime = catalog.updateTime,
                orgName = museum?.name,
                stateHash = CollectionCatalogConfirmation.hash(catalog, collection),
                canEdit = canWrite && has(CollectionConst.CATALOG_EDIT) && (!collection.registered || CollectionMigrationCatalogPolicy.initial(collection)) && !collection.published &&
                    catalog.catalogStatus in setOf(EnumCatalogStatus.DRAFT, EnumCatalogStatus.REJECTED) && approved == null,
                canReview = canWrite && has(CollectionConst.CATALOG_AUDIT) && catalog.submittedBy != actor.id &&
                    catalog.catalogStatus in setOf(EnumCatalogStatus.SUBMITTED, EnumCatalogStatus.PENDING),
            )
        }
    }
}
