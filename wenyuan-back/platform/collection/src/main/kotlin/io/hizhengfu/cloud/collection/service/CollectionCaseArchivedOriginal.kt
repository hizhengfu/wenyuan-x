package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.common.exception.BadRequestException

/** Called only after the owning service has produced its authorized, verified detail view. */
internal object CollectionCaseArchivedOriginal {
    fun require(detail: Map<String, Any?>, archiveId: String, fileId: String) {
        if (archiveId.isBlank() || fileId.isBlank() || archiveId.length > 100 || fileId.length > 100) {
            throw BadRequestException("归档原件标识不正确")
        }
        val archives = detail["archives"] as? List<*> ?: throw BadRequestException("当前案卷无可读的永久档案")
        val archive = archives.filterIsInstance<Map<*, *>>().singleOrNull { it["id"] == archiveId }
            ?: throw BadRequestException("该永久档案不属于当前案卷")
        val files = archive["files"] as? List<*> ?: throw BadRequestException("永久档案文件清单无效")
        if (files.filterIsInstance<Map<*, *>>().none { it["fileId"] == fileId }) {
            throw BadRequestException("该文件不是所选永久档案的原件")
        }
    }
}
