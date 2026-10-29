package io.hizhengfu.cloud.collection.diagnostic

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.OrgAuditUIDTable
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.EnumEmergencyDutyTarget
import io.hizhengfu.cloud.collection.service.CollectionEmergencyDutyEvidence as D
import io.hizhengfu.cloud.collection.service.CollectionEmergencyEvidence as E
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.sys.diagnostic.domain.*
import io.hizhengfu.cloud.sys.diagnostic.service.DeploymentDiagnosticCheck
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** 从业务记录及归档头双向核对；不能只扫描仍然存在的文件引用。 */
@Component
// SQLite 不支持建立连接后切换 readOnly；只读边界由仅 SELECT 的实现及实际状态回归保证。
@Transactional(timeout = 25)
class CollectionEmergencyOriginalDiagnosticCheck : DeploymentDiagnosticCheck {
    override val descriptor = DeploymentCheckDescriptor(
        "collection.emergency.originals", "DATABASE", "藏品应急管理", "历史应急业务与永久原件",
        EnumDeploymentCheckSeverity.CRITICAL, timeoutMillis = 30_000, order = 124,
    )

    override fun check() = CollectionEmergencyOriginalInspection.inspect()
}

internal object CollectionEmergencyOriginalInspection {
    private data class Source(val table: OrgAuditUIDTable, val phases: Set<String>, val verify: (String) -> Unit)
    private val sources = listOf(
        Source(CollectionEmergencyPlans, setOf("ISSUED", "KEEPER", "SECURITY", "DIRECTOR")) {
            E.plan(CollectionEmergencyPlanDao[it])
        },
        Source(CollectionEmergencyDrills, setOf("DRILL", "EVALUATOR", "DIRECTOR")) {
            E.drill(CollectionEmergencyDrillDao[it])
        },
        Source(CollectionEmergencyDrillRevisions, setOf("DRILL_REVISION", "EVALUATOR", "DIRECTOR")) {
            val row = CollectionEmergencyDrillRevisionDao[it]
            val drill = CollectionEmergencyDrillDao.findById(row.drillId)
                ?: throw BadRequestException("订正所属演练不存在")
            E.drill(drill)
        },
        Source(CollectionEmergencyImprovements, setOf("IMPROVEMENT", "REVIEW")) {
            E.improvement(CollectionEmergencyImprovementDao[it])
        },
        Source(CollectionEmergencyDuties, setOf("DUTY_APPOINTED", "DUTY_HANDOVER", "DUTY_ACCEPTED")) {
            val row = CollectionEmergencyDutyDao[it]
            val target = row.appointment.request.target
            val owner = when (target.type) {
                EnumEmergencyDutyTarget.PLAN -> CollectionEmergencyPlanDao.findById(target.targetId)?.orgId
                EnumEmergencyDutyTarget.DRILL, EnumEmergencyDutyTarget.ISSUE -> CollectionEmergencyDrillDao.findById(target.targetId)?.orgId
                EnumEmergencyDutyTarget.IMPROVEMENT -> CollectionEmergencyImprovementDao.findById(target.targetId)?.orgId
            }
            if (owner != row.orgId) throw BadRequestException("应急职责指定目标缺失或所属馆不一致")
            if (D.records(row.orgId, row.appointment.request.target).none { record -> record.id == row.id })
                throw BadRequestException("应急职责与原指定目标不一致")
        },
    )

    fun inspect(batchSize: Int = 200, sampleLimit: Int = 10): DeploymentCheckOutcome {
        require(batchSize in 1..200 && sampleLimit in 1..10)
        fun interrupted() {
            if (Thread.currentThread().isInterrupted) throw InterruptedException("Emergency original inspection interrupted")
        }
        interrupted()
        val archives = CollectionPermanentArchives
        fun counts() = sources.map { it.table.selectAll().count() } +
            archives.select(archives.id).where { archives.bizType eq E.BIZ }.count()
        val before = counts()
        val scanned = mutableListOf<Long>()
        var invalidRecords = 0L
        var unmatchedArchives = 0L
        val samples = mutableListOf<DeploymentSafeFact>()
        fun sample(table: String, id: String, org: String, issue: String) {
            if (samples.size < sampleLimit) samples += DeploymentSafeFact("issueSample${samples.size + 1}",
                JsonUtils.toJson(mapOf("table" to table, "id" to id, "orgId" to org, "issue" to issue))!!)
        }
        for (source in sources) {
            val table = source.table
            var after: String? = null
            var count = 0L
            while (true) {
                interrupted()
                val cursor = after
                val batch = table.select(table.id, table.orgId).where {
                    cursor?.let { table.id greater it } ?: Op.TRUE
                }.orderBy(table.id).limit(batchSize).toList()
                if (batch.isEmpty()) break
                for (row in batch) {
                    interrupted()
                    val id = row[table.id].value
                    try { source.verify(id) }
                    catch (_: BadRequestException) {
                        invalidRecords++
                        sample(table.tableName, id, row[table.orgId], "BUSINESS_ORIGINAL_MISMATCH")
                    }
                    catch (_: tools.jackson.core.JacksonException) {
                        invalidRecords++
                        sample(table.tableName, id, row[table.orgId], "INVALID_STORED_JSON")
                    }
                }
                count += batch.size
                after = batch.last()[table.id].value
            }
            scanned += count
        }
        var after: String? = null
        var archiveCount = 0L
        while (true) {
            interrupted()
            val cursor = after
            val batch = archives.select(archives.id, archives.orgId, archives.bizId, archives.phase).where {
                (archives.bizType eq E.BIZ) and (cursor?.let { archives.id greater it } ?: Op.TRUE)
            }.orderBy(archives.id).limit(batchSize).toList()
            if (batch.isEmpty()) break
            val ids = batch.map { it[archives.bizId] }.distinct()
            val matches = sources.associateWith { source ->
                val table = source.table
                table.select(table.id, table.orgId).where { table.id inList ids }
                    .map { it[table.id].value to it[table.orgId] }.toSet()
            }
            for (row in batch) {
                val matched = matches.count { (source, records) ->
                    row[archives.phase] in source.phases && (row[archives.bizId] to row[archives.orgId]) in records
                }
                if (matched != 1) {
                    unmatchedArchives++
                    sample(archives.tableName, row[archives.id].value, row[archives.orgId], "ARCHIVE_SOURCE_MISMATCH")
                }
            }
            archiveCount += batch.size
            after = batch.last()[archives.id].value
        }
        scanned += archiveCount
        val incomplete = before != scanned || counts() != scanned
        val facts = sources.mapIndexed { index, source -> DeploymentSafeFact(source.table.tableName, scanned[index].toString()) } + listOf(
            DeploymentSafeFact("scannedArchives", archiveCount.toString()),
            DeploymentSafeFact("invalidBusinessRecords", invalidRecords.toString()),
            DeploymentSafeFact("unmatchedArchives", unmatchedArchives.toString()),
            DeploymentSafeFact("inspectionScope", "stored emergency business, snapshots, file identity and access indexes; not physical bytes, current qualifications or operational acceptance"),
        ) + samples
        val recovery = listOf(DeploymentRemediation(EnumDeploymentRemediationType.DATABASE, "核对应急原件与业务记录并依据备份恢复", listOf(
            "先备份业务表、永久归档表、文件引用、访问索引及实际文件，在暂停相关写入的隔离副本中复查。",
            "按样本 table/id/orgId 定位记录，核对预案、演练、订正、整改及职责交接的真实签署材料和历史版本。",
            "依据已核实备份恢复缺失记录及关联；不得删除业务、重新签署或重封档来消除错误，不补造历史内容摘要。",
            "仅在原件身份已经核实后执行对应索引升级，随后重新扫描并核对业务详情、读取权限及实际文件字节。",
        )))
        return when {
            incomplete -> DeploymentCheckOutcome(EnumDeploymentCheckState.ERROR, "应急原件扫描计数不一致，请暂停相关写入后复查",
                "COLLECTION_EMERGENCY_SCAN_INCOMPLETE", facts, recovery)
            invalidRecords + unmatchedArchives > 0 -> DeploymentCheckOutcome(EnumDeploymentCheckState.FAIL,
                "应急业务与永久原件存在不一致，须核对真实材料及备份", "COLLECTION_EMERGENCY_ORIGINAL_MISMATCH", facts, recovery)
            else -> DeploymentCheckOutcome(EnumDeploymentCheckState.PASS,
                "本次已存应急业务与永久原件一致；零记录及未签署阶段不代表已完成应急工作，实际原件和业务验收仍须另行核验", safeFacts = facts)
        }
    }
}
