package io.hizhengfu.cloud.collection.rfid.dao

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class RfidHandheldReadBatchSchemaTest {

    @Test
    fun `device local session and sequence form the offline batch idempotency key`() {
        val database = Database.connect(
            "jdbc:sqlite:file:rfid-handheld-batch?mode=memory&cache=shared",
            driver = "org.sqlite.JDBC",
        )

        transaction(database) {
            SchemaUtils.create(RfidHandheldReadBatches)
            insertBatch("BATCH-1", "SESSION-1")

            assertThrows(ExposedSQLException::class.java) {
                insertBatch("BATCH-2", "SESSION-1")
            }
        }
    }

    private fun insertBatch(id: String, sessionId: String) {
        RfidHandheldReadBatches.insert {
            it[RfidHandheldReadBatches.id] = id
            it[RfidHandheldReadBatches.sessionId] = sessionId
            it[deviceId] = "HANDHELD-1"
            it[localSessionId] = "LOCAL-1"
            it[batchSequence] = 7
            it[readCount] = 2
            it[receivedAt] = LocalDateTime.now()
        }
    }
}
