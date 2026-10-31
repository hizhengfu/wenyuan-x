import request, { type HttpResponse } from '@/utils/request';

export type FileOrphanCleanupAction = 'PREVIEW' | 'QUARANTINE';

export interface FileOrphanCleanupRequest {
  roots?: string[];
  minimumAgeHours?: number;
  maxPreviewItems?: number;
  confirm?: boolean;
}

export interface FileOrphanCandidate {
  relativePath: string;
  size: number;
  lastModifiedAt: number;
}

export interface FileOrphanFailure {
  relativePath?: string | null;
  message: string;
}

export interface FileOrphanCleanupResult {
  action: FileOrphanCleanupAction;
  baseDirectory: string;
  roots: string[];
  minimumAgeHours: number;
  referencedFileCount: number;
  scannedFileCount: number;
  candidateCount: number;
  candidateBytes: number;
  movedCount: number;
  movedBytes: number;
  skippedReferencedCount: number;
  skippedChangedCount: number;
  failedCount: number;
  quarantineDirectory?: string | null;
  manifestPath?: string | null;
  previewTruncated: boolean;
  candidates: FileOrphanCandidate[];
  failures: FileOrphanFailure[];
}

/**
 * 返回指定资源下所有的文件
 * */
export function frontFiles(id: string, params: object) {
  return request({
    url: `${APP_API_FRONT_PATH}/file/content/${id}`,
    method: 'get',
    params: params || {},
  });
}

/**
 * 查询指定内容id的文件
 * */
export function listContentFiles(id: string, signal?: AbortSignal) {
  return request({
    url: `file/content/${id}`,
    signal,
    method: 'get',
  });
}

export function list(params: object) {
  return request({
    url: 'file/list',
    method: 'get',
    params: params,
  });
}

/**
 * 只读预览数据库中不存在的本地文件。
 */
export function previewOrphanFiles(data: FileOrphanCleanupRequest): Promise<HttpResponse<FileOrphanCleanupResult>> {
  return request({
    url: 'file/orphan/preview',
    method: 'post',
    data,
  }) as unknown as Promise<HttpResponse<FileOrphanCleanupResult>>;
}

/**
 * 将数据库中不存在的本地文件移入隔离目录，不直接删除。
 */
export function quarantineOrphanFiles(data: FileOrphanCleanupRequest): Promise<HttpResponse<FileOrphanCleanupResult>> {
  return request({
    url: 'file/orphan/quarantine',
    method: 'post',
    data,
  }) as unknown as Promise<HttpResponse<FileOrphanCleanupResult>>;
}

/**
 * 删除图像
 * */
export function del(id: string, contentId: string) {
  return request({
    url: `file/delete/${id}`,
    method: 'get',
    params: { contentId: contentId },
  });
}

/**
 * 更新文件是否需要授权保护
 * */
export function updateSecure(id: string, secure: boolean) {
  return request({
    url: 'file/updateSecure',
    method: 'post',
    params: { id: id, secure: secure },
  });
}

/**
 * 获取登录用户文件下载次数
 * */
export function downloadStat(id: string) {
  return request({
    url: `file/downloadStat/${id}`,
    method: 'get',
  });
}

/**
 * 查询用户所属的文件
 * */
export function user(user: string) {
  return request({
    url: `file/user/${user}`,
    method: 'get',
  });
}

/**
 * 添加文件
 * */
export function add(data: object) {
  return request({
    url: 'file/upload',
    method: 'post',
    data,
  });
}

export interface FileChunkTask {
  id: string;
  state: string;
  createdAt: number;
  updatedAt: number;
  expiresAt: number;
  expired: boolean;
}
export interface FileChunkTaskPage { list: FileChunkTask[]; total: number; pageNum: number; pageSize: number }
export interface FileChunkInspection {
  task: FileChunkTask;
  resultStatus: 'NOT_COMPLETE' | 'INVALID_RECEIPT' | 'NO_FILE_RECORD' | 'FILE_RECORD_MISSING' | 'FILE_RECORD_CHANGED' | 'FILE_NOT_READABLE' | 'FILE_RECORD_AVAILABLE';
  file?: { id: string; name?: string; size?: number; hash?: string } | null;
}
export function listChunkTasks(params: { pageNum: number; pageSize: number; state?: string; id?: string }): Promise<HttpResponse<FileChunkTaskPage>> {
  return request({ url: 'file/chunk/tasks', method: 'get', params }) as unknown as Promise<HttpResponse<FileChunkTaskPage>>;
}
export function inspectChunkTask(id: string): Promise<HttpResponse<FileChunkInspection>> {
  return request({ url: `file/chunk/tasks/${encodeURIComponent(id)}`, method: 'get' }) as unknown as Promise<HttpResponse<FileChunkInspection>>;
}

/** Current authorized metadata for ordinary references; never used for frozen business archives. */
export function readFileMetadata(references: string[], signal?: AbortSignal) {
  return request({ url: 'file/metadata', method: 'get', params: { ids: references.join(',') }, signal });
}
