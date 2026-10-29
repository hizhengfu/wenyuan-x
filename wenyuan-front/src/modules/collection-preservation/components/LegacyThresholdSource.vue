<template>
  <NSpace vertical>
    <NAlert type="warning" :bordered="false">
      请逐项核对原馆级覆盖、材质与位置规则、停用记录及候选去向。旧版混合材质的最敏感区间与新版逐材质规则并不等同，数值复制不能代替实际评估。
    </NAlert>
    <NAlert v-for="issue in conversion?.issues || []" :key="issue" type="error">{{ issue }}</NAlert>
    <NCollapse>
      <NCollapseItem
        v-for="table in source?.tables || []"
        :key="table.name"
        :name="table.name"
        :title="`${tableNames[table.name] || table.name}：${table.present ? `${tableRows(table).length} 条原记录` : '当前未发现可见原表'}`"
      >
        <NText depth="3">原值按读取时的字段保留；空值显示为“未登记”。二进制扩展字段按 Base64 保存。</NText>
        <NDataTable
          :columns="columns(table)"
          :data="tableRows(table)"
          :row-key="(row: any) => row.id"
          :scroll-x="Math.max(1000, tableColumns(table).length * 160)"
          :pagination="{ pageSize: 10 }"
        />
      </NCollapseItem>
    </NCollapse>
    <NDataTable :columns="dispositionColumns" :data="conversion?.dispositions || []" :pagination="{ pageSize: 10 }" />
  </NSpace>
</template>
<script setup lang="ts">
defineProps<{ source?: any; conversion?: any }>();
const tableNames: Record<string, string> = {
  pv_material_threshold: '原材质阈值',
  pv_location_threshold: '原位置阈值',
  pv_org_threshold: '原馆级阈值',
};
const names: Record<string, string> = {
  id: '原编号',
  org_id: '原馆标识',
  location_id: '原位置标识',
  texture: '原材质',
  metric_code: '原指标编码',
  enabled: '原启用值',
  status: '原状态值',
  national_standard_id: '原标准依据',
  expert_opinion: '原专家意见',
  remarks: '原备注',
  override_reason: '原覆盖原因',
};
const states: Record<string, string> = {
  COPIED_TO_DRAFT: '复制为候选草稿',
  INACTIVE_RETAINED: '原非启用资料保留',
  OVERRIDDEN_BY_ORGANIZATION: '原馆级规则覆盖',
  REVIEW_REQUIRED: '待人工核查',
};
// Public permanent originals omit empty arrays for absent tables. Keep the original immutable.
const missingArray = (table: any, key: string) =>
  table.present === false && !Object.prototype.hasOwnProperty.call(table, key);
const tableColumns = (table: any) => missingArray(table, 'columns') ? [] : table.columns;
const tableRows = (table: any) =>
  (table.present === true || table.present === false) && !Object.prototype.hasOwnProperty.call(table, 'rows')
    ? [] : table.rows;
const columns = (table: any) =>
  tableColumns(table).map((column: any) => ({
    title:
      names[column.name] ||
      column.name.replace(
        /^l([1-4])_(min|max)_value$/,
        (_: string, level: string, bound: string) => `原 L${level} ${bound === 'min' ? '下限' : '上限'}`,
      ),
    key: column.name,
    width: 180, ellipsis: { tooltip: true }, resizable: true,
    render: (row: any) => row[column.name] ?? '未登记',
  }));
const dispositionColumns = [
  { title: '原类别', key: 'table', width: 120, ellipsis: { tooltip: true }, resizable: true, render: (row: any) => tableNames[row.table] || row.table },
  { title: '原编号', key: 'id', width: 160, ellipsis: { tooltip: true }, resizable: true },
  { title: '候选去向', key: 'state', width: 160, ellipsis: { tooltip: true }, resizable: true, render: (row: any) => states[row.state] || row.state },
  { title: '逐项核对说明', key: 'explanation', ellipsis: { tooltip: true }, resizable: true, width: 280 },
];
</script>
