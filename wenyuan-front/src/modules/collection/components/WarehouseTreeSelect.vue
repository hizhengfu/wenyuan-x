<template>
  <NTreeSelect
    v-bind="$attrs"
    :value="value ?? null"
    :options="visibleOptions"
    :loading="loading"
    key-field="id"
    label-field="name"
    children-field="children"
    check-strategy="all"
    :placeholder="error ? '库位加载失败，请重试' : placeholder"
    :status="error ? 'error' : undefined"
    :clearable="clearable"
    :filterable="filterable"
    :multiple="multiple"
    :disabled="disabled"
    @focus="load"
    @update:value="emit('update:value', $event)"
  >
    <template v-if="error" #empty>
      <div role="alert">
        <span>库位加载失败，</span>
        <NButton text type="primary" :loading="loading" @mousedown.prevent @click.stop="load">重新加载</NButton>
      </div>
    </template>
  </NTreeSelect>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useAuthStore } from '@/store/modules/auth';
import { useCatalogOperation } from '../composables/useCatalogOperation';
import { warehouseApi } from '../api/warehouse';
import { buildWarehouseTree, extractWarehouseRows, filterWarehouseRows } from '../utils/warehouseTree';
import type { WarehouseVo } from '../typings/collection';

defineOptions({ inheritAttrs: false });

const props = withDefaults(
  defineProps<{
    value?: string | string[] | null;
    orgId?: string | null;
    placeholder?: string;
    clearable?: boolean;
    filterable?: boolean;
    multiple?: boolean;
    disabled?: boolean;
    excludedId?: string | null;
    sceneType?: string | null;
    allowedTypes?: string[];
  }>(),
  {
    value: null,
    orgId: null,
    placeholder: '请选择库位',
    clearable: true,
    filterable: true,
    multiple: false,
    disabled: false,
    excludedId: null,
    sceneType: 'STORAGE',
    allowedTypes: () => [],
  },
);

const emit = defineEmits<{
  (event: 'update:value', value: any): void;
  (event: 'loaded', rows: WarehouseVo[]): void;
}>();

const rows = ref<WarehouseVo[]>([]);
const error = ref(false);
const auth = useAuthStore();
const session = useCatalogOperation(() => { rows.value = []; error.value = false; });
const loading = session.busy;
const visibleOptions = computed(() => {
  const orgRows = props.orgId ? filterWarehouseRows(rows.value, (row) => row.orgId === props.orgId) : rows.value;
  const scopedRows = props.sceneType
    ? filterWarehouseRows(orgRows, (row) => row.sceneType === props.sceneType)
    : orgRows;
  const allowed = new Set(props.allowedTypes);
  const markSelectable = (items: any[]): any[] =>
    items.map((item) => ({
      ...item,
      disabled:
        (!!props.orgId && item.orgId !== props.orgId) ||
        (!!props.sceneType && item.sceneType !== props.sceneType) ||
        (allowed.size > 0 && !allowed.has(String(item.type || ''))),
      children: item.children?.length ? markSelectable(item.children) : undefined,
    }));
  return markSelectable(buildWarehouseTree(scopedRows, props.excludedId));
});

async function load() {
  if (auth.identitySyncing) return;
  const ticket = session.begin('load');
  if (ticket === null) return;
  error.value = false;
  try {
    const response = await warehouseApi.tree();
    if (!session.current(ticket)) return;
    rows.value = extractWarehouseRows(response);
    emit('loaded', rows.value);
  } catch {
    // 请求层保留具体接口错误提示；此处承接挂载、聚焦及重试的失败状态。
    if (session.current(ticket)) { rows.value = []; error.value = true; }
  } finally {
    session.finish(ticket);
  }
}

watch(() => [props.orgId, auth.userInfo?.id, auth.identityRevision, auth.identitySyncing, session.active.value], () => {
  session.reset();
  if (session.active.value && !auth.identitySyncing) void load();
}, { immediate: true, flush: 'sync' });
defineExpose({ reload: load, options: visibleOptions });
</script>
