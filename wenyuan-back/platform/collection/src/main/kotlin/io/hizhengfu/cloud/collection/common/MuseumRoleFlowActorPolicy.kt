package io.hizhengfu.cloud.collection.common

import io.hizhengfu.cloud.collection.service.CollectionActorDirectory
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.flow.engine.*
import java.util.Locale

/** 角色审核只派给当前本馆有效成员；管理员可以据实移交角色待办，不能替本人同意或拒绝。 */
abstract class MuseumRoleFlowActorPolicy(private val directory: CollectionActorDirectory) : FlowActorPolicy {
    protected abstract fun applies(context: FlowActorContext): Boolean
    protected abstract fun orgId(context: FlowActorContext, node: FlowNodeSnapshot): String

    override fun resolve(context: FlowActorContext, node: FlowNodeSnapshot): List<Pair<String, String>>? {
        if (node.assigneeType != "ROLE" || !applies(context)) return null
        requireFlowContract(CollectionFlowRoleConfiguration.violations(listOf(node)))
        return directory.roleMembers(node.assignees.orEmpty(), orgId(context, node))
    }

    override fun validateTask(context: FlowActorContext, node: FlowNodeSnapshot, assignee: String?, operatorId: String, action: String, transferTo: String?) {
        if (node.assigneeType != "ROLE" || !applies(context)) return
        actionRestrictions(context, node, assignee, operatorId, listOf(action))[action]?.let { throw BadRequestException(it) }
        if (action in setOf("TRANSFER", "DELEGATE") && transferTo !in directory.roleMembers(node.assignees.orEmpty(), orgId(context, node)).map { it.first }) {
            throw BadRequestException("移交或委托对象须为本馆当前有效的同一审批角色成员")
        }
    }

    override fun actionRestrictions(context: FlowActorContext, node: FlowNodeSnapshot, assignee: String?, operatorId: String, actions: List<String>): Map<String, String> {
        if (node.assigneeType != "ROLE" || !applies(context)) return emptyMap()
        return try {
            requireFlowContract(CollectionFlowRoleConfiguration.violations(listOf(node)))
            val museum = orgId(context, node)
            val operator = directory.requireActive(operatorId)
            val people = directory.roleMembers(node.assignees.orEmpty(), museum).map { it.first }.toSet()
            val admin = operator.permissions.any { it.uppercase(Locale.ROOT) in setOf("ADMIN", "FLOW_ALL", "FLOW_TASK_ADMIN_COMPLETE") }
            actions.mapNotNull { action ->
                when {
                    !(action == "TRANSFER" && admin) && (operatorId != assignee || operatorId !in people) -> action to "须由该待办当前指定且仍具本馆角色资格的本人办理，管理员不能代批"
                    action in setOf("TRANSFER", "DELEGATE") && people.none { it != assignee } -> action to "当前没有其他本馆有效的同一审批角色人员可接任"
                    else -> null
                }
            }.toMap()
        } catch (e: BadRequestException) {
            actions.associateWith { e.message ?: "当前人员不具备该审批角色资格" }
        }
    }

    override fun taskVerifier(context: FlowActorContext, node: FlowNodeSnapshot, assignee: String?, operatorId: String, action: String, transferTo: String?): () -> Unit {
        if (node.assigneeType != "ROLE" || !applies(context)) return {}
        val museum = orgId(context, node)
        return {
            if (orgId(context, node) != museum) throw BadRequestException("任务办理期间原审批馆已变化")
            validateTask(context, node, assignee, operatorId, action, transferTo)
        }
    }
}
