package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq

/** 所有期限使用方均校验永久原件和延期链，不改写原协议。 */
internal object LoanOverseasExtensionEvidence {
    private fun overseasApprovalLevel(e: LoanDao): EnumAuthorityLevel {
        val p=e.dossier!!.overseasExhibition ?: throw BadRequestException("缺少完整出境展览方案")
        return if(p.firstLevelExhibits>120 || p.firstLevelExhibits.toLong()*5>p.totalRelicExhibits) EnumAuthorityLevel.STATE_COUNCIL else EnumAuthorityLevel.NATIONAL
    }
    fun requireRecordOriginal(e:LoanDao,row:LoanComplianceRecordDao) {
        val a=CollectionPermanentArchiveDao.find { (CollectionPermanentArchives.bizType eq CollectionConst.BIZ_LOAN) and
            (CollectionPermanentArchives.bizId eq e.id.value) and (CollectionPermanentArchives.phase eq "${row.kind}_${row.id.value}") }.singleOrNull()
            ?: throw BadRequestException("借用行政或延期永久原件缺失")
        if(a.orgId!=e.orgId || a.digest!=CollectionCareEvidence.digest(a.snapshot) || JsonUtils.mapper.readTree(a.snapshot)!=JsonUtils.mapper.readTree(row.content))
            throw BadRequestException("借用行政或延期记录与永久原件不一致")
    }
    fun extensions(e:LoanDao):List<Pair<LoanComplianceRecordDao,LoanOverseasExtensionRecordVo>> {
        val rows=LoanComplianceRecordDao.find {LoanComplianceRecords.loanId eq e.id.value}.toList()
        if(rows.any {it.orgId!=e.orgId})throw BadRequestException("借用行政或延期记录所属馆与原业务不一致")
        var previousId:String?=null
        var previousEnd=e.dossier?.overseasExhibition?.exhibitionEndsOn
        var previousReturn=e.dossier?.agreedReturnAt
        var originalApproval:String?=null
        return rows.filter {it.kind=="OVERSEAS_EXTENSION"}.sortedBy {it.id.value}.map { row ->
            requireRecordOriginal(e,row)
            val value=JsonUtils.fromJson(row.content,LoanOverseasExtensionRecordVo::class.java) ?: throw BadRequestException("延期原件内容缺失")
            val request=value.request
            val approval=rows.singleOrNull {it.kind=="ADMINISTRATIVE" && it.id.value==request.originalApprovalId}
                ?: throw BadRequestException("延期对应的原出境批准记录缺失")
            requireRecordOriginal(e,approval)
            val administrative=JsonUtils.fromJson(approval.content,LoanAdministrativeVo::class.java)!!
            if(request.previousId!=previousId || value.previousExhibitionEndsOn!=previousEnd || value.previousReturnAt!=previousReturn ||
                (originalApproval!=null && originalApproval!=request.originalApprovalId) || administrative.authorityName!=request.authorityName ||
                administrative.action!=EnumAdministrativeAction.APPROVAL || administrative.level!=overseasApprovalLevel(e))
                throw BadRequestException("延期来源、顺序或原审批机关与永久记录不一致")
            previousId=row.id.value;previousEnd=request.exhibitionEndsOn;previousReturn=request.agreedReturnAt;originalApproval=request.originalApprovalId
            row to value
        }
    }
    fun effectiveReturnAt(e:LoanDao) = extensions(e).lastOrNull()?.second?.request?.agreedReturnAt ?: e.dossier?.agreedReturnAt
}
