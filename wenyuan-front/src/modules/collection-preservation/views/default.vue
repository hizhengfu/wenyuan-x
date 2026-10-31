<template>
  <div class="pv-dashboard">
    <NSpace justify="end" style="margin-bottom: 12px"
      ><NButton size="small" :loading="loading" @click="loadAll">刷新看板</NButton></NSpace
    >
    <NAlert v-if="error" type="error" :bordered="false" style="margin-bottom: 12px">{{ error }}</NAlert>
    <NSpin :show="loading">
      <NGrid :cols="4" :x-gap="12" :y-gap="12">
        <NGi><StatCard label="预警总数" :value="dashboard?.total ?? '--'" tone="info" /></NGi>
        <NGi><StatCard label="待处理" :value="dashboard?.pending ?? '--'" tone="warning" /></NGi>
        <NGi><StatCard label="藏品业务位置" :value="pointCount ?? '--'" tone="success" /></NGi>
        <NGi><StatCard label="位置接入网关" :value="gatewayCount ?? '--'" tone="default" /></NGi>
      </NGrid>

      <NGrid v-if="dashboard" :cols="2" :x-gap="12" :y-gap="12" style="margin-top: 12px">
        <NGi>
          <NCard title="按等级分布" size="small" :bordered="false">
            <NDataTable :columns="levelColumns" :data="levelRows" size="small" :bordered="false" />
          </NCard>
        </NGi>
        <NGi>
          <NCard title="按处置状态分布" size="small" :bordered="false">
            <NDataTable :columns="statusColumns" :data="statusRows" size="small" :bordered="false" />
          </NCard>
        </NGi>
      </NGrid>

      <NCard v-if="dashboard" title="最新待处置预警" size="small" :bordered="false" style="margin-top: 12px">
        <template #header-extra>
          <NButton size="tiny" quaternary @click="goAlarm">查看全部</NButton>
        </template>
        <NDataTable :columns="latestColumns" :data="latest" size="small" :bordered="false" :max-height="320" />
      </NCard>
      <NEmpty v-if="!dashboard && !loading && !error" description="尚未取得看板数据" />
    </NSpin>
  </div>
</template>

<script setup lang="ts">
import { computed, h, onMounted, onBeforeUnmount, onActivated, onDeactivated, ref } from 'vue';
import { NAlert, NSpin, NEmpty, NSpace, NButton, NCard, NDataTable, NGi, NGrid } from 'naive-ui';
import { useRouter } from 'vue-router';
import { formatDateTime } from '@/utils';
import { alarmApi } from '../api/alarm';
import AlarmLevelTag from '../components/AlarmLevelTag.vue';
import StatCard from '../components/StatCard.vue';
import type { AlarmDashboardVo, AlarmEventVo } from '../typings';

const router = useRouter();

const dashboard = ref<AlarmDashboardVo | null>(null);
const latest = ref<AlarmEventVo[]>([]);
const pointCount = ref<number | null>(null);
const gatewayCount = ref<number | null>(null);
const loading = ref(false),
  error = ref('');
let generation = 0,
  active = true,
  disposed = false;
function clearData() {
  dashboard.value = null;
  latest.value = [];
  pointCount.value = null;
  gatewayCount.value = null;
}
function validCount(value: unknown): value is number {
  return typeof value === 'number' && Number.isSafeInteger(value) && value >= 0;
}
function validDistribution(value: unknown): value is Record<string, number> {
  return !!value && typeof value === 'object' && !Array.isArray(value) && Object.values(value).every(validCount);
}

const LEVEL_LABEL: Record<string, string> = {
  RED: '一级 (红)',
  ORANGE: '二级 (橙)',
  YELLOW: '三级 (黄)',
  BLUE: '四级 (蓝)',
  NORMAL: '正常',
};
const STATUS_LABEL: Record<string, string> = {
  AWAIT: '待处理',
  PROCESSING: '处理中',
  PROCESSED: '已处置',
  CLOSED: '已关闭',
};

const levelRows = computed(() =>
  Object.entries(dashboard.value?.byLevel ?? {}).map(([k, v]) => ({ label: LEVEL_LABEL[k] ?? k, count: v, key: k })),
);
const statusRows = computed(() =>
  Object.entries(dashboard.value?.byStatus ?? {}).map(([k, v]) => ({ label: STATUS_LABEL[k] ?? k, count: v, key: k })),
);

const levelColumns = [
  { title: '等级', key: 'label', ellipsis: { tooltip: true }, resizable: true, width: 130, render: (r: any) => h(AlarmLevelTag, { level: r.key }) },
  { title: '数量', key: 'count', width: 120 },
];
const statusColumns = [
  { title: '状态', key: 'label', ellipsis: { tooltip: true }, resizable: true, width: 160 },
  { title: '数量', key: 'count', width: 120 },
];

const latestColumns = [
  {
    title: '时间',
    key: 'dateTime',
    width: 190, resizable: true,
    ellipsis: { tooltip: true },
    render: (row: AlarmEventVo) => formatDateTime(row.dateTime),
  },
  { title: '等级', key: 'level', width: 120, ellipsis: { tooltip: true }, resizable: true, render: (r: any) => h(AlarmLevelTag, { level: r.level }) },
  { title: '监测位置', key: 'sensorPointName', minWidth: 140, resizable: true, width: 180, ellipsis: { tooltip: true } },
  { title: '位置', key: 'warehouseName', minWidth: 140, resizable: true, width: 180, ellipsis: { tooltip: true } },
  { title: '指标', key: 'sensorName', width: 140, ellipsis: { tooltip: true }, resizable: true },
  { title: '数值', key: 'dataValue', width: 90 },
  { title: '受影响', key: 'affectedCount', width: 80, render: (r: any) => `${r.affectedCount ?? 0} 件` },
];

async function loadAll() {
  if (!active || disposed) return;
  const token = ++generation;
  clearData();
  error.value = '';
  loading.value = true;
  try {
    const response: any = await alarmApi.dashboard();
    if (!active || disposed || token !== generation) return;
    const data = response?.data?.data ?? response?.data ?? response;
    if (
      !data ||
      ![data.total, data.pending, data.pointCount, data.gatewayCount].every(validCount) ||
      !validDistribution(data.byLevel) ||
      !validDistribution(data.byStatus) ||
      !Array.isArray(data.latestPending) ||
      data.latestPending.some((row: AlarmEventVo) => !row || !row.id || !row.orgId || row.processStatus !== 'AWAIT')
    )
      throw new Error('看板数据不完整，请重新读取');
    dashboard.value = data;
    latest.value = data.latestPending;
    pointCount.value = data.pointCount;
    gatewayCount.value = data.gatewayCount;
  } catch (failure: any) {
    if (active && !disposed && token === generation) {
      clearData();
      error.value = failure?.message || '看板数据读取失败，请重试';
    }
  } finally {
    if (active && !disposed && token === generation) loading.value = false;
  }
}
function goAlarm() {
  if (active && !disposed) router.push('/preservation/alarm');
}
function leavePage() {
  active = false;
  ++generation;
  clearData();
  loading.value = false;
  error.value = '';
}
onMounted(loadAll);
onDeactivated(leavePage);
onActivated(() => {
  if (disposed || active) return;
  active = true;
  void loadAll();
});
onBeforeUnmount(() => {
  disposed = true;
  leavePage();
});
</script>

<style scoped>
.pv-dashboard {
  padding: 8px 4px;
}
</style>
