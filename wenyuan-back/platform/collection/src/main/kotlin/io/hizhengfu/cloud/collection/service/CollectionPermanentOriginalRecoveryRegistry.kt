package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.dao.CollectionPermanentOriginalRecoveries
import io.hizhengfu.cloud.collection.dao.CollectionPermanentOriginalRecoveryDao
import io.hizhengfu.cloud.common.domain.EnumContentStatus
import io.hizhengfu.cloud.common.file.FileContentBaseline
import org.jetbrains.exposed.v1.jdbc.exists
import org.jetbrains.exposed.v1.core.eq

/** 新文件系统上的原件仍须逐次匹配封存内容、原冻结身份和留痕的新原生身份。 */
object CollectionPermanentOriginalRecoveryRegistry {
    data class Evidence(
        val fileId: String,
        val orgIds: List<String>,
        val frozenNativeKey: String,
        val restoredNativeKey: String,
        val restoredStableKey: String,
        val contentSha256: String,
        val contentSize: Long,
        val sourceInventorySha256: String,
        val databaseBackupSha256: String,
        val fileBackupSha256: String,
        val restoreReportSha256: String,
        val reason: String,
        val assessor: String,
        val digest: String,
    ) {
        fun valid() = fileId.isNotBlank() && orgIds.isNotEmpty() && orgIds == orgIds.distinct().sorted() &&
            orgIds.all { it.isNotBlank() } && assessor.isNotBlank() && reason.isNotBlank() &&
            frozenNativeKey.matches(Regex("n[0-9a-f]{63}")) &&
            restoredNativeKey.matches(Regex("n[0-9a-f]{63}")) &&
            restoredStableKey.matches(Regex("u[0-9a-f]{63}")) &&
            listOf(contentSha256, sourceInventorySha256, databaseBackupSha256, fileBackupSha256,
                restoreReportSha256).all { it.matches(Regex("[0-9a-f]{64}")) } &&
            contentSize >= 0 && frozenNativeKey != restoredNativeKey && digest == seal(this)
    }

    fun seal(value: Evidence): String = CollectionCareEvidence.digest(JsonUtils.toJson(listOf(
        value.fileId, value.orgIds, value.frozenNativeKey, value.restoredNativeKey,
        value.restoredStableKey, value.contentSha256, value.contentSize,
        value.sourceInventorySha256, value.databaseBackupSha256, value.fileBackupSha256,
        value.restoreReportSha256, value.reason, value.assessor,
    ))!!)

    private fun capture(row: CollectionPermanentOriginalRecoveryDao): Evidence? {
        if (row.status != EnumContentStatus.NORMAL) return null
        val orgs = row.orgIds.split(',').filter(String::isNotBlank)
        if (orgs.isEmpty() || row.orgIds != orgs.joinToString(",") || row.orgId != orgs.first()) return null
        return Evidence(row.fileId, orgs, row.frozenNativeKey, row.restoredNativeKey,
            row.restoredStableKey, row.contentSha256, row.contentSize, row.sourceInventorySha256,
            row.databaseBackupSha256, row.fileBackupSha256, row.restoreReportSha256,
            row.reason, row.createBy.orEmpty(), row.evidenceDigest).takeIf(Evidence::valid)
    }

    fun one(fileId: String): Evidence? = if (CollectionPermanentOriginalRecoveries.exists())
        CollectionPermanentOriginalRecoveryDao.find { CollectionPermanentOriginalRecoveries.fileId eq fileId }
            .maxWithOrNull(compareBy({ it.createTime }, { it.id.value }))?.let(::capture) else null

    fun all(): Map<String, Evidence> = if (CollectionPermanentOriginalRecoveries.exists())
        CollectionPermanentOriginalRecoveryDao.all().groupBy { it.fileId }.mapNotNull { (fileId, rows) ->
            rows.maxWithOrNull(compareBy({ it.createTime }, { it.id.value }))?.let(::capture)?.let { fileId to it }
        }.toMap()
    else emptyMap()

    fun accepts(evidence: Evidence?, fileId: String, orgId: String, frozen: Set<String>,
                observed: Set<String>, baseline: FileContentBaseline?): Boolean =
        evidence != null && evidence.fileId == fileId && orgId in evidence.orgIds &&
            evidence.frozenNativeKey in frozen && evidence.restoredNativeKey in observed &&
            evidence.restoredStableKey in observed && baseline != null &&
            baseline.sha256 == evidence.contentSha256 && baseline.size == evidence.contentSize
}
