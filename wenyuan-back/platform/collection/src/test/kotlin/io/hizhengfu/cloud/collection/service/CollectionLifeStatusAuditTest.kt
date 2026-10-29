package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.CollectionDao
import io.hizhengfu.cloud.collection.dao.CollectionLedgerLogDao
import io.hizhengfu.cloud.collection.dao.CollectionLedgerLogs
import io.hizhengfu.cloud.collection.dao.Collections
import io.hizhengfu.cloud.collection.domain.EnumCollectionStatus
import io.hizhengfu.cloud.collection.domain.EnumLedgerAction
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.nio.file.Files
import java.time.LocalDateTime

class CollectionLifeStatusAuditTest {
    private val service = CollectionService(
        CollectionScopeResolver("single", "museum"),
        mock(BusinessAttachmentBinder::class.java),
        mock(CollectionRegistrationNumberService::class.java),
        CollectionOperationLockService(),
        mock(CollectionCatalogService::class.java),
        mock(CollectionChangeService::class.java),
        mock(CollectionRetainedLedgerService::class.java),
        mock(CollectionDisclosureService::class.java),
    )

    @Test
    fun `lifecycle change keeps old and new states with business reference and rolls back atomically`() {
        val path = Files.createTempFile("collection-life-status-audit-", ".db")
        try {
            val database = Database.connect("jdbc:sqlite:$path", driver = "org.sqlite.JDBC")
            transaction(database) {
                SchemaUtils.create(Collections, CollectionLedgerLogs)
                CollectionDao.new("piece") {
                    orgId = "museum"
                    totalRegNo = "M-1"
                    name = "测试藏品"
                    lifeStatus = EnumCollectionStatus.IN_STORE
                    registeredAt = LocalDateTime.of(2026, 9, 1, 9, 0)
                }
                service.changeLifeStatus("piece", EnumCollectionStatus.PRE_OUT,
                    sourceBizType = "COLLECTION_LOAN", sourceBizId = "loan-1")
                service.changeLifeStatus("piece", EnumCollectionStatus.PRE_OUT,
                    sourceBizType = "COLLECTION_LOAN", sourceBizId = "loan-1")
                val log = CollectionLedgerLogDao.all().single()
                assertEquals(EnumLedgerAction.CORRECT, log.action)
                assertEquals("lifeStatus", log.field)
                assertEquals("IN_STORE", log.before)
                assertEquals("PRE_OUT", log.after)
                assertEquals("loan-1", log.refBizId)
                assertEquals("museum", log.orgId)
                assertEquals("M-1", log.totalRegNo)
                assertThrows(BadRequestException::class.java) {
                    service.changeLifeStatus("piece", EnumCollectionStatus.RESTORING,
                        sourceBizType = "COLLECTION_LOAN", sourceBizId = "loan-1")
                }
                assertEquals(1L, CollectionLedgerLogDao.count())
            }
            assertThrows(IllegalStateException::class.java) {
                transaction(database) {
                    service.changeLifeStatus("piece", EnumCollectionStatus.IN_STORE,
                        sourceBizType = "COLLECTION_LOAN", sourceBizId = "loan-1")
                    error("force rollback after status and log write")
                }
            }
            transaction(database) {
                assertEquals(EnumCollectionStatus.PRE_OUT, CollectionDao["piece"].lifeStatus)
                assertEquals(1L, CollectionLedgerLogDao.count())
            }
        } finally {
            Files.deleteIfExists(path)
        }
    }
}
