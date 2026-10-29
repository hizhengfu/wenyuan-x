package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.flow.dao.FlowInstanceLogDao
import io.hizhengfu.cloud.flow.dao.FlowInstanceLogs
import io.hizhengfu.cloud.flow.domain.*
import io.hizhengfu.cloud.flow.engine.*
import io.hizhengfu.cloud.flow.service.FlowInstanceService
import io.hizhengfu.cloud.sys.file.dao.FileDao
import io.hizhengfu.cloud.sys.file.dao.Files
import io.hizhengfu.cloud.sys.system.dao.UserDao
import io.hizhengfu.cloud.sys.system.dao.Users
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.springframework.mock.web.MockHttpServletRequest
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.support.StaticApplicationContext
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import java.time.LocalDateTime

class WarehouseCompliancePersistenceTest {
    @Test fun `repeated diary creation reuses the signed record while different work remains separate`() = fixture {
        staff.save("store",roster())
        val value=log()
        val id=logs.save(value)
        val archivesBefore=CollectionPermanentArchiveDao.count()
        assertEquals(id,logs.save(value))
        assertEquals(1L,WarehouseLogDao.count())
        assertEquals(archivesBefore,CollectionPermanentArchiveDao.count())
        assertNotEquals(id,logs.save(value.copy(content="同日另一班次的实际巡库工作")))
        assertEquals(2L,WarehouseLogDao.count())
        assertThrows(BadRequestException::class.java) {logs.remove(id)}
        io.hizhengfu.cloud.sys.system.dao.RolePermissions.deleteAll()
        actorRole("operator","READ_ONLY_DIARY",setOf(CollectionConst.SELECT))
        assertThrows(BadRequestException::class.java) {logs.save(value)}
        assertEquals(2L,WarehouseLogDao.count())
    }

    @Test fun `duplicate inventory batch returns a business error before inserting a new entity`() = fixture {
        SchemaUtils.create(InventoryBatches)
        actorRole("operator","INVENTORY_STORAGE",setOf(CollectionConst.STORAGE))
        val batches=InventoryBatchService(scope,binder,archives)
        val id=batches.save(InventoryBatchVo(batchName="年度盘点"))
        val failure=assertThrows(BadRequestException::class.java) {batches.save(InventoryBatchVo(batchName=" 年度盘点 "))}
        assertEquals("盘点批次名称已存在",failure.message)
        assertEquals(1L,InventoryBatchDao.count())
        assertEquals(id,batches.save(InventoryBatchVo(id=id,batchName="年度盘点",scopeDesc="核对范围")))
    }
    private val at=LocalDateTime.of(2026,9,1,10,0)
    private val scope=CollectionScopeResolver("single","museum")
    private val users=mock(CollectionResponsibleUserService::class.java)
    private val names=mock(CollectionUserResolver::class.java).also { `when`(it.names(anyCollection())).thenAnswer { call -> (call.arguments[0] as Collection<*>).filterNotNull().associate { id -> id.toString() to "人员$id" } } }
    private val binder=mock(BusinessAttachmentBinder::class.java).also { `when`(it.resolveSingle(anyString())).thenAnswer { c -> c.getArgument<String>(0) } }
    private val archives=CollectionPermanentArchiveService(binder,scope,readAccess=unprotectedArchiveReadAccessProvider())
    private val staff=WarehouseStaffingService(scope,users,archives)
    private val statutory=WarehouseEntryStatutoryService(staff,users,archives)
    private val service=WarehouseEntryApplicationService(mock(FlowInstanceService::class.java),scope,names,binder,statutory,staff,archives)
    private val diary=WarehouseDiaryService(scope,staff,users,archives)
    private val logs=WarehouseLogService(scope,names,binder,diary)
    private fun identity(permissions:Set<String> = setOf("ADMIN"),roles:Set<String> = emptySet(),roleIds:Set<String> = emptySet())=SecurityUser(id="operator",name="经办",username="operator",password="",groupId="museum",groupIds=setOf("museum"),postIds=emptySet(),dataType=0,roles=roleIds,roleCodes=roles,authorities=permissions.map(::SimpleGrantedAuthority),enabled=true)
    @BeforeEach fun setup() {
        val context=StaticApplicationContext();context.beanFactory.registerSingleton("cacheManager",ConcurrentMapCacheManager());context.refresh();SpringContextHolder().setApplicationContext(context)
        val user=identity();SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(user,null,user.authorities)
    }
    @AfterEach fun clear()=SecurityContextHolder.clearContext()
    private fun fixture(currentReader:Boolean = true, block:()->Unit) {
        val db=Database.connect("jdbc:sqlite:file:warehouse-${System.nanoTime()}?mode=memory&cache=shared",driver="org.sqlite.JDBC")
        transaction(db) {
            SchemaUtils.create(CollectionOrgs,Warehouses,WarehouseEntryApplications,WarehouseLogs,CollectionPermanentArchives,CollectionPermanentArchiveFiles,Files,FlowInstanceLogs,Users)
            CollectionOrgDao.new("museum"){name="本馆";code="M"}
            CollectionOrgDao.new("other"){name="外馆";code="O"}
            WarehouseDao.new("store"){orgId="museum";name="一号库";sceneType=EnumLocationScene.STORAGE}
            WarehouseDao.new("other-store"){orgId="other";name="外馆库";sceneType=EnumLocationScene.STORAGE}
            WarehouseDao.new("hall"){orgId="museum";name="展厅";sceneType=EnumLocationScene.EXHIBITION;type=EnumWarehouseType.EXHIBITION_HALL}
            listOf("assignment","identity","shortage","entry-sign","exit-sign","report","diary-sign","reading","replacement").forEach { id -> FileDao.new(id){name="$id.pdf";path="/files/$id.pdf";hash=id;size=100;ext="pdf"} }
            if(currentReader) {
                originalReaderSchema();originalReader("operator",CollectionConst.SELECT)
                actorRole("operator","STAFFING_SETUP",setOf(CollectionConst.WAREHOUSE_MANAGE))
                actorRole("operator","ATTENDANCE",setOf(CollectionConst.WAREHOUSE_ENTRY_VERIFY,CollectionConst.WAREHOUSE_ENTRY_APPLY))
            }
            block()
        }
    }
    private fun roster(ids:Set<String> = setOf("k1","k2"))=WarehouseStaffingVo(ids,"keeper","leader",at.minusDays(1),"assignment")
    private fun proposal(kind:EnumWarehouseEntryKind=EnumWarehouseEntryKind.KEEPERS)=WarehouseEntryApplicationVo(warehouseIds="store",entrantIds=when(kind){EnumWarehouseEntryKind.KEEPERS->"k1,k2";EnumWarehouseEntryKind.STAFF_SHORTAGE->"k1";else->"visitor"},purpose="实际巡库和保管工作",plannedStart=at,plannedEnd=at.plusHours(1),dossier=WarehouseEntryDossierVo(kind,when(kind){EnumWarehouseEntryKind.KEEPERS->emptySet();EnumWarehouseEntryKind.VISITORS->setOf("k1","k2");else->setOf("assistant")},keeperUserId="keeper",leaderUserId=if(kind==EnumWarehouseEntryKind.STAFF_SHORTAGE)"leader" else null,identityFileIds="identity",shortageReason=if(kind==EnumWarehouseEntryKind.STAFF_SHORTAGE)"本库实际仅一名库管" else null,shortageFileIds=if(kind==EnumWarehouseEntryKind.STAFF_SHORTAGE)"shortage" else null))
    private fun application(kind:EnumWarehouseEntryKind=EnumWarehouseEntryKind.KEEPERS):WarehouseEntryApplicationDao { staff.save("store",roster(if(kind==EnumWarehouseEntryKind.STAFF_SHORTAGE)setOf("k1") else setOf("k1","k2")));return WarehouseEntryApplicationDao[service.save(proposal(kind))] }
    private fun approve(e:WarehouseEntryApplicationDao,director:Boolean=true) {
        e.flowInstanceId="flow-${e.id.value}";e.flowStatus=EnumFlowStatus.AUDIT
        statutory.actors(e).filter { director || it.key!="leader" }.entries.forEachIndexed { i,(node,person) -> FlowInstanceLogDao.new { instanceId=e.flowInstanceId!!;taskId="${e.id.value}-$node";nodeKey=node;action="APPROVE";operator=person;comment=if(node=="leader")"本人明确指定申请中的馆内陪同人员" else "本人核实库管、人员和时段";createTime=at.minusMinutes(30-i.toLong()) } }
        service.onFlowCompleted(e,FlowCompletedEvent(e.flowInstanceId!!,CollectionConst.FLOW_WAREHOUSE_ENTRY,"入库",e.id.value,CollectionConst.BIZ_WAREHOUSE_ENTRY,emptyMap(),"operator","经办"))
    }
    private fun attendance(e:WarehouseEntryApplicationDao,exit:Boolean=false)=WarehouseAttendanceVo(if(exit)at.plusHours(1) else at,splitCollectionIds(e.entrantIds).toSet(),e.dossier!!.escortUserIds,e.dossier!!.externalNames,true,true,if(exit)"exit-sign" else "entry-sign")
    private fun diaryValue()=WarehouseDiaryVo(at,setOf("k1"),true,"藏品现状与流转核查无差异","消防、安防及门窗正常",listOf(WarehouseEnvironmentReadingVo(at,22.5,50.0,"经校准的库房测量设备","reading")),false,signedDiaryFileIds="diary-sign")
    private fun log(value:WarehouseDiaryVo=diaryValue(),id:String?=null)=WarehouseLogVo(id=id,warehouseId="store",logDate=at.toLocalDate(),content="巡查库内藏品、环境及安全设施",diary=value,entries=diary.entries("store",at.toLocalDate()))

    @ParameterizedTest @ValueSource(strings=["entry", "entry-page", "entry-flow", "diary", "log-page", "entries", "gaps"])
    fun `warehouse current read rejects revoked actual permission despite old admin login`(kind:String)=fixture {
        val entry=application()
        val log=WarehouseLogDao.new { orgId="museum";warehouseId="store";logDate=at.toLocalDate();content="巡库记录" }
        fun read():Any? = when(kind) {
            "entry" -> service.get(entry.id.value)
            "entry-page" -> service.findPage(MockHttpServletRequest())
            "entry-flow" -> service.getFlowInfo(entry.id.value)
            "diary" -> diary.get(log.id.value)
            "log-page" -> logs.findPage(MockHttpServletRequest())
            "entries" -> diary.entries("store",at.toLocalDate())
            else -> diary.gaps(at.toLocalDate(),at.toLocalDate())
        }
        assertDoesNotThrow { read() }
        io.hizhengfu.cloud.sys.system.dao.RolePermissions.deleteAll()
        assertThrows(BadRequestException::class.java) { read() }
    }
    @Test fun `warehouse diary refuses a warehouse from another museum`()=fixture {
        val log=WarehouseLogDao.new { orgId="museum";warehouseId="other-store";logDate=at.toLocalDate();content="错位记录" }
        assertThrows(BadRequestException::class.java) { diary.get(log.id.value) }
    }

    @Test fun `warehouse current membership and newly granted cross permission replace the old login scope`()=fixture {
        val entry=application()
        val log=WarehouseLogDao.new { orgId="museum";warehouseId="store";logDate=at.toLocalDate();content="巡库记录" }
        val multi=CollectionScopeResolver("multi","")
        val multiEntry=WarehouseEntryApplicationService(mock(FlowInstanceService::class.java),multi,names,binder,statutory,staff,archives)
        val multiDiary=WarehouseDiaryService(multi,staff,users,archives)
        UserDao["operator"].groupId="other"
        assertThrows(BadRequestException::class.java){multiEntry.get(entry.id.value)}
        assertThrows(BadRequestException::class.java){multiDiary.get(log.id.value)}
        actorRole("operator","current_cross",setOf(CollectionConst.CROSS_ORG,CollectionConst.WAREHOUSE_ENTRY_VERIFY))
        assertEquals(entry.id.value,multiEntry.get(entry.id.value).id)
        assertEquals(log.id.value,multiDiary.get(log.id.value).id)
        assertEquals(setOf("store","other-store"),multiDiary.gaps(at.toLocalDate(),at.toLocalDate()).map {it["warehouseId"]}.toSet())
        val otherConfig=CollectionScopeResolver("single","other")
        assertThrows(BadRequestException::class.java){WarehouseDiaryService(otherConfig,staff,users,archives).get(log.id.value)}
        assertThrows(BadRequestException::class.java){WarehouseEntryApplicationService(mock(FlowInstanceService::class.java),otherConfig,names,binder,statutory,staff,archives).get(entry.id.value)}
        val timelineError=assertThrows(BadRequestException::class.java){WarehouseEntryTimelineService(otherConfig,CollectionActorDirectory(otherConfig),archives).get(entry.id.value)}
        assertTrue(timelineError.message!!.contains("当前配置收藏单位"))
        CollectionOrgDao["museum"].orgStatus=EnumOrgStatus.DISABLED
        assertEquals(log.id.value,multiDiary.get(log.id.value).id)
        UserDao["operator"].enabled=false
        assertThrows(BadRequestException::class.java){multiEntry.findPage(MockHttpServletRequest())}
        assertThrows(BadRequestException::class.java){multiDiary.gaps(at.toLocalDate(),at.toLocalDate())}
    }
    @Test fun `warehouse linked names do not reveal the warehouse of another museum`()=fixture {
        val entry=application();entry.warehouseIds="other-store"
        assertEquals(listOf("other-store"),service.get(entry.id.value).warehouseNames)
        WarehouseLogDao.new {orgId="museum";warehouseId="other-store";logDate=at.toLocalDate();content="错位记录"}
        assertNull((logs.findPage(MockHttpServletRequest()).list as List<WarehouseLogVo>).single().warehouseName)
    }
    @Test fun `warehouse details reject foreign archive headers without resealing`()=fixture {
        val entry=application()
        val log=WarehouseLogDao.new {orgId="museum";warehouseId="store";logDate=at.toLocalDate();content="巡库记录"}
        listOf(CollectionConst.BIZ_WAREHOUSE_ENTRY to entry.id.value,WarehouseDiaryService.BIZ to log.id.value).forEach {(biz,id)->
            CollectionPermanentArchiveDao.new {orgId="other";bizType=biz;bizId=id;phase="FOREIGN";snapshot="{}";digest=CollectionCareEvidence.digest("{}")}
        }
        val count=CollectionPermanentArchiveDao.count()
        assertThrows(BadRequestException::class.java){service.get(entry.id.value)}
        assertThrows(BadRequestException::class.java){diary.get(log.id.value)}
        assertEquals(count,CollectionPermanentArchiveDao.count())
    }

    @Test fun `actual role incompatibility rejects admin and conflicting role even when permissions are hidden`()=fixture(false) {
        for(p in listOf(listOf("ADMIN"),listOf("COLLECTION_ADMIN"))) {
            assertThrows(BadRequestException::class.java){CollectionDutySeparationPolicy.requireLedgerOperation(p)}
            assertThrows(BadRequestException::class.java){CollectionDutySeparationPolicy.requireStorageOperation(p)}
        }
        assertThrows(BadRequestException::class.java){CollectionDutySeparationPolicy.requireLedgerOperation(listOf(CollectionConst.LEDGER_CREATE),setOf(CollectionConst.ROLE_KEEPER))}
        actorSchema();UserDao.new("operator"){loginName="operator";name="经办";password="unused";enabled=true;groupId="museum"}
        io.hizhengfu.cloud.sys.system.dao.GroupDao.new("museum"){name="本馆机构";type='1';parentId="0";parentIds="0,"}
        actorRole("operator",CollectionConst.ROLE_KEEPER,setOf("ADMIN"))
        val ledgerRole=actorRole("operator",CollectionConst.ROLE_LEDGER_CUSTODIAN)
        val checker=CollectionResponsibleUserService(CollectionActorDirectory(scope),scope)
        assertThrows(BadRequestException::class.java){checker.requireRole("operator","museum",CollectionConst.ROLE_KEEPER)}
        io.hizhengfu.cloud.sys.system.dao.RoleUsers.deleteWhere { roleId eq ledgerRole }
        assertDoesNotThrow{checker.requireRole("operator","museum",CollectionConst.ROLE_KEEPER)}
    }
    @Test fun `staffing needs real storage location and preserves assignment versions`()=fixture {
        assertThrows(BadRequestException::class.java){staff.save("hall",roster())}
        staff.save("store",roster());staff.save("store",roster())
        assertThrows(BadRequestException::class.java){staff.save("store",roster(setOf("k1")))}
        staff.save("store",roster(setOf("k1")).copy(replacementReason="任职变更"))
        assertEquals(2,staff.get("store").archives.size)
        verify(users,atLeastOnce()).requireRole("k1","museum",CollectionConst.ROLE_KEEPER)
        assertThrows(BadRequestException::class.java){CollectionArchiveFileRetentionGuard().requireDeletable("assignment")}
    }
    @Test fun `two actual keepers require approval original signed attendance and exact time before access`()=fixture {
        val e=application()
        e.flowStatus=EnumFlowStatus.PASS
        assertThrows(BadRequestException::class.java){service.checkIn(e.id.value,attendance(e))}
        approve(e)
        assertThrows(BadRequestException::class.java){service.checkIn(e.id.value,attendance(e).copy(occurredAt=at.minusSeconds(1)))}
        assertThrows(BadRequestException::class.java){service.checkIn(e.id.value,attendance(e).copy(entrantUserIds=setOf("k1")))}
        assertThrows(BadRequestException::class.java){service.checkIn(e.id.value,attendance(e).copy(signedRecordFileIds=""))}
        service.checkIn(e.id.value,attendance(e));service.checkIn(e.id.value,attendance(e))
        assertEquals(at,e.actualEntryAt)
        assertThrows(BadRequestException::class.java){service.cancel(e.id.value)}
        assertThrows(BadRequestException::class.java){service.save(e.toModel().copy(purpose="换事由"))}
        service.checkOut(e.id.value,attendance(e,true));service.checkOut(e.id.value,attendance(e,true))
        assertEquals(at.plusHours(1),e.actualExitAt)
        assertEquals(3,service.get(e.id.value).archives.size)
    }
    @Test fun `visitors require two actual keepers with no overlap and permit external only applicants`()=fixture {
        staff.save("store",roster())
        val value=proposal(EnumWarehouseEntryKind.VISITORS)
        val e=WarehouseEntryApplicationDao[service.save(value.copy(entrantIds="",dossier=value.dossier!!.copy(externalNames=setOf("来访甲（研究所）"))))]
        approve(e);service.checkIn(e.id.value,attendance(e))
        val one=WarehouseEntryApplicationDao[service.save(value.copy(dossier=value.dossier!!.copy(escortUserIds=setOf("k1"))))]
        assertThrows(BadRequestException::class.java){statutory.requirePrepared(one)}
        val same=WarehouseEntryApplicationDao[service.save(value.copy(entrantIds="k1"))]
        assertThrows(BadRequestException::class.java){statutory.requirePrepared(same)}
    }
    @Test fun `shortage exception requires actual roster shortage and current director designation`()=fixture {
        val e=application(EnumWarehouseEntryKind.STAFF_SHORTAGE)
        assertThrows(BadRequestException::class.java){approve(e,false)}
        FlowInstanceLogDao.new { instanceId=e.flowInstanceId!!;taskId="leader-task";nodeKey="leader";action="APPROVE";operator="leader";comment="本人指定assistant实际陪同";createTime=at.minusMinutes(20) }
        statutory.confirm(e);e.flowStatus=EnumFlowStatus.PASS
        service.checkIn(e.id.value,attendance(e))
        val second=WarehouseEntryApplicationDao[service.save(proposal(EnumWarehouseEntryKind.STAFF_SHORTAGE))]
        staff.save("store",roster().copy(replacementReason="补足两名库管"))
        second.baseline=staff.baseline("museum",listOf("store"))
        assertThrows(BadRequestException::class.java){statutory.requirePrepared(second)}
    }
    @Test fun `changed staffing invalidates pending access but departure retains original attendance`()=fixture {
        val e=application();approve(e)
        staff.save("store",roster(setOf("k1","k3")).copy(replacementReason="实际更换库管"))
        assertThrows(BadRequestException::class.java){service.checkIn(e.id.value,attendance(e))}
        staff.save("store",roster().copy(replacementReason="核实原班次"))
        // 任职基线已经变化，重新建申请并审批；不得直接回写名单和已批准基线。
        val admitted=WarehouseEntryApplicationDao[service.save(proposal())]
        approve(admitted)
        service.checkIn(admitted.id.value,attendance(admitted))
        staff.save("store",roster(setOf("k1","k3")).copy(replacementReason="下一班次任职"))
        service.checkOut(admitted.id.value,attendance(admitted,true))
        assertEquals(setOf("k1","k2"),admitted.exitRecord!!.entrantUserIds)
    }
    @Test fun `interrupted accompaniment must be preserved on departure and reported in diary`()=fixture {
        val e=application(EnumWarehouseEntryKind.VISITORS);approve(e);service.checkIn(e.id.value,attendance(e))
        val exit=attendance(e,true).copy(continuousAccompaniment=false,remark="中途陪同人员离岗",exceptionMeasures="已恢复陪同并核查现场",exceptionReportFileIds="report")
        assertThrows(BadRequestException::class.java){service.checkOut(e.id.value,exit.copy(exceptionReportFileIds=null))}
        service.checkOut(e.id.value,exit)
        assertThrows(BadRequestException::class.java){logs.save(log())}
        val value=diaryValue().copy(hasAnomaly=true,anomalyDescription="陪同中断",handlingMeasures="现场核查并补充报告",keeperUserId="keeper",keeperReportedAt=at.plusHours(2),keeperReportFileIds="report",major=true,significanceReason="重要人员管理异常",leaderUserId="leader",leaderReportedAt=at.plusHours(3),leaderReportFileIds="report",resolved=true)
        val id=logs.save(log(value));assertTrue(diary.get(id).diary!!.hasAnomaly!!)
    }
    @Test fun `diary captures real entry times work environment and immutable amendments`()=fixture {
        val e=application();approve(e);service.checkIn(e.id.value,attendance(e));service.checkOut(e.id.value,attendance(e,true))
        assertThrows(BadRequestException::class.java){logs.save(log().copy(diary=null))}
        assertThrows(BadRequestException::class.java){logs.save(log(diaryValue().copy(readings=emptyList())))}
        assertThrows(BadRequestException::class.java){logs.save(log(diaryValue().copy(readings=listOf(WarehouseEnvironmentReadingVo(at,null,50.0,"设备","reading")))))}
        val id=logs.save(log());assertEquals(id,logs.save(log(id=id)))
        val saved=diary.get(id);assertEquals(at,saved.entries!!.items.single().enteredAt);assertEquals(setOf("k1","k2"),saved.entries.items.single().entry!!.entrantUserIds)
        assertThrows(BadRequestException::class.java){logs.remove(id)}
        assertThrows(BadRequestException::class.java){logs.save(log(diaryValue().copy(securitySituation="补充安防核查"),id))}
        logs.save(log(diaryValue().copy(securitySituation="补充安防核查",replacementReason="原记录补充"),id))
        assertEquals(2,diary.get(id).archives.size)
        assertThrows(BadRequestException::class.java){CollectionArchiveFileRetentionGuard().requireDeletable("diary-sign")}
    }
    @Test fun `new visits after diary snapshot are visible without silently rewriting signed history`()=fixture {
        val e=application();approve(e);service.checkIn(e.id.value,attendance(e));service.checkOut(e.id.value,attendance(e,true))
        val id=logs.save(log())
        val later=WarehouseEntryApplicationDao[service.save(proposal())];approve(later)
        service.checkIn(later.id.value,attendance(later));service.checkOut(later.id.value,attendance(later,true))
        assertTrue(diary.get(id).entriesChanged)
        assertThrows(BadRequestException::class.java){logs.save(log(diaryValue().copy(replacementReason="尚未看到新进出"),id).copy(entries=diary.get(id).entries))}
        assertEquals(1,diary.get(id).entries!!.items.size);assertEquals(2,diary.get(id).currentEntries!!.items.size)
        logs.save(log(diaryValue().copy(replacementReason="补记后来进出"),id))
        assertFalse(diary.get(id).entriesChanged)
    }
    @Test fun `amending a signed diary retains departed inspectors without granting current keeper authority`()=fixture {
        staff.save("store",roster())
        val id=logs.save(log())
        val original=diary.get(id)
        staff.save("store",roster(setOf("k2")).copy(assignedAt=at.plusDays(1),replacementReason="原巡库人员离任"))
        doThrow(BadRequestException("原巡库人员账号已停用")).`when`(users).requireMuseumMember("k1","museum")
        doThrow(BadRequestException("原巡库人员已撤角")).`when`(users).requireRole("k1","museum",CollectionConst.ROLE_KEEPER)
        val amended=original.diary!!.copy(securitySituation="补充原日安防核查说明",replacementReason="补充签署说明",signedDiaryFileIds="replacement")
        logs.save(log(amended,id).copy(content="补充原巡库工作说明"))
        assertEquals(amended,diary.get(id).diary)
        assertEquals("补充原巡库工作说明",diary.get(id).content)
        assertEquals(2,diary.get(id).archives.size)
        assertEquals(original.archives.first()["snapshot"],diary.get(id).archives.first()["snapshot"])
        assertThrows(BadRequestException::class.java){staff.requireKeeper("k1","museum",listOf("store"))}
    }
    private fun anomalyDiary()=diaryValue().copy(hasAnomaly=true,anomalyDescription="原日发现安防异常",handlingMeasures="已核查现场",
        keeperUserId="keeper",keeperReportedAt=at.plusHours(1),keeperReportFileIds="report",major=true,
        significanceReason="重大安防情况",leaderUserId="leader",leaderReportedAt=at.plusHours(2),leaderReportFileIds="report",resolved=true)
    private fun changeResponsiblePeople() {
        staff.save("store",roster(setOf("k2")).copy(keeperUserId="next-keeper",leaderUserId="next-leader",assignedAt=at.plusDays(1),replacementReason="人员交接"))
        doThrow(BadRequestException("原负责人已离任")).`when`(users).requireRole("keeper","museum",CollectionConst.ROLE_STORAGE_HEAD)
        doThrow(BadRequestException("原负责人已离任")).`when`(users).requireRole("leader","museum",CollectionConst.ROLE_DIRECTOR)
    }
    @Test fun `historical reports survive responsible people departure while keeping original signed versions`()=fixture {
        staff.save("store",roster())
        val id=logs.save(log(anomalyDiary()))
        val original=diary.get(id).archives.single()["snapshot"]
        changeResponsiblePeople()
        val amended=anomalyDiary().copy(handlingMeasures="补充原处理措施说明",signedDiaryFileIds="replacement",replacementReason="补充说明")
        logs.save(log(amended,id))
        assertEquals(amended,diary.get(id).diary)
        assertEquals(2,diary.get(id).archives.size)
        assertEquals(original,diary.get(id).archives.first()["snapshot"])
    }
    @ParameterizedTest @ValueSource(strings=["inspectedAt","newInspector","keeperTime","keeperFile","leaderTime","leaderFile","newAnomaly"])
    fun `revised personnel and reports cannot borrow departed personnel authority`(change:String)=fixture {
        staff.save("store",roster())
        val initial=if(change=="newAnomaly")diaryValue() else anomalyDiary()
        val id=logs.save(log(initial))
        val original=diary.get(id)
        changeResponsiblePeople()
        val amended=when(change) {
            "inspectedAt" -> initial.copy(inspectedAt=at.minusMinutes(1))
            "newInspector" -> initial.copy(inspectorUserIds=setOf("k1","departed-other"))
            "keeperTime" -> initial.copy(keeperReportedAt=at.plusMinutes(61))
            "keeperFile" -> initial.copy(keeperReportFileIds="replacement")
            "leaderTime" -> initial.copy(leaderReportedAt=at.plusMinutes(121))
            "leaderFile" -> initial.copy(leaderReportFileIds="replacement")
            else -> anomalyDiary()
        }.copy(replacementReason="订正历史资料",signedDiaryFileIds="replacement")
        assertThrows(BadRequestException::class.java){logs.save(log(amended,id).copy(content="不得写入的内容"))}
        assertEquals(original.diary,WarehouseLogDao[id].diary)
        assertEquals(original.content,WarehouseLogDao[id].content)
        assertEquals(1,archives.list(WarehouseDiaryService.BIZ,id).size)
    }
    @ParameterizedTest @ValueSource(strings=["digest","snapshot","files","org","missing","mutableRow"])
    fun `amendment and unchanged save reject broken latest archive evidence`(damage:String)=fixture {
        staff.save("store",roster())
        val id=logs.save(log())
        val signed=CollectionPermanentArchiveDao.find { CollectionPermanentArchives.bizId eq id }.single()
        when(damage) {
            "digest" -> signed.digest="invalid"
            "snapshot" -> { signed.snapshot="{}";signed.digest=CollectionCareEvidence.digest(signed.snapshot) }
            "files" -> CollectionPermanentArchiveFileDao.find { CollectionPermanentArchiveFiles.archiveId eq signed.id.value }.toList().forEach { it.delete() }
            "org" -> signed.orgId="other"
            "missing" -> signed.delete()
            "mutableRow" -> WarehouseLogDao[id].content="业务行已脱离签署原件"
        }
        val expectedCount=if(damage=="missing")0 else 1
        val row=WarehouseLogDao[id]
        val content=row.content
        assertThrows(BadRequestException::class.java){diary.get(id)}
        assertUnreadableListRecord(logs,id)
        assertThrows(BadRequestException::class.java){diary.gaps(at.toLocalDate(),at.toLocalDate())}
        assertThrows(BadRequestException::class.java){logs.save(log(id=id).copy(content=content))}
        assertThrows(BadRequestException::class.java){logs.save(log(diaryValue().copy(replacementReason="订正",signedDiaryFileIds="replacement"),id).copy(content="不得写入"))}
        assertEquals(content,row.content)
        assertEquals(diaryValue(),row.diary)
        assertEquals(expectedCount,CollectionPermanentArchiveDao.find { CollectionPermanentArchives.bizId eq id }.count().toInt())
    }
    @Test fun `signed diary rejects a cleared mutable row on both read and write`()=fixture {
        staff.save("store",roster())
        val id=logs.save(log())
        val archiveCount=CollectionPermanentArchiveDao.find { CollectionPermanentArchives.bizId eq id }.count()
        WarehouseLogDao[id].diary=null
        assertThrows(BadRequestException::class.java){diary.get(id)}
        assertUnreadableListRecord(logs,id)
        assertThrows(BadRequestException::class.java){diary.gaps(at.toLocalDate(),at.toLocalDate())}
        assertThrows(BadRequestException::class.java){logs.save(log(id=id))}
        assertEquals(archiveCount,CollectionPermanentArchiveDao.find { CollectionPermanentArchives.bizId eq id }.count())
    }
    @Test fun `retired warehouse permits verified historical amendment but rejects new diary`()=fixture {
        staff.save("store",roster())
        val id=logs.save(log())
        WarehouseDao["store"].status=io.hizhengfu.cloud.common.domain.EnumContentStatus.DELETE
        val amended=diaryValue().copy(replacementReason="补充退役前记录",signedDiaryFileIds="replacement",securitySituation="补充原日核查说明")
        logs.save(log(amended,id))
        assertEquals(2,diary.get(id).archives.size)
        assertThrows(BadRequestException::class.java){logs.save(log())}
    }
    @org.junit.jupiter.api.io.TempDir lateinit var originalsDir:java.nio.file.Path
    @Test fun `listing evidence issues commits through the actual diary transaction interceptor`() {
        val manager=org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager(
            org.sqlite.SQLiteDataSource().apply {url="jdbc:sqlite:${originalsDir.resolve("diary-list.db")}"})
        val transaction=org.springframework.transaction.support.TransactionTemplate(manager)
        val proxy=org.springframework.aop.framework.ProxyFactory(diary).apply {
            isProxyTargetClass=true
            addAdvice(org.springframework.transaction.interceptor.TransactionInterceptor(manager,
                org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()))
        }.proxy as WarehouseDiaryService
        val listing=WarehouseLogService(scope,names,binder,proxy)
        transaction.executeWithoutResult {
            SchemaUtils.create(CollectionOrgs,Warehouses,WarehouseLogs,CollectionPermanentArchives,CollectionPermanentArchiveFiles)
            originalReaderSchema();originalReader("operator",CollectionConst.SELECT)
            CollectionOrgDao.new("museum"){name="本馆";code="M"}
            WarehouseDao.new("store"){orgId="museum";name="一号库"}
            WarehouseLogDao.new("missing-original"){orgId="museum";warehouseId="store";logDate=at.toLocalDate();content="不能作为核实正文返回";this.diary=diaryValue()}
        }
        val result=transaction.execute {listing.findPage(MockHttpServletRequest())}!!
        val record=(result.list as List<WarehouseLogVo>).single()
        assertTrue(record.evidenceIssue!!.contains("缺少已签署日记原件"));assertNull(record.content)
    }
    private fun assertUnreadableListRecord(service:WarehouseLogService,id:String) {
        val record=(service.findPage(MockHttpServletRequest()).list as List<WarehouseLogVo>).single { it.id==id }
        assertFalse(record.evidenceIssue.isNullOrBlank())
        assertNull(record.content);assertNull(record.diary);assertNull(record.entries);assertNull(record.attachmentIds)
        assertTrue(record.archives.isEmpty())
        assertEquals("store",record.warehouseId);assertEquals(at.toLocalDate(),record.logDate)
    }
    @Test fun `replaced original keeps verified diary metadata readable but rejects original download`()=fixture {
        listOf("assignment","diary-sign","reading").forEach { id ->
            FileDao[id].path="$id.pdf"
            java.nio.file.Files.writeString(originalsDir.resolve("$id.pdf"),"original-$id")
        }
        val local=io.hizhengfu.cloud.common.file.LocalFileObjectStorage(originalsDir.toString())
        val realArchives=CollectionPermanentArchiveService(binder,scope,local,readAccess=unprotectedArchiveReadAccessProvider())
        val realStaff=WarehouseStaffingService(scope,users,realArchives)
        val realDiary=WarehouseDiaryService(scope,realStaff,users,realArchives)
        val realLogs=WarehouseLogService(scope,names,binder,realDiary)
        realStaff.save("store",roster())
        val id=realLogs.save(log())
        WarehouseLogDao.new("readable") {orgId="museum";warehouseId="store";logDate=at.toLocalDate();content="可查阅的历史巡库记录"}
        WarehouseLogDao.new("foreign") {orgId="other";warehouseId="other-store";logDate=at.toLocalDate();content="他馆记录"}
        val signed=CollectionPermanentArchiveDao.find {CollectionPermanentArchives.bizId eq id}.single()
        val frozen=CollectionPermanentArchiveFileDao.find {CollectionPermanentArchiveFiles.archiveId eq signed.id.value}.associate {it.fileId to it.metadata}
        val original=originalsDir.resolve("diary-sign.pdf")
        val replacement=originalsDir.resolve("copied-diary-sign.pdf")
        java.nio.file.Files.copy(original,replacement)
        java.nio.file.Files.move(replacement,original,java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        val page=realLogs.findPage(MockHttpServletRequest())
        val records=page.list as List<WarehouseLogVo>
        assertEquals(2L,page.total);assertEquals(setOf(id,"readable"),records.map {it.id}.toSet())
        assertEquals("可查阅的历史巡库记录",records.single {it.id=="readable"}.content)
        assertNull(records.single {it.id=="readable"}.evidenceIssue)
        assertUnreadableListRecord(realLogs,id)
        assertTrue(records.single {it.id==id}.evidenceIssue!!.contains("文件身份无法核实"))
        val detail=realDiary.get(id)
        assertEquals(diaryValue(),detail.diary)
        assertEquals(WarehouseLogDao[id].content,detail.content)
        assertFalse(detail.evidenceIssue.isNullOrBlank())
        assertTrue((detail.archives.single()["files"] as List<*>).isEmpty())
        assertThrows(BadRequestException::class.java){realDiary.requireArchivedOriginal(id,signed.id.value,"diary-sign")}
        assertEquals(frozen,CollectionPermanentArchiveFileDao.find {CollectionPermanentArchiveFiles.archiveId eq signed.id.value}.associate {it.fileId to it.metadata})
    }
    @ParameterizedTest @ValueSource(strings=["changed", "missing"])
    fun `unavailable original permits detail while blocking both no-op and amendment without replacing baseline`(damage:String)=fixture {
        listOf("assignment","diary-sign","reading","replacement").forEach { id ->
            FileDao[id].path="$id.pdf"
            java.nio.file.Files.writeString(originalsDir.resolve("$id.pdf"),"original-$id")
        }
        val local=io.hizhengfu.cloud.common.file.LocalFileObjectStorage(originalsDir.toString())
        val realArchives=CollectionPermanentArchiveService(binder,scope,local,readAccess=unprotectedArchiveReadAccessProvider())
        val realStaff=WarehouseStaffingService(scope,users,realArchives)
        val realDiary=WarehouseDiaryService(scope,realStaff,users,realArchives)
        val realLogs=WarehouseLogService(scope,names,binder,realDiary)
        realStaff.save("store",roster())
        val id=realLogs.save(log())
        val signed=CollectionPermanentArchiveDao.find { CollectionPermanentArchives.bizId eq id }.single()
        val metadata=CollectionPermanentArchiveFileDao.find { CollectionPermanentArchiveFiles.archiveId eq signed.id.value }.associate { it.fileId to it.metadata }
        if(damage=="missing") java.nio.file.Files.delete(originalsDir.resolve("diary-sign.pdf"))
        else java.nio.file.Files.writeString(originalsDir.resolve("diary-sign.pdf"),"modified-diary-bytes")
        assertUnreadableListRecord(realLogs,id)
        val detail=realDiary.get(id)
        assertEquals(diaryValue(),detail.diary)
        assertEquals(WarehouseLogDao[id].content,detail.content)
        assertFalse(detail.evidenceIssue.isNullOrBlank())
        assertTrue((detail.archives.single()["files"] as List<*>).isEmpty())
        assertThrows(BadRequestException::class.java){realDiary.requireArchivedOriginal(id,signed.id.value,"diary-sign")}
        assertThrows(BadRequestException::class.java){realLogs.save(log(id=id))}
        assertThrows(BadRequestException::class.java){realLogs.save(log(diaryValue().copy(replacementReason="订正",signedDiaryFileIds="replacement"),id))}
        assertEquals(diaryValue(),WarehouseLogDao[id].diary)
        assertEquals(1,CollectionPermanentArchiveDao.find { CollectionPermanentArchives.bizId eq id }.count().toInt())
        assertEquals(metadata,CollectionPermanentArchiveFileDao.find { CollectionPermanentArchiveFiles.archiveId eq signed.id.value }.associate { it.fileId to it.metadata })
    }
    @Test fun `missing daily diary and future or invalid environment values cannot count as completed records`()=fixture {
        staff.save("store",roster())
        val missing=diary.gaps(at.toLocalDate(),at.toLocalDate()).single{it["warehouseId"]=="store"}["missingDates"] as List<*>
        assertEquals(listOf(at.toLocalDate()),missing)
        assertThrows(BadRequestException::class.java){logs.save(log(diaryValue().copy(readings=listOf(WarehouseEnvironmentReadingVo(at,20.0,101.0,"设备","reading")))))}
        val id=logs.save(log())
        assertEquals(emptyList<Any>(),diary.gaps(at.toLocalDate(),at.toLocalDate()).single{it["warehouseId"]=="store"}["missingDates"])
        assertThrows(BadRequestException::class.java){logs.save(log(id=id).copy(warehouseId="other-store"))}
    }
    @Test fun `approved proposal cannot change purpose and old opinions cannot satisfy a new round`()=fixture {
        val e=application();approve(e)
        val original=e.purpose
        e.purpose="更换已批准的入库工作"
        assertThrows(BadRequestException::class.java){service.checkIn(e.id.value,attendance(e))}
        assertNull(e.actualEntryAt)
        e.purpose=original
        FlowInstanceLogDao.new { instanceId=e.flowInstanceId!!;nodeKey="keeper";action="REACTIVATE";operator="operator";createTime=at.minusMinutes(10) }
        assertThrows(BadRequestException::class.java){service.checkIn(e.id.value,attendance(e))}
        FlowInstanceLogDao.new { instanceId=e.flowInstanceId!!;nodeKey="keeper";taskId="new-keeper";action="APPROVE";operator="keeper";comment="本人重新核实";createTime=at.minusMinutes(5) }
        assertThrows(BadRequestException::class.java){service.checkIn(e.id.value,attendance(e))}
        statutory.confirm(e)
        service.checkIn(e.id.value,attendance(e))
        assertEquals(at,e.actualEntryAt)
    }
    @Test fun `actual membership does not inherit administrator or parent museum visibility`()=fixture(false) {
        SchemaUtils.create(io.hizhengfu.cloud.sys.system.dao.Groups)
        io.hizhengfu.cloud.sys.system.dao.GroupDao.new("museum") { name="本馆机构";type='1';parentId="0";parentIds="0," }
        CollectionOrgDao["other"].parentId="museum"
        val multi=CollectionScopeResolver("multi","")
        val person=identity(setOf("ADMIN"),roleIds=setOf("ADMIN"))
        assertTrue(multi.isMuseumMember(person,"museum"))
        assertFalse(multi.isMuseumMember(person,"other"))
    }
    @Test fun `diary never seals visits added after the user reviewed the snapshot`()=fixture {
        val e=application();approve(e)
        val reviewed=diary.entries("store",at.toLocalDate())
        service.checkIn(e.id.value,attendance(e));service.checkOut(e.id.value,attendance(e,true))
        val record=WarehouseLogDao.new { orgId="museum";warehouseId="store";logDate=at.toLocalDate();content="已核对时的巡库内容" }
        assertThrows(BadRequestException::class.java){diary.record(record,diaryValue(),reviewed)}
        assertNull(record.diary)
        assertTrue(archives.list(WarehouseDiaryService.BIZ,record.id.value).isEmpty())
    }
    @Test fun `staff reassignment never hides earlier days missing diaries and overnight visits remain included`()=fixture {
        val e=application()
        e.plannedEnd=at.plusDays(1);approve(e)
        service.checkIn(e.id.value,attendance(e))
        service.checkOut(e.id.value,attendance(e,true).copy(occurredAt=at.plusDays(1)))
        assertEquals(listOf(e.id.value),diary.entries("store",at.toLocalDate().plusDays(1)).items.map { it.applicationId })
        staff.save("store",roster().copy(assignedAt=at.plusDays(2),replacementReason="新班次"))
        assertEquals(listOf(at.toLocalDate()),diary.gaps(at.toLocalDate(),at.toLocalDate()).single { it["warehouseId"]=="store" }["missingDates"])
    }
    @Test fun `default flow routes shortage cases to actual director and normal visits finish after keeper`() {
        val raw=javaClass.getResourceAsStream("/flow/collection_warehouse_entry_2026.json")!!.bufferedReader().use { it.readText() }
        val model=JsonUtils.fromJson(JsonUtils.mapper.readTree(raw).get("model").toString(),FlowModel::class.java)!!
        val nodes=model.nodes.map { n -> n.properties.let { p -> FlowNodeSnapshot(p.nodeKey!!,p.name!!,p.nodeType!!,p.assigneeType,p.assignees,p.assigneeNames,p.multiInstance ?: false,p.multiInstanceType,p.rejectType,p.rejectNodeKey,p.formKey,p.timeoutHours,p.subDefinitionKey,p.fieldPerms,p.actions,p.description) } }.associateBy { it.nodeKey }
        val keys=model.nodes.associate { it.id to it.properties.nodeKey!! }
        val edges=model.edges.map { e -> FlowEdgeSnapshot(keys.getValue(e.sourceNodeId),keys.getValue(e.targetNodeId),e.properties.conditionType ?: "NONE",e.properties.condition,e.properties.priority ?: 0) }.groupBy { it.sourceNodeKey }.mapValues { (_,v)->v.sortedBy { it.priority } }
        val graph=FlowGraph("warehouse-2026",nodes,edges)
        assertEquals(listOf("keeper"),graph.resolveNextNodes("start").map { it.nodeKey })
        assertEquals(listOf("leader"),graph.resolveNextNodes("keeper",mapOf("requiresDirector" to true)).map { it.nodeKey })
        assertEquals(listOf("end"),graph.resolveNextNodes("keeper",mapOf("requiresDirector" to false)).map { it.nodeKey })
        assertEquals(listOf("end"),graph.resolveNextNodes("leader").map { it.nodeKey })
        assertEquals("SELF_SELECT",graph.getNode("leader").assigneeType)
    }
    @Test fun `warehouse evidence keeps explicit false zero and exact milliseconds`() {
        val value=diaryValue().copy(inspectedAt=at.withNano(123000000),readings=listOf(WarehouseEnvironmentReadingVo(at,0.0,0.0,"NO","reading")))
        val restored=JsonUtils.fromJson(JsonUtils.toJson(value)!!,WarehouseDiaryVo::class.java)!!
        assertEquals(value,restored);assertFalse(restored.hasAnomaly!!)
    }
}
