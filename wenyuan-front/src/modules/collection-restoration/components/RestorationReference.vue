<template>
  <template v-if="projectId">
    <NButton text type="primary" @click="visible = true">查看修复项目</NButton>
    <RestorationDetailView v-model:show="visible" :restoration-id="projectId" />
  </template>
</template>
<script setup lang="ts">
import { onDeactivated, ref, watch } from 'vue';
import RestorationDetailView from './RestorationDetailView.vue';
const props = defineProps<{ projectId?: string | null }>();
const visible = ref(false);
watch(
  () => props.projectId,
  () => {
    visible.value = false;
  },
);
onDeactivated(() => {
  visible.value = false;
});
</script>
