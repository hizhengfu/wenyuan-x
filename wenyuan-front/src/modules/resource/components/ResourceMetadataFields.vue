<template>
  <n-space vertical style="width: 100%">
    <n-alert v-if="error" type="error">{{ error }} <n-button text @click="load">重新加载模板</n-button></n-alert>
    <n-spin :show="loading">
      <n-form-item v-for="field in fields" :key="field.fieldName" :label="field.fieldLabel" :required="field.required">
        <n-space vertical style="width: 100%">
          <n-input
            v-if="field.fieldType === 'TEXT'"
            :value="values[field.fieldName]"
            :disabled="disabled"
            @update:value="set(field.fieldName, $event)"
          />
          <n-input-number
            v-else-if="field.fieldType === 'NUMBER'"
            :value="values[field.fieldName]"
            :disabled="disabled"
            @update:value="set(field.fieldName, $event)"
          />
          <n-date-picker
            v-else-if="field.fieldType === 'DATE'"
            :formatted-value="values[field.fieldName]"
            value-format="yyyy-MM-dd"
            type="date"
            clearable
            :disabled="disabled"
            @update:formatted-value="set(field.fieldName, $event)"
          />
          <n-select
            v-else-if="field.fieldType === 'BOOLEAN'"
            :value="values[field.fieldName] === undefined ? null : String(values[field.fieldName])"
            :options="[
              { label: '是', value: 'true' },
              { label: '否', value: 'false' },
            ]"
            clearable
            :disabled="disabled"
            @update:value="set(field.fieldName, $event === null ? null : $event === 'true')"
          />
          <n-select
            v-else
            :value="values[field.fieldName]"
            :options="options(field)"
            :multiple="field.fieldType === 'MULTI_SELECT'"
            clearable
            :disabled="disabled"
            @update:value="set(field.fieldName, $event)"
          />
          <n-text v-if="field.description" depth="3">{{ field.description }}</n-text>
        </n-space>
      </n-form-item>
      <n-empty v-if="!fields.length && !loading && !error" description="此分类与类型暂无元数据模板" size="small" />
    </n-spin>
    <n-collapse
      ><n-collapse-item title="其他元数据（JSON）"
        ><n-input
          :value="modelValue"
          type="textarea"
          :disabled="disabled"
          :autosize="{ minRows: 3, maxRows: 8 }"
          @update:value="$emit('update:modelValue', $event)" /></n-collapse-item
    ></n-collapse>
    <n-alert v-if="parseError" type="error">元数据必须是JSON对象，请先修正后再填写模板字段。</n-alert>
  </n-space>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { metadataTemplates } from '../api/resource';
const props = defineProps<{
  modelValue?: string;
  categoryId?: string;
  resourceType?: string;
  disabled?: boolean;
  create?: boolean;
}>();
const emit = defineEmits<{ 'update:modelValue': [string] }>();
const fields = ref<any[]>([]);
const loading = ref(false);
const error = ref('');
let generation = 0;
const parseError = computed(() => {
  try {
    const v = JSON.parse(props.modelValue || '{}');
    return !v || Array.isArray(v) || typeof v !== 'object';
  } catch {
    return true;
  }
});
const values = computed<Record<string, any>>(() => {
  if (parseError.value) return {};
  return JSON.parse(props.modelValue || '{}');
});
function set(key: string, value: any) {
  if (parseError.value) return;
  const v = { ...values.value };
  if (value === null || value === undefined || value === '') delete v[key];
  else v[key] = value;
  emit('update:modelValue', JSON.stringify(v));
}
function options(field: any) {
  try {
    return JSON.parse(field.options || '[]').map((v: string) => ({ label: v, value: v }));
  } catch {
    return [];
  }
}
async function load() {
  const owned = ++generation;
  fields.value = [];
  error.value = '';
  loading.value = true;
  try {
    const response: any = await metadataTemplates({
      categoryId: props.categoryId || undefined,
      resourceType: props.resourceType || undefined,
    });
    if (owned !== generation) return;
    fields.value = response.data || [];
    if (props.create && !parseError.value) {
      const next = { ...values.value };
      let changed = false;
      for (const f of fields.value)
        if (
          next[f.fieldName] === undefined &&
          f.defaultValue !== null &&
          f.defaultValue !== undefined &&
          f.defaultValue !== ''
        ) {
          next[f.fieldName] = ['TEXT', 'DATE', 'SELECT'].includes(f.fieldType)
            ? f.defaultValue
            : JSON.parse(f.defaultValue);
          changed = true;
        }
      if (changed) emit('update:modelValue', JSON.stringify(next));
    }
  } catch {
    if (owned === generation) error.value = '元数据模板读取失败。';
  } finally {
    if (owned === generation) loading.value = false;
  }
}
watch(() => [props.categoryId, props.resourceType], load, { immediate: true });
onBeforeUnmount(() => generation++);
</script>
