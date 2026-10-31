<template>
  <div class="webrtc-player" @click="closeControlMenu">
    <video
      ref="videoARef"
      class="webrtc-video"
      :class="[`is-${displayMode}`, { 'is-active': activeVideoSlot === 0 }]"
      autoplay
      muted
      playsinline
    ></video>

    <video
      ref="videoBRef"
      class="webrtc-video"
      :class="[`is-${displayMode}`, { 'is-active': activeVideoSlot === 1 }]"
      autoplay
      muted
      playsinline
    ></video>

    <div v-if="status === 'loading' && !hasPresentedFrame" class="status-overlay">
      <svg class="spin-icon" viewBox="0 0 50 50" aria-hidden="true">
        <circle
          cx="25"
          cy="25"
          r="20"
          fill="none"
          stroke="currentColor"
          stroke-width="5"
          stroke-dasharray="80 40"
          stroke-linecap="round"
        />
      </svg>
      <span>{{ loadingStatusText }}</span>
    </div>

    <div v-if="status === 'loading' && hasPresentedFrame" class="reconnect-tip">
      <span class="reconnect-dot"></span>{{ loadingStatusText }}
    </div>

    <div v-if="status === 'error' && hasPresentedFrame" class="reconnect-tip reconnect-tip--error">
      <span class="reconnect-dot reconnect-dot--error"></span>
      <span class="reconnect-error-msg" :title="errorMsg">{{ errorMsg }}</span>
      <span v-if="autoRetryEnabled && retryCountdownSeconds > 0">{{ retryHint }}</span>
    </div>

    <div v-if="status === 'idle'" class="status-overlay">
      <svg class="idle-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.2">
        <path d="M15 10l4.553-2.277A1 1 0 0121 8.649v6.702a1 1 0 01-1.447.894L15 14" />
        <rect x="2" y="6" width="13" height="12" rx="2" />
      </svg>
      <span>暂无信号</span>
    </div>

    <div v-if="status === 'error' && !hasPresentedFrame" class="status-overlay status-error">
      <svg class="error-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
        <path stroke-linecap="round" d="M12 9v4m0 4h.01" />
        <path d="M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z" />
      </svg>
      <span class="error-msg" :title="errorMsg">{{ errorMsg }}</span>
      <span v-if="autoRetryEnabled && retryCountdownSeconds > 0" class="error-retry-hint">{{ retryHint }}</span>
    </div>

    <section v-if="videoInfoVisible" class="video-info-panel" aria-label="当前视频信息" @click.stop @dblclick.stop>
      <header class="video-info-header">
        <span>当前视频信息</span>
        <button type="button" class="video-info-close" title="关闭" aria-label="关闭视频信息" @click="closeVideoInfo">
          ×
        </button>
      </header>
      <div class="video-info-content">
        <div v-if="diagnosticsManualCopyVisible" class="video-info-manual-copy">
          <p>自动复制失败，诊断文本已全选，请按 Ctrl/Cmd+C 复制。</p>
          <textarea
            ref="diagnosticsManualCopyRef"
            class="video-info-diagnostics-text"
            :value="diagnosticsManualCopyText"
            readonly
            spellcheck="false"
            aria-label="只读播放诊断文本"
            @focus="selectManualDiagnostics"
          ></textarea>
        </div>
        <template v-else>
          <div v-if="videoInfoSnapshot" class="video-info-diagnosis" :class="`is-${videoInfoSnapshot.diagnosis.level}`">
            <strong>{{ videoInfoSnapshot.diagnosis.title }}</strong>
            <p>{{ videoInfoSnapshot.diagnosis.conclusion }}</p>
            <p v-if="videoInfoSnapshot.diagnosis.suggestions[0]" class="video-info-diagnosis-suggestion">
              建议：{{ videoInfoSnapshot.diagnosis.suggestions[0] }}
            </p>
          </div>
          <dl v-if="videoInfoSnapshot" class="video-info-list">
            <div>
              <dt>摄像头</dt>
              <dd :title="videoInfoSnapshot.cameraId">{{ videoInfoSnapshot.cameraId }}</dd>
            </div>
            <div>
              <dt>播放状态</dt>
              <dd>{{ videoInfoSnapshot.status }}</dd>
            </div>
            <div>
              <dt>码流</dt>
              <dd>{{ videoInfoSnapshot.stream }}</dd>
            </div>
            <div>
              <dt>播放方式</dt>
              <dd>{{ videoInfoSnapshot.mode }}</dd>
            </div>
            <div>
              <dt>视频编码</dt>
              <dd>{{ videoInfoSnapshot.codec }}</dd>
            </div>
            <div>
              <dt>分辨率</dt>
              <dd>{{ videoInfoSnapshot.resolution }}</dd>
            </div>
            <div>
              <dt>视频源</dt>
              <dd>{{ videoInfoSnapshot.profile }}</dd>
            </div>
            <div>
              <dt>恢复次数</dt>
              <dd>{{ videoInfoSnapshot.reconnectCount }}</dd>
            </div>
          </dl>
          <p v-if="videoInfoSnapshot?.error" class="video-info-error" :title="videoInfoSnapshot.error">
            {{ videoInfoSnapshot.error }}
          </p>
        </template>
      </div>
      <footer class="video-info-actions">
        <button v-if="diagnosticsManualCopyVisible" type="button" @click="closeManualDiagnostics">返回信息</button>
        <button v-else type="button" @click="refreshCurrentVideoInfo">刷新</button>
        <button
          type="button"
          @click="diagnosticsManualCopyVisible ? selectManualDiagnostics() : copyPlaybackDiagnostics()"
        >
          {{ diagnosticsCopied ? '已复制' : diagnosticsManualCopyVisible ? '重新全选' : '复制诊断' }}
        </button>
      </footer>
    </section>

    <div
      v-if="cameraId && status !== 'idle'"
      class="player-controls"
      aria-label="视频操作"
      @click.stop
      @dblclick.stop
      @keydown.esc="closeControlMenu"
    >
      <div v-if="showStreamSwitch" class="control-menu-wrap">
        <button
          type="button"
          class="control-button stream-control-button"
          :class="{ 'is-selected': openControlMenu === 'stream' }"
          :title="streamSwitchTitle"
          aria-haspopup="menu"
          :aria-expanded="openControlMenu === 'stream'"
          @click="toggleControlMenu('stream')"
        >
          <span>{{ streamControlLabel }}</span>
          <svg class="control-caret" viewBox="0 0 12 12" aria-hidden="true"><path d="m3 7 3-3 3 3" /></svg>
        </button>
        <div v-if="openControlMenu === 'stream'" class="control-menu stream-menu" role="menu" aria-label="视频码流">
          <button
            v-for="option in streamControlOptions"
            :key="option.value"
            type="button"
            role="menuitemradio"
            :aria-checked="streamPreference === option.value"
            :class="{ 'is-active': streamPreference === option.value }"
            :data-value="option.value"
            @click="selectStreamType(option.value)"
          >
            <span class="menu-check">✓</span>{{ option.label }}
          </button>
        </div>
      </div>

      <div class="control-menu-wrap">
        <button
          type="button"
          class="control-button protocol-control-button"
          :class="{ 'is-selected': openControlMenu === 'protocol' }"
          :title="protocolSwitchTitle"
          aria-haspopup="menu"
          :aria-expanded="openControlMenu === 'protocol'"
          @click="toggleControlMenu('protocol')"
        >
          <span>{{ protocolControlLabel }}</span>
          <svg class="control-caret" viewBox="0 0 12 12" aria-hidden="true"><path d="m3 7 3-3 3 3" /></svg>
        </button>
        <div
          v-if="openControlMenu === 'protocol'"
          class="control-menu protocol-menu"
          role="menu"
          aria-label="视频播放方式"
        >
          <button
            v-for="option in protocolControlOptions"
            :key="option.value"
            type="button"
            role="menuitemradio"
            :aria-checked="modePreference === option.value"
            :class="{ 'is-active': modePreference === option.value }"
            :data-value="option.value"
            @click="selectPlaybackMode(option.value)"
          >
            <span class="menu-check">✓</span>{{ option.label }}
          </button>
        </div>
      </div>

      <button
        type="button"
        class="control-button display-mode-button"
        :title="displayModeTitle"
        :aria-label="displayModeTitle"
        @click="toggleDisplayMode"
      >
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <rect x="3" y="5" width="18" height="14" rx="1.5" />
          <rect v-if="displayMode === 'contain'" x="5.5" y="8" width="13" height="8" rx="0.8" />
          <rect v-else-if="displayMode === 'cover'" x="1" y="8" width="22" height="8" rx="0.8" />
          <path v-else d="m4 17 5-5 3 3 3-3 5 5M8 9.5h.01" />
        </svg>
        <span>{{ displayModeLabel }}</span>
      </button>

      <button
        type="button"
        class="control-button video-info-button"
        :class="{ 'is-selected': videoInfoVisible }"
        aria-label="显示当前视频信息"
        title="显示当前视频信息"
        @click="toggleVideoInfo"
      >
        信息
      </button>
    </div>
  </div>
</template>

<script lang="ts" setup>
import 'webrtc-adapter';
import { computed, nextTick, onActivated, onDeactivated, onMounted, onUnmounted, ref, watch } from 'vue';
import {
  Go2rtcPlayerCore,
  type Go2rtcPlayerActiveRecoveryEvent,
  type Go2rtcPlayerAttemptKind,
  type Go2rtcPlayerDiagnostics,
  type Go2rtcPlayerFailureEvent,
  type Go2rtcPlayerMode,
  type Go2rtcPlayerPlayingEvent,
  type Go2rtcPlayerStage,
} from './go2rtcPlayerCore';
import {
  cancelH264Fallback,
  confirmH264Fallback,
  forgetH264Fallback,
  isExplicitCodecTerminalFailure,
  isH265RuntimeUnusable,
  probeH265Capability,
  requestH264Fallback,
  resolveH265DecodeFailureAction,
  resolveH265Usable,
  resolveInitialPlaybackProfile,
  resolveSuccessfulPlaybackAction,
  shouldPreselectH264ForCapability,
  shouldSwitchToH264AfterCodecTerminals,
  type H265CapabilitySnapshot,
  type H265StatusReport,
} from './h265Capability';
import { normalizeGo2rtcWsUrl } from './go2rtcUrl';
import { calcReconnectDelay, formatRetryHint, getRetryCountdownSeconds } from './reconnectCountdown';
import type {
  PlaybackProfile,
  PlayerFunPlay,
  PlayerRequestContext,
  StreamPreference,
  StreamStatusReport,
} from './playerTypes';
import {
  isRefreshableSourceFailure,
  isSameSourceRetryableFailure,
  streamFailureMessage,
  type StreamFailureReport,
} from './webrtcFailure';

type Status = 'idle' | 'loading' | 'playing' | 'error';
type DisplayMode = 'contain' | 'cover' | 'fill';
type VideoSlot = 0 | 1;
type PlaybackModePreference = 'auto' | Go2rtcPlayerMode;
type ControlMenu = 'stream' | 'protocol';
type StreamTypeChangeOrigin = 'external' | 'manual' | 'adaptive-fallback';
type PlaybackStage =
  | 'idle'
  | 'acquiring-source'
  | 'connecting-transport'
  | 'negotiating'
  | 'buffering'
  | 'waiting-frame'
  | 'playing'
  | 'retrying'
  | 'failed';

interface PlaySource {
  wsUrl: string;
  streamType: 'L0' | 'L1' | 'STATIC' | null;
  fallbackUsed: boolean;
  playbackProfile: PlaybackProfile | null;
}

type SourceAcquisitionStatus = 'requesting' | 'succeeded' | 'failed' | 'aborted' | 'discarded';

interface SourceRequestErrorDiagnostics {
  name: string;
  code: string | null;
  httpStatus: number | null;
  message: string;
}

interface SourceAcquisitionAttemptDiagnostics {
  attempt: number;
  generation: number;
  status: SourceAcquisitionStatus;
  startedAt: string;
  finishedAt: string | null;
  durationMs: number | null;
  /** H.265 能力结论对本次 /play 造成的实际等待时间；缓存 H264 后台上报记为 0。 */
  capabilityWaitMs: number | null;
  /** 宫格限流器的排队时间；null 表示 provider 未开始或无法确认。 */
  queueWaitMs: number | null;
  /** provider 真正开始后到 resolve/reject 的时间。 */
  providerRequestMs: number | null;
  /** 从能力阶段开始到本次取源完成的总时间。 */
  totalMs: number | null;
  request: {
    streamType: StreamPreference;
    playbackProfile: PlaybackProfile;
    forceRefresh: boolean;
    sourceError: boolean;
    h265Usable: boolean | null;
    failure: StreamFailureReport | null;
  };
  response: {
    wsUrl: string | null;
    streamType: PlaySource['streamType'];
    fallbackUsed: boolean;
    playbackProfile: PlaybackProfile | null;
  } | null;
  error: SourceRequestErrorDiagnostics | null;
  _startedAtMs: number;
}

interface ScheduledSourceRetryDiagnostics {
  scheduledAt: string;
  nextAttemptAt: string;
  delayMs: number;
  attempt: number;
  forceRefresh: boolean;
  sourceError: boolean;
  failure: StreamFailureReport | null;
}

interface CodecTerminalAttemptDiagnostics {
  attemptId: number;
  expectedModes: Go2rtcPlayerMode[];
  reports: Partial<Record<Go2rtcPlayerMode, StreamFailureReport>>;
}

type CoreAttemptStatus = 'connecting' | 'playing' | 'failed' | 'replaced' | 'stopped';

interface CoreAttemptDiagnostics {
  attempt: number;
  generation: number;
  status: CoreAttemptStatus;
  startedAt: string;
  finishedAt: string | null;
  durationMs: number | null;
  modes: Go2rtcPlayerMode[];
  source: {
    wsUrl: string | null;
    streamType: PlaySource['streamType'];
    playbackProfile: PlaybackProfile | null;
  };
  lastStage: {
    stage: Go2rtcPlayerStage;
    message: string;
    timestamp: string;
  } | null;
  playing: {
    timestamp: string;
    mode: Go2rtcPlayerMode;
    codec: Go2rtcPlayerPlayingEvent['codec'];
  } | null;
  lastFailure: {
    timestamp: string;
    kind: Go2rtcPlayerFailureEvent['kind'];
    mode: Go2rtcPlayerFailureEvent['mode'];
    report: StreamFailureReport;
  } | null;
  stopReason: {
    code: string;
    label: string;
  } | null;
  diagnostics: Go2rtcPlayerDiagnostics | null;
  _startedAtMs: number;
}

interface ReadablePlaybackDiagnosis {
  level: 'normal' | 'attention' | 'error';
  title: string;
  conclusion: string;
  evidence: string[];
  suggestions: string[];
}

interface CurrentVideoInfoSnapshot {
  cameraId: string;
  status: string;
  stream: string;
  mode: string;
  codec: string;
  resolution: string;
  profile: string;
  reconnectCount: number;
  error: string;
  diagnosis: ReadablePlaybackDiagnosis;
}

interface PlayerProps {
  cameraId?: string;
  cameraName?: string;
  funPlay: PlayerFunPlay;
  streamType?: string | number;
  allowConfirmedSubstreamFallback?: boolean;
  showStreamSwitch?: boolean;
  mode?: string;
  iceServers?: RTCIceServer[];
  receiveAudio?: boolean;
  startDelayMs?: number;
}

const props = withDefaults(defineProps<PlayerProps>(), {
  cameraId: '',
  cameraName: '',
  streamType: 'auto',
  allowConfirmedSubstreamFallback: false,
  showStreamSwitch: true,
  mode: 'webrtc,mse',
  iceServers: () => [],
  receiveAudio: true,
  startDelayMs: 0,
});
// 尽早预热模块级共享能力快照；ORIGINAL 的实际 profile 决策仍在 startSource 中 await。
void probeH265Capability().catch(() => undefined);
const emit = defineEmits<{
  (event: 'h265-status', report: H265StatusReport): void;
  (event: 'stream-status', report: StreamStatusReport): void;
}>();

function normalizeStreamPreference(value: unknown): StreamPreference {
  if (value === 0 || String(value).toLowerCase() === 'main' || String(value).toUpperCase() === 'L0') return 'L0';
  if (value === 1 || String(value).toLowerCase() === 'sub' || String(value).toUpperCase() === 'L1') return 'L1';
  return 'auto';
}

const videoARef = ref<HTMLVideoElement>();
const videoBRef = ref<HTMLVideoElement>();
const activeVideoSlot = ref<VideoSlot>(0);
const hasPresentedFrame = ref(false);
const status = ref<Status>('idle');
const playbackStage = ref<PlaybackStage>('idle');
const errorMsg = ref('');
const diagnosticsCopied = ref(false);
const diagnosticsManualCopyVisible = ref(false);
const diagnosticsManualCopyText = ref('');
const diagnosticsManualCopyRef = ref<HTMLTextAreaElement>();
const autoRetryEnabled = ref(true);
const activeMode = ref<Go2rtcPlayerMode | null>(null);
const activeCodec = ref('');
const effectivePlaybackProfile = ref<PlaybackProfile | null>(null);
const streamPreference = ref<StreamPreference>(normalizeStreamPreference(props.streamType));
const actualStreamType = ref<'L0' | 'L1' | 'STATIC' | null>(null);
const fallbackUsed = ref(false);
const displayMode = ref<DisplayMode>('fill');
const modePreference = ref<PlaybackModePreference>(enabledModes().length > 1 ? 'auto' : enabledModes()[0]);
const openControlMenu = ref<ControlMenu | null>(null);
const videoInfoVisible = ref(false);
const videoInfoSnapshot = ref<CurrentVideoInfoSnapshot | null>(null);
const retryCountdownSeconds = ref(0);
const retryAttempt = ref(1);

const stageLabels: Record<PlaybackStage, string> = {
  idle: '暂无信号',
  'acquiring-source': '正在获取视频源...',
  'connecting-transport': '正在连接媒体服务...',
  negotiating: '正在协商播放方式...',
  buffering: '正在写入媒体缓冲区...',
  'waiting-frame': '正在等待视频关键帧...',
  playing: '播放中',
  retrying: '正在恢复播放...',
  failed: '播放失败',
};
const loadingStatusText = computed(() => stageLabels[playbackStage.value]);
const displayModeLabels: Record<DisplayMode, string> = { contain: '自适应', cover: '铺满', fill: '拉伸' };
const nextDisplayMode = computed<DisplayMode>(() => {
  if (displayMode.value === 'contain') return 'cover';
  if (displayMode.value === 'cover') return 'fill';
  return 'contain';
});
const displayModeLabel = computed(() => displayModeLabels[displayMode.value]);
const displayModeTitle = computed(
  () => `当前为${displayModeLabel.value}窗口，点击切换为${displayModeLabels[nextDisplayMode.value]}窗口`,
);
const configuredModes = computed(() => enabledModes());
const activeModeLabel = computed(() =>
  activeMode.value === 'webrtc' ? 'WebRTC' : activeMode.value === 'mse' ? 'MSE' : '',
);
const automaticModeLabel = computed(() =>
  activeModeLabel.value ? `自动 · ${activeModeLabel.value}` : status.value === 'loading' ? '自动 · 切换中' : '自动播放',
);
const streamControlOptions: Array<{ value: StreamPreference; label: string }> = [
  { value: 'auto', label: '自动码流' },
  { value: 'L0', label: '主码流' },
  { value: 'L1', label: '子码流' },
];
const streamControlLabel = computed(() => {
  if (streamPreference.value === 'L0') return '主码流';
  if (streamPreference.value === 'L1') return '子码流';
  if (actualStreamType.value === 'L0') return '自动 · 主码流';
  if (actualStreamType.value === 'L1') return '自动 · 子码流';
  return '自动码流';
});
const protocolControlOptions = computed<Array<{ value: PlaybackModePreference; label: string }>>(() => {
  const options: Array<{ value: PlaybackModePreference; label: string }> = [];
  if (configuredModes.value.length > 1) options.push({ value: 'auto', label: '自动选择' });
  if (configuredModes.value.includes('webrtc')) options.push({ value: 'webrtc', label: 'WebRTC' });
  if (configuredModes.value.includes('mse')) options.push({ value: 'mse', label: 'MSE' });
  return options;
});
const protocolControlLabel = computed(() => {
  if (modePreference.value === 'webrtc') return 'WebRTC';
  if (modePreference.value === 'mse') return 'MSE';
  return automaticModeLabel.value;
});
const protocolSwitchTitle = computed(() => {
  const preferred = modePreference.value === 'auto' ? '自动选择' : modePreference.value === 'webrtc' ? 'WebRTC' : 'MSE';
  return activeModeLabel.value ? `${preferred}，当前实际使用 ${activeModeLabel.value}` : preferred;
});
const retryHint = computed(() => formatRetryHint(retryCountdownSeconds.value, retryAttempt.value));
const streamSwitchTitle = computed(() => {
  const requested =
    streamPreference.value === 'auto' ? '自动选择' : streamPreference.value === 'L0' ? '主码流' : '子码流';
  const actual = actualStreamType.value === 'L0' ? '主码流' : actualStreamType.value === 'L1' ? '子码流' : '';
  return actual ? `${requested}，当前实际使用${actual}` : requested;
});

let core: Go2rtcPlayerCore | null = null;
let coreVideoSlot: VideoSlot | null = null;
let activeSource: PlaySource | null = null;
let lastCoreDiagnostics: Go2rtcPlayerDiagnostics | null = null;
let requestedProfile: PlaybackProfile = 'ORIGINAL';
let h264SwitchUsed = false;
let h264SourceRefreshUsed = false;
let h264OriginalReturnUsed = false;
const MAX_SAME_SOURCE_SLOW_RETRIES = 1;
let sameSourceSlowRetryCount = 0;
let generation = 0;
let reconnectCount = 0;
let connectStartedAt = 0;
let mounted = false;
let keepAliveActive = true;
let wasDeactivated = false;
let manuallyStopped = false;
let suspended = false;
let retryTimer: ReturnType<typeof setTimeout> | null = null;
let retryCountdownTimer: ReturnType<typeof setInterval> | null = null;
let diagnosticsCopiedTimer: ReturnType<typeof setTimeout> | null = null;
let playRequestController: AbortController | null = null;
let h265CapabilityPromise: Promise<boolean | undefined> | null = null;
let h265Usable: boolean | undefined;
let h265CapabilitySnapshot: H265CapabilitySnapshot | null = null;
const reportedCapabilityCameraIds = new Set<string>();
const codecTerminalModes = new Set<Go2rtcPlayerMode | 'unknown'>();
const codecTerminalExpectedModes = new Set<Go2rtcPlayerMode>();
let codecTerminalAttemptId: number | null = null;
let lastCodecTerminalDiagnostics: CodecTerminalAttemptDiagnostics | null = null;
let lastSourceFailure: StreamFailureReport | null = null;
const confirmedStreamTypes = new Set<'L0' | 'L1'>();
const activeRecoveryAttemptedModes = new Set<Go2rtcPlayerMode>();
let adaptiveMainFallbackEligible = false;
let sourceRequestFailuresForMain = 0;
let lastAdaptiveStreamFallback: { timestamp: string; reason: string } | null = null;
let scheduledStartTimer: ReturnType<typeof setTimeout> | null = null;
let sourceAcquisitionAttemptCount = 0;
const sourceAcquisitionHistory: SourceAcquisitionAttemptDiagnostics[] = [];
let scheduledSourceRetryDiagnostics: ScheduledSourceRetryDiagnostics | null = null;
let coreAttemptCount = 0;
const coreAttemptHistory: CoreAttemptDiagnostics[] = [];
const coreAttemptByPlayer = new WeakMap<Go2rtcPlayerCore, CoreAttemptDiagnostics>();

function beginSourceAcquisition(
  localGeneration: number,
  profile: PlaybackProfile,
  forceRefresh: boolean,
  sourceError: boolean,
  failureReport?: StreamFailureReport,
): SourceAcquisitionAttemptDiagnostics {
  const startedAt = Date.now();
  const diagnostics: SourceAcquisitionAttemptDiagnostics = {
    attempt: ++sourceAcquisitionAttemptCount,
    generation: localGeneration,
    status: 'requesting',
    startedAt: new Date(startedAt).toISOString(),
    finishedAt: null,
    durationMs: null,
    capabilityWaitMs: null,
    queueWaitMs: null,
    providerRequestMs: null,
    totalMs: null,
    request: {
      streamType: streamPreference.value,
      playbackProfile: profile,
      forceRefresh,
      sourceError,
      h265Usable: h265Usable ?? null,
      failure: failureReport ? { ...failureReport } : null,
    },
    response: null,
    error: null,
    _startedAtMs: startedAt,
  };
  sourceAcquisitionHistory.push(diagnostics);
  if (sourceAcquisitionHistory.length > 5) sourceAcquisitionHistory.shift();
  return diagnostics;
}

function finishSourceAcquisition(
  diagnostics: SourceAcquisitionAttemptDiagnostics,
  status: Exclude<SourceAcquisitionStatus, 'requesting'>,
  source?: PlaySource,
  error?: SourceRequestErrorDiagnostics,
): void {
  const finishedAt = Date.now();
  diagnostics.status = status;
  diagnostics.finishedAt = new Date(finishedAt).toISOString();
  diagnostics.durationMs = Math.max(0, finishedAt - diagnostics._startedAtMs);
  diagnostics.totalMs = diagnostics.durationMs;
  diagnostics.response = source
    ? {
        wsUrl: redactPlaybackUrl(source.wsUrl),
        streamType: source.streamType,
        fallbackUsed: source.fallbackUsed,
        playbackProfile: source.playbackProfile,
      }
    : null;
  diagnostics.error = error ?? null;
}

function sourceRequestErrorDiagnostics(error: unknown): SourceRequestErrorDiagnostics {
  const candidate = error && typeof error === 'object' ? (error as Record<string, any>) : null;
  const name = typeof candidate?.name === 'string' ? candidate.name : error instanceof Error ? error.name : 'Error';
  const code = typeof candidate?.code === 'string' ? candidate.code : null;
  const httpStatus = typeof candidate?.response?.status === 'number' ? candidate.response.status : null;
  const message =
    name === 'AbortError' || code === 'ERR_CANCELED'
      ? '播放地址请求已取消'
      : httpStatus !== null
        ? `播放地址请求失败（HTTP ${httpStatus}）`
        : code
          ? `播放地址请求失败（${code}）`
          : '播放地址请求失败';
  return { name, code, httpStatus, message };
}

function resetSourceAcquisitionDiagnostics(): void {
  sourceAcquisitionAttemptCount = 0;
  sourceAcquisitionHistory.length = 0;
  scheduledSourceRetryDiagnostics = null;
}

function beginCoreAttempt(
  playerCore: Go2rtcPlayerCore,
  source: PlaySource,
  localGeneration: number,
  modes: Go2rtcPlayerMode[],
): CoreAttemptDiagnostics {
  const startedAt = Date.now();
  const attempt: CoreAttemptDiagnostics = {
    attempt: ++coreAttemptCount,
    generation: localGeneration,
    status: 'connecting',
    startedAt: new Date(startedAt).toISOString(),
    finishedAt: null,
    durationMs: null,
    modes: [...modes],
    source: {
      wsUrl: redactPlaybackUrl(source.wsUrl),
      streamType: source.streamType,
      playbackProfile: source.playbackProfile,
    },
    lastStage: null,
    playing: null,
    lastFailure: null,
    stopReason: null,
    diagnostics: null,
    _startedAtMs: startedAt,
  };
  coreAttemptByPlayer.set(playerCore, attempt);
  coreAttemptHistory.push(attempt);
  if (coreAttemptHistory.length > 5) coreAttemptHistory.shift();
  return attempt;
}

function recordCoreStage(playerCore: Go2rtcPlayerCore, stage: Go2rtcPlayerStage, message: string): void {
  const attempt = coreAttemptByPlayer.get(playerCore);
  if (!attempt) return;
  attempt.lastStage = { stage, message, timestamp: new Date().toISOString() };
}

function recordCorePlaying(playerCore: Go2rtcPlayerCore, event: Go2rtcPlayerPlayingEvent): void {
  const attempt = coreAttemptByPlayer.get(playerCore);
  if (!attempt) return;
  attempt.status = 'playing';
  attempt.playing = {
    timestamp: new Date().toISOString(),
    mode: event.mode,
    codec: event.codec,
  };
}

function recordCoreFailure(playerCore: Go2rtcPlayerCore, event: Go2rtcPlayerFailureEvent): void {
  const attempt = coreAttemptByPlayer.get(playerCore);
  if (!attempt) return;
  attempt.lastFailure = {
    timestamp: new Date().toISOString(),
    kind: event.kind,
    mode: event.mode,
    report: { ...event.report },
  };
}

function recordCoreDiagnosticsSnapshot(
  playerCore: Go2rtcPlayerCore,
  diagnostics?: Go2rtcPlayerDiagnostics,
): Go2rtcPlayerDiagnostics | undefined {
  try {
    const snapshot = diagnostics ?? playerCore.getDiagnosticsSnapshot();
    const attempt = coreAttemptByPlayer.get(playerCore);
    if (attempt && !attempt.finishedAt) attempt.diagnostics = snapshot;
    if (core === playerCore) lastCoreDiagnostics = snapshot;
    return snapshot;
  } catch {
    // 同步快照只用于诊断，不能阻断首帧提交或故障恢复。
    return undefined;
  }
}

async function captureCoreAttemptDiagnostics(
  playerCore: Go2rtcPlayerCore,
  context?: { generation: number; videoSlot: VideoSlot },
): Promise<void> {
  const attempt = coreAttemptByPlayer.get(playerCore);
  if (!attempt || attempt.finishedAt) return;
  try {
    const diagnostics = await playerCore.getDiagnostics();
    // stopCoreOnly 已同步冻结销毁前状态时，迟到的异步采样不能再以 CLOSED/null 覆盖它。
    if (
      attempt.finishedAt ||
      (context && (context.generation !== generation || core !== playerCore || coreVideoSlot !== context.videoSlot))
    )
      return;
    attempt.diagnostics = diagnostics;
    if (context) lastCoreDiagnostics = diagnostics;
  } catch {
    // 历史诊断采集失败不能影响播放与恢复。
  }
}

function freezeCoreAttemptDiagnostics(playerCore: Go2rtcPlayerCore, frozenDiagnostics?: Go2rtcPlayerDiagnostics): void {
  let diagnostics = frozenDiagnostics;
  if (!diagnostics) {
    try {
      diagnostics = playerCore.getDiagnosticsSnapshot();
    } catch {
      return;
    }
  }
  const attempt = coreAttemptByPlayer.get(playerCore);
  if (attempt) attempt.diagnostics = diagnostics;
  lastCoreDiagnostics = diagnostics;
}

function finishCoreAttempt(playerCore: Go2rtcPlayerCore, reasonCode: string, reasonLabel: string): void {
  const attempt = coreAttemptByPlayer.get(playerCore);
  if (!attempt || attempt.finishedAt) return;
  const finishedAt = Date.now();
  attempt.finishedAt = new Date(finishedAt).toISOString();
  attempt.durationMs = Math.max(0, finishedAt - attempt._startedAtMs);
  attempt.stopReason = { code: reasonCode, label: reasonLabel };
  if (reasonCode === 'source-retry' || reasonCode === 'terminal-failure') attempt.status = 'failed';
  else if (reasonCode === 'active-recovery' || reasonCode === 'source-refresh' || reasonCode === 'mode-switch')
    attempt.status = 'replaced';
  else attempt.status = 'stopped';
}

function resetCoreAttemptDiagnostics(): void {
  coreAttemptCount = 0;
  coreAttemptHistory.length = 0;
}

function resetCodecTerminalEvidence(): void {
  codecTerminalModes.clear();
  codecTerminalExpectedModes.clear();
  codecTerminalAttemptId = null;
}

function enabledModes(): Go2rtcPlayerMode[] {
  const modes = props.mode
    .split(',')
    .map((mode) => mode.trim().toLowerCase())
    .filter((mode): mode is Go2rtcPlayerMode => mode === 'webrtc' || mode === 'mse');
  return [...new Set(modes.length ? modes : (['webrtc', 'mse'] as Go2rtcPlayerMode[]))];
}

function selectedModes(): Go2rtcPlayerMode[] {
  const modes = enabledModes();
  return modePreference.value !== 'auto' && modes.includes(modePreference.value) ? [modePreference.value] : modes;
}

function canConnect(): boolean {
  return mounted && keepAliveActive && !manuallyStopped && !suspended && !document.hidden && Boolean(props.cameraId);
}

async function reportH265CapabilityOnce(cameraId: string): Promise<void> {
  if (!h265CapabilityPromise) {
    h265CapabilityPromise = probeH265Capability()
      .then((capability) => {
        h265CapabilitySnapshot = capability;
        // 快照在模块间共享，但结论必须按当前播放器实际启用的 mode 分别计算。
        h265Usable = resolveH265Usable(capability, enabledModes());
        return h265Usable;
      })
      .catch(() => {
        h265Usable = undefined;
        return undefined;
      });
  }
  await h265CapabilityPromise;
  if (h265CapabilitySnapshot && cameraId === props.cameraId && !reportedCapabilityCameraIds.has(cameraId)) {
    reportedCapabilityCameraIds.add(cameraId);
    emit('h265-status', {
      type: 'capability',
      cameraId,
      codec: 'H265',
      capability: h265CapabilitySnapshot,
    });
  }
}

function normalizeWsUrl(path: string): string {
  return normalizeGo2rtcWsUrl(path, {
    appBaseUrl: APP_BASE_URL,
    pageUrl: window.location.href,
    useSameOriginStreamProxy: import.meta.env.DEV,
  });
}

function unwrapPlayData(response: any): any {
  if (response?.data?.wsUrl) return response.data;
  if (response?.data?.data?.wsUrl) return response.data.data;
  return response;
}

async function fetchPlaySource(
  cameraId: string,
  profile: PlaybackProfile,
  forceRefresh: boolean,
  sourceError: boolean,
  failureReport?: StreamFailureReport,
  requestContext?: PlayerRequestContext,
): Promise<PlaySource> {
  // 第 8 个参数是向后兼容的请求上下文；旧 funPlay 会自然忽略，支持者可使用 signal 取消 HTTP。
  const response = await props.funPlay(
    cameraId,
    forceRefresh,
    streamPreference.value,
    sourceError,
    failureReport,
    profile,
    h265Usable,
    requestContext,
  );
  const data = unwrapPlayData(response);
  if (!data?.wsUrl) throw new Error('play API 未返回 wsUrl');
  return {
    wsUrl: normalizeWsUrl(data.wsUrl),
    streamType:
      data.streamType === 'L0' || data.streamType === 'L1' || data.streamType === 'STATIC' ? data.streamType : null,
    fallbackUsed: data.fallbackUsed === true,
    playbackProfile:
      data.playbackProfile === 'H264' || data.playbackProfile === 'ORIGINAL' ? data.playbackProfile : null,
  };
}

function mapCoreStage(stage: Go2rtcPlayerStage): PlaybackStage {
  if (stage === 'connecting') return 'connecting-transport';
  if (stage === 'negotiating') return 'negotiating';
  if (stage === 'buffering') return 'buffering';
  if (stage === 'waiting-frame') return 'waiting-frame';
  return 'retrying';
}

function activeCapabilityStreamPreferences(): StreamPreference[] {
  const actualPreference =
    actualStreamType.value === 'L0' || actualStreamType.value === 'L1' ? actualStreamType.value : null;
  if (!actualPreference) return [streamPreference.value];
  // auto 下既保留入口缓存（下次 auto 可直接复用），也记录实际档位；显式 L0/L1 则只按实际档位记忆。
  return streamPreference.value === 'auto' ? [actualPreference, 'auto'] : [actualPreference];
}

function handleCoreStage(
  localGeneration: number,
  playerCore: Go2rtcPlayerCore,
  stage: Go2rtcPlayerStage,
  message: string,
): void {
  recordCoreStage(playerCore, stage, message);
  if (localGeneration !== generation) return;
  status.value = 'loading';
  playbackStage.value = mapCoreStage(stage);
  errorMsg.value = message;
}

function returnToOriginalAfterH264SourceFailure(failureReport?: StreamFailureReport): boolean {
  if (h264OriginalReturnUsed) return false;
  h264OriginalReturnUsed = true;
  h264SwitchUsed = true;
  forgetH264Fallback(props.cameraId, streamPreference.value);
  requestedProfile = 'ORIGINAL';
  resetCodecTerminalEvidence();
  void startSource(false, false, failureReport);
  return true;
}

function handleCorePlaying(
  localGeneration: number,
  videoSlot: VideoSlot,
  playerCore: Go2rtcPlayerCore,
  event: Go2rtcPlayerPlayingEvent,
): void {
  if (localGeneration !== generation || core !== playerCore || coreVideoSlot !== videoSlot) return;
  // requested 是客户端意图，effective 是服务端实际注册的变体，codec 才是媒体证据。
  const effectiveProfile = effectivePlaybackProfile.value ?? requestedProfile;
  const capabilityStreamPreferences = activeCapabilityStreamPreferences();
  const successAction = resolveSuccessfulPlaybackAction(effectiveProfile, event.codec);
  if (successAction === 'h264-codec-mismatch') {
    capabilityStreamPreferences.forEach((preference) => forgetH264Fallback(props.cameraId, preference));
    if (fallbackToConfirmedSubstream('主码流的 H.264 兼容源仍返回 H.265')) return;
    stopFailedPlayback();
    status.value = 'error';
    playbackStage.value = 'failed';
    errorMsg.value = 'H.264 兼容流实际仍为 H.265，请核查服务器转码';
    autoRetryEnabled.value = false;
    return;
  }

  recordCorePlaying(playerCore, event);
  // 先保留无异步等待的快照；完整 stats 只在信息面板已打开时补采。
  recordCoreDiagnosticsSnapshot(playerCore);

  activeMode.value = event.mode;
  activeCodec.value = event.codec === 'UNKNOWN' ? '' : event.codec;
  status.value = 'playing';
  playbackStage.value = 'playing';
  errorMsg.value = '';
  autoRetryEnabled.value = true;
  reconnectCount = 0;
  sameSourceSlowRetryCount = 0;
  lastSourceFailure = null;
  if (actualStreamType.value === 'L0' || actualStreamType.value === 'L1') {
    confirmedStreamTypes.add(actualStreamType.value);
  }
  sourceRequestFailuresForMain = 0;
  resetCodecTerminalEvidence();
  if (effectiveProfile === 'H264') h264SourceRefreshUsed = false;

  const previousSlot = activeVideoSlot.value;
  activeVideoSlot.value = videoSlot;
  hasPresentedFrame.value = true;
  if (previousSlot !== videoSlot) {
    void nextTick(() => {
      if (localGeneration !== generation || activeVideoSlot.value !== videoSlot || coreVideoSlot === previousSlot)
        return;
      releaseVideoSlot(previousSlot);
    });
  }

  const stats = event.runtime?.inboundVideo ?? undefined;
  if (successAction === 'clear-h264-fallback')
    capabilityStreamPreferences.forEach((preference) => forgetH264Fallback(props.cameraId, preference));
  if (successAction === 'confirm-h264-fallback')
    capabilityStreamPreferences.forEach((preference) => confirmH264Fallback(props.cameraId, preference));
  if (event.codec === 'H.265') {
    emit('h265-status', {
      type: 'runtime',
      cameraId: props.cameraId,
      codec: 'H265',
      mode: event.mode,
      status: 'decoded',
      reason: 'FIRST_FRAME',
      ...(stats ? { stats } : {}),
    });
  }
  // 首帧成功路径已保存 Core 同步快照；只有用户正在查看信息时才补采完整 stats。
  if (videoInfoVisible.value) {
    void captureCoreAttemptDiagnostics(playerCore, { generation: localGeneration, videoSlot });
  }
}

async function handleCoreFailure(
  localGeneration: number,
  playerCore: Go2rtcPlayerCore,
  event: Go2rtcPlayerFailureEvent,
  attemptKind: Go2rtcPlayerAttemptKind,
): Promise<void> {
  recordCoreFailure(playerCore, event);
  const failureDiagnostics = recordCoreDiagnosticsSnapshot(playerCore);
  void captureCoreAttemptDiagnostics(playerCore, {
    generation: localGeneration,
    videoSlot: coreVideoSlot ?? activeVideoSlot.value,
  });
  if (localGeneration !== generation || core !== playerCore) return;

  if (event.kind === 'source') lastSourceFailure = { ...event.report };

  // 主画面故障后的单模式备用 Core 只用于快速验证另一种播放方式。
  // 它的任何失败都不能单独改写 H.265/H.264 结论，而是回到 /play 刷新源。
  if (attemptKind === 'active-alternate') {
    if (event.kind === 'source' && fallbackToConfirmedSubstream(streamFailureMessage(event.report))) return;
    if ((effectivePlaybackProfile.value ?? requestedProfile) === 'H264') h264SourceRefreshUsed = true;
    // 备用模式的冻结、codec 和 UNKNOWN 失败只刷新当前源；真实的 RTSP/连接错误仍需通知后端，
    // 以保留既有的 L1 -> L0 原位回退和 producer 重建契约。
    const sourceError = event.kind === 'source' && isRefreshableSourceFailure(event.report);
    scheduleSourceRetry(
      '备用播放方式未能出画，正在重新获取播放地址',
      true,
      sourceError,
      event.report,
      failureDiagnostics,
    );
    return;
  }

  if (event.kind === 'codec-unsupported') {
    if (!isExplicitCodecTerminalFailure(event.report)) return;
    if (event.mode !== 'webrtc' && event.mode !== 'mse') return;

    const attemptId = event.attemptId ?? localGeneration;
    if (codecTerminalAttemptId !== attemptId) {
      resetCodecTerminalEvidence();
      codecTerminalAttemptId = attemptId;
      const actualModes = event.attemptedModes?.length ? event.attemptedModes : enabledModes();
      actualModes.forEach((mode) => codecTerminalExpectedModes.add(mode));
      lastCodecTerminalDiagnostics = {
        attemptId,
        expectedModes: [...actualModes],
        reports: {},
      };
    }
    if (!codecTerminalExpectedModes.has(event.mode)) return;
    codecTerminalModes.add(event.mode);
    if (lastCodecTerminalDiagnostics?.attemptId === attemptId) {
      lastCodecTerminalDiagnostics.reports[event.mode] = { ...event.report };
    }
    const allModesTerminal = [...codecTerminalExpectedModes].every((mode) => codecTerminalModes.has(mode));
    // 保留本次实际启动且仍可能成功的另一种 mode；不跨内部连接 attempt 聚合证据。
    if (!allModesTerminal) return;

    const cameraId = props.cameraId;
    void reportH265CapabilityOnce(cameraId);
    const staticSource = activeSource?.streamType === 'STATIC' || /^rtsps?:\/\//i.test(cameraId);
    if (
      !staticSource &&
      shouldSwitchToH264AfterCodecTerminals(
        requestedProfile,
        h264SwitchUsed,
        [...codecTerminalExpectedModes],
        codecTerminalModes,
      )
    ) {
      requestH264Fallback(cameraId, streamPreference.value);
      h264SwitchUsed = true;
      requestedProfile = 'H264';
      resetCodecTerminalEvidence();
      void startSource(false, false, event.report);
      return;
    }

    if (requestedProfile === 'H264') forgetH264Fallback(cameraId, streamPreference.value);
    if (!staticSource && fallbackToConfirmedSubstream('主码流编码与浏览器不兼容')) return;
    stopFailedPlayback();
    status.value = 'error';
    playbackStage.value = 'failed';
    errorMsg.value = staticSource
      ? '静态视频源的编码与浏览器不兼容，无可用的服务端转码源'
      : '已启用的播放方式均不支持当前视频编码';
    autoRetryEnabled.value = false;
    return;
  }

  if (event.kind === 'source' && fallbackToConfirmedSubstream(streamFailureMessage(event.report))) return;

  if (event.kind === 'h265-decode') {
    const stats = lastCoreDiagnostics?.webrtc?.inboundVideo ?? undefined;
    const levelMismatch = lastCoreDiagnostics?.hevcLevel?.sourceExceedsNegotiatedLevel === true;
    emit('h265-status', {
      type: 'runtime',
      cameraId: props.cameraId,
      codec: 'H265',
      mode: event.mode === 'mse' ? 'mse' : 'webrtc',
      status: 'decode-failed',
      reason: levelMismatch
        ? 'HEVC_LEVEL_MISMATCH'
        : event.mode === 'mse'
          ? 'MSE_DATA_WITHOUT_FRAME'
          : 'PACKETS_WITHOUT_DECODE',
      errorType: 'CODEC_MISMATCH',
      errorCode: 'H265_DECODE_FAILED',
      ...(stats ? { stats } : {}),
      ...(event.mode === 'mse' ? { mseBytesReceived: lastCoreDiagnostics?.mseProgress.bytesReceived ?? 0 } : {}),
    });

    const staticSource = activeSource?.streamType === 'STATIC' || /^rtsps?:\/\//i.test(props.cameraId);
    if (!staticSource && resolveH265DecodeFailureAction(requestedProfile, h264SwitchUsed) === 'switch-to-h264') {
      requestH264Fallback(props.cameraId, streamPreference.value);
      h264SwitchUsed = true;
      requestedProfile = 'H264';
      resetCodecTerminalEvidence();
      void startSource(false, false, event.report);
      return;
    }

    const effectiveProfile = effectivePlaybackProfile.value ?? requestedProfile;
    forgetH264Fallback(props.cameraId, streamPreference.value);
    if (!staticSource && fallbackToConfirmedSubstream('主码流 H.265 解码失败')) return;
    stopFailedPlayback();
    status.value = 'error';
    playbackStage.value = 'failed';
    errorMsg.value = staticSource
      ? '静态 H.265 视频源无法解码，且服务器无可用的 H.264 变体'
      : effectiveProfile === 'H264'
        ? 'H.264 兼容流仍无法解码，请核查服务器转码与摄像头编码规格'
        : '已回退原始视频源，但当前编码仍无法解码';
    autoRetryEnabled.value = false;
    return;
  }

  if (isExplicitCodecTerminalFailure(event.report)) {
    if (requestedProfile === 'H264') forgetH264Fallback(props.cameraId, streamPreference.value);
    if (fallbackToConfirmedSubstream('主码流没有可用的浏览器播放方式')) return;
    stopFailedPlayback();
    status.value = 'error';
    playbackStage.value = 'failed';
    errorMsg.value = '浏览器没有可用的播放方式，或无法确认具体的编码不兼容模式';
    autoRetryEnabled.value = false;
    return;
  }

  // 海康预览地址可能已经返回，但 RTSP producer 仍在启动。
  // 先重新调用 /play 注册同一源，不申请新的预览地址；达到上限后才回到原有强制刷新流程。
  if (
    event.kind === 'source' &&
    !hasPresentedFrame.value &&
    isSameSourceRetryableFailure(event.report) &&
    sameSourceSlowRetryCount < MAX_SAME_SOURCE_SLOW_RETRIES
  ) {
    sameSourceSlowRetryCount += 1;
    scheduleSourceRetry('视频源响应较慢，正在重试当前播放地址', false, false, event.report, failureDiagnostics);
    return;
  }
  if (event.kind === 'source' && isSameSourceRetryableFailure(event.report)) sameSourceSlowRetryCount = 0;

  const effectiveProfile = effectivePlaybackProfile.value ?? requestedProfile;
  if (effectiveProfile === 'H264' && isRefreshableSourceFailure(event.report)) {
    if (!h264SourceRefreshUsed) {
      h264SourceRefreshUsed = true;
      scheduleSourceRetry(streamFailureMessage(event.report), true, true, event.report);
      return;
    }
    if (returnToOriginalAfterH264SourceFailure(event.report)) return;
  }

  scheduleSourceRetry(streamFailureMessage(event.report), true, isRefreshableSourceFailure(event.report), event.report);
}

function videoForSlot(slot: VideoSlot): HTMLVideoElement | undefined {
  return slot === 0 ? videoARef.value : videoBRef.value;
}

function releaseVideoSlot(slot: VideoSlot): void {
  const video = videoForSlot(slot);
  if (!video) return;
  video.pause();
  video.removeAttribute('src');
  video.srcObject = null;
  video.load();
}

function clearVideoBuffers(): void {
  releaseVideoSlot(0);
  releaseVideoSlot(1);
  activeVideoSlot.value = 0;
  hasPresentedFrame.value = false;
}

function createCore(
  source: PlaySource,
  localGeneration: number,
  modes: Go2rtcPlayerMode[] = selectedModes(),
  attemptKind: Go2rtcPlayerAttemptKind = 'initial',
): void {
  const videoSlot: VideoSlot = hasPresentedFrame.value ? (activeVideoSlot.value === 0 ? 1 : 0) : activeVideoSlot.value;
  const video = videoForSlot(videoSlot);
  if (!video) throw new Error('视频元素未就绪');
  releaseVideoSlot(videoSlot);
  let playerCore: Go2rtcPlayerCore;
  playerCore = new Go2rtcPlayerCore(video, {
    modes,
    iceServers: props.iceServers,
    receiveAudio: props.receiveAudio,
    attemptKind,
    onStage: (stage, message) => handleCoreStage(localGeneration, playerCore, stage, message),
    onPlaying: (event) => handleCorePlaying(localGeneration, videoSlot, playerCore, event),
    onFailure: (event) => void handleCoreFailure(localGeneration, playerCore, event, attemptKind),
    onActiveRecovery: (event) => handleActiveCoreRecovery(localGeneration, playerCore, event),
  });
  beginCoreAttempt(playerCore, source, localGeneration, modes);
  core = playerCore;
  coreVideoSlot = videoSlot;
  playerCore.connect(source.wsUrl);
}

function handleActiveCoreRecovery(
  localGeneration: number,
  playerCore: Go2rtcPlayerCore,
  event: Go2rtcPlayerActiveRecoveryEvent,
): void {
  if (localGeneration !== generation || core !== playerCore || !activeSource || !canConnect()) return;
  const source = activeSource;
  const modes = selectedModes();
  let recoveryModes = modes;
  if (modePreference.value === 'auto' && modes.length > 1 && event.failedMode) {
    activeRecoveryAttemptedModes.add(event.failedMode);
    const alternateMode = modes.find((mode) => !activeRecoveryAttemptedModes.has(mode));
    if (!alternateMode) {
      if ((effectivePlaybackProfile.value ?? requestedProfile) === 'H264') h264SourceRefreshUsed = true;
      scheduleSourceRetry(
        '备用播放方式也未能持续出画，正在重新获取播放地址',
        true,
        false,
        undefined,
        event.diagnostics,
      );
      return;
    }
    activeRecoveryAttemptedModes.add(alternateMode);
    recoveryModes = [alternateMode];
  }
  const nextGeneration = ++generation;
  stopCoreOnly(false, 'active-recovery', '主画面异常，创建新的播放连接', event.diagnostics);
  status.value = 'loading';
  playbackStage.value = 'retrying';
  errorMsg.value =
    recoveryModes.length === 1 && recoveryModes[0] !== event.failedMode
      ? `${event.message}，正在切换至${recoveryModes[0] === 'webrtc' ? 'WebRTC' : 'MSE'}`
      : event.message;
  activeMode.value = null;
  activeCodec.value = '';
  createCore(source, nextGeneration, recoveryModes, 'active-alternate');
}

async function startSource(
  forceRefresh = false,
  sourceError = false,
  failureReport?: StreamFailureReport,
  freshStart = false,
): Promise<void> {
  if (!canConnect()) return;
  clearRetryTimer();
  abortPlayRequest();
  resetCodecTerminalEvidence();
  const cameraId = props.cameraId;
  const localGeneration = ++generation;
  connectStartedAt = Date.now();
  stopCoreOnly(!hasPresentedFrame.value || freshStart, 'source-refresh', '重新获取视频源并替换连接');
  if (freshStart) clearVideoBuffers();
  status.value = 'loading';
  playbackStage.value = 'acquiring-source';
  errorMsg.value = '';
  activeMode.value = null;
  activeCodec.value = '';
  effectivePlaybackProfile.value = null;
  const acquisitionDiagnostics = beginSourceAcquisition(
    localGeneration,
    requestedProfile,
    forceRefresh,
    sourceError,
    failureReport,
  );
  // ORIGINAL 必须在取源前拿到能力结论，以便明确不支持 H.265 时预选 H264。
  // 30 天缓存已决定 H264 时，能力上报改为后台执行，不再阻塞 /play。
  if (requestedProfile === 'H264') {
    acquisitionDiagnostics.capabilityWaitMs = 0;
    void reportH265CapabilityOnce(cameraId);
  } else {
    const capabilityStartedAt = Date.now();
    await reportH265CapabilityOnce(cameraId);
    acquisitionDiagnostics.capabilityWaitMs = Math.max(0, Date.now() - capabilityStartedAt);
  }
  acquisitionDiagnostics.request.h265Usable = h265Usable ?? null;
  if (localGeneration !== generation || cameraId !== props.cameraId || !canConnect()) {
    finishSourceAcquisition(acquisitionDiagnostics, 'discarded');
    return;
  }
  if (shouldPreselectH264ForCapability(cameraId, requestedProfile, h264SwitchUsed, h265Usable)) {
    requestH264Fallback(cameraId, streamPreference.value);
    requestedProfile = 'H264';
    h264SwitchUsed = true;
  }
  acquisitionDiagnostics.request.playbackProfile = requestedProfile;

  const requestController = typeof AbortController === 'undefined' ? null : new AbortController();
  playRequestController = requestController;
  const providerCallStartedAt = Date.now();
  let providerStartedAt: number | null = null;
  const requestContext: PlayerRequestContext = {
    ...(requestController ? { signal: requestController.signal } : {}),
    onQueueTiming: (queueWaitMs) => {
      if (providerStartedAt !== null) return;
      acquisitionDiagnostics.queueWaitMs = Math.max(0, Math.floor(queueWaitMs));
      providerStartedAt = Date.now();
    },
  };
  const finishProviderTiming = (providerDidNotStart = false) => {
    const finishedAt = Date.now();
    if (providerStartedAt === null) {
      if (providerDidNotStart) return;
      // 未上报排队回调的原始/旧 provider 视为立即开始，保持八参之前的兼容性。
      acquisitionDiagnostics.queueWaitMs = 0;
      providerStartedAt = providerCallStartedAt;
    }
    acquisitionDiagnostics.providerRequestMs = Math.max(0, finishedAt - providerStartedAt);
  };
  try {
    const source = await fetchPlaySource(
      cameraId,
      requestedProfile,
      forceRefresh,
      sourceError,
      failureReport,
      requestContext,
    );
    finishProviderTiming();
    if (localGeneration !== generation || cameraId !== props.cameraId || !canConnect()) {
      finishSourceAcquisition(acquisitionDiagnostics, 'discarded', source);
      return;
    }
    finishSourceAcquisition(acquisitionDiagnostics, 'succeeded', source);
    activeRecoveryAttemptedModes.clear();
    sourceRequestFailuresForMain = 0;
    activeSource = source;
    actualStreamType.value = source.streamType;
    fallbackUsed.value = source.fallbackUsed;
    effectivePlaybackProfile.value = source.playbackProfile ?? requestedProfile;
    emit('stream-status', {
      cameraId,
      requestedStreamType: streamPreference.value,
      actualStreamType: source.streamType,
      fallbackUsed: source.fallbackUsed,
    });
    // stream-status 监听器可以同步调用 stop/reconnect/setStreamType；旧取源回调不得在该命令之后再落地 Core。
    if (localGeneration !== generation || cameraId !== props.cameraId || !canConnect()) return;
    if (
      source.streamType === 'STATIC' &&
      requestedProfile === 'H264' &&
      effectivePlaybackProfile.value === 'ORIGINAL'
    ) {
      // 静态 RTSP 没有服务器 H264 变体；规范化后续意图并清缓存，避免恢复时重复请求 H264。
      forgetH264Fallback(cameraId, streamPreference.value);
      requestedProfile = 'ORIGINAL';
      h264SwitchUsed = true;
    }
    createCore(source, localGeneration);
  } catch (error) {
    const requestError = sourceRequestErrorDiagnostics(error);
    finishProviderTiming(requestController?.signal.aborted === true && providerStartedAt === null);
    if (requestController?.signal.aborted) {
      finishSourceAcquisition(acquisitionDiagnostics, 'aborted', undefined, requestError);
      return;
    }
    if (localGeneration !== generation || cameraId !== props.cameraId) {
      finishSourceAcquisition(acquisitionDiagnostics, 'discarded', undefined, requestError);
      return;
    }
    finishSourceAcquisition(acquisitionDiagnostics, 'failed', undefined, requestError);
    if (streamPreference.value === 'L0' && adaptiveMainFallbackEligible) {
      sourceRequestFailuresForMain += 1;
      if (sourceRequestFailuresForMain >= 2 && fallbackToConfirmedSubstream('主码流播放地址连续获取失败')) return;
    }
    if (requestedProfile === 'H264') {
      if (h264SourceRefreshUsed) {
        if (returnToOriginalAfterH264SourceFailure(failureReport)) return;
      } else {
        // 缓存或刚切换到 H264 时，取源接口本身失败也只重试该 profile 一次，避免陈旧偏好无限循环。
        h264SourceRefreshUsed = true;
        scheduleSourceRetry('获取播放地址失败', true, sourceError, failureReport);
        return;
      }
    }
    scheduleSourceRetry('获取播放地址失败', forceRefresh, sourceError, failureReport);
  } finally {
    if (playRequestController === requestController) playRequestController = null;
  }
}

function scheduleSourceRetry(
  message: string,
  forceRefresh: boolean,
  sourceError: boolean,
  failureReport?: StreamFailureReport,
  frozenDiagnostics?: Go2rtcPlayerDiagnostics,
): void {
  if (!canConnect() || retryTimer) return;
  // 销毁当前 Core 的同时让已经进入异步处理的 playing/failure 回调立即失效。
  ++generation;
  stopCoreOnly(false, 'source-retry', '视频源失败，等待重新获取播放地址', frozenDiagnostics);
  status.value = 'error';
  playbackStage.value = 'retrying';
  errorMsg.value = message;
  autoRetryEnabled.value = true;
  const delay = calcReconnectDelay(reconnectCount, Date.now() - connectStartedAt);
  reconnectCount += 1;
  retryAttempt.value = reconnectCount;
  const scheduledAt = Date.now();
  scheduledSourceRetryDiagnostics = {
    scheduledAt: new Date(scheduledAt).toISOString(),
    nextAttemptAt: new Date(scheduledAt + delay).toISOString(),
    delayMs: delay,
    attempt: reconnectCount,
    forceRefresh,
    sourceError,
    failure: failureReport ? { ...failureReport } : null,
  };
  startRetryCountdown(delay);
  retryTimer = setTimeout(() => {
    retryTimer = null;
    scheduledSourceRetryDiagnostics = null;
    clearRetryCountdown();
    void startSource(forceRefresh, sourceError, failureReport);
  }, delay);
}

function startRetryCountdown(delay: number): void {
  clearRetryCountdown();
  const deadline = Date.now() + delay;
  const update = () => {
    retryCountdownSeconds.value = getRetryCountdownSeconds(deadline);
  };
  update();
  retryCountdownTimer = setInterval(update, 1_000);
}

function clearRetryCountdown(): void {
  if (retryCountdownTimer) clearInterval(retryCountdownTimer);
  retryCountdownTimer = null;
  retryCountdownSeconds.value = 0;
}

function clearRetryTimer(): void {
  if (retryTimer) clearTimeout(retryTimer);
  retryTimer = null;
  scheduledSourceRetryDiagnostics = null;
  clearRetryCountdown();
}

function abortPlayRequest(): void {
  playRequestController?.abort();
  playRequestController = null;
}

function stopCoreOnly(
  clearVideo = true,
  reasonCode = 'stopped',
  reasonLabel = '播放器连接已停止',
  frozenDiagnostics?: Go2rtcPlayerDiagnostics,
): void {
  const playerCore = core;
  if (!clearVideo && coreVideoSlot !== null) videoForSlot(coreVideoSlot)?.pause();
  if (playerCore) {
    freezeCoreAttemptDiagnostics(playerCore, frozenDiagnostics);
    finishCoreAttempt(playerCore, reasonCode, reasonLabel);
    playerCore.destroy({ clearVideo });
  }
  core = null;
  coreVideoSlot = null;
}

/** 终态错误同时推进 View generation，拒绝已淘汰 Core 的迟到 playing 回调。 */
function stopFailedPlayback(): void {
  ++generation;
  stopCoreOnly(false, 'terminal-failure', '播放器遇到终态错误并停止恢复');
}

function beginPlayback(freshStart = false): void {
  clearScheduledStart();
  cancelH264Fallback(props.cameraId, streamPreference.value);
  reconnectCount = 0;
  sameSourceSlowRetryCount = 0;
  requestedProfile = resolveInitialPlaybackProfile(props.cameraId, streamPreference.value);
  // 从 30 天缓存起播 H264 也算当前会话已使用过降级，防止回 ORIGINAL 后再循环。
  h264SwitchUsed = requestedProfile === 'H264';
  h264SourceRefreshUsed = false;
  h264OriginalReturnUsed = false;
  resetCodecTerminalEvidence();
  lastCodecTerminalDiagnostics = null;
  activeSource = null;
  lastCoreDiagnostics = null;
  lastSourceFailure = null;
  autoRetryEnabled.value = true;
  void startSource(false, false, undefined, freshStart);
}

function scheduleInitialPlayback(freshStart: boolean): void {
  clearScheduledStart();
  if (!canConnect()) return;
  const delay = Math.max(0, Math.floor(props.startDelayMs));
  if (delay === 0) {
    beginPlayback(freshStart);
    return;
  }
  status.value = 'loading';
  playbackStage.value = 'acquiring-source';
  scheduledStartTimer = setTimeout(() => {
    scheduledStartTimer = null;
    if (canConnect()) beginPlayback(freshStart);
  }, delay);
}

function clearScheduledStart(): void {
  if (scheduledStartTimer) clearTimeout(scheduledStartTimer);
  scheduledStartTimer = null;
}

function toggleDisplayMode(): void {
  closeControlMenu();
  displayMode.value = nextDisplayMode.value;
}

function redactPlaybackUrl(value: string | undefined): string | null {
  if (!value) return null;
  try {
    const url = new URL(value, window.location.href);
    url.username = '';
    url.password = '';
    for (const key of [...url.searchParams.keys()]) {
      if (/(?:token|auth|sign|secret|password|key)/i.test(key)) url.searchParams.set(key, '[REDACTED]');
    }
    return url.toString();
  } catch {
    return '[INVALID_PLAYBACK_URL]';
  }
}

async function refreshCoreDiagnostics(): Promise<void> {
  try {
    const playerCore = core;
    if (!playerCore) return;
    const coreAttempt = coreAttemptByPlayer.get(playerCore);
    const diagnostics = await playerCore.getDiagnostics();
    // 用户主动诊断仍获取最新 stats，但不得把旧 Core 的迟到结果写到新连接。
    if (core !== playerCore || coreAttempt?.finishedAt) return;
    lastCoreDiagnostics = diagnostics;
    if (coreAttempt) coreAttempt.diagnostics = diagnostics;
  } catch {
    // 诊断刷新不能影响播放与控制操作。
  }
}

function streamTypeLabel(value: StreamPreference | 'STATIC' | null): string {
  if (value === 'L0') return '主码流';
  if (value === 'L1') return '子码流';
  if (value === 'STATIC') return '静态视频源';
  return value === 'auto' ? '自动码流' : '未知';
}

function buildReadablePlaybackDiagnosis(): ReadablePlaybackDiagnosis {
  const diagnostics = lastCoreDiagnostics;
  const inbound = diagnostics?.webrtc?.inboundVideo ?? null;
  const mse = diagnostics?.mseProgress;
  const latestSourceAttempt = sourceAcquisitionHistory.at(-1) ?? null;
  const evidence: string[] = [];

  if (status.value === 'playing') {
    const currentMode = activeMode.value;
    const mode = currentMode === 'webrtc' ? 'WebRTC' : currentMode === 'mse' ? 'MSE' : '自动模式';
    const actualStream = streamTypeLabel(actualStreamType.value ?? streamPreference.value);
    const requestedStream = streamTypeLabel(streamPreference.value);
    const activeVideo = videoForSlot(activeVideoSlot.value);
    const width = activeVideo?.videoWidth || diagnostics?.video?.videoWidth || 0;
    const height = activeVideo?.videoHeight || diagnostics?.video?.videoHeight || 0;
    const performanceSuggestions: string[] = [];
    evidence.push(`当前模式：${mode}；当前实际流：${actualStream}；编码：${activeCodec.value || '待确认'}`);
    if (actualStreamType.value && (actualStream !== requestedStream || fallbackUsed.value)) {
      evidence.push(
        `请求码流：${requestedStream}；服务端实际返回：${actualStream}${fallbackUsed.value ? '（已回退）' : ''}`,
      );
    }
    if (width > 0 && height > 0) evidence.push(`视频尺寸：${width} × ${height}`);
    const h265Performance =
      activeCodec.value === 'H.265' && currentMode
        ? (h265CapabilitySnapshot?.[currentMode].mediaCapabilities ?? null)
        : null;
    if (h265Performance) {
      evidence.push(
        `H.265 能力预检：平滑=${h265Performance.smooth ? '是' : '否'}；节能=${h265Performance.powerEfficient ? '是' : '否'}`,
      );
      if (!h265Performance.smooth || !h265Performance.powerEfficient) {
        performanceSuggestions.push(
          '当前 H.265 已成功播放，但浏览器未声明为平滑或节能解码；多路场景建议对比子码流或 H.264 的实际资源占用。',
        );
      }
    }
    if (streamPreference.value === 'L1' && actualStreamType.value === 'L0' && fallbackUsed.value) {
      performanceSuggestions.push('当前请求子码流但实际回退到主码流，多宫格下需核对设备是否真正提供子码流。');
    }
    if (diagnostics?.presentation?.lastPresentedFrameAt) evidence.push('浏览器已确认视频帧持续呈现');
    if (coreAttemptHistory.length > 1) evidence.push(`本摄像头本次会话已创建 ${coreAttemptHistory.length} 次播放连接`);
    return {
      level: performanceSuggestions.length > 0 ? 'attention' : 'normal',
      title: performanceSuggestions.length > 0 ? '当前播放正常，但需关注解码开销' : '当前播放正常',
      conclusion:
        performanceSuggestions.length > 0
          ? `播放器正在通过 ${mode} 呈现${actualStream}，当前码流或解码能力可能带来较高的 CPU/GPU 开销。`
          : `播放器正在通过 ${mode} 呈现${actualStream}。`,
      evidence,
      suggestions: performanceSuggestions,
    };
  }

  if (latestSourceAttempt?.status === 'failed' && !core) {
    const error = latestSourceAttempt.error?.message ?? '播放地址请求失败';
    evidence.push(error);
    evidence.push(
      `请求码流：${streamTypeLabel(latestSourceAttempt.request.streamType)}；视频源：${latestSourceAttempt.request.playbackProfile}`,
    );
    if (latestSourceAttempt.totalMs !== null) {
      evidence.push(
        `取源耗时：总计 ${latestSourceAttempt.totalMs}ms；能力等待 ${latestSourceAttempt.capabilityWaitMs ?? 0}ms；排队 ${latestSourceAttempt.queueWaitMs === null ? '未进入 provider' : `${latestSourceAttempt.queueWaitMs}ms`}；provider ${latestSourceAttempt.providerRequestMs === null ? '未开始' : `${latestSourceAttempt.providerRequestMs}ms`}`,
      );
    }
    if (scheduledSourceRetryDiagnostics) {
      evidence.push(`已安排第 ${scheduledSourceRetryDiagnostics.attempt} 次重试`);
    }
    return {
      level: 'error',
      title: '播放地址获取失败',
      conclusion: '播放器尚未创建媒体 Core，问题发生在播放地址接口或其网络请求阶段。',
      evidence,
      suggestions: ['检查播放接口响应、登录状态、API 网关和后端服务，不需要先排查浏览器解码能力。'],
    };
  }

  const codecFailure = lastCodecTerminalDiagnostics
    ? Object.values(lastCodecTerminalDiagnostics.reports).find(Boolean)
    : null;
  if (codecFailure || lastSourceFailure?.type === 'CODEC_MISMATCH') {
    const failure = codecFailure ?? lastSourceFailure!;
    evidence.push(`编码失败：${failure.code}；播放方式：${failure.mode}`);
    if (inbound) {
      evidence.push(
        `WebRTC 已接收 ${inbound.bytesReceived} 字节，解码 ${inbound.framesDecoded} 帧，呈现 ${inbound.framesRendered} 帧`,
      );
    }
    if (mse?.bytesReceived) {
      evidence.push(`MSE 已接收 ${mse.bytesReceived} 字节，成功写入 ${mse.successfulAppends} 次`);
    }
    return {
      level: 'error',
      title: '当前视频编码无法正常播放',
      conclusion: '媒体数据已经进入播放器或服务端明确拒绝了编码，问题更可能在编码兼容性或转码结果。',
      evidence,
      suggestions: ['核对摄像头实际编码、H.265 profile/level，以及服务器返回的 H.264 兼容源是否真正为 H.264。'],
    };
  }

  if (lastSourceFailure) {
    evidence.push(`错误类型：${lastSourceFailure.type}；错误码：${lastSourceFailure.code}`);
    evidence.push(`发生方式：${lastSourceFailure.mode}`);
    if (lastSourceFailure.type === 'SOURCE_PROTOCOL') {
      return {
        level: 'error',
        title: '视频源协议响应异常',
        conclusion: '播放地址已经获取，但媒体服务读取摄像头 RTSP 视频源时收到异常响应。',
        evidence,
        suggestions: ['检查摄像头 RTSP 地址、账号密码、Describe 响应以及主/子码流是否真实存在。'],
      };
    }
    if (lastSourceFailure.type === 'SOURCE_CONNECTION') {
      return {
        level: 'error',
        title: '摄像头或上游视频源连接失败',
        conclusion: '播放地址已经获取，但媒体服务未能连接或持续读取摄像头视频源。',
        evidence,
        suggestions: ['从 go2rtc 所在服务器检查摄像头 IP、RTSP 端口、路由和设备在线状态。'],
      };
    }
    if (lastSourceFailure.type === 'SIGNALING') {
      return {
        level: 'error',
        title: 'WebRTC 信令或网络协商失败',
        conclusion: '视频源地址已经获取，问题发生在 WebRTC offer/answer、ICE 或候选网络路径。',
        evidence,
        suggestions: ['检查浏览器到媒体服务的 WebSocket、ICE candidate、NAT/TURN 配置，并对比 MSE 是否可播放。'],
      };
    }
  }

  if (inbound && inbound.bytesReceived > 0 && inbound.framesDecoded === 0) {
    evidence.push(`WebRTC 已接收 ${inbound.bytesReceived} 字节、${inbound.packetsReceived} 个包，但解码帧为 0`);
    evidence.push(`实际入站编码：${inbound.codecMimeType || '未知'}`);
    return {
      level: 'error',
      title: 'WebRTC 已收到视频但无法解码',
      conclusion: '网络传输已经建立，黑屏发生在浏览器视频解码阶段。',
      evidence,
      suggestions: ['优先核对 H.265 浏览器支持、摄像头编码 profile/level，或切换服务器 H.264 兼容源。'],
    };
  }

  if (inbound && inbound.framesDecoded > 0 && inbound.framesRendered === 0) {
    evidence.push(`WebRTC 已解码 ${inbound.framesDecoded} 帧，但呈现帧为 0`);
    evidence.push(
      `videoWidth=${diagnostics?.video?.videoWidth ?? 0}，readyState=${diagnostics?.video?.readyState ?? 0}`,
    );
    return {
      level: 'error',
      title: '视频已解码但没有呈现画面',
      conclusion: '媒体和解码链路已工作，黑屏更可能发生在 video 元素提交或浏览器渲染阶段。',
      evidence,
      suggestions: ['保留该诊断并核对浏览器/GPU信息；可尝试切换 MSE，排除 WebRTC 主画面提交异常。'],
    };
  }

  if (mse && mse.bytesReceived > 0 && mse.bytesAppended === 0) {
    evidence.push(`MSE 已接收 ${mse.bytesReceived} 字节，但写入缓冲区为 0`);
    if (diagnostics?.failureContext?.lastMseFailure?.message) {
      evidence.push(`最近 MSE 错误：${diagnostics.failureContext.lastMseFailure.message}`);
    }
    return {
      level: 'error',
      title: 'MSE 收到媒体但无法写入缓冲区',
      conclusion: '视频源有数据，问题更可能在 fMP4、Codec MIME 或 SourceBuffer 处理阶段。',
      evidence,
      suggestions: ['核对 go2rtc 输出的 MSE codecs、fMP4 分片以及浏览器 SourceBuffer 支持情况。'],
    };
  }

  if (
    mse &&
    mse.bytesAppended > 0 &&
    mse.bufferedEnd > 0 &&
    !diagnostics?.presentation?.lastPresentedFrameAt &&
    playbackStage.value !== 'playing'
  ) {
    evidence.push(`MSE 已写入 ${mse.bytesAppended} 字节，缓冲结束时间 ${mse.bufferedEnd.toFixed(3)} 秒`);
    evidence.push('浏览器尚未确认任何视频帧呈现');
    return {
      level: 'error',
      title: 'MSE 已缓冲但没有呈现画面',
      conclusion: '媒体数据已经进入 SourceBuffer，黑屏更可能发生在解码或 video 元素呈现阶段。',
      evidence,
      suggestions: ['检查实际 codec 与浏览器能力，必要时切换 WebRTC 或请求 H.264 兼容源。'],
    };
  }

  if (
    diagnostics?.wsState === WebSocket.OPEN &&
    !inbound &&
    (mse?.bytesReceived ?? 0) === 0 &&
    (status.value === 'error' || playbackStage.value === 'retrying')
  ) {
    evidence.push('媒体 WebSocket 已连接，但 MSE 接收字节为 0');
    evidence.push('WebRTC 没有入站视频统计');
    return {
      level: 'error',
      title: '媒体服务没有输出视频数据',
      conclusion: '浏览器已连接媒体服务，但 WebRTC/MSE 都没有收到视频，更可能是 go2rtc 上游 RTSP 或摄像头问题。',
      evidence,
      suggestions: ['检查 go2rtc producer 状态和日志，并从媒体服务器直接验证摄像头 RTSP 可达性。'],
    };
  }

  const stageConclusion: Record<PlaybackStage, string> = {
    idle: '播放器当前未启动。',
    'acquiring-source': '播放器正在请求播放地址，尚未进入媒体连接阶段。',
    'connecting-transport': '播放地址已获取，正在连接媒体 WebSocket。',
    negotiating: '媒体连接已建立，正在协商 WebRTC/MSE 播放方式。',
    buffering: '播放器正在接收并缓冲媒体数据。',
    'waiting-frame': '媒体链路已推进，正在等待首个可呈现视频帧。',
    playing: '播放器正在播放。',
    retrying: '播放器遇到异常，正在执行有界恢复。',
    failed: '播放器已进入终态失败。',
  };
  if (errorMsg.value) evidence.push(errorMsg.value);
  return {
    level: status.value === 'error' ? 'error' : 'attention',
    title: status.value === 'error' ? '播放尚未恢复' : stageLabels[playbackStage.value],
    conclusion: stageConclusion[playbackStage.value],
    evidence,
    suggestions: status.value === 'error' ? ['结合 Core 尝试历史和 go2rtc 日志继续定位。'] : [],
  };
}

function updateVideoInfoSnapshot(): void {
  const activeVideo = videoForSlot(activeVideoSlot.value);
  const diagnosticsVideo = lastCoreDiagnostics?.video;
  const width = activeVideo?.videoWidth || diagnosticsVideo?.videoWidth || 0;
  const height = activeVideo?.videoHeight || diagnosticsVideo?.videoHeight || 0;
  const requestedStream = streamTypeLabel(streamPreference.value);
  const actualStream = streamTypeLabel(actualStreamType.value);
  const preferredMode =
    modePreference.value === 'auto' ? '自动选择' : modePreference.value === 'webrtc' ? 'WebRTC' : 'MSE';
  const actualMode = activeModeLabel.value;
  videoInfoSnapshot.value = {
    cameraId: /^rtsps?:\/\//i.test(props.cameraId)
      ? props.cameraName || '静态 RTSP 视频源'
      : props.cameraName
        ? `${props.cameraName}（${props.cameraId}）`
        : props.cameraId,
    status: stageLabels[playbackStage.value],
    stream:
      actualStreamType.value && actualStream !== requestedStream
        ? `${requestedStream} → ${actualStream}`
        : actualStreamType.value
          ? actualStream
          : requestedStream,
    mode: actualMode && actualMode !== preferredMode ? `${preferredMode} → ${actualMode}` : actualMode || preferredMode,
    codec: activeCodec.value || '待确认',
    resolution: width > 0 && height > 0 ? `${width} × ${height}` : '待确认',
    profile:
      effectivePlaybackProfile.value === 'H264'
        ? 'H.264 兼容源'
        : effectivePlaybackProfile.value === 'ORIGINAL'
          ? '原始视频源'
          : '待确认',
    reconnectCount,
    error: errorMsg.value,
    diagnosis: buildReadablePlaybackDiagnosis(),
  };
}

async function refreshCurrentVideoInfo(): Promise<void> {
  await refreshCoreDiagnostics();
  updateVideoInfoSnapshot();
}

function toggleVideoInfo(): void {
  closeControlMenu();
  if (videoInfoVisible.value) {
    closeVideoInfo();
    return;
  }
  videoInfoVisible.value = true;
  void refreshCurrentVideoInfo();
}

function closeVideoInfo(): void {
  videoInfoVisible.value = false;
  closeManualDiagnostics();
  closeControlMenu();
}

function closeManualDiagnostics(): void {
  diagnosticsManualCopyVisible.value = false;
  diagnosticsManualCopyText.value = '';
}

function selectManualDiagnostics(): void {
  const textarea = diagnosticsManualCopyRef.value;
  if (!textarea) return;
  textarea.focus();
  textarea.select();
  textarea.setSelectionRange(0, textarea.value.length);
}

async function showManualDiagnostics(content: string): Promise<void> {
  diagnosticsCopied.value = false;
  diagnosticsManualCopyText.value = content;
  diagnosticsManualCopyVisible.value = true;
  videoInfoVisible.value = true;
  await nextTick();
  selectManualDiagnostics();
}

function getPlaybackDiagnostics() {
  const diagnosis = buildReadablePlaybackDiagnosis();
  const recentSourceAttempts = sourceAcquisitionHistory.map((attempt) => ({
    attempt: attempt.attempt,
    generation: attempt.generation,
    status: attempt.status,
    startedAt: attempt.startedAt,
    finishedAt: attempt.finishedAt,
    durationMs: attempt.durationMs,
    capabilityWaitMs: attempt.capabilityWaitMs,
    queueWaitMs: attempt.queueWaitMs,
    providerRequestMs: attempt.providerRequestMs,
    totalMs: attempt.totalMs,
    request: {
      ...attempt.request,
      failure: attempt.request.failure ? { ...attempt.request.failure } : null,
    },
    response: attempt.response ? { ...attempt.response } : null,
    error: attempt.error ? { ...attempt.error } : null,
  }));
  const recentCoreAttempts = coreAttemptHistory.map((attempt) => ({
    attempt: attempt.attempt,
    generation: attempt.generation,
    status: attempt.status,
    startedAt: attempt.startedAt,
    finishedAt: attempt.finishedAt,
    durationMs: attempt.durationMs,
    modes: [...attempt.modes],
    source: { ...attempt.source },
    lastStage: attempt.lastStage ? { ...attempt.lastStage } : null,
    playing: attempt.playing ? { ...attempt.playing } : null,
    lastFailure: attempt.lastFailure ? { ...attempt.lastFailure, report: { ...attempt.lastFailure.report } } : null,
    stopReason: attempt.stopReason ? { ...attempt.stopReason } : null,
    diagnostics: attempt.diagnostics
      ? (JSON.parse(JSON.stringify(attempt.diagnostics)) as Go2rtcPlayerDiagnostics)
      : null,
  }));
  return {
    timestamp: new Date().toISOString(),
    cameraId: /^rtsps?:\/\//i.test(props.cameraId) ? '[STATIC_RTSP_SOURCE]' : props.cameraId,
    cameraName: props.cameraName || null,
    generation,
    status: status.value,
    stage: playbackStage.value,
    error: errorMsg.value || null,
    diagnosis,
    requested: {
      streamType: streamPreference.value,
      playbackProfile: requestedProfile,
      modes: selectedModes(),
      configuredModes: enabledModes(),
      modePreference: modePreference.value,
      h265UnavailableCached: isH265RuntimeUnusable(props.cameraId, streamPreference.value),
    },
    actual: {
      streamType: actualStreamType.value,
      playbackProfile: effectivePlaybackProfile.value,
      fallbackUsed: fallbackUsed.value,
      mode: activeMode.value,
      codec: activeCodec.value || null,
    },
    source: { wsUrl: redactPlaybackUrl(activeSource?.wsUrl) },
    sourceAcquisition: {
      totalAttempts: sourceAcquisitionAttemptCount,
      recentAttempts: recentSourceAttempts,
    },
    coreAttempts: {
      totalAttempts: coreAttemptCount,
      recentAttempts: recentCoreAttempts,
    },
    h265: {
      usable: h265Usable ?? null,
      capability: h265CapabilitySnapshot,
      lastCodecTerminal: lastCodecTerminalDiagnostics
        ? {
            attemptId: lastCodecTerminalDiagnostics.attemptId,
            expectedModes: [...lastCodecTerminalDiagnostics.expectedModes],
            reports: { ...lastCodecTerminalDiagnostics.reports },
          }
        : null,
    },
    core: lastCoreDiagnostics,
    recovery: {
      reconnectCount,
      retryAttempt: retryAttempt.value,
      retryCountdownSeconds: retryCountdownSeconds.value,
      scheduledSourceRetry: scheduledSourceRetryDiagnostics
        ? {
            ...scheduledSourceRetryDiagnostics,
            failure: scheduledSourceRetryDiagnostics.failure ? { ...scheduledSourceRetryDiagnostics.failure } : null,
          }
        : null,
      h264SwitchUsed,
      lastSourceFailure: lastSourceFailure ? { ...lastSourceFailure } : null,
      adaptiveStreamFallback: lastAdaptiveStreamFallback ? { ...lastAdaptiveStreamFallback } : null,
    },
    presentationBuffer: {
      activeSlot: activeVideoSlot.value,
      coreSlot: coreVideoSlot,
      retainedFrame: hasPresentedFrame.value,
    },
  };
}

async function copyPlaybackDiagnostics(): Promise<string> {
  await refreshCoreDiagnostics();
  const content = JSON.stringify(getPlaybackDiagnostics(), null, 2);
  let copied = false;
  try {
    await navigator.clipboard?.writeText(content);
    copied = Boolean(navigator.clipboard);
  } catch {
    copied = false;
  }
  if (!copied) {
    const textarea = document.createElement('textarea');
    textarea.value = content;
    textarea.style.position = 'fixed';
    textarea.style.opacity = '0';
    document.body.appendChild(textarea);
    textarea.select();
    try {
      copied = typeof document.execCommand === 'function' && document.execCommand('copy');
    } catch {
      copied = false;
    }
    textarea.remove();
  }
  diagnosticsCopied.value = copied;
  if (diagnosticsCopiedTimer) clearTimeout(diagnosticsCopiedTimer);
  diagnosticsCopiedTimer = null;
  if (copied) {
    closeManualDiagnostics();
    diagnosticsCopiedTimer = setTimeout(() => {
      diagnosticsCopied.value = false;
      diagnosticsCopiedTimer = null;
    }, 2_000);
  } else {
    await showManualDiagnostics(content);
  }
  return content;
}

function reconnectNow(): void {
  suspended = false;
  manuallyStopped = false;
  clearScheduledStart();
  clearRetryTimer();
  if (canConnect()) beginPlayback();
}

function stopPlay(): void {
  suspended = false;
  manuallyStopped = true;
  ++generation;
  cancelH264Fallback(props.cameraId, streamPreference.value);
  abortPlayRequest();
  clearScheduledStart();
  clearRetryTimer();
  stopCoreOnly(true, 'manual-stop', '用户或宫格逻辑停止播放');
  clearVideoBuffers();
  closeVideoInfo();
  status.value = 'idle';
  playbackStage.value = 'idle';
}

function startPlay(): void {
  suspended = false;
  manuallyStopped = false;
  clearScheduledStart();
  if (canConnect()) beginPlayback();
}

/**
 * 宫格节能挂起：释放网络和解码资源，但不清除已呈现槽位。
 * resume 时 createCore 会使用备用槽，只有新首帧成功才替换当前保留帧。
 */
function suspendPlay(): void {
  if (suspended || manuallyStopped) return;
  suspended = true;
  ++generation;
  abortPlayRequest();
  clearScheduledStart();
  clearRetryTimer();
  stopCoreOnly(false, 'suspended', '隐藏画面进入节能挂起');
  status.value = 'loading';
  playbackStage.value = 'retrying';
  errorMsg.value = '隐藏画面已暂停，显示时自动恢复';
}

function resumePlay(): void {
  if (!suspended) return;
  suspended = false;
  if (manuallyStopped) return;
  clearScheduledStart();
  clearRetryTimer();
  if (canConnect()) beginPlayback();
}

function switchPlaybackMode(value: 'auto' | 'webrtc' | 'mse'): boolean | undefined {
  if (value !== 'auto' && value !== 'webrtc' && value !== 'mse') return false;
  const availableModes = enabledModes();
  if (value !== 'auto' && !availableModes.includes(value)) return false;
  const normalizedValue: PlaybackModePreference =
    value === 'auto' && availableModes.length === 1 ? availableModes[0] : value;
  if (modePreference.value === normalizedValue) return true;
  modePreference.value = normalizedValue;
  activeRecoveryAttemptedModes.clear();
  if (!activeSource || !canConnect()) {
    if (videoInfoVisible.value) updateVideoInfoSnapshot();
    return true;
  }
  const source = activeSource;
  const localGeneration = ++generation;
  stopCoreOnly(false, 'mode-switch', '用户切换 WebRTC/MSE 播放方式');
  status.value = 'loading';
  playbackStage.value = 'retrying';
  errorMsg.value =
    normalizedValue === 'auto'
      ? '正在恢复自动播放方式'
      : `正在切换至${normalizedValue === 'webrtc' ? 'WebRTC' : 'MSE'}`;
  activeMode.value = null;
  activeCodec.value = '';
  createCore(source, localGeneration, selectedModes());
  return true;
}

function toggleControlMenu(menu: ControlMenu): void {
  const nextMenu = openControlMenu.value === menu ? null : menu;
  if (nextMenu) videoInfoVisible.value = false;
  openControlMenu.value = nextMenu;
}

function closeControlMenu(): void {
  openControlMenu.value = null;
}

function selectPlaybackMode(value: PlaybackModePreference): void {
  closeControlMenu();
  void switchPlaybackMode(value);
}

function fallbackToConfirmedSubstream(reason: string): boolean {
  if (
    streamPreference.value !== 'L0' ||
    !adaptiveMainFallbackEligible ||
    !confirmedStreamTypes.has('L1') ||
    !hasPresentedFrame.value
  ) {
    return false;
  }
  adaptiveMainFallbackEligible = false;
  sourceRequestFailuresForMain = 0;
  lastAdaptiveStreamFallback = { timestamp: new Date().toISOString(), reason };
  // 该入口只会从失败处理调用。即使失败的 L0 请求已被后端归一成 actual=L1，
  // 也必须新建连接，不能把“已经使用目标码流”的健康路径快路当成恢复成功。
  setStreamType('L1', 'adaptive-fallback', true);
  return true;
}

function setStreamType(value: unknown, origin: StreamTypeChangeOrigin = 'manual', forceRestart = false): void {
  const next = normalizeStreamPreference(value);
  if (next === streamPreference.value) {
    if (next !== 'L0' || origin === 'manual') adaptiveMainFallbackEligible = false;
    return;
  }
  adaptiveMainFallbackEligible =
    props.allowConfirmedSubstreamFallback &&
    origin === 'external' &&
    next === 'L0' &&
    streamPreference.value === 'L1' &&
    confirmedStreamTypes.has('L1');
  sourceRequestFailuresForMain = 0;
  cancelH264Fallback(props.cameraId, streamPreference.value);
  const alreadyUsingRequestedStream = next !== 'auto' && actualStreamType.value === next;
  const currentCoreOwnsRequestedStream =
    alreadyUsingRequestedStream &&
    core !== null &&
    coreVideoSlot !== null &&
    activeSource?.streamType === next &&
    playRequestController === null &&
    retryTimer === null;
  streamPreference.value = next;
  if (currentCoreOwnsRequestedStream && !forceRestart) {
    fallbackUsed.value = false;
    if (videoInfoVisible.value) updateVideoInfoSnapshot();
    return;
  }
  actualStreamType.value = null;
  fallbackUsed.value = false;
  lastCodecTerminalDiagnostics = null;
  if (canConnect()) beginPlayback();
}

function selectStreamType(value: StreamPreference): void {
  closeControlMenu();
  setStreamType(value, 'manual');
}

watch(
  () => props.cameraId,
  (cameraId, previousCameraId) => {
    if (previousCameraId) cancelH264Fallback(previousCameraId);
    resetSourceAcquisitionDiagnostics();
    ++generation;
    abortPlayRequest();
    clearScheduledStart();
    clearRetryTimer();
    stopCoreOnly(true, 'camera-change', '切换到其他摄像头');
    resetCoreAttemptDiagnostics();
    clearVideoBuffers();
    closeVideoInfo();
    videoInfoSnapshot.value = null;
    actualStreamType.value = null;
    fallbackUsed.value = false;
    lastCodecTerminalDiagnostics = null;
    lastSourceFailure = null;
    confirmedStreamTypes.clear();
    activeRecoveryAttemptedModes.clear();
    adaptiveMainFallbackEligible = false;
    sourceRequestFailuresForMain = 0;
    lastAdaptiveStreamFallback = null;
    manuallyStopped = false;
    if (!cameraId) {
      status.value = 'idle';
      playbackStage.value = 'idle';
    } else if (canConnect()) {
      scheduleInitialPlayback(true);
    } else {
      status.value = 'loading';
      playbackStage.value = 'retrying';
    }
  },
);
watch(
  () => props.streamType,
  (value) => setStreamType(value, 'external'),
);
watch(
  () => props.mode,
  () => {
    const modes = enabledModes();
    if (modePreference.value === 'auto' && modes.length > 1) return;
    if (modePreference.value !== 'auto' && modes.includes(modePreference.value)) return;
    void switchPlaybackMode(modes.length > 1 ? 'auto' : modes[0]);
  },
);
watch(
  [
    status,
    playbackStage,
    streamPreference,
    actualStreamType,
    activeMode,
    activeCodec,
    effectivePlaybackProfile,
    () => props.cameraName,
  ],
  () => {
    if (videoInfoVisible.value) updateVideoInfoSnapshot();
  },
);

function onVisibilityChange(): void {
  if (document.hidden) {
    if (!manuallyStopped && props.cameraId) {
      ++generation;
      abortPlayRequest();
      clearScheduledStart();
      clearRetryTimer();
      stopCoreOnly(true, 'page-hidden', '页面进入后台，暂停媒体连接');
      clearVideoBuffers();
      status.value = 'loading';
      playbackStage.value = 'retrying';
    }
  } else if (canConnect()) {
    beginPlayback();
  }
}

onMounted(() => {
  mounted = true;
  document.addEventListener('visibilitychange', onVisibilityChange);
  if (props.cameraId && canConnect()) scheduleInitialPlayback(true);
});
onUnmounted(() => {
  mounted = false;
  keepAliveActive = false;
  ++generation;
  cancelH264Fallback(props.cameraId, streamPreference.value);
  abortPlayRequest();
  clearScheduledStart();
  document.removeEventListener('visibilitychange', onVisibilityChange);
  clearRetryTimer();
  stopCoreOnly(true, 'unmounted', '播放器组件已卸载');
  clearVideoBuffers();
  closeVideoInfo();
  if (diagnosticsCopiedTimer) clearTimeout(diagnosticsCopiedTimer);
});
onActivated(() => {
  keepAliveActive = true;
  if (!wasDeactivated) return;
  wasDeactivated = false;
  if (canConnect()) beginPlayback();
});
onDeactivated(() => {
  keepAliveActive = false;
  wasDeactivated = true;
  ++generation;
  abortPlayRequest();
  clearScheduledStart();
  clearRetryTimer();
  stopCoreOnly(true, 'deactivated', '缓存页面停用，暂停媒体连接');
  clearVideoBuffers();
  closeVideoInfo();
  status.value = 'idle';
  playbackStage.value = 'idle';
});

defineExpose({
  startPlay,
  stopPlay,
  suspendPlay,
  resumePlay,
  reconnectNow,
  setStreamType,
  switchPlaybackMode,
  getPlaybackDiagnostics,
  copyPlaybackDiagnostics,
});
</script>

<style scoped>
.webrtc-player {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #000;
}

.webrtc-video {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  display: block;
  object-fit: contain;
  opacity: 0;
  z-index: 0;
  pointer-events: none;
  background: #000;
}

.webrtc-video.is-active {
  opacity: 1;
  z-index: 1;
}

.is-cover {
  object-fit: cover;
}

.is-fill {
  object-fit: fill;
}

.player-controls {
  position: absolute;
  left: 50%;
  bottom: 6px;
  z-index: 7;
  width: max-content;
  max-width: calc(100% - 12px);
  min-height: 32px;
  padding: 2px 4px;
  display: flex;
  align-items: center;
  gap: 1px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 5px;
  background: linear-gradient(180deg, rgba(28, 31, 36, 0.9), rgba(12, 14, 18, 0.9));
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.3);
  opacity: 0;
  pointer-events: none;
  transform: translateX(-50%);
  transition: opacity 0.16s ease;
}

.control-menu-wrap {
  position: relative;
  flex: 0 0 auto;
}

.control-button,
.video-info-actions button,
.video-info-close {
  border: 1px solid transparent;
  border-radius: 3px;
  background: transparent;
  color: rgba(255, 255, 255, 0.82);
  font: inherit;
  font-size: 11px;
  cursor: pointer;
}

.control-button {
  height: 28px;
  padding: 0 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 3px;
  flex: 0 0 auto;
  white-space: nowrap;
}

.stream-control-button {
  min-width: 76px;
}

.protocol-control-button {
  min-width: 82px;
}

.control-caret {
  width: 10px;
  height: 10px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.6;
  stroke-linecap: round;
  stroke-linejoin: round;
  opacity: 0.72;
}

.display-mode-button svg {
  width: 14px;
  height: 14px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.8;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.control-button:hover,
.control-button.is-selected {
  background: rgba(255, 255, 255, 0.12);
  color: #fff;
}

.control-menu {
  position: absolute;
  left: 50%;
  bottom: calc(100% + 7px);
  z-index: 10;
  min-width: 118px;
  padding: 4px;
  display: flex;
  flex-direction: column;
  gap: 1px;
  border: 1px solid rgba(255, 255, 255, 0.14);
  border-radius: 5px;
  background: rgba(12, 14, 18, 0.96);
  box-shadow: 0 7px 22px rgba(0, 0, 0, 0.38);
  transform: translateX(-50%);
}

.control-menu button {
  width: 100%;
  height: 28px;
  padding: 0 9px 0 6px;
  display: flex;
  align-items: center;
  gap: 5px;
  border: 0;
  border-radius: 3px;
  color: rgba(255, 255, 255, 0.76);
  background: transparent;
  font: inherit;
  font-size: 11px;
  text-align: left;
  white-space: nowrap;
  cursor: pointer;
}

.control-menu button:hover,
.control-menu button:focus-visible {
  color: #fff;
  background: rgba(255, 255, 255, 0.1);
  outline: none;
}

.control-menu button.is-active {
  color: #7dd3fc;
}

.menu-check {
  width: 12px;
  opacity: 0;
}

.control-menu button.is-active .menu-check {
  opacity: 1;
}

.video-info-panel {
  position: absolute;
  left: 50%;
  bottom: 44px;
  z-index: 8;
  width: min(300px, calc(100% - 12px));
  max-height: calc(100% - 48px);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  border: 1px solid rgba(255, 255, 255, 0.16);
  border-radius: 7px;
  color: rgba(255, 255, 255, 0.86);
  background: rgba(8, 12, 18, 0.92);
  box-shadow: 0 8px 28px rgba(0, 0, 0, 0.34);
  font-size: 11px;
  transform: translateX(-50%);
}

.video-info-header {
  flex: 0 0 auto;
  height: 32px;
  padding: 0 8px 0 10px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  font-weight: 600;
}

.video-info-content {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
}

.video-info-manual-copy {
  min-height: 0;
  padding: 8px 10px 10px;
}

.video-info-manual-copy p {
  margin: 0 0 7px;
  color: rgba(255, 255, 255, 0.64);
  line-height: 1.45;
}

.video-info-diagnostics-text {
  display: block;
  width: 100%;
  height: 220px;
  min-height: 100px;
  padding: 7px;
  box-sizing: border-box;
  resize: none;
  border: 1px solid rgba(255, 255, 255, 0.14);
  border-radius: 4px;
  outline: none;
  color: rgba(255, 255, 255, 0.84);
  background: rgba(0, 0, 0, 0.3);
  font:
    10px/1.45 ui-monospace,
    SFMono-Regular,
    Menlo,
    Monaco,
    Consolas,
    monospace;
  white-space: pre;
}

.video-info-diagnostics-text:focus {
  border-color: rgba(96, 165, 250, 0.8);
}

.video-info-close {
  width: 22px;
  height: 22px;
  padding: 0;
  border-color: transparent;
  font-size: 17px;
  line-height: 18px;
}

.video-info-list {
  margin: 0;
  padding: 6px 10px;
}

.video-info-diagnosis {
  margin: 8px 10px 0;
  padding: 7px 8px;
  border: 1px solid rgba(251, 191, 36, 0.24);
  border-radius: 4px;
  background: rgba(251, 191, 36, 0.08);
  line-height: 1.45;
}

.video-info-diagnosis.is-normal {
  border-color: rgba(74, 222, 128, 0.24);
  background: rgba(74, 222, 128, 0.08);
}

.video-info-diagnosis.is-error {
  border-color: rgba(248, 113, 113, 0.28);
  background: rgba(248, 113, 113, 0.1);
}

.video-info-diagnosis strong {
  display: block;
  color: rgba(255, 255, 255, 0.9);
  font-size: 12px;
}

.video-info-diagnosis p {
  margin: 3px 0 0;
  color: rgba(255, 255, 255, 0.68);
}

.video-info-diagnosis .video-info-diagnosis-suggestion {
  color: rgba(255, 255, 255, 0.52);
}

.video-info-list > div {
  min-height: 23px;
  display: grid;
  grid-template-columns: 64px minmax(0, 1fr);
  align-items: center;
  gap: 8px;
}

.video-info-list dt {
  color: rgba(255, 255, 255, 0.48);
}

.video-info-list dd {
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  text-align: right;
}

.video-info-error {
  margin: 0 10px 7px;
  padding: 5px 7px;
  overflow: hidden;
  border-radius: 4px;
  color: #ffb4b4;
  background: rgba(255, 77, 79, 0.12);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.video-info-actions {
  flex: 0 0 auto;
  padding: 7px 8px;
  display: flex;
  justify-content: flex-end;
  gap: 5px;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
}

.video-info-actions button {
  height: 24px;
  padding: 0 8px;
}

.webrtc-player:hover .player-controls,
.webrtc-player:focus-within .player-controls,
.player-controls:has(.is-selected) {
  opacity: 1;
  pointer-events: auto;
}

@media (hover: none) {
  .player-controls {
    opacity: 1;
    pointer-events: auto;
  }
}

.control-button:focus-visible,
.video-info-actions button:focus-visible,
.video-info-close:focus-visible {
  outline: 1px solid rgba(96, 165, 250, 0.9);
  outline-offset: 1px;
}

.status-overlay {
  position: absolute;
  inset: 0;
  z-index: 4;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: #999;
  font-size: 13px;
  background: #000;
}

.status-error {
  color: #ff6b6b;
  background: rgba(0, 0, 0, 0.85);
}

.reconnect-tip {
  position: absolute;
  top: 6px;
  left: 6px;
  z-index: 5;
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 3px 8px 3px 6px;
  border-radius: 4px;
  color: rgba(255, 255, 255, 0.72);
  font-size: 11px;
  background: rgba(0, 0, 0, 0.48);
}

.reconnect-tip--error {
  max-width: calc(100% - 16px);
  color: rgba(255, 255, 255, 0.82);
}

.reconnect-error-msg {
  max-width: min(260px, 45vw);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.reconnect-dot {
  width: 6px;
  height: 6px;
  flex-shrink: 0;
  border-radius: 50%;
  background: #faad14;
  animation: dot-pulse 1.4s ease-in-out infinite;
}

.reconnect-dot--error {
  background: #ff4d4f;
}

.error-msg {
  max-width: 90%;
  overflow: hidden;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
  text-align: center;
  word-break: break-all;
  font-size: 12px;
  line-height: 1.5;
}

.spin-icon,
.error-icon {
  width: 32px;
  height: 32px;
}

.spin-icon {
  color: #888;
  animation: spin 0.9s linear infinite;
}

.idle-icon {
  width: 36px;
  height: 36px;
  opacity: 0.4;
}

.error-retry-hint {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.38);
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@keyframes dot-pulse {
  50% {
    opacity: 0.35;
    transform: scale(0.75);
  }
}
</style>
