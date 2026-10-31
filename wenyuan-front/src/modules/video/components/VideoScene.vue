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

/**
 * 本地存储场景最后选择的内容
 */
const sceneState = useStorage<string | null>('_SCENE_ID', null);

const emits = defineEmits(['clickScene:item', 'scene:load','sucess:load']);

const pattern = ref();
const sceneList = ref();
// 选中状态管理
const selectedKeys = ref<string[]>([]);

/**
 * 右键菜单相关
 */
const showDropdown = ref(false);
const dropdownOptions = ref<DropdownOption[]>([
  {
    label: '修改名称',
    key: 'edit',
  },
  {
    label: '删除场景',
    key: 'delete',
  },
]);
const x = ref(0);
const y = ref(0);

/**
 * 场景存储相关
 */
const sceneModal = ref(false);
const activeScene = ref();

const sceneForm = ref({
  name: '',
});

const sceneRules = {
  name: {
    required: true,
    message: '请输入场景名称',
  },
};

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
      onPositiveClick: () => {
        del(activeScene.value.id);
      },
    });
  }
}

function handleClickOutside() {
  showDropdown.value = false;
}

/**
 * 保存当前场景配置
 */
function handleSaveScene() {
  return edit(Object.assign({}, activeScene.value, sceneForm.value)).then(() => {
    loadData();
  });
}

/**
 * 处理树选中状态变化
 */
function handleSelectedKeysChange(keys: string[]) {
  selectedKeys.value = keys;
  if (keys.length > 0) {
    handleSceneSelect(keys[0]);
  }
}

/**
 * 场景选择
 */
function handleSceneSelect(id: string | null) {
  // 当id不存在时直接返回空的数组
  if (!id) {
    selectedKeys.value = [];
    sceneState.value = null;
    emits('clickScene:item', []);
    return;
  }

  // 更新选中状态
  selectedKeys.value = [id];

  getSceneVideo(id)
    .then((resp: any) => {
      sceneState.value = id;
      let sceneConfig = sceneList.value.find((item: any) => item.id === id);
      if (resp.data) {
        emits('clickScene:item', resp.data, sceneConfig?.layout);
      } else {
        emits('clickScene:item', [], sceneConfig?.layout);
      }
    })
    .catch((error) => {
      console.error('获取场景视频失败:', error);
      // 失败时重置状态
      selectedKeys.value = [];
      sceneState.value = null;
    });
}

function nodeProps({ option }: { option: TreeOption }) {
  return {
    onClick() {
      handleSceneSelect(option.id as string);
    },
    onContextmenu(e: MouseEvent): void {
      activeScene.value = option;
      showDropdown.value = true;
      x.value = e.clientX;
      y.value = e.clientY;
      e.preventDefault();
    },
  };
}

const sceneParams = ref({} as any);
/**
 * 加载场景结构
 */
function loadData() {
  if (isShowAllSence) {
    sceneParams.value = {
      status: 0,
      pageSize: -1,
    };
  } else {
    sceneParams.value = {
      status: 0,
      pageSize: -1,
      sourceId: props.sourceId,
    };
  }

  list(sceneParams.value)
    .then((resp: any) => {
      sceneList.value = resp.data?.list || [];
      emits('sucess:load', sceneList.value.length)
      if (sceneList.value && sceneList.value.length > 0) {
        let targetSceneId 
        if (isShowAllSence) {
          const targetScene = sceneList.value.find((item: any) => item.sourceId == props.sourceId);
          targetSceneId = targetScene ? targetScene.id : sceneList.value[0].id;
        } else {
          if (sceneState.value) {
            if (findIndex(sceneList.value, { id: sceneState.value }) > -1) {
              targetSceneId = sceneState.value;
            } else {
              targetSceneId = sceneList.value[0].id;
            }
          } else {
            targetSceneId = sceneList.value[0].id;
          }
        }

        // 使用 nextTick 确保 DOM 更新后再设置选中状态
        nextTick(() => {
          handleSceneSelect(targetSceneId);
        });
      } else {
        selectedKeys.value = [];
        handleSceneSelect(null);
      }
    })
    .catch((error) => {
      selectedKeys.value = [];
      handleSceneSelect(null);
    });
}

onActivated(() => {
  loadData();
});

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
