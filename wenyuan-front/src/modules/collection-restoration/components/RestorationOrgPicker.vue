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
    :placeholder="error || placeholder || '选择修复机构'"
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
import { restorationOrgApi } from '../api/org';
import type { RestorationOrgVo } from '../typings';

const props = withDefaults(defineProps<{
  orgId?: string;
  value?: string | string[] | null;
  placeholder?: string;
  multiple?: boolean;
  disabled?: boolean;
  /** 只列启用的（默认 true） */
  onlyEnabled?: boolean;
  /** 按资质等级过滤（一级/二级/三级） */
  qualificationLevel?: 'FIRST' | 'SECOND' | 'THIRD';
}>(), { onlyEnabled: true });

const emit = defineEmits<{ (e: 'update:value', v: any): void }>();

const { options, loading, error, selectValue, onSearch, onShow } = useMasterDataPicker<RestorationOrgVo>(props, {
  page: restorationOrgApi.page, get: restorationOrgApi.get,
  query: () => ({ qualificationLevel: props.qualificationLevel }),
  matches: row => !props.qualificationLevel || row.qualificationLevel === props.qualificationLevel, emit: value => emit('update:value', value),
});

function renderLabel(option: any) {
  const raw: RestorationOrgVo = option.raw;
  if (!raw) return option.label;
  return h('div', { style: 'display:flex; align-items:center; gap:8px;' }, [
    raw.enabled === false ? h(NTag, { size: 'small', type: 'warning', bordered: false }, { default: () => '停用' }) : null,
    h('span', { style: 'font-weight:600;' }, raw.name || '（无名称）'),
    raw.qualificationLevel
      ? h(NTag, { size: 'small', bordered: false, type: qualType(raw.qualificationLevel) }, { default: () => qualText(raw.qualificationLevel) })
      : null,
    raw.external === false
      ? h(NTag, { size: 'small', bordered: false, type: 'info' }, { default: () => '本馆' })
      : null,
  ]);
}

function renderTag({ option }: any) {
  const raw: RestorationOrgVo = option.raw;
  return h('span', {}, `${raw?.name || option.label}${raw?.enabled === false ? '（停用）' : ''}`);
}

function qualType(l?: string): any {
  return l === 'FIRST' ? 'success' : l === 'SECOND' ? 'warning' : l === 'THIRD' ? 'default' : 'default';
}
function qualText(l?: string) {
  return ({ FIRST: '一级资质', SECOND: '二级资质', THIRD: '三级资质' } as any)[l || ''] || l || '';
}

</script>
