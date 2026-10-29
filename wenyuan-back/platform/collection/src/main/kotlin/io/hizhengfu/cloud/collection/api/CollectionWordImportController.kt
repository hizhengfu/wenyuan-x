package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.domain.EnumCollectionImportMode
import io.hizhengfu.cloud.collection.domain.EnumCollectionWordDocumentType
import io.hizhengfu.cloud.collection.service.CollectionWordImportService
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

/** 藏品登记表/动态跟踪表 Word 一键导入。 */
@RestController
@RequestMapping("\${apiPath}/collection/word-import")
class CollectionWordImportController(
    private val service: CollectionWordImportService,
) {
    @PostMapping("validate")
    @PreAuthorize(
        "hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_LEDGER_CREATE','COLLECTION_LEDGER_CORRECT','COLLECTION_STORAGE')",
    )
    fun validate(
        @RequestPart("file") file: MultipartFile,
        @RequestParam(required = false, defaultValue = "CREATE_ONLY") mode: EnumCollectionImportMode,
        @RequestParam(required = false) reason: String?,
        @RequestParam(required = false) expectedCollectionId: String?,
        @RequestParam(required = false) expectedDocumentType: EnumCollectionWordDocumentType?,
    ): ApiData = ApiData(data = service.validate(file, mode, reason, expectedCollectionId, expectedDocumentType))

    @Log("Word 一键导入藏品登记/动态跟踪记录")
    @PostMapping("commit")
    @PreAuthorize(
        "hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_LEDGER_CREATE','COLLECTION_LEDGER_CORRECT','COLLECTION_STORAGE')",
    )
    fun commit(
        @RequestPart("file") file: MultipartFile,
        @RequestParam(required = false, defaultValue = "CREATE_ONLY") mode: EnumCollectionImportMode,
        @RequestParam(required = false) reason: String?,
        @RequestParam(required = false) expectedCollectionId: String?,
        @RequestParam(required = false) expectedDocumentType: EnumCollectionWordDocumentType?,
        @RequestParam expectedOrgId: String,
        @RequestParam expectedValidationHash: String,
    ): ApiData = ApiData(data = service.commit(file, mode, reason, expectedCollectionId, expectedDocumentType, expectedOrgId, expectedValidationHash))

    @GetMapping("history/{collectionId}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_SELECT','COLLECTION_STORAGE')")
    fun history(@PathVariable collectionId: String): ApiData = ApiData(data = service.history(collectionId))
}
