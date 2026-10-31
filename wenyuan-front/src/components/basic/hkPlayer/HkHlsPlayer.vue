<template>
  <div class="player-wrap">
    <template v-for="(items, index) in playerWindows" :key="index">
      <div class="player-row">
        <div
          class="player-col"
          v-for="(item, innerIndex) in items"
          :key="innerIndex"
          :class="{ active: isActive(index, innerIndex, items) }"
          @click="handleActiveWindow(index, innerIndex)"
        >
          <HlsPlayer v-if="item.code" :url="item.code" :funPreview="funPreview"></HlsPlayer>
        </div>
      </div>
    </template>
  </div>
</template>

<script lang="ts" setup>
import { ref, watchEffect, PropType } from 'vue';
import { chunk, times } from 'lodash-es';
import HlsPlayer from '../player/HlsPlayer.vue';
import request from '@/utils/request';

// 合并所有 props，使用 TypeScript 类型 + withDefaults
const props = withDefaults(
  defineProps<{
    split?: string;               // 分栏，如 "3x3" 或 "2+2"
    streamType?: number;          // 0 主码流，1 辅码流
    videoCodeList?: any[];        // 视频播放列表
    playerWindows?: any[][];      // 外部传入的窗口布局（如果外部管理）
    funPreview?: (params: object) => Promise<any>;
  }>(),
  {
    split: '3x3',
    streamType: 0,
    videoCodeList: () => [],
    playerWindows: () => [[]],
    funPreview: (params: object) =>
      request({
        url: 'video/hik/preview',
        method: 'get',
        params,
      }),
  }
);

// 当前激活窗口索引
const activeIndex = ref(0);
// 窗口每行数量
const playerWindowRow = ref(3);
// 内部维护的视频列表（一维）
const videoList = ref<any[]>([]);
// 内部维护的窗口布局（二维）
const playerWindows = ref<any[][]>([]);

/**
 * 判断当前窗口是否激活
 */
const isActive = (rowIdx: number, colIdx: number, row: any[]) => {
  return activeIndex.value === rowIdx * row.length + colIdx;
};

/**
 * 点击窗口激活
 */
function handleActiveWindow(outIndex: number, innerIndex: number) {
  activeIndex.value = outIndex * playerWindowRow.value + innerIndex;
}

/**
 * 初始化窗口布局
 */
function initVideoLayout(splitStr: string, codeList: any[]) {
  // 解析布局字符串，支持 "3x3" 或 "2+2"
  const splitArr = splitStr.split(/[+x]/).map(Number);
  const row = splitArr[0];
  let totalCells = 0;
  if (splitStr.includes('+')) {
    totalCells = splitArr[0] + splitArr[1];
  } else if (splitStr.includes('x')) {
    totalCells = splitArr[0] * splitArr[1];
  } else {
    totalCells = 4; // 默认
  }

  // 填充视频列表
  let list: any[] = [];
  if (codeList && codeList.length >= totalCells) {
    list = codeList.slice(0, totalCells);
  } else {
    list = codeList ? codeList.slice(0, totalCells) : [];
    const remaining = totalCells - list.length;
    if (remaining > 0) {
      times(remaining, () => {
        list.push({ code: '' });
      });
    }
  }

  playerWindowRow.value = row;
  videoList.value = list;
  playerWindows.value = chunk(list, row);
}

// 监听 split 和 videoCodeList 变化，重新初始化布局
watchEffect(() => {
  initVideoLayout(props.split, props.videoCodeList);
});

/**
 * 指定窗口播放视频（由父组件调用）
 */
function startPlay(videoCode: string) {
  if (videoList.value.length > activeIndex.value) {
    videoList.value[activeIndex.value] = { code: videoCode };
  } else {
    videoList.value[0] = { code: videoCode };
  }
  playerWindows.value = chunk(videoList.value, playerWindowRow.value);
}

/**
 * 停止某个视频播放（供父组件调用）
 */
function stopPlay(index: number) {
  videoList.value[index] = { code: '' };
}

defineExpose({
  startPlay,
  stopPlay,
});
</script>

<style scoped>
.player-wrap {
  display: flex;
  flex-wrap: nowrap;
  flex-direction: column;
  justify-content: space-around;
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  user-select: none;
  color: #ffffff;
}

.player-row {
  display: flex;
  flex: 1;
  justify-content: space-around !important;
  overflow: hidden;
}

.player-col {
  background-color: #000000;
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: center;
  border: 1px solid #666666;
  overflow: hidden;
  color: #ffffff;
}

.player-col.active,
.player-col:hover {
  border: 1px solid #ffff00;
}
</style>
