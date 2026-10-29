package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.common.domain.FILE_PREFIX
import io.hizhengfu.cloud.common.file.FileReadMetadataBatchAuthorizationGuard
import io.hizhengfu.cloud.common.file.FileMetadataLocation
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.jdbc.select
import io.hizhengfu.cloud.common.file.FileReadPath
import io.hizhengfu.cloud.sys.file.dao.Files
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** 预案含联络、安防和实物保护资料；原路径与当前路径均不能通过公开文件入口绕过。 */
@Component
@Transactional(noRollbackFor = [io.hizhengfu.cloud.common.exception.BadRequestException::class])
class CollectionEmergencyFileReadGuard(private val authorization:CollectionFileReadAuthorization, private val localStorage:io.hizhengfu.cloud.common.file.LocalFileObjectStorage? = null):FileReadMetadataBatchAuthorizationGuard, io.hizhengfu.cloud.common.file.FileReadDeploymentScopeGuard {
    private fun refs(path:String,storageType:String?):List<CollectionEmergencyFileAccessDao> {
        val normalized=FileReadPath.normalized(path,storageType)
        val candidates=setOf(path,normalized,"/$normalized","$FILE_PREFIX$normalized",normalized.removePrefix("rustfs/"),"$FILE_PREFIX${normalized.removePrefix("rustfs/")}")
        val ids=Files.selectAll().where {Files.path inList candidates}.map {it[Files.id].value}
        val frozen=CollectionEmergencyFileAccessDao.find {CollectionEmergencyFileAccesses.pathKey inList FileReadPath.keys(path,storageType,localStorage)}.toList()
        val current=if(ids.isEmpty())emptyList() else CollectionEmergencyFileAccessDao.find {CollectionEmergencyFileAccesses.fileId inList ids}.toList()
        return (frozen+current).distinctBy {it.id.value}
    }
    override fun isProtected(path:String,storageType:String?)=refs(path,storageType).isNotEmpty()
    override fun requireDeploymentScope(path:String,storageType:String?) {
        authorization.requireDeploymentScope(refs(path,storageType).map {it.orgId}.toSet(), "藏品应急预案与演练原件")
    }
    override fun requireReadable(path:String,storageType:String?) {
        val refs=refs(path,storageType);if(refs.isEmpty())return
        refs.map { it.orgId }.toSet().forEach { orgId ->
            authorization.requireBusinessReadable(orgId, setOf("ADMIN", CollectionConst.ADMIN, CollectionConst.EMERGENCY), CollectionConst.CROSS_ORG, "藏品应急预案与演练原件")
        }
    }
    override fun checkReadable(path:String,storageType:String?):Boolean =
        isProtected(path,storageType).also { if(it)requireReadable(path,storageType) }
    override fun checkMetadataReadable(path:String,storageType:String?)=checkReadable(path,storageType)
    override fun checkMetadataReadableMany(locations:List<FileMetadataLocation>):Map<FileMetadataLocation,Result<Boolean>> {
        if(locations.isEmpty())return emptyMap()
        val batch=CollectionFileMetadataBatch<String>(locations,localStorage)
        val f=CollectionEmergencyFileAccesses
        f.join(Files,JoinType.LEFT,f.fileId,Files.id).select(f.orgId,f.pathKey,Files.path).forEach {r->
            batch.add(r[f.orgId],r[f.pathKey],r.getOrNull(Files.path))
        }
        return batch.results {orgIds->authorization.requireBusinessReadable(orgIds.toSet(), setOf("ADMIN", CollectionConst.ADMIN, CollectionConst.EMERGENCY), CollectionConst.CROSS_ORG, "藏品应急预案与演练原件")}
    }
}
