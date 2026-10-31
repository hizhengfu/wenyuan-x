import request from '@/utils/request';
import type { AxiosRequestConfig, AxiosResponse } from 'axios';
import { notification } from '@/utils/message';
import { apiUrl } from '@/utils/path';
import { isApplicationImageUrl, isApplicationApiImageUrl } from '@/utils/currentImage';
import { getActivePinia } from 'pinia';
import { watch } from 'vue';
import { useAuthStore } from '@/store/modules/auth';
import { saveAs } from 'file-saver';
import { useNProgress } from '@vueuse/integrations/useNProgress';

const { start, done, progress } = useNProgress(null, { showSpinner: true });
const transfers = new Set<AbortController>();
export interface DownloadOptions {
  signal?: AbortSignal;
  isCurrent?: () => boolean;
  /** 优先使用 Content-Disposition 中的文件名，未提供时保留调用方名称。 */
  preferServerFilename?: boolean;
}

function serverFilename(disposition: string): string | undefined {
  const encoded = /(?:^|;)\s*filename\*\s*=\s*(?:"([^"]*)"|([^;]*))/i.exec(disposition);
  const utf8 = /^UTF-8'[^']*'(.*)$/i.exec((encoded?.[1] ?? encoded?.[2] ?? '').trim());
  const plain = /(?:^|;)\s*filename\s*=\s*(?:"([^"]*)"|([^;]*))/i.exec(disposition);
  let name = plain?.[1] ?? plain?.[2] ?? '';
  try {
    if (utf8) name = decodeURIComponent(utf8[1]);
  } catch {
    // 编码异常时使用普通 filename 或调用方提供的名称。
  }
  const safeName = name
    .split(/[\\/]/)
    .pop()
    ?.replace(/[\x00-\x1f\x7f]/g, '')
    .trim();
  return safeName && !/^\.+$/.test(safeName) ? safeName : undefined;
}

function resolveDownloadUrl(value: string) {
  if (!value?.trim()) throw new Error('下载地址为空');
  const base = new URL(APP_BASE_URL || '.', location.href.split('#')[0]);
  const prefix = base.pathname.replace(/\/$/, '');
  const explicit = /^(?:[a-z][a-z\d+.-]*:|\/\/)/i.test(value);
  const complete = value.startsWith(`${prefix}/userfiles/`) || value.startsWith(`${prefix}${APP_API_PATH}/`);
  const url = new URL(explicit || complete ? value : apiUrl(value), location.href);
  if (!/^https?:$/.test(url.protocol) || url.username || url.password) throw new Error('下载地址不受支持');
  return url;
}
function appendParams(url: URL, params?: object) {
  for (const [key, value] of Object.entries(params || {})) {
    if (value == null) continue;
    url.searchParams.delete(key);
    for (const item of Array.isArray(value) ? value : [value])
      if (item != null) url.searchParams.append(key, String(item));
  }
  return url;
}

/** 应用地址使用当前登录请求；第三方地址使用不带凭据的 fetch。 */
export async function ajaxGetDownload(url: string, fileName: string, params?: object, options: DownloadOptions = {}) {
  const controller = new AbortController();
  let stopAuth: (() => void) | undefined;
  let cleaned = false;
  const current = () => !controller.signal.aborted && !options.signal?.aborted && (options.isCurrent?.() ?? true);
  const cancel = () => {
    controller.abort();
    cleanup();
  };
  const cleanup = () => {
    if (cleaned) return;
    cleaned = true;
    stopAuth?.();
    options.signal?.removeEventListener('abort', cancel);
    window.removeEventListener('pagehide', cancel);
    window.removeEventListener('hashchange', cancel);
    window.removeEventListener('popstate', cancel);
    if (transfers.delete(controller) && !transfers.size) done();
  };
  try {
    if (!current()) return false;
    const target = resolveDownloadUrl(url);
    options.signal?.addEventListener('abort', cancel, { once: true });
    window.addEventListener('pagehide', cancel);
    window.addEventListener('hashchange', cancel);
    window.addEventListener('popstate', cancel);
    const auth = getActivePinia() ? useAuthStore() : undefined;
    if (auth)
      stopAuth = watch(
        () => [auth.token, auth.authorizationName, auth.userInfo?.id, auth.roles, auth.permissions],
        cancel,
        { deep: true, flush: 'sync' },
      );
    if (!transfers.size) {
      start();
      progress.value = 0.5;
    }
    transfers.add(controller);
    let response: unknown;
    let outputName = fileName;
    if (isApplicationImageUrl(target.href) || isApplicationApiImageUrl(target.href)) {
      const rawResponse = options.preferServerFilename === true;
      const result = await request({
        url: target.href,
        params: params || {},
        method: 'get',
        responseType: 'blob',
        signal: controller.signal,
        adapter: 'fetch',
        fetchOptions: { cache: 'no-store', redirect: 'error' },
        ...(rawResponse ? { returnResponse: true } : {}),
      } as AxiosRequestConfig & { returnResponse?: boolean });
      if (rawResponse) {
        const original = result as unknown as AxiosResponse<Blob>;
        response = original.data;
        const headers = original.headers as Record<string, unknown> & { get?: (name: string) => unknown };
        outputName = serverFilename(String(headers?.get?.('Content-Disposition') ?? headers?.['content-disposition'] ?? '')) || fileName;
      } else response = result;
    } else {
      const external = await fetch(appendParams(target, params), {
        credentials: 'omit',
        cache: 'no-store',
        redirect: 'error',
        signal: controller.signal,
      });
      if (!external.ok) throw new Error('文件读取失败');
      if (options.preferServerFilename)
        outputName = serverFilename(external.headers.get('content-disposition') || '') || fileName;
      response = await external.blob();
    }
    if (!current()) return false;
    // 通用导出也支持合法 JSON/HTML 文件，不根据 MIME 一概丢弃。
    if (!(response instanceof Blob) || !response.size) throw new Error('下载响应不是有效文件');
    saveAs(response, outputName);
    return true;
  } catch {
    if (current()) notification({ content: '文件下载错误，请核对当前授权和文件状态。' });
    return false;
  } finally {
    cleanup();
  }
}

/** 只用于明确的链接下载；应用文件的默认 download 始终走可取消读取。 */
export function nativeDownload(url: string, fileName: string, params?: object) {
  const target = new URL(url, location.href);
  if (
    (!/^https?:$/.test(target.protocol) && !(target.protocol === 'blob:' && target.origin === location.origin)) ||
    target.username ||
    target.password
  )
    throw new Error('下载地址不受支持');
  const a = document.createElement('a');
  a.href = appendParams(target, params).href;
  a.download = fileName;
  a.rel = 'noopener';
  a.style.display = 'none';
  document.body.appendChild(a);
  try {
    a.click();
  } finally {
    a.remove();
  }
}

export function download(url: string, fileName: string, params?: object, options?: DownloadOptions) {
  return ajaxGetDownload(url, fileName, params, options);
}
