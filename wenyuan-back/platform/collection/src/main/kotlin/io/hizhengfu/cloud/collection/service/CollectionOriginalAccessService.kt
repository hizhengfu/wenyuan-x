package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.LocalDateTime

/** 日常养护、复制拓印、数字采集和有损取样共用实际原件取用依据；不代办提用、点交或人员入库。 */
@Component
@Transactional
class CollectionOriginalAccessService {
    fun requireAccess(r: ReproductionDao,at: LocalDateTime,operatorUserId: String,requisitionId: String?,onSiteWarehouseLogId: String?) {
        requireAccess(r.orgId,r.collectionId,CollectionConst.BIZ_REPRODUCTION,r.id.value,at,operatorUserId,requisitionId,onSiteWarehouseLogId)
    }
    fun requireAccess(orgId:String,collectionId:String,sourceBizType:String,sourceBizId:String,at:LocalDateTime,operatorUserId:String,requisitionId:String?,onSiteWarehouseLogId:String?) {
        val c=CollectionDao.findById(collectionId) ?: fail("藏品不存在")
        if(c.orgId!=orgId)fail("藏品所属馆已变化")
        if((requisitionId==null)==(onSiteWarehouseLogId==null))fail("使用原件须核实实际提用点交或实际在库办理记录，二选一")
        // 每次复核直接查询依据行；封存耗时期间的独立提交不能被事务内 DAO 缓存遮蔽。
        if(requisitionId!=null) {
            val req=Requisitions.selectAll().where { Requisitions.id eq requisitionId }.singleOrNull() ?: fail("实际提用不存在")
            if(req[Requisitions.orgId]!=orgId || collectionId !in req[Requisitions.collectionIds].split(',').map(String::trim) || !req[Requisitions.outExecuted] || req[Requisitions.returned] || req[Requisitions.handover]==null || req[Requisitions.handover]!!.content.handedAt.isAfter(at) || c.lifeStatus!=EnumCollectionStatus.IN_USE)fail("须先完成本馆本件实际点交出库")
            if(req[Requisitions.sourceBizType]!=sourceBizType || req[Requisitions.sourceBizId]!=sourceBizId)fail("实际提用须明确关联本次原件操作业务")
            val a=CollectionPermanentArchiveDao.find { (CollectionPermanentArchives.bizType eq CollectionConst.BIZ_REQUISITION) and (CollectionPermanentArchives.bizId eq req[Requisitions.id].value) and (CollectionPermanentArchives.phase eq "OUT_EXECUTED") }.singleOrNull() ?: fail("缺少实际提用出库档案")
            val frozen=JsonUtils.mapper.readTree(a.snapshot).get("request")
            if(a.orgId!=orgId || a.digest!=digest(a.snapshot) || !handoverMatches(frozen?.get("handover"),req[Requisitions.handover]!!) || frozen?.get("sourceBizId")?.asText()!=sourceBizId || frozen?.get("sourceBizType")?.asText()!=sourceBizType)fail("出库档案与本次实际提用点交不一致")
        } else {
            val log=WarehouseLogs.selectAll().where { WarehouseLogs.id eq onSiteWarehouseLogId!! }.singleOrNull() ?: fail("实际在库办理日记不存在")
            val location=c.warehouseId?.let { WarehouseDao.findById(it) } ?: fail("藏品实际库位不明确")
            val ids=(location.parentIds.orEmpty().split(',')+location.id.value).toSet()
            if(c.lifeStatus!=EnumCollectionStatus.IN_STORE || log[WarehouseLogs.orgId]!=orgId || log[WarehouseLogs.warehouseId] !in ids || log[WarehouseLogs.logDate]!=at.toLocalDate() || log[WarehouseLogs.diary]==null || log[WarehouseLogs.diary]!!.entryRecordsComplete!=true || log[WarehouseLogs.diary]!!.signedDiaryFileIds.isBlank())fail("须关联本件实际所在库房当日已核实人员进出及签署的日记")
            val diary=log[WarehouseLogs.diary]!!
            if(diary.hasAnomaly==null || (diary.hasAnomaly==true && diary.resolved!=true))fail("库房日记仍有未核实或未处理的异常")
            val expected=json(mapOf("warehouseId" to log[WarehouseLogs.warehouseId],"logDate" to log[WarehouseLogs.logDate],"workContent" to log[WarehouseLogs.content],"diary" to diary,"entries" to log[WarehouseLogs.entries]))
            val diaryArchives=CollectionPermanentArchiveDao.find { (CollectionPermanentArchives.bizType eq WarehouseDiaryService.BIZ) and (CollectionPermanentArchives.bizId eq log[WarehouseLogs.id].value) }.toList()
            if(diaryArchives.none { it.orgId==orgId && it.digest==digest(it.snapshot) && diaryMatches(it.snapshot,expected) })fail("库房日记缺少一致的永久原件")
            val attendance=log[WarehouseLogs.entries]?.items.orEmpty().firstOrNull { it.entry!=null && !it.enteredAt.isAfter(at) && it.exitedAt?.isBefore(at)!=true && operatorUserId in (it.entry!!.entrantUserIds+it.entry!!.escortUserIds) }
            if(attendance==null)fail("实际经办人员缺少本次在库工作时段的人员进出原件")
            val entry=attendance.entry!!
            // 日记保留签署时快照；后来登记的离库事实不能被旧快照遮掩。
            // 直接锁读实际进出行，避免沿用 DAO 缓存；与离库 UPDATE 在同一事务边界协调。
            val visits=WarehouseEntryApplications
            val visit=visits.selectAll().where { visits.id eq attendance.applicationId }.forUpdate().singleOrNull()
                ?: fail("日记对应的实际人员进出记录缺失，请核实原件")
            fun millis(value:LocalDateTime)=value.truncatedTo(java.time.temporal.ChronoUnit.MILLIS)
            if(visit[visits.orgId]!=orgId || log[WarehouseLogs.warehouseId] !in splitCollectionIds(visit[visits.warehouseIds]))fail("日记人员进出记录的所属馆或库房不一致")
            if(visit[visits.actualEntryAt]?.let(::millis)!=millis(attendance.enteredAt) || visit[visits.actualEntryAt]?.let(::millis)!=millis(entry.occurredAt))fail("日记人员进出快照与实际入库时间不一致")
            val actualEntry=visit[visits.entryRecord] ?: fail("实际入库签署记录缺失")
            if(actualEntry.copy(occurredAt=millis(actualEntry.occurredAt))!=entry.copy(occurredAt=millis(entry.occurredAt)))fail("日记人员进出快照与实际入库签署不一致")
            val exitAt=visit[visits.actualExitAt]?.let(::millis)
            val exit=visit[visits.exitRecord]
            if((exitAt==null)!=(exit==null) || (exit!=null && millis(exit.occurredAt)!=exitAt))fail("实际离库时间与签署记录不一致，请核实原件")
            if(exitAt!=null && !millis(at).isBefore(exitAt))fail("实际经办人员在该操作时间已离库，不能沿用旧日记办理原件操作")
            if(exit!=null) {
                val exitArchive=CollectionPermanentArchiveDao.find { (CollectionPermanentArchives.bizType eq CollectionConst.BIZ_WAREHOUSE_ENTRY) and (CollectionPermanentArchives.bizId eq visit[visits.id].value) and (CollectionPermanentArchives.phase eq "CHECKED_OUT") }.singleOrNull()
                if(exit.continuousAccompaniment!=true || exit.identityChecked!=true || exitAt!!.isBefore(millis(entry.occurredAt)) || exitArchive==null || exitArchive.orgId!=orgId || exitArchive.digest!=digest(exitArchive.snapshot) || !attendanceMatches(exitArchive.snapshot,exit))
                    fail("缺少一致的实际离库及全程陪同签署原件")
            }
            val entryArchive=CollectionPermanentArchiveDao.find { (CollectionPermanentArchives.bizType eq CollectionConst.BIZ_WAREHOUSE_ENTRY) and (CollectionPermanentArchives.bizId eq attendance.applicationId) and (CollectionPermanentArchives.phase eq "CHECKED_IN") }.singleOrNull()
            if(entry.identityChecked!=true || entry.continuousAccompaniment!=true || entryArchive==null || entryArchive.orgId!=orgId || entryArchive.digest!=digest(entryArchive.snapshot) || !attendanceMatches(entryArchive.snapshot,entry))fail("缺少实际共同入库和身份核验签署原件")
        }
        if(c.inventoryHold)fail("藏品仍有未处理清点异常，不得开展原件操作")
    }
    /** 人员名单是集合，数据库往返后的顺序不应使同一签署失效；其他原件字段仍逐项保留比较。 */
    private fun attendanceMatches(snapshot:String,value:WarehouseAttendanceVo):Boolean {
        val original=readSnapshot(snapshot);val current=readSnapshot(json(value))
        listOf(original,current).forEach(::attendancePeople)
        return original==current
    }
    private fun diaryMatches(snapshot:String,expected:String):Boolean {
        val original=readSnapshot(snapshot);val current=readSnapshot(expected)
        listOf(original,current).forEach {node->
            sortPeople(node.get("diary"),listOf("inspectorUserIds"))
            node.path("entries").path("items").forEach {visit->
                sortPeople(visit,listOf("legacyEntrantIds"))
                attendancePeople(visit.get("entry"));attendancePeople(visit.get("exit"))
            }
        }
        return original==current
    }
    private fun handoverMatches(original:tools.jackson.databind.JsonNode?,value:RequisitionHandoverRecordVo):Boolean {
        val current=readSnapshot(json(value))
        listOf(original,current).forEach {node->sortPeople(node?.get("content"),listOf("custodianUserIds","receivingUserIds"))}
        return original==current
    }
    private fun attendancePeople(node:tools.jackson.databind.JsonNode?)=sortPeople(node,listOf("entrantUserIds","escortUserIds","externalNames"))
    private fun sortPeople(node:tools.jackson.databind.JsonNode?,keys:List<String>) {
        if(node is tools.jackson.databind.node.ObjectNode)keys.forEach {key->
            val people=node.get(key)
            if(people?.isArray==true)node.set(key,JsonUtils.mapper.valueToTree<tools.jackson.databind.JsonNode>(people.toList().sortedBy {it.toString()}))
        }
    }
    private fun readSnapshot(text:String):tools.jackson.databind.JsonNode=try {JsonUtils.mapper.readTree(text)}
        catch (_:tools.jackson.core.JacksonException) {fail("原件使用依据无法解析")}
    private fun fail(message:String):Nothing=throw BadRequestException(message)
    private fun json(v:Any)=JsonUtils.toJson(v)!!
    private fun digest(v:String)=MessageDigest.getInstance("SHA-256").digest(v.toByteArray()).joinToString(""){"%02x".format(it)}
}
