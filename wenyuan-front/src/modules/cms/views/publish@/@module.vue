<template>
  <div class="h-full">
    <n-spin v-if="loading" />
    <n-alert v-else-if="error" type="warning">{{ error }}</n-alert>
    <component v-else-if="currentModule" :is="currentModule" :key="route.params.module" />
  </div>
</template>
<script lang="ts" setup>
import { shallowRef, ref, watch, onMounted, onUnmounted } from 'vue';
import { useRoute } from 'vue-router';
import { useCmsStore } from '../../store/cms';
import { cmsPlugins } from '../../api/plugins';
import { cmsEditor } from '../../plugins/editors';
const cmsStore = useCmsStore();
onMounted(() => { cmsStore.isPublish = true; });
onUnmounted(() => { cmsStore.isPublish = false; });
const route = useRoute();
const currentModule = shallowRef(); const loading = ref(false); const error = ref('');
// 路由快速切换时，只接受最新请求；旧组件先清空，避免显示上一类型的编辑器。
let requestId = 0;
watch(() => route.params.module, async module => {
  const id = ++requestId; currentModule.value = undefined; error.value = ''; loading.value = true;
  try {
    const definitions = await cmsPlugins(); if (id !== requestId) return;
    const definition = definitions.find(item => item.code === String(module || '').toUpperCase());
    if (!definition) { error.value = '该内容类型未注册，请检查栏目模型'; return; }
    // 插件代码负责业务身份，editorKey 负责本地组件选择，两者不要求同名。
    currentModule.value = cmsEditor(definition.editorKey);
    if (!currentModule.value) error.value = `尚未安装“${definition.label}”的管理编辑器`;
  } catch { if (id === requestId) error.value = '内容类型加载失败，请重新进入页面'; }
  finally { if (id === requestId) loading.value = false; }
}, { immediate: true });
</script>
