<template>
  <n-spin :show="loading">
    <template v-if="data">
      <div class="flow-info-status">
        <FlowStatusTag :status="data.flowStatus" />
      </div>

      <template v-if="$slots.form">
        <div style="margin-bottom: 16px">
          <slot name="form" :data="data" />
        </div>
      </template>

      <template v-if="data.diagram">
        <FlowViewer :diagram-data="data.diagram" class="flow-info-diagram" />
      </template>
      <n-empty v-else description="暂无流程图" size="small" style="margin-bottom: 16px" />

      <h3 class="flow-info-heading">审批记录</h3>
      <TaskHistory :logs="data.history || []" />
    </template>
    <n-empty v-else description="暂无审批进度" size="small" />
  </n-spin>
</template>

<script setup lang="ts">
import FlowViewer from './FlowViewer.vue'
import FlowStatusTag from './FlowStatusTag.vue'
import TaskHistory from '../task/TaskHistory.vue'

defineProps<{
  data?: any
  loading?: boolean
}>()
</script>

<style scoped>
.flow-info-status {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 16px;
}
.flow-info-diagram {
  height: 320px;
  min-height: 240px;
  margin-bottom: 24px;
  border-radius: 4px;
  overflow: hidden;
}
.flow-info-heading {
  margin: 0 0 12px;
  font-size: 14px;
  font-weight: 600;
}
</style>
