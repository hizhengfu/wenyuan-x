<template>
  <HPageDataTable class="collection-data-page" export-label="导出明细（Excel）" auto-scroll-x
    ref="tableRef"
    :columns="columns"
    :fetch-data="deregistrationApi.page"
    :params="queryParams"
    :is-add="canDeregister"
    :is-edit="false"
    :is-delete="false"
    row-key-field="id"
    @add="onAdd"
    @row-view="onView"
  >
    <template #toolbar-extra><OriginalHistoryButton business-type="COLLECTION_DEREGISTER" /></template>
    <template #queryForm>
      <NFormItem label="检索内容">
        <NInput v-model:value="queryParams.keyword" clearable placeholder="登记号 / 名称 / 说明 / 行政文号 / 附件" />
      </NFormItem>
      <NFormItem label="关联藏品">
        <CollectionSelect v-model:value="queryParams.collectionId" clearable />
      </NFormItem>
      <NFormItem label="退出原因">
        <HDictSelect v-model:value="queryParams.reason" type="col_dereg_reason" clearable placeholder="原因" />
      </NFormItem>
      <NFormItem label="批准层级">
        <HDictSelect v-model:value="queryParams.approveLevel" type="col_approve_level" clearable placeholder="层级" />
      </NFormItem>
      <NFormItem label="审批状态">
        <HDictSelect v-model:value="queryParams.flowStatus" type="flow_biz_status" clearable placeholder="状态" />
      </NFormItem>
      <NFormItem label="行政文号">
        <NInput v-model:value="queryParams.approvalDocNo" clearable placeholder="行政文号" />
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

    <FlowFormDrawer
      ref="flowDrawerRef"
      :fetch-fn="deregistrationApi.flow"
      :cancel-api="deregistrationApi.cancel"
      :void-api="deregistrationApi.voidRecord"
      @success="refreshTable"
    />

    <DeregistrationCaseDrawer ref="caseRef" @saved="refreshTable" />
    <CollectionPrintPreviewModal v-model:show="printShow" :data="printData" />
  </HPageDataTable>
</template>

<script setup lang="ts">
import '../styles/collection-page.css';
import { useCollectionSearchRecord } from '../composables/useCollectionSearchRecord';
import OriginalHistoryButton from '@/modules/collection/components/OriginalHistoryButton.vue';
import { computed, h, reactive, ref } from 'vue';
import { useAuthStore } from '@/store';
import { DEREGISTRATION_WRITE_CODES } from '../utils/deregistrationPermission';
import { NButton } from 'naive-ui';
import { deregistrationApi } from '../api/deregistration';
import CollectionSelect from '../components/CollectionSelect.vue';
import DeregistrationCaseDrawer from '../components/DeregistrationCaseDrawer.vue';
import CollectionPrintPreviewModal from '../components/CollectionPrintPreviewModal.vue';
import FlowFormDrawer from '../../flow/components/FlowFormDrawer.vue';
import { useFlowApproval } from '../../flow/composables/useFlowApproval';
import { presenceText } from '../utils/dictRender';
import type { CollectionPrintVo, DeregistrationVo } from '../typings/collection';

const auth = useAuthStore();
// PageDataTable 未传 permission 时新增按钮一律放行，这里按后端写权限码绑定 is-add。
const canDeregister = computed(() => auth.hasPermission(DEREGISTRATION_WRITE_CODES));
const tableRef = ref<any>(null);
const caseRef = ref<any>(null);
const flowDrawerRef = ref<any>(null);
const { flowStatusColumn } = useFlowApproval({
  refTable: tableRef,
  drawerRef: flowDrawerRef,
  getLabel: (row: DeregistrationVo) => row.collectionName || row.collectionTotalRegNo || row.id || '退出馆藏申请详情',
});

const queryParams = reactive({
  keyword: null as string | null,
  collectionId: null as string | null,
  reason: null as string | null,
  approveLevel: null as string | null,
  flowStatus: null as string | null,
  approvalDocNo: null as string | null,
  hasAttachment: null as string | null,
  createTimeStart: null as number | null,
  createTimeEnd: null as number | null,
});
const createTimeRange = ref<[number, number] | null>(null);


const printShow = ref(false);
const printData = ref<CollectionPrintVo | null>(null);

function syncCreateTimeRange(value: [number, number] | null) {
  queryParams.createTimeStart = value?.[0] ?? null;
  queryParams.createTimeEnd = value?.[1] ?? null;
}

const columns = [
  { resizable: true, title: '总登记号', key: 'collectionTotalRegNo', width: 150, ellipsis: { tooltip: true } },
  { resizable: true, width: 160, title: '藏品名称', key: 'collectionName', minWidth: 160, columnType: 'title', fixed: 'left', ellipsis: { tooltip: true } },
  { ellipsis: { tooltip: true }, resizable: true, title: '退出情形', key: 'reason', width: 110, dictType: 'col_dereg_reason' },
  { ellipsis: { tooltip: true }, resizable: true, title: '批准层级', key: 'approveLevel', width: 110, dictType: 'col_approve_level' },
  { resizable: true, title: '行政文号', key: 'approvalDocNo', width: 150, ellipsis: { tooltip: true } },
  {
    title: '附件',
    key: 'attachmentIds',
    width: 80,
    render: (r: DeregistrationVo) => presenceText(r.attachmentIds),
  },
  flowStatusColumn,
  { ellipsis: { tooltip: true }, resizable: true,title:'退出办理',key:'executedAt',width:160,render:(row:any)=>row.currentNoticeComplete?'当前结果公告已留档':row.executedAt?'已退出，待结果公告':row.vetoUntil?'专家否决，五年禁重启':row.flowStatus==='PASS'?'已批准，待签署执行':'办理中'},
  { title: '申请时间', key: 'createTime', width: 200, columnType: 'dateTime' },
  {
    title: '操作',
    key: 'operate',
    type: 'operate',
    width: 240,
    fixed: 'right' as const,
    render: (row: DeregistrationVo) => {
      const btns: any[] = [h(NButton, { text: true, type: 'primary', size: 'small', onClick: () => onView(row) }, () => '详情')];
      btns.push(h(NButton, { text: true, size: 'small', style: 'margin-left:10px', onClick: () => onPrint(row) }, () => '打印'));
      return btns;
    },
  },
];


function onAdd() { if (canDeregister.value) caseRef.value?.open(); }
function onView(row: DeregistrationVo) { caseRef.value?.open(row.id); }


function refreshTable() {
  tableRef.value?.handleRefresh();
  tableRef.value?.handleQuery?.();
}

async function onPrint(row: DeregistrationVo) {
  const resp: any = await deregistrationApi.print(row.id!);
  printData.value = resp?.data ?? null;
  printShow.value = true;
}

useCollectionSearchRecord('/collection/deregistration', 'DEREGISTRATION', id => caseRef.value?.open(id));
</script>
