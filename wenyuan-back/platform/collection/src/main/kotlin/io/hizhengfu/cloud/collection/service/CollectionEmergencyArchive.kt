package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.sys.file.dao.FileDao
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.dao.entityCache
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager

/** 同一原件保护边界供预案、报告与责任承接共同使用。 */
internal class CollectionEmergencyArchive(private val archives:CollectionPermanentArchiveService,private val access:CollectionEmergencyAccess) {
    fun seal(org:String,id:String,phase:String,value:Any,files:String?):String {
        val actor=io.hizhengfu.cloud.common.utils.SecurityUtils.getNullUserDetail()?.id
        val archive=archives.seal(org,CollectionEmergencyEvidence.BIZ,id,phase,value,files)
        val expectedKeys=mutableSetOf<Pair<String,String>>()
        fun reference(r:CollectionPermanentArchiveFileDao)=listOf(r.orgId,r.archiveId,r.bizId,r.fileId,r.metadata)
        val preparedRefs=CollectionPermanentArchiveFileDao.find {CollectionPermanentArchiveFiles.archiveId eq archive}.toList()
        val expectedRefs=preparedRefs.associate {it.id.value to reference(it)}
        preparedRefs.forEach {ref->
            val file=FileDao.findById(ref.fileId) ?: CollectionCareEvidence.fail("永久原件文件不存在")
            archives.fileReadKeys(file).forEach { key ->
                expectedKeys.add(ref.fileId to key)
                if(!CollectionEmergencyFileAccessDao.find {(CollectionEmergencyFileAccesses.archiveId eq archive) and (CollectionEmergencyFileAccesses.fileId eq ref.fileId) and (CollectionEmergencyFileAccesses.pathKey eq key)}.any())CollectionEmergencyFileAccessDao.new {orgId=org;archiveId=archive;fileId=ref.fileId;pathKey=key}
            }
        }
        access.finish(org,actor)
        TransactionManager.current().entityCache.flush()
        val saved=CollectionPermanentArchiveDao.find {CollectionPermanentArchives.id eq archive}.singleOrNull()
            ?: CollectionCareEvidence.fail("应急永久原件保存后缺失，请核查")
        saved.refresh(flush=false)
        val refs=CollectionPermanentArchiveFileDao.find {CollectionPermanentArchiveFiles.archiveId eq archive}.onEach {it.refresh(flush=false)}.toList()
        if(refs.associate {it.id.value to reference(it)}!=expectedRefs)
            CollectionCareEvidence.fail("应急原件保存期间附件身份发生变化，不能降为旧格式或替换引用")
        val fileIds=refs.map {it.fileId}.toSet()
        val actualFiles=FileDao.find {io.hizhengfu.cloud.sys.file.dao.Files.id inList fileIds}.onEach {it.refresh(flush=false)}.toList()
        if(actualFiles.map {it.id.value}.toSet()!=fileIds)CollectionCareEvidence.fail("应急原件文件保存后缺失，请核查")
        archives.verifyExisting(saved,org,CollectionEmergencyEvidence.BIZ,id,phase,value,files)
        val keys=CollectionEmergencyFileAccessDao.find {CollectionEmergencyFileAccesses.archiveId eq archive}.onEach {it.refresh(flush=false)}.toList()
        if(keys.any {it.orgId!=org} || keys.map {it.fileId to it.pathKey}.toSet()!=expectedKeys)
            CollectionCareEvidence.fail("应急原件访问索引保存后缺失或所属馆不一致")
        return archive
    }
}
