package io.hizhengfu.cloud.collection.common

import io.hizhengfu.cloud.collection.service.CollectionActorDirectory
import io.hizhengfu.cloud.collection.service.CollectionResponsibleUserService
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.flow.engine.*

/** 原单据上的责任人不能由通用转办替换；附加节点仍按馆方允许的动作办理。 */
data class MuseumNamedResponsibility(
    val people: List<String>,
    val roleCode: String? = null,
    val departmentId: String? = null,
    val departmentHead: Boolean = false,
    val externalAllowed: Boolean = false,
)

abstract class MuseumNamedFlowActorPolicy(
    private val directory: CollectionActorDirectory,
    private val users: CollectionResponsibleUserService,
) : FlowActorPolicy {
    protected abstract fun applies(context: FlowActorContext): Boolean
    protected abstract fun orgId(context: FlowActorContext, node: FlowNodeSnapshot): String
    protected open fun responsibility(context: FlowActorContext, node: FlowNodeSnapshot): MuseumNamedResponsibility? = null

    private fun accepts(context: FlowActorContext, node: FlowNodeSnapshot) = applies(context) &&
        node.type in setOf("APPROVE", "HANDLE") && node.assigneeType in setOf("FIXED_USER", "SELF_SELECT")

    override fun resolve(context: FlowActorContext, node: FlowNodeSnapshot, selected: Map<String, List<String>>): List<Pair<String, String>>? {
        if (!accepts(context, node)) return null
        val museum = orgId(context, node)
        val rule = responsibility(context, node)
        val people = if (node.assigneeType == "FIXED_USER") node.assignees.orEmpty() else selected[node.nodeKey].orEmpty()
        if (people.isEmpty() || people.any { it.isBlank() || it != it.trim() } || people.distinct().size != people.size) {
            throw BadRequestException("节点[${node.name}]须指定明确、不重复的实际办理人")
        }
        if (rule != null && people != rule.people) throw BadRequestException("流程指定名单与原业务责任人不一致，请保留原记录核对")
        return people.map { id ->
            requireQualified(id, museum, rule)
            id to directory.requireActive(id).name
        }
    }

    override fun validateTask(context: FlowActorContext, node: FlowNodeSnapshot, assignee: String?, operatorId: String, action: String, transferTo: String?) {
        if (!accepts(context, node)) return
        actionRestrictions(context, node, assignee, operatorId, listOf(action))[action]?.let { throw BadRequestException(it) }
        if (action in setOf("TRANSFER", "DELEGATE"))
            requireQualified(transferTo ?: throw BadRequestException("请指定实际接任人员"), orgId(context, node), null)
    }

    override fun taskVerifier(context: FlowActorContext, node: FlowNodeSnapshot, assignee: String?, operatorId: String, action: String, transferTo: String?): () -> Unit {
        if (!accepts(context, node)) return {}
        val museum = orgId(context, node)
        val rule = responsibility(context, node)?.let { it.copy(people = it.people.toList()) }
        return {
            if (orgId(context, node) != museum || responsibility(context, node) != rule) throw BadRequestException("任务办理期间原馆或指定责任依据已变化")
            validateTask(context, node, assignee, operatorId, action, transferTo)
        }
    }

    override fun actionRestrictions(context: FlowActorContext, node: FlowNodeSnapshot, assignee: String?, operatorId: String, actions: List<String>): Map<String, String> {
        if (!accepts(context, node)) return emptyMap()
        return try {
            val museum = orgId(context, node)
            val rule = responsibility(context, node)
            if (operatorId != assignee || (rule != null && operatorId !in rule.people)) {
                throw BadRequestException("须由原业务指定且仍具资格的待办本人办理，管理员不能代批或代拒绝")
            }
            requireQualified(operatorId, museum, rule)
            actions.filter { rule != null && it in setOf("TRANSFER", "DELEGATE") }
                .associateWith { "原业务指定责任人不能直接转办或委托，请先依法办理责任人变更并重新审核" }
        } catch (e: BadRequestException) {
            actions.associateWith { e.message ?: "当前人员不具备原业务指定资格" }
        }
    }

    private fun requireQualified(id: String, museum: String, rule: MuseumNamedResponsibility?) {
        directory.requireActive(id)
        if (rule?.externalAllowed != true) users.requireMuseumMember(id, museum)
        rule?.roleCode?.let { users.requireRole(id, museum, it) }
        rule?.departmentId?.let {
            if (rule.departmentHead) users.requireDepartmentHead(id, museum, it)
            else users.requireDepartmentMember(id, museum, it)
        }
    }
}
