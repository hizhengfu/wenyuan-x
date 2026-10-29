package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.EnumRole
import io.hizhengfu.cloud.common.domain.EnumContentStatus
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.IdGenerate
import io.hizhengfu.cloud.common.utils.SecurityUtils
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.dao.entityCache
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import io.hizhengfu.cloud.collection.service.WarehouseStaffingArchiveEvidence.Change

/** 名单按实际情况更新；永久档案独立保留尚未办结的离任核对，不继续授权已离岗人员。 */
@Component
@Transactional
class WarehouseStaffingHandoverService(
    private val scope: CollectionScopeResolver,
    private val directory: CollectionActorDirectory,
    private val users: CollectionResponsibleUserService,
    private val archives: CollectionPermanentArchiveService,
) {
    companion object { const val BIZ = "COLLECTION_STAFFING_HANDOVER" }
    private data class State(val changes: List<Change>, val records: List<WarehouseStaffingHandoverRecord>, val signatures: Map<String, CollectionCareSignature>)
    private fun fail(message: String): Nothing = throw BadRequestException(message)
    private fun now() = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS)
    private fun actor() = directory.requireActive(SecurityUtils.getUserDetail().id)
    private fun warehouse(id: String, actor: SecurityUser, write: Boolean, lock: Boolean = true): WarehouseDao {
        var e = WarehouseDao.findById(id) ?: fail("库房不存在")
        if (!scope.isMulti() && e.orgId != scope.currentOrgId()) fail("岗位交接不属于当前配置收藏单位")
        val manager = EnumRole.ADMIN.value in actor.roles || actor.permissions.any { it in setOf(CollectionConst.ADMIN, CollectionConst.WAREHOUSE_MANAGE) }
        val responsible = actor.roleCodes.any { it in setOf(CollectionConst.ROLE_STORAGE_HEAD, CollectionConst.ROLE_DIRECTOR) } && scope.isMuseumMember(actor, e.orgId)
        if (!manager && !responsible) fail("当前账号缺少库房岗位交接核对权限")
        OrgScopedReadPolicy.requireVisible(if (write) scope.currentWritableOrgIds(actor) else scope.currentReadableOrgIds(actor, CollectionConst.CROSS_ORG), e.orgId, "库房岗位交接")
        if (write && lock) e = CollectionLocationMutation.reference(e.orgId, id, allowRetired = true) ?: fail("库房不存在")
        val museum = CollectionOrgDao.findById(e.orgId) ?: fail("库房所属收藏单位不存在")
        if (write && (museum.status != EnumContentStatus.NORMAL || museum.orgStatus != EnumOrgStatus.ENABLED)) fail("收藏单位已停用，请先核实业务办理资格")
        return e
    }
    private fun writeContext(id: String): Pair<WarehouseDao, SecurityUser> {
        val initial = actor()
        val location = warehouse(id, initial, true)
        val current = actor()
        if (current.id != initial.id) fail("等待岗位交接办理期间认证人员已变化，请重新核对本人身份")
        warehouse(id, current, true, lock = false)
        return location to current
    }
    fun get(id: String): Map<String, Any?> {
        val user = actor(); val e = warehouse(id, user, false)
        return try {
            val s = state(e)
            val assignmentIds = s.changes.flatMap { listOf(it.before.id.value, it.after.id.value) }.toSet()
            mapOf("items" to s.changes.map { item(e, it, s, user) }, "records" to s.records,
                "archives" to archives.listForAuthorizedOrg(BIZ, id, e.orgId), "evidenceErrors" to emptyList<String>(),
                "assignmentArchives" to archives.listForAuthorizedOrg(WarehouseStaffingService.BIZ, id, e.orgId)
                    .filter { it["id"] in assignmentIds },
                "inventoryBatches" to InventoryBatchDao.find { InventoryBatches.orgId eq e.orgId }
                    .filter { it.plan?.purpose == EnumInventoryPurpose.KEEPER_DEPARTURE && it.completion != null && inventoryDepartureScopeErrors(it.plan, it.baseline, it.warehouseId).isEmpty() }
                    .map { mapOf("id" to it.id.value, "batchName" to it.batchName) },
                "historyNotice" to "仅核对已留存的连续任职记录；首次记录之前的任职与交接须另行核实。")
        } catch (ex: BadRequestException) {
            mapOf("items" to emptyList<Any>(), "records" to emptyList<Any>(), "evidenceErrors" to listOf(ex.message ?: "岗位交接原件待核对"))
        }
    }
    fun requireArchivedOriginal(id: String, archiveId: String, fileId: String) {
        val e = warehouse(id, actor(), false)
        val chain = state(e) // Verify the full assignment, handover and signature chain before exposing a retained original.
        val archive = archives.listForAuthorizedOrg(BIZ, id, e.orgId).singleOrNull { it["id"] == archiveId }
            ?: fail("该交接永久档案不属于当前库房")
        val phase = archive["phase"] as? String ?: fail("交接永久档案阶段无效")
        if (chain.records.none { record ->
                phase == "R_${record.id}" ||
                    (phase == "K_${record.id}" && phase in chain.signatures) ||
                    (phase == "L_${record.id}" && phase in chain.signatures)
            }) fail("交接永久档案阶段无效")
        val files = (archive["files"] as? List<*>)?.filterIsInstance<Map<*, *>>().orEmpty()
        if (files.none { it["fileId"] == fileId }) fail("该文件不是所选交接永久档案的原件")
    }
    fun requireAssignmentSourceOriginal(id: String, archiveId: String, fileId: String) {
        val e = warehouse(id, actor(), false)
        val chain = state(e)
        if (chain.changes.none { it.before.id.value == archiveId || it.after.id.value == archiveId })
            fail("所选任职原件不是本库离任交接的前后任职依据")
        val archive = archives.listForAuthorizedOrg(WarehouseStaffingService.BIZ, id, e.orgId)
            .singleOrNull { it["id"] == archiveId && (it["phase"] as? String)?.startsWith("ASSIGNED_") == true }
            ?: fail("所选任职原件不属于当前库房")
        val files = (archive["files"] as? List<*>)?.filterIsInstance<Map<*, *>>().orEmpty()
        if (files.none { it["fileId"] == fileId }) fail("该文件不是所选前后任职原件")
    }
    fun register(id: String, value: WarehouseStaffingHandoverRequest): String {
        val (e, user) = writeContext(id); val s = state(e)
        val change = s.changes.singleOrNull { it.after.id.value == value.assignmentArchiveId && it.userId == value.leavingUserId } ?: fail("所选人员不是该次任职原件中移除的库管")
        val last = s.records.lastOrNull { sameChange(it.request, value) }
        if (last?.request == value && last.registeredBy == user.id) { inventory(change, e.orgId, value); return last.id }
        if (value.previousId != last?.id) fail("交接核对已更新，请重新读取最新记录")
        if (last != null && value.replacementReason.isNullOrBlank()) fail("补充或更正须说明原因，原记录与确认保留")
        if (s.records.any { !sameChange(it.request, value) && it.request.inventoryBatchIds.intersect(value.inventoryBatchIds).isNotEmpty() }) fail("该清点已用于本库另一任职变动，不能复用于下一次离任")
        validateRequest(e, change, value)
        val record = WarehouseStaffingHandoverRecord(IdGenerate.nextId(), value, change.before.id.value, change.before.digest,
            change.after.digest, inventory(change, e.orgId, value).associate { it.id.value to it.digest }, user.id, now())
        seal(e, user.id, "R_${record.id}", record, value.scopeFileIds) { current, _ ->
            validateRequest(current, change, value)
        }
        return record.id
    }
    fun confirm(id: String, recordId: String, role: String, value: CollectionCareSignatureVo): String {
        val (e, user) = writeContext(id); val s = state(e)
        val record = s.records.singleOrNull { it.id == recordId } ?: fail("岗位交接核对记录不存在")
        if (s.records.lastOrNull { sameChange(it.request, record.request) }?.id != recordId) fail("该记录已有后续版本，请核对最新记录")
        val key = when (role) { "keeper" -> "K_$recordId"; "leader" -> "L_$recordId"; else -> fail("未知的确认岗位") }
        requireSigner(e, record.request, user, role)
        inventory(s.changes.single { it.after.id.value == record.request.assignmentArchiveId && it.userId == record.request.leavingUserId }, e.orgId, record.request)
        if (value.opinion.isBlank() || value.fileIds.isBlank()) fail("请填写本人核对意见并上传实际签署原件")
        val old = s.signatures[key]
        if (old != null) {
            if (old.actorUserId == user.id && old.record == value) return recordId
            fail("该岗位已确认，补充或更正须另建后续版本")
        }
        if (role == "leader" && s.signatures["K_$recordId"] == null) fail("须先由保管部门负责人核对确认")
        seal(e, user.id, key, CollectionCareSignature(user.id, now(), value), value.fileIds) { current, currentUser ->
            requireSigner(current, record.request, currentUser, role)
        }
        return recordId
    }
    /** 保留本次原馆和实际任职，实际归档后复验；不重验已经签署的历史人员。 */
    private fun seal(e: WarehouseDao, actorId: String, phase: String, snapshot: Any, fileIds: String,
        responsibility: (WarehouseDao, SecurityUser) -> Unit) {
        val id = e.id.value
        val orgId = e.orgId
        val staffing = e.staffing
        val archiveId = archives.seal(orgId, BIZ, id, phase, snapshot, fileIds)
        TransactionManager.current().entityCache.flush()
        val archive = CollectionPermanentArchiveDao.find { CollectionPermanentArchives.id eq archiveId }.singleOrNull()
            ?: fail("本次岗位交接归档缺失，请核实实际保存结果")
        archive.refresh(flush = false)
        archives.verifyExisting(archive, orgId, BIZ, id, phase, snapshot, fileIds)
        val current = WarehouseDao.find { Warehouses.id eq id }.singleOrNull() ?: fail("岗位交接对应库房已不存在")
        current.refresh(flush = false)
        if (current.orgId != orgId || current.staffing != staffing) fail("岗位交接期间库房归属或实际任职已变化，请重新核实")
        CollectionOrgDao.findById(orgId)?.refresh(flush = false)
        if (SecurityUtils.getNullUserDetail()?.id != actorId) fail("岗位交接期间办理账号变化，请重新核实")
        val currentUser = directory.requireActive(actorId)
        // 原馆的位置锁已在入口取得；末尾只读取当前资格，不再写入锁版本。
        warehouse(id, currentUser, true, lock = false)
        responsibility(current, currentUser)
    }
    private fun sameChange(a: WarehouseStaffingHandoverRequest, b: WarehouseStaffingHandoverRequest) = a.assignmentArchiveId == b.assignmentArchiveId && a.leavingUserId == b.leavingUserId
    private fun validateRequest(e: WarehouseDao, c: Change, v: WarehouseStaffingHandoverRequest) {
        if (v.scopeStatement.isBlank() || v.scopeFileIds.isBlank() || !v.fullResponsibilityScopeConfirmed) fail("须核对完整责任范围并提供签署依据；更正须明确原录入错误及其依据")
        if (v.keeperUserId == v.leaderUserId) fail("两项负责人核对须由不同人员完成")
        val current = e.staffing ?: fail("缺少当前实际任职记录")
        if (v.keeperUserId != current.keeperUserId || v.leaderUserId != current.leaderUserId) fail("请指定现任保管部门负责人和主要负责人")
        users.requireRole(v.keeperUserId, e.orgId, CollectionConst.ROLE_STORAGE_HEAD)
        users.requireRole(v.leaderUserId, e.orgId, CollectionConst.ROLE_DIRECTOR)
        inventory(c, e.orgId, v)
    }
    private fun inventory(c: Change, orgId: String, v: WarehouseStaffingHandoverRequest, requireCompleteScope: Boolean = true): List<CollectionPermanentArchiveDao> {
        if (v.kind == EnumStaffingHandoverKind.RECORD_CORRECTION) {
            if (v.inventoryBatchIds.isNotEmpty()) fail("任职录入更正不能冒充实际离任移交")
            return emptyList()
        }
        if (v.inventoryBatchIds.isEmpty()) fail("实际离任须关联已完成的库管离任清点批次")
        val dates = mutableSetOf<LocalDateTime>()
        return v.inventoryBatchIds.sorted().map { id ->
            val batch = InventoryBatchDao.findById(id) ?: fail("清点批次不存在")
            if (batch.orgId != orgId) fail("离任清点须属于本馆")
            InventoryArchiveEvidence(archives).verify(batch)
            if (requireCompleteScope) inventoryDepartureScopeErrors(batch.plan, batch.baseline, batch.warehouseId).takeIf { it.isNotEmpty() }?.let { fail(it.joinToString("；")) }
            val plan = batch.plan ?: fail("清点方案缺失")
            if (plan.purpose != EnumInventoryPurpose.KEEPER_DEPARTURE || plan.leavingUserId != c.userId || plan.successorUserId !in c.staffing.keeperUserIds || plan.successorUserId == c.userId) fail("离任人员、清点用途或接任人员与该次任职记录不一致")
            if (plan.keeperStaffingChangeIds.isNotEmpty() && c.after.id.value !in plan.keeperStaffingChangeIds) fail("历史任职清点未引用本次库管离任原件，不能用于其他任职变动")
            dates.add(plan.leavingAt ?: fail("缺少实际离任时间"))
            if (dates.size > 1) fail("同次离任各清点批次的离任时间须一致")
            if (batch.completion == null) fail("离任清点尚未完成签署及实际移交")
            rows(InventoryComplianceService.BIZ, id).singleOrNull { it.phase == "COMPLETED" } ?: fail("缺少清点完成原件")
        }
    }
    private fun requireSigner(e: WarehouseDao, v: WarehouseStaffingHandoverRequest, user: SecurityUser, role: String) {
        val expected = if (role == "keeper") v.keeperUserId else v.leaderUserId
        val current = if (role == "keeper") e.staffing?.keeperUserId else e.staffing?.leaderUserId
        val code = if (role == "keeper") CollectionConst.ROLE_STORAGE_HEAD else CollectionConst.ROLE_DIRECTOR
        if (user.id != expected || user.id != current || code !in user.roleCodes || !scope.isMuseumMember(user, e.orgId)) fail("须由所指定且仍实际任职的负责人本人确认，管理员不能代签")
        users.requireRole(user.id, e.orgId, code)
    }
    private fun item(e: WarehouseDao, c: Change, s: State, user: SecurityUser): WarehouseStaffingHandoverItem {
        val record = s.records.lastOrNull { it.request.assignmentArchiveId == c.after.id.value && it.request.leavingUserId == c.userId }
        val keeper = record?.let { s.signatures["K_${it.id}"] }; val leader = record?.let { s.signatures["L_${it.id}"] }
        val late = record?.request?.inventoryBatchIds.orEmpty().filterTo(linkedSetOf()) { id -> InventoryBatchDao[id].let { inventoryDepartureStatus(it.plan, it.completion) == EnumInventoryDepartureStatus.COMPLETED_LATE } }
        val scopeErrors = record?.request?.inventoryBatchIds.orEmpty().flatMap { id -> InventoryBatchDao[id].let { inventoryDepartureScopeErrors(it.plan, it.baseline, it.warehouseId).map { error -> "${it.batchName}：$error" } } }
        fun can(role: String) = record != null && scopeErrors.isEmpty() && try { requireSigner(e, record.request, user, role); true } catch (_: BadRequestException) { false }
        val status = when { record == null -> "PENDING"; scopeErrors.isNotEmpty() -> "SCOPE_REVIEW_REQUIRED"; keeper == null -> "PENDING_KEEPER"; leader == null -> "PENDING_LEADER"; record.request.kind == EnumStaffingHandoverKind.RECORD_CORRECTION -> "CORRECTION_CONFIRMED"; late.isNotEmpty() -> "DEPARTURE_CONFIRMED_LATE"; else -> "DEPARTURE_CONFIRMED" }
        return WarehouseStaffingHandoverItem(c.after.id.value, c.before.id.value, c.userId, c.staffing.assignedAt, c.staffing.keeperUserIds, record, keeper, leader, status, late, keeper == null && can("keeper"), keeper != null && leader == null && can("leader"), scopeErrors)
    }
    private fun rows(biz: String, id: String) = CollectionPermanentArchiveDao.find { (CollectionPermanentArchives.bizType eq biz) and (CollectionPermanentArchives.bizId eq id) }.sortedWith(compareBy({ it.createTime }, { it.id.value }))
    private inline fun <reified T> decode(a: CollectionPermanentArchiveDao, orgId: String): T {
        if (a.orgId != orgId || a.digest != CollectionCareEvidence.digest(a.snapshot)) fail("岗位交接原件摘要或所属馆不一致")
        return JsonUtils.fromJson(a.snapshot, T::class.java) ?: fail("岗位交接原件无法解析")
    }
    private fun state(e: WarehouseDao): State = try {
        val changes = WarehouseStaffingArchiveEvidence(archives).changes(e)
        val signatures = mutableMapOf<String, CollectionCareSignature>()
        val records = mutableListOf<WarehouseStaffingHandoverRecord>()
        rows(BIZ, e.id.value).forEach { a ->
            if (a.phase.startsWith("R_")) {
                val r = decode<WarehouseStaffingHandoverRecord>(a, e.orgId); val v = r.request
                if (a.phase != "R_${r.id}" || records.any { it.id == r.id }) fail("岗位交接版本原件不一致")
                val c = changes.singleOrNull { it.after.id.value == v.assignmentArchiveId && it.userId == v.leavingUserId } ?: fail("岗位交接缺少连续任职原件")
                if (r.previousAssignmentArchiveId != c.before.id.value || r.previousAssignmentDigest != c.before.digest || r.assignmentDigest != c.after.digest || records.lastOrNull { sameChange(it.request, v) }?.id != v.previousId) fail("任职或交接版本依据不一致")
                if (records.any { !sameChange(it.request, v) && it.request.inventoryBatchIds.intersect(v.inventoryBatchIds).isNotEmpty() }) fail("同一清点被重复用于本库不同次任职变动")
                if (inventory(c, e.orgId, v, requireCompleteScope = false).associate { it.id.value to it.digest } != r.inventoryArchiveDigests) fail("清点完成原件与交接核对依据不一致")
                archives.verifyExisting(a, e.orgId, BIZ, e.id.value, a.phase,
                    JsonUtils.mapper.readTree(a.snapshot), v.scopeFileIds)
                records.add(r)
            } else {
                val r = records.singleOrNull { a.phase == "K_${it.id}" || a.phase == "L_${it.id}" } ?: fail("岗位确认缺少原登记记录")
                val v = decode<CollectionCareSignature>(a, e.orgId); val leader = a.phase.startsWith("L_")
                if (v.actorUserId != (if (leader) r.request.leaderUserId else r.request.keeperUserId) || v.confirmedAt.isBefore(r.registeredAt) || (leader && (signatures["K_${r.id}"]?.confirmedAt?.let { v.confirmedAt.isBefore(it) } != false))) fail("岗位交接确认人员或顺序与原记录不一致")
                archives.verifyExisting(a, e.orgId, BIZ, e.id.value, a.phase,
                    JsonUtils.mapper.readTree(a.snapshot), v.record.fileIds)
                signatures[a.phase] = v
            }
        }
        State(changes, records, signatures)
    } catch (_: tools.jackson.core.JacksonException) { fail("岗位交接原件无法解析，请核对历史记录") }
}
