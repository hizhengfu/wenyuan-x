import { computed, onActivated, onBeforeUnmount, onDeactivated, onMounted, ref, watch, type WatchSource } from 'vue';
import { getActivePinia } from 'pinia';
import { useAuthStore } from '@/store/modules/auth';
import request from '@/utils/request';
import { scheduleImageRead } from './imageRequestQueue';

/** 只对应用自己的文件入口重新读取；第三方图片不经带登录凭据的请求客户端。 */
export function isApplicationImageUrl(value: string): boolean {
  try {
    const base = new URL(APP_BASE_URL || '.', window.location.href.split('#')[0]);
    const url = new URL(value, window.location.href);
    const prefix = base.pathname.replace(/\/$/, '');
    return (
      /^https?:$/.test(url.protocol) &&
      url.origin === base.origin &&
      !url.username &&
      !url.password &&
      (url.pathname.startsWith(`${prefix}/userfiles/`) || url.pathname === `${prefix}${APP_API_PATH}/file/preview`)
    );
  } catch {
    return false;
  }
}

/** 保留 fileId、storageType 等既有查询与片段；外部图片保持原样。 */
export function imageSizeUrl(value: string, width?: number, height?: number, padding = false): string {
  if (!value || (!width && !height && !padding) || !isApplicationImageUrl(value)) return value;
  const url = new URL(value, window.location.href);
  if (width) url.searchParams.set('w', String(width));
  if (height) url.searchParams.set('h', String(height));
  if (padding) url.searchParams.set('p', '');
  return url.href;
}

export type CurrentImageReader = (url: string, signal: AbortSignal) => Promise<Blob>;

/** API 图片只允许应用 API 根路径内的地址，不向外部地址转交登录凭据。 */
export function isApplicationApiImageUrl(value: string): boolean {
  try {
    const base = new URL(APP_BASE_URL || '.', window.location.href.split('#')[0]);
    const url = new URL(value, window.location.href);
    const prefix = base.pathname.replace(/\/$/, '') + APP_API_PATH.replace(/\/$/, '') + '/';
    return (
      /^https?:$/.test(url.protocol) &&
      url.origin === base.origin &&
      !url.username &&
      !url.password &&
      url.pathname.startsWith(prefix)
    );
  } catch {
    return false;
  }
}
function isThumbnailUrl(value: string): boolean {
  if (!isApplicationImageUrl(value)) return false;
  const url = new URL(value, window.location.href);
  return ['w', 'h'].some(key => Number(url.searchParams.get(key)) > 0);
}
async function requestImageBlob(url: string, signal: AbortSignal, params?: object, silentError = false): Promise<Blob> {
  const response: unknown = await request({
    url: new URL(url, window.location.href).href,
    params,
    method: 'get',
    responseType: 'blob',
    silentError,
    signal,
    adapter: 'fetch',
    // 缩略图使用浏览器私有缓存，每次仍向服务端验证；原图和其他 API 维持直接读取。
    fetchOptions: { cache: isThumbnailUrl(url) ? 'no-cache' : 'no-store', redirect: 'error' },
  });
  if (!(response instanceof Blob) || !response.size || /(?:json|text\/html)/i.test(response.type))
    throw new Error('图片读取结果无效');
  return response;
}
export async function readCurrentImage(
  url: string,
  signal: AbortSignal,
  options?: { silentError?: boolean },
): Promise<Blob> {
  if (!isApplicationImageUrl(url)) throw new Error('非应用图片不能使用登录请求');
  return scheduleImageRead(signal, () => requestImageBlob(url, signal, undefined, options?.silentError));
}
export async function readCurrentApiImage(url: string, signal: AbortSignal, params?: object): Promise<Blob> {
  if (!isApplicationApiImageUrl(url)) throw new Error('非应用 API 不能使用登录图片请求');
  return requestImageBlob(url, signal, params);
}

/** 每次读取先清除旧内容；即使底层忽略 abort，过期响应也不能重新显示。 */
export function useCurrentImageRead(reader: CurrentImageReader = readCurrentImage) {
  const url = ref<string>();
  const loading = ref(false);
  const error = ref('');
  let generation = 0;
  let abort: AbortController | undefined;
  function clear() {
    generation++;
    abort?.abort();
    abort = undefined;
    if (url.value) URL.revokeObjectURL(url.value);
    url.value = undefined;
    loading.value = false;
    error.value = '';
  }
  async function read(source: string) {
    clear();
    if (!source) return false;
    const ticket = generation;
    abort = new AbortController();
    loading.value = true;
    try {
      const blob = await reader(source, abort.signal);
      if (ticket !== generation) return false;
      url.value = URL.createObjectURL(blob);
      return true;
    } catch {
      if (ticket === generation) error.value = '图片读取失败，请核对当前授权和文件状态。';
      return false;
    } finally {
      if (ticket === generation) {
        loading.value = false;
        abort = undefined;
      }
    }
  }
  onBeforeUnmount(clear);
  return { url, loading, error, clear, read };
}

/** KeepAlive、会话及数据版本变化均关闭旧预览；初次激活不重复请求。 */
export function useCurrentImageLifecycle(source: WatchSource, invalidate: () => void, reload: () => void) {
  const active = ref(false);
  const auth = getActivePinia() ? useAuthStore() : undefined;
  let disposed = false;
  let resumeAfterPageShow = false;
  const loggedOut = ref(false);
  const eligible = computed(() => active.value && !disposed && !loggedOut.value && !auth?.identitySyncing);
  function refresh() {
    invalidate();
    if (eligible.value) reload();
  }
  watch(source, refresh, { deep: true, flush: 'sync' });
  if (auth)
    watch(
      () => [auth.token, auth.authorizationName, auth.userInfo?.id, auth.roles, auth.permissions, auth.identityRevision, auth.identitySyncing],
      (current, previous) => {
        if (previous?.[0] && !current[0]) loggedOut.value = true;
        else if (current[0]) loggedOut.value = false;
        refresh();
      },
      { deep: true, flush: 'sync' },
    );
  const leave = () => {
    active.value = false;
    invalidate();
  };
  const enter = () => {
    if (!active.value && !disposed) {
      active.value = true;
      refresh();
    }
  };
  onMounted(enter);
  onActivated(enter);
  onDeactivated(() => {
    resumeAfterPageShow = false;
    leave();
  });
  onBeforeUnmount(() => {
    disposed = true;
    leave();
  });
  // bfcache 恢复也必须重新读取；移除监听随组件销毁。
  const onHide = () => {
    resumeAfterPageShow = active.value;
    leave();
  };
  const onShow = () => {
    if (resumeAfterPageShow) {
      resumeAfterPageShow = false;
      enter();
    }
  };
  onMounted(() => {
    window.addEventListener('pagehide', onHide);
    window.addEventListener('pageshow', onShow);
  });
  onBeforeUnmount(() => {
    window.removeEventListener('pagehide', onHide);
    window.removeEventListener('pageshow', onShow);
  });
  return { eligible, refresh };
}
