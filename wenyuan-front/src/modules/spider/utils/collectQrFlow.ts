import type { CollectAuthorization, CollectQr } from '../api/collect';

interface QrApi {
  start(connectionId?: string): Promise<CollectAuthorization>;
  poll(connectionId: string, id: string): Promise<CollectQr>;
  cancel(connectionId: string, id: string): Promise<unknown>;
}

/** 关闭窗口、离开页面、切换连接后，迟到的请求不会重新打开二维码或覆盖新授权。 */
export function createCollectQrFlow(api: QrApi, update: (value: CollectAuthorization) => void, failed: (error: unknown) => void) {
  let generation = 0;
  let active: CollectAuthorization | undefined;
  let timer: ReturnType<typeof setTimeout> | undefined;
  const cancel = (value?: CollectAuthorization) => value ? api.cancel(value.connectionId, value.qr.id).catch(() => undefined) : Promise.resolve();

  async function close() {
    ++generation;
    clearTimeout(timer);
    const old = active;
    active = undefined;
    await cancel(old);
  }

  async function start(connectionId?: string) {
    const closing = close();
    const current = generation;
    await closing;
    if (current !== generation) return;
    try {
      const result = await api.start(connectionId);
      if (current !== generation) { await cancel(result); return; }
      active = result;
      update(result);
      const poll = async () => {
        if (current !== generation || !active) return;
        const previous = active;
        try {
          const qr = await api.poll(previous.connectionId, previous.qr.id);
          if (current !== generation) return;
          active = { ...previous, qr };
          update(active);
          if (qr.state === 'STARTING' || qr.state === 'WAITING') timer = setTimeout(poll, 2000);
          else if (qr.state === 'CONNECTED') active = undefined;
        } catch (error) { if (current === generation) { await close(); failed(error); } }
      };
      timer = setTimeout(poll, 1000);
    } catch (error) { if (current === generation) failed(error); }
  }
  return { start, close };
}
