<template>
  <NPopover trigger="click" placement="bottom-end">
    <template #trigger>
      <NButton secondary size="small" :type="statusTone">{{ statusText }}</NButton>
    </template>
    <NSpace vertical>
      <NText>操作员：{{ previewOnly ? '示例操作员' : snapshot?.operatorName || '待确认' }}</NText>
      <NText v-if="previewOnly">只读预览未连接设备。</NText>
      <NText v-else-if="failed" type="warning">设备状态读取失败，请重试。</NText>
      <NText v-else-if="!selected.length">当前作业没有可确认状态的绑定设备。</NText>
      <NCard
        v-for="item in selected"
        :key="item.binding.deviceId"
        size="small"
        :title="item.device?.deviceName || '设备信息不可用'"
      >
        <NSpace vertical>
          <HDictText type="col_rfid_workstation_device_purpose" :value="item.binding.purpose" />
          <NText v-if="!item.device">设备信息不可用</NText>
          <NText v-else-if="!item.device.enabled" type="warning">已停用</NText>
          <NText v-else-if="!item.device.connectionManaged">由终端确认连接状态</NText>
          <HDictText v-else type="museum_rfid_connection_state" :value="item.device.connectionState" />
          <NText v-if="item.device?.lastSeenAt != null" depth="3">
            最近通信：<NTime :time="item.device.lastSeenAt" format="yyyy-MM-dd HH:mm:ss" />
          </NText>
        </NSpace>
      </NCard>
      <NText v-if="snapshot" depth="3">
        更新于 <NTime :time="snapshot.checkedAt" format="HH:mm:ss" />，页面可见时每 15 秒更新
      </NText>
      <NButton v-if="!previewOnly" :loading="loading" :disabled="loading" @click="refresh">刷新设备状态</NButton>
    </NSpace>
  </NPopover>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { NButton, NCard, NPopover, NSpace, NText, NTime } from 'naive-ui';
import { getDictLabel, useDict } from '@/utils/dict';
import { useRfidWorkstationRuntime } from '../../composables/useRfidWorkstationRuntime';
import type { RfidWorkstationDevicePurpose } from '../../typings/rfid';

const props = defineProps<{
  workstationId: string;
  deviceId?: string;
  purpose: RfidWorkstationDevicePurpose;
  previewOnly: boolean;
}>();
const { snapshot, loading, failed, refresh } = useRfidWorkstationRuntime(
  () => props.workstationId,
  () => props.previewOnly,
);
const { options: connectionStates } = useDict('museum_rfid_connection_state');
const selected = computed(() => {
  const peripherals = snapshot.value?.workstationId === props.workstationId ? snapshot.value.peripherals : [];
  // 进行中的作业始终按实际 deviceId 展示，不回退到另一台默认设备。
  if (props.deviceId) {
    const matches = peripherals.filter((item) => item.binding.deviceId === props.deviceId);
    return matches.slice(0, 1);
  }
  return peripherals.filter((item) => item.binding.purpose === props.purpose);
});
const managed = computed(() => selected.value.filter((item) => item.device?.enabled && item.device.connectionManaged));
const online = computed(() => managed.value.filter((item) => item.device?.connectionState === 'ONLINE').length);
const statusText = computed(() => {
  if (props.previewOnly) return '设备：只读预览';
  if (failed.value) return '设备状态：读取失败';
  if (!snapshot.value) return loading.value ? '设备状态：查询中' : '设备状态：待确认';
  if (!selected.value.length) return props.deviceId ? '当前设备：信息不可用' : '外设：未绑定';
  if (props.deviceId && selected.value[0].device) {
    const device = selected.value[0].device;
    if (!device.enabled) return '当前设备：已停用';
    if (!device.connectionManaged) return '当前设备：由终端确认';
    return '当前设备：' + getDictLabel(connectionStates.value, device.connectionState || '');
  }
  if (!managed.value.length) return '外设：状态待确认';
  return '设备在线：' + online.value + ' / ' + selected.value.length;
});
const statusTone = computed(() => {
  if (props.previewOnly) return 'default';
  if (failed.value) return 'warning';
  if (!snapshot.value) return 'default';
  return selected.value.length > 0 && online.value === selected.value.length ? 'success' : 'warning';
});
</script>
