import type {RouteLocationNormalizedLoaded, RouteLocationRaw} from 'vue-router';
import type {GlobalTabRoute} from '@/interface';
import {globalRouter} from '@/router';
import {getNodeByParam} from '@/utils';
import {useAuthStore} from '@/store';

/**
 *  根据vue路由获取tab路由
 * @param route
 */
export function getTabRouteByVueRoute(route: RouteLocationNormalizedLoaded) {
  const authStore = useAuthStore()

  let menu: any | undefined = getNodeByParam(authStore.menus, route.query.menuId as string)
  let meta = route.meta
  let icon = menu?.iconText
  if (route.path === '/' && !menu) {
    meta.title = '首页'
    icon = 'c:home'
  }

  const tabRoute: GlobalTabRoute = {
    path: route.path,
    query: route.query,
    icon: icon,
    meta: meta,
    scrollPosition: {
      left: 0,
      top: 0
    }
  };
  return tabRoute;
}

/**
 * 路由跳转
 * @param to - 需要跳转的路由
 * @param newTab - 是否在新的浏览器Tab标签打开
 */
export function routerPush(to?: RouteLocationRaw) {
  if (!to) return
  globalRouter.push(to);
}


/**
 * 获取该页签在多页签数据中的索引
 * @param tabs - 多页签数据
 * @param path - 该页签的路径
 */
export function getTabRoute(tabs: GlobalTabRoute[], path: string): RouteLocationRaw | undefined {
  let tab = tabs.find(tab => tab.path === path);
  if (!tab) return undefined
  return {path: tab.path, query: tab.query}
}

/**
 * 获取该页签在多页签数据中的索引
 * @param tabs - 多页签数据
 * @param path - 该页签的路径
 */
export function getIndexInTabRoutes(tabs: GlobalTabRoute[], path: string) {
  return tabs.findIndex(tab => tab.path === path);
}

/**
 * 判断该页签是否在多页签数据中
 * @param tabs - 多页签数据
 * @param path - 该页签的路径
 */
export function isInTabRoutes(tabs: GlobalTabRoute[], path: string) {
  return getIndexInTabRoutes(tabs, path) > -1;
}
