import { inject, onBeforeUnmount, onMounted } from 'vue';
import {
  createScreenRefreshScheduler,
  SCREEN_REFRESH_SCHEDULER_KEY,
  type ScreenRefreshTaskHandle,
  type ScreenRefreshTaskOptions,
} from './refreshScheduler';

/**
 * 注册当前大屏作用域内的刷新任务。脱离 ScreenWrap 使用时会自动创建局部调度器。
 */
export function useScreenRefresh(options: ScreenRefreshTaskOptions) {
  const injectedScheduler = inject(SCREEN_REFRESH_SCHEDULER_KEY, null);
  const scheduler = injectedScheduler || createScreenRefreshScheduler();
  let handle: ScreenRefreshTaskHandle | undefined;

  onMounted(() => {
    handle = scheduler.register(options);
    if (!injectedScheduler) scheduler.start();
  });

  onBeforeUnmount(() => {
    handle?.unregister();
    if (!injectedScheduler) scheduler.dispose();
  });

  return {
    refresh: () => handle?.refresh(),
  };
}
