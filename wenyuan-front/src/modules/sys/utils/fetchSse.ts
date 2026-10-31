export interface FetchSseMessage {
  event: string;
  data: string;
  id?: string;
}

export interface FetchSseOptions {
  url: string;
  headers: () => Record<string, string>;
  onOpen?: (response: Response) => void | Promise<void>;
  onMessage: (message: FetchSseMessage) => void | Promise<void>;
  onError?: (error: unknown) => void | Promise<void>;
  retryDelayMs?: number;
}

export interface FetchSseConnection {
  close: () => void;
}

export class FetchSseHttpError extends Error {
  constructor(
    readonly status: number,
    readonly retryable: boolean,
  ) {
    super(`SSE request failed with status ${status}`);
    this.name = 'FetchSseHttpError';
  }
}

export function isPermanentFetchSseError(error: unknown): error is FetchSseHttpError {
  return error instanceof FetchSseHttpError && !error.retryable;
}

class SseParser {
  private buffer = '';
  private data: string[] = [];
  private event = '';

  constructor(
    private readonly emit: (message: FetchSseMessage) => void,
    private lastEventId: string,
    private readonly updateLastEventId: (id: string) => void,
  ) {}

  push(value: string, final = false) {
    this.buffer += value;
    let lineStart = 0;
    let index = 0;

    while (index < this.buffer.length) {
      const character = this.buffer[index];
      if (character !== '\n' && character !== '\r') {
        index += 1;
        continue;
      }

      // A CR at a chunk boundary may be the first half of CRLF. Keep it until
      // the next decoded chunk arrives so CRLF is consumed as one delimiter.
      if (character === '\r' && index + 1 === this.buffer.length && !final) break;

      this.processLine(this.buffer.slice(lineStart, index));
      index += character === '\r' && this.buffer[index + 1] === '\n' ? 2 : 1;
      lineStart = index;
    }

    this.buffer = this.buffer.slice(lineStart);
    if (final && this.buffer) {
      this.processLine(this.buffer);
      this.buffer = '';
    }
  }

  private processLine(line: string) {
    if (!line) {
      this.dispatch();
      return;
    }
    if (line.startsWith(':')) return;

    const colon = line.indexOf(':');
    const field = colon < 0 ? line : line.slice(0, colon);
    let value = colon < 0 ? '' : line.slice(colon + 1);
    if (value.startsWith(' ')) value = value.slice(1);

    if (field === 'data') this.data.push(value);
    else if (field === 'event') this.event = value;
    else if (field === 'id' && !value.includes('\0')) {
      this.lastEventId = value;
      this.updateLastEventId(value);
    }
  }

  private dispatch() {
    if (this.data.length > 0) {
      this.emit({
        event: this.event || 'message',
        data: this.data.join('\n'),
        ...(this.lastEventId ? { id: this.lastEventId } : {}),
      });
    }
    this.data = [];
    this.event = '';
  }
}

function isPermanentResponse(status: number) {
  return status === 204 || (status >= 400 && status < 500 && status !== 408 && status !== 429);
}

function mergeRequestHeaders(headers: Record<string, string>, lastEventId: string, hasServerLastEventId: boolean) {
  const merged = { ...headers };
  if (hasServerLastEventId) {
    Object.keys(merged).forEach((name) => {
      if (name.toLowerCase() === 'last-event-id') delete merged[name];
    });
    if (lastEventId) merged['Last-Event-ID'] = lastEventId;
  }
  merged.Accept = 'text/event-stream';
  return merged;
}

export function openFetchSse(options: FetchSseOptions): FetchSseConnection {
  let closed = false;
  let activeController: AbortController | undefined;
  let activeReader: ReadableStreamDefaultReader<Uint8Array> | undefined;
  let retryTimer: ReturnType<typeof setTimeout> | undefined;
  let releaseRetry: (() => void) | undefined;
  let lastEventId = '';
  let hasServerLastEventId = false;
  let consecutiveFailures = 0;

  const notifyError = (error: unknown) => {
    if (closed) return;
    try {
      const result = options.onError?.(error);
      void Promise.resolve(result).catch(() => undefined);
    } catch {
      // Consumer callbacks must not break transport cleanup or create an
      // unhandled rejection from the detached connection loop.
    }
  };

  const retryDelay = () => {
    if (options.retryDelayMs !== undefined) return Math.max(0, options.retryDelayMs);
    const exponent = Math.max(0, Math.min(consecutiveFailures - 1, 5));
    const exponential = Math.min(30_000, 1_000 * 2 ** exponent);
    return Math.round(exponential * (0.8 + Math.random() * 0.4));
  };

  const waitToRetry = (delayMs: number) =>
    new Promise<void>((resolve) => {
      if (closed) {
        resolve();
        return;
      }
      releaseRetry = resolve;
      retryTimer = setTimeout(() => {
        retryTimer = undefined;
        releaseRetry = undefined;
        resolve();
      }, delayMs);
    });

  const close = () => {
    if (closed) return;
    closed = true;
    activeController?.abort();
    activeController = undefined;
    try {
      void activeReader?.cancel().catch(() => undefined);
    } catch {
      // Keep close idempotent even with a non-standard stream implementation.
    }
    if (retryTimer !== undefined) clearTimeout(retryTimer);
    retryTimer = undefined;
    releaseRetry?.();
    releaseRetry = undefined;
  };

  const connect = async () => {
    while (!closed) {
      const controller = new AbortController();
      activeController = controller;

      try {
        const response = await fetch(options.url, {
          method: 'GET',
          headers: mergeRequestHeaders(options.headers(), lastEventId, hasServerLastEventId),
          credentials: 'include',
          cache: 'no-store',
          signal: controller.signal,
        });
        if (closed) return;

        if (isPermanentResponse(response.status)) {
          notifyError(new FetchSseHttpError(response.status, false));
          await response.body?.cancel().catch(() => undefined);
          close();
          return;
        }
        if (!response.ok) {
          await response.body?.cancel().catch(() => undefined);
          throw new FetchSseHttpError(response.status, true);
        }
        if (!response.body) throw new Error('SSE response has no readable body');

        const decoder = new TextDecoder();
        const parser = new SseParser(
          (message) => {
            if (closed) return;
            try {
              const result = options.onMessage(message);
              void Promise.resolve(result).catch(notifyError);
            } catch (error) {
              notifyError(error);
            }
          },
          lastEventId,
          (id) => {
            lastEventId = id;
            hasServerLastEventId = true;
          },
        );
        const reader = response.body.getReader();
        activeReader = reader;
        try {
          try {
            await options.onOpen?.(response);
          } catch (error) {
            notifyError(error);
          }
          if (closed) return;
          consecutiveFailures = 0;

          while (!closed) {
            const { done, value } = await reader.read();
            if (done) {
              if (!closed) parser.push(decoder.decode(), true);
              break;
            }
            parser.push(decoder.decode(value, { stream: true }));
          }
        } finally {
          await reader.cancel().catch(() => undefined);
          if (activeReader === reader) activeReader = undefined;
          try {
            reader.releaseLock();
          } catch {
            // A reader may already be released by an unusual stream implementation.
          }
        }
        if (closed) return;
        throw new Error('SSE connection closed unexpectedly');
      } catch (error) {
        if (closed || controller.signal.aborted) return;
        notifyError(error);
        consecutiveFailures += 1;
      } finally {
        if (activeController === controller) activeController = undefined;
      }

      await waitToRetry(retryDelay());
    }
  };

  void connect().catch(notifyError);
  return { close };
}
