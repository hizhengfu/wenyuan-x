package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.dao.EntryVoucherVo
import io.hizhengfu.cloud.collection.service.EntryVoucherService
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 入馆凭证：审批通过的征集单按批次组凭证，实际核收后生成待登记记录，初始审核完成正式登账。 */
@RestController
@RequestMapping("\${apiPath}/collection/entry-voucher")
class EntryVoucherController(private val voucherService: EntryVoucherService, private val receipts: io.hizhengfu.cloud.collection.service.AccessionReceiptService) {

    @GetMapping("{id}/receipts")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_ACQUIRE','COLLECTION_SELECT','COLLECTION_LEDGER_CREATE','COLLECTION_STORAGE')")
    fun receipts(@PathVariable id: String): ApiData = ApiData(data = receipts.detail(id))

    @PostMapping("{id}/receipts")
    @Log("登记实际逐件核收、迟办及差异原件")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_ACQUIRE','COLLECTION_STORAGE')")
    fun recordReceipt(@PathVariable id: String, @RequestBody value: io.hizhengfu.cloud.collection.domain.AccessionReceiptVo): ApiData = ApiData(data = receipts.record(id,value))

    @GetMapping("page")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_ACQUIRE','COLLECTION_SELECT')")
    fun page(request: HttpServletRequest): ApiData = ApiData(data = voucherService.findPage(request))

    /** 可入凭证的征集单（审批通过 + 未登账 + 未被其他凭证占用）。 */
    @GetMapping("registrable")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_ACQUIRE','COLLECTION_SELECT')")
    fun registrable(): ApiData = ApiData(data = voucherService.registrableAcquisitions())

    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_ACQUIRE','COLLECTION_SELECT')")
    fun get(@PathVariable id: String): ApiData = ApiData(data = voucherService.get(id))

    @GetMapping("{id}/print")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_ACQUIRE','COLLECTION_SELECT')")
    fun print(@PathVariable id: String): ApiData = ApiData(data = voucherService.print(id))

    @Log("保存入馆凭证")
    @PostMapping("save")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_ACQUIRE')")
    fun save(@RequestBody @Validated vo: EntryVoucherVo): ApiData = ApiData(data = voucherService.save(vo))

    @Log("删除入馆凭证")
    @DeleteMapping("{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_ACQUIRE')")
    fun delete(@PathVariable id: String): ApiData = ApiData(data = voucherService.remove(id))

    @Log("依据已核收原件生成待登记记录")
    @PostMapping("{id}/execute")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_LEDGER_CREATE')")
    fun execute(@PathVariable id: String, @RequestBody(required = false) request: io.hizhengfu.cloud.collection.domain.EntryVoucherExecuteVo?): ApiData =
        ApiData(data = voucherService.execute(id, request ?: io.hizhengfu.cloud.collection.domain.EntryVoucherExecuteVo()))

    @GetMapping("{id}/registration-targets")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_LEDGER_CREATE')")
    fun registrationTargets(@PathVariable id: String): ApiData = ApiData(data = voucherService.registrationTargets(id))
}
