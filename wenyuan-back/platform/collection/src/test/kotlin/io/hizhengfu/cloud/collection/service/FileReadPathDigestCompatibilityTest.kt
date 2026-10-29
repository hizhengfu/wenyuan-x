package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.common.file.FileReadPath
import io.hizhengfu.cloud.common.file.LocalFileObjectStorage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.security.MessageDigest

class FileReadPathDigestCompatibilityTest {
    private fun previousDigest(value: String) = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    @Test fun `path identities remain byte compatible with persisted formatter digests`() {
        val paths = listOf("proof.txt", "/userfiles/馆藏/甲 骨.pdf", "a/../b/item.txt", "rustfs/原件.pdf", "a\\b.txt", "") +
            (0..255).map { "archive/$it/原件.txt" }
        for (path in paths) for (storage in listOf(null, "LOCAL", "RUSTFS")) {
            assertEquals(previousDigest(FileReadPath.normalized(path, storage)), FileReadPath.key(path, storage))
        }
    }

    @Test fun `native identities and canonical path aliases keep the existing archive keys`() {
        val storage = mock(LocalFileObjectStorage::class.java)
        `when`(storage.readIdentity("alias.txt")).thenReturn(
            LocalFileObjectStorage.ReadIdentity("original.txt", "device:42/inode:98765"))
        assertEquals(setOf(previousDigest("alias.txt"), previousDigest("original.txt"),
            "n" + previousDigest("device:42/inode:98765").take(63)), FileReadPath.keys("alias.txt", "LOCAL", storage))
    }

    @Test fun `stable local identity remains the same when the volume device id changes`() {
        val storage = mock(LocalFileObjectStorage::class.java)
        `when`(storage.readIdentity("proof.txt")).thenReturn(
            LocalFileObjectStorage.ReadIdentity("proof.txt", "root\u0000(dev=100000e,ino=42)", "root\u0000inode=42\u0000created=1789456185109"),
            LocalFileObjectStorage.ReadIdentity("proof.txt", "root\u0000(dev=1000012,ino=42)", "root\u0000inode=42\u0000created=1789456185109"),
        )
        val before = FileReadPath.keys("proof.txt", "LOCAL", storage)
        val after = FileReadPath.keys("proof.txt", "LOCAL", storage)
        assertEquals(before.single { it.startsWith("u") }, after.single { it.startsWith("u") })
        org.junit.jupiter.api.Assertions.assertNotEquals(before.single { it.startsWith("n") }, after.single { it.startsWith("n") })
    }
}
