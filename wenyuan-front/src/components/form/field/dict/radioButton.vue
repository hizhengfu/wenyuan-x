<template>
  <n-radio-group v-model:value="value" v-bind="$attrs">
    <n-radio-button v-for="option in options" :key="option.value" :value="option.value">
      {{ option.label }}
    </n-radio-button>
  </n-radio-group>
</template>
<script lang="ts" setup>
import { computed, shallowRef, triggerRef, watchEffect } from 'vue';
import { type as fetchType } from '@/modules/sys/api/dict';
import { SysDictOptions } from '@/modules/sys/typings/sys';

const props = defineProps({
  value: { type: [String, Number] },
  type: { type: String, required: true },
});

const options = shallowRef<SysDictOptions[]>([]);

const emit = defineEmits(['update:value', 'load']);
const value = computed({
  get() {
    return props.value?.toString();
  },
  set(value) {
    emit('update:value', value);
  },
});

watchEffect(() => {
  const { type } = props;
  fetchType(type as string, true).then((resp) => {
    let data = resp.data;
    if (data) {
      options.value = data;
      triggerRef(options);
      emit('load', data);
    }
  });
});
</script>
