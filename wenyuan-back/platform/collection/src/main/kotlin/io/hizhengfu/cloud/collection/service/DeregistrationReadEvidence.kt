package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.select

/** 查阅退出材料时核对已固定的方案和每份独立原件，不以当前主档重建历史材料。 */
internal object DeregistrationReadEvidence {
    fun verify(e:DeregistrationDao,archives:CollectionPermanentArchiveService,verifyContent:Boolean=true) {
        val id=e.id.value
        if(DeregistrationExpertOpinions.select(DeregistrationExpertOpinions.id).where {(DeregistrationExpertOpinions.deregistrationId eq id) and (DeregistrationExpertOpinions.orgId neq e.orgId)}.any() ||
            DeregistrationNotices.select(DeregistrationNotices.id).where {(DeregistrationNotices.deregistrationId eq id) and (DeregistrationNotices.orgId neq e.orgId)}.any() ||
            CollectionAdministrativeRecords.select(CollectionAdministrativeRecords.id).where {(CollectionAdministrativeRecords.bizType eq CollectionConst.BIZ_DEREGISTER) and (CollectionAdministrativeRecords.bizId eq id) and (CollectionAdministrativeRecords.orgId neq e.orgId)}.any())fail()
        val saved=(if(verifyContent) archives.listForAuthorizedOrg(CollectionConst.BIZ_DEREGISTER,id,e.orgId) else archives.readForDisplay(CollectionConst.BIZ_DEREGISTER,id,e.orgId).archives).associateBy {it["phase"] as String}
        val expected=linkedSetOf<String>()
        fun check(phase:String,content:Any,files:String) {
            expected+=phase
            val original=saved[phase] ?: fail()
            val frozen=JsonUtils.mapper.readTree(original["snapshot"] as String)
            if(frozen!=JsonUtils.mapper.readTree(JsonUtils.toJson(content)))fail()
            archives.verifyExisting(CollectionPermanentArchiveDao[original["id"] as String],e.orgId,
                CollectionConst.BIZ_DEREGISTER,id,phase,frozen,files,verifyContent=verifyContent)
        }
        DeregistrationExpertOpinionDao.find {DeregistrationExpertOpinions.deregistrationId eq id}.forEach {row->
            val v=row.content
            if(row.expertKey!=v.expertKey)fail()
            check("EXPERT_${row.id.value}",v,listOf(v.seniorQualificationFileIds,v.independenceStatementFileIds,v.signedOpinionFileIds).joinToString(","))
        }
        DeregistrationNoticeDao.find {DeregistrationNotices.deregistrationId eq id}.forEach {row->
            val v=row.content
            check("NOTICE_${row.id.value}",v,listOf(v.publicContentFileIds,v.publicationEvidenceFileIds).joinToString(","))
        }
        CollectionAdministrativeRecordDao.find {(CollectionAdministrativeRecords.bizType eq CollectionConst.BIZ_DEREGISTER) and (CollectionAdministrativeRecords.bizId eq id)}.forEach {row->
            check("ADMIN_${row.id.value}",row.content,row.content.fileIds)
        }
        val actual=saved.keys.filter {it.startsWith("EXPERT_") || it.startsWith("NOTICE_") || it.startsWith("ADMIN_")}.toSet()
        if(actual!=expected)fail()
        if(expected.isNotEmpty() || "PROPOSAL" in saved) {
            val proposal=saved["PROPOSAL"] ?: fail()
            val printFields=DeregistrationPrintEvidence.fields(e,JsonUtils.mapper.readTree(proposal["snapshot"] as String))
            check("PROPOSAL",mapOf("collectionId" to e.collectionId,"baseline" to e.baseline,"reason" to e.reason,"detail" to e.detail,"dossier" to e.dossier,"createBy" to e.createBy)+printFields,
                listOfNotNull(e.attachmentIds,e.dossier?.dispositionPlanFileIds,e.dossier?.legalBasisFileIds,e.dossier?.recipientStateEvidenceFileIds).joinToString(","))
        }
        DeregistrationLifecycleEvidence.verify(e,archives,saved,verifyContent)
        DeregistrationVetoEvidence.verify(e,archives,verifyContent)
    }
    private fun fail():Nothing=throw BadRequestException("退出方案、专家、公示或行政材料与归档原件不一致，请核对原始档案")
}
