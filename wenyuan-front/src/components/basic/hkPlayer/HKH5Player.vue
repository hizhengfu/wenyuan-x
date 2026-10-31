<template>
  <div :id="playerId" ref="refPlayer" v-element-size="handleResize" class="hk-player"></div>
</template>
<script setup lang="ts">
import {useEventBus, useScriptTag} from '@vueuse/core'
import {vElementSize} from '@vueuse/components'
import {onActivated, onDeactivated, onMounted, onUnmounted, PropType, ref, watch, watchEffect} from 'vue'
import {uuid} from '@/utils'
import request from '@/utils/request';

if (!ENABLE_HKPLAYER) {
  throw Error('请在config配置文件中设置enable_hkplayer为true')
}

const refPlayer = ref()
const player = ref()
const playerReady = ref(false)
//超时自动关闭视频
const timeoutCloseVideo = ref(false)
//播放器运行状态
const playerStatus = ref(true)
// 当前选中窗口，默认第一个
const currentWndIndex = ref(1)

const props = defineProps({
  split: {type: String, default: '3x3'},//分栏
  streamType: {type: Number, default: 0},//0 主码流，1辅码流
  funPreview: {
    type: Function,
    default(params: object) {
      return request({
        url: 'video/hik/preview',
        method: 'get',
        params
      })
    }
  },
  videoCodeList: {//视频播放列表
    type: Array as PropType<string[]>, default() {
      return []
    }
  }
})

const emits = defineEmits(['ready'])

const playerId = 'player-' + uuid()

useScriptTag(
    './hkplayer/h5/h5player.min.js',
    () => {
      // @ts-ignore
      let innerPlayer = new JSPlugin({
        iMaxSplit: 4,
        iCurrentSplit: +props.split[0],
        szId: playerId,
        szBasePath: './hkplayer/h5',
      })

      // 事件回调绑定
      innerPlayer.JS_SetWindowControlCallback({
        windowEventSelect: function (iWndIndex: number) {  //插件选中窗口回调
          // console.log('windowSelect callback: ', iWndIndex);
          currentWndIndex.value = iWndIndex
        },
        pluginErrorHandler: function (iWndIndex: number, iErrorCode: number, oError: string) {  //插件错误回调
          console.log('pluginError callback: ', iWndIndex, iErrorCode, oError);
          // 视频重连前先关闭该窗口释放内存,3秒重连一次
          stopPlay(iWndIndex).finally(() => {
            setTimeout(() => {
              // console.log("视频重连")
              //防止暂停线程后继续启动
              if (playerStatus.value) {
                startPlay(props.videoCodeList[iWndIndex], iWndIndex)
              }
            }, 3000)
          })
        },
        windowEventOver: function (iWndIndex: number) {  //鼠标移过回调
          //console.log(iWndIndex);
        },
        windowEventOut: function (iWndIndex: number) {  //鼠标移出回调
          //console.log(iWndIndex);
        },
        windowEventUp: function (iWndIndex: number) {  //鼠标mouseup事件回调
          //console.log(iWndIndex);
        },
        windowFullCcreenChange: function (bFull: boolean) {  //全屏切换回调
          // console.log('fullScreen callback: ', bFull);
        },
        firstFrameDisplay: function (iWndIndex: number, iWidth: number, iHeight: number) {  //首帧显示回调
          // console.log('firstFrame loaded callback: ', iWndIndex, iWidth, iHeight);
        },
        performanceLack: function () {  //性能不足回调
          // console.log('performanceLack callback: ');
        }
      });

      player.value = innerPlayer
      playerReady.value = true

      // 初始化完成之后回调
      emits('ready')
    }
)


onActivated(() => {
  console.log(11, 'onActivated')
  playerStatus.value = true
  handlePlayVideos()
})
onMounted(() => {
  console.log(12, 'onMounted')
  playerStatus.value = true
})

onDeactivated(() => {
  console.log(21, 'onDeactivated')
  playerStatus.value = false
  stopAllPlay()
})

onUnmounted(() => {
  console.log(22, 'onUnmounted')
  playerStatus.value = false
  stopAllPlay()
})

/**
 * 播放-单容器播放videoList多视频
 */
function handlePlayVideos() {
  const {videoCodeList, funPreview} = props
  if (!videoCodeList || videoCodeList.length === 0) return
  // let index = p.currentWindowIndex
  videoCodeList.forEach((item: any, index: number) => {
    startPlay(item.code, index)
  })
}

function handleResize() {
  player.value?.player?.JS_Resize()
}

watch(() => props.split, (newVal) => {
  
  splitWindow(+newVal[0])
})

// 对videoList进行监听
let lastVideoList: string[] = []
watchEffect(() => {
  const {videoCodeList, split} = props
  if (videoCodeList !== lastVideoList) {
    lastVideoList = videoCodeList
    handlePlayVideos()
  }
})

/**
 * 指定窗口播放指定视频
 * url: 播放地址
 * iWndIndex: 指定窗口
 */
function startPlay(url: string, index: number) {
  const {funPreview, streamType} = props
  if (!url) return
  if (!/wss?/.test(url)) {
    funPreview({videoCode: url, streamType: streamType, protocol: 4}).then((resp: any) => {
      console.log('播放地址', resp.data);
      play(resp.data, index || currentWndIndex.value)
    })
    return;
  }
}

/**
 * 播放
 * playURL：视频url
 * curIndex：当前播放窗口
 */
function play(playURL: string, curIndex: number) {
  player.value?.JS_Play(playURL, {playURL: playURL, mode: 1}, curIndex).then(
      () => {
        console.log('realplay success')
      },
      (e: any) => {
        console.error(e)
      }
  )
}

/**
 * 获取视频信息
 * curIndex：当前播放窗口
 */
function getVideoInfo(curIndex: number) {
  player.value?.JS_GetVideoInfo(curIndex).then(
      (videoInfo: any) => {
        console.info('VideoInfo:', videoInfo);
        // do you want...
      },
      (err: any) => {
        console.info('JS_GetVideoInfo failed');
        // do you want...
      }
  );
}

/**
 * 整体全屏
 */
function fullScreen() {
  player.value.JS_FullScreenDisplay(true).then(
      () => {
        console.info('JS_FullScreenDisplay success');
        // do you want...
      },
      (err: any) => {
        console.info('JS_FullScreenDisplay failed');
        // do you want...
      }
  );
}

function stopPlay(curIndex: number) {
  return player.value?.JS_Stop(curIndex).then(
      () => {
        console.info('JS_Stop success');
        // do you want...
      },
      (err: any) => {
        console.info('JS_Stop failed:', err);
        // do you want...
      }
  );
}

function stopAllPlay() {
  player.value?.JS_StopRealPlayAll().then(
      () => {
        console.info('JS_StopRealPlayAll success');
        // do you want...
      },
      (err: any) => {
        console.info('JS_StopRealPlayAll failed');
        // do you want...
      }
  );
}

/**
 * 宫格
 * @param splitNum 分屏数 1: 1x1, 2: 2x2, 3: 3x3, 4: 4x4
 */
function splitWindow(splitNum: Number) {
  player.value?.JS_ArrangeWindow(splitNum).then(
      () => {
        console.info('JS_ArrangeWindow success');
        // do you want...
      },
      (err: any) => {
        console.info('JS_ArrangeWindow failed');
        // do you want...
      }
  );
}

/**
 * 住工具栏切换，避免界面切换响应慢的问题
 * */
let lastPath = ''
const navBarEventBus = useEventBus<String>('NAV-BAR-EVENT')
navBarEventBus.on((path) => {
  if (lastPath !== path) {
    stopAllPlay()
  }
})

defineExpose({
  fullScreen,
  startPlay,
  stopPlay,
  stopAllPlay
})

</script>
<style lang="scss">
.hk-player {
  width: 100%;
  height: 100%;

  .parent-wnd .sub-wnd {
    // 宽高比 16/9
    aspect-ratio: 16/9 !important;
    // height: auto !important;
  }
}
</style>


