package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.autoconfigure.exposed.canonicalExposedValue
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.flow.dao.FlowInstanceLogs
import io.hizhengfu.cloud.flow.dao.FlowInstances
import io.hizhengfu.cloud.sys.file.dao.Files
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.select

data class WarehouseApplicationContext(
    val application:WarehouseEntryApplicationVo?,
    val orgId:String,val orgName:String,val actorId:String,val actorName:String,val revision:String,
    val canSave:Boolean,val canSubmit:Boolean,val canCancel:Boolean,val canVoid:Boolean,
)

data class WarehouseAttendanceContext(
    val application:WarehouseEntryApplicationVo,
    val orgId:String,
    val actorId:String,
    val revision:String,
    val canCheckIn:Boolean,
    val canCheckOut:Boolean,
)

/** 使用数据库实际值生成窗口版本；时间转为完整字符串参与摘要，不截断历史微秒。 */
internal object WarehouseAttendanceConfirmation {
    private fun rows(table:Table,condition:Op<Boolean>):List<Map<String,String?>> =
        table.selectAll().where {condition}.map {row->
            table.columns.associate {column->column.name to canonicalExposedValue(row[column])}
        }.sortedBy {it["id"]}

    fun applicationRevision(entity:WarehouseEntryApplicationDao?,orgId:String,actorId:String):String {
        val org=rows(CollectionOrgs,CollectionOrgs.id eq orgId).map {row->
            // 同馆锁的协调计数不是业务资料，不使当前请求在取得锁后自行过期。
            row.filterKeys {it!=CollectionOrgs.locationMutationVersion.name}
        }
        val data=mapOf(
            "kind" to "WAREHOUSE_APPLICATION","actor" to actorId,"org" to org,
            "application" to entity?.let {revision(it,actorId)},
            "flow" to entity?.flowInstanceId?.let {rows(FlowInstances,FlowInstances.id eq it)},
            "newWarehouses" to if(entity==null)rows(Warehouses,Warehouses.orgId eq orgId) else null,
            // 新建在同馆锁内检查此版本，网络重发的旧确认不能再创建第二份申请。
            "existingApplicationIds" to if(entity==null)WarehouseEntryApplications.select(WarehouseEntryApplications.id)
                .where {WarehouseEntryApplications.orgId eq orgId}.map {it[WarehouseEntryApplications.id].value}.sorted() else null,
        )
        return CollectionCareEvidence.digest(JsonUtils.toJson(data)!!)
    }

    /** 操作可以新增归档，但不能趁写入更改已确认的馆、库房和原有文件/归档。 */
    fun resourceVerifier(entity:WarehouseEntryApplicationDao?,orgId:String,proposal:WarehouseEntryApplicationVo?=null):()->Unit {
        val archiveRows=entity?.let {rows(CollectionPermanentArchives,
            (CollectionPermanentArchives.bizType eq CollectionConst.BIZ_WAREHOUSE_ENTRY) and (CollectionPermanentArchives.bizId eq it.id.value))}.orEmpty()
        val archiveIds=archiveRows.mapNotNull {it["id"]}
        val referenceRows=entity?.let {rows(CollectionPermanentArchiveFiles,
            (CollectionPermanentArchiveFiles.bizId eq it.id.value) or (CollectionPermanentArchiveFiles.archiveId inList archiveIds))}.orEmpty()
        val referenceIds=referenceRows.mapNotNull {it["id"]}
        val warehouseIds=(splitCollectionIds(entity?.warehouseIds)+splitCollectionIds(proposal?.warehouseIds)).distinct()
        val fileIds=(referenceRows.mapNotNull {it["file_id"]}+splitCollectionIds(entity?.attachmentIds)+
            splitCollectionIds(entity?.dossier?.identityFileIds)+splitCollectionIds(entity?.dossier?.shortageFileIds)+
            splitCollectionIds(proposal?.attachmentIds)+splitCollectionIds(proposal?.dossier?.identityFileIds)+
            splitCollectionIds(proposal?.dossier?.shortageFileIds)).distinct()
        fun state()=listOf(
            rows(CollectionOrgs,CollectionOrgs.id eq orgId).map {it.filterKeys {key->key!=CollectionOrgs.locationMutationVersion.name}},
            rows(Warehouses,Warehouses.id inList warehouseIds),
            rows(Files,Files.id inList fileIds),
            rows(CollectionPermanentArchives,CollectionPermanentArchives.id inList archiveIds),
            rows(CollectionPermanentArchiveFiles,CollectionPermanentArchiveFiles.id inList referenceIds),
        )
        val before=state()
        return {if(state()!=before)throw io.hizhengfu.cloud.common.exception.BadRequestException("入库申请写入期间原馆、库房或原件资料已变化，本次操作已撤回，请重新核对")}
    }

    fun revision(entity:WarehouseEntryApplicationDao,actorId:String):String {
        val id=entity.id.value
        val archives=rows(CollectionPermanentArchives,
            (CollectionPermanentArchives.bizType eq CollectionConst.BIZ_WAREHOUSE_ENTRY) and (CollectionPermanentArchives.bizId eq id))
        val archiveIds=archives.mapNotNull {it["id"]}
        val references=rows(CollectionPermanentArchiveFiles,
            (CollectionPermanentArchiveFiles.bizId eq id) or (CollectionPermanentArchiveFiles.archiveId inList archiveIds))
        val fileIds=(references.mapNotNull {it["file_id"]} + splitCollectionIds(entity.attachmentIds) +
            splitCollectionIds(entity.dossier?.identityFileIds) + splitCollectionIds(entity.dossier?.shortageFileIds)).distinct()
        val data=mapOf(
            "actor" to actorId,
            "application" to rows(WarehouseEntryApplications,WarehouseEntryApplications.id eq id),
            "warehouses" to rows(Warehouses,Warehouses.id inList splitCollectionIds(entity.warehouseIds)),
            "approvals" to entity.flowInstanceId?.let {rows(FlowInstanceLogs,FlowInstanceLogs.instanceId eq it)}.orEmpty(),
            "archives" to archives,"references" to references,
            "files" to rows(Files,Files.id inList fileIds),
        )
        return CollectionCareEvidence.digest(JsonUtils.toJson(data)!!)
    }
}
