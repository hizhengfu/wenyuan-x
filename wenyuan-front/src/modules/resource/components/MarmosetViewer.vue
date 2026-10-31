<template>
  <div class="marmoset-viewer">
    <div ref="runtimeHost" class="marmoset-runtime-host" />
    <div v-if="loading" class="viewer-overlay">
      <n-spin size="large" />
      <span>正在加载 MVIEW 场景…</span>
    </div>
    <div v-else-if="error" class="viewer-overlay viewer-error">
      <n-icon size="42"><IconMdiAlertCircleOutline /></n-icon>
      <span>{{ error }}</span>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import {
  loadMarmosetRuntime,
  type MarmosetViewerInstance,
} from '../utils/marmosetViewerRuntime';

const props = defineProps<{
  src: string;
}>();

const runtimeHost = ref<HTMLElement | null>(null);
const loading = ref(false);
const error = ref('');

let viewer: MarmosetViewerInstance | undefined;
let resizeObserver: ResizeObserver | undefined;
let loadSequence = 0;

function viewerSize() {
  const rect = runtimeHost.value?.getBoundingClientRect();
  return {
    width: Math.max(1, Math.round(rect?.width || 800)),
    height: Math.max(1, Math.round(rect?.height || 600)),
  };
}

function resizeViewer() {
  if (!viewer) return;
  const { width, height } = viewerSize();
  viewer.resize(width, height);
}

function disposeViewer() {
  try {
    viewer?.unload();
  } finally {
    viewer = undefined;
    runtimeHost.value?.replaceChildren();
  }
}

async function mountViewer() {
  const sequence = ++loadSequence;
  disposeViewer();
  error.value = '';
  if (!props.src) return;

  loading.value = true;
  try {
    await nextTick();
    const runtime = await loadMarmosetRuntime();
    if (sequence !== loadSequence || !runtimeHost.value) return;

    const { width, height } = viewerSize();
    const nextViewer = new runtime.WebViewer(width, height, props.src);
    nextViewer.onLoad = () => {
      if (sequence === loadSequence) loading.value = false;
    };
    nextViewer.onError = (cause?: unknown) => {
      if (sequence !== loadSequence) return;
      loading.value = false;
      error.value = cause instanceof Error ? cause.message : 'MVIEW 场景加载失败';
    };
    runtimeHost.value.replaceChildren(nextViewer.domRoot);
    viewer = nextViewer;
    nextViewer.loadScene();
  } catch (cause) {
    if (sequence !== loadSequence) return;
    loading.value = false;
    error.value = cause instanceof Error ? cause.message : 'Marmoset Viewer 初始化失败';
  }
}

onMounted(() => {
  resizeObserver = new ResizeObserver(resizeViewer);
  if (runtimeHost.value) resizeObserver.observe(runtimeHost.value);
  mountViewer();
});

watch(() => props.src, mountViewer);

onBeforeUnmount(() => {
  loadSequence++;
  resizeObserver?.disconnect();
  disposeViewer();
});
</script>

<style scoped>
.marmoset-viewer,
.marmoset-runtime-host {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 320px;
  overflow: hidden;
  background: #17191d;
}

.marmoset-runtime-host :deep(> *) {
  width: 100%;
  height: 100%;
}

.viewer-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
  align-items: center;
  justify-content: center;
  color: #d5d8dc;
  background: rgb(23 25 29 / 78%);
  pointer-events: none;
}

.viewer-error {
  padding: 24px;
  color: #ffb4ab;
  text-align: center;
}
</style>
