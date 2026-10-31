<template>
  <NSpace vertical>
    <NText>{{
      definition.targetScope === 'ALL_LOCATIONS'
        ? '适用范围：全馆有效位置（随位置与藏品变化）'
        : '适用范围：所列指定位置'
    }}</NText>
    <NText v-if="definition.locationIds?.length">指定位置标识：{{ definition.locationIds.join('、') }}</NText>
    <NDataTable :columns="columns" :data="rows" :scroll-x="1050" />
    <template v-if="definition.legacyThresholdSource">
      <NAlert type="info"
        >本版共同评估同时包含以下旧阈值原资料及拟转换去向，请本人逐项核对；保留原数值不等于已确认适用于当前对象。</NAlert
      >
      <LegacyThresholdSource
        :source="definition.legacyThresholdSource.source"
        :conversion="definition.legacyThresholdSource.conversion"
      />
    </template>
  </NSpace>
</template>
<script setup lang="ts">
import { computed } from 'vue';
import LegacyThresholdSource from './LegacyThresholdSource.vue';
const props = defineProps<{ definition: Record<string, any> }>();
const rows = computed(() =>
  (props.definition.items || []).map((item: any) => ({
    ...item,
    metric: (props.definition.metrics || []).find((m: any) => m.code === item.metricCode),
  })),
);
const range = (r: any, level: number) => {
  const min = r[`l${level}MinValue`],
    max = r[`l${level}MaxValue`];
  return min == null && max == null ? '未设置' : `${min ?? '无下限'} ～ ${max ?? '无上限'}`;
};
const scopes: Record<string, string> = { GENERAL: '通用', MATERIAL: '材质', LOCATION: '位置例外' };
const columns = [
  {
    title: '类型 / 选择值',
    key: 'scope',
    width: 240, ellipsis: { tooltip: true }, resizable: true,
    render: (r: any) => `${scopes[r.scope] || r.scope} / ${r.selectorKey}`,
  },
  {
    title: '指标 / 单位',
    key: 'metricCode',
    width: 200, ellipsis: { tooltip: true }, resizable: true,
    render: (r: any) =>
      `${r.metric?.name || r.metricCode} / ${r.metric?.missing ? '目录缺失' : r.metric?.unit || '无单位'}${r.metric?.enabled === false ? '（已停用）' : ''}`,
  },
  ...[1, 2, 3, 4].map((level) => ({
    title: `L${level} 范围`,
    key: `l${level}`,
    width: 140, ellipsis: { tooltip: true }, resizable: true,
    render: (r: any) => range(r, level),
  })),
  {
    title: '标准或专家依据',
    key: 'basis',
    minWidth: 180, ellipsis: { tooltip: true }, resizable: true, width: 260,
    render: (r: any) => [r.standardRef, r.expertOpinion, r.remarks].filter(Boolean).join('；') || '未登记',
  },
];
</script>
