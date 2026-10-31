import type { AlarmSeverity, MuseumMetricSeriesVo, IngestRecordVo } from '../../museum-device/typings';

export type SafetyDomain = 'BUILDING' | 'LIGHTNING' | 'FIRE' | 'SECURITY' | 'ELECTRICITY' | 'BUILDING_ENVIRONMENT';
export type SafetyRiskLevel = 'NOTICE' | 'WARNING' | 'MAJOR' | 'CRITICAL';
export type SafetyRiskStatus = 'PENDING_CONFIRMATION' | 'PROCESSING' | 'RECOVERED_PENDING_CONFIRMATION' | 'CLOSED';
export type SafetyRiskAction =
  | 'CONFIRMED'
  | 'ASSIGNED'
  | 'PROCESSED'
  | 'RECOVERED'
  | 'RECOVERY_CONFIRMED'
  | 'CLOSED'
  | 'COMMENTED';
export type SafetyVenueDomainState = 'NORMAL' | 'RISK' | 'DATA_INTERRUPTED' | 'UNCONFIGURED';
export type SafetyReportType = 'STRUCTURE' | 'LIGHTNING' | 'FIRE' | 'SECURITY' | 'ELECTRICITY' | 'COMPREHENSIVE';
export type SafetyReportStatus = 'GENERATING' | 'PROCESSING' | 'SUCCESS' | 'FAILED';
export type SafetySystemScopeType = 'VENUE' | 'SITE' | 'BUILDING';
export type SafetySystemComponentType =
  | 'ATMOSPHERIC_ELECTRIC_FIELD'
  | 'LIGHTNING_CURRENT'
  | 'GROUNDING'
  | 'DOWN_CONDUCTOR'
  | 'SPD'
  | 'OTHER';
export type SafetySystemIntegrityWarning =
  | 'DEVICE_MISSING'
  | 'DEVICE_DISABLED'
  | 'DEVICE_ORG_MISMATCH'
  | 'LOCATION_MISSING'
  | 'LOCATION_DISABLED'
  | 'LOCATION_ORG_MISMATCH'
  | 'COLLECTION_CONTAINER_LOCATION'
  | 'INSTALLATION_OUT_OF_SCOPE'
  | 'LIGHTNING_CAPABILITY_MISSING'
  | 'BOUND_TO_OTHER_SYSTEM';

export interface SafetySystemCommand {
  orgId?: string | null;
  code?: string | null;
  name?: string | null;
  domain?: 'LIGHTNING';
  scopeType?: SafetySystemScopeType | null;
  scopeLocationId?: string | null;
  description?: string | null;
  sort?: number;
}

export interface SafetySystemDeviceCommand {
  deviceId?: string | null;
  componentType?: SafetySystemComponentType | null;
  pointCode?: string | null;
  pointName?: string | null;
  sort?: number;
}

export interface SafetySystemDevicesCommand {
  devices: SafetySystemDeviceCommand[];
}

export interface SafetySystemDeviceVo {
  bindingId: string;
  systemId: string;
  componentType: SafetySystemComponentType;
  pointCode: string;
  pointName: string;
  deviceId: string;
  deviceCode?: string | null;
  deviceName?: string | null;
  installationLocationId?: string | null;
  installationLocationPath?: string | null;
  installationInScope: boolean;
  online: boolean;
  lastSeenAt?: number | null;
  activeRisks: number;
  activeAlarms: number;
  warnings: SafetySystemIntegrityWarning[];
  sort: number;
}

export interface SafetySystemComponentVo {
  componentType: SafetySystemComponentType;
  label: string;
  state: SafetyVenueDomainState;
  configuredDevices: number;
  onlineDevices: number;
  offlineDevices: number;
  abnormalDevices: number;
  activeRisks: number;
  activeAlarms: number;
  latestDataAt?: number | null;
  integrityWarningCount: number;
  devices: SafetySystemDeviceVo[];
}

export interface SafetySystemVo {
  id: string;
  orgId: string;
  code: string;
  name: string;
  domain: 'LIGHTNING';
  scopeType: SafetySystemScopeType;
  scopeLocationId?: string | null;
  scopeLocationPath?: string | null;
  description?: string | null;
  sort: number;
  state: SafetyVenueDomainState;
  configuredDevices: number;
  onlineDevices: number;
  offlineDevices: number;
  abnormalDevices: number;
  activeRisks: number;
  activeAlarms: number;
  latestDataAt?: number | null;
  integrityWarningCount: number;
  components: SafetySystemComponentVo[];
}

export interface SafetySystemDeviceCandidateVo {
  deviceId: string;
  code: string;
  name: string;
  modelCode?: string | null;
  status: string;
  installationLocationId?: string | null;
  installationLocationPath?: string | null;
  eligible: boolean;
  metricCodes: string[];
  warnings: SafetySystemIntegrityWarning[];
  boundSystemId?: string | null;
  boundSystemName?: string | null;
}

export interface LightningSystemOverviewVo {
  systemCount: number;
  normalSystems: number;
  riskSystems: number;
  interruptedSystems: number;
  unconfiguredSystems: number;
  configuredDevices: number;
  onlineDevices: number;
  offlineDevices: number;
  activeRisks: number;
  activeAlarms: number;
  integrityWarningCount: number;
  systems: SafetySystemVo[];
}

export interface SafetyRiskLogVo {
  id: string;
  action: string;
  fromStatus?: SafetyRiskStatus | null;
  toStatus: SafetyRiskStatus;
  operatorId?: string | null;
  assigneeId?: string | null;
  result?: string | null;
  remark?: string | null;
  attachments: string[];
  occurredAt: number;
}

export interface SafetySourceAlarmVo {
  eventId: string;
  ruleId?: string | null;
  ruleVersionId?: string | null;
  metricCode?: string | null;
  fieldCode: string;
  triggerValue?: string | null;
  conditionSnapshot?: string | null;
  locationId?: string | null;
  locationSnapshotPath?: string | null;
  title: string;
}

export interface SafetyAlarmVo {
  id: string;
  alarmCode: string;
  title: string;
  severity: AlarmSeverity;
  deviceId: string;
  deviceName?: string | null;
  metricCode?: string | null;
  fieldCode: string;
  triggerValue?: string | null;
  locationSnapshotId?: string | null;
  locationSnapshotPath?: string | null;
  riskId?: string | null;
  startedAt: number;
  lastOccurredAt: number;
  occurrenceCount: number;
}

export interface SafetyRiskVo {
  id: string;
  orgId: string;
  systemId?: string | null;
  sourceType: 'ALARM' | 'MANUAL';
  sourceEventOrRuleId: string;
  sourceEventId?: string | null;
  domain: SafetyDomain;
  level: SafetyRiskLevel;
  deviceId: string;
  deviceName?: string | null;
  locationSnapshotId?: string | null;
  locationSnapshotPath?: string | null;
  currentLocationId?: string | null;
  currentLocationPath?: string | null;
  title: string;
  description?: string | null;
  status: SafetyRiskStatus;
  assigneeId?: string | null;
  occurrenceCount: number;
  firstOccurredAt: number;
  lastOccurredAt: number;
  recoveredAt?: number | null;
  closedAt?: number | null;
  logs: SafetyRiskLogVo[];
  sourceAlarm?: SafetySourceAlarmVo | null;
  trend?: MuseumMetricSeriesVo | null;
  workflow?: {
    step: number;
    nextAction: SafetyRiskAction | null;
    blockedReason: string | null;
    pendingLinkages: number;
    canCreateLinkage: boolean;
    linkageTypes: string[];
    triggerLogId: string | null;
  } | null;
}

export interface SafetyOverviewVo {
  activeRisks: number;
  majorRisks: number;
  todayNew: number;
  todayRecovered: number;
  byDomain: Partial<Record<SafetyDomain, number>>;
  byLevel: Partial<Record<SafetyRiskLevel, number>>;
  byStatus: Partial<Record<SafetyRiskStatus, number>>;
  byLocation: Array<{ locationId?: string | null; locationName: string; count: number }>;
  onlineDevices: number;
  offlineDevices: number;
  abnormalDevices: number;
  latestRisks: SafetyRiskVo[];
  recentActions: SafetyRiskLogVo[];
}

export interface SafetyPortfolioDomainVo {
  domain: SafetyDomain;
  state: SafetyVenueDomainState;
  configuredDevices: number;
  onlineDevices: number;
  offlineDevices: number;
  abnormalDevices: number;
  activeRisks: number;
  majorRisks: number;
  activeAlarms: number;
  highestRiskLevel?: SafetyRiskLevel | null;
  latestDataAt?: number | null;
  /** 防雷领域按整体系统统计；旧接口未返回时为空。 */
  systemCount?: number;
}

export interface SafetyPortfolioVenueVo {
  venueId: string;
  parentId?: string | null;
  code: string;
  name: string;
  shortName?: string | null;
  onlineDevices: number;
  offlineDevices: number;
  domains: Partial<Record<SafetyDomain, SafetyPortfolioDomainVo>>;
}

export interface SafetyPortfolioVo {
  venueCount: number;
  monitoredVenueCount: number;
  activeRisks: number;
  majorRisks: number;
  onlineDevices: number;
  offlineDevices: number;
  dataInterruptedDomains: number;
  generatedAt: number;
  venues: SafetyPortfolioVenueVo[];
}

export interface SafetyDomainVo {
  domain: SafetyDomain;
  latestRecords: IngestRecordVo[];
  latestRecordTotal: number;
  latestRecordPageNum: number;
  latestRecordPageSize: number;
  activeRisks: SafetyRiskVo[];
  activeRiskTotal: number;
  recentRisks: SafetyRiskVo[];
  historicalRiskTotal: number;
  activeAlarms: SafetyAlarmVo[];
  activeAlarmTotal: number;
  onlineDevices: number;
  offlineDevices: number;
  abnormalDevices: number;
  alarmsBySeverity: Record<string, number>;
  alarmsByType: Record<string, number>;
}

export interface SafetyRiskActionCommand {
  action: SafetyRiskAction;
  assigneeId?: string | null;
  result?: string | null;
  remark?: string | null;
  attachments?: string[];
  linkageType?: string | null;
  linkagePlanId?: string | null;
  expectedTriggerLogId?: string | null;
}

export interface SafetyReportCommand {
  orgId?: string | null;
  systemId?: string | null;
  type: SafetyReportType;
  from: number;
  to: number;
  locationId?: string | null;
  deviceIds?: string[];
  metricCodes?: string[];
}

export interface SafetyReportVo {
  id: string;
  orgId: string;
  systemId?: string | null;
  type: SafetyReportType;
  status: SafetyReportStatus;
  from: number;
  to: number;
  locationId?: string | null;
  deviceIds: string[];
  metricCodes: string[];
  dataCutoffAt?: number | null;
  generatorVersion: string;
  fileReference?: string | null;
  summary: Record<string, unknown>;
  errorMessage?: string | null;
  createdAt: number;
}

export interface SafetyAlarmDisplay {
  severity: AlarmSeverity;
  fieldCode: string;
  value?: string | null;
}

export type SafetyDocumentType =
  | 'SAFETY_POLICY'
  | 'EMERGENCY_PLAN'
  | 'EDUCATION_POLICY'
  | 'REGULATION'
  | 'OPERATING_MANUAL';
export type SafetyDocumentStatus = 'DRAFT' | 'UNDER_REVIEW' | 'EFFECTIVE' | 'REJECTED' | 'ARCHIVED';
export type SafetyReviewDecision = 'APPROVE' | 'REJECT' | 'COMMENT';
export type SafetyConfidentiality = 'PUBLIC' | 'INTERNAL' | 'RESTRICTED';
export type SafetyAccessSubjectType = 'ROLE' | 'USER';
export type SafetyAccessAction = 'VIEW' | 'EDIT' | 'DELETE' | 'REVIEW' | 'DOWNLOAD';
export type SafetyDocumentAccessScope = 'TYPE' | 'CATEGORY';

export interface SafetyComplianceBasis {
  code: string;
  title: string;
  clause?: string | null;
  issuingAuthority?: string | null;
  effectiveAt?: number | null;
  sourceUrl?: string | null;
}

export interface SafetyDocumentCommand {
  orgId?: string | null;
  type: SafetyDocumentType;
  domain?: SafetyDomain | null;
  code: string;
  title: string;
  category: string;
  applicableScope: string;
  ownerUserId: string;
  confidentiality: SafetyConfidentiality;
  requiredApprovals: number;
  content: string;
  changeSummary: string;
  attachments?: string[];
  complianceBasis?: SafetyComplianceBasis[];
  metadata?: Record<string, unknown>;
  effectiveAt?: number | null;
  expiresAt?: number | null;
}

export interface SafetyDocumentBatchResult {
  createdIds: string[];
  count: number;
}

export interface SafetyDocumentVersionCommand {
  expectedCurrentRevision: number;
  content: string;
  changeSummary: string;
  attachments?: string[];
  complianceBasis?: SafetyComplianceBasis[];
  metadata?: Record<string, unknown>;
  effectiveAt?: number | null;
  expiresAt?: number | null;
}

export interface SafetyDocumentReviewCommand {
  decision: SafetyReviewDecision;
  opinion: string;
  annotations?: string[];
}

export interface SafetyDocumentAccessRuleCommand {
  subjectType: SafetyAccessSubjectType;
  subjectId: string;
  actions: SafetyAccessAction[];
}

export interface SafetyDocumentAccessRuleVo extends SafetyDocumentAccessRuleCommand {
  id: string;
}

export interface SafetyDocumentAccessCommand {
  rules: SafetyDocumentAccessRuleCommand[];
}

export interface SafetyDocumentScopeAccessCommand {
  orgId?: string | null;
  scopeType: SafetyDocumentAccessScope;
  scopeValue: string;
  rules: SafetyDocumentAccessRuleCommand[];
}

export interface SafetyDocumentScopeAccessRuleVo extends SafetyDocumentAccessRuleCommand {
  id: string;
  orgId: string;
  scopeType: SafetyDocumentAccessScope;
  scopeValue: string;
}

export interface SafetyDocumentReviewVo {
  id: string;
  level: number;
  decision: SafetyReviewDecision;
  reviewerId: string;
  opinion: string;
  annotations: string[];
  reviewedAt: number;
}

export interface SafetyDocumentVersionVo {
  id: string;
  revision: number;
  versionNo: string;
  status: SafetyDocumentStatus;
  content: string;
  changeSummary: string;
  attachments: string[];
  complianceBasis: SafetyComplianceBasis[];
  metadata: Record<string, unknown>;
  effectiveAt?: number | null;
  expiresAt?: number | null;
  approvalCount: number;
  submittedAt?: number | null;
  reviews: SafetyDocumentReviewVo[];
}

export interface SafetyDocumentVo {
  id: string;
  orgId: string;
  type: SafetyDocumentType;
  domain?: SafetyDomain | null;
  code: string;
  title: string;
  category: string;
  applicableScope: string;
  ownerUserId: string;
  confidentiality: SafetyConfidentiality;
  requiredApprovals: number;
  currentRevision: number;
  effectiveRevision?: number | null;
  status: SafetyDocumentStatus;
  latestVersion: SafetyDocumentVersionVo;
  versions: SafetyDocumentVersionVo[];
}

export type SafetyShiftType = 'MORNING' | 'MIDDLE' | 'NIGHT' | 'CUSTOM';
export type SafetyDutyScheduleStatus = 'SCHEDULED' | 'ON_LEAVE' | 'COMPLETED' | 'CANCELLED';
export type SafetyDutyEventType =
  | 'ROUTINE'
  | 'DEVICE_ABNORMAL'
  | 'VISITOR'
  | 'SECURITY'
  | 'FIRE'
  | 'HANDOVER'
  | 'OTHER';
export type SafetyReviewStatus = 'PENDING' | 'APPROVED' | 'REJECTED';
export type SafetyDutyReviewDecision = 'APPROVE' | 'REJECT';
export type SafetyDutyAdjustmentStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface SafetyQualificationCertificate {
  name: string;
  certificateNo?: string | null;
  issuingAuthority?: string | null;
  issuedAt?: number | null;
  expiresAt?: number | null;
  attachments: string[];
}

export interface SafetyDutyScheduleCommand {
  orgId?: string | null;
  cycleCode: string;
  shiftType: SafetyShiftType;
  dutyPersonnelId: string;
  startsAt: number;
  endsAt: number;
  onLeave?: boolean;
}

export interface SafetyDutyScheduleVo extends Omit<SafetyDutyScheduleCommand, 'orgId' | 'onLeave'> {
  id: string;
  orgId: string;
  postId: string;
  position: string;
  dutyUserId: string;
  qualificationSnapshot: SafetyQualificationCertificate[];
  dutyAreaId: string;
  dutyArea: string;
  taskList: string[];
  status: SafetyDutyScheduleStatus;
}

export interface SafetyDutyPersonnelCommand {
  orgId?: string | null;
  userId: string;
  postId: string;
  qualifications: SafetyQualificationCertificate[];
  dutyAreaId: string;
  taskList?: string[];
  active?: boolean;
}

export interface SafetyDutyPersonnelVo extends Omit<SafetyDutyPersonnelCommand, 'orgId' | 'taskList' | 'active'> {
  id: string;
  orgId: string;
  name: string;
  phone: string;
  position: string;
  dutyArea: string;
  taskList: string[];
  active: boolean;
}

export type SafetyAttendanceStatus = 'SCHEDULED' | 'NORMAL' | 'LATE' | 'ABSENT' | 'ON_LEAVE';

export interface SafetyDutyAttendanceVo {
  scheduleId: string;
  orgId: string;
  dutyUserId: string;
  dutyUserName?: string | null;
  position: string;
  dutyArea: string;
  scheduledStartsAt: number;
  scheduledEndsAt: number;
  arrivedAt?: number | null;
  departedAt?: number | null;
  longitude?: number | null;
  latitude?: number | null;
  status: SafetyAttendanceStatus;
}

export interface SafetyDutyLogCommand {
  scheduleId: string;
  eventType: SafetyDutyEventType;
  occurredAt: number;
  arrivedAt?: number | null;
  departedAt?: number | null;
  longitude?: number | null;
  latitude?: number | null;
  description: string;
  result?: string | null;
  handoverUserId?: string | null;
  handoverNotes?: string | null;
  attachments?: string[];
  requiredApprovals?: number;
}

export interface SafetyDutyReviewCommand {
  approved: boolean;
  opinion: string;
}

export interface SafetyDutyReviewVo {
  id: string;
  level: number;
  decision: SafetyDutyReviewDecision;
  reviewerId: string;
  opinion: string;
  reviewedAt: number;
}
export interface SafetyDutyLogVo extends Omit<SafetyDutyLogCommand, 'attachments'> {
  id: string;
  orgId: string;
  dutyUserId: string;
  attachments: string[];
  reviewStatus: SafetyReviewStatus;
  reviewerId?: string | null;
  reviewOpinion?: string | null;
  reviewedAt?: number | null;
  requiredApprovals: number;
  approvalCount: number;
  reviews: SafetyDutyReviewVo[];
}

export interface SafetyDutyAdjustmentCommand {
  scheduleId: string;
  targetUserId: string;
  reason: string;
}

export interface SafetyDutyAdjustmentReviewCommand {
  approved: boolean;
  opinion: string;
}

export interface SafetyDutyAdjustmentVo extends SafetyDutyAdjustmentCommand {
  id: string;
  orgId: string;
  originalUserId: string;
  status: SafetyDutyAdjustmentStatus;
  requesterId: string;
  requestedAt: number;
  reviewerId?: string | null;
  reviewOpinion?: string | null;
  reviewedAt?: number | null;
}

export type SafetyInspectionCycle = 'WORKDAY' | 'WEEKEND' | 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'CUSTOM';
export type SafetyInspectionSource = 'MANUAL' | 'INGEST_REFERENCE' | 'MIXED';
export type SafetyInspectionResult = 'NORMAL' | 'ABNORMAL';
export type SafetyIssueStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'VERIFIED' | 'CANCELLED';

export interface SafetyInspectionPoint {
  code: string;
  name: string;
  longitude?: number | null;
  latitude?: number | null;
}

export interface SafetyCheckpointCommand {
  orgId?: string | null;
  name: string;
  locationId: string;
  deviceId?: string | null;
  description?: string | null;
  enabled: boolean;
}

export interface SafetyCheckpointVo extends SafetyCheckpointCommand {
  id: string;
  orgId: string;
  code: string;
  locationName: string;
}

export interface SafetyRoutePoint {
  checkpointId: string;
  required: boolean;
}
export interface SafetyRouteCommand {
  orgId?: string | null;
  name: string;
  areaLocationId?: string | null;
  points: SafetyRoutePoint[];
}
export interface SafetyRouteVo extends SafetyRouteCommand {
  id: string;
  orgId: string;
  code: string;
}
export interface SafetyInspectionTaskVo {
  id: string;
  orgId: string;
  planId: string;
  routeId: string;
  scheduledAt: number;
  dueAt: number;
  inspectorId: string | null;
  points: SafetyRoutePoint[];
  completedPointIds: string[];
  status: 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'OVERDUE';
}

export interface SafetyInspectionPlanCommand {
  orgId?: string | null;
  name: string;
  routeId: string;
  frequencyPerDay: number;
  cycle: SafetyInspectionCycle;
  firstStartsAt: number;
  inspectorQualification?: string | null;
}

export interface SafetyInspectionPlanVo extends SafetyInspectionPlanCommand {
  id: string;
  orgId: string;
  code: string;
  areaLocationId: string | null;
  routeCode: string;
  requiredPoints: SafetyInspectionPoint[];
  optionalPoints: SafetyInspectionPoint[];
}

export interface SafetyInspectionRecordCommand {
  orgId?: string | null;
  taskId: string;
  inspectorId: string;
  inspectedAt: number;
  checkpointId: string;
  deviceId?: string | null;
  collectionCode?: string | null;
  source: SafetyInspectionSource;
  ingestRecordIds?: string[];
  observations?: Record<string, unknown>;
  result: SafetyInspectionResult;
  description?: string | null;
  attachments?: string[];
}

export interface SafetyInspectionRecordVo
  extends Omit<SafetyInspectionRecordCommand, 'orgId' | 'ingestRecordIds' | 'observations' | 'attachments'> {
  id: string;
  orgId: string;
  planId: string | null;
  pointCode: string;
  locationId: string | null;
  ingestRecordIds: string[];
  observations: Record<string, unknown>;
  attachments: string[];
}

export interface SafetyInspectionIssueCommand {
  issueType: string;
  description: string;
  assigneeId: string;
  dueAt: number;
  attachments?: string[];
}

export interface SafetyIssueActionCommand {
  status: SafetyIssueStatus;
  result: string;
  completedAt?: number | null;
  maintenanceTaskId?: string | null;
}

export interface SafetyInspectionIssueVo extends Omit<SafetyInspectionIssueCommand, 'attachments'> {
  id: string;
  orgId: string;
  inspectionRecordId: string;
  status: SafetyIssueStatus;
  result?: string | null;
  completedAt?: number | null;
  maintenanceTaskId?: string | null;
  attachments: string[];
}

export type SafetyEquipmentClass = 'SECURITY' | 'FIRE' | 'LIGHTNING';
export type SafetyAssetStatus = 'IN_SERVICE' | 'OUT_OF_SERVICE' | 'MAINTENANCE' | 'RETIRED';

export interface SafetyDeviceProfileCommand {
  orgId?: string | null;
  equipmentClass: SafetyEquipmentClass;
  installedAt?: number | null;
  commissionedAt?: number | null;
  ownerUserId: string;
  assetStatus: SafetyAssetStatus;
  complianceBasis?: SafetyComplianceBasis[];
}

export interface SafetyDeviceProfileVo extends Omit<SafetyDeviceProfileCommand, 'orgId' | 'complianceBasis'> {
  id: string;
  profiled: boolean;
  orgId: string;
  deviceId: string;
  deviceCode: string;
  deviceName: string;
  locationId?: string | null;
  locationPath?: string | null;
  lastSeenAt?: number | null;
  online: boolean;
  domain: SafetyDomain;
  equipmentType: string;
  manufacturer?: string | null;
  model?: string | null;
  identifiers: Record<string, string>;
  technicalAttributes: Record<string, unknown>;
  complianceBasis: SafetyComplianceBasis[];
}

export type SafetyMaintenanceStatus = 'PENDING' | 'ASSIGNED' | 'PROCESSING' | 'COMPLETED' | 'VERIFIED' | 'CANCELLED';

export interface SafetyMaintenancePlanCommand {
  orgId?: string | null;
  code: string;
  name: string;
  domain: SafetyDomain;
  equipmentType: string;
  deviceId?: string | null;
  maintenanceType: string;
  cycleDays: number;
  nextDueAt: number;
  responsibleUserId: string;
  expectedMinutes?: number | null;
  professionalOrganization?: string | null;
  weatherWarningLinked?: boolean;
  complianceBasis?: SafetyComplianceBasis[];
}

export interface SafetyMaintenancePlanVo extends Omit<SafetyMaintenancePlanCommand, 'orgId'> {
  id: string;
  orgId: string;
}

export interface SafetyMaintenanceTaskCommand {
  orgId?: string | null;
  planId?: string | null;
  issueId?: string | null;
  deviceId: string;
  assigneeId: string;
  scheduledAt: number;
  instructions: string;
}

export interface SafetyMaintenanceCompleteCommand {
  completedAt: number;
  actualMinutes: number;
  content: string;
  measurements?: Record<string, unknown>;
  standardLimits?: Record<string, unknown>;
  calibrationResult?: string | null;
  parts?: Array<Record<string, unknown>>;
  partsCostCents?: number;
  laborCostCents?: number;
  testingOrganization?: string | null;
  instrumentNo?: string | null;
  certificateNo?: string | null;
  compliant: boolean;
  recommendation?: string | null;
  effectAssessment?: string | null;
  attachments?: string[];
}

export interface SafetyMaintenanceTaskVo extends Omit<SafetyMaintenanceTaskCommand, 'orgId'> {
  id: string;
  orgId: string;
  startedAt?: number | null;
  completedAt?: number | null;
  status: SafetyMaintenanceStatus;
  resultSnapshot: Record<string, unknown>;
}

export interface SafetyMaintenanceRecordVo {
  id: string;
  orgId: string;
  taskId: string;
  deviceId: string;
  completedAt: number;
  assigneeId: string;
  snapshot: Record<string, unknown>;
}

export type SafetyTrainingMode = 'COURSE' | 'LAW' | 'POLICY' | 'MANUAL' | 'DRILL';
export type SafetyAssessmentStatus = 'NOT_STARTED' | 'LEARNING' | 'PASSED' | 'FAILED';

export interface SafetyTrainingPlanCommand {
  orgId?: string | null;
  code: string;
  name: string;
  departmentId: string;
  mode: SafetyTrainingMode;
  startsAt: number;
  endsAt: number;
  participantIds: string[];
  documentIds?: string[];
  courses: string[];
  assessmentMethod: string;
  passScore?: number | null;
}

export interface SafetyTrainingPlanVo
  extends Omit<SafetyTrainingPlanCommand, 'orgId' | 'documentIds' | 'departmentId'> {
  id: string;
  orgId: string;
  department: string;
  departmentId?: string | null;
  documentIds: string[];
  completedCount: number;
  completionRate: number;
}

export interface SafetyLearningRecordCommand {
  planId: string;
  studentId: string;
  startedAt: number;
  completedAt?: number | null;
  durationMinutes: number;
  score?: number | null;
  status: SafetyAssessmentStatus;
  attachments?: string[];
}

export interface SafetyLearningRecordVo extends Omit<SafetyLearningRecordCommand, 'attachments'> {
  id: string;
  orgId: string;
  attachments: string[];
}

export interface SafetyWorkbenchDeviceCount {
  equipmentClass: SafetyEquipmentClass;
  total: number;
  online: number;
  offline: number;
}

export type SafetyNoticeLevel = 'NORMAL' | 'IMPORTANT' | 'URGENT';

export interface SafetyNoticeCommand {
  orgId?: string | null;
  title: string;
  content: string;
  level: SafetyNoticeLevel;
  publishAt: number;
  expiresAt?: number | null;
  attachments?: string[];
}

export interface SafetyNoticeVo extends Omit<SafetyNoticeCommand, 'orgId' | 'attachments'> {
  id: string;
  orgId: string;
  attachments: string[];
}

export interface SafetyWorkbenchRiskVo {
  id: string;
  orgId: string;
  domain: SafetyDomain;
  level: SafetyRiskLevel;
  title: string;
  status: SafetyRiskStatus;
  lastOccurredAt: number;
}

export interface SafetyVideoCameraVo {
  id: string;
  parentId: string;
  serverId?: string | null;
  online?: boolean | null;
  name: string;
  code: string;
}

export interface SafetyManagementWorkbenchVo {
  generatedAt: number;
  scheduledDutyCount: number;
  arrivedDutyCount: number;
  openIssueCount: number;
  overdueIssueCount: number;
  overdueMaintenanceCount: number;
  effectiveDocumentCount: number;
  activeRiskCount: number;
  deviceCounts: SafetyWorkbenchDeviceCount[];
  latestInspections: SafetyInspectionRecordVo[];
  latestRisks: SafetyWorkbenchRiskVo[];
  latestNotices: SafetyNoticeVo[];
}
