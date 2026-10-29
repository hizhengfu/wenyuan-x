package io.hizhengfu.cloud.collection.install

import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.diagnostic.*
import io.hizhengfu.cloud.collection.domain.EnumCollectionStatus
import io.hizhengfu.cloud.collection.domain.EnumCollectionNature
import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.service.CollectionCareEvidence
import io.hizhengfu.cloud.collection.domain.CollectionCatalogContentVo
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import org.jetbrains.exposed.v1.jdbc.select
import io.hizhengfu.cloud.sys.diagnostic.domain.EnumDeploymentCheckState
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.vendors.currentDialectMetadata
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.*
import org.sqlite.SQLiteDataSource
import java.nio.file.Path
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.ResultSet
import javax.sql.DataSource

class CollectionRegulationSchemaMigrationTest {
    @TempDir lateinit var dir:Path
    private val install=CollectionInstall("multi")

    @Test fun `installed schema supports transfer notice reads after repeated upgrades`() {
        val db=Database.connect(SQLiteDataSource().apply {url="jdbc:sqlite:${dir.resolve("transfer-notice.db")}"})
        repeat(2) {
            transaction(db) {
                SchemaUtils.createMissingTablesAndColumns(*install.initTables().toTypedArray())
                assertEquals(0L,CollectionTransferNoticeDao.count())
            }
        }
    }

    @Test fun `appended scientific fossil enum preserves original database ordinals and serialized archives`() {
        val source=SQLiteDataSource().apply {url="jdbc:sqlite:${dir.resolve("nature-enum.db")}"};val db=Database.connect(source)
        val original="""{"nature":"OTHER","objectKind":"SCIENTIFIC_FOSSIL","originalEvidence":"保留升级前原始分类"}"""
        transaction(db) {
            SchemaUtils.create(Collections,CollectionPermanentArchives)
            listOf("relic","other","unknown").forEach {key->CollectionDao.new(key){orgId="museum";name="原藏品";totalRegNo=key}}
        }
        transaction(db) {
            exec("UPDATE col_collection SET collection_nature=0 WHERE id='relic'")
            exec("UPDATE col_collection SET collection_nature=1 WHERE id='other'")
            CollectionPermanentArchiveDao.new("old"){orgId="museum";bizType="COLLECTION_LOAN";bizId="old-loan";phase="ORIGINAL";snapshot=original;digest=CollectionCareEvidence.digest(original)}
        }
        repeat(2){transaction(db){currentDialectMetadata.resetCaches();SchemaUtils.createMissingTablesAndColumns(Collections,CollectionPermanentArchives)}}
        transaction(db) {
            assertEquals(EnumCollectionNature.RELIC,CollectionDao["relic"].nature)
            assertEquals(EnumCollectionNature.OTHER,CollectionDao["other"].nature)
            assertNull(CollectionDao["unknown"].nature)
            assertEquals(original,CollectionPermanentArchiveDao["old"].snapshot)
            assertEquals(CollectionCareEvidence.digest(original),CollectionPermanentArchiveDao["old"].digest)
            CollectionDao.new("new-scientific"){orgId="museum";name="已正式核实的新类别";totalRegNo="SCI-1";nature=EnumCollectionNature.SCIENTIFIC_FOSSIL}
            CollectionDao["new-scientific"].flush()
            exec("SELECT collection_nature FROM col_collection WHERE id='new-scientific'"){rs->assertTrue(rs.next());assertEquals(2,rs.getInt(1))}
            val frozen=CollectionCatalogContentVo(nature=EnumCollectionNature.SCIENTIFIC_FOSSIL)
            assertEquals(frozen,JsonUtils.fromJson(JsonUtils.toJson(frozen)!!,CollectionCatalogContentVo::class.java))
        }
    }

    @Test fun `legacy change requests retain all original fields and do not acquire invented correction links`() {
        val source=SQLiteDataSource().apply {url="jdbc:sqlite:${dir.resolve("old-change.db")}"};val db=Database.connect(source)
        val added=setOf(CollectionChanges.previousChangeId,CollectionChanges.correctionRequestKey,CollectionChanges.correction)
        val originalColumns=CollectionChanges.columns.filter {it !in added}
        lateinit var before:List<List<Any?>>
        transaction(db){
            SchemaUtils.create(CollectionChanges)
            repeat(2){n->CollectionChangeDao.new("old-$n"){orgId="museum";collectionId="object-$n";reason="原申请事由";
                this.before=CollectionCatalogContentVo(formalName="原定名");content=CollectionCatalogContentVo(formalName="原拟定名");baseline=this.before;
                keeperUserId="原保管人";leaderUserId="原负责人";flowStatus=EnumFlowStatus.REJECT;flowInstanceId="old-flow-$n"}}
        }
        transaction(db){
            before=CollectionChanges.select(originalColumns).orderBy(CollectionChanges.id).map {r->originalColumns.map {r[it]}}
            val indexes=mutableListOf<String>()
            exec("PRAGMA index_list('col_change')"){rs->while(rs.next())indexes.add(rs.getString("name"))}
            indexes.forEach {index->
                var remove=false
                exec("PRAGMA index_info('"+index.replace("'","''")+"')"){rs->while(rs.next())if(rs.getString("name") in added.map {it.name})remove=true}
                if(remove)exec("DROP INDEX \""+index.replace("\"","\"\"")+"\"")
            }
            added.forEach {exec("ALTER TABLE col_change DROP COLUMN ${it.name}")}
        }
        repeat(2){transaction(db){currentDialectMetadata.resetCaches();SchemaUtils.createMissingTablesAndColumns(CollectionChanges)}}
        transaction(db){
            assertEquals(before,CollectionChanges.select(originalColumns).orderBy(CollectionChanges.id).map {r->originalColumns.map {r[it]}})
            repeat(2){n->val row=CollectionChangeDao["old-$n"];assertNull(row.previousChangeId);assertNull(row.correctionRequestKey);assertNull(row.correction)}
        }
    }
    @Test fun `legacy requests gain nullable set amendment without rewriting original fields or snapshots`() {
        val source=SQLiteDataSource().apply {url="jdbc:sqlite:${dir.resolve("old-set-amendment.db")}"};val db=Database.connect(source)
        val added=setOf(CollectionChanges.setAmendment)
        val originalColumns=CollectionChanges.columns.filter {it !in added}
        lateinit var before:List<List<Any?>>
        transaction(db){
            SchemaUtils.create(CollectionChanges)
            repeat(2){n->CollectionChangeDao.new("old-$n"){orgId="museum";collectionId="object-$n";reason="原申请事由";
                this.before=CollectionCatalogContentVo(formalName="原定名");content=CollectionCatalogContentVo(formalName="原拟定名");baseline=this.before;
                keeperUserId="原保管人";leaderUserId="原负责人";flowStatus=EnumFlowStatus.REJECT;flowInstanceId="old-flow-$n"}}
        }
        transaction(db){
            before=CollectionChanges.select(originalColumns).orderBy(CollectionChanges.id).map {r->originalColumns.map {r[it]}}
            val indexes=mutableListOf<String>()
            exec("PRAGMA index_list('col_change')"){rs->while(rs.next())indexes.add(rs.getString("name"))}
            indexes.forEach {index->
                var remove=false
                exec("PRAGMA index_info('"+index.replace("'","''")+"')"){rs->while(rs.next())if(rs.getString("name") in added.map {it.name})remove=true}
                if(remove)exec("DROP INDEX \""+index.replace("\"","\"\"")+"\"")
            }
            added.forEach {exec("ALTER TABLE col_change DROP COLUMN ${it.name}")}
        }
        repeat(2){transaction(db){currentDialectMetadata.resetCaches();SchemaUtils.createMissingTablesAndColumns(CollectionChanges)}}
        transaction(db){
            assertEquals(before,CollectionChanges.select(originalColumns).orderBy(CollectionChanges.id).map {r->originalColumns.map {r[it]}})
            repeat(2){n->val row=CollectionChangeDao["old-$n"];assertNull(row.setAmendment)}
        }
    }
    @Test fun `legacy requisitions keep all original values and do not acquire invented responsibility replacements`() {
        val source=SQLiteDataSource().apply {url="jdbc:sqlite:${dir.resolve("old-requisition.db")}"};val db=Database.connect(source)
        val added=setOf(Requisitions.supersededById,Requisitions.replacesId)
        val columns=Requisitions.columns.filter {it !in added}
        lateinit var before:List<List<Any?>>
        transaction(db){
            SchemaUtils.create(Requisitions)
            RequisitionDao.new("original"){orgId="museum";collectionIds="one,two";documentNo="原凭证";basis="原提用依据";flowStatus=EnumFlowStatus.PASS;flowInstanceId="original-flow"}
        }
        transaction(db){
            before=Requisitions.select(columns).orderBy(Requisitions.id).map {r->columns.map {r[it]}}
            added.forEach {exec("ALTER TABLE col_requisition DROP COLUMN ${it.name}")}
        }
        repeat(2){transaction(db){currentDialectMetadata.resetCaches();SchemaUtils.createMissingTablesAndColumns(Requisitions)}}
        transaction(db){
            assertEquals(before,Requisitions.select(columns).orderBy(Requisitions.id).map {r->columns.map {r[it]}})
            val row=RequisitionDao["original"];assertNull(row.supersededById);assertNull(row.replacesId)
        }
    }
    @Test fun `complete declared schema passes and read only inspection identifies missing new tables and columns`() {
        val source=SQLiteDataSource().apply {url="jdbc:sqlite:${dir.resolve("schema.db")}"};val db=Database.connect(source)
        val check=CollectionRegulationSchemaDiagnosticCheck(source,install)
        assertEquals(EnumDeploymentCheckState.FAIL,check.check().state)
        transaction(db){SchemaUtils.create(*install.initTables().toTypedArray())}
        assertEquals(EnumDeploymentCheckState.PASS,check.check().state)
        transaction(db){exec("ALTER TABLE col_collection DROP COLUMN collection_nature");SchemaUtils.drop(InstitutionSuccessionRecords)}
        val missing=check.check();assertEquals(EnumDeploymentCheckState.FAIL,missing.state)
        assertTrue(missing.safeFacts.single {it.name=="missingTables"}.value.contains(InstitutionSuccessionRecords.tableName))
        assertTrue(missing.safeFacts.single {it.name=="missingColumns"}.value.contains("col_collection.collection_nature"))
        assertEquals(missing,check.check())
    }
    @Test fun `generic full declaration migration keeps old ledger data without inventing nature signatures or permanent evidence`() {
        val source=SQLiteDataSource().apply {url="jdbc:sqlite:${dir.resolve("legacy.db")}"};val db=Database.connect(source)
        transaction(db){
            SchemaUtils.create(Collections)
            CollectionDao.new("old-collection"){orgId="museum";name="原始藏品";totalRegNo="000001";lifeStatus=EnumCollectionStatus.IN_STORE}
            exec("ALTER TABLE col_collection DROP COLUMN collection_nature");exec("ALTER TABLE col_collection DROP COLUMN inventory_hold")
        }
        repeat(2){transaction(db){currentDialectMetadata.resetCaches();SchemaUtils.createMissingTablesAndColumns(*install.initTables().toTypedArray())}}
        assertEquals(EnumDeploymentCheckState.PASS,CollectionRegulationSchemaDiagnosticCheck(source,install).check().state)
        transaction(db){
            val r=CollectionDao["old-collection"];assertEquals("000001",r.totalRegNo);assertEquals("原始藏品",r.name);assertEquals("museum",r.orgId)
            assertNull(r.nature);assertFalse(r.inventoryHold);assertEquals(EnumCollectionStatus.IN_STORE,r.lifeStatus)
            assertEquals(0,CollectionPermanentArchiveDao.all().count());assertEquals(0,InstitutionSuccessionCaseDao.all().count())
        }
    }
    @Test fun `other schema columns never mask missing fields in current schema`() {
        val source=mock(DataSource::class.java);val connection=mock(Connection::class.java);val metadata=mock(DatabaseMetaData::class.java)
        `when`(source.connection).thenReturn(connection);`when`(connection.metaData).thenReturn(metadata);`when`(connection.schema).thenReturn("museum")
        `when`(metadata.supportsSchemasInTableDefinitions()).thenReturn(true)
        `when`(metadata.getColumns(isNull(),eq("museum"),anyString(),eq("%"))).thenAnswer {
            val rows=mock(ResultSet::class.java);val names=Collections.columns.map {it.name};var index=-1
            `when`(rows.next()).thenAnswer {++index<names.size}
            `when`(rows.getString("TABLE_NAME")).thenReturn(Collections.tableName)
            `when`(rows.getString("TABLE_SCHEM")).thenReturn("other_museum")
            `when`(rows.getString("COLUMN_NAME")).thenAnswer {names[index]};rows
        }
        val result=CollectionModuleSchemaCheck.check(source,setOf(Collections),"测试")
        assertEquals(EnumDeploymentCheckState.FAIL,result.state)
        assertEquals("1",result.safeFacts.single {it.name=="missingTableCount"}.value)
        verify(connection,never()).createStatement()
    }
    @Test fun `unknown schema cannot produce a successful deployment check`() {
        val source=mock(DataSource::class.java);val connection=mock(Connection::class.java);val metadata=mock(DatabaseMetaData::class.java)
        `when`(source.connection).thenReturn(connection);`when`(connection.metaData).thenReturn(metadata);`when`(metadata.supportsSchemasInTableDefinitions()).thenReturn(true)
        val result=CollectionModuleSchemaCheck.check(source,setOf(Collections),"测试")
        assertEquals(EnumDeploymentCheckState.ERROR,result.state);assertEquals("COLLECTION_SCHEMA_SCOPE_UNKNOWN",result.reasonCode)
    }
}
