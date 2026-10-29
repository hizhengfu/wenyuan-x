package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.file.EnumFileStorageType
import io.hizhengfu.cloud.common.file.FileContentBaseline
import io.hizhengfu.cloud.common.file.FileReadPath
import io.hizhengfu.cloud.common.file.LocalFileObjectStorage
import io.hizhengfu.cloud.common.utils.SecurityUtils
import io.hizhengfu.cloud.common.utils.IdGenerate
import io.hizhengfu.cloud.sys.file.dao.FileDao
import io.hizhengfu.cloud.sys.file.dao.Files
import org.jetbrains.exposed.v1.core.eq
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

data class CollectionPermanentOriginalRecoveryRequest(
    val fileId: String,
    val sourceInventorySha256: String,
    val databaseBackupSha256: String,
    val fileBackupSha256: String,
    val restoreReportSha256: String,
    val verificationReport: String,
    val reason: String,
)

/** 仅在独立核对备份和恢复报告后，由管理员逐文件登记新存储身份。 */
@Component
class CollectionPermanentOriginalRecoveryService(
    private val storage: LocalFileObjectStorage,
    private val actors: CollectionActorDirectory,
) {
    @Transactional
    fun record(request: CollectionPermanentOriginalRecoveryRequest): String {
        val actor = SecurityUtils.getUserDetail()
        if (SecurityContextHolder.getContext().authentication?.authorities?.none { it.authority == "ADMIN" } != false)
            throw BadRequestException("仅系统管理员可登记恢复原件身份承接")
        if ("ADMIN" !in actors.requireActive(actor.id).roleCodes)
            throw BadRequestException("当前账号已不具备系统管理员职责")
        val digests = listOf(request.sourceInventorySha256, request.databaseBackupSha256,
            request.fileBackupSha256, request.restoreReportSha256)
        if (!request.fileId.matches(Regex("[A-Za-z0-9-]{1,64}")) ||
            digests.any { !it.matches(Regex("[0-9a-f]{64}")) } ||
            request.reason.trim().length !in 20..2000)
            throw BadRequestException("恢复原件编号、备份核验摘要或说明格式无效")

        val file = FileDao.find { Files.id eq request.fileId }.forUpdate().singleOrNull()
            ?: throw BadRequestException("恢复原件文件登记不存在")
        if (file.storageType != EnumFileStorageType.LOCAL) throw BadRequestException("此入口仅承接本地原件")
        val references = CollectionPermanentArchiveFileDao.find {
            CollectionPermanentArchiveFiles.fileId eq request.fileId
        }.toList()
        if (references.isEmpty()) throw BadRequestException("恢复原件没有永久档案引用")
        val orgs = linkedSetOf<String>()
        val nativeKeys = linkedSetOf<String>()
        val baselines = linkedSetOf<FileContentBaseline>()
        for (reference in references) {
            val archive = CollectionPermanentArchiveDao.findById(reference.archiveId)
                ?: throw BadRequestException("恢复原件永久档案头缺失")
            if (archive.digest != CollectionCareEvidence.digest(archive.snapshot) ||
                reference.orgId != archive.orgId || reference.bizId != archive.bizId)
                throw BadRequestException("恢复原件档案或引用摘要不一致")
            val metadata = try { JsonUtils.mapper.readTree(reference.metadata) }
                catch (_: tools.jackson.core.JacksonException) { throw BadRequestException("恢复原件冻结元数据无法解析") }
            val frozen = CollectionArchiveFileIdentity.keys(CollectionArchiveFileReference(
                archive.id.value, archive.orgId, archive.bizType, archive.bizId, archive.phase,
                reference.fileId, reference.metadata), metadata)
            if (metadata.path("path").asString() != file.path ||
                metadata.path("storageType").asString() != EnumFileStorageType.LOCAL.name)
                throw BadRequestException("恢复原件当前路径或存储类型与冻结档案不一致")
            orgs += archive.orgId
            nativeKeys += frozen.filter { it.startsWith("n") }
            baselines += CollectionArchiveFileIdentity.content(metadata)
                ?: throw BadRequestException("恢复原件缺少封存时的内容基线")
        }
        if (orgs.isEmpty() || nativeKeys.size != 1 || baselines.size != 1)
            throw BadRequestException("共享原件的冻结身份或内容基线不一致，不能承接")
        CollectionPermanentOriginalRecoveryReport.requireVerified(request.verificationReport,
            request.sourceInventorySha256, request.restoreReportSha256, references.map { it.id.value }.toSet())

        val before = storage.readIdentity(file.path)
        if (before.nativeIdentity == null || before.stableIdentity == null)
            throw BadRequestException("恢复原件当前文件缺少可核验的本机身份")
        val observed = FileReadPath.keys(file.path, "LOCAL", storage)
        val currentNative = observed.singleOrNull { it.startsWith("n") }
            ?: throw BadRequestException("恢复原件当前原生身份不唯一")
        val currentStable = observed.singleOrNull { it.startsWith("u") }
            ?: throw BadRequestException("恢复原件当前稳定身份不唯一")
        if (currentNative == nativeKeys.single())
            throw BadRequestException("原件冻结身份仍有效，无需恢复承接")
        val baseline = baselines.single()
        storage.openStream(file.path).use { baseline.requireMatch(FileContentBaseline.read(it, maximumBytes = baseline.size)) }
        if (storage.readIdentity(file.path) != before)
            throw BadRequestException("恢复原件核验期间文件身份发生变化")

        val proposed = CollectionPermanentOriginalRecoveryRegistry.Evidence(
            request.fileId, orgs.sorted(), nativeKeys.single(), currentNative, currentStable,
            baseline.sha256, baseline.size, request.sourceInventorySha256,
            request.databaseBackupSha256, request.fileBackupSha256, request.restoreReportSha256,
            request.reason.trim(), actor.id, "",
        )
        val sealed = proposed.copy(digest = CollectionPermanentOriginalRecoveryRegistry.seal(proposed))
        if ("ADMIN" !in actors.requireActive(actor.id).roleCodes)
            throw BadRequestException("当前账号已不具备系统管理员职责")
        val existing = CollectionPermanentOriginalRecoveryDao.find {
            CollectionPermanentOriginalRecoveries.fileId eq request.fileId
        }.toList()
        val previous = CollectionPermanentOriginalRecoveryRegistry.one(request.fileId)
        if (existing.isNotEmpty() && previous == null)
            throw BadRequestException("已有恢复承接凭据无效，请先核查")
        if (previous == sealed) return request.fileId
        CollectionPermanentOriginalRecoveryDao.new(IdGenerate.nextId()) {
            orgId = sealed.orgIds.first()
            fileId = sealed.fileId
            orgIds = sealed.orgIds.joinToString(",")
            frozenNativeKey = sealed.frozenNativeKey
            restoredNativeKey = sealed.restoredNativeKey
            restoredStableKey = sealed.restoredStableKey
            contentSha256 = sealed.contentSha256
            contentSize = sealed.contentSize
            sourceInventorySha256 = sealed.sourceInventorySha256
            databaseBackupSha256 = sealed.databaseBackupSha256
            fileBackupSha256 = sealed.fileBackupSha256
            restoreReportSha256 = sealed.restoreReportSha256
            reason = sealed.reason
            createBy = sealed.assessor
            evidenceDigest = sealed.digest
        }
        return request.fileId
    }

}
