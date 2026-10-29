package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.service.CollectionNatureReviewService
import io.hizhengfu.cloud.common.domain.ApiData
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("\${apiPath}/collection/nature-review")
class CollectionNatureController(private val service: CollectionNatureReviewService) {
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    fun page(@RequestParam(defaultValue = "1") page: Int, @RequestParam(defaultValue = "30") pageSize: Int) =
        ApiData(data = service.page(page, pageSize))
}
