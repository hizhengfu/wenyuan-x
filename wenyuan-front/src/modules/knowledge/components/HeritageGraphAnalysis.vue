<template>
  <section class="heritage-graph-analysis">
    <iframe v-if="frameSrc" :src="frameSrc" :title="title" />
    <n-spin v-else size="large" description="正在加载知识图谱" />
  </section>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue';
import analysisHtml from '../assets/heritage-graph-analysis.html?raw';
import graphStyles from '@/platforms/jiangxi/assets/graph/styles.css?raw';
import graphDataScript from '@/platforms/jiangxi/assets/graph/data.js?raw';
import graphAppScript from '@/platforms/jiangxi/assets/graph/app.js?raw';

type GraphType = '书院' | '宗祠';

const props = defineProps<{
  type: GraphType;
  title: string;
  subtitle: string;
}>();

const frameSrc = ref('');

function safeInlineScript(script: string) {
  const scriptEndTag = '<' + '/script>';
  return script.replaceAll(scriptEndTag, '<\\/script>');
}

function buildGraphHtml() {
  const scriptEndTag = '<' + '/script>';
  const dataScriptTag = '<script src="./data.js">' + scriptEndTag;
  const appScriptTag = '<script src="./app.js">' + scriptEndTag;

  return analysisHtml
    .replaceAll('{{PAGE_TYPE}}', props.type)
    .replaceAll('{{PAGE_TITLE}}', props.title)
    .replaceAll('{{PAGE_SUBTITLE}}', props.subtitle)
    .replace('<link rel="stylesheet" href="./styles.css" />', `<style>${graphStyles}</style>`)
    .replace(dataScriptTag, `<script>${safeInlineScript(graphDataScript)}${scriptEndTag}`)
    .replace(appScriptTag, `<script>${safeInlineScript(graphAppScript)}${scriptEndTag}`);
}

function refreshFrame() {
  if (frameSrc.value) URL.revokeObjectURL(frameSrc.value);
  frameSrc.value = URL.createObjectURL(new Blob([buildGraphHtml()], { type: 'text/html;charset=utf-8' }));
}

watch(() => [props.type, props.title, props.subtitle], refreshFrame, { immediate: true });

onBeforeUnmount(() => {
  if (frameSrc.value) URL.revokeObjectURL(frameSrc.value);
});
</script>

<style scoped>
.heritage-graph-analysis {
  display: flex;
  width: 100%;
  min-width: 0;
  height: calc(100vh - 166px);
  min-height: 720px;
  overflow: hidden;
  background: #eef2f6;
}

.heritage-graph-analysis iframe {
  display: block;
  width: 100%;
  height: 100%;
  border: 0;
  background: #eef2f6;
}

.heritage-graph-analysis :deep(.n-spin-container) {
  width: 100%;
  height: 100%;
}
</style>
