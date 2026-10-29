package io.hizhengfu.cloud.collection.service

import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.cache.annotation.Cacheable

class ScopedListCacheContractTest {

    @Test
    fun `scope dependent no argument list methods do not share cache keys`() {
        listOf(
            CollectionCategoryService::class.java.getDeclaredMethod("findAll"),
            CollectionOrgService::class.java.getDeclaredMethod("findAll"),
            WarehouseService::class.java.getDeclaredMethod("findAll"),
        ).forEach { method ->
            assertNull(
                method.getAnnotation(Cacheable::class.java),
                "${method.declaringClass.simpleName}.${method.name} must not share a no-arg cache across org scopes"
            )
        }
    }
}
