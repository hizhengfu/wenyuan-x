/** 预防性保护模块的业务 VO。采集运维类型统一位于 museum-device。 */

export type PvAlarmLevel = 'RED' | 'ORANGE' | 'YELLOW' | 'BLUE' | 'NORMAL';
export type PvAlarmProcessStatus = 'AWAIT' | 'PROCESSING' | 'PROCESSED' | 'TRANSFERRED_RESTORATION' | 'CLOSED';
export type PvSensorCategory = 'ENVIRONMENT' | 'POLLUTION' | 'VIBRATION' | 'BIOLOGY' | 'OTHER';
export type PvReportCycle = 'MONTH' | 'QUARTER' | 'YEAR' | 'CUSTOM';
export type PvDataQuality = 'NORMAL' | 'SUSPECT' | 'INVALID';
export type PvDataSource = 'DEVICE' | 'MANUAL' | 'IMPORT' | 'DERIVED';

export interface SensorPointVo {
  id?: string;
  orgId?: string;
  parentId?: string;
  warehouseId?: string;
  name?: string;
  code?: string;
  remarks?: string;
  warehousePath?: string;
  status?: string;
  createTime?: string;
  updateTime?: string;
}

export interface SensorVo {
  id?: string;
  name?: string;
  code?: string;
  unit?: string;
  category?: PvSensorCategory;
  icon?: string;
  remarks?: string;
  sort?: number;
}

export interface HistoryDataVo {
  sampleLocationBasis?: string | null;
  id?: string;
  orgId?: string;
  sensorPointId?: string;
  sensorId?: string;
  sourceType?: PvDataSource;
  warehouseId?: string;
  dataValue?: number;
  dateTime?: number;
  receivedAt?: number;
  gatewayId?: string;
  deviceId?: string;
  sourceMessageId?: string;
  sourceEventId?: string;
  inboxId?: string;
  quality?: PvDataQuality;
  backfill?: boolean;
  sensorName?: string;
  sensorUnit?: string;
  sensorPointName?: string;
  deviceName?: string;
}

export interface EnvironmentStandardSource {
  name: string;
  policyId?: string | null;
  policyVersion?: number | null;
  ruleVersion: number;
  effectiveFrom: number;
  standardRef?: string | null;
}

export interface EnvironmentCardVo {
  standardMatch?:
    | 'MATCHED'
    | 'NO_PUBLISHED_RULE'
    | 'RULE_NOT_ACTIVE'
    | 'NO_EFFECTIVE_VERSION'
    | 'LOCATION_NOT_COVERED'
    | 'NO_VALUE_THRESHOLD'
    | null;
  standardSources?: EnvironmentStandardSource[];
  sampleStatus?: string;
  sampleLocationId?: string | null;
  sampleLocationBasis?: string | null;
  maximumSampleAgeSeconds?: number | null;
  freshnessAssessmentIds?: string[];
  evaluatedAt?: number | null;
  orgId?: string;
  orgName?: string;
  warehouseId?: string;
  warehouseCode?: string;
  warehouseName?: string;
  warehouseThumbnail?: string;
  sensorPointId: string;
  sensorPointName?: string;
  sensorId?: string;
  sensorName?: string;
  sensorCode?: string;
  sensorUnit?: string;
  sensorIcon?: string;
  sensorCategory?: PvSensorCategory;
  sensorSort?: number;
  dataValue?: number | null;
  dateTime?: number | null;
  safe?: boolean | null;
  alarmLevel?: PvAlarmLevel | null;
  activeAlarmCount?: number;
  activeAlarmLevel?: PvAlarmLevel | null;
  activeAlarmTitle?: string | null;
  activeAlarmCode?: string | null;
  activeAlarmAt?: number | null;
  quality?: PvDataQuality | null;
  backfill?: boolean | null;
  safeMinValue?: number | null;
  safeMaxValue?: number | null;
  thresholdConflict?: boolean;
  deviceId?: string | null;
  deviceName?: string | null;
}

export interface AlarmEventVo {
  canProcess?: boolean;
  id?: string;
  orgId?: string;
  sensorPointId?: string;
  warehouseId?: string;
  sensorId?: string;
  deviceId?: string;
  historyDataId?: string;
  level?: PvAlarmLevel;
  dataValue?: number;
  dateTime?: string;
  startedAt?: string;
  lastObservedAt?: string;
  affectedCollectionIds?: string;
  affectedCount?: number;
  processStatus?: PvAlarmProcessStatus;
  handleReason?: string;
  handleBy?: string;
  handleTime?: string;
  reviewer?: string;
  reviewTime?: string;
  closedAt?: string;
  escalationFlowInstanceId?: string;
  restorationProjectId?: string | null;
  sensorName?: string;
  sensorUnit?: string;
  sensorPointName?: string;
  warehouseName?: string;
  deviceName?: string;
  alarmCode?: string | null;
  title?: string | null;
  alarmSource?: 'RULE' | 'DEVICE';
  policyId?: string | null;
  ruleVersionId?: string | null;
  thresholdSource?: 'COMPILED_POLICY' | null;
  standardRefs?: string[];
  materials?: string[];
}

export type ProtectionRuleScope = 'MATERIAL' | 'GENERAL' | 'LOCATION';
export type RuleTemplateStatus = 'DRAFT' | 'PUBLISHED' | 'RETIRED';
export type ProtectionPolicyStatus = 'ACTIVE' | 'SUSPENDED' | 'ARCHIVED';
export type ProtectionPolicyVersionStatus = 'DRAFT' | 'PUBLISHED' | 'SUPERSEDED';
export type ProtectionTargetScope = 'ALL_LOCATIONS' | 'SELECTED_LOCATIONS';

export interface ProtectionThresholdItemVo {
  id?: string;
  scope?: ProtectionRuleScope;
  selectorKey?: string;
  metricCode?: string;
  l1MinValue?: number | null;
  l1MaxValue?: number | null;
  l2MinValue?: number | null;
  l2MaxValue?: number | null;
  l3MinValue?: number | null;
  l3MaxValue?: number | null;
  l4MinValue?: number | null;
  l4MaxValue?: number | null;
  standardRef?: string | null;
  expertOpinion?: string | null;
  remarks?: string | null;
}

export interface RuleTemplateVo {
  definitionHash?: string;
  canManage?: boolean;
  id?: string;
  code?: string;
  name?: string;
  description?: string | null;
  status?: RuleTemplateStatus;
  versionId?: string;
  versionNo?: number;
  versionStatus?: RuleTemplateStatus;
  publishedVersionId?: string | null;
  publishedVersionNo?: number | null;
  hasDraft?: boolean;
  publishedItems?: ProtectionThresholdItemVo[];
  items: ProtectionThresholdItemVo[];
}

export interface ProtectionPolicyVo {
  id?: string;
  orgId?: string;
  code?: string;
  name?: string;
  status?: ProtectionPolicyStatus;
  sourceTemplateId?: string | null;
  sourceTemplateVersionId?: string | null;
  versionId?: string | null;
  versionNo?: number | null;
  versionStatus?: ProtectionPolicyVersionStatus | null;
  targetScope: ProtectionTargetScope;
  locationIds: string[];
  affectedLocationCount: number;
  appliedLocationCount: number;
  items: ProtectionThresholdItemVo[];
}

export interface ReportVo {
  canRemove?: boolean;
  id?: string;
  orgId?: string;
  warehouseId?: string;
  cycleType?: PvReportCycle;
  periodStart?: string | number;
  periodEnd?: string | number;
  complianceRate?: number;
  exceedCount?: number;
  exceedDurationMinutes?: number;
  handleRate?: number;
  summary?: string;
  filePath?: string;
  generatedAt?: string;
  warehouseName?: string;
}

export interface AlarmDashboardVo {
  pointCount: number;
  gatewayCount: number;
  latestPending: AlarmEventVo[];
  total: number;
  pending: number;
  byStatus: Record<string, number>;
  byLevel: Record<string, number>;
}
