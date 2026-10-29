<template>
  <HPageDataTable
    auto-scroll-x
    ref="tableRef"
    :columns="columns"
    :fetch-data="exhibitionApi.page"
    :params="queryParams"
    :is-edit="false"
    row-key-field="id"
    @add="onAdd"
    @row-view="onView"
  >
    <template #toolbar-extra><OriginalHistoryButton business-type="COLLECTION_EXHIBITION" /></template>
    <template #queryForm>
      <NFormItem label="检索内容">
        <NInput v-model:value="queryParams.keyword" clearable placeholder="展览 / 藏品 / 展馆 / 单位 / 备注" />
      </NFormItem>
      <NFormItem label="展览编号">
        <NInput v-model:value="queryParams.exhibitionNo" clearable placeholder="编号" />
      </NFormItem>
      <NFormItem label="展览场馆">
        <WarehouseTreeSelect
          v-model:value="queryParams.venueId"
          scene-type="EXHIBITION"
          :allowed-types="['EXHIBITION_HALL']"
          placeholder="请选择展馆/展厅"
        />
      </NFormItem>
      <NFormItem label="举办单位">
        <NInput v-model:value="queryParams.organizer" clearable placeholder="单位" />
      </NFormItem>
      <NFormItem label="展览状态">
        <HDictSelect v-model:value="queryParams.state" type="col_process_state" clearable placeholder="展览状态" />
      </NFormItem>
      <NFormItem label="审批状态">
        <HDictSelect v-model:value="queryParams.flowStatus" type="flow_biz_status" clearable placeholder="审批状态" />
      </NFormItem>
      <NFormItem label="封面影像">
        <HDictSelect v-model:value="queryParams.hasImage" type="col_presence" clearable placeholder="有无" />
      </NFormItem>
      <NFormItem label="档案附件">
        <HDictSelect v-model:value="queryParams.hasAttachment" type="col_presence" clearable placeholder="有无" />
      </NFormItem>
      <NFormItem label="开展日期">
        <NDatePicker v-model:value="startDateRange" type="daterange" clearable @update:value="syncStartDateRange" />
      </NFormItem>
      <NFormItem label="结束日期">
        <NDatePicker v-model:value="endDateRange" type="daterange" clearable @update:value="syncEndDateRange" />
      </NFormItem>
    </template>

    <FlowFormDrawer
      ref="flowDrawerRef"
      :fetch-fn="exhibitionApi.flow"
      :create-api="exhibitionApi.create"
      :save-api="saveFlowDraft"
      :submit-api="exhibitionApi.submit"
      :cancel-api="exhibitionApi.cancel"
      :void-api="exhibitionApi.voidRecord"
      :delete-api="exhibitionApi.delete"
      @success="refreshTable"
    />

    <CollectionPrintPreviewModal v-model:show="printShow" :data="printData" />
  </HPageDataTable>
</template>

<script setup lang="ts">
import { useCollectionSearchRecord } from '../composables/useCollectionSearchRecord';
import OriginalHistoryButton from '@/modules/collection/components/OriginalHistoryButton.vue';
import { h, reactive, ref } from 'vue';
import { NButton } from 'naive-ui';
import { useRouter } from 'vue-router';
import { cloneDeep } from 'lodash-es';
import { exhibitionApi } from '../api/exhibition';
import CollectionSelect from '../components/CollectionSelect.vue';
import CollectionPrintPreviewModal from '../components/CollectionPrintPreviewModal.vue';
import FlowFormDrawer from '../../flow/components/FlowFormDrawer.vue';
import { useFlowApproval } from '../../flow/composables/useFlowApproval';
import { presenceText } from '../utils/dictRender';
import { collectionSummaryCount, collectionSummaryPreview } from '../utils/collectionSummaryDisplay';
import WarehouseTreeSelect from '../components/WarehouseTreeSelect.vue';
import type { CollectionPrintVo, ExhibitionVo } from '../typings/collection';

const router = useRouter();
const tableRef = ref<any>(null);
const flowDrawerRef = ref<any>(null);
const { flowStatusColumn, handleTitleClick } = useFlowApproval({
  refTable: tableRef,
  drawerRef: flowDrawerRef,
  getLabel: (row: ExhibitionVo) => row.title || row.id || '展览详情',
});
const queryParams = reactive({
  keyword: null as string | null,
  exhibitionNo: null as string | null,
  venueId: null as string | null,
  organizer: null as string | null,
  state: null as string | null,
  flowStatus: null as string | null,
  hasImage: null as string | null,
  hasAttachment: null as string | null,
  startDateStart: null as number | null,
  startDateEnd: null as number | null,
  endDateStart: null as number | null,
  endDateEnd: null as number | null,
});
const startDateRange = ref<[number, number] | null>(null);
const endDateRange = ref<[number, number] | null>(null);

type ExhibitionForm = ExhibitionVo & { ids?: string[] };
const defaultForm: ExhibitionForm = { ids: [], state: 'DRAFT' };
const printShow = ref(false);
const printData = ref<CollectionPrintVo | null>(null);

function syncStartDateRange(value: [number, number] | null) {
  queryParams.startDateStart = value?.[0] ?? null;
  queryParams.startDateEnd = value?.[1] ?? null;
}

function syncEndDateRange(value: [number, number] | null) {
  queryParams.endDateStart = value?.[0] ?? null;
  queryParams.endDateEnd = value?.[1] ?? null;
}

function hasText(value?: string | null) {
  return Boolean(value && value.trim());
}

const columns = [
  {
    resizable: true,
    width: 180,
    title: '展览名称',
    key: 'title',
    minWidth: 180,
    columnType: 'title',
    fixed: 'left',
    ellipsis: { tooltip: true },
  },
  { resizable: true, title: '编号', key: 'exhibitionNo', width: 130, ellipsis: { tooltip: true } },
  { resizable: true, title: '展馆', key: 'venue', width: 150, ellipsis: { tooltip: true } },
  { resizable: true, title: '主办/承办', key: 'organizer', width: 150, ellipsis: { tooltip: true } },
  {
    resizable: true,
    width: 190,
    title: '藏品预览',
    key: 'collectionSummaries',
    minWidth: 190,
    ellipsis: { tooltip: true },
    render: (row: ExhibitionVo) => collectionSummaryPreview(row.collectionSummaries, row.collectionIds),
  },
  {
    title: '参展数',
    key: 'collectionIds',
    width: 90,
    render: (row: ExhibitionVo) => collectionSummaryCount(row.collectionSummaries, row.collectionIds),
  },
  {
    resizable: true,
    width: 180,
    title: '展柜分配',
    key: 'placements',
    minWidth: 180,
    ellipsis: { tooltip: true },
    render: (row: ExhibitionVo) => showcaseSummary(row),
  },
  {
    ellipsis: { tooltip: true },
    resizable: true,
    title: '状态',
    key: 'state',
    width: 100,
    dictType: 'col_process_state',
  },
  flowStatusColumn,
  { title: '封面', key: 'coverImage', width: 80, render: (row: ExhibitionVo) => presenceText(hasText(row.coverImage)) },
  {
    title: '附件',
    key: 'attachmentIds',
    width: 80,
    render: (row: ExhibitionVo) => presenceText(hasText(row.attachmentIds)),
  },
  { title: '开展', key: 'startDate', width: 110, columnType: 'date' },
  { title: '结束', key: 'endDate', width: 110, columnType: 'date' },
  {
    title: '操作',
    key: 'operate',
    type: 'operate',
    width: 160,
    fixed: 'right' as const,
    render: (row: ExhibitionVo) => {
      const btns = [
        h(NButton, { text: true, type: 'primary', size: 'small', onClick: () => onView(row) }, () => '详情'),
        h(
          NButton,
          {
            text: true,
            size: 'small',
            style: 'margin-left:10px',
            onClick: () =>
              router.push({
                path: '/collection/requisition',
                query: { sourceBizType: 'COLLECTION_EXHIBITION', sourceBizId: row.id },
              }),
          },
          () => '关联提用',
        ),
        h(NButton, { text: true, size: 'small', style: 'margin-left:10px', onClick: () => onPrint(row) }, () => '打印'),
      ];
      return btns;
    },
  },
];

function showcaseSummary(row: ExhibitionVo) {
  const groups = new Map<string, { name: string; count: number }>();
  (row.placements || []).forEach((item) => {
    if (!item.locationId) return;
    const current = groups.get(item.locationId);
    groups.set(item.locationId, {
      name: item.locationName || item.locationId,
      count: (current?.count || 0) + 1,
    });
  });
  if (!groups.size) return '--';
  return Array.from(groups.values())
    .map((item) => `${item.name}（${item.count}件）`)
    .join('、');
}

function onAdd() {
  flowDrawerRef.value?.openCreate({
    title: '新增展览',
    businessType: 'COLLECTION_EXHIBITION',
    formData: { state: 'DRAFT' },
  });
}

function onView(row: ExhibitionVo) {
  handleTitleClick(row);
}

async function onPrint(row: ExhibitionVo) {
  if (!row.id) return;
  const resp: any = await exhibitionApi.print(row.id);
  printData.value = resp?.data ?? null;
  printShow.value = true;
}

function saveFlowDraft(id: string, data: Record<string, any>) {
  return exhibitionApi.update(id, data as ExhibitionVo);
}

function refreshTable() {
  tableRef.value?.handleRefresh?.();
}
useCollectionSearchRecord('/collection/exhibition', 'EXHIBITION', (id) => flowDrawerRef.value?.open(id));
</script>

<style scoped>
.collection-summary-list {
  display: grid;
  gap: 4px;
}

.collection-summary-item {
  line-height: 1.5;
}
</style>
