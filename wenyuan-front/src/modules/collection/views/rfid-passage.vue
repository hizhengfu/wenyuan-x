<template>
  <div class="rfid-passage">
    <NTabs
      v-model:value="activeTab"
      type="card"
      class="rfid-tabs"
      pane-wrapper-style="display:flex;flex:1;min-height:0;overflow:hidden;"
      pane-style="display:flex;flex:1;min-height:0;overflow:hidden;"
    >
      <NTabPane name="events" tab="通行记录" display-directive="show">
        <template #tab>通行记录 <NBadge :value="eventTotal" :max="999" /></template>
        <HPageDataTable auto-scroll-x
          ref="eventTableRef"
          title="通行记录"
          class="rfid-page-table"
          :columns="eventColumns"
          :fetch-data="fetchEvents"
          :params="eventQuery"
          :pagination="{ pageSize: 20 }"
          :scroll-x="1100"
          :show-query-reset-button="true"
          :is-add="false"
          :is-edit="false"
          :is-copy="false"
          :is-delete="false"
          :is-export="false"
          :show-operate="false"
        >
          <template #queryForm>
            <NFormItem label="检索内容" path="keyword">
              <NInput v-model:value="eventQuery.keyword" clearable placeholder="EPC / 藏品 / 采集记录编号" />
            </NFormItem>
            <NFormItem label="通行通道" path="gateId">
              <NSelect v-model:value="eventQuery.gateId" :options="gateOptions" clearable />
            </NFormItem>
            <NFormItem label="通行方向" path="direction">
              <HDictSelect v-model:value="eventQuery.direction" type="col_rfid_direction" clearable />
            </NFormItem>
            <NFormItem label="通行判定" path="decision">
              <HDictSelect v-model:value="eventQuery.decision" type="col_rfid_passage_decision" clearable />
            </NFormItem>
            <NFormItem label="发生时间" path="occurredRange">
              <NDatePicker v-model:value="eventQuery.occurredRange" type="datetimerange" clearable />
            </NFormItem>
          </template>
          <template #toolbar-extra>
            <NSpace>
              <NTag :type="realtimeStatus === 'OPEN' ? 'success' : 'warning'">实时 {{ realtimeStatusLabel }}</NTag>
              <NButton @click="refreshTables">刷新</NButton>
            </NSpace>
          </template>
        </HPageDataTable>
      </NTabPane>

      <NTabPane name="anomalies" tab="异常处置" display-directive="show">
        <HPageDataTable auto-scroll-x
          ref="anomalyTableRef"
          title="异常处置"
          class="rfid-page-table"
          :columns="anomalyColumns"
          :fetch-data="fetchAnomalies"
          :params="anomalyQuery"
          :pagination="{ pageSize: 20 }"
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
            <NFormItem label="检索内容" path="keyword">
              <NInput v-model:value="anomalyQuery.keyword" clearable placeholder="标题 / EPC / 说明" />
            </NFormItem>
            <NFormItem label="异常类型" path="type">
              <HDictSelect v-model:value="anomalyQuery.type" type="col_rfid_anomaly_type" clearable />
            </NFormItem>
            <NFormItem label="异常状态" path="status">
              <HDictSelect v-model:value="anomalyQuery.status" type="col_rfid_anomaly_status" clearable />
            </NFormItem>
            <NFormItem label="开启时间" path="openedRange">
              <NDatePicker v-model:value="anomalyQuery.openedRange" type="datetimerange" clearable />
            </NFormItem>
          </template>
          <template #toolbar-extra>
            <NSpace>
              <NButton :type="soundEnabled ? 'success' : 'default'" @click="enableSound">{{
                soundEnabled ? '告警声音已启用' : '启用告警声音'
              }}</NButton>
              <NTag :type="openAnomalyCount ? 'error' : 'success'">待处理 {{ openAnomalyCount }}</NTag>
            </NSpace>
          </template>
        </HPageDataTable>
      </NTabPane>

      <NTabPane name="grants" tab="历史通行授权" display-directive="show">
        <HPageDataTable auto-scroll-x
          ref="grantTableRef"
          title="历史通行授权"
          class="rfid-page-table"
          :columns="grantColumns"
          :fetch-data="fetchGrants"
          :params="grantQuery"
          :pagination="{ pageSize: 20 }"
          :scroll-x="1100"
          :show-query-reset-button="true"
          :is-edit="false"
          :is-copy="false"
          :is-delete="false"
          :is-export="false"
          :show-operate="false"
          :is-add="false"
        >
          <template #queryForm>
            <NFormItem label="检索内容" path="keyword">
              <NInput v-model:value="grantQuery.keyword" clearable placeholder="藏品 / EPC / 来源单据" />
            </NFormItem>
            <NFormItem label="来源类型" path="sourceType">
              <HDictSelect v-model:value="grantQuery.sourceType" type="col_rfid_passage_source_type" clearable />
            </NFormItem>
            <NFormItem label="通行通道" path="gateId">
              <NSelect v-model:value="grantQuery.gateId" :options="gateOptions" clearable />
            </NFormItem>
            <NFormItem label="通行方向" path="direction">
              <NSelect v-model:value="grantQuery.direction" :options="directionOptions" clearable />
            </NFormItem>
            <NFormItem label="授权状态" path="status">
              <HDictSelect v-model:value="grantQuery.status" type="col_rfid_passage_grant_status" clearable />
            </NFormItem>
          </template>

          <template #toolbar-extra>
            <NText depth="3">历史授权仅供追溯。门禁按已完成的 RFID 出入库流程核验，并记录每次读卡。</NText>
          </template>
        </HPageDataTable>
      </NTabPane>
    </NTabs>
    <NModal v-model:show="detailVisible" preset="card" title="RFID 异常证据">
      <NDescriptions v-if="selectedAnomaly" bordered :column="2" label-placement="left">
        <NDescriptionsItem label="异常编号">{{ selectedAnomaly.id }}</NDescriptionsItem>
        <NDescriptionsItem label="状态">
          <HDictText type="col_rfid_anomaly_status" :value="selectedAnomaly.status" />
        </NDescriptionsItem>
        <NDescriptionsItem label="类型">
          <HDictText type="col_rfid_anomaly_type" :value="selectedAnomaly.type" />
        </NDescriptionsItem>
        <NDescriptionsItem label="开启时间">{{ time(selectedAnomaly.openedAt) }}</NDescriptionsItem>
        <NDescriptionsItem label="藏品">{{ selectedAnomaly.collectionId || '未识别' }}</NDescriptionsItem>
        <NDescriptionsItem label="EPC">{{ selectedAnomaly.epc || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="设备">{{ selectedAnomaly.evidence?.deviceId || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="通道">{{ selectedAnomaly.evidence?.gateId || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="天线">{{ selectedAnomaly.evidence?.antenna ?? '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="方向">
          <HDictText type="col_rfid_direction" :value="selectedAnomaly.evidence?.direction" />
        </NDescriptionsItem>
        <NDescriptionsItem label="设备发生时间">{{ time(selectedAnomaly.evidence?.occurredAt) }}</NDescriptionsItem>
        <NDescriptionsItem label="服务端接收时间">{{ time(selectedAnomaly.evidence?.receivedAt) }}</NDescriptionsItem>
        <NDescriptionsItem label="授权判定">
          <HDictText type="col_rfid_passage_decision" :value="selectedAnomaly.evidence?.decision" />
        </NDescriptionsItem>
        <NDescriptionsItem label="扫描会话">{{ selectedAnomaly.evidence?.sourceSessionId || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="出入库单据">{{ selectedAnomaly.evidence?.sourceId || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="核验原因" :span="2">{{ selectedAnomaly.evidence?.reason || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="报警状态">
          <HDictText type="col_rfid_alarm_status" :value="selectedAnomaly.evidence?.alarmStatus" />
        </NDescriptionsItem>
        <NDescriptionsItem label="报警命令">{{ selectedAnomaly.evidence?.alarmCommandId || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="报警回执" :span="2">{{
          selectedAnomaly.evidence?.alarmMessage || '--'
        }}</NDescriptionsItem>
        <NDescriptionsItem label="TID" :span="2">{{ selectedAnomaly.evidence?.tid || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="事实摘要" :span="2">{{ selectedAnomaly.detail || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="处置说明" :span="2" content-style="white-space: pre-wrap; overflow-wrap: anywhere;">{{ selectedAnomaly.resolutionNote || '--' }}</NDescriptionsItem>
      </NDescriptions>
    </NModal>
  </div>
</template>

<script setup lang="ts">
import { computed, h, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import {
  NBadge,
  NButton,
  NDatePicker,
  NDescriptions,
  NDescriptionsItem,
  NFormItem,
  NInput,
  NModal,
  NSelect,
  NSpace,
  NTabPane,
  NTabs,
  NTag,
  NText,
  useDialog,
  useMessage,
} from 'naive-ui';
import { getDictLabel, useDict } from '@/utils/dict';
import { rfidApi } from '../api/rfid';
import { useRfidRealtime } from '../composables/useRfidRealtime';
import type { RfidRealtimeEvent } from '../typings/rfid';

const message = useMessage();
const dialog = useDialog();
const activeTab = ref('events');
const eventTableRef = ref<any>(null);
const anomalyTableRef = ref<any>(null);
const grantTableRef = ref<any>(null);
const eventTotal = ref(0);
const openAnomalyCount = ref(0);
const gateDevices = ref<{ id: string; name: string; code: string }[]>([]);
type PassageSourceType = 'COLLECTION_LOAN' | 'COLLECTION_REQUISITION' | 'COLLECTION_RETURN';
const eventQuery = reactive({
  keyword: null as string | null,
  gateId: null as string | null,
  direction: null as string | null,
  decision: null as string | null,
  occurredRange: null as [number, number] | null,
});
const anomalyQuery = reactive({
  keyword: null as string | null,
  type: null as string | null,
  status: null as string | null,
  openedRange: null as [number, number] | null,
});
const grantQuery = reactive({
  keyword: null as string | null,
  sourceType: null as PassageSourceType | null,
  gateId: null as string | null,
  direction: null as string | null,
  status: null as string | null,
});
const detailVisible = ref(false);
const selectedAnomaly = ref<any>(null);
const soundEnabled = ref(false);
const seenAnomalies = new Set<string>();
let audioContext: AudioContext | undefined;
let anomalyLoaded = false;
let realtimeRefreshTimer: number | undefined;
const { options: directionDictOptions } = useDict('col_rfid_direction');
const { options: anomalyStatusOptions } = useDict('col_rfid_anomaly_status');
const directionOptions = computed(() =>
  directionDictOptions.value.filter((option: any) => option.value === 'IN' || option.value === 'OUT'),
);
const { status: realtimeStatus, connectScope, disconnect } = useRfidRealtime(handleRealtimeEvent);
const realtimeStatusLabel = computed(
  () => ({ CLOSED: '已断开', CONNECTING: '连接中', OPEN: '已连接', RETRYING: '重连中' })[realtimeStatus.value],
);
const gateOptions = computed(() =>
  gateDevices.value.map((item) => ({ label: `${item.name}（${item.code}）`, value: item.id })),
);

async function fetchEvents(params: any) {
  const response: any = await rfidApi.passageEvents({
    ...params,
    occurredRange: undefined,
    occurredAtStart: params.occurredRange?.[0],
    occurredAtEnd: params.occurredRange?.[1],
  });
  eventTotal.value = Number(response?.data?.total || 0);
  return response;
}
async function fetchAnomalies(params: any) {
  const [response, activeResponse]: any[] = await Promise.all([
    rfidApi.anomalies({
      ...params,
      openedRange: undefined,
      openedAtStart: params.openedRange?.[0],
      openedAtEnd: params.openedRange?.[1],
    }),
    rfidApi.anomalies({ active: true, pageNum: 1, pageSize: 1 }),
  ]);
  const nextAnomalies = responseRows(response);
  if (
    anomalyLoaded &&
    soundEnabled.value &&
    nextAnomalies.some((item: any) => !seenAnomalies.has(item.id) && item.status === 'OPEN')
  )
    beep();
  nextAnomalies.forEach((item: any) => seenAnomalies.add(item.id));
  anomalyLoaded = true;
  openAnomalyCount.value = Number(activeResponse?.data?.total || 0);
  return response;
}
async function fetchGrants(params: any) {
  return rfidApi.grants(params);
}
async function loadGateDevices() {
  gateDevices.value = responseRows(await rfidApi.passageDevices());
}
function refreshTables() {
  eventTableRef.value?.handleRefresh?.();
  anomalyTableRef.value?.handleRefresh?.();
  grantTableRef.value?.handleRefresh?.();
}
function handleRealtimeEvent(event: RfidRealtimeEvent) {
  if (event.type === 'BUSINESS_ANOMALY') {
    const anomalyId = String(event.payload.anomalyId || '');
    if (anomalyId && !seenAnomalies.has(anomalyId)) {
      seenAnomalies.add(anomalyId);
      if (soundEnabled.value) beep();
    }
  }
  if (!['GATE_EVENT', 'BUSINESS_ANOMALY', 'COMMAND_RESULT', 'RESYNC_REQUIRED'].includes(event.type)) return;
  if (realtimeRefreshTimer) return;
  realtimeRefreshTimer = window.setTimeout(() => {
    realtimeRefreshTimer = undefined;
    refreshTables();
  }, 300);
}
async function enableSound() {
  audioContext ||= new AudioContext();
  await audioContext.resume();
  soundEnabled.value = true;
  beep(0.08);
}
function beep(duration = 0.35) {
  if (!audioContext || audioContext.state !== 'running') return;
  const oscillator = audioContext.createOscillator();
  const gain = audioContext.createGain();
  oscillator.type = 'square';
  oscillator.frequency.value = 880;
  gain.gain.setValueAtTime(0.12, audioContext.currentTime);
  gain.gain.exponentialRampToValueAtTime(0.001, audioContext.currentTime + duration);
  oscillator.connect(gain);
  gain.connect(audioContext.destination);
  oscillator.start();
  oscillator.stop(audioContext.currentTime + duration);
}
async function showAnomaly(row: any) {
  selectedAnomaly.value = ((await rfidApi.anomaly(row.id)) as any)?.data;
  detailVisible.value = true;
}
function revokeGrant(row: any) {
  let reason = '';
  dialog.warning({
    title: '撤销通行授权',
    content: () =>
      h(NInput, {
        type: 'textarea',
        placeholder: '请输入撤销原因',
        autosize: { minRows: 3, maxRows: 5 },
        'onUpdate:value': (value: string) => {
          reason = value;
        },
      }),
    positiveText: '确认撤销',
    negativeText: '取消',
    onPositiveClick: async () => {
      if (!reason.trim()) {
        message.warning('请输入撤销原因');
        return false;
      }
      await rfidApi.revokeGrant(row.id, reason.trim());
      message.success('通行授权已撤销');
      grantTableRef.value?.handleRefresh?.();
      return true;
    },
  });
}
function grantActions(row: any) {
  if (!['PENDING', 'ACTIVE'].includes(row.status)) return '--';
  return h(NButton, { text: true, type: 'error', onClick: () => revokeGrant(row) }, () => '撤销');
}
async function process(row: any, status: string) {
  const statusLabel = dictLabel(anomalyStatusOptions.value, status);
  dialog.warning({
    title: '异常处置',
    content: `确认将异常设为“${statusLabel}”？处置过程会写入审计时间线。`,
    positiveText: '确认',
    negativeText: '取消',
    onPositiveClick: async () => {
      await rfidApi.processAnomaly(row.id, { status, note: '管理端处置' });
      anomalyTableRef.value?.handleRefresh?.();
    },
  });
}
function anomalyActions(row: any) {
  const buttons = [h(NButton, { text: true, type: 'primary', onClick: () => showAnomaly(row) }, () => '详情')];
  if (row.status === 'RESOLVED' || row.status === 'FALSE_POSITIVE') return buttons;
  if (row.status === 'OPEN')
    buttons.push(
      h(NButton, { text: true, type: 'warning', onClick: () => process(row, 'ACKNOWLEDGED') }, () => '确认'),
    );
  if (['OPEN', 'ACKNOWLEDGED'].includes(row.status))
    buttons.push(
      h(NButton, { text: true, style: 'margin-left:12px', onClick: () => process(row, 'INVESTIGATING') }, () => '调查'),
    );
  if (['ACKNOWLEDGED', 'INVESTIGATING', 'ESCALATED'].includes(row.status))
    buttons.push(
      h(
        NButton,
        { text: true, type: 'success', style: 'margin-left:12px', onClick: () => process(row, 'RESOLVED') },
        () => '解决',
      ),
    );
  buttons.push(
    h(NButton, { text: true, style: 'margin-left:12px', onClick: () => process(row, 'FALSE_POSITIVE') }, () => '误报'),
  );
  return buttons;
}
function time(value?: number) {
  return value ? new Date(value).toLocaleString() : '--';
}
function responseRows(response: any): any[] {
  const data = response?.data?.data ?? response?.data ?? response;
  return Array.isArray(data) ? data : Array.isArray(data?.list) ? data.list : [];
}
function dictLabel(options: any[], value: string) {
  const label = getDictLabel(options, value);
  return label === '--' ? value : label;
}
const eventColumns = [
  { title: '发生时间', key: 'occurredAt', width: 180, render: (row: any) => time(row.occurredAt) },
  { ellipsis: { tooltip: true }, resizable: true, title: '通道', key: 'gateId', width: 170 },
  { ellipsis: { tooltip: true }, resizable: true, title: '方向', key: 'direction', width: 80, dictType: 'col_rfid_direction' },
  { ellipsis: { tooltip: true }, resizable: true, width: 220, title: 'EPC', key: 'epc', minWidth: 220 },
  { ellipsis: { tooltip: true }, resizable: true, width: 180, title: '藏品 ID', key: 'collectionId', minWidth: 180 },
  { ellipsis: { tooltip: true }, resizable: true, title: '决策', key: 'decision', width: 120, dictType: 'col_rfid_passage_decision' },
  { ellipsis: { tooltip: true }, resizable: true, title: '报警', key: 'alarmStatus', width: 110, dictType: 'col_rfid_alarm_status' },
  { ellipsis: { tooltip: true }, resizable: true, width: 180, title: '出入库单据', key: 'sourceId', minWidth: 180 },
  { ellipsis: { tooltip: true }, resizable: true, width: 250, title: '核验原因', key: 'reason', minWidth: 250 },
];
const anomalyColumns = [
  { title: '开启时间', key: 'openedAt', width: 180, render: (row: any) => time(row.openedAt) },
  { ellipsis: { tooltip: true }, resizable: true, title: '类型', key: 'type', width: 180, dictType: 'col_rfid_anomaly_type' },
  { ellipsis: { tooltip: true }, resizable: true, width: 180, title: '标题', key: 'title', minWidth: 180 },
  { ellipsis: { tooltip: true }, resizable: true, width: 210, title: 'EPC', key: 'epc', minWidth: 210 },
  { ellipsis: { tooltip: true }, resizable: true, title: '状态', key: 'status', width: 110, dictType: 'col_rfid_anomaly_status' },
  { ellipsis: { tooltip: true }, resizable: true, width: 220, title: '说明', key: 'detail', minWidth: 220 },
  { title: '操作', key: 'actions', width: 260, render: anomalyActions },
];
const grantColumns = [
  { ellipsis: { tooltip: true }, resizable: true, width: 180, title: '藏品 ID', key: 'collectionId', minWidth: 180 },
  { ellipsis: { tooltip: true }, resizable: true, width: 220, title: 'EPC', key: 'epc', minWidth: 220 },
  { ellipsis: { tooltip: true }, resizable: true, title: '来源', key: 'sourceType', width: 120, dictType: 'col_rfid_passage_source_type' },
  { ellipsis: { tooltip: true }, resizable: true, width: 180, title: '来源单据', key: 'sourceId', minWidth: 180 },
  { ellipsis: { tooltip: true }, resizable: true, width: 170, title: '通道', key: 'gateId', minWidth: 170 },
  { ellipsis: { tooltip: true }, resizable: true, title: '方向', key: 'direction', width: 80, dictType: 'col_rfid_direction' },
  { ellipsis: { tooltip: true }, resizable: true, width: 360,
    title: '有效期',
    key: 'validUntil',
    minWidth: 280,
    render: (row: any) => `${time(row.validFrom)} 至 ${time(row.validUntil)}`,
  },
  { ellipsis: { tooltip: true }, resizable: true, title: '状态', key: 'status', width: 110, dictType: 'col_rfid_passage_grant_status' },
  { title: '操作', key: 'actions', width: 100, render: grantActions },
];
onMounted(() => {
  void loadGateDevices();
  connectScope();
});
onBeforeUnmount(() => {
  disconnect();
  if (realtimeRefreshTimer) window.clearTimeout(realtimeRefreshTimer);
  void audioContext?.close();
});
</script>

<style scoped>
.rfid-passage {
  box-sizing: border-box;
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding: 16px;
}
.rfid-tabs {
  flex: 1;
  min-height: 0;
}
.rfid-page-table {
  min-height: 0;
}
</style>
