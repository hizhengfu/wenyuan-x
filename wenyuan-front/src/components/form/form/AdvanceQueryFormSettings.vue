<template>
  <NPopover trigger="click" placement="bottom-end" :width="380">
    <template #trigger>
      <NTooltip trigger="hover">
        <template #trigger>
          <NButton aria-label="筛选项设置">
            <template #icon>
              <NIcon size="20">
                <IconMdiFilterCogOutline />
              </NIcon>
            </template>
          </NButton>
        </template>
        筛选项设置
      </NTooltip>
    </template>
    <div class="query-settings-panel">
      <div class="query-settings-header">
        <span>显示筛选项</span>
        <NButton text size="small" type="primary" @click="$emit('reset')">恢复默认</NButton>
      </div>
      <NCheckboxGroup :value="value" @update:value="updateValue">
        <div class="query-settings-grid">
          <NCheckbox
            v-for="option in options"
            :key="option.value"
            :value="option.value"
            :label="option.label"
            :disabled="option.disabled"
          />
        </div>
      </NCheckboxGroup>
    </div>
  </NPopover>
</template>

<script setup lang="ts">
import type { AdvanceQueryFormSettingOption } from './advanceQueryFormSettings';

defineProps<{
  options: AdvanceQueryFormSettingOption[];
  value: string[];
}>();

const emit = defineEmits<{
  'update:value': [value: string[]];
  reset: [];
}>();

function updateValue(value: Array<string | number>) {
  emit('update:value', value.map(String));
}
</script>

<style scoped>
.query-settings-panel {
  max-height: min(420px, calc(100vh - 180px));
  overflow-y: auto;
}

.query-settings-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  font-weight: 600;
}

.query-settings-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px 12px;
}
</style>
