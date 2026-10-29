package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.collection.service.DigitalAcquisitionService
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("\${apiPath}/collection/digital-acquisition")
// 各业务入口由服务读取当前职责和馆别；不以旧登录权限阻止已生效的岗位授权。
@PreAuthorize("isAuthenticated()")
class DigitalAcquisitionController(private val service:DigitalAcquisitionService,private val storage:io.hizhengfu.cloud.common.file.FileObjectStorage) {
    @GetMapping("{id}")
    fun detail(@PathVariable id:String)=ApiData(data=service.detail(id))

    @GetMapping("{id}/file/{fileId}")
    @Log("读取数字采集永久原件")
    fun original(@PathVariable id:String,@PathVariable fileId:String,response:jakarta.servlet.http.HttpServletResponse) {
        val original=service.originalDownload(id,fileId)
        val file=original.file
        fun output(input:java.io.InputStream) {
            // 大文件核验期间可能发生撤权或档案变化；重新调用服务完成独立事务核对。
            if(service.originalDownload(id,fileId)!=original)throw io.hizhengfu.cloud.common.exception.BadRequestException("数字原件身份已变化，请重新核对")
            response.setHeader("Cache-Control","private, no-store")
            response.setHeader("X-Content-Type-Options","nosniff")
            response.setHeader("X-Collection-Content-Integrity",if(original.baseline==null)"not-recorded" else "sha256-verified")
            response.contentType="application/octet-stream"
            val name=java.net.URLEncoder.encode(file.name ?: fileId,"UTF-8").replace("+","%20")
            response.setHeader("Content-Disposition","attachment;filename*=utf-8''$name")
            response.addHeader("Access-Control-Expose-Headers","Content-Disposition, X-Collection-Content-Integrity")
            input.copyTo(response.outputStream)
        }
        val stream=try {
            original.revision?.let {storage.openRevision(file.path!!,file.storageType,it)} ?: storage.openStream(file.path!!,file.storageType)
        } catch (_:java.nio.file.NoSuchFileException) {
            throw io.hizhengfu.cloud.common.exception.BadRequestException("数字采集原件文件缺失，请核查原件存储与备份恢复")
        } catch (_:java.io.FileNotFoundException) {
            throw io.hizhengfu.cloud.common.exception.BadRequestException("数字采集原件文件无法读取，请核查存储权限与备份恢复")
        }
        stream.use {input ->
            if(original.baseline==null)output(input)
            else original.baseline.withVerifiedCopy(input) {copy ->java.nio.file.Files.newInputStream(copy).use(::output)}
        }
    }

    @PostMapping("{id}/plan")
    @Log("登记信息化与保管共同制定的数字采集方案")
    fun plan(@PathVariable id:String,@RequestBody value:DigitalAcquisitionPlanVo)=ApiData(data=service.savePlan(id,value))

    @PostMapping("{id}/start")
    @Log("登记实际数字采集开始及实物安全核查")
    fun start(@PathVariable id:String,@RequestBody value:DigitalAcquisitionStartVo)=ApiData(data=service.start(id,value))

    @PostMapping("{id}/capture")
    @Log("登记实际采集结束与原始数据字节校验")
    fun capture(@PathVariable id:String,@RequestBody value:DigitalCaptureVo)=ApiData(data=service.capture(id,value))

    @PostMapping("{id}/resource")
    @Log("登记数字资源实际加工整理储存及安全记录")
    fun resource(@PathVariable id:String,@RequestBody value:DigitalResourceStageVo)=ApiData(data=service.resource(id,value))

    @PostMapping("{id}/complete")
    @Log("登记数字资源质量与安全共同复核")
    fun complete(@PathVariable id:String,@RequestBody value:DigitalAcquisitionCompletionVo)=ApiData(data=service.complete(id,value))
}
