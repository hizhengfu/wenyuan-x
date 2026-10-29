package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortCheckConstraintName
import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntity
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntityClass
import io.hizhengfu.cloud.collection.common.OrgAuditUIDTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.javatime.datetime
import java.time.LocalDateTime

/** 年度统计报表签章归档。 */
object AnnualReportArchives : OrgAuditUIDTable("col_annual_report_archive") {
    val year = integer("year", checkConstraintName = shortCheckConstraintName("year")).comment("统计年度")
    val statistician = varchar("statistician", 100).comment("统计人")
    val keeperDirector = varchar("keeper_director", 100).comment("保管部主任")
    val museumLeader = varchar("museum_leader", 100).comment("馆负责人")
    val reportAttachmentIds = text("report_attachment_ids").comment("签章报表扫描件 fileId，逗号分隔")
    val reportedBy = varchar("reported_by", 64).nullable().comment("归档操作人")
    val reportedAt = datetime("reported_at").nullable().comment("归档时间")

    init { uniqueIndex(shortIndexName("org_id_year", unique = true), orgId, year) }
}

class AnnualReportArchiveDao(id: EntityID<String>) : OrgAuditUIDEntity(id, AnnualReportArchives) {
    companion object : OrgAuditUIDEntityClass<AnnualReportArchiveDao>(AnnualReportArchives)
    var year by AnnualReportArchives.year
    var statistician by AnnualReportArchives.statistician
    var keeperDirector by AnnualReportArchives.keeperDirector
    var museumLeader by AnnualReportArchives.museumLeader
    var reportAttachmentIds by AnnualReportArchives.reportAttachmentIds
    var reportedBy by AnnualReportArchives.reportedBy
    var reportedAt by AnnualReportArchives.reportedAt

    fun toModel() = AnnualReportArchiveVo(id.value, orgId, year, statistician, keeperDirector, museumLeader, reportAttachmentIds, reportedBy, reportedAt)
}

data class AnnualReportArchiveVo(
    val id: String? = null,
    val orgId: String? = null,
    val year: Int? = null,
    val statistician: String? = null,
    val keeperDirector: String? = null,
    val museumLeader: String? = null,
    val reportAttachmentIds: String? = null,
    val reportedBy: String? = null,
    val reportedAt: LocalDateTime? = null,
)
