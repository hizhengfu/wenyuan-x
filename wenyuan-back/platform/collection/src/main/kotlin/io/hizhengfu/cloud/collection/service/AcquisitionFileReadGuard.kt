package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.file.FileMetadataLocation
import io.hizhengfu.cloud.common.file.FileReadMetadataBatchAuthorizationGuard
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Transactional(noRollbackFor = [io.hizhengfu.cloud.common.exception.BadRequestException::class])
class AcquisitionFileReadGuard(
    private val originals: AcquisitionOriginalFiles,
    private val authorization: CollectionFileReadAuthorization,
) : FileReadMetadataBatchAuthorizationGuard {
    override fun isProtected(path: String, storageType: String?) = originals.organizations(path, storageType).isNotEmpty()
    override fun requireReadable(path: String, storageType: String?) { checkReadable(path, storageType) }
    override fun checkReadable(path: String, storageType: String?): Boolean {
        val orgs = originals.organizations(path, storageType)
        authorize(orgs)
        return orgs.isNotEmpty()
    }
    override fun checkMetadataReadable(path: String, storageType: String?) = checkReadable(path, storageType)
    override fun checkMetadataReadableMany(locations: List<FileMetadataLocation>): Map<FileMetadataLocation, Result<Boolean>> =
        originals.organizationsMany(locations).mapValues { (_, source) ->
            try { val orgs = source.getOrThrow(); authorize(orgs); Result.success(orgs.isNotEmpty()) }
            catch (e: BadRequestException) { Result.failure(e) }
        }
    private fun authorize(orgs: Set<String>) {
        if (orgs.isNotEmpty()) authorization.requireBusinessReadable(orgs, AcquisitionConfidentialAccess.PERMISSIONS,
            CollectionConst.CROSS_ORG, "征集估价、谈判及专项原件", AcquisitionConfidentialAccess.ROLES)
    }
}
