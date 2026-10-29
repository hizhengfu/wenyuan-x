<template>
  <n-space vertical size="large">
    <n-card title="桥梁监测采集" size="small">
      <template #header-extra>
        <n-space>
          <n-button v-if="monitorDataAvailable" size="small" @click="router.push('/monitor/data/historyData')"
            >监测数据</n-button
          >
          <n-button v-if="monitorAlarmAvailable" size="small" @click="router.push('/monitor/data/alarmEvent')"
            >监测预警</n-button
          >
        </n-space>
      </template>
      <n-p depth="3">本页用于核对设备采集与图像。历史数据、统计分析及预警处置请前往监测管理。</n-p>
      <n-alert v-if="error" type="error" style="margin-bottom: 12px">{{ error }}</n-alert>
      <n-tabs v-model:value="tab" type="line" animated>
        <n-tab-pane name="data" tab="数据" display-directive="if">
          <n-space align="center" style="margin-bottom: 16px">
            <n-select
              v-model:value="deviceId"
              :options="options"
              placeholder="选择采集设备"
              :loading="loading"
              style="width: 280px"
            />
          </n-space>
          <IsapiData v-if="deviceId" :key="deviceId" :device-id="deviceId" />
          <n-empty v-else description="尚未配置采集设备，请在设置中新增" />
        </n-tab-pane>
        <n-tab-pane name="settings" tab="设置" display-directive="if">
          <n-space vertical size="large">
            <IsapiConfig :has-devices="listing.devices.length > 0" @changed="refresh" />
            <IsapiSettings :listing="listing" :loading="loading" @changed="refresh" @select="selectDevice" />
          </n-space>
        </n-tab-pane>
        <n-tab-pane name="status" tab="状态" display-directive="if">
          <n-space align="center" style="margin-bottom: 16px">
            <n-select
              v-model:value="deviceId"
              :options="options"
              placeholder="选择采集设备"
              :loading="loading"
              style="width: 280px"
            />
            <n-button :loading="loading" @click="refresh">刷新状态</n-button>
          </n-space>
          <IsapiStatus v-if="selectedDevice" :key="deviceId" :device="selectedDevice" :state="selectedState" />
          <n-empty v-else description="尚未配置采集设备，请在设置中新增" />
        </n-tab-pane>
      </n-tabs>
    </n-card>
  </n-space>
</template>

<script lang="ts" setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuthStore } from '@/store';
import { listDevices } from '../api/isapi';
import type { DeviceList } from '../api/isapi';
import IsapiData from '../components/isapi/IsapiData.vue';
import IsapiSettings from '../components/isapi/IsapiSettings.vue';
import IsapiConfig from '../components/isapi/IsapiConfig.vue';
import IsapiStatus from '../components/isapi/IsapiStatus.vue';

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
type Tab = 'data' | 'settings' | 'status';
function readTab(value: unknown): Tab {
  return value === 'settings' || value === 'status' ? value : 'data';
}
const tab = ref<Tab>(readTab(route.query.tab));
const deviceId = ref(typeof route.query.deviceId === 'string' ? route.query.deviceId : '');
const listing = ref<DeviceList>({ bindingAvailable: false, cameraBindingAvailable: false, devices: [], states: [] });
const loading = ref(false);
const error = ref('');
const options = computed(() => listing.value.devices.map((device) => ({ label: device.name, value: device.id! })));
const selectedDevice = computed(() => listing.value.devices.find((device) => device.id === deviceId.value));
const selectedState = computed(() => listing.value.states.find((state) => state.deviceId === deviceId.value));
const hasRoute = (path: string) => router.getRoutes().some((item) => item.path === path);
const monitorDataAvailable = computed(
  () => hasRoute('/monitor/data/historyData') && auth.hasPermission('HISTORY_DATA_ALL,HISTORY_DATA_SELECT'),
);
const monitorAlarmAvailable = computed(
  () => hasRoute('/monitor/data/alarmEvent') && auth.hasPermission('ALARM_EVENT_ALL,ALARM_EVENT_SELECT'),
);
let disposed = false;
let timer: ReturnType<typeof setInterval> | undefined;
async function refresh() {
  if (loading.value) return;
  loading.value = true;
  try {
    const result = await listDevices();
    if (disposed) return;
    listing.value = result;
    error.value = '';
    if (!result.devices.some((device) => device.id === deviceId.value)) deviceId.value = result.devices[0]?.id || '';
  } catch {
    if (!disposed) error.value = '设备列表读取失败，请检查服务及访问权限';
  } finally {
    loading.value = false;
  }
}
function selectDevice(id: string, value: 'data' | 'status') {
  deviceId.value = id;
  tab.value = value;
}
watch(
  () => route.query.tab,
  (value) => {
    tab.value = readTab(value);
  },
);
watch(
  () => route.query.deviceId,
  (value) => {
    if (typeof value === 'string') deviceId.value = value;
  },
);
watch([tab, deviceId], () => {
  if (route.path !== '/video/bridgeMonitoring') return;
  router.replace({
    path: route.path,
    query: { ...route.query, tab: tab.value, deviceId: deviceId.value || undefined },
  });
});
onMounted(() => {
  refresh();
  timer = setInterval(() => {
    if (!document.hidden) refresh();
  }, 5000);
});
onUnmounted(() => {
  disposed = true;
  if (timer) clearInterval(timer);
});
</script>
