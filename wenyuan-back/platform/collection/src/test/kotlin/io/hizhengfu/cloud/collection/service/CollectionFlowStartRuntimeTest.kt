package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.exception.ForbiddenException
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.flow.api.FlowInstanceController
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
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.support.TransactionTemplate
import org.sqlite.SQLiteDataSource
import java.nio.file.Path
import java.time.LocalDateTime

/** 实际 InventoryService/实际核验规则/资源锁/角色派单/流程回调，与当前数据库身份共同验证。 */
class CollectionFlowStartRuntimeTest {
    @TempDir lateinit var dir: Path
    @Configuration(proxyBeanMethods = false) @EnableTransactionManagement(proxyTargetClass = true)
    class Transactions
    private class Runtime(val c: AnnotationConfigApplicationContext, val tx: TransactionTemplate) {
        val inventory = c.getBean(InventoryService::class.java)
        val instances = c.getBean(FlowInstanceService::class.java)
        val tasks = c.getBean(FlowTaskService::class.java)
        val engine = c.getBean(FlowEngine::class.java)
        fun <T> db(body: () -> T): T = tx.execute { body() }
    }
    private fun fixture(mode: String = "multi", body: (Runtime) -> Unit) {
        val manager = SpringTransactionManager(SQLiteDataSource().apply { url = "jdbc:sqlite:${dir.resolve("starts.db")}" }, DatabaseConfig {})
        AnnotationConfigApplicationContext().use { c ->
            c.beanFactory.registerSingleton("transactionManager", manager)
            c.beanFactory.registerSingleton("cacheManager", ConcurrentMapCacheManager())
            c.beanFactory.registerSingleton("scope", CollectionScopeResolver(mode, "a"))
            c.beanFactory.registerSingleton("graphLoader", FlowGraphLoader(16, 5))
            c.beanFactory.registerSingleton("registry", mock(FlowBusinessTypeRegistry::class.java))
            c.beanFactory.registerSingleton("resolver", FlowAssigneeResolver(emptyList()))
            c.beanFactory.registerSingleton("collections", mock(CollectionService::class.java))
            c.beanFactory.registerSingleton("attachments", mock(BusinessAttachmentBinder::class.java).also { binder -> org.mockito.Mockito.`when`(binder.resolveSingle(org.mockito.Mockito.anyString())).thenAnswer { it.getArgument<String>(0) } })
            c.beanFactory.registerSingleton("batches", mock(InventoryBatchService::class.java))
            c.register(Transactions::class.java, CollectionPermanentArchiveService::class.java, io.hizhengfu.cloud.common.file.FileReadAccess::class.java, CollectionActorDirectory::class.java, CollectionResponsibleUserService::class.java,
                CoreCollectionFlowReadSources::class.java, CollectionFlowStartPolicy::class.java, CollectionFlowReadPolicy::class.java,
                FlowReadAccess::class.java, CollectionRoleFlowActorPolicy::class.java, CollectionFlowDefinitionConstraint::class.java, CollectionFlowFormPolicy::class.java,
                FlowDefinitionService::class.java, FlowEngine::class.java, FlowInstanceService::class.java, FlowTaskService::class.java,
                InventoryService::class.java, InventoryComplianceService::class.java, CollectionOperationLockService::class.java, FlowEventDispatcher::class.java)
            c.refresh(); SpringContextHolder().setApplicationContext(c)
            val r = Runtime(c, TransactionTemplate(manager))
            r.db {
                actorSchema()
                SchemaUtils.create(CollectionOrgs, Collections, InventoryBatches, CollectionOperationLocks, Warehouses, CollectionPermanentArchives, CollectionPermanentArchiveFiles, io.hizhengfu.cloud.sys.file.dao.Files,
                    *CoreCollectionFlowReadSources().sources().map { it.table }.toTypedArray(),
                    FlowDefinitions, FlowNodeDefs, FlowEdgeDefs, FlowInstances, FlowTasks, FlowInstanceLogs, FlowCCs)
                for (id in listOf("a", "b")) {
                    CollectionOrgDao.new(id) { code = id; name = id; systemGroupId = "g-$id"; ownership=EnumOwnership.STATE }
                    GroupDao.new("g-$id") { name = id; type = '1'; parentId = "0"; parentIds = "0," }
                }
                for (id in listOf("submitter", "reviewer", "director")) {
                    UserDao.new(id) { name = id; loginName = id; password = "unused"; enabled = true; groupId = "g-a" }
                }
                actorRole("submitter", "SUBMITTER", setOf(CollectionConst.STORAGE))
                actorRole("submitter", CollectionConst.ROLE_KEEPER, emptySet())
                actorRole("director", CollectionConst.ROLE_DIRECTOR, emptySet())
                actorRole("reviewer", CollectionConst.ROLE_STORAGE_HEAD, setOf(CollectionConst.SELECT))
                WarehouseDao.new("store"){orgId="a";name="实际库房";sceneType=EnumLocationScene.STORAGE}
                listOf("scope","ledger","card","photo","signed").forEach { key -> io.hizhengfu.cloud.sys.file.dao.FileDao.new(key){name="$key.pdf";path="$key.pdf";hash=key;size=100;ext="pdf"} }
                CollectionDao.new("object") { orgId = "a"; totalRegNo = "001"; name = "原藏品";nature=EnumCollectionNature.RELIC;lifeStatus=EnumCollectionStatus.IN_STORE;warehouseId="store";registeredAt=LocalDateTime.now().minusDays(10) }
                InventoryDao.new("record") { orgId = "a"; collectionId = "object"; batchName = "实际清点"; result = EnumInventoryResult.MATCH }
            }
            val login = r.db { c.getBean(CollectionActorDirectory::class.java).requireActive("submitter") }
            SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(login, "", login.authorities)
            try {
                val definitions = c.getBean(FlowDefinitionService::class.java)
                val template = javaClass.classLoader.getResourceAsStream("flow/collection_inventory.json")!!.use { JsonUtils.mapper.readTree(it).path("model") }
                val model = r.db { CollectionFlowRoleConfiguration.resolveTemplateCodes(JsonUtils.mapper.writeValueAsString(template)) }
                val def = definitions.create(FlowDefinitionVo(key = CollectionConst.FLOW_INVENTORY, name = "盘点", modelData = model), "author")
                definitions.publish(def.id.value, "author")
                val generic = definitions.create(FlowDefinitionVo(key = "generic", name = "普通流程", modelData = """
                    {"nodes":[{"id":"s","type":"start","properties":{"nodeType":"START"}},
                    {"id":"a","type":"approve","properties":{"nodeType":"APPROVE","assigneeType":"FIXED_USER","assignees":["reviewer"]}},
                    {"id":"e","type":"end","properties":{"nodeType":"END"}}],
                    "edges":[{"id":"sa","type":"polyline","sourceNodeId":"s","targetNodeId":"a"},{"id":"ae","type":"polyline","sourceNodeId":"a","targetNodeId":"e"}]}
                """.trimIndent()), "author")
                definitions.publish(generic.id.value, "author")
                assertEquals(1, r.engine.startPolicies.size)
                body(r)
            } finally { SecurityContextHolder.clearContext() }
        }
    }
    private fun checked(r: Runtime) = r.db {
        InventoryBatchDao.new("batch") { orgId = "a"; batchName = "已保存核验批次" }
        val compliance=r.c.getBean(InventoryComplianceService::class.java)
        compliance.begin("batch", InventoryPlanVo(LocalDateTime.now().year, EnumInventoryMethod.FULL, EnumInventoryPurpose.ROUTINE, "真实应盘清单", "reviewer", "director", "scope"))
        compliance.check("batch",InventoryItemCheckVo("object", LocalDateTime.now().minusDays(1), setOf("submitter"), "实际库位",
            true, true, true, true, "现状完整", false, "ledger", "card", "photo", "signed"))
        InventoryDao["record"].batchId = "batch"
    }
    private fun state(r: Runtime) = r.db { listOf(Inventories, InventoryBatches, CollectionPermanentArchives, CollectionPermanentArchiveFiles, CollectionOperationLocks, FlowInstances, FlowTasks, FlowInstanceLogs).map { t ->
        t.selectAll().orderBy(t.id).map { row -> t.columns.map { row[it] } }
    } }

    private fun returnableInventory(r: Runtime): String {
        val json=javaClass.classLoader.getResourceAsStream("flow/collection_inventory.json")!!.use {JsonUtils.mapper.readTree(it).path("model")}
        val model=JsonUtils.mapper.readValue(JsonUtils.mapper.writeValueAsString(json),FlowModel::class.java)
        val modified=model.copy(nodes=model.nodes.map {n->when(n.properties.nodeType){
            "START"->n.copy(properties=n.properties.copy(actions=listOf("APPROVE","SAVE","REJECT","TRANSFER","DELEGATE"),rejectType="TERMINATE",fieldPerms=listOf(FieldPermItem("workflowNote",perm="EDITABLE"))))
            "APPROVE"->n.copy(properties=n.properties.copy(rejectType="BACK_TO_START"))
            else->n
        }})
        val resolved=r.db {CollectionFlowRoleConfiguration.resolveTemplateCodes(JsonUtils.toJson(modified)!!)}
        val definitions=r.c.getBean(FlowDefinitionService::class.java)
        val def=definitions.create(FlowDefinitionVo(key=CollectionConst.FLOW_INVENTORY,name="退回核对",modelData=resolved),"author")
        definitions.publish(def.id.value,"author");checked(r);r.inventory.submit("record")
        return r.tasks.getTodoList("reviewer").single().id!!
    }

    private fun returnedTask(r: Runtime): String {
        val first=returnableInventory(r)
        r.tasks.complete(CompleteTaskRequest(first,"REJECT",comment="请原发起人核对"),"reviewer","实际复核人")
        return r.tasks.getTodoList("submitter").single().id!!
    }

    @ParameterizedTest @ValueSource(strings=["APPROVE","SAVE","REJECT"])
    fun `returned initiator action rolls back when submission duty disappears after initial checks`(action:String)=fixture {r->
        val task=returnedTask(r);val before=state(r)
        val logCount=r.db {FlowInstanceLogs.selectAll().count()};var completed=false
        r.engine.startPolicies=listOf(object:FlowStartPolicy {
            override fun validate(context:FlowStartContext) {}
            override fun initiatorTaskVerifier(context:FlowInitiatorContext,assignee:String?,operatorId:String,action:String):()->Unit = {
                completed=true
                assertTrue(FlowInstanceLogs.selectAll().count()>logCount)
            }
        })+r.engine.startPolicies
        r.engine.formPolicies=r.engine.formPolicies+object:FlowFormPolicy {
            override fun protectedFields(context:FlowActorContext):Set<String> {
                RolePermissions.deleteWhere {permissionId eq CollectionConst.STORAGE}
                return emptySet()
            }
        }
        val failure=assertThrows(ForbiddenException::class.java){r.tasks.complete(CompleteTaskRequest(task,action,formData=mapOf("workflowNote" to "核对")),"submitter","本人")}
        assertTrue(completed);assertTrue(failure.message!!.contains("提交权限"));assertEquals(before,state(r))
        r.db {assertTrue(RolePermissions.selectAll().where {RolePermissions.permissionId eq CollectionConst.STORAGE}.any())}
    }

    @ParameterizedTest @ValueSource(strings=["museum","instance"])
    fun `returned task keeps original business museum and instance even when changed source remains accessible`(change:String)=fixture {r->
        val task=returnedTask(r);val before=state(r)
        var completed=false
        r.engine.startPolicies=listOf(object:FlowStartPolicy {
            override fun validate(context:FlowStartContext) {}
            override fun initiatorTaskVerifier(context:FlowInitiatorContext,assignee:String?,operatorId:String,action:String):()->Unit = {completed=true}
        })+r.engine.startPolicies
        r.engine.formPolicies=r.engine.formPolicies+object:FlowFormPolicy {
            override fun protectedFields(context:FlowActorContext):Set<String> {
                if(change=="museum") {
                    InventoryDao["record"].orgId="b"
                    Users.update({Users.id eq "submitter"}){it[groupId]="g-b"}
                } else InventoryDao["record"].flowInstanceId="another-instance"
                return emptySet()
            }
        }
        val failure=assertThrows(RuntimeException::class.java){r.tasks.complete(CompleteTaskRequest(task,"SAVE"),"submitter","本人")}
        assertTrue(failure is BadRequestException || failure is ForbiddenException)
        assertTrue(completed);assertEquals(before,state(r))
        assertEquals("g-a",r.db {UserDao["submitter"].groupId})
    }

    @Test fun `qualified returned initiator can reject without repeating the old audit status prerequisite`()=fixture {r->
        val task=returnedTask(r)
        r.tasks.complete(CompleteTaskRequest(task,"REJECT",comment="本人终止原申请"),"submitter","本人")
        r.db {
            assertEquals(EnumFlowStatus.REJECT,InventoryDao["record"].flowStatus)
            assertEquals(InstanceStatus.REJECTED.name,FlowInstanceDao[InventoryDao["record"].flowInstanceId!!].status)
            assertEquals(TaskStatus.REJECTED.name,FlowTaskDao[task].status)
        }
    }

    @Test fun `returned task detail previews current qualification without writing task or business state`()=fixture {r->
        val task=returnedTask(r);val before=state(r)
        val allowed=r.tasks.getTaskDetail(task,"submitter")!!
        assertEquals(setOf("APPROVE","SAVE","REJECT"),allowed.availableActions!!.toSet())
        assertEquals(setOf("TRANSFER","DELEGATE"),allowed.actionRestrictions!!.keys);assertEquals(before,state(r))
        r.db {RolePermissions.deleteWhere {permissionId eq CollectionConst.STORAGE};actorRole("submitter","READ",setOf(CollectionConst.SELECT))}
        val readonly=state(r);val denied=r.tasks.getTaskDetail(task,"submitter")!!
        assertEquals(emptyList<String>(),denied.availableActions);assertEquals(5,denied.actionRestrictions!!.size)
        assertTrue(denied.actionRestrictions!!.values.all {it.contains("提交权限")})
        assertTrue(JsonUtils.toJson(denied)!!.contains("\"availableActions\":[]"));assertEquals(readonly,state(r))
    }

    @ParameterizedTest @ValueSource(strings=["disabled","locked","expired","role-disabled","read-only","moved","cross-read-only","group-disabled","source-flow","source-status","source-missing","assignee"])
    fun `returned start task checks current account permission scope and exact original workflow even with a stale login`(change:String)=fixture {r->
        val task=returnedTask(r)
        r.db {when(change){
            "disabled"->Users.update({Users.id eq "submitter"}){it[enabled]=false}
            "locked"->Users.update({Users.id eq "submitter"}){it[accountNonLocked]=false}
            "expired"->Users.update({Users.id eq "submitter"}){it[expireTime]=LocalDateTime.now().minusMinutes(1)}
            "role-disabled"->Roles.update({Roles.code eq "SUBMITTER"}){it[enabled]=false}
            "read-only"->{RolePermissions.deleteWhere {permissionId eq CollectionConst.STORAGE};actorRole("submitter","READ",setOf(CollectionConst.SELECT))}
            "moved","cross-read-only"->{Users.update({Users.id eq "submitter"}){it[groupId]="g-b"};if(change=="cross-read-only")actorRole("submitter","CROSS",setOf(CollectionConst.CROSS_ORG))}
            "group-disabled"->Groups.update({Groups.id eq "g-a"}){it[enabled]=false}
            "source-flow"->InventoryDao["record"].flowInstanceId="another-instance"
            "source-status"->InventoryDao["record"].flowStatus=EnumFlowStatus.CANCEL
            "source-missing"->InventoryDao["record"].delete()
            "assignee"->FlowTaskDao[task].assignee="reviewer"
        }}
        val before=state(r)
        // 直接引擎入口也须核验，不能只依靠任务详情的读取限制。
        val failure=assertThrows(RuntimeException::class.java){r.db {r.engine.complete(FlowTaskDao[task],"submitter","旧登录姓名","APPROVE",formData=mapOf("workflowNote" to "核对"))}}
        assertTrue(failure is BadRequestException || failure is ForbiddenException);assertEquals(before,state(r))
    }

    @ParameterizedTest @ValueSource(strings=["APPROVE","SAVE","REJECT","TRANSFER","DELEGATE"])
    fun `read permission does not authorize any returned initiator task action`(action:String)=fixture {r->
        val task=returnedTask(r)
        r.db {RolePermissions.deleteWhere {permissionId eq CollectionConst.STORAGE};actorRole("submitter","READ",setOf(CollectionConst.SELECT))}
        assertNotNull(r.tasks.getTaskDetail(task,"submitter"))
        val before=state(r)
        val failure=assertThrows(RuntimeException::class.java){r.tasks.complete(CompleteTaskRequest(task,action,formData=mapOf("workflowNote" to "旧页面"),transferTo="reviewer",transferToName="复核人"),"submitter","提交人")}
        assertTrue(failure is BadRequestException || failure is ForbiddenException);assertEquals(before,state(r))
    }

    @ParameterizedTest @ValueSource(strings=["APPROVE","SAVE","REJECT"])
    fun `administrator cannot replace the original applicant on returned tasks`(action:String)=fixture {r->
        val task=returnedTask(r);val before=state(r)
        assertThrows(ForbiddenException::class.java){r.tasks.complete(CompleteTaskRequest(task,action),"reviewer","管理员代办",canAdminComplete=true)}
        assertEquals(before,state(r))
    }

    @ParameterizedTest @ValueSource(strings=["TRANSFER","DELEGATE"])
    fun `qualified initiator must use formal business succession instead of transferring the original application`(action:String)=fixture {r->
        val task=returnedTask(r);val before=state(r)
        assertThrows(BadRequestException::class.java){r.tasks.complete(CompleteTaskRequest(task,action,transferTo="reviewer",transferToName="复核人"),"submitter","提交人")}
        assertEquals(before,state(r))
    }

    @Test fun `reviewer return is retained when applicant is disabled and restored qualified applicant can complete a full new review`()=fixture {r->
        val first=returnableInventory(r);r.db {Users.update({Users.id eq "submitter"}){it[enabled]=false}}
        r.tasks.complete(CompleteTaskRequest(first,"REJECT",comment="本人退回意见"),"reviewer","复核人")
        val task=r.db {FlowTaskDao.find {(FlowTasks.nodeKey eq "start") and (FlowTasks.status eq "PENDING")}.single().id.value}
        assertEquals(EnumFlowStatus.AUDIT,r.db {InventoryDao["record"].flowStatus})
        r.db {Users.update({Users.id eq "submitter"}){it[enabled]=true}}
        r.tasks.complete(CompleteTaskRequest(task,"SAVE",formData=mapOf("workflowNote" to "核对原材料")),"submitter","提交人")
        r.tasks.complete(CompleteTaskRequest(task,"APPROVE"),"submitter","提交人")
        assertEquals(EnumFlowStatus.AUDIT,r.db {InventoryDao["record"].flowStatus})
        r.tasks.complete(CompleteTaskRequest(r.tasks.getTodoList("reviewer").single().id!!,"APPROVE"),"reviewer","复核人")
        assertEquals(EnumFlowStatus.PASS,r.db {InventoryDao["record"].flowStatus})
        assertEquals(EnumInventoryResult.MATCH,r.db {InventoryDao["record"].result});assertEquals(0L,r.db {CollectionOperationLocks.selectAll().count()})
        assertTrue(r.db {FlowInstanceLogs.selectAll().any {it[FlowInstanceLogs.comment]=="本人退回意见"}})
    }

    @Test fun `actual inventory form cannot claim a different count result and original business still completes`() = fixture { r ->
        checked(r); r.inventory.submit("record")
        val task = r.tasks.getTodoList("reviewer").single()
        val before = state(r)
        assertThrows(BadRequestException::class.java) {
            r.tasks.complete(CompleteTaskRequest(task.id!!, "APPROVE", formData = mapOf("result" to "LOSS")), "reviewer", "实际复核人")
        }
        assertEquals(before, state(r))
        val detail = r.tasks.getTaskDetail(task.id!!, "reviewer")!!
        assertEquals("MATCH", detail.formData!!["result"])
        assertEquals("READONLY", detail.fieldPerms!!.single { it.field == "result" }.perm)
        r.tasks.complete(CompleteTaskRequest(task.id!!, "APPROVE", formData = detail.formData), "reviewer", "实际复核人")
        assertEquals(EnumFlowStatus.PASS, r.db { InventoryDao["record"].flowStatus })
        assertEquals(EnumInventoryResult.MATCH, r.db { InventoryDao["record"].result })
        assertEquals(0L, r.db { CollectionOperationLocks.selectAll().count() })
    }

    @ParameterizedTest @ValueSource(strings = ["JUMP", "REACTIVATE"])
    fun `actual inventory reentry checks original facts and rolls back while valid full review still completes`(action: String) = fixture { r ->
        checked(r)
        r.db { actorRole("submitter", "FLOW_MANAGER", setOf("FLOW_MANAGE")) }
        r.inventory.submit("record")
        val id = r.db { InventoryDao["record"].flowInstanceId!! }
        if (action == "REACTIVATE") r.instances.cancel(id, "submitter", "提交人")
        val original = r.db { InventoryBatchDao["batch"].checks }
        r.db { InventoryBatchDao["batch"].checks = null }
        val before = state(r)
        fun reenter() = if (action == "JUMP") r.instances.jump(id, JumpFlowRequest("approve"), "submitter", "管理员")
            else r.instances.reactivate(id, ReactivateFlowRequest("approve"), "submitter", "管理员")
        assertThrows(BadRequestException::class.java) { reenter() }
        assertEquals(before, state(r))
        r.db { InventoryBatchDao["batch"].checks = original }
        reenter()
        assertEquals(EnumFlowStatus.AUDIT, r.db { InventoryDao["record"].flowStatus })
        assertEquals(1L, r.db { CollectionOperationLocks.selectAll().count() })
        val task = r.tasks.getTodoList("reviewer").single()
        r.tasks.complete(CompleteTaskRequest(task.id!!, "APPROVE"), "reviewer", "实际复核人")
        assertEquals(EnumFlowStatus.PASS, r.db { InventoryDao["record"].flowStatus })
        assertEquals(0L, r.db { CollectionOperationLocks.selectAll().count() })
    }

    @Test fun `actual inventory submission enforces checks and binds the original before normal reviewer completion`() = fixture { r ->
        val before = state(r)
        assertThrows(BadRequestException::class.java) { r.inventory.submit("record") }
        assertEquals(before, state(r))
        checked(r)
        r.inventory.submit("record")
        val id = r.db { requireNotNull(InventoryDao["record"].flowInstanceId) }
        assertEquals(id, r.db { InventoryDao["record"].flowInstanceId })
        assertEquals(EnumFlowStatus.AUDIT, r.db { InventoryDao["record"].flowStatus })
        assertEquals(1L, r.db { CollectionOperationLocks.selectAll().count() })
        assertEquals("实际清点", r.db { FlowInstanceDao[id].formData!!["batchName"] })
        val task = r.tasks.getTodoList("reviewer").single()
        r.tasks.complete(CompleteTaskRequest(task.id!!, "APPROVE"), "reviewer", "实际复核人")
        assertEquals(EnumFlowStatus.PASS, r.db { InventoryDao["record"].flowStatus })
        assertEquals(0L, r.db { CollectionOperationLocks.selectAll().count() })
    }

    @ParameterizedTest @ValueSource(strings = ["COLLECTION_ACQUISITION", "COLLECTION_ACQUISITION_PLAN", "COLLECTION_EXHIBITION", "COLLECTION_INVENTORY", "COLLECTION_REPRODUCTION", "COLLECTION_CHANGE", "COLLECTION_DEREGISTER", "COLLECTION_LOAN", "COLLECTION_REQUISITION", "COLLECTION_RETURN", "COLLECTION_APPRAISAL", "COLLECTION_SAMPLING", "COLLECTION_WAREHOUSE_ENTRY", "COLLECTION_TRANSFER"])
    fun `generic start cannot target protected business even with forged form approval or administrator claim`(business: String) = fixture { r ->
        val before = state(r)
        val request = StartFlowRequest("generic", "绕过业务", "record", business, mapOf("orgId" to "a", "approved" to true), mapOf("a" to listOf("submitter")))
        assertThrows(BadRequestException::class.java) { FlowInstanceController(r.instances).start(request) }
        assertEquals(before, state(r))
    }

    @ParameterizedTest @ValueSource(strings = ["missing", "unknown", "other"])
    fun `protected definition cannot be made generic by omitting or replacing business type`(kind: String) = fixture { r ->
        val before = state(r)
        assertThrows(BadRequestException::class.java) { r.instances.start(StartFlowRequest(CollectionConst.FLOW_INVENTORY, "伪造",
            businessKey = "record", businessType = when(kind) { "missing" -> null; "unknown" -> "COLLECTION_UNKNOWN"; else -> "OTHER" }), "submitter", "旧登录") }
        assertEquals(before, state(r))
    }

    @ParameterizedTest @ValueSource(strings = ["permission", "read-only", "disabled", "role-disabled", "role-removed", "expired", "moved", "group-disabled", "cross-read-only"])
    fun `actual business submission rechecks live permissions and museum despite a retained login`(change: String) = fixture { r ->
        checked(r)
        r.db {
            when(change) {
                "permission", "read-only" -> { RolePermissions.deleteWhere { permissionId eq CollectionConst.STORAGE }; if(change=="read-only")actorRole("submitter", "READ", setOf(CollectionConst.SELECT)) }
                "disabled" -> Users.update({ Users.id eq "submitter" }) { it[enabled] = false }
                "role-disabled" -> Roles.update({ Roles.code eq "SUBMITTER" }) { it[enabled] = false }
                "role-removed" -> RoleUsers.deleteWhere { userId eq "submitter" }
                "expired" -> Users.update({ Users.id eq "submitter" }) { it[expireTime] = LocalDateTime.now().minusMinutes(1) }
                "moved", "cross-read-only" -> { Users.update({ Users.id eq "submitter" }) { it[groupId] = "g-b" }; if(change=="cross-read-only")actorRole("submitter", "CROSS", setOf(CollectionConst.CROSS_ORG)) }
                "group-disabled" -> Groups.update({ Groups.id eq "g-a" }) { it[enabled] = false }
            }
        }
        val before = state(r)
        // 业务入口先核验当前账号和馆别；未进入引擎时按业务校验返回 400。
        assertThrows(BadRequestException::class.java) { r.inventory.submit("record") }
        assertEquals(before, state(r))
    }

    @Test fun `single mode default museum cannot authorize a departed submitter`() = fixture("single") { r ->
        checked(r); r.db { Users.update({ Users.id eq "submitter" }) { it[groupId] = "g-b" } }
        val before = state(r)
        assertThrows(BadRequestException::class.java) { r.inventory.submit("record") }
        assertEquals(before, state(r))
    }

    @Test fun `duplicate active instance and missing business submission state are rejected without altering existing state`() = fixture { r ->
        checked(r)
        assertThrows(ForbiddenException::class.java) { r.instances.startBusiness(StartFlowRequest(CollectionConst.FLOW_INVENTORY,"未经过业务","record",CollectionConst.BIZ_INVENTORY),"submitter","提交人") {} }
        r.inventory.submit("record")
        val id = r.db { requireNotNull(InventoryDao["record"].flowInstanceId) }
        assertThrows(BadRequestException::class.java) { r.inventory.submit("record") }
        r.db { InventoryDao["record"].flowStatus = EnumFlowStatus.DRAFT }
        val before = state(r)
        assertThrows(BadRequestException::class.java) { r.inventory.submit("record") }
        assertEquals(before, state(r)); assertEquals(id, r.db { InventoryDao["record"].flowInstanceId })
    }

    @Test fun `actual submission rejects an edited unarchived check and leaves no partial workflow`()=fixture {r->
        checked(r)
        r.db {val e=InventoryBatchDao["batch"];e.checks=InventoryChecksVo(e.checks!!.items.map {it.copy(condition="未归档改写")})}
        val before=state(r)
        assertThrows(BadRequestException::class.java){r.inventory.submit("record")}
        assertEquals(before,state(r));assertEquals(0L,r.db {FlowInstanceDao.count()})
    }

    @Test fun `unrelated generic workflow remains available through its original public entry`() = fixture { r ->
        val id = r.instances.start(StartFlowRequest("generic", "普通业务", "unrelated", "UNRELATED"), "submitter", "提交人").id.value
        assertEquals(InstanceStatus.RUNNING.name, r.db { FlowInstanceDao[id].status })
        assertEquals(EnumFlowStatus.DRAFT, r.db { InventoryDao["record"].flowStatus })
    }
}
