<template>
  <div class="flex h-full min-h-0 flex-col gap-3">
    <NAlert type="info">本页办理馆内鉴定记录；正式文物级别须另外记录行政定级与备案依据。征集前的真伪与价值评估在征集申请中办理。</NAlert>
  <HPageDataTable auto-scroll-x
    class="collection-data-page min-h-0 flex-1"
    title="藏品鉴定记录"
    ref="tableRef"
    :columns="columns"
    :fetch-data="appraisalApi.page"
    :params="queryParams"
    :is-edit="false"
    :is-delete="false"
    row-key-field="id"
    @add="onAdd"
    @row-view="onView"
  >
    <template #queryForm>
      <NFormItem label="检索内容">
        <NInput v-model:value="queryParams.keyword" clearable placeholder="登记号 / 名称 / 定名 / 断代 / 委员 / 结论" />
      </NFormItem>
      <NFormItem label="关联藏品">
        <CollectionSelect v-model:value="queryParams.collectionId" clearable />
      </NFormItem>
      <NFormItem label="鉴定定名">
        <NInput v-model:value="queryParams.resultName" clearable placeholder="定名" />
      </NFormItem>
      <NFormItem label="建议级别">
        <HDictSelect v-model:value="queryParams.resultLevel" type="col_relic_level" clearable placeholder="级别" />
      </NFormItem>
      <NFormItem label="审批状态">
        <HDictSelect v-model:value="queryParams.flowStatus" type="flow_biz_status" clearable placeholder="状态" />
      </NFormItem>
      <NFormItem label="档案附件">
        <HDictSelect v-model:value="queryParams.hasAttachment" type="col_presence" clearable placeholder="有无" />
      </NFormItem>
      <NFormItem label="定案时间">
        <NDatePicker v-model:value="appraisedRange" type="daterange" clearable @update:value="syncAppraisedRange" />
      </NFormItem>
    </template>

    <template #toolbar-extra>
      <OriginalHistoryButton business-type="COLLECTION_APPRAISAL">
        <template #trigger="{ open }">
          <CollectionActionMenu label="更多">
            <NButton quaternary @click="gradingRef?.open()">行政定级与备案</NButton>
            <NButton quaternary size="small" @click="open">历史材料</NButton>
          </CollectionActionMenu>
        </template>
      </OriginalHistoryButton>
    </template>
    <GradingComplianceDrawer ref="gradingRef" @saved="refreshTable" />
    <FlowFormDrawer
      ref="flowDrawerRef"
      :fetch-fn="appraisalApi.flow"
      :create-api="appraisalApi.apply"
      :save-api="saveFlowDraft"
      :submit-api="appraisalApi.submit"
      :cancel-api="appraisalApi.cancel"
      @success="refreshTable"
    />

    <CollectionPrintPreviewModal v-model:show="printShow" :data="printData" />
  </HPageDataTable>
  </div>
</template>

<script setup lang="ts">
import CollectionActionMenu from '../components/CollectionActionMenu.vue';
import '../styles/collection-page.css';
import { useCollectionSearchRecord } from '../composables/useCollectionSearchRecord';
import OriginalHistoryButton from '@/modules/collection/components/OriginalHistoryButton.vue';
import { h, reactive, ref, watch, onBeforeUnmount, onDeactivated } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '@/store/modules/auth';
import { useCollectionEntryAction } from '../composables/useCollectionEntryAction';
import { NButton, useMessage } from 'naive-ui';
import { cloneDeep } from 'lodash-es';
import { appraisalApi } from '../api/appraisal';
import CollectionSelect from '../components/CollectionSelect.vue';
import GradingComplianceDrawer from '../components/GradingComplianceDrawer.vue';
import CollectionPrintPreviewModal from '../components/CollectionPrintPreviewModal.vue';
import FlowFormDrawer from '../../flow/components/FlowFormDrawer.vue';
import { useFlowApproval } from '../../flow/composables/useFlowApproval';
import { presenceText } from '../utils/dictRender';
import type { AppraisalVo, CollectionPrintVo } from '../typings/collection';

const gradingRef = ref<any>();
const entryAuth = useAuthStore();
useCollectionEntryAction('/collection/appraisal', 'grading',
  () => entryAuth.hasPermission('COLLECTION_ADMIN,COLLECTION_APPRAISE'), () => gradingRef.value?.open());
const tableRef = ref<any>(null);
const router = useRouter();
const flowDrawerRef = ref<any>(null);
const { flowStatusColumn, handleTitleClick } = useFlowApproval({
  refTable: tableRef,
  drawerRef: flowDrawerRef,
  getLabel: (row: AppraisalVo) => row.collectionName || row.resultName || row.id || '鉴定详情',
});

const queryParams = reactive({
  keyword: null as string | null,
  collectionId: null as string | null,
  resultName: null as string | null,
  resultLevel: null as string | null,
  flowStatus: null as string | null,
  hasAttachment: null as string | null,
  appraisedAtStart: null as number | null,
  appraisedAtEnd: null as number | null,
});
const appraisedRange = ref<[number, number] | null>(null);

const defaultForm: AppraisalVo = {};
const message = useMessage();
let printRevision = 0;
const printShow = ref(false);
const printData = ref<CollectionPrintVo | null>(null);
watch(
  printShow,
  (shown) => {
    if (!shown) {
      printRevision++;
      printData.value = null;
    }
  },
  { flush: 'sync' },
);
function clearPrintPreview() {
  printRevision++;
  printData.value = null;
  printShow.value = false;
}
onDeactivated(clearPrintPreview);
onBeforeUnmount(clearPrintPreview);

function syncAppraisedRange(value: [number, number] | null) {
  queryParams.appraisedAtStart = value?.[0] ?? null;
  queryParams.appraisedAtEnd = value?.[1] ?? null;
}

const columns = [
  { resizable: true, title: '总登记号', key: 'collectionTotalRegNo', width: 150, ellipsis: { tooltip: true } },
  { resizable: true, width: 160,
    title: '藏品名称',
    key: 'collectionName',
    minWidth: 160,
    columnType: 'title',
    fixed: 'left',
    ellipsis: { tooltip: true },
  },
  { resizable: true, width: 150, title: '定名', key: 'resultName', minWidth: 150, ellipsis: { tooltip: true } },
  { ellipsis: { tooltip: true }, resizable: true, title: '断代', key: 'resultEra', width: 120 },
  { ellipsis: { tooltip: true }, resizable: true, title: '建议级别', key: 'resultLevel', width: 90, dictType: 'col_relic_level' },
  {
    title: '附件',
    key: 'attachmentIds',
    width: 80,
    render: (r: AppraisalVo) => presenceText(r.attachmentIds),
  },
  flowStatusColumn,
  { title: '定案时间', key: 'appraisedAt', width: 200, columnType: 'dateTime' },
  { title: '提交时间', key: 'createTime', width: 200, columnType: 'dateTime' },
  {
    title: '操作',
    key: 'operate',
    type: 'operate',
    width: 380,
    fixed: 'right' as const,
    render: (row: AppraisalVo) => [
      h(NButton, { text: true, type: 'primary', size: 'small', onClick: () => onView(row) }, () => '详情'),
      h(
        NButton,
        {
          text: true,
          size: 'small',
          style: 'margin-left:10px',
          onClick: () => gradingRef.value?.open(row.collectionId),
        },
        () => '行政定级原件',
      ),
      row.flowStatus === 'PASS'
        ? h(
            NButton,
            { text: true, type: 'warning', size: 'small', style: 'margin-left:10px', onClick: () => onChange(row) },
            () => '发起信息变更',
          )
        : null,
      h(NButton, { text: true, size: 'small', style: 'margin-left:10px', onClick: () => onPrint(row) }, () => '打印'),
    ],
  },
];

async function onChange(row: AppraisalVo) {
  const result: any = await appraisalApi.createChange(row.id!);
  await router.push({ path: '/collection/change', query: { changeId: result.data } });
}

function onAdd() {
  flowDrawerRef.value?.openCreate({
    title: '新增鉴定申请',
    businessType: 'COLLECTION_APPRAISAL',
    formData: {
      ...cloneDeep(defaultForm),
      collectionId: undefined,
      resultName: undefined,
      resultEra: undefined,
      resultTexture: undefined,
      resultLevel: undefined,
      members: [],
      conclusion: undefined,
      attachmentIds: undefined,
      id: undefined,
    },
  });
}

function onView(row: AppraisalVo) {
  handleTitleClick(row);
}

async function onPrint(row: AppraisalVo) {
  printShow.value = false;
  printData.value = null;
  const revision = ++printRevision;
  try {
    if (!row.id) throw new Error('缺少鉴定记录标识，无法打印');
    const resp: any = await appraisalApi.print(row.id);
    if (revision !== printRevision) return;
    const data = resp?.data;
    if (!data || data.businessId !== row.id || data.type !== 'APPRAISAL')
      throw new Error('鉴定打印资料缺失或与当前记录不一致');
    printData.value = data;
    printShow.value = true;
  } catch (error: any) {
    if (revision === printRevision)
      message.error(error?.response?.data?.message || error?.message || '鉴定打印资料读取失败');
  }
}

function saveFlowDraft(id: string, data: Record<string, any>) {
  return appraisalApi.apply({ ...data, id });
}

function refreshTable() {
  tableRef.value?.handleRefresh();
  tableRef.value?.handleQuery?.();
}
useCollectionSearchRecord('/collection/appraisal', 'APPRAISAL', id => flowDrawerRef.value?.open(id));
</script>
