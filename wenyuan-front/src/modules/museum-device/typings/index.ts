export type IngestProtocol = 'HTTP' | 'MQTT' | 'TCP' | 'UDP' | 'SERIAL' | 'CAN';
export type IngestGatewayKind = 'PHYSICAL_GATEWAY' | 'BUSINESS_SOURCE';
export type IngestTransport = 'MQTT' | 'HTTP' | 'TCP' | 'UDP' | 'SERIAL' | 'CAN';
export type IngestConnectorType =
  | 'MQTT_CLIENT'
  | 'HTTP_CLIENT'
  | 'TCP_CLIENT'
  | 'UDP_SERVER'
  | 'SERIAL_PORT'
  | 'CAN_BUS';
export type IngestAcquisitionMode = 'PUSH' | 'POLL';
export type IngestConnectorProbeStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED';
export type IngestRouteMatchType =
  | 'NONE'
  | 'JSON_FIELD_EQUALS'
  | 'METADATA_EQUALS'
  | 'PAYLOAD_PREFIX_HEX'
  | 'PAYLOAD_PREFIX_TEXT';
export type IngestRecordType =
  | 'TELEMETRY'
  | 'STATE_CHANGE'
  | 'DEVICE_EVENT'
  | 'ALARM_SIGNAL'
  | 'COMMAND_RESULT'
  | 'DEVICE_HEALTH';
export type IngestDataQuality = 'NORMAL' | 'SUSPECT' | 'INVALID';
export type AlarmRuleType = 'VALUE_CONDITION' | 'STATE_CONDITION' | 'DATA_QUALITY' | 'DEVICE_OFFLINE';
export type AlarmOperator = 'GT' | 'GTE' | 'LT' | 'LTE' | 'EQ' | 'NE' | 'BETWEEN' | 'OUTSIDE';
export type AlarmSeverity = 'NOTICE' | 'WARNING' | 'MAJOR' | 'CRITICAL';
export type AlarmLifecycleStatus = 'OPEN' | 'CLOSED';
export type IngestDeviceKind = 'PHYSICAL' | 'STANDARD_SOURCE' | 'EXTERNAL_DEVICE';
export type RfidDeviceBusinessRole =
  | 'DESK_READER'
  | 'TAG_WRITER'
  | 'HANDHELD'
  | 'WAREHOUSE_GATE'
  | 'MUSEUM_GATE'
  | 'OPERATOR_CARD_READER'
  | 'TAG_PRINTER'
  | 'DOCUMENT_PRINTER';
export type StandardMetricValueType = 'NUMBER' | 'STRING' | 'BOOLEAN';
export type StandardMetricAggregation = 'GAUGE' | 'INCREMENT' | 'CUMULATIVE' | 'STATE';
export type StandardMetricCandidateStatus = 'PENDING' | 'MAPPED' | 'IGNORED';

export interface GatewayVo {
  id?: string;
  orgId?: string;
  name?: string;
  code?: string;
  kind?: IngestGatewayKind;
  tokenHint?: string;
  siteId?: string;
  protocol?: IngestProtocol;
  vendor?: string;
  lastReportTime?: number;
  remarks?: string;
  status?: string;
}

export interface MuseumVenueVo {
  id: string;
  parentId?: string | null;
  code: string;
  name: string;
  shortName?: string | null;
}

export interface IngestMqttProtocolAccessDefinition {
  topicTemplate: string;
  allowedTopicPatterns: string[];
  defaultQos: number;
  keepAliveSeconds: number;
}

export interface IngestProtocolDefinition {
  code: string;
  name: string;
  vendor: string;
  version: string;
  transports: IngestTransport[];
  description?: string;
  defaultRoutePattern?: string;
  defaultCodecCode?: string;
  historySyncSupported?: boolean;
  mqttAccess?: IngestMqttProtocolAccessDefinition | null;
}

export interface IngestCodecDefinition {
  code: string;
  name: string;
  vendor: string;
  version: string;
  protocolCodes: string[];
  models: string[];
  metricKeys: string[];
}

export interface IngestDeviceModelMetricDefinition {
  externalMetricKey: string;
  metricCode: string;
  name: string;
  unit?: string | null;
  scale: number;
  offset: number;
  valueFieldMappings: Record<string, string>;
}

export interface IngestDeviceModelDefinition {
  code: string;
  name: string;
  vendor: string;
  version: string;
  codecCode: string;
  protocolCodes: string[];
  metrics: IngestDeviceModelMetricDefinition[];
  dynamicMetrics: boolean;
}

export interface IngestNetworkAddressDefinition {
  interfaceName: string;
  interfaceDisplayName: string;
  address: string;
  family: string;
  loopback: boolean;
  linkLocal: boolean;
  siteLocal: boolean;
  multicast: boolean;
  virtual: boolean;
  pointToPoint: boolean;
}

export interface IngestBuiltInConnectorDefinition {
  code: string;
  name: string;
  type: 'MQTT_SERVER' | 'HTTP_SERVER' | 'TCP_SERVER' | 'UDP_SERVER';
  transport: IngestTransport;
  enabled: boolean;
  bindHost?: string | null;
  bindPort?: number | null;
  networkAddresses: IngestNetworkAddressDefinition[];
  tlsEnabled: boolean;
  path?: string | null;
  protocolVersions: string[];
  preferredVersion?: string | null;
  lastSeenAt?: number | null;
}

export interface IngestCatalog {
  protocols: IngestProtocolDefinition[];
  codecs: IngestCodecDefinition[];
  deviceModels: IngestDeviceModelDefinition[];
  builtInConnectors: IngestBuiltInConnectorDefinition[];
  standardMetrics: StandardMetricDefinition[];
}

export interface StandardMetricDefinition {
  code: string;
  name: string;
  category: string;
  recordType: IngestRecordType;
  valueType: StandardMetricValueType;
  aggregation: StandardMetricAggregation;
  unit?: string | null;
  description?: string | null;
  valueFields: MetricValueField[];
  aliases: string[];
  enabled: boolean;
  sort: number;
}

export interface MetricValueField {
  code: string;
  name: string;
  slot: number;
  required: boolean;
}

export interface StandardMetricCandidateVo {
  id: string;
  orgId: string;
  sourceCode: string;
  externalMetricKey: string;
  sampleValue?: string | null;
  mappedMetricCode?: string | null;
  candidateStatus: StandardMetricCandidateStatus;
  firstSeenAt: number;
  lastSeenAt: number;
  seenCount: number;
}

export interface MetricCandidateSettingsVo {
  enabled: boolean;
}

export interface IngestConnectorVo {
  id?: string;
  code?: string;
  name?: string;
  type?: IngestConnectorType;
  acquisitionMode?: IngestAcquisitionMode;
  enabled?: boolean;
  gatewayIds?: string[];
  brokerUri?: string;
  topic?: string;
  username?: string | null;
  password?: string | null;
  clearPassword?: boolean;
  passwordConfigured?: boolean;
  clientId?: string;
  qos?: number;
  tokenSource?: 'PAYLOAD' | 'TOPIC_SUFFIX';
  baseUrl?: string;
  host?: string;
  port?: number;
  pageSize?: number;
  sceneIds?: string[];
  nodeDataType?: number;
  initialLookbackSeconds?: number;
  overlapSeconds?: number;
  pollIntervalSeconds?: number;
  timeoutSeconds?: number;
  remarks?: string | null;
  probeStatus?: IngestConnectorProbeStatus | null;
  probeRequestedAt?: number | null;
  probeStartedAt?: number | null;
  probeCompletedAt?: number | null;
  probeRuntimeInstanceId?: string | null;
  probeResultCode?: string | null;
  probeMessage?: string | null;
  probeDurationMillis?: number | null;
}

export interface IngestConnectorProbeVo {
  connectorId: string;
  connectorCode: string;
  status?: IngestConnectorProbeStatus | null;
  requestedAt?: number | null;
  startedAt?: number | null;
  completedAt?: number | null;
  runtimeInstanceId?: string | null;
  resultCode?: string | null;
  message?: string | null;
  durationMillis?: number | null;
}

export interface IngestRouteVo {
  id?: string;
  orgId?: string;
  connectorId?: string;
  gatewayId?: string;
  transport?: IngestTransport;
  routePattern?: string;
  protocolCode?: string;
  protocolConfig?: string | null;
  matchType?: IngestRouteMatchType;
  matchField?: string | null;
  matchValue?: string | null;
  priority?: number;
}

export interface IngestRouteMatchTestVo {
  connectorId?: string;
  transport?: IngestTransport;
  actualRoute?: string;
  payload?: string;
  payloadEncoding?: 'TEXT' | 'BASE64' | 'HEX';
  metadata?: Record<string, string>;
}

export interface IngestRouteMatchEvaluationVo {
  routeId: string;
  gatewayId: string;
  routePattern: string;
  protocolCode: string;
  priority: number;
  matchType: IngestRouteMatchType;
  addressMatched: boolean;
  conditionMatched: boolean;
  selected: boolean;
}

export interface IngestRouteMatchResultVo {
  matched: boolean;
  selectedRouteId?: string | null;
  protocolCode?: string | null;
  gatewayId?: string | null;
  evaluations: IngestRouteMatchEvaluationVo[];
}

export interface MqttPublishProfileVo {
  routeId: string;
  protocolCode: string;
  protocolName: string;
  topic: string;
  qos: number;
  keepAliveSeconds: number;
}

export interface MqttClientConnectionVo {
  gatewayId: string;
  gatewayName: string;
  tlsEnabled: boolean;
  serverUris: string[];
  supportedVersions: string[];
  clientId: string;
  username: string;
  password: string;
  passwordConfigured: boolean;
  retain: boolean;
  cleanStart: boolean;
  publishProfiles: MqttPublishProfileVo[];
}

export interface IngestHistorySyncTaskVo {
  id?: string;
  orgId?: string;
  connectorId?: string;
  connectorName?: string;
  gatewayId?: string;
  gatewayName?: string;
  routeId?: string;
  routePattern?: string;
  protocolCode?: string;
  protocolName?: string;
  fromTime?: number;
  toTime?: number;
  taskStatus?: 'PENDING' | 'PROCESSING' | 'SUCCESS' | 'FAILED';
  deviceCount?: number;
  metricCount?: number;
  fetchedCount?: number;
  insertedCount?: number;
  skippedCount?: number;
  startedAt?: number;
  completedAt?: number;
  errorMessage?: string;
  createTime?: number;
}

export interface IngestInboxSummaryVo {
  id: string;
  connectorId: string;
  transport: IngestTransport;
  route?: string | null;
  receivedAt: number;
  status: 'UNRECOGNIZED' | 'DEAD';
  attempts: number;
  processingAt?: number | null;
  errorMessage?: string | null;
  failureStage?: string | null;
  failureCode?: string | null;
  gatewayId?: string | null;
  gatewayName?: string | null;
  protocolCode?: string | null;
  externalDeviceId?: string | null;
  payloadPreview: string;
  processedAt?: number | null;
}

export interface IngestInboxDetailVo {
  id: string;
  payload: string;
  payloadEncoding: 'TEXT' | 'BASE64';
  payloadSize: number;
}

export interface DeviceCandidateVo {
  id: string;
  name?: string | null;
  kind?: IngestDeviceKind;
  orgId: string;
  gatewayId: string;
  gatewayName?: string | null;
  externalDeviceId: string;
  protocolCode: string;
  modelHint?: string | null;
  suggestedModelCode?: string | null;
  suggestedModelName?: string | null;
  firstSeenAt: number;
  lastSeenAt: number;
  seenCount: number;
  lastInboxId?: string | null;
  candidateStatus: 'PENDING' | 'REGISTERED' | 'IGNORED';
}

export interface DeviceCandidateRegistrationResultVo {
  deviceId: string;
  replayedCount: number;
  replayPending?: boolean;
}

export interface DeviceCandidateBatchRegistrationVo {
  candidateIds: string[];
  locationId: string;
  businessRoles?: RfidDeviceBusinessRole[];
  rfidAntennaCount?: number;
}

export interface DeviceCandidateBatchRegistrationResultVo {
  deviceCount: number;
  deviceIds: string[];
  replayedCount: number;
  replayPending?: boolean;
}

export interface DeviceCandidateDeleteVo {
  candidateIds?: string[];
  allMatched?: boolean;
  gatewayId?: string;
  externalDeviceId?: string;
  modelCode?: string;
  protocolCode?: string;
  modelHint?: string;
}

export interface DeviceCandidateDeleteResultVo {
  deletedCount: number;
}

export interface MuseumLocationOptionVo {
  id: string;
  siteId?: string | null;
  code: string;
  name: string;
  type: string;
  path?: string | null;
}

export interface MuseumLocationSourceMappingVo {
  id: string;
  sourceType: string;
  sourceCode: string;
  externalId: string;
  externalCode?: string | null;
  externalName?: string | null;
  primarySource: boolean;
  lastSyncedAt: number;
  status: string;
}

export interface MuseumLocationManageVo {
  id?: string;
  orgId?: string;
  siteId?: string | null;
  parentId?: string | null;
  rootId?: string | null;
  code?: string;
  name?: string;
  type?: string;
  path?: string | null;
  manual?: boolean;
  status?: string;
  deviceCount?: number;
  latestDataAt?: number | null;
  sources?: MuseumLocationSourceMappingVo[];
  children?: MuseumLocationManageVo[];
}

export interface MuseumSpatialLocationVo {
  id: string;
  orgId: string;
  siteId?: string | null;
  parentId?: string | null;
  rootId?: string | null;
  depth: number;
  code: string;
  name: string;
  type: string;
  path?: string | null;
  planCount: number;
  deviceCount: number;
  sources: MuseumSpatialLocationSourceVo[];
  children?: MuseumSpatialLocationVo[];
}

export interface MuseumSpatialLocationSourceVo {
  sourceType: string;
  sourceCode: string;
  sourceLocationId: string;
}

export interface MuseumLocationPlanVo {
  id: string;
  orgId: string;
  locationId: string;
  locationName: string;
  locationPath?: string | null;
  name: string;
  planType: string;
  fileId: string;
  filePath?: string | null;
  imageWidth: number;
  imageHeight: number;
  defaultPlan: boolean;
  sort: number;
  revision: number;
  status: string;
}

export interface MuseumLocationPlanSaveVo {
  id?: string;
  locationId?: string;
  name?: string;
  planType?: string;
  fileId?: string;
  imageWidth?: number;
  imageHeight?: number;
  defaultPlan?: boolean;
  sort?: number;
  revision?: number;
  preserveAnchors?: boolean;
}

export type SpatialAnchorTargetType = 'DEVICE' | 'LOCATION';

export interface MuseumLocationPlanAnchorSaveVo {
  targetType: SpatialAnchorTargetType;
  targetId: string;
  xRatio: number;
  yRatio: number;
  displayName?: string | null;
  labelOffsetX?: number;
  labelOffsetY?: number;
  zIndex?: number;
}

export interface MuseumLocationPlanAnchorBatchVo {
  revision: number;
  anchors: MuseumLocationPlanAnchorSaveVo[];
}

export interface MuseumSpatialMetricVo {
  key: string;
  name?: string | null;
  numericValue?: number | null;
  stringValue?: string | null;
  booleanValue?: boolean | null;
  unit?: string | null;
  occurredAt: number;
  receivedAt: number;
}

export type MetricSeriesMode = 'RAW' | 'STATISTICS';

export interface MuseumMetricSeriesPointVo {
  occurredAt: number;
  value: number;
  minValue: number;
  maxValue: number;
  count: number;
}

export interface MuseumMetricFieldSeriesVo {
  fieldCode: string;
  fieldName: string;
  slot: number;
  total: number;
  sampled: boolean;
  latestValue?: number | null;
  latestOccurredAt?: number | null;
  minValue?: number | null;
  maxValue?: number | null;
  averageValue?: number | null;
  sumValue?: number | null;
  changeRate?: number | null;
  points: MuseumMetricSeriesPointVo[];
}

export interface MuseumDeviceLocationChangeVo {
  id: string;
  deviceId: string;
  fromLocationId?: string | null;
  fromLocationName?: string | null;
  toLocationId?: string | null;
  toLocationName?: string | null;
  changedAt: number;
  changedBy?: string | null;
  reason?: string | null;
}

export interface MuseumMetricSeriesVo {
  deviceId: string;
  key: string;
  name?: string | null;
  unit?: string | null;
  mode: MetricSeriesMode;
  aggregation: StandardMetricAggregation;
  from: number;
  to: number;
  bucketMinutes?: number | null;
  total: number;
  sampled: boolean;
  latestValue?: number | null;
  latestOccurredAt?: number | null;
  minValue?: number | null;
  maxValue?: number | null;
  averageValue?: number | null;
  sumValue?: number | null;
  changeRate?: number | null;
  points: MuseumMetricSeriesPointVo[];
  fields: MuseumMetricFieldSeriesVo[];
  currentLocationId?: string | null;
  currentLocationPath?: string | null;
  locationChangedInRange: boolean;
  locationChanges: MuseumDeviceLocationChangeVo[];
}

export interface MuseumComparisonSeriesRequestVo {
  seriesId: string;
  deviceId: string;
  key: string;
  fieldCode?: string | null;
}

export interface MuseumComparisonRequestVo {
  orgId?: string | null;
  from: number;
  to: number;
  bucketMinutes?: number | null;
  maxPoints?: number;
  qualities?: IngestDataQuality[];
  includeBackfill?: boolean;
  series: MuseumComparisonSeriesRequestVo[];
}

export interface MuseumAnalysisMetricOptionVo {
  code: string;
  name: string;
  unit?: string | null;
  aggregation: StandardMetricAggregation;
  enabled: boolean;
  valueFields: MetricValueField[];
}

export interface MuseumAnalysisMetricOptionsVo {
  deviceId: string;
  metrics: MuseumAnalysisMetricOptionVo[];
}

export interface MuseumComparisonSeriesVo {
  seriesId: string;
  deviceId: string;
  deviceCode: string;
  deviceName: string;
  locationId?: string | null;
  locationPath?: string | null;
  key: string;
  name: string;
  fieldCode: string;
  fieldName: string;
  unit?: string | null;
  aggregation: StandardMetricAggregation;
  total: number;
  bucketCount: number;
  expectedBucketCount: number;
  coverageRate: number;
  latestValue?: number | null;
  latestOccurredAt?: number | null;
  minValue?: number | null;
  maxValue?: number | null;
  averageValue?: number | null;
  sumValue?: number | null;
  locationChangedInRange: boolean;
}

export interface MuseumComparisonBucketValueVo {
  seriesId: string;
  value: number;
  minValue: number;
  maxValue: number;
  count: number;
}

export interface MuseumComparisonBucketVo {
  occurredAt: number;
  values: MuseumComparisonBucketValueVo[];
}

export interface MuseumComparisonVo {
  from: number;
  to: number;
  bucketMinutes: number;
  qualities: IngestDataQuality[];
  includeBackfill: boolean;
  series: MuseumComparisonSeriesVo[];
  buckets: MuseumComparisonBucketVo[];
}

export interface MuseumComparisonSeriesDraft {
  seriesId: string;
  deviceId: string | null;
  key: string | null;
  fieldCode?: string | null;
}

export interface MuseumSpatialAlarmVo {
  id: string;
  code: string;
  title: string;
  severity: AlarmSeverity;
  occurredAt: number;
}

export interface MuseumSpatialDeviceVo {
  id: string;
  code: string;
  name: string;
  modelCode: string;
  locationId?: string | null;
  locationPath?: string | null;
  lastSeenAt?: number | null;
  state: 'NORMAL' | 'NO_DATA' | 'OFFLINE' | 'ALARM';
  latestMetrics: MuseumSpatialMetricVo[];
  activeAlarm?: MuseumSpatialAlarmVo | null;
}

export interface MuseumSpatialAnchorVo extends MuseumLocationPlanAnchorSaveVo {
  id: string;
  targetName?: string | null;
  labelOffsetX: number;
  labelOffsetY: number;
  zIndex: number;
}

export interface MuseumLocationPlanViewVo {
  plan: MuseumLocationPlanVo;
  anchors: MuseumSpatialAnchorVo[];
  devices: MuseumSpatialDeviceVo[];
  childLocations: MuseumSpatialLocationVo[];
}

export interface DeviceVo {
  id?: string;
  orgId?: string;
  code?: string;
  name?: string;
  kind?: IngestDeviceKind;
  modelCode?: string;
  gatewayId?: string;
  protocolCode?: string;
  externalDeviceId?: string;
  siteId?: string;
  locationId?: string;
  locationPath?: string;
  instanceConfig?: string | null;
  lastSeenAt?: number | null;
  modelName?: string;
  codecCode?: string;
  gatewayName?: string;
  locationChangeReason?: string | null;
  businessRoles?: RfidDeviceBusinessRole[];
  rfidHardwareSerial?: string | null;
  rfidAntennaCount?: number | null;
}

export interface DeviceMetricBindingVo {
  id?: string;
  orgId?: string;
  deviceId?: string;
  externalMetricKey?: string;
  metricCode?: string;
  scale?: number;
  offset?: number;
  valueFieldMappings?: Record<string, string>;
  enabled?: boolean;
  /** 协议提供的厂商原名；只读展示，不覆盖人工映射。 */
  sourceMetricName?: string | null;
  /** 厂商单位或状态文本，与标准单位分开。 */
  sourceUnit?: string | null;
}

export interface IngestRecordVo {
  id: string;
  orgId: string;
  inboxId?: string | null;
  sourceCode?: string | null;
  sourceMessageId: string;
  sourceRecordId: string;
  siteId?: string | null;
  locationId?: string | null;
  gatewayId?: string | null;
  deviceId: string;
  recordType: IngestRecordType;
  key: string;
  numericValue?: number | null;
  values: Record<string, number>;
  stringValue?: string | null;
  booleanValue?: boolean | null;
  unit?: string | null;
  attributes: Record<string, unknown>;
  occurredAt: number;
  receivedAt: number;
  quality: IngestDataQuality;
  backfill: boolean;
  gatewayName?: string | null;
  deviceName?: string | null;
  locationName?: string | null;
  locationPath?: string | null;
  metricName?: string | null;
  metricUnit?: string | null;
  sourceMetricName?: string | null;
  sourceUnit?: string | null;
}

export interface RecordFilterDeviceVo {
  id: string;
  orgId: string;
  code: string;
  name: string;
  externalDeviceId: string;
  locationId?: string | null;
  locationPath?: string | null;
  status: string;
}

export interface RecordFilterLocationVo {
  id: string;
  orgId: string;
  parentId?: string | null;
  code: string;
  name: string;
  type: string;
  path?: string | null;
  status: string;
}

export interface RecordFilterOptionsVo {
  devices: RecordFilterDeviceVo[];
  locations: RecordFilterLocationVo[];
}

export interface AlarmRuleVo {
  id?: string;
  orgId?: string;
  code?: string;
  name?: string;
  ruleType?: AlarmRuleType;
  recordType?: IngestRecordType | null;
  recordKey?: string;
  siteId?: string | null;
  locationId?: string | null;
  deviceId?: string | null;
  managedBy?: 'MANUAL' | 'COLLECTION_PRESERVATION';
  runtimeStatus?: 'ACTIVE' | 'SUSPENDED' | 'ARCHIVED';
  activeVersionId?: string | null;
  versionNo?: number | null;
  triggerCount?: number;
  recoveryCount?: number;
  includeBackfill?: boolean;
  messageTemplate?: string | null;
  standardRef?: string | null;
  sourceDefinitionId?: string | null;
  sourceDefinitionVersion?: number | null;
  variants: AlarmRuleVariantVo[];
  locationName?: string | null;
  locationPath?: string | null;
  deviceName?: string | null;
  deviceCode?: string | null;
  deviceLocationPath?: string | null;
}

export interface AlarmRuleConditionVo {
  id?: string;
  recordType?: IngestRecordType;
  recordKey?: string;
  fieldCode?: string;
  operator?: AlarmOperator;
  thresholdValue?: number | null;
  thresholdValue2?: number | null;
  expectedValue?: string | null;
}

export interface AlarmRuleBranchVo {
  id?: string;
  code?: string;
  name?: string;
  severity?: AlarmSeverity;
  priority?: number;
  conditions: AlarmRuleConditionVo[];
}

export interface AlarmRuleVariantVo {
  id?: string;
  code?: string;
  name?: string;
  priority?: number;
  siteId?: string | null;
  locationId?: string | null;
  deviceId?: string | null;
  contextJson?: string | null;
  branches: AlarmRuleBranchVo[];
}

export interface AlarmRuleVersionRefVo { id: string; ruleId: string; ruleName: string; versionNo: number; }
export interface AlarmRuleVersionDetailVo {
  id: string; ruleId: string; orgId: string; ruleName: string; ruleCode: string;
  managedBy: 'MANUAL' | 'COLLECTION_PRESERVATION'; currentPublishedVersionId?: string | null;
  versionNo: number; versionStatus: 'PUBLISHED' | 'SUPERSEDED'; ruleType: AlarmRuleType;
  triggerCount: number; recoveryCount: number; includeBackfill: boolean;
  messageTemplate?: string | null; standardRef?: string | null; effectiveFrom: number; effectiveTo?: number | null;
  variants: AlarmRuleVariantVo[];
}
export interface AlarmDeviceReferenceVo {
  id: string; orgId: string; code: string; name: string; modelCode: string;
  externalDeviceId: string; locationPath?: string | null; lastSeenAt?: number | null;
}
export type AlarmSourceCategory = 'VIDEO' | 'ENV_ANALYSIS' | 'OTHER_EVENT';
export interface AlarmEventVo {
  id: string;
  orgId: string;
  siteId?: string | null;
  locationId?: string | null;
  locationSnapshotPath?: string | null;
  gatewayId: string;
  deviceId: string;
  ruleId?: string | null;
  source: 'RULE' | 'DEVICE' | 'VENUE';
  sourceCategory?: AlarmSourceCategory | null;
  alarmCode: string;
  title: string;
  severity: AlarmSeverity;
  currentSeverity?: AlarmSeverity;
  deviceName?: string | null;
  triggerRuleVersion?: AlarmRuleVersionRefVo | null;
  currentRuleVersion?: AlarmRuleVersionRefVo | null;
  triggerRuleVersionId?: string | null;
  currentRuleVersionId?: string | null;
  triggerBranchId?: string | null;
  currentBranchId?: string | null;
  lifecycleStatus: AlarmLifecycleStatus;
  sourceRecordId: string;
  triggerValue?: string | null;
  triggerUnit?: string | null;
  fieldCode: string;
  conditionSnapshot?: string | null;
  detailsJson?: string | null;
  startedAt: number;
  lastOccurredAt: number;
  endedAt?: number | null;
  terminationReason?: string | null;
  occurrenceCount: number;
}
