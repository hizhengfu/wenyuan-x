package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq

/** 连续任职原件证明指定库管的历史任职，不提供任何当前账号或岗位授权。 */
internal class WarehouseStaffingArchiveEvidence(private val archives: CollectionPermanentArchiveService) {
    data class Change(val before: CollectionPermanentArchiveDao, val after: CollectionPermanentArchiveDao, val userId: String, val staffing: WarehouseStaffingVo)
    private fun fail(message: String): Nothing = throw BadRequestException(message)
    private fun rows(id: String) = CollectionPermanentArchiveDao.find {
        (CollectionPermanentArchives.bizType eq WarehouseStaffingService.BIZ) and (CollectionPermanentArchives.bizId eq id)
    }.filter { it.phase.startsWith("ASSIGNED_") }.sortedWith(compareBy({ it.createTime }, { it.id.value }))
    private fun assignment(row: CollectionPermanentArchiveDao, orgId: String): WarehouseStaffingVo = try {
        if (row.orgId != orgId || row.digest != CollectionCareEvidence.digest(row.snapshot)) fail("库管任职原件摘要或所属馆不一致")
        val value = JsonUtils.fromJson(row.snapshot, WarehouseStaffingVo::class.java) ?: fail("库管任职原件无法解析")
        if (BusinessAttachmentBindingPlanner.plan("", "", value.signedAssignmentFileIds).isEmpty()) fail("库管任职签署原件引用缺失")
        archives.verifyExisting(row, orgId, WarehouseStaffingService.BIZ, row.bizId,
            row.phase, JsonUtils.mapper.readTree(row.snapshot), value.signedAssignmentFileIds)
        value
    } catch (_: tools.jackson.core.JacksonException) { fail("库管任职原件无法解析，请核对历史记录") }

    fun changes(e: WarehouseDao): List<Change> {
        val originals = rows(e.id.value).map { it to assignment(it, e.orgId) }
        if (originals.lastOrNull()?.second != e.staffing) fail("当前任职名单与最新原件不一致，请核对任职历史")
        return originals.zipWithNext().flatMap { (before, after) ->
            (before.second.keeperUserIds - after.second.keeperUserIds).map { Change(before.first, after.first, it, after.second) }
        }
    }

    fun forPlan(orgId: String, plan: InventoryPlanVo): List<InventoryKeeperStaffingProof> {
        if (plan.keeperStaffingChangeIds.isEmpty()) return emptyList()
        if (plan.purpose != EnumInventoryPurpose.KEEPER_DEPARTURE || plan.leadershipDepartureId != null || plan.method == EnumInventoryMethod.SAMPLE) fail("历史库管任职依据只适用于完整责任范围的保管人员离任清点")
        val leaving = plan.leavingUserId?.takeIf(String::isNotBlank) ?: fail("请明确实际离任库管")
        val successor = plan.successorUserId?.takeIf(String::isNotBlank) ?: fail("请明确实际接任库管")
        val leavingAt = plan.leavingAt ?: fail("请提供真实离任时间")
        if (leaving == successor) fail("离任人与接任人须不同")
        val result = plan.keeperStaffingChangeIds.sorted().map { id ->
            val row = CollectionPermanentArchiveDao.findById(id) ?: fail("所引用的库管任职变动原件不存在")
            if (row.bizType != WarehouseStaffingService.BIZ || row.orgId != orgId || !row.phase.startsWith("ASSIGNED_")) fail("任职依据不是本馆库管任职变动原件")
            val warehouse = WarehouseDao.findById(row.bizId) ?: fail("任职原件所对应库房不存在")
            if (warehouse.orgId != orgId) fail("任职原件与库房所属馆不一致")
            val originals = rows(warehouse.id.value)
            val index = originals.indexOfFirst { it.id.value == id }
            if (index < 1) fail("首次任职记录不能独立证明此前的库管离任")
            val previous = originals[index - 1]
            // 只核对当次前后原件；合法的后续任职及账号变动不改变当次历史资格。
            val before = assignment(previous, orgId); val after = assignment(row, orgId)
            if (leaving !in before.keeperUserIds || leaving in after.keeperUserIds || successor !in after.keeperUserIds) fail("任职变动中的离任人或接任人不匹配")
            if (before.assignedAt.isAfter(leavingAt)) fail("离任时间不能早于所依据的原任职签署时间")
            InventoryKeeperStaffingProof(warehouse.id.value, previous.id.value, previous.digest, row.id.value, row.digest)
        }
        if (result.map { it.warehouseId }.distinct().size != result.size) fail("同次清点不能混用同一库房不同次离任的任职依据")
        return result
    }
}
