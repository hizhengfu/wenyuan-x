<template>
  <n-tooltip trigger="hover" class="print-hidden">
    <template #trigger>
      <n-button text @click="handlePdf" v-bind="$attrs">
        <n-icon :size="size">
          <IconMdiFilePdfBoxOutline style="color: red"></IconMdiFilePdfBoxOutline>
        </n-icon>
      </n-button>
    </template>
    导出PDF文件
  </n-tooltip>
</template>
<script lang="ts" setup>
import html2pdf from 'html2pdf.js';

const props = defineProps({
  pdfSet: { type: [Object, String] },
  size: { type: String, default: '20px' },
  fileName: { type: String, default: '导出文件' },
});

/**
 * 点开开始打印
 * */
function handlePdf() {
  if (props.pdfSet) {
    const opt = {
      margin: 0,
      filename: props.fileName,
      image: { type: 'jpeg', quality: 0.98 },
      html2canvas: { scale: 2 },
      jsPDF: {
        unit: 'mm',
        format: 'a4',
        orientation: 'portrait',
      },
    };
    html2pdf().set(opt).from(props.pdfSet.$el).save();
  }
}
</script>
