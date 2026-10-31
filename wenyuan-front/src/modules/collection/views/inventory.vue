<template>
  <div class="inventory-page">
    <NCard title="清点批次" size="small" class="inventory-batch-card collection-data-page">
      <template #header-extra>
        <NSpace size="small" align="center">
          <CollectionActionMenu label="批次维护" :loading="batchLoading">
            <NButton quaternary size="small" :loading="batchLoading" @click="loadBatchSummaries">刷新</NButton>
            <NButton quaternary size="small" type="primary" @click="openBatchConfig()">{{
              selectedBatchName ? '维护批次' : '配置批次'
            }}</NButton>
          </CollectionActionMenu>
          <NButton
            size="small"
            quaternary
            @click="batchExpanded = !batchExpanded"
            :aria-expanded="batchExpanded"
            aria-controls="inventory-batch-list"
            >{{ batchExpanded ? '收起批次' : '展开批次' }}</NButton
          >
        </NSpace>
      </template>
      <div class="inventory-batch-context">
        <div class="inventory-batch-selection">
          <NTag size="small" :bordered="false" :type="selectedBatchName ? 'info' : 'default'">{{ selectedBatchName ? inventoryBatchTitle(selectedBatchName) : '全部批次' }}</NTag>
          <NButton v-if="selectedBatchName" size="small" text @click="clearSelectedBatch">查看全部</NButton>
          <NText depth="3" class="inventory-hint">{{ selectedBatchName ? '下方记录与新增登记使用当前批次。' : '选择批次后，可查看记录或开展 RFID 会话清点。' }}</NText>
        </div>
        <NSpace size="small" align="center" wrap>
          <NButton size="small" type="primary" secondary :disabled="!selectedBatchName" @click="openRfidSession">RFID 会话清点</NButton>
          <NDropdown trigger="click" placement="bottom-end" :options="batchActionOptions" @select="handleBatchAction">
            <NButton size="small" secondary>清点与报送<NIcon style="margin-left: 4px"><IconMdiMenuDown /></NIcon></NButton>
          </NDropdown>
        </NSpace>
      </div>
      <div v-show="batchExpanded" id="inventory-batch-list" class="inventory-batch-list">
        <NDataTable :scroll-x="1062"
          :columns="batchSummaryColumns"
          :data="batchSummaries"
          :loading="batchLoading"
          :row-class-name="batchRowClassName"
          size="small"
          :bordered="false"
          :pagination="false"
          :max-height="220"
          class="batch-summary-table"
        />
      </div>
    </NCard>

    <HPageDataTable class="collection-data-page" auto-scroll-x
      ref="tableRef"
      title="清点记录"
      :columns="columns"
      :fetch-data="inventoryApi.page"
      :params="queryParams"
      :is-add="false"
      :is-edit="false"
      :is-delete="false"
      row-key-field="id"
      @add="onAdd"
      @row-view="onView"
    >
      <template #toolbar-extra>
        <NSpace v-if="canManageInventory" size="small" align="center">
          <NButton size="small" type="primary" @click="onAdd">登记清点结果</NButton>
          <CollectionActionMenu label="批量处理">
            <NButton quaternary size="small" @click="openImport">导入清点记录</NButton>
            <NButton quaternary size="small" @click="openRfidCheck">比对已扫描标签</NButton>
          </CollectionActionMenu>
        </NSpace>
      </template>

      <template #queryForm>
        <NFormItem label="检索内容">
          <NInput
            v-model:value="queryParams.keyword"
            clearable
            placeholder="批次 / 登记号 / 名称 / 备注"
          />
        </NFormItem>
        <NFormItem label="盘点批次">
          <NInput v-model:value="queryParams.batchName" clearable placeholder="盘点批次" />
        </NFormItem>
        <NFormItem label="关联藏品">
          <CollectionSelect v-model:value="queryParams.collectionId" clearable />
        </NFormItem>
        <NFormItem label="盘点结果">
          <HDictSelect
            v-model:value="queryParams.result"
            type="col_inventory_result"
            clearable
            placeholder="盘点结果"
          />
        </NFormItem>
        <NFormItem label="审批状态">
          <HDictSelect
            v-model:value="queryParams.flowStatus"
            type="flow_biz_status"
            clearable
            placeholder="状态"
          />
        </NFormItem>
        <NFormItem label="档案附件">
          <HDictSelect
            v-model:value="queryParams.hasAttachment"
            type="col_presence"
            clearable
            placeholder="有无"
          />
        </NFormItem>
        <NFormItem label="盘点时间">
          <NDatePicker
            v-model:value="checkedRange"
            type="daterange"
            clearable
            @update:value="syncCheckedRange"
          />
        </NFormItem>
      </template>

      <FlowFormDrawer
        ref="flowDrawerRef"
        :fetch-fn="inventoryApi.flow"
        :create-api="inventoryApi.create"
        :save-api="saveFlowDraft"
        :submit-api="inventoryApi.submit"
        :cancel-api="inventoryApi.cancel"
        @success="refreshTable"
      />

      <InventoryComplianceDrawer ref="complianceRef" @saved="refreshTable" />
      <CollectionPrintPreviewModal v-model:show="printShow" :data="printData" />

      <NModal v-model:show="batchConfigShow" preset="card" title="盘点批次配置" style="width: 700px">
        <NForm label-placement="left" label-width="100">
          <NFormItem label="批次名称" required
            ><NInput v-model:value="batchConfig.batchName" :maxlength="100" show-count placeholder="如 2026 年度库房清查"
          /></NFormItem>
          <NFormItem label="盘点周期"
            ><NInputNumber v-model:value="batchConfig.cycleMonths" :min="1" placeholder="月" style="width: 100%"
          /></NFormItem>
          <NFormItem label="目标库位"
            ><WarehouseTreeSelect v-model:value="batchConfig.warehouseId" placeholder="留空表示全馆"
          /></NFormItem>
          <NFormItem label="指定应盘藏品"
            ><CollectionSelect
              v-model:value="batchCollectionIds"
              multiple
              life-status="IN_STORE"
              placeholder="留空按目标库位确定范围"
          /></NFormItem>
          <NFormItem label="开始/结束"
            ><NDatePicker v-model:value="batchDateRange" type="datetimerange" clearable style="width: 100%"
          /></NFormItem>
          <NFormItem label="范围说明"
            ><NInput v-model:value="batchConfig.scopeDesc" type="textarea" :rows="2" :maxlength="500"
          /></NFormItem>
          <NFormItem label="盘点报告"
            ><HUploadInput
              v-model:value="batchConfig.reportAttachmentIds"
              @upload-finish="batchConfig.reportAttachmentIds = $event.id"
              clearable
              placeholder="盘点结束后上传签署报告"
          /></NFormItem>
          <NFormItem label="备注"><NInput v-model:value="batchConfig.remark" type="textarea" :rows="2" :maxlength="1000" /></NFormItem>
        </NForm>
        <template #footer
          ><NSpace justify="end"
            ><NButton @click="batchConfigShow = false">取消</NButton
            ><NButton type="primary" :loading="batchConfigSaving" @click="saveBatchConfig">保存配置</NButton></NSpace
          ></template
        >
      </NModal>

      <NModal v-model:show="rfidShow" preset="card" title="RFID 批量盘点" style="width: 640px">
        <NForm label-placement="left" label-width="90">
          <NFormItem label="盘点批次" required>
            <NInput v-model:value="rfidForm.batchName" :maxlength="100" placeholder="如 2026年度库房清查" />
          </NFormItem>
          <NFormItem label="限定库位">
            <WarehouseTreeSelect v-model:value="rfidForm.warehouseId" placeholder="留空=全馆在库藏品" />
          </NFormItem>
          <NFormItem label="扫描标签">
            <NInput
              v-model:value="rfidForm.tagsText"
              type="textarea"
              :rows="6"
              placeholder="将手持机扫描结果粘贴到此处（每行一个标签号，或用逗号分隔）；扫码枪可直接对准此框连续扫描"
            />
          </NFormItem>
          <NText depth="3" style="font-size: 12px">
            比对规则：应盘（在库且已绑标签）∩ 扫到 → 相符；应盘未扫到 → 盘亏；扫到但不在应盘范围 →
            状态异常。生成的盘点草稿仍需提交审批。
          </NText>
        </NForm>
        <template #footer>
          <NSpace justify="end">
            <NButton @click="rfidShow = false">取消</NButton>
            <NButton type="primary" :loading="rfidChecking" @click="submitRfidCheck">开始比对</NButton>
          </NSpace>
        </template>
      </NModal>

      <NModal
        v-model:show="importShow"
        preset="card"
        title="批量导入盘点记录"
        style="width: min(920px, calc(100vw - 32px))"
      >
        <div class="import-modal-body">
          <NText depth="3">总登记号只匹配当前收藏单位；附件填写已上传的唯一文件名，多文件用分号分隔。</NText>
          <div class="import-upload-bar">
            <NSpace align="center">
              <NButton type="primary" secondary @click="openImportFile">选择 Excel/CSV</NButton>
              <NButton secondary :disabled="!importTemplate.length" @click="downloadImportTemplate">下载模板</NButton>
            </NSpace>
            <div class="import-file-state">
              <strong>{{ importFileName || '未选择文件' }}</strong>
              <span>{{
                importRows.length ? `已解析 ${importRows.length} 条待导入数据` : '请选择模板文件后再预校验'
              }}</span>
            </div>
          </div>
          <input
            ref="importFileInput"
            type="file"
            accept=".xlsx,.xls,.csv"
            style="display: none"
            @change="onImportFileChange"
          />

          <div class="import-section import-template-section">
            <div class="import-section-title">
              <span>导入列说明</span>
              <NText depth="3">按模板列名匹配，必填列需完整填写</NText>
            </div>
            <div class="import-table-wrap">
              <NDataTable :scroll-x="640"
                :columns="templateColumns"
                :data="importTemplate"
                size="small"
                :bordered="false"
                :max-height="220"
              />
            </div>
          </div>

          <div class="import-section import-result-section">
            <div class="import-section-title">
              <span>校验结果</span>
              <NText depth="3">{{ importErrors.length ? `${importErrors.length} 条错误` : '暂无错误' }}</NText>
            </div>
            <NEmpty
              v-if="!importRows.length && !importErrors.length"
              description="选择文件后将显示解析与校验结果"
              size="small"
            />
            <NDataTable :scroll-x="480"
              v-if="importErrors.length"
              :columns="importErrorColumns"
              :data="importErrors"
              size="small"
              :bordered="false"
              :max-height="160"
              class="import-error-table"
            />
            <NText v-else-if="importRows.length" type="success">文件已解析，可先预校验再确认导入。</NText>
          </div>
        </div>
        <template #footer>
          <NSpace justify="end">
            <NButton @click="importShow = false">取消</NButton>
            <NButton :disabled="!importRows.length" @click="validateImport">预校验</NButton>
            <NButton type="primary" :disabled="!importRows.length" @click="commitImport">确认导入</NButton>
          </NSpace>
        </template>
      </NModal>
    </HPageDataTable>
  </div>
</template>

<script setup lang="ts">
import CollectionActionMenu from '../components/CollectionActionMenu.vue';
import '../styles/collection-page.css';
import { useCollectionSearchRecord } from '../composables/useCollectionSearchRecord';
import { useAuthStore } from '@/store/modules/auth';
import { useCollectionEntryAction } from '../composables/useCollectionEntryAction';
import { computed, h, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { cloneDeep } from 'lodash-es';
import { NButton, NTag, useMessage } from 'naive-ui';
import * as XLSX from 'xlsx';
import { type as fetchDictType } from '@/modules/sys/api/dict';
import { inventoryApi } from '../api/inventory';
import CollectionSelect from '../components/CollectionSelect.vue';
import CollectionPrintPreviewModal from '../components/CollectionPrintPreviewModal.vue';
import WarehouseTreeSelect from '../components/WarehouseTreeSelect.vue';
import FlowFormDrawer from '../../flow/components/FlowFormDrawer.vue';
import InventoryComplianceDrawer from '../components/InventoryComplianceDrawer.vue';
import { useFlowApproval } from '../../flow/composables/useFlowApproval';
import { formatPrintDateTime } from '../utils/print';
import { dictText, presenceText } from '../utils/dictRender';
import { formatImportDictOptions, formatImportSample } from '../utils/importTemplateDisplay';
import { applyLegacyImportHeaders } from '../utils/importHeaderCompatibility';
import {
  applyInventoryBatchSelection,
  clearInventoryBatchSelection,
  inventoryBatchTitle,
  mergeInventoryBatchSummaries,
} from '../utils/inventoryBatchSelection';
import type {
  CollectionImportErrorVo,
  CollectionImportTemplateColumnVo,
  CollectionPrintVo,
  InventoryBatchSummaryVo,
  InventoryBatchVo,
  InventoryImportRowVo,
  InventoryVo,
} from '../typings/collection';

const message = useMessage();
const router = useRouter();
const tableRef = ref<any>(null);
const flowDrawerRef = ref<any>(null);
const complianceRef = ref<any>();
const entryAuth = useAuthStore();
const canManageInventory = computed(() => entryAuth.hasPermission('ADMIN,COLLECTION_ADMIN,COLLECTION_STORAGE'));
useCollectionEntryAction('/collection/inventory', 'compliance',
  () => entryAuth.hasPermission('COLLECTION_ADMIN,COLLECTION_STORAGE'), () => complianceRef.value?.open(selectedBatchName.value));
const { flowStatusColumn, handleTitleClick } = useFlowApproval({
  refTable: tableRef,
  drawerRef: flowDrawerRef,
  getLabel: (row: InventoryVo) => row.batchName || row.collectionName || row.id || '盘点详情',
});

const queryParams = reactive({
  keyword: null as string | null,
  batchName: null as string | null,
  collectionId: null as string | null,
  result: null as string | null,
  flowStatus: null as string | null,
  hasAttachment: null as string | null,
  checkedAtStart: null as number | null,
  checkedAtEnd: null as number | null,
});
const checkedRange = ref<[number, number] | null>(null);
const batchLoading = ref(false);
const batchSummaries = ref<InventoryBatchSummaryVo[]>([]);
const selectedBatchName = computed({
  get: () => queryParams.batchName,
  set: (value: string | null) => { queryParams.batchName = value; },
});
const batchExpanded = ref(true);
const batchOptions = computed(() => batchSummaries.value
  .filter((row) => row.batchName)
  .map((row) => ({ label: row.batchName!, value: row.batchName! })));
const batchActionOptions = [
  { label: '清点计划与离任移交', key: 'compliance' },
  { label: '年度清点报送', key: 'annual' },
];
function handleBatchAction(key: string) {
  if (key === 'compliance') void complianceRef.value?.open(selectedBatchName.value);
  if (key === 'annual') void complianceRef.value?.open(selectedBatchName.value, 'annual');
}
function batchRowClassName(row: InventoryBatchSummaryVo) {
  return row.batchName === selectedBatchName.value ? 'inventory-selected-batch' : '';
}
const batchConfigShow = ref(false);
const batchConfigSaving = ref(false);
const batchConfig = reactive<InventoryBatchVo>({ cycleMonths: 12 });
const batchCollectionIds = ref<string[]>([]);
const batchDateRange = ref<[number, number] | null>(null);

async function openBatchConfig(target?: string | null) {
  const batchName = typeof target === 'string' ? target : selectedBatchName.value;
  const resp: any = await inventoryApi.batchConfigs();
  const configs: InventoryBatchVo[] = resp?.data ?? [];
  const existing = configs.find((item) => item.batchName === batchName);
  if (existing?.plan) { await complianceRef.value?.open(existing.batchName); return; }
  Object.assign(
    batchConfig,
    existing || {
      id: undefined,
      batchName: batchName || `${new Date().getFullYear()}年度盘点`,
      cycleMonths: 12,
      warehouseId: undefined,
      scopeDesc: undefined,
      reportAttachmentIds: undefined,
      remark: undefined,
    },
  );
  batchCollectionIds.value = (existing?.collectionIds || '').split(',').filter(Boolean);
  batchDateRange.value = existing?.startedAt && existing?.finishedAt ? [existing.startedAt, existing.finishedAt] : null;
  batchConfigShow.value = true;
}

async function saveBatchConfig() {
  if (!batchConfig.batchName?.trim()) {
    message.warning('请填写盘点批次名称');
    return;
  }
  batchConfigSaving.value = true;
  try {
    await inventoryApi.saveBatch({
      ...batchConfig,
      collectionIds: batchCollectionIds.value.join(',') || undefined,
      startedAt: batchDateRange.value?.[0],
      finishedAt: batchDateRange.value?.[1],
    });
    selectedBatchName.value = batchConfig.batchName;
    queryParams.batchName = batchConfig.batchName;
    batchConfigShow.value = false;
    message.success('盘点批次配置已保存');
    await loadBatchSummaries();
    tableRef.value?.handleRefresh?.();
  } finally {
    batchConfigSaving.value = false;
  }
}

function syncCheckedRange(value: [number, number] | null) {
  queryParams.checkedAtStart = value?.[0] ?? null;
  queryParams.checkedAtEnd = value?.[1] ?? null;
}

onMounted(loadBatchSummaries);

const batchSummaryColumns = [
  {
    resizable: true,
    width: 180,
    title: '批次',
    key: 'batchName',
    minWidth: 180,
    ellipsis: { tooltip: true },
    render: (row: InventoryBatchSummaryVo) =>
      row.configId && !row.started
        ? [row.batchName, h(NTag, { size: 'small', bordered: false, style: 'margin-left:6px' }, () => '未开始')]
        : row.batchName,
  },
  { title: '记录数', key: 'total', width: 72 },
  { title: '相符', key: 'matchCount', width: 72 },
  { title: '盘盈', key: 'surplusCount', width: 72 },
  { title: '盘亏', key: 'missingCount', width: 72 },
  { title: '异常', key: 'abnormalCount', width: 72 },
  { title: '附件记录', key: 'attachmentCount', width: 92 },
  {
    title: '最近盘点',
    key: 'lastCheckedAt',
    width: 200,
    render: (row: InventoryBatchSummaryVo) => formatPrintDateTime(row.lastCheckedAt) || '--',
  },
  {
    title: '操作',
    key: 'action',
    width: 230,
    fixed: 'right' as const,
    render: (row: InventoryBatchSummaryVo) => [
      h(NButton, { text: true, type: 'primary', size: 'small', onClick: () => selectBatch(row) }, () => row.batchName === selectedBatchName.value ? '当前批次' : '查看记录'),
      ...(row.configId && canManageInventory.value
        ? [
            h(
              NButton,
              { text: true, size: 'small', style: 'margin-left:10px', onClick: () => manageBatch(row) },
              () => (row.started ? '清点进度' : '维护'),
            ),
            ...(row.started
              ? []
              : [
                  h(
                    NButton,
                    { text: true, size: 'small', style: 'margin-left:10px', onClick: () => beginBatch(row) },
                    () => '开始清点',
                  ),
                ]),
          ]
        : []),
      h(
        NButton,
        {
          text: true,
          size: 'small',
          style: 'margin-left:10px',
          disabled: !row.firstInventoryId,
          onClick: () => printBatch(row),
        },
        () => '打印',
      ),
    ],
  },
];

let batchSummaryRequest = 0;
async function loadBatchSummaries() {
  const request = ++batchSummaryRequest;
  batchSummaries.value = [];
  batchLoading.value = true;
  try {
    // /batches 只汇总已有清点记录的批次；已配置但还没开始的批次来自 /batch，合并后一起列出。
    const [resp, configs]: any[] = await Promise.all([
      inventoryApi.batches({ ...queryParams }),
      inventoryApi.batchConfigs({ silentError: true }).catch(() => null),
    ]);
    if (request === batchSummaryRequest)
      batchSummaries.value = mergeInventoryBatchSummaries(
        resp?.data ?? [],
        configs?.data ?? [],
        queryParams,
      ) as InventoryBatchSummaryVo[];
  } catch (error) {
    if (request === batchSummaryRequest) message.error(error instanceof Error ? error.message : '清点汇总读取失败，请核对当前权限');
  } finally {
    if (request === batchSummaryRequest) batchLoading.value = false;
  }
}

function selectBatch(row: InventoryBatchSummaryVo) {
  selectedBatchName.value = applyInventoryBatchSelection(row, queryParams);
  tableRef.value?.handleRefresh?.();
}

function manageBatch(row: InventoryBatchSummaryVo) {
  selectBatch(row);
  void openBatchConfig(row.batchName);
}

function beginBatch(row: InventoryBatchSummaryVo) {
  selectBatch(row);
  void complianceRef.value?.open(row.batchName);
}

function clearSelectedBatch() {
  selectedBatchName.value = clearInventoryBatchSelection(queryParams);
  tableRef.value?.handleRefresh?.();
}

function openRfidSession() {
  if (!selectedBatchName.value) return;
  void router.push({ path: '/collection/rfid-desk', query: { type: 'INVENTORY', businessId: selectedBatchName.value } });
}

async function printBatch(row: InventoryBatchSummaryVo) {
  if (!row.firstInventoryId) return;
  const resp: any = await inventoryApi.print(row.firstInventoryId);
  printData.value = resp?.data ?? null;
  printShow.value = true;
}

const defaultForm: InventoryVo = {
  id: undefined,
  batchName: undefined,
  collectionId: undefined,
  result: undefined,
  attachmentIds: undefined,
  note: undefined,
};

const printShow = ref(false);
const printData = ref<CollectionPrintVo | null>(null);

const columns = [
  { resizable: true, width: 180, title: '批次', key: 'batchName', minWidth: 180, columnType: 'title', fixed: 'left', ellipsis: { tooltip: true } },
  { ellipsis: { tooltip: true }, resizable: true,
    title: '总登记号',
    key: 'collectionTotalRegNo',
    width: 130,
    render: (row: InventoryVo) => row.collectionTotalRegNo || '--',
  },
  { resizable: true, width: 160,
    title: '藏品名称',
    key: 'collectionName',
    minWidth: 160,
    ellipsis: { tooltip: true },
    render: (row: InventoryVo) =>
      row.collectionName || row.collectionId || (row.result === 'SURPLUS' ? '未登账实物' : '--'),
  },
  { ellipsis: { tooltip: true }, resizable: true, title: '结果', key: 'result', width: 120, dictType: 'col_inventory_result' },
  flowStatusColumn,
  {
    title: '附件',
    key: 'attachmentIds',
    width: 80,
    render: (r: InventoryVo) => presenceText(r.attachmentIds),
  },
  { resizable: true, width: 160, title: '备注', key: 'note', minWidth: 160, ellipsis: { tooltip: true } },
  { title: '盘点时间', key: 'checkedAt', width: 200, columnType: 'dateTime' },
  {
    title: '操作',
    key: 'operate',
    type: 'operate',
    width: 80,
    fixed: 'right' as const,
    render: (row: InventoryVo) => {
      const btns = [
        h(NButton, { text: true, type: 'primary', size: 'small', onClick: () => onView(row) }, () => '详情'),
      ];
      return btns;
    },
  },
];

function onAdd() {
  const formData = cloneDeep(defaultForm);
  if (selectedBatchName.value) formData.batchName = selectedBatchName.value;
  flowDrawerRef.value?.openCreate({
    title: '盘点登记',
    businessType: 'COLLECTION_INVENTORY',
    formData,
  });
}

function onView(row: InventoryVo) {
  handleTitleClick(row);
}

async function saveFlowDraft(id: string, data: Record<string, any>) {
  return inventoryApi.save({ ...data, id });
}

async function refreshTable() {
  tableRef.value?.handleRefresh?.();
  tableRef.value?.handleQuery?.();
  await loadBatchSummaries();
}

// ── RFID 批量盘点 ──
const rfidShow = ref(false);
const rfidChecking = ref(false);
const rfidForm = reactive({ batchName: '', warehouseId: null as string | null, tagsText: '' });

function openRfidCheck() {
  rfidForm.batchName = `${new Date().getFullYear()}年度RFID盘点`;
  rfidForm.warehouseId = null;
  rfidForm.tagsText = '';
  rfidShow.value = true;
}

async function submitRfidCheck() {
  const tags = rfidForm.tagsText
    .split(/[\s,，;；]+/)
    .map((t) => t.trim())
    .filter(Boolean);
  if (!rfidForm.batchName.trim()) {
    message.warning('请填写盘点批次');
    return;
  }
  if (!tags.length) {
    message.warning('请粘贴扫描到的标签号');
    return;
  }
  rfidChecking.value = true;
  try {
    const resp: any = await inventoryApi.rfidCheck({
      batchName: rfidForm.batchName.trim(),
      warehouseId: rfidForm.warehouseId || undefined,
      tags,
    });
    const r = resp?.data ?? {};
    const unknown = (r.unknownTags ?? []).length;
    message.success(
      `比对完成：相符 ${r.matchCount ?? 0}、盘亏 ${r.missingCount ?? 0}、异常 ${r.abnormalCount ?? 0}` +
        `（未绑标签 ${r.untaggedCount ?? 0} 件未参与${unknown ? `，未知标签 ${unknown} 个` : ''}），已生成 ${r.createdCount ?? 0} 条盘点草稿`,
    );
    rfidShow.value = false;
    tableRef.value?.handleRefresh();
  } finally {
    rfidChecking.value = false;
  }
}

const importShow = ref(false);
const importTemplate = ref<CollectionImportTemplateColumnVo[]>([]);
const importErrors = ref<CollectionImportErrorVo[]>([]);
const importRows = ref<InventoryImportRowVo[]>([]);
const importDictOptions = ref<Record<string, { label: string; value: string }[]>>({});
const importFileInput = ref<HTMLInputElement | null>(null);
const importFileName = ref('');
const templateColumns = [
  { ellipsis: { tooltip: true }, resizable: true, title: '导入列', key: 'title', width: 150 },
  {
    title: '必填',
    key: 'required',
    width: 70,
    render: (r: CollectionImportTemplateColumnVo) => dictText('col_required', r.required),
  },
  { ellipsis: { tooltip: true }, resizable: true, width: 260,
    title: '可选值',
    key: 'dictType',
    minWidth: 260,
    render: (r: CollectionImportTemplateColumnVo) => dictOptionText(r.dictType),
  },
  { ellipsis: { tooltip: true }, resizable: true, width: 160, title: '示例', key: 'sample', minWidth: 160, render: (r: CollectionImportTemplateColumnVo) => importSampleText(r) },
];
const importErrorColumns = [
  { title: '行号', key: 'rowNo', width: 80 },
  { ellipsis: { tooltip: true }, resizable: true, title: '字段', key: 'field', width: 140 },
  { ellipsis: { tooltip: true }, resizable: true, width: 260, title: '错误', key: 'message', minWidth: 260 },
];

async function openImport() {
  if (!importTemplate.value.length) {
    const resp: any = await inventoryApi.importTemplate();
    importTemplate.value = resp?.data?.list ?? resp?.data ?? [];
    await loadImportDictOptions();
  } else {
    await loadImportDictOptions();
  }
  importErrors.value = [];
  importShow.value = true;
}

async function loadImportDictOptions() {
  const dictTypes = Array.from(new Set(importTemplate.value.map((col) => col.dictType).filter(Boolean))) as string[];
  const unloaded = dictTypes.filter((type) => !importDictOptions.value[type]);
  if (!unloaded.length) return;
  const entries = await Promise.all(
    unloaded.map(async (type) => {
      const resp: any = await fetchDictType(type, true);
      const options = (resp?.data ?? []).map((item: any) => ({
        label: String(item.label ?? item.name ?? item.value ?? ''),
        value: String(item.value ?? ''),
      }));
      return [type, options] as const;
    }),
  );
  importDictOptions.value = { ...importDictOptions.value, ...Object.fromEntries(entries) };
}

function dictOptionText(dictType?: string | null) {
  return formatImportDictOptions(dictType, importDictOptions.value);
}

function importSampleText(row: CollectionImportTemplateColumnVo) {
  return formatImportSample(row, importDictOptions.value);
}

function openImportFile() {
  importFileInput.value?.click();
}

function downloadImportTemplate() {
  const headers = importTemplate.value.map((col) => col.title || col.field);
  const samples = importTemplate.value.map((col) => col.sample || '');
  const sheet = XLSX.utils.aoa_to_sheet([headers, samples]);
  const workbook = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(workbook, sheet, '盘点导入模板');
  XLSX.writeFile(workbook, '盘点批量导入模板.xlsx');
}

function normalizeImportValue(value: any) {
  const text = typeof value === 'string' ? value.trim() : value;
  return text === '' ? undefined : text;
}

async function onImportFileChange(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;
  importFileName.value = file.name;
  const workbook = XLSX.read(await file.arrayBuffer(), { type: 'array' });
  const sheetName = workbook.SheetNames[0];
  if (!sheetName) {
    message.warning('导入文件没有可读取的工作表');
    return;
  }
  const sheet = workbook.Sheets[sheetName];
  const rawRows = XLSX.utils.sheet_to_json<Record<string, any>>(sheet, { defval: '', raw: false });
  const fieldByHeader = new Map<string, string>();
  importTemplate.value.forEach((col) => {
    fieldByHeader.set(col.field, col.field);
    fieldByHeader.set(col.title, col.field);
  });
  applyLegacyImportHeaders('inventory', fieldByHeader);
  const rows = rawRows
    .map((raw, index) => {
      const row: Record<string, any> = { rowNo: index + 2 };
      Object.entries(raw).forEach(([header, value]) => {
        const field = fieldByHeader.get(header.trim());
        if (!field) return;
        const normalized = normalizeImportValue(value);
        if (normalized !== undefined) row[field] = normalized;
      });
      return row as InventoryImportRowVo;
    })
    .filter((row) => Object.keys(row).some((key) => key !== 'rowNo'));
  importRows.value = rows;
  importErrors.value = [];
  input.value = '';
  message.success(`已解析 ${rows.length} 条导入数据`);
}

function parseImportRows(): InventoryImportRowVo[] | null {
  if (!importRows.value.length) {
    message.warning('请先选择 Excel/CSV 文件');
    return null;
  }
  return importRows.value;
}

async function validateImport() {
  const rows = parseImportRows();
  if (!rows) return;
  const resp: any = await inventoryApi.validateImport(rows);
  const data = resp?.data ?? {};
  importErrors.value = data.errors ?? [];
  if (data.valid) {
    message.success('校验通过');
  } else {
    message.warning('存在校验错误');
  }
}

async function commitImport() {
  const rows = parseImportRows();
  if (!rows) return;
  const resp: any = await inventoryApi.commitImport(rows);
  const data = resp?.data ?? {};
  importErrors.value = data.errors ?? [];
  if (!data.valid) {
    message.warning('存在校验错误，未导入');
    return;
  }
  message.success(`已导入 ${data.createdIds?.length || 0} 条盘点记录`);
  importShow.value = false;
  importRows.value = [];
  importFileName.value = '';
  tableRef.value?.handleRefresh();
  await loadBatchSummaries();
}
useCollectionSearchRecord('/collection/inventory', 'INVENTORY', id => flowDrawerRef.value?.open(id));
useCollectionSearchRecord('/collection/inventory', 'INVENTORY_BATCH', async (id, current) => {
  const response: any = await inventoryApi.batch(id);
  if (current() && response.data?.batchName) await complianceRef.value?.open(response.data.batchName, 'batch', id);
});
</script>

<style scoped>
.inventory-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.inventory-batch-card {
  margin: 0;
  flex: 0 0 auto;
}

.inventory-batch-context,
.inventory-batch-selection {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.inventory-batch-context {
  justify-content: space-between;
}

.inventory-batch-selection {
  min-width: 0;
  flex: 1 1 320px;
}

.inventory-hint {
  font-size: 12px;
}

.inventory-batch-list {
  margin-top: 12px;
}

.batch-summary-table :deep(.inventory-selected-batch td) {
  background: var(--table-color-hover, rgba(128, 128, 128, 0.08));
}

.inventory-page > :deep(.pageTable) {
  min-height: 420px;
}

.batch-summary-table {
  width: 100%;
}

.import-modal-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: min(54vh, 480px);
  overflow: hidden;
  padding-right: 4px;
}

.import-upload-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 12px;
  border: 1px solid var(--border-color, #e5e7eb);
  border-radius: 6px;
  background: var(--body-color, #f8fafc);
}

.import-file-state {
  min-width: 0;
  text-align: right;
}

.import-file-state strong,
.import-file-state span {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.import-file-state strong {
  font-weight: 600;
}

.import-file-state span {
  margin-top: 3px;
  color: var(--text-color-3, #6b7280);
  font-size: 12px;
}

.import-section {
  min-height: 0;
  padding: 12px;
  border: 1px solid var(--border-color, #e5e7eb);
  border-radius: 6px;
  background: var(--card-color, #fff);
}

.import-template-section {
  flex: 1 1 260px;
}

.import-result-section {
  flex: 0 1 170px;
}

.import-section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.import-section-title > span {
  font-weight: 600;
}

.import-table-wrap,
.import-error-table {
  max-height: 220px;
  overflow: auto;
}

@media (max-width: 640px) {
  .import-upload-bar,
  .import-section-title {
    align-items: flex-start;
    flex-direction: column;
  }

  .import-file-state {
    width: 100%;
    text-align: left;
  }
}
</style>
