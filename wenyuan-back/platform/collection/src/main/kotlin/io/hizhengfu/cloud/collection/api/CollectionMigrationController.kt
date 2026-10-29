package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.domain.CollectionMigrationRequestVo
import io.hizhengfu.cloud.collection.service.CollectionMigrationService
import io.hizhengfu.cloud.collection.service.CollectionMigrationVerificationService
import io.hizhengfu.cloud.collection.domain.CollectionMigrationVerificationContent
import io.hizhengfu.cloud.collection.domain.CollectionMigrationVerificationReviewVo
import io.hizhengfu.cloud.common.annotation.Log
import io.hizhengfu.cloud.common.domain.ApiData
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("\${apiPath}/collection/registered-migration")
class CollectionMigrationController(private val service: CollectionMigrationService, private val verification: CollectionMigrationVerificationService) {
    @GetMapping("template")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_LEDGER_CREATE')")
    fun template()=ApiData(data=service.template())
    @PostMapping("validate")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_LEDGER_CREATE')")
    fun validate(@RequestBody request: CollectionMigrationRequestVo)=ApiData(data=service.validate(request))
    @Log("迁移本馆旧系统已登账藏品")
    @PostMapping("commit")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_LEDGER_CREATE')")
    fun commit(@RequestBody request: CollectionMigrationRequestVo,@RequestParam expectedOrgId: String,@RequestParam expectedValidationHash: String)=
        ApiData(data=service.commit(request,expectedOrgId,expectedValidationHash))

    @GetMapping("verification-batches")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_SELECT','COLLECTION_LEDGER_CREATE','COLLECTION_CATALOG_AUDIT')")
    fun batches() = ApiData(data = verification.batches())

    @GetMapping("verification-batches/{batchId}")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_SELECT','COLLECTION_LEDGER_CREATE','COLLECTION_CATALOG_AUDIT')")
    fun batch(@PathVariable batchId: String) = ApiData(data = verification.batch(batchId))

    @Log("按批办理历史迁入登记复核")
    @PostMapping("verification-batches/process")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_LEDGER_CREATE','COLLECTION_CATALOG_AUDIT')")
    fun processBatch(@RequestBody request: io.hizhengfu.cloud.collection.domain.CollectionMigrationBatchRequest) = ApiData(data = verification.processBatch(request))

    @GetMapping("{collectionId}/verification")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_SELECT','COLLECTION_LEDGER_CREATE','COLLECTION_CATALOG_AUDIT')")
    fun verification(@PathVariable collectionId: String) = ApiData(data = verification.get(collectionId))

    @Log("补充历史迁入登记核验材料")
    @PostMapping("{collectionId}/verification")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_LEDGER_CREATE')")
    fun saveVerification(@PathVariable collectionId: String, @RequestBody content: CollectionMigrationVerificationContent,
        @RequestParam expectedStateHash: String, @RequestParam(defaultValue = "false") submit: Boolean) =
        ApiData(data = verification.save(collectionId, content, expectedStateHash, submit))

    @Log("撤回历史迁入登记核验材料")
    @PostMapping("{collectionId}/verification/withdraw")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_LEDGER_CREATE')")
    fun withdrawVerification(@PathVariable collectionId: String, @RequestParam expectedStateHash: String) =
        ApiData(data = verification.withdraw(collectionId, expectedStateHash))

    @Log("独立复核历史迁入登记事实")
    @PostMapping("{collectionId}/verification/review")
    @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_CATALOG_AUDIT')")
    fun reviewVerification(@PathVariable collectionId: String, @RequestBody review: CollectionMigrationVerificationReviewVo,
        @RequestParam expectedStateHash: String) = ApiData(data = verification.review(collectionId, review, expectedStateHash))
}
