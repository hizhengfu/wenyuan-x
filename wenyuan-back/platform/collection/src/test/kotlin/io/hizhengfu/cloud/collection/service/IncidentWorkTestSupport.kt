package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.domain.*
import java.time.LocalDateTime

/** 使用实际方案、本人签署、开始和结束服务，验证来源业务仍保留自身占用。 */
internal class IncidentWorkTestSupport(
    private val work: IncidentWorkService,
    private val incidents: CollectionIncidentService,
    private val at: LocalDateTime,
    private val file: String,
) {
    private val people = IncidentSourceTestSupport(incidents, at, file)
    fun plan(collectionId: String, caseId: String) = IncidentWorkPlanVo(
        collectionId,setOf(caseId),at,at.plusHours(1),at.plusHours(3),"原现场","实际不安全现状",
        setOf(EnumIncidentWorkMethod.SUPPORT),"避免进一步损伤","只在原地增设非损伤性支撑，不搬移、不修复、不取样",
        "依实物现状增设支撑","原现场专人保管及保卫","出现新风险立即停止",
        "conservation-dept","conservation","department-head","keeper","leader","security",file,file,
    )
    fun perform(collectionId: String, caseId: String): String {
        people.actor("operator")
        val id=work.create(plan(collectionId,caseId))
        listOf("conservation","department-head","keeper","leader").forEachIndexed { i,person ->
            people.actor(person)
            work.confirm(id,IncidentWorkConfirmationVo(EnumIncidentWorkStage.PLAN,work.detail(id)["planBasis"] as String,at.plusMinutes(10L+i),"本人核对实际原件",file))
        }
        people.actor("operator")
        work.start(id,IncidentWorkStartVo(at.plusHours(1),"原现场","conservation","custodian","security",true,true,true,true,"实际原件在场","支撑保护",file))
        work.end(id,IncidentWorkEndVo(at.plusHours(2),true,"实际支撑措施","本次作业后现状",true,false,"本次作业结束","继续观察，事故另行处理",file))
        listOf("conservation","keeper").forEachIndexed { i,person ->
            people.actor(person)
            work.confirm(id,IncidentWorkConfirmationVo(EnumIncidentWorkStage.END,work.detail(id)["endBasis"] as String,at.plusMinutes(130L+i),"本人核对实际结束原件",file))
        }
        people.actor("operator")
        return id
    }
    fun linkResult(caseId: String, workId: String) {
        val detail=incidents.detail(caseId)
        val old=(detail["facts"] as IncidentFactsSnapshot).facts
        incidents.amend(caseId,old.copy(relatedWorkIds=old.relatedWorkIds+workId,replacementReason="归入本次防护实际结果",replacementFileIds=file),detail["factsId"] as String)
    }
}
