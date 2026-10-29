package io.hizhengfu.cloud.collection.common

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.flow.domain.AssigneeType
import io.hizhengfu.cloud.flow.domain.FlowModel
import io.hizhengfu.cloud.flow.engine.FlowGraph
import io.hizhengfu.cloud.flow.engine.FlowNodeSnapshot
import io.hizhengfu.cloud.sys.system.dao.Roles
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.exists
import org.jetbrains.exposed.v1.jdbc.selectAll

/** 仅内置资源的 ROLE 值约定为角色编码；持久化模型和运行时始终使用主键 ID。 */
object CollectionFlowRoleConfiguration {
    fun resolveTemplateCodes(modelData: String): String {
        val model = JsonUtils.mapper.readValue(modelData, FlowModel::class.java)
        val codes = model.nodes.filter { it.properties.assigneeType == AssigneeType.ROLE.name }
            .flatMap { it.properties.assignees.orEmpty() }.toSet()
        if (codes.isEmpty()) return modelData
        check(Roles.exists()) { "角色表尚未就绪，不能导入角色审批模板" }
        val byCode = Roles.selectAll().where { Roles.code inList codes }.associateBy { it[Roles.code] }
        check(codes.all { byCode[it]?.get(Roles.enabled) == true }) { "模板要求的审批角色缺失或未启用，请核对角色配置" }
        return JsonUtils.mapper.writeValueAsString(model.copy(nodes = model.nodes.map { node ->
            if (node.properties.assigneeType != AssigneeType.ROLE.name) node
            else node.copy(properties = node.properties.copy(
                assignees = node.properties.assignees.orEmpty().map { byCode.getValue(it)[Roles.id].value },
                assigneeNames = node.properties.assignees.orEmpty().map { byCode.getValue(it)[Roles.name] },
            ))
        }))
    }

    /** 对馆方模型严格按 ID 核对，不将历史编码猜成另一个角色或权限。 */
    fun violations(graph: FlowGraph): List<String> = violations(graph.nodes.values)

    fun violations(candidates: Collection<FlowNodeSnapshot>): List<String> {
        val nodes = candidates.filter { it.assigneeType == AssigneeType.ROLE.name }
        if (nodes.isEmpty()) return emptyList()
        if (!Roles.exists()) return listOf("审批角色表尚未就绪，不能核实角色主键")
        val ids = nodes.flatMap { it.assignees.orEmpty() }.toSet()
        val enabled = if (ids.isEmpty()) emptySet() else Roles.selectAll().where { Roles.id inList ids }
            .filter { it[Roles.enabled] }.map { it[Roles.id].value }.toSet()
        return nodes.mapNotNull { node ->
            if (node.assignees.isNullOrEmpty() || node.assignees.orEmpty().any { it !in enabled })
                "角色审批节点(${node.nodeKey})须配置已启用角色的主键 ID；旧编码、权限码和失效角色须保留原版后人工核对"
            else null
        }
    }
}
