<template>
  <template v-if="deviceId">
    <NButton text type="primary" @click="visible = true">{{ name || '查看关联设备' }}</NButton>
    <HPageForm
      v-model:show="visible"
      title="关联设备资料"
      content-only
      cancel-text="关闭"
      :footer-style="{ paddingRight: '36px' }"
    >
      <NAlert v-if="error" type="error">{{ error }} <NButton text type="primary" @click="load">重试</NButton></NAlert>
      <NSpin :show="loading">
        <NDescriptions v-if="detail" :column="2" bordered label-placement="left">
          <NDescriptionsItem label="设备名称">{{ detail.name }}</NDescriptionsItem>
          <NDescriptionsItem label="设备编号">{{ detail.code }}</NDescriptionsItem>
          <NDescriptionsItem label="型号编码">{{ detail.modelCode }}</NDescriptionsItem>
          <NDescriptionsItem label="外部设备标识">{{ detail.externalDeviceId }}</NDescriptionsItem>
          <NDescriptionsItem label="当前安装位置" :span="2">{{ detail.locationPath || '--' }}</NDescriptionsItem>
          <NDescriptionsItem label="最后接收时间" :span="2">{{
            detail.lastSeenAt ? formatDateTime(detail.lastSeenAt) : '--'
          }}</NDescriptionsItem>
        </NDescriptions>
      </NSpin>
    </HPageForm>
  </template>
  <span v-else>{{ name || '--' }}</span>
</template>
<script setup lang="ts">
import { onBeforeUnmount, onDeactivated, ref, watch } from 'vue';
import { ingestApi } from '../api/ingest';
import { formatDateTime } from '@/utils';
import type { AlarmDeviceReferenceVo } from '../typings';
const props = defineProps<{ deviceId?: string | null; name?: string | null }>();
const visible = ref(false),
  detail = ref<AlarmDeviceReferenceVo | null>(null),
  loading = ref(false),
  error = ref('');
let generation = 0;
async function load() {
  if (!visible.value || !props.deviceId) return;
  const token = ++generation,
    id = props.deviceId;
  detail.value = null;
  error.value = '';
  loading.value = true;
  try {
    const response: any = await ingestApi.deviceReference(id);
    if (token !== generation) return;
    const value = response?.data?.data ?? response?.data ?? response;
    if (value?.id !== id) throw new Error('设备资料与关联设备不一致，请重新读取');
    detail.value = value;
  } catch (cause: any) {
    if (token === generation) error.value = cause?.message || '关联设备读取失败';
  } finally {
    if (token === generation) loading.value = false;
  }
}
watch(
  () => props.deviceId,
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
  if (show && props.deviceId) void load();
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
