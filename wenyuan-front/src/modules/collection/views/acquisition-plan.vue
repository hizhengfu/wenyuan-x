<template>
  <HPageDataTable class="collection-data-page"
    auto-scroll-x
    ref="tableRef"
    :columns="columns"
    :fetch-data="acquisitionPlanApi.page"
    :params="queryParams"
    :is-edit="false"
    :is-delete="false"
    row-key-field="id"
    @add="onAdd"
    @row-view="onView"
  >
    <template #toolbar-extra>
      <OriginalHistoryButton business-type="COLLECTION_ACQUISITION_PLAN">
        <template #trigger="{ open }">
          <CollectionActionMenu label="更多">
            <NButton
              quaternary
              v-auth="'ADMIN,COLLECTION_ADMIN,COLLECTION_ACQUIRE'"
              type="primary"
              @click="offlineDrawerRef?.open()"
              >线下已批准归档</NButton
            >
            <NButton quaternary size="small" @click="open">历史材料</NButton>
          </CollectionActionMenu>
        </template>
      </OriginalHistoryButton>
    </template>
    <template #queryForm>
      <NFormItem label="检索内容">
        <NInput v-model:value="queryParams.keyword" clearable placeholder="计划名称 / 说明" />
      </NFormItem>
      <NFormItem label="计划年度">
        <NDatePicker
          :formatted-value="queryParams.year == null ? null : String(queryParams.year)"
          type="year"
          format="yyyy"
          value-format="yyyy"
          clearable
          placeholder="请选择年度"
          @update:formatted-value="queryParams.year = $event == null ? null : Number($event)"
        />
      </NFormItem>
      <NFormItem label="计划周期">
        <HDictSelect v-model:value="queryParams.planType" type="col_plan_period" clearable placeholder="年度/月度" />
      </NFormItem>
      <NFormItem label="审批状态">
        <HDictSelect v-model:value="queryParams.flowStatus" type="flow_biz_status" clearable placeholder="状态" />
      </NFormItem>
    </template>

    <AcquisitionPlanOfflineDrawer ref="offlineDrawerRef" @saved="refreshTable" />
    <FlowFormDrawer
      ref="flowDrawerRef"
      :fetch-fn="acquisitionPlanApi.flowInfo"
      :create-api="acquisitionPlanApi.save"
      :save-api="saveFlowDraft"
      :submit-api="acquisitionPlanApi.submit"
      :cancel-api="acquisitionPlanApi.cancel"
      @success="refreshTable"
    />
  </HPageDataTable>
</template>

<script setup lang="ts">
import CollectionActionMenu from '../components/CollectionActionMenu.vue';
import '../styles/collection-page.css';
import { useCollectionSearchRecord } from '../composables/useCollectionSearchRecord';
import AcquisitionPlanOfflineDrawer from '../components/AcquisitionPlanOfflineDrawer.vue';
import OriginalHistoryButton from '@/modules/collection/components/OriginalHistoryButton.vue';
import { h, reactive, ref } from 'vue';
import { cloneDeep } from 'lodash-es';
import { NButton, useMessage } from 'naive-ui';
import { useRouter } from 'vue-router';
import { htmlPrint } from '@/components/basic/print/PrintUtils';
import { acquisitionPlanApi } from '../api/acquisitionPlan';
import FlowFormDrawer from '../../flow/components/FlowFormDrawer.vue';
import { useFlowApproval } from '../../flow/composables/useFlowApproval';
import type { AcquisitionPlanVo } from '../typings/collection';
import { formatYuan } from '../utils/moneyYuan';
import {
  exportAcquisitionRegister,
  loadAcquisitionRegisterData,
  printAcquisitionRegister,
} from '../utils/acquisitionRegisterForm';
import { acquisitionPrintTitle } from '../utils/acquisitionPrintShared';

const router = useRouter();
const message = useMessage();
const printing = ref(false);
const registering = ref(false);
const offlineDrawerRef = ref<any>(null);
const tableRef = ref<any>(null);
const flowDrawerRef = ref<any>(null);
const { flowStatusColumn, handleTitleClick } = useFlowApproval({
  refTable: tableRef,
  drawerRef: flowDrawerRef,
  getLabel: (row: AcquisitionPlanVo) => row.title || '征集计划详情',
});

const queryParams = reactive({
  keyword: null as string | null,
  year: null as number | null,
  planType: null as string | null,
  flowStatus: null as string | null,
});

const defaultForm: AcquisitionPlanVo = {
  id: undefined,
  title: undefined,
  planType: 'YEAR',
  year: new Date().getFullYear(),
  month: undefined,
  content: undefined,
  budget: undefined,
  attachmentIds: undefined,
};

const columns = [
  {
    resizable: true,
    width: 200,
    title: '计划名称',
    key: 'title',
    minWidth: 200,
    columnType: 'title',
    fixed: 'left',
    ellipsis: { tooltip: true },
  },
  {
    ellipsis: { tooltip: true },
    resizable: true,
    title: '周期',
    key: 'planType',
    width: 90,
    columnType: 'dict',
    dictType: 'col_plan_period',
  },
  { title: '年度', key: 'year', width: 80 },
  { title: '月份', key: 'month', width: 70, render: (r: AcquisitionPlanVo) => r.month ?? '--' },
  {
    ellipsis: { tooltip: true },
    resizable: true,
    title: '执行进度',
    key: 'acquisitionCount',
    width: 130,
    render: (r: AcquisitionPlanVo) => `登账 ${r.registeredCount ?? 0} / 征集 ${r.acquisitionCount ?? 0}`,
  },
  {
    title: '批准方式',
    key: 'approvalMode',
    width: 130,
    render: (row: AcquisitionPlanVo) =>
      row.offlineApproval ? '线下批准已归档' : row.flowInstanceId ? '线上审批' : '草稿',
  },
  flowStatusColumn,
  { title: '创建时间', key: 'createTime', width: 200, columnType: 'dateTime' },
  {
    title: '操作',
    key: 'operate',
    type: 'operate',
    width: 540,
    fixed: 'right' as const,
    render: (row: AcquisitionPlanVo) => [
      h(NButton, { text: true, type: 'primary', size: 'small', onClick: () => onView(row) }, () => '详情'),
      ...(row.flowStatus === 'DRAFT' && !row.flowInstanceId
        ? [
            h(
              NButton,
              {
                text: true,
                size: 'small',
                style: 'margin-left:10px',
                onClick: () => offlineDrawerRef.value?.open(row.id),
              },
              () => '线下批准归档',
            ),
          ]
        : []),
      h(
        NButton,
        { text: true, size: 'small', style: 'margin-left:10px', onClick: () => goAcquisitions(row) },
        () => '征集清单',
      ),
      h(
        NButton,
        {
          text: true,
          size: 'small',
          style: 'margin-left:10px',
          disabled: printing.value,
          onClick: () => printPlan(row),
        },
        () => '打印/导出',
      ),
      h(
        NButton,
        {
          text: true,
          size: 'small',
          style: 'margin-left:10px',
          disabled: registering.value,
          onClick: () => onRegisterForm(row, 'print'),
        },
        () => '打印征集登记表',
      ),
      h(
        NButton,
        {
          text: true,
          size: 'small',
          style: 'margin-left:10px',
          disabled: registering.value,
          onClick: () => onRegisterForm(row, 'export'),
        },
        () => '导出征集登记表',
      ),
    ],
  },
];

function onAdd() {
  flowDrawerRef.value?.openCreate({
    title: '新增征集计划',
    businessType: 'COLLECTION_ACQUISITION_PLAN',
    formData: cloneDeep(defaultForm),
  });
}

function onView(row: AcquisitionPlanVo) {
  if (row.offlineApproval) {
    void offlineDrawerRef.value?.open(row.id);
    return;
  }
  handleTitleClick(row);
}

function goAcquisitions(row: AcquisitionPlanVo) {
  router.push({ path: '/collection/acquisition', query: { planId: row.id } });
}

async function printPlan(selected: AcquisitionPlanVo) {
  if (printing.value) return;
  if (!selected.id) {
    message.error('计划标识缺失，请刷新列表后重试');
    return;
  }
  printing.value = true;
  try {
    const { data } = await acquisitionPlanApi.get(selected.id);
    if (!data || data.id !== selected.id) throw new Error('计划读取结果不一致');
    const row = data as AcquisitionPlanVo;
    const period = row.planType === 'MONTH' ? `${row.year || '--'}年${row.month || '--'}月` : `${row.year || '--'}年度`;
    htmlPrint(
      `<article class="plan-print"><h1>藏品征集计划</h1><table><tbody>` +
        `<tr><th>计划名称</th><td>${escapeHtml(row.title)}</td><th>计划周期</th><td>${period}</td></tr>` +
        `<tr><th>计划预算</th><td colspan="3">${formatYuan(row.budget)}</td></tr>` +
        `<tr><th>征集方向</th><td colspan="3">${escapeHtml(row.content)}</td></tr>` +
        `<tr><th>执行进度</th><td colspan="3">征集申请 ${row.acquisitionCount || 0} 单，已登账 ${row.registeredCount || 0} 单</td></tr>` +
        `<tr><th>批准方式</th><td colspan="3">${row.offlineApproval ? '线下已批准归档' : row.flowInstanceId ? '线上审批' : '草稿'}</td></tr>${row.offlineApproval ? `<tr><th>批准人或机构</th><td colspan="3">${escapeHtml(row.offlineApproval.approvedBy)}</td></tr><tr><th>批准依据</th><td colspan="3">${escapeHtml(row.offlineApproval.approvalBasis)}</td></tr>` : ''}</tbody></table><div class="signatures">编制人：________　专家评审：________　分管馆长：________</div></article>`,
      acquisitionPrintTitle(row.title || '征集计划'),
      '.plan-print{padding:20px;font-family:serif}.plan-print h1{text-align:center}.plan-print table{width:100%;border-collapse:collapse}.plan-print th,.plan-print td{border:1px solid #333;padding:10px}.plan-print th{width:120px}.signatures{margin-top:48px;line-height:2}',
    );
  } catch {
    message.error('无法取得当前可查阅的计划，请核对权限并刷新列表后重试');
  } finally {
    printing.value = false;
  }
}

async function onRegisterForm(selected: AcquisitionPlanVo, mode: 'print' | 'export') {
  if (registering.value) return;
  if (!selected.id) {
    message.error('计划标识缺失，请刷新列表后重试');
    return;
  }
  registering.value = true;
  try {
    const data = await loadAcquisitionRegisterData(selected.id);
    if (mode === 'print') printAcquisitionRegister(data);
    else exportAcquisitionRegister(data);
  } catch {
    message.error('无法生成征集登记表，请核对权限并刷新列表后重试');
  } finally {
    registering.value = false;
  }
}

function escapeHtml(value: unknown) {
  return String(value ?? '--').replace(
    /[&<>"']/g,
    (char) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[char] || char,
  );
}

function saveFlowDraft(id: string, data: Record<string, any>) {
  return acquisitionPlanApi.save({ ...data, id });
}

function refreshTable() {
  tableRef.value?.handleRefresh();
  tableRef.value?.handleQuery?.();
}
useCollectionSearchRecord('/collection/acquisition-plan', 'ACQUISITION_PLAN', async (id, current) => {
  const response: any = await acquisitionPlanApi.get(id);
  if (current() && response?.data?.id === id) onView(response.data);
});
</script>
