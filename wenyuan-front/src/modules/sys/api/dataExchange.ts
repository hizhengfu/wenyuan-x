import request from '@/utils/request';

export type SelectionMode = 'SINGLE' | 'SELECTED' | 'ALL_TABLES_IN_SCHEMA';
export type ImportStrategy =
  | 'FAIL_IF_EXISTS'
  | 'SKIP_EXISTING'
  | 'UPDATE_EXISTING'
  | 'APPEND'
  | 'CLEAR_AND_IMPORT';

export interface DataSourceOption {
  key: string;
  label: string;
  exportEnabled: boolean;
  importEnabled: boolean;
}

export interface SchemaOption {
  id: string;
  name: string;
}

export interface PhysicalColumn {
  name: string;
  ordinal: number;
  jdbcType: number;
  typeName: string;
  logicalType: string;
  nullable: boolean;
  generated: boolean;
}

export interface PhysicalTable {
  id: string;
  schemaId: string;
  name: string;
  type: string;
  remarks?: string;
  columns: PhysicalColumn[];
  primaryKey: string[];
}

export interface ExportPreview {
  previewId: string;
  requestDigest: string;
  sourceSchema: SchemaOption;
  selectionMode: SelectionMode;
  tables: PhysicalTable[];
  estimatedRows: number;
  riskLevel: string;
  stepUpRequired: boolean;
  expiresAt: number;
}

export interface ColumnInspection {
  source: string;
  target?: string;
  compatibility: string;
  message?: string;
}

export interface TableInspection {
  sourceTableId: string;
  sourceTable: string;
  targetTableCatalogId?: string;
  targetTable?: string;
  compatibility: string;
  rowCount: number;
  columns: ColumnInspection[];
  warnings: string[];
}

export interface InspectionResult {
  packageId: string;
  sourceCatalog?: string;
  sourceSchema?: string;
  targetSchema: string;
  compatible: boolean;
  tables: TableInspection[];
}

export function dataSources() {
  return request({ url: 'sys/data-exchange/data-sources', method: 'get' });
}

export function schemas(dataSource: string, direction: 'EXPORT' | 'IMPORT') {
  return request({ url: 'sys/data-exchange/schemas', method: 'get', params: { dataSource, direction } });
}

export function tables(params: Record<string, unknown>) {
  return request({ url: 'sys/data-exchange/tables', method: 'get', params: { pageSize: 500, ...params } });
}

export function previewExport(data: Record<string, unknown>) {
  return request({ url: 'sys/data-exchange/exports/preview', method: 'post', data });
}

export function createExport(data: { previewId: string; requestDigest: string }, grant?: string) {
  return request({
    url: 'sys/data-exchange/exports',
    method: 'post',
    data,
    headers: grant ? { 'X-Sensitive-Operation-Grant': grant } : undefined,
  });
}

export function createImport(data: {
  targetDataSource: string;
  targetSchemaId: string;
  fileId: string;
  uploadContentId: string;
}) {
  return request({ url: 'sys/data-exchange/imports', method: 'post', data });
}

export function inspectImport(jobId: string, fileId: string) {
  return request({ url: `sys/data-exchange/imports/${jobId}/inspect`, method: 'post', data: { fileId } });
}

export function previewImport(jobId: string, data: Record<string, unknown>) {
  return request({ url: `sys/data-exchange/imports/${jobId}/execute/preview`, method: 'post', data });
}

export function executeImport(jobId: string, data: Record<string, unknown>, grant?: string) {
  return request({
    url: `sys/data-exchange/imports/${jobId}/execute`,
    method: 'post',
    data,
    headers: grant ? { 'X-Sensitive-Operation-Grant': grant } : undefined,
  });
}

export function verifySensitiveOperation(data: Record<string, unknown>) {
  return request({ url: 'sys/data-exchange/auth/verify', method: 'post', data });
}

export function jobs(params: Record<string, unknown> = {}) {
  return request({ url: 'sys/data-exchange/jobs', method: 'get', params });
}

export function getJob(jobId: string) {
  return request({ url: `sys/data-exchange/jobs/${jobId}`, method: 'get' });
}

export function jobTables(jobId: string) {
  return request({ url: `sys/data-exchange/jobs/${jobId}/tables`, method: 'get' });
}

export function downloadDigest(jobId: string) {
  return request({ url: `sys/data-exchange/exports/${jobId}/download-digest`, method: 'get' });
}

export function downloadExport(jobId: string, grant: string) {
  return request({
    url: `sys/data-exchange/exports/${jobId}/download`,
    method: 'get',
    responseType: 'blob',
    headers: { 'X-Sensitive-Operation-Grant': grant },
  });
}
