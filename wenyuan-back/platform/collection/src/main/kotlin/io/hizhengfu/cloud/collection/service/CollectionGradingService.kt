package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.domain.GenericPage
import jakarta.servlet.http.HttpServletRequest
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
@Transactional
class CollectionGradingService(
    private val scope: CollectionScopeResolver,
    private val archives: CollectionPermanentArchiveService,
) {
    companion object { const val BIZ = "COLLECTION_GRADING" }

    private val readAuthorization = CollectionFileReadAuthorization(scope, CollectionActorDirectory(scope))
    private val history = CollectionGradingHistoryEvidence(archives)

    fun detail(collectionId: String): Map<String, Any?> {
        val collection = CollectionDao.findById(collectionId) ?: fail("藏品不存在")
        readAuthorization.requireBusinessReadable(collection.orgId, setOf("ADMIN",CollectionConst.ADMIN,CollectionConst.APPRAISE,
            CollectionConst.SELECT,CollectionConst.LEDGER_CREATE,CollectionConst.LEDGER_CORRECT),CollectionConst.CROSS_ORG,"文物定级原件")
        val items = records(collection,verifyContent=false).map { record ->
            if(record.totalRegNo!=collection.totalRegNo)fail("定级申请与本件藏品登记身份不一致，请核对原始行政档案")
            mapOf("id" to record.id.value, "version" to record.version, "application" to record.application,
                "result" to record.result, "nationalFiling" to record.nationalFiling,
                "ready" to ready(record), "archives" to archives.readForDisplay(BIZ, record.id.value, collection.orgId).archives)
        }
        return mapOf("collectionId" to collectionId, "totalRegNo" to collection.totalRegNo, "name" to collection.name,
            "nature" to collection.nature, "currentLevel" to collection.level, "registeredAt" to collection.registeredAt, "historicalConfirmedAt" to collection.historicalConfirmedAt,
            "records" to items)
    }

    /** 定位本件藏品、本次行政定级和指定阶段的冻结原件。 */
    fun requireArchivedOriginal(collectionId: String, gradingId: String, archiveId: String, fileId: String) {
        if (!archiveId.matches(Regex("[0-9]{15,20}")) || !fileId.matches(Regex("[0-9]{15,20}")))
            fail("归档原件标识不正确")
        @Suppress("UNCHECKED_CAST")
        val records = detail(collectionId)["records"] as List<Map<String, Any?>>
        val record = records.singleOrNull { it["id"] == gradingId } ?: fail("定级记录不属于本件藏品")
        @Suppress("UNCHECKED_CAST")
        val archives = record["archives"] as List<Map<String, Any?>>
        val archive = archives.singleOrNull { it["id"] == archiveId } ?: fail("永久档案不属于本次定级记录")
        @Suppress("UNCHECKED_CAST")
        val files = archive["files"] as List<Map<String, Any?>>
        if (files.none { it["fileId"] == fileId }) fail("该文件不是所选定级阶段的原件")
    }

    /** 原馆行政原件独立于藏品现所属馆列示；不带入接收馆现时主档信息。 */
    fun historicalPage(request: HttpServletRequest): GenericPage<Map<String, Any?>> {
        val access = CollectionSearchReadAccess.require(scope,
            additionalPermissions = setOf(CollectionConst.APPRAISE, CollectionConst.LEDGER_CREATE, CollectionConst.LEDGER_CORRECT),
            allowHistoricalOrgs = true)
        val page = GenericPage<Map<String, Any?>>().build(request)
        val query = CollectionGradings.selectAll()
        query.andWhere { CollectionGradings.orgId inList access.orgIds }
        request.getParameter("keyword")?.trim()?.takeIf(String::isNotBlank)?.let { keyword ->
            query.andWhere { (CollectionGradings.totalRegNo like "%$keyword%") or (CollectionGradings.collectionId like "%$keyword%") }
        }
        page.total = query.count()
        query.orderBy(CollectionGradings.createTime to SortOrder.DESC)
        if (page.pageSize > 0) query.limit(page.pageSize).offset(page.startRow)
        page.list = CollectionGradingDao.wrapRows(query).map { row ->
            requireGradingRead(row.orgId)
            requireRecorded(row,verifyContent=false)
            mapOf("id" to row.id.value, "orgId" to row.orgId, "collectionId" to row.collectionId,
                "totalRegNo" to row.totalRegNo, "version" to row.version,
                "applicationNo" to row.application.applicationNo, "ready" to ready(row))
        }
        return page
    }

    fun historicalDetail(id: String): Map<String, Any?> {
        val row = CollectionGradingDao.findById(id) ?: fail("定级历史记录不存在")
        requireGradingRead(row.orgId)
        requireRecorded(row,verifyContent=false)
        return mapOf("id" to id, "orgId" to row.orgId, "collectionId" to row.collectionId,
            "totalRegNo" to row.totalRegNo, "version" to row.version, "application" to row.application,
            "result" to row.result, "nationalFiling" to row.nationalFiling, "ready" to ready(row),
            "archives" to archives.readForDisplay(BIZ, id, row.orgId).archives)
    }

    fun requireHistoricalArchivedOriginal(id: String, archiveId: String, fileId: String) {
        if (!archiveId.matches(Regex("[0-9]{15,20}")) || !fileId.matches(Regex("[0-9]{15,20}")))
            fail("归档原件标识不正确")
        @Suppress("UNCHECKED_CAST")
        val saved = historicalDetail(id)["archives"] as List<Map<String, Any?>>
        val archive = saved.singleOrNull { it["id"] == archiveId } ?: fail("永久档案不属于本次定级记录")
        @Suppress("UNCHECKED_CAST")
        val files = archive["files"] as List<Map<String, Any?>>
        if (files.none { it["fileId"] == fileId }) fail("该文件不是所选定级阶段的原件")
    }

    private fun requireGradingRead(orgId: String) = readAuthorization.requireBusinessReadable(orgId,
        setOf("ADMIN", CollectionConst.ADMIN, CollectionConst.APPRAISE, CollectionConst.SELECT,
            CollectionConst.LEDGER_CREATE, CollectionConst.LEDGER_CORRECT), CollectionConst.CROSS_ORG, "文物定级原件")

    fun apply(collectionId: String, value: GradingApplicationVo): String {
        val collection = administrativeCollection(collectionId)
        text(value.preliminaryFileIds, value.itemDescription, value.identificationPhotoFileIds, value.provincialAuthority,
            value.applicationNo, value.applicationFileIds, value.receiptFileIds)
        if (value.proposedLevel == EnumRelicLevel.UNRATED) fail("请填写初步鉴选建议级别，未定级不能作为定级结论")
        time(value.preliminaryAt, value.submittedAt, "初步鉴选及实际向省级部门提交申请")
        val previous = records(collection)
        previous.lastOrNull()?.let { old ->
            if (old.application == value) return old.id.value
            if (value.replacementReason.isNullOrBlank()) fail("再次申请或补正定级须说明原因，原申请和行政原件永久保留")
            if (value.submittedAt.isBefore(old.result?.provincialFiledAt ?: old.application.submittedAt)) fail("新申请时间不能早于已有申请或行政定级结果")
        }
        val row = CollectionGradingDao.new {
            orgId = collection.orgId; this.collectionId = collectionId; totalRegNo = collection.totalRegNo
            version = (previous.maxOfOrNull { it.version } ?: 0) + 1; application = value
        }
        archives.seal(collection.orgId, BIZ, row.id.value, "APPLICATION",
            history.application(row, true),
            listOf(value.preliminaryFileIds, value.identificationPhotoFileIds, value.applicationFileIds, value.receiptFileIds).joinToString(","))
        history.capture(collection, row, "APPLICATION", previous + row)
        return row.id.value
    }

    fun result(id: String, value: GradingResultVo): String {
        val row = writable(id)
        if (row.result == value) return id
        if (row.result != null) fail("正式定级结果不可覆盖，更正请另建申请并留存新行政原件")
        text(value.provincialAuthority, value.documentNo, value.resultFileIds, value.provincialFilingNo, value.provincialFilingFileIds)
        if (value.provincialAuthority != row.application.provincialAuthority) fail("正式定级及备案机关须与申请的省级文物行政部门一致")
        if (value.level == EnumRelicLevel.UNRATED) fail("未定级不能作为正式定级结果")
        time(row.application.submittedAt, value.determinedAt, "省级部门实际定级")
        time(value.determinedAt, value.provincialFiledAt, "省级部门实际备案")
        val newer = records(CollectionDao[row.collectionId]).any { it.version > row.version && it.result != null }
        if (newer) fail("已有后续正式定级结果，请核对最新行政原件，不能倒置补录旧结果")
        archives.seal(row.orgId, BIZ, id, "PROVINCIAL_RESULT", value,
            listOf(value.resultFileIds, value.provincialFilingFileIds).joinToString(","))
        row.result = value
        val collection = CollectionDao[row.collectionId]
        history.capture(collection, row, "PROVINCIAL_RESULT", rawRecords(collection))
        return id
    }

    fun national(id: String, value: GradingNationalFilingVo): String {
        val row = writable(id)
        if (row.nationalFiling == value) return id
        if (row.nationalFiling != null) fail("国家备案原件不可覆盖")
        val result = row.result ?: fail("请先登记省级正式定级结果")
        if (result.level != EnumRelicLevel.FIRST) fail("本入口仅记录馆藏一级文物由省级部门报国家备案")
        text(value.submittingProvincialAuthority, value.nationalAuthority, value.submissionFileIds, value.filingNo, value.receiptFileIds)
        if (value.submittingProvincialAuthority != result.provincialAuthority) fail("馆藏一级文物应由省级文物行政部门报国务院文物行政部门备案")
        time(result.determinedAt, value.submittedAt, "省级部门实际报国家备案")
        time(value.submittedAt, value.filedAt, "国家实际备案")
        archives.seal(row.orgId, BIZ, id, "NATIONAL_FILING", value,
            listOf(value.submissionFileIds, value.receiptFileIds).joinToString(","))
        row.nationalFiling = value
        val collection = CollectionDao[row.collectionId]
        history.capture(collection, row, "NATIONAL_FILING", rawRecords(collection))
        return id
    }

    /** 正式登记和正式级别/性质变更的共同入口；普通名称订正保留既有历史级别。 */
    fun requireContent(collection: CollectionDao, content: CollectionCatalogContentVo, initial: Boolean): String? {
        if (!initial && content.level == collection.level && content.nature == collection.nature) return null
        val nature = content.nature ?: fail("请先核实文物藏品或其他藏品性质")
        if (nature == EnumCollectionNature.OTHER) {
            if (content.level != EnumRelicLevel.UNRATED) fail("其他藏品不能使用文物级别，请核实藏品性质和著录信息")
            return null
        }
        collection(collection.id.value, true)
        val latest = records(collection).lastOrNull { it.result != null }
            ?: fail("正式登记或变更文物级别须有省级行政定级及备案原件，馆内鉴定意见不能代替")
        if (!ready(latest)) fail("馆藏一级文物尚缺省级部门报国家备案的真实原件")
        if (latest.result!!.level != content.level) fail("著录级别与最新正式行政定级结果不一致")
        requireOriginals(latest)
        return latest.id.value
    }

    fun recordUse(collection: CollectionDao, gradingId: String?, operationId: String, level: EnumRelicLevel?) {
        if (gradingId == null) return
        val row = CollectionGradingDao.findById(gradingId) ?: fail("定级原件不存在")
        if (row.orgId != collection.orgId || row.collectionId != collection.id.value || !ready(row) || row.result!!.level != level) fail("正式采用的定级原件与本件藏品不符")
        records(collection)
        requireOriginals(row)
        archives.seal(row.orgId, BIZ, gradingId, "USED_${operationId.takeLast(45)}",
            mapOf("collectionId" to collection.id.value, "totalRegNo" to collection.totalRegNo, "operationId" to operationId,
                "result" to row.result, "nationalFiling" to row.nationalFiling),
            listOfNotNull(row.result?.resultFileIds, row.result?.provincialFilingFileIds, row.nationalFiling?.submissionFileIds, row.nationalFiling?.receiptFileIds).joinToString(","))
    }

    private fun requireOriginals(row: CollectionGradingDao) {
        if (!ready(row)) fail("行政定级或备案阶段的原始档案不完整")
        requireRecorded(row)
    }

    /** 核对已有阶段而不重封；尚未完成的申请与被清除的已归档结果必须区分。 */
    private fun requireRecorded(row: CollectionGradingDao,verifyContent:Boolean=true) {
        val originals = CollectionPermanentArchiveDao.find {
            (CollectionPermanentArchives.bizType eq BIZ) and (CollectionPermanentArchives.bizId eq row.id.value)
        }.toList()
        fun phase(name: String, value: Any?, files: String?) {
            val matches = originals.filter { it.phase == name }
            if (value == null) {
                if (matches.isNotEmpty()) fail("已归档的定级阶段不能清除，请核对行政原件：$name")
                return
            }
            val original = matches.singleOrNull() ?: fail("定级原始阶段缺失或重复，请核对行政原件：$name")
            archives.verifyExisting(original, row.orgId, BIZ, row.id.value, name, value, files,verifyContent=verifyContent)
        }
        val application = row.application
        phase("APPLICATION", history.application(row, history.versioned(row)),
            listOf(application.preliminaryFileIds, application.identificationPhotoFileIds, application.applicationFileIds, application.receiptFileIds).joinToString(","))
        phase("PROVINCIAL_RESULT", row.result, row.result?.let { listOf(it.resultFileIds, it.provincialFilingFileIds).joinToString(",") })
        phase("NATIONAL_FILING", row.nationalFiling, row.nationalFiling?.let { listOf(it.submissionFileIds, it.receiptFileIds).joinToString(",") })
        originals.filter { it.phase.startsWith("USED_") }.forEach { original ->
            val operationId = try {
                JsonUtils.mapper.readTree(original.snapshot)?.get("operationId")?.stringValue()
            } catch (_: tools.jackson.core.JacksonException) { fail("定级使用原件无法解析，请核对历史依据") }
            if (operationId.isNullOrBlank() || original.phase != "USED_${operationId.takeLast(45)}" || !ready(row))
                fail("定级使用阶段与业务标识或行政结果不一致")
            archives.verifyExisting(original, row.orgId, BIZ, row.id.value, original.phase,
                mapOf("collectionId" to row.collectionId, "totalRegNo" to row.totalRegNo, "operationId" to operationId,
                    "result" to row.result, "nationalFiling" to row.nationalFiling),
                listOfNotNull(row.result?.resultFileIds, row.result?.provincialFilingFileIds,
                    row.nationalFiling?.submissionFileIds, row.nationalFiling?.receiptFileIds).joinToString(","),verifyContent=verifyContent)
        }
    }

    private fun ready(row: CollectionGradingDao) = row.result != null && (row.result!!.level != EnumRelicLevel.FIRST || row.nationalFiling != null)
    private fun rawRecords(collection: CollectionDao) = CollectionGradingDao.find {
        (CollectionGradings.orgId eq collection.orgId) and (CollectionGradings.collectionId eq collection.id.value)
    }.sortedBy { it.version }
    private fun records(collection: CollectionDao,verifyContent:Boolean=true) = rawRecords(collection).onEach {requireRecorded(it,verifyContent)}.also { history.verify(collection, it) }
    private fun writable(id: String): CollectionGradingDao {
        // 先通过 SQL 确定对象，再按藏品、定级记录的固定顺序加锁；阶段归档同时阻止覆盖原件。
        val subject = CollectionGradings.selectAll().where { CollectionGradings.id eq id }.singleOrNull()
            ?.get(CollectionGradings.collectionId) ?: fail("定级申请不存在")
        val collection = administrativeCollection(subject)
        val row = CollectionGradingDao.find { CollectionGradings.id eq id }.forUpdate().singleOrNull() ?: fail("定级申请不存在")
        if (row.orgId != collection.orgId || row.totalRegNo != collection.totalRegNo) fail("定级申请所属馆或藏品登记身份已变化，请核实原始行政档案")
        records(collection)
        return row
    }
    /** 行政材料登记独立核对当前职责；编目采用已有依据不要求取得行政材料登记权限。 */
    private fun administrativeCollection(id: String): CollectionDao {
        val row = CollectionDao.find { Collections.id eq id }.forUpdate().singleOrNull() ?: fail("藏品不存在")
        CollectionCurrentOperationAccess.require(scope, row.orgId, true, setOf(CollectionConst.APPRAISE), "定级行政材料登记")
        return row
    }
    private fun collection(id: String, write: Boolean = false): CollectionDao {
        val row = (if (write) CollectionDao.find { Collections.id eq id }.forUpdate().singleOrNull() else CollectionDao.findById(id)) ?: fail("藏品不存在")
        OrgScopedReadPolicy.requireVisible(if (write) scope.writableOrgIds() else scope.readableOrgIds(), row.orgId, "文物定级")
        return row
    }
    private fun text(vararg values: String) { if (values.any(String::isBlank)) fail("请完整填写真实定级机关、文书编号和原始材料") }
    private fun time(from: LocalDateTime, to: LocalDateTime, action: String) {
        if (to.isBefore(from) || to.isAfter(LocalDateTime.now()) || from.isAfter(LocalDateTime.now())) fail("请核实$action 的真实先后时间，不能填写未来完成事实")
    }
    private fun fail(message: String): Nothing = throw BadRequestException(message)
}
