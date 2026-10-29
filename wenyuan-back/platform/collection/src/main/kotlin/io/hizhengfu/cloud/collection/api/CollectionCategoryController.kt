package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.dao.CollectionCategoryVo
import io.hizhengfu.cloud.collection.service.CollectionCategoryService
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import io.hizhengfu.cloud.common.domain.GenericUpdateSort
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 文物分类。 */
@RestController
@RequestMapping("\${apiPath}/collection/category")
class CollectionCategoryController(private val collectionCategoryService: CollectionCategoryService) {

    @GetMapping("tree")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_SELECT')")
    fun tree(): ApiData = ApiData(data = collectionCategoryService.findAll())

    @GetMapping("page")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_CATEGORY_MANAGE')")
    fun page(@RequestParam(required = false) keyword: String?, request: HttpServletRequest): ApiData =
        ApiData(data = collectionCategoryService.findPage(keyword, request))

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_SELECT')")
    fun list(): ApiData = ApiData(data = collectionCategoryService.findAll())

    @Log("文物分类创建")
    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_CATEGORY_MANAGE')")
    fun create(@RequestBody @Validated vo: CollectionCategoryVo): ApiData =
        ApiData(data = collectionCategoryService.create(vo))

    @Log("文物分类更新")
    @PutMapping("{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_CATEGORY_MANAGE')")
    fun update(@PathVariable id: String, @RequestBody @Validated vo: CollectionCategoryVo): ApiData =
        ApiData(data = collectionCategoryService.update(id, vo))

    @Log("文物分类排序")
    @PostMapping("sort")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_CATEGORY_MANAGE')")
    fun updateSort(@RequestBody sorts: List<GenericUpdateSort>): ApiData {
        collectionCategoryService.updateSort(sorts)
        return ApiData("更新顺序成功")
    }

    @Log("文物分类删除")
    @DeleteMapping("{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_CATEGORY_MANAGE')")
    fun delete(@PathVariable id: String): ApiData {
        collectionCategoryService.delete(id)
        return ApiData("已删除")
    }
}
