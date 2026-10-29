package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.file.*
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.sys.file.dao.*
import io.hizhengfu.cloud.sys.file.service.FileOriginalDownloadService
import io.hizhengfu.cloud.sys.file.service.storage.*
import jdk.jfr.Recording
import jdk.jfr.consumer.RecordingFile
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.dao.entityCache
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.mock
import org.springframework.context.annotation.*
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.support.TransactionTemplate
import org.sqlite.SQLiteDataSource
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.nio.file.Path
import java.security.MessageDigest
import java.sql.*
import java.time.Duration
import java.time.Instant
import java.util.HexFormat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

/** Measurements of real JDBC calls and JVM file-read events; no mocked timer or native service mutation. */
class ArchiveReadMeasurementRuntimeTest {
    @TempDir lateinit var dir: Path
    @Configuration(proxyBeanMethods = false) @EnableTransactionManagement(proxyTargetClass = true)
    class Transactions
    private val historyCount = 1200
    private val protectedId = "2106810000000000001"
    private val ordinaryId = "2106810000000000002"
    private val protectedBytes = ByteArray(65536) { (it % 251).toByte() }
    private val ordinaryBytes = ByteArray(65536) { ((it + 17) % 251).toByte() }
    private val active = ThreadLocal<Measurement>()

    private data class Measurement(val kind: String, val sequence: Int, val threadId: Long, val startedAt: Instant,
        var endedAt: Instant = startedAt, var elapsedNanos: Long = 0, var selectExecutions: Int = 0,
        var otherExecutions: Int = 0, var jdbcExecuteNanos: Long = 0, var cursorCalls: Int = 0,
        var cursorRows: Int = 0, var jdbcCursorNanos: Long = 0, var responseBytes: Int = 0,
        var status: Int = 0, var refusal: String? = null, var returnedSha256: String? = null,
        var originalReadOperations: Int = 0, var originalReadBytes: Long = 0, var originalReadNanos: Long = 0,
        var verifiedCopyReadOperations: Int = 0, var verifiedCopyReadBytes: Long = 0, var verifiedCopyReadNanos: Long = 0,
        var unattributedReadOperations: Int = 0, var unattributedReadBytes: Long = 0, var unattributedReadNanos: Long = 0,
        var sourceEventCoverage: String = "not-evaluated")

    /** Transparent driver delegation: count executed application SQL and time actual execute and cursor-next calls. */
    private inner class MeasuredDataSource(private val delegate: DataSource) : DataSource by delegate {
        override fun getConnection(): Connection = connection(delegate.connection)
        override fun getConnection(username: String?, password: String?): Connection = connection(delegate.getConnection(username, password))
        private fun connection(real: Connection): Connection = proxy(Connection::class.java, real) { method, args ->
            val value = invoke(real, method, args)
            when (value) {
                is CallableStatement -> statement(value, args.firstOrNull() as? String, CallableStatement::class.java)
                is PreparedStatement -> statement(value, args.firstOrNull() as? String, PreparedStatement::class.java)
                is Statement -> statement(value, null, Statement::class.java)
                else -> value
            }
        }
        private fun <T : Statement> statement(real: T, preparedSql: String?, type: Class<T>): T = proxy(type, real) { method, args ->
            val measurement = active.get()
            val executes = method.name in setOf("execute", "executeQuery", "executeUpdate", "executeLargeUpdate", "executeBatch", "executeLargeBatch")
            val sql = preparedSql ?: args.firstOrNull() as? String ?: ""
            val start = if (executes && measurement != null) System.nanoTime() else 0
            val value = try { invoke(real, method, args) } finally {
                if (start != 0L && measurement != null) {
                    measurement.jdbcExecuteNanos += System.nanoTime() - start
                    if (sql.trimStart().startsWith("SELECT", ignoreCase = true)) measurement.selectExecutions++ else measurement.otherExecutions++
                }
            }
            if (value is ResultSet) proxy(ResultSet::class.java, value) { cursorMethod, cursorArgs ->
                val current = active.get()
                val cursorStart = if (cursorMethod.name == "next" && current != null) System.nanoTime() else 0
                val result = try { invoke(value, cursorMethod, cursorArgs) } finally {
                    if (cursorStart != 0L && current != null) { current.jdbcCursorNanos += System.nanoTime() - cursorStart; current.cursorCalls++ }
                }
                if (cursorStart != 0L && result == true && current != null) current.cursorRows++
                result
            } else value
        }
    }
    private fun invoke(real: Any, method: java.lang.reflect.Method, args: Array<out Any?>): Any? =
        try { method.invoke(real, *args) } catch (failure: InvocationTargetException) { throw failure.targetException }
    private fun <T> proxy(type: Class<T>, real: T, action: (java.lang.reflect.Method, Array<out Any?>) -> Any?): T =
        type.cast(Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args -> action(method, args ?: emptyArray()) })

    @Test fun `large actual history measures authorized denied ordinary and concurrent original reads separately`() {
        val datasource = SQLiteDataSource().apply { url = "jdbc:sqlite:${dir.resolve("measurement.db")}" }
        val manager = SpringTransactionManager(MeasuredDataSource(datasource), DatabaseConfig {})
        AnnotationConfigApplicationContext().use { context ->
            context.beanFactory.registerSingleton("transactionManager", manager)
            val local = LocalFileObjectStorage(dir.toString())
            context.beanFactory.registerSingleton("local", local)
            context.beanFactory.registerSingleton("scope", CollectionScopeResolver("single", "museum"))
            context.registerBean(CollectionBusinessArchiveReadPolicy::class.java, java.util.function.Supplier {
                CollectionBusinessArchiveReadPolicy(context.getBean(CollectionFileReadAuthorization::class.java), mock(CollectionDisclosureService::class.java))
            })
            context.registerBean(FileObjectStorageService::class.java, java.util.function.Supplier {
                FileObjectStorageService(local, mock(FileStorageConfigReader::class.java), mock(RustFsStorageClient::class.java), context.getBean(FileReadAccess::class.java))
            })
            context.register(Transactions::class.java, CollectionActorDirectory::class.java, CollectionFileReadAuthorization::class.java,
                FileReadAccess::class.java, CollectionPermanentArchiveFileReadGuard::class.java, FileOriginalDownloadService::class.java)
            context.refresh(); SpringContextHolder().setApplicationContext(context)
            val tx = TransactionTemplate(manager)
            fun <T> db(body: () -> T): T = requireNotNull(tx.execute { body() })
            db {
                originalReaderSchema()
                SchemaUtils.create(CollectionOrgs, CollectionPermanentArchives, CollectionPermanentArchiveFiles,
                    CollectionPermanentOriginalTransitions, CollectionPermanentOriginalRecoveries, Files, FileContents)
                CollectionOrgDao.new("museum") { name = "独立性能测量馆"; code = "DEV-MEASURE"; systemGroupId = "museum" }
                originalReader("authorized", CollectionConst.LEDGER_FINANCE)
                originalReader("unauthorized", CollectionConst.SELECT)
                actorRole("authorized", "GENERAL_AUTHORIZED_FILE", setOf("FILE_SELECT"))
                actorRole("unauthorized", "GENERAL_UNAUTHORIZED_FILE", setOf("FILE_SELECT"))
                val archive = CollectionPermanentArchiveDao.new("measure-archive") {
                    orgId = "museum"; bizType = LedgerReconciliationService.BIZ; bizId = "isolated-history"
                    phase = "CONFIRMED"; snapshot = "{\"synthetic\":true}"; digest = CollectionCareEvidence.digest(snapshot)
                }
                fun recorded(id: String, path: String, bytes: ByteArray, referenceId: String?) {
                    java.nio.file.Files.write(dir.resolve(path), bytes)
                    val file = FileDao.new(id) { name = path; this.path = path; ext = "bin"; size = bytes.size.toLong(); secure = false
                        hash = HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(bytes)) }
                    if (referenceId != null) CollectionPermanentArchiveFileDao.new(referenceId) {
                        orgId = "museum"; archiveId = archive.id.value; bizId = archive.bizId; fileId = id
                        metadata = CollectionArchiveFileIdentity.metadata(file, "museum", archive.bizType, archive.bizId,
                            FileReadPath.keys(path, "LOCAL", local), local.openStream(path).use { CollectionOriginalContent.read(it) })
                    }
                }
                repeat(historyCount) { n ->
                    recorded("history-$n", "history-$n.bin", "DEV-HISTORY-$n".toByteArray(), "a-${n.toString().padStart(5, '0')}")
                    if (n % 200 == 199) { org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().entityCache.flush(); org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().entityCache.clear() }
                }
                recorded(protectedId, "protected.bin", protectedBytes, "zz-target")
                recorded(ordinaryId, "ordinary.bin", ordinaryBytes, null)
            }
            fun projection() = db {
                listOf(CollectionPermanentArchives, CollectionPermanentArchiveFiles, Files, FileContents).associate { table ->
                    table.tableName to table.selectAll().map { row -> table.columns.associate { col -> col.name to row[col]?.toString() } }.sortedBy { JsonUtils.toJson(it) }
                }
            }
            val before = projection()
            val originalPaths = setOf(dir.resolve("protected.bin").toRealPath().toString(), dir.resolve("ordinary.bin").toRealPath().toString(),
                dir.resolve("protected.bin").toString(), dir.resolve("ordinary.bin").toString())
            assertEquals(historyCount + 1, db { CollectionPermanentArchiveFiles.selectAll().count().toInt() })
            assertEquals(historyCount + 2, java.nio.file.Files.list(dir).use { paths -> paths.filter { it.fileName.toString().endsWith(".bin") }.count().toInt() })
            val service = context.getBean(FileOriginalDownloadService::class.java)
            fun read(kind: String, sequence: Int): Measurement {
                val allowed = kind != "unauthorized"
                val actor = if (kind == "authorized") "authorized" else "unauthorized"
                val permissions = listOf(if (actor == "authorized") CollectionConst.LEDGER_FINANCE else CollectionConst.SELECT, "FILE_SELECT")
                val user = SecurityUser(id = actor, name = actor, username = actor, password = "", groupId = "museum", groupIds = setOf("museum"),
                    postIds = emptySet(), dataType = 0, roles = emptySet(), authorities = permissions.map(::SimpleGrantedAuthority), enabled = true)
                val response = MockHttpServletResponse()
                SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(user, null, user.authorities)
                val measurement = Measurement(kind, sequence, Thread.currentThread().id, Instant.now())
                val start = System.nanoTime(); active.set(measurement)
                try {
                    if (allowed) {
                        service.download(if (kind == "ordinary") ordinaryId else protectedId, response)
                        val expected = if (kind == "ordinary") ordinaryBytes else protectedBytes
                        assertArrayEquals(expected, response.contentAsByteArray); assertEquals(200, response.status)
                        assertEquals(if (kind == "ordinary") "not-recorded" else "sha256-verified", response.getHeader("X-File-Content-Integrity"))
                        measurement.returnedSha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(response.contentAsByteArray))
                    } else {
                        measurement.refusal = assertThrows(BadRequestException::class.java) { service.download(protectedId, response) }.message
                        assertEquals(0, response.contentAsByteArray.size); assertNotEquals(304, response.status)
                    }
                    measurement.responseBytes = response.contentAsByteArray.size; measurement.status = response.status
                    assertTrue(measurement.selectExecutions > 0, "Measurement must observe actual JDBC SELECT execution")
                    assertEquals(0, measurement.otherExecutions, "The read workload must not issue application SQL writes")
                    return measurement
                } finally { measurement.elapsedNanos = System.nanoTime() - start; measurement.endedAt = Instant.now(); active.remove(); SecurityContextHolder.clearContext() }
            }
            fun record(name: String, action: () -> List<Measurement>): List<Measurement> = Recording().use { recording ->
                recording.enable("jdk.FileRead").withThreshold(Duration.ZERO).withStackTrace()
                recording.start()
                val measurements = action()
                recording.stop()
                val output = dir.resolve("$name.jfr"); recording.dump(output)
                var totalFileReadEvents = 0
                RecordingFile(output).use { events -> while (events.hasMoreEvents()) {
                    val event = events.readEvent()
                    if (event.eventType.name != "jdk.FileRead") continue
                    totalFileReadEvents++
                    val thread = event.thread ?: continue
                    val measured = measurements.singleOrNull { it.threadId == thread.javaThreadId && !event.startTime.isBefore(it.startedAt) && !event.startTime.isAfter(it.endedAt) } ?: continue
                    val path = event.getString("path")
                    val bytes = event.getLong("bytesRead").coerceAtLeast(0)
                    val nanos = event.duration.toNanos()
                    if (path in originalPaths) {
                        measured.originalReadOperations++; measured.originalReadBytes += bytes; measured.originalReadNanos += nanos
                    } else if (path != null && Path.of(path).fileName.toString().startsWith("verified-original-")) {
                        measured.verifiedCopyReadOperations++; measured.verifiedCopyReadBytes += bytes; measured.verifiedCopyReadNanos += nanos
                    } else {
                        measured.unattributedReadOperations++; measured.unattributedReadBytes += bytes; measured.unattributedReadNanos += nanos
                    }
                } }
                java.nio.file.Files.delete(output)
                measurements.forEach { m -> m.sourceEventCoverage = when {
                    m.kind == "unauthorized" -> "Denied before original storage opens; no named protected source events"
                    m.originalReadBytes == 65536L -> "Complete named source byte coverage"
                    else -> "JDK17 pathname-less channel events are unattributed; named source metrics are observed lower bounds"
                } }
                println("ARCHIVE_READ_FILESYSTEM_EVENTS " + JsonUtils.toJson(mapOf("phase" to name, "totalJvmFileReadEvents" to totalFileReadEvents,
                    "measurements" to measurements)))
                measurements.forEach { m ->
                    if (m.kind == "unauthorized") assertEquals(0, m.originalReadBytes, "Denied reads must not consume protected source bytes")
                    else if (name == "sequential") { assertTrue(m.originalReadOperations > 0, "JFR must observe actual LOCAL file reads"); assertEquals(65536L, m.originalReadBytes) }
                    if (m.kind == "authorized") assertTrue(m.verifiedCopyReadOperations > 0, "Protected bytes must actually pass through verified copies")
                    println("ARCHIVE_READ_MEASUREMENT " + JsonUtils.toJson(mapOf("phase" to name, "measurement" to m)))
                }
                measurements
            }
            val sequential = record("sequential") { listOf("authorized", "unauthorized", "ordinary").mapIndexed { n, kind -> read(kind, n) } }
            val ready = CountDownLatch(4); val release = CountDownLatch(1)
            val executor = Executors.newFixedThreadPool(4)
            var concurrentWallNanos = 0L
            var maximumOverlapping = 0
            val concurrent = try { record("concurrent") {
                val futures = (0 until 4).map { worker -> executor.submit<List<Measurement>> {
                    ready.countDown(); check(release.await(10, TimeUnit.SECONDS))
                    listOf("authorized", "unauthorized", "ordinary").mapIndexed { n, kind -> read(kind, worker * 3 + n) }
                } }
                assertTrue(ready.await(10, TimeUnit.SECONDS)); val start = System.nanoTime(); release.countDown()
                val result = futures.flatMap { it.get(120, TimeUnit.SECONDS) }
                concurrentWallNanos = System.nanoTime() - start
                maximumOverlapping = result.flatMap { listOf(it.startedAt to 1, it.endedAt to -1) }.sortedBy { it.first }.runningFold(0) { n, pair -> n + pair.second }.max()
                assertTrue(maximumOverlapping >= 2, "Independent requests must overlap in actual execution")
                result
            } } finally { release.countDown(); executor.shutdownNow(); assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS)) }
            assertEquals(before, projection(), "Measured requests must not mutate archived sources or file registration")
            assertEquals(12, concurrent.size)
            println("ARCHIVE_READ_MEASUREMENT_SUMMARY " + JsonUtils.toJson(mapOf(
                "database" to "isolated SQLite / real Spring transaction proxies", "historyAttachments" to historyCount,
                "protectedTargetOrdinal" to historyCount + 1, "fetchSize" to 200, "realLocalFiles" to historyCount + 2,
                "originalBytesEach" to 65536, "sequentialRequests" to sequential.size, "concurrentRequests" to concurrent.size,
                "workerThreads" to 4, "maximumOverlappingRequests" to maximumOverlapping, "concurrentWallNanos" to concurrentWallNanos,
                "concurrentResponsesByteExact" to 8, "concurrentDeniedRequests" to 4, "sourceProjectionUnchanged" to true,
                "filesystemMeasurement" to "JFR jdk.FileRead threshold=0: actual original and verified-copy stream reads; stat/realpath/open/close are outside event timing",
                "filesystemCoverage" to "Sequential original/ordinary source byte coverage asserted. Concurrent pathname-less channel events retained as unattributed; zero named bytes is not zero actual source I/O.",
                "databaseMeasurement" to "Transparent real JDBC execute SELECT count and duration, plus ResultSet.next row fetch calls/duration; excludes driver-internal BEGIN/COMMIT",
                "wallMeasurement" to "Actual full FileOriginalDownloadService execution with JVM/JDBC/JFR instrumentation, not production latency certification",
            )))
        }
    }
}
