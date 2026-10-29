package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.DeregistrationExpertOpinionVo
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.select
import java.time.LocalDate

internal data class DeregistrationVetoOriginal(
    val collectionId:String,
    val totalRegNo:String?,
    val expertOpinion:DeregistrationExpertOpinionVo,
    val vetoUntil:LocalDate,
    val vetoVersion:Int?=null,
    val orgId:String?=null,
    val deregistrationId:String?=null,
    val expertOpinionId:String?=null,
    val vetoStartedOn:LocalDate?=null,
    val proposalArchive:Map<String,Any?>?=null,
    val expertArchive:Map<String,Any?>?=null,
)

/** 否决期限以永久原件核对；旧申请缺失时仍按留存的同件藏品否决原件阻断重启。 */
internal object DeregistrationVetoEvidence {
    fun requireNoVeto(collectionId:String,today:LocalDate,archives:CollectionPermanentArchiveService) {
        val cases=DeregistrationDao.find {Deregistrations.collectionId eq collectionId}.toList()
        cases.forEach {verify(it,archives)}
        val ids=cases.map {it.id.value}.toSet()
        // 旧档案没有独立藏品索引，按退出 VETO 阶段查找冻结身份，不依赖可变申请关联。
        val query=CollectionPermanentArchives.select(CollectionPermanentArchives.id,CollectionPermanentArchives.bizId,CollectionPermanentArchives.snapshot)
            .where {(CollectionPermanentArchives.bizType eq CollectionConst.BIZ_DEREGISTER) and (CollectionPermanentArchives.phase eq "VETO")}
        val vetoes=query.toList()
        val proposals=vetoes.map {it[CollectionPermanentArchives.bizId]}.distinct().chunked(200).flatMap {batch->
            CollectionPermanentArchives.select(CollectionPermanentArchives.bizId,CollectionPermanentArchives.snapshot).where {
                (CollectionPermanentArchives.bizType eq CollectionConst.BIZ_DEREGISTER) and
                    (CollectionPermanentArchives.bizId inList batch) and (CollectionPermanentArchives.phase eq "PROPOSAL")
            }.map {it[CollectionPermanentArchives.bizId] to it[CollectionPermanentArchives.snapshot]}
        }.toMap()
        vetoes.forEach {row->
            val root=try {JsonUtils.mapper.readTree(row[CollectionPermanentArchives.snapshot])}
                catch (_:tools.jackson.core.JacksonException) {if(row[CollectionPermanentArchives.bizId] in ids)fail();null}
            val proposal=proposals[row[CollectionPermanentArchives.bizId]]
            val proposedId=try {proposal?.let {JsonUtils.mapper.readTree(it).get("collectionId")?.asString()}}
                catch (_:tools.jackson.core.JacksonException) {null}
            if(row[CollectionPermanentArchives.bizId] in ids || root?.get("collectionId")?.asString()==collectionId || proposedId==collectionId) {
                val archive=CollectionPermanentArchiveDao[row[CollectionPermanentArchives.id]]
                val original=verifyArchive(archive,archives,DeregistrationDao.findById(archive.bizId))
                if(original.collectionId==collectionId && today.isBefore(original.vetoUntil))
                    throw BadRequestException("该藏品已有专家否决原件，${original.vetoUntil} 前不得重新启动退出程序")
            }
        }
    }

    fun verify(e:DeregistrationDao,archives:CollectionPermanentArchiveService,verifyContent:Boolean=true) {
        val veto=CollectionPermanentArchiveDao.find {(CollectionPermanentArchives.bizType eq CollectionConst.BIZ_DEREGISTER) and
            (CollectionPermanentArchives.bizId eq e.id.value) and (CollectionPermanentArchives.phase eq "VETO")}.singleOrNull()
        val dissent=DeregistrationExpertOpinionDao.find {DeregistrationExpertOpinions.deregistrationId eq e.id.value}.any {!it.content.agree}
        if(veto==null) {if(e.vetoUntil!=null || dissent)fail();return}
        verifyArchive(veto,archives,e,verifyContent)
    }

    private fun verifyArchive(a:CollectionPermanentArchiveDao,archives:CollectionPermanentArchiveService,e:DeregistrationDao?,verifyContent:Boolean=true):DeregistrationVetoOriginal {
        val saved=(if(verifyContent) archives.listForAuthorizedOrg(CollectionConst.BIZ_DEREGISTER,a.bizId,a.orgId) else archives.readForDisplay(CollectionConst.BIZ_DEREGISTER,a.bizId,a.orgId).archives).associateBy {it["phase"] as String}
        val v=try {JsonUtils.fromJson(a.snapshot,DeregistrationVetoOriginal::class.java) ?: fail()}
            catch (_:tools.jackson.core.JacksonException) {fail()}
        if(v.collectionId.isBlank() || v.expertOpinion.agree || v.vetoUntil.isBefore(v.expertOpinion.assessedAt.plusYears(5)))fail()
        val experts=saved.filterKeys {it.startsWith("EXPERT_")}.filterValues {
            JsonUtils.mapper.readTree(it["snapshot"] as String)==JsonUtils.mapper.readTree(JsonUtils.toJson(v.expertOpinion))
        }
        if(experts.size!=1)fail()
        val expert=experts.values.single()
        val expertId=(expert["phase"] as String).removePrefix("EXPERT_")
        val proposal=saved["PROPOSAL"] ?: fail()
        val proposalSnapshot=JsonUtils.mapper.readTree(proposal["snapshot"] as String)
        if(proposalSnapshot.path("collectionId").asString()!=v.collectionId)fail()
        val dossier=proposalSnapshot.path("dossier")
        val proposalFiles=listOf(proposalSnapshot.path("attachmentIds").asString(),
            dossier.path("dispositionPlanFileIds").asString(),dossier.path("legalBasisFileIds").asString(),
            dossier.path("recipientStateEvidenceFileIds").asString()).joinToString(",")
        archives.verifyExisting(CollectionPermanentArchiveDao[proposal["id"] as String],a.orgId,
            CollectionConst.BIZ_DEREGISTER,a.bizId,"PROPOSAL",proposalSnapshot,proposalFiles,verifyContent=verifyContent)
        fun reference(value:Map<String,Any?>?,expected:Map<String,Any?>) {
            val ref=mapOf("id" to expected["id"],"phase" to expected["phase"],"digest" to expected["digest"])
            if(value==null || JsonUtils.mapper.readTree(JsonUtils.toJson(value))!=JsonUtils.mapper.readTree(JsonUtils.toJson(ref)))fail()
        }
        if(v.vetoVersion==null) {
            if(listOf(v.orgId,v.deregistrationId,v.expertOpinionId,v.vetoStartedOn,v.proposalArchive,v.expertArchive).any {it!=null})fail()
        } else {
            if(v.vetoVersion!=1 || v.orgId!=a.orgId || v.deregistrationId!=a.bizId || v.expertOpinionId!=expertId ||
                v.vetoStartedOn!=v.expertOpinion.assessedAt || v.vetoUntil!=v.vetoStartedOn.plusYears(5))fail()
            reference(v.proposalArchive,proposal);reference(v.expertArchive,expert)
        }
        archives.verifyExisting(a,a.orgId,CollectionConst.BIZ_DEREGISTER,a.bizId,"VETO",
            JsonUtils.mapper.readTree(a.snapshot),v.expertOpinion.signedOpinionFileIds,verifyContent=verifyContent)
        archives.verifyExisting(CollectionPermanentArchiveDao[expert["id"] as String],a.orgId,
            CollectionConst.BIZ_DEREGISTER,a.bizId,expert["phase"] as String,
            JsonUtils.mapper.readTree(expert["snapshot"] as String),
            listOf(v.expertOpinion.seniorQualificationFileIds,v.expertOpinion.independenceStatementFileIds,v.expertOpinion.signedOpinionFileIds).joinToString(","),verifyContent=verifyContent)
        e?.let {
            if(it.orgId!=a.orgId || it.collectionId!=v.collectionId || it.vetoUntil!=v.vetoUntil || it.flowStatus!=EnumFlowStatus.REJECT ||
                it.flowInstanceId!=null || it.executedAt!=null || it.announcedAt!=null)fail()
            val dissent=DeregistrationExpertOpinionDao.find {DeregistrationExpertOpinions.deregistrationId eq it.id.value}.filter {!it.content.agree}
            if(dissent.size!=1 || dissent.single().id.value!=expertId || dissent.single().orgId!=a.orgId || dissent.single().content!=v.expertOpinion)fail()
        }
        return v
    }
    private fun fail():Nothing=throw BadRequestException("专家否决、五年限制与永久原件不一致，请核对退出历史")
}
