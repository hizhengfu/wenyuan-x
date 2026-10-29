package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.OrgAuditUIDTable
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.common.file.*
import io.hizhengfu.cloud.sys.file.dao.FileDao
import io.hizhengfu.cloud.sys.diagnostic.domain.*
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** 只追加派生访问索引，不改历史路径、快照、摘要、文件或原有访问记录。 */
@Component
@Transactional
class CollectionFileIdentityUpgrade(private val localStorage: LocalFileObjectStorage? = null) {
    data class Target(val bizType: String, val table: OrgAuditUIDTable, val owner: Column<String>,
        val file: Column<String>, val key: Column<String>, val ownerIsArchive: Boolean = true)
    data class Result(val references: Int, val missingKeys: Int, val addedKeys: Int, val unresolved: Int) {
        fun outcome() = DeploymentCheckOutcome(
            if (missingKeys == 0 && unresolved == 0) EnumDeploymentCheckState.PASS else EnumDeploymentCheckState.FAIL,
            if (missingKeys == 0 && unresolved == 0) "当前挂载与已归档原件访问索引一致；未核验原件内容和外部直链"
            else "原件访问索引尚未齐备，须核对历史路径和实际存储后重新升级",
            "COLLECTION_FILE_IDENTITY", listOf(
                DeploymentSafeFact("archiveReferences", references.toString()),
                DeploymentSafeFact("missingKeys", missingKeys.toString()),
                DeploymentSafeFact("unresolvedRecords", unresolved.toString()),
            ), remediations = if (missingKeys == 0 && unresolved == 0) emptyList() else listOf(
                DeploymentRemediation(EnumDeploymentRemediationType.CONFIGURATION, "核对历史原件并补齐访问索引", listOf(
                    "保留原档案、文件和访问记录；核对文件挂载、历史别名、当前文件位置及所属收藏单位",
                    "恢复可核实的原文件后，重新执行对应模块升级，再运行部署扫描；扫描本身不修改数据",
                    "本机文件身份不能证明内容未变，迁机或恢复后须重新升级并另行核验原件摘要、权限和外部直链",
                )),
            ),
        )
    }
    companion object {
        val DIGITAL = Target(DigitalAcquisitionService.BIZ, DigitalAcquisitionFileAccesses,
            DigitalAcquisitionFileAccesses.planId, DigitalAcquisitionFileAccesses.fileId, DigitalAcquisitionFileAccesses.pathKey, false)
        val EMERGENCY = Target(CollectionEmergencyEvidence.BIZ, CollectionEmergencyFileAccesses,
            CollectionEmergencyFileAccesses.archiveId, CollectionEmergencyFileAccesses.fileId, CollectionEmergencyFileAccesses.pathKey)
        val CORE = listOf(DIGITAL, EMERGENCY)
    }

    fun upgrade(targets: List<Target> = CORE): Result = process(targets, true).also {
        if (it.unresolved > 0) org.slf4j.LoggerFactory.getLogger(javaClass).warn("原件访问索引升级仍有 {} 项待核对；请运行部署扫描，不应视为原件验收通过", it.unresolved)
    }
    // 仅执行 SELECT；SQLite JDBC 不支持连接建立后切换 readOnly，勿把此提示当作写入边界。
    fun inspect(targets: List<Target> = CORE): Result = process(targets, false)

    private fun process(targets: List<Target>, write: Boolean): Result {
        var references = 0; var missing = 0; var added = 0; var unresolved = 0
        val a = CollectionPermanentArchives; val f = CollectionPermanentArchiveFiles
        for (target in targets) {
            var offset = 0L
            while (true) {
                val batch = a.join(f, JoinType.INNER, a.id, f.archiveId).selectAll()
                    .where { a.bizType eq target.bizType }.orderBy(f.id).limit(200).offset(offset).toList()
                if (batch.isEmpty()) break
                for (ref in batch) {
                    references++
                    val org = ref[a.orgId]; val fileId = ref[f.fileId]
                    val owner = if (target.ownerIsArchive) ref[a.id].value else ref[a.bizId]
                    val existing = target.table.selectAll().where { (target.owner eq owner) and (target.file eq fileId) }.toList()
                    if (ref[f.orgId] != org || ref[f.bizId] != ref[a.bizId] || existing.any { it[target.table.orgId] != org }) {
                        unresolved++; continue
                    }
                    var invalid = false
                    val locations = linkedSetOf<Pair<String, String>>()
                    try {
                        val metadata = JsonUtils.mapper.readTree(ref[f.metadata])
                        val path = metadata?.get("path")?.takeIf { it.isString }?.stringValue()?.takeIf { it.isNotBlank() }
                        val type = metadata?.get("storageType")?.takeIf { it.isString }?.stringValue()
                        if (path == null || type == null || EnumFileStorageType.entries.none { it.name == type }) invalid = true
                        else locations.add(path to type)
                    } catch (_: tools.jackson.core.JacksonException) { invalid = true }
                    val current = FileDao.findById(fileId)
                    if (current == null) invalid = true else locations.add(current.path to current.storageType.name)
                    val keys = linkedSetOf<String>()
                    for ((path, type) in locations) {
                        try {
                            keys.add(FileReadPath.key(path, type))
                            val resolved = FileReadPath.keys(path, type, localStorage)
                            keys.addAll(resolved)
                            if (FileReadPath.storageType(path, type) == EnumFileStorageType.LOCAL && resolved.none { it.startsWith("n") }) invalid = true
                        } catch (_: io.hizhengfu.cloud.common.exception.BadRequestException) { invalid = true }
                        catch (_: java.io.IOException) { invalid = true }
                        catch (_: java.nio.file.InvalidPathException) { invalid = true }
                    }
                    // 原生身份可能因迁机、重建或外部替换而变化，不能静默删除旧保护或宣称已经核实。
                    if (existing.any { it[target.key].startsWith("n") && it[target.key] !in keys }) invalid = true
                    if (invalid) unresolved++
                    val absent = keys - existing.map { it[target.key] }.toSet()
                    missing += absent.size
                    if (write) for (key in absent) {
                        target.table.insert {
                            it[target.table.orgId] = org; it[target.owner] = owner
                            it[target.file] = fileId; it[target.key] = key
                        }
                        added++
                    }
                }
                offset += batch.size
            }
            // 旧访问记录没有可对应的归档来源时不得报通过，也不能猜测原始路径补写。
            offset = 0
            while (true) {
                val batch = target.table.select(target.owner, target.file, target.table.orgId).withDistinct()
                    .orderBy(target.owner to SortOrder.ASC, target.file to SortOrder.ASC, target.table.orgId to SortOrder.ASC).limit(200).offset(offset).toList()
                if (batch.isEmpty()) break
                for (index in batch) {
                    val owner = index[target.owner]
                    val source = a.join(f, JoinType.INNER, a.id, f.archiveId).selectAll().where {
                        (a.bizType eq target.bizType) and (f.fileId eq index[target.file]) and
                            (a.orgId eq index[target.table.orgId]) and
                            (if (target.ownerIsArchive) a.id eq owner else a.bizId eq owner)
                    }.limit(1).any()
                    if (!source) unresolved++
                }
                offset += batch.size
            }
        }
        return Result(references, missing, added, unresolved)
    }
}
