import type { App } from 'vue';
import { createRouter, createWebHashHistory, RouteRecordRaw } from 'vue-router';
import { scrollBehavior } from './helpers';
import { createRouterGuard } from './guard';
import { configureWebInteractionClient, type InteractionClient } from '@/modules/sys/api/interaction';

export const globalRouter = createRouter({
  history: createWebHashHistory(),
  routes: [],
  scrollBehavior,
});

export async function setupRouter(
  app: App,
  routes: RouteRecordRaw[],
  needAuth: boolean = true,
  needStat: boolean = false,
  options: {
    waitForReady?: boolean;
    /** 统一配置路由采集和个人中心使用的产品标识、产品版本及路由协议版本。 */
    interactionClient?: Partial<Omit<InteractionClient, 'type'>>;
  } = {},
) {
  if (options.interactionClient) configureWebInteractionClient(options.interactionClient);
  routes.forEach((route) => {
    globalRouter.addRoute(route);
  });
  //默认404页面
  globalRouter.addRoute({ path: '/:pathMatch(.*)*', redirect: '/error/404' });
  createRouterGuard(globalRouter, needAuth, needStat);
  app.use(globalRouter);
  if (options.waitForReady !== false) {
    await globalRouter.isReady();
  }
}
