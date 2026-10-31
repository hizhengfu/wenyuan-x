<template>
  <video
      :id="playerId"
      ref="refPlayer"
      style="height: 100%;width: 100%;object-fit: fill;"
      preload="auto"
      playsinline
      webkit-playsinline
      @dblclick="handleDbClick"></video>
</template>
<script lang="ts" setup>
/**
 * 腾讯直播播放器
 * @see https://cloud.tencent.com/document/product/881/30818
 * API文档
 * @see https://cloud.tencent.com/document/product/881/30820
 * */
//@ts-ignore
import TCPlayer from 'tcplayer.js';
import 'tcplayer.js/dist/tcplayer.min.css';
import request from '@/utils/request';

import {nextTick, onActivated, onDeactivated, onMounted, onUnmounted, ref, watch} from 'vue';
import {uuid} from '@/utils';

const playerId = 'player-' + uuid()

const props = defineProps({
  url: {type: String},
  streamType: {type: Number, default: 0},//0 主码流，1辅码流
  funPreview: {
    type: Function,
    default(params: object) {
      return request({
        url: 'relation/video/preview',
        method: 'get',
        params
      })
    }
  }
})

const refPlayer = ref()

const isStop = ref(false)

let playerInstance = ref()

/**
 * 初始化播放器
 * */
async function initPlayer() {
  const {url} = props
  let player = TCPlayer(playerId, {
    autoplay: true,//自动播放
    muted: true,//静音
    live: true,//直播模式
    controls: false,//是否显示控制栏
    reportable: false,//数据上报
    hlsConfig: {},
    plugins: {
      ContextMenu: {
        statistic: true,
      }
    }
  })
  player.on('error', (error: any) => {
        console.log(11, error)
        replay()
      }
  )

  playerInstance.value = player
  if (url) {
    fetchPreviewUrl(url)
  }
}


/**
 * 错误次数计数器
 * */
let errorCount = 0
/**
 * 最大错误播放次数
 * */
const errorMaxCount = 1000

/**
 *  播放错误时，自动重新播放
 * */
function replay() {
  const {url} = props
  setTimeout(() => {
    console.log(`${props.url}尝试重新第${errorCount}次播放`)
    if (errorCount < errorMaxCount) {
      if (url) {
        if (!isStop.value) {
          fetchPreviewUrl(url)
        }
      }
    }
    errorCount++
  }, 5000)
}

let cacheUrl: string | undefined = undefined

/**
 * 播放
 * */
function startPlay(url?: string) {
  const player = playerInstance.value
  if (player) {
    player.src(url)
    cacheUrl = url
    if (url) {
      player.play().then(() => {
        errorCount = 0
      })
    }
  }
  isStop.value = false
}

/**
 * 暂停
 */
function stopPlay() {
  playerInstance.value?.pause()
  isStop.value = true
}

function closePlay() {
  stopPlay()
  const player = playerInstance.value
  if (player) {
    player.src()
  }
}

/**
 * 获取预览地址
 * */
function fetchPreviewUrl(value: string) {
  return new Promise((resolve, reject) => {
    if (!value) {
      reject(false)
      return
    }
    const {funPreview, streamType} = props
    if (/https?/.test(value)) {
      startPlay(value)
      resolve(true)
    } else if (cacheUrl && errorCount % 1000 === 0) {
      startPlay(cacheUrl)
    } else {
      funPreview && funPreview({
        videoCode: value,
        streamType: streamType,
        protocol: '3'
      }).then((res: any) => {
        // console.log('hls地址--->', res.data);
        startPlay(res.data)
        resolve(true)
      }).catch(() => {
        reject(false)
      })
    }
  })
}

/**
 * 双击事件
 * */
function handleDbClick() {
  const player = playerInstance.value
  if (player) {
    if (player.isFullscreen()) {
      player.exitFullscreen()
    } else {
      player.requestFullscreen()
    }
  }
}

/**
 * 播放地址改变时切换地址并播放
 * */
watch(() => props.url, (value, oldValue) => {
  // 先关闭当前视频
  closePlay()
  fetchPreviewUrl(value as string)
})


onMounted(() => {
  nextTick(() => {
    initPlayer()
  })
})

onUnmounted(() => {
  const player = playerInstance.value
  if (player) {
    player.dispose()
  }
})

onActivated(() => {
  playerInstance.value?.play()
  // initPlayer()
})
onDeactivated(() => {
  stopPlay()
  // closePlay()
})

defineExpose({
  startPlay,
  stopPlay,
})
</script>
