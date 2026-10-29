package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import java.security.MessageDigest

internal object CollectionCareEvidence {
    const val BIZ = CollectionConst.BIZ_CARE
    fun json(v: Any) = JsonUtils.toJson(v)!!
    fun digest(v: String) = MessageDigest.getInstance("SHA-256").digest(v.toByteArray()).joinToString("") { "%02x".format(it) }
    fun catalog(c: CollectionDao) = CollectionCatalogMapper.fromCollection(c.toModel()).copy(warehouseId=null)
    fun plans(id: String, orgId:String? = null) = CollectionCarePlanDao.find { (CollectionCarePlans.collectionId eq id) and (orgId?.let { CollectionCarePlans.orgId eq it } ?: org.jetbrains.exposed.v1.core.Op.TRUE) }.sortedBy { it.version }
    fun checks(id: String, orgId:String? = null) = CollectionCareCheckDao.find { (CollectionCareChecks.collectionId eq id) and (orgId?.let { CollectionCareChecks.orgId eq it } ?: org.jetbrains.exposed.v1.core.Op.TRUE) }.sortedWith(compareBy({it.content.checkedAt},{it.id.value}))
    fun works(id: String, orgId:String? = null) = CollectionCareWorkDao.find { (CollectionCareWorks.collectionId eq id) and (orgId?.let { CollectionCareWorks.orgId eq it } ?: org.jetbrains.exposed.v1.core.Op.TRUE) }.sortedBy { it.started.startedAt }
    fun planValue(p: CollectionCarePlanDao) = mapOf("collectionId" to p.collectionId,"version" to p.version,"catalog" to p.catalog,"content" to p.content)
    fun checkValue(c: CollectionCareCheckDao) = mapOf("collectionId" to c.collectionId,"catalog" to c.catalog,"content" to c.content)
    fun startValue(w: CollectionCareWorkDao) = mapOf("collectionId" to w.collectionId,"planId" to w.planId,"started" to w.started)
    fun exact(org: String,id: String,phase: String,v: Any) {
        val a=CollectionPermanentArchiveDao.find { (CollectionPermanentArchives.bizType eq BIZ) and (CollectionPermanentArchives.bizId eq id) and (CollectionPermanentArchives.phase eq phase) }.singleOrNull() ?: fail("缺少日常检查养护永久原件：$phase")
        if(a.orgId!=org || a.digest!=digest(a.snapshot) || JsonUtils.mapper.readTree(a.snapshot)!=JsonUtils.mapper.readTree(json(v)))fail("日常检查养护记录与永久原件不一致")
    }
    private fun optional(org:String,id:String,phase:String,value:Any?) {
        if(value!=null)exact(org,id,phase,value)
        else if(CollectionPermanentArchiveDao.find {(CollectionPermanentArchives.bizType eq BIZ) and (CollectionPermanentArchives.bizId eq id) and (CollectionPermanentArchives.phase eq phase)}.any())fail("日常检查养护阶段不能清除已归档的原件：$phase")
    }
    fun plan(p: CollectionCarePlanDao) {
        optional(p.orgId,p.id.value,"CONSERVATION",p.conservation)
        optional(p.orgId,p.id.value,"KEEPER",p.keeper)
        exact(p.orgId,p.id.value,"PLAN",planValue(p))
        p.conservation?.let { exact(p.orgId,p.id.value,"CONSERVATION",it); if(it.actorUserId!=p.content.conservationUserId)fail("保护修复确认人员与方案不一致") }
        p.keeper?.let { exact(p.orgId,p.id.value,"KEEPER",it); if(it.actorUserId!=p.content.keeperUserId || p.conservation==null || it.confirmedAt.isBefore(p.conservation!!.confirmedAt))fail("保管确认人员或确认顺序与方案不一致") }
    }
    /** 核对全部历史版本；不能以旧版本仍有原件为由回退当前处置依据。读取不重封档案。 */
    fun clearances(org:String,id:String,current:CollectionCareClearanceSnapshot?):List<CollectionCareClearanceSnapshot> {
        val rows=CollectionPermanentArchiveDao.find {
            (CollectionPermanentArchives.bizType eq BIZ) and (CollectionPermanentArchives.bizId eq id)
        }.filter {it.phase.startsWith("CLEARED_")}.map {a ->
            if(a.orgId!=org || a.digest!=digest(a.snapshot))fail("日常检查养护处置历史所属馆或摘要不一致")
            val v=try {JsonUtils.fromJson(a.snapshot,CollectionCareClearanceSnapshot::class.java)}
                catch (_:tools.jackson.core.JacksonException) {fail("日常检查养护处置历史无法解析")}
                ?: fail("日常检查养护处置历史缺失")
            if(v.version<1 || a.phase!="CLEARED_${v.version}")fail("日常检查养护处置历史版本与归档阶段不一致")
            v
        }.sortedBy {it.version}
        if(rows.withIndex().any {(index,v)->v.version!=index+1} || rows.lastOrNull()!=current)
            fail("日常检查养护处置历史缺失或当前核对版本已回退")
        if(rows.zipWithNext().any {(old,next)->next.record.confirmedAt.isBefore(old.record.confirmedAt)})
            fail("日常检查养护处置历史时间顺序不一致")
        return rows
    }
    fun check(c: CollectionCareCheckDao) { exact(c.orgId,c.id.value,"CHECKED",checkValue(c)); clearances(c.orgId,c.id.value,c.clearance) }
    fun work(w: CollectionCareWorkDao) {
        val p=CollectionCarePlanDao.findById(w.planId) ?: fail("养护方案不存在")
        if(p.orgId!=w.orgId || p.collectionId!=w.collectionId)fail("养护方案归属不一致")
        plan(p); exact(w.orgId,w.id.value,"STARTED",startValue(w))
        val c=CollectionCareCheckDao.findById(w.started.checkId) ?: fail("实际养护所依据的检查不存在")
        if(c.orgId!=w.orgId || c.collectionId!=w.collectionId || c.content.planId!=p.id.value)fail("实际养护的检查依据归属不一致")
        check(c)
        optional(w.orgId,w.id.value,"ENDED",w.ended)
        clearances(w.orgId,w.id.value,w.clearance)
    }
    fun unsafe(c: CollectionCareCheckDao) = !c.content.safeForRoutineCare || c.content.damaged || !c.content.physicallyPresent
    fun unsafe(w: CollectionCareWorkDao) = w.ended?.let { !it.safeToRelease || it.unexpectedDamage || !it.physicallyPresent } ?: true
    fun fail(message: String): Nothing = throw BadRequestException(message)
}
