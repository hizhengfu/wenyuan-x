<template>
  <TresGroup :position="[0, 0, liftState]">
    <template v-for="polygon in polygonMeshes" :key="polygon.key">
      <TresMesh
        :geometry="polygon.geometry"
        :material="meshMaterials"
        cast-shadow
        receive-shadow
        @pointer-enter="handlePointerEnter"
        @pointer-over="handlePointerEnter"
        @pointer-leave="handlePointerLeave"
        @pointer-out="handlePointerLeave"
        @click="handleClick"
      />
      <TresLineSegments v-if="edgeVisible" :geometry="polygon.edges" :render-order="9">
        <TresLineBasicMaterial
          :color="borderColor"
          :transparent="true"
          :opacity="0.88"
          :linewidth="boundaryLineConfig.width"
          :depth-test="false"
          :depth-write="false"
        />
      </TresLineSegments>
      <TresMesh v-if="boundaryBandVisible" :geometry="polygon.boundaryBand" :render-order="8">
        <TresMeshBasicMaterial
          :color="boundaryLineConfig.color"
          :transparent="true"
          :opacity="boundaryLineConfig.opacity"
          :depth-test="false"
          :depth-write="false"
          :side="DoubleSide"
        />
      </TresMesh>
    </template>
    <TresPoints v-if="edgeFlowGeometry" :geometry="edgeFlowGeometry" :render-order="8">
      <TresPointsMaterial
        :color="edgeFlowConfig.color"
        :size="edgeFlowConfig.pointSize"
        :transparent="true"
        :opacity="0.92"
        :depth-write="false"
      />
    </TresPoints>
  </TresGroup>
</template>

<script setup lang="ts">
import { gsap } from 'gsap';
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue';
import { useRenderLoop } from '@tresjs/core';
import {
  BufferGeometry,
  CanvasTexture,
  Color,
  DoubleSide,
  DynamicDrawUsage,
  ExtrudeGeometry,
  Float32BufferAttribute,
  MeshPhysicalMaterial,
  MeshStandardMaterial,
  Path,
  RepeatWrapping,
  Shape,
  Texture,
  Vector2,
} from 'three';

import type {
  Map3DCoordinate,
  Map3DBounds,
  Map3DPointerPayload,
  Map3DProjectedRegion,
  Map3DVisualEffects,
} from './types';

defineOptions({ name: 'MapRegionMesh' });

interface PolygonMesh {
  key: string;
  geometry: ExtrudeGeometry;
  edges: BufferGeometry;
  boundaryBand: BufferGeometry;
}

interface ResolvedSideScanEffect {
  enabled: boolean;
  color: string;
  scanColor: string;
  opacity: number;
  speed: number;
  width: number;
  direction: 'bottom-to-top' | 'top-to-bottom';
  strength: number;
}

interface ResolvedEdgeFlowEffect {
  enabled: boolean;
  color: string;
  pointSize: number;
  speed: number;
  length: number;
}

interface ResolvedBoundaryLineEffect {
  enabled: boolean;
  color: string;
  width: number;
  opacity: number;
}

const props = withDefaults(
  defineProps<{
    region: Map3DProjectedRegion;
    depth?: number;
    baseColor?: string;
    activeColor?: string;
    borderColor?: string;
    labelColor?: string;
    labelVisible?: boolean;
    interactive?: boolean;
    hoverRaiseScale?: number;
    effects?: Map3DVisualEffects;
    topTexture?: Texture | null;
    textureBounds?: Map3DBounds | null;
    activeRegionKey?: string | null;
  }>(),
  {
    depth: 6,
    baseColor: '#f1d7ae',
    activeColor: '#ffb45b',
    borderColor: '#fff5df',
    labelColor: '#6f3812',
    labelVisible: true,
    interactive: true,
    hoverRaiseScale: 1.25,
    effects: () => ({}),
  },
);

const emit = defineEmits<{
  (e: 'enter', payload: Map3DPointerPayload): void;
  (e: 'leave', payload: Map3DPointerPayload): void;
  (e: 'click', payload: Map3DPointerPayload): void;
}>();

const hovered = ref(false);
const liftState = ref(0);
const HOVER_RAISE_DIRECTION = -1;
const polygonMeshes = shallowRef<PolygonMesh[]>([]);
const meshMaterials = shallowRef<[MeshStandardMaterial, MeshPhysicalMaterial]>();
const ownedTopTexture = shallowRef<Texture | null>(null);
const edgeFlowGeometry = shallowRef<BufferGeometry | null>(null);
const edgeFlowPath = shallowRef<Map3DCoordinate[]>([]);
const sideScanUniforms = shallowRef<{
  uDepth: { value: number };
  uRiseColor: { value: Color };
  uRiseTime: { value: number };
  uRiseWidth: { value: number };
  uRiseDirection: { value: number };
  uRiseStrength: { value: number };
} | null>(null);
const edgeFlowProgress = ref(0);

const effectsKey = computed(() => JSON.stringify(props.effects ?? {}));
const sideScanConfig = computed<ResolvedSideScanEffect>(() => ({
  enabled: !!props.effects?.sideScan?.enabled,
  color: props.effects?.sideScan?.color ?? props.baseColor,
  scanColor: props.effects?.sideScan?.scanColor ?? props.activeColor,
  opacity: props.effects?.sideScan?.opacity ?? 0.92,
  speed: props.effects?.sideScan?.speed ?? 0.32,
  width: props.effects?.sideScan?.width ?? 0.18,
  direction: props.effects?.sideScan?.direction ?? 'top-to-bottom',
  strength: props.effects?.sideScan?.strength ?? 0.88,
}));
const edgeFlowConfig = computed<ResolvedEdgeFlowEffect>(() => ({
  enabled: !!props.effects?.edgeFlow?.enabled && props.effects?.edgeFlow?.scope !== 'outline',
  color: props.effects?.edgeFlow?.color ?? props.borderColor,
  pointSize: props.effects?.edgeFlow?.pointSize ?? 1.2,
  speed: props.effects?.edgeFlow?.speed ?? 0.42,
  length: props.effects?.edgeFlow?.length ?? 0.18,
}));
const boundaryLineConfig = computed<ResolvedBoundaryLineEffect>(() => ({
  enabled: !!props.effects?.boundaryLine?.enabled,
  color: props.effects?.boundaryLine?.color ?? props.borderColor,
  width: Math.max(props.effects?.boundaryLine?.width ?? 1, 0),
  opacity: Math.max(0, Math.min(props.effects?.boundaryLine?.opacity ?? 0.5, 1)),
}));
const edgeVisible = computed(() => !props.activeRegionKey || props.activeRegionKey === props.region.key);
const boundaryBandVisible = computed(() => edgeVisible.value && boundaryLineConfig.value.enabled && boundaryLineConfig.value.width > 0);

function isSamePoint(a?: Map3DCoordinate, b?: Map3DCoordinate) {
  return !!a && !!b && a[0] === b[0] && a[1] === b[1];
}

function normalizeRing(ring: Map3DCoordinate[]) {
  if (ring.length < 3) {
    return [];
  }

  const points = ring.map(([x, y]) => new Vector2(x, y));
  const first = ring[0];
  const last = ring[ring.length - 1];

  return isSamePoint(first, last) ? points.slice(0, -1) : points;
}

function closeCoordinateRing(ring: Map3DCoordinate[]) {
  if (!ring.length) {
    return [];
  }

  return isSamePoint(ring[0], ring[ring.length - 1]) ? ring : [...ring, ring[0]];
}

function createTopOutlineGeometry(rings: Map3DCoordinate[][]) {
  const positions: number[] = [];
  const z = -0.18;

  rings.forEach((ring) => {
    const closedRing = closeCoordinateRing(ring);
    if (closedRing.length < 2) {
      return;
    }

    for (let index = 1; index < closedRing.length; index += 1) {
      const [x1, y1] = closedRing[index - 1];
      const [x2, y2] = closedRing[index];
      positions.push(x1, y1, z, x2, y2, z);
    }
  });

  const geometry = new BufferGeometry();
  geometry.setAttribute('position', new Float32BufferAttribute(positions, 3));

  return geometry;
}

function createTopBoundaryBandGeometry(rings: Map3DCoordinate[][], width: number) {
  const positions: number[] = [];
  const z = -0.2;
  const halfWidth = width / 2;

  rings.forEach((ring) => {
    const closedRing = closeCoordinateRing(ring);
    if (closedRing.length < 2) {
      return;
    }

    for (let index = 1; index < closedRing.length; index += 1) {
      const [x1, y1] = closedRing[index - 1];
      const [x2, y2] = closedRing[index];
      const dx = x2 - x1;
      const dy = y2 - y1;
      const length = Math.hypot(dx, dy);

      if (!length) {
        continue;
      }

      const nx = (-dy / length) * halfWidth;
      const ny = (dx / length) * halfWidth;
      const ax = x1 + nx;
      const ay = y1 + ny;
      const bx = x2 + nx;
      const by = y2 + ny;
      const cx = x2 - nx;
      const cy = y2 - ny;
      const dx2 = x1 - nx;
      const dy2 = y1 - ny;

      positions.push(
        ax, ay, z,
        bx, by, z,
        cx, cy, z,
        ax, ay, z,
        cx, cy, z,
        dx2, dy2, z,
      );
    }
  });

  const geometry = new BufferGeometry();
  geometry.setAttribute('position', new Float32BufferAttribute(positions, 3));
  geometry.computeVertexNormals();

  return geometry;
}

function applyProjectedTextureUv(geometry: ExtrudeGeometry, bounds?: Map3DBounds | null) {
  const position = geometry.getAttribute('position') as Float32BufferAttribute | undefined;
  const uv = geometry.getAttribute('uv') as Float32BufferAttribute | undefined;
  if (!position || !uv || !bounds) {
    return;
  }

  const width = bounds.maxX - bounds.minX;
  const height = bounds.maxY - bounds.minY;
  if (!width || !height) {
    return;
  }

  for (let index = 0; index < position.count; index += 1) {
    const x = position.getX(index);
    const y = position.getY(index);
    uv.setXY(index, (x - bounds.minX) / width, 1 - (y - bounds.minY) / height);
  }

  uv.needsUpdate = true;
}

function disposePolygonMeshes(meshes: PolygonMesh[]) {
  meshes.forEach((mesh) => {
    mesh.boundaryBand.dispose();
    mesh.edges.dispose();
    mesh.geometry.dispose();
  });
}

function disposeMaterials() {
  ownedTopTexture.value?.dispose();
  ownedTopTexture.value = null;
  meshMaterials.value?.forEach((material) => {
    material.dispose();
  });
  meshMaterials.value = undefined;
  sideScanUniforms.value = null;
}

function disposeEdgeFlowGeometry() {
  edgeFlowGeometry.value?.dispose();
  edgeFlowGeometry.value = null;
  edgeFlowPath.value = [];
}

function createProceduralTexture(): Texture | null {
  if (!props.effects?.topTexture?.enabled || typeof document === 'undefined') {
    return null;
  }

  // Real tile-based textures already have an async loading path in the parent.
  // Skipping the heavy procedural fallback keeps first paint responsive.
  if (props.effects.topTexture.mode === 'tianditu') {
    return null;
  }

  const canvas = document.createElement('canvas');
  canvas.width = 512;
  canvas.height = 512;

  let context: CanvasRenderingContext2D | null = null;
  try {
    context = canvas.getContext('2d');
  } catch {
    context = null;
  }

  if (!context) {
    return null;
  }

  const colorToRgb = (color: string, fallback: [number, number, number]): [number, number, number] => {
    const match = color.match(/^#([\da-f]{2})([\da-f]{2})([\da-f]{2})$/i);
    if (!match) {
      return fallback;
    }

    const [, r, g, b] = match;
    return [parseInt(r, 16), parseInt(g, 16), parseInt(b, 16)];
  };
  const clamp01 = (value: number) => Math.max(0, Math.min(1, value));
  const lerp = (start: number, end: number, amount: number) => start + (end - start) * amount;
  const mixColor = (
    start: [number, number, number],
    end: [number, number, number],
    amount: number,
  ): [number, number, number] => [
    lerp(start[0], end[0], amount),
    lerp(start[1], end[1], amount),
    lerp(start[2], end[2], amount),
  ];
  const rgba = (color: [number, number, number], alpha: number) =>
    `rgba(${Math.round(color[0])}, ${Math.round(color[1])}, ${Math.round(color[2])}, ${alpha})`;
  const hash2 = (x: number, y: number) => {
    const value = Math.sin(x * 127.1 + y * 311.7) * 43758.5453123;
    return value - Math.floor(value);
  };
  const valueNoise = (x: number, y: number) => {
    const ix = Math.floor(x);
    const iy = Math.floor(y);
    const fx = x - ix;
    const fy = y - iy;
    const sx = fx * fx * (3 - 2 * fx);
    const sy = fy * fy * (3 - 2 * fy);
    const a = hash2(ix, iy);
    const b = hash2(ix + 1, iy);
    const c = hash2(ix, iy + 1);
    const d = hash2(ix + 1, iy + 1);
    return lerp(lerp(a, b, sx), lerp(c, d, sx), sy);
  };
  const fbm = (x: number, y: number, octaves = 5) => {
    let total = 0;
    let amplitude = 0.5;
    let frequency = 1;
    let normalization = 0;

    for (let i = 0; i < octaves; i += 1) {
      total += valueNoise(x * frequency, y * frequency) * amplitude;
      normalization += amplitude;
      amplitude *= 0.52;
      frequency *= 2.08;
    }

    return total / normalization;
  };

  const gradientColors = props.effects.topTexture.gradientColors ?? ['#227f9b', '#236f92', '#175371'];
  const patchColor = props.effects.topTexture.patchColor ?? '#b8f6ff';
  const lineColor = props.effects.topTexture.lineColor ?? '#ffe8a3';
  const shadowColor = props.effects.topTexture.shadowColor ?? '#0f2a20';
  const lightForest = colorToRgb(gradientColors[0], [125, 169, 83]);
  const midForest = colorToRgb(gradientColors[1], [62, 122, 53]);
  const darkForest = colorToRgb(gradientColors[2], [21, 50, 22]);
  const highlight = colorToRgb(patchColor, [210, 239, 151]);
  const vein = colorToRgb(lineColor, [225, 244, 192]);
  const shadow = colorToRgb(shadowColor, [6, 23, 8]);
  const imageData = context.createImageData(canvas.width, canvas.height);
  const pixels = imageData.data;

  for (let y = 0; y < canvas.height; y += 1) {
    for (let x = 0; x < canvas.width; x += 1) {
      const nx = x / canvas.width;
      const ny = y / canvas.height;
      const broad = fbm(nx * 2.2 + 4.1, ny * 2.4 - 1.8, 5);
      const canopy = fbm(nx * 15.5 + 11.2, ny * 13.5 + 7.4, 5);
      const micro = fbm(nx * 46.0 - 8.0, ny * 43.0 + 5.0, 4);
      const ridgeA = 1 - Math.abs(fbm(nx * 7.2 + ny * 1.6, ny * 10.5 - nx * 1.3, 5) * 2 - 1);
      const ridgeB = 1 - Math.abs(fbm(nx * 13.0 - ny * 2.0, ny * 7.5 + nx * 1.6, 4) * 2 - 1);
      const ridge = Math.pow(clamp01(ridgeA * 0.74 + ridgeB * 0.36), 3.1);
      const valley = Math.pow(fbm(nx * 8.5 - 6.0, ny * 9.4 + 8.0, 4), 2.6);
      const slope = clamp01((nx * 0.28 + (1 - ny) * 0.18) + broad * 0.82);
      const speckle = (hash2(x, y) - 0.5) * 24;

      let color = mixColor(darkForest, midForest, slope);
      color = mixColor(color, lightForest, clamp01(canopy * 0.56 + micro * 0.28 - 0.16));
      color = mixColor(color, highlight, clamp01(ridge * 0.78 + Math.pow(canopy, 8) * 0.42));
      color = mixColor(color, shadow, clamp01(valley * 0.34 + (1 - broad) * 0.18));

      const index = (y * canvas.width + x) * 4;
      pixels[index] = clamp01((color[0] + speckle) / 255) * 255;
      pixels[index + 1] = clamp01((color[1] + speckle * 1.15) / 255) * 255;
      pixels[index + 2] = clamp01((color[2] + speckle * 0.82) / 255) * 255;
      pixels[index + 3] = 255;
    }
  }

  context.putImageData(imageData, 0, 0);

  context.globalCompositeOperation = 'multiply';
  for (let i = 0; i < 96; i += 1) {
    const x = (i * 97 + 19) % 512;
    const y = (i * 59 + 83) % 512;
    const radiusX = 28 + ((i * 17) % 80);
    const radiusY = 8 + ((i * 13) % 34);
    context.save();
    context.translate(x, y);
    context.rotate((i % 11) * 0.29 + 0.45);
    const ridgeShadow = context.createRadialGradient(0, 0, 0, 0, 0, Math.max(radiusX, radiusY));
    ridgeShadow.addColorStop(0, rgba(shadow, 0.22));
    ridgeShadow.addColorStop(1, rgba(shadow, 0));
    context.fillStyle = ridgeShadow;
    context.fillRect(-radiusX, -radiusY, radiusX * 2, radiusY * 2);
    context.restore();
  }

  context.globalCompositeOperation = 'screen';
  for (let i = 0; i < 90; i += 1) {
    const x = (i * 73 + 31) % 512;
    const y = (i * 151 + 47) % 512;
    const radius = 18 + ((i * 11) % 58);
    const patch = context.createRadialGradient(x, y, 0, x, y, radius);
    patch.addColorStop(0, rgba(highlight, 0.16));
    patch.addColorStop(1, rgba(highlight, 0));
    context.fillStyle = patch;
    context.fillRect(x - radius, y - radius, radius * 2, radius * 2);
  }

  context.globalCompositeOperation = 'screen';
  context.lineCap = 'round';
  context.lineJoin = 'round';
  for (let i = 0; i < 180; i += 1) {
    const seedX = hash2(i, 12.7);
    const seedY = hash2(i, 41.9);
    const angle = hash2(i, 88.3) * Math.PI * 2;
    const startX = seedX * 552 - 20;
    const startY = seedY * 552 - 20;
    const length = 8 + hash2(i, 103.5) * 26;
    const bend = (hash2(i, 54.1) - 0.5) * 12;
    const alpha = 0.006 + hash2(i, 7.8) * 0.012;
    const endX = startX + Math.cos(angle) * length;
    const endY = startY + Math.sin(angle) * length;
    const controlX = (startX + endX) * 0.5 + Math.cos(angle + Math.PI / 2) * bend;
    const controlY = (startY + endY) * 0.5 + Math.sin(angle + Math.PI / 2) * bend;
    context.strokeStyle = rgba(vein, alpha);
    context.lineWidth = 0.25 + hash2(i, 3.2) * 0.38;
    context.beginPath();
    context.moveTo(startX, startY);
    context.quadraticCurveTo(controlX, controlY, endX, endY);
    context.stroke();
  }

  context.globalCompositeOperation = 'source-over';
  context.globalAlpha = 1;
  const texture = new CanvasTexture(canvas);
  texture.wrapS = RepeatWrapping;
  texture.wrapT = RepeatWrapping;
  texture.generateMipmaps = true;
  const repeat = props.effects.topTexture.repeat ?? 1;
  texture.repeat.set(repeat, repeat);

  return texture;
}

function createSideMaterial() {
  const config = sideScanConfig.value;
  const material = new MeshPhysicalMaterial({
    color: config.color,
    metalness: 0.04,
    roughness: 0.72,
    transparent: config.opacity < 1,
    opacity: config.opacity,
  });

  if (!config.enabled) {
    return material;
  }

  const uniforms = {
    uDepth: { value: Math.max(props.depth, 1) },
    uRiseColor: { value: new Color(config.scanColor) },
    uRiseTime: { value: -config.width * 2 },
    uRiseWidth: { value: config.width },
    uRiseDirection: { value: config.direction === 'bottom-to-top' ? 1 : 0 },
    uRiseStrength: { value: config.strength },
  };
  sideScanUniforms.value = uniforms;
  material.onBeforeCompile = (shader) => {
    shader.uniforms = {
      ...shader.uniforms,
      ...uniforms,
    };
    shader.vertexShader = shader.vertexShader
      .replace(
        '#include <common>',
        `#include <common>
varying float vMap3DHeight;
varying vec3 vMap3DNormal;`,
      )
      .replace(
        '#include <begin_vertex>',
        `#include <begin_vertex>
vMap3DHeight = transformed.z;
vMap3DNormal = normalize(normal);`,
      );
    shader.fragmentShader = shader.fragmentShader
      .replace(
        '#include <common>',
        `#include <common>
uniform float uDepth;
uniform vec3 uRiseColor;
uniform float uRiseTime;
uniform float uRiseWidth;
uniform float uRiseDirection;
uniform float uRiseStrength;
varying float vMap3DHeight;
varying vec3 vMap3DNormal;

vec3 map3DSideScan(vec3 baseColor) {
  float normalizedHeight = clamp(vMap3DHeight / uDepth, 0.0, 1.0);
  bool isCap = abs(vMap3DNormal.z) > 0.65;
  float scanCoord = uRiseDirection > 0.5 ? 1.0 - normalizedHeight : normalizedHeight;
  float head = smoothstep(uRiseTime, uRiseTime + uRiseWidth, scanCoord);
  float tail = smoothstep(uRiseTime + uRiseWidth * 1.45, uRiseTime + uRiseWidth * 2.8, scanCoord);
  float band = clamp(head - tail, 0.0, 1.0);
  return isCap ? baseColor : mix(baseColor, uRiseColor, band * uRiseStrength);
}`,
      )
      .replace(
        '#include <dithering_fragment>',
        `#include <dithering_fragment>
gl_FragColor.rgb = map3DSideScan(gl_FragColor.rgb);`,
      );
  };
  material.customProgramCacheKey = () => `map3d-side-scan-${config.scanColor}-${config.width}-${config.direction}-${config.strength}`;

  return material;
}

function rebuildMaterials() {
  disposeMaterials();

  const texture = props.topTexture ?? createProceduralTexture();
  ownedTopTexture.value = props.topTexture ? null : texture;
  const topOpacity = props.effects?.topTexture?.opacity ?? 1;
  const usesImageTexture = props.effects?.topTexture?.mode === 'image';
  const topMaterial = new MeshStandardMaterial({
    color: props.baseColor,
    map: texture,
    metalness: props.effects?.topTexture?.enabled ? 0.05 : 0.08,
    roughness: props.effects?.topTexture?.enabled ? 0.82 : 0.7,
    transparent: topOpacity < 1 || usesImageTexture,
    alphaTest: usesImageTexture ? 0.02 : 0,
    opacity: topOpacity,
    side: DoubleSide,
  });
  const sideMaterial = createSideMaterial();

  meshMaterials.value = [topMaterial, sideMaterial];
}

function rebuildPolygonMeshes() {
  disposePolygonMeshes(polygonMeshes.value);

  polygonMeshes.value = props.region.polygons.reduce<PolygonMesh[]>((list, rings, index) => {
    const [outerRing, ...holeRings] = rings;
    const outerPoints = normalizeRing(outerRing);

    if (outerPoints.length < 3) {
      return list;
    }

    const shape = new Shape(outerPoints);

    holeRings.forEach((ring) => {
      const holePoints = normalizeRing(ring);
      if (holePoints.length >= 3) {
        shape.holes.push(new Path(holePoints));
      }
    });

    const geometry = new ExtrudeGeometry(shape, {
      depth: props.depth,
      steps: 1,
      bevelEnabled: false,
    });
    applyProjectedTextureUv(geometry, props.textureBounds);
    const edges = createTopOutlineGeometry([outerRing, ...holeRings]);
    const boundaryBand = createTopBoundaryBandGeometry([outerRing, ...holeRings], boundaryLineConfig.value.width);

    list.push({
      key: `${props.region.key}-${index}`,
      geometry,
      edges,
      boundaryBand,
    });

    return list;
  }, []);

  rebuildEdgeFlowGeometry();
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

function sampleRing(points: Map3DCoordinate[], sampleCount: number) {
  const ring = isSamePoint(points[0], points[points.length - 1])
    ? points
    : [...points, points[0]];
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

function rebuildEdgeFlowGeometry() {
  disposeEdgeFlowGeometry();
  if (!edgeFlowConfig.value.enabled) {
    return;
  }

  const path = props.region.polygons.flatMap(([outerRing]) => sampleRing(outerRing, 120));
  if (!path.length) {
    return;
  }

  const pointCount = Math.max(8, Math.min(path.length, Math.round(path.length * edgeFlowConfig.value.length)));
  const geometry = new BufferGeometry();
  const positions = new Float32Array(pointCount * 3);
  const attribute = new Float32BufferAttribute(positions, 3);
  attribute.setUsage(DynamicDrawUsage);
  geometry.setAttribute('position', attribute);

  edgeFlowPath.value = path;
  edgeFlowGeometry.value = geometry;
  updateEdgeFlowGeometry();
}

function updateEdgeFlowGeometry() {
  const geometry = edgeFlowGeometry.value;
  const path = edgeFlowPath.value;
  const attribute = geometry?.getAttribute('position') as Float32BufferAttribute | undefined;
  if (!geometry || !path.length || !attribute) {
    return;
  }

  const start = Math.floor(edgeFlowProgress.value) % path.length;
  for (let index = 0; index < attribute.count; index += 1) {
    const point = path[(start + index) % path.length];
    attribute.setXYZ(index, point[0], point[1], props.depth + 0.42);
  }
  attribute.needsUpdate = true;
  geometry.computeBoundingSphere();
}

watch(
  [() => props.region, () => props.depth, () => props.textureBounds, effectsKey],
  () => {
    rebuildPolygonMeshes();
  },
  { immediate: true },
);

watch(
  [effectsKey, () => props.baseColor, () => props.activeColor, () => props.depth, () => props.topTexture],
  () => {
    rebuildMaterials();
    rebuildEdgeFlowGeometry();
  },
  { immediate: true },
);

watch(
  [hovered, () => props.depth, () => props.hoverRaiseScale],
  ([isHovered]) => {
    gsap.to(liftState, {
      value: isHovered ? props.depth * 0.45 * props.hoverRaiseScale * HOVER_RAISE_DIRECTION : 0,
      duration: 0.18,
      ease: 'power2.out',
      overwrite: true,
    });
  },
  { immediate: true },
);

watch(
  () => props.baseColor,
  (baseColor) => {
    meshMaterials.value?.[0]?.color.set(baseColor);
  },
  { immediate: true },
);

watch(
  () => props.interactive,
  (interactive) => {
    if (!interactive) {
      hovered.value = false;
    }
  },
);

onBeforeUnmount(() => {
  gsap.killTweensOf(liftState);
  disposePolygonMeshes(polygonMeshes.value);
  disposeMaterials();
  disposeEdgeFlowGeometry();
});

try {
  const { onBeforeLoop } = useRenderLoop();
  onBeforeLoop(({ delta }) => {
    if (sideScanUniforms.value) {
      const config = sideScanConfig.value;
      sideScanUniforms.value.uRiseTime.value += delta * config.speed;
      if (sideScanUniforms.value.uRiseTime.value > 1 + config.width) {
        sideScanUniforms.value.uRiseTime.value = -config.width * 2;
      }
    }

    if (edgeFlowGeometry.value && edgeFlowPath.value.length) {
      edgeFlowProgress.value += delta * edgeFlowConfig.value.speed * edgeFlowPath.value.length;
      updateEdgeFlowGeometry();
    }
  });
} catch {
  // Unit tests mount this component outside a Tres canvas; runtime animation is handled in the canvas loop.
}

function resolveNativeEvent(event: any) {
  return event?.event ?? event?.nativeEvent ?? event;
}

function toPayload(event: any): Map3DPointerPayload {
  const nativeEvent = resolveNativeEvent(event);

  return {
    region: props.region,
    clientX: typeof nativeEvent?.clientX === 'number' ? nativeEvent.clientX : 0,
    clientY: typeof nativeEvent?.clientY === 'number' ? nativeEvent.clientY : 0,
  };
}

function handlePointerEnter(event: any) {
  if (!props.interactive || hovered.value) {
    return;
  }

  event?.stopPropagation?.();
  hovered.value = true;
  emit('enter', toPayload(event));
}

function handlePointerLeave(event: any) {
  if (!props.interactive || !hovered.value) {
    return;
  }

  event?.stopPropagation?.();
  hovered.value = false;
  emit('leave', toPayload(event));
}

function handleClick(event: any) {
  if (!props.interactive) {
    return;
  }

  event?.stopPropagation?.();
  emit('click', toPayload(event));
}
</script>
