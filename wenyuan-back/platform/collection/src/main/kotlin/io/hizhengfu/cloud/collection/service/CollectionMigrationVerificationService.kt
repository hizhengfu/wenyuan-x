package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.IdGenerate
import io.hizhengfu.cloud.common.utils.SecurityUtils
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.dao.entityCache
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.springframework.cache.annotation.CacheEvict
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

internal object CollectionMigrationHistory {
    fun source(collection: CollectionDao): CollectionMigrationSnapshotVo? = CollectionLedgerLogDao.find {
        (CollectionLedgerLogs.collectionId eq collection.id.value) and (CollectionLedgerLogs.orgId eq collection.orgId) and
            (CollectionLedgerLogs.field eq "legacyMigration")
    }.orderBy(CollectionLedgerLogs.id to SortOrder.DESC).firstOrNull()?.let {
        JsonUtils.fromJson(it.after, CollectionMigrationSnapshotVo::class.java)
            ?: throw BadRequestException("迁移原始记录不完整，请核查原登记日志")
    }

    fun requireVerifiedForCatalog(collection: CollectionDao) {
        if (source(collection)?.verification == "UNVERIFIED")
            throw BadRequestException("历史迁入的登记依据尚未核实，请先到总登记账详情的“旧系统迁移”补充材料并完成独立复核，再提交编目")
    }
}

internal object CollectionMigrationVerificationPolicy {
    fun validate(content: CollectionMigrationVerificationContent, complete: Boolean, now: LocalDateTime, classificationMissing: Boolean = false) {
        if (content.reason.length > 500 || (content.originalRegisteredBy?.length ?: 0) > 100)
            throw BadRequestException("核验说明不得超过500字，原登记人不得超过100字")
        content.originalRegisteredAt?.let {
            if (it.year < 1 || it > now) throw BadRequestException("原登记日期必须为有效历史日期，不能晚于当前时间")
        }
        val reasons = listOf(content.originalDateMissingReason, content.originalRegistrarMissingReason, content.classificationMissingReason)
        if (reasons.any { (it?.length ?: 0) > 500 }) throw BadRequestException("每项历史缺项说明不得超过500字")
        if ((content.originalRegisteredAt != null && !content.originalDateMissingReason.isNullOrBlank()) ||
            (!content.originalRegisteredBy.isNullOrBlank() && !content.originalRegistrarMissingReason.isNullOrBlank()) ||
            (!classificationMissing && !content.classificationMissingReason.isNullOrBlank()))
            throw BadRequestException("已有明确值的字段不能同时填写缺项说明，请核对原账")
        if (complete && ((content.originalRegisteredAt == null && content.originalDateMissingReason.isNullOrBlank()) ||
            (content.originalRegisteredBy.isNullOrBlank() && content.originalRegistrarMissingReason.isNullOrBlank()) ||
            (classificationMissing && content.classificationMissingReason.isNullOrBlank())))
            throw BadRequestException("原登记日期、原登记人或分类号缺失时，请逐项说明原账未载、查考情况及依据；不得推算或编造")
        if (complete && (content.registrationEvidence.isNullOrBlank() || content.reason.isBlank() || !content.matchesOriginalLedger))
            throw BadRequestException("请提供能确认原登记事实的原账依据及核验说明，并确认本件身份、性质、等级和数量与原账一致")
    }

    fun requireReviewer(submittedBy: String?, reviewer: String) {
        if (submittedBy.isNullOrBlank() || submittedBy == reviewer)
            throw BadRequestException("材料提交人与复核人不能为同一人，请由另一名有审核权限的人员办理")
    }
}

@Component
@Transactional
class CollectionMigrationVerificationService(
    private val scope: CollectionScopeResolver,
    private val attachments: BusinessAttachmentBinder,
    private val operationLocks: CollectionOperationLockService,
    private val archives: CollectionPermanentArchiveService,
) {
    fun get(collectionId: String): CollectionMigrationVerificationVo {
        val orgs = CollectionSearchReadAccess.require(scope, additionalPermissions = setOf(CollectionConst.LEDGER_CREATE, CollectionConst.CATALOG_AUDIT), allowHistoricalOrgs = true).orgIds
        val collection = CollectionDao.findById(collectionId) ?: fail("藏品不存在")
        OrgScopedReadPolicy.requireVisible(orgs, collection.orgId, "迁移登记核验")
        val source = source(collection)
        val history = history(collection)
        val record = history.firstOrNull()
        val pending = record?.status == EnumMigrationVerificationStatus.SUBMITTED
        val eligible = source.verification == "UNVERIFIED" && !collection.registered && !collection.published &&
            collection.lifeStatus != EnumCollectionStatus.DEREGISTERED
        val edit = eligible && !pending && runCatching { requireWriter(collection) }.isSuccess
        val reviewer = runCatching { requireReviewer(collection) }.getOrNull()
        val actorId = SecurityUtils.getUserId()
        return CollectionMigrationVerificationVo(collectionId, collection.totalRegNo, source, record, history,
            stateHash(collection, source, record), edit, eligible && pending && reviewer != null && record?.submittedBy != reviewer,
            eligible && pending && record?.submittedBy == actorId && runCatching { requireWriter(collection) }.isSuccess,
            when {
                !eligible -> "原登记事实已确认或记录已退出，正式信息订正请办理信息变更"
                pending && record?.submittedBy == actorId -> "已提交，等待另一名审核人员复核；需要补充时可撤回"
                pending -> "请核对本次提交的原账依据和藏品信息后复核"
                else -> null
            }, missingFields = buildList {
                if (collection.classNo.isNullOrBlank()) add("分类号")
                if (record?.content?.originalRegisteredAt == null && (record != null || CollectionMigrationPolicy.date(source.originalRegisteredAt) == null)) add("原登记日期")
                if ((if (record != null) record.content.originalRegisteredBy else source.originalRegisteredBy).isNullOrBlank()) add("原登记人")
            }, collectionSnapshot = CollectionCatalogMapper.fromCollection(collection.toModel()))
    }

    @CacheEvict(cacheNames = [CollectionConst.CACHE, CollectionConst.CACHE_CATALOG], allEntries = true)
    fun save(collectionId: String, content: CollectionMigrationVerificationContent, expectedStateHash: String, submit: Boolean): CollectionMigrationVerificationVo {
        val collection = lock(collectionId)
        val actor = requireWriter(collection)
        val source = requireUnverified(collection)
        val previous = history(collection).firstOrNull()
        requireSame(collection, source, previous, expectedStateHash)
        if (previous?.status == EnumMigrationVerificationStatus.SUBMITTED) fail("材料已提交复核，请先撤回或由复核人退回")
        val normalized = content.copy(originalRegisteredBy = content.originalRegisteredBy?.trim(),
            registrationEvidence = attachments.resolveSingle(content.registrationEvidence), reason = content.reason.trim(),
            originalDateMissingReason = content.originalDateMissingReason?.trim()?.takeIf(String::isNotBlank),
            originalRegistrarMissingReason = content.originalRegistrarMissingReason?.trim()?.takeIf(String::isNotBlank),
            classificationMissingReason = content.classificationMissingReason?.trim()?.takeIf(String::isNotBlank))
        val now = LocalDateTime.now()
        CollectionMigrationVerificationPolicy.validate(normalized, submit, now, collection.classNo.isNullOrBlank())
        normalized.registrationEvidence?.let(attachments::requireReadableFileId)
        if (submit) requireCatalogReady(collection)
        val evidenceHash = if (submit) evidenceHash(normalized.registrationEvidence!!) else null
        val id = IdGenerate.nextId()
        // 原件固定关联藏品；后续快照引用同一原件，复核人无需以自己的上传身份重新挂接。
        attachments.bindSingle(collectionId, "历史登记待核验材料", normalized.registrationEvidence)
        requireWriter(collection)
        append(collection, previous, CollectionMigrationVerificationRecord(id,
            if (submit) EnumMigrationVerificationStatus.SUBMITTED else EnumMigrationVerificationStatus.DRAFT,
            normalized, CollectionCatalogMapper.fromCollection(collection.toModel()), basisHash(collection, source),
            evidenceHash = evidenceHash,
            submittedBy = actor.takeIf { submit }, submittedAt = now.takeIf { submit }, recordedBy = actor, recordedAt = now))
        return get(collectionId)
    }

    @CacheEvict(cacheNames = [CollectionConst.CACHE, CollectionConst.CACHE_CATALOG], allEntries = true)
    fun withdraw(collectionId: String, expectedStateHash: String): CollectionMigrationVerificationVo {
        val collection = lock(collectionId)
        val actor = requireWriter(collection)
        val source = requireUnverified(collection)
        val previous = history(collection).firstOrNull()
        requireSame(collection, source, previous, expectedStateHash)
        if (previous?.status != EnumMigrationVerificationStatus.SUBMITTED || previous.submittedBy != actor)
            fail("仅本次材料提交人可以撤回待复核记录")
        append(collection, previous, previous.copy(id = IdGenerate.nextId(), status = EnumMigrationVerificationStatus.WITHDRAWN,
            recordedBy = actor, recordedAt = LocalDateTime.now()))
        return get(collectionId)
    }

    @CacheEvict(cacheNames = [CollectionConst.CACHE, CollectionConst.CACHE_CATALOG], allEntries = true)
    fun review(collectionId: String, review: CollectionMigrationVerificationReviewVo, expectedStateHash: String): CollectionMigrationVerificationVo {
        val collection = lock(collectionId)
        val actor = requireReviewer(collection)
        val source = requireUnverified(collection)
        val previous = history(collection).firstOrNull()
        requireSame(collection, source, previous, expectedStateHash)
        if (previous?.status != EnumMigrationVerificationStatus.SUBMITTED) fail("仅已提交的迁移材料可以复核")
        CollectionMigrationVerificationPolicy.requireReviewer(previous.submittedBy, actor)
        val opinion = review.opinion.trim()
        if (opinion.isBlank() || opinion.length > 500) fail("请填写复核意见，且不超过500字")
        val now = LocalDateTime.now()
        val id = IdGenerate.nextId()
        if (review.approved) {
            if (previous.basisHash != basisHash(collection, source)) fail("提交后藏品或编目资料发生变化，请退回重新核对并提交")
            requireCatalogReady(collection)
            CollectionMigrationVerificationPolicy.validate(previous.content, true, now, collection.classNo.isNullOrBlank())
            attachments.requireReadableFileId(previous.content.registrationEvidence!!)
            if (previous.evidenceHash == null || previous.evidenceHash != evidenceHash(previous.content.registrationEvidence))
                fail("原登记依据文件的内容或存储版本已变化，请退回重新核对并提交")
        }
        requireReviewer(collection)
        append(collection, previous, previous.copy(id = id,
            status = if (review.approved) EnumMigrationVerificationStatus.APPROVED else EnumMigrationVerificationStatus.REJECTED,
            reviewedBy = actor, reviewedAt = now, opinion = opinion, recordedBy = actor, recordedAt = now))
        if (review.approved) {
            val confirmed = source.copy(originalRegisteredAt = previous.content.originalRegisteredAt?.toString(),
                originalRegisteredBy = previous.content.originalRegisteredBy, registrationEvidence = previous.content.registrationEvidence,
                verification = "CONFIRMED", historyVerified = previous.content.historyVerified,
                originalDateMissingReason = previous.content.originalDateMissingReason,
                originalRegistrarMissingReason = previous.content.originalRegistrarMissingReason,
                classificationMissingReason = previous.content.classificationMissingReason)
            CollectionLedgerLogDao.new {
                orgId = collection.orgId; this.collectionId = collectionId; totalRegNo = collection.totalRegNo
                action = EnumLedgerAction.AUDIT; field = "legacyMigration"
                before = JsonUtils.toJson(source); after = JsonUtils.toJson(confirmed)
                reason = "历史登记依据独立复核通过：$opinion".take(500); refBizId = id
            }
            collection.registeredAt = previous.content.originalRegisteredAt
            collection.historicalConfirmedAt = now
            // 原登记日期待考时不能伪造 REGISTER 事件；现存馆藏照实计入，期间报表单列历史缺口。
            collection.registeredAt?.let { originalAt ->
                CollectionLedgerStatRecorder.record(collection, EnumLedgerStatEvent.REGISTER, id, originalAt,
                    if (confirmed.historyVerified) "旧系统迁移：原登记以来未发生馆藏归属、数量及单位变化，已核对原账" else "旧系统迁移：历史增减待核实")
            }
        }
        return get(collectionId)
    }

    fun batches(): List<CollectionMigrationBatchSummary> {
        val access = CollectionSearchReadAccess.require(scope, additionalPermissions = setOf(CollectionConst.LEDGER_CREATE, CollectionConst.CATALOG_AUDIT))
        val orgId = access.currentOrgId(scope)
        return CollectionLedgerLogDao.find { (CollectionLedgerLogs.orgId eq orgId) and (CollectionLedgerLogs.field eq "legacyMigration") }
            .orderBy(CollectionLedgerLogs.id to SortOrder.DESC).groupBy { it.collectionId }.mapNotNull { (_, logs) ->
                JsonUtils.fromJson(logs.first().after, CollectionMigrationSnapshotVo::class.java)
            }.groupBy { it.batchId }.map { (batch, rows) -> CollectionMigrationBatchSummary(batch, rows.first().sourceSystem, rows.first().migratedAt, rows.size) }
            .sortedByDescending { it.migratedAt }
    }

    fun batch(batchId: String): List<CollectionMigrationVerificationVo> {
        val access = CollectionSearchReadAccess.require(scope, additionalPermissions = setOf(CollectionConst.LEDGER_CREATE, CollectionConst.CATALOG_AUDIT))
        val orgId = access.currentOrgId(scope)
        val ids = CollectionLedgerLogDao.find { (CollectionLedgerLogs.orgId eq orgId) and (CollectionLedgerLogs.field eq "legacyMigration") }
            .filter { JsonUtils.fromJson(it.after, CollectionMigrationSnapshotVo::class.java)?.batchId == batchId }
            .map { it.collectionId }.distinct()
        if (ids.isEmpty()) fail("当前收藏单位没有该迁移批次")
        return ids.map(::get)
    }

    @CacheEvict(cacheNames = [CollectionConst.CACHE, CollectionConst.CACHE_CATALOG], allEntries = true)
    fun processBatch(request: CollectionMigrationBatchRequest): List<CollectionMigrationVerificationVo> {
        if (request.items.isEmpty() || request.items.size > 200 || request.items.map { it.collectionId }.distinct().size != request.items.size)
            fail("每次请选择1至200件不同的迁入记录，异常件可取消选择后单独办理")
        if (request.review != null && request.items.any { it.content != null }) fail("复核只能使用已提交快照，不能同时改写材料")
        val access = CollectionSearchReadAccess.require(scope, additionalPermissions = setOf(CollectionConst.LEDGER_CREATE, CollectionConst.CATALOG_AUDIT))
        val orgId = access.currentOrgId(scope)
        // 固定加锁顺序；复用逐件权限、本人回避、材料哈希和原件核查，事务保证不会部分成功。
        request.items.sortedBy { it.collectionId }.forEach { item ->
            val collection = lock(item.collectionId)
            if (collection.orgId != orgId || source(collection).batchId != request.batchId) fail("所选记录不属于当前收藏单位的同一迁移批次")
            try {
                if (request.review != null) review(item.collectionId, request.review, item.expectedStateHash)
                else save(item.collectionId, item.content ?: fail("请逐件提供本次核验材料"), item.expectedStateHash, true)
            } catch (e: BadRequestException) {
                fail("总登记号 ${collection.totalRegNo}：${e.message}；本批未提交任何变更")
            }
        }
        return request.items.map { get(it.collectionId) }
    }

    private fun requireCatalogReady(collection: CollectionDao) {
        val catalog = catalog(collection)
        if (catalog.catalogStatus !in setOf(EnumCatalogStatus.DRAFT, EnumCatalogStatus.REJECTED) ||
            collection.catalogStatus !in setOf(EnumCatalogStatus.DRAFT, EnumCatalogStatus.REJECTED))
            fail("编目已在审核中，请先退回编目，再办理原登记核验")
        // 核验只恢复主档登记事实，不能借机覆盖已在编目草稿中填写的不同主档内容。
        if (CollectionCatalogMapper.mergeCollection(catalog.content, collection.toModel()) != catalog.content)
            fail("编目草稿与总登记账共有字段不一致，请先核对并统一原账内容；编目专属资料会保留")
        if (collection.nature == null) fail("请先依据真实材料核实藏品性质")
        CollectionDescriptionPolicy.requireValid(CollectionCatalogMapper.fromCollection(collection.toModel()))
    }

    private fun catalog(collection: CollectionDao): CollectionCatalogDao = CollectionCatalogDao.find { CollectionCatalogs.collectionId eq collection.id.value }
        .singleOrNull()?.also { if (it.orgId != collection.orgId) fail("编目所属馆与迁移记录不一致") } ?: fail("迁入藏品的编目草稿不存在")

    private fun source(collection: CollectionDao) = CollectionMigrationHistory.source(collection) ?: fail("该藏品不是旧系统迁入记录")
    private fun requireUnverified(collection: CollectionDao): CollectionMigrationSnapshotVo {
        val source = source(collection)
        if (source.verification != "UNVERIFIED" || collection.registered || collection.published ||
            collection.catalogStatus == EnumCatalogStatus.APPROVED || collection.lifeStatus == EnumCollectionStatus.DEREGISTERED)
            fail("仅未核实、尚未正式登记且未退出的历史迁入记录可以办理；正式信息订正须走信息变更")
        if (!CollectionOrgCodeRegistry.matches(collection.orgId, source.originalOrgCode)) fail("原登记馆代码与当前馆不一致，请核查归属")
        operationLocks.requireNoOtherApproval(collection.id.value)
        return source
    }

    private fun lock(id: String): CollectionDao {
        TransactionManager.current().entityCache.flush()
        return CollectionDao.find { Collections.id eq id }.forUpdate().singleOrNull()?.also { it.refresh() } ?: fail("藏品不存在")
    }
    private fun requireWriter(collection: CollectionDao): String {
        val actor = CollectionCurrentOperationAccess.require(scope, collection.orgId, true, setOf(CollectionConst.LEDGER_CREATE), "迁移登记材料补录")
        CollectionDutySeparationPolicy.requireLedgerOperation(actor.permissions, actor.roleCodes)
        return actor.id
    }
    private fun requireReviewer(collection: CollectionDao) = CollectionCurrentOperationAccess.require(scope, collection.orgId, true,
        setOf(CollectionConst.CATALOG_AUDIT), "迁移登记复核").id

    private fun history(collection: CollectionDao) = CollectionLedgerLogDao.find {
        (CollectionLedgerLogs.collectionId eq collection.id.value) and (CollectionLedgerLogs.orgId eq collection.orgId) and
            (CollectionLedgerLogs.field eq "legacyMigrationVerification")
    }.orderBy(CollectionLedgerLogs.id to SortOrder.DESC).map {
        val record = JsonUtils.fromJson(it.after, CollectionMigrationVerificationRecord::class.java) ?: fail("迁移核验历史不完整")
        if (record.id != it.id.value) fail("迁移核验版本与登记日志不一致")
        record
    }

    private fun basisHash(collection: CollectionDao, source: CollectionMigrationSnapshotVo): String = CollectionImportConfirmation.hash(
        listOf("migration-verification/v1", collection.orgId, collection.id.value, collection.totalRegNo, collection.lifeStatus,
            collection.registeredAt, collection.historicalConfirmedAt, collection.published, collection.catalogStatus, source, CollectionCatalogMapper.fromCollection(collection.toModel()),
            catalog(collection).let { listOf(it.id.value, it.orgId, it.catalogStatus, it.version, it.submittedVersion, it.content) }))
    private fun stateHash(collection: CollectionDao, source: CollectionMigrationSnapshotVo, record: CollectionMigrationVerificationRecord?) =
        CollectionImportConfirmation.hash(listOf(basisHash(collection, source), record))
    private fun evidenceHash(fileId: String): String {
        val file = archives.prepareFiles(fileId)[fileId] ?: fail("原登记依据文件不可用")
        if (file.content == null) fail("原件内容核验服务未配置，不能确认历史登记")
        return CollectionImportConfirmation.hash(listOf(fileId, file.path, file.storage, file.keys.sorted(), file.content, file.revision))
    }
    private fun requireSame(collection: CollectionDao, source: CollectionMigrationSnapshotVo, record: CollectionMigrationVerificationRecord?, expected: String) {
        if (expected.isBlank() || expected != stateHash(collection, source, record)) fail("迁移资料或核验状态已变化，请重新加载核对后办理")
    }
    private fun append(collection: CollectionDao, previous: CollectionMigrationVerificationRecord?, record: CollectionMigrationVerificationRecord) {
        CollectionLedgerLogDao.new(record.id) {
            orgId = collection.orgId; collectionId = collection.id.value; totalRegNo = collection.totalRegNo
            action = EnumLedgerAction.AUDIT; field = "legacyMigrationVerification"; refBizId = record.id
            before = previous?.let(JsonUtils::toJson); after = JsonUtils.toJson(record)
            reason = record.opinion ?: record.content.reason
        }
    }
    private fun fail(message: String): Nothing = throw BadRequestException(message)
}
