<template>
  <div
    class="grid-wrap"
    :class="{ 'grid-wrap--one-plus-five': layout.onePlusFive }"
    :data-split="layout.value"
    :style="gridStyle"
  >
    <div
      v-for="(item, index) in videoList"
      :key="index"
      class="grid-cell"
      :class="{
        'cell-active': activeIndex === index,
        'cell-focused': focusedIndex === index,
        'cell-hidden': focusedIndex !== null && focusedIndex !== index,
      }"
      @click="handleClick(index)"
      @dblclick.stop="handleDblClick(index)"
    >
      <button v-if="showAddButton" type="button" class="cell-add-btn" @click.stop="handleAddSlot(index)">+</button>

      <WebrtcPlayer
        v-if="item.id"
        :cameraId="item.id"
        :camera-name="item.name"
        :stream-type="streamTypeForSlot(item, index)"
        :allow-confirmed-substream-fallback="adaptiveStreamEnabled"
        :show-stream-switch="showStreamSwitch"
        :mode="mode"
        :ice-servers="iceServers"
        :receive-audio="receiveAudio"
        :fun-play="funPlayForSlot(index)"
        :start-delay-ms="startDelayForSlot(item, index)"
        :ref="(el) => setPlayerRef(index, el)"
        @h265-status="forwardH265Status(index, $event)"
        @stream-status="handleStreamStatus(index, $event)"
      />
      <div v-else class="cell-empty">
        <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24">
          <path
            fill="#ffffff"
            d="M12 20c-4.41 0-8-3.59-8-8s3.59-8 8-8s8 3.59 8 8s-3.59 8-8 8m0-18A10 10 0 0 0 2 12a10 10 0 0 0 10 10a10 10 0 0 0 10-10A10 10 0 0 0 12 2m-2 14.5l6-4.5l-6-4.5v9Z"
          />
        </svg>
        <span>请选择视频</span>
      </div>

      <div v-if="showAddButton && item.id" class="cell-name" :title="item.name || item.id">
        {{ item.name || item.id }}
      </div>

      <!-- 双击提示角标 -->
      <span v-if="focusedIndex === index" class="focus-badge" @dblclick.stop="handleDblClick(index)"> 双击还原 </span>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed, nextTick, onBeforeUnmount, ref, watch, type PropType } from 'vue';
import WebrtcPlayer from './WebrtcPlayer.vue';
import {
  createPlayerFunPlayLimiter,
  defaultPlayerFunPlay,
  MAX_WEBRTC_GRID_SLOTS,
  normalizeGridVideoItem,
  normalizeWebrtcGridLayout,
  serializeGridVideoItems,
  type GridVideoItem,
  type GridVideoItemInput,
  type PlayerFunPlay,
  type GridStreamStatusReport,
  type StreamPreference,
  type StreamStatusReport,
  type WebrtcPlayerExpose,
} from './webrtc/playerTypes';
import { probeH265Capability, type GridH265StatusReport, type H265StatusReport } from './webrtc/h265Capability';

// ─── Props ────────────────────────────────────────────────────────────────────

const props = defineProps({
  /** 宫格布局，如 "2x2"、"3x3"、"1+5" */
  split: { type: String, default: '2x2' },

  /** 摄像头列表，每项为 cameraId 字符串或 { id } 对象 */
  videoCodeList: {
    type: Array as PropType<(string | GridVideoItemInput)[]>,
    default: () => [],
  },

  funPlay: {
    type: Function as PropType<PlayerFunPlay>,
    default: defaultPlayerFunPlay,
  },

  /** 固定码流兜底值；关闭 adaptiveStreamType 时生效。兼容旧值：0=主码流、1=子码流。 */
  streamType: { type: [String, Number], default: 'auto' },

  /** 默认由布局决定码流：单屏/聚焦使用主码流，多宫格使用子码流；传 false 才使用 streamType。 */
  adaptiveStreamType: { type: Boolean, default: true },

  showStreamSwitch: { type: Boolean, default: true },

  showAddButton: {
    type: Boolean,
    default: false,
  },

  mode: { type: String, default: 'webrtc,mse' },

  iceServers: { type: Array as PropType<RTCIceServer[]>, default: () => [] },

  /** 宫格默认不协商音频，避免多路无用音轨增加带宽与解码开销。 */
  receiveAudio: { type: Boolean, default: false },

  /** 聚焦单屏时是否暂停其他格子；默认 false，保持既有后台播放行为。 */
  pauseHiddenOnFocus: { type: Boolean, default: false },

  /** 大宫格聚焦稳定后保留画面并释放隐藏流；显式 pauseHiddenOnFocus 优先。 */
  suspendHiddenOnFocus: { type: Boolean, default: true },

  /** 聚焦持续多久才挂起隐藏流，避免快速进入/退出触发无意义重连。 */
  hiddenSuspendDelayMs: { type: Number, default: 3_000 },

  /** 仅达到该宫格数量时自动节能，默认从 3x3 开始。 */
  hiddenSuspendMinSlots: { type: Number, default: 9 },

  /** 宫格内同时获取播放地址的最大数量，避免多画面首次加载冲击后端。 */
  maxConcurrentSourceRequests: { type: Number, default: 3 },

  /** 每批播放器启动间隔；与并发数共同把多宫格协商分成若干启动波次。 */
  startBatchDelayMs: { type: Number, default: 300 },

  /** 退出聚焦后延迟降回子码流，期间重新聚焦可复用当前主码流。 */
  restoreSubstreamDelayMs: { type: Number, default: 3_000 },
});

const emit = defineEmits<{
  (e: 'add-slot', index: number): void;
  (e: 'h265-status', report: GridH265StatusReport): void;
  (e: 'stream-status', report: GridStreamStatusReport): void;
  (e: 'update:videoCodeList', value: GridVideoItem[]): void;
}>();

// 宫格创建时即预热页面级共享能力探测，具体 profile 仍由每路播放器在 /play 前判定。
void probeH265Capability().catch(() => undefined);

// ─── 状态 ──────────────────────────────────────────────────────────────────────

const activeIndex = ref(0);
const focusedIndex = ref<number | null>(null); // null = 宫格模式，number = 单屏模式
const mainStreamIndex = ref<number | null>(null); // 退出聚焦后短暂保留主码流，避免快速往返重复换流
const slotItems = ref<GridVideoItem[]>([]);
const playerRefs = ref<(WebrtcPlayerExpose | null)[]>([]);
const camerasWithoutSubstream = ref<Set<string>>(new Set());
const focusPausedPlayers = new Map<number, { player: WebrtcPlayerExpose; cameraId: string }>();
const focusSuspendedPlayers = new Map<number, { player: WebrtcPlayerExpose; cameraId: string }>();
// 只有命令式换入的摄像头跳过固定宫格波次；外部批量列表仍按非空摄像头顺序分批启动。
const immediateStartCameraIds = new Map<number, string>();
// 命令式换源的首次 /play 请求与聚焦请求进入交互优先队列。
const interactiveRequestCameraIds = new Map<number, string>();
const slotFunPlayProviders = new Map<number, PlayerFunPlay>();
let restoreSubstreamTimer: ReturnType<typeof setTimeout> | null = null;
let hiddenSuspendTimer: ReturnType<typeof setTimeout> | null = null;
let hiddenSuspendEngaged = false;
let hiddenSuspendFocusedIndex: number | null = null;
const normalizedConcurrentStarts = computed(() =>
  Math.min(MAX_WEBRTC_GRID_SLOTS, Math.max(1, Math.floor(props.maxConcurrentSourceRequests) || 1)),
);
const limitedFunPlay = createPlayerFunPlayLimiter(
  (...args) => props.funPlay(...args),
  () => normalizedConcurrentStarts.value,
);

function startDelayForSlot(item: GridVideoItem, index: number): number {
  if (immediateStartCameraIds.get(index) === item.id) return 0;
  const batchDelay = Math.max(0, Math.floor(props.startBatchDelayMs) || 0);
  const populatedBefore = videoList.value
    .slice(0, index)
    .reduce((count, candidate) => count + (candidate.id ? 1 : 0), 0);
  return Math.floor(populatedBefore / normalizedConcurrentStarts.value) * batchDelay;
}

function funPlayForSlot(index: number): PlayerFunPlay {
  const cached = slotFunPlayProviders.get(index);
  if (cached) return cached;

  const provider: PlayerFunPlay = (...args) => {
    const cameraId = args[0];
    const markedImmediate = immediateStartCameraIds.get(index) === cameraId;
    if (markedImmediate) immediateStartCameraIds.delete(index);
    const markedInteractive = interactiveRequestCameraIds.get(index) === cameraId;
    if (markedInteractive) interactiveRequestCameraIds.delete(index);
    const context = args[7];
    const priority =
      context?.priority === 'interactive' || markedInteractive || focusedIndex.value === index
        ? 'interactive'
        : 'normal';
    const nextArgs = [...args] as Parameters<PlayerFunPlay>;
    nextArgs[7] = { ...context, priority };
    return limitedFunPlay(...nextArgs);
  };
  slotFunPlayProviders.set(index, provider);
  return provider;
}

function normalizeConfiguredStreamType(value: unknown): StreamPreference {
  if (value === 0 || String(value).toLowerCase() === 'main' || String(value).toUpperCase() === 'L0') return 'L0';
  if (value === 1 || String(value).toLowerCase() === 'sub' || String(value).toUpperCase() === 'L1') return 'L1';
  return 'auto';
}

const adaptiveStreamEnabled = computed(() => props.adaptiveStreamType);

function streamTypeForSlot(item: GridVideoItem, index: number): StreamPreference {
  const configured = normalizeConfiguredStreamType(props.streamType);
  if (!adaptiveStreamEnabled.value) return configured;
  if (camerasWithoutSubstream.value.has(item.id)) return 'L0';
  return layout.value.total === 1 || mainStreamIndex.value === index ? 'L0' : 'L1';
}

function clearRestoreSubstreamTimer() {
  if (restoreSubstreamTimer) clearTimeout(restoreSubstreamTimer);
  restoreSubstreamTimer = null;
}

function scheduleRestoreSubstream(index: number) {
  clearRestoreSubstreamTimer();
  const delay = Math.min(5_000, Math.max(0, Math.floor(props.restoreSubstreamDelayMs) || 0));
  if (delay === 0) {
    if (mainStreamIndex.value === index) mainStreamIndex.value = null;
    return;
  }
  restoreSubstreamTimer = setTimeout(() => {
    restoreSubstreamTimer = null;
    if (focusedIndex.value === null && mainStreamIndex.value === index) mainStreamIndex.value = null;
  }, delay);
}

watch(
  () => props.maxConcurrentSourceRequests,
  () => limitedFunPlay.refreshLimit(),
);

function isWebrtcPlayerExpose(value: unknown): value is WebrtcPlayerExpose {
  if (!value || typeof value !== 'object') return false;
  const candidate = value as Partial<WebrtcPlayerExpose>;
  return (
    typeof candidate.startPlay === 'function' &&
    typeof candidate.stopPlay === 'function' &&
    typeof candidate.suspendPlay === 'function' &&
    typeof candidate.resumePlay === 'function' &&
    typeof candidate.reconnectNow === 'function' &&
    typeof candidate.setStreamType === 'function' &&
    typeof candidate.switchPlaybackMode === 'function' &&
    typeof candidate.getPlaybackDiagnostics === 'function' &&
    typeof candidate.copyPlaybackDiagnostics === 'function'
  );
}

function setPlayerRef(index: number, value: unknown) {
  playerRefs.value[index] = isWebrtcPlayerExpose(value) ? value : null;
}

// ─── 布局 ─────────────────────────────────────────────────────────────────────

const layout = computed(() => normalizeWebrtcGridLayout(props.split));

const videoList = computed<GridVideoItem[]>(() =>
  Array.from({ length: layout.value.total }, (_, index) => slotItems.value[index] ?? { id: '' }),
);

/**
 * 外部列表是受控输入快照：引用替换或内容原地变化时覆盖本地槽位。
 * 命令式 startPlay/stopPlay 只更新本地快照并发出 update 事件；单独改变 split 不会重置它。
 */
watch(
  () => props.videoCodeList,
  (items) => {
    const nextItems = items.slice(0, MAX_WEBRTC_GRID_SLOTS).map(normalizeGridVideoItem);
    for (const [index, cameraId] of immediateStartCameraIds) {
      // v-model 回传同一次命令式换源时保留立即启动标记；真正的外部列表替换恢复批量分波次。
      if (nextItems[index]?.id !== cameraId) immediateStartCameraIds.delete(index);
    }
    for (const [index, cameraId] of interactiveRequestCameraIds) {
      if (nextItems[index]?.id !== cameraId) interactiveRequestCameraIds.delete(index);
    }
    slotItems.value = nextItems;
  },
  { deep: true, immediate: true },
);

watch(
  () => props.split,
  () => {
    clearRestoreSubstreamTimer();
    activeIndex.value = Math.min(activeIndex.value, layout.value.total - 1);
    focusedIndex.value = null;
    mainStreamIndex.value = null;
  },
);

/** CSS Grid 列数跟随 split 动态变化 */
const gridStyle = computed(() => ({
  '--cols': layout.value.cols,
  '--rows': layout.value.rows,
}));

function emitVideoListUpdate() {
  let lastUsedIndex = slotItems.value.length - 1;
  while (lastUsedIndex >= 0 && !slotItems.value[lastUsedIndex]?.id) lastUsedIndex -= 1;
  emit(
    'update:videoCodeList',
    slotItems.value.slice(0, lastUsedIndex + 1).map((item) => ({ ...item })),
  );
}

function replaceSlot(index: number, item: GridVideoItem) {
  const next = slotItems.value.slice();
  while (next.length <= index) next.push({ id: '' });
  next[index] = item;
  slotItems.value = next;
  emitVideoListUpdate();
}

function clearHiddenSuspendTimer() {
  if (hiddenSuspendTimer) clearTimeout(hiddenSuspendTimer);
  hiddenSuspendTimer = null;
}

function hiddenSuspendPolicyEnabled(): boolean {
  const minSlots = Math.min(MAX_WEBRTC_GRID_SLOTS, Math.max(1, Math.floor(props.hiddenSuspendMinSlots) || 1));
  return props.suspendHiddenOnFocus && layout.value.total >= minSlots;
}

function resumeSuspendedPlayers(index?: number) {
  focusSuspendedPlayers.forEach(({ player }, suspendedIndex) => {
    if (index !== undefined && suspendedIndex !== index) return;
    const item = videoList.value[suspendedIndex];
    const currentPlayer = playerRefs.value[suspendedIndex];
    // camera 在挂起期间变化时，同一个 View 仍保持 suspended；恢复当前 camera，绝不恢复已淘汰 id。
    if (currentPlayer === player && item?.id) {
      interactiveRequestCameraIds.set(suspendedIndex, item.id);
      currentPlayer.resumePlay();
    }
    focusSuspendedPlayers.delete(suspendedIndex);
  });
}

function suspendHiddenPlayers(focused: number) {
  if (props.pauseHiddenOnFocus || focusedIndex.value !== focused || !hiddenSuspendPolicyEnabled()) {
    return;
  }
  hiddenSuspendEngaged = true;
  hiddenSuspendFocusedIndex = focused;
  videoList.value.forEach((item, index) => {
    if (!item.id || index === focused) return;
    const player = playerRefs.value[index];
    if (!player) return;
    const suspended = focusSuspendedPlayers.get(index);
    if (suspended?.player === player && suspended.cameraId === item.id) return;
    player.suspendPlay();
    focusSuspendedPlayers.set(index, { player, cameraId: item.id });
  });
}

async function syncFocusResourcePolicy() {
  await nextTick();
  const focused = focusedIndex.value;

  if (props.pauseHiddenOnFocus) {
    clearHiddenSuspendTimer();
    hiddenSuspendEngaged = false;
    hiddenSuspendFocusedIndex = null;

    if (focused === null) {
      resumeSuspendedPlayers();
      focusPausedPlayers.forEach(({ player }, index) => {
        if (playerRefs.value[index] === player && videoList.value[index]?.id) player.startPlay();
      });
      focusPausedPlayers.clear();
      return;
    }

    resumeSuspendedPlayers(focused);
    focusSuspendedPlayers.forEach(({ player }, index) => {
      const item = videoList.value[index];
      if (playerRefs.value[index] !== player || !item?.id) return;
      player.stopPlay();
      focusPausedPlayers.set(index, { player, cameraId: item.id });
    });
    focusSuspendedPlayers.clear();

    videoList.value.forEach((item, index) => {
      if (!item.id) {
        focusPausedPlayers.delete(index);
        return;
      }
      if (index === focused) {
        const paused = focusPausedPlayers.get(index);
        if (paused?.player === playerRefs.value[index]) paused.player.startPlay();
        focusPausedPlayers.delete(index);
        return;
      }
      const player = playerRefs.value[index];
      const paused = focusPausedPlayers.get(index);
      if (player && (paused?.player !== player || paused.cameraId !== item.id)) {
        player.stopPlay();
        focusPausedPlayers.set(index, { player, cameraId: item.id });
      }
    });
    return;
  }

  // 从显式立即暂停切回节能策略时，先恢复既有播放器，再由稳定聚焦计时决定是否保帧挂起。
  focusPausedPlayers.forEach(({ player }, index) => {
    if (playerRefs.value[index] === player && videoList.value[index]?.id) player.startPlay();
  });
  focusPausedPlayers.clear();

  if (focused === null || !hiddenSuspendPolicyEnabled()) {
    clearHiddenSuspendTimer();
    hiddenSuspendEngaged = false;
    hiddenSuspendFocusedIndex = null;
    resumeSuspendedPlayers();
    return;
  }

  // 快速换焦时先恢复新焦点的保留帧；已挂起的其他格子继续节能，旧焦点满 3 秒后再挂起。
  resumeSuspendedPlayers(focused);
  clearHiddenSuspendTimer();
  if (hiddenSuspendEngaged && hiddenSuspendFocusedIndex !== focused) {
    hiddenSuspendEngaged = false;
    hiddenSuspendFocusedIndex = null;
  }
  if (hiddenSuspendEngaged) {
    suspendHiddenPlayers(focused);
    return;
  }
  const delay = Math.min(60_000, Math.max(0, Math.floor(props.hiddenSuspendDelayMs) || 0));
  if (delay === 0) {
    suspendHiddenPlayers(focused);
    return;
  }
  hiddenSuspendTimer = setTimeout(() => {
    hiddenSuspendTimer = null;
    suspendHiddenPlayers(focused);
  }, delay);
}

watch(
  [
    focusedIndex,
    () => props.pauseHiddenOnFocus,
    () => props.suspendHiddenOnFocus,
    () => props.hiddenSuspendDelayMs,
    () => props.hiddenSuspendMinSlots,
    () => layout.value.total,
    () => serializeGridVideoItems(videoList.value),
  ],
  () => void syncFocusResourcePolicy(),
  { flush: 'post' },
);

// ─── 交互 ─────────────────────────────────────────────────────────────────────

function handleClick(index: number) {
  activeIndex.value = index;
}

function handleAddSlot(index: number) {
  activeIndex.value = index;
  emit('add-slot', index);
}

function forwardH265Status(index: number, report: H265StatusReport) {
  emit('h265-status', { ...report, index });
}

function handleStreamStatus(index: number, report: StreamStatusReport) {
  if (videoList.value[index]?.id !== report.cameraId) return;
  const next = new Set(camerasWithoutSubstream.value);
  if (report.requestedStreamType === 'L1' && report.actualStreamType === 'L0' && report.fallbackUsed) {
    next.add(report.cameraId);
  } else if (report.requestedStreamType === 'L1' && report.actualStreamType === 'L1') {
    next.delete(report.cameraId);
  }
  camerasWithoutSubstream.value = next;
  emit('stream-status', { ...report, index });
}

/**
 * 双击：
 * - 当前非单屏模式 → 进入单屏（聚焦该格子）
 * - 已聚焦该格子    → 退出单屏，恢复宫格
 */
function handleDblClick(index: number) {
  if (focusedIndex.value === index) {
    focusedIndex.value = null;
    scheduleRestoreSubstream(index);
    return;
  }
  clearRestoreSubstreamTimer();
  focusedIndex.value = index;
  mainStreamIndex.value = index;
}

onBeforeUnmount(() => {
  clearRestoreSubstreamTimer();
  clearHiddenSuspendTimer();
});

// ─── 公开方法 ─────────────────────────────────────────────────────────────────

/** 在当前激活格子播放指定摄像头 */
function startPlay(cameraId: string, _legacyServerId?: string) {
  const index = activeIndex.value < layout.value.total ? activeIndex.value : 0;
  immediateStartCameraIds.set(index, cameraId);
  interactiveRequestCameraIds.set(index, cameraId);
  replaceSlot(index, { id: cameraId });
}

/** 停止指定格子播放（默认激活格子） */
function stopPlay(index?: number) {
  const idx = index ?? activeIndex.value;
  if (idx >= 0 && idx < MAX_WEBRTC_GRID_SLOTS && (idx < layout.value.total || idx < slotItems.value.length)) {
    playerRefs.value[idx]?.stopPlay();
    focusPausedPlayers.delete(idx);
    focusSuspendedPlayers.delete(idx);
    immediateStartCameraIds.delete(idx);
    interactiveRequestCameraIds.delete(idx);
    replaceSlot(idx, { id: '' });
  }
}

/** 停止所有格子 */
function stopAll() {
  playerRefs.value.forEach((p) => p?.stopPlay());
  focusPausedPlayers.clear();
  focusSuspendedPlayers.clear();
  clearHiddenSuspendTimer();
  hiddenSuspendEngaged = false;
  hiddenSuspendFocusedIndex = null;
  immediateStartCameraIds.clear();
  interactiveRequestCameraIds.clear();
  slotItems.value = [];
  emitVideoListUpdate();
}

function reconnectNow(index = activeIndex.value) {
  return playerRefs.value[index]?.reconnectNow();
}

function setStreamType(value: unknown, index = activeIndex.value) {
  return playerRefs.value[index]?.setStreamType(value);
}

function switchPlaybackMode(value: 'auto' | 'webrtc' | 'mse', index = activeIndex.value) {
  return playerRefs.value[index]?.switchPlaybackMode(value);
}

function getPlaybackDiagnostics(index = activeIndex.value) {
  return playerRefs.value[index]?.getPlaybackDiagnostics();
}

function copyPlaybackDiagnostics(index = activeIndex.value) {
  return playerRefs.value[index]?.copyPlaybackDiagnostics();
}

defineExpose({
  startPlay,
  stopPlay,
  stopAll,
  reconnectNow,
  setStreamType,
  switchPlaybackMode,
  getPlaybackDiagnostics,
  copyPlaybackDiagnostics,
});
</script>

<style scoped>
.grid-wrap {
  display: grid;
  grid-template-columns: repeat(var(--cols), minmax(0, 1fr));
  grid-template-rows: repeat(var(--rows), minmax(0, 1fr));
  position: absolute;
  inset: 0;
  background: #000;
  user-select: none;
}

.grid-wrap--one-plus-five > .grid-cell:first-child {
  grid-column: span 2;
  grid-row: span 2;
}

/* ─── 格子基础样式 ──────────────────────────────────────────────────────────── */

.grid-cell {
  position: relative;
  border: 1px solid #222;
  overflow: hidden;
  cursor: pointer;
  transition: border-color 0.15s;
}

.grid-cell:hover,
.grid-cell.cell-active {
  border-color: #ffcc00;
}

/* ─── 单屏聚焦 ──────────────────────────────────────────────────────────────── */

.grid-cell.cell-focused {
  position: absolute;
  inset: 0;
  z-index: 10;
  border: 2px solid #ffcc00;
}

/* 默认仅隐藏并保持连接；pauseHiddenOnFocus=true 时由组件显式暂停。 */
.grid-cell.cell-hidden {
  visibility: hidden;
}

/* ─── 空格子占位 ─────────────────────────────────────────────────────────────── */

.cell-empty {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #ffffff;
}

.cell-empty svg {
  width: 60px;
  height: 60px;
  opacity: 0.5;
}

.cell-empty span {
  font-size: 12px;
}

.cell-add-btn {
  position: absolute;
  top: 8px;
  right: 8px;
  z-index: 30;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.68);
  color: #ffffff;
  font-size: 20px;
  line-height: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition:
    transform 0.18s ease,
    background-color 0.18s ease;
}

.cell-add-btn:active {
  transform: scale(0.94);
}

.cell-add-btn:hover {
  background: rgba(42, 117, 246, 0.76);
}

.cell-name {
  position: absolute;
  left: 8px;
  right: 40px;
  bottom: 8px;
  z-index: 20;
  padding: 4px 8px;
  border-radius: 8px;
  background: rgba(0, 0, 0, 0.5);
  color: rgba(255, 255, 255, 0.92);
  font-size: 11px;
  line-height: 1.4;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  pointer-events: none;
}

/* ─── 单屏角标 ───────────────────────────────────────────────────────────────── */

.focus-badge {
  position: absolute;
  top: 6px;
  right: 8px;
  z-index: 20;
  padding: 2px 8px;
  background: rgba(0, 0, 0, 0.55);
  color: #ffcc00;
  font-size: 11px;
  border-radius: 4px;
  pointer-events: auto;
  cursor: pointer;
}
</style>
