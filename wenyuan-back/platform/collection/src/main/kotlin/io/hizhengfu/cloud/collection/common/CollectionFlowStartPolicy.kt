package io.hizhengfu.cloud.collection.common

import io.hizhengfu.cloud.autoconfigure.exposed.FlowUIDTable
import io.hizhengfu.cloud.collection.service.CollectionActorDirectory
import io.hizhengfu.cloud.collection.domain.CollectionDutySeparationPolicy
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.exception.ForbiddenException
import io.hizhengfu.cloud.flow.dao.FlowInstances
import io.hizhengfu.cloud.flow.domain.InstanceStatus
import io.hizhengfu.cloud.flow.engine.*
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.select
import org.springframework.stereotype.Component

@Component
class CollectionFlowStartPolicy(
    private val directory: CollectionActorDirectory,
    private val scope: CollectionScopeResolver,
    private val providers: List<CollectionFlowReadSources>,
) : FlowStartPolicy {
    override fun validate(context: FlowStartContext) {
        val source = source(context.definitionKey, context.businessType) ?: return
        if (context.origin != source.startOrigin) {
            throw BadRequestException("该流程只能由原业务单据或对应后台业务发起，通用发起及子流程不能替代业务提交校验")
        }
        val key = context.businessKey?.takeIf { it.isNotBlank() }
            ?: throw BadRequestException("藏品流程缺少原业务单据编号")
        val condition = if (context.origin == FlowStartOrigin.SYSTEM) Op.TRUE else submissionCondition(source, context.initiatorId)
        requireSource(source, key, condition)
        if (FlowInstances.select(FlowInstances.id).where {
            (FlowInstances.businessType eq source.businessType) and (FlowInstances.businessKey eq key) and
                (FlowInstances.status eq InstanceStatus.RUNNING.name)
        }.limit(1).any()) throw BadRequestException("该业务已有进行中的流程，请继续办理原实例")
    }

    override fun validateInitiatorReturn(context: FlowInitiatorContext) {
        val source = source(context.definitionKey, context.businessType) ?: return
        requireHumanSource(source)
        // 原人员资格变化不抹去审核人的退回事实，实际再次办理时重新核对当前资格。
    }

    override fun validateInitiatorTask(context: FlowInitiatorContext, assignee: String?, operatorId: String, action: String) {
        if (!requireInitiator(context, assignee, operatorId)) return
        if (action in setOf("TRANSFER", "DELEGATE"))
            throw BadRequestException("发起人变更须通过原业务正式承接或补正办理，不能直接转办或委托原申请")
    }

    override fun initiatorTaskVerifier(context: FlowInitiatorContext, assignee: String?, operatorId: String, action: String): () -> Unit {
        val source = source(context.definitionKey, context.businessType) ?: return {}
        requireHumanSource(source)
        val table = source.table as? FlowUIDTable ?: throw BadRequestException("业务未配置有效的审批记录来源")
        val key = context.businessKey ?: throw BadRequestException("藏品流程缺少原业务单据编号")
        val columns = source.taskIdentityColumns.ifEmpty { listOf(table.id) }
        fun identity(): List<Any?> = source.table.select(columns).where { source.table.id eq key }.singleOrNull()
            ?.let { row -> columns.map { row[it] } } ?: throw ForbiddenException("原业务记录不存在")
        val original = identity()
        return {
            if (identity() != original) throw BadRequestException("发起人任务办理期间原业务馆别或实物关联已变化")
            // 任务可以正常进入 PASS/REJECT；只复查当前权限、原范围及实例绑定。
            requireSource(source, key, submissionCondition(source, operatorId, requireAudit = false) and (table.flowInstanceId eq context.instanceId))
        }
    }

    override fun initiatorActionRestrictions(context: FlowInitiatorContext, assignee: String?, operatorId: String, actions: List<String>): Map<String, String> = try {
        if (!requireInitiator(context, assignee, operatorId)) emptyMap() else
            actions.filter { it in setOf("TRANSFER", "DELEGATE") }.associateWith { "发起人变更须通过原业务正式承接或补正办理，不能直接转办或委托原申请" }
    } catch (e: ForbiddenException) {
        actions.associateWith { e.message ?: "当前账号缺少原业务提交资格" }
    } catch (e: BadRequestException) {
        actions.associateWith { e.message ?: "原业务提交条件不满足" }
    }

    private fun requireInitiator(context: FlowInitiatorContext, assignee: String?, operatorId: String): Boolean {
        val source = source(context.definitionKey, context.businessType) ?: return false
        requireHumanSource(source)
        if (operatorId != context.initiatorId || assignee != context.initiatorId)
            throw ForbiddenException("须由原发起人本人核对后再次办理，不能代办或沿用已转交的发起人任务")
        val table = source.table as? FlowUIDTable ?: throw BadRequestException("业务未配置有效的审批记录来源")
        val key = context.businessKey?.takeIf(String::isNotBlank) ?: throw BadRequestException("藏品流程缺少原业务单据编号")
        requireSource(source, key, submissionCondition(source, operatorId) and (table.flowInstanceId eq context.instanceId))
        return true
    }

    private fun requireHumanSource(source: CollectionFlowReadSource) {
        if (source.startOrigin != FlowStartOrigin.BUSINESS)
            throw BadRequestException("系统自动发起的流程不能退回系统账号补正，请退回实际业务办理节点")
    }

    private fun source(definitionKey: String, businessType: String?): CollectionFlowReadSource? {
        val sources = providers.flatMap { it.sources() }
        val guarded = businessType?.startsWith("COLLECTION", ignoreCase = true) == true ||
            definitionKey.startsWith("collection", ignoreCase = true) ||
            sources.any { it.businessType == businessType || definitionKey in it.definitionKeys }
        if (!guarded) return null
        return sources.singleOrNull { it.businessType == businessType }
            ?: throw BadRequestException("藏品流程缺少明确的业务类型，请从原业务单据提交")
    }

    private fun submissionCondition(source: CollectionFlowReadSource, initiatorId: String, requireAudit: Boolean = true): Op<Boolean> {
        val user = directory.findActive(initiatorId)
            ?: throw ForbiddenException("当前账号不可用，不能提交藏品业务审批")
        if (user.permissions.none { it in source.submitPermissions }) throw ForbiddenException("当前账号缺少该业务提交权限")
        if (source.businessType == CollectionConst.BIZ_CHANGE) CollectionDutySeparationPolicy.requireLedgerOperation(user.permissions, user.roleCodes)
        val orgs = scope.currentWritableOrgIds(user)
        val table = source.table as? FlowUIDTable ?: throw BadRequestException("业务未配置有效的审批记录来源")
        return (if (requireAudit) table.flowStatus eq EnumFlowStatus.AUDIT else Op.TRUE) and
            (if (orgs == null) Op.TRUE else (source.submitOrgCondition ?: source.orgCondition)(orgs))
    }

    private fun requireSource(source: CollectionFlowReadSource, key: String, condition: Op<Boolean>) {
        if (!source.table.select(source.table.id).where { (source.table.id eq key) and condition }.limit(1).any()) {
            throw ForbiddenException("原业务记录不存在、不在当前提交范围或未经过业务提交校验")
        }
    }
}
