<template>
  <div ref="containerRef" class="sog-viewer">
    <div v-if="directPreviewUnavailable" class="sog-overlay">
      <n-icon size="52" depth="3"><IconMdiCubeOutline /></n-icon>
      <div class="sog-title">场景过大，无法直接在线预览</div>
      <div class="sog-description">
        {{ resourceSummary }}。此资源是完整 SOG，浏览器需要一次性解码并创建大型显存纹理，容易导致内存分配失败。
        请保留原文件，并另行生成低精度或 Streamed SOG 预览资源包后导入。
      </div>
    </div>
    <div v-else-if="awaitingConfirmation" class="sog-overlay">
      <n-icon size="52" depth="3"><IconMdiCubeOutline /></n-icon>
      <div class="sog-title">大型高斯场景</div>
      <div class="sog-description">
        {{ resourceSummary }}。加载时可能占用较大显存，建议使用具备独立显卡的桌面设备。
      </div>
      <n-button type="primary" @click="startLoad">加载高斯预览</n-button>
    </div>

    <div v-else-if="status === 'loading' && !streamed" class="sog-overlay">
      <n-spin size="large" />
      <div class="sog-title">正在加载高斯场景</div>
      <n-progress type="line" :percentage="progress" :show-indicator="true" class="sog-progress" />
      <div class="sog-description">SOG 读取完成后将直接进入渲染阶段。</div>
    </div>
    <div v-else-if="status === 'loading' && streamed" class="sog-loading-indicator">
      <n-spin size="small" />
      <span>{{ coarseTotal ? `正在补齐低精度全景 ${coarseLoaded}/${coarseTotal}，已有分块可直接查看` : '正在读取高斯场景层级清单' }}</span>
    </div>

    <div v-else-if="status === 'error'" class="sog-overlay">
      <n-icon size="48" color="#d03050"><IconMdiAlertCircleOutline /></n-icon>
      <div class="sog-title">高斯场景加载失败</div>
      <div class="sog-description">{{ error }}</div>
      <n-button secondary @click="startLoad">重新加载</n-button>
    </div>

    <div v-if="status === 'ready' && streamed && manifest" class="sog-levels">
      <label for="sog-level">显示精度</label>
      <select id="sog-level" :value="selectedLevel" @change="onLevelChange">
        <option :value="-1">自动 · 按视角</option>
        <option v-for="level in manifest.lodLevels" :key="level" :value="level - 1"
          :disabled="!canSelectSogLevel(platform.mobile, manifest.counts[level - 1])">
          目标 LOD {{ level - 1 }} · {{ (manifest.counts[level - 1] / 10000).toFixed(1) }} 万点{{ !canSelectSogLevel(platform.mobile, manifest.counts[level - 1]) ? ' · 移动端超限' : '' }}
        </option>
      </select>
      <span v-if="selectedLevel >= 0">{{ levelLoading ? `正按视角逐区域增强至 LOD ${selectedLevel}` : `当前视角细节已稳定 · 目标 LOD ${selectedLevel}` }}</span>
      <span v-else>{{ levelLoading ? '正在按视角补充细节，低精度画面可用' : '按视角动态选择细节' }}</span>
    </div>
    <div v-if="status === 'ready' && cameraMode === 'fly'" class="sog-fly-speed">
      <label for="sog-fly-speed">移动速度</label>
      <select id="sog-fly-speed" v-model.number="flySpeedMultiplier">
        <option :value="0.25">0.25×</option>
        <option :value="0.5">0.5×</option>
        <option :value="1">1×</option>
        <option :value="2">2×</option>
        <option :value="4">4×</option>
      </select>
    </div>
    <div v-if="status === 'ready'" class="sog-hint">
      {{ cameraMode === 'orbit'
        ? '正立环绕 · 左拖旋转 / 右拖平移 · V 自由飞行'
        : '自由飞行 · 左拖转向 / WASDQE 移动 · V 正立环绕' }}
    </div>
    <div v-if="status === 'ready' && levelError" class="sog-level-error">
      {{ levelError }}
      <n-button v-if="nativeStreamed" size="tiny" secondary @click="startLoad">重新加载</n-button>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { getAuthenticationHeaders } from '@/utils/auth';
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import {
  Application,
  Asset,
  Color,
  Entity,
  FILLMODE_NONE,
  GSPLAT_LODMODE_DISTANCE,
  RESOLUTION_AUTO,
  Vec3,
  platform,
} from 'playcanvas';
import { useAuthStore } from '@/store';
import { apiUrl, fileUrl } from '@/utils/path';
import {
  buildSogBundleFromPackage,
  type SogPackageFile,
} from '../utils/sogBundle';
import { chunkPackagePath, fileIndicesByView, fileIndicesForLevel, filesForChunk, initialLevelForStreamedSog, parseStreamedSogManifest, type StreamedSogManifest } from '../utils/streamedSog';
import { createNativeStreamedSog, PackageSogAssetLoader, type NativeSogResource } from '../utils/nativeStreamedSog';
import { canSelectSogLevel, streamedSogBudget, streamedSogLodRange } from '../utils/sogViewerQuality';
import { sogMoveDeltaSeconds, sogSceneFlySpeed } from '../utils/sogCameraMotion';
import { configuredCameraView, previewBackground, type ResourcePreviewConfig } from '../utils/previewConfig';
import { sourceSceneUp } from '../utils/cameraOrientation';
import { getStreamedSogLevelFiles, streamedSogFileUrl } from '../api/resource';

type PreviewDescriptor = {
  vertexCount?: number;
  sphericalHarmonicsDegree?: number;
  boundsMin?: number[];
  boundsMax?: number[];
};

const LARGE_FILE_BYTES = 64 * 1024 * 1024;
const LARGE_SPLAT_COUNT = 4_000_000;
// 普通 SOG 会整体装配并解码；超出此规模时不要继续触发浏览器内存/显存耗尽。
const MAX_DIRECT_FILE_BYTES = platform.mobile ? 64 * 1024 * 1024 : 256 * 1024 * 1024;
const MAX_DIRECT_SPLAT_COUNT = platform.mobile ? 1_000_000 : 16_000_000;

const props = defineProps<{
  src: string;
  ext?: string;
  fileSize?: number;
  descriptor?: PreviewDescriptor;
  packageFiles?: SogPackageFile[];
  /** 可选的直接 URL 清单解析器；未传 resourceId 时不查询资源文件系统，目录分块按需读取其 meta.json。 */
  resolveSourceFiles?: (path: string, signal: AbortSignal) => Promise<SogPackageFile[]>;
  /** 直接文件服务可缺少 Content-Length，按实际下载字节限制内存；不传时保留资源模块原策略。 */
  maxDownloadBytes?: number;
  previewConfig?: ResourcePreviewConfig | null;
  streamed?: boolean;
  resourceId?: string;
  resourceVersion?: number;
  resourceStatus?: string;
}>();
const emit = defineEmits<{ ready: [ready: boolean] }>();

const authStore = useAuthStore();
const containerRef = ref<HTMLDivElement>();
const status = ref<'idle' | 'loading' | 'ready' | 'error'>('idle');
const progress = ref(0);
const error = ref('');
const userConfirmed = ref(false);
const manifest = ref<StreamedSogManifest | null>(null);
const currentLevel = ref(-1);
const selectedLevel = ref<number>(-1);
const levelLoading = ref(false);
const levelError = ref('');
const nativeStreamed = ref(false);
const coarseLoaded = ref(0);
const coarseTotal = ref(0);
const cameraMode = ref<'orbit' | 'fly'>('orbit');
const flySpeedMultiplier = ref(1);

let app: Application | null = null;
let canvasEl: HTMLCanvasElement | null = null;
let unbindOrbit: (() => void) | null = null;
let resizeObserver: ResizeObserver | null = null;
let generation = 0;
let downloadController: AbortController | null = null;
let cameraEntity: Entity | null = null;
let orbitFocus: Vec3 | null = null;
let orbitUp: Vec3 | null = null;
let sourceBounds: { min: [number, number, number]; max: [number, number, number] } | null = null;
let activeEntities: Entity[] = [];
let activeAssets: Asset[] = [];
let nativeEntity: Entity | null = null;
let nativeAsset: Asset | null = null;
let nativeResource: NativeSogResource | null = null;
let nativeLoader: PackageSogAssetLoader | null = null;
let nativeFileUrls: string[] = [];
let nativeObjectUrl: string | null = null;
let unbindNativeFrame: (() => void) | null = null;
let nativeFailure = false;
let levelFilePromises = new Map<number, Promise<SogPackageFile[]>>();
let resolvedPackageFiles = new Map<string, SogPackageFile>();
let chunkLevelByPath = new Map<string, number>();

let autoSplatBudget = streamedSogBudget(platform.mobile);

function prioritizeNativeFiles(source = manifest.value) {
  if (!source || !cameraEntity || !nativeLoader || !nativeFileUrls.length) return;
  const position = cameraEntity.getPosition();
  const forward = cameraEntity.forward;
  const indices = fileIndicesByView(source, {
    position: [position.x, position.y, position.z],
    forward: [forward.x, forward.y, forward.z],
  });
  nativeLoader.setPriorityOrder(indices.map((index) => nativeFileUrls[index]));
}

function captureView(): ResourcePreviewConfig | null {
  if (status.value !== 'ready' || !cameraEntity || !orbitFocus || !orbitUp) return null;
  const p = cameraEntity.getPosition();
  return {
    cameraPosition: [p.x, p.y, p.z],
    cameraTarget: [orbitFocus.x, orbitFocus.y, orbitFocus.z],
    cameraUp: [orbitUp.x, orbitUp.y, orbitUp.z],
    cameraFov: cameraEntity.camera?.fov || 60,
    backgroundColor: previewBackground(props.previewConfig, '#111827'),
  };
}

function restoreSavedView() {
  if (!cameraEntity || status.value !== 'ready') return;
  const frame = cameraFrame();
  unbindOrbit?.();
  cameraEntity.camera!.fov = frame.fov;
  cameraEntity.camera!.clearColor = colorFromHex(previewBackground(props.previewConfig, '#111827'));
  unbindOrbit = bindOrbit(cameraEntity, frame.center as [number, number, number], frame.distance, frame.position as [number, number, number], frame.up as [number, number, number], frame.flySpeed);
  prioritizeNativeFiles();
  if (app) app.renderNextFrame = true;
}

defineExpose({ captureView, restoreSavedView });

const splatCount = computed(() => Number(props.descriptor?.vertexCount || 0));
const packageSize = computed(() => (props.packageFiles?.length || 0) > 1
  ? (props.packageFiles || []).reduce((sum, file) => sum + Number(file.fileSize || 0), 0)
  : Number(props.fileSize || 0));
const directPreviewUnavailable = computed(() => !props.streamed &&
  (packageSize.value >= MAX_DIRECT_FILE_BYTES || splatCount.value >= MAX_DIRECT_SPLAT_COUNT));
const largeScene = computed(
  () => packageSize.value >= LARGE_FILE_BYTES || splatCount.value >= LARGE_SPLAT_COUNT,
);
const awaitingConfirmation = computed(() => status.value === 'idle' && !props.streamed && largeScene.value && !userConfirmed.value);
const resourceSummary = computed(() => {
  const parts: string[] = [];
  if (splatCount.value > 0) parts.push(`${(splatCount.value / 10_000).toFixed(1)} 万个高斯点`);
  if (packageSize.value) parts.push(`${(packageSize.value / 1024 / 1024).toFixed(1)} MiB`);
  return parts.join('，') || '该资源体积较大';
});

/** 普通目录版的主文件是 meta.json；Streamed SOG 分块由原生 octree 按需装配。 */
const directoryMode = computed(() => {
  if ((props.ext || '').toLowerCase() === 'sog') return false;
  return (props.packageFiles?.length || 0) > 1;
});

function authHeadersFor(url: string) {
  try {
    const target = new URL(url, window.location.href);
    const apiOrigin = new URL(apiUrl('resource'), window.location.href).origin;
    if ((target.origin !== window.location.origin && target.origin !== apiOrigin)) return undefined;
    return getAuthenticationHeaders(authStore.getToken, authStore.authorizationName);
  } catch {
    return undefined;
  }
}

async function fetchBytes(url: string, signal: AbortSignal, reportProgress = true, revalidate = false): Promise<Uint8Array> {
  const target = new URL(url, window.location.href);
  const apiOrigin = new URL(apiUrl('resource'), window.location.href).origin;
  const response = await fetch(url, {
    headers: authHeadersFor(url),
    credentials: target.origin === apiOrigin ? 'include' : 'same-origin',
    cache: revalidate ? 'default' : 'no-store',
    signal,
  });
  if (!response.ok) throw new Error(`资源请求失败：HTTP ${response.status}`);
  // Native streaming has its own progress events. Avoid retaining all network
  // chunks and then allocating a second full-size merged buffer.
  if (!reportProgress && !props.maxDownloadBytes) return new Uint8Array(await response.arrayBuffer());
  const total = Number(response.headers.get('content-length') || 0);
  if (!response.body || (!total && !props.maxDownloadBytes)) return new Uint8Array(await response.arrayBuffer());
  const byteLimit = props.maxDownloadBytes ? Math.min(props.maxDownloadBytes, MAX_DIRECT_FILE_BYTES) : Infinity;
  if (total > byteLimit) { await response.body.cancel(); throw new Error('SOG 分块或文件超过当前设备的安全加载大小，请使用更小分块的分层场景'); }

  const reader = response.body.getReader();
  const chunks: Uint8Array[] = [];
  let loaded = 0;
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    chunks.push(value);
    loaded += value.length;
    if (loaded > byteLimit) { await reader.cancel(); throw new Error('SOG 实际下载数据超过安全加载大小，请改用分层场景'); }
    if (reportProgress && total) progress.value = Math.min(99, Math.round((loaded / total) * 100));
  }
  const merged = new Uint8Array(loaded);
  let offset = 0;
  chunks.forEach((chunk) => {
    merged.set(chunk, offset);
    offset += chunk.length;
  });
  return merged;
}

async function assembleSog(signal: AbortSignal): Promise<ArrayBuffer> {
  if (!directoryMode.value) {
    const bytes = await fetchBytes(props.src, signal);
    return bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength) as ArrayBuffer;
  }
  return buildSogBundleFromPackage(props.packageFiles || [], async (file) => {
    if (!file.filePath) throw new Error('资源包文件缺少访问路径');
    return fetchBytes(fileUrl(file.filePath), signal);
  });
}

function cameraFrame() {
  // 根清单／已解码 SOG 的边界来自原始点云；后端描述仅在源文件不可用时兜底。
  const min = manifest.value?.tree.bound?.min || sourceBounds?.min || props.descriptor?.boundsMin;
  const max = manifest.value?.tree.bound?.max || sourceBounds?.max || props.descriptor?.boundsMax;
  const up = sourceSceneUp(min, max);
  const flySpeed = sogSceneFlySpeed(min, max);
  const configured = configuredCameraView(props.previewConfig);
  if (configured) {
    const distance = Math.max(1, Math.hypot(
      configured.position[0] - configured.target[0],
      configured.position[1] - configured.target[1],
      configured.position[2] - configured.target[2],
    ));
    return {
      center: configured.target,
      position: configured.position,
      up,
      fov: configured.fov || 60,
      distance,
      flySpeed,
    };
  }
  const valid = min?.length === 3 && max?.length === 3 && [...min, ...max].every(Number.isFinite);
  if (!valid) return {
    center: [0, 0, 0] as [number, number, number],
    position: [5, 5, 5] as [number, number, number],
    up: [0, 1, 0] as [number, number, number],
    fov: 60,
    distance: 8,
    flySpeed,
  };
  const center: [number, number, number] = [
    (min![0] + max![0]) / 2,
    (min![1] + max![1]) / 2,
    (min![2] + max![2]) / 2,
  ];
  const diagonal = Math.hypot(max![0] - min![0], max![1] - min![1], max![2] - min![2]);
  const distance = Math.max(2, diagonal * 1.2);
  const position: [number, number, number] = up[2]
    ? [center[0] + distance * 0.75, center[1] - distance, center[2] + distance * 0.55]
    : [center[0] + distance * 0.7, center[1] + distance * 0.45, center[2] + distance];
  return {
    center,
    position,
    up,
    fov: 60,
    distance,
    flySpeed,
  };
}

/** 与 SuperSplat 常用轨道/飞行操作一致，焦点仍供视角保存与恢复使用。 */
function bindOrbit(
  camera: Entity,
  center: [number, number, number],
  distance: number,
  initialPosition: [number, number, number],
  up: [number, number, number],
  flySpeed: number,
) {
  const canvas = canvasEl;
  if (!canvas) return () => {};
  const focus = new Vec3(center[0], center[1], center[2]);
  orbitFocus = focus;
  const upVector = new Vec3(up[0], up[1], up[2]).normalize();
  orbitUp = upVector;
  const baseForward = Math.abs(upVector.z) > 0.98 ? new Vec3(0, 1, 0) : new Vec3(0, 0, 1);
  const basisZ = baseForward.sub(upVector.clone().mulScalar(baseForward.dot(upVector))).normalize();
  const basisX = new Vec3().cross(upVector, basisZ).normalize();
  const offset = new Vec3(
    initialPosition[0] - center[0],
    initialPosition[1] - center[1],
    initialPosition[2] - center[2],
  );
  const initialRadius = Math.max(0.001, offset.length());
  const spherical = {
    theta: Math.atan2(offset.dot(basisX), offset.dot(basisZ)),
    phi: Math.min(Math.PI - 0.05, Math.max(0.05,
      Math.acos(Math.min(1, Math.max(-1, offset.dot(upVector) / initialRadius))))),
    radius: initialRadius,
  };
  // 环绕模式始终停在地平线上方；查看场景下方请使用自由飞行。
  const orbitMinPhi = Math.PI / 36;
  const orbitMaxPhi = Math.PI / 2;
  spherical.phi = Math.min(orbitMaxPhi, Math.max(orbitMinPhi, spherical.phi));
  const clampPhi = (phi: number, mode: 'orbit' | 'fly') => Math.min(
    mode === 'orbit' ? orbitMaxPhi : Math.PI - 0.01,
    Math.max(mode === 'orbit' ? orbitMinPhi : 0.01, phi),
  );
  const homeCenter = focus.clone();
  const homeAngles = { theta: spherical.theta, phi: spherical.phi, radius: spherical.radius };
  const minRadius = Math.max(0.01, distance * 0.0005);
  const maxRadius = Math.max(minRadius * 2, distance * 100);
  const moveKeys = new Set<string>();
  const touches = new Map<number, { x: number; y: number }>();
  let mousePointer: number | null = null;
  let mouseButton = -1;
  let lastX = 0;
  let lastY = 0;
  let touchCenter: { x: number; y: number; span: number } | null = null;
  let animationFrame: number | null = null;
  let lastFrameTime = 0;
  cameraMode.value = 'orbit';
  canvas.tabIndex = 0;
  canvas.setAttribute('aria-label', '高斯场景三维视图，点击后可使用 V 和 WASD 控制相机');
  canvas.style.touchAction = 'none';

  const radial = () => {
    const sinPhi = Math.sin(spherical.phi);
    return basisX.clone().mulScalar(sinPhi * Math.sin(spherical.theta))
      .add(upVector.clone().mulScalar(Math.cos(spherical.phi)))
      .add(basisZ.clone().mulScalar(sinPhi * Math.cos(spherical.theta)));
  };
  const right = () => basisX.clone().mulScalar(Math.cos(spherical.theta))
    .sub(basisZ.clone().mulScalar(Math.sin(spherical.theta)));
  const forward = () => radial().mulScalar(-1);

  const applyOrbit = () => {
    camera.setPosition(focus.clone().add(radial().mulScalar(spherical.radius)));
    camera.lookAt(focus, upVector);
  };
  const applyFlyLook = () => {
    focus.copy(camera.getPosition()).add(forward().mulScalar(spherical.radius));
    camera.lookAt(focus, upVector);
  };
  const rotate = (dx: number, dy: number) => {
    spherical.theta -= dx * 0.005;
    spherical.phi = clampPhi(spherical.phi - dy * 0.005, cameraMode.value);
    if (cameraMode.value === 'fly') applyFlyLook();
    else applyOrbit();
  };
  const pan = (dx: number, dy: number) => {
    const height = Math.max(1, canvas.clientHeight);
    const scale = 2 * spherical.radius * Math.tan((camera.camera?.fov || 60) * Math.PI / 360) / height;
    const upOnScreen = new Vec3().cross(radial(), right()).normalize();
    const movement = right().mulScalar(-dx * scale).add(upOnScreen.mulScalar(dy * scale));
    focus.add(movement);
    camera.setPosition(camera.getPosition().clone().add(movement));
    camera.lookAt(focus, upVector);
  };
  const move = (direction: Vec3, amount: number) => {
    const delta = direction.mulScalar(amount);
    focus.add(delta);
    camera.setPosition(camera.getPosition().clone().add(delta));
    camera.lookAt(focus, upVector);
  };
  const zoom = (factor: number) => {
    if (cameraMode.value === 'fly') {
      move(forward(), spherical.radius * (1 - factor));
    } else {
      spherical.radius = Math.min(maxRadius, Math.max(minRadius, spherical.radius * factor));
      applyOrbit();
    }
  };
  const setMode = (mode: 'orbit' | 'fly') => {
    if (cameraMode.value === mode) return;
    cameraMode.value = mode;
    if (mode === 'orbit') {
      moveKeys.clear();
      spherical.phi = clampPhi(spherical.phi, mode);
    }
    canvas.style.cursor = mode === 'orbit' ? 'grab' : 'crosshair';
    if (mode === 'orbit') applyOrbit();
  };
  const resetView = (defaultAngles: boolean) => {
    setMode('orbit');
    focus.copy(homeCenter);
    spherical.radius = homeAngles.radius;
    if (defaultAngles) {
      spherical.theta = homeAngles.theta;
      spherical.phi = homeAngles.phi;
    }
    applyOrbit();
  };

  const rebaseTouches = () => {
    const points = [...touches.values()];
    if (points.length < 2) {
      touchCenter = null;
      return;
    }
    touchCenter = {
      x: (points[0].x + points[1].x) / 2,
      y: (points[0].y + points[1].y) / 2,
      span: Math.max(1, Math.hypot(points[0].x - points[1].x, points[0].y - points[1].y)),
    };
  };

  const onPointerDown = (event: PointerEvent) => {
    if (event.pointerType !== 'touch' && ![0, 1, 2].includes(event.button)) return;
    event.preventDefault();
    canvas.focus({ preventScroll: true });
    if (event.pointerType === 'touch') {
      touches.set(event.pointerId, { x: event.clientX, y: event.clientY });
      rebaseTouches();
    } else {
      mousePointer = event.pointerId;
      mouseButton = event.button;
      canvas.style.cursor = 'grabbing';
    }
    lastX = event.clientX;
    lastY = event.clientY;
    canvas.setPointerCapture(event.pointerId);
  };
  const onPointerMove = (event: PointerEvent) => {
    if (event.pointerType === 'touch') {
      const previous = touches.get(event.pointerId);
      if (!previous) return;
      touches.set(event.pointerId, { x: event.clientX, y: event.clientY });
      if (touches.size >= 2) {
        const previousCenter = touchCenter;
        rebaseTouches();
        if (previousCenter && touchCenter) {
          pan(touchCenter.x - previousCenter.x, touchCenter.y - previousCenter.y);
          zoom(previousCenter.span / touchCenter.span);
        }
      } else rotate(event.clientX - previous.x, event.clientY - previous.y);
      return;
    }
    if (mousePointer !== event.pointerId) return;
    const dx = event.clientX - lastX;
    const dy = event.clientY - lastY;
    lastX = event.clientX;
    lastY = event.clientY;
    if ((mouseButton === 2 && (event.altKey || event.metaKey)) ||
        (mouseButton === 1 && (event.ctrlKey || cameraMode.value === 'fly'))) zoom(Math.exp(dy * 0.005));
    else if ((mouseButton === 2 && (event.ctrlKey || event.shiftKey)) || mouseButton === 0 ||
        (mouseButton === 1 && !event.shiftKey)) rotate(dx, dy);
    else pan(dx, dy);
  };
  const onPointerUp = (event: PointerEvent) => {
    if (event.pointerType === 'touch') {
      touches.delete(event.pointerId);
      rebaseTouches();
    } else if (mousePointer === event.pointerId) {
      mousePointer = null;
      mouseButton = -1;
      canvas.style.cursor = cameraMode.value === 'orbit' ? 'grab' : 'crosshair';
    }
    if (canvas.hasPointerCapture(event.pointerId)) canvas.releasePointerCapture(event.pointerId);
  };
  const onWheel = (event: WheelEvent) => {
    event.preventDefault();
    const unit = event.deltaMode === WheelEvent.DOM_DELTA_LINE ? 16
      : event.deltaMode === WheelEvent.DOM_DELTA_PAGE ? canvas.clientHeight : 1;
    const dx = event.deltaX * unit;
    const dy = event.deltaY * unit;
    if (event.shiftKey) pan(dx, dy);
    else if (event.ctrlKey) rotate(dx * 0.25, dy * 0.25);
    else zoom(Math.exp(Math.max(-400, Math.min(400, dy)) * 0.001));
  };

  const movementKeys = ['w', 'a', 's', 'd', 'q', 'e'];
  const tick = (now: number) => {
    animationFrame = null;
    const dt = sogMoveDeltaSeconds(now, lastFrameTime);
    lastFrameTime = now;
    if (cameraMode.value !== 'fly' || !movementKeys.some((key) => moveKeys.has(key))) return;
    const direction = new Vec3();
    if (moveKeys.has('w')) direction.add(forward());
    if (moveKeys.has('s')) direction.sub(forward());
    if (moveKeys.has('d')) direction.add(right());
    if (moveKeys.has('a')) direction.sub(right());
    if (moveKeys.has('e')) direction.add(upVector);
    if (moveKeys.has('q')) direction.sub(upVector);
    if (direction.lengthSq() > 0) {
      const speed = flySpeed * flySpeedMultiplier.value * (moveKeys.has('shift') ? 10 : 1) * (moveKeys.has('alt') ? 0.1 : 1);
      move(direction.normalize(), speed * dt);
    }
    animationFrame = requestAnimationFrame(tick);
  };
  const onKeyDown = (event: KeyboardEvent) => {
    if (event.ctrlKey || event.metaKey) return;
    const key = event.key.toLowerCase();
    if (key === 'v' && !event.repeat) {
      event.preventDefault();
      event.stopPropagation();
      setMode(cameraMode.value === 'orbit' ? 'fly' : 'orbit');
      return;
    }
    if (key === 'f' && !event.repeat) {
      event.preventDefault();
      event.stopPropagation();
      resetView(event.shiftKey);
      return;
    }
    if (movementKeys.includes(key)) {
      event.preventDefault();
      event.stopPropagation();
      setMode('fly');
      moveKeys.add(key);
      if (event.shiftKey) moveKeys.add('shift');
      if (event.altKey) moveKeys.add('alt');
      if (animationFrame === null) {
        lastFrameTime = 0;
        animationFrame = requestAnimationFrame(tick);
      }
    } else if (key === 'shift' || key === 'alt') moveKeys.add(key);
  };
  const onKeyUp = (event: KeyboardEvent) => {
    if (movementKeys.includes(event.key.toLowerCase())) event.stopPropagation();
    moveKeys.delete(event.key.toLowerCase());
  };
  const onBlur = () => {
    moveKeys.clear();
    lastFrameTime = 0;
  };
  const onVisibilityChange = () => {
    if (document.hidden) onBlur();
  };
  const onContextMenu = (event: MouseEvent) => event.preventDefault();

  canvas.addEventListener('pointerdown', onPointerDown);
  canvas.addEventListener('pointermove', onPointerMove);
  canvas.addEventListener('pointerup', onPointerUp);
  canvas.addEventListener('pointercancel', onPointerUp);
  canvas.addEventListener('wheel', onWheel, { passive: false });
  canvas.addEventListener('contextmenu', onContextMenu);
  canvas.addEventListener('keydown', onKeyDown);
  canvas.addEventListener('keyup', onKeyUp);
  canvas.addEventListener('blur', onBlur);
  document.addEventListener('visibilitychange', onVisibilityChange);
  canvas.style.cursor = 'grab';
  applyOrbit();

  return () => {
    canvas.removeEventListener('pointerdown', onPointerDown);
    canvas.removeEventListener('pointermove', onPointerMove);
    canvas.removeEventListener('pointerup', onPointerUp);
    canvas.removeEventListener('pointercancel', onPointerUp);
    canvas.removeEventListener('wheel', onWheel);
    canvas.removeEventListener('contextmenu', onContextMenu);
    canvas.removeEventListener('keydown', onKeyDown);
    canvas.removeEventListener('keyup', onKeyUp);
    canvas.removeEventListener('blur', onBlur);
    document.removeEventListener('visibilitychange', onVisibilityChange);
    if (animationFrame !== null) cancelAnimationFrame(animationFrame);
    moveKeys.clear();
    touches.clear();
  };
}

function disposeScene() {
  downloadController?.abort();
  downloadController = null;
  manifest.value = null;
  sourceBounds = null;
  currentLevel.value = -1;
  selectedLevel.value = -1;
  levelLoading.value = false;
  autoSplatBudget = streamedSogBudget(platform.mobile);
  nativeStreamed.value = false;
  coarseLoaded.value = 0;
  coarseTotal.value = 0;
  nativeFailure = false;
  levelFilePromises.clear();
  resolvedPackageFiles.clear();
  chunkLevelByPath.clear();
  cameraMode.value = 'orbit';
  unbindNativeFrame?.();
  unbindNativeFrame = null;
  const streamedEntity = nativeEntity;
  nativeEntity = null;
  nativeResource = null;
  nativeLoader = null;
  nativeFileUrls = [];
  const entities = activeEntities;
  const assets = activeAssets;
  activeEntities = [];
  activeAssets = [];
  unbindOrbit?.();
  unbindOrbit = null;
  cameraEntity = null;
  orbitFocus = null;
  orbitUp = null;
  resizeObserver?.disconnect();
  resizeObserver = null;
  const current = app;
  app = null;
  canvasEl = null;
  if (current) {
    try {
      releaseSplats(current, entities, assets);
      // Detach the streamed component before unloading its octree asset.
      streamedEntity?.destroy();
      if (nativeAsset) {
        current.assets.remove(nativeAsset);
        nativeAsset.unload();
      }
    } catch {
      // 抽屉关闭时 WebGL 上下文可能已被浏览器回收，忽略即可。
    }
    try {
      current.destroy();
    } catch {
      // 即使分块卸载失败，也尝试销毁应用和图形上下文。
    }
  }
  nativeAsset = null;
  if (nativeObjectUrl) URL.revokeObjectURL(nativeObjectUrl);
  nativeObjectUrl = null;
  containerRef.value?.querySelectorAll('canvas').forEach((node) => node.remove());
}

async function startLoad() {
  const container = containerRef.value;
  if (!props.src || !container || directPreviewUnavailable.value) return;

  const currentGeneration = ++generation;
  userConfirmed.value = true;
  status.value = 'loading';
  progress.value = 0;
  error.value = '';
  levelError.value = '';
  disposeScene();
  if (currentGeneration !== generation || !containerRef.value) return;
  downloadController = new AbortController();

  const canvas = document.createElement('canvas');
  canvas.className = 'sog-canvas';
  canvas.style.display = 'block';
  canvas.style.width = '100%';
  canvas.style.height = '100%';
  container.appendChild(canvas);
  canvasEl = canvas;

  try {
    const manifestBytes = props.streamed
      ? await fetchBytes(props.src, downloadController.signal, false, Boolean(props.resourceId)) : null;
    if (manifestBytes) progress.value = 10;
    const parsedManifest = manifestBytes
      ? parseStreamedSogManifest(JSON.parse(new TextDecoder().decode(manifestBytes)), props.packageFiles || [], !!props.resourceId)
      : null;
    if (parsedManifest) {
      const coarseCount = parsedManifest.counts[initialLevelForStreamedSog(parsedManifest)];
      if (!canSelectSogLevel(platform.mobile, coarseCount)) {
        throw new Error(`此场景最低精度仍有 ${(coarseCount / 10_000).toFixed(1)} 万点，超过移动端安全范围；请生成更低精度的 SOG 层级。`);
      }
      autoSplatBudget = streamedSogBudget(platform.mobile, coarseCount);
      manifest.value = parsedManifest;
    }
    const buffer = parsedManifest ? null : await assembleSog(downloadController.signal);
    if (currentGeneration !== generation) return;

    const instance = new Application(canvas, {
      graphicsDeviceOptions: { antialias: false, alpha: false, powerPreference: 'high-performance', preserveDrawingBuffer: true },
    });
    app = instance;
    // Desktop HiDPI needs a sharper drawing buffer; keep mobile at 1x to
    // avoid multiplying fill-rate and framebuffer memory on constrained GPUs.
    instance.graphicsDevice.maxPixelRatio = platform.mobile ? 1 : Math.min(window.devicePixelRatio || 1, 2);
    instance.setCanvasFillMode(FILLMODE_NONE);
    instance.setCanvasResolution(RESOLUTION_AUTO);
    instance.scene.gsplat.splatBudget = autoSplatBudget;
    instance.resizeCanvas(Math.max(canvas.clientWidth, 1), Math.max(canvas.clientHeight, 1));
    instance.start();

    const camera = new Entity('sog-camera');
    camera.addComponent('camera', {
      clearColor: colorFromHex(previewBackground(props.previewConfig, '#111827')),
      fov: cameraFrame().fov,
      nearClip: 0.01,
      farClip: 100000,
    });
    instance.root.addChild(camera);
    cameraEntity = camera;
    if (parsedManifest) {
      const frame = cameraFrame();
      unbindOrbit = bindOrbit(camera, frame.center, frame.distance, frame.position, frame.up, frame.flySpeed);
    }

    // 扩展名从 url 推导，而资源 URL 没有扩展名，因此用 .sog 占位并由 contents 提供字节，
    // 引擎不会真正请求该地址。
    if (parsedManifest) {
      for (let level = parsedManifest.lodLevels - 1; level >= 0; level -= 1) {
        fileIndicesForLevel(parsedManifest, level).forEach((index) => {
          const path = chunkPackagePath(parsedManifest, parsedManifest.filenames[index]);
          if (!chunkLevelByPath.has(path)) chunkLevelByPath.set(path, level);
        });
      }
      if (parsedManifest.environment) {
        chunkLevelByPath.set(chunkPackagePath(parsedManifest, parsedManifest.environment), parsedManifest.lodLevels - 1);
      }
      await loadFilesForLevel(initialLevelForStreamedSog(parsedManifest));
      await installNativeStream(instance, parsedManifest, downloadController.signal);
    } else if (buffer) {
      const { asset, entity } = await createSplat(instance, buffer, 'sog-scene');
      // 解码异步完成时会话可能已切换，迟到资源不能写入新场景的全局持有列表。
      if (currentGeneration !== generation) { releaseSplats(instance, [entity], [asset]); return; }
      activeAssets = [asset];
      activeEntities = [entity];
      instance.root.addChild(entity);
      const box = entity.gsplat?.resource?.aabb;
      if (box) {
        const { center, halfExtents } = box;
        sourceBounds = {
          min: [center.x - halfExtents.x, center.y - halfExtents.y, center.z - halfExtents.z],
          max: [center.x + halfExtents.x, center.y + halfExtents.y, center.z + halfExtents.z],
        };
      }
    }
    if (currentGeneration !== generation) return;

    const frame = cameraFrame();
    if (!unbindOrbit) unbindOrbit = bindOrbit(camera, frame.center, frame.distance, frame.position, frame.up, frame.flySpeed);

    resizeObserver = new ResizeObserver(() => {
      if (!app || !canvasEl) return;
      const width = Math.max(canvasEl.clientWidth, 1);
      const height = Math.max(canvasEl.clientHeight, 1);
      app.resizeCanvas(width, height);
    });
    resizeObserver.observe(container);

    progress.value = 100;
    selectedLevel.value = -1;
    currentLevel.value = -1;
    status.value = 'ready';
    if (nativeStreamed.value && nativeEntity && parsedManifest) {
      nativeEntity.gsplat!.lodRangeMin = 0;
      nativeEntity.gsplat!.lodRangeMax = parsedManifest.lodLevels - 1;
      instance.scene.gsplat.splatBudget = autoSplatBudget;
    }
  } catch (failure: any) {
    if (currentGeneration !== generation) return;
    disposeScene();
    status.value = 'error';
    error.value = failure?.name === 'AbortError' ? '资源加载已取消' : (failure?.message || '浏览器无法初始化该高斯场景');
  }
}

function availablePackageFiles(): SogPackageFile[] {
  return [...(props.packageFiles || []), ...resolvedPackageFiles.values()];
}

async function loadFilesForLevel(level: number): Promise<SogPackageFile[]> {
  if (!props.resourceId) return props.packageFiles || [];
  const existing = levelFilePromises.get(level);
  if (existing) return existing;
  const requestGeneration = generation;
  const pending = (async () => {
    const response: any = await getStreamedSogLevelFiles(
      props.resourceId!, level, Number(props.resourceVersion || 0), String(props.resourceStatus || ''),
    );
    if (requestGeneration !== generation) throw new DOMException('已取消', 'AbortError');
    const rows: any[] = response?.data || [];
    if (!Array.isArray(rows) || !rows.length) throw new Error(`LOD ${level} 缺少分块文件`);
    const files = rows.map((row) => ({
      ...row,
      filePath: streamedSogFileUrl(
        props.resourceId!, String(row.fileId), Number(props.resourceVersion || 0), String(props.resourceStatus || ''),
      ),
    })) as SogPackageFile[];
    files.forEach((file) => {
      if (!file.relativePath || !file.filePath) throw new Error(`LOD ${level} 文件信息不完整`);
      resolvedPackageFiles.set(file.relativePath, file);
    });
    return files;
  })();
  levelFilePromises.set(level, pending);
  try {
    return await pending;
  } catch (failure) {
    levelFilePromises.delete(level);
    throw failure;
  }
}

async function resolveChunkPath(path: string): Promise<string> {
  const cached = resolvedPackageFiles.get(path)?.filePath;
  if (cached) return cached;
  const level = chunkLevelByPath.get(path);
  if (level === undefined) throw new Error(`Streamed SOG 未引用分块：${path}`);
  await loadFilesForLevel(level);
  const result = resolvedPackageFiles.get(path)?.filePath;
  if (!result) throw new Error(`Streamed SOG 缺少分块：${path}`);
  return result;
}

async function installNativeStream(instance: Application, source: StreamedSogManifest, signal: AbortSignal) {
  let rejectFirstFrame: ((reason: Error) => void) | null = null;
  const loaded = await createNativeStreamedSog(
    instance,
    source,
    availablePackageFiles(),
    async (url, chunkSignal, chunkSource) => chunkSource.path.toLowerCase().endsWith('.sog')
      ? fetchBytes(url, chunkSignal, false, Boolean(props.resourceId))
      : new Uint8Array(await readChunkBundle(chunkSource.path, chunkSignal)),
    (failure) => {
      if (rejectFirstFrame) rejectFirstFrame(failure);
      else {
        nativeFailure = true;
        levelLoading.value = false;
        levelError.value = failure.message;
      }
    },
    props.resourceId ? resolveChunkPath : undefined,
  );
  nativeAsset = loaded.asset;
  nativeObjectUrl = loaded.objectUrl;
  nativeResource = loaded.resource;
  nativeLoader = loaded.loader;
  nativeFileUrls = loaded.fileUrls;
  prioritizeNativeFiles(source);
  nativeStreamed.value = true;
  instance.scene.gsplat.lodMode = GSPLAT_LODMODE_DISTANCE;
  instance.scene.gsplat.lodUpdateAngle = 90;
  instance.scene.gsplat.lodBehindPenalty = 5;
  // 引擎优先显示已加载的较粗层，并在目标细节分块就绪后逐节点替换，避免空洞和整层叠画。
  instance.scene.gsplat.lodUnderfillLimit = Math.max(0, source.lodLevels - 1);
  const coarsest = initialLevelForStreamedSog(source);
  const entity = new Entity('sog-streamed-scene');
  entity.addComponent('gsplat', { asset: loaded.asset });
  entity.gsplat!.lodRangeMin = coarsest;
  entity.gsplat!.lodRangeMax = coarsest;
  nativeEntity = entity;
  instance.root.addChild(entity);
  coarseTotal.value = fileIndicesForLevel(source, coarsest).length;
  coarseLoaded.value = 0;
  const frame = cameraFrame();
  if (!unbindOrbit) unbindOrbit = bindOrbit(cameraEntity!, frame.center, frame.distance, frame.position, frame.up, frame.flySpeed);

  const firstFrame = new Promise<void>((resolve, reject) => {
    let settled = false;
    const timeout = window.setTimeout(() => finish(new Error('粗精度分块加载超时，请检查文件服务与浏览器显存')), 180_000);
    const finish = (failure?: Error) => {
      if (settled) return;
      settled = true;
      window.clearTimeout(timeout);
      rejectFirstFrame = null;
      signal.removeEventListener('abort', abort);
      if (failure) reject(failure);
      else resolve();
    };
    const abort = () => finish(new DOMException('已取消', 'AbortError'));
    rejectFirstFrame = (failure) => finish(failure);
    signal.addEventListener('abort', abort, { once: true });
    const onFrame = (_camera: unknown, _layer: unknown, ready: boolean, loadingCount: number) => {
      const hasChunks = (nativeResource?.octree?.fileResources.size || 0) > 0;
      if (!settled) progress.value = Math.max(progress.value, hasChunks ? 75 : 20);
      // frame:ready 表示当前已载入分块的画面可用；loadingCount 只表示后续分块仍在下载。
      if (!settled && ready && hasChunks) {
        levelLoading.value = loadingCount > 0;
        finish();
      }
      if (settled && status.value === 'ready' && !nativeFailure) {
        levelLoading.value = !ready || loadingCount > 0;
      }
    };
    instance.systems.gsplat!.on('frame:ready', onFrame);
    unbindNativeFrame = () => instance.systems.gsplat!.off('frame:ready', onFrame);
    if (signal.aborted) abort();
  });
  // 粗层 11 个分块等全部就绪后再放开高精度调度；下载期间首个分块即可显示。
  await Promise.all([
    firstFrame,
    loaded.loader.preloadPinned(signal, (count) => {
      coarseLoaded.value = count;
      progress.value = Math.max(progress.value, 20 + Math.round(75 * count / Math.max(1, coarseTotal.value)));
    }),
  ]);
}

async function createSplat(instance: Application, buffer: ArrayBuffer, name: string) {
  const placeholderUrl = `${name}.sog`;
  const asset = new Asset(name, 'gsplat', {
    url: placeholderUrl,
    filename: placeholderUrl,
    contents: buffer,
  });
  instance.assets.add(asset);
  try {
    await new Promise<void>((resolve, reject) => {
      asset.once('load', () => resolve());
      asset.once('error', (failure: unknown) => reject(
        failure instanceof Error ? failure : new Error(typeof failure === 'string' ? failure : 'SOG 场景加载失败'),
      ));
      instance.assets.load(asset);
    });
    const entity = new Entity(name);
    entity.addComponent('gsplat', { asset });
    return { asset, entity };
  } catch (failure) {
    instance.assets.remove(asset);
    asset.unload();
    throw failure;
  }
}

function releaseSplats(instance: Application, entities: Entity[], assets: Asset[]) {
  entities.forEach((entity) => entity.destroy());
  assets.forEach((asset) => {
    instance.assets.remove(asset);
    asset.unload();
  });
}

async function readChunkBundle(path: string, signal: AbortSignal): Promise<ArrayBuffer> {
  const files = props.resolveSourceFiles
    ? await props.resolveSourceFiles(path, signal)
    : filesForChunk(availablePackageFiles(), path);
  if (/\.sog$/i.test(path)) {
    const file = files[0];
    if (!file.filePath) throw new Error(`分块文件缺少访问路径：${path}`);
    const bytes = await fetchBytes(fileUrl(file.filePath), signal, false, Boolean(props.resourceId));
    return bytes.buffer as ArrayBuffer;
  }
  return buildSogBundleFromPackage(files, async (file) => {
    if (!file.filePath) throw new Error(`分块文件缺少访问路径：${file.relativePath}`);
    return fetchBytes(fileUrl(file.filePath), signal, false, Boolean(props.resourceId));
  });
}

function onLevelChange(event: Event) {
  const selected = Number((event.target as HTMLSelectElement).value);
  const source = manifest.value;
  const instance = app;
  const component = nativeEntity?.gsplat;
  if (!source || !instance || !component || !Number.isInteger(selected) || selected < -1 || selected >= source.lodLevels) return;
  if (selected === currentLevel.value) return;
  if (selected >= 0 && !canSelectSogLevel(platform.mobile, source.counts[selected])) {
    selectedLevel.value = currentLevel.value;
    levelError.value = '该目标精度超过移动端安全范围，请选择较低精度或使用桌面设备';
    return;
  }
  if (nativeFailure) {
    void startLoad();
    return;
  }
  prioritizeNativeFiles(source);
  const range = streamedSogLodRange(selected, source.lodLevels);
  // Keep the coarse level available while PlayCanvas replaces regions as
  // finer chunks arrive. The selected LOD is a target, not a blocking preload.
  component.lodRangeMin = range.min;
  component.lodRangeMax = range.max;
  instance.scene.gsplat.splatBudget = selected < 0
    ? autoSplatBudget
    : Math.max(autoSplatBudget, Math.ceil(source.counts[selected] * 1.03));
  currentLevel.value = selected;
  selectedLevel.value = selected;
  levelError.value = '';
  levelLoading.value = true;
  instance.renderNextFrame = true;
}

function colorFromHex(value: string) {
  const color = Number.parseInt(value.slice(1), 16);
  return new Color(((color >> 16) & 0xff) / 255, ((color >> 8) & 0xff) / 255, (color & 0xff) / 255);
}

async function reset() {
  generation += 1;
  userConfirmed.value = false;
  status.value = 'idle';
  progress.value = 0;
  error.value = '';
  levelError.value = '';
  disposeScene();
  if ((props.streamed || !largeScene.value) && !directPreviewUnavailable.value) void startLoad();
}
// 与其他查看器统一暴露可保存视角的时机；原资源查看页面无需修改。
watch(status, (value) => emit('ready', value === 'ready'));

watch(
  () => [props.src, props.streamed, props.fileSize, props.descriptor?.vertexCount, props.packageFiles?.length,
    props.resourceId, props.resourceVersion, props.resourceStatus],
  () => void reset(),
  { deep: true },
);
watch(() => props.previewConfig, restoreSavedView);

onMounted(() => {
  if ((props.streamed || !largeScene.value) && !directPreviewUnavailable.value) void startLoad();
});

onBeforeUnmount(() => {
  generation += 1;
  disposeScene();
});
</script>

<style scoped>
.sog-viewer {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 480px;
  overflow: hidden;
  border-radius: 6px;
  background: #111827;
}

.sog-canvas {
  display: block;
}

.sog-viewer :deep(.sog-canvas:focus-visible) {
  outline: 2px solid #60a5fa;
  outline-offset: -2px;
}

.sog-overlay {
  position: absolute;
  inset: 0;
  z-index: 5;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 32px;
  color: #e5e7eb;
  text-align: center;
  background: rgba(17, 24, 39, 0.94);
}

.sog-title {
  margin-top: 12px;
  font-size: 16px;
  font-weight: 600;
}

.sog-description {
  max-width: 520px;
  margin: 10px 0 18px;
  color: #9ca3af;
  font-size: 13px;
  line-height: 1.7;
}

.sog-progress {
  width: min(420px, 80%);
  margin-top: 16px;
}

.sog-loading-indicator {
  position: absolute;
  top: 12px;
  left: 12px;
  z-index: 4;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 8px;
  color: #e5e7eb;
  font-size: 12px;
  background: rgba(17, 24, 39, 0.76);
  pointer-events: none;
}

.sog-hint {
  position: absolute;
  right: 12px;
  bottom: 12px;
  z-index: 4;
  padding: 4px 10px;
  border-radius: 6px;
  color: #e5e7eb;
  font-size: 12px;
  background: rgba(17, 24, 39, 0.72);
}

.sog-levels {
  position: absolute;
  top: 12px;
  left: 12px;
  z-index: 4;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 6px;
  color: #e5e7eb;
  font-size: 12px;
  background: rgba(17, 24, 39, 0.78);
}

.sog-levels select {
  max-width: 220px;
  padding: 4px;
  border: 1px solid #6b7280;
  border-radius: 4px;
  color: #e5e7eb;
  background: #1f2937;
}

.sog-fly-speed {
  position: absolute;
  top: 12px;
  right: 12px;
  z-index: 4;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 6px;
  color: #e5e7eb;
  font-size: 12px;
  background: rgba(17, 24, 39, 0.78);
}

.sog-fly-speed select {
  padding: 4px;
  border: 1px solid #6b7280;
  border-radius: 4px;
  color: #e5e7eb;
  background: #1f2937;
}

@media (max-width: 740px) {
  .sog-fly-speed {
    top: 68px;
  }
}

.sog-level-error {
  position: absolute;
  top: 58px;
  left: 12px;
  z-index: 4;
  padding: 8px;
  border-radius: 4px;
  color: #fecaca;
  background: rgba(127, 29, 29, 0.9);
}
</style>
