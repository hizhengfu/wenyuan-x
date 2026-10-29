package io.hizhengfu.cloud.collection.common

/**
 * 藏品管理常量：缓存区 / 权限码 / 字典 type / 流程 businessType。
 * ID 段（菜单/权限）选用 105xx / 40 段，避开 sys、message、ticket 已用段。
 */
object CollectionConst {
    const val STAFF_EDUCATION_VIEW = "COLLECTION_STAFF_EDUCATION_VIEW"
    const val STAFF_EDUCATION_MANAGE = "COLLECTION_STAFF_EDUCATION_MANAGE"
    // ── 缓存区 ──
    const val CACHE = "COLLECTION"
    const val CACHE_CATALOG = "COLLECTION_CATALOG"
    const val CACHE_ORG = "COLLECTION_ORG"
    const val CACHE_CATEGORY = "COLLECTION_CATEGORY"
    const val CACHE_WAREHOUSE = "COLLECTION_WAREHOUSE"
    // ── 权限码 ──
    const val ADMIN = "COLLECTION_ADMIN"
    const val SELECT = "COLLECTION_SELECT"
    const val LEDGER_CREATE = "COLLECTION_LEDGER_CREATE"
    const val LEDGER_CORRECT = "COLLECTION_LEDGER_CORRECT"
    const val LEDGER_FINANCE = "COLLECTION_LEDGER_FINANCE"
    const val ARCHIVE_MANAGE = "COLLECTION_ARCHIVE_MANAGE"
    const val ARCHIVE_SELECT = "COLLECTION_ARCHIVE_SELECT"
    const val STORAGE = "COLLECTION_STORAGE"
    const val APPRAISE = "COLLECTION_APPRAISE"
    const val DEREGISTER = "COLLECTION_DEREGISTER"
    const val DEREGISTER_APPROVE = "COLLECTION_DEREGISTER_APPROVE"
    const val STAT = "COLLECTION_STAT"
    const val PUBLISH = "COLLECTION_PUBLISH"
    const val CROSS_ORG = "COLLECTION_CROSS_ORG"
    const val ORG_MANAGE = "COLLECTION_ORG_MANAGE"
    const val CATEGORY_MANAGE = "COLLECTION_CATEGORY_MANAGE"
    const val WAREHOUSE_MANAGE = "COLLECTION_WAREHOUSE_MANAGE"
    const val CATALOG_EDIT = "COLLECTION_CATALOG_EDIT"
    const val CATALOG_AUDIT = "COLLECTION_CATALOG_AUDIT"
    const val WAREHOUSE_ENTRY_APPLY = "COLLECTION_WAREHOUSE_ENTRY_APPLY"
    const val WAREHOUSE_ENTRY_VERIFY = "COLLECTION_WAREHOUSE_ENTRY_VERIFY"
    const val RFID_DESK = "COLLECTION_RFID_DESK"
    const val RFID_SCAN = "COLLECTION_RFID_SCAN"
    const val RFID_FINALIZE = "COLLECTION_RFID_FINALIZE"
    const val RFID_OVERRIDE = "COLLECTION_RFID_OVERRIDE"
    const val RFID_TAG_SELECT = "COLLECTION_RFID_TAG_SELECT"
    const val RFID_TAG_WRITE = "COLLECTION_RFID_TAG_WRITE"
    const val RFID_TAG_RETIRE = "COLLECTION_RFID_TAG_RETIRE"
    const val RFID_PASSAGE = "COLLECTION_RFID_PASSAGE"
    const val RFID_ANOMALY_PROCESS = "COLLECTION_RFID_ANOMALY_PROCESS"
    const val RFID_CARD_MANAGE = "COLLECTION_RFID_CARD_MANAGE"

    // ── 字典 type（须与前端 HDictSelect type 一致）──
    const val DICT_RELIC_LEVEL = "col_relic_level"
    const val DICT_SOURCE = "col_source"
    const val DICT_TEXTURE = "col_texture"
    const val DICT_COMPLETENESS = "col_completeness"
    const val DICT_PRESERVE = "col_preserve"
    const val DICT_LIFE_STATUS = "col_life_status"
    const val DICT_CATALOG_STATUS = "col_catalog_status"
    const val DICT_ORG_LEVEL = "col_org_level"
    const val DICT_OWNERSHIP = "col_ownership"
    const val DICT_WAREHOUSE_TYPE = "col_warehouse_type"
    const val DICT_STORAGE_LOCATION_TYPE = "col_storage_location_type"
    const val DICT_EXHIBITION_LOCATION_TYPE = "col_exhibition_location_type"
    const val DICT_LOCATION_SCENE = "col_location_scene"
    const val DICT_MOVE_TYPE = "col_move_type"
    const val DICT_MOVE_REASON = "col_move_reason"
    const val DICT_DEREG_REASON = "col_dereg_reason"
    const val DICT_APPROVE_LEVEL = "col_approve_level"
    const val DICT_REP_TYPE = "col_rep_type"
    const val DICT_INVENTORY_RESULT = "col_inventory_result"
    const val DICT_SET_RELATION = "col_set_relation"
    const val DICT_MEDIA_TYPE = "col_media_type"
    const val DICT_ATTACHMENT_TYPE = "col_attachment_type"
    const val DICT_PRINT_TYPE = "col_print_type"
    const val DICT_APPLY_STATUS = "col_apply_status"
    const val DICT_ORG_STATUS = "col_org_status"
    const val DICT_PROCESS_STATE = "col_process_state"
    const val DICT_PRESENCE = "col_presence"
    const val DICT_RETURNED = "col_returned"
    const val DICT_REGISTERED = "col_registered"
    const val DICT_REQUIRED = "col_required"
    const val DICT_CATEGORY_SCOPE = "col_category_scope"
    const val DICT_LEDGER_ACTION = "col_ledger_action"
    const val DICT_PLAN_PERIOD = "col_plan_period"
    const val DICT_VOUCHER_STATUS = "col_voucher_status"
    const val DICT_RFID_WORKSTATION_CAPABILITY = "col_rfid_workstation_capability"
    const val DICT_RFID_WORKSTATION_DEVICE_PURPOSE = "col_rfid_workstation_device_purpose"
    const val DICT_RFID_WORKSTATION_STATUS = "col_rfid_workstation_status"
    const val DICT_RFID_OPERATOR_CARD_STATUS = "col_rfid_operator_card_status"
    const val DICT_RFID_TAG_STATUS = "col_rfid_tag_status"
    const val DICT_RFID_TAG_OPERATION_TYPE = "col_rfid_tag_operation_type"
    const val DICT_RFID_OPERATION_RESULT = "col_rfid_operation_result"
    const val DICT_RFID_TAG_JOB_STATUS = "col_rfid_tag_job_status"
    const val DICT_RFID_SCAN_SESSION_TYPE = "col_rfid_scan_session_type"
    const val DICT_RFID_SCAN_SESSION_STATE = "col_rfid_scan_session_state"
    const val DICT_RFID_DIRECTION = "col_rfid_direction"
    const val DICT_RFID_READ_CLASSIFICATION = "col_rfid_read_classification"
    const val DICT_RFID_PASSAGE_SOURCE_TYPE = "col_rfid_passage_source_type"
    const val DICT_RFID_PASSAGE_GRANT_STATUS = "col_rfid_passage_grant_status"
    const val DICT_RFID_WHITELIST_SYNC_STATUS = "col_rfid_whitelist_sync_status"
    const val DICT_RFID_PASSAGE_DECISION = "col_rfid_passage_decision"
    const val DICT_RFID_ALARM_STATUS = "col_rfid_alarm_status"
    const val DICT_RFID_ANOMALY_TYPE = "col_rfid_anomaly_type"
    const val DICT_RFID_ANOMALY_STATUS = "col_rfid_anomaly_status"

    const val CACHE_ACQUISITION = "COLLECTION_ACQUISITION"

    // ── 流程 businessType / definitionKey ──
    const val BIZ_CHANGE = "COLLECTION_CHANGE"
    const val FLOW_CHANGE = "collection_change"
    const val BIZ_ACQUISITION = "COLLECTION_ACQUISITION"
    const val FLOW_ACQUISITION = "collection_acquisition_2026"
    const val BIZ_DEREGISTER = "COLLECTION_DEREGISTER"
    const val FLOW_DEREGISTER = "collection_deregister_2026"
    const val BIZ_LOAN = "COLLECTION_LOAN"
    const val FLOW_LOAN = "collection_loan_2026"
    const val BIZ_TRANSFER = "COLLECTION_TRANSFER"
    const val FLOW_TRANSFER = "collection_transfer_2026"
    const val BIZ_REQUISITION = "COLLECTION_REQUISITION"
    const val FLOW_REQUISITION = "collection_requisition_2026"
    const val BIZ_RETURN = "COLLECTION_RETURN"
    const val FLOW_RETURN = "collection_return_2026"
    const val BIZ_APPRAISAL = "COLLECTION_APPRAISAL"
    const val FLOW_APPRAISAL = "collection_appraisal_2026"
    const val BIZ_EXHIBITION = "COLLECTION_EXHIBITION"
    const val FLOW_EXHIBITION = "collection_exhibition"
    const val BIZ_REPRODUCTION = "COLLECTION_REPRODUCTION"
    const val BIZ_INCIDENT = "COLLECTION_INCIDENT"
    const val BIZ_INCIDENT_RELOCATION = "COLLECTION_INCIDENT_RELOCATION"
    const val BIZ_INCIDENT_WORK = "COLLECTION_INCIDENT_WORK"
    const val INCIDENT = "COLLECTION_INCIDENT_MANAGE"
    const val BIZ_SAMPLING = "COLLECTION_SAMPLING"
    const val BIZ_CARE = "COLLECTION_CARE"
    const val BIZ_FRAGILITY = "COLLECTION_FRAGILITY"
    const val SAFETY_REMINDER = "COLLECTION_SAFETY_REMINDER_MANAGE"
    const val EMERGENCY = "COLLECTION_EMERGENCY_MANAGE"
    const val CARE = "COLLECTION_CARE_MANAGE"
    const val FLOW_SAMPLING = "collection_sampling_2026"
    const val SAMPLING = "COLLECTION_SAMPLING_MANAGE"
    const val ROLE_CONSERVATION = "COLLECTION_CONSERVATION" // 保护修复部门责任人员
    const val FLOW_REPRODUCTION = "collection_reproduction"
    const val FLOW_DIGITAL_ACQUISITION_2026 = "collection_digital_acquisition_2026"
    const val FLOW_REPRODUCTION_2026 = "collection_reproduction_2026"
    const val BIZ_INVENTORY = "COLLECTION_INVENTORY"
    const val FLOW_INVENTORY = "collection_inventory"
    const val BIZ_ACQUISITION_PLAN = "COLLECTION_ACQUISITION_PLAN"
    const val FLOW_ACQUISITION_PLAN = "collection_acquisition_plan"
    const val BIZ_WAREHOUSE_ENTRY = "COLLECTION_WAREHOUSE_ENTRY"
    const val FLOW_WAREHOUSE_ENTRY = "collection_warehouse_entry_2026"

    /** 征集权限码 */
    const val ACQUIRE = "COLLECTION_ACQUIRE"
    const val ACQUIRE_CONFIDENTIAL_SELECT = "COLLECTION_ACQUIRE_CONFIDENTIAL_SELECT"

    // ── 系统配置键（sys_config，管理台「系统配置 → 藏品管理」，免重启）──
    /** 历史配置键；自动登账已停用，征集必须由入馆凭证完成登账。 */
    const val CONFIG_ACQUISITION_AUTO_REGISTER = "collection_acquisition_auto_register"
    const val CONFIG_MODULE = "collection"

    // ── 流程审批角色（CollectionInstall 种子化，流程节点 assignees 引用其 code）──
    const val ROLE_LEDGER_CUSTODIAN = "COLLECTION_LEDGER_CUSTODIAN" // 总登记账专管人员
    const val ROLE_SECURITY = "COLLECTION_SECURITY" // 安全保卫部门责任人员
    const val ROLE_INFORMATION = "COLLECTION_INFORMATION" // 信息化部门数字资源责任人员
    const val ROLE_FINANCE = "COLLECTION_FINANCE" // 文物资源资产财务核对人员
    const val ROLE_KEEPER = "COLLECTION_KEEPER"                       // 保管员
    const val ROLE_DIRECTOR = "COLLECTION_DIRECTOR"                  // 法定代表人或主要负责人
    const val ROLE_DEPARTMENT_HEAD = "COLLECTION_DEPARTMENT_HEAD"           // 提用部门负责人
    const val ROLE_STORAGE_HEAD = "COLLECTION_STORAGE_HEAD"           // 保管部主任
    const val ROLE_VICE_DIRECTOR = "COLLECTION_VICE_DIRECTOR"         // 分管馆长
    const val ROLE_APPRAISAL_COMMITTEE = "COLLECTION_APPRAISAL_COMMITTEE" // 鉴定委员会

    // 修复相关权限已迁移至 io.hizhengfu.cloud.collection.restoration.common.RestorationConst
}
