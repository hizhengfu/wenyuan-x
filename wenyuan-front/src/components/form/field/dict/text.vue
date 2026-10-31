<template>
  <span>{{ text }}</span>
</template>
<script lang="ts" setup>
import { computed, shallowRef, triggerRef, watchEffect } from 'vue';
import { frontType as fetchType } from '@/modules/sys/api/dict';
import { isBoolean, isUndefined } from 'lodash-es';

const props = defineProps({
  value: { type: [String, Number] },
  type: { type: String, required: true },
});

const options = shallowRef<any[]>([]);

const text = computed(() => {
  let value = props.value?.toString();
  if (!options.value || isUndefined(value)) return '--';
  if (isBoolean(value)) {
    return value ? '是' : '否';
  }
  let dict = [];

  if (/,/.test(value)) {
    let values = value.split(',');
    values.forEach((v) => {
      let temp = options.value.find((item) => {
        return item.value == v;
      });
      if (temp) {
        dict.push(temp.label);
      }
    });
  } else {
    let temp = options.value.find((item) => {
      return item.value == value;
    });
    if (!isUndefined(temp)) {
      dict.push(temp.label);
    }
  }
  return dict.join(',');
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
