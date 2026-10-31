<template>
  <NSelect
    :value="value"
    :options="options"
    :loading="loading"
    :multiple="multiple"
    filterable
    remote
    clearable
    :disabled="disabled"
    :status="error ? 'error' : undefined"
    :placeholder="error || placeholder || '选择修复人员'"
    :render-label="renderLabel"
    :render-tag="renderTag"
    @search="onSearch"
    @update:value="selectValue"
    @update:show="onShow"
  />
</template>

<script setup lang="ts">
import { h } from 'vue';
import { useMasterDataPicker } from '../composables/useMasterDataPicker';
import { NSelect, NTag } from 'naive-ui';
import { restorerApi } from '../api/restorer';
import type { RestorerVo } from '../typings';

const props = withDefaults(defineProps<{
  orgId?: string;
  value?: string | string[] | null;
  placeholder?: string;
  multiple?: boolean;
  disabled?: boolean;
  /** 默认只列启用的 */
  onlyEnabled?: boolean;
  /** 按擅长领域筛选（restoration_method_category 值） */
  speciality?: string;
}>(), { onlyEnabled: true });

const emit = defineEmits<{ (e: 'update:value', v: any): void }>();

const { options, loading, error, selectValue, onSearch, onShow } = useMasterDataPicker<RestorerVo>(props, {
  page: restorerApi.page, get: restorerApi.get,
  query: () => ({ speciality: props.speciality }),
  matches: row => !props.speciality || !!row.speciality?.includes(props.speciality), emit: value => emit('update:value', value),
});

function renderLabel(option: any) {
  const raw: RestorerVo = option.raw;
  if (!raw) return option.label;
  return h('div', { style: 'display:flex; align-items:center; gap:8px;' }, [
    raw.enabled === false ? h(NTag, { size: 'small', type: 'warning', bordered: false }, { default: () => '停用' }) : null,
    h('span', { style: 'font-weight:600;' }, raw.name || '（无姓名）'),
    raw.title
      ? h(NTag, { size: 'small', bordered: false, type: 'default' }, { default: () => titleText(raw.title) })
      : null,
    raw.external
      ? h(NTag, { size: 'small', bordered: false, type: 'warning' }, { default: () => '外聘' })
      : h(NTag, { size: 'small', bordered: false, type: 'info' }, { default: () => '本馆' }),
    raw.speciality ? h('span', { style: 'color:#999;font-size:12px;' }, raw.speciality) : null,
  ]);
}

function renderTag({ option }: any) {
  const raw: RestorerVo = option.raw;
  return h('span', {}, `${raw?.name || option.label}${raw?.enabled === false ? '（停用）' : ''}`);
}

function titleText(t?: string) {
  return ({
    PRIMARY: '初级', INTERMEDIATE: '中级', SENIOR: '高级',
    TECHNICIAN: '技师', MASTER_TECHNICIAN: '高级技师',
  } as any)[t || ''] || t || '';
}

</script>
