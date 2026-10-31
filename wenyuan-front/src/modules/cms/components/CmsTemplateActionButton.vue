<template>
  <NTooltip trigger="hover" :delay="250">
    <template #trigger>
      <span class="cms-action-trigger">
        <NButton v-bind="$attrs" :size="size" :quaternary="!$slots.default" :class="{ 'cms-icon-action': !$slots.default }" :aria-label="label">
          <template #icon><NIcon :size="18"><component :is="icon" /></NIcon></template>
          <slot />
        </NButton>
      </span>
    </template>
    {{ tooltip || label }}
  </NTooltip>
</template>
<script setup lang="ts">
import type { Component } from 'vue';
import { NButton, NIcon, NTooltip } from 'naive-ui';

// 提示保留在外层：禁用的图标按钮也能说明操作含义；按钮本身提供可访问名称。
defineOptions({ inheritAttrs: false });
withDefaults(defineProps<{ label: string; tooltip?: string; icon: Component; size?: 'tiny' | 'small' | 'medium' | 'large' }>(), { size: 'medium' });
</script>
<style scoped>
.cms-action-trigger { display: inline-flex; align-items: center; flex-shrink: 0; }
.cms-icon-action { width: var(--n-height); padding: 0; }
</style>
