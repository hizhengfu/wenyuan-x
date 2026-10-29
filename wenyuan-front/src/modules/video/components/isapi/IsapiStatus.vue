<template>
  <n-space vertical size="large">
    <n-card title="采集状态" size="small">
      <n-descriptions label-placement="left" bordered :column="2">
        <n-descriptions-item label="注册状态">
          <n-tag :type="state?.state === 'RECEIVING' ? 'success' : state?.error ? 'warning' : 'default'" size="small">
            {{ stateLabel[state?.state || 'WAIT_REGISTER'] || state?.state }}
          </n-tag>
        </n-descriptions-item>
        <n-descriptions-item label="设备注册 ID">{{ device.registrationId }}</n-descriptions-item>
        <n-descriptions-item label="最近注册">{{ formatTime(state?.registeredAt) }}</n-descriptions-item>
        <n-descriptions-item label="最近接入地址">{{ state?.remoteAddress || '—' }}</n-descriptions-item>
        <n-descriptions-item label="最近收包">{{ formatTime(state?.lastPacketAt) }}</n-descriptions-item>
        <n-descriptions-item label="最近样本">{{ formatTime(state?.lastSampleAt) }}</n-descriptions-item>
        <n-descriptions-item label="最近图像">{{ formatTime(state?.lastImageAt) }}</n-descriptions-item>
        <n-descriptions-item label="监测通道">{{ device.channelId }}</n-descriptions-item>
        <n-descriptions-item label="关联标靶">{{ device.bindings.length }} 个</n-descriptions-item>
        <n-descriptions-item label="图像归档目标" :span="2">
          {{
            device.cameraBinding
              ? `相机 ${device.cameraBinding.cameraId}（站点 ${device.cameraBinding.stationId}）`
              : '未关联图像监测指标，仅在本页查看'
          }}
        </n-descriptions-item>
      </n-descriptions>
      <n-alert v-if="state?.error" type="warning" style="margin-top: 12px">{{ state.error }}</n-alert>
      <n-alert v-if="state?.imageError" type="warning" style="margin-top: 12px">{{ state.imageError }}</n-alert>
    </n-card>
    <n-card title="最近设备异常事件" size="small">
      <n-p depth="3">标靶恢复表示设备重新识别到目标。预警确认与处置请在监测管理中完成。</n-p>
      <n-alert v-if="error" type="warning" style="margin-bottom: 12px">{{ error }}</n-alert>
      <n-data-table
        :columns="columns"
        :data="events"
        :row-key="(row: DeviceEvent) => row.id"
        :scroll-x="720"
        :pagination="{ pageSize: 10 }"
      />
    </n-card>
  </n-space>
</template>

<script lang="ts" setup>
import { h, onMounted, onUnmounted, ref, watch } from 'vue';
import { NTag } from 'naive-ui';
import type { DataTableColumns } from 'naive-ui';
import { deviceEvents, formatTime, stateLabel } from '../../api/isapi';
import type { DeviceEvent, IsapiDevice, RuntimeState } from '../../api/isapi';

const props = defineProps<{ device: IsapiDevice; state?: RuntimeState }>();
const events = ref<DeviceEvent[]>([]);
const error = ref('');
const columns: DataTableColumns<DeviceEvent> = [
  { title: '事件时间', key: 'occurredAt', width: 200, render: (row) => formatTime(row.occurredAt) },
  { title: '接收时间', key: 'receivedAt', width: 200, render: (row) => formatTime(row.receivedAt) },
  {
    title: '设备事件',
    key: 'eventState',
    width: 140,
    render: (row) =>
      h(NTag, { size: 'small', type: row.eventState === 'active' ? 'warning' : 'success' }, () =>
        row.eventState === 'active' ? '标靶丢失' : '标靶恢复',
      ),
  },
  { title: '标靶', key: 'targetIds', width: 180, render: (row) => row.targetIds.join('、') || '设备未提供' },
];
let disposed = false;
let revision = 0;
let loading = false;
let timer: ReturnType<typeof setInterval> | undefined;
async function refresh() {
  if (loading || !props.device.id) return;
  loading = true;
  const requestId = ++revision;
  try {
    const result = await deviceEvents(props.device.id);
    if (!disposed && requestId === revision) {
      events.value = result;
      error.value = '';
    }
  } catch {
    if (requestId === revision) error.value = '设备异常记录读取失败';
  } finally {
    if (requestId === revision) loading = false;
  }
}
watch(
  () => props.device.id,
  () => {
    revision++;
    loading = false;
    events.value = [];
    error.value = '';
    refresh();
  },
  { immediate: true },
);
onMounted(() => {
  timer = setInterval(() => {
    if (!document.hidden) refresh();
  }, 5000);
});
onUnmounted(() => {
  disposed = true;
  revision++;
  if (timer) clearInterval(timer);
});
</script>
