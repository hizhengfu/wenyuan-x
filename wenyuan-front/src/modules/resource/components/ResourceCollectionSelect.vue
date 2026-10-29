<template>
  <n-select
    :value="modelValue || null"
    :options="options"
    :loading="loading"
    filterable
    remote
    clearable
    placeholder="按藏品名称或总登记号搜索"
    :disabled="disabled"
    @search="search"
    @update:value="select"
  />
</template>
<script setup lang="ts">
import { ref, watch, onBeforeUnmount } from 'vue';
import { collectionOptions } from '../api/business';
const props = defineProps<{ modelValue?: string; label?: string; disabled?: boolean }>();
const emit = defineEmits<{ 'update:modelValue': [string]; select: [any] }>();
const options = ref<any[]>([]);
const loading = ref(false);
let generation = 0;
async function search(keyword = '') {
  const own = ++generation;
  loading.value = true;
  try {
    const r: any = await collectionOptions(keyword);
    if (own !== generation) return;
    options.value = (r.data || []).map((v: any) => ({ label: `${v.number} · ${v.name}`, value: v.id, record: v }));
    if (props.modelValue && !options.value.some((v) => v.value === props.modelValue))
      options.value.unshift({ label: props.label || '已关联藏品', value: props.modelValue });
  } finally {
    if (own === generation) loading.value = false;
  }
}
function select(value: string | null) {
  emit('update:modelValue', value || '');
  emit('select', options.value.find((v) => v.value === value)?.record);
}
watch(
  () => props.modelValue,
  () => search(),
  { immediate: true },
);
onBeforeUnmount(() => generation++);
</script>
