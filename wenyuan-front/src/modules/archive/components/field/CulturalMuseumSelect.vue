<template>
  <n-select
    v-bind="$attrs"
    v-model:value="innerValue"
    :options="options"
    label-field="wdmc"
    filterable
    remote
    @search="handleSearch"
    value-field="id"
  />
</template>
<script lang="ts" setup>
import { computed, onBeforeMount, ref } from 'vue';
import { list } from '../../api/museum/cultural.ts';

const props = defineProps({
  value: { type: [String, Number] },
  multiple: { type: Boolean, default: false },
});

const emit = defineEmits(['update:value']);

const options = ref();

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
  list({ wdmc: query }).then((res: any) => {
    if (res.data && res.data.list) {
      options.value = res.data.list;
    } else {
      options.value = [];
    }
  });
}

onBeforeMount(() => {
  const { value } = props;
  if (value) {
    list({ id: value }).then((res: any) => {
      if (res.data && res.data.list) {
        options.value = res.data.list;
      }
    });
  } else {
    list({}).then((res: any) => {
      if (res.data && res.data.list) {
        options.value = res.data.list;
      }
    });
  }
});
</script>
