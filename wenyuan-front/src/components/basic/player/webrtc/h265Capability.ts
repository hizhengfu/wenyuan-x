export type H265PlaybackMode = 'webrtc' | 'mse';

export interface MediaDecodeCapability {
  supported: boolean;
  smooth: boolean;
  powerEfficient: boolean;
}

export interface H265CapabilitySnapshot {
  declaredSupported: boolean;
  webrtc: {
    probed: boolean;
    advertised: boolean;
    mediaCapabilities: MediaDecodeCapability | null;
  };
  mse: {
    probed: boolean;
    advertised: boolean;
    mediaCapabilities: MediaDecodeCapability | null;
  };
}

export interface InboundVideoDecodeStats {
  codecMimeType: string;
  codecPayloadType?: number;
  codecSdpFmtpLine?: string;
  bytesReceived: number;
  packetsReceived: number;
  packetsLost?: number;
  framesReceived: number;
  framesDecoded: number;
  framesRendered: number;
  framesDropped?: number;
  keyFramesDecoded?: number;
  freezeCount?: number;
  pliCount?: number;
  nackCount?: number;
  firCount?: number;
  /** 以下字段仅用于运行诊断，不参与编码、profile 或降级判断。 */
  frameWidth?: number;
  frameHeight?: number;
  framesPerSecond?: number;
  totalDecodeTime?: number;
  totalFreezesDuration?: number;
  decoderImplementation?: string;
  powerEfficientDecoder?: boolean;
  jitterBufferDelay?: number;
  jitterBufferEmittedCount?: number;
}

export interface MseFirstFrameProgress {
  bytesReceived: number;
  bytesAppended: number;
  bufferedEnd: number;
  successfulAppends: number;
}

export type InboundDecodeState = 'no-packets' | 'packets-without-decode' | 'decoded';
export type InboundVideoCodec = 'h265' | 'h264' | 'other' | 'unknown';
export type RequestedPlaybackProfile = 'ORIGINAL' | 'H264';
export type SuccessfulPlaybackAction = 'clear-h264-fallback' | 'confirm-h264-fallback' | 'h264-codec-mismatch' | 'none';
export type H265DecodeFailureAction = 'switch-to-h264' | 'stop';

export interface CodecTerminalFailure {
  type: string;
  code: string;
  mode: H265PlaybackMode | 'unknown';
}

/** 原始流直接输出 H.264 时视为正常播放，并清除不再需要的转码偏好。 */
export function resolveSuccessfulPlaybackAction(
  effectiveProfile: RequestedPlaybackProfile | null,
  codec: string,
): SuccessfulPlaybackAction {
  if (effectiveProfile === 'H264' && codec === 'H.265') return 'h264-codec-mismatch';
  if (effectiveProfile === 'ORIGINAL' && (codec === 'H.265' || codec === 'H.264')) {
    return 'clear-h264-fallback';
  }
  if (effectiveProfile === 'H264' && codec === 'H.264') return 'confirm-h264-fallback';
  return 'none';
}

/** 一次播放会话最多执行一次 ORIGINAL→H264；H264 失败后停止，不再回环原始流。 */
export function resolveH265DecodeFailureAction(
  playbackProfile: RequestedPlaybackProfile,
  h264SwitchUsed: boolean,
): H265DecodeFailureAction {
  return playbackProfile === 'ORIGINAL' && !h264SwitchUsed ? 'switch-to-h264' : 'stop';
}

/** 只有服务端明确报告 codec 不匹配/不支持，才算某个播放模式已经终止。 */
export function isExplicitCodecTerminalFailure(report: CodecTerminalFailure): boolean {
  return (
    report.type === 'CODEC_MISMATCH' && (report.code === 'CODEC_NOT_MATCHED' || report.code === 'UNSUPPORTED_CODEC')
  );
}

/**
 * 同一次播放尝试中，所有实际启用模式都明确 codec 终止时，以真实协商结果为准尝试一次 H264。
 * 能力预检可能缺失或高估传输可用性，不能覆盖实际协商；网络、超时和 ICE 错误不在此集合中。
 */
export function shouldSwitchToH264AfterCodecTerminals(
  playbackProfile: RequestedPlaybackProfile,
  h264SwitchUsed: boolean,
  modes: H265PlaybackMode[],
  terminalModes: ReadonlySet<H265PlaybackMode | 'unknown'>,
): boolean {
  if (playbackProfile !== 'ORIGINAL' || h264SwitchUsed) return false;
  const enabledModes = modes.length > 0 ? modes : (['webrtc', 'mse'] as H265PlaybackMode[]);
  return enabledModes.every((mode) => terminalModes.has(mode));
}

export type H265StatusReport =
  | {
      type: 'capability';
      cameraId: string;
      codec: 'H265';
      capability: H265CapabilitySnapshot;
    }
  | {
      type: 'runtime';
      cameraId: string;
      codec: 'H265';
      mode: H265PlaybackMode;
      status: 'decoded' | 'decode-failed';
      reason:
        | 'FIRST_FRAME'
        | 'STATS_DECODED'
        | 'PACKETS_WITHOUT_DECODE'
        | 'HEVC_LEVEL_MISMATCH'
        | 'MSE_TYPE_UNSUPPORTED'
        | 'MSE_DATA_WITHOUT_FRAME'
        | 'FIRST_FRAME_TIMEOUT';
      errorType?: 'CODEC_MISMATCH';
      errorCode?: 'H265_DECODE_FAILED';
      stats?: InboundVideoDecodeStats;
      mseBytesReceived?: number;
    };

export type GridH265StatusReport = H265StatusReport & { index: number };

const H265_MSE_CONTENT_TYPE = 'video/mp4; codecs="hvc1.1.6.L153.B0"';
const MEDIA_CAPABILITIES_TIMEOUT_MS = 500;
// V3 只保存已经由 H.264 首帧确认成功的结果；新写入键额外区分码流偏好。
const H265_RUNTIME_UNUSABLE_STORAGE_KEY = 'VIDEO-H265-RUNTIME-UNUSABLE-CAMERAS-V3';
const H264_FALLBACK_CACHE_TTL_MS = 30 * 24 * 60 * 60 * 1_000;
const runtimeH265UnusableCameraExpiries = new Map<string, number>();
const pendingH264FallbackCameraIds = new Set<string>();
let runtimeCacheLoaded = false;
let h265CapabilitySnapshotPromise: Promise<H265CapabilitySnapshot> | null = null;

function normalizeFallbackStreamType(streamType?: string | number | null): string | null {
  if (streamType === undefined || streamType === null || streamType === '') return null;
  if (streamType === 0 || String(streamType).toLowerCase() === 'main' || String(streamType).toUpperCase() === 'L0') {
    return 'L0';
  }
  if (streamType === 1 || String(streamType).toLowerCase() === 'sub' || String(streamType).toUpperCase() === 'L1') {
    return 'L1';
  }
  if (String(streamType).toUpperCase() === 'STATIC') return 'STATIC';
  return 'auto';
}

function fallbackCacheKey(cameraId: string, streamType?: string | number | null): string {
  const normalizedStreamType = normalizeFallbackStreamType(streamType);
  return normalizedStreamType ? `${cameraId}::stream=${normalizedStreamType}` : cameraId;
}

function hasUnexpiredFallback(key: string): boolean {
  const expiresAt = runtimeH265UnusableCameraExpiries.get(key);
  if (!expiresAt) return false;
  if (expiresAt > Date.now()) return true;
  runtimeH265UnusableCameraExpiries.delete(key);
  persistRuntimeH265UnusableCameraIds();
  return false;
}

function loadRuntimeH265UnusableCameraIds() {
  if (runtimeCacheLoaded) return;
  runtimeCacheLoaded = true;
  try {
    const stored = globalThis.localStorage?.getItem(H265_RUNTIME_UNUSABLE_STORAGE_KEY);
    const cameraExpiries = stored ? JSON.parse(stored) : {};
    const now = Date.now();
    if (cameraExpiries && typeof cameraExpiries === 'object' && !Array.isArray(cameraExpiries)) {
      Object.entries(cameraExpiries).forEach(([cameraId, expiresAt]) => {
        if (cameraId && typeof expiresAt === 'number' && expiresAt > now) {
          runtimeH265UnusableCameraExpiries.set(cameraId, expiresAt);
        }
      });
    }
  } catch {
    // 隐私模式、禁用存储或旧缓存格式异常时仅使用内存状态。
  }
}

function persistRuntimeH265UnusableCameraIds() {
  try {
    // 静态 RTSP 地址可能包含凭据，只保留普通摄像头 ID；RTSP 仅在当前页面内共享。
    const cameraExpiries = Object.fromEntries(
      [...runtimeH265UnusableCameraExpiries].filter(([cameraId]) => !/^rtsps?:\/\//i.test(cameraId)),
    );
    globalThis.localStorage?.setItem(H265_RUNTIME_UNUSABLE_STORAGE_KEY, JSON.stringify(cameraExpiries));
  } catch {
    // 存储不可用时仍保留当前页面内的内存状态。
  }
}

/**
 * 当前摄像头是否已由真实播放证据确认其 H.265 码流无法解码。
 */
export function isH265RuntimeUnusable(cameraId: string, streamType?: string | number | null): boolean {
  if (!cameraId) return false;
  loadRuntimeH265UnusableCameraIds();
  const key = fallbackCacheKey(cameraId, streamType);
  const streamPrefix = `${cameraId}::stream=`;
  const matched =
    hasUnexpiredFallback(key) ||
    (key !== cameraId && hasUnexpiredFallback(cameraId)) ||
    (key === cameraId &&
      [...runtimeH265UnusableCameraExpiries.keys()].some(
        (candidate) => candidate.startsWith(streamPrefix) && hasUnexpiredFallback(candidate),
      ));
  return matched;
}

/** 已确认的 30 天缓存直接决定下一次起播档位；缓存过期后重新验证原始流。 */
export function resolveInitialPlaybackProfile(
  cameraId: string,
  streamType?: string | number | null,
): RequestedPlaybackProfile {
  return isH265RuntimeUnusable(cameraId, streamType) ? 'H264' : 'ORIGINAL';
}

/**
 * 浏览器明确无法使用 H.265 时，普通摄像头无需等待真实 H.265 数据，首次取源即可选择 H.264。
 * 已经使用过兼容流的会话和没有服务端变体的静态 RTSP 都不在此处再次切换。
 */
export function shouldPreselectH264ForCapability(
  cameraId: string,
  playbackProfile: RequestedPlaybackProfile,
  h264SwitchUsed: boolean,
  h265Usable?: boolean,
): boolean {
  return (
    Boolean(cameraId) &&
    !/^rtsps?:\/\//i.test(cameraId) &&
    h265Usable === false &&
    resolveH265DecodeFailureAction(playbackProfile, h264SwitchUsed) === 'switch-to-h264'
  );
}

/** H.265 探测失败后只记录当前实例的待确认状态；H.264 首帧前不跨播放器广播。 */
export function requestH264Fallback(cameraId: string, streamType?: string | number | null): void {
  const key = fallbackCacheKey(cameraId, streamType);
  if (!cameraId || isH265RuntimeUnusable(cameraId, streamType) || pendingH264FallbackCameraIds.has(key)) return;
  pendingH264FallbackCameraIds.add(key);
}

/** H.264 已真实出首帧后确认降级有效，此时才写入会话缓存。 */
export function confirmH264Fallback(cameraId: string, streamType?: string | number | null): void {
  if (!cameraId) return;
  loadRuntimeH265UnusableCameraIds();
  const key = fallbackCacheKey(cameraId, streamType);
  pendingH264FallbackCameraIds.delete(key);
  if (hasUnexpiredFallback(key)) return;
  runtimeH265UnusableCameraExpiries.set(key, Date.now() + H264_FALLBACK_CACHE_TTL_MS);
  persistRuntimeH265UnusableCameraIds();
}

/** H.264 首帧前失败或播放被取消时撤销临时状态，让下次仍可重新探测 H.265。 */
export function cancelH264Fallback(cameraId: string, streamType?: string | number | null): void {
  if (!cameraId) return;
  const key = fallbackCacheKey(cameraId, streamType);
  pendingH264FallbackCameraIds.delete(key);
  if (key === cameraId) {
    const streamPrefix = `${cameraId}::stream=`;
    [...pendingH264FallbackCameraIds]
      .filter((candidate) => candidate.startsWith(streamPrefix))
      .forEach((candidate) => pendingH264FallbackCameraIds.delete(candidate));
  }
}

/** 实际 H.265 已成功出首帧时清除旧的 H.264 偏好，后续允许继续使用真实可播放编码。 */
export function forgetH264Fallback(cameraId: string, streamType?: string | number | null): void {
  if (!cameraId) return;
  loadRuntimeH265UnusableCameraIds();
  const key = fallbackCacheKey(cameraId, streamType);
  pendingH264FallbackCameraIds.delete(key);
  let changed = runtimeH265UnusableCameraExpiries.delete(key);
  if (key === cameraId) {
    const streamPrefix = `${cameraId}::stream=`;
    for (const candidate of [...runtimeH265UnusableCameraExpiries.keys()]) {
      if (candidate.startsWith(streamPrefix)) {
        runtimeH265UnusableCameraExpiries.delete(candidate);
        changed = true;
      }
    }
  }
  // 旧版只以 cameraId 作键；当前码流已用 ORIGINAL 出帧后同时清理遗留全局偏好。
  if (key !== cameraId) changed = runtimeH265UnusableCameraExpiries.delete(cameraId) || changed;
  if (changed) persistRuntimeH265UnusableCameraIds();
}

/** @internal 仅供单元测试隔离模块级与 localStorage 状态。 */
export function resetH265RuntimeUnusableForTests(): void {
  runtimeH265UnusableCameraExpiries.clear();
  pendingH264FallbackCameraIds.clear();
  runtimeCacheLoaded = false;
  h265CapabilitySnapshotPromise = null;
  try {
    globalThis.localStorage?.removeItem(H265_RUNTIME_UNUSABLE_STORAGE_KEY);
  } catch {
    // 测试环境未提供存储时无需处理。
  }
}

function compactCapability(value: any): MediaDecodeCapability | null {
  if (!value || typeof value.supported !== 'boolean') return null;
  return {
    supported: value.supported,
    smooth: value.smooth === true,
    powerEfficient: value.powerEfficient === true,
  };
}

async function queryMediaCapabilities(configuration: Record<string, unknown>): Promise<MediaDecodeCapability | null> {
  const mediaCapabilities = (globalThis.navigator as any)?.mediaCapabilities;
  if (!mediaCapabilities?.decodingInfo) return null;

  let timeoutId: ReturnType<typeof setTimeout> | null = null;
  try {
    const timeout = new Promise<null>((resolve) => {
      timeoutId = setTimeout(() => resolve(null), MEDIA_CAPABILITIES_TIMEOUT_MS);
    });
    return compactCapability(await Promise.race([mediaCapabilities.decodingInfo(configuration), timeout]));
  } catch {
    return null;
  } finally {
    if (timeoutId) clearTimeout(timeoutId);
  }
}

/**
 * 查询浏览器声明的 H.265 接收/解码能力。结果只代表能力预检，不代表某一路实际码流一定可解。
 */
async function detectH265Capability(): Promise<H265CapabilitySnapshot> {
  const receiver = (globalThis as any).RTCRtpReceiver;
  const receiverCapabilities = receiver?.getCapabilities?.('video');
  const webrtcProbed = Array.isArray(receiverCapabilities?.codecs);
  const webrtcAdvertised =
    receiverCapabilities?.codecs?.some((codec: { mimeType?: string }) =>
      /video\/(?:h265|hevc)/i.test(codec.mimeType ?? ''),
    ) === true;

  const MediaSourceCtor = (globalThis as any).ManagedMediaSource ?? (globalThis as any).MediaSource;
  const mseProbed = typeof MediaSourceCtor?.isTypeSupported === 'function';
  let mseAdvertised = false;
  try {
    mseAdvertised = MediaSourceCtor?.isTypeSupported?.(H265_MSE_CONTENT_TYPE) === true;
  } catch {
    mseAdvertised = false;
  }

  const videoProfile = {
    width: 1920,
    height: 1080,
    bitrate: 4_000_000,
    framerate: 25,
  };
  const [webrtcMediaCapabilities, mseMediaCapabilities] = await Promise.all([
    queryMediaCapabilities({
      type: 'webrtc',
      video: { contentType: 'video/H265', ...videoProfile },
    }),
    queryMediaCapabilities({
      type: 'media-source',
      video: { contentType: H265_MSE_CONTENT_TYPE, ...videoProfile },
    }),
  ]);

  return {
    declaredSupported:
      webrtcAdvertised ||
      mseAdvertised ||
      webrtcMediaCapabilities?.supported === true ||
      mseMediaCapabilities?.supported === true,
    webrtc: {
      probed: webrtcProbed,
      advertised: webrtcAdvertised,
      mediaCapabilities: webrtcMediaCapabilities,
    },
    mse: {
      probed: mseProbed,
      advertised: mseAdvertised,
      mediaCapabilities: mseMediaCapabilities,
    },
  };
}

/**
 * 同一页面所有播放器共享一次浏览器能力快照；各实例仍应按自己的启用 mode 调用 resolveH265Usable。
 */
export function probeH265Capability(): Promise<H265CapabilitySnapshot> {
  if (!h265CapabilitySnapshotPromise) {
    h265CapabilitySnapshotPromise = detectH265Capability().catch((error) => {
      h265CapabilitySnapshotPromise = null;
      throw error;
    });
  }
  return h265CapabilitySnapshotPromise;
}

/**
 * 将能力快照压缩为后端选流提示：true=至少一种启用模式支持，false=全部明确不支持，
 * undefined=浏览器没有提供足够证据，仍应先尝试原始流。
 */
export function resolveH265Usable(capability: H265CapabilitySnapshot, modes: H265PlaybackMode[]): boolean | undefined {
  const enabledModes = modes.length > 0 ? modes : (['webrtc', 'mse'] as H265PlaybackMode[]);
  const states = enabledModes.map((mode) => {
    const item = capability[mode];
    // SDP/SourceBuffer 中声明 codec 只代表可以协商；MediaCapabilities 的明确结论更接近真实解码能力。
    const supported = item.mediaCapabilities !== null ? item.mediaCapabilities.supported : item.advertised;
    const known = item.mediaCapabilities !== null || item.probed;
    return { supported, known };
  });

  if (states.some((state) => state.supported)) return true;
  if (states.every((state) => state.known)) return false;
  return undefined;
}

/** 聚合 WebRTC 视频入站统计，并解析当前实际使用的 codec。 */
export function collectInboundVideoDecodeStats(report: RTCStatsReport): InboundVideoDecodeStats | null {
  const codecs = new Map<string, { mimeType: string; payloadType?: number; sdpFmtpLine?: string }>();
  const inbound: any[] = [];

  report.forEach((item: any) => {
    if (item.type === 'codec' && typeof item.id === 'string') {
      codecs.set(item.id, {
        mimeType: item.mimeType ?? '',
        ...(typeof item.payloadType === 'number' ? { payloadType: item.payloadType } : {}),
        ...(typeof item.sdpFmtpLine === 'string' ? { sdpFmtpLine: item.sdpFmtpLine } : {}),
      });
    } else if (item.type === 'inbound-rtp' && (item.kind === 'video' || item.mediaType === 'video')) {
      inbound.push(item);
    }
  });

  if (inbound.length === 0) return null;

  const mediaInbound = inbound.filter((item) => {
    const mimeType = codecs.get(item.codecId)?.mimeType ?? '';
    return !/video\/(?:rtx|red|ulpfec|flexfec)/i.test(mimeType);
  });
  const trackedInbound = mediaInbound.length > 0 ? mediaInbound : inbound;
  const sum = (field: string) => trackedInbound.reduce((total, item) => total + (Number(item[field]) || 0), 0);
  const optionalNumber = (value: unknown): number | undefined => {
    if (value === null || value === undefined || value === '') return undefined;
    const number = Number(value);
    return Number.isFinite(number) ? number : undefined;
  };
  const optionalSum = (field: string): number | undefined => {
    const values = trackedInbound
      .map((item) => optionalNumber(item[field]))
      .filter((value): value is number => value !== undefined);
    return values.length > 0 ? values.reduce((total, value) => total + value, 0) : undefined;
  };
  const activeInbound = [...trackedInbound].sort(
    (left, right) =>
      (Number(right.framesReceived) || 0) - (Number(left.framesReceived) || 0) ||
      (Number(right.bytesReceived) || 0) - (Number(left.bytesReceived) || 0),
  )[0];
  const codec = codecs.get(activeInbound?.codecId);
  const frameWidth = optionalNumber(activeInbound?.frameWidth);
  const frameHeight = optionalNumber(activeInbound?.frameHeight);
  const framesPerSecond = optionalNumber(activeInbound?.framesPerSecond);
  const totalDecodeTime = optionalSum('totalDecodeTime');
  const totalFreezesDuration = optionalSum('totalFreezesDuration');
  const jitterBufferDelay = optionalSum('jitterBufferDelay');
  const jitterBufferEmittedCount = optionalSum('jitterBufferEmittedCount');
  const decoderImplementation =
    typeof activeInbound?.decoderImplementation === 'string' && activeInbound.decoderImplementation
      ? activeInbound.decoderImplementation
      : undefined;
  const powerEfficientDecoder =
    typeof activeInbound?.powerEfficientDecoder === 'boolean' ? activeInbound.powerEfficientDecoder : undefined;

  return {
    codecMimeType: codec?.mimeType ?? '',
    ...(codec?.payloadType !== undefined ? { codecPayloadType: codec.payloadType } : {}),
    ...(codec?.sdpFmtpLine ? { codecSdpFmtpLine: codec.sdpFmtpLine } : {}),
    bytesReceived: sum('bytesReceived'),
    packetsReceived: sum('packetsReceived'),
    packetsLost: sum('packetsLost'),
    framesReceived: sum('framesReceived'),
    framesDecoded: sum('framesDecoded'),
    framesRendered: sum('framesRendered'),
    framesDropped: sum('framesDropped'),
    keyFramesDecoded: sum('keyFramesDecoded'),
    freezeCount: sum('freezeCount'),
    pliCount: sum('pliCount'),
    nackCount: sum('nackCount'),
    firCount: sum('firCount'),
    ...(frameWidth !== undefined ? { frameWidth } : {}),
    ...(frameHeight !== undefined ? { frameHeight } : {}),
    ...(framesPerSecond !== undefined ? { framesPerSecond } : {}),
    ...(totalDecodeTime !== undefined ? { totalDecodeTime } : {}),
    ...(totalFreezesDuration !== undefined ? { totalFreezesDuration } : {}),
    ...(decoderImplementation !== undefined ? { decoderImplementation } : {}),
    ...(powerEfficientDecoder !== undefined ? { powerEfficientDecoder } : {}),
    ...(jitterBufferDelay !== undefined ? { jitterBufferDelay } : {}),
    ...(jitterBufferEmittedCount !== undefined ? { jitterBufferEmittedCount } : {}),
  };
}

export function classifyInboundDecode(stats: InboundVideoDecodeStats | null): InboundDecodeState {
  if (stats && stats.framesDecoded > 0) return 'decoded';
  if (stats && (stats.bytesReceived > 0 || stats.packetsReceived > 0 || stats.framesReceived > 0)) {
    return 'packets-without-decode';
  }
  return 'no-packets';
}

/** SDP 只表示可用格式集合；只有 inbound-rtp.codecId 指向的 codec 才代表当前实际入站编码。 */
export function classifyInboundVideoCodec(stats: InboundVideoDecodeStats | null): InboundVideoCodec {
  const mimeType = stats?.codecMimeType ?? '';
  if (!mimeType) return 'unknown';
  if (/^video\/(?:h265|hevc)$/i.test(mimeType)) return 'h265';
  if (/^video\/(?:h264|avc)$/i.test(mimeType)) return 'h264';
  return 'other';
}

/** H.265 只有在探测期限结束且确实收到媒体但没有解码帧时才允许降级。 */
export function shouldFallbackH265FirstFrame(
  stats: InboundVideoDecodeStats | null,
  deadlineReached: boolean,
  mediaProgressConfirmed = true,
): boolean {
  return (
    classifyInboundVideoCodec(stats) === 'h265' &&
    deadlineReached &&
    mediaProgressConfirmed &&
    classifyInboundDecode(stats) === 'packets-without-decode'
  );
}
