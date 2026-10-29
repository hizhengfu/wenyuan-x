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
class LedgerReconciliationService(
    private val scope: CollectionScopeResolver, private val users: CollectionResponsibleUserService,
    private val books: PaperLedgerService, private val archives: CollectionPermanentArchiveService,
    private val retainedLedgers: CollectionRetainedLedgerService,
) {
    companion object { const val BIZ = "COLLECTION_LEDGER_RECONCILIATION" }
    private val readAuthorization = CollectionFileReadAuthorization(scope, CollectionActorDirectory(scope))

    fun collections(orgId: String): List<Map<String, String>> {
        readAuthorization.requireBusinessReadable(orgId, setOf("ADMIN", CollectionConst.ADMIN, CollectionConst.LEDGER_FINANCE), CollectionConst.CROSS_ORG, "财务账核对候选")
        return CollectionLedgerReferenceChoices.list(orgId, retainedLedgers)
    }

    fun list(orgId: String): List<Map<String, Any?>> {
        readAuthorization.requireBusinessReadable(orgId, setOf("ADMIN", CollectionConst.ADMIN, CollectionConst.LEDGER_FINANCE), CollectionConst.CROSS_ORG, "财务账核对原件")
        return LedgerReconciliationDao.find { LedgerReconciliations.orgId eq orgId }.sortedWith(compareBy({ it.referenceNo }, { it.version })).map { row ->
            if (row.referenceNo != row.content.referenceNo) fail("财务账核对编号与原件不一致，不能展示核对结论")
            val checked = CollectionPermanentArchiveDao.find {
                (CollectionPermanentArchives.bizType eq BIZ) and (CollectionPermanentArchives.bizId eq row.id.value) and
                    (CollectionPermanentArchives.phase eq "CHECKED")
            }.singleOrNull() ?: fail("缺少财务账核对原件，不能展示核对结论")
            archives.verifyExisting(checked, orgId, BIZ, row.id.value, "CHECKED", snapshot(row), files(row.content),verifyContent=false)
            mapOf("id" to row.id.value, "version" to row.version, "content" to row.content,
                "differences" to row.content.rows.mapNotNull { line -> LedgerAccountComparison.differences(line).takeIf { it.isNotEmpty() }?.let { mapOf("lineNo" to line.lineNo, "items" to it) } },
                "unlinkedLines" to row.content.rows.filter { it.collectionId == null }.map { it.lineNo },
                "systemReferences" to row.systemReferences,
                "bookReferences" to row.bookReferences.items,
                "overdue" to row.content.nextCheckOn.isBefore(LocalDate.now()),
                "archives" to archives.readForDisplay(BIZ, row.id.value, orgId).archives)
        }
    }
    /** 财务核对版本与其签署明细严格绑定，按现时财务职责逐次复核。 */
    fun requireArchivedOriginal(orgId:String,id:String,archiveId:String,fileId:String) {
        if(listOf(orgId,id,archiveId,fileId).any {it.isBlank() || it.length>100}) fail("财务核对原件案卷、档案或文件无效")
        readAuthorization.requireBusinessReadable(orgId,setOf("ADMIN",CollectionConst.ADMIN,CollectionConst.LEDGER_FINANCE),
            CollectionConst.CROSS_ORG,"财务账核对原件")
        val row=LedgerReconciliationDao.find { (LedgerReconciliations.id eq id) and (LedgerReconciliations.orgId eq orgId) }
            .singleOrNull() ?: fail("财务核对记录不存在或不属于本馆")
        if(row.referenceNo!=row.content.referenceNo) fail("财务账核对编号与原件不一致")
        val archive=CollectionPermanentArchiveDao.findById(archiveId) ?: fail("财务核对永久档案不存在")
        archives.verifyExisting(archive,orgId,BIZ,id,"CHECKED",snapshot(row),files(row.content))
        if(CollectionPermanentArchiveFileDao.find {
                (CollectionPermanentArchiveFiles.archiveId eq archiveId) and (CollectionPermanentArchiveFiles.fileId eq fileId)
            }.none {it.orgId==orgId && it.bizId==id}) fail("原件不属于指定财务核对档案")
    }
    fun record(orgId: String, value: LedgerReconciliationVo): String {
        CollectionCurrentOperationAccess.require(scope, orgId, true, setOf(CollectionConst.LEDGER_FINANCE), "财务账核对原件")
        CollectionOrgDao.find { CollectionOrgs.id eq orgId }.forUpdate().singleOrNull() ?: fail("收藏单位不存在")
        // 等待馆锁后重新读取当前资格，重复办理同样不能沿用等待前的授权。
        CollectionCurrentOperationAccess.require(scope, orgId, true, setOf(CollectionConst.LEDGER_FINANCE), "财务账核对原件")
        val previous = LedgerReconciliationDao.find { (LedgerReconciliations.orgId eq orgId) and (LedgerReconciliations.referenceNo eq value.referenceNo) }.maxByOrNull { it.version }
        if (previous?.content == value) {
            requireOriginal(previous)
            return previous.id.value
        }
        validate(value, previous?.content)
        listOf(value.ledgerKeeperUserId, value.financeUserId).forEach { users.requireMuseumMember(it, orgId) }
        users.requireRole(value.ledgerKeeperUserId, orgId, CollectionConst.ROLE_LEDGER_CUSTODIAN)
        users.requireRole(value.financeUserId, orgId, CollectionConst.ROLE_FINANCE)
        val originals = value.bookIds.associateWith { id -> books.requireBook(orgId, id).also(books::requireBookOriginal) }
        if (originals.values.any { books.currentMetadata(it).openedOn.isAfter(value.periodTo) }) fail("核对期间不能早于所选纸账实际建账日期")
        value.rows.forEach { line ->
            if (line.bookId !in value.bookIds) fail("核对行的纸账须在本次实际核对范围内")
        }
        if (previous != null) requireOriginal(previous)
        val captured = LocalDateTime.now()
        val bookSnapshots = LedgerBookReferencesVo(originals.mapValues { books.currentMetadata(it.value) })
        val references = value.rows.mapNotNull { line -> line.collectionId?.let { collectionId ->
            val current = CollectionDao.findById(collectionId)?.takeIf { it.orgId == orgId }
            val retained = if (current == null) CollectionRetainedLedgerDao.find { (CollectionRetainedLedgers.orgId eq orgId) and (CollectionRetainedLedgers.collectionId eq collectionId) }.maxByOrNull { it.createTime } else null
            val entry = current?.toModel() ?: retained?.let { retainedLedgers.verifiedBoundSnapshotForAuthorizedBusiness(it, orgId).collection }
                ?: fail("关联藏品须属于本馆当前账或本馆留存原账；历史未建系统记录可暂不关联并如实标明")
            LedgerSystemReferenceVo(line.lineNo, collectionId, if (current != null) "CURRENT_AT_RECORDING" else "RETAINED_SOURCE_LEDGER", captured, LedgerSystemEntryVo(entry.totalRegNo, entry.name, entry.quantity?.toLong(), entry.unit))
        } }
        val row = LedgerReconciliationDao.new { this.orgId = orgId; referenceNo = value.referenceNo; version = (previous?.version ?: 0) + 1; content = value; systemReferences = references; bookReferences = bookSnapshots }
        archives.seal(orgId, BIZ, row.id.value, "CHECKED", snapshot(row), files(value))
        return row.id.value
    }
    private fun validate(v: LedgerReconciliationVo, old: LedgerReconciliationVo?) {
        if (v.referenceNo.isBlank() || v.referenceNo != v.referenceNo.trim() || v.referenceNo.length > 100 || v.scopeDescription.isBlank() || v.bookIds.isEmpty() || listOf(v.signedReportFileIds, v.paperScheduleFileIds, v.financialScheduleFileIds).any(String::isBlank)) fail("请填写实际核对编号、范围、纸账册及双方签署核对报告、纸账明细、财务账明细原件")
        if (v.periodTo.isBefore(v.periodFrom) || v.checkedAt.toLocalDate().isBefore(v.periodTo) || v.checkedAt.isAfter(LocalDateTime.now()) || !v.nextCheckOn.isAfter(v.checkedAt.toLocalDate())) fail("请核实实际核对期间、签署时间和馆方确定的下次核对日期；不得预填未来完成时间")
        if (v.ledgerKeeperUserId.isBlank() || v.financeUserId.isBlank() || v.ledgerKeeperUserId == v.financeUserId) fail("须分别记录实际总账专管人员和财务核对人员")
        if (v.rows.isEmpty() && v.emptyScopeReason.isNullOrBlank()) fail("空范围核对须说明实际依据并保留双方空表原件")
        if (v.rows.map { it.lineNo }.distinct().size != v.rows.size || v.rows.any { it.lineNo <= 0 }) fail("实际逐项明细的行号须为不重复正整数")
        v.rows.forEach { line ->
            if (line.paper == null && line.financial == null) fail("核对行不能同时缺少两边记录；误录行应在复核版本中注明排除原因")
            if (line.paperPageRef.isBlank() || (line.financial != null && line.financialRecordRef.isNullOrBlank())) fail("请填写实际纸账页码或检索范围，以及对应财务账记录位置")
            listOfNotNull(line.paper, line.financial).forEach { entry ->
                if (entry.totalRegNo.isBlank() || entry.name.isBlank() || entry.unit.isBlank() || entry.unit != entry.unit.trim() || entry.unit.length > 100) fail("请按两边原件完整填写登记号、名称、数量和实际计量单位")
            }
            if (line.collectionId != null && line.collectionId.isBlank()) fail("无系统关联的历史行请留空关联，不得填空字符串")
            if (LedgerAccountComparison.differences(line).isNotEmpty() && (line.issueExplanation.isNullOrBlank() || line.plannedAction.isNullOrBlank())) fail("发现账账差异须逐项记录原因核查和后续处理安排，不得直接标记为一致")
        }
        if (old != null) {
            if (v.periodFrom != old.periodFrom || v.periodTo != old.periodTo || v.bookIds != old.bookIds) fail("同次核对的期间和账册范围不能被替换，请为新范围另建核对编号")
            if (v.checkedAt.isBefore(old.checkedAt) || v.replacementReason.isNullOrBlank() || v.replacementFileIds.isNullOrBlank()) fail("实际处理复核或订正须追加新版本、真实时间、原因及本次原件")
            val removed = old.rows.map { it.lineNo }.toSet() - v.rows.map { it.lineNo }.toSet()
            if (v.omittedLineReasons.keys != removed || v.omittedLineReasons.values.any(String::isBlank)) fail("原明细不能悄然消失；复核时排除误录行须逐行说明并保留订正原件")
        } else if (v.omittedLineReasons.isNotEmpty()) fail("初次核对不存在可排除的旧明细")
    }
    private fun requireOriginal(row: LedgerReconciliationDao) {
        if (archives.list(BIZ, row.id.value).none { it["phase"] == "CHECKED" }) fail("缺少上次核对原件，不能直接替换为已一致")
        archives.seal(row.orgId, BIZ, row.id.value, "CHECKED", snapshot(row), files(row.content))
    }
    private fun snapshot(row: LedgerReconciliationDao) = mapOf("content" to row.content, "systemReferences" to row.systemReferences, "bookReferences" to row.bookReferences)
    private fun files(v: LedgerReconciliationVo) = listOfNotNull(v.signedReportFileIds, v.paperScheduleFileIds, v.financialScheduleFileIds, v.replacementFileIds).joinToString(",")
    private fun fail(message: String): Nothing = throw BadRequestException(message)
}
