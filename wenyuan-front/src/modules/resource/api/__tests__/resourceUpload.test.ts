import { beforeEach, describe, expect, it, vi } from 'vitest';

const { requestMock } = vi.hoisted(() => ({
  requestMock: vi.fn(),
}));

vi.mock('@/utils/request', () => ({ default: requestMock }));

import { add } from '../resource';

describe('large resource upload', () => {
  beforeEach(() => {
    requestMock.mockReset();
    requestMock.mockImplementation(async (config: any) => {
      if (config.url === 'file/chunk') {
        const phase = config.data.get('phase');
        if (phase === 'start') return { status: 'success', data: { session_id: 'session-1' } };
        if (phase === 'finish') return { status: 'success', data: { id: 'stored-file-1' } };
        return { status: 'success' };
      }
      return { status: 200, data: { id: 'resource-1' } };
    });
  });

  it('uploads files at the large-file threshold in chunks before creating the resource', async () => {
    const size = 64 * 1024 * 1024;
    const file = {
      name: 'point_cloud.ply',
      size,
      slice: vi.fn(() => new Blob(['chunk'])),
    } as unknown as File;
    const progress: number[] = [];

    await add(file, { name: '点云' }, (percentage) => progress.push(percentage));

    const chunkRequests = requestMock.mock.calls
      .map(([config]) => config)
      .filter((config) => config.url === 'file/chunk');
    expect(chunkRequests).toHaveLength(34);
    expect(chunkRequests[0].data.get('phase')).toBe('start');
    expect(chunkRequests.at(-1).data.get('phase')).toBe('finish');
    expect(chunkRequests.at(-1).data.get('size')).toBe(String(size));
    expect(requestMock).toHaveBeenLastCalledWith({
      url: 'resource/create/stored',
      method: 'post',
      data: { name: '点云', fileId: 'stored-file-1' },
    });
    expect(progress[0]).toBe(0);
    expect(progress.at(-1)).toBe(100);
  });
});
