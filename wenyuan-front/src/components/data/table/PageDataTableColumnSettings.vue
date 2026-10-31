<template>
  <NPopover trigger="click" placement="bottom-end" :width="380">
    <template #trigger>
      <NTooltip trigger="hover">
        <template #trigger>
          <NButton aria-label="列设置">
            <template #icon>
              <NIcon size="20">
                <IconMdiCogOutline />
              </NIcon>
            </template>
          </NButton>
        </template>
        列设置
      </NTooltip>
    </template>
    <div class="column-settings-panel">
      <div class="column-settings-header">
        <span>显示列</span>
        <NButton text size="small" type="primary" @click="$emit('reset')">恢复默认</NButton>
      </div>
      <NCheckboxGroup :value="value" @update:value="updateValue">
        <div class="column-settings-grid">
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
import type { PageDataTableColumnSettingOption } from './pageDataTableColumnSettings';

defineProps<{
  options: PageDataTableColumnSettingOption[];
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
.column-settings-panel {
  max-height: min(420px, calc(100vh - 180px));
  overflow-y: auto;
}

.column-settings-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  font-weight: 600;
}

.column-settings-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px 12px;
}
</style>
