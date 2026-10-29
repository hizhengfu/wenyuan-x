package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.domain.CollectionCatalogContentVo
import io.hizhengfu.cloud.collection.domain.CollectionCatalogQueryVo
import io.hizhengfu.cloud.collection.domain.CollectionCatalogReviewVo
import io.hizhengfu.cloud.collection.service.CollectionCatalogService
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RequestParam

/** 独立编目工作台：录入、提交、审核、版本和卡片。 */
@RestController
@RequestMapping("\${apiPath}/collection/catalog")
class CollectionCatalogController(
    private val catalogService: CollectionCatalogService,
) {
    @GetMapping("page")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_SELECT','COLLECTION_CATALOG_EDIT','COLLECTION_CATALOG_AUDIT')")
    fun page(@ModelAttribute query: CollectionCatalogQueryVo, request: HttpServletRequest): ApiData =
        ApiData(data = catalogService.findPage(query, request))

    @GetMapping("{collectionId}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_SELECT','COLLECTION_CATALOG_EDIT','COLLECTION_CATALOG_AUDIT')")
    fun get(@PathVariable collectionId: String): ApiData = ApiData(data = catalogService.get(collectionId))

    @Log("保存藏品编目")
    @PutMapping("{collectionId}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_CATALOG_EDIT')")
    fun save(@PathVariable collectionId: String, @RequestBody content: CollectionCatalogContentVo, @RequestParam expectedStateHash: String): ApiData =
        ApiData(data = catalogService.saveConfirmed(collectionId, content, expectedStateHash))

    @Log("保存并提交藏品编目")
    @PostMapping("{collectionId}/save-submit")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_CATALOG_EDIT')")
    fun saveAndSubmit(@PathVariable collectionId: String, @RequestBody content: CollectionCatalogContentVo, @RequestParam expectedStateHash: String): ApiData =
        ApiData(data = catalogService.saveConfirmed(collectionId, content, expectedStateHash, submit = true))

    @Log("提交藏品编目")
    @PostMapping("{collectionId}/submit")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_CATALOG_EDIT')")
    fun submit(@PathVariable collectionId: String, @RequestParam expectedStateHash: String): ApiData = ApiData(data = catalogService.submitConfirmed(collectionId, expectedStateHash))

    @Log("审核藏品编目")
    @PostMapping("{collectionId}/review")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_CATALOG_AUDIT')")
    fun review(@PathVariable collectionId: String, @RequestBody review: CollectionCatalogReviewVo, @RequestParam expectedStateHash: String): ApiData =
        ApiData(data = catalogService.reviewConfirmed(collectionId, review, expectedStateHash))

    @Log("发起编目修订")
    @PostMapping("{collectionId}/revision")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_CATALOG_EDIT')")
    fun revision(@PathVariable collectionId: String): ApiData = ApiData(data = catalogService.startRevision(collectionId))

    @GetMapping("{collectionId}/versions")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_SELECT','COLLECTION_CATALOG_EDIT','COLLECTION_CATALOG_AUDIT')")
    fun versions(@PathVariable collectionId: String): ApiData = ApiData(data = catalogService.versions(collectionId))

    @GetMapping("{collectionId}/card")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_SELECT','COLLECTION_CATALOG_EDIT','COLLECTION_CATALOG_AUDIT')")
    fun card(@PathVariable collectionId: String): ApiData = ApiData(data = catalogService.card(collectionId))
}
