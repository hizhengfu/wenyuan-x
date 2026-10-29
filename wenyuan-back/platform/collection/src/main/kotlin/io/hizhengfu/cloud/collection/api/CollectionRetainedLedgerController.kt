package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.service.CollectionRetainedLedgerService
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import io.hizhengfu.cloud.sys.file.service.FileOriginalDownloadService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("\${apiPath}/collection/retained-ledger")
class CollectionRetainedLedgerController(private val service:CollectionRetainedLedgerService,private val originals:FileOriginalDownloadService) {
    @GetMapping("page")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_SELECT')")
    fun page(request:HttpServletRequest)=ApiData(data=service.findPage(request))
    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_SELECT')")
    fun get(@PathVariable id:String)=ApiData(data=service.get(id))
    @GetMapping("{id}/original/{archiveId}/{fileId}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_SELECT')")
    @Log("查阅原馆留存总账指定永久原件")
    fun original(@PathVariable id:String,@PathVariable archiveId:String,@PathVariable fileId:String,response:HttpServletResponse) {
        service.requireArchivedOriginal(id,archiveId,fileId)
        response.setHeader("Access-Control-Expose-Headers","Content-Disposition")
        originals.downloadFromArchive(fileId,archiveId,response)
    }
}
