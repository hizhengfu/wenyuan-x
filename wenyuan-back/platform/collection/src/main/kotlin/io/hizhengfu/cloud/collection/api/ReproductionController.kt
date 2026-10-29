package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.dao.ReproductionDao
import io.hizhengfu.cloud.collection.dao.ReproductionVo
import io.hizhengfu.cloud.collection.service.CollectionPrintService
import io.hizhengfu.cloud.collection.service.ReproductionService
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import io.hizhengfu.cloud.flow.api.FlowController
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 复制 / 拓印 / 影像采集。 */
@RestController
@RequestMapping("\${apiPath}/collection/reproduction")
class ReproductionController(
    private val reproductionService: ReproductionService,
    private val printService: CollectionPrintService,
) : FlowController<ReproductionDao>() {

    override fun flowService() = reproductionService

    @GetMapping("page")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE','COLLECTION_SELECT')")
    fun page(request: HttpServletRequest): ApiData = ApiData(data = reproductionService.findPage(request))

    @Log("创建复制/拓印记录")
    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    fun create(@RequestBody @Validated vo: ReproductionVo): ApiData = ApiData(data = reproductionService.create(vo))

    @Log("提交复制/拓印审批")
    @PostMapping("{id}/submit")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    override fun submit(@PathVariable id: String): ApiData = super.submit(id)

    @Log("撤销复制/拓印审批")
    @PostMapping("{id}/cancel")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    override fun cancel(@PathVariable id: String): ApiData = super.cancel(id)

    @Log("作废复制/拓印审批")
    @PostMapping("{id}/void")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    override fun void(@PathVariable id: String): ApiData = super.void(id)

    @GetMapping("{id}/flow")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE','COLLECTION_SELECT')")
    override fun flowInfo(@PathVariable id: String): ApiData = super.flowInfo(id)

    @GetMapping("{id}/print")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE','COLLECTION_SELECT')")
    fun print(@PathVariable id: String): ApiData = ApiData(data = printService.reproductionPrint(id))
}
