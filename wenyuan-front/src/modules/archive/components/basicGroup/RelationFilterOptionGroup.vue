<template>
  <section class="relation-filter-option-section">
    <div class="relation-filter-option-title">{{ label }}</div>
    <NCheckboxGroup :value="value" class="relation-filter-option-list" @update:value="updateValue">
      <NCheckbox v-for="option in options" :key="option.value" :value="option.value" :label="option.label" />
    </NCheckboxGroup>
  </section>
</template>

<script lang="ts" setup>
export interface OptionItem {
  label: string
  value: string
}

defineProps<{ value: string[]; label: string; options: OptionItem[] }>()
const emit = defineEmits<{ 'update:value': [value: string[]] }>()

function updateValue(value: Array<string | number>) {
  emit('update:value', value.map(String))
}
</script>

<style scoped>
.relation-filter-option-section { min-width: 0; overflow: hidden; border: 1px solid #edf1f5; background: #fff; }
.relation-filter-option-title { display: flex; height: 36px; align-items: center; padding: 0 12px; border-bottom: 1px solid #edf1f5; background: #fbfcfe; color: #344054; font-size: 13px; font-weight: 600; }
.relation-filter-option-list { display: flex; max-height: 178px; flex-direction: column; overflow-y: auto; }
.relation-filter-option-list :deep(.n-checkbox) { display: flex; min-height: 34px; align-items: center; margin: 0; padding: 6px 12px; border-bottom: 1px solid #f4f6f8; color: #475467; font-size: 13px; }
.relation-filter-option-list :deep(.n-checkbox:last-child) { border-bottom: 0; }
</style>
