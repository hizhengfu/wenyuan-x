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
import org.jetbrains.exposed.v1.dao.entityCache
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.mock.web.MockHttpServletRequest
import org.jetbrains.exposed.v1.jdbc.deleteAll
import io.hizhengfu.cloud.flow.service.FlowInstanceService
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

class InventoryCompliancePersistenceTest {
    private val scope = CollectionScopeResolver("single", "museum")
    private val users = mock(CollectionResponsibleUserService::class.java)
    private val binder = mock(BusinessAttachmentBinder::class.java).also { b -> `when`(b.resolveSingle(anyString())).thenAnswer { it.arguments[0] as String } }
    private val archives = CollectionPermanentArchiveService(binder, scope, readAccess=unprotectedArchiveReadAccessProvider())
    private val incidents = CollectionIncidentService(scope, users, archives, CollectionIncidentSourceService())
    private val service = InventoryComplianceService(scope, users, archives, incidents)
    private val batches = InventoryBatchService(scope, binder, archives)
    private val observations=InventoryService(mock(FlowInstanceService::class.java),scope,mock(CollectionService::class.java),binder,batches,mock(CollectionOperationLockService::class.java),service)
    private val at = LocalDateTime.of(2026, 8, 1, 9, 0)
    @BeforeEach fun prepare() {
        val context = StaticApplicationContext(); context.beanFactory.registerSingleton("cacheManager", ConcurrentMapCacheManager()); context.refresh(); SpringContextHolder().setApplicationContext(context)
        val user = SecurityUser(id="operator",name="经办",username="operator",password="",groupId="museum",groupIds=setOf("museum"),postIds=emptySet(),dataType=0,roles=emptySet(),authorities=listOf(SimpleGrantedAuthority(CollectionConst.STORAGE)),enabled=true)
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(user, null, user.authorities)
    }
    @AfterEach fun clean() = SecurityContextHolder.clearContext()
    private fun fixture(block: (InventoryBatchDao) -> Unit) {
        val pg=System.getProperty("collection.test.pg.config")?.let {config ->
            val properties=java.util.Properties().apply {java.nio.file.Files.newInputStream(java.nio.file.Path.of(config)).use(::load)}
            org.postgresql.ds.PGSimpleDataSource().apply {setURL(properties.getProperty("url"));user=properties.getProperty("user");password=properties.getProperty("password")}
        }
        val schema="inventory_compliance_"+java.util.UUID.randomUUID().toString().replace("-","")
        pg?.connection?.use {it.createStatement().use {s->s.execute("CREATE SCHEMA $schema")}}
        pg?.currentSchema=schema
        val db=if(pg==null) Database.connect("jdbc:sqlite:file:inventory-${System.nanoTime()}?mode=memory&cache=shared",driver="org.sqlite.JDBC") else Database.connect(pg)
        try {transaction(db) {
            SchemaUtils.create(CollectionIncidents,CollectionIncidentRecords,CollectionIncidentItems,CollectionOrgs, Collections, CollectionLedgerLogs, Warehouses, Loans, LoanComplianceRecords, Requisitions, CollectionReturns, InventoryBatches, Inventories, InventoryAnnualReports, CollectionPermanentArchives, CollectionPermanentArchiveFiles, Files)
            CollectionOrgDao.new("museum") { name="本馆"; code="M"; ownership=EnumOwnership.STATE }
            CollectionOrgDao.new("other") { name="外馆"; code="O"; ownership=EnumOwnership.STATE }
            WarehouseDao.new("store") { orgId="museum"; name="一号库"; sceneType=EnumLocationScene.STORAGE }
            WarehouseDao.new("hall") { orgId="museum"; name="一号展厅"; sceneType=EnumLocationScene.EXHIBITION }
            listOf("scope","ledger","card","photo","check","report","handover","appointment","supervision","action","receipt").forEach { key -> FileDao.new(key) { name="$key.pdf"; path="/files/$key.pdf"; hash=key; size=100; ext="pdf" } }
            listOf("one", "two").forEachIndexed { i, key -> CollectionDao.new(key) { orgId="museum"; totalRegNo=key; name="藏品$key"; nature=EnumCollectionNature.RELIC; categoryId="category-$i"; level=if(i==0) EnumRelicLevel.FIRST else EnumRelicLevel.GENERAL; lifeStatus=EnumCollectionStatus.IN_STORE; warehouseId=if(i==0) "store" else "hall"; registeredAt=at.minusMonths(2) } }
            CollectionDao.new("foreign") { orgId="other"; totalRegNo="foreign"; name="外馆藏品"; lifeStatus=EnumCollectionStatus.IN_STORE }
            val e = InventoryBatchDao.new("batch") { orgId="museum"; batchName="年度抽检" }
            originalReaderSchema();originalReader("operator",CollectionConst.STORAGE)
            incidentReaderFixture("operator","keeper","leader","conservation","department-head")
            block(e)
        }} finally {pg?.connection?.use {it.createStatement().use {s->s.execute("DROP SCHEMA $schema CASCADE")}}}
    }
    @ParameterizedTest @ValueSource(strings=["batch", "configs", "coverage", "annual", "observations", "summaries", "flow", "import"])
    fun `inventory reads reject current permission removal even with old storage login`(kind:String)=fixture {batch ->
        val row=InventoryDao.new("observation"){orgId="museum";batchId=batch.id.value;batchName=batch.batchName;collectionId="one"}
        fun read():Any?=when(kind) {
            "batch" -> service.get(batch.id.value)
            "configs" -> batches.list()
            "coverage" -> service.coverage(2026)
            "annual" -> service.annualReports(2026)
            "observations" -> observations.findPage(MockHttpServletRequest())
            "summaries" -> observations.batchSummaries(MockHttpServletRequest())
            "flow" -> observations.getFlowInfo(row.id.value)
            else -> observations.validateImport(emptyList())
        }
        assertDoesNotThrow{read()}
        io.hizhengfu.cloud.sys.system.dao.RolePermissions.deleteAll()
        assertThrows(BadRequestException::class.java){read()}
    }
    @Test fun `inventory observation does not reveal foreign collection name or keyword count`()=fixture {batch ->
        InventoryDao.new {orgId="museum";batchId=batch.id.value;batchName="本馆观察";collectionId="foreign"}
        val rows=observations.findPage(MockHttpServletRequest()).list as List<InventoryVo>
        assertNull(rows.single().collectionName)
        val request=MockHttpServletRequest().apply {setParameter("keyword","外馆藏品")}
        assertEquals(0,observations.findPage(request).total)
    }
    @Test fun `inventory coverage does not reveal a foreign warehouse name`()=fixture {
        WarehouseDao.new("foreign-store"){orgId="other";name="外馆受限库房";sceneType=EnumLocationScene.STORAGE}
        CollectionDao["one"].warehouseId="foreign-store"
        val item=service.coverage(2026).population.single {it.collectionId=="one"}
        assertEquals("UNKNOWN",item.locationKey)
        assertFalse(item.locationName.contains("外馆受限库房"))
    }

    @Test fun `inventory annual defaults follow current membership and preserve frozen source facts`()=fixture {batch ->
        full(batch);service.report(report())
        val multi=CollectionScopeResolver("multi","")
        val reader=InventoryComplianceService(multi,users,archives)
        io.hizhengfu.cloud.sys.system.dao.UserDao["operator"].groupId="other"
        assertEquals(setOf("foreign"),reader.coverage(2026).population.map {it.collectionId}.toSet())
        assertTrue(reader.annualReports(2026).isEmpty())
        assertThrows(BadRequestException::class.java){reader.get(batch.id.value)}
        actorRole("operator","cross",setOf(CollectionConst.CROSS_ORG))
        assertEquals(batch.id.value,reader.get(batch.id.value).id)
        val singleOther=InventoryComplianceService(CollectionScopeResolver("single","other"),users,archives)
        assertThrows(BadRequestException::class.java){singleOther.get(batch.id.value)}
        io.hizhengfu.cloud.sys.system.dao.UserDao["operator"].groupId="museum"
        CollectionDao["one"].orgId="other";CollectionDao["one"].name="移交后新名"
        val coverage=reader.annualReports(2026).single()["coverage"] as InventoryCoverageVo
        assertEquals("藏品one",coverage.population.single {it.collectionId=="one"}.content.formalName)
    }
    @Test fun `current museum retains priority for actual use before institution transfer`()=fixture {
        RequisitionDao.new("source-use") { orgId="museum"; collectionIds="two"; outExecuted=true; outExecutedTime=at; returned=true }
        LoanDao.new("source-loan") { orgId="museum"; collectionIds="two"; outExecuted=true; outExecutedTime=at.plusHours(1); returned=true }
        RequisitionDao.new("draft") { orgId="museum"; collectionIds="two"; outExecuted=false; outExecutedTime=at.plusDays(2) }
        RequisitionDao.new("old-use") { orgId="museum"; collectionIds="two"; outExecuted=true; outExecutedTime=at.minusYears(1); returned=true }
        CollectionDao["two"].orgId="other";CollectionDao["two"].warehouseId=null
        io.hizhengfu.cloud.sys.system.dao.UserDao["operator"].groupId="other"
        val reader=InventoryComplianceService(CollectionScopeResolver("multi",""),users,archives)
        val coverage=reader.coverage(2026)
        val transferred=coverage.population.single {it.collectionId=="two"}
        assertTrue(transferred.usedThisYear)
        assertEquals(at.plusHours(1),transferred.priorityAfter)
        assertTrue("two" in coverage.uncheckedPriorityIds)
        assertFalse("foreign" in coverage.uncheckedPriorityIds)
    }
    @Test fun `transferred collection cannot satisfy current museum category or location coverage`()=fixture { batch ->
        full(batch)
        CollectionDao["one"].orgId="other"; CollectionDao["one"].warehouseId=null
        CollectionDao.new("replacement") {
            orgId="museum"; totalRegNo="replacement"; name="原类别新藏品"
            nature=EnumCollectionNature.RELIC; categoryId="category-0"; level=EnumRelicLevel.GENERAL
            lifeStatus=EnumCollectionStatus.IN_STORE; warehouseId="store"; registeredAt=at.plusDays(3)
        }
        val coverage=service.coverage(2026)
        assertEquals(setOf("two", "replacement"), coverage.population.map { it.collectionId }.toSet())
        assertEquals(setOf("two"), coverage.checkedCollectionIds)
        assertTrue("category-0" in coverage.missingCategories)
        assertTrue("WAREHOUSE:store" in coverage.missingLocations)
        assertEquals(setOf(batch.id.value), coverage.completedBatchIds)
        assertFalse(coverage.ready())
    }
    @Test fun `old check at another location cannot cover a newly occupied current location`()=fixture { batch ->
        full(batch)
        CollectionDao["one"].warehouseId="hall"
        CollectionDao.new("replacement") {
            orgId="museum"; totalRegNo="replacement"; name="一号库新藏品"
            nature=EnumCollectionNature.RELIC; categoryId="category-0"; level=EnumRelicLevel.GENERAL
            lifeStatus=EnumCollectionStatus.IN_STORE; warehouseId="store"; registeredAt=at.plusDays(3)
        }
        val coverage=service.coverage(2026)
        assertEquals(setOf("one", "two"), coverage.checkedCollectionIds)
        assertTrue("WAREHOUSE:store" in coverage.missingLocations)
        assertFalse(coverage.ready())
    }
    @Test fun `old classification check cannot cover changed or newly assigned categories`()=fixture { batch ->
        full(batch)
        CollectionDao["one"].categoryId="category-new"
        CollectionDao.new("replacement") {
            orgId="museum"; totalRegNo="replacement"; name="原类别新藏品"
            nature=EnumCollectionNature.RELIC; categoryId="category-0"; level=EnumRelicLevel.GENERAL
            lifeStatus=EnumCollectionStatus.IN_STORE; warehouseId="store"; registeredAt=at.plusDays(3)
        }
        val coverage=service.coverage(2026)
        assertEquals(setOf("one", "two"), coverage.checkedCollectionIds)
        assertEquals(setOf("category-0", "category-new"), coverage.missingCategories)
        assertFalse(coverage.ready())
    }
    @Test fun `inventory rejects foreign original ownership but preserves same museum evidence errors`()=fixture {batch ->
        service.begin(batch.id.value,plan())
        CollectionPermanentArchiveDao.all().first().orgId="other"
        assertThrows(BadRequestException::class.java){service.get(batch.id.value)}
        assertThrows(BadRequestException::class.java){batches.list()}
    }
    @Test fun `inventory annual coverage year must match report even if snapshot digest agrees`()=fixture {batch ->
        full(batch);val id=service.report(report())
        val row=InventoryAnnualReportDao[id]
        val coverage=JsonUtils.fromJson(row.coverage,InventoryCoverageVo::class.java)!!.copy(year=2025)
        row.coverage=JsonUtils.toJson(coverage)!!
        val original=CollectionPermanentArchiveDao.all().single {it.bizType==InventoryComplianceService.ANNUAL}
        val snapshot=JsonUtils.mapper.readTree(original.snapshot) as tools.jackson.databind.node.ObjectNode
        snapshot.set("coverage",JsonUtils.mapper.valueToTree(coverage));original.snapshot=snapshot.toString();original.digest=CollectionCareEvidence.digest(original.snapshot)
        val output=service.annualReports(2026).single()
        assertTrue((output["evidenceErrors"] as List<*>).isNotEmpty());assertNull(output["content"]);assertNull(output["coverage"])
    }

    @Test fun `cross museum inventory keyword counts require matching observation ownership`()=fixture {batch ->
        InventoryDao.new("wrong-link"){orgId="museum";batchId=batch.id.value;batchName="本馆观察";collectionId="foreign"}
        InventoryDao.new("correct-link"){orgId="other";batchName="外馆观察";collectionId="foreign"}
        actorRole("operator","current-cross",setOf(CollectionConst.CROSS_ORG))
        val old=io.hizhengfu.cloud.common.utils.SecurityUtils.getUserDetail()
        val login=SecurityUser(id=old.id,name=old.name,username=old.username,password="",groupId=old.groupId,groupIds=old.groupIds,postIds=emptySet(),dataType=0,roles=emptySet(),authorities=old.authorities+SimpleGrantedAuthority(CollectionConst.CROSS_ORG),enabled=true)
        SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(login,null,login.authorities)
        val multi=InventoryService(mock(FlowInstanceService::class.java),CollectionScopeResolver("multi",""),mock(CollectionService::class.java),binder,batches,mock(CollectionOperationLockService::class.java),service)
        val result=multi.findPage(MockHttpServletRequest().apply {setParameter("keyword","外馆藏品")})
        assertEquals(1L,result.total)
        val row=(result.list as List<InventoryVo>).single()
        assertEquals("correct-link",row.id);assertEquals("外馆藏品",row.collectionName)
    }

    private fun plan(purpose: EnumInventoryPurpose = EnumInventoryPurpose.ROUTINE) = InventoryPlanVo(2026, EnumInventoryMethod.FULL, purpose, "全馆账物卡照清点", "keeper", "leader", "scope")
    private fun check(id: String) = InventoryItemCheckVo(id, at, setOf("custodian"), "实际现场位置", true, true, true, true, "保存稳定", false, "ledger", "card", "photo", "check")
    private fun completion() = InventoryCompletionVo(at.plusDays(2), "keeper", "leader", "report")
    private fun action(id: String) = InventoryIssueActionVo(id, at.plusDays(1), "已启动现场及原件核查", "及时防护并安排追查", "custodian", false, at.plusDays(3), true, "涉及珍贵文物重大异常", "action", "主管文物行政部门", "报告-1", at.plusHours(1), "receipt")
    private fun incident(e: InventoryBatchDao, major: Boolean = true): String = IncidentSourceTestSupport(incidents,at,"report").create(
        IncidentSourceVo(InventoryComplianceService.BIZ,e.id.value),e.checks!!.items.filter { it.abnormal() }.map { IncidentItemVo(it.collectionId,it.damaged==true,EnumIncidentSecurity.NONE,major,it.findings!!) })
    private fun closedIncident(e: InventoryBatchDao): String = incident(e).also { IncidentSourceTestSupport(incidents,at,"report").close(it) }
    private fun resolved(case: String?) = action("one").copy(incidentId=case,resolved=true,followUpAt=null,safeForUse=true,keeperUserId="keeper",leaderUserId="leader",resolutionApprovedAt=at.plusDays(1),signedResolutionFileIds="report")
    private fun full(e: InventoryBatchDao) {
        service.begin(e.id.value, plan()); service.check(e.id.value, check("one")); service.check(e.id.value, check("two")); service.complete(e.id.value, completion())
    }
    private fun report() = InventoryAnnualReportVo(2026, at.plusDays(3), "leader", "report", "主管文物行政部门", "清点报送-1", at.plusDays(4), "receipt")

    @ParameterizedTest @ValueSource(strings=["STARTED", "CHECK_", "COMPLETED"])
    fun `inventory cannot use originals whose frozen file identity changed`(phase:String)=fixture { e ->
        full(e)
        assertTrue(service.get(e.id.value).evidenceErrors.isEmpty())
        assertTrue(e.id.value in service.coverage(2026).completedBatchIds)
        val archive=CollectionPermanentArchiveDao.all().first { it.bizId==e.id.value && it.phase.startsWith(phase) }
        val file=CollectionPermanentArchiveFileDao.all().first { it.archiveId==archive.id.value }
        val metadata=JsonUtils.mapper.readTree(file.metadata) as tools.jackson.databind.node.ObjectNode
        metadata.put("path", "/files/changed-inventory-original.pdf")
        file.metadata=metadata.toString()
        val count=CollectionPermanentArchiveDao.count()
        assertTrue(service.get(e.id.value).evidenceErrors.isNotEmpty())
        assertEquals(setOf(e.id.value),service.coverage(2026).unverifiedBatchIds)
        assertThrows(BadRequestException::class.java) { service.report(report()) }
        assertEquals(count,CollectionPermanentArchiveDao.count())
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings=["plan-cleared","year","scope","check","drop-check","completion","drop-completion","digest","archive-json","file-reference","file-missing","file-owner","moved-org"])
    fun `unverified current data cannot complete or contribute annual coverage`(change:String)=fixture { e ->
        full(e)
        when(change) {
            "plan-cleared"->e.plan=null
            "year"->e.plan=e.plan!!.copy(year=2025)
            "scope"->e.collectionIds="one"
            "check"->e.checks=InventoryChecksVo(e.checks!!.items.map {it.copy(condition="unarchived edit")})
            "drop-check"->e.checks=InventoryChecksVo(e.checks!!.items.take(1))
            "completion"->e.completion=e.completion!!.copy(signedAt=at.plusDays(3))
            "drop-completion"->e.completion=null
            "digest"->CollectionPermanentArchiveDao.all().first {it.phase.startsWith("CHECK_")}.digest="not-the-original-digest"
            "archive-json"->CollectionPermanentArchiveDao.all().first {it.phase.startsWith("CHECK_")}.snapshot="not-json"
            "file-reference"->CollectionPermanentArchiveFileDao.all().first().delete()
            "file-missing"->FileDao["check"].delete()
            "file-owner"->CollectionPermanentArchiveFileDao.all().first().orgId="other"
            "moved-org"->e.orgId="other"
        }
        val coverage=service.coverage(2026)
        assertEquals(setOf("batch"),coverage.unverifiedBatchIds)
        assertTrue(coverage.completedBatchIds.isEmpty());assertFalse(coverage.ready())
        assertThrows(BadRequestException::class.java){service.complete(e.id.value,completion())}
        assertThrows(BadRequestException::class.java){service.report(report())}
        if(change!="moved-org")assertTrue(service.get(e.id.value).evidenceErrors.isNotEmpty())
    }
    @Test fun `late keeper handover records genuine dates and supporting reasons without marking timely`()=fixture {e->
        val p=plan(EnumInventoryPurpose.KEEPER_DEPARTURE).copy(leavingUserId="old",successorUserId="new",leavingAt=at.plusDays(1),responsibilityFileIds="scope")
        service.begin(e.id.value,p);service.check(e.id.value,check("one"));service.check(e.id.value,check("two"))
        val value=completion().copy(actualHandoverAt=p.leavingAt,handoverFileIds="handover")
        assertThrows(BadRequestException::class.java){service.complete(e.id.value,value)}
        assertThrows(BadRequestException::class.java){service.complete(e.id.value,value.copy(departureDelayReason="实际迟办原因及处理"))}
        assertNull(e.completion)
        val late=value.copy(departureDelayReason="实际迟办原因及处理",departureDelayFileIds="action")
        service.complete(e.id.value,late);val count=CollectionPermanentArchiveDao.count();service.complete(e.id.value,late)
        assertEquals(count,CollectionPermanentArchiveDao.count())
        assertEquals(EnumInventoryDepartureStatus.COMPLETED_LATE,service.get(e.id.value).departureStatus)
        assertTrue(service.get(e.id.value).evidenceErrors.isEmpty())
        assertEquals(setOf(e.id.value),service.coverage(2026).departureLateBatchIds)
        val reportId=service.report(report());assertTrue(InventoryAnnualReportDao[reportId].coverage.contains("departureLateBatchIds"))
        assertEquals(p.leavingAt,e.plan!!.leavingAt);assertEquals(late,e.completion)
        assertThrows(BadRequestException::class.java){CollectionArchiveFileRetentionGuard().requireDeletable("action")}
    }
    @Test fun `late director handover still requires actual competent supervision and correct chronology`()=fixture {e->
        val p=plan(EnumInventoryPurpose.DIRECTOR_DEPARTURE).copy(leavingUserId="old",successorUserId="new",leavingAt=at.plusDays(1),responsibilityFileIds="scope")
        service.begin(e.id.value,p);service.check(e.id.value,check("one"));service.check(e.id.value,check("two"))
        val late=completion().copy(actualHandoverAt=at.plusDays(1),handoverFileIds="handover",departureDelayReason="实际迟办原因",departureDelayFileIds="action")
        assertThrows(BadRequestException::class.java){service.complete(e.id.value,late)}
        val valid=late.copy(supervisorAuthority="主管文物行政部门",supervisorNames=setOf("实际派员"),supervisionAt=at,appointmentFileIds="appointment",supervisionFileIds="supervision")
        assertThrows(BadRequestException::class.java){service.complete(e.id.value,valid.copy(actualHandoverAt=at.minusDays(1)))}
        service.complete(e.id.value,valid);assertEquals(EnumInventoryDepartureStatus.COMPLETED_LATE,service.get(e.id.value).departureStatus)
    }
    @Test fun `recording a past timely departure later does not invent a late handover`()=fixture {e->
        val p=plan(EnumInventoryPurpose.KEEPER_DEPARTURE).copy(leavingUserId="old",successorUserId="new",leavingAt=at.plusDays(5),responsibilityFileIds="scope")
        service.begin(e.id.value,p);service.check(e.id.value,check("one"));service.check(e.id.value,check("two"))
        service.complete(e.id.value,completion().copy(actualHandoverAt=at.plusDays(1),handoverFileIds="handover"))
        assertEquals(EnumInventoryDepartureStatus.COMPLETED_BEFORE_DEPARTURE,service.get(e.id.value).departureStatus)
        assertTrue(service.coverage(2026).departureLateBatchIds.isEmpty())
    }
    @Test fun `reverting current check to an earlier signed revision is not accepted`()=fixture {e->
        service.begin(e.id.value,plan());service.check(e.id.value,check("one"))
        service.check(e.id.value,check("one").copy(condition="补充实际细节",replacementReason="有据更正"));service.check(e.id.value,check("two"))
        e.checks=InventoryChecksVo(listOf(check("one"),check("two")))
        assertThrows(BadRequestException::class.java){service.complete(e.id.value,completion())}
        assertTrue(service.get(e.id.value).evidenceErrors.isNotEmpty())
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.CsvSource("revoked,false", "revoked,true", "disabled,false", "disabled,true", "moved,false", "moved,true")
    fun `observation saves reject changed current qualification`(change: String, update: Boolean)=fixture { e ->
        val value=InventoryVo(batchName=e.batchName,collectionId="one",result=EnumInventoryResult.MATCH,note="原观察")
        val id=observations.save(value)
        when(change) {
            "revoked" -> io.hizhengfu.cloud.sys.system.dao.RolePermissions.deleteAll()
            "disabled" -> io.hizhengfu.cloud.sys.system.dao.UserDao["operator"].enabled=false
            "moved" -> io.hizhengfu.cloud.sys.system.dao.UserDao["operator"].groupId="other"
        }
        val before=JsonUtils.toJson(InventoryDao[id].toModel());val count=InventoryDao.count();val batchesBefore=InventoryBatchDao.count()
        assertThrows(BadRequestException::class.java){observations.save(value.copy(id=if(update)id else null,note="旧资格写入"))}
        assertEquals(before,JsonUtils.toJson(InventoryDao[id].toModel()));assertEquals(count,InventoryDao.count());assertEquals(batchesBefore,InventoryBatchDao.count())
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.CsvSource("revoked,false", "revoked,true", "disabled,false", "disabled,true", "moved,true")
    fun `batch draft writes reject stale current qualification`(change: String, update: Boolean)=fixture { e ->
        val value=e.toModel().copy(id=if(update)e.id.value else null,batchName="权限核验批次",remark="不能通过旧资格写入")
        when(change) {
            "revoked" -> io.hizhengfu.cloud.sys.system.dao.RolePermissions.deleteAll()
            "disabled" -> io.hizhengfu.cloud.sys.system.dao.UserDao["operator"].enabled=false
            "moved" -> io.hizhengfu.cloud.sys.system.dao.UserDao["operator"].groupId="other"
        }
        val before=JsonUtils.toJson(e.toModel());val count=InventoryBatchDao.count()
        assertThrows(BadRequestException::class.java){batches.save(value)}
        assertEquals(before,JsonUtils.toJson(e.toModel()));assertEquals(count,InventoryBatchDao.count())
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.CsvSource("begin,revoked", "begin,disabled", "begin,moved", "check,revoked", "check,disabled", "check,moved", "action,revoked", "action,disabled", "action,moved", "complete,revoked", "complete,disabled", "complete,moved")
    fun `inventory stage writes reject changed current qualification`(operation: String, change: String)=fixture { e ->
        service.begin(e.id.value,plan())
        val item=if(operation=="action") check("one").copy(damaged=true,findings="发现裂隙",protectiveMeasures="暂停使用并防护") else check("one")
        service.check(e.id.value,item)
        if(operation=="action") service.action(e.id.value,action("one"))
        if(operation=="complete") {service.check(e.id.value,check("two"));service.complete(e.id.value,completion())}
        when(change) {
            "revoked" -> io.hizhengfu.cloud.sys.system.dao.RolePermissions.deleteAll()
            "disabled" -> io.hizhengfu.cloud.sys.system.dao.UserDao["operator"].enabled=false
            "moved" -> io.hizhengfu.cloud.sys.system.dao.UserDao["operator"].groupId="other"
        }
        fun originals()=CollectionPermanentArchiveDao.all().map { Triple(it.id.value,it.snapshot,it.digest) }.sortedBy { it.first }
        fun content()=JsonUtils.toJson(listOf(e.plan,e.baseline,e.checks,e.issueActions,e.completion))
        val before=originals();val business=content();val held=CollectionDao["one"].inventoryHold
        assertThrows(BadRequestException::class.java) {
            when(operation) {
                "begin" -> service.begin(e.id.value,plan())
                "check" -> service.check(e.id.value,item)
                "action" -> service.action(e.id.value,action("one"))
                else -> service.complete(e.id.value,completion())
            }
        }
        assertEquals(before,originals());assertEquals(business,content());assertEquals(held,CollectionDao["one"].inventoryHold)
    }
    @ParameterizedTest @ValueSource(booleans=[false,true])
    fun `annual inventory report rejects revoked and disabled old login`(disabled: Boolean)=fixture { e ->
        full(e);val value=report();service.report(value)
        if(disabled) io.hizhengfu.cloud.sys.system.dao.UserDao["operator"].enabled=false
        else io.hizhengfu.cloud.sys.system.dao.RolePermissions.deleteAll()
        fun originals()=CollectionPermanentArchiveDao.all().map { Triple(it.id.value,it.snapshot,it.digest) }.sortedBy { it.first }
        val before=originals();val count=InventoryAnnualReportDao.count()
        assertThrows(BadRequestException::class.java){service.report(value)}
        assertEquals(before,originals());assertEquals(count,InventoryAnnualReportDao.count())
    }
    @Test fun `repeated annual submission verifies its original retained report`()=fixture {e->
        full(e);val id=service.report(report())
        InventoryAnnualReportDao[id].coverage="{}"
        assertThrows(BadRequestException::class.java){service.report(report())}
        assertEquals(1L,InventoryAnnualReportDao.count())
        assertTrue((service.annualReports(2026).single()["evidenceErrors"] as List<*>).isNotEmpty())
        assertNull(service.annualReports(2026).single()["content"])
    }
    @Test fun `annual report rejects altered original identity without rewriting its filing`()=fixture { e ->
        full(e)
        val id=service.report(report())
        assertTrue((service.annualReports(2026).single()["evidenceErrors"] as List<*>).isEmpty())
        val archive=CollectionPermanentArchiveDao.all().single { it.bizType==InventoryComplianceService.ANNUAL && it.bizId==id }
        val file=CollectionPermanentArchiveFileDao.all().first { it.archiveId==archive.id.value }
        val metadata=JsonUtils.mapper.readTree(file.metadata) as tools.jackson.databind.node.ObjectNode
        metadata.put("path", "/files/changed-annual-report.pdf")
        file.metadata=metadata.toString()
        val count=CollectionPermanentArchiveDao.count()
        val history=service.annualReports(2026).single()
        assertTrue((history["evidenceErrors"] as List<*>).isNotEmpty())
        assertNull(history["content"])
        assertThrows(BadRequestException::class.java) { service.report(report()) }
        assertEquals(count,CollectionPermanentArchiveDao.count())
    }
    @Test fun `persisted completion and pre-upgrade absent delay fields preserve their original meaning`()=fixture {e->
        full(e)
        val a=CollectionPermanentArchiveDao.all().single {it.phase=="COMPLETED"}
        val tree=JsonUtils.mapper.readTree(a.snapshot)
        val completion=tree.get("completion") as tools.jackson.databind.node.ObjectNode
        completion.remove("departureDelayReason");completion.remove("departureDelayFileIds")
        a.snapshot=tree.toString();a.digest=CollectionCareEvidence.digest(a.snapshot)
        TransactionManager.current().entityCache.flush();TransactionManager.current().entityCache.clear()
        assertTrue(service.get("batch").evidenceErrors.isEmpty())
        assertTrue(service.coverage(2026).ready())
        service.complete("batch",completion())
    }
    @Test fun `missing incident clearance cannot be replaced by a resolved action flag`()=fixture {e->
        service.begin(e.id.value,plan())
        service.check(e.id.value,check("one").copy(damaged=true,findings="发现损傷",protectiveMeasures="停止使用并防护"))
        service.check(e.id.value,check("two"));service.action(e.id.value,action("one"));service.complete(e.id.value,completion())
        val id=closedIncident(e)
        service.action(e.id.value,action("one").copy(incidentId=id,recordedAt=at.plusDays(3),resolved=true,followUpAt=null,safeForUse=true,keeperUserId="keeper",leaderUserId="leader",resolutionApprovedAt=at.plusDays(3),signedResolutionFileIds="report"))
        CollectionPermanentArchiveDao.all().single {it.phase.startsWith("INCIDENT_CLEARANCE_")}.delete()
        assertTrue(service.get(e.id.value).evidenceErrors.isNotEmpty())
        assertEquals(setOf(e.id.value),service.coverage(2026).unverifiedBatchIds)
    }

    @Test fun `historical path references survive legitimate renaming through their frozen file identity`()=fixture {e->
        val oldPath="/files/check.pdf"
        `when`(binder.resolveSingle(oldPath)).thenReturn("check")
        service.begin(e.id.value,plan())
        service.check(e.id.value,check("one").copy(signedCheckFileIds=oldPath));service.check(e.id.value,check("two"))
        FileDao["check"].path="/files/renamed-check.pdf"
        `when`(binder.resolveSingle(oldPath)).thenThrow(BadRequestException("当前路径已经改名"))
        service.complete(e.id.value,completion())
        assertTrue(service.get(e.id.value).evidenceErrors.isEmpty())
        assertTrue(service.coverage(2026).ready())
        assertTrue(CollectionPermanentArchiveFileDao.all().any {it.fileId=="check" && it.metadata.contains(oldPath)})
        assertEquals(oldPath,e.checks!!.items.single {it.collectionId=="one"}.signedCheckFileIds)
    }

    @Test fun `free form batch fields cannot finalize actual inventory or replace frozen scope`() = fixture { e ->
        e.finishedAt=at; e.reportAttachmentIds="report"
        assertThrows(BadRequestException::class.java) { service.complete(e.id.value, completion()) }
        e.collectionIds="one"
        assertThrows(BadRequestException::class.java) { service.begin(e.id.value, plan()) }
        e.collectionIds="one,foreign"
        assertThrows(BadRequestException::class.java) { service.begin(e.id.value, plan().copy(method=EnumInventoryMethod.SAMPLE)) }
        e.collectionIds=null
        service.begin(e.id.value, plan()); service.begin(e.id.value, plan())
        assertNull(e.finishedAt); assertEquals(2, e.baseline!!.items.size)
        assertThrows(BadRequestException::class.java) { batches.save(e.toModel().copy(collectionIds="one")) }
        assertThrows(BadRequestException::class.java) { service.begin(e.id.value, plan().copy(scopeDescription="改换范围")) }
        verify(users, atLeastOnce()).requireRole("keeper","museum",CollectionConst.ROLE_STORAGE_HEAD)
    }
    @Test fun `rfid labels cannot be approved as actual inventory and false conclusions remain explicit`() = fixture { e ->
        val observation = InventoryDao.new { orgId="museum"; batchId=e.id.value; collectionId="one"; result=EnumInventoryResult.MATCH }
        assertThrows(BadRequestException::class.java) { service.requireActualCheck(observation) }
        service.begin(e.id.value, plan())
        assertThrows(BadRequestException::class.java) { service.check(e.id.value, check("one").copy(cardMatches=null)) }
        assertThrows(BadRequestException::class.java) { service.check(e.id.value, check("one").copy(cardFileIds=null)) }
        service.check(e.id.value, check("one").copy(cardMatches=false,cardFileIds=null,findings="编目卡未建立",protectiveMeasures="保留现场核查原件"))
        assertThrows(BadRequestException::class.java) { service.requireActualCheck(observation) }
        observation.result=EnumInventoryResult.ABNORMAL
        assertDoesNotThrow { service.requireActualCheck(observation) }
        assertEquals(EnumCollectionStatus.IN_STORE, CollectionDao["one"].lifeStatus)
    }
    @Test fun `all actual checks and originals required with append only corrections and sealed completion`() = fixture { e ->
        service.begin(e.id.value, plan()); service.check(e.id.value, check("one"))
        assertThrows(BadRequestException::class.java) { service.complete(e.id.value, completion()) }
        assertThrows(BadRequestException::class.java) { service.check(e.id.value, check("one").copy(condition="补记细节")) }
        service.check(e.id.value, check("one").copy(condition="补记细节",replacementReason="原记录补充"))
        service.check(e.id.value, check("two")); service.complete(e.id.value, completion()); service.complete(e.id.value, completion())
        assertEquals(5, archives.list(InventoryComplianceService.BIZ, e.id.value).size)
        assertThrows(BadRequestException::class.java) { service.check(e.id.value, check("one")) }
        assertThrows(BadRequestException::class.java) { CollectionArchiveFileRetentionGuard().requireDeletable("check") }
    }
    @Test fun `major differences require investigation and actual reporting before completion and remain followed`() = fixture { e ->
        service.begin(e.id.value, plan())
        val damaged = check("one").copy(damaged=true,findings="发现裂隙",protectiveMeasures="暂停使用并落实防护")
        service.check(e.id.value, damaged); service.check(e.id.value, check("two"))
        assertThrows(BadRequestException::class.java) { service.complete(e.id.value, completion()) }
        assertThrows(BadRequestException::class.java) { service.action(e.id.value, action("one").copy(reportFileIds=null)) }
        assertThrows(BadRequestException::class.java) { service.action(e.id.value, action("one").copy(major=null)) }
        service.action(e.id.value, action("one")); service.complete(e.id.value, completion())
        assertThrows(BadRequestException::class.java) { service.action(e.id.value,action("one").copy(major=false)) }
        assertTrue(CollectionDao["one"].inventoryHold)
        val holdLogs=CollectionLedgerLogDao.all().filter { it.collectionId=="one" && it.field=="inventoryHold" }
        assertEquals(1,holdLogs.size)
        assertEquals("false",holdLogs.single().before)
        assertEquals("true",holdLogs.single().after)
        assertEquals(e.id.value,holdLogs.single().refBizId)
        val collections=CollectionService(scope,binder,mock(CollectionRegistrationNumberService::class.java),CollectionOperationLockService(),mock(CollectionCatalogService::class.java),mock(CollectionChangeService::class.java),mock(CollectionRetainedLedgerService::class.java),mock(CollectionDisclosureService::class.java))
        for (target in listOf(EnumCollectionStatus.IN_USE,EnumCollectionStatus.ON_LOAN,EnumCollectionStatus.RESTORING,EnumCollectionStatus.PRE_OUT)) assertThrows(BadRequestException::class.java) { collections.changeLifeStatus("one",target) }
        assertEquals(EnumCollectionStatus.IN_STORE,CollectionDao["one"].lifeStatus)
        assertThrows(BadRequestException::class.java) { CollectionOutboundPolicy.requireInStore(CollectionDao["one"].lifeStatus,"借用",CollectionDao["one"].inventoryHold) }
        assertEquals(setOf("one"), service.coverage(2026).pendingIssueIds)
        service.action(e.id.value, action("one").copy(incidentId=closedIncident(e),recordedAt=at.plusDays(3),resolved=true,followUpAt=null,actions="实际调查及处理结束",safeForUse=true,keeperUserId="keeper",leaderUserId="leader",resolutionApprovedAt=at.plusDays(3),signedResolutionFileIds="report"))
        assertTrue(service.coverage(2026).pendingIssueIds.isEmpty())
        assertFalse(CollectionDao["one"].inventoryHold)
        val finalHoldLogs=CollectionLedgerLogDao.all().filter { it.collectionId=="one" && it.field=="inventoryHold" }
        assertEquals(2,finalHoldLogs.size)
        assertTrue(finalHoldLogs.any { it.before=="true" && it.after=="false" && it.refBizId==e.id.value })
        assertThrows(BadRequestException::class.java) { service.check(e.id.value, damaged.copy(damaged=false,replacementReason="抹去原差异")) }
    }
    @Test fun `state director departure requires all collection handover before leaving and competent supervision`() = fixture { e ->
        val p=plan(EnumInventoryPurpose.DIRECTOR_DEPARTURE).copy(leavingUserId="old-director",successorUserId="new-director",leavingAt=at.plusDays(5),responsibilityFileIds="scope")
        service.begin(e.id.value,p); service.check(e.id.value,check("one")); service.check(e.id.value,check("two"))
        val value=completion().copy(actualHandoverAt=at.plusDays(1),handoverFileIds="handover")
        assertThrows(BadRequestException::class.java) { service.complete(e.id.value,value) }
        val signed=value.copy(supervisorAuthority="主管文物行政部门",supervisorNames=setOf("实际派员甲"),supervisionAt=at.plusHours(1),appointmentFileIds="appointment",supervisionFileIds="supervision")
        assertThrows(BadRequestException::class.java) { service.complete(e.id.value,signed.copy(actualHandoverAt=at.plusDays(6))) }
        service.complete(e.id.value,signed)
        assertNotNull(e.completion)
        verify(users).requireRole("old-director","museum",CollectionConst.ROLE_DIRECTOR)
        verify(users).requireRole("new-director","museum",CollectionConst.ROLE_DIRECTOR)
    }
    @Test fun `keeper departure uses signed responsibility scope and two actual confirming heads`() = fixture { e ->
        e.collectionIds="one"
        val p=plan(EnumInventoryPurpose.KEEPER_DEPARTURE).copy(method=EnumInventoryMethod.SPECIAL,leavingUserId="old-keeper",successorUserId="new-keeper",leavingAt=at.plusDays(5),responsibilityFileIds="scope")
        service.begin(e.id.value,p); service.check(e.id.value,check("one"))
        assertThrows(BadRequestException::class.java) { service.complete(e.id.value,completion().copy(keeperUserId="leader",actualHandoverAt=at.plusDays(1),handoverFileIds="handover")) }
        service.complete(e.id.value,completion().copy(actualHandoverAt=at.plusDays(1),handoverFileIds="handover"))
        assertFalse(service.coverage(2026).ready())
        verify(users).requireRole("old-keeper","museum",CollectionConst.ROLE_KEEPER)
    }
    @Test fun `annual coverage catches missing category location priority and archived reports need actual signature and receipt`() = fixture { e ->
        e.collectionIds="two"
        service.begin(e.id.value,plan().copy(method=EnumInventoryMethod.SAMPLE)); service.check(e.id.value,check("two")); service.complete(e.id.value,completion())
        val coverage=service.coverage(2026)
        assertEquals(setOf("category-0"),coverage.missingCategories); assertEquals(setOf("one"),coverage.uncheckedPriorityIds); assertTrue(coverage.missingLocations.containsKey("WAREHOUSE:store"))
        assertThrows(BadRequestException::class.java) { service.report(report()) }
        val other=InventoryBatchDao.new("supplement") { orgId="museum"; batchName="补充抽检"; collectionIds="one" }
        service.begin(other.id.value,plan().copy(method=EnumInventoryMethod.SAMPLE)); service.check(other.id.value,check("one")); service.complete(other.id.value,completion())
        assertTrue(service.coverage(2026).ready())
        assertThrows(BadRequestException::class.java) { service.report(report().copy(receiptFileIds="")) }
        assertThrows(BadRequestException::class.java) { service.report(report().copy(signedAt=at)) }
        val id=service.report(report()); assertEquals(id,service.report(report()))
        assertEquals(1,service.annualReports(2026).size)
        assertThrows(BadRequestException::class.java) { service.report(report().copy(reportNo="补报")) }
        service.report(report().copy(reportNo="补报",supplementReason="补充主管机关材料"))
        assertEquals(2,service.annualReports(2026).size)
    }
    @Test fun `loan occurring after check invalidates priority coverage and current place coverage`() = fixture { e ->
        full(e)
        assertTrue(service.coverage(2026).ready())
        LoanDao.new("loan") { orgId="museum"; collectionIds="one"; borrower="借入馆"; outExecuted=true; outExecutedTime=at.plusDays(3); loanDate=at.plusDays(3).toLocalDate(); returnDate=at.plusMonths(1).toLocalDate() }
        CollectionDao["one"].lifeStatus=EnumCollectionStatus.ON_LOAN
        val c=service.coverage(2026)
        assertEquals(setOf("one"),c.uncheckedPriorityIds); assertTrue(c.missingLocations.containsKey("LOAN:借入馆"))
        assertThrows(BadRequestException::class.java) { service.report(report()) }
    }
    @Test fun `earliest per item recall makes inventory overdue even with later agreement and later recall`()=fixture {e ->
        val loan=LoanDao.new("loan") {orgId="museum";collectionIds="one,two";borrower="借入馆";outExecuted=true;outExecutedTime=at.minusDays(1);returnDate=LocalDateTime.now().toLocalDate().plusMonths(1)}
        full(e)
        fun recall(id:String,days:Long) {
            val value=LoanMonitoringVo(EnumLoanMonitoringKind.RECALL,at,setOf("one"),"keeper","借方人员","提前归还","report",recallReturnBy=java.time.LocalDate.now().plusDays(days))
            LoanComplianceRecordDao.new(id) {orgId="museum";loanId=loan.id.value;kind="MONITORING";content=JsonUtils.toJson(value)!!}
            archives.seal("museum",CollectionConst.BIZ_LOAN,loan.id.value,"MONITORING_$id",value,"report")
        }
        recall("today",0)
        assertTrue(service.get(e.id.value).overdueCollectionIds.isEmpty())
        recall("recall1",-1);recall("recall2",10)
        assertEquals(setOf("one"),service.get(e.id.value).overdueCollectionIds)
        assertTrue(service.coverage(2026).unhandledIssueIds.contains("one"))
        assertFalse(service.coverage(2026).unhandledIssueIds.contains("two"))
        CollectionPermanentArchiveDao.all().single {it.phase=="MONITORING_recall1"}.snapshot="{}"
        assertThrows(BadRequestException::class.java) {service.get(e.id.value)}
    }
    @Test fun `a new overdue source is detected after batch completion and requires subsequent handling`() = fixture { e ->
        val loan=LoanDao.new("loan") { orgId="museum";collectionIds="one";borrower="借入馆";outExecuted=true;outExecutedTime=at.minusDays(1);loanDate=at.minusDays(1).toLocalDate();returnDate=LocalDateTime.now().toLocalDate().plusDays(1) }
        full(e)
        assertFalse(e.baseline!!.items.single { it.collectionId=="one" }.overdue)
        loan.returnDate=at.plusDays(1).toLocalDate()
        assertTrue(service.get(e.id.value).overdueCollectionIds.contains("one"))
        assertTrue(service.coverage(2026).unhandledIssueIds.contains("one"))
        service.action(e.id.value,action("one").copy(recordedAt=at.plusDays(3),followUpAt=at.plusDays(5),major=false))
        assertTrue(service.coverage(2026).unhandledIssueIds.isEmpty())
        assertThrows(BadRequestException::class.java) { service.report(report().copy(signedAt=at.plusDays(2))) }
        assertTrue(service.coverage(2026).pendingIssueIds.contains("one"))
    }
    @Test fun `safety restriction remains until every batch issue has signed resolution`() = fixture { e ->
        service.begin(e.id.value,plan())
        val damaged=check("one").copy(damaged=true,findings="发现病害",protectiveMeasures="防护暂存")
        service.check(e.id.value,damaged)
        val other=InventoryBatchDao.new("second") { orgId="museum";batchName="专项复核";collectionIds="one" }
        service.begin(other.id.value,plan().copy(method=EnumInventoryMethod.SPECIAL));service.check(other.id.value,damaged)
        val resolved=action("one").copy(resolved=true,followUpAt=null,safeForUse=true,keeperUserId="keeper",leaderUserId="leader",resolutionApprovedAt=at.plusDays(1),signedResolutionFileIds="report")
        assertThrows(BadRequestException::class.java) { service.action(e.id.value,resolved.copy(signedResolutionFileIds=null)) }
        val firstCase=closedIncident(e);val secondCase=closedIncident(other)
        service.action(e.id.value,resolved.copy(incidentId=firstCase))
        assertTrue(CollectionDao["one"].inventoryHold)
        assertThrows(BadRequestException::class.java) { service.action(other.id.value,resolved.copy(incidentId=firstCase)) }
        service.action(other.id.value,resolved.copy(incidentId=secondCase))
        assertFalse(CollectionDao["one"].inventoryHold)
    }
    @Test fun `restoring object cannot be counted as still verified in original warehouse`() = fixture { e ->
        CollectionDao["two"].lifeStatus=EnumCollectionStatus.RESTORING
        service.begin(e.id.value,plan())
        assertEquals("UNKNOWN",e.baseline!!.items.single { it.collectionId=="two" }.locationKey)
        assertTrue(service.coverage(2026).unclassifiedIds.contains("two"))
    }
    @Test fun `late historical annual receipt preserves late status rather than pretending submission on time`() = fixture { _ ->
        val value=report().copy(year=2025)
        val id=service.report(value)
        val snapshot=archives.list(InventoryComplianceService.ANNUAL,id).single()["snapshot"] as String
        assertTrue(snapshot.contains("\"late\":true"))
        assertEquals(value,JsonUtils.fromJson(InventoryAnnualReportDao[id].content,InventoryAnnualReportVo::class.java))
    }
    @Test fun `json preserves explicit false and original millisecond timestamps`() {
        val value=check("one").copy(checkedAt=at.withNano(123000000),photoMatches=false,photoFileIds=null,findings="NO",protectiveMeasures="留存待核")
        val restored=JsonUtils.fromJson(JsonUtils.toJson(value)!!,InventoryItemCheckVo::class.java)!!
        assertEquals(value,restored); assertFalse(restored.photoMatches!!); assertEquals("NO",restored.findings)
    }
    @Test fun `inventory resolution needs same source case and prior matching personal confirmations`() = fixture { e ->
        service.begin(e.id.value,plan());service.check(e.id.value,check("one").copy(damaged=true,findings="实际损伤",protectiveMeasures="隔离防护"))
        assertThrows(BadRequestException::class.java){service.action(e.id.value,resolved(null))}
        val case=incident(e)
        assertThrows(BadRequestException::class.java){service.action(e.id.value,resolved(case))}
        IncidentSourceTestSupport(incidents,at,"report").close(case)
        assertThrows(BadRequestException::class.java){service.action(e.id.value,resolved(case).copy(recordedAt=at.plusMinutes(30),resolutionApprovedAt=at.plusMinutes(40)))}
        assertTrue(CollectionDao["one"].inventoryHold)
        assertTrue(e.issueActions!!.items.isEmpty())
        service.action(e.id.value,resolved(case).copy(recordedAt=at.plusMinutes(30)))
        assertFalse(CollectionDao["one"].inventoryHold)
        assertTrue(archives.list(InventoryComplianceService.BIZ,e.id.value).any { it["phase"].toString().startsWith("INCIDENT_CLEARANCE_") && it["snapshot"].toString().contains(case) })
        val facts=incidents.detail(case)["facts"] as IncidentFactsSnapshot
        incidents.amend(case,facts.facts.copy(description="发现新的实际问题",replacementReason="追加核实",replacementFileIds="report"),incidents.detail(case)["factsId"] as String)
        assertTrue(service.coverage(2026).pendingIssueIds.contains("one"))
        assertThrows(BadRequestException::class.java){service.action(e.id.value,resolved(case).copy(recordedAt=at.plusDays(2),resolutionApprovedAt=at.plusDays(2)))}
    }
    @Test fun `major inventory issue cannot rely on a case omitting competent authority handling`() = fixture { e ->
        service.begin(e.id.value,plan());service.check(e.id.value,check("one").copy(objectMatches=false,findings="待核实物证",protectiveMeasures="禁止再用"))
        val case=incident(e,major=false);IncidentSourceTestSupport(incidents,at,"report").close(case)
        assertThrows(BadRequestException::class.java){service.action(e.id.value,resolved(case))}
        assertTrue(CollectionDao["one"].inventoryHold)
        service.action(e.id.value,resolved(case).copy(major=false,significanceReason="实际调查确认并非重大情形"))
        assertFalse(CollectionDao["one"].inventoryHold)
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings=["sample-explicit","sample-warehouse","special-warehouse","special-implicit","special-explicit-and-warehouse"])
    fun `ordinary keeper departure cannot replace full responsibility with sampling or current location`(case:String)=fixture { e ->
        e.collectionIds=if("explicit" in case) "one,two" else null
        e.warehouseId=if("warehouse" in case) "store" else null
        val p=plan(EnumInventoryPurpose.KEEPER_DEPARTURE).copy(method=if(case.startsWith("sample"))EnumInventoryMethod.SAMPLE else EnumInventoryMethod.SPECIAL,leavingUserId="old",successorUserId="new",leavingAt=at.plusDays(5),responsibilityFileIds="scope")
        assertThrows(BadRequestException::class.java){service.begin(e.id.value,p)}
        assertNull(e.plan);assertNull(e.baseline);assertEquals(0L,CollectionPermanentArchiveDao.count())
    }

    /** 模拟升级前已真实签署的批次原件，保留其核验、签署和归档文件。 */
    private fun legacyScope(e:InventoryBatchDao, method:EnumInventoryMethod, selection:EnumInventoryScopeSelection?=null, warehouse:String?=null) {
        e.plan=e.plan!!.copy(method=method)
        e.baseline=e.baseline!!.copy(scopeSelection=selection)
        e.warehouseId=warehouse
        CollectionPermanentArchiveDao.all().filter{it.bizId==e.id.value && it.phase in setOf("STARTED","COMPLETED")}.forEach { row ->
            val value=JsonUtils.mapper.readTree(row.snapshot) as tools.jackson.databind.node.ObjectNode
            value.set("plan",JsonUtils.mapper.readTree(JsonUtils.toJson(e.plan)!!))
            value.set("baseline",JsonUtils.mapper.readTree(JsonUtils.toJson(e.baseline)!!))
            row.snapshot=value.toString();row.digest=CollectionCareEvidence.digest(row.snapshot)
        }
        TransactionManager.current().entityCache.flush();TransactionManager.current().entityCache.clear()
    }
    private fun keeperPlan()=plan(EnumInventoryPurpose.KEEPER_DEPARTURE).copy(leavingUserId="old",successorUserId="new",leavingAt=at.plusDays(5),responsibilityFileIds="scope")
    private fun keeperCompletion()=completion().copy(actualHandoverAt=at.plusDays(1),handoverFileIds="handover")

    @Test fun `new full and explicit keeper scopes are frozen including objects outside the warehouse`()=fixture { e ->
        e.collectionIds="one,two"
        service.begin(e.id.value,keeperPlan().copy(method=EnumInventoryMethod.SPECIAL))
        assertEquals(EnumInventoryScopeSelection.EXPLICIT_COLLECTIONS,e.baseline!!.scopeSelection)
        assertEquals(setOf("WAREHOUSE:store","WAREHOUSE:hall"),e.baseline!!.items.map{it.locationKey}.toSet())
        service.check(e.id.value,check("one"));service.check(e.id.value,check("two"));service.complete(e.id.value,keeperCompletion())
        assertTrue(service.get(e.id.value).departureScopeErrors.isEmpty())
        val next=InventoryBatchDao.new("full-keeper"){orgId="museum";batchName="完整全馆离任清点"}
        service.begin(next.id.value,keeperPlan())
        assertEquals(EnumInventoryScopeSelection.FULL_MUSEUM,next.baseline!!.scopeSelection)
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings=["sample","legacy-location","frozen-location"])
    fun `insufficient archived departure scope is visible without discarding actual checks or historical signatures`(case:String)=fixture { e ->
        service.begin(e.id.value,keeperPlan());service.check(e.id.value,check("one"));service.check(e.id.value,check("two"));service.complete(e.id.value,keeperCompletion())
        legacyScope(e,if(case=="sample")EnumInventoryMethod.SAMPLE else EnumInventoryMethod.SPECIAL,if(case=="frozen-location")EnumInventoryScopeSelection.CURRENT_LOCATION else null,if(case=="legacy-location")"store" else null)
        val value=service.get(e.id.value)
        assertTrue(value.evidenceErrors.isEmpty());assertNotNull(value.completion);assertEquals(2,value.checks!!.items.size)
        assertEquals(EnumInventoryDepartureStatus.SCOPE_REVIEW_REQUIRED,value.departureStatus);assertTrue(value.departureScopeErrors.isNotEmpty())
        assertThrows(BadRequestException::class.java){service.complete(e.id.value,keeperCompletion())}
        val coverage=service.coverage(2026)
        assertEquals(setOf("batch"),coverage.departureScopeReviewBatchIds)
        assertEquals(setOf("one","two"),coverage.checkedCollectionIds)
        assertTrue(coverage.unverifiedBatchIds.isEmpty());assertTrue(coverage.ready())
        val id=service.report(report())
        assertEquals(setOf("batch"),JsonUtils.fromJson(InventoryAnnualReportDao[id].coverage,InventoryCoverageVo::class.java)!!.departureScopeReviewBatchIds)
    }
    @Test fun `legacy explicit keeper scope with intact responsibility originals remains readable`()=fixture { e ->
        e.collectionIds="one"
        service.begin(e.id.value,keeperPlan().copy(method=EnumInventoryMethod.SPECIAL));service.check(e.id.value,check("one"));service.complete(e.id.value,keeperCompletion())
        legacyScope(e,EnumInventoryMethod.SPECIAL)
        assertTrue(service.get(e.id.value).evidenceErrors.isEmpty());assertTrue(service.get(e.id.value).departureScopeErrors.isEmpty())
    }
    @Test fun `changing frozen selection mode is an archive mismatch rather than a scope clarification`()=fixture { e ->
        service.begin(e.id.value,keeperPlan())
        e.baseline=e.baseline!!.copy(scopeSelection=EnumInventoryScopeSelection.CURRENT_LOCATION)
        assertTrue(service.get(e.id.value).evidenceErrors.isNotEmpty())
        assertThrows(BadRequestException::class.java){service.check(e.id.value,check("one"))}
    }

    private fun closedInventoryAnomaly(e: InventoryBatchDao): String {
        service.begin(e.id.value, plan())
        service.check(e.id.value, check("one").copy(damaged = true, findings = "合成清点损伤", protectiveMeasures = "合成停止使用并保护"))
        service.check(e.id.value, check("two"))
        service.action(e.id.value, action("one"))
        service.complete(e.id.value, completion())
        val id = closedIncident(e)
        service.action(e.id.value, resolved(id).copy(recordedAt = at.plusDays(3), resolutionApprovedAt = at.plusDays(3)))
        assertTrue(service.get(e.id.value).evidenceErrors.isEmpty())
        return id
    }

    @ParameterizedTest
    @ValueSource(strings = ["missing-factsId", "missing-handlingId", "missing-confirmationId", "missing-basisRevision", "missing-confirmedAt",
        "changed-factsId", "changed-handlingId", "changed-confirmationId", "changed-basisRevision", "changed-confirmedAt",
        "incident-owner", "confirmation-archive", "confirmation-record", "handling-archive", "handling-reference", "facts-source"])
    fun `clearance history verifies frozen incident proof and signed chain`(change: String) = fixture { e ->
        val id = closedInventoryAnomaly(e)
        val clearance = CollectionPermanentArchiveDao.all().single { it.phase.startsWith("INCIDENT_CLEARANCE_") }
        val saved = JsonUtils.mapper.readTree(clearance.snapshot) as tools.jackson.databind.node.ObjectNode
        val proof = saved.get("incidentProof") as tools.jackson.databind.node.ObjectNode
        val handlingId = proof.get("handlingId").stringValue()
        val confirmationId = proof.get("confirmationId").stringValue()
        fun original(recordId: String) = CollectionPermanentArchiveDao.all().single {
            it.bizId == id && it.phase.endsWith("_$recordId")
        }
        when {
            change.startsWith("missing-") -> proof.remove(change.removePrefix("missing-"))
            change.startsWith("changed-") -> proof.put(change.removePrefix("changed-"), "合成错误事故版本")
            change == "incident-owner" -> CollectionIncidentDao[id].orgId = "other"
            change == "confirmation-archive" -> original(confirmationId).delete()
            change == "confirmation-record" -> CollectionIncidentRecordDao[confirmationId].delete()
            change == "handling-archive" -> original(handlingId).delete()
            change == "handling-reference" -> {
                val archiveId = original(handlingId).id.value
                CollectionPermanentArchiveFileDao.all().single { it.archiveId == archiveId }.delete()
            }
            change == "facts-source" -> {
                val facts = CollectionIncidentRecordDao[proof.get("factsId").stringValue()]
                val content = JsonUtils.mapper.readTree(facts.content) as tools.jackson.databind.node.ObjectNode
                (content.get("facts").get("source") as tools.jackson.databind.node.ObjectNode).put("bizId", "合成另一清点批次")
                facts.content = content.toString()
                original(facts.id.value).also { it.snapshot = facts.content; it.digest = CollectionCareEvidence.digest(it.snapshot) }
            }
        }
        if (change.startsWith("missing-") || change.startsWith("changed-")) {
            clearance.snapshot = saved.toString()
            clearance.digest = CollectionCareEvidence.digest(clearance.snapshot)
        }
        TransactionManager.current().entityCache.flush()
        TransactionManager.current().entityCache.clear()
        assertTrue(service.get(e.id.value).evidenceErrors.isNotEmpty(), "实际解除档案或当次事故签署链改变后不能仍通过历史核验：$change")
        assertEquals(setOf(e.id.value), service.coverage(2026).unverifiedBatchIds)
        assertThrows(BadRequestException::class.java) { service.complete(e.id.value, completion()) }
        assertThrows(BadRequestException::class.java) { service.report(report()) }
    }

    @Test fun `clearance history retains its signed version when later incident facts reopen the case`() = fixture { e ->
        val id = closedInventoryAnomaly(e)
        val before = CollectionPermanentArchiveDao.all().single { it.phase.startsWith("INCIDENT_CLEARANCE_") }.snapshot
        val previousFactsId = incidents.detail(id)["factsId"] as String
        incidents.amend(id, IncidentFactsVo("合成后续新事实", at.plusDays(4), location = "合成现保管位置", reporterUserId = "operator",
            description = "合成后续发现追加，保留原处理与签署", immediateMeasures = "合成另行限制并核查",
            items = listOf(IncidentItemVo("one", true, EnumIncidentSecurity.NONE, true, "合成后续裂隙")),
            source = IncidentSourceVo(InventoryComplianceService.BIZ, e.id.value), replacementReason = "合成新增事故事实", replacementFileIds = "report"), previousFactsId)
        assertFalse(CollectionIncidentDao[id].closed)
        assertTrue(service.get(e.id.value).evidenceErrors.isEmpty())
        assertEquals(before, CollectionPermanentArchiveDao.all().single { it.phase.startsWith("INCIDENT_CLEARANCE_") }.snapshot)
        assertTrue("one" in service.coverage(2026).pendingIssueIds)
    }

}
