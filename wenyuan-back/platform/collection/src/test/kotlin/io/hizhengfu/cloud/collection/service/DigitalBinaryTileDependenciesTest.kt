package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.domain.DigitalFileSnapshot
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

internal object DigitalTileFixture {
    fun glb(uri: String = "texture.png"): ByteArray {
        val raw = """{"asset":{"version":"2.0"},"images":[{"uri":"$uri"}]}""".toByteArray()
        val json = raw + ByteArray((4 - raw.size % 4) % 4) { 32 }
        return ByteBuffer.allocate(32 + json.size).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0x46546c67).putInt(2).putInt(32 + json.size).putInt(json.size).putInt(0x4e4f534a).put(json)
            .putInt(4).putInt(0x004e4942).putInt(123).array()
    }
    fun tile(type: String, body: ByteArray = glb(), format: Int = 1): ByteArray {
        if (type == "cmpt") return ByteBuffer.allocate(16 + body.size).order(ByteOrder.LITTLE_ENDIAN)
            .put("cmpt".toByteArray()).putInt(1).putInt(16 + body.size).putInt(1).put(body).array()
        val header = if (type == "i3dm") 32 else 28
        val table = "{}".toByteArray() + ByteArray((8 - (header + 2) % 8) % 8) { 32 }
        val padding = ByteArray((8 - (header + table.size + body.size) % 8) % 8) { if (format == 0) 32 else 0 }
        val b = ByteBuffer.allocate(header + table.size + body.size + padding.size).order(ByteOrder.LITTLE_ENDIAN)
            .put(type.toByteArray()).putInt(1).putInt(header + table.size + body.size + padding.size)
            .putInt(table.size).putInt(0).putInt(0).putInt(0)
        if (type == "i3dm") b.putInt(format)
        return b.put(table).put(body).put(padding).array()
    }
}

class DigitalBinaryTileDependenciesTest {
    private fun inspect(bytes: ByteArray, name: String = "model.b3dm", aliases: List<String> = emptyList(), shortReads: Boolean = false): DigitalModelDependencies.Inspected {
        val input = object : ByteArrayInputStream(bytes) {
            override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, if (shortReads) minOf(len, 3) else len)
        }
        return input.use { DigitalModelDependencies.read(DigitalFileSnapshot("model",name,"pkg/$name","LOCAL",bytes.size.toLong(),""),name.substringAfterLast('.'),it,aliases) }
    }
    private fun asset(name: String) = DigitalModelDependencies.Inspected(DigitalFileSnapshot(name,name,"pkg/$name","LOCAL",1,"a"),emptySet())

    @ParameterizedTest @ValueSource(strings = ["b3dm", "i3dm", "cmpt"])
    fun `embedded texture must be registered and whole container bytes determine hash`(type: String) {
        val bytes = if (type == "cmpt") DigitalTileFixture.tile(type,DigitalTileFixture.tile("b3dm")) else DigitalTileFixture.tile(type)
        val x = inspect(bytes,"model.$type",shortReads=true)
        assertEquals(setOf("texture.png"),x.references)
        assertEquals(MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) },x.snapshot.sha256)
        assertThrows(BadRequestException::class.java) { DigitalModelDependencies.requireClosed(listOf(x)) }
        assertDoesNotThrow { DigitalModelDependencies.requireClosed(listOf(x,asset("texture.png"))) }
    }
    @Test fun `nested composite gathers all member dependencies at container logical base`() {
        val inner = DigitalTileFixture.tile("cmpt",DigitalTileFixture.tile("i3dm","mesh.glb".toByteArray(),0))
        val x = inspect(DigitalTileFixture.tile("cmpt",inner),"model.cmpt")
        assertEquals(setOf("mesh.glb"),x.references)
        assertThrows(BadRequestException::class.java) { DigitalModelDependencies.requireClosed(listOf(x)) }
        assertDoesNotThrow { DigitalModelDependencies.requireClosed(listOf(x,asset("mesh.glb"))) }
    }
    @ParameterizedTest @ValueSource(strings = ["https://outside.invalid/a.glb", "//outside.invalid/a.glb", "../a.glb", "a.glb?x=1", "{level}.glb", "data:application/octet-stream;base64,AAAA"])
    fun `external i3dm cannot hide an unmanaged model reference`(uri: String) {
        assertThrows(BadRequestException::class.java) {
            val x=inspect(DigitalTileFixture.tile("i3dm",uri.toByteArray(),0),"model.i3dm")
            DigitalModelDependencies.requireClosed(listOf(x),mapOf("model.i3dm" to "model"))
        }
    }
    @ParameterizedTest @ValueSource(strings = ["opaque.bin", "opaque.b3dm"])
    fun `container magic is inspected with opaque storage names`(name: String) {
        val bytes=DigitalTileFixture.tile("b3dm")
        assertEquals(setOf("texture.png"),inspect(bytes,name).references)
        assertEquals(setOf("texture.png"),inspect(bytes,"hash.bin",listOf(name)).references)
    }
    @ParameterizedTest @ValueSource(strings = ["magic", "version", "total", "unsigned", "table", "table-json", "embedded-length", "embedded-version", "truncated", "format", "child-count", "child-length", "trailing"])
    fun `invalid containers fail registration before claiming frozen closure`(kind: String) {
        var bytes=if(kind.startsWith("child")) DigitalTileFixture.tile("cmpt",DigitalTileFixture.tile("b3dm")) else DigitalTileFixture.tile("i3dm")
        val b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val offset=32+b.getInt(12)
        when(kind) {
            "magic" -> b.putInt(0,0)
            "version" -> b.putInt(4,2)
            "total" -> b.putInt(8,12)
            "unsigned" -> b.putInt(8,-1)
            "table" -> b.putInt(12,Int.MAX_VALUE)
            "table-json" -> bytes[32]=33
            "embedded-length" -> b.putInt(offset+8,Int.MAX_VALUE)
            "embedded-version" -> b.putInt(offset+4,1)
            "truncated" -> bytes=bytes.copyOf(bytes.size-1)
            "format" -> b.putInt(28,2)
            "child-count" -> b.putInt(12,2)
            "child-length" -> b.putInt(24,Int.MAX_VALUE)
            "trailing" -> { bytes=bytes+byteArrayOf(1);ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(8,bytes.size) }
        }
        assertThrows(BadRequestException::class.java) { inspect(bytes,if(kind.startsWith("child")) "model.cmpt" else "model.i3dm") }
    }
    @Test fun `point table payload is streamed and hashed without a false model dependency`() {
        val bytes=DigitalTileFixture.tile("pnts",ByteArray(0))
        val x=inspect(bytes,"model.pnts")
        assertEquals(emptySet<String>(),x.references)
        assertEquals(MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) },x.snapshot.sha256)
    }
    @Test fun `excessively nested composites fail with a controlled error`() {
        var bytes=DigitalTileFixture.tile("b3dm")
        repeat(66) { bytes=DigitalTileFixture.tile("cmpt",bytes) }
        assertThrows(BadRequestException::class.java) { inspect(bytes,"model.cmpt") }
    }

    @Test fun `composite siblings retain their own embedded GLB boundaries`() {
        val body=DigitalTileFixture.tile("b3dm",DigitalTileFixture.glb("a.png")) +
            DigitalTileFixture.tile("i3dm",DigitalTileFixture.glb("b.png"))
        val bytes=DigitalTileFixture.tile("cmpt",body)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(12,2)
        assertEquals(setOf("a.png","b.png"),inspect(bytes,"model.cmpt",shortReads=true).references)
    }

    @Test fun `large point payload uses bounded streaming reads and hashes every byte`() {
        val payloadSize=8*1024*1024
        val header=ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN)
            .put("pnts".toByteArray()).putInt(1).putInt(32+payloadSize).putInt(4).putInt(payloadSize).putInt(0).putInt(0)
            .put("{}  ".toByteArray()).array()
        var remaining=payloadSize
        val payload=object:java.io.InputStream() {
            override fun read():Int = if(remaining==0) -1 else {remaining--;37}
            override fun read(b:ByteArray,off:Int,len:Int):Int {
                check(len<=65536) {"Binary payload must not be accumulated as a single array"}
                if(remaining==0)return -1
                val n=minOf(remaining,len);b.fill(37,off,off+n);remaining-=n;return n
            }
        }
        val stream=java.io.SequenceInputStream(header.inputStream(),payload)
        val x=stream.use {DigitalModelDependencies.read(DigitalFileSnapshot("x","x.pnts","x","LOCAL",32L+payloadSize,""),"pnts",it)}
        val expected=MessageDigest.getInstance("SHA-256");expected.update(header)
        repeat(payloadSize/65536) {expected.update(ByteArray(65536){37})}
        assertEquals(expected.digest().joinToString("") {"%02x".format(it)},x.snapshot.sha256)
        assertEquals(0,remaining)
    }

    @Test fun `binary GLB renamed as opaque data retains texture inspection`() {
        assertEquals(setOf("texture.png"),inspect(DigitalTileFixture.glb(),"hash.bin").references)
    }

    @ParameterizedTest @ValueSource(strings=["short","extra","utf8","empty-uri","long-uri","pnts-trailing"])
    fun `stream and external URI boundaries cannot produce a frozen snapshot`(kind:String) {
        val bytes=when(kind) {
            "utf8" -> DigitalTileFixture.tile("i3dm",byteArrayOf(0xc3.toByte(),0x28),0)
            "empty-uri" -> DigitalTileFixture.tile("i3dm"," ".toByteArray(),0)
            "long-uri" -> DigitalTileFixture.tile("i3dm",ByteArray(65537){97},0)
            "pnts-trailing" -> DigitalTileFixture.tile("pnts",byteArrayOf(1))
            else -> DigitalTileFixture.tile("b3dm")
        }
        val size=bytes.size.toLong()+if(kind=="short")1 else if(kind=="extra")-1 else 0
        assertThrows(BadRequestException::class.java) {
            bytes.inputStream().use {DigitalModelDependencies.read(DigitalFileSnapshot("x","x.bin","x","LOCAL",size,""),"bin",it)}
        }
    }
}
