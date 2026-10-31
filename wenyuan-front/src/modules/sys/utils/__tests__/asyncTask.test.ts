import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { AsyncTask, AsyncTaskStatus } from '../../api/asyncTask';
import type { FetchSseOptions } from '../fetchSse';

const { asyncTaskEventsUrlMock, openFetchSseMock, useAuthStoreMock } = vi.hoisted(() => ({
  asyncTaskEventsUrlMock: vi.fn((id: string) => `/api/sys/async-task/${id}/events`),
  openFetchSseMock: vi.fn(),
  useAuthStoreMock: vi.fn(),
}));

vi.mock('../../api/asyncTask', () => ({ asyncTaskEventsUrl: asyncTaskEventsUrlMock }));
vi.mock('../fetchSse', () => ({ openFetchSse: openFetchSseMock }));
vi.mock('@/store', () => ({ useAuthStore: useAuthStoreMock }));

import {
  canCancelAsyncTask,
  canDeleteAsyncTask,
  canDownloadAsyncTask,
  createAsyncTaskWatchGeneration,
  isTerminalAsyncTask,
  subscribeAsyncTaskEvents,
} from '../asyncTask';

describe('async task watch generation', () => {
  it('allows only the latest generation for the active task to apply updates', () => {
    let activeTaskId: string | undefined = 'task-a';
    let applied = '';
    const generation = createAsyncTaskWatchGeneration(() => activeTaskId);
    const isCurrentA = generation.begin('task-a');
    const apply = (isCurrent: () => boolean, value: string) => {
      if (isCurrent()) applied = value;
    };

    apply(isCurrentA, 'a-before-switch');
    expect(applied).toBe('a-before-switch');

    const isCurrentB = generation.begin('task-b');
    activeTaskId = 'task-b';
    apply(isCurrentA, 'stale-a');
    apply(isCurrentB, 'current-b');
    expect(applied).toBe('current-b');

    activeTaskId = 'task-c';
    apply(isCurrentB, 'wrong-active-task');
    expect(applied).toBe('current-b');

    generation.invalidate();
    activeTaskId = 'task-b';
    apply(isCurrentB, 'invalidated-b');
    expect(applied).toBe('current-b');
  });
});

function task(status: AsyncTaskStatus): AsyncTask {
  return { id: 'task-1', status } as AsyncTask;
}

function connectionHarness(onOpen?: (options: FetchSseOptions) => void) {
  let options!: FetchSseOptions;
  const close = vi.fn();
  openFetchSseMock.mockImplementationOnce((value: FetchSseOptions) => {
    options = value;
    onOpen?.(value);
    return { close };
  });
  return {
    close,
    options: () => options,
    emit: (event: string, data: string) => options.onMessage({ event, data }),
  };
}

describe('async task UI policy', () => {
  it.each([
    ['CREATED', false, false, true, false],
    ['RUNNING', false, false, true, false],
    ['CANCELLING', false, false, false, false],
    ['SUCCESS', true, true, false, true],
    ['FAILED', true, false, false, true],
    ['CANCELLED', true, false, false, true],
  ] satisfies Array<[AsyncTaskStatus, boolean, boolean, boolean, boolean]>)(
    'applies permissions for %s',
    (status, terminal, downloadable, cancellable, deletable) => {
      expect(isTerminalAsyncTask(status)).toBe(terminal);
      expect(canDownloadAsyncTask(status)).toBe(downloadable);
      expect(canCancelAsyncTask(status)).toBe(cancellable);
      expect(canDeleteAsyncTask(status)).toBe(deletable);
    },
  );
});

describe('async task event subscription', () => {
  beforeEach(() => {
    asyncTaskEventsUrlMock.mockClear();
    openFetchSseMock.mockReset();
    useAuthStoreMock.mockReset();
    useAuthStoreMock.mockReturnValue({ authorizationName: 'Authorization', getToken: 'Bearer token-1' });
  });

  it('opens one authenticated fetch SSE connection and refreshes auth headers for reconnects', () => {
    const harness = connectionHarness();
    const onOpen = vi.fn(async (_response: Response) => undefined);
    const onError = vi.fn(async (_error: Error) => undefined);
    const subscription = subscribeAsyncTaskEvents('task-1', {
      onTask: vi.fn(),
      onTerminal: vi.fn(),
      onOpen,
      onError,
    });

    expect(asyncTaskEventsUrlMock).toHaveBeenCalledWith('task-1');
    expect(openFetchSseMock).toHaveBeenCalledTimes(1);
    expect(harness.options().url).toBe('/api/sys/async-task/task-1/events');
    expect(harness.options().headers()).toEqual({ Authorization: 'Bearer token-1', 'X-Web-Auth': 'jwt2' });
    useAuthStoreMock.mockReturnValue({ authorizationName: 'X-Authorization', getToken: 'Bearer token-2' });
    expect(harness.options().headers()).toEqual({ 'X-Authorization': 'Bearer token-2', 'X-Web-Auth': 'jwt2' });
    useAuthStoreMock.mockReturnValue({ authorizationName: 'Authorization', getToken: null });
    expect(harness.options().headers()).toEqual({ 'X-Web-Auth': 'jwt2' });
    expect(useAuthStoreMock).toHaveBeenCalledTimes(3);

    const response = new Response();
    const error = new Error('offline');
    harness.options().onOpen?.(response);
    harness.options().onError?.(error);
    expect(onOpen).toHaveBeenCalledWith(response);
    expect(onError).toHaveBeenCalledWith(error);
    expect(subscription).toEqual({ close: expect.any(Function) });
  });

  it('dispatches standard and extra events on the same connection and ignores malformed extra JSON', () => {
    const harness = connectionHarness();
    const onTask = vi.fn();
    const onFileFound = vi.fn();
    const onDuplicateStandard = vi.fn();
    subscribeAsyncTaskEvents('task-1', {
      onTask,
      onTerminal: vi.fn(),
      extraEvents: {
        'resource-file-found': onFileFound,
        'task-cancelling': onDuplicateStandard,
      },
    });

    harness.emit('task-cancelling', JSON.stringify({ task: task('CANCELLING') }));
    harness.emit('resource-file-found', JSON.stringify({ item: { id: 'item-1' } }));
    harness.emit('resource-file-found', '{invalid-json');

    expect(onTask).toHaveBeenCalledWith(expect.objectContaining({ status: 'CANCELLING' }));
    expect(onFileFound).toHaveBeenCalledTimes(1);
    expect(onFileFound).toHaveBeenCalledWith({ item: { id: 'item-1' } });
    expect(onDuplicateStandard).not.toHaveBeenCalled();
    expect(openFetchSseMock).toHaveBeenCalledTimes(1);
  });

  it('delivers an extra payload before closing on its terminal task snapshot', () => {
    const harness = connectionHarness();
    const calls: string[] = [];
    const onTask = vi.fn(() => calls.push('task'));
    const onTerminal = vi.fn(() => calls.push('terminal'));
    const onImported = vi.fn(() => {
      calls.push('extra');
      throw new Error('item handler failed');
    });
    subscribeAsyncTaskEvents('task-1', {
      onTask,
      onTerminal,
      extraEvents: { 'resource-file-imported': onImported },
    });
    const terminalTask = task('SUCCESS');
    const payload = { item: { id: 'item-1' }, task: terminalTask };

    harness.emit('resource-file-imported', JSON.stringify(payload));
    harness.emit('resource-file-imported', JSON.stringify(payload));

    expect(onImported).toHaveBeenCalledTimes(1);
    expect(onImported).toHaveBeenCalledWith(payload);
    expect(onTask).toHaveBeenCalledTimes(1);
    expect(onTask).toHaveBeenCalledWith(terminalTask);
    expect(harness.close).toHaveBeenCalledTimes(1);
    expect(onTerminal).toHaveBeenCalledTimes(1);
    expect(onTerminal).toHaveBeenCalledWith(terminalTask, 'resource-file-imported');
    expect(calls).toEqual(['extra', 'task', 'terminal']);
  });

  it('closes on a terminal task snapshot from an unknown event and ignores later messages', () => {
    const harness = connectionHarness();
    const onTask = vi.fn();
    const onTerminal = vi.fn();
    subscribeAsyncTaskEvents('task-1', { onTask, onTerminal });
    const terminalTask = task('FAILED');

    harness.emit('server-snapshot', JSON.stringify({ task: terminalTask }));
    harness.emit('task-progress', JSON.stringify({ task: task('RUNNING') }));

    expect(onTask).toHaveBeenCalledTimes(1);
    expect(onTask).toHaveBeenCalledWith(terminalTask);
    expect(harness.close).toHaveBeenCalledTimes(1);
    expect(onTerminal).toHaveBeenCalledTimes(1);
    expect(onTerminal).toHaveBeenCalledWith(terminalTask, 'server-snapshot');
  });

  it.each(['SUCCESS', 'FAILED', 'CANCELLED'] satisfies AsyncTaskStatus[])(
    'treats a terminal %s task from connected as terminal and ignores later events',
    (status) => {
      const harness = connectionHarness();
      const onTask = vi.fn();
      const onTerminal = vi.fn();
      subscribeAsyncTaskEvents('task-1', { onTask, onTerminal });
      const terminalTask = task(status);

      harness.emit('connected', JSON.stringify({ task: terminalTask }));
      harness.emit('task-progress', JSON.stringify({ task: task('RUNNING') }));
      harness.emit('task-finished', JSON.stringify({ task: terminalTask }));

      expect(onTask).toHaveBeenCalledTimes(1);
      expect(onTask).toHaveBeenCalledWith(terminalTask);
      expect(harness.close).toHaveBeenCalledTimes(1);
      expect(onTerminal).toHaveBeenCalledTimes(1);
      expect(onTerminal).toHaveBeenCalledWith(terminalTask, 'connected');
    },
  );

  it.each(['task-finished', 'task-failed', 'task-cancelled'])(
    'closes and invokes the terminal callback once for %s',
    (eventName) => {
      const harness = connectionHarness();
      const onTask = vi.fn();
      const onTerminal = vi.fn();
      const subscription = subscribeAsyncTaskEvents('task-1', { onTask, onTerminal });

      harness.emit(eventName, JSON.stringify({ task: task('SUCCESS') }));
      harness.emit(eventName, JSON.stringify({ task: task('SUCCESS') }));
      subscription.close();
      subscription.close();

      expect(onTask).toHaveBeenCalledTimes(1);
      expect(harness.close).toHaveBeenCalledTimes(1);
      expect(onTerminal).toHaveBeenCalledTimes(1);
      expect(onTerminal).toHaveBeenCalledWith(expect.objectContaining({ status: 'SUCCESS' }), eventName);
    },
  );

  it.each(['task-finished', 'task-failed', 'task-cancelled'])(
    'closes and invokes the terminal callback for invalid JSON from %s',
    (eventName) => {
      const harness = connectionHarness();
      const onTerminal = vi.fn();
      subscribeAsyncTaskEvents('task-1', { onTask: vi.fn(), onTerminal });

      harness.emit(eventName, '{invalid-json');

      expect(harness.close).toHaveBeenCalledTimes(1);
      expect(onTerminal).toHaveBeenCalledTimes(1);
      expect(onTerminal).toHaveBeenCalledWith(undefined, eventName);
    },
  );

  it('handles a synchronous terminal message before openFetchSse returns', () => {
    const terminalTask = task('SUCCESS');
    const onTask = vi.fn();
    const onTerminal = vi.fn();
    const harness = connectionHarness((options) => {
      void options.onMessage({ event: 'task-finished', data: JSON.stringify({ task: terminalTask }) });
    });

    const subscription = subscribeAsyncTaskEvents('task-1', { onTask, onTerminal });
    subscription.close();

    expect(onTask).toHaveBeenCalledWith(terminalTask);
    expect(onTerminal).toHaveBeenCalledWith(terminalTask, 'task-finished');
    expect(harness.close).toHaveBeenCalledTimes(1);
  });
});
