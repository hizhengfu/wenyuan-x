package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.domain.CollectionQueryVo
import io.hizhengfu.cloud.collection.service.CollectionExchangeService
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files

@RestController
@RequestMapping("\${apiPath}/collection/exchange")
class CollectionExchangeController(private val service: CollectionExchangeService) {
    @PostMapping("check")
    @PreAuthorize("hasAnyAuthority('ADMIN','${CollectionConst.SELECT}','${CollectionConst.STAT}')")
    fun check(@RequestBody query: CollectionQueryVo) = ApiData(data=service.check(query))

    @PostMapping("package")
    @Log("下载馆藏文物规范交换包")
    @PreAuthorize("hasAnyAuthority('ADMIN','${CollectionConst.SELECT}','${CollectionConst.STAT}')")
    fun download(@RequestBody query: CollectionQueryVo, response: HttpServletResponse) {
        service.build(query).use { file ->
            response.contentType="application/zip"
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename*=UTF-8''${URLEncoder.encode(file.name,StandardCharsets.UTF_8).replace("+","%20")}")
            response.setContentLengthLong(Files.size(file.path))
            Files.newInputStream(file.path).use { it.copyTo(response.outputStream) }
        }
    }
}
