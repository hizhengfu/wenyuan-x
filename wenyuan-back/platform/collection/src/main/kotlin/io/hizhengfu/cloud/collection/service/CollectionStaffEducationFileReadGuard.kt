package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.common.domain.FILE_PREFIX
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.file.FileReadMetadataBatchAuthorizationGuard
import io.hizhengfu.cloud.common.file.FileMetadataLocation
import io.hizhengfu.cloud.common.file.FileReadPath
import io.hizhengfu.cloud.common.file.LocalFileObjectStorage
import io.hizhengfu.cloud.sys.file.dao.Files
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** 培训原件含人员任职和考核资料；保留原路径及本地身份，读取时核对当前权限。 */
@Component
@Transactional(noRollbackFor = [io.hizhengfu.cloud.common.exception.BadRequestException::class])
class CollectionStaffEducationFileReadGuard(private val access:StaffEducationAccess,private val localStorage:LocalFileObjectStorage?=null):FileReadMetadataBatchAuthorizationGuard {
    private data class Reference(val orgId:String,val educationId:String)
    private fun refs(path:String,storageType:String?):List<Reference> {
        val normalized=FileReadPath.normalized(path,storageType)
        val candidates=setOf(path,normalized,"/$normalized","$FILE_PREFIX$normalized",normalized.removePrefix("rustfs/"),"$FILE_PREFIX${normalized.removePrefix("rustfs/")}")
        val ids=Files.selectAll().where {Files.path inList candidates}.map {it[Files.id].value}
        val frozen=CollectionStaffEducationFileAccessDao.find {CollectionStaffEducationFileAccesses.pathKey inList FileReadPath.keys(path,storageType,localStorage)}.toList()
        val current=if(ids.isEmpty())emptyList() else CollectionStaffEducationFileAccessDao.find {CollectionStaffEducationFileAccesses.fileId inList ids}.toList()
        // 访问索引是检索辅助。索引缺失时仍从永久归档的文件身份与原路径识别受保护文件。
        val originalRefs=CollectionPermanentArchives.join(CollectionPermanentArchiveFiles,JoinType.INNER,
            CollectionPermanentArchives.id,CollectionPermanentArchiveFiles.archiveId)
            .select(CollectionPermanentArchives.orgId,CollectionPermanentArchives.bizId,CollectionPermanentArchiveFiles.fileId,CollectionPermanentArchiveFiles.metadata)
            .where {CollectionPermanentArchives.bizType eq CollectionStaffEducationService.BIZ}.mapNotNull {r ->
                val metadata=try {JsonUtils.mapper.readTree(r[CollectionPermanentArchiveFiles.metadata])}
                    catch (_:tools.jackson.core.JacksonException){throw BadRequestException("教育原件路径无法核对，请检查归档元数据")}
                val originalPath=metadata.path("path").asString()
                val originalType=metadata.path("storageType").asString()
                if(r[CollectionPermanentArchiveFiles.fileId] in ids || (originalPath.isNotBlank() && FileReadPath.key(originalPath,originalType)==FileReadPath.key(path,storageType)))
                    Reference(r[CollectionPermanentArchives.orgId],r[CollectionPermanentArchives.bizId]) else null
            }
        return ((frozen+current).map {Reference(it.orgId,it.educationId)}+originalRefs).distinct()
    }
    override fun isProtected(path:String,storageType:String?)=refs(path,storageType).isNotEmpty()
    override fun requireReadable(path:String,storageType:String?) {
        requireReferences(refs(path,storageType))
    }
    private fun requireReferences(refs:List<Reference>) {
        refs.distinct().forEach {ref->
            val e=CollectionStaffEducationDao.findById(ref.educationId) ?: throw BadRequestException("教育原件所属记录缺失，请核对原件")
            if(e.orgId!=ref.orgId)throw BadRequestException("教育原件所属馆不一致")
            StaffEducationReadIdentity.require(e)
            access.require(e.orgId,e.userId)
        }
    }
    override fun checkReadable(path:String,storageType:String?):Boolean =
        isProtected(path,storageType).also {if(it)requireReadable(path,storageType)}
    override fun checkMetadataReadable(path:String,storageType:String?)=checkReadable(path,storageType)
    override fun checkMetadataReadableMany(locations:List<FileMetadataLocation>):Map<FileMetadataLocation,Result<Boolean>> {
        if(locations.isEmpty())return emptyMap()
        val batch=CollectionFileMetadataBatch<Reference>(locations,localStorage)
        val f=CollectionStaffEducationFileAccesses
        f.join(Files,JoinType.LEFT,f.fileId,Files.id).select(f.orgId,f.educationId,f.pathKey,Files.path).forEach {r->
            batch.add(Reference(r[f.orgId],r[f.educationId]),r[f.pathKey],r.getOrNull(Files.path))
        }
        // 索引缺失仍参加归档核验；损坏的归档元数据不得降级成不受保护。
        val a=CollectionPermanentArchives;val original=CollectionPermanentArchiveFiles
        try {
            a.join(original,JoinType.INNER,a.id,original.archiveId).join(Files,JoinType.LEFT,original.fileId,Files.id)
                .select(a.orgId,a.bizId,original.metadata,Files.path).where {a.bizType eq CollectionStaffEducationService.BIZ}
                .forEach {r->
                    val metadata=try {JsonUtils.mapper.readTree(r[original.metadata])}
                        catch (_:tools.jackson.core.JacksonException) {throw BadRequestException("教育原件路径无法核对，请检查归档元数据")}
                    val path=metadata.path("path").asString()
                    batch.add(Reference(r[a.orgId],r[a.bizId]),
                        path.takeIf {it.isNotBlank()}?.let {FileReadPath.key(it,metadata.path("storageType").asString())},
                        r.getOrNull(Files.path))
                }
        } catch(e:BadRequestException) {return locations.distinct().associateWith {Result.failure(e)}}
        return batch.results(::requireReferences)
    }
}
