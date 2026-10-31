import type { Router } from 'vue-router';
import { useTitle } from '@vueuse/core';
import { notification } from '@/utils';
import { useAuthStore, useTabStore } from '@/store';
import { useNProgress } from '@vueuse/integrations/useNProgress';
import '@/assets/style/nprogress.css';
import { reportRouteView } from './interaction';
import { shouldHidePlatformInitialLoading } from '@/utils/platformLoading.shared.ts';

/**
 * 白名单
 * */
const whiteList = [
  '/error/network',
  '/error/500',
  '/error/403',
  '/error/empty',
  '/register',
  '/login',
  '/rfid-kiosk',
  '/auth/forgotPassword',
];
const loginPath = '/login';
const noPermissionPath = '/error/empty';

const { start, done, progress } = useNProgress();
const enableRouteLoadingBar = !shouldHidePlatformInitialLoading(APP_TARGET);

/**
 * 路由守卫函数
 * @param router - 路由实例
 * @param needAuth - 是否需要登录
 * @param needStat - 是否需要统计
 */
export function createRouterGuard(router: Router, needAuth: boolean = true, needStat: boolean = false) {
  const authStore = useAuthStore();
  const tabStore = useTabStore();
  router.beforeEach(async (to, from, next) => {
    // 开始 loadingBar
    if (enableRouteLoadingBar) {
      progress.value = 0;
      start();
    }
    const meta = to.meta || {};
    if (needAuth) {
      //检查是否登录
      const requiresAuth = meta.requiresAuth;

      //需要授权同时已经有认证信息
      // if (requiresAuth !== false && authStore.hasToken) {
      if (requiresAuth !== false) {
        //当跳转的界面是登录界面时直接到登录界面
        if (whiteList.includes(to.path)) {
          next();
        } else {
          //无用户信息加载用户信息
          if (!authStore.userInfo) {
            try {
              //获取用户信息
              await authStore.fetchInfo();
            } catch (e) {
              // notification({
              //   type: 'error',
              //   title: '获取用户信息失败!',
              //   content: `获取用户信息失败，${e}!`,
              //   duration: 3000,
              // });
            }
          }
          //无菜单加载菜单，由于登录时会直接返回用户信息，故不能放在获取用户信息的逻辑中
          if (authStore.userInfo && !authStore.menus) {
            try {
              await authStore.fetchMenu();
            } catch (e) {
              notification({
                type: 'error',
                title: '获取用户菜单失败!',
                content: `获取用户菜单信息失败，${e}!`,
                duration: 3000,
              });
              // authStore.clearUserInfo()
            }
          }
          //判断菜单是否在权限范围内，由于不是所有路径都在后端进行管理，此逻辑暂时不放开
          // if (!authStore.hasMenu(to.path)) {
          //   next({
          //     path: noPermissionPath,
          //     query: {redirect: to.fullPath}
          //   })
          // } else {
          //   tabStore.addTab(to)
          //   next()
          // }

          tabStore.addTab(to);
          next();
        }
      } else {
        //需要授权才能访问
        if (requiresAuth !== false && !whiteList.includes(to.path) && to.path !== loginPath) {
          next({
            path: loginPath,
            query: { redirect: to.fullPath },
          });
        } else {
          next();
        }
      }
    } else {
      next();
    }
  });
  router.afterEach((to, from, failure) => {
    // 只有成功导航才更新标题和采集访问；失败导航仍需结束进度条。
    if (!failure) {
      if (to.meta.title) useTitle(to.meta.title as string);
      if (needStat && authStore.userInfo?.id && !authStore.identitySyncing) {
        void reportRouteView(router, to, from);
      }
    }
    if (enableRouteLoadingBar) {
      done();
    }
    // 结束 loadingBar
  });
}
