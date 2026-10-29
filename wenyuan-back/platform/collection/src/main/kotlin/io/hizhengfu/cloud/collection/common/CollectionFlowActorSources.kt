package io.hizhengfu.cloud.collection.common

import io.hizhengfu.cloud.autoconfigure.exposed.UIDTable
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.flow.engine.FlowActorContext
import io.hizhengfu.cloud.flow.engine.FlowNodeSnapshot
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select

/** 两种派单策略共同从原业务表取得所属馆；表单馆别不参与任职判断。 */
internal object CollectionFlowActorSources {
    private val sources: Map<String, Pair<UIDTable, Column<String>>> = mapOf(
        CollectionConst.BIZ_ACQUISITION to (Acquisitions to Acquisitions.orgId),
        CollectionConst.BIZ_ACQUISITION_PLAN to (AcquisitionPlans to AcquisitionPlans.orgId),
        CollectionConst.BIZ_EXHIBITION to (Exhibitions to Exhibitions.orgId),
        CollectionConst.BIZ_INVENTORY to (Inventories to Inventories.orgId),
        CollectionConst.BIZ_REPRODUCTION to (Reproductions to Reproductions.orgId),
        CollectionConst.BIZ_CHANGE to (CollectionChanges to CollectionChanges.orgId),
        CollectionConst.BIZ_DEREGISTER to (Deregistrations to Deregistrations.orgId),
        CollectionConst.BIZ_LOAN to (Loans to Loans.orgId),
        CollectionConst.BIZ_REQUISITION to (Requisitions to Requisitions.orgId),
        CollectionConst.BIZ_RETURN to (CollectionReturns to CollectionReturns.orgId),
        CollectionConst.BIZ_APPRAISAL to (Appraisals to Appraisals.orgId),
        CollectionConst.BIZ_SAMPLING to (CollectionSamplings to CollectionSamplings.orgId),
        CollectionConst.BIZ_WAREHOUSE_ENTRY to (WarehouseEntryApplications to WarehouseEntryApplications.orgId),
    )
    private val keys = CollectionFlowDefinitionConstraint().contracts.keys
    fun applies(context: FlowActorContext) = context.definitionKey in keys || context.businessType in sources || context.businessType == CollectionConst.BIZ_TRANSFER

    fun orgId(context: FlowActorContext, node: FlowNodeSnapshot): String {
        val id = context.businessKey?.takeIf(String::isNotBlank) ?: throw BadRequestException("审批缺少原业务记录，不能从表单猜测馆别")
        if (context.businessType == CollectionConst.BIZ_TRANSFER) {
            val row = CollectionTransfers.select(CollectionTransfers.orgId, CollectionTransfers.toOrgId).where { CollectionTransfers.id eq id }.singleOrNull()
                ?: throw BadRequestException("跨馆承接业务记录不存在")
            return when {
                node.nodeKey.startsWith("source_") -> row[CollectionTransfers.orgId]
                node.nodeKey.startsWith("target_") -> row[CollectionTransfers.toOrgId] ?: throw BadRequestException("接收馆尚未明确")
                else -> throw BadRequestException("跨馆承接的附加角色节点须以 source_ 或 target_ 明确所属馆")
            }
        }
        val (table, org) = sources[context.businessType] ?: throw BadRequestException("审批的藏品业务类型不明确")
        return table.select(org).where { table.id eq id }.singleOrNull()?.get(org)
            ?: throw BadRequestException("审批对应的原业务记录不存在")
    }
}
