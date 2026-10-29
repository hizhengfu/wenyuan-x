package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq

/** 同一业务的既有原件身份；调用方仍须核对当前读取权限及实际内容。 */
internal object CollectionRetainedOriginalFiles {
    /** 同一申请已归档的行政或其他阶段原件，可作为后续阶段的文件来源。 */
    fun stageIds(businessType: String, businessId: String, orgIds: Set<String>, phase: String? = null): Set<String> = CollectionPermanentArchiveDao.find {
        (CollectionPermanentArchives.bizType eq businessType) and (CollectionPermanentArchives.bizId eq businessId)
    }.filter { it.orgId in orgIds && (phase == null || it.phase == phase) }.flatMap { archive ->
        if (archive.digest != CollectionCareEvidence.digest(archive.snapshot)) throw BadRequestException("既有业务档案摘要不一致，不能继承原件")
        val files = CollectionPermanentArchiveFileDao.find { CollectionPermanentArchiveFiles.archiveId eq archive.id.value }.toList()
        if (files.map { it.fileId }.distinct().size != files.size) throw BadRequestException("既有业务档案原件引用重复")
        files.map { file ->
            if (file.orgId != archive.orgId || file.bizId != businessId) throw BadRequestException("既有业务档案原件归属不一致")
            val metadata = try { JsonUtils.mapper.readTree(file.metadata) }
                catch (_: tools.jackson.core.JacksonException) { throw BadRequestException("既有业务档案原件元数据无法解析") }
            CollectionArchiveFileIdentity.keys(CollectionArchiveFileReference(archive.id.value,archive.orgId,businessType,businessId,archive.phase,file.fileId,file.metadata),metadata)
            file.fileId
        }
    }.toSet()

    fun ids(businessType: String, businessId: String, orgIds: Set<String>? = null): Set<String> = CollectionPermanentArchiveDao.find {
        (CollectionPermanentArchives.bizType eq CollectionBusinessOriginalFiles.BIZ) and (CollectionPermanentArchives.bizId eq businessId)
    }.mapNotNull { archive ->
        if (orgIds != null && archive.orgId !in orgIds) return@mapNotNull null
        val snapshot = try { JsonUtils.mapper.readTree(archive.snapshot) }
            catch (_: tools.jackson.core.JacksonException) { throw BadRequestException("历史原件记录无法解析，请核对后办理") }
        if (snapshot.path("businessType").asString() != businessType) return@mapNotNull null
        val file = CollectionPermanentArchiveFileDao.find { CollectionPermanentArchiveFiles.archiveId eq archive.id.value }.singleOrNull()
            ?: throw BadRequestException("历史原件引用缺失或不完整，请核对后办理")
        if (!archive.phase.startsWith(CollectionBusinessOriginalFiles.PHASE) || archive.digest != CollectionCareEvidence.digest(archive.snapshot) ||
            file.orgId != archive.orgId || file.bizId != businessId || snapshot.path("fileId").asString() != file.fileId)
            throw BadRequestException("历史原件归属或留存记录不一致，请核对后办理")
        val metadata = try { JsonUtils.mapper.readTree(file.metadata) }
            catch (_: tools.jackson.core.JacksonException) { throw BadRequestException("历史原件元数据无法解析，请核对后办理") }
        CollectionArchiveFileIdentity.keys(CollectionArchiveFileReference(archive.id.value,archive.orgId,archive.bizType,businessId,archive.phase,file.fileId,file.metadata),metadata)
        file.fileId
    }.toSet()
}
