import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const { post } = vi.hoisted(() => ({ post: vi.fn() }));
vi.mock('@/utils/request', () => ({ default: { post } }));
vi.mock('@/utils', () => ({ fileUrl: (path: string) => path, notification: vi.fn() }));
vi.mock('hash-wasm', () => ({
  createMD5: async () => ({ update: vi.fn(), digest: () => 'test-md5' }),
}));

import { uploadedFileId, uploadRequest } from '../utils';

function options(blob: Blob, clientId: string, finished: () => void) {
  const file = {
    id: clientId,
    file: Object.assign(blob, { name: 'synthetic.txt' }),
    name: 'synthetic.txt',
    status: 'pending',
  } as any;
  return {
    file,
    request: {
      file,
      action: '/file/upload',
      data: {},
      onProgress: vi.fn(),
      onFinish: finished,
      onError: vi.fn(),
    } as any,
  };
}

describe('Naive UI upload identity', () => {
  beforeEach(() => post.mockReset());
  afterEach(() => vi.unstubAllGlobals());

  it('keeps the client id through an ordinary upload while exposing the stored id', async () => {
    post.mockResolvedValue({ data: { id: 'stored-1', path: '/files/1.txt' } });
    let complete!: () => void;
    const finished = new Promise<void>((resolve) => { complete = resolve; });
    const { file, request } = options(new Blob(['test']), 'client-1', complete);

    await uploadRequest(request, { enableQuickUpload: false, enableChunk: false });
    await finished;

    expect(file.id).toBe('client-1');
    expect(uploadedFileId(file)).toBe('stored-1');
    expect(file.fullPath).toBe('/files/1.txt');
    expect(request.onError).not.toHaveBeenCalled();
  });

  it('keeps the client id when the server reuses an existing file', async () => {
    class TestFileReader {
      result: ArrayBuffer | null = null;
      onload: ((event: unknown) => void) | null = null;
      readAsArrayBuffer(blob: Blob) {
        void blob.arrayBuffer().then((result) => {
          this.result = result;
          this.onload?.({ target: this });
        });
      }
    }
    vi.stubGlobal('FileReader', TestFileReader);
    post.mockResolvedValue({ data: { id: 'stored-2', path: '/files/2.txt' } });
    const finished = vi.fn();
    const { file, request } = options(new Blob(['test']), 'client-2', finished);

    await uploadRequest(request, { enableQuickUpload: true, enableChunk: false });

    expect(post).toHaveBeenCalledOnce();
    expect(file.id).toBe('client-2');
    expect(uploadedFileId(file)).toBe('stored-2');
    expect(finished).toHaveBeenCalledOnce();
  });

  it('keeps the client id across chunk completion', async () => {
    const size = 2 * 1024 * 1024 + 1;
    post.mockImplementation(async (url: string, body: { phase: string }) => {
      if (url !== '/file/chunk') return { status: 'success', data: {} };
      if (body.phase === 'start') return { status: 'success', data: { session_id: 'chunk-1', end_offset: 2 * 1024 * 1024 } };
      if (body.phase === 'finish') return { status: 'success', data: { id: 'stored-3', path: '/files/3.txt' } };
      return { status: 'success', data: {} };
    });
    const finished = vi.fn();
    const { file, request } = options(new Blob([new Uint8Array(size)]), 'client-3', finished);

    await uploadRequest(request, { enableQuickUpload: false, enableChunk: true });

    expect(file.id).toBe('client-3');
    expect(uploadedFileId(file)).toBe('stored-3');
    expect(finished).toHaveBeenCalledOnce();
    expect(request.onError).not.toHaveBeenCalled();
    expect(post.mock.calls.filter(([url]) => url === '/file/chunk')).toHaveLength(4);
  });
});
