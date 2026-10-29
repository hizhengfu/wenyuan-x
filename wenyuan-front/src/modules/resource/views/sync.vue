<template>
  <ResourcePage fill title="扫描入库">
  <div class="resource-scan-page">
    <div class="resource-scan-layout">
      <div class="resource-scan-sources">
        <n-card title="扫描源列表" size="small" :bordered="false" class="resource-scan-panel">
          <template #header-extra>
            <n-space size="small" align="center">
              <n-input
                v-model:value="sourceQuery.name"
                size="small"
                clearable
                placeholder="搜索名称"
                style="width: 150px"
                @keyup.enter="loadSources"
              >
                <template #prefix><Icon icon="mdi:magnify" /></template>
              </n-input>
              <n-button size="small" type="primary" secondary @click="openCreateSource">
                <template #icon><Icon icon="mdi:plus" /></template>
                新建
              </n-button>
              <n-button size="small" secondary :loading="sourceLoading" @click="loadSources">
                <template #icon><Icon icon="mdi:refresh" /></template>
                刷新
              </n-button>
            </n-space>
          </template>
          <n-data-table
            size="small"
            :columns="sourceColumns"
            :scroll-x="680"
            :data="sourceRows"
            :loading="sourceLoading"
            :pagination="{ pageSize: 8 }"
            :row-key="rowKey"
            :row-props="sourceRowProps"
            :bordered="false"
            flex-height
          />
        </n-card>
      </div>

      <div class="resource-scan-workspace">
        <n-card title="目录扫描入库" size="small" :bordered="false" class="resource-scan-panel">
          <template #header-extra>
            <n-space size="small" align="center">
              <n-tag :type="connectionTagType" size="small">{{ scanConnectionStatus }}</n-tag>
              <n-tag v-if="activeTask?.status" :type="taskStatusType(activeTask.status)" size="small">
                {{ taskStatusLabel(activeTask.status) }}
              </n-tag>
              <n-button
                v-if="activeTask && canCancelAsyncTask(activeTask.status)"
                size="tiny"
                type="warning"
                secondary
                :loading="scanActionLoading"
                @click="cancelActiveScan"
              >
                取消任务
              </n-button>
              <n-popconfirm
                v-if="activeTask && canDeleteAsyncTask(activeTask.status)"
                @positive-click="deleteActiveScan"
              >
                <template #trigger>
                  <n-button size="tiny" type="error" secondary :loading="scanActionLoading">删除任务</n-button>
                </template>
                删除任务时会同时清理扫描明细，是否继续？
              </n-popconfirm>
            </n-space>
          </template>

          <n-form inline label-placement="left" size="small" :show-feedback="false" class="scan-form">
            <n-form-item label="选择扫描源">
              <n-select
                v-model:value="selectedSourceId"
                :options="sourceOptions"
                filterable
                clearable
                placeholder="选择扫描源"
                :disabled="!sourceOptions.length"
              />
            </n-form-item>
            <n-form-item label="自动分类">
              <n-switch v-model:value="scanForm.autoCreateCategory" />
            </n-form-item>
            <n-form-item label="复制入库">
              <n-switch v-model:value="scanForm.importFiles" />
            </n-form-item>
            <n-form-item label="文件上限">
              <n-input-number v-model:value="scanForm.maxFiles" :min="1" :precision="0" clearable />
            </n-form-item>
            <n-form-item style="width: 100px">
              <n-button type="primary" block :loading="scanStarting" @click="startSelectedScan">
                <template #icon><Icon icon="mdi:play-circle-outline" /></template>
                开始
              </n-button>
            </n-form-item>
          </n-form>

          <div class="metric-grid">
            <div class="metric-cell">
              <span>发现</span>
              <strong>{{ activeTask?.totalCount ?? 0 }}</strong>
            </div>
            <div class="metric-cell">
              <span>入库</span>
              <strong>{{ activeTask?.successCount ?? 0 }}</strong>
            </div>
            <div class="metric-cell">
              <span>跳过</span>
              <strong>{{ activeTask?.skippedCount ?? 0 }}</strong>
            </div>
            <div class="metric-cell">
              <span>失败</span>
              <strong>{{ activeTask?.failedCount ?? 0 }}</strong>
            </div>
            <div class="metric-cell wide">
              <span>结果</span>
              <strong class="message-text">{{
                activeTask?.message || activeTask?.errorMessage || '暂无扫描任务'
              }}</strong>
            </div>
          </div>
        </n-card>

        <n-card title="扫描文件列表" size="small" :bordered="false" class="resource-scan-panel resource-scan-results">
          <template #header-extra>
            <n-space size="small">
              <n-button
                size="small"
                secondary
                :disabled="!activeTask?.id"
                :loading="scanItemsLoading"
                @click="loadActiveScanItems"
              >
                <template #icon><Icon icon="mdi:refresh" /></template>
                刷新
              </n-button>
            </n-space>
          </template>
          <n-data-table
            size="small"
            :columns="scanItemColumns"
            :data="scanItemRows"
            :loading="scanItemsLoading"
            :pagination="{ pageSize: 20 }"
            :row-key="rowKey"
            :bordered="false"
            flex-height
            scroll-x="1200"
          />
        </n-card>
      </div>
    </div>

    <n-modal
      v-model:show="sourceModalVisible"
      preset="card"
      :title="sourceModalTitle"
      style="width: min(720px, calc(100vw - 32px))"
    >
      <n-form ref="sourceFormRef" :model="sourceForm" :rules="sourceRules" label-placement="top" size="small">
        <n-grid :cols="2" :x-gap="12">
          <n-grid-item>
            <n-form-item label="名称" path="name">
              <n-input v-model:value="sourceForm.name" placeholder="扫描源名称" clearable />
            </n-form-item>
          </n-grid-item>
          <n-grid-item>
            <n-form-item label="服务器（备注）" path="serverHost">
              <n-input v-model:value="sourceForm.serverHost" placeholder="本机/主机标识" clearable />
            </n-form-item>
          </n-grid-item>
          <n-grid-item :span="2">
            <n-form-item label="扫描目录" path="sourcePath">
              <n-input
                v-model:value="sourceForm.sourcePath"
                placeholder="/data/resources、D:\resources 或 \\server\share\resources"
                clearable
              />
              <div class="source-path-hint">
                扫描由后端 Worker 执行；Windows 服务访问网络目录时请使用 UNC 路径，不要使用登录用户映射盘。
              </div>
            </n-form-item>
          </n-grid-item>
          <n-grid-item :span="2">
            <n-form-item label="挂载父分类" path="targetCategoryId">
              <CategoryTreeSelect v-model:value="sourceForm.targetCategoryId" />
            </n-form-item>
          </n-grid-item>
          <n-grid-item>
            <n-form-item label="启用" path="enabled">
              <n-switch v-model:value="sourceForm.enabled" />
            </n-form-item>
          </n-grid-item>
        </n-grid>
        <n-space justify="end">
          <n-button @click="sourceModalVisible = false">取消</n-button>
          <n-button type="primary" :loading="sourceSaving" @click="saveSource">
            <template #icon><Icon icon="mdi:content-save-outline" /></template>
            保存
          </n-button>
        </n-space>
      </n-form>
    </n-modal>
  </div>
  </ResourcePage>
</template>

<script lang="ts" setup>
import ResourcePage from '../components/ResourcePage.vue';
import { computed, h, nextTick, onMounted, onUnmounted, ref } from 'vue';
import { Icon } from '@iconify/vue';
import {
  NButton,
  NPopconfirm,
  NSpace,
  NTag,
  useMessage,
  type DataTableColumns,
  type FormInst,
  type FormRules,
} from 'naive-ui';
import { cancelAsyncTask, deleteAsyncTask, getAsyncTask } from '@/modules/sys/api/asyncTask';
import type { AsyncTask, AsyncTaskStatus } from '@/modules/sys/api/asyncTask';
import {
  canCancelAsyncTask,
  canDeleteAsyncTask,
  createAsyncTaskWatchGeneration,
  isTerminalAsyncTask,
  subscribeAsyncTaskEvents,
} from '@/modules/sys/utils/asyncTask';
import type { AsyncTaskEventSubscription } from '@/modules/sys/utils/asyncTask';
import { isPermanentFetchSseError } from '@/modules/sys/utils/fetchSse';
import CategoryTreeSelect from '../components/CategoryTreeSelect.vue';
import { formatSize } from '../api/stat';
import { deleteSyncSource, saveSyncSource, scanItems, startDirectoryScan, syncSourceList } from '../api/resource';

type TagType = 'default' | 'success' | 'warning' | 'error' | 'info';

interface SyncSourceRow {
  id?: string;
  name?: string;
  serverHost?: string;
  sourcePath?: string;
  targetCategoryId?: string;
  enabled?: boolean;
  status?: string;
  lastSyncTime?: string;
  message?: string;
}

interface ScanItemRow {
  id?: string;
  relativePath?: string;
  fileName?: string;
  fileExt?: string;
  fileSize?: number;
  fileHash?: string;
  categoryPath?: string;
  resourceId?: string;
  status?: string;
  message?: string;
}

const message = useMessage();
const sourceFormRef = ref<FormInst | null>(null);
const sourceLoading = ref(false);
const sourceSaving = ref(false);
const sourceModalVisible = ref(false);
const scanStarting = ref(false);
const scanActionLoading = ref(false);
const scanItemsLoading = ref(false);
const sourceRows = ref<SyncSourceRow[]>([]);
const selectedSourceId = ref('');
const activeTask = ref<AsyncTask | null>(null);
const scanItemRows = ref<ScanItemRow[]>([]);
const scanConnectionStatus = ref('未连接');
let scanSubscription: AsyncTaskEventSubscription | null = null;
let sourceRequestGeneration = 0;
let scanItemsRequestGeneration = 0;
let taskRequestGeneration = 0;
const scanWatchGeneration = createAsyncTaskWatchGeneration(() => activeTask.value?.id);

const sourceQuery = ref({ name: '' });
const sourceForm = ref<SyncSourceRow>({
  name: '',
  serverHost: '',
  sourcePath: '',
  targetCategoryId: '',
  enabled: true,
});
const scanForm = ref({
  autoCreateCategory: true,
  importFiles: true,
  maxFiles: null as number | null,
});

const sourceRules: FormRules = {
  name: { required: true, message: '请输入扫描源名称', trigger: ['input', 'blur'] },
  sourcePath: { required: true, message: '请输入扫描目录', trigger: ['input', 'blur'] },
};

const activeSource = computed(() => sourceRows.value.find((row) => row.id === selectedSourceId.value));
const sourceModalTitle = computed(() => (sourceForm.value.id ? '编辑扫描源' : '新建扫描源'));
const sourceOptions = computed(() =>
  sourceRows.value
    .filter((row) => row.id)
    .map((row) => ({
      label: `${row.name || row.sourcePath || '未命名扫描源'}${row.enabled === false ? '（停用）' : ''}`,
      value: row.id as string,
      disabled: row.enabled === false,
    })),
);
const connectionTagType = computed<TagType>(() => {
  if (scanConnectionStatus.value === '已连接') return 'success';
  if (scanConnectionStatus.value === '连接中') return 'info';
  if (scanConnectionStatus.value.includes('断开')) return 'warning';
  if (scanConnectionStatus.value.includes('失败')) return 'error';
  return 'default';
});

const sourceColumns: DataTableColumns<SyncSourceRow> = [
  {
    title: '名称',
    key: 'name',
    minWidth: 140,
    ellipsis: { tooltip: true },
  },
  {
    title: '目录',
    key: 'sourcePath',
    minWidth: 220,
    ellipsis: { tooltip: true },
  },
  {
    title: '状态',
    key: 'status',
    width: 86,
    render: (row) =>
      h(NTag, { size: 'small', type: sourceStatusType(row.status) }, { default: () => sourceStatusLabel(row.status) }),
  },
  {
    title: '操作',
    key: 'actions',
    width: 220,
    fixed: 'right',
    render: (row) =>
      h(
        NSpace,
        { size: 6 },
        {
          default: () => [
            h(
              NButton,
              {
                size: 'tiny',
                quaternary: true,
                onClick: (event: MouseEvent) => {
                  event.stopPropagation();
                  openEditSource(row);
                },
              },
              { icon: () => h(Icon, { icon: 'mdi:pencil-outline' }), default: () => '编辑' },
            ),
            h(
              NButton,
              {
                size: 'tiny',
                type: 'primary',
                quaternary: true,
                onClick: (event: MouseEvent) => {
                  event.stopPropagation();
                  startSourceScan(row);
                },
              },
              { icon: () => h(Icon, { icon: 'mdi:play-circle-outline' }), default: () => '扫描' },
            ),
            h(
              NPopconfirm,
              {
                onPositiveClick: () => deleteSource(row),
              },
              {
                trigger: () =>
                  h(
                    NButton,
                    {
                      size: 'tiny',
                      type: 'error',
                      quaternary: true,
                      onClick: (event: MouseEvent) => event.stopPropagation(),
                    },
                    { icon: () => h(Icon, { icon: 'mdi:delete-outline' }), default: () => '删除' },
                  ),
                default: () => '确认删除该扫描源？',
              },
            ),
          ],
        },
      ),
  },
];

const scanItemColumns: DataTableColumns<ScanItemRow> = [
  {
    title: '相对路径',
    key: 'relativePath',
    minWidth: 260,
    ellipsis: { tooltip: true },
    render: (row) => row.relativePath || row.fileName || '-',
  },
  {
    title: '格式',
    key: 'fileExt',
    width: 80,
    render: (row) => row.fileExt || '-',
  },
  {
    title: '大小',
    key: 'fileSize',
    width: 110,
    render: (row) => formatSize(Number(row.fileSize || 0)),
  },
  {
    title: '自动分类',
    key: 'categoryPath',
    minWidth: 180,
    ellipsis: { tooltip: true },
    render: (row) => row.categoryPath || '-',
  },
  {
    title: '状态',
    key: 'status',
    width: 92,
    render: (row) =>
      h(NTag, { size: 'small', type: itemStatusType(row.status) }, { default: () => itemStatusLabel(row.status) }),
  },
  {
    title: '资源ID',
    key: 'resourceId',
    width: 170,
    ellipsis: { tooltip: true },
    render: (row) => row.resourceId || '-',
  },
  {
    title: '消息',
    key: 'message',
    minWidth: 220,
    ellipsis: { tooltip: true },
    render: (row) => row.message || '-',
  },
];

function rowKey(row: any) {
  return row.id || row.sourcePath || row.relativePath;
}

function sourceRowProps(row: SyncSourceRow) {
  return {
    class: row.id === selectedSourceId.value ? 'is-selected-source' : '',
    onClick: () => selectSource(row),
  };
}

function resetSourceForm() {
  sourceForm.value = {
    name: '',
    serverHost: '',
    sourcePath: '',
    targetCategoryId: '',
    enabled: true,
  };
}

function restoreSourceValidation() {
  nextTick(() => sourceFormRef.value?.restoreValidation());
}

function openCreateSource() {
  resetSourceForm();
  sourceModalVisible.value = true;
  restoreSourceValidation();
}

function selectSource(row: SyncSourceRow) {
  selectedSourceId.value = row.id || '';
}

function openEditSource(row: SyncSourceRow) {
  selectSource(row);
  sourceForm.value = {
    id: row.id,
    name: row.name || '',
    serverHost: row.serverHost || '',
    sourcePath: row.sourcePath || '',
    targetCategoryId: row.targetCategoryId || '',
    enabled: row.enabled !== false,
  };
  sourceModalVisible.value = true;
  restoreSourceValidation();
}

async function loadSources(shouldApplyOrEvent?: (() => boolean) | Event) {
  const shouldApply = typeof shouldApplyOrEvent === 'function' ? shouldApplyOrEvent : () => true;
  const requestGeneration = ++sourceRequestGeneration;
  sourceLoading.value = true;
  try {
    const resp: any = await syncSourceList({ ...sourceQuery.value, pageSize: 0 });
    if (requestGeneration !== sourceRequestGeneration || !shouldApply()) return;
    sourceRows.value = extractListPayload(resp);
    const selectedStillExists = sourceRows.value.some((row) => row.id === selectedSourceId.value);
    if (!selectedStillExists) {
      const firstEnabled = sourceRows.value.find((row) => row.enabled !== false);
      selectedSourceId.value = firstEnabled?.id || sourceRows.value[0]?.id || '';
    }
  } finally {
    if (requestGeneration === sourceRequestGeneration) sourceLoading.value = false;
  }
}

async function saveSource() {
  await sourceFormRef.value?.validate();
  sourceSaving.value = true;
  try {
    const resp: any = await saveSyncSource({
      ...sourceForm.value,
      targetCategoryId: sourceForm.value.targetCategoryId || null,
    });
    const savedId = resp?.data || selectedSourceId.value;
    selectedSourceId.value = savedId;
    message.success('保存成功');
    await loadSources();
    selectedSourceId.value = savedId;
    sourceModalVisible.value = false;
  } finally {
    sourceSaving.value = false;
  }
}

async function deleteSource(row: SyncSourceRow) {
  if (!row.id) return;
  await deleteSyncSource(row.id);
  message.success('删除成功');
  if (selectedSourceId.value === row.id) {
    selectedSourceId.value = '';
    closeScanEvents();
    activeTask.value = null;
    scanItemRows.value = [];
  }
  await loadSources();
}

async function startSelectedScan() {
  if (!activeSource.value) {
    message.warning('请选择扫描源或先新建扫描源');
    return;
  }
  await startSourceScan(activeSource.value);
}

async function startSourceScan(row: SyncSourceRow) {
  const sourceId = row.id || '';
  const sourcePath = row.sourcePath || sourceForm.value.sourcePath;
  if (!sourceId && !sourcePath) {
    message.warning('请选择扫描源或输入扫描目录');
    return;
  }
  scanStarting.value = true;
  try {
    const resp: any = await startDirectoryScan({
      sourceId: sourceId || undefined,
      sourcePath: sourceId ? undefined : sourcePath,
      targetCategoryId: row.targetCategoryId || sourceForm.value.targetCategoryId || undefined,
      autoCreateCategory: scanForm.value.autoCreateCategory,
      importFiles: scanForm.value.importFiles,
      maxFiles: scanForm.value.maxFiles || undefined,
    });
    activeTask.value = resp?.data || null;
    scanItemRows.value = [];
    const startedTask = activeTask.value;
    if (startedTask?.id) {
      watchScanTask(startedTask);
      await loadScanItemsByTask(startedTask.id);
      await refreshActiveTask(startedTask.id);
    }
    message.success('扫描任务已启动');
  } finally {
    scanStarting.value = false;
  }
}

async function loadActiveScanItems() {
  const taskId = activeTask.value?.id;
  if (!taskId) return;
  await loadScanItemsByTask(taskId);
  await refreshActiveTask(taskId);
}

async function refreshActiveTask(taskId: string, shouldApply: () => boolean = () => activeTask.value?.id === taskId) {
  const requestGeneration = ++taskRequestGeneration;
  const resp: any = await getAsyncTask(taskId);
  if (requestGeneration === taskRequestGeneration && shouldApply() && resp?.data) {
    activeTask.value = resp.data;
  }
}

async function loadScanItemsByTask(taskId: string, shouldApply: () => boolean = () => activeTask.value?.id === taskId) {
  const requestGeneration = ++scanItemsRequestGeneration;
  scanItemsLoading.value = true;
  try {
    const resp: any = await scanItems(taskId);
    if (requestGeneration !== scanItemsRequestGeneration || !shouldApply()) return;
    scanItemRows.value = extractListPayload(resp);
  } finally {
    if (requestGeneration === scanItemsRequestGeneration) scanItemsLoading.value = false;
  }
}

function extractListPayload(resp: any): any[] {
  const candidates = [
    resp?.data?.list,
    resp?.data?.data,
    resp?.data?.records,
    resp?.data?.rows,
    resp?.data?.content,
    resp?.list,
    resp?.records,
    resp?.rows,
    resp?.content,
    resp?.data,
    resp,
  ];
  for (const item of candidates) {
    if (Array.isArray(item)) return item;
    if (item && typeof item === 'object') {
      const nested = [item.list, item.data, item.records, item.rows, item.content].find((value) =>
        Array.isArray(value),
      );
      if (nested) return nested;
    }
  }
  return [];
}

function watchScanTask(task: AsyncTask) {
  closeScanEvents();
  activeTask.value = task;
  const isCurrentWatch = scanWatchGeneration.begin(task.id);
  if (isTerminalAsyncTask(task.status)) {
    scanConnectionStatus.value = terminalConnectionLabel(task.status);
    return;
  }

  scanConnectionStatus.value = '连接中';
  const itemEventHandler = (payload: unknown) => {
    if (!isCurrentWatch()) return;
    handleScanItemEvent(payload);
  };
  let terminalReceived = false;
  let connectionStopped = false;
  let closeWhenReady = false;
  let refreshRequested = false;
  let subscription: AsyncTaskEventSubscription | undefined;
  const refreshScanStateOnce = () => {
    if (refreshRequested) return;
    refreshRequested = true;
    void Promise.all([
      loadSources(isCurrentWatch),
      loadScanItemsByTask(task.id, isCurrentWatch),
      refreshActiveTask(task.id, isCurrentWatch),
    ]);
  };
  const stopSubscription = () => {
    scanSubscription = null;
    if (subscription) subscription.close();
    else closeWhenReady = true;
  };
  subscription = subscribeAsyncTaskEvents(task.id, {
    onOpen: () => {
      if (!isCurrentWatch()) return;
      if (!terminalReceived && !connectionStopped) scanConnectionStatus.value = '已连接';
    },
    onError: (error) => {
      if (!isCurrentWatch()) return;
      if (terminalReceived || connectionStopped) return;
      if (!isPermanentFetchSseError(error)) {
        scanConnectionStatus.value = '断开重连中';
        return;
      }
      connectionStopped = true;
      scanConnectionStatus.value = '连接失败，已停止重试';
      stopSubscription();
      refreshScanStateOnce();
    },
    onTask: (updatedTask) => {
      if (!isCurrentWatch()) return;
      if (updatedTask.id !== task.id) return;
      activeTask.value = updatedTask;
    },
    onTerminal: (terminalTask, eventName) => {
      if (!isCurrentWatch()) return;
      if (terminalTask?.id && terminalTask.id !== task.id) return;
      terminalReceived = true;
      scanSubscription = null;
      scanConnectionStatus.value = terminalConnectionLabel(terminalTask?.status, eventName);
      refreshScanStateOnce();
    },
    extraEvents: {
      'resource-file-found': itemEventHandler,
      'resource-file-imported': itemEventHandler,
      'resource-file-skipped': itemEventHandler,
      'resource-file-failed': itemEventHandler,
    },
  });
  if (!isCurrentWatch() || terminalReceived || connectionStopped || closeWhenReady) subscription.close();
  else scanSubscription = subscription;
}

function closeScanEvents() {
  scanWatchGeneration.invalidate();
  scanSubscription?.close();
  scanSubscription = null;
  scanConnectionStatus.value = '未连接';
}

function handleScanItemEvent(payload: unknown) {
  const item = (payload as { item?: ScanItemRow } | null)?.item;
  if (item) upsertScanItem(item);
}

async function cancelActiveScan() {
  const task = activeTask.value;
  if (!task || !canCancelAsyncTask(task.status)) return;
  scanActionLoading.value = true;
  try {
    await cancelAsyncTask(task.id);
    const resp: any = await getAsyncTask(task.id);
    if (resp?.data) watchScanTask(resp.data);
    message.success('已提交取消请求');
  } finally {
    scanActionLoading.value = false;
  }
}

async function deleteActiveScan() {
  const task = activeTask.value;
  if (!task || !canDeleteAsyncTask(task.status)) return;
  scanActionLoading.value = true;
  try {
    await deleteAsyncTask(task.id);
    closeScanEvents();
    activeTask.value = null;
    scanItemRows.value = [];
    await loadSources();
    message.success('任务及扫描明细已删除');
  } finally {
    scanActionLoading.value = false;
  }
}

function terminalConnectionLabel(status?: AsyncTaskStatus, eventName?: string) {
  if (status === 'SUCCESS') return '扫描完成';
  if (status === 'CANCELLED') return '已取消';
  if (status === 'FAILED') return '扫描失败';
  if (eventName === 'task-finished') return '扫描完成';
  if (eventName === 'task-cancelled') return '已取消';
  return '扫描失败';
}

function upsertScanItem(item: ScanItemRow) {
  if (!item?.id) return;
  const index = scanItemRows.value.findIndex((row) => row.id === item.id);
  if (index >= 0) {
    scanItemRows.value[index] = { ...scanItemRows.value[index], ...item };
  } else {
    scanItemRows.value.push(item);
  }
}

function sourceStatusLabel(status?: string) {
  return (
    (
      {
        IDLE: '空闲',
        RUNNING: '运行中',
        SUCCESS: '成功',
        FAILED: '失败',
      } as Record<string, string>
    )[status || ''] || '空闲'
  );
}

function sourceStatusType(status?: string): TagType {
  return (
    (
      {
        IDLE: 'default',
        RUNNING: 'warning',
        SUCCESS: 'success',
        FAILED: 'error',
      } as Record<string, TagType>
    )[status || ''] || 'default'
  );
}

function taskStatusLabel(status?: AsyncTaskStatus) {
  return (
    (
      {
        CREATED: '已创建',
        RUNNING: '扫描中',
        CANCELLING: '正在取消',
        SUCCESS: '完成',
        FAILED: '失败',
        CANCELLED: '已取消',
      } as Record<string, string>
    )[status || ''] || '-'
  );
}

function taskStatusType(status?: AsyncTaskStatus): TagType {
  return (
    (
      {
        CREATED: 'info',
        RUNNING: 'warning',
        CANCELLING: 'warning',
        SUCCESS: 'success',
        FAILED: 'error',
        CANCELLED: 'default',
      } as Record<string, TagType>
    )[status || ''] || 'default'
  );
}

function itemStatusLabel(status?: string) {
  return (
    (
      {
        FOUND: '发现',
        IMPORTED: '已入库',
        SKIPPED: '已跳过',
        FAILED: '失败',
      } as Record<string, string>
    )[status || ''] || '-'
  );
}

function itemStatusType(status?: string): TagType {
  return (
    (
      {
        FOUND: 'info',
        IMPORTED: 'success',
        SKIPPED: 'warning',
        FAILED: 'error',
      } as Record<string, TagType>
    )[status || ''] || 'default'
  );
}

onMounted(() => {
  loadSources();
});

onUnmounted(() => {
  closeScanEvents();
});
</script>

<style scoped>
.resource-scan-page {
  padding: 0;
  min-height: 0;
  background: transparent;
}

.resource-scan-panel {
  border-radius: 6px;
  box-shadow: 0 1px 2px rgba(16, 24, 40, 0.06);
}

.mt-3 {
  margin-top: 12px;
}

.scan-form {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 16px;
  margin-bottom: 16px;
}
.scan-form :deep(.n-form-item) { flex: none; margin: 0; }
.scan-form :deep(.n-form-item:first-child) { flex: 1 1 240px; min-width: 200px; }
.scan-form :deep(.n-form-item:nth-child(4)) { width: 200px; }
.scan-form :deep(.n-form-item-label) { white-space: nowrap; }

.source-path-hint {
  margin-top: 6px;
  color: var(--text-color-3);
  font-size: 12px;
  line-height: 1.5;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(92px, 1fr)) minmax(180px, 2fr);
  gap: 10px;
}

.metric-cell {
  min-height: 64px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  padding: 10px 12px;
  background: #fff;
}

.metric-cell span {
  display: block;
  color: #6b7280;
  font-size: 12px;
  line-height: 18px;
}

.metric-cell strong {
  display: block;
  margin-top: 4px;
  color: #111827;
  font-size: 22px;
  font-weight: 600;
  line-height: 28px;
}

.metric-cell .message-text {
  font-size: 13px;
  line-height: 20px;
  font-weight: 500;
  word-break: break-all;
}

:deep(.is-selected-source td) {
  background: #eef6ff !important;
}

@media (max-width: 960px) {
  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .metric-cell.wide {
    grid-column: span 2;
  }
}
</style>
