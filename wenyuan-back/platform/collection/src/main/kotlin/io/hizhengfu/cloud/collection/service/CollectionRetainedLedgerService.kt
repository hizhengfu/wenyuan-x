package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import org.jetbrains.exposed.v1.jdbc.select
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.CollectionOrgFilterPolicy
import io.hizhengfu.cloud.common.domain.GenericPage
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.service.CrudService
import jakarta.servlet.http.HttpServletRequest
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import io.hizhengfu.cloud.sys.file.dao.FileDao
import io.hizhengfu.cloud.sys.file.dao.Files

@Component
@Transactional
class CollectionRetainedLedgerService(private val scope:CollectionScopeResolver,private val archives:CollectionPermanentArchiveService) : CrudService<CollectionRetainedLedgerDao>(CollectionRetainedLedgerDao,"COLLECTION") {
    fun retain(collection:CollectionDao,toOrgId:String,transferId:String):String {
        CollectionRetainedLedgerDao.find { CollectionRetainedLedgers.transferId eq transferId }.singleOrNull()?.let {
            if (it.orgId != collection.orgId || it.collectionId != collection.id.value || it.targetOrgId != toOrgId) throw BadRequestException("原馆留存总账与本次调拨不一致")
            if(!verify(it).second)fail()
            return it.id.value
        }
        val versions=CollectionCatalogVersionDao.find { CollectionCatalogVersions.collectionId eq collection.id.value }.sortedBy { it.version }.map { it.toModel() }
        val media=CollectionMediaDao.find { CollectionMedias.collectionId eq collection.id.value }.map { it.toModel() }
        val attachments=CollectionAttachmentDao.find { CollectionAttachments.collectionId eq collection.id.value }.map { it.toModel() }
        val references=CollectionMainOriginalFileSource().references(CollectionMainOriginalFileSource.BIZ,collection.id.value)
        if(references.any { it.businessType!=CollectionMainOriginalFileSource.BIZ || (it.orgId!=collection.orgId && !CollectionMainOriginalFileSource().isRetainedVersion(it)) })
            throw BadRequestException("主档原件与调出馆归属不一致，不能生成接收依据")
        val originalIds=(references.map { it.file }+CollectionMainOriginalFileSource().historicalFileIds(collection.id.value)).map { reference ->
            FileDao.findById(reference)?.id?.value ?: FileDao.find { Files.path eq reference }.singleOrNull()?.id?.value
                ?: throw BadRequestException("调拨原件缺失或位置不唯一，请先核对原记录")
        }.distinct().sorted()
        val snapshot=CollectionRetainedLedgerSnapshot(collection.toModel(),versions,media,attachments,originalIds,
            CollectionRetainedLedgerIdentity(collection.orgId,collection.id.value,transferId,toOrgId))
        val row=CollectionRetainedLedgerDao.new {
            orgId=collection.orgId;collectionId=collection.id.value;this.transferId=transferId;totalRegNo=collection.totalRegNo;name=collection.name;targetOrgId=toOrgId
            archiveId="";this.snapshot=snapshot
        }
        row.archiveId=archives.seal(collection.orgId,"COLLECTION_RETAINED_LEDGER",row.id.value,"TRANSFERRED",snapshot,
            originalIds.joinToString(","))
        return row.id.value
    }
    fun findPage(request:HttpServletRequest):GenericPage<Map<String,Any?>> {
        val access=CollectionSearchReadAccess.require(scope,allowHistoricalOrgs=true)
        val page=GenericPage<CollectionRetainedLedgerDao>().build(request)
        val query=CollectionRetainedLedgers.selectAll()
        CollectionOrgFilterPolicy.resolve(access.orgIds,request.getParameter("orgId"))?.let { ids->query.andWhere { CollectionRetainedLedgers.orgId inList ids } }
        request.getParameter("keyword")?.trim()?.takeIf(String::isNotBlank)?.let { key->query.andWhere { (CollectionRetainedLedgers.totalRegNo like "%$key%") or (CollectionRetainedLedgers.name like "%$key%") } }
        query.orderBy(CollectionRetainedLedgers.createTime to SortOrder.DESC)
        val result=findPage(query,page)
        val rows=result.data?.map { row->
            // 列表保留定位用的账目摘要；异常原件不能拖垮整页，也不能被标为已核验或旧格式。
            val inspection=try {
                mapOf("bindingVerified" to verify(row).second,"evidenceIssue" to null)
            } catch(failure:BadRequestException) {
                mapOf("bindingVerified" to null,"evidenceIssue" to
                    (failure.message ?: "原馆留存账原件核验失败，请核对原始记录"))
            }
            row.summary()+inspection
        }
        @Suppress("UNCHECKED_CAST")
        val output=result as GenericPage<Map<String,Any?>>
        output.list=rows
        return output
    }
    fun get(id:String):Map<String,Any?> {
        val org=CollectionRetainedLedgers.select(CollectionRetainedLedgers.orgId).where {CollectionRetainedLedgers.id eq id}.singleOrNull()?.get(CollectionRetainedLedgers.orgId)
            ?: throw BadRequestException("原馆留存总账不存在")
        CollectionCurrentOperationAccess.require(scope,org,false,setOf(CollectionConst.SELECT),"原馆留存总账")
        val row=CollectionRetainedLedgerDao[id]
        val inspected=archives.readForDisplay("COLLECTION_RETAINED_LEDGER",id,org)
        val verified=verify(row,inspected.archives,verifyContent=false)
        return row.summary()+mapOf("snapshot" to row.snapshot,"archives" to verified.first,
            "bindingVerified" to verified.second.takeIf { inspected.issues.isEmpty() },
            "evidenceIssue" to inspected.issues.takeIf { it.isNotEmpty() }?.joinToString("\n"))
    }
    fun requireArchivedOriginal(id:String,archiveId:String,fileId:String) {
        if (listOf(id,archiveId,fileId).any { it.isBlank() || it.length>100 }) fail()
        get(id) // Recheck the current account, original museum and frozen metadata.
        val row=CollectionRetainedLedgerDao[id]
        verify(row) // Download still requires the original content to pass verification.
        if(row.archiveId!=archiveId)fail()
        val archive=CollectionPermanentArchiveDao.findById(archiveId) ?: fail()
        if(archive.orgId!=row.orgId || archive.bizType!="COLLECTION_RETAINED_LEDGER" ||
            archive.bizId!=id || archive.phase!="TRANSFERRED")fail()
        // get() has checked exact equality between the frozen snapshot references and this archive's
        // file rows. Historical snapshots can refer to a path while the archive stores its file ID.
        if(CollectionPermanentArchiveFileDao.find {
                (CollectionPermanentArchiveFiles.archiveId eq archiveId) and
                    (CollectionPermanentArchiveFiles.fileId eq fileId)
            }.none {it.orgId==row.orgId && it.bizId==id})fail()
    }
    /** 业务读取仅返回已封存且绑定来源的原馆快照，不开放留存总账详情接口。 */
    internal fun verifiedBoundSnapshot(id:String,orgId:String):CollectionRetainedLedgerSnapshot {
        CollectionCurrentOperationAccess.require(scope,orgId,false,setOf(CollectionConst.SELECT,CollectionConst.ACQUIRE),"原馆留存总账读取")
        val row=CollectionRetainedLedgerDao.findById(id) ?: fail()
        if(row.orgId!=orgId || !verify(row).second)fail()
        return row.snapshot
    }
    data class BoundSnapshotInspection(val snapshot:CollectionRetainedLedgerSnapshot?=null,val issue:String?=null)

    /** 历史详情可使用已核对绑定和摘要的留存快照；原件字节异常单独提示。 */
    fun boundSnapshotForDisplay(id:String,orgId:String,additionalPermissions:Set<String>,crossOrgPermission:String):BoundSnapshotInspection {
        val access=CollectionSearchReadAccess.require(scope,additionalPermissions=additionalPermissions,
            crossOrgPermission=crossOrgPermission,allowHistoricalOrgs=true)
        OrgScopedReadPolicy.requireVisible(access.orgIds,orgId,"原馆留存总账读取")
        val row=CollectionRetainedLedgerDao.findById(id) ?: fail()
        if(row.orgId!=orgId)fail()
        val inspected=archives.readForDisplay("COLLECTION_RETAINED_LEDGER",id,orgId)
        if(!verify(row,inspected.archives,verifyContent=false).second)fail()
        return BoundSnapshotInspection(row.snapshot,inspected.issues.takeIf { it.isNotEmpty() }?.joinToString("\n"))
    }

    /** 先核对当前业务读取资格；列表可以定位异常，但不取得损坏的冻结快照。 */
    @Transactional(readOnly=true,noRollbackFor=[BadRequestException::class])
    internal fun inspectBoundSnapshot(id:String,orgId:String):BoundSnapshotInspection {
        CollectionCurrentOperationAccess.require(scope,orgId,false,setOf(CollectionConst.SELECT,CollectionConst.ACQUIRE),"原馆留存总账读取")
        return try {
            val row=CollectionRetainedLedgerDao.findById(id) ?: fail()
            if(row.orgId!=orgId || !verify(row).second)fail()
            BoundSnapshotInspection(snapshot=row.snapshot)
        } catch(failure:BadRequestException) {
            BoundSnapshotInspection(issue=failure.message ?: "原馆留存账原件核验失败，请核对原始记录")
        }
    }
    /** 调用业务已完成当前馆别和职责授权；复用留存账详情的冻结快照与绑定核验。 */
    internal fun verifiedBoundSnapshotForAuthorizedBusiness(row:CollectionRetainedLedgerDao,orgId:String):CollectionRetainedLedgerSnapshot {
        if(row.orgId!=orgId || !verify(row).second)fail()
        return row.snapshot
    }
    internal fun boundSnapshotForAuthorizedDisplay(row:CollectionRetainedLedgerDao,orgId:String):BoundSnapshotInspection {
        if(row.orgId!=orgId)fail()
        val inspected=archives.readForDisplay("COLLECTION_RETAINED_LEDGER",row.id.value,orgId)
        if(!verify(row,inspected.archives,verifyContent=false).second)fail()
        return BoundSnapshotInspection(row.snapshot,inspected.issues.takeIf {it.isNotEmpty()}?.joinToString("\n"))
    }
    /** 跨模块历史业务按自身当前查阅职责核验后读取原馆冻结快照，不能从接收馆现时主档反推旧资料。 */
    fun verifiedBoundSnapshotForReader(
        id:String,orgId:String,additionalPermissions:Set<String>,crossOrgPermission:String,
    ):CollectionRetainedLedgerSnapshot {
        val access=CollectionSearchReadAccess.require(scope,additionalPermissions=additionalPermissions,
            crossOrgPermission=crossOrgPermission,allowHistoricalOrgs=true)
        OrgScopedReadPolicy.requireVisible(access.orgIds,orgId,"原馆留存总账读取")
        val row=CollectionRetainedLedgerDao.findById(id) ?: fail()
        if(row.orgId!=orgId || !verify(row).second)fail()
        return row.snapshot
    }
    /** 列表逐项展示原馆资料核验异常；当前账户和馆别授权失败仍须拒绝读取。 */
    @Transactional(readOnly=true,noRollbackFor=[BadRequestException::class])
    fun inspectBoundSnapshotForReader(
        id:String,orgId:String,additionalPermissions:Set<String>,crossOrgPermission:String,
    ):BoundSnapshotInspection {
        val access=CollectionSearchReadAccess.require(scope,additionalPermissions=additionalPermissions,
            crossOrgPermission=crossOrgPermission,allowHistoricalOrgs=true)
        OrgScopedReadPolicy.requireVisible(access.orgIds,orgId,"原馆留存总账读取")
        val row=CollectionRetainedLedgerDao.findById(id) ?: fail()
        if(row.orgId!=orgId)fail()
        return try {
            if(!verify(row).second)fail()
            BoundSnapshotInspection(snapshot=row.snapshot)
        } catch(failure:BadRequestException) {
            BoundSnapshotInspection(issue=failure.message ?: "原馆留存账原件核验失败，请核对原始记录")
        }
    }
    /** 仅供已核验调拨双方读取权限的业务服务使用；不依赖现存主档补造原号。 */
    internal fun transferNumber(transfer: CollectionTransferDao): String? {
        val row = CollectionRetainedLedgerDao.find { CollectionRetainedLedgers.transferId eq transfer.id.value }.singleOrNull()
            ?: return null // 未留存的旧格式明确保持未知。
        if (row.orgId != transfer.orgId || row.collectionId != transfer.collectionId || row.targetOrgId != transfer.toOrgId) fail()
        verify(row)
        return row.totalRegNo
    }

    private fun verify(row:CollectionRetainedLedgerDao,
        snapshots:List<Map<String,Any?>> = archives.listForAuthorizedOrg("COLLECTION_RETAINED_LEDGER",row.id.value,row.orgId),
        verifyContent:Boolean = true):Pair<List<Map<String,Any?>>,Boolean> {
        val original=snapshots.singleOrNull {it["phase"]=="TRANSFERRED"} ?: fail()
        if(original["id"]!=row.archiveId)fail()
        val saved=JsonUtils.mapper.readTree(original["snapshot"] as String)
        val snapshot=row.snapshot
        val current=JsonUtils.mapper.readTree(JsonUtils.toJson(snapshot)) as tools.jackson.databind.node.ObjectNode
        if(!saved.has("originalFileIds") && snapshot.originalFileIds.isEmpty())current.remove("originalFileIds")
        if(!saved.has("identity") && snapshot.identity==null)current.remove("identity")
        if(saved!=current || snapshot.collection.id!=row.collectionId || snapshot.collection.orgId!=row.orgId ||
            snapshot.collection.totalRegNo!=row.totalRegNo || snapshot.collection.name!=row.name || row.orgId==row.targetOrgId ||
            snapshot.catalogVersions.any {it.collectionId!=row.collectionId} ||
            snapshot.media.any {it.collectionId!=row.collectionId || it.orgId!=row.orgId} ||
            snapshot.attachments.any {it.collectionId!=row.collectionId || it.orgId!=row.orgId})fail()
        snapshot.identity?.let {if(it!=CollectionRetainedLedgerIdentity(row.orgId,row.collectionId,row.transferId,row.targetOrgId))fail()}
        val files=(snapshot.originalFileIds+listOfNotNull(snapshot.collection.coverImage)+snapshot.media.mapNotNull {it.fileId}+snapshot.attachments.mapNotNull {it.fileId}).filter {it.isNotBlank()}.distinct()
        if(verifyContent) archives.verifyExisting(CollectionPermanentArchiveDao[row.archiveId],row.orgId,"COLLECTION_RETAINED_LEDGER",
            row.id.value,"TRANSFERRED",current,files.joinToString(","))
        else archives.requireExistingMetadata(CollectionPermanentArchiveDao[row.archiveId],row.orgId,"COLLECTION_RETAINED_LEDGER",
            row.id.value,"TRANSFERRED",current,files.joinToString(","))
        return snapshots to (snapshot.identity!=null)
    }
    private fun fail():Nothing=throw BadRequestException("原馆留存总账与永久原件或业务关联不一致，请核对原账")
}
