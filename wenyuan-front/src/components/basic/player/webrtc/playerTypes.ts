import request from '@/utils/request';
import { streamFailureParams, type StreamFailureReport } from './webrtcFailure';

export type StreamPreference = 'auto' | 'L0' | 'L1';
export type PlaybackProfile = 'ORIGINAL' | 'H264';

export interface StreamStatusReport {
  cameraId: string;
  requestedStreamType: StreamPreference;
  actualStreamType: 'L0' | 'L1' | 'STATIC' | null;
  fallbackUsed: boolean;
}

export interface GridStreamStatusReport extends StreamStatusReport {
  index: number;
}

export interface PlayerRequestContext {
  signal?: AbortSignal;
  /** 宫格调度提示：只影响尚未开始的 /play 请求，不抢占已经执行的请求。 */
  priority?: 'normal' | 'interactive';
  /** @internal 限流器在真正进入 provider 前上报排队时间；自定义 provider 可选支持。 */
  onQueueTiming?: (queueWaitMs: number) => void;
}

export interface GridVideoItemInput {
  id?: unknown;
  code?: unknown;
  name?: unknown;
  label?: unknown;
  [key: string]: unknown;
}

export interface GridVideoItem {
  id: string;
  name?: string;
  [key: string]: unknown;
}

export interface WebrtcGridLayout {
  value: string;
  cols: number;
  rows: number;
  total: number;
  onePlusFive: boolean;
}

export interface WebrtcPlayerExpose {
  startPlay(): void;
  stopPlay(): void;
  suspendPlay(): void;
  resumePlay(): void;
  reconnectNow(): void;
  setStreamType(value: unknown): void;
  switchPlaybackMode(value: 'auto' | 'webrtc' | 'mse'): boolean | undefined;
  getPlaybackDiagnostics(): unknown;
  copyPlaybackDiagnostics(): Promise<string> | undefined;
}

export const MAX_WEBRTC_GRID_SLOTS = 36;

const DEFAULT_WEBRTC_GRID_LAYOUT: WebrtcGridLayout = {
  value: '2x2',
  cols: 2,
  rows: 2,
  total: 4,
  onePlusFive: false,
};

/**
 * 只接受正整数矩形宫格以及标准 1+5 六画面布局。非法或过大的布局回退为 2x2，
 * 防止错误配置一次性挂载大量实时播放器。
 */
export function normalizeWebrtcGridLayout(value: unknown): WebrtcGridLayout {
  const split = typeof value === 'string' ? value.trim().toLowerCase() : '';
  if (split === '1+5') {
    return { value: split, cols: 3, rows: 3, total: 6, onePlusFive: true };
  }

  const matched = /^([1-9]\d*)x([1-9]\d*)$/.exec(split);
  if (!matched) return { ...DEFAULT_WEBRTC_GRID_LAYOUT };

  const cols = Number(matched[1]);
  const rows = Number(matched[2]);
  const total = cols * rows;
  if (!Number.isSafeInteger(total) || total > MAX_WEBRTC_GRID_SLOTS) {
    return { ...DEFAULT_WEBRTC_GRID_LAYOUT };
  }

  return { value: `${cols}x${rows}`, cols, rows, total, onePlusFive: false };
}

function normalizeText(value: unknown): string {
  return typeof value === 'string' || typeof value === 'number' ? String(value) : '';
}

export function normalizeGridVideoItem(item: string | GridVideoItemInput): GridVideoItem {
  if (typeof item === 'string') return { id: item };
  const id = normalizeText(item?.id ?? item?.code);
  const name = normalizeText(item?.name ?? item?.label);
  const { id: _rawId, name: _rawName, ...metadata } = item ?? {};
  return name ? { ...metadata, id, name } : { ...metadata, id };
}

export function serializeGridVideoItems(items: readonly GridVideoItem[]): string {
  return JSON.stringify(items.map(({ id, name }) => [id, name ?? '']));
}

/** WebrtcPlayer 与 WebrtcGridPlayer 共享的播放地址提供函数契约。 */
export type PlayerFunPlay = (
  id: string,
  forceRefresh?: boolean,
  streamType?: StreamPreference,
  sourceError?: boolean,
  failureReport?: StreamFailureReport,
  playbackProfile?: PlaybackProfile,
  h265Usable?: boolean,
  requestContext?: PlayerRequestContext,
) => Promise<unknown>;

interface QueuedPlayerRequest {
  args: Parameters<PlayerFunPlay>;
  resolve: (value: unknown) => void;
  reject: (reason?: unknown) => void;
  signal?: AbortSignal;
  priority: NonNullable<PlayerRequestContext['priority']>;
  enqueuedAtMs: number;
  onAbort?: () => void;
}

export type PlayerFunPlayLimiter = PlayerFunPlay & { refreshLimit(): void };

function createAbortError(): Error {
  if (typeof DOMException !== 'undefined') return new DOMException('播放地址请求已取消', 'AbortError');
  const error = new Error('播放地址请求已取消');
  error.name = 'AbortError';
  return error;
}

/** 为宫格内的 /play 请求削峰；只限制取源并发，不改变后续 WebRTC/MSE 竞争与 profile。 */
export function createPlayerFunPlayLimiter(provider: PlayerFunPlay, getLimit: () => number): PlayerFunPlayLimiter {
  const queue: QueuedPlayerRequest[] = [];
  let activeCount = 0;

  const enqueue = (entry: QueuedPlayerRequest) => {
    if (entry.priority === 'normal') {
      queue.push(entry);
      return;
    }

    // 交互请求排在所有普通等待项之前，但不会越过更早到达的交互请求。
    // 已经进入 provider 的 active 请求不在 queue 中，因此不会被抢占。
    const firstNormalIndex = queue.findIndex((queued) => queued.priority === 'normal');
    if (firstNormalIndex < 0) queue.push(entry);
    else queue.splice(firstNormalIndex, 0, entry);
  };

  const drain = () => {
    const limit = Math.max(1, Math.floor(getLimit()) || 1);
    while (activeCount < limit && queue.length > 0) {
      const entry = queue.shift()!;
      if (entry.onAbort && entry.signal) entry.signal.removeEventListener('abort', entry.onAbort);
      if (entry.signal?.aborted) {
        entry.reject(createAbortError());
        continue;
      }

      activeCount += 1;
      try {
        entry.args[7]?.onQueueTiming?.(Math.max(0, Date.now() - entry.enqueuedAtMs));
      } catch {
        // 诊断回调不能阻断真实的播放地址请求。
      }
      let request: Promise<unknown>;
      try {
        request = provider(...entry.args);
      } catch (error) {
        activeCount -= 1;
        entry.reject(error);
        continue;
      }
      void request.then(entry.resolve, entry.reject).finally(() => {
        activeCount -= 1;
        drain();
      });
    }
  };

  const limited = ((...args: Parameters<PlayerFunPlay>) =>
    new Promise((resolve, reject) => {
      const signal = args[7]?.signal;
      if (signal?.aborted) {
        reject(createAbortError());
        return;
      }

      const entry: QueuedPlayerRequest = {
        args,
        resolve,
        reject,
        signal,
        priority: args[7]?.priority === 'interactive' ? 'interactive' : 'normal',
        enqueuedAtMs: Date.now(),
      };
      if (signal) {
        entry.onAbort = () => {
          const index = queue.indexOf(entry);
          if (index < 0) return;
          queue.splice(index, 1);
          reject(createAbortError());
        };
        signal.addEventListener('abort', entry.onAbort, { once: true });
      }
      enqueue(entry);
      drain();
    })) as PlayerFunPlayLimiter;
  limited.refreshLimit = drain;
  return limited;
}

/** WebrtcPlayer 与 WebrtcGridPlayer 共用的默认取流实现；第八参可选，原有七参调用保持兼容。 */
export const defaultPlayerFunPlay: PlayerFunPlay = (
  id,
  forceRefresh = false,
  streamType = 'auto',
  sourceError = false,
  failureReport,
  playbackProfile = 'ORIGINAL',
  h265Usable,
  requestContext,
) =>
  request({
    url: 'video/stream/play',
    method: 'get',
    ...(requestContext?.signal ? { signal: requestContext.signal } : {}),
    params: {
      id,
      ...(forceRefresh ? { refresh: true } : {}),
      ...(streamType !== 'auto' ? { streamType } : {}),
      ...(sourceError ? { sourceError: true } : {}),
      playbackProfile,
      ...(typeof h265Usable === 'boolean' ? { h265Usable } : {}),
      ...streamFailureParams(failureReport),
    },
  });
