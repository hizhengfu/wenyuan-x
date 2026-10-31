<template>
  <HPageDataTable
    ref="tableRef"
    v-model:data="tableRows"
    :clear-selection-on-fetch="true"
    :columns="columns"
    :fetch-data="fetchData"
    :params="queryParams"
    :is-delete="false"
    row-key-field="id"
    :is-add="false"
    :is-edit="false"
    @row-view="onView"
  >
    <template #toolbar-extra>
      <NAlert v-if="listError" type="error">{{ listError }}</NAlert>
      <OriginalHistoryButton business-type="PRESERVATION_REPORT_ORIGINAL" />
      <NButton
        v-permission="'PRESERVATION_ADMIN,PRESERVATION_REPORT_GENERATE'"
        size="small"
        type="primary"
        @click="openGenerate"
        >生成数据报告</NButton
      >
    </template>
    <template #queryForm>
      <NFormItem label="位置">
        <WarehousePicker v-model:value="queryParams.warehouseId" />
      </NFormItem>
      <NFormItem label="周期">
        <HDictSelect
          v-model:value="queryParams.cycleType"
          type="pv_report_cycle"
          clearable
          placeholder="周期"
          style="width: 140px"
        />
      </NFormItem>
    </template>

    <HPageView v-model:show="viewShow" title="数据报告详情" width="640px" :show-action="false">
      <NAlert v-if="viewError" type="error">{{ viewError }}</NAlert>
      <NSpin v-if="viewLoading" show />
      <NPopconfirm v-if="viewRow?.canRemove === true" @positive-click="removeHandler">
        <template #trigger
          ><NButton :disabled="removing" v-permission="'PRESERVATION_ADMIN,PRESERVATION_REPORT_GENERATE'"
            >删除报告</NButton
          ></template
        >确认删除本报告？已留存的原件继续保留。
      </NPopconfirm>
      <NDescriptions v-if="viewRow" label-placement="left" :column="1" bordered size="small">
        <NDescriptionsItem label="周期"
          ><HDictText type="pv_report_cycle" :value="viewRow.cycleType"
        /></NDescriptionsItem>
        <NDescriptionsItem label="时段">
          {{ formatDateValue(viewRow.periodStart) }} ~ {{ formatDateValue(viewRow.periodEnd) }}
        </NDescriptionsItem>
        <NDescriptionsItem label="采样点达标率">{{ pct(viewRow.complianceRate) }}</NDescriptionsItem>
        <NDescriptionsItem label="评估边界"
          >仅比较有适用阈值的有效采样点，不代表整个时段连续监测或持续达标。</NDescriptionsItem
        >
        <NDescriptionsItem label="超标次数">{{ viewRow.exceedCount ?? 0 }}</NDescriptionsItem>
        <NDescriptionsItem label="累计超标时长">{{ duration(viewRow.exceedDurationMinutes) }}</NDescriptionsItem>
        <NDescriptionsItem label="预警处置率">{{ pct(viewRow.handleRate) }}</NDescriptionsItem>
        <NDescriptionsItem label="生成时间"
          ><span style="white-space: nowrap">{{ formatDateTime(viewRow.generatedAt) }}</span></NDescriptionsItem
        >
        <NDescriptionsItem label="位置依据">
          {{
            reportSummary.sampleScopeBasis === 'CURRENT_DEVICE_ASSOCIATION'
              ? '按设备当前关联位置归集原始历史数据'
              : reportSummary.sampleScopeBasis === 'RECORDED_DEVICE_CONFIGURATION'
                ? '按采样时的安装配置归集；不代替现场位置核验'
                : '旧报告未核实历史位置，请重新生成并核查'
          }}
        </NDescriptionsItem>
        <NDescriptionsItem
          v-if="reportSummary.sampleScopeBasis && reportSummary.sampleScopeBasis !== 'CURRENT_DEVICE_ASSOCIATION'"
          label="位置未核实样本"
        >
          候选设备中 {{ reportSummary.unattributedSamples ?? 0 }} 条未能归属，未计入本场所统计及达标率；
          {{ reportSummary.excludedOtherLocationSamples ?? 0 }} 条属于其他位置，已排除。
        </NDescriptionsItem>
        <NDescriptionsItem label="样本统计">
          总计 {{ reportSummary.totalSamples ?? 0 }}；正常 {{ reportSummary.normalSamples ?? 0 }}；可疑
          {{ reportSummary.suspectSamples ?? 0 }}；无效 {{ reportSummary.invalidSamples ?? 0 }}
        </NDescriptionsItem>
        <NDescriptionsItem v-if="reportSummary.sampleScopeBasis" label="时间异常样本">
          {{ reportSummary.timeInvalidSamples ?? 0 }} 条，保留事实但不参与达标率；请核对设备时钟和接收时间。
        </NDescriptionsItem>
        <NDescriptionsItem label="达标统计">
          参与评估 {{ reportSummary.evaluatedSamples ?? 0 }}；达标 {{ reportSummary.compliantSamples ?? 0 }}；超标
          {{ reportSummary.exceededSamples ?? 0 }}
        </NDescriptionsItem>
      </NDescriptions>
    </HPageView>

    <NModal v-model:show="genShow" preset="dialog" title="生成数据报告" style="width: 520px">
      <NAlert v-if="genError" type="error">{{ genError }}</NAlert>
      <NForm
        :disabled="genLoading || generating || !genOrgId"
        :model="genForm"
        label-placement="left"
        label-width="80"
        :rules="genRules"
      >
        <NFormItem label="位置" path="warehouseId">
          <WarehousePicker v-model:value="genForm.warehouseId" />
        </NFormItem>
        <NFormItem label="周期" path="cycleType">
          <HDictSelect v-model:value="genForm.cycleType" type="pv_report_cycle" placeholder="周期" />
        </NFormItem>
        <NFormItem label="起始" path="periodStart">
          <NDatePicker
            v-model:value="genStart"
            type="date"
            clearable
            style="width: 180px"
            @update:value="(v) => (genForm.periodStart = formatDate(v))"
          />
        </NFormItem>
        <NFormItem label="截止" path="periodEnd">
          <NDatePicker
            v-model:value="genEnd"
            type="date"
            clearable
            style="width: 180px"
            @update:value="(v) => (genForm.periodEnd = formatDate(v))"
          />
        </NFormItem>
      </NForm>
      <template #action>
        <NSpace>
          <NButton @click="genShow = false">取消</NButton>
          <NButton
            type="primary"
            :loading="generating || genLoading"
            :disabled="!genOrgId || genLoading || generating"
            @click="submitGenerate"
            >生成</NButton
          >
        </NSpace>
      </template>
    </NModal>
  </HPageDataTable>
</template>

<script setup lang="ts">
import OriginalHistoryButton from '@/modules/collection/components/OriginalHistoryButton.vue';
import { computed, onBeforeUnmount, onActivated, onDeactivated, reactive, ref, watch } from 'vue';
import {
  NAlert,
  NSpin,
  NPopconfirm,
  NButton,
  NDatePicker,
  NDescriptions,
  NDescriptionsItem,
  NForm,
  NFormItem,
  NModal,
  NSpace,
  useMessage,
} from 'naive-ui';
import { formatDateTime, formatDateValue } from '@/utils';
import { reportApi } from '../api/report';
import WarehousePicker from '../components/WarehousePicker.vue';
import type { PvReportCycle, ReportVo } from '../typings';

const message = useMessage();
const tableRef = ref<any>(null);
const tableRows = ref<any[]>([]),
  listError = ref('');
const queryParams = reactive({ warehouseId: null as string | null, cycleType: null as string | null });

async function fetchData(params: any) {
  if (disposed || !active) throw new Error('页面已离开');
  const current = ++listGeneration;
  listError.value = '';
  try {
    const response: any = await reportApi.page({ ...params, ...queryParams });
    if (disposed || !active || current !== listGeneration) throw new Error('列表请求已失效');
    const data = response?.data?.data ?? response?.data ?? response;
    const list = data?.list ?? data;
    if (!Array.isArray(list)) throw new Error('报告列表格式异常，请重新读取');
    list.forEach((row: ReportVo & { periodStartText?: string }) => {
      row.periodStartText = formatDateValue(row.periodStart);
    });
    return response;
  } catch (e: any) {
    if (!disposed && active && current === listGeneration) {
      tableRows.value = [];
      listError.value = e?.message || '列表读取失败';
      clearView();
      viewError.value = e?.message || '报告列表读取失败';
      genShow.value = false;
    }
    throw e;
  }
}

const columns = [
  {
    title: '周期',
    key: 'cycleType',
    width: 110,
    ellipsis: { tooltip: true },
    resizable: true,
    dictType: 'pv_report_cycle',
  },
  {
    title: '起始日期',
    key: 'periodStartText',
    width: 120,
    ellipsis: { tooltip: true },
    resizable: true,
    columnType: 'title',
  },
  {
    title: '截止日期',
    key: 'periodEnd',
    width: 120,
    ellipsis: { tooltip: true },
    resizable: true,
    render: (r: ReportVo) => formatDateValue(r.periodEnd),
  },
  { title: '采样点达标率', key: 'complianceRate', width: 100, render: (r: any) => pct(r.complianceRate) },
  { title: '超标次数', key: 'exceedCount', width: 100 },
  {
    title: '超标时长',
    key: 'exceedDurationMinutes',
    width: 160,
    ellipsis: { tooltip: true },
    resizable: true,
    render: (r: ReportVo) => duration(r.exceedDurationMinutes),
  },
  { title: '预警处置率', key: 'handleRate', width: 110, render: (r: any) => pct(r.handleRate) },
  {
    title: '生成时间',
    key: 'generatedAt',
    width: 190,
    resizable: true,
    ellipsis: { tooltip: true },
    render: (r: ReportVo) => formatDateTime(r.generatedAt),
  },
];

function pct(v?: number | null): string {
  if (v == null) return '--';
  return `${(v * 100).toFixed(1)}%`;
}

const viewShow = ref(false);
const viewRow = ref<ReportVo | null>(null);
let viewGeneration = 0,
  listGeneration = 0,
  genGeneration = 0,
  disposed = false,
  active = true;
const viewLoading = ref(false),
  viewError = ref(''),
  removing = ref(false);
const removeHandler = ref<() => Promise<unknown>>(async () => false);
const unpack = (r: any) => r?.data?.data ?? r?.data ?? r;
const currentView = (token: number) => !disposed && active && viewShow.value && token === viewGeneration;
function clearView() {
  ++viewGeneration;
  viewRow.value = null;
  viewLoading.value = false;
  removing.value = false;
  removeHandler.value = async () => false;
}
async function onView(row: ReportVo) {
  if (disposed || !active) return;
  clearView();
  viewError.value = '';
  viewShow.value = true;
  const token = viewGeneration,
    id = row.id;
  viewLoading.value = true;
  try {
    if (!id) throw new Error('报告标识缺失，请重新读取列表');
    const value = unpack(await reportApi.get(id));
    if (!currentView(token)) return;
    if (!value || value.id !== id || (row.orgId && value.orgId !== row.orgId))
      throw new Error('报告身份已变化，请重新核对');
    viewRow.value = value;
    removeHandler.value = () => removeReport(id, token);
  } catch (e: any) {
    if (currentView(token)) {
      viewRow.value = null;
      viewError.value = e?.message || '报告详情读取失败';
    }
  } finally {
    if (currentView(token)) viewLoading.value = false;
  }
}
async function removeReport(id: string, token: number) {
  if (!currentView(token) || viewRow.value?.id !== id || viewRow.value.canRemove !== true || removing.value)
    return false;
  removing.value = true;
  try {
    await reportApi.delete(id);
    if (!currentView(token)) return false;
    message.success('报告已删除，留存原件继续保留');
    viewShow.value = false;
    tableRef.value?.handleRefresh();
  } catch (e: any) {
    if (currentView(token)) {
      clearView();
      viewError.value = e?.message || '删除失败，请重新核对报告';
    }
  } finally {
    if (currentView(token)) removing.value = false;
  }
}
const reportSummary = computed<Record<string, number | string>>(() => {
  try {
    return JSON.parse(viewRow.value?.summary || '{}');
  } catch {
    return {};
  }
});

function duration(minutes?: number | null): string {
  if (minutes == null) return '--';
  if (minutes < 60) return `${minutes} 分钟`;
  return `${Math.floor(minutes / 60)} 小时 ${minutes % 60} 分钟`;
}

const genShow = ref(false);
const genForm = reactive({
  warehouseId: null as string | null,
  cycleType: 'MONTH' as PvReportCycle,
  periodStart: '' as string,
  periodEnd: '' as string,
});
const genStart = ref<number | null>(null);
const genEnd = ref<number | null>(null);
const genRules = {
  cycleType: { required: true, message: '请选择周期', trigger: 'change' },
  periodStart: { required: true, message: '请选择开始日期', trigger: 'change' },
  periodEnd: { required: true, message: '请选择结束日期', trigger: 'change' },
};

const genLoading = ref(false),
  generating = ref(false),
  genError = ref(''),
  genOrgId = ref('');
const currentGen = (token: number) => !disposed && active && genShow.value && token === genGeneration;
async function openGenerate() {
  if (disposed || !active) return;
  const token = ++genGeneration;
  Object.assign(genForm, { warehouseId: null, cycleType: 'MONTH', periodStart: '', periodEnd: '' });
  genStart.value = null;
  genEnd.value = null;
  genOrgId.value = '';
  genError.value = '';
  genShow.value = true;
  genLoading.value = true;
  generating.value = false;
  try {
    const result = unpack(await reportApi.generationContext());
    if (!currentGen(token)) return;
    if (!result?.orgId) throw new Error('未取得当前可办理收藏单位');
    genOrgId.value = result.orgId;
  } catch (e: any) {
    if (currentGen(token)) genError.value = e?.message || '无法核对报告生成资格';
  } finally {
    if (currentGen(token)) genLoading.value = false;
  }
}
function formatDate(v: number | null): string {
  if (!v) return '';
  const d = new Date(v);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

async function submitGenerate() {
  if (!genShow.value || disposed || !active || !genOrgId.value || genLoading.value || generating.value) return;
  if (!genForm.periodStart || !genForm.periodEnd) {
    message.warning('请选择起止日期');
    return;
  }
  const token = genGeneration,
    body = { ...genForm, orgId: genOrgId.value, warehouseId: genForm.warehouseId ?? undefined };
  generating.value = true;
  try {
    await reportApi.generate(body);
    if (!currentGen(token)) return;
    message.success('已生成数据报告');
    genShow.value = false;
    tableRef.value?.handleRefresh();
  } catch (e: any) {
    if (currentGen(token)) {
      genOrgId.value = '';
      genError.value = e?.message || '生成失败，请重新核对资格与范围';
    }
  } finally {
    if (currentGen(token)) generating.value = false;
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
  genShow,
  (value) => {
    if (!value) {
      ++genGeneration;
      genOrgId.value = '';
      genLoading.value = false;
      generating.value = false;
      genError.value = '';
    }
  },
  { flush: 'sync' },
);
function leavePage() {
  active = false;
  ++listGeneration;
  ++genGeneration;
  tableRows.value = [];
  viewShow.value = false;
  clearView();
  genShow.value = false;
  genOrgId.value = '';
  genLoading.value = false;
  generating.value = false;
  genStart.value = null;
  genEnd.value = null;
  Object.assign(genForm, { warehouseId: null, cycleType: 'MONTH', periodStart: '', periodEnd: '' });
  listError.value = '';
  viewError.value = '';
  genError.value = '';
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

<style scoped>
.pv-report-summary {
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
  font-family: inherit;
  font-size: 12px;
}
</style>
