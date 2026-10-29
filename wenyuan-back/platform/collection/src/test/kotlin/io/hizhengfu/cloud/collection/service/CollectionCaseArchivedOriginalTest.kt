package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.common.exception.BadRequestException
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class CollectionCaseArchivedOriginalTest {
    @Test fun `only file in selected authorized archive can be read`() {
        val detail = mapOf<String, Any?>("archives" to listOf(
            mapOf("id" to "first", "files" to listOf(mapOf("fileId" to "one"))),
            mapOf("id" to "second", "files" to listOf(mapOf("fileId" to "two"))),
        ))
        assertDoesNotThrow { CollectionCaseArchivedOriginal.require(detail, "first", "one") }
        assertThrows(BadRequestException::class.java) { CollectionCaseArchivedOriginal.require(detail, "first", "two") }
        assertThrows(BadRequestException::class.java) { CollectionCaseArchivedOriginal.require(detail, "missing", "one") }
        assertThrows(BadRequestException::class.java) { CollectionCaseArchivedOriginal.require(mapOf("archives" to emptyList<Any>()), "first", "one") }
    }
}
