<template>
  <NSpace vertical>
    <NButton
      v-for="file in files"
      :key="file.fileId"
      text
      type="primary"
      :loading="loading === file.fileId"
      :disabled="!eligible || !!loading"
      @click="download(file)"
      >下载原件：{{ displayName(file) }}</NButton
    >
    <NText v-for="file in files" :key="`path-${file.fileId}`" depth="3">{{ displayPath(file) }}</NText>
    <NText v-if="error" type="error">{{ error }}</NText>
    <NText v-if="integrityNotice">{{ integrityNotice }}</NText>
  </NSpace>
</template>
<script setup lang="ts">
import type { CollectionReadonlyFile } from '../utils/readonlyFiles';
type DigitalFile = CollectionReadonlyFile & { fileId: string };
function displayName(file: DigitalFile): string {
  let metadata=file.metadata;
  if(typeof metadata==='string'){try{metadata=JSON.parse(metadata);}catch{metadata=undefined;}}
  return metadata && typeof metadata==='object' && typeof metadata.name==='string' && metadata.name
    ? metadata.name : file.name || '数字藏品原件';
}
function displayPath(file:DigitalFile):string {
  let metadata=file.metadata;
  if(typeof metadata==='string'){try{metadata=JSON.parse(metadata);}catch{metadata=undefined;}}
  return metadata && typeof metadata==='object' && typeof metadata.path==='string' ? metadata.path : file.path || '';
}
import { ref } from 'vue';
import { useCurrentImageLifecycle } from '@/utils/currentImage';
import { saveAs } from 'file-saver';
import { digitalAcquisitionApi } from '../api/digitalAcquisition';
const props = defineProps<{ projectId: string; files: DigitalFile[] }>();
const loading = ref(''),
  error = ref(''),
  integrityNotice = ref('');
let generation = 0;
let abort: AbortController | undefined;
function reset() {
  generation++;
  abort?.abort();
  abort = undefined;
  loading.value = '';
  error.value = '';
  integrityNotice.value = '';
}
const { eligible } = useCurrentImageLifecycle(
  () => [props.projectId, props.files],
  reset,
  () => {},
);
async function download(file: DigitalFile) {
  if (!eligible.value || loading.value || !props.projectId || !props.files.some((item) => item.fileId === file.fileId))
    return;
  const target = props.projectId;
  abort = new AbortController();
  const token = ++generation;
  loading.value = file.fileId;
  error.value = '';
  integrityNotice.value = '';
  try {
    const result: unknown = await digitalAcquisitionApi.original(target, file.fileId, abort.signal);
    if (token !== generation) return;
    const download = result as { blob?: Blob; name?: string; integrity?: string };
    const blob = result instanceof Blob ? result : download?.blob;
    if (!(blob instanceof Blob) || blob.type !== 'application/octet-stream') throw new Error('原件返回格式异常');
    saveAs(blob, download?.name || displayName(file));
    integrityNotice.value =
      download?.integrity === 'sha256-verified'
        ? '下载内容已与留存版本核对一致。'
        : download?.integrity === 'not-recorded'
          ? '该历史附件未留存内容摘要，本次下载未验证内容一致性。'
          : '服务器未提供本次下载的内容核验结果。';
  } catch {
    if (token !== generation) return;
    error.value = '原件读取失败，请核实当前授权及文件状态。';
  } finally {
    if (token === generation) {
      loading.value = '';
      abort = undefined;
    }
  }
}
</script>
