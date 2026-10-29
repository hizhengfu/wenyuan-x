package io.hizhengfu.cloud.collection.rfid.diagnostic

import io.hizhengfu.cloud.collection.dao.Collections
import io.hizhengfu.cloud.collection.rfid.dao.RfidAnomalies
import io.hizhengfu.cloud.collection.rfid.dao.RfidAnomalyTransitions
import io.hizhengfu.cloud.collection.rfid.dao.RfidHandheldReadBatches
import io.hizhengfu.cloud.collection.rfid.dao.RfidOperatorCards
import io.hizhengfu.cloud.collection.rfid.dao.RfidPassageEvents
import io.hizhengfu.cloud.collection.rfid.dao.RfidPassageGrants
import io.hizhengfu.cloud.collection.rfid.dao.RfidRealtimeEvents
import io.hizhengfu.cloud.collection.rfid.dao.RfidScanExpectedItems
import io.hizhengfu.cloud.collection.rfid.dao.RfidScanReads
import io.hizhengfu.cloud.collection.rfid.dao.RfidScanSessions
import io.hizhengfu.cloud.collection.rfid.dao.RfidTagJobs
import io.hizhengfu.cloud.collection.rfid.dao.RfidTagOperations
import io.hizhengfu.cloud.collection.rfid.dao.RfidTags
import io.hizhengfu.cloud.collection.rfid.dao.RfidWorkstationDeviceBindings
import io.hizhengfu.cloud.collection.rfid.dao.RfidWorkstations
import io.hizhengfu.cloud.sys.diagnostic.domain.DeploymentCheckDescriptor
import io.hizhengfu.cloud.sys.diagnostic.domain.DeploymentCheckOutcome
import io.hizhengfu.cloud.sys.diagnostic.domain.DeploymentRemediation
import io.hizhengfu.cloud.sys.diagnostic.domain.DeploymentSafeFact
import io.hizhengfu.cloud.sys.diagnostic.domain.EnumDeploymentCheckSeverity
import io.hizhengfu.cloud.sys.diagnostic.domain.EnumDeploymentCheckState
import io.hizhengfu.cloud.sys.diagnostic.domain.EnumDeploymentRemediationType
import io.hizhengfu.cloud.sys.diagnostic.service.DeploymentDiagnosticCheck
import org.jetbrains.exposed.v1.core.Table
import org.springframework.stereotype.Component
import java.sql.DatabaseMetaData
import java.util.Locale
import javax.sql.DataSource

/**
 * 部署扫描中的 RFID 核心表及藏品主表结构检查。只读取 JDBC 元数据，不执行 DDL，也不返回连接信息。
 * 不替代各来源业务单据、完整平台升级及真实设备验收。
 */
@Component
class RfidSchemaDiagnosticCheck(
    private val dataSource: DataSource,
) : DeploymentDiagnosticCheck {
    override val descriptor = DeploymentCheckDescriptor(
        id = "collection.rfid.database-schema",
        category = "DATABASE",
        feature = "藏品 RFID",
        name = "RFID 核心及藏品主表字段",
        severity = EnumDeploymentCheckSeverity.CRITICAL,
        timeoutMillis = 10_000,
        order = 115,
    )

    override fun check(): DeploymentCheckOutcome = dataSource.connection.use { connection ->
        // 部分驱动（SQLite）只能在建连前声明只读；本检查本身仅调用 JDBC 元数据。
        runCatching { connection.isReadOnly = true }
        val metadata = connection.metaData
        val catalog = runCatching { connection.catalog }.getOrNull()
        val schema = runCatching { connection.schema }.getOrNull()
        val missingTables = mutableListOf<String>()
        val missingColumns = mutableListOf<String>()

        REQUIRED_TABLES.forEach { table ->
            val tableName = table.tableName.lowercase(Locale.ROOT)
            val actualColumns = columns(metadata, catalog, schema, tableName)
            if (actualColumns.isEmpty()) {
                missingTables += tableName
            } else {
                table.columns.map { it.name.lowercase(Locale.ROOT) }
                    .filterNot(actualColumns::contains)
                    .forEach { missingColumns += "$tableName.$it" }
            }
        }

        val facts = listOf(
            DeploymentSafeFact("declaredTableCount", REQUIRED_TABLES.size.toString()),
            DeploymentSafeFact("missingTableCount", missingTables.size.toString()),
            DeploymentSafeFact("missingColumnCount", missingColumns.size.toString()),
            DeploymentSafeFact("missingTables", missingTables.take(100).joinToString()),
            DeploymentSafeFact("missingColumns", missingColumns.take(100).joinToString()),
        )
        if (missingTables.isEmpty() && missingColumns.isEmpty()) {
            DeploymentCheckOutcome(
                state = EnumDeploymentCheckState.PASS,
                summary = "RFID 核心及藏品主表共 ${REQUIRED_TABLES.size} 张表及声明字段均已落库",
                safeFacts = facts,
            )
        } else {
            DeploymentCheckOutcome(
                state = EnumDeploymentCheckState.FAIL,
                summary = "RFID 核心或藏品主表结构不完整：缺 ${missingTables.size} 张表、${missingColumns.size} 个字段",
                reasonCode = "COLLECTION_RFID_SCHEMA_INCOMPLETE",
                safeFacts = facts,
                remediations = listOf(
                    DeploymentRemediation(
                        type = EnumDeploymentRemediationType.DATABASE,
                        summary = "执行完整藏品平台表结构升级",
                        steps = listOf(
                            "先备份目标数据库并确认当前连接的 catalog、schema 和账号 DDL 权限",
                            "确认运行资源中的 app.version 与待部署源码一致，不能只编译类而沿用旧资源",
                            "部署版本号高于数据库安装版本的藏品平台，并检查最早出现的迁移错误",
                            "不要只手工补当前报错字段；应让安装器扫描 RFID 声明表及其依赖的藏品主表",
                            "升级完成后重新运行部署扫描，确认缺失表和字段数量均为 0",
                        ),
                        restartRequired = true,
                    ),
                ),
            )
        }
    }

    private fun columns(
        metadata: DatabaseMetaData,
        catalog: String?,
        schema: String?,
        expectedTable: String,
    ): Set<String> {
        val result = linkedSetOf<String>()
        val candidates = linkedSetOf(expectedTable, expectedTable.uppercase(Locale.ROOT))
        val schemaCandidates = linkedSetOf(schema, null)
        schemaCandidates.forEach { schemaCandidate ->
            candidates.forEach { tableCandidate ->
                metadata.getColumns(catalog, schemaCandidate, tableCandidate, "%").use { rows ->
                    while (rows.next()) {
                        if (rows.getString("TABLE_NAME").equals(expectedTable, ignoreCase = true)) {
                            result += rows.getString("COLUMN_NAME").lowercase(Locale.ROOT)
                        }
                    }
                }
            }
        }
        return result
    }

    internal companion object {
        val RFID_TABLES: List<Table> = listOf(
            RfidWorkstations,
            RfidWorkstationDeviceBindings,
            RfidOperatorCards,
            RfidTags,
            RfidTagOperations,
            RfidTagJobs,
            RfidScanSessions,
            RfidScanExpectedItems,
            RfidScanReads,
            RfidHandheldReadBatches,
            RfidPassageGrants,
            RfidPassageEvents,
            RfidAnomalies,
            RfidAnomalyTransitions,
            RfidRealtimeEvents,
        )

        // 标签绑定、读数解析、应读清单和藏品选择都会查询主表的完整映射。
        // 只检查 col_rfid_* 会漏掉旧库缺失主表字段造成的业务查询失败。
        private val REQUIRED_TABLES: List<Table> = RFID_TABLES + Collections
    }
}
