<template>
  <NDrawer v-model:show="visible" :width="'min(640px, 100vw)'" :auto-focus="false">
    <NDrawerContent :title="title" closable>
      <NAlert v-if="error" type="error">{{ error }} <NButton text @click="retry">重新读取</NButton></NAlert>
      <NSpin :show="loading"
        ><HFileList v-if="files.length" :value="files" /><NEmpty
          v-else-if="!loading && !error"
          description="当前记录没有可读取的文件"
      /></NSpin>
    </NDrawerContent>
  </NDrawer>
</template>
<script setup lang="ts">
import { onBeforeUnmount, onDeactivated, ref, watch } from 'vue';
import { useAuthStore } from '@/store';
import { collectionApi } from '../api/collection';
import type { CollectionSearchItem } from '../api/search';
const auth = useAuthStore(),
  visible = ref(false),
  title = ref(''),
  files = ref<string[]>([]),
  loading = ref(false),
  error = ref('');
let request = 0,
  item: CollectionSearchItem | null = null;
const identity = () => `${auth.userInfo?.id}:${auth.identityRevision}:${auth.identitySyncing}`;
async function open(value: CollectionSearchItem) {
  item = value;
  const version = ++request,
    key = identity();
  visible.value = true;
  title.value = value.title;
  files.value = [];
  error.value = '';
  loading.value = true;
  const id = value.relatedCollections[0]?.id;
  try {
    if (!id) throw new Error('未找到当前可读取的关联藏品');
    await collectionApi.get(id);
    const response: any =
      value.type === 'ATTACHMENT' ? await collectionApi.attachments(id) : await collectionApi.media(id);
    if (version !== request || key !== identity() || !visible.value) return;
    const entries = (response.data || []).filter((row: any) =>
      value.type === 'MEDIA_FOLDER' ? row.folderId === value.id : row.id === value.id,
    );
    files.value = entries.map((row: any) => row.fileId).filter(Boolean);
    if (!entries.length && value.type !== 'MEDIA_FOLDER') throw new Error('文件记录已变化或已不可读取，请重新检索');
  } catch (failure: any) {
    if (version === request && key === identity() && visible.value) error.value = failure?.message || '文件读取失败';
  } finally {
    if (version === request) loading.value = false;
  }
}
function retry() {
  if (item) void open(item);
}
function close() {
  ++request;
  visible.value = false;
  files.value = [];
  item = null;
  loading.value = false;
}
watch(
  visible,
  (show) => {
    if (!show) close();
  },
  { flush: 'sync' },
);
watch(identity, close, { flush: 'sync' });
onDeactivated(close);
onBeforeUnmount(close);
defineExpose({ open, close });
</script>
