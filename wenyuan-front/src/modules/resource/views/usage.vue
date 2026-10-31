<template>
  <ResourcePage fill title="使用日志">
    <div class="resource-records">
      <n-alert v-if="error" type="error">{{ error }}</n-alert>
      <h-page-data-table
        ref="tableRef"
        :key="auth.identityRevision"
        class="resource-data-table"
        title="使用日志"
        :bordered="false"
        :is-add="false"
        :is-export="false"
        :is-edit="false"
        :is-copy="false"
        :is-delete="false"
        :show-operate="false"
        :enable-drag="false"
        :initial-page-size="20"
        :pagination="{ showQuickJumper: false, pageSlot: 5 }"
        reset-page-on-size-change
        :fetch-data="fetchRows"
        :params="queryParams"
        :columns="columns"
        :scroll-x="1200"
        column-config-key="resource-usage"
        :query-configurable="false"
      >
        <template #toolbar-extra
          ><n-button v-if="canExport" :loading="exporting" @click="download"
            ><template #icon
              ><n-icon><IconMdiDownload /></n-icon></template
            >导出日志</n-button
          ></template
        >

        <template #queryForm>
          <n-form-item label="资源" path="resourceKeyword"
            ><n-input v-model:value="form.resourceKeyword" placeholder="资源名称或编号" clearable @keydown.enter="load"
          /></n-form-item>
          <n-form-item label="操作人" path="caller"
            ><n-input v-model:value="form.caller" placeholder="姓名或账号" clearable @keydown.enter="load"
          /></n-form-item>
          <n-form-item label="动作" path="action"
            ><n-select
              v-model:value="form.action"
              :options="actions"
              clearable
              placeholder="全部动作"
              style="width: 140px"
          /></n-form-item>
          <n-form-item label="时间" path="from"
            ><n-date-picker
              v-model:formatted-value="range"
              type="datetimerange"
              value-format="yyyy-MM-dd HH:mm:ss"
              clearable
          /></n-form-item>
        </template>
      </h-page-data-table>
    </div>
    <ResourcePreview v-if="previewShow" v-model:show="previewShow" :resource-id="previewId" />
  </ResourcePage>
</template>
<script setup lang="ts">
import HPageDataTable from '@/components/data/table/PageDataTable.vue';
import ResourcePage from '../components/ResourcePage.vue';
import { computed, defineAsyncComponent, h, onBeforeUnmount, ref, watch } from 'vue';
import { NButton } from 'naive-ui';
import { formatDateTime } from '@/utils/datetime';
import { USAGE_ACTIONS, usageActionLabel, usageSceneLabel, usageResourceLabel } from '../utils/usage';
import { useAuthStore } from '@/store/modules/auth';
import { usageLogs } from '../api/business';
import { exportRows } from '../utils/files';
const ResourcePreview = defineAsyncComponent(async () => (await import('../components/ResourcePreview.vue')).default);
const auth = useAuthStore();
const canExport = computed(() => auth.hasPermission('RESOURCE_ALL,RESOURCE_LOG_EXPORT'));
const canPreview = computed(() => auth.hasPermission('RESOURCE_ALL,RESOURCE_SELECT'));
const previewShow = ref(false),
  previewId = ref('');
const form = ref<any>({ resourceKeyword: '', caller: '', action: null }),
  range = ref<[string, string] | null>(null),
  exporting = ref(false),
  error = ref('');
let generation = 0;
const actions = USAGE_ACTIONS;
const tableRef = ref<InstanceType<typeof HPageDataTable>>();
const columns = [
  { title: '时间', key: 'createTime', columnType: 'dateTime', width: 180 },
  {
    title: '动作',
    key: 'action',
    width: 100,
    render: (r: any) => usageActionLabel(r.action),
  },
  {
    title: '资源',
    key: 'resourceId',
    width: 230,
    render: (row: any) => {
      const label = usageResourceLabel(row);
      if (!canPreview.value || !row.resourceId || !row.resourceName || row.resourceAvailable === false) return label;
      return h(
        NButton,
        {
          text: true,
          type: 'primary',
          style: 'max-width: 100%',
          title: row.resourceCode ? `${label}（${row.resourceCode}）` : label,
          onClick: () => {
            previewId.value = row.resourceId;
            previewShow.value = true;
          },
        },
        () => h('span', { style: 'overflow: hidden; text-overflow: ellipsis; white-space: nowrap' }, label),
      );
    },
  },
  { title: '操作人', key: 'callerName', width: 140 },
  { title: '使用场景', key: 'scene', width: 130, render: (row: any) => usageSceneLabel(row.scene) },
  { title: '详情', key: 'detail', minWidth: 250, ellipsis: { tooltip: true } },
];
function params() {
  return { ...form.value, from: range.value?.[0], to: range.value?.[1] };
}
const queryParams = computed(params);
async function fetchRows(query: any) {
  const own = ++generation;
  error.value = '';
  try {
    return await usageLogs(query);
  } catch {
    if (own === generation) error.value = '日志读取失败或当前账号无查询权限';
    return { data: { list: [], total: 0 } };
  }
}
function load() {
  tableRef.value?.handleQuery();
}
async function download() {
  exporting.value = true;
  try {
    const r: any = await usageLogs(params(), true);
    await exportRows(
      (r.data?.list || []).map((v: any) => ({
        时间: formatDateTime(v.createTime),
        动作: usageActionLabel(v.action),
        资源: usageResourceLabel(v),
        资源编号: v.resourceCode || v.resourceId || '--',
        操作人: v.callerName,
        场景: usageSceneLabel(v.scene),
        详情: v.detail,
      })),
      '数字资源使用日志',
    );
  } finally {
    exporting.value = false;
  }
}
watch(
  () => auth.identityRevision,
  () => {
    generation++;
    previewShow.value = false;
    previewId.value = '';
    error.value = '';
  },
);
onBeforeUnmount(() => generation++);
</script>
<style scoped>
.resource-admin-page {
  height: 100%;
  overflow: auto;
}
</style>
