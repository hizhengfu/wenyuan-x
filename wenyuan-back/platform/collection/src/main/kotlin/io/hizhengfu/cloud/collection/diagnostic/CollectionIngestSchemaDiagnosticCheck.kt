package io.hizhengfu.cloud.collection.diagnostic

import io.hizhengfu.cloud.museum.device.install.MuseumDeviceInstall
import io.hizhengfu.cloud.sys.diagnostic.domain.*
import io.hizhengfu.cloud.sys.diagnostic.service.DeploymentDiagnosticCheck
import org.springframework.stereotype.Component
import java.sql.SQLException
import javax.sql.DataSource

/** 藏品保护依赖的采集表必须检查字段及关键约束，不能仅凭表存在或安装版本通过。 */
@Component
class CollectionIngestSchemaDiagnosticCheck(
    private val dataSource: DataSource,
    private val install: MuseumDeviceInstall,
) : DeploymentDiagnosticCheck {
    override val descriptor = DeploymentCheckDescriptor(
        "collection.ingest.database-schema", "DATABASE", "藏品采集与预警依赖", "采集声明表、字段及关键约束",
        EnumDeploymentCheckSeverity.CRITICAL, timeoutMillis = 30_000, order = 115,
    )

    override fun check(): DeploymentCheckOutcome = try {
        CollectionModuleSchemaCheck.check(dataSource, install.initTables(), "藏品采集与预警依赖")
    } catch (_: SQLException) {
        DeploymentCheckOutcome(
            EnumDeploymentCheckState.ERROR, "无法读取当前采集依赖结构元数据，不能判定升级已完成",
            "COLLECTION_INGEST_SCHEMA_METADATA_UNAVAILABLE",
            remediations = listOf(DeploymentRemediation(EnumDeploymentRemediationType.DATABASE,
                "核对当前数据源、命名空间与元数据读取权限", listOf(
                    "核对当前运行产物使用的数据库 catalog、schema、owner 与账号权限",
                    "在数据库恢复连接及元数据可见后重新检查；不要把读取失败当作结构不存在而直接建表",
                ))),
        )
    }
}
