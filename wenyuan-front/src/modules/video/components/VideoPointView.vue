<template>
  <n-layout has-sider class="wh-full" bordered embedded>
    <n-layout-sider
      v-model:collapsed="isCollapsed"
      collapse-mode="transform"
      :collapsed-width="0"
      :content-style="{
        padding: '0',
        borderWidth: '3px',
        borderStyle: 'solid',
        borderColor: borderColor,
        borderRadius: '10px',
      }"
      :width="260"
      :show-collapsed-content="false"
      show-trigger="bar"
      bordered
    >
      <n-layout-header>
        <n-tabs v-model:value="localTab" type="segment" size="large">
          <n-tab name="group" tab="视频目录" v-if="showGroup"></n-tab>
          <n-tab name="scene" tab="视频场景" v-if="scenceList > 0"></n-tab>
        </n-tabs>
      </n-layout-header>
      <n-layout-content position="absolute" style="top: 46px; bottom: 42px; left: 10px; right: 10px">
        <template v-if="localTab === 'group'">
          <VideoTree
            @click:item="handleVideoSelect"
            @successLoad="treeSuccessLoad"
            v-bind="$attrs"
            :group-id="groupId"
            :source-id="sourceId"
          ></VideoTree>
        </template>
        <template v-else>
          <VideoScene @click-scene:item="handleSceneSelect" :source-id="sourceId" @sucess:load="senceLoad"></VideoScene>
        </template>
      </n-layout-content>
      <n-layout-footer
        bordered
        position="absolute"
        style="
          bottom: 3px;
          left: 3px;
          right: 3px;
          padding: 3px;
          border-bottom-left-radius: 10px;
          border-bottom-right-radius: 10px;
        "
      >
        <n-flex justify="space-around" align="center" size="medium">
          <n-button-group>
            <n-button @click="localSplit = '1x1'">
              <template #icon>
                <n-icon><iconCgrid1></iconCgrid1></n-icon>
              </template>
              1x1
            </n-button>
            <n-button @click="localSplit = '2x2'">
              <template #icon>
                <n-icon><iconCgrid4></iconCgrid4></n-icon>
              </template>
              2x2
            </n-button>
            <n-button @click="localSplit = '3x3'">
              <template #icon>
                <n-icon><iconCgrid9></iconCgrid9></n-icon>
              </template>
              3x3
            </n-button>
          </n-button-group>
        </n-flex>
      </n-layout-footer>
    </n-layout-sider>
    <n-layout-content
      position="absolute"
      :content-style="{
        padding: '10px',
        borderWidth: '3px',
        borderStyle: 'solid',
        borderColor: borderColor,
        borderRadius: '10px',
        position: 'relative',
      }"
      :style="{ left: isCollapsed ? '26px' : '280px' }"
    >
      <HkPlayer
        :video-code-list="videoCodeList"
        :split="localSplit"
        ref="refHkPlayer"
        :fun-preview="fetchPreview"
        :stream-type="props.streamType"
      ></HkPlayer>
    </n-layout-content>
  </n-layout>
</template>
<script setup lang="ts">
import HkPlayer from '@/components/basic/hkPlayer/HKPlayer.vue';
import request from '@/utils/request';
import { ref } from 'vue';
import { useStorage } from '@vueuse/core';
import VideoTree from './VideoTree.vue';
import VideoScene from './VideoPointScene.vue';
import { camera, tree } from '../api/group';

const props = defineProps({
  defaultTab: { type: String, default: 'scene' },
  borderColor: { type: String, default: '#4A9ACB' },
  groupId: { type: String },
  sourceId: { type: String },
  initPlaying: { type: Boolean, default: false },
  showGroup: { type: Boolean, default: true },
  streamType: { type: Number, default: 1 }, //初始码流：0 主码流，1 子码流
});

// 使用独立 key，避免与 VideoView 共用 localStorage 互相污染
const localTab = useStorage('VIDEO-POINT-DEFAULT-TAB', props.defaultTab);
const localSplit = useStorage('VIDEO-POINT-DEFAULT-SPLIT', '1x1');

const isCollapsed = ref(false);
const videoCodeList = ref<any[]>([]);
const refHkPlayer = ref();

function fetchPreview(params: object) {
  return request({ url: '/video/hik/preview', method: 'get', params });
}

function handleVideoSelect(item: any) {
  if (item.code) {
    refHkPlayer.value?.startPlay(item.code, item.serverId);
  }
}

async function treeSuccessLoad(data: any) {
  if (props.initPlaying) {
    const resp = await tree({ id: props.groupId, sourceId: props.sourceId });
    if (resp.data) {
      const respData = resp.data;
      const promises = respData.map(async (item: any) => {
        const cameraResp = await camera(item.id);
        item.children = cameraResp.data || [];
      });
      await Promise.all(promises);
      const codeList = respData.flatMap(
        (item: any) =>
          item.children?.map((child: any) => ({ code: child.code, serverId: child.serverId })) || [],
      );
      if (codeList.length === 1) {
        localSplit.value = '1x1';
      } else if (codeList.length <= 4) {
        localSplit.value = '2x2';
      } else {
        localSplit.value = '3x3';
      }
      videoCodeList.value = codeList.length > 0 ? codeList : [];
      if (codeList.length === 0) localSplit.value = '1x1';
    }
  }
}

/**
 * 收到场景/视频条目选择事件
 * layout 来自 scene.layout（list 接口有该字段），无值时降级为 1*1
 */
function handleSceneSelect(items: any[], layout: string) {
  const codeList = items.map((item: any) => ({ code: item.code, serverId: item.serverId }));
  videoCodeList.value = codeList;
  // layout 统一用 * 分隔，转成 x 格式给 HkPlayer；无 layout 则 1x1
  localSplit.value = layout ? layout.replace('*', 'x') : '1x1';
}

const scenceList = ref(0);
function senceLoad(data: any) {
  scenceList.value = data;
  if (scenceList.value < 1 && props.showGroup === true) {
    localTab.value = 'group';
  }
}

function cuttingHKVideo(iLeft: number, iTop: number, iWidth: number, iHeight: number) {
  refHkPlayer.value.cuttingPartWindow(iLeft, iTop, iWidth, iHeight);
}

function repairHKVideo(iLeft: number, iTop: number, iWidth: number, iHeight: number) {
  refHkPlayer.value.repairPartWindow(iLeft, iTop, iWidth, iHeight);
}

defineExpose({ cuttingHKVideo, repairHKVideo });
</script>
<style lang="scss" scoped>
.video {
  position: absolute;
  display: flex;
  left: 0;
  right: 0;
  bottom: 0;
  top: 0;

  .sidebar {
    width: 300px;
    margin-right: 15px;
    color: #ffffff;
    overflow: hidden;
    box-sizing: border-box;
    position: relative;
    padding: 15px 0;

    .bg {
      position: absolute;
      left: 0;
      right: 0;
      bottom: 0;
      top: 0;
    }

    :deep(.n-tabs) {
      height: 100%;
    }

    :deep(.n-tab-pane) {
      position: relative;
      height: 100%;
    }
  }

  .video-content {
    position: relative;
    height: 100%;
    width: 100%;
    flex: 1;
    box-sizing: border-box;
  }
}

.scene-operate {
  position: absolute;
  right: 0;
  top: -100px;
}
</style>
