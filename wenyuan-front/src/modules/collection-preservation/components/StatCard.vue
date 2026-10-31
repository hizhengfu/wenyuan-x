<template>
  <div class="pv-stat-card" :class="`tone-${tone || 'default'}`">
    <div class="pv-stat-value">{{ value }}</div>
    <div class="pv-stat-label">{{ label }}</div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  label: string;
  value: number | string;
  tone?: 'info' | 'warning' | 'success' | 'default';
}>();
</script>

<style scoped>
.pv-stat-card {
  border-radius: 6px;
  padding: 16px 18px;
  background: var(--n-color, #f5f7fa);
  color: var(--n-text-color, #333);
  min-height: 90px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}
.pv-stat-value { font-size: 28px; font-weight: 600; line-height: 1.2; }
.pv-stat-label { font-size: 12px; opacity: 0.7; margin-top: 4px; }
.tone-info { background: rgba(59, 130, 246, 0.08); color: #3b82f6; }
.tone-warning { background: rgba(245, 158, 11, 0.1); color: #d97706; }
.tone-success { background: rgba(16, 185, 129, 0.1); color: #10b981; }
</style>
