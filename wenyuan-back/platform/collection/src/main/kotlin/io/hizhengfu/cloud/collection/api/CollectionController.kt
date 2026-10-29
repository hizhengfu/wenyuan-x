package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.common.CollectionFlowImporter
import io.hizhengfu.cloud.collection.dao.CollectionVo
import io.hizhengfu.cloud.collection.dao.AnnualReportArchiveVo
import io.hizhengfu.cloud.collection.domain.CollectionCatalogAuditVo
import io.hizhengfu.cloud.collection.domain.CollectionCorrectVo
import io.hizhengfu.cloud.collection.domain.CollectionFileImportItemVo
import io.hizhengfu.cloud.collection.domain.CollectionImportRowVo
import io.hizhengfu.cloud.collection.domain.CollectionQueryVo
import io.hizhengfu.cloud.collection.domain.CollectionUnifiedSearchQueryVo
import io.hizhengfu.cloud.collection.domain.EnumCollectionImportMode
import io.hizhengfu.cloud.collection.domain.RfidBatchQueryVo
import io.hizhengfu.cloud.collection.domain.RfidBindVo
import io.hizhengfu.cloud.collection.service.CollectionDashboardService
import io.hizhengfu.cloud.collection.service.CollectionImportService
import io.hizhengfu.cloud.collection.service.CollectionFileService
import io.hizhengfu.cloud.collection.service.CollectionService
import io.hizhengfu.cloud.collection.service.CollectionUnifiedSearchService
import io.hizhengfu.cloud.collection.service.AnnualReportArchiveService
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 藏品档案（总账/分类账/一物一档）。 */
@RestController
@RequestMapping("\${apiPath}/collection")
class CollectionController(
    private val collectionService: CollectionService,
    private val importService: CollectionImportService,
    private val collectionFileService: CollectionFileService,
    private val flowImporter: CollectionFlowImporter,
    private val dashboardService: CollectionDashboardService,
    private val annualReportArchiveService: AnnualReportArchiveService,
    private val annualReportEvidence: io.hizhengfu.cloud.collection.service.AnnualReportEvidenceService,
    private val unifiedSearchService: CollectionUnifiedSearchService,
) {

    /**
     * 手动导入/发布藏品流程定义（读取 flow/collection_*.json），返回逐条结果，便于排查。
     * force=true 时即便已 ACTIVE 也新建版本发布（用于把旧的 ADMIN 链替换为角色审批链）。
     */
    @PostMapping("flow/seed")
    @Log("导入或发布藏品流程定义")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN')")
    fun seedFlows(@RequestParam(required = false, defaultValue = "false") force: Boolean): ApiData =
        ApiData(data = flowImporter.importAll(force))

    @GetMapping("page")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_SELECT')")
    fun page(@ModelAttribute query: CollectionQueryVo, request: HttpServletRequest): ApiData =
        ApiData(data = collectionService.findPage(query, request))

    /**
     * 藏品系统统一检索：覆盖藏品档案及其征集、编目、流转、库房和基础资料。
     * 服务层继续按当前用户的收藏单位可见范围过滤，不能通过搜索绕过馆级数据权限。
     */
    @GetMapping("search")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_SELECT')")
    fun search(@ModelAttribute query: CollectionUnifiedSearchQueryVo): ApiData =
        ApiData(data = unifiedSearchService.search(query))

    @GetMapping("stat/summary")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STAT','COLLECTION_SELECT')")
    fun statSummary(): ApiData = ApiData(data = collectionService.summary())

    /** 工作台：出入库按月趋势。 */
    @GetMapping("stat/movement-trend")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STAT','COLLECTION_SELECT')")
    fun movementTrend(@RequestParam(required = false, defaultValue = "12") months: Int): ApiData =
        ApiData(data = dashboardService.movementTrend(months))

    /** 工作台：编目审核汇总（通过率）。 */
    @GetMapping("stat/audit-summary")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STAT','COLLECTION_SELECT')")
    fun auditSummary(): ApiData = ApiData(data = dashboardService.auditSummary())

    /** 工作台：进行中的流程单据（最新动态）。 */
    @GetMapping("stat/ongoing")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STAT','COLLECTION_SELECT')")
    fun ongoing(@RequestParam(required = false, defaultValue = "20") limit: Int): ApiData =
        ApiData(data = dashboardService.ongoing(limit))

    /** 工作台/年报：年度统计（期初/新增/退出馆藏/期末/流动量）。 */
    @GetMapping("stat/annual")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STAT','COLLECTION_SELECT')")
    fun annual(@RequestParam year: Int): ApiData = ApiData(data = dashboardService.annual(year))

    @GetMapping("stat/annual/archive")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STAT','COLLECTION_SELECT')")
    fun annualArchive(@RequestParam year: Int): ApiData = ApiData(data = annualReportArchiveService.get(year))

    @Log("归档藏品年度统计签章报表")
    @PostMapping("stat/annual/archive")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STAT')")
    fun saveAnnualArchive(@RequestBody vo: AnnualReportArchiveVo): ApiData = ApiData(data = annualReportArchiveService.save(vo))

    @GetMapping("stat/annual/reports")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STAT','COLLECTION_SELECT')")
    fun annualReports(@RequestParam year: Int)=ApiData(data=annualReportEvidence.detail(year))

    @PostMapping("stat/annual/reports")
    @Log("登记实际签署的年度统计及核对原件")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STAT')")
    fun signAnnualReport(@RequestBody value: io.hizhengfu.cloud.collection.domain.AnnualReportSignedVo,@RequestParam expectedOrgId: String)=ApiData(data=annualReportEvidence.signed(value,expectedOrgId))

    @PostMapping("stat/annual/reports/{id}/submission")
    @Log("登记年度统计实际报送凭据")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STAT')")
    fun submitAnnualReport(@PathVariable id: String,@RequestBody value: io.hizhengfu.cloud.collection.domain.AnnualReportSubmissionVo)=ApiData(data=annualReportEvidence.submission(id,value))

    @PostMapping("stat/annual/reports/{id}/filing")
    @Log("登记年度统计实际备案回执")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STAT')")
    fun fileAnnualReport(@PathVariable id: String,@RequestBody value: io.hizhengfu.cloud.collection.domain.AnnualReportFilingVo)=ApiData(data=annualReportEvidence.filing(id,value))

    @GetMapping("stat/survey-status")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STAT','COLLECTION_SELECT')")
    fun surveyStatus(@ModelAttribute query: CollectionQueryVo)=ApiData(data=collectionService.surveyStatus(query))

    /** 普查导出：14 项基本指标列表（前端转 CSV/Excel）。 */
    @GetMapping("stat/survey-export")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STAT','COLLECTION_SELECT')")
    fun surveyExport(@ModelAttribute query: CollectionQueryVo): ApiData =
        ApiData(data = collectionService.exportSurvey(query))

    /** 已核查公开稿 feed；停止供稿记录无正文，CMS 须移除旧内容。 */
    @GetMapping("published")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_PUBLISH','COLLECTION_SELECT')")
    fun published(): ApiData = ApiData(data = collectionService.publishedFeed())

    @GetMapping("ledger-logs/page")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_SELECT','COLLECTION_LEDGER_CORRECT')")
    fun ledgerLogPage(request: HttpServletRequest): ApiData =
        ApiData(data = collectionService.ledgerLogPage(request))

    @GetMapping("import/template")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_LEDGER_CREATE')")
    fun importTemplate(): ApiData = ApiData(data = importService.template())

    @PostMapping("import/validate")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_LEDGER_CREATE','COLLECTION_LEDGER_CORRECT')")
    fun validateImport(
        @RequestBody rows: List<CollectionImportRowVo>,
        @RequestParam(defaultValue = "CREATE_ONLY") mode: EnumCollectionImportMode,
        @RequestParam(required = false) reason: String?,
    ): ApiData = ApiData(data = importService.validate(rows, mode, reason))

    @Log("批量导入藏品")
    @PostMapping("import/commit")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_LEDGER_CREATE','COLLECTION_LEDGER_CORRECT')")
    fun commitImport(
        @RequestBody rows: List<CollectionImportRowVo>,
        @RequestParam(defaultValue = "CREATE_ONLY") mode: EnumCollectionImportMode,
        @RequestParam(required = false) reason: String?,
        @RequestParam expectedOrgId: String,
        @RequestParam expectedValidationHash: String,
    ): ApiData = ApiData(data = importService.commit(rows, mode, reason, expectedOrgId, expectedValidationHash))

    @PostMapping("file-import/validate")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_LEDGER_CREATE')")
    fun validateFileImport(@RequestBody items: List<CollectionFileImportItemVo>): ApiData =
        ApiData(data = collectionFileService.validateBatchFiles(items))

    @Log("批量关联藏品影像附件")
    @PostMapping("file-import/commit")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_LEDGER_CREATE')")
    fun commitFileImport(@RequestBody items: List<CollectionFileImportItemVo>, @RequestParam expectedOrgId: String, @RequestParam expectedValidationHash: String): ApiData =
        ApiData(data = collectionFileService.bindBatchFiles(items, expectedOrgId, expectedValidationHash))

    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_SELECT')")
    fun get(@PathVariable id: String): ApiData = ApiData(data = collectionService.get(id))

    @Log("藏品登记")
    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_LEDGER_CREATE')")
    fun create(@RequestBody @Validated vo: CollectionVo): ApiData =
        ApiData(data = collectionService.create(vo))

    @Log("藏品编辑")
    @PutMapping("{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_LEDGER_CREATE')")
    fun update(@PathVariable id: String, @RequestBody @Validated vo: CollectionVo): ApiData =
        ApiData(data = collectionService.update(id, vo))

    @Log("编目审核")
    @PostMapping("{id}/catalog-audit")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_LEDGER_CORRECT','COLLECTION_CATALOG_AUDIT')")
    fun auditCatalog(@PathVariable id: String, @RequestBody @Validated vo: CollectionCatalogAuditVo, @RequestParam expectedStateHash: String): ApiData =
        ApiData(data = collectionService.auditCatalog(id, vo, expectedStateHash))

    @Log("藏品关键字段更正")
    @PostMapping("{id}/correct")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_LEDGER_CORRECT')")
    fun correct(@PathVariable id: String, @RequestBody @Validated vo: CollectionCorrectVo): ApiData =
        ApiData(data = collectionService.correct(id, vo))

    @GetMapping("{id}/ledger-logs")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_SELECT')")
    fun ledgerLogs(@PathVariable id: String): ApiData =
        ApiData(data = collectionService.ledgerLogs(id))

    @Log("启用已核查藏品公开稿CMS供稿")
    @PostMapping("{id}/publish-cms")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_PUBLISH')")
    fun publishCms(@PathVariable id: String): ApiData =
        ApiData(data = collectionService.publishCms(id))

    @Log("绑定RFID标签")
    @PostMapping("{id}/rfid")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_LEDGER_CORRECT','COLLECTION_LEDGER_CREATE')")
    fun bindRfid(@PathVariable id: String, @RequestBody @Validated vo: RfidBindVo): ApiData =
        ApiData(data = collectionService.bindRfid(id, vo.tag))

    @Log("解绑RFID标签")
    @DeleteMapping("{id}/rfid")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_LEDGER_CORRECT','COLLECTION_LEDGER_CREATE')")
    fun unbindRfid(@PathVariable id: String): ApiData =
        ApiData(data = collectionService.unbindRfid(id))

    /** 手持机单扫：标签 → 藏品。 */
    @GetMapping("rfid/{tag}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_SELECT')")
    fun findByRfid(@PathVariable tag: String): ApiData =
        ApiData(data = collectionService.findByRfid(tag))

    /** 批量扫描：标签清单 → 命中藏品 + 未知标签。 */
    @PostMapping("rfid/batch")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_SELECT')")
    fun batchByRfid(@RequestBody vo: RfidBatchQueryVo): ApiData =
        ApiData(data = collectionService.batchByRfid(vo.tags))
}
