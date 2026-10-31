<template>
  <div class="current-document-preview" :aria-busy="loading">
    <iframe
      v-if="display?.html"
      :key="display.id"
      :srcdoc="display.html"
      sandbox=""
      title="表格文档预览"
      class="file-preview-document"
      referrerpolicy="no-referrer"
      v-on="{ error: display.fail }"
    />
    <VueOfficePdf
      v-else-if="display?.url && display.pdf"
      :key="display.id"
      :src="display.url"
      class="file-preview-document"
      v-on="{ error: display.fail }"
    />
    <iframe
      v-else-if="display?.url"
      :key="display.id"
      :src="display.url"
      title="文档预览"
      class="file-preview-document"
      referrerpolicy="no-referrer"
      v-on="{ error: display.fail }"
    />
    <div v-if="loading" role="status">正在读取当前文档…</div>
    <div v-if="error" role="alert">{{ error }} <button @click="refresh">重新读取文档</button></div>
  </div>
</template>
<script setup lang="ts">
import { ref } from 'vue';
import { useCurrentImageLifecycle } from '@/utils/currentImage';
import { isApplicationDocumentUrl, readCurrentDocument } from '@/utils/currentDocument';
import VueOfficePdf from '@vue-office/pdf';
const props = defineProps<{ src: string; kind: string; revision?: string | number }>();
const loading = ref(false),
  error = ref('');
const display = ref<{ id: number; url?: string; html?: string; pdf?: boolean; fail: () => void }>();
let generation = 0;
let abort: AbortController | undefined;
let ownedUrl: string | undefined;
function clear() {
  generation++;
  abort?.abort();
  abort = undefined;
  display.value = undefined;
  if (ownedUrl) URL.revokeObjectURL(ownedUrl);
  ownedUrl = undefined;
  loading.value = false;
  error.value = '';
}
async function reload() {
  const ticket = generation,
    source = props.src;
  if (!source) return;
  const fail = () => {
    if (ticket !== generation) return;
    clear();
    error.value = '文档读取失败，请核对当前授权、文件状态和预览转换入口。';
  };
  try {
    const url = new URL(source, window.location.href);
    if (!/^https?:$/.test(url.protocol) || url.username || url.password) throw new Error('不支持的文档地址');
    if (!isApplicationDocumentUrl(source)) {
      display.value = { id: ticket, url: url.href, fail };
      return;
    }
    abort = new AbortController();
    loading.value = true;
    const result = await readCurrentDocument(source, props.kind, abort.signal);
    if (ticket !== generation) return;
    if ('blob' in result) {
      ownedUrl = URL.createObjectURL(result.blob);
      display.value = { id: ticket, url: ownedUrl + url.hash, pdf: true, fail };
    } else display.value = { id: ticket, html: result.html, fail };
  } catch {
    fail();
  } finally {
    if (ticket === generation) {
      loading.value = false;
      abort = undefined;
    }
  }
}
const { refresh } = useCurrentImageLifecycle(
  () => [props.src, props.kind, props.revision],
  clear,
  () => {
    void reload();
  },
);
</script>
<style scoped>
.current-document-preview {
  height: 100%;
}
.file-preview-document {
  display: block;
  width: 100%;
  min-height: 600px;
  height: 100%;
  border: 0;
}
</style>
