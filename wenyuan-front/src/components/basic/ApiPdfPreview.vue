<template>
  <CurrentDocumentPreview v-if="targetPath" :src="targetPath" :kind="previewType || 'pdf'" class="api-pdf-preview" />
</template>
<script lang="ts" setup>
import { computed } from 'vue';
import { apiUrl } from '@/utils/path';
import CurrentDocumentPreview from './CurrentDocumentPreview.vue';

const props = defineProps({
  path: { type: String }, //文件内容
  previewType: { type: String }, //预览类型
});

/**
 * 生成目标路径
 * */
const targetPath = computed(() => {
  if (!props.path) return null;
  return apiUrl(props.path as string);
});
</script>
<style scoped>
.api-pdf-preview {
  display: block;
  width: 100%;
  min-height: 600px;
  height: 100%;
  border: 0;
}
</style>
