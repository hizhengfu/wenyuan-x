package io.hizhengfu.cloud.collection.diagnostic

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.flow.dao.*
import io.hizhengfu.cloud.flow.domain.DefinitionStatus
import io.hizhengfu.cloud.flow.domain.FlowModel
import io.hizhengfu.cloud.flow.domain.InstanceStatus
import io.hizhengfu.cloud.flow.engine.*
import io.hizhengfu.cloud.flow.service.FlowDefinitionService
import io.hizhengfu.cloud.sys.diagnostic.domain.*
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.vendors.inProperCase
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager

/** 仅返回声明标识、固定说明及数量；实例查询不读取标题、表单或自选人员字段。 */
object CollectionFlowContractReadiness {
    /** 诊断工作线程使用各自事务的元数据，避免方言共享缓存保留另一线程的 JDBC 连接。 */
    @OptIn(org.jetbrains.exposed.v1.core.InternalApi::class)
    fun tableAvailable(table: Table): Boolean {
        require(table === FlowInstances || table === FlowDefinitions)
        val (schema, names) = TransactionManager.current().connection.metadata {
            tableNamesByCurrentSchema(null)
        }
        val expected = (if (schema.isEmpty()) table.tableName else "$schema.${table.tableName}").inProperCase()
        return expected in names
    }

    fun check(
        service: FlowDefinitionService,
        loader: FlowGraphLoader,
        constraint: FlowDefinitionConstraint,
        keys: Set<String>,
        contractKeys: Set<String>,
        businessKeys: Map<String, Set<String>>,
        feature: String,
    ): DeploymentCheckOutcome {
        val active = keys.associateWith { service.findActiveByKey(it) }
        val missing = active.filterValues { it == null }.keys.sorted()
        val duplicate = keys.filter { key -> FlowDefinitionDao.find { (FlowDefinitions.key eq key) and (FlowDefinitions.status eq DefinitionStatus.ACTIVE) }.count() > 1 }
        val invalid = linkedMapOf<String, List<String>>()
        val checked = mutableMapOf<String, List<String>>()
        fun verify(definition: FlowDefinitionDao): List<String> = checked.getOrPut(definition.id.value) {
            if (definition.key !in contractKeys) emptyList()
            else publishedGraphIssues(definition, loader) { graph -> constraint.violations(definition.key, graph) }
        }
        active.forEach { (key, definition) ->
            if (definition != null) verify(definition).takeIf { it.isNotEmpty() }?.let { invalid[key] = it }
        }
        var running = 0L
        var older = 0L
        var incompatible = 0L
        val instancesAvailable = tableAvailable(FlowInstances)
        if (instancesAvailable) {
            val count = FlowInstances.id.count()
            FlowInstances.select(FlowInstances.definitionId, FlowInstances.definitionKey, FlowInstances.businessType, count)
                .where { (FlowInstances.status eq InstanceStatus.RUNNING.name) and (FlowInstances.businessType.inList(businessKeys.keys) or FlowInstances.definitionKey.inList(keys)) }
                .groupBy(FlowInstances.definitionId, FlowInstances.definitionKey, FlowInstances.businessType)
                .forEach { row ->
                    val amount = row[count]
                    running += amount
                    val key = row[FlowInstances.definitionKey]
                    val id = row[FlowInstances.definitionId]
                    val definition = FlowDefinitionDao.findById(id)
                    if (active[key]?.id?.value != id) older += amount
                    val expected = businessKeys[row[FlowInstances.businessType]]
                    if (definition == null || definition.key != key || key !in keys || expected != null && key !in expected || verify(definition).isNotEmpty()) incompatible += amount
                }
        }
        val facts = listOf(
            DeploymentSafeFact("requiredFlowCount", keys.size.toString()),
            DeploymentSafeFact("missingActiveFlows", missing.joinToString()),
            DeploymentSafeFact("duplicateActiveFlows", duplicate.joinToString()),
            DeploymentSafeFact("approvalContractFlowCount", contractKeys.size.toString()),
            DeploymentSafeFact("presenceOnlyFlows", (keys - contractKeys).sorted().joinToString()),
            DeploymentSafeFact("invalidApprovalFlows", invalid.keys.joinToString()),
            DeploymentSafeFact("approvalContractIssues", invalid.entries.joinToString("；") { (key, issues) -> "$key: ${issues.joinToString("；")}" }),
            DeploymentSafeFact("runningInstances", running.toString()),
            DeploymentSafeFact("olderDefinitionRunningInstances", older.toString()),
            DeploymentSafeFact("incompatibleRunningInstances", incompatible.toString()),
            DeploymentSafeFact("runningInstanceScan", if (instancesAvailable) "DEFINITION_REFERENCES_ONLY" else "TABLE_UNAVAILABLE"),
            DeploymentSafeFact("validationScope", "ACTIVE_PRESENCE_PUBLISHED_APPROVAL_GRAPH_AND_RUNNING_DEFINITION_REFERENCES"),
        )
        val failed = missing.isNotEmpty() || duplicate.isNotEmpty() || invalid.isNotEmpty() || incompatible > 0 || !instancesAvailable
        val remediation = if (failed || older > 0) listOf(DeploymentRemediation(EnumDeploymentRemediationType.CONFIGURATION, "保留原流程并核对当前定义及在办单据", listOf(
            "在流程定义中核对提示的业务标识、实际节点 Key、自选责任人、会签及审批路径；需要订正时新建版本，保留原模型和审计记录",
            "信息变更等业务指定的责任人节点不能被固定人员、角色池或自动跳过替代；入库的人员不足分支须保留主要负责人指定",
            "在流程实例中按业务及进行中状态核查旧版实例、单据状态、已实际实施事项与签署原件；不要直接改挂定义、补造日志或强制跳节点",
            "未实施单据按本业务允许的撤回、订正及重提办理；已实施或本业务禁止重提的单据继续原件核查及异常处理，不能套用通用重提",
            "发布后重新扫描，再由实际账号验证本人待办和业务执行；本检查不证明实际岗位、签署和行政手续已经履行",
        ))) else emptyList()
        return DeploymentCheckOutcome(
            if (failed) EnumDeploymentCheckState.FAIL else EnumDeploymentCheckState.PASS,
            if (failed) "$feature 存在活动定义、必需审批路径或在办实例引用缺口，原流程与任务保持不变"
            else "$feature 活动定义及 ${contractKeys.size} 类必需审批运行图契约通过；角色成员、旧版在办 $older 项仍须核对实际岗位和业务原件",
            if (failed) "COLLECTION_FLOW_CONTRACT_INCOMPLETE" else null,
            facts, remediation,
        )
    }

    /** 核对原设计模型与已发布运行图；供仍在办的旧版定义复用，不要求当前仍有活动定义。 */
    fun publishedGraphIssues(
        definition: FlowDefinitionDao,
        loader: FlowGraphLoader,
        violations: (FlowGraph) -> List<String>,
    ): List<String> = runCatching {
        val model = JsonUtils.mapper.readValue(definition.modelData ?: error("缺少模型"), FlowModel::class.java)
        val design = flowContractGraph(definition.id.value, model)
        val runtime = loader.loadUncached(definition.id.value)
        buildList {
            addAll(violations(runtime))
            addAll(violations(design))
            if (signature(design) != signature(runtime)) add("设计模型与已发布运行节点不一致，须保留原版并核实发布记录")
        }.distinct()
    }.getOrElse { listOf("流程模型或运行节点无法完整读取，不能认定升级就绪") }

    private fun signature(graph: FlowGraph) = Triple(
        graph.nodes.values.map { it.copy(name = "", assigneeNames = null, description = null) }.sortedBy { it.nodeKey },
        graph.outEdges.values.flatten().sortedWith(compareBy({ it.sourceNodeKey }, { it.priority }, { it.targetNodeKey }, { it.conditionType }, { it.condition ?: "" })),
        graph.properties.copy(description = null),
    )
}
