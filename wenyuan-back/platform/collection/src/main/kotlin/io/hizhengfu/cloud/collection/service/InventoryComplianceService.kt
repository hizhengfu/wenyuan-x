package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.IdGenerate
import io.hizhengfu.cloud.common.utils.SecurityUtils
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.dao.entityCache
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.springframework.stereotype.Component
import org.springframework.cache.annotation.CacheConfig
import org.springframework.cache.annotation.CacheEvict
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
@Transactional
@CacheConfig(cacheNames = [CollectionConst.CACHE])
class InventoryComplianceService(
    private val scope: CollectionScopeResolver,
    private val users: CollectionResponsibleUserService,
    private val archives: CollectionPermanentArchiveService,
    private val incidents: CollectionIncidentService? = null,
) {
    private val evidence = InventoryArchiveEvidence(archives)
    companion object { const val BIZ = "COLLECTION_INVENTORY_BATCH"; const val ANNUAL = "COLLECTION_INVENTORY_ANNUAL" }

    fun get(id: String): InventoryBatchVo {
        val e = InventoryBatchDao.findById(id) ?: fail("盘点批次不存在")
        OrgScopedReadPolicy.requireVisible(scope.inventoryReadAccess().orgIds, e.orgId, "盘点批次")
        val errors = evidence.errors(e).toMutableList()
        val replacement = try { e.plan?.leadershipDepartureId?.let { LeadershipDepartureEvidence(archives).activeBatchId(e.orgId, it) }?.takeIf { it != id } }
            catch (ex: BadRequestException) { errors.add(ex.message ?: "重办清点链待核对"); null }
        return e.toModel().copy(evidenceErrors = errors, supersededByBatchId = replacement, archives = archives.inventoryOriginals(e.orgId, BIZ, id, errors), overdueCollectionIds = e.plan?.let { population(e.orgId, it.year).filter { it.overdue }.map { it.collectionId }.toSet() }.orEmpty())
    }
    fun requireBatchArchivedOriginal(id: String, archiveId: String, fileId: String) {
        if (id.isBlank() || id.length > 100) fail("清点批次标识不正确")
        val batch = get(id)
        CollectionCaseArchivedOriginal.require(mapOf("archives" to batch.archives), archiveId, fileId)
    }

    fun begin(id: String, value: InventoryPlanVo): String {
        val actorId = writerId()
        val e = locked(id)
        val verifyWriter = writerVerifier(e.orgId, actorId)
        verifyWriter()
        evidence.verify(e)
        if (e.plan != null) {
            if (e.plan == value) { verifyWriter(); return id }
            fail("已开始清点的范围及责任人已归档，不能替换")
        }
        if (value.year !in 1900..LocalDateTime.now().year || value.scopeDescription.isBlank()) fail("请填写真实清点年度与范围依据")
        requireHeads(e.orgId, value.keeperUserId, value.leaderUserId)
        requiredFiles(value.scopeFileIds)
        val ownership = CollectionOrgDao[e.orgId].ownership ?: fail("请先核实本馆举办主体")
        if (value.leadershipDepartureId != null) {
            // locked 已先取得馆锁，与案卷更正和逐件核验保持同一顺序。
            val departures = LeadershipDepartureEvidence(archives)
            val departure = departures.requirePlan(e.orgId, value, ownership)
            val activeId = departures.activeBatchId(e.orgId, departure.id)
            if (activeId != value.replacesInventoryBatchId) fail("该案已有清点批次；重办须明确引用当前批次并保留原件")
            if (activeId != null) {
                val previous = InventoryBatchDao.findById(activeId) ?: fail("原清点批次不存在")
                evidence.verify(previous)
                if (previous.completion != null) fail("已签署完成的清点与移交不能作为未完成批次重办")
                if (value.replanReason.isNullOrBlank()) fail("请说明重新组织全馆清点的实际原因")
                requiredFiles(value.replanFileIds)
            } else if (!value.replanReason.isNullOrBlank() || !value.replanFileIds.isNullOrBlank()) fail("首次清点不能携带无对应原批次的重办依据")
        } else if (value.replacesInventoryBatchId != null || !value.replanReason.isNullOrBlank() || !value.replanFileIds.isNullOrBlank()) fail("重办链只适用于已核对的负责人离任交接案")
        val population = population(e.orgId, value.year)
        val configuredIds = splitCollectionIds(e.collectionIds).toSet()
        val keeperSources = WarehouseStaffingArchiveEvidence(archives).forPlan(e.orgId, value)
        if (value.purpose == EnumInventoryPurpose.KEEPER_DEPARTURE) {
            if (value.method == EnumInventoryMethod.SAMPLE) fail("保管人员离任须核对完整责任范围，不能用抽检代替")
            if (!e.warehouseId.isNullOrBlank() || (value.method != EnumInventoryMethod.FULL && configuredIds.isEmpty())) fail("保管人员离任须明确完整责任清单，不能按当前单个库位推定范围")
        }
        val selected = when {
            value.method == EnumInventoryMethod.FULL || value.purpose == EnumInventoryPurpose.DIRECTOR_DEPARTURE -> {
                if (configuredIds.isNotEmpty() && configuredIds != population.map { it.collectionId }.toSet()) fail("全面或主要负责人离任清点须覆盖全馆正式藏品，不能用子集代替")
                if (!e.warehouseId.isNullOrBlank()) fail("全面或主要负责人离任清点不得限定单个库位")
                population
            }
            configuredIds.isNotEmpty() -> population.filter { it.collectionId in configuredIds }.also {
                if (it.size != configuredIds.size) fail("应盘清单含外馆、未正式入藏或已退出藏品")
            }
            !e.warehouseId.isNullOrBlank() -> population.filter { it.locationKey == "WAREHOUSE:${e.warehouseId}" }
            else -> fail("专项或抽检请明确实际应盘藏品清单或位置")
        }
        if (selected.isEmpty()) fail("应盘清单不能为空")
        if (value.purpose != EnumInventoryPurpose.ROUTINE) {
            val role = if (value.purpose == EnumInventoryPurpose.DIRECTOR_DEPARTURE) CollectionConst.ROLE_DIRECTOR else CollectionConst.ROLE_KEEPER
            val leaving = value.leavingUserId?.takeIf(String::isNotBlank) ?: fail("请明确离任人员")
            val successor = value.successorUserId?.takeIf(String::isNotBlank) ?: fail("请明确实际接任人员")
            if (leaving == successor) fail("离任与接任须为不同人员")
            if (value.leadershipDepartureId == null && keeperSources.isEmpty()) users.requireRole(leaving, e.orgId, role)
            users.requireRole(successor, e.orgId, role)
            if (value.leavingAt == null) fail("请明确离任时间")
            requiredFiles(value.responsibilityFileIds)
        }
        e.plan = value
        val selection = when {
            value.method == EnumInventoryMethod.FULL || value.purpose == EnumInventoryPurpose.DIRECTOR_DEPARTURE -> EnumInventoryScopeSelection.FULL_MUSEUM
            configuredIds.isNotEmpty() -> EnumInventoryScopeSelection.EXPLICIT_COLLECTIONS
            else -> EnumInventoryScopeSelection.CURRENT_LOCATION
        }
        e.baseline = InventoryBaselineVo(LocalDateTime.now(), ownership, selected, keeperSources, selection)
        e.collectionIds = selected.joinToString(",") { it.collectionId }
        e.checks = InventoryChecksVo(); e.issueActions = InventoryIssueActionsVo()
        // Existing free-form start/end fields are not legal completion evidence.
        e.finishedAt = null
        seal(e, "STARTED", mapOf("plan" to value, "baseline" to e.baseline), files(value.scopeFileIds, value.responsibilityFileIds, value.replanFileIds))
        verifyWriter()
        return id
    }

    @CacheEvict(allEntries = true)
    fun check(id: String, value: InventoryItemCheckVo): String {
        val actorId = writerId()
        val e = lockedItem(id, value.collectionId)
        val verifyWriter = writerVerifier(e.orgId, actorId)
        verifyWriter()
        evidence.verify(e); editable(e)
        val plan = e.plan!!
        val source = e.baseline!!.items.singleOrNull { it.collectionId == value.collectionId } ?: fail("核验藏品不在冻结清单中")
        val old = e.checks?.items.orEmpty().singleOrNull { it.collectionId == value.collectionId }
        if (old == value) { verifyWriter(); return id }
        if (old != null && value.replacementReason.isNullOrBlank()) fail("更正核验须说明原因，原件仍永久保留")
        if (e.issueActions?.items.orEmpty().any { it.collectionId == value.collectionId }) fail("已经登记差异处置，不能覆盖原核验事实")
        if (value.checkedAt.year != plan.year || value.checkedAt.isAfter(LocalDateTime.now())) fail("请填写清点年度内实际核验时间，不得填未来时间")
        if (value.checkerUserIds.isEmpty()) fail("请明确实际核验人员")
        value.checkerUserIds.forEach { users.requireRole(it, e.orgId, CollectionConst.ROLE_KEEPER) }
        if (value.actualLocation.isBlank() || value.condition.isBlank()) fail("请如实记录现场位置和保存现状")
        if (listOf(value.ledgerMatches, value.objectMatches, value.cardMatches, value.photoMatches, value.damaged).any { it == null }) fail("请逐项明确账、物、卡、照与损毁核验结论")
        if (value.ledgerMatches == true) requiredFiles(value.ledgerFileIds)
        if (value.cardMatches == true) requiredFiles(value.cardFileIds)
        if (value.photoMatches == true) requiredFiles(value.photoFileIds)
        requiredFiles(value.signedCheckFileIds)
        if ((value.abnormal() || source.overdue || population(e.orgId, plan.year).any { it.collectionId == value.collectionId && it.overdue }) && (value.findings.isNullOrBlank() || value.protectiveMeasures.isNullOrBlank())) fail("差异、损毁或逾期件须记录具体问题及即时处置措施")
        seal(e, "CHECK_${IdGenerate.nextId()}", value, files(value.ledgerFileIds, value.cardFileIds, value.photoFileIds, value.signedCheckFileIds))
        e.checks = InventoryChecksVo(e.checks?.items.orEmpty().filterNot { it.collectionId == value.collectionId } + value)
        syncSafetyHold(e, value.collectionId)
        verifyWriter()
        return id
    }

    @CacheEvict(allEntries = true)
    fun action(id: String, value: InventoryIssueActionVo): String {
        val actorId = writerId()
        val e = lockedItem(id, value.collectionId)
        val verifyWriter = writerVerifier(e.orgId, actorId)
        verifyWriter()
        evidence.verify(e)
        if (e.plan == null) fail("请先开始清点")
        val check = e.checks?.items.orEmpty().singleOrNull { it.collectionId == value.collectionId } ?: fail("请先记录实际核验")
        if (!check.abnormal() && e.baseline!!.items.none { it.collectionId == value.collectionId && it.overdue } && population(e.orgId, e.plan!!.year).none { it.collectionId == value.collectionId && it.overdue }) fail("本件没有已登记清点差异或逾期事项")
        val old = e.issueActions?.items.orEmpty().singleOrNull { it.collectionId == value.collectionId }
        if (old == value) { verifyWriter(); return id }
        if (value.recordedAt.isBefore(check.checkedAt) || value.recordedAt.isAfter(LocalDateTime.now()) || (old != null && value.recordedAt.isBefore(old.recordedAt))) fail("处理时间须在核验及上次处置之后且不得虚填未来时间")
        if (value.cause.isBlank() || value.actions.isBlank() || value.significanceReason.isBlank()) fail("请填写原因调查、处理措施和重大情况判断依据")
        users.requireRole(value.responsibleUserId, e.orgId, CollectionConst.ROLE_KEEPER)
        if (value.resolved == null || value.major == null) fail("请明确填写是否处理完毕及是否重大情况，不得省略判断")
        if (old?.major == true && value.major != true) fail("既有重大情况报告义务不能被后续处置覆盖；调查结论另行如实说明")
        if (value.resolved == false && (value.followUpAt == null || !value.followUpAt.isAfter(value.recordedAt))) fail("未处理完毕须指定后续核查时间")
        requiredFiles(value.evidenceFileIds)
        if (value.major == true) {
            if (value.authorityName.isNullOrBlank() || value.reportNo.isNullOrBlank() || value.reportedAt == null || value.reportedAt.isBefore(check.checkedAt) || value.reportedAt.isAfter(LocalDateTime.now())) fail("重大情况须记录向主管文物行政部门实际报告的机关、编号和时间")
            requiredFiles(value.reportFileIds)
        }
        if (value.resolved == true) {
            if (value.safeForUse != true || value.keeperUserId != e.plan!!.keeperUserId || value.leaderUserId != e.plan!!.leaderUserId) fail("结束异常须由方案责任人核实处理及安全结论，不能仅勾选完成")
            requireHeads(e.orgId, value.keeperUserId, value.leaderUserId)
            if (value.resolutionApprovedAt == null || value.resolutionApprovedAt.isBefore(value.recordedAt) || value.resolutionApprovedAt.isAfter(LocalDateTime.now())) fail("请核实处理结果的实际签署时间")
            requiredFiles(value.signedResolutionFileIds)
            if (check.abnormal()) {
                val incidentId = value.incidentId?.takeIf(String::isNotBlank) ?: fail("清点异常恢复可用须关联本批次本件已完成处理和本人确认的事故案卷")
                val proof = (incidents ?: fail("事故处置服务未就绪，不能解除清点限制")).requireClearance(
                    incidentId, e.orgId, value.collectionId, IncidentSourceVo(BIZ, id), value.resolutionApprovedAt,
                    value.keeperUserId, value.leaderUserId,
                    if (value.major == true) listOf(IncidentReportRequirement(value.collectionId, EnumIncidentTarget.COMPETENT, EnumIncidentReportKind.INCIDENT)) else emptyList(),
                )
                seal(e, "INCIDENT_CLEARANCE_${IdGenerate.nextId()}", mapOf("action" to value, "incidentProof" to proof), files(value.evidenceFileIds, value.signedResolutionFileIds))
            }
        }
        seal(e, "ACTION_${IdGenerate.nextId()}", value, files(value.evidenceFileIds, value.reportFileIds, value.signedResolutionFileIds))
        e.issueActions = InventoryIssueActionsVo(e.issueActions?.items.orEmpty().filterNot { it.collectionId == value.collectionId } + value)
        syncSafetyHold(e, value.collectionId)
        verifyWriter()
        return id
    }

    fun complete(id: String, value: InventoryCompletionVo): String {
        val actorId = writerId()
        val e = locked(id)
        val verifyWriter = writerVerifier(e.orgId, actorId)
        verifyWriter()
        evidence.verify(e)
        inventoryDepartureScopeErrors(e.plan, e.baseline, e.warehouseId).takeIf { it.isNotEmpty() }?.let { fail(it.joinToString("；") + "；请保留原件，另建完整责任清点批次") }
        if (e.completion != null) {
            if (e.completion == value) { verifyWriter(); return id }
            fail("已签署结果不能覆盖；后续问题处理继续追加归档")
        }
        editable(e)
        val plan = e.plan!!; val checks = e.checks?.items.orEmpty()
        if (checks.map { it.collectionId }.toSet() != e.baseline!!.items.map { it.collectionId }.toSet()) fail("尚有应盘藏品未逐件核验账物卡照，不能完成清点")
        if (plan.method == EnumInventoryMethod.FULL || plan.purpose == EnumInventoryPurpose.DIRECTOR_DEPARTURE) {
            if (population(e.orgId, plan.year).map { it.collectionId }.toSet() != checks.map { it.collectionId }.toSet()) fail("全馆藏品范围已变化，请补充清点新批次并重新组织完整离任或全面清点")
        }
        val liveOverdue = population(e.orgId, plan.year).filter { it.overdue }.map { it.collectionId }.toSet()
        if (CollectionOrgDao[e.orgId].ownership != e.baseline!!.ownership) fail("本馆举办主体信息已变化，请重新核实离任监督要求与清点方案")
        val problems = e.baseline!!.items.filter { it.overdue || it.collectionId in liveOverdue || checks.single { c -> c.collectionId == it.collectionId }.abnormal() }.map { it.collectionId }
        if (!e.issueActions?.items.orEmpty().map { it.collectionId }.containsAll(problems)) fail("差异、损毁或逾期件尚未登记原因调查、处置和重大情况报告")
        if (value.keeperUserId != plan.keeperUserId || value.leaderUserId != plan.leaderUserId) fail("签署责任人须与清点方案一致")
        requireHeads(e.orgId, value.keeperUserId, value.leaderUserId)
        if (value.signedAt.isAfter(LocalDateTime.now()) || checks.any { it.checkedAt.isAfter(value.signedAt) } || e.issueActions?.items.orEmpty().any { it.recordedAt.isAfter(value.signedAt) || it.reportedAt?.isAfter(value.signedAt) == true || it.resolutionApprovedAt?.isAfter(value.signedAt) == true }) fail("清点报告签署须在实际核验及已记处置之后，不得虚填未来时间")
        requiredFiles(value.reportFileIds)
        if (plan.purpose != EnumInventoryPurpose.ROUTINE) {
            val at = value.actualHandoverAt ?: fail("离任须记录实际移交时间")
            if (at.isAfter(LocalDateTime.now()) || checks.any { it.checkedAt.isAfter(at) } || value.signedAt.isBefore(at)) fail("实际移交须在核验之后、结果签署之前，不得虚填未来时间")
            if (inventoryDepartureStatus(plan, value) == EnumInventoryDepartureStatus.COMPLETED_LATE) {
                if (value.departureDelayReason.isNullOrBlank()) fail("清点、移交及签署应在离任前完成；迟办须如实记录原因、处理情况和依据，不能标作按期完成")
                requiredFiles(value.departureDelayFileIds)
            }
            requiredFiles(value.handoverFileIds)
            if (plan.purpose == EnumInventoryPurpose.DIRECTOR_DEPARTURE && e.baseline!!.ownership == EnumOwnership.STATE) {
                if (value.supervisorAuthority.isNullOrBlank() || value.supervisorNames.map(String::trim).filter(String::isNotBlank).isEmpty() || value.supervisionAt == null || value.supervisionAt.isAfter(at) || value.supervisionAt.isBefore(checks.minOf { it.checkedAt })) fail("国有馆主要负责人离任须登记主管文物行政部门实际派员监督清点、移交的情况")
                requiredFiles(value.appointmentFileIds); requiredFiles(value.supervisionFileIds)
            }
        }
        seal(e, "COMPLETED", mapOf("plan" to plan, "baseline" to e.baseline, "checks" to e.checks, "issueActions" to e.issueActions, "completion" to value), files(value.reportFileIds, value.handoverFileIds, value.appointmentFileIds, value.supervisionFileIds, value.departureDelayFileIds))
        e.completion = value; e.finishedAt = value.signedAt; e.reportAttachmentIds = value.reportFileIds
        verifyWriter()
        return id
    }

    /** RFID observations and imported result labels cannot substitute for four-way physical verification. */
    fun requireActualCheck(entity: InventoryDao) {
        val batch = entity.batchId?.let { InventoryBatchDao.findById(it) } ?: fail("请先建立实际清点方案")
        if (batch.orgId != entity.orgId) fail("清点明细与批次须属于同一收藏单位")
        evidence.verify(batch)
        val check = batch.checks?.items.orEmpty().singleOrNull { it.collectionId == entity.collectionId } ?: fail("请先在批次中逐件核验账物卡照；RFID 标签比对不能直接作为验收结论")
        val expected = when { check.objectMatches == false -> EnumInventoryResult.MISSING; check.abnormal() -> EnumInventoryResult.ABNORMAL; else -> EnumInventoryResult.MATCH }
        if (entity.result != expected) fail("盘点结果与已签署的实际核验不一致")
    }

    fun coverage(year: Int): InventoryCoverageVo = coverageFor(scope.inventoryReadAccess().currentOrgId(scope), year)
    private fun coverageFor(orgId: String, year: Int): InventoryCoverageVo {
        if (year !in 1900..LocalDateTime.now().year) fail("清点年度无效")
        val population = population(orgId, year)
        val archivedIds = evidence.archivedBatchIds(orgId, year)
        val batches = InventoryBatchDao.find { InventoryBatches.orgId eq orgId }.filter { it.plan?.year == year || it.id.value in archivedIds }
        val unverified = batches.filter { evidence.errors(it).isNotEmpty() }.map { it.id.value }.toSet() + (archivedIds - batches.map { it.id.value }.toSet())
        val verified = batches.filter { it.id.value !in unverified }
        val completed = verified.filter { it.completion != null }
        val currentItems = population.associateBy { it.collectionId }
        // 历史批次永久保留，但已承接到其他馆的藏品不能继续充当本馆当前类别、地点的清点覆盖。
        val covered = completed.flatMap { e -> e.baseline!!.items.filter { i -> i.collectionId in currentItems && e.checks!!.items.any { it.collectionId == i.collectionId && it.checkedAt.year == year } } }
        val latestChecks = completed.flatMap { it.checks!!.items }.groupBy { it.collectionId }.mapValues { (_, items) -> items.maxOf { it.checkedAt } }
        val checked = covered.map { it.collectionId }.toSet()
        val priorityChecked = population.filter { i -> latestChecks[i.collectionId]?.let { at -> i.priorityAfter == null || !at.isBefore(i.priorityAfter) } == true }.map { it.collectionId }.toSet()
        // 账物卡照的旧快照不能替变更后的类别或存放地点作当期核查依据。
        val categories = covered.mapNotNull { old -> currentItems[old.collectionId]?.categoryId?.takeIf { it == old.categoryId } }.toSet()
        val locations = covered.mapNotNull { old -> currentItems[old.collectionId]?.locationKey?.takeIf { it == old.locationKey } }.toSet()
        val unhandled = mutableSetOf<String>(); val pending = mutableSetOf<String>()
        val liveOverdue = population.filter { it.overdue }.associateBy { it.collectionId }
        val timelyActions = mutableSetOf<String>()
        // Include issues from unfinished batches too, so finalizing a clean batch cannot hide another batch's findings.
        verified.forEach { e ->
            e.checks?.items.orEmpty().filter { c -> c.abnormal() || c.collectionId in liveOverdue || e.baseline!!.items.any { it.collectionId == c.collectionId && it.overdue } }.forEach { c ->
                val action = e.issueActions?.items.orEmpty().singleOrNull { it.collectionId == c.collectionId }
                if (action == null) unhandled += c.collectionId
                else {
                    if (action.resolved != true || c.collectionId in liveOverdue) pending += c.collectionId
                    val due = liveOverdue[c.collectionId]?.overdueAt
                    if (due != null && !action.recordedAt.isBefore(due)) timelyActions += c.collectionId
                }
            }
        }
        unhandled += liveOverdue.keys - timelyActions
        pending += incidents?.restrictedCollectionIds(orgId).orEmpty().intersect(population.map { it.collectionId }.toSet())
        return InventoryCoverageVo(year, LocalDateTime.now(), population, checked, completed.map { it.id.value }.toSet(),
            population.mapNotNull { it.categoryId }.toSet() - categories,
            population.filter { it.locationKey !in locations }.associate { it.locationKey to it.locationName },
            population.filter { (it.precious || it.usedThisYear) && it.collectionId !in priorityChecked }.map { it.collectionId }.toSet(),
            population.filter { it.categoryId.isNullOrBlank() || !it.locationVerified || it.locationKey == "UNKNOWN" || it.content.nature == null }.map { it.collectionId }.toSet(), unhandled, pending, unverified,
            completed.filter { inventoryDepartureStatus(it.plan, it.completion) == EnumInventoryDepartureStatus.COMPLETED_LATE }.map { it.id.value }.toSet(),
            verified.filter { inventoryDepartureScopeErrors(it.plan, it.baseline, it.warehouseId).isNotEmpty() }.map { it.id.value }.toSet())
    }

    fun annualReports(year: Int): List<Map<String, Any?>> {
        val orgId = scope.inventoryReadAccess().currentOrgId(scope)
        if (year !in 1900..LocalDateTime.now().year) fail("清点年度无效")
        return InventoryAnnualReportDao.find { (InventoryAnnualReports.orgId eq orgId) and (InventoryAnnualReports.year eq year) }.sortedByDescending { it.version }.map {
            val errors = evidence.annualErrors(it).toMutableList()
            val originals=archives.inventoryOriginals(orgId,ANNUAL,it.id.value,errors)
            mapOf("id" to it.id.value, "year" to it.year, "version" to it.version, "evidenceErrors" to errors,
                "content" to if (errors.isEmpty()) JsonUtils.fromJson(it.content, InventoryAnnualReportVo::class.java) else null,
                "coverage" to if (errors.isEmpty()) JsonUtils.fromJson(it.coverage, InventoryCoverageVo::class.java) else null,
                "archives" to originals)
        }
    }
    fun requireAnnualArchivedOriginal(year: Int, id: String, archiveId: String, fileId: String) {
        if (id.isBlank() || id.length > 100) fail("年度清点报告标识不正确")
        val record = annualReports(year).singleOrNull { it["id"] == id } ?: fail("本年度清点报告不存在")
        CollectionCaseArchivedOriginal.require(record, archiveId, fileId)
    }

    fun report(value: InventoryAnnualReportVo): String {
        val actorId = writerId()
        val orgId = scope.currentOrgId()
        CollectionCurrentOperationAccess.require(scope, orgId, true, setOf(CollectionConst.STORAGE), "年度清点报告")
        CollectionOrgDao.find { CollectionOrgs.id eq orgId }.forUpdate().single()
        // 等待馆级锁期间账号或职责可能变化，须按当前权限重新核对后再写入或返回旧回执。
        val verifyWriter = writerVerifier(orgId, actorId)
        verifyWriter()
        val previous = InventoryAnnualReportDao.find { (InventoryAnnualReports.orgId eq orgId) and (InventoryAnnualReports.year eq value.year) }.maxByOrNull { it.version }
        if (previous != null) evidence.annual(previous)
        if (previous != null && JsonUtils.fromJson(previous.content, InventoryAnnualReportVo::class.java) == value) { verifyWriter(); return previous.id.value }
        if (previous != null && value.supplementReason.isNullOrBlank()) fail("年度报告补报须说明原因，原报告不能覆盖")
        val coverage = coverageFor(orgId, value.year)
        if (!coverage.ready()) fail("年度清点仍有原件待核对批次、未覆盖类别、地点、重点藏品或未处理差异，请查看覆盖缺口")
        users.requireRole(value.leaderUserId, orgId, CollectionConst.ROLE_DIRECTOR)
        if (value.signedAt.year < value.year || value.signedAt.isAfter(value.reportedAt) || value.reportedAt.isAfter(LocalDateTime.now())) fail("请填写实际负责人签署和随后报送时间，不得虚填未来时间")
        if (coverage.completedBatchIds.any { InventoryBatchDao[it].completion!!.signedAt.isAfter(value.signedAt) }) fail("年度报告签署时间不能早于纳入报告的批次清点结果")
        val latestActionTime = InventoryBatchDao.find { InventoryBatches.orgId eq orgId }.filter { it.plan?.year == value.year }.flatMap { it.issueActions?.items.orEmpty() }.flatMap { listOfNotNull(it.recordedAt, it.reportedAt, it.resolutionApprovedAt) }.maxOrNull()
        if (latestActionTime?.isAfter(value.signedAt) == true) fail("年度报告须在所载最新差异处理及报告事实之后签署")
        if (value.authorityName.isBlank() || value.reportNo.isBlank()) fail("请填写主管文物行政部门及实际报送编号")
        requiredFiles(value.signedReportFileIds); requiredFiles(value.receiptFileIds)
        val record = InventoryAnnualReportDao.new {
            this.orgId = orgId; year = value.year; version = (previous?.version ?: 0) + 1
            content = JsonUtils.toJson(value)!!; this.coverage = JsonUtils.toJson(coverage)!!
        }
        archives.seal(orgId, ANNUAL, record.id.value, "REPORTED", mapOf("report" to value, "coverage" to coverage, "late" to (value.reportedAt.year > value.year)), files(value.signedReportFileIds, value.receiptFileIds))
        verifyWriter()
        return record.id.value
    }

    private fun writerId(): String = SecurityUtils.getNullUserDetail()?.id ?: fail("请登录后办理清点")

    /** 实际 SQL 后复核当前资格；失败时清点、安全限制及签署档案由同一事务回滚。 */
    private fun writerVerifier(orgId: String, actorId: String): () -> Unit = {
        TransactionManager.current().entityCache.flush()
        if (SecurityUtils.getNullUserDetail()?.id != actorId) fail("写入期间清点办理账号已变化，请重新登录")
        val org = CollectionOrgDao.findById(orgId) ?: fail("清点所属收藏单位不存在")
        org.refresh(flush = false)
        CollectionCurrentOperationAccess.require(scope, orgId, true, setOf(CollectionConst.STORAGE), "清点办理")
    }

    private fun population(orgId: String, year: Int): List<InventoryBaselineItemVo> {
        val physicalReturns = CollectionReturnDao.find { CollectionReturns.orgId eq orgId }.filter { it.executionState == EnumReturnExecutionState.EXECUTED }
            .groupBy { it.sourceBizType to it.sourceBizId }.mapValues { (_, rows) -> rows.flatMap { splitCollectionIds(it.collectionIds) }.toSet() }
        val loans = LoanDao.find { Loans.orgId eq orgId }.filter { it.outExecuted }
        val requisitions = RequisitionDao.find { Requisitions.orgId eq orgId }.filter { it.outExecuted }
        val collections = CollectionDao.find { Collections.orgId eq orgId }.filter { it.lifeStatus !in setOf(EnumCollectionStatus.PENDING, EnumCollectionStatus.DEREGISTERED) && (it.registeredAt == null || it.registeredAt!!.year <= year) }.toList()
        // 实际使用属于藏品历史，机构承接不能抹掉原馆本年已经发生的出库事实。
        val yearStart = LocalDateTime.of(year, 1, 1, 0, 0)
        val nextYear = yearStart.plusYears(1)
        val currentIds = collections.map { it.id.value }.toSet()
        val historicalUse = (
            LoanDao.find {
                (Loans.outExecuted eq true) and (
                    ((Loans.outExecutedTime greaterEq yearStart) and (Loans.outExecutedTime less nextYear)) or
                        (Loans.outExecutedTime.isNull() and (Loans.loanDate greaterEq yearStart.toLocalDate()) and (Loans.loanDate less nextYear.toLocalDate()))
                    )
            }.flatMap { loan ->
                val at = loan.outExecutedTime ?: loan.loanDate?.atStartOfDay()
                splitCollectionIds(loan.collectionIds).filter { it in currentIds }.mapNotNull { id -> at?.let { id to it } }
            } + RequisitionDao.find {
                (Requisitions.outExecuted eq true) and (Requisitions.outExecutedTime greaterEq yearStart) and (Requisitions.outExecutedTime less nextYear)
            }.flatMap { req ->
                splitCollectionIds(req.collectionIds).filter { it in currentIds }.mapNotNull { id -> req.outExecutedTime?.let { id to it } }
            }
        ).groupBy({ it.first }, { it.second }).mapValues { (_, times) -> times.max() }
        return collections.map { c ->
            val id = c.id.value
            val loan = loans.lastOrNull { id in splitCollectionIds(it.collectionIds) && !it.returned && id !in physicalReturns[CollectionConst.BIZ_LOAN to it.id.value].orEmpty() }
            val req = requisitions.lastOrNull { id in splitCollectionIds(it.collectionIds) && !it.returned && id !in physicalReturns[CollectionConst.BIZ_REQUISITION to it.id.value].orEmpty() }
            val warehouse = c.warehouseId?.let { WarehouseDao.findById(it) }?.takeIf { it.orgId==orgId }
            val location = when {
                loan != null -> "LOAN:${loan.borrower.orEmpty()}${loan.dossier?.useSite?.let { ":$it" }.orEmpty()}" to "借入单位：${loan.borrower.orEmpty()}；${loan.dossier?.useSite ?: "实际存放地点待核实"}"
                req?.dossier != null -> "USE:${req.dossier!!.usageSite}" to "提用地点：${req.dossier!!.usageSite}"
                c.lifeStatus in setOf(EnumCollectionStatus.RESTORING, EnumCollectionStatus.ON_LOAN) || (c.lifeStatus == EnumCollectionStatus.IN_USE && warehouse?.sceneType != EnumLocationScene.EXHIBITION) -> "UNKNOWN" to "出库实物所在展厅、修复室或其他实际地点尚待核实"
                warehouse != null -> "WAREHOUSE:${warehouse.id.value}" to warehouse.name
                else -> "UNKNOWN" to "尚未核实实际存放位置"
            }
            val useTimes = loans.filter { id in splitCollectionIds(it.collectionIds) }.mapNotNull { it.outExecutedTime ?: it.loanDate?.atStartOfDay() } + requisitions.filter { id in splitCollectionIds(it.collectionIds) }.mapNotNull { it.outExecutedTime }
            val priorityAfter = (useTimes.filter { it.year == year } + listOfNotNull(historicalUse[id])).maxOrNull()
            val used = priorityAfter != null
            val due = loan?.let { LoanReturnDeadlineEvidence.forCollection(it,id) } ?: req?.dossier?.expectedReturnAt ?: req?.returnDate?.plusDays(1)?.atStartOfDay()
            InventoryBaselineItemVo(id, CollectionCatalogMapper.fromCollection(c.toModel()), c.categoryId, location.first, location.second,
                c.level in setOf(EnumRelicLevel.FIRST, EnumRelicLevel.SECOND, EnumRelicLevel.THIRD), used, due?.isBefore(LocalDateTime.now()) == true, priorityAfter, due, location.first != "UNKNOWN" && (loan == null || !loan.dossier?.useSite.isNullOrBlank()))
        }
    }

    private fun lockedItem(id: String, collectionId: String): InventoryBatchDao {
        val batch = InventoryBatchDao.findById(id) ?: fail("盘点批次不存在")
        CollectionCurrentOperationAccess.require(scope, batch.orgId, true, setOf(CollectionConst.STORAGE), "盘点批次")
        CollectionOrgDao.find { CollectionOrgs.id eq batch.orgId }.forUpdate().single()
        val collection = CollectionDao.find { Collections.id eq collectionId }.forUpdate().singleOrNull() ?: fail("藏品不存在")
        if (collection.orgId != batch.orgId) fail("不能核验或处置外馆藏品")
        return locked(id)
    }
    private fun syncSafetyHold(e: InventoryBatchDao, collectionId: String) {
        e.flush()
        val held = InventoryBatchDao.find { InventoryBatches.orgId eq e.orgId }.forUpdate().any { batch ->
            evidence.verify(batch)
            batch.checks?.items.orEmpty().any { it.collectionId == collectionId && it.abnormal() } && batch.issueActions?.items.orEmpty().none { it.collectionId == collectionId && it.resolved == true && it.safeForUse == true }
        }
        CollectionLedgerFieldChangeRecorder.inventoryHold(CollectionDao[collectionId],held,"清点异常安全限制复核",e.id.value)
    }

    private fun locked(id: String): InventoryBatchDao {
        val orgId = InventoryBatchDao.findById(id)?.orgId ?: fail("盘点批次不存在")
        CollectionCurrentOperationAccess.require(scope, orgId, true, setOf(CollectionConst.STORAGE), "盘点批次")
        // 核验先锁馆、藏品再锁批次；开始和完成也须先锁馆，不能反向等待。
        CollectionOrgDao.find { CollectionOrgs.id eq orgId }.forUpdate().single()
        val e = InventoryBatchDao.find { InventoryBatches.id eq id }.forUpdate().singleOrNull() ?: fail("盘点批次不存在")
        e.refresh(flush = false)
        if (e.orgId != orgId) fail("清点批次所属收藏单位已变化，请重新核实")
        CollectionCurrentOperationAccess.require(scope, e.orgId, true, setOf(CollectionConst.STORAGE), "盘点批次")
        return e
    }
    /** 调用方持有批次行锁；无方案草稿也须排除残留签署档案或被清空的合规字段。 */
    internal fun requireObservationWritable(e: InventoryBatchDao) {
        evidence.verify(e)
        if (e.completion != null) fail("已签署清点结果不能追加或修改观察记录，请另建补充批次")
        if (e.plan != null) editable(e)
    }

    private fun editable(e: InventoryBatchDao) {
        if (e.plan == null || e.baseline == null) fail("请先冻结清点方案与应盘清单")
        if (e.completion != null) fail("已签署完成的清点不可改写，后续处置请追加登记")
        e.plan?.leadershipDepartureId?.let {
            if (LeadershipDepartureEvidence(archives).activeBatchId(e.orgId, it) != e.id.value) fail("该清点已有后续全馆重办批次，原核验保留，不能继续改写或完成旧批次")
        }
    }
    private fun requireHeads(orgId: String, keeper: String, leader: String) {
        if (keeper == leader) fail("保管部门负责人和主要负责人须由不同人员承担")
        users.requireRole(keeper, orgId, CollectionConst.ROLE_STORAGE_HEAD)
        users.requireRole(leader, orgId, CollectionConst.ROLE_DIRECTOR)
    }
    private fun seal(e: InventoryBatchDao, phase: String, value: Any, fileIds: String?) = archives.seal(e.orgId, BIZ, e.id.value, phase, value, fileIds)
    private fun requiredFiles(value: String?) { if (BusinessAttachmentBindingPlanner.plan("", "", value).isEmpty()) fail("请提供实际签署或核验依据原件") }
    private fun files(vararg values: String?) = values.filterNotNull().filter(String::isNotBlank).joinToString(",").ifBlank { null }
    private fun fail(message: String): Nothing = throw BadRequestException(message)
}
