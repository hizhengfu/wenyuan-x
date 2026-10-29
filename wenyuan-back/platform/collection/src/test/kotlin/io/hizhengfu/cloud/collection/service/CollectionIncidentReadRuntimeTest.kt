package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.file.LocalFileObjectStorage
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.sys.file.dao.*
import io.hizhengfu.cloud.sys.system.dao.*
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.*
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.*
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.support.TransactionTemplate
import org.sqlite.SQLiteDataSource
import java.nio.file.Path
import java.time.LocalDateTime

class CollectionIncidentReadRuntimeTest {
    @TempDir lateinit var dir:Path
    @Configuration(proxyBeanMethods=false) @EnableTransactionManagement(proxyTargetClass=true) class Transactions
    private class Runtime(val c:AnnotationConfigApplicationContext,val tx:TransactionTemplate) {
        val incidents=c.getBean(CollectionIncidentService::class.java)
        val work=c.getBean(IncidentWorkService::class.java)
        val relocation=c.getBean(IncidentRelocationService::class.java)
        lateinit var incidentId:String
        lateinit var workId:String
        lateinit var relocationId:String
        fun <T> db(body:()->T):T=tx.execute {body()}
        fun permissions(vararg values:String)=db {RolePermissions.deleteAll();actorRole("reader","FILE_reader",values.toSet())}
        fun read(view:String):Any?=when(view) {
            "incident"->incidents.detail(incidentId)
            "page"->incidents.page(MockHttpServletRequest())
            "work"->work.detail(workId)
            "work-list"->work.list(incidentId)
            "work-context"->work.context("one")
            "relocation"->relocation.detail(relocationId)
            "relocation-list"->relocation.list(incidentId)
            else->relocation.context("one")
        }
    }
    private val at=LocalDateTime.of(2026,9,1,10,0)
    private fun fixture(mode:String="single",realOriginal:Boolean=false,body:(Runtime)->Unit) {
        val manager=SpringTransactionManager(SQLiteDataSource().apply {url="jdbc:sqlite:${dir.resolve("incident-read.db")}"},DatabaseConfig {})
        val login=SecurityUser(id="reader",name="事故读取人员",username="reader",password="",groupId="museum",groupIds=setOf("museum"),postIds=emptySet(),dataType=0,roles=setOf("ADMIN"),authorities=listOf("ADMIN",CollectionConst.CROSS_ORG).map(::SimpleGrantedAuthority),enabled=true)
        SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(login,null,login.authorities)
        try {AnnotationConfigApplicationContext().use {c ->
            c.beanFactory.registerSingleton("transactionManager",manager)
            c.beanFactory.registerSingleton("scope",CollectionScopeResolver(mode,"museum"))
            c.beanFactory.registerSingleton("localStorage",LocalFileObjectStorage(dir.toString()))
            c.beanFactory.registerSingleton("cacheManager",ConcurrentMapCacheManager())
            c.beanFactory.registerSingleton("users",mock(CollectionResponsibleUserService::class.java))
            val binder=mock(BusinessAttachmentBinder::class.java)
            `when`(binder.resolveSingle(anyString())).thenAnswer {it.getArgument<String>(0)}
            c.beanFactory.registerSingleton("binder",binder)
            c.register(Transactions::class.java,CollectionPermanentArchiveService::class.java, io.hizhengfu.cloud.common.file.FileReadAccess::class.java,CollectionIncidentSourceService::class.java,
                CollectionIncidentService::class.java,IncidentWorkService::class.java,IncidentRelocationService::class.java,WarehouseStaffingService::class.java)
            c.refresh();SpringContextHolder().setApplicationContext(c)
            val r=Runtime(c,TransactionTemplate(manager))
            r.db {
                originalReaderSchema()
                SchemaUtils.create(CollectionOrgs,Collections,CollectionIncidents,CollectionIncidentRecords,CollectionIncidentItems,
                    IncidentWorks,IncidentWorkRecords,IncidentRelocations,IncidentRelocationRecords,CollectionOperationLocks,
                    CollectionPermanentArchives,CollectionPermanentArchiveFiles,Files,Warehouses,StorageMovements)
                listOf("museum","other").forEach {id->CollectionOrgDao.new(id){name=id;code=id;systemGroupId=id}}
                originalReader("reader",CollectionConst.INCIDENT)
                actorRole("reader","SETUP_STAFFING",setOf(CollectionConst.WAREHOUSE_MANAGE))
                listOf("from","to").forEach {id->WarehouseDao.new(id){orgId="museum";name=id;sceneType=EnumLocationScene.STORAGE;type=EnumWarehouseType.WAREHOUSE}}
                CollectionDao.new("one"){orgId="museum";name="本馆藏品";totalRegNo="M-1";lifeStatus=EnumCollectionStatus.IN_STORE;warehouseId="from"}
                FileDao.new("proof"){name="实际原件.pdf";path="proof";hash=CollectionCareEvidence.digest("ORIGINAL");size=8;ext="pdf"}
            }
            java.nio.file.Files.writeString(dir.resolve("proof"),"ORIGINAL")
            listOf("from","to").forEach {id->c.getBean(WarehouseStaffingService::class.java).save(id,WarehouseStaffingVo(setOf("custodian","receiver"),"keeper","leader",at.minusDays(1),"proof"))}
            r.incidentId=r.incidents.create(IncidentFactsVo(title="事故核对",discoveredAt=at,location="原库房",reporterUserId="reader",description="发现裂隙",immediateMeasures="隔离保护",items=listOf(IncidentItemVo("one",true,EnumIncidentSecurity.NONE,false,"现状记录")),evidenceFileIds=if(realOriginal)"proof" else null))
            r.workId=r.work.create(IncidentWorkTestSupport(r.work,r.incidents,at,"proof").plan("one",r.incidentId))
            r.relocationId=r.relocation.create(IncidentRelocationPlanVo("one",setOf(r.incidentId),at,at.plusHours(1),at.plusHours(2),"from","to","必要搬移","当前状态","人工搬移","独立包护","馆内路线","现场保护","风险停止",at,true,"department","conservation","department-head","keeper","leader","custodian","receiver","security","本馆","supervisor","proof","proof","proof","proof",
                transportStandardReference="GB/T 23862-2024",transportStandardApplicability="逐件核对文物包装运输条件"))
            body(r)
        }} finally {SecurityContextHolder.clearContext()}
    }
    @Test fun `incident details retain records and warn when original bytes change`()=fixture(realOriginal=true) {r ->
        val original=r.db {CollectionPermanentArchiveDao.all().single {it.bizType==CollectionConst.BIZ_INCIDENT && it.bizId==r.incidentId}}
        val metadata=r.db {CollectionPermanentArchiveFileDao.all().single {it.archiveId==original.id.value}.metadata}
        assertNotNull(CollectionArchiveFileIdentity.content(io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils.mapper.readTree(metadata)))
        assertNotNull(r.incidents.detail(r.incidentId))
        val path=dir.resolve("proof")
        val timestamp=java.nio.file.Files.getLastModifiedTime(path)
        java.nio.file.Files.writeString(path,"CHANGED!")
        java.nio.file.Files.setLastModifiedTime(path,timestamp)
        assertUnavailableOriginals(r.incidents.detail(r.incidentId))
        assertUnavailableOriginals(r.work.detail(r.workId))
        assertUnavailableOriginals(r.relocation.detail(r.relocationId))
        java.nio.file.Files.delete(path)
        assertUnavailableOriginals(r.incidents.detail(r.incidentId))
        assertUnavailableOriginals(r.work.detail(r.workId))
        assertUnavailableOriginals(r.relocation.detail(r.relocationId))
    }
    @ParameterizedTest @ValueSource(strings=["incident","page","work","work-list","work-context","relocation","relocation-list","relocation-context"])
    fun `incident reads reject removed current duty despite old administrator login`(view:String)=fixture {r ->
        assertNotNull(r.read(view));r.permissions()
        assertThrows(BadRequestException::class.java){r.read(view)}
    }
    @ParameterizedTest @ValueSource(strings=["incident","page","work","work-list","work-context","relocation","relocation-list","relocation-context"])
    fun `ordinary collection viewing duty does not authorize incident material`(view:String)=fixture {r ->
        r.permissions(CollectionConst.SELECT)
        assertThrows(BadRequestException::class.java){r.read(view)}
    }
    @ParameterizedTest @ValueSource(strings=["incident","work","relocation"])
    fun `foreign records are rejected before their content is parsed`(view:String)=fixture {r ->
        r.db {when(view) {
            "incident"->CollectionIncidentRecordDao.all().first().apply {orgId="other";content="not-json"}
            "work"->IncidentWorkRecordDao.all().first().apply {orgId="other";content="not-json"}
            else->IncidentRelocationRecordDao.all().first().apply {orgId="other";content="not-json"}
        }}
        val e=assertThrows(BadRequestException::class.java){r.read(view)}
        assertTrue(e.message!!.contains("所属馆"))
    }
    @ParameterizedTest @ValueSource(strings=["incident","work","work-list","relocation","relocation-list"])
    fun `extra foreign original headers cannot attach to an owned case`(view:String)=fixture {r ->
        val biz=when {view.startsWith("work")->CollectionConst.BIZ_INCIDENT_WORK;view.startsWith("relocation")->CollectionConst.BIZ_INCIDENT_RELOCATION;else->CollectionConst.BIZ_INCIDENT}
        val id=when(biz){CollectionConst.BIZ_INCIDENT_WORK->r.workId;CollectionConst.BIZ_INCIDENT_RELOCATION->r.relocationId;else->r.incidentId}
        r.db {CollectionPermanentArchiveDao.new {orgId="other";bizType=biz;bizId=id;phase="EXTRA";snapshot="{}";digest=CollectionCareEvidence.digest(snapshot)}}
        val count=r.db {CollectionPermanentArchiveDao.count()}
        assertThrows(BadRequestException::class.java){r.read(view)}
        assertEquals(count,r.db {CollectionPermanentArchiveDao.count()})
    }
    @ParameterizedTest @ValueSource(strings=["single","multi"])
    fun `current cross grant restores allowed history but disabled account is still refused`(mode:String)=fixture(mode) {r ->
        r.db {UserDao["reader"].groupId="other"}
        assertThrows(BadRequestException::class.java){r.read("incident")}
        r.permissions(CollectionConst.INCIDENT,CollectionConst.CROSS_ORG)
        for(view in listOf("incident","work","relocation"))assertNotNull(r.read(view))
        r.db {UserDao["reader"].enabled=false}
        for(view in listOf("incident","page","work","relocation"))assertThrows(BadRequestException::class.java){r.read(view)}
    }
    @ParameterizedTest @ValueSource(strings=["work","work-context","relocation","relocation-context"])
    fun `foreign restriction rows cannot enter facts comparison or planning context`(view:String)=fixture {r ->
        r.db {CollectionIncidentItemDao.all().first().orgId="other"}
        assertThrows(BadRequestException::class.java){r.read(view)}
    }
    @Test fun `relocation context does not borrow a foreign warehouse name`()=fixture {r ->
        r.db {WarehouseDao["from"].orgId="other";WarehouseDao["from"].name="外馆保密场所"}
        assertThrows(BadRequestException::class.java){r.read("relocation-context")}
    }
    @Test fun `historical case originals are retained after current collection ownership changes`()=fixture {r ->
        val before=r.db {CollectionPermanentArchiveDao.all().map {it.snapshot to it.digest}}
        r.db {CollectionDao["one"].orgId="other";CollectionDao["one"].name="移交后名称";CollectionIncidentItemDao.all().forEach {it.restricted=false}}
        val value=r.incidents.detail(r.incidentId)["facts"] as IncidentFactsSnapshot
        assertEquals("本馆藏品",value.items.single().catalog.formalName)
        assertNotNull(r.work.detail(r.workId));assertNotNull(r.relocation.detail(r.relocationId))
        assertEquals(before,r.db {CollectionPermanentArchiveDao.all().map {it.snapshot to it.digest}})
    }

    @ParameterizedTest @ValueSource(strings=["incident","work","relocation"])
    fun `current revoked duty also rejects direct incident mutations`(kind:String)=fixture {r ->
        val detail=r.incidents.detail(r.incidentId)
        val facts=(detail["facts"] as IncidentFactsSnapshot).facts
        val work=(r.work.detail(r.workId)["plan"] as IncidentWorkPlanSnapshot).plan
        val relocation=(r.relocation.detail(r.relocationId)["plan"] as IncidentRelocationPlanSnapshot).plan
        val before=r.db {CollectionPermanentArchiveDao.count()}
        r.permissions()
        assertThrows(BadRequestException::class.java) {when(kind) {
            "incident"->r.incidents.amend(r.incidentId,facts.copy(description="撤权后的改写",replacementReason="核实事实",replacementFileIds="proof"),detail["factsId"] as String)
            "work"->r.work.create(work)
            else->r.relocation.create(relocation)
        }}
        assertEquals(before,r.db {CollectionPermanentArchiveDao.count()})
    }

    @ParameterizedTest @ValueSource(strings=["work","relocation"])
    fun `facts comparison checks source original even when its current record id is unchanged`(view:String)=fixture {r ->
        r.db {CollectionPermanentArchiveDao.all().first {it.bizType==CollectionConst.BIZ_INCIDENT && it.bizId==r.incidentId}.apply {snapshot="{}";digest=CollectionCareEvidence.digest(snapshot)}}
        assertThrows(BadRequestException::class.java){r.read(view)}
    }

    private fun businessState(r:Runtime)=r.db {
        listOf(CollectionIncidents,CollectionIncidentRecords,CollectionIncidentItems,
            IncidentWorks,IncidentWorkRecords,IncidentRelocations,IncidentRelocationRecords,
            CollectionPermanentArchives,CollectionPermanentArchiveFiles,CollectionOperationLocks).associate {table ->
            table.tableName to table.selectAll().map {row ->
                table.columns.map {column -> row[column].toString()}
            }.sortedBy {it.toString()}
        }
    }

    @Test fun `current cross museum history never grants foreign incident or plan writes`()=fixture("multi") {r ->
        val detail=r.incidents.detail(r.incidentId)
        val facts=(detail["facts"] as IncidentFactsSnapshot).facts
        val work=(r.work.detail(r.workId)["plan"] as IncidentWorkPlanSnapshot).plan
        val relocation=(r.relocation.detail(r.relocationId)["plan"] as IncidentRelocationPlanSnapshot).plan
        r.db {UserDao["reader"].groupId="other"}
        r.permissions(CollectionConst.INCIDENT,CollectionConst.CROSS_ORG)
        for(view in listOf("incident","work","relocation"))assertNotNull(r.read(view))
        val before=businessState(r)
        assertThrows(BadRequestException::class.java) {
            r.incidents.amend(r.incidentId,facts.copy(description="跨馆只读不能改写",replacementReason="核对事实",replacementFileIds="proof"),detail["factsId"] as String)
        }
        assertEquals(before,businessState(r))
        assertThrows(BadRequestException::class.java){r.work.create(work)}
        assertEquals(before,businessState(r))
        assertThrows(BadRequestException::class.java){r.relocation.create(relocation)}
        assertEquals(before,businessState(r))
    }

    @ParameterizedTest @ValueSource(strings=["incident","work","relocation"])
    fun `foreign case header rejects reads without changing retained business evidence`(view:String)=fixture {r ->
        assertNotNull(r.read(view))
        r.db {when(view) {
            "incident"->CollectionIncidentDao[r.incidentId].orgId="other"
            "work"->IncidentWorkDao[r.workId].orgId="other"
            else->IncidentRelocationDao[r.relocationId].orgId="other"
        }}
        val before=businessState(r)
        assertThrows(BadRequestException::class.java){r.read(view)}
        assertEquals(before,businessState(r))
    }

}
