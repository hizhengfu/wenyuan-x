package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.dao.CollectionAttachmentVo
import io.hizhengfu.cloud.collection.dao.CollectionMediaVo
import io.hizhengfu.cloud.collection.dao.CollectionMediaFolderVo
import io.hizhengfu.cloud.collection.service.CollectionFileService
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 藏品影像与附件。 */
@RestController
@RequestMapping("\${apiPath}/collection/{collectionId}")
class CollectionFileController(
    private val fileService: CollectionFileService,
) {

    @GetMapping("media-folder")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_SELECT')")
    fun mediaFolders(@PathVariable collectionId: String): ApiData = ApiData(data = fileService.listMediaFolders(collectionId))

    @Log("保存藏品影像目录")
    @PostMapping("media-folder")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_LEDGER_CREATE')")
    fun saveMediaFolder(@PathVariable collectionId: String, @RequestBody @Validated vo: CollectionMediaFolderVo): ApiData =
        ApiData(data = fileService.saveMediaFolder(collectionId, vo))

    @Log("删除藏品影像目录")
    @DeleteMapping("media-folder/{folderId}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_LEDGER_CREATE')")
    fun deleteMediaFolder(@PathVariable collectionId: String, @PathVariable folderId: String): ApiData =
        ApiData(data = fileService.deleteMediaFolder(collectionId, folderId))

    @GetMapping("media")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_SELECT')")
    fun media(@PathVariable collectionId: String): ApiData =
        ApiData(data = fileService.listMedia(collectionId))

    @Log("保存藏品影像")
    @PostMapping("media")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_LEDGER_CREATE')")
    fun saveMedia(@PathVariable collectionId: String, @RequestBody @Validated vo: CollectionMediaVo): ApiData =
        ApiData(data = fileService.saveMedia(collectionId, vo))

    @Log("删除藏品影像")
    @DeleteMapping("media/{mediaId}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_LEDGER_CREATE')")
    fun deleteMedia(@PathVariable collectionId: String, @PathVariable mediaId: String): ApiData =
        ApiData(data = fileService.deleteMedia(collectionId, mediaId))

    @GetMapping("attachment")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_SELECT')")
    fun attachments(@PathVariable collectionId: String): ApiData =
        ApiData(data = fileService.listAttachments(collectionId))

    @Log("保存藏品附件")
    @PostMapping("attachment")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_LEDGER_CREATE')")
    fun saveAttachment(@PathVariable collectionId: String, @RequestBody @Validated vo: CollectionAttachmentVo): ApiData =
        ApiData(data = fileService.saveAttachment(collectionId, vo))

    @Log("删除藏品附件")
    @DeleteMapping("attachment/{attachmentId}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_LEDGER_CREATE')")
    fun deleteAttachment(@PathVariable collectionId: String, @PathVariable attachmentId: String): ApiData =
        ApiData(data = fileService.deleteAttachment(collectionId, attachmentId))
}
