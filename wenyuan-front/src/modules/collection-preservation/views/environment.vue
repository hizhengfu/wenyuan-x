<template>
  <NCard title="实时数据" size="small" :bordered="false">
    <template #header-extra>
      <NButton size="small" :loading="loading" @click="loadCards">刷新</NButton>
    </template>

    <NSpace class="pv-environment-filters" align="center">
      <WarehouseTreeSelect
        v-model:value="selectedWarehouseId"
        :scene-type="null"
        class="pv-environment-warehouse-filter"
        placeholder="全部保管/展陈位置"
        clearable
        filterable
        @loaded="onWarehousesLoaded"
      />
      <NInput v-model:value="keyword" clearable placeholder="搜索馆、直接位置、监测位置或指标" style="width: 300px" />
      <NRadioGroup v-model:value="aggregationDimension" size="small">
        <NRadioButton value="DEVICE">按设备</NRadioButton>
        <NRadioButton value="LOCATION">按位置</NRadioButton>
      </NRadioGroup>
      <NSelect
        v-model:value="metricFilter"
        :options="metricOptions"
        clearable
        filterable
        placeholder="全部指标"
        style="width: 210px"
      />
      <NSelect
        v-model:value="valueOperator"
        :options="valueOperatorOptions"
        clearable
        placeholder="数值条件"
        style="width: 125px"
        @update:value="onValueOperatorChange"
      />
      <NInputNumber
        v-if="valueOperator"
        v-model:value="valueFilterValue"
        :placeholder="valueOperator === 'BETWEEN' ? '区间下限' : '比较值'"
        clearable
        style="width: 130px"
      />
      <NInputNumber
        v-if="valueOperator === 'BETWEEN'"
        v-model:value="valueFilterValue2"
        placeholder="区间上限"
        clearable
        style="width: 130px"
      />
      <NSelect v-model:value="statusFilter" :options="statusOptions" style="width: 150px" />
      <NText depth="3">
        共 {{ filteredLocations.length }} {{ aggregationDimension === 'DEVICE' ? '个设备/位置组合' : '个位置' }}
      </NText>
    </NSpace>

    <NAlert v-if="cardError" type="error" :bordered="false">{{ cardError }}</NAlert>
    <NSpin :show="loading">
      <NGrid
        v-if="filteredLocations.length"
        class="pv-environment-grid"
        cols="1 s:2 m:3"
        responsive="screen"
        :x-gap="14"
        :y-gap="14"
      >
        <NGi v-for="location in filteredLocations" :key="location.key" class="pv-environment-grid__item">
          <NCard
            class="pv-environment-card"
            :class="[locationStatusClass(location), { 'has-thumbnail': location.thumbnail }]"
            size="small"
            hoverable
            embedded
            @click="openHistory(location)"
          >
            <template #header>
              <div class="pv-environment-card__header">
                <div class="pv-environment-card__heading">
                  <div class="pv-environment-card__title">{{ locationTitle(location) }}</div>
                  <NText depth="3" class="pv-environment-card__path" :title="locationPath(location)">
                    {{ locationPath(location) }}
                  </NText>
                </div>
                <div class="pv-environment-card__actions">
                  <NTooltip trigger="hover">
                    <template #trigger>
                      <NIcon
                        class="pv-environment-card__history-icon"
                        size="18"
                        role="img"
                        aria-label="查看该位置全部历史数据"
                      >
                        <HIcon icon="mdi:chart-timeline-variant" />
                      </NIcon>
                    </template>
                    点击查看该位置全部历史数据
                  </NTooltip>
                  <NTooltip v-if="location.activeAlarmCount" trigger="hover">
                    <template #trigger>
                      <NTag :type="locationStatusTagType(location)" size="small" round>
                        {{ activeAlarmLabel(location) }}
                      </NTag>
                    </template>
                    <div>{{ location.activeAlarmTitle || '设备告警' }}</div>
                    <div v-if="location.activeAlarmCode">告警编码：{{ location.activeAlarmCode }}</div>
                    <div v-if="location.activeAlarmAt">最近发生：{{ formatTime(location.activeAlarmAt) }}</div>
                  </NTooltip>
                  <NTag
                    v-else-if="shouldShowLocationStatus(location)"
                    :type="locationStatusTagType(location)"
                    size="small"
                    round
                  >
                    {{ locationStatusLabel(location) }}
                  </NTag>
                </div>
              </div>
            </template>

            <div v-if="missingStandardCount(location.metrics)" class="pv-environment-card__standard-hint">
              <span>{{ missingStandardCount(location.metrics) }} 项指标未匹配生效标准，暂无法判断是否达标</span>
              <NButton text size="small" @click.stop="openHistory(location)">查看原因</NButton>
            </div>

            <div v-if="location.thumbnail" class="pv-environment-card__image">
              <HImage
                :src="fileUrl(location.thumbnail)"
                :alt="location.warehouseName || '位置缩略图'"
                :revision="cardRequest"
                preview-disabled
              />
            </div>

            <div v-if="location.thumbnail || aggregationDimension === 'LOCATION'" class="pv-environment-card__summary">
              <span v-if="aggregationDimension === 'LOCATION'">{{ location.devices.length }} 台设备</span>
              <span>{{ location.pointNames.length }} 个监测位置</span>
              <span>{{ uniqueMetricCount(location) }} 项指标</span>
              <span v-if="location.thumbnail">{{ location.orgName || '--' }}</span>
            </div>

            <div v-if="location.thumbnail && location.metrics.length" class="pv-environment-card__visual-metrics">
              <div
                v-for="metric in displayMetrics(location, 3)"
                :key="`${metric.sensorPointId}-${metric.deviceId || 'NO_DEVICE'}-${metric.sensorId}`"
                class="pv-environment-visual-metric"
              >
                <div class="pv-environment-visual-metric__value">
                  <strong
                    >{{ formatValue(metric.dataValue) }}<small>{{ metric.sensorUnit || '' }}</small></strong
                  >
                  <span v-if="shouldShowMetricStatus(metric)" :title="environmentStatusDescription(metric, elapsedMs)"
                    ><i :class="`is-${cardStatus(metric).toLowerCase()}`" />{{ statusLabel(metric) }}</span
                  >
                </div>
                <div class="pv-environment-visual-metric__name">{{ metric.sensorName || '未知指标' }}</div>
                <div class="pv-environment-metric__evidence">{{ sampleTimeLabel(metric) }}</div>
                <div
                  v-if="aggregationDimension === 'LOCATION' && (metric.deviceName || metric.deviceId)"
                  class="pv-environment-metric__device"
                >
                  {{ metric.deviceName || metric.deviceId }}
                </div>
                <div v-if="hasConfiguredRange(metric)" class="pv-environment-visual-metric__range">
                  当前标准区间：{{ safeRange(metric) }}
                </div>
              </div>
            </div>
            <NEmpty
              v-else-if="location.thumbnail"
              class="pv-environment-card__empty"
              size="small"
              description="暂无监测指标或采集数据"
            />
            <NText
              v-if="location.thumbnail && uniqueMetricCount(location) > 3"
              depth="3"
              class="pv-environment-card__more"
            >
              另有 {{ uniqueMetricCount(location) - 3 }} 项指标，点击查看全部
            </NText>

            <template v-else>
              <div v-if="location.metrics.length" class="pv-environment-card__plain-metrics">
                <div
                  v-for="metric in displayMetrics(location, 4)"
                  :key="`${metric.sensorPointId}-${metric.deviceId || 'NO_DEVICE'}-${metric.sensorId}`"
                  class="pv-environment-plain-metric"
                >
                  <div class="pv-environment-plain-metric__title">
                    <span>
                      <NIcon size="19"><HIcon :icon="sensorIcon(metric)" /></NIcon>
                      {{ metric.sensorName || '未知指标' }}
                    </span>
                    <NTag v-if="shouldShowMetricStatus(metric)" :title="environmentStatusDescription(metric, elapsedMs)" :type="cardStatusTagType(metric)" size="small" round>
                      {{ statusLabel(metric) }}
                    </NTag>
                  </div>
                  <div
                    v-if="aggregationDimension === 'LOCATION' && (metric.deviceName || metric.deviceId)"
                    class="pv-environment-metric__device"
                  >
                    {{ metric.deviceName || metric.deviceId }}
                  </div>
                  <strong
                    >{{ formatValue(metric.dataValue) }}<small>{{ metric.sensorUnit || '' }}</small></strong
                  >
                  <div class="pv-environment-metric__evidence">{{ sampleTimeLabel(metric) }}</div>
                  <div v-if="hasConfiguredRange(metric)" class="pv-environment-plain-metric__range">
                    当前标准区间：{{ safeRange(metric) }}
                  </div>
                </div>
              </div>
              <NEmpty v-else class="pv-environment-card__empty" size="small" description="暂无监测指标或采集数据" />
              <NText v-if="uniqueMetricCount(location) > 4" depth="3" class="pv-environment-card__more">
                另有 {{ uniqueMetricCount(location) - 4 }} 项指标，点击查看全部
              </NText>
              <div class="pv-environment-card__collected-at">采集时间：{{ formatTime(location.latestAt) }}</div>
            </template>
          </NCard>
        </NGi>
      </NGrid>
      <NEmpty v-else-if="!cardError" description="暂无符合条件的设备/位置监测数据" />
    </NSpin>

    <NDrawer v-model:show="historyShow" width="min(1120px, 96vw)" placement="right">
      <NDrawerContent :title="historyTitle" closable>
        <template v-if="selectedLocation">
          <NDescriptions :column="2" bordered size="small" label-placement="left">
            <NDescriptionsItem label="所在区域" :span="2">
              {{ locationPath(selectedLocation) }}
            </NDescriptionsItem>
            <NDescriptionsItem label="直接位置">{{ selectedLocation.warehouseName || '--' }}</NDescriptionsItem>
            <NDescriptionsItem label="监测位置">
              {{ selectedLocation.pointNames.join('、') || '--' }}
            </NDescriptionsItem>
            <NDescriptionsItem label="设备">
              {{ locationDeviceText(selectedLocation) }}
            </NDescriptionsItem>
            <NDescriptionsItem v-if="shouldShowLocationStatus(selectedLocation)" label="环境评价">
              <NTag :type="locationStatusTagType(selectedLocation)" size="small">
                {{ locationStatusLabel(selectedLocation) }}
              </NTag>
            </NDescriptionsItem>
            <NDescriptionsItem v-if="selectedLocation.thumbnail" label="位置图片" :span="2">
              <HImage
                class="pv-environment-history__image"
                :src="fileUrl(selectedLocation.thumbnail)"
                :alt="selectedLocation.warehouseName || '位置缩略图'"
                :revision="historyGeneration"
              />
            </NDescriptionsItem>
            <NDescriptionsItem label="最近采集">{{ formatTime(selectedLocation.latestAt) }}</NDescriptionsItem>
          </NDescriptions>

          <EnvironmentAssessmentDetails :metrics="selectedLocation.metrics" :elapsed-ms="elapsedMs" />

          <NSpace class="pv-environment-history__toolbar" align="center">
            <NDatePicker v-model:value="historyRange" type="datetimerange" clearable style="width: 360px" />
            <NSelect
              v-model:value="historyDeviceId"
              :options="historyDeviceOptions"
              clearable
              filterable
              placeholder="该位置全部设备"
              style="width: 220px"
            />
            <NSelect
              v-model:value="historySensorId"
              :options="historyMetricOptions"
              clearable
              filterable
              placeholder="全部指标"
              style="width: 210px"
            />
            <NSelect
              v-model:value="historyValueOperator"
              :options="valueOperatorOptions"
              clearable
              placeholder="数值条件"
              style="width: 125px"
              @update:value="onHistoryValueOperatorChange"
            />
            <NInputNumber
              v-if="historyValueOperator"
              v-model:value="historyValue"
              :placeholder="historyValueOperator === 'BETWEEN' ? '区间下限' : '比较值'"
              clearable
              style="width: 130px"
            />
            <NInputNumber
              v-if="historyValueOperator === 'BETWEEN'"
              v-model:value="historyValue2"
              placeholder="区间上限"
              clearable
              style="width: 130px"
            />
            <NButton type="primary" :loading="historyLoading" @click="loadHistory">查询</NButton>
          </NSpace>

          <NAlert v-if="historyError" type="error" :bordered="false">{{ historyError }}</NAlert>
          <NAlert v-if="historyHasUnverifiedRows" type="warning" :bordered="false">
            部分曲线仅按设备检索，采样时所在库房无法核实；这些读数不代表当前库房的历史环境达标情况。
          </NAlert>
          <NTabs v-model:value="historyTab" type="line" animated>
            <NTabPane name="trend" tab="历史趋势" display-directive="show">
              <NSpin :show="historyLoading">
                <LocationTrendChart v-if="historyChartRows.length" :data="historyChartRows" :metrics="historyMetrics" />
                <NEmpty
                  v-else-if="!historyError"
                  :description="historyNeedsQuery ? '请点击查询加载当前条件的数据' : '所选时间范围内暂无历史数据'"
                />
              </NSpin>
            </NTabPane>
            <NTabPane name="detail" tab="历史明细" display-directive="show">
              <div class="pv-environment-history__table">
                <HPageDataTable
                  ref="historyTableRef"
                  v-model:data="historyTableRows"
                  :clear-selection-on-fetch="true"
                  :columns="historyColumns"
                  :fetch-data="fetchHistoryPage"
                  :params="historyQueryParams"
                  row-key-field="id"
                  :show-header="false"
                  :show-toolbar="false"
                  :show-operate="false"
                  :is-add="false"
                  :is-edit="false"
                  :is-delete="false"
                  :is-export="false"
                  :init-query="false"
                  :scroll-x="780"
                />
              </div>
            </NTabPane>
          </NTabs>
        </template>
      </NDrawerContent>
    </NDrawer>
  </NCard>
</template>

<script setup lang="ts">
import dayjs from 'dayjs';
import { computed, nextTick, onMounted, onBeforeUnmount, reactive, ref, watch, onActivated, onDeactivated } from 'vue';
import {
  NAlert,
  NButton,
  NCard,
  NDatePicker,
  NDescriptions,
  NDescriptionsItem,
  NDrawer,
  NDrawerContent,
  NEmpty,
  NGi,
  NGrid,
  NIcon,
  NInput,
  NInputNumber,
  NRadioButton,
  NRadioGroup,
  NSelect,
  NSpace,
  NSpin,
  NTabPane,
  NTabs,
  NTag,
  NText,
  useMessage,
} from 'naive-ui';
import { historyApi } from '../api/history';
import { fileUrl } from '@/utils';
import HIcon from '@/components/basic/DynamicIcon.vue';
import WarehouseTreeSelect from '@/modules/collection/components/WarehouseTreeSelect.vue';
import { collectWarehouseSubtreeIds } from '@/modules/collection/utils/warehouseTree';
import type { WarehouseVo } from '@/modules/collection/typings/collection';
import LocationTrendChart from '../components/LocationTrendChart.vue';
import EnvironmentAssessmentDetails from '../components/EnvironmentAssessmentDetails.vue';
import type { EnvironmentCardVo, HistoryDataVo, PvAlarmLevel } from '../typings';

import {
  environmentCardStatus,
  hasLocationAssociation,
  locationAssociationLabel,
  aggregateEnvironmentStatus,
  sampleStatusLabels,
  environmentStatusDescription,
  missingStandardCount,
  type EnvironmentCardStatus as CardStatus,
} from '../utils/environmentSample';
type LocationStatus = CardStatus | 'PARTIAL';
type NumericValueOperator = 'GT' | 'LT' | 'BETWEEN';
type AggregationDimension = 'DEVICE' | 'LOCATION';

interface EnvironmentDeviceSummary {
  id: string;
  name?: string | null;
}

interface EnvironmentLocationGroup {
  key: string;
  aggregationDimension: AggregationDimension;
  orgId?: string;
  orgName?: string;
  warehouseId?: string;
  warehouseCode?: string;
  warehouseName?: string;
  thumbnail?: string;
  deviceId?: string | null;
  deviceName?: string | null;
  devices: EnvironmentDeviceSummary[];
  pointNames: string[];
  metrics: EnvironmentCardVo[];
  latestAt?: number | null;
  activeAlarmCount: number;
  activeAlarmLevel?: PvAlarmLevel | null;
  activeAlarmTitle?: string | null;
  activeAlarmCode?: string | null;
  activeAlarmAt?: number | null;
  activeAlarmSourceKeys: Set<string>;
}

const message = useMessage();
const loading = ref(false);
const cardError = ref('');
let disposed = false;
let active = true;
const cards = ref<EnvironmentCardVo[]>([]);
const elapsedMs = ref(0);
let loadedAt = performance.now();
let lastAttemptAt = loadedAt;
let cardRequest = 0;
let ageTimer: ReturnType<typeof setInterval> | undefined;
const keyword = ref('');
const aggregationDimension = ref<AggregationDimension>('DEVICE');
const metricFilter = ref<string | null>(null);
const valueOperator = ref<NumericValueOperator | null>(null);
const valueFilterValue = ref<number | null>(null);
const valueFilterValue2 = ref<number | null>(null);
const selectedWarehouseId = ref<string | null>(null);
const warehouseRows = ref<WarehouseVo[]>([]);
const statusFilter = ref<'ALL' | LocationStatus>('ALL');
const statusOptions = [
  { label: '全部状态', value: 'ALL' },
  { label: '正常', value: 'SAFE' },
  { label: '存在预警', value: 'UNSAFE' },
  { label: '数据可疑', value: 'SUSPECT' },
  { label: '数据不完整', value: 'PARTIAL' },
  { label: '阈值冲突', value: 'CONFLICT' },
  { label: '暂无数据', value: 'NO_DATA' },
  ...Object.entries(sampleStatusLabels).map(([value, label]) => ({ value, label })),
];
const valueOperatorOptions = [
  { label: '大于', value: 'GT' },
  { label: '小于', value: 'LT' },
  { label: '区间内（含边界）', value: 'BETWEEN' },
];

const metricOptions = computed(() => {
  const options = new Map<string, { label: string; value: string }>();
  cards.value.forEach((card) => {
    if (!card.sensorId) return;
    options.set(card.sensorId, {
      label: `${card.sensorName || card.sensorId}（${card.sensorId}）`,
      value: card.sensorId,
    });
  });
  return [...options.values()].sort((a, b) => a.label.localeCompare(b.label, 'zh-CN'));
});

const selectedWarehouseIds = computed(() =>
  selectedWarehouseId.value ? collectWarehouseSubtreeIds(warehouseRows.value, selectedWarehouseId.value) : null,
);

const warehousePathNames = computed(() => {
  const rowsById = new Map(
    warehouseRows.value
      .filter((row): row is WarehouseVo & { id: string } => Boolean(row.id))
      .map((row) => [row.id, row]),
  );
  const paths = new Map<string, string[]>();

  function resolve(id: string, visiting = new Set<string>()): string[] {
    const cached = paths.get(id);
    if (cached) return cached;
    const row = rowsById.get(id);
    if (!row || visiting.has(id)) return [];

    const nextVisiting = new Set(visiting).add(id);
    const parents = row.parentId && row.parentId !== '0' ? resolve(row.parentId, nextVisiting) : [];
    const path = row.name ? [...parents, row.name] : parents;
    paths.set(id, path);
    return path;
  }

  rowsById.forEach((_, id) => resolve(id));
  return paths;
});

const locations = computed<EnvironmentLocationGroup[]>(() => {
  const grouped = new Map<string, EnvironmentLocationGroup>();
  const dimension = aggregationDimension.value;
  cards.value.forEach((card) => {
    const locationKey = card.warehouseId ? `LOCATION:${card.warehouseId}` : `POINT:${card.sensorPointId}`;
    const key = dimension === 'DEVICE' ? `${locationKey}:DEVICE:${card.deviceId || 'UNASSIGNED'}` : locationKey;
    let group = grouped.get(key);
    if (!group) {
      group = {
        key,
        aggregationDimension: dimension,
        orgId: card.orgId,
        orgName: card.orgName,
        warehouseId: card.warehouseId,
        warehouseCode: card.warehouseCode,
        warehouseName: card.warehouseName,
        thumbnail: card.warehouseThumbnail,
        deviceId: dimension === 'DEVICE' ? card.deviceId : null,
        deviceName: dimension === 'DEVICE' ? card.deviceName : null,
        devices: [],
        pointNames: [],
        metrics: [],
        latestAt: null,
        activeAlarmCount: 0,
        activeAlarmLevel: null,
        activeAlarmTitle: null,
        activeAlarmCode: null,
        activeAlarmAt: null,
        activeAlarmSourceKeys: new Set<string>(),
      };
      grouped.set(key, group);
    }
    if (card.deviceId && !group.devices.some((device) => device.id === card.deviceId)) {
      group.devices.push({ id: card.deviceId, name: card.deviceName });
    }
    if (card.sensorId) group.metrics.push(card);
    const alarmSourceKey = `${card.sensorPointId}:DEVICE:${card.deviceId || 'UNASSIGNED'}`;
    if ((card.activeAlarmCount || 0) > 0 && !group.activeAlarmSourceKeys.has(alarmSourceKey)) {
      group.activeAlarmSourceKeys.add(alarmSourceKey);
      group.activeAlarmCount += card.activeAlarmCount || 0;
      if (shouldUseAlarmSummary(group, card)) {
        group.activeAlarmLevel = card.activeAlarmLevel;
        group.activeAlarmTitle = card.activeAlarmTitle;
        group.activeAlarmCode = card.activeAlarmCode;
        group.activeAlarmAt = card.activeAlarmAt;
      }
    }
    if (card.sensorPointName && !group.pointNames.includes(card.sensorPointName)) {
      group.pointNames.push(card.sensorPointName);
    }
    if (card.dateTime && (!group.latestAt || dayjs(card.dateTime).isAfter(group.latestAt))) {
      group.latestAt = card.dateTime;
    }
  });
  return [...grouped.values()]
    .map((group) => ({
      ...group,
      devices: group.devices.sort((a, b) => (a.name || a.id).localeCompare(b.name || b.id, 'zh-CN')),
      pointNames: group.pointNames.sort((a, b) => a.localeCompare(b, 'zh-CN')),
      metrics: group.metrics.sort(
        (a, b) =>
          (a.sensorSort ?? Number.MAX_SAFE_INTEGER) - (b.sensorSort ?? Number.MAX_SAFE_INTEGER) ||
          (a.sensorName || '').localeCompare(b.sensorName || '', 'zh-CN') ||
          (a.sensorPointName || '').localeCompare(b.sensorPointName || '', 'zh-CN'),
      ),
    }))
    .sort(
      (a, b) =>
        (a.orgName || '').localeCompare(b.orgName || '', 'zh-CN') ||
        (a.warehouseName || '').localeCompare(b.warehouseName || '', 'zh-CN') ||
        (a.deviceName || a.deviceId || '').localeCompare(b.deviceName || b.deviceId || '', 'zh-CN'),
    );
});

const filteredLocations = computed(() => {
  const q = keyword.value.trim().toLowerCase();
  const warehouseIds = selectedWarehouseIds.value;
  const hasMetricFilter = Boolean(metricFilter.value || valueOperator.value);
  return locations.value
    .map((location) => ({
      ...location,
      metrics: location.metrics.filter(
        (metric) =>
          (!metricFilter.value || metric.sensorId === metricFilter.value) &&
          matchesNumericValue(metric.dataValue, valueOperator.value, valueFilterValue.value, valueFilterValue2.value),
      ),
    }))
    .filter((location) => {
      if (warehouseIds && (!location.warehouseId || !warehouseIds.has(location.warehouseId))) return false;
      if (hasMetricFilter && !location.metrics.length) return false;
      if (statusFilter.value !== 'ALL' && locationStatus(location) !== statusFilter.value) return false;
      if (!q) return true;
      return [
        locationPath(location),
        location.orgName,
        location.warehouseCode,
        location.warehouseName,
        location.deviceId,
        location.deviceName,
        ...location.devices.flatMap((device) => [device.id, device.name]),
        location.activeAlarmTitle,
        location.activeAlarmCode,
        ...location.pointNames,
        ...location.metrics.flatMap((metric) => [metric.sensorName, metric.sensorCode]),
      ].some((value) => value?.toLowerCase().includes(q));
    });
});

function onWarehousesLoaded(rows: WarehouseVo[]) {
  warehouseRows.value = rows;
}

function matchesNumericValue(
  value: number | null | undefined,
  operator: NumericValueOperator | null,
  first: number | null,
  second: number | null,
): boolean {
  if (!operator) return true;
  if (first == null) return false;
  if (value == null || !Number.isFinite(value)) return false;
  if (operator === 'GT') return value > first;
  if (operator === 'LT') return value < first;
  if (second == null) return false;
  return value >= Math.min(first, second) && value <= Math.max(first, second);
}

function onValueOperatorChange(operator: NumericValueOperator | null) {
  valueOperator.value = operator;
  if (!operator) {
    valueFilterValue.value = null;
    valueFilterValue2.value = null;
    return;
  }
  if (valueFilterValue.value == null) valueFilterValue.value = 0;
  if (operator === 'BETWEEN') {
    if (valueFilterValue2.value == null) valueFilterValue2.value = valueFilterValue.value;
  } else {
    valueFilterValue2.value = null;
  }
}

function cardStatus(card: EnvironmentCardVo): CardStatus {
  return environmentCardStatus(card, elapsedMs.value);
}

function hasConfiguredRange(card: EnvironmentCardVo): boolean {
  return card.thresholdConflict === true || card.safeMinValue != null || card.safeMaxValue != null;
}

function shouldShowMetricStatus(card: EnvironmentCardVo): boolean {
  // 缺少标准在位置卡片中汇总，逐项原因在详情中展示。
  return cardStatus(card) !== 'UNCONFIGURED';
}

function metricRisk(card: EnvironmentCardVo): number {
  const alarmRanks: Record<string, number> = { RED: 60, ORANGE: 50, YELLOW: 40, BLUE: 30 };
  if (card.alarmLevel && alarmRanks[card.alarmLevel]) return alarmRanks[card.alarmLevel];
  const statusRanks: Partial<Record<CardStatus, number>> = {
    UNSAFE: 25,
    CONFLICT: 24,
    SUSPECT: 22,
    UNCONFIGURED: 20,
    NO_DATA: 10,
    SAFE: 0,
  };
  return statusRanks[cardStatus(card)] ?? 23;
}

function uniqueMetricCount(location: EnvironmentLocationGroup): number {
  return new Set(location.metrics.map((metric) => metric.sensorId).filter(Boolean)).size;
}

/** 同一位置有多个监测位置时，每个指标优先展示风险最高的一条，其次取采集时间更新的一条。 */
function displayMetrics(location: EnvironmentLocationGroup, limit: number): EnvironmentCardVo[] {
  const selected = new Map<string, EnvironmentCardVo>();
  location.metrics.forEach((metric) => {
    const sensorId = metric.sensorId;
    if (!sensorId) return;
    const current = selected.get(sensorId);
    const newer = !current?.dateTime || (metric.dateTime && dayjs(metric.dateTime).isAfter(current.dateTime));
    if (!current || metricRisk(metric) > metricRisk(current) || (metricRisk(metric) === metricRisk(current) && newer)) {
      selected.set(sensorId, metric);
    }
  });
  return [...selected.values()]
    .sort(
      (a, b) =>
        (a.sensorSort ?? Number.MAX_SAFE_INTEGER) - (b.sensorSort ?? Number.MAX_SAFE_INTEGER) ||
        (a.sensorName || '').localeCompare(b.sensorName || '', 'zh-CN'),
    )
    .slice(0, limit);
}

function cardStatusTagType(card: EnvironmentCardVo): 'success' | 'error' | 'warning' | 'info' | 'default' {
  const status = cardStatus(card);
  if (status === 'SAFE') return 'success';
  if (status === 'UNSAFE') return 'error';
  if (['UNCONFIGURED', 'FRESHNESS_UNKNOWN', 'EVIDENCE_UNKNOWN'].includes(status)) return 'info';
  if (status !== 'NO_DATA') return 'warning';
  return 'default';
}

function locationTitle(location: EnvironmentLocationGroup): string {
  if (location.aggregationDimension === 'LOCATION') {
    if (location.warehouseName) return location.warehouseName;
    if (!location.pointNames.length) return '未命名位置';
    if (location.pointNames.length === 1) return location.pointNames[0];
    return `${location.pointNames[0]} 等 ${location.pointNames.length} 个监测位置`;
  }
  if (location.deviceName) return location.deviceName;
  if (location.deviceId) return location.deviceId;
  if (!location.pointNames.length) return '未命名监测位置';
  if (location.pointNames.length === 1) return location.pointNames[0];
  return `${location.pointNames[0]} 等 ${location.pointNames.length} 个监测位置`;
}

function locationDeviceText(location: EnvironmentLocationGroup): string {
  if (location.aggregationDimension === 'DEVICE') {
    return location.deviceName || location.deviceId || '未关联设备';
  }
  if (!location.devices.length) return '未关联设备';
  const labels = location.devices.map((device) => device.name || device.id);
  if (labels.length <= 4) return labels.join('、');
  return `${labels.slice(0, 4).join('、')} 等 ${labels.length} 台设备`;
}

function locationPath(location: EnvironmentLocationGroup): string {
  const parts: string[] = [];
  const append = (value?: string | null) => {
    const name = value?.trim();
    if (name && parts[parts.length - 1] !== name) parts.push(name);
  };

  append(location.orgName);
  const warehouseNames = location.warehouseId ? warehousePathNames.value.get(location.warehouseId) || [] : [];
  warehouseNames.forEach(append);
  if (!warehouseNames.length) append(location.warehouseName);
  location.pointNames.forEach(append);
  return parts.join(' / ') || '未关联区域';
}

function sensorIcon(card: EnvironmentCardVo): string {
  if (card.sensorIcon) return card.sensorIcon;
  const icons: Record<string, string> = {
    TEMP: 'mdi:thermometer',
    HUMIDITY: 'mdi:water-percent',
    LIGHT: 'mdi:white-balance-sunny',
    UV: 'mdi:weather-sunny-alert',
    TVOC: 'mdi:molecule',
    PM25: 'mdi:blur',
    CO2: 'mdi:molecule-co2',
    VIBRATION: 'mdi:pulse',
    MOLD: 'mdi:bacteria-outline',
  };
  return icons[card.sensorCode?.toUpperCase() || ''] || 'mdi:chart-line';
}

function locationStatus(location: EnvironmentLocationGroup): LocationStatus {
  return aggregateEnvironmentStatus(location.metrics.map(cardStatus), location.activeAlarmCount);
}

function shouldShowLocationStatus(location: EnvironmentLocationGroup): boolean {
  return true;
}

function locationStatusClass(location: EnvironmentLocationGroup): string | null {
  const status = locationStatus(location);
  return `is-${status.toLowerCase()}`;
}

const alarmLevelRanks: Record<PvAlarmLevel, number> = { RED: 4, ORANGE: 3, YELLOW: 2, BLUE: 1, NORMAL: 0 };

function shouldUseAlarmSummary(group: EnvironmentLocationGroup, card: EnvironmentCardVo): boolean {
  if (!group.activeAlarmLevel) return true;
  const groupRank = alarmLevelRanks[group.activeAlarmLevel] ?? 0;
  const cardRank = card.activeAlarmLevel ? (alarmLevelRanks[card.activeAlarmLevel] ?? 0) : 0;
  if (cardRank !== groupRank) return cardRank > groupRank;
  if (!group.activeAlarmAt) return true;
  return Boolean(card.activeAlarmAt && dayjs(card.activeAlarmAt).isAfter(group.activeAlarmAt));
}

function activeAlarmLabel(location: EnvironmentLocationGroup): string {
  const levelLabels: Partial<Record<PvAlarmLevel, string>> = {
    RED: '一级预警',
    ORANGE: '二级预警',
    YELLOW: '三级预警',
    BLUE: '四级预警',
  };
  const level = location.activeAlarmLevel ? levelLabels[location.activeAlarmLevel] : null;
  const title = location.activeAlarmTitle?.trim();
  const primary = [level, title].filter((item, index, values) => item && values.indexOf(item) === index).join(' · ');
  if (location.activeAlarmCount > 1) return `${primary || '存在预警'} 等 ${location.activeAlarmCount} 项`;
  return primary || '存在预警';
}

function statusLabel(card: EnvironmentCardVo): string {
  const status = cardStatus(card);
  if (status === 'SAFE') return card.backfill ? '正常（补传）' : '正常';
  if (status === 'UNSAFE') {
    const levelLabels: Record<string, string> = {
      RED: '一级预警',
      ORANGE: '二级预警',
      YELLOW: '三级预警',
      BLUE: '四级预警',
    };
    return levelLabels[card.alarmLevel || ''] || '超出保护阈值';
  }
  if (status === 'SUSPECT') return '数据可疑';
  if (status === 'CONFLICT') return '阈值冲突';
  return status === 'NO_DATA' ? '暂无数据' : sampleStatusLabels[status] || '暂无法判定';
}

function locationStatusLabel(location: EnvironmentLocationGroup): string {
  const status = locationStatus(location);
  if (status === 'SAFE') return '正常';
  if (status === 'UNSAFE') return '存在预警';
  if (status === 'SUSPECT') return '数据可疑';
  if (status === 'CONFLICT') return '阈值冲突';
  if (status === 'PARTIAL') return '部分指标暂无数据';
  if (status === 'UNCONFIGURED') return `${missingStandardCount(location.metrics)} 项指标待判定`;
  if (sampleStatusLabels[status]) return sampleStatusLabels[status];
  return '暂无数据';
}

function locationStatusTagType(location: EnvironmentLocationGroup): 'success' | 'error' | 'warning' | 'info' | 'default' {
  const status = locationStatus(location);
  if (status === 'SAFE') return 'success';
  if (status === 'UNSAFE') return 'error';
  if (['UNCONFIGURED', 'FRESHNESS_UNKNOWN', 'EVIDENCE_UNKNOWN'].includes(status)) return 'info';
  if (status !== 'NO_DATA') {
    return 'warning';
  }
  return 'default';
}

function formatValue(value?: number | null): string {
  if (value == null || !Number.isFinite(value)) return '--';
  return new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 3 }).format(value);
}

function sampleTimeLabel(card: EnvironmentCardVo): string {
  if (card.dateTime == null) return '等待采样';
  const limit = card.maximumSampleAgeSeconds;
  return `采集 ${dayjs(card.dateTime).format('MM-DD HH:mm:ss')} · ${limit ? `有效期 ${limit} 秒` : '数据时效待评估'}`;
}
function safeRange(card: EnvironmentCardVo): string {
  if (card.thresholdConflict) return '阈值冲突';
  const min = card.safeMinValue;
  const max = card.safeMaxValue;
  const unit = card.sensorUnit ? ` ${card.sensorUnit}` : '';
  if (min != null && max != null) return `${formatValue(min)} ~ ${formatValue(max)}${unit}`;
  if (min != null) return `≥ ${formatValue(min)}${unit}`;
  if (max != null) return `≤ ${formatValue(max)}${unit}`;
  return '';
}

function formatTime(value?: number | null): string {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '--';
}

async function loadCards() {
  if (disposed || !active) return;
  const request = ++cardRequest;
  const startedAt = performance.now();
  lastAttemptAt = startedAt;
  loading.value = true;
  cardError.value = '';
  try {
    const resp: any = await historyApi.latestCards();
    if (disposed || !active || request !== cardRequest) return;
    const data = resp?.data?.data ?? resp?.data ?? resp;
    const rows = data?.list ?? data;
    if (!Array.isArray(rows)) throw new Error('实时环境数据格式异常');
    // 只显示已绑定设备的卡片，已绑定但尚未采样的设备继续保留。
    cards.value = rows.filter((card: EnvironmentCardVo) => Boolean(card.deviceId?.trim()));
    if (selectedLocation.value) {
      const current = locations.value.find((item) => item.key === selectedLocation.value?.key);
      if (!current) closeHistory();
      else selectedLocation.value = current;
    }
    loadedAt = startedAt;
    elapsedMs.value = performance.now() - startedAt;
  } catch (error: any) {
    if (!disposed && active && request === cardRequest) {
      cards.value = [];
      closeHistory();
      cardError.value = error?.message || '加载实时环境数据失败';
    }
  } finally {
    if (!disposed && active && request === cardRequest) loading.value = false;
  }
}

const historyShow = ref(false);
const historyLoading = ref(false);
const historyError = ref('');
const historyTableRows = ref<HistoryDataVo[]>([]);
let historyGeneration = 0;
let historyPageRequest = 0;
let historyReady = false;
const historyNeedsQuery = ref(true);
const historyTab = ref<'trend' | 'detail'>('trend');
const historyTableRef = ref<any>(null);
const selectedLocation = ref<EnvironmentLocationGroup | null>(null);
const historyRows = ref<HistoryDataVo[]>([]);
const historyRange = ref<[number, number] | null>(null);
const historyDeviceId = ref<string | null>(null);
const historySensorId = ref<string | null>(null);
const historyValueOperator = ref<NumericValueOperator | null>(null);
const historyValue = ref<number | null>(null);
const historyValue2 = ref<number | null>(null);
const historyQueryParams = reactive({
  warehouseId: null as string | null,
  deviceId: null as string | null,
  sensorId: null as string | null,
  valueOperator: null as NumericValueOperator | null,
  value: null as number | null,
  value2: null as number | null,
  from: null as number | null,
  to: null as number | null,
});
const historyTitle = computed(() =>
  selectedLocation.value ? selectedLocation.value.warehouseName || '直接位置' : '历史数据',
);
const historyLocationGroups = computed(() => {
  const selected = selectedLocation.value;
  if (!selected) return [];
  return locations.value.filter((location) =>
    selected.warehouseId ? location.warehouseId === selected.warehouseId : location.key === selected.key,
  );
});
const historyDeviceOptions = computed(() => {
  const options = new Map<string, { label: string; value: string }>();
  historyLocationGroups.value.forEach((location) => {
    location.devices.forEach((device) => {
      options.set(device.id, {
        label: device.name ? `${device.name}（${device.id}）` : device.id,
        value: device.id,
      });
    });
  });
  historyRows.value.forEach((row) => {
    if (row.deviceId && hasLocationAssociation(row.sampleLocationBasis) && !options.has(row.deviceId))
      options.set(row.deviceId, { value: row.deviceId, label: `${row.deviceName || row.deviceId}（历史采样设备）` });
  });
  return [...options.values()].sort((a, b) => a.label.localeCompare(b.label, 'zh-CN'));
});
const historyAvailableMetrics = computed(() => {
  const current = historyLocationGroups.value.flatMap((location) => location.metrics);
  const keys = new Set(current.map((metric) => `${metric.sensorPointId}:${metric.deviceId}:${metric.sensorId}`));
  const historic: EnvironmentCardVo[] = [];
  historyRows.value.forEach((row) => {
    if (!row.sensorPointId || !row.sensorId || !hasLocationAssociation(row.sampleLocationBasis)) return;
    const key = `${row.sensorPointId}:${row.deviceId}:${row.sensorId}`;
    if (keys.has(key)) return;
    keys.add(key);
    historic.push({
      sensorPointId: row.sensorPointId,
      sensorId: row.sensorId,
      sensorName: row.sensorName,
      sensorUnit: row.sensorUnit,
      deviceId: row.deviceId,
      deviceName: row.deviceName,
    });
  });
  return [...current, ...historic];
});
const historyMetricOptions = computed(() => {
  const options = new Map<string, { label: string; value: string }>();
  historyAvailableMetrics.value.forEach((metric) => {
    if (!metric.sensorId) return;
    options.set(metric.sensorId, {
      label: `${metric.sensorName || metric.sensorId}（${metric.sensorId}）`,
      value: metric.sensorId,
    });
  });
  return [...options.values()].sort((a, b) => a.label.localeCompare(b.label, 'zh-CN'));
});
const historyMetrics = computed(() =>
  historyAvailableMetrics.value.filter(
    (metric) =>
      (!historyDeviceId.value || metric.deviceId === historyDeviceId.value) &&
      (!historySensorId.value || metric.sensorId === historySensorId.value),
  ),
);
const historyChartRows = computed(() =>
  historyRows.value.filter(
    (row) =>
      (!historyDeviceId.value || row.deviceId === historyDeviceId.value) &&
      (!historySensorId.value || row.sensorId === historySensorId.value),
  ),
);
const historyHasUnverifiedRows = computed(() =>
  historyChartRows.value.some((row) => !hasLocationAssociation(row.sampleLocationBasis)),
);

const historyColumns = [
  {
    title: '位置依据',
    key: 'sampleLocationBasis',
    width: 170,
    ellipsis: { tooltip: true },
    resizable: true,
    render: (row: HistoryDataVo) => locationAssociationLabel(row.sampleLocationBasis),
  },
  {
    title: '采集时间',
    key: 'dateTime',
    width: 190,
    resizable: true,
    columnType: 'dateTime',
    ellipsis: { tooltip: true },
  },
  {
    title: '监测位置',
    key: 'sensorPointName',
    minWidth: 170,
    resizable: true,
    width: 180,
    ellipsis: { tooltip: true },
  },
  {
    title: '设备',
    key: 'deviceName',
    minWidth: 170,
    ellipsis: { tooltip: true },
    resizable: true,
    width: 180,
    render: (row: HistoryDataVo) => row.deviceName || row.deviceId || '--',
  },
  { title: '指标', key: 'sensorName', minWidth: 130, resizable: true, width: 140, ellipsis: { tooltip: true } },
  { title: '监测值', key: 'dataValue', width: 110, render: (row: HistoryDataVo) => formatValue(row.dataValue) },
  {
    title: '单位',
    key: 'sensorUnit',
    width: 110,
    ellipsis: { tooltip: true },
    resizable: true,
    render: (row: HistoryDataVo) => row.sensorUnit || '--',
  },
  {
    title: '质量',
    key: 'quality',
    width: 120,
    ellipsis: { tooltip: true },
    resizable: true,
    render: (row: HistoryDataVo) => (row.quality === 'SUSPECT' ? '可疑' : row.quality === 'INVALID' ? '无效' : '正常'),
  },
  {
    title: '补传',
    key: 'backfill',
    width: 90,
    ellipsis: { tooltip: true },
    resizable: true,
    render: (row: HistoryDataVo) => (row.backfill ? '是' : '否'),
  },
];

async function openHistory(location: EnvironmentLocationGroup) {
  if (disposed || !active || cardError.value) return;
  const current = locations.value.find((item) => item.key === location.key);
  if (!current) return;
  location = current;
  invalidateHistory();
  if (!location.warehouseId) {
    message.warning('该监测位置未关联直接位置');
    return;
  }
  selectedLocation.value = location;
  historyRows.value = [];
  historyTab.value = 'trend';
  historyDeviceId.value = location.aggregationDimension === 'DEVICE' ? location.deviceId || null : null;
  historySensorId.value =
    metricFilter.value && location.metrics.some((metric) => metric.sensorId === metricFilter.value)
      ? metricFilter.value
      : null;
  historyValueOperator.value = valueOperator.value;
  historyValue.value = valueFilterValue.value;
  historyValue2.value = valueFilterValue2.value;
  const latestAt = location.latestAt ? dayjs(location.latestAt) : dayjs();
  historyRange.value = [latestAt.subtract(1, 'day').valueOf(), latestAt.valueOf()];
  historyShow.value = true;
  const generation = historyGeneration;
  await nextTick();
  if (currentHistory(generation)) await loadHistory();
}

function currentHistory(generation: number) {
  return !disposed && active && historyShow.value && generation === historyGeneration;
}

function invalidateHistory() {
  ++historyGeneration;
  ++historyPageRequest;
  historyReady = false;
  historyNeedsQuery.value = true;
  historyRows.value = [];
  historyTableRows.value = [];
  historyLoading.value = false;
  historyError.value = '';
}

function closeHistory() {
  invalidateHistory();
  historyShow.value = false;
  selectedLocation.value = null;
}

async function fetchHistoryPage(params: any) {
  const generation = historyGeneration;
  const request = ++historyPageRequest;
  if (!currentHistory(generation) || !historyReady) throw new Error('请重新查询历史数据');
  try {
    const response: any = await historyApi.page({ ...params, ...historyQueryParams });
    if (!currentHistory(generation) || request !== historyPageRequest) throw new Error('历史明细请求已失效');
    const data = response?.data?.data ?? response?.data ?? response;
    if (!Array.isArray(data?.list ?? data)) throw new Error('历史明细数据格式异常');
    return response;
  } catch (error: any) {
    if (currentHistory(generation) && request === historyPageRequest) {
      invalidateHistory();
      historyError.value = error?.message || '历史明细读取失败';
    }
    throw error;
  }
}

function onHistoryValueOperatorChange(operator: NumericValueOperator | null) {
  historyValueOperator.value = operator;
  if (!operator) {
    historyValue.value = null;
    historyValue2.value = null;
    return;
  }
  if (historyValue.value == null) historyValue.value = 0;
  if (operator === 'BETWEEN') {
    if (historyValue2.value == null) historyValue2.value = historyValue.value;
  } else {
    historyValue2.value = null;
  }
}

function historyValueFilterIsValid(): boolean {
  if (!historyValueOperator.value) return true;
  if (historyValue.value == null) return false;
  return historyValueOperator.value !== 'BETWEEN' || historyValue2.value != null;
}

async function loadHistory() {
  if (disposed || !active || !historyShow.value) return;
  invalidateHistory();
  const generation = historyGeneration;
  const location = selectedLocation.value;
  if (!location?.warehouseId || !historyRange.value) {
    message.warning('请选择查询时间范围');
    return;
  }
  if (!historyValueFilterIsValid()) {
    message.warning(historyValueOperator.value === 'BETWEEN' ? '请输入完整的数值区间' : '请输入比较值');
    return;
  }
  const [from, to] = historyRange.value;
  historyQueryParams.warehouseId = location.warehouseId;
  historyQueryParams.deviceId = historyDeviceId.value;
  historyQueryParams.sensorId = historySensorId.value;
  historyQueryParams.valueOperator = historyValueOperator.value;
  historyQueryParams.value = historyValueOperator.value ? historyValue.value : null;
  historyQueryParams.value2 = historyValueOperator.value === 'BETWEEN' ? historyValue2.value : null;
  historyQueryParams.from = from;
  historyQueryParams.to = to;
  historyLoading.value = true;
  try {
    const resp: any = await historyApi.locationSeries({
      warehouseId: location.warehouseId,
      deviceId: historyDeviceId.value || undefined,
      sensorId: historySensorId.value || undefined,
      valueOperator: historyValueOperator.value || undefined,
      value: historyValueOperator.value ? (historyValue.value ?? undefined) : undefined,
      value2: historyValueOperator.value === 'BETWEEN' ? (historyValue2.value ?? undefined) : undefined,
      from: historyQueryParams.from,
      to: historyQueryParams.to,
      limit: 10_000,
    });
    if (!currentHistory(generation)) return;
    const data = resp?.data?.data ?? resp?.data ?? resp;
    const rows = data?.list ?? data;
    if (!Array.isArray(rows)) throw new Error('位置历史数据格式异常');
    historyRows.value = rows;
    historyReady = true;
    historyNeedsQuery.value = false;
    await nextTick();
    if (currentHistory(generation)) historyTableRef.value?.handleQuery();
  } catch (error: any) {
    if (currentHistory(generation)) {
      invalidateHistory();
      historyError.value = error?.message || '加载位置历史数据失败';
    }
  } finally {
    if (currentHistory(generation)) historyLoading.value = false;
  }
}

watch(
  historyShow,
  (show) => {
    if (!show) closeHistory();
  },
  { flush: 'sync' },
);
watch(
  [historyRange, historyDeviceId, historySensorId, historyValueOperator, historyValue, historyValue2],
  () => invalidateHistory(),
  { deep: true, flush: 'sync' },
);

function startMonitoring() {
  void loadCards();
  ageTimer = setInterval(() => {
    elapsedMs.value = performance.now() - loadedAt;
    if (!loading.value && performance.now() - lastAttemptAt >= 30000) void loadCards();
  }, 1000);
}
function stopMonitoring() {
  active = false;
  closeHistory();
  cards.value = [];
  cardRequest++;
  loading.value = false;
  if (ageTimer) clearInterval(ageTimer);
  ageTimer = undefined;
}
onMounted(startMonitoring);
onDeactivated(stopMonitoring);
onActivated(() => {
  if (disposed || active) return;
  active = true;
  startMonitoring();
});
onBeforeUnmount(() => {
  disposed = true;
  stopMonitoring();
});
</script>

<style scoped>
.pv-environment-filters {
  margin-bottom: 16px;
}

.pv-environment-warehouse-filter {
  width: 240px;
}

.pv-environment-grid__item {
  align-self: start;
}

.pv-environment-card {
  position: relative;
  overflow: hidden;
  cursor: pointer;
  border-top: 3px solid #d9d9d9;
  transition:
    transform 0.18s ease,
    box-shadow 0.18s ease;
}

.pv-environment-card:hover {
  transform: translateY(-3px);
}

.pv-environment-card.is-safe {
  border-top-color: #18a058;
}

.pv-environment-card.is-unsafe {
  border-top-color: #d03050;
}

.pv-environment-card.is-partial {
  border-top-color: #f0a020;
}

.pv-environment-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.pv-environment-card__actions {
  display: flex;
  flex: none;
  align-items: center;
  gap: 8px;
}

.pv-environment-card__history-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 4px;
  border-radius: 50%;
  color: var(--n-text-color-3);
  background: var(--n-color-embedded);
  transition:
    color 0.18s ease,
    background-color 0.18s ease;
}

.pv-environment-card:hover .pv-environment-card__history-icon {
  color: #2080f0;
  background: rgba(32, 128, 240, 0.12);
}

.pv-environment-card__heading {
  min-width: 0;
}

.pv-environment-card__title {
  overflow: hidden;
  font-size: 17px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pv-environment-card__path {
  display: block;
  margin-top: 3px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.pv-environment-card__path {
  display: -webkit-box;
  line-height: 1.45;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.pv-environment-card__summary {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 14px;
  padding-bottom: 12px;
  color: var(--n-text-color-3);
  font-size: 12px;
}

.pv-environment-card__image {
  width: 100%;
  aspect-ratio: 2.45 / 1;
  margin-bottom: 11px;
  overflow: hidden;
  border-radius: 6px;
  background: #151515;
}

.pv-environment-card__image :deep(img) {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: cover;
}

.pv-environment-card__visual-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0;
  border-top: 1px solid var(--n-border-color);
  border-bottom: 1px solid var(--n-border-color);
}

.pv-environment-visual-metric {
  min-width: 0;
  padding: 10px 9px;
}

.pv-environment-visual-metric + .pv-environment-visual-metric {
  border-left: 1px solid var(--n-border-color);
}

.pv-environment-visual-metric__value {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 5px;
}

.pv-environment-visual-metric__value strong,
.pv-environment-plain-metric > strong {
  color: #315f7d;
  font-size: 22px;
  line-height: 1.2;
}

.pv-environment-visual-metric__value strong small,
.pv-environment-plain-metric > strong small {
  margin-left: 3px;
  color: var(--n-text-color-3);
  font-size: 10px;
  font-weight: 400;
}

.pv-environment-visual-metric__value span {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  color: var(--n-text-color-3);
  font-size: 10px;
  white-space: nowrap;
}

.pv-environment-visual-metric__value i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #a3a3a3;
}

.pv-environment-visual-metric__value i.is-safe {
  background: #18a058;
}

.pv-environment-visual-metric__value i.is-unsafe {
  background: #d03050;
}

.pv-environment-visual-metric__name {
  margin-top: 4px;
  overflow: hidden;
  font-size: 12px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pv-environment-metric__device {
  margin-top: 3px;
  overflow: hidden;
  color: var(--n-text-color-3);
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pv-environment-visual-metric__range,
.pv-environment-plain-metric__range {
  margin-top: 5px;
  overflow: hidden;
  color: var(--n-text-color-3);
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pv-environment-card__plain-metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  border-top: 1px solid var(--n-border-color);
}

.pv-environment-plain-metric {
  min-width: 0;
  padding: 14px 12px 12px;
  border-bottom: 1px solid var(--n-border-color);
}

.pv-environment-plain-metric:nth-child(odd) {
  border-right: 1px solid var(--n-border-color);
}

.pv-environment-plain-metric__title {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}

.pv-environment-plain-metric__title > span {
  display: inline-flex;
  min-width: 0;
  align-items: center;
  gap: 5px;
  overflow: hidden;
  font-size: 13px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pv-environment-plain-metric__title .n-icon {
  flex: none;
  color: #536c9a;
}

.pv-environment-plain-metric > strong {
  display: block;
  margin-top: 7px;
}

.pv-environment-card__collected-at {
  overflow: hidden;
  margin-top: 10px;
  color: var(--n-text-color-3);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pv-environment-card__empty {
  padding: 22px 0 18px;
}

.pv-environment-card__metrics {
  display: grid;
  gap: 8px;
}

.pv-environment-metric {
  padding: 9px 10px;
  border-radius: 6px;
  background: color-mix(in srgb, var(--n-color-embedded) 72%, var(--n-border-color));
}

.pv-environment-metric__main,
.pv-environment-metric__range {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.pv-environment-metric__name {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 6px;
}

.pv-environment-metric__name i {
  width: 7px;
  height: 7px;
  flex: none;
  border-radius: 50%;
  background: #a3a3a3;
}

.pv-environment-metric__name i.is-safe {
  background: #18a058;
}

.pv-environment-metric__name i.is-unsafe {
  background: #d03050;
}

.pv-environment-metric__name span,
.pv-environment-metric__name small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pv-environment-metric__name small {
  color: var(--n-text-color-3);
}

.pv-environment-metric__main > strong {
  flex: none;
  color: #2080f0;
  font-size: 19px;
}

.pv-environment-metric__main > strong small {
  margin-left: 4px;
  color: var(--n-text-color-3);
  font-size: 11px;
  font-weight: 400;
}

.pv-environment-metric__range {
  margin-top: 4px;
  color: var(--n-text-color-3);
  font-size: 11px;
}

.pv-environment-card__more {
  display: block;
  margin-top: 10px;
  text-align: right;
  font-size: 12px;
}

.pv-environment-history__toolbar {
  margin: 16px 0 4px;
}

.pv-environment-history__table {
  height: 480px;
  padding-top: 8px;
}

.pv-environment-history__image {
  width: min(520px, 100%);
  max-height: 220px;
  display: block;
  border-radius: 6px;
  object-fit: cover;
}

@media (max-width: 640px) {
  .pv-environment-warehouse-filter {
    width: min(280px, 78vw);
  }

  .pv-environment-filters :deep(.n-input) {
    width: min(280px, 78vw) !important;
  }

  .pv-environment-card__visual-metrics,
  .pv-environment-card__plain-metrics {
    grid-template-columns: 1fr;
  }

  .pv-environment-visual-metric + .pv-environment-visual-metric,
  .pv-environment-plain-metric:nth-child(odd) {
    border-left: 0;
    border-right: 0;
  }
}

.pv-environment-card__standard-hint {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px 8px;
  margin-bottom: 10px;
  color: var(--n-text-color-2);
  font-size: 12px;
  line-height: 1.6;
}
</style>
