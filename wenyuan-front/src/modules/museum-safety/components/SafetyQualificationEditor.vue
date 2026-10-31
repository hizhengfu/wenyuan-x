<template>
  <div class="qualification-editor">
    <div v-if="!modelValue.length" class="qualification-editor__empty">尚未登记资质证书</div>
    <div v-for="(certificate, index) in modelValue" :key="index" class="qualification-card">
      <div class="qualification-card__header">
        <strong>证书 {{ index + 1 }}</strong>
        <NButton text type="error" @click="remove(index)">移除</NButton>
      </div>
      <div class="qualification-card__grid">
        <NFormItem label="证书名称" required>
          <NInput
            :value="certificate.name"
            placeholder="例如：消防设施操作员"
            @update:value="update(index, 'name', $event)"
          />
        </NFormItem>
        <NFormItem label="证书编号">
          <NInput :value="certificate.certificateNo || ''" @update:value="update(index, 'certificateNo', $event)" />
        </NFormItem>
        <NFormItem label="发证单位">
          <NInput
            :value="certificate.issuingAuthority || ''"
            @update:value="update(index, 'issuingAuthority', $event)"
          />
        </NFormItem>
        <NFormItem label="有效期">
          <NDatePicker
            :value="certificateRange(certificate)"
            type="daterange"
            clearable
            @update:value="updateRange(index, $event)"
          />
        </NFormItem>
      </div>
      <NFormItem label="证书原件" class="qualification-card__upload">
        <SafetyAttachmentUpload
          :model-value="certificate.attachments"
          accept="image/*,.pdf,application/pdf"
          @update:model-value="update(index, 'attachments', $event)"
        />
      </NFormItem>
    </div>
    <NButton dashed block @click="add">添加资质证书</NButton>
  </div>
</template>

<script setup lang="ts">
import { NButton, NDatePicker, NFormItem, NInput } from 'naive-ui';
import type { SafetyQualificationCertificate } from '../typings';
import SafetyAttachmentUpload from './SafetyAttachmentUpload.vue';

const props = defineProps<{ modelValue: SafetyQualificationCertificate[] }>();
const emit = defineEmits<{ 'update:modelValue': [value: SafetyQualificationCertificate[]] }>();

function add() {
  emit('update:modelValue', [
    ...props.modelValue,
    { name: '', certificateNo: '', issuingAuthority: '', issuedAt: null, expiresAt: null, attachments: [] },
  ]);
}

function remove(index: number) {
  emit(
    'update:modelValue',
    props.modelValue.filter((_, itemIndex) => itemIndex !== index),
  );
}

function update<K extends keyof SafetyQualificationCertificate>(
  index: number,
  key: K,
  value: SafetyQualificationCertificate[K],
) {
  emit(
    'update:modelValue',
    props.modelValue.map((item, itemIndex) => (itemIndex === index ? { ...item, [key]: value } : item)),
  );
}

function certificateRange(certificate: SafetyQualificationCertificate): [number, number] | null {
  return certificate.issuedAt != null && certificate.expiresAt != null
    ? [certificate.issuedAt, certificate.expiresAt]
    : null;
}

function updateRange(index: number, value: [number, number] | null) {
  const current = props.modelValue[index];
  if (!current) return;
  emit(
    'update:modelValue',
    props.modelValue.map((item, itemIndex) =>
      itemIndex === index ? { ...item, issuedAt: value?.[0] ?? null, expiresAt: value?.[1] ?? null } : item,
    ),
  );
}
</script>

<style scoped>
.qualification-editor {
  width: 100%;
}
.qualification-editor__empty {
  margin-bottom: 10px;
  padding: 18px;
  color: var(--n-text-color-disabled);
  text-align: center;
  background: var(--n-color-embedded);
  border-radius: 6px;
}
.qualification-card {
  margin-bottom: 12px;
  padding: 12px;
  border: 1px solid var(--n-border-color);
  border-radius: 6px;
}
.qualification-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.qualification-card__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 12px;
}
.qualification-card__upload {
  margin-bottom: 0;
}
@media (max-width: 720px) {
  .qualification-card__grid {
    grid-template-columns: 1fr;
  }
}
</style>
