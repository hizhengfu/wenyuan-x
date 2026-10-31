<template>
  <div class="flex flex-col wh-full">
    <n-tabs type="card" v-model:value="activeLog">
      <n-tab name="system">系统日志</n-tab>
      <n-tab name="message">信息发送日志</n-tab>
      <n-tab name="job">计划任务日志</n-tab>
      <n-tab name="log">实时日志</n-tab>
    </n-tabs>
    <div class="flex flex-1 mt-1">
      <component :is="commentLog"></component>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue';
import SystemLog from '../components/log/system.vue';
// 信息发送日志直接复用 message 模块的「投递审计」页面（查 sys_message_dispatch 表）。
// 老的 components/log/message.vue + api/messageLog.ts 是 module/message-old 的 sys_message_send_log
// 入口，已删除。
import MessageLog from '@/modules/message/views/dispatch.vue';
import JobLog from '../components/log/job.vue';
import RealLog from '../components/log/log.vue';

const activeLog = ref('system');

const commentLog = computed(() => {
  if (activeLog.value === 'job') {
    return JobLog;
  } else if (activeLog.value === 'message') {
    return MessageLog;
  } else if (activeLog.value === 'log') {
    return RealLog;
  } else {
    return SystemLog;
  }
});
</script>
