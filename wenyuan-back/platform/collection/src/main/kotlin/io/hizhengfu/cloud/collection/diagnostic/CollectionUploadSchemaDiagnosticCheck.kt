package io.hizhengfu.cloud.collection.diagnostic

import io.hizhengfu.cloud.sys.diagnostic.domain.*
import io.hizhengfu.cloud.sys.diagnostic.service.DeploymentDiagnosticCheck
import io.hizhengfu.cloud.sys.file.dao.*
import io.hizhengfu.cloud.sys.system.dao.Users
import org.jetbrains.exposed.v1.core.Table
import org.springframework.stereotype.Component
import java.sql.SQLException
import java.sql.Types
import java.util.Locale
import javax.sql.DataSource

/** 共享上传新增状态表也必须进入藏品部署核查，不查询账号、回执或文件正文。 */
@Component
class CollectionUploadSchemaDiagnosticCheck(private val dataSource: DataSource) : DeploymentDiagnosticCheck {
    override val descriptor = DeploymentCheckDescriptor(
        "collection.upload.database-schema", "DATABASE", "藏品材料上传", "上传任务及依赖结构",
        EnumDeploymentCheckSeverity.CRITICAL, timeoutMillis = 30_000, order = 119,
    )

    override fun check(): DeploymentCheckOutcome = try {
        dataSource.connection.use { connection ->
            val base = CollectionModuleSchemaCheck.check(connection, REQUIRED_TABLES, "藏品材料上传依赖")
            if (base.state != EnumDeploymentCheckState.PASS) return@use base
            val meta = connection.metaData
            val catalog = runCatching { connection.catalog }.getOrNull()?.takeIf(String::isNotBlank)
            val schema = runCatching { connection.schema }.getOrNull()?.takeIf(String::isNotBlank)
            val tableName = when {
                meta.storesUpperCaseIdentifiers() -> FileChunkSessions.tableName.uppercase(Locale.ROOT)
                meta.storesLowerCaseIdentifiers() -> FileChunkSessions.tableName.lowercase(Locale.ROOT)
                else -> FileChunkSessions.tableName
            }
            val escape = meta.searchStringEscape.orEmpty()
            fun pattern(value: String): String = if (escape.isEmpty()) value else
                value.replace(escape, escape + escape).replace("_", escape + "_").replace("%", escape + "%")
            val columns = linkedMapOf<String, ColumnType>()
            val duplicates = mutableSetOf<String>()
            meta.getColumns(catalog, schema?.let(::pattern), pattern(tableName), "%").use { rows ->
                while (rows.next()) {
                    if (!rows.getString("TABLE_NAME").equals(tableName, ignoreCase = !meta.supportsMixedCaseIdentifiers())) continue
                    if (catalog != null && catalog != rows.getString("TABLE_CAT")) continue
                    if (schema != null && schema != rows.getString("TABLE_SCHEM")) continue
                    fun number(field: String): Int? = rows.getInt(field).let { if (rows.wasNull()) null else it }
                    val name = rows.getString("COLUMN_NAME").lowercase(Locale.ROOT)
                    if (columns.containsKey(name)) duplicates += name
                    columns[name] = ColumnType(number("DATA_TYPE"), rows.getString("TYPE_NAME"), number("COLUMN_SIZE"), number("DECIMAL_DIGITS"))
                }
            }
            val sqlite = meta.databaseProductName.equals("SQLite", ignoreCase = true)
            val missing = FileChunkSessions.columns.map { it.name }.filter { it !in columns }
            val unknown = mutableListOf<String>()
            val incompatible = FileChunkSessions.columns.mapNotNull { col ->
                val actual = columns[col.name] ?: return@mapNotNull null
                val accepted = when (col.name) {
                    "created_at", "expires_at", "updated_at", "revision" -> actual.exactLong(sqlite)
                    "receipt" -> actual.largeText(sqlite)
                    "state" -> actual.variableText(20, sqlite)
                    else -> actual.variableText(64, sqlite)
                }
                if (accepted == null) unknown += "${FileChunkSessions.tableName}.${col.name}"
                if (accepted == false) "${FileChunkSessions.tableName}.${col.name}" else null
            }
            val facts = base.safeFacts + listOf(
                DeploymentSafeFact("taskTypeColumnCount", FileChunkSessions.columns.size.toString()),
                DeploymentSafeFact("taskTypeMissingColumns", missing.joinToString()),
                DeploymentSafeFact("taskTypeIncompatibleColumns", incompatible.joinToString()),
                DeploymentSafeFact("taskTypeUnverifiedColumns", (unknown + duplicates).joinToString()),
                DeploymentSafeFact("typeInspectionScope", "upload task only; dependency types and historical values require separate verification"),
            )
            when {
                missing.isNotEmpty() || duplicates.isNotEmpty() -> DeploymentCheckOutcome(EnumDeploymentCheckState.ERROR, "上传任务字段元数据发生变化或不完整，请重新核对", "COLLECTION_UPLOAD_TYPE_METADATA_INCOMPLETE", facts, listOf(remediation()))
                incompatible.isNotEmpty() -> DeploymentCheckOutcome(EnumDeploymentCheckState.FAIL, "上传任务有 ${incompatible.size} 个字段类型或容量不符合要求，不能据表存在认定可继续上传", "COLLECTION_UPLOAD_COLUMN_TYPE_INCOMPATIBLE", facts, listOf(remediation()))
                unknown.isNotEmpty() -> DeploymentCheckOutcome(EnumDeploymentCheckState.ERROR, "上传任务有字段的类型容量无法通过当前驱动元数据核实", "COLLECTION_UPLOAD_COLUMN_TYPE_UNVERIFIED", facts, listOf(remediation()))
                else -> DeploymentCheckOutcome(EnumDeploymentCheckState.PASS, "上传依赖表、字段及关键约束可见，任务编号、状态、毫秒时间、版本及回执列容量满足要求；历史值、其他依赖类型、存储与实际流程仍须验收", safeFacts = facts)
            }
        }
    } catch (_: SQLException) {
        DeploymentCheckOutcome(EnumDeploymentCheckState.ERROR, "无法读取上传依赖结构元数据，请核对当前连接与驱动", "COLLECTION_UPLOAD_SCHEMA_METADATA_UNAVAILABLE", remediations = listOf(remediation()))
    }

    private data class ColumnType(val jdbc: Int?, val name: String?, val size: Int?, val scale: Int?) {
        private fun sqliteText(): Boolean? = name?.takeIf(String::isNotBlank)?.uppercase(Locale.ROOT)?.let { "INT" !in it && ("CHAR" in it || "CLOB" in it || "TEXT" in it) }
        fun variableText(minimum: Int, sqlite: Boolean): Boolean? = when {
            sqlite -> sqliteText()
            jdbc == null -> null
            jdbc !in setOf(Types.VARCHAR, Types.NVARCHAR) -> false
            size == null || size <= 0 -> null
            else -> size >= minimum
        }
        fun exactLong(sqlite: Boolean): Boolean? = when {
            sqlite -> name?.takeIf(String::isNotBlank)?.uppercase(Locale.ROOT)?.contains("INT")
            jdbc == null -> null
            jdbc == Types.BIGINT -> true
            jdbc in setOf(Types.NUMERIC, Types.DECIMAL) -> if (size == null || size <= 0 || scale == null) null else size >= 19 && scale == 0
            else -> false
        }
        fun largeText(sqlite: Boolean): Boolean? = when {
            sqlite -> sqliteText()
            jdbc == null -> null
            jdbc in setOf(Types.CLOB, Types.NCLOB) -> true
            jdbc in setOf(Types.VARCHAR, Types.NVARCHAR, Types.LONGVARCHAR, Types.LONGNVARCHAR) -> if (size == null || size <= 0) null else size >= 65_535
            else -> false
        }

    }

    private fun remediation() = DeploymentRemediation(EnumDeploymentRemediationType.DATABASE, "保留上传记录并核对完整升级", listOf(
        "先备份当前数据库与原文件目录，核对应用实际连接的数据库及命名空间",
        "按当前平台安装器声明核对完整依赖结构，不只手工补当前报错字段",
        "时间及版本列必须能够精确保存有符号 64 位整数；不可先截断历史值来匹配窄字段",
        "编号和回执字段须保持原值，不生成假归属、不重算旧回执、不清理待核对分片",
        "完成结构和历史数据核对后重新扫描，并验证正常上传、重复完成、过期及异常任务查阅",
    ), restartRequired = true)

    companion object {
        val REQUIRED_TABLES: Set<Table> = setOf(FileChunkSessions, Files, FileUsers, FileContents, FileFolders, Users)
    }
}
