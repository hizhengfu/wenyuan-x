<template>
  <n-card title="文物流程记录" :segmented="{ content: 'soft' }">
    <n-timeline size="large">
      <n-timeline-item v-for="item in refList" :content="item.remarks" :type="renderType(item.type)">
        <template #header>
          {{ item.operator }}进行
          <HDictText :value="item.type" type="wms_storage_operation_type"></HDictText>
          操作
        </template>
        {{item.code}}({{item.remarks}})
        <template #footer>
          <n-time :time="item.operationTime"></n-time>
        </template>
      </n-timeline-item>
    </n-timeline>
    <n-empty v-if="refList && refList.length === 0" description="暂无文物流程记录"></n-empty>
  </n-card>
</template>
<script lang="ts" setup>
import { history } from '../api/operation.ts';
import { ref, watch, watchEffect } from 'vue';

const props = defineProps({
  id: { type: String, required: true },
});

const refList = ref<any[]>([]);

const types = { '0': 'default', '1': 'error', '2': 'warning', '3': 'success' };

function renderType(type: string): 'default' | 'error' | 'info' | 'success' | 'warning' | undefined {
  //@ts-ignore
  return types[type] || 'default';
}

function fetchData(id: string) {
  if (!id) {
    refList.value = [];
    return;
  }
  history({ artifactId: id }).then((resp) => {
    refList.value = resp.data || [];
  });
}

watch(
  () => props.id,
  (newId) => {
    fetchData(newId);
  },
  { immediate: true },
);
</script>
