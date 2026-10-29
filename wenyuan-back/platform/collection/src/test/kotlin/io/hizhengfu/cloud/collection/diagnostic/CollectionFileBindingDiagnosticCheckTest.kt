package io.hizhengfu.cloud.collection.diagnostic

import io.hizhengfu.cloud.autoconfigure.exposed.UIDTable
import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.common.install.AbstractInstall
import io.hizhengfu.cloud.sys.diagnostic.domain.*
import io.hizhengfu.cloud.sys.file.dao.FileContents
import io.hizhengfu.cloud.sys.file.dao.Files
import io.hizhengfu.cloud.sys.file.service.FileUploadTargetSchema
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.sqlite.SQLiteDataSource
import java.nio.file.Path

class CollectionFileBindingDiagnosticCheckTest {
    @TempDir lateinit var dir: Path
    private object Ordinary : UIDTable("binding_ordinary")
    private object Restricted : UIDTable("binding_restricted")
    private object Old : UIDTable("binding_undeclared")
    private fun fixture(body: () -> Unit) {
        val source = SQLiteDataSource().apply { url = "jdbc:sqlite:${dir.resolve("bindings.db")}" }
        transaction(Database.connect(source)) { SchemaUtils.create(Files, FileContents, Ordinary, Restricted, Old); body() }
    }
    private fun file(id: String) { Files.insert {
        it[Files.id] = id; it[name] = "不应输出的原件名称"; it[path] = "/private/original-$id";
        it[ext] = "txt"; it[hash] = "hash-$id"; it[size] = 1
    } }
    private fun bind(file: String, target: String) { FileContents.insert {
        it[fileId] = file; it[contentId] = target; it[aliasName] = "不应输出的保密正文"
    } }
    private fun inspect(batch: Int = 200) = CollectionFileBindingInspection.inspect(listOf(Ordinary, Restricted), batch)
    private fun DeploymentCheckOutcome.fact(name: String) = safeFacts.single { it.name == name }.value
    private fun DeploymentCheckOutcome.samples() = safeFacts.filter { it.name.startsWith("issueSample") }
        .map { JsonUtils.mapper.readTree(it.value) }

    @Test fun `ordinary and restricted confirmed records are not classified as illegal bindings`() = fixture {
        Ordinary.insert { it[id] = "ordinary" }; Restricted.insert { it[id] = "restricted" }
        file("one"); file("two"); bind("one", "ordinary"); bind("two", "restricted")
        val result = inspect()
        assertEquals(EnumDeploymentCheckState.PASS, result.state)
        assertEquals("2", result.fact("scannedBindings"))
        assertTrue(result.fact("inspectionScope").contains("not business confirmation"))
        assertFalse(JsonUtils.toJson(result)!!.contains("不应输出"))
        assertFalse(JsonUtils.toJson(result)!!.contains("/private/"))
    }

    @ParameterizedTest @ValueSource(strings = ["unknown", "missing-file", "both", "ambiguous", "undeclared"])
    fun `historical inconsistencies are reported without deleting or rewriting evidence`(kind: String) = fixture {
        if (kind !in setOf("missing-file", "both")) file("original")
        if (kind !in setOf("unknown", "both", "undeclared")) Ordinary.insert { it[id] = "target" }
        if (kind == "ambiguous") Restricted.insert { it[id] = "target" }
        if (kind == "undeclared") Old.insert { it[id] = "target" }
        bind("original", "target")
        val result = inspect()
        assertEquals(EnumDeploymentCheckState.FAIL, result.state)
        assertEquals(if (kind in setOf("missing-file", "both")) "1" else "0", result.fact("missingFileBindings"))
        assertEquals(if (kind in setOf("unknown", "both", "undeclared")) "1" else "0", result.fact("unresolvedTargetBindings"))
        assertEquals(if (kind == "ambiguous") "1" else "0", result.fact("ambiguousTargetBindings"))
        assertEquals(1, result.samples().size)
        assertEquals("不应输出的保密正文", FileContents.selectAll().single()[FileContents.aliasName])
        assertEquals(1L, FileContents.selectAll().count())
    }

    @Test fun `cursor covers same-target files and later targets across pages with bounded samples`() = fixture {
        repeat(213) { index -> file("f%04d".format(index)); bind("f%04d".format(index), if (index < 205) "a" else "b") }
        val result = inspect()
        assertEquals(EnumDeploymentCheckState.FAIL, result.state)
        assertEquals("213", result.fact("scannedBindings"))
        assertEquals("213", result.fact("unresolvedTargetBindings"))
        assertEquals(10, result.samples().size)
    }

    @Test fun `current installer and restricted declarations are both used and duplicate declarations count once`() = fixture {
        Restricted.insert { it[id] = "restricted" }; file("original"); bind("original", "restricted")
        val beans = DefaultListableBeanFactory()
        beans.registerSingleton("install", object : AbstractInstall() { override fun initTables(): Set<Table> = setOf(Ordinary) })
        beans.registerSingleton("restricted", object : FileUploadTargetSchema { override fun publicUploadBlockedTables(): Set<Table> = setOf(Restricted, Ordinary) })
        val check = CollectionFileBindingDiagnosticCheck(beans.getBeanProvider(AbstractInstall::class.java), beans.getBeanProvider(FileUploadTargetSchema::class.java))
        assertEquals(EnumDeploymentCheckState.PASS, check.check().state)
        assertEquals("2", check.check().fact("declaredStringIdTables"))
    }

    @Test fun `same identifier in different query groups remains ambiguous`() = fixture {
        val tables = List(51) { UIDTable("binding_partition_$it") }
        SchemaUtils.create(*tables.toTypedArray())
        for (table in listOf(tables.first(), tables.last())) table.insert { it[table.id] = "shared" }
        file("original"); bind("original", "shared")
        val result = CollectionFileBindingInspection.inspect(tables)
        assertEquals(EnumDeploymentCheckState.FAIL, result.state)
        assertEquals("1", result.fact("ambiguousTargetBindings"))
        assertEquals(2, result.samples().first().path("matchingTargets").asInt())
    }

    @Test fun `long historical identifiers remain complete in each bounded diagnostic fact`() = fixture {
        repeat(12) { i -> bind("f".repeat(62) + "%02d".format(i), "t".repeat(64)) }
        val result = inspect()
        assertEquals(10, result.samples().size)
        assertTrue(result.safeFacts.filter { it.name.startsWith("issueSample") }.all { it.value.length < 2_000 })
        result.samples().forEach {
            assertEquals(64, it.path("fileId").asString().length)
            assertEquals(64, it.path("contentId").asString().length)
        }
    }

    @Test fun `scan uses current records and never caches a previous unresolved result`() = fixture {
        bind("original", "target")
        assertEquals(EnumDeploymentCheckState.FAIL, inspect().state)
        file("original"); Ordinary.insert { it[id] = "target" }
        assertEquals(EnumDeploymentCheckState.PASS, inspect().state)
        Restricted.insert { it[id] = "target" }
        assertEquals(EnumDeploymentCheckState.FAIL, inspect().state)
    }

    @Test fun `no declared target schema cannot produce a readiness pass`() = fixture {
        assertEquals(EnumDeploymentCheckState.ERROR, CollectionFileBindingInspection.inspect(emptyList()).state)
    }

    @Test fun `empty relation table passes only the explicitly limited existence check`() = fixture {
        assertEquals(EnumDeploymentCheckState.PASS, inspect().state)
        assertEquals("0", inspect().fact("scannedBindings"))
    }

    @Test fun `historical duplicate keys cannot be skipped by the cursor and reported as complete`() = fixture {
        SchemaUtils.drop(FileContents)
        org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().exec(
            "CREATE TABLE sys_file_content (content_id varchar(64), file_id varchar(64), alias_name varchar(255), sort integer DEFAULT 0)"
        )
        Ordinary.insert { it[id] = "target" }; file("original")
        bind("original", "target"); bind("original", "target")
        val result = inspect(batch = 1)
        assertEquals(EnumDeploymentCheckState.ERROR, result.state)
        assertEquals("COLLECTION_FILE_BINDING_SCAN_INCOMPLETE", result.reasonCode)
        assertEquals(2L, FileContents.selectAll().count())
    }

    @Test fun `cancellation stops scanning and keeps the interrupt for the diagnostic executor`() = fixture {
        try {
            Thread.currentThread().interrupt()
            assertThrows(InterruptedException::class.java) { inspect() }
            assertTrue(Thread.currentThread().isInterrupted)
        } finally { Thread.interrupted() }
    }
}
