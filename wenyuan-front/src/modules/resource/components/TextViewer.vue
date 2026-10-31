<template>
  <div class="text-viewer">
    <div v-if="loading" class="flex items-center justify-center py-10">
      <n-spin />
    </div>
    <n-alert v-else-if="error" type="error" :title="error" />
    <template v-else>
      <div class="viewer-toolbar">
        <span class="text-xs text-gray-500">
          {{ encoding.toUpperCase() }}
          <span v-if="truncated" class="ml-2 text-orange-500">（内容过长，已截断前 {{ limitKb }}KB）</span>
        </span>
        <n-button size="tiny" text @click="copy">
          <template #icon><n-icon><IconMdiContentCopy /></n-icon></template>
          复制
        </n-button>
      </div>
      <pre class="viewer-pre"><code>{{ content }}</code></pre>
    </template>
  </div>
</template>

<script lang="ts" setup>
import { ref, watch, onMounted } from 'vue';
import { useMessage } from 'naive-ui';

const props = defineProps<{
  src: string;
  ext: string;
}>();

const message = useMessage();

const content = ref('');
const loading = ref(false);
const error = ref('');
const encoding = ref('utf-8');
const truncated = ref(false);
const limitKb = 512; // 最多读取 512KB

async function load() {
  if (!props.src) return;
  loading.value = true;
  error.value = '';
  content.value = '';
  truncated.value = false;
  try {
    const resp = await fetch(props.src, {
      headers: { Range: `bytes=0-${limitKb * 1024 - 1}` },
    });
    if (!resp.ok && resp.status !== 206) {
      throw new Error(`HTTP ${resp.status}`);
    }
    const total = Number(resp.headers.get('Content-Length') || 0);
    const blob = await resp.blob();
    if (total && total > limitKb * 1024) truncated.value = true;
    if (blob.size >= limitKb * 1024) truncated.value = true;

    // JSON 格式化显示
    if (props.ext === 'json') {
      const raw = await blob.text();
      try {
        content.value = JSON.stringify(JSON.parse(raw), null, 2);
      } catch {
        content.value = raw;
      }
    } else {
      content.value = await blob.text();
    }
  } catch (e: any) {
    error.value = `加载文本失败：${e?.message || e}`;
  } finally {
    loading.value = false;
  }
}

function copy() {
  if (!content.value) return;
  navigator.clipboard?.writeText(content.value).then(
    () => message.success('已复制'),
    () => message.error('复制失败'),
  );
}

onMounted(load);
watch(() => [props.src, props.ext], load);
</script>

<style scoped>
.text-viewer {
  width: 100%;
  background: #fafafa;
  border: 1px solid #efeff5;
  border-radius: 6px;
  overflow: hidden;
}
.viewer-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 10px;
  background: #f0f2f5;
  border-bottom: 1px solid #e5e7eb;
}
.viewer-pre {
  margin: 0;
  padding: 12px;
  max-height: 520px;
  overflow: auto;
  font-family: 'Menlo', 'Consolas', monospace;
  font-size: 12.5px;
  line-height: 1.55;
  white-space: pre;
  color: #1f2937;
  background: #fff;
}
.viewer-pre code {
  white-space: pre;
}
</style>
