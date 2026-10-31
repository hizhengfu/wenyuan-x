<template>
  <HPageDataTable class="collection-data-page" auto-scroll-x
    ref="tableRef"
    :columns="columns"
    :fetch-data="transferApi.page"
    :params="queryParams"
    :is-edit="false"
    :is-delete="false"
    row-key-field="id"
    @add="onAdd"
    @row-view="onView"
  >
    <template #toolbar-extra>
      <OriginalHistoryButton business-type="COLLECTION_TRANSFER">
        <template #trigger="{ open }">
          <CollectionActionMenu label="更多">
            <NButton quaternary size="small" @click="institutionRef?.open()">终止合并全馆统筹清册</NButton>
            <NButton quaternary size="small" @click="open">历史材料</NButton>
          </CollectionActionMenu>
        </template>
      </OriginalHistoryButton>
    </template>
    <template #queryForm>
      <NFormItem label="检索内容">
        <NInput v-model:value="queryParams.keyword" clearable placeholder="登记号 / 名称 / 单位 / 事由 / 批文" />
      </NFormItem>
      <NFormItem label="关联藏品">
        <CollectionSelect v-model:value="queryParams.collectionId" clearable />
      </NFormItem>
      <NFormItem label="来源单位">
        <NSelect v-model:value="queryParams.fromOrgId" :options="orgOptions" filterable clearable placeholder="源单位" />
      </NFormItem>
      <NFormItem label="目标单位">
        <NSelect v-model:value="queryParams.toOrgId" :options="orgOptions" filterable clearable placeholder="目标单位" />
      </NFormItem>
      <NFormItem label="审批状态">
        <HDictSelect v-model:value="queryParams.flowStatus" type="flow_biz_status" clearable placeholder="状态" />
      </NFormItem>
      <NFormItem label="批文编号">
        <NInput v-model:value="queryParams.approvalDocNo" clearable placeholder="批文号" />
      </NFormItem>
      <NFormItem label="档案附件">
        <HDictSelect v-model:value="queryParams.hasAttachment" type="col_presence" clearable placeholder="有无" />
      </NFormItem>
      <NFormItem label="申请时间">
        <NDatePicker
          v-model:value="createTimeRange"
          type="daterange"
          clearable
          @update:value="syncCreateTimeRange"
        />
      </NFormItem>
    </template>

    <CollectionTransferCaseDrawer ref="caseRef" @saved="refreshTable" />
    <InstitutionSuccessionCaseDrawer ref="institutionRef" @transfer="(id: string) => caseRef?.open(id)" @saved="refreshTable" />

    <CollectionPrintPreviewModal v-model:show="printShow" :data="printData" />
  </HPageDataTable>
</template>

<script setup lang="ts">
import CollectionActionMenu from '../components/CollectionActionMenu.vue';
import '../styles/collection-page.css';
import { useCollectionSearchRecord } from '../composables/useCollectionSearchRecord';
import OriginalHistoryButton from '@/modules/collection/components/OriginalHistoryButton.vue';
import { h, onMounted, reactive, ref } from 'vue';
import { NButton } from 'naive-ui';
import { transferApi } from '../api/transfer';
import CollectionSelect from '../components/CollectionSelect.vue';
import CollectionPrintPreviewModal from '../components/CollectionPrintPreviewModal.vue';
import CollectionTransferCaseDrawer from '../components/CollectionTransferCaseDrawer.vue';
import InstitutionSuccessionCaseDrawer from '../components/InstitutionSuccessionCaseDrawer.vue';
import { useFlowApproval } from '../../flow/composables/useFlowApproval';
import { presenceText } from '../utils/dictRender';
import { transferHandoverProgress } from '../utils/transferStatus';
import type { CollectionPrintVo, CollectionTransferVo } from '../typings/collection';

const tableRef = ref<any>(null);
const caseRef = ref<any>(null);
const institutionRef = ref<any>(null);
const { flowStatusColumn } = useFlowApproval({
  refTable: tableRef,
  drawerRef: caseRef,
  getLabel: (row: CollectionTransferVo) => row.collectionName || row.collectionTotalRegNo || row.id || '跨馆调拨详情',
});

const queryParams = reactive({
  keyword: null as string | null,
  collectionId: null as string | null,
  fromOrgId: null as string | null,
  toOrgId: null as string | null,
  flowStatus: null as string | null,
  approvalDocNo: null as string | null,
  hasAttachment: null as string | null,
  createTimeStart: null as number | null,
  createTimeEnd: null as number | null,
});
const createTimeRange = ref<[number, number] | null>(null);

const orgOptions = ref<{ label: string; value: string }[]>([]);

const printShow = ref(false);
const printData = ref<CollectionPrintVo | null>(null);

function syncCreateTimeRange(value: [number, number] | null) {
  queryParams.createTimeStart = value?.[0] ?? null;
  queryParams.createTimeEnd = value?.[1] ?? null;
}

function orgName(id?: string | null) {
  return orgOptions.value.find((item) => item.value === id)?.label || id || '--';
}

const columns = [
  { resizable: true, title: '总登记号', key: 'collectionTotalRegNo', width: 150, ellipsis: { tooltip: true } },
  { resizable: true, width: 160, title: '藏品名称', key: 'collectionName', minWidth: 160, columnType: 'title', fixed: 'left', ellipsis: { tooltip: true } },
  { resizable: true, title: '源单位', key: 'fromOrgId', width: 150, ellipsis: { tooltip: true }, render: (r: CollectionTransferVo) => orgName(r.fromOrgId) },
  { resizable: true, title: '目标单位', key: 'toOrgId', width: 150, ellipsis: { tooltip: true }, render: (r: CollectionTransferVo) => orgName(r.toOrgId) },
  { resizable: true, title: '批文号', key: 'approvalDocNo', width: 150, ellipsis: { tooltip: true } },
  { ellipsis: { tooltip: true }, resizable: true,
    title: '附件',
    key: 'attachmentIds',
    width: 80,
    render: (r: CollectionTransferVo) => presenceText(r.attachmentIds),
  },
  flowStatusColumn,
  { resizable: true, title: '交接办理', key: 'executedAt', width: 260, ellipsis: { tooltip: true }, render: transferHandoverProgress },
  { title: '创建时间', key: 'createTime', width: 200, columnType: 'dateTime' },
  {
    title: '操作',
    key: 'operate',
    type: 'operate',
    width: 250,
    fixed: 'right' as const,
    render: (row: CollectionTransferVo) => {
      const btns: any[] = [h(NButton, { text: true, type: 'primary', size: 'small', onClick: () => onView(row) }, () => '详情')];
      btns.push(h(NButton, { text: true, size: 'small', style: 'margin-left:10px', onClick: () => onPrint(row) }, () => '打印'));
      return btns;
    },
  },
];

async function loadOrgs() {
  const resp:any=await transferApi.parties();
  orgOptions.value=resp.data || [];
}
onMounted(loadOrgs);

function onAdd() { caseRef.value?.open(); }
function onView(row: CollectionTransferVo) { caseRef.value?.open(row.id); }

async function onPrint(row: CollectionTransferVo) {
  const resp: any = await transferApi.print(row.id!);
  printData.value = resp?.data ?? null;
  printShow.value = true;
}

function refreshTable() {
  tableRef.value?.handleRefresh();
  tableRef.value?.handleQuery?.();
}
useCollectionSearchRecord('/collection/transfer', 'TRANSFER', id => caseRef.value?.open(id));
</script>
