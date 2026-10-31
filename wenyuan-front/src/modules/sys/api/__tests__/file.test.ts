import { beforeEach, describe, expect, it, vi } from 'vitest';

const { requestMock } = vi.hoisted(() => ({
  requestMock: vi.fn(),
}));

vi.mock('@/utils/request', () => ({ default: requestMock }));

import { previewOrphanFiles, quarantineOrphanFiles, listChunkTasks, inspectChunkTask } from '../file';

describe('file orphan cleanup api', () => {
  beforeEach(() => {
    requestMock.mockReset();
  });

  it('posts a read-only orphan file preview request', () => {
    const data = { minimumAgeHours: 24, maxPreviewItems: 200 };

    previewOrphanFiles(data);

    expect(requestMock).toHaveBeenCalledWith({
      url: 'file/orphan/preview',
      method: 'post',
      data,
    });
  });

  it('posts an explicitly confirmed quarantine request', () => {
    const data = { minimumAgeHours: 24, maxPreviewItems: 200, confirm: true };

    quarantineOrphanFiles(data);

    expect(requestMock).toHaveBeenCalledWith({
      url: 'file/orphan/quarantine',
      method: 'post',
      data,
    });
  });
});

describe('file chunk task inspection api', () => {
  it('uses read-only endpoints and encodes an exact task id', () => {
    listChunkTasks({pageNum: 2, pageSize: 20, state: 'REVIEW'});
    expect(requestMock).toHaveBeenLastCalledWith({url: 'file/chunk/tasks', method: 'get', params: {pageNum: 2, pageSize: 20, state: 'REVIEW'}});
    inspectChunkTask('id/with-path');
    expect(requestMock).toHaveBeenLastCalledWith({url: 'file/chunk/tasks/id%2Fwith-path', method: 'get'});
  });
});
