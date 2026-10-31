<template>
  <div ref="refPlayer"></div>
</template>
<script lang="ts" setup>
import Player from 'xgplayer';
// @ts-ignore
import FLvPlugin from 'xgplayer-flv';
import "xgplayer/dist/index.min.css"

import {nextTick, onMounted, ref, watchEffect} from 'vue';

const props = defineProps({
  url: {type: String, required: true}
})

const refPlayer = ref()

let playerInstance = ref()

function initPlayer() {
  playerInstance.value = new Player({
    el: refPlayer.value,
    url: props.url,
    isLive: true,
    lang: 'zh-cn',
    screenShot: true,
    autoplay: true,
    plugins: [FLvPlugin]
  });
}

onMounted(() => {
  nextTick(() => {
    initPlayer()
  })
})

watchEffect(() => {
  const player = playerInstance.value
  if (player && props.url) {
    if (player.src !== props.url) {
      player.src = props.url
      setTimeout(() => {
        player.play()
      }, 1000)
    }
  }
})
</script>
