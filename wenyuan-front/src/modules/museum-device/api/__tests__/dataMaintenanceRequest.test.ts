// @vitest-environment happy-dom
import { afterEach, expect, it, vi } from 'vitest';
import { AxiosHeaders, type InternalAxiosRequestConfig } from 'axios';
import request from '@/utils/request';
import { maintenanceApi } from '../dataMaintenance';

vi.mock('@/store', () => ({
  useAuthStore: () => ({ getToken: '', identityRevision: 0, identitySyncing: false, reconcileSharedIdentity: vi.fn() }),
  useAppStore: () => ({ silent: true }),
}));
vi.mock('@/router', () => ({ globalRouter: { currentRoute: { value: { query: {} } }, replace: vi.fn() } }));
vi.mock('@/utils/message', () => ({ notification: vi.fn() }));
vi.mock('@/utils/auth', () => ({
  getRefreshToken: () => '',
  getTokenExpAt: () => 0,
  setRefreshToken: vi.fn(),
  setToken: vi.fn(),
  setTokenExpAt: vi.fn(),
}));

const originalAdapter = request.defaults.adapter;
afterEach(() => {
  request.defaults.adapter = originalAdapter;
});

function capture() {
  let captured: InternalAxiosRequestConfig | undefined;
  request.defaults.adapter = async (config) => {
    captured = config;
    return { data: { data: { id: 'TASK' } }, status: 200, statusText: 'OK', headers: new AxiosHeaders(), config };
  };
  return () => captured!;
}
it('streams the workbook as binary without the shared JSON serialization or 60 second timeout', async () => {
  const config = capture();
  const file = new File(['workbook bytes'], '补录.xlsx');
  // happy-dom lacks the native File tag used by Axios to identify binary bodies.
  Object.defineProperty(file, Symbol.toStringTag, { value: 'File' });
  await maintenanceApi.upload('ORG', 'Asia/Shanghai', file);
  expect(config().headers.getContentType()).toBe('application/octet-stream');
  expect(config().data).toBe(file);
  expect(await config().data.text()).toBe('workbook bytes');
  expect(config().timeout).toBe(0);
  expect(config().params).toMatchObject({ orgId: 'ORG', timeZone: 'Asia/Shanghai', fileName: '补录.xlsx' });
});
it('confirms a persisted task without uploading the file again', async () => {
  const config = capture();
  await maintenanceApi.confirm('TASK', '人工补录');
  expect(config().url).toContain('/import/tasks/TASK/confirm');
  expect(JSON.parse(config().data)).toEqual({ reason: '人工补录' });
});
it('downloads the real Excel blob rather than an API JSON envelope', async () => {
  const blob = new Blob(['xlsx bytes']);
  request.defaults.adapter = async (config) => {
    expect(config.responseType).toBe('blob');
    expect(config.params).toEqual({ orgId: 'ORG' });
    return { data: blob, status: 200, statusText: 'OK', headers: new AxiosHeaders(), config };
  };
  expect(await maintenanceApi.template('ORG')).toBe(blob);
});
