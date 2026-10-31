<template>
  <div>
    <div ref="refPlayer"></div>
    <div v-if="error" role="alert">{{ error }} <button @click="refresh">重新读取媒体</button></div>
  </div>
</template>
<script setup lang="ts">
import Player from 'xgplayer';
import 'xgplayer/dist/index.min.css';
import { ref } from 'vue';
import { useCurrentMediaPlayer } from './useCurrentMediaPlayer';
const props = defineProps({
  url: { type: String, required: true },
  customConfig: { type: Object, default: () => ({}) },
});
const refPlayer = ref<HTMLElement>();
const { error, refresh, startPlay, stopPlay } = useCurrentMediaPlayer({
  source: () => props.url,
  configuration: () => props.customConfig,
  create: (url, state) =>
    new Player({
      lang: 'zh-cn',
      autoplay: true,
      autoplayMuted: true,
      controls: true,
      fluid: true,
      closeVideoClick: true,
      plugins: [],
      ...props.customConfig,
      // DOM 和来源由组件管理；配置仍可调整控件、静音、插件和布局。
      el: refPlayer.value,
      url,
      ...(state.autoplay === undefined ? {} : { autoplay: state.autoplay }),
      ...(!props.customConfig.isLive && state.resumeTime ? { startTime: state.resumeTime } : {}),
    }),
});
defineExpose({ startPlay, stopPlay });
</script>
