package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.collection.service.DesignatedReceptionService
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import jakarta.servlet.http.HttpServletResponse
import io.hizhengfu.cloud.sys.file.service.FileOriginalDownloadService

@RestController
@RequestMapping("\${apiPath}/collection/designated-reception")
@PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_ACQUIRE')")
class DesignatedReceptionController(private val service:DesignatedReceptionService, private val originals:FileOriginalDownloadService) {
    @GetMapping("context") fun context()=ApiData(data=service.context())
    @GetMapping("page") fun page(@RequestParam(defaultValue="1") page:Int,@RequestParam(defaultValue="20") pageSize:Int,@RequestParam(required=false) keyword:String?)=ApiData(data=service.page(page,pageSize,keyword))
    @GetMapping("{id}") fun detail(@PathVariable id:String)=ApiData(data=service.detail(id))
    @GetMapping("{id}/original/{fileId}") @Log("查阅行政指定接收案卷原件")
    fun original(@PathVariable id:String,@PathVariable fileId:String,response:HttpServletResponse) {
        service.requireOriginal(id,fileId)
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition")
        originals.download(fileId,response)
    }
    @PostMapping @Log("登记行政指定接收原件") fun create(@RequestParam expectedOrgId:String,@RequestBody v:DesignatedNoticeVo)=ApiData(data=service.create(v,expectedOrgId=expectedOrgId))
    @PostMapping("{id}/assessment") @Log("登记指定接收专业鉴定评估") fun assess(@PathVariable id:String,@RequestParam expectedOrgId:String,@RequestBody v:DesignatedAssessmentVo)=ApiData(data=service.assess(id,v,expectedOrgId=expectedOrgId))
    @PostMapping("{id}/custody") @Log("登记指定接收对象实际保管") fun custody(@PathVariable id:String,@RequestParam expectedOrgId:String,@RequestBody v:DesignatedCustodyVo)=ApiData(data=service.custody(id,v,expectedOrgId=expectedOrgId))
    @PostMapping("{id}/acceptance") @Log("本人核对指定接收及实际取得") fun accept(@PathVariable id:String,@RequestParam expectedOrgId:String,@RequestBody v:DesignatedAcceptanceVo)=ApiData(data=service.accept(id,v,expectedOrgId=expectedOrgId))
    @PostMapping("{id}/referral") @Log("登记报请原部门另行指定的实际原件") fun refer(@PathVariable id:String,@RequestParam expectedOrgId:String,@RequestBody v:DesignatedReferralVo)=ApiData(data=service.refer(id,v,expectedOrgId=expectedOrgId))
    @PostMapping("{id}/reassignment") @Log("登记另行指定接收单位的行政决定") fun reassign(@PathVariable id:String,@RequestParam expectedOrgId:String,@RequestBody v:DesignatedReassignmentVo)=ApiData(data=service.reassign(id,v,expectedOrgId=expectedOrgId))
    @PostMapping("{id}/settlement") @Log("登记另行指定后的实际承接结果") fun settle(@PathVariable id:String,@RequestParam expectedOrgId:String,@RequestBody v:DesignatedSettlementVo)=ApiData(data=service.settle(id,v,expectedOrgId=expectedOrgId))
}
