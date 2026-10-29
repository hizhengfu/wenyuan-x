package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.sys.file.dao.*
import io.hizhengfu.cloud.sys.system.dao.*
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.dao.entityCache
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.*
import org.postgresql.ds.PGSimpleDataSource
import org.springframework.aop.framework.ProxyFactory
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.support.StaticApplicationContext
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource
import org.springframework.transaction.interceptor.TransactionInterceptor
import org.springframework.transaction.support.TransactionTemplate
import org.sqlite.SQLiteDataSource
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.Properties
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource

/** Current actor directory, actual archive INSERT and Spring transaction rollback. */
class InventoryWriterRuntimeTest {
    @TempDir lateinit var dir: Path
    private val at = LocalDateTime.now().minusHours(8).withNano(0)
    private val tables = arrayOf(CollectionOrgs, Collections, CollectionLedgerLogs, Warehouses, Loans, LoanComplianceRecords,
        Requisitions, CollectionReturns, InventoryBatches, Inventories, InventoryAnnualReports,
        CollectionIncidents, CollectionIncidentRecords, CollectionIncidentItems,
        CollectionPermanentArchives, CollectionPermanentArchiveFiles, Files,
        Users, Groups, Roles, RoleUsers, RolePermissions, Permissions, UserGroups)

    private class Runtime(val tx: TransactionTemplate, val service: InventoryComplianceService,
        val incidents: CollectionIncidentService, val scope: CollectionScopeResolver, val postgres: Boolean,
        val source: DataSource, val schema: String, val archives: CollectionPermanentArchiveService) {
        var incidentId: String? = null
        fun <T> db(block: () -> T): T = tx.execute { block() }
        fun login(id: String = "operator") {
            val user = db { CollectionActorDirectory(scope).requireActive(id) }
            SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(user, null, user.authorities)
        }
    }

    private fun fixture(block: (Runtime) -> Unit) {
        val schema = "inventory_writer_" + UUID.randomUUID().toString().replace("-", "")
        val pg = System.getProperty("collection.test.pg.config")?.let { path ->
            val properties = Properties().apply { Path.of(path).toFile().inputStream().use(::load) }
            PGSimpleDataSource().apply {
                setURL(properties.getProperty("url")); user = properties.getProperty("user"); password = properties.getProperty("password")
                connection.use { it.createStatement().use { statement -> statement.execute("CREATE SCHEMA $schema") } }
                currentSchema = schema
                applicationName = schema
            }
        }
        val source: DataSource = pg ?: SQLiteDataSource().apply { url = "jdbc:sqlite:${dir.resolve("writer.db")}" }
        val manager = SpringTransactionManager(source, DatabaseConfig {})
        val scope = CollectionScopeResolver("single", "museum")
        val binder = mock(BusinessAttachmentBinder::class.java).also { value ->
            `when`(value.resolveSingle(anyString())).thenAnswer { it.getArgument<String>(0) }
        }
        val users = mock(CollectionResponsibleUserService::class.java)
        val archives = CollectionPermanentArchiveService(binder, scope, readAccess = unprotectedArchiveReadAccessProvider())
        val incidents = CollectionIncidentService(scope, users, archives, CollectionIncidentSourceService())
        val factory = ProxyFactory(InventoryComplianceService(scope, users, archives, incidents)).apply {
            isProxyTargetClass = true
            addAdvice(TransactionInterceptor().apply {
                transactionManager = manager; transactionAttributeSource = AnnotationTransactionAttributeSource()
            })
        }
        try { StaticApplicationContext().use { context ->
            context.beanFactory.registerSingleton("cacheManager", ConcurrentMapCacheManager())
            context.refresh(); SpringContextHolder().setApplicationContext(context)
            val runtime = Runtime(TransactionTemplate(manager), factory.proxy as InventoryComplianceService, incidents, scope, pg != null, source, schema, archives)
            runtime.db {
                SchemaUtils.create(*tables)
                originalReaderSchema(); originalReader("operator", CollectionConst.STORAGE)
                actorRole("operator", "FILE_operator", setOf(CollectionConst.SELECT, "FILE_SELECT"))
                CollectionOrgDao.new("museum") { name = "合成本馆"; code = "M"; ownership = EnumOwnership.STATE; systemGroupId = "museum" }
                CollectionOrgDao.new("other") { name = "合成外馆"; code = "O"; ownership = EnumOwnership.STATE; systemGroupId = "other" }
                WarehouseDao.new("store") { orgId = "museum"; name = "合成库房"; sceneType = EnumLocationScene.STORAGE }
                listOf("one", "two").forEachIndexed { index, id -> CollectionDao.new(id) {
                    orgId = "museum"; name = "合成藏品$id"; totalRegNo = id; nature = EnumCollectionNature.RELIC
                    categoryId = "category-$index"; level = EnumRelicLevel.GENERAL; lifeStatus = EnumCollectionStatus.IN_STORE
                    warehouseId = "store"; registeredAt = at.minusMonths(1)
                } }
                FileDao.new("proof") { name = "合成签署原件.pdf"; path = "proof"; hash = "proof"; size = 100; ext = "pdf" }
                InventoryBatchDao.new("batch") { orgId = "museum"; batchName = "合成全馆清点" }
                incidentReaderFixture("operator", "keeper", "leader", "custodian")
                TransactionManager.current().exec("CREATE TABLE inventory_writer_fault (hits INTEGER NOT NULL)")
                TransactionManager.current().exec("INSERT INTO inventory_writer_fault VALUES (0)")
            }
            runtime.login()
            try { block(runtime) } finally { SecurityContextHolder.clearContext() }
        } } finally { pg?.connection?.use { it.createStatement().use { statement -> statement.execute("DROP SCHEMA $schema CASCADE") } } }
    }

    private fun plan() = InventoryPlanVo(at.year, EnumInventoryMethod.FULL, EnumInventoryPurpose.ROUTINE, "合成全馆账物卡照核验", "keeper", "leader", "proof")
    private fun check(id: String, abnormal: Boolean = false) = InventoryItemCheckVo(id, at.plusMinutes(10), setOf("custodian"),
        "合成实物所在位置", true, true, true, true, "合成保存状况", abnormal, "proof", "proof", "proof", "proof",
        findings = if (abnormal) "合成发现裂隙" else null, protectiveMeasures = if (abnormal) "合成停止使用并防护" else null)
    private fun action() = InventoryIssueActionVo("one", at.plusHours(1), "合成原因调查", "合成保护及追查", "custodian", false,
        at.plusHours(5), false, "合成非重大情况说明", "proof")
    private fun resolved(id: String) = action().copy(resolved = true, followUpAt = null, safeForUse = true,
        keeperUserId = "keeper", leaderUserId = "leader", resolutionApprovedAt = at.plusHours(1), signedResolutionFileIds = "proof", incidentId = id)
    private fun completion() = InventoryCompletionVo(at.plusHours(2), "keeper", "leader", "proof")
    private fun report() = InventoryAnnualReportVo(at.year, at.plusHours(3), "leader", "proof", "合成主管机关", "合成清点报送", at.plusHours(4), "proof")

    private fun prepare(runtime: Runtime, kind: String) {
        if (kind == "begin") return
        runtime.service.begin("batch", plan())
        if (kind == "check") return
        val abnormal = kind in setOf("action", "clearance")
        runtime.service.check("batch", check("one", abnormal))
        runtime.service.check("batch", check("two"))
        if (kind == "clearance") runtime.db {
            val support = IncidentSourceTestSupport(runtime.incidents, at, "proof")
            runtime.incidentId = support.create(IncidentSourceVo(InventoryComplianceService.BIZ, "batch"),
                listOf(IncidentItemVo("one", true, EnumIncidentSecurity.NONE, false, "合成裂隙")))
            support.close(runtime.incidentId!!)
        }
        if (kind == "report") runtime.service.complete("batch", completion())
        runtime.login()
    }

    private fun operate(runtime: Runtime, kind: String): String = when (kind) {
        "begin" -> runtime.service.begin("batch", plan())
        "check" -> runtime.service.check("batch", check("one"))
        "action" -> runtime.service.action("batch", action())
        "clearance" -> runtime.service.action("batch", resolved(runtime.incidentId!!))
        "complete" -> runtime.service.complete("batch", completion())
        "report" -> runtime.service.report(report())
        else -> error(kind)
    }

    private fun trigger(runtime: Runtime, kind: String, change: String) = runtime.db {
        val phase = when (kind) {
            "begin" -> "NEW.phase='STARTED'"; "check" -> "NEW.phase LIKE 'CHECK_%'"
            "action" -> "NEW.phase LIKE 'ACTION_%'"; "clearance" -> "NEW.phase LIKE 'INCIDENT_CLEARANCE_%'"
            "complete" -> "NEW.phase='COMPLETED'"; else -> "NEW.phase='REPORTED'"
        }
        val business = if (kind == "report") InventoryComplianceService.ANNUAL else InventoryComplianceService.BIZ
        val predicate = "NEW.biz_type='$business' AND $phase"
        val mutation = qualificationChange(runtime, change)
        val statements = "UPDATE inventory_writer_fault SET hits=hits+1; $mutation;"
        if (runtime.postgres) {
            TransactionManager.current().exec("CREATE FUNCTION mutate_inventory_writer() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF $predicate THEN $statements END IF; RETURN NEW; END $$")
            TransactionManager.current().exec("CREATE TRIGGER mutate_inventory_writer AFTER INSERT ON ${CollectionPermanentArchives.tableName} FOR EACH ROW EXECUTE FUNCTION mutate_inventory_writer()")
        } else TransactionManager.current().exec("CREATE TRIGGER mutate_inventory_writer AFTER INSERT ON ${CollectionPermanentArchives.tableName} WHEN $predicate BEGIN $statements END")
    }
    private fun dropTrigger(runtime: Runtime) = runtime.db {
        TransactionManager.current().exec("DROP TRIGGER mutate_inventory_writer" + if (runtime.postgres) " ON ${CollectionPermanentArchives.tableName}" else "")
        if (runtime.postgres) TransactionManager.current().exec("DROP FUNCTION mutate_inventory_writer()")
    }
    private fun snapshot(runtime: Runtime) = runtime.db { settledSafetySelectionState(*tables) }
    private fun hits(runtime: Runtime) = runtime.db {
        TransactionManager.current().exec("SELECT hits FROM inventory_writer_fault") { row -> row.next(); row.getInt(1) }!!
    }

    private fun qualificationChange(runtime: Runtime, change: String): String = when (change) {
        "permission" -> "DELETE FROM ${RolePermissions.tableName} WHERE role_id='role:FILE_operator' AND permission_id='${CollectionConst.STORAGE}'"
        "account" -> "UPDATE ${Users.tableName} SET enabled=" + (if (runtime.postgres) "false" else "0") + " WHERE id='operator'"
        "museum" -> "UPDATE ${CollectionOrgs.tableName} SET org_status=${EnumOrgStatus.DISABLED.ordinal} WHERE id='museum'"
        else -> error(change)
    }

    private fun restoreQualification(runtime: Runtime, change: String) = runtime.db {
        TransactionManager.current().exec(when (change) {
            "permission" -> "INSERT INTO ${RolePermissions.tableName}(role_id,permission_id) VALUES ('role:FILE_operator','${CollectionConst.STORAGE}')"
            "account" -> "UPDATE ${Users.tableName} SET enabled=true WHERE id='operator'"
            "museum" -> "UPDATE ${CollectionOrgs.tableName} SET org_status=${EnumOrgStatus.ENABLED.ordinal} WHERE id='museum'"
            else -> error(change)
        })
    }

    /** Both requests are valid for the same prepared director-departure batch. */
    @Tag("postgres-concurrency")
    @ParameterizedTest
    @ValueSource(strings = ["begin", "complete"])
    fun `PostgreSQL director departure check and batch writer share one lock order`(kind: String) = fixture { runtime ->
        assertTrue(runtime.postgres, "Explicit isolated PostgreSQL configuration required")
        val directory = CollectionActorDirectory(runtime.scope)
        val departures = LeadershipDepartureService(runtime.scope, directory,
            CollectionResponsibleUserService(directory, runtime.scope), runtime.archives)
        runtime.db {
            UserDao.new("old") { name = "合成离任人"; loginName = "old"; password = "unused"; enabled = false; groupId = "museum" }
            actorRole("keeper", CollectionConst.ROLE_STORAGE_HEAD)
            actorRole("leader", CollectionConst.ROLE_DIRECTOR)
            actorRole("custodian", CollectionConst.ROLE_KEEPER)
        }
        val departure = LeadershipDepartureRequest(EnumLeadershipPost.BOTH, "old", at.minusYears(2), at.plusDays(3),
            "proof", "proof", "leader", "proof", "keeper", "合成全馆任职责任核对", "proof")
        val departureId = runtime.db { departures.register("museum", departure) }
        runtime.login("keeper")
        runtime.db { departures.confirm("museum", departureId, "PK", CollectionCareSignatureVo("合成保管负责人核对", "proof")) }
        runtime.login("leader")
        runtime.db { departures.confirm("museum", departureId, "PS", CollectionCareSignatureVo("合成接任人核对", "proof")) }
        runtime.login()
        val p = plan().copy(purpose = EnumInventoryPurpose.DIRECTOR_DEPARTURE, leavingUserId = "old",
            successorUserId = "leader", leavingAt = departure.leavingAt, responsibilityFileIds = "proof", leadershipDepartureId = departureId)
        val c = completion().copy(actualHandoverAt = at.plusHours(1), handoverFileIds = "proof",
            supervisorAuthority = "合成主管机关", supervisorNames = setOf("合成监督人员"), supervisionAt = at.plusMinutes(10),
            appointmentFileIds = "proof", supervisionFileIds = "proof")
        runtime.service.begin("batch", p)
        runtime.service.check("batch", check("one"))
        runtime.service.check("batch", check("two"))
        val before = snapshot(runtime)
        val authentication = SecurityContextHolder.getContext().authentication
        val pool = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(1)
        val release = CountDownLatch(1)
        val holderPid = AtomicInteger()
        val writerPid = AtomicInteger()
        fun sqlState(failure: Throwable?): String = generateSequence(failure) { it.cause }
            .filterIsInstance<java.sql.SQLException>().mapNotNull { it.sqlState }.firstOrNull() ?: "none"
        try {
            val holder = pool.submit<Result<String>> {
                SecurityContextHolder.getContext().authentication = authentication
                try { runCatching { runtime.db {
                    TransactionManager.current().exec("SELECT pg_backend_pid()") { row -> row.next(); holderPid.set(row.getInt(1)) }
                    // Same first lock as check(); the gate exposes the possible opposing lock order.
                    TransactionManager.current().exec("SELECT id FROM ${CollectionOrgs.tableName} WHERE id='museum' FOR UPDATE") { row -> assertTrue(row.next()) }
                    ready.countDown()
                    check(release.await(15, TimeUnit.SECONDS))
                    runtime.service.check("batch", check("one"))
                } } } finally { SecurityContextHolder.clearContext() }
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS))
            val writer = pool.submit<Result<String>> {
                SecurityContextHolder.getContext().authentication = authentication
                try { runCatching { runtime.db {
                    TransactionManager.current().exec("SELECT pg_backend_pid()") { row -> row.next(); writerPid.set(row.getInt(1)) }
                    if (kind == "begin") runtime.service.begin("batch", p) else runtime.service.complete("batch", c)
                } } } finally { SecurityContextHolder.clearContext() }
            }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8)
            var waiting = false
            while (!waiting && System.nanoTime() < deadline) {
                assertFalse(writer.isDone, "Batch writer must actually wait for the checking transaction")
                waiting = runtime.source.connection.use { connection ->
                    connection.prepareStatement("SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE pid=? AND application_name=? AND wait_event_type='Lock' AND ?=ANY(pg_blocking_pids(pid)))").use { statement ->
                        statement.setInt(1, writerPid.get()); statement.setString(2, runtime.schema); statement.setInt(3, holderPid.get())
                        statement.executeQuery().use { row -> row.next(); row.getBoolean(1) }
                    }
                }
                if (!waiting) Thread.sleep(20)
            }
            assertTrue(waiting, "Observe this writer PID blocked by the actual checking transaction before proceeding")
            release.countDown()
            val checked = holder.get(15, TimeUnit.SECONDS)
            val written = writer.get(15, TimeUnit.SECONDS)
            println("INVENTORY_DIRECTOR_LOCK_ORDER kind=$kind holder=${holderPid.get()} writer=${writerPid.get()} observed=true checkSqlState=${sqlState(checked.exceptionOrNull())} writerSqlState=${sqlState(written.exceptionOrNull())} schema=${runtime.schema}")
            assertEquals("batch", checked.getOrThrow(), "Valid repeated item check must succeed")
            assertEquals("batch", written.getOrThrow(), "Valid batch write must succeed")
            val saved = snapshot(runtime)
            if (kind == "begin") assertEquals(before, saved, "Repeated start and check must be completely idempotent")
            else {
                val changed = setOf(InventoryBatches.tableName, CollectionPermanentArchives.tableName, CollectionPermanentArchiveFiles.tableName)
                before.filterKeys { it !in changed }.forEach { (table, rows) -> assertEquals(rows, saved[table], table) }
                for (table in changed - InventoryBatches.tableName) {
                    assertTrue(saved.getValue(table).containsAll(before.getValue(table)), "Existing originals must remain: $table")
                    assertEquals(before.getValue(table).size + 1, saved.getValue(table).size, "Only one completion original/reference: $table")
                }
                runtime.db {
                    val batch = InventoryBatchDao["batch"]
                    assertEquals(p, batch.plan); assertEquals(c, batch.completion)
                    assertEquals(listOf(check("one"), check("two")), batch.checks!!.items)
                    InventoryArchiveEvidence(runtime.archives).verify(batch)
                }
            }
            assertEquals("batch", if (kind == "begin") runtime.service.begin("batch", p) else runtime.service.complete("batch", c))
            assertEquals(saved, snapshot(runtime), "Repeated batch write must preserve every settled row")
        } finally {
            release.countDown(); pool.shutdownNow()
            assertTrue(pool.awaitTermination(20, TimeUnit.SECONDS), "Both real transactions must end before schema cleanup")
        }
    }

    /** Each parameter runs two fresh schemas: a committed change and its rollback control. */
    @Tag("postgres-concurrency")
    @ParameterizedTest
    @CsvSource("begin,permission", "begin,account", "begin,museum", "check,permission", "check,account", "check,museum",
        "action,permission", "action,account", "action,museum", "clearance,permission", "clearance,account", "clearance,museum",
        "complete,permission", "complete,account", "complete,museum", "report,permission", "report,account", "report,museum")
    fun `PostgreSQL lock wait rereads inventory writer qualification after commit or rollback`(kind: String, change: String) {
        assertNotNull(System.getProperty("collection.test.pg.config"), "Explicit isolated PostgreSQL configuration required")
        for (commit in listOf(true, false)) fixture { runtime ->
            assertTrue(runtime.postgres)
            prepare(runtime, kind)
            val before = snapshot(runtime)
            val authentication = SecurityContextHolder.getContext().authentication
            val pool = Executors.newFixedThreadPool(2)
            val ready = CountDownLatch(1)
            val release = CountDownLatch(1)
            val writerPid = AtomicInteger()
            try {
                val holder = pool.submit<Result<Map<String, List<List<String?>>>>> {
                    SecurityContextHolder.getContext().authentication = authentication
                    try { runCatching { runtime.db {
                        val table = if (kind in setOf("begin", "complete")) InventoryBatches.tableName else CollectionOrgs.tableName
                        val id = if (kind in setOf("begin", "complete")) "batch" else "museum"
                        TransactionManager.current().exec("SELECT id FROM $table WHERE id='$id' FOR UPDATE") { row -> assertTrue(row.next()) }
                        TransactionManager.current().exec(qualificationChange(runtime, change))
                        val changed = settledSafetySelectionState(*tables)
                        ready.countDown()
                        check(release.await(15, TimeUnit.SECONDS)) { "Synthetic lock holder was not released" }
                        if (!commit) throw IllegalStateException("synthetic qualification rollback")
                        changed
                    } } } finally { SecurityContextHolder.clearContext() }
                }
                assertTrue(ready.await(10, TimeUnit.SECONDS), "Lock holder must enter its actual transaction")
                val writer = pool.submit<Result<String>> {
                    SecurityContextHolder.getContext().authentication = authentication
                    try { runCatching { runtime.db {
                        TransactionManager.current().exec("SELECT pg_backend_pid()") { row -> row.next(); writerPid.set(row.getInt(1)) }
                        // Preserve pre-wait DAO state, as a request can do before its write entry.
                        assertNotNull(InventoryBatchDao.findById("batch"))
                        operate(runtime, kind)
                    } } } finally { SecurityContextHolder.clearContext() }
                }
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8)
                var waiting = false
                while (!waiting && System.nanoTime() < deadline) {
                    assertFalse(writer.isDone, "Write request must be waiting for the held row")
                    waiting = runtime.source.connection.use { connection ->
                        connection.prepareStatement("SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE pid=? AND application_name=? AND wait_event_type='Lock')").use { statement ->
                            statement.setInt(1, writerPid.get()); statement.setString(2, runtime.schema)
                            statement.executeQuery().use { row -> row.next(); row.getBoolean(1) }
                        }
                    }
                    if (!waiting) Thread.sleep(20)
                }
                assertTrue(waiting, "Must observe this writer's PostgreSQL backend actually waiting on a lock")
                release.countDown()
                val held = holder.get(15, TimeUnit.SECONDS)
                val written = writer.get(15, TimeUnit.SECONDS)
                if (commit) {
                    val expected = held.getOrThrow()
                    assertInstanceOf(BadRequestException::class.java, written.exceptionOrNull(), "Committed current qualification change must reject old principal")
                    assertEquals(expected, snapshot(runtime), "Rejected request must preserve all pre-request facts and the committed qualification change")
                    restoreQualification(runtime, change)
                    assertEquals(before, snapshot(runtime), "Restoring only the qualification must reproduce the pre-request state")
                } else {
                    assertInstanceOf(IllegalStateException::class.java, held.exceptionOrNull())
                    assertEquals("synthetic qualification rollback", held.exceptionOrNull()?.message)
                    written.getOrThrow()
                }
                val id = operate(runtime, kind)
                val saved = snapshot(runtime)
                assertEquals(id, operate(runtime, kind), "Restored/rolled-back qualification must support idempotent retry")
                assertEquals(saved, snapshot(runtime), "Repeated legal request must preserve saved facts and archives")
                println("INVENTORY_WRITER_LOCK_WAIT kind=$kind change=$change commit=$commit pid=${writerPid.get()} observed=true rejected=${commit} retry=true idempotent=true schema=${runtime.schema}")
            } finally {
                release.countDown()
                pool.shutdownNow()
                assertTrue(pool.awaitTermination(20, TimeUnit.SECONDS), "Both isolated transactions must end before schema cleanup")
            }
        }
    }

    @Tag("postgres-concurrency")
    @ParameterizedTest
    @ValueSource(strings = ["begin", "check", "action", "clearance", "complete", "report"])
    fun `PostgreSQL cached inventory batch cannot retain old museum after lock wait`(kind: String) {
        assertNotNull(System.getProperty("collection.test.pg.config"))
        for (commit in listOf(true, false)) fixture { runtime ->
            prepare(runtime, kind)
            val before = snapshot(runtime)
            val authentication = SecurityContextHolder.getContext().authentication
            val pool = Executors.newFixedThreadPool(2)
            val ready = CountDownLatch(1)
            val release = CountDownLatch(1)
            val writerPid = AtomicInteger()
            try {
                val holder = pool.submit<Result<Map<String, List<List<String?>>>>> {
                    SecurityContextHolder.getContext().authentication = authentication
                    try { runCatching { runtime.db {
                        val table = if (kind in setOf("begin", "complete")) InventoryBatches.tableName else CollectionOrgs.tableName
                        val id = if (kind in setOf("begin", "complete")) "batch" else "museum"
                        TransactionManager.current().exec("SELECT id FROM $table WHERE id='$id' FOR UPDATE") { row -> assertTrue(row.next()) }
                        TransactionManager.current().exec("UPDATE ${InventoryBatches.tableName} SET org_id='other' WHERE id='batch'")
                        val changed = settledSafetySelectionState(*tables)
                        ready.countDown()
                        check(release.await(15, TimeUnit.SECONDS))
                        if (!commit) throw IllegalStateException("synthetic batch museum rollback")
                        changed
                    } } } finally { SecurityContextHolder.clearContext() }
                }
                assertTrue(ready.await(10, TimeUnit.SECONDS))
                val writer = pool.submit<Result<String>> {
                    SecurityContextHolder.getContext().authentication = authentication
                    try { runCatching { runtime.db {
                        TransactionManager.current().exec("SELECT pg_backend_pid()") { row -> row.next(); writerPid.set(row.getInt(1)) }
                        assertEquals("museum", InventoryBatchDao["batch"].orgId, "Request must actually cache the old museum before waiting")
                        operate(runtime, kind)
                    } } } finally { SecurityContextHolder.clearContext() }
                }
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8)
                var waiting = false
                while (!waiting && System.nanoTime() < deadline) {
                    assertFalse(writer.isDone)
                    waiting = runtime.source.connection.use { connection ->
                        connection.prepareStatement("SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE pid=? AND application_name=? AND wait_event_type='Lock')").use { statement ->
                            statement.setInt(1, writerPid.get()); statement.setString(2, runtime.schema)
                            statement.executeQuery().use { row -> row.next(); row.getBoolean(1) }
                        }
                    }
                    if (!waiting) Thread.sleep(20)
                }
                assertTrue(waiting, "Old museum must remain cached through an observed real PostgreSQL lock wait")
                release.countDown()
                val held = holder.get(15, TimeUnit.SECONDS)
                val written = writer.get(15, TimeUnit.SECONDS)
                if (commit) {
                    val expected = held.getOrThrow()
                    assertInstanceOf(BadRequestException::class.java, written.exceptionOrNull(), "Committed batch museum change must reject the cached old-museum request")
                    assertEquals(expected, snapshot(runtime), "Rejected old-museum request must not create facts or archives")
                    runtime.db { TransactionManager.current().exec("UPDATE ${InventoryBatches.tableName} SET org_id='museum' WHERE id='batch'") }
                    assertEquals(before, snapshot(runtime))
                } else {
                    assertInstanceOf(IllegalStateException::class.java, held.exceptionOrNull())
                    assertEquals("synthetic batch museum rollback", held.exceptionOrNull()?.message)
                    written.getOrThrow()
                }
                val id = operate(runtime, kind)
                val saved = snapshot(runtime)
                assertEquals(id, operate(runtime, kind))
                assertEquals(saved, snapshot(runtime))
                println("INVENTORY_BATCH_MUSEUM_WAIT kind=$kind commit=$commit pid=${writerPid.get()} observed=true rejected=$commit retry=true idempotent=true schema=${runtime.schema}")
            } finally {
                release.countDown(); pool.shutdownNow()
                assertTrue(pool.awaitTermination(20, TimeUnit.SECONDS))
            }
        }
    }

    @ParameterizedTest
    @CsvSource("begin,permission", "begin,account", "begin,museum", "check,permission", "check,account", "check,museum",
        "action,permission", "action,account", "action,museum", "clearance,permission", "clearance,account", "clearance,museum",
        "complete,permission", "complete,account", "complete,museum", "report,permission", "report,account", "report,museum")
    fun `archive SQL qualification change rolls back all inventory facts and restored retry succeeds`(kind: String, change: String) = fixture { runtime ->
        prepare(runtime, kind)
        trigger(runtime, kind, change)
        val before = snapshot(runtime)
        val failure = runCatching { operate(runtime, kind) }.exceptionOrNull()
        if (failure == null) {
            assertEquals(1, hits(runtime), "反例须确实执行归档SQL及资格变化")
            fail<Unit>("实际归档SQL撤回${change}后仍提交${kind}，预期应拒绝并回滚")
        }
        assertInstanceOf(BadRequestException::class.java, failure)
        assertEquals(before, snapshot(runtime), "清点、事故、签署原件、安全限制及当前资格须整体回滚")
        assertEquals(0, hits(runtime))
        dropTrigger(runtime)
        assertDoesNotThrow { operate(runtime, kind) }
    }
}
