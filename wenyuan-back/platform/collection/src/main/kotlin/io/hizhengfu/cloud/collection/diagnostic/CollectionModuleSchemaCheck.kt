package io.hizhengfu.cloud.collection.diagnostic

import io.hizhengfu.cloud.collection.install.CollectionInstall
import io.hizhengfu.cloud.sys.diagnostic.domain.*
import io.hizhengfu.cloud.sys.diagnostic.service.DeploymentDiagnosticCheck
import org.jetbrains.exposed.v1.core.Table
import org.springframework.stereotype.Component
import java.util.Locale
import javax.sql.DataSource

/** 只读核对当前命名空间的声明表、列、主键、非空及普通唯一键元数据；不执行 DDL，不据此认定类型、检查约束或业务验收通过。 */
object CollectionModuleSchemaCheck {
    fun check(dataSource:DataSource,tables:Set<Table>,feature:String):DeploymentCheckOutcome = dataSource.connection.use { check(it,tables,feature) }

    /** 调用方拥有连接；允许依赖结构与类型检查使用同一个实际命名空间。 */
    internal fun check(connection:java.sql.Connection,tables:Set<Table>,feature:String):DeploymentCheckOutcome = run {
        runCatching {connection.isReadOnly=true}
        val metadata=connection.metaData
        val catalog=runCatching {connection.catalog}.getOrNull()?.takeIf(String::isNotBlank)
        val schema=runCatching {connection.schema}.getOrNull()?.takeIf(String::isNotBlank)
        if((metadata.supportsSchemasInTableDefinitions() && schema==null) || (metadata.supportsCatalogsInTableDefinitions() && catalog==null)) {
            return@run DeploymentCheckOutcome(EnumDeploymentCheckState.ERROR,"无法确定当前数据库命名空间，不能使用其他库或模式的同名表作为升级依据","COLLECTION_SCHEMA_SCOPE_UNKNOWN")
        }
        val missingTables=mutableListOf<String>();val missingColumns=mutableListOf<String>();val ambiguous=mutableListOf<String>()
        val missingUniqueKeys=mutableListOf<String>();val unsupportedUniqueKeys=mutableListOf<String>();val uniqueMetadataErrors=mutableListOf<String>()
        val declaredUniqueKeys=tables.sumOf {table->table.indices.count {it.unique}}
        val missingPrimaryKeys=mutableListOf<String>();val primaryMetadataErrors=mutableListOf<String>()
        val missingNotNull=mutableListOf<String>();val unknownNullability=mutableListOf<String>()
        val declaredPrimaryKeys=tables.count {it.primaryKey!=null}
        val declaredNotNull=tables.sumOf {table->table.columns.count {!it.columnType.nullable}}
        val escape=metadata.searchStringEscape.orEmpty()
        fun pattern(value:String):String {
            if(escape.isEmpty())return value
            return value.replace(escape,escape+escape).replace("_",escape+"_").replace("%",escape+"%")
        }
        fun matches(expected:String?,actual:String?)=expected==null || expected==actual
        for(table in tables.sortedBy {it.tableName}) {
            val name=table.tableName
            val queryName=when {metadata.storesUpperCaseIdentifiers()->name.uppercase(Locale.ROOT);metadata.storesLowerCaseIdentifiers()->name.lowercase(Locale.ROOT);else->name}
            val owners=linkedMapOf<Pair<String?,String?>,MutableMap<String,Int?>>()
            metadata.getColumns(catalog,schema?.let(::pattern),pattern(queryName),"%").use {rows->
                while(rows.next()) {
                    if(!rows.getString("TABLE_NAME").equals(queryName,ignoreCase=!metadata.supportsMixedCaseIdentifiers()))continue
                    val actualCatalog=rows.getString("TABLE_CAT")?.takeIf(String::isNotBlank)
                    val actualSchema=rows.getString("TABLE_SCHEM")?.takeIf(String::isNotBlank)
                    if(!matches(catalog,actualCatalog) || !matches(schema,actualSchema))continue
                    val column=rows.getString("COLUMN_NAME").lowercase(Locale.ROOT)
                    val nullable=rows.getInt("NULLABLE").let {if(rows.wasNull())null else it}
                    owners.getOrPut(actualCatalog to actualSchema) {linkedMapOf()}[column]=nullable
                }
            }
            if(owners.size>1)ambiguous+=name
            else {
                val columns=owners.values.singleOrNull().orEmpty()
                if(columns.isEmpty())missingTables+=name
                else {
                    val absent=table.columns.filter {it.name.lowercase(Locale.ROOT) !in columns}
                    absent.forEach {missingColumns+="$name.${it.name}"}
                    table.columns.filter {!it.columnType.nullable && it !in absent}.forEach {column->
                        when(columns[column.name.lowercase(Locale.ROOT)]) {
                            java.sql.DatabaseMetaData.columnNoNulls->Unit
                            java.sql.DatabaseMetaData.columnNullable->missingNotNull+="$name.${column.name}"
                            else->unknownNullability+="$name.${column.name}"
                        }
                    }
                    val primary=table.primaryKey
                    if(absent.isEmpty() && primary!=null) {
                        try {
                            val keys=linkedMapOf<String?,MutableSet<String>>()
                            metadata.getPrimaryKeys(catalog,schema,queryName).use {rows->
                                while(rows.next()) {
                                    if(!rows.getString("TABLE_NAME").equals(queryName,ignoreCase=!metadata.supportsMixedCaseIdentifiers()))continue
                                    if(!matches(catalog,rows.getString("TABLE_CAT")?.takeIf(String::isNotBlank)) ||
                                        !matches(schema,rows.getString("TABLE_SCHEM")?.takeIf(String::isNotBlank)))continue
                                    val column=rows.getString("COLUMN_NAME")?.takeIf(String::isNotBlank) ?: continue
                                    keys.getOrPut(rows.getString("PK_NAME")) {linkedSetOf()}.add(column.lowercase(Locale.ROOT))
                                }
                            }
                            if(keys.size!=1 || keys.values.single()!=primary.columns.map {it.name.lowercase(Locale.ROOT)}.toSet())
                                missingPrimaryKeys+="$name(${primary.columns.joinToString {it.name}})"
                        } catch (_:java.sql.SQLException) {primaryMetadataErrors+=name}
                    }
                    val expected=table.indices.filter {it.unique}
                    if(absent.isEmpty() && expected.isNotEmpty()) {
                        data class UniqueKey(val columns:MutableSet<String> = linkedSetOf(),var usable:Boolean = true)
                        val actual=linkedMapOf<String,UniqueKey>()
                        try {
                            // SQLite JDBC 不提供条件索引谓词；PostgreSQL 还须排除并发创建失败留下的无效索引。
                            val unusableIndexNames=linkedSetOf<String>()
                            if(metadata.databaseProductName.equals("SQLite",ignoreCase=true)) {
                                connection.createStatement().use {statement->
                                    statement.executeQuery("PRAGMA index_list('"+queryName.replace("'","''")+"')").use {rows->
                                        while(rows.next())if(rows.getInt("partial")!=0)unusableIndexNames+=rows.getString("name")
                                    }
                                }
                            } else if(metadata.databaseProductName.equals("PostgreSQL",ignoreCase=true)) {
                                connection.prepareStatement("SELECT i.relname FROM pg_catalog.pg_index x JOIN pg_catalog.pg_class t ON t.oid=x.indrelid JOIN pg_catalog.pg_namespace n ON n.oid=t.relnamespace JOIN pg_catalog.pg_class i ON i.oid=x.indexrelid WHERE n.nspname=? AND t.relname=? AND (NOT x.indisvalid OR NOT x.indisready OR x.indpred IS NOT NULL OR x.indexprs IS NOT NULL)").use {statement->
                                    statement.setString(1,schema);statement.setString(2,queryName)
                                    statement.executeQuery().use {rows->while(rows.next())unusableIndexNames+=rows.getString(1)}
                                }
                            }
                            metadata.getIndexInfo(catalog,schema,queryName,true,false).use {rows->
                                while(rows.next()) {
                                    if(!rows.getString("TABLE_NAME").equals(queryName,ignoreCase=!metadata.supportsMixedCaseIdentifiers()))continue
                                    if(!matches(catalog,rows.getString("TABLE_CAT")?.takeIf(String::isNotBlank)) ||
                                        !matches(schema,rows.getString("TABLE_SCHEM")?.takeIf(String::isNotBlank)))continue
                                    val indexName=rows.getString("INDEX_NAME")?.takeIf(String::isNotBlank) ?: continue
                                    val key=actual.getOrPut(indexName) {UniqueKey(usable=indexName !in unusableIndexNames)}
                                    val nonUnique=rows.getBoolean("NON_UNIQUE")
                                    if(rows.wasNull() || nonUnique || !rows.getString("FILTER_CONDITION").isNullOrBlank())key.usable=false
                                    val column=rows.getString("COLUMN_NAME")?.takeIf(String::isNotBlank)
                                    if(column==null)key.usable=false else key.columns+=column.lowercase(Locale.ROOT)
                                }
                            }
                            expected.forEach {index->
                                val label="$name(${index.columns.joinToString {it.name}})"
                                if(index.filterCondition!=null || !index.functions.isNullOrEmpty())unsupportedUniqueKeys+=label
                                else if(actual.values.none {it.usable && it.columns==index.columns.map {c->c.name.lowercase(Locale.ROOT)}.toSet()})missingUniqueKeys+=label
                            }
                        } catch (_:java.sql.SQLException) {uniqueMetadataErrors+=name}
                    }
                }
            }
        }
        val facts=listOf(
            DeploymentSafeFact("declaredTableCount",tables.size.toString()),
            DeploymentSafeFact("missingTableCount",missingTables.size.toString()),DeploymentSafeFact("missingColumnCount",missingColumns.size.toString()),
            DeploymentSafeFact("missingTables",missingTables.take(100).joinToString()),DeploymentSafeFact("missingColumns",missingColumns.take(100).joinToString()),
            DeploymentSafeFact("ambiguousTables",ambiguous.take(100).joinToString()),
            DeploymentSafeFact("declaredUniqueKeyCount",declaredUniqueKeys.toString()),
            DeploymentSafeFact("missingUniqueKeyCount",missingUniqueKeys.size.toString()),
            DeploymentSafeFact("missingUniqueKeys",missingUniqueKeys.take(100).joinToString()),
            DeploymentSafeFact("unsupportedUniqueKeys",unsupportedUniqueKeys.take(100).joinToString()),
            DeploymentSafeFact("uniqueMetadataErrors",uniqueMetadataErrors.take(100).joinToString()),
            DeploymentSafeFact("declaredPrimaryKeyCount",declaredPrimaryKeys.toString()),
            DeploymentSafeFact("missingPrimaryKeyCount",missingPrimaryKeys.size.toString()),
            DeploymentSafeFact("missingPrimaryKeys",missingPrimaryKeys.take(100).joinToString()),
            DeploymentSafeFact("primaryKeyMetadataErrors",primaryMetadataErrors.take(100).joinToString()),
            DeploymentSafeFact("declaredNotNullCount",declaredNotNull.toString()),
            DeploymentSafeFact("missingNotNullCount",missingNotNull.size.toString()),
            DeploymentSafeFact("missingNotNullColumns",missingNotNull.take(100).joinToString()),
            DeploymentSafeFact("unknownNullabilityColumns",unknownNullability.take(100).joinToString()),
            DeploymentSafeFact("validationScope","TABLE_COLUMN_PRIMARY_KEY_NOT_NULL_AND_UNIQUE_KEY_METADATA"),
        )
        when {
            ambiguous.isNotEmpty()->DeploymentCheckOutcome(EnumDeploymentCheckState.ERROR,"$feature 同名表的命名空间不明确，不能合并其他模式的字段判为通过","COLLECTION_SCHEMA_SCOPE_AMBIGUOUS",facts)
            primaryMetadataErrors.isNotEmpty() || unknownNullability.isNotEmpty()->DeploymentCheckOutcome(EnumDeploymentCheckState.ERROR,"$feature 部分主键或非空约束无法通过当前驱动元数据核实，须完成专用约束核验","COLLECTION_REQUIRED_CONSTRAINT_METADATA_UNVERIFIED",facts)
            unsupportedUniqueKeys.isNotEmpty() || uniqueMetadataErrors.isNotEmpty()->DeploymentCheckOutcome(EnumDeploymentCheckState.ERROR,"$feature 部分唯一键无法通过当前驱动元数据核实，须完成专用约束核验","COLLECTION_UNIQUE_KEY_METADATA_UNVERIFIED",facts)
            missingTables.isEmpty() && missingColumns.isEmpty() && missingUniqueKeys.isEmpty() && missingPrimaryKeys.isEmpty() && missingNotNull.isEmpty()->DeploymentCheckOutcome(EnumDeploymentCheckState.PASS,"$feature 声明的 ${tables.size} 张表、字段、$declaredPrimaryKeys 个主键、$declaredNotNull 个非空字段及 $declaredUniqueKeys 个唯一键均可见；类型精度、外键及检查约束、历史数据和实际流程仍须验收",safeFacts=facts)
            else->DeploymentCheckOutcome(EnumDeploymentCheckState.FAIL,"$feature 缺失或不可见 ${missingTables.size} 张表、${missingColumns.size} 个字段、${missingPrimaryKeys.size} 个主键、${missingNotNull.size} 个非空约束、${missingUniqueKeys.size} 个唯一键","COLLECTION_MODULE_SCHEMA_INCOMPLETE",facts,
                listOf(DeploymentRemediation(EnumDeploymentRemediationType.DATABASE,"核对并完成该平台的完整升级",listOf(
                    "保留目标库完整备份，核对实际运行产物、数据库命名空间及 DDL 权限",
                    "通过平台安装器执行已核对的结构及模块数据升级，不只手工补当前报错字段",
                    "唯一键缺失时先核对历史重复值与原始账册，不自动删除、重编号或放宽约束以通过检查",
                    "主键或非空约束缺失时核对历史重复标识和空值的真实依据，不自动生成标识或补造必填业务事实",
                    "结构或模块更新失败时先处理首个错误；版本号推进不能代替结构、数据和流程核实",
                    "重新运行部署扫描，再核对历史账册、永久原件、馆方自定义流程及真实办理入口",
                ),restartRequired=true)))
        }
    }
}

@Component
class CollectionRegulationSchemaDiagnosticCheck(private val dataSource:DataSource,private val install:CollectionInstall):DeploymentDiagnosticCheck {
    override val descriptor=DeploymentCheckDescriptor("collection.regulation.database-schema","DATABASE","藏品管理","藏品管理声明表、字段及关键约束",EnumDeploymentCheckSeverity.CRITICAL,timeoutMillis=30_000,order=116)
    override fun check()=CollectionModuleSchemaCheck.check(dataSource,install.initTables(),"藏品管理")
}
