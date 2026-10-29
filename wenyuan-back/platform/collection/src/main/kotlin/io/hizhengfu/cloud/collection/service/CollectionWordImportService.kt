package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.CollectionCategoryDao
import io.hizhengfu.cloud.collection.dao.CollectionDao
import io.hizhengfu.cloud.collection.dao.CollectionTrackingEventDao
import io.hizhengfu.cloud.collection.dao.CollectionTrackingEvents
import io.hizhengfu.cloud.collection.dao.CollectionWordImportDao
import io.hizhengfu.cloud.collection.dao.CollectionWordImports
import io.hizhengfu.cloud.collection.dao.Collections
import io.hizhengfu.cloud.collection.dao.WarehouseDao
import io.hizhengfu.cloud.collection.domain.CollectionImportErrorVo
import io.hizhengfu.cloud.collection.domain.CollectionTrackingHistoryVo
import io.hizhengfu.cloud.collection.domain.CollectionWordImportCommitResultVo
import io.hizhengfu.cloud.collection.domain.CollectionWordImportPreviewVo
import io.hizhengfu.cloud.collection.domain.EnumCatalogStatus
import io.hizhengfu.cloud.collection.domain.EnumCollectionImportMode
import io.hizhengfu.cloud.collection.domain.EnumCollectionWordDocumentType
import io.hizhengfu.cloud.collection.domain.EnumLocationScene
import io.hizhengfu.cloud.common.domain.EnumContentStatus
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SecurityUtils
import io.hizhengfu.cloud.sys.file.service.FileSystemStorageService
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.springframework.cache.annotation.CacheEvict
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile

/** 两种馆藏 Word 的预校验、正式入账、原件留存和管理记录查询。 */
@Component
@Transactional
class CollectionWordImportService(
    private val parser: CollectionWordDocumentParser,
    private val scope: CollectionScopeResolver,
    private val collectionImportService: CollectionImportService,
    private val catalogService: CollectionCatalogService,
    private val collectionFileService: CollectionFileService,
    private val storageMovementService: StorageMovementService,
    private val fileSystemStorageService: FileSystemStorageService,
) {
    fun validate(
        file: MultipartFile,
        mode: EnumCollectionImportMode = EnumCollectionImportMode.CREATE_ONLY,
        reason: String? = null,
        expectedCollectionId: String? = null,
        expectedDocumentType: EnumCollectionWordDocumentType? = null,
    ): CollectionWordImportPreviewVo {
        val orgId = currentOperationOrgId()
        val fileName = file.originalFilename?.substringAfterLast('/')?.substringAfterLast('\\') ?: "upload.doc"
        var preview = parser.parse(file.bytes, fileName)
        if (!preview.valid) return preview
        if (expectedDocumentType != null && preview.documentType != expectedDocumentType) {
            return preview.invalid(
                "documentType",
                if (expectedDocumentType == EnumCollectionWordDocumentType.REGISTRATION) {
                    "当前入口只允许导入“文物藏品登记表”"
                } else "当前入口只允许导入“文物藏品动态跟踪记录汇总表”",
            )
        }
        requireDocumentAccess(preview.documentType, orgId)
        val duplicate = CollectionWordImportDao.find {
            (CollectionWordImports.orgId eq orgId) and (CollectionWordImports.sourceHash eq preview.sourceHash.orEmpty())
        }.firstOrNull()
        if (duplicate != null) {
            return preview.invalid("sourceHash", "该 Word 已于此前导入，不能重复提交（导入记录：${duplicate.id.value}）")
        }

        preview = when (preview.documentType) {
            EnumCollectionWordDocumentType.REGISTRATION -> validateRegistration(preview, orgId, mode, reason)
            EnumCollectionWordDocumentType.DYNAMIC_TRACKING -> validateTracking(preview, orgId, expectedCollectionId)
            null -> preview.invalid("structure", "无法确定 Word 文档类型")
        }
        if (!preview.valid) return preview
        val ledgerBasis = preview.registrationValidationHash
        val target = preview.collectionId?.let { CollectionDao.findById(it)?.toModel() }
        return preview.copy(confirmation = CollectionImportConfirmation.create(orgId, listOf(
            "collection-word-import/v1", orgId, mode, reason?.trim().orEmpty(), expectedCollectionId, expectedDocumentType,
            java.security.MessageDigest.getInstance("SHA-256").digest(file.bytes).toList(),
            preview.copy(confirmation = null), ledgerBasis, target,
        )))
    }

    @CacheEvict(cacheNames = [CollectionConst.CACHE, CollectionConst.CACHE_CATALOG], allEntries = true)
    fun commit(
        file: MultipartFile,
        mode: EnumCollectionImportMode = EnumCollectionImportMode.CREATE_ONLY,
        reason: String? = null,
        expectedCollectionId: String? = null,
        expectedDocumentType: EnumCollectionWordDocumentType? = null,
        expectedOrgId: String? = null,
        expectedValidationHash: String? = null,
    ): CollectionWordImportCommitResultVo {
        val orgId = currentOperationOrgId()
        if (expectedOrgId != null && expectedOrgId != orgId) throw BadRequestException("Word 导入收藏单位已变化，请重新核对")
        var preview = validate(file, mode, reason, expectedCollectionId, expectedDocumentType)
        if (preview.valid && preview.documentType == EnumCollectionWordDocumentType.DYNAMIC_TRACKING) {
            val id = preview.collectionId ?: throw BadRequestException("动态记录未匹配到藏品")
            val target = CollectionDao.find { Collections.id eq id }.forUpdate().singleOrNull() ?: throw BadRequestException("藏品不存在")
            target.refresh(flush = true)
            preview = validate(file, mode, reason, expectedCollectionId, expectedDocumentType)
        }
        if (!preview.valid) {
            return CollectionWordImportCommitResultVo(
                valid = false,
                documentType = preview.documentType,
                warnings = preview.warnings,
                errors = preview.errors,
            )
        }
        CollectionImportConfirmation.requireSame(preview.confirmation, expectedValidationHash)
        requireDocumentAccess(preview.documentType, orgId)
        val documentType = preview.documentType ?: throw BadRequestException("无法确定 Word 文档类型")
        val warnings = preview.warnings.toMutableList()
        var createdCollection = false
        var updatedCollection = false

        val collectionId = when (documentType) {
            EnumCollectionWordDocumentType.REGISTRATION -> {
                val registration = preview.registration ?: throw BadRequestException("未解析到登记表数据")
                val result = collectionImportService.commit(listOf(registration), mode, reason, expectedOrgId = orgId,
                    expectedValidationHash = preview.registrationValidationHash)
                if (!result.valid) {
                    return CollectionWordImportCommitResultVo(
                        valid = false,
                        documentType = documentType,
                        warnings = warnings,
                        errors = result.errors,
                    )
                }
                createdCollection = result.createdIds.isNotEmpty()
                updatedCollection = result.updatedIds.isNotEmpty()
                val id = (result.createdIds + result.updatedIds + result.unchangedIds).singleOrNull()
                    ?: throw BadRequestException("登记表入账后未能确定唯一藏品")
                if (createdCollection) {
                    preview.catalog?.let { catalogService.saveFromImport(id, it, orgId) }
                } else if (preview.catalog != null) {
                    warnings += "已有藏品的独立编目工作副本未被 Word 直接覆盖；核心台账字段已按所选模式处理"
                }
                id
            }
            EnumCollectionWordDocumentType.DYNAMIC_TRACKING -> {
                preview.collectionId ?: throw BadRequestException("动态跟踪记录汇总表未匹配到藏品")
            }
        }

        // 提交前已检查过，数据库唯一索引继续防止并发重复提交。
        val import = CollectionWordImportDao.new {
            this.orgId = orgId
            this.collectionId = collectionId
            this.documentType = documentType
            sourceFileName = preview.sourceFileName ?: file.originalFilename ?: "upload.doc"
            sourceHash = preview.sourceHash ?: throw BadRequestException("Word 摘要缺失")
            fingerprint = preview.fingerprint ?: throw BadRequestException("Word 结构指纹缺失")
            startYear = preview.startYear
            endYear = preview.endYear
        }

        preview.events.forEach { item ->
            CollectionTrackingEventDao.new {
                this.orgId = orgId
                this.collectionId = collectionId
                importId = import.id.value
                eventType = item.eventType
                title = item.title
                content = item.content
                occurredAt = item.occurredAt
                endedAt = item.endedAt
                sort = item.sort
            }
        }
        preview.movements.forEachIndexed { index, item ->
            storageMovementService.createHistoricalImport(collectionId, import.id.value, index + 1, item,
                preview.totalRegNo, preview.collectionName)
        }

        val uploaded = fileSystemStorageService.upload(
            file = file,
            contentId = collectionId,
            module = CollectionConst.CONFIG_MODULE,
        ) ?: throw BadRequestException("Word 原件上传失败")
        val fileId = uploaded.id ?: throw BadRequestException("Word 原件未生成文件 ID")
        import.sourceFileId = fileId
        collectionFileService.saveWordOriginal(import.id.value, orgId)
        return CollectionWordImportCommitResultVo(
            valid = true,
            importId = import.id.value,
            collectionId = collectionId,
            documentType = documentType,
            createdCollection = createdCollection,
            updatedCollection = updatedCollection,
            eventCount = preview.events.size,
            movementCount = preview.movements.size,
            warnings = warnings.distinct(),
        )
    }

    fun history(collectionId: String): CollectionTrackingHistoryVo {
        val orgIds = CollectionSearchReadAccess.require(scope, additionalPermissions = setOf(CollectionConst.STORAGE), allowHistoricalOrgs = true).orgIds
        val collection = CollectionDao.findById(collectionId) ?: throw BadRequestException("藏品不存在")
        OrgScopedReadPolicy.requireVisible(orgIds, collection.orgId, "藏品管理记录")
        val imports = CollectionWordImportDao.find {
            (CollectionWordImports.collectionId eq collectionId) and (CollectionWordImports.orgId inList orgIds)
        }.orderBy(CollectionWordImports.createTime to SortOrder.DESC).toList()
        val byId = imports.associateBy { it.id.value }
        val events = CollectionTrackingEventDao.find {
            (CollectionTrackingEvents.collectionId eq collectionId) and (CollectionTrackingEvents.orgId inList orgIds)
        }.orderBy(CollectionTrackingEvents.occurredAt to SortOrder.DESC, CollectionTrackingEvents.createTime to SortOrder.DESC).map {
            val source = byId[it.importId]
            if (source == null || source.orgId != it.orgId) throw BadRequestException("管理记录与原始导入关联不一致，请先核实")
            it.toModel()
        }
        return CollectionTrackingHistoryVo(imports.map { it.toModel() }, events)
    }

    private fun validateRegistration(
        preview: CollectionWordImportPreviewVo,
        orgId: String,
        mode: EnumCollectionImportMode,
        reason: String?,
    ): CollectionWordImportPreviewVo {
        val parsed = preview.registration ?: return preview.invalid("registration", "未解析到登记表数据")
        val categoryName = parsed.categoryId?.trim().orEmpty()
        val allMatches = CollectionCategoryDao.all().filter { CollectionCategoryPolicy.isStandard(it) && it.name == categoryName }
        val category = when (allMatches.size) {
            1 -> allMatches.single()
            0 -> return preview.invalid("categoryId", "登录规范35类目录中不存在文物分类：$categoryName")
            else -> return preview.invalid("categoryId", "规范分类目录存在重复记录，请先核对初始化数据：$categoryName")
        }
        val resolved = parsed.copy(categoryId = category.id.value)
        val ledger = collectionImportService.validate(listOf(resolved), mode, reason, expectedOrgId = orgId)
        if (!ledger.valid) {
            return preview.copy(valid = false, registration = resolved, errors = preview.errors + ledger.errors)
        }
        val catalog = preview.catalog?.copy(categoryId = category.id.value)
        val warnings = preview.warnings.toMutableList()
        if (ledger.updateCount > 0) warnings += "检测到已有总登记号，将按更正模式更新核心台账并保留原 Word"
        if (ledger.createCount > 0 && expectedNameCollision(orgId, preview.collectionName.orEmpty())) {
            warnings += "当前馆已有同名藏品；本次仍以总登记号作为唯一匹配依据"
        }
        return preview.copy(valid = true, registration = resolved, catalog = catalog, warnings = warnings.distinct(), registrationValidationHash = ledger.confirmation?.validationHash)
    }

    private fun validateTracking(
        preview: CollectionWordImportPreviewVo,
        orgId: String,
        expectedCollectionId: String?,
    ): CollectionWordImportPreviewVo {
        val regNo = preview.totalRegNo.orEmpty()
        val matches = CollectionDao.find {
            (Collections.orgId eq orgId) and (Collections.totalRegNo eq regNo)
        }.toList()
        if (matches.isEmpty()) return preview.invalid("totalRegNo", "当前收藏单位不存在总登记号：$regNo")
        if (matches.size > 1) return preview.invalid("totalRegNo", "当前收藏单位的总登记号不唯一：$regNo")
        val collection = matches.single()
        if (compact(collection.name) != compact(preview.collectionName.orEmpty())) {
            return preview.invalid("collectionName", "文档藏品名称与系统台账不一致：系统为“${collection.name}”")
        }
        if (!expectedCollectionId.isNullOrBlank() && expectedCollectionId != collection.id.value) {
            return preview.invalid("collectionId", "文档总登记号匹配的藏品与当前详情页藏品不一致")
        }
        val warnings = preview.warnings.toMutableList()
        val resolvedMovements = preview.movements.map { movement ->
            val from = resolveWarehouse(orgId, movement.fromWarehouseName)
            val to = resolveWarehouse(orgId, movement.toWarehouseName)
            if (movement.fromWarehouseName != null && from == null) {
                warnings += "历史库位“${movement.fromWarehouseName}”未在当前库位树中唯一匹配，原文将保留在流水依据中"
            }
            if (movement.toWarehouseName != null && to == null) {
                warnings += "历史库位“${movement.toWarehouseName}”未在当前库位树中唯一匹配，原文将保留在流水依据中"
            }
            movement.copy(fromWarehouseId = from?.id?.value, toWarehouseId = to?.id?.value)
        }
        return preview.copy(
            valid = true,
            collectionId = collection.id.value,
            movements = resolvedMovements,
            warnings = warnings.distinct(),
        )
    }

    private fun resolveWarehouse(orgId: String, name: String?): WarehouseDao? {
        val clean = name?.trim()?.takeIf(String::isNotBlank) ?: return null
        return WarehouseDao.all().filter {
            it.orgId == orgId && it.sceneType == EnumLocationScene.STORAGE && it.name.trim() == clean
        }.singleOrNull()
    }

    private fun expectedNameCollision(orgId: String, name: String): Boolean = CollectionDao.find {
        (Collections.orgId eq orgId) and (Collections.name eq name)
    }.any()

    private fun currentOperationOrgId(): String {
        val id = SecurityUtils.getNullUserDetail()?.id ?: throw BadRequestException("请登录后导入 Word")
        val actor = CollectionActorDirectory(scope).requireActive(id)
        val orgId = scope.currentOrgId(actor)
        CollectionCurrentOperationAccess.require(scope, orgId, true,
            setOf(CollectionConst.LEDGER_CREATE, CollectionConst.LEDGER_CORRECT, CollectionConst.STORAGE), "Word 导入")
        return orgId
    }

    private fun requireDocumentAccess(type: EnumCollectionWordDocumentType?, expectedOrgId: String) {
        if (currentOperationOrgId() != expectedOrgId) throw BadRequestException("Word 导入收藏单位已变化，请重新核对")
        when (type) {
            EnumCollectionWordDocumentType.REGISTRATION -> if (collectionImportService.currentImportOrgId() != expectedOrgId)
                throw BadRequestException("登记表导入收藏单位已变化，请重新核对")
            EnumCollectionWordDocumentType.DYNAMIC_TRACKING -> {
                val actor = CollectionCurrentOperationAccess.require(scope, expectedOrgId, true,
                    setOf(CollectionConst.STORAGE), "动态跟踪记录导入")
                io.hizhengfu.cloud.collection.domain.CollectionDutySeparationPolicy.requireStorageOperation(actor.permissions, actor.roleCodes)
            }
            null -> throw BadRequestException("无法确定 Word 文档类型")
        }
    }

    private fun CollectionWordImportPreviewVo.invalid(field: String, message: String) = copy(
        valid = false,
        errors = errors + CollectionImportErrorVo(0, field, message),
    )

    private fun compact(value: String): String = value.replace(Regex("\\s+"), "").trim()
}
