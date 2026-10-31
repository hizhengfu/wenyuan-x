import {
  computed,
  isRef,
  onActivated,
  onDeactivated,
  shallowRef,
  watch,
  type WatchCallback,
  type WatchOptions,
  type WatchSource,
  type WatchStopHandle,
} from 'vue';
import { useRoute, type RouteLocationNormalizedLoaded } from 'vue-router';

type RoutePathMatcher = string | string[] | ((route: RouteLocationNormalizedLoaded) => boolean);
type RouteActiveWatchSource<T> = WatchSource<T> | (() => T);

/**
 * 与 Vue 原生 watch options 保持一致，只额外支持 path。
 * 不传 path 时，会默认使用 setup 执行时的 route.path。
 */
export interface UseRouteActiveWatchOptions extends WatchOptions {
  path?: RoutePathMatcher;
}

function matchesPath(route: RouteLocationNormalizedLoaded, matcher?: RoutePathMatcher): boolean {
  if (!matcher) {
    return true;
  }
  if (typeof matcher === 'function') {
    return matcher(route);
  }
  if (Array.isArray(matcher)) {
    return matcher.includes(route.path);
  }
  return route.path === matcher;
}

function isSameValue(value: unknown, oldValue: unknown): boolean {
  if (Object.is(value, oldValue)) {
    return true;
  }
  // route.query 可能是 string[]，这里按 Vue Router 常见取法比较第一个值。
  if (Array.isArray(value) || Array.isArray(oldValue)) {
    return String(Array.isArray(value) ? value[0] || '' : value || '') ===
      String(Array.isArray(oldValue) ? oldValue[0] || '' : oldValue || '');
  }
  return false;
}

function readSourceValue<T>(source: RouteActiveWatchSource<T>): T {
  if (typeof source === 'function') {
    return source();
  }
  if (isRef(source)) {
    return source.value;
  }
  return source as T;
}

/**
 * 只在当前 keep-alive 页面仍然激活时执行 callback 的 watch。
 *
 * 用法与 watch(source, callback, options) 基本一致：
 * useRouteActiveWatch(() => route.query.id, loadDetail, { immediate: true })
 *
 * 解决的问题：缓存详情页切到其他页面后，其他页面的 query 变化不应触发本页 watcher。
 */
export function useRouteActiveWatch<T>(
  source: RouteActiveWatchSource<T>,
  callback: WatchCallback<T>,
  options: UseRouteActiveWatchOptions = {},
): WatchStopHandle {
  const route = useRoute();
  const { path, immediate, ...watchOptions } = options;
  const ownerPath = path ?? route.path;
  const keepAliveActive = shallowRef(true);
  const active = computed(() => keepAliveActive.value && matchesPath(route, ownerPath));
  // 记录上一次真正触发业务 callback 的值，忽略页面非激活期间的 source 变化。
  let lastActiveValue = readSourceValue(source);
  let isImmediateCallback = Boolean(immediate);

  onActivated(() => {
    keepAliveActive.value = true;
  });

  onDeactivated(() => {
    keepAliveActive.value = false;
  });

  return watch(
    // active 放进 source，可以在页面重新激活时检查当前值是否真的变化。
    () => [active.value, readSourceValue(source)] as const,
    ([isActive, value], _oldTuple, onCleanup) => {
      if (!isActive) {
        return;
      }

      if (!isImmediateCallback && isSameValue(value, lastActiveValue)) {
        return;
      }

      const oldValue = isImmediateCallback ? undefined : lastActiveValue;
      isImmediateCallback = false;
      lastActiveValue = value;
      callback(value, oldValue as T, onCleanup);
    },
    {
      ...watchOptions,
      immediate,
    },
  );
}
