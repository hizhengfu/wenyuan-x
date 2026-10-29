package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.common.exception.BadRequestException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class CollectionOriginalContentTest {
    @Test fun `invalid remote byte limit is rejected without reading or looping`() {
        val input = object : java.io.InputStream() {
            override fun read(): Int = error("invalid size must be rejected before reading")
        }
        assertThrows(BadRequestException::class.java) { CollectionOriginalContent.read(input, maximumBytes = -1) }
    }
    @Test fun `server content uses standard SHA256 and actual bytes`() {
        assertEquals(CollectionOriginalContent("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", 3), CollectionOriginalContent.read("abc".byteInputStream()))
        assertEquals(CollectionOriginalContent("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", 0), CollectionOriginalContent.read(byteArrayOf().inputStream()))
    }

    @Test fun `same size changed content never reaches output`() {
        val original = CollectionOriginalContent.read("abc".byteInputStream())
        assertThrows(BadRequestException::class.java) {
            original.withVerifiedCopy("abd".byteInputStream()) { fail<Unit>("Unverified content reached output") }
        }
    }

    @Test fun `length mismatch is rejected even with correct digest`() {
        val original = CollectionOriginalContent.read("abc".byteInputStream()).copy(size = 4)
        assertThrows(BadRequestException::class.java) { original.withVerifiedCopy("abc".byteInputStream()) { fail<Unit>("Incorrect size reached output") } }
    }

    @Test fun `verified output uses retained copy and removes it after success`() {
        val bytes = ByteArray(200000) { (it % 251).toByte() }
        val original = CollectionOriginalContent.read(bytes.inputStream())
        var path: Path? = null
        original.withVerifiedCopy(bytes.inputStream()) { copy ->
            path = copy
            val expected = bytes.copyOf()
            bytes.fill(0)
            assertArrayEquals(expected, Files.readAllBytes(copy))
        }
        assertFalse(Files.exists(path!!))
    }

    @Test fun `failed response also removes verified copy`() {
        val original = CollectionOriginalContent.read("abc".byteInputStream())
        var path: Path? = null
        assertThrows(java.io.IOException::class.java) {
            original.withVerifiedCopy("abc".byteInputStream()) { copy -> path = copy; throw java.io.IOException("client disconnected") }
        }
        assertFalse(Files.exists(path!!))
    }

    @Test fun `overgrown source is stopped after expected size plus one byte`() {
        val bytes=ByteArray(1000000){1}
        val baseline=CollectionOriginalContent.read("abc".byteInputStream())
        val input=bytes.inputStream()
        assertThrows(BadRequestException::class.java) { baseline.withVerifiedCopy(input) { fail<Unit>("oversized source accepted") } }
        assertEquals(bytes.size-4,input.available())
    }
    @Test fun `owned verified stream enforces range across reads and skips`() {
        val baseline=CollectionOriginalContent.read("abcdef".byteInputStream())
        var checked=false
        baseline.openVerifiedStream("abcdef".byteInputStream(),1,4){checked=true}.use { input ->
            assertTrue(checked);assertEquals('b'.code,input.read());assertEquals(1,input.skip(1))
            assertEquals("de",input.readBytes().toString(Charsets.UTF_8));assertEquals(-1,input.read())
            assertEquals(0,input.read(ByteArray(0),0,0));assertEquals(0,input.available())
        }
    }
    @Test fun `range cannot avoid mismatched bytes outside its bounds`() {
        val baseline=CollectionOriginalContent.read("abcdef".byteInputStream())
        assertThrows(BadRequestException::class.java) { baseline.openVerifiedStream("abcdeX".byteInputStream(),0,1){fail<Unit>("unverified range exposed")}.close() }
    }
    @Test fun `authorization failure after copy prevents a stream from being returned`() {
        val baseline=CollectionOriginalContent.read("abcdef".byteInputStream())
        assertThrows(BadRequestException::class.java) { baseline.openVerifiedStream("abcdef".byteInputStream(),null,null){throw BadRequestException("revoked")}.close() }
    }
}
