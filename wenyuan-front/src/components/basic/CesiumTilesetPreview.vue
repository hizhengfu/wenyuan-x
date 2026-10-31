<template><div ref="container" class="cesium-tileset-preview"></div></template>
<script setup lang="ts">
import { onActivated, onBeforeUnmount, onDeactivated, onMounted, ref, watch } from 'vue';
import 'cesium/Build/Cesium/Widgets/widgets.css';
import { openCurrentTileset } from './cesium/currentTilesetEngine';
const props = defineProps<{ url: string }>();
const emit = defineEmits<{ (event: 'load'): void; (event: 'error', reason: unknown): void }>();
const container = ref<HTMLElement>();
let active = false;
let session: ReturnType<typeof openCurrentTileset> | undefined;
function stop() {
  session?.close();
  session = undefined;
}
function start() {
  if (!active || session || !container.value || !props.url) return;
  session = openCurrentTileset({
    container: container.value,
    url: props.url,
    onLoad: () => emit('load'),
    onError: (reason) => emit('error', reason),
  });
}
function enter() {
  active = true;
  start();
}
function leave() {
  active = false;
  stop();
}
watch(
  () => props.url,
  () => {
    stop();
    start();
  },
  { flush: 'sync' },
);
onMounted(enter);
onActivated(enter);
onDeactivated(leave);
onBeforeUnmount(leave);
</script>
<style scoped>
.cesium-tileset-preview {
  width: 100%;
  height: 100%;
  position: relative;
  overflow: hidden;
}
</style>
