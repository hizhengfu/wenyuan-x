package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.SecurityUser
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import java.time.LocalDateTime

/** 源业务集成测试使用真实案卷、报告、处理、本人签署和永久档案。 */
internal class IncidentSourceTestSupport(
    private val incidents: CollectionIncidentService,
    private val at: LocalDateTime,
    private val fileId: String,
) {
    fun actor(id: String) {
        val user = SecurityUser(id=id,name=id,username=id,password="",groupId="museum",groupIds=setOf("museum"),postIds=emptySet(),dataType=0,roles=emptySet(),authorities=listOf(SimpleGrantedAuthority("ADMIN")),enabled=true)
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(user, null, user.authorities)
    }

    fun create(source: IncidentSourceVo?, items: List<IncidentItemVo>): String {
        actor("operator")
        return incidents.create(IncidentFactsVo("实际核验异常", at, location="实际保管位置", reporterUserId="operator", description="来源逐件发现事实", immediateMeasures="隔离保护并报告", items=items, source=source))
    }

    @Suppress("UNCHECKED_CAST")
    fun close(id: String, keeper: String = "keeper", leader: String = "leader") {
        val detail = incidents.detail(id)
        val factsId = detail["factsId"] as String
        val snapshot = detail["facts"] as IncidentFactsSnapshot
        val requirements = detail["requirements"] as List<Map<String, Any>>
        val reports = requirements.filter { it["kind"] == EnumIncidentReportKind.INCIDENT }.groupBy { it["target"] as EnumIncidentTarget }.map { (target, rows) ->
            val report = IncidentReportVo(factsId, rows.map { it["collectionId"] as String }.toSet(), target, EnumIncidentReportKind.INCIDENT, "实际接收机关或部门", if (target == EnumIncidentTarget.KEEPER) keeper else if (target == EnumIncidentTarget.DIRECTOR) leader else null, "回执编号", at.plusMinutes(5), "实际报送内容", fileId)
            incidents.report(id, report) to report
        }.toMap()
        val resultId = incidents.recordHandling(id, IncidentHandlingVo(
            factsId, at.plusMinutes(30), "operator", "调查确认实际原因", "已完成保护及核实", fileId,
            snapshot.items.map { IncidentItemResolutionVo(it.item.collectionId, EnumIncidentDisposition.PRESENT_SAFE, true, "核实后保存安全", "实际保管位置", "custodian", "独立支撑保护", "定期复核", fileId) },
            reports.filter { it.value.target !in setOf(EnumIncidentTarget.KEEPER, EnumIncidentTarget.DIRECTOR) }.map { (rid, report) -> IncidentAuthorityResultVo(report.target, report.authorityName, report.collectionIds, at.plusMinutes(25), setOf(rid), "实际核查处理结论", fileId) },
            keeper, leader, "实际案卷索引", fileId,
        ))
        val filingIds = requirements.filter { it["kind"] == EnumIncidentReportKind.RESULT_FILING }.map { it["collectionId"] as String }.toSet()
        if (filingIds.isNotEmpty()) incidents.report(id, IncidentReportVo(factsId, filingIds, EnumIncidentTarget.NATIONAL, EnumIncidentReportKind.RESULT_FILING, "国务院文物行政部门", null, "结果备案回执", at.plusMinutes(35), "实际核查处理结果备案", fileId, resultId))
        for ((who, minutes) in listOf(keeper to 40L, leader to 45L)) {
            actor(who)
            incidents.confirm(id, IncidentConfirmationVo(incidents.detail(id)["basisRevision"] as String, at.plusMinutes(minutes), "本人核对本版完整案卷及原件", fileId))
        }
        actor("operator")
    }
}
