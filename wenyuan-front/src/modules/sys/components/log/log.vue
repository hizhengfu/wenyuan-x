<template>
  <n-card
    style="position: relative"
    size="small"
    content-style="display:flex;position:absolute;left:0;right:0;top:52px;bottom:0;overflow: auto;"
  >
    <template #header> 链接状态:{{ status }} </template>
    <template #header-extra>
      <n-flex align="center">
        <n-checkbox v-model:checked="autoScroll">自动滚动</n-checkbox>
        <n-button size="small" @click="clearLogs">清空日志</n-button>
        <n-text depth="3" style="font-size: 12px"> {{ logList.length }} / {{ MAX_LINES }} 行 </n-text>
      </n-flex>
    </template>
    <n-log class="log-container" :lines="logList" ref="logInstRef"> </n-log>
  </n-card>
</template>
<script lang="ts" setup>
import { useEventSource } from '@vueuse/core';
import { apiUrl } from '@/utils';
import { watch, ref, onUnmounted } from 'vue';

const logList = ref<string[]>([]);

const { data, error, status, close } = useEventSource(apiUrl('sse/subscribe'), ['connected', 'log'], {
  withCredentials: true,
  autoReconnect: true,
  immediate: true,
});

const autoScroll = ref(true);
const logInstRef = ref();

const MAX_LINES = 10_000;
watch(data, (n) => {
  if (n) {
    logList.value.push(n);
  }
  // 超过最大行数，裁剪顶部
  if (logList.value.length > MAX_LINES) {
    const removeCount = logList.value.length - MAX_LINES;
    logList.value.splice(0, removeCount);
  }
  if (autoScroll.value) {
    logInstRef.value?.scrollTo({ position: 'bottom', silent: true });
  }
});

// 清空日志
const clearLogs = () => {
  logList.value = [];
};

onUnmounted(() => {
  close();
});
</script>
<style scoped>
.log-container {
  height: 100% !important;
  width: 100%;
  background: #0f172a;
  color: #e5e7eb;
  font-family: monospace;
}
</style>
