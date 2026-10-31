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
      <n-layout-header v-if="availableTabs.length > 0">
        <n-tabs v-model:value="activeTab" type="segment" size="large">
          <n-tab v-if="availableTabs.includes('group')" name="group" tab="视频目录"></n-tab>
          <n-tab v-if="availableTabs.includes('scene')" name="scene" tab="视频场景"></n-tab>
        </n-tabs>
      </n-layout-header>
      <n-layout-content position="absolute" style="top: 46px; bottom: 42px; left: 10px; right: 10px">
        <template v-if="!sceneLoaded || activeTab === 'scene'">
          <VideoScene @click-scene:item="handleSceneSelect" :source-id="sourceId" @sucess:load="senceLoad"></VideoScene>
        </template>
        <template v-else-if="activeTab === 'group'">
          <VideoTree
            @click:item="handleVideoSelect"
            @successLoad="treeSuccessLoad"
            v-bind="$attrs"
            :group-id="groupId"
            :source-id="sourceId"
          ></VideoTree>
        </template>
        <n-empty v-else description="暂无可用视频" />
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
                <n-icon>
                  <iconCgrid1></iconCgrid1>
                </n-icon>
              </template>
              1x1
            </n-button>
            <n-button @click="localSplit = '2x2'">
              <template #icon>
                <n-icon>
                  <iconCgrid4></iconCgrid4>
                </n-icon>
              </template>
              2x2
            </n-button>
            <n-button @click="localSplit = '3x3'">
              <template #icon>
                <n-icon>
                  <iconCgrid9></iconCgrid9>
                </n-icon>
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
      <WebrtcGridPlayer
        :video-code-list="videoCodeList"
        :split="localSplit"
        ref="refWebRtcPlayer"
        :stream-type="props.streamType"
        :receive-audio="true"
      ></WebrtcGridPlayer>
    </n-layout-content>
  </n-layout>
</template>
<script setup lang="ts">
import WebrtcGridPlayer from '@/components/basic/player/WebrtcGridPlayer.vue';
import { computed, ref } from 'vue';
import { useStorage } from '@vueuse/core';
import VideoTree from './VideoTree.vue';
import VideoScene from './VideoScene.vue';
import { camera, tree } from '../api/group';

const props = defineProps({
  defaultTab: { type: String, default: 'scene' }, //默认打开的tab
  borderColor: { type: String, default: '#4A9ACB' },
  groupId: { type: String }, //群组id
  sourceId: { type: String }, //群组关联id
  initPlaying: { type: Boolean, default: false },
  showGroup: { type: Boolean, default: true },
  streamType: { type: [String, Number], default: 'auto' }, //初始码流：L0/0 主码流，L1/1 子码流
});

type VideoTab = 'group' | 'scene';

const scenceList = ref(0);
const sceneLoaded = ref(false);

/**
 * 默认打开场景tab
 * */
const localTab = useStorage<VideoTab>('VIDEO-DEFAULT-TAB', props.defaultTab as VideoTab);

/** 当前组件实际可以展示的页签。场景数据加载完成前保持为空，先由 VideoScene 完成探测。 */
const availableTabs = computed<VideoTab[]>(() => {
  if (!sceneLoaded.value) return [];

  const tabs: VideoTab[] = [];
  if (props.showGroup) tabs.push('group');
  if (scenceList.value > 0) tabs.push('scene');
  return tabs;
});

/**
 * 页面实际使用的页签。
 * 单页签时临时回退到唯一可用项，不覆盖 localTab 中保存的用户偏好。
 */
const activeTab = computed<VideoTab | undefined>({
  get() {
    const tabs = availableTabs.value;
    if (tabs.includes(localTab.value)) return localTab.value;

    const defaultTab = props.defaultTab as VideoTab;
    if (tabs.includes(defaultTab)) return defaultTab;
    return tabs[0];
  },
  set(value) {
    if (value && availableTabs.value.length > 1 && availableTabs.value.includes(value)) {
      localTab.value = value;
    }
  },
});

/**
 * 窗体分割方式
 * */
const localSplit = useStorage('VIDEO-DEFAULT-SPLIT', '3x3');
// const localSplit = ref('3x3');
/**
 * 是否折叠
 * */
const isCollapsed = ref(false);
/**
 * 播放列表
 * */
const videoCodeList = ref<any[]>([]);
//海康播放器
const refWebRtcPlayer = ref();

/**
 * 视频单独选择
 * */
function handleVideoSelect(item: any) {
  if (item.code) {
    refWebRtcPlayer.value?.startPlay(item.code, item.serverId);
  }
  // videoCodeList.value.push(code)
}

/**
 * 视频树加载
 * */
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
          item.children?.map((child: any) => ({
            code: child.code,
            serverId: child.serverId,
          })) || [],
      );
      if (codeList.length > 0) {
        if (codeList.length == 1) {
          localSplit.value = '1x1';
        } else if (codeList.length > 1 && codeList.length <= 4) {
          localSplit.value = '2x2';
        } else {
          localSplit.value = '3x3';
        }
      } else {
        localSplit.value = '1x1';
      }
      videoCodeList.value = codeList;
    }

    //     if (data.children && data.children.length > 0) {
    //      if(data.children.length == 1){

    //         localSplit.value = '1x1'
    //         console.log( localSplit.value,'3434453');
    //      }else if(data.children.length > 1 && data.children.length <=4){
    //         localSplit.value = '2x2'
    //      }else{
    //         localSplit.value = '3x3'
    //      }
    //       data.children.forEach((child:any) => {
    //         codeList.push({ code: child.code, serverId: child.serverId });
    //       });
    //     } else {
    //       localSplit.value = '1x1'
    //       codeList.push({ code: data.code, serverId: data.serverId });
    //     }

    // videoCodeList.value = codeList;
  }
}

/**
 * 场景批量选择
 * @param layout 场景单独的布局
 * */
function handleSceneSelect(items: any[], layout: string) {
  videoCodeList.value = [];
  let codeList: any[] = [];
  items.forEach((item: any) => {
    codeList.push({ code: item.code, serverId: item.serverId });
  });
  videoCodeList.value = codeList;
  if (layout) {
    localSplit.value = layout.replace('*', 'x');
  }
}

/**
 * 场景数量
 * @param data
 */
function senceLoad(data: any) {
  scenceList.value = Number(data) || 0;
  sceneLoaded.value = true;
}

/**
 * 场景存储相关
 * */
const sceneModal = ref(false);

const sceneForm = ref({
  name: '',
});

const sceneRules = {
  name: {
    required: true,
    message: '请输入场景名称',
  },
};

/**
 * 保存当前视频列表为场景
 * */
function handleSaveScene() {
  sceneModal.value = true;
}

// 裁剪视频插件以取消遮挡
function cuttingHKVideo(iLeft: number, iTop: number, iWidth: number, iHeight: number) {
  refWebRtcPlayer.value.cuttingPartWindow(iLeft, iTop, iWidth, iHeight);
}

// 恢复视频插件窗口
function repairHKVideo(iLeft: number, iTop: number, iWidth: number, iHeight: number) {
  refWebRtcPlayer.value.repairPartWindow(iLeft, iTop, iWidth, iHeight);
}

defineExpose({
  cuttingHKVideo,
  repairHKVideo,
});
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
