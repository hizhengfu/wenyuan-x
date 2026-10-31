<template>
  <div class="scene-tree">
    <div class="scene-tree-search">
      <n-input size="medium" v-model:value="pattern" placeholder="快速检索" />
    </div>
    <n-divider style="padding: 0; margin: 6px 0"></n-divider>
    <div class="scene-tree-content">
      <n-tree
        block-line
        :data="sceneList"
        :pattern="pattern"
        :show-irrelevant-nodes="false"
        key-field="id"
        label-field="name"
        :node-props="nodeProps"
        selectable
        :selected-keys="selectedKeys"
        @update:selected-keys="handleSelectedKeysChange"
      />
      <n-dropdown
        trigger="manual"
        placement="bottom-start"
        :show="showDropdown"
        :options="dropdownOptions"
        :x="x"
        :y="y"
        @select="handleDropdownSelect"
        @clickoutside="handleClickOutside"
      >
      </n-dropdown>
      <HDialogForm
        :model="sceneForm"
        :rules="sceneRules"
        title="修改场景"
        width="360px"
        :handleSubmit="handleSaveScene"
        v-model:show="sceneModal"
      >
        <n-form-item label="场景名称">
          <n-input v-model:value="sceneForm.name"></n-input>
        </n-form-item>
      </HDialogForm>
    </div>
  </div>
</template>
<script lang="ts" setup>
import { onActivated, ref, nextTick, inject } from 'vue';
import { del, edit, getSceneVideo, list } from '../api/scene';
import { DropdownOption, TreeOption } from 'naive-ui';
import { confirm } from '@/utils';
import { useStorage } from '@vueuse/core';
import { findIndex } from 'lodash-es';

const isShowAllSence = inject<boolean>('IS_SHOW_All_SENCE', false);
const props = defineProps({
  sourceId: { type: String },
});

const sceneState = useStorage<string | null>('_POINT_SCENE_ID', null);

const emits = defineEmits(['clickScene:item', 'scene:load', 'sucess:load']);

const pattern = ref();
const sceneList = ref();
const isVideoItemTree = ref(false);
const selectedKeys = ref<string[]>([]);
// 缓存当前场景的 layout，供视频条目点击时使用
const currentSceneLayout = ref<string>('');

const showDropdown = ref(false);
const dropdownOptions = ref<DropdownOption[]>([
  { label: '修改名称', key: 'edit' },
  { label: '删除场景', key: 'delete' },
]);
const x = ref(0);
const y = ref(0);

const sceneModal = ref(false);
const activeScene = ref();
const sceneForm = ref({ name: '' });
const sceneRules = { name: { required: true, message: '请输入场景名称' } };

function handleDropdownSelect(key: string | number, option: DropdownOption) {
  showDropdown.value = false;
  if (key === 'edit') {
    sceneModal.value = true;
    sceneForm.value.name = activeScene.value.name;
  } else if (key === 'delete') {
    confirm({
      title: '请注意',
      type: 'error',
      content: '点击确认将删除当前场景',
      onPositiveClick: () => { del(activeScene.value.id); },
    });
  }
}

function handleClickOutside() {
  showDropdown.value = false;
}

function handleSaveScene() {
  return edit(Object.assign({}, activeScene.value, sceneForm.value)).then(() => { loadData(); });
}

function handleSelectedKeysChange(keys: string[]) {
  selectedKeys.value = keys;
  if (keys.length > 0) {
    if (isVideoItemTree.value) {
      handleVideoItemSelect(keys[0]);
    } else {
      handleSceneSelect(keys[0]);
    }
  }
}

function handleVideoItemSelect(id: string | null) {
  if (!id) {
    selectedKeys.value = [];
    emits('clickScene:item', [], '1*1');
    return;
  }
  selectedKeys.value = [id];
  const videoItem = sceneList.value?.find((item: any) => item.id === id);
  // 点击单个视频条目 → 1*1；无 layout 缓存时也默认 1*1
  emits('clickScene:item', videoItem ? [videoItem] : [], '1*1');
}

function handleSceneSelect(id: string | null) {
  if (!id) {
    selectedKeys.value = [];
    sceneState.value = null;
    emits('clickScene:item', [], '1*1');
    return;
  }
  selectedKeys.value = [id];
  getSceneVideo(id)
    .then((resp: any) => {
      sceneState.value = id;
      const sceneConfig = sceneList.value.find((item: any) => item.id === id);
      const layout = sceneConfig?.layout || '1*1';
      emits('clickScene:item', resp.data || [], layout);
    })
    .catch(() => {
      selectedKeys.value = [];
      sceneState.value = null;
    });
}

function nodeProps({ option }: { option: TreeOption }) {
  return {
    onClick() {
      if (isVideoItemTree.value) {
        handleVideoItemSelect(option.id as string);
      } else {
        handleSceneSelect(option.id as string);
      }
    },
    onContextmenu(e: MouseEvent): void {
      if (isVideoItemTree.value) return;
      activeScene.value = option;
      showDropdown.value = true;
      x.value = e.clientX;
      y.value = e.clientY;
      e.preventDefault();
    },
  };
}

const sceneParams = ref({} as any);

async function loadSceneVideosAsTree(scenes: any[]) {
  const targetScene =
    scenes.find((item: any) => item.sourceId == props.sourceId || item.id == props.sourceId) ||
    scenes[0];

  if (!targetScene) {
    isVideoItemTree.value = true;
    sceneList.value = [];
    selectedKeys.value = [];
    currentSceneLayout.value = '';
    emits('sucess:load', 0);
    emits('clickScene:item', [], '1*1');
    return;
  }

  // 从 list 接口缓存 layout，getSceneVideo 返回的视频里没有此字段
  currentSceneLayout.value = targetScene.layout || '1*1';

  const resp = await getSceneVideo(targetScene.id);
  const videoList = resp.data || [];
  isVideoItemTree.value = true;
  sceneList.value = videoList;
  emits('sucess:load', videoList.length);

  if (videoList.length > 0) {
    selectedKeys.value = [videoList[0].id];
    emits('clickScene:item', videoList, currentSceneLayout.value);
  } else {
    selectedKeys.value = [];
    emits('clickScene:item', [], '1*1');
  }
}

function loadData() {
  sceneParams.value = isShowAllSence
    ? { status: 0, pageSize: -1 }
    : { status: 0, pageSize: -1, sourceId: props.sourceId };

  list(sceneParams.value)
    .then((resp: any) => {
      const respList = resp.data?.list || [];
      if (props.sourceId) {
        return loadSceneVideosAsTree(
          respList.filter((item: any) => item.sourceId == props.sourceId || item.id == props.sourceId),
        );
      }

      isVideoItemTree.value = false;
      sceneList.value = respList;
      emits('sucess:load', sceneList.value.length);

      if (sceneList.value && sceneList.value.length > 0) {
        let targetSceneId: string;
        if (isShowAllSence) {
          const targetScene = sceneList.value.find((item: any) => item.sourceId == props.sourceId);
          targetSceneId = targetScene ? targetScene.id : sceneList.value[0].id;
        } else {
          if (sceneState.value && findIndex(sceneList.value, { id: sceneState.value }) > -1) {
            targetSceneId = sceneState.value;
          } else {
            targetSceneId = sceneList.value[0].id;
          }
        }
        nextTick(() => { handleSceneSelect(targetSceneId); });
      } else {
        selectedKeys.value = [];
        handleSceneSelect(null);
      }
    })
    .catch(() => {
      isVideoItemTree.value = false;
      selectedKeys.value = [];
      handleSceneSelect(null);
    });
}

onActivated(() => { loadData(); });
loadData();
</script>
<style lang="scss" scoped>
.scene-tree {
  display: flex;
  flex-direction: column;
  position: absolute;
  top: 0;
  bottom: 0;
  left: 0;
  right: 0;

  .scene-tree-search {
    padding: 15px 15px 0 15px;
  }

  .scene-tree-content {
    flex: 1;
    overflow: auto;
  }
}
</style>
