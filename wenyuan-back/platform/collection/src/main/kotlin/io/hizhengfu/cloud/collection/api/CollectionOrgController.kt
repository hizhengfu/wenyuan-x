package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.dao.CollectionOrgVo
import io.hizhengfu.cloud.collection.domain.CollectionOrgImportRowVo
import io.hizhengfu.cloud.collection.service.CollectionOrgService
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

/** 收藏单位（多馆）。 */
@RestController
@RequestMapping("\${apiPath}/collection/org")
class CollectionOrgController(private val orgService: CollectionOrgService,
    private val codes: io.hizhengfu.cloud.collection.service.CollectionOrgCodeService) {

    @GetMapping("{id}/code-context")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_ORG_MANAGE')")
    fun codeContext(@PathVariable id: String): ApiData = ApiData(data = codes.context(id))

    @PostMapping("{id}/code-corrections")
    @Log("留痕更正收藏单位代码")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_ORG_MANAGE')")
    fun correctCode(@PathVariable id: String, @RequestBody request: io.hizhengfu.cloud.collection.dao.CollectionOrgCodeCorrectionVo,
        @RequestParam expectedStateHash: String): ApiData = ApiData(data = codes.correct(id, request, expectedStateHash))


    @GetMapping("tree")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_SELECT')")
    fun tree(): ApiData = ApiData(data = orgService.findAll())

    @GetMapping("overview")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_SELECT','COLLECTION_CROSS_ORG')")
    fun overview(): ApiData = ApiData(data = orgService.overview())

    @GetMapping("business-context")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_SELECT','COLLECTION_STAT','COLLECTION_ORG_MANAGE','COLLECTION_LEDGER_CREATE','COLLECTION_LEDGER_CORRECT','COLLECTION_LEDGER_FINANCE','COLLECTION_CARE_MANAGE','COLLECTION_WAREHOUSE_MANAGE')")
    fun businessContext(): ApiData = ApiData(data = orgService.businessContext())

    @GetMapping("page")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_ORG_MANAGE')")
    fun page(@RequestParam(required = false) keyword: String?, request: HttpServletRequest): ApiData =
        ApiData(data = orgService.findPage(keyword, request))

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_SELECT')")
    fun list(): ApiData = ApiData(data = orgService.findAll())

    @GetMapping("import/template")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_ORG_MANAGE')")
    fun importTemplate(): ApiData = ApiData(data = orgService.importTemplate())

    @PostMapping("import/validate")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_ORG_MANAGE')")
    fun validateImport(@RequestBody rows: List<CollectionOrgImportRowVo>): ApiData =
        ApiData(data = orgService.validateImport(rows))

    @Log("批量导入收藏单位")
    @PostMapping("import/commit")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_ORG_MANAGE')")
    fun commitImport(@RequestBody rows: List<CollectionOrgImportRowVo>): ApiData =
        ApiData(data = orgService.commitImport(rows))

    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_SELECT')")
    fun get(@PathVariable id: String): ApiData = ApiData(data = orgService.get(id))

    @Log("收藏单位创建")
    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_ORG_MANAGE')")
    fun create(@RequestBody @Validated vo: CollectionOrgVo): ApiData =
        ApiData(data = orgService.create(vo))

    @Log("收藏单位更新")
    @PutMapping("{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_ORG_MANAGE')")
    fun update(@PathVariable id: String, @RequestBody @Validated vo: CollectionOrgVo): ApiData =
        ApiData(data = orgService.update(id, vo))

    @Log("收藏单位排序")
    @PostMapping("sort")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_ORG_MANAGE')")
    fun updateSort(@RequestBody sorts: List<GenericUpdateSort>): ApiData {
        orgService.updateSort(sorts)
        return ApiData("更新顺序成功")
    }

    @Log("收藏单位删除")
    @DeleteMapping("{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_ORG_MANAGE')")
    fun delete(@PathVariable id: String): ApiData {
        orgService.delete(id)
        return ApiData("已删除")
    }
}
