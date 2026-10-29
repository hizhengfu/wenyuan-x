package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntity
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntityClass
import io.hizhengfu.cloud.collection.common.OrgAuditUIDTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID

object ReturnComplianceRecords : OrgAuditUIDTable("col_return_compliance_record") {
    val returnId = varchar("return_id",64).index(shortIndexName("return_id"))
    val kind = varchar("kind",32)
    val content = text("content")
}
class ReturnComplianceRecordDao(id: EntityID<String>) : OrgAuditUIDEntity(id,ReturnComplianceRecords) {
    companion object : OrgAuditUIDEntityClass<ReturnComplianceRecordDao>(ReturnComplianceRecords)
    var returnId by ReturnComplianceRecords.returnId
    var kind by ReturnComplianceRecords.kind
    var content by ReturnComplianceRecords.content
}
