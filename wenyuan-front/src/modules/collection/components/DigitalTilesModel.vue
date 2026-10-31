<template><div ref="host" class="digital-tiles-model" /></template>
<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref, watch } from 'vue';
import { openDigitalTiles } from '../preview/digitalTilesEngine';
const props = defineProps<{ filePath: string; fileType?: string }>();
const emit = defineEmits<{ (e: 'load'): void; (e: 'error', error: unknown): void }>();
const host = ref<HTMLElement>();
let current: ReturnType<typeof openDigitalTiles> | undefined;
let epoch = 0;
function stop() {
  epoch++;
  current?.close();
  current = undefined;
}
function load() {
  stop();
  if (!host.value) return;
  const ticket = epoch;
  current = openDigitalTiles(host.value, props.filePath, {
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
.digital-tiles-model {
  width: 100%;
  height: 100%;
}
.digital-tiles-model :deep(canvas) {
  display: block;
}
</style>
