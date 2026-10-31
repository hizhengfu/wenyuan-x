import { Asset, platform, type Application } from 'playcanvas';
import { fileUrl } from '@/utils/path';
import { chunkPackagePath, fileIndicesForLevel, initialLevelForStreamedSog, type StreamedSogManifest } from './streamedSog';
import { normalizePackagePath, type SogPackageFile } from './sogBundle';

type ChunkSource = { path: string; file: SogPackageFile };
type FetchChunk = (url: string, signal: AbortSignal, source: ChunkSource) => Promise<Uint8Array>;
export type NativeSogResource = {
  octree: {
    fileResources: Map<number, object>;
    assetLoader: { destroy(): void } | null;
  } | null;
};

/** 引擎使用逻辑 URL 调度分块；实际字节仍从入库文件的受保护地址读取。 */
export function nativeSogManifest(
  source: StreamedSogManifest,
  packageFiles: SogPackageFile[],
  allowPartialFiles = false,
): { data: StreamedSogManifest; sources: Map<string, ChunkSource> } {
  const sources = new Map<string, ChunkSource>();
  const add = (path: string, index: string) => {
    const logicalUrl = `sog-stream://resource/${index}.sog`;
    const packagePath = chunkPackagePath(source, path);
    const file = packageFiles.find((item) => normalizePackagePath(item.relativePath || '') === packagePath) ||
      { relativePath: packagePath };
    if (!allowPartialFiles && !file.filePath) throw new Error(`分块文件缺少访问路径：${packagePath}`);
    sources.set(logicalUrl, { path: packagePath, file });
    return logicalUrl;
  };
  const filenames = source.filenames.map((path, index) => add(path, String(index)));
  const environment = source.environment ? add(source.environment, 'environment') : undefined;
  return { data: { ...source, filenames, environment }, sources };
}

/** 满足 PlayCanvas octree 的按需 load/unload 协议，同时保留鉴权与资源释放。 */
export class PackageSogAssetLoader {
  private readonly assets = new Map<string, Asset>();
  private readonly active = new Map<string, AbortController>();
  private readonly queued: string[] = [];
  private readonly queuedVisible = new Set<string>();
  private priorityOrder = new Map<string, number>();
  private readonly failed = new Set<string>();
  private readonly waiters = new Map<string, Set<(failure?: Error) => void>>();
  private destroyed = false;

  constructor(
    private readonly app: Application,
    private readonly sources: Map<string, ChunkSource>,
    private readonly fetchChunk: FetchChunk,
    private readonly onError: (error: Error) => void,
    private readonly resolveChunkPath?: (path: string) => Promise<string>,
    private readonly pinnedUrls: ReadonlySet<string> = new Set(),
  ) {}

  /** 首屏先补齐完整粗层；分块仍会逐个进入画面，不阻塞首个可用画面。 */
  async preloadPinned(signal: AbortSignal, onProgress?: (loaded: number, total: number) => void): Promise<void> {
    return this.preloadUrls([...this.pinnedUrls].sort((a, b) => this.priority(a) - this.priority(b)), signal, onProgress);
  }

  /** Reorder pending downloads when the saved or current camera view changes. */
  setPriorityOrder(urls: string[]): void {
    this.priorityOrder = new Map(urls.map((url, index) => [url, index]));
    this.queued.sort((a, b) => this.priority(a) - this.priority(b));
  }

  private priority(url: string): number {
    return (this.queuedVisible.has(url) ? 0 : 1) * (this.sources.size + 1) +
      (this.priorityOrder.get(url) ?? this.sources.size);
  }

  /** Download and decode requested chunks while the octree remains renderable. */
  async preloadUrls(urls: string[], signal: AbortSignal, onProgress?: (loaded: number, total: number) => void): Promise<void> {
    let loaded = 0;
    await Promise.all(urls.map(async (url) => {
      await this.waitForResource(url, signal);
      onProgress?.(++loaded, urls.length);
    }));
  }

  private waitForResource(url: string, signal: AbortSignal): Promise<void> {
    if (this.destroyed || signal.aborted) return Promise.reject(new DOMException('已取消', 'AbortError'));
    if (this.getResource(url)) return Promise.resolve();
    if (this.failed.has(url)) return Promise.reject(new Error(`SOG 分块加载失败：${url}`));
    return new Promise((resolve, reject) => {
      const finish = (failure?: Error) => {
        signal.removeEventListener('abort', abort);
        const listeners = this.waiters.get(url);
        listeners?.delete(finish);
        if (listeners?.size === 0) this.waiters.delete(url);
        if (failure) reject(failure);
        else resolve();
      };
      const abort = () => finish(new DOMException('已取消', 'AbortError'));
      signal.addEventListener('abort', abort, { once: true });
      const listeners = this.waiters.get(url) || new Set();
      listeners.add(finish);
      this.waiters.set(url, listeners);
      if (signal.aborted) abort();
      else this.enqueue(url, false);
    });
  }

  private notifyWaiters(url: string, failure?: Error): void {
    for (const finish of [...(this.waiters.get(url) || [])]) finish(failure);
  }

  load(url: string): void {
    this.enqueue(url, true);
  }

  private enqueue(url: string, visible: boolean): void {
    if (this.destroyed || this.failed.has(url) || this.assets.get(url)?.resource || this.active.has(url)) return;
    if (!this.sources.has(url)) {
      this.failed.add(url);
      this.onError(new Error(`未知的 SOG 分块：${url}`));
      return;
    }
    const queuedIndex = this.queued.indexOf(url);
    if (queuedIndex >= 0) this.queued.splice(queuedIndex, 1);
    if (visible) this.queuedVisible.add(url);
    const priority = this.priority(url);
    const insertAt = this.queued.findIndex((queued) => this.priority(queued) > priority);
    this.queued.splice(insertAt < 0 ? this.queued.length : insertAt, 0, url);
    this.pump();
  }

  getResource(url: string): object | undefined {
    return this.assets.get(url)?.resource || undefined;
  }

  hasFailed(url: string): boolean {
    return this.failed.has(url);
  }

  unload(url: string): void {
    // PlayCanvas 可以释放节点引用，但粗层解码资源要留在会话内，视角移动时立即补位。
    if (!this.destroyed && this.pinnedUrls.has(url)) return;
    const queuedIndex = this.queued.indexOf(url);
    if (queuedIndex >= 0) this.queued.splice(queuedIndex, 1);
    this.queuedVisible.delete(url);
    this.active.get(url)?.abort();
    this.active.delete(url);
    const asset = this.assets.get(url);
    if (asset) {
      this.assets.delete(url);
      this.app.assets.remove(asset);
      asset.unload();
    }
    this.failed.delete(url);
    this.pump();
  }

  destroy(): void {
    this.destroyed = true;
    for (const url of [...this.waiters.keys()]) this.notifyWaiters(url, new DOMException('已取消', 'AbortError'));
    this.queued.length = 0;
    this.queuedVisible.clear();
    for (const url of new Set([...this.active.keys(), ...this.assets.keys()])) this.unload(url);
  }

  private pump(): void {
    // Mobile decodes fewer chunks concurrently to keep temporary ZIP, image
    // and GPU allocations from peaking together.
    const maxConcurrentLoads = platform.mobile ? 2 : 4;
    while (!this.destroyed && this.active.size < maxConcurrentLoads && this.queued.length) {
      const url = this.queued.shift()!;
      this.queuedVisible.delete(url);
      const controller = new AbortController();
      this.active.set(url, controller);
      void this.loadOne(url, controller);
    }
  }

  private async loadOne(url: string, controller: AbortController): Promise<void> {
    try {
      const source = this.sources.get(url)!;
      const path = source.file.filePath || await this.resolveChunkPath?.(source.path);
      if (!path) throw new Error(`分块文件缺少访问路径：${source.path}`);
      const bytes = await this.fetchChunk(fileUrl(path), controller.signal, source);
      if (controller.signal.aborted || this.destroyed) return;
      // Loose chunks are assembled as SOG bundles by the caller. The synthetic
      // .sog filename selects the bundle parser for both package layouts.
      const filename = url.split('/').pop() || 'chunk.sog';
      const asset = new Asset(filename, 'gsplat', {
        url: filename,
        filename,
        contents: bytes.byteOffset === 0 && bytes.byteLength === bytes.buffer.byteLength && bytes.buffer instanceof ArrayBuffer
          ? bytes.buffer : bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength) as ArrayBuffer,
      });
      this.assets.set(url, asset);
      this.app.assets.add(asset);
      await new Promise<void>((resolve, reject) => {
        const abort = () => reject(new DOMException('已取消', 'AbortError'));
        controller.signal.addEventListener('abort', abort, { once: true });
        asset.once('load', () => {
          controller.signal.removeEventListener('abort', abort);
          resolve();
        });
        asset.once('error', (failure: unknown) => {
          controller.signal.removeEventListener('abort', abort);
          reject(failure);
        });
        this.app.assets.load(asset);
      });
      this.notifyWaiters(url);
    } catch (failure: any) {
      if (!controller.signal.aborted && !this.destroyed) {
        this.failed.add(url);
        const asset = this.assets.get(url);
        if (asset) {
          this.assets.delete(url);
          this.app.assets.remove(asset);
          asset.unload();
        }
        const error = new Error(`SOG 分块加载失败：${this.sources.get(url)?.path || url}，${failure?.message || String(failure)}`);
        this.notifyWaiters(url, error);
        this.onError(error);
      }
    } finally {
      if (this.active.get(url) === controller) this.active.delete(url);
      this.pump();
    }
  }
}

export async function createNativeStreamedSog(
  app: Application,
  source: StreamedSogManifest,
  packageFiles: SogPackageFile[],
  fetchChunk: FetchChunk,
  onError: (error: Error) => void,
  resolveChunkPath?: (path: string) => Promise<string>,
): Promise<{ asset: Asset; objectUrl: string; resource: NativeSogResource; loader: PackageSogAssetLoader; fileUrls: string[] }> {
  const { data, sources } = nativeSogManifest(source, packageFiles, !!resolveChunkPath);
  const coarseLevel = initialLevelForStreamedSog(source);
  const pinnedUrls = new Set(fileIndicesForLevel(source, coarseLevel).map((index) => data.filenames[index]));
  const objectUrl = URL.createObjectURL(new Blob([JSON.stringify(data)], { type: 'application/json' }));
  const asset = new Asset('sog-streamed-scene', 'gsplat', { url: `${objectUrl}#/lod-meta.json` });
  app.assets.add(asset);
  try {
    await new Promise<void>((resolve, reject) => {
      asset.once('load', () => resolve());
      asset.once('error', (failure: unknown) => reject(failure));
      app.assets.load(asset);
    });
    const resource = asset.resource as NativeSogResource;
    const octree = resource?.octree;
    if (!octree) throw new Error('PlayCanvas 未识别 Streamed SOG 层级清单');
    octree.assetLoader?.destroy();
    const loader = new PackageSogAssetLoader(app, sources, fetchChunk, onError, resolveChunkPath, pinnedUrls);
    octree.assetLoader = loader;
    return { asset, objectUrl, resource, loader, fileUrls: data.filenames };
  } catch (failure) {
    app.assets.remove(asset);
    asset.unload();
    URL.revokeObjectURL(objectUrl);
    throw failure;
  }
}
