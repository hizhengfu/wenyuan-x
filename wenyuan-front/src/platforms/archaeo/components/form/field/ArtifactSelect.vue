<template>
  <n-select :options="options" v-model:value="value" label-field="name" value-field="id" v-bind="$attrs" filterable />
</template>
<script lang="ts" setup>
import { computed, ref, watchEffect } from 'vue';
import { list } from '../../../api/artifact.ts';

const props = defineProps({
  value: { type: [String, Number] },
  multiple: { type: Boolean, default: false },
});
const options = ref<any>([]);

const emit = defineEmits(['update:value']);
const value = computed({
  get() {
    let value = props.value?.toString();
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
  list({}).then((resp: any) => {
    let data = resp.data.list || [];
    if (data) {
      options.value = data;
    }
  });
});
</script>
