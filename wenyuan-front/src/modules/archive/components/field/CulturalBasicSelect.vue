<template>
  <n-select
    v-bind="$attrs"
    v-model:value="innerValue"
    :options="options"
    label-field="publishName"
    filterable
    remote
    @search="handleSearch"
    value-field="id"
  />
</template>
<script lang="ts" setup>
import { computed, onBeforeMount, PropType, ref, watch } from 'vue';
import { list } from '../../api/basic/culturalRecordBasic.ts';
import {
  buildCulturalBasicSelectInitialParams,
  buildCulturalBasicSelectQuery,
  buildCulturalBasicSelectSelectedParams,
  mergeCulturalBasicSelectOptions,
} from './culturalBasicSelect';

const props = defineProps({
  value: { type: [String, Number] },
  multiple: { type: Boolean, default: false },
  params: { type: Object as PropType<Record<string, unknown>>, default: () => ({}) },
});

const emit = defineEmits(['update:value']);

const options = ref<Array<Record<string, unknown>>>([]);
const selectedOptions = ref<Array<Record<string, unknown>>>([]);

const innerValue = computed({
  get() {
    let value = props.value?.toString() || null;
    if (props.multiple) {
      return value?.split(',') || [];
    } else {
      return value;
    }
  },
  set(value) {
    if (value instanceof Array) {
      emit('update:value', value ? value.join(',') : value);
    } else {
      emit('update:value', value);
    }
  },
});

/**
 * 检索函数
 * */
function handleSearch(query: string) {
  loadOptions({ publishName: query });
}

let loadToken = 0;

function requestOptions(params: Record<string, unknown> = {}) {
  return list(buildCulturalBasicSelectQuery(props.params, params)).then((res: any) =>
    res.data && Array.isArray(res.data.list) ? res.data.list : [],
  );
}

async function loadOptions(params: Record<string, unknown> = {}) {
  const currentToken = ++loadToken;
  const loadedOptions = await requestOptions(params);
  if (currentToken !== loadToken) {
    return;
  }
  options.value = mergeCulturalBasicSelectOptions(selectedOptions.value, loadedOptions);
}

async function refreshOptions() {
  const currentToken = ++loadToken;
  const initialParams = buildCulturalBasicSelectInitialParams(props.value, props.params);
  const selectedParams = buildCulturalBasicSelectSelectedParams(props.value, props.params);
  const [candidateOptions, queriedSelectedOptions] = await Promise.all([
    requestOptions(initialParams),
    selectedParams ? requestOptions(selectedParams) : Promise.resolve([]),
  ]);
  if (currentToken !== loadToken) {
    return;
  }

  const selectedValue = props.value?.toString() || '';
  selectedOptions.value = mergeCulturalBasicSelectOptions(
    queriedSelectedOptions,
    candidateOptions.filter((option: Record<string, unknown>) => `${option.id ?? ''}` === selectedValue),
  );
  options.value = mergeCulturalBasicSelectOptions(selectedOptions.value, candidateOptions);
}

onBeforeMount(() => {
  refreshOptions();
});

watch(
  [() => props.value, () => props.params],
  () => refreshOptions(),
  { deep: true },
);
</script>
