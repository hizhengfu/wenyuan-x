<template>
  <NAlert v-if="reasons.length" type="warning" title="当前办理限制" style="margin-bottom: 12px">
    <div v-for="reason in reasons" :key="reason">{{ reason }}</div>
  </NAlert>
</template>
<script setup lang="ts">
import { computed } from 'vue';
import { NAlert } from 'naive-ui';
import type { FlowTask } from '../../typings/flow';
const props = defineProps<{ task?: FlowTask | null }>();
const reasons = computed(() => {
  const values = [...new Set(Object.values(props.task?.actionRestrictions || {}))];
  if (!values.length && Array.isArray(props.task?.availableActions) && !props.task!.availableActions!.length)
    return ['当前没有可执行的任务操作，请核对现行资格和业务状态。'];
  return values;
});
</script>
