package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.*
import java.time.LocalDateTime

/** Reconstruct safety state from sealed events before a mutable projection can exclude a case. */
internal object IncidentSafetyLifecycleEvidence {
    private data class Event(val id:String,val sequence:Int,val kind:String,val content:String,val orgId:String)
    private fun fail():Nothing=throw BadRequestException("专项安全作业的阶段、确认或记录与永久原件不一致")
    private inline fun <reified T> read(content:String):T = try {
        JsonUtils.fromJson(content,T::class.java) ?: fail()
    } catch (_:tools.jackson.core.JacksonException) {fail()}
    private fun events(org:String,biz:String,id:String,sequence:Int,rows:List<Event>,collectionId:String?=null) {
        if(rows.isEmpty() || rows.size!=sequence || rows.withIndex().any {(index,row)->row.sequence!=index+1 || row.orgId!=org})fail()
        val saved=CollectionPermanentArchiveDao.find {(CollectionPermanentArchives.bizType eq biz) and (CollectionPermanentArchives.bizId eq id)}.toList()
        val locations=saved.filter {it.phase=="LOCATIONS_LEFT"}
        if(locations.isNotEmpty()) {
            if(biz!=CollectionConst.BIZ_INCIDENT_RELOCATION || locations.size!=1 || collectionId==null)fail()
            val departure=read<IncidentRelocationDepartureVo>(one(rows,"DEPARTURE",true)!!.content)
            val archive=locations.single()
            val content=JsonUtils.mapper.readTree(archive.snapshot)
            val placements=content.path("placements")
            val placementIds=placements.asSequence().map {it.path("id").asString()}.toList()
            if(archive.orgId!=org || archive.digest!=CollectionCareEvidence.digest(archive.snapshot) ||
                content.path("leftAt")!=JsonUtils.mapper.readTree(CollectionCareEvidence.json(departure.departedAt)) ||
                !placements.isArray || placements.isEmpty || placementIds.any(String::isBlank) || placementIds.distinct().size!=placementIds.size ||
                placements.any {it.path("sourceBizType").asString().isBlank() || it.path("sourceBizId").asString().isBlank() ||
                    it.path("location").path("collectionId").asString()!=collectionId || it.path("location").path("locationId").asString()!=departure.fromLocationId})fail()
        }
        if(saved.filter {it.phase!="LOCATIONS_LEFT"}.map {it.phase}.toSet()!=rows.map {"${it.kind}_${it.id}"}.toSet() || saved.size!=rows.size+locations.size)fail()
        val phases=saved.associateBy {it.phase}
        rows.forEach {row ->
            val archive=phases.getValue("${row.kind}_${row.id}")
            if(archive.orgId!=org || archive.digest!=CollectionCareEvidence.digest(archive.snapshot) || JsonUtils.mapper.readTree(archive.snapshot)!=JsonUtils.mapper.readTree(row.content))fail()
        }
    }
    private fun one(rows:List<Event>,kind:String,required:Boolean=false):Event? {
        val matching=rows.filter {it.kind==kind}
        if(matching.size>1 || required && matching.isEmpty())fail()
        return matching.singleOrNull()
    }
    private fun confirmations(rows:List<Event>,stage:EnumIncidentWorkStage,roles:Map<String,String>,earliest:LocalDateTime?):Int {
        val signed=rows.filter {it.kind=="CONFIRMATION"}.map {read<IncidentWorkConfirmationSnapshot>(it.content)}.filter {it.content.stage==stage}
        if(signed.isNotEmpty() && earliest==null || signed.size>roles.size || signed.map {it.role}!=roles.keys.take(signed.size))fail()
        val basis=CollectionCareEvidence.digest(CollectionCareEvidence.json(rows.filter {it.kind!="CONFIRMATION" && (stage==EnumIncidentWorkStage.END || it.kind=="PLAN")}.map {it.content}))
        var previous=earliest
        signed.forEach {signature ->
            if(signature.actorUserId!=roles[signature.role] || signature.content.basisRevision!=basis || signature.content.signedAt.isBefore(previous ?: fail()))fail()
            previous=signature.content.signedAt
        }
        return signed.size
    }
    fun work(root:IncidentWorkDao) {
        val rows=IncidentWorkRecordDao.find {IncidentWorkRecords.workId eq root.id.value}.sortedBy {it.sequence}.map {Event(it.id.value,it.sequence,it.kind.name,it.content,it.orgId)}
        events(root.orgId,CollectionConst.BIZ_INCIDENT_WORK,root.id.value,root.sequence,rows)
        val plan=read<IncidentWorkPlanSnapshot>(one(rows,"PLAN",true)!!.content).plan
        if(plan.collectionId!=root.collectionId)fail()
        val start=one(rows,"STARTED")?.let {read<IncidentWorkStartVo>(it.content)}
        val end=one(rows,"ENDED")?.let {read<IncidentWorkEndVo>(it.content)}
        val cancel=one(rows,"CANCELED")?.let {read<IncidentWorkCancelVo>(it.content)}
        if(cancel!=null && (start!=null || end!=null) || end!=null && start==null || rows.any {it.kind=="OBSERVATION"} && start==null)fail()
        val planCount=confirmations(rows,EnumIncidentWorkStage.PLAN,linkedMapOf("CONSERVATION" to plan.conservationUserId,"DEPARTMENT" to plan.departmentHeadUserId,"KEEPER" to plan.keeperUserId,"DIRECTOR" to plan.leaderUserId),plan.preparedAt)
        val endCount=confirmations(rows,EnumIncidentWorkStage.END,linkedMapOf("CONSERVATION" to plan.conservationUserId,"KEEPER" to plan.keeperUserId),end?.endedAt)
        if(start!=null && planCount!=4 || start!=null && start.startedAt.isBefore(plan.preparedAt) || end!=null && end.endedAt.isBefore(start!!.startedAt))fail()
        val expected=when {
            cancel!=null -> EnumIncidentWorkState.CANCELED
            end!=null && endCount==2 -> EnumIncidentWorkState.COMPLETED
            end!=null -> EnumIncidentWorkState.ENDED
            start!=null -> EnumIncidentWorkState.STARTED
            planCount==4 -> EnumIncidentWorkState.APPROVED
            else -> EnumIncidentWorkState.PLAN
        }
        if(root.state!=expected)fail()
    }
    fun relocation(root:IncidentRelocationDao) {
        val rows=IncidentRelocationRecordDao.find {IncidentRelocationRecords.relocationId eq root.id.value}.sortedBy {it.sequence}.map {Event(it.id.value,it.sequence,it.kind.name,it.content,it.orgId)}
        events(root.orgId,CollectionConst.BIZ_INCIDENT_RELOCATION,root.id.value,root.sequence,rows,root.collectionId)
        val planned=root.mode==EnumIncidentRelocationMode.PLANNED
        val plan=one(rows,"PLAN",planned)?.let {read<IncidentRelocationPlanSnapshot>(it.content).plan}
        val emergency=one(rows,"EMERGENCY",!planned)?.let {read<IncidentEmergencyRelocationSnapshot>(it.content).event}
        if(planned && emergency!=null || !planned && plan!=null || (plan?.collectionId ?: emergency!!.collectionId)!=root.collectionId)fail()
        val departure=one(rows,"DEPARTURE")?.let {read<IncidentRelocationDepartureVo>(it.content)}
        val receipt=one(rows,"RECEIPT")?.let {read<IncidentRelocationReceiptSnapshot>(it.content).receipt}
        val cancel=one(rows,"CANCELED")?.let {read<IncidentWorkCancelVo>(it.content)}
        if(cancel!=null && (!planned || departure!=null || receipt!=null) || receipt!=null && departure==null || !planned && departure==null || rows.any {it.kind=="OBSERVATION"} && departure==null)fail()
        val planRoles=plan?.let {linkedMapOf("CONSERVATION" to it.conservationUserId,"DEPARTMENT" to it.departmentHeadUserId,"KEEPER" to it.keeperUserId,"DIRECTOR" to it.leaderUserId)} ?: emptyMap()
        val planCount=confirmations(rows,EnumIncidentWorkStage.PLAN,planRoles,plan?.preparedAt)
        val endCount=confirmations(rows,EnumIncidentWorkStage.END,linkedMapOf("CONSERVATION" to (plan?.conservationUserId ?: emergency!!.conservationUserId),"KEEPER" to (plan?.keeperUserId ?: emergency!!.keeperUserId)),receipt?.receivedAt)
        if(planned && departure!=null && planCount!=4 || receipt!=null && receipt.receivedAt.isBefore(departure!!.departedAt))fail()
        val expected=when {
            cancel!=null -> EnumIncidentRelocationState.CANCELED
            receipt!=null && endCount==2 -> EnumIncidentRelocationState.COMPLETED
            receipt!=null -> EnumIncidentRelocationState.RECEIVED
            departure!=null -> EnumIncidentRelocationState.DEPARTED
            planCount==4 -> EnumIncidentRelocationState.APPROVED
            else -> EnumIncidentRelocationState.PLAN
        }
        if(root.state!=expected)fail()
    }
}
