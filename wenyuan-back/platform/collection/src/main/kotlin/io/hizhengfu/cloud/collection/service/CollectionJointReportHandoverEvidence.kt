package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import io.hizhengfu.cloud.collection.service.CollectionJointSafetyEvidence as J
import io.hizhengfu.cloud.collection.service.CollectionCareEvidence as C

internal object CollectionJointReportHandoverEvidence {
    const val PREFIX = "REPORT_HANDOVER_"
    const val CURRENT_BASIS = "DIRECTOR_CURRENT_REPORT_BASIS"
    data class Entry(val record: CollectionJointReportHandoverRecord, val signatures: MutableMap<String,CollectionCareSignature> = linkedMapOf()) {
        val complete get() = signatures.keys.containsAll(setOf("KEEPER","DIRECTOR"))
    }
    fun actors(e: Entry) = mapOf("KEEPER" to e.record.request.keeperUserId,"DIRECTOR" to e.record.request.directorUserId)
    fun files(v: CollectionJointReportHandoverVo) = "${v.appointmentFileIds},${v.handoverFileIds}"
    fun roundDigest(row: CollectionJointSafetyRoundDao): String {
        J.exact(row.orgId,row.id.value,"ROUND",J.roundValue(row))
        return CollectionPermanentArchiveDao.find {(CollectionPermanentArchives.bizType eq J.BIZ) and (CollectionPermanentArchives.bizId eq row.id.value) and (CollectionPermanentArchives.phase eq "ROUND")}.single().digest
    }
    fun currentBasis(row: CollectionJointSafetyRoundDao): CollectionJointCurrentReportRecord? = try {
        val a=CollectionPermanentArchiveDao.find {(CollectionPermanentArchives.bizType eq J.BIZ) and (CollectionPermanentArchives.bizId eq row.id.value) and (CollectionPermanentArchives.phase eq CURRENT_BASIS)}.singleOrNull()
        a?.let {
            val v=JsonUtils.fromJson(it.snapshot,CollectionJointCurrentReportRecord::class.java) ?: C.fail("现任负责人报告职责依据缺失")
            if(it.orgId!=row.orgId || it.digest!=C.digest(it.snapshot) || v.orgId!=row.orgId || v.requisitionId!=row.requisitionId || v.roundId!=row.id.value ||
                v.request.expectedRoundDigest!=roundDigest(row) || v.request.report!=row.report || v.request.report.directorUserId==CollectionJointSafetyPlanDao[row.planId].content.directorUserId ||
                v.request.report.reporterUserId==v.request.report.directorUserId || v.request.changeReason.isBlank() || v.registeredAt.isBefore(v.request.report.reportedAt)) C.fail("现任负责人报告与原检查或任职依据不一致")
            CollectionResponsibilityArchiveEvidence.references(it,v.request.appointmentFileIds);v
        }
    } catch(_:tools.jackson.core.JacksonException) {C.fail("现任负责人报告职责原件无法解析")}
    fun report(row: CollectionJointSafetyRoundDao): CollectionPermanentArchiveDao {
        val value=row.report ?: C.fail("须先保留实际形成的共同检查问题报告")
        J.exact(row.orgId,row.id.value,"DIRECTOR_REPORT",value)
        return CollectionPermanentArchiveDao.find {(CollectionPermanentArchives.bizType eq J.BIZ) and (CollectionPermanentArchives.bizId eq row.id.value) and (CollectionPermanentArchives.phase eq "DIRECTOR_REPORT")}.single().also {
            CollectionResponsibilityArchiveEvidence.references(it,value.signedFileIds)
        }
    }
    fun entries(row: CollectionJointSafetyRoundDao): List<Entry> = try {
        val rows=CollectionPermanentArchiveDao.find {(CollectionPermanentArchives.bizType eq J.BIZ) and (CollectionPermanentArchives.bizId eq row.id.value)}
            .filter {it.phase.startsWith(PREFIX)}.sortedWith(compareBy({it.createTime},{it.id.value}))
        val result=mutableListOf<Entry>()
        for(a in rows) {
            if(a.orgId!=row.orgId || a.digest!=C.digest(a.snapshot))C.fail("报告移交所属馆或原件摘要不一致")
            if(a.phase.startsWith("${PREFIX}P_")) {
                val v=JsonUtils.fromJson(a.snapshot,CollectionJointReportHandoverRecord::class.java) ?: C.fail("报告移交登记原件缺失")
                val original=report(row)
                if(v.orgId!=row.orgId || v.roundId!=row.id.value || v.requisitionId!=row.requisitionId || a.phase!="${PREFIX}P_${v.id}" ||
                    v.request.previousId!=result.lastOrNull()?.record?.id || result.any {it.record.id==v.id || it.complete} ||
                    v.reportArchiveId!=original.id.value || v.request.expectedReportDigest!=original.digest ||
                    v.registeredBy!=v.request.keeperUserId || v.request.keeperUserId==v.request.directorUserId || v.request.directorUserId==row.report!!.directorUserId ||
                    v.request.handedAt.isBefore(row.report!!.reportedAt) || v.registeredAt.isBefore(v.request.handedAt) ||
                    result.lastOrNull()?.record?.registeredAt?.let {v.registeredAt.isBefore(it)}==true) C.fail("报告移交版本、接收人员或来源时间不一致")
                CollectionResponsibilityArchiveEvidence.references(a,files(v.request));result.add(Entry(v))
            } else {
                val entry=result.lastOrNull() ?: C.fail("报告移交确认缺少登记原件")
                val role=actors(entry).keys.singleOrNull {a.phase=="$PREFIX${it}_${entry.record.id}"} ?: C.fail("报告移交确认不属于最新版本")
                val sig=JsonUtils.fromJson(a.snapshot,CollectionCareSignature::class.java) ?: C.fail("报告移交本人确认缺失")
                if(entry.signatures.containsKey(role) || sig.actorUserId!=actors(entry)[role] || sig.confirmedAt.isBefore(entry.record.registeredAt) ||
                    (role=="DIRECTOR" && entry.signatures["KEEPER"]?.confirmedAt?.let {sig.confirmedAt.isBefore(it)}!=false)) C.fail("报告移交本人确认人员或顺序不一致")
                CollectionResponsibilityArchiveEvidence.references(a,sig.record.fileIds);entry.signatures[role]=sig
            }
        }
        if(row.director!=null && result.any {it.complete})C.fail("原收悉与报告移交同时完成，请核对真实处理依据")
        result
    } catch(_:tools.jackson.core.JacksonException) {C.fail("报告移交原件无法解析，请核对历史记录")}
}
