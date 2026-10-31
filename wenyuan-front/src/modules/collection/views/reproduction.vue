<template>
  <HPageDataTable auto-scroll-x
    ref="tableRef"
    :columns="columns"
    :fetch-data="reproductionApi.page"
    :params="queryParams"
    :is-edit="false"
    :is-delete="false"
    row-key-field="id"
    @add="onAdd"
    @row-view="onView"
  >
    <template #toolbar-extra><OriginalHistoryButton business-type="COLLECTION_REPRODUCTION" /></template>
    <template #queryForm>
      <NFormItem label="检索内容">
        <NInput v-model:value="queryParams.keyword" clearable placeholder="登记号 / 名称 / 用途 / 影像ID" />
      </NFormItem>
      <NFormItem label="关联藏品">
        <CollectionSelect v-model:value="queryParams.collectionId" clearable />
      </NFormItem>
      <NFormItem label="复制类型">
        <HDictSelect v-model:value="queryParams.repType" type="col_rep_type" clearable placeholder="类型" />
      </NFormItem>
      <NFormItem label="审批状态">
        <HDictSelect v-model:value="queryParams.flowStatus" type="flow_biz_status" clearable placeholder="状态" />
      </NFormItem>
      <NFormItem label="复制用途">
        <NInput v-model:value="queryParams.purpose" clearable placeholder="用途" />
      </NFormItem>
      <NFormItem label="藏品影像">
        <HDictSelect v-model:value="queryParams.hasImage" type="col_presence" clearable placeholder="有无" />
      </NFormItem>
      <NFormItem label="采集日期">
        <NDatePicker
          v-model:value="repDateRange"
          type="daterange"
          clearable
          @update:value="syncRepDateRange"
        />
      </NFormItem>
    </template>

    <FlowFormDrawer
      ref="flowDrawerRef"
      :fetch-fn="reproductionApi.flow"
      :create-api="reproductionApi.create"
      :save-api="saveFlowDraft"
      :submit-api="reproductionApi.submit"
      :cancel-api="reproductionApi.cancel"
      @success="refreshTable"
    />

    <ReproductionComplianceDrawer ref="complianceRef" @saved="refreshTable" />
    <DigitalAcquisitionDrawer ref="digitalRef" @saved="refreshTable" />
    <CollectionPrintPreviewModal v-model:show="printShow" :data="printData" />
  </HPageDataTable>
</template>

<script setup lang="ts">
import { useCollectionSearchRecord } from '../composables/useCollectionSearchRecord';
import OriginalHistoryButton from '@/modules/collection/components/OriginalHistoryButton.vue';
import { h, reactive, ref } from 'vue';
import { cloneDeep } from 'lodash-es';
import { NButton } from 'naive-ui';
import { reproductionApi } from '../api/reproduction';
import CollectionSelect from '../components/CollectionSelect.vue';
import ReproductionComplianceDrawer from '../components/ReproductionComplianceDrawer.vue';
import DigitalAcquisitionDrawer from '../components/DigitalAcquisitionDrawer.vue';
import CollectionPrintPreviewModal from '../components/CollectionPrintPreviewModal.vue';
import FlowFormDrawer from '../../flow/components/FlowFormDrawer.vue';
import { useFlowApproval } from '../../flow/composables/useFlowApproval';
import { presenceText } from '../utils/dictRender';
import type { CollectionPrintVo, ReproductionVo } from '../typings/collection';

const tableRef = ref<any>(null);
const complianceRef = ref<any>();
const digitalRef = ref<any>();
const flowDrawerRef = ref<any>(null);
const { flowStatusColumn, handleTitleClick } = useFlowApproval({
  refTable: tableRef,
  drawerRef: flowDrawerRef,
  getLabel: (row: ReproductionVo) => row.collectionName || row.collectionTotalRegNo || row.id || '复制/拓印记录详情',
});

const queryParams = reactive({
  keyword: null as string | null,
  collectionId: null as string | null,
  repType: null as string | null,
  flowStatus: null as string | null,
  purpose: null as string | null,
  hasImage: null as string | null,
  repDateStart: null as number | null,
  repDateEnd: null as number | null,
});
const repDateRange = ref<[number, number] | null>(null);

const defaultForm: ReproductionVo = {
  id: undefined,
  collectionId: undefined,
  repType: undefined,
  repDate: undefined,
  purpose: undefined,
  images: undefined,
};

const printShow = ref(false);
const printData = ref<CollectionPrintVo | null>(null);


function syncRepDateRange(value: [number, number] | null) {
  queryParams.repDateStart = value?.[0] ?? null;
  queryParams.repDateEnd = value?.[1] ?? null;
}

function splitFileIds(value?: string | null) {
  return (value || '')
    .split(/[,，;\n]/)
    .map((item) => item.trim())
    .filter(Boolean);
}

function hasFiles(value?: string | null) {
  return splitFileIds(value).length > 0;
}

const columns = [
  { resizable: true, title: '总登记号', key: 'collectionTotalRegNo', width: 150, ellipsis: { tooltip: true } },
  { resizable: true, width: 160, title: '藏品名称', key: 'collectionName', minWidth: 160, columnType: 'title', fixed: 'left', ellipsis: { tooltip: true } },
  { ellipsis: { tooltip: true }, resizable: true, title: '类型', key: 'repType', width: 110, dictType: 'col_rep_type' },
  flowStatusColumn,
  { resizable: true, width: 160, title: '用途', key: 'purpose', minWidth: 160, ellipsis: { tooltip: true } },
  {
    title: '影像',
    key: 'images',
    width: 80,
    render: (r: ReproductionVo) => presenceText(hasFiles(r.images)),
  },
  { title: '日期', key: 'repDate', width: 120, columnType: 'date' },
  { title: '创建时间', key: 'createTime', width: 200, columnType: 'dateTime' },
  {
    title: '操作',
    key: 'operate',
    type: 'operate',
    width: 250,
    fixed: 'right' as const,
    render: (row: ReproductionVo) => {
      const btns = [
        h(NButton, { text: true, type: 'primary', size: 'small', onClick: () => onView(row) }, () => '详情'),
        h(NButton, { text: true, size: 'small', style: 'margin-left:10px', onClick: () => onPrint(row) }, () => '打印'),
      ];
      if (row.repType === 'COPY' || row.repType === 'RUBBING') btns.push(h(NButton, { text: true, type: 'primary', size: 'small', style: 'margin-left:10px', onClick: () => complianceRef.value?.open(row.id) }, () => '方案与实施'));
      if (row.repType === 'PHOTO' || row.repType === 'SCAN_3D') btns.push(h(NButton, { text: true, type: 'primary', size: 'small', style: 'margin-left:10px', onClick: () => digitalRef.value?.open(row.id) }, () => '数字采集与资源'));
      return btns;
    },
  },
];

function onAdd() {
  flowDrawerRef.value?.openCreate({
    title: '新增复制拓印或数字采集',
    businessType: 'COLLECTION_REPRODUCTION',
    formData: cloneDeep(defaultForm),
  });
}

function onView(row: ReproductionVo) {
  handleTitleClick(row);
}

async function onPrint(row: ReproductionVo) {
  const resp: any = await reproductionApi.print(row.id!);
  printData.value = resp?.data ?? null;
  printShow.value = true;
}

function saveFlowDraft(id: string, data: Record<string, any>) {
  return reproductionApi.create({ ...data, id });
}

function refreshTable() {
  tableRef.value?.handleRefresh?.();
  tableRef.value?.handleQuery?.();
}
useCollectionSearchRecord('/collection/reproduction', 'REPRODUCTION', id => flowDrawerRef.value?.open(id));
</script>
