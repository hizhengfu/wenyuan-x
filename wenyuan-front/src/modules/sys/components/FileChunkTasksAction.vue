<template>
  <span class="chunk-tasks-action">
    <NButton @click="open">我的上传任务</NButton>
    <NModal :show="visible" preset="card" title="我的上传任务" style="width: min(960px, calc(100vw - 24px))" @update:show="setVisible">
      <div class="task-panel">
        <NAlert type="info" :bordered="false">仅显示本人当前机构的分片上传任务。任务完成不代表材料已归档；普通整文件上传请到文件列表核对。</NAlert>
        <div class="task-filters">
          <label>任务编号<NInput v-model:value="queryId" placeholder="按完整任务编号查找" clearable :disabled="loading" /></label>
          <label>任务状态<NSelect v-model:value="queryState" :options="stateOptions" clearable placeholder="全部状态" :disabled="loading" /></label>
          <NButton :loading="loading" @click="refresh">查询 / 刷新</NButton>
        </div>
        <NAlert v-if="error" type="error" role="alert">{{ error }}</NAlert>
        <p v-if="loading" role="status">正在读取当前任务，请稍候…</p>
        <div v-else-if="rows.length" class="task-table-scroll">
          <table class="task-table">
            <caption>本人当前机构的上传任务，共 {{ total }} 项</caption>
            <thead><tr><th>任务编号</th><th>状态</th><th>最近更新</th><th>有效期至</th><th>核对</th></tr></thead>
            <tbody><tr v-for="row in rows" :key="row.id">
              <td class="task-id" data-label="任务编号">{{ row.id }}</td><td data-label="状态"><div>{{ stateText(row.state) }}<br /><span v-if="row.expired">已过期，可继续核对</span></div></td>
              <td data-label="最近更新">{{ time(row.updatedAt) }}</td><td data-label="有效期至">{{ time(row.expiresAt) }}</td>
              <td data-label="核对"><NButton size="small" :disabled="detailLoading" @click="inspect(row.id)">核对结果</NButton></td>
            </tr></tbody>
          </table>
        </div>
        <p v-else-if="!error">当前筛选没有上传任务。旧版本未记录的任务不会出现在这里。</p>
        <div v-if="total > pageSize" class="task-pages">
          <NButton :disabled="loading || pageNum <= 1" @click="load(pageNum - 1)">上一页</NButton>
          <span>第 {{ pageNum }} / {{ Math.ceil(total / pageSize) }} 页</span>
          <NButton :disabled="loading || pageNum * pageSize >= total" @click="load(pageNum + 1)">下一页</NButton>
        </div>
        <p v-if="detailLoading" role="status">正在核对任务与当前文件记录…</p>
        <section v-if="detail" ref="detailPanel" tabindex="-1" class="task-detail" aria-label="任务核对结果">
          <h3>任务 {{ detail.task.id }}</h3>
          <p>{{ stateText(detail.task.state) }} · {{ detail.task.expired ? '已过期' : '有效期内' }}</p>
          <NAlert :type="detail.resultStatus === 'FILE_RECORD_AVAILABLE' ? 'info' : 'warning'" :bordered="false">{{ resultText[detail.resultStatus] }}</NAlert>
          <dl v-if="detail.file"><dt>上传时文件名称</dt><dd>{{ detail.file.name || '未命名' }}</dd><dt>文件编号</dt><dd>{{ detail.file.id }}</dd><dt>文件摘要</dt><dd>{{ detail.file.hash || '未留存' }}</dd></dl>
          <p>需要人工核对时，请保留任务编号和更新时间，联系授权人员核查文件列表与原业务记录。不要反复合并、重置状态或删除分片。</p>
        </section>
        <NButton @click="close">关闭</NButton>
      </div>
    </NModal>
  </span>
</template>
<script setup lang="ts">
import { ref, watch, nextTick, onBeforeUnmount, onDeactivated } from 'vue';
import { NAlert, NButton, NInput, NModal, NSelect } from 'naive-ui';
import { useAuthStore } from '@/store';
import { listChunkTasks, inspectChunkTask, type FileChunkTask, type FileChunkInspection } from '../api/file';

const auth = useAuthStore();
const visible = ref(false), loading = ref(false), detailLoading = ref(false), error = ref('');
const rows = ref<FileChunkTask[]>([]), detail = ref<FileChunkInspection>();
const queryId = ref(''), queryState = ref<string | null>(null), total = ref(0), pageNum = ref(1), pageSize = 20;
const detailPanel = ref<HTMLElement>();
let generation = 0;
const states: Record<string, string> = { OPEN: '等待上传或补片', FINISHING: '合并结果待核对', COMPLETE: '已保存完成回执', REVIEW: '异常结果待核对' };
const stateOptions = Object.entries(states).map(([value, label]) => ({ value, label }));
const resultText: Record<FileChunkInspection['resultStatus'], string> = {
  NOT_COMPLETE: '尚无可确认的完成结果，请核对原任务及业务记录。',
  INVALID_RECEIPT: '完成回执缺失或格式异常，需要人工核查。',
  NO_FILE_RECORD: '本任务没有文件目录记录，请核对接收该上传的业务处理结果。',
  FILE_RECORD_MISSING: '完成回执对应的文件记录已缺失，需要核查原存储与备份。',
  FILE_RECORD_CHANGED: '文件记录与完成回执不一致，需要核查历史变化。',
  FILE_NOT_READABLE: '当前无法按原件规则查阅文件，未展示文件详情。',
  FILE_RECORD_AVAILABLE: '文件记录与回执一致，且当前允许查阅。可按文件摘要在文件列表核对；业务归档和原件字节仍需分别确认。',
};
function stateText(state: string) { return states[state] || '未识别状态，请人工核对'; }
function time(value: number) { return new Date(value).toLocaleString('zh-CN', { hour12: false }); }
function validTask(value: unknown): value is FileChunkTask {
  if (!value || typeof value !== 'object') return false;
  const v = value as FileChunkTask;
  return typeof v.id === 'string' && !!v.id && typeof v.state === 'string' && typeof v.expired === 'boolean'
    && [v.createdAt, v.updatedAt, v.expiresAt].every(n => Number.isSafeInteger(n) && n >= 0);
}
function resetResults() { rows.value = []; detail.value = undefined; total.value = 0; error.value = ''; }
function close() { visible.value = false; generation++; loading.value = false; detailLoading.value = false; resetResults(); }
function setVisible(show: boolean) { if (!show) close(); }
function open() { visible.value = true; queryId.value = ''; queryState.value = null; void load(1); }
function refresh() { void load(1); }
async function load(page: number) {
  const ticket = ++generation;
  resetResults(); detailLoading.value = false; loading.value = true; pageNum.value = page;
  try {
    const response = await listChunkTasks({ pageNum: page, pageSize, id: queryId.value.trim() || undefined, state: queryState.value || undefined });
    if (!visible.value || ticket !== generation) return;
    const d = response.data;
    if (response.status !== 200 || !d || !Array.isArray(d.list) || !d.list.every(validTask) || !Number.isSafeInteger(d.total) || d.total < 0
      || d.pageNum !== page || d.pageSize !== pageSize || d.list.length > pageSize || d.total < d.list.length || new Set(d.list.map(r => r.id)).size !== d.list.length) throw new Error('invalid page');
    rows.value = d.list; total.value = d.total;
  } catch {
    if (visible.value && ticket === generation) { resetResults(); error.value = '未能读取当前任务。请重新查询；不能据此认定没有上传记录。'; }
  } finally { if (ticket === generation) loading.value = false; }
}
async function inspect(id: string) {
  const ticket = ++generation;
  detail.value = undefined; error.value = ''; detailLoading.value = true;
  try {
    const response = await inspectChunkTask(id);
    if (!visible.value || ticket !== generation) return;
    const d = response.data;
    if (response.status !== 200 || !d || !validTask(d.task) || d.task.id !== id || !Object.hasOwn(resultText, d.resultStatus)
      || (d.resultStatus === 'FILE_RECORD_AVAILABLE' && (d.task.state !== 'COMPLETE' || !d.file?.id))) throw new Error('invalid inspection');
    detail.value = { ...d, file: d.resultStatus === 'FILE_RECORD_AVAILABLE' ? d.file : undefined };
    await nextTick();
    if (visible.value && ticket === generation) { detailPanel.value?.focus({ preventScroll: true }); detailPanel.value?.scrollIntoView?.({ block: 'nearest' }); }
  } catch {
    if (visible.value && ticket === generation) { resetResults(); error.value = '未能核对当前任务。请重新查询，不要根据旧结果重复上传。'; }
  } finally { if (ticket === generation) detailLoading.value = false; }
}
watch(() => JSON.stringify([auth.token, auth.userInfo, auth.roles, auth.permissions]), close);
onDeactivated(close);
onBeforeUnmount(close);
</script>
<style scoped>
.chunk-tasks-action { display: inline-flex; }
.task-panel { display: grid; gap: 16px; max-height: 76vh; overflow: auto; }
.task-filters { display: flex; gap: 12px; align-items: end; flex-wrap: wrap; }
.task-filters label { display: grid; gap: 6px; min-width: 180px; flex: 1; }
.task-table-scroll { min-width: 0; overflow-x: auto; }
.task-table { width: 100%; border-collapse: collapse; text-align: left; }
.task-table caption { text-align: left; padding-bottom: 10px; }
.task-table th, .task-table td { padding: 10px; border-bottom: 1px solid #8883; }
.task-id { word-break: break-all; min-width: 120px; }
.task-pages { display: flex; gap: 12px; align-items: center; }
.task-detail { border: 1px solid #8884; border-radius: 6px; padding: 12px; overflow-wrap: anywhere; }
.task-detail h3 { margin-top: 0; }
.task-detail dd { margin: 4px 0 12px; }
@media (max-width: 600px) {
  .task-table, .task-table caption, .task-table tbody, .task-table tr { display: block; }
  .task-table thead { position: absolute; width: 1px; height: 1px; overflow: hidden; clip-path: inset(50%); }
  .task-table tr { border: 1px solid #8884; border-radius: 6px; padding: 8px; margin-bottom: 10px; }
  .task-table td { display: grid; grid-template-columns: 72px minmax(0, 1fr); gap: 8px; border: 0; padding: 5px 0; overflow-wrap: anywhere; }
  .task-table td::before { content: attr(data-label); font-weight: 600; }
  .task-id { min-width: 0; }
}
</style>
