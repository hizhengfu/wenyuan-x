<template>
  <div
    ref="containerRef"
    class="map3d"
    @wheel.prevent="handleWheelZoom"
  >
    <TresCanvas class="map3d__canvas">
      <TresPerspectiveCamera
        ref="sceneCameraRef"
        :position="cameraConfig.position"
        :args="[cameraConfig.fov, 1, cameraConfig.near, cameraConfig.far]"
      />

      <TresAmbientLight :intensity="1.8" />
      <TresDirectionalLight :position="[120, 160, 180]" :intensity="1.4" color="#fff4e8" />
      <TresDirectionalLight :position="[-150, 80, -100]" :intensity="0.6" color="#ffffff" />

      <TresGroup :rotation="mapSurfaceRotation">
        <TresGroup :scale="[sceneScale, sceneScale, sceneScale]">
          <TresGroup :position="scenePosition">
            <MapRegionMesh
              v-for="region in projection.regions"
              :key="region.key"
              :region="region"
              :depth="depth"
              :base-color="baseColor"
              :active-color="activeColor"
              :border-color="borderColor"
              :label-color="labelColor"
              :label-visible="labelVisible"
              :hover-raise-scale="hoverRaiseScale"
              :interactive="interactive"
              :effects="effects"
              :top-texture="tiledImageryTexture"
              :texture-bounds="projection.bounds"
              :active-region-key="hoveredRegionKey"
              @enter="handleRegionEnter"
              @leave="handleRegionLeave"
              @click="handleRegionClick"
            />
            <MapBoundaryFlow
              v-if="outlineEdgeFlowVisible"
              :rings="projection.outlineRings"
              :depth="depth"
              :config="effects.edgeFlow"
              :fallback-color="borderColor"
            />
          </TresGroup>
        </TresGroup>
      </TresGroup>
    </TresCanvas>

    <div v-if="mapSurfaceReady && beaconOverlayVisible" class="map3d__beacons" aria-hidden="true">
      <div
        v-for="region in visibleBeaconRegions"
        :key="region.key"
        class="map3d__beacon"
        :style="resolveBeaconStyle(region)"
      >
        <img
          class="map3d__beacon-image"
          :src="effects.beacon?.imageUrl"
          alt=""
        >
        <div
          v-if="beaconLabelVisible"
          class="map3d__beacon-label"
          :class="{ 'map3d__beacon-label--active': hoveredRegionKey === region.key }"
          :style="{ color: effects.beacon?.labelColor ?? '#ffffff' }"
        >
          {{ region.name }}
        </div>
      </div>
    </div>

    <div v-if="mapSurfaceReady && labelVisible && !beaconOverlayVisible" class="map3d__labels" aria-hidden="true">
      <div
        v-for="region in projection.regions"
        :key="region.key"
        class="map3d__label"
        :class="{
          'map3d__label--active': hoveredRegionKey === region.key,
          'map3d__label--beacon': beaconLabelVisible,
        }"
        :style="resolveLabelStyle(region)"
      >
        {{ region.name }}
      </div>
    </div>

    <MapTooltip
      :visible="tooltip.visible"
      :x="tooltip.x"
      :y="tooltip.y"
      :title="tooltip.title"
      :rows="tooltip.rows"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue';
import { TresCanvas } from '@tresjs/core';
import { Texture } from 'three';

import MapBoundaryFlow from './MapBoundaryFlow.vue';
import MapRegionMesh from './MapRegionMesh.vue';
import MapTooltip from './MapTooltip.vue';
import {
  projectMap3DCoordinate,
  projectMap3DLabelCoordinate,
  resolveMap3DAnchorLiftZ,
  resolveMap3DLabelLiftZ,
} from './labelProjection';
import {
  createImageTopTexture,
  createTiledImageryTexture,
  isImageTopTexture,
  isTiledImageryTopTexture,
} from './tileTexture';
import {
  buildMapRegionEvent,
  buildTooltipRows,
  normalizeMap3DInput,
  projectMapRegions,
} from './useMap3D';
import type {
  Map3DCoordinate,
  Map3DPointerPayload,
  Map3DProjectedRegion,
  Map3DRegionEvent,
  Map3DTooltipField,
  Map3DTooltipRow,
  Map3DVisualEffects,
} from './types';

defineOptions({ name: 'Map3D' });

const mapSurfaceRotation: [number, number, number] = [-Math.PI / 2, 0, 0];

interface Map3DCameraConfig {
  position: [number, number, number];
  up: [number, number, number];
  lookAt: [number, number, number];
  fov: number;
  near: number;
  far: number;
}

const props = withDefaults(
  defineProps<{
    data: unknown;
    nameKey?: string;
    depth?: number;
    baseColor?: string;
    activeColor?: string;
    borderColor?: string;
    labelColor?: string;
    labelVisible?: boolean;
    tooltipVisible?: boolean;
    tooltipFields?: Map3DTooltipField[];
    hoverRaiseScale?: number;
    camera?: Partial<Map3DCameraConfig>;
    interactive?: boolean;
    zoomable?: boolean;
    initialScale?: number;
    minScale?: number;
    maxScale?: number;
    zoomStep?: number;
    effects?: Map3DVisualEffects;
  }>(),
  {
    nameKey: 'name',
    depth: 6,
    baseColor: '#f7ead7',
    activeColor: '#ffb35c',
    borderColor: '#fff9f0',
    labelColor: '#7f4515',
    labelVisible: true,
    tooltipVisible: true,
    tooltipFields: () => [],
    hoverRaiseScale: 1.25,
    camera: () => ({}),
    interactive: true,
    zoomable: true,
    initialScale: 1,
    minScale: 0.8,
    maxScale: 2.2,
    zoomStep: 0.12,
    effects: () => ({}),
  },
);

const emit = defineEmits<{
  (e: 'ready'): void;
  (e: 'region-enter', payload: Map3DRegionEvent): void;
  (e: 'region-leave', payload: Map3DRegionEvent): void;
  (e: 'region-click', payload: Map3DRegionEvent): void;
}>();

const normalizedInput = computed(() => normalizeMap3DInput(props.data));
const projection = computed(() => projectMapRegions(normalizedInput.value, { nameKey: props.nameKey }));
const sceneScale = ref(props.initialScale);
const tiledImageryTexture = shallowRef<Texture | null>(null);
const topTextureLoadFailed = ref(false);
const readyEmitted = ref(false);
const tiledImageryLoadKey = computed(() => JSON.stringify({
  bounds: projection.value.geoBounds,
  topTexture: props.effects?.topTexture ?? null,
}));
let tiledImageryLoadId = 0;

const cameraConfig = computed<Map3DCameraConfig>(() => ({
  position: props.camera.position ?? [0, 140, 220],
  up: props.camera.up ?? [0, 1, 0],
  lookAt: props.camera.lookAt ?? [0, 0, 0],
  fov: props.camera.fov ?? 45,
  near: props.camera.near ?? 0.1,
  far: props.camera.far ?? 2000,
}));

const scenePosition = computed<[number, number, number]>(() => {
  const [centerX, centerY] = projection.value.center;
  return [-centerX, 0, centerY];
});
const outlineEdgeFlowVisible = computed(() => (
  !!props.effects?.edgeFlow?.enabled
  && props.effects.edgeFlow.scope !== 'region'
  && projection.value.outlineRings.length > 0
));
const topTexturePending = computed(() => (
  isImageTopTexture(props.effects?.topTexture)
  || (!!projection.value.geoBounds && isTiledImageryTopTexture(props.effects?.topTexture))
));
const mapSurfaceReady = computed(() => !topTexturePending.value || !!tiledImageryTexture.value || topTextureLoadFailed.value);
const beaconVisible = computed(() => !!props.effects?.beacon?.enabled && projection.value.regions.length > 0);
const beaconLabelVisible = computed(() => beaconVisible.value && props.effects?.beacon?.showLabels !== false);
const beaconOverlayVisible = computed(() => beaconVisible.value && !!props.effects?.beacon?.imageUrl);
const beaconLabelLift = computed(() => {
  const beacon = props.effects?.beacon;
  if (!beacon) {
    return 20;
  }

  return beacon.labelLift ?? ((beacon.imageHeight ?? beacon.height ?? 20) + 1.2);
});
const visibleBeaconRegions = computed(() => {
  const includeNames = new Set(props.effects?.beacon?.includeNames ?? []);
  return projection.value.regions.filter((region) => !includeNames.size || includeNames.has(region.name));
});

const containerRef = ref<HTMLElement | null>(null);
const sceneCameraRef = ref<any>(null);
const hoveredRegionKey = ref<string | null>(null);
const tooltip = ref<{
  visible: boolean;
  x: number;
  y: number;
  title: string;
  rows: Map3DTooltipRow[];
}>({
  visible: false,
  x: 0,
  y: 0,
  title: '',
  rows: [],
});

function closeTooltip() {
  tooltip.value.visible = false;
}

function disposeTiledImageryTexture() {
  tiledImageryTexture.value?.dispose();
  tiledImageryTexture.value = null;
}

function emitReadyOnce() {
  if (readyEmitted.value || !mapSurfaceReady.value) {
    return;
  }

  requestAnimationFrame(() => {
    if (readyEmitted.value || !mapSurfaceReady.value) {
      return;
    }

    readyEmitted.value = true;
    emit('ready');
  });
}

function clampScale(nextScale: number) {
  return Math.min(props.maxScale, Math.max(props.minScale, nextScale));
}

function resolveFallbackLabelCoordinate([x, y]: Map3DCoordinate) {
  const bounds = projection.value.bounds;
  if (!bounds) {
    return null;
  }

  const [centerX, centerY] = projection.value.center;
  const halfWidth = Math.max((bounds.maxX - bounds.minX) / 2, 1);
  const halfHeight = Math.max((bounds.maxY - bounds.minY) / 2, 1);

  return {
    left: 50 + ((x - centerX) / halfWidth) * 50 * sceneScale.value,
    top: 50 + ((y - centerY) / halfHeight) * 50 * sceneScale.value,
  };
}

function resolveProjectedMapCoordinate(region: Map3DProjectedRegion, liftZ: number) {
  const rect = containerRef.value?.getBoundingClientRect();
  const camera = sceneCameraRef.value;
  if (
    !rect
    || rect.width <= 0
    || rect.height <= 0
  ) {
    return null;
  }

  return projectMap3DLabelCoordinate({
    labelPosition: region.labelPosition,
    center: projection.value.center,
    sceneScale: sceneScale.value,
    liftZ,
    camera,
  });
}

function resolveProjectedLabelCoordinate(region: Map3DProjectedRegion) {
  return resolveProjectedMapCoordinate(
    region,
    beaconLabelVisible.value
      ? props.depth + beaconLabelLift.value
      : resolveMap3DLabelLiftZ({
          isHovered: hoveredRegionKey.value === region.key,
          depth: props.depth,
          hoverRaiseScale: props.hoverRaiseScale,
        }),
  );
}

function resolveLabelStyle(region: Map3DProjectedRegion) {
  const position = resolveProjectedLabelCoordinate(region)
    ?? resolveFallbackLabelCoordinate(region.labelPosition);
  if (!position) {
    return {};
  }

  return {
    left: `${position.left}%`,
    top: `${position.top}%`,
    color: beaconLabelVisible.value ? (props.effects?.beacon?.labelColor ?? '#ffffff') : props.labelColor,
  };
}

function resolveBeaconStyle(region: Map3DProjectedRegion) {
  const isHovered = hoveredRegionKey.value === region.key;
  const position = projectMap3DCoordinate({
    coordinate: region.geometryCenter,
    center: projection.value.center,
    sceneScale: sceneScale.value,
    liftZ: resolveMap3DAnchorLiftZ({
      baseLiftZ: props.depth + 0.4,
      isHovered,
      depth: props.depth,
      hoverRaiseScale: props.hoverRaiseScale,
    }),
    camera: sceneCameraRef.value,
  }) ?? resolveFallbackLabelCoordinate(region.geometryCenter);
  if (!position) {
    return {};
  }

  const beacon = props.effects?.beacon;
  const scale = Math.max(0.82, Math.min(sceneScale.value, 1.45));
  const height = (beacon?.screenHeight ?? 56) * scale;
  const width = (beacon?.screenWidth ?? height * (83 / 95)) * scale;
  const ringOffset = height * 0.34;
  const labelBottom = height * 0.55 + (beacon?.labelOffset ?? 4);
  const offset = beacon?.positionOffsets?.[region.name];

  return {
    left: `calc(${position.left}% + ${offset?.x ?? 0}px)`,
    top: `calc(${position.top}% + ${offset?.y ?? 0}px)`,
    '--beacon-width': `${width}px`,
    '--beacon-height': `${height}px`,
    '--beacon-ring-offset': `${ringOffset}px`,
    '--beacon-label-bottom': `${labelBottom}px`,
    opacity: `${beacon?.imageOpacity ?? 1}`,
  };
}

watch(
  () => projection.value.warnings.join('|'),
  () => {
    projection.value.warnings.forEach((warning) => console.warn(`[Map3D] ${warning}`));
  },
  { immediate: true },
);

watch(
  tiledImageryLoadKey,
  async () => {
    const effect = props.effects?.topTexture;
    const geoBounds = projection.value.geoBounds;
    const loadId = ++tiledImageryLoadId;

    disposeTiledImageryTexture();
    topTextureLoadFailed.value = false;

    if (!effect) {
      emitReadyOnce();
      return;
    }

    try {
      const texture = isImageTopTexture(effect)
        ? await createImageTopTexture(effect)
        : geoBounds && isTiledImageryTopTexture(effect)
          ? await createTiledImageryTexture(geoBounds, effect)
          : null;
      if (loadId !== tiledImageryLoadId) {
        texture?.dispose();
        return;
      }

      tiledImageryTexture.value = texture;
      emitReadyOnce();
    } catch (error) {
      if (loadId === tiledImageryLoadId) {
        topTextureLoadFailed.value = true;
        console.warn('[Map3D] 地图顶部纹理加载失败，已回退到程序化纹理。', error);
        emitReadyOnce();
      }
    }
  },
  { immediate: true },
);

watch(mapSurfaceReady, () => {
  emitReadyOnce();
}, { immediate: true });

watch(
  () => [props.tooltipVisible, props.interactive, projection.value.regions] as const,
  ([tooltipVisible, interactive, regions]) => {
    if (!tooltipVisible || !interactive || !regions.length) {
      closeTooltip();
    }
  },
);

watch(
  () => [props.data, props.nameKey, props.tooltipFields] as const,
  () => {
    closeTooltip();
  },
);

watch(
  () => [props.initialScale, props.minScale, props.maxScale] as const,
  ([initialScale]) => {
    sceneScale.value = clampScale(initialScale);
  },
  { immediate: true },
);

function syncCameraViewport() {
  const camera = sceneCameraRef.value;
  const rect = containerRef.value?.getBoundingClientRect();
  if (
    !camera
    || !rect
    || rect.width <= 0
    || rect.height <= 0
    || typeof camera.updateProjectionMatrix !== 'function'
  ) {
    return;
  }

  camera.aspect = rect.width / rect.height;
  camera.updateProjectionMatrix();
  if (typeof camera.updateMatrixWorld === 'function') {
    camera.updateMatrixWorld(true);
  }
}

watch(
  [cameraConfig, projection],
  async () => {
    await nextTick();

    const camera = sceneCameraRef.value;
    if (!camera) {
      return;
    }

    if (
      typeof camera.updateProjectionMatrix !== 'function'
      || typeof camera.lookAt !== 'function'
      || typeof camera.position?.set !== 'function'
      || typeof camera.up?.set !== 'function'
    ) {
      return;
    }

    camera.fov = cameraConfig.value.fov;
    camera.near = cameraConfig.value.near;
    camera.far = cameraConfig.value.far;
    camera.up.set(...cameraConfig.value.up);
    camera.position.set(...cameraConfig.value.position);
    camera.lookAt(...cameraConfig.value.lookAt);
    syncCameraViewport();
  },
  { immediate: true },
);

let resizeObserver: ResizeObserver | null = null;

onMounted(() => {
  if (typeof ResizeObserver === 'undefined' || !containerRef.value) {
    return;
  }

  resizeObserver = new ResizeObserver(() => {
    syncCameraViewport();
  });
  resizeObserver.observe(containerRef.value);
});

onBeforeUnmount(() => {
  resizeObserver?.disconnect();
  resizeObserver = null;
  tiledImageryLoadId += 1;
  disposeTiledImageryTexture();
});

function openTooltip(payload: Map3DPointerPayload) {
  if (!props.tooltipVisible || !props.interactive) {
    return;
  }

  const bounds = containerRef.value?.getBoundingClientRect();
  const x = bounds ? payload.clientX - bounds.left : payload.clientX;
  const y = bounds ? payload.clientY - bounds.top : payload.clientY;

  tooltip.value = {
    visible: true,
    x,
    y,
    title: payload.region.name,
    rows: buildTooltipRows(payload.region.properties, {
      nameKey: props.nameKey,
      fields: props.tooltipFields,
    }),
  };
}

function handleRegionEnter(payload: Map3DPointerPayload) {
  hoveredRegionKey.value = payload.region.key;
  openTooltip(payload);
  emit('region-enter', buildMapRegionEvent(payload.region, 'enter'));
}

function handleRegionLeave(payload: Map3DPointerPayload) {
  if (hoveredRegionKey.value === payload.region.key) {
    hoveredRegionKey.value = null;
  }
  closeTooltip();
  emit('region-leave', buildMapRegionEvent(payload.region, 'leave'));
}

function handleRegionClick(payload: Map3DPointerPayload) {
  emit('region-click', buildMapRegionEvent(payload.region, 'click'));
}

function handleWheelZoom(event: WheelEvent) {
  if (!props.zoomable) {
    return;
  }

  const direction = event.deltaY < 0 ? 1 : -1;
  sceneScale.value = clampScale(sceneScale.value + direction * props.zoomStep);
}
</script>

<style scoped lang="scss">
.map3d {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 320px;
  overflow: hidden;
  border-radius: 18px;
  background:
    radial-gradient(circle at top, rgba(255, 244, 229, 0.96), rgba(241, 224, 203, 0.94) 42%, rgba(231, 213, 192, 0.98) 100%);
}

.map3d__canvas {
  width: 100%;
  height: 100%;
}

.map3d__labels {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 2;
}

.map3d__beacons {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 2;
}

.map3d__beacon {
  position: absolute;
  width: 0;
  height: 0;
  transform: translate3d(0, 0, 0);
  transition:
    left 0.18s ease-out,
    top 0.18s ease-out;
  overflow: visible;
}

.map3d__beacon-image {
  position: absolute;
  left: 0;
  bottom: calc(var(--beacon-ring-offset) * -1);
  width: var(--beacon-width);
  height: var(--beacon-height);
  max-width: none;
  transform: translateX(-50%);
  object-fit: fill;
  filter:
    saturate(0)
    brightness(1.24)
    contrast(1.08)
    drop-shadow(0 0 6px rgba(255, 255, 255, 0.74));
  user-select: none;
}

.map3d__beacon-label {
  position: absolute;
  left: 0;
  bottom: var(--beacon-label-bottom);
  transform: translateX(-50%);
  padding: 5px 11px 6px;
  border: 1px solid rgba(236, 255, 243, 0.88);
  border-radius: 4px;
  background:
    linear-gradient(180deg, rgba(13, 91, 62, 0.96) 0%, rgba(4, 50, 42, 0.96) 100%);
  box-shadow:
    0 0 10px rgba(21, 234, 168, 0.28),
    inset 0 0 12px rgba(106, 255, 198, 0.12);
  color: #ffffff;
  font-size: 24px;
  font-weight: 800;
  line-height: 1;
  letter-spacing: 0.02em;
  white-space: nowrap;
  text-shadow:
    0 1px 2px rgba(0, 0, 0, 0.52),
    0 0 8px rgba(130, 255, 200, 0.24);
}

.map3d__beacon-label--active {
  transform: translateX(-50%) scale(1.04);
}

.map3d__label {
  position: absolute;
  transform: translate(-50%, -50%);
  white-space: nowrap;
  font-size: 24px;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: #ffffff;
  text-shadow:
    0 0 12px rgba(8, 36, 44, 0.72),
    0 0 18px rgba(8, 36, 44, 0.48);
  transition:
    transform 0.18s ease,
    text-shadow 0.18s ease,
    opacity 0.18s ease;
}

.map3d__label--active {
  transform: translate(-50%, -50%) scale(1.08);
  text-shadow:
    0 0 14px rgba(99, 226, 213, 0.66),
    0 0 22px rgba(13, 61, 66, 0.82);
}

.map3d__label--beacon {
  transform: translate(-50%, -108%);
  padding: 5px 11px 6px;
  border: 1px solid rgba(236, 255, 243, 0.88);
  border-radius: 4px;
  background:
    linear-gradient(180deg, rgba(13, 91, 62, 0.96) 0%, rgba(4, 50, 42, 0.96) 100%);
  box-shadow:
    0 0 10px rgba(21, 234, 168, 0.28),
    inset 0 0 12px rgba(106, 255, 198, 0.12);
  font-size: 24px;
  font-weight: 800;
  letter-spacing: 0.02em;
  text-shadow:
    0 1px 2px rgba(0, 0, 0, 0.52),
    0 0 8px rgba(130, 255, 200, 0.24);
}

.map3d__label--beacon.map3d__label--active {
  transform: translate(-50%, -108%) scale(1.04);
}
</style>
