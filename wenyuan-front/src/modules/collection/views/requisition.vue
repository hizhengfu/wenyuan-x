<template>
  <HPageDataTable class="collection-data-page" export-label="导出明细（Excel）"
    auto-scroll-x
    ref="tableRef"
    :columns="columns"
    :fetch-data="requisitionApi.page"
    :params="queryParams"
    :is-edit="false"
    :is-delete="false"
    row-key-field="id"
    enable-selection
    @add="onAdd"
    @row-view="onView"
  >
    <template #activeBtn="{ checkedTableData }">
      <NTooltip trigger="hover">
        <template #trigger>
          <NButton :disabled="!checkedTableData.length" @click="onExportVouchers(checkedTableData)">导出凭证（PDF/Word）</NButton>
        </template>
        勾选提用单后，批量导出《馆藏藏品提用单》PDF / Word
      </NTooltip>
    </template>
    <template #toolbar-extra
      ><NSpace
        ><OriginalHistoryButton business-type="COLLECTION_REQUISITION" /><NTag
          v-if="queryParams.sourceBizId"
          closable
          @close="
            queryParams.sourceBizId = null;
            queryParams.sourceBizType = null;
            refreshTable();
          "
          >当前来源展览的提用记录</NTag
        ></NSpace
      ></template
    >
    <template #queryForm>
      <NFormItem label="检索内容">
        <NInput v-model:value="queryParams.keyword" clearable placeholder="单号 / 藏品 / 依据 / 部门 / 附件" />
      </NFormItem>
      <NFormItem label="关联藏品">
        <CollectionSelect v-model:value="queryParams.collectionId" clearable />
      </NFormItem>
      <NFormItem label="提用单号">
        <NInput v-model:value="queryParams.documentNo" clearable placeholder="提用单号" />
      </NFormItem>
      <NFormItem label="提用事由">
        <NSelect
          v-model:value="queryParams.purpose"
          :options="requisitionPurposeOptions"
          clearable
          placeholder="提用事由"
        />
      </NFormItem>
      <NFormItem label="提用部门">
        <NInput v-model:value="queryParams.department" clearable placeholder="提用部门" />
      </NFormItem>
      <NFormItem label="审批状态">
        <HDictSelect v-model:value="queryParams.flowStatus" type="flow_biz_status" clearable placeholder="状态" />
      </NFormItem>
      <NFormItem label="归还状态">
        <HDictSelect v-model:value="queryParams.returned" type="col_returned" clearable placeholder="状态" />
      </NFormItem>
      <NFormItem label="档案附件">
        <HDictSelect v-model:value="queryParams.hasAttachment" type="col_presence" clearable placeholder="有无" />
      </NFormItem>
      <NFormItem label="预计归还">
        <NDatePicker
          v-model:value="expectReturnRange"
          type="daterange"
          clearable
          @update:value="syncExpectReturnRange"
        />
      </NFormItem>
      <NFormItem label="创建时间">
        <NDatePicker v-model:value="createRange" type="daterange" clearable @update:value="syncCreateRange" />
      </NFormItem>
    </template>

    <CollectionRequisitionCaseDrawer ref="caseRef" @saved="refreshTable" />

    <CollectionReturnCaseDrawer ref="returnDrawerRef" @saved="refreshTable" />

    <CollectionPrintPreviewModal v-model:show="printShow" :data="printData" />
    <CollectionPrintPreviewModal
      v-model:show="exportShow"
      :type="COLLECTION_VOUCHER_PRINT_TYPES.REQUISITION.type"
      :ids="exportIds"
      :title="voucherExportTitle('REQUISITION', exportIds.length)"
    />
  </HPageDataTable>
</template>

<script setup lang="ts">
import '../styles/collection-page.css';
import { useCollectionSearchRecord } from '../composables/useCollectionSearchRecord';
import OriginalHistoryButton from '@/modules/collection/components/OriginalHistoryButton.vue';
import { h, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { requisitionPurposeOptions } from '../utils/requisitionPurpose';
import { NButton, NTooltip, useMessage } from 'naive-ui';
import { collectionReturnApi } from '../api/return';
import { requisitionApi } from '../api/requisition';
import CollectionSelect from '../components/CollectionSelect.vue';
import CollectionPrintPreviewModal from '../components/CollectionPrintPreviewModal.vue';
import CollectionReturnCaseDrawer from '../components/CollectionReturnCaseDrawer.vue';
import CollectionRequisitionCaseDrawer from '../components/CollectionRequisitionCaseDrawer.vue';
import FlowFormDrawer from '../../flow/components/FlowFormDrawer.vue';
import { useFlowApproval } from '../../flow/composables/useFlowApproval';
import { presenceText, returnedText } from '../utils/dictRender';
import { collectionSummaryCount, collectionSummaryPreview } from '../utils/collectionSummaryDisplay';
import type { CollectionPrintVo, RequisitionVo } from '../typings/collection';
import { COLLECTION_VOUCHER_PRINT_TYPES, voucherExportIds, voucherExportTitle } from '../utils/voucherExport';
import { REQUISITION_HANDOVER_TOO_EARLY, isBeforeExpectedOutAt } from '../utils/requisitionPrerequisite';

const message = useMessage();
const router = useRouter();
const route = useRoute();
const tableRef = ref<any>(null);
const caseRef = ref<any>(null);
const returnDrawerRef = ref<any>(null);
const { flowStatusColumn } = useFlowApproval({
  refTable: tableRef,
  drawerRef: caseRef,
  getLabel: (row: RequisitionVo) => row.documentNo || row.id || '提用单详情',
});

const queryParams = reactive({
  sourceBizType: typeof route.query.sourceBizType === 'string' ? route.query.sourceBizType : null,
  sourceBizId: typeof route.query.sourceBizId === 'string' ? route.query.sourceBizId : null,
  keyword: null as string | null,
  collectionId: null as string | null,
  documentNo: null as string | null,
  purpose: null as string | null,
  department: null as string | null,
  flowStatus: null as string | null,
  returned: null as string | null,
  hasAttachment: null as string | null,
  expectReturnDateStart: null as number | null,
  expectReturnDateEnd: null as number | null,
  createTimeStart: null as number | null,
  createTimeEnd: null as number | null,
});
const expectReturnRange = ref<[number, number] | null>(null);
const createRange = ref<[number, number] | null>(null);
const printShow = ref(false);
const printData = ref<CollectionPrintVo | null>(null);

function syncExpectReturnRange(value: [number, number] | null) {
  queryParams.expectReturnDateStart = value?.[0] ?? null;
  queryParams.expectReturnDateEnd = value?.[1] ?? null;
}

function syncCreateRange(value: [number, number] | null) {
  queryParams.createTimeStart = value?.[0] ?? null;
  queryParams.createTimeEnd = value?.[1] ?? null;
}

const columns = [
  {
    resizable: true,
    title: '单号',
    key: 'documentNo',
    width: 130,
    columnType: 'title',
    fixed: 'left',
    ellipsis: { tooltip: true },
  },
  {
    resizable: true,
    width: 190,
    title: '藏品预览',
    key: 'collectionSummaries',
    minWidth: 190,
    ellipsis: { tooltip: true },
    render: (r: RequisitionVo) => collectionSummaryPreview(r.collectionSummaries, r.collectionIds),
  },
  {
    title: '件数',
    key: 'collectionIds',
    width: 70,
    render: (r: RequisitionVo) => collectionSummaryCount(r.collectionSummaries, r.collectionIds),
  },
  {
    ellipsis: { tooltip: true },
    resizable: true,
    title: '事由',
    key: 'purpose',
    width: 100,
    dictType: 'col_move_reason',
  },
  { resizable: true, title: '部门', key: 'department', width: 120, ellipsis: { tooltip: true } },
  {
    title: '附件',
    key: 'attachmentIds',
    width: 80,
    render: (r: RequisitionVo) => presenceText(r.attachmentIds),
  },
  { ellipsis: { tooltip: true }, resizable: true, title: '经办人', key: 'operatorName', width: 110 },
  { ellipsis: { tooltip: true }, resizable: true, title: '复核人', key: 'reviewerName', width: 110 },
  flowStatusColumn,
  {
    ellipsis: { tooltip: true },
    resizable: true,
    title: '出库状态',
    key: 'outExecuted',
    width: 160,
    render: (r: RequisitionVo) =>
      r.supersededById || r.sourceRetirement
        ? '旧单执行已终止'
        : r.outExecuted
          ? '已出库'
          : r.returned
            ? '旧出库记录待核查'
            : r.flowStatus === 'PASS'
              ? '待出库'
              : '--',
  },
  {
    title: '归还进度',
    key: 'returned',
    width: 110,
    render: (r: any) => {
      const total = String(r.collectionIds || '')
        .split(',')
        .filter(Boolean).length;
      const done = (r.returnedCollectionIds || []).length;
      if (r.returned) return returnedText(true);
      return total ? `已归 ${done}/${total}` : returnedText(r.returned);
    },
  },
  { title: '预计归还', key: 'expectReturnDate', width: 110, columnType: 'date' },
  {
    title: '操作',
    key: 'operate',
    type: 'operate',
    width: 290,
    fixed: 'right' as const,
    render: (row: any) => {
      const btns: any[] = [
        h(NButton, { text: true, type: 'primary', size: 'small', onClick: () => onView(row) }, () => '详情'),
      ];
      if (
        row.flowStatus === 'PASS' &&
        !row.returned &&
        !row.outExecuted &&
        !row.supersededById &&
        !row.sourceRetirement &&
        !row.replacementErrors?.length
      ) {
        btns.push(
          h(
            NButton,
            {
              text: true,
              type: 'success',
              size: 'small',
              style: 'margin-left:10px',
              onClick: () => openRfidSession(row),
            },
            () => 'RFID 出库',
          ),
        );
        const tooEarly = isBeforeExpectedOutAt(row.dossier);
        btns.push(
          h(
            NTooltip,
            { trigger: 'hover', disabled: !tooEarly },
            {
              trigger: () =>
                h(
                  NButton,
                  {
                    text: true,
                    type: 'warning',
                    size: 'small',
                    style: 'margin-left:10px',
                    disabled: tooEarly,
                    onClick: () => {
                      if (tooEarly) {
                        message.warning(REQUISITION_HANDOVER_TOO_EARLY);
                        return;
                      }
                      onView(row);
                    },
                  },
                  () => '点交与出库',
                ),
              default: () => REQUISITION_HANDOVER_TOO_EARLY,
            },
          ),
        );
      }
      if (row.flowStatus === 'PASS' && row.outExecuted && !row.returned) {
        btns.push(
          h(
            NButton,
            { text: true, type: 'primary', size: 'small', style: 'margin-left:10px', onClick: () => onReturn(row) },
            () => '归还',
          ),
        );
      }
      btns.push(
        h(NButton, { text: true, size: 'small', style: 'margin-left:10px', onClick: () => onPrint(row) }, () => '打印'),
      );
      if (row.sourceBizType === 'COLLECTION_EXHIBITION' && row.sourceBizId)
        btns.push(
          h(
            NButton,
            {
              text: true,
              size: 'small',
              style: 'margin-left:10px',
              onClick: () =>
                router.push({
                  path: '/collection/exhibition',
                  query: { recordId: row.sourceBizId, recordType: 'EXHIBITION' },
                }),
            },
            () => '来源展览',
          ),
        );
      return btns;
    },
  },
];

watch(
  () => [route.query.sourceBizType, route.query.sourceBizId],
  () => {
    if (route.path !== '/collection/requisition') return;
    queryParams.sourceBizType = typeof route.query.sourceBizType === 'string' ? route.query.sourceBizType : null;
    queryParams.sourceBizId = typeof route.query.sourceBizId === 'string' ? route.query.sourceBizId : null;
    refreshTable();
  },
  { flush: 'post' },
);
function onAdd() {
  void caseRef.value?.open();
}
function onView(row: RequisitionVo) {
  void caseRef.value?.open(row.id);
}
function openRfidSession(row: RequisitionVo) {
  if (!row.handover) {
    message.warning('请先在点交与出库页面登记逐件点交原件');
    onView(row);
    return;
  }
  void router.push({ path: '/collection/rfid-desk', query: { type: 'REQUISITION_OUT', businessId: row.id } });
}
const exportShow = ref(false);
const exportIds = ref<string[]>([]);
function onExportVouchers(rows: RequisitionVo[]) {
  const ids = voucherExportIds(rows);
  if (!ids.length) {
    message.warning('请先勾选要导出的提用单');
    return;
  }
  exportIds.value = ids;
  exportShow.value = true;
}
async function onPrint(row: RequisitionVo) {
  const resp: any = await requisitionApi.print(row.id!);
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
async function onReturn(row: RequisitionVo) {
  const resp: any = await collectionReturnApi.ensureRequisition(row.id!);
  const draftId = resolveDraftId(resp);
  if (!draftId) {
    message.error('归还流程草稿创建失败');
    return;
  }
  message.success('已打开归还流程草稿，请提交审批');
  await returnDrawerRef.value?.open(draftId, `${row.documentNo || '提用单'}归还审批`);
}

function resolveDraftId(resp: any) {
  const data = resp?.data?.data ?? resp?.data ?? resp;
  if (typeof data === 'string') return data;
  return data?.id || data?.businessKey || '';
}
useCollectionSearchRecord('/collection/requisition', 'REQUISITION', (id) => caseRef.value?.open(id));
</script>
