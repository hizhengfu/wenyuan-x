package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.domain.DigitalFileSnapshot
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class DigitalPackageManifestTest {
    private fun item(id: String, path: String, refs: Set<String> = emptySet(), storage: String = "LOCAL") =
        DigitalModelDependencies.Inspected(DigitalFileSnapshot(id,path.substringAfterLast('/'),path,storage,3,"abc"),refs)
    private val model = item("model","models/abcdef.gltf",setOf("textures/t.png"))
    private val texture = item("texture","images/123456.png")
    private val mapping = mapOf("scene.gltf" to "model","textures/t.png" to "texture")

    @Test fun `logical package paths resolve references despite hashed physical filenames`() {
        assertThrows(BadRequestException::class.java){DigitalModelDependencies.requireClosed(listOf(model,texture))}
        assertDoesNotThrow{DigitalModelDependencies.requireClosed(listOf(model,texture),mapping)}
        assertEquals("models/abcdef.gltf",model.snapshot.path)
        assertEquals("images/123456.png",texture.snapshot.path)
    }
    @Test fun `explicit mapping can combine objects from different storage backends`() {
        val remote=texture.copy(snapshot=texture.snapshot.copy(storageType="RUSTFS",path="rustfs/images/123456.png"))
        assertDoesNotThrow{DigitalModelDependencies.requireClosed(listOf(model,remote),mapping)}
        assertEquals("RUSTFS",remote.snapshot.storageType)
    }
    @Test fun `deduplicated storage object may occupy multiple logical texture paths`() {
        val source=model.copy(references=setOf("textures/t.png","textures/copy.png"))
        assertDoesNotThrow{DigitalModelDependencies.requireClosed(listOf(source,texture),mapping+mapOf("textures/copy.png" to "texture"))}
    }
    @Test fun `reused manifest is checked at each logical base directory`() {
        val source=model.copy(references=setOf("texture.png"))
        val paths=mapOf("a/model.gltf" to "model","b/model.gltf" to "model","a/texture.png" to "texture")
        assertThrows(BadRequestException::class.java){DigitalModelDependencies.requireClosed(listOf(source,texture),paths)}
        assertDoesNotThrow{DigitalModelDependencies.requireClosed(listOf(source,texture),paths+mapOf("b/texture.png" to "texture"))}
    }
    @ParameterizedTest @ValueSource(strings=["empty","omitted","foreign","extra"])
    fun `manifest must cover exactly the selected storage identities`(kind: String) {
        val paths=when(kind) {"empty"->emptyMap();"omitted"->mapOf("scene.gltf" to "model");"foreign"->mapping+mapOf("foreign.png" to "foreign");else->mapping}
        val selected=if(kind=="extra")listOf(model,texture,item("extra","unused.bin")) else listOf(model,texture)
        assertThrows(BadRequestException::class.java){DigitalModelDependencies.requireClosed(selected,paths)}
    }
    @ParameterizedTest @ValueSource(strings=["/scene.gltf","../scene.gltf","a/../scene.gltf","a/./scene.gltf","a//scene.gltf","a/","C:/scene.gltf","a\\scene.gltf",""])
    fun `noncanonical package path is rejected even without dependencies`(path: String) {
        assertThrows(BadRequestException::class.java){DigitalModelDependencies.requireClosed(listOf(model.copy(references=emptySet())),mapOf(path to "model"))}
    }
    @Test fun `package filename triggers model inspection for an opaque stored filename`() {
        val bytes="""{"asset":{"version":"2.0"},"images":[{"uri":"textures/t.png"}]}""".toByteArray()
        val identity=DigitalFileSnapshot("model","opaque.bin","files/hash.bin","LOCAL",bytes.size.toLong(),"")
        val inspected=bytes.inputStream().use {DigitalModelDependencies.read(identity,"bin",it,listOf("scene.gltf"))}
        assertEquals(setOf("textures/t.png"),inspected.references)
        assertEquals(identity.path,inspected.snapshot.path)
        assertThrows(BadRequestException::class.java){DigitalModelDependencies.requireClosed(listOf(inspected),mapOf("scene.gltf" to "model"))}
    }
    @Test fun `reference cannot escape logical package through parent traversal`() {
        assertThrows(BadRequestException::class.java){DigitalModelDependencies.requireClosed(listOf(model.copy(references=setOf("../textures/t.png")),texture),mapping)}
    }
}
