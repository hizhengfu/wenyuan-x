package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import java.time.LocalDateTime
import io.hizhengfu.cloud.collection.service.CollectionCareEvidence as C
import io.hizhengfu.cloud.collection.service.CollectionEmergencyEvidence as E

internal object CollectionEmergencyDutyEvidence {
    fun key(t:EmergencyDutyTarget)=C.digest(C.json(t))
    fun value(r:CollectionEmergencyDutyDao)=mapOf("targetKey" to r.targetKey,"version" to r.version,"appointment" to r.appointment)
    fun activeAt(r:CollectionEmergencyDutyDao):LocalDateTime?=r.acceptance?.let {maxOf(r.appointment.request.effectiveAt,it.confirmedAt)}
    fun records(org:String,t:EmergencyDutyTarget):List<CollectionEmergencyDutyDao> {
        val k=key(t)
        val rows=CollectionEmergencyDutyDao.find {(CollectionEmergencyDuties.orgId eq org) and (CollectionEmergencyDuties.targetKey eq k)}.sortedBy {it.version}
        rows.forEachIndexed {index,r->
            val a=r.appointment;val v=a.request
            if(v.target!=t || r.version!=index+1 || v.previousId!=rows.getOrNull(index-1)?.id?.value)C.fail("应急职责承接版本不连续")
            E.exact(org,r.id.value,"DUTY_APPOINTED",value(r),listOfNotNull(v.fileIds,v.unavailableFileIds).joinToString(","))
            E.exact(org,r.id.value,"DUTY_HANDOVER",r.handover,r.handover?.record?.fileIds)
            E.exact(org,r.id.value,"DUTY_ACCEPTED",r.acceptance,r.acceptance?.record?.fileIds)
            if(r.handover?.actorUserId?.let {it!=a.fromUserId}==true || r.acceptance?.actorUserId?.let {it!=v.toUserId}==true)C.fail("应急职责承接须由原任和接任人员分别本人核对")
            if(r.acceptance!=null && (r.handover==null && !v.predecessorUnavailable || r.acceptance!!.confirmedAt<a.submittedAt || r.handover?.let {it.confirmedAt>r.acceptance!!.confirmedAt}==true))C.fail("应急职责承接缺少在先交接依据")
            if(r.handover?.let {it.confirmedAt<a.submittedAt}==true)C.fail("应急职责交接时间不得早于实际登记")
            val from=active(rows.take(index),a.submittedAt)?.appointment?.request?.toUserId ?: a.originalUserId
            if(a.fromUserId!=from)C.fail("应急职责承接须连续引用实际在任人员")
        }
        val archived=CollectionPermanentArchiveDao.find {(CollectionPermanentArchives.orgId eq org) and (CollectionPermanentArchives.bizType eq E.BIZ) and (CollectionPermanentArchives.phase eq "DUTY_APPOINTED")}.onEach {if(it.digest!=C.digest(it.snapshot))C.fail("应急职责指定原件摘要不一致")}.filter {JsonUtils.mapper.readTree(it.snapshot)["targetKey"]?.asText()==k}.map {it.bizId}.toSet()
        if(archived!=rows.map {it.id.value}.toSet())C.fail("应急职责指定原件不能删除")
        return rows
    }
    fun active(rows:List<CollectionEmergencyDutyDao>,at:LocalDateTime):CollectionEmergencyDutyDao?=rows.filterIndexed {index,r->
        val start=activeAt(r)
        start!=null && start<=at && (rows.getOrNull(index+1)?.appointment?.submittedAt?.let {start<=it}!=false)
    }.lastOrNull()
    fun active(org:String,t:EmergencyDutyTarget,at:LocalDateTime)=active(records(org,t),at)
    fun actor(org:String,t:EmergencyDutyTarget,original:String,at:LocalDateTime)=active(org,t,at)?.appointment?.request?.toUserId ?: original
    fun signatureValue(s:CollectionCareSignature?,dutyId:String?):Any? {
        if(s==null) {if(dutyId!=null)C.fail("承接依据不能脱离实际核对签署");return null}
        return if(dutyId==null)s else mapOf("signature" to s,"dutyId" to dutyId)
    }
    fun requireActor(org:String,t:EmergencyDutyTarget,original:String,actor:String,at:LocalDateTime,dutyId:String?) {
        if(dutyId==null) {if(actor!=original)C.fail("应急原件核对人员变化须有已生效的责任承接原件");return}
        val rows=records(org,t)
        val r=rows.find {it.id.value==dutyId} ?: C.fail("应急责任承接原件不存在")
        val start=activeAt(r)
        if(start!=null && rows.getOrNull(rows.indexOf(r)+1)?.appointment?.submittedAt?.let {start>it}==true)C.fail("生效前已被替代的责任指定不能授权后续办理")
        if(start==null || start>at || r.appointment.request.toUserId!=actor)C.fail("须在本人接受职责且实际生效后承接后续办理")
    }
}
