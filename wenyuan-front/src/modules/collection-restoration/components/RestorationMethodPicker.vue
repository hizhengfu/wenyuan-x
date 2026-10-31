<template>
  <NSelect
    :value="value"
    :options="options"
    :loading="loading"
    filterable
    remote
    clearable
    :disabled="disabled"
    :status="error ? 'error' : undefined"
    :placeholder="error || placeholder || '选择修复方法'"
    @search="onSearch"
    @update:value="selectValue"
    @update:show="onShow"
    :render-label="renderLabel"
    :render-tag="renderTag"
  />
</template>

<script setup lang="ts">
import { useMasterDataPicker } from '../composables/useMasterDataPicker';
import { NSelect } from 'naive-ui';
import { restorationMethodApi } from '../api/method';
import type { RestorationMethodVo } from '../typings';

const props = defineProps<{ value?: string | null; disabled?: boolean; placeholder?: string; orgId?: string }>();
const emit = defineEmits<{ (e: 'update:value', value: string | null): void }>();
const { options, loading, error, selectValue, onSearch, onShow } = useMasterDataPicker<RestorationMethodVo>(props, {
  page: restorationMethodApi.page, get: restorationMethodApi.get,
  query: () => ({}), matches: () => true, emit: value => emit('update:value', value),
});
function renderLabel(option: any) {
  const row: RestorationMethodVo = option.raw;
  return `${option.label}${row?.category ? ` · ${row.category}` : ''}${row?.enabled === false ? '（停用）' : ''}`;
}
function renderTag({ option }: any) { return renderLabel(option); }
</script>
