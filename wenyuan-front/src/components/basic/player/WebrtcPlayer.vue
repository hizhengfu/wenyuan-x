<template>
  <Go2rtcPlayerView
    ref="playerRef"
    :camera-id="cameraId"
    :camera-name="cameraName"
    :fun-play="funPlay"
    :stream-type="streamType"
    :allow-confirmed-substream-fallback="allowConfirmedSubstreamFallback"
    :show-stream-switch="showStreamSwitch"
    :mode="mode"
    :ice-servers="iceServers"
    :receive-audio="receiveAudio"
    :start-delay-ms="startDelayMs"
    @h265-status="emit('h265-status', $event)"
    @stream-status="emit('stream-status', $event)"
  />
</template>

<script lang="ts" setup>
import { ref, type PropType } from 'vue';
import Go2rtcPlayerView from './webrtc/Go2rtcPlayerView.vue';
import { probeH265Capability, type H265StatusReport } from './webrtc/h265Capability';
import { defaultPlayerFunPlay, type PlayerFunPlay, type StreamStatusReport } from './webrtc/playerTypes';

const props = defineProps({
  cameraId: { type: String, default: '' },
  cameraName: { type: String, default: '' },
  funPlay: {
    type: Function as PropType<PlayerFunPlay>,
    default: defaultPlayerFunPlay,
  },
  streamType: { type: [String, Number], default: 'auto' },
  /** 布局自动升主码流失败时，允许回退到本会话已确认可播的子码流。 */
  allowConfirmedSubstreamFallback: { type: Boolean, default: false },
  showStreamSwitch: { type: Boolean, default: true },
  mode: { type: String, default: 'webrtc,mse' },
  iceServers: { type: Array as () => RTCIceServer[], default: () => [] },
  /** 单路播放器默认保留音频；宫格会显式关闭，避免为每格协商和接收无用音轨。 */
  receiveAudio: { type: Boolean, default: true },
  /** 宫格分批启动使用；单播放器默认立即启动。 */
  startDelayMs: { type: Number, default: 0 },
});

// 提前启动页面级共享能力探测；真正的 profile 决策仍由 View 在 /play 前等待同一个 Promise。
void probeH265Capability().catch(() => undefined);

const emit = defineEmits<{
  (event: 'h265-status', report: H265StatusReport): void;
  (event: 'stream-status', report: StreamStatusReport): void;
}>();
const playerRef = ref<InstanceType<typeof Go2rtcPlayerView>>();

function startPlay() {
  return playerRef.value?.startPlay();
}

function stopPlay() {
  return playerRef.value?.stopPlay();
}

function suspendPlay() {
  return playerRef.value?.suspendPlay();
}

function resumePlay() {
  return playerRef.value?.resumePlay();
}

function reconnectNow() {
  return playerRef.value?.reconnectNow();
}

function setStreamType(value: unknown) {
  return playerRef.value?.setStreamType(value);
}

function switchPlaybackMode(value: 'auto' | 'webrtc' | 'mse') {
  return playerRef.value?.switchPlaybackMode(value);
}

function getPlaybackDiagnostics() {
  return playerRef.value?.getPlaybackDiagnostics();
}

function copyPlaybackDiagnostics() {
  return playerRef.value?.copyPlaybackDiagnostics();
}

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
