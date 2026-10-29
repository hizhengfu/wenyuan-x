package io.hizhengfu.cloud.collection.diagnostic

import io.hizhengfu.cloud.collection.service.CollectionFileIdentityUpgrade
import io.hizhengfu.cloud.sys.diagnostic.domain.*
import io.hizhengfu.cloud.sys.diagnostic.service.DeploymentDiagnosticCheck
import org.springframework.stereotype.Component

@Component
class CollectionFileIdentityDiagnosticCheck(private val upgrade: CollectionFileIdentityUpgrade): DeploymentDiagnosticCheck {
    override val descriptor = DeploymentCheckDescriptor("collection.files.identity", "CONFIGURATION", "藏品管理",
        "数字采集和应急归档文件访问索引", EnumDeploymentCheckSeverity.CRITICAL, order = 119)
    override fun check() = upgrade.inspect().outcome()
}
