<template>
  <n-select
    v-model:value="innerValue"
    :options="options"
    placeholder="请选择市级"
    clearable
    filterable
    label-field="name"
    :value-field="valueField"
    v-bind="$attrs"
  />
</template>
<script lang="ts" setup>
import { computed, shallowRef, triggerRef, watchEffect ,PropType} from 'vue';
import { SelectMixedOption } from 'naive-ui/es/select/src/interface';
import { list } from '../../../api/area';

const props = defineProps({
  value: { type: String as PropType<string | null> },
    valueField:{type:String ,default:'id'}
});
const emit = defineEmits(['update:value']);

const innerValue = computed({
  get() {
    return props.value;
  },
  set(value) {
    emit('update:value', value);
  },
});

const options = shallowRef<SelectMixedOption[]>([]);

watchEffect(() => {
  options.value = [];
  list({parentId:'360000'}).then((resp: any) => {
    if (resp?.data?.list) {
      options.value = resp.data.list;
      triggerRef(options);
    }
  });
});
</script>
