<template>
  <div ref="refPlayer"></div>
</template>
<script lang="ts" setup>
//@ts-ignore
import Player from 'xgplayer';
import HlsPlugin from 'xgplayer-hls';
import 'xgplayer/dist/index.min.css';
import { nextTick, onActivated, onDeactivated, onMounted, onUnmounted, ref, watch, watchEffect } from 'vue';
import request from '@/utils/request';
import { uuid } from '@/utils';

const playerId = 'player-' + uuid();
/**
 * 错误次数计数器
 * */
let errorCount = 0;
/**
 * 最大错误播放次数
 * */
const errorMaxCount = 1000;

const props = defineProps({
  url: { type: String, required: true },
  streamType: { type: Number, default: 0 }, //0 主码流，1辅码流
  funPreview: {
    type: Function,
    default(params: object) {
      return request({
        url: 'video/hik/preview',
        method: 'get',
        params,
      });
    },
  },
});

const refPlayer = ref();

let playerInstance: Player | undefined = undefined;
const isStop = ref(false);

/**
 * 初始化播放器
 * */
function initPlayer() {
  const { url } = props;
  if (url) {
    fetchPreviewUrl(url).then((realUrl) => {
      const config = {
        el: refPlayer.value,
        url: realUrl,
        lang: 'zh-cn',
        autoplay: true,
        autoplayMuted: true,
        controls: false,
        isLive: true,
        fluid: true,
        closeVideoClick: true,
        plugins: [],
      };

      if (!document.createElement('video').canPlayType('application/vnd.apple.mpegurl')) {
        config.isLive = true;
        //@ts-ignore
        config.plugins.push(HlsPlugin);
        // config.hls = {
        //   retryCount: 5,
        //   maxJumpDistance: 10,
        //   preloadTime: 30, // 默认值
        //   targetLatency: 10, // 直播目标延迟，默认 10 秒
        //   maxLatency: 20, // 直播允许的最大延迟，默认 20 秒
        //   disconnectTime: 0 // 直播断流时间，默认 0 秒，（独立使用时等于 maxLatency）
        // }
      }

      let player = new Player(config);
      player.on('error', (e: any) => {
        replay();
      });

      playerInstance = player;
    });
  }
}

/**
 *  播放错误时，自动重新播放
 * */
function replay() {
  const { url } = props;
  setTimeout(() => {
    console.log(`${props.url}尝试重新第${errorCount}次播放`);
    if (errorCount < errorMaxCount) {
      if (url) {
        if (!isStop.value) {
          playerInstance?.replay();
          // fetchPreviewUrl(url).then((realUrl) => {
          //   startPlay(realUrl)
          //   // replay()
          // })
        }
      }
    }
    errorCount++;
  }, 5000);
}

/**
 * 获取预览地址
 * */
function fetchPreviewUrl(value: string): Promise<string> {
  return new Promise((resolve, reject) => {
    if (!value) {
      reject(false);
      return;
    }
    const { funPreview, streamType } = props;
    if (/https?/.test(value)) {
      resolve(value);
    } else {
      funPreview &&
        funPreview({
          videoCode: value,
          streamType: streamType,
          protocol: '3',
        })
          .then((res: any) => {
            resolve(res.data);
          })
          .catch(() => {
            reject(false);
          });
    }
  });
}

/**
 * 播放地址改变时切换地址并播放
 * */
watch(
  () => props.url,
  (value, oldValue) => {
    fetchPreviewUrl(value as string);
  },
);

onMounted(() => {
  nextTick(() => {
    initPlayer();
  });
});
onUnmounted(() => {
  stopPlay();
});

onActivated(() => {
  const player = playerInstance;
  if (player && player.paused) {
    player.muted = true;
    player.play();
  }
});

onDeactivated(() => {
  const player = playerInstance;
  if (player && !player.paused) {
    player.pause();
  }
  isStop.value = true;
});

/**
 * 播放
 * */
function startPlay(url?: string) {
  const player = playerInstance;
  if (player && url) {
    player.switchURL(url)?.then(() => {
      errorCount = 0;
    });
  }
  isStop.value = false;
}

/**
 * 暂停
 */
function stopPlay() {
  const player = playerInstance;
  if (player && !player.paused) {
    player.pause();
  }
  isStop.value = true;
}

defineExpose({
  startPlay,
  stopPlay,
});
</script>
