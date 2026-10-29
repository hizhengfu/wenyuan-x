package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll

/** 捕获已核验的业务值，写入后直接核对表行；不使用可能保留写前值的 DAO 缓存。 */
internal object ReproductionWriteEvidence {
    private fun fail(): Nothing = throw BadRequestException("复制拓印写入后的活动、归属或归档与本次核对内容不一致；本次登记未完成，请重新核实")

    // 使用与持久化相同的 JSON 契约冻结值，保留毫秒，避免把亚毫秒截断误判成业务变化。
    private fun serialized(value: Any?) = JsonUtils.toJson(value)?.let { JsonUtils.mapper.readTree(it) }
    private fun frozen(values: Map<Column<*>, Any?>) = values.mapValues { serialized(it.value) }

    private fun matches(row: ResultRow?, values: Map<Column<*>, Any?>): Boolean =
        row != null && values.all { (column, expected) -> serialized(row[column]) == expected }

    private fun row(table: IdTable<String>, id: String, values: Map<Column<*>, Any?>): () -> Unit {
        val expected = frozen(values)
        return {
            val written = table.selectAll().where { table.id eq id }.singleOrNull()
            if (!matches(written, expected)) fail()
        }
    }

    fun business(r: ReproductionDao): () -> Unit = row(Reproductions, r.id.value, mapOf(
        Reproductions.orgId to r.orgId,
        Reproductions.collectionId to r.collectionId,
        Reproductions.repType to r.repType,
        Reproductions.purpose to r.purpose,
        Reproductions.repDate to r.repDate,
        Reproductions.flowStatus to r.flowStatus,
        Reproductions.flowInstanceId to r.flowInstanceId,
    ))

    fun plan(p: ReproductionPlanDao): () -> Unit = row(ReproductionPlans, p.id.value, mapOf(
        ReproductionPlans.orgId to p.orgId,
        ReproductionPlans.reproductionId to p.reproductionId,
        ReproductionPlans.version to p.version,
        ReproductionPlans.sourceSnapshot to p.sourceSnapshot,
        ReproductionPlans.qualification to p.qualification,
        ReproductionPlans.content to p.content,
        ReproductionPlans.started to p.started,
        ReproductionPlans.completed to p.completed,
        ReproductionPlans.stopped to p.stopped,
        ReproductionPlans.clearance to p.clearance,
    ))

    private fun outputValues(out: ReproductionOutputDao): Map<Column<*>, Any?> = mapOf(
        ReproductionOutputs.orgId to out.orgId,
        ReproductionOutputs.planId to out.planId,
        ReproductionOutputs.number to out.number,
        ReproductionOutputs.content to out.content,
        ReproductionOutputs.custody to out.custody,
    )

    fun output(out: ReproductionOutputDao): () -> Unit = row(ReproductionOutputs, out.id.value, outputValues(out))

    fun existingArchives(p:ReproductionPlanDao): () -> Unit {
        val originals=CollectionPermanentArchives.selectAll().where {CollectionPermanentArchives.bizId eq p.id.value}.toList()
        val checks=originals.map {saved->row(CollectionPermanentArchives,saved[CollectionPermanentArchives.id].value,
            CollectionPermanentArchives.columns.filterNot {it==CollectionPermanentArchives.id}.associateWith {saved[it]})}
        return {checks.forEach {it()}}
    }

    /** 一次查询核对本批全体产物，包含被移走、删除或异常追加的情况。 */
    fun outputs(p: ReproductionPlanDao, outputs: List<ReproductionOutputDao>): () -> Unit {
        val planId = p.id.value
        val expected = outputs.associate { it.id.value to frozen(outputValues(it)) }
        return {
            val written = ReproductionOutputs.selectAll().where { ReproductionOutputs.planId eq planId }
                .associateBy { it[ReproductionOutputs.id].value }
            if (written.keys != expected.keys || expected.any { (id, values) -> !matches(written[id], values) }) fail()
        }
    }

    fun safety(s: ReproductionSafetyDao): () -> Unit = row(ReproductionSafetyRecords, s.id.value, mapOf(
        ReproductionSafetyRecords.orgId to s.orgId,
        ReproductionSafetyRecords.planId to s.planId,
        ReproductionSafetyRecords.content to s.content,
    ))

    fun administrative(a: CollectionAdministrativeRecordDao): () -> Unit = row(CollectionAdministrativeRecords, a.id.value, mapOf(
        CollectionAdministrativeRecords.orgId to a.orgId,
        CollectionAdministrativeRecords.bizType to a.bizType,
        CollectionAdministrativeRecords.bizId to a.bizId,
        CollectionAdministrativeRecords.content to a.content,
    ))

    fun use(u: ReproductionOutputUseDao): () -> Unit = row(ReproductionOutputUses, u.id.value, mapOf(
        ReproductionOutputUses.orgId to u.orgId,
        ReproductionOutputUses.outputId to u.outputId,
        ReproductionOutputUses.content to u.content,
        ReproductionOutputUses.returned to u.returned,
    ))

    fun archive(id: String, orgId: String, planId: String, phase: String, snapshot: String): () -> Unit =
        row(CollectionPermanentArchives, id, mapOf(
            CollectionPermanentArchives.orgId to orgId,
            CollectionPermanentArchives.bizType to ReproductionComplianceService.BIZ,
            CollectionPermanentArchives.bizId to planId,
            CollectionPermanentArchives.phase to phase,
            CollectionPermanentArchives.snapshot to snapshot,
            CollectionPermanentArchives.digest to CollectionCareEvidence.digest(snapshot),
        ))
}
