package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import org.springframework.stereotype.Component

@Component
class CollectionDisclosureArchiveReadPolicy(
    private val disclosures: CollectionDisclosureService,
    private val authorization: CollectionFileReadAuthorization,
) : CollectionArchiveReadPolicy {
    override val businessTypes = setOf(CollectionDisclosureService.BIZ)
    override fun requireReadable(reference: CollectionArchiveFileReference) {
        if (disclosures.isPublicArchiveFile(reference)) return
        authorization.requireBusinessReadable(reference.orgId, setOf("ADMIN", CollectionConst.ADMIN, CollectionConst.PUBLISH), CollectionConst.CROSS_ORG, "公开核查和实发原件")
    }
}
