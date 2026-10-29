package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortCheckConstraintName

import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.domain.*
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.json.json

object AnnualReportEvidenceRecords : OrgAuditUIDTable("col_annual_report_evidence") {
    val year=integer("year", checkConstraintName = shortCheckConstraintName("year"))
    val version=integer("version", checkConstraintName = shortCheckConstraintName("version"))
    val signed=json("signed",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,AnnualReportSignedVo::class.java)!!})
    val calculated=json("calculated",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,AnnualLedgerPreviewVo::class.java)!!})
    val submission=json("submission",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,AnnualReportSubmissionVo::class.java)!!}).nullable()
    val filing=json("filing",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,AnnualReportFilingVo::class.java)!!}).nullable()
    init { uniqueIndex(shortIndexName("org_year_version",unique=true),orgId,year,version) }
}
class AnnualReportEvidenceDao(id: EntityID<String>) : OrgAuditUIDEntity(id,AnnualReportEvidenceRecords) {
    companion object : OrgAuditUIDEntityClass<AnnualReportEvidenceDao>(AnnualReportEvidenceRecords)
    var year by AnnualReportEvidenceRecords.year
    var version by AnnualReportEvidenceRecords.version
    var signed by AnnualReportEvidenceRecords.signed
    var calculated by AnnualReportEvidenceRecords.calculated
    var submission by AnnualReportEvidenceRecords.submission
    var filing by AnnualReportEvidenceRecords.filing
}
