package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.eq
import java.time.LocalDateTime

internal object LoanExhibitionEndEvidence {
    fun records(e:LoanDao):List<Pair<LoanComplianceRecordDao,LoanExhibitionEndRecordVo>> {
        var previous:String?=null
        return LoanComplianceRecordDao.find {LoanComplianceRecords.loanId eq e.id.value}.toList().also {rows->if(rows.any {it.orgId!=e.orgId})throw BadRequestException("展览结束原件所属馆不一致")}.filter {it.kind=="EXHIBITION_END"}.sortedBy {it.id.value}.map {row ->
            LoanOverseasExtensionEvidence.requireRecordOriginal(e,row)
            val value=JsonUtils.fromJson(row.content,LoanExhibitionEndRecordVo::class.java) ?: throw BadRequestException("展览实际结束原件缺失")
            if(value.request.previousId!=previous)throw BadRequestException("展览结束事实更正链与原件不一致")
            previous=row.id.value
            row to value
        }
    }
    fun requireDisplayStart(loan:LoanDao,startedAt:LocalDateTime) {
        val end=records(loan).lastOrNull()?.second?.request?.actualEndedOn ?: return
        if(startedAt.toLocalDate().isAfter(end))throw BadRequestException("展览已经实际结束，不能登记结束日期之后的新展出；回程运输须按运输事项办理")
    }
}
