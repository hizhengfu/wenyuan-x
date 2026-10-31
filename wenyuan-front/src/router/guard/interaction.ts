import type { RouteLocationNormalized, Router } from 'vue-router';
import { recordInteractionView, type InteractionPage } from '@/modules/sys/api/interaction';

declare module 'vue-router' {
  interface RouteMeta {
    /** false 表示页面自行采集或不记录；业务内容加载后采集时应关闭守卫采集。 */
    interactionView?: boolean;
    /** 跨路径版本保持不变的页面标识；缺省使用路由名或完整路由模板。 */
    interactionPageKey?: string;
    /** 参与页面身份的 query 白名单，缺省仅 id；分页、筛选、标题不参与去重。 */
    interactionQueryKeys?: string[];
  }
}

const excludedPath = /^\/(?:error|auth|login|register|redirect|rfid-kiosk)(?:\/|$)/;
const credentialKey = /(?:token|password|passwd|secret|signature|authorization|credential)|^(?:code|ticket)$/i;
const validKey = /^[A-Za-z0-9_.:/-]{1,100}$/;
const validParam = /^[A-Za-z0-9_.-]{1,64}$/;

/** 只收录普通内部页面及允许的身份参数，避免把凭据、筛选条件和锚点带入历史路径。 */
export function routeInteractionPage(router: Router, route: RouteLocationNormalized): InteractionPage | null {
  const last = route.matched.at(-1);
  if (
    route.meta.interactionView === false ||
    excludedPath.test(route.path) ||
    !last?.components ||
    last.redirect ||
    route.matched.some((record) => record.path.includes('(.*)'))
  )
    return null;
  const key = route.meta.interactionPageKey ?? (typeof route.name === 'string' ? route.name : last.path);
  if (!validKey.test(key)) return null;
  const params: Record<string, string> = {};
  const query: Record<string, string> = {};
  for (const [name, value] of Object.entries(route.params)) {
    if (!validParam.test(name) || credentialKey.test(name) || typeof value !== 'string' || value.length > 256)
      return null;
    params[name] = value;
  }
  for (const name of route.meta.interactionQueryKeys ?? ['id']) {
    if (!validParam.test(name) || credentialKey.test(name) || name === 'title') continue;
    const value = route.query[name];
    if (value == null || Object.hasOwn(params, name)) continue;
    if (typeof value !== 'string' || value.length > 256) return null;
    params[name] = value;
    query[name] = value;
  }
  if (Object.keys(params).length > 20) return null;
  let title = route.path;
  if (typeof route.meta.title === 'string') title = route.meta.title;
  if (typeof route.query.title === 'string') title = route.query.title;
  title = title.trim().slice(0, 255);
  // title 是项目现有页签约定：保存以便再次打开显示标题，但不作为页面身份。
  if (typeof route.query.title === 'string' && title) query.title = title;
  try {
    const decodedPath = decodeURIComponent(route.path);
    if (!decodedPath.startsWith('/') || decodedPath.startsWith('//') || /[\\\u0000-\u001f\u007f]/.test(decodedPath))
      return null;
    let path = router.resolve({ path: route.path, query }).fullPath;
    if (path.length > 512) {
      delete query.title;
      path = router.resolve({ path: route.path, query }).fullPath;
    }
    if (path.length > 512) return null;
    return { key, params, title, path };
  } catch {
    return null;
  }
}

/** 比较业务页面身份；改变查询参数顺序、标题或列表页码不算另一次页面访问。 */
function samePage(left: InteractionPage, right: InteractionPage): boolean {
  return (
    left.key === right.key &&
    Object.keys(left.params).length === Object.keys(right.params).length &&
    Object.entries(left.params).every(([name, value]) => right.params[name] === value)
  );
}

/**
 * 由守卫在成功导航后调用；实际离开再返回会生成新事件，不缓存所有已访问页面。
 * 采集失败不阻塞导航、不弹出业务错误，也不在前端累计无限重试队列。
 */
export async function reportRouteView(
  router: Router,
  to: RouteLocationNormalized,
  from: RouteLocationNormalized,
): Promise<void> {
  const page = routeInteractionPage(router, to);
  if (!page) return;
  const previous = routeInteractionPage(router, from);
  if (previous && samePage(previous, page)) return;
  try {
    await recordInteractionView(page);
  } catch {
    // 浏览为尽力采集；401 仍由统一请求拦截器处理登录态。
  }
}
