package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.domain.*
import java.time.LocalDateTime

/** 来源联动使用真实搬移、原件、本人核对和案卷关联。 */
internal class IncidentRelocationTestSupport(
    private val relocation:IncidentRelocationService,
    private val incidents:CollectionIncidentService,
    private val at:LocalDateTime,
    private val file:String,
) {
    private val people=IncidentSourceTestSupport(incidents,at,file)
    fun actualEmergency(collectionId:String,caseId:String,from:String,to:String):String {
        people.actor("operator")
        return relocation.recordEmergency(IncidentEmergencyRelocationVo(collectionId,setOf(caseId),"原现场突发风险已实际转移","实际指挥和现场经过，未冒充事前方案批准",file,"conservation","keeper",
            IncidentRelocationDepartureVo(at.plusHours(1),from,setOf("custodian","conservation"),"supervisor",true,true,"真实搬出前状态","真实支撑包护",file,file),
            IncidentRelocationReceiptVo(at.plusHours(2),to,setOf("receiver","conservation"),"receiver",true,true,true,false,true,"真实接收状态","支撑隔离","定期核查并完成事故处理",file,file)))
    }
    fun confirmAndLink(caseId:String,id:String) {
        for((i,actor) in listOf("conservation","keeper").withIndex()) {
            people.actor(actor)
            relocation.confirm(id,IncidentWorkConfirmationVo(EnumIncidentWorkStage.END,relocation.detail(id)["endBasis"] as String,at.plusMinutes(130L+i),"本人核对实际搬移原件",file))
        }
        people.actor("operator")
        val d=incidents.detail(caseId);val facts=(d["facts"] as IncidentFactsSnapshot).facts
        incidents.amend(caseId,facts.copy(relatedRelocationIds=facts.relatedRelocationIds+id,replacementReason="补入实际搬移及接收原件",replacementFileIds=file),d["factsId"] as String)
    }
}
