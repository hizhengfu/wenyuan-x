<template>
  <HPageDataTable class="collection-data-page" export-label="导出明细（Excel）" auto-scroll-x
    ref="tableRef"
    :columns="columns"
    :fetch-data="loanApi.page"
    :params="queryParams"
    :is-edit="false"
    :is-delete="false"
    row-key-field="id"
    @add="onAdd"
    @row-view="onView"
  >
    <template #toolbar-extra><OriginalHistoryButton business-type="COLLECTION_LOAN" /></template>
    <template #queryForm>
      <NFormItem label="检索内容">
        <NInput v-model:value="queryParams.keyword" clearable placeholder="展览 / 藏品 / 借入方 / 协议 / 批文" />
      </NFormItem>
      <NFormItem label="借入单位">
        <NInput v-model:value="queryParams.borrower" clearable placeholder="借入单位" />
      </NFormItem>
      <NFormItem label="协议编号">
        <NInput v-model:value="queryParams.protocolNo" clearable placeholder="协议号" />
      </NFormItem>
      <NFormItem label="原登记审批层级">
        <HDictSelect v-model:value="queryParams.approveLevel" type="col_approve_level" clearable placeholder="层级" />
      </NFormItem>
      <NFormItem label="审批状态">
        <HDictSelect v-model:value="queryParams.flowStatus" type="flow_biz_status" clearable placeholder="状态" />
      </NFormItem>
      <NFormItem label="返还状态">
        <HDictSelect v-model:value="queryParams.returned" type="col_returned" clearable placeholder="返还状态" />
      </NFormItem>
      <NFormItem label="经办人员">
        <UserSelect v-model:value="queryParams.operator" clearable placeholder="经办人" />
      </NFormItem>
      <NFormItem label="复核人员">
        <UserSelect v-model:value="queryParams.reviewer" clearable placeholder="复核人" />
      </NFormItem>
      <NFormItem label="档案附件">
        <HDictSelect v-model:value="queryParams.hasAttachment" type="col_presence" clearable placeholder="有无" />
      </NFormItem>
      <NFormItem label="借出日期">
        <NDatePicker
          v-model:value="loanDateRange"
          type="daterange"
          clearable
          @update:value="syncLoanDateRange"
        />
      </NFormItem>
    </template>

    <CollectionLoanCaseDrawer ref="caseRef" @saved="refreshTable"/>

    <CollectionReturnCaseDrawer ref="returnDrawerRef" @saved="refreshTable" />

    <CollectionPrintPreviewModal v-model:show="printShow" :data="printData" />
  </HPageDataTable>
</template>

<script setup lang="ts">
import '../styles/collection-page.css';
import { useCollectionSearchRecord } from '../composables/useCollectionSearchRecord';
import OriginalHistoryButton from '@/modules/collection/components/OriginalHistoryButton.vue';
import { formatDateTime, formatLocalDateValue } from '@/utils';
import { h, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { NButton, useMessage } from 'naive-ui';
import { collectionReturnApi } from '../api/return';
import { loanApi } from '../api/loan';
import UserSelect from '@/modules/sys/components/form/field/UserSelect.vue';
import UserNameText from '../components/UserNameText.vue';
import CollectionPrintPreviewModal from '../components/CollectionPrintPreviewModal.vue';
import CollectionReturnCaseDrawer from '../components/CollectionReturnCaseDrawer.vue';
import CollectionLoanCaseDrawer from '../components/CollectionLoanCaseDrawer.vue';
import FlowFormDrawer from '../../flow/components/FlowFormDrawer.vue';
import { useFlowApproval } from '../../flow/composables/useFlowApproval';
import { loanBorrowerOptions, loanObligationSummary } from '../utils/loanEvidenceFields';
import { presenceText, returnedText } from '../utils/dictRender';
import { collectionSummaryCount, collectionSummaryPreview } from '../utils/collectionSummaryDisplay';
import type { CollectionPrintVo, LoanVo } from '../typings/collection';

const message = useMessage();
const router = useRouter();
const tableRef = ref<any>(null);
const caseRef = ref<any>(null);
const returnDrawerRef = ref<any>(null);
const { flowStatusColumn } = useFlowApproval({
  refTable: tableRef,
  drawerRef: caseRef,
  getLabel: (row: LoanVo) => row.title || row.borrower || row.id || '馆外借用单详情',
});

const queryParams = reactive({
  keyword: null as string | null,
  borrower: null as string | null,
  protocolNo: null as string | null,
  approveLevel: null as string | null,
  flowStatus: null as string | null,
  returned: null as string | null,
  operator: null as string | null,
  reviewer: null as string | null,
  hasAttachment: null as string | null,
  loanDateStart: null as number | null,
  loanDateEnd: null as number | null,
});
const loanDateRange = ref<[number, number] | null>(null);
const printShow = ref(false);
const printData = ref<CollectionPrintVo | null>(null);

function syncLoanDateRange(value: [number, number] | null) {
  queryParams.loanDateStart = value?.[0] ?? null;
  queryParams.loanDateEnd = value?.[1] ?? null;
}

const columns = [
  { resizable: true, width: 180, title: '展览/事由', key: 'title', minWidth: 180, columnType: 'title', fixed: 'left', ellipsis: { tooltip: true } },
  { resizable: true, width: 190, title: '藏品预览', key: 'collectionSummaries', minWidth: 190, ellipsis: { tooltip: true }, render: (r: LoanVo) => collectionSummaryPreview(r.collectionSummaries, r.collectionIds) },
  { title: '件数', key: 'collectionIds', width: 70, render: (r: LoanVo) => collectionSummaryCount(r.collectionSummaries, r.collectionIds) },
  { ellipsis: { tooltip: true }, resizable: true, title: '借入方', key: 'borrower', width: 160 },
  { resizable: true, title: '协议号', key: 'protocolNo', width: 130, ellipsis: { tooltip: true } },
  { ellipsis: { tooltip: true }, resizable: true, title: '借用主体', key: 'dossier', width: 130, render: (r: LoanVo) => loanBorrowerOptions.find(o=>o.value===r.dossier?.borrowerKind)?.label||'待核实' },
  { resizable: true, width: 250, title: '备案与在借监管', key: 'obligations', minWidth: 250, ellipsis: { tooltip: true }, render: (r: LoanVo) => loanObligationSummary(r) },
  { resizable: true, title: '经办人', key: 'operator', width: 120, ellipsis: { tooltip: true }, render: (r: LoanVo) => h(UserNameText, { id: r.operator, name: r.operatorName }) },
  { resizable: true, title: '复核人', key: 'reviewer', width: 120, ellipsis: { tooltip: true }, render: (r: LoanVo) => h(UserNameText, { id: r.reviewer, name: r.reviewerName }) },
  {
    title: '附件',
    key: 'attachmentIds',
    width: 80,
    render: (r: LoanVo) => presenceText(r.attachmentIds),
  },
  flowStatusColumn,
  {
    title: '出库状态',
    key: 'outExecuted',
    width: 100,
    render: (r: LoanVo) => (r.outExecuted ? '已出库' : r.returned ? '历史出库待核实' : r.flowStatus === 'PASS' ? '待出库' : '--'),
  },
  {
    title: '返还进度',
    key: 'returned',
    width: 110,
    render: (r: any) => {
      const total = String(r.collectionIds || '').split(',').filter(Boolean).length;
      const done = (r.returnedCollectionIds || []).length;
      if (r.returned) return returnedText(true);
      return total ? `已验收 ${done}/${total}${r.heldForReviewCollectionIds?.length?`；${r.heldForReviewCollectionIds.length} 件待核查`:""}` : returnedText(r.returned);
    },
  },
  { title: '借出日', key: 'loanDate', width: 110, columnType: 'date' },
  {
    title: '约定归还时间', key: 'returnDate', width: 170,
    render: (r: LoanVo) => {
      const dueAt = r.obligations?.effectiveReturnDueAt;
      return dueAt != null ? formatDateTime(dueAt) : r.returnDate == null ? '--' : formatLocalDateValue(r.returnDate);
    },
  },
  {
    title: '操作', key: 'operate', type: 'operate', width: 320, fixed: 'right' as const,
    render: (row: any) => {
      const btns: any[] = [h(NButton, { text: true, type: 'primary', size: 'small', onClick: () => onView(row) }, () => '详情')];
      if (row.flowStatus === 'PASS' && !row.outExecuted && !row.returned) {
        btns.push(h(NButton, { text: true, type: 'success', size: 'small', style: 'margin-left:10px', onClick: () => openRfidSession(row) }, () => 'RFID 出库'));
        btns.push(h(NButton, { text: true, type: 'warning', size: 'small', style: 'margin-left:10px', onClick: () => onView(row) }, () => '点交与出库'));
      }
      if (row.flowStatus === 'PASS' && row.outExecuted && !row.returned) {
        btns.push(h(NButton, { text: true, type: 'primary', size: 'small', style: 'margin-left:10px', onClick: () => onReturn(row) }, () => '返还'));
      }
      btns.push(h(NButton, { text: true, size: 'small', style: 'margin-left:10px', onClick: () => onPrint(row) }, () => '打印'));
      return btns;
    },
  },
];

function onAdd(){void caseRef.value?.open();}
function onView(row:LoanVo){void caseRef.value?.open(row.id);}
function openRfidSession(row:LoanVo){if(!row.handover){message.warning('请先登记双方逐件点交原件');onView(row);return;}void router.push({path:'/collection/rfid-desk',query:{type:'LOAN_OUT',businessId:row.id}});}
async function onPrint(row: LoanVo) {
  const resp: any = await loanApi.print(row.id!);
  printData.value = resp?.data ?? null;
  printShow.value = true;
}

function saveReturnFlowDraft(id: string, data: Record<string, any>) {
  return collectionReturnApi.save({ ...data, id });
}

function refreshTable() {
  tableRef.value?.handleRefresh();
  tableRef.value?.handleQuery?.();
}
async function onReturn(row: LoanVo) {
  const resp: any = await collectionReturnApi.ensureLoan(row.id!);
  const draftId = resolveDraftId(resp);
  if (!draftId) {
    message.error('返还流程草稿创建失败');
    return;
  }
  message.success('已打开返还流程草稿，请提交审批');
  await returnDrawerRef.value?.open(draftId, `${row.title || row.borrower || '馆外借用单'}返还审批`);
}

function resolveDraftId(resp: any) {
  const data = resp?.data?.data ?? resp?.data ?? resp;
  if (typeof data === 'string') return data;
  return data?.id || data?.businessKey || '';
}
useCollectionSearchRecord('/collection/loan', 'LOAN', id => caseRef.value?.open(id));
</script>
