<template>
  <HPageDataTable auto-scroll-x
    ref="tableRef"
    :columns="columns"
    :fetch-data="warehouseEntryApi.page"
    :params="queryParams"
    :is-edit="false"
    :is-delete="false"
    row-key-field="id"
    @add="onAdd"
    @row-view="onView"
  >
    <template #toolbar-extra><OriginalHistoryButton business-type="COLLECTION_WAREHOUSE_ENTRY" /></template>
    <template #queryForm>
      <NFormItem label="检索内容">
        <NInput v-model:value="queryParams.keyword" clearable placeholder="申请编号 / 事由 / 外部人员" />
      </NFormItem>
      <NFormItem label="审批状态">
        <HDictSelect v-model:value="queryParams.flowStatus" type="flow_biz_status" clearable />
      </NFormItem>
      <NFormItem label="计划时间">
        <NDatePicker v-model:value="plannedRange" type="datetimerange" clearable @update:value="syncPlannedRange" />
      </NFormItem>
    </template>

    <WarehouseEntryCaseDrawer ref="caseRef" @saved="refreshTable" />
  </HPageDataTable>
</template>

<script setup lang="ts">
import { useCollectionSearchRecord } from '../composables/useCollectionSearchRecord';
import OriginalHistoryButton from '@/modules/collection/components/OriginalHistoryButton.vue';
import { computed, h, reactive, ref } from 'vue';
import { useAuthStore } from '@/store/modules/auth';
import { NButton, NSpace } from 'naive-ui';
import { warehouseEntryApi } from '../api/warehouseEntry';
import WarehouseEntryCaseDrawer from '../components/WarehouseEntryCaseDrawer.vue';
import { useFlowApproval } from '../../flow/composables/useFlowApproval';
import type { WarehouseEntryApplicationVo } from '../typings/collection';

const auth = useAuthStore();
const canVerifyAttendance = computed(() =>
  !!auth.userInfo?.id && auth.userInfo.enabled === true &&
  auth.hasPermission('ADMIN,COLLECTION_ADMIN,COLLECTION_WAREHOUSE_ENTRY_VERIFY'),
);
const tableRef = ref<any>(null);
const caseRef = ref<any>(null);
const { flowStatusColumn } = useFlowApproval({
  refTable: tableRef,
  drawerRef: caseRef,
  getLabel: (row: WarehouseEntryApplicationVo) => row.applicationNo || row.id || '人员入库申请',
});

const queryParams = reactive({
  keyword: null as string | null,
  flowStatus: null as string | null,
  plannedStart: null as number | null,
  plannedEnd: null as number | null,
});
const plannedRange = ref<[number, number] | null>(null);

function syncPlannedRange(value: [number, number] | null) {
  queryParams.plannedStart = value?.[0] ?? null;
  queryParams.plannedEnd = value?.[1] ?? null;
}

const columns = [
  { ellipsis: { tooltip: true }, resizable: true, title: '申请编号', key: 'applicationNo', width: 160, columnType: 'title', fixed: 'left' as const },
  { ellipsis: { tooltip: true }, resizable: true, title: '申请人', key: 'applicantName', width: 110 },
  { resizable: true, width: 180,
    title: '入库人员',
    key: 'entrantNames',
    minWidth: 180,
    ellipsis: { tooltip: true },
    render: (row: WarehouseEntryApplicationVo) => row.entrantNames?.join('、') || '--',
  },
  { resizable: true, width: 180,
    title: '拟进入库房',
    key: 'warehouseNames',
    minWidth: 180,
    ellipsis: { tooltip: true },
    render: (row: WarehouseEntryApplicationVo) => row.warehouseNames?.join('、') || '--',
  },
  { resizable: true, width: 180, title: '人员入库事由', key: 'purpose', minWidth: 180, ellipsis: { tooltip: true } },
  { title: '计划进入', key: 'plannedStart', width: 180, columnType: 'dateTime' },
  { title: '计划离开', key: 'plannedEnd', width: 180, columnType: 'dateTime' },
  flowStatusColumn,
  { ellipsis: { tooltip: true }, resizable: true,
    title: '核验状态',
    key: 'actualEntryAt',
    width: 120,
    render: (row: WarehouseEntryApplicationVo) =>
      row.actualExitAt
        ? '已离库'
        : row.actualEntryAt
          ? '已入库'
          : row.flowStatus === 'PASS'
            ? row.baseline
              ? '待入库'
              : '旧材料待核实'
            : '--',
  },
  {
    title: '操作',
    key: 'operate',
    type: 'operate',
    width: 220,
    fixed: 'right' as const,
    render: (row: WarehouseEntryApplicationVo) => {
      const actions = [
        h(NButton, { text: true, type: 'primary', size: 'small', onClick: () => onView(row) }, () => '详情'),
      ];
      if (canVerifyAttendance.value && row.flowStatus === 'PASS' && row.baseline && !row.actualEntryAt) {
        actions.push(h(NButton, { text: true, size: 'small', onClick: () => openCheck(row, 'in') }, () => '人员入库核验'));
      }
      if (canVerifyAttendance.value && row.actualEntryAt && !row.actualExitAt) {
        actions.push(h(NButton, { text: true, size: 'small', onClick: () => openCheck(row, 'out') }, () => '离库核验'));
      }
      return h(NSpace, { size: 10 }, () => actions);
    },
  },
];

function onAdd() {
  caseRef.value?.open();
}
function onView(row: WarehouseEntryApplicationVo) {
  caseRef.value?.open(row.id);
}
function openCheck(row: WarehouseEntryApplicationVo, mode: 'in' | 'out') {
  if (canVerifyAttendance.value) caseRef.value?.open(row.id, mode);
}

function refreshTable() {
  tableRef.value?.handleRefresh?.();
  tableRef.value?.handleQuery?.();
}
useCollectionSearchRecord('/collection/warehouse-entry', 'WAREHOUSE_ENTRY', id => caseRef.value?.open(id));
</script>
