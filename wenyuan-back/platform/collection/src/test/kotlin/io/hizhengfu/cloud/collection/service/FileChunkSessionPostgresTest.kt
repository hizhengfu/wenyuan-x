package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.file.*
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.sys.file.dao.*
import io.hizhengfu.cloud.sys.file.service.*
import io.hizhengfu.cloud.sys.system.dao.Users
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import org.postgresql.ds.PGSimpleDataSource
import org.springframework.context.annotation.*
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.support.TransactionTemplate
import java.nio.file.Path
import java.util.Properties
import java.util.UUID
import java.util.concurrent.*

@Tag("postgres-concurrency")
class FileChunkSessionPostgresTest {
    @TempDir lateinit var root: Path
    @Configuration(proxyBeanMethods = false) @EnableTransactionManagement(proxyTargetClass = true) class Transactions
    private object OrdinaryContents : io.hizhengfu.cloud.autoconfigure.exposed.UIDTable("ordinary_content")
    private class OrdinaryInstall : io.hizhengfu.cloud.common.install.AbstractInstall() {
        override fun initTables() = setOf(OrdinaryContents)
    }
    private fun login() {
        val user = SecurityUser(id = "owner", name = "owner", username = "owner", password = "", groupId = "group", groupIds = setOf("group"), postIds = emptySet(), dataType = 0, roles = emptySet(), authorities = emptyList(), enabled = true)
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(user, null, emptyList())
    }
    private fun fixture(museumTargets: Boolean = false, body: (AnnotationConfigApplicationContext, TransactionTemplate, ExecutorService, PGSimpleDataSource, String) -> Unit) {
        val config = Properties().apply { Path.of(System.getProperty("collection.test.pg.config")).toFile().inputStream().use(::load) }
        val schema = "chunk_" + UUID.randomUUID().toString().replace("-", "")
        val source = PGSimpleDataSource().apply { setURL(config.getProperty("url")); user = config.getProperty("user"); password = config.getProperty("password"); currentSchema = schema; applicationName = schema }
        source.connection.use { it.createStatement().use { s -> s.execute("CREATE SCHEMA $schema") } }
        val manager = SpringTransactionManager(source, DatabaseConfig {})
        val executor = Executors.newFixedThreadPool(2)
        try { AnnotationConfigApplicationContext().use { c ->
            c.environment.propertySources.addFirst(org.springframework.core.env.MapPropertySource("test", mapOf("file.basedir" to root.toString())))
            c.beanFactory.registerSingleton("transactionManager", manager)
            c.register(Transactions::class.java, LocalFileObjectStorage::class.java, FileReadAccess::class.java, FileService::class.java,
                FileFolderService::class.java, FileSystemStorageService::class.java, FileChunkSessionService::class.java)
            if (museumTargets) {
                c.beanFactory.registerSingleton("museumUploadTargets", io.hizhengfu.cloud.collection.install.CollectionInstall("single"))
                c.beanFactory.registerSingleton("ordinaryInstall", OrdinaryInstall())
                c.register(CollectionPublicUploadTargetGuard::class.java)
            }
            c.refresh(); SpringContextHolder().setApplicationContext(c)
            val tx = TransactionTemplate(manager)
            tx.executeWithoutResult {
                SchemaUtils.create(Users, Files, FileUsers, FileContents, FileFolders, FileChunkSessions)
                Users.insert { it[Users.id] = "owner"; it[loginName] = "owner"; it[name] = "owner"; it[password] = "test-only"; it[groupId] = "group"; it[enabled] = true }
            }
            login()
            if (museumTargets) tx.executeWithoutResult {
                SchemaUtils.create(*io.hizhengfu.cloud.collection.install.CollectionInstall("single").initTables().toTypedArray())
                SchemaUtils.create(OrdinaryContents)
                OrdinaryContents.insert { it[id]="ordinary-content" }
                val t = io.hizhengfu.cloud.collection.dao.Collections
                t.insert { it[id]="museum-case"; it[orgId]="museum"; it[name]="馆藏记录"; it[totalRegNo]="A1" }
            }
            body(c, tx, executor, source, schema)
        } } finally {
            executor.shutdownNow(); executor.awaitTermination(10, TimeUnit.SECONDS); SecurityContextHolder.clearContext()
            source.connection.use { it.createStatement().use { s -> s.execute("DROP SCHEMA $schema CASCADE") } }
        }
    }
    private fun <T> worker(executor: ExecutorService, body: () -> T): Future<T> = executor.submit<T> { login(); try { body() } finally { SecurityContextHolder.clearContext() } }
    private fun second(c: AnnotationConfigApplicationContext) = FileChunkSessionService(
        FileSystemStorageService(root.toString(), c.getBean(FileService::class.java), c.getBean(FileFolderService::class.java), c, c.getBean(LocalFileObjectStorage::class.java), c.getBean(FileReadAccess::class.java)),
        c.getBean(SpringTransactionManager::class.java), c.getBean(FileReadAccess::class.java))
    private fun chunk(text: String = "evidence") = MockMultipartFile("chunk", "proof.txt", "text/plain", text.toByteArray())

    @Test fun `public chunk binding uses the current PostgreSQL museum target registry`() = fixture(museumTargets = true) { c, tx, _, _, _ ->
        val sessions=c.getBean(FileChunkSessionService::class.java)
        val id=sessions.start(); sessions.upload(id,0,chunk())
        assertThrows(BadRequestException::class.java) { sessions.finish(id,"proof.txt",8,"museum-case",null,emptyMap()) }
        assertThrows(BadRequestException::class.java) { sessions.finish(id,"proof.txt",8,"999999999999999999",null,emptyMap()) }
        tx.executeWithoutResult {
            assertEquals("OPEN",FileChunkSessions.selectAll().single()[FileChunkSessions.state])
            assertEquals(0L,Files.selectAll().count()); assertEquals(0L,FileContents.selectAll().count())
        }
        val result=sessions.finish(id,"proof.txt",8,"ordinary-content",null,emptyMap())
        org.junit.jupiter.api.Assertions.assertNotNull(result.id)
        tx.executeWithoutResult { assertEquals("ordinary-content",FileContents.selectAll().single()[FileContents.contentId]) }
    }
    private fun waitForLock(source: PGSimpleDataSource, schema: String) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < deadline) {
            val waiting = source.connection.use { c -> c.prepareStatement("SELECT count(*) FROM pg_stat_activity WHERE application_name=? AND wait_event_type='Lock'").use { s -> s.setString(1, schema); s.executeQuery().use { r -> r.next(); r.getInt(1) > 0 } } }
            if (waiting) return
            Thread.sleep(20)
        }
        fail<Unit>("No PostgreSQL row lock wait observed")
    }

    @Test fun `separate service instances serialize conflicting writes on persistent task row`() = fixture { c, _, executor, source, schema ->
        val first = c.getBean(FileChunkSessionService::class.java); val other = second(c); val id = first.start()
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val slow = object : MockMultipartFile("chunk", "proof.txt", "text/plain", "first".toByteArray()) {
            override fun getInputStream(): java.io.InputStream { entered.countDown(); check(release.await(10, TimeUnit.SECONDS)); return super.getInputStream() }
        }
        val one = worker(executor) { first.upload(id, 0, slow) }
        try {
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            val two = worker(executor) { assertThrows(BadRequestException::class.java) { other.upload(id, 0, chunk("other")) } }
            waitForLock(source, schema); release.countDown(); one.get(10, TimeUnit.SECONDS); two.get(10, TimeUnit.SECONDS)
            assertEquals("first", java.nio.file.Files.readString(root.resolve("temp/${id}_0")))
        } finally { release.countDown() }
    }

    @Test fun `durable finishing state prevents a second merge and completion replays once`() = fixture { c, _, executor, _, _ ->
        val first = c.getBean(FileChunkSessionService::class.java); val other = second(c); val id = first.start(); first.upload(id, 0, chunk())
        val entered = CountDownLatch(1); val release = CountDownLatch(1); val events = java.util.concurrent.atomic.AtomicInteger()
        c.addApplicationListener { if (it is io.hizhengfu.cloud.event.FileUploadEvent) { events.incrementAndGet(); entered.countDown(); check(release.await(10, TimeUnit.SECONDS)) } }
        val one = worker(executor) { first.finish(id, "proof.txt", 8, null, null, emptyMap()) }
        try {
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            assertThrows(BadRequestException::class.java) { other.finish(id, "proof.txt", 8, null, null, emptyMap()) }
            release.countDown(); val result = one.get(10, TimeUnit.SECONDS)
            assertEquals(result, other.finish(id, "proof.txt", 8, null, null, emptyMap())); assertEquals(1, events.get())
        } finally { release.countDown() }
    }

    @Test fun `account revocation committed during lock wait rejects finish before file persistence`() = fixture { c, tx, executor, source, schema ->
        val sessions = c.getBean(FileChunkSessionService::class.java); val id = sessions.start(); sessions.upload(id, 0, chunk())
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val one = worker(executor) { tx.executeWithoutResult {
            FileChunkSessions.selectAll().where { FileChunkSessions.id eq id }.forUpdate().single()
            Users.update({ Users.id eq "owner" }) { it[enabled] = false }; entered.countDown(); check(release.await(10, TimeUnit.SECONDS))
        } }
        try {
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            val two = worker(executor) { assertThrows(BadRequestException::class.java) { second(c).finish(id, "proof.txt", 8, null, null, emptyMap()) } }
            waitForLock(source, schema); release.countDown(); one.get(10, TimeUnit.SECONDS); two.get(10, TimeUnit.SECONDS)
            tx.executeWithoutResult { assertEquals(0L, Files.selectAll().count()); assertEquals("OPEN", FileChunkSessions.selectAll().single()[FileChunkSessions.state]) }
        } finally { release.countDown() }
    }
}
