<template>
  <NCascader
    v-bind="$attrs"
    :value="value"
    :options="options"
    :disabled="disabled || !orgId"
    :loading="loading"
    check-strategy="all"
    show-path
    filterable
    clearable
    :placeholder="orgId ? placeholder : '请先选择场馆'"
    @update:value="emit('update:value', normalizeValue($event))"
  />
</template>

<script setup lang="ts">
import { ref, watch, type PropType } from 'vue';
import { NCascader, type CascaderOption } from 'naive-ui';
import { ingestApi } from '../api/ingest';
import type { MuseumSpatialLocationVo } from '../typings';

defineOptions({ inheritAttrs: false });

const props = defineProps({
  value: { type: String as PropType<string | null>, default: null },
  orgId: { type: String as PropType<string | null>, default: null },
  disabled: { type: Boolean, default: false },
  placeholder: { type: String, default: '请选择场馆空间' },
});
const emit = defineEmits<{ 'update:value': [value: string | null] }>();
const options = ref<CascaderOption[]>([]);
const loading = ref(false);
let requestVersion = 0;

function responseList<T>(response: any): T[] {
  const payload = response?.data ?? response;
  if (Array.isArray(payload)) return payload;
  return Array.isArray(payload?.list) ? payload.list : [];
}

function buildTree(rows: MuseumSpatialLocationVo[]): CascaderOption[] {
  const ids = new Set(rows.map((item) => item.id));
  const children = new Map<string | null, MuseumSpatialLocationVo[]>();
  rows.forEach((item) => {
    const parentId = item.parentId && ids.has(item.parentId) ? item.parentId : null;
    children.set(parentId, [...(children.get(parentId) || []), item]);
  });
  const build = (parentId: string | null): CascaderOption[] =>
    (children.get(parentId) || [])
      .sort((left, right) => left.depth - right.depth || left.name.localeCompare(right.name, 'zh-CN'))
      .map((item) => ({
        label: item.name,
        value: item.id,
        children: build(item.id),
      }));
  return build(null);
}

function normalizeValue(value: string | number | null) {
  return value == null || value === '' ? null : String(value);
}

async function loadLocations() {
  const version = ++requestVersion;
  if (!props.orgId) {
    options.value = [];
    return;
  }
  loading.value = true;
  try {
    const rows = responseList<MuseumSpatialLocationVo>(await ingestApi.spatialLocations({ orgId: props.orgId }));
    if (version === requestVersion) options.value = buildTree(rows);
  } finally {
    if (version === requestVersion) loading.value = false;
  }
}

watch(
  () => props.orgId,
  () => void loadLocations(),
  { immediate: true },
);
</script>
