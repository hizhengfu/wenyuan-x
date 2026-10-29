<template>
  <NAlert type="info" :bordered="false">
    下载模板填写历史遥测，上传后由后台校验。校验通过后确认导入，重复采样自动跳过，冲突和错误需修正。
    文件上传完成后可关闭页面，后台任务会继续执行。
  </NAlert>
  <NCard size="small" :bordered="false">
    <NSpace vertical :size="14">
      <NSpace align="center">
        <NButton type="primary" secondary :disabled="busy" :loading="action === 'template'" @click="downloadTemplate"
          >下载补录模板（Excel）</NButton
        >
        <input
          ref="fileInput"
          type="file"
          accept=".xlsx"
          :disabled="busy"
          aria-label="选择补录模板文件"
          @change="chooseFile"
        />
      </NSpace>
      <NForm inline :show-feedback="false" label-placement="left">
        <NFormItem label="采样时间所属时区">
          <NSelect v-model:value="timeZone" :disabled="busy" :options="timeZones" style="width: 230px" />
        </NFormItem>
        <NButton :disabled="!file || busy" :loading="action === 'upload'" @click="upload">上传并后台校验</NButton>
      </NForm>
      <template v-if="action === 'upload'">
        <NProgress type="line" :percentage="uploadPercent" />
        <NText>{{ uploadPercent < 100 ? '正在上传，请保持页面打开…' : '上传完成，正在保存后台任务…' }}</NText>
      </template>
    </NSpace>
  </NCard>
  <NAlert v-if="error" type="error" closable @close="error = ''">{{ error }}</NAlert>
  <NSpace justify="space-between" align="center">
    <NText strong>补录任务</NText>
    <NButton size="small" :disabled="busy" @click="refresh">刷新任务</NButton>
  </NSpace>
  <NDataTable
    :columns="taskColumns"
    :data="taskList"
    :loading="refreshing"
    remote
    :row-key="(row: BackfillTask) => row.id"
    :pagination="taskPagination"
    :scroll-x="1000"
    size="small"
  />
  <NCard v-if="selected" size="small" :title="`当前任务：${selected.fileName}`" :bordered="false">
    <NSpace vertical :size="12">
      <NText depth="3"
        >任务编号：{{ selected.id }} · 采样时区：{{ selected.timeZone }} · {{ statusLabels[selected.status] }}</NText
      >
      <NAlert v-if="running(selected)" type="info" :bordered="false">
        {{
          selected.status === 'PARSING'
            ? `已解析 ${selected.total} 行`
            : `已处理 ${selected.processed} / ${selected.total} 行`
        }}，后台继续执行，页面会自动更新。
      </NAlert>
      <NProgress
        v-if="['VALIDATING', 'IMPORTING'].includes(selected.status) && selected.total"
        type="line"
        :percentage="Math.min(100, Math.round((selected.processed * 100) / selected.total))"
      />
      <NAlert :type="selected.errors || selected.status === 'FAILED' ? 'warning' : 'info'" :bordered="false">
        共 {{ selected.total }} 行，可导入 {{ selected.insertable }} 行，重复 {{ selected.duplicates }} 行，错误或冲突
        {{ selected.errors }} 行。 已导入 {{ selected.affected }} 行，已跳过 {{ selected.skipped }} 行。
      </NAlert>
      <NAlert v-if="selected.errorMessage" type="error"
        >{{ selected.errorMessage }}。已完成的批次保留，重试将从断点继续。</NAlert
      >
      <NAlert v-if="selected.status === 'INVALID'" type="warning"
        >校验未通过，尚未导入。请查看行结果，修正文件后重新上传。</NAlert
      >
      <NAlert v-if="selected.purged || selected.status === 'EXPIRED'" type="warning"
        >任务明细已到期，需补录时请重新上传文件。</NAlert
      >
      <template v-if="selected.status === 'READY'">
        <NInput
          v-model:value="reason"
          :disabled="busy"
          maxlength="500"
          placeholder="填写补录原因，如设备离线期间人工记录补录"
        />
        <NButton type="primary" :disabled="busy || !reason.trim()" @click="confirmImport">确认后台导入</NButton>
      </template>
      <NButton v-if="selected.status === 'FAILED' && !selected.purged" :disabled="busy" @click="retry"
        >重试任务</NButton
      >
      <NSpace v-if="!selected.purged" align="center">
        <NText>行结果</NText>
        <NSelect v-model:value="rowStatus" :options="rowStatuses" style="width: 180px" />
        <NText depth="3">明细保留至 {{ formatDateTime(selected.expiresAt) }}。</NText>
      </NSpace>
      <NDataTable
        v-if="!selected.purged"
        :columns="rowColumns"
        :data="rowList"
        remote
        :loading="rowsLoading"
        :row-key="(row: BackfillRow) => row.rowNumber"
        :pagination="rowPagination"
        :scroll-x="1100"
        size="small"
      />
    </NSpace>
  </NCard>
</template>

<script setup lang="ts">
import { computed, h, onBeforeUnmount, ref, shallowRef, watch } from 'vue';
import { NButton, NTag, useDialog, useMessage, type DataTableColumns } from 'naive-ui';
import { saveAs } from 'file-saver';
import { formatDateTime } from '@/utils';
import { maintenanceApi, type BackfillRow, type BackfillTask } from '../api/dataMaintenance';

const props = defineProps<{ orgId: string }>();
const emit = defineEmits<{ busy: [value: boolean]; completed: [] }>();
const dialog = useDialog();
const message = useMessage();
const action = ref('');
const busy = computed(() => !!action.value);
const error = ref('');
const timeZone = ref('Asia/Shanghai');
const timeZones = [
  { label: '北京时间（Asia/Shanghai）', value: 'Asia/Shanghai' },
  { label: 'UTC', value: 'UTC' },
];
const file = shallowRef<File | null>(null);
const fileInput = ref<HTMLInputElement | null>(null);
const uploadPercent = ref(0);
const reason = ref('');
const taskList = ref<BackfillTask[]>([]);
const taskPage = ref(1);
const taskTotal = ref(0);
const selected = ref<BackfillTask | null>(null);
const refreshing = ref(false);
const rowList = ref<BackfillRow[]>([]);
const rowPage = ref(1);
const rowTotal = ref(0);
const rowStatus = ref('');
const rowsLoading = ref(false);
let epoch = 0;
let refreshSequence = 0;
let rowSequence = 0;
let timer: ReturnType<typeof setTimeout> | undefined;
let uploadController: AbortController | undefined;
let disposed = false;
const statusLabels: Record<BackfillTask['status'], string> = {
  QUEUED: '等待校验',
  PARSING: '解析文件',
  VALIDATING: '校验重复',
  READY: '待确认导入',
  INVALID: '校验未通过',
  IMPORT_QUEUED: '等待导入',
  IMPORTING: '导入中',
  SUCCEEDED: '已完成',
  FAILED: '执行失败',
  EXPIRED: '已过期',
};
const labels: Record<BackfillRow['status'], string> = {
  READY: '可导入',
  DUPLICATE: '重复跳过',
  CONFLICT: '值或质量冲突',
  INVALID: '校验失败',
  IMPORTED: '已导入',
  SKIPPED: '已跳过',
};
const rowStatuses = [
  { label: '全部结果', value: '' },
  ...Object.entries(labels).map(([value, label]) => ({ value, label })),
];
const running = (task: BackfillTask) =>
  ['QUEUED', 'PARSING', 'VALIDATING', 'IMPORT_QUEUED', 'IMPORTING'].includes(task.status);
const taskColumns: DataTableColumns<BackfillTask> = [
  { title: '文件', key: 'fileName', minWidth: 240 },
  { title: '状态', key: 'status', width: 130, render: (row) => statusLabels[row.status] },
  { title: '总行数', key: 'total', width: 100 },
  { title: '已导入', key: 'affected', width: 100 },
  { title: '上传时间', key: 'createdAt', width: 180, render: (row) => formatDateTime(row.createdAt) },
  {
    title: '操作',
    key: 'action',
    width: 90,
    render: (row) =>
      h(NButton, { size: 'small', disabled: busy.value, onClick: () => select(row) }, { default: () => '查看' }),
  },
];
const rowColumns: DataTableColumns<BackfillRow> = [
  { title: '行号', key: 'rowNumber', width: 75 },
  { title: '设备编码', key: 'deviceCode', width: 150 },
  { title: '指标', key: 'metricCode', width: 140 },
  {
    title: '采样时间',
    key: 'occurredAt',
    width: 175,
    render: (row) => (row.occurredAt == null ? '--' : formatDateTime(row.occurredAt)),
  },
  { title: '值', key: 'value', width: 150 },
  {
    title: '结果',
    key: 'status',
    width: 140,
    render: (row) =>
      h(
        NTag,
        { size: 'small', type: ['INVALID', 'CONFLICT'].includes(row.status) ? 'error' : 'info' },
        { default: () => labels[row.status] },
      ),
  },
  { title: '说明', key: 'message', minWidth: 260 },
];
const taskPagination = computed(() => ({
  page: taskPage.value,
  pageSize: 10,
  itemCount: taskTotal.value,
  onUpdatePage: (page: number) => {
    taskPage.value = page;
    void refresh();
  },
}));
const rowPagination = computed(() => ({
  page: rowPage.value,
  pageSize: 20,
  itemCount: rowTotal.value,
  onUpdatePage: (page: number) => {
    rowPage.value = page;
    void loadRows();
  },
}));

function fail(cause: unknown) {
  const e = cause as { response?: { data?: { message?: string } }; message?: string };
  error.value = e.response?.data?.message || e.message || '操作失败，请重试';
}
async function run(name: string, work: () => Promise<void>) {
  if (busy.value) return;
  action.value = name;
  error.value = '';
  try {
    await work();
  } catch (cause) {
    if (!disposed) fail(cause);
  } finally {
    action.value = '';
  }
}
function chooseFile(event: Event) {
  file.value = null;
  const chosen = (event.target as HTMLInputElement).files?.[0];
  if (!chosen) return;
  if (!chosen.name.toLowerCase().endsWith('.xlsx') || !chosen.size) {
    error.value = '请选择非空 xlsx 模板文件';
    return;
  }
  file.value = chosen;
  error.value = '';
}
function downloadTemplate() {
  void run('template', async () => saveAs(await maintenanceApi.template(props.orgId), '采集数据补录模板.xlsx'));
}
function upload() {
  const chosen = file.value;
  if (!chosen) return;
  void run('upload', async () => {
    uploadPercent.value = 0;
    uploadController = new AbortController();
    const task = (
      await maintenanceApi.upload(
        props.orgId,
        timeZone.value,
        chosen,
        (value) => {
          uploadPercent.value = value;
        },
        uploadController.signal,
      )
    ).data;
    if (disposed) return;
    selected.value = task;
    reason.value = '';
    rowPage.value = 1;
    rowStatus.value = '';
    rowList.value = [];
    file.value = null;
    if (fileInput.value) fileInput.value.value = '';
    taskPage.value = 1;
    message.success(`文件已提交后台校验，任务编号：${task.id}`);
    await refresh();
  });
}
function confirmImport() {
  const task = selected.value;
  if (!task || task.status !== 'READY' || !reason.value.trim()) return;
  const explanation = reason.value.trim();
  dialog.warning({
    title: '确认后台补录',
    content: `将导入“${task.fileName}”，校验可导入 ${task.insertable} 行，重复 ${task.duplicates} 行。后台分批执行，执行时再次核对当前数据；中断后保留已完成批次，可重试继续。`,
    positiveText: '确认导入',
    negativeText: '取消',
    onPositiveClick: () =>
      run('confirm', async () => {
        if (selected.value?.id !== task.id || selected.value.status !== 'READY' || props.orgId !== task.orgId)
          throw new Error('任务已变化，请重新确认');
        selected.value = (await maintenanceApi.confirm(task.id, explanation)).data;
        message.success('已加入后台导入队列');
        await refresh();
      }),
  });
}
function retry() {
  const task = selected.value;
  if (!task || task.status !== 'FAILED') return;
  void run('retry', async () => {
    selected.value = (await maintenanceApi.retry(task.id)).data;
    await refresh();
  });
}
function select(task: BackfillTask) {
  selected.value = task;
  reason.value = task.reason;
  rowPage.value = 1;
  rowStatus.value = '';
  rowList.value = [];
  void loadRows();
  void refresh();
}
function schedule() {
  clearTimeout(timer);
  if (!disposed && (taskList.value.some(running) || (selected.value && running(selected.value))))
    timer = setTimeout(() => {
      void refresh();
    }, 3000);
}
async function refresh() {
  const version = epoch;
  const sequence = ++refreshSequence;
  const org = props.orgId;
  clearTimeout(timer);
  refreshing.value = true;
  try {
    const result = (await maintenanceApi.tasks(org, taskPage.value)).data;
    if (disposed || version !== epoch || sequence !== refreshSequence) return;
    taskList.value = result.list;
    taskTotal.value = result.total;
    const id = selected.value?.id || result.list[0]?.id;
    if (id) {
      const current = (await maintenanceApi.task(id)).data;
      if (disposed || version !== epoch || sequence !== refreshSequence || (selected.value && selected.value.id !== id))
        return;
      const previous = selected.value;
      selected.value = current;
      if (current.status === 'SUCCEEDED' && previous?.status !== 'SUCCEEDED') emit('completed');
      if (!previous || previous.id !== id || previous.updatedAt !== current.updatedAt) await loadRows();
    }
  } catch (cause) {
    if (!disposed && version === epoch && sequence === refreshSequence) fail(cause);
  } finally {
    if (version === epoch && sequence === refreshSequence) {
      refreshing.value = false;
      schedule();
    }
  }
}
async function loadRows() {
  const task = selected.value;
  const version = epoch;
  const sequence = ++rowSequence;
  if (!task || task.purged) {
    rowList.value = [];
    rowTotal.value = 0;
    return;
  }
  rowsLoading.value = true;
  try {
    const result = (await maintenanceApi.taskRows(task.id, rowPage.value, 20, rowStatus.value)).data;
    if (disposed || version !== epoch || sequence !== rowSequence || selected.value?.id !== task.id) return;
    rowList.value = result.list;
    rowTotal.value = result.total;
  } catch (cause) {
    if (!disposed && version === epoch && sequence === rowSequence) fail(cause);
  } finally {
    if (version === epoch && sequence === rowSequence) rowsLoading.value = false;
  }
}
watch(busy, (value) => emit('busy', value));
watch(rowStatus, () => {
  rowPage.value = 1;
  void loadRows();
});
watch(
  () => props.orgId,
  () => {
    epoch++;
    clearTimeout(timer);
    selected.value = null;
    taskList.value = [];
    taskTotal.value = 0;
    rowList.value = [];
    rowTotal.value = 0;
    taskPage.value = 1;
    rowPage.value = 1;
    rowStatus.value = '';
    file.value = null;
    reason.value = '';
    error.value = '';
    if (fileInput.value) fileInput.value.value = '';
    void refresh();
  },
  { immediate: true },
);
onBeforeUnmount(() => {
  disposed = true;
  epoch++;
  clearTimeout(timer);
  uploadController?.abort();
  emit('busy', false);
});
</script>
