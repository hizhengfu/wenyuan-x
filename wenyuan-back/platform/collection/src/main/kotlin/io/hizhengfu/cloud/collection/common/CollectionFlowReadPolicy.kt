package io.hizhengfu.cloud.collection.common

import io.hizhengfu.cloud.collection.service.CollectionActorDirectory
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.flow.dao.FlowInstances
import io.hizhengfu.cloud.flow.service.FlowReadPolicy
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.select
import org.springframework.stereotype.Component

@Component
class CollectionFlowReadPolicy(
    private val directory: CollectionActorDirectory,
    private val scope: CollectionScopeResolver,
    private val providers: List<CollectionFlowReadSources>,
) : FlowReadPolicy {
    override fun condition(viewerId: String, administrative: Boolean): Op<Boolean> {
        val sources = providers.flatMap { it.sources() }
        val keys = sources.flatMap { it.definitionKeys }.toSet()
        val guarded = ((FlowInstances.businessType.isNotNull()) and (FlowInstances.businessType like "COLLECTION%")) or
            (FlowInstances.definitionKey like "collection%") or
            ((FlowInstances.businessType.isNotNull()) and (FlowInstances.businessType inList sources.map { it.businessType })) or
            (if (keys.isEmpty()) Op.FALSE else FlowInstances.definitionKey inList keys)
        val user = directory.findActive(viewerId)
        val canManage = user?.permissions?.any { p -> setOf("ADMIN", "FLOW_ALL", "FLOW_MANAGE").any { it.equals(p, true) } } == true
        val scopes = mutableMapOf<String, Set<String>?>()
        val allowed = if (user == null || (administrative && !canManage)) Op.FALSE else sources
            .filter { source -> user.permissions.any { it in source.permissions } }
            .map { source ->
                // 同次查询的同一业务域只解析一次馆别，不跨请求缓存当前授权。
                val orgs = scopes.getOrPut(source.crossOrgPermission) {
                    try {
                        scope.currentBusinessReadableOrgIds(user, source.crossOrgPermission)
                    } catch (_: BadRequestException) {
                        // 默认馆失效时关闭藏品流程读取，保留共享引擎其他业务的原有读取规则。
                        emptySet()
                    }
                }
                (FlowInstances.businessType eq source.businessType) and
                    (FlowInstances.businessKey inSubQuery source.table.select(source.table.id).where {
                        source.recordCondition and (if (orgs == null) Op.TRUE else source.orgCondition(orgs))
                    })
            }.fold<Op<Boolean>, Op<Boolean>>(Op.FALSE) { result, next -> result or next }
        // 不认识的藏品业务/缺少原记录时不猜测馆别，也不使用实例表单中的 orgId。
        return FlowInstances.id.isNotNull() and (not(guarded) or allowed)
    }
}
