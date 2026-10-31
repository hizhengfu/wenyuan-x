<template>
  <main class="resource-preview-page">
    <ResourcePreview
      v-if="resourceId"
      :show="true"
      :resource-id="resourceId"
      standalone
    />
    <n-result v-else status="warning" title="无法打开资源" description="资源编号为空，请返回资源列表后重新打开。" />
  </main>
</template>

<script lang="ts" setup>
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import ResourcePreview from '../components/ResourcePreview.vue';

const route = useRoute();
const resourceId = computed(() => {
  const value = route.params.id;
  return (Array.isArray(value) ? value[0] : value || '').trim();
});
</script>

<style scoped>
.resource-preview-page {
  min-height: 100vh;
  background: #f5f7fa;
}
</style>
