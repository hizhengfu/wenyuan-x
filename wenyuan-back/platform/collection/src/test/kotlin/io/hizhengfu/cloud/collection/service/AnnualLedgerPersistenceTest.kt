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
import io.hizhengfu.cloud.sys.system.dao.*
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
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

class AnnualLedgerPersistenceTest {
    private val at=LocalDateTime.of(2025,6,1,12,0)
    private val signedAt=LocalDateTime.of(2026,1,2,12,0)
    private val scope=CollectionScopeResolver("multi","")
    private val binder=mock(BusinessAttachmentBinder::class.java).also { `when`(it.resolveSingle(anyString())).thenAnswer { c -> c.getArgument<String>(0) } }
    private val archives=CollectionPermanentArchiveService(binder,scope,readAccess=unprotectedArchiveReadAccessProvider())
    private val users=mock(CollectionResponsibleUserService::class.java)
    private val statistics=CollectionLedgerStatisticsService(scope)
    private val reports=AnnualReportEvidenceService(scope,statistics,users,archives)
    private val collections=CollectionService(scope,binder,CollectionRegistrationNumberService(),CollectionOperationLockService(),mock(CollectionCatalogService::class.java),mock(CollectionChangeService::class.java),CollectionRetainedLedgerService(scope,archives),mock(CollectionDisclosureService::class.java))
    @BeforeEach fun setup() {
        val context=StaticApplicationContext();context.beanFactory.registerSingleton("cacheManager",ConcurrentMapCacheManager());context.refresh();SpringContextHolder().setApplicationContext(context)
        login()
    }
    @AfterEach fun clear()=SecurityContextHolder.clearContext()
    private fun login(org: String="museum") {
        val user=SecurityUser(id="statistician",name="统计经办",username="statistician",password="",groupId=org,groupIds=setOf(org),postIds=emptySet(),dataType=0,roles=emptySet(),authorities=listOf(SimpleGrantedAuthority(CollectionConst.STAT)),enabled=true)
        SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(user,null,user.authorities)
    }
    private fun fixture(block:(Database)->Unit) {
        val file=java.nio.file.Files.createTempFile("annual-ledger-",".sqlite")
        val db=Database.connect("jdbc:sqlite:$file",driver="org.sqlite.JDBC")
        try {
            transaction(db) {
                originalReaderSchema();originalReader("statistician",CollectionConst.STAT)
                GroupDao.new("target") { name="接收馆";type='1';parentId="0";parentIds="0," }
                SchemaUtils.create(StorageMovements,Requisitions,Loans,Collections,CollectionOrgs,CollectionRetainedLedgers,CollectionLedgerStatEvents,CollectionLedgerLogs,CollectionMedias,CollectionAttachments,CollectionCategories,Warehouses,CollectionOperationLocks,AnnualReportEvidenceRecords,AnnualReportArchives,CollectionPermanentArchives,CollectionPermanentArchiveFiles,Files)
                CollectionOrgDao.new("museum"){name="本馆";code="M"};CollectionOrgDao.new("target"){name="接收馆";code="T"}
                listOf("sign","reconcile","replacement","submission","filing").forEach { id -> FileDao.new(id){name="$id.pdf";path="/files/$id.pdf";hash=id;size=100;ext="pdf"} }
                val r=item("one");CollectionLedgerStatRecorder.record(r,EnumLedgerStatEvent.REGISTER,"initial",at)
            }
            block(db)
        } finally { java.nio.file.Files.deleteIfExists(file) }
    }
    private fun item(id:String, registered:Boolean=true)=CollectionDao.new(id){
        orgId="museum";totalRegNo=id;name="物品$id";nature=EnumCollectionNature.RELIC;level=EnumRelicLevel.SECOND;categoryId="ceramic";source=EnumSource.DONATE;quantity=2;unit="件"
        if(registered){registeredAt=at;catalogStatus=EnumCatalogStatus.APPROVED;lifeStatus=EnumCollectionStatus.IN_STORE}else{lifeStatus=EnumCollectionStatus.PENDING}
    }
    @Test fun `arbitrary period separates temporary movements and actual quantity adjustments`()=fixture { db -> transaction(db) {
        val row=CollectionDao["one"]
        row.quantity=1;row.unit="套";row.actualQuantity=5;row.actualQuantityUnit="件"
        CollectionLedgerStatRecorder.record(row,EnumLedgerStatEvent.CHANGE,"change",at.plusMonths(1))
        StorageMovementDao.new {orgId="museum";collectionId="one";type=EnumMoveType.OUT;reason=EnumMoveReason.RESEARCH;operatedAt=at.plusDays(2)}
        val v=statistics.period(at.toLocalDate().plusDays(1),at.toLocalDate().plusMonths(1))
        assertEquals(1,v.openingTotal);assertEquals(1,v.closingTotal);assertEquals(0,v.newTotal);assertEquals(0,v.deregTotal)
        assertEquals(1,v.quantityBalances.single {it.unit=="套"}.closing)
        assertEquals(5,v.actualQuantityBalances.single {it.unit=="件"}.closing)
        assertEquals(1,v.unknownActualQuantityCount)
        assertEquals(v,JsonUtils.fromJson(JsonUtils.toJson(v)!!,PeriodLedgerPreviewVo::class.java))
    } }
    @Test fun `period signature preserves snapshot and requires reconciliation for unknown history`()=fixture { db -> transaction(db) {
        val service=CollectionPeriodReportService(scope,statistics,archives,users)
        val start=at.toLocalDate().withDayOfMonth(1);val end=start.plusMonths(1).minusDays(1)
        val report=service.preview(start,end)
        val value=CollectionPeriodReportSignVo("museum",start,end,report.digest,"statistician","head","leader",signedAt,"sign")
        assertThrows(BadRequestException::class.java) {service.signed(value)}
        val complete=value.copy(reconciliationNote="按纸质总账核实，历史实际件数仍待核实",reconciliationFileIds="reconcile")
        val id=service.signed(complete)
        CollectionDao["one"].name="后续订正名称"
        val saved=service.history(start,end).single()
        assertEquals(id,saved.id);assertEquals(report,saved.calculated)
        assertEquals(id,service.signed(complete))
        assertThrows(BadRequestException::class.java) {service.signed(complete.copy(signedAt=signedAt.plusDays(1)))}
        RolePermissions.deleteAll()
        assertThrows(BadRequestException::class.java) {service.history(start,end)}
    } }

    @Test fun `period usage groups frozen category units and scope without recounting returns`()=fixture { db -> transaction(db) {
        val c=CollectionDao["one"];c.quantity=1;c.unit="套";c.actualQuantity=2;c.actualQuantityUnit="件"
        val second=item("two");second.categoryId="paper";second.quantity=3;second.actualQuantity=3;second.actualQuantityUnit="册";second.unit="册"
        RequisitionDao.new("request") {orgId="museum";collectionIds="one,two";outExecuted=true;outExecutedTime=at.plusDays(1)}
        LoanDao.new("loan") {orgId="museum";collectionIds="one";outExecuted=true;outExecutedTime=at.plusDays(2)}
        fun movement(id:String,biz:String,collection:CollectionDao,date:LocalDateTime) {
            val row=StorageMovementDao.new(id) {orgId="museum";collectionId=collection.id.value;totalRegNoAtMovement=collection.totalRegNo;collectionNameAtMovement=collection.name;type=EnumMoveType.OUT;reason=EnumMoveReason.RESEARCH;refBizId=biz;operatedAt=date}
            CollectionMovementFacts.capture(row,archives)
        }
        movement("m1","request",c,at.plusDays(1));movement("m2","request",second,at.plusDays(1));movement("m3","loan",c,at.plusDays(2))
        StorageMovementDao.new {orgId="museum";collectionId="one";type=EnumMoveType.IN;reason=EnumMoveReason.RESEARCH;refBizId="return-1";operatedAt=at.plusDays(3)}
        StorageMovementDao.new {orgId="museum";collectionId="two";type=EnumMoveType.IN;reason=EnumMoveReason.RESEARCH;refBizId="return-2";operatedAt=at.plusDays(4)}
        c.name="后续名称";c.quantity=99;c.actualQuantity=99;c.categoryId="later"
        val service=CollectionPeriodReportService(scope,statistics,archives,users)
        val report=service.preview(at.toLocalDate(),at.toLocalDate().plusDays(5))
        assertEquals(2,report.useCount);assertEquals(3,report.collectionOccurrences);assertEquals(3,report.usage.size)
        assertTrue(report.usageIssues.isEmpty());assertEquals(setOf("INTERNAL","EXTERNAL"),report.usage.map {it.scope}.toSet())
        val row=report.usage.single {it.scope=="EXTERNAL"}
        assertEquals("ceramic",row.categoryId);assertEquals(mapOf("套" to 1L),row.ledgerQuantities);assertEquals(mapOf("件" to 2L),row.actualQuantities)
        assertEquals(mapOf("册" to 3L),report.usage.single {it.categoryId=="paper"}.ledgerQuantities)
        assertEquals(0,report.ledger.deregTotal);assertEquals(report,JsonUtils.fromJson(JsonUtils.toJson(report)!!,CollectionPeriodReportVo::class.java))
        StorageMovementDao["m1"].reason=EnumMoveReason.EXHIBIT
        assertThrows(BadRequestException::class.java) {service.preview(at.toLocalDate(),at.toLocalDate().plusDays(5))}
    } }

    private fun figures()=AnnualDeclaredFiguresVo(0,1,0,0,0,1,listOf(AnnualDeclaredUnitVo("件",0,2,0,0,0,0,2)),"新增捐赠1条，共2件；其他信息未变更")
    private fun signed()=AnnualReportSignedVo(2025,"statistician","head","leader",signedAt,"sign",figures())
    private fun submission()=AnnualReportSubmissionVo("主管文物局","报2025-1",signedAt.plusDays(1),"submission")
    private fun filing()=AnnualReportFilingVo("主管文物局","备2025-1",signedAt.plusDays(2),"filing")

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource("signed,revoked", "signed,disabled", "submission,revoked", "submission,disabled", "submission,moved", "filing,revoked", "filing,disabled", "filing,moved")
    fun `annual writes reject stale eligibility`(operation: String, change: String)=fixture { db -> transaction(db) {
        val id=reports.signed(signed(), "museum");reports.submission(id,submission());reports.filing(id,filing())
        when(change) {
            "revoked" -> RolePermissions.deleteAll()
            "disabled" -> UserDao["statistician"].enabled=false
            "moved" -> UserDao["statistician"].groupId="target"
        }
        fun originals()=CollectionPermanentArchiveDao.all().map { Triple(it.id.value,it.snapshot,it.digest) }.sortedBy { it.first }
        val before=originals();val count=AnnualReportEvidenceDao.count()
        assertThrows(BadRequestException::class.java) {
            when(operation) {
                "signed" -> reports.signed(signed(), "museum")
                "submission" -> reports.submission(id,submission())
                else -> reports.filing(id,filing())
            }
        }
        assertEquals(before,originals());assertEquals(count,AnnualReportEvidenceDao.count())
    } }
    @Test fun `current revocation rejects annual signed originals`()=fixture { db -> transaction(db) {
        reports.signed(signed(), "museum");RolePermissions.deleteAll()
        assertThrows(BadRequestException::class.java){reports.detail(2025)}
    } }
    @Test fun `current revocation rejects legacy annual originals`()=fixture { db -> transaction(db) {
        RolePermissions.deleteAll()
        assertThrows(BadRequestException::class.java){AnnualReportArchiveService(scope,binder).get(2025)}
    } }
    @Test fun `current revocation rejects annual ledger preview`()=fixture { db -> transaction(db) {
        RolePermissions.deleteAll()
        assertThrows(BadRequestException::class.java){statistics.preview(2025)}
    } }

    @Test fun `annual default museum follows current assignment and explicit old museum is denied`()=fixture { db -> transaction(db) {
        reports.signed(signed(), "museum");UserDao["statistician"].groupId="target"
        assertEquals("target",reports.detail(2025)["orgId"])
        assertTrue((reports.detail(2025)["records"] as List<*>).isEmpty())
        assertNull(AnnualReportArchiveService(scope,binder).get(2025))
        assertEquals("target",statistics.preview(2025).orgId)
        assertThrows(BadRequestException::class.java){statistics.preview(2025,"museum")}
        actorRole("statistician","CURRENT_CROSS",setOf(CollectionConst.CROSS_ORG))
        assertEquals("museum",statistics.preview(2025,"museum").orgId)
        val single=CollectionScopeResolver("single","target")
        assertThrows(BadRequestException::class.java){CollectionLedgerStatisticsService(single).preview(2025,"museum")}
    } }
    @Test fun `disabled annual reader is denied while historical museum needs explicit current cross grant`()=fixture { db -> transaction(db) {
        reports.signed(signed(), "museum");UserDao["statistician"].enabled=false
        assertThrows(BadRequestException::class.java){reports.detail(2025)}
        assertThrows(BadRequestException::class.java){AnnualReportArchiveService(scope,binder).get(2025)}
        assertThrows(BadRequestException::class.java){statistics.preview(2025)}
        UserDao["statistician"].enabled=true;CollectionOrgDao["museum"].orgStatus=EnumOrgStatus.DISABLED
        assertThrows(BadRequestException::class.java){reports.detail(2025)}
        actorRole("statistician","HISTORY",setOf(CollectionConst.CROSS_ORG))
        assertEquals("museum",reports.detail(2025)["orgId"]);assertEquals(1,statistics.preview(2025).closingTotal)
    } }
    @Test fun `annual read rejects foreign frozen calculation and mismatched ledger event identity`()=fixture { db -> transaction(db) {
        val id=reports.signed(signed(), "museum");val row=AnnualReportEvidenceDao[id]
        row.calculated=row.calculated.copy(orgId="target")
        assertThrows(BadRequestException::class.java){reports.detail(2025)}
        val event=CollectionLedgerStatEventDao.all().single();event.item=event.item.copy(collectionId="foreign")
        assertThrows(BadRequestException::class.java){statistics.preview(2025)}
    } }
    @Test fun `annual current authorized read preserves signed snapshot after master transfer`()=fixture { db -> transaction(db) {
        val id=reports.signed(signed(), "museum");val frozen=AnnualReportEvidenceDao[id].calculated
        CollectionDao["one"].orgId="target";CollectionDao["one"].name="后来名称"
        assertEquals(frozen,(reports.detail(2025)["records"] as List<Map<String,Any?>>).single()["calculated"])
        val archive=CollectionPermanentArchiveFileDao.all().first();archive.orgId="target"
        assertThrows(BadRequestException::class.java){reports.detail(2025)}
    } }

    @Test fun `historical classification and quantities survive later master changes`()=fixture { db ->
        transaction(db){val row=CollectionDao["one"];row.level=EnumRelicLevel.FIRST;row.quantity=5;CollectionLedgerStatRecorder.record(row,EnumLedgerStatEvent.CHANGE,"change",signedAt)}
        transaction(db){
            val old=statistics.preview(2025);assertEquals(mapOf("SECOND" to 1),old.closingByLevel);assertEquals(2L,old.quantityBalances.single().closing)
            val current=statistics.preview(2026);assertEquals(1,current.openingTotal);assertEquals(5L,current.quantityBalances.single().closing);assertEquals(3L,current.quantityBalances.single().adjustment)
        }
    }
    @Test fun `transfer moves each museums historical balance at handover without reusing original registration date`()=fixture { db ->
        transaction(db){val r=CollectionDao["one"];CollectionLedgerStatRecorder.record(r,EnumLedgerStatEvent.TRANSFER_OUT,"transfer",signedAt);r.orgId="target";CollectionLedgerStatRecorder.record(r,EnumLedgerStatEvent.TRANSFER_IN,"transfer",signedAt)}
        transaction(db){
            assertEquals(1,statistics.preview(2025).closingTotal)
            val source=statistics.preview(2026);assertEquals(1,source.openingTotal);assertEquals(1,source.transferOutTotal);assertEquals(0,source.closingTotal)
            UserDao["statistician"].groupId="target";login("target");assertEquals(0,statistics.preview(2025).closingTotal)
            val target=statistics.preview(2026);assertEquals(0,target.openingTotal);assertEquals(1,target.transferInTotal);assertEquals(0,target.newTotal);assertEquals(1,target.closingTotal)
        }
    }
    @Test fun `same date handover does not precede earlier recorded registration due to date-only midnight precision`()=fixture { db -> transaction(db){
        val r=CollectionDao["one"];CollectionLedgerStatRecorder.record(r,EnumLedgerStatEvent.TRANSFER_OUT,"same-day",at.toLocalDate().atStartOfDay())
        r.orgId="target";CollectionLedgerStatRecorder.record(r,EnumLedgerStatEvent.TRANSFER_IN,"same-day",at.toLocalDate().atStartOfDay())
        val result=statistics.preview(2025);assertTrue(result.issues.isEmpty());assertEquals(1,result.newTotal);assertEquals(1,result.transferOutTotal);assertEquals(0,result.closingTotal)
    } }
    @Test fun `units remain separate and changes reconcile both old and new units`()=fixture { db -> transaction(db){
        val r=CollectionDao["one"];r.quantity=1;r.unit="套";CollectionLedgerStatRecorder.record(r,EnumLedgerStatEvent.CHANGE,"unit-change",at.plusDays(1))
        val result=statistics.preview(2025);val piece=result.quantityBalances.single { it.unit=="件" };val set=result.quantityBalances.single { it.unit=="套" }
        assertEquals(-2L,piece.adjustment);assertEquals(0L,piece.closing);assertEquals(1L,set.adjustment);assertEquals(1L,set.closing);assertEquals(1,result.closingTotal)
    } }
    @Test fun `current draft legacy and exit records are separated in summary and export`()=fixture { db -> transaction(db){
        item("draft",false);val old=item("legacy");old.registeredAt=null
        val retired=item("retired");retired.lifeStatus=EnumCollectionStatus.DEREGISTERED
        val summary=collections.summary();assertEquals(1,summary.total);assertEquals(1,summary.pendingRegistrationTotal);assertEquals(1,summary.unverifiedRegistrationTotal);assertEquals(1,summary.retiredTotal)
        val overview=CollectionStatBuilder.build(CollectionDao.all().map { it.toModel() },at.toLocalDate())
        assertEquals(1,overview.registeredThisYear);assertEquals(1,overview.registeredThisMonth)
        val export=collections.exportSurvey(CollectionQueryVo());assertEquals(listOf("one"),export.map { it.totalRegNo })
        assertEquals(3,(collections.surveyStatus(CollectionQueryVo())["excluded"] as List<*>).size)
        val history=statistics.preview(2025);assertEquals(setOf("legacy","retired"),history.issues.map { it.collectionId }.toSet());assertEquals(1,history.closingTotal)
    } }
    @Test fun `real deregistration hook closes formal ledger without deleting earlier year`()=fixture { db -> transaction(db){
        collections.executeDeregister("one","依法退出","exit","省批文")
        assertEquals(1,statistics.preview(2025).closingTotal)
        val current=statistics.preview(2026);assertEquals(1,current.deregTotal);assertEquals(0,current.closingTotal)
        assertEquals(2L,CollectionLedgerStatEventDao.count())
    } }
    @Test fun `signed report submission and filing are separate immutable actual stages`()=fixture { db ->
        var id=""
        transaction(db){id=reports.signed(signed(), "museum");assertEquals(id,reports.signed(signed(), "museum"));assertNull(AnnualReportEvidenceDao[id].submission);assertNull(AnnualReportEvidenceDao[id].filing)}
        transaction(db){reports.submission(id,submission());reports.submission(id,submission());assertNull(AnnualReportEvidenceDao[id].filing);reports.filing(id,filing());reports.filing(id,filing())
            assertEquals(signedAt.plusDays(1),AnnualReportEvidenceDao[id].submission!!.submittedAt)
            assertThrows(BadRequestException::class.java){reports.submission(id,submission().copy(reportNo="替换"))}
            assertThrows(BadRequestException::class.java){reports.filing(id,filing().copy(filingNo="替换"))}
            assertThrows(BadRequestException::class.java){CollectionArchiveFileRetentionGuard().requireDeletable("filing")}
        }
        transaction(db){assertEquals(3,archives.list(AnnualReportEvidenceService.BIZ,id).size)}
    }
    @Test fun `unverified historical numbers require signed paper reconciliation and preserve declared totals`()=fixture { db -> transaction(db){
        item("legacy")
        assertThrows(BadRequestException::class.java){reports.signed(signed(), "museum")}
        val declared=figures().copy(openingTotal=1,closingTotal=2,quantities=listOf(AnnualDeclaredUnitVo("件",2,2,0,0,0,0,4)))
        val id=reports.signed(signed().copy(figures=declared,reconciliationNote="逐件核对原纸质总账补齐旧登记",reconciliationFileIds="reconcile"), "museum")
        assertEquals(2,AnnualReportEvidenceDao[id].signed.figures.closingTotal);assertEquals(1,AnnualReportEvidenceDao[id].calculated.closingTotal)
        assertEquals(1,AnnualReportEvidenceDao[id].calculated.issues.size)
    } }
    @Test fun `replacement is versioned with fresh signed originals and no copied submission`()=fixture { db -> transaction(db){
        val old=reports.signed(signed(), "museum");reports.submission(old,submission())
        val replacement=signed().copy(signedAt=signedAt.plusDays(3),figures=figures().copy(changesDescription="补充说明"))
        assertThrows(BadRequestException::class.java){reports.signed(replacement, "museum")}
        val id=reports.signed(replacement.copy(replacementReason="补充遗漏说明",replacementFileIds="replacement"), "museum")
        assertEquals(2,AnnualReportEvidenceDao[id].version);assertNull(AnnualReportEvidenceDao[id].submission);assertNotNull(AnnualReportEvidenceDao[old].submission)
        assertEquals(signed(),AnnualReportEvidenceDao[old].signed)
    } }
    @Test fun `future timing imbalance wrong authorities and museum scope cannot be reported`()=fixture { db -> transaction(db){
        assertThrows(BadRequestException::class.java){reports.signed(signed().copy(year=LocalDateTime.now().year), "museum")}
        assertThrows(BadRequestException::class.java){reports.signed(signed().copy(figures=figures().copy(closingTotal=3)), "museum")}
        val id=reports.signed(signed(), "museum")
        assertThrows(BadRequestException::class.java){reports.filing(id,filing())}
        assertThrows(BadRequestException::class.java){reports.submission(id,submission().copy(submittedAt=signedAt.minusDays(1)))}
        reports.submission(id,submission());assertThrows(BadRequestException::class.java){reports.filing(id,filing().copy(authorityName="另一个机关"))}
        UserDao["statistician"].groupId="target";login("target");assertThrows(BadRequestException::class.java){reports.filing(id,filing())};assertThrows(BadRequestException::class.java){statistics.preview(2025,"museum")}
    } }
    @Test fun `declared quantities cannot be absent below entry counts or overflow balanced`()=fixture { db -> transaction(db){
        val zero=AnnualDeclaredUnitVo("件",0,0,0,0,0,0,0)
        val overflow=AnnualDeclaredUnitVo("件",Long.MAX_VALUE,Long.MAX_VALUE,2,0,0,0,0)
        listOf(emptyList(),listOf(zero),listOf(overflow)).forEach { quantities ->
            assertThrows(BadRequestException::class.java){reports.signed(signed().copy(figures=figures().copy(quantities=quantities),reconciliationNote="不能用核对说明绕过错误数量",reconciliationFileIds="reconcile"), "museum")}
        }
        assertEquals(0L,AnnualReportEvidenceDao.count())
        assertNotNull(reports.signed(signed(), "museum"))
    } }
    @Test fun `old archive is preserved as legacy and cannot generate reporting time through save`()=fixture { db -> transaction(db){
        AnnualReportArchiveDao.new {orgId="museum";year=2025;statistician="旧制表人";keeperDirector="旧负责人";museumLeader="旧馆长";reportAttachmentIds="sign";reportedAt=signedAt}
        val detail=reports.detail(2025);assertNotNull(detail["legacyArchive"]);assertTrue((detail["records"] as List<*>).isEmpty())
        assertThrows(BadRequestException::class.java){AnnualReportArchiveService(scope,binder).save(AnnualReportArchiveVo(year=2025,reportAttachmentIds="sign"))}
        assertEquals(signedAt,AnnualReportArchiveDao.all().single().reportedAt)
    } }
    @Test fun `legacy annual reads follow current database assignment and enforce current eligibility`()=fixture { db -> transaction(db){
        for(org in listOf("museum","target")) AnnualReportArchiveDao.new {
            orgId=org;year=2025;statistician="旧制表人-$org";keeperDirector="旧负责人-$org";museumLeader="旧馆长-$org"
            reportAttachmentIds="sign";reportedAt=signedAt
        }
        val legacy=AnnualReportArchiveService(scope,binder)
        fun originals()=AnnualReportArchiveDao.all().map { it.toModel() }.sortedBy { it.id }
        val before=originals()
        assertEquals(before.single {it.orgId=="museum"},legacy.get(2025))
        UserDao["statistician"].groupId="target" // Authentication deliberately keeps its old museum snapshot.
        assertEquals(before.single {it.orgId=="target"},legacy.get(2025))
        assertEquals(before.single {it.orgId=="target"},AnnualReportArchiveService(CollectionScopeResolver("single","target"),binder).get(2025))
        RolePermissions.deleteAll()
        assertThrows(BadRequestException::class.java){legacy.get(2025)}
        actorRole("statistician","CURRENT_STAT",setOf(CollectionConst.STAT))
        UserDao["statistician"].enabled=false
        assertThrows(BadRequestException::class.java){legacy.get(2025)}
        UserDao["statistician"].enabled=true;CollectionOrgDao["target"].orgStatus=EnumOrgStatus.DISABLED
        assertThrows(BadRequestException::class.java){legacy.get(2025)}
        actorRole("statistician","CURRENT_HISTORY",setOf(CollectionConst.CROSS_ORG))
        assertEquals(before.single {it.orgId=="target"},legacy.get(2025))
        assertEquals(before,originals());assertEquals(0L,AnnualReportEvidenceDao.count());assertEquals(0L,CollectionPermanentArchiveDao.count())
    } }
    @Test fun `failed original archive rolls back report and exact millisecond dates survive persistence`()=fixture { db ->
        val value=signed().copy(signedAt=signedAt.withNano(123000000),signedReportFileIds="missing")
        assertEquals(value,JsonUtils.fromJson(JsonUtils.toJson(value)!!,AnnualReportSignedVo::class.java))
        assertThrows(BadRequestException::class.java){transaction(db){reports.signed(value, "museum")}}
        transaction(db){assertEquals(0L,AnnualReportEvidenceDao.count())}
    }
}
