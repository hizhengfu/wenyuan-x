<template>
  <div
    class="spatial-canvas"
    :class="{ 'spatial-canvas--drop-target': editing && draggingTarget }"
    @dragover.prevent="onDragOver"
    @drop.prevent="onDrop"
  >
    <LeafletImage
      v-if="view.plan.filePath"
      ref="imageMapRef"
      :url="view.plan.filePath"
      fit-type="auto"
      class="spatial-map"
      @click="onMapClick"
      @zoomend="mapZoom = $event.target.getZoom()"
    >
      <LMarker
        v-for="group in anchorGroups"
        :key="group.key"
        :lat-lng="groupLatLng(group)"
        :icon="anchorIcon(group)"
        :draggable="editing && !group.clustered"
        :z-index-offset="groupZIndex(group)"
        @ready="(marker: L.Marker) => marker.setIcon(anchorIcon(group))"
        @click="() => onAnchorClick(group)"
        @moveend="(event: any) => onMove(group.primary, event)"
      >
        <LTooltip :offset="[0, -14]" :options="{ opacity: 1 }">
          <div v-if="group.clustered" class="anchor-hover-card anchor-hover-card--cluster">
            <div class="anchor-hover-card__title">
              <strong>{{ group.anchors.length }} 台重叠设备</strong>
              <small>{{ clusterMetricLabels(group) }}</small>
            </div>
            <div v-for="anchor in group.anchors.slice(0, 5)" :key="anchor.targetId" class="anchor-hover-device">
              <template v-if="deviceOf(anchor)">
                <span>{{ deviceOf(anchor)?.name }}</span>
                <strong>{{ metricSummary(deviceOf(anchor)) }}</strong>
              </template>
              <span v-else>{{ anchor.displayName || anchor.targetName || anchor.targetId }}</span>
            </div>
            <small v-if="group.anchors.length > 5">另有 {{ group.anchors.length - 5 }} 台设备，点击锚点查看</small>
          </div>
          <div
            v-else-if="group.primary.targetType === 'DEVICE' && deviceOf(group.primary)"
            class="device-popup anchor-hover-card"
          >
            <div class="device-popup__title">
              <span>{{ deviceOf(group.primary)?.name }}</span>
              <NTag size="small" :type="deviceTagType(deviceOf(group.primary)!)" :bordered="false">
                {{ deviceStateLabel(deviceOf(group.primary)!) }}
              </NTag>
            </div>
            <div class="device-popup__path">{{ deviceOf(group.primary)?.locationPath || '未设置安装位置' }}</div>
            <div v-if="deviceOf(group.primary)?.activeAlarm" class="device-popup__alarm">
              {{ deviceOf(group.primary)?.activeAlarm?.title }}
            </div>
            <div v-if="deviceOf(group.primary)?.latestMetrics?.length" class="device-popup__metrics">
              <div v-for="metric in sortedMetrics(deviceOf(group.primary)).slice(0, 4)" :key="metric.key">
                <span>{{ metric.name || metric.key }}</span>
                <strong>{{ formatMetricValue(metric) }}</strong>
              </div>
            </div>
            <NEmpty v-else size="small" description="暂无实时数据" />
            <div class="device-popup__footer">
              <span>最近上报：{{ formatDateTime(deviceOf(group.primary)?.lastSeenAt) }}</span>
              <strong v-if="!editing">点击查看历史曲线</strong>
            </div>
          </div>
          <div v-else class="anchor-tooltip">
            <strong>{{ group.primary.displayName || group.primary.targetName || group.primary.targetId }}</strong>
            <small>{{ group.primary.targetType === 'DEVICE' ? deviceIconLabel(group.primary) : '下级位置' }}</small>
          </div>
        </LTooltip>
        <LPopup
          v-if="group.clustered || group.primary.targetType === 'LOCATION' || editing"
          :options="{ minWidth: group.clustered ? 360 : 260, maxWidth: group.clustered ? 440 : 340 }"
        >
          <template v-if="group.clustered">
            <div class="cluster-popup">
              <div class="cluster-popup__header">
                <strong>当前位置共 {{ group.anchors.length }} 台设备</strong>
                <span>{{ clusterMetricLabels(group) }}</span>
              </div>
              <div v-for="anchor in group.anchors" :key="anchor.targetId" class="cluster-device">
                <template v-if="deviceOf(anchor)">
                  <div class="cluster-device__title">
                    <span>{{ deviceOf(anchor)?.name }}</span>
                    <NTag size="small" :type="deviceTagType(deviceOf(anchor)!)" :bordered="false">
                      {{ deviceStateLabel(deviceOf(anchor)!) }}
                    </NTag>
                  </div>
                  <small>{{ deviceOf(anchor)?.locationPath || '未设置安装位置' }}</small>
                  <div class="cluster-device__footer">
                    <span>{{ metricSummary(deviceOf(anchor)) }}</span>
                    <NButton text type="primary" size="small" @click.stop="openDeviceDetail(deviceOf(anchor)!)"
                      >详情</NButton
                    >
                  </div>
                </template>
                <template v-else>
                  <strong>{{ anchor.displayName || anchor.targetName || anchor.targetId }}</strong>
                  <small>设备已停用或不在当前图纸的位置范围内</small>
                </template>
              </div>
            </div>
          </template>
          <template v-else>
            <div class="location-popup">
              <strong>{{ group.primary.displayName || group.primary.targetName || '子位置' }}</strong>
              <NSpace size="small">
                <NButton v-if="editing" text type="error" size="small" @click.stop="emit('remove', group.primary)"
                  >移除</NButton
                >
                <NButton
                  v-if="locationNavigation"
                  text
                  type="primary"
                  size="small"
                  @click.stop="emit('open-location', group.primary.targetId)"
                >
                  进入位置图
                </NButton>
              </NSpace>
            </div>
          </template>
        </LPopup>
      </LMarker>
    </LeafletImage>
    <NEmpty v-else description="底图文件不可用，请重新上传" />
    <div class="map-legend">
      <span><i class="legend-dot legend-dot--normal"></i>正常</span>
      <span><i class="legend-dot legend-dot--warning"></i>告警</span>
      <span><i class="legend-dot legend-dot--offline"></i>离线/无数据</span>
      <span><i class="legend-dot legend-dot--location"></i>下级位置</span>
      <span><i class="legend-dot legend-dot--cluster"></i>重叠设备</span>
    </div>
    <div v-if="editing && pendingTarget" class="placement-tip">请在图上点击放置“{{ pendingTarget.name }}”</div>
    <DeviceDataModal v-model:show="deviceModalShow" :device="detailDevice" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import L from 'leaflet';
import { LMarker, LPopup, LTooltip } from '@vue-leaflet/vue-leaflet';
import { NButton, NEmpty, NSpace, NTag } from 'naive-ui';
import { formatDateTime as format } from '@/utils';
import LeafletImage from '@/components/basic/leaflet/LeafletImage.vue';
import { groupSpatialAnchors, type SpatialAnchorGroup } from '../utils/spatialAnchors';
import { formatMetricValue } from '../utils/metricValue';
import {
  resolveSpatialMetricIcons,
  sortSpatialMetrics,
  spatialMetricIconLabel,
  spatialMetricIconSvg,
  type SpatialMetricIconKind,
} from '../utils/spatialMetricIcon';
import type {
  MuseumLocationPlanViewVo,
  MuseumSpatialAnchorVo,
  MuseumSpatialDeviceVo,
  SpatialAnchorTargetType,
} from '../typings';
import DeviceDataModal from './DeviceDataModal.vue';

const props = withDefaults(
  defineProps<{
    view: MuseumLocationPlanViewVo;
    editing?: boolean;
    pendingTarget?: { type: SpatialAnchorTargetType; id: string; name: string } | null;
    draggingTarget?: boolean;
    locationNavigation?: boolean;
  }>(),
  {
    locationNavigation: true,
  },
);

const emit = defineEmits<{
  (event: 'place', point: { xRatio: number; yRatio: number }): void;
  (event: 'move', payload: { anchor: MuseumSpatialAnchorVo; xRatio: number; yRatio: number }): void;
  (event: 'remove', anchor: MuseumSpatialAnchorVo): void;
  (event: 'device-click', device: MuseumSpatialDeviceVo): void;
  (event: 'open-location', locationId: string): void;
}>();

const devices = computed(() => new Map((props.view.devices || []).map((device) => [device.id, device])));
const mapZoom = ref(0);
const anchorGroups = computed(() =>
  groupSpatialAnchors(
    props.view.anchors || [],
    props.view.plan.imageWidth,
    props.view.plan.imageHeight,
    Boolean(props.editing),
    mapZoom.value,
  ),
);
const imageMapRef = ref<InstanceType<typeof LeafletImage> | null>(null);
const deviceModalShow = ref(false);
const detailDevice = ref<MuseumSpatialDeviceVo | null>(null);

function deviceOf(anchor: MuseumSpatialAnchorVo) {
  return devices.value.get(anchor.targetId);
}

function groupLatLng(group: SpatialAnchorGroup): [number, number] {
  return [group.yRatio * props.view.plan.imageHeight, group.xRatio * props.view.plan.imageWidth];
}

function groupZIndex(group: SpatialAnchorGroup) {
  return Math.max(...group.anchors.map((anchor) => anchor.zIndex || 0));
}

function ratios(lat: number, lng: number) {
  return {
    xRatio: clamp(lng / props.view.plan.imageWidth),
    yRatio: clamp(lat / props.view.plan.imageHeight),
  };
}

function onMapClick(event: any) {
  if (!props.editing || !props.pendingTarget || !event?.latlng) return;
  emit('place', ratios(event.latlng.lat, event.latlng.lng));
}

function onDragOver(event: DragEvent) {
  if (!props.editing || !props.pendingTarget) return;
  if (event.dataTransfer) event.dataTransfer.dropEffect = 'move';
}

function onDrop(event: DragEvent) {
  if (!props.editing || !props.pendingTarget) return;
  const map = imageMapRef.value?.mapInstance;
  if (!map) return;
  const point = map.mouseEventToLatLng(event);
  emit('place', ratios(point.lat, point.lng));
}

function onMove(anchor: MuseumSpatialAnchorVo, event: any) {
  const point = event?.target?.getLatLng?.();
  if (!point) return;
  emit('move', { anchor, ...ratios(point.lat, point.lng) });
}

function onAnchorClick(group: SpatialAnchorGroup) {
  if (props.editing || group.clustered || group.primary.targetType !== 'DEVICE') return;
  const device = deviceOf(group.primary);
  if (device) openDeviceDetail(device);
}

function openDeviceDetail(device: MuseumSpatialDeviceVo) {
  detailDevice.value = device;
  deviceModalShow.value = true;
  emit('device-click', device);
}

function anchorIcon(group: SpatialAnchorGroup): any {
  if (group.clustered) {
    const state = clusterMarkerState(group);
    return L.divIcon({
      className: 'spatial-anchor-wrap',
      html: `<span class="spatial-anchor spatial-anchor--${state} spatial-anchor--cluster"><span class="spatial-anchor__cluster-count">${group.anchors.length}</span></span>`,
      iconSize: [34, 34],
      iconAnchor: [17, 17],
      popupAnchor: [0, -15],
    });
  }

  const anchor = group.primary;
  const device = anchor.targetType === 'DEVICE' ? deviceOf(anchor) : null;
  const state = anchor.targetType === 'LOCATION' ? 'location' : markerState(device);
  const kinds: SpatialMetricIconKind[] =
    anchor.targetType === 'LOCATION' ? ['location'] : resolveSpatialMetricIcons(device);
  const multi = kinds.length > 1;
  const glyphClass = multi ? 'spatial-anchor__glyph--multi' : `spatial-anchor__glyph--${kinds[0]}`;
  const glyphs = kinds.map((kind) => spatialMetricIconSvg(kind)).join('');
  return L.divIcon({
    className: 'spatial-anchor-wrap',
    html: `<span class="spatial-anchor spatial-anchor--${state}${multi ? ' spatial-anchor--multi' : ''}"><span class="spatial-anchor__glyph ${glyphClass}">${glyphs}</span></span>`,
    iconSize: multi ? [32, 32] : [28, 28],
    iconAnchor: multi ? [16, 16] : [14, 14],
    popupAnchor: [0, -12],
  });
}

function deviceIconLabel(anchor: MuseumSpatialAnchorVo) {
  return resolveSpatialMetricIcons(deviceOf(anchor)).map(spatialMetricIconLabel).join(' / ');
}

function clusterMetricLabels(group: SpatialAnchorGroup) {
  const kinds = group.anchors.flatMap((anchor) => resolveSpatialMetricIcons(deviceOf(anchor)));
  return [...new Set(kinds)].slice(0, 4).map(spatialMetricIconLabel).join(' / ') || '其他传感器';
}

function clusterMarkerState(group: SpatialAnchorGroup) {
  const states = group.anchors.map((anchor) => markerState(deviceOf(anchor)));
  if (states.includes('critical')) return 'critical';
  if (states.includes('warning')) return 'warning';
  if (states.every((state) => state === 'offline')) return 'offline';
  return 'normal';
}

function markerState(device?: MuseumSpatialDeviceVo | null) {
  if (!device || device.state === 'NO_DATA' || device.state === 'OFFLINE') return 'offline';
  if (device.activeAlarm?.severity === 'CRITICAL' || device.activeAlarm?.severity === 'MAJOR') return 'critical';
  if (device.activeAlarm) return 'warning';
  return 'normal';
}

function deviceTagType(device: MuseumSpatialDeviceVo): 'success' | 'warning' | 'error' | 'default' {
  const state = markerState(device);
  if (state === 'critical') return 'error';
  if (state === 'warning') return 'warning';
  if (state === 'normal') return 'success';
  return 'default';
}

function deviceStateLabel(device: MuseumSpatialDeviceVo) {
  if (device.state === 'OFFLINE') return '离线';
  if (device.state === 'NO_DATA') return '无数据';
  if (device.activeAlarm) return '告警';
  return '正常';
}

function metricSummary(device?: MuseumSpatialDeviceVo) {
  if (!device?.latestMetrics?.length) return '暂无最新数据';
  return sortedMetrics(device)
    .slice(0, 2)
    .map((metric) => `${metric.name || metric.key} ${formatMetricValue(metric)}`)
    .join(' · ');
}

function sortedMetrics(device?: MuseumSpatialDeviceVo) {
  return sortSpatialMetrics(device?.latestMetrics || []);
}

function formatDateTime(value?: number | null) {
  return value ? format(value) : '--';
}

function clamp(value: number) {
  return Math.min(1, Math.max(0, value));
}
</script>

<style scoped>
.spatial-canvas {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 480px;
  overflow: hidden;
  background: #f3f4f6;
}

.spatial-map {
  width: 100%;
  height: 100%;
}

.spatial-canvas--drop-target::after {
  position: absolute;
  inset: 8px;
  z-index: 650;
  border: 2px dashed #18a058;
  border-radius: 8px;
  pointer-events: none;
  content: '';
}

.placement-tip {
  position: absolute;
  top: 16px;
  left: 50%;
  z-index: 800;
  transform: translateX(-50%);
  padding: 8px 14px;
  border-radius: 18px;
  color: #fff;
  background: rgba(24, 160, 88, 0.92);
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.18);
}

.map-legend {
  position: absolute;
  right: 16px;
  bottom: 16px;
  z-index: 700;
  display: flex;
  gap: 12px;
  padding: 7px 10px;
  border-radius: 6px;
  color: #555;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12);
  font-size: 12px;
}

.map-legend span {
  display: flex;
  align-items: center;
  gap: 4px;
}
.legend-dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: #8b8b8b;
}
.legend-dot--normal {
  background: #18a058;
}
.legend-dot--warning {
  background: #d03050;
}
.legend-dot--location {
  border-radius: 2px;
  background: #2080f0;
}
.legend-dot--cluster {
  border: 2px solid #fff;
  background: #722ed1;
  box-shadow: 0 0 0 1px #722ed1;
}

:deep(.spatial-anchor-wrap) {
  border: 0;
  background: transparent;
}

:deep(.spatial-anchor) {
  display: flex;
  width: 24px;
  height: 24px;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  border: 3px solid #fff;
  border-radius: 50% 50% 50% 0;
  transform: rotate(-45deg);
  color: #fff;
  font-size: 15px;
  font-weight: 700;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.32);
}

:deep(.spatial-anchor--normal) {
  background: #18a058;
}
:deep(.spatial-anchor--warning) {
  background: #f0a020;
}
:deep(.spatial-anchor--critical) {
  background: #d03050;
}
:deep(.spatial-anchor--offline) {
  background: #8b8b8b;
}
:deep(.spatial-anchor--location) {
  background: #2080f0;
  transform: none;
  border-radius: 7px;
}
:deep(.spatial-anchor--multi) {
  width: 28px;
  height: 28px;
}
:deep(.spatial-anchor--cluster) {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  transform: none;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.38);
}
:deep(.spatial-anchor__cluster-count) {
  line-height: 1;
  font-size: 13px;
  font-weight: 700;
}

:deep(.spatial-anchor__glyph) {
  display: flex;
  width: 14px;
  height: 14px;
  align-items: center;
  justify-content: center;
  transform: rotate(45deg);
}

:deep(.spatial-anchor__glyph--location) {
  transform: none;
}
:deep(.spatial-anchor__glyph svg) {
  width: 14px;
  height: 14px;
}
:deep(.spatial-anchor__glyph--multi) {
  width: 20px;
  gap: 1px;
}
:deep(.spatial-anchor__glyph--multi svg) {
  width: 9px;
  height: 9px;
  flex: none;
}

.anchor-tooltip {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.anchor-tooltip small {
  color: #888;
  font-size: 11px;
}
.anchor-hover-card {
  width: 300px;
  padding: 3px 2px;
  white-space: normal;
}
.anchor-hover-card--cluster {
  display: flex;
  width: 330px;
  flex-direction: column;
  gap: 7px;
}
.anchor-hover-card__title {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}
.anchor-hover-card__title small,
.anchor-hover-card--cluster > small {
  color: #888;
  font-size: 11px;
}
.anchor-hover-device {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}
.anchor-hover-device span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.anchor-hover-device strong {
  color: #555;
  font-size: 11px;
  font-weight: 500;
  white-space: nowrap;
}

.device-popup__title,
.device-popup__footer,
.location-popup {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.device-popup__title {
  font-size: 15px;
  font-weight: 600;
}
.device-popup__path {
  margin-top: 4px;
  color: #777;
  font-size: 12px;
}
.device-popup__alarm {
  margin-top: 8px;
  padding: 6px 8px;
  border-radius: 4px;
  color: #d03050;
  background: #fff0f2;
}
.device-popup__metrics {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px 12px;
  margin-top: 10px;
}
.device-popup__metrics div {
  display: flex;
  flex-direction: column;
  color: #777;
  font-size: 12px;
}
.device-popup__metrics strong {
  color: #222;
  font-size: 15px;
}
.device-popup__footer {
  margin-top: 10px;
  color: #888;
  font-size: 12px;
}
.device-popup__footer strong {
  color: #2080f0;
  font-size: 12px;
  font-weight: 500;
}
.orphan-popup p {
  margin: 8px 0;
  color: #888;
  font-size: 12px;
}
.cluster-popup {
  max-height: 360px;
  overflow-y: auto;
}
.cluster-popup__header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 8px;
}
.cluster-popup__header span {
  color: #888;
  font-size: 12px;
}
.cluster-device {
  padding: 9px 0;
  border-top: 1px solid #eee;
}
.cluster-device__title,
.cluster-device__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.cluster-device__title {
  font-weight: 600;
}
.cluster-device > small {
  display: block;
  margin-top: 3px;
  color: #888;
}
.cluster-device__footer {
  margin-top: 5px;
  color: #666;
  font-size: 12px;
}
.cluster-device__footer span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
