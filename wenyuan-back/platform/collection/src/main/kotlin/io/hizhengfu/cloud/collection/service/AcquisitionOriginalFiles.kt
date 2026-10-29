package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.common.domain.FILE_PREFIX
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.file.FileReadPath
import io.hizhengfu.cloud.common.file.LocalFileObjectStorage
import io.hizhengfu.cloud.common.file.FileMetadataLocation
import io.hizhengfu.cloud.sys.file.dao.FileContents
import io.hizhengfu.cloud.sys.file.dao.FileDao
import io.hizhengfu.cloud.sys.file.dao.Files
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** 原件绑定身份独立留存，不代表专家意见、协议或实际取得已经完成。 */
@Component
@Transactional
class AcquisitionOriginalFiles(
    private val archives: CollectionPermanentArchiveService,
    private val localStorage: LocalFileObjectStorage? = null,
) {
    companion object { const val IDENTITY_PHASE = "FILE_ACCESS_" }
    data class Reference(val orgId: String, val acquisitionId: String, val file: String)

    private fun refs(org: String, id: String, values: List<String?>) = values.flatMap {
        BusinessAttachmentBindingPlanner.plan(id, "征集原件", it).map { file -> Reference(org, id, file.fileId) }
    }

    /** 包括尚未提交或永久归档的材料；不以是否审批通过决定保密性。 */
    private fun current(id: String? = null): List<Reference> = buildList {
        val acquisitions = Acquisitions.select(Acquisitions.id, Acquisitions.orgId, Acquisitions.images,
            Acquisitions.expertOpinionAttachmentIds, Acquisitions.approvalAttachmentIds,
            Acquisitions.contractAttachmentIds, Acquisitions.invoiceAttachmentIds)
        if (id != null) acquisitions.andWhere { Acquisitions.id eq id }
        acquisitions.forEach { r -> addAll(refs(r[Acquisitions.orgId], r[Acquisitions.id].value, listOf(
            r[Acquisitions.images], r[Acquisitions.expertOpinionAttachmentIds], r[Acquisitions.approvalAttachmentIds],
            r[Acquisitions.contractAttachmentIds], r[Acquisitions.invoiceAttachmentIds]))) }
        val cases = AcquisitionComplianceCaseDao.find { if (id == null) Op.TRUE else AcquisitionComplianceCases.acquisitionId eq id }
        cases.forEach { c ->
            val d = c.dossier; val v = c.completion
            addAll(refs(c.orgId, c.acquisitionId, listOf(d.standardsFileIds, d.authenticityValueFileIds,
                d.sourceInvestigationFileIds, d.conflictDisclosureFileIds, d.intentionFileIds,
                v?.collectiveDecisionFileIds, v?.agreementFileIds, v?.handoverReceiptFileIds,
                v?.comparablePriceFileIds, v?.highValueDeterminationFileIds, v?.assetAssessmentFileIds,
                v?.negotiationFileIds, v?.paymentFileIds)))
        }
        AcquisitionExpertOpinionDao.find { if (id == null) Op.TRUE else AcquisitionExpertOpinions.acquisitionId eq id }.forEach { e ->
            addAll(refs(e.orgId, e.acquisitionId, listOf(e.content.qualificationFileIds, e.content.independenceFileIds, e.content.signedOpinionFileIds)))
        }
        val attachments = Acquisitions.join(FileContents, JoinType.INNER, Acquisitions.id, FileContents.contentId)
            .select(Acquisitions.orgId, Acquisitions.id, FileContents.fileId)
        if (id != null) attachments.andWhere { Acquisitions.id eq id }
        attachments.forEach { add(Reference(it[Acquisitions.orgId], it[Acquisitions.id].value, it[FileContents.fileId])) }
    }.distinct()

    private fun file(reference: String): FileDao? = FileDao.findById(reference)
        ?: FileDao.find { Files.path eq reference }.singleOrNull()

    /** 已不存在的旧路径仍保留词法身份；有实际目标时同时核对符号链接、大小写和硬链接。 */
    private fun keys(path: String, storage: String?): Set<String> = try {
        FileReadPath.keys(path, storage, localStorage)
    } catch (_: BadRequestException) { setOf(FileReadPath.key(path, storage)) }
      catch (_: java.io.IOException) { setOf(FileReadPath.key(path, storage)) }

    /** 只读取变更前实际来源；子记录馆别不符时不能产生原件资格。 */
    internal fun existingReferences(id: String): List<Reference> {
        val entity=AcquisitionDao.findById(id) ?: throw BadRequestException("征集申请不存在")
        if(AcquisitionComplianceCaseDao.find { AcquisitionComplianceCases.acquisitionId eq id }.any { it.orgId!=entity.orgId } ||
            AcquisitionExpertOpinionDao.find { AcquisitionExpertOpinions.acquisitionId eq id }.any { it.orgId!=entity.orgId })
            throw BadRequestException("征集调查或专家原件的所属馆与申请不一致")
        return current(id).also { refs ->
            if(refs.any { it.orgId!=entity.orgId })throw BadRequestException("征集原件的所属馆与申请不一致")
        }
    }

    internal fun rememberExisting(id: String) = remember(id,existingSource=true)
    fun remember(id: String) = remember(id,existingSource=false)
    private fun remember(id: String,existingSource: Boolean) {
        existingReferences(id).forEach { ref ->
            val original = file(ref.file) ?: throw BadRequestException("征集原件缺失或路径不唯一，请核对后办理")
            val identity = archives.fileReadKeys(original)
            val phase = IDENTITY_PHASE + CollectionCareEvidence.digest("${original.id.value}|${original.path}|${original.storageType}|${identity.sorted()}").take(48)
            val snapshot=mapOf("fileId" to original.id.value, "path" to original.path, "storageType" to original.storageType.name,
                "readKeys" to identity.sorted(), "purpose" to "仅留存征集原件读取身份，不代表业务完成或实际签署")
            if(existingSource) archives.sealExistingAcquisitionOriginal(ref,phase,snapshot)
            else archives.seal(ref.orgId,CollectionConst.BIZ_ACQUISITION,id,phase,snapshot,original.id.value)
        }
    }

    @Transactional(noRollbackFor = [BadRequestException::class])
    fun organizations(path: String, storageType: String?): Set<String> {
        val locationKeys = mutableMapOf<Pair<String, String?>, Set<String>>()
        fun readKeys(location: String, type: String?) = locationKeys.getOrPut(location to type) { keys(location, type) }
        val current = current()
        val a = CollectionPermanentArchives; val f = CollectionPermanentArchiveFiles
        val files = CollectionOriginalFileLocations.find(current.map { it.file })
        val requestKeys = readKeys(path, storageType)
        val orgs = linkedSetOf<String>()
        current.forEach { ref ->
            val file = files[ref.file]
            val matches = if (file == null) readKeys(ref.file, null).any(requestKeys::contains)
                else readKeys(file.path, file.storageType).any(requestKeys::contains)
            if (matches) orgs += ref.orgId
        }
        // 冻结位置与当前文件位置在同一查询中取得，缺失当前行仍保留历史保护。
        a.join(f, JoinType.INNER, a.id, f.archiveId).join(Files, JoinType.LEFT, f.fileId, Files.id)
            .select(a.orgId, a.bizId, a.phase, a.snapshot, a.digest,
                f.orgId, f.bizId, f.fileId, f.metadata, Files.path, Files.storageType)
            .where { a.bizType eq CollectionConst.BIZ_ACQUISITION }.forEach { r ->
            val metadata = try { JsonUtils.mapper.readTree(r[f.metadata]) }
                catch (_: tools.jackson.core.JacksonException) { throw BadRequestException("征集永久原件的文件身份无法核对") }
            val originalPath = metadata.path("path").asString()
            val originalStorage = metadata.path("storageType").asString()
            val currentPath = r.getOrNull(Files.path)
            val currentStorage = r.getOrNull(Files.storageType)?.name
            val savedKeys: Set<String> = if (r[a.phase].startsWith(IDENTITY_PHASE)) {
                if (r[a.digest] != CollectionCareEvidence.digest(r[a.snapshot])) throw BadRequestException("征集原件留存身份摘要不一致")
                try {
                    val identity = JsonUtils.mapper.readTree(r[a.snapshot])
                    if (identity.path("fileId").asString() != r[f.fileId] || identity.path("path").asString() != originalPath || identity.path("storageType").asString() != originalStorage)
                        throw BadRequestException("征集原件留存身份与文件引用不一致")
                    identity.path("readKeys").mapNotNull { it.asString() }.toSet()
                }
                catch (_: tools.jackson.core.JacksonException) { throw BadRequestException("征集原件留存身份无法核对") }
            } else emptySet()
            val matches = savedKeys.any(requestKeys::contains) ||
                (originalPath.isNotBlank() && readKeys(originalPath, originalStorage).any(requestKeys::contains)) ||
                (currentPath != null && readKeys(currentPath, currentStorage).any(requestKeys::contains))
            if (matches) {
                if (r[a.orgId] != r[f.orgId] || r[a.bizId] != r[f.bizId]) throw BadRequestException("征集永久原件的归属不一致")
                orgs += r[a.orgId]
            }
        }
        return orgs
    }

    /** 历史目录按位置批量读取来源；单个错馆引用不影响其他合法材料，损坏索引仍拒绝。 */
    @Transactional(noRollbackFor = [BadRequestException::class])
    fun organizationsMany(locations: List<FileMetadataLocation>): Map<FileMetadataLocation, Result<Set<String>>> {
        if (locations.isEmpty()) return emptyMap()
        val orgs = locations.distinct().associateWith { linkedSetOf<String>() }
        val failures = mutableMapOf<FileMetadataLocation, BadRequestException>()
        try {
            val resolved = object : LinkedHashMap<Pair<String, String?>, Set<String>>(256, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<String, String?>, Set<String>>?) = size > 256
            }
            val files = object : LinkedHashMap<String, FileDao?>(512, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FileDao?>?) = size > 512
            }
            fun readKeys(path: String, type: String?) = resolved.getOrPut(path to type) { keys(path, type) }
            fun currentFile(id: String) = if (files.containsKey(id)) files[id] else file(id).also { files[id] = it }
            val requested = orgs.keys.associateWith { readKeys(it.path, it.storageType) }
            current().forEach { ref ->
                val f = currentFile(ref.file)
                val sourceKeys = readKeys(f?.path ?: ref.file, f?.storageType?.name)
                requested.forEach { (location, values) ->
                    if (sourceKeys.any(values::contains)) orgs.getValue(location) += ref.orgId
                }
            }
            val a = CollectionPermanentArchives; val f = CollectionPermanentArchiveFiles
            a.join(f, JoinType.INNER, a.id, f.archiveId).select(a.orgId, a.bizId, a.phase, a.snapshot, a.digest,
                f.orgId, f.bizId, f.fileId, f.metadata).where { a.bizType eq CollectionConst.BIZ_ACQUISITION }.forEach { r ->
                val metadata = try { JsonUtils.mapper.readTree(r[f.metadata]) }
                    catch (_: tools.jackson.core.JacksonException) { throw BadRequestException("征集永久原件的文件身份无法核对") }
                val path = metadata.path("path").asString()
                val type = metadata.path("storageType").asString()
                val current = currentFile(r[f.fileId])
                val savedKeys: Set<String> = if (r[a.phase].startsWith(IDENTITY_PHASE)) {
                    if (r[a.digest] != CollectionCareEvidence.digest(r[a.snapshot])) throw BadRequestException("征集原件留存身份摘要不一致")
                    val identity = try { JsonUtils.mapper.readTree(r[a.snapshot]) }
                        catch (_: tools.jackson.core.JacksonException) { throw BadRequestException("征集原件留存身份无法核对") }
                    if (identity.path("fileId").asString() != r[f.fileId] || identity.path("path").asString() != path || identity.path("storageType").asString() != type)
                        throw BadRequestException("征集原件留存身份与文件引用不一致")
                    identity.path("readKeys").mapNotNull { it.asString() }.toSet()
                } else emptySet()
                val frozen = if (path.isBlank()) emptySet() else readKeys(path, type)
                val currentKeys = if (current == null) emptySet() else readKeys(current.path, current.storageType.name)
                requested.forEach { (location, values) ->
                    if (savedKeys.any(values::contains) || frozen.any(values::contains) || currentKeys.any(values::contains)) {
                        if (r[a.orgId] != r[f.orgId] || r[a.bizId] != r[f.bizId])
                            failures[location] = BadRequestException("征集永久原件的归属不一致")
                        else orgs.getValue(location) += r[a.orgId]
                    }
                }
            }
        } catch (e: BadRequestException) {
            return orgs.mapValues { Result.failure(e) }
        }
        return orgs.mapValues { (location, values) ->
            failures[location]?.let { Result.failure(it) } ?: Result.success(values.toSet())
        }
    }
}
