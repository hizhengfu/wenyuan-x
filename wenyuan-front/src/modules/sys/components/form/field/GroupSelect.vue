<template>
  <n-tree-select
    v-bind="$attrs"
    :options="options"
    label-field="name"
    key-field="id"
    filterable
    clearable
    :default-expanded-keys="defaultExpandedKeys"
    :expanded-keys="expandedKeys"
    :watch-props="['defaultExpandedKeys']"
    :override-default-node-click-behavior="overrideDefaultNodeClickBehavior"
    @update:expanded-keys="handleUpdateExpandedKeys"
  />
</template>
<script lang="ts" setup>
import { computed, onBeforeMount, ref } from 'vue';
import { tree } from '../../../api/group';
import { transformToTreeFormat } from '@/utils';
import { isGroupSelectFolderNode, normalizeGroupSelectOptions } from './groupSelect.shared.js';

const props = withDefaults(defineProps<{
  singleRootLeafOnly?: boolean;
  singleRootDefaultExpand?: boolean;
}>(), {
  singleRootLeafOnly: false,
  singleRootDefaultExpand: false,
});

const options = ref<any[]>([]);
const defaultExpandedKeys = ref<Array<string | number>>([]);
const expandedKeys = ref<Array<string | number> | undefined>(undefined);
const singleRootLeafOnlyActive = ref(false);
const singleRootDefaultExpandActive = ref(false);

const overrideDefaultNodeClickBehavior = computed(() => {
  if (!singleRootLeafOnlyActive.value) return undefined;
  return ({ option }: { option: any }) => (isGroupSelectFolderNode(option) ? 'toggleExpand' : 'default');
});

function handleUpdateExpandedKeys(value: Array<string | number>) {
  if (!singleRootDefaultExpandActive.value) return;
  expandedKeys.value = value;
}

onBeforeMount(() => {
  tree({}).then((res) => {
    const treeOptions = transformToTreeFormat(res || [], { pIdKey: 'parentId' });
    const normalized = normalizeGroupSelectOptions(treeOptions, {
      singleRootLeafOnly: props.singleRootLeafOnly,
      singleRootDefaultExpand: props.singleRootDefaultExpand,
    });
    options.value = normalized.options;
    defaultExpandedKeys.value = normalized.defaultExpandedKeys;
    expandedKeys.value = normalized.expandedKeys;
    singleRootLeafOnlyActive.value = normalized.singleRootLeafOnlyActive;
    singleRootDefaultExpandActive.value = normalized.expandedKeys !== undefined;
  });
});
</script>
