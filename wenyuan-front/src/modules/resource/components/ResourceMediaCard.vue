<template>
  <n-card :title="title" size="small"
    ><n-spin :show="loading"
      ><n-alert v-if="error" type="error">{{ error }}</n-alert
      ><template v-else-if="resource"
        ><ResourcePanorama v-if="resource.resourceType === 'PANORAMA'" :src="url" /><video
          v-else-if="resource.resourceType === 'VIDEO'"
          :src="url"
          controls
          style="width: 100%; max-height: 400px"
        /><n-image v-else :src="url" object-fit="contain" style="width: 100%; max-height: 400px" /><n-text>{{
          resource.name
        }}</n-text></template
      ></n-spin
    ></n-card
  >
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { getPreview } from '../api/resource';
import { fileUrl } from '@/utils/path';
import ResourcePanorama from './ResourcePanorama.vue';
const props = defineProps<{ id: string; title?: string }>();
const resource = ref<any>(),
  loading = ref(false),
  error = ref('');
let generation = 0;
const url = computed(() => {
  const p = resource.value?.filePath || '';
  return /^https?:|^blob:/.test(p) ? p : fileUrl(p);
});
watch(
  () => props.id,
  async (id) => {
    const own = ++generation;
    resource.value = undefined;
    error.value = '';
    if (!id) return;
    loading.value = true;
    try {
      const r: any = await getPreview(id);
      if (own === generation) resource.value = r.data;
    } catch {
      if (own === generation) error.value = '资源不可访问，请确认权限或发布状态';
    } finally {
      if (own === generation) loading.value = false;
    }
  },
  { immediate: true },
);
onBeforeUnmount(() => generation++);
</script>
