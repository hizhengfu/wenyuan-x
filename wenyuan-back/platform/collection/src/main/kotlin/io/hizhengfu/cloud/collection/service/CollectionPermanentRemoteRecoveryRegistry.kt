package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.dao.CollectionPermanentRemoteRecoveries
import io.hizhengfu.cloud.collection.dao.CollectionPermanentRemoteRecoveryDao
import io.hizhengfu.cloud.common.domain.EnumContentStatus
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.file.EnumFileStorageType
import io.hizhengfu.cloud.common.file.FileContentBaseline
import io.hizhengfu.cloud.common.file.FileObjectRevision
import io.hizhengfu.cloud.common.file.FileReadPath
import io.hizhengfu.cloud.sys.file.dao.FileDao
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.exists

/** 只承接当前登记仍指向同一对象键、内容基线与原留存版本的远端文件。 */
object CollectionPermanentRemoteRecoveryRegistry {
    fun objectKey(path: String): String? {
        if (path.startsWith("http://") || path.startsWith("https://") || '\\' in path) return null
        val key = FileReadPath.relative(path).removePrefix("rustfs/")
        return key.takeIf { it.isNotBlank() && it.split('/').all { part ->
            part.isNotEmpty() && part != "." && part != ".." && part.none(Char::isISOControl)
        } }
    }

    data class Evidence(
        val fileId: String,
        val orgIds: List<String>,
        val frozenPath: String,
        val frozenRevisions: List<FileObjectRevision>,
        val restoredRevision: FileObjectRevision,
        val contentSha256: String,
        val contentSize: Long,
        val sourceInventorySha256: String,
        val databaseBackupSha256: String,
        val objectBackupSha256: String,
        val restoreReportSha256: String,
        val reason: String,
        val assessor: String,
        val digest: String,
    ) {
        fun valid() = fileId.isNotBlank() && orgIds.isNotEmpty() && orgIds == orgIds.distinct().sorted() &&
            orgIds.all(String::isNotBlank) && objectKey(frozenPath) != null &&
            assessor.isNotBlank() && reason.isNotBlank() && contentSize >= 0 &&
            frozenRevisions.isNotEmpty() && frozenRevisions == frozenRevisions.distinct().sortedWith(REVISION_ORDER) &&
            restoredRevision !in frozenRevisions &&
            listOf(contentSha256, sourceInventorySha256, databaseBackupSha256, objectBackupSha256,
                restoreReportSha256).all { it.matches(Regex("[0-9a-f]{64}")) } && digest == seal(this)
    }

    fun seal(value: Evidence): String = CollectionCareEvidence.digest(JsonUtils.toJson(listOf(
        value.fileId, value.orgIds, value.frozenPath,
        value.frozenRevisions.map { listOf(it.storageIdentity, it.versionId, it.eTag) },
        value.restoredRevision.storageIdentity, value.restoredRevision.versionId, value.restoredRevision.eTag,
        value.contentSha256, value.contentSize, value.sourceInventorySha256,
        value.databaseBackupSha256, value.objectBackupSha256, value.restoreReportSha256,
        value.reason, value.assessor,
    ))!!)

    private fun capture(row: CollectionPermanentRemoteRecoveryDao): Evidence? {
        if (row.status != EnumContentStatus.NORMAL) return null
        val orgs = row.orgIds.split(',').filter(String::isNotBlank)
        if (orgs.isEmpty() || row.orgIds != orgs.joinToString(",") || row.orgId != orgs.first()) return null
        return try {
            val node = JsonUtils.mapper.readTree(row.frozenRevisions)
            if (!node.isArray) return null
            val frozen = (0 until node.size()).map { index ->
                val fields = node[index]
                if (!fields.isArray || fields.size() != 3 || !fields[0].isString ||
                    !(fields[1].isNull || fields[1].isString) || !(fields[2].isNull || fields[2].isString)) return null
                FileObjectRevision(fields[0].asString(), fields[1].takeUnless { it.isNull }?.asString(),
                    fields[2].takeUnless { it.isNull }?.asString())
            }
            Evidence(row.fileId, orgs, row.frozenPath,
                frozen,
                FileObjectRevision(row.restoredStorageIdentity, row.restoredVersionId, row.restoredETag),
                row.contentSha256, row.contentSize, row.sourceInventorySha256,
                row.databaseBackupSha256, row.objectBackupSha256, row.restoreReportSha256,
                row.reason, row.createBy.orEmpty(), row.evidenceDigest).takeIf(Evidence::valid)
        } catch (_: BadRequestException) { null }
        catch (_: tools.jackson.core.JacksonException) { null }
    }

    fun one(fileId: String): Evidence? {
        if (!CollectionPermanentRemoteRecoveries.exists()) return null
        val latest = CollectionPermanentRemoteRecoveryDao.find { CollectionPermanentRemoteRecoveries.fileId eq fileId }
            .maxWithOrNull(compareBy({ it.createTime }, { it.id.value })) ?: return null
        return capture(latest) ?: throw BadRequestException("最新远端原件承接凭据无效，请先核查")
    }

    fun effective(fileId: String, orgId: String, path: String, frozen: FileObjectRevision?,
                  baseline: FileContentBaseline?): FileObjectRevision? {
        if (frozen == null || baseline == null) return frozen
        val evidence = one(fileId) ?: return frozen
        val current = FileDao.findById(fileId) ?: return frozen
        return if (evidence.fileId == fileId && orgId in evidence.orgIds &&
            evidence.frozenPath == path && current.path == path &&
            current.storageType == EnumFileStorageType.RUSTFS &&
            frozen in evidence.frozenRevisions && evidence.contentSha256 == baseline.sha256 &&
            evidence.contentSize == baseline.size) evidence.restoredRevision else frozen
    }

    val REVISION_ORDER: Comparator<FileObjectRevision> = compareBy(
        { it.storageIdentity }, { it.versionId.orEmpty() }, { it.eTag.orEmpty() })
}
