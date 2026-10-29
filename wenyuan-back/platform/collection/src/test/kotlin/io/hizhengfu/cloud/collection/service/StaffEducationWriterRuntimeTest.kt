package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.file.LocalFileObjectStorage
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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.postgresql.ds.PGSimpleDataSource
import org.mockito.Mockito.*
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
import java.time.temporal.ChronoUnit
import java.util.Properties
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

/** Actual archive INSERT triggers and independent transactions; no development business tables are used. */
class StaffEducationWriterRuntimeTest {
    @TempDir lateinit var dir: Path
    private val at = LocalDateTime.now().minusDays(2).truncatedTo(ChronoUnit.MILLIS)
    private val tables = arrayOf(CollectionOrgs, Warehouses, CollectionStaffEducations, CollectionStaffEducationVersions,
        CollectionStaffEducationFileAccesses, CollectionPermanentArchives, CollectionPermanentArchiveFiles, Files,
        Users, Groups, Roles, RoleUsers, RolePermissions, Permissions, UserGroups)
    companion object {
        private val stages = listOf("create", "historical-create", "revision", "withdrawal", "confirmation")
        @JvmStatic fun qualificationCases(): List<Arguments> =
            stages.flatMap { stage -> listOf("account", "membership", "museum", "role-disabled", "role-detached").map { Arguments.of(stage, it) } } +
                listOf("create", "revision").map { Arguments.of(it, "permission") } +
                listOf("subject-account", "subject-membership").map { Arguments.of("create", it) }
        @JvmStatic fun writerStages() = stages
        @JvmStatic fun lockCases(): List<Arguments> =
            listOf("create", "create-repeat", "revision", "withdrawal", "confirmation").flatMap { stage -> listOf(true, false).map { Arguments.of(stage, it) } }
        @JvmStatic fun historicalStages() = listOf("revision", "withdrawal", "confirmation")
    }
    private class Runtime(val tx: TransactionTemplate, val service: CollectionStaffEducationService,
        val scope: CollectionScopeResolver, val binder: BusinessAttachmentBinder, val postgres: Boolean, val source: DataSource) {
        lateinit var operation: () -> String
        lateinit var actor: String
        lateinit var id: String
        fun <T> db(block: () -> T): T = tx.execute { block() }
        fun login(id: String) {
            val user = db { CollectionActorDirectory(scope).requireActive(id) }
            SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(user, null, user.authorities)
        }
    }
    private fun fixture(block: (Runtime) -> Unit) {
        val schema = "education_writer_" + UUID.randomUUID().toString().replace("-", "")
        val pg = System.getProperty("collection.test.pg.config")?.let { name ->
            val p = Properties().apply { Path.of(name).toFile().inputStream().use(::load) }
            PGSimpleDataSource().apply {
                setURL(p.getProperty("url")); user = p.getProperty("user"); password = p.getProperty("password")
                connection.use { it.createStatement().use { sql -> sql.execute("CREATE SCHEMA $schema") } }
                currentSchema = schema; applicationName = schema
            }
        }
        val source: DataSource = pg ?: SQLiteDataSource().apply { url = "jdbc:sqlite:${dir.resolve("education.db")}" }
        val manager = SpringTransactionManager(source, DatabaseConfig {})
        val scope = CollectionScopeResolver("single", "museum")
        val access = StaffEducationAccess(scope, CollectionActorDirectory(scope))
        val binder = mock(BusinessAttachmentBinder::class.java)
        `when`(binder.resolveSingle(anyString())).thenAnswer { it.getArgument<String>(0) }
        val root = createSafetyOriginalRoot()
        val archives = CollectionPermanentArchiveService(binder, scope, LocalFileObjectStorage(root.toString()), unprotectedArchiveReadAccessProvider())
        val proxy = ProxyFactory(CollectionStaffEducationService(access, scope, CollectionActorDirectory(scope), archives)).apply {
            isProxyTargetClass = true
            addAdvice(TransactionInterceptor().apply { transactionManager = manager; transactionAttributeSource = AnnotationTransactionAttributeSource() })
        }
        try { StaticApplicationContext().use { context ->
            context.beanFactory.registerSingleton("cacheManager", ConcurrentMapCacheManager()); context.refresh()
            SpringContextHolder().setApplicationContext(context)
            val r = Runtime(TransactionTemplate(manager), proxy.proxy as CollectionStaffEducationService, scope, binder, pg != null, source)
            r.db {
                SchemaUtils.create(*tables)
                actorSchema()
                for (id in listOf("museum", "other")) {
                    GroupDao.new(id) { name = id; type = '1'; parentId = "0"; parentIds = "0," }
                    CollectionOrgDao.new(id) { name = id; code = id; systemGroupId = id }
                }
                for (id in listOf("manager", "author", "head", "person", "former", "other-manager"))
                    UserDao.new(id) { name = id; loginName = id; password = "unused"; enabled = id != "former"; groupId = "museum" }
                for (id in listOf("manager", "author", "other-manager")) actorRole(id, "EDUCATION_$id", setOf(CollectionConst.STAFF_EDUCATION_MANAGE))
                actorRole("head", CollectionConst.ROLE_STORAGE_HEAD)
                FileDao.new("proof") { name = "合成教育原件.pdf"; path = "proof"; hash = "proof"; size = 100; ext = "pdf" }
                TransactionManager.current().exec("CREATE TABLE education_writer_fault (hits INTEGER NOT NULL)")
                TransactionManager.current().exec("INSERT INTO education_writer_fault VALUES (0)")
            }
            r.login("manager")
            try { block(r) } finally { SecurityContextHolder.clearContext() }
        } } finally {
            pg?.connection?.use { it.createStatement().use { sql -> sql.execute("DROP SCHEMA $schema CASCADE") } }
            removeSafetyOriginalRoot(root)
        }
    }
    private fun plan() = StaffEducationPlan(EnumStaffEducationKind.POST_TRAINING, "合成岗位培训", "合成保管职责", "合成技能需求", at, "proof")
    private fun create(historical: Boolean = false) = StaffEducationCreate("request", if (historical) "former" else "person", historicalPerson = historical,
        employmentFileIds = "proof", plan = plan())
    private fun revision() = StaffEducationRevision(1, StaffEducationContent(plan(), StaffEducationParticipation("合成教育机构", "合成课程",
        at.plusHours(1), at.plusHours(2), 60, "合成考核", EnumStaffEducationOutcome.COMPLETED, "proof")), "合成补充依据", "proof")
    private val confirmation = StaffEducationConfirmationVo(true, "合成本人核实，不代表真实培训", "proof")
    private fun prepare(r: Runtime, stage: String) {
        if (stage in setOf("create", "historical-create", "create-repeat")) {
            r.actor = if (stage == "historical-create") "head" else "manager"; r.login(r.actor)
            val value = create(stage == "historical-create")
            r.operation = { r.service.create("museum", value) }
            if (stage == "create-repeat") r.id = r.operation()
            return
        }
        r.login("author"); r.id = r.service.create("museum", create())
        if (stage == "confirmation") r.service.revise(r.id, revision())
        r.actor = if (stage == "revision") "manager" else "head"; r.login(r.actor)
        r.operation = when (stage) {
            "revision" -> { { r.service.revise(r.id, revision()) } }
            "withdrawal" -> { { r.service.revise(r.id, StaffEducationRevision(1, StaffEducationContent(plan(), withdrawal = StaffEducationWithdrawal("合成需求变化", "proof")), "合成据实终止", "proof")) } }
            else -> { { r.service.confirm(r.id, 2, confirmation) } }
        }
    }
    private fun mutation(r: Runtime, change: String): String {
        val role = if (r.actor == "head") CollectionConst.ROLE_STORAGE_HEAD else "EDUCATION_${r.actor}"
        return when (change) {
            "permission" -> "DELETE FROM sys_role_permission WHERE role_id='role:$role'"
            "account" -> "UPDATE sys_user SET enabled=${if (r.postgres) "false" else "0"} WHERE id='${r.actor}'"
            "membership" -> "UPDATE sys_user SET group_id='other' WHERE id='${r.actor}'"
            "museum" -> "UPDATE col_org SET org_status=${EnumOrgStatus.DISABLED.ordinal} WHERE id='museum'"
            "role-disabled" -> "UPDATE sys_role SET enabled=${if (r.postgres) "false" else "0"} WHERE id='role:$role'"
            "role-detached" -> "DELETE FROM sys_role_user WHERE user_id='${r.actor}'"
            "subject-account" -> "UPDATE sys_user SET enabled=${if (r.postgres) "false" else "0"} WHERE id='person'"
            "subject-membership" -> "UPDATE sys_user SET group_id='other' WHERE id='person'"
            else -> error(change)
        }
    }
    private fun trigger(r: Runtime, stage: String, change: String) = r.db {
        val phase = when (stage) { "create", "historical-create" -> "ORIGINAL"; "confirmation" -> "REVIEW_2"; else -> "V_2" }
        val predicate = "NEW.biz_type='${CollectionStaffEducationService.BIZ}' AND NEW.phase='$phase'"
        val sql = mutation(r, change)
        if (r.postgres) {
            TransactionManager.current().exec("CREATE FUNCTION mutate_education_writer() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF $predicate THEN UPDATE education_writer_fault SET hits=hits+1; $sql; END IF; RETURN NEW; END $$")
            TransactionManager.current().exec("CREATE TRIGGER mutate_education_writer AFTER INSERT ON col_permanent_archive FOR EACH ROW EXECUTE FUNCTION mutate_education_writer()")
        } else TransactionManager.current().exec("CREATE TRIGGER mutate_education_writer AFTER INSERT ON col_permanent_archive WHEN $predicate BEGIN UPDATE education_writer_fault SET hits=hits+1; $sql; END")
    }
    private fun snapshot(r: Runtime) = r.db { settledSafetySelectionState(*tables) }
    private fun qualification(stage: String, change: String) = fixture { r ->
        prepare(r, stage); trigger(r, stage, change)
        val before = snapshot(r)
        val failure = runCatching { r.operation() }.exceptionOrNull()
        if (failure == null) {
            assertEquals(1, r.db { TransactionManager.current().exec("SELECT hits FROM education_writer_fault") { rows -> rows.next(); rows.getInt(1) } })
            fail<Unit>("Actual education archive SQL $change still committed $stage")
        }
        assertInstanceOf(BadRequestException::class.java, failure)
        assertEquals(before, snapshot(r), "Education facts, versions, file protections and current directory must roll back together")
        assertEquals(0, r.db { TransactionManager.current().exec("SELECT hits FROM education_writer_fault") { rows -> rows.next(); rows.getInt(1) } })
        r.db {
            TransactionManager.current().exec("DROP TRIGGER mutate_education_writer" + if (r.postgres) " ON col_permanent_archive" else "")
            if (r.postgres) TransactionManager.current().exec("DROP FUNCTION mutate_education_writer()")
        }
        val id = r.operation(); val settled = snapshot(r)
        assertEquals(id, r.operation()); assertEquals(settled, snapshot(r))
    }
    @ParameterizedTest @MethodSource("qualificationCases")
    fun `education actual archive SQL checks current writer and new subject`(stage: String, change: String) = qualification(stage, change)
    @Tag("postgres-concurrency") @ParameterizedTest @MethodSource("qualificationCases")
    fun `postgres education actual archive SQL checks current writer and new subject`(stage: String, change: String) {
        assertNotNull(System.getProperty("collection.test.pg.config")); qualification(stage, change)
    }
    @ParameterizedTest @MethodSource("writerStages")
    fun `binding cannot replace the authenticated education writer`(stage: String) = fixture { r ->
        prepare(r, stage); val other = r.db { CollectionActorDirectory(r.scope).requireActive("other-manager") }
        doAnswer {
            TransactionManager.current().entityCache.flush()
            SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(other, null, other.authorities)
            null
        }.`when`(r.binder).bindSingle(anyString(), anyString(), anyString())
        val before = snapshot(r)
        assertThrows(BadRequestException::class.java) { r.operation() }
        assertEquals(before, snapshot(r))
    }
    @ParameterizedTest @MethodSource("historicalStages")
    fun `former record author and subject do not block current education continuation`(stage: String) = fixture { r ->
        prepare(r, stage)
        r.db { TransactionManager.current().exec("UPDATE sys_user SET enabled=${if (r.postgres) "false" else "0"} WHERE id IN ('author','person')") }
        val id = r.operation(); assertEquals(id, r.operation())
        assertEquals("author", (r.service.detail(id)["original"] as StaffEducationOriginal).registeredBy)
    }
    @Test fun `exact registration replay retains its original subject after later departure`() = fixture { r ->
        prepare(r, "create"); val id = r.operation()
        r.db { TransactionManager.current().exec("UPDATE sys_user SET enabled=${if (r.postgres) "false" else "0"} WHERE id='person'") }
        val before = snapshot(r)
        assertEquals(id, r.operation()); assertEquals(before, snapshot(r))
        assertEquals("person", (r.service.detail(id)["original"] as StaffEducationOriginal).request.userId)
    }
    private fun lockWait(stage: String, commit: Boolean) = fixture { r ->
        assertTrue(r.postgres); prepare(r, stage); val before = snapshot(r)
        val revoked = r.source.connection.apply { autoCommit = false }
        val pool = Executors.newSingleThreadExecutor()
        try {
            revoked.createStatement().use { it.executeQuery("SELECT id FROM col_org WHERE id='museum' FOR UPDATE").close() }
            revoked.createStatement().use { it.executeUpdate("UPDATE sys_user SET enabled=false WHERE id='${r.actor}'") }
            val actor = r.db { CollectionActorDirectory(r.scope).requireActive(r.actor) }
            val job = pool.submit<Result<String>> {
                SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(actor, null, actor.authorities)
                try { runCatching { r.operation() } } finally { SecurityContextHolder.clearContext() }
            }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            var waiting = false
            while (System.nanoTime() < deadline) {
                waiting = r.source.connection.use { observer -> observer.createStatement().use {
                    it.executeQuery("SELECT count(*) FROM pg_stat_activity WHERE application_name='${(r.source as PGSimpleDataSource).applicationName}' AND wait_event_type='Lock' AND query LIKE '%col_org%'").use { rows -> rows.next(); rows.getInt(1) > 0 }
                } }
                if (waiting) break
                Thread.sleep(10)
            }
            assertTrue(waiting, "Actual education request must reach the museum row-lock wait")
            if (commit) revoked.commit() else revoked.rollback()
            val result = job.get(20, TimeUnit.SECONDS)
            if (commit) {
                assertInstanceOf(BadRequestException::class.java, result.exceptionOrNull())
                r.db { TransactionManager.current().exec("UPDATE sys_user SET enabled=true WHERE id='${r.actor}'") }
                assertEquals(before, snapshot(r))
            } else assertTrue(result.isSuccess, result.toString())
            r.login(r.actor); val id = r.operation(); val settled = snapshot(r)
            assertEquals(id, r.operation()); assertEquals(settled, snapshot(r))
        } finally { revoked.rollback(); revoked.close(); pool.shutdownNow() }
    }
    @Tag("postgres-concurrency") @ParameterizedTest @MethodSource("lockCases")
    fun `education writer rechecks committed and rolled back qualification after the museum lock`(stage: String, commit: Boolean) = lockWait(stage, commit)
}
