<template>
  <NSpace vertical size="small">
    <NSpace v-for="archive in archives" :key="archive.id" vertical size="small">
      <NText depth="3">{{ archive.phase === 'REGISTERED' ? '初次登记' : archive.phase === 'RECORDED' ? '本版保管记录' : '本版财务核对' }}封存原件</NText>
      <HFileList :files="archive.files || []" :original-url="(fileId: string) => originalUrl(archive.id, fileId)" :revision="[archive?.id, archive?.digest]" show-download-btn />
    </NSpace>
  </NSpace>
</template>
<script setup lang="ts">
import { type CollectionReadonlyFile } from '../utils/readonlyFiles';
defineProps<{
  archives?: { id: string; phase: string; digest?: string; files?: CollectionReadonlyFile[] }[];
  originalUrl: (archiveId: string, fileId: string) => string;
}>();
</script>
