<template>
  <NPopover
    v-model:show="show"
    trigger="click"
    placement="bottom-end"
    :show-arrow="false"
    internal-trap-focus
    :disabled="disabled || loading"
  >
    <template #trigger>
      <NButton size="small" secondary :disabled="disabled" :loading="loading" :aria-expanded="show">
        {{ label }}<NIcon style="margin-left: 4px"><IconMdiMenuDown /></NIcon>
      </NButton>
    </template>
    <div class="collection-action-menu" role="group" :aria-label="label" @click="closeAfterAction" @keydown.esc.stop="show = false">
      <slot />
    </div>
  </NPopover>
</template>

<script setup lang="ts">
import { onDeactivated, ref } from 'vue';

defineProps<{ label: string; disabled?: boolean; loading?: boolean }>();
const show = ref(false);
onDeactivated(() => {
  show.value = false;
});

function closeAfterAction(event: MouseEvent) {
  const button = (event.target as Element | null)?.closest('button');
  if (button && !button.disabled && button.getAttribute('aria-disabled') !== 'true') show.value = false;
}
</script>

<style scoped>
.collection-action-menu {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 160px;
  max-width: min(360px, calc(100vw - 48px));
}
.collection-action-menu :deep(.n-button) {
  width: 100%;
  height: auto;
  min-height: 30px;
  justify-content: flex-start;
  padding: 7px 10px;
  text-align: left;
}
.collection-action-menu :deep(.n-button__content) {
  white-space: normal;
}
</style>
