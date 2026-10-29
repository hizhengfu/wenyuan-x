package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.domain.CollectionPrintRenderRequest
import io.hizhengfu.cloud.collection.domain.CollectionPrintTemplateVo
import io.hizhengfu.cloud.collection.domain.CollectionPrintVo
import io.hizhengfu.cloud.collection.domain.EnumCollectionPrintType
import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class CollectionPrintRenderRequestResolverTest {

    @ParameterizedTest @EnumSource(EnumCollectionPrintType::class)
    fun `every supplied business print reloads current data and template`(type: EnumCollectionPrintType) {
        val calls=mutableListOf<String>()
        val original=print("source").copy(type=type)
        val template=CollectionPrintTemplateRegistry.resolve(original)
        val stale=original.copy(title="旧标题",template=template.copy(name="旧模板"))
        val current=original.copy(title="当前标题",template=template.copy(name="当前模板"))
        val result=CollectionPrintRenderRequestResolver.resolve(CollectionPrintRenderRequest(data=stale),{t,id ->
            calls+="$t:$id";current
        })
        assertEquals(listOf("$type:source"),calls)
        assertEquals("当前标题",result.single().title)
        assertEquals("当前模板",result.single().template!!.name)
    }

    @ParameterizedTest @EnumSource(EnumCollectionPrintType::class)
    fun `supplied print without an original business id cannot bypass current reads`(type: EnumCollectionPrintType) {
        assertThrows(BadRequestException::class.java) {
            CollectionPrintRenderRequestResolver.resolve(CollectionPrintRenderRequest(data=print("source").copy(type=type,businessId=null)),{_,_->print("unused")})
        }
    }

    @Test
    fun `display names come only from freshly authorized collections and unresolved references remain visible`() {
        val current=print("source").copy(
            fields=listOf(
                io.hizhengfu.cloud.collection.domain.CollectionPrintFieldVo("文物分类","category"),
                io.hizhengfu.cloud.collection.domain.CollectionPrintFieldVo("登账藏品","collection,unknown")),
            collections=listOf(io.hizhengfu.cloud.collection.dao.CollectionVo(id="collection",name="当前藏品名",categoryId="category",categoryName="当前分类")))
        val stale=current.copy(collections=current.collections.map { it.copy(name="客户端旧名",categoryName="客户端旧分类") })
        val result=CollectionPrintRenderRequestResolver.resolve(CollectionPrintRenderRequest(data=stale),{_,_->current}).single()
        assertEquals(listOf("当前分类","当前藏品名，unknown"),result.fields.map {it.value})
    }

    @Test
    fun `mixed prepared batch fails entirely when a current business read is denied`() {
        val calls=mutableListOf<String>()
        assertThrows(BadRequestException::class.java) {
            CollectionPrintRenderRequestResolver.resolve(CollectionPrintRenderRequest(items=listOf(print("a"),print("b"))),{_,id ->
                calls+=id
                if(id=="b")throw BadRequestException("当前无权读取")
                print(id)
            })
        }
        assertEquals(listOf("a","b"),calls)
    }

    @Test
    fun `request data selects original business and discards frontend changes`() {
        val payload = print("DATA-001")

        val resolved = CollectionPrintRenderRequestResolver.resolve(
            CollectionPrintRenderRequest(data = payload),
            fetch = { _, id -> print(id).copy(title = "当前标题") },
        )

        assertEquals(1, resolved.size)
        assertEquals(payload.documentNo, resolved.single().documentNo)
        assertEquals("当前标题", resolved.single().title)
        assertNotNull(resolved.single().template)
    }

    @Test
    fun `render request accepts custom template when empty template path was omitted from api json`() {
        val template = CollectionPrintTemplateVo(
            code = EnumCollectionPrintType.MOVEMENT.name,
            type = EnumCollectionPrintType.MOVEMENT,
            name = "馆内模板",
            title = "馆藏文物入库单",
            documentLabel = "出入库单号",
            collectionTitle = "入库藏品清单",
            templatePath = "",
            signatures = listOf("经办人", "复核人"),
            sort = 1,
            custom = true,
        )
        val request = CollectionPrintRenderRequest(data = print("DATA-001").copy(template = template))
        val json = JsonUtils.mapper.writeValueAsString(request)

        assertFalse(json.contains("templatePath"), json)

        val parsed = JsonUtils.mapper.readValue(json, CollectionPrintRenderRequest::class.java)

        assertEquals("", parsed.data?.template?.templatePath)
    }

    @Test
    fun `request ids are fetched in order when backend generates data from template type`() {
        val calls = mutableListOf<String>()

        val resolved = CollectionPrintRenderRequestResolver.resolve(
            CollectionPrintRenderRequest(type = EnumCollectionPrintType.MOVEMENT, ids = listOf("a", "b")),
            fetch = { type, id ->
                calls += "${type.name}:$id"
                print(id)
            },
        )

        assertEquals(listOf("MOVEMENT:a", "MOVEMENT:b"), calls)
        assertEquals(listOf("a", "b"), resolved.map { it.documentNo })
    }

    @Test
    fun `request must provide data or type with at least one id`() {
        assertThrows(BadRequestException::class.java) {
            CollectionPrintRenderRequestResolver.resolve(CollectionPrintRenderRequest(), fetch = { _, _ -> print("unused") })
        }
    }

    private fun print(documentNo: String) = CollectionPrintVo(
        type = EnumCollectionPrintType.MOVEMENT,
        title = "馆藏文物入库单",
        businessId = documentNo,
        documentNo = documentNo,
        orgName = "主馆",
    )
}
