package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.GenericPage
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.service.CrudService
import io.hizhengfu.cloud.common.utils.SecurityUtils
import jakarta.servlet.http.HttpServletRequest
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.LocalDateTime

@Component
@Transactional
class CollectionIncidentService(
    private val scope: CollectionScopeResolver,
    private val users: CollectionResponsibleUserService,
    private val archives: CollectionPermanentArchiveService,
    private val sources: CollectionIncidentSourceService,
    private val resolutionGuards: List<IncidentResolutionGuard> = emptyList(),
) : CrudService<CollectionIncidentDao>(CollectionIncidentDao, CollectionConst.CACHE) {
    private val archiveEvidence = CollectionIncidentArchiveEvidence(archives, sources)
    private fun access(orgId:String,write:Boolean=false) {
        CollectionIncidentReadAccess.require(scope,write).requireOrg(orgId)
    }
    private fun rows(r: CollectionIncidentDao) = CollectionIncidentRecordDao.find { CollectionIncidentRecords.incidentId eq r.id.value }.sortedBy { it.sequence }.also { rows ->
        if(rows.any {it.orgId!=r.orgId})fail("事故记录所属馆与案卷不一致")
    }
    private fun members(id: String) = CollectionIncidentItemDao.find { CollectionIncidentItems.incidentId eq id }.toList().also { rows ->
        val owner=CollectionIncidentDao[id].orgId
        if(rows.any {it.orgId!=owner})fail("事故明细所属馆与案卷不一致")
    }
    private fun lockCollections(ids: Collection<String>) {
        ids.sorted().forEach { id -> CollectionDao.find { Collections.id eq id }.forUpdate().singleOrNull() ?: fail("藏品不存在") }
    }
    /** 先锁受影响实物，再取新鲜案卷行；与原件操作的安全核验共用同一实物锁。 */
    private fun locked(id: String, additionalIds: Set<String> = emptySet()): CollectionIncidentDao {
        val metadata = CollectionIncidents.selectAll().where { CollectionIncidents.id eq id }.singleOrNull() ?: fail("事故案卷不存在")
        access(metadata[CollectionIncidents.orgId], true)
        lockCollections(members(id).map { it.collectionId }.toSet() + additionalIds)
        return CollectionIncidentDao.find { CollectionIncidents.id eq id }.forUpdate().single()
    }
    private inline fun <reified T> value(row: CollectionIncidentRecordDao): T = JsonUtils.fromJson(row.content, T::class.java)!!
    private fun facts(r: CollectionIncidentDao) = rows(r).lastOrNull { it.kind == EnumIncidentRecordKind.FACTS } ?: fail("缺少事故事实原件")
    private fun reports(r: CollectionIncidentDao, f: CollectionIncidentRecordDao) = rows(r).filter { it.kind == EnumIncidentRecordKind.REPORT && value<IncidentReportVo>(it).factsId == f.id.value }
    private fun handling(r: CollectionIncidentDao, f: CollectionIncidentRecordDao) = rows(r).lastOrNull { it.kind == EnumIncidentRecordKind.HANDLING && value<IncidentHandlingVo>(it).factsId == f.id.value } ?: fail("请先登记本版事实的实际核查处理结果")
    private fun verify(r: CollectionIncidentDao, row: CollectionIncidentRecordDao) = archiveEvidence.verify(r, row)
    private fun append(r: CollectionIncidentDao, kind: EnumIncidentRecordKind, content: Any, files: String?): CollectionIncidentRecordDao {
        val json = json(content)
        rows(r).lastOrNull { it.kind == kind && it.content == json }?.let { verify(r, it); return it }
        r.sequence += 1
        val row = CollectionIncidentRecordDao.new { orgId = r.orgId; incidentId = r.id.value; sequence = r.sequence; this.kind = kind; this.content = json }
        archives.seal(r.orgId, CollectionConst.BIZ_INCIDENT, r.id.value, "${kind}_${row.id.value}", content, files)
        return row
    }
    fun create(v: IncidentFactsVo): String {
        val orgId = scope.currentOrgId(); access(orgId, true); lockCollections(v.items.map { it.collectionId })
        val snapshot = prepareFacts(orgId, v, null)
        val r = CollectionIncidentDao.new { this.orgId = orgId; title = v.title }
        saveFacts(r, snapshot)
        return r.id.value
    }
    fun amend(id: String, v: IncidentFactsVo, expectedFactsId: String): String {
        val r = locked(id, v.items.map { it.collectionId }.toSet())
        if(facts(r).id.value != expectedFactsId) fail("事故事实版本已经变化，请重新核实后订正")
        val old = value<IncidentFactsSnapshot>(facts(r))
        if (old.facts == v) return facts(r).id.value.also { verify(r, facts(r)) }
        val snapshot = prepareFacts(r.orgId, v, old)
        return saveFacts(r, snapshot)
    }
    fun linkSource(id:String, expectedFactsId:String, source:IncidentSourceVo, reason:String, fileIds:String):String {
        val r=locked(id)
        val current=value<IncidentFactsSnapshot>(facts(r))
        if(current.facts.source!=null)fail("本案已经关联实际来源，不能替换历史关联")
        return amend(id,current.facts.copy(source=source,replacementReason=reason,replacementFileIds=fileIds),expectedFactsId)
    }
    private fun prepareFacts(orgId: String, v: IncidentFactsVo, old: IncidentFactsSnapshot?): IncidentFactsSnapshot {
        if (v.title.isBlank() || v.title.length > 500 || listOf(v.location, v.description, v.immediateMeasures).any(String::isBlank) || v.items.isEmpty() || v.items.map { it.collectionId }.distinct().size != v.items.size || v.items.any { it.findings.isBlank() }) fail("请登记已知的事故时间、地点、受影响藏品、实际发现和即时措施")
        if (v.discoveredAt.isAfter(LocalDateTime.now()) || v.occurredAt?.isAfter(v.discoveredAt) == true) fail("请据实填写发现时间；不明的发生时间可留空")
        users.requireMuseumMember(v.reporterUserId, orgId)
        if (old != null) {
            if (!v.relatedRelocationIds.containsAll(old.facts.relatedRelocationIds)) fail("已关联的应急搬移原件不能从后续事实版本删除")
            if (!v.relatedWorkIds.containsAll(old.facts.relatedWorkIds)) fail("已关联的现场防护原件不能从后续事实版本删除")
            if (!v.relatedRestorationPlanIds.containsAll(old.facts.relatedRestorationPlanIds)) fail("已关联的修复实际停止原件不能从后续事实版本删除")
            if (v.replacementReason.isNullOrBlank() || v.replacementFileIds.isNullOrBlank()) fail("补充或订正事实须追加原因和原件，旧版永久保留")
            if ((old.facts.source != null && v.source != old.facts.source) || !v.items.map { it.collectionId }.containsAll(old.items.map { it.item.collectionId })) fail("不能替换已关联事故来源或删除已登记受影响藏品；误报应在调查处理中说明")
            old.items.forEach { before ->
                val item = v.items.single { it.collectionId == before.item.collectionId }
                if ((before.item.damaged && !item.damaged) || (before.item.major && !item.major) || (before.item.security != EnumIncidentSecurity.NONE && item.security != before.item.security)) fail("既有损伤、重大或失窃丢失事实不得被覆盖；调查结论另行留存")
            }
        }
        resolutionGuards.forEach { it.requireFactsEvidence(orgId, v) }
        val items = v.items.map { item ->
            val c = CollectionDao.findById(item.collectionId) ?: fail("藏品不存在")
            if (c.orgId != orgId) fail("受影响藏品须属于本馆；跨馆旧案应在原馆保留原件继续调查")
            IncidentFactItemSnapshot(item, CollectionCatalogMapper.fromCollection(c.toModel()))
        }
        return IncidentFactsSnapshot(v, items, v.source?.let { sources.snapshot(orgId, it, v.items) })
    }
    private fun saveFacts(r: CollectionIncidentDao, v: IncidentFactsSnapshot): String {
        val row = append(r, EnumIncidentRecordKind.FACTS, v, (listOfNotNull(v.facts.evidenceFileIds, v.facts.replacementFileIds) + v.facts.items.mapNotNull { it.imageFileIds }).joinToString(","))
        val old = members(r.id.value).associateBy { it.collectionId }
        v.items.forEach { i -> (old[i.item.collectionId] ?: CollectionIncidentItemDao.new { orgId = r.orgId; incidentId = r.id.value; collectionId = i.item.collectionId }).restricted = true }
        r.title = v.facts.title; r.closed = false
        return row.id.value
    }
    /** 保留历史已确定的高层级义务，后续主档降级不能抹去事故当时的报告义务。 */
    private fun requirements(r: CollectionIncidentDao): List<IncidentReportRequirement> = rows(r).filter { it.kind == EnumIncidentRecordKind.FACTS }.flatMap { row ->
        sources.reportingItems(value<IncidentFactsSnapshot>(row)).flatMap { s -> buildList {
            fun add(target: EnumIncidentTarget, kind: EnumIncidentReportKind = EnumIncidentReportKind.INCIDENT) { add(IncidentReportRequirement(s.item.collectionId, target, kind)) }
            add(EnumIncidentTarget.KEEPER); add(EnumIncidentTarget.DIRECTOR)
            if (s.item.major) add(EnumIncidentTarget.COMPETENT)
            if (s.item.damaged) {
                if (CollectionNaturePolicy.usesRelicRules(s.catalog.nature) && s.catalog.level == EnumRelicLevel.FIRST) add(EnumIncidentTarget.NATIONAL)
                else if (CollectionNaturePolicy.usesRelicRules(s.catalog.nature) && s.catalog.level !in setOf(null, EnumRelicLevel.UNRATED)) { add(EnumIncidentTarget.PROVINCE); add(EnumIncidentTarget.NATIONAL, EnumIncidentReportKind.RESULT_FILING) }
                else if (s.catalog.nature == EnumCollectionNature.OTHER) add(EnumIncidentTarget.COMPETENT)
            }
            if (s.item.security != EnumIncidentSecurity.NONE) { add(EnumIncidentTarget.POLICE); add(EnumIncidentTarget.COMPETENT) }
        } }
    }.distinct()
    fun report(id: String, v: IncidentReportVo): String {
        val r = locked(id); if (r.closed) fail("已归档案卷不能覆盖或追加旧版报告；新发现须先追加事实版本")
        val f = facts(r); verify(r, f)
        val snapshot = value<IncidentFactsSnapshot>(f)
        if (v.factsId != f.id.value || v.collectionIds.isEmpty() || !snapshot.items.map { it.item.collectionId }.containsAll(v.collectionIds)) fail("报告须明确覆盖本版事实中的实际藏品")
        val discovered = rows(r).filter { it.kind == EnumIncidentRecordKind.FACTS }.minOf { value<IncidentFactsSnapshot>(it).facts.discoveredAt }
        if (v.reportedAt.isBefore(discovered) || v.reportedAt.isAfter(LocalDateTime.now()) || listOf(v.authorityName, v.reportNo, v.description, v.fileIds).any(String::isBlank)) fail("请登记实际报告或报案的接收机关、时间、编号、内容和送达原件")
        if (v.target in setOf(EnumIncidentTarget.KEEPER, EnumIncidentTarget.DIRECTOR)) {
            val recipient = v.recipientUserId?.takeIf(String::isNotBlank) ?: fail("馆内报告须记录实际接收人员")
            users.requireMuseumMember(recipient, r.orgId)
            users.requireRole(recipient, r.orgId, if (v.target == EnumIncidentTarget.KEEPER) CollectionConst.ROLE_STORAGE_HEAD else CollectionConst.ROLE_DIRECTOR)
        }
        if (v.kind == EnumIncidentReportKind.RESULT_FILING) {
            val h = handling(r, f); val result = value<IncidentHandlingVo>(h); verify(r, h)
            if (v.target != EnumIncidentTarget.NATIONAL || v.handlingId != h.id.value || v.reportedAt.isBefore(result.completedAt) || v.collectionIds.any { cid -> requirements(r).none { it.collectionId == cid && it.kind == EnumIncidentReportKind.RESULT_FILING } }) fail("结果备案须关联本次实际核查处理结果，在处理后向国务院文物行政部门报送适用藏品")
        } else if (v.handlingId != null) fail("情况报告不应冒用核查处理结果备案关联")
        return append(r, EnumIncidentRecordKind.REPORT, v, v.fileIds).id.value
    }
    private fun requireReports(r: CollectionIncidentDao, f: CollectionIncidentRecordDao, at: LocalDateTime, includeFiling: Boolean, handlingId: String? = null) {
        val actual = reports(r, f).onEach { verify(r, it) }.map { value<IncidentReportVo>(it) }
        requirements(r).filter { includeFiling || it.kind != EnumIncidentReportKind.RESULT_FILING }.forEach { req ->
            if (actual.none { it.target == req.target && it.kind == req.kind && req.collectionId in it.collectionIds && !it.reportedAt.isAfter(at) && (req.kind != EnumIncidentReportKind.RESULT_FILING || it.handlingId == handlingId) }) fail("尚缺实际报告原件：${req.collectionId} / ${req.target} / ${req.kind}")
        }
    }
    fun recordHandling(id: String, v: IncidentHandlingVo): String {
        val r = locked(id); if (r.closed) fail("已归档处理结果不能覆盖；新发现须追加事实版本")
        val f = facts(r); verify(r, f); val snapshot = value<IncidentFactsSnapshot>(f)
        if (v.factsId != f.id.value || v.completedAt.isBefore(snapshot.facts.discoveredAt) || v.completedAt.isAfter(LocalDateTime.now()) || listOf(v.cause, v.actions, v.investigationFileIds, v.archiveIndex, v.signedHandlingFileIds).any(String::isBlank)) fail("请记录本版事实的实际原因调查、处理、档案索引和签署原件")
        if (v.items.size != snapshot.items.size || v.items.map { it.collectionId }.toSet() != snapshot.items.map { it.item.collectionId }.toSet()) fail("处理结果须逐件覆盖全部受影响藏品")
        resolutionGuards.forEach { it.requireHandlingEvidence(snapshot.facts, v.completedAt) }
        if (snapshot.items.any { it.catalog.nature == null || (CollectionNaturePolicy.usesRelicRules(it.catalog.nature) && it.catalog.level in setOf(null, EnumRelicLevel.UNRATED)) }) fail("可先据实首报；完成处理前须依法核实藏品性质级别并追加有依据的事实版本")
        users.requireMuseumMember(v.investigatorUserId, r.orgId); heads(r.orgId, v.keeperUserId, v.leaderUserId)
        v.items.forEach { item ->
            if (listOf(item.condition, item.actualLocation, item.protectiveMeasures, item.followUp, item.fileIds).any(String::isBlank)) fail("逐件记录实际去向、现状、防护、后续安排和原件；未找回位置可据实填写不明")
            if (item.disposition == EnumIncidentDisposition.PRESENT_SAFE && (!item.authenticityConfirmed || item.custodianUserId.isNullOrBlank())) fail("确认实物安全须核实真实性及实际保管责任人员")
            item.custodianUserId?.let { users.requireMuseumMember(it, r.orgId) }
        }
        requireReports(r, f, v.completedAt, false)
        val actual = reports(r, f).associate { it.id.value to value<IncidentReportVo>(it) }
        v.authorityResults.forEach { a ->
            if (a.target in setOf(EnumIncidentTarget.KEEPER, EnumIncidentTarget.DIRECTOR) || a.collectionIds.isEmpty() || !v.items.map { it.collectionId }.containsAll(a.collectionIds) || a.reportIds.isEmpty() || listOf(a.authorityName, a.result, a.fileIds).any(String::isBlank) || a.completedAt.isAfter(v.completedAt)) fail("行政或公安核查处理须有实际机关、对应藏品、原报告关联和处理结果原件")
            val related = a.reportIds.map { actual[it] ?: fail("处理依据的实际报告不属于本版案卷") }
            if (related.any { it.target != a.target || it.kind != EnumIncidentReportKind.INCIDENT || it.reportedAt.isAfter(a.completedAt) } || !related.flatMap { it.collectionIds }.containsAll(a.collectionIds)) fail("核查处理结果与前期报告机关、时间或藏品范围不一致")
        }
        requirements(r).filter { it.kind == EnumIncidentReportKind.INCIDENT && it.target !in setOf(EnumIncidentTarget.KEEPER, EnumIncidentTarget.DIRECTOR) }.forEach { req ->
            if (v.authorityResults.none { it.target == req.target && req.collectionId in it.collectionIds }) fail("尚缺对应行政或公安核查处理结果：${req.collectionId} / ${req.target}")
        }
        rows(r).lastOrNull { it.kind == EnumIncidentRecordKind.HANDLING && value<IncidentHandlingVo>(it).factsId == f.id.value }?.let { old ->
            if (value<IncidentHandlingVo>(old) == v) { verify(r, old); return old.id.value }
            if (v.replacementReason.isNullOrBlank() || v.replacementFileIds.isNullOrBlank() || v.completedAt.isBefore(value<IncidentHandlingVo>(old).completedAt)) fail("处理订正须追加原因、原件和实际时间；原处理及结果备案不能覆盖")
        }
        return append(r, EnumIncidentRecordKind.HANDLING, v, (listOfNotNull(v.investigationFileIds, v.signedHandlingFileIds, v.replacementFileIds) + v.items.map { it.fileIds } + v.authorityResults.map { it.fileIds }).joinToString(",")).id.value
    }
    private fun heads(orgId: String, keeper: String, leader: String) {
        if (keeper == leader) fail("保管负责人和主要负责人须分别核实")
        users.requireMuseumMember(keeper, orgId); users.requireMuseumMember(leader, orgId)
        users.requireRole(keeper, orgId, CollectionConst.ROLE_STORAGE_HEAD); users.requireRole(leader, orgId, CollectionConst.ROLE_DIRECTOR)
    }
    private fun basis(r: CollectionIncidentDao) = digest(json(rows(r).filter { it.kind != EnumIncidentRecordKind.CONFIRMATION }.map { mapOf("id" to it.id.value, "kind" to it.kind, "content" to it.content) }))
    fun confirm(id: String, v: IncidentConfirmationVo): String {
        val r = locked(id); val f = facts(r); val h = handling(r, f); val result = value<IncidentHandlingVo>(h)
        rows(r).forEach { verify(r, it) }; heads(r.orgId, result.keeperUserId, result.leaderUserId)
        if (v.basisRevision != basis(r) || v.signedAt.isBefore(result.completedAt) || v.signedAt.isAfter(LocalDateTime.now()) || v.opinion.isBlank() || v.fileIds.isBlank()) fail("须核对当前完整案卷并填写本人意见、实际签署时间和原件")
        requireReports(r, f, v.signedAt, true, h.id.value)
        val actor = SecurityUtils.getUserDetail().id
        val role = when (actor) { result.keeperUserId -> "KEEPER"; result.leaderUserId -> "DIRECTOR"; else -> fail("仅本案指定责任人员本人可签署；管理员不能代办本人确认") }
        val old = rows(r).filter { it.kind == EnumIncidentRecordKind.CONFIRMATION }.map { value<IncidentConfirmationSnapshot>(it) }
        old.lastOrNull { it.actorUserId == actor && it.request == v }?.let { return r.id.value }
        if (r.closed) fail("本版案卷已共同签署归档，不能覆盖")
        if (role == "DIRECTOR") {
            val keeper = old.lastOrNull { it.role == "KEEPER" && it.actorUserId == result.keeperUserId && it.request.basisRevision == v.basisRevision } ?: fail("保管负责人须先本人确认当前版完整案卷")
            if (v.signedAt.isBefore(keeper.request.signedAt)) fail("主要负责人签署应在本版保管负责人确认之后")
            result.items.filter { it.disposition == EnumIncidentDisposition.PRESENT_SAFE }.forEach { item -> resolutionGuards.forEach { it.requireSafeResolution(item.collectionId) } }
        }
        append(r, EnumIncidentRecordKind.CONFIRMATION, IncidentConfirmationSnapshot(v, actor, role, rows(r).filter { it.kind != EnumIncidentRecordKind.CONFIRMATION }.map { it.id.value }), v.fileIds)
        if (role == "DIRECTOR") {
            r.closed = true
            members(id).forEach { row -> row.restricted = result.items.single { it.collectionId == row.collectionId }.disposition != EnumIncidentDisposition.PRESENT_SAFE }
        }
        return id
    }
    /** 来源服务已授权；只返回解限证明，不扩大事故文件查阅权限。不代改来源状态。待办会将失效原件转成核查事项。 */
    @Transactional(noRollbackFor = [BadRequestException::class])
    fun requireClearance(
        id: String, orgId: String, collectionId: String, source: IncidentSourceVo, at: LocalDateTime,
        keeperUserId: String? = null, leaderUserId: String? = null,
        requiredReports: List<IncidentReportRequirement> = emptyList(),
    ): Map<String, String> {
        val proof=verifyClearance(id,orgId,collectionId,source,at,keeperUserId,leaderUserId,requiredReports)
        resolutionGuards.forEach { it.requireSafeResolution(collectionId) }
        return proof
    }
    /** 已保存解除证明由来源永久原件保护；重验旧证明不能要求后来合法开始的作业重新停止。 */
    @Transactional(noRollbackFor = [BadRequestException::class])
    internal fun requireStoredClearance(
        id:String,orgId:String,collectionId:String,source:IncidentSourceVo,at:LocalDateTime,
        recordedProof:Map<String,String>,keeperUserId:String?=null,leaderUserId:String?=null,
        requiredReports:List<IncidentReportRequirement> = emptyList(),
    ):Map<String,String> {
        val proof=verifyClearance(id,orgId,collectionId,source,at,keeperUserId,leaderUserId,requiredReports)
        if(proof!=recordedProof)fail("已保存的事故解除依据已变化，须重新核实")
        return proof
    }
    private fun verifyClearance(
        id:String,orgId:String,collectionId:String,source:IncidentSourceVo,at:LocalDateTime,
        keeperUserId:String?,leaderUserId:String?,requiredReports:List<IncidentReportRequirement>,
    ):Map<String,String> {
        lockCollections(setOf(collectionId))
        val r = CollectionIncidentDao.find { CollectionIncidents.id eq id }.forUpdate().singleOrNull() ?: fail("事故案卷不存在")
        if (r.orgId != orgId || !r.closed) fail("须关联本馆已实际共同签署归档的事故处理")
        val f = facts(r); val snapshot = value<IncidentFactsSnapshot>(f); val h = handling(r, f); val result = value<IncidentHandlingVo>(h)
        if ((keeperUserId != null && keeperUserId != result.keeperUserId) || (leaderUserId != null && leaderUserId != result.leaderUserId)) fail("事故案卷本人确认须与来源业务的保管负责人和主要负责人一致")
        if (snapshot.facts.source != source || result.items.none { it.collectionId == collectionId && it.disposition == EnumIncidentDisposition.PRESENT_SAFE && it.authenticityConfirmed }) fail("事故案卷未核实本来源本件实物已恢复安全，归档本身不能代替")
        if(sources.snapshot(orgId,source,snapshot.facts.items)!=snapshot.sourceEvidence)fail("来源异常事实已经变化，须重新核实事故处理依据")
        rows(r).forEach { verify(r, it) }
        val director = rows(r).lastOrNull { it.kind == EnumIncidentRecordKind.CONFIRMATION && value<IncidentConfirmationSnapshot>(it).role == "DIRECTOR" } ?: fail("缺少主要负责人本人确认")
        val approval = value<IncidentConfirmationSnapshot>(director)
        if (approval.actorUserId != result.leaderUserId || approval.request.basisRevision != basis(r) || approval.request.signedAt.isAfter(at)) fail("事故确认已失效或晚于本次实际解限")
        val actualReports = reports(r, f).map { value<IncidentReportVo>(it) }
        requiredReports.forEach { req ->
            if (req.collectionId != collectionId || actualReports.none { it.target == req.target && it.kind == req.kind && collectionId in it.collectionIds && !it.reportedAt.isAfter(at) && (req.kind != EnumIncidentReportKind.RESULT_FILING || it.handlingId == h.id.value) }) fail("事故案卷尚缺来源业务要求的实际报告：${req.target} / ${req.kind}")
            if (req.kind == EnumIncidentReportKind.INCIDENT && req.target !in setOf(EnumIncidentTarget.KEEPER, EnumIncidentTarget.DIRECTOR) && result.authorityResults.none { it.target == req.target && collectionId in it.collectionIds }) fail("事故案卷尚缺来源业务要求的实际核查处理结果：${req.target}")
        }
        if (CollectionIncidentItemDao.find { (CollectionIncidentItems.collectionId eq collectionId) and (CollectionIncidentItems.restricted eq true) }.any()) fail("该藏品仍有其他事故或新事实的安全限制")
        return mapOf("incidentId" to id, "factsId" to f.id.value, "handlingId" to h.id.value, "confirmationId" to director.id.value, "basisRevision" to basis(r), "confirmedAt" to approval.request.signedAt.toString())
    }
    /** 读取旧归还案卷时按当次主要负责人签署前的记录重建依据；后续合法追加事故事实不抹掉旧档案。 */
    internal fun archivedClearanceProof(
        id:String,orgId:String,collectionId:String,source:IncidentSourceVo,at:LocalDateTime,
        keeperUserId:String,leaderUserId:String,confirmationId:String,
        requiredReports:List<IncidentReportRequirement>,
    ):Map<String,String> = archiveEvidence.clearanceProof(
        id,orgId,collectionId,source,at,keeperUserId,leaderUserId,confirmationId,requiredReports,
    )
    /** 来源业务已做馆级授权；只返回仍受限件，不暴露事故原件。 */
    fun restrictedCollectionIds(orgId: String): Set<String> = CollectionIncidentItemDao.find {
        (CollectionIncidentItems.orgId eq orgId) and (CollectionIncidentItems.restricted eq true)
    }.map { it.collectionId }.toSet()

    /** 现场防护只获取本件全部当前限制的事实基线，不解除或代办任何事故。 */
    fun requireWorkBasis(orgId: String, collectionId: String, incidentIds: Set<String>, verifySource: Boolean = true): Map<String, String> {
        access(orgId, true)
        lockCollections(setOf(collectionId))
        val actual = CollectionIncidentItemDao.find { (CollectionIncidentItems.collectionId eq collectionId) and (CollectionIncidentItems.restricted eq true) }.toList()
        if (actual.isEmpty() || actual.any { it.orgId != orgId } || actual.map { it.incidentId }.toSet() != incidentIds) fail("现场防护方案须核对本件全部当前事故限制，不能漏案或沿用已解除的案卷")
        return incidentIds.sorted().associateWith { id ->
            val r = CollectionIncidentDao.find { CollectionIncidents.id eq id }.forUpdate().single()
            if (r.orgId != orgId) fail("事故案卷所属馆不一致")
            val row = facts(r); verify(r, row)
            val snapshot = value<IncidentFactsSnapshot>(row)
            snapshot.facts.source?.takeIf { verifySource }?.let { if (sources.snapshot(orgId, it, snapshot.facts.items) != snapshot.sourceEvidence) fail("来源异常事实已变化，请先追加核实事故版本") }
            row.id.value
        }
    }
    /** 已由本馆来源服务授权的关联摘要；不返回事故原件文件，不代办结案或解限。待办会将失效原件转成核查事项。 */
    @Transactional(noRollbackFor = [BadRequestException::class])
    internal fun referenceSummary(orgId:String,id:String):Map<String,Any?> {
        val r=CollectionIncidentDao.findById(id) ?: fail("关联实际事故不存在")
        if(r.orgId!=orgId)fail("不能关联外馆实际事故")
        val all=rows(r);all.forEach {verify(r,it)}
        val f=facts(r);val current=value<IncidentFactsSnapshot>(f)
        if(r.closed) {
            val h=handling(r,f);val result=value<IncidentHandlingVo>(h);val b=basis(r)
            val confirmed=all.filter {it.kind==EnumIncidentRecordKind.CONFIRMATION}.map {value<IncidentConfirmationSnapshot>(it)}.filter {it.request.basisRevision==b}
            if(confirmed.none {it.role=="KEEPER" && it.actorUserId==result.keeperUserId} || confirmed.none {it.role=="DIRECTOR" && it.actorUserId==result.leaderUserId})fail("关联事故已结案标记缺少本版共同确认原件")
            requireReports(r,f,LocalDateTime.now(),true,h.id.value)
        }
        return mapOf("id" to id,"title" to r.title,"closed" to r.closed,"basisRevision" to if(r.closed)basis(r) else null,"factsId" to f.id.value,"discoveredAt" to current.facts.discoveredAt,"occurredAt" to current.facts.occurredAt)
    }
    fun detail(id: String): Map<String, Any?> {
        val access=CollectionIncidentReadAccess.require(scope)
        val r=CollectionIncidentDao.findById(id) ?: fail("事故案卷不存在")
        access.require(r.orgId,CollectionConst.BIZ_INCIDENT,id)
        val all=rows(r); all.forEach {archiveEvidence.verify(r,it,verifyContent=false)}; val f=facts(r)
        val h = all.lastOrNull { it.kind == EnumIncidentRecordKind.HANDLING && value<IncidentHandlingVo>(it).factsId == f.id.value }
        val currentReports = reports(r, f).map { value<IncidentReportVo>(it) }
        return summary(r) + mapOf("basisRevision" to basis(r), "factsId" to f.id.value, "facts" to value<IncidentFactsSnapshot>(f), "handlingId" to h?.id?.value, "handling" to h?.let { value<IncidentHandlingVo>(it) }, "requirements" to requirements(r).map { req -> mapOf("collectionId" to req.collectionId, "target" to req.target, "kind" to req.kind, "recorded" to currentReports.any { it.target == req.target && it.kind == req.kind && req.collectionId in it.collectionIds && (req.kind != EnumIncidentReportKind.RESULT_FILING || it.handlingId == h?.id?.value) }) }, "items" to members(id).map { mapOf("collectionId" to it.collectionId, "restricted" to it.restricted) }, "records" to all.map { mapOf("id" to it.id.value, "kind" to it.kind, "sequence" to it.sequence, "content" to JsonUtils.mapper.readTree(it.content)) }, "archives" to archives.readForDisplay(CollectionConst.BIZ_INCIDENT,id,r.orgId).archives)
    }
    fun requireArchivedOriginal(id: String, archiveId: String, fileId: String) {
        if (id.isBlank() || id.length > 100) fail("事故案卷标识不正确")
        CollectionCaseArchivedOriginal.require(detail(id), archiveId, fileId)
    }
    fun page(request: HttpServletRequest): GenericPage<Map<String, Any?>> {
        val access=CollectionIncidentReadAccess.require(scope)
        val q=CollectionIncidents.selectAll().where {CollectionIncidents.orgId inList access.orgIds}
        request.getParameter("keyword")?.takeIf(String::isNotBlank)?.let { q.andWhere { CollectionIncidents.title like "%$it%" } }
        q.orderBy(CollectionIncidents.createTime to SortOrder.DESC)
        val page = findPage(q, GenericPage<CollectionIncidentDao>().build(request))
        return GenericPage<Map<String, Any?>>().build(request).also { it.total = page.total; it.list = page.data?.map { row -> access.require(row.orgId,CollectionConst.BIZ_INCIDENT,row.id.value); summary(row) } }
    }
    private fun summary(r: CollectionIncidentDao): Map<String, Any?> = mapOf("id" to r.id.value, "title" to r.title, "closed" to r.closed, "createTime" to r.createTime)
    private fun json(v: Any) = JsonUtils.toJson(v)!!
    private fun digest(v: String) = MessageDigest.getInstance("SHA-256").digest(v.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun fail(message: String): Nothing = throw BadRequestException(message)
}
