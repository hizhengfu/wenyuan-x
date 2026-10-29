package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.dao.CollectionChangeDao
import io.hizhengfu.cloud.collection.domain.CollectionChangeExecutionVo
import io.hizhengfu.cloud.collection.domain.CollectionChangeVo
import io.hizhengfu.cloud.collection.domain.CollectionChangeCorrectionRequest
import io.hizhengfu.cloud.collection.service.CollectionChangeService
import io.hizhengfu.cloud.collection.service.CollectionPermanentArchiveService
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import io.hizhengfu.cloud.flow.api.FlowController
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import io.hizhengfu.cloud.sys.file.service.FileOriginalDownloadService
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("\${apiPath}/collection/change")
@PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_LEDGER_CORRECT')")
class CollectionChangeController(private val service: CollectionChangeService, private val archives: CollectionPermanentArchiveService,
    private val originals: FileOriginalDownloadService) : FlowController<CollectionChangeDao>() {
    override fun flowService() = service

    @GetMapping("page")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_SELECT','COLLECTION_LEDGER_CORRECT')")
    fun page(request: HttpServletRequest) = ApiData(data = service.findPage(request))

    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_SELECT','COLLECTION_LEDGER_CORRECT')")
    fun get(@PathVariable id: String) = ApiData(data = service.get(id))

    @GetMapping("{id}/archive")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_SELECT','COLLECTION_LEDGER_CORRECT')")
    fun archive(@PathVariable id: String): ApiData {
        return ApiData(data = service.archivesForDisplay(id))
    }

    @GetMapping("{id}/original/{fileId}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_SELECT','COLLECTION_LEDGER_CORRECT')")
    @Log("查阅藏品信息变更案卷原件")
    fun original(@PathVariable id: String, @PathVariable fileId: String, response: HttpServletResponse) {
        service.requireOriginal(id, fileId)
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition")
        originals.download(fileId, response)
    }

    @GetMapping("{id}/original/{archiveId}/{fileId}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_SELECT','COLLECTION_LEDGER_CORRECT')")
    @Log("查阅藏品信息变更指定阶段归档原件")
    fun archivedOriginal(@PathVariable id: String, @PathVariable archiveId: String, @PathVariable fileId: String,
                         response: HttpServletResponse) {
        service.requireArchivedOriginal(id, archiveId, fileId)
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition")
        originals.downloadFromArchive(fileId, archiveId, response)
    }

    @GetMapping("proposal/{collectionId}")
    fun proposal(@PathVariable collectionId: String, @RequestParam(defaultValue = "false") includeSetRelations: Boolean = false) =
        ApiData(data = service.proposal(collectionId, includeSetRelations))

    @Log("保存藏品信息变更申请")
    @PostMapping("save")
    fun save(@RequestBody vo: CollectionChangeVo) = ApiData(data = service.save(vo))

    @Log("依据原信息变更申请另建补正草稿")
    @PostMapping("{id}/corrections")
    fun correct(@PathVariable id: String, @RequestBody vo: CollectionChangeCorrectionRequest) = ApiData(data = service.createCorrection(id, vo))

    @GetMapping("{id}/corrections")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_SELECT','COLLECTION_LEDGER_CORRECT')")
    fun corrections(@PathVariable id: String) = ApiData(data = service.corrections(id))

    @Log("签署归档并执行藏品信息变更")
    @PostMapping("{id}/execute")
    fun execute(@PathVariable id: String, @RequestBody vo: CollectionChangeExecutionVo) = ApiData(data = service.execute(id, vo))

    @GetMapping("{id}/flow")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_SELECT','COLLECTION_LEDGER_CORRECT')")
    override fun flowInfo(@PathVariable id: String): ApiData = super.flowInfo(id)
}
