package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.dao.ExhibitionDao
import io.hizhengfu.cloud.collection.dao.ExhibitionVo
import io.hizhengfu.cloud.collection.service.CollectionPrintService
import io.hizhengfu.cloud.collection.service.ExhibitionService
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import io.hizhengfu.cloud.flow.api.FlowController
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
import org.springframework.web.bind.annotation.RestController

/** 藏品展览。 */
@RestController
@RequestMapping("\${apiPath}/collection/exhibition")
class ExhibitionController(
    private val exhibitionService: ExhibitionService,
    private val printService: CollectionPrintService,
) : FlowController<ExhibitionDao>() {

    override fun flowService() = exhibitionService

    @GetMapping("page")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE','COLLECTION_SELECT')")
    fun page(request: HttpServletRequest): ApiData = ApiData(data = exhibitionService.findPage(request))

    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE','COLLECTION_SELECT')")
    fun get(@PathVariable id: String): ApiData = ApiData(data = exhibitionService.get(id))

    @GetMapping("{id}/print")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE','COLLECTION_SELECT')")
    fun print(@PathVariable id: String): ApiData = ApiData(data = printService.exhibitionPrint(id))

    @Log("展览创建")
    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    fun create(@RequestBody @Validated vo: ExhibitionVo): ApiData =
        ApiData(data = exhibitionService.save(vo))

    @Log("展览更新")
    @PutMapping("{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    fun update(@PathVariable id: String, @RequestBody @Validated vo: ExhibitionVo): ApiData =
        ApiData(data = exhibitionService.save(vo.copy(id = id)))

    @Log("提交展览审批")
    @PostMapping("{id}/submit")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    override fun submit(@PathVariable id: String): ApiData = super.submit(id)

    @Log("撤销展览审批")
    @PostMapping("{id}/cancel")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    override fun cancel(@PathVariable id: String): ApiData = super.cancel(id)

    @Log("作废展览审批")
    @PostMapping("{id}/void")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    override fun void(@PathVariable id: String): ApiData = super.void(id)

    @GetMapping("{id}/flow")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE','COLLECTION_SELECT')")
    override fun flowInfo(@PathVariable id: String): ApiData = super.flowInfo(id)

    @Log("展览删除")
    @DeleteMapping("{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    fun delete(@PathVariable id: String): ApiData {
        exhibitionService.deleteChecked(id)
        return ApiData("已删除")
    }
}
