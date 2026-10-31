<template>
  <n-modal
    v-model:show="dialog"
    :style="{ width: '800px' }"
    size="small"
    :showOperate="false"
    :content-style="{ flex: 1, display: 'flex', position: 'relative', flexDirection: 'column', overflow: 'hidden' }"
    :title="props.title"
    :segmented="{
      content: 'soft',
      footer: 'soft',
    }"
    preset="card"
  >
    <slot></slot>
  </n-modal>
</template>
<script lang="ts" setup>
import { computed } from 'vue';

const emits = defineEmits(['update:show']);

const props = defineProps({
  show: { type: Boolean, default: false },
  title: { type: String, default: '编辑' },
});

const dialog = computed<boolean>({
  get() {
    return props.show;
  },
  set(value) {
    emits('update:show', value);
  },
});
</script>
