import { getAuthenticationHeaders } from '@/utils/auth';
import { asyncTaskEventsUrl } from '../api/asyncTask';
import type { AsyncTask, AsyncTaskStatus } from '../api/asyncTask';
import { useAuthStore } from '@/store';
import { openFetchSse } from './fetchSse';
import type { FetchSseConnection, FetchSseMessage } from './fetchSse';

const TERMINAL_STATUSES = new Set<AsyncTaskStatus>(['SUCCESS', 'FAILED', 'CANCELLED']);
const STANDARD_EVENT_NAMES = [
  'connected',
  'task-started',
  'task-stage',
  'task-progress',
  'task-cancelling',
  'task-finished',
  'task-failed',
  'task-cancelled',
] as const;
const TERMINAL_EVENT_NAMES = new Set<string>(['task-finished', 'task-failed', 'task-cancelled']);

export interface AsyncTaskEventCallbacks {
  onTask: (task: AsyncTask) => void;
  onTerminal: (task?: AsyncTask, eventName?: string) => void;
  onOpen?: (response: Response) => void | Promise<void>;
  onError?: (error: Error) => void | Promise<void>;
  /** 复用同一个通用任务 SSE 连接订阅业务增量事件，避免业务模块另建事件端点。 */
  extraEvents?: Record<string, (payload: unknown) => void>;
}

export interface AsyncTaskEventSubscription {
  close: () => void;
}

export function createAsyncTaskWatchGeneration(getActiveTaskId: () => string | undefined) {
  let generation = 0;
  return {
    begin(taskId: string) {
      const watchGeneration = ++generation;
      return () => watchGeneration === generation && getActiveTaskId() === taskId;
    },
    invalidate() {
      generation += 1;
    },
  };
}

export function isTerminalAsyncTask(status: AsyncTaskStatus) {
  return TERMINAL_STATUSES.has(status);
}

export function canDownloadAsyncTask(status: AsyncTaskStatus) {
  return status === 'SUCCESS';
}

export function canCancelAsyncTask(status: AsyncTaskStatus) {
  return status === 'CREATED' || status === 'RUNNING';
}

export function canDeleteAsyncTask(status: AsyncTaskStatus) {
  return isTerminalAsyncTask(status);
}

export function subscribeAsyncTaskEvents(
  taskId: string,
  callbacks: AsyncTaskEventCallbacks,
): AsyncTaskEventSubscription {
  let closed = false;
  let terminalNotified = false;
  let connection: FetchSseConnection | undefined;
  let closePending = false;

  const close = () => {
    if (closed) return;
    closed = true;
    if (connection) connection.close();
    else closePending = true;
  };

  const handleMessage = (message: FetchSseMessage) => {
    if (closed) return;

    let payload: unknown;
    let parsed = false;
    try {
      payload = JSON.parse(message.data);
      parsed = true;
    } catch {
      // A malformed event must not prevent terminal cleanup.
    }

    const eventTask = payload && typeof payload === 'object' ? (payload as { task?: AsyncTask }).task : undefined;
    const terminal =
      TERMINAL_EVENT_NAMES.has(message.event) || Boolean(eventTask && isTerminalAsyncTask(eventTask.status));
    const standardEvent = (STANDARD_EVENT_NAMES as readonly string[]).includes(message.event);

    if (!standardEvent && parsed) {
      const extraHandler = callbacks.extraEvents?.[message.event];
      if (extraHandler) {
        try {
          extraHandler(payload);
        } catch {
          // 单条业务增量事件损坏或处理失败时，仍继续处理同一消息中的任务终态。
        }
      }
    }

    try {
      if (eventTask) callbacks.onTask(eventTask);
    } finally {
      if (terminal && !terminalNotified) {
        terminalNotified = true;
        close();
        callbacks.onTerminal(eventTask, message.event);
      }
    }
  };

  connection = openFetchSse({
    url: asyncTaskEventsUrl(taskId),
    headers: () => {
      const authStore = useAuthStore();
      return getAuthenticationHeaders(authStore.getToken, authStore.authorizationName);
    },
    onOpen: callbacks.onOpen,
    onMessage: handleMessage,
    onError: (error) => callbacks.onError?.(error instanceof Error ? error : new Error(String(error))),
  });
  if (closePending) connection.close();

  return { close };
}
