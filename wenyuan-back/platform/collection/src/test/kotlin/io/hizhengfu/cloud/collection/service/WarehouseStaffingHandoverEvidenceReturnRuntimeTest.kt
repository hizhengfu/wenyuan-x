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

@Tag("postgres-concurrency")
class WarehouseStaffingHandoverEvidenceReturnRuntimeTest {
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
    private object CommitTrace:org.jetbrains.exposed.v1.core.Table("handover_evidence_commit_trace") { val value=varchar("value",100) }
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
                SchemaUtils.create(CommitTrace,Groups,Users,UserGroups,Roles,RoleUsers,Permissions,RolePermissions,CollectionOrgs,Collections,Warehouses,Loans,Requisitions,CollectionReturns,InventoryBatches,Inventories,CollectionPermanentArchives,CollectionPermanentArchiveFiles,Files)
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
    @ParameterizedTest @ValueSource(strings=["roster-missing","malformed"])
    fun `same identity evidence gap returns through actual proxy and commits outer investigation transaction`(change:String)=evidenceReturn(change)
    @Test fun `missing original reference returns through actual proxy and commits outer investigation transaction`()=evidenceReturn("file-missing")
    private fun evidenceReturn(change:String)=fixture {r->
        assertTrue(r.source is org.postgresql.ds.PGSimpleDataSource)
        assertTrue(org.springframework.aop.support.AopUtils.isAopProxy(r.handovers))
        val first=WarehouseStaffingVo(setOf("old"),"keeper","leader",at.minusDays(3),"appointment")
        r.staffing.save("store",first)
        r.staffing.save("store",first.copy(keeperUserIds=setOf("new"),assignedAt=at.plusDays(5),replacementReason="实际离任接任"))
        assertTrue((r.handovers.get("store")["evidenceErrors"] as List<*>).isEmpty())
        r.db {when(change) {
            "roster-missing"->WarehouseDao["store"].staffing=null
            "malformed"->CollectionPermanentArchiveDao.all().first().let { it.snapshot="invalid";it.digest=CollectionCareEvidence.digest(it.snapshot) }
            "file-missing"->FileDao["appointment"].delete()
        }}
        val before=state(r)
        val result=r.db {
            CommitTrace.insert {it[value]=change}
            r.handovers.get("store")
        }
        assertTrue((result["evidenceErrors"] as List<*>).isNotEmpty())
        assertTrue((result["items"] as List<*>).isEmpty())
        assertTrue((result["records"] as List<*>).isEmpty())
        assertNull(result["assignmentArchives"]);assertNull(result["archives"])
        assertEquals(1L,r.db {CommitTrace.selectAll().count()},"Outer Spring transaction must really commit rather than become rollback-only")
        assertEquals(before,state(r))
    }
    @Test fun `foreign identity is denied before any same identity evidence payload and outer write rolls back`()=fixture {r->
        r.db {
            GroupDao.new("other"){name="外馆";type='1';parentId="0";parentIds="0,"}
            CollectionOrgDao.new("other"){name="外馆";code="O";systemGroupId="other"}
            WarehouseDao.new("foreign"){orgId="other";name="外馆库房";sceneType=EnumLocationScene.STORAGE}
        }
        assertThrows(BadRequestException::class.java) {r.db {
            CommitTrace.insert {it[value]="foreign"}
            r.handovers.get("foreign")
        }}
        assertEquals(0L,r.db {CommitTrace.selectAll().count()})
    }
}
