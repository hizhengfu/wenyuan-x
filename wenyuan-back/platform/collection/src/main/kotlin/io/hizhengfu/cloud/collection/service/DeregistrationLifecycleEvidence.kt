package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.EnumCollectionStatus
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.flow.dao.FlowInstanceLogDao
import io.hizhengfu.cloud.flow.service.FlowApprovalHistory
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import tools.jackson.databind.JsonNode

/** 核对各阶段保留的审批原日志与实际执行、关闭事实；公告追加不会覆盖第一次关闭快照。 */
internal object DeregistrationLifecycleEvidence {
    private val immutable = listOf("id","orgId","collectionId","reason","detail","dossier","vetoUntil","approveLevel","approvalDocNo","expertOpinion","destination","attachmentIds","createTime","createBy")
    fun verify(e:DeregistrationDao,archives:CollectionPermanentArchiveService,saved:Map<String,Map<String,Any?>>,verifyContent:Boolean=true) {
        fun node(value:Any?):JsonNode=JsonUtils.mapper.readTree(JsonUtils.toJson(value))
        val current=node(e.toModel())
        val experts=DeregistrationExpertOpinionDao.find {DeregistrationExpertOpinions.deregistrationId eq e.id.value}.map {it.content.copy(id=it.id.value)}
        val notices=DeregistrationNoticeDao.find {DeregistrationNotices.deregistrationId eq e.id.value}.map {it.content.copy(id=it.id.value)}
        val admin=CollectionAdministrativeRecordDao.find {(CollectionAdministrativeRecords.bizType eq io.hizhengfu.cloud.collection.common.CollectionConst.BIZ_DEREGISTER) and (CollectionAdministrativeRecords.bizId eq e.id.value)}.map {it.content.copy(id=it.id.value)}
        fun sameSubset(snapshot:JsonNode,key:String,values:Any,allowLater:Boolean=false) {
            val old=snapshot.get(key) ?: JsonUtils.mapper.createArrayNode()
            val now=node(values)
            if(!old.isArray || !now.isArray)fail()
            val ids=old.asSequence().map {it.get("id")?.asText() ?: fail()}.toList()
            if(ids.size!=ids.distinct().size)fail()
            if(old.any {item->now.none {it==item}} || (!allowLater && old.size()!=now.size()))fail()
            if(allowLater && now.any {item->old.none {it==item} && text(item,"kind")!="RESULT"})fail()
        }
        fun request(value:JsonNode,keys:List<String> = immutable) {
            keys.forEach {key->if(value.get(key)!=current.get(key))fail()}
            sameSubset(value,"experts",experts)
            sameSubset(value,"notices",notices,true)
            sameSubset(value,"administrativeEvidence",admin)
        }
        fun files(value:JsonNode):String = (listOf(text(value,"attachmentIds"))+listOf("dispositionPlanFileIds","legalBasisFileIds","recipientStateEvidenceFileIds").map {text(value.path("dossier"),it)}).filterNotNull().joinToString(",")
        fun references(phase:String,ids:String) {
            val original=saved[phase] ?: fail()
            archives.verifyExisting(CollectionPermanentArchiveDao[original["id"] as String],e.orgId,
                io.hizhengfu.cloud.collection.common.CollectionConst.BIZ_DEREGISTER,e.id.value,phase,
                JsonUtils.mapper.readTree(original["snapshot"] as String),ids,verifyContent=verifyContent)
        }
        fun reference(value:JsonNode,phase:String) {
            val original=saved[phase] ?: fail()
            if(value!=node(mapOf("id" to original["id"],"phase" to original["phase"],"digest" to original["digest"])))fail()
        }
        fun version(value:JsonNode):Boolean {
            if(!value.has("lifecycleVersion")) {
                if(listOf("proposalArchive","priorApprovals","priorApprovalCount","submissionState","submissionArchive","approvalArchive","executionArchive").any(value::has))fail()
                return false
            }
            if(value.get("lifecycleVersion").asInt()!=1)fail()
            return true
        }
        val approvalIds=mutableMapOf<String,List<String>>()
        saved.filterKeys {it.startsWith("APPROVED_")}.forEach { (phase,entry)->
            val root=JsonUtils.mapper.readTree(entry["snapshot"] as String)
            val req=root.get("request") ?: fail();request(req)
            if(version(root)) {
                reference(root.get("proposalArchive") ?: fail(),"PROPOSAL")
                when(text(root,"submissionState")) {
                    "NOT_RECORDED"->if("SUBMITTED" in saved || root.has("submissionArchive"))fail()
                    "RECORDED"->reference(root.get("submissionArchive") ?: fail(),"SUBMITTED")
                    else->fail()
                }
                val count=root.get("priorApprovalCount")?.asInt() ?: fail()
                val prior=root.get("priorApprovals") ?: JsonUtils.mapper.createArrayNode()
                if(!prior.isArray || count!=prior.size())fail()
                val phases=prior.asSequence().map {text(it,"phase") ?: fail()}.toList()
                if(phases.size!=phases.distinct().size || phases.any {it==phase || !it.startsWith("APPROVED_")})fail()
                prior.forEach {reference(it,text(it,"phase") ?: fail())}
            }
            if(req.get("flowInstanceId")!=current.get("flowInstanceId"))fail()
            val signatures=root.get("approvals") ?: fail()
            if(!signatures.isArray || signatures.size()!=2)fail()
            val logs=signatures.asSequence().map {signature->
                val id=text(signature,"id") ?: fail()
                val log=FlowInstanceLogDao.findById(id) ?: fail()
                val actual=node(log.toVo())
                // 显示文案来自代码字典，不作为历史原日志字段；旧格式的空轮次字段保持可读。
                listOf("id","instanceId","taskId","nodeKey","nodeName","action","operator","operatorName","comment","createTime","approvalRoundId","eventSequence").forEach {key->
                    val old=signature.get(key);val now=actual.get(key)
                    if(old!=now && !(old==null && (now==null || now.isNull)))fail()
                }
                if(log.instanceId!=e.flowInstanceId || log.action!="APPROVE" || log.comment.isNullOrBlank())fail()
                log
            }.toList()
            val d=e.dossier ?: fail()
            if(logs.map {it.nodeKey}!=listOf("keeper","leader") || logs.map {it.operator}!=listOf(d.keeperUserId,d.leaderUserId) ||
                phase!="APPROVED_${logs.last().taskId?.takeLast(40) ?: fail()}")fail()
            FlowApprovalHistory.requireOrdered(logs)
            approvalIds[phase]=logs.map {it.id.value}
            references(phase,files(req))
        }
        if(e.flowStatus==EnumFlowStatus.PASS || e.executedAt!=null) {
            val id=e.flowInstanceId ?: fail();val d=e.dossier ?: fail()
            val actual=FlowApprovalHistory.current(id)
            val required=listOf("keeper" to d.keeperUserId,"leader" to d.leaderUserId).map { (key,user)->actual.lastOrNull {it.nodeKey==key && it.operator==user && !it.comment.isNullOrBlank()} ?: fail() }
            FlowApprovalHistory.requireOrdered(required)
            val phase="APPROVED_${required.last().taskId?.takeLast(40) ?: fail()}"
            if(approvalIds[phase]!=required.map {it.id.value} || e.approvedBy!=d.leaderUserId)fail()
            val latest=JsonUtils.mapper.readTree(saved.getValue(phase)["snapshot"] as String)
            if(version(latest) && (latest.get("priorApprovals") ?: JsonUtils.mapper.createArrayNode()).asSequence().map {text(it,"phase")}.toSet()!=approvalIds.keys-phase)fail()
        }
        saved["SUBMITTED"]?.let {entry->
            val root=JsonUtils.mapper.readTree(entry["snapshot"] as String)
            request(root,listOf("collectionId","reason","detail","approveLevel","approvalDocNo","expertOpinion","destination","attachmentIds","dossier"))
            if(root.get("baseline")!=node(e.baseline))fail()
            references("SUBMITTED",files(root))
        }
        val executed=saved["EXECUTED"]
        if((e.executedAt!=null)!=(executed!=null))fail()
        executed?.let {entry->
            val root=JsonUtils.mapper.readTree(entry["snapshot"] as String);val req=root.get("request") ?: fail()
            request(req)
            if(version(root)) {
                reference(root.get("proposalArchive") ?: fail(),"PROPOSAL")
                reference(root.get("approvalArchive") ?: fail(),currentApprovalPhase(e))
            }
            listOf("executedAt","flowInstanceId","approvedBy","flowStatus").forEach {if(req.get(it)!=current.get(it))fail()}
            if(e.flowStatus!=EnumFlowStatus.PASS)fail()
            val status=Collections.select(Collections.lifeStatus).where {(Collections.id eq e.collectionId) and (Collections.orgId eq e.orgId)}.singleOrNull()?.get(Collections.lifeStatus)
            if(status!=null && status!=EnumCollectionStatus.DEREGISTERED)fail()
            val execution=root.get("execution") ?: fail()
            references("EXECUTED",listOf(files(req),text(execution,"signedChangeFileIds") ?: fail(),text(execution,"dispositionReceiptFileIds") ?: fail()).joinToString(","))
        }
        val closed=saved["CLOSED"]
        if((e.announcedAt!=null)!=(closed!=null))fail()
        closed?.let {entry->
            if(e.executedAt==null)fail()
            val req=JsonUtils.mapper.readTree(entry["snapshot"] as String);request(req)
            if(version(req))reference(req.get("executionArchive") ?: fail(),"EXECUTED")
            val results=req.path("notices").asSequence().filter {text(it,"kind")=="RESULT"}.groupBy {text(it,"disclosureReviewId")}
            if(results.values.none {rows->rows.map {text(it,"channel")}.containsAll(listOf("MUSEUM_ONSITE","MUSEUM_WEBSITE"))})fail()
            listOf("announcedAt","executedAt","flowInstanceId","approvedBy","flowStatus").forEach {if(req.get(it)!=current.get(it))fail()}
            references("CLOSED",files(req))
        }
    }
    fun reference(e:DeregistrationDao,archives:CollectionPermanentArchiveService,phase:String):Map<String,Any?> {
        val original=archives.listForAuthorizedOrg(io.hizhengfu.cloud.collection.common.CollectionConst.BIZ_DEREGISTER,e.id.value,e.orgId).singleOrNull {it["phase"]==phase} ?: fail()
        return mapOf("id" to original["id"],"phase" to original["phase"],"digest" to original["digest"])
    }
    fun currentApprovalPhase(e:DeregistrationDao):String {
        val d=e.dossier ?: fail();val logs=FlowApprovalHistory.current(e.flowInstanceId ?: fail())
        val leader=logs.lastOrNull {it.nodeKey=="leader" && it.operator==d.leaderUserId} ?: fail()
        return "APPROVED_${leader.taskId?.takeLast(40) ?: fail()}"
    }
    private fun text(node:JsonNode,key:String):String?=node.get(key)?.takeUnless {it.isNull}?.asText()?.takeIf(String::isNotBlank)
    private fun fail():Nothing=throw BadRequestException("退出审批、执行或结果公告状态与永久原件不一致，请核对历史记录")
}
