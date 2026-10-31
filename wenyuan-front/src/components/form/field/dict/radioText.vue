<template>
  <n-flex>
    <n-tag v-for="option in formatOptions" checkable>
      {{ option.checked }}
      {{ option.label }}
    </n-tag>
  </n-flex>
</template>
<script lang="ts" setup>
import { computed, PropType, ref, shallowRef, triggerRef, watchEffect } from 'vue';
import { type as fetchType } from '@/modules/sys/api/dict';
import { SysDictOptions } from '@/modules/sys/typings/sys';

const props = defineProps({
  value: [String, Number] as PropType<string | number | null>,
  type: { type: String, required: true },
  vertical: { type: Boolean },
});

const options = shallowRef<SysDictOptions[]>([]);

const formatOptions = computed(() => {
  const { value } = props;
  const valueText = value?.toString();
  return options.value.map((option: any) => {
    let checked = '〇';
    if (valueText === option.value) {
      checked = '●';
    }
    return {
      checked: checked,
      value: option.value,
      label: option.label,
    };
  });
});

watchEffect(() => {
  const { type } = props;
  fetchType(type as string, true).then((resp) => {
    let data = resp.data;
    if (data) {
      options.value = data;
      triggerRef(options);
    }
  });
});
</script>
