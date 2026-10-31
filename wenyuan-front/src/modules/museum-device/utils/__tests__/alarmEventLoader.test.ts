import { afterEach, describe, expect, it, vi } from 'vitest';
import { createAlarmEventLoader } from '../alarmEventLoader';

function deferred<T>() {
  let resolve!: (value: T) => void, reject!: (reason: unknown) => void;
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}

describe('alarm list serial requests', () => {
  afterEach(() => { vi.clearAllTimers(); vi.useRealTimers(); });

  it('does not send another request while eleven second polling is still waiting', async () => {
    vi.useFakeTimers();
    const first = deferred<string>();
    const request = vi.fn().mockReturnValueOnce(first.promise).mockResolvedValue('next');
    const load = createAlarmEventLoader(request);
    const params = { lifecycleStatus: 'OPEN', pageNum: 1, pageSize: 100 };
    const initial = load(params);
    const timer = setInterval(() => { void load(params); }, 11_000);
    await vi.advanceTimersByTimeAsync(55_000);
    expect(request).toHaveBeenCalledTimes(1);
    first.resolve('initial');
    await expect(initial).resolves.toBe('initial');
    await vi.advanceTimersByTimeAsync(11_000);
    expect(request).toHaveBeenCalledTimes(2);
    clearInterval(timer);
  });

  it('waits for completion and sends only the newest filter or page change', async () => {
    const first = deferred<string>(), second = deferred<string>();
    const request = vi.fn().mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise);
    const load = createAlarmEventLoader(request);
    const a = load({ pageNum: 1 });
    const b = load({ pageNum: 2 });
    const c = load({ pageNum: 3, lifecycleStatus: 'CLOSED' });
    await Promise.resolve();
    expect(request).toHaveBeenCalledTimes(1);
    first.resolve('old');
    await a;
    await Promise.resolve();
    expect(request.mock.calls.map(([params]) => params)).toEqual([{ pageNum: 1 }, { pageNum: 3, lifecycleStatus: 'CLOSED' }]);
    second.resolve('newest');
    await expect(b).resolves.toBe('newest');
    await expect(c).resolves.toBe('newest');
  });

  it('releases its in-flight guard after a failed request and allows a retry', async () => {
    const first = deferred<string>();
    const request = vi.fn().mockReturnValueOnce(first.promise).mockResolvedValue('recovered');
    const load = createAlarmEventLoader(request);
    const failed = load({ pageNum: 1 });
    const rejection = expect(failed).rejects.toThrow('timeout');
    const next = load({ pageNum: 2 });
    first.reject(new Error('timeout'));
    await rejection;
    await expect(next).resolves.toBe('recovered');
    expect(request).toHaveBeenCalledTimes(2);
    await expect(load({ pageNum: 2 })).resolves.toBe('recovered');
  });

  it('cancels queued work on page leave without resetting the still-running request', async () => {
    const first = deferred<string>();
    const request = vi.fn().mockReturnValueOnce(first.promise).mockResolvedValue('resumed');
    const load = createAlarmEventLoader(request);
    const active = load({ pageNum: 1 });
    const queued = load({ pageNum: 2 });
    const rejection = expect(queued).rejects.toThrow('取消待执行查询');
    load.cancelPending();
    await rejection;
    // KeepAlive 返回时，也必须等原在途请求完成。
    const resumed = load({ pageNum: 3 });
    await Promise.resolve();
    expect(request).toHaveBeenCalledTimes(1);
    first.resolve('old');
    await active;
    await expect(resumed).resolves.toBe('resumed');
    expect(request.mock.calls.map(([params]) => params.pageNum)).toEqual([1, 3]);
  });

  it('snapshots query params and does not block independent alarm page instances', async () => {
    const first = deferred<string>();
    const request = vi.fn().mockReturnValue(first.promise);
    const load = createAlarmEventLoader(request);
    const params = { pageNum: 1 };
    const promise = load(params);
    params.pageNum = 2;
    const other = createAlarmEventLoader(request)({ pageNum: 3 });
    await Promise.resolve();
    expect(request.mock.calls.map(([value]) => value.pageNum)).toEqual([1, 3]);
    first.resolve('done');
    await Promise.all([promise, other]);
  });
});
