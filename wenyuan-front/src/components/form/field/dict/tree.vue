<template>
  <n-tree-select
    :options="options"
    v-model:value="innerValue"
    :key-field="keyField"
    v-bind="$attrs"
    :showPath="showPath"
    :multiple="multiple"
  />
</template>
<script lang="ts" setup>
import { computed, shallowRef, triggerRef, watchEffect } from 'vue';
import { type as fetchType } from '@/modules/sys/api/dict';
import { TreeSelectOption } from 'naive-ui';
import { transformToTreeFormat } from '@/utils';

const props = defineProps({
  value: { type: [String, Number] },
  multiple: { type: Boolean, default: false },
  showPath: { type: Boolean, default: true },
  keyField: { type: String, default: 'value' },
  type: { type: String, required: true },
});

const options = shallowRef<TreeSelectOption[]>([]);

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

watchEffect(() => {
  const { type } = props;
  fetchType(type as string, true).then((resp) => {
    let data = resp.data;
    if (data) {
      options.value = transformToTreeFormat(data, { pIdKey: 'parentId' });
      triggerRef(options);
    }
  });
});
</script>
