<template><div ref="host" class="digital-compressed-model" /></template>
<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref, watch } from 'vue';
import { createDigitalCompressedEngine } from '../preview/digitalCompressedEngine';
const props = defineProps<{ filePath: string; fileType?: string }>();
const emit = defineEmits<{ (e: 'load'): void; (e: 'error', error: unknown): void }>();
const host = ref<HTMLElement>();
let current: ReturnType<typeof createDigitalCompressedEngine> | undefined;
let epoch = 0;
function stop() {
  epoch++;
  current?.dispose();
  current = undefined;
}
function load() {
  stop();
  if (!host.value) return;
  const ticket = epoch;
  current = createDigitalCompressedEngine(host.value, props.filePath, {
    loaded: () => {
      if (ticket === epoch) emit('load');
    },
    failed: (error) => {
      if (ticket === epoch) emit('error', error);
    },
  });
}
onMounted(load);
watch(() => props.filePath, load);
onBeforeUnmount(stop);
</script>
<style scoped>
.digital-compressed-model {
  width: 100%;
  height: 100%;
}
.digital-compressed-model :deep(canvas) {
  display: block;
}
</style>
