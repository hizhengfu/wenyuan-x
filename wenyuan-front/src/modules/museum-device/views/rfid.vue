<template>
  <div class="rfid-device-page">
    <NAlert type="info" :bordered="false" class="tip">
      RFID 型号、业务角色、硬件序列号、天线数和本机服务参数统一在“设备实例”维护。此处查看设备档案、实时连接和命令审计；
      旧 C5 TCP 由中心采集端接入，斯科 C5/5A 由工作站浏览器连接本机服务。
    </NAlert>
    <NTabs
      v-model:value="activeTab"
      type="card"
      class="rfid-tabs"
      pane-wrapper-style="display:flex;flex:1;min-height:0;overflow:hidden;"
      pane-style="display:flex;flex:1;min-height:0;overflow:hidden;"
    >
      <NTabPane name="profiles" tab="设备档案" display-directive="show">
        <HPageDataTable
          ref="profileTableRef"
          title="设备档案"
          class="rfid-page-table"
          :columns="profileColumns"
          :fetch-data="fetchProfiles"
          :params="profileQuery"
          :scroll-x="1000"
          :show-query-reset-button="true"
          :is-add="false"
          :is-edit="false"
          :is-copy="false"
          :is-delete="false"
          :is-export="false"
          :show-operate="false"
        >
          <template #queryForm>
            <NFormItem label="设备" path="deviceId">
              <NSelect v-model:value="profileQuery.deviceId" :options="deviceOptions" filterable clearable />
            </NFormItem>
            <NFormItem label="角色" path="role">
              <HDictSelect v-model:value="profileQuery.role" type="museum_rfid_device_role" clearable />
            </NFormItem>
            <NFormItem label="连接状态" path="connectionState">
              <HDictSelect v-model:value="profileQuery.connectionState" type="museum_rfid_connection_state" clearable />
            </NFormItem>
            <NFormItem label="硬件序列号" path="hardwareSerial">
              <NInput v-model:value="profileQuery.hardwareSerial" clearable placeholder="输入序列号" />
            </NFormItem>
          </template>
          <template #toolbar-extra><NButton @click="refreshTables">刷新</NButton></template>
        </HPageDataTable>
      </NTabPane>

      <NTabPane name="connections" tab="实时连接" display-directive="show">
        <HPageDataTable
          ref="connectionTableRef"
          title="实时连接"
          class="rfid-page-table"
          :columns="connectionColumns"
          :fetch-data="fetchConnections"
          :params="connectionQuery"
          :scroll-x="1000"
          :show-query-reset-button="true"
          :is-add="false"
          :is-edit="false"
          :is-copy="false"
          :is-delete="false"
          :is-export="false"
          :show-operate="false"
        >
          <template #queryForm>
            <NFormItem label="设备" path="deviceId">
              <NSelect v-model:value="connectionQuery.deviceId" :options="deviceOptions" filterable clearable />
            </NFormItem>
            <NFormItem label="状态" path="state">
              <HDictSelect v-model:value="connectionQuery.state" type="museum_rfid_connection_state" clearable />
            </NFormItem>
            <NFormItem label="远端地址" path="remoteAddress">
              <NInput v-model:value="connectionQuery.remoteAddress" clearable placeholder="IP / 端口" />
            </NFormItem>
          </template>
        </HPageDataTable>
      </NTabPane>

      <NTabPane name="commands" tab="命令审计" display-directive="show">
        <HPageDataTable
          ref="commandTableRef"
          title="命令审计"
          class="rfid-page-table"
          :columns="commandColumns"
          :fetch-data="museumRfidApi.commands"
          :params="commandQuery"
          :scroll-x="1200"
          :show-query-reset-button="true"
          :is-add="false"
          :is-edit="false"
          :is-copy="false"
          :is-delete="false"
          :is-export="false"
          :show-operate="false"
        >
          <template #queryForm>
            <NFormItem label="设备" path="deviceId">
              <NSelect v-model:value="commandQuery.deviceId" :options="deviceOptions" filterable clearable />
            </NFormItem>
            <NFormItem label="状态" path="status">
              <HDictSelect v-model:value="commandQuery.status" type="museum_device_command_status" clearable />
            </NFormItem>
          </template>
        </HPageDataTable>
      </NTabPane>
    </NTabs>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { NAlert, NButton, NFormItem, NInput, NSelect, NTabPane, NTabs } from 'naive-ui';
import { getDictLabel, useDict } from '@/utils/dict';
import { ingestApi } from '../api/ingest';
import { museumRfidApi } from '../api/rfid';
import type { DeviceVo } from '../typings';

const activeTab = ref('profiles');
const profileTableRef = ref<any>(null);
const connectionTableRef = ref<any>(null);
const commandTableRef = ref<any>(null);
const devices = ref<DeviceVo[]>([]);
const profileQuery = reactive({
  deviceId: null as string | null,
  role: null as string | null,
  connectionState: null as string | null,
  hardwareSerial: null as string | null,
});
const connectionQuery = reactive({
  deviceId: null as string | null,
  state: null as string | null,
  remoteAddress: null as string | null,
});
const commandQuery = reactive({ deviceId: null as string | null, status: null as string | null });
const { options: roleOptions } = useDict('museum_rfid_device_role');
const deviceOptions = computed(() =>
  devices.value
    .filter((device) => device.id && device.businessRoles?.length)
    .map((device) => ({
      label: `${device.name || device.code || '未命名设备'}（${device.code || device.id}）${device.locationPath ? ` · ${device.locationPath}` : ''}`,
      value: device.id!,
    })),
);
let timer: number | undefined;
function time(value?: number) {
  return value ? new Date(value).toLocaleString() : '--';
}
async function load() {
  const response: any = await ingestApi.devices();
  devices.value = response?.data || [];
}
async function fetchProfiles(params: any) {
  const hardwareSerial = normalizedKeyword(params.hardwareSerial);
  const rows = responseRows(await museumRfidApi.profiles()).filter((row: any) => {
    if (params.deviceId && row.deviceId !== params.deviceId) return false;
    if (params.role && !(row.roles || [row.role]).includes(params.role)) return false;
    if (params.connectionState && row.connectionState !== params.connectionState) return false;
    return (
      !hardwareSerial ||
      String(row.hardwareSerial || '')
        .toLowerCase()
        .includes(hardwareSerial)
    );
  });
  return pageResponse(rows, params);
}
async function fetchConnections(params: any) {
  const remoteAddress = normalizedKeyword(params.remoteAddress);
  const rows = responseRows(await museumRfidApi.connections()).filter((row: any) => {
    if (params.deviceId && row.deviceId !== params.deviceId) return false;
    if (params.state && row.state !== params.state) return false;
    return (
      !remoteAddress ||
      String(row.remoteAddress || '')
        .toLowerCase()
        .includes(remoteAddress)
    );
  });
  return pageResponse(rows, params);
}
function refreshTables() {
  profileTableRef.value?.handleRefresh?.();
  connectionTableRef.value?.handleRefresh?.();
  commandTableRef.value?.handleRefresh?.();
}
const profileColumns = [
  { title: '设备', key: 'deviceName', minWidth: 160 },
  { title: '设备编码', key: 'deviceCode', width: 130 },
  {
    title: '业务角色',
    key: 'roles',
    minWidth: 220,
    render: (row: any) =>
      (row.roles || [row.role]).map((value: string) => dictLabel(roleOptions.value, value)).join('、'),
  },
  { title: '硬件序列号', key: 'hardwareSerial', minWidth: 220 },
  { title: '天线', key: 'antennaCount', width: 70 },
  { title: '连接', key: 'connectionState', width: 110, dictType: 'museum_rfid_connection_state' },
  { title: '最近上报', key: 'lastSeenAt', width: 180, render: (row: any) => time(row.lastSeenAt) },
];
const connectionColumns = [
  { title: '设备 ID', key: 'deviceId', minWidth: 180 },
  { title: '远端地址', key: 'remoteAddress', minWidth: 180 },
  { title: '状态', key: 'state', width: 110, dictType: 'museum_rfid_connection_state' },
  { title: '协议', key: 'protocolVersion', width: 100 },
  { title: '连接时间', key: 'connectedAt', width: 180, render: (row: any) => time(row.connectedAt) },
  { title: '最后活动', key: 'lastSeenAt', width: 180, render: (row: any) => time(row.lastSeenAt) },
  { title: '错误', key: 'lastError', minWidth: 220 },
];
const commandColumns = [
  { title: '创建时间', key: 'createdAt', width: 180, render: (row: any) => time(row.createdAt) },
  { title: '设备 ID', key: 'deviceId', minWidth: 180 },
  { title: '命令', key: 'commandType', width: 160, dictType: 'museum_device_command_type' },
  { title: '状态', key: 'status', width: 140, dictType: 'museum_device_command_status' },
  { title: '业务 ID', key: 'businessId', minWidth: 180 },
  { title: '结果码', key: 'resultCode', width: 150 },
  { title: '结果', key: 'resultSummary', minWidth: 220 },
];
function responseRows(response: any): any[] {
  const data = response?.data?.data ?? response?.data ?? response;
  return Array.isArray(data) ? data : Array.isArray(data?.list) ? data.list : [];
}
function dictLabel(options: any[], value: string) {
  const label = getDictLabel(options, value);
  return label === '--' ? value : label;
}
function normalizedKeyword(value: unknown) {
  return String(value || '')
    .trim()
    .toLowerCase();
}
function pageResponse<T>(rows: T[], params: any) {
  const pageSize = Math.max(1, Number(params?.pageSize || 30));
  const pageNum = Math.max(1, Number(params?.pageNum || 1));
  return {
    data: {
      list: rows.slice((pageNum - 1) * pageSize, pageNum * pageSize),
      total: rows.length,
      pages: Math.max(1, Math.ceil(rows.length / pageSize)),
    },
  };
}
onMounted(() => {
  void load();
  timer = window.setInterval(refreshTables, 5000);
});
onBeforeUnmount(() => {
  if (timer) window.clearInterval(timer);
});
</script>

<style scoped>
.rfid-device-page {
  box-sizing: border-box;
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding: 16px;
}
.tip {
  margin-bottom: 16px;
}
.rfid-tabs {
  flex: 1;
  min-height: 0;
}
.rfid-page-table {
  min-height: 0;
}
</style>
