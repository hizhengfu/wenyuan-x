package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.api.WarehouseEntryApplicationController
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.flow.service.FlowInstanceService
import io.hizhengfu.cloud.flow.dao.FlowInstanceLogs
import io.hizhengfu.cloud.flow.dao.FlowInstanceLogDao
import io.hizhengfu.cloud.sys.file.dao.*
import io.hizhengfu.cloud.sys.system.dao.*
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.Mockito.*
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.*
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.support.TransactionTemplate
import org.sqlite.SQLiteDataSource
import java.nio.file.Path
import java.time.LocalDateTime

class WarehouseAttendanceAccessRuntimeTest {
    @TempDir lateinit var dir:Path
    @Configuration(proxyBeanMethods=false) @EnableTransactionManagement(proxyTargetClass=true) @EnableMethodSecurity class Transactions
    companion object {
        val at:LocalDateTime=LocalDateTime.of(2026,9,1,10,0)
        fun login(id:String="verifier",authority:String="ADMIN") {
            val user=SecurityUser(id=id,name=id,username=id,password="",groupId="museum",groupIds=setOf("museum"),postIds=emptySet(),dataType=0,roles=emptySet(),authorities=listOf(SimpleGrantedAuthority(authority)),enabled=true)
            SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(user,null,user.authorities)
        }
    }
    private class Runtime(val c:AnnotationConfigApplicationContext,val tx:TransactionTemplate,val exit:Boolean,val binder:BusinessAttachmentBinder) {
        val service=c.getBean(WarehouseEntryApplicationService::class.java)
        var controllerRevision=""
        fun <T> db(body:()->T):T=tx.execute {body()}
        fun value()=WarehouseAttendanceVo(if(exit)at.plusHours(1) else at,setOf("k1","k2"),identityChecked=true,continuousAccompaniment=true,signedRecordFileIds="proof")
        fun write()=if(exit)service.checkOut("visit",value()) else service.checkIn("visit",value())
        fun controllerWrite()=c.getBean(WarehouseEntryApplicationController::class.java).let {
            if(exit)it.checkOut("visit",value(),"museum",controllerRevision) else it.checkIn("visit",value(),"museum",controllerRevision)
        }
        fun read()=service.get("visit")
        fun revoke()=RolePermissions.deleteWhere {permissionId eq CollectionConst.WAREHOUSE_ENTRY_VERIFY}
        fun unchanged()=db {
            val e=WarehouseEntryApplicationDao["visit"]
            assertEquals(if(exit)at else null,e.actualEntryAt);assertNull(e.actualExitAt)
            assertEquals(if(exit)"former" else null,e.checkInBy);assertNull(e.checkOutBy)
            assertEquals(0L,CollectionPermanentArchiveDao.count());assertEquals(0L,CollectionPermanentArchiveFileDao.count())
            assertEquals("museum",e.orgId);assertEquals("k1,k2",e.entrantIds);assertEquals("flow-original",e.flowInstanceId)
        }
    }
    private fun fixture(exit:Boolean,body:(Runtime)->Unit) {
        login()
        val manager=SpringTransactionManager(SQLiteDataSource().apply {url="jdbc:sqlite:${dir.resolve("attendance-${System.nanoTime()}.db")}"})
        val scope=CollectionScopeResolver("single","museum")
        val binder=mock(BusinessAttachmentBinder::class.java)
        `when`(binder.resolveSingle(anyString())).thenAnswer {it.getArgument<String>(0)}
        val statutory=spy(WarehouseEntryStatutoryService(mock(WarehouseStaffingService::class.java),mock(CollectionResponsibleUserService::class.java),CollectionPermanentArchiveService(binder,scope,readAccess=unprotectedArchiveReadAccessProvider())))
        // 审批正确性由完整库房合规回归覆盖；本夹具隔离现行职责及实际写入后的事务行为。
        val placeholder=mock(WarehouseEntryApplicationDao::class.java)
        doNothing().`when`(statutory).requireApproved(any(WarehouseEntryApplicationDao::class.java) ?: placeholder)
        try { AnnotationConfigApplicationContext().use {c->
            c.beanFactory.registerSingleton("transactionManager",manager);c.beanFactory.registerSingleton("scope",scope)
            c.beanFactory.registerSingleton("cacheManager",ConcurrentMapCacheManager());c.beanFactory.registerSingleton("flow",mock(FlowInstanceService::class.java))
            c.beanFactory.registerSingleton("binder",binder);c.beanFactory.registerSingleton("statutory",statutory)
            c.beanFactory.registerSingleton("readAccess",unprotectedArchiveReadAccessProvider().getIfAvailable()!!)
            c.beanFactory.registerSingleton("staff",mock(WarehouseStaffingService::class.java))
            c.beanFactory.registerSingleton("names",mock(CollectionUserResolver::class.java).also {`when`(it.names(anyCollection())).thenAnswer {call->(call.arguments[0] as Collection<*>).filterNotNull().associate {id->id.toString() to id.toString()}}})
            c.register(Transactions::class.java,CollectionPermanentArchiveService::class.java,WarehouseEntryApplicationService::class.java,WarehouseEntryApplicationController::class.java)
            c.refresh();SpringContextHolder().setApplicationContext(c)
            val r=Runtime(c,TransactionTemplate(manager),exit,binder)
            r.db {
                originalReaderSchema()
                originalReader("verifier",CollectionConst.SELECT);actorRole("verifier","VERIFY",setOf(CollectionConst.WAREHOUSE_ENTRY_VERIFY))
                originalReader("replacement",CollectionConst.WAREHOUSE_ENTRY_VERIFY)
                SchemaUtils.create(CollectionOrgs,Warehouses,WarehouseEntryApplications,CollectionPermanentArchives,CollectionPermanentArchiveFiles,Files,FlowInstanceLogs)
                listOf("museum","other").forEach {id->CollectionOrgDao.new(id){name=id;code=id}}
                WarehouseDao.new("store"){orgId="museum";name="库房";sceneType=EnumLocationScene.STORAGE}
                FileDao.new("proof"){name="合成签署材料";path="synthetic-proof";hash="synthetic";size=1;ext="pdf"}
                WarehouseEntryApplicationDao.new("visit") {
                    orgId="museum";applicationNo="TEST-VISIT";applicantId="k1";entrantIds="k1,k2";warehouseIds="store";purpose="实际工作"
                    plannedStart=at;plannedEnd=at.plusHours(1);flowStatus=EnumFlowStatus.PASS;flowInstanceId="flow-original";approvedAt=at.minusHours(1)
                    dossier=WarehouseEntryDossierVo(EnumWarehouseEntryKind.KEEPERS,keeperUserId="head",identityFileIds="proof")
                    baseline=WarehouseEntryBaselineVo(listOf(WarehouseStaffingItemVo("store","库房",
                        WarehouseStaffingVo(setOf("k1","k2"),"head","leader",at.minusDays(1),"proof"))))
                    if(exit){actualEntryAt=at;entryRecord=WarehouseAttendanceVo(at,setOf("k1","k2"),identityChecked=true,continuousAccompaniment=true,signedRecordFileIds="old-entry");checkInBy="former"}
                }
            }
            r.controllerRevision=r.service.attendanceContext("visit").revision
            body(r)
        }} finally {SecurityContextHolder.clearContext()}
    }

    @ParameterizedTest @ValueSource(booleans=[false,true])
    fun `stale administrator login with current read permission cannot verify attendance`(exit:Boolean)=fixture(exit) {r->
        r.db {r.revoke()}
        assertEquals("visit",r.read().id)
        assertThrows(BadRequestException::class.java){r.write()}
        r.unchanged()
    }
    @ParameterizedTest @ValueSource(booleans=[false,true])
    fun `secured controller old administrator permission cannot replace current verification duty`(exit:Boolean)=fixture(exit) {r->
        r.db {r.revoke()}
        assertThrows(BadRequestException::class.java){r.controllerWrite()}
        r.unchanged()
    }
    @ParameterizedTest @ValueSource(booleans=[false,true])
    fun `secured controller requires both login gate and actual current verification duty`(exit:Boolean)=fixture(exit) {r->
        login(authority=CollectionConst.SELECT)
        assertThrows(AccessDeniedException::class.java){r.controllerWrite()}
        r.unchanged()
        login(authority=CollectionConst.WAREHOUSE_ENTRY_VERIFY)
        assertDoesNotThrow {r.controllerWrite()}
        r.db {assertEquals(1L,CollectionPermanentArchiveDao.count())}
    }
    @ParameterizedTest @ValueSource(booleans=[false,true])
    fun `current cross museum reading after reassignment cannot authorize old museum attendance`(exit:Boolean)=fixture(exit) {r->
        r.db {UserDao["verifier"].groupId="other";actorRole("verifier","CROSS",setOf(CollectionConst.CROSS_ORG))}
        assertEquals("visit",r.read().id)
        assertThrows(BadRequestException::class.java){r.write()}
        r.unchanged()
    }
    @ParameterizedTest @ValueSource(booleans=[false,true])
    fun `valid current verifier records attendance and preserves idempotent actor`(exit:Boolean)=fixture(exit) {r->
        val first=r.write();val second=r.write()
        assertEquals("visit",first.id);assertEquals(first,second)
        assertEquals("verifier",if(exit)second.checkOutBy else second.checkInBy)
        r.db {assertEquals(1L,CollectionPermanentArchiveDao.count());assertEquals(1L,CollectionPermanentArchiveFileDao.count())}
        r.db {r.revoke()}
        assertThrows(BadRequestException::class.java){r.write()}
        r.db {assertEquals(1L,CollectionPermanentArchiveDao.count());assertEquals("verifier",if(exit)WarehouseEntryApplicationDao["visit"].checkOutBy else WarehouseEntryApplicationDao["visit"].checkInBy)}
    }
    @ParameterizedTest @CsvSource("false,revoked","true,revoked","false,actor","true,actor","false,logged-out","true,logged-out")
    fun `archive callback cannot finish attendance after authority or actor changes`(exit:Boolean,kind:String)=fixture(exit) {r->
        doAnswer {when(kind){"revoked"->r.revoke();"actor"->login("replacement");else->SecurityContextHolder.clearContext()};null}
            .`when`(r.binder).bindSingle(anyString(),anyString(),anyString())
        assertThrows(BadRequestException::class.java){r.write()}
        r.unchanged()
    }
    @ParameterizedTest @CsvSource("false,revoked","true,revoked","false,moved","true,moved","false,group","true,group","false,org","true,org","false,source","true,source","false,entrants","true,entrants","false,flow","true,flow")
    fun `actual attendance SQL rechecks current authority and original source before commit`(exit:Boolean,kind:String)=fixture(exit) {r->
        r.db {
            val mutation=when(kind) {
                "revoked"->"DELETE FROM sys_role_permission WHERE permission_id='${CollectionConst.WAREHOUSE_ENTRY_VERIFY}'"
                "moved"->"UPDATE sys_user SET group_id='other' WHERE id='verifier'"
                "group"->"UPDATE sys_group SET enabled=0 WHERE id='museum'"
                "org"->"UPDATE col_org SET org_status=1 WHERE id='museum'"
                "source"->"UPDATE col_warehouse_entry_apply SET org_id='other' WHERE id='visit'"
                "entrants"->"UPDATE col_warehouse_entry_apply SET entrant_ids='k1' WHERE id='visit'"
                else->"UPDATE col_warehouse_entry_apply SET flow_instance_id='foreign' WHERE id='visit'"
            }
            val column=if(exit)"actual_exit_at" else "actual_entry_at"
            TransactionManager.current().exec("CREATE TRIGGER change_after_attendance AFTER UPDATE OF $column ON col_warehouse_entry_apply WHEN NEW.$column IS NOT NULL BEGIN $mutation; END")
        }
        assertThrows(BadRequestException::class.java){r.write()}
        r.unchanged()
        r.db {assertEquals("museum",UserDao["verifier"].groupId);assertTrue(GroupDao["museum"].enabled);assertEquals(EnumOrgStatus.ENABLED,CollectionOrgDao["museum"].orgStatus)}
    }
    @ParameterizedTest @ValueSource(booleans=[false,true])
    fun `opened confirmation binds actor museum source and saved attendance`(exit:Boolean)=fixture(exit) {r->
        val context=r.service.attendanceContext("visit")
        assertEquals("museum",context.orgId);assertEquals("verifier",context.actorId)
        assertEquals("visit",context.application.id);assertEquals(!exit,context.canCheckIn);assertEquals(exit,context.canCheckOut)
        assertTrue(context.revision.matches(Regex("[0-9a-f]{64}")))
        assertEquals(context.revision,r.service.attendanceContext("visit").revision)
        assertDoesNotThrow {r.controllerWrite()}
        val saved=r.service.attendanceContext("visit")
        assertNotEquals(context.revision,saved.revision)
        assertFalse(saved.canCheckIn);assertEquals(!exit,saved.canCheckOut)
        assertEquals("verifier",if(exit)saved.application.checkOutBy else saved.application.checkInBy)
        assertThrows(BadRequestException::class.java){r.controllerWrite()}
        r.controllerRevision=saved.revision
        assertDoesNotThrow {r.controllerWrite()}
        r.db {assertEquals(1L,CollectionPermanentArchiveDao.count())}
    }
    @ParameterizedTest @CsvSource("false,purpose","true,purpose","false,microseconds","true,microseconds","false,warehouse","true,warehouse","false,staffing","true,staffing","false,file","true,file","false,approval","true,approval","false,archive","true,archive","false,reference","true,reference","false,actor","true,actor")
    fun `changed inputs after opening invalidate the confirmation before archive write`(exit:Boolean,kind:String)=fixture(exit) {r->
        r.db {
            when(kind) {
                "purpose"->WarehouseEntryApplicationDao["visit"].purpose="变更工作"
                "microseconds"->WarehouseEntryApplicationDao["visit"].plannedEnd=at.plusHours(1).plusNanos(1000)
                "warehouse"->WarehouseDao["store"].name="新库房名"
                "staffing"->WarehouseDao["store"].staffing=WarehouseStaffingVo(setOf("k1","k2"),"head","leader",at.minusDays(1),"proof")
                "file"->FileDao["proof"].hash="changed-proof"
                "approval"->FlowInstanceLogDao.new("approval"){instanceId="flow-original";action="APPROVE";operator="head";createTime=at.minusHours(1)}
                "archive"->CollectionPermanentArchiveDao.new("historical"){orgId="museum";bizType=CollectionConst.BIZ_WAREHOUSE_ENTRY;bizId="visit";phase="PREPARED";snapshot="{}";digest="old"}
                "reference"->CollectionPermanentArchiveFileDao.new("orphan"){orgId="museum";archiveId="foreign";bizId="visit";fileId="proof";metadata="{}"}
                "actor"->login("replacement")
            }
        }
        assertThrows(BadRequestException::class.java){r.controllerWrite()}
        r.db {
            val e=WarehouseEntryApplicationDao["visit"]
            assertEquals(if(exit)at else null,e.actualEntryAt);assertNull(e.actualExitAt)
            assertEquals(if(kind=="archive")1L else 0L,CollectionPermanentArchiveDao.count())
            assertEquals(if(kind=="reference")1L else 0L,CollectionPermanentArchiveFileDao.count())
        }
    }
    @ParameterizedTest @ValueSource(booleans=[false,true])
    fun `confirmation requires matching nonblank museum and revision and current read duty`(exit:Boolean)=fixture(exit) {r->
        listOf("other" to r.controllerRevision,"" to r.controllerRevision,"museum" to "",null to r.controllerRevision,"museum" to null).forEach {(org,revision)->
            assertThrows(BadRequestException::class.java){if(exit)r.service.checkOut("visit",r.value(),org,revision) else r.service.checkIn("visit",r.value(),org,revision)}
        }
        r.db {r.revoke()}
        assertThrows(BadRequestException::class.java){r.service.attendanceContext("visit")}
        r.unchanged()
    }

}
