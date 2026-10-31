<template>
  <n-tooltip trigger="hover" class="no-print">
    <template #trigger>
      <n-button text @click="handlePrint" v-bind="$attrs" class="no-print">
        <n-icon :size="size">
          <IconMdiPrinter></IconMdiPrinter>
        </n-icon>
      </n-button>
    </template>
    点击开始打印
  </n-tooltip>
</template>
<script lang="ts" setup>
import printJS from 'print-js';
import { PropType } from 'vue';
import defaultStyle from './printStyle.css?raw';

const props = defineProps({
  printable: { type: [Object, String] },
  type: { type: String as PropType<'pdf' | 'html' | 'image' | 'json' | 'raw-html'>, default: 'html' },
  style: { type: String },
  size: { type: String, default: '20px' },
});

/**
 * 点开开始打印
 * */
function handlePrint() {
  const { printable, type, style } = props;
  if (printable) {
    printJS({
      //@ts-ignored
      printable: printable?.$el || printable,
      scanStyles: false,
      style: defaultStyle + (style || ''),
      type: type,
      showModal: true,
      modalMessage: '准备打印',
    });
  }
}
</script>
