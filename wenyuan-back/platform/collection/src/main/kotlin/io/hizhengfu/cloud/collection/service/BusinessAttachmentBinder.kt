package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.sys.file.service.FileService
import org.springframework.stereotype.Component
import org.jetbrains.exposed.v1.core.eq

data class BusinessAttachmentBindingItem(
    val fileId: String,
    val contentId: String,
    val aliasName: String,
)

object BusinessAttachmentBindingPlanner {
    fun plan(contentId: String, aliasPrefix: String, attachmentIds: String?): List<BusinessAttachmentBindingItem> =
        attachmentIds
            ?.split(",", "，", ";", "；", "\n", "\r\n")
            ?.mapNotNull { it.trim().takeIf(String::isNotBlank) }
            ?.distinct()
            ?.mapIndexed { index, fileId ->
                BusinessAttachmentBindingItem(
                    fileId = fileId,
                    contentId = contentId,
                    aliasName = "$aliasPrefix ${index + 1}",
                )
            }
            ?: emptyList()

    fun planSingle(contentId: String, aliasName: String, fileId: String?): List<BusinessAttachmentBindingItem> =
        fileId
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.let { listOf(BusinessAttachmentBindingItem(it, contentId, aliasName)) }
            ?: emptyList()
}

@Component
class BusinessAttachmentBinder(
    private val fileService: FileService,
) {
    fun resolveSingle(fileReference: String?): String? = fileReference
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?.let(fileService::resolveFileId)

    /** 迁移原账依据不能仅凭文件ID存在就关联，提交时重核当前读取资格。 */
    fun requireReadableFileId(fileId: String) { fileService.readableMetadata(listOf(fileId)) }

    /** 文件名必须唯一且当前可读；不把文件管理列表可见性当成字节读取授权。 */
    fun resolveByName(name: String): String {
        val matches = io.hizhengfu.cloud.sys.file.dao.FileDao.find {
            io.hizhengfu.cloud.sys.file.dao.Files.name eq name.trim()
        }.limit(2).toList()
        if (matches.isEmpty()) throw io.hizhengfu.cloud.common.exception.BadRequestException("文件「${name.trim()}」不存在，请先上传")
        if (matches.size != 1) throw io.hizhengfu.cloud.common.exception.BadRequestException("文件「${name.trim()}」重名，请使用唯一文件名或旧模板中的文件ID")
        val id = matches.single().id.value
        fileService.readableMetadata(listOf(id))
        return id
    }

    /** 新模板按文件名解析，旧模板按原来的 ID/路径解析；同时填写时必须指向同一组文件。 */
    fun resolveImportNames(names: String?, references: String?): String? {
        fun split(value: String?) = value.orEmpty().split(",", "，", ";", "；", "\n", "\r\n")
            .map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val named = split(names)
        val legacy = split(references)
        if (named.isEmpty()) return references?.trim()?.takeIf { it.isNotEmpty() }
        val ids = named.map(::resolveByName).distinct()
        if (legacy.isNotEmpty() && legacy.map { resolveSingle(it)!! }.toSet() != ids.toSet())
            throw io.hizhengfu.cloud.common.exception.BadRequestException("附件文件名与旧文件引用不一致")
        return ids.joinToString(",")
    }

    fun bind(contentId: String, aliasPrefix: String, attachmentIds: String?) {
        BusinessAttachmentBindingPlanner.plan(contentId, aliasPrefix, attachmentIds).forEach { item ->
            fileService.insertFileContent(item.fileId, item.contentId, item.aliasName)
        }
    }

    fun bindSingle(contentId: String, aliasName: String, fileId: String?) {
        BusinessAttachmentBindingPlanner.planSingle(contentId, aliasName, fileId).forEach { item ->
            fileService.insertFileContent(item.fileId, item.contentId, item.aliasName)
        }
    }
}
