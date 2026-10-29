package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.collection.service.CollectionGradingService
import io.hizhengfu.cloud.sys.file.service.FileOriginalDownloadService
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpServletRequest
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("\${apiPath}/collection/grading")
class CollectionGradingController(private val service: CollectionGradingService,
    private val originals: FileOriginalDownloadService) {
    @GetMapping("collection/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_APPRAISE','COLLECTION_SELECT','COLLECTION_LEDGER_CREATE','COLLECTION_LEDGER_CORRECT')")
    fun detail(@PathVariable id: String) = ApiData(data = service.detail(id))

    @GetMapping("collection/{collectionId}/record/{id}/original/{archiveId}/{fileId}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_APPRAISE','COLLECTION_SELECT','COLLECTION_LEDGER_CREATE','COLLECTION_LEDGER_CORRECT')")
    @Log("查阅行政定级指定阶段原件")
    fun archivedOriginal(@PathVariable collectionId: String, @PathVariable id: String,
        @PathVariable archiveId: String, @PathVariable fileId: String, response: HttpServletResponse) {
        service.requireArchivedOriginal(collectionId, id, archiveId, fileId)
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition")
        originals.downloadFromArchive(fileId, archiveId, response)
    }

    @GetMapping("history/page")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_APPRAISE','COLLECTION_SELECT','COLLECTION_LEDGER_CREATE','COLLECTION_LEDGER_CORRECT')")
    fun historyPage(request: HttpServletRequest) = ApiData(data = service.historicalPage(request))

    @GetMapping("history/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_APPRAISE','COLLECTION_SELECT','COLLECTION_LEDGER_CREATE','COLLECTION_LEDGER_CORRECT')")
    fun history(@PathVariable id: String) = ApiData(data = service.historicalDetail(id))

    @GetMapping("history/{id}/original/{archiveId}/{fileId}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_APPRAISE','COLLECTION_SELECT','COLLECTION_LEDGER_CREATE','COLLECTION_LEDGER_CORRECT')")
    @Log("查阅原馆行政定级历史阶段原件")
    fun historicalOriginal(@PathVariable id: String, @PathVariable archiveId: String,
        @PathVariable fileId: String, response: HttpServletResponse) {
        service.requireHistoricalArchivedOriginal(id, archiveId, fileId)
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition")
        originals.downloadFromArchive(fileId, archiveId, response)
    }

    @PostMapping("collection/{id}/application")
    @Log("记录真实省级定级申请及收件原件")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_APPRAISE')")
    fun apply(@PathVariable id: String, @RequestBody value: GradingApplicationVo) = ApiData(data = service.apply(id, value))

    @PostMapping("{id}/result")
    @Log("记录省级正式定级及备案原件")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_APPRAISE')")
    fun result(@PathVariable id: String, @RequestBody value: GradingResultVo) = ApiData(data = service.result(id, value))

    @PostMapping("{id}/national")
    @Log("记录省级部门报国家备案原件")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_APPRAISE')")
    fun national(@PathVariable id: String, @RequestBody value: GradingNationalFilingVo) = ApiData(data = service.national(id, value))
}
