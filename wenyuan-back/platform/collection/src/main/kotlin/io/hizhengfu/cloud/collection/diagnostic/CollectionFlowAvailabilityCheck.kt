package io.hizhengfu.cloud.collection.diagnostic

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionFlowDefinitionConstraint
import io.hizhengfu.cloud.flow.engine.FlowGraphLoader
import io.hizhengfu.cloud.flow.service.FlowDefinitionService
import io.hizhengfu.cloud.sys.diagnostic.domain.*
import io.hizhengfu.cloud.sys.diagnostic.service.DeploymentDiagnosticCheck
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

object CollectionFlowAvailabilityCheck {
    fun check(service:FlowDefinitionService,keys:Set<String>,feature:String):DeploymentCheckOutcome {
        val missing=keys.filter {it.isBlank() || service.findActiveByKey(it)==null}.sorted()
        val facts=listOf(DeploymentSafeFact("requiredFlowCount",keys.size.toString()),DeploymentSafeFact("missingActiveFlows",missing.joinToString()),DeploymentSafeFact("validationScope","ACTIVE_DEFINITION_PRESENCE"))
        return if(missing.isEmpty())DeploymentCheckOutcome(EnumDeploymentCheckState.PASS,"$feature 所需活动流程定义均存在；节点、人员及真实业务仍须核对",safeFacts=facts)
        else DeploymentCheckOutcome(EnumDeploymentCheckState.FAIL,"$feature 有 ${missing.size} 个实际使用流程缺少活动版本，已有草稿和旧定义继续保留","COLLECTION_REQUIRED_FLOW_INACTIVE",facts,
            listOf(DeploymentRemediation(EnumDeploymentRemediationType.CONFIGURATION,"核对馆方流程后发布所需版本",listOf(
                "核对业务实际使用的流程标识、2026 年要求及馆方自定义节点，保留原定义和在办实例",
                "仅缺少全部历史定义时由默认导入补种；已有草稿或禁用定义须由有权限人员核对后发布",
                "不要用强制导入覆盖馆方活动流程来消除提示；发布后重新扫描并验证实际审批和执行入口",
            ))))
    }
}

// 检查仅查询；SQLite 建连后不能切换 readOnly，保留事务以覆盖实际后台扫描。
@Component
@Transactional
class CollectionRequiredFlowDiagnosticCheck(
    private val service:FlowDefinitionService,
    private val constraint:CollectionFlowDefinitionConstraint = CollectionFlowDefinitionConstraint(),
    private val loader:FlowGraphLoader = FlowGraphLoader(1,1),
):DeploymentDiagnosticCheck {
    override val descriptor=DeploymentCheckDescriptor("collection.regulation.required-flows","CONFIGURATION","藏品管理","藏品业务所需活动流程",EnumDeploymentCheckSeverity.CRITICAL,order=119)
    override fun check()=CollectionFlowContractReadiness.check(service,loader,constraint,REQUIRED_KEYS,constraint.contracts.keys,constraint.businessKeys,"藏品管理")
    companion object {
        val REQUIRED_KEYS=setOf(CollectionConst.FLOW_CHANGE,CollectionConst.FLOW_ACQUISITION,CollectionConst.FLOW_DEREGISTER,CollectionConst.FLOW_LOAN,
            CollectionConst.FLOW_TRANSFER,CollectionConst.FLOW_REQUISITION,CollectionConst.FLOW_RETURN,CollectionConst.FLOW_APPRAISAL,
            CollectionConst.FLOW_EXHIBITION,CollectionConst.FLOW_SAMPLING,CollectionConst.FLOW_REPRODUCTION,CollectionConst.FLOW_DIGITAL_ACQUISITION_2026,
            CollectionConst.FLOW_REPRODUCTION_2026,CollectionConst.FLOW_INVENTORY,CollectionConst.FLOW_ACQUISITION_PLAN,CollectionConst.FLOW_WAREHOUSE_ENTRY)
    }
}
