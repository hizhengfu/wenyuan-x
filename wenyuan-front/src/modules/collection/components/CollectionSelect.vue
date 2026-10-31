<template>
  <NInputGroup class="collection-select-picker" :class="$attrs.class" :style="$attrs.style">
    <NSelect
      v-bind="inputAttrs()"
      class="collection-select"
      :value="value"
      :options="options"
      :multiple="multiple"
      :loading="loading"
      :render-label="renderLabel"
      :theme-overrides="selectThemeOverrides"
      filterable
      remote
      :clearable="clearable"
      :disabled="effectiveDisabled"
      :placeholder="selectPlaceholder"
      :status="error ? 'error' : undefined"
      @search="onSearch"
      @update:show="onShow"
      @update:value="updateValue"
    />
    <NButton v-if="multiple && !effectiveDisabled" class="collection-select-picker__advanced" @click="advancedShow = true">高级检索</NButton>
  </NInputGroup>
  <CollectionAdvancedSearch
    v-if="advancedShow && multiple && !effectiveDisabled"
    v-model:show="advancedShow"
    :selected-ids="selectedIds"
    :selected-rows="selectedRows"
    :multiple="multiple"
    :org-id="orgId"
    :life-status="lifeStatus"
    :life-statuses="lifeStatuses"
    :level="level"
    @confirm="confirmAdvanced"
  />
</template>

<script setup lang="ts">
import type { SelectRenderLabel } from 'naive-ui';
import { computed, defineAsyncComponent, h, onBeforeUnmount, ref, useAttrs, watch } from 'vue';
import { useAuthStore } from '@/store';
import { collectionApi } from '../api/collection';
import type { CollectionVo, RelicLevel } from '../typings/collection';
import { buildCollectionSelectOption, type CollectionSelectOption } from '../utils/collectionSelectOption';
import { COLLECTION_SELECT_QUERY_CODES } from '../utils/collectionSelectPermission';

defineOptions({ inheritAttrs: false });

const props = withDefaults(defineProps<{
  value?: string | string[] | null;
  placeholder?: string;
  multiple?: boolean;
  lifeStatus?: string;
  lifeStatuses?: string[];
  level?: RelicLevel;
  disabled?: boolean;
  clearable?: boolean;
  orgId?: string;
  /** 无藏品查询权限时用业务快照回退展示（如总账号+名称），避免裸 id 与无权限报错弹窗。 */
  fallbackLabel?: string;
}>(), { clearable: true });
const emit = defineEmits<{
  (e: 'update:value', v: any): void;
  (e: 'loaded', rows: CollectionVo[]): void;
  (e: 'selected', row: CollectionVo | null): void;
}>();

const auth = useAuthStore();
/** 与 CollectionController.page/get 一致；无权限时不发远程请求（列表筛选项否则会 403）。 */
const canQueryCollection = computed(() => auth.hasPermission(COLLECTION_SELECT_QUERY_CODES));
const effectiveDisabled = computed(() => !!props.disabled || !canQueryCollection.value);

const attrs = useAttrs();
function inputAttrs() {
  return Object.fromEntries(Object.entries(attrs).filter(([key]) => key !== 'class' && key !== 'style'));
}

const rows = ref<CollectionVo[]>([]);
const selectedRows = ref<CollectionVo[]>([]);
const advancedShow = ref(false);
const CollectionAdvancedSearch = defineAsyncComponent(() => import('./CollectionAdvancedSearch.vue'));
const loading = ref(false);
const error = ref(false);
const selectPlaceholder = computed(() => {
  if (!canQueryCollection.value) return '无藏品查询权限';
  if (error.value) return '藏品加载失败，请重新打开或检索';
  return props.placeholder || '选择藏品（可按登记号或名称检索）';
});
const selectedIds = computed(() => props.value == null ? [] : Array.isArray(props.value) ? props.value : [props.value]);
function fallbackOption(id: string): CollectionSelectOption | null {
  const label = String(props.fallbackLabel || '').trim();
  if (!id || !label) return null;
  return { label, value: id, description: '', searchText: label.toLocaleLowerCase() };
}

const options = computed<CollectionSelectOption[]>(() => {
  const ids = new Set(selectedIds.value);
  const current = selectedRows.value.filter((row) => row.id && ids.has(row.id));
  const seen = new Set(current.map((row) => row.id));
  const list = [...current, ...rows.value.filter((row) => row.id && !seen.has(row.id))]
    .map(buildCollectionSelectOption).filter((option) => option.value);
  // 已选 id 不在列表里时，用业务快照补一条展示项（不发请求）。
  for (const id of selectedIds.value) {
    if (id && !list.some((option) => option.value === id)) {
      const option = fallbackOption(id);
      if (option) list.unshift(option);
    }
  }
  return list;
});
const selectThemeOverrides = {
  peers: {
    InternalSelectMenu: {
      optionHeightMedium: '50px',
    },
  },
};

const renderLabel: SelectRenderLabel = (option) => {
  const description = String(option.description || '').trim();
  return h('div', { class: 'collection-select-option' }, [
    h('div', { class: 'collection-select-option__main' }, String(option.label || '')),
    description ? h('div', { class: 'collection-select-option__description', title: description }, description) : null,
  ]);
};

let request = 0;
let timer: ReturnType<typeof setTimeout> | undefined;
function stopPending() {
  ++request;
  if (timer) clearTimeout(timer);
  timer = undefined;
}

function hasFallbackLabel() {
  return !!String(props.fallbackLabel || '').trim();
}

async function load(keyword = '') {
  // 无 COLLECTION_SELECT / COLLECTION_ADMIN / ADMIN 时不发 page（列表筛选项否则会 403）。
  if (props.disabled || !canQueryCollection.value) return;
  // 已有业务快照且当前已选值时，不为「回填标签」去打 page（无 COLLECTION_SELECT 会 403）。
  // 用户主动输入关键字检索时仍走下方请求。
  if (hasFallbackLabel() && !keyword.trim() && selectedIds.value.length) return;
  const token = ++request;
  loading.value = true;
  error.value = false;
  try {
    const resp: any = await collectionApi.page({
      pageNum: 1,
      pageSize: 30,
      ...(keyword.trim() ? { keyword: keyword.trim() } : {}),
      ...(props.lifeStatus ? { lifeStatus: props.lifeStatus } : {}),
      ...(props.lifeStatuses?.length ? { lifeStatuses: props.lifeStatuses } : {}),
      ...(props.level ? { level: props.level } : {}),
      ...(props.orgId ? { orgId: props.orgId } : {}),
    });
    if (token !== request) return;
    const list: CollectionVo[] = resp?.data?.list ?? resp?.data ?? [];
    rows.value = Array.isArray(list) ? list : [];
    emit('loaded', rows.value);
  } catch {
    if (token === request) {
      rows.value = [];
      error.value = true;
    }
  } finally {
    if (token === request) loading.value = false;
  }
}

function onSearch(keyword: string) {
  if (props.disabled || !canQueryCollection.value) return;
  stopPending();
  rows.value = [];
  loading.value = true;
  timer = setTimeout(() => {
    timer = undefined;
    void load(keyword);
  }, 250);
}

function onShow(show: boolean) {
  if (!show || props.disabled || !canQueryCollection.value) return;
  stopPending();
  void load();
}

function updateValue(value: string | string[] | null) {
  const ids = Array.isArray(value) ? value : typeof value === 'string' ? [value] : [];
  const available = [...rows.value, ...selectedRows.value];
  selectedRows.value = ids.flatMap((id) => {
    const row = available.find((item) => item.id === id);
    return row ? [row] : [];
  });
  const row = typeof value === 'string' ? selectedRows.value[0] || null : null;
  emit('update:value', value);
  emit('selected', row);
}

function confirmAdvanced(ids: string[], chosenRows: CollectionVo[]) {
  selectedRows.value = [...selectedRows.value, ...chosenRows];
  updateValue(props.multiple ? ids : ids[0] || null);
  void hydrateSelected(ids);
  advancedShow.value = false;
}

watch(() => [props.multiple, effectiveDisabled.value], ([multiple, disabled]) => {
  if (!multiple || disabled) advancedShow.value = false;
});

watch(() => [props.orgId, props.lifeStatus, props.lifeStatuses, props.level, props.disabled, canQueryCollection.value], (_value, previous) => {
  stopPending();
  rows.value = [];
  selectedRows.value = [];
  loading.value = false;
  if (previous) void hydrateSelected(selectedIds.value);
  void load();
}, { immediate: true });

async function hydrateSelected(ids: string[]) {
  const current = new Set(ids);
  selectedRows.value = selectedRows.value.filter((row) => row.id && current.has(row.id));
  const missing = ids.filter((id) => id && !rows.value.some((row) => row.id === id) && !selectedRows.value.some((row) => row.id === id));
  if (!missing.length) return;
  // 有快照回退时不再强拉藏品详情；只读或无查询权限时也不强拉（等业务快照写入后再由 fallbackLabel watch 回填）。
  const label = String(props.fallbackLabel || '').trim();
  if (label || props.disabled || !canQueryCollection.value) {
    if (label && !props.multiple && missing.length === 1) {
      selectedRows.value = [{ id: missing[0], name: label, totalRegNo: '' } as CollectionVo];
    }
    return;
  }
  const results = await Promise.allSettled(missing.map((id) => collectionApi.get(id)));
  const stillSelected = new Set(selectedIds.value);
  const loaded = results.flatMap((result) => {
    if (result.status !== 'fulfilled') return [];
    const row: CollectionVo = result.value?.data?.data ?? result.value?.data;
    return row?.id && stillSelected.has(row.id) && (!props.orgId || row.orgId === props.orgId) ? [row] : [];
  });
  const unique = new Map([...selectedRows.value.filter((row) => row.id && stillSelected.has(row.id)), ...loaded].map((row) => [row.id, row]));
  selectedRows.value = [...unique.values()];
}

watch(selectedIds, (ids) => { void hydrateSelected(ids); }, { immediate: true });
// 快照标签后到时补一次本地回填，避免先 get 403 后再也不更新选项。
watch(() => props.fallbackLabel, () => { void hydrateSelected(selectedIds.value); });

onBeforeUnmount(stopPending);
</script>

<style>
.collection-select-picker {
  width: 100%;
  min-width: 0;
}

.collection-select-picker .collection-select {
  flex: 1;
  min-width: 0;
}

.collection-select-picker__advanced {
  flex: none;
}

.collection-select-option {
  min-width: 0;
  line-height: 1.3;
}

.collection-select-option__main,
.collection-select-option__description {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.collection-select-option__main {
  font-weight: 500;
}

.collection-select-option__description {
  margin-top: 2px;
  font-size: 12px;
  line-height: 16px;
  opacity: 0.68;
}

.collection-select .n-base-selection .collection-select-option__description {
  display: none;
}
</style>
