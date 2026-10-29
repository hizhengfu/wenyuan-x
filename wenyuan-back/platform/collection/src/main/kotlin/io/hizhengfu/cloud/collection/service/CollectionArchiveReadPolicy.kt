package io.hizhengfu.cloud.collection.service

/** 各业务声明其永久原件的读取职责；共享文件须满足所有来源的读取条件。 */
data class CollectionArchiveFileReference(
    val archiveId: String,
    val orgId: String,
    val businessType: String,
    val businessId: String,
    val phase: String,
    val fileId: String,
    val metadata: String,
)

interface CollectionArchiveReadPolicy {
    val businessTypes: Set<String>
    fun requireReadable(reference: CollectionArchiveFileReference)
}
