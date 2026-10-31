<template>
  <div v-if="hasRelation" class="association-summary">
    <span class="association-summary__name" :title="relationLabel">{{ relationLabel }}</span>
    <n-tag v-if="extraCount > 0" size="small" type="info">+{{ extraCount }}</n-tag>
  </div>
  <n-tag v-else size="small" type="default">未关联</n-tag>
</template>

<script lang="ts" setup>
import { computed } from 'vue';

const props = defineProps<{
  row: any;
}>();

const primaryRelation = computed(() => props.row?.primaryRelation);
const relationCount = computed(() => Number(props.row?.relationCount || 0));
const hasRelation = computed(() => Boolean(primaryRelation.value) && relationCount.value > 0);
const relationLabel = computed(() => primaryRelation.value?.sourceName || primaryRelation.value?.sourceId || '--');
const extraCount = computed(() => Math.max(relationCount.value - 1, 0));
</script>

<style scoped>
.association-summary {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  width: 100%;
}

.association-summary__name {
  min-width: 0;
  max-width: 170px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
