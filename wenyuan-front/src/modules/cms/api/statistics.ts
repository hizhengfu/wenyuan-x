import request from '@/utils/request';
import type { AsyncTask } from '@/modules/sys/api/asyncTask';

export interface CmsStatParams {
  siteId?: string | null; categoryId?: string | null; module?: string | null; status?: string | null;
  state?: string | null; start?: number; end?: number; dateField?: string; dimension?: string;
  title?: string; quality?: string | null; pageNum?: number; pageSize?: number; replied?: boolean; includeReplyStats?: boolean; feedbackId?: string | null;
}
export type StatRow = Record<string, any>;
export function statistics<T = StatRow>(section: string, params: CmsStatParams = {}): Promise<{ data: T }> {
  return request({ url: `cms/statistics/${section}`, params }) as unknown as Promise<{ data: T }>;
}
export function exportStatistics(section: string, params: CmsStatParams): Promise<{ data: AsyncTask }> {
  return request({ url: 'cms/statistics/export', method: 'post', data: { section, params } }) as unknown as Promise<{ data: AsyncTask }>;
}
export function publication(module: string, id: string): Promise<{ data: StatRow }> {
  return request({ url: `cms/content/${module}/${id}/publication` }) as unknown as Promise<{ data: StatRow }>;
}
export function changePublication(module: string, id: string, data: { state: string; publishAt?: number | null; offlineAt?: number | null }) {
  return request({ url: `cms/content/${module}/${id}/publication`, method: 'put', data });
}
export function versions(module: string, id: string, pageNum = 1): Promise<{ data: { list: StatRow[]; total: number } }> {
  return request({ url: `cms/content/${module}/${id}/versions`, params: { pageNum, pageSize: 20 } }) as unknown as Promise<{ data: { list: StatRow[]; total: number } }>;
}
export function version(module: string, id: string, versionId: string): Promise<{ data: StatRow }> {
  return request({ url: `cms/content/${module}/${id}/versions/${versionId}` }) as unknown as Promise<{ data: StatRow }>;
}
export function restoreVersion(module: string, id: string, versionId: string) {
  return request({ url: `cms/content/${module}/${id}/versions/${versionId}/restore`, method: 'post' });
}
