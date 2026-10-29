package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.domain.DigitalFileSnapshot
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

class DigitalModelDependenciesTest {
    private fun file(path: String, bytes: ByteArray, ext: String = path.substringAfterLast('.'), storage: String = "LOCAL", id: String = path): DigitalModelDependencies.Inspected =
        bytes.inputStream().use { DigitalModelDependencies.read(DigitalFileSnapshot(id,path.substringAfterLast('/'),path,storage,bytes.size.toLong(),""),ext,it) }
    private fun model(uri: String, path: String = "pkg/model.gltf", storage: String = "LOCAL") =
        file(path,"""{"asset":{"version":"2.0"},"buffers":[{"uri":"$uri"}]}""".toByteArray(),storage=storage)
    private fun asset(path: String, storage: String = "LOCAL", id: String = path) = file(path,byteArrayOf(1,2,3),storage=storage,id=id)
    private fun glb(json: String): ByteArray {
        val encoded=json.toByteArray();val padded=encoded+ByteArray((4-encoded.size%4)%4){32}
        return ByteBuffer.allocate(20+padded.size+12).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0x46546c67).putInt(2).putInt(20+padded.size+12).putInt(padded.size).putInt(0x4e4f534a).put(padded)
            .putInt(4).putInt(0x004e4942).putInt(0).array()
    }

    @Test fun `GLB external texture is extracted while hash includes binary chunk`() {
        val bytes=glb("""{"asset":{"version":"2.0"},"images":[{"uri":"texture.png"}]}""")
        val inspected=file("pkg/model.glb",bytes)
        assertEquals(setOf("texture.png"),inspected.references)
        assertEquals(MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)},inspected.snapshot.sha256)
        assertThrows(BadRequestException::class.java){DigitalModelDependencies.requireClosed(listOf(inspected))}
        assertDoesNotThrow{DigitalModelDependencies.requireClosed(listOf(inspected,asset("pkg/texture.png")))}
    }
    @ParameterizedTest @ValueSource(strings=["data:application/octet-stream;base64,AQID", "data:image/png;base64,AQID"])
    fun `embedded glTF buffers and textures need no separate file`(uri: String) {
        assertDoesNotThrow{DigitalModelDependencies.requireClosed(listOf(model(uri)))}
    }
    @ParameterizedTest @ValueSource(strings=["texture.png", "./texture.png", "sub/../texture.png", "tex%74ure.png"])
    fun `relative reference resolves to exact registered path`(uri: String) {
        assertDoesNotThrow{DigitalModelDependencies.requireClosed(listOf(model(uri),asset("pkg/texture.png")))}
    }
    @Test fun `encoded plus space and unicode resolve without form decoding`() {
        assertDoesNotThrow{DigitalModelDependencies.requireClosed(listOf(model("a+%20%E4%B8%AD.png"),asset("pkg/a+ 中.png")))}
    }
    @ParameterizedTest @ValueSource(strings=["https://example.invalid/texture.png", "//example.invalid/x", "/userfiles/pkg/x", "file:///tmp/x", "texture.png?storageType=RUSTFS", "texture.png#x", "{level}/{x}.glb", "%5Cprivate.png"])
    fun `unmanaged or ambiguous URI cannot claim package closure`(uri: String) {
        assertThrows(BadRequestException::class.java){DigitalModelDependencies.requireClosed(listOf(model(uri),asset("pkg/texture.png")))}
    }
    @Test fun `same filename in another directory is not a dependency match`() {
        assertThrows(BadRequestException::class.java){DigitalModelDependencies.requireClosed(listOf(model("texture.png"),asset("other/texture.png")))}
    }
    @Test fun `object storage dependency cannot be supplied by local file`() {
        assertThrows(BadRequestException::class.java){DigitalModelDependencies.requireClosed(listOf(model("texture.png",storage="RUSTFS"),asset("pkg/texture.png")))}
        assertDoesNotThrow{DigitalModelDependencies.requireClosed(listOf(model("texture.png",storage="RUSTFS"),asset("/userfiles/rustfs/pkg/texture.png","RUSTFS")))}
    }
    @Test fun `duplicate paths with different file identities cannot prove unique dependency`() {
        assertThrows(BadRequestException::class.java){DigitalModelDependencies.requireClosed(listOf(model("texture.png"),asset("pkg/texture.png",id="one"),asset("pkg/texture.png",id="two")))}
    }
    @Test fun `explicit nested tilesets and GLB require every referenced file`() {
        val top=file("pkg/tileset.json","""{"asset":{"version":"1.1"},"root":{"contents":[{"uri":"child.json"},{"uri":"mesh.glb"}]}}""".toByteArray())
        val child=file("pkg/child.json","""{"asset":{"version":"1.0"},"root":{"content":{"url":"mesh.glb"}}}""".toByteArray())
        val mesh=file("pkg/mesh.glb",glb("""{"asset":{"version":"2.0"},"images":[{"uri":"texture.png"}]}"""))
        assertThrows(BadRequestException::class.java){DigitalModelDependencies.requireClosed(listOf(top,child,mesh))}
        assertDoesNotThrow{DigitalModelDependencies.requireClosed(listOf(top,child,mesh,asset("pkg/texture.png")))}
    }
    @Test fun `extras URLs are metadata and generic JSON is not a tileset`() {
        val generic=file("pkg/report.json","""{"uri":"https://example.invalid/report"}""".toByteArray())
        val gltf=file("pkg/model.gltf","""{"asset":{"version":"2.0"},"extras":{"uri":"https://example.invalid/credit"}}""".toByteArray())
        assertDoesNotThrow{DigitalModelDependencies.requireClosed(listOf(generic,gltf))}
    }
    @ParameterizedTest @ValueSource(strings=["magic", "version", "total", "type", "oversized", "unaligned", "truncated"])
    fun `malformed GLB cannot bypass dependency inspection`(kind: String) {
        val bytes=glb("""{"asset":{"version":"2.0"}}""")
        val b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        when(kind) {
            "magic"->b.putInt(0,0); "version"->b.putInt(4,1); "total"->b.putInt(8,1); "type"->b.putInt(16,0)
            "oversized"->b.putInt(12,Int.MAX_VALUE); "unaligned"->b.putInt(12,3)
        }
        assertThrows(BadRequestException::class.java){file("model.glb",if(kind=="truncated")bytes.copyOf(12) else bytes)}
    }
    @Test fun `actual source length must match the metadata used for frozen identity`() {
        assertThrows(BadRequestException::class.java) {
            byteArrayOf(1).inputStream().use { DigitalModelDependencies.read(DigitalFileSnapshot("x","x.bin","x","LOCAL",2,""),"bin",it) }
        }
    }
    @Test fun `GLB header split across short reads retains all dependency bytes`() {
        val bytes=glb("""{"asset":{"version":"2.0"},"images":[{"uri":"texture.png"}]}""")
        val input=object:java.io.ByteArrayInputStream(bytes) {
            override fun read(buffer:ByteArray,off:Int,len:Int)=super.read(buffer,off,minOf(len,3))
        }
        val inspected=input.use {DigitalModelDependencies.read(DigitalFileSnapshot("x","x.glb","pkg/x.glb","LOCAL",bytes.size.toLong(),""),"glb",it)}
        assertEquals(setOf("texture.png"),inspected.references)
        assertEquals(bytes.size.toLong(),inspected.snapshot.size)
    }

}
