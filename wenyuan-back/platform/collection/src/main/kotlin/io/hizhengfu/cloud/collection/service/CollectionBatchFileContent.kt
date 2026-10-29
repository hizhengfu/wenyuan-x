package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.file.FileContentBaseline
import io.hizhengfu.cloud.common.file.FileObjectRevision
import io.hizhengfu.cloud.sys.file.dao.FileDao
import io.hizhengfu.cloud.sys.file.service.FileService
import org.jetbrains.exposed.v1.dao.entityCache
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager

/** 实际读取确认文件；不信任控件摘要或 sys_file.hash，也不持有整个文件的内存副本。 */
internal data class CollectionBatchFileContent(
    val id: String, val path: String, val storageType: String, val name: String?,
    val recordedHash: String?, val recordedSize: Long?, val revision: FileObjectRevision?, val content: FileContentBaseline,
) {
    companion object {
        fun read(service: FileService, id: String): CollectionBatchFileContent {
            val file = FileDao.findById(id) ?: throw BadRequestException("文件记录不存在，请重新上传")
            TransactionManager.current().entityCache.flush()
            file.refresh()
            val storage = service.fileObjectStorage
            val metadata = storage.metadata(file.path, file.storageType.name)
            val bytes = (metadata.revision?.let { storage.openRevision(file.path, file.storageType.name, it) }
                ?: storage.openStream(file.path, file.storageType.name)).use { FileContentBaseline.read(it, maximumBytes = metadata.size) }
            if (bytes.size != metadata.size) throw BadRequestException("文件在核对期间发生变化，请重新预校验")
            return CollectionBatchFileContent(id, file.path, file.storageType.name, file.name, file.hash, file.size, metadata.revision, bytes)
        }
    }
}
