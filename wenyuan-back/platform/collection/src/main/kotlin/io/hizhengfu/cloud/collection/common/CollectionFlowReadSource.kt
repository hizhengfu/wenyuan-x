package io.hizhengfu.cloud.collection.common

import io.hizhengfu.cloud.autoconfigure.exposed.UIDTable
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.Column
import io.hizhengfu.cloud.flow.engine.FlowStartOrigin

data class CollectionFlowReadSource(
    val businessType: String,
    val table: UIDTable,
    val permissions: Set<String>,
    val definitionKeys: Set<String> = emptySet(),
    val crossOrgPermission: String = CollectionConst.CROSS_ORG,
    val startOrigin: FlowStartOrigin = FlowStartOrigin.BUSINESS,
    val submitPermissions: Set<String> = emptySet(),
    val submitOrgCondition: ((Set<String>) -> Op<Boolean>)? = null,
    /** 任务动作前后须保持的原业务关联；不得使用客户端表单的馆别。 */
    val taskIdentityColumns: List<Column<*>> = emptyList(),
    /** Applies even when the viewer can read across museums. */
    val recordCondition: Op<Boolean> = Op.TRUE,
    val orgCondition: (Set<String>) -> Op<Boolean>,
)

/** 可选修复、预防保护模块各自提供权威业务表、读取及发起权限，核心不反向依赖其数据库表。 */
interface CollectionFlowReadSources { fun sources(): List<CollectionFlowReadSource> }
