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
    :placeholder="error || placeholder || '选择修复项目'"
    :render-label="renderLabel"
    :render-tag="renderTag"
    @search="onSearch"
    @update:value="selectValue"
    @update:show="onShow"
  />
</template>

<script setup lang="ts">
import { h } from 'vue';
import { useCurrentRecordPicker } from '../composables/useCurrentRecordPicker';
import { NSelect, NTag } from 'naive-ui';
import { restorationApi } from '../api/restoration';
import type { RestorationVo } from '../typings';

const props = defineProps<{
  value?: string | string[] | null;
  placeholder?: string;
  multiple?: boolean;
  disabled?: boolean;
  /** 只列已通过审批的项目，默认 false（全部 flow 状态可见） */
  onlyPassed?: boolean;
  /** 只列指定 state（DRAFT/ONGOING/DONE/CANCELLED） */
  state?: string;
}>();

const emit = defineEmits<{ (e: 'update:value', v: any): void }>();

const states = new Set(['DRAFT', 'ONGOING', 'DONE', 'CANCELLED']);
const flowStatuses = new Set(['DRAFT', 'AUDIT', 'REVIEWED', 'PASS', 'REJECT', 'CANCEL', 'COMPLETE', 'VOID']);
const { options, loading, error, selectValue, onSearch, onShow } = useCurrentRecordPicker<RestorationVo>(props, {
  page: restorationApi.page, get: restorationApi.get,
  query: () => ({ ...(props.onlyPassed ? { flowStatus: 'PASS' } : {}), ...(props.state ? { state: props.state } : {}) }),
  validate: row => typeof row.collectionId === 'string' && !!row.collectionId.trim()
    && states.has(row.state!) && flowStatuses.has(row.flowStatus!),
  label: row => `${row.collectionTotalRegNo || ''} ${row.collectionName || row.collectionId}${row.restoreOrg ? ` · ${row.restoreOrg}` : ''}`.trim(),
  matches: row => (!props.onlyPassed || row.flowStatus === 'PASS') && (!props.state || row.state === props.state),
  emit: value => emit('update:value', value),
});

/** 自定义 label：登记号加粗 + 名称 + 审批状态徽标 + 修复状态徽标 */
function renderLabel(option: any) {
  const raw: RestorationVo = option.raw;
  if (!raw) return option.label;
  return h('div', { style: 'display:flex; align-items:center; gap:8px;' }, [
    h('span', { style: 'font-weight:600;' }, raw.collectionTotalRegNo || '（无登记号）'),
    h('span', {}, raw.collectionName || ''),
    raw.flowStatus
      ? h(NTag, { size: 'small', type: flowStatusType(raw.flowStatus), bordered: false }, { default: () => flowStatusText(raw.flowStatus) })
      : null,
    raw.state
      ? h(NTag, { size: 'small', type: stateType(raw.state), bordered: false }, { default: () => stateText(raw.state) })
      : null,
  ]);
}

function renderTag({ option }: any) {
  const raw: RestorationVo = option.raw;
  if (!raw) return option.label;
  return h('span', {}, `${raw.collectionTotalRegNo || ''} ${raw.collectionName || raw.id || ''}`.trim());
}

function flowStatusType(s?: string): any {
  return s === 'PASS' ? 'success' : s === 'REJECT' ? 'error' : s === 'AUDIT' ? 'warning' : 'default';
}
function flowStatusText(s?: string) {
  return { DRAFT: '草稿', AUDIT: '审批中', REVIEWED: '已审核', COMPLETE: '已完成', VOID: '已作废', PASS: '已通过', REJECT: '已驳回', CANCEL: '已撤销' }[s || ''] || s || '';
}
function stateType(s?: string): any {
  return s === 'DONE' ? 'success' : s === 'ONGOING' ? 'info' : s === 'CANCELLED' ? 'error' : 'default';
}
function stateText(s?: string) {
  return { DRAFT: '草稿', ONGOING: '进行中', DONE: '已竣工', CANCELLED: '已作废' }[s || ''] || s || '';
}

</script>
