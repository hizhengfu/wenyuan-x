package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.rfid.service.RfidWorkstationService
import io.hizhengfu.cloud.collection.rfid.domain.RfidWorkstationSaveVo
import io.hizhengfu.cloud.collection.rfid.dao.RfidWorkstations
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.eq
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionUserResolver
import io.hizhengfu.cloud.museum.device.dao.*
import io.hizhengfu.cloud.common.domain.EnumContentStatus
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.flow.service.FlowInstanceService
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.collection.install.CollectionInstall
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.dao.entityCache
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*
import org.postgresql.ds.PGSimpleDataSource
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.*
import org.springframework.jdbc.datasource.AbstractDataSource
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.support.TransactionTemplate
import io.hizhengfu.cloud.sys.system.dao.RolePermissions
import java.nio.file.Path
import java.sql.Connection
import java.util.Properties
import java.util.UUID
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean

/** 仅由postgresConcurrencyTest运行；独立schema只装测试夹具，不接真实馆藏库。 */
@Tag("postgres-concurrency")
class CollectionLocationConcurrencyPostgresTest {
    companion object {
        private fun <T> asDiaryReader(body: () -> T): T {
            val previous=SecurityContextHolder.getContext()
            val user=SecurityUser(id="k1",name="库管",username="k1",password="",groupId="museum",groupIds=setOf("museum"),postIds=emptySet(),dataType=0,roles=emptySet(),authorities=listOf(CollectionConst.STORAGE,CollectionConst.WAREHOUSE_ENTRY_VERIFY).map(::SimpleGrantedAuthority),enabled=true)
            SecurityContextHolder.setContext(SecurityContextHolder.createEmptyContext().apply {
                authentication=UsernamePasswordAuthenticationToken(user,null,user.authorities)
            })
            try { return body() } finally { SecurityContextHolder.setContext(previous) }
        }
    }
    @Configuration(proxyBeanMethods=false) @EnableCaching @EnableTransactionManagement(proxyTargetClass=true) class Transactions
    private class Runtime(val c: AnnotationConfigApplicationContext,val tx: TransactionTemplate,val source: PGSimpleDataSource,val schema: String) {
        val warehouse=c.getBean(WarehouseService::class.java)
        val collections=c.getBean(CollectionService::class.java)
        val guard=c.getBean(CollectionLocationReferenceGuard::class.java)
        val vouchers=c.getBean(EntryVoucherService::class.java)
        val executor=Executors.newFixedThreadPool(2)
        fun <T> db(body: () -> T): T = tx.execute { body() }
        fun worker(name: String,body: () -> String): Future<Result<String>> = executor.submit<Result<String>> {
            Thread.currentThread().name=name
            asDiaryReader { runCatching(body) }
        }
        fun pauseFirstCheck(entered: CountDownLatch,release: CountDownLatch) {
            val once=AtomicBoolean()
            doAnswer { invocation ->
                invocation.callRealMethod()
                if(Thread.currentThread().name=="first" && once.compareAndSet(false,true)) {
                    entered.countDown();check(release.await(15,TimeUnit.SECONDS)) { "test release timed out" }
                };null
            }.`when`(guard).requireUnreferenced(anySet<String>())
        }
        fun awaitSecondProgress(second: Future<*>, requireLock: Boolean = true, blockerName: String? = null) {
            val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(8)
            while(System.nanoTime()<deadline) {
                if(second.isDone) { if(requireLock)fail<Unit>("Second transaction completed before the first released its location lock: ${second.get()}");return }
                val waiting=source.connection.use { connection ->
                    connection.prepareStatement("""
                        SELECT EXISTS (
                            SELECT 1 FROM pg_stat_activity waiting
                            WHERE waiting.application_name = ? AND waiting.wait_event_type = 'Lock'
                              AND (?::text IS NULL OR EXISTS (
                                  SELECT 1 FROM pg_stat_activity blocking
                                  WHERE blocking.pid = ANY(pg_blocking_pids(waiting.pid))
                                    AND blocking.application_name = ?
                              ))
                        )
                    """.trimIndent()).use { statement ->
                        statement.setString(1,schema+"-second")
                        statement.setString(2,blockerName)
                        statement.setString(3,blockerName?.let { schema+"-"+it })
                        statement.executeQuery().use { rs -> rs.next();rs.getBoolean(1) }
                    }
                }
                if(waiting)return
                Thread.sleep(20)
            }
            fail<Unit>("Second transaction neither finished nor reached an observed PostgreSQL lock wait; expected blocker=$blockerName")
        }
    }
    private fun fixture(isolation: Int = Connection.TRANSACTION_READ_COMMITTED, body: (Runtime) -> Unit) {
        val props=Properties().apply { Path.of(System.getProperty("collection.test.pg.config")).toFile().inputStream().use(::load) }
        val schema="loc_"+UUID.randomUUID().toString().replace("-","")
        val source=PGSimpleDataSource().apply { setURL(props.getProperty("url"));user=props.getProperty("user");password=props.getProperty("password");currentSchema=schema }
        source.connection.use { it.createStatement().use { statement -> statement.execute("CREATE SCHEMA $schema") } }
        val dataSource=object: AbstractDataSource() {
            private fun named(connection: Connection): Connection {
                connection.prepareStatement("SELECT set_config('application_name', ?, false)").use { s -> s.setString(1,schema+"-"+Thread.currentThread().name);s.execute() }
                return connection
            }
            override fun getConnection()=named(source.connection)
            override fun getConnection(username: String,password: String)=named(source.getConnection(username,password))
        }
        val manager=SpringTransactionManager(dataSource,DatabaseConfig {})
        val originalRoot=java.nio.file.Files.createTempDirectory("collection-location-originals-")
        val storage=io.hizhengfu.cloud.common.file.LocalFileObjectStorage(originalRoot.toString())
        try { AnnotationConfigApplicationContext().use { c ->
            c.beanFactory.registerSingleton("transactionManager",manager)
            c.beanFactory.registerSingleton("cacheManager",ConcurrentMapCacheManager())
            c.beanFactory.registerSingleton("keyGenerator",org.springframework.cache.interceptor.SimpleKeyGenerator())
            c.beanFactory.registerSingleton("scope",CollectionScopeResolver("single","museum"))
            c.beanFactory.registerSingleton("originalStorage",storage)
            c.beanFactory.registerSingleton("binder",mock(BusinessAttachmentBinder::class.java))
            c.beanFactory.registerSingleton("locationGuard",spy(CollectionLocationReferenceGuard()))
            c.beanFactory.registerSingleton("catalogs",mock(CollectionCatalogService::class.java))
            c.beanFactory.registerSingleton("changes",mock(CollectionChangeService::class.java))
            c.beanFactory.registerSingleton("retained",mock(CollectionRetainedLedgerService::class.java))
            c.beanFactory.registerSingleton("disclosure",mock(CollectionDisclosureService::class.java))
            c.beanFactory.registerSingleton("flow",mock(FlowInstanceService::class.java))
            c.beanFactory.registerSingleton("userResolver",mock(CollectionUserResolver::class.java).also { resolver ->
                `when`(resolver.names(anyCollection())).thenAnswer { call -> (call.arguments[0] as Collection<*>).filterNotNull().associate { it.toString() to it.toString() } }
            })
            c.beanFactory.registerSingleton("returnStatutory",mock(ReturnStatutoryService::class.java))
            c.beanFactory.registerSingleton("archives",mock(CollectionPermanentArchiveService::class.java))
            c.beanFactory.registerSingleton("responsibleUsers",mock(CollectionResponsibleUserService::class.java))
            c.beanFactory.registerSingleton("requisition",mock(RequisitionService::class.java))
            c.beanFactory.registerSingleton("transferStatutory",mock(CollectionTransferStatutoryService::class.java))
            c.beanFactory.registerSingleton("entryPrinter",mock(CollectionPrintService::class.java))
            c.beanFactory.registerSingleton("entryCompliance",mock(AcquisitionComplianceService::class.java))
            c.beanFactory.registerSingleton("entryReceipts",mock(AccessionReceiptService::class.java))
            c.beanFactory.registerSingleton("mainFileReadPolicy",mock(CollectionMainFileReadPolicy::class.java))
            c.register(EntryVoucherService::class.java,InventoryComplianceService::class.java,CollectionLedgerStatisticsService::class.java,CollectionArchivePacketService::class.java)
            c.register(Transactions::class.java,CollectionOriginalAccessService::class.java,WarehouseLogService::class.java,WarehouseEntryApplicationService::class.java,WarehouseEntryStatutoryService::class.java,WarehouseService::class.java,CollectionService::class.java,CollectionRegistrationNumberService::class.java,CollectionOperationLockService::class.java,
                CollectionPlacementService::class.java,StorageMovementService::class.java,CollectionReturnService::class.java,CollectionBusinessItemService::class.java,ExhibitionService::class.java,CollectionTransferService::class.java,InventoryBatchService::class.java,RfidWorkstationService::class.java)
            // 任职维护核验实际档案行，专用实例使用真实归档；其他位置协作仍保留原替身范围。
            c.registerBean(WarehouseStaffingService::class.java,java.util.function.Supplier {
                val scope=c.getBean(CollectionScopeResolver::class.java)
                val binder=mock(BusinessAttachmentBinder::class.java).also { b ->
                    `when`(b.resolveSingle(anyString())).thenAnswer { it.getArgument<String>(0) }
                }
                WarehouseStaffingService(scope,c.getBean(CollectionResponsibleUserService::class.java),CollectionPermanentArchiveService(binder,scope,readAccess=unprotectedArchiveReadAccessProvider()))
            })
            c.registerBean(WarehouseDiaryService::class.java,java.util.function.Supplier {
                val scope=c.getBean(CollectionScopeResolver::class.java)
                val binder=mock(BusinessAttachmentBinder::class.java).also { b ->
                    `when`(b.resolveSingle(anyString())).thenAnswer { it.getArgument<String>(0) }
                }
                WarehouseDiaryService(scope,c.getBean(WarehouseStaffingService::class.java),
                    c.getBean(CollectionResponsibleUserService::class.java),CollectionPermanentArchiveService(binder,scope,localStorage=storage,readAccess=unprotectedArchiveReadAccessProvider()))
            })
            c.registerBean(PaperLedgerService::class.java,java.util.function.Supplier {
                val scope=c.getBean(CollectionScopeResolver::class.java)
                val binder=mock(BusinessAttachmentBinder::class.java).also { b ->
                    `when`(b.resolveSingle(anyString())).thenAnswer { it.getArgument<String>(0) }
                }
                PaperLedgerService(scope,c.getBean(CollectionResponsibleUserService::class.java),CollectionPermanentArchiveService(binder,scope,readAccess=unprotectedArchiveReadAccessProvider()))
            })
            c.registerBean(LedgerReconciliationService::class.java,java.util.function.Supplier {
                val scope=c.getBean(CollectionScopeResolver::class.java)
                val binder=mock(BusinessAttachmentBinder::class.java).also { b ->
                    `when`(b.resolveSingle(anyString())).thenAnswer { it.getArgument<String>(0) }
                }
                val archives=CollectionPermanentArchiveService(binder,scope,readAccess=unprotectedArchiveReadAccessProvider())
                LedgerReconciliationService(scope,c.getBean(CollectionResponsibleUserService::class.java),
                    c.getBean(PaperLedgerService::class.java),archives,CollectionRetainedLedgerService(scope,archives))
            })
            c.registerBean(AnnualReportEvidenceService::class.java,java.util.function.Supplier {
                val scope=c.getBean(CollectionScopeResolver::class.java)
                val binder=mock(BusinessAttachmentBinder::class.java).also { b ->
                    `when`(b.resolveSingle(anyString())).thenAnswer { it.getArgument<String>(0) }
                }
                AnnualReportEvidenceService(scope,c.getBean(CollectionLedgerStatisticsService::class.java),c.getBean(CollectionResponsibleUserService::class.java),CollectionPermanentArchiveService(binder,scope,readAccess=unprotectedArchiveReadAccessProvider()))
            })
            c.refresh();SpringContextHolder().setApplicationContext(c)
            val r=Runtime(c,TransactionTemplate(manager).apply { isolationLevel = isolation },source,schema)
            r.db {
                SchemaUtils.create(*CollectionInstall("single").initTables().toTypedArray())
                originalReaderSchema()
                originalReader("k1",CollectionConst.STORAGE)
                actorRole("k1","FILE_k1",setOf(CollectionConst.WAREHOUSE_ENTRY_VERIFY))
                actorRole("k1","STAFFING_SETUP",setOf(CollectionConst.WAREHOUSE_MANAGE))
                SchemaUtils.create(io.hizhengfu.cloud.sys.file.dao.Files)
                listOf("test-assignment","test-reading","test-diary").forEach { id ->
                    val bytes=("Synthetic PostgreSQL location fixture: "+id).padEnd(100,' ').toByteArray()
                    java.nio.file.Files.write(originalRoot.resolve("$id.pdf"),bytes)
                    io.hizhengfu.cloud.sys.file.dao.FileDao.new(id) {name="$id.pdf";path="$id.pdf";hash=id;size=bytes.size.toLong();ext="pdf"}
                }
                CollectionOrgDao.new("museum") { code="museum";name="测试馆" }
                CollectionOrgDao.new("other") { code="other";name="另一馆" }
                listOf("branch","target").forEach { key -> WarehouseDao.new(key) { orgId="museum";name=key;type=EnumWarehouseType.WAREHOUSE;sceneType=EnumLocationScene.STORAGE;parentId="0";parentIds="0,";isLeaf=true } }
            }
            try { asDiaryReader { body(r) } } finally { r.executor.shutdownNow();check(r.executor.awaitTermination(20,TimeUnit.SECONDS)) }
        } } finally {
            try { source.connection.use { it.createStatement().use { statement -> statement.execute("DROP SCHEMA $schema CASCADE") } } }
            finally { java.nio.file.Files.walk(originalRoot).use { files -> files.sorted(java.util.Comparator.reverseOrder()).forEach(java.nio.file.Files::delete) } }
            check(!java.nio.file.Files.exists(originalRoot))
        }
    }
    @ParameterizedTest @ValueSource(booleans=[false,true])
    fun `inventory annual report rechecks permission after waiting for museum lock`(rollback:Boolean)=fixture {r->
        val service=r.c.getBean(InventoryComplianceService::class.java)
        val at=java.time.LocalDateTime.now().minusMinutes(2)
        val input=InventoryAnnualReportVo(at.year,at,"leader","test-assignment","测试主管部门","TEST-ANNUAL",at.plusSeconds(1),"test-assignment")
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val first=r.worker("first") {r.db {
            CollectionLocationMutation.acquire("museum")
            RolePermissions.deleteWhere {permissionId eq CollectionConst.STORAGE}
            entered.countDown();check(release.await(15,TimeUnit.SECONDS))
            if(rollback)error("permission rollback")
            "revoked"
        }}
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS))
            val second=r.worker("second") {service.report(input)}
            r.awaitSecondProgress(second);release.countDown()
            val permission=first.get(15,TimeUnit.SECONDS);val report=second.get(15,TimeUnit.SECONDS)
            if(rollback) {
                assertEquals("permission rollback",permission.exceptionOrNull()?.message)
                val id=report.getOrThrow()
                r.db {assertEquals(id,InventoryAnnualReportDao.all().single().id.value)}
            } else {
                assertEquals("revoked",permission.getOrThrow())
                assertTrue(report.exceptionOrNull() is BadRequestException,"Revoked permission must reject annual report: $report")
                r.db {assertEquals(0L,InventoryAnnualReportDao.count())}
            }
        } finally {release.countDown()}
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource("new,false", "new,true", "repeat,false", "repeat,true", "custody,false", "custody,true", "finance,false", "finance,true", "finance-repeat,false", "finance-repeat,true")
    fun `paper and finance writes recheck permission after waiting for museum lock`(operation:String,rollback:Boolean)=fixture {r->
        val books=r.c.getBean(PaperLedgerService::class.java)
        val finance=r.c.getBean(LedgerReconciliationService::class.java)
        val at=java.time.LocalDateTime.now().minusMinutes(3).truncatedTo(java.time.temporal.ChronoUnit.MILLIS)
        val custody=PaperLedgerCustodyVo(at,"ledger","测试纸账柜",EnumPaperLedgerCondition.GOOD,"合成完整","test-assignment")
        val book=PaperLedgerBookVo("LOCK-BOOK","测试纸账",at.toLocalDate().minusDays(1),10,"合成范围","test-assignment",custody)
        r.db {actorRole("k1","PAPER_LOCK",setOf(CollectionConst.LEDGER_CORRECT,CollectionConst.LEDGER_FINANCE))}
        val bookId=if(operation=="new") null else books.register("museum",book)
        val report=bookId?.let {LedgerReconciliationVo("LOCK-FINANCE",at.toLocalDate(),at.toLocalDate(),at,"ledger","finance","合成空账范围",setOf(it),"test-assignment","test-assignment","test-assignment",at.toLocalDate().plusDays(1),emptyScopeReason="独立测试空馆")}
        if(operation=="finance-repeat")finance.record("museum",report!!)
        fun snapshot()=r.db {listOf(PaperLedgerBookDao.all().map {it.id.value to it.content},PaperLedgerCustodyDao.all().map {it.id.value to it.content},LedgerReconciliationDao.all().map {it.id.value to it.content},CollectionPermanentArchiveDao.all().map {Triple(it.id.value,it.snapshot,it.digest)})}
        val before=snapshot()
        val permission=if(operation.startsWith("finance"))CollectionConst.LEDGER_FINANCE else CollectionConst.LEDGER_CORRECT
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val first=r.worker("first") {r.db {
            CollectionLocationMutation.acquire("museum")
            RolePermissions.deleteWhere {permissionId eq permission}
            entered.countDown();check(release.await(15,TimeUnit.SECONDS))
            if(rollback)error("permission rollback")
            "revoked"
        }}
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS))
            val second=r.worker("second") {
                when(operation) {
                    "custody" -> books.custody("museum",bookId!!,custody.copy(checkedAt=at.plusSeconds(1),reason="实际复核"))
                    "finance","finance-repeat" -> finance.record("museum",report!!)
                    else -> books.register("museum",book)
                }
            }
            r.awaitSecondProgress(second);release.countDown()
            val revoked=first.get(15,TimeUnit.SECONDS);val saved=second.get(15,TimeUnit.SECONDS)
            if(rollback) {
                assertEquals("permission rollback",revoked.exceptionOrNull()?.message)
                assertTrue(saved.getOrThrow().isNotBlank())
                if(operation in setOf("repeat","finance-repeat"))assertEquals(before,snapshot())
            } else {
                assertEquals("revoked",revoked.getOrThrow())
                assertTrue(saved.exceptionOrNull() is BadRequestException,"Revoked permission must reject $operation: $saved")
                assertEquals(before,snapshot())
            }
        } finally {release.countDown()}
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource("annual,false", "annual,true", "annual-repeat,false", "annual-repeat,true", "jurisdiction,false", "jurisdiction,true")
    fun `annual signing and archive jurisdiction recheck permission after waiting for museum lock`(operation:String,rollback:Boolean)=fixture {r->
        val at=java.time.LocalDateTime.now().minusMinutes(3).truncatedTo(java.time.temporal.ChronoUnit.MILLIS)
        val permission=if(operation.startsWith("annual"))CollectionConst.STAT else CollectionConst.ARCHIVE_MANAGE
        r.db {actorRole("k1","REPORT_LOCK",setOf(permission))}
        val annual=AnnualReportSignedVo(at.year-1,"statistician","keeper","leader",at,"test-assignment",AnnualDeclaredFiguresVo(0,0,0,0,0,0,changesDescription="合成空馆年度"))
        val jurisdiction=ArchiveJurisdictionVo(EnumArchiveJurisdiction.LOCAL_COMPETENT,"本地主管机关","国家机关","地方档案机关",at.toLocalDate(),at,"leader","test-assignment")
        if(operation=="annual-repeat")r.c.getBean(AnnualReportEvidenceService::class.java).signed(annual, "museum")
        val before=r.db {AnnualReportEvidenceDao.count()}
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val first=r.worker("first") {r.db {
            CollectionLocationMutation.acquire("museum")
            RolePermissions.deleteWhere {permissionId eq permission}
            entered.countDown();check(release.await(15,TimeUnit.SECONDS))
            if(rollback)error("permission rollback")
            "revoked"
        }}
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS))
            val second=r.worker("second") {
                if(operation.startsWith("annual"))r.c.getBean(AnnualReportEvidenceService::class.java).signed(annual, "museum")
                else r.c.getBean(CollectionArchivePacketService::class.java).jurisdiction("museum",jurisdiction)
            }
            r.awaitSecondProgress(second);release.countDown()
            val revoked=first.get(15,TimeUnit.SECONDS);val saved=second.get(15,TimeUnit.SECONDS)
            if(rollback) {
                assertEquals("permission rollback",revoked.exceptionOrNull()?.message)
                val id=saved.getOrThrow()
                r.db {if(operation.startsWith("annual"))assertEquals(id,AnnualReportEvidenceDao.all().single().id.value) else assertEquals(id,ArchiveJurisdictionDao.all().single().id.value)}
            } else {
                assertEquals("revoked",revoked.getOrThrow())
                assertTrue(saved.exceptionOrNull() is BadRequestException,"Revoked permission must reject $operation: $saved")
                r.db {assertEquals(before,AnnualReportEvidenceDao.count());assertEquals(0L,ArchiveJurisdictionDao.count())}
            }
        } finally {release.countDown()}
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource("false,false", "false,true", "true,false", "true,true")
    fun `disclosure stop rechecks permission after waiting for museum lock`(repeat:Boolean,rollback:Boolean)=fixture {r->
        val scope=r.c.getBean(CollectionScopeResolver::class.java)
        val binder=mock(BusinessAttachmentBinder::class.java).also {b -> `when`(b.resolveSingle(anyString())).thenAnswer {it.getArgument<String>(0)}}
        val service=CollectionDisclosureService(scope,r.c.getBean(CollectionResponsibleUserService::class.java),CollectionPermanentArchiveService(binder,scope,readAccess=unprotectedArchiveReadAccessProvider()))
        val at=java.time.LocalDateTime.now().minusMinutes(3).truncatedTo(java.time.temporal.ChronoUnit.MILLIS)
        r.db {
            actorRole("k1","DISCLOSURE_LOCK",setOf(CollectionConst.PUBLISH))
            CollectionDisclosureReviewDao.new("review-lock") {
                orgId="museum";subject=EnumDisclosureSubject.RESOURCE;subjectId="fixture";version=1
                content=CollectionDisclosureReviewVo("fixture-revision",EnumDisclosureDecision.PUBLIC,at,"reviewer","test-assignment",EnumDisclosureRestriction.entries.associateWith {DisclosureRestrictionVo(EnumDisclosureHandling.NONE_PRESENT,"合成核查")},content=CollectionPublicContentVo("合成稿","合成内容","合成反馈渠道"),publicContentFileIds="test-assignment")
                feedEnabledAt=at
            }
            if(repeat)service.stop("museum","review-lock",DisclosureFeedStopVo("合成停止供稿"))
        }
        fun snapshot()=r.db {listOf(CollectionDisclosureReviewDao.all().map {listOf(it.id.value,it.content,it.feedEnabledAt,it.feedStoppedAt)},CollectionPermanentArchiveDao.all().map {Triple(it.id.value,it.snapshot,it.digest)})}
        val before=snapshot();val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val first=r.worker("first") {r.db {
            CollectionLocationMutation.acquire("museum")
            RolePermissions.deleteWhere {permissionId eq CollectionConst.PUBLISH}
            entered.countDown();check(release.await(15,TimeUnit.SECONDS))
            if(rollback)error("permission rollback")
            "revoked"
        }}
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS))
            val second=r.worker("second") {r.db {service.stop("museum","review-lock",DisclosureFeedStopVo("合成停止供稿"))}}
            r.awaitSecondProgress(second);release.countDown()
            val revoked=first.get(15,TimeUnit.SECONDS);val saved=second.get(15,TimeUnit.SECONDS)
            if(rollback) {
                assertEquals("permission rollback",revoked.exceptionOrNull()?.message)
                assertEquals("review-lock",saved.getOrThrow())
                r.db {assertNotNull(CollectionDisclosureReviewDao["review-lock"].feedStoppedAt)}
                if(repeat)assertEquals(before,snapshot())
            } else {
                assertEquals("revoked",revoked.getOrThrow())
                assertTrue(saved.exceptionOrNull() is BadRequestException,"Revoked permission must reject disclosure stop: $saved")
                assertEquals(before,snapshot())
            }
        } finally {release.countDown()}
    }

    private fun entryAcquisition(r:Runtime) = r.db {
        CollectionOrgDao["museum"].systemGroupId="museum"
        actorRole("k1","ENTRY_LOCATION",setOf(CollectionConst.ACQUIRE,CollectionConst.LEDGER_CREATE))
        AcquisitionDao.new("entry-acquisition") {orgId="museum";name="入馆位置测试";source=EnumSource.PURCHASE;flowStatus=EnumFlowStatus.PASS}
        EntryVoucherVo(acquisitionIds="entry-acquisition",warehouseId="branch",remark="original")
    }
    @ParameterizedTest @ValueSource(booleans=[false,true])
    fun `entry voucher rechecks permission after waiting for museum lock`(rollback:Boolean)=fixture {r->
        val input=entryAcquisition(r)
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val first=r.worker("first") { r.db {
            CollectionLocationMutation.acquire("museum")
            RolePermissions.deleteWhere { permissionId eq CollectionConst.ACQUIRE }
            entered.countDown();check(release.await(15,TimeUnit.SECONDS))
            if(rollback)error("permission rollback")
            "revoked"
        } }
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS))
            val second=r.worker("second") {r.vouchers.save(input)}
            r.awaitSecondProgress(second);release.countDown()
            val revoked=first.get(15,TimeUnit.SECONDS);val saved=second.get(15,TimeUnit.SECONDS)
            if(rollback) {
                assertEquals("permission rollback",revoked.exceptionOrNull()?.message)
                val id=saved.getOrThrow()
                r.db {assertEquals("branch",EntryVoucherDao[id].warehouseId)}
            } else {
                assertEquals("revoked",revoked.getOrThrow())
                assertTrue(saved.exceptionOrNull() is BadRequestException)
                r.db {assertEquals(0L,EntryVoucherDao.count());assertNull(AcquisitionDao["entry-acquisition"].collectionId)}
            }
        } finally {release.countDown()}
    }

    @ParameterizedTest @ValueSource(booleans=[false,true])
    fun `entry voucher waits for location deletion commit or rollback`(rollback:Boolean)=fixture {r->
        val input=entryAcquisition(r)
        val entered=CountDownLatch(1);val release=CountDownLatch(1);r.pauseFirstCheck(entered,release)
        val first=r.worker("first") {r.db {r.warehouse.delete("branch");if(rollback)error("rollback deletion");"deleted"}}
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS))
            val second=r.worker("second") {r.db {WarehouseDao["branch"].name;r.vouchers.save(input)}}
            r.awaitSecondProgress(second);release.countDown()
            val removed=first.get(15,TimeUnit.SECONDS);val saved=second.get(15,TimeUnit.SECONDS)
            if(rollback) {
                assertTrue(removed.isFailure);val id=saved.getOrThrow()
                r.db {assertNotNull(WarehouseDao.findById("branch"));assertEquals("branch",EntryVoucherDao[id].warehouseId)}
            } else {
                removed.getOrThrow();assertTrue(saved.exceptionOrNull() is BadRequestException)
                r.db {assertNull(WarehouseDao.findById("branch"));assertEquals(0L,EntryVoucherDao.all().count())}
            }
        } finally {release.countDown()}
    }
    @ParameterizedTest @ValueSource(booleans=[false,true])
    fun `entry voucher reference commit or rollback coordinates waiting location deletion`(rollback:Boolean)=fixture {r->
        val input=entryAcquisition(r)
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val first=r.worker("first") {r.db {
            val id=r.vouchers.save(input);EntryVoucherDao[id].flush();entered.countDown()
            check(release.await(15,TimeUnit.SECONDS));if(rollback)error("rollback voucher");id
        }}
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS))
            val second=r.worker("second") {r.warehouse.delete("branch");"deleted"}
            r.awaitSecondProgress(second);release.countDown()
            val saved=first.get(15,TimeUnit.SECONDS);val removed=second.get(15,TimeUnit.SECONDS)
            if(rollback) {
                assertTrue(saved.isFailure);removed.getOrThrow()
                r.db {assertEquals(0L,EntryVoucherDao.all().count());assertNull(WarehouseDao.findById("branch"))}
            } else {
                val id=saved.getOrThrow();assertTrue(removed.exceptionOrNull() is BadRequestException)
                r.db {assertEquals("branch",EntryVoucherDao[id].warehouseId);assertNotNull(WarehouseDao.findById("branch"))}
            }
        } finally {release.countDown()}
    }

    @Test fun `entry voucher failed update preserves prior position and fields after waiting deletion`()=fixture {r->
        val input=entryAcquisition(r)
        val id=r.vouchers.save(input.copy(warehouseId="target"))
        val entered=CountDownLatch(1);val release=CountDownLatch(1);r.pauseFirstCheck(entered,release)
        val first=r.worker("first") {r.warehouse.delete("branch");"deleted"}
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS))
            val second=r.worker("second") {r.db {
                WarehouseDao["branch"].name
                r.vouchers.save(input.copy(id=id,remark="must roll back"))
            }}
            r.awaitSecondProgress(second);release.countDown();first.get(15,TimeUnit.SECONDS).getOrThrow()
            assertTrue(second.get(15,TimeUnit.SECONDS).exceptionOrNull() is BadRequestException)
            r.db {assertEquals("target",EntryVoucherDao[id].warehouseId);assertEquals("original",EntryVoucherDao[id].remark)}
        } finally {release.countDown()}
    }
    @ParameterizedTest @ValueSource(strings=["missing-parent","foreign-parent","cycle"])
    fun `entry voucher rejects inconsistent actual parent chain without writing a draft`(kind:String)=fixture {r->
        val input=entryAcquisition(r)
        r.db {
            when(kind) {
                "missing-parent"->WarehouseDao["branch"].parentId="missing"
                "foreign-parent"->{WarehouseDao["target"].orgId="other";WarehouseDao["branch"].parentId="target"}
                "cycle"->WarehouseDao["branch"].parentId="branch"
            }
        }
        assertThrows(BadRequestException::class.java) {r.vouchers.save(input)}
        r.db {assertEquals(0L,EntryVoucherDao.all().count())}
    }
    @Test fun `entry voucher draft still permits an unselected position`()=fixture {r->
        val input=entryAcquisition(r)
        val id=r.vouchers.save(input.copy(warehouseId="  "))
        r.db {assertNull(EntryVoucherDao[id].warehouseId);assertEquals(EnumVoucherStatus.DRAFT,EntryVoucherDao[id].voucherStatus)}
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.CsvSource("false,branch","true,branch","false,blank","true,blank")
    fun `entry voucher competing acquisition claim observes committed owner after waiting`(rollback:Boolean,position:String)=fixture {r->
        val input=entryAcquisition(r).copy(warehouseId=position.takeUnless {it=="blank"})
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val first=r.worker("first") {r.db {
            val id=r.vouchers.save(input);EntryVoucherDao[id].flush();entered.countDown()
            check(release.await(15,TimeUnit.SECONDS));if(rollback)error("rollback claim");id
        }}
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS))
            val second=r.worker("second") {r.vouchers.save(input)}
            r.awaitSecondProgress(second);release.countDown()
            val saved=first.get(15,TimeUnit.SECONDS);val competing=second.get(15,TimeUnit.SECONDS)
            if(rollback) {assertTrue(saved.isFailure);competing.getOrThrow()}
            else {saved.getOrThrow();assertTrue(competing.exceptionOrNull() is BadRequestException,"Competing voucher result: $competing")}
            r.db {assertEquals(1L,EntryVoucherDao.all().count());assertEquals("entry-acquisition",EntryVoucherDao.all().single().acquisitionIds)}
        } finally {release.countDown()}
    }

    @ParameterizedTest @ValueSource(strings=["rejected","registered","foreign","missing"])
    fun `entry voucher refreshes changed acquisition after position lock wait`(kind:String)=fixture {r->
        val input=entryAcquisition(r)
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val first=r.worker("first") {r.db {
            CollectionLocationMutation.acquire("museum")
            val acquisition=AcquisitionDao["entry-acquisition"]
            when(kind) {
                "rejected"->acquisition.flowStatus=EnumFlowStatus.REJECT
                "registered"->acquisition.collectionId="already-registered"
                "foreign"->acquisition.orgId="other"
                "missing"->acquisition.delete()
            }
            if(kind!="missing")acquisition.flush()
            entered.countDown();check(release.await(15,TimeUnit.SECONDS));"changed"
        }}
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS))
            val second=r.worker("second") {r.vouchers.save(input)}
            r.awaitSecondProgress(second);release.countDown();first.get(15,TimeUnit.SECONDS).getOrThrow()
            assertTrue(second.get(15,TimeUnit.SECONDS).exceptionOrNull() is BadRequestException)
            r.db {assertEquals(0L,EntryVoucherDao.all().count())}
        } finally {release.countDown()}
    }

    @ParameterizedTest @ValueSource(strings=["save","execute"])
    fun `entry voucher rejects duplicate acquisitions before receipt or collection mutation`(operation:String)=fixture {r->
        val input=entryAcquisition(r).copy(acquisitionIds="entry-acquisition，entry-acquisition")
        if(operation=="execute")r.db {
            EntryVoucherDao.new("legacy-duplicate") {orgId="museum";voucherNo="RJ-legacy-duplicate";acquisitionIds=input.acquisitionIds!!;warehouseId="branch"}
        }
        val error=assertThrows(BadRequestException::class.java) {
            if(operation=="save")r.vouchers.save(input) else r.vouchers.execute("legacy-duplicate")
        }
        assertTrue(error.message!!.contains("不能重复"))
        verifyNoInteractions(r.c.getBean(AccessionReceiptService::class.java))
        r.db {
            assertEquals(0L,CollectionDao.all().count());assertNull(AcquisitionDao["entry-acquisition"].collectionId)
            if(operation=="save")assertEquals(0L,EntryVoucherDao.all().count())
            else {assertEquals(input.acquisitionIds,EntryVoucherDao["legacy-duplicate"].acquisitionIds);assertEquals(EnumVoucherStatus.DRAFT,EntryVoucherDao["legacy-duplicate"].voucherStatus)}
        }
    }

    private fun accessionAtNewLocation(r:Runtime):Triple<AccessionReceiptService,String,AccessionReceiptVo> {
        val input=entryAcquisition(r)
        val voucher=r.vouchers.save(input.copy(warehouseId="target"))
        val at=java.time.LocalDateTime.now().minusHours(2).withNano(123000000)
        val compliance=r.c.getBean(AcquisitionComplianceService::class.java)
        val acquisition=r.db { AcquisitionDao["entry-acquisition"] }
        `when`(compliance.requirePrepared(any(AcquisitionDao::class.java) ?: acquisition))
            .thenReturn(AcquisitionPreparedSource(EnumCollectionNature.OTHER,at.toLocalDate().minusDays(1)))
        val service=r.c.autowireCapableBeanFactory.createBean(AccessionReceiptService::class.java)
        assertFalse(mockingDetails(service).isMock)
        assertTrue(org.springframework.aop.support.AopUtils.isAopProxy(service))
        val value=AccessionReceiptVo("entry-acquisition",at,"branch",setOf("k1"),setOf("合成移交人"),
            listOf(AccessionPieceVo("piece-1",at.minusMinutes(10),"合成实物编号","合成保存状况",true,true,"receipt-photo")),true,"receipt-sign")
        return Triple(service,voucher,value)
    }
    @ParameterizedTest @ValueSource(booleans=[false,true])
    fun `accession receipt rechecks permission after waiting for museum lock`(rollback:Boolean)=fixture {r->
        val (service,voucher,value)=accessionAtNewLocation(r)
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val first=r.worker("first") {r.db {
            CollectionLocationMutation.acquire("museum")
            RolePermissions.deleteWhere { (permissionId eq CollectionConst.ACQUIRE) or (permissionId eq CollectionConst.STORAGE) }
            entered.countDown();check(release.await(15,TimeUnit.SECONDS))
            if(rollback)error("permission rollback")
            "revoked"
        }}
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS))
            val second=r.worker("second") {service.record(voucher,value)}
            r.awaitSecondProgress(second,blockerName="first");release.countDown()
            val revoked=first.get(15,TimeUnit.SECONDS);val saved=second.get(15,TimeUnit.SECONDS)
            if(rollback) {
                assertEquals("permission rollback",revoked.exceptionOrNull()?.message)
                val id=saved.getOrThrow()
                r.db {assertEquals(value,AccessionReceiptDao[id].content)}
            } else {
                assertEquals("revoked",revoked.getOrThrow())
                assertTrue(saved.exceptionOrNull() is BadRequestException)
                verifyNoInteractions(r.c.getBean(CollectionPermanentArchiveService::class.java))
                r.db {assertEquals(0L,AccessionReceiptDao.count());assertEquals(EnumVoucherStatus.DRAFT,EntryVoucherDao[voucher].voucherStatus)}
            }
        } finally {release.countDown()}
    }

    @ParameterizedTest @ValueSource(booleans=[false,true])
    fun `accession receipt waits for actual location deletion commit or rollback`(rollback:Boolean)=fixture {r->
        val (service,voucher,value)=accessionAtNewLocation(r)
        val entered=CountDownLatch(1);val release=CountDownLatch(1);r.pauseFirstCheck(entered,release)
        val first=r.worker("first") {r.db {r.warehouse.delete("branch");if(rollback)error("rollback deletion");"deleted"}}
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS))
            val second=r.worker("second") {r.db {WarehouseDao["branch"].name;service.record(voucher,value)}}
            r.awaitSecondProgress(second,blockerName="first");release.countDown()
            val deleted=first.get(15,TimeUnit.SECONDS);val recorded=second.get(15,TimeUnit.SECONDS)
            if(rollback) {
                assertTrue(deleted.isFailure);val id=recorded.getOrThrow()
                r.db {assertEquals("branch",AccessionReceiptDao[id].content.warehouseId);assertNotNull(WarehouseDao.findById("branch"))}
            } else {
                deleted.getOrThrow()
                val failure=recorded.exceptionOrNull()
                assertTrue(failure is BadRequestException,failure?.stackTraceToString() ?: recorded.toString())
                assertTrue(failure!!.message in setOf("库位不存在","库位已被删除，请重新选择位置","实际接收保管库位不存在"),failure.stackTraceToString())
                r.db {assertEquals(0L,AccessionReceiptDao.all().count());assertNull(WarehouseDao.findById("branch"))}
            }
            r.db {assertEquals("target",EntryVoucherDao[voucher].warehouseId)}
        } finally {release.countDown()}
    }
    @ParameterizedTest @ValueSource(booleans=[false,true])
    fun `accession receipt reference commit or rollback coordinates location deletion`(rollback:Boolean)=fixture {r->
        val (service,voucher,value)=accessionAtNewLocation(r)
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val first=r.worker("first") {r.db {
            val id=service.record(voucher,value);AccessionReceiptDao[id].flush();entered.countDown()
            check(release.await(15,TimeUnit.SECONDS));if(rollback)error("rollback receipt");id
        }}
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS))
            val second=r.worker("second") {r.warehouse.delete("branch");"deleted"}
            r.awaitSecondProgress(second);release.countDown()
            val recorded=first.get(15,TimeUnit.SECONDS);val deleted=second.get(15,TimeUnit.SECONDS)
            if(rollback) {
                assertTrue(recorded.isFailure);deleted.getOrThrow()
                r.db {assertEquals(0L,AccessionReceiptDao.all().count());assertNull(WarehouseDao.findById("branch"))}
            } else {
                val id=recorded.getOrThrow();assertTrue(deleted.exceptionOrNull() is BadRequestException)
                r.db {assertEquals("branch",AccessionReceiptDao[id].content.warehouseId);assertNotNull(WarehouseDao.findById("branch"))}
            }
        } finally {release.countDown()}
    }

    @ParameterizedTest @ValueSource(strings=["missing-parent","foreign-parent","cycle"])
    fun `accession receipt rejects inconsistent actual location parents before recording`(kind:String)=fixture {r->
        val (service,voucher,value)=accessionAtNewLocation(r)
        r.db {
            when(kind) {
                "missing-parent"->WarehouseDao["branch"].parentId="missing"
                "foreign-parent"->{WarehouseDao["target"].orgId="other";WarehouseDao["branch"].parentId="target"}
                "cycle"->WarehouseDao["branch"].parentId="branch"
            }
        }
        assertThrows(BadRequestException::class.java) {service.record(voucher,value)}
        verifyNoInteractions(r.c.getBean(CollectionPermanentArchiveService::class.java))
        r.db {assertEquals(0L,AccessionReceiptDao.all().count());assertEquals(EnumVoucherStatus.DRAFT,EntryVoucherDao[voucher].voucherStatus)}
    }

    @ParameterizedTest @ValueSource(strings=["foreign","exhibition","retired"])
    fun `accession receipt rechecks cached actual location after a committed change`(kind:String)=fixture {r->
        val (service,voucher,value)=accessionAtNewLocation(r)
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val first=r.worker("first") {r.db {
            CollectionLocationMutation.acquire("museum")
            val location=WarehouseDao["branch"]
            when(kind) {"foreign"->location.orgId="other";"exhibition"->location.sceneType=EnumLocationScene.EXHIBITION;"retired"->location.status=EnumContentStatus.DELETE}
            location.flush();entered.countDown();check(release.await(15,TimeUnit.SECONDS));"changed"
        }}
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS))
            val second=r.worker("second") {r.db {
                assertEquals("museum",WarehouseDao["branch"].orgId)
                assertEquals(EnumLocationScene.STORAGE,WarehouseDao["branch"].sceneType)
                assertEquals(EnumContentStatus.NORMAL,WarehouseDao["branch"].status)
                service.record(voucher,value)
            }}
            r.awaitSecondProgress(second);release.countDown();first.get(15,TimeUnit.SECONDS).getOrThrow()
            assertTrue(second.get(15,TimeUnit.SECONDS).exceptionOrNull() is BadRequestException)
            verifyNoInteractions(r.c.getBean(CollectionPermanentArchiveService::class.java))
            r.db {assertEquals(0L,AccessionReceiptDao.all().count());assertEquals("target",EntryVoucherDao[voucher].warehouseId)}
        } finally {release.countDown()}
    }

    @Test fun `accession receipt rejects a retired location without changing the recorded voucher`()=fixture {r->
        val (service,voucher,value)=accessionAtNewLocation(r)
        val before=r.db {WarehouseDao["branch"].status=EnumContentStatus.DELETE;EntryVoucherDao[voucher].toModel()}
        val error=assertThrows(BadRequestException::class.java) {service.record(voucher,value)}
        assertTrue(error.message.orEmpty().contains("库位已退役"),error.message)
        verifyNoInteractions(r.c.getBean(CollectionPermanentArchiveService::class.java))
        r.db {assertEquals(before,EntryVoucherDao[voucher].toModel());assertEquals(0L,AccessionReceiptDao.count())}
    }

    @ParameterizedTest @ValueSource(strings=[" branch ",""])
    fun `accession receipt rejects a location value that cannot be retained as its exact identity`(locationId:String)=fixture {r->
        val (service,voucher,value)=accessionAtNewLocation(r)
        assertThrows(BadRequestException::class.java) {service.record(voucher,value.copy(warehouseId=locationId))}
        verifyNoInteractions(r.c.getBean(CollectionPermanentArchiveService::class.java))
        r.db {assertEquals(0L,AccessionReceiptDao.all().count())}
    }

    private data class LocationWriteScenario(val save:()->String,val verifySaved:(String)->Unit,val state:()->List<Any?>,val before:List<Any?>)
    private fun relocationAtNewLocation(r:Runtime,operation:String,locationId:String="branch"):LocationWriteScenario {
        val scope=r.c.getBean(CollectionScopeResolver::class.java)
        val users=r.c.getBean(CollectionResponsibleUserService::class.java)
        val binder=mock(BusinessAttachmentBinder::class.java).also {b->`when`(b.resolveSingle(anyString())).thenAnswer {it.getArgument<String>(0)}}
        val archives=CollectionPermanentArchiveService(binder,scope,localStorage=r.c.getBean(io.hizhengfu.cloud.common.file.LocalFileObjectStorage::class.java),readAccess=unprotectedArchiveReadAccessProvider())
        val incidents=CollectionIncidentService(scope,users,archives,CollectionIncidentSourceService())
        val work=IncidentWorkService(scope,users,incidents,archives)
        val service=r.c.autowireCapableBeanFactory.initializeBean(IncidentRelocationService(scope,users,incidents,work,archives),"incidentLocationService") as IncidentRelocationService
        assertTrue(org.springframework.aop.support.AopUtils.isAopProxy(service));assertFalse(mockingDetails(service).isMock)
        val at=java.time.LocalDateTime.now().minusDays(1).withNano(123000000)
        val file="test-assignment"
        val case=r.db {
            actorRole("k1","INCIDENT_LOCATION",setOf(CollectionConst.INCIDENT))
            CollectionOrgDao["museum"].systemGroupId="museum"
            listOf("target","branch").forEach {WarehouseDao[it].sceneType=EnumLocationScene.EXHIBITION;WarehouseDao[it].type=EnumWarehouseType.EXHIBITION_HALL}
            CollectionDao.new("relocation-piece") {orgId="museum";name="合成应急实物";totalRegNo="EM-1";lifeStatus=EnumCollectionStatus.IN_STORE;warehouseId="target"}
            incidents.create(IncidentFactsVo("合成风险事实",at.minusHours(1),location="实际地点",reporterUserId="k1",description="核实需搬离风险",immediateMeasures="隔离并报告",items=listOf(IncidentItemVo("relocation-piece",false,EnumIncidentSecurity.NONE,false,"实际风险")),evidenceFileIds=file))
        }
        val departure=IncidentRelocationDepartureVo(at.plusHours(1),if(operation=="departure")locationId else"target",emptySet(),"",true,false,"实际现状","实际包护",file,file,
            appliedTransportStandardReference="GB/T 23862-2024",packingStandardConfirmed=true,packingCheckFileIds=file)
        val receipt=IncidentRelocationReceiptVo(at.plusHours(2),locationId,emptySet(),"",true,false,false,true,false,"实际接收现状","实际保护措施","继续核查",file,file)
        val emergency=IncidentEmergencyRelocationVo("relocation-piece",setOf(case),"已实际移离险情","如实补记，无事前批准",file,"conservation","keeper",departure,if(operation=="emergency")receipt else null)
        val prepared=if(operation=="receipt")service.recordEmergency(emergency)else null
        val plan=IncidentRelocationPlanVo("relocation-piece",setOf(case),at,at.plusHours(1),at.plusHours(3),"target",locationId,"移离风险","实物现状","双人取放","专用包护","实际通道","全程安全要求","出现风险立即停止",at.minusMinutes(1),true,"department","conservation","department-head","keeper","leader","custodian","receiver","security","合成运输组","supervisor",file,file,file,file,
            transportStandardReference="GB/T 23862-2024",transportStandardApplicability="逐件核对文物包装运输条件")
        val state:()->List<Any?> = {r.db {listOf(
            CollectionDao["relocation-piece"].warehouseId,
            IncidentRelocationDao.all().map {listOf(it.id.value,it.state,it.sequence)},
            IncidentRelocationRecordDao.all().map {it.id.value to it.content},
            StorageMovementDao.all().map {listOf(it.id.value,it.fromWarehouseId,it.toWarehouseId,it.operatedAt)},
            CollectionPermanentArchiveDao.all().map {it.id.value to it.digest},
            CollectionPermanentArchiveFileDao.all().map {it.id.value to it.metadata})}}
        val save:()->String={when(operation) {"plan"->service.create(plan);"receipt"->service.receive(prepared!!,receipt);else->service.recordEmergency(emergency)}}
        val verifySaved:(String)->Unit={id->r.db {
            val root=IncidentRelocationDao[id]
            val rows=IncidentRelocationRecordDao.find {IncidentRelocationRecords.relocationId eq id}.toList()
            rows.forEach {IncidentRelocationEvidence.requireOriginal(root,it)}
            if(operation=="plan") {
                val content=io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils.fromJson(rows.single().content,IncidentRelocationPlanSnapshot::class.java)!!
                assertEquals("branch",content.plan.toLocationId);assertEquals(EnumIncidentRelocationState.PLAN,root.state)
                assertEquals("target",CollectionDao["relocation-piece"].warehouseId)
            } else if(operation=="departure") {
                val content=io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils.fromJson(rows.single {it.kind==EnumIncidentRelocationKind.DEPARTURE}.content,IncidentRelocationDepartureVo::class.java)!!
                assertEquals(departure,content);assertEquals(EnumIncidentRelocationState.DEPARTED,root.state)
                assertNull(CollectionDao["relocation-piece"].warehouseId)
            } else {
                assertEquals(receipt,IncidentRelocationEvidence.receipt(root,archives));assertEquals(EnumIncidentRelocationState.RECEIVED,root.state)
                assertEquals("branch",CollectionDao["relocation-piece"].warehouseId)
            }
        }}
        return LocationWriteScenario(save,verifySaved,state,state())
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.CsvSource("plan,false","plan,true","receipt,false","receipt,true","emergency,false","emergency,true","departure,false","departure,true")
    fun `incident relocation waits for new location deletion commit or rollback`(operation:String,rollback:Boolean)=fixture {r->
        val scenario=relocationAtNewLocation(r,operation)
        val entered=CountDownLatch(1);val release=CountDownLatch(1);r.pauseFirstCheck(entered,release)
        val first=r.worker("first") {r.db {r.warehouse.delete("branch");if(rollback)error("rollback deletion");"deleted"}}
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS))
            val second=r.worker("second") {r.db {WarehouseDao["branch"].name;scenario.save()}}
            r.awaitSecondProgress(second);release.countDown()
            val deleted=first.get(15,TimeUnit.SECONDS);val saved=second.get(15,TimeUnit.SECONDS)
            if(rollback) {assertTrue(deleted.isFailure);scenario.verifySaved(saved.getOrThrow());r.db {assertNotNull(WarehouseDao.findById("branch"))}}
            else {deleted.getOrThrow();assertTrue(saved.exceptionOrNull() is BadRequestException);assertEquals(scenario.before,scenario.state());r.db {assertNull(WarehouseDao.findById("branch"))}}
        } finally {release.countDown()}
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.CsvSource("plan,false","plan,true","receipt,false","receipt,true","emergency,false","emergency,true","departure,false","departure,true")
    fun `incident relocation reference commit or rollback coordinates deletion`(operation:String,rollback:Boolean)=fixture {r->
        val scenario=relocationAtNewLocation(r,operation)
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val first=r.worker("first") {r.db {
            val id=scenario.save();org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().entityCache.flush()
            entered.countDown();check(release.await(15,TimeUnit.SECONDS));if(rollback)error("rollback relocation");id
        }}
        try {
            if(!entered.await(10,TimeUnit.SECONDS)) {first.get(1,TimeUnit.SECONDS).getOrThrow();error("relocation did not reach transaction hold")}
            val second=r.worker("second") {r.warehouse.delete("branch");"deleted"}
            r.awaitSecondProgress(second);release.countDown()
            val saved=first.get(15,TimeUnit.SECONDS);val deleted=second.get(15,TimeUnit.SECONDS)
            if(rollback) {assertTrue(saved.isFailure);deleted.getOrThrow();assertEquals(scenario.before,scenario.state());r.db {assertNull(WarehouseDao.findById("branch"))}}
            else {scenario.verifySaved(saved.getOrThrow());assertTrue(deleted.exceptionOrNull() is BadRequestException);r.db {assertNotNull(WarehouseDao.findById("branch"))}}
        } finally {release.countDown()}
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.CsvSource("plan,missing","plan,foreign","plan,cycle","receipt,missing","receipt,foreign","receipt,cycle")
    fun `incident relocation rejects inconsistent location ancestry without changing facts`(operation:String,kind:String)=fixture {r->
        val scenario=relocationAtNewLocation(r,operation)
        r.db {
            when(kind) {
                "missing"->WarehouseDao["branch"].parentId="missing"
                "foreign"->{WarehouseDao.new("foreign-parent") {orgId="other";name="外馆父级";sceneType=EnumLocationScene.EXHIBITION};WarehouseDao["branch"].parentId="foreign-parent"}
                "cycle"->WarehouseDao["branch"].parentId="branch"
            }
        }
        assertThrows(BadRequestException::class.java) {scenario.save()}
        assertEquals(scenario.before,scenario.state())
    }

    @ParameterizedTest @ValueSource(strings=["plan","receipt"])
    fun `incident relocation refreshes inactive location while preserving actual receipt facts`(operation:String)=fixture {r->
        val scenario=relocationAtNewLocation(r,operation)
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val first=r.worker("first") {r.db {
            CollectionLocationMutation.acquire("museum");WarehouseDao["branch"].status=EnumContentStatus.DELETE;WarehouseDao["branch"].flush()
            entered.countDown();check(release.await(15,TimeUnit.SECONDS));"changed"
        }}
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS))
            val second=r.worker("second") {r.db {assertEquals(EnumContentStatus.NORMAL,WarehouseDao["branch"].status);scenario.save()}}
            r.awaitSecondProgress(second);release.countDown();first.get(15,TimeUnit.SECONDS).getOrThrow()
            val result=second.get(15,TimeUnit.SECONDS)
            if(operation=="plan") {assertTrue(result.exceptionOrNull() is BadRequestException);assertEquals(scenario.before,scenario.state())}
            else scenario.verifySaved(result.getOrThrow())
            r.db {assertEquals(EnumContentStatus.DELETE,WarehouseDao["branch"].status)}
        } finally {release.countDown()}
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.CsvSource("plan,blank","plan,spaces","receipt,blank","receipt,spaces")
    fun `incident relocation requires an exact retained location identity`(operation:String,kind:String)=fixture {r->
        val scenario=relocationAtNewLocation(r,operation,if(kind=="blank")""else" branch ")
        assertThrows(BadRequestException::class.java) {scenario.save()}
        assertEquals(scenario.before,scenario.state())
    }

    private fun designatedCustodyAtNewLocation(r:Runtime,correction:Boolean=false,locationId:String="branch"):LocationWriteScenario {
        val scope=r.c.getBean(CollectionScopeResolver::class.java)
        val binder=mock(BusinessAttachmentBinder::class.java).also {b->`when`(b.resolveSingle(anyString())).thenAnswer {it.getArgument<String>(0)}}
        val archives=CollectionPermanentArchiveService(binder,scope,readAccess=unprotectedArchiveReadAccessProvider())
        val service=r.c.autowireCapableBeanFactory.initializeBean(DesignatedReceptionService(scope,r.c.getBean(CollectionResponsibleUserService::class.java),archives),"designatedLocationService") as DesignatedReceptionService
        assertTrue(org.springframework.aop.support.AopUtils.isAopProxy(service));assertFalse(mockingDetails(service).isMock)
        val at=java.time.LocalDateTime.now().minusDays(2).withNano(123000000)
        val file="test-assignment"
        r.db {actorRole("k1","DESIGNATED_LOCATION",setOf(CollectionConst.ACQUIRE));CollectionOrgDao["museum"].apply {systemGroupId="museum";ownership=EnumOwnership.STATE}}
        val notice=DesignatedNoticeVo(orgId="museum",authorityLevel=EnumAuthorityLevel.PROVINCE,authorityCode="province",authorityName="合成指定部门",documentNo="指定测试-1",issuedOn=at.toLocalDate().minusDays(1),notifiedOn=at.toLocalDate().minusDays(1),designatedReceiverName="测试馆",senderKind=EnumDesignatedSender.ARCHAEOLOGY,senderName="合成移交单位",senderIdentity="sender",transferLegalBasis="合成指定决定及移交依据",documentFileIds=file,item=DesignatedItemVo("source-piece","合成移交物",EnumCollectionNature.OTHER,1,"件",EnumCompleteness.COMPLETE,"实际现状",file))
        val case=service.create(notice)
        val old=DesignatedCustodyVo(at,"k1","target","k1",setOf("合成移交人"),listOf(AccessionPieceVo("piece-1",at.minusHours(1),"实物特征","保存状况",true,true,file)),file)
        val previous=if(correction)service.custody(case,old)else null
        val value=old.copy(checkedAt=at.plusHours(1),warehouseId=locationId,previousId=previous,correctionReason=if(correction)"核实实际保管地点"else null,correctionFileIds=if(correction)file else null)
        val state:()->List<Any?> = {r.db {listOf(
            DesignatedReceptionDao[case].acquisitionId,
            DesignatedReceptionRecordDao.all().map {listOf(it.id.value,it.content,it.recordedAt)},
            CollectionPermanentArchiveDao.all().map {it.id.value to it.digest},
            CollectionPermanentArchiveFileDao.all().map {it.id.value to it.metadata})}}
        val verifySaved:(String)->Unit={id->r.db {
            DesignatedReceptionEvidence.verify(DesignatedReceptionDao[case])
            val row=DesignatedReceptionRecordDao[id]
            assertEquals(EnumDesignatedRecord.CUSTODY,row.kind);assertEquals(value,DesignatedReceptionEvidence.value<DesignatedCustodyVo>(row))
            assertEquals(if(correction)2 else 1,DesignatedReceptionRecordDao.all().count {it.kind==EnumDesignatedRecord.CUSTODY})
            if(previous!=null)assertEquals(old,DesignatedReceptionEvidence.value<DesignatedCustodyVo>(DesignatedReceptionRecordDao[previous]))
            val archivesBefore=CollectionPermanentArchiveDao.all().map {it.id.value to it.digest}
            assertEquals(id,service.custody(case,value))
            assertEquals(archivesBefore,CollectionPermanentArchiveDao.all().map {it.id.value to it.digest})
        }}
        return LocationWriteScenario({service.custody(case,value)},verifySaved,state,state())
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.CsvSource("false,false","false,true","true,false","true,true")
    fun `designated custody waits for location deletion and preserves earlier originals`(correction:Boolean,rollback:Boolean)=fixture {r->
        val scenario=designatedCustodyAtNewLocation(r,correction)
        val entered=CountDownLatch(1);val release=CountDownLatch(1);r.pauseFirstCheck(entered,release)
        val first=r.worker("first") {r.db {r.warehouse.delete("branch");if(rollback)error("rollback deletion");"deleted"}}
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS))
            val second=r.worker("second") {r.db {WarehouseDao["branch"].name;scenario.save()}}
            r.awaitSecondProgress(second);release.countDown()
            val deleted=first.get(15,TimeUnit.SECONDS);val saved=second.get(15,TimeUnit.SECONDS)
            if(rollback) {assertTrue(deleted.isFailure);scenario.verifySaved(saved.getOrThrow());r.db {assertNotNull(WarehouseDao.findById("branch"))}}
            else {deleted.getOrThrow();assertTrue(saved.exceptionOrNull() is BadRequestException);assertEquals(scenario.before,scenario.state());r.db {assertNull(WarehouseDao.findById("branch"))}}
        } finally {release.countDown()}
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.CsvSource("false,false","false,true","true,false","true,true")
    fun `designated custody reference commit or rollback coordinates location deletion`(correction:Boolean,rollback:Boolean)=fixture {r->
        val scenario=designatedCustodyAtNewLocation(r,correction)
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val first=r.worker("first") {r.db {
            val id=scenario.save();entered.countDown();check(release.await(15,TimeUnit.SECONDS));if(rollback)error("rollback custody");id
        }}
        try {
            if(!entered.await(10,TimeUnit.SECONDS)) {first.get(1,TimeUnit.SECONDS).getOrThrow();error("custody did not reach transaction hold")}
            val second=r.worker("second") {r.warehouse.delete("branch");"deleted"}
            r.awaitSecondProgress(second);release.countDown()
            val saved=first.get(15,TimeUnit.SECONDS);val deleted=second.get(15,TimeUnit.SECONDS)
            if(rollback) {assertTrue(saved.isFailure);deleted.getOrThrow();assertEquals(scenario.before,scenario.state());r.db {assertNull(WarehouseDao.findById("branch"))}}
            else {scenario.verifySaved(saved.getOrThrow());assertTrue(deleted.exceptionOrNull() is BadRequestException);r.db {assertNotNull(WarehouseDao.findById("branch"))}}
        } finally {release.countDown()}
    }

    @ParameterizedTest @ValueSource(strings=["missing","foreign","cycle"])
    fun `designated custody rejects invalid parent chains without replacing the earlier version`(kind:String)=fixture {r->
        val scenario=designatedCustodyAtNewLocation(r,correction=true)
        r.db {
            when(kind) {
                "missing"->WarehouseDao["branch"].parentId="missing"
                "foreign"->{WarehouseDao.new("foreign-parent") {orgId="other";name="外馆父级";sceneType=EnumLocationScene.STORAGE};WarehouseDao["branch"].parentId="foreign-parent"}
                "cycle"->WarehouseDao["branch"].parentId="branch"
            }
        }
        assertThrows(BadRequestException::class.java) {scenario.save()}
        assertEquals(scenario.before,scenario.state())
    }

    @ParameterizedTest @ValueSource(strings=["foreign","exhibition"])
    fun `designated custody rechecks a cached new location after a committed change`(kind:String)=fixture {r->
        val scenario=designatedCustodyAtNewLocation(r,correction=true)
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val first=r.worker("first") {r.db {
            CollectionLocationMutation.acquire("museum")
            val w=WarehouseDao["branch"]
            if(kind=="foreign")w.orgId="other"else w.sceneType=EnumLocationScene.EXHIBITION
            w.flush();entered.countDown();check(release.await(15,TimeUnit.SECONDS));"changed"
        }}
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS))
            val second=r.worker("second") {r.db {
                assertEquals("museum",WarehouseDao["branch"].orgId);assertEquals(EnumLocationScene.STORAGE,WarehouseDao["branch"].sceneType)
                scenario.save()
            }}
            r.awaitSecondProgress(second);release.countDown();first.get(15,TimeUnit.SECONDS).getOrThrow()
            assertTrue(second.get(15,TimeUnit.SECONDS).exceptionOrNull() is BadRequestException)
            assertEquals(scenario.before,scenario.state())
        } finally {release.countDown()}
    }

    @Test fun `designated custody rejects a retired correction location and preserves earlier originals`()=fixture {r->
        val scenario=designatedCustodyAtNewLocation(r,correction=true)
        r.db {WarehouseDao["branch"].status=EnumContentStatus.DELETE}
        val error=assertThrows(BadRequestException::class.java) {scenario.save()}
        assertTrue(error.message.orEmpty().contains("库位已退役"),error.message)
        assertEquals(scenario.before,scenario.state())
    }

    @ParameterizedTest @ValueSource(strings=[""," branch "])
    fun `designated custody rejects invalid retained identities without new originals`(locationId:String)=fixture {r->
        val scenario=designatedCustodyAtNewLocation(r,correction=true,locationId=locationId)
        assertThrows(BadRequestException::class.java) {scenario.save()}
        assertEquals(scenario.before,scenario.state())
    }

    private fun requisitionAtNewLocation(r:Runtime,update:Boolean=false,locationId:String?="branch"):LocationWriteScenario {
        val scope=r.c.getBean(CollectionScopeResolver::class.java)
        val binder=r.c.getBean(BusinessAttachmentBinder::class.java)
        val archives=r.c.getBean(CollectionPermanentArchiveService::class.java)
        val service=r.c.autowireCapableBeanFactory.initializeBean(RequisitionService(
            r.c.getBean(FlowInstanceService::class.java),scope,r.collections,r.c.getBean(CollectionUserResolver::class.java),binder,
            r.c.getBean(CollectionReturnService::class.java),r.c.getBean(CollectionBusinessItemService::class.java),r.c.getBean(CollectionOperationLockService::class.java),
            r.c.getBean(CollectionPlacementService::class.java),RequisitionStatutoryService(scope,r.c.getBean(CollectionResponsibleUserService::class.java),binder,archives),archives),"requisitionLocationService") as RequisitionService
        assertTrue(org.springframework.aop.support.AopUtils.isAopProxy(service));assertFalse(mockingDetails(service).isMock)
        r.db {CollectionDao.new("requisition-piece") {orgId="museum";name="合成提用实物";totalRegNo="TY-1";lifeStatus=EnumCollectionStatus.IN_STORE;warehouseId="target"}}
        val old=RequisitionVo(collectionIds="requisition-piece",purpose=EnumMoveReason.RESEARCH,basis="原提用依据",operator="k1",toWarehouseId="target")
        val previous=if(update)service.save(old)else null
        val input=old.copy(id=previous,toWarehouseId=locationId,basis="核实后的提用依据")
        val state:()->List<Any?> = {r.db {listOf(
            CollectionDao["requisition-piece"].warehouseId,CollectionDao["requisition-piece"].lifeStatus,
            RequisitionDao.all().map {listOf(it.id.value,it.toWarehouseId,it.basis,it.baseline,it.flowStatus)},
            CollectionBusinessItemDao.all().map {listOf(it.id.value,it.orgId,it.bizId,it.collectionId)},
            StorageMovementDao.all().map {it.id.value})}}
        val verifySaved:(String)->Unit={id->r.db {
            val row=RequisitionDao[id]
            assertEquals(locationId?.trim()?.takeIf(String::isNotBlank),row.toWarehouseId)
            assertEquals(input.basis,row.basis);assertEquals(EnumFlowStatus.DRAFT,row.flowStatus)
            assertEquals("requisition-piece",row.collectionIds);assertNotNull(row.baseline)
            assertEquals(listOf("requisition-piece"),r.c.getBean(CollectionBusinessItemService::class.java).collectionIdsForBusiness(CollectionConst.BIZ_REQUISITION,id))
            assertEquals("target",CollectionDao["requisition-piece"].warehouseId);assertEquals(EnumCollectionStatus.IN_STORE,CollectionDao["requisition-piece"].lifeStatus)
            assertEquals(0L,StorageMovementDao.all().count());assertEquals(1L,RequisitionDao.all().count())
            if(previous!=null)assertEquals(previous,id)
        }}
        return LocationWriteScenario({service.save(input)},verifySaved,state,state())
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.CsvSource("false,false","false,true","true,false","true,true")
    fun `requisition draft waits for return location deletion without changing earlier fields`(update:Boolean,rollback:Boolean)=fixture {r->
        val scenario=requisitionAtNewLocation(r,update)
        val entered=CountDownLatch(1);val release=CountDownLatch(1);r.pauseFirstCheck(entered,release)
        val first=r.worker("first") {r.db {r.warehouse.delete("branch");if(rollback)error("rollback deletion");"deleted"}}
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS))
            val second=r.worker("second") {r.db {WarehouseDao["branch"].name;scenario.save()}}
            r.awaitSecondProgress(second);release.countDown()
            val deleted=first.get(15,TimeUnit.SECONDS);val saved=second.get(15,TimeUnit.SECONDS)
            if(rollback) {assertTrue(deleted.isFailure);scenario.verifySaved(saved.getOrThrow());r.db {assertNotNull(WarehouseDao.findById("branch"))}}
            else {deleted.getOrThrow();assertTrue(saved.exceptionOrNull() is BadRequestException,saved.toString());assertEquals(scenario.before,scenario.state());r.db {assertNull(WarehouseDao.findById("branch"))}}
        } finally {release.countDown()}
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.CsvSource("false,false","false,true","true,false","true,true")
    fun `requisition draft return reference commit or rollback coordinates deletion`(update:Boolean,rollback:Boolean)=fixture {r->
        val scenario=requisitionAtNewLocation(r,update)
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val first=r.worker("first") {r.db {
            val id=scenario.save();org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().entityCache.flush()
            entered.countDown();check(release.await(15,TimeUnit.SECONDS));if(rollback)error("rollback requisition");id
        }}
        try {
            if(!entered.await(10,TimeUnit.SECONDS)) {first.get(1,TimeUnit.SECONDS).getOrThrow();error("requisition did not reach transaction hold")}
            val second=r.worker("second") {r.warehouse.delete("branch");"deleted"}
            r.awaitSecondProgress(second);release.countDown()
            val saved=first.get(15,TimeUnit.SECONDS);val deleted=second.get(15,TimeUnit.SECONDS)
            if(rollback) {assertTrue(saved.isFailure);deleted.getOrThrow();assertEquals(scenario.before,scenario.state());r.db {assertNull(WarehouseDao.findById("branch"))}}
            else {scenario.verifySaved(saved.getOrThrow());assertTrue(deleted.exceptionOrNull() is BadRequestException,deleted.toString());r.db {assertNotNull(WarehouseDao.findById("branch"))}}
        } finally {release.countDown()}
    }

    @ParameterizedTest @ValueSource(strings=["missing","foreign","cycle"])
    fun `requisition draft invalid return parent preserves prior draft and item bindings`(kind:String)=fixture {r->
        val scenario=requisitionAtNewLocation(r,update=true)
        r.db {
            when(kind) {
                "missing"->WarehouseDao["branch"].parentId="missing"
                "foreign"->{WarehouseDao.new("foreign-parent") {orgId="other";name="外馆父级";sceneType=EnumLocationScene.STORAGE};WarehouseDao["branch"].parentId="foreign-parent"}
                "cycle"->WarehouseDao["branch"].parentId="branch"
            }
        }
        assertThrows(BadRequestException::class.java) {scenario.save()}
        assertEquals(scenario.before,scenario.state())
    }

    @ParameterizedTest @ValueSource(strings=["foreign","exhibition","retired"])
    fun `requisition draft refreshes cached return location after a committed change`(kind:String)=fixture {r->
        val scenario=requisitionAtNewLocation(r,update=true)
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val first=r.worker("first") {r.db {
            CollectionLocationMutation.acquire("museum")
            val w=WarehouseDao["branch"]
            when(kind) {"foreign"->w.orgId="other";"exhibition"->w.sceneType=EnumLocationScene.EXHIBITION;"retired"->w.status=EnumContentStatus.DELETE}
            w.flush();entered.countDown();check(release.await(15,TimeUnit.SECONDS));"changed"
        }}
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS))
            val second=r.worker("second") {r.db {
                assertEquals("museum",WarehouseDao["branch"].orgId);assertEquals(EnumLocationScene.STORAGE,WarehouseDao["branch"].sceneType)
                assertEquals(EnumContentStatus.NORMAL,WarehouseDao["branch"].status)
                scenario.save()
            }}
            r.awaitSecondProgress(second);release.countDown();first.get(15,TimeUnit.SECONDS).getOrThrow()
            assertTrue(second.get(15,TimeUnit.SECONDS).exceptionOrNull() is BadRequestException)
            assertEquals(scenario.before,scenario.state())
        } finally {release.countDown()}
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.CsvSource("false,null","false,blank","true,null","true,blank")
    fun `requisition draft permits an unselected or cleared return location`(update:Boolean,kind:String)=fixture {r->
        val scenario=requisitionAtNewLocation(r,update,if(kind=="null")null else"  ")
        scenario.verifySaved(scenario.save())
    }
    @Test fun `requisition draft stores the normalized identity of the selected return location`()=fixture {r->
        val scenario=requisitionAtNewLocation(r,update=true,locationId=" branch ")
        scenario.verifySaved(scenario.save())
    }
    @Test fun `requisition draft rejects a retired return location without changing its prior bindings`()=fixture {r->
        val scenario=requisitionAtNewLocation(r,update=true)
        r.db {WarehouseDao["branch"].status=EnumContentStatus.DELETE}
        val error=assertThrows(BadRequestException::class.java) {scenario.save()}
        assertTrue(error.message.orEmpty().contains("库位已退役"),error.message)
        assertEquals(scenario.before,scenario.state())
    }

    private fun catalogAtNewLocation(r:Runtime,operation:String,locationId:String?="branch"):LocationWriteScenario {
        val service=r.c.autowireCapableBeanFactory.initializeBean(CollectionCatalogService(
            r.c.getBean(CollectionScopeResolver::class.java),r.c.getBean(CollectionOperationLockService::class.java),mock(CollectionGradingService::class.java),
            r.c.getBean(AccessionReceiptService::class.java),r.c.getBean(BusinessAttachmentBinder::class.java)),"catalogLocationService") as CollectionCatalogService
        assertTrue(org.springframework.aop.support.AopUtils.isAopProxy(service));assertFalse(mockingDetails(service).isMock)
        val input=r.db {
            originalReader("catalog-writer",CollectionConst.CATALOG_EDIT)
            actorRole("catalog-writer","CATALOG_LOCATION",setOf(CollectionConst.CATALOG_AUDIT,CollectionConst.LEDGER_CREATE,CollectionConst.SELECT))
            CollectionOrgDao["museum"].systemGroupId="museum"
            val c=CollectionDao.new("catalog-piece") {orgId="museum";name="合成编目实物";totalRegNo="BM-1";lifeStatus=EnumCollectionStatus.PENDING;warehouseId="target"
                catalogStatus=EnumCatalogStatus.DRAFT
                categoryId="category";era="当代";texture="纸";level=EnumRelicLevel.GENERAL;quantity=1;unit="件";source=EnumSource.OLD;acquireDate=java.time.LocalDate.of(2026,9,1);completeness=EnumCompleteness.COMPLETE}
            service.ensureCatalogFor(c)
            CollectionCatalogMapper.fromCollection(c.toModel()).copy(warehouseId=locationId,remark="核实后的编目内容")
        }
        fun catalog()=CollectionCatalogDao.find {CollectionCatalogs.collectionId eq "catalog-piece"}.single()
        if(operation in setOf("submit","approve","reject"))r.db {
            val row=catalog();row.content=input
            if(operation!="submit") {
                row.catalogStatus=EnumCatalogStatus.SUBMITTED;row.version=1;row.submittedVersion=1;row.submittedBy="previous-editor"
                CollectionDao["catalog-piece"].catalogStatus=EnumCatalogStatus.SUBMITTED
                CollectionCatalogVersionDao.new {orgId="museum";catalogId=row.id.value;collectionId="catalog-piece";version=1;catalogStatus=EnumCatalogStatus.SUBMITTED;content=input;submittedBy="previous-editor"}
            }
        }
        val hash=r.db {CollectionCatalogConfirmation.hash(catalog(),CollectionDao["catalog-piece"])}
        val save:()->String={
            val previous=SecurityContextHolder.getContext()
            val user=SecurityUser(id="catalog-writer",name="编目登记员",username="catalog-writer",password="",groupId="museum",groupIds=setOf("museum"),postIds=emptySet(),dataType=0,roles=emptySet(),authorities=listOf(CollectionConst.CATALOG_EDIT,CollectionConst.CATALOG_AUDIT,CollectionConst.LEDGER_CREATE,CollectionConst.SELECT).map(::SimpleGrantedAuthority),enabled=true)
            SecurityContextHolder.setContext(SecurityContextHolder.createEmptyContext().apply {authentication=UsernamePasswordAuthenticationToken(user,null,user.authorities)})
            try {when(operation) {
            "confirmed","confirm-submit"->service.saveConfirmed("catalog-piece",input,hash,operation=="confirm-submit").collectionId!!
            "import"->service.saveFromImport("catalog-piece",input,"museum")
            "submit"->service.submit("catalog-piece",hash).toString()
            "approve","reject"->service.review("catalog-piece",CollectionCatalogReviewVo(if(operation=="approve")EnumCatalogStatus.APPROVED else EnumCatalogStatus.REJECTED,"核对本版资料"),hash)
            else->service.save("catalog-piece",input)
            }} finally {SecurityContextHolder.setContext(previous)}
        }
        val state:()->List<Any?> = {r.db {listOf<Any?>(
            CollectionDao["catalog-piece"].toModel(),CollectionCatalogDao.all().map {listOf(it.id.value,it.content,it.catalogStatus,it.version,it.submittedVersion,it.submittedBy,it.auditBy,it.auditTime,it.auditOpinion)},CollectionCatalogVersionDao.all().map {it.toModel()},
            CollectionLedgerLogDao.all().map {it.id.value},StorageMovementDao.all().map {it.id.value})}}
        val verifySaved:(String)->Unit={_->r.db {
            val row=catalog()
            val expectedStatus=when(operation) {"confirmed","save","import"->EnumCatalogStatus.DRAFT;"reject"->EnumCatalogStatus.REJECTED;"approve"->EnumCatalogStatus.APPROVED;else->EnumCatalogStatus.SUBMITTED}
            assertEquals(expectedStatus,row.catalogStatus)
            assertEquals(if(operation in setOf("submit","approve","reject"))input else input.copy(warehouseId=locationId?.trim()?.takeIf(String::isNotBlank)),row.content)
            if(operation!="approve") {assertEquals("target",CollectionDao["catalog-piece"].warehouseId);assertEquals(EnumCollectionStatus.PENDING,CollectionDao["catalog-piece"].lifeStatus);assertNull(CollectionDao["catalog-piece"].registeredAt)}
            else {assertEquals("target",CollectionDao["catalog-piece"].warehouseId,"编目审核不能代替实际移动");assertEquals(EnumCollectionStatus.IN_STORE,CollectionDao["catalog-piece"].lifeStatus);assertNotNull(CollectionDao["catalog-piece"].registeredAt)}
            val ledger = CollectionLedgerLogDao.all().toList()
            assertEquals(when(operation) {"approve"->3;"reject","submit","confirm-submit"->1;else->0},ledger.size)
            if(operation in setOf("submit","confirm-submit")) {
                val transition=ledger.single()
                assertEquals(EnumLedgerAction.CORRECT,transition.action)
                assertEquals("catalogStatus",transition.field)
                assertEquals(EnumCatalogStatus.DRAFT.name,transition.before)
                assertEquals(EnumCatalogStatus.SUBMITTED.name,transition.after)
                assertEquals(row.id.value,transition.refBizId)
            }
            assertEquals(if(expectedStatus==EnumCatalogStatus.DRAFT)0L else 1L,CollectionCatalogVersionDao.all().count())
            CollectionCatalogVersionDao.all().forEach {assertEquals(row.content,it.content)}
            assertEquals(0L,StorageMovementDao.all().count())
        }}
        return LocationWriteScenario(save,verifySaved,state,state())
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.CsvSource("save,false","save,true","confirmed,false","confirmed,true","confirm-submit,false","confirm-submit,true","import,false","import,true")
    fun `catalog location new references wait for deletion commit or rollback`(operation:String,rollback:Boolean)=fixture {r->
        val scenario=catalogAtNewLocation(r,operation)
        val entered=CountDownLatch(1);val release=CountDownLatch(1);r.pauseFirstCheck(entered,release)
        val first=r.worker("first") {r.db {r.warehouse.delete("branch");if(rollback)error("rollback deletion");"deleted"}}
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS))
            val second=r.worker("second") {r.db {WarehouseDao["branch"].name;scenario.save()}}
            r.awaitSecondProgress(second);release.countDown()
            val deleted=first.get(15,TimeUnit.SECONDS);val saved=second.get(15,TimeUnit.SECONDS)
            if(rollback) {assertTrue(deleted.isFailure);scenario.verifySaved(saved.getOrThrow());r.db {assertNotNull(WarehouseDao.findById("branch"))}}
            else {deleted.getOrThrow();assertTrue(saved.exceptionOrNull() is BadRequestException,saved.toString());assertEquals(scenario.before,scenario.state());r.db {assertNull(WarehouseDao.findById("branch"))}}
        } finally {release.countDown()}
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.CsvSource("save,false","save,true","confirmed,false","confirmed,true","confirm-submit,false","confirm-submit,true","import,false","import,true")
    fun `catalog location reference commit or rollback coordinates deletion`(operation:String,rollback:Boolean)=fixture {r->
        val scenario=catalogAtNewLocation(r,operation)
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val first=r.worker("first") {r.db {
            val id=scenario.save();org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().entityCache.flush()
            entered.countDown();check(release.await(15,TimeUnit.SECONDS));if(rollback)error("rollback catalog");id
        }}
        try {
            if(!entered.await(10,TimeUnit.SECONDS)) {first.get(1,TimeUnit.SECONDS).getOrThrow();error("catalog did not reach transaction hold")}
            val second=r.worker("second") {r.warehouse.delete("branch");"deleted"}
            r.awaitSecondProgress(second);release.countDown()
            val saved=first.get(15,TimeUnit.SECONDS);val deleted=second.get(15,TimeUnit.SECONDS)
            if(rollback) {assertTrue(saved.isFailure);deleted.getOrThrow();assertEquals(scenario.before,scenario.state());r.db {assertNull(WarehouseDao.findById("branch"))}}
            else {scenario.verifySaved(saved.getOrThrow());assertTrue(deleted.exceptionOrNull() is BadRequestException,deleted.toString());r.db {assertNotNull(WarehouseDao.findById("branch"))}}
        } finally {release.countDown()}
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.CsvSource("submit,missing","submit,foreign","submit,exhibition","approve,missing","approve,foreign","approve,exhibition")
    fun `catalog location invalid historical content cannot submit or approve a version`(operation:String,kind:String)=fixture {r->
        val scenario=catalogAtNewLocation(r,operation,if(kind=="missing")"missing"else"branch")
        if(kind!="missing")r.db {if(kind=="foreign")WarehouseDao["branch"].orgId="other"else WarehouseDao["branch"].sceneType=EnumLocationScene.EXHIBITION}
        assertThrows(BadRequestException::class.java) {scenario.save()}
        assertEquals(scenario.before,scenario.state())
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.CsvSource("save,missing","save,foreign","save,cycle","submit,missing","submit,foreign","submit,cycle","approve,missing","approve,foreign","approve,cycle")
    fun `catalog location invalid parent prevents draft snapshot and approval writes`(operation:String,kind:String)=fixture {r->
        val scenario=catalogAtNewLocation(r,operation)
        r.db {when(kind) {
            "missing"->WarehouseDao["branch"].parentId="missing"
            "foreign"->{WarehouseDao.new("foreign-parent") {orgId="other";name="外馆父级";sceneType=EnumLocationScene.STORAGE};WarehouseDao["branch"].parentId="foreign-parent"}
            "cycle"->WarehouseDao["branch"].parentId="branch"
        }}
        val error=assertThrows(BadRequestException::class.java) {scenario.save()}
        assertTrue(error.message.orEmpty().contains("位置父级"),error.message)
        assertEquals(scenario.before,scenario.state())
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.CsvSource("save,foreign","save,exhibition","submit,foreign","submit,exhibition","approve,foreign","approve,exhibition","save,retired","submit,retired","approve,retired")
    fun `catalog location refreshes cached identity after a committed location change`(operation:String,kind:String)=fixture {r->
        val scenario=catalogAtNewLocation(r,operation)
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val first=r.worker("first") {r.db {
            CollectionLocationMutation.acquire("museum")
            val w=WarehouseDao["branch"]
            when(kind) {"foreign"->w.orgId="other";"exhibition"->w.sceneType=EnumLocationScene.EXHIBITION;"retired"->w.status=EnumContentStatus.DELETE}
            w.flush();entered.countDown();check(release.await(15,TimeUnit.SECONDS));"changed"
        }}
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS))
            val second=r.worker("second") {r.db {
                assertEquals("museum",WarehouseDao["branch"].orgId);assertEquals(EnumLocationScene.STORAGE,WarehouseDao["branch"].sceneType)
                assertEquals(EnumContentStatus.NORMAL,WarehouseDao["branch"].status)
                scenario.save()
            }}
            r.awaitSecondProgress(second);release.countDown();first.get(15,TimeUnit.SECONDS).getOrThrow()
            val result=second.get(15,TimeUnit.SECONDS)
            assertTrue(result.exceptionOrNull() is BadRequestException,result.toString())
            assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("库位"),result.toString())
            assertEquals(scenario.before,scenario.state())
        } finally {release.countDown()}
    }

    @ParameterizedTest @ValueSource(strings=["missing","foreign","exhibition","padded"])
    fun `catalog location rejection preserves the submitted evidence despite an invalid location`(kind:String)=fixture {r->
        val scenario=catalogAtNewLocation(r,"reject",when(kind) {"missing"->"missing";"padded"->" branch ";else->"branch"})
        r.db {when(kind) {"foreign"->WarehouseDao["branch"].orgId="other";"exhibition"->WarehouseDao["branch"].sceneType=EnumLocationScene.EXHIBITION}}
        scenario.verifySaved(scenario.save())
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.CsvSource("save,null","save,blank","import,null","import,blank","submit,null","submit,blank")
    fun `catalog location unselected drafts retain existing completeness rules`(operation:String,kind:String)=fixture {r->
        val scenario=catalogAtNewLocation(r,operation,if(kind=="null")null else"  ")
        scenario.verifySaved(scenario.save())
    }
    @ParameterizedTest @ValueSource(strings=["save","confirmed","import"])
    fun `catalog location draft saves normalize a selected identity`(operation:String)=fixture {r->
        val scenario=catalogAtNewLocation(r,operation," branch ")
        scenario.verifySaved(scenario.save())
    }
    @ParameterizedTest @ValueSource(strings=["submit","approve"])
    fun `catalog location padded legacy identity requires draft correction before snapshot or approval`(operation:String)=fixture {r->
        val scenario=catalogAtNewLocation(r,operation," branch ")
        val error=assertThrows(BadRequestException::class.java) {scenario.save()}
        assertTrue(error.message.orEmpty().contains("库位编号不一致"),error.message)
        assertEquals(scenario.before,scenario.state())
    }
    @ParameterizedTest @ValueSource(strings=["save","submit","approve"])
    fun `catalog location rejects a retired position without replacing its factual records`(operation:String)=fixture {r->
        val scenario=catalogAtNewLocation(r,operation)
        r.db {WarehouseDao["branch"].status=EnumContentStatus.DELETE}
        val error=assertThrows(BadRequestException::class.java) {scenario.save()}
        assertTrue(error.message.orEmpty().contains("库位已退役"),error.message)
        assertEquals(scenario.before,scenario.state())
    }
    @Test fun `catalog location valid approval registers the verified version without moving the collection`()=fixture {r->
        val scenario=catalogAtNewLocation(r,"approve")
        scenario.verifySaved(scenario.save())
    }

    @Test fun `child created during parent move follows the committed parent path`() = fixture { r ->
        val entered=CountDownLatch(1);val release=CountDownLatch(1);r.pauseFirstCheck(entered,release)
        val first=r.worker("first") { r.warehouse.update("branch",WarehouseVo(parentId="target")) }
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS))
            val second=r.worker("second") { r.db {
                assertEquals("0,",WarehouseDao["branch"].parentIds) // 模拟批量入口提前缓存父节点。
                r.warehouse.create(WarehouseVo(name="新下级",parentId="branch",type=EnumWarehouseType.AREA))
            } }
            r.awaitSecondProgress(second);release.countDown()
            first.get(15,TimeUnit.SECONDS).getOrThrow();val child=second.get(15,TimeUnit.SECONDS).getOrThrow()
            r.db { assertEquals("0,target,branch,",WarehouseDao[child].parentIds) }
        } finally { release.countDown() }
    }
    @Test fun `concurrent reciprocal moves cannot commit a parent cycle`() = fixture { r ->
        val entered=CountDownLatch(1);val release=CountDownLatch(1);r.pauseFirstCheck(entered,release)
        val first=r.worker("first") { r.warehouse.update("branch",WarehouseVo(parentId="target")) }
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS))
            val second=r.worker("second") { r.warehouse.update("target",WarehouseVo(parentId="branch")) }
            r.awaitSecondProgress(second);release.countDown()
            val results=listOf(first.get(15,TimeUnit.SECONDS),second.get(15,TimeUnit.SECONDS))
            assertEquals(1,results.count { it.isSuccess })
            assertTrue(results[1].exceptionOrNull() is BadRequestException)
            r.db { assertFalse(WarehouseDao["branch"].parentId=="target" && WarehouseDao["target"].parentId=="branch") }
        } finally { release.countDown() }
    }
    @Test fun `new collection cannot commit a reference while its location is being removed`() = fixture { r ->
        val entered=CountDownLatch(1);val release=CountDownLatch(1);r.pauseFirstCheck(entered,release)
        val first=r.worker("first") { r.warehouse.delete("branch");"deleted" }
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS))
            val second=r.worker("second") { r.collections.createForOrg("museum",CollectionVo(name="测试藏品",totalRegNo="A1",acquireDate=java.time.LocalDate.of(2026,9,10),warehouseId="branch")) }
            r.awaitSecondProgress(second);release.countDown()
            val outcomes=listOf(first.get(15,TimeUnit.SECONDS),second.get(15,TimeUnit.SECONDS))
            assertTrue(outcomes[0].isSuccess)
            assertEquals("库位不存在", outcomes[1].exceptionOrNull()?.message)
            assertEquals(1,outcomes.count { it.isSuccess })
            r.db { assertTrue(WarehouseDao.findById("branch")!=null || CollectionDao.all().none { it.warehouseId=="branch" }) }
        } finally { release.countDown() }
    }
    @Test fun `a committed new collection prevents the waiting location removal`() = fixture { r ->
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val first=r.worker("first") { r.db {
            val id=r.collections.createForOrg("museum",CollectionVo(name="藏品",totalRegNo="A2",acquireDate=java.time.LocalDate.of(2026,9,10),warehouseId="branch"))
            entered.countDown();check(release.await(15,TimeUnit.SECONDS));id
        } }
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS))
            val second=r.worker("second") { r.warehouse.delete("branch");"deleted" }
            r.awaitSecondProgress(second);release.countDown()
            val id=first.get(15,TimeUnit.SECONDS).getOrThrow()
            val error=second.get(15,TimeUnit.SECONDS).exceptionOrNull()
            assertTrue(error is BadRequestException, error.toString())
            assertTrue(error!!.message.orEmpty().contains("引用"),error.message)
            r.db { assertEquals("branch",CollectionDao[id].warehouseId);assertNotNull(WarehouseDao.findById("branch")) }
        } finally { release.countDown() }
    }
    @Test fun `a rolled back new reference releases its lock without blocking a valid removal`() = fixture { r ->
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val first=r.worker("first") { r.db {
            r.collections.createForOrg("museum",CollectionVo(name="藏品",totalRegNo="A3",acquireDate=java.time.LocalDate.of(2026,9,10),warehouseId="branch"))
            entered.countDown();check(release.await(15,TimeUnit.SECONDS));throw IllegalStateException("test rollback")
        } }
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS))
            val second=r.worker("second") { r.warehouse.delete("branch");"deleted" }
            r.awaitSecondProgress(second);release.countDown()
            assertEquals("test rollback",first.get(15,TimeUnit.SECONDS).exceptionOrNull()?.message)
            second.get(15,TimeUnit.SECONDS).getOrThrow()
            r.db { assertNull(WarehouseDao.findById("branch"));assertTrue(CollectionDao.all().none()) }
        } finally { release.countDown() }
    }
    @Test fun `repeatable read rejects a stale concurrent structural transaction`() = fixture(Connection.TRANSACTION_REPEATABLE_READ) { r ->
        val entered=CountDownLatch(1);val release=CountDownLatch(1);r.pauseFirstCheck(entered,release)
        val first=r.worker("first") { r.db { r.warehouse.update("branch",WarehouseVo(parentId="target")) } }
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS))
            val second=r.worker("second") { r.db { r.warehouse.update("target",WarehouseVo(parentId="branch")) } }
            r.awaitSecondProgress(second);release.countDown()
            first.get(15,TimeUnit.SECONDS).getOrThrow()
            val failure=second.get(15,TimeUnit.SECONDS).exceptionOrNull()
            assertNotNull(failure)
            assertTrue(generateSequence(failure) { it.cause }.filterIsInstance<java.sql.SQLException>().any { it.sqlState=="40001" },failure.toString())
            r.db { assertEquals("target",WarehouseDao["branch"].parentId);assertEquals("0",WarehouseDao["target"].parentId) }
        } finally { release.countDown() }
    }
    @ParameterizedTest @ValueSource(strings=["missing","foreign","exhibition","invalid-type","invalid-ancestor"])
    fun `new collection rejects invalid current storage before creating ledger records`(kind: String) = fixture { r ->
        r.db {
            when(kind) {
                "missing" -> WarehouseDao["branch"].delete()
                "foreign" -> WarehouseDao["branch"].orgId="other"
                "exhibition" -> WarehouseDao["branch"].apply { sceneType=EnumLocationScene.EXHIBITION;type=EnumWarehouseType.EXHIBITION_HALL }
                "invalid-type" -> WarehouseDao["branch"].type=EnumWarehouseType.EXHIBITION_HALL
                "invalid-ancestor" -> WarehouseDao["branch"].parentId="absent"
            }
        }
        assertThrows(BadRequestException::class.java) { r.collections.createForOrg("museum",CollectionVo(name="藏品",totalRegNo="A4",acquireDate=java.time.LocalDate.of(2026,9,10),warehouseId="branch")) }
        r.db { assertTrue(CollectionDao.all().none());assertTrue(CollectionLedgerLogDao.all().none()) }
    }

    private fun prepareWriter(r: Runtime, kind: String) = r.db {
        if (kind in setOf("history", "inventory")) {
            originalReaderSchema()
            originalReader("keeper", CollectionConst.STORAGE)
            CollectionOrgDao["museum"].systemGroupId = "museum"
        }

        if(kind in setOf("placement","venue"))WarehouseDao["branch"].apply {
            sceneType=EnumLocationScene.EXHIBITION
            type=if(kind=="placement")EnumWarehouseType.SHOWCASE else EnumWarehouseType.EXHIBITION_HALL
        }
        CollectionDao.new("writer-item") { orgId=if(kind=="transfer")"other" else "museum";name="藏品";totalRegNo="writer-item";lifeStatus=if(kind=="return")EnumCollectionStatus.ON_LOAN else EnumCollectionStatus.IN_STORE }
        ExhibitionDao.new("writer-show") { orgId="museum";title="展览";collectionIds="writer-item" }
        LoanDao.new("writer-loan") { orgId="museum";title="来源借用";borrower="外馆";protocolNo="L1";collectionIds="writer-item";flowStatus=EnumFlowStatus.PASS;outExecuted=true }
    }
    private fun writeLocation(r: Runtime, kind: String): String {
        val previous=SecurityContextHolder.getContext()
        val user=SecurityUser(id="keeper",name="保管",username="keeper",password="",groupId="museum",groupIds=setOf("museum"),postIds=emptySet(),dataType=0,roles=emptySet(),authorities=listOf(SimpleGrantedAuthority(CollectionConst.STORAGE)),enabled=true)
        SecurityContextHolder.setContext(SecurityContextHolder.createEmptyContext().apply { authentication=UsernamePasswordAuthenticationToken(user,null,user.authorities) })
        try { return when(kind) {
            "entry" -> r.c.getBean(WarehouseEntryApplicationService::class.java).save(entryProposal())
            "placement" -> r.db { r.c.getBean(CollectionPlacementService::class.java).replaceExhibitionPlacements(ExhibitionDao["writer-show"],listOf(ExhibitionPlacementVo(collectionId="writer-item",locationId="branch")));"writer-show" }
            "venue" -> r.c.getBean(ExhibitionService::class.java).save(ExhibitionVo(title="新展览",venueId="branch",collectionIds="writer-item"))
            "return" -> r.c.getBean(CollectionReturnService::class.java).save(CollectionReturnVo(sourceBizType=CollectionConst.BIZ_LOAN,sourceBizId="writer-loan",collectionIds="writer-item",toWarehouseId="branch",keeperUserId="keeper"))
            "transfer" -> r.c.getBean(CollectionTransferService::class.java).save(CollectionTransferVo(collectionId="writer-item",toOrgId="museum",dossier=CollectionTransferDossierVo(EnumCollectionTransferKind.STATE_ALLOCATION,"source-keeper","source-leader","target-keeper","target-leader","branch","archive-files","source-book","target-book")))
            "inventory" -> r.c.getBean(InventoryBatchService::class.java).save(InventoryBatchVo(batchName="位置盘点",warehouseId="branch"))
            else -> r.c.getBean(StorageMovementService::class.java).createHistoricalImport("writer-item","source",1,CollectionWordMovementPreviewVo(2,java.time.LocalDateTime.of(2026,1,1,9,0),EnumMoveType.IN,toWarehouseId="branch"))
        } } finally { SecurityContextHolder.setContext(previous) }
    }
    @ParameterizedTest @ValueSource(strings=["placement","venue","return","history","transfer","inventory"])
    fun `location removal prevents a concurrent new business reference`(kind: String) = fixture { r ->
        prepareWriter(r,kind)
        val entered=CountDownLatch(1);val release=CountDownLatch(1);r.pauseFirstCheck(entered,release)
        val first=r.worker("first") { r.warehouse.delete("branch");"deleted" }
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS))
            val second=r.worker("second") { writeLocation(r,kind) }
            r.awaitSecondProgress(second);release.countDown()
            first.get(15,TimeUnit.SECONDS).getOrThrow()
            val failure=second.get(15,TimeUnit.SECONDS).exceptionOrNull()
            assertTrue(failure is BadRequestException,failure.toString())
            r.db { assertNull(WarehouseDao.findById("branch"));assertTrue(CollectionPlacementDao.all().none());assertTrue(CollectionReturnDao.all().none());assertTrue(StorageMovementDao.all().none());assertEquals(1L,ExhibitionDao.count());assertTrue(CollectionTransferDao.all().none());assertTrue(InventoryBatchDao.all().none());assertEquals(0L,RfidWorkstations.selectAll().count()) }
        } finally { release.countDown() }
    }

    @ParameterizedTest @ValueSource(strings=["placement","venue","return","history","transfer","inventory"])
    fun `committed business reference prevents a waiting location removal`(kind: String) = fixture { r ->
        prepareWriter(r,kind)
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val first=r.worker("first") { r.db {
            val id=writeLocation(r,kind);entered.countDown();check(release.await(15,TimeUnit.SECONDS));id
        } }
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS))
            val second=r.worker("second") { r.warehouse.delete("branch");"deleted" }
            r.awaitSecondProgress(second);release.countDown()
            first.get(15,TimeUnit.SECONDS).getOrThrow()
            val failure=second.get(15,TimeUnit.SECONDS).exceptionOrNull()
            assertTrue(failure is BadRequestException,failure.toString())
            assertTrue(failure!!.message.orEmpty().contains("引用"),failure.message)
            r.db { assertNotNull(WarehouseDao.findById("branch")) }
        } finally { release.countDown() }
    }
    @ParameterizedTest @ValueSource(strings=["placement","venue","return","history","transfer","inventory"])
    fun `rolled back business reference allows a waiting location removal`(kind: String) = fixture { r ->
        prepareWriter(r,kind)
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val first=r.worker("first") { r.db {
            writeLocation(r,kind);entered.countDown();check(release.await(15,TimeUnit.SECONDS));throw IllegalStateException("writer rollback")
        } }
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS))
            val second=r.worker("second") { r.warehouse.delete("branch");"deleted" }
            r.awaitSecondProgress(second);release.countDown()
            assertEquals("writer rollback",first.get(15,TimeUnit.SECONDS).exceptionOrNull()?.message)
            second.get(15,TimeUnit.SECONDS).getOrThrow()
            r.db { assertNull(WarehouseDao.findById("branch"));assertTrue(CollectionPlacementDao.all().none());assertTrue(CollectionReturnDao.all().none());assertTrue(StorageMovementDao.all().none());assertEquals(1L,ExhibitionDao.count());assertTrue(CollectionTransferDao.all().none());assertTrue(InventoryBatchDao.all().none());assertEquals(0L,RfidWorkstations.selectAll().count()) }
        } finally { release.countDown() }
    }
    @ParameterizedTest @ValueSource(strings=["placement","venue","return","history","transfer","inventory"])
    fun `business reference cannot point into another museum`(kind: String) = fixture { r ->
        prepareWriter(r,kind);r.db { WarehouseDao["branch"].orgId="other" }
        assertThrows(BadRequestException::class.java) { writeLocation(r,kind) }
        r.db { assertTrue(CollectionPlacementDao.all().none());assertTrue(CollectionReturnDao.all().none());assertTrue(StorageMovementDao.all().none());assertEquals(1L,ExhibitionDao.count());assertTrue(CollectionTransferDao.all().none());assertTrue(InventoryBatchDao.all().none());assertEquals(0L,RfidWorkstations.selectAll().count()) }
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.CsvSource("placement,false","placement,true","venue,false","venue,true","return,false","return,true","history,false","history,true","transfer,false","transfer,true","inventory,false","inventory,true")
    fun `cached business location waits for deletion and returns a business error or preserves a rollback`(kind:String,rollback:Boolean)=fixture {r->
        prepareWriter(r,kind)
        val entered=CountDownLatch(1);val release=CountDownLatch(1);r.pauseFirstCheck(entered,release)
        val first=r.worker("first") {r.db {r.warehouse.delete("branch");if(rollback)error("rollback location deletion");"deleted"}}
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS))
            val second=r.worker("second") {r.db {
                assertEquals("museum",WarehouseDao["branch"].orgId)
                writeLocation(r,kind)
            }}
            r.awaitSecondProgress(second);release.countDown()
            val removed=first.get(15,TimeUnit.SECONDS);val saved=second.get(15,TimeUnit.SECONDS)
            if(rollback) {
                assertTrue(removed.isFailure);val id=saved.getOrThrow()
                r.db {
                    assertNotNull(WarehouseDao.findById("branch"))
                    val reference=when(kind) {
                        "placement"->CollectionPlacementDao.all().single().locationId
                        "venue"->ExhibitionDao[id].venueId
                        "return"->CollectionReturnDao[id].toWarehouseId
                        "history"->StorageMovementDao[id].toWarehouseId
                        "transfer"->CollectionTransferDao[id].dossier!!.targetWarehouseId
                        else->InventoryBatchDao[id].warehouseId
                    }
                    assertEquals("branch",reference)
                }
            }
            else {
                removed.getOrThrow()
                assertTrue(saved.exceptionOrNull() is BadRequestException,saved.exceptionOrNull()?.stackTraceToString() ?: saved.toString())
                r.db {assertNull(WarehouseDao.findById("branch"));assertTrue(CollectionPlacementDao.all().none());assertTrue(CollectionReturnDao.all().none());assertTrue(StorageMovementDao.all().none());assertEquals(1L,ExhibitionDao.count());assertTrue(CollectionTransferDao.all().none());assertTrue(InventoryBatchDao.all().none())}
            }
        } finally {release.countDown()}
    }

    private fun existingShowcasePlacement(r:Runtime) {
        prepareWriter(r,"placement")
        r.db {
            WarehouseDao["target"].apply {sceneType=EnumLocationScene.EXHIBITION;type=EnumWarehouseType.SHOWCASE}
            r.c.getBean(CollectionPlacementService::class.java).replaceExhibitionPlacements(ExhibitionDao["writer-show"],listOf(ExhibitionPlacementVo(collectionId="writer-item",locationId="target")))
        }
    }
    private fun showcaseState(r:Runtime)=r.db {CollectionPlacements.selectAll().orderBy(CollectionPlacements.id).map {row->CollectionPlacements.columns.map {row[it]}}}

    @ParameterizedTest @ValueSource(booleans=[false,true])
    fun `cached showcase replacement preserves earlier assignments when location deletion commits`(rollback:Boolean)=fixture {r->
        existingShowcasePlacement(r);val before=showcaseState(r)
        val entered=CountDownLatch(1);val release=CountDownLatch(1);r.pauseFirstCheck(entered,release)
        val first=r.worker("first") {r.db {r.warehouse.delete("branch");if(rollback)error("rollback deletion");"deleted"}}
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS))
            val second=r.worker("second") {r.db {WarehouseDao["branch"].name;writeLocation(r,"placement")}}
            r.awaitSecondProgress(second);release.countDown()
            val removed=first.get(15,TimeUnit.SECONDS);val saved=second.get(15,TimeUnit.SECONDS)
            if(rollback) {
                assertTrue(removed.isFailure);saved.getOrThrow()
                r.db {assertEquals("branch",CollectionPlacementDao.all().single().locationId);assertNotNull(WarehouseDao.findById("branch"))}
            } else {
                removed.getOrThrow();assertTrue(saved.exceptionOrNull() is BadRequestException,saved.toString())
                assertEquals("展柜已被删除，请重新分配展柜",saved.exceptionOrNull()?.message)
                assertEquals(before,showcaseState(r));r.db {assertNull(WarehouseDao.findById("branch"))}
            }
            r.db {assertEquals(EnumCollectionStatus.IN_STORE,CollectionDao["writer-item"].lifeStatus);assertNull(CollectionDao["writer-item"].warehouseId);assertEquals(0L,StorageMovementDao.all().count())}
        } finally {release.countDown()}
    }

    @ParameterizedTest @ValueSource(strings=["foreign","scene","type"])
    fun `cached showcase replacement rejects changed identity without erasing prior assignments`(kind:String)=fixture {r->
        existingShowcasePlacement(r);val before=showcaseState(r)
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val first=r.worker("first") {r.db {
            CollectionLocationMutation.acquire("museum")
            val w=WarehouseDao["branch"]
            when(kind) {"foreign"->w.orgId="other";"scene"->w.sceneType=EnumLocationScene.STORAGE;"type"->w.type=EnumWarehouseType.EXHIBITION_HALL}
            w.flush();entered.countDown();check(release.await(15,TimeUnit.SECONDS));"changed"
        }}
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS))
            val second=r.worker("second") {r.db {
                val w=WarehouseDao["branch"];assertEquals("museum",w.orgId);assertEquals(EnumLocationScene.EXHIBITION,w.sceneType);assertEquals(EnumWarehouseType.SHOWCASE,w.type)
                writeLocation(r,"placement")
            }}
            r.awaitSecondProgress(second);release.countDown();first.get(15,TimeUnit.SECONDS).getOrThrow()
            val result=second.get(15,TimeUnit.SECONDS)
            assertTrue(result.exceptionOrNull() is BadRequestException,result.toString());assertEquals(before,showcaseState(r))
        } finally {release.countDown()}
    }

    @Test fun `historical location names remain text without inventing current location ids`() = fixture { r ->
        prepareWriter(r,"history")
        val previous=SecurityContextHolder.getContext()
        val user=SecurityUser(id="keeper",name="保管",username="keeper",password="",groupId="museum",groupIds=setOf("museum"),postIds=emptySet(),dataType=0,roles=emptySet(),authorities=listOf(SimpleGrantedAuthority(CollectionConst.STORAGE)),enabled=true)
        SecurityContextHolder.setContext(SecurityContextHolder.createEmptyContext().apply { authentication=UsernamePasswordAuthenticationToken(user,null,user.authorities) })
        try {
            val movement=CollectionWordMovementPreviewVo(2,java.time.LocalDateTime.of(2026,1,1,9,0),EnumMoveType.IN,fromWarehouseName="旧馆旧库",toWarehouseName="历史临时库")
            val legacyId=r.c.getBean(StorageMovementService::class.java).createHistoricalImport("writer-item","source",1,movement)
            val sourcedId=r.c.getBean(StorageMovementService::class.java).createHistoricalImport("writer-item","source",2,movement,sourceTotalRegNo="原始登记号-1",sourceCollectionName="原件旧名")
            r.db {
                CollectionDao["writer-item"].apply { totalRegNo="当前登记号-2";name="当前名称" }
                val legacy=StorageMovementDao[legacyId]
                assertNull(legacy.totalRegNoAtMovement);assertNull(legacy.collectionNameAtMovement)
                val row=StorageMovementDao[sourcedId]
                assertNull(row.fromWarehouseId);assertNull(row.toWarehouseId)
                assertTrue(row.basis!!.contains("旧馆旧库"));assertTrue(row.basis!!.contains("历史临时库"))
                assertEquals("原始登记号-1",row.totalRegNoAtMovement)
                assertEquals("原件旧名",row.collectionNameAtMovement)
                assertEquals("原始登记号-1",row.toModel().totalRegNo)
                assertEquals("原件旧名",row.toModel().collectionName)
                assertEquals(EnumCollectionStatus.IN_STORE,CollectionDao["writer-item"].lifeStatus)
                assertNull(CollectionDao["writer-item"].warehouseId)
            }
        } finally { SecurityContextHolder.setContext(previous) }
    }

    private fun installation(r: Runtime, id: String = "installation", org: String = "museum", active: Boolean = true) = r.db {
        SchemaUtils.create(MuseumLocations,MuseumLocationSources)
        MuseumLocationDao.new(id) { orgId=org;code=id;name="统一安装位置";manual=true;status=if(active)EnumContentStatus.NORMAL else EnumContentStatus.DELETE }
    }
    @ParameterizedTest @ValueSource(strings=["missing","foreign","disabled"])
    fun `RFID workstations reject invalid unified installation locations`(kind: String) = fixture { r ->
        installation(r,org=if(kind=="foreign")"other" else "museum",active=kind!="disabled")
        assertThrows(BadRequestException::class.java) { r.c.getBean(RfidWorkstationService::class.java).saveWorkstation(RfidWorkstationSaveVo(name="工作站",locationId=if(kind=="missing")"absent" else "installation",enabled=false)) }
        r.db { assertEquals(0L,RfidWorkstations.selectAll().count()) }
    }
    @Test fun `RFID installation ids do not become warehouse references through id collision`() = fixture { r ->
        installation(r,id="branch")
        val id=r.c.getBean(RfidWorkstationService::class.java).saveWorkstation(RfidWorkstationSaveVo(name="工作站",locationId="branch",enabled=false)).id
        r.warehouse.delete("branch")
        r.db { assertNotNull(MuseumLocationDao.findById("branch"));assertEquals("branch",RfidWorkstations.selectAll().where { RfidWorkstations.id eq id }.single()[RfidWorkstations.locationId]) }
    }
    @ParameterizedTest @ValueSource(strings=["mapped","disabled-mapping","other-source"])
    fun `warehouse maintenance follows typed installation source mappings`(kind: String) = fixture { r ->
        installation(r)
        r.db { MuseumLocationSourceDao.new("mapping") { orgId="museum";locationId="installation";sourceType="BUSINESS_MASTER";sourceCode=if(kind=="other-source")"FIRE_SPACE" else "COL_WAREHOUSE";externalId="branch";lastSyncedAt=java.time.LocalDateTime.now();status=if(kind=="disabled-mapping")EnumContentStatus.DELETE else EnumContentStatus.NORMAL } }
        if(kind=="other-source")r.warehouse.delete("branch")
        else assertThrows(BadRequestException::class.java) { r.warehouse.delete("branch") }
    }
    @Test fun `RFID save waits for installation status change and validates committed state`() = fixture { r ->
        installation(r)
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val first=r.worker("first") { r.db { MuseumLocations.update({MuseumLocations.id eq "installation"}) { it[status]=EnumContentStatus.DELETE };entered.countDown();check(release.await(15,TimeUnit.SECONDS));"disabled" } }
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS))
            val second=r.worker("second") { r.c.getBean(RfidWorkstationService::class.java).saveWorkstation(RfidWorkstationSaveVo(name="工作站",locationId="installation",enabled=false)).id }
            r.awaitSecondProgress(second);release.countDown()
            first.get(15,TimeUnit.SECONDS).getOrThrow()
            assertTrue(second.get(15,TimeUnit.SECONDS).exceptionOrNull() is BadRequestException)
            r.db { assertEquals(0L,RfidWorkstations.selectAll().count()) }
        } finally { release.countDown() }
    }

    @ParameterizedTest @ValueSource(strings=["missing","foreign","disabled"])
    fun `RFID workstation update preserves prior valid binding when target is invalid`(kind: String) = fixture { r ->
        installation(r,id="existing")
        installation(r,org=if(kind=="foreign")"other" else "museum",active=kind!="disabled")
        val service=r.c.getBean(RfidWorkstationService::class.java)
        val id=service.saveWorkstation(RfidWorkstationSaveVo(name="原工作站",locationId="existing",enabled=false)).id
        assertThrows(BadRequestException::class.java) { service.saveWorkstation(RfidWorkstationSaveVo(id=id,name="不应保存",locationId=if(kind=="missing")"absent" else "installation",enabled=false)) }
        r.db { val row=RfidWorkstations.selectAll().where { RfidWorkstations.id eq id }.single();assertEquals("原工作站",row[RfidWorkstations.name]);assertEquals("existing",row[RfidWorkstations.locationId]) }
    }
    @Test fun `RFID workstation can change to another valid unified location`() = fixture { r ->
        installation(r,id="existing");installation(r)
        val service=r.c.getBean(RfidWorkstationService::class.java)
        val id=service.saveWorkstation(RfidWorkstationSaveVo(name="工作站",locationId="existing",enabled=false)).id
        service.saveWorkstation(RfidWorkstationSaveVo(id=id,name="新位置工作站",locationId="installation",enabled=false))
        r.db { val row=RfidWorkstations.selectAll().where { RfidWorkstations.id eq id }.single();assertEquals("新位置工作站",row[RfidWorkstations.name]);assertEquals("installation",row[RfidWorkstations.locationId]) }
    }
    @Test fun `cross museum transfer waits for sorted first org without holding later org`() = fixture { r ->
        prepareWriter(r,"transfer")
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val first=r.worker("first") { r.db { CollectionLocationMutation.acquire("museum");entered.countDown();check(release.await(15,TimeUnit.SECONDS));"locked" } }
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS))
            val second=r.worker("second") { writeLocation(r,"transfer") }
            r.awaitSecondProgress(second)
            // 若按源馆other优先取锁，这条写入会超时；馆序museum优先时other仍可访问。
            r.source.connection.use { c -> c.createStatement().use { st ->
                st.execute("SET lock_timeout = '500ms'")
                assertEquals(1,st.executeUpdate("UPDATE col_org SET location_mutation_version=location_mutation_version+1 WHERE id='other'"))
            } }
            release.countDown();first.get(15,TimeUnit.SECONDS).getOrThrow();second.get(15,TimeUnit.SECONDS).getOrThrow()
            r.db { assertEquals(1L,CollectionTransferDao.count()) }
        } finally { release.countDown() }
    }

    private fun assignment() = WarehouseStaffingVo(setOf("k1","k2"),"keeper","leader",java.time.LocalDateTime.of(2026,9,1,10,0),"test-assignment")
    @Test fun `committed staffing prevents deletion that waited behind its row lock`() = fixture { r ->
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val first=r.worker("first") { r.db {
            r.c.getBean(WarehouseStaffingService::class.java).save("branch",assignment())
            entered.countDown();check(release.await(15,TimeUnit.SECONDS));"assigned"
        } }
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS))
            val second=r.worker("second") { r.warehouse.delete("branch");"deleted" }
            r.awaitSecondProgress(second);release.countDown();first.get(15,TimeUnit.SECONDS).getOrThrow()
            val failure=second.get(15,TimeUnit.SECONDS).exceptionOrNull()
            assertTrue(failure is BadRequestException,"Unexpected result: $failure")
            r.db { assertNotNull(WarehouseDao.findById("branch")?.staffing) }
        } finally { release.countDown() }
    }
    @ParameterizedTest @ValueSource(booleans=[false,true])
    fun `staffing waiting for location lock uses current maintenance permission after commit or rollback`(rollback:Boolean)=fixture {r ->
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val first=r.worker("first") {r.db {
            CollectionLocationMutation.acquire("museum")
            RolePermissions.deleteWhere {permissionId eq CollectionConst.WAREHOUSE_MANAGE}
            entered.countDown();check(release.await(15,TimeUnit.SECONDS))
            if(rollback)error("maintenance rollback")
            "maintenance removed"
        }}
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS))
            val second=r.worker("second") {r.c.getBean(WarehouseStaffingService::class.java).save("branch",assignment())}
            r.awaitSecondProgress(second);release.countDown()
            val permission=first.get(15,TimeUnit.SECONDS);val saved=second.get(15,TimeUnit.SECONDS)
            if(rollback) {
                assertEquals("maintenance rollback",permission.exceptionOrNull()?.message)
                assertEquals("branch",saved.getOrThrow())
                r.db {assertEquals(assignment(),WarehouseDao["branch"].staffing);assertEquals(1L,CollectionPermanentArchiveDao.count())}
            } else {
                assertEquals("maintenance removed",permission.getOrThrow())
                val failure=saved.exceptionOrNull()
                assertTrue(failure is BadRequestException,"Unexpected result: $failure")
                assertTrue(failure!!.message.orEmpty().contains("当前账号没有库房任职维护权限"))
                r.db {assertNull(WarehouseDao["branch"].staffing);assertEquals(0L,CollectionPermanentArchiveDao.count());assertEquals(0L,CollectionPermanentArchiveFileDao.count())}
            }
        } finally {release.countDown()}
    }
    @Test fun `deletion ahead of staffing refuses a new assignment against a removed warehouse`() = fixture { r ->
        val entered=CountDownLatch(1);val release=CountDownLatch(1);r.pauseFirstCheck(entered,release)
        val first=r.worker("first") { r.warehouse.delete("branch");"deleted" }
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS))
            val second=r.worker("second") { r.c.getBean(WarehouseStaffingService::class.java).save("branch",assignment()) }
            r.awaitSecondProgress(second);release.countDown();first.get(15,TimeUnit.SECONDS).getOrThrow()
            val failure=second.get(15,TimeUnit.SECONDS).exceptionOrNull()
            assertTrue(failure is BadRequestException,"Unexpected result: $failure")
            r.db { assertNull(WarehouseDao.findById("branch")) }
        } finally { release.countDown() }
    }
    @Test fun `staffing cannot be newly assigned to a disabled warehouse`() = fixture { r ->
        r.db { WarehouseDao["branch"].status=EnumContentStatus.DELETE }
        assertThrows(BadRequestException::class.java) { r.c.getBean(WarehouseStaffingService::class.java).save("branch",assignment()) }
        r.db { assertNull(WarehouseDao["branch"].staffing) }
    }

    private fun entryProposal() = WarehouseEntryApplicationVo(warehouseIds="branch",entrantIds="k1,k2",purpose="实际巡库",
        plannedStart=java.time.LocalDateTime.of(2026,9,10,9,0),plannedEnd=java.time.LocalDateTime.of(2026,9,10,10,0),
        dossier=WarehouseEntryDossierVo(EnumWarehouseEntryKind.KEEPERS,keeperUserId="keeper",identityFileIds="test-identity"))
    private fun seedStaffing(r: Runtime, applicant:Boolean=false) = r.db {
        if(applicant)originalReader("keeper",CollectionConst.WAREHOUSE_ENTRY_APPLY)
        r.c.getBean(WarehouseStaffingService::class.java).save("branch",assignment())
    }
    private fun replacementAssignment()=assignment().copy(keeperUserIds=setOf("k1"),assignedAt=assignment().assignedAt.plusHours(1),replacementReason="实际任职变化")
    @Test fun `rolled back staffing assignment leaves no phantom reference preventing deletion`() = fixture { r ->
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val first=r.worker("first") { r.db {
            r.c.getBean(WarehouseStaffingService::class.java).save("branch",assignment())
            entered.countDown();check(release.await(15,TimeUnit.SECONDS));error("assignment rollback")
        } }
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS))
            val second=r.worker("second") { r.warehouse.delete("branch");"deleted" }
            r.awaitSecondProgress(second);release.countDown();assertEquals("assignment rollback",first.get(15,TimeUnit.SECONDS).exceptionOrNull()?.message)
            second.get(15,TimeUnit.SECONDS).getOrThrow();r.db { assertNull(WarehouseDao.findById("branch")) }
        } finally { release.countDown() }
    }
    @Test fun `staffing waits for a structural scene change and rejects the committed exhibition location`() = fixture { r ->
        val entered=CountDownLatch(1);val release=CountDownLatch(1);r.pauseFirstCheck(entered,release)
        val first=r.worker("first") { r.warehouse.update("branch",WarehouseVo(sceneType=EnumLocationScene.EXHIBITION,type=EnumWarehouseType.EXHIBITION_HALL)) }
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS))
            val second=r.worker("second") { r.c.getBean(WarehouseStaffingService::class.java).save("branch",assignment()) }
            r.awaitSecondProgress(second);release.countDown();first.get(15,TimeUnit.SECONDS).getOrThrow()
            val failure=second.get(15,TimeUnit.SECONDS).exceptionOrNull()
            assertTrue(failure is BadRequestException,"Unexpected result: $failure")
            r.db { assertEquals(EnumLocationScene.EXHIBITION,WarehouseDao["branch"].sceneType);assertNull(WarehouseDao["branch"].staffing) }
        } finally { release.countDown() }
    }
    @Test fun `entry proposal saved before staffing change retains its baseline and cannot pass later revalidation`() = fixture { r ->
        seedStaffing(r,applicant=true)
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val first=r.worker("first") { r.db { val id=writeLocation(r,"entry");entered.countDown();check(release.await(15,TimeUnit.SECONDS));id } }
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS))
            val second=r.worker("second") { r.c.getBean(WarehouseStaffingService::class.java).save("branch",replacementAssignment()) }
            r.awaitSecondProgress(second);release.countDown();val id=first.get(15,TimeUnit.SECONDS).getOrThrow();second.get(15,TimeUnit.SECONDS).getOrThrow()
            r.db {
                val proposal=WarehouseEntryApplicationDao[id]
                assertEquals(assignment(),proposal.baseline!!.items.single().staffing)
                assertEquals(replacementAssignment(),WarehouseDao["branch"].staffing)
            }
            val failure=assertThrows(BadRequestException::class.java) { r.db {
                r.c.getBean(WarehouseEntryStatutoryService::class.java).requirePrepared(WarehouseEntryApplicationDao[id])
            } }
            assertTrue(failure.message.orEmpty().contains("名单已经变化"))
        } finally { release.countDown() }
    }
    @Test fun `entry proposal waiting behind staffing change captures the committed replacement baseline`() = fixture { r ->
        seedStaffing(r,applicant=true)
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val first=r.worker("first") { r.db { r.c.getBean(WarehouseStaffingService::class.java).save("branch",replacementAssignment());entered.countDown();check(release.await(15,TimeUnit.SECONDS));"changed" } }
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS))
            val second=r.worker("second") { r.db {
                assertEquals(assignment(),WarehouseDao["branch"].staffing)
                writeLocation(r,"entry")
            } }
            r.awaitSecondProgress(second);release.countDown();first.get(15,TimeUnit.SECONDS).getOrThrow();val id=second.get(15,TimeUnit.SECONDS).getOrThrow()
            r.db { assertEquals(replacementAssignment(),WarehouseEntryApplicationDao[id].baseline!!.items.single().staffing) }
        } finally { release.countDown() }
    }
    @ParameterizedTest @ValueSource(strings=["disabled","ancestor","type"])
    fun `entry rejects invalid location before creating a partially filled application`(kind: String) = fixture { r ->
        seedStaffing(r,applicant=true)
        r.db { when(kind) {
            "disabled" -> WarehouseDao["branch"].status=EnumContentStatus.DELETE
            "ancestor" -> WarehouseDao["branch"].parentId="missing"
            "type" -> WarehouseDao["branch"].type=EnumWarehouseType.SHOWCASE
        } }
        assertThrows(BadRequestException::class.java) { writeLocation(r,"entry") }
        r.db { assertEquals(0L,WarehouseEntryApplicationDao.count()) }
    }
    @ParameterizedTest @ValueSource(strings=["ancestor","type"])
    fun `staffing rejects malformed or non storage locations without mutating the roster`(kind: String) = fixture { r ->
        r.db { if(kind=="ancestor")WarehouseDao["branch"].parentId="missing" else WarehouseDao["branch"].type=EnumWarehouseType.SHOWCASE }
        assertThrows(BadRequestException::class.java) { r.c.getBean(WarehouseStaffingService::class.java).save("branch",assignment()) }
        r.db { assertNull(WarehouseDao["branch"].staffing) }
    }

    private val diaryAt=java.time.LocalDateTime.of(2026,9,1,12,0)
    private fun prepareDiaryVisit(r: Runtime): WarehouseDiaryEntriesVo {
        seedStaffing(r)
        return r.db {
            WarehouseEntryApplicationDao.new("diary-visit") {
                orgId="museum";applicationNo="DIARY-VISIT";warehouseIds="branch";purpose="巡库";applicantId="k1"
                entrantIds="k1,k2";plannedStart=diaryAt.minusHours(3);plannedEnd=java.time.LocalDateTime.now().plusDays(1)
                dossier=entryProposal().dossier;baseline=WarehouseEntryBaselineVo(listOf(WarehouseStaffingItemVo("branch","branch",assignment())))
                flowStatus=EnumFlowStatus.PASS;approvedAt=diaryAt.minusHours(3);actualEntryAt=diaryAt.minusHours(2)
                entryRecord=diaryAttendance(false)
            }
            r.c.getBean(WarehouseDiaryService::class.java).entries("branch",diaryAt.toLocalDate())
        }
    }
    private fun diaryAttendance(exit: Boolean)=WarehouseAttendanceVo(
        occurredAt=if(exit)diaryAt.minusHours(1) else diaryAt.minusHours(2),entrantUserIds=setOf("k1","k2"),
        identityChecked=true,continuousAccompaniment=true,signedRecordFileIds=if(exit)"test-exit" else "test-entry"
    )
    private fun diaryProposal(entries: WarehouseDiaryEntriesVo)=WarehouseLogVo(
        warehouseId="branch",logDate=diaryAt.toLocalDate(),content="实际巡库记录",entries=entries,
        diary=WarehouseDiaryVo(diaryAt,setOf("k1"),true,"藏品无移动","安全正常",
            listOf(WarehouseEnvironmentReadingVo(diaryAt,20.0,50.0,"现场测量","test-reading")),false,signedDiaryFileIds="test-diary")
    )
    private fun checkoutDiaryVisit(r: Runtime): String = asDiaryReader {
        r.c.getBean(WarehouseEntryApplicationService::class.java).checkOut("diary-visit",diaryAttendance(true))
        "checked-out"
    }
    @ParameterizedTest @ValueSource(booleans=[false,true])
    fun `diary waiting for a location lock uses committed current permissions and honors rollback`(rollback:Boolean) = fixture { r ->
        val reviewed=prepareDiaryVisit(r)
        // 任职准备已完成；保留原用例的两项日记职责，避免维护权限影响撤权断言。
        r.db { RolePermissions.deleteWhere { permissionId eq CollectionConst.WAREHOUSE_MANAGE } }
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val first=r.worker("first") { r.db {
            CollectionLocationMutation.acquire("museum")
            RolePermissions.deleteWhere { permissionId eq CollectionConst.STORAGE }
            entered.countDown();check(release.await(15,TimeUnit.SECONDS))
            if(rollback)error("permission rollback")
            "permission removed"
        } }
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS))
            // 此线程的登录快照仍有 STORAGE；服务必须读取另一事务提交后的当前权限。
            val second=r.worker("second") { r.c.getBean(WarehouseLogService::class.java).save(diaryProposal(reviewed)) }
            r.awaitSecondProgress(second);release.countDown()
            val removal=first.get(15,TimeUnit.SECONDS);val saved=second.get(15,TimeUnit.SECONDS)
            if(rollback) {
                assertEquals("permission rollback",removal.exceptionOrNull()?.message)
                val id=saved.getOrThrow()
                r.db { assertEquals(reviewed,WarehouseLogDao[id].entries);assertEquals(1L,WarehouseLogDao.count());assertEquals(2L,RolePermissions.selectAll().count()) }
            } else {
                assertEquals("permission removed",removal.getOrThrow())
                val failure=saved.exceptionOrNull()
                assertTrue(failure is BadRequestException,"Unexpected result: $failure")
                assertTrue(failure!!.message.orEmpty().contains("当前账号没有查询藏品资料的权限"))
                r.db { assertEquals(0L,WarehouseLogDao.count());assertEquals(1L,RolePermissions.selectAll().count()) }
            }
        } finally { release.countDown() }
    }
    @Test fun `checkout ahead of diary save forces refreshed reviewed visit snapshots`() = fixture { r ->
        val reviewed=prepareDiaryVisit(r);val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val first=r.worker("first") { r.db { checkoutDiaryVisit(r);entered.countDown();check(release.await(15,TimeUnit.SECONDS));"checked-out" } }
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS))
            val second=r.worker("second") { r.db {
                assertNull(WarehouseEntryApplicationDao["diary-visit"].exitRecord)
                r.c.getBean(WarehouseLogService::class.java).save(diaryProposal(reviewed))
            } }
            r.awaitSecondProgress(second);release.countDown();first.get(15,TimeUnit.SECONDS).getOrThrow()
            val failure=second.get(15,TimeUnit.SECONDS).exceptionOrNull()
            assertTrue(failure is BadRequestException,"Unexpected result: $failure")
            r.db { assertNotNull(WarehouseEntryApplicationDao["diary-visit"].exitRecord);assertEquals(0L,WarehouseLogDao.count()) }
        } finally { release.countDown() }
    }

    @Test fun `diary saved before checkout keeps signed snapshot and reports later visits changed`() = fixture { r ->
        val reviewed=prepareDiaryVisit(r);val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val first=r.worker("first") { r.db { val id=r.c.getBean(WarehouseLogService::class.java).save(diaryProposal(reviewed));entered.countDown();check(release.await(15,TimeUnit.SECONDS));id } }
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS))
            val second=r.worker("second") { checkoutDiaryVisit(r) }
            r.awaitSecondProgress(second);release.countDown();val id=first.get(15,TimeUnit.SECONDS).getOrThrow();second.get(15,TimeUnit.SECONDS).getOrThrow()
            r.db {
                val diary=r.c.getBean(WarehouseDiaryService::class.java).get(id)
                assertEquals(reviewed,diary.entries);assertTrue(diary.entriesChanged)
                assertNull(diary.entries!!.items.single().exit);assertNotNull(diary.currentEntries!!.items.single().exit)
            }
        } finally { release.countDown() }
    }
    @Test fun `rolled back checkout allows the reviewed diary snapshot to remain valid`() = fixture { r ->
        val reviewed=prepareDiaryVisit(r);val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val first=r.worker("first") { r.db { checkoutDiaryVisit(r);entered.countDown();check(release.await(15,TimeUnit.SECONDS));error("checkout rollback") } }
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS))
            val second=r.worker("second") { r.c.getBean(WarehouseLogService::class.java).save(diaryProposal(reviewed)) }
            r.awaitSecondProgress(second);release.countDown();assertEquals("checkout rollback",first.get(15,TimeUnit.SECONDS).exceptionOrNull()?.message)
            val id=second.get(15,TimeUnit.SECONDS).getOrThrow()
            r.db { assertNull(WarehouseEntryApplicationDao["diary-visit"].exitRecord);assertEquals(reviewed,WarehouseLogDao[id].entries) }
        } finally { release.countDown() }
    }
    @Test fun `rolled back diary releases checkout without leaving a signed record`() = fixture { r ->
        val reviewed=prepareDiaryVisit(r);val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val first=r.worker("first") { r.db { r.c.getBean(WarehouseLogService::class.java).save(diaryProposal(reviewed));entered.countDown();check(release.await(15,TimeUnit.SECONDS));error("diary rollback") } }
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS))
            val second=r.worker("second") { checkoutDiaryVisit(r) }
            r.awaitSecondProgress(second);release.countDown();assertEquals("diary rollback",first.get(15,TimeUnit.SECONDS).exceptionOrNull()?.message)
            second.get(15,TimeUnit.SECONDS).getOrThrow()
            r.db { assertEquals(0L,WarehouseLogDao.count());assertNotNull(WarehouseEntryApplicationDao["diary-visit"].exitRecord) }
        } finally { release.countDown() }
    }
    @Test fun `staffing change ahead of diary rejects inspectors no longer in the active roster`() = fixture { r ->
        val reviewed=prepareDiaryVisit(r);val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val proposal=diaryProposal(reviewed).let { it.copy(diary=it.diary!!.copy(inspectorUserIds=setOf("k2"))) }
        val first=r.worker("first") { r.db { r.c.getBean(WarehouseStaffingService::class.java).save("branch",replacementAssignment());entered.countDown();check(release.await(15,TimeUnit.SECONDS));"changed" } }
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS))
            val second=r.worker("second") { r.db {
                assertEquals(assignment(),WarehouseDao["branch"].staffing)
                r.c.getBean(WarehouseLogService::class.java).save(proposal)
            } }
            r.awaitSecondProgress(second);release.countDown();first.get(15,TimeUnit.SECONDS).getOrThrow()
            val failure=second.get(15,TimeUnit.SECONDS).exceptionOrNull()
            assertTrue(failure is BadRequestException,"Unexpected result: $failure")
            r.db { assertEquals(0L,WarehouseLogDao.count()) }
        } finally { release.countDown() }
    }
    @Test fun `diary signed before a staffing change retains original inspector facts`() = fixture { r ->
        val reviewed=prepareDiaryVisit(r);val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val proposal=diaryProposal(reviewed).let { it.copy(diary=it.diary!!.copy(inspectorUserIds=setOf("k2"))) }
        val first=r.worker("first") { r.db { val id=r.c.getBean(WarehouseLogService::class.java).save(proposal);entered.countDown();check(release.await(15,TimeUnit.SECONDS));id } }
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS))
            val second=r.worker("second") { r.c.getBean(WarehouseStaffingService::class.java).save("branch",replacementAssignment()) }
            r.awaitSecondProgress(second);release.countDown();val id=first.get(15,TimeUnit.SECONDS).getOrThrow();second.get(15,TimeUnit.SECONDS).getOrThrow()
            r.db { assertEquals(setOf("k2"),r.c.getBean(WarehouseDiaryService::class.java).get(id).diary!!.inspectorUserIds);assertEquals(setOf("k1"),WarehouseDao["branch"].staffing!!.keeperUserIds) }
        } finally { release.countDown() }
    }
    @Test fun `actual checkout remains recordable after later staffing change and warehouse retirement`() = fixture { r ->
        prepareDiaryVisit(r)
        r.c.getBean(WarehouseStaffingService::class.java).save("branch",replacementAssignment())
        r.db { WarehouseDao["branch"].status=EnumContentStatus.DELETE }
        checkoutDiaryVisit(r)
        r.db { assertEquals(setOf("k1","k2"),WarehouseEntryApplicationDao["diary-visit"].exitRecord!!.entrantUserIds) }
    }
    @Test fun `diary history queries stay read only and do not change location versions`() = fixture { r ->
        val reviewed=prepareDiaryVisit(r)
        val id=r.c.getBean(WarehouseLogService::class.java).save(diaryProposal(reviewed))
        val before=r.db { CollectionOrgs.select(CollectionOrgs.locationMutationVersion).map { it[CollectionOrgs.locationMutationVersion] } }
        org.springframework.transaction.support.TransactionTemplate(r.c.getBean(SpringTransactionManager::class.java)).apply { isReadOnly=true }.execute {
            org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().exec("SHOW transaction_read_only") { rows -> assertTrue(rows.next());assertEquals("on",rows.getString(1)) }
            assertEquals(reviewed,r.c.getBean(WarehouseDiaryService::class.java).get(id).entries)
            assertEquals(reviewed,r.c.getBean(WarehouseDiaryService::class.java).entries("branch",diaryAt.toLocalDate()))
        }
        r.db { assertEquals(before,CollectionOrgs.select(CollectionOrgs.locationMutationVersion).map { it[CollectionOrgs.locationMutationVersion] }) }
    }

    @Test fun `diary snapshots round trip legacy microsecond times without altering source timestamps`() = fixture { r ->
        prepareDiaryVisit(r)
        val entered=diaryAt.minusHours(2).withNano(123456000)
        val exited=diaryAt.minusHours(1).withNano(987654000)
        r.db { WarehouseEntryApplicationDao["diary-visit"].apply {
            actualEntryAt=entered;actualExitAt=exited
            entryRecord=diaryAttendance(false).copy(occurredAt=entered)
            exitRecord=diaryAttendance(true).copy(occurredAt=exited)
        } }
        val reviewed=r.db { r.c.getBean(WarehouseDiaryService::class.java).entries("branch",diaryAt.toLocalDate()) }
        val json=io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils.toJson(reviewed)!!
        val client=io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils.fromJson(json,WarehouseDiaryEntriesVo::class.java)!!
        assertEquals(reviewed,client)
        assertEquals(123000000,client.items.single().enteredAt.nano)
        assertEquals(987000000,client.items.single().exitedAt!!.nano)
        val id=r.c.getBean(WarehouseLogService::class.java).save(diaryProposal(client))
        r.db {
            val diary=r.c.getBean(WarehouseDiaryService::class.java).get(id)
            assertFalse(diary.entriesChanged);assertEquals(client,diary.entries)
            assertEquals(entered,WarehouseEntryApplicationDao["diary-visit"].actualEntryAt)
            assertEquals(exited,WarehouseEntryApplicationDao["diary-visit"].actualExitAt)
        }
    }

    private fun prepareOnsiteOriginal(r:Runtime) {
        val entries=prepareDiaryVisit(r)
        r.db {
            val proposal=diaryProposal(entries)
            CollectionDao.new("onsite-piece"){orgId="museum";name="在库原件";totalRegNo="ONSITE-1";warehouseId="branch";lifeStatus=EnumCollectionStatus.IN_STORE}
            val log=WarehouseLogDao.new("onsite-log"){orgId="museum";warehouseId="branch";logDate=proposal.logDate!!;content=proposal.content!!;diary=proposal.diary;this.entries=entries}
            fun original(id:String,biz:String,bizId:String,phase:String,value:Any) {
                val snapshot=io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils.toJson(value)!!
                CollectionPermanentArchiveDao.new(id){orgId="museum";bizType=biz;this.bizId=bizId;this.phase=phase;this.snapshot=snapshot;digest=CollectionCareEvidence.digest(snapshot)}
            }
            original("onsite-diary",WarehouseDiaryService.BIZ,log.id.value,"RECORDED_1",mapOf("warehouseId" to log.warehouseId,"logDate" to log.logDate,"workContent" to log.content,"diary" to log.diary,"entries" to entries))
            original("onsite-entry",CollectionConst.BIZ_WAREHOUSE_ENTRY,"diary-visit","CHECKED_IN",diaryAttendance(false))
        }
    }
    private fun onsiteOriginal(r:Runtime):String {
        r.c.getBean(CollectionOriginalAccessService::class.java).requireAccess("museum","onsite-piece","TEST_CARE","onsite-case",diaryAt,"k1",null,"onsite-log")
        return "verified"
    }
    @ParameterizedTest @ValueSource(booleans=[false,true])
    fun `original access waits for actual checkout and ignores previously cached attendance`(rollback:Boolean)=fixture {r->
        prepareOnsiteOriginal(r)
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val first=r.worker("first"){r.db {
            checkoutDiaryVisit(r);entered.countDown();check(release.await(15,TimeUnit.SECONDS))
            if(rollback)throw IllegalStateException("rollback synthetic checkout")
            "checked-out"
        }}
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS))
            val second=r.worker("second"){r.db {
                assertNull(WarehouseEntryApplicationDao["diary-visit"].actualExitAt)
                onsiteOriginal(r)
            }}
            r.awaitSecondProgress(second);release.countDown()
            val firstResult=first.get(15,TimeUnit.SECONDS);val secondResult=second.get(15,TimeUnit.SECONDS)
            if(rollback) {
                assertTrue(firstResult.exceptionOrNull() is IllegalStateException)
                assertEquals("verified",secondResult.getOrThrow())
            } else {
                firstResult.getOrThrow()
                val failure=secondResult.exceptionOrNull();assertTrue(failure is BadRequestException,"Unexpected failure: $failure")
                assertTrue(failure!!.message!!.contains("已离库"))
            }
            r.db {
                assertEquals(rollback,WarehouseEntryApplicationDao["diary-visit"].actualExitAt==null)
                assertNull(WarehouseLogDao["onsite-log"].entries!!.items.single().exitedAt)
            }
        } finally {release.countDown()}
    }
    @Test fun `later checkout waits until original access transaction releases actual visit row`()=fixture {r->
        prepareOnsiteOriginal(r)
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val first=r.worker("first"){r.db {onsiteOriginal(r);entered.countDown();check(release.await(15,TimeUnit.SECONDS));"verified"}}
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS))
            val second=r.worker("second"){
                r.c.getBean(WarehouseEntryApplicationService::class.java).checkOut("diary-visit",diaryAttendance(true).copy(occurredAt=diaryAt.plusHours(1)))
                "checked-out"
            }
            r.awaitSecondProgress(second);release.countDown()
            assertEquals("verified",first.get(15,TimeUnit.SECONDS).getOrThrow());assertEquals("checked-out",second.get(15,TimeUnit.SECONDS).getOrThrow())
            r.db {assertEquals(diaryAt.plusHours(1),WarehouseEntryApplicationDao["diary-visit"].actualExitAt);assertNull(WarehouseLogDao["onsite-log"].entries!!.items.single().exitedAt)}
        } finally {release.countDown()}
    }

    @ParameterizedTest @ValueSource(booleans=[false,true])
    fun `opened attendance waits for original row and detects committed microsecond changes`(rollback:Boolean)=fixture {r->
        prepareDiaryVisit(r)
        r.db {SchemaUtils.create(io.hizhengfu.cloud.sys.file.dao.Files)}
        val service=r.c.getBean(WarehouseEntryApplicationService::class.java)
        val context=service.attendanceContext("diary-visit")
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val first=r.worker("first"){r.db {
            val before=WarehouseEntryApplicationDao["diary-visit"].plannedEnd
            WarehouseEntryApplications.update({WarehouseEntryApplications.id eq "diary-visit"}){it[plannedEnd]=before.plusNanos(1000)}
            entered.countDown();check(release.await(15,TimeUnit.SECONDS))
            if(rollback)throw IllegalStateException("rollback source change")
            "source-changed"
        }}
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS))
            val second=r.worker("second"){r.db {
                // 预先缓存未提交变更前的申请，提交仍须在取得行锁后重新读取。
                assertNull(WarehouseEntryApplicationDao["diary-visit"].actualExitAt)
                service.checkOut("diary-visit",diaryAttendance(true),context.orgId,context.revision)
                "checked-out"
            }}
            r.awaitSecondProgress(second);release.countDown()
            val firstResult=first.get(15,TimeUnit.SECONDS);val secondResult=second.get(15,TimeUnit.SECONDS)
            if(rollback) {
                assertEquals("rollback source change",firstResult.exceptionOrNull()?.message)
                assertEquals("checked-out",secondResult.getOrThrow())
            } else {
                assertEquals("source-changed",firstResult.getOrThrow())
                val error=secondResult.exceptionOrNull()
                assertTrue(error is BadRequestException,"Unexpected failure: $error")
                assertTrue(error!!.message!!.contains("申请依据已变化"))
            }
            r.db {assertEquals(!rollback,WarehouseEntryApplicationDao["diary-visit"].actualExitAt==null)}
        } finally {release.countDown()}
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.CsvSource("draft,false","draft,true","void,false","void,true")
    fun `application operation waiting for original row rechecks current duty after commit or rollback`(op:String,rollback:Boolean)=fixture {r->
        prepareDiaryVisit(r)
        r.db {
            actorRole("k1","APPLICATION",setOf(CollectionConst.WAREHOUSE_ENTRY_APPLY))
            WarehouseEntryApplicationDao["diary-visit"].apply {flowStatus=EnumFlowStatus.DRAFT;actualEntryAt=null;entryRecord=null}
        }
        val service=r.c.getBean(WarehouseEntryApplicationService::class.java)
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val first=r.worker("first"){r.db {
            WarehouseEntryApplications.update({WarehouseEntryApplications.id eq "diary-visit"}){it[purpose]="锁定后核实的新方案"}
            RolePermissions.deleteWhere {permissionId eq CollectionConst.WAREHOUSE_ENTRY_APPLY}
            entered.countDown();check(release.await(15,TimeUnit.SECONDS))
            if(rollback)throw IllegalStateException("rollback application duty")
            "revoked"
        }}
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS))
            val second=r.worker("second"){r.db {
                assertEquals("museum",WarehouseEntryApplicationDao["diary-visit"].orgId)
                if(op=="draft")service.saveDraft("diary-visit") else service.void("diary-visit")
                "finished"
            }}
            r.awaitSecondProgress(second);release.countDown()
            val firstResult=first.get(15,TimeUnit.SECONDS);val secondResult=second.get(15,TimeUnit.SECONDS)
            if(rollback) {
                assertEquals("rollback application duty",firstResult.exceptionOrNull()?.message)
                assertEquals("finished",secondResult.getOrThrow())
            } else {
                assertEquals("revoked",firstResult.getOrThrow())
                val error=secondResult.exceptionOrNull();assertTrue(error is BadRequestException,"Unexpected failure: $error")
                assertTrue(error!!.message!!.contains("人员入库申请权限"))
            }
            r.db {assertEquals(if(rollback && op=="void")EnumFlowStatus.VOID else EnumFlowStatus.DRAFT,WarehouseEntryApplicationDao["diary-visit"].flowStatus)}
        } finally {release.countDown()}
    }

    @ParameterizedTest @ValueSource(booleans=[false,true])
    fun `confirmed application waits for original row and detects committed microsecond change`(rollback:Boolean)=fixture {r->
        prepareDiaryVisit(r)
        r.db {
            SchemaUtils.create(io.hizhengfu.cloud.sys.file.dao.Files)
            actorRole("k1","APPLICATION",setOf(CollectionConst.WAREHOUSE_ENTRY_APPLY))
            WarehouseEntryApplicationDao["diary-visit"].apply {flowStatus=EnumFlowStatus.DRAFT;actualEntryAt=null;entryRecord=null;plannedStart=plannedStart.withNano(123456000)}
        }
        val service=r.c.getBean(WarehouseEntryApplicationService::class.java)
        val confirmation=service.applicationContext("diary-visit")
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val first=r.worker("first"){r.db {
            val old=WarehouseEntryApplicationDao["diary-visit"].plannedStart
            WarehouseEntryApplications.update({WarehouseEntryApplications.id eq "diary-visit"}){it[plannedStart]=old.plusNanos(1000)}
            entered.countDown();check(release.await(15,TimeUnit.SECONDS))
            if(rollback)throw IllegalStateException("rollback original microsecond")
            "changed"
        }}
        try {
            assertTrue(entered.await(10,TimeUnit.SECONDS))
            val second=r.worker("second"){r.db {
                assertEquals(123456000,WarehouseEntryApplicationDao["diary-visit"].plannedStart.nano)
                service.operateConfirmed("diary-visit",io.hizhengfu.cloud.flow.service.FlowUserOperation.VOID,confirmation.orgId,confirmation.revision)
                "voided"
            }}
            r.awaitSecondProgress(second);release.countDown()
            val a=first.get(15,TimeUnit.SECONDS);val b=second.get(15,TimeUnit.SECONDS)
            if(rollback){assertEquals("rollback original microsecond",a.exceptionOrNull()?.message);assertEquals("voided",b.getOrThrow())}
            else {assertEquals("changed",a.getOrThrow());assertTrue(b.exceptionOrNull() is BadRequestException)}
            r.db {assertEquals(if(rollback)EnumFlowStatus.VOID else EnumFlowStatus.DRAFT,WarehouseEntryApplicationDao["diary-visit"].flowStatus)}
        } finally {release.countDown()}
    }

    @ParameterizedTest @ValueSource(booleans=[false,true])
    fun `concurrent new application confirmations cannot duplicate a committed creation`(rollback:Boolean)=fixture {r->
        seedStaffing(r)
        r.db {SchemaUtils.create(io.hizhengfu.cloud.sys.file.dao.Files);actorRole("k1","APPLICATION",setOf(CollectionConst.WAREHOUSE_ENTRY_APPLY))}
        val service=r.c.getBean(WarehouseEntryApplicationService::class.java)
        val confirmation=service.applicationContext()
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val first=r.worker("first"){r.db {
            service.saveConfirmed(entryProposal(),confirmation.orgId,confirmation.revision)
            entered.countDown();check(release.await(15,TimeUnit.SECONDS))
            if(rollback)throw IllegalStateException("rollback first creation")
            "created"
        }}
        try {
            if(!entered.await(10,TimeUnit.SECONDS)) {first.get(1,TimeUnit.SECONDS).getOrThrow();error("first creation did not reach the transaction hold")}
            val second=r.worker("second"){service.saveConfirmed(entryProposal(),confirmation.orgId,confirmation.revision);"created"}
            r.awaitSecondProgress(second);release.countDown()
            val a=first.get(15,TimeUnit.SECONDS);val b=second.get(15,TimeUnit.SECONDS)
            if(rollback){assertEquals("rollback first creation",a.exceptionOrNull()?.message);assertEquals("created",b.getOrThrow())}
            else {assertEquals("created",a.getOrThrow());assertTrue(b.exceptionOrNull() is BadRequestException)}
            r.db {assertEquals(1L,WarehouseEntryApplicationDao.count())}
        } finally {release.countDown()}
    }

}
