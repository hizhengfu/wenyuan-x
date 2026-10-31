<template>
  <div>
    <HFileList v-if="files.length && caseId" :files="readonlyFiles(value, archiveFiles)" :original-url="originalUrl"
      :revision="[caseType, caseId, archiveId, value, archiveFiles]" show-download-btn />
    <HFileList v-else-if="files.length" :value="files" show-download-btn />
    <NText v-else depth="3">未上传</NText>
  </div>
</template>
<script setup lang="ts">
import { computed } from 'vue';
import { readonlyFiles, readonlyFileIds, type CollectionReadonlyFile } from '../utils/readonlyFiles';
const props = defineProps<{ caseType: 'change' | 'deregistration' | 'transfer' | 'restoration' | 'restoration-closeout' | 'restoration-restricted'; caseId?: string | null; archiveId?: string | null; value?: string | null; archiveFiles?: CollectionReadonlyFile[] }>();
const files = computed(() => readonlyFileIds(props.value));
function originalUrl(fileId: string) {
  if (!props.caseId) return undefined;
  if ((props.archiveId || props.archiveFiles !== undefined) && !props.archiveFiles?.some(file => (file.fileId || file.id) === fileId)) return undefined;
  const base = props.caseType === 'restoration-restricted' ? '/collection/restoration/' + encodeURIComponent(props.caseId) + '/restricted' : '/collection/' + props.caseType + '/' + encodeURIComponent(props.caseId);
  const stage = props.caseType === 'change' && props.archiveId ? encodeURIComponent(props.archiveId) + '/' : '';
  return base + '/original/' + stage + encodeURIComponent(fileId);
}
</script>
