<template>
  <NSpace vertical
    ><NCard v-for="(row, index) in value || []" :key="index" :title="`第 ${index + 1} 项`"
      ><template #header-extra><NButton @click="remove(index)">移除本项</NButton></template
      ><CollectionFeedbackItem v-for="field in fields" :key="field.key" :label="field.label" :hint="field.hint" :required="field.required"
        ><HUploadInput
          v-if="field.type === 'file'"
          :value="row[field.key]"
          :original-url="originalUrl"
          @update:value="change(index, field.key, $event)"
          @upload-finish="change(index, field.key, $event.id)" /><NSelect
          v-else-if="field.type === 'select'"
          :value="row[field.key]"
          :options="field.options"
          :multiple="field.multiple"
          clearable
          @update:value="change(index, field.key, $event)" /><CollectionSelect
          v-else-if="field.type === 'collection'"
          :value="row[field.key]"
          :multiple="field.multiple"
          @update:value="change(index, field.key, $event)" /><UserSelect
          v-else-if="field.type === 'user'"
          :value="row[field.key]"
          :multiple="field.multiple"
          clearable
          @update:value="change(index, field.key, $event)" /><NDatePicker
          v-else-if="field.type === 'date' || field.type === 'datetime'"
          :value="row[field.key]"
          :type="field.type"
          clearable
          @update:value="change(index, field.key, $event)" /><NInputNumber
          v-else-if="field.type === 'number'"
          :value="row[field.key]"
          :min="0"
          :precision="0"
          @update:value="change(index, field.key, $event)" /><NInput
          v-else
          :value="row[field.key]"
          :type="field.type === 'textarea' ? 'textarea' : 'text'"
          :placeholder="field.label"
          @update:value="change(index, field.key, $event)" /></CollectionFeedbackItem></NCard
    ><NButton @click="add">添加实际记录</NButton></NSpace
  >
</template>
<script setup lang="ts">
import CollectionFeedbackItem from './CollectionFeedbackItem.vue';
import CollectionSelect from './CollectionSelect.vue';
import UserSelect from '@/modules/sys/components/form/field/UserSelect.vue';
import type { EvidenceField } from '../utils/evidenceFields';
const props = defineProps<{ value?: Record<string, any>[]; fields: EvidenceField[]; originalUrl?: (fileId: string) => string | undefined }>();
const emit = defineEmits<{ (e: 'update:value', value: Record<string, any>[]): void }>();
function change(index: number, key: string, value: any) {
  emit(
    'update:value',
    (props.value || []).map((r, i) => (i === index ? { ...r, [key]: value } : { ...r })),
  );
}
function add() {
  emit('update:value', [...(props.value || []), {}]);
}
function remove(index: number) {
  emit(
    'update:value',
    (props.value || []).filter((_, i) => i !== index),
  );
}
</script>
