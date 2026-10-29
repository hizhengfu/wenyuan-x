package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.sys.file.dao.FileDao
import io.hizhengfu.cloud.sys.file.dao.Files
import io.hizhengfu.cloud.sys.system.dao.*
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ValueSource
import org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.*
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.support.TransactionTemplate
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class LeadershipDepartureRuntimeTest {
    @TempDir lateinit var dir: Path
    @Configuration(proxyBeanMethods=false) @EnableTransactionManagement(proxyTargetClass=true) class Transactions
    private class Runtime(val c: AnnotationConfigApplicationContext, val tx: TransactionTemplate, val failBinding: AtomicBoolean, val source: javax.sql.DataSource) {
        val departures = c.getBean(LeadershipDepartureService::class.java)
        val inventory = c.getBean(InventoryComplianceService::class.java)
        fun <T> db(body: () -> T): T = tx.execute { body() }
        fun login(id: String) {
            val user = db { c.getBean(CollectionActorDirectory::class.java).requireActive(id) }
            SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(user,null,user.authorities)
        }
    }
    private val at=LocalDateTime.of(2026,8,1,9,0)
    @AfterEach fun clear()=SecurityContextHolder.clearContext()
    private fun fixture(body:(Runtime)->Unit) = departureTestDatabase(dir.resolve("departure.db")) { source ->
        val manager=SpringTransactionManager(source,DatabaseConfig {})
        val failBinding=AtomicBoolean(false)
        val binder=mock(BusinessAttachmentBinder::class.java)
        `when`(binder.resolveSingle(anyString())).thenAnswer {it.getArgument<String>(0)}
        doAnswer {if(failBinding.get())throw IllegalStateException("simulated archive binding failure");null}.`when`(binder).bindSingle(anyString(),anyString(),anyString())
        AnnotationConfigApplicationContext().use { c ->
            c.beanFactory.registerSingleton("transactionManager",manager)
            c.beanFactory.registerSingleton("cacheManager",ConcurrentMapCacheManager())
            c.beanFactory.registerSingleton("scope",CollectionScopeResolver("multi","museum"))
            c.beanFactory.registerSingleton("binder",binder)
            c.register(Transactions::class.java,CollectionActorDirectory::class.java,CollectionResponsibleUserService::class.java,CollectionPermanentArchiveService::class.java, io.hizhengfu.cloud.common.file.FileReadAccess::class.java,LeadershipDepartureService::class.java,InventoryComplianceService::class.java)
            c.refresh();SpringContextHolder().setApplicationContext(c)
            val r=Runtime(c,TransactionTemplate(manager),failBinding,source)
            val initial=SecurityUser(id="operator",name="operator",username="operator",password="",groupId="museum",groupIds=setOf("museum"),postIds=emptySet(),dataType=0,roles=emptySet(),authorities=emptyList(),enabled=true)
            SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(initial,null,initial.authorities)
            r.db {
                SchemaUtils.create(Groups,Users,UserGroups,Roles,RoleUsers,Permissions,RolePermissions,CollectionOrgs,Collections,Warehouses,Loans,Requisitions,CollectionReturns,InventoryBatches,Inventories,CollectionPermanentArchives,CollectionPermanentArchiveFiles,Files)
                GroupDao.new("museum"){name="本馆机构";type='1';parentId="0";parentIds="0,"}
                CollectionOrgDao.new("museum"){name="本馆";code="M";systemGroupId="museum";ownership=EnumOwnership.STATE}
                listOf("operator","keeper","successor","former").forEach {id->UserDao.new(id){name=id;loginName=id;password="unused";enabled=id!="former";groupId="museum"}}
                fun role(user:String,code:String,permission:String?=null) {
                    val role=RoleDao.new {name=code;this.code=code;enabled=true}
                    RoleUsers.insert {it[userId]=user;it[roleId]=role.id.value}
                    if(permission!=null){val p=PermissionDao.new{name=permission;this.code=permission};RolePermissions.insert{it[roleId]=role.id;it[permissionId]=p.id}}
                }
                role("operator","MANAGER",CollectionConst.STORAGE);role("keeper",CollectionConst.ROLE_STORAGE_HEAD);role("successor",CollectionConst.ROLE_DIRECTOR)
                WarehouseDao.new("store"){orgId="museum";name="库房";sceneType=EnumLocationScene.STORAGE}
                CollectionDao.new("one"){orgId="museum";totalRegNo="one";name="藏品";nature=EnumCollectionNature.RELIC;categoryId="category";level=EnumRelicLevel.GENERAL;lifeStatus=EnumCollectionStatus.IN_STORE;warehouseId="store";registeredAt=at.minusMonths(1)}
                listOf("a","b","c").forEach {id->InventoryBatchDao.new(id){orgId="museum";batchName=id}}
                listOf("appointment","scope","signature","replan").forEach{id->FileDao.new(id){name="$id.pdf";path="/files/$id.pdf";hash=id;size=100;ext="pdf"}}
            }
            r.login("operator");body(r)
        }
    }
    private fun request()=LeadershipDepartureRequest(EnumLeadershipPost.PRINCIPAL,"former",at.minusYears(1),at.plusDays(5),"appointment","appointment","successor","appointment","keeper","核对全部历史任职及保管责任","scope")
    private fun signature()=CollectionCareSignatureVo("本人已核对完整任职依据","signature")
    private fun prepared(r:Runtime):String {
        val id=r.departures.register("museum",request())
        r.login("keeper");r.departures.confirm("museum",id,"PK",signature());r.login("successor");r.departures.confirm("museum",id,"PS",signature());r.login("operator");return id
    }
    private fun plan(id:String)=InventoryPlanVo(2026,EnumInventoryMethod.FULL,EnumInventoryPurpose.DIRECTOR_DEPARTURE,"完整全馆清点","keeper","successor","scope","former","successor",at.plusDays(5),"appointment",id)
    private fun state(r:Runtime)=r.db {listOf(InventoryBatches,CollectionPermanentArchives,CollectionPermanentArchiveFiles).map {t->t.selectAll().map{row->t.columns.map{row[it]}.toString()}.sorted()}}

    companion object {
        @JvmStatic fun departureLockCases() = listOf("REGISTER", "PK", "PS", "HK", "HS").flatMap { stage ->
            listOf(false, true).flatMap { repeat -> listOf("account", "role", "museum").flatMap { change ->
                listOf(true, false).map { Arguments.of(stage, repeat, change, it) }
            } }
        }
        @JvmStatic fun departureWriteChanges() = listOf("REGISTER", "PK", "PS", "HK", "HS").flatMap { stage ->
            listOf("actor-disabled", "actor-role", "actor-moved", "museum-disabled").map { Arguments.of(stage, it) }
        } + listOf(Arguments.of("REGISTER", "keeper-disabled"), Arguments.of("REGISTER", "successor-role"))
    }
    @Tag("postgres-concurrency") @ParameterizedTest @MethodSource("departureLockCases")
    fun `director departure first and repeated stages use qualification after the actual museum lock`(stage:String,repeat:Boolean,change:String,commit:Boolean)=fixture { r ->
        val (initialId, actorId) = readyStage(r, stage)
        r.login(actorId)
        var id = initialId
        val operation = { if(stage=="REGISTER") r.departures.register("museum",request()) else r.departures.confirm("museum",id,stage,signature()) }
        if (repeat) id = operation()
        val actor = r.db { r.c.getBean(CollectionActorDirectory::class.java).requireActive(actorId) }
        assertDepartureLockWait(r.source,actor,change,commit,"director-$stage-repeat=$repeat",operation,
            { r.db { departureDeclaredState() } }, { r.login(actorId) })
    }
    private fun readyStage(r:Runtime, stage:String):Pair<String,String> {
        if(stage=="REGISTER")return "" to "operator"
        val id=r.departures.register("museum",request())
        if(stage=="PK")return id to "keeper"
        r.login("keeper");r.departures.confirm("museum",id,"PK",signature())
        if(stage=="PS")return id to "successor"
        r.login("successor");r.departures.confirm("museum",id,"PS",signature())
        r.db { actorRole("operator",CollectionConst.ROLE_KEEPER) };r.login("operator");r.inventory.begin("a",plan(id))
        r.inventory.check("a",InventoryItemCheckVo("one",at,setOf("operator"),"实际库房",true,true,true,true,"完整",false,"scope","scope","scope","signature"))
        r.inventory.complete("a",InventoryCompletionVo(at.plusDays(2),"keeper","successor","signature",at.plusDays(1),"signature","实际主管文物部门",setOf("实际监交人员"),at,"appointment","signature"))
        if(stage=="HK")return id to "keeper"
        r.login("keeper");r.departures.confirm("museum",id,"HK",signature())
        return id to "successor"
    }
    @ParameterizedTest @MethodSource("departureWriteChanges")
    fun `departure registration and every signature reject authority changes during actual archive write`(stage:String,change:String)=fixture {r ->
        val (id,actorId)=readyStage(r,stage);r.login(actorId)
        val before=state(r)
        val prefix=if(stage=="REGISTER")"CASE" else stage
        r.db {
            val sql=when(change) {
                "actor-disabled"->"UPDATE ${Users.tableName} SET enabled=0 WHERE id='$actorId'"
                "actor-role"->"UPDATE ${Roles.tableName} SET enabled=0 WHERE id IN (SELECT role_id FROM ${RoleUsers.tableName} WHERE user_id='$actorId')"
                "actor-moved"->"UPDATE ${Users.tableName} SET group_id='departed-museum' WHERE id='$actorId'"
                "museum-disabled"->"UPDATE ${CollectionOrgs.tableName} SET org_status=${EnumOrgStatus.values().first { it!=EnumOrgStatus.ENABLED }.ordinal} WHERE id='museum'"
                "keeper-disabled"->"UPDATE ${Users.tableName} SET enabled=0 WHERE id='keeper'"
                else->"UPDATE ${Roles.tableName} SET enabled=0 WHERE code='${CollectionConst.ROLE_DIRECTOR}'"
            }
            TransactionManager.current().exec("CREATE TRIGGER departure_authority_change AFTER INSERT ON ${CollectionPermanentArchives.tableName} WHEN NEW.biz_type='${LeadershipDepartureEvidence.BIZ}' AND NEW.phase LIKE '${prefix}_%' BEGIN $sql; END")
        }
        assertThrows(BadRequestException::class.java) {
            if(stage=="REGISTER")r.departures.register("museum",request()) else r.departures.confirm("museum",id,stage,signature())
        }
        assertEquals(before,state(r))
        r.db {
            assertTrue(UserDao[actorId].enabled==true);assertEquals("museum",UserDao[actorId].groupId)
            assertTrue(UserDao["keeper"].enabled==true);assertTrue(RoleDao.all().all {it.enabled==true})
            assertEquals(EnumOrgStatus.ENABLED,CollectionOrgDao["museum"].orgStatus)
            TransactionManager.current().exec("DROP TRIGGER departure_authority_change")
        }
        // 原实际任职恢复后可重新办理；原离任账号始终停用，不恢复其授权。
        val result=if(stage=="REGISTER")r.departures.register("museum",request()) else r.departures.confirm("museum",id,stage,signature())
        assertTrue(result.isNotBlank());assertFalse(r.db {UserDao["former"].enabled==true})
    }

    @ParameterizedTest @ValueSource(strings=["ownership", "archive-digest", "archive-content", "archive-delete"])
    fun `departure cannot accept changed supervision basis or newly written original`(change:String)=fixture {r ->
        val before=state(r)
        r.db {
            val sql=when(change) {
                "ownership"->"UPDATE ${CollectionOrgs.tableName} SET ownership=NULL WHERE id='museum'"
                "archive-digest"->"UPDATE ${CollectionPermanentArchives.tableName} SET digest='changed' WHERE id=NEW.id"
                "archive-content"->"UPDATE ${CollectionPermanentArchives.tableName} SET snapshot='{}' WHERE id=NEW.id"
                else->"DELETE FROM ${CollectionPermanentArchives.tableName} WHERE id=NEW.id"
            }
            TransactionManager.current().exec("CREATE TRIGGER departure_original_change AFTER INSERT ON ${CollectionPermanentArchives.tableName} WHEN NEW.biz_type='${LeadershipDepartureEvidence.BIZ}' AND NEW.phase LIKE 'CASE_%' BEGIN $sql; END")
        }
        assertThrows(BadRequestException::class.java){r.departures.register("museum",request())}
        assertEquals(before,state(r));assertEquals(EnumOwnership.STATE,r.db {CollectionOrgDao["museum"].ownership})
    }
    @ParameterizedTest @ValueSource(strings=["PS", "HS"])
    fun `later legitimate signer preserves earlier departed officers historical signatures`(stage:String)=fixture {r ->
        val (id,actorId)=readyStage(r,stage)
        val originals=state(r)
        r.db {UserDao["keeper"].enabled=false}
        r.login(actorId);assertEquals(id,r.departures.confirm("museum",id,stage,signature()))
        val value=r.departures.get("museum")
        assertTrue((value["evidenceErrors"] as List<*>).isEmpty())
        val item=(value["items"] as List<*>).filterIsInstance<Map<String,Any?>>().single()
        assertEquals(if(stage=="HS")"COMPLETED" else "PENDING_INVENTORY",item["status"])
        val after=state(r)
        originals.zip(after).forEach {(old,current)->assertTrue(current.containsAll(old))}
        assertFalse(r.db {UserDao["keeper"].enabled==true});assertFalse(r.db {UserDao["former"].enabled==true})
    }

    @Test fun `current cross grant can inspect leadership originals after login from another museum`()=fixture {r ->
        val id=r.departures.register("museum",request())
        r.db {
            GroupDao.new("other"){name="他馆";type='1';parentId="0";parentIds="0,"}
            CollectionOrgDao.new("other"){name="他馆";code="O";systemGroupId="other"}
            UserDao["operator"].groupId="other"
        }
        r.login("operator");r.db {actorRole("operator","CURRENT_CROSS",setOf(CollectionConst.CROSS_ORG))}
        val value=r.departures.get("museum")
        assertTrue((value["evidenceErrors"] as List<*>).isEmpty())
        assertFalse((value["archives"] as List<*>).isEmpty())
        val archive=r.db {CollectionPermanentArchiveDao.all().single {it.bizType==LeadershipDepartureEvidence.BIZ && it.phase=="CASE_$id"}.id.value}
        r.departures.requireArchivedOriginal("museum",archive,"appointment")
        r.db {RolePermissions.deleteAll()}
        assertThrows(BadRequestException::class.java){r.departures.get("museum")}
        assertThrows(BadRequestException::class.java){r.departures.requireArchivedOriginal("museum",archive,"appointment")}
    }

    @Test fun `multi museum current officers can read the case original and lose it after role revocation`()=fixture {r ->
        val id=prepared(r)
        val archive=r.db {CollectionPermanentArchiveDao.all().single {it.bizType==LeadershipDepartureEvidence.BIZ && it.phase=="CASE_$id"}.id.value}
        r.login("keeper");r.departures.requireArchivedOriginal("museum",archive,"appointment")
        r.login("successor");r.departures.requireArchivedOriginal("museum",archive,"appointment")
        r.db {RoleDao.all().single {it.code==CollectionConst.ROLE_STORAGE_HEAD}.enabled=false}
        r.login("keeper")
        assertThrows(BadRequestException::class.java){r.departures.requireArchivedOriginal("museum",archive,"appointment")}
    }

    @Test fun `single museum configuration blocks another museum leadership originals`()=fixture {r ->
        val single=CollectionScopeResolver("single","other")
        r.db {
            val service=LeadershipDepartureService(single,CollectionActorDirectory(single),r.c.getBean(CollectionResponsibleUserService::class.java),r.c.getBean(CollectionPermanentArchiveService::class.java))
            assertThrows(BadRequestException::class.java){service.get("museum")}
        }
    }

    @Test fun `archive binding failure rolls back the entire case and retry keeps one original`()=fixture {r->
        val before=state(r);r.failBinding.set(true)
        assertThrows(IllegalStateException::class.java){r.departures.register("museum",request())};assertEquals(before,state(r))
        r.failBinding.set(false);val id=r.departures.register("museum",request());val after=state(r)
        assertEquals(id,r.departures.register("museum",request()));assertEquals(after,state(r))
    }
    @Test fun `failed preparation signature leaves no phase or attachment references`()=fixture {r->
        val id=r.departures.register("museum",request());r.login("keeper");val before=state(r);r.failBinding.set(true)
        assertThrows(IllegalStateException::class.java){r.departures.confirm("museum",id,"PK",signature())};assertEquals(before,state(r))
        r.failBinding.set(false);r.departures.confirm("museum",id,"PK",signature())
        assertFalse(r.db{LeadershipDepartureEvidence(r.c.getBean(CollectionPermanentArchiveService::class.java)).read("museum").prepared(id)})
    }
    @Test fun `failed replan archive cannot replace active batch or leave partial plan`()=fixture {r->
        val id=prepared(r);r.inventory.begin("a",plan(id));val before=state(r)
        val next=plan(id).copy(replacesInventoryBatchId="a",replanReason="真实范围变化",replanFileIds="replan")
        r.failBinding.set(true);assertThrows(IllegalStateException::class.java){r.inventory.begin("b",next)}
        assertEquals(before,state(r));assertNull(r.db{InventoryBatchDao["b"].plan})
        r.failBinding.set(false);r.inventory.begin("b",next)
        assertEquals("b",r.inventory.get("a").supersededByBatchId)
    }
    @Test fun `concurrent starts commit only one active full inventory and loser retry cannot branch`()=fixture {r->
        val id=prepared(r);val ready=CountDownLatch(2);val start=CountDownLatch(1);val pool=Executors.newFixedThreadPool(2)
        try {
            val futures=listOf("a","b").map {batch->pool.submit<Pair<String,Throwable?>> {
                try {r.login("operator");ready.countDown();check(start.await(10,TimeUnit.SECONDS));batch to runCatching{r.inventory.begin(batch,plan(id))}.exceptionOrNull()}
                finally {SecurityContextHolder.clearContext()}
            }}
            assertTrue(ready.await(10,TimeUnit.SECONDS));start.countDown()
            val results=futures.map {it.get(30,TimeUnit.SECONDS)}
            assertEquals(1,results.count{it.second==null});val loser=results.single{it.second!=null}
            assertTrue(loser.second is BadRequestException || generateSequence(loser.second){it.cause}.any{it is java.sql.SQLException && it.errorCode==5},loser.second.toString())
            assertEquals(1,r.db{InventoryBatchDao.all().count{it.plan!=null}})
            assertNull(r.db{InventoryBatchDao[loser.first].plan})
            val before=state(r);assertThrows(BadRequestException::class.java){r.inventory.begin(loser.first,plan(id))};assertEquals(before,state(r))
        } finally {start.countDown();pool.shutdownNow();pool.awaitTermination(5,TimeUnit.SECONDS)}
    }
}
