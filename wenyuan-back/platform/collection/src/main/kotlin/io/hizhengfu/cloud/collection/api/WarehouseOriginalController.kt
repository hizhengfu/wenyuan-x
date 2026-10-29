package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.service.WarehouseDiaryService
import io.hizhengfu.cloud.collection.service.WarehouseEntryApplicationService
import io.hizhengfu.cloud.collection.service.WarehouseEntryTimelineService
import io.hizhengfu.cloud.collection.service.WarehouseStaffingService
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.sys.file.service.FileOriginalDownloadService
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("\${apiPath}/collection")
class WarehouseOriginalController(
    private val diaries:WarehouseDiaryService,
    private val entries:WarehouseEntryApplicationService,
    private val timelines:WarehouseEntryTimelineService,
    private val staffing:WarehouseStaffingService,
    private val originals:FileOriginalDownloadService,
) {
    @GetMapping("warehouse-log/{id}/original/{archiveId}/{fileId}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_SELECT','COLLECTION_STORAGE','COLLECTION_WAREHOUSE_MANAGE')")
    @Log("查阅指定库房日记归档原件")
    fun diary(@PathVariable id:String,@PathVariable archiveId:String,@PathVariable fileId:String,response:HttpServletResponse) {
        diaries.requireArchivedOriginal(id,archiveId,fileId)
        response.setHeader("Access-Control-Expose-Headers","Content-Disposition")
        originals.downloadFromArchive(fileId,archiveId,response)
    }

    @GetMapping("warehouse-entry/{id}/original/{archiveId}/{fileId}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_WAREHOUSE_ENTRY_APPLY','COLLECTION_WAREHOUSE_ENTRY_VERIFY','COLLECTION_SELECT')")
    @Log("查阅人员入库归档原件")
    fun entry(@PathVariable id:String,@PathVariable archiveId:String,@PathVariable fileId:String,response:HttpServletResponse) {
        entries.requireArchivedOriginal(id,archiveId,fileId)
        response.setHeader("Access-Control-Expose-Headers","Content-Disposition")
        originals.downloadFromArchive(fileId,archiveId,response)
    }

    @GetMapping("warehouse/{id}/staffing/original/{archiveId}/{fileId}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_WAREHOUSE_MANAGE','COLLECTION_SELECT','COLLECTION_WAREHOUSE_ENTRY_APPLY')")
    @Log("查阅库管任职归档原件")
    fun staffingOriginal(@PathVariable id:String,@PathVariable archiveId:String,@PathVariable fileId:String,response:HttpServletResponse) {
        staffing.requireArchivedOriginal(id,archiveId,fileId)
        response.setHeader("Access-Control-Expose-Headers","Content-Disposition")
        originals.downloadFromArchive(fileId,archiveId,response)
    }

    @GetMapping("warehouse-entry/{id}/timeline/review/{archiveId}/original/{fileId}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_WAREHOUSE_ENTRY_VERIFY')")
    @Log("查阅人员离库时间核查归档原件")
    fun timeline(@PathVariable id:String,@PathVariable archiveId:String,@PathVariable fileId:String,response:HttpServletResponse) {
        timelines.requireArchivedOriginal(id,archiveId,fileId)
        response.setHeader("Access-Control-Expose-Headers","Content-Disposition")
        originals.downloadFromArchive(fileId,archiveId,response)
    }
}
