<template>
  <HkPlayer v-bind="$attrs" ref="hkPlayerRef"></HkPlayer>
</template>
<script setup lang="ts">
import {defineAsyncComponent, PropType, ref, shallowRef, watchEffect} from 'vue'
import {useAppStore} from '@/store'

const lastPath = ref()
const appStore = useAppStore()

const props = defineProps({
  playerModel: {
    type: String as PropType<'web' | 'hls' | 'h5'>
  }
})

watchEffect(() => {
  lastPath.value = appStore.path
})

const hkPlayerRef = ref()
let HkPlayer = shallowRef()
const userAgent = navigator.userAgent

const playerMap = {
  'web': defineAsyncComponent(() => import('./HKWebPlayer.vue')),
  'h5': defineAsyncComponent(() => import('./HKH5Player.vue')),
  'hls': defineAsyncComponent(() => import('./HkHlsPlayer.vue'))
}

if (props.playerModel) {
  HkPlayer.value = playerMap[props.playerModel]
} else {
  if (userAgent.indexOf('Windows') !== -1) {
    HkPlayer.value = playerMap.web
  } else if (userAgent.indexOf('Linux') !== -1) {
    HkPlayer.value = playerMap.h5
  } else {
    HkPlayer.value = playerMap.hls
  }
}


function startPlay(videoCode: string, windowIndex: number = -1, serverId: string) {
  if (hkPlayerRef.value?.startPlay) {
    hkPlayerRef.value.startPlay(videoCode, windowIndex, serverId)
  }
}

function stopPlay(currentWndIndex: number) {
  if (hkPlayerRef.value?.stopPlay) {
    hkPlayerRef.value.stopPlay(currentWndIndex)
  }
}

function rePlayerList() {
  if (hkPlayerRef.value?.rePlayerList) {
    hkPlayerRef.value.rePlayerList()
  }
}

function stopAllPlay() {
  if (hkPlayerRef.value?.stopAllPlay) {
    hkPlayerRef.value.stopAllPlay()
  }
}

function destroyWnd() {
  if (hkPlayerRef.value?.destroyWnd) {
    hkPlayerRef.value.destroyWnd()
  }
}

function fullScreen() {
  if (hkPlayerRef.value?.fullScreen) {
    hkPlayerRef.value.fullScreen()
  }
}

function exitFullScreen() {
  if (hkPlayerRef.value?.exitFullScreen) {
    hkPlayerRef.value.exitFullScreen()
  }
}

function resize() {
  if (hkPlayerRef.value?.resize) {
    hkPlayerRef.value.resize()
  }
}

function closeHkPlayer() {
  if (hkPlayerRef.value?.closeHkPlayer) {
    hkPlayerRef.value.closeHkPlayer()
  }
}

function cuttingPartWindow(iLeft: number, iTop: number, iWidth: number, iHeight: number) {
  if (hkPlayerRef.value?.repairPartWindow) {
    hkPlayerRef.value.cuttingPartWindow(iLeft, iTop, iWidth, iHeight)
  }
}

function repairPartWindow(iLeft: number, iTop: number, iWidth: number, iHeight: number) {
  if (hkPlayerRef.value?.repairPartWindow) {
    hkPlayerRef.value.repairPartWindow(iLeft, iTop, iWidth, iHeight)
  }
}

defineExpose({
  fullScreen,
  exitFullScreen,
  startPlay,
  rePlayerList,
  stopPlay,
  stopAllPlay,
  destroyWnd,
  cuttingPartWindow,
  repairPartWindow,
  resize,
  closeHkPlayer
})

</script>


