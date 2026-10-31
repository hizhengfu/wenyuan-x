<template>
  <HkPlayer
    :video-code-list="videoCodeList"
    :split="localSplit"
    ref="refHkPlayer"
    :fun-preview="fetchPreview"
    :streamType="1"
  ></HkPlayer>
</template>
<script setup lang="ts">
import HkPlayer from '@/components/basic/hkPlayer/HKPlayer.vue';
import request from '@/utils/request';
import { ref, watch } from 'vue';

import { get, getSceneVideo } from '../api/scene';

const props = defineProps({
  sceneId: { type: String }, //场景id
  layout: { type: String, default: '3x3' }, //场景id
  initPlaying: { type: Boolean, default: false },
});

const sceneConfig = ref();

/**
 * 窗体分割方式
 * */
const localSplit = ref('3x3');

/**
 * 播放列表
 * */
const videoCodeList = ref<any[]>([]);
//海康播放器
const refHkPlayer = ref();

function fetchPreview(params: object) {
  return request({
    url: '/video/hik/preview',
    method: 'get',
    params,
  });
}

function handleLoadScene(id: string | undefined) {
  if (!id) {
    sceneConfig.value = {};
    videoCodeList.value = [];
    return;
  }
  get(id).then((resp: any) => {
    sceneConfig.value = resp.data || {};

    handleSceneVideoLoad(id, sceneConfig.value.layout);
  });
}

/**
 * 加载场景视频
 * */
function handleSceneVideoLoad(id: string, layout: string) {
  videoCodeList.value = [];
  localSplit.value = props.layout || '3x3';
  getSceneVideo(id).then((resp: any) => {
    if (resp.data) {
      let codeList: any[] = [];
      resp.data.forEach((item: any) => {
        codeList.push({ code: item.code, serverId: item.serverId });
      });
      videoCodeList.value = codeList;
      if (layout) {
        localSplit.value = layout.replace('*', 'x');
      }
    }
  });
}

watch(
  () => props.sceneId,
  (value) => {
    handleLoadScene(value);
  },
  { immediate: true },
);
</script>
