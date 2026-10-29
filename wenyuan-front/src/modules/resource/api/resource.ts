import request from '@/utils/request';
import { apiUrl } from '@/utils/path';
import type { ResourcePreviewConfig } from '../utils/previewConfig';

const LARGE_RESOURCE_FILE_BYTES = 64 * 1024 * 1024;
const RESOURCE_CHUNK_BYTES = 2 * 1024 * 1024;

/**
 * 分页列表
 */
export function list(params: any) {
  return request({
    url: 'resource/list',
    method: 'get',
    params,
  });
}

/** 文件查看画廊：服务端固定为已审核、已发布的可浏览资源。 */
export function viewerList(params: { pageNum: number; pageSize: number; keyword?: string; resourceType?: string }) {
  return request({
    url: 'resource/viewer/list',
    method: 'get',
    params,
  });
}

/**
 * 资源详情（含文件/版本/关联）
 */
export function get(id: string) {
  return request({
    url: `resource/${id}`,
    method: 'get',
  });
}

/** 预览详情：Streamed SOG 仅返回主清单和已保存的描述。 */
export async function getPreview(id: string) {
  try {
    return await request({ url: `resource/${encodeURIComponent(id)}/preview`, method: 'get' });
  } catch (failure: any) {
    // 前后端分别热更新时，旧服务仍可用；后端升级后自动进入按层清单路径。
    if (failure?.response?.status !== 404) throw failure;
    return get(id);
  }
}

export function getStreamedSogLevelFiles(id: string, level: number, version: number, status: string) {
  return request({
    url: `resource/${encodeURIComponent(id)}/lod-files`,
    method: 'get',
    params: { level, version, status },
  });
}

/** 分块地址始终回到资源权限入口；服务端每次重新核对授权、版本和发布状态。 */
export function streamedSogFileUrl(id: string, fileId: string, version: number, status: string) {
  const query = new URLSearchParams({ version: String(version), status });
  return apiUrl(`resource/${encodeURIComponent(id)}/lod-file/${encodeURIComponent(fileId)}?${query}`);
}

/** 仅保存三维预览的展示视角，不修改资源正文或文件。 */
export function savePreviewView(id: string, config: ResourcePreviewConfig) {
  return request({ url: `resource/${id}/preview-view`, method: 'put', data: config });
}

/**
 * 创建资源（multipart：file + vo）
 * @param file 文件对象
 * @param vo 资源元信息
 */
export async function add(file: File, vo: Record<string, any>, onProgress?: (percentage: number) => void) {
  if (file.size >= LARGE_RESOURCE_FILE_BYTES) {
    onProgress?.(0);
    const stored = await uploadResourceFileInChunks(file, onProgress);
    return request({
      url: 'resource/create/stored',
      method: 'post',
      data: { ...vo, fileId: stored.id },
    });
  }
  const form = new FormData();
  form.append('file', file);
  form.append(
    'vo',
    new Blob([JSON.stringify(vo)], { type: 'application/json' }),
  );
  return request({
    url: 'resource/create',
    method: 'post',
    data: form,
    headers: { 'Content-Type': 'multipart/form-data' },
  });
}

async function uploadResourceFileInChunks(file: File, onProgress?: (percentage: number) => void) {
  const start = new FormData();
  start.append('phase', 'start');
  start.append('module', 'resource');
  const started: any = await request({
    url: 'file/chunk',
    method: 'post',
    data: start,
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  if (started?.status !== 'success' || !started?.data?.session_id) {
    throw new Error(started?.message || '无法创建大文件上传任务');
  }

  const sessionId = started.data.session_id as string;
  for (let offset = 0; offset < file.size; offset += RESOURCE_CHUNK_BYTES) {
    const chunk = new FormData();
    chunk.append('phase', 'upload');
    chunk.append('session_id', sessionId);
    chunk.append('start_offset', String(offset));
    chunk.append('module', 'resource');
    chunk.append('chunk', file.slice(offset, Math.min(offset + RESOURCE_CHUNK_BYTES, file.size)), file.name);
    await retryChunkRequest(chunk);
    onProgress?.(Math.min(99, Math.floor(((offset + RESOURCE_CHUNK_BYTES) / file.size) * 100)));
  }

  const finish = new FormData();
  finish.append('phase', 'finish');
  finish.append('session_id', sessionId);
  finish.append('size', String(file.size));
  finish.append('name', file.name);
  finish.append('module', 'resource');
  const completed = await retryChunkRequest(finish);
  if (!completed?.data?.id) throw new Error(completed?.message || '大文件上传结果不完整');
  onProgress?.(100);
  return completed.data;
}

async function retryChunkRequest(data: FormData) {
  let lastFailure: any;
  for (let attempt = 0; attempt < 3; attempt += 1) {
    try {
      const response: any = await request({
        url: 'file/chunk',
        method: 'post',
        data,
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      if (response?.status !== 'success') throw new Error(response?.message || '文件分片上传失败');
      return response;
    } catch (failure: any) {
      lastFailure = failure;
      if (failure?.response?.status === 400) break;
    }
  }
  throw lastFailure || new Error('文件分片上传失败');
}

/**
 * 批量创建资源（multipart：files + vo）
 */
export function batchAdd(files: File[], vo: Record<string, any>) {
  const form = new FormData();
  files.forEach((file) => form.append('files', file));
  form.append(
    'vo',
    new Blob([JSON.stringify(vo)], { type: 'application/json' }),
  );
  return request({
    url: 'resource/create/batch',
    method: 'post',
    data: form,
    headers: { 'Content-Type': 'multipart/form-data' },
  });
}

/**
 * 创建资源包（multipart：file(zip) + vo）
 */
export function packageAdd(file: File, vo: Record<string, any>) {
  const form = new FormData();
  form.append('file', file);
  form.append(
    'vo',
    new Blob([JSON.stringify(vo)], { type: 'application/json' }),
  );
  return request({
    url: 'resource/create/package',
    method: 'post',
    data: form,
    headers: { 'Content-Type': 'multipart/form-data' },
  });
}

/**
 * 检查资源包（multipart：file(zip)）
 */
export function inspectPackage(file: File) {
  const form = new FormData();
  form.append('file', file);
  return request({
    url: 'resource/package/inspect',
    method: 'post',
    data: form,
    headers: { 'Content-Type': 'multipart/form-data' },
  });
}

/**
 * 检查已解开的资源包（multipart：files + relativePaths）
 */
export function inspectPackageFiles(files: File[], relativePaths: string[]) {
  const form = new FormData();
  files.forEach((file, index) => {
    form.append('files', file);
    form.append('relativePaths', relativePaths[index] || file.name);
  });
  return request({
    url: 'resource/package/inspect/files',
    method: 'post',
    data: form,
    headers: { 'Content-Type': 'multipart/form-data' },
  });
}

/**
 * 创建已解开的资源包（multipart：files + relativePaths + vo）
 */
export function packageFilesAdd(files: File[], relativePaths: string[], vo: Record<string, any>) {
  const form = new FormData();
  files.forEach((file, index) => {
    form.append('files', file);
    form.append('relativePaths', relativePaths[index] || file.name);
  });
  form.append(
    'vo',
    new Blob([JSON.stringify(vo)], { type: 'application/json' }),
  );
  return request({
    url: 'resource/create/package/files',
    method: 'post',
    data: form,
    headers: { 'Content-Type': 'multipart/form-data' },
  });
}

/**
 * 更新元信息（不变更文件）
 */
export function edit(data: any) {
  return request({
    url: 'resource/update',
    method: 'post',
    data,
  });
}

/** 单条删除 */
export function del(id: string) {
  return request({
    url: `resource/delete/${id}`,
    method: 'post',
  });
}

/** 批量删除 */
export function batchDel(ids: string[]) {
  return request({
    url: 'resource/delete',
    method: 'post',
    data: ids,
  });
}

/** 发布 */
export function publish(id: string) {
  return request({
    url: `resource/publish/${id}`,
    method: 'post',
  });
}

/** 批量发布 */
export function batchPublish(ids: string[]) {
  return request({
    url: 'resource/publish',
    method: 'post',
    data: ids,
  });
}

/** 归档 */
export function archive(id: string) {
  return request({
    url: `resource/archive/${id}`,
    method: 'post',
  });
}

/** 批量归档 */
export function batchArchive(ids: string[]) {
  return request({
    url: 'resource/archive',
    method: 'post',
    data: ids,
  });
}

/** 批量更新标签 */
export function batchUpdateTags(data: { ids: string[]; tags: string[]; replace?: boolean }) {
  return request({
    url: 'resource/tags/batch',
    method: 'post',
    data,
  });
}

/** 提交入库审核 */
export function submitImport(id: string) {
  return request({
    url: `resource/${id}/submit`,
    method: 'post',
  });
}

/** 流程信息 */
export function flowInfo(id: string) {
  return request({
    url: `resource/${id}/flow`,
    method: 'get',
  });
}

/** 上传新版本 */
export function uploadVersion(resourceId: string, file: File, changeLog?: string) {
  const form = new FormData();
  form.append('file', file);
  if (changeLog) form.append('changeLog', changeLog);
  return request({
    url: `resource/version/upload/${resourceId}`,
    method: 'post',
    data: form,
    headers: { 'Content-Type': 'multipart/form-data' },
  });
}

/** 版本历史 */
export function versionList(resourceId: string) {
  return request({
    url: `resource/version/list/${resourceId}`,
    method: 'get',
  });
}

/** 回滚到指定版本 */
export function rollback(resourceId: string, versionNumber: number) {
  return request({
    url: `resource/version/rollback/${resourceId}/${versionNumber}`,
    method: 'post',
  });
}

/** 删除未审核通过的历史版本 */
export function deleteVersion(resourceId: string, versionNumber: number) {
  return request({
    url: `resource/version/${resourceId}/${versionNumber}`,
    method: 'delete',
  });
}

/** 添加关联 */
export function addRelation(data: any) {
  return request({
    url: 'resource/relation/add',
    method: 'post',
    data,
  });
}

/** 关联列表 */
export function relationList(resourceId: string) {
  return request({
    url: `resource/relation/list/${resourceId}`,
    method: 'get',
  });
}

/** 删除关联 */
export function deleteRelation(relationId: string) {
  return request({
    url: `resource/relation/delete/${relationId}`,
    method: 'post',
  });
}

/** 获取元数据模板 */
export function metadataTemplates(params: { categoryId?: string; resourceType?: string }) {
  return request({
    url: 'resource/metadata/templates',
    method: 'get',
    params,
  });
}

/** 热门标签 */
export function topTags(limit = 20) {
  return request({
    url: 'resource/tags/top',
    method: 'get',
    params: { limit },
  });
}

/** 校验报告 */
export function validationReports(resourceId: string) {
  return request({
    url: `resource/validation/list/${resourceId}`,
    method: 'get',
  });
}

/** 权限申请列表 */
export function accessGrantList(params: any) {
  return request({
    url: 'resource/access-grant/list',
    method: 'get',
    params,
  });
}

/** 保存权限申请 */
export function saveAccessGrant(data: any) {
  return request({
    url: 'resource/access-grant/save',
    method: 'post',
    data,
  });
}

/** 提交权限申请 */
export function submitAccessGrant(id: string) {
  return request({
    url: `resource/access-grant/${id}/submit`,
    method: 'post',
  });
}

/** 启停资源权限 */
export function setAccessGrantEnabled(id: string, enabled: boolean) {
  return request({
    url: `resource/access-grant/${id}/enabled`,
    method: 'post',
    params: { enabled },
  });
}

/** 批量授权预检 */
export function previewResourceGrantBatch(data: any) {
  return request({
    url: 'resource/grant/batch/preview',
    method: 'post',
    data,
  });
}

/** 创建批量授权 */
export function createResourceGrantBatch(data: any) {
  return request({
    url: 'resource/grant/batch',
    method: 'post',
    data,
  });
}

/** 撤销批量授权 */
export function revokeResourceGrantBatch(batchId: string) {
  return request({
    url: `resource/grant/batch/${batchId}/revoke`,
    method: 'post',
  });
}

/** 授权批次历史 */
export function resourceGrantBatchList(params: any = {}) {
  return request({
    url: 'resource/grant/batch/list',
    method: 'get',
    params,
  });
}

/** 授权批次完整审计详情 */
export function resourceGrantBatchDetail(batchId: string) {
  return request({
    url: `resource/grant/batch/${batchId}`,
    method: 'get',
  });
}

/** 当前用户可复用的授权模板 */
export function resourceGrantTemplateList() {
  return request({
    url: 'resource/grant/template/list',
    method: 'get',
  });
}

/** 保存授权模板 */
export function saveResourceGrantTemplate(data: any) {
  return request({
    url: 'resource/grant/template',
    method: 'post',
    data,
  });
}

/** 删除授权模板 */
export function deleteResourceGrantTemplate(id: string) {
  return request({
    url: `resource/grant/template/${id}`,
    method: 'delete',
  });
}

/** 同步源列表 */
export function syncSourceList(params: any) {
  return request({
    url: 'resource/sync-source/list',
    method: 'get',
    params,
  });
}

/** 保存同步源 */
export function saveSyncSource(data: any) {
  return request({
    url: 'resource/sync-source/save',
    method: 'post',
    data,
  });
}

/** 触发同步源 */
export function triggerSyncSource(id: string) {
  return request({
    url: `resource/sync-source/${id}/trigger`,
    method: 'post',
  });
}

/** 删除同步源 */
export function deleteSyncSource(id: string) {
  return request({
    url: `resource/sync-source/delete/${id}`,
    method: 'post',
  });
}

/** 启动目录扫描入库 */
export function startDirectoryScan(data: any) {
  return request({
    url: 'resource/sync-source/scan/start',
    method: 'post',
    data,
  });
}

/** 扫描文件明细 */
export function scanItems(taskId: string) {
  return request({
    url: `resource/sync-source/scan/items/${taskId}`,
    method: 'get',
  });
}

/**
 * 记录浏览（打开预览时调用，后端计数 +1）
 * 走 front 路径，无需 RESOURCE_SELECT 权限
 */
export function recordView(id: string) {
  return request({
    url: `front/resource/${id}`,
    method: 'get',
  });
}

/**
 * 记录下载（前端触发文件下载前调用，后端计数 +1 并返回文件路径）
 * 走 front 路径，无需 RESOURCE_SELECT 权限
 */
export function recordDownload(id: string) {
  return request({
    url: `front/resource/${id}/download`,
    method: 'post',
  });
}

/** 资源类型枚举 */
export const RESOURCE_TYPE_OPTIONS = [
  { label: '图片', value: 'IMAGE' },
  { label: '视频', value: 'VIDEO' },
  { label: '音频', value: 'AUDIO' },
  { label: '3D模型', value: 'MODEL_3D' },
  { label: 'VR全景', value: 'PANORAMA' },
  { label: '文档', value: 'DOCUMENT' },
];

/** 预览类型：AUTO 只做默认推断，其他值是资源的显式展示策略。 */
export const RESOURCE_PREVIEW_TYPE_OPTIONS = [
  { label: '自动识别', value: 'AUTO' },
  { label: '图片', value: 'IMAGE' },
  { label: '视频', value: 'VIDEO' },
  { label: '音频', value: 'AUDIO' },
  { label: 'PDF', value: 'PDF' },
  { label: 'Office文档', value: 'OFFICE_DOCUMENT' },
  { label: '电子表格', value: 'SPREADSHEET' },
  { label: '文本', value: 'TEXT' },
  { label: 'Marmoset场景', value: 'MVIEW' },
  { label: '三维模型', value: 'MODEL_3D' },
  { label: '普通点云', value: 'POINT_CLOUD' },
  { label: '3D Tiles', value: 'TILES_3D' },
  { label: 'PLY高斯泼溅', value: 'GAUSSIAN_SPLAT_PLY' },
  { label: 'SOG高斯泼溅', value: 'GAUSSIAN_SPLAT_SOG' },
  { label: 'Streamed SOG分层高斯泼溅', value: 'GAUSSIAN_SPLAT_SOG_LOD' },
  { label: '仅下载', value: 'DOWNLOAD_ONLY' },
];

/** 资源状态枚举 */
export const RESOURCE_STATUS_OPTIONS = [
  { label: '草稿', value: 'DRAFT' },
  { label: '已发布', value: 'PUBLISHED' },
  { label: '已归档', value: 'ARCHIVED' },
  { label: '已禁用', value: 'DISABLED' },
];

/** 状态对应 Naive UI Tag 颜色 */
export const RESOURCE_STATUS_TYPE_MAP: Record<string, 'default' | 'success' | 'warning' | 'error' | 'info'> = {
  DRAFT: 'default',
  PUBLISHED: 'success',
  ARCHIVED: 'warning',
  DISABLED: 'error',
};

export const RESOURCE_FLOW_STATUS_OPTIONS = [
  { label: '暂存', value: 'DRAFT' },
  { label: '待审核', value: 'AUDIT' },
  { label: '通过', value: 'PASS' },
  { label: '驳回', value: 'REJECT' },
  { label: '取消', value: 'CANCEL' },
  { label: '作废', value: 'VOID' },
];

export const RESOURCE_FLOW_STATUS_TYPE_MAP: Record<string, 'default' | 'success' | 'warning' | 'error' | 'info'> = {
  DRAFT: 'default',
  AUDIT: 'warning',
  PASS: 'success',
  REJECT: 'error',
  CANCEL: 'default',
  VOID: 'error',
};

export const RESOURCE_VALIDATION_STATUS_OPTIONS = [
  { label: '通过', value: 'PASS' },
  { label: '人工复核', value: 'MANUAL_REVIEW' },
  { label: '不通过', value: 'FAILED' },
];

export const RESOURCE_VALIDATION_STATUS_TYPE_MAP: Record<string, 'default' | 'success' | 'warning' | 'error' | 'info'> = {
  PASS: 'success',
  MANUAL_REVIEW: 'warning',
  FAILED: 'error',
};

export const RESOURCE_PERMISSION_LEVEL_OPTIONS = [
  { label: '一级基础访问', value: 'LEVEL_1' },
  { label: '二级普通操作', value: 'LEVEL_2' },
  { label: '三级高级操作', value: 'LEVEL_3' },
  { label: '四级管理员', value: 'LEVEL_4' },
];

export const RESOURCE_VISIBILITY_OPTIONS = [
  { label: '仅负责人', value: 'PRIVATE' },
  { label: '所属机构', value: 'GROUP' },
  { label: '所属机构及下级机构', value: 'GROUP_TREE' },
  { label: '仅按授权策略', value: 'ACL' },
  { label: '公开访问', value: 'PUBLIC' },
];

export const RESOURCE_GRANT_OPERATION_OPTIONS = [
  { label: '查看元数据', value: 'VIEW_METADATA' },
  { label: '低精度预览', value: 'PREVIEW_LOW' },
  { label: '下载轻量资源', value: 'DOWNLOAD_LIGHTWEIGHT' },
  { label: '下载高精度资源', value: 'DOWNLOAD_HIGH' },
  { label: '导出记录', value: 'EXPORT' },
  { label: '修改资源', value: 'EDIT' },
  { label: '授权管理', value: 'GRANT' },
];

/** 关系类型 */
export const RELATION_TYPE_OPTIONS = [
  { label: '相关资源', value: 'RELATED' },
  { label: '衍生资源', value: 'DERIVED' },
  { label: '附件', value: 'ATTACHMENT' },
  { label: '引用', value: 'REFERENCE' },
];

/** 类型图标映射（iconify 名） */
export const RESOURCE_TYPE_ICON_MAP: Record<string, string> = {
  IMAGE: 'mdi-file-image-outline',
  VIDEO: 'mdi-file-video-outline',
  AUDIO: 'mdi-file-music-outline',
  MODEL_3D: 'mdi-cube-outline',
  DOCUMENT: 'mdi-file-document-outline',
};

/** 列表导出通过独立权限入口并记录使用日志。 */
export function exportList(params: any) { return request({ url: 'resource/export', method: 'get', params }); }
