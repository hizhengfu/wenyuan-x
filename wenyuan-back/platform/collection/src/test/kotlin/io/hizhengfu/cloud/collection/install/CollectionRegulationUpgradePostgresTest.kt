package io.hizhengfu.cloud.collection.install

import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.diagnostic.CollectionModuleSchemaCheck
import io.hizhengfu.cloud.collection.diagnostic.CollectionRegulationSchemaDiagnosticCheck
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.collection.service.CollectionCareEvidence
import io.hizhengfu.cloud.collection.service.CollectionLocationMutation
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.sys.diagnostic.domain.EnumDeploymentCheckState
import io.hizhengfu.cloud.sys.install.service.InstallService
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.vendors.currentDialectMetadata
import org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.postgresql.ds.PGSimpleDataSource
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.support.TransactionTemplate
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.Properties
import java.util.UUID

/** 沿用显式 PostgreSQL 集成任务；合成旧库只在独立随机 schema 中创建，不连接实际业务数据。 */
@Tag("postgres-concurrency")
class CollectionRegulationUpgradePostgresTest {
    @Configuration(proxyBeanMethods=false)
    @EnableTransactionManagement(proxyTargetClass=true)
    class Transactions

    private val at=LocalDateTime.of(2026,8,31,10,20,30,123456000)
    private val originalArchive="""{"oldField":"原始签署材料","legacyPrecision":"2026-08-31T10:20:30.123456","unknownField":{"keep":true}}"""
    private val added=linkedMapOf<Table,Set<Column<*>>>(
        CollectionOrgs to setOf(CollectionOrgs.locationMutationVersion),
        Collections to setOf(Collections.nature,Collections.inventoryHold),
        Warehouses to setOf(Warehouses.staffing),
        WarehouseLogs to setOf(WarehouseLogs.diary,WarehouseLogs.entries),
        WarehouseEntryApplications to setOf(WarehouseEntryApplications.dossier,WarehouseEntryApplications.baseline,WarehouseEntryApplications.entryRecord,WarehouseEntryApplications.exitRecord),
        CollectionChanges to setOf(CollectionChanges.paperLedgerBookId,CollectionChanges.paperLedgerPageNo),
    )
    private fun exec(sql:String)=org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().exec(sql)
    private fun quoted(value:String)="\""+value.replace("\"","\"\"")+"\""
    private class Runtime(val source:PGSimpleDataSource,val install:CollectionInstall,val service:InstallService,val tx:TransactionTemplate) {
        fun <T> db(body:()->T):T=tx.execute { body() }
    }
    private fun fixture(body:(Runtime)->Unit) {
        val props=Properties().apply { Path.of(System.getProperty("collection.test.pg.config")).toFile().inputStream().use(::load) }
        val schema="reg_upgrade_"+UUID.randomUUID().toString().replace("-","")
        val source=PGSimpleDataSource().apply { setURL(props.getProperty("url"));user=props.getProperty("user");password=props.getProperty("password");currentSchema=schema }
        source.connection.use { c -> c.createStatement().use { it.execute("CREATE SCHEMA $schema") } }
        try { AnnotationConfigApplicationContext().use { context ->
            val manager=SpringTransactionManager(source,DatabaseConfig {})
            val install=CollectionInstall("multi")
            context.beanFactory.registerSingleton("transactionManager",manager)
            context.beanFactory.registerSingleton("cacheManager",ConcurrentMapCacheManager())
            context.beanFactory.registerSingleton("collectionInstall",install)
            context.register(Transactions::class.java,InstallService::class.java)
            context.refresh();SpringContextHolder().setApplicationContext(context)
            body(Runtime(source,install,context.getBean(InstallService::class.java),TransactionTemplate(manager)))
        } } finally {
            source.connection.use { c -> c.createStatement().use { it.execute("DROP SCHEMA $schema CASCADE") } }
        }
    }
    private fun legacy(r:Runtime) {
        r.db {
            SchemaUtils.create(*r.install.initTables().toTypedArray())
            CollectionOrgDao.new("museum") {code="M1";name="原收藏单位"}
            CollectionDao.new("object") {orgId="museum";name="原始藏品";totalRegNo="000001";lifeStatus=EnumCollectionStatus.IN_STORE}
            WarehouseDao.new("store") {orgId="museum";name="原保管库";sceneType=EnumLocationScene.STORAGE}
            WarehouseLogDao.new("log") {orgId="museum";warehouseId="store";logDate=at.toLocalDate();content="旧纸质日记文字";attachmentIds="old-paper-file"}
            WarehouseEntryApplicationDao.new("visit") {
                orgId="museum";applicationNo="OLD-1";applicantId="former";entrantIds="former,other";warehouseIds="store";purpose="原入库事由"
                plannedStart=at;plannedEnd=at.plusHours(1);approvedAt=at.minusHours(1);actualEntryAt=at;actualExitAt=at.plusMinutes(30)
                flowStatus=EnumFlowStatus.PASS;flowInstanceId="old-flow";attachmentIds="old-approval-file"
            }
            CollectionChangeDao.new("old-applied") {orgId="museum";collectionId="object";reason="原信息变更";
                before=CollectionCatalogContentVo(formalName="原名");content=CollectionCatalogContentVo(formalName="新名");baseline=before;
                paperLedgerRef="原自由文本引用";flowStatus=EnumFlowStatus.PASS}
            CollectionPermanentArchiveDao.new("original") {orgId="museum";bizType="LEGACY_CASE";bizId="case";phase="ORIGINAL";snapshot=originalArchive;digest=CollectionCareEvidence.digest(originalArchive)}
        }
        r.db {
            added.forEach { (table,columns) -> columns.forEach { column -> exec("ALTER TABLE ${quoted(table.tableName)} DROP COLUMN ${quoted(column.name)}") } }
            SchemaUtils.drop(InstitutionSuccessionRecords,CollectionTransferNotices,CollectionTransferTerminations)
            currentDialectMetadata.resetCaches()
        }
    }
    private fun oldValues(r:Runtime):Map<String,List<List<String?>>> = (added.keys+CollectionPermanentArchives).associate { table ->
        val columns=table.columns.filter {it !in added[table].orEmpty()}
        val rows=r.source.connection.use { c -> c.createStatement().use { statement ->
            statement.executeQuery("SELECT ${columns.joinToString { quoted(it.name) }} FROM ${quoted(table.tableName)} ORDER BY id").use { rs ->
                buildList {while(rs.next())add(columns.indices.map {rs.getString(it+1)})}
            }
        } }
        table.tableName to rows
    }

    @Test fun `actual installer upgrades legacy columns idempotently without manufacturing statutory facts`()=fixture { r ->
        legacy(r)
        val before=oldValues(r)
        assertEquals(EnumDeploymentCheckState.FAIL,CollectionRegulationSchemaDiagnosticCheck(r.source,r.install).check().state)
        repeat(2) { assertTrue(r.service.updateTable()) }
        assertEquals(EnumDeploymentCheckState.PASS,CollectionRegulationSchemaDiagnosticCheck(r.source,r.install).check().state)
        assertEquals(before,oldValues(r))
        r.db {
            val collection=CollectionDao["object"];assertNull(collection.nature);assertFalse(collection.inventoryHold)
            assertNull(WarehouseDao["store"].staffing)
            assertNull(WarehouseLogDao["log"].diary);assertNull(WarehouseLogDao["log"].entries)
            val visit=WarehouseEntryApplicationDao["visit"]
            assertNull(visit.dossier);assertNull(visit.baseline);assertNull(visit.entryRecord);assertNull(visit.exitRecord)
            assertEquals(at,visit.actualEntryAt);assertEquals(EnumFlowStatus.PASS,visit.flowStatus)
            val change=CollectionChangeDao["old-applied"]
            assertEquals("原自由文本引用",change.paperLedgerRef)
            assertNull(change.paperLedgerBookId);assertNull(change.paperLedgerPageNo)
            assertEquals(originalArchive,CollectionPermanentArchiveDao["original"].snapshot)
            assertEquals(1L,CollectionPermanentArchiveDao.count());assertEquals(0L,InstitutionSuccessionRecordDao.count())
            assertEquals(0L,CollectionTransferNoticeDao.count())
            assertEquals(0L,CollectionTransferTerminationDao.count())
        }
    }

    @Test fun `legacy organization gets a usable transactional location mutex after upgrade`()=fixture { r ->
        legacy(r);assertTrue(r.service.updateTable())
        fun version()=r.source.connection.use { c -> c.createStatement().use { s -> s.executeQuery("SELECT location_mutation_version FROM col_org WHERE id='museum'").use {rs->assertTrue(rs.next());rs.getLong(1)} } }
        assertEquals(0L,version())
        r.db { CollectionLocationMutation.reference("museum","store") }
        assertEquals(1L,version())
        assertThrows(IllegalStateException::class.java) { r.db { CollectionLocationMutation.reference("museum","store");error("rollback location transaction") } }
        assertEquals(1L,version())
        r.db {assertEquals("原收藏单位",CollectionOrgDao["museum"].name);assertEquals("原保管库",WarehouseDao["store"].name)}
    }

    @Test fun `complete neighboring schema cannot conceal missing target columns`()=fixture { r ->
        legacy(r)
        val decoy="reg_decoy_"+UUID.randomUUID().toString().replace("-","")
        r.source.connection.use { c -> c.createStatement().use { s ->
            s.execute("CREATE SCHEMA $decoy")
            s.execute("CREATE TABLE $decoy.col_org (LIKE col_org INCLUDING ALL)")
            s.execute("ALTER TABLE $decoy.col_org ADD COLUMN location_mutation_version BIGINT NOT NULL DEFAULT 0")
        } }
        try {
            val before=oldValues(r)
            val result=CollectionModuleSchemaCheck.check(r.source,setOf(CollectionOrgs),"目标收藏单位")
            assertEquals(EnumDeploymentCheckState.FAIL,result.state)
            assertTrue(result.safeFacts.single {it.name=="missingColumns"}.value.contains("col_org.location_mutation_version"))
            assertEquals(before,oldValues(r))
        } finally {r.source.connection.use { c -> c.createStatement().use {it.execute("DROP SCHEMA $decoy CASCADE")} } }
    }

    @Test fun `unique keys in a neighboring schema cannot cover a missing target key`()=fixture { r ->
        legacy(r);assertTrue(r.service.updateTable())
        val index=CollectionOrgs.indices.single {it.columns==listOf(CollectionOrgs.code)}.indexName
        val decoy="key_decoy_"+UUID.randomUUID().toString().replace("-","")
        r.db {exec("ALTER TABLE col_org DROP CONSTRAINT ${quoted(index)}")}
        r.source.connection.use {c->c.createStatement().use {s->
            s.execute("CREATE SCHEMA $decoy")
            s.execute("CREATE TABLE $decoy.col_org (LIKE col_org INCLUDING ALL)")
            s.execute("CREATE UNIQUE INDEX ${quoted(index)} ON $decoy.col_org(code)")
        } }
        try {
            val result=CollectionModuleSchemaCheck.check(r.source,setOf(CollectionOrgs),"目标收藏单位")
            assertEquals(EnumDeploymentCheckState.FAIL,result.state)
            assertEquals("0",result.safeFacts.single {it.name=="missingColumnCount"}.value)
            assertEquals("col_org(code)",result.safeFacts.single {it.name=="missingUniqueKeys"}.value)
        } finally {r.source.connection.use {c->c.createStatement().use {it.execute("DROP SCHEMA $decoy CASCADE")}}}
    }

    @Test fun `failed concurrent unique index is not treated as an enforced key`()=fixture { r ->
        legacy(r);assertTrue(r.service.updateTable())
        val index=CollectionOrgs.indices.single {it.columns==listOf(CollectionOrgs.code)}.indexName
        r.db {
            exec("ALTER TABLE col_org DROP CONSTRAINT ${quoted(index)}")
            exec("INSERT INTO col_org SELECT (jsonb_populate_record(NULL::col_org, to_jsonb(o) || '{\"id\":\"duplicate\"}'::jsonb)).* FROM col_org o WHERE id='museum'")
        }
        r.source.connection.use {c->c.createStatement().use {statement->
            assertThrows(java.sql.SQLException::class.java) {statement.execute("CREATE UNIQUE INDEX CONCURRENTLY invalid_unique_candidate ON col_org(code)")}
            statement.executeQuery("SELECT x.indisvalid FROM pg_catalog.pg_index x JOIN pg_catalog.pg_class i ON i.oid=x.indexrelid WHERE i.oid='invalid_unique_candidate'::regclass").use {rows->
                assertTrue(rows.next());assertFalse(rows.getBoolean(1))
            }
        } }
        val result=CollectionModuleSchemaCheck.check(r.source,setOf(CollectionOrgs),"目标收藏单位")
        assertEquals(EnumDeploymentCheckState.FAIL,result.state)
        assertTrue(result.safeFacts.single {it.name=="missingUniqueKeys"}.value.contains("col_org(code)"))
        r.db {assertEquals(2L,CollectionOrgDao.count())}
    }

    @Test fun `duplicate legacy organization codes report failed upgrade and support a later explicit repair retry`()=fixture { r ->
        legacy(r)
        val index=CollectionOrgs.indices.single {it.columns==listOf(CollectionOrgs.code)}.indexName
        r.db {
            exec("ALTER TABLE col_org DROP CONSTRAINT ${quoted(index)}")
            exec("INSERT INTO col_org SELECT (jsonb_populate_record(NULL::col_org, to_jsonb(o) || '{\"id\":\"duplicate\"}'::jsonb)).* FROM col_org o WHERE id='museum'")
            currentDialectMetadata.resetCaches()
        }
        val before=oldValues(r)
        assertFalse(r.service.updateTable())
        assertEquals(before,oldValues(r))
        assertEquals(EnumDeploymentCheckState.FAIL,CollectionRegulationSchemaDiagnosticCheck(r.source,r.install).check().state)
        // Only repair the synthetic conflict. The installer must neither delete nor renumber real data automatically.
        r.db {exec("UPDATE col_org SET code='M2' WHERE id='duplicate'");currentDialectMetadata.resetCaches()}
        assertTrue(r.service.updateTable())
        assertEquals(EnumDeploymentCheckState.PASS,CollectionRegulationSchemaDiagnosticCheck(r.source,r.install).check().state)
        r.db {assertEquals("M1",CollectionOrgDao["museum"].code);assertEquals("M2",CollectionOrgDao["duplicate"].code);assertEquals("000001",CollectionDao["object"].totalRegNo)}
    }
    @Test fun `missing primary key is not covered by a unique index or neighboring schema`()=fixture {r->
        assertTrue(r.service.updateTable())
        val neighbor="reg_primary_neighbor_"+UUID.randomUUID().toString().replace("-","")
        r.source.connection.use {c->c.createStatement().use {s->
            s.execute("CREATE SCHEMA $neighbor")
            try {
                s.execute("CREATE TABLE $neighbor.col_org (id VARCHAR(32) PRIMARY KEY)")
                val primary=c.metaData.getPrimaryKeys(c.catalog,c.schema,"col_org").use {rs->assertTrue(rs.next());rs.getString("PK_NAME")}
                s.execute("ALTER TABLE col_org DROP CONSTRAINT ${quoted(primary)}")
                s.execute("CREATE UNIQUE INDEX replacement_unique_id ON col_org(id)")
                val result=CollectionModuleSchemaCheck.check(r.source,r.install.initTables(),"藏品管理")
                assertEquals(EnumDeploymentCheckState.FAIL,result.state)
                assertEquals("col_org(id)",result.safeFacts.single {it.name=="missingPrimaryKeys"}.value)
                s.execute("ALTER TABLE col_org ADD PRIMARY KEY(id)")
                assertEquals(EnumDeploymentCheckState.PASS,CollectionModuleSchemaCheck.check(r.source,r.install.initTables(),"藏品管理").state)
            } finally {s.execute("DROP SCHEMA $neighbor CASCADE")}
        }}
    }
    @Test fun `nullable required column is reported without inventing historical values`()=fixture {r->
        assertTrue(r.service.updateTable())
        r.db {CollectionOrgDao.new("museum") {code="M1";name="原收藏单位"}}
        r.db {exec("ALTER TABLE col_org ALTER COLUMN name DROP NOT NULL");exec("UPDATE col_org SET name=NULL WHERE id='museum'")}
        val before=oldValues(r)
        val result=CollectionModuleSchemaCheck.check(r.source,r.install.initTables(),"藏品管理")
        assertEquals(EnumDeploymentCheckState.FAIL,result.state)
        assertEquals("col_org.name",result.safeFacts.single {it.name=="missingNotNullColumns"}.value)
        assertEquals(before,oldValues(r))
        r.db {exec("UPDATE col_org SET name='原收藏单位' WHERE id='museum'");exec("ALTER TABLE col_org ALTER COLUMN name SET NOT NULL")}
        assertEquals(EnumDeploymentCheckState.PASS,CollectionModuleSchemaCheck.check(r.source,r.install.initTables(),"藏品管理").state)
    }

}
