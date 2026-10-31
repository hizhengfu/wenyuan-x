<template>
  <n-tree-select
    v-bind="$attrs"
    :options="options"
    :loading="loading"
    label-field="label"
    key-field="value"
    @update:show="onShow"
  />
</template>
<script lang="ts" setup>
import { onBeforeMount, ref } from "vue";
import { template } from "../../../api/template";

const options = ref();
const loading = ref(false);

// 每次展开重新读取，站点表单可直接选择刚创建或导入的模板或皮肤。
async function loadOptions() {
  if (loading.value) return;
  loading.value = true;
  try {
    options.value = await template({});
  } catch {
    // 请求层已统一提示错误，保留现有选项，下一次展开可重试。
  } finally {
    loading.value = false;
  }
}

function onShow(show: boolean) {
  if (show) void loadOptions();
}

onBeforeMount(loadOptions);
</script>
