package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq

/** 独立于可变定级行的版本凭据；旧阶段不重封，新阶段固定当时已存在的版本及行政原件。 */
internal class CollectionGradingHistoryEvidence(private val archives: CollectionPermanentArchiveService) {
    companion object {
        const val BIZ = "COLLECTION_GRADING_HISTORY"
        private val phases = setOf("APPLICATION", "PROVINCIAL_RESULT", "NATIONAL_FILING")
    }
    data class Original(val archiveId: String, val digest: String)
    data class Entry(val gradingId: String, val version: Int, val application: Original,
        val provincialResult: Original? = null, val nationalFiling: Original? = null)
    data class Snapshot(val formatVersion: Int, val orgId: String, val collectionId: String, val totalRegNo: String,
        val phase: String, val gradingId: String, val entryCount: Int, val entries: List<Entry>)

    private fun key(collection: CollectionDao) = CollectionCareEvidence.digest(JsonUtils.toJson(listOf(collection.orgId, collection.id.value))!!)
    private fun original(row: CollectionGradingDao, phase: String): CollectionPermanentArchiveDao = CollectionPermanentArchiveDao.find {
        (CollectionPermanentArchives.bizType eq CollectionGradingService.BIZ) and
            (CollectionPermanentArchives.bizId eq row.id.value) and (CollectionPermanentArchives.phase eq phase)
    }.singleOrNull() ?: fail("定级版本缺少唯一行政原件：$phase")
    private fun reference(row: CollectionGradingDao, phase: String) = original(row, phase).let { Original(it.id.value, it.digest) }

    fun versioned(row: CollectionGradingDao): Boolean {
        val node = try { JsonUtils.mapper.readTree(original(row, "APPLICATION").snapshot) }
            catch (_: tools.jackson.core.JacksonException) { fail("定级申请原件无法解析") }
        val format = node?.get("formatVersion") ?: return false
        if (!format.isIntegralNumber || !format.canConvertToInt() || format.intValue() != 2) fail("定级申请原件格式无法核实")
        return true
    }
    fun application(row: CollectionGradingDao, versioned: Boolean): Map<String, Any> = buildMap {
        put("collectionId", row.collectionId); put("totalRegNo", row.totalRegNo); put("application", row.application)
        if (versioned) { put("formatVersion", 2); put("version", row.version) }
    }
    private fun entry(row: CollectionGradingDao, result: Boolean, national: Boolean): Entry {
        if (result && row.result == null || national && row.nationalFiling == null) fail("已固定的行政结果不能清除或回退")
        return Entry(row.id.value, row.version, reference(row, "APPLICATION"),
            if (result) reference(row, "PROVINCIAL_RESULT") else null,
            if (national) reference(row, "NATIONAL_FILING") else null)
    }
    private fun snapshot(collection: CollectionDao, phase: String, gradingId: String, entries: List<Entry>) =
        Snapshot(1, collection.orgId, collection.id.value, collection.totalRegNo, phase, gradingId, entries.size, entries)

    fun capture(collection: CollectionDao, subject: CollectionGradingDao, phase: String, rows: List<CollectionGradingDao>) {
        if (phase !in phases) fail("未知定级版本阶段")
        val saved = snapshot(collection, phase, subject.id.value, rows.map { entry(it, it.result != null, it.nationalFiling != null) })
        archives.seal(collection.orgId, BIZ, key(collection), "${phase}_${subject.version}", saved, null)
    }

    fun verify(collection: CollectionDao, rows: List<CollectionGradingDao>) {
        if (rows.map { it.version } != (1..rows.size).toList()) fail("定级版本顺序缺失或异常，请核对行政原件")
        val headers = CollectionPermanentArchiveDao.find {
            (CollectionPermanentArchives.bizType eq BIZ) and (CollectionPermanentArchives.bizId eq key(collection))
        }.toList()
        val required = rows.filter(::versioned).flatMap { row ->
            listOfNotNull("APPLICATION_${row.version}", row.result?.let { "PROVINCIAL_RESULT_${row.version}" },
                row.nationalFiling?.let { "NATIONAL_FILING_${row.version}" })
        }.toSet()
        if (!headers.map { it.phase }.toSet().containsAll(required)) fail("新版定级的独立版本凭据缺失，请核对原件与备份")
        var covered = 0
        headers.forEach { header ->
            val saved = try { JsonUtils.fromJson(header.snapshot, Snapshot::class.java) }
                catch (_: tools.jackson.core.JacksonException) { fail("定级版本凭据无法解析") }
                ?: fail("定级版本凭据为空")
            if (saved.formatVersion != 1 || saved.phase !in phases || saved.entryCount !in 1..rows.size || saved.entryCount != saved.entries.size)
                fail("定级版本凭据数量或格式不一致")
            val subject = rows.find { it.id.value == saved.gradingId } ?: fail("已固定的定级记录缺失，不能采用旧结论")
            if (header.phase != "${saved.phase}_${subject.version}" || subject.version > saved.entryCount ||
                saved.phase == "PROVINCIAL_RESULT" && subject.result == null || saved.phase == "NATIONAL_FILING" && subject.nationalFiling == null)
                fail("定级版本阶段或行政记录不一致")
            val entries = saved.entries.zip(rows.take(saved.entryCount)).map { (old, row) ->
                if (old.gradingId != row.id.value || old.version != row.version) fail("定级版本顺序与已固定依据不一致")
                if (old.nationalFiling != null && old.provincialResult == null)
                    fail("国家备案版本凭据缺少省级结果原件引用")
                if (row.id.value == subject.id.value) {
                    val requiresResult = saved.phase != "APPLICATION"
                    val requiresNational = saved.phase == "NATIONAL_FILING"
                    if ((old.provincialResult != null) != requiresResult || (old.nationalFiling != null) != requiresNational)
                        fail("定级版本凭据的原件引用与当时行政阶段不一致")
                }
                entry(row, old.provincialResult != null, old.nationalFiling != null)
            }
            archives.verifyExisting(header, collection.orgId, BIZ, key(collection), header.phase,
                snapshot(collection, saved.phase, subject.id.value, entries), null)
            covered = maxOf(covered, saved.entryCount)
        }
        if (headers.isNotEmpty() && covered != rows.size) fail("定级版本未完整纳入已固定依据")
        if (headers.isEmpty()) verifyLegacy(collection, rows)
    }

    /** 旧格式无按藏品索引的凭据，只在此兼容路径按馆扫描申请阶段，不生成新基线。 */
    private fun verifyLegacy(collection: CollectionDao, rows: List<CollectionGradingDao>) {
        val ids = rows.map { it.id.value }.toSet()
        CollectionPermanentArchiveDao.find {
            (CollectionPermanentArchives.bizType eq CollectionGradingService.BIZ) and
                (CollectionPermanentArchives.phase eq "APPLICATION") and (CollectionPermanentArchives.orgId eq collection.orgId)
        }.forEach { archive ->
            val node = try { JsonUtils.mapper.readTree(archive.snapshot) }
                catch (_: tools.jackson.core.JacksonException) {
                    if (archive.bizId in ids) fail("旧定级申请原件无法核实")
                    return@forEach
                }
            if (node?.get("collectionId")?.stringValue() == collection.id.value && archive.bizId !in ids)
                fail("旧定级申请原件仍存在但业务记录缺失，不能回退或补建版本")
        }
        if (rows.zipWithNext().any { (old, next) -> !next.application.submittedAt.isAfter(old.application.submittedAt) })
            fail("旧定级申请的实际时间不能证明当前版本顺序，请核对行政原件")
    }
    private fun fail(message: String): Nothing = throw BadRequestException(message)
}
