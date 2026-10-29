package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.and
import java.security.MessageDigest
import org.springframework.stereotype.Component
import java.time.LocalDateTime

data class IncidentStoppedCustody(val stoppedAt: LocalDateTime, val evidence: String)

/** 可选业务模块提供自己的永久原件；藏品核心不反向依赖业务模块。 */
interface CollectionIncidentSourceProvider {
    val bizType: String
    fun relatedCustodySources(facts: IncidentFactsVo): List<IncidentSourceVo> = emptyList()
    fun snapshot(orgId: String, source: IncidentSourceVo, items: List<IncidentItemVo>): Any
    fun reportingCatalogs(evidence: String): Map<String, CollectionCatalogContentVo> = emptyMap()
    fun stoppedCustody(orgId: String, collectionId: String, source: IncidentSourceVo): IncidentStoppedCustody
}

/** 来源只提供既有事实，登记案卷不会替来源业务变更状态。 */
@Component
class CollectionIncidentSourceService(private val providers: List<CollectionIncidentSourceProvider> = emptyList()) {
    fun custodySources(facts: IncidentFactsVo): List<IncidentSourceVo> = (providers.flatMap {it.relatedCustodySources(facts)} + listOfNotNull(facts.source)).distinct()
    fun supportsStoppedCustody(bizType: String) = providers.any { it.bizType == bizType }
    fun stoppedCustody(orgId: String, collectionId: String, source: IncidentSourceVo): IncidentStoppedCustody =
        provider(source.bizType)?.stoppedCustody(orgId, collectionId, source) ?: fail("该来源尚未接入实际停止及保管原件核验")
    private fun provider(bizType: String): CollectionIncidentSourceProvider? {
        val found = providers.filter { it.bizType == bizType }
        if (found.size > 1) fail("事故来源核验配置重复，请核实业务模块配置")
        return found.singleOrNull()
    }
    /** 来源冻结的性质级别也进入报告判断，后续主档订正不能抹去原已知义务。 */
    fun reportingItems(snapshot: IncidentFactsSnapshot): List<IncidentFactItemSnapshot> {
        val source = snapshot.sourceEvidence?.let { JsonUtils.mapper.readTree(it) }
        val original = when (snapshot.facts.source?.bizType) {
            CollectionConst.BIZ_RETURN -> source?.get("sourceSnapshot")?.let {
                JsonUtils.fromJson(it.toString(), ReturnSourceSnapshotVo::class.java)!!.items.associate { item -> item.collectionId to item.content }
            }
            InventoryComplianceService.BIZ -> source?.get("baseline")?.let {
                JsonUtils.fromJson(it.toString(), InventoryBaselineVo::class.java)!!.items.associate { item -> item.collectionId to item.content }
            }
            else -> snapshot.facts.source?.let { s -> snapshot.sourceEvidence?.let { provider(s.bizType)?.reportingCatalogs(it) } }
        }.orEmpty()
        return snapshot.items + snapshot.items.mapNotNull { item -> original[item.item.collectionId]?.let { IncidentFactItemSnapshot(item.item, it) } }
    }

    fun snapshot(orgId: String, source: IncidentSourceVo, items: List<IncidentItemVo>): String {
        val ids = items.map { it.collectionId }.toSet()
        val value: Any = when (source.bizType) {
            CollectionConst.BIZ_SAMPLING -> {
                val r = CollectionSamplingDao.findById(source.bizId) ?: fail("来源取样不存在")
                val p = source.recordId?.let(CollectionSamplingPlanDao::findById) ?: fail("须关联实际取样方案版本")
                if (r.orgId != orgId || p.orgId != orgId || p.samplingId != r.id.value || ids != setOf(r.collectionId) || p.started == null) fail("事故来源须为本馆本件已实际开始的取样")
                exact(orgId,CollectionConst.BIZ_SAMPLING,p.id.value,"PLAN",mapOf("samplingId" to p.samplingId,"version" to p.version,"sourceSnapshot" to p.sourceSnapshot,"authority" to p.authority,"content" to p.content))
                exact(orgId,CollectionConst.BIZ_SAMPLING,p.id.value,"STARTED",p.started!!)
                p.ended?.let { exact(orgId,CollectionConst.BIZ_SAMPLING,p.id.value,"EXTRACTION_ENDED",it) }
                val rows=CollectionSampleDao.find { CollectionSamples.planId eq p.id.value }.sortedBy { it.id.value }
                rows.forEach { exact(orgId,CollectionConst.BIZ_SAMPLING,p.id.value,"SAMPLE_${it.id.value}",it.content) }
                val samples = rows.map { it.content }
                if (samples.none { !it.withinApprovedScope || it.unexpectedDamage } && p.ended?.safeToReleaseOriginal != false) fail("请先据实登记来源取样异常或不安全结束")
                if (samples.any { it.unexpectedDamage } && items.single().damaged != true) fail("来源已记录额外损伤，案卷不能省略损毁事实")
                mapOf("planId" to p.id.value, "plan" to p.content, "started" to p.started, "ended" to p.ended, "samples" to samples)
            }
            CollectionConst.BIZ_RETURN -> {
                if(source.recordId!=null)fail("归还来源只需实际归还单编号，不应混入取样方案编号")
                val r = CollectionReturnDao.findById(source.bizId) ?: fail("来源归还单不存在")
                val checks = r.inspection?.items.orEmpty().filter { it.collectionId in ids }
                if (r.orgId != orgId || checks.size != ids.size || checks.any { !it.abnormal() }) fail("须关联本馆已登记的逐件归还异常")
                val expectedInspection=JsonUtils.mapper.readTree(JsonUtils.toJson(r.inspection)!!)
                val expectedSource=JsonUtils.mapper.readTree(JsonUtils.toJson(r.sourceSnapshot ?: fail("来源归还点交基线尚未核实，可先独立登记已知事故事实"))!!)
                requireArchive(orgId,CollectionConst.BIZ_RETURN,r.id.value){ phase,snapshot->phase.startsWith("INSPECTION_") && snapshot.get("inspection")==expectedInspection && snapshot.get("sourceSnapshot")==expectedSource }
                checks.forEach { check ->
                    val item = items.single { it.collectionId == check.collectionId }
                    if ((check.damaged && !item.damaged) || (check.securityIncident != EnumReturnSecurityIncident.NONE && item.security.name != check.securityIncident.name)) fail("案卷不得省略来源归还损伤或失窃丢失事实")
                }
                mapOf("inspection" to r.inspection, "sourceSnapshot" to r.sourceSnapshot)
            }
            InventoryComplianceService.BIZ -> {
                if(source.recordId!=null)fail("清点来源只需实际批次编号，不应混入取样方案编号")
                val r = InventoryBatchDao.findById(source.bizId) ?: fail("来源清点批次不存在")
                val checks = r.checks?.items.orEmpty().filter { it.collectionId in ids }
                if (r.orgId != orgId || checks.size != ids.size || checks.any { !it.abnormal() }) fail("须关联本馆已登记的逐件清点异常")
                checks.forEach { check->
                    val expected=JsonUtils.mapper.readTree(JsonUtils.toJson(check)!!)
                    requireArchive(orgId,InventoryComplianceService.BIZ,r.id.value){phase,snapshot->phase.startsWith("CHECK_") && snapshot==expected}
                }
                checks.forEach { check -> if (check.damaged == true && items.single { it.collectionId == check.collectionId }.damaged != true) fail("案卷不得省略来源清点损伤事实") }
                mapOf("plan" to r.plan, "baseline" to r.baseline, "checks" to checks)
            }
            else -> provider(source.bizType)?.snapshot(orgId, source, items)
                ?: fail("该来源尚未接入事故原件核验；可先独立登记真实事故，不能耽误实际报告")
        }
        return JsonUtils.toJson(value)!!
    }
    private fun exact(orgId:String,bizType:String,bizId:String,phase:String,value:Any) {
        val expected=JsonUtils.mapper.readTree(JsonUtils.toJson(value)!!)
        requireArchive(orgId,bizType,bizId){name,snapshot->name==phase && snapshot==expected}
    }
    private fun requireArchive(orgId:String,bizType:String,bizId:String,matches:(String,tools.jackson.databind.JsonNode)->Boolean) {
        val found=CollectionPermanentArchiveDao.find { (CollectionPermanentArchives.bizType eq bizType) and (CollectionPermanentArchives.bizId eq bizId) }.any { row->
            row.orgId==orgId && row.digest==MessageDigest.getInstance("SHA-256").digest(row.snapshot.toByteArray()).joinToString(""){"%02x".format(it)} && matches(row.phase,JsonUtils.mapper.readTree(row.snapshot))
        }
        if(!found)fail("来源异常缺少一致的永久原件，请先核实原业务记录")
    }
    private fun fail(message: String): Nothing = throw BadRequestException(message)
}
