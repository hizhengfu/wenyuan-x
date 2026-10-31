<template>
  <n-alert v-if="task?.sequentialReview" :type="task.sequentialReview.needsReview ? 'warning' : 'info'" :show-icon="false" style="margin: 8px 0">
    <template v-if="task.sequentialReview.needsReview">
      本轮顺签的原名单或审批位置待核对。请保留原审批记录，核实原始名单和实际承接依据后再办理。
    </template>
    <template v-else>
      本轮顺签第 {{ task.sequentialReview.position }} / {{ task.sequentialReview.total }} 位，原指定人：{{ task.sequentialReview.originalAssigneeName || task.sequentialReview.originalAssigneeId }}。
      <span v-if="task.assignee !== task.sequentialReview.originalAssigneeId">当前办理人：{{ task.assigneeName || task.assignee }}。</span>
      角色成员变化不会改变本轮顺序；移交后仍须完成本位置审核。
    </template>
  </n-alert>
</template>

<script setup lang="ts">
import type { FlowTask } from '../../typings/flow';
defineProps<{ task?: FlowTask | null }>();
</script>
