<template>
  <n-timeline v-if="logs.length > 0">
    <n-timeline-item
      v-for="log in logs"
      :key="log.id"
      :type="flowLogTimelineType(log.action)"
      :title="log.nodeName || log.action"
      :time="formatTime(log.createTime)"
    >
      <div class="log-content">
        <span class="log-operator">{{ log.operatorName || log.operator }}</span>
        <span class="log-action">{{ actionLabel(log) }}</span>
      </div>
      <p v-if="log.comment" class="log-comment">{{ log.comment }}</p>
    </n-timeline-item>
  </n-timeline>
  <n-empty v-else description="暂无操作记录" size="small" />
</template>

<script setup lang="ts">
import dayjs from 'dayjs'
import type { FlowInstanceLog } from '../../typings/flow'
import { flowLogActionLabel, flowLogTimelineType } from '../../utils/flowLogActionDisplay'
import { useDict } from '@/utils/dict'

defineProps<{ logs: FlowInstanceLog[] }>()
const { options: actionDict } = useDict('flow_instance_log_action')

function actionLabel(log: FlowInstanceLog) {
  return flowLogActionLabel(log, actionDict.value as any[])
}

function formatTime(t?: string) {
  return t ? dayjs(t).format('YYYY-MM-DD HH:mm:ss') : '-'
}
</script>

<style scoped>
.log-content {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.log-operator {
  font-weight: 500;
  color: var(--n-title-text-color, #303133);
}
.log-action {
  color: var(--n-content-text-color, #606266);
  font-size: 13px;
}
.log-comment {
  margin: 6px 0 0;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
  color: var(--n-content-text-color, #606266);
  font-size: 13px;
}
</style>
