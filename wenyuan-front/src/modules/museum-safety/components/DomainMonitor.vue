<template>
  <SafetyPageGuard>
    <div class="domain-page">
      <NCard :bordered="false" class="domain-card">
        <template #header>
          <div class="page-title">{{ title }}</div>
        </template>

        <NTabs v-model:value="activeTab" type="line" @update:value="handleTabChange">
          <NTabPane v-for="tab in tabs" :key="tab.key || tab.domain" :name="tab.key || tab.domain" :tab="tab.label" />
        </NTabs>

        <div class="scope-toolbar">
          <VenueSelect v-model:value="orgId" placeholder="全部场馆" @update:value="handleVenueChange" />
          <span>{{ orgId ? `当前为${activeTabConfig.label}单馆监测视图` : `按场馆比较${activeTabConfig.label}状态` }}</span>
        </div>

        <div v-if="orgId" class="filters">
          <SafetyLocationTreeSelect v-model="locationId" :options="safetyLocations" />
          <NSelect v-model:value="deviceId" :options="deviceOptions" clearable filterable placeholder="全部设备" />
          <NSelect v-model:value="metricCode" :options="metricOptions" clearable filterable placeholder="全部指标" />
          <NSelect v-model:value="fieldCode" :options="fieldOptions" clearable placeholder="全部字段" :disabled="!fieldOptions.length" />
          <NDatePicker v-model:value="range" type="datetimerange" clearable />
          <NRadioGroup v-if="showEnergyGranularity" v-model:value="energyGranularity" size="small">
            <NRadioButton value="DAY">日趋势</NRadioButton>
            <NRadioButton value="MONTH">月趋势</NRadioButton>
          </NRadioGroup>
          <NButton type="primary" :loading="loading" @click="refresh">查询</NButton>
        </div>

        <NAlert v-if="error" type="error" closable class="state-alert" @close="error = ''">{{ error }}</NAlert>
        <NAlert v-if="catalogError" type="warning" closable class="state-alert" @close="catalogError = ''">{{ catalogError }}</NAlert>
        <NAlert v-if="orgId && series?.locationChangedInRange" type="warning" class="state-alert">
          查询范围内设备位置发生过变化；曲线始终按设备连续展示，当前位置为
          {{ series.currentLocationPath || series.currentLocationId || '未设置' }}。
          <span v-if="series.locationChanges?.length">共 {{ series.locationChanges.length }} 次位置调整。</span>
          <ul v-if="series.locationChanges?.length" class="location-changes">
            <li v-for="change in series.locationChanges" :key="change.id">
              {{ formatDateTime(change.changedAt) }}：{{ change.fromLocationName || change.fromLocationId || '未设置' }}
              → {{ change.toLocationName || change.toLocationId || '未设置' }}<span v-if="change.reason">（{{ change.reason }}）</span>
            </li>
          </ul>
        </NAlert>

        <NSpin :show="loading">
          <template v-if="!orgId">
            <NGrid :cols="4" responsive="screen" :x-gap="12" :y-gap="12" class="device-summary-grid">
              <NGridItem v-for="card in portfolioCards" :key="card.label">
                <NCard size="small"><NStatistic :label="card.label" :value="card.value" /><div class="stat-hint">{{ card.hint }}</div></NCard>
              </NGridItem>
            </NGrid>
            <NCard :title="`${activeTabConfig.label} · 场馆对比`" size="small" class="section-card portfolio-comparison">
              <template #header-extra>统计范围只包含当前页签对应指标</template>
              <NDataTable
                v-if="portfolio.venues.length"
                :columns="portfolioColumns"
                :data="portfolio.venues"
                :row-key="(row) => row.venueId"
                :scroll-x="1120"
              />
              <NEmpty v-else-if="!loading && !error" :description="`暂无${activeTabConfig.label}场馆数据`" class="domain-empty" />
            </NCard>
          </template>
          <template v-else>
          <div class="profile-heading">
            <div><strong>{{ profileTitle }}</strong><span>{{ profileDescription }}</span></div>
            <NButton v-if="profile === 'STRUCTURAL'" text type="primary" @click="router.push('/museum-device/spatial')">查看空间与设备</NButton>
          </div>
          <NGrid :cols="4" responsive="screen" :x-gap="12" :y-gap="12" class="device-summary-grid" :class="`device-summary-grid--${profile.toLowerCase()}`">
            <NGridItem v-for="card in profileCards" :key="card.label">
              <NCard size="small"><NStatistic :label="card.label" :value="card.value" /><div class="stat-hint">{{ card.hint }}</div></NCard>
            </NGridItem>
          </NGrid>
          <template v-if="!loading">
            <NEmpty
              v-if="!hasDomainContent && !error"
              description="当前筛选范围暂无监测、告警或风险数据"
              class="domain-empty"
            />
            <template v-else-if="hasDomainContent">
              <NCard size="small" title="活动告警统计" class="section-card alarm-statistics">
                <div><span>按等级</span><NTag v-for="row in severityStatistics" :key="row.key" :type="row.key === 'CRITICAL' || row.key === 'MAJOR' ? 'error' : row.key === 'WARNING' ? 'warning' : 'default'">{{ row.label }} {{ row.count }}</NTag></div>
                <div><span>按类型</span><NTag v-for="row in typeStatistics" :key="row.key">{{ row.label }} {{ row.count }}</NTag></div>
                <NEmpty v-if="!severityStatistics.length && !typeStatistics.length" description="暂无活动告警统计" />
              </NCard>

              <div v-if="domainData.latestRecordTotal" class="record-summary">共 {{ domainData.latestRecordTotal }} 个设备指标最新值</div>
              <NGrid responsive="screen" :cols="'1 s:2 m:3 l:4'" :x-gap="12" :y-gap="12" class="latest-grid">
                <NGridItem v-for="record in filteredRecords" :key="record.id">
                  <NCard size="small" hoverable class="metric-card" @click="selectRecord(record)">
                    <div class="metric-card__name">{{ record.metricName || record.key }}</div>
                    <div class="metric-card__value">{{ displayRecordValue(record) }}</div>
                    <div class="metric-card__meta">{{ record.deviceName || record.deviceId }}</div>
                    <div class="metric-card__meta">{{ record.locationPath || record.locationName || '未设置位置' }}</div>
                  </NCard>
                </NGridItem>
              </NGrid>
              <NEmpty v-if="!filteredRecords.length" description="当前筛选范围暂无监测数据" class="empty" />
              <NPagination
                v-if="domainData.latestRecordTotal > recordPageSize"
                v-model:page="recordPage"
                v-model:page-size="recordPageSize"
                :item-count="domainData.latestRecordTotal"
                :page-sizes="[24, 48, 96]"
                show-size-picker
                class="record-pagination"
                @update:page="loadDomain"
                @update:page-size="handleRecordPageSize"
              />

              <NCard v-if="series" size="small" title="指标趋势" class="section-card">
                <template #header-extra>
                  <span>{{ selectedRecord?.deviceName || selectedRecord?.deviceId }} · {{ series.name || series.key }}</span>
                </template>
                <NGrid :cols="5" responsive="screen" :x-gap="12" class="summary-grid">
                  <NGridItem><NStatistic label="最新值" :value="summaryValue('latestValue')" /></NGridItem>
                  <NGridItem><NStatistic label="最小值" :value="summaryValue('minValue')" /></NGridItem>
                  <NGridItem><NStatistic label="最大值" :value="summaryValue('maxValue')" /></NGridItem>
                  <NGridItem><NStatistic label="平均值" :value="summaryValue('averageValue')" /></NGridItem>
                  <NGridItem><NStatistic label="变化率" :value="summaryChangeRate" /></NGridItem>
                </NGrid>
                <MetricSeriesChart :data="displaySeries" height="340px" @point-click="openSeriesPoint" />
              </NCard>

              <NCard size="small" title="当前活动告警" class="section-card">
                <template #header-extra>共 {{ domainData.activeAlarmTotal }} 条，展示最近 {{ filteredAlarms.length }} 条</template>
                <NDataTable v-if="filteredAlarms.length" :columns="alarmColumns" :data="filteredAlarms" :row-key="(row) => row.id" :scroll-x="1100" />
                <NTimeline v-if="filteredAlarms.length" class="alarm-timeline">
                  <NTimelineItem v-for="alarm in filteredAlarms.slice(0, 10)" :key="alarm.id" :title="alarm.title" :time="formatDateTime(alarm.lastOccurredAt)">
                    {{ alarm.deviceName || alarm.deviceId }} · {{ alarm.metricCode || alarm.alarmCode }} / {{ fieldName(alarm.metricCode || '', alarm.fieldCode) }} · {{ alarm.triggerValue || '--' }}
                  </NTimelineItem>
                </NTimeline>
                <NEmpty v-if="!filteredAlarms.length" description="当前范围暂无活动告警" class="empty" />
              </NCard>

              <NCard size="small" title="活动风险" class="section-card">
                <template #header-extra>共 {{ domainData.activeRiskTotal }} 条，展示最近 {{ filteredRisks.length }} 条</template>
                <NDataTable v-if="filteredRisks.length" :columns="riskColumns" :data="filteredRisks" :row-key="(row) => row.id" :scroll-x="980" />
                <NEmpty v-else description="当前范围暂无活动风险" class="empty" />
              </NCard>

              <NCard size="small" title="历史风险" class="section-card">
                <template #header-extra>共 {{ domainData.historicalRiskTotal }} 条，展示最近 {{ filteredHistoryRisks.length }} 条</template>
                <NDataTable v-if="filteredHistoryRisks.length" :columns="riskColumns" :data="filteredHistoryRisks" :row-key="(row) => row.id" :scroll-x="980" />
                <NEmpty v-else description="当前范围暂无历史风险" class="empty" />
              </NCard>
            </template>
          </template>
          </template>
        </NSpin>
      </NCard>
      <RiskEventHandling v-model:show="riskHandlingVisible" :id="riskHandlingId" @saved="loadDomain" />
    </div>
  </SafetyPageGuard>
</template>

<script setup lang="ts">
import { computed, h, onActivated, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  NAlert,
  NButton,
  NCard,
  NDataTable,
  NDatePicker,
  NEmpty,
  NGrid,
  NGridItem,
  NPagination,
  NRadioButton,
  NRadioGroup,
  NSelect,
  NSpin,
  NStatistic,
  NTabPane,
  NTabs,
  NTag,
  NTimeline,
  NTimelineItem,
  type DataTableColumns,
} from 'naive-ui';
import { formatDateTime } from '@/utils';
import { useAuthStore } from '@/store/modules/auth';
import { ingestApi } from '../../museum-device/api/ingest';
import MetricSeriesChart from '../../museum-device/components/MetricSeriesChart.vue';
import VenueSelect from '../../museum-device/components/VenueSelect.vue';
import { loadVenueCatalog } from '../../museum-device/utils/venueCatalog';
import type {
  IngestRecordVo,
  MuseumMetricFieldSeriesVo,
  MuseumMetricSeriesVo,
  RecordFilterOptionsVo,
  StandardMetricDefinition,
} from '../../museum-device/typings';
import { safetyApi, unwrapResponse } from '../api';
import type {
  SafetyAlarmVo,
  SafetyDomain,
  SafetyDomainVo,
  SafetyPortfolioDomainVo,
  SafetyPortfolioVenueVo,
  SafetyPortfolioVo,
  SafetyRiskVo,
  SafetyVenueDomainState,
} from '../typings';
import SafetyPageGuard from './SafetyPageGuard.vue';
import RiskEventHandling from './RiskEventHandling.vue';
const riskHandlingId = ref(''), riskHandlingVisible = ref(false);
function openRisk(id: string) { riskHandlingId.value = id; riskHandlingVisible.value = true; }
import SafetyLocationTreeSelect from './SafetyLocationTreeSelect.vue';
import { filterSafetyLocations, safetyLocationSubtreeIds } from '../utils/location';

interface DomainTab { key?: string; label: string; domain: SafetyDomain; metricCodes?: string[] }
type DomainProfile = 'STRUCTURAL' | 'INCIDENT' | 'ELECTRIC';
const props = defineProps<{ title: string; tabs: DomainTab[]; profile: DomainProfile }>();
const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();
const canSelect = computed(() => authStore.hasPermission('ADMIN,MUSEUM_SAFETY_SELECT'));
const requestedTab = String(route.query.tab || '');
const initialTab = props.tabs.find((tab) => (tab.key || tab.domain) === requestedTab || tab.domain === requestedTab) || props.tabs[0];
const activeTab = ref(initialTab.key || initialTab.domain);
const activeTabConfig = computed(() => props.tabs.find((tab) => (tab.key || tab.domain) === activeTab.value) || props.tabs[0]);
const activeDomain = computed(() => activeTabConfig.value.domain);
const orgId = ref<string | null>((route.query.orgId as string) || null);
const loading = ref(false);
const error = ref('');
const catalogError = ref('');
let firstActivation = true;
const emptyDomainData = (domain: SafetyDomain): SafetyDomainVo => ({
  domain, latestRecords: [], activeRisks: [], recentRisks: [], activeAlarms: [],
  latestRecordTotal: 0, latestRecordPageNum: 1, latestRecordPageSize: 24,
  activeRiskTotal: 0, historicalRiskTotal: 0, activeAlarmTotal: 0,
  onlineDevices: 0, offlineDevices: 0, abnormalDevices: 0, alarmsBySeverity: {}, alarmsByType: {},
});
const domainData = ref<SafetyDomainVo>(emptyDomainData(props.tabs[0].domain));
const emptyPortfolio = (): SafetyPortfolioVo => ({
  venueCount: 0, monitoredVenueCount: 0, activeRisks: 0, majorRisks: 0,
  onlineDevices: 0, offlineDevices: 0, dataInterruptedDomains: 0,
  generatedAt: Date.now(), venues: [],
});
const portfolio = ref<SafetyPortfolioVo>(emptyPortfolio());
const options = ref<RecordFilterOptionsVo>({ devices: [], locations: [] });
const metrics = ref<StandardMetricDefinition[]>([]);
const locationId = ref<string | null>(null);
const deviceId = ref<string | null>(null);
const metricCode = ref<string | null>(null);
const fieldCode = ref<string | null>(null);
const recordPage = ref(1);
const recordPageSize = ref(24);
const energyGranularity = ref<'DAY' | 'MONTH'>('DAY');
const now = Date.now();
const range = ref<[number, number] | null>([now - 24 * 60 * 60 * 1000, now]);
const selectedRecord = ref<IngestRecordVo | null>(null);
const series = ref<MuseumMetricSeriesVo | null>(null);
const safetyLocations = computed(() => filterSafetyLocations(options.value.locations));

const domainCategories: Record<SafetyDomain, string[]> = {
  BUILDING: ['STRUCTURE'],
  LIGHTNING: ['LIGHTNING_PROTECTION'],
  FIRE: ['FIRE'],
  SECURITY: ['SECURITY'],
  ELECTRICITY: ['ELECTRICITY'],
  BUILDING_ENVIRONMENT: ['BUILDING_ENVIRONMENT'],
};
const locationIds = computed(() => locationId.value
  ? safetyLocationSubtreeIds(locationId.value, safetyLocations.value)
  : new Set(safetyLocations.value.map((item) => item.id)));
const deviceOptions = computed(() => options.value.devices
  .filter((item) => !item.locationId || locationIds.value.has(item.locationId))
  .map((item) => ({
  label: `${item.name || item.code}${item.locationPath ? ` · ${item.locationPath}` : ''}`,
  value: item.id,
})));
const availableMetrics = computed(() => metrics.value.filter((item) =>
  domainCategories[activeDomain.value].includes(item.category)
  && (!activeTabConfig.value.metricCodes?.length || activeTabConfig.value.metricCodes.includes(item.code)),
));
const metricOptions = computed(() => availableMetrics.value.map((item) => ({ label: `${item.name}（${item.code}）`, value: item.code })));
const selectedMetric = computed(() => metrics.value.find((item) => item.code === metricCode.value));
const fieldOptions = computed(() => (selectedMetric.value?.valueFields || []).map((field) => ({ label: field.name, value: field.code })));
const showEnergyGranularity = computed(() => activeDomain.value === 'ELECTRICITY' && metricCode.value === 'PHASE_ENERGY');
const filteredRecords = computed(() => domainData.value.latestRecords.filter((record) =>
  availableMetrics.value.some((metric) => metric.code === record.key)
  && (!deviceId.value || record.deviceId === deviceId.value)
  && (!metricCode.value || record.key === metricCode.value),
));
const filteredRisks = computed(() => domainData.value.activeRisks.filter((risk) => !deviceId.value || risk.deviceId === deviceId.value));
const filteredHistoryRisks = computed(() => domainData.value.recentRisks.filter((risk) =>
  risk.status === 'CLOSED'
  && (!deviceId.value || risk.deviceId === deviceId.value),
));
const filteredAlarms = computed(() => domainData.value.activeAlarms.filter((alarm) =>
  (!deviceId.value || alarm.deviceId === deviceId.value)
  && (!metricCode.value || alarm.metricCode === metricCode.value)
  && (!fieldCode.value || alarm.fieldCode === fieldCode.value),
));
const severityStatistics = computed(() => Object.entries(domainData.value.alarmsBySeverity || {}).map(([key, count]) => ({ key, count, label: alarmSeverityLabel(key) })));
const typeStatistics = computed(() => Object.entries(domainData.value.alarmsByType || {}).map(([key, count]) => ({ key, count, label: key })));
const profileTitle = computed(() => ({ STRUCTURAL: '监测连续性', INCIDENT: '事件态势', ELECTRIC: '运行状态' })[props.profile]);
const profileDescription = computed(() => ({
  STRUCTURAL: '关注监测点覆盖、数据连续性与结构异常趋势',
  INCIDENT: '关注当前事件、重大风险与设备就绪情况',
  ELECTRIC: '关注关键运行指标、异常设备与风险变化',
})[props.profile]);
const profileCards = computed(() => {
  const configured = domainData.value.onlineDevices + domainData.value.offlineDevices;
  if (props.profile === 'INCIDENT') return [
    { label: `${activeTabConfig.value.label}活动告警`, value: domainData.value.activeAlarmTotal, hint: '等待确认或处置的事件' },
    { label: '重大活动风险', value: domainData.value.activeRisks.filter((item) => item.level === 'MAJOR' || item.level === 'CRITICAL').length, hint: '重大和严重等级' },
    { label: '设备就绪', value: `${domainData.value.onlineDevices} / ${configured}`, hint: '在线设备 / 已配置设备' },
    { label: '处置中风险', value: domainData.value.activeRiskTotal, hint: '当前未关闭风险' },
  ];
  if (props.profile === 'ELECTRIC') return [
    { label: '最新监测指标', value: domainData.value.latestRecordTotal, hint: '当前页签设备指标最新值' },
    { label: '在线设备', value: domainData.value.onlineDevices, hint: `共配置 ${configured} 台` },
    { label: '异常设备', value: domainData.value.abnormalDevices, hint: '存在活动告警的设备' },
    { label: '活动风险', value: domainData.value.activeRiskTotal, hint: '当前未关闭风险' },
  ];
  return [
    { label: '监测点', value: configured, hint: '当前页签已配置设备' },
    { label: '数据中断', value: domainData.value.offlineDevices, hint: '超过在线判定时间' },
    { label: '活动风险', value: domainData.value.activeRiskTotal, hint: '当前未关闭风险' },
    { label: '活动告警', value: domainData.value.activeAlarmTotal, hint: '当前有效告警' },
  ];
});
const portfolioCells = computed(() => portfolio.value.venues.map((venue) => portfolioCell(venue)));
const portfolioCards = computed(() => [
  { label: '纳入监测场馆', value: portfolioCells.value.filter((item) => item.configuredDevices > 0).length, hint: `共 ${portfolio.value.venueCount} 个可见场馆` },
  { label: '存在风险场馆', value: portfolioCells.value.filter((item) => item.activeRisks > 0).length, hint: `共 ${portfolioCells.value.reduce((sum, item) => sum + item.activeRisks, 0)} 项活动风险` },
  { label: '重大风险场馆', value: portfolioCells.value.filter((item) => item.majorRisks > 0).length, hint: '存在重大或严重风险' },
  { label: '数据中断场馆', value: portfolioCells.value.filter((item) => item.state === 'DATA_INTERRUPTED').length, hint: '已配置但存在离线设备' },
]);
const hasDomainContent = computed(() => Boolean(
  domainData.value.latestRecordTotal
  || filteredAlarms.value.length
  || filteredRisks.value.length
  || filteredHistoryRisks.value.length
  || severityStatistics.value.length
  || typeStatistics.value.length
  || series.value,
));
const displaySeries = computed<MuseumMetricSeriesVo>(() => {
  if (!series.value) return {} as MuseumMetricSeriesVo;
  if (!fieldCode.value) return series.value;
  return { ...series.value, fields: series.value.fields.filter((field) => field.fieldCode === fieldCode.value) };
});

const riskColumns: DataTableColumns<SafetyRiskVo> = [
  { title: '等级', key: 'level', width: 90, render: (row) => h(NTag, { type: levelType(row.level), bordered: false }, { default: () => levelLabel(row.level) }) },
  { title: '风险', key: 'title', minWidth: 220 },
  { title: '设备', key: 'deviceName', minWidth: 160, render: (row) => row.deviceName || row.deviceId },
  { title: '触发位置', key: 'locationSnapshotPath', minWidth: 200, render: (row) => row.locationSnapshotPath || row.locationSnapshotId || '--' },
  { title: '状态', key: 'status', width: 130, render: (row) => statusLabel(row.status) },
  { title: '时间', key: 'lastOccurredAt', width: 180, render: (row) => formatDateTime(row.lastOccurredAt) },
  { title: '操作', key: 'actions', width: 90, render: (row) => h(NButton, { text: true, type: 'primary', onClick: () => openRisk(row.id) }, { default: () => '查看' }) },
];
const alarmColumns: DataTableColumns<SafetyAlarmVo> = [
  { title: '等级', key: 'severity', width: 90, render: (row) => h(NTag, { type: row.severity === 'CRITICAL' || row.severity === 'MAJOR' ? 'error' : row.severity === 'WARNING' ? 'warning' : 'default', bordered: false }, { default: () => alarmSeverityLabel(row.severity) }) },
  { title: '告警', key: 'title', minWidth: 220 },
  { title: '设备', key: 'deviceName', minWidth: 160, render: (row) => row.deviceName || row.deviceId },
  { title: '指标字段', key: 'metricCode', minWidth: 170, render: (row) => `${row.metricCode || '--'} / ${fieldName(row.metricCode || '', row.fieldCode)}` },
  { title: '触发值', key: 'triggerValue', width: 110, render: (row) => row.triggerValue || '--' },
  { title: '触发位置', key: 'locationSnapshotPath', minWidth: 200, render: (row) => row.locationSnapshotPath || row.locationSnapshotId || '--' },
  { title: '最近发生', key: 'lastOccurredAt', width: 180, render: (row) => formatDateTime(row.lastOccurredAt) },
  { title: '操作', key: 'actions', width: 100, render: (row) => h(NButton, { text: true, type: 'primary', onClick: () => openAlarm(row) }, { default: () => row.riskId ? '处置' : '告警' }) },
];
const portfolioColumns: DataTableColumns<SafetyPortfolioVenueVo> = [
  { title: '场馆', key: 'name', fixed: 'left', width: 190, render: (row) => row.shortName || row.name },
  { title: '状态', key: 'state', width: 110, render: (row) => {
    const state = portfolioCell(row).state;
    return h(NTag, { type: portfolioStateType(state), bordered: false }, { default: () => portfolioStateLabel(state) });
  } },
  { title: '监测设备', key: 'configuredDevices', width: 105, render: (row) => portfolioCell(row).configuredDevices },
  { title: '在线', key: 'onlineDevices', width: 80, render: (row) => portfolioCell(row).onlineDevices },
  { title: '离线', key: 'offlineDevices', width: 80, render: (row) => portfolioCell(row).offlineDevices },
  { title: '异常', key: 'abnormalDevices', width: 80, render: (row) => portfolioCell(row).abnormalDevices },
  { title: '活动告警', key: 'activeAlarms', width: 100, render: (row) => portfolioCell(row).activeAlarms },
  { title: '活动风险', key: 'activeRisks', width: 100, render: (row) => portfolioCell(row).activeRisks },
  { title: '最近数据', key: 'latestDataAt', width: 180, render: (row) => portfolioCell(row).latestDataAt ? formatDateTime(portfolioCell(row).latestDataAt!) : '--' },
  { title: '操作', key: 'actions', fixed: 'right', width: 90, render: (row) => h(NButton, { text: true, type: 'primary', onClick: () => selectVenue(row.venueId) }, { default: () => '进入场馆' }) },
];

watch(metricCode, () => {
  const fields = fieldOptions.value;
  fieldCode.value = fields.length === 1 ? String(fields[0].value) : null;
});
watch(locationId, () => {
  if (deviceId.value && !deviceOptions.value.some((item) => item.value === deviceId.value)) deviceId.value = null;
});

async function loadCatalogs() {
  if (!canSelect.value) return;
  const [optionResponse, metricResponse] = await Promise.all([
    orgId.value ? ingestApi.recordFilterOptions({ orgId: orgId.value }) : Promise.resolve(null),
    ingestApi.standardMetrics(true),
  ]);
  options.value = optionResponse ? unwrapResponse<RecordFilterOptionsVo>(optionResponse) : { devices: [], locations: [] };
  metrics.value = unwrapResponse<StandardMetricDefinition[]>(metricResponse);
}

async function loadDomain() {
  if (!canSelect.value) return;
  if (metricCode.value && !availableMetrics.value.some((metric) => metric.code === metricCode.value)) {
    metricCode.value = null;
    fieldCode.value = null;
  }
  loading.value = true;
  error.value = '';
  series.value = null;
  selectedRecord.value = null;
  try {
    if (!orgId.value) {
      const value = unwrapResponse<SafetyPortfolioVo>(await safetyApi.portfolioOverview({
        domain: activeDomain.value,
        metricCodes: activeTabConfig.value.metricCodes?.join(','),
      }));
      portfolio.value = { ...emptyPortfolio(), ...value, venues: value?.venues || [] };
      domainData.value = emptyDomainData(activeDomain.value);
      return;
    }
    const value = unwrapResponse<SafetyDomainVo>(await safetyApi.domain(activeDomain.value, {
      orgId: orgId.value,
      locationId: locationId.value || undefined,
      deviceId: deviceId.value || undefined,
      metricCodes: (metricCode.value ? [metricCode.value] : activeTabConfig.value.metricCodes)?.join(','),
      pageNum: recordPage.value,
      pageSize: recordPageSize.value,
    }));
    domainData.value = {
      ...emptyDomainData(activeDomain.value),
      ...value,
      latestRecords: value?.latestRecords || [],
      activeRisks: value?.activeRisks || [],
      recentRisks: value?.recentRisks || [],
      activeAlarms: value?.activeAlarms || [],
      alarmsBySeverity: value?.alarmsBySeverity || {},
      alarmsByType: value?.alarmsByType || {},
    };
    recordPage.value = Number(value?.latestRecordPageNum || recordPage.value);
    recordPageSize.value = Number(value?.latestRecordPageSize || recordPageSize.value);
  } catch (cause: any) {
    domainData.value = emptyDomainData(activeDomain.value);
    error.value = cause?.message || '监测数据加载失败，请稍后重试。';
  } finally {
    loading.value = false;
  }
}

async function refresh() {
  if (!canSelect.value) return;
  recordPage.value = 1;
  await loadDomain();
  if (!orgId.value) return;
  const preferred = filteredRecords.value.find((item) => !metricCode.value || item.key === metricCode.value);
  if (preferred) await selectRecord(preferred);
}

function handleRecordPageSize() {
  recordPage.value = 1;
  void loadDomain();
}

async function handleTabChange() {
  recordPage.value = 1;
  metricCode.value = null;
  fieldCode.value = null;
  await router.replace({ query: { ...route.query, tab: activeTab.value } });
  await loadDomain();
}

async function handleVenueChange() {
  locationId.value = null;
  deviceId.value = null;
  metricCode.value = null;
  fieldCode.value = null;
  recordPage.value = 1;
  await router.replace({ query: { ...route.query, orgId: orgId.value || undefined, locationId: undefined, tab: activeTab.value } });
  try { await loadCatalogs(); } catch (cause: any) { catalogError.value = cause?.message || '当前场馆筛选目录加载失败。'; }
  await loadDomain();
}

function selectVenue(value: string) {
  orgId.value = value;
  void handleVenueChange();
}

function portfolioCell(venue: SafetyPortfolioVenueVo): SafetyPortfolioDomainVo {
  return venue.domains[activeDomain.value] || {
    domain: activeDomain.value, state: 'UNCONFIGURED', configuredDevices: 0, onlineDevices: 0,
    offlineDevices: 0, abnormalDevices: 0, activeRisks: 0, majorRisks: 0,
    activeAlarms: 0, highestRiskLevel: null, latestDataAt: null,
  };
}
function portfolioStateLabel(state: SafetyVenueDomainState) { return ({ NORMAL: '正常', RISK: '存在风险', DATA_INTERRUPTED: '数据中断', UNCONFIGURED: '未配置' } as Record<string, string>)[state] || state; }
function portfolioStateType(state: SafetyVenueDomainState): 'default' | 'success' | 'warning' | 'error' { return state === 'RISK' ? 'error' : state === 'DATA_INTERRUPTED' ? 'warning' : state === 'NORMAL' ? 'success' : 'default'; }

async function selectRecord(record: IngestRecordVo) {
  if (!canSelect.value || !range.value) return;
  selectedRecord.value = record;
  metricCode.value = record.key;
  loading.value = true;
  error.value = '';
  try {
    series.value = unwrapResponse<MuseumMetricSeriesVo>(await ingestApi.recordSeries({
      deviceId: record.deviceId,
      key: record.key,
      from: range.value[0],
      to: range.value[1],
      mode: 'STATISTICS',
      maxPoints: 1000,
      bucketMinutes: showEnergyGranularity.value ? (energyGranularity.value === 'MONTH' ? 43200 : 1440) : undefined,
    }));
  } catch (cause: any) {
    series.value = null;
    error.value = cause?.message || '趋势数据加载失败，请稍后重试。';
  } finally {
    loading.value = false;
  }
}

function summaryField(): MuseumMetricFieldSeriesVo | MuseumMetricSeriesVo | null {
  if (!series.value) return null;
  return (fieldCode.value ? series.value.fields.find((item) => item.fieldCode === fieldCode.value) : null) || series.value.fields[0] || series.value;
}
function summaryValue(key: 'latestValue' | 'minValue' | 'maxValue' | 'averageValue') {
  const value = summaryField()?.[key];
  return value == null ? '--' : `${Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 3 })}${series.value?.unit ? ` ${series.value.unit}` : ''}`;
}
const summaryChangeRate = computed(() => {
  const value = summaryField()?.changeRate;
  return value == null ? '--' : `${Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 2 })}%`;
});
function displayRecordValue(record: IngestRecordVo) {
  const values = Object.entries(record.values || {});
  if (values.length) return values.map(([code, value]) => `${fieldName(record.key, code)} ${value}`).join(' · ');
  if (record.booleanValue != null) return record.booleanValue ? '是' : '否';
  return record.stringValue ?? '--';
}
function fieldName(metric: string, field: string) {
  return metrics.value.find((item) => item.code === metric)?.valueFields.find((item) => item.code === field)?.name || field;
}
function levelLabel(level: string) { return ({ NOTICE: '提示', WARNING: '警告', MAJOR: '重大', CRITICAL: '严重' } as Record<string, string>)[level] || level; }
function levelType(level: string): 'default' | 'warning' | 'error' { return level === 'CRITICAL' || level === 'MAJOR' ? 'error' : level === 'WARNING' ? 'warning' : 'default'; }
function statusLabel(status: string) { return ({ PENDING_CONFIRMATION: '待确认', PROCESSING: '处理中', RECOVERED_PENDING_CONFIRMATION: '已恢复待确认', CLOSED: '已关闭' } as Record<string, string>)[status] || status; }
function alarmSeverityLabel(severity: string) { return ({ NOTICE: '提示', INFO: '提示', WARNING: '警告', MAJOR: '重大', CRITICAL: '严重' } as Record<string, string>)[severity] || severity; }
function openAlarm(row: SafetyAlarmVo) {
  if (row.riskId) openRisk(row.riskId);
  else router.push({ path: '/museum-device/alarm', query: { eventId: row.id } });
}
function openSeriesPoint() {
  if (!selectedRecord.value) return;
  const risk = domainData.value.activeRisks.find((item) => item.deviceId === selectedRecord.value?.deviceId);
  if (risk) openRisk(risk.id);
  else router.push({ path: '/museum-device/records', query: { deviceId: selectedRecord.value.deviceId, key: selectedRecord.value.key } });
}

onMounted(async () => {
  if (!canSelect.value) return;
  try {
    const venues = await loadVenueCatalog();
    if (!orgId.value && venues.length === 1) orgId.value = venues[0].id;
    await loadCatalogs();
  } catch (cause: any) { catalogError.value = cause?.message || '筛选目录加载失败，仍可查看领域汇总。'; }
  await loadDomain();
});
onActivated(() => {
  if (firstActivation) { firstActivation = false; return; }
  if (!canSelect.value) return;
  const nextOrgId = (route.query.orgId as string) || null;
  const nextTab = props.tabs.find((tab) => (tab.key || tab.domain) === route.query.tab || tab.domain === route.query.tab);
  orgId.value = nextOrgId;
  if (nextTab) activeTab.value = nextTab.key || nextTab.domain;
  locationId.value = null;
  deviceId.value = null;
  metricCode.value = null;
  fieldCode.value = null;
  void loadCatalogs()
    .catch((cause: any) => { catalogError.value = cause?.message || '筛选目录加载失败，仍可查看领域汇总。'; })
    .then(refresh);
});
</script>

<style scoped>
.domain-page { width: 100%; height: 100%; min-height: 0; overflow: auto; }
.domain-card { min-height: 100%; }
.page-title { font-size: 18px; font-weight: 700; }
.scope-toolbar { display: grid; grid-template-columns: minmax(220px, 320px) 1fr; gap: 12px; align-items: center; margin: 12px 0; }
.scope-toolbar > span { color: #697386; font-size: 13px; }
.filters { display: grid; grid-template-columns: minmax(180px, 1.2fr) minmax(160px, 1fr) minmax(180px, 1fr) 140px minmax(280px, 1.4fr) auto; gap: 10px; align-items: center; margin: 12px 0 16px; }
.state-alert { margin-bottom: 12px; }
.location-changes { margin: 8px 0 0; padding-left: 20px; }
.device-summary-grid { margin-bottom: 12px; }
.device-summary-grid--structural :deep(.n-card) { border-top: 3px solid #2080f0; }
.device-summary-grid--incident :deep(.n-card) { border-top: 3px solid #d03050; }
.device-summary-grid--electric :deep(.n-card) { border-top: 3px solid #f0a020; }
.stat-hint { margin-top: 6px; color: #697386; font-size: 12px; }
.profile-heading { display: flex; justify-content: space-between; gap: 12px; align-items: center; margin-bottom: 10px; }
.profile-heading strong, .profile-heading span { display: block; }
.profile-heading span { margin-top: 3px; color: #697386; font-size: 12px; }
.portfolio-comparison { margin-top: 0; }
.alarm-statistics > :deep(.n-card__content) > div { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; margin: 6px 0; }
.alarm-statistics > :deep(.n-card__content) > div > span:first-child { width: 52px; color: #7a8494; }
.alarm-timeline { margin-top: 18px; }
.latest-grid { margin-bottom: 16px; }
.record-summary { margin: 4px 0 12px; color: #7a8494; font-size: 13px; }
.record-pagination { justify-content: flex-end; margin: 4px 0 16px; }
.metric-card { cursor: pointer; min-height: 126px; }
.metric-card__name { font-weight: 600; }
.metric-card__value { margin: 12px 0; color: #2080f0; font-size: 20px; font-weight: 700; }
.metric-card__meta { color: #7a8494; font-size: 12px; line-height: 1.6; }
.section-card { margin-top: 16px; }
.summary-grid { margin-bottom: 12px; }
.domain-empty { padding: 72px 0; }
.empty { padding: 28px 0; }
@media (max-width: 1280px) { .filters { grid-template-columns: repeat(3, minmax(180px, 1fr)); } }
@media (max-width: 720px) { .scope-toolbar { grid-template-columns: 1fr; } }
</style>
