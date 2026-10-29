package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.sys.file.dao.FileDao
import io.hizhengfu.cloud.sys.file.dao.Files
import org.jetbrains.exposed.v1.dao.entityCache
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
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
import java.time.temporal.ChronoUnit

class CollectionEmergencyPersistenceTest {
    private val scope=CollectionScopeResolver("single","museum")
    private val users=mock(CollectionResponsibleUserService::class.java)
    private val binder=mock(BusinessAttachmentBinder::class.java).also {`when`(it.resolveSingle(anyString())).thenAnswer {c->c.getArgument<String>(0)}}
    private val archives=CollectionPermanentArchiveService(binder,scope,readAccess=unprotectedArchiveReadAccessProvider())
    private val incidents=CollectionIncidentService(scope,users,archives,CollectionIncidentSourceService())
    private val service=CollectionEmergencyService(scope,users,archives,incidents)
    private val dutyService=CollectionEmergencyDutyService(scope,users,archives)
    private val files=CollectionEmergencyFileReadGuard(CollectionFileReadAuthorization(scope,CollectionActorDirectory(scope)))
    private val context=StaticApplicationContext()
    private lateinit var db:Database
    private lateinit var at:LocalDateTime
    private fun now()=LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS)
    @BeforeEach fun setup(){context.beanFactory.registerSingleton("cacheManager",ConcurrentMapCacheManager());context.refresh();SpringContextHolder().setApplicationContext(context);actor("operator")}
    @AfterEach fun cleanup(){SecurityContextHolder.clearContext();context.close()}
    private fun actor(id:String,permission:String="ADMIN") {
        val u=SecurityUser(id=id,name=id,username=id,password="",groupId="museum",groupIds=setOf("museum"),postIds=emptySet(),dataType=0,roles=emptySet(),authorities=listOf(SimpleGrantedAuthority(permission)),enabled=true)
        SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(u,null,u.authorities)
    }
    private fun fixture(block:()->Unit) {
        db=Database.connect("jdbc:sqlite:file:emergency-${System.nanoTime()}?mode=memory&cache=shared",driver="org.sqlite.JDBC",databaseConfig=org.jetbrains.exposed.v1.core.DatabaseConfig {useNestedTransactions=true})
        transaction(db) {
            SchemaUtils.create(CollectionEmergencyPlans,CollectionEmergencyDrills,CollectionEmergencyDrillRevisions,CollectionEmergencyDuties,CollectionEmergencyImprovements,CollectionEmergencyFileAccesses,Collections,CollectionOrgs,CollectionIncidents,CollectionIncidentRecords,CollectionIncidentItems,CollectionPermanentArchives,CollectionPermanentArchiveFiles,Files,Requisitions,StorageMovements)
            at=now().minusMinutes(10)
            CollectionOrgDao.new("museum"){name="本馆";code="M"}
            CollectionOrgDao.new("other"){name="外馆";code="O"}
            CollectionDao.new("piece"){orgId="museum";name="实物";totalRegNo="M1";lifeStatus=EnumCollectionStatus.IN_STORE;nature=EnumCollectionNature.OTHER}
            FileDao.new("proof"){name="真实签署原件.pdf";path="proof.pdf";hash="proof";size=100;ext="pdf"}
            incidentReaderFixture("operator","keeper","leader","conservation","department-head")
            // 当前应急权限来自真实关系表；登录切换不会自动补回被撤销的授权。
            listOf("operator","keeper","security","director","owner","administrator","keeper2","security2","director2","owner2","recorder2","recorder3","successor").forEach {id->
                if(io.hizhengfu.cloud.sys.system.dao.UserDao.findById(id)==null)io.hizhengfu.cloud.sys.system.dao.UserDao.new(id){name=id;loginName=id;password="unused";enabled=true;groupId="museum"}
                actorRole(id,"EMERGENCY_OPERATOR",setOf(CollectionConst.EMERGENCY))
            }
            block()
        }
    }
    private fun reject(block:()->Unit):BadRequestException {TransactionManager.current().entityCache.flush();return assertThrows(BadRequestException::class.java){transaction(db){block()}}}
    private val sig=CollectionCareSignatureVo("本人核对实际原件及责任安排","proof")
    private fun plan()=CollectionEmergencyPlanVo(title="本馆藏品安全应急预案",documentNo="YA-1",issuedAt=at.minusDays(10),effectiveAt=at.minusDays(9),keeperUserId="keeper",securityUserId="security",directorUserId="director",commandAndDuties="真实指挥体系和人员分工",warningAndActivation="预警及启动条件",emergencyContacts="真实联络渠道与备份方式",siteProtectionAndTransfer="保护现场及实物安全转移要求",resourcesAndSupport="实际物资、队伍及外部协助",firstGradeDamageReporting="一级文物损毁报国务院文物行政部门核查处理",otherRelicDamageReporting="其他文物损毁报省级核查并将结果报国家备案",theftRobberyLossReporting="被盗被抢丢失立即公安报案并报主管部门",recoveryAndReview="实际恢复、复盘和预案更新",scenarios=(CollectionEmergencyEvidence.reportScenarios+EnumEmergencyScenario.FIRE).map {EmergencyScenarioPlanVo(it,"该情景实际演练目标",7,at.minusDays(2))},originalFileIds="proof")
    private fun confirmPlan(id:String) {listOf("KEEPER" to "keeper","SECURITY" to "security","DIRECTOR" to "director").forEach {(role,user)->actor(user);service.confirmPlan("museum",id,role,sig)};actor("operator")}
    private fun prepared()=service.registerPlan("museum",plan()).also(::confirmPlan)
    private fun issue(revision:Boolean=false)=EmergencyDrillIssueVo("联络链路存在缺口","实际评估发现备用通信欠缺","owner",at.plusDays(1),"补齐并实际验证备用链路",revision)
    private fun drill(p:String,issues:List<EmergencyDrillIssueVo> = emptyList())=CollectionEmergencyDrillVo(planId=p,title="实际消防桌面推演",mode=EnumEmergencyDrillMode.TABLETOP,startedAt=at,endedAt=at.plusMinutes(1),scenarios=setOf(EnumEmergencyScenario.FIRE),conductorUserId="operator",evaluatorUserId="security",participantUserIds=setOf("operator","security","keeper"),actualLocation="本馆会议室",actualActions="按实际脚本推演指挥、保护与报告链路",communicationRehearsal="仅为模拟报警和报告，未向机关提交事故报案",actualOutcome="已实际完成的动作及评估",involvesOriginals=false,hasIssues=issues.isNotEmpty(),issues=issues,hasRealIncident=false,attendanceFileIds="proof",signedReportFileIds="proof")
    private fun confirmDrill(id:String) {actor("security");service.confirmDrill("museum",id,"EVALUATOR",sig);actor("director");service.confirmDrill("museum",id,"DIRECTOR",sig);actor("operator")}
    @Suppress("UNCHECKED_CAST") private fun due()=service.detail("museum")["due"] as List<Map<String,Any?>>
    private fun improvement()=EmergencyImprovementVo(1,completedAt=now(),ownerUserId="owner",verifierUserId="keeper",actualActions="已完成备用链路建设和验证",result="实测结果及复测依据",fileIds="proof")
    @Test fun `emergency original download requires the visible record phase and archived file`()=fixture {
        val originals=CollectionEmergencyOriginalService(service,archives)
        val p=prepared();val initial=drill(p,listOf(issue()));val d=service.recordDrill("museum",initial);confirmDrill(d)
        actor("owner");val i=service.improve("museum",d,improvement())
        actor("keeper");service.reviewImprovement("museum",d,i,EmergencyImprovementReviewVo(true,"实际独立复核通过","proof"));actor("operator")
        val r=service.reviseDrill("museum",d,revision(d,initial.copy(title="追加本次报告说明")))
        confirmReport(d,r)
        listOf(p to "ISSUED",p to "KEEPER",p to "SECURITY",p to "DIRECTOR",
            d to "DRILL",d to "EVALUATOR",d to "DIRECTOR",r to "DRILL_REVISION",
            r to "EVALUATOR",r to "DIRECTOR",i to "IMPROVEMENT",i to "REVIEW").forEach {(id,phase)->
            assertNotNull(originals.requireArchive("museum",id,phase,"proof"))
        }
        assertThrows(BadRequestException::class.java){originals.requireArchive("museum",d,"ISSUED","proof")}
        assertThrows(BadRequestException::class.java){originals.requireArchive("museum",r,"DRILL","proof")}
        assertThrows(BadRequestException::class.java){originals.requireArchive("museum",i,"REVIEW","missing")}
        assertThrows(BadRequestException::class.java){originals.requireArchive("museum","unrelated","ISSUED","proof")}
        assertThrows(BadRequestException::class.java){originals.requireArchive("other",p,"ISSUED","proof")}
    }
    @Test fun `emergency duty original download follows appointment handover and acceptance`()=fixture {
        val originals=CollectionEmergencyOriginalService(service,archives)
        val p=service.registerPlan("museum",plan())
        val v=appointment(EmergencyDutyTarget(EnumEmergencyDutyTarget.PLAN,p,EnumEmergencyDutyRole.KEEPER),"keeper2",EnumEmergencyDutyQualification.KEEPER,unavailable=false)
        actor("director");val id=dutyService.appoint("museum",v)
        actor("keeper");dutyService.sign("museum",id,"HANDOVER",sig)
        actor("keeper2");dutyService.sign("museum",id,"ACCEPT",sig);actor("operator")
        listOf("DUTY_APPOINTED","DUTY_HANDOVER","DUTY_ACCEPTED").forEach {phase->
            assertNotNull(originals.requireArchive("museum",id,phase,"proof"))
        }
        assertThrows(BadRequestException::class.java){originals.requireArchive("museum",id,"KEEPER","proof")}
    }
    @org.junit.jupiter.api.io.TempDir lateinit var originalsDir:java.nio.file.Path
    @Test fun `new business archive indexes its actual local object and retains the requested alias`()=fixture {
        java.nio.file.Files.writeString(originalsDir.resolve("proof.pdf"),"retained-original-bytes")
        java.nio.file.Files.createSymbolicLink(originalsDir.resolve("old-alias"),java.nio.file.Path.of("proof.pdf"))
        FileDao["proof"].path="old-alias"
        val local=io.hizhengfu.cloud.common.file.LocalFileObjectStorage(originalsDir.toString())
        val realArchives=CollectionPermanentArchiveService(binder,scope,local,readAccess=unprotectedArchiveReadAccessProvider())
        val realService=CollectionEmergencyService(scope,users,realArchives,incidents)
        realService.registerPlan("museum",plan())
        val keys=CollectionEmergencyFileAccessDao.all().map {it.pathKey}.toSet()
        assertTrue(keys.contains(io.hizhengfu.cloud.common.file.FileReadPath.key("old-alias","LOCAL")))
        assertTrue(keys.contains(io.hizhengfu.cloud.common.file.FileReadPath.key("proof.pdf","LOCAL")))
        java.nio.file.Files.createLink(originalsDir.resolve("later-link"),originalsDir.resolve("proof.pdf"))
        val identity=io.hizhengfu.cloud.common.file.FileReadPath.keys("later-link","LOCAL",local).single {it.startsWith("n")}
        assertTrue(identity in keys)
        assertTrue(CollectionPermanentArchiveFileDao.all().all {it.metadata.contains("old-alias")})
        java.nio.file.Files.delete(originalsDir.resolve("proof.pdf"))
        assertThrows(BadRequestException::class.java){realArchives.fileReadKeys(FileDao["proof"])}
    }
    @Test fun `actual issued plan and late confirmations do not fabricate past exercise`()=fixture {
        val p=service.registerPlan("museum",plan());assertEquals(p,service.registerPlan("museum",plan()))
        assertEquals(setOf("PENDING_CONFIRMATION"),due().map {it["status"]}.toSet())
        actor("director");reject {service.confirmPlan("museum",p,"DIRECTOR",sig)};actor("operator");confirmPlan(p)
        assertEquals(setOf("OVERDUE"),due().map {it["status"]}.toSet())
        val d=service.recordDrill("museum",drill(p));assertEquals(d,service.recordDrill("museum",drill(p)))
        assertEquals(setOf("OVERDUE"),due().map {it["status"]}.toSet());confirmDrill(d)
        val byScenario=due().associateBy {it["scenario"]}
        assertEquals(at.plusMinutes(1).plusDays(7),byScenario[EnumEmergencyScenario.FIRE]!!["nextDueAt"])
        assertEquals("SCHEDULED",byScenario[EnumEmergencyScenario.FIRE]!!["status"])
        assertEquals("OVERDUE",byScenario[EnumEmergencyScenario.THEFT]!!["status"])
        assertEquals(0,CollectionIncidentDao.all().toList().size);assertEquals(0,RequisitionDao.all().toList().size);assertEquals(0,StorageMovementDao.all().toList().size)
    }
    @Test fun `new revision cannot push unexercised old deadline into future`()=fixture {
        val p=prepared();val v=plan().copy(previousPlanId=p,issuedAt=at.minusDays(1),effectiveAt=at.minusDays(1),documentNo="YA-2",scenarios=plan().scenarios.map {it.copy(firstDueAt=at.plusDays(6))},replacementReason="根据实际条件修订",replacementFileIds="proof")
        val p2=service.registerPlan("museum",v);confirmPlan(p2)
        assertEquals(setOf(at.minusDays(2)),due().map {it["nextDueAt"]}.toSet())
        reject {service.recordDrill("museum",drill(p))}
        val id=service.recordDrill("museum",drill(p2));confirmDrill(id)
        assertEquals("SCHEDULED",due().first {it["scenario"]==EnumEmergencyScenario.FIRE}["status"])
    }
    @Test fun `future issued plan cannot backdate exercise and missing scenarios remain visible`()=fixture {
        reject {service.registerPlan("museum",plan().copy(issuedAt=now().plusDays(1)))}
        val p=service.registerPlan("museum",plan().copy(scenarios=listOf(plan().scenarios.last())))
        confirmPlan(p)
        assertEquals(4,due().count {it["status"]=="MISSING_SCENARIO"})
        reject {service.recordDrill("museum",drill(p).copy(startedAt=at.minusDays(11)))}
        reject {service.recordDrill("museum",drill(p).copy(endedAt=now().plusHours(1)))}
    }
    @Test fun `self confirmation and original signatures cannot be overwritten or removed`()=fixture {
        val p=prepared();val d=service.recordDrill("museum",drill(p))
        reject {service.confirmDrill("museum",d,"EVALUATOR",sig)}
        actor("director");reject {service.confirmDrill("museum",d,"DIRECTOR",sig)};actor("operator");confirmDrill(d)
        actor("security");assertEquals(d,service.confirmDrill("museum",d,"EVALUATOR",sig));reject {service.confirmDrill("museum",d,"EVALUATOR",sig.copy(opinion="替换原件"))}
        CollectionEmergencyDrillDao[d].evaluator=null;reject {service.detail("museum")}
    }
    @Test fun `independent improvement can request rework and preserves earlier result`()=fixture {
        val p=prepared();val d=service.recordDrill("museum",drill(p,listOf(issue())));confirmDrill(d)
        reject {service.improve("museum",d,improvement())}
        actor("owner");val v=improvement();val i=service.improve("museum",d,v);assertEquals(i,service.improve("museum",d,v))
        reject {service.reviewImprovement("museum",d,i,EmergencyImprovementReviewVo(true,"代签","proof"))}
        actor("keeper");service.reviewImprovement("museum",d,i,EmergencyImprovementReviewVo(false,"复测仍有失败，继续整改","proof"))
        assertEquals(1,due().first()["pendingIssueCount"])
        actor("owner");val i2=service.improve("museum",d,v.copy(previousId=i,completedAt=now(),result="重新验证已稳定",replacementReason="按复核意见继续实施"))
        actor("keeper");service.reviewImprovement("museum",d,i2,EmergencyImprovementReviewVo(true,"实际独立复核通过","proof"))
        assertEquals(0,due().first()["pendingIssueCount"])
        assertEquals(2,CollectionEmergencyImprovementDao.all().toList().size)
        actor("owner");reject {service.improve("museum",d,v.copy(previousId=i2,replacementReason="覆盖已完成"))}
    }
    @Test fun `required plan revision must be actual issued confirmed and effective before correction completion`()=fixture {
        val p=prepared();val d=service.recordDrill("museum",drill(p,listOf(issue(true))));confirmDrill(d)
        actor("owner");reject {service.improve("museum",d,improvement())}
        actor("operator");val p2=service.registerPlan("museum",plan().copy(previousPlanId=p,documentNo="YA-2",issuedAt=at.plusMinutes(2),effectiveAt=at.plusMinutes(2),scenarios=plan().scenarios.map {it.copy(firstDueAt=at.plusDays(3))},replacementReason="演练问题要求修订",replacementFileIds="proof"))
        actor("owner");reject {service.improve("museum",d,improvement().copy(revisedPlanId=p2))}
        actor("operator");confirmPlan(p2);actor("owner")
        val i=service.improve("museum",d,improvement().copy(revisedPlanId=p2));actor("keeper");service.reviewImprovement("museum",d,i,EmergencyImprovementReviewVo(true,"核对新版已生效及实际整改","proof"))
        assertEquals(0,due().first()["pendingIssueCount"])
    }
    @Test fun `exercise involving originals records missing basis as an issue without authorizing extraction`()=fixture {
        val p=prepared();val v=drill(p).copy(mode=EnumEmergencyDrillMode.FIELD,involvesOriginals=true)
        reject {service.recordDrill("museum",v)}
        val facts=v.copy(originalOperations="实际在库原件操作情况",originalOperationFileIds="proof",originalUseBasisVerified=false)
        reject {service.recordDrill("museum",facts)}
        val d=service.recordDrill("museum",facts.copy(hasIssues=true,issues=listOf(issue())))
        assertFalse(CollectionEmergencyDrillDao[d].content.originalUseBasisVerified!!)
        assertEquals(0,StorageMovementDao.all().toList().size);assertEquals(EnumCollectionStatus.IN_STORE,CollectionDao["piece"].lifeStatus)
        reject {service.recordDrill("museum",drill(p).copy(requisitionIds=setOf("not-actual")))}
    }
    @Test fun `real incident is linked separately and drill assessment never closes it`()=fixture {
        val p=prepared()
        val incident=incidents.create(IncidentFactsVo("演练期间实际损伤",at.plusSeconds(10),location="本馆",reporterUserId="operator",description="真实实物受损",immediateMeasures="停止并保护",items=listOf(IncidentItemVo("piece",true,EnumIncidentSecurity.NONE,false,"真实损伤"))))
        val v=drill(p,listOf(issue())).copy(hasRealIncident=true,actualIncidentIds=setOf(incident))
        val d=service.recordDrill("museum",v);confirmDrill(d)
        assertFalse(CollectionIncidentDao[incident].closed);assertTrue(CollectionIncidentItemDao.all().single().restricted)
        assertEquals(1,due().first()["openIncidentCount"])
        actor("owner");val i=service.improve("museum",d,improvement());actor("keeper");service.reviewImprovement("museum",d,i,EmergencyImprovementReviewVo(true,"整改事项实际完成","proof"))
        assertEquals(0,due().first()["pendingIssueCount"]);assertEquals(1,due().first()["openIncidentCount"])
        CollectionIncidentDao[incident].closed=true;reject {service.detail("museum")}
    }
    @Test fun `old or foreign incident cannot masquerade as an exercise incident`()=fixture {
        val p=prepared();val incident=incidents.create(IncidentFactsVo("旧事故",at.minusDays(1),location="本馆",reporterUserId="operator",description="以前真实问题",immediateMeasures="已保护",items=listOf(IncidentItemVo("piece",false,EnumIncidentSecurity.NONE,false,"既往问题"))))
        reject {service.recordDrill("museum",drill(p,listOf(issue())).copy(hasRealIncident=true,actualIncidentIds=setOf(incident)))}
        CollectionIncidentDao[incident].orgId="other"
        reject {service.recordDrill("museum",drill(p,listOf(issue())).copy(hasRealIncident=true,actualIncidentIds=setOf(incident)))}
    }
    @Test fun `actual issue and incident declarations cannot conceal contradictory facts`()=fixture {
        val p=prepared()
        reject {service.recordDrill("museum",drill(p).copy(hasIssues=false,issues=listOf(issue())))}
        reject {service.recordDrill("museum",drill(p).copy(hasIssues=true))}
        reject {service.recordDrill("museum",drill(p).copy(hasRealIncident=true))}
        reject {service.recordDrill("museum",drill(p).copy(conductorUserId="director"))}
        reject {service.recordDrill("museum",drill(p).copy(participantUserIds=setOf("operator")))}
        assertEquals(0,CollectionEmergencyDrillDao.all().toList().size)
    }
    @Test fun `archive failures roll back business rows and protected path bindings`()=fixture {
        reject {service.registerPlan("museum",plan().copy(originalFileIds="missing"))}
        assertEquals(0,CollectionEmergencyPlanDao.all().toList().size);assertEquals(0,CollectionEmergencyFileAccessDao.all().toList().size)
        val p=prepared();val count=CollectionPermanentArchiveDao.all().toList().size
        reject {service.recordDrill("museum",drill(p).copy(signedReportFileIds="missing"))}
        assertEquals(0,CollectionEmergencyDrillDao.all().toList().size);assertEquals(count,CollectionPermanentArchiveDao.all().toList().size)
    }
    @Test fun `actual emergency plan index follows configured museum without rewriting evidence`()=fixture {
        prepared()
        actorRole("operator","EMERGENCY_CROSS",setOf(CollectionConst.EMERGENCY,CollectionConst.CROSS_ORG))
        val before=CollectionPermanentArchiveDao.all().associate {it.id.value to (it.snapshot to it.digest)}
        val indexes=CollectionEmergencyFileAccessDao.all().map {it.id.value}.toSet()
        service.detail("museum");files.requireReadable("proof.pdf",null)
        org.springframework.test.util.ReflectionTestUtils.setField(scope,"defaultOrgId","other")
        try {
            reject {service.detail("museum")}
            FileDao["proof"].path="relocated-proof.pdf"
            for(path in listOf("proof.pdf","relocated-proof.pdf")) {
                assertTrue(files.isProtected(path,null));reject {files.requireReadable(path,null)}
            }
            assertEquals(before,CollectionPermanentArchiveDao.all().associate {it.id.value to (it.snapshot to it.digest)})
            assertEquals(indexes,CollectionEmergencyFileAccessDao.all().map {it.id.value}.toSet())
        } finally {FileDao["proof"].path="proof.pdf";org.springframework.test.util.ReflectionTestUtils.setField(scope,"defaultOrgId","museum")}
        service.detail("museum");files.requireReadable("proof.pdf",null)
    }

    @Test fun `emergency files require scoped original permission through frozen and current paths`()=fixture {
        prepared();originalReaderSchema();originalReader("reader","COLLECTION_VIEW");originalReader("responsible",CollectionConst.EMERGENCY)
        assertTrue(files.isProtected("proof.pdf",null))
        SecurityContextHolder.clearContext();assertThrows(BadRequestException::class.java){files.requireReadable("proof.pdf",null)}
        actor("reader","COLLECTION_VIEW");reject {files.requireReadable("proof.pdf",null)};reject {service.detail("museum")}
        actor("responsible",CollectionConst.EMERGENCY);assertDoesNotThrow {files.requireReadable("proof.pdf",null)}
        FileDao["proof"].path="relocated.pdf"
        assertTrue(files.isProtected("proof.pdf",null));assertTrue(files.isProtected("relocated.pdf",null))
        assertFalse(files.isProtected("unrelated.pdf",null))
        reject {service.detail("other")}
        assertThrows(BadRequestException::class.java){CollectionArchiveFileRetentionGuard().requireDeletable("proof")}
    }
    @Test fun `org workbench includes missing plans and respects page size zero without foreign disclosure`()=fixture {
        val request=org.springframework.mock.web.MockHttpServletRequest().also {it.setParameter("pageSize","0")}
        assertEquals(4,service.page(request).total)
        prepared();assertEquals(5,service.page(request).total)
        request.setParameter("keyword","外馆");assertEquals(0,service.page(request).total)
        request.setParameter("keyword","");request.setParameter("status","OVERDUE");assertEquals(5,service.page(request).total)
        actor("reader","COLLECTION_VIEW");reject {service.page(request)}
    }
    @Test fun `original plan mutation fails closed for drills and workbench`()=fixture {
        val p=prepared();CollectionEmergencyPlanDao[p].content=plan().copy(emergencyContacts="篡改指挥联系")
        reject {service.detail("museum")};reject {service.recordDrill("museum",drill(p))}
        reject {service.page(org.springframework.mock.web.MockHttpServletRequest())}
    }
    @Test fun `Spring file access dispatches emergency guard for public file path`()=fixture {
        prepared();originalReaderSchema();originalReader("reader","COLLECTION_VIEW");originalReader("responsible",CollectionConst.EMERGENCY)
        val ctx=org.springframework.context.annotation.AnnotationConfigApplicationContext()
        try {
            ctx.beanFactory.registerSingleton("scope",scope)
            ctx.register(CollectionEmergencyFileReadGuard::class.java,CollectionFileReadAuthorization::class.java,CollectionActorDirectory::class.java,io.hizhengfu.cloud.common.file.FileReadAccess::class.java)
            ctx.refresh()
            val access=ctx.getBean(io.hizhengfu.cloud.common.file.FileReadAccess::class.java)
            assertTrue(access.isProtected("proof.pdf"))
            actor("reader","COLLECTION_VIEW");assertThrows(BadRequestException::class.java){access.requireReadable("proof.pdf")}
            actor("responsible",CollectionConst.EMERGENCY);assertTrue(access.requireReadable("proof.pdf"))
        } finally {ctx.close()}
    }

    @Test fun `longer revised interval cannot forgive already missed cycle after earlier real drill`()=fixture {
        val p=prepared();val first=service.recordDrill("museum",drill(p));confirmDrill(first)
        val future=at.plusDays(8).plusHours(1)
        mockStatic(LocalDateTime::class.java,CALLS_REAL_METHODS).use {clock->
            clock.`when`<LocalDateTime> {LocalDateTime.now()}.thenReturn(future)
            val p2=service.registerPlan("museum",plan().copy(previousPlanId=p,documentNo="YA-2",issuedAt=at.plusDays(8),effectiveAt=at.plusDays(8),scenarios=plan().scenarios.map {it.copy(drillIntervalDays=30,firstDueAt=at.plusDays(20))},replacementReason="实际调整周期",replacementFileIds="proof"))
            confirmPlan(p2)
            assertEquals("OVERDUE",due().first {it["scenario"]==EnumEmergencyScenario.FIRE}["status"])
            assertEquals(at.plusMinutes(1).plusDays(7),due().first {it["scenario"]==EnumEmergencyScenario.FIRE}["nextDueAt"])
            val actual=service.recordDrill("museum",drill(p2).copy(startedAt=future.minusMinutes(20),endedAt=future.minusMinutes(10)))
            confirmDrill(actual)
            assertEquals(future.minusMinutes(10).plusDays(30),due().first {it["scenario"]==EnumEmergencyScenario.FIRE}["nextDueAt"])
        }
    }

    @Test fun `late issued original revealing obsolete drill basis keeps actual record but stops compliance count`()=fixture {
        val p=prepared();val d=service.recordDrill("museum",drill(p));confirmDrill(d)
        val p2=service.registerPlan("museum",plan().copy(previousPlanId=p,documentNo="YA-2",issuedAt=at.minusDays(1),effectiveAt=at.minusDays(1),scenarios=plan().scenarios.map {it.copy(firstDueAt=at.plusDays(2))},replacementReason="补齐先前已印发的新版本原件",replacementFileIds="proof"))
        confirmPlan(p2)
        assertFalse(CollectionEmergencyEvidence.counted(CollectionEmergencyDrillDao[d]))
        assertEquals(1,CollectionEmergencyDrillDao.all().toList().size)
        assertNotNull(CollectionEmergencyDrillDao[d].director)
        assertEquals("OVERDUE",due().first {it["scenario"]==EnumEmergencyScenario.FIRE}["status"])
    }

    private fun revision(id:String,v:CollectionEmergencyDrillVo)=EmergencyDrillRevisionVo(id,v,"据实际原件订正并补充发现","proof")
    private fun confirmReport(drillId:String,reportId:String) {
        actor("security");service.confirmDrill("museum",drillId,reportId,"EVALUATOR",sig)
        actor("director");service.confirmDrill("museum",drillId,reportId,"DIRECTOR",sig);actor("operator")
    }
    @Suppress("UNCHECKED_CAST") private fun detailDrill(id:String)=
        (service.detail("museum")["drills"] as List<Map<String,Any?>>).single {it["id"]==id}

    @Test fun `amend same drill keeps original signatures and issue closure but requires fresh report confirmation`()=fixture {
        val p=prepared();val initial=drill(p,listOf(issue()));val d=service.recordDrill("museum",initial);confirmDrill(d)
        actor("owner");val i=service.improve("museum",d,improvement())
        actor("keeper");service.reviewImprovement("museum",d,i,EmergencyImprovementReviewVo(true,"实际复核完成","proof"));actor("operator")
        val original=CollectionEmergencyDrillDao[d].director
        val archivesBefore=CollectionPermanentArchiveDao.all().associate {it.id.value to it.snapshot}
        val revised=initial.copy(title="订正名称并追加新发现",hasIssues=true,issues=initial.issues+issue().copy(title="追加消防物资问题"))
        val request=revision(d,revised);val r=service.reviseDrill("museum",d,request)
        assertEquals(r,service.reviseDrill("museum",d,request))
        TransactionManager.current().entityCache.flush();TransactionManager.current().entityCache.clear()
        assertEquals(1,CollectionEmergencyDrillDao.all().toList().size)
        assertEquals(1,CollectionEmergencyDrillRevisionDao.all().toList().size)
        assertEquals(original,CollectionEmergencyDrillDao[d].director)
        archivesBefore.forEach {(id,snapshot)->assertEquals(snapshot,CollectionPermanentArchiveDao[id].snapshot)}
        assertFalse(CollectionEmergencyEvidence.counted(CollectionEmergencyDrillDao[d]))
        assertEquals(1,due().first()["pendingIssueCount"])
        assertNull(detailDrill(d)["evaluator"]);assertEquals(r,detailDrill(d)["reportId"])
        actor("security");reject {service.confirmDrill("museum",d,"EVALUATOR",sig)}
        actor("director");reject {service.confirmDrill("museum",d,r,"DIRECTOR",sig)}
        confirmReport(d,r)
        assertTrue(CollectionEmergencyEvidence.counted(CollectionEmergencyDrillDao[d]))
        assertEquals(initial.endedAt.plusDays(7),due().first {it["scenario"]==EnumEmergencyScenario.FIRE}["nextDueAt"])
        assertEquals(2,(detailDrill(d)["reports"] as List<*>).size)
    }

    @Test fun `relevant corrected facts reopen accepted issue and freeze replacement improvement basis`()=fixture {
        val p=prepared();val initial=drill(p,listOf(issue()));val d=service.recordDrill("museum",initial)
        actor("owner");val v=improvement();val i=service.improve("museum",d,v)
        actor("keeper");service.reviewImprovement("museum",d,i,EmergencyImprovementReviewVo(true,"原依据实际通过","proof"));actor("operator")
        val r=service.reviseDrill("museum",d,revision(d,initial.copy(endedAt=at.plusMinutes(2))))
        assertEquals(1,due().first()["pendingIssueCount"])
        assertTrue(CollectionEmergencyImprovementDao[i].review!!.record.accepted)
        actor("keeper");reject {service.reviewImprovement("museum",d,r,i,EmergencyImprovementReviewVo(true,"原依据实际通过","proof"))}
        actor("owner");reject {service.improve("museum",d,v.copy(previousId=i,replacementReason="旧页面"))}
        val i2=service.improve("museum",d,r,v.copy(previousId=i,replacementReason="按订正后的实际发现时间重新核对",completedAt=now()))
        assertEquals(r,CollectionEmergencyImprovementDao[i2].reportRevisionId)
        actor("keeper");reject {service.reviewImprovement("museum",d,i2,EmergencyImprovementReviewVo(true,"旧页面","proof"))}
        service.reviewImprovement("museum",d,r,i2,EmergencyImprovementReviewVo(true,"按本次依据独立复核","proof"))
        assertEquals(0,due().first()["pendingIssueCount"])
        assertEquals(2,CollectionEmergencyImprovementDao.all().toList().size)
    }

    @Test fun `unreviewed improvement cannot be approved against changed report facts`()=fixture {
        val p=prepared();val initial=drill(p,listOf(issue()));val d=service.recordDrill("museum",initial)
        actor("owner");val i=service.improve("museum",d,improvement());actor("operator")
        val r=service.reviseDrill("museum",d,revision(d,initial.copy(endedAt=at.plusMinutes(2))))
        actor("keeper");reject {service.reviewImprovement("museum",d,r,i,EmergencyImprovementReviewVo(true,"试图沿用旧整改","proof"))}
        assertNull(CollectionEmergencyImprovementDao[i].review)
    }

    @Test fun `legacy improvement original shape remains verifiable and changes reopen it`()=fixture {
        val p=prepared();val initial=drill(p,listOf(issue()));val d=service.recordDrill("museum",initial)
        val v=improvement();val review=EmergencyImprovementReview("keeper",now(),EmergencyImprovementReviewVo(true,"历史实际复核","proof"))
        val i=CollectionEmergencyImprovementDao.new {orgId="museum";drillId=d;issueNumber=1;version=1;content=v;this.review=review}
        val historicalArchive=CollectionEmergencyArchive(archives,CollectionEmergencyAccess(scope))
        val archive=historicalArchive.seal("museum",i.id.value,"IMPROVEMENT",CollectionEmergencyEvidence.improvementValue(i),"proof")
        historicalArchive.seal("museum",i.id.value,"REVIEW",review,"proof")
        val snapshot=CollectionPermanentArchiveDao[archive].snapshot
        assertFalse(snapshot.contains("reportRevisionId"));assertEquals(0,due().first()["pendingIssueCount"])
        val r=service.reviseDrill("museum",d,revision(d,initial.copy(title="补正文字")))
        assertEquals(0,due().first()["pendingIssueCount"])
        service.reviseDrill("museum",d,revision(r,initial.copy(title="补正文字及时间",endedAt=at.plusMinutes(2))))
        assertEquals(1,due().first()["pendingIssueCount"]);assertEquals(snapshot,CollectionPermanentArchiveDao[archive].snapshot)
    }

    @Test fun `report amendment preserves issues and real incident lineage and forbids substitute submitters`()=fixture {
        val p=prepared();val incident=incidents.create(IncidentFactsVo("演练实发事故",at.plusSeconds(10),location="本馆",reporterUserId="operator",description="实际损伤",immediateMeasures="保护现场",items=listOf(IncidentItemVo("piece",true,EnumIncidentSecurity.NONE,false,"真实受损"))))
        val initial=drill(p,listOf(issue())).copy(hasRealIncident=true,actualIncidentIds=setOf(incident));val d=service.recordDrill("museum",initial)
        reject {service.reviseDrill("museum",d,revision(d,initial.copy(hasIssues=false,issues=emptyList())))}
        reject {service.reviseDrill("museum",d,revision(d,initial.copy(issues=listOf(issue().copy(ownerUserId="replacement")))))}
        reject {service.reviseDrill("museum",d,revision(d,initial.copy(hasRealIncident=false,actualIncidentIds=emptySet())))}
        reject {service.reviseDrill("museum",d,revision(d,initial.copy(conductorUserId="replacement")))}
        actor("administrator");reject {service.reviseDrill("museum",d,revision(d,initial.copy(title="代登记")))};actor("operator")
        val r=service.reviseDrill("museum",d,revision(d,initial.copy(title="补齐事实")))
        reject {service.reviseDrill("museum",d,revision(d,initial.copy(title="旧页面继续订正")))}
        assertEquals(r,detailDrill(d)["reportId"]);assertFalse(CollectionIncidentDao[incident].closed)
        assertTrue(CollectionIncidentItemDao.all().single().restricted)
    }

    @Test fun `report amendment cannot duplicate occurrence and archive failure rolls back revision`()=fixture {
        val p=prepared();val initial=drill(p);val d=service.recordDrill("museum",initial)
        reject {service.recordDrill("museum",initial.copy(title="重建同次演练"))}
        val count=CollectionPermanentArchiveDao.all().toList().size
        reject {service.reviseDrill("museum",d,revision(d,initial.copy(title="修正")).copy(fileIds="missing"))}
        assertEquals(0,CollectionEmergencyDrillRevisionDao.all().toList().size);assertEquals(count,CollectionPermanentArchiveDao.all().toList().size)
        assertEquals(d,detailDrill(d)["reportId"])
        assertNotEquals(d,service.recordDrill("museum",initial.copy(startedAt=at.plusMinutes(2),endedAt=at.plusMinutes(3))))
    }

    @Test fun `obsolete actual plan can be reported honestly but does not count even after confirmation`()=fixture {
        val p=prepared();val p2=service.registerPlan("museum",plan().copy(previousPlanId=p,documentNo="YA-2",issuedAt=at.minusDays(1),effectiveAt=at.minusDays(1),scenarios=plan().scenarios.map {it.copy(firstDueAt=at.plusDays(2))},replacementReason="实际已印发的新版",replacementFileIds="proof"));confirmPlan(p2)
        reject {service.recordDrill("museum",drill(p))}
        val initial=drill(p,listOf(issue()));val d=service.recordDrill("museum",initial);confirmDrill(d)
        assertFalse(CollectionEmergencyEvidence.counted(CollectionEmergencyDrillDao[d]))
        val r=service.reviseDrill("museum",d,revision(d,initial.copy(planId=p2)))
        assertFalse(CollectionEmergencyEvidence.counted(CollectionEmergencyDrillDao[d]));confirmReport(d,r)
        assertTrue(CollectionEmergencyEvidence.counted(CollectionEmergencyDrillDao[d]))
    }

    @Test fun `unchanged historical participant need not still belong to museum for factual amendment`()=fixture {
        val p=prepared();val initial=drill(p).copy(participantUserIds=setOf("operator","security","former"));val d=service.recordDrill("museum",initial)
        doThrow(BadRequestException("已离馆")).`when`(users).requireMuseumMember("former","museum")
        val r=service.reviseDrill("museum",d,revision(d,initial.copy(title="按原签到订正")))
        assertEquals(r,detailDrill(d)["reportId"])
        doThrow(BadRequestException("新增人员不在本馆")).`when`(users).requireMuseumMember("foreign","museum")
        reject {service.reviseDrill("museum",d,revision(r,initial.copy(title="追加人员",participantUserIds=initial.participantUserIds+"foreign")))}
    }

    @Test fun `latest amendment deletion cannot silently restore old approved report`()=fixture {
        val p=prepared();val initial=drill(p);val d=service.recordDrill("museum",initial);confirmDrill(d)
        val r=service.reviseDrill("museum",d,revision(d,initial.copy(title="已订正报告")))
        CollectionEmergencyDrillRevisionDao[r].delete();reject {service.detail("museum")}
    }

    @Test fun `amended report signatures remain immutable and archived sets survive database reload`()=fixture {
        val p=prepared();val initial=drill(p);val d=service.recordDrill("museum",initial)
        val r=service.reviseDrill("museum",d,revision(d,initial.copy(title="多情景实际补充",scenarios=setOf(EnumEmergencyScenario.THEFT,EnumEmergencyScenario.FIRE),participantUserIds=setOf("keeper","security","operator"))))
        confirmReport(d,r)
        TransactionManager.current().entityCache.flush();TransactionManager.current().entityCache.clear()
        assertTrue(detailDrill(d)["counted"] as Boolean)
        CollectionEmergencyDrillRevisionDao[r].evaluator=null;reject {service.detail("museum")}
    }

    @Test fun `amendment attachments gain original access control and report contents cannot be changed in place`()=fixture {
        val p=prepared();val initial=drill(p);val d=service.recordDrill("museum",initial)
        FileDao.new("amendment-proof"){name="订正依据原件.pdf";path="amendment.pdf";hash="amendment";size=200;ext="pdf"}
        val r=service.reviseDrill("museum",d,revision(d,initial.copy(title="订正事实")).copy(fileIds="amendment-proof"))
        assertTrue(files.isProtected("amendment.pdf",null))
        originalReaderSchema();originalReader("reader","COLLECTION_VIEW")
        actor("reader","COLLECTION_VIEW");reject {files.requireReadable("amendment.pdf",null)}
        actor("operator");assertThrows(BadRequestException::class.java){CollectionArchiveFileRetentionGuard().requireDeletable("amendment-proof")}
        val row=CollectionEmergencyDrillRevisionDao[r]
        row.record=row.record.copy(request=row.record.request.copy(report=row.record.request.report.copy(actualOutcome="直接篡改")))
        reject {service.detail("museum")}
    }

    private fun appointment(t:EmergencyDutyTarget,to:String,q:EnumEmergencyDutyQualification,reportId:String?=null,unavailable:Boolean=true)=EmergencyDutyAppointmentVo(
        target=t,expectedReportId=reportId,toUserId=to,qualification=q,appointedAt=now().minusMinutes(1),effectiveAt=now().minusMinutes(1),reason="实际岗位调整，指定后续核对和未完事项承接",fileIds="proof",predecessorUnavailable=unavailable,unavailableReason=if(unavailable)"原任已离任且不能本人交接" else null,unavailableFileIds=if(unavailable)"proof" else null,
    )
    private fun accept(v:EmergencyDutyAppointmentVo,issuer:String="director"):String {
        actor(issuer);val id=dutyService.appoint("museum",v);actor(v.toUserId);dutyService.sign("museum",id,"ACCEPT",sig);return id
    }
    private fun retired(id:String){doThrow(BadRequestException("已离馆")).`when`(users).requireMuseumMember(id,"museum")}

    @Test fun `responsibility appointment requires actual director and qualified museum successor without admin substitute`()=fixture {
        val p=prepared();val t=EmergencyDutyTarget(EnumEmergencyDutyTarget.PLAN,p,EnumEmergencyDutyRole.KEEPER)
        val v=appointment(t,"keeper2",EnumEmergencyDutyQualification.KEEPER)
        actor("administrator");doThrow(BadRequestException("没有主要负责人角色")).`when`(users).requireRole("administrator","museum",CollectionConst.ROLE_DIRECTOR)
        reject {dutyService.appoint("museum",v)}
        actor("director");reject {dutyService.appoint("museum",v.copy(qualification=EnumEmergencyDutyQualification.MEMBER))}
        retired("foreign");reject {dutyService.appoint("museum",v.copy(toUserId="foreign"))}
        reject {dutyService.appoint("museum",v.copy(unavailableFileIds=null))}
        reject {dutyService.appoint("museum",v.copy(appointedAt=now().plusDays(1)))}
        val id=dutyService.appoint("museum",v);assertEquals(id,dutyService.appoint("museum",v));assertEquals(1,CollectionEmergencyDutyDao.all().toList().size)
        actor("administrator");reject {dutyService.sign("museum",id,"ACCEPT",sig)}
    }

    @Test fun `predecessor handover then successor acceptance enables unsigned plan without rewriting issued people`()=fixture {
        val p=service.registerPlan("museum",plan());val t=EmergencyDutyTarget(EnumEmergencyDutyTarget.PLAN,p,EnumEmergencyDutyRole.KEEPER)
        val v=appointment(t,"keeper2",EnumEmergencyDutyQualification.KEEPER,unavailable=false)
        actor("director");val id=dutyService.appoint("museum",v)
        assertEquals(1,due().first()["pendingDutyCount"])
        actor("keeper2");reject {dutyService.sign("museum",id,"ACCEPT",sig)};reject {service.confirmPlan("museum",p,"KEEPER",sig)}
        actor("keeper");dutyService.sign("museum",id,"HANDOVER",sig);assertEquals(id,dutyService.sign("museum",id,"HANDOVER",sig));retired("keeper")
        actor("keeper2");dutyService.sign("museum",id,"ACCEPT",sig);service.confirmPlan("museum",p,"KEEPER",sig)
        assertEquals("keeper",CollectionEmergencyPlanDao[p].content.keeperUserId)
        assertEquals("keeper2",CollectionEmergencyPlanDao[p].keeper!!.actorUserId)
        assertEquals(id,CollectionEmergencyPlanDao[p].keeperDutyId)
        assertEquals(0,due().first()["pendingDutyCount"])
        actor("security");service.confirmPlan("museum",p,"SECURITY",sig);actor("director");service.confirmPlan("museum",p,"DIRECTOR",sig)
        assertTrue(CollectionEmergencyEvidence.confirmed(CollectionEmergencyPlanDao[p]));assertDoesNotThrow {service.detail("museum")}
    }

    @Test fun `successor continues report under own identity while actual conductor and old signatures remain`()=fixture {
        val p=prepared();val initial=drill(p);val d=service.recordDrill("museum",initial);confirmDrill(d)
        val old=CollectionEmergencyDrillDao[d].director
        val id=accept(appointment(EmergencyDutyTarget(EnumEmergencyDutyTarget.DRILL,d,EnumEmergencyDutyRole.RECORDER),"recorder2",EnumEmergencyDutyQualification.MEMBER,d))
        retired("operator")
        val r=service.reviseDrill("museum",d,revision(d,initial.copy(title="接任后核实原件追加报告")))
        assertEquals("operator",CollectionEmergencyDrillRevisionDao[r].record.request.report.conductorUserId)
        assertEquals("recorder2",CollectionEmergencyDrillRevisionDao[r].record.submittedBy)
        assertEquals(id,CollectionEmergencyDrillRevisionDao[r].recorderDutyId)
        assertEquals(old,CollectionEmergencyDrillDao[d].director)
        actor("operator");reject {service.reviseDrill("museum",d,revision(r,initial.copy(title="原任继续办理")))}
        actor("recorder2");assertEquals(r,detailDrill(d)["reportId"])
    }

    @Test fun `successor verifies pending drill original without claiming to be its actual participant`()=fixture {
        val p=prepared();val initial=drill(p);val d=service.recordDrill("museum",initial)
        val id=accept(appointment(EmergencyDutyTarget(EnumEmergencyDutyTarget.DRILL,d,EnumEmergencyDutyRole.EVALUATOR),"security2",EnumEmergencyDutyQualification.SECURITY,d))
        retired("security");retired("keeper")
        service.confirmDrill("museum",d,"EVALUATOR",sig)
        val row=CollectionEmergencyDrillDao[d]
        assertEquals("security",row.content.evaluatorUserId);assertFalse("security2" in row.content.participantUserIds)
        assertEquals("security2",row.evaluator!!.actorUserId);assertEquals(id,row.evaluatorDutyId)
        actor("director");service.confirmDrill("museum",d,"DIRECTOR",sig)
        TransactionManager.current().entityCache.flush();TransactionManager.current().entityCache.clear()
        assertTrue(detailDrill(d)["counted"] as Boolean)
    }

    @Test fun `new actual director can designate and accept old pending confirmation while retaining prior plan signature`()=fixture {
        val p=prepared();val initial=drill(p);val d=service.recordDrill("museum",initial)
        actor("security");service.confirmDrill("museum",d,"EVALUATOR",sig)
        retired("director")
        val id=accept(appointment(EmergencyDutyTarget(EnumEmergencyDutyTarget.DRILL,d,EnumEmergencyDutyRole.DIRECTOR),"director2",EnumEmergencyDutyQualification.DIRECTOR,d),"director2")
        service.confirmDrill("museum",d,"DIRECTOR",sig)
        assertEquals("director",CollectionEmergencyPlanDao[p].director!!.actorUserId)
        assertEquals("director2",CollectionEmergencyDrillDao[d].director!!.actorUserId)
        assertEquals(id,CollectionEmergencyDrillDao[d].directorDutyId)
        assertTrue(detailDrill(d)["counted"] as Boolean)
    }

    @Test fun `issue successor records own post acceptance work and cannot backdate predecessor work`()=fixture {
        val p=prepared();val initial=drill(p,listOf(issue()));val d=service.recordDrill("museum",initial)
        val id=accept(appointment(EmergencyDutyTarget(EnumEmergencyDutyTarget.ISSUE,d,EnumEmergencyDutyRole.OWNER,1),"owner2",EnumEmergencyDutyQualification.MEMBER,d))
        retired("owner")
        val v=improvement().copy(ownerUserId="owner2")
        reject {service.improve("museum",d,v.copy(completedAt=at.plusMinutes(2)))}
        val i=service.improve("museum",d,v.copy(completedAt=now()))
        assertEquals("owner",CollectionEmergencyDrillDao[d].content.issues.first().ownerUserId)
        assertEquals("owner2",CollectionEmergencyImprovementDao[i].content.ownerUserId);assertEquals(id,CollectionEmergencyImprovementDao[i].ownerDutyId)
        actor("keeper");service.reviewImprovement("museum",d,i,EmergencyImprovementReviewVo(true,"复核接任后实际结果","proof"))
        assertEquals(0,due().first()["pendingIssueCount"])
    }

    @Test fun `independent reviewer successor signs pending improvement while original nominee remains historical`()=fixture {
        val p=prepared();val d=service.recordDrill("museum",drill(p,listOf(issue())))
        actor("owner");val i=service.improve("museum",d,improvement())
        val id=accept(appointment(EmergencyDutyTarget(EnumEmergencyDutyTarget.IMPROVEMENT,i,EnumEmergencyDutyRole.REVIEWER),"keeper2",EnumEmergencyDutyQualification.KEEPER,d))
        retired("keeper")
        service.reviewImprovement("museum",d,i,EmergencyImprovementReviewVo(true,"接任后独立核对实际整改","proof"))
        assertEquals("keeper",CollectionEmergencyImprovementDao[i].content.verifierUserId)
        assertEquals("keeper2",CollectionEmergencyImprovementDao[i].review!!.actorUserId)
        assertEquals(id,CollectionEmergencyImprovementDao[i].reviewDutyId)
        TransactionManager.current().entityCache.flush();TransactionManager.current().entityCache.clear()
        assertEquals(0,due().first()["pendingIssueCount"])
    }

    @Test fun `responsibility changes retain independence across plan drill owner and reviewer`()=fixture {
        val p=prepared();val d=service.recordDrill("museum",drill(p,listOf(issue())))
        actor("owner");val i=service.improve("museum",d,improvement());actor("director")
        reject {dutyService.appoint("museum",appointment(EmergencyDutyTarget(EnumEmergencyDutyTarget.PLAN,p,EnumEmergencyDutyRole.KEEPER),"security",EnumEmergencyDutyQualification.KEEPER))}
        reject {dutyService.appoint("museum",appointment(EmergencyDutyTarget(EnumEmergencyDutyTarget.DRILL,d,EnumEmergencyDutyRole.EVALUATOR),"operator",EnumEmergencyDutyQualification.SECURITY,d))}
        reject {dutyService.appoint("museum",appointment(EmergencyDutyTarget(EnumEmergencyDutyTarget.IMPROVEMENT,i,EnumEmergencyDutyRole.REVIEWER),"owner",EnumEmergencyDutyQualification.KEEPER,d))}
        reject {dutyService.appoint("museum",appointment(EmergencyDutyTarget(EnumEmergencyDutyTarget.ISSUE,d,EnumEmergencyDutyRole.OWNER,1),"keeper",EnumEmergencyDutyQualification.MEMBER,d))}
        assertEquals(0,CollectionEmergencyDutyDao.all().toList().size)
    }

    @Test fun `future accepted appointment is inactive and superseded future assignment never activates`()=fixture {
        val p=prepared();val d=service.recordDrill("museum",drill(p));val t=EmergencyDutyTarget(EnumEmergencyDutyTarget.DRILL,d,EnumEmergencyDutyRole.RECORDER)
        val future=now().plusHours(2)
        val first=appointment(t,"recorder2",EnumEmergencyDutyQualification.MEMBER,d).copy(effectiveAt=future)
        val id=accept(first)
        reject {service.reviseDrill("museum",d,revision(d,drill(p).copy(title="尚未生效")))}
        actor("director");reject {dutyService.appoint("museum",appointment(t,"recorder3",EnumEmergencyDutyQualification.MEMBER,d).copy(previousId=id,appointedAt=first.appointedAt.minusDays(1)))}
        val id2=dutyService.appoint("museum",appointment(t,"recorder3",EnumEmergencyDutyQualification.MEMBER,d).copy(previousId=id))
        mockStatic(LocalDateTime::class.java,CALLS_REAL_METHODS).use {clock->
            clock.`when`<LocalDateTime> {LocalDateTime.now()}.thenReturn(future.plusHours(1))
            actor("recorder2");reject {service.reviseDrill("museum",d,revision(d,drill(p).copy(title="已被替代的旧指定")))}
            reject {dutyService.sign("museum",id,"ACCEPT",sig)}
            actor("recorder3");dutyService.sign("museum",id2,"ACCEPT",sig)
            val r=service.reviseDrill("museum",d,revision(d,drill(p).copy(title="最新指定本人承接")))
            assertEquals(id2,CollectionEmergencyDrillRevisionDao[r].recorderDutyId)
        }
    }

    @Test fun `pending acceptance rechecks current role and revised report before enabling continuation`()=fixture {
        val p=prepared();val initial=drill(p);val d=service.recordDrill("museum",initial)
        val t=EmergencyDutyTarget(EnumEmergencyDutyTarget.DRILL,d,EnumEmergencyDutyRole.EVALUATOR)
        actor("director");val v=appointment(t,"security2",EnumEmergencyDutyQualification.SECURITY,d);val id=dutyService.appoint("museum",v)
        actor("security2");doThrow(BadRequestException("岗位已变化")).`when`(users).requireRole("security2","museum",CollectionConst.ROLE_SECURITY)
        reject {dutyService.sign("museum",id,"ACCEPT",sig)}
        doNothing().`when`(users).requireRole("security2","museum",CollectionConst.ROLE_SECURITY)
        actor("operator");val r=service.reviseDrill("museum",d,revision(d,initial.copy(title="指定后报告已订正")))
        actor("security2");reject {dutyService.sign("museum",id,"ACCEPT",sig)}
        actor("director");val id2=dutyService.appoint("museum",v.copy(expectedReportId=r,previousId=id))
        actor("security2");dutyService.sign("museum",id2,"ACCEPT",sig);service.confirmDrill("museum",d,r,"EVALUATOR",sig)
        assertEquals(id2,CollectionEmergencyDrillRevisionDao[r].evaluatorDutyId)
    }

    @Test fun `accepted assignment and prior signature originals cannot be overwritten cleared or deleted`()=fixture {
        val p=prepared();val d=service.recordDrill("museum",drill(p));confirmDrill(d)
        val original=CollectionEmergencyDrillDao[d].evaluator
        val id=accept(appointment(EmergencyDutyTarget(EnumEmergencyDutyTarget.DRILL,d,EnumEmergencyDutyRole.EVALUATOR),"security2",EnumEmergencyDutyQualification.SECURITY,d))
        reject {service.confirmDrill("museum",d,"EVALUATOR",sig)}
        assertEquals(original,CollectionEmergencyDrillDao[d].evaluator)
        reject {dutyService.sign("museum",id,"ACCEPT",sig.copy(opinion="覆盖接任签署"))}
        CollectionEmergencyDutyDao[id].acceptance=null;reject {service.detail("museum")}
    }

    @Test fun `responsibility appointment rollback and protected file bindings preserve permanent originals`()=fixture {
        val p=prepared();val t=EmergencyDutyTarget(EnumEmergencyDutyTarget.PLAN,p,EnumEmergencyDutyRole.KEEPER);actor("director")
        val v=appointment(t,"keeper2",EnumEmergencyDutyQualification.KEEPER)
        reject {dutyService.appoint("museum",v.copy(fileIds="missing"))};assertEquals(0,CollectionEmergencyDutyDao.all().toList().size)
        FileDao.new("duty-proof"){name="实际责任指定.pdf";path="duty-proof.pdf";hash="duty-proof";size=100;ext="pdf"}
        val id=dutyService.appoint("museum",v.copy(fileIds="duty-proof"));assertTrue(files.isProtected("duty-proof.pdf",null))
        originalReaderSchema();originalReader("reader","COLLECTION_VIEW")
        actor("reader","COLLECTION_VIEW");reject {files.requireReadable("duty-proof.pdf",null)}
        actor("director");CollectionEmergencyDutyDao[id].delete();reject {service.detail("museum")}
    }

    @Test fun `future responsibility appointments cannot converge independent plan duties on same person`()=fixture {
        val p=service.registerPlan("museum",plan());val future=now().plusHours(2)
        accept(appointment(EmergencyDutyTarget(EnumEmergencyDutyTarget.PLAN,p,EnumEmergencyDutyRole.KEEPER),"successor",EnumEmergencyDutyQualification.KEEPER).copy(effectiveAt=future))
        actor("director");reject {dutyService.appoint("museum",appointment(EmergencyDutyTarget(EnumEmergencyDutyTarget.PLAN,p,EnumEmergencyDutyRole.SECURITY),"successor",EnumEmergencyDutyQualification.SECURITY).copy(effectiveAt=future))}
    }

    @Test fun `new actual drill recognizes plan officers who accepted responsibility before exercise`()=fixture {
        val p=prepared()
        accept(appointment(EmergencyDutyTarget(EnumEmergencyDutyTarget.PLAN,p,EnumEmergencyDutyRole.KEEPER),"keeper2",EnumEmergencyDutyQualification.KEEPER))
        accept(appointment(EmergencyDutyTarget(EnumEmergencyDutyTarget.PLAN,p,EnumEmergencyDutyRole.DIRECTOR),"director2",EnumEmergencyDutyQualification.DIRECTOR),"director2")
        val future=now().plusHours(1)
        mockStatic(LocalDateTime::class.java,CALLS_REAL_METHODS).use {clock->
            clock.`when`<LocalDateTime> {LocalDateTime.now()}.thenReturn(future)
            actor("operator")
            val v=drill(p).copy(startedAt=future.minusMinutes(10),endedAt=future.minusMinutes(5),evaluatorUserId="keeper2",participantUserIds=setOf("operator","keeper2"))
            val d=service.recordDrill("museum",v)
            actor("keeper2");doThrow(BadRequestException("仅担任保管负责人")).`when`(users).requireRole("keeper2","museum",CollectionConst.ROLE_SECURITY)
            service.confirmDrill("museum",d,"EVALUATOR",sig)
            actor("director2");service.confirmDrill("museum",d,"DIRECTOR",sig)
            assertEquals("director2",CollectionEmergencyDrillDao[d].director!!.actorUserId)
            assertTrue(detailDrill(d)["counted"] as Boolean)
        }
    }

    @Test fun `successive recorder appointments preserve prior proofs and cannot move report author into self review`()=fixture {
        val p=prepared();val initial=drill(p);val d=service.recordDrill("museum",initial)
        val target=EmergencyDutyTarget(EnumEmergencyDutyTarget.DRILL,d,EnumEmergencyDutyRole.RECORDER)
        val id1=accept(appointment(target,"recorder2",EnumEmergencyDutyQualification.MEMBER,d))
        val r1=service.reviseDrill("museum",d,revision(d,initial.copy(title="首位接任人续写报告")))
        val id2=accept(appointment(target,"recorder3",EnumEmergencyDutyQualification.MEMBER,r1).copy(previousId=id1))
        assertEquals("recorder2",CollectionEmergencyDutyDao[id2].appointment.fromUserId)
        actor("director");reject {dutyService.appoint("museum",appointment(EmergencyDutyTarget(EnumEmergencyDutyTarget.DRILL,d,EnumEmergencyDutyRole.EVALUATOR),"recorder2",EnumEmergencyDutyQualification.SECURITY,r1))}
        actor("recorder3");val r2=service.reviseDrill("museum",d,revision(r1,initial.copy(title="第二位接任人续写报告")))
        assertEquals(id1,CollectionEmergencyDrillRevisionDao[r1].recorderDutyId)
        assertEquals(id2,CollectionEmergencyDrillRevisionDao[r2].recorderDutyId)
        assertEquals(r2,detailDrill(d)["reportId"])
    }

    @Test fun `existing signature holder cannot switch duty and countersign same original`()=fixture {
        val p=service.registerPlan("museum",plan());actor("security");service.confirmPlan("museum",p,"SECURITY",sig)
        accept(appointment(EmergencyDutyTarget(EnumEmergencyDutyTarget.PLAN,p,EnumEmergencyDutyRole.SECURITY),"security2",EnumEmergencyDutyQualification.SECURITY))
        actor("director");reject {dutyService.appoint("museum",appointment(EmergencyDutyTarget(EnumEmergencyDutyTarget.PLAN,p,EnumEmergencyDutyRole.KEEPER),"security",EnumEmergencyDutyQualification.KEEPER))}
    }

    @Test fun `emergency task source uses current report stage and real duty effective actor`()=fixture {
        val p=prepared();val d=service.recordDrill("museum",drill(p,listOf(issue())))
        val source=CollectionEmergencyTaskSource()
        var tasks=source.tasks("museum",now())
        assertTrue(tasks.any {it.kind=="REPORT_EVALUATOR"});assertFalse(tasks.any {it.kind=="REPORT_DIRECTOR"})
        assertEquals(setOf(CollectionConst.ROLE_SECURITY),tasks.single {it.kind=="REPORT_EVALUATOR"}.assignees.single().roles)
        val t=EmergencyDutyTarget(EnumEmergencyDutyTarget.DRILL,d,EnumEmergencyDutyRole.EVALUATOR)
        val duty=accept(appointment(t,"security2",EnumEmergencyDutyQualification.SECURITY,d))
        tasks=source.tasks("museum",now())
        assertEquals(listOf("security2"),tasks.single {it.kind=="REPORT_EVALUATOR"}.assignees.map {it.userId})
        actor("security2");service.confirmDrill("museum",d,"EVALUATOR",sig)
        assertEquals(duty,CollectionEmergencyDrillDao[d].evaluatorDutyId)
        tasks=source.tasks("museum",now());assertFalse(tasks.any {it.kind=="REPORT_EVALUATOR"});assertTrue(tasks.any {it.kind=="REPORT_DIRECTOR"})
        actor("director");service.confirmDrill("museum",d,"DIRECTOR",sig);actor("operator")
        val revised=service.reviseDrill("museum",d,EmergencyDrillRevisionVo(expectedReportId=d,reason="核实实际结果",fileIds="proof",report=drill(p,listOf(issue())).copy(actualOutcome="重新核实结果")))
        assertEquals(revised,source.tasks("museum",now()).single {it.kind=="REPORT_EVALUATOR"}.recordId)
    }
    @Test fun `emergency handover tasks move through actual signatures and issue review remains independent`()=fixture {
        val p=prepared();val d=service.recordDrill("museum",drill(p,listOf(issue())))
        val source=CollectionEmergencyTaskSource();val target=EmergencyDutyTarget(EnumEmergencyDutyTarget.ISSUE,d,EnumEmergencyDutyRole.OWNER,1)
        actor("director");val duty=dutyService.appoint("museum",appointment(target,"owner2",EnumEmergencyDutyQualification.MEMBER,d,unavailable=false))
        assertEquals(listOf("owner"),source.tasks("museum",now()).single {it.kind=="DUTY_HANDOVER"}.assignees.map {it.userId})
        actor("owner");dutyService.sign("museum",duty,"HANDOVER",sig)
        assertEquals(listOf("owner2"),source.tasks("museum",now()).single {it.kind=="DUTY_ACCEPT"}.assignees.map {it.userId})
        actor("owner2");dutyService.sign("museum",duty,"ACCEPT",sig)
        assertEquals(listOf("owner2"),source.tasks("museum",now()).single {it.kind=="ISSUE_1"}.assignees.map {it.userId})
        val i=service.improve("museum",d,improvement().copy(ownerUserId="owner2"))
        val review=source.tasks("museum",now()).single {it.kind=="IMPROVEMENT_REVIEW"}
        assertEquals(i,review.recordId);assertEquals(listOf("keeper"),review.assignees.map {it.userId})
    }

    @Test fun `pending duty acceptance requires appointed qualification and future transfer does not notify early`()=fixture {
        val p=service.registerPlan("museum",plan());val target=EmergencyDutyTarget(EnumEmergencyDutyTarget.PLAN,p,EnumEmergencyDutyRole.KEEPER)
        val v=appointment(target,"keeper2",EnumEmergencyDutyQualification.KEEPER).copy(effectiveAt=now().plusDays(2))
        actor("director");val duty=dutyService.appoint("museum",v);val source=CollectionEmergencyTaskSource()
        val acceptance=source.tasks("museum",now()).single {it.kind=="DUTY_ACCEPT"}
        assertEquals(setOf(CollectionConst.ROLE_STORAGE_HEAD),acceptance.assignees.single().roles)
        actor("keeper2");dutyService.sign("museum",duty,"ACCEPT",sig)
        assertEquals("keeper",source.tasks("museum",now()).single {it.kind=="PLAN_KEEPER"}.assignees.single().userId)
        assertEquals("keeper2",source.tasks("museum",v.effectiveAt.plusSeconds(1)).single {it.kind=="PLAN_KEEPER"}.assignees.single().userId)
    }

    @Test fun `emergency detail exposes frozen files for current and historical report stages`()=fixture {
        val p=prepared();val initial=drill(p,listOf(issue()));val d=service.recordDrill("museum",initial);confirmDrill(d)
        actor("owner");val i=service.improve("museum",d,improvement())
        actor("keeper");service.reviewImprovement("museum",d,i,EmergencyImprovementReviewVo(true,"复核通过","proof"));actor("operator")
        val revision=service.reviseDrill("museum",d,revision(d,initial.copy(title="原报告订正")));confirmReport(d,revision)
        actor("director");val duty=dutyService.appoint("museum",appointment(EmergencyDutyTarget(EnumEmergencyDutyTarget.PLAN,p,EnumEmergencyDutyRole.KEEPER),"keeper2",EnumEmergencyDutyQualification.KEEPER,unavailable=false))
        actor("keeper");dutyService.sign("museum",duty,"HANDOVER",sig)
        actor("keeper2");dutyService.sign("museum",duty,"ACCEPT",sig)
        FileDao["proof"].name="当前通用文件名称，不应覆盖冻结名称.pdf";actor("keeper",CollectionConst.EMERGENCY)
        val before=settledSafetySelectionState(CollectionPermanentArchives,CollectionPermanentArchiveFiles,Files)
        @Suppress("UNCHECKED_CAST")
        val frozen=service.detail("museum")["archiveFiles"] as Map<String,Map<String,List<Map<String,Any?>>>>
        listOf(p to "ISSUED",p to "KEEPER",p to "SECURITY",p to "DIRECTOR",d to "DRILL",d to "EVALUATOR",
            revision to "DRILL_REVISION",revision to "DIRECTOR",i to "IMPROVEMENT",i to "REVIEW",
            duty to "DUTY_APPOINTED",duty to "DUTY_HANDOVER",duty to "DUTY_ACCEPTED").forEach {(record,phase)->
            val file=frozen.getValue(record).getValue(phase).single();assertEquals("proof",file["fileId"])
            val saved=CollectionPermanentArchiveDao.all().single {it.bizId==record && it.phase==phase}
            assertEquals(CollectionPermanentArchiveFileDao.all().single {it.archiveId==saved.id.value}.metadata,file["metadata"])
            assertTrue(file["metadata"].toString().contains("真实签署原件.pdf"));assertFalse(file["metadata"].toString().contains("当前通用文件名称"))
        }
        assertFalse(frozen.getValue(d).containsKey("DRILL_REVISION"));assertFalse(frozen.getValue(revision).containsKey("DRILL"))
        assertEquals(before,settledSafetySelectionState(CollectionPermanentArchives,CollectionPermanentArchiveFiles,Files))
        reject {CollectionPermanentArchiveDao.all().single {it.bizId==p && it.phase=="ISSUED"}.orgId="other";service.detail("museum")}
        reject {io.hizhengfu.cloud.sys.system.dao.RolePermissions.deleteAll();service.detail("museum")}
    }

}
