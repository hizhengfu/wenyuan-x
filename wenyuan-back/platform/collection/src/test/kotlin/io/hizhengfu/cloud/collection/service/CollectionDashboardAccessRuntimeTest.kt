package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.flow.dao.*
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
import org.springframework.context.annotation.*
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.support.TransactionTemplate
import org.sqlite.SQLiteDataSource
import java.nio.file.Path
import java.time.LocalDateTime

class CollectionDashboardAccessRuntimeTest {
    @TempDir lateinit var dir:Path
    @Configuration(proxyBeanMethods=false) @EnableTransactionManagement(proxyTargetClass=true) class Transactions
    private class Runtime(val c:AnnotationConfigApplicationContext,val tx:TransactionTemplate) {
        val service=c.getBean(CollectionDashboardService::class.java)
        val scope=c.getBean(CollectionScopeResolver::class.java)
        fun <T> db(body:()->T):T=tx.execute {body()}
        // SQLite cannot switch a live connection to read-only; join an outer test transaction.
        fun ongoing(limit:Int)=db {service.ongoing(limit)}
        fun auditSummary()=db {service.auditSummary()}
        fun movementTrend(months:Int)=db {service.movementTrend(months)}
        fun grant(vararg permissions:String)=db {RolePermissions.deleteAll();actorRole("reader","CURRENT_STAT",permissions.toSet())}
        fun damage(kind:String)=db {when(kind) {
            "revoked"->RolePermissions.deleteAll()
            "disabled"->UserDao["reader"].enabled=false
            "role"->RoleDao.find {Roles.code eq "FILE_reader"}.single().enabled=false
            "group"->GroupDao["museum"].enabled=false
            "museum"->CollectionOrgDao["museum"].orgStatus=EnumOrgStatus.DISABLED
            "moved"->UserDao["reader"].groupId="other"
            "configured"->ReflectionTestUtils.setField(scope,"defaultOrgId","other")
        }}
        fun instance(id:String,key:String,type:String,round:String?=null)=db {
            FlowInstanceDao.new(id) {definitionId="d";definitionKey="d";definitionName="d";title="流程";businessKey=key;businessType=type;initiator="reader";initiatorName="reader";approvalRoundId=round}
        }
        fun task(id:String,instance:String,title:String,round:String?=null)=db {
            FlowTaskDao.new(id) {instanceId=instance;definitionId="d";type="APPROVAL";nodeKey="node";nodeName=title;approvalRoundId=round}
        }
    }
    private fun fixture(mode:String="single",body:(Runtime)->Unit) {
        val user=SecurityUser(id="reader",name="旧管理员",username="reader",password="",groupId="museum",groupIds=setOf("museum"),postIds=emptySet(),dataType=0,roles=setOf(EnumRole.ADMIN.value),enabled=true,authorities=listOf(SimpleGrantedAuthority("ADMIN")))
        SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(user,null,user.authorities)
        val manager=SpringTransactionManager(SQLiteDataSource().apply {url="jdbc:sqlite:${dir.resolve("dashboard.db")}"},DatabaseConfig {})
        AnnotationConfigApplicationContext().use {c ->
            c.beanFactory.registerSingleton("transactionManager",manager)
            c.beanFactory.registerSingleton("scope",CollectionScopeResolver(mode,"museum"))
            c.beanFactory.registerSingleton("ledger",mock(CollectionLedgerStatisticsService::class.java))
            c.register(Transactions::class.java,CollectionDashboardService::class.java,CollectionUserResolver::class.java)
            c.refresh();SpringContextHolder().setApplicationContext(c)
            val r=Runtime(c,TransactionTemplate(manager))
            try {
                r.db {
                    originalReaderSchema();originalReader("reader",CollectionConst.STAT)
                    SchemaUtils.create(CollectionOrgs,Collections,StorageMovements,AcquisitionPlans,Acquisitions,Appraisals,Requisitions,Loans,
                        CollectionReturns,CollectionTransfers,Deregistrations,Inventories,Exhibitions,Reproductions,CollectionSamplings,WarehouseEntryApplications,FlowInstances,FlowTasks)
                    for(org in listOf("museum","other")) {
                        CollectionOrgDao.new(org){name=org;code=org;systemGroupId=org}
                        CollectionDao.new("item-$org"){orgId=org;name=org;totalRegNo=org;catalogStatus=if(org=="museum")EnumCatalogStatus.APPROVED else EnumCatalogStatus.REJECTED}
                        StorageMovementDao.new("move-$org"){orgId=org;collectionId="item-$org";type=if(org=="museum")EnumMoveType.IN else EnumMoveType.OUT}
                        val rows=listOf(
                            AcquisitionPlanDao.new("plan-$org"){orgId=org;title="plan-$org";year=2026},
                            AcquisitionDao.new("acq-$org"){orgId=org;name="acq-$org"},
                            AppraisalDao.new("appraisal-$org"){orgId=org;collectionId="item-$org"},
                            RequisitionDao.new("req-$org"){orgId=org},
                            LoanDao.new("loan-$org"){orgId=org},
                            CollectionReturnDao.new("return-$org"){orgId=org;sourceBizType=CollectionConst.BIZ_REQUISITION;sourceBizId="req-$org";collectionIds="item-$org"},
                            CollectionTransferDao.new("transfer-$org"){orgId=org;collectionId="item-$org"},
                            DeregistrationDao.new("dereg-$org"){orgId=org;collectionId="item-$org"},
                            InventoryDao.new("inventory-$org"){orgId=org;batchName="inventory-$org"},
                            ExhibitionDao.new("exhibition-$org"){orgId=org;title="exhibition-$org"},
                            ReproductionDao.new("reproduction-$org"){orgId=org;collectionId="item-$org"},
                            CollectionSamplingDao.new("sampling-$org"){orgId=org;collectionId="item-$org";title="sampling-$org"},
                            WarehouseEntryApplicationDao.new("entry-$org"){orgId=org;applicationNo="entry-$org";applicantId="reader";entrantIds="reader";warehouseIds="store";purpose="核查";plannedStart=LocalDateTime.now();plannedEnd=LocalDateTime.now().plusHours(1)}
                        )
                        rows.forEach {it.flowStatus=EnumFlowStatus.AUDIT;it.createBy="reader";it.createTime=LocalDateTime.now()}
                    }
                }
                body(r)
            } finally {SecurityContextHolder.clearContext()}
        }
    }
    @ParameterizedTest @ValueSource(strings=["single","multi"])
    fun `all thirteen ongoing sources trends and audit counts share current museum`(mode:String)=fixture(mode) {r ->
        val ongoing=r.ongoing(100);assertEquals(13,ongoing.size);assertTrue(ongoing.all {it.id.endsWith("museum")})
        assertEquals(13,ongoing.map {it.bizType}.toSet().size);assertEquals(1,r.ongoing(1).size)
        val trend=r.movementTrend(1).single();assertEquals(1,trend.inCount);assertEquals(0,trend.outCount)
        val counts=r.auditSummary();assertEquals(1,counts.approved);assertEquals(0,counts.rejected)
    }
    @ParameterizedTest @ValueSource(strings=["revoked","disabled","role"])
    fun `old administrator login cannot use current revoked identity for dashboard reads`(damage:String)=fixture {r ->
        r.damage(damage)
        assertThrows(BadRequestException::class.java){r.ongoing(0)}
        assertThrows(BadRequestException::class.java){r.auditSummary()}
        assertThrows(BadRequestException::class.java){r.movementTrend(0)}
    }
    @ParameterizedTest @ValueSource(strings=["group","museum","moved","configured"])
    fun `no current visible museum yields no business data`(damage:String)=fixture {r ->
        r.damage(damage);assertTrue(r.ongoing(100).isEmpty())
        assertEquals(0,r.auditSummary().approved);assertEquals(0,r.auditSummary().rejected)
        assertEquals(0,r.movementTrend(1).single().inCount);assertEquals(0,r.movementTrend(1).single().outCount)
    }
    @Test fun `actual reassignment and explicit cross museum statistics use current grants`()=fixture("multi") {r ->
        r.damage("moved");assertTrue(r.ongoing(100).all {it.id.endsWith("other")})
        assertEquals(1,r.auditSummary().rejected);assertEquals(1,r.movementTrend(1).single().outCount)
        r.grant(CollectionConst.SELECT,CollectionConst.CROSS_ORG);assertEquals(26,r.ongoing(100).size)
        assertEquals(1,r.auditSummary().approved);assertEquals(1,r.auditSummary().rejected)
    }
    @ParameterizedTest @ValueSource(strings=["single","multi"])
    fun `mismatched workflow does not expose foreign task names`(mode:String)=fixture(mode) {r ->
        if(mode=="multi")r.grant(CollectionConst.STAT,CollectionConst.CROSS_ORG)
        r.instance("foreign","exhibition-other",CollectionConst.BIZ_EXHIBITION);r.task("foreign-task","foreign","外馆保密节点")
        r.db {ExhibitionDao["exhibition-museum"].flowInstanceId="foreign";ExhibitionDao["exhibition-other"].flowInstanceId="foreign"}
        val row=r.ongoing(100).single {it.id=="exhibition-museum"}
        assertEquals("流程关联异常，请核实",row.stage)
    }
    @Test fun `current matching workflow includes current round tasks only`()=fixture {r ->
        r.instance("current","exhibition-museum",CollectionConst.BIZ_EXHIBITION,"r2")
        r.task("old","current","过期轮次","r1");r.task("new","current","当前确认","r2")
        r.db {ExhibitionDao["exhibition-museum"].flowInstanceId="current"}
        assertEquals("当前确认",r.ongoing(100).single {it.id=="exhibition-museum"}.stage)
    }

    @ParameterizedTest @ValueSource(strings=["single","multi"])
    fun `workbench business context uses backend museum and fresh grants`(mode:String)=fixture(mode) {r ->
        fun context() = r.db { CollectionOrgService(r.scope).businessContext() }
        r.grant(CollectionConst.ORG_MANAGE)
        val current = context()
        assertEquals("museum", current["defaultOrgId"])
        assertEquals(listOf(mapOf("value" to "museum", "label" to "museum")), current["orgs"])
        r.damage("revoked")
        assertThrows(BadRequestException::class.java) { context() }
    }

    @ParameterizedTest @ValueSource(strings=["single","multi"])
    fun `workbench museum options cannot use old membership after reassignment`(mode:String)=fixture(mode) {r ->
        r.grant(CollectionConst.LEDGER_FINANCE)
        r.damage("moved")
        val current = r.db { CollectionOrgService(r.scope).businessContext() }
        if (mode == "single") {
            assertEquals(emptyList<Any>(), current["orgs"])
            assertNull(current["defaultOrgId"])
        } else {
            assertEquals(listOf(mapOf("value" to "other", "label" to "other")), current["orgs"])
            assertEquals("other", current["defaultOrgId"])
        }
    }
}
