<template>
  <n-drawer v-model:show="visible" width="min(960px, 100vw)" placement="right">
    <n-drawer-content title="统计导出任务" closable>
      <n-alert v-if="error" type="error" :show-icon="false">{{ error }}</n-alert>
      <n-card v-if="active" title="当前任务" size="small" class="export-current">
        <n-space vertical>
          <n-text>{{ statusNames[active.status] }}：{{ active.message }}</n-text>
          <n-progress type="line" :percentage="active.progress" :status="active.status === 'FAILED' ? 'error' : active.status === 'SUCCESS' ? 'success' : 'default'" />
          <n-text v-if="active.errorMessage" type="error">{{ active.errorMessage }}</n-text>
        </n-space>
      </n-card>
      <h-page-data-table ref="table" :fetch-data="fetchTasks" :columns="columns" :scroll-x="800" :is-add="false" :show-operate="false" :show-header="false" style="height: 520px" />
    </n-drawer-content>
  </n-drawer>
</template>
<script setup lang="ts">
import { h, onBeforeUnmount, ref, watch } from 'vue';
import { NButton, NSpace, useDialog, type DataTableColumns } from 'naive-ui';
import dayjs from 'dayjs';
import { ajaxGetDownload, apiUrl } from '@/utils';
import { cancelAsyncTask, deleteAsyncTask, getAsyncTask, listAsyncTasks, type AsyncTask } from '@/modules/sys/api/asyncTask';
import { canCancelAsyncTask, canDeleteAsyncTask, canDownloadAsyncTask, createAsyncTaskWatchGeneration, isTerminalAsyncTask, subscribeAsyncTaskEvents, type AsyncTaskEventSubscription } from '@/modules/sys/utils/asyncTask';
const visible = ref(false); const active = ref<AsyncTask>(); const error = ref(''); const table = ref<{ handleRefresh: () => void }>();
const dialog = useDialog(); let subscription: AsyncTaskEventSubscription | undefined;
const generation = createAsyncTaskWatchGeneration(() => active.value?.id);
const statusNames: Record<string, string> = { CREATED: '等待执行', RUNNING: '正在导出', CANCELLING: '正在取消', SUCCESS: '已完成', FAILED: '失败', CANCELLED: '已取消' };
function closeWatch() { generation.invalidate(); subscription?.close(); subscription = undefined; }
function watchTask(task: AsyncTask) {
  closeWatch(); active.value = task;
  if (isTerminalAsyncTask(task.status)) return;
  const current = generation.begin(task.id);
  subscription = subscribeAsyncTaskEvents(task.id, {
    onTask: updated => { if (current()) active.value = updated; },
    onTerminal: () => { if (current()) { subscription = undefined; table.value?.handleRefresh(); } },
    onError: () => { if (current()) error.value = '任务进度连接中断，可刷新任务列表查看状态'; },
  });
}
function open(task?: AsyncTask) { error.value = ''; visible.value = true; if (task) watchTask(task); table.value?.handleRefresh(); }
async function fetchTasks(params: Record<string, unknown>) {
  const response: any = await listAsyncTasks({ ...params, taskType: 'CMS_STAT_EXPORT' });
  if (!visible.value) return response;
  const rows: AsyncTask[] = response?.data?.list || [];
  const selected = rows.find(row => row.id === active.value?.id);
  if (selected && (!subscription || isTerminalAsyncTask(selected.status))) watchTask(selected);
  else if (!active.value) { const running = rows.find(row => !isTerminalAsyncTask(row.status)); if (running) watchTask(running); }
  return response;
}
async function download(row: AsyncTask) {
  try {
    const response: any = await getAsyncTask(row.id); const artifact = response.data?.artifacts?.[0];
    if (!artifact) { error.value = '导出文件已过期或已清理'; return; }
    await ajaxGetDownload(apiUrl(`sys/async-task/${row.id}/artifacts/${artifact.id}/download`), artifact.fileName);
  } catch { error.value = '下载失败，请重试'; }
}
function confirmAction(row: AsyncTask, remove: boolean) {
  dialog.warning({ title: remove ? '删除导出任务' : '取消导出任务', content: remove ? '将删除任务记录及导出文件。' : '将停止本次导出。', positiveText: '确认', negativeText: '返回',
    onPositiveClick: async () => {
      try {
        if (remove) { await deleteAsyncTask(row.id); if (active.value?.id === row.id) { closeWatch(); active.value = undefined; } }
        else { await cancelAsyncTask(row.id); const response: any = await getAsyncTask(row.id); if (visible.value) watchTask(response.data); }
        table.value?.handleRefresh();
      } catch { error.value = remove ? '删除失败' : '取消失败'; }
    },
  });
}
const columns: DataTableColumns<AsyncTask> = [
  { key: 'createTime', title: '创建时间', width: 170, render: row => dayjs(row.createTime).format('YYYY-MM-DD HH:mm:ss') },
  { key: 'status', title: '状态', width: 100, render: row => h(NButton, { text: true, onClick: () => watchTask(row) }, () => statusNames[row.status]) },
  { key: 'processedCount', title: '已导出行数', width: 120 },
  { key: 'message', title: '进度说明', ellipsis: { tooltip: true } },
  { key: 'operate', title: '操作', width: 180, render: row => h(NSpace, {}, { default: () => [
    canDownloadAsyncTask(row.status) ? h(NButton, { size: 'small', onClick: () => download(row) }, () => '下载') : null,
    canCancelAsyncTask(row.status) ? h(NButton, { size: 'small', onClick: () => confirmAction(row, false) }, () => '取消') : null,
    canDeleteAsyncTask(row.status) ? h(NButton, { size: 'small', onClick: () => confirmAction(row, true) }, () => '删除') : null,
  ] }) },
];
watch(visible, open => { if (!open) closeWatch(); });
onBeforeUnmount(closeWatch);
defineExpose({ open });
</script>
<style scoped>.export-current { margin: 12px 0; }</style>
