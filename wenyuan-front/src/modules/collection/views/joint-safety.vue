<template>
  <NAlert v-if="readError" type="error">{{ readError }} <NButton @click="tableRef?.handleRefresh?.()">重新读取</NButton></NAlert>
  <HPageDataTable auto-scroll-x
    ref="tableRef"
    :columns="columns"
    :fetch-data="fetchPage"
    :params="query"
    :is-add="false"
    :is-edit="false"
    :is-delete="false"
    row-key-field="rowKey"
    @row-view="view"
  >
    <template #queryForm
      ><NFormItem label="检索内容"><NInput v-model:value="query.keyword" placeholder="提用单或藏品" clearable /></NFormItem
      ><NFormItem label="检查状态"
        ><NSelect v-model:value="query.status" :options="statusOptions" clearable style="min-width: 230px" /></NFormItem
    ></template>
  </HPageDataTable>
  <CollectionJointSafetyDrawer ref="drawerRef" @saved="tableRef?.handleRefresh?.()" />
</template>
<script setup lang="ts">
import { h, reactive, ref, watch, onActivated, onDeactivated, onBeforeUnmount } from 'vue';
import { cloneDeep } from 'lodash-es';
import { NButton } from 'naive-ui';
import { jointSafetyApi } from '../api/jointSafety';
import { jointSafetyStatuses } from '../utils/jointSafetyFields';
import CollectionJointSafetyDrawer from '../components/CollectionJointSafetyDrawer.vue';
const tableRef = ref<any>(),
  drawerRef = ref<any>(),
  query = reactive({ keyword: '', status: null as string | null });
const listedRows = ref<any[]>([]), readError = ref(''), reading = ref(false);
let active = true, request = 0;
function invalidate() { request++; listedRows.value = []; readError.value = ''; reading.value = false; drawerRef.value?.close(); }
watch(query, invalidate, { deep: true, flush: 'sync' });
onDeactivated(() => { active = false; invalidate(); });
onBeforeUnmount(() => { active = false; invalidate(); });
onActivated(() => { if (!active) { active = true; tableRef.value?.handleRefresh?.(); } });
async function fetchPage(params: Record<string, any>) {
  if (!active) return null;
  invalidate(); const token = request; reading.value = true;
  try {
    const response: any = await jointSafetyApi.page(cloneDeep(params)), result = response?.data;
    if (!active || token !== request) return null;
    if (!Array.isArray(result?.list) || typeof result.total !== 'number' || result.total < result.list.length ||
      result.list.some((row: any) => !row?.rowKey || !row.requisitionId || !row.collectionId)) throw new Error('共同检查列表不完整，请重新读取');
    listedRows.value = result.list; return response;
  } catch (error: any) {
    if (!active || token !== request) return null;
    listedRows.value = []; readError.value = error?.message || '共同检查列表读取失败，请重新读取';
    return { data: { list: [], total: 0 } };
  } finally { if (token === request) reading.value = false; }
}
function rowAction(row: any) { const token = request; return () => { if (token === request) view(row); }; }
const statusOptions = Object.entries(jointSafetyStatuses).map(([value, label]) => ({ value, label }));
const columns = [
  { ellipsis: { tooltip: true }, resizable: true, width: 170, title: '提用单', key: 'documentNo', minWidth: 170 },
  { ellipsis: { tooltip: true }, resizable: true, title: '总登记号', key: 'totalRegNo', width: 150 },
  { ellipsis: { tooltip: true }, resizable: true, width: 180, title: '藏品', key: 'name', columnType: 'title', minWidth: 180 },
  { ellipsis: { tooltip: true }, resizable: true, width: 210,
    title: '共同检查状态',
    key: 'status',
    minWidth: 210,
    render: (r: any) => jointSafetyStatuses[r.status] || r.status,
  },
  { title: '下次逐件检查', key: 'nextDueAt', columnType: 'dateTime', width: 190 },
  { title: '待确认轮次', key: 'pendingRoundCount', width: 115 },
  { title: '待报告处理', key: 'pendingDirectorCount', width: 115 },
  { ellipsis: { tooltip: true }, resizable: true, width: 180, title: '原件核查信息', key: 'evidenceError', minWidth: 180 },
  {
    title: '操作',
    key: 'operate',
    type: 'operate',
    width: 160,
    render: (r: any) => h(NButton, { text: true, type: 'primary', onClick: rowAction(r) }, () => '共同检查与报告'),
  },
];
function view(row: any) {
  if (active && !reading.value && !readError.value && listedRows.value.some(r => r.rowKey === row.rowKey && r.requisitionId === row.requisitionId && r.collectionId === row.collectionId)) drawerRef.value?.open(row.requisitionId);
}
</script>
