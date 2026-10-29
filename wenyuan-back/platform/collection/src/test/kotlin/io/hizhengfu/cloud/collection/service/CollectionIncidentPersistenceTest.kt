package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.sys.file.dao.FileDao
import io.hizhengfu.cloud.sys.file.dao.Files
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.provider.EnumSource as NatureSource
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.support.StaticApplicationContext
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import java.time.LocalDateTime

class CollectionIncidentPersistenceTest {
    private val at = LocalDateTime.of(2026, 9, 1, 10, 0)
    private val scope = CollectionScopeResolver("single", "museum")
    private val users = mock(CollectionResponsibleUserService::class.java)
    private val binder = mock(BusinessAttachmentBinder::class.java).also { `when`(it.resolveSingle(anyString())).thenAnswer { c -> c.getArgument<String>(0) } }
    private val archives = CollectionPermanentArchiveService(binder, scope, readAccess=unprotectedArchiveReadAccessProvider())
    private val service = CollectionIncidentService(scope, users, archives, CollectionIncidentSourceService())
    private val locks = CollectionOperationLockService(listOf(CollectionIncidentOperationGuard()))
    private val context = StaticApplicationContext()
    private val temporaryDatabases=mutableListOf<java.nio.file.Path>()
    @BeforeEach fun setup() { context.beanFactory.registerSingleton("cacheManager", ConcurrentMapCacheManager()); context.refresh(); SpringContextHolder().setApplicationContext(context); actor("operator") }
    @AfterEach fun cleanup() { SecurityContextHolder.clearContext(); context.close();temporaryDatabases.forEach { java.nio.file.Files.deleteIfExists(it) } }
    private fun actor(id: String) {
        val user = SecurityUser(id=id,name=id,username=id,password="",groupId="museum",groupIds=setOf("museum"),postIds=emptySet(),dataType=0,roles=emptySet(),authorities=listOf(SimpleGrantedAuthority("ADMIN")),enabled=true)
        SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(user,null,user.authorities)
    }
    private fun database(): Database {
        val databaseFile=java.nio.file.Files.createTempFile("collection-incident-", ".db").also { temporaryDatabases.add(it) }
        val db = Database.connect("jdbc:sqlite:$databaseFile", driver="org.sqlite.JDBC")
        transaction(db) {
            SchemaUtils.create(CollectionOrgs, Collections, CollectionIncidents, CollectionIncidentRecords, CollectionIncidentItems, CollectionPermanentArchives, CollectionPermanentArchiveFiles, Files, CollectionOperationLocks)
            CollectionOrgDao.new("museum") { name="本馆";code="M" }
            incidentReaderFixture("operator","keeper","leader","conservation","department-head")
            listOf("first","second","other","unknown").forEach { key -> CollectionDao.new(key) { orgId="museum";name=key;totalRegNo=key;lifeStatus=EnumCollectionStatus.IN_STORE;nature=if(key=="unknown")null else if(key=="other")EnumCollectionNature.OTHER else EnumCollectionNature.RELIC;level=if(key=="first")EnumRelicLevel.FIRST else if(key=="second")EnumRelicLevel.SECOND else EnumRelicLevel.UNRATED } }
            FileDao.new("proof") { name="实际签署原件.pdf";path="proof";hash="proof";size=100;ext="pdf" }
        }
        return db
    }
    private fun fixture(block: () -> Unit) { transaction(database()) { block() } }
    private fun facts(vararg ids: String) = IncidentFactsVo(title="实际损伤事故",discoveredAt=at,location="实际展厅",reporterUserId="operator",description="已发现的实际损伤",immediateMeasures="隔离并保护现场",items=ids.map { IncidentItemVo(it,true,EnumIncidentSecurity.NONE,false,"损伤部位及实际现状") })
    private fun amend(id:String,v:IncidentFactsVo)=service.amend(id,v,factsId(id))
    private fun factsId(id: String) = service.detail(id)["factsId"] as String
    private fun initialReports(id: String): Map<String, IncidentReportVo> {
        val detail = service.detail(id)
        @Suppress("UNCHECKED_CAST") val reqs = detail["requirements"] as List<Map<String, Any>>
        return reqs.filter { it["kind"]==EnumIncidentReportKind.INCIDENT }.groupBy { it["target"] as EnumIncidentTarget }.map { (target, req) ->
            val v=IncidentReportVo(factsId(id),req.map { it["collectionId"] as String }.toSet(),target,EnumIncidentReportKind.INCIDENT,"实际接收机关或部门",if(target==EnumIncidentTarget.KEEPER)"keeper" else if(target==EnumIncidentTarget.DIRECTOR)"leader" else null,"实际报告编号",at.plusMinutes(5),"实际报告内容","proof")
            service.report(id,v) to v
        }.toMap()
    }
    private fun handling(id: String, reports: Map<String, IncidentReportVo>, disposition: EnumIncidentDisposition = EnumIncidentDisposition.PRESENT_SAFE): IncidentHandlingVo {
        val snapshot = service.detail(id)["facts"] as IncidentFactsSnapshot
        return IncidentHandlingVo(factsId(id),at.plusMinutes(30),"operator","实际原因调查","实际处理措施","proof",snapshot.items.map { IncidentItemResolutionVo(it.item.collectionId,disposition,disposition==EnumIncidentDisposition.PRESENT_SAFE,"处理后的真实现状","实际保管位置或未找到说明",if(disposition==EnumIncidentDisposition.PRESENT_SAFE)"custodian" else null,"后续保护措施","已安排后续检查","proof") },reports.filter { it.value.target !in setOf(EnumIncidentTarget.KEEPER,EnumIncidentTarget.DIRECTOR) }.map { (rid,v) -> IncidentAuthorityResultVo(v.target,v.authorityName,v.collectionIds,at.plusMinutes(25),setOf(rid),"实际核查处理结果","proof") },"keeper","leader","实际案卷索引","proof")
    }
    private fun filing(id: String, h: String, ids: Set<String> = setOf("second")) = service.report(id,IncidentReportVo(factsId(id),ids,EnumIncidentTarget.NATIONAL,EnumIncidentReportKind.RESULT_FILING,"国务院文物行政部门",null,"实际结果备案编号",at.plusMinutes(35),"本次核查处理结果","proof",h))
    private fun confirm(id: String, who: String, minute: Long) { actor(who); service.confirm(id,IncidentConfirmationVo(service.detail(id)["basisRevision"] as String,at.plusMinutes(minute),"本人核对完整实际案卷","proof")) }
    private fun close(id: String, disposition: EnumIncidentDisposition = EnumIncidentDisposition.PRESENT_SAFE) {
        val report=initialReports(id);val h=service.recordHandling(id,handling(id,report,disposition))
        @Suppress("UNCHECKED_CAST") val req=service.detail(id)["requirements"] as List<Map<String,Any>>
        val ids=req.filter { it["kind"]==EnumIncidentReportKind.RESULT_FILING }.map { it["collectionId"] as String }.toSet()
        if(ids.isNotEmpty())filing(id,h,ids)
        confirm(id,"keeper",40);confirm(id,"leader",45)
    }
    @Test fun `first report accepts unknown grade and absent images but holds original independently of approval locks`()=fixture {
        val id=service.create(facts("unknown"))
        assertNotNull(service.detail(id)["factsId"])
        assertThrows(BadRequestException::class.java){locks.acquire("museum",CollectionConst.BIZ_LOAN,"loan",listOf("unknown"))}
        locks.release(CollectionConst.BIZ_LOAN,"loan")
        assertThrows(BadRequestException::class.java){locks.requireUnlocked("unknown")}
        assertDoesNotThrow{locks.acquire("museum",CollectionConst.BIZ_RETURN,"return",listOf("unknown"))}
        val reports=initialReports(id)
        assertThrows(BadRequestException::class.java){service.recordHandling(id,handling(id,reports))}
    }
    @ParameterizedTest
    @NatureSource(value=EnumCollectionNature::class,names=["RELIC","SCIENTIFIC_FOSSIL"])
    fun `mixed grades and theft generate per item national province police and result filing obligations`(nature:EnumCollectionNature)=fixture {
        listOf("first","second").forEach { CollectionDao[it].nature=nature }
        val input=facts("first","second","other");val id=service.create(input.copy(items=input.items.map { if(it.collectionId=="other")it.copy(security=EnumIncidentSecurity.THEFT) else it }))
        @Suppress("UNCHECKED_CAST") val req=service.detail(id)["requirements"] as List<Map<String,Any>>
        fun has(cid:String,target:EnumIncidentTarget,kind:EnumIncidentReportKind=EnumIncidentReportKind.INCIDENT)=req.any { it["collectionId"]==cid && it["target"]==target && it["kind"]==kind }
        assertTrue(has("first",EnumIncidentTarget.NATIONAL));assertFalse(has("first",EnumIncidentTarget.PROVINCE))
        assertTrue(has("second",EnumIncidentTarget.PROVINCE));assertTrue(has("second",EnumIncidentTarget.NATIONAL,EnumIncidentReportKind.RESULT_FILING))
        assertTrue(has("other",EnumIncidentTarget.POLICE));assertTrue(has("other",EnumIncidentTarget.COMPETENT))
        close(id)
        assertEquals(true,service.detail(id)["closed"])
    }
    @Test fun `facts cannot silently drop affected objects damage or security and actual report is version bound`()=fixture {
        val input=facts("first","second");val id=service.create(input)
        val amendment=input.copy(replacementReason="订正依据",replacementFileIds="proof")
        assertThrows(BadRequestException::class.java){amend(id,amendment.copy(items=input.items.take(1)))}
        assertThrows(BadRequestException::class.java){amend(id,amendment.copy(items=input.items.map { it.copy(damaged=false) }))}
        val reports=initialReports(id);amend(id,amendment.copy(description="新增核实事实"))
        assertThrows(BadRequestException::class.java){service.report(id,reports.values.first())}
        assertThrows(BadRequestException::class.java){service.recordHandling(id,handling(id,reports))}
    }
    @ParameterizedTest
    @NatureSource(value=EnumCollectionNature::class,names=["RELIC","SCIENTIFIC_FOSSIL"])
    fun `reported original grade survives later catalog downgrade`(nature:EnumCollectionNature)=fixture {
        listOf("first","second").forEach { CollectionDao[it].nature=nature }
        val input=facts("first");val id=service.create(input);CollectionDao["first"].level=EnumRelicLevel.SECOND
        amend(id,input.copy(replacementReason="依法订正主档后追加说明",replacementFileIds="proof"))
        @Suppress("UNCHECKED_CAST") val req=service.detail(id)["requirements"] as List<Map<String,Any>>
        assertTrue(req.any { it["target"]==EnumIncidentTarget.NATIONAL && it["kind"]==EnumIncidentReportKind.INCIDENT })
    }
    @Test fun `partial or future reports and unrelated coverage cannot authorize processing`()=fixture {
        val id=service.create(facts("first","second"));val report=initialReports(id).values.first()
        assertThrows(BadRequestException::class.java){service.report(id,report.copy(collectionIds=setOf("other")))}
        assertThrows(BadRequestException::class.java){service.report(id,report.copy(reportedAt=LocalDateTime.now().plusDays(1)))}
        val h=handling(id,initialReports(id))
        assertThrows(BadRequestException::class.java){service.recordHandling(id,h.copy(items=h.items.take(1)))}
        assertThrows(BadRequestException::class.java){service.recordHandling(id,h.copy(authorityResults=emptyList()))}
    }
    @Test fun `authority processing must match original reports target dates and scope`()=fixture {
        val id=service.create(facts("first"));val h=handling(id,initialReports(id));val a=h.authorityResults.single()
        assertThrows(BadRequestException::class.java){service.recordHandling(id,h.copy(authorityResults=listOf(a.copy(target=EnumIncidentTarget.PROVINCE))))}
        assertThrows(BadRequestException::class.java){service.recordHandling(id,h.copy(authorityResults=listOf(a.copy(completedAt=at))))}
        assertThrows(BadRequestException::class.java){service.recordHandling(id,h.copy(authorityResults=listOf(a.copy(reportIds=setOf("unrelated")))))}
        service.recordHandling(id,h)
    }
    @Test fun `result filing requires actual handling and cannot be inherited by replacement results`()=fixture {
        val id=service.create(facts("second"));val reports=initialReports(id)
        assertThrows(BadRequestException::class.java){filing(id,"missing")}
        val h=handling(id,reports);val old=service.recordHandling(id,h);filing(id,old)
        val current=service.recordHandling(id,h.copy(actions="新核查结果",replacementReason="实际订正",replacementFileIds="proof"))
        assertNotEquals(old,current)
        assertThrows(BadRequestException::class.java){confirm(id,"keeper",40)}
        filing(id,current);confirm(id,"keeper",40);confirm(id,"leader",45)
    }
    @Test fun `admins cannot substitute responsible signers and leader must follow keeper on same complete evidence`()=fixture {
        val id=service.create(facts("first"));service.recordHandling(id,handling(id,initialReports(id)))
        assertThrows(BadRequestException::class.java){confirm(id,"operator",40)}
        assertThrows(BadRequestException::class.java){confirm(id,"leader",45)}
        actor("keeper")
        assertThrows(BadRequestException::class.java){service.confirm(id,IncidentConfirmationVo("stale",at.plusMinutes(40),"核对","proof"))}
        confirm(id,"keeper",40);confirm(id,"leader",45)
        assertDoesNotThrow{locks.requireUnlocked("first")}
        verify(users,atLeastOnce()).requireRole("leader","museum",CollectionConst.ROLE_DIRECTOR)
    }
    @Test fun `new evidence after keeper confirmation requires fresh keeper review`()=fixture {
        val id=service.create(facts("first"));val reports=initialReports(id);service.recordHandling(id,handling(id,reports));confirm(id,"keeper",40)
        service.report(id,reports.values.first().copy(description="追加实际送达补充原件",reportedAt=at.plusMinutes(41)))
        assertThrows(BadRequestException::class.java){confirm(id,"leader",45)}
        confirm(id,"keeper",42);confirm(id,"leader",45)
    }
    @Test fun `archiving not recovered property keeps restriction and never fabricates restored lifecycle`()=fixture {
        val id=service.create(facts("first").let { it.copy(items=it.items.map { i->i.copy(security=EnumIncidentSecurity.LOSS) }) });close(id,EnumIncidentDisposition.NOT_RECOVERED)
        assertEquals(true,service.detail(id)["closed"])
        assertThrows(BadRequestException::class.java){locks.requireUnlocked("first")}
        assertEquals(EnumCollectionStatus.IN_STORE,CollectionDao["first"].lifeStatus)
    }
    @Test fun `new facts reopen closed case and one case cannot clear another restriction`()=fixture {
        val input=facts("first");val id=service.create(input);val second=service.create(input.copy(title="另一起真实事故"));close(id)
        assertThrows(BadRequestException::class.java){locks.requireUnlocked("first")}
        close(second);assertDoesNotThrow{locks.requireUnlocked("first")}
        amend(id,input.copy(description="归档后新发现",replacementReason="新证据",replacementFileIds="proof"))
        assertEquals(false,service.detail(id)["closed"]);assertThrows(BadRequestException::class.java){locks.requireUnlocked("first")}
    }
    @Test fun `safe disposition requires authenticity and custody while false conclusions remain recordable`()=fixture {
        val id=service.create(facts("other"));val h=handling(id,initialReports(id))
        assertThrows(BadRequestException::class.java){service.recordHandling(id,h.copy(items=h.items.map { it.copy(authenticityConfirmed=false) }))}
        service.recordHandling(id,h.copy(items=h.items.map { it.copy(disposition=EnumIncidentDisposition.PRESENT_RESTRICTED,authenticityConfirmed=false) }))
        confirm(id,"keeper",40);confirm(id,"leader",45)
        assertThrows(BadRequestException::class.java){locks.requireUnlocked("other")}
    }
    @Test fun `tampered permanent evidence prevents confirmation and archive files cannot be deleted`()=fixture {
        val id=service.create(facts("first"));val reports=initialReports(id);service.recordHandling(id,handling(id,reports))
        assertThrows(BadRequestException::class.java){CollectionArchiveFileRetentionGuard().requireDeletable("proof")}
        CollectionIncidentRecordDao[reports.keys.first()].content=JsonUtils.toJson(reports.values.first().copy(description="篡改"))!!
        assertThrows(BadRequestException::class.java){confirm(id,"keeper",40)}
    }
    @Test fun `failed file sealing rolls back report and sequence in actual transaction`() {
        val db=database();val id=transaction(db){service.create(facts("first"))};val before=transaction(db){CollectionIncidentDao[id].sequence}
        assertThrows(BadRequestException::class.java){transaction(db){service.report(id,IncidentReportVo(factsId(id),setOf("first"),EnumIncidentTarget.NATIONAL,EnumIncidentReportKind.INCIDENT,"机关",null,"编号",at.plusMinutes(5),"实际报告","missing-file"))}}
        transaction(db){assertEquals(before,CollectionIncidentDao[id].sequence);assertEquals(1L,CollectionIncidentRecordDao.all().count())}
    }
    @Test fun `admin does not bypass museum scope and zero page size returns real total`()=fixture {
        val id=service.create(facts("first"));val request=MockHttpServletRequest().also { it.setParameter("pageSize","0") }
        assertEquals(1,service.page(request).total)
        CollectionIncidentDao[id].orgId="other-museum"
        assertEquals(0,service.page(request).total)
        assertThrows(BadRequestException::class.java){service.detail(id)}
        assertThrows(BadRequestException::class.java){amend(id,facts("first"))}
    }
    @Test fun `spring injects incident restriction into shared operation lock while information correction remains available`()=fixture {
        service.create(facts("first"))
        org.springframework.context.annotation.AnnotationConfigApplicationContext().use { runtime->
            runtime.register(CollectionIncidentOperationGuard::class.java,CollectionOperationLockService::class.java);runtime.refresh()
            val actual=runtime.getBean(CollectionOperationLockService::class.java)
            assertThrows(BadRequestException::class.java){actual.requireUnlocked("first")}
            assertDoesNotThrow{actual.requireUnlocked("first",CollectionConst.BIZ_CHANGE)}
        }
    }

    @Test fun `return incident source verifies actual inspection archive and preserves reported damage`()=fixture {
        SchemaUtils.create(CollectionReturns, Requisitions)
        val check=ReturnItemCheckVo("first",EnumLoanObjectKind.RELIC,true,"实际查验",false,"发现损伤",true,"proof",true,EnumReturnSecurityIncident.NONE,"实际差异","即时防护")
        val inspection=ReturnInspectionVo(at,custodianUserIds=setOf("keeper"),returningUserIds=setOf("operator"),returningAuthorizationFileIds="proof",distinctPartiesConfirmed=true,originalDocumentsChecked=true,items=listOf(check),signedHandoverFileIds="proof")
        val original=ReturnSourceSnapshotVo(at.minusDays(1),"proof","proof","keeper")
        val r=CollectionReturnDao.new("return") {orgId="museum";sourceBizType=CollectionConst.BIZ_REQUISITION;sourceBizId="req";collectionIds="first";this.inspection=inspection;sourceSnapshot=original}
        val input=facts("first").copy(source=IncidentSourceVo(CollectionConst.BIZ_RETURN,"return"))
        assertThrows(BadRequestException::class.java){service.create(input)}
        archives.seal("museum",CollectionConst.BIZ_RETURN,"return","INSPECTION_original",mapOf("inspection" to inspection,"sourceSnapshot" to original),"proof")
        assertThrows(BadRequestException::class.java){service.create(input.copy(items=input.items.map { it.copy(damaged=false) }))}
        val id=service.create(input);assertNotNull((service.detail(id)["facts"] as IncidentFactsSnapshot).sourceEvidence)
        r.inspection=inspection.copy(items=listOf(check.copy(condition="未经签署改写现状")))
        assertThrows(BadRequestException::class.java){service.create(input.copy(title="试图沿用旧原件"))}
    }
    @Test fun `inventory incident source requires batch check original and cannot silently replace it`()=fixture {
        SchemaUtils.create(InventoryBatches)
        val check=InventoryItemCheckVo("first",at,setOf("keeper"),"实际库位",true,true,true,true,"实际损伤",true,"proof","proof","proof","proof","损伤发现","防护措施")
        val r=InventoryBatchDao.new("batch"){orgId="museum";batchName="实际核查";checks=InventoryChecksVo(listOf(check))}
        val input=facts("first").copy(source=IncidentSourceVo(InventoryComplianceService.BIZ,"batch"))
        assertThrows(BadRequestException::class.java){service.create(input)}
        archives.seal("museum",InventoryComplianceService.BIZ,"batch","CHECK_original",check,"proof")
        assertNotNull(service.create(input))
        r.checks=InventoryChecksVo(listOf(check.copy(condition="没有对应原件的新事实")))
        assertThrows(BadRequestException::class.java){service.create(input.copy(title="新案卷"))}
    }

}
