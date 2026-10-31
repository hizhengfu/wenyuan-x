<template>
  <n-select
    v-bind="$attrs"
    :options="options"
    :loading="loading"
    remote
    filterable
    @search="loadData"
    label-field="name"
    value-field="id"
  />
</template>
<script lang="ts" setup>
import { onBeforeMount, ref } from 'vue';
import { list } from '../../../api/protocol';

const props = defineProps({
  type: { type: String }, //规约类型
});

const options = ref();
/**
 * 加载状态
 * */
const loading = ref(false);

/**
 * 数据加载函数
 * */
function loadData(keywords?: string) {
  loading.value = true;
  list({ pageSize: 30, name: keywords, type: props.type })
    .then((resp: any) => {
      if (resp.data) {
        options.value = resp.data.list || [];
      }
    })
    .finally(() => {
      loading.value = false;
    });
}

onBeforeMount(() => {
  loadData();
});
</script>
