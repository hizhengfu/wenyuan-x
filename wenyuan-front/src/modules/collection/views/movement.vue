<template>
  <HPageDataTable export-label="导出明细（Excel）" auto-scroll-x
    class="collection-data-page"
    :title="typeof $route?.query?.title === 'string' && $route.query.title.trim() ? $route.query.title : '出入库记录'"
    :columns="columns"
    :fetch-data="movementApi.page"
    :params="queryParams"
    :is-add="false"
    :is-edit="false"
    :is-delete="false"
    :show-operate="false"
    row-key-field="id"
    enable-selection
    @row-view="onView"
  >
    <template #activeBtn="{ checkedTableData }">
      <NButtonGroup>
        <NTooltip trigger="hover">
          <template #trigger>
            <NButton :disabled="!checkedTableData.length" @click="onExportVouchers('MOVEMENT', checkedTableData)">导出凭证（PDF/Word）</NButton>
          </template>
          勾选流水后，批量导出《藏品出入库凭证》PDF / Word
        </NTooltip>
        <NTooltip trigger="hover">
          <template #trigger>
            <NButton :disabled="!checkedTableData.length" @click="onExportVouchers('MOUNTING', checkedTableData)">导出装裱凭证（PDF/Word）</NButton>
          </template>
          勾选送裱的出入库流水后，批量导出《书画装裱交接凭证》PDF / Word
        </NTooltip>
      </NButtonGroup>
    </template>
    <template #toolbar-extra>
      <OriginalHistoryButton />
      <CollectionFieldLabel label="记录来源" help="出入库流水由正式业务办理生成；历史记录请在藏品详情中导入动态跟踪记录原件。" />
    </template>

    <template #queryForm>
      <NFormItem label="检索内容">
        <NInput v-model:value="queryParams.keyword" clearable placeholder="单号 / 藏品 / 依据 / 人员" />
      </NFormItem>
      <NFormItem label="操作类型">
        <HDictSelect v-model:value="queryParams.type" type="col_move_type" clearable placeholder="入库/出库" />
      </NFormItem>
      <NFormItem label="操作事由">
        <HDictSelect v-model:value="queryParams.reason" type="col_move_reason" clearable placeholder="事由" />
      </NFormItem>
      <NFormItem label="经办人员">
        <NInput v-model:value="queryParams.operator" clearable placeholder="经办人" />
      </NFormItem>
      <NFormItem label="档案附件">
        <HDictSelect v-model:value="queryParams.hasAttachment" type="col_presence" clearable placeholder="有无" />
      </NFormItem>
      <NFormItem label="操作时间">
        <NDatePicker
          v-model:value="operatedRange"
          type="daterange"
          clearable
          @update:value="syncOperatedRange"
        />
      </NFormItem>
    </template>

    <!-- 详情 -->
    <HPageForm content-only :enable-cancel="false" v-model:show="viewShow" title="出入库流水详情">
      <NDescriptions v-if="viewRow" label-placement="left" :column="1" bordered size="small">
        <NDescriptionsItem label="单号">{{ viewRow.documentNo || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="总登记号">{{ viewRow.totalRegNo || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="藏品名称">{{ viewRow.collectionName || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="藏品ID">{{ viewRow.collectionId || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="类型"><HDictText type="col_move_type" :value="viewRow.type" /></NDescriptionsItem>
        <NDescriptionsItem label="事由"><HDictText type="col_move_reason" :value="viewRow.reason" /></NDescriptionsItem>
        <NDescriptionsItem label="依据" content-style="white-space: pre-wrap; overflow-wrap: anywhere;">{{ viewRow.basis || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="部门">{{ viewRow.department || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="原库位">{{ viewRow.fromWarehouseId || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="目标库位">{{ viewRow.toWarehouseId || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="经办人"><UserNameText :id="viewRow.operator" :name="viewRow.operatorName" /></NDescriptionsItem>
        <NDescriptionsItem label="复核人"><UserNameText :id="viewRow.reviewer" :name="viewRow.reviewerName" /></NDescriptionsItem>
        <NDescriptionsItem label="保管人"><UserNameText :id="viewRow.custodian" :name="viewRow.custodianName" /></NDescriptionsItem>
        <NDescriptionsItem label="交接人"><UserNameText :id="viewRow.handoverUser" :name="viewRow.handoverUserName" /></NDescriptionsItem>
        <NDescriptionsItem label="复核意见" content-style="white-space: pre-wrap; overflow-wrap: anywhere;">{{ viewRow.reviewOpinion || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="附件"><HFileList :value="viewRow.attachmentIds || ''" :revision="viewRow.id" show-download-btn /></NDescriptionsItem>
        <NDescriptionsItem label="操作时间">{{ formatPrintDateTime(viewRow.operatedAt) || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="关联业务">{{ viewRow.refBizId || '--' }}</NDescriptionsItem>
      </NDescriptions>
    </HPageForm>

    <CollectionPrintPreviewModal v-model:show="printShow" :data="printData" />
    <CollectionPrintPreviewModal
      v-model:show="exportShow"
      :type="COLLECTION_VOUCHER_PRINT_TYPES[exportKind].type"
      :ids="exportIds"
      :title="voucherExportTitle(exportKind, exportIds.length)"
    />

  </HPageDataTable>
</template>

<script setup lang="ts">
import CollectionFieldLabel from '../components/CollectionFieldLabel.vue';
import '../styles/collection-page.css';
import OriginalHistoryButton from '@/modules/collection/components/OriginalHistoryButton.vue';
import { h, reactive, ref, watch, onDeactivated } from 'vue';
import { useRoute } from 'vue-router';
import { useAuthStore } from '@/store';
import { useCollectionSearchRecord } from '../composables/useCollectionSearchRecord';
import { NButton, useMessage } from 'naive-ui';
import { collectionPrintApi } from '../api/print';
import { COLLECTION_VOUCHER_PRINT_TYPES, voucherExportIds, voucherExportTitle } from '../utils/voucherExport';
import { movementApi } from '../api/movement';
import type { CollectionPrintVo, MovementVo } from '../typings/collection';
import { presenceText } from '../utils/dictRender';
import { formatPrintDateTime } from '../utils/print';
import CollectionPrintPreviewModal from '../components/CollectionPrintPreviewModal.vue';
import UserNameText from '../components/UserNameText.vue';

const message = useMessage();
const operatedRange = ref<[number, number] | null>(null);
const viewShow = ref(false);
const viewRow = ref<MovementVo | null>(null);
const printShow = ref(false);
const printData = ref<CollectionPrintVo | null>(null);
const queryParams = reactive({
  keyword: null as string | null,
  type: null as string | null,
  reason: null as string | null,
  operator: null as string | null,
  hasAttachment: null as string | null,
  operatedAtStart: null as number | null,
  operatedAtEnd: null as number | null,
});

function syncOperatedRange(value: [number, number] | null) {
  queryParams.operatedAtStart = value?.[0] ?? null;
  queryParams.operatedAtEnd = value?.[1] ?? null;
}

const columns = [
  { resizable: true, title: '单号', key: 'documentNo', width: 140, ellipsis: { tooltip: true } },
  { resizable: true, title: '总登记号', key: 'totalRegNo', width: 120, ellipsis: { tooltip: true } },
  { resizable: true, width: 160, title: '藏品名称', key: 'collectionName', minWidth: 160, columnType: 'title', fixed: 'left', ellipsis: { tooltip: true } },
  { ellipsis: { tooltip: true }, resizable: true, title: '类型', key: 'type', width: 90, dictType: 'col_move_type' },
  { ellipsis: { tooltip: true }, resizable: true, title: '事由', key: 'reason', width: 110, dictType: 'col_move_reason' },
  { resizable: true, title: '部门', key: 'department', width: 120, ellipsis: { tooltip: true } },
  { ellipsis: { tooltip: true }, resizable: true, title: '经办人', key: 'operator', width: 120, render: (row: MovementVo) => h(UserNameText, { id: row.operator, name: row.operatorName }) },
  { ellipsis: { tooltip: true }, resizable: true, title: '复核人', key: 'reviewer', width: 120, render: (row: MovementVo) => h(UserNameText, { id: row.reviewer, name: row.reviewerName }) },
  { title: '附件', key: 'attachmentIds', width: 80, render: (row: MovementVo) => presenceText(row.attachmentIds) },
  { ellipsis: { tooltip: true }, resizable: true, title: '操作时间', key: 'operatedAt', width: 200, render: (row: MovementVo) => formatPrintDateTime(row.operatedAt) || '--' },
  {
    title: '操作',
    key: 'operate',
    type: 'operate',
    width: 220,
    fixed: 'right' as const,
    render: (row: MovementVo) =>
      [
        h(NButton, { text: true, type: 'primary', size: 'small', onClick: () => onView(row) }, () => '详情'),
        h(NButton, { text: true, size: 'small', style: 'margin-left:10px', disabled: !row.id, onClick: () => onPrint(row) }, () => '打印本单'),
        h(NButton, { text: true, size: 'small', style: 'margin-left:10px', disabled: !row.id, onClick: () => onPrintMounting(row) }, () => '装裱凭证'),
      ],
  },
];

function onView(row: MovementVo) {
  viewRow.value = row;
  viewShow.value = true;
}

async function onPrint(row: MovementVo) {
  const resp: any = await movementApi.print(row.id!);
  printData.value = resp?.data ?? null;
  printShow.value = true;
}

/** 装裱凭证：后端打印类型 MOUNTING，按出入库流水 id 取数（与入库凭证一样先取打印数据再预览）。 */
async function onPrintMounting(row: MovementVo) {
  const resp: any = await collectionPrintApi.data(COLLECTION_VOUCHER_PRINT_TYPES.MOUNTING.type, row.id!);
  printData.value = resp?.data ?? null;
  printShow.value = true;
}

const exportShow = ref(false);
const exportIds = ref<string[]>([]);
const exportKind = ref<'MOVEMENT' | 'MOUNTING'>('MOVEMENT');
function onExportVouchers(kind: 'MOVEMENT' | 'MOUNTING', rows: MovementVo[]) {
  const ids = voucherExportIds(rows);
  if (!ids.length) {
    message.warning('请先勾选要导出的出入库流水');
    return;
  }
  exportKind.value = kind;
  exportIds.value = ids;
  exportShow.value = true;
}

const searchRoute = useRoute(), searchAuth = useAuthStore();
function clearSearchDetail() {
  viewShow.value = false;
  viewRow.value = null;
  printShow.value = false;
  printData.value = null;
  exportShow.value = false;
  exportIds.value = [];
}
watch(() => [searchAuth.userInfo?.id, searchAuth.identityRevision, searchAuth.identitySyncing], clearSearchDetail, { flush: 'sync' });
onDeactivated(clearSearchDetail);
useCollectionSearchRecord('/collection/movement', 'MOVEMENT', async (id, current) => {
  clearSearchDetail();
  const collectionId = typeof searchRoute.query.collectionId === 'string' ? searchRoute.query.collectionId : '';
  if (!collectionId) throw new Error('该流水缺少藏品关联，请重新检索');
  const response: any = await movementApi.byCollection(collectionId);
  if (!current()) return;
  const row = (response.data || []).find((entry: MovementVo) => String(entry.id) === id);
  if (!row) throw new Error('指定流水已不存在或当前不可读取');
  onView(row);
});
</script>
