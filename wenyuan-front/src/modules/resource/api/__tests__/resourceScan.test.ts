import { beforeEach, expect, it, vi } from 'vitest';

const { requestMock } = vi.hoisted(() => ({ requestMock: vi.fn() }));

vi.mock('@/utils/request', () => ({ default: requestMock }));

import { scanItems, startDirectoryScan } from '../resource';

beforeEach(() => requestMock.mockReset());

it('keeps only the business start and scan item endpoints', () => {
  startDirectoryScan({ sourceId: 'source-1' });
  scanItems('task-1');

  expect(requestMock).toHaveBeenNthCalledWith(1, {
    url: 'resource/sync-source/scan/start',
    method: 'post',
    data: { sourceId: 'source-1' },
  });
  expect(requestMock).toHaveBeenNthCalledWith(2, {
    url: 'resource/sync-source/scan/items/task-1',
    method: 'get',
  });
});
