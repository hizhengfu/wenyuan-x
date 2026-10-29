package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

@Component
@Transactional
class CollectionManagementRulesService(
    private val scope: CollectionScopeResolver,
    private val users: CollectionResponsibleUserService,
    private val archives: CollectionPermanentArchiveService,
) {
    companion object { const val BIZ = "COLLECTION_MANAGEMENT_RULES" }

    private val readAuthorization = CollectionFileReadAuthorization(scope, CollectionActorDirectory(scope))

    fun detail(orgId: String): Map<String, Any?> {
        readAuthorization.requireBusinessReadable(orgId, setOf("ADMIN", CollectionConst.ADMIN, CollectionConst.ORG_MANAGE, CollectionConst.SELECT), CollectionConst.CROSS_ORG, "藏品管理制度")
        val records = records(orgId)
        val current = records.lastOrNull { !it.rules.effectiveOn.isAfter(LocalDate.now()) }
        return mapOf("orgId" to orgId, "currentId" to current?.id?.value, "records" to records.map { row ->
            mapOf("id" to row.id.value, "version" to row.version, "rules" to row.rules,
                "missingTopics" to EnumCollectionRuleTopic.entries.filter { row.rules.topicReferences[it].isNullOrBlank() },
                "submission" to row.submission, "filing" to row.filing, "archives" to archives.readForDisplay(BIZ, row.id.value, orgId).archives)
        })
    }

    /** 只允许读取本馆该制度版本中已核验的永久档案成员。 */
    fun requireOriginal(orgId: String, id: String, archiveId: String, fileId: String) {
        readAuthorization.requireBusinessReadable(orgId,
            setOf("ADMIN", CollectionConst.ADMIN, CollectionConst.ORG_MANAGE, CollectionConst.SELECT),
            CollectionConst.CROSS_ORG, "藏品管理制度")
        if (!fileId.matches(Regex("[0-9]{15,20}"))) fail("制度原件文件标识不正确")
        CollectionManagementRulesDao.find {
            (CollectionManagementRulesRecords.id eq id) and (CollectionManagementRulesRecords.orgId eq orgId)
        }.singleOrNull() ?: fail("该收藏单位的制度版本不存在")
        val archive = archives.listForAuthorizedOrg(BIZ, id, orgId).singleOrNull { it["id"] == archiveId }
            ?: fail("制度版本的永久档案不存在")
        @Suppress("UNCHECKED_CAST")
        val files = archive["files"] as List<Map<String, Any?>>
        if (files.none { it["fileId"] == fileId }) fail("该文件不是本制度版本的归档原件")
    }

    fun register(orgId: String, value: CollectionManagementRulesVo): String {
        lockOrg(orgId)
        val previous = records(orgId).lastOrNull()
        if (previous?.rules == value) {
            requireIssued(previous)
            requireWrite(orgId)
            return previous.id.value
        }
        if (listOf(value.title, value.documentNo, value.originalFileIds, value.workflowFileIds).any(String::isBlank)) fail("请填写实际制度名称、文号、制度原件及配套内部流程原件")
        if (value.issuedAt.isAfter(LocalDateTime.now())) fail("不能预填制度已印发的未来时间")
        if (value.topicReferences.values.any(String::isBlank)) fail("已标明覆盖的事项须填写制度原件中的章节或条款位置")
        users.requireMuseumMember(value.responsibleUserId, orgId)
        users.requireRole(value.responsibleUserId, orgId, CollectionConst.ROLE_DIRECTOR)
        if (previous != null && (value.replacementReason.isNullOrBlank() || value.replacementFileIds.isNullOrBlank() || value.issuedAt.isBefore(previous.rules.issuedAt))) {
            fail("修订制度须追加新版本，说明修订原因并保留实际修订原件和时间")
        }
        val row = CollectionManagementRulesDao.new { this.orgId = orgId; version = (previous?.version ?: 0) + 1; rules = value }
        archives.seal(orgId, BIZ, row.id.value, "ISSUED", value, files(value))
        requireWrite(orgId)
        return row.id.value
    }

    fun submission(orgId: String, id: String, value: CollectionRulesSubmissionVo): String {
        val row = record(orgId, id)
        if (row.submission != null) {
            if (row.submission == value) {
                requireIssued(row)
                requireSubmission(row)
                requireWrite(orgId)
                return id
            }
            fail("实际报送凭据不能覆盖，请登记新的制度修订版本")
        }
        if (listOf(value.authorityName, value.reportNo, value.submissionFileIds).any(String::isBlank)) fail("请填写实际主管文物行政部门、报送编号和凭据原件")
        if (value.submittedAt.isBefore(row.rules.issuedAt) || value.submittedAt.isAfter(LocalDateTime.now())) fail("报送时间须在实际制度印发之后且不得填未来时间")
        requireIssued(row)
        row.submission = value
        archives.seal(orgId, BIZ, id, "SUBMITTED", value, value.submissionFileIds)
        requireWrite(orgId)
        return id
    }

    fun filing(orgId: String, id: String, value: CollectionRulesFilingVo): String {
        val row = record(orgId, id)
        if (row.filing != null) {
            if (row.filing == value) {
                requireIssued(row)
                requireSubmission(row)
                requirePhase(row, "FILED")
                archives.seal(orgId, BIZ, id, "FILED", value, value.receiptFileIds)
                requireWrite(orgId)
                return id
            }
            fail("制度备案回执不能覆盖，请保留旧回执并另建实际修订版本")
        }
        val submission = row.submission ?: fail("请先登记实际向主管文物行政部门报送的凭据")
        if (value.authorityName != submission.authorityName || value.filingNo.isBlank() || value.receiptFileIds.isBlank()) fail("请核实同一主管文物行政部门的实际备案回执及编号")
        if (value.filedAt.isBefore(submission.submittedAt) || value.filedAt.isAfter(LocalDateTime.now())) fail("备案回执时间须在实际报送之后且不得填写未来时间")
        requireIssued(row)
        requireSubmission(row)
        row.filing = value
        archives.seal(orgId, BIZ, id, "FILED", value, value.receiptFileIds)
        requireWrite(orgId)
        return id
    }

    private fun records(orgId: String) = CollectionManagementRulesDao.find { CollectionManagementRulesRecords.orgId eq orgId }.sortedBy { it.version }
    private fun lockOrg(orgId: String) {
        requireWrite(orgId)
        val org = CollectionOrgDao.find { CollectionOrgs.id eq orgId }.forUpdate().singleOrNull() ?: fail("收藏单位不存在")
        org.refresh()
        requireWrite(orgId)
    }
    private fun requireWrite(orgId: String) = CollectionCurrentOperationAccess.require(
        scope, orgId, true, setOf(CollectionConst.ORG_MANAGE), "藏品管理制度",
    )

    private fun record(orgId: String, id: String): CollectionManagementRulesDao {
        lockOrg(orgId)
        return CollectionManagementRulesDao.find { (CollectionManagementRulesRecords.id eq id) and (CollectionManagementRulesRecords.orgId eq orgId) }
            .forUpdate().singleOrNull() ?: fail("该收藏单位的制度版本不存在")
    }
    private fun files(value: CollectionManagementRulesVo) = listOfNotNull(value.originalFileIds, value.workflowFileIds, value.replacementFileIds).joinToString(",")
    private fun requirePhase(row: CollectionManagementRulesDao, phase: String) {
        if (archives.list(BIZ, row.id.value).none { it["phase"] == phase }) fail("缺少已归档的制度原始阶段，请核实原件")
    }
    private fun requireIssued(row: CollectionManagementRulesDao) {
        requirePhase(row, "ISSUED")
        archives.seal(row.orgId, BIZ, row.id.value, "ISSUED", row.rules, files(row.rules))
    }
    private fun requireSubmission(row: CollectionManagementRulesDao) {
        val submission = row.submission ?: fail("缺少已登记的制度报送凭据")
        requirePhase(row, "SUBMITTED")
        archives.seal(row.orgId, BIZ, row.id.value, "SUBMITTED", submission, submission.submissionFileIds)
    }
    private fun fail(message: String): Nothing = throw BadRequestException(message)
}
