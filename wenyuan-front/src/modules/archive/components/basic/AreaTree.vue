<template>
  <n-tree
    block-line
    block-node
    v-bind="$attrs"
    :data="menus"
    :label-field="labelField"
    key-field="id"
    :node-props="nodeProps"
    selectable
    :render-suffix="renderSuffix"
    :show-irrelevant-nodes="false"
    :accordion="accordion"
    class="custom-tree-class"
    :default-selected-keys="defaultKey"
    :selected-keys="selectedKeys"
    @update:selected-keys="handleSelectedKeysUpdate"
  />
</template>

<script lang="ts" setup>
import { list as treeList } from '../../api/basic/areatree.ts';
import { computed, ref, useAttrs, watchEffect, h, watch, triggerRef } from 'vue';
import { NButton, TreeOption, TreeOverrideNodeClickBehavior } from 'naive-ui';
import { transformToTreeFormat } from '../../../../utils';

const props = defineProps({
  params: {
    type: Object,
    default() {
      return {};
    },
  },
  refresh: {
    type: Boolean,
    default: false
  },
  list: {
    type: Function,
    default: treeList,
  },
  labelField: {
    type: String,
    default: 'id',
  },
  accordion: {
    type: Boolean,
    default: true,
  },
});

const accordion = computed(() => props.accordion);
const $attrs = useAttrs();
const menus = ref();
const emits = defineEmits(['click:item', 'firstItem']);
const flage = ref()
const selectedKeys = ref<string[]>([]);

watch(
  () => props.refresh,
  (newValue, oldValue) => {
    if (newValue == true) {
      flage.value = 'refresh'
      fetchList()
    }
  }
)

// 处理选中状态变化
function handleSelectedKeysUpdate(keys: string[]) {
  selectedKeys.value = keys;
  
  if (keys.length > 0) {
    const selectedNode = findNodeById(menus.value, keys[0]);
    if (selectedNode) {
      emits('click:item', selectedNode);
    }
  } else {
    emits('click:item', null); 
  }
}

function findNodeById(nodes: any[], id: string): any {
  if (!nodes) return null;
  
  for (const node of nodes) {
    if (node.id === id) {
      return node;
    }
    if (node.children) {
      const found = findNodeById(node.children, id);
      if (found) return found;
    }
  }
  return null;
}

function nodeProps({ option }: { option: TreeOption }) {
  return {
  };
}

function renderSuffix({ option }: { option: TreeOption }) {
  return h('div', { text: true, type: 'primary' }, { default: () => `(${option.count ?? option.filesCount ?? option.remarks ?? ''})` });
}

const defaultKey = ref([] as any);

function fetchList() {
  const { list, labelField } = props;
  list(props.params).then((resp: any) => {
    if (resp) {
      if (resp.data) {
        let data;
        if (resp.data.list) {
          data = transformToTreeFormat(resp.data.list, { pIdKey: 'parentId' });
        } else {
          data = transformToTreeFormat(resp.data, { pIdKey: 'parentId' });
        }
        setLeafProperty(data);
        menus.value = data;
        triggerRef(menus);
      } else {
        menus.value = resp || [];
      }
      if (flage.value != 'refresh') {
        emits('firstItem', menus.value[0]);
      }

      if (menus.value) {
        const key = findFirstLeaf(menus.value)?.id || '';
        defaultKey.value = [key];
        selectedKeys.value = [key];
      }
    }
  });
}

function setLeafProperty(items: any) {
  items.forEach((item: any) => {
    if (item.children && item.children.length > 0) {
      item.isLeaf = false;
      setLeafProperty(item.children);
    } else {
      item.isLeaf = true;
    }
  });
}

interface TreeNode<T> {
  id: string;
  children?: TreeNode<T>[];
}

function findFirstLeaf<T>(nodes: TreeNode<T>[]): TreeNode<T> | undefined {
  for (const node of nodes) {
    if (!node.children || node.children.length === 0) {
      return node;
    }
    const leafNode = findFirstLeaf(node.children);
    if (leafNode) {
      return leafNode;
    }
  }

  return undefined;
}

watchEffect(() => {
  if (props.params) {
    fetchList();
  }
});

defineExpose({
  fetchList,
});
</script>