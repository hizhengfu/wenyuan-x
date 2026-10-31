<template>
  <div ref="refViewer" class="model-viewer"></div>
</template>
<script lang="ts" setup>
import qs from 'qs';
import { onMounted, ref, watchEffect } from 'vue';
import { Viewer } from './viewer.js';
import { useEventBus } from '@vueuse/core';

const viewEventBus = useEventBus('viewEventBus');

const props = defineProps({
  modelUrl: { type: String, required: true },
});

const emits = defineEmits(['load', 'click']);

const hash = location.hash ? qs.parse(location.hash) : {};
const options = {
  kiosk: Boolean(hash.kiosk),
  model: hash.model || '',
  preset: hash.preset || '',
  cameraPosition: hash.cameraPosition ? (hash.cameraPosition as string).split(',').map(Number) : null,
};

const refViewer = ref();
let viewer = ref<Viewer>();

onMounted(() => {
  viewer.value = new Viewer(refViewer.value, options);
  loadModel(props.modelUrl);
});

viewEventBus.on((intersect) => {
  emits('click', intersect);
});

function loadModel(modelUrl: string) {
  viewer.value
    ?.load(modelUrl, './')
    .catch(() => {
      emits('load', null);
    })
    .then((gltf) => {
      emits('load', gltf);
    });
}

watchEffect(() => {
  loadModel(props.modelUrl);
});

defineExpose({ viewer });
</script>
<style>
.model-viewer .gui-wrap {
  position: absolute;
  top: 0;
  left: 0;
  pointer-events: none;
}

.model-viewer .gui-wrap > .main {
  pointer-events: all;
  max-height: 100%;
  overflow: auto;
}

.model-viewer .axes {
  width: 100px;
  height: 100px;
  margin: 20px;
  padding: 0;
  position: absolute;
  left: 0;
  bottom: 0;
  z-index: 10;
  pointer-events: none;
}
</style>
