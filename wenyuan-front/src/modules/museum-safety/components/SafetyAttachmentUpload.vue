<template>
  <div class="attachment-upload">
    <UploadInput
      v-model:value="uploadedPath"
      :accept="accept"
      module="museum-safety"
      placeholder="也可直接填写已有文件引用"
      @upload-finish="appendUploadedFile"
    >
      上传附件
    </UploadInput>
    <NButton v-if="uploadedPath" text type="primary" class="append-reference" @click="appendReference">
      添加现有引用
    </NButton>
    <NSpace v-if="modelValue.length" class="attachment-list">
      <NTag v-for="(item, index) in modelValue" :key="`${item}-${index}`" closable @close="remove(index)">
        {{ item }}
      </NTag>
    </NSpace>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { NButton, NSpace, NTag, type UploadFileInfo } from 'naive-ui';
import UploadInput from '@/components/form/field/upload/input.vue';

const props = withDefaults(
  defineProps<{
    modelValue: string[];
    accept?: string;
  }>(),
  { accept: undefined },
);
const emit = defineEmits<{ 'update:modelValue': [value: string[]] }>();
const uploadedPath = ref<string | null>(null);

function appendUploadedFile(file: UploadFileInfo) {
  const reference = String(file.id || file.fullPath || '').trim();
  append(reference);
  uploadedPath.value = null;
}
function appendReference() {
  append(String(uploadedPath.value || '').trim());
  uploadedPath.value = null;
}
function append(reference: string) {
  if (reference && !props.modelValue.includes(reference)) {
    emit('update:modelValue', [...props.modelValue, reference]);
  }
}
function remove(index: number) {
  emit(
    'update:modelValue',
    props.modelValue.filter((_, itemIndex) => itemIndex !== index),
  );
}
</script>

<style scoped>
.attachment-upload {
  width: 100%;
}
.attachment-list {
  margin-top: 8px;
}
.append-reference {
  margin-top: 6px;
}
</style>
