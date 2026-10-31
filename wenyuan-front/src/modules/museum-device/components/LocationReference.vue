<template>
  <template v-if="locationId">
    <NButton text type="primary" @click="visible = true">{{ name || '查看关联位置' }}</NButton>
    <HPageForm
      v-model:show="visible"
      title="关联位置资料（当前）"
      content-only
      cancel-text="关闭"
      :footer-style="{ paddingRight: '36px' }"
    >
      <NAlert v-if="error" type="error">{{ error }} <NButton text type="primary" @click="load">重试</NButton></NAlert>
      <NSpin :show="loading">
        <NDescriptions v-if="detail" :column="2" bordered label-placement="left">
          <NDescriptionsItem label="位置名称">{{ detail.name }}</NDescriptionsItem>
          <NDescriptionsItem label="位置编号">{{ detail.code }}</NDescriptionsItem>
          <NDescriptionsItem label="类型"
            ><HDictText type="museum_location_type" :value="detail.type"
          /></NDescriptionsItem>
          <NDescriptionsItem label="当前路径" :span="2">{{ detail.path || detail.name }}</NDescriptionsItem>
        </NDescriptions>
      </NSpin>
    </HPageForm>
  </template>
  <span v-else>{{ name || '--' }}</span>
</template>
<script setup lang="ts">
import { onBeforeUnmount, onDeactivated, ref, watch } from 'vue';
import { ingestApi } from '../api/ingest';
const props = defineProps<{ locationId?: string | null; name?: string | null }>();
interface Location {
  id: string;
  name: string;
  code: string;
  path?: string | null;
  type: string;
}
const visible = ref(false),
  detail = ref<Location | null>(null),
  loading = ref(false),
  error = ref('');
let generation = 0;
async function load() {
  if (!visible.value || !props.locationId) return;
  const token = ++generation,
    id = props.locationId;
  detail.value = null;
  error.value = '';
  loading.value = true;
  try {
    const response: any = await ingestApi.locationReference(id);
    if (token !== generation) return;
    const value = response?.data?.data ?? response?.data ?? response;
    if (value?.id !== id) throw new Error('位置资料与关联位置不一致，请重新读取');
    detail.value = value;
  } catch (cause: any) {
    if (token === generation) error.value = cause?.message || '关联位置读取失败';
  } finally {
    if (token === generation) loading.value = false;
  }
}
watch(
  () => props.locationId,
  () => {
    visible.value = false;
    ++generation;
    detail.value = null;
  },
);
watch(visible, (show) => {
  ++generation;
  detail.value = null;
  error.value = '';
  loading.value = false;
  if (show && props.locationId) void load();
});
onDeactivated(() => {
  visible.value = false;
  ++generation;
  detail.value = null;
  loading.value = false;
  error.value = '';
});
onBeforeUnmount(() => {
  ++generation;
});
</script>
