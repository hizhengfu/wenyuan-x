<template>
  <div class="location-page">
    <HPageDataTable
      ref="tableRef"
      title="采集位置"
      :columns="columns"
      :fetch-data="fetchLocations"
      :params="query"
      :scroll-x="1380"
      row-key-field="id"
      is-tree
      :pagination="false"
      :is-add="false"
      :is-edit="false"
      :is-copy="false"
      :is-delete="false"
      :is-export="false"
      :show-operate="false"
    >
      <template #queryForm>
        <NFormItem label="所属场馆">
          <VenueSelect v-model:value="query.orgId" clearable placeholder="全部可见场馆" style="width: 240px" />
        </NFormItem>
        <NFormItem label="关键字">
          <NInput
            v-model:value="query.keyword"
            clearable
            placeholder="名称 / 编码 / 路径"
            style="width: 230px"
            @keyup.enter="refreshTable"
          />
        </NFormItem>
        <NFormItem label="来源">
          <NSelect v-model:value="query.sourceType" :options="sourceTypeOptions" clearable style="width: 170px" />
        </NFormItem>
        <NFormItem label="显示已停用">
          <NSwitch v-model:value="query.includeDisabled" />
        </NFormItem>
      </template>
      <template #toolbar-extra>
        <NSpace>
          <NButton @click="refreshTable">刷新</NButton>
          <NButton type="primary" :loading="syncing" @click="syncLocations">同步位置</NButton>
          <NButton type="primary" secondary @click="addManual">新增位置</NButton>
        </NSpace>
      </template>
    </HPageDataTable>

    <HPageForm
      v-model:show="formShow"
      :title="form.id ? '编辑位置' : '新增位置'"
      :model="form"
      :rules="rules"
      :handle-submit="saveManual"
      label-placement="left"
      label-width="90"
      width="560px"
    >
      <NFormItem label="所属场馆" path="orgId">
        <VenueSelect v-model:value="form.orgId" :disabled="Boolean(form.id)" placeholder="请选择所属场馆" />
      </NFormItem>
      <NFormItem label="上级位置" path="parentId">
        <NSelect
          v-model:value="form.parentId"
          :options="parentOptions"
          clearable
          filterable
          placeholder="不选择则作为根位置"
        />
      </NFormItem>
      <NFormItem label="位置名称" path="name"><NInput v-model:value="form.name" /></NFormItem>
      <NFormItem label="位置编码" path="code">
        <NInput v-model:value="form.code" clearable placeholder="可选，未填写由系统自动生成" />
      </NFormItem>
      <NFormItem label="位置类型" path="type">
        <NSelect v-model:value="form.type" :options="locationTypeOptions" filterable />
      </NFormItem>
      <NFormItem label="馆区标识" path="siteId">
        <NInput v-model:value="form.siteId" clearable placeholder="可选，默认继承上级位置" />
      </NFormItem>
    </HPageForm>
  </div>
</template>

<script setup lang="ts">
import { computed, h, reactive, ref } from 'vue';
import {
  NButton,
  NFormItem,
  NInput,
  NSelect,
  NSpace,
  NSwitch,
  NTag,
  type DataTableColumns,
  useDialog,
  useMessage,
} from 'naive-ui';
import { cloneDeep } from 'lodash-es';
import { formatDateTime } from '@/utils';
import { getDictLabel, useDict } from '@/utils/dict';
import { ingestApi } from '../api/ingest';
import VenueSelect from '../components/VenueSelect.vue';
import VenueText from '../components/VenueText.vue';
import type { MuseumLocationManageVo } from '../typings';
import { LOCATION_TYPE_DICT } from '../utils/ingestDict';

const message = useMessage();
const dialog = useDialog();
const { options: locationTypeOptions } = useDict(LOCATION_TYPE_DICT);
const tableRef = ref<any>(null);
const syncing = ref(false);
const rows = ref<MuseumLocationManageVo[]>([]);
const query = reactive({
  orgId: null as string | null,
  keyword: null as string | null,
  sourceType: null as string | null,
  includeDisabled: false,
});
const sourceTypeOptions = [
  { label: '业务主数据', value: 'BUSINESS_MASTER' },
  { label: '消防系统', value: 'FIRE_SYSTEM' },
  { label: '安防系统', value: 'SECURITY_SYSTEM' },
  { label: '防雷系统', value: 'LIGHTNING_SYSTEM' },
];
const parentOptions = computed(() =>
  rows.value
    .filter(
      (row) =>
        row.orgId === form.value.orgId &&
        row.status === 'NORMAL' &&
        row.id !== form.value.id &&
        !isDescendantOfCurrent(row),
    )
    .map((row) => ({ label: row.path || row.name || row.code || row.id, value: row.id! })),
);

const columns: DataTableColumns<MuseumLocationManageVo> = [
  {
    title: '位置名称',
    key: 'name',
    fixed: 'left',
    minWidth: 220,
    render: (row) => row.name || '--',
  },
  { title: '位置编码', key: 'code', width: 190, ellipsis: { tooltip: true } },
  { title: '类型', key: 'type', width: 110, render: (row) => locationTypeLabel(row.type) },
  {
    title: '来源',
    key: 'sources',
    minWidth: 220,
    render: (row) =>
      row.manual
        ? h(NTag, { size: 'small', bordered: false }, { default: () => '本系统' })
        : h(
            NSpace,
            { size: 4 },
            {
              default: () =>
                (row.sources || []).map((source) =>
                  h(
                    NTag,
                    { size: 'small', type: 'info', bordered: false },
                    { default: () => sourceLabel(source.sourceCode, source.sourceType) },
                  ),
                ),
            },
          ),
  },
  { title: '所属场馆', key: 'orgId', width: 180, render: (row) => h(VenueText, { value: row.orgId }) },
  { title: '关联设备', key: 'deviceCount', width: 90, align: 'right' },
  {
    title: '最近数据',
    key: 'latestDataAt',
    width: 180,
    render: (row) => (row.latestDataAt ? formatDateTime(row.latestDataAt) : '--'),
  },
  {
    title: '状态',
    key: 'status',
    width: 90,
    render: (row) =>
      h(
        NTag,
        { size: 'small', type: row.status === 'NORMAL' ? 'success' : 'default', bordered: false },
        { default: () => (row.status === 'NORMAL' ? '启用' : '停用') },
      ),
  },
  {
    title: '操作',
    key: 'actions',
    width: 150,
    fixed: 'right',
    render: (row) =>
      row.manual
        ? h(
            NSpace,
            { size: 4 },
            {
              default: () => [
                h(
                  NButton,
                  { size: 'small', text: true, type: 'primary', onClick: () => editManual(row) },
                  { default: () => '编辑' },
                ),
                h(
                  NButton,
                  {
                    size: 'small',
                    text: true,
                    type: row.status === 'NORMAL' ? 'warning' : 'success',
                    onClick: () => toggleEnabled(row),
                  },
                  { default: () => (row.status === 'NORMAL' ? '停用' : '启用') },
                ),
              ],
            },
          )
        : '--',
  },
];

const formShow = ref(false);
const defaultForm: MuseumLocationManageVo = { type: 'SPACE' };
const form = ref<MuseumLocationManageVo>(cloneDeep(defaultForm));
const rules = {
  orgId: { required: true, message: '请选择所属场馆', trigger: 'change' },
  name: { required: true, message: '请输入位置名称', trigger: ['blur', 'input'] },
  type: { required: true, message: '请选择位置类型', trigger: 'change' },
};

function responsePayload(response: any): any {
  let payload = response;
  for (let depth = 0; depth < 5; depth += 1) {
    if (Array.isArray(payload) || payload === null || typeof payload !== 'object' || !('data' in payload)) {
      return payload;
    }
    payload = payload.data;
  }
  return payload;
}

function responseList<T>(response: any): T[] {
  const payload = responsePayload(response);
  if (Array.isArray(payload)) return payload;
  return Array.isArray(payload?.list) ? payload.list : [];
}

async function fetchLocations(params: Record<string, unknown>) {
  const response = await ingestApi.locationCatalog({
    ...params,
    orgId: query.orgId || undefined,
    keyword: query.keyword?.trim() || undefined,
    sourceType: query.sourceType || undefined,
    includeDisabled: query.includeDisabled,
  });
  rows.value = responseList<MuseumLocationManageVo>(response) || [];
  return { data: { list: rows.value, total: rows.value.length, pages: 1 } };
}

function refreshTable() {
  tableRef.value?.handleRefresh?.();
}

async function syncLocations() {
  if (!query.orgId) {
    message.warning('请先选择需要同步的场馆');
    return;
  }
  syncing.value = true;
  try {
    const count = Number(responsePayload(await ingestApi.syncLocations(query.orgId))) || 0;
    message.success(`位置同步完成，共 ${count} 个有效位置`);
    refreshTable();
  } finally {
    syncing.value = false;
  }
}

function addManual() {
  form.value = { ...cloneDeep(defaultForm), orgId: query.orgId || undefined };
  formShow.value = true;
}

function editManual(row: MuseumLocationManageVo) {
  form.value = cloneDeep(row);
  formShow.value = true;
}

async function saveManual() {
  await ingestApi.saveLocation({ ...form.value, children: undefined, sources: undefined });
  message.success('采集位置已保存');
  refreshTable();
}

function toggleEnabled(row: MuseumLocationManageVo) {
  const enabled = row.status !== 'NORMAL';
  dialog.warning({
    title: enabled ? '启用采集位置' : '停用采集位置',
    content: `确认${enabled ? '启用' : '停用'}“${row.name || row.code}”？`,
    positiveText: '确认',
    negativeText: '取消',
    onPositiveClick: async () => {
      await ingestApi.updateLocationEnabled(row.id!, enabled);
      message.success(enabled ? '已启用' : '已停用');
      refreshTable();
    },
  });
}

function isDescendantOfCurrent(row: MuseumLocationManageVo) {
  if (!form.value.id) return false;
  let current = row;
  const visited = new Set<string>();
  while (current.parentId && !visited.has(current.parentId)) {
    if (current.parentId === form.value.id) return true;
    visited.add(current.parentId);
    const parent = rows.value.find((item) => item.id === current.parentId);
    if (!parent) break;
    current = parent;
  }
  return false;
}

function locationTypeLabel(value?: string) {
  const label = getDictLabel(locationTypeOptions.value, value);
  return label === '--' ? value || '--' : label;
}

function sourceLabel(sourceCode: string, sourceType: string) {
  if (sourceCode === 'COL_WAREHOUSE') return '藏品位置';
  return sourceTypeOptions.find((item) => item.value === sourceType)?.label || sourceCode;
}
</script>

<style scoped>
.location-page {
  display: flex;
  width: 100%;
  height: 100%;
  flex: 1;
  min-height: 0;
  flex-direction: column;
  overflow: hidden;
}
</style>
