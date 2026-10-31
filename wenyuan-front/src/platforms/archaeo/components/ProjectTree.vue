<template>
  <n-tree
    block-node
    accordion
    showLine
    :pattern="pattern"
    :show-irrelevant-nodes="false"
    :data="treeOptions"
    :on-load="handleLoad"
    :node-props="nodeProps"
    key-field="id"
    label-field="name"
  />
</template>
<script lang="ts" setup>
import { ref } from 'vue';
import { tree } from '../api/project.ts';
import { tree as plotTree } from '../api/projectPlot.ts';
import { tree as cellTree } from '../api/projectCell.ts';
import { TreeOption } from 'naive-ui';

const props = defineProps({
  value: { type: [String, Number] },
  pattern: { type: String },
  multiple: { type: Boolean, default: false },
});
const treeOptions = ref<any>([]);

const emits = defineEmits(['itemClick']);

function nodeProps({ option }: { option: TreeOption }) {
  return {
    onClick() {
      emits('itemClick', option);
    },
  };
}

/**
 * 获取项目数据
 * */
function fetchProjectData() {
  tree({}).then((resp: any) => {
    if (resp && resp.data) {
      treeOptions.value = resp.data || [];
    }
  });
}

/**
 * 点击家在事件
 * */
function handleLoad(node: TreeOption) {
  if (node.type === 'PROJECT') {
    //@ts-ignore
    if (node.plotCount && node.plotCount > 0) {
      return plotTree({ projectId: node.id }).then((resp: any) => {
        node.children = resp.data || [];
      });
      //@ts-ignore
    } else if (node.cellCount && node.cellCount > 0) {
      return cellTree({ projectId: node.projectId }).then((resp: any) => {
        node.children = resp.data || [];
      });
    }
  } else if (node.type === 'PLOT') {
    return cellTree({ plotId: node.id, projectId: node.projectId }).then((resp: any) => {
      node.children = resp.data || [];
    });
  } else {
    return cellTree({ projectId: node.projectId }).then((resp: any) => {
      node.children = resp.data || [];
    });
  }
  return new Promise((resolve) => resolve(null));
}

fetchProjectData();
</script>
