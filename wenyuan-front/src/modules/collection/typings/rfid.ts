export type RfidWorkstationCapability =
  | 'BORROW_RETURN'
  | 'INVENTORY'
  | 'TAG_ISSUE'
  | 'TAG_PRINT'
  | 'DOCUMENT_PRINT'
  | 'PASSAGE_MONITOR';
export type RfidWorkstationDevicePurpose =
  | 'RFID_READ'
  | 'RFID_WRITE'
  | 'CARD_READ'
  | 'TAG_PRINT'
  | 'DOCUMENT_PRINT'
  | 'PASSAGE_READ';
export type RfidDirection = 'IN' | 'OUT' | 'UNKNOWN';
export type RfidSessionType =
  | 'LOAN_OUT'
  | 'REQUISITION_OUT'
  | 'RETURN_IN'
  | 'INVENTORY'
  | 'TAG_VERIFY'
  | 'PASSAGE_DIAG';
export type RfidSessionState =
  | 'CREATED'
  | 'STARTING'
  | 'READING'
  | 'STOPPING'
  | 'PREVIEW_READY'
  | 'FINALIZING'
  | 'FINALIZED'
  | 'CANCELLED'
  | 'EXPIRED'
  | 'FAILED';
export type RfidReadClassification = 'EXPECTED' | 'MISSING' | 'EXTRA' | 'UNKNOWN' | 'CONFLICT';
export type RfidRealtimeEventType =
  | 'DEVICE_STATUS'
  | 'OPERATOR_CARD_READ'
  | 'AUTH_STATE'
  | 'SESSION_STATE'
  | 'TAG_BATCH'
  | 'COMMAND_RESULT'
  | 'GATE_EVENT'
  | 'BUSINESS_ANOMALY'
  | 'RESYNC_REQUIRED';

export interface RfidRealtimeEvent {
  id: string;
  type: RfidRealtimeEventType;
  workstationId?: string;
  sessionId?: string;
  sequence: number;
  occurredAt: number;
  payload: Record<string, unknown>;
}

export interface RfidWorkstation {
  id: string;
  orgId: string;
  code: string;
  name: string;
  defaultCapability?: RfidWorkstationCapability;
  locationId?: string;
  capabilities: RfidWorkstationCapability[];
  deviceBindings: RfidWorkstationDeviceBinding[];
  antennaDirections: Record<number, RfidDirection>;
  passageDebounceSeconds: number;
  alarmOutputIndex: number;
  alarmDurationMillis: number;
  enabled: boolean;
  lastLoginAt?: number;
}

export interface RfidWorkstationContext {
  workstationId: string;
  workstationCode: string;
  workstationName: string;
  defaultCapability?: RfidWorkstationCapability;
  capabilities: RfidWorkstationCapability[];
  enabled: boolean;
}

export interface RfidWorkstationDeviceBinding {
  id?: string;
  deviceId: string;
  purpose: RfidWorkstationDevicePurpose;
  isDefault: boolean;
  priority: number;
  enabled: boolean;
}

export interface RfidWorkstationPeripheralRuntime {
  binding: RfidWorkstationDeviceBinding;
  device: {
    deviceId: string;
    deviceName: string;
    enabled: boolean;
    connectionManaged: boolean;
    connectionState: 'CONNECTING' | 'ONLINE' | 'DEGRADED' | 'OFFLINE' | null;
    lastSeenAt: number | null;
  } | null;
}

export interface RfidWorkstationRuntime {
  workstationId: string;
  operatorName: string;
  checkedAt: number;
  peripherals: RfidWorkstationPeripheralRuntime[];
}

export interface RfidScanSession {
  id: string;
  sessionNo: string;
  workstationId: string;
  deviceId: string;
  type: RfidSessionType;
  businessType?: string;
  businessId?: string;
  state: RfidSessionState;
  directInput: boolean;
  expectedCount: number;
  readCount: number;
  createdAt: number;
  startedAt?: number;
  stoppedAt?: number;
  finalizedAt?: number;
  errorMessage?: string;
}

export interface RfidTagOperation {
  id: string;
  tagId: string;
  operationType: string;
  workstationId?: string;
  operatorId?: string;
  commandId?: string;
  beforeEpc?: string;
  afterEpc?: string;
  tid?: string;
  success: boolean;
  resultCode?: string;
  detail?: string;
  occurredAt: number;
}

export interface RfidRead {
  epc: string;
  tid?: string;
  antenna?: number;
  direction: RfidDirection;
  firstSeenAt: number;
  lastSeenAt: number;
  readCount: number;
  peakRssi?: number;
  collectionId?: string;
  collectionNo?: string;
  collectionName?: string;
  classification: RfidReadClassification;
}

export interface RfidScanPreview {
  sessionId: string;
  state: RfidSessionState;
  expected: number;
  matched: number;
  missing: number;
  extra: number;
  unknown: number;
  conflict: number;
  stopAcknowledged: boolean;
  settled: boolean;
  canFinalize: boolean;
  blockReasons: string[];
  reads: RfidRead[];
  missingItems: Array<{ collectionId: string; collectionNo?: string; collectionName?: string; expectedEpc?: string }>;
}

export interface RfidTag {
  id: string;
  epc: string;
  tid?: string;
  collectionId?: string;
  collectionNo?: string;
  collectionName?: string;
  status: string;
  activatedAt?: number;
  retiredReason?: string;
}
