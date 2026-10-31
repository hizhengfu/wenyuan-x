<template>
  <NModal
    v-model:show="visible"
    preset="card"
    :title="device?.name || '设备数据'"
    class="device-data-modal"
    style="width: 80%"
    content-style="max-height: min(82vh, 860px); overflow: auto;"
  >
    <template v-if="device">
      <NDescriptions bordered label-placement="left" :column="2" size="small">
        <NDescriptionsItem label="设备编号">{{ device.code }}</NDescriptionsItem>
        <NDescriptionsItem label="设备型号">{{ device.modelCode }}</NDescriptionsItem>
        <NDescriptionsItem label="安装位置">{{ device.locationPath || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="最后上报">{{ formatDeviceTime(device.lastSeenAt) }}</NDescriptionsItem>
      </NDescriptions>
      <NAlert v-if="device.activeAlarm" type="error" class="device-data-modal__alarm">
        {{ device.activeAlarm.title }}（{{ device.activeAlarm.code }}）
      </NAlert>
      <DeviceMetricHistory :device-id="device.id" :metrics="device.latestMetrics || []" />
    </template>
  </NModal>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { NAlert, NDescriptions, NDescriptionsItem, NModal } from 'naive-ui';
import { formatDateTime } from '@/utils';
import type { MuseumSpatialDeviceVo } from '../typings';
import DeviceMetricHistory from './DeviceMetricHistory.vue';

const props = defineProps<{
  show: boolean;
  device?: MuseumSpatialDeviceVo | null;
}>();

const emit = defineEmits<{
  (event: 'update:show', show: boolean): void;
}>();

const visible = computed({
  get: () => props.show,
  set: (show: boolean) => emit('update:show', show),
});

function formatDeviceTime(value?: number | null) {
  return value ? formatDateTime(value) : '--';
}
</script>

<style scoped>
.device-data-modal {
  width: min(1080px, calc(100vw - 32px));
}
.device-data-modal__alarm {
  margin-top: 12px;
}
</style>
