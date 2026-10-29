package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.sys.file.dao.FileDao
import io.hizhengfu.cloud.sys.file.dao.Files
import io.hizhengfu.cloud.common.file.LocalFileObjectStorage
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.dao.entityCache
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.junit.jupiter.api.Assertions.*
import java.nio.file.Path

internal fun createSafetyOriginalRoot(): Path = java.nio.file.Files.createTempDirectory("safety-originals-").also { root ->
    listOf("proof", "packing").forEach { java.nio.file.Files.write(root.resolve(it), ByteArray(100) { n -> n.toByte() }) }
}

internal fun removeSafetyOriginalRoot(root: Path) {
    java.nio.file.Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(java.nio.file.Files::deleteIfExists) }
}

internal fun safetyOriginalFile(root: Path, id: String): String {
    java.nio.file.Files.write(root.resolve(id), ByteArray(100) { (it + id.hashCode()).toByte() })
    FileDao.new(id) { name="$id.pdf"; path=id; hash=id; size=100; ext="pdf" }
    return id
}

/** Corrupt only the selected phase. A whole file row is restored without fabricating a new archive. */
internal fun assertSafetyOriginalDamage(root: Path, archive: CollectionPermanentArchiveDao, fileId: String, damage: String,
    tables: Array<out Table>, actions: Array<() -> Unit>, readActions: Array<() -> Unit> = emptyArray(), restored: () -> Unit) {
    val storage=LocalFileObjectStorage(root.toString())
    val file=FileDao[fileId]
    val ref=CollectionPermanentArchiveFileDao.find {
        (CollectionPermanentArchiveFiles.archiveId eq archive.id.value) and (CollectionPermanentArchiveFiles.fileId eq fileId)
    }.single()
    val saved=CollectionPermanentArchiveFiles.selectAll().single { it[CollectionPermanentArchiveFiles.id]==ref.id }
    val refId=ref.id
    val bytes=java.nio.file.Files.readAllBytes(root.resolve(file.path))
    val modified=java.nio.file.Files.getLastModifiedTime(root.resolve(file.path))
    val unrelated=safetyOriginalFile(root,"unrelated-phase-original")
    val other=FileDao[unrelated]
    val otherMetadata=CollectionArchiveFileIdentity.metadata(other,archive.orgId,archive.bizType,archive.bizId,
        io.hizhengfu.cloud.common.file.FileReadPath.keys(other.path,"LOCAL",storage),
        storage.openStream(other.path).use { CollectionOriginalContent.read(it) })
    var added: EntityIDHolder?=null
    settledSafetySelectionState(*tables)
    restored()
    try {
        when(damage) {
            "MISSING" -> ref.delete()
            "IDENTITY" -> { ref.fileId=unrelated; ref.metadata=otherMetadata }
            "EXTRA" -> added=EntityIDHolder(CollectionPermanentArchiveFileDao.new {
                orgId=archive.orgId; archiveId=archive.id.value; bizId=archive.bizId; this.fileId=unrelated; metadata=otherMetadata
            }.id.value)
            "BYTES" -> {
                java.nio.file.Files.write(root.resolve(file.path), bytes.map { (it.toInt() xor 1).toByte() }.toByteArray())
                java.nio.file.Files.setLastModifiedTime(root.resolve(file.path), modified)
                assertEquals(bytes.size.toLong(),java.nio.file.Files.size(root.resolve(file.path)))
                assertEquals(modified,java.nio.file.Files.getLastModifiedTime(root.resolve(file.path)))
            }
            "BASELINE" -> ref.metadata=CollectionArchiveFileIdentity.metadata(file,archive.orgId,archive.bizType,archive.bizId,
                io.hizhengfu.cloud.common.file.FileReadPath.keys(file.path,"LOCAL",storage))
            else -> error(damage)
        }
        val damaged=settledSafetySelectionState(*tables)
        readActions.forEach { read ->
            if(damage=="BYTES") assertDoesNotThrow(read) else assertThrows(io.hizhengfu.cloud.common.exception.BadRequestException::class.java,read)
        }
        assertSafetySelectionIntegrity(*actions)
        val after=settledSafetySelectionState(*tables)
        assertEquals(damaged.keys,after.keys)
        damaged.forEach {(name,rows)->
            val current=after.getValue(name)
            assertTrue(rows==current) {
                val changed=rows.zip(current).withIndex().filter {(_,pair)->pair.first!=pair.second}.map {it.index}
                val columns=tables.single {it.tableName==name}.columns
                val fields=changed.flatMap {index->rows[index].zip(current[index]).mapIndexedNotNull {field,pair->
                    columns[field].name.takeIf {pair.first!=pair.second}
                }}.distinct()
                "拒绝请求改写了 $name 的全部字段快照；行数 ${rows.size}/${current.size}，变化行 $changed，字段 $fields"
            }
        }
    } finally {
        java.nio.file.Files.write(root.resolve(file.path),bytes)
        java.nio.file.Files.setLastModifiedTime(root.resolve(file.path),modified)
        added?.let { CollectionPermanentArchiveFiles.deleteWhere { id eq it.value } }
        CollectionPermanentArchiveFiles.deleteWhere { id eq refId }
        CollectionPermanentArchiveFiles.insert { insert ->
            CollectionPermanentArchiveFiles.columns.forEach { column ->
                @Suppress("UNCHECKED_CAST")
                insert[column as Column<Any?>]=saved[column]
            }
        }
        TransactionManager.current().entityCache.clear()
        settledSafetySelectionState(*tables)
    }
    restored()
}

private data class EntityIDHolder(val value: String)
