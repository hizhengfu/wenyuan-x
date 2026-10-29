package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.api.WarehouseEntryApplicationController
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.access.AccessDeniedException
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.flow.dao.*
import io.hizhengfu.cloud.flow.domain.StartFlowRequest
import io.hizhengfu.cloud.flow.service.FlowInstanceService
import io.hizhengfu.cloud.sys.file.dao.*
import io.hizhengfu.cloud.sys.system.dao.*
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
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
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.support.TransactionTemplate
import org.sqlite.SQLiteDataSource
import java.nio.file.Path
import java.time.LocalDateTime

class WarehouseEntryOperationRuntimeTest {
    @TempDir lateinit var dir:Path
    @Configuration(proxyBeanMethods=false) @EnableTransactionManagement(proxyTargetClass=true) @EnableMethodSecurity class Transactions
    companion object {
        val at:LocalDateTime=LocalDateTime.of(2026,9,1,10,0)
        fun login(id:String="applicant",authority:String="ADMIN") {
            val user=SecurityUser(id=id,name=id,username=id,password="",groupId="museum",groupIds=setOf("museum"),postIds=emptySet(),dataType=0,roles=emptySet(),authorities=listOf(SimpleGrantedAuthority(authority)),enabled=true)
            SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(user,null,user.authorities)
        }
        fun baseline()=WarehouseEntryBaselineVo(listOf(WarehouseStaffingItemVo("store","库房",WarehouseStaffingVo(setOf("k1","k2"),"head","leader",at.minusDays(1),"proof"))))
        fun proposal(id:String?="visit")=WarehouseEntryApplicationVo(id=id,warehouseIds="store",entrantIds="k1,k2",purpose="新方案",plannedStart=at,plannedEnd=at.plusHours(1),attachmentIds="proof",
            dossier=WarehouseEntryDossierVo(EnumWarehouseEntryKind.KEEPERS,keeperUserId="head",identityFileIds="proof"))
    }
    private class Runtime(val c:AnnotationConfigApplicationContext,val tx:TransactionTemplate,val op:String,val binder:BusinessAttachmentBinder) {
        val service=c.getBean(WarehouseEntryApplicationService::class.java)
        lateinit var confirmation:WarehouseApplicationContext
        fun <T> db(body:()->T):T=tx.execute {body()}
        fun write():Any?=when(op){"create"->service.save(proposal(null));"save"->service.save(proposal());"draft"->service.saveDraft("visit");"submit"->service.submit("visit");"cancel"->service.cancel("visit");else->service.void("visit")}
        fun controllerWrite():Any?=c.getBean(WarehouseEntryApplicationController::class.java).let {when(op){"create"->it.save(proposal(null),confirmation.orgId,confirmation.revision);"save"->it.save(proposal(),confirmation.orgId,confirmation.revision);"submit"->it.submit("visit",confirmation.orgId,confirmation.revision);"cancel"->it.cancel("visit",confirmation.orgId,confirmation.revision);else->it.void("visit",confirmation.orgId,confirmation.revision)}}
        fun revoke()=RolePermissions.deleteWhere {permissionId eq CollectionConst.WAREHOUSE_ENTRY_APPLY}
        fun unchanged()=db {
            assertEquals(1L,WarehouseEntryApplicationDao.count())
            val e=WarehouseEntryApplicationDao["visit"]
            assertEquals("museum",e.orgId);assertEquals("applicant",e.applicantId);assertEquals("原方案",e.purpose);assertEquals("k1,k2",e.entrantIds)
            assertEquals(if(op=="cancel")EnumFlowStatus.AUDIT else EnumFlowStatus.DRAFT,e.flowStatus)
            assertEquals(if(op=="cancel")"existing-flow" else null,e.flowInstanceId)
            assertNull(e.actualEntryAt);assertEquals(0L,CollectionPermanentArchiveDao.count());assertEquals(0L,CollectionPermanentArchiveFileDao.count())
            assertEquals(if(op=="cancel")1L else 0L,FlowInstanceDao.count())
        }
    }
    private fun fixture(op:String,mode:String="single",physicalOriginal:Boolean=false,body:(Runtime)->Unit) {
        login();val manager=SpringTransactionManager(SQLiteDataSource().apply {url="jdbc:sqlite:${dir.resolve("apply-${System.nanoTime()}.db")}"})
        val scope=CollectionScopeResolver(mode,"museum");val binder=mock(BusinessAttachmentBinder::class.java)
        `when`(binder.resolveSingle(anyString())).thenAnswer {it.getArgument<String>(0)}
        val staff=mock(WarehouseStaffingService::class.java)
        `when`(staff.baselineForWrite(anyString(),anyCollection())).thenReturn(baseline())
        val statutory=spy(WarehouseEntryStatutoryService(staff,mock(CollectionResponsibleUserService::class.java),CollectionPermanentArchiveService(binder,scope,readAccess=unprotectedArchiveReadAccessProvider())))
        val placeholder=mock(WarehouseEntryApplicationDao::class.java)
        doNothing().`when`(statutory).requirePrepared(any(WarehouseEntryApplicationDao::class.java) ?: placeholder)
        val flow=mock(FlowInstanceService::class.java)
        try {AnnotationConfigApplicationContext().use {c->
            c.beanFactory.registerSingleton("transactionManager",manager);c.beanFactory.registerSingleton("scope",scope);c.beanFactory.registerSingleton("binder",binder)
            c.beanFactory.registerSingleton("cacheManager",ConcurrentMapCacheManager());c.beanFactory.registerSingleton("flow",flow);c.beanFactory.registerSingleton("statutory",statutory);c.beanFactory.registerSingleton("staff",staff)
            if(physicalOriginal)c.beanFactory.registerSingleton("localStorage",io.hizhengfu.cloud.common.file.LocalFileObjectStorage(dir.toString()))
            c.beanFactory.registerSingleton("names",mock(CollectionUserResolver::class.java).also {`when`(it.names(anyCollection())).thenAnswer {call->(call.arguments[0] as Collection<*>).filterNotNull().associate {id->id.toString() to id.toString()}}})
            c.register(Transactions::class.java,CollectionPermanentArchiveService::class.java,io.hizhengfu.cloud.common.file.FileReadAccess::class.java,
                WarehouseEntryApplicationService::class.java,WarehouseEntryApplicationController::class.java);c.refresh();SpringContextHolder().setApplicationContext(c)
            val r=Runtime(c,TransactionTemplate(manager),op,binder)
            r.db {
                originalReaderSchema();originalReader("applicant",CollectionConst.SELECT);actorRole("applicant","APPLY",setOf(CollectionConst.WAREHOUSE_ENTRY_APPLY));originalReader("replacement",CollectionConst.WAREHOUSE_ENTRY_APPLY)
                SchemaUtils.create(CollectionOrgs,Warehouses,WarehouseEntryApplications,CollectionPermanentArchives,CollectionPermanentArchiveFiles,Files,FlowInstances,FlowInstanceLogs)
                listOf("museum","other").forEach {id->CollectionOrgDao.new(id){name=id;code=id}}
                WarehouseDao.new("store"){orgId="museum";name="库房";sceneType=EnumLocationScene.STORAGE}
                FileDao.new("proof"){name="合成材料";path=if(physicalOriginal)"proof.txt" else "synthetic-proof";hash="synthetic";size=1;ext="pdf"}
                if(physicalOriginal)java.nio.file.Files.writeString(dir.resolve("proof.txt"),"original-proof")
                WarehouseEntryApplicationDao.new("visit"){
                    orgId="museum";applicationNo="TEST-VISIT";applicantId="applicant";createBy="applicant";entrantIds="k1,k2";warehouseIds="store";purpose="原方案";plannedStart=at;plannedEnd=at.plusHours(1)
                    dossier=proposal().dossier;baseline=baseline();flowStatus=if(op=="cancel")EnumFlowStatus.AUDIT else EnumFlowStatus.DRAFT
                    if(op=="cancel")flowInstanceId="existing-flow"
                }
                if(op=="cancel")newFlow("existing-flow")
            }
            doAnswer {call->val e=newFlow("new-flow");call.getArgument<(FlowInstanceDao)->Unit>(3)(e);e}.`when`(flow)
                .startBusiness(any(StartFlowRequest::class.java) ?: StartFlowRequest("test","test"),anyString(),anyString(),any<(FlowInstanceDao)->Unit>() ?: {})
            doAnswer {FlowInstanceDao[it.getArgument<String>(0)].status="CANCELED";WarehouseEntryApplicationDao["visit"].flowStatus=EnumFlowStatus.CANCEL;null}.`when`(flow)
                .cancel(anyString(),anyString(),anyString(),anyBoolean(),anyBoolean())
            r.confirmation=r.service.applicationContext(if(op=="create")null else "visit")
            body(r)
        }}finally {SecurityContextHolder.clearContext()}
    }
    private fun newFlow(key:String)=FlowInstanceDao.new(key){definitionId="definition";definitionKey=CollectionConst.FLOW_WAREHOUSE_ENTRY;definitionName="合成流程";title="合成入库审批";businessKey="visit";businessType=CollectionConst.BIZ_WAREHOUSE_ENTRY;initiator="applicant";initiatorName="applicant"}

    @ParameterizedTest @ValueSource(strings=["missing","changed","replaced","fileRow","digest"])
    fun `unavailable originals preserve readable application context without granting write or download`(damage:String)=fixture("void",physicalOriginal=true){r->
        val archives=r.c.getBean(CollectionPermanentArchiveService::class.java)
        val archiveId=r.db {
            val id=archives.seal("museum",CollectionConst.BIZ_WAREHOUSE_ENTRY,"visit","SUBMITTED",mapOf("purpose" to "原方案"),"proof")
            archives.seal("museum",CollectionConst.BIZ_WAREHOUSE_ENTRY,"visit","ADDITIONAL",mapOf("note" to "有效档案"),null)
            id
        }
        assertEquals(2,r.service.applicationContext("visit").application!!.archives.size)
        when(damage) {
            "missing"->java.nio.file.Files.delete(dir.resolve("proof.txt"))
            "changed"->java.nio.file.Files.writeString(dir.resolve("proof.txt"),"changed-proof")
            "replaced"->{
                java.nio.file.Files.copy(dir.resolve("proof.txt"),dir.resolve("copy.txt"))
                java.nio.file.Files.move(dir.resolve("copy.txt"),dir.resolve("proof.txt"),java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }
            "fileRow"->r.db {FileDao["proof"].delete()}
            else->r.db {CollectionPermanentArchiveDao[archiveId].digest="invalid"}
        }
        val before=r.db {WarehouseEntryApplicationDao["visit"].toModel() to CollectionPermanentArchiveFileDao.all().associate {it.id.value to it.metadata}}
        val response=r.c.getBean(WarehouseEntryApplicationController::class.java).applicationContext("visit").data as WarehouseApplicationContext
        val detail=response.application!!
        assertEquals("原方案",detail.purpose);assertEquals("k1,k2",detail.entrantIds);assertEquals(at,detail.plannedStart)
        assertEquals(listOf("ADDITIONAL"),detail.archives.map {it["phase"]})
        assertEquals(1,detail.evidenceErrors.size)
        if(damage=="missing")assertTrue(detail.evidenceErrors.single().contains("原件文件缺失（文件编号：proof）"))
        assertFalse(response.canSave);assertFalse(response.canSubmit);assertFalse(response.canCancel);assertFalse(response.canVoid)
        r.confirmation=response
        assertThrows(BadRequestException::class.java){r.controllerWrite()}
        assertThrows(BadRequestException::class.java){r.service.requireArchivedOriginal("visit",archiveId,"proof")}
        assertThrows(BadRequestException::class.java){r.db {archives.archiveForAuthorizedOrg(CollectionConst.BIZ_WAREHOUSE_ENTRY,"visit","museum",archiveId)}}
        val validId=detail.archives.single()["id"] as String
        assertEquals("ADDITIONAL",r.db {archives.archiveForAuthorizedOrg(CollectionConst.BIZ_WAREHOUSE_ENTRY,"visit","museum",validId)["phase"]})
        assertThrows(BadRequestException::class.java){r.db {archives.archiveForAuthorizedOrg(CollectionConst.BIZ_WAREHOUSE_ENTRY,"other-visit","museum",validId)}}
        assertEquals(before,r.db {WarehouseEntryApplicationDao["visit"].toModel() to CollectionPermanentArchiveFileDao.all().associate {it.id.value to it.metadata}})
        r.db {actorRole("applicant","VERIFY",setOf(CollectionConst.WAREHOUSE_ENTRY_VERIFY));WarehouseEntryApplicationDao["visit"].flowStatus=EnumFlowStatus.PASS}
        assertFalse(r.service.attendanceContext("visit").canCheckIn)
    }

    @Test fun `application detail does not inspect restricted time review originals`()=fixture("void"){r->
        r.db {CollectionPermanentArchiveDao.new {orgId="museum";bizType=CollectionConst.BIZ_WAREHOUSE_ENTRY;bizId="visit";phase=WarehouseEntryTimelineService.PHASE+"restricted";snapshot="private";digest="invalid"}}
        val detail=r.service.applicationContext("visit").application!!
        assertTrue(detail.archives.isEmpty());assertTrue(detail.evidenceErrors.isEmpty())
        val id=r.db {CollectionPermanentArchiveDao.all().single().id.value}
        assertThrows(BadRequestException::class.java){r.db {r.c.getBean(CollectionPermanentArchiveService::class.java).archiveForAuthorizedOrg(CollectionConst.BIZ_WAREHOUSE_ENTRY,"visit","museum",id,WarehouseEntryTimelineService.PHASE)}}
    }

    @ParameterizedTest @ValueSource(strings=["create","save","draft","submit","cancel","void"])
    fun `old administrator login cannot replace current application duty`(op:String)=fixture(op){r->
        r.db {r.revoke()};assertThrows(BadRequestException::class.java){r.write()};r.unchanged()
    }
    @ParameterizedTest @ValueSource(strings=["create","save","draft","submit","cancel","void"])
    fun `reassigned current reader cannot change applications of former museum`(op:String)=fixture(op){r->
        r.db {UserDao["applicant"].groupId="other";actorRole("applicant","CROSS",setOf(CollectionConst.CROSS_ORG))}
        assertThrows(BadRequestException::class.java){r.write()};r.unchanged()
    }
    @ParameterizedTest @ValueSource(strings=["create","save","draft","submit","cancel","void"])
    fun `current application duty preserves valid operations`(op:String)=fixture(op){r->assertDoesNotThrow {r.write()}}
    @ParameterizedTest @ValueSource(strings=["create","save","submit"])
    fun `attachment callback revocation rolls back application and archives`(op:String)=fixture(op){r->
        doAnswer {r.revoke();null}.`when`(r.binder).bindSingle(anyString(),anyString(),anyString())
        assertThrows(BadRequestException::class.java){r.write()};r.unchanged()
    }
    @ParameterizedTest @CsvSource("save,purpose","draft,org","submit,purpose","cancel,org","void,entrant","save,revoked","draft,revoked","submit,revoked","cancel,revoked","void,revoked","submit,state","cancel,state","void,state","submit,instance","save,creator")
    fun `actual SQL mutations roll back instead of persisting changed application`(op:String,kind:String)=fixture(op){r->
        r.db {
            val mutation=when(kind){"creator"->"UPDATE col_warehouse_entry_apply SET create_by='other' WHERE id='visit'";"state"->"UPDATE col_warehouse_entry_apply SET flow_status='DRAFT' WHERE id='visit'";"instance"->"UPDATE col_warehouse_entry_apply SET flow_instance_id='foreign' WHERE id='visit'";"purpose"->"UPDATE col_warehouse_entry_apply SET purpose='篡改方案' WHERE id='visit'";"org"->"UPDATE col_warehouse_entry_apply SET org_id='other' WHERE id='visit'";"entrant"->"UPDATE col_warehouse_entry_apply SET entrant_ids='other' WHERE id='visit'";else->"DELETE FROM sys_role_permission WHERE permission_id='${CollectionConst.WAREHOUSE_ENTRY_APPLY}'"}
            val column=when(op){"save"->"purpose";"draft"->"update_time";else->"flow_status"}
            TransactionManager.current().exec("CREATE TRIGGER mutate_application AFTER UPDATE OF $column ON col_warehouse_entry_apply BEGIN $mutation; END")
        }
        assertThrows(BadRequestException::class.java){r.write()};r.unchanged()
    }
    @ParameterizedTest @ValueSource(booleans=[false,true])
    fun `cancelling another applicant requires current flow management rather than old administrator login`(manager:Boolean)=fixture("cancel"){r->
        login("replacement")
        if(manager)r.db {actorRole("replacement","FLOW",setOf("FLOW_MANAGE"))}
        if(manager)assertDoesNotThrow{r.write()} else {assertThrows(BadRequestException::class.java){r.write()};r.unchanged()}
    }
    @ParameterizedTest @ValueSource(strings=["PASS","AUDIT","CANCEL","VOID"])
    fun `generic draft helper cannot reset a non draft application without flow instance`(state:String)=fixture("draft"){r->
        r.db {WarehouseEntryApplicationDao["visit"].flowStatus=EnumFlowStatus.valueOf(state)}
        assertThrows(BadRequestException::class.java){r.write()}
        r.db {assertEquals(EnumFlowStatus.valueOf(state),WarehouseEntryApplicationDao["visit"].flowStatus)}
    }

    @ParameterizedTest @CsvSource("create,actor","save,actor","submit,actor","create,group","save,group","submit,group")
    fun `late attachment callback cannot replace operator or disable original group`(op:String,kind:String)=fixture(op){r->
        doAnswer {if(kind=="actor")login("replacement") else GroupDao["museum"].enabled=false;null}.`when`(r.binder).bindSingle(anyString(),anyString(),anyString())
        assertThrows(BadRequestException::class.java){r.write()};r.unchanged()
        r.db {assertTrue(GroupDao["museum"].enabled)}
    }

    @ParameterizedTest @ValueSource(strings=["create","save","submit","cancel","void"])
    fun `secured controller requires current application duty even for old administrator token`(op:String)=fixture(op){r->
        r.db {r.revoke()};assertThrows(BadRequestException::class.java){r.controllerWrite()};r.unchanged()
    }
    @ParameterizedTest @ValueSource(strings=["create","save","submit","cancel","void"])
    fun `secured controller keeps login gate in addition to current application duty`(op:String)=fixture(op){r->
        login(authority=CollectionConst.SELECT)
        assertThrows(AccessDeniedException::class.java){r.controllerWrite()};r.unchanged()
        login(authority=CollectionConst.WAREHOUSE_ENTRY_APPLY)
        assertDoesNotThrow {r.controllerWrite()}
    }

    @ParameterizedTest @CsvSource("save,purpose","submit,purpose","cancel,purpose","void,purpose","create,warehouse","save,warehouse","submit,warehouse","cancel,warehouse","void,warehouse","save,file","submit,file","cancel,file","void,file","create,org","save,org","submit,org","cancel,org","void,org","create,actor","save,actor","submit,actor","cancel,actor","void,actor")
    fun `opened confirmation rejects changed source without further writes`(op:String,kind:String)=fixture(op){r->
        r.db {
            when(kind) {
                "purpose"->WarehouseEntryApplicationDao["visit"].purpose="其他人已改写的方案"
                "warehouse"->WarehouseDao["store"].name="变更后的库房"
                "file"->FileDao["proof"].hash="changed-original"
                "org"->CollectionOrgDao["museum"].name="变更后的收藏单位"
            }
        }
        if(kind=="actor")login("replacement")
        fun state()=r.db {listOf(WarehouseEntryApplications,CollectionPermanentArchives,CollectionPermanentArchiveFiles,FlowInstances,CollectionOrgs).map {table->
            table.selectAll().map {row->table.columns.associate {it.name to row[it]?.toString()}}.sortedBy {it["id"]}
        }}
        val before=state()
        assertThrows(BadRequestException::class.java){r.controllerWrite()}
        assertEquals(before,state())
    }

    @ParameterizedTest @ValueSource(strings=["create","save","submit","cancel","void"])
    fun `wrong original museum is rejected before mutation`(op:String)=fixture(op){r->
        r.confirmation=r.confirmation.copy(orgId="other")
        assertThrows(BadRequestException::class.java){r.controllerWrite()};r.unchanged()
    }

    @ParameterizedTest @ValueSource(strings=["create","save","submit","cancel","void"])
    fun `successful confirmed receipt matches independent readback`(op:String)=fixture(op){r->
        val receipt=(r.controllerWrite() as ApiData).data as WarehouseApplicationContext
        val id=receipt.application!!.id!!
        assertEquals("museum",receipt.orgId);assertEquals("applicant",receipt.actorId)
        val reread=r.service.applicationContext(id)
        assertEquals(receipt,reread)
        assertNotEquals(r.confirmation.revision,receipt.revision)
        if(op=="create" || op=="save") {
            assertTrue(receipt.canSave);assertTrue(receipt.canSubmit)
            assertEquals(at,receipt.application.plannedStart)
        }
    }

    @Test fun `old new application confirmation cannot create a second record`()=fixture("create"){r->
        r.controllerWrite()
        assertThrows(BadRequestException::class.java){r.controllerWrite()}
        r.db {assertEquals(2L,WarehouseEntryApplicationDao.count())}
    }

    @Test fun `current reader can inspect but cannot acquire a writable context`()=fixture("save"){r->
        r.db {r.revoke()}
        val view=r.service.applicationContext("visit")
        assertFalse(view.canSave);assertFalse(view.canSubmit);assertFalse(view.canCancel);assertFalse(view.canVoid)
        assertEquals("visit",view.application!!.id)
        assertThrows(BadRequestException::class.java){r.service.applicationContext()}
        r.unchanged()
    }

    @ParameterizedTest @ValueSource(strings=["single","multi"])
    fun `creation uses current database actor rather than stale login default group`(mode:String)=fixture("create",mode){r->
        r.db {UserDao["applicant"].groupId="other"}
        if(mode=="multi")assertEquals("other",r.service.applicationContext().orgId)
        else assertThrows(BadRequestException::class.java){r.service.applicationContext()}
        assertThrows(BadRequestException::class.java){r.controllerWrite()};r.unchanged()
    }

    @ParameterizedTest @CsvSource("create,warehouse","save,warehouse","submit,warehouse","cancel,warehouse","void,warehouse","create,file","save,file","submit,file","cancel,file","void,file","create,org","save,org","submit,org","cancel,org","void,org")
    fun `confirmed write rolls back when SQL changes original referenced resources`(op:String,kind:String)=fixture(op){r->
        r.db {
            val mutation=when(kind){"warehouse"->"UPDATE col_warehouse SET name='写入期间改换库房' WHERE id='store'";"file"->"UPDATE sys_file SET hash='changed-during-write' WHERE id='proof'";else->"UPDATE col_org SET name='写入期间改换馆名' WHERE id='museum'"}
            val event=if(op=="create")"INSERT" else "UPDATE OF "+if(op=="save")"purpose" else "flow_status"
            TransactionManager.current().exec("CREATE TRIGGER mutate_resources AFTER $event ON col_warehouse_entry_apply BEGIN $mutation; END")
        }
        assertThrows(BadRequestException::class.java){r.controllerWrite()};r.unchanged()
        r.db {assertEquals("库房",WarehouseDao["store"].name);assertEquals("synthetic",FileDao["proof"].hash);assertEquals("museum",CollectionOrgDao["museum"].name)}
    }

    @Test fun `raw structured source change cannot hide behind equal display strings`()=fixture("void"){r->
        r.db {WarehouseEntryApplicationDao["visit"].dossier=proposal().dossier!!.copy(externalNames=setOf("甲, 乙"))}
        r.confirmation=r.service.applicationContext("visit")
        r.db {
            WarehouseEntryApplications.update({WarehouseEntryApplications.id eq "visit"}) {
                it[dossier]=proposal().dossier!!.copy(externalNames=setOf("甲","乙"))
            }
        }
        assertThrows(BadRequestException::class.java){r.controllerWrite()}
        r.db {assertEquals(EnumFlowStatus.DRAFT,WarehouseEntryApplicationDao["visit"].flowStatus)}
    }

    @ParameterizedTest @ValueSource(strings=["FLOW_MANAGE","ADMIN"])
    fun `current manager can cancel with an application-only login token`(grant:String)=fixture("cancel"){r->
        r.db {
            if(grant=="ADMIN") {
                RoleDao.new(EnumRole.ADMIN.value){code="ADMIN";name="系统管理员"}
                RoleUsers.insert {it[userId]="replacement";it[roleId]=EnumRole.ADMIN.value}
            } else actorRole("replacement",grant,setOf(grant))
        }
        login("replacement",CollectionConst.WAREHOUSE_ENTRY_APPLY)
        r.confirmation=r.service.applicationContext("visit")
        assertTrue(r.confirmation.canCancel)
        assertDoesNotThrow {r.controllerWrite()}
        r.db {assertEquals(EnumFlowStatus.CANCEL,WarehouseEntryApplicationDao["visit"].flowStatus)}
    }

    @ParameterizedTest @ValueSource(strings=["REJECT","CANCEL"])
    fun `historical non draft without flow cannot be submitted through confirmed application window`(state:String)=fixture("submit"){r->
        r.db {WarehouseEntryApplicationDao["visit"].flowStatus=EnumFlowStatus.valueOf(state)}
        r.confirmation=r.service.applicationContext("visit")
        assertFalse(r.confirmation.canSubmit)
        assertThrows(BadRequestException::class.java){r.controllerWrite()}
        r.db {assertEquals(EnumFlowStatus.valueOf(state),WarehouseEntryApplicationDao["visit"].flowStatus);assertEquals(0L,FlowInstanceDao.count());assertEquals(0L,CollectionPermanentArchiveDao.count())}
    }

    @Test fun `terminal void does not offer or accept another mutation`()=fixture("void"){r->
        r.db {WarehouseEntryApplicationDao["visit"].flowStatus=EnumFlowStatus.VOID}
        r.confirmation=r.service.applicationContext("visit")
        assertFalse(r.confirmation.canVoid)
        assertThrows(BadRequestException::class.java){r.controllerWrite()}
        assertEquals(r.confirmation,r.service.applicationContext("visit"))
    }

}
