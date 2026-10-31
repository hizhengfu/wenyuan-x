<template>
  <div class="video-tree">
    <div class="video-tree-search">
      <n-input size="medium" v-model:value="pattern" placeholder="快速检索" />
    </div>
    <n-divider style="padding: 0; margin: 6px 0"></n-divider>
    <div class="video-tree-content">
      <n-tree
        block-line
        block-node
        :data="data"
        :on-load="handleLoadChild"
        :pattern="pattern"
        :show-irrelevant-nodes="false"
        key-field="id"
        label-field="name"
        :expandedKeys="expandedKeys"
        :node-props="nodeProps"
        :render-prefix="renderPrefix"
        :render-suffix="renderSuffix"
        @update:expandedKeys="updateExpandedKeys"
        selectable
      />
    </div>
  </div>
</template>
<script lang="ts" setup>
import { ref, h, watch } from 'vue';
import { transformToTreeFormat } from '@/utils';
import { tree, camera } from '../api/group';
import { list as cameraList } from '../api/camera';
import { TreeOption, NIcon } from 'naive-ui';
import { sumBy, isNumber } from 'lodash-es';
import VideoOn from '~icons/mdi/video-outline';
import VideoOff from '~icons/mdi/video-off-outline';

const props = defineProps({
  groupId: { type: String },
  sourceId: { type: String },
  relationSourceType: { type: Number },
});
const emits = defineEmits(['click:item', 'successLoad']);

const expandedKeys = ref();

const pattern = ref();

const data = ref();

function nodeProps({ option }: { option: TreeOption }) {
  return {
    onClick() {
      emits('click:item', option);
    },
  };
}

// 递归计算树形结构的 count 值
function calculateCount(node: any, field: string = 'count'): number {
  if (isNumber(node[field])) {
    return node[field] + sumBy(node.children, (child) => calculateCount(child, field));
  } else {
    return 0;
  }
}

function renderPrefix({ option }: { option: TreeOption }) {
  if (option.code) {
    return h(
      NIcon,
      {
        color: option.online === false ? 'red' : 'green',
      },
      {
        default: () => h(option.online === false ? VideoOff : VideoOn),
      },
    );
  } else {
    return '';
  }
}

function renderSuffix({ option }: { option: TreeOption }) {
  return h(
    'div',
    {
      text: true,
      type: 'primary',
    },
    {
      default: () => {
        let allCount = calculateCount(option);
        if (allCount > 0) {
          return `${calculateCount(option)}/${calculateCount(option, 'onlineCount')}`;
        } else {
          return '';
        }
      },
    },
  );
}

function updateExpandedKeys(items: any[]) {
  expandedKeys.value = items;
}

/**
 * 加载下级节点
 * */
function handleLoadChild(node: TreeOption) {
  return new Promise<void>((resolve) => {
    camera(node.id as string).then((resp: any) => {
      node.children = resp.data || [];

      resolve();
    });
  });
}

/**
 * 加载机构树
 * */
async function loadData() {
  data.value = [];
  try {
    if (props.sourceId && props.relationSourceType != null) {
      const resp = await cameraList({
        relationSourceType: props.relationSourceType,
        relationSourceId: props.sourceId,
        status: 0,
        show: true,
        pageSize: -1,
      });
      data.value = resp?.data?.list || [];
      expandedKeys.value = [];
      emits('successLoad', data.value);
      return;
    }

    const resp = await tree({ id: props.groupId, sourceId: props.sourceId });
    if (resp.data) {
      data.value = transformToTreeFormat(resp.data, { pIdKey: 'parentId' });
      if (data.value && data.value.length > 0) {
        expandedKeys.value = [data.value[0].id];
      }
    }
  } catch (e: any) {}
  emits('successLoad', data.value);
}

watch(
  () => [props.groupId, props.sourceId, props.relationSourceType],
  () => void loadData(),
  { immediate: true },
);
</script>
<style lang="scss" scoped>
.video-tree {
  display: flex;
  flex-direction: column;
  position: absolute;
  top: 0;
  bottom: 0;
  left: 0;
  right: 0;

  .video-tree-search {
    padding: 15px 15px 0 15px;
  }

  .video-tree-content {
    flex: 1;
    overflow: auto;
    white-space: nowrap;

    :deep(.n-tree-node-content__text) {
      white-space: nowrap;
    }
  }
}
</style>
