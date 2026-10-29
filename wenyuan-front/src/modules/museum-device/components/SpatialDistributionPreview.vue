<template>
  <div class="spatial-preview" :style="previewStyle">
    <NSpin :show="loading" class="spatial-preview__body">
      <div class="spatial-preview__map-stage">
        <SpatialPlanCanvas
          v-if="effectivePlanView"
          :view="effectivePlanView"
          :location-navigation="!previewView"
          @device-click="emit('device-click', $event)"
          @open-location="selectLocation"
        />
        <div v-else class="spatial-preview__empty">
          <NEmpty :description="emptyDescription" />
        </div>

        <div v-if="effectivePlanView" class="map-toolbar">
          <NCascader
            v-if="showControls && locations.length"
            :value="selectedLocationId"
            :options="locationCascaderOptions"
            check-strategy="all"
            show-path
            filterable
            size="small"
            placeholder="切换楼层或空间"
            class="map-location-cascader"
            @update:value="selectCascaderLocation"
          />
          <NSelect
            v-if="showControls && plans.length > 1"
            v-model:value="selectedPlanId"
            :options="planOptions"
            size="small"
            class="map-plan-select"
            @update:value="loadPlanView"
          />
          <span v-if="showControls && (locations.length || plans.length > 1)" class="map-toolbar__divider"></span>
          <NTag size="small" :bordered="false">{{ effectivePlanView.devices.length }} 台设备</NTag>
          <NTag v-if="alarmCount" size="small" type="error" :bordered="false">{{ alarmCount }} 条告警</NTag>
          <NButton
            v-if="showControls"
            size="tiny"
            circle
            secondary
            :loading="loading"
            title="刷新实时数据"
            @click="refresh"
          >
            ↻
          </NButton>
        </div>

        <div v-if="contextName" class="map-context-card">
          <span class="map-context-card__dot"></span>
          <span class="map-context-card__content">
            <strong>{{ contextName }}</strong>
            <small>{{ contextPath }}</small>
          </span>
          <NTag
            v-if="selectedPlanLocation && selectedLocation && selectedPlanLocation.id !== selectedLocation.id"
            size="tiny"
            :bordered="false"
          >
            图层：{{ selectedPlanLocation.name }}
          </NTag>
        </div>
      </div>
    </NSpin>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { NButton, NCascader, NEmpty, NSelect, NSpin, NTag, type CascaderOption } from 'naive-ui';
import { ingestApi } from '../api/ingest';
import type {
  MuseumLocationPlanViewVo,
  MuseumLocationPlanVo,
  MuseumSpatialDeviceVo,
  MuseumSpatialLocationVo,
} from '../typings';
import {
  deepestSpatialLocationId,
  findSpatialLocation,
  findSpatialSourceLocation,
  preferredSpatialLocation,
  spatialLayerSelection,
  spatialPlanLocation,
  type SpatialLayerSelection,
} from '../utils/spatialLayers';
import SpatialPlanCanvas from './SpatialPlanCanvas.vue';

const props = withDefaults(
  defineProps<{
    /** 场馆即统一采集域的 orgId；也可改传一组明确的位置参数。 */
    venueId?: string | null;
    /** 统一采集位置 ID，仅接受 ing_location.id。 */
    locationId?: string | null;
    /** 来源系统编码，例如藏品库房为 COL_WAREHOUSE。 */
    sourceCode?: string | null;
    /** 来源系统中的位置 ID，例如 col_warehouse.id。 */
    sourceLocationId?: string | null;
    /** 编辑界面可直接传入当前内存视图，用于预览尚未保存的锚点。 */
    previewView?: MuseumLocationPlanViewVo | null;
    height?: string | number;
    refreshInterval?: number;
    showControls?: boolean;
  }>(),
  {
    venueId: null,
    locationId: null,
    sourceCode: null,
    sourceLocationId: null,
    previewView: null,
    height: 560,
    refreshInterval: 30_000,
    showControls: true,
  },
);

const emit = defineEmits<{
  (event: 'update:locationId', locationId: string | null): void;
  (event: 'device-click', device: MuseumSpatialDeviceVo): void;
  (event: 'loaded', view: MuseumLocationPlanViewVo | null): void;
}>();

const loading = ref(false);
const locations = ref<MuseumSpatialLocationVo[]>([]);
const selectedLocationId = ref<string | null>(null);
const plans = ref<MuseumLocationPlanVo[]>([]);
const selectedPlanId = ref<string | null>(null);
const planLocationId = ref<string | null>(null);
const planView = ref<MuseumLocationPlanViewVo | null>(null);
const selection = reactive<SpatialLayerSelection>({ siteId: null, buildingId: null, floorId: null, spaceId: null });
let locationRequest = 0;
let planRequest = 0;
let refreshTimer: ReturnType<typeof setInterval> | undefined;

const previewStyle = computed(() => ({
  height: typeof props.height === 'number' ? `${props.height}px` : props.height,
}));
const selectedLocation = computed(() => locations.value.find((item) => item.id === selectedLocationId.value) || null);
const selectedPlanLocation = computed(() => locations.value.find((item) => item.id === planLocationId.value) || null);
const effectivePlanView = computed(() => props.previewView || planView.value);
const contextName = computed(() => selectedLocation.value?.name || effectivePlanView.value?.plan.locationName || '');
const contextPath = computed(
  () => selectedLocation.value?.path || effectivePlanView.value?.plan.locationPath || contextName.value,
);
const locationCascaderOptions = computed<CascaderOption[]>(() => {
  const ids = new Set(locations.value.map((item) => item.id));
  const children = new Map<string | null, MuseumSpatialLocationVo[]>();
  locations.value.forEach((item) => {
    const parentId = item.parentId && ids.has(item.parentId) ? item.parentId : null;
    children.set(parentId, [...(children.get(parentId) || []), item]);
  });
  const build = (parentId: string | null): CascaderOption[] =>
    (children.get(parentId) || [])
      .sort((left, right) => String(left.path || left.name).localeCompare(String(right.path || right.name), 'zh-CN'))
      .map((item) => {
        const nested = build(item.id);
        return {
          label: item.name,
          value: item.id,
          children: nested.length ? nested : undefined,
        };
      });
  return build(null);
});
const planOptions = computed(() => plans.value.map((plan) => ({ label: plan.name, value: plan.id })));
const alarmCount = computed(
  () => (effectivePlanView.value?.devices || []).filter((device) => device.activeAlarm).length,
);
const hasSourceReference = computed(() => Boolean(props.sourceCode && props.sourceLocationId));
const hasIncompleteSourceReference = computed(() => Boolean(props.sourceCode) !== Boolean(props.sourceLocationId));
const hasConflictingReferences = computed(() => Boolean(props.locationId) && hasSourceReference.value);
const emptyDescription = computed(() => {
  if (props.previewView) return '当前编辑内容暂无可预览图层';
  if (hasIncompleteSourceReference.value) return 'sourceCode 和 sourceLocationId 必须同时传入';
  if (hasConflictingReferences.value) return '统一位置 ID 与来源位置参数不能同时传入';
  if (!props.venueId && !props.locationId && !hasSourceReference.value) {
    return '请传入场馆 ID、统一位置 ID 或来源位置参数';
  }
  if (!locations.value.length) return '所属场馆尚未配置建筑、楼层、展厅或库位';
  if (selectedLocation.value) return `${selectedLocation.value.name}及其上下级尚未配置分布图`;
  return '请选择建筑、楼层、展厅或库位';
});

function unwrapResponse(response: any): any {
  let payload = response;
  for (let depth = 0; depth < 5; depth += 1) {
    if (Array.isArray(payload) || payload === null || typeof payload !== 'object' || !('data' in payload))
      return payload;
    payload = payload.data;
  }
  return payload;
}

function responseList<T>(response: any): T[] {
  const payload = unwrapResponse(response);
  if (Array.isArray(payload)) return payload;
  return Array.isArray(payload?.list) ? payload.list : [];
}

function normalizePlanView(payload: any): MuseumLocationPlanViewVo | null {
  if (!payload?.plan) return null;
  return {
    ...payload,
    anchors: Array.isArray(payload.anchors) ? payload.anchors : [],
    devices: Array.isArray(payload.devices) ? payload.devices : [],
    childLocations: Array.isArray(payload.childLocations) ? payload.childLocations : [],
  };
}

function clearLocationState() {
  locations.value = [];
  selectedLocationId.value = null;
  plans.value = [];
  selectedPlanId.value = null;
  planLocationId.value = null;
  planView.value = null;
  Object.assign(selection, spatialLayerSelection([], null));
  emit('loaded', null);
}

async function loadLocations() {
  const requestId = ++locationRequest;
  if (props.previewView) {
    emit('loaded', props.previewView);
    return;
  }
  if (
    hasIncompleteSourceReference.value ||
    hasConflictingReferences.value ||
    (!props.venueId && !props.locationId && !hasSourceReference.value)
  ) {
    clearLocationState();
    return;
  }
  loading.value = true;
  try {
    const rows = responseList<MuseumSpatialLocationVo>(
      await ingestApi.spatialLocations({
        orgId: props.venueId || undefined,
        locationId: props.locationId || undefined,
        sourceCode: props.sourceCode || undefined,
        sourceLocationId: props.sourceLocationId || undefined,
      }),
    );
    if (requestId !== locationRequest) return;
    locations.value = rows;
    const requested = props.locationId
      ? findSpatialLocation(rows, props.locationId)
      : findSpatialSourceLocation(rows, props.sourceCode, props.sourceLocationId);
    const preferred = requested || preferredSpatialLocation(rows);
    await selectLocation(preferred?.id || null);
  } finally {
    if (requestId === locationRequest) loading.value = false;
  }
}

async function selectLocation(locationId: string | null) {
  const target = findSpatialLocation(locations.value, locationId);
  selectedLocationId.value = target?.id || null;
  Object.assign(selection, spatialLayerSelection(locations.value, target?.id));
  emit('update:locationId', selectedLocationId.value);
  await loadPlans();
}

async function selectCascaderLocation(value: string | number | null) {
  await selectLocation(value === null ? null : String(value));
}

async function loadPlans(preferredPlanId?: string) {
  const requestId = ++planRequest;
  const locationId = selectedLocationId.value || deepestSpatialLocationId(selection);
  if (!locationId) {
    plans.value = [];
    selectedPlanId.value = null;
    planLocationId.value = null;
    planView.value = null;
    emit('loaded', null);
    return;
  }
  const planLocation = spatialPlanLocation(locations.value, locationId);
  const resolvedPlanLocationId = planLocation?.id || locationId;
  planLocationId.value = resolvedPlanLocationId;
  const rows = responseList<MuseumLocationPlanVo>(await ingestApi.locationPlans(resolvedPlanLocationId));
  if (requestId !== planRequest) return;
  plans.value = rows;
  selectedPlanId.value =
    rows.find((plan) => plan.id === preferredPlanId)?.id ||
    rows.find((plan) => plan.defaultPlan)?.id ||
    rows[0]?.id ||
    null;
  await loadPlanView();
}

async function loadPlanView() {
  const requestId = ++planRequest;
  if (!selectedPlanId.value) {
    planView.value = null;
    emit('loaded', null);
    return;
  }
  const view = normalizePlanView(unwrapResponse(await ingestApi.locationPlanView(selectedPlanId.value)));
  if (requestId !== planRequest) return;
  planView.value = view;
  emit('loaded', view);
}

async function refresh() {
  if (props.previewView) {
    emit('loaded', props.previewView);
    return;
  }
  loading.value = true;
  try {
    if (selectedPlanId.value) await loadPlanView();
    else await loadLocations();
  } finally {
    loading.value = false;
  }
}

watch(
  () =>
    [
      props.venueId || null,
      props.locationId || null,
      props.sourceCode || null,
      props.sourceLocationId || null,
    ] as const,
  async ([venueId, locationId, sourceCode, sourceLocationId], previous) => {
    const localTarget = locationId
      ? findSpatialLocation(locations.value, locationId)
      : findSpatialSourceLocation(locations.value, sourceCode, sourceLocationId);
    if (previous && venueId === previous[0] && localTarget) {
      if (localTarget.id !== selectedLocationId.value) await selectLocation(localTarget.id);
      return;
    }
    await loadLocations();
  },
  { immediate: true },
);

onMounted(() => {
  if (props.refreshInterval > 0 && !props.previewView) {
    refreshTimer = setInterval(() => {
      if (selectedPlanId.value) loadPlanView();
    }, props.refreshInterval);
  }
});
onBeforeUnmount(() => refreshTimer && clearInterval(refreshTimer));

defineExpose({ refresh, selectLocation });
</script>

<style scoped>
.spatial-preview {
  position: relative;
  display: flex;
  min-height: 420px;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

.spatial-preview__body {
  width: 100%;
  min-height: 0;
  flex: 1;
}
.spatial-preview__body :deep(.n-spin-content) {
  height: 100%;
}
.spatial-preview__body :deep(.spatial-canvas) {
  min-height: 100%;
}
.spatial-preview__map-stage {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 0;
}
.spatial-preview__empty {
  display: flex;
  height: 100%;
  min-height: 360px;
  align-items: center;
  justify-content: center;
}

.map-toolbar {
  position: absolute;
  top: 16px;
  right: 16px;
  z-index: 810;
  display: flex;
  max-width: calc(100% - 88px);
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 6px;
  padding: 6px 7px;
  border: 1px solid rgba(255, 255, 255, 0.8);
  border-radius: 9px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 4px 14px rgba(31, 45, 61, 0.15);
  backdrop-filter: blur(8px);
}
.map-location-cascader {
  width: 160px;
}
.map-plan-select {
  width: 150px;
}
.map-toolbar__divider {
  width: 1px;
  height: 20px;
  margin: 0 2px;
  background: #e4e7ec;
}

.map-context-card {
  position: absolute;
  bottom: 16px;
  left: 16px;
  z-index: 790;
  display: flex;
  max-width: min(520px, calc(100% - 330px));
  align-items: center;
  gap: 9px;
  padding: 8px 11px;
  border: 1px solid rgba(255, 255, 255, 0.82);
  border-radius: 9px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 4px 14px rgba(31, 45, 61, 0.16);
  backdrop-filter: blur(8px);
}
.map-context-card__dot {
  width: 8px;
  height: 8px;
  flex: none;
  border-radius: 50%;
  background: #18a058;
  box-shadow: 0 0 0 4px rgba(24, 160, 88, 0.13);
}
.map-context-card__content {
  display: flex;
  min-width: 0;
  flex-direction: column;
}
.map-context-card__content strong {
  color: #283345;
  font-size: 13px;
}
.map-context-card__content small {
  overflow: hidden;
  color: #7d8795;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 900px) {
  .map-toolbar {
    top: 12px;
    right: 12px;
    max-width: calc(100% - 76px);
  }
  .map-location-cascader {
    width: min(230px, calc(100vw - 190px));
  }
  .map-plan-select {
    width: 120px;
  }
  .map-context-card {
    right: 12px;
    bottom: 58px;
    left: 12px;
    max-width: none;
  }
}
</style>
