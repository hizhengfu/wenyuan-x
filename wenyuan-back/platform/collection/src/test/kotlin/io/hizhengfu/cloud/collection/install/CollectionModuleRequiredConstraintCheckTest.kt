package io.hizhengfu.cloud.collection.install

import io.hizhengfu.cloud.collection.diagnostic.CollectionModuleSchemaCheck
import io.hizhengfu.cloud.sys.diagnostic.domain.EnumDeploymentCheckState
import org.jetbrains.exposed.v1.core.Table
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.sqlite.SQLiteDataSource
import org.mockito.Mockito.*
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.ResultSet
import java.sql.SQLFeatureNotSupportedException
import javax.sql.DataSource
import java.nio.file.Path

class CollectionModuleRequiredConstraintCheckTest {
    @TempDir lateinit var dir:Path
    private object Required:Table("col_required_probe") {
        val id=varchar("id",32)
        val name=varchar("name",100)
        val note=text("note").nullable()
        override val primaryKey=PrimaryKey(id)
    }
    @ParameterizedTest @ValueSource(strings=["complete","missing-primary","unique-only","wrong-primary","nullable-name","nullable-primary"])
    fun `inspect actual constraints without changing legacy rows`(kind:String) {
        val source=SQLiteDataSource().apply {url="jdbc:sqlite:${dir.resolve("required.db")}"}
        source.connection.use {c->c.createStatement().use {s->
            val id=when(kind) {
                "missing-primary","wrong-primary"->"id TEXT NOT NULL"
                "unique-only"->"id TEXT NOT NULL UNIQUE"
                "nullable-primary"->"id TEXT PRIMARY KEY"
                else->"id TEXT NOT NULL PRIMARY KEY"
            }
            val name=when(kind) {
                "nullable-name"->"name TEXT"
                "wrong-primary"->"name TEXT NOT NULL PRIMARY KEY"
                else->"name TEXT NOT NULL"
            }
            s.execute("CREATE TABLE col_required_probe ($id, $name, note TEXT)")
            s.execute("INSERT INTO col_required_probe VALUES ('old','原始记录',NULL)")
            if(kind=="nullable-primary")s.execute("INSERT INTO col_required_probe VALUES (NULL,'历史空标识',NULL)")
            if(kind=="nullable-name")s.execute("INSERT INTO col_required_probe VALUES ('empty',NULL,NULL)")
        }}
        fun snapshot()=source.connection.use {c->c.createStatement().use {s->s.executeQuery("SELECT id,name,note FROM col_required_probe ORDER BY rowid").use {rs->buildList {while(rs.next())add((1..3).map {rs.getString(it)})}}}}
        val before=snapshot()
        val result=CollectionModuleSchemaCheck.check(source,setOf(Required),"约束测试")
        assertEquals(if(kind=="complete")EnumDeploymentCheckState.PASS else EnumDeploymentCheckState.FAIL,result.state,result.toString())
        assertEquals("0",result.safeFacts.single {it.name=="missingColumnCount"}.value)
        if(kind in setOf("missing-primary","unique-only","wrong-primary"))assertEquals("1",result.safeFacts.single {it.name=="missingPrimaryKeyCount"}.value)
        if(kind.startsWith("nullable"))assertEquals("1",result.safeFacts.single {it.name=="missingNotNullCount"}.value)
        assertEquals(before,snapshot())
    }
    @ParameterizedTest @ValueSource(strings=["unknown-nullability","null-nullability","unsupported-primary-metadata"])
    fun `unavailable required metadata cannot pass`(kind:String) {
        val source=mock(DataSource::class.java)
        val connection=mock(Connection::class.java)
        val metadata=mock(DatabaseMetaData::class.java)
        val columns=mock(ResultSet::class.java)
        val primary=mock(ResultSet::class.java)
        `when`(source.connection).thenReturn(connection)
        `when`(connection.metaData).thenReturn(metadata)
        `when`(metadata.getColumns(isNull(),isNull(),anyString(),eq("%"))).thenReturn(columns)
        val names=listOf("id","name","note");var cursor=-1
        `when`(columns.next()).thenAnswer {++cursor<names.size}
        `when`(columns.getString("TABLE_NAME")).thenReturn(Required.tableName)
        `when`(columns.getString("COLUMN_NAME")).thenAnswer {names[cursor]}
        `when`(columns.getInt("NULLABLE")).thenReturn(if(kind=="unknown-nullability")DatabaseMetaData.columnNullableUnknown else DatabaseMetaData.columnNoNulls)
        `when`(columns.wasNull()).thenReturn(kind=="null-nullability")
        if(kind=="unsupported-primary-metadata") {
            `when`(metadata.getPrimaryKeys(isNull(),isNull(),anyString())).thenThrow(SQLFeatureNotSupportedException())
        } else {
            `when`(metadata.getPrimaryKeys(isNull(),isNull(),anyString())).thenReturn(primary)
            `when`(primary.next()).thenReturn(true,false)
            `when`(primary.getString("TABLE_NAME")).thenReturn(Required.tableName)
            `when`(primary.getString("COLUMN_NAME")).thenReturn("id")
        }
        val result=CollectionModuleSchemaCheck.check(source,setOf(Required),"约束测试")
        assertEquals(EnumDeploymentCheckState.ERROR,result.state)
        assertEquals("COLLECTION_REQUIRED_CONSTRAINT_METADATA_UNVERIFIED",result.reasonCode)
        verify(connection,never()).createStatement()
    }

}
