package io.hizhengfu.cloud.collection.common

import io.hizhengfu.cloud.collection.service.CollectionActorDirectory
import io.hizhengfu.cloud.flow.engine.FlowActorContext
import io.hizhengfu.cloud.flow.engine.FlowNodeSnapshot
import org.springframework.stereotype.Component

@Component
class CollectionRoleFlowActorPolicy(directory: CollectionActorDirectory) : MuseumRoleFlowActorPolicy(directory) {
    override fun applies(context: FlowActorContext) = CollectionFlowActorSources.applies(context)
    override fun orgId(context: FlowActorContext, node: FlowNodeSnapshot) = CollectionFlowActorSources.orgId(context, node)
}
