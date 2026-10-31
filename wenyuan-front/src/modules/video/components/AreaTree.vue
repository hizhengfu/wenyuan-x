<template>
  <n-tree
    block-line
    block-node
    :data="menus"
    :label-field="labelField"
    key-field="id"
    :node-props="nodeProps"
    multiple
    :show-irrelevant-nodes="false"
    v-bind="$attrs"
    class="custom-tree-class"
    :selected-keys="selectedKeys"
    @update:selected-keys="handleSelectedKeysUpdate"
  />
</template>

<script lang="ts" setup>
import { list as treeList } from '@/modules/archive/api/basic/areatree.ts';
import { computed, PropType, ref, useAttrs, watchEffect, watch, triggerRef } from 'vue';
import { TreeOption } from 'naive-ui';
import { transformToTreeFormat } from '@/utils';

const props = defineProps({
  value: {
    type: Array as PropType<string[]>,
    default() {
      return [];
    },
  },
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
});

const $attrs = useAttrs();
const menus = ref();
const emits = defineEmits(['click:item', 'firstItem', 'update:value']);
const flage = ref();
const selectedKeys = ref<string[]>([]);
const nodeIdSet = computed(() => new Set(flattenNodeIds(menus.value || [])));

watch(
  () => props.refresh,
  (newValue) => {
    if (newValue == true) {
      flage.value = 'refresh';
      fetchList();
    }
  },
);

watch(
  () => props.value,
  (newValue) => {
    selectedKeys.value = Array.isArray(newValue) ? newValue.map((key) => String(key)) : [];
  },
  { immediate: true, deep: true },
);

function handleSelectedKeysUpdate(keys: Array<string | number>) {
  const normalizedKeys = keys.map((key) => String(key));
  selectedKeys.value = normalizedKeys;
  const checkedNodes = findNodesByIds(menus.value || [], normalizedKeys);
  emits('update:value', [...normalizedKeys]);
  emits('click:item', checkedNodes);
}

function findNodeById(nodes: any[], id: string): any {
  if (!nodes) return null;
  
  for (const node of nodes) {
    if (String(node.id) === String(id)) {
      return node;
    }
    if (node.children) {
      const found = findNodeById(node.children, id);
      if (found) return found;
    }
  }
  return null;
}

function findNodesByIds(nodes: any[], ids: string[]) {
  return ids
    .map((id) => findNodeById(nodes, id))
    .filter((node) => Boolean(node));
}

function flattenNodeIds(nodes: any[]): string[] {
  return nodes.flatMap((node) => {
    const currentId = node?.id ? [String(node.id)] : [];
    return currentId.concat(node.children ? flattenNodeIds(node.children) : []);
  });
}

function nodeProps({ option: _option }: { option: TreeOption }) {
  return {
  };
}

function fetchList() {
  const { list } = props;
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
      syncSelectedKeys();
    }
  });
}

function syncSelectedKeys() {
  const validKeys = selectedKeys.value.filter((key) => nodeIdSet.value.has(String(key)));
  if (validKeys.length !== selectedKeys.value.length) {
    selectedKeys.value = validKeys;
    emits('update:value', [...validKeys]);
    emits('click:item', findNodesByIds(menus.value || [], validKeys));
  }
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

watchEffect(() => {
  if (props.params) {
    fetchList();
  }
});

defineExpose({
  fetchList,
});
</script>
