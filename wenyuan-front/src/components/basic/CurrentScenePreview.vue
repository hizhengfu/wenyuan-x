<template>
  <div class="current-scene-preview" :class="$attrs.class" :style="$attrs.style as StyleValue" :aria-busy="loading">
    <Model
      v-if="display && kind === 'model'"
      :key="display.id"
      v-bind="sceneAttrs()"
      :file-path="display.url"
      :enable-draco="true"
      draco-dir="./draco/gltf/"
      class="scene-content"
      v-on="{ load: display.loaded, error: display.failed }"
    />
    <Tileset
      v-else-if="display && kind === 'tileset'"
      :key="display.id"
      v-bind="sceneAttrs()"
      :url="display.url"
      class="scene-content"
      v-on="{ load: display.loaded, error: display.failed }"
    />
    <iframe
      v-else-if="display"
      :key="display.id"
      :src="display.url"
      title="全景预览"
      class="scene-content"
      allowfullscreen
      referrerpolicy="no-referrer"
      v-on="{ load: display.loaded, error: display.failed }"
    />
    <div v-if="loading" role="status" class="scene-status">正在加载预览…</div>
    <div v-if="error" role="alert" class="scene-status">{{ error }} <button @click="refresh">重新加载</button></div>
  </div>
</template>
<script setup lang="ts">
import { defineAsyncComponent, onErrorCaptured, ref, useAttrs } from 'vue';
import type { StyleValue } from 'vue';
import { useCurrentImageLifecycle } from '@/utils/currentImage';
defineOptions({ inheritAttrs: false });
const props = defineProps<{ src: string; kind: 'model' | 'tileset' | 'panorama'; revision?: string | number }>();
const emit = defineEmits<{ (event: 'load'): void; (event: 'error', error: unknown): void }>();
const Model = defineAsyncComponent(() => import('vue-3d-loader').then((module) => module.vue3dLoader));
const Tileset = defineAsyncComponent(() => import('./CesiumTilesetPreview.vue').then((module) => module.default));
const loading = ref(false),
  error = ref('');
const display = ref<{ id: number; url: string; loaded: () => void; failed: (reason: unknown) => void }>();
const attrs = useAttrs();
function sceneAttrs() {
  const { class: _class, style: _style, ...rest } = attrs;
  return rest;
}
onErrorCaptured((reason) => {
  display.value?.failed(reason);
  return false;
});
let generation = 0;
function clear() {
  generation++;
  display.value = undefined;
  loading.value = false;
  error.value = '';
}
function reload() {
  if (!props.src) return;
  const ticket = generation;
  const failed = (reason: unknown) => {
    if (ticket !== generation) return;
    clear();
    error.value = '预览加载失败，请核对文件、授权和解码资源。';
    emit('error', reason);
  };
  try {
    const url = new URL(props.src, location.href);
    if (!/^https?:$/.test(url.protocol) || url.username || url.password) throw new Error('不支持的场景地址');
    loading.value = true;
    display.value = {
      id: ticket,
      url: url.href,
      failed,
      loaded: () => {
        if (ticket === generation) {
          loading.value = false;
          emit('load');
        }
      },
    };
  } catch (reason) {
    failed(reason);
  }
}
const { refresh } = useCurrentImageLifecycle(() => [props.src, props.kind, props.revision], clear, reload);
</script>
<style scoped>
.current-scene-preview {
  width: 100%;
  height: 100%;
  position: relative;
  overflow: hidden;
}
.scene-content {
  width: 100%;
  height: 100%;
  border: 0;
  display: block;
}
.scene-status {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background: #fff9;
}
</style>
