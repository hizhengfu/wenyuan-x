<template>
  <SafetyPageGuard>
    <div class="overview-page">
      <div class="page-heading">
        <div>
          <h2>场馆安全总览</h2>
          <div class="scope-caption">{{ orgId ? '单馆运行视图' : '全部场馆对比视图' }}</div>
        </div>
        <div class="heading-actions" :class="{ 'heading-actions--venue': orgId }">
          <VenueSelect v-model:value="orgId" placeholder="全部场馆" @update:value="handleVenueChange" />
          <SafetyLocationTreeSelect
            v-if="orgId"
            v-model="locationId"
            :options="safetyLocations"
            @update:model-value="load"
          />
          <NButton :loading="loading" @click="load">刷新</NButton>
        </div>
      </div>

      <NAlert v-if="error" type="error" closable class="state-alert" @close="error = ''">{{ error }}</NAlert>
      <NAlert v-if="catalogError" type="warning" closable class="state-alert" @close="catalogError = ''">{{
        catalogError
      }}</NAlert>
      <NSpin :show="loading">
        <template v-if="!orgId">
          <NGrid :cols="4" responsive="screen" :x-gap="12" :y-gap="12">
            <NGridItem v-for="card in portfolioCards" :key="card.label">
              <button
                :class="['summary-card-button', `summary-card-button--${card.tone}`]"
                type="button"
                :disabled="!card.action"
                @click="card.action?.()"
              >
                <span class="summary-card__label">{{ card.label }}</span>
                <strong class="summary-card__value">{{ card.value }}</strong>
                <span class="summary-card__hint">{{ card.hint }}</span>
              </button>
            </NGridItem>
          </NGrid>

          <NCard title="场馆 × 安全领域" class="portfolio-card">
            <template #header-extra
              ><span class="data-cutoff">数据截止 {{ formatDateTime(portfolio.generatedAt) }}</span></template
            >
            <div class="state-legend" aria-label="状态说明">
              <span v-for="item in stateLegend" :key="item.state"
                ><i :class="`state-dot state-dot--${item.state.toLowerCase()}`" />{{ item.label }}</span
              >
            </div>
            <div v-if="portfolio.venues.length" class="matrix-scroll">
              <table class="venue-matrix">
                <thead>
                  <tr>
                    <th scope="col">场馆</th>
                    <th v-for="item in portfolioDomains" :key="item.domain" scope="col">{{ item.label }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="venue in portfolio.venues" :key="venue.venueId">
                    <th scope="row">
                      <button type="button" class="venue-name" @click="selectVenue(venue.venueId)">
                        <strong>{{ venue.shortName || venue.name }}</strong>
                        <span>{{ venue.onlineDevices }} 在线 / {{ venue.offlineDevices }} 离线</span>
                      </button>
                    </th>
                    <td v-for="item in portfolioDomains" :key="item.domain">
                      <button
                        type="button"
                        class="domain-cell"
                        :class="`domain-cell--${domainCell(venue, item.domain).state.toLowerCase()}`"
                        :aria-label="`${venue.name}${item.label}${stateLabel(domainCell(venue, item.domain).state)}`"
                        @click="openDomain(venue.venueId, item.domain)"
                      >
                        <strong>{{ cellHeadline(domainCell(venue, item.domain), item.domain) }}</strong>
                        <span>{{ cellDetail(domainCell(venue, item.domain), item.domain) }}</span>
                        <small>{{ stateLabel(domainCell(venue, item.domain).state) }}</small>
                      </button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <NEmpty v-else-if="!loading" description="当前账号暂无可查看的场馆安全数据" class="overview-empty" />
          </NCard>
        </template>

        <template v-else>
          <NGrid :cols="4" responsive="screen" :x-gap="12" :y-gap="12">
            <NGridItem v-for="card in summaryCards" :key="card.label">
              <button
                :class="['summary-card-button', `summary-card-button--${card.tone}`]"
                type="button"
                @click="openRisks(card.query)"
              >
                <span class="summary-card__label">{{ card.label }}</span>
                <strong class="summary-card__value">{{ card.value }}</strong>
                <span class="summary-card__hint">{{ card.hint }}</span>
              </button>
            </NGridItem>
          </NGrid>

          <NGrid :cols="2" responsive="screen" :x-gap="12" :y-gap="12" class="section-grid">
            <NGridItem v-if="hasOverviewRiskContent">
              <NCard title="安全领域分布" class="section-card">
                <button
                  v-for="row in domainRows"
                  :key="row.domain"
                  type="button"
                  class="distribution-row"
                  @click="openRisks({ domain: row.domain })"
                >
                  <span>{{ row.label }}</span
                  ><NProgress type="line" :percentage="row.percentage" :show-indicator="false" /><strong>{{
                    row.count
                  }}</strong>
                </button>
                <NEmpty v-if="!domainRows.some((row) => row.count)" description="暂无活动风险" />
              </NCard>
            </NGridItem>
            <NGridItem :span="hasOverviewRiskContent ? 1 : 2">
              <NCard title="设备运行状态" class="section-card">
                <div class="device-states">
                  <div>
                    <strong>{{ overview.onlineDevices }}</strong
                    ><span>在线</span>
                  </div>
                  <div>
                    <strong>{{ overview.offlineDevices }}</strong
                    ><span>离线</span>
                  </div>
                  <button type="button" @click="openRisks({ activeOnly: 'true' })">
                    <strong>{{ overview.abnormalDevices }}</strong
                    ><span>异常</span>
                  </button>
                </div>
                <div class="status-tags">
                  <NTag
                    v-for="row in statusRows"
                    :key="row.status"
                    :type="row.status === 'CLOSED' ? 'success' : 'warning'"
                    bordered
                    @click="openRisks({ status: row.status })"
                    >{{ row.label }} {{ row.count }}</NTag
                  >
                </div>
              </NCard>
            </NGridItem>
            <NGridItem v-if="hasOverviewRiskContent">
              <NCard title="建筑 / 空间风险分布" class="section-card">
                <button
                  v-for="row in overview.byLocation"
                  :key="row.locationId || 'none'"
                  type="button"
                  class="location-row"
                  @click="openRisks({ locationId: row.locationId || undefined })"
                >
                  <span>{{ row.locationName }}</span
                  ><strong>{{ row.count }}</strong>
                </button>
                <NEmpty v-if="!overview.byLocation.length" description="暂无空间风险" />
              </NCard>
            </NGridItem>
            <NGridItem v-if="hasOverviewRiskContent">
              <NCard title="最近处置动态" class="section-card">
                <NTimeline v-if="overview.recentActions.length"
                  ><NTimelineItem
                    v-for="log in overview.recentActions"
                    :key="log.id"
                    :time="formatDateTime(log.occurredAt)"
                    :title="actionLabel(log.action)"
                    >{{ log.remark || log.result || statusLabel(log.toStatus) }}</NTimelineItem
                  ></NTimeline
                >
                <NEmpty v-else description="暂无处置动态" />
              </NCard>
            </NGridItem>
          </NGrid>

          <NEmpty
            v-if="!hasOverviewRiskContent && !loading && !error"
            description="当前场馆暂无安全风险或处置记录；设备运行状态仍按实时数据展示"
            class="overview-empty"
          />
          <NCard v-if="hasOverviewRiskContent" title="最新风险" class="latest-card">
            <NDataTable
              v-if="overview.latestRisks.length"
              :columns="columns"
              :data="overview.latestRisks"
              :row-key="(row) => row.id"
              :scroll-x="1050"
            />
            <NEmpty v-else description="暂无风险记录" />
          </NCard>
        </template>
      </NSpin>
    </div>
  </SafetyPageGuard>
</template>

<script setup lang="ts">
import { computed, h, onActivated, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  NAlert,
  NButton,
  NCard,
  NDataTable,
  NEmpty,
  NGrid,
  NGridItem,
  NProgress,
  NSpin,
  NTag,
  NTimeline,
  NTimelineItem,
  type DataTableColumns,
} from 'naive-ui';
import { formatDateTime } from '@/utils';
import { useAuthStore } from '@/store/modules/auth';
import { ingestApi } from '../../museum-device/api/ingest';
import VenueSelect from '../../museum-device/components/VenueSelect.vue';
import { loadVenueCatalog } from '../../museum-device/utils/venueCatalog';
import type { RecordFilterOptionsVo } from '../../museum-device/typings';
import { safetyApi, unwrapResponse } from '../api';
import type {
  SafetyDomain,
  SafetyOverviewVo,
  SafetyPortfolioDomainVo,
  SafetyPortfolioVenueVo,
  SafetyPortfolioVo,
  SafetyRiskStatus,
  SafetyRiskVo,
  SafetyVenueDomainState,
} from '../typings';
import SafetyPageGuard from '../components/SafetyPageGuard.vue';
import SafetyLocationTreeSelect from '../components/SafetyLocationTreeSelect.vue';
import { filterSafetyLocations } from '../utils/location';

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();
const canSelect = computed(() => authStore.hasPermission('ADMIN,MUSEUM_SAFETY_SELECT'));
const loading = ref(false);
const error = ref('');
const catalogError = ref('');
let firstActivation = true;
const orgId = ref<string | null>((route.query.orgId as string) || null);
const locationId = ref<string | null>((route.query.locationId as string) || null);
const catalogs = ref<RecordFilterOptionsVo>({ devices: [], locations: [] });
const safetyLocations = computed(() => filterSafetyLocations(catalogs.value.locations));
const emptyOverview: SafetyOverviewVo = {
  activeRisks: 0,
  majorRisks: 0,
  todayNew: 0,
  todayRecovered: 0,
  byDomain: {},
  byLevel: {},
  byStatus: {},
  byLocation: [],
  onlineDevices: 0,
  offlineDevices: 0,
  abnormalDevices: 0,
  latestRisks: [],
  recentActions: [],
};
const emptyPortfolio: SafetyPortfolioVo = {
  venueCount: 0,
  monitoredVenueCount: 0,
  activeRisks: 0,
  majorRisks: 0,
  onlineDevices: 0,
  offlineDevices: 0,
  dataInterruptedDomains: 0,
  generatedAt: Date.now(),
  venues: [],
};
const overview = ref<SafetyOverviewVo>({ ...emptyOverview });
const portfolio = ref<SafetyPortfolioVo>({ ...emptyPortfolio });
const domainLabels: Record<SafetyDomain, string> = {
  BUILDING: '建筑结构',
  LIGHTNING: '防雷',
  FIRE: '消防',
  SECURITY: '安防',
  ELECTRICITY: '用电',
  BUILDING_ENVIRONMENT: '建筑环境',
};
const portfolioDomains = (Object.keys(domainLabels) as SafetyDomain[]).map((domain) => ({
  domain,
  label: domainLabels[domain],
}));
const statusLabels: Record<SafetyRiskStatus, string> = {
  PENDING_CONFIRMATION: '待确认',
  PROCESSING: '处理中',
  RECOVERED_PENDING_CONFIRMATION: '已恢复待确认',
  CLOSED: '已关闭',
};
const stateLegend: Array<{ state: SafetyVenueDomainState; label: string }> = [
  { state: 'NORMAL', label: '正常' },
  { state: 'RISK', label: '存在风险' },
  { state: 'DATA_INTERRUPTED', label: '数据中断' },
  { state: 'UNCONFIGURED', label: '未配置' },
];
const summaryCards = computed(() => [
  {
    label: '活动风险',
    value: overview.value.activeRisks,
    hint: '全部未关闭风险',
    tone: 'danger',
    query: { activeOnly: 'true' },
  },
  {
    label: '重大风险',
    value: overview.value.majorRisks,
    hint: '重大和严重等级风险',
    tone: 'critical',
    query: { majorOnly: 'true', activeOnly: 'true' },
  },
  {
    label: '今日新增',
    value: overview.value.todayNew,
    hint: '今日首次触发',
    tone: 'info',
    query: { from: startOfToday() },
  },
  {
    label: '今日恢复',
    value: overview.value.todayRecovered,
    hint: '等待人工确认恢复',
    tone: 'success',
    query: { status: 'RECOVERED_PENDING_CONFIRMATION' },
  },
]);
const portfolioCards = computed(() => [
  {
    label: '纳入监测场馆',
    value: `${portfolio.value.monitoredVenueCount} / ${portfolio.value.venueCount}`,
    hint: '已配置安全监测 / 可见场馆',
    tone: 'info',
  },
  {
    label: '活动风险',
    value: portfolio.value.activeRisks,
    hint: '跨馆未关闭风险',
    tone: 'danger',
    action: () => openRisks({ activeOnly: 'true' }),
  },
  {
    label: '重大风险',
    value: portfolio.value.majorRisks,
    hint: '重大和严重等级风险',
    tone: 'critical',
    action: () => openRisks({ majorOnly: 'true', activeOnly: 'true' }),
  },
  {
    label: '数据中断领域',
    value: portfolio.value.dataInterruptedDomains,
    hint: '已配置但存在离线设备',
    tone: 'warning',
  },
]);
const domainRows = computed(() => {
  const max = Math.max(1, ...Object.values(overview.value.byDomain).map(Number));
  return portfolioDomains.map(({ domain, label }) => ({
    domain,
    label,
    count: overview.value.byDomain[domain] || 0,
    percentage: Math.round(((overview.value.byDomain[domain] || 0) / max) * 100),
  }));
});
const statusRows = computed(() =>
  (Object.keys(statusLabels) as SafetyRiskStatus[]).map((status) => ({
    status,
    label: statusLabels[status],
    count: overview.value.byStatus[status] || 0,
  })),
);
const hasOverviewRiskContent = computed(() =>
  Boolean(
    overview.value.activeRisks ||
      overview.value.majorRisks ||
      overview.value.todayNew ||
      overview.value.todayRecovered ||
      overview.value.byLocation.length ||
      overview.value.latestRisks.length ||
      overview.value.recentActions.length ||
      Object.values(overview.value.byDomain).some(Number) ||
      Object.values(overview.value.byStatus).some(Number),
  ),
);
const columns: DataTableColumns<SafetyRiskVo> = [
  { title: '领域', key: 'domain', width: 110, render: (row) => domainLabels[row.domain] },
  { title: '等级', key: 'level', width: 90, render: (row) => levelLabel(row.level) },
  { title: '风险', key: 'title', minWidth: 220 },
  { title: '设备', key: 'deviceName', minWidth: 160, render: (row) => row.deviceName || row.deviceId },
  {
    title: '触发位置',
    key: 'locationSnapshotPath',
    minWidth: 220,
    render: (row) => row.locationSnapshotPath || row.locationSnapshotId || '--',
  },
  { title: '状态', key: 'status', width: 130, render: (row) => statusLabel(row.status) },
  { title: '最近发生', key: 'lastOccurredAt', width: 180, render: (row) => formatDateTime(row.lastOccurredAt) },
  {
    title: '操作',
    key: 'actions',
    width: 80,
    render: (row) =>
      h(NButton, { text: true, type: 'primary', onClick: () => openRisks({ id: row.id }) }, { default: () => '查看' }),
  },
];

async function loadCatalogs() {
  catalogs.value = { devices: [], locations: [] };
  if (!orgId.value) return;
  try {
    catalogs.value = unwrapResponse<RecordFilterOptionsVo>(await ingestApi.recordFilterOptions({ orgId: orgId.value }));
  } catch {
    catalogError.value = '当前场馆建筑/空间目录加载失败，仍可查看场馆总览。';
  }
}
async function load() {
  if (!canSelect.value) return;
  loading.value = true;
  error.value = '';
  const failures: string[] = [];
  try {
    try {
      portfolio.value = {
        ...emptyPortfolio,
        ...unwrapResponse<SafetyPortfolioVo>(await safetyApi.portfolioOverview({ orgId: orgId.value || undefined })),
      };
    } catch (cause: any) {
      portfolio.value = { ...emptyPortfolio };
      failures.push(cause?.message || '多场馆态势加载失败');
    }
    if (orgId.value) {
      try {
        const value = unwrapResponse<SafetyOverviewVo>(
          await safetyApi.overview({ orgId: orgId.value, locationId: locationId.value || undefined }),
        );
        overview.value = {
          ...emptyOverview,
          ...value,
          byDomain: value?.byDomain || {},
          byLevel: value?.byLevel || {},
          byStatus: value?.byStatus || {},
          byLocation: value?.byLocation || [],
          latestRisks: value?.latestRisks || [],
          recentActions: value?.recentActions || [],
        };
      } catch (cause: any) {
        overview.value = { ...emptyOverview };
        failures.push(cause?.message || '单馆安全总览加载失败');
      }
    } else overview.value = { ...emptyOverview };
    error.value = failures.join('；');
  } finally {
    loading.value = false;
  }
}
async function handleVenueChange() {
  locationId.value = null;
  catalogError.value = '';
  await router.replace({ query: { ...route.query, orgId: orgId.value || undefined, locationId: undefined } });
  await loadCatalogs();
  await load();
}
function selectVenue(value: string) {
  orgId.value = value;
  void handleVenueChange();
}
function domainCell(venue: SafetyPortfolioVenueVo, domain: SafetyDomain): SafetyPortfolioDomainVo {
  return (
    venue.domains[domain] || {
      domain,
      state: 'UNCONFIGURED',
      configuredDevices: 0,
      onlineDevices: 0,
      offlineDevices: 0,
      abnormalDevices: 0,
      activeRisks: 0,
      majorRisks: 0,
      activeAlarms: 0,
      highestRiskLevel: null,
      latestDataAt: null,
      systemCount: 0,
    }
  );
}
function cellHeadline(cell: SafetyPortfolioDomainVo, domain: SafetyDomain) {
  if (domain === 'LIGHTNING' && cell.systemCount != null) return `${cell.systemCount} 套系统`;
  if (cell.state === 'UNCONFIGURED') return '—';
  if (cell.activeRisks) return `${cell.activeRisks} 项风险`;
  if (cell.activeAlarms) return `${cell.activeAlarms} 条告警`;
  return `${cell.onlineDevices} / ${cell.configuredDevices}`;
}
function cellDetail(cell: SafetyPortfolioDomainVo, domain: SafetyDomain) {
  if (domain === 'LIGHTNING' && cell.state === 'UNCONFIGURED')
    return cell.systemCount ? '系统尚未绑定设备' : '未配置防雷系统';
  if (domain === 'LIGHTNING' && cell.activeRisks) return `${cell.activeRisks} 项活动风险`;
  if (domain === 'LIGHTNING' && cell.activeAlarms) return `${cell.activeAlarms} 条活动告警`;
  if (cell.state === 'UNCONFIGURED') return '无监测设备';
  if (cell.majorRisks) return `${cell.majorRisks} 项重大风险`;
  if (cell.offlineDevices) return `${cell.offlineDevices} 台离线`;
  return cell.latestDataAt ? `更新 ${formatDateTime(cell.latestDataAt)}` : '暂无上报时间';
}
function stateLabel(state: SafetyVenueDomainState) {
  return stateLegend.find((item) => item.state === state)?.label || state;
}
function openDomain(venueId: string, domain: SafetyDomain) {
  const routeByDomain: Record<SafetyDomain, string> = {
    BUILDING: '/museum-safety/building',
    LIGHTNING: '/museum-safety/lightning',
    FIRE: '/museum-safety/fire-security',
    SECURITY: '/museum-safety/fire-security',
    ELECTRICITY: '/museum-safety/electric-environment',
    BUILDING_ENVIRONMENT: '/museum-safety/electric-environment',
  };
  router.push({
    path: routeByDomain[domain],
    query: { orgId: venueId, ...(domain === 'LIGHTNING' ? {} : { tab: domain }) },
  });
}
function openRisks(query: Record<string, unknown>) {
  router.push({
    path: '/museum-safety/risk',
    query: {
      ...(orgId.value ? { orgId: orgId.value } : {}),
      ...(locationId.value ? { locationId: locationId.value } : {}),
      ...query,
    } as any,
  });
}
function startOfToday() {
  const date = new Date();
  date.setHours(0, 0, 0, 0);
  return date.getTime();
}
function levelLabel(level: string) {
  return (
    ({ NOTICE: '提示', WARNING: '警告', MAJOR: '重大', CRITICAL: '严重' } as Record<string, string>)[level] || level
  );
}
function statusLabel(status: string) {
  return statusLabels[status as SafetyRiskStatus] || status;
}
function actionLabel(action: string) {
  return (
    (
      {
        TRIGGERED: '风险触发',
        UPDATED: '告警更新',
        CONFIRMED: '已确认',
        ASSIGNED: '已派单',
        PROCESSED: '处理记录',
        RECOVERED: '来源已恢复',
        RECOVERY_CONFIRMED: '恢复已确认',
        CLOSED: '已关闭',
        COMMENTED: '补充说明',
      } as Record<string, string>
    )[action] || action
  );
}
onMounted(async () => {
  if (!canSelect.value) return;
  try {
    const venues = await loadVenueCatalog();
    if (!orgId.value && venues.length === 1) orgId.value = venues[0].id;
  } catch {
    catalogError.value = '场馆目录加载失败，将按当前账号可见范围展示。';
  }
  await loadCatalogs();
  await load();
});
onActivated(() => {
  if (firstActivation) {
    firstActivation = false;
    return;
  }
  if (!canSelect.value) return;
  orgId.value = (route.query.orgId as string) || null;
  locationId.value = (route.query.locationId as string) || null;
  void loadCatalogs().then(load);
});
</script>

<style scoped>
.overview-page {
  width: 100%;
  height: 100%;
  min-height: 0;
  padding: 16px;
  overflow: auto;
  box-sizing: border-box;
}
.page-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}
.page-heading h2 {
  margin: 0;
  font-size: 20px;
}
.scope-caption {
  margin-top: 4px;
  color: #697386;
  font-size: 12px;
}
.heading-actions {
  display: grid;
  grid-template-columns: minmax(220px, 300px) auto;
  gap: 10px;
  align-items: center;
}
.heading-actions--venue {
  grid-template-columns: minmax(200px, 260px) minmax(240px, 320px) auto;
}
.state-alert {
  margin-bottom: 12px;
}
.summary-card-button {
  --card-accent: #2080f0;
  --card-bg: rgba(32, 128, 240, 0.08);
  width: 100%;
  min-height: 126px;
  padding: 18px 22px;
  border: 1px solid color-mix(in srgb, var(--card-accent) 30%, transparent);
  border-top: 3px solid var(--card-accent);
  border-radius: 6px;
  background: linear-gradient(135deg, var(--card-bg), rgba(255, 255, 255, 0.02));
  color: inherit;
  text-align: left;
  cursor: pointer;
  transition:
    transform 0.18s,
    border-color 0.18s,
    box-shadow 0.18s;
}
.summary-card-button:not(:disabled):hover,
.summary-card-button:not(:disabled):focus-visible {
  transform: translateY(-1px);
  border-color: var(--card-accent);
  box-shadow: 0 5px 16px color-mix(in srgb, var(--card-accent) 18%, transparent);
  outline: none;
}
.summary-card-button:disabled {
  cursor: default;
}
.summary-card-button--info {
  --card-accent: #2080f0;
  --card-bg: rgba(32, 128, 240, 0.09);
}
.summary-card-button--danger {
  --card-accent: #d03050;
  --card-bg: rgba(208, 48, 80, 0.09);
}
.summary-card-button--critical {
  --card-accent: #9f1239;
  --card-bg: rgba(159, 18, 57, 0.1);
}
.summary-card-button--warning {
  --card-accent: #d97706;
  --card-bg: rgba(217, 119, 6, 0.1);
}
.summary-card-button--success {
  --card-accent: #18a058;
  --card-bg: rgba(24, 160, 88, 0.09);
}
.summary-card__label,
.summary-card__hint,
.summary-card__value {
  display: block;
}
.summary-card__label {
  opacity: 0.76;
}
.summary-card__value {
  margin: 9px 0 5px;
  color: var(--card-accent);
  font-size: 30px;
}
.summary-card__hint {
  opacity: 0.68;
  font-size: 12px;
}
.portfolio-card {
  margin-top: 12px;
}
.data-cutoff {
  color: #697386;
  font-size: 12px;
}
.state-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
  margin-bottom: 14px;
  color: #556173;
}
.state-legend span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.state-dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: #9ca3af;
}
.state-dot--normal {
  background: #18a058;
}
.state-dot--risk {
  background: #d03050;
}
.state-dot--data_interrupted {
  background: #f0a020;
}
.state-dot--unconfigured {
  background: #9ca3af;
}
.matrix-scroll {
  overflow: auto;
}
.venue-matrix {
  width: 100%;
  min-width: 1120px;
  border-collapse: separate;
  border-spacing: 0;
}
.venue-matrix th,
.venue-matrix td {
  padding: 8px;
  border-right: 1px solid rgba(128, 128, 128, 0.14);
  border-bottom: 1px solid rgba(128, 128, 128, 0.14);
  vertical-align: stretch;
}
.venue-matrix thead th {
  padding: 12px 8px;
  background: rgba(128, 128, 128, 0.06);
  text-align: center;
}
.venue-matrix tr > :first-child {
  border-left: 1px solid rgba(128, 128, 128, 0.14);
}
.venue-matrix thead tr:first-child th {
  border-top: 1px solid rgba(128, 128, 128, 0.14);
}
.venue-name,
.domain-cell {
  width: 100%;
  height: 100%;
  border: 0;
  background: transparent;
  color: inherit;
  text-align: left;
  cursor: pointer;
}
.venue-name {
  min-width: 150px;
  padding: 8px;
}
.venue-name strong,
.venue-name span,
.domain-cell strong,
.domain-cell span,
.domain-cell small {
  display: block;
}
.venue-name span {
  margin-top: 6px;
  color: #697386;
  font-size: 12px;
}
.domain-cell {
  min-width: 130px;
  padding: 10px;
  border-radius: 6px;
}
.domain-cell:hover,
.domain-cell:focus-visible {
  box-shadow: inset 0 0 0 2px #7c4dff;
  outline: none;
}
.domain-cell strong {
  font-size: 15px;
}
.domain-cell span {
  min-height: 18px;
  margin-top: 5px;
  font-size: 12px;
}
.domain-cell small {
  margin-top: 8px;
  font-weight: 600;
}
.domain-cell--normal {
  background: rgba(24, 160, 88, 0.1);
}
.domain-cell--risk {
  background: rgba(208, 48, 80, 0.12);
}
.domain-cell--data_interrupted {
  background: rgba(240, 160, 32, 0.14);
}
.domain-cell--unconfigured {
  background: rgba(128, 128, 128, 0.08);
  color: #646d79;
}
.section-grid {
  margin-top: 12px;
}
.section-card {
  height: 310px;
  overflow: auto;
}
.distribution-row,
.location-row {
  width: 100%;
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
}
.distribution-row {
  display: grid;
  grid-template-columns: 86px 1fr 38px;
  gap: 10px;
  align-items: center;
  padding: 7px 0;
  text-align: left;
}
.location-row {
  display: flex;
  justify-content: space-between;
  padding: 10px 2px;
  border-bottom: 1px solid rgba(128, 128, 128, 0.12);
}
.device-states {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}
.device-states button,
.device-states > div {
  border: 0;
  border-radius: 8px;
  padding: 20px 8px;
  background: rgba(32, 128, 240, 0.08);
  color: inherit;
  text-align: center;
}
.device-states button {
  cursor: pointer;
}
.device-states strong,
.device-states span {
  display: block;
}
.device-states strong {
  font-size: 26px;
}
.device-states span {
  margin-top: 4px;
  color: #697386;
}
.status-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 20px;
}
.status-tags :deep(.n-tag) {
  cursor: pointer;
}
.latest-card {
  margin-top: 12px;
}
.overview-empty {
  padding: 64px 0;
}
@media (max-width: 1100px) {
  .page-heading {
    align-items: flex-start;
    flex-direction: column;
  }
  .heading-actions,
  .heading-actions--venue {
    width: 100%;
    grid-template-columns: 1fr 1fr auto;
  }
}
@media (max-width: 720px) {
  .heading-actions,
  .heading-actions--venue {
    grid-template-columns: 1fr;
  }
  .overview-page {
    padding: 10px;
  }
}
</style>
