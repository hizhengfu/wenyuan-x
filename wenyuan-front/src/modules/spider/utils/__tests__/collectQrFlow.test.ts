import { describe, expect, it, vi, afterEach } from 'vitest';
import { createCollectQrFlow } from '../collectQrFlow';
import type { CollectAuthorization } from '../../api/collect';

const waiting = (id = 'qr-1'): CollectAuthorization => ({ connectionId: 'connection-1', qr: { id, state: 'WAITING', image: 'data:image/png;base64,picture', expiresAt: 10000, message: '扫码' } });
const deferred = <T>() => { let resolve!: (value: T) => void; const promise = new Promise<T>(r => { resolve = r; }); return { promise, resolve }; };
afterEach(() => vi.useRealTimers());

describe('collection QR flow', () => {
  it('cancels a late start response after the modal was closed', async () => {
    const result = deferred<CollectAuthorization>();
    const api = { start: vi.fn(() => result.promise), poll: vi.fn(), cancel: vi.fn().mockResolvedValue(undefined) };
    const update = vi.fn(); const flow = createCollectQrFlow(api, update, vi.fn());
    const started = flow.start(); await vi.waitFor(() => expect(api.start).toHaveBeenCalledTimes(1)); await flow.close();
    result.resolve(waiting()); await started;
    expect(update).not.toHaveBeenCalled(); expect(api.cancel).toHaveBeenCalledWith('connection-1', 'qr-1');
  });

  it('does not accept stale polling after switching to a new QR', async () => {
    vi.useFakeTimers();
    const old = deferred<CollectAuthorization['qr']>();
    const api = { start: vi.fn().mockResolvedValueOnce(waiting()).mockResolvedValueOnce(waiting('qr-2')), poll: vi.fn(() => old.promise), cancel: vi.fn().mockResolvedValue(undefined) };
    const update = vi.fn(); const flow = createCollectQrFlow(api, update, vi.fn());
    await flow.start(); await vi.advanceTimersByTimeAsync(1000);
    await flow.start(); old.resolve({ ...waiting().qr, state: 'CONNECTED' }); await Promise.resolve();
    expect(update.mock.calls.map(call => call[0].qr.state)).toEqual(['WAITING', 'WAITING']);
    await flow.close();
  });

  it('stops polling on success without revoking the saved connection', async () => {
    vi.useFakeTimers();
    const api = { start: vi.fn().mockResolvedValue(waiting()), poll: vi.fn().mockResolvedValue({ ...waiting().qr, state: 'CONNECTED' }), cancel: vi.fn().mockResolvedValue(undefined) };
    const flow = createCollectQrFlow(api, vi.fn(), vi.fn());
    await flow.start(); await vi.advanceTimersByTimeAsync(10000); await flow.close();
    expect(api.poll).toHaveBeenCalledTimes(1); expect(api.cancel).not.toHaveBeenCalled();
  });

  it('cancels and reports a polling failure instead of spinning indefinitely', async () => {
    vi.useFakeTimers();
    const api = { start: vi.fn().mockResolvedValue(waiting()), poll: vi.fn().mockRejectedValue(new Error('授权窗口已失效')), cancel: vi.fn().mockResolvedValue(undefined) };
    const failed = vi.fn(); const flow = createCollectQrFlow(api, vi.fn(), failed);
    await flow.start(); await vi.advanceTimersByTimeAsync(10000);
    expect(failed).toHaveBeenCalledTimes(1); expect(api.poll).toHaveBeenCalledTimes(1); expect(api.cancel).toHaveBeenCalledTimes(1);
  });
});
