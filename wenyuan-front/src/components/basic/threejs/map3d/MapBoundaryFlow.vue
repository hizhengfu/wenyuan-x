<template>
  <TresLineSegments v-if="outlineGeometry" :geometry="outlineGeometry" :render-order="11">
    <TresLineBasicMaterial
      :color="flowConfig.color"
      :transparent="true"
      :opacity="flowConfig.lineOpacity"
      :depth-write="false"
      :depth-test="false"
    />
  </TresLineSegments>
  <TresPoints v-if="flowGeometry" :geometry="flowGeometry" :render-order="12">
    <TresPointsMaterial
      :color="flowConfig.color"
      :size="flowConfig.pointSize"
      :transparent="true"
      :opacity="0.96"
      :depth-write="false"
      :depth-test="false"
    />
  </TresPoints>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue';
import { useRenderLoop } from '@tresjs/core';
import {
  BufferGeometry,
  DynamicDrawUsage,
  Float32BufferAttribute,
} from 'three';

import type {
  Map3DCoordinate,
  Map3DEdgeFlowEffect,
} from './types';

defineOptions({ name: 'MapBoundaryFlow' });

interface ResolvedBoundaryFlowConfig {
  color: string;
  pointSize: number;
  speed: number;
  length: number;
  lineOpacity: number;
  minRingLengthRatio: number;
}

interface BoundaryRingMetric {
  ring: Map3DCoordinate[];
  length: number;
}

interface FlowSegment {
  path: Map3DCoordinate[];
  pointCount: number;
  offset: number;
}

const props = withDefaults(
  defineProps<{
    rings: Map3DCoordinate[][];
    depth?: number;
    config?: Map3DEdgeFlowEffect;
    fallbackColor?: string;
  }>(),
  {
    depth: 6,
    config: () => ({}),
    fallbackColor: '#b9fff6',
  },
);

const outlineGeometry = shallowRef<BufferGeometry | null>(null);
const flowGeometry = shallowRef<BufferGeometry | null>(null);
const flowSegments = shallowRef<FlowSegment[]>([]);
const flowProgress = ref(0);
const configKey = computed(() => JSON.stringify(props.config ?? {}));
const OUTLINE_Z_OFFSET = 0.16;
const FLOW_Z_OFFSET = 0.3;
const flowConfig = computed<ResolvedBoundaryFlowConfig>(() => ({
  color: props.config?.color ?? props.fallbackColor,
  pointSize: props.config?.pointSize ?? 1.35,
  speed: props.config?.speed ?? 0.42,
  length: Math.max(0.02, Math.min(1, props.config?.length ?? 0.18)),
  lineOpacity: props.config?.lineOpacity ?? 0.48,
  minRingLengthRatio: Math.max(0, Math.min(1, props.config?.minRingLengthRatio ?? 0.02)),
}));

function isSamePoint(a?: Map3DCoordinate, b?: Map3DCoordinate) {
  return !!a && !!b && a[0] === b[0] && a[1] === b[1];
}

function resolveRingLength(points: Map3DCoordinate[]) {
  let length = 0;
  for (let index = 1; index < points.length; index += 1) {
    const [x1, y1] = points[index - 1];
    const [x2, y2] = points[index];
    length += Math.hypot(x2 - x1, y2 - y1);
  }
  return length;
}

function resolveClosedRing(points: Map3DCoordinate[]) {
  if (!points.length) {
    return [];
  }

  return isSamePoint(points[0], points[points.length - 1])
    ? points
    : [...points, points[0]];
}

function resolveRenderableRings() {
  const metrics = props.rings
    .map((ring) => {
      const closedRing = resolveClosedRing(ring);
      return {
        ring: closedRing,
        length: resolveRingLength(closedRing),
      };
    })
    .filter((metric): metric is BoundaryRingMetric => metric.ring.length >= 4 && metric.length > 0)
    .sort((a, b) => b.length - a.length);

  if (!metrics.length) {
    return [];
  }

  const threshold = metrics[0].length * flowConfig.value.minRingLengthRatio;
  const filtered = metrics.filter((metric) => metric.length >= threshold);

  return filtered.length ? filtered : [metrics[0]];
}

function sampleRing(points: Map3DCoordinate[], sampleCount: number) {
  if (points.length < 2) {
    return [];
  }

  const ring = resolveClosedRing(points);
  const totalLength = resolveRingLength(ring);
  if (!totalLength) {
    return [];
  }

  const samples: Map3DCoordinate[] = [];
  for (let sampleIndex = 0; sampleIndex < sampleCount; sampleIndex += 1) {
    let target = (sampleIndex / sampleCount) * totalLength;
    for (let index = 1; index < ring.length; index += 1) {
      const [x1, y1] = ring[index - 1];
      const [x2, y2] = ring[index];
      const segmentLength = Math.hypot(x2 - x1, y2 - y1);
      if (target > segmentLength) {
        target -= segmentLength;
        continue;
      }

      const ratio = segmentLength ? target / segmentLength : 0;
      samples.push([
        x1 + (x2 - x1) * ratio,
        y1 + (y2 - y1) * ratio,
      ]);
      break;
    }
  }

  return samples;
}

function disposeFlowGeometry() {
  outlineGeometry.value?.dispose();
  outlineGeometry.value = null;
  flowGeometry.value?.dispose();
  flowGeometry.value = null;
  flowSegments.value = [];
}

function rebuildFlowGeometry() {
  disposeFlowGeometry();

  const ringMetrics = resolveRenderableRings();
  if (!ringMetrics.length) {
    return;
  }

  const outlinePositions: number[] = [];
  ringMetrics.forEach(({ ring }) => {
    const closedRing = resolveClosedRing(ring);
    for (let index = 1; index < closedRing.length; index += 1) {
      const [x1, y1] = closedRing[index - 1];
      const [x2, y2] = closedRing[index];
      outlinePositions.push(
        x1,
        y1,
        props.depth + OUTLINE_Z_OFFSET,
        x2,
        y2,
        props.depth + OUTLINE_Z_OFFSET,
      );
    }
  });

  if (outlinePositions.length) {
    const geometry = new BufferGeometry();
    geometry.setAttribute('position', new Float32BufferAttribute(outlinePositions, 3));
    outlineGeometry.value = geometry;
  }

  let pointOffset = 0;
  const segments = ringMetrics.reduce<FlowSegment[]>((list, { ring, length }) => {
    const sampleCount = Math.max(160, Math.min(1800, Math.round(length * 1.35)));
    const path = sampleRing(ring, sampleCount);
    if (!path.length) {
      return list;
    }

    const pointCount = Math.max(10, Math.min(path.length, Math.round(path.length * flowConfig.value.length)));
    list.push({
      path,
      pointCount,
      offset: pointOffset,
    });
    pointOffset += pointCount;

    return list;
  }, []);
  if (!segments.length) {
    return;
  }

  const geometry = new BufferGeometry();
  const positions = new Float32Array(pointOffset * 3);
  const attribute = new Float32BufferAttribute(positions, 3);
  attribute.setUsage(DynamicDrawUsage);
  geometry.setAttribute('position', attribute);

  flowSegments.value = segments;
  flowGeometry.value = geometry;
  updateFlowGeometry();
}

function updateFlowGeometry() {
  const geometry = flowGeometry.value;
  const segments = flowSegments.value;
  const attribute = geometry?.getAttribute('position') as Float32BufferAttribute | undefined;
  if (!geometry || !segments.length || !attribute) {
    return;
  }

  segments.forEach((segment) => {
    const start = Math.floor(flowProgress.value * segment.path.length) % segment.path.length;
    for (let index = 0; index < segment.pointCount; index += 1) {
      const point = segment.path[(start + index) % segment.path.length];
      attribute.setXYZ(segment.offset + index, point[0], point[1], props.depth + FLOW_Z_OFFSET);
    }
  });
  attribute.needsUpdate = true;
  geometry.computeBoundingSphere();
}

watch(
  [() => props.rings, () => props.depth, configKey],
  () => {
    rebuildFlowGeometry();
  },
  { immediate: true },
);

onBeforeUnmount(() => {
  disposeFlowGeometry();
});

try {
  const { onBeforeLoop } = useRenderLoop();
  onBeforeLoop(({ delta }) => {
    if (!flowGeometry.value || !flowSegments.value.length) {
      return;
    }

    flowProgress.value = (flowProgress.value + delta * flowConfig.value.speed) % 1;
    updateFlowGeometry();
  });
} catch {
  // Unit tests can mount this component without an active Tres canvas.
}
</script>
