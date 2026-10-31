<template>
  <HPageDataTable class="collection-data-page" export-label="导出明细（Excel）" auto-scroll-x
    ref="tableRef"
    :columns="columns"
    :fetch-data="collectionReturnApi.page"
    :params="queryParams"
    :is-add="false"
    :is-edit="false"
    :is-delete="false"
    row-key-field="id"
    @row-view="onView"
  >
    <template #toolbar-extra><OriginalHistoryButton business-type="COLLECTION_RETURN" /></template>
    <template #queryForm>
      <NFormItem label="检索内容">
        <NInput v-model:value="queryParams.keyword" clearable placeholder="来源单号 / 藏品 / 依据 / 人员" />
      </NFormItem>
      <NFormItem label="来源类型">
        <NSelect v-model:value="queryParams.sourceBizType" :options="sourceTypeOptions" clearable placeholder="来源类型" />
      </NFormItem>
      <NFormItem label="审批状态">
        <HDictSelect v-model:value="queryParams.flowStatus" type="flow_biz_status" clearable placeholder="状态" />
      </NFormItem>
      <NFormItem label="现场归还">
        <NSelect v-model:value="queryParams.executionState" :options="executionStateOptions" clearable placeholder="执行状态" />
      </NFormItem>
      <NFormItem label="档案附件">
        <HDictSelect v-model:value="queryParams.hasAttachment" type="col_presence" clearable placeholder="有无" />
      </NFormItem>
      <NFormItem label="预计归还">
        <NDatePicker
          v-model:value="expectedReturnRange"
          type="daterange"
          clearable
          @update:value="syncExpectedReturnRange"
        />
      </NFormItem>
    </template>

    <CollectionReturnCaseDrawer ref="flowDrawerRef" @saved="refreshTable" />
  </HPageDataTable>
</template>

<script setup lang="ts">
import '../styles/collection-page.css';
import { useCollectionSearchRecord } from '../composables/useCollectionSearchRecord';
import OriginalHistoryButton from '@/modules/collection/components/OriginalHistoryButton.vue';
import { h, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { NButton } from 'naive-ui';
import { collectionReturnApi } from '../api/return';
import CollectionReturnCaseDrawer from '../components/CollectionReturnCaseDrawer.vue';
import {returnCaseStatus} from '../utils/returnEvidenceFields';
import { useFlowApproval } from '../../flow/composables/useFlowApproval';
import { presenceText } from '../utils/dictRender';
import { collectionSummaryCount, collectionSummaryPreview } from '../utils/collectionSummaryDisplay';
import type { CollectionReturnVo } from '../typings/collection';

const tableRef = ref<any>(null);
const router = useRouter();
const flowDrawerRef = ref<any>(null);
const { flowStatusColumn, handleTitleClick } = useFlowApproval({
  refTable: tableRef,
  drawerRef: flowDrawerRef,
  getLabel: (row: CollectionReturnVo) => row.sourceDocumentNo || row.id || '归还单详情',
});

const queryParams = reactive({
  keyword: null as string | null,
  sourceBizType: null as string | null,
  flowStatus: null as string | null,
  executionState: null as string | null,
  hasAttachment: null as string | null,
  expectedReturnDateStart: null as number | null,
  expectedReturnDateEnd: null as number | null,
});
const expectedReturnRange = ref<[number, number] | null>(null);
const sourceTypeOptions = [
  { label: '提用', value: 'COLLECTION_REQUISITION' },
  { label: '借展', value: 'COLLECTION_LOAN' },
];
const executionStateOptions = [
  { label: '待现场归还', value: 'PENDING' },
  { label: '已接收（含待核查）', value: 'EXECUTED' },
];

function syncExpectedReturnRange(value: [number, number] | null) {
  queryParams.expectedReturnDateStart = value?.[0] ?? null;
  queryParams.expectedReturnDateEnd = value?.[1] ?? null;
}

const columns = [
  { resizable: true, width: 150, title: '来源单号', key: 'sourceDocumentNo', minWidth: 150, columnType: 'title', fixed: 'left', ellipsis: { tooltip: true } },
  { ellipsis: { tooltip: true }, resizable: true, title: '来源', key: 'sourceBizType', width: 90, render: (r: CollectionReturnVo) => sourceTypeText(r.sourceBizType) },
  { resizable: true, width: 190, title: '藏品预览', key: 'collectionSummaries', minWidth: 190, ellipsis: { tooltip: true }, render: (r: CollectionReturnVo) => collectionSummaryPreview(r.collectionSummaries, r.collectionIds) },
  { title: '件数', key: 'collectionIds', width: 70, render: (r: CollectionReturnVo) => collectionSummaryCount(r.collectionSummaries, r.collectionIds) },
  { ellipsis: { tooltip: true }, resizable: true, title: '事由', key: 'reason', width: 100, dictType: 'col_move_reason' },
  { resizable: true, title: '部门/单位', key: 'department', width: 130, ellipsis: { tooltip: true } },
  { ellipsis: { tooltip: true }, resizable: true, title: '经办人', key: 'operatorName', width: 110 },
  { ellipsis: { tooltip: true }, resizable: true, title: '复核人', key: 'reviewerName', width: 110 },
  {
    title: '附件',
    key: 'attachmentIds',
    width: 80,
    render: (r: CollectionReturnVo) => presenceText(r.attachmentIds),
  },
  flowStatusColumn,
  { ellipsis: { tooltip: true }, resizable: true, title: '现场归还', key: 'executionState', width: 110, render: (r: CollectionReturnVo) => returnCaseStatus(r) },
  { title: '预计归还', key: 'expectedReturnDate', width: 110, columnType: 'date' },
  { title: '实际归还', key: 'returnDate', width: 110, columnType: 'date' },
  {
    title: '操作',
    key: 'operate',
    type: 'operate',
    width: 180,
    fixed: 'right' as const,
    render: (row: CollectionReturnVo) => {
      const buttons = [h(NButton, { text: true, type: 'primary', size: 'small', onClick: () => onView(row) }, () => '详情')];
      if (row.flowStatus === 'PASS' && row.executionState === 'PENDING' && row.inspection) {
        buttons.push(h(NButton, { text: true, type: 'success', size: 'small', style: 'margin-left:10px', onClick: () => openRfidSession(row) }, () => 'RFID 归还'));
      }
      return buttons;
    },
  },
];

function sourceTypeText(value?: string | null) {
  if (value === 'COLLECTION_REQUISITION') return '提用';
  if (value === 'COLLECTION_LOAN') return '借展';
  return value || '--';
}

function onView(row: CollectionReturnVo) {
  void flowDrawerRef.value?.open(row.id);
}
function openRfidSession(row: CollectionReturnVo) {
  void router.push({ path: '/collection/rfid-desk', query: { type: 'RETURN_IN', businessId: row.id } });
}

function saveFlowDraft(id: string, data: Record<string, any>) {
  return collectionReturnApi.save({ ...data, id });
}

function refreshTable() {
  tableRef.value?.handleRefresh();
  tableRef.value?.handleQuery?.();
}
useCollectionSearchRecord('/collection/return', 'RETURN', id => flowDrawerRef.value?.open(id));
</script>
