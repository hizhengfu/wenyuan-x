package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.common.exception.BadRequestException
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class CollectionCirculationArchivedOriginalTest {
    @Test fun `only a file in the selected archive is readable`() {
        val archiveId="2100000000000000001"
        val fileId="2100000000000000002"
        val otherFile="2100000000000000003"
        val archives=listOf(mapOf<String,Any?>("id" to archiveId,"phase" to "HANDOVER_1",
            "files" to listOf(mapOf("fileId" to fileId))))
        assertDoesNotThrow {CollectionCirculationArchivedOriginal.require(archives,archiveId,fileId)}
        assertThrows(BadRequestException::class.java) {CollectionCirculationArchivedOriginal.require(archives,"2100000000000000004",fileId)}
        assertThrows(BadRequestException::class.java) {CollectionCirculationArchivedOriginal.require(archives,archiveId,otherFile)}
        assertThrows(BadRequestException::class.java) {CollectionCirculationArchivedOriginal.require(archives,"bad",fileId)}
    }
}
