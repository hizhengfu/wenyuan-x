<template>
  <n-modal
    v-model:show="dialog"
    v-bind="$attrs"
    :style="{ width: width }"
    :title="props.title"
    :segmented="{
      content: 'soft',
      footer: 'soft',
    }"
    preset="card"
  >
    <slot />
  </n-modal>
</template>
<script lang="ts" setup>
import { computed } from 'vue';

const emits = defineEmits(['update:show']);

const props = defineProps({
  show: { type: Boolean, default: false },
  width: { type: String, default: '80%' },
  title: { type: String, default: '查看' },
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
