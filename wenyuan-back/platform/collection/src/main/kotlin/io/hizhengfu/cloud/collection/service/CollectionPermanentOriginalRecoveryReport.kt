package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.dao.CollectionPermanentArchiveDao
import io.hizhengfu.cloud.collection.dao.CollectionPermanentArchiveFileDao
import io.hizhengfu.cloud.common.exception.BadRequestException
import java.security.MessageDigest
import java.util.HexFormat

/** 报告只证明提交材料内部一致；来源清单和备份摘要仍须由馆方独立核实。 */
internal object CollectionPermanentOriginalRecoveryReport {
    fun requireVerified(raw: String, sourceInventorySha256: String, restoreReportSha256: String,
                        referenceIds: Set<String>) {
        if (raw.length !in 1..2_000_000 ||
            HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))) != restoreReportSha256)
            throw BadRequestException("恢复核验报告缺失、超限或摘要不符")
        val report = try { JsonUtils.mapper.readTree(raw) }
            catch (_: tools.jackson.core.JacksonException) { throw BadRequestException("恢复核验报告无法解析") }
        val count = report.path("expectedReferences").longValue()
        val results = report.path("results")
        val restoredReferences = report.path("restoredReferences")
        val restoredArchives = report.path("restoredArchives")
        val archiveCount = restoredArchives.path("expectedArchives").longValue()
        if (report.path("format").asString() != "collection-restored-content-check-v3" ||
            report.path("result").asString() != "ALL_CONTENT_REFERENCES_AND_ARCHIVES_MATCH" ||
            report.path("inventorySha256").asString() != sourceInventorySha256 ||
            count <= 0 || count != CollectionPermanentArchiveFileDao.count() ||
            count != results.size().toLong() ||
            report.path("counts").path("CONTENT_MATCH").longValue() != count ||
            restoredReferences.path("result").asString() != "MATCH" ||
            restoredReferences.path("independentReferenceCount").longValue() != count ||
            restoredReferences.path("counts").path("REFERENCE_MATCH").longValue() != count ||
            restoredArchives.path("result").asString() != "MATCH" || archiveCount <= 0 ||
            archiveCount != CollectionPermanentArchiveDao.count() ||
            restoredArchives.path("independentRestoredArchiveCount").longValue() != archiveCount ||
            restoredArchives.path("counts").path("ARCHIVE_MATCH").longValue() != archiveCount ||
            restoredArchives.path("sourceReferenceLinks").path("result").asString() != "MATCH" ||
            restoredArchives.path("restoredReferenceLinks").path("result").asString() != "MATCH")
            throw BadRequestException("恢复核验报告未证明全档案、引用与原件内容一致")
        val seen = hashSetOf<String>()
        for (entry in results) {
            val id = entry.path("referenceId").asString()
            if (!id.matches(Regex("[A-Za-z0-9-]{1,64}")) || !seen.add(id) ||
                entry.path("status").asString() != "CONTENT_MATCH")
                throw BadRequestException("恢复核验报告含重复或未匹配的原件引用")
        }
        if (!seen.containsAll(referenceIds))
            throw BadRequestException("恢复核验报告未覆盖该文件全部永久档案引用")
    }
}
