<template>
  <div class="hls-video-container" id="hls-fullscreen">
    <div class="grid wh-full">
      <div class="grid-item" :class="['hls-video-box', { 'active-hls-video-box': selectWndIndex == index }]"
           :style="{ width: `calc(100% / ${split[0]})` }" v-for="(item, index) in +split[0] * +split[2]"
           @click="onVideoIndex(index)">
        <TcPlayer :url="videoCodeList[index] || videoUrl" ref="refPlayer"></TcPlayer>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {computed, nextTick, ref, onMounted, onActivated, onDeactivated} from 'vue'
import TcPlayer from '@/components/basic/player/TcPlayer.vue'

const props = defineProps({
  // 视频数组
  videoCodeList: {
    type: Array as any,
    default: () => []
  },
  split: {
    type: String,
    default: '3x3'
  },
  iWndIndex: {
    // 当前播放窗口
    type: Number,
    default: 0
  },
})

const emits = defineEmits(['onIndex', 'ready'])
const refPlayer = ref()
// 选中的视频索引
const selectWndIndex = ref(props.iWndIndex)

const onVideoIndex = (index: number) => {
  selectWndIndex.value = index
  emits('onIndex', index)
}

onMounted(() => {
  emits('ready', refPlayer.value)
})

const videoUrl = ref('')

function startPlay(videoCode: string) {
  videoUrl.value = videoCode
}

function stopPlay() {
  if (refPlayer.value?.stopPlay) {
    refPlayer.value.stopPlay()
  }
}

const isfullscreen = ref(false)

function fullScreen() {
  let element = document.getElementById('hls-fullscreen');
  if (isfullscreen.value) {
    if (document.exitFullscreen) {
      document.exitFullscreen();
    }
  } else {
    if (element?.requestFullscreen) {
      element.requestFullscreen();
    }
  }
  // isfullscreen.value = !isfullscreen.value
}


defineExpose({
  startPlay,
  stopPlay,
  fullScreen,
})

</script>

<style lang="scss" scoped>
.hls-video-container {
  width: 100%;
  height: 100%;
  background-color: #000;
  // overflow: hidden;

  .hls-video-box {
    border: 1px #2b2b2b solid;
    aspect-ratio: 16/9 !important;
    cursor: pointer;
  }

  .active-hls-video-box {
    border: 1px #fecb29 solid;
    aspect-ratio: 16/9 !important;
  }

  .xgplayer {
    width: 100% !important;
    height: 100% !important;
  }
}

.grid {
  display: flex;
  flex-wrap: wrap;

  .grid-item {
    box-sizing: border-box;
  }
}
</style>
