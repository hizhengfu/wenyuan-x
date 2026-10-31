<template>
  <NDataTable :columns="columns" :data="rows" :pagination="false" :scroll-x="660" size="small" />
</template>
<script setup lang="ts">
import { collectionNatureLabel } from '../utils/collectionNature';
import { computed, h } from 'vue';
import { NText } from 'naive-ui';
import { formatLocalDateValue } from '@/utils';
import { dictText } from '../utils/dictRender';
import { changedFields } from '../utils/collectionChange';
import type { CollectionCatalogContentVo } from '../typings/collection';
const props = defineProps<{ before?: CollectionCatalogContentVo; after?: CollectionCatalogContentVo }>();
const rows = computed(() => changedFields(props.before, props.after));
const dicts: Record<string, string> = { level: 'col_relic_level', texture: 'col_texture', source: 'col_source', completeness: 'col_completeness', preserveState: 'col_preserve' };
const nestedLabels: Record<string, string> = { kind:'类型', name:'名称', condition:'状况', quantity:'数量', unit:'单位', remark:'备注', height:'通高', mouth:'口径', bottom:'底径', belly:'腹径', length:'长', width:'宽', diameter:'直径' };
function display(value: any): string {
  if (value == null || value === '') return '未填写';
  if (Array.isArray(value)) return value.map(display).join('；');
  if (typeof value === 'object') return Object.entries(value).map(([key, val]) => `${nestedLabels[key] || key}：${display(val)}`).join('，');
  return String(value);
}
function valueCell(row: typeof rows.value[number], side: 'before' | 'after') {
  const value = row[side];
  if (row.key === 'nature') return collectionNatureLabel(value);
  if (dicts[row.key] && value != null) return dictText(dicts[row.key], value);
  return h(NText, { type: side === 'after' ? 'warning' : undefined }, () => row.key === 'acquireDate' && value ? formatLocalDateValue(value as number) : display(value));
}
const columns = [
  { title:'变更项目', key:'label', minWidth: 100, resizable: true, ellipsis: { tooltip: true }, width:130 },
  { title:'变更前', key:'before', resizable: true, minWidth:250, render:(r: typeof rows.value[number]) => valueCell(r,'before') },
  { title:'拟变更为', key:'after', resizable: true, minWidth:250, render:(r: typeof rows.value[number]) => valueCell(r,'after') },
];
</script>
