<template>
  <NPopover trigger="click" placement="bottom-start" :width="300">
    <template #trigger>
      <NInput :value="displayText" readonly clearable placeholder="请选择关联数" @clear.stop="clearValue" />
    </template>
    <div class="relation-count-popover">
      <NRadioGroup :value="value.mode" @update:value="changeMode">
        <NSpace><NRadio value="zero">0个</NRadio><NRadio value="range">区间</NRadio></NSpace>
      </NRadioGroup>
      <div v-if="value.mode === 'range'" class="relation-count-range">
        <NInputNumber :value="value.min" :min="0" :precision="0" clearable placeholder="最小值" @update:value="updateMin" />
        <span>~</span>
        <NInputNumber :value="value.max" :min="0" :precision="0" clearable placeholder="最大值" @update:value="updateMax" />
      </div>
      <NText v-if="invalid" type="error" class="relation-count-error">最小值不能大于最大值</NText>
    </div>
  </NPopover>
</template>

<script lang="ts" setup>
import { computed } from 'vue'

export interface RelationCountValue { mode: 'zero' | 'range' | null; min: number | null; max: number | null }
const props = defineProps<{ value: RelationCountValue }>()
const emit = defineEmits<{ 'update:value': [value: RelationCountValue] }>()
const invalid = computed(() => props.value.min !== null && props.value.max !== null && props.value.min > props.value.max)
const displayText = computed(() => {
  if (props.value.mode === 'zero') return '0个'
  if (props.value.mode !== 'range') return ''
  if (props.value.min !== null && props.value.max !== null) return `${props.value.min}~${props.value.max}个`
  if (props.value.min !== null) return `≥${props.value.min}个`
  if (props.value.max !== null) return `≤${props.value.max}个`
  return ''
})

function changeMode(mode: 'zero' | 'range') {
  emit('update:value', mode === 'zero' ? { mode: 'zero', min: 0, max: 0 } : { mode: 'range', min: null, max: null })
}
function updateMin(min: number | null) { emit('update:value', { mode: 'range', min, max: props.value.max }) }
function updateMax(max: number | null) { emit('update:value', { mode: 'range', min: props.value.min, max }) }
function clearValue() { emit('update:value', { mode: null, min: null, max: null }) }
defineExpose({ invalid })
</script>

<style scoped>
.relation-count-popover { display: flex; flex-direction: column; gap: 14px; padding: 4px; }
.relation-count-range { display: grid; grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr); gap: 8px; align-items: center; }
.relation-count-error { font-size: 12px; }
</style>
