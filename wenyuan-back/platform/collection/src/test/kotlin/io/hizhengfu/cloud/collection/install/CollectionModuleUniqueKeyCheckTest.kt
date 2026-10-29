package io.hizhengfu.cloud.collection.install

import io.hizhengfu.cloud.collection.diagnostic.CollectionModuleSchemaCheck
import io.hizhengfu.cloud.sys.diagnostic.domain.EnumDeploymentCheckState
import org.jetbrains.exposed.v1.core.Table
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.sqlite.SQLiteDataSource
import java.nio.file.Path
import java.sql.SQLException

class CollectionModuleUniqueKeyCheckTest {
    @TempDir lateinit var dir:Path
    private object Keys:Table("col_upgrade_unique_probe") {
        val org=varchar("org_id",32)
        val number=varchar("register_no",32)
        val variant=varchar("variant",32)
        init {uniqueIndex("declared_unique_key",org,number)}
    }
    private fun source()=SQLiteDataSource().apply {url="jdbc:sqlite:${dir.resolve("unique.db")}"}.also {source->
        source.connection.use { c->c.createStatement().use {it.execute("CREATE TABLE col_upgrade_unique_probe (org_id TEXT NOT NULL, register_no TEXT NOT NULL, variant TEXT NOT NULL)")} }
    }
    @ParameterizedTest @ValueSource(strings=["missing","non-unique","partial","superset"])
    fun `fields alone and weaker indexes never establish required uniqueness`(kind:String) {
        val source=source()
        source.connection.use {c->c.createStatement().use {s->
            when(kind) {
                "non-unique"->s.execute("CREATE INDEX candidate ON col_upgrade_unique_probe(org_id,register_no)")
                "partial"->s.execute("CREATE UNIQUE INDEX candidate ON col_upgrade_unique_probe(org_id,register_no) WHERE variant='active'")
                "superset"->s.execute("CREATE UNIQUE INDEX candidate ON col_upgrade_unique_probe(org_id,register_no,variant)")
            }
            s.execute("INSERT INTO col_upgrade_unique_probe VALUES ('museum','0001','old-a'),('museum','0001','old-b')")
        } }
        val result=CollectionModuleSchemaCheck.check(source,setOf(Keys),"测试账册")
        assertEquals(EnumDeploymentCheckState.FAIL,result.state)
        assertEquals("0",result.safeFacts.single {it.name=="missingColumnCount"}.value)
        assertEquals("1",result.safeFacts.single {it.name=="missingUniqueKeyCount"}.value)
        source.connection.use {c->c.createStatement().use {s->s.executeQuery("SELECT count(*) FROM col_upgrade_unique_probe").use {rs->assertTrue(rs.next());assertEquals(2,rs.getInt(1))}}}
    }
    @Test fun `renamed unique key with reversed column order still proves the same column combination`() {
        val source=source()
        source.connection.use {c->c.createStatement().use {s->
            s.execute("CREATE UNIQUE INDEX museum_chosen_name ON col_upgrade_unique_probe(register_no,org_id)")
            s.execute("INSERT INTO col_upgrade_unique_probe VALUES ('museum','0001','old-a')")
            assertThrows(SQLException::class.java){s.execute("INSERT INTO col_upgrade_unique_probe VALUES ('museum','0001','old-b')")}
        } }
        val result=CollectionModuleSchemaCheck.check(source,setOf(Keys),"测试账册")
        assertEquals(EnumDeploymentCheckState.PASS,result.state)
        assertEquals("0",result.safeFacts.single {it.name=="missingUniqueKeyCount"}.value)
        assertEquals("1",result.safeFacts.single {it.name=="declaredUniqueKeyCount"}.value)
    }
}
