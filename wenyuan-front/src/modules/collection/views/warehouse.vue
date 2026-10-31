<template>
  <NTabs v-model:value="activeTab" type="line" animated class="warehouse-tabs">
    <NTabPane name="warehouse" tab="位置管理" display-directive="show">
      <HPageDataTable
        auto-scroll-x
        class="collection-data-page"
        :title="typeof route.query.title === 'string' && route.query.title.trim() ? route.query.title : '库房与保管日记'"
        ref="tableRef"
        :columns="columns"
        :fetch-data="fetchData"
        :params="queryParams"
        :fun-api-delete="warehouseApi.delete"
        is-tree
        :pagination="false"
        row-key-field="id"
        @add="onAdd"
        @rowEdit="onEdit"
        @row-view="onView"
      >
        <template #toolbar-extra>
          <div class="collection-toolbar-group">
            <NButton v-if="queryParams.sceneType === 'STORAGE'" size="small" type="primary" @click="onLogAdd">
              登记今日巡库
            </NButton>
            <NDropdown
              trigger="click"
              placement="bottom-end"
              :options="warehouseStaffOptions"
              @select="handleWarehouseToolSelect"
            >
              <NButton size="small" secondary>
                人员与值班<NIcon style="margin-left: 4px"><IconMdiMenuDown /></NIcon>
              </NButton>
            </NDropdown>
            <NDropdown
              trigger="click"
              placement="bottom-end"
              :options="warehouseMaintenanceOptions"
              @select="handleWarehouseToolSelect"
            >
              <NButton size="small" secondary :loading="checkingLocations">
                库位维护<NIcon style="margin-left: 4px"><IconMdiMenuDown /></NIcon>
              </NButton>
            </NDropdown>
          </div>
        </template>

        <template #queryForm>
          <NFormItem label="位置场景">
            <NRadioGroup
              v-model:value="queryParams.sceneType"
              size="small"
              style="width: auto; min-width: max-content"
              @update:value="onQuerySceneChange"
            >
              <NRadioButton value="STORAGE">库房与保管位置</NRadioButton>
              <NRadioButton value="EXHIBITION">展厅与展陈位置</NRadioButton>
            </NRadioGroup>
          </NFormItem>
          <NFormItem v-if="selectedWarehouseId" label="库区范围">
            <NTag
              closable
              @close="
                selectedWarehouseId = '';
                tableRef?.handleQuery();
              "
              >当前库区</NTag
            >
          </NFormItem>
          <NFormItem label="检索内容">
            <NInput v-model:value="queryParams.keyword" clearable placeholder="位置名称 / 编码 / 温湿度" />
          </NFormItem>

          <NFormItem label="位置类型">
            <HDictSelect
              v-if="queryLocationTypeDict"
              v-model:value="queryParams.type"
              :type="queryLocationTypeDict"
              clearable
              placeholder="类型"
            />
            <NSelect v-else disabled placeholder="请先选择场景" />
          </NFormItem>
        </template>

        <!-- 新增 / 编辑表单 -->
        <HPageForm
          v-model:show="formShow"
          :title="formType === 'add' ? '新增位置' : '编辑位置'"
          :model="form"
          :rules="rules"
          :ref-table="tableRef"
          :handle-submit="handleSubmit"
          label-placement="left"
          label-width="80"
        >
          <NFormItem label="上级位置" path="parentId">
            <WarehouseTreeSelect
              v-model:value="form.parentId"
              :excluded-id="form.id"
              :scene-type="form.sceneType"
              placeholder="顶级留空"
            />
          </NFormItem>
          <NFormItem label="名称" path="name"
            ><NInput v-model:value="form.name" :maxlength="100" show-count placeholder="如 一号库房 / 临展厅 / 3 号展柜"
          /></NFormItem>
          <NFormItem label="缩略图" path="thumbnail">
            <HUploadInput
              v-model:value="form.thumbnail"
              accept="image/*"
              clearable
              placeholder="上传库房、库位或展柜缩略图"
            />
          </NFormItem>
          <NFormItem label="场景" path="sceneType">
            <HDictSelect
              v-model:value="form.sceneType"
              type="col_location_scene"
              placeholder="位置场景"
              @update:value="onLocationSceneChange"
            />
          </NFormItem>
          <NFormItem :label="locationTypeLabel(form.sceneType)" path="type"
            ><HDictSelect
              v-model:value="form.type"
              :type="formLocationTypeDict"
              placeholder="位置类型"
              @update:value="onLocationTypeChange"
          /></NFormItem>
          <NFormItem label="编码" path="code"
            ><NInput v-model:value="form.code" :maxlength="64" placeholder="柜架/层格/囊匣使用 N.N.N，如 1.2.3"
          /></NFormItem>
          <NFormItem label="容量" path="capacity"
            ><NInputNumber v-model:value="form.capacity" :min="0" style="width: 160px"
          /></NFormItem>
          <NFormItem label="温湿度" path="temperature"
            ><NInput v-model:value="form.temperature" :maxlength="32" placeholder="如 18-22℃ / 45-55%RH"
          /></NFormItem>
          <NFormItem label="保管责任人备注" path="custodian"
            ><NInput v-model:value="form.custodian" :maxlength="100" placeholder="姓名 / 岗位"
          /></NFormItem>
          <NFormItem label="排序" path="sort"
            ><NInputNumber v-model:value="form.sort" :min="0" style="width: 160px"
          /></NFormItem>
        </HPageForm>

        <!-- 详情 -->
        <HPageForm content-only :enable-cancel="false" v-model:show="viewShow" title="位置详情">
          <NDescriptions v-if="viewRow" label-placement="left" :column="1" bordered size="small">
            <NDescriptionsItem label="名称">{{ viewRow.name || '--' }}</NDescriptionsItem>
            <NDescriptionsItem label="缩略图">
              <NImage v-if="viewRow.thumbnail" :src="fileUrl(viewRow.thumbnail)" width="160" object-fit="cover" />
              <span v-else>--</span>
            </NDescriptionsItem>
            <NDescriptionsItem label="场景"
              ><HDictText type="col_location_scene" :value="viewRow.sceneType"
            /></NDescriptionsItem>
            <NDescriptionsItem label="类型"
              ><HDictText type="col_warehouse_type" :value="viewRow.type"
            /></NDescriptionsItem>
            <NDescriptionsItem label="编码">{{ viewRow.code || '--' }}</NDescriptionsItem>
            <NDescriptionsItem label="容量">{{ viewRow.capacity ?? '--' }}</NDescriptionsItem>
            <NDescriptionsItem label="温湿度">{{ viewRow.temperature || '--' }}</NDescriptionsItem>
            <NDescriptionsItem label="保管责任人备注" content-style="white-space: pre-wrap; overflow-wrap: anywhere;">{{
              viewRow.custodian || '--'
            }}</NDescriptionsItem>
            <NDescriptionsItem label="排序">{{ viewRow.sort ?? '--' }}</NDescriptionsItem>
          </NDescriptions>
        </HPageForm>

        <NModal
          v-model:show="correctionShow"
          preset="card"
          title="误分类库房订正为展厅"
          style="width: min(640px, calc(100vw - 32px))"
        >
          <NAlert type="warning" :bordered="false" style="margin-bottom: 16px">
            原库房位置及采集历史将保留为退役记录；系统会新建展厅位置并归档本次订正依据。请先核实实际用途。
          </NAlert>
          <NForm label-placement="left" label-width="110">
            <NFormItem label="原位置">{{ correction.oldName }}（{{ correction.oldId }}）</NFormItem>
            <NFormItem label="新展厅名称" required>
              <NInput v-model:value="correction.name" placeholder="实际展厅名称" />
            </NFormItem>
            <NFormItem label="新位置编码">
              <NInput v-model:value="correction.code" placeholder="可选，建议填写唯一编码" />
            </NFormItem>
            <NFormItem label="订正原因" required>
              <NInput
                v-model:value="correction.reason"
                type="textarea"
                :rows="3"
                placeholder="说明原分类错误及实际展陈用途"
              />
            </NFormItem>
            <NFormItem label="签署依据" required>
              <HUploadInput
                v-model:value="correction.signedFileIds"
                @upload-finish="correction.signedFileIds = $event.id"
                clearable
              />
            </NFormItem>
          </NForm>
          <template #footer>
            <NSpace justify="end">
              <NButton @click="correctionShow = false">取消</NButton>
              <NButton type="primary" :loading="submittingCorrection" @click="submitCorrection">确认订正并归档</NButton>
            </NSpace>
          </template>
        </NModal>

        <NModal
          v-model:show="correctionReceiptShow"
          preset="card"
          title="位置订正归档结果"
          style="width: min(600px, calc(100vw - 32px))"
        >
          <NDescriptions v-if="correctionReceipt" label-placement="left" :column="1" bordered size="small">
            <NDescriptionsItem label="退役位置">{{ correctionReceipt.oldId }}</NDescriptionsItem>
            <NDescriptionsItem label="新展厅位置">{{ correctionReceipt.newId }}</NDescriptionsItem>
            <NDescriptionsItem label="永久档案">{{ correctionReceipt.archiveId }}</NDescriptionsItem>
          </NDescriptions>
          <NSpace v-if="correctionReceipt" justify="end" style="margin-top: 16px">
            <NButton @click="openCorrectionHistory(correctionReceipt.oldId)">查看订正档案</NButton>
            <HFileList
              :files="[{ fileId: correctionReceipt.fileId, name: '本次签署依据原件' }]"
              :original-url="
                (fileId: string) =>
                  correctionReceipt
                    ? warehouseApi.correctionOriginalUrl(correctionReceipt.oldId, correctionReceipt.archiveId, fileId)
                    : undefined
              "
              :revision="correctionReceipt"
              show-download-btn
            />
          </NSpace>
        </NModal>

        <NModal
          v-model:show="correctionHistoryShow"
          preset="card"
          title="位置订正档案"
          style="width: min(680px, calc(100vw - 32px))"
        >
          <NSpace align="center" style="margin-bottom: 16px">
            <NInput v-model:value="historyLookupId" placeholder="输入退役前的原位置 ID" style="width: 280px" />
            <NButton :loading="loadingCorrectionHistory" @click="loadCorrectionHistory">查询</NButton>
          </NSpace>
          <NEmpty v-if="loadedHistoryId && !correctionHistory.length" description="此位置没有订正档案" />
          <NDescriptions
            v-for="archive in correctionHistory"
            :key="archive.id"
            label-placement="left"
            :column="1"
            bordered
            size="small"
            style="margin-bottom: 12px"
          >
            <NDescriptionsItem label="原位置">{{ loadedHistoryId }}</NDescriptionsItem>
            <NDescriptionsItem label="新展厅">{{
              correctionHistorySnapshot(archive).replacementLocationId || '--'
            }}</NDescriptionsItem>
            <NDescriptionsItem label="订正原因" content-style="white-space: pre-wrap; overflow-wrap: anywhere;">{{
              correctionHistorySnapshot(archive).reason || '--'
            }}</NDescriptionsItem>
            <NDescriptionsItem label="永久档案">{{ archive.id }}</NDescriptionsItem>
            <NDescriptionsItem label="档案摘要">{{ archive.digest }}</NDescriptionsItem>
            <NDescriptionsItem label="签署依据">
              <NSpace>
                <HFileList
                  :files="archive.files"
                  :original-url="
                    (fileId: string) => warehouseApi.correctionOriginalUrl(loadedHistoryId, archive.id, fileId)
                  "
                  :revision="[archive?.id, archive?.digest]"
                  show-download-btn
                />
              </NSpace>
            </NDescriptionsItem>
          </NDescriptions>
        </NModal>

        <NModal
          v-model:show="importShow"
          preset="card"
          title="批量导入库房库位"
          style="width: min(920px, calc(100vw - 32px))"
        >
          <div class="warehouse-import-body">
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

            <div class="import-section">
              <div class="import-section-title">
                <span>导入列说明</span>
                <NText depth="3">上级填写现有库位名称或完整路径，也可填编码；缩略图填写已上传的唯一文件名</NText>
              </div>
              <NDataTable
                :scroll-x="670"
                :columns="templateColumns"
                :data="importTemplate"
                size="small"
                :bordered="false"
                :max-height="200"
              />
            </div>

            <div class="import-section">
              <div class="import-section-title">
                <span>校验结果</span>
                <NText depth="3">{{ importErrors.length ? `${importErrors.length} 条错误` : '暂无错误' }}</NText>
              </div>
              <NEmpty
                v-if="!importRows.length && !importErrors.length"
                description="选择文件后将显示解析与校验结果"
                size="small"
              />
              <NDataTable
                :scroll-x="480"
                v-if="importErrors.length"
                :columns="importErrorColumns"
                :data="importErrors"
                size="small"
                :bordered="false"
                :max-height="150"
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

        <NModal
          v-model:show="locationCheckShow"
          preset="card"
          title="库位一致性核查"
          style="width: min(900px, calc(100vw - 32px))"
        >
          <NAlert
            :type="locationCheckResult?.issues.length ? 'warning' : 'success'"
            :bordered="false"
            style="margin-bottom: 12px"
          >
            已核对 {{ locationCheckResult?.checked || 0 }} 件在库藏品，匹配
            {{ locationCheckResult?.matched || 0 }} 件，异常 {{ locationCheckResult?.issues.length || 0 }} 件。
          </NAlert>
          <NDataTable
            :scroll-x="770"
            :columns="locationIssueColumns"
            :data="locationCheckResult?.issues || []"
            size="small"
            :bordered="false"
            :max-height="420"
          />
        </NModal>
      </HPageDataTable>
    </NTabPane>

    <NTabPane name="log" tab="日常巡库与日记" display-directive="show:lazy">
      <HPageDataTable
        auto-scroll-x
        class="collection-data-page"
        title="日常巡库与日记"
        :is-add="false"
        ref="logTableRef"
        :columns="logColumns"
        :fetch-data="warehouseLogApi.page"
        :params="logQueryParams"
        :is-delete="false"
        row-key-field="id"
        @add="onLogAdd"
        @rowEdit="onLogEdit"
        @row-view="onLogEdit"
      >
        <template #queryForm>
          <NFormItem label="保管库房">
            <WarehouseTreeSelect v-model:value="logQueryParams.warehouseId" placeholder="全部库房" />
          </NFormItem>
          <NFormItem label="检索内容">
            <NInput v-model:value="logQueryParams.keyword" clearable placeholder="事件内容" />
          </NFormItem>
          <NFormItem label="记录日期">
            <NDatePicker v-model:value="logDateRange" type="daterange" clearable @update:value="syncLogDateRange" />
          </NFormItem>
        </template>

        <template #toolbar-extra
          ><NSpace
            ><NButton type="primary" size="small" @click="onLogAdd">登记今日巡库</NButton
            ><NButton size="small" @click="gapsRef?.open()">核对每日记录缺口</NButton></NSpace
          ></template
        >
        <WarehouseDiaryDrawer ref="diaryRef" @saved="logTableRef?.handleRefresh?.()" />
        <WarehouseDiaryGaps ref="gapsRef" />
      </HPageDataTable>
    </NTabPane>
  </NTabs>
  <WarehouseSecurityDutyDrawer ref="securityDutyRef" />
  <WarehouseStaffingDrawer ref="staffingRef" @saved="tableRef?.handleRefresh?.()" />
</template>

<script setup lang="ts">
import '../styles/collection-page.css';
import { useRoute } from 'vue-router';
import { useCollectionSearchRecord } from '../composables/useCollectionSearchRecord';
import { nextTick as searchNextTick } from 'vue';
import { computed, h, reactive, ref, watch } from 'vue';
import { useAuthStore } from '@/store/modules/auth';
import { useCollectionEntryAction } from '../composables/useCollectionEntryAction';
import { NButton, NImage, NSelect, NTag, useMessage } from 'naive-ui';
import { cloneDeep } from 'lodash-es';
import * as XLSX from 'xlsx';
import { fileUrl } from '@/utils';
import { type as fetchDictType } from '@/modules/sys/api/dict';
import { warehouseApi } from '../api/warehouse';
import { warehouseLogApi } from '../api/warehouseLog';
import WarehouseStaffingDrawer from '../components/WarehouseStaffingDrawer.vue';
import WarehouseSecurityDutyDrawer from '../components/WarehouseSecurityDutyDrawer.vue';
import WarehouseDiaryDrawer from '../components/WarehouseDiaryDrawer.vue';
import WarehouseDiaryGaps from '../components/WarehouseDiaryGaps.vue';
import WarehouseTreeSelect from '../components/WarehouseTreeSelect.vue';
import { formatImportDictOptions, formatImportSample } from '../utils/importTemplateDisplay';
import { applyLegacyImportHeaders } from '../utils/importHeaderCompatibility';
import { filterWarehouseRows } from '../utils/warehouseTree';
import type {
  CollectionImportErrorVo,
  CollectionImportTemplateColumnVo,
  WarehouseImportRowVo,
  WarehouseLocationCheckVo,
  WarehouseLogVo,
  WarehouseVo,
} from '../typings/collection';

const message = useMessage();
const authStore = useAuthStore();
const activeTab = ref('warehouse');
const tableRef = ref<any>(null);
const route = useRoute();
const selectedWarehouseId = ref(typeof route.query.warehouseId === 'string' ? route.query.warehouseId : '');
watch(
  () => route.query.warehouseId,
  () => {
    if (route.path !== '/collection/warehouse') return;
    selectedWarehouseId.value = typeof route.query.warehouseId === 'string' ? route.query.warehouseId : '';
    tableRef.value?.handleQuery();
  },
  { flush: 'post' },
);
const queryParams = reactive({
  keyword: null as string | null,
  sceneType: 'STORAGE' as string | null,
  type: null as string | null,
});
const checkingLocations = ref(false);
const warehouseStaffOptions = [
  { label: '库管任职与交接', key: 'staffing' },
  { label: '库区安全值班', key: 'security-duty' },
];
const canReadCorrectionHistory = computed(() =>
  authStore.hasPermission('ADMIN,COLLECTION_ADMIN,COLLECTION_WAREHOUSE_MANAGE,COLLECTION_SELECT'),
);
const warehouseMaintenanceOptions = computed(() => [
  { label: '批量导入', key: 'import' },
  { label: '一键核对库位', key: 'check-locations', disabled: checkingLocations.value },
  ...(canReadCorrectionHistory.value ? [{ label: '订正档案', key: 'correction-history' }] : []),
]);
async function handleWarehouseToolSelect(key: string) {
  if (key === 'staffing') staffingRef.value?.open();
  if (key === 'security-duty') securityDutyRef.value?.open();
  if (key === 'import') await openImport();
  if (key === 'check-locations' && !checkingLocations.value) await checkLocations();
  if (key === 'correction-history' && canReadCorrectionHistory.value) openCorrectionHistory();
}
const locationCheckShow = ref(false);
const locationCheckResult = ref<WarehouseLocationCheckVo | null>(null);
const locationIssueColumns = [
  { ellipsis: { tooltip: true }, resizable: true, title: '总登记号', key: 'totalRegNo', width: 150 },
  { ellipsis: { tooltip: true }, resizable: true, width: 180, title: '藏品名称', key: 'collectionName', minWidth: 180 },
  {
    ellipsis: { tooltip: true },
    resizable: true,
    width: 180,
    title: '台账库位ID',
    key: 'warehouseId',
    minWidth: 180,
    render: (row: any) => row.warehouseId || '--',
  },
  { ellipsis: { tooltip: true }, resizable: true, width: 260, title: '异常原因', key: 'reason', minWidth: 260 },
];

async function checkLocations() {
  checkingLocations.value = true;
  try {
    const resp: any = await warehouseApi.locationCheck();
    locationCheckResult.value = resp?.data ?? resp;
    locationCheckShow.value = true;
  } finally {
    checkingLocations.value = false;
  }
}

// ── 库房日志页签 ──
const logTableRef = ref<any>(null);
const logQueryParams = reactive({
  warehouseId: null as string | null,
  keyword: null as string | null,
  logDateStart: null as number | null,
  logDateEnd: null as number | null,
});
const logDateRange = ref<[number, number] | null>(null);
const securityDutyRef = ref<any>();
const staffingRef = ref<any>(),
  diaryRef = ref<any>(),
  gapsRef = ref<any>();
useCollectionEntryAction(
  '/collection/warehouse',
  'staffing',
  () => authStore.hasPermission('COLLECTION_ADMIN,COLLECTION_WAREHOUSE_MANAGE'),
  () => staffingRef.value?.open(),
);
const logColumns = [
  {
    title: '原件核对',
    key: 'evidenceIssue',
    width: 320,
    render: (row: WarehouseLogVo) => row.evidenceIssue
      ? h('div', { style: 'display:flex;flex-direction:column;gap:6px;white-space:normal;overflow-wrap:anywhere' }, [
          h(NTag, { type: 'warning', size: 'small', bordered: false }, () => '原件待核查'),
          h('span', row.evidenceIssue),
        ])
      : '可查阅',
  },
  { title: '日期', key: 'logDate', width: 120, columnType: 'date', fixed: 'left' as const },
  {
    ellipsis: { tooltip: true },
    resizable: true,
    title: '库房',
    key: 'warehouseName',
    width: 160,
    render: (r: WarehouseLogVo) => r.warehouseName || (r.warehouseId ? r.warehouseId : '整库/通用'),
  },
  {
    resizable: true,
    width: 320,
    title: '事件内容',
    key: 'content',
    minWidth: 320,
    columnType: 'title',
    ellipsis: { tooltip: true },
  },
  {
    ellipsis: { tooltip: true },
    resizable: true,
    title: '记录人',
    key: 'createByName',
    width: 120,
    render: (r: WarehouseLogVo) => r.createByName || r.createBy || '--',
  },
  { title: '记录时间', key: 'createTime', width: 180, columnType: 'dateTime' },
];

function syncLogDateRange(value: [number, number] | null) {
  logQueryParams.logDateStart = value?.[0] ?? null;
  logQueryParams.logDateEnd = value?.[1] ?? null;
}

async function onLogAdd() {
  activeTab.value = 'log';
  await searchNextTick();
  diaryRef.value?.open(undefined, { warehouseId: selectedWarehouseId.value || undefined, logDate: Date.now() });
}
function onLogEdit(row: WarehouseLogVo) {
  diaryRef.value?.open(row.id);
}

const formShow = ref(false);
const formType = ref<'add' | 'edit'>('add');
const defaultForm: WarehouseVo = { sort: 0, sceneType: 'STORAGE', type: 'WAREHOUSE' };
const form = ref<WarehouseVo>(cloneDeep(defaultForm));

const viewShow = ref(false);
const viewRow = ref<WarehouseVo | null>(null);
const correctionShow = ref(false);
const correctionReceiptShow = ref(false);
const correctionReceipt = ref<{ oldId: string; newId: string; archiveId: string; fileId: string } | null>(null);
const submittingCorrection = ref(false);
const correction = reactive({ oldId: '', oldName: '', name: '', code: '', reason: '', signedFileIds: '' });
type CorrectionArchive = { id: string; digest: string; snapshot: string; files: { fileId: string }[] };
const correctionHistoryShow = ref(false);
const loadingCorrectionHistory = ref(false);
const historyLookupId = ref('');
const loadedHistoryId = ref('');
const correctionHistory = ref<CorrectionArchive[]>([]);

function correctionHistorySnapshot(archive: CorrectionArchive): { replacementLocationId?: string; reason?: string } {
  try {
    return JSON.parse(archive.snapshot || '{}');
  } catch {
    return {};
  }
}

function openCorrectionHistory(oldId = '') {
  historyLookupId.value = oldId;
  loadedHistoryId.value = '';
  correctionHistory.value = [];
  correctionHistoryShow.value = true;
  if (oldId) void loadCorrectionHistory();
}

async function loadCorrectionHistory() {
  const oldId = historyLookupId.value.trim();
  if (!oldId) {
    message.warning('请输入退役前的原位置 ID');
    return;
  }
  loadedHistoryId.value = '';
  correctionHistory.value = [];
  loadingCorrectionHistory.value = true;
  try {
    const resp: any = await warehouseApi.corrections(oldId);
    correctionHistory.value = resp?.data ?? resp ?? [];
    loadedHistoryId.value = oldId;
  } finally {
    loadingCorrectionHistory.value = false;
  }
}

function openCorrection(row: WarehouseVo) {
  if (!row.id) return;
  Object.assign(correction, {
    oldId: row.id,
    oldName: row.name || '',
    name: row.name || '',
    code: '',
    reason: '',
    signedFileIds: '',
  });
  correctionShow.value = true;
}

async function submitCorrection() {
  if (!correction.oldId || !correction.name.trim() || !correction.reason.trim() || !correction.signedFileIds) {
    message.warning('请填写新展厅名称、订正原因并上传签署依据');
    return;
  }
  submittingCorrection.value = true;
  try {
    const resp: any = await warehouseApi.retireAsExhibition(correction.oldId, {
      replacement: {
        name: correction.name.trim(),
        code: correction.code.trim() || undefined,
        parentId: '0',
        sceneType: 'EXHIBITION',
        type: 'EXHIBITION_HALL',
      },
      reason: correction.reason.trim(),
      signedFileIds: correction.signedFileIds,
    });
    const result = resp?.data ?? resp;
    correctionReceipt.value = {
      oldId: result.retiredLocationId,
      newId: result.replacementLocationId,
      archiveId: result.archiveId,
      fileId: correction.signedFileIds,
    };
    message.success('位置已订正，新展厅已建立；原位置和采集历史保留');
    correctionShow.value = false;
    correctionReceiptShow.value = true;
    tableRef.value?.handleRefresh?.();
  } finally {
    submittingCorrection.value = false;
  }
}

const rules = {
  name: { required: true, message: '请输入名称', trigger: ['blur', 'input'] },
  sceneType: { required: true, message: '请选择位置场景', trigger: ['blur', 'change'] },
  type: { required: true, message: '请选择位置类型', trigger: ['blur', 'change'] },
  code: {
    validator: (_rule: any, value: string | null) =>
      !['CABINET', 'LAYER', 'BOX'].includes(String(form.value.type || '')) ||
      /^[1-9]\d*\.[1-9]\d*\.[1-9]\d*$/.test(String(value || '')),
    message: '柜架、层格和囊匣编码须为 N.N.N 三段数字格式',
    trigger: ['blur', 'input'],
  },
};

// warehouseApi.list 返回当前可见范围全量；基础库位页在前端做轻量筛选。
async function fetchData() {
  const resp: any = await warehouseApi.list();
  const list: WarehouseVo[] = resp?.data?.list ?? resp?.data ?? [];
  const keyword = queryParams.keyword?.trim();
  const descendants = new Set<string>(selectedWarehouseId.value ? [selectedWarehouseId.value] : []);
  for (let expanded = true; expanded; ) {
    expanded = false;
    for (const row of list)
      if (row.id && row.parentId && descendants.has(row.parentId) && !descendants.has(row.id)) {
        descendants.add(row.id);
        expanded = true;
      }
  }
  const filtered = filterWarehouseRows(list, (row) => {
    const matchesKeyword =
      !keyword ||
      [row.name, row.code, row.temperature, row.custodian].some((value) => String(value || '').includes(keyword));
    const matchesType = !queryParams.type || row.type === queryParams.type;
    const matchesScene = !queryParams.sceneType || row.sceneType === queryParams.sceneType;
    return (
      matchesKeyword && matchesType && matchesScene && (!selectedWarehouseId.value || descendants.has(row.id || ''))
    );
  });
  return { ...resp, data: filtered };
}

const columns = [
  {
    resizable: true,
    width: 200,
    title: '名称',
    key: 'name',
    minWidth: 200,
    columnType: 'title',
    fixed: 'left',
    ellipsis: { tooltip: true },
  },
  {
    ellipsis: { tooltip: true },
    resizable: true,
    title: '缩略图',
    key: 'thumbnail',
    width: 86,
    align: 'center',
    columnType: 'image',
  },
  {
    ellipsis: { tooltip: true },
    resizable: true,
    title: '场景',
    key: 'sceneType',
    width: 90,
    dictType: 'col_location_scene',
  },
  {
    ellipsis: { tooltip: true },
    resizable: true,
    title: '类型',
    key: 'type',
    width: 110,
    dictType: 'col_warehouse_type',
  },
  { ellipsis: { tooltip: true }, resizable: true, title: '编码', key: 'code', width: 140 },
  { title: '容量', key: 'capacity', width: 90 },
  { ellipsis: { tooltip: true }, resizable: true, title: '温湿度', key: 'temperature', width: 220 },
  { ellipsis: { tooltip: true }, resizable: true, title: '保管责任人', key: 'custodian', width: 130 },
  {
    title: '订正',
    key: 'correction',
    width: 110,
    render: (row: WarehouseVo) =>
      row.id &&
      row.sceneType === 'STORAGE' &&
      ['WAREHOUSE', 'AREA'].includes(String(row.type || '')) &&
      authStore.hasPermission('ADMIN,COLLECTION_ADMIN,COLLECTION_WAREHOUSE_MANAGE')
        ? h(NButton, { text: true, type: 'primary', onClick: () => openCorrection(row) }, () => '误分类订正')
        : null,
  },
];

function onAdd() {
  formType.value = 'add';
  form.value = cloneDeep(defaultForm);
  form.value.sceneType = queryParams.sceneType || 'STORAGE';
  form.value.type = form.value.sceneType === 'EXHIBITION' ? 'EXHIBITION_HALL' : 'WAREHOUSE';
  formShow.value = true;
}
function onEdit(row: WarehouseVo) {
  formType.value = 'edit';
  form.value = { ...cloneDeep(defaultForm), ...cloneDeep(row) };
  formShow.value = true;
}
function onView(row: WarehouseVo) {
  viewRow.value = row;
  viewShow.value = true;
}

const exhibitionTypes = new Set(['EXHIBITION_HALL', 'DISPLAY_AREA', 'SHOWCASE']);
const formLocationTypeDict = computed(() =>
  form.value.sceneType === 'EXHIBITION' ? 'col_exhibition_location_type' : 'col_storage_location_type',
);
const queryLocationTypeDict = computed(() => {
  if (queryParams.sceneType === 'STORAGE') return 'col_storage_location_type';
  if (queryParams.sceneType === 'EXHIBITION') return 'col_exhibition_location_type';
  return null;
});

function locationTypeLabel(sceneType?: string | null) {
  if (sceneType === 'STORAGE') return '保管类型';
  if (sceneType === 'EXHIBITION') return '展陈类型';
  return '类型';
}

function onQuerySceneChange() {
  queryParams.type = null;
}

function onLocationSceneChange(sceneType: string | null) {
  form.value.parentId = undefined;
  if (sceneType === 'EXHIBITION' && !exhibitionTypes.has(String(form.value.type || ''))) {
    form.value.type = 'EXHIBITION_HALL';
  }
  if (sceneType === 'STORAGE' && exhibitionTypes.has(String(form.value.type || ''))) {
    form.value.type = 'WAREHOUSE';
  }
}

function onLocationTypeChange(type: string | null) {
  if (!type) return;
  const nextScene = exhibitionTypes.has(type) ? 'EXHIBITION' : 'STORAGE';
  if (form.value.sceneType !== nextScene) form.value.parentId = undefined;
  form.value.sceneType = nextScene;
}
function handleSubmit() {
  return form.value.id ? warehouseApi.update(form.value.id, { ...form.value }) : warehouseApi.create({ ...form.value });
}

const importShow = ref(false);
const importTemplate = ref<CollectionImportTemplateColumnVo[]>([]);
const importErrors = ref<CollectionImportErrorVo[]>([]);
const importRows = ref<WarehouseImportRowVo[]>([]);
const importDictOptions = ref<Record<string, { label: string; value: string }[]>>({});
const importFileInput = ref<HTMLInputElement | null>(null);
const importFileName = ref('');
const templateColumns = [
  { ellipsis: { tooltip: true }, resizable: true, title: '导入列', key: 'title', width: 160 },
  {
    title: '必填',
    key: 'required',
    width: 70,
    render: (r: CollectionImportTemplateColumnVo) => (r.required ? '是' : '否'),
  },
  {
    ellipsis: { tooltip: true },
    resizable: true,
    width: 260,
    title: '可选值',
    key: 'dictType',
    minWidth: 260,
    render: (r: CollectionImportTemplateColumnVo) => dictOptionText(r.dictType),
  },
  {
    ellipsis: { tooltip: true },
    resizable: true,
    width: 180,
    title: '示例',
    key: 'sample',
    minWidth: 180,
    render: (r: CollectionImportTemplateColumnVo) => importSampleText(r),
  },
];
const importErrorColumns = [
  { title: '行号', key: 'rowNo', width: 80 },
  { ellipsis: { tooltip: true }, resizable: true, title: '字段', key: 'field', width: 140 },
  { ellipsis: { tooltip: true }, resizable: true, width: 260, title: '错误', key: 'message', minWidth: 260 },
];

async function openImport() {
  if (!importTemplate.value.length) {
    const resp: any = await warehouseApi.importTemplate();
    importTemplate.value = resp?.data?.list ?? resp?.data ?? [];
  }
  await loadImportDictOptions();
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
  XLSX.utils.book_append_sheet(workbook, sheet, '库房库位导入模板');
  XLSX.writeFile(workbook, '库房库位批量导入模板.xlsx');
}

function normalizeImportValue(field: string, value: any) {
  const text = typeof value === 'string' ? value.trim() : value;
  if (text === '') return undefined;
  if (field === 'capacity' || field === 'sort') {
    const n = Number(text);
    return Number.isFinite(n) ? n : text;
  }
  return text;
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
  applyLegacyImportHeaders('warehouse', fieldByHeader);
  const rows = rawRows
    .map((raw, index) => {
      const row: Record<string, any> = { rowNo: index + 2 };
      Object.entries(raw).forEach(([header, value]) => {
        const field = fieldByHeader.get(header.trim());
        if (!field) return;
        const normalized = normalizeImportValue(field, value);
        if (normalized !== undefined) row[field] = normalized;
      });
      return row as WarehouseImportRowVo;
    })
    .filter((row) => Object.keys(row).some((key) => key !== 'rowNo'));
  importRows.value = rows;
  importErrors.value = [];
  input.value = '';
  message.success(`已解析 ${rows.length} 条导入数据`);
}

function parseImportRows(): WarehouseImportRowVo[] | null {
  if (!importRows.value.length) {
    message.warning('请先选择 Excel/CSV 文件');
    return null;
  }
  return importRows.value;
}

async function validateImport() {
  const rows = parseImportRows();
  if (!rows) return;
  const resp: any = await warehouseApi.validateImport(rows);
  const data = resp?.data ?? {};
  importErrors.value = data.errors ?? [];
  if (data.valid) message.success('校验通过');
  else message.warning('存在校验错误');
}

async function commitImport() {
  const rows = parseImportRows();
  if (!rows) return;
  const resp: any = await warehouseApi.commitImport(rows);
  const data = resp?.data ?? {};
  importErrors.value = data.errors ?? [];
  if (!data.valid) {
    message.warning('存在校验错误，未导入');
    return;
  }
  message.success(`已导入 ${data.createdIds?.length || 0} 条库房库位`);
  importShow.value = false;
  importRows.value = [];
  importFileName.value = '';
  tableRef.value?.handleRefresh();
}

useCollectionSearchRecord('/collection/warehouse', 'WAREHOUSE_LOG', async (id, current) => {
  activeTab.value = 'log';
  await searchNextTick();
  if (current()) await diaryRef.value?.open(id);
});
</script>

<style scoped>
.warehouse-tabs {
  display: flex;
  height: 100%;
  min-height: 0;
  flex-direction: column;
  overflow: hidden;
}

.warehouse-tabs :deep(.n-tabs-nav) {
  flex: none;
}

.warehouse-tabs :deep(.n-tabs-pane-wrapper),
.warehouse-tabs :deep(.n-tab-pane) {
  display: flex;
  min-height: 0;
  flex: 1;
  overflow: hidden;
}

.warehouse-import-body {
  display: flex;
  max-height: min(54vh, 500px);
  flex-direction: column;
  gap: 12px;
  overflow: hidden;
}

.import-upload-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.import-file-state {
  display: flex;
  min-width: 220px;
  flex-direction: column;
  gap: 2px;
  color: var(--text-color-3);
  font-size: 12px;
  text-align: right;
}

.import-file-state strong {
  color: var(--text-color-1);
  font-size: 13px;
}

.import-section {
  min-height: 0;
}

.import-section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 6px;
  font-weight: 600;
}
</style>
