import { ensureImageBitmapCompatibility, type CesiumImageResource } from './imageBitmapCompatibility';

export type PreviewCesiumRuntime = Pick<typeof import('cesium'), 'Viewer' | 'Cesium3DTileset'> & {
  Resource: CesiumImageResource;
};
let pending: Promise<PreviewCesiumRuntime> | undefined;
function currentRuntime(): PreviewCesiumRuntime | undefined {
  const runtime = (globalThis as typeof globalThis & { Cesium?: PreviewCesiumRuntime }).Cesium;
  return typeof runtime?.Viewer === 'function' &&
    typeof runtime?.Cesium3DTileset?.fromUrl === 'function' &&
    typeof runtime?.Resource?.supportsImageBitmapOptions === 'function'
    ? runtime
    : undefined;
}
/** The deployment already provides ./cesium. Share the script, never a Viewer or tileset. */
export function loadPreviewCesiumRuntime(): Promise<PreviewCesiumRuntime> {
  if (!ENABLE_CESIUM) return Promise.reject(new Error('当前平台未启用 Cesium 预览'));
  const runtime = currentRuntime();
  if (runtime) {
    ensureImageBitmapCompatibility(runtime.Resource);
    return Promise.resolve(runtime);
  }
  if (pending) return pending;
  const attempt = new Promise<PreviewCesiumRuntime>((resolve, reject) => {
    const url = new URL('./cesium/Cesium.js', document.baseURI).href;
    const existing = Array.from(document.scripts).find((script) => script.src === url);
    const script = existing || document.createElement('script');
    let timer: ReturnType<typeof setTimeout> | undefined;
    const cleanup = () => {
      clearTimeout(timer);
      script.removeEventListener('load', loaded);
      script.removeEventListener('error', failed);
    };
    const fail = (reason: Error) => {
      cleanup();
      if (!existing) script.remove();
      reject(reason);
    };
    const loaded = () => {
      const value = currentRuntime();
      if (!value) {
        fail(new Error('Cesium 运行时不支持当前瓦片预览'));
        return;
      }
      cleanup();
      ensureImageBitmapCompatibility(value.Resource);
      resolve(value);
    };
    const failed = () => fail(new Error('Cesium 运行时加载失败，请核对部署资源'));
    script.addEventListener('load', loaded);
    script.addEventListener('error', failed);
    timer = setTimeout(() => fail(new Error('Cesium 运行时加载超时')), 30000);
    if (!existing) {
      script.src = url;
      script.async = true;
      try {
        document.head.append(script);
      } catch (reason) {
        fail(reason instanceof Error ? reason : new Error(String(reason)));
      }
    }
  });
  pending = attempt;
  // Only retain the in-flight load: VueCesium can remove the runtime when a view closes.
  const clearPending = () => {
    if (pending === attempt) pending = undefined;
  };
  void attempt.then(clearPending, clearPending);
  return attempt;
}
