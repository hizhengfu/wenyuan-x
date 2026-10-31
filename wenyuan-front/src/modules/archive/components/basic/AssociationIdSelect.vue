<template>
  <n-select
    v-bind="$attrs"
    v-model:value="innerValue"
    :options="options"
    :loading="loading"
    :multiple="multiple"
    filterable
    :label-field="labelField"
    value-field="id"
    @search="handleSearch"
    @scroll="handleScroll"
    @clear="handleClear"
    ref="selectInstRef"
    clearable
    :reset-menu-on-options-change="false"
  >
  </n-select>
</template>

<script lang="ts" setup>
import { onBeforeMount, ref, computed, watch } from 'vue';

const props = defineProps({
  value: { type: String, default: '' },
  multiple: { type: Boolean, default: false },
  fetchdata: { type: Function, default: () => {} },
  flage: { type: Number, default: 0 },
  call: { type: Boolean, default: false },
  labelField: { type: String, default: 'name' },
  params: { type: Object, default: () => ({}) },
  pageSize: { type: Number, default: 30 },
});

const options = ref([] as any);
const selectInstRef = ref();
const loading = ref(false);
const currentPage = ref(1);
const hasMore = ref(true);
const currentKeywords = ref('');

const emit = defineEmits(['update:value']);

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
 * 判断字符串是否为ID
 */
function isId(str: string): boolean {
  if (!str) return false;
  const trimmedStr = str.trim();

  // 判断是否为UUID格式
  const uuidRegex = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

  // 判断是否为长数字ID
  const longNumericIdRegex = /^\d{10,}$/;

  return uuidRegex.test(trimmedStr) || longNumericIdRegex.test(trimmedStr);
}

/**
 * 数据加载函数
 */
async function loadData(keywords = '', page = 1, isSearch = false, initialValue = '') {
  if (loading.value || (!hasMore.value && !isSearch && page > 1)) return;

  loading.value = true;

  try {
    const { fetchdata, params, labelField } = props;
    const requestParams:any = {
      pageSize: props.pageSize,
      pageNum: page,
      ...params
    };

    if (initialValue) {
      if (isId(initialValue)) {
        requestParams.id = initialValue;
      } else {
        requestParams[labelField] = initialValue;
      }
    } else if (keywords) {
      requestParams[labelField] = keywords;
    }

    const resp = await fetchdata(requestParams);
    const newData = resp.data.list || [];

    if (isSearch || page == 1) {
      options.value = newData;
    } else {
      options.value = [...options.value, ...newData];
    }

    hasMore.value = newData.length === props.pageSize;
    currentPage.value = page;

  } finally {
    loading.value = false;
  }
}

/**
 * 搜索处理
 */
function handleSearch(keywords = '') {
  currentKeywords.value = keywords;
  currentPage.value = 1;
  hasMore.value = true;

  if (!keywords) {
    loadData('', 1, true);
  } else {
    loadData(keywords, 1, true);
  }
}

/**
 * 清除处理
 */
function handleClear() {
  currentKeywords.value = '';
  currentPage.value = 1;
  hasMore.value = true;
  loadData('', 1, true);
}

function handleScroll(e: any) {
  const { target } = e;

  if (!target) return;

  const { scrollTop, scrollHeight, clientHeight } = target;
  const isNearBottom = scrollHeight - scrollTop - clientHeight < 50;

  if (isNearBottom && hasMore.value && !loading.value) {
    const nextPage = currentPage.value + 1;
    loadData(currentKeywords.value, nextPage, false);
  }
}

watch(() => props.value, (newValue, oldValue) => {
  if (newValue !== oldValue && newValue) {
    currentKeywords.value = '';
    currentPage.value = 1;
    hasMore.value = true;
    loadData('', 1, true, newValue);
  }
}, { immediate: false });

watch(() => props.flage, () => {
  currentKeywords.value = '';
  currentPage.value = 1;
  hasMore.value = true;
  loadData('', 1, true);
});

onBeforeMount(() => {
  if (props.value) {
    loadData('', 1, true, props.value);
  } else {
    loadData('', 1, true);
  }
});
</script>

<style lang="scss">
.icon-hover {
  cursor: pointer;
  transform: translateY(-5px);
}
</style>
