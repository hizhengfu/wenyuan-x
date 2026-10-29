package io.hizhengfu.cloud.collection.diagnostic

import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.EnumCollectionStatus
import io.hizhengfu.cloud.collection.domain.EnumRegistrationNoState
import io.hizhengfu.cloud.sys.diagnostic.domain.EnumDeploymentCheckState
import io.hizhengfu.cloud.sys.diagnostic.service.DeploymentDiagnosticRegistry
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.postgresql.ds.PGSimpleDataSource
import org.springframework.aop.support.AopUtils
import org.springframework.context.annotation.*
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.support.TransactionTemplate
import org.sqlite.SQLiteDataSource
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import java.util.UUID
import javax.sql.DataSource

open class CollectionNumberDiagnosticCheckTest {
    protected open val postgres = false
    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    class Transactions

    private class Runtime(val tx: TransactionTemplate, val source: DataSource, val check: CollectionNumberDiagnosticCheck) {
        fun <T> db(body: () -> T): T = tx.execute { body() }
        fun collection(id: String = "object", org: String = "museum", number: String = "000001") {
            CollectionDao.new(id) { orgId=org;totalRegNo=number;name="private-collection-name";lifeStatus=EnumCollectionStatus.IN_STORE }
        }
        fun reservation(id: String = "reservation", org: String = "museum", owner: String? = "object", number: String = "000001", retired: Boolean = false) {
            CollectionRegistrationNumberDao.new(id) {
                orgId=org;collectionId=owner;totalRegNo=number;state=if(retired)EnumRegistrationNoState.RETIRED else EnumRegistrationNoState.USED
                reason="private-reservation-reason"
            }
        }
        fun retained(id: String = "retained", owner: String = "object", org: String = "source") {
            CollectionRetainedLedgerDao.new(id) {
                orgId=org;collectionId=owner;totalRegNo="000001";transferId="transfer-$id";targetOrgId="museum";name="private-retained-name"
                archiveId="private-archive";snapshot=CollectionRetainedLedgerSnapshot(CollectionVo(id=owner,name="private-snapshot"))
            }
        }
        fun snapshot() = source.connection.use { connection ->
            listOf(Collections,CollectionRegistrationNumbers,CollectionRetainedLedgers).map { table ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT * FROM ${table.tableName} ORDER BY id").use { rows ->
                        buildList { while(rows.next()) add((1..rows.metaData.columnCount).map { rows.getString(it) }) }
                    }
                }
            }
        }
    }

    private fun fixture(body: (Runtime) -> Unit) {
        val file = if(postgres)null else Files.createTempFile("collection-number-inspection-", ".sqlite")
        val schema = "number_" + UUID.randomUUID().toString().replace("-", "")
        val source: DataSource = if(postgres) {
            val config=Properties().apply { Path.of(System.getProperty("collection.test.pg.config")).toFile().inputStream().use(::load) }
            PGSimpleDataSource().apply {
                setURL(config.getProperty("url"));user=config.getProperty("user");password=config.getProperty("password")
                connection.use { it.createStatement().use { s -> s.execute("CREATE SCHEMA $schema") } }
                currentSchema=schema
            }
        } else SQLiteDataSource().apply { url="jdbc:sqlite:$file?jdbc.explicit_readonly=true" }
        try {
            AnnotationConfigApplicationContext().use { context ->
                val manager=SpringTransactionManager(source,DatabaseConfig {})
                context.beanFactory.registerSingleton("transactionManager",manager)
                val definition=ClassPathScanningCandidateComponentProvider(true)
                    .findCandidateComponents("io.hizhengfu.cloud.collection.diagnostic")
                    .single { it.beanClassName==CollectionNumberDiagnosticCheck::class.java.name }
                context.registerBeanDefinition("numberDiagnosticCheck",definition)
                context.register(Transactions::class.java,DeploymentDiagnosticRegistry::class.java)
                context.refresh()
                val check=context.getBean(CollectionNumberDiagnosticCheck::class.java)
                assertTrue(AopUtils.isAopProxy(check))
                assertEquals("collection.regulation.number-identities",context.getBean(DeploymentDiagnosticRegistry::class.java).checks.single().descriptor.id)
                val r=Runtime(TransactionTemplate(manager),source,check)
                r.db { SchemaUtils.create(Collections,CollectionRegistrationNumbers,CollectionRetainedLedgers) }
                body(r)
            }
        } finally {
            if(postgres)source.connection.use { it.createStatement().use { s -> s.execute("DROP SCHEMA $schema CASCADE") } }
            else Files.deleteIfExists(file!!)
        }
    }

    @ParameterizedTest @ValueSource(strings=["empty","current","two-museums","multiple-transfers","deregistered"])
    fun `consistent identities pass without changing historical content`(kind: String)=fixture { r ->
        r.db {
            if(kind!="empty") { r.collection();r.reservation() }
            when(kind) {
                "two-museums" -> { r.collection("other","other");r.reservation("other-reservation","other","other") }
                "multiple-transfers" -> { r.reservation("old-reservation","source",retired=true);r.retained();r.retained("second-transfer") }
                "deregistered" -> { CollectionDao["object"].lifeStatus=EnumCollectionStatus.DEREGISTERED;CollectionRegistrationNumberDao["reservation"].state=EnumRegistrationNoState.RETIRED }
            }
            if(kind=="multiple-transfers")TransactionManager.current().exec("UPDATE col_retained_ledger SET snapshot = '\"private-unparsed-snapshot\"'")
        }
        val before=r.snapshot()
        repeat(2) {
            val result=r.check.check()
            assertEquals(EnumDeploymentCheckState.PASS,result.state,result.toString())
            assertFalse(result.toString().contains("private-"))
        }
        assertEquals(before,r.snapshot())
    }

    @ParameterizedTest @ValueSource(strings=["missing-current","wrong-current-owner","duplicate-master","master-retained-conflict",
        "duplicate-reservation","unresolved-owner","unknown-owner","wrong-state","missing-retained","wrong-retained-owner","conflicting-retained",
        "empty-master","empty-reservation","empty-retained","empty-museum"])
    fun `historical inconsistencies are reported with bounded metadata samples`(kind:String)=fixture { r ->
        val expected=r.db {
            r.collection();r.reservation()
            fun dropNumberIndex(table: io.hizhengfu.cloud.collection.common.OrgAuditUIDTable, number: Column<String>) {
                val index=table.indices.single { it.columns==listOf(table.orgId,number) }.indexName
                // PostgreSQL唯一索引由约束持有；仅隔离旧库夹具模拟缺失唯一约束。
                TransactionManager.current().exec(if(postgres)"ALTER TABLE ${table.tableName} DROP CONSTRAINT \"$index\"" else "DROP INDEX \"$index\"")
            }
            when(kind) {
                "empty-master" -> { CollectionDao["object"].totalRegNo="";"MASTER_IDENTITY_EMPTY" }
                "empty-reservation" -> { CollectionRegistrationNumberDao["reservation"].totalRegNo="";"RESERVATION_IDENTITY_EMPTY" }
                "empty-retained" -> { r.retained();CollectionRetainedLedgerDao["retained"].totalRegNo="";"RETAINED_IDENTITY_EMPTY" }
                "empty-museum" -> { CollectionDao["object"].orgId="";CollectionRegistrationNumberDao["reservation"].orgId="";"MASTER_IDENTITY_EMPTY" }
                "missing-current" -> { CollectionRegistrationNumbers.deleteAll();"MASTER_RESERVATION_MISSING" }
                "wrong-current-owner" -> { CollectionRegistrationNumberDao["reservation"].collectionId="wrong";"MASTER_RESERVATION_OWNER_CONFLICT" }
                "duplicate-master" -> { dropNumberIndex(Collections,Collections.totalRegNo);r.collection("duplicate");"MASTER_NUMBER_DUPLICATE" }
                "master-retained-conflict" -> { r.retained(owner="different-object",org="museum");"MASTER_RETAINED_OWNER_CONFLICT" }
                "duplicate-reservation" -> { dropNumberIndex(CollectionRegistrationNumbers,CollectionRegistrationNumbers.totalRegNo);r.reservation("duplicate");"RESERVATION_DUPLICATE" }
                "unresolved-owner" -> { r.reservation("orphan",owner="unknown",number="orphan-number");"RESERVATION_OWNER_UNRESOLVED" }
                "unknown-owner" -> { r.reservation("orphan",owner=null,number="orphan-number");"RESERVATION_OWNER_UNRESOLVED" }
                "wrong-state" -> { CollectionRegistrationNumberDao["reservation"].state=EnumRegistrationNoState.RETIRED;"RESERVATION_STATE_CONFLICT" }
                "missing-retained" -> { r.retained();"RETAINED_RESERVATION_MISSING" }
                "wrong-retained-owner" -> { r.retained();r.reservation("source-reservation","source","wrong",retired=true);"RETAINED_RESERVATION_OWNER_CONFLICT" }
                else -> { r.retained();r.retained("conflict","different-object");"RETAINED_OWNER_CONFLICT" }
            }
        }
        val before=r.snapshot()
        val result=r.check.check()
        assertEquals(EnumDeploymentCheckState.FAIL,result.state,result.toString())
        assertTrue(result.safeFacts.single { it.name==expected }.value.toLong()>0,result.toString())
        assertTrue(result.safeFacts.count { it.name.startsWith("issueSample") } in 1..10)
        assertFalse(result.toString().contains("private-"))
        assertEquals(before,r.snapshot())
    }

    @Test fun `sample limits do not truncate issue counts`()=fixture { r ->
        r.db { repeat(25) { r.collection("item-$it",number="number-$it") } }
        val before=r.snapshot()
        val result=r.db { CollectionNumberInspection.inspect(sampleLimit=3) }
        assertEquals("25",result.safeFacts.single { it.name=="MASTER_RESERVATION_MISSING" }.value)
        assertEquals(3,result.safeFacts.count { it.name.startsWith("issueSample") })
        assertEquals(before,r.snapshot())
    }

    @Test fun `interruption cannot produce a passing scan`()=fixture { r ->
        r.db {
            Thread.currentThread().interrupt()
            try { assertThrows(InterruptedException::class.java) { CollectionNumberInspection.inspect() } }
            finally { Thread.interrupted() }
        }
    }

    @Test fun `missing ledger table is an execution failure and is never treated as an empty ledger`()=fixture { r ->
        r.db { r.collection();r.reservation();SchemaUtils.drop(CollectionRetainedLedgers) }
        val error=assertThrows(Exception::class.java) { r.check.check() }
        assertTrue(generateSequence(error as Throwable?) { it.cause }.any {
            it is java.sql.SQLException && it.message.orEmpty().contains("col_retained_ledger")
        },error.stackTraceToString())
        r.db {
            assertEquals(1L,Collections.select(Collections.id).count())
            assertEquals(1L,CollectionRegistrationNumbers.select(CollectionRegistrationNumbers.id).count())
        }
    }
}

@Tag("postgres-concurrency")
class CollectionNumberDiagnosticPostgresTest : CollectionNumberDiagnosticCheckTest() {
    override val postgres = true
}
