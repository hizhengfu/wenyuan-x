package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import java.time.LocalDateTime

/**
 * 来源业务先完成馆级授权；这里只重放封存的事故版本，不暴露事故内容或代办解除。
 * 以指定主要负责人签署为截止点，后续合法补录不会替换原解除依据。
 * 清点来源属于核心内置来源；其他业务仍使用事故服务注入的来源提供者。
 */
internal class CollectionIncidentArchiveEvidence(
    private val archives: CollectionPermanentArchiveService,
    private val sources: CollectionIncidentSourceService = CollectionIncidentSourceService(),
) {
    private fun rows(r: CollectionIncidentDao) = CollectionIncidentRecordDao.find { CollectionIncidentRecords.incidentId eq r.id.value }.sortedBy { it.sequence }.also { rows ->
        if(rows.any {it.orgId!=r.orgId})fail("事故记录所属馆与案卷不一致")
    }
    private fun originalFiles(row: CollectionIncidentRecordDao): String = when (row.kind) {
        EnumIncidentRecordKind.FACTS -> value<IncidentFactsSnapshot>(row).facts.let { v ->
            (listOfNotNull(v.evidenceFileIds, v.replacementFileIds) + v.items.mapNotNull { it.imageFileIds }).joinToString(",")
        }
        EnumIncidentRecordKind.REPORT -> value<IncidentReportVo>(row).fileIds
        EnumIncidentRecordKind.HANDLING -> value<IncidentHandlingVo>(row).let { v ->
            (listOfNotNull(v.investigationFileIds, v.signedHandlingFileIds, v.replacementFileIds) +
                v.items.map { it.fileIds } + v.authorityResults.map { it.fileIds }).joinToString(",")
        }
        EnumIncidentRecordKind.CONFIRMATION -> value<IncidentConfirmationSnapshot>(row).request.fileIds
    }
    fun verify(r: CollectionIncidentDao, row: CollectionIncidentRecordDao,verifyContent:Boolean=true) {
        val a = CollectionPermanentArchiveDao.find { (CollectionPermanentArchives.bizType eq CollectionConst.BIZ_INCIDENT) and (CollectionPermanentArchives.bizId eq r.id.value) and (CollectionPermanentArchives.phase eq "${row.kind}_${row.id.value}") }.singleOrNull() ?: fail("缺少事故永久原件")
        val snapshot = JsonUtils.mapper.readTree(a.snapshot) ?: fail("事故永久原件无法解析")
        if (row.orgId != r.orgId || a.orgId != r.orgId || a.digest != digest(a.snapshot) || snapshot != JsonUtils.mapper.readTree(row.content)) fail("事故案卷与永久原件不一致")
        archives.verifyExisting(a, r.orgId, CollectionConst.BIZ_INCIDENT, r.id.value,
            "${row.kind}_${row.id.value}", snapshot, originalFiles(row),verifyContent=verifyContent)
    }
    fun clearanceProof(
        id:String,orgId:String,collectionId:String,source:IncidentSourceVo,at:LocalDateTime,
        keeperUserId:String,leaderUserId:String,confirmationId:String,
        requiredReports:List<IncidentReportRequirement>, purpose:String="归还",
    ):Map<String,String> {
        val r=CollectionIncidentDao.findById(id) ?: fail("事故案卷不存在")
        if(r.orgId!=orgId)fail("${purpose}事故原件所属馆不一致")
        val all=rows(r)
        val director=all.singleOrNull {it.id.value==confirmationId && it.kind==EnumIncidentRecordKind.CONFIRMATION}
            ?: fail("${purpose}事故原件缺少当次主要负责人确认")
        val historical=all.filter {it.sequence<=director.sequence}
        historical.forEach {verify(r,it)}
        val evidence=historical.filter {it.kind!=EnumIncidentRecordKind.CONFIRMATION}
        val evidenceIds=evidence.map {it.id.value}
        val revision=digest(json(evidence.map {mapOf("id" to it.id.value,"kind" to it.kind,"content" to it.content)}))
        val confirmation=value<IncidentConfirmationSnapshot>(director)
        if(confirmation.role!="DIRECTOR" || confirmation.actorUserId!=leaderUserId ||
            confirmation.request.basisRevision!=revision || confirmation.evidenceIds!=evidenceIds || confirmation.request.signedAt.isAfter(at))
            fail("${purpose}事故原件的主要负责人确认与当次依据不一致")
        val keeper=historical.lastOrNull {row -> row.kind==EnumIncidentRecordKind.CONFIRMATION &&
            value<IncidentConfirmationSnapshot>(row).let {it.role=="KEEPER" && it.actorUserId==keeperUserId && it.request.basisRevision==revision}}
            ?: fail("${purpose}事故原件缺少当次保管负责人确认")
        val keeperProof=value<IncidentConfirmationSnapshot>(keeper)
        if(keeperProof.evidenceIds!=evidenceIds || keeperProof.request.signedAt.isAfter(confirmation.request.signedAt))
            fail("${purpose}事故原件的两级确认依据或顺序不一致")
        val f=historical.lastOrNull {it.kind==EnumIncidentRecordKind.FACTS} ?: fail("${purpose}事故原件缺少当次事故事实")
        val facts=value<IncidentFactsSnapshot>(f)
        if(facts.facts.source!=source || facts.items.none {it.item.collectionId==collectionId} ||
            sources.snapshot(orgId,source,facts.items.map {it.item})!=facts.sourceEvidence)
            fail("${purpose}事故原件与来源异常事实不一致")
        val h=historical.lastOrNull {it.kind==EnumIncidentRecordKind.HANDLING && value<IncidentHandlingVo>(it).factsId==f.id.value}
            ?: fail("${purpose}事故原件缺少当次处理结果")
        val result=value<IncidentHandlingVo>(h)
        if(result.keeperUserId!=keeperUserId || result.leaderUserId!=leaderUserId ||
            result.items.none {it.collectionId==collectionId && it.disposition==EnumIncidentDisposition.PRESENT_SAFE && it.authenticityConfirmed})
            fail("${purpose}事故原件未确认本件真实且安全")
        val reports=historical.filter {it.kind==EnumIncidentRecordKind.REPORT}.map {value<IncidentReportVo>(it)}
        requiredReports.forEach {required ->
            if(required.collectionId!=collectionId || reports.none {it.factsId==f.id.value && it.target==required.target &&
                it.kind==required.kind && collectionId in it.collectionIds && !it.reportedAt.isAfter(at) &&
                (required.kind!=EnumIncidentReportKind.RESULT_FILING || it.handlingId==h.id.value)} ||
                (required.kind==EnumIncidentReportKind.INCIDENT && required.target !in setOf(EnumIncidentTarget.KEEPER,EnumIncidentTarget.DIRECTOR) &&
                    result.authorityResults.none {it.target==required.target && collectionId in it.collectionIds}))
                fail("${purpose}事故原件缺少当次实际报告或核查处理结果")
        }
        return mapOf("incidentId" to id,"factsId" to f.id.value,"handlingId" to h.id.value,
            "confirmationId" to director.id.value,"basisRevision" to revision,"confirmedAt" to confirmation.request.signedAt.toString())
    }
    private inline fun <reified T> value(row: CollectionIncidentRecordDao): T = JsonUtils.fromJson(row.content, T::class.java)!!
    private fun json(value: Any) = JsonUtils.toJson(value)!!
    private fun digest(value: String) = CollectionCareEvidence.digest(value)
    private fun fail(message: String): Nothing = throw BadRequestException(message)
}
