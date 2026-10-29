package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.dao.CollectionWordImportDao
import io.hizhengfu.cloud.collection.domain.EnumCollectionWordDocumentType
import io.hizhengfu.cloud.collection.domain.CollectionDutySeparationPolicy
import io.hizhengfu.cloud.common.utils.SecurityUtils
import org.jetbrains.exposed.v1.dao.entityCache
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import io.hizhengfu.cloud.collection.dao.CollectionAttachmentDao
import io.hizhengfu.cloud.collection.dao.CollectionAttachmentVo
import io.hizhengfu.cloud.collection.dao.CollectionAttachments
import io.hizhengfu.cloud.collection.dao.CollectionCatalogDao
import io.hizhengfu.cloud.collection.dao.CollectionCatalogs
import io.hizhengfu.cloud.collection.dao.CollectionDao
import io.hizhengfu.cloud.collection.dao.Collections
import io.hizhengfu.cloud.collection.dao.CollectionMediaDao
import io.hizhengfu.cloud.collection.dao.CollectionMediaFolderDao
import io.hizhengfu.cloud.collection.dao.CollectionMediaFolderVo
import io.hizhengfu.cloud.collection.dao.CollectionMediaFolders
import io.hizhengfu.cloud.collection.dao.CollectionMediaVo
import io.hizhengfu.cloud.collection.dao.CollectionMedias
import io.hizhengfu.cloud.collection.domain.CollectionMediaCaptureVo
import io.hizhengfu.cloud.collection.domain.CollectionMediaStandard
import io.hizhengfu.cloud.collection.domain.CollectionMediaStatVo
import io.hizhengfu.cloud.collection.domain.CollectionFileImportItemVo
import io.hizhengfu.cloud.collection.domain.CollectionFileImportResultVo
import io.hizhengfu.cloud.collection.domain.CollectionFileImportMatchVo
import io.hizhengfu.cloud.collection.domain.CollectionImportConfirmationVo
import io.hizhengfu.cloud.collection.domain.CollectionImportErrorVo
import io.hizhengfu.cloud.collection.domain.EnumCollectionAttachmentType
import io.hizhengfu.cloud.collection.domain.EnumCollectionFileImportKind
import io.hizhengfu.cloud.collection.domain.EnumCollectionMediaType
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.sys.file.service.FileService
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.inList
import org.springframework.cache.annotation.CacheEvict
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** 藏品影像/附件绑定。统一复用 sys_file 的上传归属校验。 */
@Component
@Transactional
class CollectionFileService(
    private val scope: CollectionScopeResolver,
    private val fileService: FileService,
    private val operationLocks: CollectionOperationLockService,
    private val originalFiles: CollectionBusinessOriginalFiles? = null,
) {

    private data class BatchFilePlan(
        val rowNo: Int,
        val collection: CollectionDao,
        val fileId: String,
        val fileName: String?,
        val item: CollectionFileImportItemVo,
        val mediaType: EnumCollectionMediaType,
        val attachmentType: EnumCollectionAttachmentType,
        val existingId: String? = null,
    )

    fun mediaStat(): CollectionMediaStatVo {
        val ids = CollectionSearchReadAccess.require(scope, additionalPermissions = setOf(CollectionConst.STAT)).orgIds
        val rows = CollectionMediaDao.find { CollectionMedias.orgId inList ids }.toList()
        val collections = rows.map { it.collectionId }.distinct().associateWith(CollectionDao::findById)
        if (rows.any { collections[it.collectionId]?.orgId != it.orgId }) {
            throw BadRequestException("影像与藏品归属不一致，请核对原记录")
        }
        return CollectionMediaStatVo(
            total = rows.size,
            collectionCount = rows.map { it.collectionId }.distinct().size,
            byType = rows.groupingBy { it.mediaType.name }.eachCount(),
            byCollection = rows.groupBy { it.collectionId }.mapValues { (_, items) -> items.groupingBy { it.mediaType.name }.eachCount() },
        )
    }

    fun listMediaFolders(collectionId: String): List<CollectionMediaFolderVo> {
        scopedCollection(collectionId)
        return CollectionMediaFolderDao.find { CollectionMediaFolders.collectionId eq collectionId }
            .orderBy(CollectionMediaFolders.sort to SortOrder.ASC, CollectionMediaFolders.name to SortOrder.ASC)
            .map { it.toModel() }
    }

    fun saveMediaFolder(collectionId: String, vo: CollectionMediaFolderVo): String {
        val coll = scopedCollection(collectionId, writable = true)
        val name = vo.name?.trim()?.takeIf { it.isNotBlank() } ?: throw BadRequestException("目录名称必填")
        if (name.length > 100) throw BadRequestException("影像目录名称不得超过100字")
        val parentId = vo.parentId?.takeIf { it.isNotBlank() }
        if (parentId != null) requireFolder(collectionId, parentId, coll.orgId)
        val duplicated = CollectionMediaFolderDao.find { CollectionMediaFolders.collectionId eq collectionId }
            .any { it.id.value != vo.id && it.parentId == parentId && it.name == name }
        if (duplicated) throw BadRequestException("同级影像目录名称不能重复")
        val existing = vo.id?.let { CollectionMediaFolderDao.findById(it) ?: throw BadRequestException("待修改的影像目录不存在") }
        if (existing != null && (existing.collectionId != collectionId || existing.orgId != coll.orgId)) throw BadRequestException("目录不属于当前藏品")
        // 写操作已锁定本件主档，祖先核验与保存串行；不依赖可能已经过期的树路径。
        val visited = mutableSetOf<String>()
        var ancestor = parentId
        while (ancestor != null) {
            if (ancestor == vo.id || !visited.add(ancestor)) throw BadRequestException("上级目录会形成循环，请重新选择")
            ancestor = requireFolder(collectionId, ancestor, coll.orgId).parentId
        }
        val entity = existing ?: CollectionMediaFolderDao.new {
            orgId = coll.orgId
            this.collectionId = collectionId
        }
        if (entity.collectionId != collectionId || entity.orgId != coll.orgId) throw BadRequestException("目录不属于当前藏品")
        if (parentId == entity.id.value) throw BadRequestException("目录不能以自身作为上级")
        entity.apply {
            this.name = name
            this.parentId = parentId
            sort = vo.sort ?: 0
        }
        return entity.id.value
    }

    fun deleteMediaFolder(collectionId: String, folderId: String): String {
        val coll = scopedCollection(collectionId, writable = true)
        val folder = requireFolder(collectionId, folderId, coll.orgId)
        if (CollectionMediaFolderDao.find { CollectionMediaFolders.parentId eq folderId }.any()) {
            throw BadRequestException("目录下存在子目录，不能删除")
        }
        if (CollectionMediaDao.find { CollectionMedias.folderId eq folderId }.any()) {
            throw BadRequestException("目录下存在影像，不能删除")
        }
        folder.delete()
        return folderId
    }

    fun listMedia(collectionId: String): List<CollectionMediaVo> {
        scopedCollection(collectionId)
        return CollectionMediaDao.find { CollectionMedias.collectionId eq collectionId }
            .orderBy(CollectionMedias.sort to SortOrder.ASC, CollectionMedias.createTime to SortOrder.ASC)
            .map { it.toModel() }
    }

    @CacheEvict(cacheNames = ["COLLECTION"], allEntries = true)
    fun saveMedia(collectionId: String, vo: CollectionMediaVo): String {
        val coll = scopedCollection(collectionId, writable = true)
        val existing = vo.id?.let { CollectionMediaDao.findById(it) ?: throw BadRequestException("待修改的影像不存在") }
        if (existing != null && (existing.collectionId != collectionId || existing.orgId != coll.orgId)) throw BadRequestException("影像不属于当前藏品")
        vo.folderId?.takeIf { it.isNotBlank() }?.let { requireFolder(collectionId, it, coll.orgId) }
        val capture=vo.capture
        CollectionMediaStandard.validate(capture, vo.mediaType ?: EnumCollectionMediaType.OTHER)
        if(capture?.view!=null && capture.sequence!=null && CollectionMediaDao.find { CollectionMedias.collectionId eq collectionId }
                .any { it.id.value!=vo.id && it.capture?.view==capture.view && it.capture?.sequence==capture.sequence })
            throw BadRequestException("同一藏品同一标准视图的图片顺序号不能重复，分件使用连续顺序号并另填分件号")
        val fileId = vo.fileId?.takeIf { it.isNotBlank() }?.let(fileService::resolveFileId)
            ?: throw BadRequestException("影像文件必填")
        if (CollectionMediaDao.find { (CollectionMedias.collectionId eq collectionId) and (CollectionMedias.fileId eq fileId) }
                .any { it.id.value != vo.id }) throw BadRequestException("该文件已挂接到本件藏品，请编辑已有影像")
        if(coll.registered && vo.isCover==true && coll.coverImage!=fileId)
            throw BadRequestException("正式主档的封面照片须通过信息变更核实；影像可先另行添加")
        originalFiles?.rememberExistingMain(collectionId)
        if (originalFiles?.bindExistingMainReference(collectionId, existing?.id?.value, fileId, collectionId, vo.title) != true) {
            CollectionFileReferenceAccess.require(scope, fileId, collectionId, existing?.fileId == fileId)
            fileService.insertFileContent(fileId, collectionId, vo.title)
        }
        requireWrite(coll)
        val entity = existing ?: CollectionMediaDao.new {
            orgId = coll.orgId
            this.collectionId = collectionId
        }
        entity.apply {
            orgId = coll.orgId
            this.fileId = fileId
            folderId = vo.folderId?.takeIf { it.isNotBlank() }
            mediaType = vo.mediaType ?: EnumCollectionMediaType.OTHER
            this.capture = capture
            title = vo.title
            isCover = vo.isCover ?: false
            bizId = vo.bizId
            sort = vo.sort ?: 0
            remark = vo.remark
        }
        if (!coll.registered && (entity.isCover || coll.coverImage.isNullOrBlank())) {
            if(coll.coverImage!=fileId) {
                coll.registrationDetails=coll.registrationDetails?.copy(coverIsFrontView=null)
                CollectionCatalogDao.find { CollectionCatalogs.collectionId eq collectionId }.forEach { catalog ->
                    if(catalog.catalogStatus in setOf(io.hizhengfu.cloud.collection.domain.EnumCatalogStatus.SUBMITTED,io.hizhengfu.cloud.collection.domain.EnumCatalogStatus.PENDING))
                        throw BadRequestException("编目审核期间不能更换封面，请先退回后核实正面照片")
                    catalog.content=catalog.content.copy(coverImage=fileId,registrationDetails=catalog.content.registrationDetails?.copy(coverIsFrontView=null))
                }
            }
            coll.coverImage = fileId
            if (entity.isCover) {
                CollectionMediaDao.find {
                    (CollectionMedias.collectionId eq collectionId) and CollectionMedias.id.neq(entity.id)
                }.forEach { it.isCover = false }
            }
        }
        originalFiles?.remember(CollectionMainOriginalFileSource.BIZ, collectionId)
        return entity.id.value
    }

    @CacheEvict(cacheNames = ["COLLECTION"], allEntries = true)
    fun deleteMedia(collectionId: String, mediaId: String): String {
        val coll = scopedCollection(collectionId, writable = true)
        val media = CollectionMediaDao.findById(mediaId) ?: throw BadRequestException("影像不存在")
        if (media.collectionId != collectionId || media.orgId != coll.orgId) throw BadRequestException("影像不属于当前藏品")
        originalFiles?.rememberExistingMain(collectionId)
        val fileId = media.fileId
        if(coll.registered && coll.coverImage==fileId)throw BadRequestException("该影像是正式主档封面，请先经信息变更订正主档再移除关联")
        if (coll.coverImage == fileId) {
            val catalogs = CollectionCatalogDao.find { CollectionCatalogs.collectionId eq collectionId }.toList()
            if(catalogs.any { it.catalogStatus in setOf(io.hizhengfu.cloud.collection.domain.EnumCatalogStatus.SUBMITTED, io.hizhengfu.cloud.collection.domain.EnumCatalogStatus.PENDING) })
                throw BadRequestException("编目审核期间不能删除封面，请先退回后核实正面照片")
            val next = CollectionMediaDao.find { (CollectionMedias.collectionId eq collectionId) and CollectionMedias.id.neq(media.id) }
                .orderBy(CollectionMedias.sort to SortOrder.ASC).firstOrNull()?.fileId
            coll.registrationDetails=coll.registrationDetails?.copy(coverIsFrontView=null)
            coll.coverImage = next
            catalogs.forEach { it.content=it.content.copy(coverImage=next,registrationDetails=it.content.registrationDetails?.copy(coverIsFrontView=null)) }
        }
        media.delete()
        originalFiles?.remember(CollectionMainOriginalFileSource.BIZ, collectionId)
        return mediaId
    }

    fun listAttachments(collectionId: String): List<CollectionAttachmentVo> {
        scopedCollection(collectionId)
        return CollectionAttachmentDao.find { CollectionAttachments.collectionId eq collectionId }
            .orderBy(CollectionAttachments.sort to SortOrder.ASC, CollectionAttachments.createTime to SortOrder.ASC)
            .map { it.toModel() }
    }

    @CacheEvict(cacheNames = ["COLLECTION"], allEntries = true)
    fun saveAttachment(collectionId: String, vo: CollectionAttachmentVo): String {
        val coll = scopedCollection(collectionId, writable = true)
        return saveAttachmentContent(coll, vo) { requireWrite(coll) }
    }

    /** Only the original file attached to this validated Word import may use its document-specific duty. */
    @CacheEvict(cacheNames = ["COLLECTION"], allEntries = true)
    internal fun saveWordOriginal(importId: String, expectedOrgId: String): String {
        val source = CollectionWordImportDao.findById(importId) ?: throw BadRequestException("Word 原件导入记录不存在")
        val collectionId = source.collectionId
        val coll = lockedCollection(collectionId)
        val fileId = source.sourceFileId?.takeIf(String::isNotBlank) ?: throw BadRequestException("Word 原件文件缺失")
        val documentType = source.documentType
        fun recheck() {
            if (source.orgId != expectedOrgId || coll.orgId != expectedOrgId || source.collectionId != collectionId ||
                source.sourceFileId != fileId || source.documentType != documentType)
                throw BadRequestException("Word 原件与本次导入对象不一致，请重新核对")
            val actor = CollectionCurrentOperationAccess.require(scope, expectedOrgId, true,
                if (documentType == EnumCollectionWordDocumentType.REGISTRATION) setOf(CollectionConst.LEDGER_CREATE, CollectionConst.LEDGER_CORRECT)
                else setOf(CollectionConst.STORAGE), "Word 原件留存")
            if (scope.currentOrgId(actor) != expectedOrgId) throw BadRequestException("Word 原件收藏单位已变化")
            if (documentType == EnumCollectionWordDocumentType.REGISTRATION) CollectionDutySeparationPolicy.requireLedgerOperation(actor.permissions, actor.roleCodes)
            else CollectionDutySeparationPolicy.requireStorageOperation(actor.permissions, actor.roleCodes)
            requireFileOwnership(coll)
        }
        recheck(); operationLocks.requireUnlocked(collectionId)
        return saveAttachmentContent(coll, CollectionAttachmentVo(fileId = fileId,
            attachmentType = if (documentType == EnumCollectionWordDocumentType.REGISTRATION) EnumCollectionAttachmentType.REGISTRATION else EnumCollectionAttachmentType.OTHER,
            name = source.sourceFileName, bizId = importId,
            remark = if (documentType == EnumCollectionWordDocumentType.REGISTRATION) "Word 一键导入原件" else "动态跟踪记录汇总表原件"), ::recheck)
    }

    private fun saveAttachmentContent(coll: CollectionDao, vo: CollectionAttachmentVo, recheck: () -> Unit): String {
        val collectionId = coll.id.value
        val existing = vo.id?.let { CollectionAttachmentDao.findById(it) ?: throw BadRequestException("待修改的附件不存在") }
        if (existing != null && (existing.collectionId != collectionId || existing.orgId != coll.orgId)) throw BadRequestException("附件不属于当前藏品")
        val fileId = vo.fileId?.takeIf { it.isNotBlank() }?.let(fileService::resolveFileId)
            ?: throw BadRequestException("附件文件必填")
        originalFiles?.rememberExistingMain(collectionId)
        if (originalFiles?.bindExistingMainReference(collectionId, existing?.id?.value, fileId, collectionId, vo.name) != true) {
            CollectionFileReferenceAccess.require(scope, fileId, collectionId, existing?.fileId == fileId)
            fileService.insertFileContent(fileId, collectionId, vo.name)
        }
        recheck()
        val entity = existing ?: CollectionAttachmentDao.new {
            orgId = coll.orgId
            this.collectionId = collectionId
        }
        entity.apply {
            orgId = coll.orgId
            this.fileId = fileId
            attachmentType = vo.attachmentType ?: EnumCollectionAttachmentType.OTHER
            name = vo.name
            bizId = vo.bizId
            sort = vo.sort ?: 0
            remark = vo.remark
        }
        originalFiles?.remember(CollectionMainOriginalFileSource.BIZ, collectionId)
        return entity.id.value
    }

    @CacheEvict(cacheNames = ["COLLECTION"], allEntries = true)
    fun deleteAttachment(collectionId: String, attachmentId: String): String {
        val coll = scopedCollection(collectionId, writable = true)
        val attachment = CollectionAttachmentDao.findById(attachmentId) ?: throw BadRequestException("附件不存在")
        if (attachment.collectionId != collectionId || attachment.orgId != coll.orgId) throw BadRequestException("附件不属于当前藏品")
        originalFiles?.rememberExistingMain(collectionId)
        attachment.delete()
        originalFiles?.remember(CollectionMainOriginalFileSource.BIZ, collectionId)
        return attachmentId
    }

    /** 预校验批量图片/附件与一普台账记录的匹配，不写数据库。 */
    fun validateBatchFiles(items: List<CollectionFileImportItemVo>): CollectionFileImportResultVo {
        val orgId = currentFileOrgId()
        val (plans, errors) = planBatchFiles(items, orgId)
        val proof = if (errors.isEmpty()) batchProof(orgId, items, plans) else null
        return CollectionFileImportResultVo(
            valid = errors.isEmpty(),
            matchedCount = plans.size,
            mediaCount = plans.count { it.item.kind == EnumCollectionFileImportKind.MEDIA },
            attachmentCount = plans.count { it.item.kind == EnumCollectionFileImportKind.ATTACHMENT },
            unchangedIds = plans.mapNotNull { it.existingId },
            errors = errors,
            confirmation = proof?.confirmation, matches = proof?.matches.orEmpty(),
        )
    }

    /** 按当前收藏单位 + 总登记号，全有或全无地批量绑定图片及附件。 */
    @CacheEvict(cacheNames = ["COLLECTION"], allEntries = true)
    fun bindBatchFiles(items: List<CollectionFileImportItemVo>, expectedOrgId: String? = null, expectedValidationHash: String? = null): CollectionFileImportResultVo {
        val orgId = currentFileOrgId(expectedOrgId)
        val (preview, previewErrors) = planBatchFiles(items, orgId)
        if (previewErrors.isNotEmpty()) return CollectionFileImportResultVo(valid = false, errors = previewErrors)
        // 与审批加锁/调拨执行共用藏品行锁，按固定顺序锁住整个批次，之后重新读取匹配结果。
        val lockedIds = preview.map { it.collection.id.value }.toSortedSet()
        lockedIds.forEach { scopedCollection(it, writable = true) }
        val (plans, errors) = planBatchFiles(items, orgId)
        if (errors.isNotEmpty()) return CollectionFileImportResultVo(valid = false, errors = errors)
        if (plans.map { it.rowNo to it.collection.id.value } != preview.map { it.rowNo to it.collection.id.value }) throw BadRequestException("批量关联期间藏品匹配已变化，请刷新后重新核对")
        val proof = batchProof(orgId, items, plans)
        CollectionImportConfirmation.requireSame(proof.confirmation, expectedValidationHash)
        val created = mutableListOf<String>()
        val unchanged = mutableListOf<String>()
        val bindings = mutableListOf<CollectionFileImportMatchVo>()
        plans.forEachIndexed { index, plan ->
            plan.existingId?.let {
                unchanged += it
                bindings += proof.matches[index].copy(bindingId = it)
                return@forEachIndexed
            }
            val title = plan.item.title?.trim()?.takeIf(String::isNotBlank) ?: plan.fileName
            val id = when (plan.item.kind) {
                EnumCollectionFileImportKind.MEDIA -> saveMedia(
                    plan.collection.id.value,
                    CollectionMediaVo(
                        fileId = plan.fileId,
                        mediaType = plan.mediaType,
                        capture = CollectionMediaStandard.parse(plan.fileName)?.let { CollectionMediaCaptureVo(view=it.view,sequence=it.sequence) },
                        title = title,
                        isCover = plan.item.isCover,
                        sort = plan.item.sort,
                        remark = plan.item.remark,
                    ),
                )
                EnumCollectionFileImportKind.ATTACHMENT -> saveAttachment(
                    plan.collection.id.value,
                    CollectionAttachmentVo(
                        fileId = plan.fileId,
                        attachmentType = plan.attachmentType,
                        name = title,
                        sort = plan.item.sort,
                        remark = plan.item.remark,
                    ),
                )
            }
            created += id
            bindings += proof.matches[index].copy(bindingId = id)
        }
        currentFileOrgId(orgId)
        proof.files.forEach { (id, expected) ->
            if (CollectionBatchFileContent.read(fileService, id) != expected) throw BadRequestException("文件内容在关联期间变化，本批关联已撤销，请重新核对")
        }
        return CollectionFileImportResultVo(
            valid = true,
            matchedCount = plans.size,
            mediaCount = plans.count { it.item.kind == EnumCollectionFileImportKind.MEDIA },
            attachmentCount = plans.count { it.item.kind == EnumCollectionFileImportKind.ATTACHMENT },
            createdIds = created,
            unchangedIds = unchanged,
            confirmation = proof.confirmation, matches = bindings,
        )
    }

    private data class BatchProof(val confirmation: CollectionImportConfirmationVo, val matches: List<CollectionFileImportMatchVo>, val files: Map<String, CollectionBatchFileContent>)

    private fun batchProof(orgId: String, items: List<CollectionFileImportItemVo>, plans: List<BatchFilePlan>): BatchProof {
        currentFileOrgId(orgId)
        plans.forEach { CollectionFileReferenceAccess.require(scope, it.fileId, it.collection.id.value, it.existingId != null) }
        val files = plans.map { it.fileId }.distinct().sorted().associateWith { CollectionBatchFileContent.read(fileService, it) }
        val matches = plans.map { p ->
            val file = files.getValue(p.fileId)
            CollectionFileImportMatchVo(p.rowNo, p.collection.id.value, p.collection.totalRegNo, p.collection.name, orgId,
                p.fileId, file.name ?: p.fileName, p.item.kind,
                p.mediaType.takeIf { p.item.kind == EnumCollectionFileImportKind.MEDIA },
                p.attachmentType.takeIf { p.item.kind == EnumCollectionFileImportKind.ATTACHMENT }, file.content.sha256, file.content.size, p.existingId)
        }
        val basis = linkedMapOf("schema" to "collection-file-import/v1", "orgId" to orgId, "orgName" to io.hizhengfu.cloud.collection.dao.CollectionOrgDao[orgId].name, "input" to items, "matches" to matches, "files" to files,
            "targets" to plans.map { p -> linkedMapOf("collectionId" to p.collection.id.value, "orgId" to p.collection.orgId,
                "totalRegNo" to p.collection.totalRegNo, "name" to p.collection.name, "cover" to p.collection.coverImage,
                "registeredAt" to p.collection.registeredAt, "historicalConfirmedAt" to p.collection.historicalConfirmedAt, "catalogStatus" to p.collection.catalogStatus, "lifeStatus" to p.collection.lifeStatus,
                "existing" to p.existingId?.let { if (p.item.kind == EnumCollectionFileImportKind.MEDIA) CollectionMediaDao[it].toModel() else CollectionAttachmentDao[it].toModel() }) })
        currentFileOrgId(orgId)
        return BatchProof(CollectionImportConfirmation.create(orgId, basis), matches, files)
    }

    private fun planBatchFiles(
        items: List<CollectionFileImportItemVo>, expectedOrgId: String? = null,
    ): Pair<List<BatchFilePlan>, List<CollectionImportErrorVo>> {
        val orgId = currentFileOrgId(expectedOrgId)
        if (items.isEmpty()) {
            return emptyList<BatchFilePlan>() to listOf(CollectionImportErrorVo(0, "items", "请选择需要关联的文件"))
        }
        val errors = mutableListOf<CollectionImportErrorVo>()
        val parsed = items.mapIndexedNotNull { index, item ->
            val rowNo = item.rowNo.takeIf { it > 0 } ?: index + 1
            val standard=CollectionMediaStandard.parse(item.fileName)
            if(standard!=null && item.kind==EnumCollectionFileImportKind.MEDIA &&
                io.hizhengfu.cloud.collection.dao.CollectionOrgDao[orgId].exchangeCode!=standard.institutionCode) {
                errors += CollectionImportErrorVo(rowNo,"fileName","规范文件名的机构代码与当前收藏单位已核实的交换代码不符")
                return@mapIndexedNotNull null
            }
            if(standard!=null && item.totalRegNo?.takeIf(String::isNotBlank)?.let {it!=standard.totalRegNo}==true) {
                errors += CollectionImportErrorVo(rowNo,"totalRegNo","手工登记号与规范文件名不一致，请核对原件")
                return@mapIndexedNotNull null
            }
            // 手工指定的完整历史原号优先，不能先规范化后把原件关联到另一件藏品。
            val regNo = item.totalRegNo?.takeIf(String::isNotBlank)
                ?: CollectionFileImportNameParser.totalRegNo(null, item.fileName)
            if (regNo == null) {
                errors += CollectionImportErrorVo(rowNo, "totalRegNo", "无法从文件名识别总登记号，请按“总登记号__类型__序号”命名或手工指定")
                return@mapIndexedNotNull null
            }
            val reference = item.fileId?.trim()?.takeIf(String::isNotBlank)
            if (reference == null) {
                errors += CollectionImportErrorVo(rowNo, "fileId", "文件引用必填")
                return@mapIndexedNotNull null
            }
            Triple(rowNo, regNo, item.copy(fileId = reference))
        }
        val regNos = parsed.flatMap { listOf(it.second, it.second.trim()) }.toSet()
        val collections = if (regNos.isEmpty()) emptyMap() else CollectionDao.find {
            (Collections.orgId eq orgId) and (Collections.totalRegNo inList regNos)
        }.toList().groupBy { it.totalRegNo }.also { grouped ->
            if (grouped.any { it.value.size != 1 }) throw BadRequestException("当前馆存在重复总登记号，请先核实原账")
        }.mapValues { it.value.single() }
        val seen = mutableSetOf<String>()
        val seenViews = mutableSetOf<String>()
        val plans = parsed.mapNotNull { (rowNo, regNo, item) ->
            val collection = collections[regNo] ?: collections[regNo.trim()]
            if (collection == null) {
                errors += CollectionImportErrorVo(rowNo, "totalRegNo", "当前收藏单位不存在总登记号「$regNo」")
                return@mapNotNull null
            }
            try {
                requireFileOwnership(collection)
            } catch (e: BadRequestException) {
                errors += CollectionImportErrorVo(rowNo, "totalRegNo", e.message ?: "原件归属异常")
                return@mapNotNull null
            }
            val fileId = try {
                fileService.resolveFileId(item.fileId!!)
            } catch (e: BadRequestException) {
                errors += CollectionImportErrorVo(rowNo, "fileId", e.message ?: "文件不存在")
                return@mapNotNull null
            }
            val duplicateKey = "${collection.id.value}|${item.kind.name}|$fileId"
            if (!seen.add(duplicateKey)) {
                errors += CollectionImportErrorVo(rowNo, "fileId", "同一文件在本批次中重复关联")
                return@mapNotNull null
            }
            val mediaType = item.mediaType ?: CollectionFileImportNameParser.mediaType(item.fileName)
            val attachmentType = item.attachmentType ?: CollectionFileImportNameParser.attachmentType(item.fileName)
            val existingId = when (item.kind) {
                EnumCollectionFileImportKind.MEDIA -> CollectionMediaDao.find {
                    (CollectionMedias.collectionId eq collection.id.value) and (CollectionMedias.fileId eq fileId)
                }.firstOrNull()?.id?.value
                EnumCollectionFileImportKind.ATTACHMENT -> CollectionAttachmentDao.find {
                    (CollectionAttachments.collectionId eq collection.id.value) and (CollectionAttachments.fileId eq fileId)
                }.firstOrNull()?.id?.value
            }
            if (item.kind == EnumCollectionFileImportKind.MEDIA && existingId == null) {
                val standard = CollectionMediaStandard.parse(item.fileName)
                if (standard != null) {
                    val capture = CollectionMediaCaptureVo(view=standard.view,sequence=standard.sequence)
                    try { CollectionMediaStandard.validate(capture, mediaType) }
                    catch(e: BadRequestException) { errors += CollectionImportErrorVo(rowNo,"mediaType",e.message ?: "标准视图不符") }
                    val key = "${collection.id.value}|${standard.view}|${standard.sequence}"
                    if(!seenViews.add(key) || CollectionMediaDao.find { CollectionMedias.collectionId eq collection.id.value }
                        .any { it.capture?.view==standard.view && it.capture?.sequence==standard.sequence })
                        errors += CollectionImportErrorVo(rowNo,"fileName","同一藏品同一标准视图的顺序号重复，请核实分件连续序号")
                }
                if(collection.registered && item.isCover==true && collection.coverImage!=fileId)
                    errors += CollectionImportErrorVo(rowNo,"isCover","正式主档封面须通过信息变更订正")
            }
            BatchFilePlan(
                rowNo = rowNo,
                collection = collection,
                fileId = fileId,
                fileName = item.fileName?.substringAfterLast('/')?.substringAfterLast('\\'),
                item = item,
                mediaType = mediaType,
                attachmentType = attachmentType,
                existingId = existingId,
            )
        }
        currentFileOrgId(orgId)
        return plans to errors
    }

    private fun currentFileOrgId(expected: String? = null): String {
        val actor = CollectionActorDirectory(scope).requireActive(SecurityUtils.getUserId() ?: throw BadRequestException("请登录后关联藏品文件"))
        val orgId = scope.currentOrgId(actor)
        if (expected != null && orgId != expected) throw BadRequestException("文件关联收藏单位已变化，请重新核对")
        CollectionCurrentOperationAccess.require(scope, orgId, true, setOf(CollectionConst.LEDGER_CREATE), "藏品文件关联")
        return orgId
    }

    private fun requireWrite(coll: CollectionDao) {
        CollectionCurrentOperationAccess.require(scope, coll.orgId, true, setOf(CollectionConst.LEDGER_CREATE), "藏品影像附件")
        requireFileOwnership(coll)
    }

    private fun lockedCollection(collectionId: String): CollectionDao {
        TransactionManager.current().entityCache.flush()
        val coll = CollectionDao.find { Collections.id eq collectionId }.forUpdate().singleOrNull() ?: throw BadRequestException("藏品不存在")
        coll.refresh()
        return coll
    }

    private fun scopedCollection(collectionId: String, writable: Boolean = false): CollectionDao {
        val readable = if (writable) null else CollectionSearchReadAccess.require(scope, allowHistoricalOrgs = true).orgIds
        val coll = if (writable) lockedCollection(collectionId) else CollectionDao.findById(collectionId) ?: throw BadRequestException("藏品不存在")
        if (writable) requireWrite(coll) else OrgScopedReadPolicy.requireVisible(readable, coll.orgId, "藏品影像附件")
        if (writable) operationLocks.requireUnlocked(collectionId)
        requireFileOwnership(coll)
        return coll
    }

    /** 错误馆别不能通过列表省略、更新重写或批量“已存在”被悄悄接受。 */
    private fun requireFileOwnership(collection: CollectionDao) {
        val id = collection.id.value
        if (CollectionMediaDao.find { CollectionMedias.collectionId eq id }.any { it.orgId != collection.orgId } ||
            CollectionAttachmentDao.find { CollectionAttachments.collectionId eq id }.any { it.orgId != collection.orgId } ||
            CollectionMediaFolderDao.find { CollectionMediaFolders.collectionId eq id }.any { it.orgId != collection.orgId }) {
            throw BadRequestException("藏品影像、附件或目录与所属馆不一致，请核对原记录后办理")
        }
    }

    private fun requireFolder(collectionId: String, folderId: String, orgId: String): CollectionMediaFolderDao {
        val folder = CollectionMediaFolderDao.findById(folderId) ?: throw BadRequestException("影像目录不存在")
        if (folder.collectionId != collectionId || folder.orgId != orgId) throw BadRequestException("影像目录不属于当前藏品")
        return folder
    }
}

/** 文件命名规则：`总登记号__类型__序号.ext`；没有类型段时文件名主干即总登记号。 */
object CollectionFileImportNameParser {
    private val mediaLabels = mapOf(
        "正面" to EnumCollectionMediaType.FRONT,
        "背面" to EnumCollectionMediaType.BACK,
        "细部" to EnumCollectionMediaType.DETAIL,
        "铭文" to EnumCollectionMediaType.INSCRIPTION,
        "拓片" to EnumCollectionMediaType.RUBBING,
        "线图" to EnumCollectionMediaType.LINE_DRAWING,
        "三维" to EnumCollectionMediaType.THREE_D,
        "视频" to EnumCollectionMediaType.VIDEO,
    )
    private val attachmentLabels = mapOf(
        "登记表" to EnumCollectionAttachmentType.REGISTRATION,
        "征集合同" to EnumCollectionAttachmentType.ACQUISITION_CONTRACT,
        "鉴定意见" to EnumCollectionAttachmentType.APPRAISAL_OPINION,
        "修复方案" to EnumCollectionAttachmentType.RESTORATION_PLAN,
        "借展协议" to EnumCollectionAttachmentType.LOAN_AGREEMENT,
        "退出馆藏批准文件" to EnumCollectionAttachmentType.DEREG_APPROVAL,
        "注销批文" to EnumCollectionAttachmentType.DEREG_APPROVAL,
        "出入库凭证" to EnumCollectionAttachmentType.MOVEMENT_RECEIPT,
        "盘点记录" to EnumCollectionAttachmentType.INVENTORY_RECORD,
    )

    fun totalRegNo(explicit: String?, fileName: String?): String? = explicit
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?: CollectionMediaStandard.parse(fileName)?.totalRegNo
        ?: parts(fileName).firstOrNull()?.takeIf(String::isNotBlank)

    fun mediaType(fileName: String?): EnumCollectionMediaType = CollectionMediaStandard.parse(fileName)?.let { CollectionMediaStandard.mediaType(it.view) } ?: token(fileName)?.let { value ->
        enumValues<EnumCollectionMediaType>().firstOrNull { it.name.equals(value, ignoreCase = true) }
            ?: mediaLabels[value]
    } ?: EnumCollectionMediaType.OTHER

    fun attachmentType(fileName: String?): EnumCollectionAttachmentType = token(fileName)?.let { value ->
        enumValues<EnumCollectionAttachmentType>().firstOrNull { it.name.equals(value, ignoreCase = true) }
            ?: attachmentLabels[value]
    } ?: EnumCollectionAttachmentType.OTHER

    private fun token(fileName: String?): String? = parts(fileName).getOrNull(1)?.trim()?.takeIf(String::isNotBlank)

    private fun parts(fileName: String?): List<String> {
        val name = fileName?.substringAfterLast('/')?.substringAfterLast('\\')?.trim().orEmpty()
        if (name.isBlank()) return emptyList()
        val stem = name.substringBeforeLast('.', name)
        return stem.split("__").map(String::trim)
    }
}
