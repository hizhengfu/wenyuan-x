import request, { type HttpResponse } from '@/utils/request';

export type DeploymentCheckState = 'PASS' | 'FAIL' | 'SKIPPED' | 'ERROR' | 'TIMEOUT';
export type DeploymentCheckSeverity = 'CRITICAL' | 'WARNING' | 'INFO';
export type DeploymentReadinessStatus = 'UNKNOWN' | 'READY' | 'DEGRADED' | 'NOT_READY';
export type DeploymentScanLifecycle = 'NEVER_RUN' | 'RUNNING' | 'COMPLETED';
export type DeploymentRemediationType =
  | 'PACKAGE'
  | 'CONFIGURATION'
  | 'FILE_SYSTEM'
  | 'DATABASE'
  | 'SERVICE'
  | 'DOCUMENTATION';

export interface DeploymentSafeFact {
  name: string;
  value: string;
}

export interface DeploymentRemediation {
  type: DeploymentRemediationType;
  summary: string;
  steps: string[];
  restartRequired: boolean;
  verification: string;
}

export interface DeploymentCheckResult {
  id: string;
  category: string;
  feature: string;
  name: string;
  state: DeploymentCheckState;
  severity: DeploymentCheckSeverity;
  summary: string;
  reasonCode?: string | null;
  safeFacts: DeploymentSafeFact[];
  remediations: DeploymentRemediation[];
  checkedAt: number;
  durationMillis: number;
  diagnosticRef?: string | null;
}

export interface DeploymentScanSummary {
  total: number;
  passed: number;
  warnings: number;
  failed: number;
  skipped: number;
  errors: number;
  timeouts: number;
}

export interface DeploymentRuntimeInfo {
  applicationName: string;
  applicationVersion: string;
  activeProfiles: string[];
  javaVersion: string;
  operatingSystem: string;
  architecture: string;
}

export interface DeploymentScanReport {
  scanId: string;
  overallStatus: DeploymentReadinessStatus;
  ready: boolean;
  startedAt: number;
  finishedAt: number;
  durationMillis: number;
  runtime: DeploymentRuntimeInfo;
  summary: DeploymentScanSummary;
  items: DeploymentCheckResult[];
}

export interface DeploymentScanSnapshot {
  lifecycle: DeploymentScanLifecycle;
  scanId?: string | null;
  startedAt?: number | null;
  report?: DeploymentScanReport | null;
  stale: boolean;
  expiresAt?: number | null;
}

export function latestDeploymentDiagnostics(): Promise<HttpResponse<DeploymentScanSnapshot>> {
  return request({
    url: 'system/deployment-diagnostics/latest',
    method: 'get',
  }) as unknown as Promise<HttpResponse<DeploymentScanSnapshot>>;
}

export function startDeploymentDiagnostics(): Promise<HttpResponse<DeploymentScanSnapshot>> {
  return request({
    url: 'system/deployment-diagnostics/scan',
    method: 'post',
  }) as unknown as Promise<HttpResponse<DeploymentScanSnapshot>>;
}
