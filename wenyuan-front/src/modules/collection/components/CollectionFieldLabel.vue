<template>
  <span class="collection-field-label">
    <span>{{ label }}</span>
    <NTooltip v-if="help" :show="showHelp" trigger="hover" placement="top" :delay="100"
      :style="{ maxWidth: 'min(360px, calc(100vw - 32px))', lineHeight: '1.65', whiteSpace: 'pre-line' }"
      @update:show="showHelp = $event" @clickoutside="showHelp = false">
      <template #trigger>
        <NButton class="collection-field-help" text size="tiny" attr-type="button"
          :aria-label="`${label}说明`" :aria-describedby="showHelp ? helpId : undefined"
          @focus="showHelp = true" @blur="showHelp = false"
          @click.stop.prevent="showHelp = true" @keydown.esc.stop.prevent="showHelp = false">
          <NIcon :size="16" aria-hidden="true"><IconMdiHelpCircleOutline /></NIcon>
        </NButton>
      </template>
      <span :id="helpId">{{ help }}</span>
    </NTooltip>
  </span>
</template>
<script setup lang="ts">
import { onDeactivated, ref, useId } from 'vue';
import { NButton, NIcon, NTooltip } from 'naive-ui';
defineProps<{ label?: string; help?: string }>();
const showHelp = ref(false);
const helpId = useId();
onDeactivated(() => { showHelp.value = false; });
</script>
<style scoped>
.collection-field-label { display: inline-flex; align-items: center; gap: 4px; max-width: 100%; }
.collection-field-help { flex: none; width: 20px; height: 20px; border-radius: 50%; vertical-align: middle; }
.collection-field-help:focus-visible { outline: 2px solid currentColor; outline-offset: 2px; }
</style>
