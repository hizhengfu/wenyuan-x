package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortCheckConstraintName

import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.collection.common.*
import org.jetbrains.exposed.v1.core.dao.id.EntityID

/** 年度报告按实际报送版本追加，不覆盖先前签署与回执。 */
object InventoryAnnualReports : OrgAuditUIDTable("col_inventory_annual_report") {
    val year = integer("report_year", checkConstraintName = shortCheckConstraintName("report_year"))
    val version = integer("report_version", checkConstraintName = shortCheckConstraintName("report_version"))
    val content = text("report_content")
    val coverage = text("coverage_snapshot")
    init { uniqueIndex(shortIndexName("org_year_version", unique = true), orgId, year, version) }
}
class InventoryAnnualReportDao(id: EntityID<String>) : OrgAuditUIDEntity(id, InventoryAnnualReports) {
    companion object : OrgAuditUIDEntityClass<InventoryAnnualReportDao>(InventoryAnnualReports)
    var year by InventoryAnnualReports.year
    var version by InventoryAnnualReports.version
    var content by InventoryAnnualReports.content
    var coverage by InventoryAnnualReports.coverage
}
