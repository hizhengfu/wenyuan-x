package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.CollectionIncidents
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.sqlite.SQLiteDataSource
import org.springframework.aop.support.AopUtils
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.support.TransactionTemplate

class CollectionSafetyTaskTransactionTest {
    private object Probe : Table("safety_task_transaction_probe") {
        val id = integer("id")
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    class Transactions

    @Test
    fun `invalid incident original can become a safety task without rolling back the read transaction`() {
        val ds = SQLiteDataSource().apply {
            url = "jdbc:sqlite:file:safety-task-tx-${System.nanoTime()}?mode=memory&cache=shared"
        }
        ds.connection.use {
            val manager = SpringTransactionManager(ds, DatabaseConfig {})
            val template = TransactionTemplate(manager)
            AnnotationConfigApplicationContext().use { context ->
                context.beanFactory.registerSingleton("transactionManager", manager)
                context.beanFactory.registerSingleton("scope", mock(CollectionScopeResolver::class.java))
                context.beanFactory.registerSingleton("users", mock(CollectionResponsibleUserService::class.java))
                context.beanFactory.registerSingleton("archives", mock(CollectionPermanentArchiveService::class.java))
                context.beanFactory.registerSingleton("sources", mock(CollectionIncidentSourceService::class.java))
                context.register(Transactions::class.java, CollectionIncidentService::class.java)
                context.refresh()

                val incidents = context.getBean(CollectionIncidentService::class.java)
                assertTrue(AopUtils.isAopProxy(incidents))
                template.executeWithoutResult { SchemaUtils.create(CollectionIncidents, Probe) }
                template.executeWithoutResult {
                    assertThrows(BadRequestException::class.java) {
                        incidents.referenceSummary("museum", "missing-incident")
                    }
                    Probe.insert { row -> row[Probe.id] = 1 }
                }
                template.executeWithoutResult { assertEquals(1L, Probe.selectAll().count()) }
            }
        }
    }
}
