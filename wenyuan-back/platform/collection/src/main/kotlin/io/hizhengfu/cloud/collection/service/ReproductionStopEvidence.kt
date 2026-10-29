package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import java.time.LocalDateTime

internal object ReproductionStopEvidence {
    fun fail(message:String):Nothing=throw BadRequestException(message)
    fun phase(p:ReproductionPlanDao,key:String,value:Any,archives:CollectionPermanentArchiveService,verifyContent:Boolean=true) {
        val a=CollectionPermanentArchives
        val row=CollectionPermanentArchiveDao.find {(a.bizType eq ReproductionComplianceService.BIZ) and (a.bizId eq p.id.value) and (a.phase eq key)}.singleOrNull()
            ?: fail("复制拓印停止或处置原件缺失")
        val expected=JsonUtils.toJson(value)!!
        if(row.orgId!=p.orgId || row.snapshot!=expected || row.digest!=CollectionCareEvidence.digest(expected))fail("复制拓印停止或处置原件不一致")
        val files=ReproductionArchiveOriginalReferences.fileIds(key,row.snapshot) ?: fail("复制拓印停止阶段原件清单规则未配置")
        archives.verifyExisting(row,p.orgId,ReproductionComplianceService.BIZ,p.id.value,key,value,files,verifyContent=verifyContent)
    }
    fun stopped(p:ReproductionPlanDao,archives:CollectionPermanentArchiveService):ReproductionStopVo {
        val v=p.stopped ?: fail("尚未登记实际停止")
        if(v.planId!=p.id.value || p.started==null || p.completed!=null)fail("复制拓印停止阶段与方案不一致")
        phase(p,"PLAN",mapOf("reproductionId" to p.reproductionId,"version" to p.version,"sourceSnapshot" to p.sourceSnapshot,"qualification" to p.qualification,"content" to p.content),archives)
        phase(p,"STARTED",p.started!!,archives);phase(p,"STOPPED",v,archives)
        return v
    }
    fun checks(p:ReproductionPlanDao,archives:CollectionPermanentArchiveService,verifyContent:Boolean=true):List<ReproductionSafetyDao> = ReproductionSafetyDao.find {ReproductionSafetyRecords.planId eq p.id.value}.toList().onEach {
        if(it.orgId!=p.orgId)fail("复制拓印安全检查所属馆不一致")
        phase(p,"SAFETY_${it.id.value}",it.content,archives,verifyContent)
    }
    fun unresolved(p:ReproductionPlanDao,archives:CollectionPermanentArchiveService,verifyContent:Boolean=true):Boolean {
        val records=checks(p,archives,verifyContent);val resolved=records.filter {it.content.safe}.flatMap {it.content.resolvesRecordIds}.toSet()
        return records.any {!it.content.safe && it.id.value !in resolved}
    }
    fun unsafe(p:ReproductionPlanDao,archives:CollectionPermanentArchiveService,verifyContent:Boolean=true):Boolean = p.stopped?.let {
        // 即使停止事实已说明不安全，仍核对后续安全检查原件，不能因布尔短路跳过历史依据。
        val unresolved=unresolved(p,archives,verifyContent)
        !it.originalUndamaged || !it.physicallyPresent || !it.safeForCustody || unresolved
    } ?: false
    fun active(collectionId:String,archives:CollectionPermanentArchiveService):List<ReproductionPlanDao> {
        val ids=ReproductionDao.find {Reproductions.collectionId eq collectionId}.map {it.id.value}
        if(ids.isEmpty())return emptyList()
        val plans=ReproductionPlanDao.find {ReproductionPlans.reproductionId inList ids}.toList()
        if(plans.isEmpty())return emptyList()
        val a=CollectionPermanentArchives
        val stages=CollectionPermanentArchiveDao.find {
            (a.bizType eq ReproductionComplianceService.BIZ) and (a.bizId inList plans.map {it.id.value}) and
                (a.phase inList listOf("STARTED","COMPLETED","STOPPED"))
        }.groupBy({it.bizId},{it.phase})
        return plans.filter {p ->
            val frozen=stages[p.id.value].orEmpty().toSet()
            // 阶段状态只是业务投影。先核对已形成的永久依据，再决定是否还有原件占用，
            // 防止清空开始、伪造完成或改写使用原件标记时直接漏出安全检查。
            if(frozen.isEmpty() && p.started==null && p.completed==null && p.stopped==null)false
            else {
                phase(p,"PLAN",mapOf("reproductionId" to p.reproductionId,"version" to p.version,
                    "sourceSnapshot" to p.sourceSnapshot,"qualification" to p.qualification,"content" to p.content),archives)
                if(JsonUtils.mapper.readTree(p.sourceSnapshot).path("collectionId").asString()!=collectionId)
                    fail("复制拓印原方案与业务藏品身份不一致")
                fun lifecycle(key:String,value:Any?) {
                    if((value!=null)!=(key in frozen))fail("复制拓印阶段状态与永久原件不一致：$key")
                    if(value!=null)phase(p,key,value,archives)
                }
                lifecycle("STARTED",p.started);lifecycle("COMPLETED",p.completed);lifecycle("STOPPED",p.stopped)
                if((p.completed!=null || p.stopped!=null) && p.started==null || p.completed!=null && p.stopped!=null)
                    fail("复制拓印实际开始、完成与停止阶段不一致")
                if(p.started?.planId?.let {it!=p.id.value}==true || p.stopped?.planId?.let {it!=p.id.value}==true)
                    fail("复制拓印实际阶段与原方案身份不一致")
                p.started!=null && p.completed==null && p.content.usesOriginal
            }
        }
    }
}

/** 停止只释放生产占用；原件的异常保管仍须有真实事故处置依据。 */
@Component
class ReproductionOperationSafetyGuard(@param:Lazy private val archives:CollectionPermanentArchiveService,
    @param:Lazy private val incidents:CollectionIncidentService?=null):CollectionOperationSafetyGuard,IncidentResolutionGuard {
    override fun requireAllowed(collectionId:String,bizType:String?,bizId:String?) {
        if(bizType in setOf(CollectionConst.BIZ_RETURN,CollectionConst.BIZ_INVENTORY,CollectionConst.BIZ_CHANGE))return
        ReproductionStopEvidence.active(collectionId,archives).forEach {p ->
            if(p.stopped==null) {
                if(bizType!=ReproductionComplianceService.BIZ || bizId!=p.reproductionId)ReproductionStopEvidence.fail("本件仍在实际复制拓印，须先据实完成或停止")
            } else {
                ReproductionStopEvidence.stopped(p,archives)
                if(ReproductionStopEvidence.unsafe(p,archives)) {
                    val saved=p.clearance ?: ReproductionStopEvidence.fail("复制拓印停止后的原件安全异常尚未实际核对处置；可继续办理归还接收、清点和信息订正")
                    ReproductionStopEvidence.phase(p,"STOP_CLEARED_${saved.version}",saved,archives)
                    val proof=incidents?.requireStoredClearance(saved.record.incidentId,p.orgId,collectionId,IncidentSourceVo(ReproductionComplianceService.BIZ,p.reproductionId,p.id.value),saved.record.confirmedAt,saved.incidentProof,keeperUserId=saved.record.keeperUserId)
                    if(proof!=saved.incidentProof)ReproductionStopEvidence.fail("复制拓印停止后的事故处置依据已变化，请重新核实")
                }
            }
        }
    }
    override fun requireSafeResolution(collectionId:String) {
        ReproductionStopEvidence.active(collectionId,archives).forEach {
            if(it.stopped==null)ReproductionStopEvidence.fail("本件复制拓印尚未实际结束，不能确认事故安全处置")
            ReproductionStopEvidence.stopped(it,archives)
            ReproductionStopEvidence.checks(it,archives)
        }
    }
    override fun requireExclusiveWork(collectionId:String,bizType:String,bizId:String)=requireSafeResolution(collectionId)
    override fun requireHandlingEvidence(facts:IncidentFactsVo,completedAt:LocalDateTime) {
        val source=facts.source?.takeIf {it.bizType==ReproductionComplianceService.BIZ} ?: return
        val p=source.recordId?.let(ReproductionPlanDao::findById) ?: ReproductionStopEvidence.fail("缺少复制拓印停止方案")
        if(p.reproductionId!=source.bizId || completedAt.isBefore(ReproductionStopEvidence.stopped(p,archives).stoppedAt))ReproductionStopEvidence.fail("事故处置不能早于复制拓印实际停止")
    }
}

@Component
class ReproductionIncidentSourceProvider(@param:Lazy private val archives:CollectionPermanentArchiveService):CollectionIncidentSourceProvider {
    override val bizType=ReproductionComplianceService.BIZ
    override fun snapshot(orgId:String,source:IncidentSourceVo,items:List<IncidentItemVo>):Any {
        val r=ReproductionDao.findById(source.bizId) ?: ReproductionStopEvidence.fail("复制拓印来源不存在")
        val p=source.recordId?.let(ReproductionPlanDao::findById) ?: ReproductionStopEvidence.fail("须选择实际停止的复制拓印方案")
        if(r.orgId!=orgId || p.orgId!=orgId || p.reproductionId!=r.id.value || items.map {it.collectionId}.toSet()!=setOf(r.collectionId))ReproductionStopEvidence.fail("停止来源须对应本馆本批本件原件")
        val stopped=ReproductionStopEvidence.stopped(p,archives)
        if(!ReproductionStopEvidence.unsafe(p,archives))ReproductionStopEvidence.fail("本批尚无停止安全异常")
        if(!stopped.originalUndamaged && !items.single().damaged)ReproductionStopEvidence.fail("事故事实不得省略停止时已记录的原件损伤")
        val sourceData=JsonUtils.mapper.readTree(p.sourceSnapshot)
        if(sourceData.path("collectionId").asString()!=r.collectionId)ReproductionStopEvidence.fail("停止原方案与本件藏品关联不一致")
        return mapOf("collectionId" to r.collectionId,"catalog" to JsonUtils.fromJson(sourceData.path("catalog").toString(),CollectionCatalogContentVo::class.java),"stopped" to stopped,
            "safety" to ReproductionStopEvidence.checks(p,archives).filter {!it.content.safe}.map {mapOf("id" to it.id.value,"content" to it.content)})
    }
    override fun reportingCatalogs(evidence:String):Map<String,CollectionCatalogContentVo> {
        val node=JsonUtils.mapper.readTree(evidence)
        return mapOf(node.path("collectionId").asString() to JsonUtils.fromJson(node.path("catalog").toString(),CollectionCatalogContentVo::class.java)!!)
    }
    override fun stoppedCustody(orgId:String,collectionId:String,source:IncidentSourceVo):IncidentStoppedCustody {
        val p=source.recordId?.let(ReproductionPlanDao::findById) ?: ReproductionStopEvidence.fail("停止方案不存在")
        val stopped=ReproductionStopEvidence.stopped(p,archives)
        val value=snapshot(orgId,source,listOf(IncidentItemVo(collectionId,!stopped.originalUndamaged,EnumIncidentSecurity.NONE,false,"核对实际停止保管")))
        if(!stopped.physicallyPresent || stopped.custodianUserId.isNullOrBlank())ReproductionStopEvidence.fail("停止记录尚未核实实物在场及保管人员")
        return IncidentStoppedCustody(stopped.stoppedAt,JsonUtils.toJson(value)!!)
    }
}
