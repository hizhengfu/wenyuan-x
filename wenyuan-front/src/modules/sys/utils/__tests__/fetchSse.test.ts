import { afterEach, describe, expect, it, vi } from 'vitest';
import { FetchSseHttpError, isPermanentFetchSseError, openFetchSse } from '../fetchSse';

const encoder = new TextEncoder();

function streamResponse(chunks: Array<string | Uint8Array>, status = 200) {
  return new Response(
    new ReadableStream<Uint8Array>({
      start(controller) {
        chunks.forEach((chunk) => controller.enqueue(typeof chunk === 'string' ? encoder.encode(chunk) : chunk));
        controller.close();
      },
    }),
    { status },
  );
}

function deferredResponse(status = 200, onCancel?: () => void) {
  let controller!: ReadableStreamDefaultController<Uint8Array>;
  const response = new Response(
    new ReadableStream<Uint8Array>({
      start(value) {
        controller = value;
      },
      cancel: onCancel,
    }),
    { status },
  );
  return { response, controller };
}

async function flush() {
  await Promise.resolve();
  await Promise.resolve();
  await Promise.resolve();
}

describe('openFetchSse', () => {
  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('parses split UTF-8, CR/LF boundaries, fields, comments and multiline data', async () => {
    const bytes = encoder.encode(
      ': ignore\r\nevent: update\rid: 42\rdata: first\r\ndata: 中文\r\rdata: default\r\rid: 99\r\rdata: id only\r\rid:\r\rdata: cleared\n\n',
    );
    const splitCrLf = bytes.indexOf(0x0d) + 1;
    const splitLoneCr = bytes.indexOf(0x0d, splitCrLf + 1) + 1;
    const splitInsideChinese = bytes.indexOf(0xe4) + 1;
    const chunks = [
      bytes.slice(0, splitCrLf),
      bytes.slice(splitCrLf, splitLoneCr),
      bytes.slice(splitLoneCr, splitInsideChinese),
      bytes.slice(splitInsideChinese),
    ];
    const messages: unknown[] = [];
    const errors: unknown[] = [];
    const { response, controller } = deferredResponse();
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response));

    const connection = openFetchSse({
      url: '/events',
      headers: () => ({ Authorization: 'Bearer token' }),
      onMessage: (message) => {
        messages.push(message);
      },
      onError: (error) => {
        errors.push(error);
      },
      retryDelayMs: 10_000,
    });
    chunks.forEach((chunk) => controller.enqueue(chunk));
    await vi.waitFor(() => expect(messages).toHaveLength(4));

    expect(messages).toEqual([
      { event: 'update', data: 'first\n中文', id: '42' },
      { event: 'message', data: 'default', id: '42' },
      { event: 'message', data: 'id only', id: '99' },
      { event: 'message', data: 'cleared' },
    ]);
    expect(errors).toEqual([]);
    expect(fetch).toHaveBeenCalledWith('/events', {
      method: 'GET',
      headers: { Authorization: 'Bearer token', Accept: 'text/event-stream' },
      credentials: 'include',
      cache: 'no-store',
      signal: expect.any(AbortSignal),
    });
    connection.close();
  });

  it.each([401, 403])('reports %s once and never reconnects', async (status) => {
    vi.useFakeTimers();
    const onError = vi.fn();
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status }));
    vi.stubGlobal('fetch', fetchMock);

    openFetchSse({ url: '/events', headers: () => ({}), onMessage: vi.fn(), onError, retryDelayMs: 5 });
    await flush();
    await vi.advanceTimersByTimeAsync(50);

    expect(onError).toHaveBeenCalledTimes(1);
    const error = onError.mock.calls[0][0];
    expect(error).toBeInstanceOf(FetchSseHttpError);
    expect(error).toMatchObject({ status, retryable: false });
    expect(isPermanentFetchSseError(error)).toBe(true);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('reconnects after a network failure and 5xx with fresh authorization headers', async () => {
    vi.useFakeTimers();
    let token = 'one';
    const headers = vi.fn(() => ({ Authorization: `Bearer ${token}` }));
    const onError = vi.fn();
    const pending = deferredResponse();
    const fetchMock = vi
      .fn()
      .mockRejectedValueOnce(new TypeError('offline'))
      .mockResolvedValueOnce(new Response(null, { status: 503 }))
      .mockResolvedValueOnce(pending.response);
    vi.stubGlobal('fetch', fetchMock);

    const connection = openFetchSse({
      url: '/events',
      headers,
      onMessage: vi.fn(),
      onError,
      retryDelayMs: 5,
    });
    await flush();
    token = 'two';
    await vi.advanceTimersByTimeAsync(5);
    token = 'three';
    await vi.advanceTimersByTimeAsync(5);

    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(headers).toHaveBeenCalledTimes(3);
    expect(fetchMock.mock.calls.map(([, init]) => (init as RequestInit).headers)).toEqual([
      { Authorization: 'Bearer one', Accept: 'text/event-stream' },
      { Authorization: 'Bearer two', Accept: 'text/event-stream' },
      { Authorization: 'Bearer three', Accept: 'text/event-stream' },
    ]);
    expect(onError).toHaveBeenCalledTimes(2);
    expect(isPermanentFetchSseError(onError.mock.calls[0][0])).toBe(false);
    expect(onError.mock.calls[1][0]).toMatchObject({ status: 503, retryable: true });
    expect(isPermanentFetchSseError(onError.mock.calls[1][0])).toBe(false);
    connection.close();
  });

  it('treats an unexpected EOF as retryable', async () => {
    vi.useFakeTimers();
    const pending = deferredResponse();
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(streamResponse(['id: event-7\ndata: before eof\n\n']))
      .mockResolvedValueOnce(pending.response);
    const onMessage = vi.fn();
    const onError = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const connection = openFetchSse({
      url: '/events',
      headers: () => ({}),
      onMessage,
      onError,
      retryDelayMs: 5,
    });
    await flush();
    await vi.advanceTimersByTimeAsync(5);

    expect(onMessage).toHaveBeenCalledWith({ event: 'message', data: 'before eof', id: 'event-7' });
    expect(onError).toHaveBeenCalledTimes(1);
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect((fetchMock.mock.calls[1][1] as RequestInit).headers).toEqual({
      Accept: 'text/event-stream',
      'Last-Event-ID': 'event-7',
    });
    connection.close();
  });

  it('uses a caller cursor initially, then advances to the server cursor regardless of header casing', async () => {
    vi.useFakeTimers();
    const pending = deferredResponse();
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(streamResponse(['id: server-id\ndata: first\n\n']))
      .mockResolvedValueOnce(pending.response);
    vi.stubGlobal('fetch', fetchMock);

    const connection = openFetchSse({
      url: '/events',
      headers: () => ({ 'last-event-id': 'caller-id' }),
      onMessage: vi.fn(),
      retryDelayMs: 5,
    });
    await flush();
    await vi.advanceTimersByTimeAsync(5);

    expect((fetchMock.mock.calls[0][1] as RequestInit).headers).toEqual({
      'last-event-id': 'caller-id',
      Accept: 'text/event-stream',
    });
    expect((fetchMock.mock.calls[1][1] as RequestInit).headers).toEqual({
      Accept: 'text/event-stream',
      'Last-Event-ID': 'server-id',
    });
    connection.close();
  });

  it('does not restore a caller cursor after an empty server id clears it', async () => {
    vi.useFakeTimers();
    const pending = deferredResponse();
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(streamResponse(['id:\ndata: cleared\n\n']))
      .mockResolvedValueOnce(pending.response);
    vi.stubGlobal('fetch', fetchMock);

    const connection = openFetchSse({
      url: '/events',
      headers: () => ({ 'LAST-EVENT-ID': 'caller-id' }),
      onMessage: vi.fn(),
      retryDelayMs: 5,
    });
    await flush();
    await vi.advanceTimersByTimeAsync(5);

    expect((fetchMock.mock.calls[0][1] as RequestInit).headers).toEqual({
      'LAST-EVENT-ID': 'caller-id',
      Accept: 'text/event-stream',
    });
    expect((fetchMock.mock.calls[1][1] as RequestInit).headers).toEqual({
      Accept: 'text/event-stream',
    });
    connection.close();
  });

  it.each([204, 400, 404])('reports %s once and permanently stops', async (status) => {
    vi.useFakeTimers();
    const onError = vi.fn();
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status }));
    vi.stubGlobal('fetch', fetchMock);

    openFetchSse({ url: '/events', headers: () => ({}), onMessage: vi.fn(), onError, retryDelayMs: 5 });
    await flush();
    await vi.advanceTimersByTimeAsync(50);

    expect(onError).toHaveBeenCalledTimes(1);
    const error = onError.mock.calls[0][0];
    expect(error).toBeInstanceOf(FetchSseHttpError);
    expect(error).toMatchObject({ status, retryable: false });
    expect(isPermanentFetchSseError(error)).toBe(true);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('retries 429 responses', async () => {
    vi.useFakeTimers();
    const pending = deferredResponse();
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(new Response(null, { status: 429 }))
      .mockResolvedValueOnce(pending.response);
    vi.stubGlobal('fetch', fetchMock);

    const connection = openFetchSse({
      url: '/events',
      headers: () => ({}),
      onMessage: vi.fn(),
      retryDelayMs: 5,
    });
    await flush();
    await vi.advanceTimersByTimeAsync(5);

    expect(fetchMock).toHaveBeenCalledTimes(2);
    connection.close();
  });

  it('isolates exceptions from onOpen, onMessage and onError without adding connections', async () => {
    const { response, controller } = deferredResponse();
    const fetchMock = vi.fn().mockResolvedValue(response);
    const onOpen = vi.fn(() => {
      throw new Error('open callback failed');
    });
    const onMessage = vi.fn(() => {
      throw new Error('message callback failed');
    });
    const onError = vi.fn(() => {
      throw new Error('error callback failed');
    });
    vi.stubGlobal('fetch', fetchMock);

    const connection = openFetchSse({
      url: '/events',
      headers: () => ({}),
      onOpen,
      onMessage,
      onError,
      retryDelayMs: 10_000,
    });
    controller.enqueue(encoder.encode('data: one\n\ndata: two\n\n'));
    await vi.waitFor(() => expect(onMessage).toHaveBeenCalledTimes(2));

    expect(onOpen).toHaveBeenCalledTimes(1);
    expect(onError).toHaveBeenCalledTimes(3);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    connection.close();
  });

  it('handles rejected callback promises without unhandled rejections', async () => {
    const { response, controller } = deferredResponse();
    const fetchMock = vi.fn().mockResolvedValue(response);
    const onOpen = vi.fn(async () => {
      throw new Error('async open callback failed');
    });
    const onMessage = vi.fn(async () => {
      throw new Error('async message callback failed');
    });
    const onError = vi.fn(async () => {
      throw new Error('async error callback failed');
    });
    vi.stubGlobal('fetch', fetchMock);

    const connection = openFetchSse({
      url: '/events',
      headers: () => ({}),
      onOpen,
      onMessage,
      onError,
      retryDelayMs: 10_000,
    });
    controller.enqueue(encoder.encode('data: one\n\n'));
    await vi.waitFor(() => expect(onError).toHaveBeenCalledTimes(2));

    expect(onMessage).toHaveBeenCalledTimes(1);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    connection.close();
  });

  it('close is idempotent, aborts active fetch and suppresses callbacks and retries', async () => {
    vi.useFakeTimers();
    let signal!: AbortSignal;
    const fetchMock = vi.fn((_url: string, init?: RequestInit) => {
      signal = init?.signal as AbortSignal;
      return new Promise<Response>((_resolve, reject) => {
        signal.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')));
      });
    });
    const onError = vi.fn();
    const onMessage = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const connection = openFetchSse({ url: '/events', headers: () => ({}), onMessage, onError, retryDelayMs: 5 });
    await flush();
    connection.close();
    connection.close();
    await flush();
    await vi.advanceTimersByTimeAsync(50);

    expect(signal.aborted).toBe(true);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(onError).not.toHaveBeenCalled();
    expect(onMessage).not.toHaveBeenCalled();
  });

  it('close releases a pending retry wait without starting another request', async () => {
    vi.useFakeTimers();
    const fetchMock = vi.fn().mockRejectedValue(new TypeError('offline'));
    const onError = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const connection = openFetchSse({
      url: '/events',
      headers: () => ({}),
      onMessage: vi.fn(),
      onError,
      retryDelayMs: 5_000,
    });
    await flush();
    expect(onError).toHaveBeenCalledTimes(1);

    connection.close();
    await flush();
    await vi.advanceTimersByTimeAsync(10_000);

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(vi.getTimerCount()).toBe(0);
  });

  it('close cancels and releases an established response stream', async () => {
    vi.useFakeTimers();
    const cancel = vi.fn();
    const pending = deferredResponse(200, cancel);
    const onOpen = vi.fn();
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(pending.response));

    const connection = openFetchSse({ url: '/events', headers: () => ({}), onOpen, onMessage: vi.fn() });
    await vi.waitFor(() => expect(onOpen).toHaveBeenCalledTimes(1));
    connection.close();
    await flush();

    expect(cancel).toHaveBeenCalledTimes(1);
    expect(vi.getTimerCount()).toBe(0);
  });
});
