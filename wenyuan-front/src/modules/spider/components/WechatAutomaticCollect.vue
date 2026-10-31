<template>
  <div class="collect-page">
    <n-space vertical :size="20">
      <n-card title="扫码连接" :bordered="false">
        <template #header-extra><n-button @click="openRecords">采集记录</n-button></template>
        <n-alert type="info" :bordered="false">
          使用公众号管理员或运营者的微信扫码，即可搜索自有及外部公众号。认证过期后重新扫码，采集内容默认进入草稿。
        </n-alert>
        <n-space class="collect-toolbar" align="center">
          <n-select
            v-model:value="connectionId"
            :options="connectionOptions"
            placeholder="选择公众号连接"
            class="collect-select"
            @update:value="resetSource"
          />
          <n-button type="primary" :disabled="qrBusy" @click="authorize()">扫码添加连接</n-button>
          <n-button v-if="selectedConnection?.kind === 'SESSION'" :disabled="qrBusy" @click="authorize(connectionId)"
            >重新扫码</n-button
          >
          <n-tag v-if="selectedConnection" :type="selectedConnection.status === 'CONNECTED' ? 'success' : 'warning'">
            {{ selectedConnection.status === 'CONNECTED' ? '已连接' : '需要重新扫码' }}
          </n-tag>
        </n-space>
        <n-text depth="3"
          >扫码时需使用公众号管理员或运营者的微信，并在手机上确认登录。连接可用于搜索和采集其他公众号。</n-text
        >
      </n-card>

      <n-card title="添加采集" :bordered="false">
        <n-form label-placement="top">
          <n-grid :cols="1" :x-gap="20" responsive="screen" item-responsive>
            <n-form-item-gi span="1 m:1" label="选择公众号">
              <n-space vertical class="full-width">
                <n-input-group>
                  <n-input
                    v-model:value="query"
                    :disabled="!connected || selectedConnection?.kind === 'OFFICIAL'"
                    placeholder="输入公众号名称搜索"
                    @keyup.enter="search"
                  />
                  <n-button :loading="searching" :disabled="!connected" @click="search">{{
                    selectedConnection?.kind === 'OFFICIAL' ? '选择本号' : '搜索'
                  }}</n-button>
                </n-input-group>
                <n-select
                  v-if="sources.length"
                  v-model:value="sourceKey"
                  :options="sourceOptions"
                  placeholder="从搜索结果选择公众号"
                  @update:value="onSourceSelected"
                />
                <n-text v-if="searched && !sources.length" depth="3">未找到公众号，请尝试完整名称。</n-text>
              </n-space>
            </n-form-item-gi>
          </n-grid>
          <n-grid :cols="1" :x-gap="20" responsive="screen" item-responsive>
            <n-form-item-gi span="1" label="采集到栏目"
              ><n-select
                v-model:value="categoryId"
                filterable
                :options="categoryOptions"
                placeholder="选择可管理的文章栏目"
            /></n-form-item-gi>
            <n-form-item-gi span="1" label="计划名称"
              ><n-input v-model:value="name" :maxlength="100" placeholder="例如：行业资讯"
            /></n-form-item-gi>
            <n-form-item-gi span="1" label="执行方式">
              <n-radio-group v-model:value="executionMode">
                <n-radio-button value="once">仅采集一次</n-radio-button>
                <n-radio-button value="scheduled">定时采集</n-radio-button>
              </n-radio-group>
            </n-form-item-gi>
            <n-form-item-gi v-if="executionMode === 'scheduled'" span="1" label="采集频率"
              ><n-select v-model:value="intervalMinutes" :options="intervalOptions"
            /></n-form-item-gi>
            <n-form-item-gi span="1" label="采集后发布">
              <n-space vertical>
                <n-switch v-model:value="autoPublish" :disabled="!canPublish && !autoPublish">
                  <template #checked>正式发布</template>
                  <template #unchecked>草稿（默认）</template>
                </n-switch>
                <n-text depth="3">{{
                  canPublish ? '开启后，新采集文章会直接正式发布。' : '开启正式发布需要文章审核发布权限。'
                }}</n-text>
              </n-space>
            </n-form-item-gi>
          </n-grid>
          <n-space>
            <n-button :loading="previewing" :disabled="!sourceKey" @click="preview">预览最新一篇</n-button>
            <n-button type="primary" :loading="saving" :disabled="!canCreate" @click="createPlan">{{
              executionMode === 'once' ? '立即采集一次' : '保存并开启采集'
            }}</n-button>
          </n-space>
        </n-form>
        <n-text depth="3"
          >每次检查最近 20 组发布记录（最多 100 篇），已采集文章会跳过。{{
            executionMode === 'once' ? '单次采集完成后不会自动重复执行。' : '首次自动执行通常在一分钟内开始。'
          }}</n-text
        >
      </n-card>

      <n-card title="采集计划" :bordered="false">
        <template #header-extra><n-button text :loading="loading" @click="refresh">刷新</n-button></template>
        <n-text depth="3">发布选项变更从后续任务生效，已入队任务及已采集文章保持原设置。</n-text>
        <n-data-table
          :columns="planColumns"
          :data="plans"
          :loading="loading"
          :row-key="(row: CollectPlan) => row.id"
          :scroll-x="1240"
          :pagination="{ pageSize: 10 }"
        />
      </n-card>
    </n-space>

    <n-modal
      :show="qrVisible"
      preset="card"
      title="微信扫码授权"
      style="width: min(92vw, 430px)"
      :mask-closable="false"
      @update:show="closeQr"
    >
      <div class="qr-box">
        <n-spin v-if="qr?.state === 'STARTING'" />
        <img v-if="qr?.image" :src="qr.image" alt="微信公众号登录二维码" class="qr-image" />
        <p role="status">{{ qr?.message }}</p>
        <n-text v-if="qr?.image" depth="3">二维码有效期约 5 分钟，请在手机上完成确认。</n-text>
        <n-button v-if="qr && ['EXPIRED', 'FAILED', 'CANCELLED'].includes(qr.state)" @click="authorize(qrConnectionId)"
          >重新生成二维码</n-button
        >
      </div>
    </n-modal>

    <n-modal v-model:show="previewVisible" preset="card" title="采集预览" style="width: min(94vw, 840px)">
      <h3>{{ previewData?.title }}</h3>
      <n-text depth="3">确认正文正常后即可采集，内容将{{ autoPublish ? '正式发布' : '进入草稿' }}。</n-text>
      <iframe
        v-if="previewData"
        :srcdoc="previewDocument"
        sandbox=""
        referrerpolicy="no-referrer"
        title="文章正文预览"
        class="preview-frame"
      />
    </n-modal>

    <n-modal
      v-model:show="recordsVisible"
      preset="card"
      title="采集记录（最近 50 次）"
      style="width: min(96vw, 1100px)"
    >
      <n-data-table
        :columns="taskColumns"
        :data="records"
        :scroll-x="900"
        :row-key="(row: AsyncTask) => row.id"
        :pagination="{ pageSize: 10 }"
      />
      <n-text depth="3">失败后可在计划中再次采集；已经成功入库的文章不会重复创建。记录按系统任务保留策略清理。</n-text>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, h, onActivated, onBeforeUnmount, onDeactivated, onMounted, ref } from 'vue';
import { NButton, NSelect, NSpace, NSwitch, NTag, useDialog, useMessage } from 'naive-ui';
import type { DataTableColumns } from 'naive-ui';
import * as api from '../api/collect';
import type { CollectPlan, CollectSource, CollectConnection, CollectQr, CollectPreview } from '../api/collect';
import { createCollectQrFlow } from '../utils/collectQrFlow';
import { listAsyncTasks, cancelAsyncTask } from '@/modules/sys/api/asyncTask';
import type { AsyncTask } from '@/modules/sys/api/asyncTask';
import { useAuthStore } from '@/store';
import { collectPreviewDocument } from '../utils/collectPreview';

defineOptions({ name: 'WechatAutomaticCollect' });
const message = useMessage();
const dialog = useDialog();
const auth = useAuthStore();
const canPublish = computed(() => auth.hasPermission(['ARTICLE_ALL', 'ARTICLE_AUDIT']));
const connections = ref<CollectConnection[]>([]);
const categories = ref<{ id: string; name: string }[]>([]);
const plans = ref<CollectPlan[]>([]);
const sources = ref<CollectSource[]>([]);
const connectionId = ref<string>();
const sourceKey = ref<string>();
const categoryId = ref<string>();
const query = ref('');
const name = ref('');
const intervalMinutes = ref(360);
const executionMode = ref<'once' | 'scheduled'>('scheduled');
const autoPublish = ref(false);
const loading = ref(false);
const searching = ref(false);
const searched = ref(false);
const previewing = ref(false);
const saving = ref(false);
const qrVisible = ref(false);
const qr = ref<CollectQr>();
const qrConnectionId = ref<string>();
const previewVisible = ref(false);
const previewData = ref<CollectPreview>();
const recordsVisible = ref(false);
const records = ref<AsyncTask[]>([]);
let disposed = false;
let searchGeneration = 0;
let refreshGeneration = 0;
let refreshTimer: ReturnType<typeof setTimeout> | undefined;
const intervalOptions = [
  { label: '每小时', value: 60 },
  { label: '每 6 小时', value: 360 },
  { label: '每天', value: 1440 },
];
const selectedConnection = computed(() => connections.value.find((c) => c.id === connectionId.value));
const connected = computed(() => selectedConnection.value?.status === 'CONNECTED');
const qrBusy = computed(() => qrVisible.value && ['STARTING', 'WAITING'].includes(qr.value?.state ?? ''));
const connectionOptions = computed(() =>
  connections.value.map((c) => ({ label: `${c.name}${c.status !== 'CONNECTED' ? ' · 需扫码' : ''}`, value: c.id })),
);
const categoryOptions = computed(() => categories.value.map((c) => ({ label: c.name, value: c.id })));
const sourceOptions = computed(() =>
  sources.value.map((c) => ({ label: c.name + (c.alias ? `（${c.alias}）` : ''), value: c.key })),
);
const canCreate = computed(() => connected.value && sourceKey.value && categoryId.value && name.value.trim());
const previewDocument = computed(() => collectPreviewDocument(previewData.value?.content ?? ''));
const errorText = (error: unknown) =>
  (error as { response?: { data?: { message?: string } } })?.response?.data?.message ||
  (error instanceof Error ? error.message : '操作失败，请重试');
const date = (value: number) => new Date(value).toLocaleString('zh-CN', { hour12: false });

async function refresh() {
  const current = ++refreshGeneration;
  loading.value = true;
  try {
    const values = await Promise.all([api.collectConnections(), api.collectCategories(), api.collectPlans()]);
    if (disposed || current !== refreshGeneration) return;
    [connections.value, categories.value, plans.value] = values;
    if (!connectionId.value) connectionId.value = connections.value[0]?.id;
  } finally {
    if (current === refreshGeneration) loading.value = false;
  }
}

const qrFlow = createCollectQrFlow(
  { start: api.startCollectAuthorization, poll: api.pollCollectAuthorization, cancel: api.cancelCollectAuthorization },
  (result) => {
    qr.value = result.qr;
    qrConnectionId.value = result.connectionId;
    if (result.qr.state === 'CONNECTED') {
      qrVisible.value = false;
      connectionId.value = result.connectionId;
      resetSource();
      message.success('授权成功，现在可以搜索公众号');
      void refresh().catch(() => undefined);
    }
  },
  (error) => {
    qr.value = { id: '', state: 'FAILED', expiresAt: 0, message: errorText(error) };
  },
);

function authorize(id?: string) {
  qrConnectionId.value = id;
  qr.value = { id: '', state: 'STARTING', expiresAt: 0, message: '正在生成二维码，请稍候' };
  qrVisible.value = true;
  void qrFlow.start(id);
}
function closeQr() {
  qrVisible.value = false;
  void qrFlow.close();
}
function resetSource() {
  ++searchGeneration;
  searching.value = false;
  sources.value = [];
  sourceKey.value = undefined;
  searched.value = false;
  previewData.value = undefined;
}
function onSourceSelected() {
  if (!name.value) name.value = sources.value.find((s) => s.key === sourceKey.value)?.name ?? '';
}
async function search() {
  if (!connected.value || !connectionId.value || searching.value) return;
  if (selectedConnection.value?.kind !== 'OFFICIAL' && query.value.trim().length < 2) {
    message.info('请输入至少两个字的公众号名称');
    return;
  }
  const current = ++searchGeneration;
  searching.value = true;
  try {
    const result = await api.searchCollectSources(connectionId.value, query.value.trim());
    if (current !== searchGeneration || disposed) return;
    sources.value = result;
    searched.value = true;
    sourceKey.value = undefined;
    if (result.length === 1) {
      sourceKey.value = result[0].key;
      onSourceSelected();
    }
  } finally {
    if (current === searchGeneration) searching.value = false;
  }
}
async function preview() {
  if (!connectionId.value || !sourceKey.value) return;
  previewing.value = true;
  try {
    previewData.value = await api.previewCollectSource(connectionId.value, sourceKey.value);
    previewVisible.value = true;
  } finally {
    previewing.value = false;
  }
}
async function createPlan() {
  if (saving.value || !canCreate.value || !connectionId.value || !sourceKey.value || !categoryId.value) return;
  saving.value = true;
  try {
    const scheduled = executionMode.value === 'scheduled';
    const publish = autoPublish.value;
    const id = await api.createCollectPlan({
      name: name.value.trim(),
      connectionId: connectionId.value,
      sourceKey: sourceKey.value,
      categoryId: categoryId.value,
      intervalMinutes: intervalMinutes.value,
      enabled: scheduled,
      autoPublish: autoPublish.value,
    });
    name.value = '';
    autoPublish.value = false;
    resetSource();
    try {
      if (scheduled) message.success(publish ? '计划已开启，采集文章将正式发布' : '计划已开启，采集文章将进入草稿');
      else {
        const taskId = await api.runCollectPlan(id);
        if (taskId) message.success('已加入单次采集队列，不会定时执行');
        else message.info('该配置已有采集任务，请查看采集记录');
        void openRecords().catch(() => undefined);
      }
    } catch {
      message.warning('配置已保存，请在下方采集计划中重试“立即采集”');
    } finally {
      await refresh();
    }
  } finally {
    saving.value = false;
  }
}
async function configure(row: CollectPlan, enabled: boolean, interval: number, publish?: boolean) {
  await api.configureCollectPlan(row.id, enabled, interval, publish);
  await refresh();
}
async function run(row: CollectPlan) {
  const id = await api.runCollectPlan(row.id);
  if (id) {
    message.success('已加入采集队列');
    await openRecords();
  }
  await refresh();
}
function remove(row: CollectPlan) {
  dialog.warning({
    title: '删除采集计划',
    content: `删除“${row.name}”？已采集文章会保留。`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      await api.deleteCollectPlan(row.id);
      await refresh();
    },
  });
}
const planColumns: DataTableColumns<CollectPlan> = [
  {
    title: '计划 / 公众号',
    key: 'name',
    width: 170,
    render: (row) => h('div', [h('strong', row.name), h('div', row.accountName)]),
  },
  { title: '目标栏目', key: 'categoryName', width: 140 },
  {
    title: '采集后发布',
    key: 'autoPublish',
    width: 140,
    render: (row) =>
      h(
        NSwitch,
        {
          value: row.autoPublish ?? false,
          size: 'small',
          disabled: !canPublish.value && !row.autoPublish,
          'onUpdate:value': (value: boolean) =>
            configure(row, row.enabled, row.intervalMinutes, value).catch(() => undefined),
        },
        { checked: () => '正式发布', unchecked: () => '草稿' },
      ),
  },
  {
    title: '频率',
    key: 'intervalMinutes',
    width: 140,
    render: (row) =>
      h(NSelect, {
        value: row.intervalMinutes,
        options: intervalOptions,
        size: 'small',
        'onUpdate:value': (value: number) => configure(row, row.enabled, value).catch(() => undefined),
      }),
  },
  {
    title: '状态',
    key: 'status',
    width: 220,
    render: (row) =>
      h('div', [
        h(
          NTag,
          { type: row.authorization !== 'CONNECTED' ? 'warning' : row.enabled ? 'success' : 'default', size: 'small' },
          {
            default: () => (row.authorization !== 'CONNECTED' ? '需要重新扫码' : row.enabled ? '定时采集' : '手动采集'),
          },
        ),
        h('div', { class: 'plan-note' }, row.lastError || (row.enabled ? `下次检查：${date(row.nextRunAt)}` : '')),
      ]),
  },
  {
    title: '操作',
    key: 'actions',
    width: 320,
    render: (row) =>
      h(
        NSpace,
        { size: 8 },
        {
          default: () => [
            ...(row.authorization !== 'CONNECTED'
              ? [
                  h(
                    NButton,
                    { size: 'small', type: 'warning', onClick: () => authorize(row.connectionId) },
                    { default: () => '重新扫码' },
                  ),
                ]
              : [
                  h(
                    NButton,
                    { size: 'small', onClick: () => run(row).catch(() => undefined) },
                    { default: () => '立即采集' },
                  ),
                ]),
            h(
              NButton,
              {
                size: 'small',
                onClick: () => configure(row, !row.enabled, row.intervalMinutes).catch(() => undefined),
              },
              { default: () => (row.enabled ? '停止定时' : '开启定时') },
            ),
            h(NButton, { size: 'small', onClick: () => remove(row) }, { default: () => '删除' }),
          ],
        },
      ),
  },
];

async function loadRecords() {
  const current = activation;
  const response = await listAsyncTasks({ taskType: 'CMS_WECHAT_COLLECT', page: 1, pageSize: 50 });
  if (!disposed && current === activation) records.value = response.data?.list ?? response.data?.records ?? [];
}
async function openRecords() {
  recordsVisible.value = true;
  await loadRecords();
}
const statuses: Record<string, string> = {
  CREATED: '等待执行',
  RUNNING: '采集中',
  CANCELLING: '正在取消',
  SUCCESS: '完成',
  FAILED: '失败，可重试',
  CANCELLED: '已取消',
};
const taskColumns: DataTableColumns<AsyncTask> = [
  { title: '开始时间', key: 'createTime', width: 170, render: (row) => date(row.createTime) },
  { title: '状态', key: 'status', width: 130, render: (row) => statuses[row.status] ?? row.status },
  {
    title: '入库 / 跳过 / 失败',
    key: 'counts',
    width: 160,
    render: (row) => `${row.successCount} / ${row.skippedCount} / ${row.failedCount}`,
  },
  { title: '进度与原因', key: 'message', render: (row) => row.errorMessage || row.message || '等待执行' },
  {
    title: '操作',
    key: 'action',
    width: 80,
    render: (row) =>
      ['CREATED', 'RUNNING'].includes(row.status)
        ? h(
            NButton,
            {
              size: 'small',
              onClick: async () => {
                await cancelAsyncTask(row.id);
                await loadRecords();
              },
            },
            { default: () => '取消' },
          )
        : '',
  },
];
let active = false;
let activation = 0;
async function activate() {
  if (active || disposed) return;
  active = true;
  const current = ++activation;
  await refresh().catch(() => undefined);
  const poll = async () => {
    if (!active || current !== activation) return;
    try {
      if (document.visibilityState === 'visible') {
        if (recordsVisible.value) await loadRecords();
        const [currentPlans, currentConnections] = await Promise.all([
          api.collectPlans(true),
          api.collectConnections(true),
        ]);
        if (active && current === activation) {
          plans.value = currentPlans;
          connections.value = currentConnections;
        }
      }
    } catch {
      /* 定时刷新失败时保留现有数据，仍可手动重试。 */
    }
    if (active && current === activation) refreshTimer = setTimeout(poll, recordsVisible.value ? 5000 : 15000);
  };
  if (active && current === activation) refreshTimer = setTimeout(poll, 5000);
}
function deactivate() {
  active = false;
  ++activation;
  ++refreshGeneration;
  ++searchGeneration;
  searching.value = false;
  clearTimeout(refreshTimer);
  closeQr();
}
onMounted(activate);
onActivated(activate);
onDeactivated(deactivate);
onBeforeUnmount(() => {
  disposed = true;
  deactivate();
});
</script>

<style scoped>
.collect-page {
  height: 100%;
  overflow: auto;
}
.collect-toolbar {
  margin: 18px 0 10px;
}
.collect-select {
  width: min(340px, 76vw);
}
.full-width {
  width: 100%;
}
.qr-box {
  min-height: 240px;
  display: flex;
  align-items: center;
  flex-direction: column;
  justify-content: center;
  gap: 12px;
  text-align: center;
}
.qr-image {
  width: 240px;
  height: 240px;
  object-fit: contain;
}
.preview-frame {
  width: 100%;
  height: 60vh;
  border: 1px solid #ddd;
  margin-top: 16px;
}
:deep(.plan-note) {
  font-size: 12px;
  color: #767676;
  margin-top: 6px;
  overflow-wrap: anywhere;
}
</style>
