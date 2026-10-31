<template>
  <n-checkbox-group v-model:value="value" v-bind="$attrs">
    <n-space :vertical="vertical">
      <n-checkbox v-for="option in options" :key="option.value" :value="option.value">
        {{ option.label }}
      </n-checkbox>
    </n-space>
  </n-checkbox-group>
</template>
<script lang="ts" setup>
import { computed, PropType, shallowRef, triggerRef, watchEffect } from 'vue';
import { type as fetchType } from '@/modules/sys/api/dict';
import { SysDictOptions } from '@/modules/sys/typings/sys';
import { isEmpty } from 'lodash-es';

const props = defineProps({
  value: String as PropType<string | null>,
  type: { type: String, required: true },
  vertical: { type: Boolean },
});

const options = shallowRef<SysDictOptions[]>([]);

const emit = defineEmits(['update:value']);
const value = computed<Array<string> | null | undefined>({
  get() {
    if (isEmpty(props.value)) {
      return null;
    } else {
      return props.value?.split(',');
    }
  },
  set(value) {
    emit('update:value', value?.join(','));
  },
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
