<template>
  <div class="wh-full" style="position: relative">
    <div ref="refPlayer"></div>
    <div class="canvasWrap"><canvas ref="refCanvas" class="canvas"></canvas></div>
    <div v-if="error" role="alert">{{ error }} <button @click="refresh">重新读取媒体</button></div>
  </div>
</template>
<script setup lang="ts">
import Player from 'xgplayer';
import MusicPreset, { Analyze } from 'xgplayer-music';
import 'xgplayer/dist/index.min.css';
import { ref } from 'vue';
import { useCurrentMediaPlayer } from './useCurrentMediaPlayer';
const props = defineProps({ url: { type: String, required: true } });
const refPlayer = ref<HTMLElement>(),
  refCanvas = ref<HTMLCanvasElement>();
const { error, refresh, startPlay, stopPlay } = useCurrentMediaPlayer({
  source: () => props.url,
  create: (url, state) =>
    new Player({
      el: refPlayer.value,
      url,
      mediaType: 'audio',
      lang: 'zh-cn',
      width: '100%',
      height: '100%',
      presets: ['default', MusicPreset],
      ignores: ['playbackrate'],
      controls: { initShow: true, mode: 'flex' },
      autoplay: state.autoplay ?? true,
      marginControls: true,
      videoConfig: { crossOrigin: 'anonymous' },
      ...(state.resumeTime ? { startTime: state.resumeTime } : {}),
    }),
  ready: (player) => {
    const canvas = refCanvas.value;
    if (!canvas || !Analyze.AudioCtx) return;
    const analyze = new Analyze(player, canvas, { bgColor: 'rgba(0,0,0,0.7)', stroke: 3 });
    let disposed = false;
    const resume = () => {
      if (analyze._audioCtx?.state !== 'suspended') return;
      void analyze._audioCtx
        .resume()
        .then(() => {
          if (!disposed && error.value === '音频输出未启用，请使用播放控件重试。') error.value = '';
        })
        .catch(() => {
          if (!disposed) error.value = '音频输出未启用，请使用播放控件重试。';
        });
    };
    const pause = () => analyze.stop();
    player.on('pause', pause);
    player.on('play', resume);
    if (!player.paused) {
      resume();
      analyze.start();
    }
    return () => {
      disposed = true;
      player.off('pause', pause);
      player.off('play', resume);
      analyze.destroy();
      canvas.getContext('2d')?.clearRect(0, 0, canvas.width, canvas.height);
    };
  },
});
defineExpose({ startPlay, stopPlay });
</script>
<style lang="scss" scoped>
.canvasWrap {
  width: 100%;
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 45px;
  pointer-events: none;
  canvas {
    width: 100%;
    bottom: 0;
  }
}
</style>
