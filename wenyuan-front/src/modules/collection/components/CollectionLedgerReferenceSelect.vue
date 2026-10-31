<template>
  <div>
    <NSelect
      :value="value"
      :options="options"
      :loading="loading"
      filterable
      clearable
      placeholder="选择本馆藏品或原馆留存账"
      @update:value="(value: string | null) => emit('update:value', value)"
    />
    <NAlert v-if="error" type="error">{{ error }}</NAlert>
  </div>
</template>
<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue';
import { archivePacketsApi } from '../api/archivePackets';
import { orgApi } from '../api/org';
const props = defineProps<{ value?: string; orgId: string; purpose?: 'archive' | 'finance' }>();
const emit = defineEmits<{ (event: 'update:value', value: string | null): void }>();
const options = ref<{ value: string; label: string }[]>([]);
const loading = ref(false);
const error = ref('');
let request = 0;
watch(() => [props.orgId, props.purpose] as const, async ([orgId]) => {
  const token = ++request;
  options.value = [];
  error.value = '';
  loading.value = false;
  if (!orgId) return;
  loading.value = true;
  try {
    const response: any = await (props.purpose === 'finance' ? orgApi.reconciliationCollections(orgId) : archivePacketsApi.collections(orgId));
    if (token !== request) return;
    options.value = (response?.data || []).map((row: { id: string; totalRegNo: string; name: string; source: string }) => ({
      value: row.id,
      label: `${row.totalRegNo} ${row.name}${row.source === 'RETAINED_SOURCE_MUSEUM' ? '（原馆留存账）' : ''}`,
    }));
  } catch {
    if (token === request) error.value = '账目藏品列表加载失败，请重新打开窗口后重试';
  } finally {
    if (token === request) loading.value = false;
  }
}, { immediate: true });
onBeforeUnmount(() => { ++request; });
</script>
