<template>
  <HPageDataTable export-label="导出明细（Excel）" class="collection-data-page" auto-scroll-x
    ref="tableRef"
    :columns="columns"
    :fetch-data="acquisitionApi.page"
    :params="queryParams"
    :is-edit="false"
    :is-delete="false"
    row-key-field="id"
    @row-view="onView"
    @add="onAdd"
  >
    <template #toolbar-extra>
      <CollectionActionMenu label="更多" v-permission="'COLLECTION_ADMIN,COLLECTION_ACQUIRE'">
        <NButton
          quaternary
          v-permission="'COLLECTION_ADMIN,COLLECTION_ACQUIRE'"
          size="small"
          @click="designatedRef?.open()"
          >指定接收登记与承接</NButton
        >
        <NButton
          quaternary
          v-permission="'COLLECTION_ADMIN,COLLECTION_ACQUIRE'"
          size="small"
          type="primary"
          @click="openImport"
          >批量导入</NButton
        >
      </CollectionActionMenu>
    </template>

    <template #queryForm>
      <NFormItem label="检索内容">
        <NInput v-model:value="queryParams.keyword" :placeholder="canReadConfidential ? '名称 / 登记号 / 年代 / 持有人 / 理由' : '名称 / 登记号 / 年代 / 持有人'" clearable />
      </NFormItem>
      <NFormItem label="文物分类">
        <NSelect v-model:value="queryParams.categoryId" :options="catOptions" filterable clearable placeholder="分类" />
      </NFormItem>
      <NFormItem label="来源主体">
        <NInput v-model:value="queryParams.holder" placeholder="卖方、捐赠人或移交方" clearable />
      </NFormItem>
      <NFormItem label="拟定级别">
        <HDictSelect v-model:value="queryParams.proposedLevel" type="col_relic_level" clearable placeholder="级别" />
      </NFormItem>
      <NFormItem label="征集方式">
        <HDictSelect v-model:value="queryParams.source" type="col_source" clearable placeholder="方式" />
      </NFormItem>
      <NFormItem label="审批状态">
        <HDictSelect v-model:value="queryParams.flowStatus" type="flow_biz_status" clearable placeholder="状态" />
      </NFormItem>
      <NFormItem v-if="canReadConfidential" label="附件影像">
        <HDictSelect v-model:value="queryParams.hasImage" type="col_presence" clearable placeholder="有无" />
      </NFormItem>
      <NFormItem label="登记状态">
        <HDictSelect v-model:value="queryParams.registered" type="col_registered" clearable placeholder="状态" />
      </NFormItem>
      <NFormItem label="申请时间">
        <NDatePicker v-model:value="createTimeRange" type="daterange" clearable @update:value="syncCreateTimeRange" />
      </NFormItem>
    </template>

    <FlowFormDrawer
      ref="flowDrawerRef"
      :fetch-fn="acquisitionApi.flow"
      :create-api="acquisitionApi.save"
      :save-api="saveFlowDraft"
      :submit-api="acquisitionApi.submit"
      :cancel-api="acquisitionApi.cancel"
      @success="refreshTable"
    />

    <NModal v-model:show="approvalShow" preset="card" title="征集审批进度" style="width: min(900px, calc(100vw - 32px))">
      <CollectionApprovalProgress v-if="approvalShow && approvalRecordId" :business-id="approvalRecordId" business-type="COLLECTION_ACQUISITION" :fetch-flow="acquisitionApi.flow" />
    </NModal>
    <DesignatedReceptionDrawer ref="designatedRef" @saved="refreshTable" />
    <AcquisitionComplianceDrawer ref="complianceRef" @saved="refreshTable" />
    <CollectionPrintPreviewModal v-model:show="printShow" :data="printData" />

    <NModal
      v-model:show="importShow"
      preset="card"
      title="批量导入征集申请"
      style="width: min(920px, calc(100vw - 32px))"
    >
      <div class="acquisition-import-body">
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
            <NText depth="3">分类填写名称或完整路径；附件填写已上传文件名，多文件用分号分隔；导入后生成征集草稿</NText>
          </div>
          <NDataTable :scroll-x="680"
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
          <NDataTable :scroll-x="480"
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
    <CollectionDisclosureDrawer ref="disclosureRef" />
  </HPageDataTable>
</template>

<script setup lang="ts">
import CollectionActionMenu from '../components/CollectionActionMenu.vue';
import '../styles/collection-page.css';
import { useCollectionSearchRecord } from '../composables/useCollectionSearchRecord';
import { useAuthStore } from '@/store/modules/auth';
import CollectionDisclosureDrawer from '../components/CollectionDisclosureDrawer.vue';

import AcquisitionComplianceDrawer from '../components/AcquisitionComplianceDrawer.vue';
import DesignatedReceptionDrawer from '../components/DesignatedReceptionDrawer.vue';
import { computed, h, onMounted, onDeactivated, reactive, ref, watch } from 'vue';
import CollectionApprovalProgress from '../components/CollectionApprovalProgress.vue';
const approvalRecordId = ref<string>();
const approvalShow = ref(false);
onDeactivated(() => { approvalShow.value = false; approvalRecordId.value = undefined; });
import { NButton, NTag, useMessage } from 'naive-ui';
import { cloneDeep } from 'lodash-es';
import * as XLSX from 'xlsx';
import { type as fetchDictType } from '@/modules/sys/api/dict';
import { acquisitionApi } from '../api/acquisition';
import { categoryApi } from '../api/category';
import CollectionPrintPreviewModal from '../components/CollectionPrintPreviewModal.vue';
import FlowFormDrawer from '../../flow/components/FlowFormDrawer.vue';
import { useRoute } from 'vue-router';
import { useFlowApproval } from '../../flow/composables/useFlowApproval';
import { presenceText, registeredText } from '../utils/dictRender';
import { formatImportDictOptions, formatImportSample } from '../utils/importTemplateDisplay';
import { applyLegacyImportHeaders } from '../utils/importHeaderCompatibility';
import { parseImportAmount, yuanImportColumn } from '../utils/moneyYuan';
import { normalizeCollectionPrintData } from '../utils/printFile';
import {
  exportAcquisitionItemRegister,
  loadAcquisitionItemRegisterData,
  printAcquisitionItemRegister,
} from '../utils/acquisitionRegisterForm';
import type {
  AcquisitionImportRowVo,
  AcquisitionVo,
  CollectionImportErrorVo,
  CollectionImportTemplateColumnVo,
  CollectionPrintVo,
} from '../typings/collection';

const message = useMessage();
const route = useRoute();
const tableRef = ref<any>(null);
const flowDrawerRef = ref<any>(null);
const complianceRef = ref<any>(null);
const designatedRef = ref<any>(null);
const { flowStatusColumn, handleTitleClick } = useFlowApproval({
  refTable: tableRef,
  drawerRef: flowDrawerRef,
  getLabel: (row: AcquisitionVo) => row.name || row.collectionName || row.id || '征集申请详情',
});

const queryParams = reactive({
  keyword: null as string | null,
  planId: (route.query.planId as string) || null,
  categoryId: null as string | null,
  holder: null as string | null,
  proposedLevel: null as string | null,
  source: null as string | null,
  flowStatus: null as string | null,
  hasImage: null as string | null,
  registered: null as string | null,
  createTimeStart: null as number | null,
  createTimeEnd: null as number | null,
});
const createTimeRange = ref<[number, number] | null>(null);

watch(
  () => route.query.planId,
  (planId) => {
    queryParams.planId = (planId as string) || null;
    tableRef.value?.handleQuery?.();
  },
);

const defaultForm: AcquisitionVo = {
  id: undefined,
  name: undefined,
  categoryId: undefined,
  era: undefined,
  texture: undefined,
  proposedLevel: undefined,
  source: undefined,
  holder: undefined,
  quantity: 1,
  unit: '件',
  acquireDate: null,
  proposedPrice: undefined,
  appraisedValue: undefined,
  reason: undefined,
  images: undefined,
};
const catOptions = ref<{ label: string; value: string }[]>([]);

const printShow = ref(false);
const printData = ref<CollectionPrintVo | null>(null);
const itemRegistering = ref(false);

function syncCreateTimeRange(value: [number, number] | null) {
  queryParams.createTimeStart = value?.[0] ?? null;
  queryParams.createTimeEnd = value?.[1] ?? null;
}

const disclosureRef = ref<any>();
const disclosureAuth = useAuthStore();
const canReadDesignated = computed(() => disclosureAuth.hasPermission('COLLECTION_ADMIN,COLLECTION_ACQUIRE'));
const canReadConfidential = computed(() =>
  disclosureAuth.hasPermission('COLLECTION_ADMIN,COLLECTION_ACQUIRE,COLLECTION_ACQUIRE_CONFIDENTIAL_SELECT') ||
  ['COLLECTION_STORAGE_HEAD', 'COLLECTION_DIRECTOR', 'COLLECTION_VICE_DIRECTOR', 'COLLECTION_APPRAISAL_COMMITTEE']
    .some((code) => disclosureAuth.hasRoleCode(code)),
);
const columns = [
  { resizable: true, width: 200, title: '拟征集名称', key: 'name', minWidth: 200, columnType: 'title', fixed: 'left', ellipsis: { tooltip: true } },
  {
    title: '原馆留存账',
    key: 'collectionEvidenceIssue',
    width: 300,
    render: (row: AcquisitionVo) => row.collectionEvidenceIssue
      ? h('div', { style: 'display:flex;flex-direction:column;gap:6px;white-space:normal;overflow-wrap:anywhere' }, [
          h(NTag, { type: 'warning', size: 'small', bordered: false }, () => '原馆原件待核查'),
          h('span', row.collectionEvidenceIssue),
        ])
      : '--',
  },
  { resizable: true,
    title: '所属计划',
    key: 'planTitle',
    width: 150,
    ellipsis: { tooltip: true },
    render: (r: AcquisitionVo) => r.planTitle || '--',
  },
  { resizable: true,
    title: '总登记号',
    key: 'collectionTotalRegNo',
    width: 140,
    ellipsis: { tooltip: true },
    render: (r: AcquisitionVo) => r.collectionTotalRegNo || '--',
  },
  { resizable: true, width: 140,
    title: '藏品名称',
    key: 'collectionName',
    minWidth: 140,
    ellipsis: { tooltip: true },
    render: (r: AcquisitionVo) => r.collectionName || '--',
  },
  { ellipsis: { tooltip: true }, resizable: true, title: '拟定级别', key: 'proposedLevel', width: 90, dictType: 'col_relic_level' },
  { ellipsis: { tooltip: true }, resizable: true, title: '征集方式', key: 'source', width: 100, dictType: 'col_source' },
  { title: '数量', key: 'quantity', width: 90, render: (r: AcquisitionVo) => r.quantity ? `${r.quantity}${r.unit || '件'}` : '--' },
  { resizable: true, title: '来源人/单位', key: 'holder', width: 140, ellipsis: { tooltip: true } },
  {
    title: '附件',
    key: 'images',
    width: 80,
    render: (r: AcquisitionVo) => canReadConfidential.value ? presenceText(r.images) : '受限',
  },
  {
    ...flowStatusColumn,
    width: 150, ellipsis: { tooltip: true }, resizable: true,
    render: (r: AcquisitionVo) => (r.designatedReceptionId ? '指定接收已核对' : flowStatusColumn.render?.(r)),
  },
  { title: '登账藏品', key: 'collectionId', width: 110, render: (r: AcquisitionVo) => registeredText(r.collectionId) },
  { title: '创建时间', key: 'createTime', width: 200, columnType: 'dateTime' },
  {
    title: '操作',
    key: 'operate',
    type: 'operate',
    width: 420,
    fixed: 'right' as const,
    render: (row: AcquisitionVo) => {
      const btns: any[] = [
        h(NButton, { text: true, type: 'primary', size: 'small', onClick: () => onView(row) }, () => '详情'),
      ];
      if (!row.designatedReceptionId) btns.push(h(NButton, { text: true, type: 'primary', size: 'small', style: 'margin-left:10px', onClick: () => { approvalRecordId.value = row.id; approvalShow.value = true; } }, () => '审批进度'));
      if (row.designatedReceptionId ? canReadDesignated.value : canReadConfidential.value) btns.push(
        h(
          NButton,
          {
            text: true,
            size: 'small',
            style: 'margin-left:10px',
            onClick: () =>
              row.designatedReceptionId
                ? designatedRef.value?.open(row.designatedReceptionId)
                : complianceRef.value?.open(row.id),
          },
          () => '法定材料',
        ),
      );
      if (!row.designatedReceptionId && disclosureAuth.hasPermission('COLLECTION_ADMIN,COLLECTION_PUBLISH'))
        btns.push(
          h(
            NButton,
            {
              text: true,
              size: 'small',
              style: 'margin-left:10px',
              onClick: () => disclosureRef.value?.open({ orgId: row.orgId, subject: 'ACQUISITION', subjectId: row.id }),
            },
            () => '公开核查与实发',
          ),
        );
      if (!row.designatedReceptionId && canReadConfidential.value)
        btns.push(
          h(
            NButton,
            { text: true, size: 'small', style: 'margin-left:10px', onClick: () => onPrint(row) },
            () => '打印',
          ),
        );
      if (!row.designatedReceptionId)
        btns.push(
          h(
            NButton,
            {
              text: true,
              size: 'small',
              style: 'margin-left:10px',
              disabled: itemRegistering.value,
              onClick: () => onItemRegister(row, 'print'),
            },
            () => '打印登记表',
          ),
          h(
            NButton,
            {
              text: true,
              size: 'small',
              style: 'margin-left:10px',
              disabled: itemRegistering.value,
              onClick: () => onItemRegister(row, 'export'),
            },
            () => '导出登记表',
          ),
        );
      return btns;
    },
  },
];

async function loadCats() {
  const resp: any = await categoryApi.list();
  const list: any[] = resp?.data?.list ?? resp?.data ?? [];
  catOptions.value = list.map((c) => ({ label: c.name, value: c.id }));
}
onMounted(loadCats);

function onAdd() {
  flowDrawerRef.value?.openCreate({
    title: '新增征集申请',
    businessType: 'COLLECTION_ACQUISITION',
    formData: { ...cloneDeep(defaultForm), planId: queryParams.planId || undefined },
  });
}

function onView(row: AcquisitionVo) {
  if (row.designatedReceptionId) {
    void designatedRef.value?.open(row.designatedReceptionId);
    return;
  }
  handleTitleClick(row);
}

async function onItemRegister(row: AcquisitionVo, mode: 'print' | 'export') {
  if (itemRegistering.value) return;
  if (!row.id) {
    message.error('征集单标识缺失，请刷新列表后重试');
    return;
  }
  itemRegistering.value = true;
  try {
    const data = await loadAcquisitionItemRegisterData(row.id);
    if (mode === 'print') printAcquisitionItemRegister(data);
    else exportAcquisitionItemRegister(data);
  } catch (error: any) {
    message.error(error?.message || '征集登记表生成失败');
  } finally {
    itemRegistering.value = false;
  }
}

async function onPrint(row: AcquisitionVo) {
  const resp: any = await acquisitionApi.print(row.id!);
  printData.value = normalizeAcquisitionPrintData(resp?.data ?? null, row);
  printShow.value = true;
}

function optionLabel(options: { label: string; value: string }[], value: string | null | undefined) {
  if (!value) return '';
  return options.find((option) => option.value === value)?.label || '';
}

function printFieldValue(data: CollectionPrintVo | null, pattern: RegExp) {
  return data?.fields?.find((field) => pattern.test(field.label || ''))?.value || '';
}

function collectionNameForPrint(data: CollectionPrintVo, row: AcquisitionVo) {
  if (row.collectionName) return row.collectionName;
  const collectionId = row.collectionId || printFieldValue(data, /登账藏品|入账藏品|关联藏品/);
  const collection = data.collections?.find((item) => item.id === collectionId || item.totalRegNo === collectionId);
  return collection?.name || row.collectionTotalRegNo || collection?.totalRegNo || '';
}

function normalizeAcquisitionPrintData(data: CollectionPrintVo | null, row: AcquisitionVo) {
  if (!data) return null;
  const normalized = normalizeCollectionPrintData(data);
  const categoryId = row.categoryId || printFieldValue(normalized, /文物分类|藏品分类/);
  const categoryName =
    row.categoryName ||
    optionLabel(catOptions.value, categoryId) ||
    normalized.collections?.find((item) => item.categoryName)?.categoryName ||
    '';
  const collectionName = collectionNameForPrint(normalized, row);
  const fields = normalized.fields.map((field) => {
    if (/文物分类|藏品分类/.test(field.label || '') && categoryName) {
      return { ...field, value: categoryName, dictType: undefined };
    }
    if (/登账藏品|入账藏品|关联藏品/.test(field.label || '') && collectionName) {
      return { ...field, value: collectionName, dictType: undefined };
    }
    return field;
  });
  return { ...normalized, fields };
}

function saveFlowDraft(id: string, data: Record<string, any>) {
  return acquisitionApi.save({ ...data, id });
}

function refreshTable() {
  tableRef.value?.handleRefresh();
  tableRef.value?.handleQuery?.();
}

const importShow = ref(false);
const importTemplate = ref<CollectionImportTemplateColumnVo[]>([]);
const importErrors = ref<CollectionImportErrorVo[]>([]);
const importRows = ref<AcquisitionImportRowVo[]>([]);
const importDictOptions = ref<Record<string, { label: string; value: string }[]>>({});
const importFileInput = ref<HTMLInputElement | null>(null);
const importFileName = ref('');
const templateColumns = [
  { ellipsis: { tooltip: true }, resizable: true, title: '导入列', key: 'title', width: 170 },
  {
    title: '必填',
    key: 'required',
    width: 70,
    render: (r: CollectionImportTemplateColumnVo) => (r.required ? '是' : '否'),
  },
  { ellipsis: { tooltip: true }, resizable: true, width: 260,
    title: '可选值',
    key: 'dictType',
    minWidth: 260,
    render: (r: CollectionImportTemplateColumnVo) => dictOptionText(r.dictType),
  },
  { ellipsis: { tooltip: true }, resizable: true, width: 180, title: '示例', key: 'sample', minWidth: 180, render: (r: CollectionImportTemplateColumnVo) => importSampleText(r) },
];
const importErrorColumns = [
  { title: '行号', key: 'rowNo', width: 80 },
  { ellipsis: { tooltip: true }, resizable: true, title: '字段', key: 'field', width: 140 },
  { ellipsis: { tooltip: true }, resizable: true, width: 260, title: '错误', key: 'message', minWidth: 260 },
];

async function openImport() {
  if (!importTemplate.value.length) {
    const resp: any = await acquisitionApi.importTemplate();
    importTemplate.value = (resp?.data?.list ?? resp?.data ?? []).map((col: CollectionImportTemplateColumnVo) =>
      yuanImportColumn(col, new Set(['proposedPrice', 'appraisedValue'])),
    );
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
  XLSX.utils.book_append_sheet(workbook, sheet, '征集导入模板');
  XLSX.writeFile(workbook, '征集入藏批量导入模板.xlsx');
}

function normalizeImportValue(field: string, value: any, header: string) {
  const text = typeof value === 'string' ? value.trim() : value;
  if (text === '') return undefined;
  if (field === 'quantity' || field === 'proposedPrice' || field === 'appraisedValue') {
    const n = Number(text);
    return field === 'quantity' ? (Number.isFinite(n) ? n : text) : parseImportAmount(text, header);
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
    if (['proposedPrice', 'appraisedValue'].includes(col.field)) fieldByHeader.set(col.title.replace('（元）', '（分）'), col.field);
  });
  applyLegacyImportHeaders('acquisition', fieldByHeader);
  let rows: AcquisitionImportRowVo[];
  try {
    rows = rawRows
      .map((raw, index) => {
        const row: Record<string, any> = { rowNo: index + 2 };
        Object.entries(raw).forEach(([header, value]) => {
          const field = fieldByHeader.get(header.trim());
          if (!field) return;
          const normalized = normalizeImportValue(field, value, header);
          if (normalized !== undefined) row[field] = normalized;
        });
        return row as AcquisitionImportRowVo;
      })
      .filter((row) => Object.keys(row).some((key) => key !== 'rowNo'));
  } catch (error: any) {
    message.error(error?.message || '金额须为非负整数元');
    return;
  }
  importRows.value = rows;
  importErrors.value = [];
  input.value = '';
  message.success(`已解析 ${rows.length} 条导入数据`);
}

function parseImportRows(): AcquisitionImportRowVo[] | null {
  if (!importRows.value.length) {
    message.warning('请先选择 Excel/CSV 文件');
    return null;
  }
  return importRows.value;
}

async function validateImport() {
  const rows = parseImportRows();
  if (!rows) return;
  const resp: any = await acquisitionApi.validateImport(rows);
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
  const resp: any = await acquisitionApi.commitImport(rows);
  const data = resp?.data ?? {};
  importErrors.value = data.errors ?? [];
  if (!data.valid) {
    message.warning('存在校验错误，未导入');
    return;
  }
  message.success(`已导入 ${data.createdIds?.length || 0} 条征集申请`);
  importShow.value = false;
  importRows.value = [];
  importFileName.value = '';
  tableRef.value?.handleRefresh();
}
useCollectionSearchRecord('/collection/acquisition', 'ACQUISITION', id => flowDrawerRef.value?.open(id));
</script>

<style scoped>
.acquisition-import-body {
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
