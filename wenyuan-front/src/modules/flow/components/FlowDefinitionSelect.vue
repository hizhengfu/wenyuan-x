<template>
  <n-select
    v-model:value="value"
    v-bind="$attrs"
    :options="options"
    :loading="loading"
    :filterable="filterable"
    :clearable="clearable"
    :placeholder="placeholder"
  />
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import type { SelectGroupOption, SelectOption } from 'naive-ui';
import { listDefinitions } from '../api/definition';
import type { DefinitionStatus, FlowDefinition } from '../typings/flow';

defineOptions({ inheritAttrs: false });

const props = withDefaults(
  defineProps<{
    status?: DefinitionStatus | null;
    excludeKey?: string;
    groupByCategory?: boolean;
    filterable?: boolean;
    clearable?: boolean;
    placeholder?: string;
  }>(),
  {
    status: 'ACTIVE',
    excludeKey: '',
    groupByCategory: false,
    filterable: true,
    clearable: true,
    placeholder: '请选择流程',
  },
);

const value = defineModel<string | null | undefined>('value');
const loading = ref(false);
const definitions = ref<FlowDefinition[]>([]);

interface DefinitionOption extends SelectOption {
  label: string;
  value: string;
  category: string;
}

const flatOptions = computed<DefinitionOption[]>(() => {
  const optionsByKey = new Map<string, DefinitionOption>();
  definitions.value.forEach((definition) => {
    const key = definition.key;
    if (!key || key === props.excludeKey || optionsByKey.has(key)) return;
    optionsByKey.set(key, {
      label: `${definition.name || key}（${key}）`,
      value: key,
      category: definition.category || '未分类',
    });
  });
  return Array.from(optionsByKey.values());
});

const options = computed<Array<SelectOption | SelectGroupOption>>(() => {
  if (!props.groupByCategory) return flatOptions.value;

  const groups = new Map<string, Array<{ label: string; value: string }>>();
  flatOptions.value.forEach(({ label, value: optionValue, category }) => {
    const items = groups.get(category) ?? [];
    items.push({ label, value: optionValue });
    groups.set(category, items);
  });
  if (groups.size <= 1) return flatOptions.value;

  return Array.from(groups.entries()).map<SelectGroupOption>(([category, children]) => ({
    type: 'group',
    key: `group-${category}`,
    label: category,
    children,
  }));
});

async function loadOptions() {
  loading.value = true;
  try {
    const response = await listDefinitions({ status: props.status || undefined, pageSize: 0 });
    const page = response.data?.data ?? response.data;
    definitions.value = Array.isArray(page) ? page : page?.list ?? [];
  } finally {
    loading.value = false;
  }
}

watch(() => props.status, loadOptions, { immediate: true });

defineExpose({ reload: loadOptions });
</script>
