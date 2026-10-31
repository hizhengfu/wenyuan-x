<template>
  <SafetyPageGuard>
    <div class="workbench-page" :style="workbenchTheme">
      <header class="headline">
        <div class="headline-title">
          <span class="headline-icon" aria-hidden="true"><NIcon :component="ShieldCheckmarkOutline" /></span>
          <div>
            <h2>场馆安全工作台</h2>
            <div class="headline-meta">
              <span>值班巡检 · 风险处置 · 设备监测</span>
              <span v-if="data" class="generated-at">更新于 {{ formatDateTime(data.generatedAt) }}</span>
            </div>
          </div>
        </div>
        <div class="headline-actions">
          <VenueSelect v-model:value="orgId" class="venue-selector" placeholder="全部场馆" @update:value="load" />
          <NButton :loading="loading" @click="load">
            <template #icon><NIcon :component="RefreshOutline" /></template>刷新
          </NButton>
        </div>
      </header>
      <NAlert v-if="error" type="error" closable class="state-alert" @close="error = ''">{{ error }}</NAlert>
      <NSpin :show="loading">
        <div class="indicator-grid">
          <button
            v-for="item in indicators"
            :key="item.label"
            type="button"
            class="indicator"
            :class="item.tone"
            @click="router.push(item.path)"
          >
            <div class="indicator-top">
              <span class="indicator-label">{{ item.label }}</span>
              <NIcon :component="item.icon" class="indicator-icon" aria-hidden="true" />
            </div>
            <div class="indicator-value">{{ data ? item.value : '—' }}</div>
            <div class="indicator-bottom">
              <span class="indicator-note">{{ item.note }}</span>
              <NIcon :component="ChevronForwardOutline" aria-hidden="true" />
            </div>
          </button>
        </div>

        <div class="workspace-columns">
          <div class="workspace-main">
            <NCard title="最新安全告警" :bordered="false" class="workbench-panel">
              <template #header-extra>
                <NButton text type="primary" @click="router.push('/museum-safety/risk')">查看全部</NButton>
              </template>
              <NDataTable
                :columns="riskColumns"
                :data="data?.latestRisks || []"
                :row-key="(row) => row.id"
                :pagination="false"
                :bordered="false"
                :scroll-x="620"
                size="small"
              />
            </NCard>
            <NCard title="最新巡检记录" :bordered="false" class="workbench-panel">
              <template #header-extra>
                <NButton text type="primary" @click="router.push('/museum-safety/operations')">进入值班巡检</NButton>
              </template>
              <NDataTable
                :columns="inspectionColumns"
                :data="data?.latestInspections || []"
                :row-key="(row) => row.id"
                :pagination="false"
                :bordered="false"
                :scroll-x="620"
                size="small"
              />
            </NCard>
          </div>
          <div class="workspace-side">
            <NCard title="设备与实时监测" :bordered="false" class="workbench-panel monitoring-panel">
              <div class="device-list">
                <button
                  v-for="device in deviceCards"
                  :key="device.equipmentClass"
                  type="button"
                  class="device-card"
                  @click="
                    router.push({ path: '/museum-safety/equipment', query: { equipmentClass: device.equipmentClass } })
                  "
                >
                  <div class="device-title">
                    <span>{{ equipmentLabel(device.equipmentClass) }}</span>
                    <NTag
                      size="small"
                      :type="!device.total ? 'default' : device.offline ? 'warning' : 'success'"
                      :bordered="false"
                    >
                      {{ !device.total ? '未接入' : device.offline ? '存在离线' : '运行正常' }}
                    </NTag>
                  </div>
                  <div class="device-states">
                    <span class="device-total">{{ device.total }}<small> 台</small></span>
                    <span
                      >在线 <strong>{{ device.online }}</strong></span
                    >
                    <span :class="{ offline: device.offline > 0 }"
                      >离线 <strong>{{ device.offline }}</strong></span
                    >
                    <NIcon :component="ChevronForwardOutline" class="device-arrow" aria-hidden="true" />
                  </div>
                </button>
              </div>
              <div class="monitor-links">
                <button
                  v-for="entry in monitorEntries"
                  :key="entry.path"
                  type="button"
                  @click="router.push({ path: entry.path, query: orgId ? { orgId } : {} })"
                >
                  <NIcon :component="entry.icon" class="monitor-icon" aria-hidden="true" />
                  <span class="monitor-copy"
                    ><span>{{ entry.title }}</span
                    ><small>{{ entry.description }}</small></span
                  >
                  <NIcon :component="ChevronForwardOutline" class="monitor-arrow" aria-hidden="true" />
                </button>
              </div>
            </NCard>
            <NCard title="通知公告" :bordered="false" class="workbench-panel">
              <template #header-extra>
                <NButton v-if="canGovern" text type="primary" @click="openNotice">发布公告</NButton>
              </template>
              <NDataTable
                :columns="visibleNoticeColumns"
                :data="data?.latestNotices || []"
                :row-key="(row) => row.id"
                :pagination="false"
                :bordered="false"
                :scroll-x="canGovern ? 520 : 450"
                size="small"
              />
            </NCard>
          </div>
        </div>

        <NCard :bordered="false" class="workbench-panel video-panel">
          <template #header>
            <span class="video-title"><NIcon :component="VideocamOutline" aria-hidden="true" />核心位置监控画面</span>
          </template>
          <template #header-extra>
            <NSelect
              :value="selectedCameraIds"
              :options="videoCameraOptions"
              multiple
              filterable
              clearable
              :max-tag-count="'responsive'"
              placeholder="选择要预览的摄像机（最多 9 个）"
              class="camera-selector"
              :disabled="!orgId || videoLoading"
              @update:value="updateCameraSelection"
            />
          </template>
          <NAlert v-if="!orgId" type="info" :bordered="false">请选择一个场馆后查看关联的核心位置画面。</NAlert>
          <NSpin v-else :show="videoLoading">
            <NAlert v-if="videoError" type="error" :bordered="false" class="state-alert">{{ videoError }}</NAlert>
            <NEmpty v-if="!videoLoading && !videoCameras.length" description="该场馆尚未关联核心位置摄像机" />
            <NEmpty v-else-if="!videoLoading && !selectedCameraIds.length" description="请选择至少一个摄像机" />
            <div v-else class="video-monitor-frame">
              <HkPlayer
                ref="videoPlayer"
                :video-code-list="videoCodeList"
                :split="videoSplit"
                :fun-preview="fetchVideoPreview"
                :stream-type="1"
              />
            </div>
          </NSpin>
        </NCard>
      </NSpin>

      <RiskEventHandling v-model:show="riskDetailVisible" :id="riskDetailId" @saved="load" />

      <HPageForm
        v-model:show="noticeVisible"
        title="发布安全通知公告"
        :model="noticeForm"
        :auto-close="false"
        :handle-submit="saveNotice"
        label-placement="left"
        label-width="90"
        submit-text="发布"
      >
        <NAlert v-if="error" type="error" closable class="state-alert" @close="error = ''">{{ error }}</NAlert>
        <NFormItem label="所属场馆" required
          ><VenueSelect v-model:value="noticeForm.orgId" placeholder="请选择场馆"
        /></NFormItem>
        <NFormItem label="公告级别"
          ><NSelect v-model:value="noticeForm.level" :options="noticeLevelOptions"
        /></NFormItem>
        <NFormItem label="公告标题" required><NInput v-model:value="noticeForm.title" /></NFormItem>
        <NFormItem label="公告内容" required><NInput v-model:value="noticeForm.content" type="textarea" /></NFormItem>
        <NFormItem label="发布时间"><NDatePicker v-model:value="noticeForm.publishAt" type="datetime" /></NFormItem>
        <NFormItem label="失效时间"
          ><NDatePicker v-model:value="noticeForm.expiresAt" type="datetime" clearable
        /></NFormItem>
        <NFormItem label="公告附件"><SafetyAttachmentUpload v-model="noticeForm.attachments" /></NFormItem>
      </HPageForm>
    </div>
  </SafetyPageGuard>
</template>

<script setup lang="ts">
import { computed, h, onActivated, onBeforeUnmount, onDeactivated, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import {
  NAlert,
  NButton,
  NCard,
  NDataTable,
  NIcon,
  NDatePicker,
  NEmpty,
  NFormItem,
  NInput,
  NPopconfirm,
  NSelect,
  NSpin,
  NTag,
  useThemeVars,
  type DataTableColumns,
} from 'naive-ui';
import {
  ChevronForwardOutline,
  ClipboardOutline,
  ConstructOutline,
  FlashOutline,
  FlameOutline,
  HomeOutline,
  PeopleOutline,
  PulseOutline,
  RefreshOutline,
  ShieldCheckmarkOutline,
  VideocamOutline,
} from '@vicons/ionicons5';
import { formatDateTime } from '@/utils';
import request from '@/utils/request';
import { useAuthStore } from '@/store/modules/auth';
import HkPlayer from '@/components/basic/hkPlayer/HKPlayer.vue';
import VenueSelect from '../../museum-device/components/VenueSelect.vue';
import { safetyApi, unwrapResponse } from '../api';
import type {
  SafetyEquipmentClass,
  SafetyInspectionRecordVo,
  SafetyManagementWorkbenchVo,
  SafetyNoticeLevel,
  SafetyNoticeVo,
  SafetyVideoCameraVo,
  SafetyWorkbenchRiskVo,
} from '../typings';
import SafetyAttachmentUpload from '../components/SafetyAttachmentUpload.vue';
import SafetyPageGuard from '../components/SafetyPageGuard.vue';
import RiskEventHandling from '../components/RiskEventHandling.vue';

const riskDetailVisible = ref(false);
const riskDetailId = ref('');

function openRiskDetail(id: string) {
  riskDetailId.value = id;
  riskDetailVisible.value = true;
}

const themeVars = useThemeVars();
const workbenchTheme = computed(() => ({
  '--body-color': themeVars.value.tableHeaderColor,
  '--card-color': themeVars.value.cardColor,
  '--border-color': themeVars.value.borderColor,
  '--primary-color': themeVars.value.primaryColor,
  '--text-color-1': themeVars.value.textColor1,
  '--text-color-2': themeVars.value.textColor2,
  '--text-color-3': themeVars.value.textColor3,
  '--warning-color': themeVars.value.warningColor,
  '--error-color': themeVars.value.errorColor,
}));
const router = useRouter();
const authStore = useAuthStore();
const canGovern = computed(() => authStore.hasPermission('ADMIN,MUSEUM_SAFETY_GOVERNANCE'));
const orgId = ref<string | null>(null);
const data = ref<SafetyManagementWorkbenchVo | null>(null);
const loading = ref(false);
const error = ref('');
const noticeVisible = ref(false);
const videoLoading = ref(false);
const videoError = ref('');
const videoCameras = ref<SafetyVideoCameraVo[]>([]);
const selectedCameraIds = ref<string[]>([]);
let timer: number | null = null;
let active = true;

const videoCameraOptions = computed(() =>
  videoCameras.value.map((camera) => ({
    label: `${camera.name}${camera.online === false ? '（离线）' : ''}`,
    value: camera.id,
  })),
);
const videoCodeList = computed(() =>
  selectedCameraIds.value
    .map((id) => videoCameras.value.find((camera) => camera.id === id))
    .filter((camera): camera is SafetyVideoCameraVo => !!camera)
    .map((camera) => ({ code: camera.code, serverId: camera.serverId })),
);
const videoSplit = computed(() => {
  if (videoCodeList.value.length <= 1) return '1x1';
  if (videoCodeList.value.length <= 4) return '2x2';
  return '3x3';
});

const indicators = computed(() => [
  {
    label: '今日值班到岗',
    icon: PeopleOutline,
    value: `${data.value?.arrivedDutyCount || 0}/${data.value?.scheduledDutyCount || 0}`,
    note: '已到岗 / 应到岗',
    path: '/museum-safety/operations',
    tone: '',
  },
  {
    label: '待处置巡检问题',
    icon: ClipboardOutline,
    value: data.value?.openIssueCount || 0,
    note: `其中逾期 ${data.value?.overdueIssueCount || 0} 项`,
    path: '/museum-safety/operations',
    tone: data.value?.overdueIssueCount ? 'danger' : '',
  },
  {
    label: '逾期维护计划',
    icon: ConstructOutline,
    value: data.value?.overdueMaintenanceCount || 0,
    note: '需尽快安排维护任务',
    path: '/museum-safety/maintenance',
    tone: data.value?.overdueMaintenanceCount ? 'warning' : '',
  },
  {
    label: '当前安全风险',
    icon: ShieldCheckmarkOutline,
    value: data.value?.activeRiskCount || 0,
    note: `已生效制度 ${data.value?.effectiveDocumentCount || 0} 项`,
    path: '/museum-safety/risk',
    tone: data.value?.activeRiskCount ? 'danger' : '',
  },
]);
const deviceCards = computed(() => data.value?.deviceCounts || []);
const monitorEntries = [
  {
    title: '建筑结构监测',
    icon: HomeOutline,
    description: '结构变形、裂缝及环境数据',
    path: '/museum-safety/building',
  },
  {
    title: '防雷系统监测',
    icon: FlashOutline,
    description: '防雷系统与监测点运行态势',
    path: '/museum-safety/lightning',
  },
  {
    title: '消防安防监测',
    icon: FlameOutline,
    description: '消防与安防采集告警',
    path: '/museum-safety/fire-security',
  },
  {
    title: '用电环境监测',
    icon: PulseOutline,
    description: '电气安全与建筑环境',
    path: '/museum-safety/electric-environment',
  },
];
const inspectionColumns: DataTableColumns<SafetyInspectionRecordVo> = [
  {
    title: '巡检时间',
    key: 'inspectedAt',
    width: 175,
    ellipsis: { tooltip: true },
    render: (row) => formatDateTime(row.inspectedAt),
  },
  { title: '巡检人员', key: 'inspectorId', width: 125, ellipsis: { tooltip: true } },
  { title: '巡检点', key: 'pointCode', minWidth: 150, ellipsis: { tooltip: true } },
  {
    title: '数据来源',
    key: 'source',
    width: 120,
    render: (row) => (row.source === 'MANUAL' ? '人工' : row.source === 'INGEST_REFERENCE' ? '采集引用' : '混合'),
  },
  {
    title: '结果',
    key: 'result',
    width: 100,
    render: (row) =>
      h(
        NTag,
        { type: row.result === 'NORMAL' ? 'success' : 'error', bordered: false },
        { default: () => (row.result === 'NORMAL' ? '正常' : '异常') },
      ),
  },
];
const riskColumns: DataTableColumns<SafetyWorkbenchRiskVo> = [
  {
    title: '时间',
    key: 'lastOccurredAt',
    width: 175,
    ellipsis: { tooltip: true },
    render: (row) => formatDateTime(row.lastOccurredAt),
  },
  {
    title: '级别',
    key: 'level',
    width: 76,
    render: (row) =>
      h(
        NTag,
        {
          size: 'small',
          bordered: false,
          type:
            row.level === 'CRITICAL' || row.level === 'MAJOR'
              ? 'error'
              : row.level === 'WARNING'
                ? 'warning'
                : 'default',
        },
        {
          default: () => ({ NOTICE: '提示', WARNING: '警告', MAJOR: '重大', CRITICAL: '严重' })[row.level] || row.level,
        },
      ),
  },
  { title: '告警', key: 'title', minWidth: 220, ellipsis: { tooltip: true } },
  {
    title: '操作',
    key: 'actions',
    width: 90,
    fixed: 'right',
    render: (row) =>
      h(NButton, { text: true, type: 'primary', onClick: () => openRiskDetail(row.id) }, { default: () => '详情' }),
  },
];
const noticeColumns: DataTableColumns<SafetyNoticeVo> = [
  {
    title: '发布时间',
    key: 'publishAt',
    width: 175,
    ellipsis: { tooltip: true },
    render: (row) => formatDateTime(row.publishAt),
  },
  {
    title: '级别',
    key: 'level',
    width: 90,
    render: (row) =>
      h(
        NTag,
        { type: row.level === 'URGENT' ? 'error' : row.level === 'IMPORTANT' ? 'warning' : 'info', bordered: false },
        { default: () => noticeLevelLabel(row.level) },
      ),
  },
  { title: '标题', key: 'title', minWidth: 180, ellipsis: { tooltip: true } },
  {
    title: '操作',
    key: 'actions',
    width: 70,
    render: (row) =>
      canGovern.value
        ? h(
            NPopconfirm,
            { onPositiveClick: () => archiveNotice(row) },
            {
              trigger: () => h(NButton, { text: true, type: 'error' }, { default: () => '归档' }),
              default: () => '确认归档该公告？',
            },
          )
        : '--',
  },
];
const visibleNoticeColumns = computed(() =>
  canGovern.value ? noticeColumns : noticeColumns.filter((column) => !('key' in column) || column.key !== 'actions'),
);
const noticeLevelOptions = [
  { label: '普通', value: 'NORMAL' },
  { label: '重要', value: 'IMPORTANT' },
  { label: '紧急', value: 'URGENT' },
];
const noticeForm = reactive({
  orgId: null as string | null,
  title: '',
  content: '',
  level: 'NORMAL' as SafetyNoticeLevel,
  publishAt: Date.now(),
  expiresAt: null as number | null,
  attachments: [] as string[],
});
function equipmentLabel(value: SafetyEquipmentClass) {
  return value === 'SECURITY' ? '安防设备' : value === 'FIRE' ? '消防设备' : '防雷设备';
}
function noticeLevelLabel(value: SafetyNoticeLevel) {
  return noticeLevelOptions.find((item) => item.value === value)?.label || value;
}
function fetchVideoPreview(params: object) {
  return request({ url: '/video/hik/preview', method: 'get', params });
}
function updateCameraSelection(value: string[]) {
  selectedCameraIds.value = value.slice(0, 9);
}
async function loadVideo() {
  const targetOrgId = orgId.value;
  if (!targetOrgId) {
    videoCameras.value = [];
    selectedCameraIds.value = [];
    videoError.value = '';
    return;
  }
  videoLoading.value = true;
  videoError.value = '';
  try {
    const values = unwrapResponse<SafetyVideoCameraVo[]>(await safetyApi.videoCameras({ orgId: targetOrgId })) || [];
    if (orgId.value !== targetOrgId) return;
    videoCameras.value = values;
    const availableIds = new Set(values.map((camera) => camera.id));
    const retained = selectedCameraIds.value.filter((id) => availableIds.has(id));
    const preferred = values.filter((camera) => camera.online !== false);
    selectedCameraIds.value = retained.length
      ? retained
      : (preferred.length ? preferred : values).slice(0, 4).map((item) => item.id);
  } catch (cause: any) {
    if (orgId.value === targetOrgId) {
      videoCameras.value = [];
      selectedCameraIds.value = [];
      videoError.value = cause?.message || '核心位置监控目录加载失败。';
    }
  } finally {
    if (orgId.value === targetOrgId) videoLoading.value = false;
  }
}
function openNotice() {
  Object.assign(noticeForm, {
    orgId: orgId.value,
    title: '',
    content: '',
    level: 'NORMAL',
    publishAt: Date.now(),
    expiresAt: null,
    attachments: [],
  });
  noticeVisible.value = true;
}
async function saveNotice() {
  if (!noticeForm.orgId || !noticeForm.title.trim() || !noticeForm.content.trim()) {
    error.value = '请完整填写场馆、标题和公告内容。';
    return;
  }
  try {
    await safetyApi.createManagementNotice(noticeForm);
    noticeVisible.value = false;
    await load();
  } catch (cause: any) {
    error.value = cause?.message || '公告发布失败。';
  }
}
async function archiveNotice(row: SafetyNoticeVo) {
  try {
    await safetyApi.archiveManagementNotice(row.id);
    await load();
  } catch (cause: any) {
    error.value = cause?.message || '公告归档失败。';
  }
}
function schedule() {
  if (timer != null) window.clearTimeout(timer);
  if (active) timer = window.setTimeout(load, 60_000);
}
async function load() {
  void loadVideo();
  loading.value = true;
  error.value = '';
  try {
    data.value = unwrapResponse<SafetyManagementWorkbenchVo>(
      await safetyApi.managementWorkbench({ orgId: orgId.value || undefined }),
    );
  } catch (cause: any) {
    error.value = cause?.message || '安全工作台加载失败。';
  } finally {
    loading.value = false;
    schedule();
  }
}
onMounted(load);
onActivated(() => {
  active = true;
  void load();
});
onDeactivated(() => {
  active = false;
  if (timer != null) window.clearTimeout(timer);
});
onBeforeUnmount(() => {
  active = false;
  if (timer != null) window.clearTimeout(timer);
});
</script>

<style scoped>
.workbench-page {
  width: 100%;
  min-width: 0;
  min-height: 100%;
  padding: 20px;
  box-sizing: border-box;
  overflow: auto;
  background: var(--body-color);
  container-type: inline-size;
}
.headline {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 16px;
  margin-bottom: 20px;
}
.headline-title {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}
.headline-icon {
  display: grid;
  place-items: center;
  flex: 0 0 44px;
  height: 44px;
  border-radius: 12px;
  color: var(--primary-color);
  background: var(--card-color);
  border: 1px solid var(--border-color);
  font-size: 24px;
}
.headline h2 {
  margin: 0 0 6px;
  font-size: 22px;
  font-weight: 650;
  line-height: 1.3;
}
.headline-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 18px;
  font-size: 12px;
  color: var(--text-color-3);
}
.headline-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}
.venue-selector {
  width: 220px;
}
.state-alert {
  margin-bottom: 12px;
}
.indicator-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}
.indicator,
.device-card,
.monitor-links button {
  box-sizing: border-box;
  min-width: 0;
  color: var(--text-color-1);
  font: inherit;
  text-align: left;
  background: var(--card-color);
  border: 1px solid var(--border-color);
  cursor: pointer;
  transition:
    border-color 0.16s ease,
    background-color 0.16s ease;
}
.indicator:hover,
.device-card:hover,
.monitor-links button:hover {
  border-color: var(--primary-color);
}
.indicator:focus-visible,
.device-card:focus-visible,
.monitor-links button:focus-visible {
  outline: 2px solid var(--primary-color);
  outline-offset: 3px;
}
.indicator {
  padding: 16px 18px;
  border-radius: 12px;
}
.indicator-top,
.indicator-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.indicator-label {
  color: var(--text-color-2);
  font-size: 13px;
}
.indicator-icon {
  color: var(--text-color-3);
  font-size: 20px;
}
.indicator-value {
  margin: 8px 0;
  font-size: 30px;
  font-weight: 650;
  line-height: 1.2;
  font-variant-numeric: tabular-nums;
}
.indicator.warning .indicator-value {
  color: var(--warning-color, #d97706);
}
.indicator.danger .indicator-value {
  color: var(--error-color, #dc2626);
}
.indicator-note,
.indicator-bottom > .n-icon {
  color: var(--text-color-3);
  font-size: 12px;
}
.workspace-columns {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(0, 1fr);
  gap: 16px;
  margin-top: 16px;
  align-items: start;
}
.workspace-main,
.workspace-side {
  display: grid;
  gap: 16px;
  min-width: 0;
}
.workbench-panel {
  min-width: 0;
  border: 1px solid var(--border-color);
  border-radius: 12px;
}
.workbench-panel :deep(.n-card-header) {
  padding: 16px 18px 14px;
}
.workbench-panel :deep(.n-card-header__main) {
  font-size: 15px;
  font-weight: 600;
}
.workbench-panel :deep(.n-card__content) {
  padding: 0 18px 18px;
}
.workbench-panel :deep(.n-data-table-empty) {
  padding: 24px 0;
}
.workbench-panel :deep(.n-data-table-th) {
  font-size: 12px;
}
.device-list {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}
.device-card {
  padding: 10px 12px;
  border-radius: 8px;
}
.device-title {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  font-size: 13px;
  font-weight: 500;
}
.device-states {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px 12px;
  margin-top: 6px;
  font-size: 12px;
  color: var(--text-color-3);
}
.device-states strong {
  font-weight: 500;
  color: var(--text-color-2);
}
.device-total {
  flex-basis: 100%;
  min-width: 40px;
  font-size: 22px;
  font-weight: 600;
  line-height: 1.2;
  color: var(--text-color-1);
  font-variant-numeric: tabular-nums;
}
.device-total small {
  font-size: 12px;
  font-weight: 400;
  color: var(--text-color-3);
}
.device-states .offline,
.device-states .offline strong {
  color: var(--warning-color, #d97706);
}
.device-arrow {
  display: none;
  margin-left: auto;
}
.monitor-links {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  padding-top: 14px;
  margin-top: 14px;
  border-top: 1px solid var(--border-color);
}
.monitor-links button {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 12px 10px;
  border-radius: 8px;
}
.monitor-icon {
  flex: 0 0 18px;
  margin-top: 2px;
  font-size: 18px;
  color: var(--primary-color);
}
.monitor-copy {
  display: flex;
  flex-direction: column;
  min-width: 0;
  gap: 4px;
  font-size: 13px;
}
.monitor-links small {
  color: var(--text-color-3);
  font-size: 11px;
  line-height: 1.5;
}
.monitor-arrow {
  flex: 0 0 12px;
  margin: 3px 0 0 auto;
  color: var(--text-color-3);
  font-size: 12px;
}
.video-panel {
  margin-top: 16px;
}
.video-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.video-title > .n-icon {
  color: var(--text-color-3);
  font-size: 18px;
}
.camera-selector {
  width: min(480px, 48vw);
}
.video-monitor-frame {
  height: 540px;
  overflow: hidden;
  border-radius: 8px;
}
@container (max-width: 900px) {
  .workspace-columns {
    grid-template-columns: minmax(0, 1fr);
  }
  .monitor-links {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
  .monitor-icon,
  .monitor-arrow {
    display: none;
  }
}
@container (max-width: 700px) {
  .indicator-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .monitor-links {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .camera-selector {
    width: 100%;
  }
  .video-panel :deep(.n-card-header) {
    align-items: flex-start;
    flex-direction: column;
    gap: 12px;
  }
  .video-panel :deep(.n-card-header__extra) {
    width: 100%;
  }
}
@container (max-width: 440px) {
  .headline-actions {
    width: 100%;
  }
  .venue-selector {
    min-width: 0;
    flex: 1;
  }
  .indicator {
    padding: 12px;
  }
  .indicator-top {
    align-items: flex-start;
  }
  .indicator-icon {
    display: none;
  }
  .indicator-value {
    font-size: 26px;
  }
  .device-list {
    grid-template-columns: minmax(0, 1fr);
  }
  .device-title {
    flex-direction: row;
    align-items: center;
  }
  .device-total {
    flex-basis: auto;
  }
  .device-states {
    gap: 16px;
  }
  .device-arrow {
    display: block;
  }
  .headline-meta {
    gap: 4px;
    flex-direction: column;
  }
}
@media (max-width: 760px) {
  .workbench-page {
    padding: 12px;
  }
  .headline {
    gap: 12px;
    margin-bottom: 16px;
  }
  .headline h2 {
    font-size: 20px;
  }
  .indicator-grid,
  .workspace-main,
  .workspace-side {
    gap: 12px;
  }
  .workspace-columns,
  .video-panel {
    margin-top: 12px;
  }
  .video-monitor-frame {
    height: 360px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .indicator,
  .device-card,
  .monitor-links button {
    transition: none;
  }
}
</style>
