<template>
  <NForm ref="formRef" :model="inner" label-width="100px">
    <NGrid :cols="2" :x-gap="16">
      <NGi v-for="field in fields" :key="field.key" :span="field.span || 1">
        <FlowFormItem :field="field.key" :label="field.label" :rule="fieldRule(field)" v-slot="{ disabled }">
          <TopicSelect
            v-if="field.type === 'topic'"
            :value="inner[field.key]"
            :placeholder="field.placeholder"
            :disabled="disabled || field.readonly"
            @update:value="setValue(field.key, $event)"
          />
          <HDictSelect
            v-else-if="field.type === 'dict'"
            :value="dictValue(field)"
            :type="field.dictType"
            clearable
            :placeholder="field.placeholder"
            :disabled="disabled || field.readonly"
            @update:value="setValue(field.key, $event)"
          />
          <NDatePicker
            v-else-if="field.type === 'date'"
            :formatted-value="inner[field.key] ?? null"
            value-format="yyyy-MM-dd"
            type="date"
            clearable
            style="width: 100%"
            :disabled="disabled || field.readonly"
            @update:formatted-value="setValue(field.key, $event)"
          />
          <NInputNumber
            v-else-if="field.type === 'number'"
            :value="inner[field.key]"
            :min="field.min"
            style="width: 100%"
            :placeholder="field.placeholder"
            :disabled="disabled || field.readonly"
            @update:value="setValue(field.key, $event)"
          />
          <NSwitch
            v-else-if="field.type === 'switch'"
            :value="!!inner[field.key]"
            :disabled="disabled || field.readonly"
            @update:value="setValue(field.key, $event)"
          />
          <HUploadInput
            v-else-if="field.type === 'upload'"
            :value="inner[field.key]"
            clearable
            :placeholder="field.placeholder"
            :disabled="disabled || field.readonly"
            @update:value="setValue(field.key, $event)"
            @upload-finish="setValue(field.key, $event.id)"
          />
          <NInput
            v-else
            :value="displayValue(field)"
            :type="field.type === 'textarea' ? 'textarea' : 'text'"
            :rows="field.type === 'textarea' ? field.rows || 3 : undefined"
            :placeholder="field.placeholder"
            :disabled="disabled || field.readonly"
            @update:value="setValue(field.key, $event)"
          />
        </FlowFormItem>
      </NGi>
    </NGrid>
  </NForm>
</template>

<script setup lang="ts">
import { computed, resolveComponent, watch } from 'vue';
import { NDatePicker, NForm, NGi, NGrid, NInput, NInputNumber, NSwitch } from 'naive-ui';
import { useFlowForm } from '../../flow/composables/useFlowForm';
import FlowFormItem from '../../flow/components/FlowFormItem.vue';
import type { FlowFormExpose, FlowFormProps } from '../../flow/typings/flow';
import TopicSelect from '../components/TopicSelect.vue';
import { topicApi } from '../api/topic';
import { achievementApi } from '../api/achievement';

const HDictSelect = resolveComponent('HDictSelect');
const HUploadInput = resolveComponent('HUploadInput');

type FieldType = 'text' | 'textarea' | 'number' | 'date' | 'dict' | 'upload' | 'switch' | 'topic';

type ResearchFlowField = {
  key: string;
  label: string;
  type?: FieldType;
  dictType?: string;
  span?: number;
  rows?: number;
  min?: number;
  placeholder?: string;
  required?: boolean;
  readonly?: boolean;
};

const props = defineProps<FlowFormProps>();
const emit = defineEmits<{ (e: 'update:formData', val: Record<string, any>): void }>();

const { inner, formRef, validate } = useFlowForm(props, emit);

const fields = computed(() => {
  const type = props.businessType?.toUpperCase?.() || '';
  return fieldRegistry[type] || fallbackFields(inner.value);
});

const detailFetchers: Record<string, ((id: string) => Promise<any>) | undefined> = {
  RESEARCH_TOPIC: topicApi.get,
  RESEARCH_ACHIEVEMENT: achievementApi.get,
};

watch(
  () => [props.businessType, props.businessKey] as const,
  async ([businessType, businessKey]) => {
    const type = businessType?.toUpperCase?.() || '';
    const fetcher = detailFetchers[type];
    if (!fetcher || !businessKey) return;
    const resp = await fetcher(businessKey);
    const data = resp?.data?.data ?? resp?.data;
    if (data) {
      inner.value = { ...inner.value, ...data };
    }
  },
  { immediate: true },
);

function fieldRule(field: ResearchFlowField) {
  if (!field.required) return undefined;
  const selectTypes: FieldType[] = ['dict', 'date', 'topic'];
  const message = field.placeholder || `${selectTypes.includes(field.type || 'text') ? '请选择' : '请输入'}${field.label}`;
  if (field.type === 'number') {
    return { type: 'number' as const, required: true, message, trigger: ['blur', 'change'] };
  }
  return { required: true, message, trigger: ['blur', 'change'] };
}

function setValue(key: string, value: any) {
  inner.value[key] = value ?? undefined;
}

function displayValue(field: ResearchFlowField) {
  const value = inner.value[field.key];
  if (value === undefined || value === null) return '';
  return String(value);
}

function dictValue(field: ResearchFlowField) {
  const value = inner.value[field.key];
  if (value === undefined || value === null) return undefined;
  return String(value);
}

function fallbackFields(data: Record<string, any>): ResearchFlowField[] {
  return Object.keys(data || {}).map((key) => ({ key, label: key, type: 'text' }));
}

const fieldRegistry: Record<string, ResearchFlowField[]> = {
  RESEARCH_TOPIC: [
    { key: 'topicNo', label: '课题编号', readonly: true, placeholder: '立项通过后自动生成' },
    { key: 'name', label: '课题名称', required: true },
    { key: 'direction', label: '课题类别', type: 'dict', dictType: 'research_direction', required: true },
    { key: 'level', label: '课题级别', type: 'dict', dictType: 'research_topic_level' },
    { key: 'leaderName', label: '负责人', required: true },
    { key: 'orgName', label: '承担单位/部门' },
    { key: 'external', label: '馆外课题', type: 'switch' },
    { key: 'budget', label: '预算(分)', type: 'number', min: 0 },
    { key: 'startDate', label: '计划开始', type: 'date' },
    { key: 'endDate', label: '计划结束', type: 'date' },
    { key: 'members', label: '参与人员', span: 2, placeholder: '逗号分隔' },
    { key: 'purpose', label: '研究目的', type: 'textarea', required: true, span: 2 },
    { key: 'content', label: '研究内容', type: 'textarea', required: true, span: 2, rows: 4 },
    { key: 'expectedResult', label: '预期成果', type: 'textarea', span: 2 },
    { key: 'attachmentIds', label: '申报材料', type: 'upload', span: 2 },
  ],
  RESEARCH_ACHIEVEMENT: [
    { key: 'achievementNo', label: '成果编号', readonly: true, placeholder: '审核通过后自动生成' },
    { key: 'title', label: '成果名称', required: true },
    { key: 'achievementType', label: '成果类型', type: 'dict', dictType: 'research_achievement_type', required: true },
    { key: 'direction', label: '研究方向', type: 'dict', dictType: 'research_direction' },
    { key: 'topicId', label: '关联课题', type: 'topic', span: 2 },
    { key: 'authors', label: '作者', required: true, placeholder: '逗号分隔，第一作者在前' },
    { key: 'team', label: '研究团队/部门' },
    { key: 'publishOrg', label: '发表刊物/出版社' },
    { key: 'publishDate', label: '发表时间', type: 'date' },
    { key: 'keywords', label: '关键词', span: 2, placeholder: '逗号分隔' },
    { key: 'summary', label: '摘要', type: 'textarea', required: true, span: 2, rows: 4 },
    { key: 'attachmentIds', label: '成果全文/附件', type: 'upload', span: 2 },
    { key: 'watermark', label: '数字水印标识', readonly: true, placeholder: '审核通过后自动生成' },
  ],
};

defineExpose<FlowFormExpose>({ validate });
</script>
