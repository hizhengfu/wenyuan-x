package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.collection.service.*
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import io.hizhengfu.cloud.sys.file.service.FileOriginalDownloadService
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("\${apiPath}/collection/org/{orgId}/paper-ledger")
class PaperLedgerController(private val books: PaperLedgerService, private val reconciliation: LedgerReconciliationService,
    private val originals: FileOriginalDownloadService) {
    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_SELECT','COLLECTION_ORG_MANAGE','COLLECTION_LEDGER_CREATE','COLLECTION_LEDGER_CORRECT','COLLECTION_LEDGER_FINANCE')")
    fun list(@PathVariable orgId: String) = ApiData(data = books.list(orgId))
    @GetMapping("{id}/original/{archiveId}/{fileId}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_SELECT','COLLECTION_ORG_MANAGE','COLLECTION_LEDGER_CREATE','COLLECTION_LEDGER_CORRECT','COLLECTION_LEDGER_FINANCE')")
    @Log("查阅纸质总登记账指定永久原件")
    fun bookOriginal(@PathVariable orgId:String,@PathVariable id:String,@PathVariable archiveId:String,@PathVariable fileId:String,response:HttpServletResponse) {
        books.requireArchivedOriginal(orgId,id,archiveId,fileId)
        response.setHeader("Access-Control-Expose-Headers","Content-Disposition")
        originals.downloadFromArchive(fileId,archiveId,response)
    }
    @PostMapping
    @Log("登记纸质总登记账实际保管原件")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_ORG_MANAGE','COLLECTION_LEDGER_CREATE','COLLECTION_LEDGER_CORRECT')")
    fun register(@PathVariable orgId: String, @RequestBody value: PaperLedgerBookVo) = ApiData(data = books.register(orgId, value))
    @PostMapping("{id}/custody")
    @Log("追加纸质总账实际保管或移交记录")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_ORG_MANAGE','COLLECTION_LEDGER_CREATE','COLLECTION_LEDGER_CORRECT')")
    fun custody(@PathVariable orgId: String, @PathVariable id: String, @RequestBody value: PaperLedgerCustodyVo) = ApiData(data = books.custody(orgId, id, value))
    @GetMapping("reconciliation-collections")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_LEDGER_FINANCE')")
    fun reconciliationCollections(@PathVariable orgId: String) = ApiData(data = reconciliation.collections(orgId))
    @GetMapping("reconciliations")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_LEDGER_FINANCE')")
    fun reconciliations(@PathVariable orgId: String) = ApiData(data = reconciliation.list(orgId))
    @GetMapping("reconciliations/{id}/original/{archiveId}/{fileId}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_LEDGER_FINANCE')")
    @Log("查阅纸账财务核对指定永久原件")
    fun reconciliationOriginal(@PathVariable orgId:String,@PathVariable id:String,@PathVariable archiveId:String,@PathVariable fileId:String,response:HttpServletResponse) {
        reconciliation.requireArchivedOriginal(orgId,id,archiveId,fileId)
        response.setHeader("Access-Control-Expose-Headers","Content-Disposition")
        originals.downloadFromArchive(fileId,archiveId,response)
    }
    @PostMapping("reconciliations")
    @Log("登记纸账与财务账实际核对及复核原件")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_LEDGER_FINANCE')")
    fun reconcile(@PathVariable orgId: String, @RequestBody value: LedgerReconciliationVo) = ApiData(data = reconciliation.record(orgId, value))
}
