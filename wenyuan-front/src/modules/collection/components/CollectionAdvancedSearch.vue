<template>
  <HPageForm v-model:show="drawerShow" title="高级检索藏品" content-only :enable-cancel="false">
    <div class="collection-advanced-search">
      <div v-if="level || lifeStatus || lifeStatuses?.length" class="collection-advanced-search__scope">
        <span>当前可选范围</span>
        <NTag v-if="level" size="small" :bordered="false">级别：{{ levelText(level) }}</NTag>
        <NTag v-if="lifeStatus" size="small" :bordered="false">状态：{{ statusText(lifeStatus) }}</NTag>
        <NTag v-else-if="lifeStatuses?.length" size="small" :bordered="false">
          状态：{{ lifeStatuses.map(statusText).join('、') }}
        </NTag>
      </div>
      <NAlert v-if="error" type="error" title="检索失败，请重试" />
      <div class="collection-advanced-search__table">
        <HPageDataTable auto-scroll-x
          ref="tableRef"
          :columns="columns"
          :fetch-data="fetchCollections"
          :params="filters"
          :pagination="{ pageSize: 30, pageSizes: [30], showSizePicker: false }"
          :show-header="false"
          :is-add="false"
          :is-edit="false"
          :is-copy="false"
          :is-delete="false"
          :is-export="false"
          :show-operate="false"
          :column-configurable="false"
          :show-query-reset-button="true"
          query-form-storage-key="__APP_COLLECTION_ADVANCED_SEARCH_QUERY__"
          reset-button-type="default"
          :scroll-x="900"
          size="small"
          @update:data="updateRows"
        >
          <template #queryForm>
            <NFormItem label="检索内容">
              <NInput v-model:value="filters.keyword" clearable placeholder="名称 / 登记号 / RFID" @keydown.enter="search" />
            </NFormItem>
            <NFormItem label="总登记号">
              <NInput v-model:value="filters.totalRegNo" clearable placeholder="总登记号" @keydown.enter="search" />
            </NFormItem>
            <NFormItem label="分类号">
              <NInput v-model:value="filters.classNo" clearable placeholder="分类号" @keydown.enter="search" />
            </NFormItem>
            <NFormItem label="文物分类">
              <NSelect v-model:value="filters.categoryId" :options="categoryOptions" filterable clearable placeholder="全部分类" />
            </NFormItem>
            <NFormItem label="藏品年代">
              <NInput v-model:value="filters.era" clearable placeholder="年代" @keydown.enter="search" />
            </NFormItem>
            <NFormItem label="藏品质地">
              <NInput v-model:value="filters.texture" clearable placeholder="质地" @keydown.enter="search" />
            </NFormItem>
            <NFormItem v-if="!level" label="藏品级别">
              <HDictSelect v-model:value="filters.level" type="col_relic_level" clearable placeholder="全部级别" />
            </NFormItem>
            <NFormItem v-if="!lifeStatus && !lifeStatuses?.length" label="藏品状态">
              <HDictSelect v-model:value="filters.lifeStatus" type="col_life_status" clearable placeholder="全部状态" />
            </NFormItem>
          </template>
          <template #toolbar-extra>
            <NButton v-if="multiple && rows.some(canAdd)" size="small" secondary @click="addCurrentPage">
              本页加入选择列表
            </NButton>
          </template>
        </HPageDataTable>
      </div>

      <section class="collection-advanced-search__selection">
        <div class="collection-advanced-search__heading">
          <strong>选择列表 <span>（{{ pendingIds.length }} 件）</span></strong>
          <NButton v-if="pendingIds.length && multiple" size="small" text @click="clearPending">清空</NButton>
        </div>
        <div v-if="pendingIds.length" class="collection-advanced-search__selected">
          <div v-for="id in pendingIds" :key="id" class="collection-advanced-search__selected-row">
            <span :title="pendingRows.get(id) ? buildCollectionSelectOption(pendingRows.get(id)!).description : id">
              {{ pendingRows.get(id) ? buildCollectionSelectOption(pendingRows.get(id)!).label : id }}
            </span>
            <NButton size="tiny" text type="error" :aria-label="`移除藏品 ${id}`" @click="remove(id)">移除</NButton>
          </div>
        </div>
        <NEmpty v-else description="从上方检索结果加入藏品" size="small" />
      </section>
    </div>
    <template #form-action>
      <NButton @click="drawerShow = false">取消</NButton>
      <NButton type="primary" class="ml-2" @click="confirm">确认选择{{ pendingIds.length ? `（${pendingIds.length}）` : '' }}</NButton>
    </template>
  </HPageForm>
</template>

<script setup lang="ts">
import type { DataTableColumns } from 'naive-ui';
import { NButton } from 'naive-ui';
import { computed, h, onBeforeUnmount, reactive, ref, watch } from 'vue';
import { categoryApi } from '../api/category';
import { collectionApi } from '../api/collection';
import type { CollectionVo, RelicLevel } from '../typings/collection';
import { buildCollectionSelectOption } from '../utils/collectionSelectOption';

const props = defineProps<{
  show: boolean;
  selectedIds: string[];
  selectedRows: CollectionVo[];
  multiple?: boolean;
  orgId?: string;
  lifeStatus?: string;
  lifeStatuses?: string[];
  level?: RelicLevel;
}>();
const emit = defineEmits<{
  (e: 'update:show', value: boolean): void;
  (e: 'confirm', ids: string[], rows: CollectionVo[]): void;
}>();

const drawerShow = computed({ get: () => props.show, set: (value: boolean) => emit('update:show', value) });
const tableRef = ref<{ handleQuery: () => void } | null>(null);
const filters = reactive({ keyword: '', totalRegNo: '', classNo: '', categoryId: null as string | null,
  era: '', texture: '', level: null as string | null, lifeStatus: null as string | null });
const rows = ref<CollectionVo[]>([]);
const error = ref(false);
const categoryOptions = ref<{ label: string; value: string }[]>([]);
const pendingIds = ref<string[]>([]);
const pendingRows = ref(new Map<string, CollectionVo>());
const pendingIdSet = computed(() => new Set(pendingIds.value));

function levelText(value: string) {
  return ({ FIRST: '一级', SECOND: '二级', THIRD: '三级', GENERAL: '一般文物', UNRATED: '未定级' } as Record<string, string>)[value] || value;
}
function statusText(value: string) {
  return ({ PENDING: '待定', IN_STORE: '在库', IN_USE: '提用中', RESTORING: '修复中',
    ON_LOAN: '借出', MISSING: '盘亏', DEREGISTERED: '已退出馆藏', PRE_OUT: '待出库',
    RETURN_HOLD: '归还待核查' } as Record<string, string>)[value] || value;
}
function inScope(row: CollectionVo) {
  return !!row.id && (!props.orgId || row.orgId === props.orgId)
    && (!props.level || row.level === props.level)
    && (!props.lifeStatus || row.lifeStatus === props.lifeStatus)
    && (!props.lifeStatuses?.length || props.lifeStatuses.includes(String(row.lifeStatus || '')));
}
function canAdd(row: CollectionVo) { return inScope(row) && !pendingIdSet.value.has(row.id!); }
function add(row: CollectionVo) {
  if (!inScope(row) || !row.id) return;
  pendingIds.value = props.multiple ? [...new Set([...pendingIds.value, row.id])] : [row.id];
  if (props.multiple) pendingRows.value.set(row.id, row);
  else pendingRows.value = new Map([[row.id, row]]);
}
function addCurrentPage() { rows.value.filter(canAdd).forEach(add); }
function remove(id: string) {
  pendingIds.value = pendingIds.value.filter((item) => item !== id);
  pendingRows.value.delete(id);
}
function clearPending() { pendingIds.value = []; pendingRows.value.clear(); }
function confirm() {
  emit('confirm', pendingIds.value, pendingIds.value.flatMap((id) => {
    const row = pendingRows.value.get(id);
    return row ? [row] : [];
  }));
}
function search() { tableRef.value?.handleQuery(); }
function updateRows(data: CollectionVo[]) { rows.value = Array.isArray(data) ? data : []; }

const columns = computed<DataTableColumns<CollectionVo>>(() => [
  { title: '总登记号', key: 'totalRegNo', minWidth: 120, resizable: true, ellipsis: { tooltip: true }, width: 150, render: (row) => row.totalRegNo || '—' },
  { title: '藏品名称', key: 'name', width: 200, resizable: true, ellipsis: { tooltip: true }, minWidth: 170, render: (row) => row.name || '未命名藏品' },
  { title: '分类号', key: 'classNo', minWidth: 100, resizable: true, ellipsis: { tooltip: true }, width: 125, render: (row) => row.classNo || '—' },
  { title: '文物分类', key: 'categoryName', minWidth: 100, resizable: true, ellipsis: { tooltip: true }, width: 140, render: (row) => row.categoryName || '—' },
  { title: '年代', key: 'era', minWidth: 100, resizable: true, ellipsis: { tooltip: true }, width: 130, render: (row) => row.era || '—' },
  { title: '级别', key: 'level', minWidth: 80, resizable: true, ellipsis: { tooltip: true }, width: 100, render: (row) => levelText(String(row.level || '')) },
  { title: '状态', key: 'lifeStatus', minWidth: 90, resizable: true, ellipsis: { tooltip: true }, width: 120, render: (row) => statusText(String(row.lifeStatus || '')) },
  { title: '操作', key: 'action', width: 118, fixed: 'right', render: (row) => h(NButton, {
    size: 'tiny', type: canAdd(row) ? 'primary' : 'default', secondary: true,
    disabled: !canAdd(row), onClick: () => add(row),
  }, { default: () => !inScope(row) ? '不符合范围' : pendingIdSet.value.has(row.id!) ? '已加入' : '加入列表' }) },
]);

let request = 0;
async function fetchCollections(params: Record<string, unknown>) {
  const token = ++request;
  error.value = false;
  try {
    const query = Object.fromEntries(Object.entries(params)
      .filter(([key, value]) => !['pageNum', 'pageSize', 'level', 'lifeStatus'].includes(key)
        && value != null && String(value).trim())
      .map(([key, value]) => [key, String(value).trim()]));
    const response = await collectionApi.page({ pageNum: params.pageNum, pageSize: 30, ...query,
      ...(props.orgId ? { orgId: props.orgId } : {}),
      ...(props.level || params.level ? { level: props.level || params.level } : {}),
      ...(props.lifeStatus || params.lifeStatus ? { lifeStatus: props.lifeStatus || params.lifeStatus } : {}),
      ...(props.lifeStatuses?.length ? { lifeStatuses: props.lifeStatuses } : {}),
    });
    return token === request ? response : null;
  } catch {
    if (token !== request) return null;
    error.value = true;
    return { data: { list: [], total: 0 } };
  }
}
async function loadCategories() {
  try {
    const response: any = await categoryApi.list();
    const list = response?.data?.list ?? response?.data ?? [];
    categoryOptions.value = Array.isArray(list) ? list.filter((item) => item.id && item.name)
      .map((item) => ({ label: item.name, value: item.id })) : [];
  } catch { categoryOptions.value = []; }
}
watch(() => props.show, (show) => {
  if (!show) { ++request; return; }
  pendingIds.value = [...props.selectedIds];
  pendingRows.value = new Map(props.selectedRows.filter((row) => row.id).map((row) => [row.id!, row]));
  void loadCategories();
}, { immediate: true });
watch(() => props.selectedRows, (selectedRows) => {
  selectedRows.forEach((row) => {
    if (row.id && pendingIds.value.includes(row.id)) pendingRows.value.set(row.id, row);
  });
});
onBeforeUnmount(() => { ++request; });
</script>

<style scoped>
.collection-advanced-search { display: grid; gap: 12px; }
.collection-advanced-search__scope { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; color: var(--n-text-color-3); font-size: 13px; }
.collection-advanced-search__table { height: clamp(320px, 53vh, 590px); min-width: 0; }
.collection-advanced-search__selection { display: grid; gap: 10px; padding: 12px 15px; border: 1px solid var(--n-border-color); border-radius: 4px; }
.collection-advanced-search__heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.collection-advanced-search__heading span { font-weight: normal; color: var(--n-text-color-3); font-size: 13px; }
.collection-advanced-search__selected { display: grid; gap: 6px; max-height: 140px; overflow-y: auto; }
.collection-advanced-search__selected-row { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 5px 8px; border: 1px solid var(--n-border-color); border-radius: 4px; }
.collection-advanced-search__selected-row span { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>
