import { afterEach, describe, expect, it, vi } from 'vitest';
import { createScreenRefreshScheduler } from '../refreshScheduler';

afterEach(() => {
  vi.useRealTimers();
});

describe('screen refresh scheduler', () => {
  it('runs tasks with their configured interval', async () => {
    vi.useFakeTimers();
    const scheduler = createScreenRefreshScheduler();
    const task = vi.fn();
    scheduler.register({ key: 'realtime', interval: 5_000, task });

    scheduler.start();
    await vi.advanceTimersByTimeAsync(4_999);
    expect(task).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(1);
    expect(task).toHaveBeenCalledTimes(1);
    scheduler.dispose();
  });

  it('waits until an asynchronous task completes before scheduling the next run', async () => {
    vi.useFakeTimers();
    const scheduler = createScreenRefreshScheduler();
    let finish: (() => void) | undefined;
    const task = vi.fn(() => new Promise<void>((resolve) => { finish = resolve; }));
    scheduler.register({ key: 'alarm', interval: 5_000, task });

    scheduler.start();
    await vi.advanceTimersByTimeAsync(5_000);
    expect(task).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(10_000);
    expect(task).toHaveBeenCalledTimes(1);

    finish?.();
    await Promise.resolve();
    await vi.advanceTimersByTimeAsync(4_999);
    expect(task).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(1);
    expect(task).toHaveBeenCalledTimes(2);
    scheduler.dispose();
  });

  it('pauses all registered tasks and refreshes once after resuming', async () => {
    vi.useFakeTimers();
    const scheduler = createScreenRefreshScheduler();
    const task = vi.fn();
    const editing = Symbol('editing');
    scheduler.register({ key: 'realtime', interval: 5_000, task });

    scheduler.start();
    await vi.advanceTimersByTimeAsync(2_000);
    scheduler.pause(editing);
    await vi.advanceTimersByTimeAsync(20_000);
    expect(task).not.toHaveBeenCalled();

    scheduler.resume(editing);
    await vi.advanceTimersByTimeAsync(0);
    expect(task).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(5_000);
    expect(task).toHaveBeenCalledTimes(2);
    scheduler.dispose();
  });
});
