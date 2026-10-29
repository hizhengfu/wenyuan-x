package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.common.exception.BadRequestException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CollectionImportNameResolverTest {
    private val resolver = CollectionImportNameResolver(listOf(
        CollectionImportNameResolver.Entry("a", "一号库房", "0"),
        CollectionImportNameResolver.Entry("b", "二号库房", "0"),
        CollectionImportNameResolver.Entry("c", "一号柜", "a"),
        CollectionImportNameResolver.Entry("d", "一号柜", "b"),
    ))
    @Test fun `unique names and legacy ids resolve without changing id semantics`() {
        assertEquals("a", resolver.resolve(" 一号库房 ", null, "库位"))
        assertEquals("a", resolver.resolve(null, "a", "库位"))
        assertNull(resolver.resolve(null, null, "库位"))
    }
    @Test fun `ambiguous names require full path and never select first record`() {
        assertThrows(BadRequestException::class.java) { resolver.resolve("一号柜", null, "库位") }
        assertEquals("c", resolver.resolve("一号库房/一号柜", null, "库位"))
        assertThrows(BadRequestException::class.java) { resolver.resolve("三号库房/一号柜", null, "库位") }
        assertThrows(BadRequestException::class.java) { resolver.resolve("一号库房", "b", "库位") }
    }
}
