<template>
  <section class="resource-page" :class="{ 'resource-page--fill': fill }" :aria-label="title" :style="colors">
    <header v-if="showTitle || $slots.actions" class="resource-page__header">
      <h1 v-if="showTitle">{{ title }}</h1>
      <div v-if="$slots.actions" class="resource-page__actions"><slot name="actions" /></div>
    </header>
    <div class="resource-page__body"><slot /></div>
  </section>
</template>
<script setup lang="ts">
import { computed } from 'vue';
import { useThemeVars } from 'naive-ui';
defineProps<{ title: string; fill?: boolean; showTitle?: boolean }>();
const theme = useThemeVars();
const colors = computed(() => ({
  '--resource-accent': theme.value.primaryColor,
  '--resource-text': theme.value.textColor1,
  '--resource-muted': theme.value.textColor3,
  '--resource-border': theme.value.borderColor,
  '--resource-surface': theme.value.cardColor,
  '--resource-background': theme.value.bodyColor,
}));
</script>
<style src="../styles/resource.css"></style>
