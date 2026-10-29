package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionConst as C
import org.springframework.stereotype.Component
import org.springframework.beans.factory.ObjectProvider
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import org.springframework.web.servlet.HandlerMapping
import io.hizhengfu.cloud.common.exception.BadRequestException

/** 与各业务原件查阅职责对应；权限来自当前账号目录，不能用旧登录或普通附件权限替代。 */
@Component
class CollectionBusinessArchiveReadPolicy(private val authorization: CollectionFileReadAuthorization, private val disclosures: CollectionDisclosureService,
    private val mainFiles: CollectionMainFileReadPolicy? = null,
    private val transfers: CollectionTransferArchiveReadPolicy? = null,
    private val fragilityOriginals: ObjectProvider<CollectionFragilityOriginalService>? = null) : CollectionArchiveReadPolicy {
    private data class Rule(val permissions: Set<String>, val roles: Set<String> = emptySet())
    private fun rule(vararg permissions: String) = Rule(setOf("ADMIN", C.ADMIN, *permissions))
    private val rules = mapOf(
        C.BIZ_ACQUISITION to Rule(AcquisitionConfidentialAccess.PERMISSIONS, AcquisitionConfidentialAccess.ROLES),
        C.BIZ_ACQUISITION_PLAN to rule(C.ACQUIRE, C.SELECT),
        DesignatedReceptionEvidence.BIZ to rule(C.ACQUIRE, C.ACQUIRE_CONFIDENTIAL_SELECT),
        AccessionReceiptService.BIZ to rule(C.ACQUIRE, C.SELECT, C.LEDGER_CREATE, C.STORAGE, C.CATALOG_AUDIT),
        CollectionGradingService.BIZ to rule(C.APPRAISE, C.SELECT, C.LEDGER_CREATE, C.LEDGER_CORRECT),
        CollectionGradingHistoryEvidence.BIZ to rule(C.APPRAISE, C.SELECT, C.LEDGER_CREATE, C.LEDGER_CORRECT),
        C.BIZ_APPRAISAL to rule(C.APPRAISE, C.SELECT),
        C.BIZ_CHANGE to rule(C.LEDGER_CORRECT, C.SELECT),
        C.BIZ_DEREGISTER to rule(C.DEREGISTER, C.SELECT),
        "COLLECTION_RETAINED_LEDGER" to rule(C.LEDGER_CORRECT, C.SELECT),
        "COLLECTION_AUXILIARY" to rule(C.LEDGER_CORRECT, C.SELECT),
        C.BIZ_REQUISITION to rule(C.STORAGE, C.SELECT),
        C.BIZ_RETURN to rule(C.STORAGE, C.SELECT),
        C.BIZ_LOAN to rule(C.STORAGE, C.SELECT),
        C.BIZ_EXHIBITION to rule(C.STORAGE, C.SELECT),
        C.BIZ_REPRODUCTION to rule(C.STORAGE, C.SELECT),
        ReproductionComplianceService.BIZ to rule(C.STORAGE, C.SELECT),
        DigitalAcquisitionService.BIZ to Rule(setOf("ADMIN", C.ADMIN, C.STORAGE), setOf(C.ROLE_INFORMATION, C.ROLE_STORAGE_HEAD)),
        C.BIZ_INVENTORY to rule(C.STORAGE, C.SELECT),
        InventoryComplianceService.BIZ to rule(C.STORAGE, C.SELECT),
        InventoryComplianceService.ANNUAL to rule(C.STORAGE, C.SELECT),
        C.BIZ_WAREHOUSE_ENTRY to rule(C.SELECT, C.WAREHOUSE_ENTRY_APPLY, C.WAREHOUSE_ENTRY_VERIFY),
        WarehouseDiaryService.BIZ to rule(C.SELECT, C.STORAGE, C.WAREHOUSE_MANAGE),
        WarehouseStaffingService.BIZ to Rule(setOf("ADMIN", C.ADMIN, C.SELECT, C.WAREHOUSE_MANAGE, C.WAREHOUSE_ENTRY_APPLY),
            setOf(C.ROLE_STORAGE_HEAD, C.ROLE_DIRECTOR)),
        CollectionManagementRulesService.BIZ to rule(C.ORG_MANAGE, C.SELECT),
        "COLLECTION_CALENDAR" to rule(C.SELECT),
        "COLLECTION_STORAGE_MOVEMENT" to rule(C.SELECT, C.STORAGE),
        "COLLECTION_WAREHOUSE_FILES" to rule(C.SELECT, C.WAREHOUSE_MANAGE, C.STORAGE, C.WAREHOUSE_ENTRY_APPLY, C.WAREHOUSE_ENTRY_VERIFY),
        WarehouseService.CORRECTION_BIZ to rule(C.SELECT, C.WAREHOUSE_MANAGE),
        "COLLECTION_TRACKING_IMPORT" to rule(C.SELECT, C.LEDGER_CREATE, C.LEDGER_CORRECT),
        PaperLedgerService.BIZ to rule(C.SELECT, C.ORG_MANAGE, C.LEDGER_CREATE, C.LEDGER_CORRECT, C.LEDGER_FINANCE),
        LedgerReconciliationService.BIZ to rule(C.LEDGER_FINANCE),
        CollectionArchivePacketService.BIZ to rule(C.ARCHIVE_MANAGE, C.ARCHIVE_SELECT),
        CollectionArchivePacketService.JURISDICTION_BIZ to rule(C.ARCHIVE_MANAGE, C.ARCHIVE_SELECT),
        AnnualReportEvidenceService.BIZ to rule(C.STAT, C.SELECT),
        CollectionPeriodReportService.BIZ to rule(C.STAT, C.SELECT),
        InstitutionSuccessionCaseEvidence.BIZ to rule(C.STORAGE, C.CROSS_ORG, C.LEDGER_CORRECT),
        C.BIZ_CARE to rule(C.CARE),
        C.BIZ_FRAGILITY to rule(C.CARE),
        CollectionJointSafetyEvidence.BIZ to rule(C.CARE),
        C.BIZ_SAMPLING to Rule(setOf("ADMIN", C.ADMIN, C.SAMPLING), setOf(C.ROLE_CONSERVATION, C.ROLE_STORAGE_HEAD)),
        C.BIZ_INCIDENT to rule(C.INCIDENT),
        C.BIZ_INCIDENT_RELOCATION to rule(C.INCIDENT),
        C.BIZ_INCIDENT_WORK to rule(C.INCIDENT),
        CollectionEmergencyEvidence.BIZ to rule(C.EMERGENCY),
    )
    override val businessTypes = rules.keys
    override fun requireReadable(reference: CollectionArchiveFileReference) {
        if (reference.businessType == C.BIZ_LOAN && requireFragileLoanMonitoringSource(reference)) return
        if (reference.businessType == CollectionMainFileReadPolicy.RETAINED && mainFiles != null) {
            mainFiles.requireRetainedReadable(reference)
            return
        }
        if (reference.businessType == C.BIZ_DEREGISTER && disclosures.isPublicNoticeArchiveFile(reference)) return
        if (reference.businessType == InstitutionSuccessionCaseEvidence.BIZ) {
            val rule = rules.getValue(reference.businessType)
            try {
                authorization.requireReadable(setOf(reference.orgId), rule.permissions, C.CROSS_ORG, "藏品业务永久原件")
            } catch (denied: io.hizhengfu.cloud.common.exception.BadRequestException) {
                if (transfers?.requireSuccessionBasisReadable(reference) != true) throw denied
            }
            return
        }
        // 行政、账务及报送原件必须遵守详情入口的单馆配置，跨馆权限不能绕过部署边界。
        if (reference.businessType in setOf(CollectionManagementRulesService.BIZ, PaperLedgerService.BIZ,
                LedgerReconciliationService.BIZ, CollectionArchivePacketService.BIZ,
                CollectionArchivePacketService.JURISDICTION_BIZ, AnnualReportEvidenceService.BIZ, CollectionPeriodReportService.BIZ,
                CollectionGradingService.BIZ, CollectionGradingHistoryEvidence.BIZ, C.BIZ_APPRAISAL, C.BIZ_CHANGE)) {
            val rule = rules.getValue(reference.businessType)
            authorization.requireBusinessReadable(reference.orgId, rule.permissions, C.CROSS_ORG, "行政及账务原件")
            return
        }
        // 征集草稿、留存文件身份及接收入藏原件与业务列表使用相同单馆边界；保留法定人员职责读取。
        if (reference.businessType in setOf(C.BIZ_ACQUISITION, C.BIZ_ACQUISITION_PLAN,
                DesignatedReceptionEvidence.BIZ, AccessionReceiptService.BIZ)) {
            val rule = rules.getValue(reference.businessType)
            authorization.requireBusinessReadable(reference.orgId, rule.permissions, C.CROSS_ORG,
                "征集及接收入藏原件", rule.roles)
            return
        }
        // 保护、利用及应急材料与业务入口采用相同部署馆别，同时保留数字采集的实际岗位读取资格。
        if (reference.businessType in setOf(C.BIZ_REPRODUCTION, ReproductionComplianceService.BIZ,
                DigitalAcquisitionService.BIZ, C.BIZ_FRAGILITY, CollectionJointSafetyEvidence.BIZ,
                C.BIZ_SAMPLING, CollectionEmergencyEvidence.BIZ)) {
            val rule = rules.getValue(reference.businessType)
            authorization.requireBusinessReadable(reference.orgId, rule.permissions, C.CROSS_ORG,
                "藏品保护利用及应急原件", rule.roles)
            return
        }
        // 时间核查签署材料与专用核查入口使用同一职责，不能降为普通入库申请附件。
        val rule = if (reference.businessType == C.BIZ_WAREHOUSE_ENTRY && reference.phase.startsWith(WarehouseEntryTimelineService.PHASE))
            Rule(WarehouseEntryTimelineService.READ_PERMISSIONS)
        else if (reference.businessType == C.BIZ_REQUISITION && reference.phase.matches(Regex("(CUSTODY_(P|KEEPER|USE|DIRECTOR)|EARLY_RETURN)_[0-9]+")))
            rule(C.STORAGE, C.SELECT, C.CARE)
        else rules.getValue(reference.businessType)
        if (reference.businessType in setOf(C.BIZ_DEREGISTER,"COLLECTION_AUXILIARY",WarehouseStaffingService.BIZ,WarehouseDiaryService.BIZ,C.BIZ_WAREHOUSE_ENTRY,C.BIZ_INVENTORY,InventoryComplianceService.BIZ,InventoryComplianceService.ANNUAL,C.BIZ_REQUISITION,C.BIZ_LOAN,C.BIZ_RETURN,C.BIZ_INCIDENT,C.BIZ_INCIDENT_WORK,C.BIZ_INCIDENT_RELOCATION,C.BIZ_CARE))
            authorization.requireBusinessReadable(reference.orgId, rule.permissions, C.CROSS_ORG, "藏品业务原件", rule.roles)
        else authorization.requireReadable(setOf(reference.orgId), rule.permissions, C.CROSS_ORG, "藏品业务永久原件", rule.roles)
    }

    /** 仅对专用来源路由重新核对当前职责与冻结关系；不放开借用案卷或通用文件入口。 */
    private fun requireFragileLoanMonitoringSource(reference: CollectionArchiveFileReference): Boolean {
        val request = (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request ?: return false
        val route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE) as? String ?: return false
        if (request.method !in setOf("GET", "HEAD") || !route.endsWith("/collection/fragility/{collectionId}/original/loan-monitoring/{orgId}/{adjustmentId}/{monitoringId}/{fileId}")) return false
        val variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) as? Map<*, *>
            ?: throw BadRequestException("借用监管来源路由身份缺失，请重新从变更原件进入")
        fun variable(name: String) = (variables[name] as? String)?.takeIf(String::isNotBlank)
            ?: throw BadRequestException("借用监管来源路由身份缺失，请重新从变更原件进入")
        val orgId = variable("orgId")
        val monitoringId = variable("monitoringId")
        val fileId = variable("fileId")
        if (reference.orgId != orgId || reference.fileId != fileId || reference.phase != "MONITORING_$monitoringId")
            throw BadRequestException("下载原件与本次借用监管来源身份不一致")
        val originals = fragilityOriginals?.getIfAvailable()
            ?: throw BadRequestException("借用监管来源读取服务不可用，请恢复后重新核对")
        val archiveId = originals.requireLoanMonitoringSource(variable("collectionId"), orgId,
            variable("adjustmentId"), monitoringId, fileId)
        if (archiveId != reference.archiveId) throw BadRequestException("下载档案与本次变更冻结借用监管来源不一致")
        return true
    }
}
