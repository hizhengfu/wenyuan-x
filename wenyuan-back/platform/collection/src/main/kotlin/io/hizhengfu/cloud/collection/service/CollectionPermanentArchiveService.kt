package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.sys.file.dao.FileDao
import io.hizhengfu.cloud.sys.file.dao.FileContents
import io.hizhengfu.cloud.sys.file.dao.FileUsers
import io.hizhengfu.cloud.sys.file.domain.FILE_CACHE
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.sys.file.service.FileRetentionGuard
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.or
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.cache.annotation.CacheEvict
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.dao.entityCache
import java.security.MessageDigest
import io.hizhengfu.cloud.common.file.*

@Component
@Transactional
class CollectionPermanentArchiveService(private val binder: BusinessAttachmentBinder, private val scope: CollectionScopeResolver, private val localStorage: io.hizhengfu.cloud.common.file.LocalFileObjectStorage? = null, private val readAccess: org.springframework.beans.factory.ObjectProvider<io.hizhengfu.cloud.common.file.FileReadAccess>? = null, private val objectStorage: org.springframework.beans.factory.ObjectProvider<FileObjectStorage>? = null,
    private val archiveReadPolicies: org.springframework.beans.factory.ObjectProvider<CollectionArchiveReadPolicy>? = null) {
    internal data class PreparedFile(val path: String, val storage: EnumFileStorageType, val keys: Set<String>, val content: CollectionOriginalContent?, val revision: FileObjectRevision?)
    internal data class SealRequest(val orgId: String, val bizType: String, val bizId: String, val phase: String, val snapshot: Any, val fileIds: String?)

    /** 仅在责任重建写前取得，不接受客户端指定继承清单，也不授予上传归属。 */
    internal class RequisitionReplacementOriginals internal constructor(
        internal val sourceId: String, internal val orgId: String, internal val actorId: String,
        internal val proposal: String, internal val instanceId: String,
        internal val initialStatus: io.hizhengfu.cloud.common.domain.EnumFlowStatus,
        internal val previousId: String?, internal val submittedId: String,
        internal val submittedSnapshot: String, internal val submittedDigest: String,
        internal val submittedFiles: Map<String, List<String>>, internal val files: Map<String, PreparedFile>,
    ) {
        internal val basisFileIds: Set<String> get() = files.keys
        private var targetId: String? = null
        internal fun selectTarget(id: String) {
            if (targetId != null && targetId != id) throw BadRequestException("原提用依据不能用于另一个承接目标")
            targetId = id
        }
    }

    private fun replacementBasis(entity: RequisitionDao): Set<String> =
        BusinessAttachmentBindingPlanner.plan(entity.id.value, "原提用依据", entity.attachmentIds)
            .map { binder.resolveSingle(it.fileId) ?: throw BadRequestException("原提用依据不能为空") }.toSet()

    private fun replacementSubmittedFiles(archiveId: String): Map<String, List<String>> =
        CollectionPermanentArchiveFileDao.find { CollectionPermanentArchiveFiles.archiveId eq archiveId }
            .onEach { it.refresh(flush = false) }
            .associate { it.id.value to listOf(it.orgId, it.bizId, it.fileId, it.metadata) }

    /** 在取消旧流程、释放预占和建立新草稿之前，核实真实 SUBMITTED 与旧依据字节。 */
    internal fun prepareRequisitionReplacementOriginals(sourceId: String, expectedSubmittedDigest: String): RequisitionReplacementOriginals {
        org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().entityCache.flush()
        val source = RequisitionDao.findById(sourceId) ?: throw BadRequestException("原提用申请不存在")
        source.refresh(flush = false)
        val originals = RequisitionReplacementEvidence(this).source(source)
        val submitted = originals.singleOrNull { it.phase == "SUBMITTED" }
            ?: throw BadRequestException("责任承接缺少原提用已提交档案")
        if (submitted.digest != expectedSubmittedDigest) throw BadRequestException("原提用提交依据已变化，请重新核对")
        val actor = CollectionCurrentOperationAccess.require(scope, source.orgId, true, setOf(CollectionConst.STORAGE), "提用责任变更")
        CollectionResponsibleUserService(CollectionActorDirectory(scope), scope).requireRole(actor.id, source.orgId, CollectionConst.ROLE_DIRECTOR)
        val files = prepareFiles(source.attachmentIds)
        val proof = RequisitionReplacementOriginals(sourceId, source.orgId, actor.id,
            JsonUtils.toJson(requisitionProposal(source))!!, source.flowInstanceId ?: throw BadRequestException("原审批实例不存在"),
            source.flowStatus, source.replacesId, submitted.id.value, submitted.snapshot, submitted.digest,
            replacementSubmittedFiles(submitted.id.value), files)
        verifyRequisitionReplacementOriginals(proof)
        return proof
    }

    /** 当前本人、旧来源及新草稿每次绑定前后重核；合法取消状态与原 proposal 分别校验。 */
    internal fun verifyRequisitionReplacementOriginals(proof: RequisitionReplacementOriginals, replacementId: String? = null) {
        org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().entityCache.flush()
        val source = RequisitionDao.findById(proof.sourceId) ?: throw BadRequestException("原提用申请不存在")
        source.refresh(flush = false)
        val actor = CollectionCurrentOperationAccess.require(scope, proof.orgId, true, setOf(CollectionConst.STORAGE), "提用责任变更")
        if (actor.id != proof.actorId) throw BadRequestException("责任承接期间办理本人已变化")
        CollectionResponsibleUserService(CollectionActorDirectory(scope), scope).requireRole(actor.id, proof.orgId, CollectionConst.ROLE_DIRECTOR)
        if (source.orgId != proof.orgId || source.replacesId != proof.previousId || source.sourceRetirement != null ||
            source.sourceBizType == "COLLECTION_RESTORATION" || JsonUtils.toJson(requisitionProposal(source)) != proof.proposal ||
            replacementBasis(source) != proof.basisFileIds || source.flowInstanceId != proof.instanceId)
            throw BadRequestException("责任承接原提用所属馆、清单或提交依据已变化")
        if (source.outExecuted || source.outExecutedTime != null || source.handover != null || source.returned || source.returnDate != null ||
            CollectionPermanentArchiveDao.find { (CollectionPermanentArchives.bizType eq CollectionConst.BIZ_REQUISITION) and (CollectionPermanentArchives.bizId eq proof.sourceId) }
                .any { it.phase == "OUT_EXECUTED" || it.phase.startsWith("HANDOVER_") } ||
            StorageMovementDao.find { (StorageMovements.orgId eq proof.orgId) and (StorageMovements.refBizId eq proof.sourceId) and
                (StorageMovements.type eq io.hizhengfu.cloud.collection.domain.EnumMoveType.OUT) }.any())
            throw BadRequestException("原提用已有实际执行事实，不能继承为未执行草稿")
        val instance = io.hizhengfu.cloud.flow.dao.FlowInstanceDao.findById(proof.instanceId)
            ?: throw BadRequestException("原审批实例不存在")
        instance.refresh(flush = false)
        val expectedStatus = when (source.flowStatus) {
            io.hizhengfu.cloud.common.domain.EnumFlowStatus.AUDIT -> "RUNNING"
            io.hizhengfu.cloud.common.domain.EnumFlowStatus.PASS -> "COMPLETED"
            io.hizhengfu.cloud.common.domain.EnumFlowStatus.CANCEL -> "CANCELED"
            io.hizhengfu.cloud.common.domain.EnumFlowStatus.REJECT -> "REJECTED"
            else -> throw BadRequestException("原提用尚未提交，不能继承依据")
        }
        val allowedStatus = source.flowStatus == proof.initialStatus ||
            (proof.initialStatus == io.hizhengfu.cloud.common.domain.EnumFlowStatus.AUDIT && source.flowStatus == io.hizhengfu.cloud.common.domain.EnumFlowStatus.CANCEL)
        if (!allowedStatus || instance.status != expectedStatus || instance.businessType != CollectionConst.BIZ_REQUISITION ||
            instance.businessKey != proof.sourceId || instance.parentInstanceId != null || instance.parentTaskId != null)
            throw BadRequestException("责任承接期间原提用与流程来源或终止状态不一致")
        val submitted = CollectionPermanentArchiveDao.findById(proof.submittedId) ?: throw BadRequestException("原提用提交档案缺失")
        submitted.refresh(flush = false)
        if (submitted.orgId != proof.orgId || submitted.bizType != CollectionConst.BIZ_REQUISITION || submitted.bizId != proof.sourceId ||
            submitted.phase != "SUBMITTED" || submitted.snapshot != proof.submittedSnapshot || submitted.digest != proof.submittedDigest ||
            submitted.digest != CollectionCareEvidence.digest(submitted.snapshot) || replacementSubmittedFiles(proof.submittedId) != proof.submittedFiles)
            throw BadRequestException("责任承接原提交档案、原件引用或摘要已变化")
        RequisitionReplacementEvidence(this).source(source)
        verifyRecordedContent(submitted)
        proof.files.forEach { (id, saved) ->
            val ref = CollectionPermanentArchiveFileDao.find {
                (CollectionPermanentArchiveFiles.archiveId eq proof.submittedId) and (CollectionPermanentArchiveFiles.fileId eq id)
            }.singleOrNull() ?: throw BadRequestException("旧提用依据不属于原提交档案")
            val metadata = JsonUtils.mapper.readTree(ref.metadata)
            val content = CollectionArchiveFileIdentity.content(metadata)
                ?: throw BadRequestException("原提用依据缺少已冻结的内容基线")
            if (saved.content != content || saved.revision != CollectionPermanentRemoteRecoveryRegistry.effective(id, proof.orgId,
                    metadata.path("path").asString(), CollectionArchiveFileIdentity.revision(metadata), content))
                throw BadRequestException("旧提用依据内容或有效存储版本已变化")
            val file = FileDao.findById(id) ?: throw BadRequestException("原提用依据文件不存在")
            file.refresh(flush = false)
            if (file.path != saved.path || file.storageType != saved.storage || fileReadKeys(file) != saved.keys)
                throw BadRequestException("旧提用依据文件身份已变化")
            requireReadAccess().requireReadable(file.path, file.storageType.name)
            val captured = captureContent(file)
            if (captured.content != saved.content || captured.revision != saved.revision || fileReadKeys(file) != saved.keys)
                throw BadRequestException("责任承接期间旧提用依据字节或存储版本变化")
        }
        if (replacementId == null) {
            if (source.supersededById != null) throw BadRequestException("原提用已有后续申请")
            return
        }
        proof.selectTarget(replacementId)
        val child = RequisitionDao.findById(replacementId) ?: throw BadRequestException("责任承接草稿不存在")
        child.refresh(flush = false)
        if (child.id == source.id || child.orgId != proof.orgId || child.createBy != proof.actorId ||
            child.flowStatus != io.hizhengfu.cloud.common.domain.EnumFlowStatus.DRAFT || child.flowInstanceId != null ||
            child.outExecuted || child.outExecutedTime != null || child.handover != null || child.returned || child.returnDate != null || child.sourceRetirement != null ||
            splitCollectionIds(child.collectionIds).toSet() != splitCollectionIds(source.collectionIds).toSet() ||
            child.purpose != source.purpose || child.sourceBizType != source.sourceBizType || child.sourceBizId != source.sourceBizId ||
            child.basis != source.basis || child.department != source.department || replacementBasis(child) != proof.basisFileIds ||
            child.dossier?.leaderUserId != proof.actorId ||
            (source.supersededById == null) != (child.replacesId == null) ||
            (source.supersededById != null && (source.supersededById != replacementId || child.replacesId != proof.sourceId)))
            throw BadRequestException("原提用依据与本次同馆同清单的责任承接草稿不一致")
        // 同一文件即便属于旧 basis，也不能借继承能力冒作本次新安全/运输材料。
        val dossierFiles = child.dossier?.let { listOfNotNull(it.safetyPlanFileIds, it.transportCarrierCapabilityFileIds, it.transportPlanFileIds) }
            .orEmpty().flatMap { BusinessAttachmentBindingPlanner.plan(child.id.value, "本次新提用方案原件", it) }
            .mapNotNull { binder.resolveSingle(it.fileId) }.toSet()
        dossierFiles.intersect(proof.basisFileIds).forEach { fileId ->
            if (FileUsers.selectAll().where { (FileUsers.userId eq proof.actorId) and (FileUsers.fileId eq fileId) }.empty())
                throw BadRequestException("无权引用该文件，请勿使用他人的文件")
        }
    }

    /** 仅为本次新草稿封存 exact 旧 basis；不得借本次新身份档案反向自授来源资格。 */
    @CacheEvict(cacheNames = [FILE_CACHE], allEntries = true)
    internal fun sealRequisitionReplacementOriginal(proof: RequisitionReplacementOriginals, ref: CollectionOriginalFileReference,
                                                    phase: String, snapshot: Map<String, Any>): String {
        verifyRequisitionReplacementOriginals(proof, ref.businessId)
        val id = binder.resolveSingle(ref.file) ?: throw BadRequestException("承接原件不能为空")
        val saved = proof.files[id] ?: throw BadRequestException("该文件不属于本次原提用依据")
        val expectedPhase = CollectionBusinessOriginalFiles.PHASE + CollectionCareEvidence.digest(
            "${proof.orgId}|${CollectionConst.BIZ_REQUISITION}|${id}|${saved.path}|${saved.storage}|${saved.keys.sorted()}").take(48)
        if (ref.businessType != CollectionConst.BIZ_REQUISITION || ref.orgId != proof.orgId || ref.recordId != ref.businessId ||
            ref !in CollectionBusinessOriginalFileSource().references(CollectionConst.BIZ_REQUISITION, ref.businessId) ||
            phase != expectedPhase || snapshot["businessType"] != CollectionConst.BIZ_REQUISITION || snapshot["fileId"] != id ||
            snapshot["path"] != saved.path || snapshot["storageType"] != saved.storage.name || snapshot["readKeys"] != saved.keys.sorted())
            throw BadRequestException("承接依据的目标业务或冻结文件身份不一致")
        val archiveId = sealFromArchives(proof.orgId, CollectionBusinessOriginalFiles.BIZ, ref.businessId, phase, snapshot, id,
            mapOf(proof.submittedId to proof.submittedDigest))
        verifyRequisitionReplacementOriginals(proof, ref.businessId)
        return archiveId
    }

    private fun requireReadAccess(): FileReadAccess =
        readAccess?.getIfAvailable() ?: throw BadRequestException("原件读取授权服务未配置")

    /** 过户前按经办人的当前权限读取；只供同一业务事务随后生成双方档案。 */
    internal fun prepareFiles(fileIds: String?): Map<String, PreparedFile> =
        BusinessAttachmentBindingPlanner.plan("", "调拨归档原件", fileIds).associate { ref ->
            val id = binder.resolveSingle(ref.fileId) ?: throw BadRequestException("档案文件不能为空")
            val file = FileDao.findById(id) ?: throw BadRequestException("档案文件不存在")
            requireReadAccess().requireReadable(file.path, file.storageType.name)
            val keys = fileReadKeys(file)
            val capture = captureContent(file)
            if (fileReadKeys(file) != keys) throw BadRequestException("归档准备期间原件身份变化，请核查后重试")
            id to PreparedFile(file.path, file.storageType, keys, capture.content, capture.revision)
        }

    /** 先在事务内写齐相互引用的双方档案，再以完整移交证据核对内容和关联权限。 */
    @CacheEvict(cacheNames = [FILE_CACHE], allEntries = true)
    internal fun sealPrepared(requests: List<SealRequest>, prepared: Map<String, PreparedFile>): List<String> {
        // 必须在新增档案之前取得旧依据，不能让本次刚写入的引用自行授予资格。
        val inherited = requests.map { inheritedFiles(it.orgId, it.bizType, it.bizId, JsonUtils.toJson(it.snapshot)!!) }
        val rows = requests.map { request ->
            val json = JsonUtils.toJson(request.snapshot) ?: throw BadRequestException("档案内容不能为空")
            val digest = CollectionCareEvidence.digest(json)
            val files = BusinessAttachmentBindingPlanner.plan(request.bizId, "档案附件", request.fileIds).map { ref ->
                val id = binder.resolveSingle(ref.fileId) ?: throw BadRequestException("档案文件不能为空")
                val file = FileDao.findById(id) ?: throw BadRequestException("档案文件不存在")
                val saved = prepared[id] ?: throw BadRequestException("该原件未在过户前核对，不得补入本次接收档案")
                if (file.path != saved.path || file.storageType != saved.storage || fileReadKeys(file) != saved.keys)
                    throw BadRequestException("归档准备后原件身份变化，请核查后重试")
                file to saved
            }.distinctBy { it.first.id.value }
            val existing = CollectionPermanentArchiveDao.find {
                (CollectionPermanentArchives.bizType eq request.bizType) and (CollectionPermanentArchives.bizId eq request.bizId) and (CollectionPermanentArchives.phase eq request.phase)
            }.singleOrNull()
            if (existing != null) {
                if (existing.digest != digest || existing.orgId != request.orgId) throw BadRequestException("该阶段已永久归档，不能替换原始记录")
                requireFileReferences(existing, request.fileIds)
                existing
            } else {
                CollectionPermanentArchiveDao.new {
                    orgId=request.orgId;bizType=request.bizType;bizId=request.bizId;phase=request.phase;snapshot=json;this.digest=digest
                }.also { archive ->
                    files.forEach { (file, saved) ->
                        CollectionPermanentArchiveFileDao.new {
                            orgId=request.orgId;archiveId=archive.id.value;bizId=request.bizId;fileId=file.id.value
                            metadata=CollectionArchiveFileIdentity.metadata(file, request.orgId, request.bizType, request.bizId, saved.keys, saved.content, saved.revision)
                        }
                    }
                }
            }
        }
        rows.forEachIndexed { index, archive ->
            verifyRecordedContent(archive)
            CollectionPermanentArchiveFileDao.find { CollectionPermanentArchiveFiles.archiveId eq archive.id.value }.forEach { file ->
                bindArchiveFile(archive, FileDao[file.fileId], file.fileId in inherited[index])
            }
        }
        return rows.map { it.id.value }
    }

    fun fileReadKeys(file: FileDao): Set<String> {
        val keys = io.hizhengfu.cloud.common.file.FileReadPath.keys(file.path, file.storageType.name, localStorage)
        if (localStorage != null && io.hizhengfu.cloud.common.file.FileReadPath.storageType(file.path, file.storageType.name) == io.hizhengfu.cloud.common.file.EnumFileStorageType.LOCAL && keys.none { it.startsWith("n") })
            throw BadRequestException("原件文件不存在或当前存储无法提供文件身份，请核对后归档")
        return keys
    }

    @CacheEvict(cacheNames = [FILE_CACHE], allEntries = true)
    fun seal(orgId: String, bizType: String, bizId: String, phase: String, snapshot: Any, fileIds: String?): String =
        sealInternal(orgId, bizType, bizId, phase, snapshot, fileIds)

    /** 调用业务须先核对来源关系；仅继承指定摘要对应、同馆且当前可读的已归档原件。 */
    @CacheEvict(cacheNames = [FILE_CACHE], allEntries = true)
    fun sealFromArchives(orgId: String, bizType: String, bizId: String, phase: String, snapshot: Any,
                         fileIds: String?, sourceDigests: Map<String, String>): String {
        if (sourceDigests.isNotEmpty()) requireReadAccess()
        val inherited = sourceDigests.flatMap { (id, expectedDigest) ->
            val source = CollectionPermanentArchiveDao.findById(id)
                ?: throw BadRequestException("继承来源档案不存在")
            if (source.orgId != orgId || source.digest != expectedDigest ||
                source.digest != CollectionCareEvidence.digest(source.snapshot))
                throw BadRequestException("继承来源档案归属或摘要不一致")
            verifyRecordedContent(source)
            val files = CollectionPermanentArchiveFileDao.find { CollectionPermanentArchiveFiles.archiveId eq id }.toList()
            if (files.map { it.fileId }.distinct().size != files.size ||
                files.any { it.orgId != orgId || it.bizId != source.bizId })
                throw BadRequestException("继承来源档案原件重复或归属不一致")
            val policy = archiveReadPolicies?.orderedStream()?.filter { source.bizType in it.businessTypes }?.toList()?.singleOrNull()
                ?: throw BadRequestException("继承来源档案读取规则尚未唯一配置")
            files.forEach { file ->
                policy.requireReadable(CollectionArchiveFileReference(id, orgId, source.bizType, source.bizId, source.phase, file.fileId, file.metadata))
            }
            files.map { it.fileId }
        }.toSet()
        return sealInternal(orgId, bizType, bizId, phase, snapshot, fileIds, inherited)
    }

    /** 业务先核对来源关系，只继承其选定字段；既存来源在目标写入前后均须保持可读及完整。 */
    @CacheEvict(cacheNames = [FILE_CACHE], allEntries = true)
    fun sealFromArchiveFiles(orgId: String, bizType: String, bizId: String, phase: String, snapshot: Any,
                             fileIds: String?, sourceDigests: Map<String, String>, sourceFileIds: Set<String>): String {
        if (sourceDigests.isEmpty() || sourceFileIds.isEmpty())
            throw BadRequestException("继承原件须明确既存来源和选定文件")
        val requested = BusinessAttachmentBindingPlanner.plan(bizId, "档案附件", fileIds)
            .map { binder.resolveSingle(it.fileId) ?: throw BadRequestException("档案文件不能为空") }.toSet()
        if (!requested.containsAll(sourceFileIds)) throw BadRequestException("选定来源文件不属于本次归档字段")
        data class Source(val orgId: String, val bizType: String, val bizId: String, val phase: String,
                          val snapshot: String, val digest: String, val files: Map<String, List<String>>)
        fun verify(id: String, expectedDigest: String): Source {
            val source = CollectionPermanentArchiveDao.findById(id) ?: throw BadRequestException("继承来源档案不存在")
            source.refresh(flush = true)
            if (source.orgId != orgId || source.digest != expectedDigest || source.digest != CollectionCareEvidence.digest(source.snapshot))
                throw BadRequestException("继承来源档案归属或摘要不一致")
            verifyRecordedContent(source)
            val selected = CollectionPermanentArchiveFileDao.find { CollectionPermanentArchiveFiles.archiveId eq id }
                .onEach { it.refresh(flush = false) }.filter { it.fileId in sourceFileIds }
            if (selected.isEmpty() || selected.map { it.fileId }.distinct().size != selected.size ||
                selected.any { it.orgId != orgId || it.bizId != source.bizId })
                throw BadRequestException("选定继承原件缺失、重复或归属不一致")
            val policy = archiveReadPolicies?.orderedStream()?.filter { source.bizType in it.businessTypes }?.toList()?.singleOrNull()
                ?: throw BadRequestException("继承来源档案读取规则尚未唯一配置")
            selected.forEach { file ->
                if (CollectionArchiveFileIdentity.content(JsonUtils.mapper.readTree(file.metadata)) == null)
                    throw BadRequestException("选定继承原件缺少已冻结内容依据")
                policy.requireReadable(CollectionArchiveFileReference(id, orgId, source.bizType, source.bizId, source.phase, file.fileId, file.metadata))
            }
            return Source(source.orgId, source.bizType, source.bizId, source.phase, source.snapshot, source.digest,
                selected.associate { it.id.value to listOf(it.orgId, it.bizId, it.fileId, it.metadata) })
        }
        val before = sourceDigests.mapValues { (id, expected) -> verify(id, expected) }
        if (before.values.flatMap { it.files.values.map { ref -> ref[2] } }.toSet() != sourceFileIds)
            throw BadRequestException("选定原件未全部留存在指定来源中")
        val prepared = prepareFiles(sourceFileIds.sorted().joinToString(","))
        val result = sealInternal(orgId, bizType, bizId, phase, snapshot, fileIds, sourceFileIds)
        if (sourceDigests.mapValues { (id, expected) -> verify(id, expected) } != before ||
            prepareFiles(sourceFileIds.sorted().joinToString(",")) != prepared)
            throw BadRequestException("归档期间继承来源原件已变化，请重新核对")
        return result
    }

    /** 供已注册业务继承本单原件；返回前核对冻结身份及当前业务读取职责。 */
    fun retainedFileIds(orgId: String, businessType: String, businessId: String): Set<String> {
        val ids = CollectionRetainedOriginalFiles.stageIds(businessType, businessId, setOf(orgId))
        if (ids.isEmpty()) return ids
        val matches = archiveReadPolicies?.orderedStream()?.filter { businessType in it.businessTypes }?.toList().orEmpty()
        val policy = matches.singleOrNull() ?: throw BadRequestException("永久原件读取规则尚未唯一配置")
        CollectionPermanentArchiveDao.find {
            (CollectionPermanentArchives.orgId eq orgId) and (CollectionPermanentArchives.bizType eq businessType) and
                (CollectionPermanentArchives.bizId eq businessId)
        }.forEach { archive ->
            CollectionPermanentArchiveFileDao.find { CollectionPermanentArchiveFiles.archiveId eq archive.id.value }.forEach { file ->
                policy.requireReadable(CollectionArchiveFileReference(archive.id.value, orgId, businessType, businessId, archive.phase, file.fileId, file.metadata))
            }
        }
        return ids
    }

    /** 仅供变更前留存已存在的主档来源；不接受目标业务或任意附件列表。 */
    @CacheEvict(cacheNames = [FILE_CACHE], allEntries = true)
    internal fun sealExistingMainOriginal(ref: CollectionOriginalFileReference, phase: String, snapshot: Map<String, Any>): String {
        if (ref.businessType != CollectionMainOriginalFileSource.BIZ ||
            ref !in CollectionMainOriginalFileSource().references(ref.businessType, ref.businessId))
            throw BadRequestException("原件不属于变更前的有效主档来源")
        return sealExistingOriginal(ref, phase, snapshot)
    }

    @CacheEvict(cacheNames = [FILE_CACHE], allEntries = true)
    internal fun sealExistingBusinessOriginal(ref: CollectionOriginalFileReference, phase: String, snapshot: Map<String, Any>): String {
        val appraisalMember = ref.businessType == CollectionConst.BIZ_APPRAISAL &&
            AppraisalMemberDao.findById(ref.recordId)?.let { member ->
                member.appraisalId == ref.businessId && member.orgId == ref.orgId &&
                    AppraisalDao.findById(ref.businessId)?.orgId == ref.orgId
            } == true
        if (ref.businessType !in CollectionBusinessOriginalFiles.INHERITABLE_BUSINESSES ||
            (ref.recordId != ref.businessId && !appraisalMember) ||
            ref !in CollectionBusinessOriginalFileSource().references(ref.businessType, ref.businessId))
            throw BadRequestException("原件不属于变更前的有效业务来源")
        return sealExistingOriginal(ref, phase, snapshot)
    }

    @CacheEvict(cacheNames = [FILE_CACHE], allEntries = true)
    internal fun sealExistingAcquisitionOriginal(ref: AcquisitionOriginalFiles.Reference,phase: String,snapshot: Map<String,Any>): String {
        if(ref !in AcquisitionOriginalFiles(this,localStorage).existingReferences(ref.acquisitionId))
            throw BadRequestException("原件不属于变更前的征集来源")
        val id=binder.resolveSingle(ref.file) ?: throw BadRequestException("征集原件引用不能为空")
        val file=FileDao[id]
        val access=requireReadAccess()
        access.requireReadable(file.path,file.storageType.name)
        val keys=fileReadKeys(file).sorted()
        val expectedPhase=AcquisitionOriginalFiles.IDENTITY_PHASE+CollectionCareEvidence.digest("${id}|${file.path}|${file.storageType}|${keys}").take(48)
        if(phase!=expectedPhase || snapshot["fileId"]!=id || snapshot["path"]!=file.path ||
            snapshot["storageType"]!=file.storageType.name || snapshot["readKeys"]!=keys)
            throw BadRequestException("征集原件身份与已存来源不一致")
        return sealInternal(ref.orgId,CollectionConst.BIZ_ACQUISITION,ref.acquisitionId,phase,snapshot,id,setOf(id))
    }

    private fun sealExistingOriginal(ref: CollectionOriginalFileReference, phase: String, snapshot: Map<String, Any>): String {
        val id = binder.resolveSingle(ref.file) ?: throw BadRequestException("原件引用不能为空")
        val file = FileDao[id]
        val access = requireReadAccess()
        val receivedMain = if (ref.businessType == CollectionMainOriginalFileSource.BIZ)
            prepareReceivedMainOriginals(ref.orgId, CollectionBusinessOriginalFiles.BIZ, ref.businessId,
                JsonUtils.toJson(snapshot)!!, id) else emptyMap()
        if (receivedMain.isEmpty()) access.requireReadable(file.path, file.storageType.name)
        if (snapshot["businessType"] != ref.businessType || snapshot["fileId"] != id ||
            snapshot["path"] != file.path || snapshot["storageType"] != file.storageType.name ||
            snapshot["readKeys"] != fileReadKeys(file).sorted() || !phase.startsWith(CollectionBusinessOriginalFiles.PHASE))
            throw BadRequestException("原件留存身份与已存来源不一致")
        return sealInternal(ref.orgId, CollectionBusinessOriginalFiles.BIZ, ref.businessId, phase, snapshot, id, setOf(id), receivedMain)
    }

    private fun sealInternal(orgId: String, bizType: String, bizId: String, phase: String, snapshot: Any, fileIds: String?, existingOriginals: Set<String> = emptySet(),
                             preparedMain: Map<String, ReceivedMainOriginal>? = null): String {
        val json = JsonUtils.toJson(snapshot) ?: throw BadRequestException("档案内容不能为空")
        val digest = MessageDigest.getInstance("SHA-256").digest(json.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        // 来源能力必须在本次档案头及文件引用写入之前取得，不能由新 MAIN/APPLIED 自行授权。
        val receivedMain = preparedMain ?: prepareReceivedMainOriginals(orgId, bizType, bizId, json, fileIds)
        val existing = CollectionPermanentArchiveDao.find {
            (CollectionPermanentArchives.bizType eq bizType) and (CollectionPermanentArchives.bizId eq bizId) and (CollectionPermanentArchives.phase eq phase)
        }.singleOrNull()
        if (existing != null) {
            if (existing.digest != CollectionCareEvidence.digest(existing.snapshot)) throw BadRequestException("既有永久档案快照与摘要不一致，请核对原档案")
            if (existing.digest != digest || existing.orgId != orgId) throw BadRequestException("该阶段已永久归档，不能替换原始记录")
            requireFileReferences(existing, fileIds)
            verifyRecordedContent(existing)
            receivedMain.values.forEach(::verifyReceivedMainOriginal)
            return existing.id.value
        }
        val inherited = existingOriginals + inheritedFiles(orgId, bizType, bizId, json)
        val files = BusinessAttachmentBindingPlanner.plan(bizId, "档案附件", fileIds).map { ref ->
            val fileId = binder.resolveSingle(ref.fileId) ?: throw BadRequestException("档案文件不能为空")
            FileDao.findById(fileId) ?: throw BadRequestException("档案文件不存在")
        }
        if (files.isNotEmpty()) requireReadAccess()
        val archive = CollectionPermanentArchiveDao.new {
            this.orgId=orgId; this.bizType=bizType; this.bizId=bizId; this.phase=phase; this.snapshot=json; this.digest=digest
        }
        files.distinctBy { it.id.value }.forEach { file ->
            val keys = fileReadKeys(file)
            val captured = receivedMain[file.id.value]?.let { prior ->
                verifyReceivedMainOriginal(prior)
                requireReadAccess().contentBaseline(file.path, file.storageType.name)?.requireMatch(prior.content)
                ContentCapture(prior.content, prior.revision)
            } ?: captureContent(file)
            if (fileReadKeys(file) != keys) throw BadRequestException("归档期间原件文件身份发生变化，请核查后重试")
            val metadata = CollectionArchiveFileIdentity.metadata(file, orgId, bizType, bizId, keys, captured.content, captured.revision)
            CollectionPermanentArchiveFileDao.new {
                this.orgId=orgId; archiveId=archive.id.value; this.bizId=bizId; fileId=file.id.value
                this.metadata=metadata
            }
        }
        // 绑定会核对读取权限及完整原件集合，须先在同一事务中写齐引用。
        files.distinctBy { it.id.value }.forEach { file -> bindArchiveFile(archive, file, file.id.value in inherited, receivedMain[file.id.value]) }
        receivedMain.values.forEach(::verifyReceivedMainOriginal)
        return archive.id.value
    }

    private fun inheritedFiles(orgId: String, bizType: String, bizId: String, json: String): Set<String> {
        val origin = if (bizType == CollectionBusinessOriginalFiles.BIZ) JsonUtils.mapper.readTree(json).path("businessType").asString() else bizType
        // 自动继承必须有唯一业务读取规则；缺失策略时只能走普通上传引用校验。
        val canInherit=archiveReadPolicies?.orderedStream()?.filter { origin in it.businessTypes }?.count()==1L
        if (!canInherit) return emptySet()
        val business = run {
            val orgs = if (origin == CollectionConst.BIZ_TRANSFER) CollectionTransferDao.findById(bizId)?.let { setOfNotNull(it.orgId, it.toOrgId) } ?: setOf(orgId) else setOf(orgId)
            CollectionRetainedOriginalFiles.ids(origin, bizId, orgs) + CollectionRetainedOriginalFiles.stageIds(origin, bizId, orgs)
        }
        val returnedSource = if (origin == CollectionConst.BIZ_RETURN) returnSourceOriginals(orgId, bizId) else emptySet()
        val exhibitionSource = if (origin == CollectionConst.BIZ_REQUISITION) exhibitionSourceOriginals(orgId, bizId) else emptySet()
        return business + returnedSource + exhibitionSource + retainedMainFiles(bizType, bizId, json)
    }

    /** 展览审批派生提用时，审批人可沿用同馆、同清单的已批准展览当前原件；不取得上传归属。 */
    private fun exhibitionSourceOriginals(orgId: String, id: String): Set<String> {
        val requisition = RequisitionDao.findById(id) ?: return emptySet()
        if (requisition.orgId != orgId || requisition.sourceBizType != CollectionConst.BIZ_EXHIBITION ||
            requisition.purpose != io.hizhengfu.cloud.collection.domain.EnumMoveReason.EXHIBIT) return emptySet()
        val exhibition = requisition.sourceBizId?.let(ExhibitionDao::findById) ?: return emptySet()
        if (exhibition.orgId != orgId || exhibition.flowStatus != io.hizhengfu.cloud.common.domain.EnumFlowStatus.PASS ||
            splitCollectionIds(exhibition.collectionIds).toSet().isEmpty() ||
            splitCollectionIds(exhibition.collectionIds).toSet() != splitCollectionIds(requisition.collectionIds).toSet()) return emptySet()
        if (archiveReadPolicies?.orderedStream()?.filter { CollectionConst.BIZ_EXHIBITION in it.businessTypes }?.count() != 1L)
            return emptySet()
        val current = BusinessAttachmentBindingPlanner.plan(exhibition.id.value, "展览当前原件", exhibition.attachmentIds)
            .mapNotNull { binder.resolveSingle(it.fileId) }.toSet()
        return current.intersect(CollectionRetainedOriginalFiles.ids(CollectionConst.BIZ_EXHIBITION, exhibition.id.value, setOf(orgId)))
    }

    /** 归还只能继承本单实际出库来源的已核实原件，来源类型、馆别和逐件基线须一致。 */
    private fun returnSourceOriginals(orgId: String, id: String): Set<String> {
        val entity = CollectionReturnDao.findById(id) ?: return emptySet()
        if (entity.orgId != orgId || entity.sourceSnapshot == null) return emptySet()
        val source = try { returnSourceSnapshot(entity) } catch (_: BadRequestException) { return emptySet() }
        if (entity.sourceSnapshot != source) return emptySet()
        return CollectionRetainedOriginalFiles.ids(entity.sourceBizType, entity.sourceBizId, setOf(orgId)) +
            CollectionRetainedOriginalFiles.stageIds(entity.sourceBizType, entity.sourceBizId, setOf(orgId))
    }

    /** 主档可进入自身移交档案；信息变更只继承系统取得的变更前封面。 */
    private fun retainedMainFiles(bizType: String, bizId: String, json: String): Set<String> {
        val origin = if (bizType == CollectionBusinessOriginalFiles.BIZ) JsonUtils.mapper.readTree(json).path("businessType").asString() else bizType
        if (origin == CollectionConst.BIZ_CHANGE) {
            val change = CollectionChangeDao.findById(bizId) ?: return emptySet()
            val before = binder.resolveSingle(change.before.coverImage) ?: return emptySet()
            return CollectionMainOriginalFileSource().historicalFileIds(change.collectionId).filter { it == before }.toSet()
        }
        val collectionId = when (bizType) {
            CollectionBusinessOriginalFiles.BIZ -> if (JsonUtils.mapper.readTree(json).path("businessType").asString() == CollectionMainOriginalFileSource.BIZ) bizId else null
            "COLLECTION_RETAINED_LEDGER" -> CollectionRetainedLedgerDao.findById(bizId)?.collectionId
            CollectionConst.BIZ_TRANSFER -> CollectionTransferDao.findById(bizId)?.collectionId
            else -> null
        } ?: return emptySet()
        return CollectionMainOriginalFileSource().historicalFileIds(collectionId).toSet()
    }

    /** 专用归档关联只指向已写齐的永久引用，不能用于任意内容；不改写上传人。 */
    private fun bindArchiveFile(archive: CollectionPermanentArchiveDao, file: FileDao, inheritedOriginal: Boolean,
                                receivedMain: ReceivedMainOriginal? = null) {
        val access = requireReadAccess()
        if (!inheritedOriginal && receivedMain == null) {
            binder.bindSingle(archive.id.value, "永久档案-${file.name}", file.id.value)
            return
        }
        val ref = CollectionPermanentArchiveFileDao.find {
            (CollectionPermanentArchiveFiles.archiveId eq archive.id.value) and (CollectionPermanentArchiveFiles.fileId eq file.id.value)
        }.singleOrNull() ?: throw BadRequestException("永久档案原件引用缺失或重复")
        if (ref.orgId != archive.orgId || ref.bizId != archive.bizId || archive.digest != CollectionCareEvidence.digest(archive.snapshot))
            throw BadRequestException("永久档案原件归属不一致")
        if (receivedMain != null) {
            if (receivedMain.fileId != file.id.value || receivedMain.orgId != archive.orgId)
                throw BadRequestException("接收原件继承上下文与目标档案不一致")
            verifyReceivedMainOriginal(receivedMain)
            val metadata = JsonUtils.mapper.readTree(ref.metadata)
            if (CollectionArchiveFileIdentity.content(metadata) != receivedMain.content ||
                CollectionArchiveFileIdentity.revision(metadata) != receivedMain.revision)
                throw BadRequestException("继承原件内容或存储版本与接收依据不一致")
        } else if (!access.requireReadable(file.path, file.storageType.name)) {
            throw BadRequestException("原件未经过业务读取规则保护，不能继承引用资格")
        }
        if (FileContents.selectAll().where { (FileContents.fileId eq file.id.value) and (FileContents.contentId eq archive.id.value) }.empty()) {
            FileContents.insert {
                it[fileId] = file.id.value
                it[contentId] = archive.id.value
                it[aliasName] = "永久档案-${file.name}"
            }
        }
    }

    /** 私有能力只承认本次写入前既存的逐件 RECEIVED，不授予通用文件或原馆行政案卷权限。 */
    private data class ReceivedMainOriginal(
        val collectionId: String, val orgId: String, val fileId: String,
        val path: String, val storage: EnumFileStorageType, val keys: Set<String>,
        val archiveId: String, val snapshot: String, val digest: String, val metadata: String,
        val content: CollectionOriginalContent, val revision: FileObjectRevision?,
    )

    private fun receivedMainContext(bizType: String, bizId: String, json: String): String? = when {
        bizType == CollectionBusinessOriginalFiles.BIZ &&
            JsonUtils.mapper.readTree(json).path("businessType").asString() == CollectionMainOriginalFileSource.BIZ -> bizId
        bizType == CollectionConst.BIZ_CHANGE || (bizType == CollectionBusinessOriginalFiles.BIZ &&
            JsonUtils.mapper.readTree(json).path("businessType").asString() == CollectionConst.BIZ_CHANGE) ->
            CollectionChangeDao.findById(bizId)?.collectionId
        else -> null
    }

    private fun prepareReceivedMainOriginals(orgId: String, bizType: String, bizId: String,
                                           json: String, fileIds: String?): Map<String, ReceivedMainOriginal> {
        val collectionId = receivedMainContext(bizType, bizId, json) ?: return emptyMap()
        if (CollectionDao.findById(collectionId)?.orgId != orgId) return emptyMap()
        val allowed = if (bizType == CollectionBusinessOriginalFiles.BIZ &&
            JsonUtils.mapper.readTree(json).path("businessType").asString() == CollectionMainOriginalFileSource.BIZ) {
            val source = CollectionMainOriginalFileSource()
            source.references(CollectionMainOriginalFileSource.BIZ, collectionId)
                .filter { it.businessType == CollectionMainOriginalFileSource.BIZ }
                .mapNotNull { binder.resolveSingle(it.file) }.toSet() + source.historicalFileIds(collectionId)
        } else retainedMainFiles(bizType, bizId, json)
        val ids = BusinessAttachmentBindingPlanner.plan(bizId, "接收主档原件", fileIds)
            .mapNotNull { binder.resolveSingle(it.fileId) }.toSet().intersect(allowed)
        return ids.mapNotNull { id ->
            val prior = CollectionRetainedLedgerDao.find {
                (CollectionRetainedLedgers.collectionId eq collectionId) and (CollectionRetainedLedgers.targetOrgId eq orgId)
            }.filter { id in it.snapshot.originalFileIds }.map { retained ->
                CollectionPermanentArchiveDao.find {
                    (CollectionPermanentArchives.bizType eq CollectionConst.BIZ_TRANSFER) and
                        (CollectionPermanentArchives.bizId eq retained.transferId) and (CollectionPermanentArchives.phase eq "RECEIVED")
                }.singleOrNull() ?: throw BadRequestException("主档继承缺少本次接收馆的既存接收档案")
            }.sortedBy { it.id.value }.firstOrNull() ?: return@mapNotNull null
            val ref = CollectionPermanentArchiveFileDao.find {
                (CollectionPermanentArchiveFiles.archiveId eq prior.id.value) and (CollectionPermanentArchiveFiles.fileId eq id)
            }.singleOrNull() ?: throw BadRequestException("主档继承接收原件引用缺失或重复")
            val file = FileDao.findById(id) ?: throw BadRequestException("接收主档原件不存在")
            val metadata = JsonUtils.mapper.readTree(ref.metadata)
            val content = CollectionArchiveFileIdentity.content(metadata)
                ?: throw BadRequestException("接收主档原件缺少冻结内容基线，不能继承归档")
            val revision = CollectionPermanentRemoteRecoveryRegistry.effective(id, orgId,
                metadata.path("path").asString(), CollectionArchiveFileIdentity.revision(metadata), content)
            val proof = ReceivedMainOriginal(collectionId, orgId, id, file.path, file.storageType, fileReadKeys(file),
                prior.id.value, prior.snapshot, prior.digest, ref.metadata, content, revision)
            verifyReceivedMainOriginal(proof)
            id to proof
        }.toMap()
    }

    /** 当前身份、藏品归属、完整移交链及来源原件在绑定前和返回前重新核对。 */
    private fun verifyReceivedMainOriginal(proof: ReceivedMainOriginal) {
        requireReadAccess()
        if (CollectionDao.findById(proof.collectionId)?.orgId != proof.orgId)
            throw BadRequestException("接收主档所属馆已变化，请重新核查")
        val source = CollectionPermanentArchiveDao.findById(proof.archiveId)
            ?: throw BadRequestException("主档继承接收档案不存在")
        if (source.orgId != proof.orgId || source.bizType != CollectionConst.BIZ_TRANSFER || source.phase != "RECEIVED" ||
            source.snapshot != proof.snapshot || source.digest != proof.digest || source.digest != CollectionCareEvidence.digest(source.snapshot))
            throw BadRequestException("主档继承接收档案已变化或摘要不一致")
        val retained = CollectionRetainedLedgerDao.find {
            CollectionRetainedLedgers.transferId eq source.bizId
        }.singleOrNull() ?: throw BadRequestException("主档继承缺少逐件调拨留存依据")
        if (retained.collectionId != proof.collectionId || retained.targetOrgId != proof.orgId ||
            proof.fileId !in retained.snapshot.originalFileIds)
            throw BadRequestException("该原件不属于本藏品实际接收清单")
        val ref = CollectionPermanentArchiveFileDao.find {
            (CollectionPermanentArchiveFiles.archiveId eq proof.archiveId) and (CollectionPermanentArchiveFiles.fileId eq proof.fileId)
        }.singleOrNull() ?: throw BadRequestException("主档继承接收原件引用缺失或重复")
        if (ref.orgId != proof.orgId || ref.bizId != source.bizId || ref.metadata != proof.metadata)
            throw BadRequestException("主档继承接收原件归属或身份已变化")
        val metadata = JsonUtils.mapper.readTree(ref.metadata)
        val currentRevision = CollectionPermanentRemoteRecoveryRegistry.effective(proof.fileId, proof.orgId,
            metadata.path("path").asString(), CollectionArchiveFileIdentity.revision(metadata), proof.content)
        if (currentRevision != proof.revision)
            throw BadRequestException("接收主档原件的有效存储版本已变化，请重新核查")
        val mainPolicy = archiveReadPolicies?.orderedStream()?.filter { it is CollectionMainFileReadPolicy }
            ?.toList()?.singleOrNull() as? CollectionMainFileReadPolicy
            ?: throw BadRequestException("主档接收原件读取规则尚未唯一配置")
        if (!mainPolicy.requireReceivedOriginalReadable(CollectionArchiveFileReference(source.id.value, source.orgId,
                source.bizType, source.bizId, source.phase, proof.fileId, ref.metadata)))
            throw BadRequestException("缺少与本主档原件一致的实际接收依据")
        val file = FileDao.findById(proof.fileId) ?: throw BadRequestException("接收主档原件不存在")
        if (file.path != proof.path || file.storageType != proof.storage || fileReadKeys(file) != proof.keys)
            throw BadRequestException("接收主档原件身份已变化，请重新核查")
        requireFileReferences(source, CollectionPermanentArchiveFileDao.find {
            CollectionPermanentArchiveFiles.archiveId eq source.id.value
        }.joinToString(",") { it.fileId })
        verifyRecordedContent(source)
    }

    private data class ContentCapture(val content: CollectionOriginalContent?, val revision: FileObjectRevision? = null)

    private fun captureContent(file: FileDao): ContentCapture {
        val access = requireReadAccess()
        if (FileReadPath.storageType(file.path, file.storageType.name) == EnumFileStorageType.RUSTFS) {
            val storage = objectStorage?.getIfAvailable() ?: throw BadRequestException("远端原件存储读取服务未配置，无法核验归档内容")
            val before = storage.metadata(file.path, file.storageType.name)
            val revision = before.revision ?: throw BadRequestException("远端存储未提供版本或条件读取标识，无法稳定核验归档原件")
            val content = storage.openRevision(file.path, file.storageType.name, revision).use { CollectionOriginalContent.read(it, maximumBytes = before.size) }
            val after = storage.metadataAtRevision(file.path, file.storageType.name, revision)
            if (before.size != content.size || before != after) throw BadRequestException("归档期间远端原件信息发生变化，请核查后重试")
            access.contentBaseline(file.path, file.storageType.name)?.requireMatch(content)
            return ContentCapture(content, revision)
        }
        val local = localStorage ?: return ContentCapture(null)
        val before = local.metadata(file.path)
        val content = local.openStream(file.path).use { CollectionOriginalContent.read(it) }
        val after = local.metadata(file.path)
        if (before.size != content.size || before != after) throw BadRequestException("归档期间原件内容发生变化，请核查后重试")
        access.contentBaseline(file.path, file.storageType.name)?.requireMatch(content)
        return ContentCapture(content)
    }

    /** 幂等归档不重新建立内容基线；发现当前原件变化时也不能返回成功。 */
    private fun verifyRecordedContent(archive: CollectionPermanentArchiveDao) {
        CollectionPermanentArchiveFileDao.find { CollectionPermanentArchiveFiles.archiveId eq archive.id.value }.forEach { file ->
            requireReadAccess()
            val metadata = try { JsonUtils.mapper.readTree(file.metadata) }
                catch (_: tools.jackson.core.JacksonException) { throw BadRequestException("永久原件元数据无法解析") }
            val ref = CollectionArchiveFileReference(archive.id.value, archive.orgId, archive.bizType, archive.bizId, archive.phase, file.fileId, file.metadata)
            val keys = CollectionArchiveFileIdentity.keys(ref, metadata)
            val expected = CollectionArchiveFileIdentity.content(metadata) ?: return@forEach
            val revision = CollectionPermanentRemoteRecoveryRegistry.effective(file.fileId, archive.orgId,
                metadata.path("path").asString(), CollectionArchiveFileIdentity.revision(metadata), expected)
            if (revision != null) {
                val storage = objectStorage?.getIfAvailable() ?: throw BadRequestException("远端原件存储服务未配置")
                storage.openRevision(metadata.path("path").asString(), metadata.path("storageType").asString(), revision).use {
                    expected.requireMatch(CollectionOriginalContent.read(it, maximumBytes = expected.size))
                }
                return@forEach
            }
            val local = localStorage ?: throw BadRequestException("无法核对已留存的本地原件内容，请核查存储配置")
            val current = FileDao.findById(file.fileId)
            val candidates = listOf(metadata.path("path").asString()) + listOfNotNull(current?.takeIf { it.storageType == io.hizhengfu.cloud.common.file.EnumFileStorageType.LOCAL }?.path)
            val native = keys.filter { it.startsWith("n") || it.startsWith("u") }.toSet()
            val locations = candidates.distinct()
            val missing = mutableSetOf<String>()
            val path = locations.firstOrNull { candidate ->
                try {
                    val observed = io.hizhengfu.cloud.common.file.FileReadPath.keys(candidate, "LOCAL", local)
                    // 缺失文件的读取键只剩路径。用明确的文件不存在异常区分身份变化和存储访问失败。
                    if (observed.none { it.startsWith("n") || it.startsWith("u") }) local.metadata(candidate)
                    observed.any(native::contains) || CollectionPermanentOriginalTransitionRegistry.accepts(
                        CollectionPermanentOriginalTransitionRegistry.one(file.fileId), file.fileId, archive.orgId,
                        native, observed, expected) || CollectionPermanentOriginalRecoveryRegistry.accepts(
                        CollectionPermanentOriginalRecoveryRegistry.one(file.fileId), file.fileId, archive.orgId,
                        native, observed, expected)
                }
                catch (_: java.nio.file.NoSuchFileException) { missing += candidate; false }
                catch (_: java.io.IOException) { false }
                catch (_: BadRequestException) { false }
            } ?: throw BadRequestException(if (missing.size == locations.size)
                "已归档原件文件缺失（文件编号：${file.fileId}），请核查原件存储位置与备份"
                else "已归档原件的文件身份无法核实，请核查原件与备份")
            local.openStream(path).use { expected.requireMatch(CollectionOriginalContent.read(it)) }
        }
    }

    /** 业务模块完成当前授权后只读核对已存在阶段；false 表示历史附件缺少内容基线，不能宣称已验证字节。 */
    @Transactional(readOnly = true, noRollbackFor = [BadRequestException::class])
    fun verifyExisting(archive: CollectionPermanentArchiveDao, orgId: String, bizType: String, bizId: String,
        phase: String, snapshot: Any, fileIds: String?, verifyContent: Boolean = true): Boolean {
        requireExistingMetadata(archive, orgId, bizType, bizId, phase, snapshot, fileIds)
        if (verifyContent) verifyRecordedContent(archive)
        return CollectionPermanentArchiveFileDao.find { CollectionPermanentArchiveFiles.archiveId eq archive.id.value }.all {
            CollectionArchiveFileIdentity.content(JsonUtils.mapper.readTree(it.metadata)) != null
        }
    }

    /** 详情先核对业务、快照和文件引用；文件字节可用性单独展示，不能替代办理时的 verifyExisting。 */
    internal fun requireExistingMetadata(archive: CollectionPermanentArchiveDao, orgId: String, bizType: String, bizId: String,
        phase: String, snapshot: Any, fileIds: String?) {
        val expected = JsonUtils.toJson(snapshot) ?: throw BadRequestException("核验依据不能为空")
        if (archive.orgId != orgId || archive.bizType != bizType || archive.bizId != bizId || archive.phase != phase ||
            archive.digest != CollectionCareEvidence.digest(archive.snapshot) || archive.digest != CollectionCareEvidence.digest(expected))
            throw BadRequestException("现有记录与永久原件内容或摘要不一致，请核查原件")
        requireFileReferences(archive, fileIds)
    }

    /** 核对归档引用与冻结文件身份；内容字节验收仍由 verifyExisting 执行。 */
    @Transactional(noRollbackFor = [BadRequestException::class])
    fun requireFileReferences(archive: CollectionPermanentArchiveDao, fileIds: String?) {
        val refs = CollectionPermanentArchiveFileDao.find { CollectionPermanentArchiveFiles.archiveId eq archive.id.value }.toList()
        val expected = BusinessAttachmentBindingPlanner.plan(archive.bizId, "档案原件", fileIds).map {
            val reference = it.fileId
            if (refs.any { ref -> ref.fileId == reference }) reference else {
                // 旧表单可按路径归档；改名后按当时冻结的元数据找回同一文件，不能只查当前路径。
                val frozen = refs.filter { ref ->
                    try { JsonUtils.mapper.readTree(ref.metadata)?.get("path")?.stringValue() == reference }
                    catch (_: tools.jackson.core.JacksonException) { throw BadRequestException("档案文件元数据无法解析，请核对原档案") }
                }.map { ref -> ref.fileId }.distinct()
                when (frozen.size) {
                    0 -> binder.resolveSingle(reference) ?: throw BadRequestException("档案原件引用无法核实")
                    1 -> frozen.single()
                    else -> throw BadRequestException("档案路径对应多个原件，请核对原档案")
                }
            }
        }.toSet()
        if (refs.map { it.fileId }.toSet() != expected || refs.any { it.orgId != archive.orgId || it.bizId != archive.bizId || FileDao.findById(it.fileId) == null })
            throw BadRequestException("档案原件引用缺失或归属不一致，请核对原档案")
        refs.forEach { ref ->
            val metadata = try { JsonUtils.mapper.readTree(ref.metadata) }
                catch (_: tools.jackson.core.JacksonException) { throw BadRequestException("档案文件元数据无法解析，请核对原档案") }
            CollectionArchiveFileIdentity.keys(CollectionArchiveFileReference(archive.id.value, archive.orgId,
                archive.bizType, archive.bizId, archive.phase, ref.fileId, ref.metadata), metadata)
        }
    }

    /** 供藏品、保护等业务服务在完成当前读取授权后调用；每个档案头及文件引用仍须与该业务馆别一致。 */
    @Transactional(noRollbackFor = [BadRequestException::class])
    fun listForAuthorizedOrg(bizType: String, bizId: String, orgId: String): List<Map<String, Any?>> =
        listForAuthorizedOrgs(bizType, bizId, setOf(orgId))

    /** 原件下载逐份核验所选档案；其他阶段原件不可用不影响这份有效档案。 */
    @Transactional(noRollbackFor = [BadRequestException::class])
    fun archiveForAuthorizedOrg(bizType: String, bizId: String, orgId: String, archiveId: String, excludedPhasePrefix: String? = null): Map<String, Any?> {
        val archive=CollectionPermanentArchiveDao.findById(archiveId)
            ?.takeIf {it.bizType==bizType && it.bizId==bizId && it.orgId==orgId &&
                (excludedPhasePrefix==null || !it.phase.startsWith(excludedPhasePrefix))}
            ?: throw BadRequestException("该永久档案不属于当前业务或可查阅阶段")
        return verifiedArchive(archive,bizId,setOf(orgId))
    }

    /** 已由调用方核验本馆业务读取权；只读取指定馆别与阶段，避免历史接收单触及原馆其他档案。 */
    @Transactional(noRollbackFor = [BadRequestException::class])
    fun phaseForAuthorizedOrg(bizType: String, bizId: String, orgId: String, phase: String): Map<String, Any?>? =
        CollectionPermanentArchiveDao.find {
            (CollectionPermanentArchives.bizType eq bizType) and (CollectionPermanentArchives.bizId eq bizId) and
                (CollectionPermanentArchives.phase eq phase)
        }.singleOrNull()?.let { verifiedArchive(it, bizId, setOf(orgId)) }

    /** 详情中的单个阶段保留冻结记录；仅文件内容异常转为提示，归属与快照仍严格核对。 */
    @Transactional(noRollbackFor = [BadRequestException::class])
    fun phaseForDisplay(bizType: String, bizId: String, orgId: String, phase: String): Map<String, Any?>? =
        CollectionPermanentArchiveDao.find {
            (CollectionPermanentArchives.bizType eq bizType) and (CollectionPermanentArchives.bizId eq bizId) and
                (CollectionPermanentArchives.phase eq phase)
        }.singleOrNull()?.let { displayArchive(it, archiveMetadata(it, bizId, setOf(orgId))) }

    /** 双馆业务已校验申请读取权后，逐份核验档案确属预期双方；本方法本身不授予读取权。 */
    @Transactional(noRollbackFor = [BadRequestException::class])
    fun listForAuthorizedOrgs(bizType: String, bizId: String, orgIds: Set<String>): List<Map<String, Any?>> =
        CollectionPermanentArchiveDao.find {
            (CollectionPermanentArchives.bizType eq bizType) and (CollectionPermanentArchives.bizId eq bizId)
        }.sortedBy { it.createTime }.map { archive -> verifiedArchive(archive, bizId, orgIds) }

    data class ArchiveInspection(val archives: List<Map<String, Any?>>, val issues: List<String>)

    /** 仅用于已授权的详情展示。快照或归属异常仍拒绝；原件不可用时保留记录并移除下载引用。 */
    @Transactional(noRollbackFor = [BadRequestException::class])
    fun readForDisplay(bizType: String, bizId: String, orgId: String): ArchiveInspection {
        return readForDisplay(bizType, bizId, setOf(orgId))
    }

    @Transactional(noRollbackFor = [BadRequestException::class])
    fun readForDisplay(bizType: String, bizId: String, orgIds: Set<String>): ArchiveInspection {
        val rows = CollectionPermanentArchiveDao.find {
            (CollectionPermanentArchives.bizType eq bizType) and (CollectionPermanentArchives.bizId eq bizId)
        }.sortedBy { it.createTime }
        // 先完成全部元数据核验，再检查存储；不能把权限或快照错误当成文件不可用。
        val metadata = rows.map { archiveMetadata(it, bizId, orgIds) }
        val issues = mutableListOf<String>()
        val result = rows.zip(metadata).map { (archive, data) ->
            displayArchive(archive, data).also { value ->
                (value["evidenceIssue"] as? String)?.let { issues += "${archive.phase}：$it" }
            }
        }
        return ArchiveInspection(result, issues)
    }

    private fun displayArchive(archive: CollectionPermanentArchiveDao, metadata: Map<String, Any?>): Map<String, Any?> {
        val issue = try { verifyRecordedContent(archive); null }
        catch (failure: BadRequestException) { failure.message ?: "归档原件暂不可用，请核查原件与备份" }
        catch (_: java.io.IOException) { "归档原件暂不可用，请核查存储位置与备份" }
        return if (issue == null) metadata else metadata + mapOf("evidenceIssue" to issue, "files" to emptyList<Any>())
    }

    /** 调用方已核实本馆案卷读取权；详情可展示业务记录和逐份异常，损坏档案不返回快照或下载引用。 */
    @Transactional(noRollbackFor = [BadRequestException::class])
    fun inspectForAuthorizedOrg(bizType: String, bizId: String, orgId: String, excludedPhasePrefix: String? = null): ArchiveInspection {
        val rows = CollectionPermanentArchiveDao.find {
            (CollectionPermanentArchives.bizType eq bizType) and (CollectionPermanentArchives.bizId eq bizId)
        }.sortedBy { it.createTime }
        // 馆别属于授权边界，不能作为可展示的档案问题捕获，也不能返回异馆阶段名称。
        if (rows.any { it.orgId != orgId }) throw BadRequestException("永久档案所属馆不一致，请核对原件")
        val valid = mutableListOf<Map<String, Any?>>()
        val issues = mutableListOf<String>()
        rows.filterNot { excludedPhasePrefix != null && it.phase.startsWith(excludedPhasePrefix) }.forEach { archive ->
            try { valid += verifiedArchive(archive, bizId, setOf(orgId)) }
            catch (failure: BadRequestException) { issues += "${archive.phase}：${failure.message}" }
        }
        return ArchiveInspection(valid, issues)
    }

    private fun verifiedArchive(archive: CollectionPermanentArchiveDao, bizId: String, orgIds: Set<String>): Map<String, Any?> {
        val metadata = archiveMetadata(archive, bizId, orgIds)
        verifyRecordedContent(archive)
        return metadata
    }

    private fun archiveMetadata(archive: CollectionPermanentArchiveDao, bizId: String, orgIds: Set<String>): Map<String, Any?> {
        if (archive.orgId !in orgIds || archive.digest != CollectionCareEvidence.digest(archive.snapshot))
            throw BadRequestException("永久档案所属馆或快照摘要不一致，请核对原件")
        val declared = when (archive.bizType) {
            CollectionConst.BIZ_SAMPLING -> SamplingArchiveOriginalReferences.fileIds(archive.phase, archive.snapshot)
            ReproductionComplianceService.BIZ -> ReproductionArchiveOriginalReferences.fileIds(archive.phase, archive.snapshot)
            else -> null
        }
        declared?.let { requireFileReferences(archive, it) }
        val files = CollectionPermanentArchiveFileDao.find { CollectionPermanentArchiveFiles.archiveId eq archive.id.value }.map { file ->
            if (file.orgId != archive.orgId || file.bizId != bizId || FileDao.findById(file.fileId) == null)
                throw BadRequestException("永久档案附件引用缺失或所属业务不一致")
            val metadata = try { JsonUtils.mapper.readTree(file.metadata) }
                catch (_: tools.jackson.core.JacksonException) { throw BadRequestException("永久原件文件元数据无法解析") }
            CollectionArchiveFileIdentity.keys(CollectionArchiveFileReference(archive.id.value, archive.orgId, archive.bizType, bizId,
                archive.phase, file.fileId, file.metadata), metadata)
            mapOf("fileId" to file.fileId, "metadata" to file.metadata)
        }
        return mapOf("id" to archive.id.value, "phase" to archive.phase, "snapshot" to archive.snapshot,
            "digest" to archive.digest, "archivedAt" to archive.createTime, "files" to files)
    }

    @Transactional(noRollbackFor = [BadRequestException::class])
    fun list(bizType: String, bizId: String): List<Map<String, Any?>> = CollectionPermanentArchiveDao.find {
        (CollectionPermanentArchives.bizType eq bizType) and (CollectionPermanentArchives.bizId eq bizId)
    }.sortedBy { it.createTime }.map { archive ->
        OrgScopedReadPolicy.requireVisible(scope.readableOrgIds(), archive.orgId, "永久档案")
        verifiedArchive(archive, bizId, setOf(archive.orgId))
    }
}

/** 仅依赖归档表，避免文件服务与业务服务形成循环依赖。 */
@Component
class CollectionArchiveFileRetentionGuard : FileRetentionGuard {
    override fun requireDeletable(fileId: String) {
        if (CollectionPermanentArchiveFileDao.find { CollectionPermanentArchiveFiles.fileId eq fileId }.any()) {
            throw BadRequestException("该文件属于藏品永久档案，不能删除")
        }
    }
    override fun requireUnlinkable(fileId: String, contentId: String) {
        if (CollectionPermanentArchiveFileDao.find {
            (CollectionPermanentArchiveFiles.fileId eq fileId) and
                ((CollectionPermanentArchiveFiles.bizId eq contentId) or (CollectionPermanentArchiveFiles.archiveId eq contentId))
        }.any()) throw BadRequestException("该关联属于藏品永久档案，不能移除")
    }
}
