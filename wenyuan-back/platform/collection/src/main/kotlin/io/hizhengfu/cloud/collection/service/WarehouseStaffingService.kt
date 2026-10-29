package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.EnumContentStatus
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.IdGenerate
import io.hizhengfu.cloud.common.utils.SecurityUtils
import org.jetbrains.exposed.v1.dao.entityCache
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.and
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
@Transactional
class WarehouseStaffingService(
    private val scope: CollectionScopeResolver,
    private val users: CollectionResponsibleUserService,
    private val archives: CollectionPermanentArchiveService,
) {
    companion object { const val BIZ = "COLLECTION_WAREHOUSE_STAFFING" }
    private val readAuthorization = CollectionFileReadAuthorization(scope, CollectionActorDirectory(scope))
    fun get(id: String): WarehouseVo {
        val e=WarehouseDao.findById(id) ?: fail("库房不存在")
        readAuthorization.requireBusinessReadable(e.orgId,setOf("ADMIN",CollectionConst.ADMIN,CollectionConst.WAREHOUSE_MANAGE,CollectionConst.SELECT,CollectionConst.WAREHOUSE_ENTRY_APPLY),CollectionConst.CROSS_ORG,"库房岗位原件")
        return e.toModel().copy(archives=archives.readForDisplay(BIZ,id,e.orgId).archives)
    }
    fun requireArchivedOriginal(id:String,archiveId:String,fileId:String) {
        if(listOf(id,archiveId,fileId).any { !it.matches(Regex("[0-9]{15,20}")) })fail("库房任职归档原件标识不正确")
        val archive=get(id).archives.singleOrNull {
            it["id"]==archiveId && it["phase"].toString().startsWith("ASSIGNED_")
        } ?: fail("该任职永久档案不属于当前库房")
        @Suppress("UNCHECKED_CAST")
        val files=archive["files"] as? List<Map<String,Any?>> ?: fail("库房任职永久档案文件清单无效")
        if(files.none { it["fileId"]==fileId })fail("该文件不是所选任职永久档案的原件")
    }
    fun save(id: String, value: WarehouseStaffingVo): String {
        val initial=WarehouseDao.findById(id) ?: fail("库房不存在")
        val orgId=initial.orgId
        val actorId=CollectionCurrentOperationAccess.require(scope,orgId,true,setOf(CollectionConst.WAREHOUSE_MANAGE),"库房任职维护").id
        val e=CollectionLocationMutation.reference(orgId,id) ?: fail("库房不存在")
        CollectionCurrentOperationAccess.require(scope,orgId,true,setOf(CollectionConst.WAREHOUSE_MANAGE),"库房任职维护")
        requireStorage(e)
        // 更新不能抹掉尚未核实的任职历史；重复请求也须核验当前原件。
        WarehouseStaffingArchiveEvidence(archives).changes(e)
        if(e.staffing!=null) {
            val previous=CollectionPermanentArchiveDao.find {
                (CollectionPermanentArchives.bizType eq BIZ) and (CollectionPermanentArchives.bizId eq id)
            }.filter { it.phase.startsWith("ASSIGNED_") }.maxWithOrNull(compareBy({ it.createTime },{ it.id.value }))
                ?: fail("缺少当前任职原件，请先核实任职历史")
            archives.seal(e.orgId,BIZ,id,previous.phase,JsonUtils.mapper.readTree(previous.snapshot),e.staffing!!.signedAssignmentFileIds)
        }
        if(e.staffing==value)return id
        if(e.staffing!=null && value.replacementReason.isNullOrBlank())fail("库管名单更正须说明原因，原任职原件保留")
        if(value.keeperUserIds.isEmpty())fail("库房必须明确实际保管人员")
        if(value.keeperUserId==value.leaderUserId)fail("库管任职须分别明确保管部门负责人和主要负责人")
        requireMembers(orgId,value)
        if(value.assignedAt.isAfter(LocalDateTime.now()) || e.staffing?.assignedAt?.isAfter(value.assignedAt)==true)fail("请填写实际任职签署时间，不能用旧时间覆盖新任职")
        if(value.signedAssignmentFileIds.isBlank())fail("请上传实际岗位责任及任职签署原件")
        val originals=history(id).associate { it.id.value to archiveValues(it) }
        val phase="ASSIGNED_${IdGenerate.nextId()}"
        val archiveId=archives.seal(orgId,BIZ,id,phase,value,value.signedAssignmentFileIds)
        e.staffing=value
        // 任职列会延迟写入；必须执行全部SQL后直接读取表行，再核对任职与原件。
        TransactionManager.current().entityCache.flush()
        val current=WarehouseDao.find { Warehouses.id eq id }.singleOrNull() ?: fail("写入后库房不存在，请核实实际任职登记结果")
        current.refresh(flush=false)
        // 按持久化字段契约比较：保管人员是集合，JSON数组顺序不代表任职变化。
        val expected=JsonUtils.fromJson(JsonUtils.toJson(value),WarehouseStaffingVo::class.java)
        if(current.orgId!=orgId || current.staffing!=expected)fail("实际任职记录或所属馆与本次登记不一致")
        val written=history(id).onEach { it.refresh(flush=false) }
        if(written.map {it.id.value}.toSet()!=originals.keys+archiveId || written.any {a->originals[a.id.value]?.let {it!=archiveValues(a)}==true})fail("任职登记期间历史原件发生变化，请核对完整任职链")
        val archive=written.single {it.id.value==archiveId}
        archives.verifyExisting(archive,orgId,BIZ,id,phase,value,value.signedAssignmentFileIds)
        WarehouseStaffingArchiveEvidence(archives).changes(current)
        requireStorage(current)
        requireMembers(orgId,value)
        CollectionOrgDao.findById(orgId)?.refresh(flush=false)
        if(SecurityUtils.getNullUserDetail()?.id!=actorId)fail("任职登记期间办理账号变化，请重新核实")
        CollectionCurrentOperationAccess.require(scope,orgId,true,setOf(CollectionConst.WAREHOUSE_MANAGE),"库房任职维护")
        return id
    }
    private fun requireMembers(orgId:String,value:WarehouseStaffingVo) {
        users.requireMuseumMember(value.keeperUserId,orgId);users.requireMuseumMember(value.leaderUserId,orgId)
        users.requireRole(value.keeperUserId,orgId,CollectionConst.ROLE_STORAGE_HEAD)
        users.requireRole(value.leaderUserId,orgId,CollectionConst.ROLE_DIRECTOR)
        value.keeperUserIds.forEach { users.requireMuseumMember(it,orgId);users.requireRole(it,orgId,CollectionConst.ROLE_KEEPER) }
    }
    private fun history(id:String)=CollectionPermanentArchiveDao.find {
        (CollectionPermanentArchives.bizType eq BIZ) and (CollectionPermanentArchives.bizId eq id)
    }.filter {it.phase.startsWith("ASSIGNED_")}
    private fun archiveValues(a:CollectionPermanentArchiveDao)=listOf(a.orgId,a.phase,a.snapshot,a.digest)
    /** 写入位置引用或核验当前入库资格时，在整个业务事务内保持位置及任职基线稳定。 */
    fun baselineForWrite(orgId: String, warehouseIds: Collection<String>): WarehouseEntryBaselineVo {
        CollectionLocationMutation.acquire(orgId)
        warehouseIds.distinct().sorted().forEach { CollectionLocationMutation.reference(orgId, it) }
        return baseline(orgId, warehouseIds)
    }
    fun baseline(orgId: String, warehouseIds: Collection<String>): WarehouseEntryBaselineVo = WarehouseEntryBaselineVo(warehouseIds.map { id ->
        val e=WarehouseDao.findById(id) ?: fail("库房不存在")
        if(e.orgId!=orgId)fail("库房须属于本馆")
        requireStorage(e)
        val staff=e.staffing ?: fail("请先核实${e.name}的实际库管任职名单及签署原件")
        WarehouseStaffingItemVo(id,e.name,staff)
    })
    fun requireKeeper(userId: String, orgId: String, warehouseIds: Collection<String>) {
        baseline(orgId,warehouseIds).items.forEach { if(userId !in it.staffing.keeperUserIds)fail("所选人员不是${it.warehouseName}的实际库房管理人员") }
        users.requireMuseumMember(userId,orgId)
        users.requireRole(userId,orgId,CollectionConst.ROLE_KEEPER)
    }
    private fun requireStorage(e: WarehouseDao) {
        if(e.status!=EnumContentStatus.NORMAL)fail("库房已停用，不能办理新的任职或入库")
        if(e.sceneType!=EnumLocationScene.STORAGE || e.type !in setOf(EnumWarehouseType.WAREHOUSE,EnumWarehouseType.AREA))fail("岗位及人员入库只适用于保管库房或库区")
    }
    private fun fail(message: String): Nothing = throw BadRequestException(message)
}
