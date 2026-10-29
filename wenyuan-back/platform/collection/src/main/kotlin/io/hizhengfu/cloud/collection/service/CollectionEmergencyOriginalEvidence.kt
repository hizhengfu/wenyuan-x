package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.common.file.FileReadPath
import io.hizhengfu.cloud.sys.file.dao.Files
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.select
import io.hizhengfu.cloud.collection.service.CollectionCareEvidence as C

/** 只读核对声明附件、冻结的定位身份与访问索引；不补写历史摘要，也不据此宣称字节验收。 */
internal object CollectionEmergencyOriginalEvidence {
    fun require(archive:CollectionPermanentArchiveDao,declared:String?) {
        val refs=CollectionPermanentArchiveFileDao.find {CollectionPermanentArchiveFiles.archiveId eq archive.id.value}.toList()
        val metadata=refs.associate {ref->ref.id.value to try {
            JsonUtils.mapper.readTree(ref.metadata) ?: C.fail("应急原件文件身份为空，请核查原件")
        } catch(_:tools.jackson.core.JacksonException) {C.fail("应急原件文件身份无法解析，请核查原件")}}
        val expected=BusinessAttachmentBindingPlanner.plan(archive.bizId,"应急原件",declared).map {p->
            refs.filter {it.fileId==p.fileId || metadata.getValue(it.id.value).path("path").asString()==p.fileId}
                .map {it.fileId}.distinct().singleOrNull() ?: C.fail("应急原件声明附件缺失或不唯一")
        }.toSet()
        if(expected.isEmpty() || refs.map {it.fileId}.toSet()!=expected || refs.size!=expected.size ||
            refs.any {it.orgId!=archive.orgId || it.bizId!=archive.bizId})C.fail("应急原件附件引用或所属业务不一致")
        if(Files.select(Files.id).where {Files.id inList expected}.map {it[Files.id].value}.toSet()!=expected)
            C.fail("应急原件关联文件记录缺失，请核查原件与备份")
        val keys=refs.associate {ref->
            val node=metadata.getValue(ref.id.value)
            val frozen=CollectionArchiveFileIdentity.keys(CollectionArchiveFileReference(archive.id.value,archive.orgId,archive.bizType,archive.bizId,archive.phase,ref.fileId,ref.metadata),node)
            ref.fileId to frozen.ifEmpty {FileReadPath.keys(node.path("path").asString(),node.path("storageType").asString(),null)}
        }
        val indexes=CollectionEmergencyFileAccessDao.find {CollectionEmergencyFileAccesses.archiveId eq archive.id.value}.toList()
        if(indexes.any {it.orgId!=archive.orgId || it.fileId !in expected} || keys.any {(file,required)->
                !indexes.filter {it.fileId==file}.map {it.pathKey}.toSet().containsAll(required)
            })C.fail("应急原件访问索引缺失或所属馆不一致，请核查原件")
    }
}
