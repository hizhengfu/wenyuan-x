package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortCheckConstraintName

import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.domain.*
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.json.json

object PaperLedgerBooks : OrgAuditUIDTable("col_paper_ledger_book") {
    val bookNo = varchar("book_no", 100)
    val content = json("content", { JsonUtils.toJson(it)!! }, { JsonUtils.fromJson(it, PaperLedgerBookVo::class.java)!! })
    init { uniqueIndex(shortIndexName("org_book_no", unique = true), orgId, bookNo) }
}
class PaperLedgerBookDao(id: EntityID<String>) : OrgAuditUIDEntity(id, PaperLedgerBooks) {
    companion object : OrgAuditUIDEntityClass<PaperLedgerBookDao>(PaperLedgerBooks)
    var bookNo by PaperLedgerBooks.bookNo
    var content by PaperLedgerBooks.content
}
object PaperLedgerCustodies : OrgAuditUIDTable("col_paper_ledger_custody") {
    val bookId = varchar("book_id", 64)
    val version = integer("version", checkConstraintName = shortCheckConstraintName("version"))
    val content = json("content", { JsonUtils.toJson(it)!! }, { JsonUtils.fromJson(it, PaperLedgerCustodyVo::class.java)!! })
    init { uniqueIndex(shortIndexName("book_version", unique = true), bookId, version) }
}
class PaperLedgerCustodyDao(id: EntityID<String>) : OrgAuditUIDEntity(id, PaperLedgerCustodies) {
    companion object : OrgAuditUIDEntityClass<PaperLedgerCustodyDao>(PaperLedgerCustodies)
    var bookId by PaperLedgerCustodies.bookId
    var version by PaperLedgerCustodies.version
    var content by PaperLedgerCustodies.content
}
object LedgerReconciliations : OrgAuditUIDTable("col_ledger_reconciliation") {
    val referenceNo = varchar("reference_no", 100)
    val version = integer("version", checkConstraintName = shortCheckConstraintName("version"))
    val content = json("content", { JsonUtils.toJson(it)!! }, { JsonUtils.fromJson(it, LedgerReconciliationVo::class.java)!! })
    val systemReferences = json("system_references", { JsonUtils.toJson(it)!! }, { JsonUtils.fromJson(it, Array<LedgerSystemReferenceVo>::class.java)!!.toList() }).default(emptyList())
    val bookReferences = json("book_references", { JsonUtils.toJson(it)!! }, { JsonUtils.fromJson(it, LedgerBookReferencesVo::class.java)!! })
    init { uniqueIndex(shortIndexName("org_ref_version", unique = true), orgId, referenceNo, version) }
}
class LedgerReconciliationDao(id: EntityID<String>) : OrgAuditUIDEntity(id, LedgerReconciliations) {
    companion object : OrgAuditUIDEntityClass<LedgerReconciliationDao>(LedgerReconciliations)
    var referenceNo by LedgerReconciliations.referenceNo
    var version by LedgerReconciliations.version
    var content by LedgerReconciliations.content
    var systemReferences by LedgerReconciliations.systemReferences
    var bookReferences by LedgerReconciliations.bookReferences
}
