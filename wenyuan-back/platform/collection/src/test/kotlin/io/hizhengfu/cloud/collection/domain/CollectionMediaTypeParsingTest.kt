package io.hizhengfu.cloud.collection.domain

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.dao.CollectionMediaVo
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CollectionMediaTypeParsingTest {
    @Test fun `explicit invalid media types are rejected instead of silently becoming OTHER`() {
        assertEquals(EnumCollectionMediaType.FRONT,JsonUtils.fromJson("""{"fileId":"file","mediaType":"FRONT"}""",CollectionMediaVo::class.java)!!.mediaType)
        assertNull(JsonUtils.fromJson("""{"fileId":"file"}""",CollectionMediaVo::class.java)!!.mediaType)
        for (value in listOf("\"INVALID\"","\"\"","3")) {
            assertThrows(Exception::class.java) {JsonUtils.fromJson("""{"fileId":"file","mediaType":$value}""",CollectionMediaVo::class.java)}
        }
    }
}
