<template>
  <NTreeSelect
    :value="modelValue"
    :options="treeOptions"
    clearable
    filterable
    :placeholder="placeholder"
    @update:value="(value) => emit('update:modelValue', value == null ? null : String(value))"
  />
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { NTreeSelect, type TreeSelectOption } from 'naive-ui';
import type { RecordFilterLocationVo } from '../../museum-device/typings';

const props = withDefaults(defineProps<{
  modelValue?: string | null;
  options: RecordFilterLocationVo[];
  placeholder?: string;
}>(), { modelValue: null, placeholder: '全部建筑/空间' });
const emit = defineEmits<{ 'update:modelValue': [value: string | null] }>();

const treeOptions = computed<TreeSelectOption[]>(() => {
  const nodes = new Map<string, TreeSelectOption & { children?: TreeSelectOption[] }>();
  props.options.forEach((item) => nodes.set(item.id, {
    key: item.id,
    label: item.name || item.path || item.code,
  }));
  const roots: TreeSelectOption[] = [];
  props.options.forEach((item) => {
    const node = nodes.get(item.id)!;
    const parent = item.parentId ? nodes.get(item.parentId) : undefined;
    if (parent) {
      (parent.children ||= []).push(node);
    } else {
      roots.push(node);
    }
  });
  return roots;
});
</script>
