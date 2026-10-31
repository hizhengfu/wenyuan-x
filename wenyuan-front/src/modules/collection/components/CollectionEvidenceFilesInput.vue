<template>
  <NSpace vertical>
    <NInputGroup v-for="(_, index) in rows" :key="index"
      ><HUploadInput
        :value="rows[index]"
        :original-url="originalUrl"
        @update:value="change(index, $event)"
        @upload-finish="change(index, $event.id)"
      /><NButton @click="remove(index)">移除</NButton></NInputGroup
    >
    <NButton @click="add">添加实际文件</NButton>
  </NSpace>
</template>
<script setup lang="ts">
import { computed } from 'vue';
const props = defineProps<{ value?: string[]; originalUrl?: (fileId: string) => string | undefined }>();
const emit = defineEmits<{ (e: 'update:value', value: string[]): void }>();
const rows = computed(() => (props.value?.length ? props.value : ['']));
function change(index: number, value: string) {
  const next = [...rows.value];
  next[index] = value || '';
  emit('update:value', next);
}
function add() {
  emit('update:value', [...rows.value, '']);
}
function remove(index: number) {
  emit(
    'update:value',
    rows.value.filter((_, i) => i !== index),
  );
}
</script>
