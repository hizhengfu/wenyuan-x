<template>
  <n-date-picker v-model:value="innerValue" @update-value="handleUpdateValue" v-bind="$attrs"></n-date-picker>
</template>

<script setup lang="ts">
import { computed } from 'vue';

// 定义最小必要的 props
const props = defineProps({
  value: {
    type: [String, Number],
    default: null,
  },
});

const emit = defineEmits(['update:value']);

// 处理输入值转换
const innerValue = computed(() => {
  if (!props.value) return null;
  const timestamp = Number(props.value);
  return isNaN(timestamp) ? null : timestamp;
});

// 处理值更新
const handleUpdateValue = (value: any) => {
  emit('update:value', value === null ? null : String(value));
};
</script>
