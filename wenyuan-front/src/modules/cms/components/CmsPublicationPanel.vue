<template>
  <n-card v-if="!definition || definition.capabilities.includes('PUBLISH')" title="发布管理与版本历史" size="small" class="publication-panel">
    <n-alert v-if="error" type="error" :show-icon="false" class="publication-actions">{{ error }}</n-alert>
    <n-spin :show="busy">
      <n-space align="center" justify="space-between">
        <n-space align="center"><n-tag>{{ states[info.state] || '历史内容，发布时间未记录' }}</n-tag><span>实际发布：{{ formatTime(info.publishedAt) }}</span></n-space>
        <n-button v-if="definition?.capabilities.includes('VERSION')" size="small" :disabled="busy" @click="showVersions">版本历史</n-button>
      </n-space>
      <n-text v-if="!disabled" depth="3">发布操作针对已保存的内容；修改正文后请先保存。</n-text>
      <n-space v-if="!disabled" class="publication-actions" align="center">
        <n-button v-if="canEdit && info.state !== 'TRASH'" size="small" :disabled="busy" @click="apply('DRAFT')">存为草稿</n-button>
        <n-button v-if="canEdit && info.state !== 'TRASH'" size="small" :disabled="busy" @click="apply('AUDIT')">提交审核</n-button>
        <n-button v-if="canAudit && info.state !== 'TRASH'" size="small" type="primary" :disabled="busy" @click="apply('PUBLISHED')">发布</n-button>
        <n-button v-if="canEdit && info.state !== 'TRASH'" size="small" :disabled="busy" @click="apply('OFFLINE')">下线</n-button>
        <n-popconfirm v-if="canDelete && info.state !== 'TRASH'" @positive-click="apply('TRASH')">
          <template #trigger><n-button size="small" type="error" :disabled="busy">移入回收站</n-button></template>保留内容及附件，之后可以恢复为草稿。
        </n-popconfirm>
        <n-button v-if="canDelete && canEdit && info.state === 'TRASH'" size="small" :disabled="busy" @click="apply('DRAFT')">恢复为草稿</n-button>
      </n-space>
      <n-space v-if="!disabled && canAudit && info.state !== 'TRASH'" class="publication-actions" align="center">
        <n-date-picker :disabled="busy" v-model:value="publishAt" type="datetime" clearable placeholder="定时发布时间" />
        <n-date-picker :disabled="busy" v-model:value="offlineAt" type="datetime" clearable placeholder="可选下线时间" />
        <n-button size="small" :disabled="busy || !publishAt" @click="apply('SCHEDULED')">设置定时发布</n-button>
      </n-space>
      <n-text v-if="info.publishAt" depth="3">计划发布：{{ formatTime(info.publishAt) }}；计划下线：{{ formatTime(info.offlineAt) }}</n-text>
    </n-spin>
    <n-modal v-model:show="historyOpen" preset="card" title="内容版本" style="width: min(960px, 95vw)">
      <n-data-table :columns="versionColumns" :data="history" :loading="historyLoading" :row-key="row => row.id" />
      <n-pagination :disabled="saving" v-model:page="historyPage" :page-size="20" :item-count="historyTotal" @update:page="loadVersions" class="publication-actions" />
      <n-card v-if="snapshot" title="版本内容" size="small" class="publication-actions">
        <n-descriptions :column="2" label-placement="left" bordered>
          <n-descriptions-item v-for="(value, key) in snapshot" :key="key" :label="fieldLabels[key] || String(key)">
            <pre class="snapshot-value">{{ value ?? '—' }}</pre>
          </n-descriptions-item>
        </n-descriptions>
      </n-card>
    </n-modal>
  </n-card>
</template>
<script setup lang="ts">
import { computed, h, onBeforeUnmount, ref, watch } from 'vue';
import { NButton, NPopconfirm, type DataTableColumns } from 'naive-ui';
import dayjs from 'dayjs';
import { useAuthStore } from '@/store';
import { cmsPlugins, type CmsPluginDefinition } from '../api/plugins';
import { publication, changePublication, versions, version, restoreVersion, type StatRow } from '../api/statistics';
const props = defineProps<{ module: string; contentId: string; disabled?: boolean }>();
const emit = defineEmits<{ changed: [state: string] }>();
const auth = useAuthStore();
const definition = ref<CmsPluginDefinition>();
// 权限前缀和发布/版本能力由插件定义提供，避免 FEEDBACK 等特殊前缀写成类型分支。
const permission = computed(() => definition.value?.permissionPrefix || '');
const canEdit = computed(() => auth.hasPermissionByCode('EDIT', permission.value));
const canAudit = computed(() => auth.hasPermissionByCode('AUDIT', permission.value));
const canDelete = computed(() => auth.hasPermissionByCode('DELETE', permission.value));
const states: Record<string, string> = { DRAFT: '草稿', AUDIT: '待审核', PUBLISHED: '已发布', SCHEDULED: '定时发布', OFFLINE: '已下线', TRASH: '回收站' };
const fieldLabels: Record<string, string> = { title: '标题', content: '正文', image: '封面', description: '摘要', author: '作者', keywords: '关键字', category_id: '栏目', copy_from: '来源', status: '原状态', create_time: '内容日期', update_time: '修改时间' };
const saving = ref(false); const snapshotLoading = ref(false);
const busy = computed(() => loading.value || saving.value);
const error = ref(''); const info = ref<StatRow>({}); const loading = ref(false); const publishAt = ref<number | null>(null); const offlineAt = ref<number | null>(null);
const historyOpen = ref(false); const historyLoading = ref(false); const history = ref<StatRow[]>([]);
const historyPage = ref(1); const historyTotal = ref(0); const snapshot = ref<StatRow | null>(null);
function formatTime(value: number | null | undefined) { return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '未记录'; }
// 每条异步链捕获目标内容和代次，旧请求不能回写状态、清除新请求的loading或触发当前表单重载。
let generation = 0;
const sequences = { info: 0, history: 0, snapshot: 0 };
function begin(channel?: keyof typeof sequences) {
  const current = generation; const module = props.module; const contentId = props.contentId;
  const sequence = channel ? ++sequences[channel] : 0;
  return { module, contentId, current: () => current === generation && module === props.module && contentId === props.contentId && (!channel || sequence === sequences[channel]) };
}
async function load() {
  const request = begin('info');
  if (!request.contentId) return;
  loading.value = true; error.value = '';
  try {
    const definitions = await cmsPlugins();
    if (!request.current()) return;
    definition.value = definitions.find(item => item.code === request.module.toUpperCase());
    if (!definition.value?.capabilities.includes('PUBLISH')) return;
    const response = await publication(request.module, request.contentId);
    if (request.current()) { info.value = response.data; publishAt.value = info.value.publishAt ?? null; offlineAt.value = info.value.offlineAt ?? null; }
  } catch { if (request.current()) error.value = '发布信息加载失败或无内容查看权限'; }
  finally { if (request.current()) loading.value = false; }
}
/** 目标在点击时固定；跨内容切换的成功或失败不影响新表单，重复操作在保存期间禁用。 */
async function mutate(action: (module: string, contentId: string) => Promise<unknown>) {
  if (busy.value || !props.contentId) return;
  const request = begin(); saving.value = true; error.value = '';
  try {
    await action(request.module, request.contentId);
    if (!request.current()) return;
    snapshot.value = null; ++sequences.snapshot;
    await load();
    if (!request.current()) return;
    if (historyOpen.value) await loadVersions();
    if (request.current()) emit('changed', info.value.state);
  } catch { if (request.current()) error.value = '操作失败，请核对权限和发布计划'; }
  finally { if (request.current()) saving.value = false; }
}
function apply(state: string) {
  const data = { state, publishAt: state === 'SCHEDULED' ? publishAt.value : null, offlineAt: ['SCHEDULED', 'PUBLISHED'].includes(state) ? offlineAt.value : null };
  return mutate((module, id) => changePublication(module, id, data));
}
async function loadVersions() {
  const request = begin('history'); const page = historyPage.value;
  ++sequences.snapshot; snapshot.value = null; snapshotLoading.value = false; history.value = [];
  if (!request.contentId) return;
  historyLoading.value = true;
  try { const response = await versions(request.module, request.contentId, page); if (request.current()) { history.value = response.data.list; historyTotal.value = response.data.total; } }
  catch { if (request.current()) error.value = '版本历史加载失败'; }
  finally { if (request.current()) historyLoading.value = false; }
}
async function showVersions() { if (busy.value) return; historyOpen.value = true; historyPage.value = 1; await loadVersions(); }
async function showSnapshot(row: StatRow) {
  const request = begin('snapshot'); snapshotLoading.value = true; snapshot.value = null;
  try { const response = await version(request.module, request.contentId, row.id); if (request.current()) snapshot.value = response.data; }
  catch { if (request.current()) error.value = '版本内容加载失败'; }
  finally { if (request.current()) snapshotLoading.value = false; }
}
const versionColumns: DataTableColumns<StatRow> = [
  { key: 'createdAt', title: '记录时间', render: row => formatTime(row.createdAt) },
  { key: 'action', title: '操作', render: row => states[row.action] || ({ CREATE: '创建', EDIT: '编辑', BASELINE: '历史基线', RESTORE_VERSION: '版本恢复', AUTO_PUBLISH: '定时发布', AUTO_OFFLINE: '定时下线' } as Record<string, string>)[row.action] || row.action },
  { key: 'actorId', title: '操作人', render: row => row.actorId || '系统' },
  { key: 'operate', title: '操作', render: row => h('div', { style: 'display:flex;gap:8px' }, [
    h(NButton, { size: 'small', disabled: busy.value || snapshotLoading.value, onClick: () => showSnapshot(row) }, () => '查看'),
    // 恢复版本后重新加载发布与历史，并通知业务表单重读正文，避免继续保存恢复前的旧字段。
    !props.disabled && canEdit.value && (info.value.state !== 'TRASH' || canDelete.value) ? h(NPopconfirm, { onPositiveClick: () => mutate((module, id) => restoreVersion(module, id, row.id)) }, {
      trigger: () => h(NButton, { size: 'small', disabled: busy.value }, () => '恢复'), default: () => '恢复该版本并转为草稿，审核后再发布。',
    }) : null,
  ]) },
];
watch(() => [props.module, props.contentId], () => {
  ++generation; definition.value = undefined; info.value = {}; error.value = ''; loading.value = false; saving.value = false;
  historyOpen.value = false; historyLoading.value = false; history.value = []; historyTotal.value = 0; historyPage.value = 1;
  snapshot.value = null; snapshotLoading.value = false; publishAt.value = null; offlineAt.value = null;
  void load();
}, { immediate: true, flush: 'sync' });
watch(historyOpen, open => { if (!open) { ++sequences.history; ++sequences.snapshot; historyLoading.value = false; snapshotLoading.value = false; } });
onBeforeUnmount(() => { ++generation; });
</script>
<style scoped>
.publication-panel { margin: 12px 0; } .publication-actions { margin-top: 12px; }
.snapshot-value { white-space: pre-wrap; overflow-wrap: anywhere; max-height: 300px; overflow: auto; margin: 0; font: inherit; }
</style>
