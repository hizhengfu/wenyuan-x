/** 文物修复管理模块类型定义 */

/** 当前收藏单位的实际修复与提用工作统计；服务端逐次核对当前权限。 */
export interface RestorationWorkSummary {
  orgId: string;
  activeRestorationProjects: number;
  sentRestorationUnreturnedCollections: number;
  otherRequisitionUnreturnedCollections: number;
}

/** 修复机构主数据 */
export interface RestorationOrgVo {
  id?: string;
  orgId?: string;
  name?: string;
  code?: string;
  qualificationLevel?: string;
  qualificationCertId?: string;
  qualificationCertNo?: string;
  qualificationExpireDate?: number | null;
  scope?: string;
  address?: string;
  contactName?: string;
  contactPhone?: string;
  contactEmail?: string;
  legalPerson?: string;
  taxNo?: string;
  bankAccount?: string;
  external?: boolean;
  enabled?: boolean;
  remarks?: string;
  createTime?: number | null;
}

/** 修复项目主 VO（含 M2 扩展字段） */
export interface RestorationVo {
  id?: string;
  orgId?: string;
  collectionId?: string;
  collectionTotalRegNo?: string;
  collectionName?: string;
  plan?: string;
  process?: string;
  beforeImages?: string;
  afterImages?: string;
  restoreOrgId?: string;
  restoreOrg?: string;
  operator?: string;
  operatorName?: string;
  reviewer?: string;
  reviewerName?: string;
  startDate?: number | null;
  endDate?: number | null;
  state?: string;
  previousPlanId?: string | null;
  supersededById?: string | null;
  replacesId?: string | null;
  replacementErrors?: string[];
  actualStoppedAt?: number | null;
  sentStoppedAt?: number | null;
  flowStatus?: string;
  flowInstanceId?: string;
  createTime?: number | null;
  createBy?: string;
  // M2 扩展字段
  collectionLevel?: string;
  approvalScope?: string;
  triggerSource?: string;
  preservationAlarmId?: string;
  urgencyLevel?: string;
  restoreOrgQualification?: string;
  contractFileIds?: string;
  testReportFileIds?: string;
  planFileIds?: string;
  targetEnvironment?: string;
}

/** 修复方法库 */
export interface RestorationMethodVo {
  id?: string;
  orgId?: string;
  name?: string;
  category?: string;
  applicableTexture?: string;
  summary?: string;
  process?: string;
  materials?: string;
  precautions?: string;
  enabled?: boolean;
  sort?: number;
  createTime?: number | null;
}

/** 修复档案（WW/T 0032-2010） */
export interface RestorationArchiveVo {
  id?: string;
  orgId?: string;
  restorationId?: string;
  archiveNo?: string;
  beforeDescription?: string;
  afterDescription?: string;
  materialsUsed?: string;
  restorerSignatures?: string;
  approvedBy?: string;
  approvedByName?: string;
  approvedTime?: number | null;
  remarks?: string;
  createTime?: number | null;
  createBy?: string;
  // 列表展示 enrich
  collectionId?: string;
  collectionTotalRegNo?: string;
  collectionName?: string;
  restoreOrg?: string;
  damageCount?: number;
}

/** 病害记录 */
export interface RestorationDamageVo {
  id?: string;
  orgId?: string;
  restorationId?: string;
  collectionId?: string;
  damageType?: string;
  severity?: string;
  location?: string;
  description?: string;
  imageFileIds?: string;
  sort?: number;
  createTime?: number | null;
  // 列表展示 enrich
  collectionTotalRegNo?: string;
  collectionName?: string;
  restorationArchiveNo?: string;
}

/** 修复验收单 */
export interface RestorationAcceptanceVo {
  id?: string;
  orgId?: string;
  restorationId?: string;
  acceptanceType?: string;
  experts?: string;
  conclusion?: string;
  opinion?: string;
  reportFileId?: string;
  acceptedTime?: number | null;
  createTime?: number | null;
  // 列表 enrich
  collectionId?: string;
  collectionTotalRegNo?: string;
  collectionName?: string;
  restoreOrg?: string;
  restorationArchiveNo?: string;
}

/** 修复人员 */
export interface RestorerVo {
  id?: string;
  orgId?: string;
  name?: string;
  sex?: string;
  birthDate?: number | null;
  title?: string;
  certNo?: string;
  speciality?: string;
  specialityYears?: number;
  telephone?: string;
  email?: string;
  external?: boolean;
  enabled?: boolean;
  remarks?: string;
  createTime?: number | null;
}

/** 资质证书 */
export interface QualificationCertVo {
  id?: string;
  orgId?: string;
  holderType?: string;
  holderId?: string;
  holderName?: string;
  certNo?: string;
  certLevel?: string;
  scope?: string;
  issuer?: string;
  issueDate?: number | null;
  expireDate?: number | null;
  attachmentFileId?: string;
  enabled?: boolean;
  remarks?: string;
  createTime?: number | null;
}

/** 委外修复合同 */
export interface RestorationContractVo {
  id?: string;
  orgId?: string;
  restorationId?: string;
  contractNo?: string;
  partyA?: string;
  partyBOrgId?: string;
  partyB?: string;
  partyBCertId?: string;
  partyBCertNo?: string;
  contractAmount?: number | null;
  signDate?: number | null;
  plannedStart?: number | null;
  plannedEnd?: number | null;
  insuranceInfo?: string;
  liquidatedDamages?: string;
  copyrightClause?: string;
  attachmentFileIds?: string;
  remarks?: string;
  createTime?: number | null;
  // 列表 enrich
  collectionId?: string;
  collectionTotalRegNo?: string;
  collectionName?: string;
  restoreOrg?: string;
  collectionLevel?: string;
}

/** 修复工序 */
export interface RestorationProcessVo {
  id?: string;
  orgId?: string;
  restorationId?: string;
  sort?: number;
  stageName?: string;
  methodId?: string;
  methodName?: string;
  materials?: string;
  tools?: string;
  operatorId?: string;
  operatorName?: string;
  assistantIds?: string;
  assistantNames?: string;
  startTime?: number | null;
  endTime?: number | null;
  duration?: number;
  description?: string;
  imageFileIds?: string;
  createTime?: number | null;
  // 列表 enrich
  collectionId?: string;
  collectionTotalRegNo?: string;
  collectionName?: string;
  restoreOrg?: string;
}
