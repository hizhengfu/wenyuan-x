package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.GenericPage
import io.hizhengfu.cloud.common.utils.SecurityUtils
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.select
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime
import io.hizhengfu.cloud.collection.service.CollectionCareEvidence as C

/** 待办来源保持只读；提醒状态不能充当签署、处置、检查或演练记录。 */
@Component
@Transactional
class CollectionSafetyReminderService(
    private val scope:CollectionScopeResolver,
    private val recipients:CollectionSafetyRecipientService,
    private val sources:List<CollectionSafetyTaskSource>,
    private val ports:List<CollectionSafetyReminderDeliveryPort> = emptyList(),
    private val clock:Clock = Clock.systemDefaultZone(),
) {
    private fun now()=LocalDateTime.now(clock)
    private fun records(org:String)=CollectionSafetyReminderSettingsDao.find {CollectionSafetyReminderSettings.orgId eq org}.sortedBy {it.version}
    private fun latest(org:String)=records(org).lastOrNull()
    // 新接入的来源须由本馆管理员明确选择；旧的默认配置不因版本升级扩大发送范围。
    private fun defaultSettings()=CollectionSafetyReminderSettingsVo(enabled=false,domains=setOf(EnumCollectionSafetyDomain.CARE,EnumCollectionSafetyDomain.JOINT,EnumCollectionSafetyDomain.EMERGENCY),leadDays=0,repeatDays=7,coordinatorUserIds=emptySet())
    private fun lock(org:String)=CollectionOrgDao.find {CollectionOrgs.id eq org}.forUpdate().singleOrNull() ?: C.fail("收藏单位不存在")
    private fun visible(org:String,write:Boolean=false) {
        val allowed=if(!scope.isMulti())setOf(scope.currentOrgId()) else if(write)scope.writableOrgIds() else scope.readableOrgIds()
        OrgScopedReadPolicy.requireVisible(allowed,org,"藏品安全待办")
        CollectionOrgDao.findById(org) ?: C.fail("收藏单位不存在")
    }
    private fun manage(org:String,write:Boolean=false) {visible(org,write);if(!CollectionSafetyRecipientService.canManage(SecurityUtils.getUserDetail()))C.fail("缺少安全待办提醒设置权限")}
    private fun readable()=EnumCollectionSafetyDomain.entries.filter {CollectionSafetyRecipientService.canRead(SecurityUtils.getUserDetail(),it)}.toSet()
    private fun collect(org:String,domains:Set<EnumCollectionSafetyDomain>,at:LocalDateTime)=sources.filter {it.domain in domains}.flatMap {source->
        source.tasks(org,at).onEach {if(it.orgId!=org || it.domain!=source.domain)C.fail("待办来源返回了错误的收藏单位或业务范围")}
    }.also {if(it.map {t->t.key}.distinct().size!=it.size)C.fail("安全待办来源标识重复，请核实任务来源")}
    private fun eligible(task:CollectionSafetyTask,v:CollectionSafetyReminderSettingsVo,at:LocalDateTime):Boolean =
        task.status!="IN_PROGRESS" && (task.dueAt?.let {it<=at.plusDays(v.leadDays.toLong())} ?: (task.status in setOf("PENDING","ORIGINAL_ERROR","OVERDUE")))
    private fun fingerprint(task:CollectionSafetyTask)=C.digest("${task.key}|${task.basis}|${task.status}")
    private fun resolved(org:String,v:CollectionSafetyReminderSettingsVo,at:LocalDateTime)=collect(org,v.domains,at).filter {eligible(it,v,at)}.flatMap {task->
        recipients.resolve(task,v.coordinatorUserIds).userIds.sorted().map {user->(user to task.key) to fingerprint(task)}
    }.toMap()

    fun options():Map<String,Any?> {
        val domains=readable();if(domains.isEmpty())C.fail("缺少可查阅的安全待办业务权限")
        val allowed=if(!scope.isMulti())setOf(scope.currentOrgId()) else scope.readableOrgIds()
        return mapOf("domains" to domains,"orgs" to CollectionOrgDao.all().filter {allowed==null || it.id.value in allowed}.map {mapOf("value" to it.id.value,"label" to it.name)})
    }
    fun context(org:String):Map<String,Any?> {
        visible(org);val domains=readable();if(domains.isEmpty())C.fail("缺少可查阅的安全待办业务权限")
        val v=latest(org)?.record?.request ?: defaultSettings()
        return mapOf("orgId" to org,"domains" to domains,"enabled" to v.enabled,"reminderDomains" to v.domains.intersect(domains),"transportAvailable" to (ports.size==1),"canManage" to CollectionSafetyRecipientService.canManage(SecurityUtils.getUserDetail()))
    }
    fun page(page:GenericPage<Map<String,Any?>>,orgId:String?=null,domain:EnumCollectionSafetyDomain?=null,status:String?=null,keyword:String?=null):GenericPage<Map<String,Any?>> {
        val domains=readable();if(domains.isEmpty() || domain!=null && domain !in domains)C.fail("缺少所选安全待办业务权限")
        val allowed=if(!scope.isMulti())setOf(scope.currentOrgId()) else scope.readableOrgIds()
        if(orgId!=null)visible(orgId)
        val orgs=CollectionOrgDao.all().filter {(allowed==null || it.id.value in allowed) && (orgId==null || it.id.value==orgId)}
        val at=now();val selected=domain?.let {setOf(it)} ?: domains
        val result=orgs.flatMap {org->
            val v=latest(org.id.value)?.record?.request ?: defaultSettings()
            collect(org.id.value,selected,at).filter {(status.isNullOrBlank() || it.status==status) && (keyword.isNullOrBlank() || listOfNotNull(it.title,it.name,it.sourceId).any {s->s.contains(keyword,true)})}.map {task->
                val r=recipients.resolve(task,v.coordinatorUserIds)
                mapOf<String,Any?>("id" to task.key,"orgId" to org.id.value,"orgName" to org.name,"domain" to task.domain,"kind" to task.kind,"title" to task.title,"sourceId" to task.sourceId,"recordId" to task.recordId,"collectionId" to task.collectionId,"name" to task.name,"dueAt" to task.dueAt,"status" to task.status,"evidenceError" to task.evidenceError,
                    "responsibleUserIds" to task.assignees.map {it.userId}.distinct(),"recipientUserIds" to r.userIds,"unavailableUserIds" to r.unavailableUserIds,"coordinatorUserIds" to r.coordinatorUserIds,"coordinationMissing" to r.coordinationMissing,
                    "reminderEnabled" to (v.enabled && org.orgStatus==EnumOrgStatus.ENABLED && task.domain in v.domains),"reminderDue" to eligible(task,v,at))
            }
        }.sortedWith(compareBy<Map<String,Any?>>({when(it["status"]){"ORIGINAL_ERROR"->0;"OVERDUE"->1;"PENDING"->2;"SCHEDULED"->3;else->4}},{it["dueAt"] as? LocalDateTime},{it["id"] as String}))
        page.total=result.size.toLong();page.list=if(page.pageSize==0)result else result.drop(page.startRow.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()).take(page.pageSize);return page
    }
    fun settings(org:String):Map<String,Any?> {
        manage(org);val rows=records(org);val last=rows.lastOrNull()
        return mapOf("orgId" to org,"currentId" to last?.id?.value,"settings" to (last?.record?.request ?: defaultSettings()),"transportAvailable" to (ports.size==1),"history" to rows.asReversed().map {mapOf("id" to it.id.value,"version" to it.version,"record" to it.record)})
    }
    fun saveSettings(org:String,v:CollectionSafetyReminderSettingsVo):String {
        manage(org,true);lock(org)
        val last=latest(org)
        if(last?.record?.request==v)return last.id.value
        if(v.previousId!=last?.id?.value)C.fail("提醒设置已变化，请重新核对最新版本")
        if(v.leadDays !in 0..365 || v.repeatDays !in 1..365 || v.domains.isEmpty())C.fail("请选择提醒业务，提前天数为 0 至 365 天，重复间隔为 1 至 365 天")
        if(v.enabled && sources.map {it.domain}.toSet().containsAll(v.domains).not())C.fail("所选业务缺少可用的待办来源")
        v.coordinatorUserIds.forEach {user->if(user.isBlank() || v.domains.any {!recipients.eligible(org,it,CollectionSafetyAssignee(user))})C.fail("协调人员须为本馆有效账号，并具有全部所选业务的查看办理权限")}
        val row=CollectionSafetyReminderSettingsDao.new {orgId=org;version=(last?.version ?: 0)+1;record=CollectionSafetyReminderSettingsRecord(v,SecurityUtils.getUserId() ?: C.fail("须登录后设置提醒"),now())}
        // 旧投递先取消；旧设置仍保留，下一次巡检按新版本重新生成。
        pending(org).forEach {cancel(it,now())}
        return row.id.value
    }
    fun history(org:String,page:GenericPage<Map<String,Any?>>):GenericPage<Map<String,Any?>> {
        manage(org)
        val rows=CollectionSafetyReminderDeliveryDao.find {CollectionSafetyReminderDeliveries.orgId eq org}.sortedByDescending {it.requestedAt}
        page.total=rows.size.toLong();val selected=if(page.pageSize==0)rows else rows.drop(page.startRow.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()).take(page.pageSize)
        page.list=selected.map {d->mapOf("id" to d.id.value,"userId" to d.userId,"taskCount" to d.items.size,"state" to d.state,"requestedAt" to d.requestedAt,"nextAttemptAt" to d.nextAttemptAt,"attempts" to d.attempts,"lastError" to d.lastError,"queuedAt" to d.queuedAt,"messageId" to d.messageId,
            "attemptHistory" to CollectionSafetyReminderAttemptDao.find {CollectionSafetyReminderAttempts.deliveryId eq d.id.value}.sortedBy {it.attemptedAt}.map {mapOf("attemptedAt" to it.attemptedAt,"result" to it.result,"messageId" to it.messageId)})};return page
    }
    private fun pending(org:String)=CollectionSafetyReminderDeliveryDao.find {(CollectionSafetyReminderDeliveries.orgId eq org) and (CollectionSafetyReminderDeliveries.state inList listOf("PENDING","RETRY"))}.toList()
    private fun states(org:String)=CollectionSafetyReminderStateDao.find {CollectionSafetyReminderStates.orgId eq org}.toList()
    private fun attempt(d:CollectionSafetyReminderDeliveryDao,result:String,at:LocalDateTime,message:String?=null) {CollectionSafetyReminderAttemptDao.new {orgId=d.orgId;deliveryId=d.id.value;attemptedAt=at;this.result=result;messageId=message}}
    private fun cancel(d:CollectionSafetyReminderDeliveryDao,at:LocalDateTime) {
        if(d.state !in setOf("PENDING","RETRY"))return
        d.state="CANCELLED";d.items.forEach {item->CollectionSafetyReminderStateDao.findById(item.stateId)?.takeIf {it.orgId==d.orgId && it.pendingDeliveryId==d.id.value}?.pendingDeliveryId=null};attempt(d,"CANCELLED",at)
    }
    /** 每个调用独立事务，先形成待投递批次，不在此调用消息模块。 */
    fun refresh(org:String):List<String> {
        val museum=lock(org);val setting=latest(org);val v=setting?.record?.request;val at=now();val old=states(org)
        if(museum.orgStatus!=EnumOrgStatus.ENABLED || v?.enabled!=true) {old.forEach {it.active=false};pending(org).forEach {cancel(it,at)};return emptyList()}
        val actual=resolved(org,v,at)
        pending(org).filter {d->d.settingsId!=setting.id.value || d.items.any {actual[d.userId to it.taskKey]!=it.fingerprint}}.forEach {cancel(it,at)}
        val byTarget=old.associateBy {it.userId to it.taskKey}.toMutableMap()
        old.filter {(it.userId to it.taskKey) !in actual}.forEach {it.active=false}
        actual.forEach {(key,fp)->
            val state=byTarget[key] ?: CollectionSafetyReminderStateDao.new {orgId=org;userId=key.first;taskKey=key.second;fingerprint=fp}.also {byTarget[key]=it}
            if(!state.active)state.lastFingerprint=null
            state.active=true;state.fingerprint=fp
        }
        val due=byTarget.values.filter {it.active && it.pendingDeliveryId==null && (it.lastFingerprint!=it.fingerprint || it.lastNotifiedAt?.plusDays(v.repeatDays.toLong())?.let {time->time<=at}!=false)}
        return due.groupBy {it.userId}.toSortedMap().flatMap {(user,rows)->rows.sortedBy {it.taskKey}.chunked(500).map {chunk->
            val d=CollectionSafetyReminderDeliveryDao.new {orgId=org;userId=user;settingsId=setting.id.value;idempotencyKey=C.digest("collection-safety|${id.value}");items=chunk.map {CollectionSafetyReminderItem(it.id.value,it.taskKey,it.fingerprint)};requestedAt=at;nextAttemptAt=at}
            chunk.forEach {it.pendingDeliveryId=d.id.value};d.id.value
        }}
    }
    /** 与消息表入队处于同一数据库事务。失败须由外部调度调用 failed，不能在本事务内吞掉。 */
    fun deliver(id:String):String? {
        val first=CollectionSafetyReminderDeliveryDao.findById(id) ?: return null
        val museum=lock(first.orgId)
        val d=CollectionSafetyReminderDeliveryDao.find {CollectionSafetyReminderDeliveries.id eq id}.forUpdate().single()
        if(d.state !in setOf("PENDING","RETRY") || d.nextAttemptAt>now())return d.messageId
        val at=now();val setting=latest(d.orgId);val v=setting?.record?.request
        if(museum.orgStatus!=EnumOrgStatus.ENABLED || v?.enabled!=true || setting.id.value!=d.settingsId) {cancel(d,at);return null}
        val actual=resolved(d.orgId,v,at)
        if(d.items.isEmpty() || d.items.any {item->
            val state=CollectionSafetyReminderStateDao.findById(item.stateId)
            state==null || !state.active || state.orgId!=d.orgId || state.userId!=d.userId || state.taskKey!=item.taskKey || state.fingerprint!=item.fingerprint || state.pendingDeliveryId!=id || actual[d.userId to item.taskKey]!=item.fingerprint
        }) {cancel(d,at);return null}
        val port=ports.singleOrNull() ?: C.fail("站内提醒投递通道不可用")
        val message=port.enqueue(d.orgId,d.userId,id,d.idempotencyKey)
        if(message.isBlank())C.fail("站内提醒未返回有效消息记录")
        d.state="QUEUED";d.messageId=message;d.queuedAt=at;d.attempts+=1;d.lastError=null
        d.items.forEach {item->CollectionSafetyReminderStateDao[item.stateId].apply {lastFingerprint=item.fingerprint;lastNotifiedAt=at;pendingDeliveryId=null}}
        attempt(d,"QUEUED",at,message);return message
    }
    fun failed(id:String) {
        val first=CollectionSafetyReminderDeliveryDao.findById(id) ?: return;lock(first.orgId)
        val d=CollectionSafetyReminderDeliveryDao.find {CollectionSafetyReminderDeliveries.id eq id}.forUpdate().single()
        if(d.state !in setOf("PENDING","RETRY") || d.nextAttemptAt>now())return
        d.attempts+=1;d.state="RETRY";d.lastError="站内提醒未入队，将自动重试；请检查消息服务和业务原件"
        val minutes=(5L shl minOf(d.attempts-1,7)).coerceAtMost(360L);d.nextAttemptAt=now().plusMinutes(minutes);attempt(d,"FAILED",now())
    }
    fun enabledOrgs():List<String> {
        val s=CollectionSafetyReminderSettings
        // 一次读取有设置的启用单位，并按最新版判断；不再全量读取单位后逐馆查设置。
        return s.join(CollectionOrgs,JoinType.INNER,s.orgId,CollectionOrgs.id)
            .select(s.orgId,s.version,s.record).where {CollectionOrgs.orgStatus eq EnumOrgStatus.ENABLED}
            .orderBy(s.version).associateBy {it[s.orgId]}.values
            .filter {it[s.record].request.enabled}.map {it[s.orgId]}
    }
    fun nextWakeAt(orgs:List<String>):LocalDateTime? {
        val at=now();val times=mutableListOf<LocalDateTime>()
        val d=CollectionSafetyReminderDeliveries;val retry=d.nextAttemptAt.min()
        d.select(retry).where {d.state inList listOf("PENDING","RETRY")}.single()[retry]?.let(times::add)
        orgs.forEach {org->
            val v=latest(org)?.record?.request?.takeIf {it.enabled} ?: return@forEach
            collect(org,v.domains,at).forEach {task->task.dueAt?.let {due->
                listOf(due.minusDays(v.leadDays.toLong()),due).filter {it>at}.forEach(times::add)
            }}
            states(org).filter {it.active && it.pendingDeliveryId==null}.forEach {state->
                state.lastNotifiedAt?.plusDays(v.repeatDays.toLong())?.takeIf {it>at}?.let(times::add)
            }
        }
        // 日级义务可能在跨日后才出现；仅启用提醒的单位需要跨日校验。
        if(orgs.isNotEmpty())times+=at.toLocalDate().plusDays(1).atStartOfDay()
        return times.minOrNull()
    }
    fun dueDeliveries():List<String> = CollectionSafetyReminderDeliveryDao.find {(CollectionSafetyReminderDeliveries.state inList listOf("PENDING","RETRY")) and (CollectionSafetyReminderDeliveries.nextAttemptAt lessEq now())}.orderBy(CollectionSafetyReminderDeliveries.nextAttemptAt to SortOrder.ASC).limit(200).map {it.id.value}
}
