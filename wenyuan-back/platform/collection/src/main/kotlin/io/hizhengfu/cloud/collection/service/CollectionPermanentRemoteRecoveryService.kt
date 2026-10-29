package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.file.EnumFileStorageType
import io.hizhengfu.cloud.common.file.FileContentBaseline
import io.hizhengfu.cloud.common.file.FileObjectRevision
import io.hizhengfu.cloud.common.utils.IdGenerate
import io.hizhengfu.cloud.common.utils.SecurityUtils
import io.hizhengfu.cloud.sys.file.dao.FileDao
import io.hizhengfu.cloud.sys.file.dao.Files
import io.hizhengfu.cloud.sys.file.service.storage.RustFsStorageClient
import org.jetbrains.exposed.v1.core.eq
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

data class CollectionPermanentRemoteRecoveryRequest(
    val fileId: String,
    val targetVersionId: String?,
    val targetETag: String?,
    val sourceInventorySha256: String,
    val databaseBackupSha256: String,
    val objectBackupSha256: String,
    val restoreReportSha256: String,
    val verificationReport: String,
    val reason: String,
)

/** 独立核对完整恢复报告后，逐文件承接目标桶中已定位的历史对象版本。 */
@Component
class CollectionPermanentRemoteRecoveryService(
    private val client: RustFsStorageClient,
    private val actors: CollectionActorDirectory,
) {
    @Transactional
    fun record(request: CollectionPermanentRemoteRecoveryRequest): String {
        val actor = SecurityUtils.getUserDetail()
        if (SecurityContextHolder.getContext().authentication?.authorities?.none { it.authority == "ADMIN" } != false ||
            "ADMIN" !in actors.requireActive(actor.id).roleCodes)
            throw BadRequestException("仅当前系统管理员可登记远端原件恢复承接")
        val digests = listOf(request.sourceInventorySha256, request.databaseBackupSha256,
            request.objectBackupSha256, request.restoreReportSha256)
        if (!request.fileId.matches(Regex("[A-Za-z0-9-]{1,64}")) ||
            digests.any { !it.matches(Regex("[0-9a-f]{64}")) } ||
            request.reason.trim().length !in 20..2000 ||
            request.targetVersionId?.length?.let { it !in 1..1024 } == true ||
            request.targetETag?.length?.let { it !in 1..1024 } == true)
            throw BadRequestException("远端恢复编号、版本、备份摘要或说明格式无效")
        val file = FileDao.find { Files.id eq request.fileId }.forUpdate().singleOrNull()
            ?: throw BadRequestException("远端恢复文件登记不存在")
        if (file.storageType != EnumFileStorageType.RUSTFS)
            throw BadRequestException("此入口仅承接远端永久档案原件")
        val references = CollectionPermanentArchiveFileDao.find {
            CollectionPermanentArchiveFiles.fileId eq request.fileId
        }.toList()
        if (references.isEmpty()) throw BadRequestException("远端恢复原件没有永久档案引用")
        val orgs = linkedSetOf<String>()
        val revisions = linkedSetOf<FileObjectRevision>()
        val baselines = linkedSetOf<FileContentBaseline>()
        for (reference in references) {
            val archive = CollectionPermanentArchiveDao.findById(reference.archiveId)
                ?: throw BadRequestException("远端恢复原件永久档案头缺失")
            if (archive.digest != CollectionCareEvidence.digest(archive.snapshot) ||
                reference.orgId != archive.orgId || reference.bizId != archive.bizId)
                throw BadRequestException("远端恢复原件档案或引用摘要不一致")
            val metadata = try { JsonUtils.mapper.readTree(reference.metadata) }
                catch (_: tools.jackson.core.JacksonException) { throw BadRequestException("远端恢复原件冻结元数据无法解析") }
            CollectionArchiveFileIdentity.keys(CollectionArchiveFileReference(
                archive.id.value, archive.orgId, archive.bizType, archive.bizId, archive.phase,
                reference.fileId, reference.metadata), metadata)
            if (metadata.path("path").asString() != file.path ||
                metadata.path("storageType").asString() != EnumFileStorageType.RUSTFS.name)
                throw BadRequestException("远端恢复对象键或存储类型与冻结档案不一致")
            orgs += archive.orgId
            revisions += CollectionArchiveFileIdentity.revision(metadata)
                ?: throw BadRequestException("远端恢复原件缺少冻结版本")
            baselines += CollectionArchiveFileIdentity.content(metadata)
                ?: throw BadRequestException("远端恢复原件缺少封存内容基线")
        }
        if (orgs.isEmpty() || revisions.isEmpty() || baselines.size != 1)
            throw BadRequestException("共享原件的冻结版本或内容基线不一致，不能承接")
        CollectionPermanentOriginalRecoveryReport.requireVerified(request.verificationReport,
            request.sourceInventorySha256, request.restoreReportSha256, references.map { it.id.value }.toSet())

        val target = FileObjectRevision(client.currentStorageIdentity(), request.targetVersionId, request.targetETag)
        if (target in revisions) throw BadRequestException("远端冻结版本仍有效，无需恢复承接")
        val baseline = baselines.single()
        val key = CollectionPermanentRemoteRecoveryRegistry.objectKey(file.path)
            ?: throw BadRequestException("远端恢复对象键无效")
        val before = client.metadata(key, target)
        if (before.size != baseline.size)
            throw BadRequestException("远端恢复版本大小与冻结基线不一致")
        client.getInputStream(key, revision = target).use {
            baseline.requireMatch(FileContentBaseline.read(it, maximumBytes = baseline.size))
        }
        val after = client.metadata(key, target)
        if (before.size != after.size || before.revision != after.revision)
            throw BadRequestException("远端恢复版本核验期间发生变化")

        val proposed = CollectionPermanentRemoteRecoveryRegistry.Evidence(
            request.fileId, orgs.sorted(), file.path,
            revisions.sortedWith(CollectionPermanentRemoteRecoveryRegistry.REVISION_ORDER), target,
            baseline.sha256, baseline.size, request.sourceInventorySha256,
            request.databaseBackupSha256, request.objectBackupSha256, request.restoreReportSha256,
            request.reason.trim(), actor.id, "",
        )
        val sealed = proposed.copy(digest = CollectionPermanentRemoteRecoveryRegistry.seal(proposed))
        if ("ADMIN" !in actors.requireActive(actor.id).roleCodes)
            throw BadRequestException("当前账号已不具备系统管理员职责")
        val previous = CollectionPermanentRemoteRecoveryRegistry.one(request.fileId)
        if (previous == sealed) return request.fileId
        CollectionPermanentRemoteRecoveryDao.new(IdGenerate.nextId()) {
            orgId = sealed.orgIds.first()
            fileId = sealed.fileId
            orgIds = sealed.orgIds.joinToString(",")
            frozenPath = sealed.frozenPath
            frozenRevisions = JsonUtils.toJson(sealed.frozenRevisions.map {
                listOf(it.storageIdentity, it.versionId, it.eTag)
            })!!
            restoredStorageIdentity = sealed.restoredRevision.storageIdentity
            restoredVersionId = sealed.restoredRevision.versionId
            restoredETag = sealed.restoredRevision.eTag
            contentSha256 = sealed.contentSha256
            contentSize = sealed.contentSize
            sourceInventorySha256 = sealed.sourceInventorySha256
            databaseBackupSha256 = sealed.databaseBackupSha256
            objectBackupSha256 = sealed.objectBackupSha256
            restoreReportSha256 = sealed.restoreReportSha256
            reason = sealed.reason
            createBy = sealed.assessor
            evidenceDigest = sealed.digest
        }
        return request.fileId
    }
}
