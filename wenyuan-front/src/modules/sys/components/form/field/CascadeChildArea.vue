<template>
  <n-cascader
    v-bind="$attrs"
    v-model:value="innerValue"
    :options="options"
    :cascade="true"
    check-strategy="child"
    :show-path="true"
    :allow-checking-not-loaded="true"
    label-field="name"
    value-field="id"
  />
</template>
<script setup lang="ts">
import { computed, onBeforeMount, PropType, ref } from 'vue';
import { tree } from '../../../api/area';
import { transformToTreeFormat } from '@/utils';

const props = defineProps({
  value: { type: String as PropType<string | null> },
  parentId: { type: String, default: '0' },
});

const options = ref();

const emit = defineEmits(['update:value']);

const innerValue = computed({
  get() {
    return props.value;
  },
  set(value) {
    emit('update:value', value);
  },
});

onBeforeMount(() => {
  const { parentId } = props;
  tree({ id: parentId }).then((resp) => {
    options.value = transformToTreeFormat(resp, { pIdKey: 'parentId' });
  });
});
</script>
