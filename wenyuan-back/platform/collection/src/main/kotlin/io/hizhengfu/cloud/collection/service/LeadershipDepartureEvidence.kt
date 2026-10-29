package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq

/** 历史任职依据只用于指定离任清点；不作为当前账号、角色或流程办理授权。 */
internal class LeadershipDepartureEvidence(private val archives: CollectionPermanentArchiveService) {
    companion object { const val BIZ = "COLLECTION_LEADERSHIP_DEPARTURE" }
    data class State(val records: List<LeadershipDepartureRecord>, val preparations: Map<String, CollectionCareSignature>, val completions: Map<String, LeadershipDepartureCompletionReview>) {
        fun latest(id: String) = records.none { it.request.previousId == id }
        fun prepared(id: String) = preparations["PK_$id"] != null && preparations["PS_$id"] != null
    }
    fun files(v: LeadershipDepartureRequest) = listOf(v.appointmentFileIds, v.departureFileIds, v.successorAppointmentFileIds, v.responsibilityFileIds).joinToString(",")
    fun rows(orgId: String) = CollectionPermanentArchiveDao.find {
        (CollectionPermanentArchives.bizType eq BIZ) and (CollectionPermanentArchives.bizId eq orgId)
    }.sortedWith(compareBy({ it.createTime }, { it.id.value }))
    private inline fun <reified T> decode(row: CollectionPermanentArchiveDao, orgId: String): T {
        if (row.orgId != orgId || row.digest != CollectionCareEvidence.digest(row.snapshot)) fail("负责人离任交接原件摘要或馆别不一致")
        return JsonUtils.fromJson(row.snapshot, T::class.java) ?: fail("负责人离任交接原件无法读取")
    }
    fun read(orgId: String): State = try {
        val records = mutableListOf<LeadershipDepartureRecord>()
        val prepared = mutableMapOf<String, CollectionCareSignature>()
        val completed = mutableMapOf<String, LeadershipDepartureCompletionReview>()
        rows(orgId).forEach { row ->
            if (row.phase.startsWith("CASE_")) {
                val r = decode<LeadershipDepartureRecord>(row, orgId)
                if (row.phase != "CASE_${r.id}" || records.any { it.id == r.id }) fail("离任交接案编号与原件不一致")
                if (listOf(r.request.appointmentFileIds, r.request.departureFileIds, r.request.successorAppointmentFileIds, r.request.responsibilityFileIds).any { BusinessAttachmentBindingPlanner.plan("", "", it).isEmpty() }) fail("离任交接各项任职原件引用缺失")
                r.request.previousId?.let { previous ->
                    if (records.none { it.id == previous } || records.any { it.request.previousId == previous }) fail("离任交接更正版本顺序不一致")
                }
                archives.verifyExisting(row, orgId, BIZ, orgId, row.phase, r, files(r.request)); records.add(r)
            } else {
                val r = records.singleOrNull { row.phase in setOf("PK_${it.id}", "PS_${it.id}", "HK_${it.id}", "HS_${it.id}") } ?: fail("离任交接确认缺少登记原件")
                val preparation = row.phase.startsWith("P")
                val review = if (preparation) null else decode<LeadershipDepartureCompletionReview>(row, orgId)
                val signature = review?.signature ?: decode<CollectionCareSignature>(row, orgId)
                if (signature.record.opinion.isBlank() || BusinessAttachmentBindingPlanner.plan("", "", signature.record.fileIds).isEmpty()) fail("本人确认意见或签署原件缺失")
                val keeper = row.phase.startsWith("PK_") || row.phase.startsWith("HK_")
                if (signature.actorUserId != (if (keeper) r.request.keeperUserId else r.request.successorUserId) || signature.confirmedAt.isBefore(r.registeredAt)) fail("离任交接确认人员或时间与指定原件不一致")
                if (!keeper) {
                    val first = if (preparation) prepared["PK_${r.id}"] else completed["HK_${r.id}"]?.signature
                    if (first == null || signature.confirmedAt.isBefore(first.confirmedAt)) fail("须依次核对同一离任交接案")
                }
                if (!preparation && (prepared["PS_${r.id}"] == null || signature.confirmedAt.isBefore(prepared["PS_${r.id}"]!!.confirmedAt))) fail("须先核对原任职、离任与接任依据")
                if (review != null && !keeper && completed["HK_${r.id}"]?.let { it.inventoryArchiveId != review.inventoryArchiveId || it.inventoryArchiveDigest != review.inventoryArchiveDigest } != false) fail("双方须核对同一清点完成原件")
                archives.verifyExisting(row, orgId, BIZ, orgId, row.phase, review ?: signature, signature.record.fileIds)
                if (preparation) prepared[row.phase] = signature else completed[row.phase] = review!!
            }
        }
        State(records, prepared, completed)
    } catch (_: tools.jackson.core.JacksonException) { fail("负责人离任交接原件无法解析，请核对历史记录") }

    fun requirePlan(orgId: String, plan: InventoryPlanVo, ownership: EnumOwnership): LeadershipDepartureRecord {
        val id = plan.leadershipDepartureId?.takeIf(String::isNotBlank) ?: fail("请关联负责人离任交接案")
        val state = read(orgId)
        val r = state.records.singleOrNull { it.id == id } ?: fail("负责人离任交接案不存在或属于其他馆")
        val v = r.request
        if (!state.latest(id) || !state.prepared(id)) fail("离任交接案须先完成本版本的任职依据核对")
        if (plan.purpose != EnumInventoryPurpose.DIRECTOR_DEPARTURE || plan.method != EnumInventoryMethod.FULL || plan.leavingUserId != v.leavingUserId || plan.successorUserId != v.successorUserId || plan.leavingAt != v.leavingAt || plan.keeperUserId != v.keeperUserId || plan.leaderUserId != v.successorUserId || ownership != r.ownership) fail("清点用途、范围、责任人、离任时间或举办主体与交接案不一致")
        return r
    }
    /** 同时查原始开始档案，不能通过清空当前方案绕过案卷绑定。 */
    fun boundBatchIds(orgId: String, caseId: String): Set<String> {
        val ids = InventoryBatchDao.find { InventoryBatches.orgId eq orgId }.filter { it.plan?.leadershipDepartureId == caseId }.mapTo(linkedSetOf()) { it.id.value }
        CollectionPermanentArchiveDao.find { (CollectionPermanentArchives.bizType eq InventoryComplianceService.BIZ) and (CollectionPermanentArchives.orgId eq orgId) and (CollectionPermanentArchives.phase eq "STARTED") }.forEach { row ->
            val node = try { JsonUtils.mapper.readTree(row.snapshot) } catch (_: tools.jackson.core.JacksonException) { fail("清点开始原件无法解析，须先核对既有交接绑定") }
            if (node?.get("plan")?.get("leadershipDepartureId")?.stringValue() == caseId) ids.add(row.bizId)
        }
        return ids
    }
    fun activeBatchId(orgId: String, caseId: String): String? {
        val ids = boundBatchIds(orgId, caseId)
        if (ids.isEmpty()) return null
        val links = ids.associateWith { id ->
            val row = CollectionPermanentArchiveDao.find { (CollectionPermanentArchives.bizType eq InventoryComplianceService.BIZ) and (CollectionPermanentArchives.bizId eq id) and (CollectionPermanentArchives.phase eq "STARTED") }.singleOrNull() ?: fail("离任清点缺少开始原件")
            if (row.orgId != orgId || row.digest != CollectionCareEvidence.digest(row.snapshot)) fail("离任清点重办链原件不一致")
            val plan = try { JsonUtils.fromJson(JsonUtils.mapper.readTree(row.snapshot).get("plan").toString(), InventoryPlanVo::class.java) } catch (_: tools.jackson.core.JacksonException) { fail("离任清点原方案无法解析") } ?: fail("离任清点缺少原方案")
            if (plan.leadershipDepartureId != caseId) fail("离任清点的原始案卷绑定不一致")
            plan.replacesInventoryBatchId
        }
        if (links.values.count { it == null } != 1 || links.values.filterNotNull().any { it !in ids } || links.values.filterNotNull().distinct().size != links.values.filterNotNull().size) fail("离任清点重办链存在缺失或分叉")
        val last = (ids - links.values.filterNotNull().toSet()).singleOrNull() ?: fail("离任清点重办顺序不一致")
        val visited = mutableSetOf<String>(); var current: String? = last
        while (current != null && visited.add(current)) current = links[current]
        if (current != null || visited != ids) fail("离任清点重办链存在循环或脱节")
        return last
    }
    private fun fail(message: String): Nothing = throw BadRequestException(message)
}
