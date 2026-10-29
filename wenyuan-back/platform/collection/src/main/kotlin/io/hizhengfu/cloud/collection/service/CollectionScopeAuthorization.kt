package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.flow.service.FlowUserOperation

/** Keep read-only flow inspection separate from flow mutations. */
internal fun CollectionScopeResolver.requireFlowAccess(
    orgId: String?,
    operation: FlowUserOperation,
    subject: String,
) {
    val orgIds = when (operation) {
        FlowUserOperation.FLOW_INFO -> readableOrgIds()
        else -> writableOrgIds()
    }
    OrgScopedReadPolicy.requireVisible(orgIds, orgId, subject)
}
