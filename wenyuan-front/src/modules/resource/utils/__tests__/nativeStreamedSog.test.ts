import type { Application } from 'playcanvas';
import { describe, expect, it, vi } from 'vitest';
import { nativeSogManifest, PackageSogAssetLoader } from '../nativeStreamedSog';
import { parseStreamedSogManifest } from '../streamedSog';

const data = {
  lodLevels: 2,
  filenames: ['0_0.sog', '1_0.sog'],
  tree: {
    bound: { min: [-1, -1, -1], max: [1, 1, 1] },
    lods: { 0: { file: 0, offset: 0, count: 20 }, 1: { file: 1, offset: 0, count: 10 } },
  },
};

describe('native Streamed SOG bridge', () => {
  it('maps engine chunk URLs back to protected package files', () => {
    const files = [
      { relativePath: 'wrapper/lod-meta.json', filePath: '/api/file/preview?fileId=root', role: 'MAIN' },
      { relativePath: 'wrapper/0_0.sog', filePath: '/api/file/preview?fileId=fine' },
      { relativePath: 'wrapper/1_0.sog', filePath: '/api/file/preview?fileId=coarse' },
    ];
    const parsed = parseStreamedSogManifest(data, files);
    const { data: native, sources } = nativeSogManifest(parsed, files);
    expect(native.filenames).toEqual(['sog-stream://resource/0.sog', 'sog-stream://resource/1.sog']);
    expect(sources.get(native.filenames[1])?.file.filePath).toBe('/api/file/preview?fileId=coarse');
    expect(sources.get(native.filenames[1])?.path).toBe('wrapper/1_0.sog');
    expect(parsed.filenames).toEqual(data.filenames);
  });

  it('keeps unresolved chunks logical until their LOD address is requested', () => {
    const files = [{ relativePath: 'lod-meta.json', role: 'MAIN' }];
    const parsed = parseStreamedSogManifest(data, files, true);
    const { data: native, sources } = nativeSogManifest(parsed, files, true);
    expect(sources.get(native.filenames[1])?.path).toBe('1_0.sog');
    expect(sources.get(native.filenames[1])?.file.filePath).toBeUndefined();
  });

  it('routes loose directory chunks through synthetic SOG URLs', () => {
    const files = [
      { relativePath: 'lod-meta.json', filePath: '/root' },
      { relativePath: '0_0/meta.json', filePath: '/fine' },
      { relativePath: '1_0/meta.json', filePath: '/coarse' },
    ];
    const parsed = parseStreamedSogManifest({
      ...data,
      version: 1,
      count: 30,
      counts: [20, 10],
      filenames: ['0_0/meta.json', '1_0/meta.json'],
    }, files);
    const { data: native, sources } = nativeSogManifest(parsed, files);
    expect(native.filenames).toEqual(['sog-stream://resource/0.sog', 'sog-stream://resource/1.sog']);
    expect(sources.get(native.filenames[0])?.path).toBe('0_0/meta.json');
    expect(sources.get(native.filenames[1])?.file.filePath).toBe('/coarse');
  });

  it('passes the loose source to the bundle fetcher and loads it as a SOG asset', async () => {
    const url = 'sog-stream://resource/1.sog';
    const source = { path: '1_0/meta.json', file: { filePath: 'https://example.com/coarse' } };
    const fetchChunk = vi.fn(async () => new Uint8Array([1, 2, 3]));
    const assets = {
      add: vi.fn(),
      remove: vi.fn(),
      load: vi.fn((asset: any) => {
        asset.resource = { coarse: true };
        asset.fire('load');
      }),
    };
    const loader = new PackageSogAssetLoader(
      { assets } as unknown as Application,
      new Map([[url, source]]),
      fetchChunk,
      vi.fn(),
    );
    loader.load(url);
    await vi.waitFor(() => expect(loader.getResource(url)).toBeTruthy());
    expect(fetchChunk).toHaveBeenCalledWith(expect.any(String), expect.any(AbortSignal), source);
    expect(assets.add.mock.calls[0][0].file.filename).toBe('1.sog');
    loader.destroy();
  });

  it('reports a failed chunk once instead of retrying it every frame', async () => {
    const url = 'sog-stream://resource/0.sog';
    const onError = vi.fn();
    const fetchChunk = vi.fn(async () => { throw new Error('HTTP 404'); });
    const loader = new PackageSogAssetLoader(
      {} as Application,
      new Map([[url, { path: '0_0.sog', file: { filePath: 'https://example.com/0_0.sog' } }]]),
      fetchChunk,
      onError,
    );
    loader.load(url);
    await vi.waitFor(() => expect(loader.hasFailed(url)).toBe(true));
    loader.load(url);
    expect(fetchChunk).toHaveBeenCalledTimes(1);
    expect(onError).toHaveBeenCalledTimes(1);
    expect(onError.mock.calls[0][0].message).toContain('0_0.sog');
    loader.destroy();
  });

  it('preloads and retains the coarse fallback while PlayCanvas releases node references', async () => {
    const url = 'sog-stream://resource/1.sog';
    const fetchChunk = vi.fn(async () => new Uint8Array([1, 2, 3]));
    const assets = {
      add: vi.fn(),
      remove: vi.fn(),
      load: vi.fn((asset: any) => {
        asset.resource = { coarse: true };
        asset.fire('load');
      }),
    };
    const loader = new PackageSogAssetLoader(
      { assets } as unknown as Application,
      new Map([[url, { path: '1_0.sog', file: { filePath: 'https://example.com/1_0.sog' } }]]),
      fetchChunk,
      vi.fn(),
      undefined,
      new Set([url]),
    );

    await loader.preloadPinned(new AbortController().signal);
    expect(fetchChunk).toHaveBeenCalledTimes(1);
    expect(loader.getResource(url)).toBeTruthy();
    loader.unload(url);
    expect(loader.getResource(url)).toBeTruthy();
    loader.load(url);
    expect(fetchChunk).toHaveBeenCalledTimes(1);
    loader.destroy();
    expect(loader.getResource(url)).toBeUndefined();
  });

  it('preloads a requested fixed level and can release it after cancellation', async () => {
    const urls = ['sog-stream://resource/0.sog', 'sog-stream://resource/1.sog'];
    const assets = {
      add: vi.fn(),
      remove: vi.fn(),
      load: vi.fn((asset: any) => {
        asset.resource = { loaded: true };
        asset.fire('load');
      }),
    };
    const loader = new PackageSogAssetLoader(
      { assets } as unknown as Application,
      new Map(urls.map((url, index) => [url, {
        path: `${index}.sog`, file: { filePath: `https://example.com/${index}.sog` },
      }])),
      vi.fn(async () => new Uint8Array([1, 2, 3])),
      vi.fn(),
    );
    const onProgress = vi.fn();
    await loader.preloadUrls(urls, new AbortController().signal, onProgress);
    expect(onProgress).toHaveBeenCalledTimes(2);
    expect(urls.every((url) => loader.getResource(url))).toBe(true);
    urls.forEach((url) => loader.unload(url));
    expect(urls.every((url) => !loader.getResource(url))).toBe(true);
    expect(assets.remove).toHaveBeenCalledTimes(2);
    loader.destroy();
  });

  it('loads saved-view chunks first and reorders pending work when the view changes', async () => {
    const urls = Array.from({ length: 6 }, (_, index) => `sog-stream://resource/${index}.sog`);
    const started: string[] = [];
    let release!: () => void;
    const gate = new Promise<void>((resolve) => { release = resolve; });
    const assets = {
      add: vi.fn(), remove: vi.fn(),
      load: vi.fn((asset: any) => {
        asset.resource = { loaded: true };
        asset.fire('load');
      }),
    };
    const loader = new PackageSogAssetLoader(
      { assets } as unknown as Application,
      new Map(urls.map((url, index) => [url, {
        path: `${index}.sog`, file: { filePath: `https://example.com/${index}.sog` },
      }])),
      async (url) => {
        started.push(url);
        await gate;
        return new Uint8Array([1, 2, 3]);
      },
      vi.fn(), undefined, new Set(urls),
    );
    loader.setPriorityOrder([urls[1], urls[2], urls[3], urls[4], urls[5], urls[0]]);
    const pending = loader.preloadPinned(new AbortController().signal);
    await vi.waitFor(() => expect(started).toHaveLength(4));
    expect(started.map((url) => url.split('/').pop())).toEqual(['1.sog', '2.sog', '3.sog', '4.sog']);
    loader.setPriorityOrder([urls[0], urls[5], ...urls.slice(1, 5)]);
    release();
    await pending;
    expect(started[4]).toMatch(/\/0\.sog$/);
    loader.destroy();
  });

  it('cancels pending coarse preload when the viewer is destroyed', async () => {
    const url = 'sog-stream://resource/1.sog';
    const loader = new PackageSogAssetLoader(
      { assets: { add: vi.fn(), remove: vi.fn(), load: vi.fn() } } as unknown as Application,
      new Map([[url, { path: '1_0.sog', file: { filePath: 'https://example.com/1_0.sog' } }]]),
      () => new Promise<Uint8Array>(() => {}),
      vi.fn(),
      undefined,
      new Set([url]),
    );
    const pending = loader.preloadPinned(new AbortController().signal);

    loader.destroy();
    await expect(pending).rejects.toMatchObject({ name: 'AbortError' });
  });
});
