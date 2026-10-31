<template>
  <div
    v-if="visible && (normalizedRows.length || title)"
    class="map3d-tooltip"
    :style="{
      left: `${x}px`,
      top: `${y}px`,
    }"
  >
    <div v-if="title" class="map3d-tooltip__title">{{ title }}</div>
    <div v-for="row in normalizedRows" :key="row.key" class="map3d-tooltip__row">
      <span class="map3d-tooltip__label">{{ row.label }}</span>
      <span class="map3d-tooltip__value">{{ row.value }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';

import type { Map3DTooltipRow } from './types';

defineOptions({ name: 'MapTooltip' });

const props = withDefaults(
  defineProps<{
    visible?: boolean;
    x?: number;
    y?: number;
    title?: string;
    rows?: Map3DTooltipRow[];
  }>(),
  {
    visible: false,
    x: 0,
    y: 0,
    title: '',
    rows: () => [],
  },
);

const normalizedRows = computed(() => props.rows.filter((row) => row && row.label && row.value !== undefined));
</script>

<style scoped lang="scss">
.map3d-tooltip {
  position: absolute;
  z-index: 20;
  min-width: 220px;
  max-width: 360px;
  padding: 14px 16px;
  pointer-events: none;
  transform: translate(18px, 18px);
  border: 1px solid rgba(255, 205, 138, 0.58);
  border-radius: 14px;
  background:
    linear-gradient(180deg, rgba(52, 32, 15, 0.96), rgba(23, 16, 11, 0.94)),
    radial-gradient(circle at top, rgba(255, 191, 120, 0.14), transparent 62%);
  box-shadow:
    0 14px 36px rgba(0, 0, 0, 0.32),
    inset 0 0 0 1px rgba(255, 244, 226, 0.05);
  color: #fff7ea;
  backdrop-filter: blur(8px);

  &::before {
    position: absolute;
    inset: 6px;
    border: 1px solid rgba(255, 208, 156, 0.12);
    border-radius: 10px;
    content: '';
  }
}

.map3d-tooltip__title,
.map3d-tooltip__row {
  position: relative;
  z-index: 1;
}

.map3d-tooltip__title {
  margin-bottom: 10px;
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 0.06em;
  color: #ffdca8;
  text-shadow: 0 0 10px rgba(255, 177, 86, 0.18);
}

.map3d-tooltip__row {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 10px;
  align-items: start;
  font-size: 13px;
  line-height: 1.6;
}

.map3d-tooltip__row + .map3d-tooltip__row {
  margin-top: 4px;
}

.map3d-tooltip__label {
  color: #f7c780;
  white-space: nowrap;
}

.map3d-tooltip__value {
  color: #fffaf3;
  word-break: break-word;
}
</style>
