package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.dao.CollectionSetEntryVo
import io.hizhengfu.cloud.collection.service.CollectionSetService
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 成套藏品的一次性主件、分件登记入口。 */
@RestController
@RequestMapping("\${apiPath}/collection/set-entry")
class CollectionSetEntryController(
    private val service: CollectionSetService,
) {
    @Log("成套藏品分件登记")
    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_LEDGER_CREATE')")
    fun create(@RequestBody @Validated vo: CollectionSetEntryVo): ApiData =
        ApiData(data = service.createSetEntry(vo))
}
