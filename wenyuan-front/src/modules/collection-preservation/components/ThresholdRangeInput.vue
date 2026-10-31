<template>
  <div class="pv-threshold-range">
    <NDataTable :columns="columns" :data="rows" size="small" :bordered="false" />
  </div>
</template>

<script setup lang="ts">
import { computed, h } from 'vue';
import { NDataTable, NInputNumber } from 'naive-ui';

const props = defineProps<{
  model: {
    l1MinValue?: number | null; l1MaxValue?: number | null;
    l2MinValue?: number | null; l2MaxValue?: number | null;
    l3MinValue?: number | null; l3MaxValue?: number | null;
    l4MinValue?: number | null; l4MaxValue?: number | null;
  };
  unit?: string;
}>();

const emit = defineEmits<{ (e: 'update:model', v: any): void }>();

const rows = computed(() => [
  { level: '一级 (红)', key: 'l1' },
  { level: '二级 (橙)', key: 'l2' },
  { level: '三级 (黄)', key: 'l3' },
  { level: '四级 (蓝)', key: 'l4' },
]);

function inputCell(field: string) {
  return h(NInputNumber, {
    value: props.model[field as keyof typeof props.model] ?? null,
    'onUpdate:value': (v: number | null) => emit('update:model', { ...props.model, [field]: v }),
    style: 'width: 100%',
    placeholder: '—',
  });
}

const columns = computed(() => [
  { title: '等级', key: 'level', width: 120, ellipsis: { tooltip: true }, resizable: true },
  { title: '下限', key: 'min', render: (r: any) => inputCell(`${r.key}MinValue`) },
  { title: `上限${props.unit ? ` (${props.unit})` : ''}`, key: 'max', render: (r: any) => inputCell(`${r.key}MaxValue`) },
]);
</script>
