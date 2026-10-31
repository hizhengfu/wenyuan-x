<template>
 <NSpace><HFileList :files="ids.map(fileId => ({ fileId, name: '库区值班签署原件' }))"
  :original-url="(fileId: string) => `/collection/warehouse/${encodeURIComponent(warehouseId)}/security-duty/original/${encodeURIComponent(fileId)}`"
  :revision="[warehouseId, fileIds]" show-download-btn /></NSpace>
</template>
<script setup lang="ts">
import {computed} from 'vue';
const props=defineProps<{warehouseId:string;fileIds?:string}>();
const ids=computed(()=>[...new Set((props.fileIds||'').split(',').map(s=>s.trim()).filter(Boolean))]);
</script>
