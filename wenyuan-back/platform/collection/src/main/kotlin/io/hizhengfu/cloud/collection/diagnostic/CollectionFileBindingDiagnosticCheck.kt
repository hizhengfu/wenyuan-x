package io.hizhengfu.cloud.collection.diagnostic

import io.hizhengfu.cloud.autoconfigure.exposed.UIDTable
import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.common.install.AbstractInstall
import io.hizhengfu.cloud.sys.diagnostic.domain.*
import io.hizhengfu.cloud.sys.diagnostic.service.DeploymentDiagnosticCheck
import io.hizhengfu.cloud.sys.file.dao.FileContents
import io.hizhengfu.cloud.sys.file.dao.Files
import io.hizhengfu.cloud.sys.file.service.FileUploadTargetSchema
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** 历史通用附件关联的只读核查；记录存在性不能代替原业务确认或原件读取授权。 */
// 检查仅查询；SQLite 建连后不能切换 readOnly，保留事务以覆盖实际后台扫描。
@Component
@Transactional(timeout = 25)
class CollectionFileBindingDiagnosticCheck(
    private val installs: ObjectProvider<AbstractInstall>,
    private val restricted: ObjectProvider<FileUploadTargetSchema>,
) : DeploymentDiagnosticCheck {
    override val descriptor = DeploymentCheckDescriptor(
        "collection.files.binding-targets", "DATABASE", "藏品材料上传", "历史附件关联目标",
        EnumDeploymentCheckSeverity.CRITICAL, timeoutMillis = 30_000, order = 123,
    )

    override fun check(): DeploymentCheckOutcome {
        val declared = installs.orderedStream().toList().flatMap { it.initTables().orEmpty() } +
            restricted.orderedStream().toList().flatMap { it.publicUploadBlockedTables() }
        return CollectionFileBindingInspection.inspect(declared.filterIsInstance<UIDTable>().distinctBy { it.tableName })
    }
}

internal object CollectionFileBindingInspection {
    fun inspect(tables: List<UIDTable>, batchSize: Int = 200, sampleLimit: Int = 10): DeploymentCheckOutcome {
        require(batchSize in 1..200 && sampleLimit in 1..10)
        if (Thread.currentThread().isInterrupted) throw InterruptedException("Historical file binding inspection interrupted")
        val targets = tables.distinctBy { it.tableName }
        if (targets.isEmpty()) return DeploymentCheckOutcome(
            EnumDeploymentCheckState.ERROR, "未取得业务记录表声明，无法核对历史附件关联", "COLLECTION_FILE_BINDING_SCHEMA_UNAVAILABLE",
        )
        val totalBefore = FileContents.selectAll().count()
        var scanned = 0L
        var missingFiles = 0L
        var missingTargets = 0L
        var ambiguousTargets = 0L
        var after: Pair<String, String>? = null
        val samples = mutableListOf<Map<String, Any>>()
        while (true) {
            if (Thread.currentThread().isInterrupted) throw InterruptedException("Historical file binding inspection interrupted")
            val cursor = after
            val batch = FileContents.select(FileContents.contentId, FileContents.fileId).where {
                cursor?.let {
                    (FileContents.contentId greater it.first) or
                        ((FileContents.contentId eq it.first) and (FileContents.fileId greater it.second))
                } ?: Op.TRUE
            }.orderBy(FileContents.contentId to SortOrder.ASC, FileContents.fileId to SortOrder.ASC).limit(batchSize).toList()
            if (batch.isEmpty()) break
            val contentIds = batch.map { it[FileContents.contentId] }.distinct()
            val fileIds = batch.map { it[FileContents.fileId] }.distinct()
            val presentFiles = Files.select(Files.id).where { Files.id inList fileIds }.map { it[Files.id].value }.toSet()
            val matches = mutableMapOf<String, Int>()
            // 每批一次联合主键查询；分组避免超过数据库的 compound SELECT 限制。
            for (group in targets.chunked(50)) {
                val firstId = group.first().id
                val queries = group.map { table -> table.select(table.id).where { table.id inList contentIds } }
                val found = if (queries.size == 1) queries.single().map { it[firstId].value }
                else queries.drop(2).fold(queries[0].unionAll(queries[1])) { union, query -> union.unionAll(query) }
                    .map { it[firstId].value }
                found.forEach { matches[it] = (matches[it] ?: 0) + 1 }
            }
            for (row in batch) {
                val contentId = row[FileContents.contentId]
                val fileId = row[FileContents.fileId]
                val targetCount = matches[contentId] ?: 0
                val fileMissing = fileId !in presentFiles
                if (fileMissing) missingFiles++
                if (targetCount == 0) missingTargets++
                if (targetCount > 1) ambiguousTargets++
                if ((fileMissing || targetCount != 1) && samples.size < sampleLimit) samples += mapOf(
                    "contentId" to contentId, "fileId" to fileId,
                    "fileRecordMissing" to fileMissing, "matchingTargets" to targetCount,
                )
            }
            scanned += batch.size
            after = batch.last().let { it[FileContents.contentId] to it[FileContents.fileId] }
        }
        val totalAfter = FileContents.selectAll().count()
        val facts = listOf(
            DeploymentSafeFact("declaredStringIdTables", targets.size.toString()),
            DeploymentSafeFact("scannedBindings", scanned.toString()),
            DeploymentSafeFact("missingFileBindings", missingFiles.toString()),
            DeploymentSafeFact("unresolvedTargetBindings", missingTargets.toString()),
            DeploymentSafeFact("ambiguousTargetBindings", ambiguousTargets.toString()),
            DeploymentSafeFact("inspectionScope", "current file rows and declared string-ID targets only; not business confirmation, permissions or original bytes"),
        ) + samples.mapIndexed { index, sample ->
            // 聚合诊断限制单个事实长度；逐条输出，避免长历史编号使整组样本被截断。
            DeploymentSafeFact("issueSample${index + 1}", JsonUtils.toJson(sample)!!)
        }
        val remediation = listOf(DeploymentRemediation(
            EnumDeploymentRemediationType.DATABASE, "依据原件清单和业务办理记录核对历史关联，不直接删除或改号",
            listOf("先备份关系表、相关业务记录和文件存储，在暂停相关写入的隔离副本中复查。",
                "按样本 contentId/fileId 定位原记录；未声明旧表、缺失目标或同号记录须核实真实来源。",
                "文件记录存在不代表实际字节完整或已经业务确认；另行核对读取权限和备份前独立摘要。"),
        ))
        return when {
            totalBefore != scanned || totalAfter != scanned -> DeploymentCheckOutcome(
                EnumDeploymentCheckState.ERROR, "附件关联扫描计数不一致，可能存在历史重复关联或扫描期间变化，请暂停相关写入后复查",
                "COLLECTION_FILE_BINDING_SCAN_INCOMPLETE", facts, remediation,
            )
            missingFiles + missingTargets + ambiguousTargets > 0 -> DeploymentCheckOutcome(
                EnumDeploymentCheckState.FAIL, "历史附件关联存在缺失文件、未确认目标或同号多目标，须核实原记录",
                "COLLECTION_FILE_BINDING_UNRESOLVED", facts, remediation,
            )
            else -> DeploymentCheckOutcome(
                EnumDeploymentCheckState.PASS, "本次附件关联均能对应文件记录和唯一的已声明业务记录，业务确认与实际原件仍须单独核验",
                safeFacts = facts,
            )
        }
    }
}
