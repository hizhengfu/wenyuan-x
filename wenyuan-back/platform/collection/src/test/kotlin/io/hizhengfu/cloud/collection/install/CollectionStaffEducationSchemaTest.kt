package io.hizhengfu.cloud.collection.install

import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.diagnostic.CollectionModuleSchemaCheck
import io.hizhengfu.cloud.sys.diagnostic.domain.EnumDeploymentCheckState
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.vendors.currentDialectMetadata
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.sqlite.SQLiteDataSource
import java.nio.file.Path

class CollectionStaffEducationSchemaTest {
    @TempDir lateinit var dir:Path
    @Test fun `education schema is installed without inventing training records or changing existing warehouse data`() {
        val source=SQLiteDataSource().apply {url="jdbc:sqlite:${dir.resolve("education-upgrade.db")}"};val db=Database.connect(source)
        val install=CollectionInstall("multi");val added=setOf(CollectionStaffEducations,CollectionStaffEducationVersions,CollectionStaffEducationFileAccesses)
        assertTrue(install.initTables().containsAll(added))
        transaction(db){SchemaUtils.create(Warehouses);WarehouseDao.new("original"){orgId="museum";name="原实际库房"}}
        val before=transaction(db){Warehouses.selectAll().map {r->Warehouses.columns.map {r[it]}}}
        assertEquals(EnumDeploymentCheckState.FAIL,CollectionModuleSchemaCheck.check(source,added,"岗位教育").state)
        repeat(2) {
            transaction(db){currentDialectMetadata.resetCaches();SchemaUtils.createMissingTablesAndColumns(*install.initTables().toTypedArray())}
            assertEquals(EnumDeploymentCheckState.PASS,CollectionModuleSchemaCheck.check(source,added,"岗位教育").state)
            transaction(db){
                assertEquals(before,Warehouses.selectAll().map {r->Warehouses.columns.map {r[it]}})
                added.forEach {assertEquals(0L,it.selectAll().count())}
            }
        }
        assertTrue(install.initMenus().any {it.path=="/collection/staff-education"})
        assertTrue(install.initPermissions().any {it.code=="COLLECTION_STAFF_EDUCATION_MANAGE"})
    }
}
