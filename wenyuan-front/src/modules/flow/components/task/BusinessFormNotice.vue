<template>
  <NAlert v-if="task?.businessFormNotice" type="info" title="本次提交的申请材料" style="margin-bottom: 12px">
    {{ task.businessFormNotice }}
  </NAlert>
</template>
<script setup lang="ts">
import { NAlert } from 'naive-ui';
import type { FlowTask } from '../../typings/flow';
defineProps<{ task?: FlowTask | null }>();
</script>
