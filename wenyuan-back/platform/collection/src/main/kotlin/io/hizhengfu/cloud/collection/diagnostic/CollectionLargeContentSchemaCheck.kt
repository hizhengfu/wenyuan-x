package io.hizhengfu.cloud.collection.diagnostic

import io.hizhengfu.cloud.collection.install.CollectionInstall
import io.hizhengfu.cloud.flow.install.FlowInstall
import io.hizhengfu.cloud.sys.diagnostic.domain.*
import io.hizhengfu.cloud.sys.diagnostic.service.DeploymentDiagnosticCheck
import org.jetbrains.exposed.v1.core.JsonColumnMarker
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.TextColumnType
import org.springframework.stereotype.Component
import java.sql.SQLException
import java.sql.Types
import java.util.Locale
import javax.sql.DataSource

/** 声明为JSON或text的正文不得因固定长度字符列而被误认作已完成大字段升级。只读元数据，不读取正文。 */
object CollectionLargeContentSchemaCheck {
    internal enum class Capacity { LARGE, BOUNDED, INCOMPATIBLE, UNKNOWN }
    internal data class StoredType(val jdbc: Int?, val name: String?, val size: Long?) {
        fun capacity(product: String, json: Boolean, binary: Boolean): Capacity {
            val type = name?.trim()?.uppercase(Locale.ROOT)?.takeIf(String::isNotEmpty) ?: return Capacity.UNKNOWN
            val vendor = product.uppercase(Locale.ROOT)
            if (vendor == "SQLITE") {
                // SQLite不执行VARCHAR(n)长度限制；JSONB使用BLOB，普通正文不能借此放行二进制列。
                return when {
                    type.contains("INT") -> Capacity.INCOMPATIBLE // SQLite的INTEGER亲和性规则优先于CHAR/TEXT/BLOB。
                    binary && type.contains("BLOB") -> Capacity.LARGE
                    type.contains("CHAR") || type.contains("CLOB") || type.contains("TEXT") -> Capacity.LARGE
                    else -> Capacity.INCOMPATIBLE
                }
            }
            if (jdbc == null) return Capacity.UNKNOWN
            if (jdbc in setOf(Types.CLOB, Types.NCLOB)) return Capacity.LARGE
            val postgres = vendor.contains("POSTGRES") || vendor.contains("OPENGAUSS")
            val mysql = vendor.contains("MYSQL") || vendor.contains("MARIADB")
            val dm = vendor.startsWith("DM") || vendor.contains("DAMENG")
            if (json && type in setOf("JSON", "JSONB") && (postgres || mysql || dm)) return Capacity.LARGE
            if (postgres && type == "TEXT" && jdbc in TEXT_TYPES) return Capacity.LARGE
            if (mysql && type in setOf("TEXT", "MEDIUMTEXT", "LONGTEXT") && jdbc in TEXT_TYPES) return Capacity.LARGE
            if (postgres && type in setOf("VARCHAR", "CHARACTER VARYING") && jdbc == Types.VARCHAR) {
                // PostgreSQL JDBC用Int.MAX_VALUE表示没有声明长度的varchar。
                return when { size == null || size <= 0 -> Capacity.UNKNOWN; size == Int.MAX_VALUE.toLong() -> Capacity.LARGE; else -> Capacity.BOUNDED }
            }
            if (type == "TINYTEXT" && mysql) return Capacity.BOUNDED
            if (jdbc in setOf(Types.CHAR, Types.NCHAR, Types.VARCHAR, Types.NVARCHAR)) {
                return if (size == null || size <= 0) Capacity.UNKNOWN else Capacity.BOUNDED
            }
            if (jdbc in setOf(Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB, Types.INTEGER, Types.BIGINT,
                    Types.NUMERIC, Types.DECIMAL, Types.FLOAT, Types.REAL, Types.DOUBLE, Types.BOOLEAN, Types.DATE, Types.TIMESTAMP)) return Capacity.INCOMPATIBLE
            return Capacity.UNKNOWN
        }
        companion object { private val TEXT_TYPES = setOf(Types.VARCHAR, Types.NVARCHAR, Types.LONGVARCHAR, Types.LONGNVARCHAR) }
    }

    fun check(source: DataSource, tables: Set<Table>, feature: String): DeploymentCheckOutcome {
        val expected = tables.sortedBy(Table::tableName).associateWith { table -> table.columns.filter {
            it.columnType is JsonColumnMarker || it.columnType is TextColumnType
        } }.filterValues { it.isNotEmpty() }
        if (expected.isEmpty()) return DeploymentCheckOutcome(EnumDeploymentCheckState.SKIPPED, "$feature 未声明JSON或长文本字段")
        return try { source.connection.use { connection ->
            runCatching { connection.isReadOnly = true }
            val meta = connection.metaData
            val catalog = runCatching { connection.catalog }.getOrNull()?.takeIf(String::isNotBlank)
            val schema = runCatching { connection.schema }.getOrNull()?.takeIf(String::isNotBlank)
            if ((meta.supportsSchemasInTableDefinitions() && schema == null) || (meta.supportsCatalogsInTableDefinitions() && catalog == null)) {
                return@use DeploymentCheckOutcome(EnumDeploymentCheckState.ERROR, "无法确定当前数据库命名空间，不能核实大字段容量", "COLLECTION_CONTENT_SCOPE_UNKNOWN")
            }
            val escape = meta.searchStringEscape.orEmpty()
            fun pattern(value: String): String = if (escape.isEmpty()) value else value.replace(escape, escape + escape).replace("_", escape + "_").replace("%", escape + "%")
            val missing = mutableListOf<String>(); val bounded = mutableListOf<String>(); val incompatible = mutableListOf<String>(); val unknown = mutableListOf<String>()
            var checked = 0
            for ((table, columns) in expected) {
                val queryName = when { meta.storesUpperCaseIdentifiers() -> table.tableName.uppercase(Locale.ROOT); meta.storesLowerCaseIdentifiers() -> table.tableName.lowercase(Locale.ROOT); else -> table.tableName }
                val actual = linkedMapOf<String, StoredType>(); val duplicates = mutableSetOf<String>()
                val owners = mutableSetOf<Pair<String?, String?>>()
                meta.getColumns(catalog, schema?.let(::pattern), pattern(queryName), "%").use { rows ->
                    while (rows.next()) {
                        if (!rows.getString("TABLE_NAME").equals(queryName, ignoreCase = !meta.supportsMixedCaseIdentifiers())) continue
                        val actualCatalog = rows.getString("TABLE_CAT")?.takeIf(String::isNotBlank)
                        val actualSchema = rows.getString("TABLE_SCHEM")?.takeIf(String::isNotBlank)
                        if (catalog != null && catalog != actualCatalog || schema != null && schema != actualSchema) continue
                        owners += actualCatalog to actualSchema
                        val name = rows.getString("COLUMN_NAME")?.lowercase(Locale.ROOT) ?: continue
                        if (actual.containsKey(name)) duplicates += name
                        val jdbc = rows.getInt("DATA_TYPE").let { if (rows.wasNull()) null else it }
                        val size = rows.getLong("COLUMN_SIZE").let { if (rows.wasNull()) null else it }
                        actual[name] = StoredType(jdbc, rows.getString("TYPE_NAME"), size)
                    }
                }
                for (column in columns) {
                    val name = column.name.lowercase(Locale.ROOT); val key = "${table.tableName}.${column.name}"
                    val type = actual[name]
                    when {
                        owners.size > 1 || name in duplicates -> unknown += key
                        type == null -> missing += key
                        else -> {
                            checked++
                            val json = column.columnType as? JsonColumnMarker
                            when (type.capacity(meta.databaseProductName, json != null, json?.usesBinaryFormat == true)) {
                                Capacity.LARGE -> Unit
                                Capacity.BOUNDED -> bounded += "$key(size=${type.size ?: "unknown"})"
                                Capacity.INCOMPATIBLE -> incompatible += key
                                Capacity.UNKNOWN -> unknown += key
                            }
                        }
                    }
                }
            }
            val facts = listOf(DeploymentSafeFact("declaredLargeContentColumns", expected.values.sumOf { it.size }.toString()),
                DeploymentSafeFact("checkedLargeContentColumns", checked.toString()), DeploymentSafeFact("missingLargeContentColumns", missing.joinToString()),
                DeploymentSafeFact("boundedLargeContentColumns", bounded.joinToString()), DeploymentSafeFact("incompatibleLargeContentColumns", incompatible.joinToString()),
                DeploymentSafeFact("unverifiedLargeContentColumns", unknown.joinToString()),
                DeploymentSafeFact("capacityScope", "declared JSON and text column storage families only; engine limits, check constraints, historical values and roundtrip require separate verification"))
            when {
                missing.isNotEmpty() || bounded.isNotEmpty() || incompatible.isNotEmpty() -> DeploymentCheckOutcome(EnumDeploymentCheckState.FAIL,
                    "$feature 存在缺失、固定长度或不兼容的大字段，须核对完整材料存储", "COLLECTION_CONTENT_STORAGE_INCOMPATIBLE", facts, listOf(remediation()))
                unknown.isNotEmpty() -> DeploymentCheckOutcome(EnumDeploymentCheckState.ERROR, "$feature 部分大字段元数据或存储类型无法核实", "COLLECTION_CONTENT_STORAGE_UNVERIFIED", facts, listOf(remediation()))
                else -> DeploymentCheckOutcome(EnumDeploymentCheckState.PASS, "$feature 声明的 $checked 个JSON/长文本字段使用已识别的大字段存储；实际容量上限、约束及历史材料回读仍须验收", safeFacts = facts)
            }
        } } catch (_: SQLException) { DeploymentCheckOutcome(EnumDeploymentCheckState.ERROR, "$feature 大字段元数据读取失败，不能认定容量兼容", "COLLECTION_CONTENT_METADATA_UNAVAILABLE", remediations = listOf(remediation())) }
    }

    private fun remediation() = DeploymentRemediation(EnumDeploymentRemediationType.DATABASE, "在恢复副本核实长文本字段与历史材料", listOf(
        "确认应用实际连接的数据库、模式、驱动及兼容模式，先备份数据库和原文件目录",
        "逐列核对JSON、档案快照及长正文的类型和容量，不使用其他模式的同名字段作为依据",
        "在恢复副本验证适合目标数据库的大字段类型、JSON函数、完整中文材料写入和回读，再制定增量升级",
        "保留全部历史正文及签署摘要，禁止通过截断原文、删除字段或重算旧摘要消除容量告警",
        "升级后重新扫描；大字段类型识别通过仍须核对数据库限制、历史最大内容、约束及恢复结果",
    ))
}

@Component
class CollectionLargeContentSchemaDiagnosticCheck(private val source: DataSource, private val install: CollectionInstall, private val flowInstall: FlowInstall) : DeploymentDiagnosticCheck {
    override val descriptor = DeploymentCheckDescriptor("collection.large-content-schema", "DATABASE", "藏品及工作流", "JSON与档案长文本存储", EnumDeploymentCheckSeverity.CRITICAL, timeoutMillis = 30_000, order = 120)
    override fun check() = CollectionLargeContentSchemaCheck.check(source, install.initTables() + flowInstall.initTables(), "藏品及工作流")
}
