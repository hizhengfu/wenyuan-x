package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.EnumContentStatus
import io.hizhengfu.cloud.common.domain.EnumRole
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.IdGenerate
import io.hizhengfu.cloud.common.utils.SecurityUtils
import io.hizhengfu.cloud.sys.system.dao.Users
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.dao.entityCache
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.select
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@Component
@Transactional
class LeadershipDepartureService(
    private val scope: CollectionScopeResolver,
    private val directory: CollectionActorDirectory,
    private val users: CollectionResponsibleUserService,
    private val archives: CollectionPermanentArchiveService,
) {
    private val evidence = LeadershipDepartureEvidence(archives)
    private fun fail(message: String): Nothing = throw BadRequestException(message)
    private fun now() = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS)
    private fun actor() = directory.requireActive(SecurityUtils.getUserDetail().id)
    private fun org(id: String, user: SecurityUser, write: Boolean, lock: Boolean = true): CollectionOrgDao {
        if (!scope.isMulti() && id != scope.currentOrgId()) fail("负责人离任交接不属于当前配置收藏单位")
        val e = (if (write && lock) CollectionOrgDao.find { CollectionOrgs.id eq id }.forUpdate().singleOrNull() else CollectionOrgDao.findById(id)) ?: fail("收藏单位不存在")
        if (EnumRole.ADMIN.value !in user.roles && user.permissions.none { it in setOf(CollectionConst.ADMIN, CollectionConst.ORG_MANAGE, CollectionConst.STORAGE) } && !(user.roleCodes.any { it in setOf(CollectionConst.ROLE_DIRECTOR, CollectionConst.ROLE_STORAGE_HEAD) } && scope.isMuseumMember(user, id))) fail("当前账号缺少负责人离任交接办理权限")
        OrgScopedReadPolicy.requireVisible(if (write) scope.currentWritableOrgIds(user) else scope.currentReadableOrgIds(user, CollectionConst.CROSS_ORG), id, "负责人离任交接")
        if (write && (e.status != EnumContentStatus.NORMAL || e.orgStatus != EnumOrgStatus.ENABLED)) fail("收藏单位已停用，不能登记新的交接确认")
        return e
    }
    private fun writeContext(orgId: String): Pair<CollectionOrgDao, SecurityUser> {
        val initial = actor()
        val museum = org(orgId, initial, true)
        val current = actor()
        if (current.id != initial.id) fail("等待离任交接办理期间认证人员已变化，请重新核对本人身份")
        org(orgId, current, true, lock = false)
        return museum to current
    }
    fun get(orgId: String): Map<String, Any?> {
        val user = actor(); org(orgId, user, false)
        return try {
            val state = evidence.read(orgId)
            mapOf("items" to state.records.map { item(orgId, it, state, user) }, "archives" to archives.listForAuthorizedOrg(LeadershipDepartureEvidence.BIZ, orgId, orgId), "evidenceErrors" to emptyList<String>())
        } catch (ex: BadRequestException) { mapOf("items" to emptyList<Any>(), "evidenceErrors" to listOf(ex.message ?: "离任交接原件待核对")) }
    }
    fun requireArchivedOriginal(orgId: String, archiveId: String, fileId: String) {
        org(orgId, actor(), false)
        val state = evidence.read(orgId)
        val archive = archives.listForAuthorizedOrg(LeadershipDepartureEvidence.BIZ, orgId, orgId)
            .singleOrNull { it["id"] == archiveId } ?: fail("所选负责人离任交接原件不属于本馆")
        val phase = archive["phase"] as? String ?: fail("负责人离任交接原件阶段无效")
        val record = state.records.singleOrNull { phase == "CASE_${it.id}" ||
            phase in setOf("PK_${it.id}", "PS_${it.id}", "HK_${it.id}", "HS_${it.id}") }
            ?: fail("所选原件不属于已核验的负责人离任交接案")
        if (phase.startsWith("P") && phase !in state.preparations ||
            phase.startsWith("H") && phase !in state.completions) fail("所选离任交接确认阶段尚未形成")
        if (phase.startsWith("H")) completion(orgId, record, state)
        val files = (archive["files"] as? List<*>)?.filterIsInstance<Map<*, *>>().orEmpty()
        if (files.none { it["fileId"] == fileId }) fail("该文件不是所选负责人离任交接永久原件")
    }
    fun register(orgId: String, input: LeadershipDepartureRequest): String {
        val (museum, user) = writeContext(orgId); val state = evidence.read(orgId)
        // 接受原账号名或已有身份编号，供停用账号补录；只精确匹配，不开放历史人员列表。
        val leaving = Users.select(Users.id, Users.name).where { (Users.id eq input.leavingUserId) or (Users.loginName eq input.leavingUserId) }.singleOrNull() ?: fail("离任人员原账号不存在或标识有歧义，请核对原始身份资料")
        val v = input.copy(leavingUserId = leaving[Users.id].value)
        state.records.lastOrNull { state.latest(it.id) && it.request == v && it.registeredBy == user.id }?.let { return it.id }
        if (v.previousId != null) {
            val previous = state.records.singleOrNull { it.id == v.previousId } ?: fail("被更正的离任交接案不存在")
            if (!state.latest(previous.id) || evidence.boundBatchIds(orgId, previous.id).isNotEmpty()) fail("旧版本已更新或已开始清点，须保留原清点依据，不能替换交接案")
            if (v.replacementReason.isNullOrBlank()) fail("更正任职或离任依据须说明原因")
        }
        if (state.records.any { state.latest(it.id) && it.id != v.previousId && it.request.leavingUserId == v.leavingUserId && it.request.leavingAt == v.leavingAt && (it.request.post == v.post || it.request.post == EnumLeadershipPost.BOTH || v.post == EnumLeadershipPost.BOTH) }) fail("同次离任已登记，请引用现有案卷更正，不能重复办理")
        if (v.leavingUserId == v.successorUserId || v.keeperUserId in setOf(v.leavingUserId, v.successorUserId)) fail("离任人、接任人及保管核对负责人须分别明确")
        if (v.appointedAt.isAfter(now()) || !v.appointedAt.isBefore(v.leavingAt)) fail("原任职开始时间须真实且早于离任时间")
        if (listOf(v.appointmentFileIds, v.departureFileIds, v.successorAppointmentFileIds, v.responsibilityStatement, v.responsibilityFileIds).any(String::isBlank)) fail("请提供真实任职、离任、接任与完整责任范围说明及原件")
        if (listOf(v.appointmentFileIds, v.departureFileIds, v.successorAppointmentFileIds, v.responsibilityFileIds).any { BusinessAttachmentBindingPlanner.plan("", "", it).isEmpty() }) fail("任职、离任、接任及责任范围各项原件引用不能为空")
        // 只读取历史身份的姓名；停用或撤角不抹除历史任职，但此处不授予任何当前权限。
        val leavingName = leaving[Users.name].takeIf(String::isNotBlank) ?: fail("离任人员历史身份姓名缺失，请核对原始资料")
        users.requireRole(v.keeperUserId, orgId, CollectionConst.ROLE_STORAGE_HEAD)
        users.requireRole(v.successorUserId, orgId, CollectionConst.ROLE_DIRECTOR)
        val successor = directory.requireActive(v.successorUserId)
        val record = LeadershipDepartureRecord(IdGenerate.nextId(), v, leavingName, successor.name, museum.ownership ?: fail("请核实本馆举办主体"), user.id, now())
        seal(orgId, user.id, "CASE_${record.id}", record, evidence.files(v), state) { currentMuseum, _ ->
            if (currentMuseum.ownership != record.ownership) fail("登记期间收藏单位举办主体已变化，请重新核实离任监督要求")
            users.requireRole(v.keeperUserId, orgId, CollectionConst.ROLE_STORAGE_HEAD)
            users.requireRole(v.successorUserId, orgId, CollectionConst.ROLE_DIRECTOR)
        }
        return record.id
    }
    fun confirm(orgId: String, id: String, stage: String, value: CollectionCareSignatureVo): String {
        val (_, user) = writeContext(orgId); val state = evidence.read(orgId)
        val r = state.records.singleOrNull { it.id == id } ?: fail("负责人离任交接案不存在")
        if (!state.latest(id)) fail("该交接案已更正，请办理最新版本")
        if (stage !in setOf("PK", "PS", "HK", "HS")) fail("未知的离任交接确认阶段")
        signer(orgId, r, user, stage.endsWith("K"))
        if (value.opinion.isBlank() || BusinessAttachmentBindingPlanner.plan("", "", value.fileIds).isEmpty()) fail("请填写本人核对意见并上传实际签署原件")
        val phase = "${stage}_$id"
        val previous = state.preparations[phase] ?: state.completions[phase]?.signature
        if (previous != null) {
            if (previous.actorUserId == user.id && previous.record == value) { if (stage.startsWith("H")) completion(orgId, r, state); return id }
            fail("本阶段原确认已保留，不能替换")
        }
        if (stage == "PS" && state.preparations["PK_$id"] == null) fail("须先由保管部门负责人核对任职及责任范围依据")
        val signature = CollectionCareSignature(user.id, now(), value)
        val snapshot: Any = if (stage.startsWith("H")) {
            if (!state.prepared(id)) fail("须先完成任职依据核对")
            if (stage == "HS" && state.completions["HK_$id"] == null) fail("须先由保管部门负责人核对清点移交结果")
            val archive = completion(orgId, r, state)
            LeadershipDepartureCompletionReview(archive.id.value, archive.digest, signature)
        } else signature
        seal(orgId, user.id, phase, snapshot, value.fileIds, state) { _, currentUser ->
            signer(orgId, r, currentUser, stage.endsWith("K"))
        }
        return id
    }
    /** 先完成实际归档 SQL，再复查本次办理资格；历史离任人和旧签署不重新授权。 */
    private fun seal(orgId: String, actorId: String, phase: String, snapshot: Any, fileIds: String,
        previous: LeadershipDepartureEvidence.State,
        responsibility: (CollectionOrgDao, SecurityUser) -> Unit) {
        val archiveId = archives.seal(orgId, LeadershipDepartureEvidence.BIZ, orgId, phase, snapshot, fileIds)
        TransactionManager.current().entityCache.flush()
        val archive = CollectionPermanentArchiveDao.find { CollectionPermanentArchives.id eq archiveId }.singleOrNull() ?: fail("本次离任交接归档缺失，请核实实际保存结果")
        archive.refresh(flush = false)
        archives.verifyExisting(archive, orgId, LeadershipDepartureEvidence.BIZ, orgId, phase, snapshot, fileIds)
        // 原任职案及既有签署未被馆锁锁住，归档期间可能被另一事务改写。
        // 重新读取原件内容与引用，比较办理前的完整链；只移除本次合法新增阶段。
        evidence.rows(orgId).forEach { row ->
            row.refresh(flush = false)
            CollectionPermanentArchiveFileDao.find { CollectionPermanentArchiveFiles.archiveId eq row.id.value }
                .forEach { it.refresh(flush = false) }
        }
        val currentEvidence = evidence.read(orgId)
        val retained = LeadershipDepartureEvidence.State(
            currentEvidence.records.filterNot { phase == "CASE_${it.id}" },
            currentEvidence.preparations - phase,
            currentEvidence.completions - phase,
        )
        if (retained != previous) fail("离任交接办理期间原任职或既有确认依据已变化，请重新核对原件")
        if (SecurityUtils.getNullUserDetail()?.id != actorId) fail("离任交接登记期间账号变化，请重新核实")
        val current = directory.requireActive(actorId)
        CollectionOrgDao.findById(orgId)?.refresh(flush = false)
        val museum = org(orgId, current, true)
        responsibility(museum, current)
    }
    private fun signer(orgId: String, r: LeadershipDepartureRecord, user: SecurityUser, keeper: Boolean) {
        val id = if (keeper) r.request.keeperUserId else r.request.successorUserId
        val role = if (keeper) CollectionConst.ROLE_STORAGE_HEAD else CollectionConst.ROLE_DIRECTOR
        if (user.id != id || role !in user.roleCodes || !scope.isMuseumMember(user, orgId)) fail("须由指定且当前仍具备本馆职责的本人确认，管理员不能代签")
        users.requireRole(id, orgId, role)
    }
    private fun completion(orgId: String, r: LeadershipDepartureRecord, state: LeadershipDepartureEvidence.State): CollectionPermanentArchiveDao {
        val id = evidence.activeBatchId(orgId, r.id) ?: fail("须先关联全馆离任清点批次")
        evidence.boundBatchIds(orgId, r.id).forEach { bound ->
            val original = InventoryBatchDao.findById(bound) ?: fail("原清点批次缺失")
            InventoryArchiveEvidence(archives).verify(original)
            if (bound != id && original.completion != null) fail("已签署完成的原清点不能被后续重办批次替代")
        }
        val b = InventoryBatchDao.findById(id) ?: fail("关联清点批次缺失")
        if (b.orgId != orgId || b.plan?.leadershipDepartureId != r.id) fail("关联清点批次与交接案不一致")
        InventoryArchiveEvidence(archives).verify(b)
        if (b.completion == null) fail("须先完成清点签署、实际移交及适用的主管部门监交记录")
        val archive = CollectionPermanentArchiveDao.find { (CollectionPermanentArchives.bizType eq InventoryComplianceService.BIZ) and (CollectionPermanentArchives.bizId eq id) and (CollectionPermanentArchives.phase eq "COMPLETED") }.singleOrNull() ?: fail("缺少清点完成原件")
        state.completions.filterKeys { it.endsWith("_${r.id}") }.values.forEach { if (it.inventoryArchiveId != archive.id.value || it.inventoryArchiveDigest != archive.digest) fail("清点完成原件与双方核对依据不一致") }
        return archive
    }
    private fun item(orgId: String, r: LeadershipDepartureRecord, state: LeadershipDepartureEvidence.State, user: SecurityUser): Map<String, Any?> {
        val bound = evidence.boundBatchIds(orgId, r.id); var error: String? = null
        val activeId = try { evidence.activeBatchId(orgId, r.id) } catch (ex: BadRequestException) { error = ex.message; null }
        val b = activeId?.let { InventoryBatchDao.findById(it) }
        if (bound.isNotEmpty() && b == null && error == null) error = "关联的清点批次缺失"
        bound.forEach { id -> try {
            val original = InventoryBatchDao.findById(id) ?: fail("原清点批次缺失")
            InventoryArchiveEvidence(archives).verify(original)
        } catch (ex: BadRequestException) { error = ex.message } }
        if (b != null) try {
            if (b.orgId != orgId || b.plan?.leadershipDepartureId != r.id) fail("当前清点与交接案绑定不一致")
            InventoryArchiveEvidence(archives).verify(b)
            if (b.completion != null || state.completions.keys.any { it.endsWith("_${r.id}") }) completion(orgId, r, state)
        } catch (ex: BadRequestException) { error = ex.message }
        val finished = state.completions["HS_${r.id}"] != null
        val status = when {
            !state.latest(r.id) -> "SUPERSEDED"
            error != null -> "EVIDENCE_REQUIRED"
            state.preparations["PK_${r.id}"] == null -> "PENDING_KEEPER"
            !state.prepared(r.id) -> "PENDING_SUCCESSOR"
            b == null -> "PENDING_INVENTORY"
            b.completion == null -> "INVENTORY_IN_PROGRESS"
            !finished -> "PENDING_HANDOVER_REVIEW"
            inventoryDepartureStatus(b.plan, b.completion) == EnumInventoryDepartureStatus.COMPLETED_LATE -> "COMPLETED_LATE"
            else -> "COMPLETED"
        }
        val actions = if (error != null || !state.latest(r.id)) emptyList() else listOf("PK", "PS", "HK", "HS").filter { stage ->
            val phase = "${stage}_${r.id}"
            val ready = when (stage) { "PK" -> true; "PS" -> state.preparations["PK_${r.id}"] != null; "HK" -> b?.completion != null && state.prepared(r.id); else -> state.completions["HK_${r.id}"] != null }
            ready && phase !in state.preparations && phase !in state.completions && try { signer(orgId, r, user, stage.endsWith("K")); true } catch (_: BadRequestException) { false }
        }
        return mapOf("record" to r, "status" to status, "evidenceError" to error, "inventoryBatchId" to b?.id?.value, "inventoryBatchName" to b?.batchName,
            "canCorrect" to (state.latest(r.id) && bound.isEmpty()), "actions" to actions,
            "canReplan" to (state.latest(r.id) && error == null && b != null && b.completion == null),
            "inventoryHistory" to bound.map { id -> mapOf("id" to id, "batchName" to InventoryBatchDao.findById(id)?.batchName, "current" to (id == activeId)) },
            "preparations" to state.preparations.filterKeys { it.endsWith("_${r.id}") }, "completionReviews" to state.completions.filterKeys { it.endsWith("_${r.id}") })
    }
}
