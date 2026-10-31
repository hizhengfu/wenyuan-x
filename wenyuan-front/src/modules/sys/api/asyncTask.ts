import { apiUrl } from '@/utils';
import request from '@/utils/request';

export type AsyncTaskStatus = 'CREATED' | 'RUNNING' | 'CANCELLING' | 'SUCCESS' | 'FAILED' | 'CANCELLED';

export interface AsyncTaskArtifact {
  id: string;
  taskId: string;
  fileName: string;
  relativePath: string;
  contentType: string;
  fileSize: number;
  fileHash?: string;
  downloadCount: number;
  lastDownloadAt?: number;
  createTime: number;
  expiresAt: number;
}

export interface AsyncTask {
  id: string;
  taskType: string;
  queueName: string;
  status: AsyncTaskStatus;
  stage?: string;
  progress: number;
  message?: string;
  errorMessage?: string;
  totalCount: number;
  processedCount: number;
  successCount: number;
  skippedCount: number;
  failedCount: number;
  retryCount: number;
  maxRetries: number;
  createBy?: string;
  createTime: number;
  startedAt?: number;
  finishedAt?: number;
  expiresAt: number;
  artifacts?: AsyncTaskArtifact[];
}

export interface ListAsyncTasksParams {
  taskType?: string;
  taskTypes?: string[];
  [key: string]: unknown;
}

export function listAsyncTasks(params: ListAsyncTasksParams = {}) {
  const { taskTypes, ...otherParams } = params;
  return request({
    url: 'sys/async-task/list',
    method: 'get',
    params: taskTypes?.length ? { ...otherParams, taskTypes: taskTypes.join(',') } : otherParams,
  });
}

export function getAsyncTask(id: string) {
  return request({ url: `sys/async-task/${id}`, method: 'get' });
}

export function cancelAsyncTask(id: string) {
  return request({ url: `sys/async-task/${id}/cancel`, method: 'post' });
}

export function deleteAsyncTask(id: string) {
  return request({ url: `sys/async-task/${id}`, method: 'post' });
}

export function asyncTaskEventsUrl(id: string) {
  return apiUrl(`sys/async-task/${id}/events`);
}
