import type { InjectionKey } from 'vue';

export type ScreenRefreshTaskKey = string | symbol;

export type ScreenRefreshTaskOptions = {
  key: ScreenRefreshTaskKey;
  interval: number;
  task: () => void | Promise<void>;
  enabled?: () => boolean;
  immediate?: boolean;
  refreshOnVisible?: boolean;
  onError?: (error: unknown) => void;
};

export type ScreenRefreshTaskHandle = {
  refresh: () => void;
  unregister: () => void;
};

export type ScreenRefreshScheduler = {
  register: (options: ScreenRefreshTaskOptions) => ScreenRefreshTaskHandle;
  refresh: (key?: ScreenRefreshTaskKey) => void;
  pause: (reason: ScreenRefreshTaskKey) => void;
  resume: (reason: ScreenRefreshTaskKey, refresh?: boolean) => void;
  start: () => void;
  dispose: () => void;
};

type RefreshTaskState = {
  options: ScreenRefreshTaskOptions;
  timer?: ReturnType<typeof setTimeout>;
  running: boolean;
  queued: boolean;
  removed: boolean;
};

export const SCREEN_REFRESH_SCHEDULER_KEY: InjectionKey<ScreenRefreshScheduler> = Symbol('SCREEN-REFRESH-SCHEDULER');

function normalizeInterval(interval: number) {
  return Math.max(250, Number.isFinite(interval) ? interval : 5_000);
}

export function createScreenRefreshScheduler(): ScreenRefreshScheduler {
  const tasks = new Set<RefreshTaskState>();
  const pauseReasons = new Set<ScreenRefreshTaskKey>();
  const visibilityPauseReason = Symbol('SCREEN-HIDDEN');
  let started = false;
  let disposed = false;
  if (typeof document !== 'undefined' && document.hidden) pauseReasons.add(visibilityPauseReason);

  function isPaused() {
    return pauseReasons.size > 0;
  }

  function clearTaskTimer(state: RefreshTaskState) {
    if (state.timer) clearTimeout(state.timer);
    state.timer = undefined;
  }

  function schedule(state: RefreshTaskState) {
    clearTaskTimer(state);
    if (!started || disposed || isPaused() || state.removed) return;
    state.timer = setTimeout(() => execute(state), normalizeInterval(state.options.interval));
  }

  function execute(state: RefreshTaskState) {
    clearTaskTimer(state);
    if (!started || disposed || isPaused() || state.removed) return;
    if (state.running) {
      state.queued = true;
      return;
    }
    if (state.options.enabled && !state.options.enabled()) {
      schedule(state);
      return;
    }

    state.running = true;
    Promise.resolve()
      .then(state.options.task)
      .catch((error) => state.options.onError?.(error))
      .finally(() => {
        state.running = false;
        if (state.removed || disposed || isPaused()) return;
        if (state.queued) {
          state.queued = false;
          execute(state);
        } else {
          // 以上一次异步任务完成时间为起点，避免慢请求和固定间隔定时器叠加。
          schedule(state);
        }
      });
  }

  function register(options: ScreenRefreshTaskOptions): ScreenRefreshTaskHandle {
    const state: RefreshTaskState = { options, running: false, queued: false, removed: false };
    tasks.add(state);
    if (started && !isPaused()) {
      if (options.immediate) execute(state);
      else schedule(state);
    }

    return {
      refresh: () => execute(state),
      unregister: () => {
        state.removed = true;
        clearTaskTimer(state);
        tasks.delete(state);
      },
    };
  }

  function refresh(key?: ScreenRefreshTaskKey) {
    tasks.forEach((state) => {
      if (key === undefined || state.options.key === key) execute(state);
    });
  }

  function pause(reason: ScreenRefreshTaskKey) {
    if (disposed || pauseReasons.has(reason)) return;
    pauseReasons.add(reason);
    tasks.forEach(clearTaskTimer);
  }

  function resume(reason: ScreenRefreshTaskKey, refreshTasks = true) {
    if (disposed || !pauseReasons.delete(reason) || isPaused() || !started) return;
    tasks.forEach((state) => {
      if (refreshTasks) execute(state);
      else schedule(state);
    });
  }

  function handleVisibilityChange() {
    if (document.hidden) {
      pause(visibilityPauseReason);
      return;
    }
    if (!pauseReasons.delete(visibilityPauseReason) || isPaused() || !started) return;
    tasks.forEach((state) => state.options.refreshOnVisible !== false ? execute(state) : schedule(state));
  }

  function start() {
    if (started || disposed) return;
    started = true;
    if (typeof document !== 'undefined') {
      if (document.hidden) pauseReasons.add(visibilityPauseReason);
      else pauseReasons.delete(visibilityPauseReason);
      document.addEventListener('visibilitychange', handleVisibilityChange);
    }
    if (isPaused()) return;
    tasks.forEach((state) => {
      if (state.options.immediate) execute(state);
      else schedule(state);
    });
  }

  function dispose() {
    if (disposed) return;
    disposed = true;
    started = false;
    tasks.forEach((state) => {
      state.removed = true;
      clearTaskTimer(state);
    });
    tasks.clear();
    pauseReasons.clear();
    if (typeof document !== 'undefined') document.removeEventListener('visibilitychange', handleVisibilityChange);
  }

  return { register, refresh, pause, resume, start, dispose };
}
