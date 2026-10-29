package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.domain.CollectionPeriodReportSignVo
import io.hizhengfu.cloud.collection.service.CollectionPeriodReportService
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@RequestMapping("\${apiPath}/collection/stat/period")
class CollectionPeriodReportController(private val service:CollectionPeriodReportService) {
    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN','${CollectionConst.SELECT}','${CollectionConst.STAT}')")
    fun preview(@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) startDate:LocalDate,
                @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) endDate:LocalDate,
                @RequestParam(required=false) orgId:String?)=ApiData(data=service.preview(startDate,endDate,orgId))

    @GetMapping("history")
    @PreAuthorize("hasAnyAuthority('ADMIN','${CollectionConst.SELECT}','${CollectionConst.STAT}')")
    fun history(@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) startDate:LocalDate,
                @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) endDate:LocalDate,
                @RequestParam(required=false) orgId:String?)=ApiData(data=service.history(startDate,endDate,orgId,verifyContent=false))

    @PostMapping("signed")
    @Log("登记期间馆藏增减及提用动态报表签署原件")
    @PreAuthorize("hasAnyAuthority('ADMIN','${CollectionConst.STAT}')")
    fun signed(@RequestBody input:CollectionPeriodReportSignVo)=ApiData(data=service.signed(input))
}
