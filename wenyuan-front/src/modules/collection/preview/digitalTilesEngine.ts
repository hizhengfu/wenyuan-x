import { openCurrentTileset } from '@/components/basic/cesium/currentTilesetEngine';
import type { PreviewCesiumRuntime } from '@/components/basic/cesium/previewCesiumRuntime';
/** A separate document owns this preview's Cesium runtime, caches and dedicated workers. */
export function openDigitalTiles(
  host: HTMLElement,
  url: string,
  callbacks: { loaded: () => void; failed: (reason: unknown) => void },
) {
  let closed = false,
    frame: HTMLIFrameElement | undefined,
    engine: ReturnType<typeof openCurrentTileset> | undefined,
    timer: ReturnType<typeof setTimeout> | undefined;
  let cancelRuntime = () => {},
    finishClose = () => {},
    releaseRuntimeResources = () => {};
  const ended = new Promise<void>((resolve) => (finishClose = resolve));
  function close() {
    if (closed) return;
    closed = true;
    finishClose();
    clearTimeout(timer);
    cancelRuntime();
    cancelRuntime = () => {};
    try {
      engine?.close();
    } finally {
      engine = undefined;
      releaseRuntimeResources();
      releaseRuntimeResources = () => {};
      frame?.remove();
      frame = undefined;
    }
  }
  function fail(reason: unknown) {
    if (closed) return;
    close();
    callbacks.failed(reason);
  }
  const work = (async () => {
    try {
      if (!ENABLE_CESIUM) throw new Error('当前平台未启用瓦片预览，请核对平台配置。');
      if (!url.startsWith('blob:')) throw new Error('瓦片入口必须是已经核验的本地预览地址。');
      frame = document.createElement('iframe');
      frame.title = '已核验瓦片模型预览';
      frame.style.cssText = 'width:100%;height:100%;border:0;display:block';
      const documentReady = new Promise<void>((resolve, reject) => {
        const clean = () => {
          clearTimeout(timer);
          frame!.onload = null;
          frame!.onerror = null;
        };
        cancelRuntime = () => {
          clean();
          reject(new DOMException('瓦片预览已取消', 'AbortError'));
        };
        frame!.onload = () => {
          clean();
          resolve();
        };
        frame!.onerror = () => {
          clean();
          reject(new Error('瓦片预览页面加载失败。'));
        };
        timer = setTimeout(() => {
          clean();
          reject(new Error('瓦片预览页面加载超时。'));
        }, 30000);
      });
      frame.src = new URL('./collection-preview.html', document.baseURI).href;
      host.appendChild(frame);
      await documentReady;
      if (closed) return;

      const doc = frame.contentDocument,
        win = frame.contentWindow;
      if (!doc || !win || doc.documentElement.dataset.collectionPreview !== 'true')
        throw new Error('瓦片预览页面未正确部署。');
      doc.documentElement.style.height = '100%';
      doc.body.style.cssText = 'margin:0;height:100%;overflow:hidden';
      const container = doc.createElement('div');
      container.style.cssText = 'width:100%;height:100%';
      doc.body.appendChild(container);
      // Instrument only the owned document; never patch the application's global runtime.
      const workers = new Set<Worker>(),
        workerUrls = new Set<string>(),
        scope = win as any;
      if (typeof scope.Worker === 'function') {
        const Original = scope.Worker;
        scope.Worker = new Proxy(Original, {
          construct(Type, args) {
            if (closed) throw new DOMException('预览已关闭', 'AbortError');
            const worker = Reflect.construct(Type, args) as Worker;
            workers.add(worker);
            return worker;
          },
        });
      }
      const originalCreate = scope.URL.createObjectURL?.bind(scope.URL),
        originalRevoke = scope.URL.revokeObjectURL?.bind(scope.URL);
      if (originalCreate && originalRevoke) {
        scope.URL.createObjectURL = (blob: Blob) => {
          if (closed) throw new DOMException('预览已关闭', 'AbortError');
          const value = originalCreate(blob);
          workerUrls.add(value);
          return value;
        };
        scope.URL.revokeObjectURL = (value: string) => {
          workerUrls.delete(value);
          originalRevoke(value);
        };
      }
      releaseRuntimeResources = () => {
        workers.forEach((worker) => worker.terminate());
        workers.clear();
        workerUrls.forEach((value) => scope.URL.revokeObjectURL(value));
        workerUrls.clear();
      };
      const base = new URL('./cesium/', document.baseURI).href;
      (win as any).CESIUM_BASE_URL = base;
      const css = doc.createElement('link');
      css.rel = 'stylesheet';
      css.href = base + 'Widgets/widgets.css';
      doc.head.appendChild(css);
      const script = doc.createElement('script');
      script.src = base + 'Cesium.js';
      script.async = true;
      const runtime = new Promise<PreviewCesiumRuntime>((resolve, reject) => {
        const clean = () => {
          clearTimeout(timer);
          script.onload = null;
          script.onerror = null;
        };
        cancelRuntime = () => {
          clean();
          script.remove();
          reject(new DOMException('瓦片预览已取消', 'AbortError'));
        };
        script.onload = () => {
          clean();
          if (closed) return;
          const value = (win as any).Cesium;
          if (typeof value?.Viewer !== 'function' || typeof value?.Cesium3DTileset?.fromUrl !== 'function') {
            reject(new Error('瓦片运行时不支持当前预览。'));
            return;
          }
          resolve(value);
        };
        script.onerror = () => {
          clean();
          reject(new Error('瓦片运行时加载失败，请核对部署资源。'));
        };
        timer = setTimeout(() => {
          clean();
          reject(new Error('瓦片运行时加载超时。'));
        }, 30000);
      });
      engine = openCurrentTileset({
        container,
        url,
        acceptPreparedBlob: true,
        waitForVisibleTiles: true,
        runtime: () => runtime,
        onLoad: () => {
          if (!closed) callbacks.loaded();
        },
        onError: fail,
      });
      doc.head.appendChild(script);
      await engine.ready;
    } catch (reason) {
      fail(reason);
    }
  })();
  return { close, ready: Promise.race([work, ended]) };
}
