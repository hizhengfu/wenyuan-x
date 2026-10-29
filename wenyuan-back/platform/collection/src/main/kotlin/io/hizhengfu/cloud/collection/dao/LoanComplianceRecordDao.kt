package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntity
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntityClass
import io.hizhengfu.cloud.collection.common.OrgAuditUIDTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID

/** 行政及在借监管证据只追加，正文和原件同步进入永久档案。 */
object LoanComplianceRecords : OrgAuditUIDTable("col_loan_compliance_record") {
    val loanId= varchar("loan_id",64).index(shortIndexName("loan_id"))
    val kind= varchar("kind",32)
    val content= text("content")
}
class LoanComplianceRecordDao(id:EntityID<String>) : OrgAuditUIDEntity(id,LoanComplianceRecords) {
    companion object : OrgAuditUIDEntityClass<LoanComplianceRecordDao>(LoanComplianceRecords)
    var loanId by LoanComplianceRecords.loanId
    var kind by LoanComplianceRecords.kind
    var content by LoanComplianceRecords.content
}
