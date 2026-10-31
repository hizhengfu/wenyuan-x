import request from '@/utils/request';

export interface BackfillRow {
  rowNumber: number;
  deviceCode: string;
  metricCode: string;
  occurredAt: number | null;
  value: string;
  status: 'READY' | 'DUPLICATE' | 'CONFLICT' | 'INVALID' | 'IMPORTED' | 'SKIPPED';
  message: string;
}
export interface BackfillTask {
  id: string;
  orgId: string;
  fileName: string;
  fileSize: number;
  timeZone: string;
  status:
    | 'QUEUED'
    | 'PARSING'
    | 'VALIDATING'
    | 'READY'
    | 'INVALID'
    | 'IMPORT_QUEUED'
    | 'IMPORTING'
    | 'SUCCEEDED'
    | 'FAILED'
    | 'EXPIRED';
  phase: string;
  total: number;
  processed: number;
  insertable: number;
  duplicates: number;
  errors: number;
  affected: number;
  skipped: number;
  reason: string;
  errorMessage: string | null;
  createdAt: number;
  updatedAt: number;
  expiresAt: number;
  purged: boolean;
}
export interface MaintenancePage<T> {
  list: T[];
  total: number;
  pageNum: number;
  pageSize: number;
}
export interface DuplicateQuery {
  orgId: string;
  deviceId: string;
  from: number;
  to: number;
  metricCode: string;
}
export interface DuplicateGroup {
  metricCode: string;
  sourceCode: string | null;
  occurredAt: number;
  count: number;
  removable: number;
  protected: number;
  conflict: boolean;
  value: string;
}
export interface DuplicatePreview {
  previewHash: string;
  scanned: number;
  removable: number;
  protected: number;
  conflicts: number;
  groups: DuplicateGroup[];
}
export interface MaintenanceResult {
  operationId: string;
  affected: number;
  skipped: number;
}
export interface MaintenanceOperation {
  id: string;
  operation: 'IMPORT' | 'DEDUPLICATE';
  operatorId: string | null;
  reason: string;
  fileName: string | null;
  affectedCount: number;
  skippedCount: number;
  createdAt: number;
}
const base = '/museum/ingest/maintenance';
export const maintenanceApi = {
  template: (orgId: string) =>
    request<Blob, Blob>({ url: `${base}/template`, params: { orgId }, responseType: 'blob' }),
  upload: (orgId: string, timeZone: string, file: File, onProgress?: (percent: number) => void, signal?: AbortSignal) =>
    request<BackfillTask, { data: BackfillTask }>({
      url: `${base}/import/tasks`,
      method: 'post',
      params: { orgId, timeZone, fileName: file.name },
      headers: { 'Content-Type': 'application/octet-stream' },
      data: file,
      timeout: 0,
      signal,
      silentError: true,
      onUploadProgress: (event) => {
        if (event.total) onProgress?.(Math.round((event.loaded * 100) / event.total));
      },
    }),
  tasks: (orgId: string, pageNum = 1, pageSize = 10) =>
    request<MaintenancePage<BackfillTask>, { data: MaintenancePage<BackfillTask> }>({
      url: `${base}/import/tasks`,
      params: { orgId, pageNum, pageSize },
      silentError: true,
    }),
  task: (id: string) =>
    request<BackfillTask, { data: BackfillTask }>({ url: `${base}/import/tasks/${id}`, silentError: true }),
  taskRows: (id: string, pageNum = 1, pageSize = 20, status?: string) =>
    request<MaintenancePage<BackfillRow>, { data: MaintenancePage<BackfillRow> }>({
      url: `${base}/import/tasks/${id}/rows`,
      params: { pageNum, pageSize, status: status || undefined },
      silentError: true,
    }),
  confirm: (id: string, reason: string) =>
    request<BackfillTask, { data: BackfillTask }>({
      url: `${base}/import/tasks/${id}/confirm`,
      method: 'post',
      data: { reason },
      silentError: true,
    }),
  retry: (id: string) =>
    request<BackfillTask, { data: BackfillTask }>({
      url: `${base}/import/tasks/${id}/retry`,
      method: 'post',
      silentError: true,
    }),
  previewDuplicates: (data: DuplicateQuery) =>
    request<DuplicatePreview, { data: DuplicatePreview }>({
      url: `${base}/duplicates/preview`,
      method: 'post',
      data,
      silentError: true,
    }),
  cleanDuplicates: (query: DuplicateQuery, previewHash: string, reason: string) =>
    request<MaintenanceResult, { data: MaintenanceResult }>({
      url: `${base}/duplicates/clean`,
      method: 'post',
      data: { query, previewHash, reason },
      silentError: true,
    }),
  operations: (params: Record<string, unknown>) => request({ url: `${base}/operations`, params }),
};
