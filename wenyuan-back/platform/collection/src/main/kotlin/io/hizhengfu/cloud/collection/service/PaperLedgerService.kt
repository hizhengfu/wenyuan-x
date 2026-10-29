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
class PaperLedgerService(
    private val scope: CollectionScopeResolver, private val users: CollectionResponsibleUserService,
    private val archives: CollectionPermanentArchiveService,
) {
    companion object { const val BIZ = "COLLECTION_PAPER_LEDGER" }
    data class PageReference(val bookId: String, val bookNo: String, val pageNo: Int, val pageCount: Int) {
        val display: String get() = "$bookNo 第${pageNo}页"
    }
    private val readAuthorization = CollectionFileReadAuthorization(scope, CollectionActorDirectory(scope))

    /** 新办理使用已归档的本馆账册及实际页码；旧自由文本仅作为历史原值保留。 */
    fun requirePageReference(orgId: String, bookId: String?, pageNo: Int?): PageReference {
        CollectionCurrentOperationAccess.require(scope, orgId, true, setOf(CollectionConst.LEDGER_CORRECT), "纸质总账订正页码")
        if (bookId.isNullOrBlank() || pageNo == null) fail("请选择本馆已登记纸质总账及实际订正页码")
        val book = requireBook(orgId, bookId)
        requireBookOriginal(book)
        val metadata = currentMetadata(book)
        if (pageNo !in 1..metadata.pageCount) fail("纸质账订正页码超出当前登记账册页数，请核对实际原件")
        return PageReference(book.id.value, metadata.bookNo, pageNo, metadata.pageCount)
    }

    fun list(orgId: String): List<Map<String, Any?>> {
        readAuthorization.requireBusinessReadable(orgId, setOf("ADMIN", CollectionConst.ADMIN, CollectionConst.SELECT, CollectionConst.ORG_MANAGE, CollectionConst.LEDGER_CREATE, CollectionConst.LEDGER_CORRECT, CollectionConst.LEDGER_FINANCE), CollectionConst.CROSS_ORG, "纸质总账")
        return PaperLedgerBookDao.find { PaperLedgerBooks.orgId eq orgId }.map { book ->
            if (book.bookNo != book.content.bookNo) fail("纸质总账册号与登记原件不一致，不能展示账册")
            val registered = CollectionPermanentArchiveDao.find {
                (CollectionPermanentArchives.bizType eq BIZ) and (CollectionPermanentArchives.bizId eq book.id.value) and
                    (CollectionPermanentArchives.phase eq "REGISTERED")
            }.singleOrNull() ?: fail("缺少纸质总账登记原件，不能展示账册")
            archives.verifyExisting(registered, orgId, BIZ, book.id.value, "REGISTERED", book.content,
                listOf(book.content.contentsFileIds, custodyFiles(book.content.custody)).joinToString(","),verifyContent=false)
            val versions = verifiedCustodies(book,verifyContent=false)
            val metadata = metadataFrom(book, versions)
            mapOf("id" to book.id.value, "bookNo" to metadata.bookNo, "book" to metadata, "originalBook" to book.content,
                "retention" to "PERMANENT", "custodies" to versions.map { row ->
                    mapOf("id" to row.id.value, "version" to row.version, "content" to row.content,
                        "overdue" to (row.content.nextCheckOn?.isBefore(LocalDate.now()) == true),
                        "archives" to archives.readForDisplay(BIZ, row.id.value, orgId).archives)
                }, "archives" to archives.readForDisplay(BIZ, book.id.value, orgId).archives)
        }
    }
    /** 账册原始登记和各版保管记录分别绑定自己的冻结档案，不能借同馆另一账册的文件。 */
    fun requireArchivedOriginal(orgId:String,bookId:String,archiveId:String,fileId:String) {
        if(listOf(orgId,bookId,archiveId,fileId).any {it.isBlank() || it.length>100}) fail("纸账原件案卷、档案或文件无效")
        readAuthorization.requireBusinessReadable(orgId,
            setOf("ADMIN",CollectionConst.ADMIN,CollectionConst.SELECT,CollectionConst.ORG_MANAGE,
                CollectionConst.LEDGER_CREATE,CollectionConst.LEDGER_CORRECT,CollectionConst.LEDGER_FINANCE),
            CollectionConst.CROSS_ORG,"纸质总账原件")
        val book=requireBook(orgId,bookId)
        if(book.bookNo!=book.content.bookNo) fail("纸质总账册号与登记原件不一致")
        val registered=CollectionPermanentArchiveDao.find {
            (CollectionPermanentArchives.bizType eq BIZ) and (CollectionPermanentArchives.bizId eq bookId) and
                (CollectionPermanentArchives.phase eq "REGISTERED")
        }.singleOrNull() ?: fail("纸质总账登记原件缺失")
        archives.verifyExisting(registered,orgId,BIZ,bookId,"REGISTERED",book.content,
            listOf(book.content.contentsFileIds,custodyFiles(book.content.custody)).joinToString(","))
        val versions=verifiedCustodies(book)
        val archive=CollectionPermanentArchiveDao.findById(archiveId) ?: fail("纸账永久档案不存在")
        val valid=(archive.id.value==registered.id.value && archive.bizId==bookId && archive.phase=="REGISTERED") ||
            (archive.phase=="RECORDED" && versions.any {it.id.value==archive.bizId})
        if(!valid || archive.orgId!=orgId || archive.bizType!=BIZ) fail("纸账原件与当前账册或历史版本不一致")
        if(CollectionPermanentArchiveFileDao.find {
                (CollectionPermanentArchiveFiles.archiveId eq archiveId) and (CollectionPermanentArchiveFiles.fileId eq fileId)
            }.none {it.orgId==orgId && it.bizId==archive.bizId}) fail("原件不属于指定纸账档案")
    }
    fun register(orgId: String, value: PaperLedgerBookVo): String {
        lockOrg(orgId)
        val existing = aliasOwner(orgId, value.bookNo)
        if (existing != null) {
            if (existing.content == value) {
                requireBookOriginal(existing)
                return existing.id.value
            }
            fail("该纸质总账册号已登记，不能替换原账册；请追加实际保管或移交记录")
        }
        if (value.bookNo.isBlank() || value.bookNo != value.bookNo.trim() || value.bookNo.length > 100 || value.title.isBlank() || value.pageCount <= 0 || value.coverageDescription.isBlank() || value.contentsFileIds.isBlank()) fail("请核实纸质账册号、名称、总页数、涵盖范围及原始目录原件")
        if (value.openedOn.isAfter(value.custody.checkedAt.toLocalDate())) fail("纸质账册建账日期不能晚于实际保管核查日期")
        if (value.custody.correctedMetadata != null) fail("首次登记请直接填写真实账册资料，不能附带旧资料订正")
        validateCustody(orgId, value.custody, null)
        val book = PaperLedgerBookDao.new { this.orgId = orgId; bookNo = value.bookNo; content = value }
        archives.seal(orgId, BIZ, book.id.value, "REGISTERED", value, listOf(value.contentsFileIds, custodyFiles(value.custody)).joinToString(","))
        append(book, value.custody)
        return book.id.value
    }
    fun custody(orgId: String, bookId: String, value: PaperLedgerCustodyVo): String {
        lockOrg(orgId)
        val book = requireBook(orgId, bookId)
        requireBookOriginal(book)
        val previous = custodies(bookId).lastOrNull() ?: fail("缺少原始账册保管记录")
        requirePhase(previous.id.value, "RECORDED")
        archives.seal(orgId, BIZ, previous.id.value, "RECORDED", previous.content, custodyFiles(previous.content))
        if (previous.content == value) return previous.id.value
        if (value.reason.isNullOrBlank()) fail("新增保管核查、搬移、移交或订正须说明实际事由")
        validateCustody(orgId, value, previous.content)
        value.correctedMetadata?.let { metadata ->
            if (value.metadataCorrectionFileIds.isNullOrBlank() || metadata.bookNo.isBlank() || metadata.bookNo != metadata.bookNo.trim() || metadata.bookNo.length > 100 || metadata.title.isBlank() || metadata.pageCount <= 0 || metadata.coverageDescription.isBlank() || metadata.contentsFileIds.isBlank() || metadata.openedOn.isAfter(value.checkedAt.toLocalDate())) fail("账册资料订正须填完整真实资料、原目录及本次订正依据原件")
            if (aliasOwner(orgId, metadata.bookNo)?.id?.value?.let { it != bookId } == true) fail("订正后的册号已属于另一纸账或其历史册号，不能混淆原账")
        }
        return append(book, value)
    }
    fun requireBook(orgId: String, id: String): PaperLedgerBookDao = PaperLedgerBookDao.find {
        (PaperLedgerBooks.id eq id) and (PaperLedgerBooks.orgId eq orgId)
    }.singleOrNull() ?: fail("请选择本馆实际纸质账册")
    fun requireBookOriginal(book: PaperLedgerBookDao) {
        if (book.bookNo != book.content.bookNo) fail("纸质总账册号与登记原件不一致")
        requirePhase(book.id.value, "REGISTERED")
        archives.seal(book.orgId, BIZ, book.id.value, "REGISTERED", book.content, listOf(book.content.contentsFileIds, custodyFiles(book.content.custody)).joinToString(","))
        verifiedCustodies(book)
    }
    fun currentMetadata(book: PaperLedgerBookDao): PaperLedgerMetadataVo {
        return metadataFrom(book, verifiedCustodies(book))
    }
    private fun metadataFrom(book: PaperLedgerBookDao, versions: List<PaperLedgerCustodyDao>): PaperLedgerMetadataVo =
        versions.lastOrNull { it.content.correctedMetadata != null }?.content?.correctedMetadata
            ?: book.content.let { PaperLedgerMetadataVo(it.bookNo, it.title, it.openedOn, it.pageCount, it.coverageDescription, it.contentsFileIds) }
    private fun aliasOwner(orgId: String, number: String): PaperLedgerBookDao? = PaperLedgerBookDao.find { PaperLedgerBooks.orgId eq orgId }.firstOrNull { book ->
        book.bookNo == number || custodies(book.id.value).any { it.content.correctedMetadata?.bookNo == number }
    }
    private fun append(book: PaperLedgerBookDao, value: PaperLedgerCustodyVo): String {
        val nextVersion = (custodies(book.id.value).lastOrNull()?.version ?: 0) + 1
        val row = PaperLedgerCustodyDao.new { orgId = book.orgId; bookId = book.id.value; version = nextVersion; content = value }
        archives.seal(book.orgId, BIZ, row.id.value, "RECORDED", value, custodyFiles(value))
        return row.id.value
    }
    private fun validateCustody(orgId: String, value: PaperLedgerCustodyVo, previous: PaperLedgerCustodyVo?) {
        if (value.checkedAt.isAfter(LocalDateTime.now()) || (previous != null && value.checkedAt.isBefore(previous.checkedAt))) fail("保管核查时间须真实，后续记录不能早于上一份原件或填写未来完成时间")
        if (listOf(value.keeperUserId, value.storageLocation, value.conditionDescription, value.signedCustodyFileIds).any(String::isBlank)) fail("请登记纸质账实际专管人员、存放位置、保存状况及签署原件")
        users.requireMuseumMember(value.keeperUserId, orgId)
        users.requireRole(value.keeperUserId, orgId, CollectionConst.ROLE_LEDGER_CUSTODIAN)
        if (previous != null && previous.keeperUserId != value.keeperUserId) {
            if (value.fromKeeperUserId != previous.keeperUserId || value.handoverFileIds.isNullOrBlank()) fail("更换纸账专管人员须核实前任专管人员及双方实际签署移交原件")
        } else if (!value.fromKeeperUserId.isNullOrBlank()) fail("本次未更换专管人员，不应填写新的移交人")
        if (value.condition == EnumPaperLedgerCondition.ISSUE || previous?.condition == EnumPaperLedgerCondition.ISSUE) {
            if (value.handlingDescription.isNullOrBlank() || value.handlingFileIds.isNullOrBlank()) fail("纸账保存异常及后续恢复须保留实际处理说明和原件")
            if (value.condition == EnumPaperLedgerCondition.ISSUE && (value.nextCheckOn == null || !value.nextCheckOn.isAfter(value.checkedAt.toLocalDate()))) fail("尚有保存异常时须确定后续复核日期")
        }
    }
    private fun checkedCustodies(book: PaperLedgerBookDao) = custodies(book.id.value).also { rows ->
        if (rows.any { it.orgId != book.orgId }) fail("纸账保管记录所属收藏单位与原账册不一致，请核对原件")
    }
    private fun verifiedCustodies(book: PaperLedgerBookDao,verifyContent:Boolean=true) = checkedCustodies(book).also { rows ->
        if (rows.isEmpty()) fail("缺少纸账保管记录，不能核对原件")
        rows.forEach { row ->
            val recorded = CollectionPermanentArchiveDao.find {
                (CollectionPermanentArchives.bizType eq BIZ) and (CollectionPermanentArchives.bizId eq row.id.value) and
                    (CollectionPermanentArchives.phase eq "RECORDED")
            }.singleOrNull() ?: fail("缺少纸账保管原件，不能核对保管记录")
            archives.verifyExisting(recorded, book.orgId, BIZ, row.id.value, "RECORDED", row.content, custodyFiles(row.content),verifyContent=verifyContent)
        }
    }
    private fun custodies(id: String) = PaperLedgerCustodyDao.find { PaperLedgerCustodies.bookId eq id }.sortedBy { it.version }
    private fun custodyFiles(v: PaperLedgerCustodyVo) = listOfNotNull(v.signedCustodyFileIds, v.handoverFileIds, v.handlingFileIds, v.metadataCorrectionFileIds, v.correctedMetadata?.contentsFileIds).joinToString(",")
    private fun requirePhase(id: String, phase: String) { if (archives.list(BIZ, id).none { it["phase"] == phase }) fail("缺少纸质账已归档原件阶段") }
    private fun lockOrg(orgId: String) {
        CollectionCurrentOperationAccess.require(scope, orgId, true,
            setOf(CollectionConst.ORG_MANAGE, CollectionConst.LEDGER_CREATE, CollectionConst.LEDGER_CORRECT), "纸质总账")
        CollectionOrgDao.find { CollectionOrgs.id eq orgId }.forUpdate().singleOrNull() ?: fail("收藏单位不存在")
        // 等待馆锁后重新读取当前资格，重复办理同样不能沿用等待前的授权。
        CollectionCurrentOperationAccess.require(scope, orgId, true,
            setOf(CollectionConst.ORG_MANAGE, CollectionConst.LEDGER_CREATE, CollectionConst.LEDGER_CORRECT), "纸质总账")
    }
    private fun fail(message: String): Nothing = throw BadRequestException(message)
}
