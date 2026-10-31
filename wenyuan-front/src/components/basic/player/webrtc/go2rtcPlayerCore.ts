import { BoundedMediaQueue } from './mseBuffer';
import {
  classifyInboundVideoCodec,
  shouldFallbackH265FirstFrame,
  type InboundVideoDecodeStats,
  type MseFirstFrameProgress,
} from './h265Capability';
import {
  buildHevcLevelCompatibilityDiagnostics,
  readWebrtcRuntimeDiagnostics,
  type HevcLevelCompatibilityDiagnostics,
  type WebrtcRuntimeDiagnostics,
} from './webrtcDiagnostics';
import { classifyStreamFailure, type StreamFailureReport } from './webrtcFailure';

export type Go2rtcPlayerMode = 'webrtc' | 'mse';
export type Go2rtcPlayerStage = 'connecting' | 'negotiating' | 'buffering' | 'waiting-frame' | 'reconnecting';
export type Go2rtcPlayerAttemptKind = 'initial' | 'active-alternate';

export interface Go2rtcPlayerPlayingEvent {
  mode: Go2rtcPlayerMode;
  codec: 'H.265' | 'H.264' | 'OTHER' | 'UNKNOWN';
  runtime: WebrtcRuntimeDiagnostics | null;
  hevcLevel: HevcLevelCompatibilityDiagnostics | null;
}

export interface Go2rtcPlayerFailureEvent {
  kind: 'source' | 'h265-decode' | 'codec-unsupported';
  message: string;
  report: StreamFailureReport;
  mode: Go2rtcPlayerMode | 'unknown';
  /** Core 内部连接尝试编号；由外层据此隔离同一次尝试内的编码证据。 */
  attemptId?: number;
  /** 本次尝试中浏览器实际启动的候选模式，不等同于配置中声明的模式。 */
  attemptedModes?: Go2rtcPlayerMode[];
}

export interface Go2rtcPlayerActiveRecoveryEvent {
  message: string;
  failedMode: Go2rtcPlayerMode | null;
  /** disconnectTransport 之前同步冻结的故障现场。 */
  diagnostics: Go2rtcPlayerDiagnostics;
}

export interface Go2rtcPlayerAttemptTimingDiagnostics {
  attemptId: number;
  socketOpenMs?: number;
  firstMseByteMs?: number;
  firstWebrtcMediaMs?: number;
  firstMediaMs?: number;
  primaryPresentedMs?: number;
  playingMs?: number;
}

export interface Go2rtcVideoPlaybackQualityDiagnostics {
  totalVideoFrames: number | null;
  droppedVideoFrames: number | null;
  corruptedVideoFrames: number | null;
}

export interface Go2rtcPlayerDiagnostics {
  timestamp: string;
  wsState: number;
  pcConnectionState: RTCPeerConnectionState | null;
  activeMode: Go2rtcPlayerMode | null;
  mseCodecs: string | null;
  mseProgress: MseFirstFrameProgress;
  webrtc: WebrtcRuntimeDiagnostics | null;
  hevcLevel: HevcLevelCompatibilityDiagnostics | null;
  attemptTiming: Go2rtcPlayerAttemptTimingDiagnostics | null;
  modeRecovery: {
    configuredModes: Go2rtcPlayerMode[];
    attemptedModes: Go2rtcPlayerMode[];
    failedModes: Go2rtcPlayerMode[];
    modeOverride: Go2rtcPlayerMode | null;
    manualMode: Go2rtcPlayerMode | null;
    switchCount: number;
    maxSwitches: number;
    lastSwitchReason: string | null;
  };
  presentation: {
    requestVideoFrameCallbackSupported: boolean;
    lastPresentedFrameAt: number | null;
    waitingForPrimaryFrame: boolean;
    sourceTransition: 'to-webrtc' | 'to-mse' | null;
  };
  failureContext: {
    lastWebrtcSelection: {
      confirmed: boolean;
      hiddenVideoWidth: number;
      hiddenVideoHeight: number;
      inboundVideo: InboundVideoDecodeStats | null;
    } | null;
    lastPrimaryCommitFailure: {
      phase: 'initial' | 'reset-retry';
      mseRestorable: boolean;
      video: Go2rtcVideoElementDiagnostics;
      inboundVideo: InboundVideoDecodeStats | null;
    } | null;
    lastMseFailure: {
      message: string;
      codecs: string | null;
      progress: MseFirstFrameProgress;
      video: Go2rtcVideoElementDiagnostics;
    } | null;
  };
  video: {
    readyState: number;
    networkState: number;
    currentTime: number;
    videoWidth: number;
    videoHeight: number;
    playbackRate: number;
    playbackQuality: Go2rtcVideoPlaybackQualityDiagnostics | null;
  };
}

export interface Go2rtcPlayerCoreOptions {
  modes: Go2rtcPlayerMode[];
  iceServers: RTCIceServer[];
  /** 是否协商并接收音频；默认 true。 */
  receiveAudio?: boolean;
  /** 外层双 video 为已播放画面创建的备用 Core；其失败必须回外层重新取源，不能改变全局编码结论。 */
  attemptKind?: Go2rtcPlayerAttemptKind;
  firstFrameTimeoutMs?: number;
  reconnectTimeoutMs?: number;
  websocketOpenTimeoutMs?: number;
  peerDisconnectedGraceMs?: number;
  healthCheckIntervalMs?: number;
  healthFailureThreshold?: number;
  maxInternalReconnects?: number;
  alternateFirstFrameTimeoutMs?: number;
  webrtcEvidenceTimeoutMs?: number;
  primaryFrameTimeoutMs?: number;
  maxModeSwitches?: number;
  onStage?: (stage: Go2rtcPlayerStage, message: string) => void;
  onPlaying?: (event: Go2rtcPlayerPlayingEvent) => void;
  onFailure?: (event: Go2rtcPlayerFailureEvent) => void;
  /** 已经呈现过首帧后的恢复交给外层，以便在备用 video 首帧就绪前保留当前画面。 */
  onActiveRecovery?: (event: Go2rtcPlayerActiveRecoveryEvent) => void;
}

export type Go2rtcFirstFrameTimeoutAction = 'h265-decode-failed-webrtc' | 'h265-decode-failed-mse' | 'reconnect';

export interface Go2rtcFirstFrameTimeoutEvidence {
  inboundVideo: InboundVideoDecodeStats | null;
  inboundProgressSamples: number;
  mseProgressSamples?: number;
  mseCodecs: string;
  initialMse: MseFirstFrameProgress;
  currentMse: MseFirstFrameProgress;
}

export type Go2rtcPrimaryVideoErrorAction = 'h265-decode-failed-mse' | 'ignore' | 'reconnect';

export interface Go2rtcPrimaryVideoErrorEvidence {
  activeMode: Go2rtcPlayerMode | null;
  playingReported: boolean;
  mediaErrorCode: number | null;
  mseCodecs: string;
  inboundVideo: InboundVideoDecodeStats | null;
  primarySourceTransition?: boolean;
}

export interface Go2rtcPlaybackHealthSample {
  currentTime: number;
  framesDecoded: number | null;
  framesRendered: number | null;
}

export interface Go2rtcMsePlaybackAdjustment {
  playbackRate: number;
  seekTo: number | null;
}

export interface Go2rtcVideoElementDiagnostics {
  readyState: number;
  networkState: number;
  currentTime: number;
  videoWidth: number;
  videoHeight: number;
  playbackRate: number;
  playbackQuality: Go2rtcVideoPlaybackQualityDiagnostics | null;
  mediaErrorCode: number | null;
}

type MessageHandler = (message: { type?: string; value?: string }) => void;
type Go2rtcAttemptTimingField = Exclude<keyof Go2rtcPlayerAttemptTimingDiagnostics, 'attemptId' | 'firstMediaMs'>;
type Go2rtcAttemptTimingState = Omit<Go2rtcPlayerAttemptTimingDiagnostics, 'firstMediaMs'> & { startedAt: number };

// 生产环境可将 go2rtc RTSP 慢源读取窗口提高到 60 秒；播放器再多等 10 秒，
// 确保优先接收到 go2rtc 的明确源错误，而不是由前端提前关闭连接。
const DEFAULT_FIRST_FRAME_TIMEOUT_MS = 70_000;
const DEFAULT_RECONNECT_TIMEOUT_MS = 30_000;
const DEFAULT_WEBSOCKET_OPEN_TIMEOUT_MS = 8_000;
const DEFAULT_PEER_DISCONNECTED_GRACE_MS = 2_000;
const DEFAULT_HEALTH_CHECK_INTERVAL_MS = 5_000;
const DEFAULT_HEALTH_FAILURE_THRESHOLD = 3;
const DEFAULT_MAX_INTERNAL_RECONNECTS = 1;
const DEFAULT_ALTERNATE_FIRST_FRAME_TIMEOUT_MS = 10_000;
const DEFAULT_WEBRTC_EVIDENCE_TIMEOUT_MS = 2_000;
const DEFAULT_PRIMARY_FRAME_TIMEOUT_MS = 3_000;
const WEBRTC_EVIDENCE_SAMPLE_INTERVAL_MS = 100;
const DEFAULT_MAX_MODE_SWITCHES = 1;
const PRESENTATION_SAMPLE_INTERVAL_MS = 1_000;
const MAX_MSE_QUEUE_BYTES = 8 * 1024 * 1024;
const MSE_CATCH_UP_THRESHOLD_SECONDS = 0.1;
const MSE_SEVERE_LAG_SECONDS = 2;
const MSE_LIVE_TARGET_SECONDS = 0.5;
const MSE_CATCH_UP_RATE = 1.05;
const MSE_RETAIN_SECONDS = 5;
const MSE_TRIM_THRESHOLD_SECONDS = 7;
const MSE_TRIM_INTERVAL_MS = 2_000;

type VideoWithFrameCallbacks = HTMLVideoElement & {
  requestVideoFrameCallback?: (callback: (now: number) => void) => number;
  cancelVideoFrameCallback?: (handle: number) => void;
};
type VideoWithPlaybackQuality = HTMLVideoElement & {
  getVideoPlaybackQuality?: () => Partial<VideoPlaybackQuality>;
};

// 与 go2rtc v1.9.14 www/video-rtc.js 保持一致；这里只保留本组件启用的 MSE/WebRTC 两种模式。
const GO2RTC_CODECS = [
  'avc1.640029',
  'avc1.64002A',
  'avc1.640033',
  'hvc1.1.6.L153.B0',
  'mp4a.40.2',
  'mp4a.40.5',
  'flac',
  'opus',
];
const GO2RTC_AUDIO_CODECS = new Set(['mp4a.40.2', 'mp4a.40.5', 'flac', 'opus']);

function asPlaybackCodec(codec: ReturnType<typeof classifyInboundVideoCodec>): Go2rtcPlayerPlayingEvent['codec'] {
  if (codec === 'h265') return 'H.265';
  if (codec === 'h264') return 'H.264';
  if (codec === 'other') return 'OTHER';
  return 'UNKNOWN';
}

function codecFromMse(codecs: string): Go2rtcPlayerPlayingEvent['codec'] {
  if (/(?:hvc1|hev1)\./i.test(codecs)) return 'H.265';
  if (/avc1\./i.test(codecs)) return 'H.264';
  return codecs ? 'OTHER' : 'UNKNOWN';
}

function emptyMseProgress(): MseFirstFrameProgress {
  return { bytesReceived: 0, bytesAppended: 0, bufferedEnd: 0, successfulAppends: 0 };
}

/**
 * MSE 只做温和追帧：轻微落后时小幅加速，明显落后时跳到直播点前的安全余量。
 * 永远不使用小于 1 的 playbackRate，避免把本应追赶的画面进一步减速。
 */
export function resolveGo2rtcMsePlaybackAdjustment(
  currentTime: number,
  bufferedStart: number,
  bufferedEnd: number,
): Go2rtcMsePlaybackAdjustment {
  if (![currentTime, bufferedStart, bufferedEnd].every(Number.isFinite) || bufferedEnd <= bufferedStart) {
    return { playbackRate: 1, seekTo: null };
  }

  const lag = bufferedEnd - currentTime;
  if (currentTime < bufferedStart || lag > MSE_SEVERE_LAG_SECONDS) {
    const liveTarget = Math.max(bufferedStart, bufferedEnd - MSE_LIVE_TARGET_SECONDS);
    return { playbackRate: 1, seekTo: liveTarget > currentTime ? liveTarget : null };
  }
  return {
    playbackRate: lag > MSE_CATCH_UP_THRESHOLD_SECONDS ? MSE_CATCH_UP_RATE : 1,
    seekTo: null,
  };
}

/** SourceBuffer 超过高水位且距离上次裁剪达到最小间隔时，才裁回保留窗口。 */
export function resolveGo2rtcMseTrimStart(
  bufferedStart: number,
  bufferedEnd: number,
  lastTrimAt: number | null,
  now: number,
): number | null {
  if (![bufferedStart, bufferedEnd, now].every(Number.isFinite)) return null;
  if (bufferedEnd - bufferedStart <= MSE_TRIM_THRESHOLD_SECONDS) return null;
  if (lastTrimAt !== null && now >= lastTrimAt && now - lastTrimAt < MSE_TRIM_INTERVAL_MS) return null;
  const trimStart = bufferedEnd - MSE_RETAIN_SECONDS;
  return trimStart > bufferedStart ? trimStart : null;
}

function h265DecodeFailure(mode: Go2rtcPlayerMode): Go2rtcPlayerFailureEvent {
  return {
    kind: 'h265-decode',
    message: '已收到 H.265 媒体数据，但浏览器未能解码首帧',
    report: { type: 'CODEC_MISMATCH', code: 'H265_DECODE_FAILED', mode },
    mode,
  };
}

/**
 * 首帧超时只把两类证据认作 H.265 解码失败：
 * WebRTC stats 的实际 inbound codec=H265 且媒体持续推进，或 MSE 的实际 hvc1/hev1 轨已形成 buffered 时间轴。
 */
export function resolveGo2rtcFirstFrameTimeoutAction(
  evidence: Go2rtcFirstFrameTimeoutEvidence,
): Go2rtcFirstFrameTimeoutAction {
  // WebRTC 已经解出帧时，主 video 未触发 loadeddata 属于桥接/渲染问题，不能被并行 MSE 误判成 H265 不可解。
  if ((evidence.inboundVideo?.framesDecoded ?? 0) > 0) return 'reconnect';
  if (shouldFallbackH265FirstFrame(evidence.inboundVideo, true, evidence.inboundProgressSamples >= 2)) {
    return 'h265-decode-failed-webrtc';
  }
  // stats 已明确实际走 H264/其他编码时，不允许并行 MSE 的 HEVC 轨覆盖真实 WebRTC codec 结论。
  if (classifyInboundVideoCodec(evidence.inboundVideo) !== 'unknown') return 'reconnect';

  const mseH265 = /(?:hvc1|hev1)\./i.test(evidence.mseCodecs);
  const mseProgressed =
    evidence.currentMse.bytesReceived > evidence.initialMse.bytesReceived &&
    evidence.currentMse.bytesAppended > evidence.initialMse.bytesAppended &&
    evidence.currentMse.successfulAppends > evidence.initialMse.successfulAppends &&
    evidence.currentMse.bufferedEnd > evidence.initialMse.bufferedEnd + 0.001;
  return mseH265 && mseProgressed && (evidence.mseProgressSamples ?? 0) >= 2 ? 'h265-decode-failed-mse' : 'reconnect';
}

/**
 * video.error 只有在首帧前、当前实际模式为 MSE 且服务端已声明 HEVC 轨时，
 * 才能作为 H.265 解码失败的直接证据。WebRTC 仍交给 stats 首帧探针判断，
 * 避免 ICE/轨道中断被浏览器笼统的 MediaError 误判成编码不兼容。
 */
export function resolveGo2rtcPrimaryVideoErrorAction(
  evidence: Go2rtcPrimaryVideoErrorEvidence,
): Go2rtcPrimaryVideoErrorAction {
  // src/srcObject 切换会让浏览器为旧媒体源派发迟到的 error；交给主画面呈现确认判断新源是否成功。
  if (evidence.primarySourceTransition) return 'ignore';
  // MEDIA_ERR_ABORTED 只表示旧资源被主动终止，不代表新媒体源解码或网络失败。
  if (evidence.mediaErrorCode === 1) return 'ignore';
  if (evidence.playingReported) return 'reconnect';
  if (evidence.mediaErrorCode !== 3 && evidence.mediaErrorCode !== 4) return 'reconnect';

  const activeCodec =
    evidence.activeMode === 'webrtc'
      ? asPlaybackCodec(classifyInboundVideoCodec(evidence.inboundVideo))
      : evidence.activeMode === 'mse'
        ? codecFromMse(evidence.mseCodecs)
        : 'UNKNOWN';
  return evidence.activeMode === 'mse' && activeCodec === 'H.265' ? 'h265-decode-failed-mse' : 'reconnect';
}

/** 低频健康探针只判断是否继续产出画面，不参与 codec/H264 决策。 */
export function hasGo2rtcPlaybackProgress(
  mode: Go2rtcPlayerMode,
  previous: Go2rtcPlaybackHealthSample,
  current: Go2rtcPlaybackHealthSample,
): boolean {
  if (mode === 'webrtc') {
    const hasFrameCounters =
      (previous.framesDecoded ?? 0) > 0 ||
      (current.framesDecoded ?? 0) > 0 ||
      (previous.framesRendered ?? 0) > 0 ||
      (current.framesRendered ?? 0) > 0;
    if (hasFrameCounters) {
      return (
        (current.framesDecoded !== null &&
          previous.framesDecoded !== null &&
          current.framesDecoded > previous.framesDecoded) ||
        (current.framesRendered !== null &&
          previous.framesRendered !== null &&
          current.framesRendered > previous.framesRendered)
      );
    }
  }
  // 部分 Safari/WebView 不暴露帧计数，此时退回媒体时间轴；有帧计数时不允许音频推进掩盖视频冻结。
  return current.currentTime > previous.currentTime + 0.01;
}

/**
 * go2rtc 官方 VideoRTC 的 Vue 适配核心。
 *
 * 核心拥有 WebSocket、MSE 和 RTCPeerConnection 的整个生命周期；外层只负责获取 src、
 * ORIGINAL/H264 源切换与 UI。这样不会再出现两个恢复状态机同时操作同一个 video 的情况。
 */
export class Go2rtcPlayerCore {
  private readonly receiveAudio: boolean;
  private readonly firstFrameTimeoutMs: number;
  private readonly reconnectTimeoutMs: number;
  private readonly websocketOpenTimeoutMs: number;
  private readonly peerDisconnectedGraceMs: number;
  private readonly healthCheckIntervalMs: number;
  private readonly healthFailureThreshold: number;
  private readonly maxInternalReconnects: number;
  private readonly alternateFirstFrameTimeoutMs: number;
  private readonly webrtcEvidenceTimeoutMs: number;
  private readonly primaryFrameTimeoutMs: number;
  private readonly maxModeSwitches: number;
  private readonly messageHandlers = new Map<Go2rtcPlayerMode, MessageHandler>();
  private ws: WebSocket | null = null;
  private pc: RTCPeerConnection | null = null;
  private hiddenVideo: HTMLVideoElement | null = null;
  private mediaSource: MediaSource | null = null;
  private sourceBuffer: SourceBuffer | null = null;
  private lastMseTrimAt: number | null = null;
  private objectUrl = '';
  private onBinaryData: ((data: ArrayBuffer) => void) | null = null;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private websocketOpenTimer: ReturnType<typeof setTimeout> | null = null;
  private peerDisconnectedTimer: ReturnType<typeof setTimeout> | null = null;
  private firstFrameTimer: ReturnType<typeof setTimeout> | null = null;
  private statsTimer: ReturnType<typeof setInterval> | null = null;
  private healthTimer: ReturnType<typeof setInterval> | null = null;
  private healthProbeGeneration = 0;
  private healthSampleInFlightGeneration: number | null = null;
  private healthBaseline: Go2rtcPlaybackHealthSample | null = null;
  private consecutiveHealthFailures = 0;
  private primaryPresentationTimer: ReturnType<typeof setTimeout> | null = null;
  private primaryPresentationFrameId: number | null = null;
  private primaryPresentationResolve: ((success: boolean) => void) | null = null;
  private primaryPresentationCleanup: (() => void) | null = null;
  private presentationMonitorTimer: ReturnType<typeof setTimeout> | null = null;
  private presentationMonitorFrameId: number | null = null;
  private presentationProbeGeneration = 0;
  private lastPresentedFrameAt = 0;
  private pendingPlaying: { mode: Go2rtcPlayerMode; attemptId: number; promise: Promise<boolean> } | null = null;
  private primarySourceTransition: 'to-webrtc' | 'to-mse' | null = null;
  private wsUrl = '';
  private connectStartedAt = 0;
  private attemptTiming: Go2rtcAttemptTimingState | null = null;
  private generation = 0;
  private activeAttemptId = 0;
  private attemptedModes: Go2rtcPlayerMode[] = [];
  private readonly codecUnsupportedModes = new Set<Go2rtcPlayerMode>();
  private stopped = true;
  private externallyRecovering = false;
  private expectedSocketClose = false;
  private playingReported = false;
  private hasProducedFirstFrame = false;
  private internalReconnectCount = 0;
  private modeOverride: Go2rtcPlayerMode | null = null;
  private manualMode: Go2rtcPlayerMode | null = null;
  private modeSwitchCount = 0;
  private lastSwitchReason: string | null = null;
  private automaticAlternatePending = false;
  private automaticWinner: Go2rtcPlayerMode | null = null;
  private readonly failedModes = new Set<Go2rtcPlayerMode>();
  private activeMode: Go2rtcPlayerMode | null = null;
  private mseCodecs = '';
  private mseProgress = emptyMseProgress();
  private latestRuntime: WebrtcRuntimeDiagnostics | null = null;
  private latestHevcLevel: HevcLevelCompatibilityDiagnostics | null = null;
  private lastInbound: InboundVideoDecodeStats | null = null;
  private inboundProgressSamples = 0;
  private lastMseProbe = emptyMseProgress();
  private mseProgressSamples = 0;
  private lastWebrtcSelection: Go2rtcPlayerDiagnostics['failureContext']['lastWebrtcSelection'] = null;
  private lastPrimaryCommitFailure: Go2rtcPlayerDiagnostics['failureContext']['lastPrimaryCommitFailure'] = null;
  private lastMseFailure: Go2rtcPlayerDiagnostics['failureContext']['lastMseFailure'] = null;

  constructor(
    private readonly video: HTMLVideoElement,
    private readonly options: Go2rtcPlayerCoreOptions,
  ) {
    this.receiveAudio = options.receiveAudio !== false;
    this.firstFrameTimeoutMs = options.firstFrameTimeoutMs ?? DEFAULT_FIRST_FRAME_TIMEOUT_MS;
    this.reconnectTimeoutMs = options.reconnectTimeoutMs ?? DEFAULT_RECONNECT_TIMEOUT_MS;
    this.websocketOpenTimeoutMs = options.websocketOpenTimeoutMs ?? DEFAULT_WEBSOCKET_OPEN_TIMEOUT_MS;
    this.peerDisconnectedGraceMs = options.peerDisconnectedGraceMs ?? DEFAULT_PEER_DISCONNECTED_GRACE_MS;
    this.healthCheckIntervalMs = options.healthCheckIntervalMs ?? DEFAULT_HEALTH_CHECK_INTERVAL_MS;
    this.healthFailureThreshold = Math.max(1, options.healthFailureThreshold ?? DEFAULT_HEALTH_FAILURE_THRESHOLD);
    this.maxInternalReconnects =
      options.attemptKind === 'active-alternate'
        ? 0
        : Math.max(0, Math.floor(options.maxInternalReconnects ?? DEFAULT_MAX_INTERNAL_RECONNECTS));
    this.alternateFirstFrameTimeoutMs = Math.max(
      0,
      options.alternateFirstFrameTimeoutMs ?? DEFAULT_ALTERNATE_FIRST_FRAME_TIMEOUT_MS,
    );
    this.webrtcEvidenceTimeoutMs = Math.max(0, options.webrtcEvidenceTimeoutMs ?? DEFAULT_WEBRTC_EVIDENCE_TIMEOUT_MS);
    this.primaryFrameTimeoutMs = Math.max(0, options.primaryFrameTimeoutMs ?? DEFAULT_PRIMARY_FRAME_TIMEOUT_MS);
    this.maxModeSwitches = Math.max(0, Math.floor(options.maxModeSwitches ?? DEFAULT_MAX_MODE_SWITCHES));
    this.video.addEventListener('loadeddata', this.onPrimaryLoadedData);
    this.video.addEventListener('error', this.onPrimaryVideoError);
  }

  connect(wsUrl: string): void {
    this.disconnectTransport(false);
    this.wsUrl = wsUrl;
    this.internalReconnectCount = 0;
    this.resetModeRecoverySession();
    this.resetFailureContext();
    this.stopped = false;
    this.externallyRecovering = false;
    this.openSocket();
  }

  reconnectNow(): void {
    if (!this.wsUrl) return;
    this.disconnectTransport(false);
    this.internalReconnectCount = 0;
    this.resetModeRecoverySession();
    this.resetFailureContext();
    this.stopped = false;
    this.externallyRecovering = false;
    this.openSocket();
  }

  disconnect(): void {
    this.stopped = true;
    this.disconnectTransport(true);
  }

  /** 手动固定播放方式；传 auto 恢复 WebRTC/MSE 自动竞争。 */
  switchMode(mode: Go2rtcPlayerMode | 'auto'): boolean {
    if (!this.wsUrl || this.stopped) return false;
    if (mode !== 'auto' && !this.configuredModes().includes(mode)) return false;
    if (mode !== 'auto' && this.activeMode === mode && this.playingReported && this.manualMode === mode) return true;

    this.manualMode = mode === 'auto' ? null : mode;
    this.modeOverride = this.manualMode;
    this.modeSwitchCount = 0;
    this.automaticAlternatePending = false;
    this.failedModes.clear();
    this.lastSwitchReason = mode === 'auto' ? '手动恢复自动播放方式' : `手动切换至${this.modeLabel(mode)}`;
    this.internalReconnectCount = 0;
    this.disconnectTransport(false);
    this.stopped = false;
    this.externallyRecovering = false;
    this.options.onStage?.('reconnecting', this.lastSwitchReason);
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;
      this.openSocket();
    }, 0);
    return true;
  }

  destroy(options: { clearVideo?: boolean } = {}): void {
    this.stopped = true;
    this.disconnectTransport(options.clearVideo !== false);
    this.video.removeEventListener('loadeddata', this.onPrimaryLoadedData);
    this.video.removeEventListener('error', this.onPrimaryVideoError);
  }

  async getDiagnostics(): Promise<Go2rtcPlayerDiagnostics> {
    await this.sampleWebrtcRuntime();
    return this.getDiagnosticsSnapshot();
  }

  /**
   * 同步冻结当前运行态，供销毁前保存诊断使用。
   * 不再等待 getStats，避免异步采样在 destroy 后把有效故障状态覆盖成 CLOSED/null。
   */
  getDiagnosticsSnapshot(): Go2rtcPlayerDiagnostics {
    const videoPlaybackQuality = this.readVideoPlaybackQuality();
    return {
      timestamp: new Date().toISOString(),
      wsState: this.ws?.readyState ?? WebSocket.CLOSED,
      pcConnectionState: this.pc?.connectionState ?? null,
      activeMode: this.activeMode,
      mseCodecs: this.mseCodecs || null,
      mseProgress: { ...this.mseProgress },
      webrtc: this.latestRuntime,
      hevcLevel: this.latestHevcLevel,
      attemptTiming: this.snapshotAttemptTiming(),
      modeRecovery: {
        configuredModes: this.configuredModes(),
        attemptedModes: [...this.attemptedModes],
        failedModes: [...this.failedModes],
        modeOverride: this.modeOverride,
        manualMode: this.manualMode,
        switchCount: this.modeSwitchCount,
        maxSwitches: this.maxModeSwitches,
        lastSwitchReason: this.lastSwitchReason,
      },
      presentation: {
        requestVideoFrameCallbackSupported: this.supportsVideoFrameCallback(),
        lastPresentedFrameAt: this.lastPresentedFrameAt || null,
        waitingForPrimaryFrame: this.primaryPresentationResolve !== null,
        sourceTransition: this.primarySourceTransition,
      },
      failureContext: {
        lastWebrtcSelection: this.lastWebrtcSelection
          ? {
              ...this.lastWebrtcSelection,
              inboundVideo: this.lastWebrtcSelection.inboundVideo ? { ...this.lastWebrtcSelection.inboundVideo } : null,
            }
          : null,
        lastPrimaryCommitFailure: this.lastPrimaryCommitFailure
          ? {
              ...this.lastPrimaryCommitFailure,
              video: { ...this.lastPrimaryCommitFailure.video },
              inboundVideo: this.lastPrimaryCommitFailure.inboundVideo
                ? { ...this.lastPrimaryCommitFailure.inboundVideo }
                : null,
            }
          : null,
        lastMseFailure: this.lastMseFailure
          ? {
              ...this.lastMseFailure,
              progress: { ...this.lastMseFailure.progress },
              video: { ...this.lastMseFailure.video },
            }
          : null,
      },
      video: {
        readyState: this.video.readyState,
        networkState: this.video.networkState,
        currentTime: this.video.currentTime,
        videoWidth: this.video.videoWidth,
        videoHeight: this.video.videoHeight,
        playbackRate: this.video.playbackRate,
        playbackQuality: videoPlaybackQuality,
      },
    };
  }

  private openSocket(): void {
    if (this.stopped || !this.wsUrl || this.ws || this.pc) return;
    const generation = ++this.generation;
    this.resetAttemptState();
    this.connectStartedAt = Date.now();
    this.attemptTiming = { attemptId: generation, startedAt: this.connectStartedAt };
    this.options.onStage?.('connecting', '正在连接流媒体服务');

    let ws: WebSocket;
    try {
      ws = new WebSocket(this.wsUrl);
    } catch {
      this.scheduleReconnect('流媒体信令连接失败，正在重连');
      return;
    }
    ws.binaryType = 'arraybuffer';
    this.ws = ws;
    this.startWebsocketOpenWatchdog(generation, ws);
    ws.addEventListener('open', () => {
      if (this.isStale(generation, ws)) return;
      this.onSocketOpen(generation);
    });
    ws.addEventListener('close', () => {
      if (this.isStale(generation, ws)) return;
      this.onSocketClose();
    });
  }

  private onSocketOpen(generation: number): void {
    const ws = this.ws;
    if (!ws) return;
    this.recordAttemptTiming(generation, 'socketOpenMs');
    this.clearWebsocketOpenWatchdog();
    this.options.onStage?.('negotiating', '正在协商 WebRTC / MSE');
    ws.addEventListener('message', (event) => {
      if (this.isStale(generation, ws)) return;
      if (typeof event.data === 'string') {
        this.handleJsonMessage(event.data);
      } else if (event.data instanceof ArrayBuffer) {
        if (event.data.byteLength > 0 && this.messageHandlers.has('mse')) {
          this.recordAttemptTiming(generation, 'firstMseByteMs');
        }
        this.onBinaryData?.(event.data);
      }
    });

    const attemptModes = this.modeOverride ? [this.modeOverride] : this.configuredModes();
    if (attemptModes.includes('mse') && this.getMediaSourceConstructor()) this.setupMse();
    if (attemptModes.includes('webrtc') && typeof RTCPeerConnection !== 'undefined') this.setupWebrtc();

    this.activeAttemptId = generation;
    this.attemptedModes = [...this.messageHandlers.keys()];

    if (this.messageHandlers.size === 0) {
      this.reportExternalFailure({
        kind: 'source',
        message: '当前浏览器不支持 WebRTC 或 MSE',
        report: { type: 'CODEC_MISMATCH', code: 'UNSUPPORTED_CODEC', mode: 'unknown' },
        mode: 'unknown',
      });
      return;
    }
    this.startFirstFrameProbe();
  }

  private onSocketClose(): void {
    this.clearWebsocketOpenWatchdog();
    this.ws = null;
    if (this.stopped || this.externallyRecovering || this.expectedSocketClose) {
      this.expectedSocketClose = false;
      return;
    }
    this.scheduleReconnect('连接中断，正在重新连接');
  }

  private handleJsonMessage(raw: string): void {
    let message: { type?: string; value?: string };
    try {
      message = JSON.parse(raw);
    } catch {
      return;
    }

    if (message.type === 'error' && typeof message.value === 'string') {
      const report = classifyStreamFailure(message.value);
      if (
        report.type === 'CODEC_MISMATCH' &&
        (report.code === 'CODEC_NOT_MATCHED' || report.code === 'UNSUPPORTED_CODEC') &&
        (report.mode === 'webrtc' || report.mode === 'mse')
      ) {
        this.reportModeCodecUnsupported({ ...report, mode: report.mode });
        return;
      }
      if (report.type === 'SOURCE_CONNECTION' || report.type === 'SOURCE_PROTOCOL') {
        this.reportExternalFailure({
          kind: 'source',
          message: '视频源不可用',
          report,
          mode: report.mode,
        });
        return;
      }
    }
    this.messageHandlers.forEach((handler) => handler(message));
  }

  private reportModeCodecUnsupported(
    report: StreamFailureReport & { mode: Go2rtcPlayerMode },
    message = `${report.mode === 'webrtc' ? 'WebRTC' : 'MSE'} 模式不支持当前视频编码`,
  ): void {
    const mode = report.mode;
    if (this.stopped || !this.messageHandlers.has(mode)) return;
    if (this.isActiveAlternateAttempt()) {
      this.reportAlternateModeFailure('备用播放方式不支持当前视频编码，正在重新获取播放地址', mode);
      return;
    }
    this.codecUnsupportedModes.add(mode);

    const pendingPc = mode === 'webrtc' ? this.pc : null;
    if (pendingPc && this.canFallbackPendingWebrtcToMse(pendingPc)) {
      const codecEvidenceModes = this.failedModes.size > 0 ? this.configuredModes() : this.attemptedModes;
      // 先保留本次 WebRTC codec 证据，再重建 MSE；disconnect 会销毁当前 attempt 的运行态。
      this.options.onFailure?.({
        kind: 'codec-unsupported',
        message,
        report,
        mode,
        attemptId: this.activeAttemptId,
        attemptedModes: [...codecEvidenceModes],
      });
      this.fallbackPendingWebrtcToMse(pendingPc, 'WebRTC 编码不受支持，正在回退 MSE');
      return;
    }

    if (mode === 'webrtc') {
      this.closePeerConnection();
    } else {
      this.lastMseFailure = {
        message,
        codecs: this.mseCodecs || null,
        progress: { ...this.mseProgress },
        video: this.snapshotVideoElement(),
      };
      this.cleanupMse();
    }
    if (this.activeMode === mode) {
      this.activeMode = this.messageHandlers.has('mse') ? 'mse' : null;
    }
    if (this.messageHandlers.size === 0) {
      this.clearFirstFrameProbe();
      this.clearHealthMonitor();
    }

    // 运行期从一个已成功模式切到备用模式时，前一个模式只是传输/呈现失败，不是 codec 终止证据。
    // 此时必须保留完整配置范围，避免备用模式单独报 codec 错误就误触发 H264。
    const codecEvidenceModes = this.failedModes.size > 0 ? this.configuredModes() : this.attemptedModes;
    const allModesCodecUnsupported =
      this.messageHandlers.size === 0 &&
      codecEvidenceModes.every((attemptedMode) => this.codecUnsupportedModes.has(attemptedMode));
    if (allModesCodecUnsupported) {
      // 外层最多等待 500ms 能力快照后决定是否请求 H264；期间禁止迟到的 close/video.error 抢先重连。
      this.externallyRecovering = true;
    }

    // 逐模式终止只提供编码证据；有其他候选时不关闭它，也不进入整链恢复。
    this.options.onFailure?.({
      kind: 'codec-unsupported',
      message,
      report,
      mode,
      attemptId: this.activeAttemptId,
      attemptedModes: [...codecEvidenceModes],
    });

    if (this.messageHandlers.size === 0 && !allModesCodecUnsupported) {
      // 其他候选因信令/传输而非 codec 退出时，证据不足以降级 H264；回外层同 profile 重新取源。
      this.reportExternalFailure({
        kind: 'source',
        message: '播放候选均已终止，正在重新获取播放地址',
        report: { type: 'UNKNOWN', code: 'UNKNOWN_STREAM_ERROR', mode: 'unknown' },
        mode: 'unknown',
      });
    }
  }

  /**
   * MSE 已明确建立 HEVC 轨且浏览器报告解码错误时，只淘汰本次 attempt 的 MSE 候选。
   * 对外按该 mode 的终止编码证据上报，允许仍在竞争的 WebRTC 在同一 attempt 内继续出画或给出终止证据。
   */
  private reportCompetingMseH265DecodeFailure(message: string): void {
    this.reportModeCodecUnsupported({ type: 'CODEC_MISMATCH', code: 'UNSUPPORTED_CODEC', mode: 'mse' }, message);
  }

  private send(type: string, value?: string): void {
    if (this.ws?.readyState !== WebSocket.OPEN) return;
    this.ws.send(JSON.stringify(value === undefined ? { type } : { type, value }));
  }

  private setupMse(): void {
    const MediaSourceCtor = this.getMediaSourceConstructor();
    if (!MediaSourceCtor) return;

    const mediaSource = new MediaSourceCtor() as MediaSource;
    this.mediaSource = mediaSource;
    this.activeMode = 'mse';
    this.mseCodecs = '';
    this.mseProgress = emptyMseProgress();
    this.lastMseTrimAt = null;
    this.video.playbackRate = 1;

    mediaSource.addEventListener(
      'sourceopen',
      () => {
        if (this.mediaSource !== mediaSource) return;
        this.send('mse', this.supportedMseCodecs(MediaSourceCtor));
      },
      { once: true },
    );

    const ManagedMediaSourceCtor = (window as any).ManagedMediaSource;
    if (typeof ManagedMediaSourceCtor === 'function' && mediaSource instanceof ManagedMediaSourceCtor) {
      this.video.disableRemotePlayback = true;
      this.video.srcObject = mediaSource as any;
    } else {
      this.objectUrl = URL.createObjectURL(mediaSource);
      this.video.srcObject = null;
      this.video.src = this.objectUrl;
    }
    void this.playVideo(this.video);

    this.messageHandlers.set('mse', (message) => {
      if (message.type !== 'mse' || !message.value || this.mediaSource !== mediaSource) return;
      this.mseCodecs = message.value;
      this.options.onStage?.('buffering', '正在接收 MSE 媒体数据');
      this.createSourceBuffer(mediaSource, message.value);
    });
  }

  private createSourceBuffer(mediaSource: MediaSource, codecs: string): void {
    let sourceBuffer: SourceBuffer;
    try {
      sourceBuffer = mediaSource.addSourceBuffer(codecs);
      sourceBuffer.mode = 'segments';
    } catch {
      this.handleMsePipelineFailure('MSE 初始化失败，正在重连');
      return;
    }

    this.sourceBuffer = sourceBuffer;
    const queue = new BoundedMediaQueue(MAX_MSE_QUEUE_BYTES);
    const appendNext = () => {
      if (this.sourceBuffer !== sourceBuffer || sourceBuffer.updating) return;
      const data = queue.shift();
      if (!data) return;
      try {
        sourceBuffer.appendBuffer(data);
        this.mseProgress.bytesAppended += data.byteLength;
      } catch {
        this.handleMsePipelineFailure('MSE 缓冲异常，正在重连');
      }
    };

    sourceBuffer.addEventListener('updateend', () => {
      if (this.sourceBuffer !== sourceBuffer) return;
      this.mseProgress.successfulAppends += 1;
      this.updateMseBufferedRange(sourceBuffer, mediaSource);
      appendNext();
    });

    this.onBinaryData = (data) => {
      this.mseProgress.bytesReceived += data.byteLength;
      if (sourceBuffer.updating || queue.length > 0) {
        if (!queue.enqueue(data)) {
          this.handleMsePipelineFailure('MSE 缓冲积压，正在重连');
        }
        return;
      }
      try {
        sourceBuffer.appendBuffer(data);
        this.mseProgress.bytesAppended += data.byteLength;
      } catch {
        this.handleMsePipelineFailure('MSE 数据追加失败，正在重连');
      }
    };
  }

  /** 竞争阶段只淘汰失败的 MSE 候选；MSE 已经成为赢家后才恢复整条同 profile 管线。 */
  private handleMsePipelineFailure(message: string): void {
    this.lastMseFailure = {
      message,
      codecs: this.mseCodecs || null,
      progress: { ...this.mseProgress },
      video: this.snapshotVideoElement(),
    };
    if (!this.playingReported && this.messageHandlers.has('webrtc')) {
      const webRtcOwnsPrimary = this.activeMode === 'webrtc' || this.primarySourceTransition === 'to-webrtc';
      this.cleanupMse();
      if (webRtcOwnsPrimary) return;
      if (this.activeMode === 'mse') this.activeMode = null;
      this.video.pause();
      this.video.removeAttribute('src');
      this.video.srcObject = null;
      this.video.load();
      return;
    }
    this.scheduleReconnect(message);
  }

  private updateMseBufferedRange(sourceBuffer: SourceBuffer, mediaSource: MediaSource): void {
    let end: number;
    let first: number;
    try {
      const buffered = sourceBuffer.buffered;
      if (!buffered.length) return;
      end = buffered.end(buffered.length - 1);
      first = buffered.start(0);
    } catch {
      // WebRTC 接管主 video 后，旧 MediaSource detach 会让迟到的 updateend 读取 buffered 时抛错。
      return;
    }
    this.mseProgress.bufferedEnd = Math.max(this.mseProgress.bufferedEnd, end);
    const now = Date.now();
    const trimStart = resolveGo2rtcMseTrimStart(first, end, this.lastMseTrimAt, now);
    if (trimStart !== null && !sourceBuffer.updating) {
      try {
        sourceBuffer.remove(first, trimStart);
        this.lastMseTrimAt = now;
        mediaSource.setLiveSeekableRange?.(trimStart, end);
      } catch {
        // 浏览器正在切换 SourceBuffer 状态时下一轮 updateend 会继续清理。
      }
    }
    const adjustment = resolveGo2rtcMsePlaybackAdjustment(this.video.currentTime, first, end);
    if (adjustment.seekTo !== null) {
      try {
        this.video.currentTime = adjustment.seekTo;
      } catch {
        // 媒体时间轴正在 detach/切换时忽略本轮跳转，下一次 updateend 会重新计算。
      }
    }
    this.video.playbackRate = adjustment.playbackRate;
  }

  private setupWebrtc(): void {
    const pc = new RTCPeerConnection({
      bundlePolicy: 'max-bundle',
      iceServers: this.options.iceServers,
      sdpSemantics: 'unified-plan',
    } as RTCConfiguration);
    this.pc = pc;

    pc.addEventListener('icecandidate', (event) => {
      if (pc !== this.pc || this.stopped) return;
      const candidate = event.candidate?.toJSON().candidate ?? '';
      this.send('webrtc/candidate', candidate);
    });
    pc.addEventListener('connectionstatechange', () => {
      if (pc !== this.pc) return;
      if (pc.connectionState === 'connected') {
        this.clearPeerDisconnectedGrace();
        if (!this.playingReported || this.activeMode !== 'webrtc') this.attachWebrtcPreview(pc);
      } else if (pc.connectionState === 'failed') {
        this.handlePeerTransportFailure(pc);
      } else if (pc.connectionState === 'disconnected') {
        this.startPeerDisconnectedGrace(pc);
      }
    });

    this.messageHandlers.set('webrtc', (message) => {
      if (pc !== this.pc) return;
      if (message.type === 'webrtc/candidate' && typeof message.value === 'string') {
        void pc.addIceCandidate({ candidate: message.value, sdpMid: '0' }).catch(() => undefined);
      } else if (message.type === 'webrtc/answer' && typeof message.value === 'string') {
        void pc.setRemoteDescription({ type: 'answer', sdp: message.value }).catch(() => {
          if (pc !== this.pc || this.stopped) return;
          if (this.fallbackPendingWebrtcToMse(pc, 'WebRTC 应答无效，正在回退 MSE')) return;
          this.closePeerConnection();
          if (!this.messageHandlers.has('mse')) this.scheduleReconnect('WebRTC 应答无效，正在重连');
        });
      } else if (message.type === 'error' && message.value?.includes('webrtc/offer')) {
        if (this.fallbackPendingWebrtcToMse(pc, 'WebRTC 信令失败，正在回退 MSE')) return;
        this.closePeerConnection();
        if (!this.messageHandlers.has('mse')) this.scheduleReconnect('WebRTC 信令失败，正在重连');
      }
    });

    pc.addTransceiver('video', { direction: 'recvonly' });
    if (this.receiveAudio) pc.addTransceiver('audio', { direction: 'recvonly' });
    void pc
      .createOffer()
      .then(async (offer) => {
        if (pc !== this.pc || this.stopped) return;
        await pc.setLocalDescription(offer);
        if (pc !== this.pc || this.stopped) return;
        this.send('webrtc/offer', offer.sdp ?? '');
      })
      .catch(() => {
        if (pc !== this.pc || this.stopped) return;
        if (this.fallbackPendingWebrtcToMse(pc, 'WebRTC 协商失败，正在回退 MSE')) return;
        this.closePeerConnection();
        if (!this.messageHandlers.has('mse')) this.scheduleReconnect('WebRTC 协商失败，正在重连');
      });
  }

  private startPeerDisconnectedGrace(pc: RTCPeerConnection): void {
    if (this.peerDisconnectedTimer !== null) return;
    this.peerDisconnectedTimer = setTimeout(() => {
      this.peerDisconnectedTimer = null;
      if (pc !== this.pc || pc.connectionState !== 'disconnected') return;
      this.handlePeerTransportFailure(pc);
    }, this.peerDisconnectedGraceMs);
  }

  private handlePeerTransportFailure(pc: RTCPeerConnection): void {
    if (pc !== this.pc) return;
    this.clearPeerDisconnectedGrace();
    if (this.fallbackPendingWebrtcToMse(pc, 'WebRTC 连接失败，正在回退 MSE')) return;
    const wasActiveWebrtc = this.activeMode === 'webrtc';
    this.closePeerConnection();
    // 竞争阶段保留仍可用的 MSE；WebRTC 已胜出或是唯一模式时，按同一 wsUrl/profile 恢复。
    if (wasActiveWebrtc || !this.messageHandlers.has('mse')) {
      this.scheduleReconnect('WebRTC 连接中断，正在重连');
    }
  }

  private attachWebrtcPreview(pc: RTCPeerConnection): void {
    if (this.hiddenVideo) return;
    const tracks = pc
      .getTransceivers()
      .filter((transceiver) => transceiver.currentDirection === 'recvonly')
      .map((transceiver) => transceiver.receiver.track);
    const video = document.createElement('video');
    video.autoplay = true;
    video.muted = true;
    video.playsInline = true;
    video.srcObject = new MediaStream(tracks);
    video.addEventListener('loadeddata', () => void this.selectWebrtcIfPreferred(video, pc), { once: true });
    this.hiddenVideo = video;
    void this.playVideo(video);
  }

  private async selectWebrtcIfPreferred(video: HTMLVideoElement, pc: RTCPeerConnection): Promise<void> {
    if (pc !== this.pc || video !== this.hiddenVideo) return;
    const hasVideoEvidence = await this.waitForWebrtcVideoEvidence(video, pc);
    if (this.stopped || pc !== this.pc || video !== this.hiddenVideo || !this.messageHandlers.has('webrtc')) {
      return;
    }

    const stream = video.srcObject as MediaStream;
    if (!hasVideoEvidence) {
      const mseAvailable = this.messageHandlers.has('mse') && this.mediaSource !== null;
      this.closePeerConnection();
      if (mseAvailable) {
        this.activeMode = 'mse';
      } else {
        this.activeMode = null;
        this.scheduleReconnect('WebRTC 未产生可解码视频帧，正在重新连接');
      }
      video.srcObject = null;
      if (this.hiddenVideo === video) this.hiddenVideo = null;
      return;
    }

    // 自动双模式中，主 video 已经真实呈现 MSE 后本轮赢家不可再被迟到的 WebRTC 原地替换。
    // 这里只清理落败的 RTC 候选，不把它记入 failedModes/codecUnsupported，也不触发恢复回调。
    if (this.automaticWinner === 'mse') {
      this.closePeerConnection();
      return;
    }

    // 保留 go2rtc 的候选权重：音视频完整度、实际编码，再以 WebRTC 作为同等条件下的优先项。
    let rtcPriority = 0;
    let msePriority = 0;
    if (stream.getVideoTracks().length > 0) {
      const actualCodec = classifyInboundVideoCodec(this.latestRuntime?.inboundVideo ?? null);
      rtcPriority += actualCodec === 'h265' ? 0x240 : 0x220;
    }
    if (this.receiveAudio && stream.getAudioTracks().length > 0) rtcPriority += 0x102;
    if (/(?:hvc1|hev1)\./i.test(this.mseCodecs)) msePriority += 0x230;
    if (/avc1\./i.test(this.mseCodecs)) msePriority += 0x210;
    if (this.receiveAudio && /(?:mp4a\.|flac|opus)/i.test(this.mseCodecs)) msePriority += 0x101;

    if (rtcPriority >= msePriority) {
      // 隐藏 video 只能证明 WebRTC 解码链有帧，不能证明主 video 已经真正提交画面。
      // 主画面确认失败时不能复用已 detach 的旧 MediaSource，而是新建一次 MSE 管线。
      const replacingPlayingMse = this.playingReported && this.activeMode !== 'webrtc';
      if (replacingPlayingMse) {
        this.playingReported = false;
        this.clearHealthMonitor();
        this.options.onStage?.('buffering', '正在确认 WebRTC 主画面');
      }
      this.activeMode = 'webrtc';
      const committed = await this.commitWebrtcToPrimary(stream, pc, false);
      if (this.stopped || pc !== this.pc) return;

      if (committed) {
        this.finalizeWebrtcCommit();
      } else {
        const canFallbackToMse = this.canFallbackPendingWebrtcToMse(pc);
        this.recordPrimaryCommitFailure('initial', canFallbackToMse);
        if (this.isActiveAlternateAttempt()) {
          this.primarySourceTransition = null;
          this.closePeerConnection();
          this.activeMode = null;
          this.reportAlternateModeFailure('备用 WebRTC 主画面未能呈现，正在重新获取播放地址', 'webrtc');
        } else if (!this.fallbackPendingWebrtcToMse(pc, 'WebRTC 主画面未能呈现，正在回退 MSE')) {
          // 纯 WebRTC 场景保留一次干净的主 video 重置，处理 MediaStream 已解码但元素未出画面的情况。
          const resetCommitted = await this.commitWebrtcToPrimary(stream, pc, true);
          if (this.stopped || pc !== this.pc) return;
          if (resetCommitted) {
            this.finalizeWebrtcCommit();
          } else {
            this.recordPrimaryCommitFailure('reset-retry', false);
            this.primarySourceTransition = null;
            this.closePeerConnection();
            this.activeMode = null;
            this.scheduleReconnect('WebRTC 主画面重置后仍未呈现，正在重新连接');
          }
        }
      }
    } else {
      this.activeMode = 'mse';
      this.closePeerConnection();
    }
    if (video === this.hiddenVideo) {
      video.srcObject = null;
      this.hiddenVideo = null;
    }
  }

  private async commitWebrtcToPrimary(
    stream: MediaStream,
    pc: RTCPeerConnection,
    resetMediaElement: boolean,
  ): Promise<boolean> {
    if (this.stopped || pc !== this.pc) return false;
    this.primarySourceTransition = 'to-webrtc';
    this.pendingPlaying = null;
    if (resetMediaElement) {
      this.video.pause();
      this.video.removeAttribute('src');
      this.video.srcObject = null;
      this.video.load();
    }
    this.video.srcObject = stream;
    const playStarted = await this.playVideo(this.video);
    return playStarted && (await this.reportPlaying('webrtc'));
  }

  private finalizeWebrtcCommit(): void {
    this.primarySourceTransition = null;
    this.cleanupMseForWebrtc();
    this.expectedSocketClose = true;
    this.ws?.close();
    this.ws = null;
  }

  private async waitForWebrtcVideoEvidence(video: HTMLVideoElement, pc: RTCPeerConnection): Promise<boolean> {
    const deadline = Date.now() + this.webrtcEvidenceTimeoutMs;
    const attemptId = this.activeAttemptId;
    const stream = video.srcObject as MediaStream | null;
    const hasVideoTrack = Boolean(stream?.getVideoTracks().length);

    do {
      if (this.stopped || pc !== this.pc || video !== this.hiddenVideo) return false;
      if (hasVideoTrack && video.videoWidth > 0 && video.videoHeight > 0) {
        this.recordAttemptTiming(attemptId, 'firstWebrtcMediaMs');
        const inboundVideo = this.latestRuntime?.inboundVideo ?? null;
        this.lastWebrtcSelection = {
          confirmed: true,
          hiddenVideoWidth: video.videoWidth,
          hiddenVideoHeight: video.videoHeight,
          inboundVideo: inboundVideo ? { ...inboundVideo } : null,
        };
        return true;
      }
      await this.sampleWebrtcRuntime();
      if (this.stopped || pc !== this.pc || video !== this.hiddenVideo) return false;
      const inboundVideo = this.latestRuntime?.inboundVideo ?? null;
      const confirmed =
        hasVideoTrack && ((inboundVideo?.framesDecoded ?? 0) > 0 || (video.videoWidth > 0 && video.videoHeight > 0));
      this.lastWebrtcSelection = {
        confirmed,
        hiddenVideoWidth: video.videoWidth,
        hiddenVideoHeight: video.videoHeight,
        inboundVideo: inboundVideo ? { ...inboundVideo } : null,
      };
      if (confirmed) return true;
      const remaining = deadline - Date.now();
      if (remaining <= 0) return false;
      await new Promise((resolve) => setTimeout(resolve, Math.min(WEBRTC_EVIDENCE_SAMPLE_INTERVAL_MS, remaining)));
    } while (true);
  }

  /** WebRTC 已开始接管主 video 后失败时，旧 MediaSource 通常已 closed，必须新建 MSE 管线。 */
  private canFallbackPendingWebrtcToMse(pc: RTCPeerConnection): boolean {
    return (
      pc === this.pc &&
      !this.stopped &&
      !this.isActiveAlternateAttempt() &&
      this.primarySourceTransition === 'to-webrtc' &&
      !this.manualMode &&
      this.configuredModes().includes('mse') &&
      this.canStartMode('mse') &&
      !this.codecUnsupportedModes.has('mse') &&
      !this.failedModes.has('mse') &&
      this.modeSwitchCount < this.maxModeSwitches
    );
  }

  private fallbackPendingWebrtcToMse(pc: RTCPeerConnection, reason: string): boolean {
    if (!this.canFallbackPendingWebrtcToMse(pc)) return false;

    this.failedModes.add('webrtc');
    this.modeSwitchCount += 1;
    this.modeOverride = 'mse';
    this.automaticAlternatePending = true;
    this.lastSwitchReason = reason;
    this.disconnectTransport(false);
    // 跨过下一次 openSocket/resetAttemptState，直到新 MSE loadeddata 才解除切源错误隔离。
    this.primarySourceTransition = 'to-mse';
    this.stopped = false;
    this.externallyRecovering = false;
    this.options.onStage?.('reconnecting', reason);
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;
      this.openSocket();
    }, 0);
    return true;
  }

  private readonly onPrimaryLoadedData = (): void => {
    if (this.stopped || this.playingReported || !this.activeMode || this.primarySourceTransition === 'to-webrtc')
      return;
    if (this.primarySourceTransition === 'to-mse') {
      if (this.activeMode !== 'mse') return;
      this.primarySourceTransition = null;
    }
    const mode = this.activeMode;
    void this.reportPlaying(mode).then((committed) => {
      if (committed || this.stopped || this.playingReported || this.activeMode !== mode) return;
      if (mode === 'mse' && this.messageHandlers.has('webrtc')) {
        this.handleMsePipelineFailure('MSE 主画面未能呈现，正在尝试 WebRTC');
      } else {
        this.scheduleReconnect('主画面未能呈现，正在重新连接');
      }
    });
  };

  private readonly onPrimaryVideoError = (): void => {
    if (this.stopped || this.primarySourceTransition !== null) return;
    // MSE 候选已被淘汰且 WebRTC 仍在协商时，忽略撤销旧 blob/src 产生的迟到 MediaError。
    if (!this.activeMode && this.messageHandlers.has('webrtc')) return;
    const action = resolveGo2rtcPrimaryVideoErrorAction({
      activeMode: this.activeMode,
      playingReported: this.playingReported,
      mediaErrorCode: this.video.error?.code ?? null,
      mseCodecs: this.mseCodecs,
      inboundVideo: this.latestRuntime?.inboundVideo ?? null,
      primarySourceTransition: this.primarySourceTransition !== null,
    });
    if (action === 'ignore') return;
    if (action === 'h265-decode-failed-mse') {
      if (!this.playingReported && this.messageHandlers.has('webrtc')) {
        this.reportCompetingMseH265DecodeFailure('MSE H.265 解码异常，继续尝试 WebRTC');
        return;
      }
      this.reportExternalFailure(h265DecodeFailure('mse'));
      return;
    }
    if (this.activeMode === 'mse') {
      this.handleMsePipelineFailure('MSE 媒体异常，正在恢复播放');
      return;
    }
    this.scheduleReconnect('媒体解码异常，正在重连');
  };

  private reportPlaying(mode: Go2rtcPlayerMode): Promise<boolean> {
    const attemptId = this.activeAttemptId;
    if (this.pendingPlaying?.mode === mode && this.pendingPlaying.attemptId === attemptId) {
      return this.pendingPlaying.promise;
    }

    const promise = this.commitPlaying(mode, attemptId);
    this.pendingPlaying = { mode, attemptId, promise };
    const clearPending = () => {
      if (this.pendingPlaying?.promise === promise) this.pendingPlaying = null;
    };
    void promise.then(clearPending, clearPending);
    return promise;
  }

  private async commitPlaying(mode: Go2rtcPlayerMode, attemptId: number): Promise<boolean> {
    const expectedPc = mode === 'webrtc' ? this.pc : null;
    const expectedMediaSource = mode === 'mse' ? this.mediaSource : null;
    const isCurrentAttempt = () =>
      attemptId === this.activeAttemptId &&
      !this.externallyRecovering &&
      (mode === 'webrtc' ? expectedPc !== null && this.pc === expectedPc : this.mediaSource === expectedMediaSource) &&
      this.isModeStillActive(mode);
    if (this.playingReported || !isCurrentAttempt()) return false;
    const presented = await this.waitForPrimaryPresentation(isCurrentAttempt, () =>
      this.lockAutomaticWinner(mode, attemptId),
    );
    if (!presented || this.stopped || this.playingReported || !isCurrentAttempt()) return false;
    this.recordAttemptTiming(attemptId, 'primaryPresentedMs');
    await this.sampleWebrtcRuntime();
    if (this.stopped || this.playingReported || !isCurrentAttempt()) return false;
    // loadeddata 可能略早于 codec stats；短暂等待实际 inbound codec，避免用 SDP 或 UNKNOWN 确认 H264 缓存。
    if (mode === 'webrtc') {
      for (let attempt = 0; attempt < 5; attempt += 1) {
        if (classifyInboundVideoCodec(this.latestRuntime?.inboundVideo ?? null) !== 'unknown') break;
        await new Promise((resolve) => setTimeout(resolve, 100));
        if (this.stopped || !isCurrentAttempt()) return false;
        await this.sampleWebrtcRuntime();
      }
    }
    if (this.stopped || this.playingReported || !isCurrentAttempt()) return false;
    this.playingReported = true;
    this.hasProducedFirstFrame = true;
    this.automaticAlternatePending = false;
    this.internalReconnectCount = 0;
    this.codecUnsupportedModes.clear();
    this.clearFirstFrameProbe();
    this.recordAttemptTiming(attemptId, 'playingMs');
    const codec =
      mode === 'webrtc'
        ? asPlaybackCodec(classifyInboundVideoCodec(this.latestRuntime?.inboundVideo ?? null))
        : codecFromMse(this.mseCodecs);
    this.options.onPlaying?.({ mode, codec, runtime: this.latestRuntime, hevcLevel: this.latestHevcLevel });
    // 外层可能在 onPlaying 中因实际 codec/profile 不匹配而同步销毁或替换当前 Core。
    // destroy 已经清理过定时器，此处必须重新校验，避免随后又为失效 Core 创建永久 health interval。
    if (this.stopped || !this.playingReported || !this.isModeStillActive(mode)) return false;
    this.startHealthMonitor(mode);
    return true;
  }

  private isModeStillActive(mode: Go2rtcPlayerMode): boolean {
    if (this.activeMode !== mode || !this.messageHandlers.has(mode)) return false;
    return mode === 'webrtc' ? this.pc !== null : this.mediaSource !== null;
  }

  /** 只在同一自动双模式 attempt 的可见主画面真实呈现时锁定；手动/单模式/恢复模式保持原行为。 */
  private lockAutomaticWinner(mode: Go2rtcPlayerMode, attemptId: number): boolean {
    if (attemptId !== this.activeAttemptId) return false;
    const configuredModes = this.configuredModes();
    const isAutomaticDualMode =
      !this.manualMode && !this.modeOverride && configuredModes.includes('webrtc') && configuredModes.includes('mse');
    if (!isAutomaticDualMode) return true;
    if (this.automaticWinner && this.automaticWinner !== mode) return false;
    if (this.automaticWinner === mode) return true;

    this.automaticWinner = mode;
    if (mode === 'mse') this.closePeerConnection();
    return true;
  }

  private startFirstFrameProbe(): void {
    this.clearFirstFrameProbe();
    this.options.onStage?.('waiting-frame', '正在等待视频首帧');
    const initialMse = { ...this.mseProgress };
    this.lastMseProbe = { ...initialMse };
    const startedAt = this.connectStartedAt;
    const attemptId = this.activeAttemptId;
    let sampleInFlight = false;

    this.statsTimer = setInterval(() => {
      if (sampleInFlight) return;
      sampleInFlight = true;
      void this.sampleWebrtcRuntime()
        .then(() => {
          if (this.stopped || this.playingReported || attemptId !== this.activeAttemptId) return;
          const inbound = this.latestRuntime?.inboundVideo ?? null;
          const inboundProgressed = Boolean(
            inbound &&
              this.lastInbound &&
              (inbound.bytesReceived > this.lastInbound.bytesReceived ||
                inbound.packetsReceived > this.lastInbound.packetsReceived),
          );
          this.inboundProgressSamples = inboundProgressed ? this.inboundProgressSamples + 1 : 0;
          this.lastInbound = inbound;

          const mseProgressed =
            this.mseProgress.bytesReceived > this.lastMseProbe.bytesReceived &&
            this.mseProgress.bytesAppended > this.lastMseProbe.bytesAppended &&
            this.mseProgress.successfulAppends > this.lastMseProbe.successfulAppends &&
            this.mseProgress.bufferedEnd > this.lastMseProbe.bufferedEnd + 0.001;
          this.mseProgressSamples = mseProgressed ? this.mseProgressSamples + 1 : 0;
          this.lastMseProbe = { ...this.mseProgress };
        })
        .finally(() => {
          sampleInFlight = false;
        });
    }, 500);

    const firstFrameTimeoutMs =
      this.automaticAlternatePending || this.isActiveAlternateAttempt()
        ? Math.min(this.firstFrameTimeoutMs, this.alternateFirstFrameTimeoutMs)
        : this.firstFrameTimeoutMs;
    this.firstFrameTimer = setTimeout(() => {
      void this.handleFirstFrameTimeout(initialMse, startedAt, attemptId);
    }, firstFrameTimeoutMs);
  }

  private async handleFirstFrameTimeout(
    initialMse: MseFirstFrameProgress,
    startedAt: number,
    attemptId: number,
  ): Promise<void> {
    const isCurrentAttempt = () =>
      !this.stopped &&
      !this.playingReported &&
      startedAt === this.connectStartedAt &&
      attemptId === this.activeAttemptId;
    if (!isCurrentAttempt()) return;
    // loadeddata/隐藏预览已进入主画面提交时，交由可见性感知的呈现确认决定结果；
    // 不能让传输首帧计时器在 CSS 隐藏期间抢先重连或误判 H.265 解码失败。
    if (this.primarySourceTransition === 'to-webrtc' || this.primaryPresentationResolve !== null) {
      if (this.automaticAlternatePending || this.isActiveAlternateAttempt()) {
        this.reportAlternateModeFailure('备用播放方式未能在限定时间内呈现首帧，正在重新获取播放地址');
        return;
      }
      this.clearFirstFrameProbe();
      return;
    }
    await this.sampleWebrtcRuntime();
    if (!isCurrentAttempt()) return;
    if (this.automaticAlternatePending || this.isActiveAlternateAttempt()) {
      this.reportAlternateModeFailure('备用播放方式未能在限定时间内呈现首帧，正在重新获取播放地址');
      return;
    }
    const action = resolveGo2rtcFirstFrameTimeoutAction({
      inboundVideo: this.latestRuntime?.inboundVideo ?? null,
      inboundProgressSamples: this.inboundProgressSamples,
      mseProgressSamples: this.mseProgressSamples,
      mseCodecs: this.mseCodecs,
      initialMse,
      currentMse: this.mseProgress,
    });
    if (action === 'h265-decode-failed-webrtc') {
      this.reportExternalFailure(h265DecodeFailure('webrtc'));
      return;
    }
    if (action === 'h265-decode-failed-mse') {
      this.reportExternalFailure(h265DecodeFailure('mse'));
      return;
    }

    this.scheduleReconnect('未收到可播放首帧，正在重新连接');
  }

  private startHealthMonitor(mode: Go2rtcPlayerMode): void {
    this.clearHealthMonitor();
    if (this.healthCheckIntervalMs <= 0) return;
    const healthGeneration = this.healthProbeGeneration;
    this.healthBaseline = this.createHealthSample();
    this.startPresentationMonitor();
    this.healthTimer = setInterval(() => {
      void this.checkPlaybackHealth(mode, healthGeneration);
    }, this.healthCheckIntervalMs);
  }

  private async checkPlaybackHealth(mode: Go2rtcPlayerMode, healthGeneration: number): Promise<void> {
    if (this.healthSampleInFlightGeneration === healthGeneration) return;
    if (
      this.stopped ||
      this.externallyRecovering ||
      !this.playingReported ||
      this.activeMode !== mode ||
      healthGeneration !== this.healthProbeGeneration
    ) {
      return;
    }

    if (
      this.video.paused ||
      this.video.ended ||
      (typeof window !== 'undefined' && window.getComputedStyle(this.video).visibility === 'hidden') ||
      (typeof document !== 'undefined' && document.visibilityState === 'hidden')
    ) {
      this.healthBaseline = this.createHealthSample();
      this.consecutiveHealthFailures = 0;
      return;
    }

    const frameCallbackSupported = this.supportsVideoFrameCallback();
    const presentationFreshBeforeSample =
      frameCallbackSupported &&
      this.lastPresentedFrameAt > 0 &&
      Date.now() - this.lastPresentedFrameAt <= this.healthCheckIntervalMs * this.healthFailureThreshold;

    this.healthSampleInFlightGeneration = healthGeneration;
    try {
      // rVFC 已直接证明主 video 正在持续呈现时，无需每轮再调用 WebRTC getStats。
      // 呈现证据陈旧或浏览器不支持 rVFC 时仍保留原有 stats/时间轴健康检测。
      if (mode === 'webrtc' && !presentationFreshBeforeSample) await this.sampleWebrtcRuntime();
      if (
        this.stopped ||
        this.externallyRecovering ||
        !this.playingReported ||
        this.activeMode !== mode ||
        healthGeneration !== this.healthProbeGeneration
      ) {
        return;
      }

      const current = this.createHealthSample();
      const presentationFresh =
        !frameCallbackSupported ||
        (this.lastPresentedFrameAt > 0 &&
          Date.now() - this.lastPresentedFrameAt <= this.healthCheckIntervalMs * this.healthFailureThreshold);
      const mediaProgressed =
        mode === 'webrtc' && presentationFreshBeforeSample && presentationFresh
          ? true
          : this.healthBaseline
            ? hasGo2rtcPlaybackProgress(mode, this.healthBaseline, current)
            : true;
      const progressed = mediaProgressed && presentationFresh;
      this.healthBaseline = current;
      this.consecutiveHealthFailures = !presentationFresh
        ? this.healthFailureThreshold
        : progressed
          ? 0
          : this.consecutiveHealthFailures + 1;
      if (this.consecutiveHealthFailures >= this.healthFailureThreshold) {
        // 健康探针永远只重连当前 wsUrl；不产生 h265-decode，不请求外层切换 profile。
        this.scheduleReconnect('播放画面停滞，正在重新连接');
      }
    } finally {
      if (this.healthSampleInFlightGeneration === healthGeneration) {
        this.healthSampleInFlightGeneration = null;
      }
    }
  }

  private createHealthSample(): Go2rtcPlaybackHealthSample {
    const inbound = this.latestRuntime?.inboundVideo ?? null;
    return {
      currentTime: this.video.currentTime,
      framesDecoded: inbound?.framesDecoded ?? null,
      framesRendered: inbound?.framesRendered ?? null,
    };
  }

  private waitForPrimaryPresentation(isCurrentAttempt: () => boolean, onPresented?: () => boolean): Promise<boolean> {
    this.clearPrimaryPresentationWait();
    const video = this.video as VideoWithFrameCallbacks;
    const requestFrame = video.requestVideoFrameCallback;
    return new Promise<boolean>((resolve) => {
      this.primaryPresentationResolve = resolve;
      let visiblyActive = false;
      let visibilityObserver: MutationObserver | null = null;

      const clearDeadline = () => {
        if (this.primaryPresentationTimer !== null) clearTimeout(this.primaryPresentationTimer);
        this.primaryPresentationTimer = null;
      };
      const cancelPendingFrame = () => {
        if (this.primaryPresentationFrameId === null) return;
        try {
          video.cancelVideoFrameCallback?.(this.primaryPresentationFrameId);
        } catch {
          // 媒体源或可见性变化时浏览器可能已经释放旧回调。
        }
        this.primaryPresentationFrameId = null;
      };
      const finish = (success: boolean) => {
        if (this.primaryPresentationResolve !== resolve) return;
        const presented = success && (!onPresented || onPresented());
        this.primaryPresentationCleanup?.();
        this.primaryPresentationCleanup = null;
        this.primaryPresentationResolve = null;
        clearDeadline();
        cancelPendingFrame();
        if (presented) this.lastPresentedFrameAt = Date.now();
        resolve(presented);
      };

      const armDeadline = () => {
        clearDeadline();
        this.primaryPresentationTimer = setTimeout(() => {
          if (!this.isPrimaryPresentationVisible()) {
            visiblyActive = false;
            clearDeadline();
            cancelPendingFrame();
            return;
          }
          finish(false);
        }, this.primaryFrameTimeoutMs);
      };

      const checkLegacyPresentation = () => {
        if (!visiblyActive || !this.isPrimaryPresentationVisible()) return;
        const presented = isCurrentAttempt() && video.readyState >= 2 && video.videoWidth > 0 && video.videoHeight > 0;
        if (presented) finish(true);
      };

      const beginVisibleWait = () => {
        armDeadline();
        if (typeof requestFrame !== 'function') {
          checkLegacyPresentation();
          return;
        }
        if (this.primaryPresentationFrameId !== null) return;
        try {
          const frameId = requestFrame.call(video, () => {
            this.primaryPresentationFrameId = null;
            if (!isCurrentAttempt()) {
              finish(false);
              return;
            }
            if (!this.isPrimaryPresentationVisible()) {
              visiblyActive = false;
              clearDeadline();
              return;
            }
            finish(true);
          });
          if (this.primaryPresentationResolve === resolve) this.primaryPresentationFrameId = frameId;
          else video.cancelVideoFrameCallback?.(frameId);
        } catch {
          finish(false);
        }
      };

      const syncVisibility = () => {
        const nextVisible = this.isPrimaryPresentationVisible();
        if (nextVisible === visiblyActive) return;
        visiblyActive = nextVisible;
        if (nextVisible) beginVisibleWait();
        else {
          clearDeadline();
          cancelPendingFrame();
        }
      };

      if (typeof requestFrame !== 'function') {
        video.addEventListener('loadeddata', checkLegacyPresentation);
        video.addEventListener('playing', checkLegacyPresentation);
        video.addEventListener('timeupdate', checkLegacyPresentation);
      }
      if (typeof MutationObserver !== 'undefined') {
        visibilityObserver = new MutationObserver(syncVisibility);
        let element: HTMLElement | null = video;
        while (element) {
          visibilityObserver.observe(element, {
            attributes: true,
            attributeFilter: ['class', 'style', 'hidden'],
          });
          element = element.parentElement;
        }
      }
      if (typeof document !== 'undefined') document.addEventListener('visibilitychange', syncVisibility);
      this.primaryPresentationCleanup = () => {
        visibilityObserver?.disconnect();
        if (typeof document !== 'undefined') document.removeEventListener('visibilitychange', syncVisibility);
        if (typeof requestFrame !== 'function') {
          video.removeEventListener('loadeddata', checkLegacyPresentation);
          video.removeEventListener('playing', checkLegacyPresentation);
          video.removeEventListener('timeupdate', checkLegacyPresentation);
        }
      };
      syncVisibility();
    });
  }

  private isPrimaryPresentationVisible(): boolean {
    if (typeof document !== 'undefined' && document.visibilityState === 'hidden') return false;
    if (typeof window === 'undefined') return true;
    let element: HTMLElement | null = this.video;
    while (element) {
      const style = window.getComputedStyle(element);
      if (style.display === 'none' || style.visibility === 'hidden' || style.visibility === 'collapse') return false;
      element = element.parentElement;
    }
    return true;
  }

  private recordAttemptTiming(attemptId: number, field: Go2rtcAttemptTimingField): void {
    const timing = this.attemptTiming;
    if (!timing || timing.attemptId !== attemptId || timing[field] !== undefined) return;
    timing[field] = Math.max(Date.now() - timing.startedAt, 0);
  }

  private snapshotAttemptTiming(): Go2rtcPlayerAttemptTimingDiagnostics | null {
    const timing = this.attemptTiming;
    if (!timing) return null;
    const mediaTimings = [timing.firstMseByteMs, timing.firstWebrtcMediaMs].filter(
      (value): value is number => value !== undefined,
    );
    const firstMediaMs = mediaTimings.length > 0 ? Math.min(...mediaTimings) : undefined;
    return {
      attemptId: timing.attemptId,
      ...(timing.socketOpenMs !== undefined ? { socketOpenMs: timing.socketOpenMs } : {}),
      ...(timing.firstMseByteMs !== undefined ? { firstMseByteMs: timing.firstMseByteMs } : {}),
      ...(timing.firstWebrtcMediaMs !== undefined ? { firstWebrtcMediaMs: timing.firstWebrtcMediaMs } : {}),
      ...(firstMediaMs !== undefined ? { firstMediaMs } : {}),
      ...(timing.primaryPresentedMs !== undefined ? { primaryPresentedMs: timing.primaryPresentedMs } : {}),
      ...(timing.playingMs !== undefined ? { playingMs: timing.playingMs } : {}),
    };
  }

  private readVideoPlaybackQuality(): Go2rtcVideoPlaybackQualityDiagnostics | null {
    const getVideoPlaybackQuality = (this.video as VideoWithPlaybackQuality).getVideoPlaybackQuality;
    if (typeof getVideoPlaybackQuality !== 'function') return null;
    try {
      const quality = getVideoPlaybackQuality.call(this.video);
      const nullableNumber = (value: unknown): number | null => {
        const number = Number(value);
        return value !== null && value !== undefined && value !== '' && Number.isFinite(number) ? number : null;
      };
      return {
        totalVideoFrames: nullableNumber(quality?.totalVideoFrames),
        droppedVideoFrames: nullableNumber(quality?.droppedVideoFrames),
        corruptedVideoFrames: nullableNumber(quality?.corruptedVideoFrames),
      };
    } catch {
      return null;
    }
  }

  private snapshotVideoElement(): Go2rtcVideoElementDiagnostics {
    return {
      readyState: this.video.readyState,
      networkState: this.video.networkState,
      currentTime: this.video.currentTime,
      videoWidth: this.video.videoWidth,
      videoHeight: this.video.videoHeight,
      playbackRate: this.video.playbackRate,
      playbackQuality: this.readVideoPlaybackQuality(),
      mediaErrorCode: this.video.error?.code ?? null,
    };
  }

  private recordPrimaryCommitFailure(phase: 'initial' | 'reset-retry', mseRestorable: boolean): void {
    const inboundVideo = this.latestRuntime?.inboundVideo ?? null;
    this.lastPrimaryCommitFailure = {
      phase,
      mseRestorable,
      video: this.snapshotVideoElement(),
      inboundVideo: inboundVideo ? { ...inboundVideo } : null,
    };
  }

  private clearPrimaryPresentationWait(): void {
    const video = this.video as VideoWithFrameCallbacks;
    if (this.primaryPresentationTimer !== null) clearTimeout(this.primaryPresentationTimer);
    if (this.primaryPresentationFrameId !== null) {
      try {
        video.cancelVideoFrameCallback?.(this.primaryPresentationFrameId);
      } catch {
        // 某些 WebView 在媒体源切换后会拒绝取消旧帧回调。
      }
    }
    this.primaryPresentationTimer = null;
    this.primaryPresentationFrameId = null;
    this.primaryPresentationCleanup?.();
    this.primaryPresentationCleanup = null;
    const resolve = this.primaryPresentationResolve;
    this.primaryPresentationResolve = null;
    resolve?.(false);
  }

  private startPresentationMonitor(): void {
    this.clearPresentationMonitor();
    const video = this.video as VideoWithFrameCallbacks;
    const requestFrame = video.requestVideoFrameCallback;
    if (typeof requestFrame !== 'function') return;
    const generation = this.presentationProbeGeneration;

    const requestNext = () => {
      if (this.stopped || !this.playingReported || generation !== this.presentationProbeGeneration) return;
      try {
        this.presentationMonitorFrameId = requestFrame.call(video, () => {
          this.presentationMonitorFrameId = null;
          if (this.stopped || !this.playingReported || generation !== this.presentationProbeGeneration) return;
          this.lastPresentedFrameAt = Date.now();
          this.presentationMonitorTimer = setTimeout(requestNext, PRESENTATION_SAMPLE_INTERVAL_MS);
        });
      } catch {
        this.presentationMonitorFrameId = null;
      }
    };
    requestNext();
  }

  private clearPresentationMonitor(): void {
    ++this.presentationProbeGeneration;
    if (this.presentationMonitorTimer !== null) clearTimeout(this.presentationMonitorTimer);
    this.presentationMonitorTimer = null;
    const video = this.video as VideoWithFrameCallbacks;
    if (this.presentationMonitorFrameId !== null) {
      try {
        video.cancelVideoFrameCallback?.(this.presentationMonitorFrameId);
      } catch {
        // 媒体源已被浏览器释放时无需继续处理。
      }
    }
    this.presentationMonitorFrameId = null;
  }

  private supportsVideoFrameCallback(): boolean {
    return typeof (this.video as VideoWithFrameCallbacks).requestVideoFrameCallback === 'function';
  }

  private async sampleWebrtcRuntime(): Promise<void> {
    const pc = this.pc;
    const attemptId = this.activeAttemptId;
    if (!pc) {
      if (this.mseCodecs) {
        this.latestHevcLevel = buildHevcLevelCompatibilityDiagnostics(this.mseCodecs, null, null);
      }
      return;
    }
    const runtime = await readWebrtcRuntimeDiagnostics(pc);
    if (!runtime || pc !== this.pc || attemptId !== this.activeAttemptId) return;
    this.latestRuntime = runtime;
    const inboundVideo = runtime.inboundVideo;
    if (
      inboundVideo &&
      (inboundVideo.bytesReceived > 0 ||
        inboundVideo.packetsReceived > 0 ||
        inboundVideo.framesReceived > 0 ||
        inboundVideo.framesDecoded > 0)
    ) {
      this.recordAttemptTiming(attemptId, 'firstWebrtcMediaMs');
    }
    const payloadType = runtime.inboundVideo?.codecPayloadType;
    this.latestHevcLevel = buildHevcLevelCompatibilityDiagnostics(
      this.mseCodecs,
      pc.localDescription?.sdp,
      pc.remoteDescription?.sdp,
      payloadType,
    );
  }

  private reportExternalFailure(event: Go2rtcPlayerFailureEvent): void {
    if (this.stopped || this.externallyRecovering) return;
    if (this.isActiveAlternateAttempt() && !this.playingReported && event.kind !== 'source') {
      this.reportAlternateModeFailure('备用播放方式启动失败，正在重新获取播放地址', event.mode);
      return;
    }
    this.externallyRecovering = true;
    this.clearFirstFrameProbe();
    this.clearPrimaryPresentationWait();
    this.clearHealthMonitor();
    this.clearPeerDisconnectedGrace();
    this.clearWebsocketOpenWatchdog();
    this.options.onFailure?.(event);
  }

  private scheduleReconnect(message: string): void {
    if (this.stopped || this.externallyRecovering || this.reconnectTimer !== null) return;
    const hadFirstFrame = this.hasProducedFirstFrame || this.playingReported;
    if (hadFirstFrame && this.options.onActiveRecovery) {
      const failedMode = this.activeMode;
      const diagnostics = this.getDiagnosticsSnapshot();
      this.video.pause();
      this.disconnectTransport(false);
      this.stopped = true;
      this.options.onStage?.('reconnecting', message);
      this.options.onActiveRecovery({ message, failedMode, diagnostics });
      return;
    }
    if (this.isActiveAlternateAttempt() && !hadFirstFrame) {
      this.reportAlternateModeFailure('备用播放方式连接失败，正在重新获取播放地址');
      return;
    }
    if (this.trySwitchToAlternateMode(message)) return;
    if (this.automaticAlternatePending && !this.playingReported) {
      this.reportAlternateModeFailure('备用播放方式连接失败，正在重新获取播放地址');
      return;
    }
    if (this.internalReconnectCount >= this.maxInternalReconnects) {
      const mode =
        this.activeMode ??
        (this.messageHandlers.size === 1 ? (this.messageHandlers.keys().next().value ?? 'unknown') : 'unknown');
      this.reportExternalFailure({
        kind: 'source',
        message: '播放器内部重连已用尽，正在重新获取播放地址',
        report: { type: 'UNKNOWN', code: 'UNKNOWN_STREAM_ERROR', mode },
        mode,
      });
      return;
    }

    this.internalReconnectCount += 1;
    this.disconnectTransport(false);
    this.stopped = false;
    this.options.onStage?.('reconnecting', message);
    // 首帧后的断流按 go2rtc 行为立即恢复；首帧前仍把尝试节流到既有 30 秒窗口。
    const delay = hadFirstFrame ? 0 : Math.max(this.reconnectTimeoutMs - (Date.now() - this.connectStartedAt), 0);
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;
      this.openSocket();
    }, delay);
  }

  private disconnectTransport(clearVideo: boolean): void {
    ++this.generation;
    this.attemptTiming = null;
    this.primarySourceTransition = null;
    this.clearTimers();
    this.expectedSocketClose = true;
    this.ws?.close();
    this.ws = null;
    this.closePeerConnection();
    this.cleanupMse();
    this.messageHandlers.clear();
    this.onBinaryData = null;
    if (clearVideo) {
      this.video.pause();
      this.video.removeAttribute('src');
      this.video.srcObject = null;
      this.video.load();
    }
  }

  private trySwitchToAlternateMode(reason: string): boolean {
    if (!this.playingReported || !this.activeMode || this.manualMode || this.modeSwitchCount >= this.maxModeSwitches) {
      return false;
    }
    const failedMode = this.activeMode;
    const alternateMode = this.configuredModes().find(
      (mode) => mode !== failedMode && !this.failedModes.has(mode) && this.canStartMode(mode),
    );
    if (!alternateMode) return false;

    this.failedModes.add(failedMode);
    this.modeSwitchCount += 1;
    this.modeOverride = alternateMode;
    this.automaticAlternatePending = true;
    this.lastSwitchReason = `${this.modeLabel(failedMode)}异常，正在切换到${this.modeLabel(alternateMode)}：${reason}`;
    this.disconnectTransport(false);
    this.stopped = false;
    this.externallyRecovering = false;
    this.options.onStage?.('reconnecting', this.lastSwitchReason);
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;
      this.openSocket();
    }, 0);
    return true;
  }

  private configuredModes(): Go2rtcPlayerMode[] {
    return [...new Set(this.options.modes.filter((mode) => mode === 'webrtc' || mode === 'mse'))];
  }

  private canStartMode(mode: Go2rtcPlayerMode): boolean {
    return mode === 'webrtc' ? typeof RTCPeerConnection !== 'undefined' : this.getMediaSourceConstructor() !== null;
  }

  private isActiveAlternateAttempt(): boolean {
    return this.options.attemptKind === 'active-alternate';
  }

  private modeLabel(mode: Go2rtcPlayerMode): string {
    return mode === 'webrtc' ? 'WebRTC' : 'MSE';
  }

  private resetModeRecoverySession(): void {
    this.modeOverride = null;
    this.manualMode = null;
    this.modeSwitchCount = 0;
    this.automaticAlternatePending = false;
    this.lastSwitchReason = null;
    this.failedModes.clear();
    this.lastPresentedFrameAt = 0;
  }

  private resetFailureContext(): void {
    this.lastWebrtcSelection = null;
    this.lastPrimaryCommitFailure = null;
    this.lastMseFailure = null;
  }

  private reportAlternateModeFailure(message: string, failedMode?: Go2rtcPlayerMode | 'unknown'): void {
    const mode = failedMode ?? this.modeOverride ?? this.activeMode ?? 'unknown';
    this.reportExternalFailure({
      kind: 'source',
      message,
      report: { type: 'UNKNOWN', code: 'UNKNOWN_STREAM_ERROR', mode },
      mode,
    });
  }

  private closePeerConnection(): void {
    this.clearPeerDisconnectedGrace();
    const pc = this.pc;
    this.pc = null;
    this.messageHandlers.delete('webrtc');
    this.latestRuntime = null;
    this.lastInbound = null;
    this.inboundProgressSamples = 0;
    this.latestHevcLevel = this.mseCodecs ? buildHevcLevelCompatibilityDiagnostics(this.mseCodecs, null, null) : null;
    if (this.hiddenVideo) {
      this.hiddenVideo.srcObject = null;
      this.hiddenVideo = null;
    }
    if (!pc) return;
    pc.getSenders().forEach((sender) => sender.track?.stop());
    pc.close();
  }

  private cleanupMseForWebrtc(): void {
    this.onBinaryData = null;
    this.messageHandlers.delete('mse');
    this.sourceBuffer = null;
    this.mediaSource = null;
    this.lastMseTrimAt = null;
    this.video.playbackRate = 1;
    if (this.objectUrl) {
      URL.revokeObjectURL(this.objectUrl);
      this.objectUrl = '';
    }
  }

  private cleanupMse(): void {
    this.cleanupMseForWebrtc();
    this.mseCodecs = '';
    this.latestHevcLevel = null;
  }

  private resetAttemptState(): void {
    this.clearHealthMonitor();
    this.attemptTiming = null;
    this.externallyRecovering = false;
    this.expectedSocketClose = false;
    this.playingReported = false;
    this.hasProducedFirstFrame = false;
    this.activeMode = null;
    this.activeAttemptId = 0;
    this.attemptedModes = [];
    this.codecUnsupportedModes.clear();
    this.latestRuntime = null;
    this.latestHevcLevel = null;
    this.lastInbound = null;
    this.inboundProgressSamples = 0;
    this.lastMseProbe = emptyMseProgress();
    this.mseProgressSamples = 0;
    this.mseProgress = emptyMseProgress();
    this.lastMseTrimAt = null;
    this.pendingPlaying = null;
    this.automaticWinner = null;
    if (this.primarySourceTransition !== 'to-mse') this.primarySourceTransition = null;
  }

  private clearFirstFrameProbe(): void {
    if (this.firstFrameTimer !== null) clearTimeout(this.firstFrameTimer);
    if (this.statsTimer !== null) clearInterval(this.statsTimer);
    this.firstFrameTimer = null;
    this.statsTimer = null;
  }

  private startWebsocketOpenWatchdog(generation: number, ws: WebSocket): void {
    this.clearWebsocketOpenWatchdog();
    this.websocketOpenTimer = setTimeout(
      () => {
        this.websocketOpenTimer = null;
        if (this.isStale(generation, ws) || ws.readyState === WebSocket.OPEN) return;
        this.scheduleReconnect('流媒体信令连接超时，正在重连');
      },
      Math.max(this.websocketOpenTimeoutMs, 0),
    );
  }

  private clearWebsocketOpenWatchdog(): void {
    if (this.websocketOpenTimer !== null) clearTimeout(this.websocketOpenTimer);
    this.websocketOpenTimer = null;
  }

  private clearPeerDisconnectedGrace(): void {
    if (this.peerDisconnectedTimer !== null) clearTimeout(this.peerDisconnectedTimer);
    this.peerDisconnectedTimer = null;
  }

  private clearHealthMonitor(): void {
    ++this.healthProbeGeneration;
    if (this.healthTimer !== null) clearInterval(this.healthTimer);
    this.healthTimer = null;
    this.healthSampleInFlightGeneration = null;
    this.healthBaseline = null;
    this.consecutiveHealthFailures = 0;
    this.clearPresentationMonitor();
  }

  private clearTimers(): void {
    this.clearPrimaryPresentationWait();
    this.clearFirstFrameProbe();
    this.clearWebsocketOpenWatchdog();
    this.clearPeerDisconnectedGrace();
    this.clearHealthMonitor();
    if (this.reconnectTimer !== null) clearTimeout(this.reconnectTimer);
    this.reconnectTimer = null;
  }

  private supportedMseCodecs(MediaSourceCtor: typeof MediaSource): string {
    let codecs = [...GO2RTC_CODECS];
    // 与 go2rtc 官方 VideoRTC 一致：Safari 对音频 codec 的 isTypeSupported 结果并不可靠。
    const safari = window.navigator.userAgent.match(/Version\/(\d+).+Safari/);
    if (safari) {
      const skip = safari[1] < '13' ? 'mp4a.40.2' : safari[1] < '14' ? 'flac' : 'opus';
      const index = codecs.indexOf(skip);
      if (index >= 0) codecs = codecs.slice(0, index);
    }
    if (!this.receiveAudio) codecs = codecs.filter((codec) => !GO2RTC_AUDIO_CODECS.has(codec));
    return codecs
      .filter((codec) => {
        try {
          return MediaSourceCtor.isTypeSupported(`video/mp4; codecs="${codec}"`);
        } catch {
          return false;
        }
      })
      .join();
  }

  private getMediaSourceConstructor(): typeof MediaSource | null {
    return ((window as any).ManagedMediaSource ?? window.MediaSource ?? null) as typeof MediaSource | null;
  }

  private isStale(generation: number, ws: WebSocket): boolean {
    return generation !== this.generation || ws !== this.ws || this.stopped;
  }

  private async playVideo(video: HTMLVideoElement): Promise<boolean> {
    try {
      await video.play();
      return true;
    } catch {
      if (!video.muted) {
        video.muted = true;
        try {
          await video.play();
          return true;
        } catch {
          return false;
        }
      }
      return false;
    }
  }
}
