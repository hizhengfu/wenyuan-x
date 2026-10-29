package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.dao.CollectionAuxiliaryVo
import io.hizhengfu.cloud.collection.service.CollectionAuxiliaryService
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import io.hizhengfu.cloud.sys.file.service.FileOriginalDownloadService
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("\${apiPath}/collection/auxiliary")
class CollectionAuxiliaryController(private val service: CollectionAuxiliaryService,private val originals:FileOriginalDownloadService) {
    @GetMapping("{orgId}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_SELECT','COLLECTION_LEDGER_CORRECT')")
    fun list(@PathVariable orgId:String) = ApiData(data=service.list(orgId))

    @GetMapping("{orgId}/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_SELECT','COLLECTION_LEDGER_CORRECT')")
    fun detail(@PathVariable orgId:String,@PathVariable id:String)=ApiData(data=service.detail(orgId,id))

    @GetMapping("{orgId}/{id}/original/{archiveId}/{fileId}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_SELECT','COLLECTION_LEDGER_CORRECT')")
    @Log("查阅辅助账册指定登记原件")
    fun original(@PathVariable orgId:String,@PathVariable id:String,@PathVariable archiveId:String,@PathVariable fileId:String,response:HttpServletResponse) {
        service.requireArchivedOriginal(orgId,id,archiveId,fileId)
        response.setHeader("Access-Control-Expose-Headers","Content-Disposition")
        originals.downloadFromArchive(fileId,archiveId,response)
    }

    @Log("登记辅助账册原始记录")
    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_LEDGER_CORRECT')")
    fun register(@RequestBody vo:CollectionAuxiliaryVo) = ApiData(data=service.register(vo))
}
