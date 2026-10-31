<template>
  <n-select
    :value="modelValue"
    :options="options"
    multiple
    filterable
    remote
    :loading="loading"
    :disabled="disabled"
    placeholder="搜索并关联已入库的资源"
    @search="search"
    @update:value="$emit('update:modelValue', $event)"
  />
</template>
<script setup lang="ts">
import { ref, watch, onBeforeUnmount } from 'vue';
import { list, get } from '../api/resource';
const props = defineProps<{ modelValue: string[]; disabled?: boolean }>();
defineEmits<{ 'update:modelValue': [string[]] }>();
const options = ref<any[]>([]);
const loading = ref(false);
const labels = new Map<string, string>();
let generation = 0;
async function search(keyword = '') {
  const own = ++generation;
  loading.value = true;
  try {
    const r: any = await list({ keyword, pageNum: 1, pageSize: 50 });
    if (own !== generation) return;
    for (const v of r.data?.list || []) labels.set(v.id, v.name);
    options.value = [...new Set([...props.modelValue, ...(r.data?.list || []).map((v: any) => v.id)])].map((value) => ({
      value,
      label: labels.get(value) || '关联资源（详情未读取）',
    }));
  } finally {
    if (own === generation) loading.value = false;
  }
}
watch(
  () => props.modelValue,
  async (ids) => {
    await Promise.allSettled(
      ids
        .filter((id) => !labels.has(id))
        .map(async (id) => {
          const r: any = await get(id);
          labels.set(id, r.data?.name || '关联资源');
        }),
    );
    await search();
  },
  { immediate: true },
);
onBeforeUnmount(() => generation++);
</script>
