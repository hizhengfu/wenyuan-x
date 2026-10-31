import type { CollectionNature } from '../utils/collectionNature';
/**
 * 藏品管理 —— 类型定义（对应后端 io.hizhengfu.cloud.collection 的 VO）
 * 设计基线：docs/superpowers/specs/2026-06-21-collection-management-design.md
 */

/** 藏品级别 EnumRelicLevel */
export type RelicLevel = 'FIRST' | 'SECOND' | 'THIRD' | 'GENERAL' | 'UNRATED';
/** 藏品业务状态 EnumCollectionStatus（生命周期状态机） */
export type LifeStatus =
  | 'PENDING' // 待定
  | 'IN_STORE' // 在库
  | 'IN_USE' // 提用中
  | 'RESTORING' // 修复中
  | 'ON_LOAN' // 借出
  | 'MISSING' // 盘亏
  | 'DEREGISTERED' // 已退出馆藏（终态）
  | 'PRE_OUT'
  | 'RETURN_HOLD'; // 审批通过、待现场交接出库

/** 藏品档案主记录（总账 + 分类账 + 一物一档） */
export interface CollectionRegistrationDetailsVo {
  paperLedgerBookNo?: string; paperLedgerPage?: string;
  appraisalInstitution?: string; appraisers?: string; appraisedOn?: number | null;
  coverIsFrontView?: boolean;
  eraMeaning?: string; eraNotation?: string;
  dimensionUnknownReason?: string; weightUnknownReason?: string; quantityUnknownReason?: string;
  weightExcludesAccessories?: boolean; measurementMethod?: string; measuredOn?: number | null; measuredBy?: string;
  parts?: { partNo?: string; name?: string; dimensions?: Record<string, string>; dimensionUnit?: string; weight?: string; weightUnit?: string; includesAccessories?: boolean; remark?: string; dimensionUnknownReason?: string; weightUnknownReason?: string }[];
  provenance?: { from?: number | null; to?: number | null; periodDescription?: string; institution?: string; collector?: string; originalNumber?: string; evidence?: string; remark?: string }[];
  excavations?: { date?: number | null; periodDescription?: string; site?: string; position?: string; institution?: string; evidence?: string }[];
  publications?: { publishedOn?: number | null; periodDescription?: string; publisher?: string; title?: string; author?: string; chapter?: string; pages?: string; summary?: string; remark?: string }[];
  manufacture?: { period?: string; place?: string; technique?: string; institution?: string; maker?: string; remark?: string }[];
}
export interface CollectionVo {
  actualQuantity?: number | null;
  actualQuantityUnit?: string;
  dimensionUnit?: string;
  entryDate?: number | null;
  receiptCompletedAt?: number | null;
  registrationDetails?: CollectionRegistrationDetailsVo;
  inventoryHold?: boolean;
  id?: string;
  orgId?: string;
  /** 总登记号（按馆唯一、不可重用；确定后永久不变，不提供改号入口） */
  totalRegNo?: string;
  /** 分类号 */
  classNo?: string;
  /** 辅助编号 / 原藏号 */
  auxNo?: string;
  name?: string;
  /** 原名 */
  originalName?: string;
  categoryId?: string;
  categoryName?: string;
  /** 年代 / 断代 */
  era?: string;
  /** 具体质地，col_texture 提供常用选项，也可填写组合材质 */
  texture?: string;
  /** 规范质地类别与完整细类路径 */
  textureCategory1?: string;
  textureCategory2?: string;
  /** 藏品级别（字典 col_relic_level） */
  nature?: CollectionNature;
  level?: RelicLevel | string;
  quantity?: number;
  /** 计量单位（件/套） */
  unit?: string;
  /** 来源方式（字典 col_source） */
  source?: string;
  sourceDesc?: string;
  /** 取得日期，后端按毫秒时间戳返回/接收 */
  acquireDate?: number | null;
  /** 完残程度（字典 col_completeness） */
  completeness?: string;
  completeStatus?: string;
  /** 尺寸 {通高,口径,底径,腹径,...} */
  dimensions?: Record<string, any>;
  weight?: string;
  weightRange?: string;
  weightUnit?: string;
  /** 地域 */
  region?: string;
  /** 容积 */
  volume?: string;
  volumeUnit?: string;
  /** 色泽 */
  color?: string;
  /** 来源号 */
  sourceNo?: string;
  /** 国别 */
  country?: string;
  /** 人文类型 */
  culture?: string;
  /** RFID 标签号（馆内唯一，经 bindRfid/unbindRfid 维护） */
  rfidTag?: string;
  /** 入馆凭证号（入馆凭证登账时回写） */
  entryVoucherNo?: string;
  /** 退出馆藏凭证号/批文号（退出馆藏执行时回写） */
  deregVoucherNo?: string;
  /** 铭文款识 */
  inscription?: string;
  /** 特征描述 */
  feature?: string;
  /** 保存状态（字典 col_preserve） */
  preserveState?: string;
  /** 取得时间范围，保留原始文本表达，如 1949.10.1-1965 */
  acquireDateRange?: string;
  acquireYear?: string;
  author?: string;
  edition?: string;
  volumeNo?: string;
  /** 估价：接口单位为分，界面单位为元 */
  valuation?: number;
  /** 封面影像（fileId / url） */
  coverImage?: string;
  previewImage?: string;
  /** 当前库位 */
  warehouseId?: string;
  warehouseName?: string;
  /** 编目审核状态（字典 col_catalog_status） */
  catalogStatus?: string;
  catalogAuditOpinion?: string;
  catalogAuditBy?: string;
  catalogAuditTime?: string;
  /** 藏品业务状态（字典 col_life_status） */
  lifeStatus?: LifeStatus | string;
  remark?: string;
  registeredAt?: string;
  historicalConfirmedAt?: number | null;
  registered?: boolean;
  createTime?: string;
  createBy?: string;
  mediaCount?: number;
  attachmentCount?: number;
  /** 是否已单向发布为 cms 数字藏品 */
  published?: boolean;
  publishedAt?: string;
}

export type CatalogStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'DRAFT' | 'SUBMITTED';

export interface CatalogAccessoryVo {
  kind?: string;
  name?: string;
  condition?: string;
  quantity?: number | null;
  unit?: string;
  remark?: string;
}

/** 编目工作副本；审核提交后由后端固化为不可覆盖的版本快照。 */
export interface CollectionCatalogContentVo {
  entryDate?: number | null;
  receiptCompletedAt?: number | null;
  registrationDetails?: CollectionRegistrationDetailsVo;
  registrationSubNo?: string;
  classificationNo?: string;
  originalNo?: string;
  totalName?: string;
  componentName?: string;
  formalName?: string;
  originalName?: string;
  categoryId?: string;
  comprehensiveCategory?: string;
  topicCategories?: string[];
  era?: string;
  region?: string;
  country?: string;
  culture?: string;
  cultureType?: string;
  textureCategory1?: string;
  textureCategory2?: string;
  texture?: string;
  color?: string;
  nature?: CollectionNature;
  level?: RelicLevel | string;
  appraisalOpinion?: string;
  quantity?: number | null;
  unit?: string;
  actualQuantity?: number | null;
  actualQuantityUnit?: string;
  dimensions?: Record<string, string>;
  dimensionUnit?: string;
  weightRange?: string;
  weight?: string;
  weightUnit?: string;
  volume?: string;
  volumeUnit?: string;
  completeness?: string;
  completeStatus?: string;
  preserveState?: string;
  source?: string;
  sourceNo?: string;
  sourceDesc?: string;
  acquireDate?: number | null;
  acquireDateRange?: string;
  acquireYear?: string;
  feature?: string;
  inscription?: string;
  colophon?: string;
  bibliography?: string;
  researchNote?: string;
  author?: string;
  edition?: string;
  volumeNo?: string;
  valuation?: number | null;
  copyrightOwner?: string;
  rightsRestrictions?: string;
  accessories?: CatalogAccessoryVo[];
  attachmentDescription?: string;
  coverImage?: string;
  warehouseId?: string;
  remark?: string;
}

export interface CatalogCompletenessVo {
  percent: number;
  missingFields: string[];
  complete: boolean;
}

export interface CollectionCatalogVo {
  receiptLinked?: boolean;
  historicalMigration?: boolean;
  migrationVerificationRequired?: boolean;
  id?: string;
  orgId?: string;
  collectionId?: string;
  totalRegNo?: string;
  collectionName?: string;
  categoryName?: string;
  warehouseName?: string;
  status?: CatalogStatus;
  version?: number;
  approvedVersion?: number | null;
  submittedVersion?: number | null;
  submittedBy?: string;
  submittedAt?: string;
  auditOpinion?: string;
  auditBy?: string;
  auditTime?: number | string;
  content: CollectionCatalogContentVo;
  completeness: CatalogCompletenessVo;
  mediaCount?: number;
  attachmentCount?: number;
  createTime?: string;
  updateTime?: number | string;
  orgName?: string;
  stateHash?: string;
  canEdit?: boolean;
  canReview?: boolean;
}

export interface CollectionCatalogVersionVo {
  id?: string;
  catalogId?: string;
  collectionId?: string;
  version: number;
  status?: CatalogStatus;
  content: CollectionCatalogContentVo;
  submittedBy?: string;
  submittedAt?: string;
  auditOpinion?: string;
  auditBy?: string;
  auditTime?: string;
}

export interface CollectionCatalogCardVo {
  entryVoucherNo?: string;
  registrationFields?: { label: string; value?: string; dictType?: string }[];
  catalog: CollectionCatalogVo;
  generatedAt?: string;
}

/** 总账留痕（append-only） */
export interface LedgerLogVo {
  id?: string;
  collectionId?: string;
  totalRegNo?: string;
  collectionName?: string;
  /** REGISTER / CORRECT / MERGE / SPLIT / DEREGISTER */
  action?: string;
  field?: string;
  before?: string;
  after?: string;
  reason?: string;
  refBizId?: string;
  createBy?: string;
  createByName?: string;
  createTime?: string;
}

/** 收藏单位（多馆基础） */
export interface CollectionOrgVo {
  codeStateHash?: string;
  exchangeCode?: string;
  exchangeCodeBasis?: string;
  id?: string;
  parentId?: string;
  parentName?: string;
  /** 本系统收藏单位代码；规范交换代码另行管理 */
  code?: string;
  name?: string;
  shortName?: string;
  /** 单位级别（字典 col_org_level） */
  level?: string;
  /** 国有/非国有（字典 col_ownership） */
  ownership?: string;
  legalPerson?: string;
  areaId?: string;
  address?: string;
  contact?: string;
  phone?: string;
  /** 一普机构编码（互通 archive 名录） */
  sourceId?: string;
  /** 授权机构（关联 sys_group.id），用于多馆业务归属和数据范围 */
  systemGroupId?: string | null;
  /** 关联物理安装位置根（逻辑关联 col_warehouse.id） */
  locationRootId?: string;
  locationRootName?: string;
  status?: string;
  sort?: number;
  createTime?: string;
}

export interface CollectionOrgImportRowVo {
  rowNo?: number;
  parentId?: string;
  parentName?: string;
  parentCode?: string;
  code?: string;
  name?: string;
  shortName?: string;
  level?: string;
  ownership?: string;
  legalPerson?: string;
  areaId?: string;
  areaName?: string;
  areaCode?: string;
  address?: string;
  contact?: string;
  phone?: string;
  sourceId?: string;
  locationRootId?: string;
  locationRootName?: string;
  status?: string;
  sort?: number;
}

/** 馆藏架构统计 */
export interface CollectionUsageItem {
  id: string;
  orgId: string;
  quantity?: number;
  unit?: string;
  onLoan: boolean;
  onExhibition: boolean;
  warehouseId?: string;
  inStore: boolean;
}
export interface CollectionUsageSummary {
  total: number;
  loanCount: number;
  exhibitionCount: number;
  quantityByUnit: Record<string, number>;
  loanQuantityByUnit: Record<string, number>;
  exhibitionQuantityByUnit: Record<string, number>;
  quantityUnverifiedTotal: number;
  items: CollectionUsageItem[];
}
export interface AcquisitionPlanOfflineApproval {
  approvedAt?: number;
  approvedBy?: string;
  approvalBasis?: string;
  signedFileIds?: string;
  supplementaryReason?: string;
}
export interface CollectionOrgOverviewStats {
  total: number;
  byLevel: Record<string, number>;
  byStatus: Record<string, number>;
  imageCount: number;
  attachmentCount: number;
}

/** 馆藏架构节点（主馆 / 分馆 + 直属 / 汇总统计） */
export interface CollectionOrgOverviewNode {
  id: string;
  parentId?: string | null;
  name: string;
  code?: string | null;
  direct: CollectionOrgOverviewStats;
  aggregate: CollectionOrgOverviewStats;
  children: CollectionOrgOverviewNode[];
}

export interface CollectionSummaryVo {
  id: string;
  totalRegNo?: string;
  name?: string;
}

/** 文物分类：WW/T 0017—2013 附录D固定35类。 */
export interface CategoryVo {
  id?: string;
  parentId?: string;
  parentName?: string;
  /** 规范目录为全馆共用，orgId为空。 */
  orgId?: string;
  code?: string;
  name?: string;
  sort?: number;
}

/** 退出馆藏申请（流程驱动） */
export interface DeregistrationVo {
  evidenceErrors?: string[];
  currentNoticeComplete?: boolean;
  id?: string;
  collectionId?: string;
  collectionTotalRegNo?: string;
  collectionName?: string;
  /** 退出馆藏原因（字典 col_dereg_reason） */
  reason?: string;
  detail?: string;
  /** 审批层级 馆级/省级/国家文物局（字典 col_approve_level） */
  approveLevel?: string;
  approvalDocNo?: string;
  expertOpinion?: string;
  approvedBy?: string;
  destination?: string;
  attachmentIds?: string;
  flowStatus?: string;
  flowInstanceId?: string;
  createTime?: string;
}

/** 库房库位（树） */
export interface WarehouseVo {
  staffing?: Record<string, any>;
  archives?: any[];
  id?: string;
  parentId?: string;
  orgId?: string;
  code?: string;
  name?: string;
  thumbnail?: string;
  /** STORAGE / EXHIBITION */
  sceneType?: string;
  /** 库位类型（字典 col_warehouse_type） */
  type?: string;
  capacity?: number;
  temperature?: string;
  custodian?: string;
  sort?: number;
}

export interface WarehouseImportRowVo {
  rowNo?: number;
  parentId?: string;
  parentName?: string;
  parentCode?: string;
  code?: string;
  name?: string;
  thumbnail?: string;
  thumbnailName?: string;
  sceneType?: string;
  type?: string;
  capacity?: number;
  temperature?: string;
  custodian?: string;
  sort?: number;
}

export interface WarehouseImportValidateResultVo {
  valid: boolean;
  errors: CollectionImportErrorVo[];
  items: WarehouseVo[];
}

/** 出入库流水 */
export interface MovementVo {
  id?: string;
  orgId?: string;
  documentNo?: string;
  /** 可用 RFID 标签替代藏品ID（服务端解析） */
  rfidTag?: string;
  collectionId?: string;
  totalRegNo?: string;
  collectionName?: string;
  /** IN / OUT */
  type?: 'IN' | 'OUT' | string;
  /** 出入库事由（字典 col_move_reason） */
  reason?: string;
  basis?: string;
  department?: string;
  fromWarehouseId?: string;
  toWarehouseId?: string;
  operator?: string;
  operatorName?: string;
  reviewer?: string;
  reviewerName?: string;
  custodian?: string;
  custodianName?: string;
  handoverUser?: string;
  handoverUserName?: string;
  reviewOpinion?: string;
  attachmentIds?: string;
  operatedAt?: number | null;
  refBizId?: string;
}

export interface MovementImportRowVo {
  rowNo?: number;
  documentNo?: string;
  collectionId?: string;
  totalRegNo?: string;
  type?: string;
  reason?: string;
  basis?: string;
  department?: string;
  fromWarehouseId?: string;
  toWarehouseId?: string;
  operator?: string;
  operatorName?: string;
  reviewer?: string;
  reviewerName?: string;
  custodian?: string;
  handoverUser?: string;
  reviewOpinion?: string;
  attachmentIds?: string;
  refBizId?: string;
}

export interface MovementImportValidateResultVo {
  valid: boolean;
  errors: CollectionImportErrorVo[];
  items: MovementVo[];
}

/** 藏品展览项目 */
export interface ExhibitionPlacementVo {
  collectionId?: string;
  locationId?: string;
  locationName?: string;
  validFrom?: string | null;
  validTo?: string | null;
}

export interface ExhibitionVo {
  id?: string;
  orgId?: string;
  title?: string;
  exhibitionNo?: string;
  venueId?: string;
  venue?: string;
  organizer?: string;
  collectionIds?: string;
  collectionSummaries?: CollectionSummaryVo[];
  placements?: ExhibitionPlacementVo[];
  coverImage?: string;
  attachmentIds?: string;
  startDate?: number | null;
  endDate?: number | null;
  state?: string;
  remark?: string;
  flowStatus?: string;
  flowInstanceId?: string;
  createTime?: string;
}

/** 鉴定 */
export interface AppraisalMemberVo {
  id?: string;
  appraisalId?: string;
  userId?: string;
  memberName?: string;
  memberRole?: 'LEADER' | 'EXPERT' | 'MEMBER' | string;
  organization?: string;
  professionalTitle?: string;
  specialty?: string;
  opinion?: string;
  signatureFileId?: string;
  sort?: number;
}

export interface AppraisalVo {
  id?: string;
  orgId?: string;
  collectionId?: string;
  collectionTotalRegNo?: string;
  collectionName?: string;
  resultName?: string;
  resultEra?: string;
  resultTexture?: string;
  resultLevel?: string;
  committee?: string;
  members?: AppraisalMemberVo[];
  conclusion?: string;
  rejectReason?: string;
  attachmentIds?: string;
  appraisedAt?: number | null;
  flowStatus?: string;
  flowInstanceId?: string;
  createTime?: string;
}

// RestorationVo / RestorationMethodVo 已迁移至 @/modules/collection-restoration/typings

/** 借展 */
export interface LoanVo {
  evidenceErrors?: string[];
  dossier?: Record<string, any>;
  baseline?: Record<string, any>;
  handover?: Record<string, any>;
  administrativeEvidence?: Record<string, any>[];
  monitoringRecords?: Record<string, any>[];
  obligations?: Record<string, any>;
  archives?: Record<string, any>[];
  proposalEditable?: boolean;
  id?: string;
  orgId?: string;
  title?: string;
  collectionIds?: string;
  collectionSummaries?: CollectionSummaryVo[];
  borrower?: string;
  protocolNo?: string;
  insurance?: string;
  /** 审批层级 馆级/省级/国家文物局（字典 col_approve_level） */
  approveLevel?: string;
  approvalDocNo?: string;
  attachmentIds?: string;
  conditionNote?: string;
  conditionPhotoIds?: string;
  signedVoucherAttachmentIds?: string;
  outExecuted?: boolean;
  outExecutedTime?: string;
  operator?: string;
  operatorName?: string;
  reviewer?: string;
  reviewerName?: string;
  loanDate?: number | null;
  returnDate?: number | null;
  returned?: boolean;
  /** 已返还藏品 id（部分归库进度） */
  returnedCollectionIds?: string[];
  heldForReviewCollectionIds?: string[];
  flowStatus?: string;
  flowInstanceId?: string;
  createTime?: string;
}

/** 征集入藏（流程驱动） */
export interface AcquisitionVo {
  collectionEvidenceIssue?: string | null;
  designatedReceptionId?: string;
  orgId?: string | null;
  id?: string;
  /** 所属征集计划 */
  planId?: string;
  planTitle?: string;
  name?: string;
  categoryId?: string;
  categoryName?: string;
  era?: string;
  texture?: string;
  proposedLevel?: string;
  source?: string;
  quantity?: number;
  unit?: string;
  holder?: string;
  proposedPrice?: number;
  appraisedValue?: number;
  acquireDate?: number | null;
  dimensions?: Record<string, any>;
  weight?: string;
  completeness?: string;
  completeStatus?: string;
  reason?: string;
  images?: string;
  expertOpinionAttachmentIds?: string;
  approvalAttachmentIds?: string;
  contractAttachmentIds?: string;
  invoiceAttachmentIds?: string;
  collectionId?: string;
  collectionTotalRegNo?: string;
  collectionName?: string;
  flowStatus?: string;
  flowInstanceId?: string;
  createTime?: string;
}

export interface AcquisitionImportRowVo {
  rowNo?: number;
  name?: string;
  categoryId?: string;
  categoryName?: string;
  era?: string;
  texture?: string;
  proposedLevel?: string;
  source?: string;
  quantity?: number;
  unit?: string;
  holder?: string;
  proposedPrice?: number;
  appraisedValue?: number;
  reason?: string;
  images?: string;
  imageNames?: string;
  height?: string;
  mouth?: string;
  bottom?: string;
  belly?: string;
  length?: string;
  width?: string;
  diameter?: string;
  weight?: string;
  completeness?: string;
  completeStatus?: string;
  expertOpinionAttachmentIds?: string;
  expertOpinionAttachmentNames?: string;
  approvalAttachmentIds?: string;
  approvalAttachmentNames?: string;
  contractAttachmentIds?: string;
  contractAttachmentNames?: string;
  invoiceAttachmentIds?: string;
  invoiceAttachmentNames?: string;
}

export interface AcquisitionImportValidateResultVo {
  valid: boolean;
  errors: CollectionImportErrorVo[];
  items: AcquisitionVo[];
}

/** 征集计划（流程驱动：编制→专家评审→分管馆长审批） */
export interface AcquisitionPlanVo {
  offlineApproval?: AcquisitionPlanOfflineApproval;
  id?: string;
  orgId?: string;
  title?: string;
  /** YEAR / MONTH（字典 col_plan_period） */
  planType?: string;
  year?: number;
  month?: number;
  content?: string;
  /** 计划预算：接口与存储为分，界面以元录入和显示 */
  budget?: number;
  attachmentIds?: string;
  flowStatus?: string;
  flowInstanceId?: string;
  createTime?: string;
  createBy?: string;
  acquisitionCount?: number;
  registeredCount?: number;
}

/** 入馆凭证（审批通过的征集单按批次组凭证，execute 批量登账） */
export interface EntryVoucherVo {
  id?: string;
  orgId?: string;
  voucherNo?: string;
  /** 征集单 id，逗号分隔 */
  acquisitionIds?: string;
  /** 已逐件逐项核对的征集单 id，逗号分隔 */
  verifiedAcquisitionIds?: string;
  basis?: string;
  entryDate?: number | null;
  /** DRAFT 待核收 / RECEIVED 已核收待登记 / REGISTERED 已正式登账（字典 col_voucher_status） */
  voucherStatus?: string;
  attachmentIds?: string;
  handoverUser?: string;
  receiverUser?: string;
  custodian?: string;
  warehouseId?: string;
  warehouseName?: string;
  remark?: string;
  createBy?: string;
  createTime?: string;
  acquisitionSummaries?: AcquisitionVo[];
  movementIds?: string[];
}

export interface WarehouseEntryApplicationVo {
  evidenceErrors?: string[];
  dossier?: Record<string, any>;
  baseline?: Record<string, any>;
  entryRecord?: Record<string, any>;
  exitRecord?: Record<string, any>;
  archives?: any[];
  id?: string;
  orgId?: string;
  applicationNo?: string;
  applicantId?: string;
  applicantName?: string;
  entrantIds?: string;
  entrantNames?: string[];
  externalEntrants?: string;
  warehouseIds?: string;
  warehouseNames?: string[];
  purpose?: string;
  plannedStart?: number | null;
  plannedEnd?: number | null;
  escortUserId?: string;
  escortUserName?: string;
  attachmentIds?: string;
  approvedAt?: string;
  actualEntryAt?: string;
  actualExitAt?: string;
  checkInBy?: string;
  checkOutBy?: string;
  accessRemark?: string;
  flowStatus?: string;
  flowInstanceId?: string;
  createTime?: string;
}

/** 库房日志 */
export interface WarehouseLogVo {
  evidenceIssue?: string | null;
  diary?: Record<string, any>;
  entries?: Record<string, any>;
  currentEntries?: Record<string, any>;
  entriesChanged?: boolean;
  archives?: any[];
  id?: string;
  orgId?: string;
  warehouseId?: string;
  warehouseName?: string;
  logDate?: number | null;
  content?: string;
  attachmentIds?: string;
  createBy?: string;
  createByName?: string;
  createTime?: string;
}

/** 工作台：出入库按月趋势点 */
export interface MovementTrendVo {
  month: string;
  inCount: number;
  outCount: number;
}

/** 工作台：编目审核汇总 */
export interface CatalogAuditSummaryVo {
  draft: number;
  pending: number;
  approved: number;
  rejected: number;
  passRate: number;
}

/** 工作台：进行中的流程单据 */
export interface OngoingFlowVo {
  bizType: string;
  bizName: string;
  id: string;
  title?: string;
  applicant?: string;
  applicantName?: string;
  stage?: string;
  flowInstanceId?: string;
  createTime?: string;
}

/** 年度统计（年终上报） */
export interface AnnualStatVo {
  orgId: string;
  year: number;
  cutoff: number;
  completeYear: boolean;
  transferInTotal: number;
  transferOutTotal: number;
  quantityBalances: AnnualUnitBalanceVo[];
  issues: { collectionId: string; totalRegNo: string; description: string }[];
  events: {
    id: string;
    kind: 'REGISTER' | 'TRANSFER_IN' | 'TRANSFER_OUT' | 'DEREGISTER' | 'CHANGE';
    occurredAt: number;
    recordedAt: number;
    sourceId: string;
    reason?: string;
    item: {
      collectionId: string;
      totalRegNo: string;
      name: string;
      nature?: string;
      level: string;
      categoryId?: string;
      source?: string;
      quantity: number;
      unit: string;
    };
  }[];
  openingTotal: number;
  newTotal: number;
  newBySource: Record<string, number>;
  deregTotal: number;
  deregByReason: Record<string, number>;
  closingTotal: number;
  inCount: number;
  outCount: number;
  closingByLevel: Record<string, number>;
  closingByCategory: Record<string, number>;
}

export interface AnnualUnitBalanceVo {
  unit: string;
  opening: number;
  added: number;
  transferIn: number;
  transferOut: number;
  deregistered: number;
  adjustment: number;
  closing: number;
}

export interface AnnualReportArchiveVo {
  id?: string;
  orgId?: string;
  year?: number;
  statistician?: string;
  keeperDirector?: string;
  museumLeader?: string;
  reportAttachmentIds?: string;
  reportedBy?: string;
  reportedAt?: string;
}

/** 复制/拓印 */
export interface ReproductionVo {
  id?: string;
  orgId?: string;
  collectionId?: string;
  collectionTotalRegNo?: string;
  collectionName?: string;
  repType?: string;
  images?: string;
  purpose?: string;
  repDate?: number | null;
  flowStatus?: string;
  flowInstanceId?: string;
  createTime?: string;
}

/** 盘点 */
export interface InventoryVo {
  id?: string;
  orgId?: string;
  batchName?: string;
  collectionId?: string;
  collectionTotalRegNo?: string;
  collectionName?: string;
  result?: string;
  note?: string;
  attachmentIds?: string;
  checkedAt?: number | null;
  flowStatus?: string;
  flowInstanceId?: string;
  createTime?: string;
}

export interface InventoryBatchVo {
  plan?: Record<string, any>;
  baseline?: Record<string, any>;
  checks?: Record<string, any>;
  issueActions?: Record<string, any>;
  completion?: Record<string, any>;
  archives?: any[];
  id?: string;
  orgId?: string;
  batchName?: string;
  scopeDesc?: string;
  cycleMonths?: number;
  warehouseId?: string;
  collectionIds?: string;
  startedAt?: number | null;
  finishedAt?: number | null;
  reportAttachmentIds?: string;
  remark?: string;
}

export interface InventoryBatchSummaryVo {
  batchName: string;
  firstInventoryId?: string;
  total: number;
  matchCount: number;
  surplusCount: number;
  missingCount: number;
  abnormalCount: number;
  attachmentCount: number;
  firstCheckedAt?: number | null;
  lastCheckedAt?: number | null;
  /** 前端合并：对应的批次配置 id（已配置但还没有清点记录的批次也会列出）。 */
  configId?: string;
  /** 前端合并：批次配置是否已核实方案开始清点。 */
  started?: boolean;
}

/** 跨馆调拨（流程驱动） */
export interface CollectionTransferVo {
  currentNoticeComplete?: boolean;
  terminationClosed?: boolean;
  terminationPending?: boolean;
  sourceWritable?: boolean;
  proposalEditable?: boolean;
  orgId?: string;
  dossier?: Record<string, any>;
  baseline?: Record<string, any>;
  execution?: Record<string, any>;
  executedAt?: number;
  announcedAt?: number;
  administrativeEvidence?: Record<string, any>[];
  notices?: Record<string, any>[];
  id?: string;
  collectionId?: string;
  collectionTotalRegNo?: string;
  collectionName?: string;
  fromOrgId?: string;
  toOrgId?: string;
  reason?: string;
  approvalDocNo?: string;
  attachmentIds?: string;
  flowStatus?: string;
  flowInstanceId?: string;
  createTime?: string;
}

/** 提用单（出入库流程） */
export interface RequisitionDossierVo {
  departmentGroupId: string;
  departmentHeadUserId: string;
  keeperUserId: string;
  leaderUserId: string;
  useResponsibleUserId: string;
  usageMethod: string;
  purposeDescription: string;
  usageSite: string;
  safetyRequirements: string;
  safetyPlanFileIds: string;
  expectedOutAt: number;
  expectedReturnAt: number;
  transportRequired?: boolean;
  transportDecisionReason?: string;
  transportStandardReference?: string;
  transportStandardApplicability?: string;
  transportCarrierName?: string;
  transportCarrierCapabilityFileIds?: string;
  transportPlanFileIds?: string;
}
export interface RequisitionHandoverVo {
  handedAt: number;
  reviewedAt: number;
  reviewerUserId: string;
  reviewAndAssignmentFileIds: string;
  custodianUserIds: string[];
  receivingUserIds: string[];
  items: { collectionId: string; condition: string; textNeedsImages: boolean; imageFileIds?: string }[];
  safetyMeasuresConfirmed: boolean;
  siteSafetyConfirmed: boolean;
  safetyCheckFileIds: string;
  signedHandoverFileIds: string;
  replacementReason?: string;
  appliedTransportStandardReference?: string;
  packingStandardConfirmed?: boolean;
  packingCheckFileIds?: string;
}
export interface RequisitionVo {
  sourceRetirement?: {
    context: {
      sourceBizType: string;
      sourceId: string;
      replacementSourceId: string;
      reason: string;
      authorizedBy: string;
      authorizedAt: number;
      appointmentFileIds: string;
      withdrawalFileIds: string;
    };
  } | null;
  supersededById?: string;
  replacesId?: string;
  replacementErrors?: string[];
  archives?: Record<string, any>[];
  dossier?: RequisitionDossierVo;
  baseline?: { items: { collectionId: string; content: Record<string, any> }[] };
  handover?: { content: RequisitionHandoverVo; approvalTaskId: string; recordedBy: string; recordedAt: number };
  id?: string;
  orgId?: string;
  documentNo?: string;
  sourceBizType?: string;
  sourceBizId?: string;
  collectionIds?: string;
  collectionSummaries?: CollectionSummaryVo[];
  /** 提用事由（字典 col_move_reason） */
  purpose?: string;
  basis?: string;
  department?: string;
  /** 经办人（系统用户 id） */
  operator?: string;
  operatorName?: string;
  /** 复核人（系统用户 id） */
  reviewer?: string;
  reviewerName?: string;
  custodian?: string;
  custodianName?: string;
  handoverUser?: string;
  handoverUserName?: string;
  reviewOpinion?: string;
  attachmentIds?: string;
  conditionNote?: string;
  conditionPhotoIds?: string;
  signedVoucherAttachmentIds?: string;
  outExecuted?: boolean;
  outExecutedTime?: string;
  toWarehouseId?: string;
  expectReturnDate?: number | null;
  returnDate?: number | null;
  returned?: boolean;
  /** 已归还藏品 id（部分归库进度） */
  returnedCollectionIds?: string[];
  heldForReviewCollectionIds?: string[];
  flowStatus?: string;
  flowInstanceId?: string;
  createTime?: string;
}

/** 归还/返还流程 */
export interface CollectionReturnVo {
  evidenceErrors?: string[];
  keeperUserId?: string;
  inspection?: Record<string, any>;
  sourceSnapshot?: Record<string, any>;
  acceptedCollectionIds?: string;
  complianceRecords?: Record<string, any>[];
  archives?: Record<string, any>[];
  pendingRequirements?: Record<string, any>[];
  id?: string;
  orgId?: string;
  sourceBizType?: string;
  sourceBizId?: string;
  sourceDocumentNo?: string;
  collectionIds?: string;
  collectionSummaries?: CollectionSummaryVo[];
  /** 出入库事由（字典 col_move_reason） */
  reason?: string;
  basis?: string;
  department?: string;
  operator?: string;
  operatorName?: string;
  reviewer?: string;
  reviewerName?: string;
  custodian?: string;
  handoverUser?: string;
  reviewOpinion?: string;
  attachmentIds?: string;
  toWarehouseId?: string;
  expectedReturnDate?: number | null;
  returnDate?: number | null;
  /** 损伤确认记录（双方当面确认后录入） */
  damageNote?: string;
  damagePhotoIds?: string;
  signedVoucherAttachmentIds?: string;
  executionState?: 'PENDING' | 'EXECUTED';
  executedAt?: string | null;
  executedBy?: string | null;
  flowStatus?: string;
  flowInstanceId?: string;
  createTime?: string;
}

/** 通用分页查询 */
export interface PageQuery {
  pageNum?: number;
  pageSize?: number;
  keyword?: string | null;
  [key: string]: any;
}

export interface CollectionMediaCaptureVo {
  view?: 'A' | 'B' | 'C' | 'D' | 'E' | 'F'; sequence?: number | null; partNo?: string;
  institution?: string; location?: string; photographedOn?: number | null; photographer?: string;
  accessoriesIncluded?: boolean; scaleDescription?: string;
}
export interface CollectionMediaVo {
  capture?: CollectionMediaCaptureVo;
  id?: string;
  orgId?: string;
  collectionId?: string;
  folderId?: string;
  fileId?: string;
  mediaType?: string;
  title?: string;
  isCover?: boolean;
  bizId?: string;
  sort?: number;
  remark?: string;
  createTime?: string;
}

export interface CollectionMediaFolderVo {
  id?: string;
  orgId?: string;
  collectionId?: string;
  parentId?: string;
  name?: string;
  sort?: number;
}

export interface CollectionMediaStatVo {
  byCollection?: Record<string, Record<string, number>>;
  total: number;
  collectionCount: number;
  byType: Record<string, number>;
}

export interface CollectionOutboundExecutionVo {
  conditionNote?: string;
  conditionPhotoIds?: string;
  signedVoucherAttachmentIds?: string;
}

export interface WarehouseLocationIssueVo {
  collectionId: string;
  totalRegNo?: string;
  collectionName?: string;
  warehouseId?: string;
  reason: string;
}

export interface WarehouseLocationCheckVo {
  checked: number;
  matched: number;
  issues: WarehouseLocationIssueVo[];
}

export interface CollectionAttachmentVo {
  id?: string;
  orgId?: string;
  collectionId?: string;
  fileId?: string;
  attachmentType?: string;
  name?: string;
  bizId?: string;
  sort?: number;
  remark?: string;
  createTime?: string;
}

export type SetRelation = 'MAIN' | 'ATTACH' | 'PART';

export interface CollectionSetVo {
  id?: string;
  orgId?: string;
  parentCollectionId?: string;
  childCollectionId?: string;
  partNo?: string;
  partName?: string;
  relation?: SetRelation | string;
  quantity?: number;
  unit?: string;
  sort?: number;
  createTime?: string;
}

export interface CollectionSetEntryPartVo {
  partNo?: string;
  partName?: string;
  relation?: SetRelation | string;
  quantity?: number;
  unit?: string;
  sort?: number;
  collection?: CollectionVo;
}

export interface CollectionSetEntryVo {
  main?: CollectionVo;
  parts: CollectionSetEntryPartVo[];
}

export interface CollectionImportRowVo {
  nature?: string;
  actualQuantity?: number; actualQuantityUnit?: string; dimensionUnit?: string; entryDate?: string;
  rowNo?: number;
  totalRegNo?: string;
  classNo?: string;
  auxNo?: string;
  name?: string;
  originalName?: string;
  categoryId?: string;
  categoryName?: string;
  eraMeaning?: string;
  eraNotation?: string;
  era?: string;
  texture?: string;
  textureCategory1?: string;
  textureCategory2?: string;
  level?: string;
  quantity?: number;
  unit?: string;
  source?: string;
  sourceDesc?: string;
  acquireDate?: string;
  acquireDateRange?: string;
  acquireYear?: string;
  completeness?: string;
  completeStatus?: string;
  height?: string;
  mouth?: string;
  bottom?: string;
  belly?: string;
  length?: string;
  width?: string;
  diameter?: string;
  weight?: string;
  weightRange?: string;
  weightUnit?: string;
  inscription?: string;
  feature?: string;
  author?: string;
  edition?: string;
  volumeNo?: string;
  preserveState?: string;
  valuation?: number;
  coverImage?: string;
  coverImageName?: string;
  warehouseId?: string;
  warehouseName?: string;
  remark?: string;
}

export interface CollectionImportErrorVo {
  rowNo: number;
  field: string;
  message: string;
}

export interface CollectionImportConfirmationVo {
  orgId: string;
  orgName: string;
  validationHash: string;
}

export interface CollectionImportValidateResultVo {
  confirmation?: CollectionImportConfirmationVo;
  valid: boolean;
  errors: CollectionImportErrorVo[];
  items: CollectionVo[];
  createCount?: number;
  updateCount?: number;
}

export type CollectionImportMode = 'CREATE_ONLY' | 'UPSERT';

export type CollectionWordDocumentType = 'REGISTRATION' | 'DYNAMIC_TRACKING';

export type CollectionTrackingEventType =
  | 'EXTRACT_RETURN'
  | 'OBSERVATION'
  | 'RESTORATION'
  | 'DIGITIZATION'
  | 'APPRAISAL_CHANGE'
  | 'RESEARCH_USE'
  | 'INCIDENT'
  | 'DEREGISTRATION'
  | 'NOTE'
  | 'REGISTRATION_DETAIL';

export interface CollectionTrackingEventPreviewVo {
  eventType: CollectionTrackingEventType;
  title: string;
  content: string;
  occurredAt?: string;
  endedAt?: string;
  sort?: number;
}

export interface CollectionWordMovementPreviewVo {
  rowNo: number;
  operatedAt: string;
  type: 'IN' | 'OUT';
  reason?: string;
  fromWarehouseName?: string;
  toWarehouseName?: string;
  fromWarehouseId?: string;
  toWarehouseId?: string;
  basis?: string;
  operator?: string;
  remark?: string;
}

export interface CollectionWordImportPreviewVo {
  confirmation?: CollectionImportConfirmationVo;
  valid: boolean;
  documentType?: CollectionWordDocumentType;
  fingerprint?: string;
  sourceFileName?: string;
  sourceHash?: string;
  collectionId?: string;
  totalRegNo?: string;
  collectionName?: string;
  startYear?: number;
  endYear?: number;
  registration?: CollectionImportRowVo;
  catalog?: CollectionCatalogContentVo;
  events: CollectionTrackingEventPreviewVo[];
  movements: CollectionWordMovementPreviewVo[];
  warnings: string[];
  errors: CollectionImportErrorVo[];
}

export interface CollectionWordImportRecordVo {
  id: string;
  documentType: CollectionWordDocumentType;
  sourceFileId?: string;
  sourceFileName: string;
  sourceHash: string;
  fingerprint: string;
  startYear?: number;
  endYear?: number;
  createTime?: string;
}

export interface CollectionTrackingEventVo extends CollectionTrackingEventPreviewVo {
  id: string;
  importId: string;
  createTime?: string;
}

export interface CollectionTrackingHistoryVo {
  imports: CollectionWordImportRecordVo[];
  events: CollectionTrackingEventVo[];
}

export interface CollectionFileImportItemVo {
  rowNo?: number;
  totalRegNo?: string;
  fileId?: string;
  fileName?: string;
  kind?: 'MEDIA' | 'ATTACHMENT';
  mediaType?: string;
  attachmentType?: string;
  title?: string;
  isCover?: boolean;
  sort?: number;
  remark?: string;
}

export interface CollectionFileImportMatchVo {
  rowNo: number;
  collectionId: string;
  totalRegNo: string;
  collectionName: string;
  orgId: string;
  fileId: string;
  fileName?: string;
  kind: 'MEDIA' | 'ATTACHMENT';
  mediaType?: string;
  attachmentType?: string;
  contentSha256: string;
  size: number;
  existingId?: string;
  bindingId?: string;
}

export interface CollectionFileImportResultVo {
  valid: boolean;
  matchedCount: number;
  mediaCount: number;
  attachmentCount: number;
  createdIds: string[];
  unchangedIds: string[];
  errors: CollectionImportErrorVo[];
  confirmation?: CollectionImportConfirmationVo;
  matches: CollectionFileImportMatchVo[];
}

export interface CollectionImportTemplateColumnVo {
  field: string;
  title: string;
  required?: boolean;
  dictType?: string;
  sample?: string;
}

export interface CollectionPrintVo {
  type: string;
  title: string;
  businessId?: string;
  documentNo?: string;
  orgId?: string;
  orgName?: string;
  generatedAt?: string;
  fields: { label: string; value?: string; dictType?: string; fileReferences?: string[] }[];
  collections: CollectionVo[];
  template?: CollectionPrintTemplateVo;
}

export interface CollectionPrintTemplateVo {
  code: string;
  type: string;
  name: string;
  title: string;
  documentLabel: string;
  collectionTitle: string;
  templatePath: string;
  signatures: string[];
  sort: number;
  enabled?: boolean;
  format?: string;
  orgId?: string;
  remark?: string;
  custom?: boolean;
  createTime?: string;
}

export interface CollectionPrintTemplateSaveVo {
  name?: string;
  title?: string;
  documentLabel?: string;
  collectionTitle?: string;
  templatePath?: string;
  signatures?: string[];
  enabled?: boolean;
  sort?: number;
  remark?: string;
}

export interface CollectionPrintFieldUsageVo {
  group: string;
  label: string;
  placeholder: string;
  example: string;
  usage: string;
}

export interface CollectionPrintTemplateUsageVo {
  type: string;
  fields: CollectionPrintFieldUsageVo[];
}

export interface CollectionPrintRenderRequest {
  type?: string;
  id?: string;
  ids?: string[];
  data?: CollectionPrintVo;
  items?: CollectionPrintVo[];
}

export interface InventoryImportRowVo {
  rowNo?: number;
  batchName?: string;
  collectionId?: string;
  totalRegNo?: string;
  result?: string;
  note?: string;
  attachmentIds?: string;
  attachmentNames?: string;
}

export interface InventoryImportValidateResultVo {
  valid: boolean;
  errors: CollectionImportErrorVo[];
  items: InventoryVo[];
}
