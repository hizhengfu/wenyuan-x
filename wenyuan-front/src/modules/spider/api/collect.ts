import request from '@/utils/request';

export interface CollectConnection { id: string; name: string; kind: 'SESSION' | 'OFFICIAL'; status: string }
export interface CollectSource { key: string; name: string; alias: string }
export interface CollectPlan {
  id: string; name: string; categoryId: string; categoryName: string; connectionId: string;
  accountName: string; intervalMinutes: number; enabled: boolean; autoPublish: boolean; nextRunAt: number;
  lastTaskId?: string; lastError?: string; authorization: string;
}
export interface CollectPlanInput { name: string; categoryId: string; connectionId: string; sourceKey: string; intervalMinutes: number; enabled: boolean; autoPublish?: boolean }
export interface CollectQr { id: string; state: string; image?: string; expiresAt: number; message: string }
export interface CollectAuthorization { connectionId: string; qr: CollectQr }
export interface CollectPreview { title: string; url: string; content: string }

const data = <T>(promise: unknown) => (promise as Promise<{ data: T }>).then(result => result.data);
const base = 'cms/collect';
export const collectConnections = (silentError = false) => data<CollectConnection[]>(request({ url: `${base}/connections`, silentError }));
export const collectCategories = () => data<{ id: string; name: string }[]>(request({ url: `${base}/categories` }));
export const collectPlans = (silentError = false) => data<CollectPlan[]>(request({ url: `${base}/plans`, silentError }));
export const startCollectAuthorization = (connectionId?: string) => data<CollectAuthorization>(request({ url: `${base}/authorization`, method: 'post', data: { connectionId }, silentError: true }));
export const pollCollectAuthorization = (connectionId: string, id: string) => data<CollectQr>(request({ url: `${base}/connections/${connectionId}/authorization/${id}`, method: 'post', silentError: true }));
export const cancelCollectAuthorization = (connectionId: string, id: string) => request({ url: `${base}/connections/${connectionId}/authorization/${id}`, method: 'delete', silentError: true });
export const searchCollectSources = (connectionId: string, query: string) => data<CollectSource[]>(request({ url: `${base}/sources`, params: { connectionId, query } }));
export const previewCollectSource = (connectionId: string, sourceKey: string) => data<CollectPreview>(request({ url: `${base}/preview`, method: 'post', data: { connectionId, sourceKey } }));
export const createCollectPlan = (input: CollectPlanInput) => data<string>(request({ url: `${base}/plans`, method: 'post', data: input }));
export const configureCollectPlan = (id: string, enabled: boolean, intervalMinutes: number, autoPublish?: boolean) => request({ url: `${base}/plans/${id}`, method: 'put', data: { enabled, intervalMinutes, ...(autoPublish === undefined ? {} : { autoPublish }) } });
export const deleteCollectPlan = (id: string) => request({ url: `${base}/plans/${id}`, method: 'delete' });
export const runCollectPlan = (id: string) => data<string | null>(request({ url: `${base}/plans/${id}/run`, method: 'post' }));
