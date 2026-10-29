package io.hizhengfu.cloud.collection.install

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.common.CollectionFlowImporter
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.CollectionDescriptionPolicy
import io.hizhengfu.cloud.collection.domain.EnumLedgerAction
import io.hizhengfu.cloud.collection.domain.EnumOrgStatus
import io.hizhengfu.cloud.collection.rfid.dao.*
import io.hizhengfu.cloud.collection.rfid.domain.*
import io.hizhengfu.cloud.common.annotation.Install
import io.hizhengfu.cloud.common.domain.Dict
import io.hizhengfu.cloud.common.domain.Menu
import io.hizhengfu.cloud.common.domain.Permission
import io.hizhengfu.cloud.common.install.AbstractInstall
import io.hizhengfu.cloud.common.utils.IdGenerate
import io.hizhengfu.cloud.sys.system.dao.Configs
import io.hizhengfu.cloud.sys.system.dao.Roles
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * 藏品管理模块初始化：表 / 权限 / 字典 / 菜单 / 首装数据。
 * 菜单 ID 段 105xx，权限 ID 段 40，避开 sys/message/ticket 已用段。
 */
@Install(
    value = "藏品管理初始化",
    module = CollectionConst.CONFIG_MODULE,
    label = "藏品管理",
)
@Component
class CollectionInstall(
    @param:Value("\${collection.mode:single}") private val mode: String,
    @param:Autowired(required = false) @param:Lazy private val flowImporter: CollectionFlowImporter? = null,
    @param:Autowired(required = false) @param:Lazy private val fileIdentityUpgrade: io.hizhengfu.cloud.collection.service.CollectionFileIdentityUpgrade? = null,
) : AbstractInstall(), io.hizhengfu.cloud.sys.file.service.FileUploadTargetSchema {

    override fun publicUploadBlockedTables(): Set<Table> = initTables()

    override fun initTables(): Set<Table> = setOf(
        CollectionOrgs,
        CollectionOrgCodes,
        CollectionOrgCodeChanges,
        Collections,
        CollectionCatalogs,
        CollectionCatalogVersions,
        CollectionChanges,
        CollectionChangeApprovals,
        CollectionPermanentArchives,
        CollectionPermanentArchiveFiles,
        CollectionPermanentOriginalTransitions,
        CollectionPermanentOriginalRecoveries,
        CollectionPermanentRemoteRecoveries,
        CollectionCalendarVersions,
        CollectionOpeningCalendarVersions,
        CollectionAuxiliaries,
        CollectionRetainedLedgers,
        CollectionRegistrationNumbers,
        CollectionLedgerLogs,
        CollectionBusinessItems,
        CollectionPlacements,
        CollectionOperationLocks,
        CollectionSets,
        CollectionMediaFolders,
        CollectionMedias,
        CollectionAttachments,
        AnnualReportArchives,
        CollectionLedgerStatEvents,
        AnnualReportEvidenceRecords,
        CollectionManagementRulesRecords,
        CollectionStaffEducations,
        CollectionStaffEducationVersions,
        CollectionStaffEducationFileAccesses,
        PaperLedgerBooks,
        PaperLedgerCustodies,
        LedgerReconciliations,
        ArchiveJurisdictions,
        CollectionArchivePackets,
        CollectionArchiveSubmissions,
        CollectionDisclosureReviews,
        CollectionDisclosureEvents,
        CollectionCategories,
        Warehouses,
        WarehouseLogs,
        WarehouseEntryApplications,
        StorageMovements,
        Deregistrations,
        DeregistrationExpertOpinions,
        DeregistrationNotices,
        CollectionAdministrativeRecords,
        Appraisals,
        CollectionGradings,
        AppraisalMembers,
        // 修复相关表迁移至 module-collection-restoration.RestorationInstall
        Loans,
        LoanComplianceRecords,
        Reproductions,
        CollectionIncidents,
        CollectionIncidentRecords,
        CollectionIncidentItems,
        IncidentWorks,
        IncidentWorkRecords,
        IncidentRelocations,
        IncidentRelocationRecords,
        CollectionSamplings,
        CollectionEmergencyPlans,
        CollectionEmergencyDrills,
        CollectionEmergencyDrillRevisions,
        CollectionEmergencyDuties,
        CollectionSafetyReminderSettings,
        CollectionSafetyReminderStates,
        CollectionSafetyReminderDeliveries,
        CollectionSafetyReminderAttempts,
        CollectionEmergencyImprovements,
        CollectionEmergencyFileAccesses,
        CollectionJointSafetyPlans,
        CollectionJointSafetyRounds,
        CollectionCarePlans,
        CollectionCareChecks,
        CollectionCareWorks,
        CollectionFragilityAssessments,
        CollectionFragileMonitorRuntimes,
        CollectionFragileAutomaticChecks,
        CollectionFragileMonitorings,
        CollectionFragileUsePlans,
        CollectionFragileUseRecords,
        CollectionFragileUseAdjustments,
        CollectionSamplingPlans,
        CollectionSamples,
        DigitalAcquisitionPlans,
        DigitalAcquisitionResources,
        DigitalAcquisitionFiles,
        DigitalAcquisitionFileAccesses,
        ReproductionPlans,
        ReproductionSafetyRecords,
        ReproductionOutputs,
        ReproductionOutputUses,
        InventoryBatches,
        MobileOperationReceipts,
        InventoryAnnualReports,
        Inventories,
        CollectionTransfers,
        CollectionTransferTerminations,
        CollectionTransferNotices,
        AcquisitionPlans,
        Acquisitions,
        DesignatedReceptions,
        DesignatedReceptionRecords,
        InstitutionSuccessionCases,
        InstitutionSuccessionItems,
        InstitutionSuccessionRecords,
        AcquisitionComplianceCases,
        AcquisitionExpertOpinions,
        EntryVouchers,
        AccessionReceipts,
        Requisitions,
        CollectionReturns,
        ReturnComplianceRecords,
        Exhibitions,
        CollectionPrintTemplates,
        CollectionWordImports,
        CollectionTrackingEvents,
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

    override fun initMenus(): Set<Menu> {
        val root = "10500"
        val rootIds = "0,$root,"
        fun group(id: String, sort: Long, name: String, icon: String) =
            Menu(id = id, parentId = root, parentIds = rootIds, sort = sort, name = name, icon = icon)
        fun page(id: String, parent: String, sort: Long, name: String, path: String, icon: String) =
            Menu(id = id, parentId = parent, parentIds = if (parent == root) rootIds else "$rootIds$parent,",
                sort = sort, name = name, path = path, icon = icon)

        // 保留业务页面 ID 和路由；目录只组织办理事项，不再重复指向第一张单据。
        return linkedSetOf(
            Menu(id = root, parentId = "0", parentIds = "0,", sort = 30, name = "藏品管理", icon = "m:collection"),
            page("10522", root, 1, "藏品工作台", "/collection", "m:collection-home"),
            page("10521", root, 2, "藏品检索", "/collection/search", "m:collection-search"),
            group("10530", 3, "征集入藏", "m:acquisition"),
            group("10531", 4, "账册与编目", "m:ledger"),
            group("10534", 5, "库房保管", "m:warehouse"),
            group("10532", 6, "提用借用与归库", "m:requisition"),
            group("10533", 7, "展示与利用", "m:exhibition"),
            group("10556", 8, "调拨与退出", "m:transfer"),
            group("10557", 9, "藏品安全", "m:inventory"),
            group("10535", 10, "统计报表", "m:stat"),
            group("10536", 11, "基础设置", "m:org"),
            group("10558", 12, "RFID管理", "m:movement"),

            page("10526", "10530", 1, "征集计划", "/collection/acquisition-plan", "m:acquisition"),
            page("10513", "10530", 2, "征集申请与审批", "/collection/acquisition", "m:acquisition"),
            page("10507", "10530", 4, "鉴定与定级", "/collection/appraisal", "m:appraisal"),
            page("10527", "10530", 3, "入馆核收与登账", "/collection/entry-voucher", "m:acquisition"),

            page("10501", "10531", 1, "藏品总登记账", "/collection/ledger", "m:ledger"),
            page("10560", "10531", 2, "藏品分类账", "/collection/classification-ledger", "m:ledger"),
            page("10529", "10531", 3, "编目录入", "/collection/catalog-entry", "m:catalog-entry"),
            page("10520", "10531", 4, "编目审核", "/collection/catalog-audit", "m:catalog-audit"),
            page("10537", "10531", 5, "藏品编目卡", "/collection/catalog-card", "m:catalog-card"),
            page("10542", "10531", 6, "藏品信息变更", "/collection/change", "m:ledger"),
            page("10516", "10531", 7, "登记与更正记录", "/collection/ledger-log", "m:ledger"),
            page("10544", "10531", 8, "辅助账册", "/collection/auxiliary", "m:ledger"),
            page("10545", "10531", 9, "原馆留存账", "/collection/retained-ledger", "m:ledger"),
            page("10523", "10531", 10, "藏品影像", "/collection/gallery", "m:collection-gallery"),

            page("10502", "10534", 1, "库房与保管日记", "/collection/warehouse", "m:warehouse"),
            page("10538", "10534", 2, "人员出入库登记", "/collection/warehouse-entry", "m:warehouse"),
            page("10511", "10534", 3, "藏品清点", "/collection/inventory", "m:inventory"),

            page("10514", "10532", 1, "馆内提用", "/collection/requisition", "m:requisition"),
            page("10509", "10532", 2, "馆外借用", "/collection/loan", "m:loan"),
            page("10525", "10532", 3, "归库验收", "/collection/return", "m:return"),
            page("10517", "10532", 4, "出入库记录", "/collection/movement", "m:movement"),

            page("10519", "10533", 1, "展览管理", "/collection/exhibition", "m:exhibition"),
            page("10510", "10533", 2, "复制拓印与数字采集", "/collection/reproduction", "m:reproduction"),
            page("10550", "10533", 3, "有损取样", "/collection/sampling", "m:reproduction"),
            page("10512", "10556", 1, "调拨交换与承接", "/collection/transfer", "m:transfer"),
            page("10503", "10556", 2, "退出馆藏", "/collection/deregistration", "m:deregistration"),

            page("10554", "10557", 1, "安全待办", "/collection/safety-task", "m:inventory"),
            page("10552", "10557", 2, "共同安全检查", "/collection/joint-safety", "m:inventory"),
            page("10551", "10557", 3, "事故报告与处置", "/collection/incident", "m:inventory"),
            page("10553", "10557", 4, "应急预案与演练", "/collection/emergency", "m:inventory"),
            page("10555", "10557", 5, "保管岗位培训与继续教育", "/collection/staff-education", "m:inventory"),

            page("10506", "10535", 1, "藏品统计分析", "/collection/stat", "m:stat"),
            page("10528", "10535", 2, "年度统计报送", "/collection/annual-report", "m:stat"),
            page("10504", "10536", 1, "收藏单位", "/collection/org", "m:org"),
            page("10505", "10536", 2, "藏品分类", "/collection/category", "m:category"),
            page("10524", "10536", 3, "打印模板", "/collection/print-template", "m:print-template"),
            page("10543", "10536", 4, "法定工作日历", "/collection/working-calendar", "m:stat"),
            page("10559", "10536", 5, "开放与休馆日历", "/collection/opening-calendar", "m:stat"),

            // 作业入口与基础配置分别导航；独立工作台及设备业务沿用现有流程。
            page("10539", "10558", 1, "RFID作业入口", "/collection/rfid-workstation-entry", "m:movement"),
            page("10540", "10558", 2, "RFID基础管理", "/collection/rfid-operation", "m:inventory"),
            page("10541", "10558", 3, "RFID通行监控", "/collection/rfid-passage", "m:movement"),
        )
    }

    override fun initPermissions(): Set<Permission> {
        val root = "40"
        val rootIds = "0,$root,"
        return linkedSetOf(
            Permission(id = root, code = CollectionConst.ADMIN, name = "藏品管理（总）", parentId = "0", parentIds = "0,"),
            Permission(id = "40_1", code = CollectionConst.SELECT, name = "藏品查看", parentId = root, parentIds = rootIds),
            Permission(id = "40_2", code = CollectionConst.LEDGER_CREATE, name = "藏品登记", parentId = root, parentIds = rootIds),
            Permission(id = "40_3", code = CollectionConst.LEDGER_CORRECT, name = "总账更正", parentId = root, parentIds = rootIds),
            Permission(id = "40_4", code = CollectionConst.STORAGE, name = "出入库/提用", parentId = root, parentIds = rootIds),
            Permission(id = "40_5", code = CollectionConst.DEREGISTER, name = "退出馆藏申请", parentId = root, parentIds = rootIds),
            Permission(id = "40_6", code = CollectionConst.DEREGISTER_APPROVE, name = "退出馆藏审批", parentId = root, parentIds = rootIds),
            Permission(id = "40_7", code = CollectionConst.ORG_MANAGE, name = "收藏单位管理", parentId = root, parentIds = rootIds),
            Permission(id = "40_8", code = CollectionConst.CATEGORY_MANAGE, name = "文物分类管理", parentId = root, parentIds = rootIds),
            Permission(id = "40_9", code = CollectionConst.WAREHOUSE_MANAGE, name = "库房库位管理", parentId = root, parentIds = rootIds),
            Permission(id = "40_10", code = CollectionConst.PUBLISH, name = "发布数字藏品", parentId = root, parentIds = rootIds),
            Permission(id = "40_11", code = CollectionConst.STAT, name = "统计查看", parentId = root, parentIds = rootIds),
            Permission(id = "40_12", code = CollectionConst.CROSS_ORG, name = "跨馆汇总（只读）", parentId = root, parentIds = rootIds),
            Permission(id = "40_13", code = CollectionConst.ACQUIRE, name = "征集入藏", parentId = root, parentIds = rootIds),
            Permission(id = "40_38", code = CollectionConst.ACQUIRE_CONFIDENTIAL_SELECT, name = "征集保密原件查阅", parentId = root, parentIds = rootIds),
            Permission(id = "40_14", code = CollectionConst.APPRAISE, name = "鉴定定级", parentId = root, parentIds = rootIds),
            Permission(id = "40_15", code = CollectionConst.CATALOG_EDIT, name = "编目录入", parentId = root, parentIds = rootIds),
            Permission(id = "40_16", code = CollectionConst.CATALOG_AUDIT, name = "编目审核", parentId = root, parentIds = rootIds),
            Permission(id = "40_17", code = CollectionConst.WAREHOUSE_ENTRY_APPLY, name = "人员入库申请", parentId = root, parentIds = rootIds),
            Permission(id = "40_18", code = CollectionConst.WAREHOUSE_ENTRY_VERIFY, name = "人员进出库核验", parentId = root, parentIds = rootIds),
            Permission(id = "40_19", code = CollectionConst.RFID_DESK, name = "RFID借还工作台", parentId = root, parentIds = rootIds),
            Permission(id = "40_20", code = CollectionConst.RFID_SCAN, name = "RFID扫描控制", parentId = root, parentIds = rootIds),
            Permission(id = "40_21", code = CollectionConst.RFID_FINALIZE, name = "RFID业务确认", parentId = root, parentIds = rootIds),
            Permission(id = "40_22", code = CollectionConst.RFID_OVERRIDE, name = "RFID异常强制确认", parentId = root, parentIds = rootIds),
            Permission(id = "40_23", code = CollectionConst.RFID_TAG_SELECT, name = "RFID标签档案查看", parentId = root, parentIds = rootIds),
            Permission(id = "40_24", code = CollectionConst.RFID_TAG_WRITE, name = "RFID发标换标", parentId = root, parentIds = rootIds),
            Permission(id = "40_25", code = CollectionConst.RFID_TAG_RETIRE, name = "RFID解绑报废", parentId = root, parentIds = rootIds),
            Permission(id = "40_26", code = CollectionConst.RFID_PASSAGE, name = "RFID通行查看", parentId = root, parentIds = rootIds),
            Permission(id = "40_27", code = CollectionConst.RFID_ANOMALY_PROCESS, name = "RFID异常处置", parentId = root, parentIds = rootIds),
            Permission(id = "40_28", code = CollectionConst.RFID_CARD_MANAGE, name = "RFID人员卡管理", parentId = root, parentIds = rootIds),
            Permission(id = "40_29", code = CollectionConst.LEDGER_FINANCE, name = "纸质总账与文物资源资产财务账核对", parentId = root, parentIds = rootIds),
            Permission(id = "40_30", code = CollectionConst.ARCHIVE_MANAGE, name = "藏品档案原件及行政备案管理", parentId = root, parentIds = rootIds),
            Permission(id = "40_39", code = CollectionConst.ARCHIVE_SELECT, name = "藏品档案原件只读查阅", parentId = root, parentIds = rootIds),
            Permission(id = "40_32", code = CollectionConst.INCIDENT, name = "藏品事故报告及处理原件管理", parentId = root, parentIds = rootIds),
            Permission(id = "40_31", code = CollectionConst.SAMPLING, name = "有损取样方案及实际实施管理", parentId = root, parentIds = rootIds),
            Permission(id = "40_35", code = CollectionConst.SAFETY_REMINDER, name = "藏品安全待办提醒设置", parentId = root, parentIds = rootIds),
            Permission(id = "40_36", code = CollectionConst.STAFF_EDUCATION_VIEW, name = "保管人员岗位培训及继续教育查阅", parentId = root, parentIds = rootIds),
            Permission(id = "40_37", code = CollectionConst.STAFF_EDUCATION_MANAGE, name = "保管人员岗位培训及继续教育登记", parentId = root, parentIds = rootIds),
            Permission(id = "40_34", code = CollectionConst.EMERGENCY, name = "藏品应急预案与演练原件管理", parentId = root, parentIds = rootIds),
            Permission(id = "40_33", code = CollectionConst.CARE, name = "日常检查与养护原件管理", parentId = root, parentIds = rootIds),
            // 修复档案/验收/派单查看/人员/资质/合同/工序权限迁移至 module-collection-restoration.RestorationInstall (72 段)
        )
    }

    override fun initDicts(): Set<Dict> {
        val dicts = mutableSetOf<Dict>()
        fun add(type: String, remark: String, pairs: List<Pair<String, String>>) {
            pairs.forEachIndexed { i, (v, l) -> dicts.add(Dict(label = l, value = v, type = type, sort = i, remarks = remark)) }
        }
        add("col_loan_object_kind", "借还验收对象类别", listOf("RELIC" to "文物", "SCIENTIFIC_FOSSIL" to "具有科学价值的古生物化石", "OTHER" to "其他藏品"))
        add("col_return_security_incident", "归还验收安全事件", listOf("NONE" to "无安全事件", "THEFT" to "盗窃", "ROBBERY" to "抢劫", "LOSS" to "丢失"))
        add(CollectionConst.DICT_RELIC_LEVEL, "藏品级别", listOf(
            "FIRST" to "一级", "SECOND" to "二级", "THIRD" to "三级", "GENERAL" to "一般文物", "UNRATED" to "未定级"))
        add(CollectionConst.DICT_SOURCE, "来源方式", listOf(
            "EXCAVATION" to "考古发掘", "COLLECT" to "采集", "PURCHASE" to "收购", "ALLOCATE" to "调拨",
            "TRANSFER" to "拨交", "EXCHANGE" to "交换", "DONATE" to "捐赠", "HANDOVER" to "移交",
            "PICK" to "拣选", "OLD" to "旧藏", "REPRO" to "复制", "DESIGNATED" to "行政指定接收"))
        add(CollectionConst.DICT_TEXTURE, "具体质地", CollectionDescriptionPolicy.materials.map { it to it })
        add(CollectionConst.DICT_COMPLETENESS, "完残程度", listOf(
            "COMPLETE" to "完整", "BASIC" to "基本完整", "DAMAGED" to "残缺", "SEVERE" to "严重残缺（含缺失部件）"))
        add(CollectionConst.DICT_PRESERVE, "保存状态", listOf(
            "A" to "状态稳定，不需修复", "B" to "部分损腐，需要修复", "C" to "腐蚀损毁严重，急需修复"))
        add(CollectionConst.DICT_LIFE_STATUS, "藏品状态", listOf(
            "PENDING" to "待定", "IN_STORE" to "在库", "IN_USE" to "提用中", "RESTORING" to "修复中",
            "ON_LOAN" to "借出", "MISSING" to "盘亏", "DEREGISTERED" to "已退出馆藏", "PRE_OUT" to "待出库", "RETURN_HOLD" to "归还待核查"))
        add(CollectionConst.DICT_CATALOG_STATUS, "编目审核状态", listOf(
            "DRAFT" to "草稿", "SUBMITTED" to "已提交", "PENDING" to "待审核（旧）",
            "APPROVED" to "已通过", "REJECTED" to "已驳回"))
        add(CollectionConst.DICT_ORG_LEVEL, "单位级别", listOf(
            "NATIONAL_1" to "国家一级博物馆", "NATIONAL_2" to "国家二级博物馆", "NATIONAL_3" to "国家三级博物馆", "UNRATED" to "未定级"))
        add(CollectionConst.DICT_OWNERSHIP, "举办主体", listOf("STATE" to "国有", "NON_STATE" to "非国有"))
        val storageLocationTypes = listOf(
            "WAREHOUSE" to "库房", "AREA" to "库区", "CABINET" to "柜架", "LAYER" to "层格", "BOX" to "囊匣")
        val exhibitionLocationTypes = listOf(
            "EXHIBITION_HALL" to "展厅", "DISPLAY_AREA" to "展区", "SHOWCASE" to "展柜")
        add(CollectionConst.DICT_WAREHOUSE_TYPE, "位置类型（全部）", storageLocationTypes + exhibitionLocationTypes)
        add(CollectionConst.DICT_STORAGE_LOCATION_TYPE, "保管位置类型", storageLocationTypes)
        add(CollectionConst.DICT_EXHIBITION_LOCATION_TYPE, "展陈位置类型", exhibitionLocationTypes)
        add(CollectionConst.DICT_LOCATION_SCENE, "位置场景", listOf("STORAGE" to "保管", "EXHIBITION" to "展陈"))
        add(CollectionConst.DICT_MOVE_TYPE, "出入库类型", listOf(
            "IN" to "入库", "OUT" to "出库"))
        add(CollectionConst.DICT_MOVE_REASON, "出入库事由", listOf(
            "RESEARCH" to "研究", "EXHIBIT" to "展览", "RESTORE" to "修复", "LOAN" to "外借",
            "DIGITIZE" to "数字化", "INVENTORY" to "实物清点核查", "TRANSFER" to "调拨", "ACQUISITION" to "征集入馆", "EMERGENCY" to "应急搬移",
            "RELOCATION" to "库房搬迁", "OTHER" to "其他馆内事项"))
        add(CollectionConst.DICT_DEREG_REASON, "退出馆藏原因", listOf(
            "DAMAGE" to "损毁", "LOSS" to "丢失", "THEFT" to "被盗", "RETURN" to "退还",
            "MISJUDGE" to "误定（历史）", "NATURAL" to "自然淘汰（历史）", "TRANSFER" to "调拨注销（历史）",
            "DAMAGE_NO_VALUE" to "老化、腐蚀等毁损且无保存价值", "OTHER_BELOW_STANDARD" to "其他藏品不够入藏标准",
            "FORCE_MAJEURE" to "不可抗力灭失", "STATE_EXCHANGE_TRANSFER" to "国有馆之间交换、调拨", "OTHER_LEGAL" to "法律法规规定的其他情形"))
        add(CollectionConst.DICT_APPROVE_LEVEL, "审批层级", listOf(
            "MUSEUM" to "馆级", "PROVINCE" to "省级", "NATIONAL" to "国家文物局"))
        add(CollectionConst.DICT_REP_TYPE, "复制类型", listOf(
            "COPY" to "复制", "RUBBING" to "拓印", "SCAN_3D" to "三维扫描", "PHOTO" to "影像采集"))
        // 修复方法类别字典迁移至 module-collection-restoration.RestorationInstall
        add(CollectionConst.DICT_INVENTORY_RESULT, "盘点结果", listOf(
            "MATCH" to "账物相符", "SURPLUS" to "盘盈", "MISSING" to "盘亏", "ABNORMAL" to "状态异常"))
        add(CollectionConst.DICT_SET_RELATION, "套件关系", listOf(
            "MAIN" to "主件", "ATTACH" to "附件", "PART" to "分件"))
        add(CollectionConst.DICT_MEDIA_TYPE, "藏品影像类型", listOf(
            "FRONT" to "正面", "BACK" to "背面", "DETAIL" to "细节", "INSCRIPTION" to "款识",
            "RUBBING" to "拓片", "LINE_DRAWING" to "线图", "THREE_D" to "三维", "VIDEO" to "视频",
            "RESTORATION_BEFORE" to "修复前", "RESTORATION_AFTER" to "修复后",
            "RESTORATION_PROCESS" to "修复过程", "OTHER" to "其他", "TOP" to "俯视图", "SIDE" to "侧视图", "PANORAMIC" to "全景图", "BOTTOM" to "底视图"))
        add(CollectionConst.DICT_ATTACHMENT_TYPE, "藏品附件类型", listOf(
            "REGISTRATION" to "登记凭证", "ACQUISITION_CONTRACT" to "征集合同", "APPRAISAL_OPINION" to "鉴定意见",
            "RESTORATION_PLAN" to "修复方案", "LOAN_AGREEMENT" to "借展协议", "DEREG_APPROVAL" to "退出馆藏批准文件",
            "MOVEMENT_RECEIPT" to "出入库签收单", "INVENTORY_RECORD" to "盘点记录", "OTHER" to "其他"))
        add(CollectionConst.DICT_APPLY_STATUS, "藏品申请状态", listOf(
            "APPLYING" to "申请中", "APPROVED" to "已通过", "REJECTED" to "已驳回", "EXECUTED" to "已执行"))
        add(CollectionConst.DICT_ORG_STATUS, "收藏单位状态", listOf(
            "ENABLED" to "启用", "DISABLED" to "停用"))
        add(CollectionConst.DICT_PROCESS_STATE, "业务过程状态", listOf(
            "DRAFT" to "待开始", "ONGOING" to "进行中", "DONE" to "已完成", "CANCELLED" to "已取消"))
        add(CollectionConst.DICT_PRESENCE, "有无状态", listOf(
            "true" to "有", "false" to "无"))
        add(CollectionConst.DICT_RETURNED, "归还状态", listOf(
            "true" to "已归还", "false" to "未归还"))
        add(CollectionConst.DICT_REGISTERED, "登账状态", listOf(
            "true" to "已登账", "false" to "未登账"))
        add(CollectionConst.DICT_REQUIRED, "必填状态", listOf(
            "true" to "必填", "false" to "可选"))
        add(CollectionConst.DICT_CATEGORY_SCOPE, "文物分类类型", listOf(
            "NATIONAL" to "登录规范分类"))
        add(CollectionConst.DICT_LEDGER_ACTION, "总账留痕动作", listOf(
            "REGISTER" to "登记", "CORRECT" to "更正", "MERGE" to "合并", "SPLIT" to "拆分", "DEREGISTER" to "退出馆藏",
            "AUDIT" to "编目审核", "RESERVE_REG_NO" to "预留登记号"))
        add(CollectionConst.DICT_PRINT_TYPE, "藏品打印表单类型", listOf(
            "COLLECTION_ARCHIVE" to "藏品登记表",
            "MOVEMENT" to "出入库单", "REQUISITION" to "提用单", "LOAN_HANDOVER" to "借展点交单",
            "DEREGISTRATION" to "退出馆藏审批表", "INVENTORY" to "盘点表", "APPRAISAL" to "鉴定定级表",
            "TRANSFER" to "跨馆调拨表", "RESTORATION" to "修复记录表", "ACQUISITION" to "征集入藏表",
            "REPRODUCTION" to "复制拓印记录表", "EXHIBITION" to "展览记录表",
            "MOUNTING" to "装裱凭证", "ENTRY_VOUCHER" to "入馆凭证"))
        add(CollectionConst.DICT_PLAN_PERIOD, "征集计划周期", listOf(
            "YEAR" to "年度", "MONTH" to "月度"))
        add(CollectionConst.DICT_VOUCHER_STATUS, "入馆凭证状态", listOf(
            "DRAFT" to "待核收", "REGISTERED" to "已正式登账", "RECEIVED" to "已核收待登记"))
        add(CollectionConst.DICT_RFID_WORKSTATION_CAPABILITY, "RFID 工作站业务能力",
            EnumRfidWorkstationCapability.entries.map { it.name to it.description })
        add(CollectionConst.DICT_RFID_WORKSTATION_DEVICE_PURPOSE, "RFID 工作站外设用途",
            EnumRfidWorkstationDevicePurpose.entries.map { it.name to it.description })
        add(CollectionConst.DICT_RFID_WORKSTATION_STATUS, "RFID 工作站状态", listOf(
            "ENABLED" to "启用", "DISABLED" to "停用"))
        add(CollectionConst.DICT_RFID_OPERATOR_CARD_STATUS, "RFID 人员卡状态",
            EnumRfidOperatorCardStatus.entries.map { it.name to it.description })
        add(CollectionConst.DICT_RFID_TAG_STATUS, "RFID 标签状态",
            EnumRfidTagStatus.entries.map { it.name to it.description })
        add(CollectionConst.DICT_RFID_TAG_OPERATION_TYPE, "RFID 标签操作类型",
            EnumRfidTagOperationType.entries.map { it.name to it.description })
        add(CollectionConst.DICT_RFID_OPERATION_RESULT, "RFID 操作结果", listOf("true" to "成功", "false" to "失败"))
        add(CollectionConst.DICT_RFID_TAG_JOB_STATUS, "RFID 发标任务状态",
            EnumRfidTagJobStatus.entries.map { it.name to it.description })
        add(CollectionConst.DICT_RFID_SCAN_SESSION_TYPE, "RFID 扫描会话类型",
            EnumRfidScanSessionType.entries.map { it.name to it.description })
        add(CollectionConst.DICT_RFID_SCAN_SESSION_STATE, "RFID 扫描会话状态",
            EnumRfidScanSessionState.entries.map { it.name to it.description })
        add(CollectionConst.DICT_RFID_DIRECTION, "RFID 通行方向",
            EnumRfidDirection.entries.map { it.name to it.description })
        add(CollectionConst.DICT_RFID_READ_CLASSIFICATION, "RFID 读取分类",
            EnumRfidReadClassification.entries.map { it.name to it.description })
        add(CollectionConst.DICT_RFID_PASSAGE_SOURCE_TYPE, "RFID 通行授权来源", listOf(
            "COLLECTION_LOAN" to "借展", "COLLECTION_REQUISITION" to "提用", "COLLECTION_RETURN" to "归还"))
        add(CollectionConst.DICT_RFID_PASSAGE_GRANT_STATUS, "RFID 通行授权状态",
            EnumRfidPassageGrantStatus.entries.map { it.name to it.description })
        add(CollectionConst.DICT_RFID_WHITELIST_SYNC_STATUS, "RFID 白名单同步状态",
            EnumRfidWhitelistSyncStatus.entries.map { it.name to it.description })
        add(CollectionConst.DICT_RFID_PASSAGE_DECISION, "RFID 通行判定",
            EnumRfidPassageDecision.entries.map { it.name to it.description })
        add(CollectionConst.DICT_RFID_ALARM_STATUS, "RFID 报警执行状态",
            EnumRfidAlarmStatus.entries.map { it.name to it.description })
        add(CollectionConst.DICT_RFID_ANOMALY_TYPE, "RFID 异常类型",
            EnumRfidAnomalyType.entries.map { it.name to it.description })
        add(CollectionConst.DICT_RFID_ANOMALY_STATUS, "RFID 异常状态",
            EnumRfidAnomalyStatus.entries.map { it.name to it.description })
        return dicts
    }

    override fun initData() {
        // 单馆：首装一个默认收藏单位（多馆由管理员自建）
        if (mode.equals("single", ignoreCase = true) && CollectionOrgDao.all().count() == 0L) {
            CollectionOrgDao.new {
                code = "DEFAULT"
                name = "默认收藏单位"
                orgStatus = EnumOrgStatus.ENABLED
                parentId = "0"
                parentIds = "0,"
                isLeaf = true
            }
        }
        CollectionOrgCodeRegistry.seedCurrentCodes()
        seedRoles()
        seedCategories()
        seedSystemConfigs()
        flowImporter?.importAll()
        fileIdentityUpgrade?.upgrade()
    }

    override fun update(version: String) {
        CollectionBusinessTerminologyUpgrade.apply()
        backfillAcquireDateRanges()
        annotateUnverifiedLegacyRegistrationLogs()
        backfillLegacyRfidTags()
        normalizePendingReturnExecutionState()
        CollectionOrgCodeRegistry.seedCurrentCodes()
        seedRoles()
        seedCategories()
        seedSystemConfigs()
        flowImporter?.importAll()
        fileIdentityUpgrade?.upgrade()
    }


    /**
     * 把历史 Collections.rfidTag 幂等迁入标签生命周期表。只迁移可证明为 EPC 的偶数长度十六进制值；
     * 格式异常或已被同馆其他藏品占用的数据保持原状，留给上线前数据审计处理。
     */
    private fun backfillLegacyRfidTags() {
        val now = LocalDateTime.now()
        Collections.selectAll().where { Collections.rfidTag.isNotNull() }.forEach { collection ->
            val epc = collection[Collections.rfidTag]?.replace(" ", "")?.uppercase()?.takeIf {
                it.length in 2..128 && it.length % 2 == 0 && it.matches(Regex("[0-9A-F]+"))
            } ?: return@forEach
            val orgId = collection[Collections.orgId]
            val collectionId = collection[Collections.id].value
            val existingForCollection = RfidTags.selectAll().where {
                (RfidTags.orgId eq orgId) and
                    (RfidTags.collectionId eq collectionId) and
                    (RfidTags.tagStatus eq EnumRfidTagStatus.ACTIVE)
            }.limit(1).any()
            if (existingForCollection) return@forEach
            val epcOccupied = RfidTags.selectAll().where {
                (RfidTags.orgId eq orgId) and (RfidTags.epc eq epc)
            }.limit(1).any()
            if (epcOccupied) return@forEach
            val tagId = IdGenerate.nextId()
            RfidTags.insert {
                it[id] = tagId
                it[RfidTags.orgId] = orgId
                it[RfidTags.epc] = epc
                it[RfidTags.collectionId] = collectionId
                it[tagStatus] = EnumRfidTagStatus.ACTIVE
                it[issuedAt] = collection[Collections.createTime]
                it[issuedBy] = "MIGRATION"
                it[activatedAt] = collection[Collections.updateTime]
                it[version] = 0
                it[createTime] = now
                it[createBy] = "MIGRATION"
                it[updateTime] = now
                it[updateBy] = "MIGRATION"
            }
            RfidTagOperations.insert {
                it[id] = IdGenerate.nextId()
                it[RfidTagOperations.orgId] = orgId
                it[RfidTagOperations.tagId] = tagId
                it[operationType] = EnumRfidTagOperationType.MIGRATED
                it[operatorId] = "MIGRATION"
                it[afterEpc] = epc
                it[success] = true
                it[resultCode] = "LEGACY_RFID_TAG_BACKFILL"
                it[detail] = "从 col_collection.rfid_tag 幂等回填"
                it[occurredAt] = now
            }
        }
    }

    /** 新字段默认兼容历史已执行 PASS 数据；所有非 PASS 历史单据统一归一为待现场执行。 */
    private fun normalizePendingReturnExecutionState() {
        CollectionReturns.update({ CollectionReturns.flowStatus neq io.hizhengfu.cloud.common.domain.EnumFlowStatus.PASS }) {
            it[executionState] = EnumReturnExecutionState.PENDING
            it[executedAt] = null
            it[executedBy] = null
        }
    }

    /** 新增范围字段后，用既有精确取得日期幂等回填历史藏品，并在同一升级事务留痕。 */
    internal fun backfillAcquireDateRanges() {
        Collections.selectAll()
            .where {
                (Collections.acquireDateRange.isNull() or Collections.acquireYear.isNull()) and
                    Collections.acquireDate.isNotNull()
            }
            .forEach { row ->
                val acquireDate = row[Collections.acquireDate] ?: return@forEach
                val changes = buildList {
                    if (row[Collections.acquireDateRange] == null) add("acquireDateRange" to acquireDate.toString())
                    if (row[Collections.acquireYear] == null) add("acquireYear" to acquireDate.year.toString())
                }
                Collections.update({ Collections.id eq row[Collections.id] }) {
                    if (row[Collections.acquireDateRange] == null) {
                        it[Collections.acquireDateRange] = acquireDate.toString()
                    }
                    if (row[Collections.acquireYear] == null) {
                        it[Collections.acquireYear] = acquireDate.year.toString()
                    }
                }
                changes.forEach { (fieldName, value) ->
                    CollectionLedgerLogDao.new {
                        orgId = row[Collections.orgId]
                        collectionId = row[Collections.id].value
                        totalRegNo = row[Collections.totalRegNo]
                        action = EnumLedgerAction.CORRECT
                        field = fieldName
                        before = null
                        after = value
                        reason = "系统升级依据原精确取得日期自动回填；待与原始凭证及纸账核对"
                        createBy = "SYSTEM_COLLECTION_INSTALL"
                    }
                }
            }
    }

    /** 旧版把预留号写成 REGISTER 的记录保留原貌，追加待核实说明，不补造正式登记日期。 */
    internal fun annotateUnverifiedLegacyRegistrationLogs() {
        val legacy = CollectionLedgerLogs.join(
            Collections, JoinType.INNER, CollectionLedgerLogs.collectionId, Collections.id,
        ).select(CollectionLedgerLogs.id, CollectionLedgerLogs.orgId, CollectionLedgerLogs.collectionId,
            CollectionLedgerLogs.totalRegNo)
            .where {
                (CollectionLedgerLogs.action eq EnumLedgerAction.REGISTER) and
                    Collections.isUnregistered()
            }.toList()
        legacy.forEach { source ->
            val sourceId = source[CollectionLedgerLogs.id].value
            val alreadyAnnotated = CollectionLedgerLogs.selectAll().where {
                (CollectionLedgerLogs.action eq EnumLedgerAction.CORRECT) and
                    (CollectionLedgerLogs.field eq LEGACY_REGISTRATION_CLAIM_FIELD) and
                    (CollectionLedgerLogs.refBizId eq sourceId)
            }.limit(1).any()
            if (alreadyAnnotated) return@forEach
            CollectionLedgerLogDao.new {
                orgId = source[CollectionLedgerLogs.orgId]
                collectionId = source[CollectionLedgerLogs.collectionId]
                totalRegNo = source[CollectionLedgerLogs.totalRegNo]
                action = EnumLedgerAction.CORRECT
                field = LEGACY_REGISTRATION_CLAIM_FIELD
                before = EnumLedgerAction.REGISTER.name
                after = "UNVERIFIED"
                reason = "历史日志记为登记入账，但主档缺正式登记时间；须核对核收、编目及纸质原账，原日志保留"
                refBizId = sourceId
                createBy = "SYSTEM_COLLECTION_INSTALL"
            }
        }
    }

    private companion object {
        const val LEGACY_REGISTRATION_CLAIM_FIELD = "legacyRegistrationClaim"
    }

    /**
     * 藏品模块系统配置（管理台「系统配置 → 藏品管理」，免重启）。
     * 自动登账已停用：升级时强制关闭历史开关，确保所有征集藏品都经过入馆凭证。
     */
    private fun seedSystemConfigs() {
        io.hizhengfu.cloud.collection.common.CollectionDefaultOrgSettings.seed()
        val name = CollectionConst.CONFIG_ACQUISITION_AUTO_REGISTER
        fun applySchema(it: org.jetbrains.exposed.v1.core.statements.UpdateBuilder<*>) {
            it[Configs.fieldType] = "BOOLEAN"
            it[Configs.module] = CollectionConst.CONFIG_MODULE
            it[Configs.fieldGroup] = "藏品管理"
            it[Configs.label] = "征集审批通过自动登账（已停用）"
            it[Configs.required] = false
            it[Configs.sort] = 0
            it[Configs.enable] = false
            it[Configs.content] = "FALSE"
            it[Configs.remarks] = "已停用：征集审批通过后进入待入馆池，必须由入馆凭证完成登账及实物入库流水"
        }
        val exists = Configs.selectAll().where { Configs.name eq name }.count() > 0
        if (!exists) {
            Configs.insert {
                it[Configs.name] = name
                it[enable] = false
                it[isPrivate] = true
                it[content] = "FALSE"
                it[remarks] = "已停用：征集审批通过后进入待入馆池，必须由入馆凭证完成登账及实物入库流水"
                applySchema(it)
            }
        } else {
            Configs.update({ Configs.name eq name }) { applySchema(it) }
        }
    }

    /** 种子化藏品流程审批角色（供流程节点 ROLE assignees 解析；用户分配到角色为部署配置）。 */
    private fun seedRoles() {
        val roles = listOf(
            Triple("1986600000000001001", CollectionConst.ROLE_KEEPER, "保管员"),
            Triple("1986600000000001002", CollectionConst.ROLE_STORAGE_HEAD, "保管部主任"),
            Triple("1986600000000001003", CollectionConst.ROLE_VICE_DIRECTOR, "分管馆长"),
            Triple("1986600000000001004", CollectionConst.ROLE_APPRAISAL_COMMITTEE, "鉴定委员会"),
            Triple("1986600000000001005", CollectionConst.ROLE_DIRECTOR, "法定代表人或主要负责人"),
            Triple("1986600000000001006", CollectionConst.ROLE_DEPARTMENT_HEAD, "提用部门负责人"),
            Triple("1986600000000001007", CollectionConst.ROLE_LEDGER_CUSTODIAN, "总登记账专管人员"),
            Triple("1986600000000001008", CollectionConst.ROLE_FINANCE, "文物资源资产财务核对人员"),
            Triple("1986600000000001009", CollectionConst.ROLE_INFORMATION, "信息化部门数字资源责任人员"),
            Triple("1986600000000001010", CollectionConst.ROLE_CONSERVATION, "保护修复部门责任人员"),
            Triple("1986600000000001011", CollectionConst.ROLE_SECURITY, "安全保卫部门责任人员"),
        )
        roles.forEachIndexed { idx, (roleId, code, name) ->
            if (Roles.selectAll().where { Roles.code eq code }.count() > 0L) return@forEachIndexed
            Roles.insert {
                it[id] = EntityID(roleId, Roles)
                it[Roles.code] = code
                it[Roles.name] = name
                it[remarks] = "藏品管理流程角色：$name"
                it[enabled] = true
                it[level] = idx
            }
        }
    }

    /** 按用户指定规范固定为附录D的35类，首装与版本升级共用同一目录。 */
    private fun seedCategories() = CollectionCategoryInitializer.apply()
}
