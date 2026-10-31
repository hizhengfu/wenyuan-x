<template>
  <n-tooltip trigger="hover" class="print-hidden" placement="left">
    <template #trigger>
      <n-float-button right="20" bottom="20" @click="handlePrint" v-bind="$attrs" class="print-hidden">
        <n-icon :size="size">
          <IconMdiPrinter></IconMdiPrinter>
        </n-icon>
      </n-float-button>
    </template>
    点击开始打印
  </n-tooltip>
</template>
<script lang="ts" setup>
const props = defineProps({
  size: { type: String, default: '20px' },
});
function handlePrint() {
  window.print();
}
</script>
<style scoped lang="scss">
@media print {
  body {
    -webkit-print-color-adjust: exact;
    print-color-adjust: exact;
  }

  .n-layout,
  .n-layout-content,
  .n-scrollbar,
  .n-scrollbar-container {
    height: auto !important;
    overflow: visible !important;
  }

  .page-break {
    page-break-after: always;
  }

  .avoid-break {
    page-break-inside: avoid;
  }
}
</style>
