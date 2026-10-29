package io.hizhengfu.cloud.collection.install

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionFlowDefinitionConstraint
import io.hizhengfu.cloud.collection.common.CollectionFlowFormPolicy
import io.hizhengfu.cloud.collection.common.CollectionFlowImporter
import io.hizhengfu.cloud.collection.common.CollectionFlowRoleConfiguration
import io.hizhengfu.cloud.collection.diagnostic.CollectionRequiredFlowDiagnosticCheck
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.flow.dao.*
import io.hizhengfu.cloud.flow.domain.*
import io.hizhengfu.cloud.flow.engine.*
import io.hizhengfu.cloud.flow.service.FlowDefinitionService
import io.hizhengfu.cloud.flow.service.FlowInstanceService
import io.hizhengfu.cloud.flow.service.FlowApprovalHistory
import io.hizhengfu.cloud.flow.service.FlowTaskService
import io.hizhengfu.cloud.flow.service.FlowBusinessTypeRegistry
import io.hizhengfu.cloud.sys.diagnostic.domain.EnumDeploymentCheckState
import io.hizhengfu.cloud.sys.system.dao.RoleUsers
import io.hizhengfu.cloud.sys.system.dao.Roles
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.support.TransactionTemplate
import org.sqlite.SQLiteDataSource
import java.nio.file.Path

/** 实际发布、图持久化、事务及引擎派单；不代替业务任职、行政原件或真实人员签署验收。 */
class CollectionFlowRuntimeContractTest {
    @TempDir lateinit var dir: Path

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    class Transactions

    private class Runtime(val tx: TransactionTemplate, val definitions: FlowDefinitionService, val instances: FlowInstanceService, val engine: FlowEngine, val loader: FlowGraphLoader) {
        fun <T : Any> db(block: () -> T): T = tx.execute { block() }
    }

    private fun fixture(block: (Runtime) -> Unit) {
        val ds = SQLiteDataSource().apply { url = "jdbc:sqlite:${dir.resolve("contracts.db")}" }
        val manager = SpringTransactionManager(ds, DatabaseConfig {})
        val template = TransactionTemplate(manager)
        AnnotationConfigApplicationContext().use { context ->
            context.beanFactory.registerSingleton("transactionManager", manager)
            context.beanFactory.registerSingleton("graphLoader", FlowGraphLoader(32, 5))
            context.beanFactory.registerSingleton("assigneeResolver", FlowAssigneeResolver(listOf(object : AssigneeProvider {
                override fun getUsersByRoleId(roleId: String) = RoleUsers.selectAll().where { RoleUsers.roleId eq roleId }.map { it[RoleUsers.userId] to it[RoleUsers.userId] }
            })))
            context.register(Transactions::class.java, CollectionFlowDefinitionConstraint::class.java, CollectionFlowFormPolicy::class.java, FlowDefinitionService::class.java, FlowEngine::class.java, FlowInstanceService::class.java)
            context.refresh()
            template.executeWithoutResult { SchemaUtils.create(FlowDefinitions, FlowNodeDefs, FlowEdgeDefs, FlowInstances, FlowTasks, FlowInstanceLogs, FlowCCs, RoleUsers); seedFlowRoles() }
            val r = Runtime(template, context.getBean(FlowDefinitionService::class.java), context.getBean(FlowInstanceService::class.java), context.getBean(FlowEngine::class.java), context.getBean(FlowGraphLoader::class.java))
            assertEquals(1, r.definitions.definitionConstraints.size)
            assertEquals(1, r.engine.definitionConstraints.size)
            block(r)
        }
    }

    private fun model(key: String): FlowModel {
        val mapper = JsonUtils.mapper
        val root = javaClass.classLoader.getResourceAsStream("flow/$key.json")!!.use { mapper.readTree(it) }
        return mapper.readValue(mapper.writeValueAsString(root.path("model")), FlowModel::class.java)
    }
    private fun draft(r: Runtime, key: String, graph: FlowModel = model(key)) =
        r.definitions.create(FlowDefinitionVo(key = key, name = "馆方核对版本", modelData = JsonUtils.toJson(graph)), "author").id.value
    private fun publish(r: Runtime, key: String, graph: FlowModel = model(key)): String = draft(r, key, graph).also { r.definitions.publish(it, "publisher") }
    private fun start(r: Runtime, key: String, selected: Map<String, List<String>>, form: Map<String, Any?> = emptyMap(), businessType: String? = null): String =
        r.instances.start(StartFlowRequest(definitionKey = key, title = "测试单据", businessType = businessType, businessKey = "test-business", formData = form, selfSelectAssignees = selected), "initiator", "发起人员").id.value
    private fun pending(r: Runtime, instance: String) = r.db {
        FlowTasks.selectAll().where { (FlowTasks.instanceId eq instance) and (FlowTasks.status eq TaskStatus.PENDING.name) }.map { it[FlowTasks.nodeKey] to it[FlowTasks.assignee] }.toSet()
    }
    private fun approve(r: Runtime, instance: String, user: String) = r.db {
        val task = FlowTaskDao.find { (FlowTasks.instanceId eq instance) and (FlowTasks.assignee eq user) and (FlowTasks.status eq TaskStatus.PENDING.name) }.single()
        r.engine.complete(task, user, user, "APPROVE", "本人核对意见")
    }
    private fun definitionSnapshot(r: Runtime) = r.db { FlowDefinitions.selectAll().map { listOf(it[FlowDefinitions.id].value, it[FlowDefinitions.status].name, it[FlowDefinitions.version].toString(), it[FlowDefinitions.modelData]) }.toSet() }

    private fun runtimeSnapshot(r: Runtime) = r.db { listOf(FlowInstances, FlowTasks, FlowInstanceLogs, FlowCCs).map { table ->
        table.selectAll().orderBy(table.id).map { row -> table.columns.map { row[it] } }
    } }

    @ParameterizedTest @CsvSource(
        "collection_acquisition_2026,dossier", "collection_acquisition_plan,budget", "collection_exhibition,placements",
        "collection_inventory,result", "collection_reproduction,purpose", "collection_change,content",
        "collection_deregister_2026,experts", "collection_loan_2026,dossier", "collection_requisition_2026,dossier",
        "collection_return_2026,inspection", "collection_appraisal_2026,members", "collection_transfer_2026,dossier",
        "collection_sampling_2026,plan", "collection_digital_acquisition_2026,digitalPlan", "collection_reproduction_2026,plan",
        "collection_warehouse_entry_2026,requiresDirector",
    )
    fun `returned initiator cannot revise application fields but configured workflow supplements and full review still work`(key: String, field: String) = fixture { r ->
        (0..2).forEach { assignRole(r, it) }
        val contract = CollectionFlowDefinitionConstraint().contracts.getValue(key)
        val first = contract.stages.first().key
        val base = roleModel(r, key)
        val originalValue: Any = if (field == "requiresDirector") true else mapOf("text" to "原申请", "count" to 1, "files" to listOf("原件甲", "原件乙"))
        val original = mapOf(field to originalValue, "requiresDirector" to true)
        publish(r, key, base.copy(nodes = base.nodes.map { n -> when {
            n.properties.nodeType == "START" -> n.copy(properties = n.properties.copy(actions = listOf("APPROVE", "SAVE"),
                fieldPerms = listOf(FieldPermItem(field, perm = "REQUIRED"), FieldPermItem("customNote", perm = "EDITABLE"))))
            n.properties.nodeKey == first -> n.copy(properties = n.properties.copy(rejectType = "BACK_TO_START"))
            else -> n
        } }))
        val people = contract.stages.filter { it.assigneeType == AssigneeType.SELF_SELECT }.associate { it.key to listOf("person-${it.key}") }
        val instance = start(r, key, people, original)
        val initial = pending(r, instance).single()
        r.db {
            val task = FlowTaskDao.find { (FlowTasks.instanceId eq instance) and (FlowTasks.status eq "PENDING") }.single()
            r.engine.complete(task, initial.second!!, "实际审核人", "REJECT", "退回重新核对")
        }
        val tasks = FlowTaskService(r.engine, r.loader, FlowBusinessTypeRegistry())
        val returned = r.db { FlowTaskDao.find { (FlowTasks.instanceId eq instance) and (FlowTasks.status eq "PENDING") }.single().id.value }
        val detail = r.db { tasks.getTaskDetail(returned, "initiator")!! }
        assertEquals("READONLY", detail.fieldPerms!!.single { it.field == field }.perm)
        assertEquals("EDITABLE", detail.fieldPerms!!.single { it.field == "customNote" }.perm)
        assertNotNull(detail.businessFormNotice)
        val before = runtimeSnapshot(r)
        val edits = listOf(mapOf(field to "改变材料"), mapOf(field to null), mapOf("$field.changed" to true), mapOf("orgId" to "另一馆"))
        listOf("APPROVE", "SAVE").forEach { action -> edits.forEach { edit ->
            assertThrows(BadRequestException::class.java) { r.db { tasks.complete(CompleteTaskRequest(returned, action, formData = edit), "initiator", "发起人员") } }
            assertEquals(before, runtimeSnapshot(r))
        } }
        r.db { tasks.complete(CompleteTaskRequest(returned, "SAVE", formData = detail.formData!! + mapOf("customNote" to "流程补充说明")), "initiator", "发起人员") }
        assertEquals("流程补充说明", r.db { requireNotNull(FlowInstanceDao[instance].formData!!["customNote"]) })
        assertEquals(setOf("customNote"), r.db { FlowTaskDao[returned].formData!!.keys })
        r.db { tasks.complete(CompleteTaskRequest(returned, "APPROVE", formData = original + mapOf("customNote" to "核对后再次提交")), "initiator", "发起人员") }
        contract.stages.forEach { stage ->
            val current = pending(r, instance).single(); assertEquals(stage.key, current.first); approve(r, instance, current.second!!)
        }
        assertEquals("COMPLETED", r.db { FlowInstanceDao[instance].status })
        assertEquals(JsonUtils.toJson(originalValue), r.db { requireNotNull(JsonUtils.toJson(FlowInstanceDao[instance].formData!![field])) })
        assertEquals("核对后再次提交", r.db { requireNotNull(FlowInstanceDao[instance].formData!!["customNote"]) })
    }

    @ParameterizedTest @ValueSource(strings = ["collection_acquisition_2026", "collection_acquisition_plan", "collection_exhibition", "collection_inventory", "collection_reproduction", "collection_change", "collection_deregister_2026", "collection_loan_2026", "collection_requisition_2026", "collection_return_2026", "collection_appraisal_2026", "collection_transfer_2026", "collection_sampling_2026", "collection_digital_acquisition_2026", "collection_reproduction_2026", "collection_warehouse_entry_2026"])
    fun `all protected contracts reject bypass reentry and preserve full new approval rounds`(key: String) = fixture { r ->
        (0..2).forEach { assignRole(r, it) }
        val contract = CollectionFlowDefinitionConstraint().contracts.getValue(key)
        val base = roleModel(r, key)
        val end = base.nodes.single { it.properties.nodeType == "END" }.id
        val tail = FlowModelNode("tail", "bpmn:userTask", properties = FlowNodeProperties(nodeKey = "tail", nodeType = "HANDLE", name = "馆方收尾", assigneeType = "FIXED_USER", assignees = listOf("tail-person"), fieldPerms = listOf(FieldPermItem("*", perm = "READONLY"))))
        val graph = base.copy(nodes = base.nodes + tail, edges = base.edges.map { if (it.targetNodeId == end) it.copy(targetNodeId = "tail") else it } + FlowModelEdge("tail-end", "tail", end))
        publish(r, key, graph)
        val people = contract.stages.filter { it.assigneeType == AssigneeType.SELF_SELECT }.associate { it.key to listOf("person-${it.key}") }
        val instance = start(r, key, people, mapOf("requiresDirector" to true))
        val before = runtimeSnapshot(r)
        assertThrows(BadRequestException::class.java) { r.db { r.engine.jumpToNode(FlowInstanceDao[instance], "tail", "admin", "管理员") } }
        assertEquals(before, runtimeSnapshot(r))
        r.db { r.engine.cancel(FlowInstanceDao[instance], "initiator", "发起人员") }
        val canceled = runtimeSnapshot(r)
        assertThrows(BadRequestException::class.java) { r.db { r.engine.reactivateToNode(FlowInstanceDao[instance], "tail", "admin", "管理员") } }
        assertEquals(canceled, runtimeSnapshot(r))
        r.db { r.engine.reactivateToNode(FlowInstanceDao[instance], contract.stages.first().key, "admin", "管理员") }
        fun completeStages() { contract.stages.forEach { stage ->
            val task = pending(r, instance).single(); assertEquals(stage.key, task.first); approve(r, instance, task.second!!)
        } }
        completeStages()
        val old = r.db { FlowApprovalHistory.current(instance).map { it.id.value } }
        assertEquals(contract.stages.size, old.size)
        r.db { r.engine.jumpToNode(FlowInstanceDao[instance], contract.stages.first().key, "admin", "管理员") }
        assertTrue(r.db { FlowApprovalHistory.current(instance).isEmpty() })
        completeStages(); approve(r, instance, "tail-person")
        assertEquals(InstanceStatus.COMPLETED.name, r.db { FlowInstanceDao[instance].status })
        assertTrue(r.db { FlowApprovalHistory.current(instance).none { it.id.value in old } })
        assertEquals(old.size.toLong(), r.db { FlowInstanceLogs.selectAll().where { FlowInstanceLogs.id inList old }.count() })
    }

    @ParameterizedTest @ValueSource(strings = ["BACK_TO_PREV", "BACK_TO_NODE", "BACK_TO_START"])
    fun `return path must repeat all required stages and old opinions stay historical`(reject: String) = fixture { r ->
        (0..2).forEach { assignRole(r, it) }
        val key = CollectionConst.FLOW_ACQUISITION
        val base = roleModel(r, key)
        publish(r, key, base.copy(nodes = base.nodes.map { if (it.properties.nodeKey == "approve") it.copy(properties = it.properties.copy(rejectType = reject, rejectNodeKey = "review")) else it }))
        val instance = start(r, key, emptyMap())
        approve(r, instance, "reviewer-0"); approve(r, instance, "reviewer-1")
        val before = runtimeSnapshot(r)
        val rejectAction = { r.db {
            val task = FlowTaskDao.find { (FlowTasks.instanceId eq instance) and (FlowTasks.status eq TaskStatus.PENDING.name) }.single()
            r.engine.complete(task, "reviewer-2", "负责人", "REJECT", "实际退回意见")
        } }
        if (reject != "BACK_TO_START") {
            assertThrows(BadRequestException::class.java) { rejectAction() }
            assertEquals(before, runtimeSnapshot(r))
        } else {
            rejectAction()
            assertTrue(r.db { FlowApprovalHistory.current(instance).isEmpty() })
            approve(r, instance, "initiator")
            (0..2).forEach { approve(r, instance, "reviewer-$it") }
            assertEquals(InstanceStatus.COMPLETED.name, r.db { FlowInstanceDao[instance].status })
            assertEquals(listOf("appraise", "review", "approve"), r.db { FlowApprovalHistory.current(instance).mapNotNull { it.nodeKey }.filter { it != "start" } })
        }
    }

    private fun assignRole(r: Runtime, role: Int, user: String = "reviewer-$role") = r.db {
        RoleUsers.insert { it[roleId] = "museum-role-$role"; it[userId] = user }
    }

    private fun roleModel(r: Runtime, key: String) = r.db {
        JsonUtils.mapper.readValue(CollectionFlowRoleConfiguration.resolveTemplateCodes(JsonUtils.mapper.writeValueAsString(model(key))), FlowModel::class.java)
    }

    @ParameterizedTest @ValueSource(strings = ["collection_acquisition_2026", "collection_acquisition_plan", "collection_exhibition", "collection_inventory", "collection_reproduction"])
    fun `role templates use local role ids and require real tasks through every configured stage`(key: String) = fixture { r ->
        r.db { CollectionFlowImporter(r.definitions).importAll() }
        (0..2).forEach { assignRole(r, it) }
        val contract = CollectionFlowDefinitionConstraint().contracts.getValue(key)
        val instance = start(r, key, emptyMap())
        val graph = r.db { r.loader.load(FlowInstanceDao[instance].definitionId) }
        contract.stages.forEach { stage ->
            val roleId = graph.getNode(stage.key).assignees!!.single()
            assertTrue(roleId.startsWith("museum-role-"))
            val user = "reviewer-${roleId.substringAfterLast('-')}"
            assertEquals(setOf(stage.key to user), pending(r, instance))
            approve(r, instance, user)
        }
        assertEquals(InstanceStatus.COMPLETED.name, r.db { FlowInstanceDao[instance].status })
        assertEquals(0L, r.db { FlowTasks.selectAll().where { (FlowTasks.instanceId eq instance) and (FlowTasks.status eq TaskStatus.SKIPPED.name) }.count() })
    }

    @Test fun `template code lookup does not confuse an unrelated role id with the requested code`() = fixture { r ->
        r.db { Roles.insert { it[id] = org.jetbrains.exposed.v1.core.dao.id.EntityID(CollectionConst.ROLE_STORAGE_HEAD, Roles); it[code] = "OTHER_ROLE"; it[name] = "无关角色" } }
        val graph = roleModel(r, CollectionConst.FLOW_INVENTORY)
        assertEquals(listOf("museum-role-1"), graph.nodes.single { it.properties.nodeKey == "approve" }.properties.assignees)
        // 手工/历史模型不会走模板编码解析，已存在的主键仍按主键解释。
        val idModel = graph.copy(nodes = graph.nodes.map { if (it.properties.nodeKey == "approve") it.copy(properties = it.properties.copy(assignees = listOf(CollectionConst.ROLE_STORAGE_HEAD))) else it })
        publish(r, CollectionConst.FLOW_INVENTORY, idModel)
    }

    @ParameterizedTest @ValueSource(strings = ["bypass", "handle", "fixed", "code", "disabled", "auto"])
    fun `role approval contracts reject unsafe draft changes without replacing the active version`(change: String) = fixture { r ->
        val key = CollectionConst.FLOW_ACQUISITION
        val base = roleModel(r, key)
        val id = publish(r, key, base)
        val candidate = when (change) {
            "bypass" -> base.copy(edges = base.edges + FlowModelEdge("shortcut", base.nodes.first().id, base.nodes.last().id))
            "auto" -> base.copy(properties = FlowProperties(autoPassRepeat = true))
            else -> base.copy(nodes = base.nodes.map { if (it.properties.nodeKey != "review") it else it.copy(properties = when (change) {
                "handle" -> it.properties.copy(nodeType = "HANDLE")
                "fixed" -> it.properties.copy(assigneeType = "FIXED_USER", assignees = listOf("person"))
                "code" -> it.properties.copy(assignees = listOf(CollectionConst.ROLE_STORAGE_HEAD))
                else -> it.properties
            }) })
        }
        if (change == "disabled") r.db { Roles.update({ Roles.id eq "museum-role-1" }) { it[enabled] = false } }
        assertThrows(BadRequestException::class.java) { publish(r, key, candidate) }
        assertEquals(id, r.db { r.definitions.findActiveByKey(key)!!.id.value })
        assertEquals(JsonUtils.toJson(base), r.db { FlowDefinitionDao[id].modelData!! })
    }

    @Test fun `empty later role rejects before start and losing the next role rolls back approval`() = fixture { r ->
        val key = CollectionConst.FLOW_ACQUISITION
        publish(r, key, roleModel(r, key))
        assignRole(r, 0)
        assignRole(r, 1)
        assertThrows(BadRequestException::class.java) { start(r, key, emptyMap()) }
        assertEquals(listOf(0L, 0L, 0L), r.db { listOf(FlowInstances.selectAll().count(), FlowTasks.selectAll().count(), FlowInstanceLogs.selectAll().count()) })
        assignRole(r, 2)
        val instance = start(r, key, emptyMap())
        r.db { RoleUsers.deleteWhere { roleId eq "museum-role-1" } }
        val logCount = r.db { FlowInstanceLogs.selectAll().count() }
        assertThrows(BadRequestException::class.java) { approve(r, instance, "reviewer-0") }
        assertEquals(setOf("appraise" to "reviewer-0"), pending(r, instance))
        assertEquals(logCount, r.db { FlowInstanceLogs.selectAll().count() })
        assignRole(r, 1)
        approve(r, instance, "reviewer-0")
        assertEquals(setOf("review" to "reviewer-1"), pending(r, instance))
    }

    @Test fun `empty role during jump or reactivation never falls back to initiator`() = fixture { r ->
        val key = CollectionConst.FLOW_INVENTORY
        publish(r, key, roleModel(r, key))
        assignRole(r, 1)
        val instance = start(r, key, emptyMap())
        r.db { RoleUsers.deleteAll() }
        assertThrows(BadRequestException::class.java) { r.db { r.engine.jumpToNode(FlowInstanceDao[instance], "approve", "admin", "管理员") } }
        assertEquals(setOf("approve" to "reviewer-1"), pending(r, instance))
        r.db { r.engine.cancel(FlowInstanceDao[instance], "initiator", "发起人员") }
        val logs = r.db { FlowInstanceLogs.selectAll().count() }
        assertThrows(BadRequestException::class.java) { r.db { r.engine.reactivateToNode(FlowInstanceDao[instance], "approve", "admin", "管理员") } }
        assertEquals(InstanceStatus.CANCELED.name, r.db { FlowInstanceDao[instance].status })
        assertEquals(logs, r.db { FlowInstanceLogs.selectAll().count() })
        assertTrue(pending(r, instance).isEmpty())
    }

    @Test fun `same reviewer in two role pools receives one actual task`() = fixture { r ->
        val key = CollectionConst.FLOW_INVENTORY
        val base = roleModel(r, key)
        publish(r, key, base.copy(nodes = base.nodes.map { if (it.properties.nodeKey == "approve") it.copy(properties = it.properties.copy(assignees = listOf("museum-role-0", "museum-role-1"), multiInstance = true, multiInstanceType = "ALL")) else it }))
        assignRole(r, 0, "shared-reviewer"); assignRole(r, 1, "shared-reviewer")
        val instance = start(r, key, emptyMap())
        assertEquals(1L, r.db { FlowTasks.selectAll().where { FlowTasks.instanceId eq instance }.count() })
        approve(r, instance, "shared-reviewer")
        assertEquals(InstanceStatus.COMPLETED.name, r.db { FlowInstanceDao[instance].status })
    }

    @Test fun `sequential role cannot be treated as completed when the remaining pool becomes empty`() = fixture { r ->
        val key = CollectionConst.FLOW_INVENTORY
        val base = roleModel(r, key)
        publish(r, key, base.copy(nodes = base.nodes.map { if (it.properties.nodeKey == "approve") it.copy(properties = it.properties.copy(multiInstance = true, multiInstanceType = "SEQUENTIAL")) else it }))
        assignRole(r, 1, "reviewer-a"); assignRole(r, 1, "reviewer-b")
        val instance = start(r, key, emptyMap())
        val before = pending(r, instance)
        r.db { RoleUsers.deleteAll() }
        assertThrows(BadRequestException::class.java) { approve(r, instance, requireNotNull(before.single().second)) }
        assertEquals(before, pending(r, instance))
        assertEquals(InstanceStatus.RUNNING.name, r.db { FlowInstanceDao[instance].status })
    }

    @Test fun `disabling the next role blocks dispatch even if provider still returns members`() = fixture { r ->
        val key = CollectionConst.FLOW_ACQUISITION
        publish(r, key, roleModel(r, key))
        (0..2).forEach { assignRole(r, it) }
        val instance = start(r, key, emptyMap())
        r.db { Roles.update({ Roles.id eq "museum-role-1" }) { it[enabled] = false } }
        assertThrows(BadRequestException::class.java) { approve(r, instance, "reviewer-0") }
        assertEquals(setOf("appraise" to "reviewer-0"), pending(r, instance))
    }

    @Test fun `old role codes are diagnosed without rewriting the definition`() = fixture { r ->
        r.db { CollectionFlowImporter(r.definitions).importAll() }
        val key = CollectionConst.FLOW_INVENTORY
        val id = r.db { r.definitions.findActiveByKey(key)!!.id.value }
        r.db {
            FlowDefinitionDao[id].modelData = JsonUtils.toJson(model(key))
            FlowNodeDefs.update({ (FlowNodeDefs.definitionId eq id) and (FlowNodeDefs.nodeKey eq "approve") }) { it[assignees] = listOf(CollectionConst.ROLE_STORAGE_HEAD) }
        }
        val before = definitionSnapshot(r)
        r.db { CollectionFlowImporter(r.definitions).importAll() }
        val report = r.db { CollectionRequiredFlowDiagnosticCheck(r.definitions, loader = r.loader).check() }
        assertEquals(EnumDeploymentCheckState.FAIL, report.state)
        assertTrue(report.safeFacts.single { it.name == "invalidApprovalFlows" }.value.contains(key))
        assertEquals(before, definitionSnapshot(r))
        assertThrows(BadRequestException::class.java) { start(r, key, emptyMap()) }
    }

    @Test fun `all current self selected templates publish with actual runtime graph matching the design`() = fixture { r ->
        r.db { CollectionFlowImporter(r.definitions).importAll() }
        val report = r.db { CollectionRequiredFlowDiagnosticCheck(r.definitions, loader = r.loader).check() }
        assertEquals(EnumDeploymentCheckState.PASS, report.state, report.safeFacts.toString())
        assertEquals("16", report.safeFacts.single { it.name == "approvalContractFlowCount" }.value)
        assertEquals("", report.safeFacts.single { it.name == "presenceOnlyFlows" }.value)
        assertEquals(16, r.db { FlowDefinitionDao.all().count { it.key in CollectionRequiredFlowDiagnosticCheck.REQUIRED_KEYS && it.status == DefinitionStatus.ACTIVE } })
    }

    @ParameterizedTest @ValueSource(strings = ["bypass", "role", "auto", "handle", "no-reject"])
    fun `unsafe new versions are rejected before replacing the active version or changing existing instances`(change: String) = fixture { r ->
        val key = CollectionConst.FLOW_REQUISITION
        val original = publish(r, key)
        val instance = start(r, key, mapOf("department" to listOf("d"), "keeper" to listOf("k"), "leader" to listOf("l")))
        var candidate = model(key)
        candidate = when (change) {
            "bypass" -> candidate.copy(edges = candidate.edges + FlowModelEdge("bypass", "start", "end"))
            "auto" -> candidate.copy(properties = FlowProperties(autoPassRepeat = true))
            else -> candidate.copy(nodes = candidate.nodes.map { n -> if (n.properties.nodeKey != "keeper") n else n.copy(properties = when (change) {
                "role" -> n.properties.copy(assigneeType = "ROLE", assignees = listOf("ADMIN"))
                "handle" -> n.properties.copy(nodeType = "HANDLE")
                else -> n.properties.copy(actions = listOf("APPROVE"))
            }) })
        }
        val next = draft(r, key, candidate)
        val before = definitionSnapshot(r)
        assertThrows(BadRequestException::class.java) { r.definitions.publish(next, "publisher") }
        assertEquals(before, definitionSnapshot(r))
        assertEquals(original, r.db { r.definitions.findActiveByKey(key)!!.id.value })
        assertEquals(setOf("department" to "d"), pending(r, instance))
        assertEquals(original, r.db { FlowInstanceDao[instance].definitionId })
    }

    @Test fun `requisition tasks follow the three actual selected people in order`() = fixture { r ->
        val key = CollectionConst.FLOW_REQUISITION
        publish(r, key)
        val instance = start(r, key, mapOf("department" to listOf("department-user"), "keeper" to listOf("keeper-user"), "leader" to listOf("leader-user")), businessType = CollectionConst.BIZ_REQUISITION)
        listOf("department", "keeper", "leader").forEach { node ->
            assertEquals(setOf(node to "$node-user"), pending(r, instance))
            approve(r, instance, "$node-user")
        }
        assertEquals(InstanceStatus.COMPLETED.name, r.db { FlowInstanceDao[instance].status })
        assertEquals(listOf("department", "keeper", "leader"), r.db { FlowInstanceLogDao.find { (FlowInstanceLogs.instanceId eq instance) and (FlowInstanceLogs.action eq "APPROVE") }.map { it.nodeKey } })
    }

    @ParameterizedTest @ValueSource(booleans = [false, true])
    fun `warehouse routes actual shortage and ordinary entry independently`(shortage: Boolean) = fixture { r ->
        val key = CollectionConst.FLOW_WAREHOUSE_ENTRY
        publish(r, key)
        val selected = mutableMapOf("keeper" to listOf("keeper-user")).apply { if (shortage) put("leader", listOf("leader-user")) }
        val instance = start(r, key, selected, mapOf("requiresDirector" to shortage))
        approve(r, instance, "keeper-user")
        if (shortage) { assertEquals(setOf("leader" to "leader-user"), pending(r, instance)); approve(r, instance, "leader-user") }
        assertEquals(InstanceStatus.COMPLETED.name, r.db { FlowInstanceDao[instance].status })
    }

    @Test fun `missing selected signer and undefined warehouse branch fail before any instance or task is created`() = fixture { r ->
        val key = CollectionConst.FLOW_WAREHOUSE_ENTRY
        publish(r, key)
        assertThrows(BadRequestException::class.java) { start(r, key, mapOf("keeper" to listOf("k")), mapOf("requiresDirector" to true)) }
        assertThrows(BadRequestException::class.java) { start(r, key, mapOf("keeper" to listOf("k"))) }
        assertEquals(0L, r.db { FlowInstances.selectAll().count() })
        assertEquals(0L, r.db { FlowTasks.selectAll().count() })
        assertEquals(0L, r.db { FlowInstanceLogs.selectAll().count() })
    }

    @ParameterizedTest @ValueSource(strings = ["reverse", "editable", "unrestricted"])
    fun `warehouse condition cannot be reversed or edited to bypass special designation`(change: String) = fixture { r ->
        val key = CollectionConst.FLOW_WAREHOUSE_ENTRY
        val source = model(key)
        val graph = if (change == "reverse") source.copy(edges = source.edges.map { edge -> if (edge.properties.conditionType == "EXPRESSION") edge.copy(properties = edge.properties.copy(condition = "#requiresDirector == false")) else edge })
        else source.copy(nodes = source.nodes.map { node -> if (node.properties.nodeKey == "keeper") node.copy(properties = node.properties.copy(fieldPerms = if (change == "editable") listOf(FieldPermItem("requiresDirector", perm = "EDITABLE")) else null)) else node })
        assertThrows(BadRequestException::class.java) { publish(r, key, graph) }
    }

    @Test fun `appraisal unanimous and sequential votes retain all actual members and any vote is refused`() = fixture { r ->
        val key = CollectionConst.FLOW_APPRAISAL
        val graph = model(key)
        val any = graph.copy(nodes = graph.nodes.map { if (it.properties.nodeKey == "committee") it.copy(properties = it.properties.copy(multiInstanceType = "ANY")) else it })
        assertThrows(BadRequestException::class.java) { publish(r, key, any) }
        publish(r, key)
        val instance = start(r, key, mapOf("committee" to listOf("one", "two")))
        assertEquals(setOf("committee" to "one", "committee" to "two"), pending(r, instance))
        approve(r, instance, "one")
        assertEquals(InstanceStatus.RUNNING.name, r.db { FlowInstanceDao[instance].status })
        approve(r, instance, "two")
        assertEquals(InstanceStatus.COMPLETED.name, r.db { FlowInstanceDao[instance].status })
        val sequential = graph.copy(nodes = graph.nodes.map { if (it.properties.nodeKey == "committee") it.copy(properties = it.properties.copy(multiInstanceType = "SEQUENTIAL")) else it })
        assertDoesNotThrow { publish(r, key, sequential) }
    }

    @Test fun `custom intermediate work and cosmetic changes are preserved when required approvals remain`() = fixture { r ->
        val key = CollectionConst.FLOW_CHANGE
        val graph = model(key)
        val extra = FlowModelNode("museum-work", "bpmn:userTask", properties = FlowNodeProperties(nodeKey = "museum-work", nodeType = "HANDLE", name = "馆方附加核对", assigneeType = "FIXED_USER", assignees = listOf("reviewer")))
        val revised = graph.copy(nodes = graph.nodes.map { it.copy(x = it.x + 300, properties = it.properties.copy(name = "馆方显示名称")) } + extra, edges = graph.edges.filterNot { it.sourceNodeId == "keeper" && it.targetNodeId == "leader" } + listOf(FlowModelEdge("extra1", "keeper", "museum-work"), FlowModelEdge("extra2", "museum-work", "leader")))
        val id = publish(r, key, revised)
        assertEquals(JsonUtils.toJson(revised), r.db { FlowDefinitionDao[id].modelData!! })
    }

    @Test fun `old published node configuration cannot start a new business even if definition is active`() = fixture { r ->
        val key = CollectionConst.FLOW_RETURN
        val id = publish(r, key)
        r.db { FlowNodeDefs.update({ (FlowNodeDefs.definitionId eq id) and (FlowNodeDefs.nodeKey eq "keeper") }) { it[assigneeType] = "ROLE"; it[assignees] = listOf("ADMIN") } }
        assertThrows(BadRequestException::class.java) { start(r, key, mapOf("keeper" to listOf("keeper-user"))) }
        assertEquals(0L, r.db { FlowInstances.selectAll().count() })
        assertEquals(DefinitionStatus.ACTIVE, r.db { FlowDefinitionDao[id].status })
    }

    @Test fun `legacy key cannot be used to start new current business and remains available for historical review`() = fixture { r ->
        val key = "collection_return"
        val id = publish(r, key, model(CollectionConst.FLOW_RETURN))
        assertThrows(BadRequestException::class.java) { start(r, key, mapOf("keeper" to listOf("k")), businessType = CollectionConst.BIZ_RETURN) }
        assertEquals(DefinitionStatus.ACTIVE, r.db { FlowDefinitionDao[id].status })
        assertEquals(0L, r.db { FlowInstances.selectAll().count() })
    }

    @Test fun `diagnostic inspects current published rows and old running references without reading business details`() = fixture { r ->
        r.db { CollectionFlowImporter(r.definitions).importAll() }
        val key = CollectionConst.FLOW_RETURN
        val original = r.db { r.definitions.findActiveByKey(key)!!.id.value }
        val instance = start(r, key, mapOf("keeper" to listOf("secret-person")), businessType = CollectionConst.BIZ_RETURN)
        publish(r, key)
        r.db {
            FlowInstanceDao.new("legacy-instance") { definitionId = "missing-old-definition"; definitionKey = "collection_return"; definitionName = "private-title"; title = "private-title"; businessType = CollectionConst.BIZ_RETURN; businessKey = "private-business"; initiator = "secret-person"; initiatorName = "private-name"; formData = mapOf("private-original" to "secret-original") }
        }
        val before = r.db { listOf(FlowInstances.selectAll().count(), FlowTasks.selectAll().count(), FlowInstanceLogs.selectAll().count(), FlowDefinitions.selectAll().count()) }
        repeat(2) {
            val report = r.db { CollectionRequiredFlowDiagnosticCheck(r.definitions, loader = r.loader).check() }
            assertEquals(EnumDeploymentCheckState.FAIL, report.state)
            assertEquals("2", report.safeFacts.single { it.name == "olderDefinitionRunningInstances" }.value)
            assertEquals("1", report.safeFacts.single { it.name == "incompatibleRunningInstances" }.value)
            assertFalse(report.toString().contains("secret-")); assertFalse(report.toString().contains("private-"))
        }
        assertEquals(original, r.db { FlowInstanceDao[instance].definitionId })
        assertEquals(before, r.db { listOf(FlowInstances.selectAll().count(), FlowTasks.selectAll().count(), FlowInstanceLogs.selectAll().count(), FlowDefinitions.selectAll().count()) })
        val current = r.db { r.definitions.findActiveByKey(key)!!.id.value }
        r.db { r.loader.load(current) }
        r.db { FlowNodeDefs.update({ (FlowNodeDefs.definitionId eq current) and (FlowNodeDefs.nodeKey eq "keeper") }) { it[assigneeType] = "ROLE" } }
        val report = r.db { CollectionRequiredFlowDiagnosticCheck(r.definitions, loader = r.loader).check() }
        assertTrue(report.safeFacts.single { it.name == "invalidApprovalFlows" }.value.contains(key))
        assertTrue(report.safeFacts.single { it.name == "approvalContractIssues" }.value.contains("设计模型与已发布运行节点不一致"))
    }

    @Test fun `missing runtime table is a scan failure rather than zero outstanding instances`() = fixture { r ->
        r.db { CollectionFlowImporter(r.definitions).importAll(); SchemaUtils.drop(FlowInstances) }
        val report = r.db { CollectionRequiredFlowDiagnosticCheck(r.definitions, loader = r.loader).check() }
        assertEquals(EnumDeploymentCheckState.FAIL, report.state)
        assertEquals("TABLE_UNAVAILABLE", report.safeFacts.single { it.name == "runningInstanceScan" }.value)
    }

    @ParameterizedTest @ValueSource(strings = ["COMPLETED", "CANCELED", "REJECTED"])
    fun `historical definitions remain immutable and cannot be deleted after their instances end`(status: String) = fixture { r ->
        val key = CollectionConst.FLOW_RETURN
        val original = publish(r, key)
        val instance = start(r, key, mapOf("keeper" to listOf("keeper-user")))
        when (status) {
            "COMPLETED" -> approve(r, instance, "keeper-user")
            "CANCELED" -> r.instances.cancel(instance, "initiator", "发起人员")
            else -> r.db {
                val task = FlowTaskDao.find { (FlowTasks.instanceId eq instance) and (FlowTasks.status eq TaskStatus.PENDING.name) }.single()
                r.engine.complete(task, "keeper-user", "保管负责人", "REJECT", "本人拒绝意见")
            }
        }
        assertEquals(status, r.db { FlowInstanceDao[instance].status })
        val current = publish(r, key)
        val before = definitionSnapshot(r)
        val nodeCount = r.db { FlowNodeDefs.selectAll().where { FlowNodeDefs.definitionId eq original }.count() }
        assertThrows(BadRequestException::class.java) { r.definitions.delete(original) }
        assertThrows(BadRequestException::class.java) { r.definitions.publish(original, "publisher") }
        assertEquals(before, definitionSnapshot(r))
        assertEquals(nodeCount, r.db { FlowNodeDefs.selectAll().where { FlowNodeDefs.definitionId eq original }.count() })
        assertEquals(current, r.db { r.definitions.findActiveByKey(key)!!.id.value })
        val copied = r.definitions.copy(original, "author").id.value
        assertNotEquals(original, copied)
        r.definitions.publish(copied, "publisher")
        assertEquals(original, r.db { FlowInstanceDao[instance].definitionId })
        assertEquals(1, r.db { FlowDefinitionDao[original].version })
    }
}
