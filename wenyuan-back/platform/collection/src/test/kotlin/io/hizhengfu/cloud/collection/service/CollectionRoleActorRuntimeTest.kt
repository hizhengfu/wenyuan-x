package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.EnumOrgStatus
import io.hizhengfu.cloud.common.domain.EnumContentStatus
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.flow.dao.*
import io.hizhengfu.cloud.flow.domain.*
import io.hizhengfu.cloud.flow.engine.*
import io.hizhengfu.cloud.flow.service.*
import io.hizhengfu.cloud.sys.system.dao.*
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.mock
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.support.TransactionTemplate
import org.sqlite.SQLiteDataSource
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger

/** 当前关系表、原业务馆别与实际引擎/任务服务；不装配实物盘点等业务原件回调。 */
class CollectionRoleActorRuntimeTest {
    @TempDir lateinit var dir: Path
    @Configuration(proxyBeanMethods = false) @EnableTransactionManagement(proxyTargetClass = true)
    class Transactions
    private class Runtime(val c: AnnotationConfigApplicationContext, val tx: TransactionTemplate, val staleCalls: AtomicInteger) {
        val engine = c.getBean(FlowEngine::class.java)
        val definitions = c.getBean(FlowDefinitionService::class.java)
        val instances = c.getBean(FlowInstanceService::class.java)
        val tasks = c.getBean(FlowTaskService::class.java)
        val directory = c.getBean(CollectionActorDirectory::class.java)
        val scope = c.getBean(CollectionScopeResolver::class.java)
        fun <T> db(body: () -> T): T = tx.execute { body() }
    }
    private fun fixture(body: (Runtime) -> Unit) {
        val ds = SQLiteDataSource().apply { url = "jdbc:sqlite:${dir.resolve("actors.db")}" }
        val manager = SpringTransactionManager(ds, DatabaseConfig {})
        val staleCalls = AtomicInteger()
        AnnotationConfigApplicationContext().use { c ->
            c.beanFactory.registerSingleton("transactionManager", manager)
            c.beanFactory.registerSingleton("scope", CollectionScopeResolver("multi", ""))
            c.beanFactory.registerSingleton("graphLoader", FlowGraphLoader(16, 5))
            c.beanFactory.registerSingleton("registry", mock(FlowBusinessTypeRegistry::class.java))
            c.beanFactory.registerSingleton("resolver", FlowAssigneeResolver(listOf(object : AssigneeProvider {
                override fun getUsersByRoleId(roleId: String): List<Pair<String, String>> { staleCalls.incrementAndGet(); return listOf("foreign" to "过期的全局查询结果") }
            })))
            c.register(Transactions::class.java, CollectionActorDirectory::class.java, CollectionRoleFlowActorPolicy::class.java,
                CollectionFlowDefinitionConstraint::class.java, FlowDefinitionService::class.java, FlowEngine::class.java,
                FlowInstanceService::class.java, FlowTaskService::class.java)
            c.refresh()
            val r = Runtime(c, TransactionTemplate(manager), staleCalls)
            r.db {
                actorSchema()
                SchemaUtils.create(CollectionOrgs, Inventories, Acquisitions, AcquisitionPlans, Exhibitions, Reproductions,
                    FlowDefinitions, FlowNodeDefs, FlowEdgeDefs, FlowInstances, FlowTasks, FlowInstanceLogs, FlowCCs)
                CollectionOrgDao.new("a") { code = "A"; name = "甲馆"; systemGroupId = "group-a" }
                CollectionOrgDao.new("b") { code = "B"; name = "乙馆"; systemGroupId = "group-b" }
                CollectionOrgDao.new("child") { code = "C"; name = "甲馆下属馆"; parentId = "a"; systemGroupId = "child-group" }
                listOf("group-a", "group-b").forEach { id -> GroupDao.new(id) { name = id; type = '1'; parentId = "0"; parentIds = "0," } }
                GroupDao.new("dept-a") { name = "甲馆部门"; type = '2'; parentId = "group-a"; parentIds = "0,group-a," }
                GroupDao.new("child-group") { name = "下属馆机构"; type = '1'; parentId = "group-a"; parentIds = "0,group-a," }
                GroupDao.new("child-dept") { name = "下属馆部门"; type = '2'; parentId = "child-group"; parentIds = "0,group-a,child-group," }
                person("current", "dept-a"); person("foreign", "group-b"); person("manager", "group-b")
                listOf(CollectionConst.ROLE_STORAGE_HEAD, CollectionConst.ROLE_APPRAISAL_COMMITTEE, CollectionConst.ROLE_VICE_DIRECTOR).forEach { role ->
                    actorRole("current", role); actorRole("foreign", role)
                }
                actorRole("manager", "MANAGER", setOf("FLOW_TASK_ADMIN_COMPLETE"))
                InventoryDao.new("record") { orgId = "a"; batchName = "真实甲馆盘点" }
            }
            assertEquals(1, r.engine.actorPolicies.size)
            body(r)
        }
    }
    private fun person(id: String, group: String?) = UserDao.new(id) { name = id; loginName = id; password = "not-read-by-directory"; enabled = true; groupId = group }
    private fun publish(r: Runtime, key: String = CollectionConst.FLOW_INVENTORY, transfer: Boolean = false, sequential: Boolean = false, save: Boolean = false) {
        val mapper = JsonUtils.mapper
        var model = javaClass.classLoader.getResourceAsStream("flow/$key.json")!!.use { mapper.readTree(it) }.path("model").let {
            r.db { mapper.readValue(CollectionFlowRoleConfiguration.resolveTemplateCodes(mapper.writeValueAsString(it)), FlowModel::class.java) }
        }
        if (transfer) model = model.copy(nodes = model.nodes.map { if (it.properties.nodeType == "APPROVE") it.copy(properties = it.properties.copy(actions = listOf("APPROVE", "REJECT", "TRANSFER", "DELEGATE"))) else it })
        if (save) model = model.copy(nodes = model.nodes.map { if (it.properties.nodeType == "APPROVE") it.copy(properties = it.properties.copy(actions = it.properties.actions.orEmpty() + "SAVE")) else it })
        if (sequential) model = model.copy(nodes = model.nodes.map { if (it.properties.nodeType == "APPROVE") it.copy(properties = it.properties.copy(multiInstance = true, multiInstanceType = "SEQUENTIAL")) else it })
        val id = r.definitions.create(FlowDefinitionVo(key = key, name = "任职范围核对", modelData = JsonUtils.toJson(model)), "author").id.value
        r.definitions.publish(id, "publisher")
    }
    private fun start(r: Runtime, key: String = CollectionConst.FLOW_INVENTORY, business: String? = CollectionConst.BIZ_INVENTORY, record: String? = "record") =
        r.instances.start(StartFlowRequest(definitionKey = key, title = "待办", businessType = business, businessKey = record,
            formData = mapOf("orgId" to "b", "scope" to "all")), "current", "current").id.value
    private fun task(r: Runtime, instance: String) = r.db {
        FlowTasks.selectAll().where { (FlowTasks.instanceId eq instance) and (FlowTasks.status eq TaskStatus.PENDING.name) }.single()[FlowTasks.id].value
    }
    private fun complete(r: Runtime, instance: String, operator: String = "current", action: String = "APPROVE", target: String? = null, admin: Boolean = false) =
        r.tasks.complete(CompleteTaskRequest(taskId = task(r, instance), action = action, transferTo = target, transferToName = target), operator, operator, canAdminComplete = admin)
    private fun taskState(r: Runtime, instance: String) = r.db { FlowTasks.selectAll().where { FlowTasks.instanceId eq instance }.map { row -> FlowTasks.columns.map { row[it] } } }

    @ParameterizedTest @ValueSource(strings=["APPROVE","REJECT","SAVE","TRANSFER","DELEGATE"])
    fun `all role actions roll back actual task and logs if role disappears during action`(action:String)=fixture {r ->
        publish(r,transfer=true,save=true);val id=start(r)
        r.db {person("next","dept-a");actorRole("next",CollectionConst.ROLE_STORAGE_HEAD)}
        fun state()=r.db {listOf(FlowInstances,FlowTasks,FlowInstanceLogs).map {t->t.selectAll().map {row->t.columns.map {row[it]}}}}
        val before=state();val logs=r.db {FlowInstanceLogDao.count()};var reached=false
        r.engine.actorPolicies=listOf(object:FlowActorPolicy {
            override fun taskVerifier(context:FlowActorContext,node:FlowNodeSnapshot,assignee:String?,operatorId:String,action:String,transferTo:String?):()->Unit = {
                reached=true;assertTrue(FlowInstanceLogDao.count()>logs)
            }
        })+r.engine.actorPolicies
        r.engine.formPolicies=r.engine.formPolicies+object:FlowFormPolicy {
            override fun protectedFields(context:FlowActorContext):Set<String> {
                RoleUsers.deleteWhere {RoleUsers.userId eq "current"};return emptySet()
            }
        }
        assertThrows(BadRequestException::class.java){complete(r,id,action=action,target=if(action in setOf("TRANSFER","DELEGATE"))"next" else null)}
        assertTrue(reached);assertEquals(before,state())
    }

    @ParameterizedTest @ValueSource(strings=["TRANSFER","DELEGATE"])
    fun `role transfer verifies recipient again after creating replacement task`(action:String)=fixture {r ->
        publish(r,transfer=true);val id=start(r)
        r.db {person("next","dept-a");actorRole("next",CollectionConst.ROLE_STORAGE_HEAD)}
        val before=taskState(r,id);var returned=false
        r.c.addApplicationListener(org.springframework.context.ApplicationListener<org.springframework.context.ApplicationEvent> {event ->
            val payload=(event as? org.springframework.context.PayloadApplicationEvent<*>)?.payload
            if(payload is FlowTaskCompletedEvent && payload.instanceId==id) {returned=true;UserDao["next"].enabled=false}
        })
        assertThrows(BadRequestException::class.java){complete(r,id,action=action,target="next")}
        assertTrue(returned);assertEquals(before,taskState(r,id))
    }

    @Test fun `role action keeps original museum even if operator remains qualified in new museum`()=fixture {r ->
        publish(r,save=true);val id=start(r)
        r.engine.formPolicies=r.engine.formPolicies+object:FlowFormPolicy {
            override fun protectedFields(context:FlowActorContext):Set<String> {
                InventoryDao["record"].orgId="b";UserDao["current"].groupId="group-b";return emptySet()
            }
        }
        val before=taskState(r,id)
        val failure=assertThrows(BadRequestException::class.java){complete(r,id,action="SAVE")}
        assertTrue(failure.message!!.contains("原审批馆"));assertEquals(before,taskState(r,id));assertEquals("a",r.db {InventoryDao["record"].orgId})
    }

    @Test fun `role action preview keeps qualified transfer and does not grant administrator personal approval`()=fixture {r->
        publish(r,transfer=true);val id=start(r);val task=task(r,id)
        fun raw()=r.db {listOf(FlowInstances,FlowTasks,FlowInstanceLogs).map {t->t.selectAll().map {row->t.columns.map {row[it]}}}}
        val before=raw();val alone=r.tasks.getTaskDetail(task,"current")!!
        assertEquals(setOf("APPROVE","REJECT"),alone.availableActions!!.toSet());assertEquals(before,raw())
        r.db {person("replacement","dept-a");actorRole("replacement",CollectionConst.ROLE_STORAGE_HEAD)}
        val withTarget=raw()
        assertEquals(setOf("APPROVE","REJECT","TRANSFER","DELEGATE"),r.tasks.getTaskDetail(task,"current")!!.availableActions!!.toSet())
        assertEquals(listOf("TRANSFER"),r.tasks.getTaskDetail(task,"manager",isAdmin=true,canAdminComplete=true)!!.availableActions)
        assertEquals(emptyList<String>(),r.tasks.getTaskDetail(task,"manager",isAdmin=true,canAdminComplete=false)!!.availableActions)
        assertEquals(withTarget,raw())
        r.db {Users.update({Users.id eq "current"}){it[enabled]=false}}
        assertEquals(emptyList<String>(),r.tasks.getTaskDetail(task,"current")!!.availableActions)
        complete(r,id,"manager","TRANSFER","replacement",admin=true);complete(r,id,"replacement")
        assertEquals("COMPLETED",r.db {FlowInstanceDao[id].status})
    }

    @ParameterizedTest @ValueSource(strings = ["collection_acquisition_2026", "collection_acquisition_plan", "collection_exhibition", "collection_inventory", "collection_reproduction"])
    fun `all default role businesses use original record museum and ignore global cached members and form museum`(key: String) = fixture { r ->
        val business = r.db {
            when (key) {
                CollectionConst.FLOW_ACQUISITION -> { AcquisitionDao.new("record") { orgId = "a"; name = "征集原件" }; CollectionConst.BIZ_ACQUISITION }
                CollectionConst.FLOW_ACQUISITION_PLAN -> { AcquisitionPlanDao.new("record") { orgId = "a"; title = "征集计划"; year = 2026 }; CollectionConst.BIZ_ACQUISITION_PLAN }
                CollectionConst.FLOW_EXHIBITION -> { ExhibitionDao.new("record") { orgId = "a"; title = "展览" }; CollectionConst.BIZ_EXHIBITION }
                CollectionConst.FLOW_REPRODUCTION -> { ReproductionDao.new("record") { orgId = "a"; collectionId = "object" }; CollectionConst.BIZ_REPRODUCTION }
                else -> CollectionConst.BIZ_INVENTORY
            }
        }
        publish(r, key); val instance = start(r, key, business)
        repeat(CollectionFlowDefinitionConstraint().contracts.getValue(key).stages.size) {
            assertEquals("current", r.db { FlowTaskDao[task(r, instance)].assignee })
            complete(r, instance)
        }
        assertEquals(InstanceStatus.COMPLETED.name, r.db { FlowInstanceDao[instance].status })
        assertEquals(0, r.staleCalls.get())
    }

    @ParameterizedTest @ValueSource(strings = ["disabled", "deleted", "locked", "account-expired", "credentials-expired", "expired-time", "role-removed", "role-disabled", "moved", "group-disabled", "group-deleted", "museum-disabled"])
    fun `current qualification is rechecked after dispatch and failed action rolls back optimistic version`(change: String) = fixture { r ->
        publish(r); val instance = start(r)
        r.db { assertNotNull(r.directory.findActive("current")) }
        val before = taskState(r, instance)
        r.db {
            when (change) {
                "disabled" -> Users.update({ Users.id eq "current" }) { it[enabled] = false }
                "deleted" -> Users.update({ Users.id eq "current" }) { it[status] = EnumContentStatus.DELETE }
                "locked" -> Users.update({ Users.id eq "current" }) { it[accountNonLocked] = false }
                "account-expired" -> Users.update({ Users.id eq "current" }) { it[accountNonExpired] = false }
                "credentials-expired" -> Users.update({ Users.id eq "current" }) { it[credentialsNonExpired] = false }
                "expired-time" -> Users.update({ Users.id eq "current" }) { it[expireTime] = LocalDateTime.now().minusMinutes(1) }
                "role-removed" -> RoleUsers.deleteWhere { userId eq "current" }
                "role-disabled" -> Roles.update({ Roles.code eq CollectionConst.ROLE_STORAGE_HEAD }) { it[enabled] = false }
                "moved" -> Users.update({ Users.id eq "current" }) { it[groupId] = "group-b" }
                "group-disabled" -> Groups.update({ Groups.id eq "dept-a" }) { it[enabled] = false }
                "group-deleted" -> Groups.deleteWhere { id eq "dept-a" }
                else -> CollectionOrgs.update({ CollectionOrgs.id eq "a" }) { it[orgStatus] = EnumOrgStatus.DISABLED }
            }
        }
        assertThrows(BadRequestException::class.java) { complete(r, instance) }
        assertEquals(before, taskState(r, instance))
        assertEquals(InstanceStatus.RUNNING.name, r.db { FlowInstanceDao[instance].status })
        assertEquals(0, r.staleCalls.get())
    }

    @Test fun `no current museum member fails before instance creation without falling back to stale global result`() = fixture { r ->
        publish(r)
        r.db { RoleUsers.deleteWhere { userId eq "current" } }
        assertThrows(BadRequestException::class.java) { start(r) }
        assertEquals(0L, r.db { FlowInstances.selectAll().count() }); assertEquals(0L, r.db { FlowTasks.selectAll().count() })
        assertEquals(0, r.staleCalls.get())
    }

    @Test fun `administrator cannot substitute approval but can explicitly transfer an enabled transfer node to a qualified member`() = fixture { r ->
        publish(r, transfer = true); val instance = start(r)
        val before = taskState(r, instance)
        assertThrows(BadRequestException::class.java) { complete(r, instance, "manager", admin = true) }
        assertEquals(before, taskState(r, instance))
        r.db { person("replacement", "dept-a"); actorRole("replacement", CollectionConst.ROLE_STORAGE_HEAD); Users.update({ Users.id eq "current" }) { it[enabled] = false } }
        complete(r, instance, "manager", "TRANSFER", "replacement", admin = true)
        assertEquals("replacement", r.db { FlowTaskDao[task(r, instance)].assignee })
        complete(r, instance, "replacement")
        assertEquals(InstanceStatus.COMPLETED.name, r.db { FlowInstanceDao[instance].status })
    }

    @ParameterizedTest @ValueSource(strings = ["foreign", "manager"])
    fun `transfer cannot assign another museum or an unqualified administrator`(target: String) = fixture { r ->
        publish(r, transfer = true); val instance = start(r); val before = taskState(r, instance)
        assertThrows(BadRequestException::class.java) { complete(r, instance, action = "TRANSFER", target = target) }
        assertEquals(before, taskState(r, instance))
    }

    @Test fun `revoked administrator permission cannot be replaced by an old API admin flag`() = fixture { r ->
        publish(r, transfer = true); val instance = start(r)
        r.db { person("replacement", "dept-a"); actorRole("replacement", CollectionConst.ROLE_STORAGE_HEAD); RoleUsers.deleteWhere { userId eq "manager" } }
        assertThrows(BadRequestException::class.java) { complete(r, instance, "manager", "TRANSFER", "replacement", admin = true) }
        assertEquals("current", r.db { FlowTaskDao[task(r, instance)].assignee })
    }

    @ParameterizedTest @ValueSource(strings = ["missing-business", "missing-record", "deleted-record"])
    fun `missing authoritative business cannot be replaced by editable form data`(condition: String) = fixture { r ->
        publish(r)
        if (condition == "deleted-record") r.db { Inventories.deleteAll() }
        assertThrows(BadRequestException::class.java) { start(r, business = if (condition == "missing-business") null else CollectionConst.BIZ_INVENTORY, record = if (condition == "missing-record") null else "record") }
        assertEquals(0L, r.db { FlowInstances.selectAll().count() })
    }

    @Test fun `role and secondary group changes are read again without login or cache eviction`() = fixture { r ->
        r.db {
            val role = Roles.selectAll().where { Roles.code eq CollectionConst.ROLE_STORAGE_HEAD }.single()[Roles.id].value
            assertEquals(listOf("current"), r.directory.roleMembers(listOf(role), "a").map { it.first })
            Users.update({ Users.id eq "foreign" }) { it[groupId] = null }
            UserGroups.insert { it[userId] = "foreign"; it[groupId] = "dept-a" }
            assertEquals(setOf("current", "foreign"), r.directory.roleMembers(listOf(role), "a").map { it.first }.toSet())
            UserGroups.deleteWhere { userId eq "foreign" }
            assertEquals(listOf("current"), r.directory.roleMembers(listOf(role), "a").map { it.first })
        }
    }

    @Test fun `actual duties never inherit administrator or parent museum write scope even in single mode`() = fixture { r ->
        r.db {
            val role = CollectionConst.ROLE_STORAGE_HEAD
            val checker = CollectionResponsibleUserService(r.directory, r.scope)
            assertDoesNotThrow { checker.requireRole("current", "a", role) }
            assertThrows(BadRequestException::class.java) { checker.requireRole("current", "child", role) }
            Roles.insert { it[id] = io.hizhengfu.cloud.common.domain.EnumRole.ADMIN.value; it[code] = "ADMIN"; it[name] = "管理员" }
            actorRole("foreign", "ADMIN", setOf("ADMIN"))
            assertNull(r.scope.writableOrgIds(r.directory.requireActive("foreign")))
            assertThrows(BadRequestException::class.java) { checker.requireRole("foreign", "a", role) }
            val single = CollectionScopeResolver("single", "a")
            val singleChecker = CollectionResponsibleUserService(CollectionActorDirectory(single), single)
            assertThrows(BadRequestException::class.java) { singleChecker.requireRole("foreign", "a", role) }
            person("child-user", "child-dept"); actorRole("child-user", role)
            assertDoesNotThrow { checker.requireRole("child-user", "child", role) }
            assertThrows(BadRequestException::class.java) { checker.requireRole("child-user", "a", role) }
        }
    }

    @Test fun `external experts cannot be current department members of the same museum`() = fixture { r ->
        r.db {
            val checker = CollectionResponsibleUserService(r.directory, r.scope)
            assertThrows(BadRequestException::class.java) { checker.requireExternalToMuseum("current", "a") }
            assertDoesNotThrow { checker.requireExternalToMuseum("foreign", "a") }
        }
    }

    @Test fun `real role removal and additions do not change the original sequential seats`() = fixture { r ->
        r.db { listOf("second", "third").forEach { person(it, "dept-a"); actorRole(it, CollectionConst.ROLE_STORAGE_HEAD) } }
        publish(r, sequential = true); val instance = start(r)
        complete(r, instance)
        r.db {
            RoleUsers.deleteWhere { userId eq "current" }
            person("aaa-new", "dept-a"); actorRole("aaa-new", CollectionConst.ROLE_STORAGE_HEAD)
        }
        complete(r, instance, "second")
        assertEquals("third", r.db { FlowTaskDao[task(r, instance)].assignee })
        assertEquals(InstanceStatus.RUNNING.name, r.db { FlowInstanceDao[instance].status })
        complete(r, instance, "third")
        assertEquals(InstanceStatus.COMPLETED.name, r.db { FlowInstanceDao[instance].status })
        assertEquals(listOf("current", "second", "third"), r.db { FlowTaskDao.find { FlowTasks.instanceId eq instance }.single { it.sequentialRoster != null }.sequentialRoster!!.map { it.userId } })
        assertEquals(0L, r.db { FlowTasks.selectAll().where { (FlowTasks.instanceId eq instance) and (FlowTasks.assignee eq "aaa-new") }.count() })
    }

    @ParameterizedTest @ValueSource(strings = ["disabled", "moved", "role-removed"])
    fun `unavailable later person remains an uncompleted original seat and a qualified replacement continues it`(change: String) = fixture { r ->
        r.db { listOf("second", "third").forEach { person(it, "dept-a"); actorRole(it, CollectionConst.ROLE_STORAGE_HEAD) } }
        publish(r, transfer = true, sequential = true); val instance = start(r)
        complete(r, instance)
        r.db {
            when (change) {
                "disabled" -> UserDao["third"].enabled = false
                "moved" -> UserDao["third"].groupId = "group-b"
                else -> RoleUsers.deleteWhere { userId eq "third" }
            }
        }
        complete(r, instance, "second")
        val before = taskState(r, instance)
        assertThrows(BadRequestException::class.java) { complete(r, instance, "third") }
        assertEquals(before, taskState(r, instance))
        assertThrows(BadRequestException::class.java) { complete(r, instance, "manager", admin = true) }
        assertThrows(BadRequestException::class.java) { complete(r, instance, "manager", "TRANSFER", "second", admin = true) }
        r.db { person("replacement", "dept-a"); actorRole("replacement", CollectionConst.ROLE_STORAGE_HEAD) }
        complete(r, instance, "manager", "TRANSFER", "replacement", admin = true)
        assertEquals(2, r.db { FlowTaskDao[task(r, instance)].sequentialIndex })
        complete(r, instance, "replacement")
        assertEquals(InstanceStatus.COMPLETED.name, r.db { FlowInstanceDao[instance].status })
        assertEquals(listOf("current", "second", "third"), r.db { FlowTaskDao.find { FlowTasks.instanceId eq instance }.single { it.sequentialRoster != null }.sequentialRoster!!.map { it.userId } })
    }
}
