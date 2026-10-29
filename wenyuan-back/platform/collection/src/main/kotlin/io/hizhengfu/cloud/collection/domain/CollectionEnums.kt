package io.hizhengfu.cloud.collection.domain

/** 藏品级别 */
enum class EnumRelicLevel { FIRST, SECOND, THIRD, GENERAL, UNRATED }

/** 来源方式 */
enum class EnumSource { EXCAVATION, COLLECT, PURCHASE, ALLOCATE, TRANSFER, EXCHANGE, DONATE, HANDOVER, PICK, OLD, REPRO, DESIGNATED }

/** 完残程度 */
enum class EnumCompleteness { COMPLETE, BASIC, DAMAGED, SEVERE }

/** 保存状态 */
enum class EnumPreserve { A, B, C }

/** 藏品业务状态（生命周期状态机） */
// Exposed 默认按 ordinal 存储，只能在尾部追加，避免改变历史数据含义。
enum class EnumCollectionStatus { PENDING, IN_STORE, IN_USE, RESTORING, ON_LOAN, MISSING, DEREGISTERED, PRE_OUT, RETURN_HOLD }

/**
 * 编目工作状态。
 *
 * Exposed 按 ordinal 持久化，历史值 PENDING/APPROVED/REJECTED 的顺序不得调整；
 * DRAFT/SUBMITTED 为独立编目流程新增状态，PENDING 仅兼容升级前“创建即待审”的数据。
 */
enum class EnumCatalogStatus { PENDING, APPROVED, REJECTED, DRAFT, SUBMITTED }

/**
 * 藏品位置节点类型。
 *
 * Exposed 的历史字段按 ordinal 持久化，原有五项顺序不得调整；展陈位置类型只能尾部追加。
 */
enum class EnumWarehouseType {
    WAREHOUSE, AREA, CABINET, LAYER, BOX,
    EXHIBITION_HALL, DISPLAY_AREA, SHOWCASE,
}

/** 位置使用场景；库房与展陈位置共用同一棵位置树。 */
enum class EnumLocationScene { STORAGE, EXHIBITION }

/** 出入库类型 */
enum class EnumMoveType { IN, OUT }

/** 出入库事由 */
// ordinal 持久化：新增事由只能追加，保留历史清点和应急搬移含义。
enum class EnumMoveReason { RESEARCH, EXHIBIT, RESTORE, LOAN, DIGITIZE, INVENTORY, TRANSFER, ACQUISITION, EMERGENCY, RELOCATION, OTHER }

/** 总账动作（ordinal 存储：只能尾部追加） */
enum class EnumLedgerAction { REGISTER, CORRECT, MERGE, SPLIT, DEREGISTER, AUDIT, RESERVE_REG_NO }

/** 总登记号占用状态 */
enum class EnumRegistrationNoState { USED, RETIRED }

/** 退出馆藏原因 */
enum class EnumDeregReason {
    DAMAGE, LOSS, THEFT, RETURN, MISJUDGE, NATURAL, TRANSFER,
    DAMAGE_NO_VALUE, OTHER_BELOW_STANDARD, FORCE_MAJEURE, STATE_EXCHANGE_TRANSFER, OTHER_LEGAL,
}

/** 正式藏品的法定性质；辅助账册独立管理，不能作为正式藏品的类型选项。 */
// ordinal 持久化：原有 RELIC=0、OTHER=1 保持不变。第61条化石单独识别，不推定所有化石都属于此类。
enum class EnumCollectionNature { RELIC, OTHER, SCIENTIFIC_FOSSIL }

/** 审批层级 */
enum class EnumApproveLevel { MUSEUM, PROVINCE, NATIONAL }

/** 套件关系 */
enum class EnumSetRelation { MAIN, ATTACH, PART }

/** 鉴定组成员职责。 */
enum class EnumAppraisalMemberRole { LEADER, EXPERT, MEMBER }

/** 提用单状态 */
enum class EnumRequisitionState { PENDING, OUT, RETURNED, OVERDUE }

/** 收藏单位级别 */
enum class EnumOrgLevel { NATIONAL_1, NATIONAL_2, NATIONAL_3, UNRATED }

/** 举办主体 */
enum class EnumOwnership { STATE, NON_STATE }

/** 收藏单位状态 */
enum class EnumOrgStatus { ENABLED, DISABLED }

/** 申请单审批状态（退出馆藏 / 提用 等，状态机驱动，申请≠审批） */
enum class EnumApplyStatus { APPLYING, APPROVED, REJECTED, EXECUTED }

/** 复制/拓印/影像采集类型 */
enum class EnumRepType { COPY, RUBBING, SCAN_3D, PHOTO }

/** 征集计划周期 */
enum class EnumPlanPeriod { YEAR, MONTH }

/** 入馆凭证状态 */
enum class EnumVoucherStatus { DRAFT, REGISTERED, RECEIVED }

/** 盘点结果 */
enum class EnumInventoryResult { MATCH, SURPLUS, MISSING, ABNORMAL }

/** 业务过程通用状态（修复/借展等） */
enum class EnumProcessState { DRAFT, ONGOING, DONE, CANCELLED }

/** 藏品影像类型 */
@tools.jackson.databind.annotation.JsonDeserialize(using = CollectionMediaTypeDeserializer::class)
enum class EnumCollectionMediaType {
    FRONT,
    BACK,
    DETAIL,
    INSCRIPTION,
    RUBBING,
    LINE_DRAWING,
    THREE_D,
    VIDEO,
    RESTORATION_BEFORE,
    RESTORATION_AFTER,
    RESTORATION_PROCESS,
    OTHER,
    TOP, SIDE, PANORAMIC, BOTTOM,
}

/** 藏品附件类型 */
enum class EnumCollectionAttachmentType {
    REGISTRATION,
    ACQUISITION_CONTRACT,
    APPRAISAL_OPINION,
    RESTORATION_PLAN,
    LOAN_AGREEMENT,
    DEREG_APPROVAL,
    MOVEMENT_RECEIPT,
    INVENTORY_RECORD,
    OTHER,
}

/** 打印表单类型（ordinal 存储：只能尾部追加） */
enum class EnumCollectionPrintType {
    COLLECTION_ARCHIVE,
    MOVEMENT,
    REQUISITION,
    LOAN_HANDOVER,
    DEREGISTRATION,
    INVENTORY,
    APPRAISAL,
    TRANSFER,
    RESTORATION,
    ACQUISITION,
    REPRODUCTION,
    EXHIBITION,
    MOUNTING,
    ENTRY_VOUCHER,
}

// 修复相关枚举已迁移至 module-collection-restoration
// io.hizhengfu.cloud.collection.restoration.domain.RestorationEnums
// 保留在此的是跨模块共用枚举（EnumProcessState 用于修复+展览+借展）
