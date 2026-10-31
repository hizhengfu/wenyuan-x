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
            :relation-source-type="relationSourceType"
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
      <HkPlayer
        :video-code-list="videoCodeList"
        :split="localSplit"
        ref="refHkPlayer"
        :fun-preview="fetchPreview"
        :streamType="1"
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
import VideoScene from './VideoScene.vue';
import { camera, tree } from '../api/group';

const props = defineProps({
  defaultTab: { type: String, default: 'scene' }, //默认打开的tab
  borderColor: { type: String, default: '#4A9ACB' },
  groupId: { type: String }, //群组id
  sourceId: { type: String }, //群组关联id
  relationSourceType: { type: Number }, //摄像机关联对象类型；传入时不再读取旧视频分组关系
  initPlaying: { type: Boolean, default: false },
  showGroup: { type: Boolean, default: true },
});

/**
 * 默认打开场景tab
 * */
const localTab = props.relationSourceType == null
  ? useStorage('VIDEO-DEFAULT-TAB', props.defaultTab)
  : ref(props.defaultTab);

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
const refHkPlayer = ref();

function fetchPreview(params: object) {
  return request({
    url: '/video/hik/preview',
    method: 'get',
    params,
  });
}

/**
 * 视频单独选择
 * */
function handleVideoSelect(item: any) {
  if (item.code) {
    refHkPlayer.value?.startPlay(item.code, item.serverId);
  }
  // videoCodeList.value.push(code)
}

/**
 * 视频树加载
 * */
async function treeSuccessLoad(data: any) {
  if (props.initPlaying) {
    if (props.relationSourceType != null) {
      applyVideoCodeList(Array.isArray(data) ? data : []);
      return;
    }

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
      applyVideoCodeList(codeList);
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

function applyVideoCodeList(cameras: any[]) {
  const codeList = cameras
    .filter((item: any) => item?.code)
    .map((item: any) => ({ code: item.code, serverId: item.serverId }));

  if (codeList.length === 1 || codeList.length === 0) {
    localSplit.value = '1x1';
  } else if (codeList.length <= 4) {
    localSplit.value = '2x2';
  } else {
    localSplit.value = '3x3';
  }
  videoCodeList.value = codeList;
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
  console.log(videoCodeList.value, '------999');
}

const scenceList = ref(0);
/**
 * 场景数量
 * @param data
 */
function senceLoad(data: any) {
  scenceList.value = data;
  if (scenceList.value < 1 && props.showGroup == true) {
    localTab.value = 'group';
  }
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
  refHkPlayer.value.cuttingPartWindow(iLeft, iTop, iWidth, iHeight);
}

// 恢复视频插件窗口
function repairHKVideo(iLeft: number, iTop: number, iWidth: number, iHeight: number) {
  refHkPlayer.value.repairPartWindow(iLeft, iTop, iWidth, iHeight);
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
