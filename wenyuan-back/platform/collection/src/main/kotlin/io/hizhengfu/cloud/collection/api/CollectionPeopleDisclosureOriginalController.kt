package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.domain.EnumDisclosureSubject
import io.hizhengfu.cloud.collection.service.CollectionDisclosureService
import io.hizhengfu.cloud.collection.service.CollectionStaffEducationService
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
class CollectionPeopleDisclosureOriginalController(
    private val education:CollectionStaffEducationService,
    private val disclosure:CollectionDisclosureService,
    private val originals:FileOriginalDownloadService,
) {
    @GetMapping("staff-education/{id}/original/{archiveId}/{fileId}")
    @PreAuthorize("isAuthenticated()")
    @Log("查阅岗位教育指定归档原件")
    fun education(@PathVariable id:String,@PathVariable archiveId:String,@PathVariable fileId:String,response:HttpServletResponse) {
        education.requireArchivedOriginal(id,archiveId,fileId)
        response.setHeader("Access-Control-Expose-Headers","Content-Disposition")
        originals.downloadFromArchive(fileId,archiveId,response)
    }

    @GetMapping("disclosure/{orgId}/{subject}/{id}/record/{recordId}/original/{archiveId}/{fileId}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_PUBLISH')")
    @Log("查阅公开核查指定版本归档原件")
    fun disclosure(
        @PathVariable orgId:String,@PathVariable subject:EnumDisclosureSubject,@PathVariable id:String,
        @PathVariable recordId:String,@PathVariable archiveId:String,@PathVariable fileId:String,response:HttpServletResponse,
    ) {
        disclosure.requireArchivedOriginal(orgId,subject,id,recordId,archiveId,fileId)
        response.setHeader("Access-Control-Expose-Headers","Content-Disposition")
        originals.downloadFromArchive(fileId,archiveId,response)
    }
}
