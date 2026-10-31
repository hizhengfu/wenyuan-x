<template>
  <CollectionFeedbackItem v-for="field in fields" :key="field.key" :label="field.label" :hint="field.hint" :required="field.required"
    ><HUploadInput
      v-if="field.type === 'file'"
      v-model:value="model[field.key]"
      @upload-finish="model[field.key] = $event.id" /><UserSelect
      v-else-if="field.type === 'user'"
      v-model:value="model[field.key]"
      :multiple="field.multiple"
      preserve-unlisted-values /><NDatePicker
      v-else-if="field.type === 'datetime' || field.type === 'date'"
      v-model:value="model[field.key]"
      :type="field.type === 'date' ? 'date' : 'datetime'" /><NSelect
      v-else-if="field.type === 'select'"
      v-model:value="model[field.key]"
      :options="field.options"
      :multiple="field.multiple" /><NInputNumber
      v-else-if="field.type === 'number'"
      v-model:value="model[field.key]"
      :precision="2" /><NInput
      v-else
      v-model:value="model[field.key]"
      :type="field.type === 'textarea' ? 'textarea' : 'text'"
  /></CollectionFeedbackItem>
</template>
<script setup lang="ts">
import CollectionFeedbackItem from './CollectionFeedbackItem.vue';
import UserSelect from '@/modules/sys/components/form/field/UserSelect.vue';
import type { EvidenceField } from '../utils/evidenceFields';
defineProps<{ fields: EvidenceField[] }>();
const model = defineModel<Record<string, any>>({ required: true });
</script>
