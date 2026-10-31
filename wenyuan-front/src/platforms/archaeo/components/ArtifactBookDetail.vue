<template>
  <div class="artifact-book-detail">
    <n-tabs type="card" placement="left" pane-style="height: 100%; overflow: auto;">
      <n-tab-pane name="basic" tab="登记信息">
        <ArtifactView :id="id"></ArtifactView>
      </n-tab-pane>
      <n-tab-pane name="book" tab="文物研究">
        <ArtifactBookView :id="id"></ArtifactBookView>
      </n-tab-pane>
    </n-tabs>
  </div>
</template>
<script lang="ts" setup>
import ArtifactView from './ArtifactView.vue';
import ArtifactBookView from './ArtifactBookView.vue';

const props = defineProps({
  id: { type: String, required: true },
});
</script>
<style scoped>
.artifact-book-detail {
  display: flex;
  height: 100%;
  min-height: 0;
  width: 100%;
}
.artifact-book-detail :deep(.n-tabs) {
  flex: 1;
  height: 100%;
  min-height: 0;
}
.artifact-book-detail :deep(.n-tabs-pane-wrapper) {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}
.artifact-book-detail :deep(.n-tab-pane) {
  height: 100%;
  overflow: auto;
}
:deep(.n-tabs-nav--left .n-tabs-tab__label) {
  writing-mode: vertical-lr;
  transform: rotate(0deg);
  padding: 20px 0;
}
</style>
