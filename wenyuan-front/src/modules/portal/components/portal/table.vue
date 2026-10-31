<template>
  <HPageDataTable
    style="flex: 1"
    ref="refPortalTable"
    :showHeader="false"
    :showToolbar="false"
    :isEdit="false"
    :fetch-data="fetchData"
    :columns="tableColumn"
    :params="params"
  ></HPageDataTable>
</template>
<script lang="ts" setup>
import request from '@/utils/request.ts';
import { computed, watch, ref } from 'vue';

const props = defineProps({
  url: { type: String },
  method: { type: [String, Number] },
  columns: { type: Array },
  params: { type: Object },
});

const refPortalTable = ref();

const tableColumn = computed(() => {
  let temp = props.columns?.map((column: any) => {
    let c: any = {
      key: column.value,
      title: column.name,
    };
    if (column.dictType) {
      c['dictType'] = column.dictType;
    }

    return c;
  });
  return temp || [];
});

function fetchData() {
  const { method, params, url } = props;
  if (url) {
    return request({
      url: url,
      params: Object.assign({}, params),
      method: method as string,
    });
  } else {
    return new Promise((resolve) => {
      resolve(null);
    });
  }
}

watch(
  () => props.params,
  () => {
    refPortalTable.value?.handleQuery();
  },
  {
    deep: true,
  },
);
</script>
