<template>
  <HPageDataTable auto-scroll-x
    ref="tableRef"
    :columns="columns"
    :fetch-data="orgApi.page"
    :params="queryParams"
    :fun-api-delete="orgApi.delete"
    is-tree
    :pagination="false"
    row-key-field="id"
    @updateSort="handleUpdateSort"
    @add="onAdd"
    @rowEdit="onEdit"
    @row-view="onView"
  >
    <template #toolbar-extra>
      <NButton size="small" type="primary" secondary @click="openImport">批量导入</NButton>
    </template>

    <template #queryForm>
      <NFormItem label="检索内容">
        <NInput
          v-model:value="queryParams.keyword"
          placeholder="名称 / 代码 / 联系人 / 一普编码"
          clearable
        />
      </NFormItem>
      <NFormItem label="单位级别">
        <HDictSelect
          v-model:value="queryParams.level"
          type="col_org_level"
          clearable
          placeholder="级别"
        />
      </NFormItem>
      <NFormItem label="举办主体">
        <HDictSelect
          v-model:value="queryParams.ownership"
          type="col_ownership"
          clearable
          placeholder="主体"
        />
      </NFormItem>
      <NFormItem label="单位状态">
        <HDictSelect
          v-model:value="queryParams.status"
          type="col_org_status"
          clearable
          placeholder="状态"
        />
      </NFormItem>
    </template>

    <!-- 新增 / 编辑表单 -->
    <HPageForm
      v-model:show="formShow"
      :title="formType === 'add' ? '新增收藏单位' : '编辑收藏单位'"
      :model="form"
      :rules="rules"
      :ref-table="tableRef"
      :handle-submit="handleSubmit"
      label-placement="left"
      label-width="100"
    >
      <NGrid :cols="2" :x-gap="16">
        <NGridItem>
          <CollectionOrgCodeField :org-id="form.id" :active="formShow" v-model:value="form.code"
            v-model:state-hash="form.codeStateHash" @changed="tableRef?.handleRefresh()" />
        </NGridItem>
        <NFormItemGi label="名称" path="name"
          ><NInput v-model:value="form.name" placeholder="收藏单位名称"
        /></NFormItemGi>
        <NFormItemGi label="简称"><NInput v-model:value="form.shortName" placeholder="简称" /></NFormItemGi>
        <NFormItemGi label="上级单位">
          <NSelect
            v-model:value="form.parentId"
            :options="orgOptions"
            filterable
            clearable
            placeholder="总分馆：选择上级馆"
          />
        </NFormItemGi>
        <NFormItemGi
          label="授权机构"
          path="systemGroupId"
          :span="2"
        ><template #label><CollectionFieldLabel label="授权机构" help="多馆模式下，用于确定该机构及下属部门用户的新建业务归属和数据范围。各分馆应关联各自的系统机构。" /></template>
          <GroupSelect v-model:value="form.systemGroupId" placeholder="请选择对应的系统机构" />
        </NFormItemGi>
        <NFormItemGi
          label="关联安装位置"
          :span="2"
        ><template #label><CollectionFieldLabel label="关联安装位置" help="例如将收藏单位“一号楼”关联到库房库位树中的“一号楼”；设备登记时只展示该位置及其下级。" /></template>
          <WarehouseTreeSelect
            v-model:value="form.locationRootId"
            :scene-type="null"
            placeholder="可选：选择该单位对应的建筑或空间根"
          />
        </NFormItemGi>
        <NFormItemGi label="单位级别"
          ><HDictSelect v-model:value="form.level" type="col_org_level" clearable placeholder="级别"
        /></NFormItemGi>
        <NFormItemGi label="举办主体"
          ><HDictSelect v-model:value="form.ownership" type="col_ownership" clearable placeholder="举办主体"
        /></NFormItemGi>
        <NFormItemGi label="法定代表人"><NInput v-model:value="form.legalPerson" placeholder="法人" /></NFormItemGi>
        <NFormItemGi label="联系人"><NInput v-model:value="form.contact" placeholder="联系人" /></NFormItemGi>
        <NFormItemGi label="联系电话"><NInput v-model:value="form.phone" placeholder="电话" /></NFormItemGi>
        <NFormItem label="规范交换代码"><NInput v-model:value="form.exchangeCode" maxlength="14" placeholder="按原赋码资料填写14位机构代码" /></NFormItem>
        <NFormItem label="赋码依据"><NInput v-model:value="form.exchangeCodeBasis" placeholder="赋码文件、记录号及核实来源；不可由内部代码推算" /></NFormItem>
        <NFormItemGi label="一普编码"
          ><NInput v-model:value="form.sourceId" placeholder="第一次全国可移动文物普查机构编码"
        /></NFormItemGi>
        <NFormItemGi label="状态">
          <HDictSelect v-model:value="form.status" type="col_org_status" placeholder="状态" />
        </NFormItemGi>
        <NFormItemGi label="排序"><NInputNumber v-model:value="form.sort" :min="0" style="width: 100%" /></NFormItemGi>
        <NFormItemGi label="地址" :span="2"><NInput v-model:value="form.address" placeholder="单位地址" /></NFormItemGi>
      </NGrid>
    </HPageForm>

    <!-- 详情 -->
    <HPageForm content-only :enable-cancel="false" v-model:show="viewShow" title="收藏单位详情">
      <NDescriptions v-if="viewRow" label-placement="left" :column="2" bordered size="small">
        <NDescriptionsItem label="代码"><CollectionOrgCodeField :org-id="viewRow.id" :value="viewRow.code" :active="viewShow" readonly /></NDescriptionsItem>
        <NDescriptionsItem label="名称">{{ viewRow.name || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="简称">{{ viewRow.shortName || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="级别"><HDictText type="col_org_level" :value="viewRow.level" /></NDescriptionsItem>
        <NDescriptionsItem label="举办主体"
          ><HDictText type="col_ownership" :value="viewRow.ownership"
        /></NDescriptionsItem>
        <NDescriptionsItem label="法定代表人">{{ viewRow.legalPerson || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="联系人">{{ viewRow.contact || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="联系电话">{{ viewRow.phone || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="规范交换代码">{{ viewRow.exchangeCode || '待核实' }}</NDescriptionsItem>
        <NDescriptionsItem label="赋码依据">{{ viewRow.exchangeCodeBasis || '待核实' }}</NDescriptionsItem>
        <NDescriptionsItem label="一普编码">{{ viewRow.sourceId || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="授权机构" :span="2">
          <GroupSelect v-if="viewRow.systemGroupId" :value="viewRow.systemGroupId" disabled />
          <span v-else>未关联</span>
        </NDescriptionsItem>
        <NDescriptionsItem label="关联安装位置" :span="2">{{ viewRow.locationRootName || '--' }}</NDescriptionsItem>
        <NDescriptionsItem label="状态"><HDictText type="col_org_status" :value="viewRow.status" /></NDescriptionsItem>
        <NDescriptionsItem label="地址" :span="2">{{ viewRow.address || '--' }}</NDescriptionsItem>
      </NDescriptions>
    </HPageForm>

    <NModal v-model:show="importShow" preset="card" title="批量导入收藏单位" style="width: min(920px, calc(100vw - 32px))">
      <div class="org-import-body">
        <div class="import-upload-bar">
          <NSpace align="center">
            <NButton type="primary" secondary @click="openImportFile">选择 Excel/CSV</NButton>
            <NButton secondary :disabled="!importTemplate.length" @click="downloadImportTemplate">下载模板</NButton>
          </NSpace>
          <div class="import-file-state">
            <strong>{{ importFileName || '未选择文件' }}</strong>
            <span>{{ importRows.length ? `已解析 ${importRows.length} 条收藏单位` : '请选择模板文件后再预校验' }}</span>
          </div>
        </div>
        <input ref="importFileInput" type="file" accept=".xlsx,.xls,.csv" style="display: none" @change="onImportFileChange" />

        <div class="import-section">
          <div class="import-section-title">
            <span>导入列说明</span>
            <NText depth="3">上级单位和安装位置可填名称或完整路径；行政区划可填编码或名称路径</NText>
          </div>
          <NDataTable :scroll-x="670" :columns="templateColumns" :data="importTemplate" size="small" :bordered="false" :max-height="200" />
        </div>

        <div class="import-section">
          <div class="import-section-title">
            <span>校验结果</span>
            <NText depth="3">{{ importErrors.length ? `${importErrors.length} 条错误` : '暂无错误' }}</NText>
          </div>
          <NEmpty v-if="!importRows.length && !importErrors.length" description="选择文件后将显示解析与校验结果" size="small" />
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
  </HPageDataTable>
  <CollectionManagementRulesDrawer ref="managementRulesRef" />
  <PaperLedgerDrawer ref="paperLedgerRef" />
  <CollectionArchivePacketDrawer ref="archivePacketRef" />
</template>

<script setup lang="ts">
import CollectionFieldLabel from '../components/CollectionFieldLabel.vue';
import CollectionOrgCodeField from '../components/CollectionOrgCodeField.vue';
import { h, reactive, ref } from 'vue';
import { useAuthStore } from '@/store/modules/auth';
import { NButton, useMessage } from 'naive-ui';
import { cloneDeep } from 'lodash-es';
import * as XLSX from 'xlsx';
import { type as fetchDictType } from '@/modules/sys/api/dict';
import { orgApi } from '../api/org';
import WarehouseTreeSelect from '../components/WarehouseTreeSelect.vue';
import GroupSelect from '@/modules/sys/components/form/field/GroupSelect.vue';
import CollectionManagementRulesDrawer from '../components/CollectionManagementRulesDrawer.vue';
import PaperLedgerDrawer from '../components/PaperLedgerDrawer.vue';
import CollectionArchivePacketDrawer from '../components/CollectionArchivePacketDrawer.vue';
const authStore=useAuthStore();
const archivePacketRef=ref<any>();
const paperLedgerRef=ref<any>();
const managementRulesRef=ref<any>();
import { formatImportDictOptions, formatImportSample } from '../utils/importTemplateDisplay';
import { applyLegacyImportHeaders } from '../utils/importHeaderCompatibility';
import type { CollectionImportErrorVo, CollectionImportTemplateColumnVo, CollectionOrgImportRowVo, CollectionOrgVo } from '../typings/collection';

const tableRef = ref<any>(null);
const message = useMessage();
const queryParams = reactive({
  keyword: null as string | null,
  level: null as string | null,
  ownership: null as string | null,
  status: null as string | null,
  pageSize: 0,
});

const formShow = ref(false);
const formType = ref<'add' | 'edit'>('add');
const defaultForm: CollectionOrgVo = { status: 'ENABLED', sort: 0 };
const form = ref<CollectionOrgVo>(cloneDeep(defaultForm));
const orgOptions = ref<{ label: string; value: string }[]>([]);

const viewShow = ref(false);
const viewRow = ref<CollectionOrgVo | null>(null);

const rules = {
  code: { required: true, message: '请输入收藏单位代码', trigger: ['blur', 'input'] },
  name: { required: true, message: '请输入名称', trigger: ['blur', 'input'] },
};

const columns = [
  {title:'档案与行政备案',key:'archivePackets',width:150,render:(row:CollectionOrgVo)=>authStore.hasPermission('COLLECTION_ADMIN,COLLECTION_ARCHIVE_MANAGE') ? h(NButton,{text:true,type:'primary',onClick:()=>archivePacketRef.value?.open({orgId:row.id,orgName:row.name})},()=> '档案与备案') : null},
  {title:'纸账与财务核对',key:'paperLedger',width:160,render:(row:CollectionOrgVo)=>h(NButton,{text:true,type:'primary',onClick:()=>paperLedgerRef.value?.open({id:row.id,name:row.name})},()=> '纸账保管与核对')},
  { title: '制度及备案', key: 'managementRules', width: 130, render: (row: CollectionOrgVo) => h(NButton,{text:true,type:'primary',onClick:()=>managementRulesRef.value?.open({id:row.id,name:row.name})},()=> '制度与备案') },
  { resizable: true, width: 200, title: '名称', key: 'name', minWidth: 200, columnType: 'title', fixed: 'left', rowDrag: true, ellipsis: { tooltip: true } },
  { ellipsis: { tooltip: true }, resizable: true, title: '代码', key: 'code', width: 140 },
  { ellipsis: { tooltip: true }, resizable: true, title: '级别', key: 'level', width: 120, dictType: 'col_org_level' },
  { ellipsis: { tooltip: true }, resizable: true, title: '举办主体', key: 'ownership', width: 110, dictType: 'col_ownership' },
  { ellipsis: { tooltip: true }, resizable: true, title: '法人', key: 'legalPerson', width: 100 },
  { ellipsis: { tooltip: true }, resizable: true, title: '联系人', key: 'contact', width: 100 },
  { ellipsis: { tooltip: true }, resizable: true, title: '电话', key: 'phone', width: 130 },
  { resizable: true, title: '安装位置根', key: 'locationRootName', width: 180, ellipsis: { tooltip: true } },
  { ellipsis: { tooltip: true }, resizable: true, title: '状态', key: 'status', width: 80, dictType: 'col_org_status' },
];

async function loadOrgOptions() {
  const resp: any = await orgApi.list();
  const list: any[] = resp?.data?.list ?? resp?.data ?? [];
  orgOptions.value = list.map((o) => ({ label: o.name, value: o.id }));
}

function onAdd() {
  formType.value = 'add';
  form.value = cloneDeep(defaultForm);
  loadOrgOptions();
  formShow.value = true;
}
function onEdit(row: CollectionOrgVo) {
  formType.value = 'edit';
  form.value = { ...cloneDeep(defaultForm), ...cloneDeep(row) };
  loadOrgOptions();
  formShow.value = true;
}
function onView(row: CollectionOrgVo) {
  viewRow.value = row;
  viewShow.value = true;
}
function handleSubmit() {
  if (form.value.id && !form.value.codeStateHash) {
    message.warning('请等待收藏单位代码核对完成；加载失败时请点击重新加载');
    return Promise.reject(new Error('收藏单位代码尚未核对'));
  }
  return form.value.id ? orgApi.update(form.value.id, { ...form.value }) : orgApi.create({ ...form.value });
}

function handleUpdateSort(items: CollectionOrgVo[]) {
  const sorts = items
    .filter((item) => item.id)
    .map((item, index) => ({ id: item.id!, sort: index }));
  return orgApi.sort(sorts).finally(() => {
    tableRef.value?.handleRefresh();
  });
}

const importShow = ref(false);
const importTemplate = ref<CollectionImportTemplateColumnVo[]>([]);
const importErrors = ref<CollectionImportErrorVo[]>([]);
const importRows = ref<CollectionOrgImportRowVo[]>([]);
const importDictOptions = ref<Record<string, { label: string; value: string }[]>>({});
const importFileInput = ref<HTMLInputElement | null>(null);
const importFileName = ref('');
const templateColumns = [
  { ellipsis: { tooltip: true }, resizable: true, title: '导入列', key: 'title', width: 160 },
  { title: '必填', key: 'required', width: 70, render: (r: CollectionImportTemplateColumnVo) => (r.required ? '是' : '否') },
  { ellipsis: { tooltip: true }, resizable: true, width: 260, title: '可选值', key: 'dictType', minWidth: 260, render: (r: CollectionImportTemplateColumnVo) => dictOptionText(r.dictType) },
  { ellipsis: { tooltip: true }, resizable: true, width: 180, title: '示例', key: 'sample', minWidth: 180, render: (r: CollectionImportTemplateColumnVo) => importSampleText(r) },
];
const importErrorColumns = [
  { title: '行号', key: 'rowNo', width: 80 },
  { ellipsis: { tooltip: true }, resizable: true, title: '字段', key: 'field', width: 140 },
  { ellipsis: { tooltip: true }, resizable: true, width: 260, title: '错误', key: 'message', minWidth: 260 },
];

async function openImport() {
  if (!importTemplate.value.length) {
    const resp: any = await orgApi.importTemplate();
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
  XLSX.utils.book_append_sheet(workbook, sheet, '收藏单位导入模板');
  XLSX.writeFile(workbook, '收藏单位批量导入模板.xlsx');
}

function normalizeImportValue(field: string, value: any) {
  const text = typeof value === 'string' ? value.trim() : value;
  if (text === '') return undefined;
  if (field === 'sort') {
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
  applyLegacyImportHeaders('org', fieldByHeader);
  const rows = rawRows
    .map((raw, index) => {
      const row: Record<string, any> = { rowNo: index + 2 };
      Object.entries(raw).forEach(([header, value]) => {
        const field = fieldByHeader.get(header.trim());
        if (!field) return;
        const normalized = normalizeImportValue(field, value);
        if (normalized !== undefined) row[field] = normalized;
      });
      return row as CollectionOrgImportRowVo;
    })
    .filter((row) => Object.keys(row).some((key) => key !== 'rowNo'));
  importRows.value = rows;
  importErrors.value = [];
  input.value = '';
  message.success(`已解析 ${rows.length} 条导入数据`);
}

function parseImportRows(): CollectionOrgImportRowVo[] | null {
  if (!importRows.value.length) {
    message.warning('请先选择 Excel/CSV 文件');
    return null;
  }
  return importRows.value;
}

async function validateImport() {
  const rows = parseImportRows();
  if (!rows) return;
  const resp: any = await orgApi.validateImport(rows);
  const data = resp?.data ?? {};
  importErrors.value = data.errors ?? [];
  if (data.valid) message.success('校验通过');
  else message.warning('存在校验错误');
}

async function commitImport() {
  const rows = parseImportRows();
  if (!rows) return;
  const resp: any = await orgApi.commitImport(rows);
  const data = resp?.data ?? {};
  importErrors.value = data.errors ?? [];
  if (!data.valid) {
    message.warning('存在校验错误，未导入');
    return;
  }
  message.success(`已导入 ${data.createdIds?.length || 0} 条收藏单位`);
  importShow.value = false;
  importRows.value = [];
  importFileName.value = '';
  tableRef.value?.handleRefresh();
}
</script>

<style scoped>
.org-import-body {
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
