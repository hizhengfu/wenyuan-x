package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.domain.EnumDisclosureSubject
import java.time.LocalDateTime

/** Optional module source identity only. Public text still comes exclusively from a reviewed disclosure. */
data class CollectionDisclosureSource(
    val revision: String, val name: String, val ready: Boolean, val effectiveAt: LocalDateTime?,
    val expertIds: List<String> = emptyList(),
)
interface CollectionDisclosureSourceProvider {
    val subject: EnumDisclosureSubject
    fun source(orgId: String, id: String, lock: Boolean): CollectionDisclosureSource?
}
