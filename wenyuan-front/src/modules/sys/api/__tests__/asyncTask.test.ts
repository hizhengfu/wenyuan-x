import { beforeEach, describe, expect, it, vi } from 'vitest';

const { requestMock, apiUrlMock } = vi.hoisted(() => ({
  requestMock: vi.fn(),
  apiUrlMock: vi.fn((path: string) => `/api/${path}`),
}));

vi.mock('@/utils/request', () => ({ default: requestMock }));
vi.mock('@/utils', () => ({ apiUrl: apiUrlMock }));

import {
  asyncTaskEventsUrl,
  cancelAsyncTask,
  deleteAsyncTask,
  getAsyncTask,
  listAsyncTasks,
} from '../asyncTask';

describe('shared async task api', () => {
  beforeEach(() => {
    requestMock.mockReset();
    apiUrlMock.mockClear();
  });

  it('lists a single task type with taskType', () => {
    listAsyncTasks({ taskType: 'MONITOR_DATA_EXPORT' });

    expect(requestMock).toHaveBeenCalledWith({
      url: 'sys/async-task/list',
      method: 'get',
      params: { taskType: 'MONITOR_DATA_EXPORT' },
    });
  });

  it('serializes multiple task types as one comma-separated parameter', () => {
    const taskTypes = ['MONITOR_DATA_EXPORT', 'MONITOR_DATA_IMPORT'];
    const params = { pageNum: 2, taskTypes };

    listAsyncTasks(params);

    expect(requestMock).toHaveBeenCalledWith({
      url: 'sys/async-task/list',
      method: 'get',
      params: { pageNum: 2, taskTypes: 'MONITOR_DATA_EXPORT,MONITOR_DATA_IMPORT' },
    });
    expect(params).toEqual({ pageNum: 2, taskTypes: ['MONITOR_DATA_EXPORT', 'MONITOR_DATA_IMPORT'] });
    expect(taskTypes).toEqual(['MONITOR_DATA_EXPORT', 'MONITOR_DATA_IMPORT']);
  });

  it('gets, cancels and deletes one task through the shared endpoints', () => {
    getAsyncTask('task-1');
    cancelAsyncTask('task-1');
    deleteAsyncTask('task-1');

    expect(requestMock).toHaveBeenNthCalledWith(1, {
      url: 'sys/async-task/task-1',
      method: 'get',
    });
    expect(requestMock).toHaveBeenNthCalledWith(2, {
      url: 'sys/async-task/task-1/cancel',
      method: 'post',
    });
    expect(requestMock).toHaveBeenNthCalledWith(3, {
      url: 'sys/async-task/task-1',
      method: 'post',
    });
  });

  it('builds the authenticated SSE endpoint URL', () => {
    expect(asyncTaskEventsUrl('task-1')).toBe('/api/sys/async-task/task-1/events');
    expect(apiUrlMock).toHaveBeenCalledWith('sys/async-task/task-1/events');
  });
});
