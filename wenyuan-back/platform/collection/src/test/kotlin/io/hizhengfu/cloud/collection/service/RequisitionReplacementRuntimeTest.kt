package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.flow.dao.*
import io.hizhengfu.cloud.flow.domain.*
import io.hizhengfu.cloud.flow.engine.*
import io.hizhengfu.cloud.flow.service.*
import io.hizhengfu.cloud.sys.file.dao.FileDao
import io.hizhengfu.cloud.sys.file.dao.Files
import io.hizhengfu.cloud.sys.system.dao.*
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.Mockito.*
import org.postgresql.ds.PGSimpleDataSource
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
import java.util.Properties
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager

/** 实际本人目录、流程派单/回调和 Spring 事务；附件传输与实物点交不在本运行验证范围。 */
class RequisitionReplacementRuntimeTest {
    @TempDir lateinit var dir: Path
    @Configuration(proxyBeanMethods = false) @EnableTransactionManagement(proxyTargetClass = true)
    class Transactions
    private class Runtime(val c: AnnotationConfigApplicationContext, val tx: TransactionTemplate, val binder: BusinessAttachmentBinder,
        val source: DataSource, val schema: String) {
        val requisitions = c.getBean(RequisitionService::class.java)
        val replacement = c.getBean(RequisitionResponsibilityReplacementService::class.java)
        val statutory = c.getBean(RequisitionStatutoryService::class.java)
        val instances = c.getBean(FlowInstanceService::class.java)
        val tasks = c.getBean(FlowTaskService::class.java)
        fun <T> db(body: () -> T): T = tx.execute { body() }
        fun login(id: String) {
            val actor = db { c.getBean(CollectionActorDirectory::class.java).requireActive(id) }
            SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(actor, null, actor.authorities)
        }
    }
    private val time = LocalDateTime.of(2026, 9, 9, 9, 0)
    private fun dossier() = RequisitionDossierVo("dept", "department", "keeper", "director", "researcher", "无损观察", "器形比较", "研究室", "限位支撑及专人值守", "plan", time, time.plusDays(10), transportRequired=false, transportDecisionReason="本馆同室提用，不需要包装运输")
    private fun fixture(scopeMode:String="multi",postgres:Boolean=false,body: (Runtime, String) -> Unit) {
        val schema="requisition_replacement_"+UUID.randomUUID().toString().replace("-", "")
        val pg=if(postgres) {
            val path=System.getProperty("collection.test.pg.config") ?: error("Explicit isolated PostgreSQL configuration required")
            val properties=Properties().apply {Path.of(path).toFile().inputStream().use(::load)}
            PGSimpleDataSource().apply {
                setURL(properties.getProperty("url"));user=properties.getProperty("user");password=properties.getProperty("password")
                connection.use {it.createStatement().use {s->s.execute("CREATE SCHEMA $schema")}}
                currentSchema=schema;applicationName=schema
            }
        } else null
        val connectionSource:DataSource=pg ?: SQLiteDataSource().apply {url="jdbc:sqlite:${dir.resolve("replacement.db")}"}
        val manager = SpringTransactionManager(connectionSource, DatabaseConfig {})
        val binder = mock(BusinessAttachmentBinder::class.java).also { b -> `when`(b.resolveSingle(anyString())).thenAnswer { it.getArgument<String>(0) } }
        try {AnnotationConfigApplicationContext().use { c ->
            val scope = CollectionScopeResolver(scopeMode, "museum")
            val locks = CollectionOperationLockService(listOf(CollectionIncidentOperationGuard()))
            c.beanFactory.registerSingleton("transactionManager", manager)
            c.beanFactory.registerSingleton("cacheManager", ConcurrentMapCacheManager())
            c.beanFactory.registerSingleton("scope", scope)
            c.beanFactory.registerSingleton("binder", binder)
            c.beanFactory.registerSingleton("loader", FlowGraphLoader(16, 5))
            c.beanFactory.registerSingleton("registry", mock(FlowBusinessTypeRegistry::class.java))
            c.beanFactory.registerSingleton("resolver", FlowAssigneeResolver(emptyList()))
            c.beanFactory.registerSingleton("locks", locks)
            c.beanFactory.registerSingleton("names", mock(CollectionUserResolver::class.java))
            c.beanFactory.registerSingleton("placement", mock(CollectionPlacementService::class.java))
            c.beanFactory.registerSingleton("returns", mock(CollectionReturnService::class.java))
            c.beanFactory.registerSingleton("collections", CollectionService(scope, binder, mock(CollectionRegistrationNumberService::class.java), locks, mock(CollectionCatalogService::class.java), mock(CollectionChangeService::class.java), mock(CollectionRetainedLedgerService::class.java), mock(CollectionDisclosureService::class.java)))
            c.register(Transactions::class.java, CollectionActorDirectory::class.java, CollectionResponsibleUserService::class.java,
                CollectionPermanentArchiveService::class.java, io.hizhengfu.cloud.common.file.FileReadAccess::class.java, CollectionBusinessItemService::class.java, RequisitionStatutoryService::class.java,
                RequisitionService::class.java, RequisitionResponsibilityReplacementService::class.java,
                CoreCollectionFlowReadSources::class.java, CollectionFlowStartPolicy::class.java, CollectionFlowReadPolicy::class.java, FlowReadAccess::class.java,
                CollectionNamedFlowActorPolicy::class.java, CollectionRoleFlowActorPolicy::class.java, CollectionFlowDefinitionConstraint::class.java, CollectionFlowFormPolicy::class.java,
                FlowDefinitionService::class.java, FlowEngine::class.java, FlowInstanceService::class.java, FlowTaskService::class.java, FlowEventDispatcher::class.java)
            c.refresh(); SpringContextHolder().setApplicationContext(c)
            val r = Runtime(c, TransactionTemplate(manager), binder,connectionSource,schema)
            r.db {
                actorSchema()
                SchemaUtils.create(CollectionLedgerLogs,CollectionOrgs, Collections, CollectionIncidentItems, CollectionBusinessItems, CollectionOperationLocks, StorageMovements, Warehouses,
                    CollectionPermanentArchives, CollectionPermanentArchiveFiles, Files,
                    *CoreCollectionFlowReadSources().sources().map { it.table }.toTypedArray(),
                    FlowDefinitions, FlowNodeDefs, FlowEdgeDefs, FlowInstances, FlowTasks, FlowInstanceLogs, FlowCCs)
                for (id in listOf("museum", "other")) {
                    CollectionOrgDao.new(id) { name=id; code=id; systemGroupId="g-$id" }
                    GroupDao.new("g-$id") { name=id; type='1'; parentId="0"; parentIds="0," }
                }
                GroupDao.new("dept") { name="研究部"; type='2'; parentId="g-museum"; parentIds="0,g-museum," }
                for (id in listOf("operator", "department", "keeper", "director", "researcher", "new-operator", "new-department", "new-keeper", "new-director", "admin")) {
                    UserDao.new(id) { name=id; loginName=id; password="unused"; enabled=true; groupId="dept" }
                    actorRole(id, "BUSINESS", setOf(CollectionConst.STORAGE, CollectionConst.SELECT))
                }
                for (prefix in listOf("", "new-")) {
                    actorRole("${prefix}department", CollectionConst.ROLE_DEPARTMENT_HEAD)
                    actorRole("${prefix}keeper", CollectionConst.ROLE_STORAGE_HEAD)
                    actorRole("${prefix}director", CollectionConst.ROLE_DIRECTOR)
                }
                actorRole("admin", "ADMIN", setOf("ADMIN", "FLOW_MANAGE"))
                actorRole("new-director", "FLOW", setOf("FLOW_MANAGE"))
                WarehouseDao.new("warehouse") { orgId="museum"; name="一库"; sceneType=EnumLocationScene.STORAGE; type=EnumWarehouseType.WAREHOUSE }
                listOf("plan", "appointment", "withdrawal", "sign").forEach { key -> FileDao.new(key) { name="$key.pdf"; path="/files/$key.pdf"; hash=key; size=100; ext="pdf" } }
                listOf("one", "two").forEach { key -> CollectionDao.new(key) { orgId="museum"; totalRegNo=key; name=key; lifeStatus=EnumCollectionStatus.IN_STORE; warehouseId="warehouse"; catalogStatus=EnumCatalogStatus.APPROVED; source=EnumSource.OLD } }
            }
            r.login("operator")
            val model = javaClass.classLoader.getResourceAsStream("flow/${CollectionConst.FLOW_REQUISITION}.json")!!.use { JsonUtils.mapper.readTree(it) }.path("model")
            val definitions = c.getBean(FlowDefinitionService::class.java)
            val definition = definitions.create(FlowDefinitionVo(key=CollectionConst.FLOW_REQUISITION, name="提用", modelData=JsonUtils.toJson(model)), "operator")
            definitions.publish(definition.id.value, "operator")
            val id = r.requisitions.save(RequisitionVo(collectionIds="one,two", purpose=EnumMoveReason.RESEARCH, basis="研究课题", department="研究部", operator="operator", toWarehouseId="warehouse", dossier=dossier()))
            r.requisitions.submit(id)
            try { body(r, id) } finally { SecurityContextHolder.clearContext() }
        }} finally {
            SecurityContextHolder.clearContext()
            pg?.let {it.currentSchema="public";it.connection.use {c->c.createStatement().use {s->s.execute("DROP SCHEMA $schema CASCADE")}}}
        }
    }
    private fun task(r: Runtime, id: String) = r.db { FlowTaskDao.find { (FlowTasks.instanceId eq RequisitionDao[id].flowInstanceId!!) and (FlowTasks.status eq "PENDING") }.single().id.value }
    private fun approve(r: Runtime, id: String, prefix: String = "") {
        for (actor in listOf("department", "keeper", "director")) {
            r.login(prefix + actor)
            r.tasks.complete(CompleteTaskRequest(task(r,id), "APPROVE", comment="本人核对本次方案"), prefix+actor, prefix+actor)
        }
    }
    private fun request(r: Runtime, id: String): RequisitionReplacementRequest {
        r.login("new-director")
        val info = r.replacement.get(id)
        assertEquals(true, info["canReplace"], info.toString())
        return RequisitionReplacementRequest(info["expectedInstanceId"] as String, info["expectedSubmittedDigest"] as String, "岗位变动后重新核定责任人", "appointment", "withdrawal", dossier().copy(departmentHeadUserId="new-department", keeperUserId="new-keeper", leaderUserId="new-director"), "new-operator")
    }
    private val stateTables = listOf(Requisitions, Collections, CollectionBusinessItems, CollectionOperationLocks, FlowInstances, FlowTasks, FlowInstanceLogs, CollectionPermanentArchives, CollectionPermanentArchiveFiles, StorageMovements)
    private fun state(r: Runtime) = r.db { stateTables.map { t -> t.selectAll().map { row -> t.columns.map { row[it] } }.sortedBy { it.toString() } } }

    @Tag("postgres-concurrency")
    @ParameterizedTest
    @CsvSource("requisition,permission", "org,permission", "requisition,account", "org,account", "requisition,org-status", "org,org-status")
    fun `PostgreSQL repeated responsibility replacement rereads qualification after actual row lock wait`(lockTarget:String,change:String) {
        for(commit in listOf(true,false)) fixture(postgres=true) {r,id->
            val value=request(r,id)
            val child=r.replacement.replace(id,value)
            val tables=(stateTables+listOf(CollectionOrgs,Users,Groups,Roles,RoleUsers,RolePermissions,Permissions,UserGroups,FlowCCs)).toTypedArray()
            fun snapshot()=r.db {settledSafetySelectionState(*tables)}
            val before=snapshot()
            val authentication=SecurityContextHolder.getContext().authentication
            val pool=Executors.newFixedThreadPool(2)
            val ready=CountDownLatch(1);val release=CountDownLatch(1);val writerPid=AtomicInteger()
            try {
                val holder=pool.submit<Result<Map<String,List<List<String?>>>>> {
                    SecurityContextHolder.getContext().authentication=authentication
                    try {runCatching {r.db {
                        val table=if(lockTarget=="requisition")Requisitions.tableName else CollectionOrgs.tableName
                        val key=if(lockTarget=="requisition")id else "museum"
                        TransactionManager.current().exec("SELECT id FROM $table WHERE id='$key' FOR UPDATE") {row->assertTrue(row.next())}
                        val sql=when(change) {
                            "permission"->"DELETE FROM ${RolePermissions.tableName} WHERE role_id='role:BUSINESS' AND permission_id='${CollectionConst.STORAGE}'"
                            "account"->"UPDATE ${Users.tableName} SET enabled=false WHERE id='new-director'"
                            else->"UPDATE ${CollectionOrgs.tableName} SET org_status=${EnumOrgStatus.DISABLED.ordinal} WHERE id='museum'"
                        }
                        TransactionManager.current().exec(sql)
                        val changed=settledSafetySelectionState(*tables)
                        ready.countDown();check(release.await(15,TimeUnit.SECONDS)) {"Synthetic qualification holder must be released"}
                        if(!commit)error("synthetic qualification rollback")
                        changed
                    }}} finally {SecurityContextHolder.clearContext()}
                }
                assertTrue(ready.await(10,TimeUnit.SECONDS))
                val writer=pool.submit<Result<String>> {
                    SecurityContextHolder.getContext().authentication=authentication
                    try {runCatching {r.db {
                        TransactionManager.current().exec("SELECT pg_backend_pid()") {row->row.next();writerPid.set(row.getInt(1))}
                        // A cached museum must not survive a subsequent row lock with outdated status.
                        assertNotNull(CollectionOrgDao.findById("museum"))
                        assertNotNull(RequisitionDao.findById(id))
                        r.replacement.replace(id,value)
                    }}} finally {SecurityContextHolder.clearContext()}
                }
                val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(8)
                var waiting=false
                while(!waiting && System.nanoTime()<deadline) {
                    assertFalse(writer.isDone,"Repeated request must reach the held row lock")
                    waiting=r.source.connection.use {c->c.prepareStatement("SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE pid=? AND application_name=? AND wait_event_type='Lock')").use {s->
                        s.setInt(1,writerPid.get());s.setString(2,r.schema);s.executeQuery().use {row->row.next();row.getBoolean(1)}
                    }}
                    if(!waiting)Thread.sleep(20)
                }
                assertTrue(waiting,"Observe this writer's actual PostgreSQL lock wait")
                release.countDown()
                val held=holder.get(15,TimeUnit.SECONDS);val written=writer.get(15,TimeUnit.SECONDS)
                if(commit) {
                    val expected=held.getOrThrow()
                    assertInstanceOf(BadRequestException::class.java,written.exceptionOrNull(),"Committed qualification change must reject repeated authorization")
                    assertEquals(expected,snapshot(),"Rejected repeat must preserve source, child, old workflow and committed qualification")
                    r.db {when(change) {
                        "permission"->actorRole("new-director","BUSINESS",setOf(CollectionConst.STORAGE,CollectionConst.SELECT))
                        "account"->TransactionManager.current().exec("UPDATE ${Users.tableName} SET enabled=true WHERE id='new-director'")
                        else->TransactionManager.current().exec("UPDATE ${CollectionOrgs.tableName} SET org_status=${EnumOrgStatus.ENABLED.ordinal} WHERE id='museum'")
                    }}
                    assertEquals(before,snapshot())
                } else {
                    assertEquals("synthetic qualification rollback",held.exceptionOrNull()?.message)
                    assertEquals(child,written.getOrThrow())
                    assertEquals(before,snapshot())
                }
                assertEquals(child,r.replacement.replace(id,value))
                assertEquals(before,snapshot(),"Restored or rolled-back authorization must permit the same repeat without new facts")
                println("REQUISITION_REPLACEMENT_LOCK_WAIT target=$lockTarget change=$change commit=$commit pid=${writerPid.get()} observed=true rejected=$commit idempotent=true schema=${r.schema}")
            } finally {
                release.countDown();pool.shutdownNow()
                assertTrue(pool.awaitTermination(20,TimeUnit.SECONDS),"Both real transactions must end before isolated schema cleanup")
            }
        }
    }

    @ParameterizedTest @ValueSource(strings=["actor-disabled","actor-role","actor-permission","actor-moved","museum-disabled","department-disabled","keeper-role","operator-moved","reviewer-disabled","source-link","child-org","child-proposal","child-flow","child-deleted","source-proof","replacement-proof","created-proof","created-deleted","flow-revived","task-revived"])
    fun `replacement final write rejects changed authority links originals and workflow with complete rollback`(fault:String)=fixture {r,id ->
        val value=request(r,id).copy(reviewerUserId="researcher")
        val before=state(r)
        val instance=r.db {RequisitionDao[id].flowInstanceId!!}
        r.db {
            val sql=when(fault) {
                "actor-disabled"->"UPDATE ${Users.tableName} SET enabled=0 WHERE id='new-director'"
                "actor-role"->"DELETE FROM ${RoleUsers.tableName} WHERE user_id='new-director' AND role_id='role:${CollectionConst.ROLE_DIRECTOR}'"
                "actor-permission"->"DELETE FROM ${RolePermissions.tableName} WHERE permission_id='${CollectionConst.STORAGE}'"
                "actor-moved"->"UPDATE ${Users.tableName} SET group_id='g-other' WHERE id='new-director'"
                "museum-disabled"->"UPDATE ${CollectionOrgs.tableName} SET org_status=${EnumOrgStatus.DISABLED.ordinal} WHERE id='museum'"
                "department-disabled"->"UPDATE ${Users.tableName} SET enabled=0 WHERE id='new-department'"
                "keeper-role"->"DELETE FROM ${RoleUsers.tableName} WHERE user_id='new-keeper' AND role_id='role:${CollectionConst.ROLE_STORAGE_HEAD}'"
                "operator-moved"->"UPDATE ${Users.tableName} SET group_id='g-other' WHERE id='new-operator'"
                "reviewer-disabled"->"UPDATE ${Users.tableName} SET enabled=0 WHERE id='researcher'"
                "source-link"->"UPDATE ${Requisitions.tableName} SET superseded_by_id=NULL WHERE id='$id'"
                "child-org"->"UPDATE ${Requisitions.tableName} SET org_id='other' WHERE id=NEW.biz_id"
                "child-proposal"->"UPDATE ${Requisitions.tableName} SET basis='changed' WHERE id=NEW.biz_id"
                "child-flow"->"UPDATE ${Requisitions.tableName} SET flow_status='PASS' WHERE id=NEW.biz_id"
                "child-deleted"->"DELETE FROM ${Requisitions.tableName} WHERE id=NEW.biz_id"
                "source-proof"->"UPDATE ${CollectionPermanentArchives.tableName} SET snapshot='{}' WHERE biz_id='$id' AND phase='SUBMITTED'"
                "replacement-proof"->"UPDATE ${CollectionPermanentArchives.tableName} SET digest='changed' WHERE biz_id='$id' AND phase='${RequisitionReplacementEvidence.REPLACED}'"
                "created-proof"->"UPDATE ${CollectionPermanentArchives.tableName} SET snapshot='{}' WHERE id=NEW.id"
                "created-deleted"->"DELETE FROM ${CollectionPermanentArchives.tableName} WHERE id=NEW.id"
                "flow-revived"->"UPDATE ${FlowInstances.tableName} SET status='RUNNING' WHERE id='$instance'"
                else->"UPDATE ${FlowTasks.tableName} SET status='PENDING' WHERE instance_id='$instance'"
            }
            org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().exec("CREATE TRIGGER replacement_final_fault AFTER INSERT ON ${CollectionPermanentArchives.tableName} WHEN NEW.phase='${RequisitionReplacementEvidence.CREATED}' BEGIN $sql; END")
        }
        assertThrows(BadRequestException::class.java){r.replacement.replace(id,value)}
        assertEquals(before,state(r))
        r.db {org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().exec("DROP TRIGGER replacement_final_fault")}
        val child=r.replacement.replace(id,value)
        assertEquals(child,r.replacement.replace(id,value))
        assertEquals(EnumFlowStatus.DRAFT,r.db {RequisitionDao[child].flowStatus})
    }

    @ParameterizedTest @ValueSource(strings=["actor-disabled","source-out","child-link","flow-log","collection-state"])
    fun `approved replacement final failure restores reservations and all previous approvals`(fault:String)=fixture {r,id ->
        approve(r,id);val value=request(r,id);val before=state(r)
        val instance=r.db {RequisitionDao[id].flowInstanceId!!}
        r.db {
            val sql=when(fault) {
                "actor-disabled"->"UPDATE ${Users.tableName} SET enabled=0 WHERE id='new-director'"
                "source-out"->"UPDATE ${Requisitions.tableName} SET out_executed=1 WHERE id='$id'"
                "child-link"->"UPDATE ${Requisitions.tableName} SET replaces_id=NULL WHERE id=NEW.biz_id"
                "flow-log"->"DELETE FROM ${FlowInstanceLogs.tableName} WHERE instance_id='$instance'"
                else->"UPDATE ${Collections.tableName} SET life_status=${EnumCollectionStatus.IN_USE.ordinal} WHERE id='one'"
            }
            org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().exec("CREATE TRIGGER replacement_approved_fault AFTER INSERT ON ${CollectionPermanentArchives.tableName} WHEN NEW.phase='${RequisitionReplacementEvidence.CREATED}' BEGIN $sql; END")
        }
        assertThrows(BadRequestException::class.java){r.replacement.replace(id,value)}
        assertEquals(before,state(r))
        r.db {org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().exec("DROP TRIGGER replacement_approved_fault")}
        assertNotNull(r.replacement.replace(id,value))
    }

    @ParameterizedTest @ValueSource(strings=["AUDIT", "PASS", "CANCEL", "REJECT"])
    fun `replacement retains old workflow and requires three fresh approvals`(status: String) = fixture { r,id ->
        when(status) {
            "PASS" -> approve(r,id)
            "CANCEL" -> r.requisitions.cancel(id)
            "REJECT" -> { r.login("department"); r.tasks.complete(CompleteTaskRequest(task(r,id),"REJECT",comment="原申请驳回"),"department","department") }
        }
        val oldInstance = r.db { RequisitionDao[id].flowInstanceId!! }
        val oldRows = r.db { CollectionPermanentArchiveDao.all().associate { it.id.value to it.snapshot } }
        val value=request(r,id)
        r.db { for (actor in listOf("operator", "department", "keeper", "director")) UserDao[actor].enabled=false }
        val child = r.replacement.replace(id,value)
        assertEquals(child,r.replacement.replace(id,value))
        assertEquals(2L,r.db { RequisitionDao.count() })
        assertEquals(if(status=="PASS")"COMPLETED" else if(status=="REJECT")"REJECTED" else "CANCELED",r.db { FlowInstanceDao[oldInstance].status })
        assertEquals(child,r.db { RequisitionDao[id].supersededById })
        assertEquals(EnumFlowStatus.DRAFT,r.db { RequisitionDao[child].flowStatus })
        assertNotEquals(r.db {RequisitionDao[id].documentNo},r.db {RequisitionDao[child].documentNo})
        assertThrows(BadRequestException::class.java) {r.requisitions.executeOut(id,CollectionOutboundExecutionVo())}
        assertNull(r.db { RequisitionDao[child].flowInstanceId }); assertNull(r.db { RequisitionDao[child].handover })
        assertEquals(0L,r.db { CollectionOperationLockDao.count() })
        assertEquals(EnumCollectionStatus.IN_STORE,r.db { CollectionDao["one"].lifeStatus })
        assertThrows(BadRequestException::class.java) { r.db { r.statutory.requireCurrentApproval(RequisitionDao[id]) } }
        assertThrows(RuntimeException::class.java) { r.instances.reactivate(oldInstance,ReactivateFlowRequest("department"),"new-director","new-director") }
        r.requisitions.submit(child)
        assertEquals("new-department",r.db { FlowTaskDao[task(r,child)].assignee })
        approve(r,child,"new-")
        assertEquals(EnumFlowStatus.PASS,r.db { RequisitionDao[child].flowStatus })
        assertEquals(EnumCollectionStatus.PRE_OUT,r.db { CollectionDao["one"].lifeStatus })
        assertEquals(0L,r.db { StorageMovementDao.count() })
        assertEquals(oldRows,r.db { oldRows.keys.associateWith { CollectionPermanentArchiveDao[it].snapshot } })
        assertEquals(3L,r.db { FlowInstanceLogDao.find { (FlowInstanceLogs.instanceId eq RequisitionDao[child].flowInstanceId!!) and (FlowInstanceLogs.action eq "APPROVE") }.count() })
    }

    @ParameterizedTest @ValueSource(strings=["admin", "role", "disabled", "cross-org", "permission", "stale-instance", "stale-digest", "invalid-head", "missing-file", "no-change", "wrong-director"])
    fun `invalid authority or replacement leaves original tasks reservations and evidence unchanged`(kind:String)=fixture { r,id ->
        var value=request(r,id)
        r.db { when(kind) {
            "role" -> RoleUsers.deleteWhere { (userId eq "new-director") and (roleId eq "role:${CollectionConst.ROLE_DIRECTOR}") }
            "disabled" -> UserDao["new-director"].enabled=false
            "cross-org" -> UserDao["new-director"].groupId="g-other"
            "permission" -> RolePermissions.deleteWhere { permissionId eq CollectionConst.STORAGE }; else -> Unit
        } }
        when(kind) {
            "admin" -> r.login("admin")
            "stale-instance" -> value=value.copy(expectedInstanceId="wrong")
            "stale-digest" -> value=value.copy(expectedSubmittedDigest="wrong")
            "invalid-head" -> value=value.copy(dossier=value.dossier.copy(keeperUserId="researcher"))
            "missing-file" -> value=value.copy(appointmentFileIds="missing")
            "no-change" -> { r.login("director"); value=value.copy(dossier=dossier(),operatorUserId="operator") }
            "wrong-director" -> value=value.copy(dossier=value.dossier.copy(leaderUserId="director"))
        }
        val before=state(r)
        assertThrows(RuntimeException::class.java) { r.replacement.replace(id,value) }
        assertEquals(before,state(r))
    }

    @ParameterizedTest @ValueSource(strings=["AUDIT", "PASS"])
    fun `second archive failure rolls back cancellation child reservation release and original link then permits retry`(status:String)=fixture { r,id ->
        if(status=="PASS")approve(r,id)
        val value=request(r,id); val before=state(r); val calls=AtomicInteger()
        doAnswer { if(calls.incrementAndGet()==3)throw IllegalStateException("模拟新单归档写入失败"); null }.`when`(r.binder).bindSingle(anyString(),anyString(),anyString())
        assertThrows(IllegalStateException::class.java) { r.replacement.replace(id,value) }
        assertEquals(before,state(r));assertEquals(3,calls.get())
        doNothing().`when`(r.binder).bindSingle(anyString(),anyString(),anyString())
        assertNotNull(r.replacement.replace(id,value))
    }

    @ParameterizedTest @ValueSource(strings=["out-flag", "out-time", "handover-archive", "handover-field", "return", "movement", "restoration"])
    fun `actual execution and restricted restoration source cannot be reset to a draft`(kind:String)=fixture { r,id ->
        val value=request(r,id)
        r.db { val e=RequisitionDao[id];when(kind) {
            "out-flag" -> e.outExecuted=true
            "out-time" -> e.outExecutedTime=time
            "return" -> e.returned=true
            "handover-field" -> e.handover=RequisitionHandoverRecordVo(RequisitionHandoverVo(time,time,"keeper","sign",setOf("operator"),setOf("researcher"),listOf(RequisitionConditionVo("one","已点交",false)),true,true,"sign","sign"),"old-task","keeper",time)
            "handover-archive" -> r.c.getBean(CollectionPermanentArchiveService::class.java).seal(e.orgId,CollectionConst.BIZ_REQUISITION,id,"HANDOVER_recorded",mapOf("actual" to true),"sign")
            "movement" -> StorageMovementDao.new { orgId="museum";collectionId="one";type=EnumMoveType.OUT;refBizId=id }
            "restoration" -> e.sourceBizType="COLLECTION_RESTORATION"
        } }
        val before=state(r);assertThrows(BadRequestException::class.java) { r.replacement.replace(id,value) };assertEquals(before,state(r))
    }

    @ParameterizedTest @ValueSource(strings=["source-link", "peer-proof", "source-proof", "source-file", "peer-file", "source-proposal"])
    fun `changed original evidence blocks child submission and reports a readable gap without read rollback`(kind:String)=fixture { r,id ->
        val value=request(r,id);val child=r.replacement.replace(id,value)
        r.db { when(kind) {
            "source-link" -> RequisitionDao[id].supersededById=null
            "peer-proof" -> CollectionPermanentArchiveDao.find { (CollectionPermanentArchives.bizId eq id) and (CollectionPermanentArchives.phase eq RequisitionReplacementEvidence.REPLACED) }.single().delete()
            "source-proof" -> CollectionPermanentArchiveDao.find { (CollectionPermanentArchives.bizId eq id) and (CollectionPermanentArchives.phase eq "SUBMITTED") }.single().snapshot="{}"
            "source-file" -> Files.deleteWhere { Files.id eq "plan" }
            "peer-file" -> Files.deleteWhere { Files.id eq "appointment" }
            "source-proposal" -> RequisitionDao[id].basis="被改写"
        } }
        val before=state(r)
        assertThrows(RuntimeException::class.java) { r.requisitions.submit(child) };assertEquals(before,state(r))
        assertTrue(r.requisitions.get(child)!!.replacementErrors.isNotEmpty())
        assertEquals(false,r.replacement.get(id)["canReplace"]);assertEquals(before,state(r))
    }

    @ParameterizedTest @ValueSource(strings=["collections", "purpose", "source"])
    fun `successor draft cannot replace inherited objects purpose or source`(field:String)=fixture { r,id ->
        val child=r.replacement.replace(id,request(r,id));var vo=r.db { RequisitionDao[child].toModel() }
        vo=when(field) { "collections" -> vo.copy(collectionIds="one"); "purpose" -> vo.copy(purpose=EnumMoveReason.EXHIBIT); else -> vo.copy(sourceBizId="another") }
        val before=state(r);assertThrows(BadRequestException::class.java) { r.requisitions.save(vo) };assertEquals(before,state(r))
    }
    @Test fun `concurrent retries create one successor and preserve exact original instance`()=fixture { r,id ->
        approve(r,id);val value=request(r,id)
        val identity=SecurityContextHolder.getContext().authentication
        val ready=java.util.concurrent.CountDownLatch(2);val release=java.util.concurrent.CountDownLatch(1)
        val pool=java.util.concurrent.Executors.newFixedThreadPool(2)
        try {
            val requests=(1..2).map { pool.submit<Result<String>> {
                SecurityContextHolder.getContext().authentication=identity
                ready.countDown();release.await()
                try {runCatching {r.replacement.replace(id,value)}} finally {SecurityContextHolder.clearContext()}
            } }
            assertTrue(ready.await(10,java.util.concurrent.TimeUnit.SECONDS));release.countDown()
            val outcomes=requests.map {it.get(20,java.util.concurrent.TimeUnit.SECONDS)}
            assertTrue(outcomes.any {it.isSuccess},outcomes.toString())
            val child=r.replacement.replace(id,value)
            assertTrue(outcomes.filter {it.isSuccess}.all {it.getOrThrow()==child})
            assertEquals(2L,r.db {RequisitionDao.count()});assertEquals(1L,r.db {FlowInstanceDao.count()})
            assertEquals(1L,r.db {CollectionPermanentArchiveDao.find {CollectionPermanentArchives.phase eq RequisitionReplacementEvidence.REPLACED}.count()})
            assertEquals(EnumFlowStatus.PASS,r.db {RequisitionDao[id].flowStatus})
        } finally {release.countDown();pool.shutdownNow()}
    }

    @Test fun `a second departure preserves the full succession chain and rejects a missing ancestor original`()=fixture { r,id ->
        val child=r.replacement.replace(id,request(r,id));r.requisitions.submit(child)
        val next=request(r,child).copy(operatorUserId="researcher")
        val grandchild=r.replacement.replace(child,next)
        r.requisitions.submit(grandchild)
        assertEquals(id,r.db {RequisitionDao[child].replacesId});assertEquals(child,r.db {RequisitionDao[grandchild].replacesId})
        r.requisitions.cancel(grandchild)
        r.db {CollectionPermanentArchiveDao.find {(CollectionPermanentArchives.bizId eq id) and (CollectionPermanentArchives.phase eq RequisitionReplacementEvidence.REPLACED)}.single().delete()}
        assertTrue(r.requisitions.get(grandchild)!!.replacementErrors.isNotEmpty())
    }

    @Test fun `read only preview and exact repeated request do not permit altered new instructions`()=fixture {r,id ->
        val value=request(r,id);r.login("admin")
        assertEquals(false,r.replacement.get(id)["canReplace"])
        r.login("new-director");val child=r.replacement.replace(id,value);val before=state(r)
        assertThrows(BadRequestException::class.java) {r.replacement.replace(id,value.copy(reason="改写原授权"))};assertEquals(before,state(r))
        val fields=CollectionPrintFieldBuilder.requisitionFields(r.db {RequisitionDao[id].toModel()})
        assertTrue(fields.any {it.label=="原提用执行已终止，后续须重新审批" && it.value==child})
    }

    @Test fun `replacement of only the reviewer still creates a fresh proposal without reusing prior approval`()=fixture {r,id ->
        val template=request(r,id);r.login("director")
        val value=template.copy(dossier=dossier(),operatorUserId="operator",reviewerUserId="researcher")
        val child=r.replacement.replace(id,value)
        assertEquals("researcher",r.db {RequisitionDao[child].reviewer})
        assertEquals(EnumFlowStatus.DRAFT,r.db {RequisitionDao[child].flowStatus})
        assertNull(r.db {RequisitionDao[child].flowInstanceId})
        r.requisitions.submit(child);approve(r,child)
        assertEquals(EnumFlowStatus.PASS,r.db {RequisitionDao[child].flowStatus})
        assertFalse(r.db {RequisitionDao[child].outExecuted})
    }

    @Test fun `single museum missing original retains readable replacement preview diagnostic`()=fixture("single") {r,id ->
        r.login("new-director")
        val healthy=r.replacement.get(id)
        assertEquals(true,healthy["canReplace"],healthy.toString())
        assertTrue((healthy["evidenceErrors"] as List<*>).isEmpty())
        assertNotNull(healthy["expectedSubmittedDigest"])
        r.db {
            assertEquals("museum",RequisitionDao[id].orgId)
            assertEquals(1,Files.deleteWhere {Files.id eq "plan"})
        }
        val declaredTables=(stateTables+listOf(CollectionOrgs,Files,Users,Groups,Roles,RoleUsers,RolePermissions,Permissions,UserGroups,FlowCCs)).distinct()
        fun declaredState()=r.db {declaredTables.map {table->
            table.selectAll().map {row->table.columns.map {row[it]}}.sortedBy {it.toString()}
        }}
        val beforeRead=declaredState()
        val preview=assertDoesNotThrow<Map<String,Any?>> {r.replacement.get(id)}
        assertEquals(false,preview["canReplace"],preview.toString())
        val errors=preview["evidenceErrors"] as List<*>
        assertTrue(errors.isNotEmpty(),preview.toString())
        assertTrue(errors.all {it is String && it.isNotBlank()},preview.toString())
        assertEquals(beforeRead,declaredState())
    }

    @ParameterizedTest @ValueSource(strings=["single","multi"])
    fun `replacement preview respects configured museum despite current cross grant`(mode:String)=fixture(mode) {r,id ->
        r.db {
            actorRole("operator","CROSS_READER",setOf(CollectionConst.CROSS_ORG))
            val row=RequisitionDao[id];row.orgId="other"
            CollectionPermanentArchiveDao.all().forEach {
                it.orgId="other"
                if(it.phase=="SUBMITTED")it.snapshot=JsonUtils.toJson(requisitionProposal(row))!!
                it.digest=CollectionCareEvidence.digest(it.snapshot)
            }
            CollectionPermanentArchiveFileDao.all().forEach { ref ->
                val original=JsonUtils.mapper.readTree(ref.metadata)
                val archive=CollectionPermanentArchiveDao[ref.archiveId]
                ref.orgId="other"
                ref.metadata=CollectionArchiveFileIdentity.metadata(FileDao[ref.fileId],"other",
                    archive.bizType,archive.bizId,CollectionArchiveFileIdentity.candidates(original),
                    CollectionArchiveFileIdentity.content(original),CollectionArchiveFileIdentity.revision(original))
            }
        }
        val before=state(r)
        if(mode=="single")assertThrows(BadRequestException::class.java){r.replacement.get(id)}
        else assertNotNull(r.replacement.get(id)["expectedSubmittedDigest"])
        assertEquals(before,state(r))
    }
    @Test fun `replacement preview rejects mismatched original owner before diagnostic projection`()=fixture {r,id ->
        r.db {CollectionPermanentArchiveDao.all().forEach {it.orgId="other"}}
        val before=state(r)
        assertThrows(BadRequestException::class.java){r.replacement.get(id)}
        assertEquals(before,state(r))
    }

    @ParameterizedTest @ValueSource(strings=["disabled","permission","moved"])
    fun `replacement preview rechecks the current reader without trusting old login`(kind:String)=fixture {r,id ->
        assertNotNull(r.replacement.get(id)["expectedSubmittedDigest"])
        r.db {when(kind) {
            "disabled" -> UserDao["operator"].enabled=false
            "permission" -> RolePermissions.deleteAll()
            else -> UserDao["operator"].groupId="g-other"
        }}
        val before=state(r)
        assertThrows(BadRequestException::class.java){r.replacement.get(id)}
        assertEquals(before,state(r))
    }

    @Test fun `single museum rejects direct replacement outside configured museum before checking old proof`()=fixture("single") {r,id ->
        val value=request(r,id)
        r.db {RequisitionDao[id].orgId="other";UserDao["new-director"].groupId="g-other"}
        val before=state(r)
        val error=assertThrows(BadRequestException::class.java){r.replacement.replace(id,value)}
        assertTrue(error.message!!.contains("当前配置收藏单位"),error.message)
        assertEquals(before,state(r))
    }

}
