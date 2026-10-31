export interface MarmosetViewerInstance {
  domRoot: HTMLElement;
  onLoad?: () => void;
  onError?: (error?: unknown) => void;
  loadScene: () => void;
  resize: (width: number, height: number) => void;
  unload: () => void;
}

export interface MarmosetRuntime {
  WebViewer: new (width: number, height: number, fileUrl: string) => MarmosetViewerInstance;
  dataLocale?: string;
}

const RUNTIME_SCRIPT_ID = 'marmoset-viewer-runtime';

let runtimePromise: Promise<MarmosetRuntime> | undefined;

function runtimeAssetUrl(relativePath: string): string {
  return new URL(`./marmoset/${relativePath}`, document.baseURI).href;
}

function currentRuntime(): MarmosetRuntime | undefined {
  return (window as Window & { marmoset?: MarmosetRuntime }).marmoset;
}

function configureRuntime(runtime: MarmosetRuntime): MarmosetRuntime {
  // Marmoset 会按需读取按钮、动画和帮助图片，必须在创建 WebViewer 前指向本地 data 目录。
  runtime.dataLocale = runtimeAssetUrl('data/');
  return runtime;
}

/**
 * Marmoset 官方运行时只允许完整、未修改地嵌入。这里按需加载官方脚本，
 * 避免把它拆入 Vite bundle，并确保同一页面只请求一次。
 */
export function loadMarmosetRuntime(): Promise<MarmosetRuntime> {
  if (!ENABLE_MARMOSET) {
    return Promise.reject(new Error('当前平台未启用 Marmoset Viewer，请在 Vite 平台配置中设置 enable_marmoset=true'));
  }

  const loaded = currentRuntime();
  if (loaded?.WebViewer) return Promise.resolve(configureRuntime(loaded));
  if (runtimePromise) return runtimePromise;

  const pending = new Promise<MarmosetRuntime>((resolve, reject) => {
    const resolveLoadedRuntime = () => {
      const runtime = currentRuntime();
      if (runtime?.WebViewer) {
        resolve(configureRuntime(runtime));
      } else {
        document.getElementById(RUNTIME_SCRIPT_ID)?.remove();
        reject(new Error('Marmoset Viewer 运行时未正确初始化'));
      }
    };

    const existing = document.getElementById(RUNTIME_SCRIPT_ID) as HTMLScriptElement | null;
    if (existing) {
      existing.addEventListener('load', resolveLoadedRuntime, { once: true });
      existing.addEventListener('error', () => {
        existing.remove();
        reject(new Error('Marmoset Viewer 运行时加载失败'));
      }, { once: true });
      return;
    }

    const script = document.createElement('script');
    script.id = RUNTIME_SCRIPT_ID;
    script.src = runtimeAssetUrl('marmoset.js');
    script.async = true;
    script.addEventListener('load', resolveLoadedRuntime, { once: true });
    script.addEventListener('error', () => {
      script.remove();
      reject(new Error('Marmoset Viewer 运行时加载失败'));
    }, { once: true });
    document.head.appendChild(script);
  });

  runtimePromise = pending.catch((error) => {
    runtimePromise = undefined;
    throw error;
  });
  return runtimePromise;
}
