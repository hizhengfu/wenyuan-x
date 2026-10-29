package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.dao.CollectionPermanentOriginalTransitionDao
import io.hizhengfu.cloud.collection.dao.CollectionPermanentOriginalTransitions
import io.hizhengfu.cloud.common.domain.EnumContentStatus
import io.hizhengfu.cloud.common.file.FileContentBaseline
import org.jetbrains.exposed.v1.jdbc.exists

/** 只接受逐文件、已留痕的旧设备号承接；从不改写原永久档案里的冻结键。 */
object CollectionPermanentOriginalTransitionRegistry {
    data class Evidence(
        val fileId: String,
        val orgId: String,
        val frozenNativeKey: String,
        val stableKey: String,
        val contentSha256: String,
        val contentSize: Long,
        val formerDevice: String,
        val currentDevice: String,
        val inode: String,
        val birthMillis: Long,
        val reason: String,
        val assessor: String,
        val digest: String,
    ) {
        fun valid() = assessor.isNotBlank() && reason.isNotBlank() &&
            frozenNativeKey.matches(Regex("n[0-9a-f]{63}")) && stableKey.matches(Regex("u[0-9a-f]{63}")) &&
            digest == seal(this)
    }

    fun seal(value: Evidence): String = CollectionCareEvidence.digest(JsonUtils.toJson(listOf(
        value.fileId, value.orgId, value.frozenNativeKey, value.stableKey,
        value.contentSha256, value.contentSize, value.formerDevice, value.currentDevice,
        value.inode, value.birthMillis, value.reason, value.assessor,
    ))!!)

    private fun capture(row: CollectionPermanentOriginalTransitionDao): Evidence? {
        if (row.status != EnumContentStatus.NORMAL) return null
        return Evidence(row.fileId, row.orgId, row.frozenNativeKey, row.stableKey,
            row.contentSha256, row.contentSize, row.formerDevice, row.currentDevice,
            row.inode, row.birthMillis, row.reason, row.createBy.orEmpty(), row.evidenceDigest)
            .takeIf(Evidence::valid)
    }

    fun one(fileId: String): Evidence? = if (CollectionPermanentOriginalTransitions.exists())
        CollectionPermanentOriginalTransitionDao.findById(fileId)?.let(::capture) else null

    /** 部署扫描在打开历史引用游标前读取承接表，避免游标内重入数据库查询。 */
    fun all(): Map<String, Evidence> = if (CollectionPermanentOriginalTransitions.exists())
        CollectionPermanentOriginalTransitionDao.all().mapNotNull { row -> capture(row)?.let { it.fileId to it } }.toMap()
    else emptyMap()

    fun accepts(evidence: Evidence?, fileId: String, orgId: String, frozen: Set<String>,
                current: Set<String>, baseline: FileContentBaseline?): Boolean =
        evidence != null && evidence.fileId == fileId && evidence.orgId == orgId &&
            evidence.frozenNativeKey in frozen && evidence.stableKey in current &&
            baseline != null && baseline.sha256 == evidence.contentSha256 && baseline.size == evidence.contentSize
}
