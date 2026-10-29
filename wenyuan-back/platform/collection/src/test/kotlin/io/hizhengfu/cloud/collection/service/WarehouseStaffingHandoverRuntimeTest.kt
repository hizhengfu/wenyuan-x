package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.common.domain.EnumContentStatus
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.sys.file.dao.FileDao
import io.hizhengfu.cloud.sys.file.dao.Files
import io.hizhengfu.cloud.sys.system.dao.*
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
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

class WarehouseStaffingHandoverRuntimeTest {
    @TempDir lateinit var dir:Path
    @Configuration(proxyBeanMethods=false) @EnableTransactionManagement(proxyTargetClass=true) class Transactions
    private class Runtime(val c:AnnotationConfigApplicationContext,val tx:TransactionTemplate,val source:javax.sql.DataSource) {
        val handovers=c.getBean(WarehouseStaffingHandoverService::class.java)
        val staffing=c.getBean(WarehouseStaffingService::class.java)
        val inventory=c.getBean(InventoryComplianceService::class.java)
        fun <T> db(body:()->T):T=tx.execute {body()}
        fun login(id:String) {
            val user=db {c.getBean(CollectionActorDirectory::class.java).requireActive(id)}
            SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(user,null,user.authorities)
        }
    }
    private val at=LocalDateTime.of(2026,8,1,9,0)
    @AfterEach fun clear()=SecurityContextHolder.clearContext()
    private fun fixture(body:(Runtime)->Unit)=departureTestDatabase(dir.resolve("staffing.db")) { source ->
        val manager=SpringTransactionManager(source,DatabaseConfig {})
        val binder=mock(BusinessAttachmentBinder::class.java)
        `when`(binder.resolveSingle(anyString())).thenAnswer {it.getArgument<String>(0)}
        AnnotationConfigApplicationContext().use {c ->
            c.beanFactory.registerSingleton("transactionManager",manager)
            c.beanFactory.registerSingleton("cacheManager",ConcurrentMapCacheManager())
            c.beanFactory.registerSingleton("scope",CollectionScopeResolver("multi","museum"))
            c.beanFactory.registerSingleton("binder",binder)
            c.register(Transactions::class.java,CollectionActorDirectory::class.java,CollectionResponsibleUserService::class.java,
                CollectionPermanentArchiveService::class.java, io.hizhengfu.cloud.common.file.FileReadAccess::class.java,WarehouseStaffingService::class.java,WarehouseStaffingHandoverService::class.java,InventoryComplianceService::class.java)
            c.refresh();SpringContextHolder().setApplicationContext(c)
            val r=Runtime(c,TransactionTemplate(manager),source)
            val initial=SecurityUser(id="operator",name="operator",username="operator",password="",groupId="museum",groupIds=setOf("museum"),postIds=emptySet(),dataType=0,roles=emptySet(),authorities=emptyList(),enabled=true)
            SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(initial,null,initial.authorities)
            r.db {
                SchemaUtils.create(Groups,Users,UserGroups,Roles,RoleUsers,Permissions,RolePermissions,CollectionOrgs,Collections,Warehouses,Loans,Requisitions,CollectionReturns,InventoryBatches,Inventories,CollectionPermanentArchives,CollectionPermanentArchiveFiles,Files)
                GroupDao.new("museum"){name="本馆";type='1';parentId="0";parentIds="0,"}
                CollectionOrgDao.new("museum"){name="本馆";code="M";systemGroupId="museum";ownership=EnumOwnership.STATE}
                listOf("operator","keeper","leader","old","new").forEach {id->UserDao.new(id){name=id;loginName=id;password="unused";enabled=true;groupId="museum"}}
                actorRole("operator","MANAGER",setOf(CollectionConst.STORAGE,CollectionConst.WAREHOUSE_MANAGE,CollectionConst.SELECT))
                actorRole("keeper",CollectionConst.ROLE_STORAGE_HEAD);actorRole("leader",CollectionConst.ROLE_DIRECTOR)
                listOf("operator","old","new").forEach {actorRole(it,CollectionConst.ROLE_KEEPER)}
                WarehouseDao.new("store"){orgId="museum";name="库房";sceneType=EnumLocationScene.STORAGE}
                CollectionDao.new("one"){orgId="museum";totalRegNo="one";name="藏品";nature=EnumCollectionNature.RELIC;categoryId="category";level=EnumRelicLevel.GENERAL;lifeStatus=EnumCollectionStatus.IN_STORE;warehouseId="store";registeredAt=at.minusMonths(1)}
                InventoryBatchDao.new("batch"){orgId="museum";batchName="实际离任全范围清点"}
                listOf("appointment","scope","signature").forEach {id->FileDao.new(id){name="$id.pdf";path="/files/$id.pdf";hash=id;size=100;ext="pdf"}}
            }
            r.login("operator");body(r)
        }
    }
    private fun items(r:Runtime)=(r.handovers.get("store")["items"] as List<*>).filterIsInstance<WarehouseStaffingHandoverItem>()
    private fun prepared(r:Runtime):WarehouseStaffingHandoverRequest {
        val first=WarehouseStaffingVo(setOf("old"),"keeper","leader",at.minusDays(3),"appointment")
        r.staffing.save("store",first)
        r.staffing.save("store",first.copy(keeperUserIds=setOf("new"),assignedAt=at.plusDays(5),replacementReason="实际离任接任"))
        val change=items(r).single()
        r.inventory.begin("batch",InventoryPlanVo(2026,EnumInventoryMethod.FULL,EnumInventoryPurpose.KEEPER_DEPARTURE,"完整责任清点","keeper","leader","scope","old","new",at.plusDays(5),"scope",keeperStaffingChangeIds=setOf(change.assignmentArchiveId)))
        r.inventory.check("batch",InventoryItemCheckVo("one",at,setOf("operator"),"实际库房",true,true,true,true,"完整",false,"scope","scope","scope","signature"))
        r.inventory.complete("batch",InventoryCompletionVo(at.plusDays(2),"keeper","leader","signature",at.plusDays(1),"signature"))
        r.db {UserDao["old"].enabled=false}
        return WarehouseStaffingHandoverRequest(change.assignmentArchiveId,"old",EnumStaffingHandoverKind.DUTY_DEPARTURE,setOf("batch"),"核实完整责任与实际移交","scope",true,"keeper","leader")
    }
    private fun signature()=CollectionCareSignatureVo("本人已核对完整责任与真实清点原件","signature")
    private fun state(r:Runtime)=r.db {listOf(Warehouses,InventoryBatches,CollectionPermanentArchives,CollectionPermanentArchiveFiles).map {t->t.selectAll().map {row->t.columns.map {row[it]}.toString()}.sorted()}}
    private fun stage(r:Runtime,v:WarehouseStaffingHandoverRequest,stage:String):Pair<String,String> {
        if(stage=="REGISTER")return "" to "operator"
        val id=r.handovers.register("store",v)
        if(stage=="keeper")return id to "keeper"
        r.login("keeper");r.handovers.confirm("store",id,"keeper",signature())
        return id to "leader"
    }
    @Test fun `multi museum officers read only current assignment sources and lose access after role revocation`()=fixture { r ->
        prepared(r)
        val change=items(r).single()
        val sources=r.handovers.get("store")["assignmentArchives"] as List<*>
        assertEquals(setOf(change.previousAssignmentArchiveId,change.assignmentArchiveId),
            sources.filterIsInstance<Map<*,*>>().map {it["id"]}.toSet())
        val scope=r.c.getBean(CollectionScopeResolver::class.java)
        val directory=r.c.getBean(CollectionActorDirectory::class.java)
        val policy=CollectionBusinessArchiveReadPolicy(CollectionFileReadAuthorization(scope,directory),mock(CollectionDisclosureService::class.java))
        val reference=CollectionArchiveFileReference(change.assignmentArchiveId,"museum",WarehouseStaffingService.BIZ,"store","ASSIGNED_TEST","appointment","{}")
        r.login("keeper")
        r.handovers.requireAssignmentSourceOriginal("store",change.assignmentArchiveId,"appointment")
        r.db {policy.requireReadable(reference)}
        r.login("leader")
        r.handovers.requireAssignmentSourceOriginal("store",change.previousAssignmentArchiveId,"appointment")
        r.db {policy.requireReadable(reference)}
        r.db {RoleDao.all().single {it.code==CollectionConst.ROLE_STORAGE_HEAD}.enabled=false}
        r.login("keeper")
        assertThrows(BadRequestException::class.java) {r.handovers.requireAssignmentSourceOriginal("store",change.assignmentArchiveId,"appointment")}
        assertThrows(BadRequestException::class.java) {r.db {policy.requireReadable(reference)}}
    }
    companion object {
        @JvmStatic fun handoverLockCases()=listOf("REGISTER","keeper","leader").flatMap {stage ->
            listOf(false,true).flatMap { repeat -> listOf("account","role","museum").flatMap { change ->
                listOf(true,false).map { Arguments.of(stage,repeat,change,it) }
            } }
        }
        @JvmStatic fun rosterEntryChanges()=listOf(false,true).flatMap {repeat ->
            listOf("actor-disabled","actor-role","actor-moved","museum-disabled").map {Arguments.of(repeat,it)}
        }
        @JvmStatic fun rosterWriteChanges()=listOf(false,true).flatMap {update ->
            listOf("actor-disabled","actor-role","actor-moved","museum-disabled","keeper-disabled","leader-role","selected-disabled","selected-ledger").map {Arguments.of(update,it)}
        }
        @JvmStatic fun changedWrites()=listOf("REGISTER","keeper","leader").flatMap {stage ->
            listOf("actor-disabled","actor-role","actor-moved","museum-disabled","warehouse-moved","staffing-cleared").map {Arguments.of(stage,it)}
        } + listOf(Arguments.of("REGISTER","keeper-disabled"),Arguments.of("REGISTER","leader-role"))
    }
    @Tag("postgres-concurrency") @ParameterizedTest @MethodSource("handoverLockCases")
    fun `keeper departure first and repeated stages use qualification after the actual museum lock`(stage:String,repeat:Boolean,change:String,commit:Boolean)=fixture { r ->
        val request=prepared(r)
        val (initialId,actorId)=stage(r,request,stage)
        r.login(actorId)
        var id=initialId
        val operation={if(stage=="REGISTER")r.handovers.register("store",request) else r.handovers.confirm("store",id,stage,signature())}
        if(repeat)id=operation()
        val actor=r.db {r.c.getBean(CollectionActorDirectory::class.java).requireActive(actorId)}
        assertDepartureLockWait(r.source,actor,change,commit,"keeper-$stage-repeat=$repeat",operation,
            {r.db {departureDeclaredState()}},{r.login(actorId)})
    }
    @ParameterizedTest @MethodSource("changedWrites")
    fun `actual handover and both signatures reject current authority or warehouse changes during archive write`(stage:String,change:String)=fixture {r ->
        val v=prepared(r);val (id,actor)=stage(r,v,stage);r.login(actor);val before=state(r)
        val prefix=when(stage){"REGISTER"->"R";"keeper"->"K";else->"L"}
        r.db {
            val sql=when(change) {
                "actor-disabled"->"UPDATE ${Users.tableName} SET enabled=0 WHERE id='$actor'"
                "actor-role"->"UPDATE ${Roles.tableName} SET enabled=0 WHERE id IN (SELECT role_id FROM ${RoleUsers.tableName} WHERE user_id='$actor')"
                "actor-moved"->"UPDATE ${Users.tableName} SET group_id='other-museum' WHERE id='$actor'"
                "museum-disabled"->"UPDATE ${CollectionOrgs.tableName} SET org_status=${EnumOrgStatus.DISABLED.ordinal} WHERE id='museum'"
                "warehouse-moved"->"UPDATE ${Warehouses.tableName} SET org_id='other-museum' WHERE id='store'"
                "staffing-cleared"->"UPDATE ${Warehouses.tableName} SET ${Warehouses.staffing.name}=NULL WHERE id='store'"
                "keeper-disabled"->"UPDATE ${Users.tableName} SET enabled=0 WHERE id='keeper'"
                else->"UPDATE ${Roles.tableName} SET enabled=0 WHERE code='${CollectionConst.ROLE_DIRECTOR}'"
            }
            TransactionManager.current().exec("CREATE TRIGGER changed_handover AFTER INSERT ON ${CollectionPermanentArchives.tableName} WHEN NEW.biz_type='${WarehouseStaffingHandoverService.BIZ}' AND NEW.phase LIKE '${prefix}_%' BEGIN $sql; END")
        }
        assertThrows(BadRequestException::class.java){if(stage=="REGISTER")r.handovers.register("store",v) else r.handovers.confirm("store",id,stage,signature())}
        assertEquals(before,state(r))
        r.db {
            assertTrue(UserDao[actor].enabled==true);assertEquals("museum",UserDao[actor].groupId)
            assertTrue(UserDao["keeper"].enabled==true);assertTrue(RoleDao.all().all {it.enabled==true})
            assertEquals(EnumOrgStatus.ENABLED,CollectionOrgDao["museum"].orgStatus)
            TransactionManager.current().exec("DROP TRIGGER changed_handover")
        }
        val result=if(stage=="REGISTER")r.handovers.register("store",v) else r.handovers.confirm("store",id,stage,signature())
        assertTrue(result.isNotBlank());assertFalse(r.db {UserDao["old"].enabled==true})
    }
    @ParameterizedTest @ValueSource(strings=["digest", "content", "delete"])
    fun `new handover original must survive actual insert unchanged`(change:String)=fixture {r ->
        val v=prepared(r);val before=state(r)
        r.db {
            val sql=when(change) {
                "digest"->"UPDATE ${CollectionPermanentArchives.tableName} SET digest='changed' WHERE id=NEW.id"
                "content"->"UPDATE ${CollectionPermanentArchives.tableName} SET snapshot='{}' WHERE id=NEW.id"
                else->"DELETE FROM ${CollectionPermanentArchives.tableName} WHERE id=NEW.id"
            }
            TransactionManager.current().exec("CREATE TRIGGER handover_original_change AFTER INSERT ON ${CollectionPermanentArchives.tableName} WHEN NEW.biz_type='${WarehouseStaffingHandoverService.BIZ}' AND NEW.phase LIKE 'R_%' BEGIN $sql; END")
        }
        assertThrows(BadRequestException::class.java){r.handovers.register("store",v)}
        assertEquals(before,state(r))
    }
    @ParameterizedTest @ValueSource(strings=["earlier-signer-disabled", "historical-location"])
    fun `legitimate final signer preserves earlier history after departure or location retirement`(change:String)=fixture {r ->
        val v=prepared(r);val id=r.handovers.register("store",v)
        r.login("keeper");r.handovers.confirm("store",id,"keeper",signature())
        r.db {
            if(change=="earlier-signer-disabled")UserDao["keeper"].enabled=false
            else WarehouseDao["store"].status=EnumContentStatus.DELETE
        }
        val before=state(r)
        r.login("leader");assertEquals(id,r.handovers.confirm("store",id,"leader",signature()))
        val after=state(r)
        before.zip(after).forEach {(old,current)->assertTrue(current.containsAll(old))}
        assertEquals("DEPARTURE_CONFIRMED",items(r).single().status)
        assertEquals(id,r.handovers.confirm("store",id,"leader",signature()));assertEquals(after,state(r))
        assertFalse(r.db {UserDao["old"].enabled==true})
        if(change=="earlier-signer-disabled")assertFalse(r.db {UserDao["keeper"].enabled==true})
    }

    private fun roster(old:Boolean=false)=WarehouseStaffingVo(setOf(if(old)"old" else "new"),"keeper","leader",if(old)at.minusDays(3) else at.minusDays(1),"appointment",if(old)null else "实际任职调整")
    private fun rosterChangeSql(change:String):String=when(change) {
        "actor-disabled"->"UPDATE ${Users.tableName} SET enabled=0 WHERE id='operator'"
        "actor-role"->"DELETE FROM ${RolePermissions.tableName}"
        "actor-moved"->"UPDATE ${Users.tableName} SET group_id='other-museum' WHERE id='operator'"
        "museum-disabled"->"UPDATE ${CollectionOrgs.tableName} SET org_status=${EnumOrgStatus.DISABLED.ordinal} WHERE id='museum'"
        "keeper-disabled"->"UPDATE ${Users.tableName} SET enabled=0 WHERE id='keeper'"
        "leader-role"->"UPDATE ${Roles.tableName} SET enabled=0 WHERE code='${CollectionConst.ROLE_DIRECTOR}'"
        "selected-disabled"->"UPDATE ${Users.tableName} SET enabled=0 WHERE id='new'"
        else->"INSERT INTO ${RoleUsers.tableName} (user_id,role_id) VALUES ('new','ledger-role')"
    }
    @ParameterizedTest @MethodSource("rosterEntryChanges")
    fun `roster first save and repeat use current credentials rather than stale login`(repeat:Boolean,change:String)=fixture {r ->
        val v=roster();if(repeat)r.staffing.save("store",v)
        r.db {TransactionManager.current().exec(rosterChangeSql(change))}
        val before=state(r)
        assertThrows(BadRequestException::class.java){r.staffing.save("store",v)}
        assertEquals(before,state(r))
    }
    @ParameterizedTest @MethodSource("rosterWriteChanges")
    fun `roster actual write revalidates operator selected officers and incompatible duties`(update:Boolean,change:String)=fixture {r ->
        if(update)r.staffing.save("store",roster(true))
        val before=state(r)
        r.db {
            RoleDao.new("ledger-role"){name="总账责任";code=CollectionConst.ROLE_LEDGER_CUSTODIAN;enabled=true}
            TransactionManager.current().exec("CREATE TRIGGER roster_write_change AFTER INSERT ON ${CollectionPermanentArchives.tableName} WHEN NEW.biz_type='${WarehouseStaffingService.BIZ}' BEGIN ${rosterChangeSql(change)}; END")
        }
        assertThrows(BadRequestException::class.java){r.staffing.save("store",roster())}
        assertEquals(before,state(r))
        r.db {TransactionManager.current().exec("DROP TRIGGER roster_write_change")}
        assertEquals("store",r.staffing.save("store",roster()))
        assertEquals(setOf("new"),r.db {WarehouseDao["store"].staffing!!.keeperUserIds})
    }

    @ParameterizedTest @ValueSource(strings=["staffing-clear","warehouse-move","warehouse-delete","warehouse-disabled","archive-delete","archive-content","archive-digest","previous-content"])
    fun `roster verified target and complete original chain survive actual update unchanged`(change:String)=fixture {r ->
        r.staffing.save("store",roster(true));val before=state(r)
        r.db {
            val target=change.startsWith("warehouse") || change=="staffing-clear"
            val sql=when(change) {
                "staffing-clear"->"UPDATE ${Warehouses.tableName} SET ${Warehouses.staffing.name}=NULL WHERE id='store'"
                "warehouse-move"->"UPDATE ${Warehouses.tableName} SET org_id='other-museum' WHERE id='store'"
                "warehouse-delete"->"DELETE FROM ${Warehouses.tableName} WHERE id='store'"
                "warehouse-disabled"->"UPDATE ${Warehouses.tableName} SET status=${EnumContentStatus.DELETE.ordinal} WHERE id='store'"
                "archive-delete"->"DELETE FROM ${CollectionPermanentArchives.tableName} WHERE id=NEW.id"
                "archive-content"->"UPDATE ${CollectionPermanentArchives.tableName} SET snapshot='{}' WHERE id=NEW.id"
                "archive-digest"->"UPDATE ${CollectionPermanentArchives.tableName} SET digest='changed' WHERE id=NEW.id"
                else->"UPDATE ${CollectionPermanentArchives.tableName} SET snapshot='{}' WHERE biz_type='${WarehouseStaffingService.BIZ}' AND id<>NEW.id"
            }
            val event=if(target)"AFTER UPDATE OF ${Warehouses.staffing.name} ON ${Warehouses.tableName} WHEN NEW.${Warehouses.staffing.name} IS NOT NULL"
                else "AFTER INSERT ON ${CollectionPermanentArchives.tableName} WHEN NEW.biz_type='${WarehouseStaffingService.BIZ}'"
            TransactionManager.current().exec("CREATE TRIGGER roster_evidence_change $event BEGIN $sql; END")
        }
        assertThrows(BadRequestException::class.java){r.staffing.save("store",roster())}
        assertEquals(before,state(r))
    }
    @Test fun `roster preserves unordered keeper sets and persisted millisecond time`()=fixture {r ->
        val value=roster().copy(keeperUserIds=linkedSetOf("old","new"),assignedAt=roster().assignedAt.withNano(123456789))
        val expected=JsonUtils.fromJson(JsonUtils.toJson(value),WarehouseStaffingVo::class.java)!!
        assertEquals("store",r.staffing.save("store",value))
        assertEquals(expected,r.db {WarehouseDao["store"].staffing})
        val before=state(r)
        assertEquals("store",r.staffing.save("store",expected.copy(keeperUserIds=linkedSetOf("new","old"))))
        assertEquals(before,state(r))
    }
    @Test fun `roster newly granted maintenance permission works without replacing the old login`()=fixture {r ->
        r.db {RolePermissions.deleteAll()};r.login("operator")
        r.db {actorRole("operator","NEW_MAINTAINER",setOf(CollectionConst.WAREHOUSE_MANAGE))}
        assertEquals("store",r.staffing.save("store",roster()))
    }
    @Test fun `roster replacement preserves departed keeper and unchanged archived repeat is not a new assignment`()=fixture {r ->
        r.staffing.save("store",roster(true))
        r.db {UserDao["old"].enabled=false}
        assertEquals("store",r.staffing.save("store",roster()))
        val before=state(r)
        r.db {UserDao["new"].enabled=false}
        assertEquals("store",r.staffing.save("store",roster()));assertEquals(before,state(r))
        assertThrows(BadRequestException::class.java){r.staffing.save("store",roster().copy(replacementReason="新的任职签署"))}
        assertEquals(before,state(r));assertFalse(r.db {UserDao["old"].enabled==true})
    }

}
