package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.EnumContentStatus
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SecurityUtils
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.LocalDateTime

@Component
@Transactional
class IncidentRelocationService(
    private val scope: CollectionScopeResolver,
    private val users: CollectionResponsibleUserService,
    private val incidents: CollectionIncidentService,
    private val work: IncidentWorkService,
    private val archives: CollectionPermanentArchiveService,
    private val guards: List<IncidentResolutionGuard> = emptyList(),
) {
    companion object { const val BIZ = CollectionConst.BIZ_INCIDENT_RELOCATION }
    private fun access(orgId:String,write:Boolean=false) {
        CollectionIncidentReadAccess.require(scope,write).requireOrg(orgId)
    }
    private fun collection(id:String,write:Boolean):CollectionDao = CollectionDao.find {Collections.id eq id}.let {if(write)it.forUpdate()else it}.singleOrNull()?.also {access(it.orgId,write)} ?: fail("藏品不存在")
    private fun root(id:String,write:Boolean=false):IncidentRelocationDao {
        val meta=IncidentRelocations.selectAll().where {IncidentRelocations.id eq id}.singleOrNull() ?: fail("应急搬移不存在")
        access(meta[IncidentRelocations.orgId],write)
        if(write)collection(meta[IncidentRelocations.collectionId],true)
        return IncidentRelocationDao.find {IncidentRelocations.id eq id}.let {if(write)it.forUpdate()else it}.single()
    }
    private fun rows(r:IncidentRelocationDao)=IncidentRelocationRecordDao.find {IncidentRelocationRecords.relocationId eq r.id.value}.sortedBy {it.sequence}.also { rows ->
        if(rows.any {it.orgId!=r.orgId})fail("应急搬移记录所属馆与业务不一致")
    }
    private inline fun <reified T> read(row:IncidentRelocationRecordDao):T=JsonUtils.fromJson(row.content,T::class.java)!!
    private inline fun <reified T> record(r:IncidentRelocationDao,kind:EnumIncidentRelocationKind):T?=rows(r).lastOrNull {it.kind==kind}?.let {read<T>(it)}
    private fun plan(r:IncidentRelocationDao)=record<IncidentRelocationPlanSnapshot>(r,EnumIncidentRelocationKind.PLAN) ?: fail("实际紧急搬移补记没有事前方案，不能作新搬移授权")
    private fun emergency(r:IncidentRelocationDao)=record<IncidentEmergencyRelocationSnapshot>(r,EnumIncidentRelocationKind.EMERGENCY)!!
    private fun json(value:Any)=JsonUtils.toJson(value)!!
    private fun digest(value:String)=MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString(""){"%02x".format(it)}
    private fun verify(r:IncidentRelocationDao,verifyContent:Boolean=true) {
        rows(r)
        CollectionOperationSafetyOriginals(archives,verifyContent).relocation(r)
    }
    private fun append(r:IncidentRelocationDao,kind:EnumIncidentRelocationKind,value:Any,files:String):String {
        text(files)
        val content=json(value)
        rows(r).firstOrNull {it.kind==kind && it.content==content}?.let {verify(r);return it.id.value}
        r.sequence+=1
        val row=IncidentRelocationRecordDao.new {orgId=r.orgId;relocationId=r.id.value;sequence=r.sequence;this.kind=kind;this.content=content}
        archives.seal(r.orgId,BIZ,r.id.value,"${kind}_${row.id.value}",value,files)
        return row.id.value
    }
    private fun location(orgId:String,id:String,active:Boolean):List<WarehouseDao> {
        // 实际接收和紧急补记可引用方案之外的地点；先与位置维护协调并刷新整条父级关系。
        val location=CollectionLocationMutation.reference(orgId,id,allowRetired=!active) ?: fail("请明确实际位置")
        if(location.id.value!=id)fail("实际位置编号不一致，请重新选择地点")
        val result=mutableListOf<WarehouseDao>();var current:String?=id
        while(!current.isNullOrBlank() && current!="0") {
            if(result.any {it.id.value==current})fail("位置树存在循环，请核实实际地点")
            val node=WarehouseDao.findById(current) ?: fail("实际位置不存在")
            if(node.orgId!=orgId || (active && node.status!=EnumContentStatus.NORMAL))fail("位置须属于本馆且实际可用")
            result+=node;current=node.parentId
        }
        if(result.isEmpty())fail("请明确实际位置")
        return result
    }
    private fun locationSnapshot(orgId:String,id:String)=json(location(orgId,id,true).map {it.toModel()})
    private fun keeper(orgId:String,locationId:String,userId:String) {
        users.requireRole(userId,orgId,CollectionConst.ROLE_KEEPER)
        val path=location(orgId,locationId,true)
        if(path.first().sceneType==EnumLocationScene.STORAGE) {
            val assigned=path.firstOrNull {it.staffing!=null}?.staffing ?: fail("保管位置须先核实实际库管任职原件")
            if(userId !in assigned.keeperUserIds)fail("实际保管人不在该库房任职名单中")
        }
    }
    private fun people(orgId:String,p:IncidentRelocationPlanVo) {
        users.requireDepartmentMember(p.conservationUserId,orgId,p.departmentId)
        users.requireRole(p.conservationUserId,orgId,CollectionConst.ROLE_CONSERVATION)
        users.requireDepartmentHead(p.departmentHeadUserId,orgId,p.departmentId)
        users.requireRole(p.keeperUserId,orgId,CollectionConst.ROLE_STORAGE_HEAD)
        users.requireRole(p.leaderUserId,orgId,CollectionConst.ROLE_DIRECTOR)
        if(setOf(p.conservationUserId,p.departmentHeadUserId,p.keeperUserId,p.leaderUserId).size!=4)fail("方案四个确认职责须分别由本人承担")
        keeper(orgId,p.fromLocationId,p.fromCustodianUserId);keeper(orgId,p.toLocationId,p.toCustodianUserId)
        users.requireMuseumMember(p.securityUserId,orgId);users.requireMuseumMember(p.transportSupervisorUserId,orgId)
    }
    private fun exclusive(r:IncidentRelocationDao) {
        guards.forEach {it.requireExclusiveWork(r.collectionId,BIZ,r.id.value)}
        IncidentRelocationGuard(archives).requireExclusiveWork(r.collectionId,BIZ,r.id.value)
    }
    private fun current(r:IncidentRelocationDao):IncidentRelocationPlanSnapshot {
        verify(r);val p=plan(r);val c=collection(r.collectionId,true)
        if(incidents.requireWorkBasis(r.orgId,r.collectionId,p.plan.incidentIds)!=p.caseFacts || work.originalContext(c,p.plan.incidentIds)!=p.originalContext || locationSnapshot(r.orgId,p.plan.fromLocationId)!=p.fromLocation || locationSnapshot(r.orgId,p.plan.toLocationId)!=p.toLocation)fail("事故、原业务保管依据或地点人员条件已变化，须重新核实方案；已经发生的搬移应如实补记")
        people(r.orgId,p.plan);return p
    }
    fun create(v:IncidentRelocationPlanVo):String {
        val c=collection(v.collectionId,true)
        past(v.preparedAt);past(v.destinationCheckedAt)
        if(v.plannedDeparture.isBefore(v.preparedAt)||!v.plannedArrival.isAfter(v.plannedDeparture)||v.destinationCheckedAt.isAfter(v.preparedAt)||!v.destinationSafe||v.fromLocationId==v.toLocationId)fail("请核实具体搬移时段及已检查安全的不同目的地")
        text(v.necessity,v.conditionBefore,v.handlingMethod,v.packingMethod,v.route,v.safetyRequirements,v.stopConditions,v.carrierName,v.sourceLocationFileIds,v.destinationSafetyFileIds,v.carrierCapabilityFileIds,v.signedPlanFileIds)
        if(v.transportStandardReference.isNullOrBlank() || v.transportStandardApplicability.isNullOrBlank())
            fail("事前搬移方案须明确适用包装运输标准及现行版本、逐件适用依据")
        people(c.orgId,v)
        val facts=incidents.requireWorkBasis(c.orgId,v.collectionId,v.incidentIds)
        if(facts.values.any {JsonUtils.fromJson(CollectionIncidentRecordDao[it].content,IncidentFactsSnapshot::class.java)!!.facts.discoveredAt.isAfter(v.preparedAt)})fail("方案不能早于所依据事故的实际发现")
        val original=work.originalContext(c,v.incidentIds)
        val physical=IncidentRelocationLocationResolver().latest(c.orgId,setOf(c.id.value),LocalDateTime.now())
        if(physical.containsKey(c.id.value)&&physical[c.id.value]!=v.fromLocationId)fail("实际搬移位置已变化或仍在途，不能沿用原地点安排新的搬移")
        if(c.lifeStatus !in setOf(EnumCollectionStatus.IN_USE,EnumCollectionStatus.RESTORING) && c.warehouseId!=v.fromLocationId)fail("实际在库或归还待核查位置与来源不一致")
        val snapshot=IncidentRelocationPlanSnapshot(v,facts,original,locationSnapshot(c.orgId,v.fromLocationId),locationSnapshot(c.orgId,v.toLocationId))
        val r=IncidentRelocationDao.new {orgId=c.orgId;collectionId=c.id.value;mode=EnumIncidentRelocationMode.PLANNED}
        append(r,EnumIncidentRelocationKind.PLAN,snapshot,listOf(v.sourceLocationFileIds,v.destinationSafetyFileIds,v.carrierCapabilityFileIds,v.signedPlanFileIds).joinToString(","))
        return r.id.value
    }
    private fun basis(r:IncidentRelocationDao,stage:EnumIncidentWorkStage)=digest(json(rows(r).filter {it.kind!=EnumIncidentRelocationKind.CONFIRMATION && (stage==EnumIncidentWorkStage.END||it.kind==EnumIncidentRelocationKind.PLAN)}.map {it.content}))
    fun confirm(id:String,v:IncidentWorkConfirmationVo):String {
        val r=root(id,true);verify(r)
        val p=if(r.mode==EnumIncidentRelocationMode.PLANNED)plan(r).plan else null
        val event=if(p==null)emergency(r).event else null
        val roles=if(v.stage==EnumIncidentWorkStage.PLAN) {
            if(p==null)fail("补记事实没有事前方案，不得补造事前批准")
            linkedMapOf("CONSERVATION" to p.conservationUserId,"DEPARTMENT" to p.departmentHeadUserId,"KEEPER" to p.keeperUserId,"DIRECTOR" to p.leaderUserId)
        }else linkedMapOf("CONSERVATION" to (p?.conservationUserId?:event!!.conservationUserId),"KEEPER" to (p?.keeperUserId?:event!!.keeperUserId))
        val actor=SecurityUtils.getUserDetail().id;val role=roles.entries.singleOrNull {it.value==actor}?.key ?: fail("请由指定责任人员本人核对，管理员不能代签")
        val previous=rows(r).filter {it.kind==EnumIncidentRelocationKind.CONFIRMATION}.map {read<IncidentWorkConfirmationSnapshot>(it)}
        if(previous.any {it.actorUserId==actor&&it.content==v})return id
        past(v.signedAt);text(v.opinion,v.fileIds)
        if(v.basisRevision!=basis(r,v.stage))fail("完整原件已变化，请核对当前版本")
        val earliest=if(v.stage==EnumIncidentWorkStage.PLAN) {
            if(r.state!=EnumIncidentRelocationState.PLAN)fail("方案已批准或实际执行，不能覆盖")
            current(r);exclusive(r);p!!.preparedAt
        }else {
            if(r.state!=EnumIncidentRelocationState.RECEIVED)fail("须先如实登记实际接收及现状，再共同核对")
            users.requireRole(roles.getValue("CONSERVATION"),r.orgId,CollectionConst.ROLE_CONSERVATION)
            users.requireRole(roles.getValue("KEEPER"),r.orgId,CollectionConst.ROLE_STORAGE_HEAD)
            record<IncidentRelocationReceiptSnapshot>(r,EnumIncidentRelocationKind.RECEIPT)!!.receipt.receivedAt
        }
        if(v.signedAt.isBefore(earliest))fail("确认时间不得早于对应实际事项")
        roles.keys.takeWhile {it!=role}.forEach {key ->
            val signed=previous.lastOrNull {it.role==key&&it.actorUserId==roles[key]&&it.content.stage==v.stage&&it.content.basisRevision==v.basisRevision} ?: fail("前序责任人员须先核对同版完整原件")
            if(signed.content.signedAt.isAfter(v.signedAt))fail("本人确认时间与实际顺序不一致")
        }
        append(r,EnumIncidentRelocationKind.CONFIRMATION,IncidentWorkConfirmationSnapshot(v,actor,role),v.fileIds)
        if(role==roles.keys.last())r.state=if(v.stage==EnumIncidentWorkStage.PLAN)EnumIncidentRelocationState.APPROVED else EnumIncidentRelocationState.COMPLETED
        return id
    }
    private fun departureFacts(orgId:String,v:IncidentRelocationDepartureVo) {
        past(v.departedAt);location(orgId,v.fromLocationId,false)
        if(!v.physicallyPresent)fail("未确认实物在场不能登记已点交搬出；未找回等情况应继续据实登记事故事实")
        text(v.condition,v.actualPackaging,v.accessRecordFileIds,v.signedFileIds)
    }
    private fun usesCurrentWarehouse(c:CollectionDao)=c.lifeStatus !in setOf(EnumCollectionStatus.IN_USE,EnumCollectionStatus.RESTORING,EnumCollectionStatus.ON_LOAN)
    private fun applyDepartureLocation(r:IncidentRelocationDao,c:CollectionDao,at:LocalDateTime,files:String) {
        if(usesCurrentWarehouse(c))CollectionLedgerFieldChangeRecorder.warehouse(c,null,"事故搬移实际搬出库位",r.id.value)
        val active=CollectionPlacementDao.find {(CollectionPlacements.orgId eq r.orgId) and (CollectionPlacements.collectionId eq r.collectionId) and (CollectionPlacements.approved eq true)}.filter {it.validFrom?.isAfter(at)==false && (it.validTo==null||it.validTo!!.isAfter(at))}
        if(active.isNotEmpty()) {
            archives.seal(r.orgId,BIZ,r.id.value,"LOCATIONS_LEFT",mapOf("leftAt" to at,"placements" to active.map {mapOf("id" to it.id.value,"sourceBizType" to it.sourceBizType,"sourceBizId" to it.sourceBizId,"location" to it.toExhibitionModel())}),files)
            active.forEach {it.validTo=at}
        }
    }
    private fun newestMovementAt(collectionId:String)=StorageMovementDao.find {StorageMovements.collectionId eq collectionId}.maxOfOrNull {it.operatedAt}
    private fun canUpdate(c:CollectionDao,at:LocalDateTime)=c.lifeStatus!=EnumCollectionStatus.DEREGISTERED && newestMovementAt(c.id.value)?.isAfter(at)!=true
    private fun movement(r:IncidentRelocationDao,type:EnumMoveType,at:LocalDateTime,from:String?,to:String?,people:Set<String>,custodian:String?,files:String) {
        val collection=CollectionDao[r.collectionId]
        val movement=StorageMovementDao.new {
            orgId=r.orgId;collectionId=r.collectionId;documentNo="EM-${r.id.value}-${type.name}";this.type=type;reason=EnumMoveReason.EMERGENCY
            totalRegNoAtMovement=collection.totalRegNo;collectionNameAtMovement=collection.name
            basis=if(r.mode==EnumIncidentRelocationMode.PLANNED)"事故专项搬移原件 ${r.id.value}"else "实际紧急搬移补记 ${r.id.value}；不代表事前批准"
            fromWarehouseId=from;toWarehouseId=to;operator=people.firstOrNull();reviewer=people.drop(1).firstOrNull();this.custodian=custodian?.takeIf(String::isNotBlank);attachmentIds=files;operatedAt=at;refBizId=r.id.value
        }
        CollectionMovementFacts.capture(movement,archives)
    }
    fun depart(id:String,v:IncidentRelocationDepartureVo):String {
        val r=root(id,true);verify(r)
        record<IncidentRelocationDepartureVo>(r,EnumIncidentRelocationKind.DEPARTURE)?.let {if(it==v)return id;fail("实际搬出原件不能覆盖")}
        if(r.state!=EnumIncidentRelocationState.APPROVED)fail("新搬移须先完成方案本人确认；紧急已发生事项使用真实补记入口")
        val prepared=current(r);val p=prepared.plan;exclusive(r);departureFacts(r.orgId,v)
        val stopped=JsonUtils.mapper.readTree(prepared.originalContext).get("stoppedAt")
        if(stopped!=null&&!stopped.isNull&&v.departedAt.isBefore(JsonUtils.mapper.treeToValue(stopped,LocalDateTime::class.java)))fail("实际搬出不得早于原作业实际停止或接收保管")
        val approved=rows(r).filter {it.kind==EnumIncidentRelocationKind.CONFIRMATION}.map {read<IncidentWorkConfirmationSnapshot>(it)}.last {it.role=="DIRECTOR"&&it.content.stage==EnumIncidentWorkStage.PLAN}.content.signedAt
        if(v.departedAt.isBefore(approved)||v.departedAt.isBefore(p.plannedDeparture)||v.departedAt.isAfter(p.plannedArrival)||v.fromLocationId!=p.fromLocationId||!v.routeSafe||v.transportSupervisorUserId!=p.transportSupervisorUserId||v.releasingUserIds.size<2||p.fromCustodianUserId !in v.releasingUserIds)fail("须核实批准时段、原地点、实际保管及复核人员、运输监督和安全条件")
        if(v.appliedTransportStandardReference.isNullOrBlank() ||
            v.appliedTransportStandardReference.trim()!=p.transportStandardReference?.trim() ||
            v.packingStandardConfirmed!=true || v.packingCheckFileIds.isNullOrBlank())
            fail("计划搬移须核对实际采用标准与批准方案一致、逐件包装符合标准并留存签署核验原件")
        v.releasingUserIds.forEach {users.requireMuseumMember(it,r.orgId)}
        val c=collection(r.collectionId,true)
        if(!canUpdate(c,v.departedAt))fail("已有更晚的实物移动记录，不能以旧搬出覆盖当前地点；请据实补记历史")
        val departureFiles=listOf(v.accessRecordFileIds,v.signedFileIds,v.packingCheckFileIds).joinToString(",")
        append(r,EnumIncidentRelocationKind.DEPARTURE,v,departureFiles)
        movement(r,EnumMoveType.OUT,v.departedAt,v.fromLocationId,null,v.releasingUserIds,p.fromCustodianUserId,departureFiles)
        applyDepartureLocation(r,c,v.departedAt,v.signedFileIds);r.state=EnumIncidentRelocationState.DEPARTED
        return id
    }
    fun observation(id:String,v:IncidentWorkObservationVo):String {
        val r=root(id,true);verify(r)
        if(r.state!=EnumIncidentRelocationState.DEPARTED)fail("搬出后尚未接收时可记录实际运输观察；接收后新问题请追加事故事实")
        val departed=record<IncidentRelocationDepartureVo>(r,EnumIncidentRelocationKind.DEPARTURE)!!.departedAt
        past(v.observedAt);text(v.condition,v.actualMeasures,v.fileIds)
        if(v.observedAt.isBefore(departed))fail("运输观察不能早于实际搬出")
        users.requireMuseumMember(v.observerUserId,r.orgId)
        return append(r,EnumIncidentRelocationKind.OBSERVATION,v,v.fileIds)
    }
    private fun receiptFacts(r:IncidentRelocationDao,v:IncidentRelocationReceiptVo) {
        val departure=record<IncidentRelocationDepartureVo>(r,EnumIncidentRelocationKind.DEPARTURE)!!
        past(v.receivedAt);location(r.orgId,v.toLocationId,false)
        if(!v.physicallyPresent||v.receivedAt.isBefore(departure.departedAt))fail("须据实核对已接收在场的实物和实际时间；未收到实物时继续记录查找及事故事实")
        text(v.condition,v.actualMeasures,v.followUp,v.accessRecordFileIds,v.signedFileIds)
        val observations=rows(r).filter {it.kind==EnumIncidentRelocationKind.OBSERVATION}.map {read<IncidentWorkObservationVo>(it)}
        if(observations.any {it.observedAt.isAfter(v.receivedAt)})fail("接收时间不得早于已经登记的运输观察")
        if(observations.any {it.unexpectedDamage}&&!v.unexpectedDamage)fail("运输中实际发现的额外损伤不得抹去")
    }
    fun receive(id:String,v:IncidentRelocationReceiptVo):String {
        val r=root(id,true);verify(r)
        record<IncidentRelocationReceiptSnapshot>(r,EnumIncidentRelocationKind.RECEIPT)?.let {if(it.receipt==v)return id;fail("实际接收原件不能覆盖，新增情况请追加事实")}
        if(r.state!=EnumIncidentRelocationState.DEPARTED)fail("须先记录实际搬出")
        receiptFacts(r,v)
        // 已发生的偏离地点、缺员、不安全或真实性存疑也必须如实接收记录，不能迫使填写安全结论。
        val c=collection(r.collectionId,true);val update=canUpdate(c,v.receivedAt)
        val snapshot=IncidentRelocationReceiptSnapshot(v,update,c.warehouseId)
        append(r,EnumIncidentRelocationKind.RECEIPT,snapshot,"${v.accessRecordFileIds},${v.signedFileIds}")
        val departure=record<IncidentRelocationDepartureVo>(r,EnumIncidentRelocationKind.DEPARTURE)!!
        movement(r,EnumMoveType.IN,v.receivedAt,departure.fromLocationId,v.toLocationId,v.receivingUserIds,v.custodianUserId,v.signedFileIds)
        if(update && usesCurrentWarehouse(c))CollectionLedgerFieldChangeRecorder.warehouse(c,v.toLocationId,"事故搬移实际接收库位",r.id.value)
        r.state=EnumIncidentRelocationState.RECEIVED
        return id
    }
    fun recordEmergency(v:IncidentEmergencyRelocationVo):String {
        val c=collection(v.collectionId,true);text(v.reason,v.actualInstruction,v.instructionFileIds)
        if(v.conservationUserId==v.keeperUserId)fail("事实复核须明确不同的保护修复人员和保管负责人")
        users.requireRole(v.conservationUserId,c.orgId,CollectionConst.ROLE_CONSERVATION)
        users.requireRole(v.keeperUserId,c.orgId,CollectionConst.ROLE_STORAGE_HEAD)
        // 真实事实补记不以现行任职或事前方案冒充当时授权；历史实际人员及缺失情况由原件保留、后续核查。
        IncidentRelocationDao.find {(IncidentRelocations.orgId eq c.orgId) and (IncidentRelocations.collectionId eq c.id.value) and (IncidentRelocations.mode eq EnumIncidentRelocationMode.EMERGENCY_RECORD)}.firstOrNull {emergency(it).event==v}?.let {verify(it);return it.id.value}
        departureFacts(c.orgId,v.departure)
        val facts=incidents.requireWorkBasis(c.orgId,c.id.value,v.incidentIds,verifySource=false)
        val original=json(mapOf("catalog" to c.toModel(),"recordedAt" to LocalDateTime.now(),"latestMovementAt" to newestMovementAt(c.id.value)))
        val update=canUpdate(c,v.departure.departedAt)
        val r=IncidentRelocationDao.new {orgId=c.orgId;collectionId=c.id.value;mode=EnumIncidentRelocationMode.EMERGENCY_RECORD}
        append(r,EnumIncidentRelocationKind.EMERGENCY,IncidentEmergencyRelocationSnapshot(v,facts,original,update),v.instructionFileIds)
        val departureFiles=listOfNotNull(v.departure.accessRecordFileIds,v.departure.signedFileIds,v.departure.packingCheckFileIds).joinToString(",")
        append(r,EnumIncidentRelocationKind.DEPARTURE,v.departure,departureFiles)
        movement(r,EnumMoveType.OUT,v.departure.departedAt,v.departure.fromLocationId,null,v.departure.releasingUserIds,null,departureFiles)
        if(update)applyDepartureLocation(r,c,v.departure.departedAt,v.departure.signedFileIds)
        r.state=EnumIncidentRelocationState.DEPARTED
        v.receipt?.let {receive(r.id.value,it)}
        return r.id.value
    }
    fun cancel(id:String,v:IncidentWorkCancelVo):String {
        val r=root(id,true);verify(r)
        record<IncidentWorkCancelVo>(r,EnumIncidentRelocationKind.CANCELED)?.let {if(it==v)return id;fail("取消原件不能覆盖")}
        if(r.mode!=EnumIncidentRelocationMode.PLANNED||r.state !in setOf(EnumIncidentRelocationState.PLAN,EnumIncidentRelocationState.APPROVED))fail("真实已发生搬移不得作废，须继续登记实际接收和处理")
        past(v.canceledAt);text(v.reason,v.fileIds)
        if(v.canceledAt.isBefore(plan(r).plan.preparedAt)||rows(r).filter {it.kind==EnumIncidentRelocationKind.CONFIRMATION}.any {read<IncidentWorkConfirmationSnapshot>(it).content.signedAt.isAfter(v.canceledAt)})fail("取消不得早于实际方案或本人确认")
        append(r,EnumIncidentRelocationKind.CANCELED,v,v.fileIds);r.state=EnumIncidentRelocationState.CANCELED;return id
    }
    fun context(collectionId:String):Map<String,Any?> {
        val c=CollectionDao.findById(collectionId) ?: fail("藏品不存在")
        CollectionIncidentReadAccess.require(scope).requireOrg(c.orgId)
        val overrides=IncidentRelocationLocationResolver().latest(c.orgId,setOf(collectionId),LocalDateTime.now())
        val locationId=if(overrides.containsKey(collectionId))overrides[collectionId]else c.warehouseId
        locationId?.let {if(WarehouseDao.findById(it)?.orgId!=c.orgId)fail("当前位置缺失或所属馆不一致")}
        return work.context(collectionId)+mapOf("actualLocation" to if(overrides.containsKey(collectionId))mapOf("inTransit" to (overrides[collectionId]==null),"locationId" to overrides[collectionId],"locationName" to overrides[collectionId]?.let {WarehouseDao.findById(it)?.name})else null,"currentLocationId" to if(overrides.containsKey(collectionId))overrides[collectionId]else c.warehouseId,"locations" to WarehouseDao.find {Warehouses.orgId eq c.orgId}.map {mapOf("value" to it.id.value,"label" to it.name,"active" to (it.status==EnumContentStatus.NORMAL))})
    }
    fun list(incidentId:String):List<Map<String,Any?>> {
        incidents.detail(incidentId);val orgId=CollectionIncidentDao[incidentId].orgId
        val access=CollectionIncidentReadAccess.require(scope)
        return IncidentRelocationDao.find {IncidentRelocations.orgId eq orgId}.onEach {rows(it)}.filter {incidentId in IncidentRelocationEvidence.caseIds(it)}.map {
            access.require(it.orgId,BIZ,it.id.value)
            verify(it,verifyContent=false)
            val declared=if(it.mode==EnumIncidentRelocationMode.PLANNED)plan(it).plan.collectionId else emergency(it).event.collectionId
            if(declared!=it.collectionId)fail("应急搬移原件与业务藏品不一致")
            incidentReadCaseFacts(it.orgId,IncidentRelocationEvidence.caseIds(it))
            summary(it) + mapOf("evidenceErrors" to archives.readForDisplay(BIZ,it.id.value,it.orgId).issues)
        }
    }
    fun detail(id:String):Map<String,Any?> {
        val access=CollectionIncidentReadAccess.require(scope)
        val r=IncidentRelocationDao.findById(id) ?: fail("应急搬移不存在")
        access.require(r.orgId,BIZ,id)
        verify(r,verifyContent=false)
        val p=if(r.mode==EnumIncidentRelocationMode.PLANNED)plan(r)else null
        val declaredCollection=if(p!=null)p.plan.collectionId else emergency(r).event.collectionId
        if(declaredCollection!=r.collectionId)fail("应急搬移原件与业务藏品不一致")
        val currentIds=incidentReadAffectedCases(r.orgId,r.collectionId)
        val facts=incidentReadCaseFacts(r.orgId,IncidentRelocationEvidence.caseIds(r))
        val expected=p?.caseFacts?:emergency(r).caseFacts
        val declaredCases=p?.plan?.incidentIds?:emergency(r).event.incidentIds
        if(expected.keys!=declaredCases)fail("应急搬移原件与关联事故清单不一致")
        return summary(r)+mapOf("plan" to p,"emergency" to if(p==null)emergency(r)else null,"factsChanged" to (facts!=expected||currentIds!=expected.keys),"planBasis" to basis(r,EnumIncidentWorkStage.PLAN),"endBasis" to basis(r,EnumIncidentWorkStage.END),"records" to rows(r).map {mapOf("id" to it.id.value,"kind" to it.kind,"content" to JsonUtils.mapper.readTree(it.content))},"archives" to archives.readForDisplay(BIZ,id,r.orgId).archives)
    }
    fun requireArchivedOriginal(id:String,archiveId:String,fileId:String) {
        if (id.isBlank() || id.length>100) fail("应急搬移标识不正确")
        CollectionCaseArchivedOriginal.require(detail(id),archiveId,fileId)
    }
    private fun summary(r:IncidentRelocationDao):Map<String,Any?> = mapOf("id" to r.id.value,"collectionId" to r.collectionId,"mode" to r.mode,"state" to r.state,"incidentIds" to IncidentRelocationEvidence.caseIds(r))
    private fun past(at:LocalDateTime) {if(at.isAfter(LocalDateTime.now()))fail("请填写已经实际发生的时间")}
    private fun text(vararg value:String) {if(value.any(String::isBlank))fail("请完整提供实际情况、现场进出及签署原件")}
    private fun fail(message:String):Nothing=throw BadRequestException(message)
}
