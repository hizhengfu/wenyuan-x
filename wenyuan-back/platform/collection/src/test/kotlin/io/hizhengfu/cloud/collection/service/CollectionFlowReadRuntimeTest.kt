package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.CollectionCatalogContentVo
import io.hizhengfu.cloud.common.domain.EnumContentStatus
import io.hizhengfu.cloud.common.exception.ForbiddenException
import io.hizhengfu.cloud.flow.dao.*
import io.hizhengfu.cloud.flow.api.FlowInstanceController
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
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.support.TransactionTemplate
import org.sqlite.SQLiteDataSource
import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalDateTime

/** 真实当前身份表、原业务表及 Spring 事务服务；无登录缓存、无外发通知。 */
class CollectionFlowReadRuntimeTest {
    @TempDir lateinit var dir: Path
    @Configuration(proxyBeanMethods = false) @EnableTransactionManagement(proxyTargetClass = true)
    class Transactions
    private class Runtime(val c: AnnotationConfigApplicationContext, val tx: TransactionTemplate) {
        val instances = c.getBean(FlowInstanceService::class.java)
        val tasks = c.getBean(FlowTaskService::class.java)
        val timeouts = c.getBean(FlowTimeoutService::class.java)
        val stats = c.getBean(FlowStatisticsService::class.java)
        lateinit var definition: String
        fun <T> db(body: () -> T): T = tx.execute { body() }
    }
    private class BusinessInfo(instances: FlowInstanceService) : FlowService<InventoryDao>(InventoryDao, instances) {
        override fun businessType() = CollectionConst.BIZ_INVENTORY
        override fun definitionKey() = "collection_read_fixture"
        override fun buildTitle(entity: InventoryDao) = entity.batchName ?: "盘点"
        override fun buildFormData(entity: InventoryDao) = mapOf("batchName" to entity.batchName)
    }

    private fun fixture(mode: String = "multi", backendDefault: Boolean = false, body: (Runtime) -> Unit) {
        val manager = SpringTransactionManager(SQLiteDataSource().apply { url = "jdbc:sqlite:${dir.resolve("reads.db")}" }, DatabaseConfig {})
        AnnotationConfigApplicationContext().use { c ->
            c.beanFactory.registerSingleton("transactionManager", manager)
            c.beanFactory.registerSingleton("scope", if (backendDefault) CollectionScopeResolver(mode, CollectionDefaultOrgSettings()) else CollectionScopeResolver(mode, "a"))
            c.beanFactory.registerSingleton("graphLoader", FlowGraphLoader(16, 5))
            c.beanFactory.registerSingleton("registry", mock(FlowBusinessTypeRegistry::class.java))
            c.beanFactory.registerSingleton("resolver", FlowAssigneeResolver(emptyList()))
            c.register(Transactions::class.java, CollectionActorDirectory::class.java, CoreCollectionFlowReadSources::class.java,
                CollectionFlowReadPolicy::class.java, FlowReadAccess::class.java, FlowDefinitionService::class.java,
                FlowEngine::class.java, FlowInstanceService::class.java, FlowTaskService::class.java,
                FlowTimeoutService::class.java, FlowStatisticsService::class.java)
            c.refresh()
            val r = Runtime(c, TransactionTemplate(manager))
            r.db {
                actorSchema()
                SchemaUtils.create(Configs, CollectionOrgs, *CoreCollectionFlowReadSources().sources().map { it.table }.toTypedArray(),
                    FlowDefinitions, FlowNodeDefs, FlowEdgeDefs, FlowInstances, FlowTasks, FlowInstanceLogs, FlowCCs)
                listOf("a", "b").forEach { org ->
                    CollectionOrgDao.new(org) { code = org; name = "馆$org"; systemGroupId = "group-$org" }
                    GroupDao.new("group-$org") { name = org; type = '1'; parentId = "0"; parentIds = "0," }
                    InventoryDao.new("record-$org") { orgId = org; batchName = "盘点原件$org" }
                }
                if (backendDefault) {
                    CollectionDefaultOrgSettings.seed()
                    Configs.update({ Configs.name eq CollectionDefaultOrgSettings.KEY }) { it[content] = "a" }
                }
                person("viewer", "group-a")
                actorRole("viewer", "READER", setOf(CollectionConst.SELECT))
                actorRole("viewer", "MANAGER", setOf("FLOW_MANAGE"))
            }
            val definitions = c.getBean(FlowDefinitionService::class.java)
            r.definition = definitions.create(FlowDefinitionVo(key = "collection_read_fixture", name = "读取验证", modelData = """
                {"nodes":[
                  {"id":"start","type":"start","x":0,"y":0,"properties":{"nodeType":"START"}},
                  {"id":"review","type":"approve","x":100,"y":0,"properties":{"nodeType":"APPROVE","assigneeType":"FIXED_USER","assignees":["viewer"]}},
                  {"id":"end","type":"end","x":200,"y":0,"properties":{"nodeType":"END"}}
                ],"edges":[{"id":"e1","type":"polyline","sourceNodeId":"start","targetNodeId":"review"},{"id":"e2","type":"polyline","sourceNodeId":"review","targetNodeId":"end"}]}
            """.trimIndent()), "author").id.value
            definitions.publish(r.definition, "author")
            r.db { instance(r, "a"); instance(r, "b") }
            assertEquals(1, c.getBean(FlowReadAccess::class.java).policies.size)
            body(r)
        }
    }

    private fun person(id: String, group: String?) = UserDao.new(id) {
        name = id; loginName = id; password = "unused"; enabled = true; groupId = group
    }
    private fun instance(r: Runtime, id: String, business: String? = CollectionConst.BIZ_INVENTORY,
                         record: String? = "record-$id", key: String = "collection_read_fixture") {
        FlowInstanceDao.new(id) {
            definitionId = r.definition; definitionKey = key; definitionName = "读取验证"; title = "敏感标题$id"
            businessType = business; businessKey = record; formData = mapOf("orgId" to "a", "secret" to id)
            initiator = "viewer"; initiatorName = "旧的发起人姓名"; currentNodeKeys = listOf("review")
            startTime = LocalDate.now().atTime(12, 0)
        }
        listOf("pending", "done").forEach { kind ->
            FlowTaskDao.new("$id-$kind") {
                instanceId = id; definitionId = r.definition; nodeKey = "review"; nodeName = "审查"; type = "APPROVE"
                assignee = "viewer"; assigneeName = "历史任职者"; formData = mapOf("attachment" to "$id.pdf")
                status = if (kind == "pending") TaskStatus.PENDING.name else TaskStatus.COMPLETED.name
                if (kind == "done") { action = "APPROVE"; completeTime = LocalDateTime.now() }
                dueTime = LocalDateTime.now().minusDays(1)
            }
        }
        FlowCCDao.new("$id-cc") { instanceId = id; taskId = "$id-done"; nodeName = "抄送"; userId = "viewer" }
        FlowInstanceLogDao.new("$id-log") { instanceId = id; action = "APPROVE"; comment = "历史意见$id" }
    }
    private fun request(size: Int = 0, page: Int = 1) = MockHttpServletRequest().apply {
        setParameter("pageSize", size.toString()); setParameter("pageNum", page.toString())
    }
    private fun state(r: Runtime) = r.db {
        listOf(FlowInstances, FlowTasks, FlowCCs, FlowInstanceLogs).map { table ->
            table.selectAll().orderBy(table.id).map { row -> table.columns.map { row[it] } }
        }
    }
    private fun assertReadDenied(r: Runtime, id: String) {
        assertThrows(ForbiddenException::class.java) { r.instances.findById(id, "viewer", true) }
        assertThrows(ForbiddenException::class.java) { r.instances.getHistory(id, "viewer", true) }
        assertThrows(ForbiddenException::class.java) { r.instances.getDiagramData(id, "viewer", true) }
        assertThrows(ForbiddenException::class.java) { r.instances.listOperationNodes(id, "viewer") }
        assertThrows(ForbiddenException::class.java) { r.instances.assertCurrentBusinessRead(id, "viewer") }
        assertThrows(ForbiddenException::class.java) { r.tasks.getInstanceTasks(id, "viewer", true) }
        assertThrows(ForbiddenException::class.java) { r.tasks.getTaskDetail("$id-pending", "viewer", true) }
        assertThrows(ForbiddenException::class.java) { r.tasks.readCC("$id-cc", "viewer") }
        assertThrows(ForbiddenException::class.java) { r.timeouts.urgeTask("$id-pending", "viewer", "旧姓名", isAdmin = true) }
        listOf("APPROVE", "REJECT", "SAVE", "TRANSFER").forEach { action ->
            assertThrows(ForbiddenException::class.java) {
                r.tasks.complete(CompleteTaskRequest(taskId = "$id-pending", action = action, transferTo = "viewer"), "viewer", "旧姓名", true)
            }
        }
        assertTrue(r.instances.findByBusiness("record-$id", CollectionConst.BIZ_INVENTORY, "viewer", true).isEmpty())
    }
    private fun assertLists(r: Runtime, expected: Set<String>, global: Set<String> = expected) {
        val count = expected.size.toLong()
        assertEquals(expected, r.tasks.getTodoList("viewer").map { it.instanceId }.toSet())
        assertEquals(expected, r.tasks.getDoneList("viewer").map { it.instanceId }.toSet())
        assertEquals(expected, r.tasks.getCCList("viewer").map { it.instanceId }.toSet())
        assertEquals(count, r.tasks.getTodoGroupByBusinessType("viewer").sumOf { it.count })
        assertEquals(expected, r.instances.listByInitiator("viewer").map { it.id.value }.toSet())
        assertEquals(global, r.instances.listAll(viewerId = "viewer").map { it.id.value }.toSet())
        for (size in listOf(0, 1)) {
            val pages = listOf(r.tasks.getTodoPage("viewer", request(size)), r.tasks.getDonePage("viewer", request(size)),
                r.tasks.getCCPage("viewer", request(size)), r.instances.listByInitiatorPage("viewer", request(size)),
                r.timeouts.findOverduePage(request(size), assigneeId = "viewer", viewerId = "viewer"))
            pages.forEach { p -> assertEquals(count, p.total); assertEquals(if (size == 0) count.toInt() else minOf(1, count.toInt()), (p.list as List<*>).size) }
            assertEquals(global.size.toLong(), r.timeouts.findOverduePage(request(size), viewerId = "viewer", administrative = true).total)
        }
        val overview = r.stats.overview("viewer", true)
        listOf(overview.todoCount, overview.doneCount, overview.myRunningCount, overview.ccTotalCount, overview.myOverdueCount).forEach { assertEquals(count, it) }
        listOf(overview.totalInstances, overview.runningInstances, overview.todayInstances, overview.pendingTasks, overview.overdueTasks).forEach { assertEquals(global.size.toLong(), it) }
        for (administrative in listOf(false, true)) {
            val n = (if (administrative) global else expected).size.toLong()
            assertEquals(n, r.stats.instanceStatusDistribution(viewerId = "viewer", administrative = administrative).sumOf { it.count })
            assertEquals(n * 2, r.stats.taskStatusDistribution(viewerId = "viewer", administrative = administrative).sumOf { it.count })
            assertEquals(n, r.stats.definitionRanking(viewerId = "viewer", administrative = administrative).sumOf { it.total })
            assertEquals(n, r.stats.recentTrend(days = 1, viewerId = "viewer", administrative = administrative).sumOf { it.started })
        }
    }

    @ParameterizedTest @ValueSource(strings = ["multi", "single"])
    fun `past participation and stale admin cannot expose foreign records including pagination and totals`(mode: String) = fixture(mode) { r ->
        val before = state(r)
        assertLists(r, setOf("a")); assertReadDenied(r, "b")
        assertNotNull(r.instances.findById("a", "viewer"))
        assertEquals("历史意见a", r.instances.getHistory("a", "viewer").single().comment)
        assertNotNull(r.instances.getDiagramData("a", "viewer"))
        assertEquals("a", r.tasks.getTaskDetail("a-pending", "viewer")!!.formData!!["secret"])
        assertEquals(2, r.tasks.getInstanceTasks("a", "viewer").size)
        assertEquals(before, state(r))
    }

    @ParameterizedTest @ValueSource(strings = ["disabled", "deleted", "locked", "account-expired", "credentials-expired", "expired-time", "permission-removed", "permission-deleted", "role-removed", "role-disabled", "group-disabled", "group-deleted", "moved"])
    fun `current database changes revoke reads without relogin and keep original records intact`(change: String) = fixture { r ->
        assertLists(r, setOf("a")); val before = state(r)
        r.db {
            when (change) {
                "disabled" -> Users.update({ Users.id eq "viewer" }) { it[enabled] = false }
                "deleted" -> Users.update({ Users.id eq "viewer" }) { it[status] = EnumContentStatus.DELETE }
                "locked" -> Users.update({ Users.id eq "viewer" }) { it[accountNonLocked] = false }
                "account-expired" -> Users.update({ Users.id eq "viewer" }) { it[accountNonExpired] = false }
                "credentials-expired" -> Users.update({ Users.id eq "viewer" }) { it[credentialsNonExpired] = false }
                "expired-time" -> Users.update({ Users.id eq "viewer" }) { it[expireTime] = LocalDateTime.now().minusMinutes(1) }
                "permission-removed" -> RolePermissions.deleteWhere { permissionId eq CollectionConst.SELECT }
                "permission-deleted" -> Permissions.deleteWhere { code eq CollectionConst.SELECT }
                "role-removed" -> RoleUsers.deleteWhere { (userId eq "viewer") and (roleId eq "role:READER") }
                "role-disabled" -> Roles.update({ Roles.code eq "READER" }) { it[enabled] = false }
                "group-disabled" -> Groups.update({ Groups.id eq "group-a" }) { it[enabled] = false }
                "group-deleted" -> Groups.deleteWhere { id eq "group-a" }
                "moved" -> Users.update({ Users.id eq "viewer" }) { it[groupId] = "group-b" }
            }
        }
        assertLists(r, if (change == "moved") setOf("b") else emptySet())
        assertReadDenied(r, "a"); assertEquals(before, state(r))
    }

    @Test fun `current business access remains required for administrators and cross museum grant is revocable`() = fixture { r ->
        r.db { actorRole("viewer", "CROSS", setOf(CollectionConst.CROSS_ORG)) }
        assertLists(r, setOf("a", "b"))
        r.db { RoleUsers.deleteWhere { (userId eq "viewer") and (roleId eq "role:READER") } }
        assertLists(r, emptySet()); assertReadDenied(r, "b")
        r.db { actorRole("viewer", "OTHER_READER", setOf(CollectionConst.SELECT)); RoleUsers.deleteWhere { roleId eq "role:CROSS" } }
        assertLists(r, setOf("a"))
    }

    @ParameterizedTest @ValueSource(strings = ["cross", "admin"])
    fun `single museum bounds current cross readers and administrators in all flow projections`(grant: String) = fixture("single") { r ->
        r.db {
            if (grant == "admin") {
                Roles.insert { it[id] = "1"; it[code] = "ADMIN"; it[name] = "系统管理员" }
                actorRole("viewer", "ADMIN", setOf("ADMIN"))
            }
            else actorRole("viewer", "CROSS", setOf(CollectionConst.CROSS_ORG))
        }
        val before = state(r)
        assertLists(r, setOf("a"))
        assertReadDenied(r, "b")
        assertNotNull(r.instances.findById("a", "viewer", true))
        assertEquals(before, state(r))
    }

    @Test fun `single backend default changes every flow read without restarting the resolver`() = fixture("single", backendDefault = true) { r ->
        r.db { actorRole("viewer", "CROSS", setOf(CollectionConst.CROSS_ORG)) }
        val before = state(r)
        assertLists(r, setOf("a"))
        r.db { Configs.update({ Configs.name eq CollectionDefaultOrgSettings.KEY }) { it[content] = "b" } }
        assertLists(r, setOf("b")); assertReadDenied(r, "a")
        assertNotNull(r.instances.findById("b", "viewer"))
        r.db { Configs.update({ Configs.name eq CollectionDefaultOrgSettings.KEY }) { it[content] = "a" } }
        assertLists(r, setOf("a")); assertReadDenied(r, "b")
        assertEquals(before, state(r))
    }

    @ParameterizedTest @ValueSource(strings = ["empty", "missing", "setting-disabled", "museum-disabled"])
    fun `invalid backend default closes collection flow reads and totals without changing history`(damage: String) = fixture("single", backendDefault = true) { r ->
        assertLists(r, setOf("a")); val before = state(r)
        r.db {
            actorRole("viewer", "CROSS", setOf(CollectionConst.CROSS_ORG))
            when (damage) {
                "empty" -> Configs.update({ Configs.name eq CollectionDefaultOrgSettings.KEY }) { it[content] = "" }
                "missing" -> Configs.update({ Configs.name eq CollectionDefaultOrgSettings.KEY }) { it[content] = "missing" }
                "setting-disabled" -> Configs.update({ Configs.name eq CollectionDefaultOrgSettings.KEY }) { it[enable] = false }
                "museum-disabled" -> CollectionOrgs.update({ CollectionOrgs.id eq "a" }) { it[orgStatus] = io.hizhengfu.cloud.collection.domain.EnumOrgStatus.DISABLED }
            }
        }
        assertLists(r, emptySet()); assertReadDenied(r, "a"); assertReadDenied(r, "b")
        assertEquals(before, state(r))
    }

    @Test fun `single default does not replace actual museum membership after a move`() = fixture("single", backendDefault = true) { r ->
        val before = state(r)
        assertLists(r, setOf("a"))
        r.db { Users.update({ Users.id eq "viewer" }) { it[groupId] = "group-b" } }
        assertLists(r, emptySet()); assertReadDenied(r, "a"); assertReadDenied(r, "b")
        r.db { Configs.update({ Configs.name eq CollectionDefaultOrgSettings.KEY }) { it[content] = "b" } }
        assertLists(r, setOf("b")); assertReadDenied(r, "a")
        assertEquals(before, state(r))
    }

    @Test fun `invalid single default does not block unrelated shared workflow business`() = fixture("single", backendDefault = true) { r ->
        r.db {
            actorRole("viewer", "CROSS", setOf(CollectionConst.CROSS_ORG))
            instance(r, "other", "PURCHASE", "purchase", "purchase")
            Configs.update({ Configs.name eq CollectionDefaultOrgSettings.KEY }) { it[content] = "" }
        }
        val before = state(r)
        assertLists(r, setOf("other")); assertReadDenied(r, "a"); assertReadDenied(r, "b")
        assertNotNull(r.instances.findById("other", "viewer"))
        assertEquals(before, state(r))
    }

    @Test fun `revoked manager cannot use stale administrative flag but still reads own participating records`() = fixture { r ->
        r.db { RoleUsers.deleteWhere { roleId eq "role:MANAGER" } }
        assertLists(r, setOf("a"), emptySet())
        assertNotNull(r.instances.findById("a", "viewer", true))
        assertFalse(r.instances.canAdminRead("a", "viewer"))
        assertThrows(ForbiddenException::class.java) { r.instances.listOperationNodes("a", "viewer") }
        assertThrows(ForbiddenException::class.java) { r.instances.jump("a", JumpFlowRequest(targetNodeKey = "review"), "viewer", "viewer") }
        assertThrows(ForbiddenException::class.java) { r.instances.reactivate("a", ReactivateFlowRequest(targetNodeKey = "review"), "viewer", "viewer") }
        r.db {
            FlowInstances.update({ FlowInstances.id eq "a" }) { it[initiator] = "someone-else" }
            FlowTasks.update({ FlowTasks.instanceId eq "a" }) { it[assignee] = "someone-else" }
            FlowCCs.update({ FlowCCs.instanceId eq "a" }) { it[userId] = "someone-else" }
        }
        assertThrows(ForbiddenException::class.java) { r.instances.findById("a", "viewer", true) }
        assertThrows(ForbiddenException::class.java) { r.instances.cancel("a", "viewer", "viewer", true) }
    }

    @Test fun `secondary groups and independent current reader role apply immediately`() = fixture { r ->
        r.db { UserGroups.insert { it[userId] = "viewer"; it[groupId] = "group-b" } }
        assertLists(r, setOf("a", "b"))
        r.db {
            actorRole("viewer", "OTHER_READER", setOf(CollectionConst.SELECT))
            RoleUsers.deleteWhere { roleId eq "role:READER" }
            UserGroups.deleteWhere { groupId eq "group-b" }
        }
        assertLists(r, setOf("a"))
    }

    @Test fun `mark all cc read affects only currently visible museum`() = fixture { r ->
        r.tasks.readAllCC("viewer")
        assertTrue(r.db { FlowCCDao["a-cc"].isRead }); assertFalse(r.db { FlowCCDao["b-cc"].isRead })
        assertEquals(0L, r.stats.overview("viewer", false).ccUnreadCount)
        assertEquals(1L, r.stats.overview("viewer", false).ccTotalCount)
    }

    @ParameterizedTest @ValueSource(strings = ["missing-record", "missing-business", "unknown-business", "unregistered-key", "deleted-original"])
    fun `damaged legacy links and forged form museum never substitute original business ownership`(damage: String) = fixture { r ->
        r.db {
            when (damage) {
                "missing-record" -> FlowInstances.update({ FlowInstances.id eq "a" }) { it[businessKey] = null }
                "missing-business" -> FlowInstances.update({ FlowInstances.id eq "a" }) { it[businessType] = null }
                "unknown-business" -> FlowInstances.update({ FlowInstances.id eq "a" }) { it[businessType] = "COLLECTION_UNKNOWN"; it[definitionKey] = "custom" }
                "unregistered-key" -> FlowInstances.update({ FlowInstances.id eq "a" }) { it[businessType] = "UNRELATED" }
                else -> Inventories.deleteWhere { id eq "record-a" }
            }
        }
        assertLists(r, emptySet()); assertReadDenied(r, "a")
    }

    @Test fun `unrelated business retains shared workflow access behavior even without collection permission`() = fixture { r ->
        r.db { instance(r, "other", "PURCHASE", "purchase", "purchase"); RoleUsers.deleteWhere { userId eq "viewer" } }
        assertLists(r, setOf("other"))
        assertNotNull(r.instances.findById("other", "viewer"))
    }

    @Test fun `business flow info must bind to the requested original record and type`() = fixture { r ->
        assertDoesNotThrow { r.instances.assertCurrentBusinessRead("a", "viewer", "record-a", CollectionConst.BIZ_INVENTORY) }
        assertThrows(ForbiddenException::class.java) { r.instances.assertCurrentBusinessRead("a", "viewer", "record-b", CollectionConst.BIZ_INVENTORY) }
        assertThrows(ForbiddenException::class.java) { r.instances.assertCurrentBusinessRead("a", "viewer", "record-a", CollectionConst.BIZ_LOAN) }
    }

    @Test fun `sampling requires its specific read grant while ledger corrections accept current select grant`() = fixture { r ->
        r.db {
            CollectionSamplingDao.new("sample") { orgId = "a"; collectionId = "object"; title = "取样" }
            CollectionChangeDao.new("change") {
                orgId = "a"; collectionId = "object"; reason = "订正"; before = CollectionCatalogContentVo()
                content = CollectionCatalogContentVo(); baseline = CollectionCatalogContentVo()
            }
            instance(r, "sampling", CollectionConst.BIZ_SAMPLING, "sample")
            instance(r, "correction", CollectionConst.BIZ_CHANGE, "change")
        }
        assertEquals(setOf("a", "correction"), r.tasks.getTodoList("viewer").map { it.instanceId }.toSet())
        assertReadDenied(r, "sampling")
        r.db { actorRole("viewer", "SAMPLING", setOf(CollectionConst.SAMPLING)) }
        assertNotNull(r.instances.findById("sampling", "viewer"))
    }

    @Test fun `warehouse visitor application does not inherit ordinary storage permission`() = fixture { r ->
        r.db {
            WarehouseEntryApplicationDao.new("entry") {
                orgId = "a"; applicationNo = "ENTRY"; applicantId = "viewer"; entrantIds = "viewer"; warehouseIds = "warehouse"; purpose = "核对"
                plannedStart = LocalDateTime.now(); plannedEnd = plannedStart.plusHours(1)
            }
            instance(r, "entry", CollectionConst.BIZ_WAREHOUSE_ENTRY, "entry")
            RoleUsers.deleteWhere { roleId eq "role:READER" }; actorRole("viewer", "STORAGE", setOf(CollectionConst.STORAGE))
        }
        assertReadDenied(r, "entry")
        r.db { actorRole("viewer", "VERIFY", setOf(CollectionConst.WAREHOUSE_ENTRY_VERIFY)) }
        assertNotNull(r.instances.findById("entry", "viewer"))
    }

    @Test fun `transfer destination reads its original shared transfer without gaining unrelated museum records`() = fixture { r ->
        r.db {
            CollectionTransferDao.new("transfer") { orgId = "b"; fromOrgId = "b"; toOrgId = "a"; collectionId = "object" }
            instance(r, "transfer", CollectionConst.BIZ_TRANSFER, "transfer")
        }
        assertEquals(setOf("a", "transfer"), r.tasks.getTodoList("viewer").map { it.instanceId }.toSet())
        assertReadDenied(r, "b")
        r.db { CollectionTransfers.update({ CollectionTransfers.id eq "transfer" }) { it[toOrgId] = null } }
        assertReadDenied(r, "transfer")
    }

    @Test fun `active system administrator role id grants museum scope and removal revokes it immediately`() = fixture { r ->
        r.db {
            Roles.insert { it[id] = "1"; it[code] = "ADMIN"; it[name] = "系统管理员" }
            actorRole("viewer", "ADMIN", setOf("ADMIN"))
        }
        assertLists(r, setOf("a", "b"))
        r.db { RoleUsers.deleteWhere { roleId eq "1" } }
        assertLists(r, setOf("a"))
    }

    @Test fun `nearest department mapping preserves parent read expansion without giving child readers sibling access`() = fixture { r ->
        r.db {
            CollectionOrgDao.new("child") { code = "C"; name = "下属馆"; parentId = "a"; systemGroupId = "child-group" }
            GroupDao.new("child-group") { name = "下属馆机构"; type = '1'; parentId = "group-a"; parentIds = "0,group-a," }
            GroupDao.new("child-dept") { name = "下属馆部门"; type = '2'; parentId = "child-group"; parentIds = "0,group-a,child-group," }
            InventoryDao.new("record-child") { orgId = "child"; batchName = "下属馆盘点" }
            instance(r, "child")
        }
        assertLists(r, setOf("a", "child"))
        r.db { Users.update({ Users.id eq "viewer" }) { it[groupId] = "child-dept" } }
        assertLists(r, setOf("child")); assertReadDenied(r, "a"); assertReadDenied(r, "b")
    }

    @Test fun `keyword queries cannot reveal foreign titles through counts or page gaps`() = fixture { r ->
        listOf(r.tasks.getTodoPage("viewer", request(1), "敏感标题b"),
            r.tasks.getDonePage("viewer", request(1), "敏感标题b"), r.tasks.getCCPage("viewer", request(1), keyword = "敏感标题b"),
            r.instances.listByInitiatorPage("viewer", request(1), keyword = "敏感标题b")).forEach { assertEquals(0L, it.total) }
        r.db { actorRole("viewer", "CROSS", setOf(CollectionConst.CROSS_ORG)) }
        val page = r.tasks.getTodoPage("viewer", request(1, 2))
        assertEquals(2L, page.total); assertEquals(1, (page.list as List<*>).size)
    }

    @Test fun `business flow info and controller refresh an existing login while preserving legitimate nonparticipant business reading`() = fixture { r ->
        val login = r.db { r.c.getBean(CollectionActorDirectory::class.java).requireActive("viewer") }
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(login, "", login.authorities)
        try {
            val info = BusinessInfo(r.instances)
            r.db {
                InventoryDao["record-a"].flowInstanceId = "a"
                FlowInstances.update({ FlowInstances.id eq "a" }) { it[initiator] = "other" }
                FlowTasks.update({ FlowTasks.instanceId eq "a" }) { it[assignee] = "other" }
                FlowCCs.update({ FlowCCs.instanceId eq "a" }) { it[userId] = "other" }
            }
            assertEquals("record-a", r.db { info.getFlowInfo("record-a")!!["businessKey"] })
            val controller = FlowInstanceController(r.instances)
            assertNotNull(controller.get("a").data)
            r.db { actorRole("viewer", "CROSS", setOf(CollectionConst.CROSS_ORG)); InventoryDao["record-a"].flowInstanceId = "b" }
            assertThrows(ForbiddenException::class.java) { r.db { info.getFlowInfo("record-a") } }
            r.db { InventoryDao["record-a"].flowInstanceId = "a"; Users.update({ Users.id eq "viewer" }) { it[enabled] = false } }
            assertThrows(ForbiddenException::class.java) { r.db { info.getFlowInfo("record-a") } }
            assertThrows(ForbiddenException::class.java) { controller.get("a") }
            assertEquals(emptyList<Any>(), controller.list().data)
        } finally { SecurityContextHolder.clearContext() }
    }
}
