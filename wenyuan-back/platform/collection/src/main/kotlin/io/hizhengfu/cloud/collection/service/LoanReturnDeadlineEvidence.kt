package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.dao.LoanDao
import io.hizhengfu.cloud.collection.domain.EnumLoanMonitoringKind
import java.time.LocalDateTime

/** 盘点按逐件最早有效期限识别逾期；日期型催还允许在该日内归还。 */
internal object LoanReturnDeadlineEvidence {
    fun forCollection(loan:LoanDao,collectionId:String):LocalDateTime? {
        val agreement=LoanOverseasExtensionEvidence.effectiveReturnAt(loan) ?: loan.returnDate?.plusDays(1)?.atStartOfDay()
        val recall=CollectionFragilityEvidence.loanMonitoring(loan.orgId,CollectionConst.BIZ_LOAN,loan.id.value)
            .map {it.second}.filter {it.kind==EnumLoanMonitoringKind.RECALL && collectionId in it.collectionIds}
            .mapNotNull {it.recallReturnBy}.minOrNull()?.plusDays(1)?.atStartOfDay()
        return listOfNotNull(agreement,recall).minOrNull()
    }
}
