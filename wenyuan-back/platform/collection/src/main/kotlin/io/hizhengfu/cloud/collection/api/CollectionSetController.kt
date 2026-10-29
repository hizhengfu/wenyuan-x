package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.dao.CollectionSetVo
import io.hizhengfu.cloud.collection.service.CollectionSetService
import io.hizhengfu.cloud.common.domain.ApiData
import io.hizhengfu.cloud.common.annotation.Log
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("\${apiPath}/collection/{collectionId}/set")
class CollectionSetController(
    private val setService: CollectionSetService,
) {

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_SELECT')")
    fun list(@PathVariable collectionId: String): ApiData =
        ApiData(data = setService.list(collectionId))

    @Log("保存藏品套件关系")
    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_LEDGER_CREATE')")
    fun save(@PathVariable collectionId: String, @RequestBody @Validated vo: CollectionSetVo): ApiData =
        ApiData(data = setService.save(collectionId, vo))

    @Log("删除藏品套件关系")
    @DeleteMapping("{setId}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_LEDGER_CREATE')")
    fun delete(@PathVariable collectionId: String, @PathVariable setId: String): ApiData {
        setService.delete(collectionId, setId)
        return ApiData()
    }
}
