package io.hizhengfu.cloud.collection.common

import io.hizhengfu.cloud.flow.engine.*
import io.hizhengfu.cloud.flow.domain.AssigneeType
import io.hizhengfu.cloud.flow.domain.NodeType
import org.springframework.stereotype.Component

/** 节点 Key 来自业务的 buildSelfSelectAssignees 与实际签署日志核验，不能仅按图形 ID 比较。 */
@Component
class CollectionFlowDefinitionConstraint : FlowDefinitionConstraint {
    val contracts: Map<String, FlowApprovalContract> = mapOf(
        CollectionConst.FLOW_ACQUISITION to roleApproval("征集入藏", "appraise" to "征集材料鉴定复核", "review" to "保管部门审核", "approve" to "分管负责人审批"),
        CollectionConst.FLOW_ACQUISITION_PLAN to roleApproval("征集计划", "committee" to "委员会审核", "vice_director" to "分管负责人审批"),
        CollectionConst.FLOW_EXHIBITION to roleApproval("藏品展览", "approve" to "保管部门审核"),
        CollectionConst.FLOW_INVENTORY to roleApproval("藏品盘点", "approve" to "保管部门审核"),
        CollectionConst.FLOW_REPRODUCTION to roleApproval("通用复制历史流程", "approve" to "保管部门审核"),
        CollectionConst.FLOW_CHANGE to approval("信息变更", "keeper" to "保管负责人确认", "leader" to "主要负责人批准"),
        CollectionConst.FLOW_DEREGISTER to approval("退出办理", "keeper" to "保管负责人确认", "leader" to "主要负责人批准"),
        CollectionConst.FLOW_LOAN to approval("藏品借用", "keeper" to "保管负责人确认", "leader" to "主要负责人批准"),
        CollectionConst.FLOW_REQUISITION to approval("馆内提用", "department" to "提用部门负责人确认", "keeper" to "保管负责人确认", "leader" to "主要负责人批准"),
        CollectionConst.FLOW_RETURN to approval("逐件归还", "keeper" to "保管负责人确认"),
        CollectionConst.FLOW_APPRAISAL to FlowApprovalContract("馆内鉴选", listOf(FlowApprovalStage("committee", "鉴选成员本人意见", unanimous = true))),
        CollectionConst.FLOW_TRANSFER to approval("跨馆承接", "source_keeper" to "原馆保管确认", "source_leader" to "原馆依法负责人员确认", "target_keeper" to "接收馆保管确认", "target_leader" to "接收馆负责人批准"),
        CollectionConst.FLOW_SAMPLING to approval("有损取样", "conservation" to "保护修复部门确认", "keeper" to "保管部门确认"),
        CollectionConst.FLOW_DIGITAL_ACQUISITION_2026 to approval("数字采集", "information" to "信息化部门确认", "keeper" to "保管部门确认"),
        CollectionConst.FLOW_REPRODUCTION_2026 to approval("复制拓印", "keeper" to "保管负责人确认", "leader" to "主要负责人批准"),
        CollectionConst.FLOW_WAREHOUSE_ENTRY to FlowApprovalContract(
            "人员入库", listOf(FlowApprovalStage("keeper", "保管负责人确认"), FlowApprovalStage("leader", "主要负责人指定特殊陪同")),
            conditionalLastStageVariable = "requiresDirector",
        ),
    )

    val businessKeys: Map<String, Set<String>> = mapOf(
        CollectionConst.BIZ_CHANGE to setOf(CollectionConst.FLOW_CHANGE),
        CollectionConst.BIZ_ACQUISITION to setOf(CollectionConst.FLOW_ACQUISITION),
        CollectionConst.BIZ_DEREGISTER to setOf(CollectionConst.FLOW_DEREGISTER),
        CollectionConst.BIZ_LOAN to setOf(CollectionConst.FLOW_LOAN),
        CollectionConst.BIZ_TRANSFER to setOf(CollectionConst.FLOW_TRANSFER),
        CollectionConst.BIZ_REQUISITION to setOf(CollectionConst.FLOW_REQUISITION),
        CollectionConst.BIZ_RETURN to setOf(CollectionConst.FLOW_RETURN),
        CollectionConst.BIZ_APPRAISAL to setOf(CollectionConst.FLOW_APPRAISAL),
        CollectionConst.BIZ_SAMPLING to setOf(CollectionConst.FLOW_SAMPLING),
        CollectionConst.BIZ_WAREHOUSE_ENTRY to setOf(CollectionConst.FLOW_WAREHOUSE_ENTRY),
        CollectionConst.BIZ_REPRODUCTION to setOf(CollectionConst.FLOW_REPRODUCTION, CollectionConst.FLOW_DIGITAL_ACQUISITION_2026, CollectionConst.FLOW_REPRODUCTION_2026),
        CollectionConst.BIZ_EXHIBITION to setOf(CollectionConst.FLOW_EXHIBITION),
        CollectionConst.BIZ_INVENTORY to setOf(CollectionConst.FLOW_INVENTORY),
        CollectionConst.BIZ_ACQUISITION_PLAN to setOf(CollectionConst.FLOW_ACQUISITION_PLAN),
    )

    override fun violations(definitionKey: String, graph: FlowGraph) = contracts[definitionKey]?.let {
        it.violations(graph) + CollectionFlowRoleConfiguration.violations(graph)
    }.orEmpty()

    override fun requiresAssignees(definitionKey: String, businessType: String?, node: FlowNodeSnapshot) =
        (definitionKey in contracts || businessType in businessKeys) && node.type in setOf(NodeType.APPROVE.name, NodeType.HANDLE.name)

    override fun assignmentViolations(definitionKey: String, businessType: String?, node: FlowNodeSnapshot) =
        if (requiresAssignees(definitionKey, businessType, node)) CollectionFlowRoleConfiguration.violations(listOf(node)) else emptyList()

    override fun startViolations(definitionKey: String, businessType: String?, graph: FlowGraph, formData: Map<String, Any?>, selected: Map<String, List<String>>): List<String> {
        val expected = businessKeys[businessType]
        return buildList {
            if (expected != null && definitionKey !in expected) add("该业务须使用当前流程标识：${expected.joinToString()}，旧实例应保留并单独核查")
            addAll(violations(definitionKey, graph))
            addAll(contracts[definitionKey]?.startViolations(formData, selected).orEmpty())
        }
    }

    private fun approval(label: String, vararg stages: Pair<String, String>) =
        FlowApprovalContract(label, stages.map { FlowApprovalStage(it.first, it.second) })

    override fun reentryViolations(definitionKey: String, businessType: String?, graph: FlowGraph, targetNodeKey: String, formData: Map<String, Any?>, selected: Map<String, List<String>>) =
        startViolations(definitionKey, businessType, graph, formData, selected) +
            contracts[definitionKey]?.violations(graph, targetNodeKey).orEmpty()

    private fun roleApproval(label: String, vararg stages: Pair<String, String>) =
        FlowApprovalContract(label, stages.map { FlowApprovalStage(it.first, it.second, assigneeType = AssigneeType.ROLE) })
}
