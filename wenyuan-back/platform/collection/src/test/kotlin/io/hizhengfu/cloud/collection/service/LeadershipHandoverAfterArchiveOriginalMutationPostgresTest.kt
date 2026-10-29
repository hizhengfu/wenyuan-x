package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.postgresql.ds.PGSimpleDataSource
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.support.TransactionTemplate
import java.lang.reflect.InvocationTargetException
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource

/** Only eight missing PG original mutation points: coherent committed CASE body pair, and six own-write new archive header mutations.
 * Reuses existing isolated fixture construction, never invokes its accepted tests or shared dev cases.
 * The coherent CASE trigger only waits before a distinct JDBC connection changes committed history.
 * The six new uncommitted headers are changed by the writer's own trigger, never claim independent visibility.
 */
@Tag("postgres-concurrency")
class LeadershipHandoverAfterArchiveOriginalMutationPostgresTest {
    @TempDir lateinit var dir: Path
    @AfterEach fun clear() = SecurityContextHolder.clearContext()
    private data class Runtime(val delegate: Any) {
        val context = property("C") as AnnotationConfigApplicationContext
        val tx = property("Tx") as TransactionTemplate
        val source = property("Source") as DataSource
        val local = property("Local") as Path
        private fun property(name: String): Any = delegate.javaClass.getDeclaredMethod("get$name").apply { isAccessible = true }.invoke(delegate)
        fun <T> db(block: () -> T): T = tx.execute { block() }
        fun login(id: String) = delegate.javaClass.getDeclaredMethod("login", String::class.java).apply { isAccessible = true }.invoke(delegate, id)
    }
    private fun call(owner: Any, name: String, vararg arguments: Any): Any? = try {
        owner.javaClass.declaredMethods.single { it.name == name }.apply { isAccessible = true }.invoke(owner, *arguments)
    } catch (e: InvocationTargetException) { throw e.targetException }
    private fun fixture(kind: String, body: (Any, Runtime) -> Unit) {
        require(System.getProperty("collection.test.pg.config") != null)
        val owner: Any = if (kind == "C05") LeadershipDepartureAfterArchiveAuthorityPostgresTest() else WarehouseHandoverAfterArchiveAuthorityPostgresTest()
        owner.javaClass.getDeclaredField("dir").set(owner, Files.createDirectory(dir.resolve("owned-fixture")))
        val callback: (Any) -> Unit = { body(owner, Runtime(it)) }
        call(owner, "fixture", callback)
    }
    private data class TableState(val rows: Long, val digest: String)
    private fun projection(source: DataSource, schema: String, excludedArchive: String? = null): Map<String, TableState> = source.connection.use { c ->
        val names = c.prepareStatement("SELECT tablename FROM pg_tables WHERE schemaname=? ORDER BY tablename").use { s ->
            s.setString(1, schema); s.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.getString(1)) } }
        }
        names.associateWith { table ->
            val where = if (table == CollectionPermanentArchives.tableName && excludedArchive != null) " WHERE id='$excludedArchive' IS NOT TRUE" else ""
            c.createStatement().use { s ->
                s.executeQuery("SELECT count(*),md5(coalesce(string_agg(to_jsonb(t)::text,'|' ORDER BY to_jsonb(t)::text),'')) FROM \"$schema\".\"$table\" t$where").use { rs ->
                    assertTrue(rs.next()); TableState(rs.getLong(1), rs.getString(2))
                }
            }
        }
    }
    private fun bytes(root: Path) = Files.list(root).use { files -> files.sorted().toList().associate { p ->
        p.fileName.toString() to MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)).joinToString("") { "%02x".format(it) }
    } }
    private fun museum(source: DataSource, schema: String): Pair<String, Long> = source.connection.use { c -> c.createStatement().use { s ->
        s.executeQuery("SELECT md5((to_jsonb(t)-'location_mutation_version')::text),location_mutation_version FROM \"$schema\".col_org t WHERE id='museum'").use { rs ->
            assertTrue(rs.next()); rs.getString(1) to rs.getLong(2)
        }
    } }

    @ParameterizedTest(name = "coherent prior CASE body and matching digest independent rollback={0}")
    @CsvSource("false", "true")
    fun `first PK rejects coherent independently committed CASE body revision`(rollback: Boolean) = fixture("C05") { owner, r ->
        val kind = "C05"
        val source = r.source as PGSimpleDataSource
        val schema = requireNotNull(source.currentSchema)
        val departure = if (kind == "C05") r.context.getBean(LeadershipDepartureService::class.java) else null
        val handover = if (kind == "C06") r.context.getBean(WarehouseStaffingHandoverService::class.java) else null
        val id: String
        val oldArchiveId: String
        val request: WarehouseStaffingHandoverRequest?
        if (kind == "C05") {
            val ready = call(owner, "ready", r.delegate, "PK") as Pair<*, *>
            id = ready.first as String
            r.login(ready.second as String)
            oldArchiveId = r.db { CollectionPermanentArchiveDao.all().single { it.phase == "CASE_$id" }.id.value }
            request = null
        } else {
            request = call(owner, "prepared", r.delegate) as WarehouseStaffingHandoverRequest
            id = ""
            oldArchiveId = request.assignmentArchiveId
            r.login("operator")
        }
        val actor = r.db { r.context.getBean(CollectionActorDirectory::class.java).requireActive(if (kind == "C05") "keeper" else "operator") }
        assertFalse("ADMIN" in actor.roles); assertFalse(CollectionConst.CROSS_ORG in actor.permissions)
        val originalDigest = r.db { CollectionPermanentArchiveDao[oldArchiveId].digest }
        val originalSnapshot = r.db { CollectionPermanentArchiveDao[oldArchiveId].snapshot }
        val changedNode = JsonUtils.mapper.readTree(originalSnapshot)
        (changedNode.get("request") as tools.jackson.databind.node.ObjectNode).put("responsibilityStatement", "DEV-SYNTHETIC independently changed responsibility body, no real appointment or signature")
        val changedSnapshot = changedNode.toString()
        val changedDigest = CollectionCareEvidence.digest(changedSnapshot)
        assertNotEquals(originalDigest, changedDigest)
        val before = projection(source, schema)
        val beforeExceptChangedOriginal = projection(source, schema, oldArchiveId)
        assertEquals(19, before.size)
        val localBefore = bytes(r.local)
        val museumBefore = museum(source, schema)
        val phase = if (kind == "C05") "PK_$id" else "R_%"
        val business = if (kind == "C05") LeadershipDepartureEvidence.BIZ else WarehouseStaffingHandoverService.BIZ
        val marker = "09C05.coherent.after_file"
        val lockKey = System.nanoTime() and Long.MAX_VALUE
        source.connection.use { c -> c.createStatement().use { s ->
            s.execute("CREATE FUNCTION baseline_after_file_fn() RETURNS trigger AS \$\$ DECLARE p text; b text; BEGIN SELECT phase,biz_type INTO p,b FROM col_permanent_archive WHERE id=NEW.archive_id; IF b='$business' AND p LIKE '$phase' THEN PERFORM set_config('application_name','$marker',false); PERFORM pg_advisory_xact_lock($lockKey); END IF; RETURN NEW; END; \$\$ LANGUAGE plpgsql")
            s.execute("CREATE TRIGGER baseline_after_file AFTER INSERT ON col_permanent_archive_file FOR EACH ROW EXECUTE FUNCTION baseline_after_file_fn()")
        } }
        val auth = SecurityContextHolder.getContext().authentication
        val writerPid = AtomicInteger()
        val pool = Executors.newSingleThreadExecutor()
        var observed: Map<String, Any>? = null
        var outcome: Result<String>? = null
        try { source.connection.use { holder ->
            holder.autoCommit = false
            val holderPid = holder.createStatement().use { s -> s.executeQuery("SELECT pg_backend_pid(),pg_advisory_lock($lockKey)").use { rs -> rs.next(); rs.getInt(1) } }
            try {
                val future = pool.submit<Result<String>> {
                    SecurityContextHolder.getContext().authentication = auth
                    try { runCatching { r.db {
                        TransactionManager.current().exec("SELECT pg_backend_pid()") { rs -> rs.next(); writerPid.set(rs.getInt(1)) }
                        if (kind == "C05") requireNotNull(departure).confirm("museum", id, "PK", CollectionCareSignatureVo("DEV-SYNTHETIC after-file baseline verification, no physical confirmation", "signature"))
                        else requireNotNull(handover).register("store", requireNotNull(request))
                    } } } finally { SecurityContextHolder.clearContext() }
                }
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12)
                while (observed == null && System.nanoTime() < deadline) {
                    if (future.isDone) fail<Nothing>("Did not reach actual new archive-file INSERT: ${future.get().exceptionOrNull()}")
                    if (writerPid.get() != 0) source.connection.use { observer -> observer.prepareStatement("SELECT wait_event_type,wait_event,application_name,pg_blocking_pids(pid),pg_backend_pid() FROM pg_stat_activity WHERE pid=?").use { s ->
                        s.setInt(1, writerPid.get()); s.executeQuery().use { rs ->
                            if (rs.next() && rs.getString(1) == "Lock" && rs.getString(2) == "advisory") {
                                val blockers = (rs.getArray(4).array as Array<*>).map { (it as Number).toInt() }
                                assertEquals(marker, rs.getString(3)); assertTrue(holderPid in blockers)
                                assertNotEquals(holderPid, rs.getInt(5)); assertNotEquals(writerPid.get(), rs.getInt(5))
                                observed = mapOf("writerPid" to writerPid.get(), "holderPid" to holderPid, "observerPid" to rs.getInt(5), "marker" to marker, "waitEvent" to "Lock/advisory")
                            }
                        }
                    } }
                    if (observed == null) Thread.sleep(20)
                }
                assertNotNull(observed)
                assertEquals(before, projection(source, schema), "The new archive is still uncommitted after its actual file INSERT")
                holder.prepareStatement("UPDATE col_permanent_archive SET snapshot=?,digest=? WHERE id=?").use { statement ->
                    statement.setString(1, changedSnapshot); statement.setString(2, changedDigest); statement.setString(3, oldArchiveId)
                    assertEquals(1, statement.executeUpdate())
                }
                if (rollback) holder.rollback() else holder.commit()
                source.connection.use { observer -> observer.createStatement().use { s -> s.executeQuery("SELECT digest,snapshot FROM col_permanent_archive WHERE id='$oldArchiveId'").use { rs ->
                    rs.next(); assertEquals(if (rollback) originalDigest else changedDigest, rs.getString(1)); assertEquals(if (rollback) originalSnapshot else changedSnapshot, rs.getString(2))
                } } }
                holder.createStatement().use { it.execute("SELECT pg_advisory_unlock($lockKey)") }
                outcome = future.get(25, TimeUnit.SECONDS)
            } finally { holder.rollback(); holder.createStatement().use { it.execute("SELECT pg_advisory_unlock($lockKey)") } }
        } } finally { pool.shutdownNow(); assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS)) }
        val result = requireNotNull(outcome)
        val after = projection(source, schema)
        println("09_COHERENT_CASE_AFTER_FILE_RESULT " + JsonUtils.toJson(mapOf("kind" to kind, "independentRollback" to rollback,
            "schema" to schema, "oldArchiveId" to oldArchiveId, "actualWait" to observed, "declaredTables" to before.size,
            "before" to before.mapValues { (_, v) -> mapOf("rows" to v.rows, "digest" to v.digest) },
            "after" to after.mapValues { (_, v) -> mapOf("rows" to v.rows, "digest" to v.digest) },
            "localInputHashes" to localBefore, "result" to if (result.isSuccess) "success" else result.exceptionOrNull()!!::class.java.simpleName)))
        if (!rollback) {
            assertInstanceOf(BadRequestException::class.java, result.exceptionOrNull(), "Committed coherent old CASE body revision must roll back this first write")
            assertTrue(result.exceptionOrNull()!!.message!!.contains("原任职或既有确认依据已变化"), "Must reach the new retained State comparison, not fail a malformed original")
            assertEquals(beforeExceptChangedOriginal, projection(source, schema, oldArchiveId), "All rows except the deliberate pre-existing external corruption must roll back")
            source.connection.use { c -> c.prepareStatement("UPDATE col_permanent_archive SET digest=?,snapshot=? WHERE id=?").use { s ->
                s.setString(1, originalDigest); s.setString(2, originalSnapshot); s.setString(3, oldArchiveId); assertEquals(1, s.executeUpdate())
            } }
            assertEquals(before, projection(source, schema))
        } else {
            assertTrue(result.isSuccess, "An independently rolled-back baseline change must permit the legitimate write: ${result.exceptionOrNull()}")
            val changed = setOf(CollectionPermanentArchives.tableName, CollectionPermanentArchiveFiles.tableName, io.hizhengfu.cloud.sys.file.dao.FileContents.tableName, CollectionOrgs.tableName)
            assertEquals(before.filterKeys { it !in changed }, after.filterKeys { it !in changed })
            changed.filter { it != CollectionOrgs.tableName }.forEach { assertEquals(before.getValue(it).rows + 1, after.getValue(it).rows, it) }
            val museumAfter = museum(source, schema)
            assertEquals(museumBefore.first, museumAfter.first)
            assertEquals(museumBefore.second + if (kind == "C06") 1 else 0, museumAfter.second)
            assertEquals(originalDigest, r.db { CollectionPermanentArchiveDao[oldArchiveId].digest })
        }
        assertEquals(localBefore, bytes(r.local), "Existing uploaded LOCAL inputs must be retained unchanged")
    }

    @ParameterizedTest(name = "{0} new header {1} after archive-file INSERT in writer transaction")
    @CsvSource("C05,digest", "C05,body", "C05,missing", "C06,digest", "C06,body", "C06,missing")
    fun `new CASE and R headers cannot change after actual archive file insert`(kind: String, change: String) = fixture(kind) { owner, r ->
        val source = r.source as PGSimpleDataSource
        val schema = requireNotNull(source.currentSchema)
        val departureRequest = if (kind == "C05") call(owner, "request") as LeadershipDepartureRequest else null
        val handoverRequest = if (kind == "C06") call(owner, "prepared", r.delegate) as WarehouseStaffingHandoverRequest else null
        r.login("operator")
        val actor = r.db { r.context.getBean(CollectionActorDirectory::class.java).requireActive("operator") }
        assertFalse("ADMIN" in actor.roles); assertFalse(CollectionConst.CROSS_ORG in actor.permissions)
        val before = projection(source, schema)
        assertEquals(19, before.size)
        val localBefore = bytes(r.local)
        assertEquals(3, localBefore.size)
        val phase = if (kind == "C05") "CASE_%" else "R_%"
        val business = if (kind == "C05") LeadershipDepartureEvidence.BIZ else WarehouseStaffingHandoverService.BIZ
        val marker = "09$kind.new.$change.after_file"
        val lockKey = System.nanoTime() and Long.MAX_VALUE
        val mutation = when (change) {
            "digest" -> "UPDATE col_permanent_archive SET digest='DEV-SYNTHETIC-new-head-invalid-digest' WHERE id=NEW.archive_id"
            "body" -> "UPDATE col_permanent_archive SET snapshot='{}' WHERE id=NEW.archive_id"
            "missing" -> "DELETE FROM col_permanent_archive WHERE id=NEW.archive_id"
            else -> error("Unsupported new header change")
        }
        // New headers belong to this uncommitted write. Only this writer's AFTER-file trigger can change them.
        // A second connection merely holds the observation barrier; it never mutates this new header.
        source.connection.use { c -> c.createStatement().use { s ->
            s.execute("CREATE FUNCTION new_header_after_file_fn() RETURNS trigger AS \$\$ DECLARE p text; b text; BEGIN SELECT phase,biz_type INTO p,b FROM col_permanent_archive WHERE id=NEW.archive_id; IF b='$business' AND p LIKE '$phase' AND NEW.file_id='scope' THEN $mutation; IF NOT FOUND THEN RAISE EXCEPTION 'target new header mutation did not affect a row'; END IF; PERFORM set_config('application_name','$marker',false); PERFORM pg_advisory_xact_lock($lockKey); END IF; RETURN NEW; END; \$\$ LANGUAGE plpgsql")
            s.execute("CREATE TRIGGER new_header_after_file AFTER INSERT ON col_permanent_archive_file FOR EACH ROW EXECUTE FUNCTION new_header_after_file_fn()")
        } }
        val auth = SecurityContextHolder.getContext().authentication
        val writerPid = AtomicInteger()
        val pool = Executors.newSingleThreadExecutor()
        var observed: Map<String, Any>? = null
        var outcome: Result<String>? = null
        try { source.connection.use { holder ->
            holder.autoCommit = false
            val holderPid = holder.createStatement().use { s -> s.executeQuery("SELECT pg_backend_pid(),pg_advisory_lock($lockKey)").use { rs -> rs.next(); rs.getInt(1) } }
            try {
                val future = pool.submit<Result<String>> {
                    SecurityContextHolder.getContext().authentication = auth
                    try { runCatching { r.db {
                        TransactionManager.current().exec("SELECT pg_backend_pid()") { rs -> rs.next(); writerPid.set(rs.getInt(1)) }
                        if (kind == "C05") r.context.getBean(LeadershipDepartureService::class.java).register("museum", requireNotNull(departureRequest))
                        else r.context.getBean(WarehouseStaffingHandoverService::class.java).register("store", requireNotNull(handoverRequest))
                    } } } finally { SecurityContextHolder.clearContext() }
                }
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12)
                while (observed == null && System.nanoTime() < deadline) {
                    if (future.isDone) fail<Nothing>("Did not reach mutation after actual new archive-file INSERT: ${future.get().exceptionOrNull()}")
                    if (writerPid.get() != 0) source.connection.use { observer -> observer.prepareStatement("SELECT wait_event_type,wait_event,application_name,pg_blocking_pids(pid),pg_backend_pid() FROM pg_stat_activity WHERE pid=?").use { s ->
                        s.setInt(1, writerPid.get()); s.executeQuery().use { rs ->
                            if (rs.next() && rs.getString(1) == "Lock" && rs.getString(2) == "advisory") {
                                val blockers = (rs.getArray(4).array as Array<*>).map { (it as Number).toInt() }
                                assertEquals(marker, rs.getString(3)); assertTrue(holderPid in blockers)
                                assertNotEquals(holderPid, rs.getInt(5)); assertNotEquals(writerPid.get(), rs.getInt(5))
                                observed = mapOf("writerPid" to writerPid.get(), "holderPid" to holderPid, "observerPid" to rs.getInt(5),
                                    "marker" to marker, "waitEvent" to "Lock/advisory", "mutationConnection" to "writer", "newHeaderCommitted" to false)
                            }
                        }
                    } }
                    if (observed == null) Thread.sleep(20)
                }
                assertNotNull(observed)
                assertEquals(before, projection(source, schema), "The third connection sees only the committed baseline, never the new header")
                holder.createStatement().use { it.execute("SELECT pg_advisory_unlock($lockKey)") }
                outcome = future.get(25, TimeUnit.SECONDS)
            } finally { holder.rollback(); holder.createStatement().use { it.execute("SELECT pg_advisory_unlock($lockKey)") } }
        } } finally { pool.shutdownNow(); assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS)) }
        val result = requireNotNull(outcome)
        val after = projection(source, schema)
        println("09_NEW_HEADER_AFTER_FILE_RESULT " + JsonUtils.toJson(mapOf("kind" to kind, "change" to change,
            "schema" to schema, "newHeaderMutation" to "same-writer AFTER archive-file INSERT", "independentMutation" to false,
            "actualWait" to observed, "declaredTables" to before.size,
            "before" to before.mapValues { (_, v) -> mapOf("rows" to v.rows, "digest" to v.digest) },
            "after" to after.mapValues { (_, v) -> mapOf("rows" to v.rows, "digest" to v.digest) },
            "localInputHashes" to localBefore, "result" to if (result.isSuccess) "success" else result.exceptionOrNull()!!::class.java.simpleName)))
        assertInstanceOf(BadRequestException::class.java, result.exceptionOrNull(), "New header $change must reject the first actual write")
        assertEquals(before, after, "Every row of all 19 fixture tables must roll back, including new references and file bindings")
        assertEquals(localBefore, bytes(r.local), "Uploaded LOCAL source bodies are existing inputs, retained unchanged")
    }
}
