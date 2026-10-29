package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.dao.MovementVo
import io.hizhengfu.cloud.collection.domain.MovementImportRowVo
import io.hizhengfu.cloud.collection.service.CollectionPrintService
import io.hizhengfu.cloud.collection.service.StorageMovementService
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 出入库流水。 */
@RestController
@RequestMapping("\${apiPath}/collection/movement")
class StorageMovementController(
    private val movementService: StorageMovementService,
    private val printService: CollectionPrintService,
) {

    @GetMapping("page")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE','COLLECTION_SELECT')")
    fun page(request: HttpServletRequest): ApiData =
        ApiData(data = movementService.findPage(request))

    @Log("藏品出入库")
    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    fun create(@RequestBody @Validated vo: MovementVo): ApiData =
        ApiData(data = movementService.create(vo))

    @GetMapping("import/template")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE','COLLECTION_SELECT')")
    fun importTemplate(): ApiData = ApiData(data = movementService.importTemplate())

    @PostMapping("import/validate")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    fun validateImport(@RequestBody rows: List<MovementImportRowVo>): ApiData =
        ApiData(data = movementService.validateImport(rows))

    @Log("批量导入出入库流水")
    @PostMapping("import/commit")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE')")
    fun commitImport(@RequestBody rows: List<MovementImportRowVo>): ApiData =
        ApiData(data = movementService.commitImport(rows))

    @GetMapping("collection/{collectionId}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_SELECT')")
    fun byCollection(@PathVariable collectionId: String): ApiData =
        ApiData(data = movementService.findByCollection(collectionId))

    @GetMapping("{id}/print")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_STORAGE','COLLECTION_SELECT')")
    fun print(@PathVariable id: String): ApiData =
        ApiData(data = printService.movementPrint(id))
}
