<template>
  <HPageDataTable auto-scroll-x
    ref="tableRef"
    :columns="columns"
    :fetch-data="collectionApi.ledgerLogPage"
    :params="queryParams"
    :is-add="false"
    :is-edit="false"
    :is-delete="false"
    :show-operate="false"
    row-key-field="id"
    @row-view="onView"
  >
    <template #toolbar-extra>
      <NSpace size="small">
        <NButton size="small" secondary :loading="exporting" @click="exportLedgerLog">导出登记账</NButton>
        <NButton size="small" type="primary" secondary :loading="printing" @click="printLedgerLog">打印登记账</NButton>
      </NSpace>
    </template>

    <template #queryForm>
      <NFormItem label="检索内容">
        <NInput v-model:value="queryParams.keyword" clearable placeholder="登记号 / 名称 / 业务单ID / 字段 / 原值 / 新值 / 原因" />
      </NFormItem>
      <NFormItem label="总登记号">
        <NInput v-model:value="queryParams.totalRegNo" clearable placeholder="总登记号" />
      </NFormItem>
      <NFormItem label="关联单据">
        <NInput v-model:value="queryParams.refBizId" clearable placeholder="精确匹配业务单ID" />
      </NFormItem>
      <NFormItem label="登记动作">
        <HDictSelect v-model:value="queryParams.action" type="col_ledger_action" clearable placeholder="动作" />
      </NFormItem>
      <NFormItem label="更正字段">
        <NInput v-model:value="queryParams.field" clearable placeholder="更正字段" />
      </NFormItem>
      <NFormItem label="记录时间">
        <NDatePicker
          v-model:value="createRange"
          type="daterange"
          clearable
          @update:value="syncCreateRange"
        />
      </NFormItem>
    </template>

    <!-- 详情 -->
    <HPageForm content-only :enable-cancel="false" v-model:show="viewShow" title="登记账详情">
      <NDescriptions v-if="viewRow" label-placement="left" :column="1" bordered size="small">
        <NDescriptionsItem label="总登记号">{{ viewRow.totalRegNo || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="藏品名称">{{ viewRow.collectionName || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="藏品ID">{{ viewRow.collectionId || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="关联业务单ID">{{ viewRow.refBizId || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="动作"><HDictText type="col_ledger_action" :value="viewRow.action" /></NDescriptionsItem>
        <NDescriptionsItem label="字段">{{ ledgerFieldLabel(viewRow.field) }}</NDescriptionsItem>
        <NDescriptionsItem label="原值"><CollectionLedgerLogValue :log="viewRow" side="before" /></NDescriptionsItem>
        <NDescriptionsItem label="新值"><CollectionLedgerLogValue :log="viewRow" side="after" /></NDescriptionsItem>
        <NDescriptionsItem label="原因" content-style="white-space: pre-wrap; overflow-wrap: anywhere;">{{ viewRow.reason || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="记录人"><UserNameText :id="viewRow.createBy" :name="viewRow.createByName" /></NDescriptionsItem>
        <NDescriptionsItem label="记录时间">{{ formatPrintDateTime(viewRow.createTime) || '--' }}</NDescriptionsItem>
      </NDescriptions>
    </HPageForm>
  </HPageDataTable>
</template>

<script setup lang="ts">
import CollectionLedgerLogValue from '../components/CollectionLedgerLogValue.vue';
import { ledgerFieldLabel } from '../utils/ledgerLogDisplay';
import { h, reactive, ref, watch, onDeactivated } from 'vue';
import { useAuthStore } from '@/store';
import { useCollectionSearchRecord } from '../composables/useCollectionSearchRecord';
import { NButton, useMessage } from 'naive-ui';
import { htmlPrint } from '@/components/basic/print/PrintUtils';
import { type as fetchDictType } from '@/modules/sys/api/dict';
import { collectionApi } from '../api/collection';
import type { LedgerLogVo } from '../typings/collection';
import { formatPrintDateTime } from '../utils/print';
import { buildLedgerLogCsv, buildLedgerLogRegisterHtml, type LedgerLogActionLabels } from '../utils/ledgerLogRegister';
import UserNameText from '../components/UserNameText.vue';

const tableRef = ref<any>(null);
const message = useMessage();
const exporting = ref(false);
const printing = ref(false);
const viewShow = ref(false);
const viewRow = ref<LedgerLogVo | null>(null);
const createRange = ref<[number, number] | null>(null);
const queryParams = reactive({
  keyword: null as string | null,
  totalRegNo: null as string | null,
  refBizId: null as string | null,
  action: null as string | null,
  field: null as string | null,
  createTimeStart: null as number | null,
  createTimeEnd: null as number | null,
});

function syncCreateRange(value: [number, number] | null) {
  queryParams.createTimeStart = value?.[0] ?? null;
  queryParams.createTimeEnd = value?.[1] ?? null;
}

const columns = [
  { resizable: true, title: '总登记号', key: 'totalRegNo', width: 130, ellipsis: { tooltip: true } },
  { resizable: true, width: 150, title: '藏品名称', key: 'collectionName', minWidth: 150, columnType: 'title', fixed: 'left', ellipsis: { tooltip: true } },
  { resizable: true, title: '藏品ID', key: 'collectionId', width: 150, ellipsis: { tooltip: true } },
  { resizable: true, title: '关联业务单ID', key: 'refBizId', width: 160, ellipsis: { tooltip: true } },
  { ellipsis: { tooltip: true }, resizable: true, title: '动作', key: 'action', width: 110, dictType: 'col_ledger_action' },
  { resizable: true, title: '字段', key: 'field', width: 130, ellipsis: { tooltip: true }, render: (row: LedgerLogVo) => ledgerFieldLabel(row.field) },
  { resizable: true, width: 260, title: '原值', key: 'before', minWidth: 220, render: (row: LedgerLogVo) => h(CollectionLedgerLogValue, { log: row, side: 'before', compact: true }) },
  { resizable: true, width: 260, title: '新值', key: 'after', minWidth: 220, render: (row: LedgerLogVo) => h(CollectionLedgerLogValue, { log: row, side: 'after', compact: true }) },
  { resizable: true, width: 180, title: '原因', key: 'reason', minWidth: 180, ellipsis: { tooltip: true } },
  { ellipsis: { tooltip: true }, resizable: true, title: '记录人', key: 'createBy', width: 120, render: (row: LedgerLogVo) => h(UserNameText, { id: row.createBy, name: row.createByName }) },
  { title: '记录时间', key: 'createTime', width: 200, render: (row: LedgerLogVo) => formatPrintDateTime(row.createTime) || '--' },
  {
    title: '操作',
    key: 'operate',
    type: 'operate',
    width: 80,
    fixed: 'right' as const,
    render: (row: LedgerLogVo) => h(NButton, { text: true, type: 'primary', size: 'small', onClick: () => onView(row) }, () => '详情'),
  },
];

function onView(row: LedgerLogVo) {
  viewRow.value = row;
  viewShow.value = true;
}

async function loadLedgerActionLabels(): Promise<LedgerLogActionLabels> {
  const resp: any = await fetchDictType('col_ledger_action', true);
  return Object.fromEntries(
    (resp?.data ?? []).map((item: any) => [
      String(item.value ?? ''),
      String(item.label ?? item.name ?? item.value ?? ''),
    ]),
  );
}

async function fetchAllLedgerRows(): Promise<LedgerLogVo[]> {
  const resp: any = await collectionApi.ledgerLogPage({ ...queryParams, pageNum: 1, pageSize: 0 });
  return resp?.data?.list ?? resp?.data ?? [];
}

function downloadCsv(content: string, fileName: string) {
  const blob = new Blob(['\ufeff' + content], { type: 'text/csv;charset=utf-8' });
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = fileName;
  a.click();
  URL.revokeObjectURL(a.href);
}

async function exportLedgerLog() {
  exporting.value = true;
  try {
    const [rows, labels] = await Promise.all([fetchAllLedgerRows(), loadLedgerActionLabels()]);
    downloadCsv(buildLedgerLogCsv(rows, labels), '藏品资料登记账.csv');
    message.success(`已导出 ${rows.length} 条`);
  } finally {
    exporting.value = false;
  }
}

async function printLedgerLog() {
  printing.value = true;
  try {
    const [rows, labels] = await Promise.all([fetchAllLedgerRows(), loadLedgerActionLabels()]);
    if (!rows.length) message.warning('当前查询没有登记账记录');
    htmlPrint(buildLedgerLogRegisterHtml(rows, labels, { ...queryParams }), '藏品资料登记账');
  } finally {
    printing.value = false;
  }
}
const auth = useAuthStore();
const readIdentity = () => `${auth.userInfo?.id}:${auth.identityRevision}:${auth.identitySyncing}`;
function clearSearchDetail() { viewShow.value = false; viewRow.value = null; }
watch(readIdentity, clearSearchDetail, { flush: 'sync' }); onDeactivated(clearSearchDetail);
useCollectionSearchRecord('/collection/ledger-log', 'LEDGER_LOG', async (id, current) => {
  clearSearchDetail(); const identity = readIdentity();
  const response: any = await collectionApi.ledgerLogPage({ recordId: id, pageNum: 1, pageSize: 1 });
  if (!current() || identity !== readIdentity()) return;
  const row = response.data?.list?.[0];
  if (!row || row.id !== id) throw new Error('登记留痕已不存在或当前不可读取');
  onView(row);
});
</script>
