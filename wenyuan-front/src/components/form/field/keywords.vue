<template>
  <n-dynamic-tags v-model:value="innerValue" v-bind="$attrs" />
</template>
<script lang="ts" setup>
import { computed, PropType } from 'vue';

const emits = defineEmits(['update:value']);
const props = defineProps({
  value: { type: [String, Array] as PropType<string | string[]> },
  returnArray: { type: Boolean, default: false },
});

const innerValue = computed({
  get: () => {
    //@ts-ignore
    return (props.value && (props.returnArray ? props.value : props.value.split(','))) || [];
  },
  set: (val) => {
    emits('update:value', props.returnArray ? val : val?.join(','));
  },
});
</script>
