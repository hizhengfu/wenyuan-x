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
import { computed, PropType, ref, watch } from 'vue';
import { cascade } from '../../../api/area'
import { transformToTreeFormat } from '@/utils';

const props = defineProps({
  value: { type: String as PropType<string | null> },
    type: { type: Number, default: 4 },// 省直辖市 2， 市 3 ，区县 4
  parentId: { type: String, default: '0' },
});

const options = ref<any[]>([]);

const emit = defineEmits(['update:value']);

const innerValue = computed({
  get() {
    return props.value;
  },
  set(value) {
    emit('update:value', value);
  },
});

watch(
  () => [props.parentId, props.type],
  ([parentId, type]) => {
    cascade({ id: parentId, type }).then((resp) => {
      options.value = transformToTreeFormat(resp, { pIdKey: 'parentId' });
    });
  },
  { immediate: true },
);
</script>
