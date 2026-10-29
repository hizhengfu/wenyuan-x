package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.service.CollectionOriginalHistoryService
import io.hizhengfu.cloud.common.domain.ApiData
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("\${apiPath}/collection/original-history")
@PreAuthorize("isAuthenticated()")
class CollectionOriginalHistoryController(private val service: CollectionOriginalHistoryService) {
    @GetMapping
    fun page(@RequestParam(required = false) businessType: String?, @RequestParam(required = false) businessId: String?,
             @RequestParam(required = false) keyword: String?, @RequestParam(defaultValue = "1") pageNum: Long,
             @RequestParam(defaultValue = "20") pageSize: Int, response: HttpServletResponse): ApiData {
        response.setHeader("Cache-Control", "private, no-store")
        return ApiData(data = service.page(businessType, businessId, keyword, pageNum, pageSize))
    }

    @GetMapping("{id}/download")
    fun download(@PathVariable id: String, response: HttpServletResponse) = service.download(id, response)
}
