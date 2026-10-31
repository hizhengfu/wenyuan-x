<template>
  <span>{{ text }}</span>
</template>
<script lang="ts" setup>
import { computed, shallowRef, triggerRef, watchEffect } from 'vue';
import { SelectMixedOption } from 'naive-ui/es/select/src/interface';
import { isBoolean, isUndefined, isArray } from 'lodash-es';

const props = defineProps({
  value: { type: [String, Number, Array<string | number>] },
  params: { type: Object, default: () => ({}) }, //查询条件
  fetchFun: { type: Function, required: true }, //接口
  showValue: { type: String, default: 'id' }, //匹配key
  showLabel: { type: String, default: 'name' }, //显示字段
});

const options = shallowRef<SelectMixedOption[]>([]);

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
        return item[props.showValue] == v;
      });
      if (temp) {
        dict.push(temp[props.showLabel]);
      }
    });
  } else {
    let temp = options.value.find((item) => {
      return item[props.showValue] == value;
    });
    if (!isUndefined(temp)) {
      dict.push(temp[props.showLabel]);
    }
  }
  return dict.join(',');
});

watchEffect(() => {
  const { params, fetchFun, value } = props;
  options.value = [];
  fetchFun(params).then((resp: any) => {
    if (isArray(resp?.data)) {
      options.value = resp.data;
    } else if (isArray(resp?.data?.list)) {
      options.value = resp.data.list;
    }
    triggerRef(options);
  });
});
</script>
