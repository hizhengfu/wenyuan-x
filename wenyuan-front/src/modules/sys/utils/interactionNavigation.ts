import type { RouteLocationRaw, Router } from 'vue-router';
import type { InteractionClient, InteractionItem, InteractionPage } from '../api/interaction';

/** 平台提供的当前版本页面路由映射；不支持的 page.key 返回 null。 */
export type InteractionPageResolver = (page: InteractionPage) => RouteLocationRaw | null;

/** 校验编码解析后的路径仍为内部相对地址，拒绝协议相对地址、反斜线及控制字符。 */
function internalPath(path: string): boolean {
  try {
    const decoded = decodeURIComponent(path);
    return decoded.startsWith('/') && !decoded.startsWith('//') && !/[\\\u0000-\u001f\u007f]/.test(decoded);
  } catch {
    return false;
  }
}

/**
 * 按平台映射、同名 Vue 路由、兼容历史路径的顺序解析当前端可打开的位置。
 * 历史路径必须同客户端、同产品且路由版本明确匹配；失效内容或未知、跳转、错误路由返回 null。
 * 这里只解析导航，实际内容访问仍由业务接口鉴权。
 */
export function interactionLocation(
  item: InteractionItem,
  client: InteractionClient,
  router: Router,
  resolvePage?: InteractionPageResolver,
): RouteLocationRaw | null {
  if (!item.available || !item.page) return null;
  const page = item.page;
  try {
    let location = resolvePage?.(page) ?? null;
    if (!location && router.hasRoute(page.key)) {
      const record = router.getRoutes().find((route) => route.name === page.key)!;
      const names = [...record.path.matchAll(/:([A-Za-z0-9_]+)/g)].map((match) => match[1]);
      location = {
        name: page.key,
        params: Object.fromEntries(Object.entries(page.params).filter(([key]) => names.includes(key))),
        query: Object.fromEntries(Object.entries(page.params).filter(([key]) => !names.includes(key))),
      };
    }
    if (
      !location &&
      page.path &&
      item.client?.type === client.type &&
      item.client.appId === client.appId &&
      client.routeVersion &&
      item.client.routeVersion === client.routeVersion
    )
      location = page.path;
    if (!location) return null;
    const rawPath = typeof location === 'string' ? location : 'path' in location ? location.path : undefined;
    if (rawPath && !internalPath(rawPath)) return null;
    const resolved = router.resolve(location);
    const last = resolved.matched.at(-1);
    // 不能把未知页面交给 catch-all、登录页或外链重定向。
    if (
      !internalPath(resolved.fullPath.split('?')[0]) ||
      !last ||
      last.redirect ||
      !last.components ||
      resolved.matched.some((route) => route.path.includes('(.*)')) ||
      /^\/(?:error|auth|login|register)(?:\/|$)/.test(resolved.path)
    )
      return null;
    return resolved.fullPath;
  } catch {
    return null;
  }
}

/** 仅展示站内相对地址或 HTTPS 封面，非法协议和控制字符返回 undefined 以使用占位图。 */
export function interactionCover(value?: string | null): string | undefined {
  if (!value || /[\\\u0000-\u001f]/.test(value)) return undefined;
  if (value.startsWith('/') && !value.startsWith('//')) return value;
  try {
    return new URL(value).protocol === 'https:' ? value : undefined;
  } catch {
    return undefined;
  }
}
