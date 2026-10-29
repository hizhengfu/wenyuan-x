package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.sys.file.dao.FileDao
import org.jetbrains.exposed.v1.core.eq

/** 责任承接读取只核对已冻结的文件身份，不按改名后的新路径猜测原件。 */
object CollectionResponsibilityArchiveEvidence {
    fun references(a: CollectionPermanentArchiveDao, declared: String) {
        val refs = CollectionPermanentArchiveFileDao.find { CollectionPermanentArchiveFiles.archiveId eq a.id.value }.toList()
        val expected = BusinessAttachmentBindingPlanner.plan(a.bizId,"责任承接原件",declared).map { p ->
            refs.filter { it.fileId == p.fileId || JsonUtils.mapper.readTree(it.metadata).path("path").asString() == p.fileId }.map { it.fileId }.distinct().singleOrNull()
                ?: CollectionCareEvidence.fail("责任承接原件引用缺失或不唯一")
        }.toSet()
        if (expected.isEmpty() || refs.map { it.fileId }.toSet() != expected || refs.any { it.orgId != a.orgId || it.bizId != a.bizId || FileDao.findById(it.fileId) == null })
            CollectionCareEvidence.fail("责任承接原件引用或所属馆不一致")
    }
}
