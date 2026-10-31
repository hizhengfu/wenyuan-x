<template>
  <n-tabs type="card" placement="left" pane-style="overflow: auto;padding-bottom:15px;">
    <n-tab-pane name="basic" tab="基础信息">
      <ArtifactView :id="id"></ArtifactView>
    </n-tab-pane>
    <n-tab-pane name="history" tab="库房记录">
      <ArtifactRecordTimeline :id="id"></ArtifactRecordTimeline>
    </n-tab-pane>
  </n-tabs>
</template>
<script lang="ts" setup>
import ArtifactView from './ArtifactView.vue';
import ArtifactRecordTimeline from './ArtifactRecordTimeline.vue';

const props = defineProps({
  id: { type: String, required: true },
});
</script>
<style scoped>
:deep(.n-tabs-nav--left .n-tabs-tab__label) {
  writing-mode: vertical-lr;
  transform: rotate(0deg);
  padding: 20px 0;
}
</style>
