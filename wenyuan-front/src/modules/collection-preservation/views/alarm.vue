<template>
  <HPageDataTable
    ref="tableRef"
    v-model:data="tableRows"
    :clear-selection-on-fetch="true"
    :columns="columns"
    :fetch-data="fetchData"
    :params="queryParams"
    row-key-field="id"
    :is-add="false"
    :is-edit="false"
    :is-delete="false"
    :show-operate="false"
    @row-view="onView"
  >
    <template #toolbar-extra
      ><NAlert v-if="listError" type="error">{{ listError }}</NAlert></template
    >
    <template #queryForm>
      <NFormItem label="位置">
        <WarehousePicker v-model:value="queryParams.warehouseId" />
      </NFormItem>
      <NFormItem label="等级">
        <HDictSelect
          v-model:value="queryParams.level"
          type="pv_alarm_level"
          clearable
          placeholder="预警等级"
          style="width: 140px"
        />
      </NFormItem>
      <NFormItem label="状态">
        <HDictSelect
          v-model:value="queryParams.processStatus"
          type="pv_alarm_process_status"
          clearable
          placeholder="处置状态"
          style="width: 140px"
        />
      </NFormItem>
      <NFormItem label="时间">
        <NDatePicker
          v-model:value="range"
          type="datetimerange"
          clearable
          style="width: 320px"
          @update:value="onRangeChange"
        />
      </NFormItem>
    </template>

    <HPageForm
      v-model:show="viewShow"
      title="预警详情"
      content-only
      cancel-text="关闭"
      :footer-style="{ paddingRight: '36px' }"
    >
      <NAlert v-if="viewError" type="error">{{ viewError }}</NAlert>
      <NSpin v-if="viewLoading" show />
      <div v-if="viewRow">
        <NDescriptions label-placement="left" :column="2" bordered size="small">
          <NDescriptionsItem label="预警事件" :span="2">{{ viewRow.title || '--' }}</NDescriptionsItem>
          <NDescriptionsItem label="设备 / 数据源" :span="2"><DeviceReference :device-id="viewRow.deviceId" :name="viewRow.deviceName" /></NDescriptionsItem>
          <NDescriptionsItem label="等级"><AlarmLevelTag :level="viewRow.level" /></NDescriptionsItem>
          <NDescriptionsItem label="处置状态"
            ><HDictText type="pv_alarm_process_status" :value="viewRow.processStatus"
          /></NDescriptionsItem>
          <NDescriptionsItem label="规则来源">{{ ruleSourceText(viewRow) }}</NDescriptionsItem>
          <NDescriptionsItem label="计算规则版本"><AlarmRuleVersionLink :version-id="viewRow.ruleVersionId" :org-id="viewRow.orgId" /></NDescriptionsItem>
          <NDescriptionsItem label="来源采集事件" :span="2"><AlarmEventReference :event-id="viewRow.id" /></NDescriptionsItem>
          <NDescriptionsItem label="依据标准">{{ viewRow.standardRefs?.join('；') || '--' }}</NDescriptionsItem>
          <NDescriptionsItem label="监测位置"><LocationReference :location-id="viewRow.sensorPointId" :name="viewRow.sensorPointName" /></NDescriptionsItem>
          <NDescriptionsItem label="指标"
            >{{ viewRow.sensorName }} {{ viewRow.sensorUnit ? `(${viewRow.sensorUnit})` : '' }}</NDescriptionsItem
          >
          <NDescriptionsItem label="触发数值">{{ viewRow.dataValue }}</NDescriptionsItem>
          <NDescriptionsItem label="位置">{{ viewRow.warehouseName || '--' }}</NDescriptionsItem>
          <NDescriptionsItem label="开始时间"
            ><span style="white-space: nowrap">{{
              formatDateTime(viewRow.startedAt || viewRow.dateTime)
            }}</span></NDescriptionsItem
          >
          <NDescriptionsItem label="最近异常"
            ><span style="white-space: nowrap">{{
              formatDateTime(viewRow.lastObservedAt || viewRow.dateTime)
            }}</span></NDescriptionsItem
          >
          <NDescriptionsItem label="来源告警结束时间"
            ><span style="white-space: nowrap">{{ formatDateTime(viewRow.closedAt) }}</span></NDescriptionsItem
          >
          <NDescriptionsItem label="持续时长">{{ alarmDuration(viewRow) }}</NDescriptionsItem>
          <NDescriptionsItem label="受影响藏品">{{ viewRow.affectedCount ?? 0 }} 件</NDescriptionsItem>
          <NDescriptionsItem v-if="viewRow.materials?.length" label="适用材质" :span="2">
            {{ viewRow.materials.join('、') }}
          </NDescriptionsItem>
          <NDescriptionsItem v-if="viewRow.restorationProjectId" label="关联修复项目" :span="2"><RestorationReference :project-id="viewRow.restorationProjectId" /></NDescriptionsItem>
          <NDescriptionsItem v-if="viewRow.handleBy" label="处置人"><UserText :value="viewRow.handleBy" /></NDescriptionsItem>
          <NDescriptionsItem v-if="viewRow.handleTime" label="处置时间">{{ formatDateTime(viewRow.handleTime) }}</NDescriptionsItem>
          <NDescriptionsItem v-if="viewRow.reviewer && viewRow.reviewTime" label="复核人"><UserText :value="viewRow.reviewer" /></NDescriptionsItem>
          <NDescriptionsItem label="处置说明" :span="2"
            ><div style="white-space: pre-wrap; overflow-wrap: anywhere">
              {{ viewRow.handleReason || '--' }}
            </div></NDescriptionsItem
          >
          <NDescriptionsItem v-if="viewRow.escalationFlowInstanceId" label="升级审批" :span="2">
            <NSpace align="center" :size="6">
              <NTag type="warning" :bordered="false" size="small">已发起升级</NTag>
              <NButton size="tiny" quaternary type="primary" @click="goEscalation(viewRow.escalationFlowInstanceId!)">
                查看审批进度
              </NButton>
              <NText depth="3">审批结果保留在原流程中；实际处置、逐件检查和关闭复核仍须单独登记。</NText>
            </NSpace>
          </NDescriptionsItem>
        </NDescriptions>

        <NCard size="small" title="处置进度" style="margin-top: 16px">
          <NSteps
            :current="processStep"
            :status="viewRow.processStatus === 'CLOSED' ? 'finish' : 'process'"
            size="small"
          >
            <NStep title="开始处置" /><NStep title="登记处置结果" /><NStep title="逐件检查与复核关闭" />
          </NSteps>
          <NAlert
            v-if="viewRow.processStatus === 'TRANSFERRED_RESTORATION'"
            type="warning"
            :bordered="false"
            style="margin-top: 12px"
            >已转修复，关联项目须完成验收及养护复核后才能关闭。</NAlert
          >
        </NCard>
        <NDivider>受影响藏品</NDivider>
        <AffectedCollectionList :items="affected" />
        <NAlert v-if="affected.length < (viewRow.affectedCount || 0)" type="warning"
          >原告警涉及 {{ viewRow.affectedCount }} 件，当前可查询
          {{ affected.length }} 件。请核对缺失或已转馆藏品的处置与交接，不能仅按当前可见清单关闭。</NAlert
        >

        <NSpace
          v-if="hasClosureRecords || (viewRow.canProcess === true && viewRow.processStatus === 'PROCESSED')"
          style="margin: 12px 0"
          ><NButton v-if="hasClosureRecords" size="small" :disabled="historyLoading || busy" @click="openHistory"
            >本事件复核记录</NButton
          ><NButton
            v-if="viewRow.canProcess === true && viewRow.processStatus === 'PROCESSED'"
            size="small"
            :disabled="busy || viewLoading"
            @click="openProcess"
            >修改处置说明</NButton
          ></NSpace
        >
        <NAlert v-if="historyError" type="error" :bordered="false">
          {{ historyError }}
          <NButton text type="primary" :loading="historyLoading" @click="loadHistoryPresence">重试</NButton>
        </NAlert>
        <NText v-if="viewRow.reviewTime">业务实际复核时间：{{ formatDateTime(viewRow.reviewTime) }}</NText>
        <NAlert v-if="viewRow.processStatus === 'CLOSED'" type="info" :bordered="false">
          此预警已完成处置并关闭。
        </NAlert>
        <NAlert v-else-if="viewRow.canProcess === false" type="warning" :bordered="false">
          当前账号无权处置此场馆的预警，可查看详情和复核记录。
        </NAlert>
        <NAlert v-else-if="viewRow.canProcess !== true" type="warning" :bordered="false">
          未获取到处置权限信息，请刷新重试；若仍未恢复，请检查后端版本。
        </NAlert>
      </div>
      <template #form-action>
        <template v-if="viewRow">
          <NSpace v-if="viewRow.canProcess === true && viewRow.processStatus !== 'CLOSED'" style="margin-right: 12px">
            <NButton
              v-if="viewRow.processStatus === 'AWAIT'"
              type="primary"
              size="medium"
              :disabled="busy || viewLoading"
              @click="startProcess"
              >开始处置</NButton
            >
            <NButton
              v-if="viewRow.processStatus === 'PROCESSING'"
              type="primary"
              size="medium"
              :disabled="busy || viewLoading"
              @click="openProcess"
              >登记处置结果</NButton
            >
            <NButton
              v-if="['PROCESSED', 'TRANSFERRED_RESTORATION'].includes(viewRow.processStatus || '')"
              type="primary"
              size="medium"
              :disabled="busy || viewLoading"
              @click="openClose"
              >逐件检查与复核关闭</NButton
            >
          </NSpace>
        </template>
      </template>
    </HPageForm>

    <HPageForm v-model:show="processShow" title="登记处置结果" content-only :footer-style="{ paddingRight: '36px' }">
      <NAlert v-if="processError" type="error">{{ processError }}</NAlert>
      <NFormItem label="处置措施 / 结果" required
        ><NInput
          v-model:value="processReason"
          type="textarea"
          :rows="6"
          :maxlength="2000"
          :disabled="busy"
          placeholder="登记实际采取的措施及处理结果"
      /></NFormItem>
      <template #form-action>
        <NButton
          type="primary"
          :disabled="!processReason.trim() || busy"
          :loading="busy"
          style="margin-right: 12px"
          @click="processHandler"
          >提交处置结果</NButton
        >
      </template>
    </HPageForm>

    <PreservationClosureDrawer ref="closureRef" @saved="afterClosure" />
  </HPageDataTable>
</template>

<script setup lang="ts">
import PreservationClosureDrawer from '../components/PreservationClosureDrawer.vue';
const closureRef = ref<any>();
import { computed, h, onBeforeUnmount, onActivated, onDeactivated, reactive, ref, watch } from 'vue';
import {
  NAlert,
  NSpin,
  NButton,
  NDatePicker,
  NDescriptions,
  NDescriptionsItem,
  NDivider,
  NFormItem,
  NInput,
  NCard,
  NSteps,
  NStep,
  NSpace,
  NTag,
  useMessage,
} from 'naive-ui';
import { formatDateTime } from '@/utils';
import { alarmApi } from '../api/alarm';
import AlarmLevelTag from '../components/AlarmLevelTag.vue';
import AlarmRuleVersionLink from '@/modules/museum-device/components/AlarmRuleVersionLink.vue';
import AlarmEventReference from '@/modules/museum-device/components/AlarmEventReference.vue';
import DeviceReference from '@/modules/museum-device/components/DeviceReference.vue';
import LocationReference from '@/modules/museum-device/components/LocationReference.vue';
import RestorationReference from '@/modules/collection-restoration/components/RestorationReference.vue';
import UserText from '@/modules/sys/components/UserText.vue';
import AffectedCollectionList from '../components/AffectedCollectionList.vue';
import WarehousePicker from '../components/WarehousePicker.vue';
import type { AlarmEventVo } from '../typings';

const message = useMessage();
const tableRef = ref<any>(null);
const tableRows = ref<any[]>([]),
  listError = ref('');
const queryParams = reactive({
  warehouseId: null as string | null,
  level: null as string | null,
  processStatus: null as string | null,
  from: null as number | null,
  to: null as number | null,
});
const range = ref<[number, number] | null>(null);

const viewShow = ref(false);
const viewRow = ref<AlarmEventVo | null>(null);
const affected = ref<any[]>([]);
const hasClosureRecords = ref(false),
  historyLoading = ref(false),
  historyError = ref('');
let historyRequest = 0;

const processShow = ref(false);
const processReason = ref(''),
  processError = ref('');
const processStep = computed(
  () =>
    ({ AWAIT: 1, PROCESSING: 2, PROCESSED: 3, TRANSFERRED_RESTORATION: 3, CLOSED: 3 })[
      viewRow.value?.processStatus || 'AWAIT'
    ],
);

function onRangeChange(v: [number, number] | null) {
  queryParams.from = v?.[0] ?? null;
  queryParams.to = v?.[1] ?? null;
  tableRef.value?.handleRefresh();
}

async function fetchData(params: any) {
  if (disposed || !active) throw new Error('页面已离开');
  const current = ++listGeneration;
  listError.value = '';
  try {
    const result = await alarmApi.page({ ...params, ...queryParams });
    if (disposed || !active || current !== listGeneration) throw new Error('列表请求已失效');
    const data = unpack(result);
    if (!Array.isArray(data?.list ?? data)) throw new Error('预警列表格式异常，请重新读取');
    return result;
  } catch (e: any) {
    if (!disposed && active && current === listGeneration) {
      tableRows.value = [];
      listError.value = e?.message || '列表读取失败';
      clearView();
      viewError.value = e?.message || '预警列表读取失败';
    }
    throw e;
  }
}

const columns = [
  {
    title: '详情 / 处置',
    key: 'actions',
    width: 130,
    fixed: 'right' as const,
    render: (row: AlarmEventVo) =>
      h(NButton, { text: true, type: 'primary', onClick: () => onView(row) }, { default: () => '详情 / 处置' }),
  },
  {
    title: '开始时间',
    key: 'startedAt',
    width: 190, resizable: true,
    ellipsis: { tooltip: true },
    render: (row: AlarmEventVo) => formatDateTime(row.startedAt || row.dateTime),
  },
  {
    title: '最近异常',
    key: 'lastObservedAt',
    width: 190, resizable: true,
    ellipsis: { tooltip: true },
    render: (row: AlarmEventVo) => formatDateTime(row.lastObservedAt || row.dateTime),
  },
  { title: '等级', key: 'level', width: 120, ellipsis: { tooltip: true }, resizable: true, render: (row: any) => h(AlarmLevelTag, { level: row.level }) },
  { title: '规则来源', key: 'alarmSource', width: 150, ellipsis: { tooltip: true }, resizable: true, render: (row: AlarmEventVo) => ruleSourceText(row) },
  { title: '状态', key: 'processStatus', width: 130, ellipsis: { tooltip: true }, resizable: true, dictType: 'pv_alarm_process_status' },
  { title: '预警事件', key: 'title', minWidth: 240, resizable: true, width: 240, columnType: 'title', ellipsis: { tooltip: true } },
  { title: '监测位置', key: 'sensorPointName', minWidth: 150, resizable: true, width: 180, ellipsis: { tooltip: true } },
  { title: '位置', key: 'warehouseName', minWidth: 130, resizable: true, width: 180, ellipsis: { tooltip: true } },
  { title: '指标', key: 'sensorName', width: 140, ellipsis: { tooltip: true }, resizable: true },
  { title: '数值', key: 'dataValue', width: 90 },
  { title: '受影响', key: 'affectedCount', width: 90, render: (r: any) => `${r.affectedCount ?? 0} 件` },
];

const alarmSourceLabels = {
  RULE: '采集规则',
  DEVICE: '设备直报',
} as const;
const thresholdSourceLabels = {
  COMPILED_POLICY: '保护方案版本',
} as const;

function ruleSourceText(row: AlarmEventVo): string {
  const source = row.policyId ? '保护方案' : row.alarmSource ? alarmSourceLabels[row.alarmSource] : '未知来源';
  const threshold = row.thresholdSource ? thresholdSourceLabels[row.thresholdSource] : null;
  return threshold ? `${source} · ${threshold}` : source;
}

function alarmDuration(row: AlarmEventVo): string {
  const start = row.startedAt || row.dateTime;
  const end = row.closedAt || row.lastObservedAt;
  if (!start || !end) return '--';
  const minutes = Math.max(0, Math.round((new Date(end).getTime() - new Date(start).getTime()) / 60_000));
  if (minutes < 60) return `${minutes} 分钟`;
  return `${Math.floor(minutes / 60)} 小时 ${minutes % 60} 分钟`;
}

let generation = 0,
  listGeneration = 0,
  disposed = false,
  active = true,
  closureGeneration = 0,
  processGeneration = 0;
const viewLoading = ref(false),
  viewError = ref(''),
  busy = ref(false);
const processHandler = ref<() => Promise<unknown>>(async () => false);
const unpack = (r: any) => r?.data?.data ?? r?.data ?? r;
const currentView = (token: number, id?: string) =>
  !disposed && active && viewShow.value && generation === token && (!id || viewRow.value?.id === id);
function clearView() {
  ++generation;
  ++historyRequest;
  hasClosureRecords.value = false;
  historyLoading.value = false;
  historyError.value = '';
  viewRow.value = null;
  affected.value = [];
  viewLoading.value = false;
  busy.value = false;
  processShow.value = false;
  processReason.value = '';
  processHandler.value = async () => false;
  closureRef.value?.close();
}
async function onView(row: AlarmEventVo) {
  if (disposed || !active) return;
  clearView();
  viewShow.value = true;
  viewError.value = '';
  viewLoading.value = true;
  const token = generation,
    id = row.id;
  try {
    if (!id) throw new Error('预警标识缺失，请重新读取列表');
    const [detail, items] = await Promise.all([alarmApi.get(id), alarmApi.affected(id)]);
    if (!currentView(token)) return;
    const value = unpack(detail),
      list = unpack(items);
    if (!value || value.id !== id || (row.orgId && value.orgId !== row.orgId) || !Array.isArray(list))
      throw new Error('预警资料或身份已变化，请重新核对');
    viewRow.value = value;
    affected.value = list;
    void loadHistoryPresence();
  } catch (e: any) {
    if (currentView(token)) {
      clearView();
      viewError.value = e?.message || '预警详情读取失败';
    }
  } finally {
    if (currentView(token)) viewLoading.value = false;
  }
}
function goEscalation(instanceId: string) {
  // 走 module-flow 通用待办/详情页，携带流程实例 id
  window.open(`/#/flow/task?instanceId=${instanceId}`);
}

async function startProcess() {
  const row = viewRow.value,
    token = generation;
  if (!row?.id || row.canProcess !== true || row.processStatus !== 'AWAIT' || busy.value || !currentView(token, row.id))
    return;
  busy.value = true;
  try {
    await alarmApi.start(row.id);
    if (!currentView(token, row.id)) return;
    message.success('已进入处置中');
    await refreshViewAndList(row);
  } catch (e: any) {
    if (currentView(token, row.id)) {
      clearView();
      viewError.value = e?.message || '开始处置失败，请重新核对';
    }
  } finally {
    if (currentView(token, row.id)) busy.value = false;
  }
}
function openProcess() {
  const row = viewRow.value,
    token = generation;
  if (
    !row?.id ||
    row.canProcess !== true ||
    row.processStatus === 'CLOSED' ||
    busy.value ||
    !currentView(token, row.id)
  )
    return;
  processReason.value = row.processStatus === 'PROCESSED' ? row.handleReason || '' : '';
  processError.value = '';
  processShow.value = true;
  const session = ++processGeneration;
  processHandler.value = () => submitProcess(row.id!, token, session);
}
async function submitProcess(id: string, token: number, session: number) {
  const current = () => processShow.value && processGeneration === session && currentView(token, id);
  if (!current() || viewRow.value?.canProcess !== true || !processReason.value.trim() || busy.value) return false;
  const reason = processReason.value;
  busy.value = true;
  try {
    await alarmApi.process(id, reason);
    if (!current()) return false;
    processShow.value = false;
    message.success('已提交处置');
    await refreshViewAndList({ id });
  } catch (e: any) {
    if (current()) {
      if ([401, 403].includes(e?.response?.status ?? e?.status)) {
        clearView();
        viewError.value = e?.message || '处置权限已变化，请重新核对';
      } else processError.value = e?.message || '处置提交失败，内容已保留，请重试';
    }
  } finally {
    if (current()) busy.value = false;
  }
}
async function loadHistoryPresence() {
  const id = viewRow.value?.id,
    token = generation;
  if (!id || historyLoading.value || !currentView(token, id)) return;
  const request = ++historyRequest;
  const current = () => request === historyRequest && currentView(token, id);
  hasClosureRecords.value = false;
  historyLoading.value = true;
  historyError.value = '';
  try {
    const records = unpack(await alarmApi.closureRecords(id));
    if (!current()) return;
    if (!Array.isArray(records)) throw new Error('本事件复核记录格式异常，请重新读取');
    hasClosureRecords.value = records.length > 0;
  } catch (e: any) {
    if (current()) historyError.value = e?.message || '本事件复核记录读取失败';
  } finally {
    if (current()) historyLoading.value = false;
  }
}
function openHistory() {
  if (hasClosureRecords.value && !historyLoading.value && viewRow.value?.id && currentView(generation) && !busy.value)
    closureRef.value?.open(viewRow.value.id, true);
}
function openClose() {
  if (viewRow.value?.id && currentView(generation) && !busy.value) {
    closureGeneration = generation;
    closureRef.value?.open(viewRow.value.id);
  }
}
async function refreshViewAndList(row: AlarmEventVo) {
  const pending = onView(row),
    token = generation;
  await pending;
  if (currentView(token, row.id) && viewRow.value) tableRef.value?.handleRefresh();
}
async function afterClosure(target: string) {
  if (target && currentView(closureGeneration, target)) {
    await refreshViewAndList({ id: target });
  }
}
watch(
  viewShow,
  (value) => {
    if (!value) {
      clearView();
      viewError.value = '';
    }
  },
  { flush: 'sync' },
);
watch(
  processShow,
  (value) => {
    if (!value) {
      ++processGeneration;
      busy.value = false;
      processReason.value = '';
      processError.value = '';
      processHandler.value = async () => false;
    }
  },
  { flush: 'sync' },
);
function leavePage() {
  active = false;
  ++listGeneration;
  ++closureGeneration;
  ++processGeneration;
  tableRows.value = [];
  viewShow.value = false;
  clearView();
  listError.value = '';
  viewError.value = '';
}
onDeactivated(leavePage);
onActivated(() => {
  if (disposed || active) return;
  active = true;
  tableRef.value?.handleRefresh();
});
onBeforeUnmount(() => {
  disposed = true;
  leavePage();
});
</script>
