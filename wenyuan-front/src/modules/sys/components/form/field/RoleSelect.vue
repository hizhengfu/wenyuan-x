<template>
  <n-select
    v-bind="$attrs"
    :options="options"
    filterable
    label-field="name"
    value-field="id"
    :render-label="renderLabel"
  />
</template>
<script lang="ts" setup>
import { onBeforeMount, ref, VNodeChild } from 'vue';
import { tree } from '../../../api/role';
import { SelectOption } from 'naive-ui';

const options = ref();

function renderLabel(option: SelectOption): VNodeChild {
  const remarks = option.remarks as string | undefined;
  const name = option.name as string | undefined;
  if (remarks && name) return `${remarks}(${name})`;
  if (remarks) return remarks;
  if (name) return name;
  return (option.label as string) ?? String(option.value ?? '');
}

onBeforeMount(() => {
  tree().then((res) => {
    if (res.data) {
      options.value = res.data || [];
    }
  });
});
</script>
