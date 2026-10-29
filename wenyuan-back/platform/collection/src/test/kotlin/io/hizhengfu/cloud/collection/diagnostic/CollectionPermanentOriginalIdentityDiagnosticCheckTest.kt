package io.hizhengfu.cloud.collection.diagnostic

import io.hizhengfu.cloud.collection.dao.CollectionPermanentArchiveDao
import io.hizhengfu.cloud.collection.dao.CollectionPermanentArchiveFileDao
import io.hizhengfu.cloud.collection.dao.CollectionPermanentArchiveFiles
import io.hizhengfu.cloud.collection.dao.CollectionPermanentArchives
import io.hizhengfu.cloud.collection.service.CollectionArchiveFileIdentity
import io.hizhengfu.cloud.collection.service.CollectionCareEvidence
import io.hizhengfu.cloud.collection.service.CollectionOriginalContent
import io.hizhengfu.cloud.common.file.FileReadPath
import io.hizhengfu.cloud.common.file.LocalFileObjectStorage
import io.hizhengfu.cloud.sys.diagnostic.domain.EnumDeploymentCheckState
import io.hizhengfu.cloud.sys.file.dao.FileDao
import io.hizhengfu.cloud.sys.file.dao.Files as FileTable
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.sqlite.SQLiteDataSource
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class CollectionPermanentOriginalIdentityDiagnosticCheckTest {
    @TempDir lateinit var dir: Path

    private fun fixture(body: (LocalFileObjectStorage, Path) -> Unit) {
        val source = SQLiteDataSource().apply { url = "jdbc:sqlite:${dir.resolve("originals.db")}" }
        val root = dir.resolve("userfiles")
        Files.createDirectories(root)
        val path = root.resolve("signed.txt")
        Files.writeString(path, "synthetic signed original")
        val storage = LocalFileObjectStorage(root.toString())
        transaction(Database.connect(source)) {
            SchemaUtils.create(FileTable, CollectionPermanentArchives, CollectionPermanentArchiveFiles)
            val file = FileDao.new("proof") {
                name = "保密签署原件"; this.path = "/userfiles/signed.txt"
                ext = "txt"; hash = "synthetic-hash"; size = Files.size(path)
            }
            val archive = CollectionPermanentArchiveDao.new("archive") {
                orgId = "museum"; bizType = "COLLECTION_ACCESSION_RECEIPT"; bizId = "receipt"
                phase = "RECORDED"; snapshot = "{}"; digest = CollectionCareEvidence.digest(snapshot)
            }
            CollectionPermanentArchiveFileDao.new("reference") {
                orgId = "museum"; archiveId = archive.id.value; bizId = "receipt"; fileId = file.id.value
                metadata = CollectionArchiveFileIdentity.metadata(file, "museum", archive.bizType, archive.bizId,
                    FileReadPath.keys(file.path, file.storageType.name, storage))
            }
            body(storage, path)
        }
    }

    @Test fun `replacement with identical bytes is found without rewriting the archived identity`() = fixture { storage, path ->
        val before = CollectionPermanentArchiveFileDao["reference"].metadata
        val second = CollectionPermanentArchiveDao.new("archive-two") {
            orgId = "museum"; bizType = "COLLECTION_ACCESSION_RECEIPT"; bizId = "receipt-two"
            phase = "RECORDED"; snapshot = "{}"; digest = CollectionCareEvidence.digest(snapshot)
        }
        val file = FileDao["proof"]
        CollectionPermanentArchiveFileDao.new("reference-two") {
            orgId = "museum"; archiveId = second.id.value; bizId = second.bizId; fileId = file.id.value
            metadata = CollectionArchiveFileIdentity.metadata(file, "museum", second.bizType, second.bizId,
                FileReadPath.keys(file.path, file.storageType.name, storage))
        }
        assertEquals(EnumDeploymentCheckState.PASS, CollectionPermanentOriginalIdentityInspection.inspect(storage).state)
        val replacement = path.resolveSibling("replacement.txt")
        Files.copy(path, replacement)
        Files.move(replacement, path, StandardCopyOption.REPLACE_EXISTING)
        val result = CollectionPermanentOriginalIdentityInspection.inspect(storage)
        assertEquals(EnumDeploymentCheckState.FAIL, result.state)
        assertEquals("2", result.safeFacts.single { it.name == "nativeIdentityMismatch" }.value)
        assertEquals("1", result.safeFacts.single { it.name == "distinctMismatchedFiles" }.value)
        assertEquals(1, result.safeFacts.count { it.name.startsWith("issueSample") })
        assertEquals(before, CollectionPermanentArchiveFileDao["reference"].metadata)
        assertEquals("synthetic signed original", Files.readString(path))
        assertFalse(result.toString().contains("保密签署原件"))
    }

    @Test fun `stable archived identity survives a device number change`() = fixture { storage, _ ->
        val file = FileDao["proof"]
        val archive = CollectionPermanentArchiveDao["archive"]
        val keys = FileReadPath.keys(file.path, file.storageType.name, storage)
        assertTrue(keys.any { it.startsWith("u") })
        val changed = keys.filterNot { it.startsWith("n") }.toSet() + ("n" + "0".repeat(63))
        CollectionPermanentArchiveFileDao["reference"].metadata = CollectionArchiveFileIdentity.metadata(
            file, archive.orgId, archive.bizType, archive.bizId, changed)
        val outcome = CollectionPermanentOriginalIdentityInspection.inspect(storage)
        assertEquals(EnumDeploymentCheckState.PASS, outcome.state)
        assertEquals("0", outcome.safeFacts.single { it.name == "nativeIdentityMismatch" }.value)
    }

    @Test fun `missing local storage or malformed metadata cannot pass as verified`() = fixture { storage, _ ->
        val noStorage = CollectionPermanentOriginalIdentityInspection.inspect(null)
        assertEquals(EnumDeploymentCheckState.FAIL, noStorage.state)
        assertEquals("1", noStorage.safeFacts.single { it.name == "localStorageUnavailable" }.value)
        CollectionPermanentArchiveFileDao["reference"].metadata = "{}"
        val malformed = CollectionPermanentOriginalIdentityInspection.inspect(storage)
        assertEquals(EnumDeploymentCheckState.FAIL, malformed.state)
        assertEquals("1", malformed.safeFacts.single { it.name == "invalidMetadata" }.value)
    }

    @Tag("postgres-concurrency") @Test
    fun `one hundred thousand valid permanent references scan in isolated PostgreSQL`() {
        val config = System.getProperty("collection.test.pg.config") ?: error("Explicit isolated PostgreSQL configuration required")
        val properties = java.util.Properties().apply { Files.newInputStream(Path.of(config)).use(::load) }
        fun source() = org.postgresql.ds.PGSimpleDataSource().apply {
            setURL(properties.getProperty("url")); user = properties.getProperty("user"); password = properties.getProperty("password")
        }
        val schema = "permanent_identity_" + java.util.UUID.randomUUID().toString().replace("-", "")
        source().connection.use { it.createStatement().use { statement -> statement.execute("CREATE SCHEMA $schema") } }
        try {
            val scoped = source().apply { currentSchema = schema }
            val root = dir.resolve("scale-files")
            Files.createDirectories(root)
            Files.writeString(root.resolve("proof.txt"), "synthetic original for scale test")
            val storage = LocalFileObjectStorage(root.toString())
            val database = Database.connect(scoped)
            val metadata = transaction(database) {
                SchemaUtils.create(FileTable, CollectionPermanentArchives, CollectionPermanentArchiveFiles)
                val file = FileDao.new("proof") {
                    name = "合成容量原件"; path = "proof.txt"; ext = "txt"; hash = "synthetic"; size = Files.size(root.resolve("proof.txt"))
                }
                val content = CollectionOriginalContent(
                    java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(root.resolve("proof.txt")))
                        .joinToString("") { "%02x".format(it) }, file.size,
                )
                CollectionArchiveFileIdentity.metadata(file, "museum", "COLLECTION_ACCESSION_RECEIPT", "receipt",
                    FileReadPath.keys(file.path, file.storageType.name, storage), content)
            }
            val digest = CollectionCareEvidence.digest("{}")
            scoped.connection.use { connection ->
                connection.prepareStatement(
                    "INSERT INTO col_permanent_archive (id, create_time, org_id, biz_type, biz_id, phase, snapshot, digest) " +
                        "SELECT 'scale-archive-' || lpad(g::text, 6, '0'), now(), 'museum', " +
                        "'COLLECTION_ACCESSION_RECEIPT', 'receipt', 'RECORDED_' || g, '{}', ? FROM generate_series(1, 100000) g",
                ).use { statement -> statement.setString(1, digest); statement.executeUpdate() }
                connection.prepareStatement(
                    "INSERT INTO col_permanent_archive_file (id, create_time, org_id, archive_id, biz_id, file_id, metadata) " +
                        "SELECT 'scale-reference-' || lpad(g::text, 6, '0'), now(), 'museum', " +
                        "'scale-archive-' || lpad(g::text, 6, '0'), 'receipt', 'proof', ? FROM generate_series(1, 100000) g",
                ).use { statement -> statement.setString(1, metadata); statement.executeUpdate() }
            }
            val started = System.nanoTime()
            val outcome = transaction(database) { CollectionPermanentOriginalIdentityInspection.inspect(storage) }
            val elapsed = (System.nanoTime() - started) / 1_000_000
            println("PERMANENT_IDENTITY_SCALE_OBSERVATION phase=verified references=100000 elapsedMillis=$elapsed state=${outcome.state}")
            assertEquals(EnumDeploymentCheckState.PASS, outcome.state)
            assertEquals("100000", outcome.safeFacts.single { it.name == "scannedArchiveFiles" }.value)
            assertEquals("0", outcome.safeFacts.single { it.name == "nativeIdentityMismatch" }.value)
            val replacement = root.resolve("replacement.txt")
            Files.copy(root.resolve("proof.txt"), replacement)
            Files.move(replacement, root.resolve("proof.txt"), StandardCopyOption.REPLACE_EXISTING)
            val mismatchStarted = System.nanoTime()
            val mismatch = transaction(database) { CollectionPermanentOriginalIdentityInspection.inspect(storage) }
            val mismatchElapsed = (System.nanoTime() - mismatchStarted) / 1_000_000
            println("PERMANENT_IDENTITY_SCALE_OBSERVATION phase=replaced references=100000 elapsedMillis=$mismatchElapsed state=${mismatch.state}")
            assertEquals(EnumDeploymentCheckState.FAIL, mismatch.state)
            assertEquals("100000", mismatch.safeFacts.single { it.name == "nativeIdentityMismatch" }.value)
            assertEquals("1", mismatch.safeFacts.single { it.name == "distinctMismatchedFiles" }.value)
            assertEquals(1, mismatch.safeFacts.count { it.name.startsWith("issueSample") })
        } finally {
            source().connection.use { it.createStatement().use { statement -> statement.execute("DROP SCHEMA $schema CASCADE") } }
        }
    }
}
