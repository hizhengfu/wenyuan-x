package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
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
class IncidentWorkService(
    private val scope: CollectionScopeResolver,
    private val users: CollectionResponsibleUserService,
    private val incidents: CollectionIncidentService,
    private val archives: CollectionPermanentArchiveService,
    private val exclusiveGuards: List<IncidentResolutionGuard> = emptyList(),
    private val sourcesService: CollectionIncidentSourceService = CollectionIncidentSourceService(),
) {
    companion object { const val BIZ = CollectionConst.BIZ_INCIDENT_WORK }
    private fun access(orgId:String,write:Boolean=false) {
        CollectionIncidentReadAccess.require(scope,write).requireOrg(orgId)
    }
    private fun locked(id: String): IncidentWorkDao {
        val meta = IncidentWorks.selectAll().where { IncidentWorks.id eq id }.singleOrNull() ?: fail("现场防护作业不存在")
        access(meta[IncidentWorks.orgId], true)
        CollectionDao.find { Collections.id eq meta[IncidentWorks.collectionId] }.forUpdate().singleOrNull() ?: fail("藏品不存在")
        return IncidentWorkDao.find { IncidentWorks.id eq id }.forUpdate().single()
    }
    private fun rows(r: IncidentWorkDao) = IncidentWorkRecordDao.find { IncidentWorkRecords.workId eq r.id.value }.sortedBy { it.sequence }.also { rows ->
        if(rows.any {it.orgId!=r.orgId})fail("现场防护记录所属馆与业务不一致")
    }
    private inline fun <reified T> value(row: IncidentWorkRecordDao): T = JsonUtils.fromJson(row.content, T::class.java)!!
    private fun plan(r: IncidentWorkDao) = value<IncidentWorkPlanSnapshot>(rows(r).first { it.kind == EnumIncidentWorkRecordKind.PLAN })
    private inline fun <reified T> record(r: IncidentWorkDao, kind: EnumIncidentWorkRecordKind): T? = rows(r).lastOrNull { it.kind == kind }?.let { value<T>(it) }
    private fun json(v: Any) = JsonUtils.toJson(v)!!
    private fun digest(v: String) = MessageDigest.getInstance("SHA-256").digest(v.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun verify(r: IncidentWorkDao,verifyContent:Boolean=true) {
        rows(r)
        CollectionOperationSafetyOriginals(archives,verifyContent).work(r)
    }
    private fun append(r: IncidentWorkDao, kind: EnumIncidentWorkRecordKind, v: Any, files: String): String {
        if (files.isBlank()) fail("请提供实际原件")
        val content = json(v)
        rows(r).firstOrNull { it.kind == kind && it.content == content }?.let { verify(r); return it.id.value }
        r.sequence += 1
        val row = IncidentWorkRecordDao.new { orgId = r.orgId; workId = r.id.value; sequence = r.sequence; this.kind = kind; this.content = content }
        archives.seal(r.orgId, BIZ, r.id.value, "${kind}_${row.id.value}", v, files)
        return row.id.value
    }
    private fun requirePeople(orgId: String, p: IncidentWorkPlanVo) {
        users.requireDepartmentMember(p.conservationUserId, orgId, p.departmentId)
        users.requireRole(p.conservationUserId, orgId, CollectionConst.ROLE_CONSERVATION)
        users.requireDepartmentHead(p.departmentHeadUserId, orgId, p.departmentId)
        users.requireRole(p.keeperUserId, orgId, CollectionConst.ROLE_STORAGE_HEAD)
        users.requireRole(p.leaderUserId, orgId, CollectionConst.ROLE_DIRECTOR)
        users.requireMuseumMember(p.securityUserId, orgId)
        if (setOf(p.conservationUserId, p.departmentHeadUserId, p.keeperUserId, p.leaderUserId).size != 4) fail("现场防护方案四个确认职责须由不同实际人员承担")
    }
    internal fun originalContext(c: CollectionDao, caseIds: Set<String>): String {
        if (c.lifeStatus !in setOf(EnumCollectionStatus.IN_STORE, EnumCollectionStatus.RETURN_HOLD, EnumCollectionStatus.IN_USE, EnumCollectionStatus.RESTORING)) fail("本入口须有馆内现有实物及原业务实际停止保管依据；不能代办归还或寻找失物")
        val lock = CollectionOperationLockDao.find { CollectionOperationLocks.collectionId eq c.id.value }.singleOrNull()
        var stoppedAt: LocalDateTime? = null
        var extraCustody: IncidentStoppedCustody? = null
        val sources = caseIds.flatMap { id ->
            val row = CollectionIncidentRecordDao.find { (CollectionIncidentRecords.incidentId eq id) and (CollectionIncidentRecords.kind eq EnumIncidentRecordKind.FACTS) }.maxBy { it.sequence }
            sourcesService.custodySources(JsonUtils.fromJson(row.content, IncidentFactsSnapshot::class.java)!!.facts)
        }
        val externalSource = sources.filterNotNull().firstOrNull { sourcesService.supportsStoppedCustody(it.bizType) }
        if (c.lifeStatus in setOf(EnumCollectionStatus.IN_USE, EnumCollectionStatus.RESTORING) && externalSource != null) {
            extraCustody = sourcesService.stoppedCustody(c.orgId, c.id.value, externalSource)
            stoppedAt = extraCustody.stoppedAt
        } else if (c.lifeStatus == EnumCollectionStatus.IN_USE) {
            val source = sources.firstOrNull { it?.bizType == CollectionConst.BIZ_SAMPLING } ?: fail("使用中实物须核实原作业实际停止，不能用现场防护绕过原业务")
            val p = source.recordId?.let(CollectionSamplingPlanDao::findById) ?: fail("实际取样方案缺失")
            if (p.samplingId != source.bizId || p.orgId != c.orgId || CollectionSamplingDao[source.bizId].collectionId != c.id.value) fail("实际取样原件来源不一致")
            stoppedAt = p.ended?.endedAt ?: fail("取样尚未实际结束，不能并行实施现场防护")
        } else if (c.lifeStatus == EnumCollectionStatus.RESTORING) {
            fail("修复中实物须关联本次修复实际停止及保管原件")
        }
        if (c.lifeStatus == EnumCollectionStatus.RETURN_HOLD && lock?.bizType != CollectionConst.BIZ_RETURN) fail("归还待核查实物应保留原归还占用，请先核实实际保管记录")
        if (lock != null) {
            if (lock.orgId != c.orgId) fail("原业务占用所属馆不一致")
            when (lock.bizType) {
                CollectionConst.BIZ_SAMPLING -> {
                    val source = sources.firstOrNull { it?.bizType == lock.bizType && it.bizId == lock.bizId } ?: fail("须核对当前取样占用的实际事故来源")
                    val p = source.recordId?.let(CollectionSamplingPlanDao::findById) ?: fail("取样方案来源缺失")
                    if (p.samplingId != lock.bizId || p.orgId != c.orgId || CollectionSamplingDao[lock.bizId].collectionId != c.id.value) fail("取样占用来源不一致")
                    stoppedAt = p.ended?.endedAt ?: fail("原取样尚未实际结束，须先如实记录停止操作，再办理现场防护")
                }
                CollectionConst.BIZ_RETURN -> {
                    if (sources.none { it?.bizType == lock.bizType && it.bizId == lock.bizId }) fail("须核对当前归还待核查的实际事故来源")
                    val r = CollectionReturnDao.findById(lock.bizId) ?: fail("归还来源不存在")
                    if (r.orgId != c.orgId || r.executionState != EnumReturnExecutionState.EXECUTED || c.lifeStatus != EnumCollectionStatus.RETURN_HOLD || c.id.value !in splitCollectionIds(r.collectionIds)) fail("须先登记实际接收保管，不能通过现场防护冒充已归还")
                    stoppedAt = r.executedAt ?: fail("实际接收时间缺失")
                    val received = CollectionPermanentArchiveDao.find { (CollectionPermanentArchives.bizType eq lock.bizType) and (CollectionPermanentArchives.bizId eq lock.bizId) and (CollectionPermanentArchives.phase eq "RECEIVED") }.singleOrNull() ?: fail("实际接收原件缺失")
                    if (received.orgId != c.orgId || received.digest != digest(received.snapshot) || JsonUtils.mapper.readTree(received.snapshot).get("inspection") != JsonUtils.mapper.readTree(json(r.inspection!!))) fail("归还实际接收原件不一致")
                }
                else -> {
                    val source = sources.filterNotNull().firstOrNull { it.bizType == lock.bizType && it.bizId == lock.bizId && sourcesService.supportsStoppedCustody(it.bizType) }
                        ?: fail("原业务仍在占用实物，请先按原流程撤回未实施作业或如实办理中止；现场防护不能绕过")
                    extraCustody = sourcesService.stoppedCustody(c.orgId, c.id.value, source)
                    stoppedAt = extraCustody.stoppedAt
                }
            }
        }
        val actualMovement=StorageMovementDao.find {StorageMovements.collectionId eq c.id.value}.orderBy(StorageMovements.operatedAt to SortOrder.DESC,StorageMovements.id to SortOrder.DESC).limit(1).singleOrNull()?.toModel()
        return json(mapOf("actualMovement" to actualMovement,"catalog" to CollectionCatalogMapper.fromCollection(c.toModel()), "lifeStatus" to c.lifeStatus, "inventoryHold" to c.inventoryHold, "sourceLock" to lock?.let { CollectionOperationLockRef(it.collectionId, it.bizType, it.bizId) }, "stoppedAt" to stoppedAt, "extraCustody" to extraCustody))
    }
    private fun current(r: IncidentWorkDao): IncidentWorkPlanSnapshot {
        verify(r)
        val p = plan(r); val c = CollectionDao[r.collectionId]
        if (c.orgId != r.orgId || incidents.requireWorkBasis(r.orgId, r.collectionId, p.plan.incidentIds) != p.caseFacts || !CollectionCatalogBaselineComparison.sameSource(p.originalContext, originalContext(c, p.plan.incidentIds))) fail("事故事实或实物占用依据已变化，旧方案不能继续授权新作业；已开始的作业请如实记录停止和结束")
        requirePeople(r.orgId, p.plan)
        return p
    }
    private fun noOtherWork(r: IncidentWorkDao) {
        exclusiveGuards.forEach { it.requireExclusiveWork(r.collectionId,BIZ,r.id.value) }
        if (IncidentWorkDao.find { (IncidentWorks.collectionId eq r.collectionId) and (IncidentWorks.state inList listOf(EnumIncidentWorkState.APPROVED, EnumIncidentWorkState.STARTED, EnumIncidentWorkState.ENDED)) }.any { it.id != r.id }) fail("同一实物仍有其他已批准或未共同确认结束的现场防护作业")
        val formed = plan(r).plan.preparedAt
        val laterEnd = IncidentWorkDao.find { (IncidentWorks.collectionId eq r.collectionId) and (IncidentWorks.state eq EnumIncidentWorkState.COMPLETED) }.any { other -> record<IncidentWorkEndVo>(other, EnumIncidentWorkRecordKind.ENDED)?.endedAt?.isAfter(formed) == true }
        if (laterEnd) fail("方案形成后已有其他现场作业完成，请重新核实当前条件另立方案")
    }
    fun create(v: IncidentWorkPlanVo): String {
        val c = CollectionDao.find { Collections.id eq v.collectionId }.forUpdate().singleOrNull() ?: fail("藏品不存在")
        access(c.orgId, true)
        if (v.preparedAt.isAfter(LocalDateTime.now()) || v.plannedStart.isBefore(v.preparedAt) || !v.plannedEnd.isAfter(v.plannedStart) || v.methods.isEmpty() || listOf(v.location, v.conditionBefore, v.necessity, v.interventionBoundary, v.procedure, v.safetyRequirements, v.stopConditions, v.siteAndAssignmentFileIds, v.signedPlanFileIds).any(String::isBlank)) fail("请完整记录原地非损伤性防护的实际方案、明确边界、时段、场所、责任人及原件")
        requirePeople(c.orgId, v)
        val facts = incidents.requireWorkBasis(c.orgId, c.id.value, v.incidentIds)
        if (facts.values.any { id ->
            val discovered = JsonUtils.fromJson(CollectionIncidentRecordDao[id].content, IncidentFactsSnapshot::class.java)!!.facts.discoveredAt
            v.preparedAt.isBefore(discovered)
        }) fail("专项方案形成时间不得早于所依据事故的实际发现")
        val original = originalContext(c, v.incidentIds)
        val stopped = JsonUtils.mapper.readTree(original).get("stoppedAt")
        if (stopped != null && !stopped.isNull) {
            val at = JsonUtils.mapper.treeToValue(stopped, LocalDateTime::class.java)
            if (v.plannedStart.isBefore(at)) fail("计划防护时间不得早于原业务实际停止或接收保管")
        }
        val r = IncidentWorkDao.new { orgId = c.orgId; collectionId = c.id.value }
        append(r, EnumIncidentWorkRecordKind.PLAN, IncidentWorkPlanSnapshot(v, facts, original), "${v.siteAndAssignmentFileIds},${v.signedPlanFileIds}")
        return r.id.value
    }
    private fun basis(r: IncidentWorkDao, stage: EnumIncidentWorkStage): String = digest(json(rows(r).filter { it.kind != EnumIncidentWorkRecordKind.CONFIRMATION && (stage == EnumIncidentWorkStage.END || it.kind == EnumIncidentWorkRecordKind.PLAN) }.map { it.content }))
    fun confirm(id: String, v: IncidentWorkConfirmationVo): String {
        val r = locked(id); verify(r); val p = plan(r).plan
        val roles = if (v.stage == EnumIncidentWorkStage.PLAN) linkedMapOf("CONSERVATION" to p.conservationUserId, "DEPARTMENT" to p.departmentHeadUserId, "KEEPER" to p.keeperUserId, "DIRECTOR" to p.leaderUserId) else linkedMapOf("CONSERVATION" to p.conservationUserId, "KEEPER" to p.keeperUserId)
        val actor = SecurityUtils.getUserDetail().id
        val role = roles.entries.singleOrNull { it.value == actor }?.key ?: fail("须由方案指定责任人员本人确认，管理员不能代办")
        if (v.stage == EnumIncidentWorkStage.PLAN) requirePeople(r.orgId, p) else {
            users.requireRole(p.conservationUserId, r.orgId, CollectionConst.ROLE_CONSERVATION)
            users.requireRole(p.keeperUserId, r.orgId, CollectionConst.ROLE_STORAGE_HEAD)
        }
        val all = rows(r).filter { it.kind == EnumIncidentWorkRecordKind.CONFIRMATION }.map { value<IncidentWorkConfirmationSnapshot>(it) }
        if (all.any { it.actorUserId == actor && it.content == v }) return id
        if (v.basisRevision != basis(r, v.stage) || v.opinion.isBlank() || v.fileIds.isBlank() || v.signedAt.isAfter(LocalDateTime.now())) fail("请核对当前完整方案或实际结束记录，填写本人意见、实际签署时间和原件")
        val earliest = if (v.stage == EnumIncidentWorkStage.PLAN) {
            if (r.state != EnumIncidentWorkState.PLAN) fail("方案已批准或开始，不能覆盖确认")
            current(r); noOtherWork(r); p.preparedAt
        } else {
            if (r.state != EnumIncidentWorkState.ENDED) fail("须先如实记录实际结束，再共同核对结束原件")
            record<IncidentWorkEndVo>(r, EnumIncidentWorkRecordKind.ENDED)!!.endedAt
        }
        if (v.signedAt.isBefore(earliest)) fail("本人签署不能早于方案形成或实际结束")
        val previousRoles = roles.keys.takeWhile { it != role }
        previousRoles.forEach { previous ->
            val signed = all.lastOrNull { it.role == previous && it.actorUserId == roles[previous] && it.content.stage == v.stage && it.content.basisRevision == v.basisRevision } ?: fail("请由前序责任人员先本人核对本版完整原件")
            if (v.signedAt.isBefore(signed.content.signedAt)) fail("本人签署时间与实际确认顺序不一致")
        }
        append(r, EnumIncidentWorkRecordKind.CONFIRMATION, IncidentWorkConfirmationSnapshot(v, actor, role), v.fileIds)
        if (role == roles.keys.last()) r.state = if (v.stage == EnumIncidentWorkStage.PLAN) EnumIncidentWorkState.APPROVED else EnumIncidentWorkState.COMPLETED
        return id
    }
    fun start(id: String, v: IncidentWorkStartVo): String {
        val r = locked(id); verify(r)
        record<IncidentWorkStartVo>(r, EnumIncidentWorkRecordKind.STARTED)?.let { if (it == v) return id; fail("实际开始原件不能覆盖") }
        if (r.state != EnumIncidentWorkState.APPROVED) fail("须先完成本方案四方本人确认")
        val p = current(r).plan; noOtherWork(r)
        val approved = rows(r).filter { it.kind == EnumIncidentWorkRecordKind.CONFIRMATION }.map { value<IncidentWorkConfirmationSnapshot>(it) }.last { it.role == "DIRECTOR" && it.content.stage == EnumIncidentWorkStage.PLAN }.content.signedAt
        if (v.startedAt.isBefore(approved) || v.startedAt.isBefore(p.plannedStart) || v.startedAt.isAfter(p.plannedEnd) || v.startedAt.isAfter(LocalDateTime.now()) || v.location != p.location || v.conservationUserId != p.conservationUserId || v.securityUserId != p.securityUserId || !v.physicallyPresent || !v.siteSafe || !v.noRelocation || !v.noRestorationOrSampling || listOf(v.condition, v.actualProtection, v.signedStartFileIds).any(String::isBlank)) fail("请核实原地实际开始时间、现场实物、安全条件、人员和非损伤性防护边界；不能通过此入口搬移、修复或取样")
        users.requireRole(v.custodianUserId, r.orgId, CollectionConst.ROLE_KEEPER)
        if (v.custodianUserId == v.conservationUserId) fail("防护执行与实际保管人员须分别在场")
        append(r, EnumIncidentWorkRecordKind.STARTED, v, v.signedStartFileIds); r.state = EnumIncidentWorkState.STARTED
        return id
    }
    /** 实际安全观察允许在事实变化后如实追加；它不是继续实施作业的授权。 */
    fun observation(id: String, v: IncidentWorkObservationVo): String {
        val r = locked(id); verify(r)
        if (r.state != EnumIncidentWorkState.STARTED) fail("仅在实际作业尚未结束时登记现场观察；结束后新问题应追加事故事实")
        val started = record<IncidentWorkStartVo>(r, EnumIncidentWorkRecordKind.STARTED)!!
        if (v.observedAt.isBefore(started.startedAt) || v.observedAt.isAfter(LocalDateTime.now()) || listOf(v.condition, v.actualMeasures, v.fileIds).any(String::isBlank)) fail("请记录实际现场观察时间、状态、已采取措施和原件")
        users.requireMuseumMember(v.observerUserId, r.orgId)
        return append(r, EnumIncidentWorkRecordKind.OBSERVATION, v, v.fileIds)
    }
    fun end(id: String, v: IncidentWorkEndVo): String {
        val r = locked(id); verify(r)
        record<IncidentWorkEndVo>(r, EnumIncidentWorkRecordKind.ENDED)?.let { if (it == v) return id; fail("已记录的实际结束不得覆盖；新增情况应追加事故事实") }
        if (r.state != EnumIncidentWorkState.STARTED) fail("尚未开始的方案请取消；已开始的作业须如实记录停止和结束")
        val start = record<IncidentWorkStartVo>(r, EnumIncidentWorkRecordKind.STARTED)!!
        val observations = rows(r).filter { it.kind == EnumIncidentWorkRecordKind.OBSERVATION }.map { value<IncidentWorkObservationVo>(it) }
        if (v.endedAt.isBefore(start.startedAt) || v.endedAt.isAfter(LocalDateTime.now()) || observations.any { it.observedAt.isAfter(v.endedAt) } || listOf(v.actualMeasures, v.conditionAfter, v.stopReason, v.followUp, v.signedEndFileIds).any(String::isBlank)) fail("须如实记录实际结束时间、措施、现状、停止或完成原因、后续安排及原件")
        if (observations.any { it.unexpectedDamage } && !v.unexpectedDamage) fail("已观察到的额外损伤不能在结束记录中抹去")
        if (v.endedAt.isAfter(plan(r).plan.plannedEnd) && v.withinApprovedScope) fail("超出批准时段须如实说明，不能标记为符合方案")
        append(r, EnumIncidentWorkRecordKind.ENDED, v, v.signedEndFileIds); r.state = EnumIncidentWorkState.ENDED
        return id
    }
    fun cancel(id: String, v: IncidentWorkCancelVo): String {
        val r = locked(id); verify(r)
        record<IncidentWorkCancelVo>(r, EnumIncidentWorkRecordKind.CANCELED)?.let { if (it == v) return id; fail("取消原件不能覆盖") }
        if (r.state !in setOf(EnumIncidentWorkState.PLAN, EnumIncidentWorkState.APPROVED)) fail("已实际开始不能作废，应如实记录停止和结束")
        if (v.canceledAt.isBefore(plan(r).plan.preparedAt) || v.canceledAt.isAfter(LocalDateTime.now()) || v.reason.isBlank() || v.fileIds.isBlank()) fail("请如实记录未实施取消的时间、原因及原件")
        if (rows(r).filter { it.kind == EnumIncidentWorkRecordKind.CONFIRMATION }.any { value<IncidentWorkConfirmationSnapshot>(it).content.signedAt.isAfter(v.canceledAt) }) fail("取消时间不得早于已经发生的本人确认")
        append(r, EnumIncidentWorkRecordKind.CANCELED, v, v.fileIds); r.state = EnumIncidentWorkState.CANCELED
        return id
    }
    fun list(incidentId: String): List<Map<String, Any?>> {
        incidents.detail(incidentId)
        val case = CollectionIncidentDao[incidentId]
        val access=CollectionIncidentReadAccess.require(scope)
        return IncidentWorkDao.find { IncidentWorks.orgId eq case.orgId }.filter { incidentId in plan(it).plan.incidentIds }.map {
            access.require(it.orgId,BIZ,it.id.value)
            verify(it,verifyContent=false)
            val p=plan(it)
            if(p.plan.collectionId!=it.collectionId || p.caseFacts.keys!=p.plan.incidentIds)fail("现场防护方案与业务藏品或事故清单不一致")
            incidentReadCaseFacts(it.orgId,p.plan.incidentIds)
            summary(it) + mapOf("evidenceErrors" to archives.readForDisplay(BIZ,it.id.value,it.orgId).issues)
        }
    }
    fun detail(id: String): Map<String, Any?> {
        val access=CollectionIncidentReadAccess.require(scope)
        val r=IncidentWorkDao.findById(id) ?: fail("现场防护作业不存在")
        access.require(r.orgId,BIZ,id)
        verify(r,verifyContent=false)
        val p=plan(r)
        if(p.plan.collectionId!=r.collectionId || p.caseFacts.keys!=p.plan.incidentIds)fail("现场防护方案与业务藏品或事故清单不一致")
        val currentIds=incidentReadAffectedCases(r.orgId,r.collectionId)
        val actualFacts=incidentReadCaseFacts(r.orgId,p.plan.incidentIds)
        return summary(r) + mapOf("plan" to p, "planBasis" to basis(r, EnumIncidentWorkStage.PLAN), "endBasis" to basis(r, EnumIncidentWorkStage.END), "factsChanged" to (actualFacts != p.caseFacts || currentIds != p.plan.incidentIds), "records" to rows(r).map { mapOf("id" to it.id.value, "kind" to it.kind, "content" to JsonUtils.mapper.readTree(it.content)) }, "archives" to archives.readForDisplay(BIZ,id,r.orgId).archives)
    }
    fun requireArchivedOriginal(id: String, archiveId: String, fileId: String) {
        if (id.isBlank() || id.length > 100) fail("现场防护作业标识不正确")
        CollectionCaseArchivedOriginal.require(detail(id), archiveId, fileId)
    }
    fun context(collectionId: String): Map<String, Any?> {
        val c = CollectionDao.findById(collectionId) ?: fail("藏品不存在")
        CollectionIncidentReadAccess.require(scope).requireOrg(c.orgId)
        val ids=incidentReadAffectedCases(c.orgId,collectionId)
        return mapOf("collectionId" to collectionId, "incidentIds" to ids, "totalRegNo" to c.totalRegNo, "name" to c.name)
    }
    private fun summary(r: IncidentWorkDao): Map<String, Any?> = mapOf("id" to r.id.value, "collectionId" to r.collectionId, "state" to r.state, "plan" to plan(r).plan)
    private fun fail(message: String): Nothing = throw BadRequestException(message)
}
