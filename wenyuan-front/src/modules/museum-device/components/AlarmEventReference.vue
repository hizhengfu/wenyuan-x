<template>
  <template v-if="eventId">
    <NButton text type="primary" @click="visible = true">{{ label || '查看来源采集事件' }}</NButton>
    <AlarmEventDetail v-model:show="visible" :event-id="eventId" :can-manage="false" />
  </template>
  <span v-else>--</span>
</template>
<script setup lang="ts">
import { onDeactivated, ref, watch } from 'vue';
import AlarmEventDetail from './AlarmEventDetail.vue';
const props = defineProps<{ eventId?: string | null; label?: string | null }>();
const visible = ref(false);
watch(
  () => props.eventId,
  () => {
    visible.value = false;
  },
);
onDeactivated(() => {
  visible.value = false;
});
</script>
