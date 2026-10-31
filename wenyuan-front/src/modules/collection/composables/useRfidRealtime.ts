import { ref } from 'vue';
import { useAuthStore } from '@/store';
import { getAuthenticationHeaders } from '@/utils/auth';
import type { RfidRealtimeEvent } from '../typings/rfid';

export type RfidRealtimeStatus = 'CLOSED' | 'CONNECTING' | 'OPEN' | 'RETRYING';

/** EventSource 不能携带 JWT 请求头，因此使用 fetch 流读取标准 SSE。 */
export function useRfidRealtime(onEvent: (event: RfidRealtimeEvent) => void | Promise<void>) {
  const authStore = useAuthStore();
  const status = ref<RfidRealtimeStatus>('CLOSED');
  let controller: AbortController | undefined;
  let generation = 0;

  function disconnect() {
    generation += 1;
    controller?.abort();
    controller = undefined;
    status.value = 'CLOSED';
  }

  function connect(workstationId: string, afterSequence = 0) {
    connectPath(`/collection/rfid/realtime/events?workstationId=${encodeURIComponent(workstationId)}`, afterSequence);
  }

  function connectScope(afterSequence = 0) {
    connectPath('/collection/rfid/realtime/scope-events', afterSequence);
  }

  function connectPath(path: string, afterSequence: number) {
    disconnect();
    const current = generation;
    controller = new AbortController();
    void consume(path, afterSequence, current, controller);
  }

  async function consume(path: string, initialSequence: number, current: number, abortController: AbortController) {
    let lastSequence = initialSequence;
    let retryMillis = 1_000;
    while (current === generation && !abortController.signal.aborted) {
      try {
        status.value = retryMillis === 1_000 ? 'CONNECTING' : 'RETRYING';
        const base = APP_BASE_URL.replace(/\/$/, '');
        const api = APP_API_PATH.replace(/\/$/, '');
        const response = await fetch(`${base}${api}${path}`, {
          method: 'GET',
          credentials: 'include',
          cache: 'no-store',
          headers: {
            Accept: 'text/event-stream',
            ...getAuthenticationHeaders(authStore.getToken, authStore.authorizationName),
            ...(lastSequence > 0 ? { 'Last-Event-ID': String(lastSequence) } : {}),
          },
          signal: abortController.signal,
        });
        if (!response.ok || !response.body) throw new Error(`SSE_HTTP_${response.status}`);
        status.value = 'OPEN';
        retryMillis = 1_000;
        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';
        while (current === generation && !abortController.signal.aborted) {
          const { value, done } = await reader.read();
          buffer += decoder.decode(value, { stream: !done }).replace(/\r\n/g, '\n');
          let boundary = buffer.indexOf('\n\n');
          while (boundary >= 0) {
            const block = buffer.slice(0, boundary);
            buffer = buffer.slice(boundary + 2);
            const parsed = parseEvent(block);
            if (parsed) {
              if (parsed.sequence > 0) lastSequence = Math.max(lastSequence, parsed.sequence);
              await onEvent(parsed);
            }
            boundary = buffer.indexOf('\n\n');
          }
          if (done) break;
        }
      } catch (error) {
        if (abortController.signal.aborted || current !== generation) return;
      }
      if (current !== generation || abortController.signal.aborted) return;
      status.value = 'RETRYING';
      await delay(retryMillis, abortController.signal);
      retryMillis = Math.min(retryMillis * 2, 15_000);
    }
  }

  return { status, connect, connectScope, disconnect };
}

function parseEvent(block: string): RfidRealtimeEvent | null {
  const data = block
    .split('\n')
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice(5).trimStart())
    .join('\n');
  if (!data) return null;
  try {
    return JSON.parse(data) as RfidRealtimeEvent;
  } catch {
    return null;
  }
}

function delay(milliseconds: number, signal: AbortSignal) {
  return new Promise<void>((resolve) => {
    const timer = window.setTimeout(resolve, milliseconds);
    signal.addEventListener(
      'abort',
      () => {
        window.clearTimeout(timer);
        resolve();
      },
      { once: true },
    );
  });
}
