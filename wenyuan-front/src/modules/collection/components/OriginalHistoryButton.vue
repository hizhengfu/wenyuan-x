<template>
  <slot name="trigger" :open="open">
    <NButton size="small" secondary @click="open">{{ label || '历史材料' }}</NButton>
  </slot>
  <HPageForm content-only :enable-cancel="false" v-model:show="show" title="历史材料查阅">
    <OriginalHistoryWorkspace v-if="show" :business-type="businessType" :business-id="businessId" />
  </HPageForm>
</template>
<script setup lang="ts">
import { defineAsyncComponent, ref } from 'vue';
const OriginalHistoryWorkspace = defineAsyncComponent(() => import('./OriginalHistoryWorkspace.vue'));
defineProps<{ businessType?: string; businessId?: string; label?: string }>();
const show = ref(false);
function open() {
  show.value = true;
}
</script>
