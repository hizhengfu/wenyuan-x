<template>
  <n-tooltip trigger="hover">
    <template #trigger>
      <n-button @click="handlePrint" v-bind="$attrs">
        <template #icon>
          <n-icon :size="iconSize">
            <IconMdiPrinter></IconMdiPrinter>
          </n-icon>
        </template>
      </n-button>
    </template>
    <slot>点击开始打印</slot>
  </n-tooltip>
</template>
<script lang="ts" setup>
import { notification } from '@/utils';
import { compiledTemplate, htmlPrint, tableColumnData } from './PrintUtils.ts';

const props = defineProps({
  template: { type: String, required: true }, //打印模版
  data: { type: Array }, //打印数据
  columns: {
    type: Array,
    default() {
      return [];
    },
  },
  style: { type: String },
  title: { type: String, default: '打印' },
  iconSize: { type: String, default: '20px' },
});

/**
 * 根据模版构建内容
 * */
function buildPrintContent(): string {
  const { template: htmlTemplate, data, columns } = props;

  let printData: any[] | undefined = data;
  if (columns && columns.length > 0) {
    printData = tableColumnData(columns, data);
  }

  return compiledTemplate(htmlTemplate, printData);
}

/**
 * 点开开始打印
 * */
function handlePrint() {
  const { data } = props;
  if (!data || data.length === 0) {
    notification({ type: 'error', title: '请注意', content: '请选择需要打印的数据后再点击打印' });
    return;
  }
  //构建打印内容，并根据模版自动分页
  let printable = buildPrintContent();
  if (printable) {
    htmlPrint(printable, '准备打印');
  }
}
</script>
