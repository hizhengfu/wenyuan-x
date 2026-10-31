export interface Go2rtcWsUrlOptions {
  appBaseUrl: string;
  pageUrl: string;
  useSameOriginStreamProxy: boolean;
}

/**
 * 开发环境经 Vite 的 /stream WebSocket 代理访问后端，避免远端媒体服务拒绝 localhost Origin。
 * 生产环境保留后端返回的地址；go2rtc 原生 1984 端口仍沿用既有同源代理约定。
 */
export function normalizeGo2rtcWsUrl(path: string, options: Go2rtcWsUrlOptions): string {
  const pageUrl = new URL(options.pageUrl);
  const apiBase = new URL(options.appBaseUrl, pageUrl);
  let url = new URL(path, apiBase);
  const isStreamPath = url.pathname === '/stream' || url.pathname.startsWith('/stream/');
  const useDevelopmentProxy =
    options.useSameOriginStreamProxy && isStreamPath && url.host.toLowerCase() === apiBase.host.toLowerCase();

  if (url.port === '1984' || useDevelopmentProxy) {
    url = new URL(`${url.pathname}${url.search}`, pageUrl.origin);
  }
  if (url.protocol === 'http:') url.protocol = 'ws:';
  else if (url.protocol === 'https:') url.protocol = 'wss:';
  else if (url.protocol !== 'ws:' && url.protocol !== 'wss:') {
    throw new Error(`不支持的播放地址协议: ${url.protocol}`);
  }
  return url.toString();
}
