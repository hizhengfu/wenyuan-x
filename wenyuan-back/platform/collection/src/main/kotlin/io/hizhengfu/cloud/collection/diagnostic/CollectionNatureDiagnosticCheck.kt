package io.hizhengfu.cloud.collection.diagnostic

import io.hizhengfu.cloud.collection.service.CollectionNatureReviewService
import io.hizhengfu.cloud.sys.diagnostic.domain.*
import io.hizhengfu.cloud.sys.diagnostic.service.DeploymentDiagnosticCheck
import org.springframework.stereotype.Component

@Component
class CollectionNatureDiagnosticCheck(private val review: CollectionNatureReviewService) : DeploymentDiagnosticCheck {
    override val descriptor = DeploymentCheckDescriptor("collection.regulation.nature", "CONFIGURATION", "藏品管理",
        "正式藏品法定性质与第61条化石存量核查", EnumDeploymentCheckSeverity.CRITICAL, order = 120)
    override fun check(): DeploymentCheckOutcome {
        val result = review.inspect(pageSize = 1)
        val ready = result.total == 0 && result.unresolvedArchives == 0
        return DeploymentCheckOutcome(if (ready) EnumDeploymentCheckState.PASS else EnumDeploymentCheckState.FAIL,
            if (ready) "本次扫描未发现法定性质、等级和已有化石类别记录冲突；不代表真实鉴定及全部历史原件已经验收"
            else "正式藏品法定性质或历史化石记录待核实，请从总账的法定性质核查查看明细",
            "COLLECTION_NATURE_REVIEW", listOf(DeploymentSafeFact("collectionsToReview", result.total.toString()),
                DeploymentSafeFact("unresolvedArchives", result.unresolvedArchives.toString())),
            remediations = if (ready) emptyList() else listOf(DeploymentRemediation(EnumDeploymentRemediationType.CONFIGURATION,
                "核对实际分类与行政原件后办理正式修订", listOf(
                    "第61条类别仅用于具有科学价值的古脊椎动物、古人类化石；不能按名称或自然资源重点保护等级直接推定",
                    "从藏品总账的法定性质核查定位对象，保留旧审批、借还原件和原号，依据真实鉴定及行政材料办理正式信息变更",
                    "历史借用或归还选项只作为待核查线索；扫描不改主档、不补造历史定级或批准事实",
                ))))
    }
}
