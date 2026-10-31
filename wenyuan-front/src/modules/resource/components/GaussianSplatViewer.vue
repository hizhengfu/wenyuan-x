<template>
  <div class="gaussian-viewer">
    <div ref="containerRef" class="gaussian-canvas"></div>

    <div v-if="awaitingConfirmation" class="gaussian-overlay">
      <n-icon size="52" depth="3"><IconMdiCubeOutline /></n-icon>
      <div class="gaussian-title">大型高斯场景</div>
      <div class="gaussian-description">
        {{ resourceSummary }}。加载时可能占用 1GB 以上浏览器内存，建议使用具备独立显卡的桌面设备。
      </div>
      <n-button type="primary" @click="startLoad">加载高斯预览</n-button>
    </div>

    <div v-else-if="status === 'loading'" class="gaussian-overlay">
      <n-spin size="large" />
      <div class="gaussian-title">正在加载高斯场景</div>
      <n-progress type="line" :percentage="progress" :show-indicator="true" class="gaussian-progress" />
      <div class="gaussian-description">已启用渐进加载，可在首批数据准备完成后开始查看。</div>
    </div>

    <div v-else-if="status === 'error'" class="gaussian-overlay">
      <n-icon size="48" color="#d03050"><IconMdiAlertCircleOutline /></n-icon>
      <div class="gaussian-title">高斯场景加载失败</div>
      <div class="gaussian-description">{{ error }}</div>
      <n-button secondary @click="startLoad">重新加载</n-button>
    </div>

    <div v-if="status === 'ready' && progress < 100" class="gaussian-download-progress">
      <span>后台继续加载 {{ progress }}%</span>
      <n-progress type="line" :percentage="progress" :show-indicator="false" />
    </div>

    <div v-if="status === 'ready'" class="gaussian-orientation">
      <button type="button" class="gaussian-orientation-toggle" :aria-expanded="showOrientationTools"
        aria-controls="gaussian-orientation-tools" @click="showOrientationTools = !showOrientationTools">
        <IconMdiAxisArrow /> 方向校正
      </button>
      <div v-if="showOrientationTools" id="gaussian-orientation-tools" class="gaussian-orientation-panel">
        <div class="gaussian-orientation-title">以原文件坐标为准设置上方向</div>
        <div class="gaussian-axis-buttons">
          <button v-for="option in upAxes" :key="option.label" type="button"
            :class="{ active: activeUpAxis === option.label }" :aria-label="`${option.label} 向上`"
            @click="setUpAxis(option.value, option.label)">{{ option.label }}</button>
        </div>
        <div class="gaussian-orientation-actions">
          <button type="button" aria-label="逆时针滚转 15 度" @click="rollCamera(-15)">↶ 15°</button>
          <button type="button" aria-label="顺时针滚转 15 度" @click="rollCamera(15)">↷ 15°</button>
        </div>
        <button type="button" class="gaussian-reset-direction" @click="restoreSourceFrame">按原始点云重新居中</button>
        <small>校正后，有编辑权限时可用下方工具栏保存视角。</small>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { getAuthenticationHeaders } from '@/utils/auth';
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import * as THREE from 'three';
// 该包未提供与项目 TypeScript 配置兼容的声明文件。
// @ts-ignore
import * as GaussianSplats3D from '@mkkellogg/gaussian-splats-3d';
import { useAuthStore } from '@/store';
import { configuredCameraView, previewBackground, type ResourcePreviewConfig } from '../utils/previewConfig';
import { resolveOrbitUp } from '../utils/cameraOrientation';
import { gaussianCameraPosition, gaussianFrameFromBounds, gaussianFrameFromPoints, type GaussianSourceFrame, type Vector3 } from '../utils/gaussianSourceFrame';

type PreviewDescriptor = {
  vertexCount?: number;
  sphericalHarmonicsDegree?: number;
  boundsMin?: number[];
  boundsMax?: number[];
};

const LARGE_FILE_BYTES = 256 * 1024 * 1024;
const LARGE_SPLAT_COUNT = 4_000_000;

const props = defineProps<{
  src: string;
  /** 默认保持资源模块的 PLY 路径；监测图层可通过直接 URL 指定 splat/ksplat 容器。 */
  format?: 'ply' | 'splat' | 'ksplat';
  fileSize?: number;
  descriptor?: PreviewDescriptor;
  previewConfig?: ResourcePreviewConfig | null;
}>();
const emit = defineEmits<{ ready: [ready: boolean] }>();

const authStore = useAuthStore();
const containerRef = ref<HTMLDivElement>();
const status = ref<'idle' | 'loading' | 'ready' | 'error'>('idle');
const progress = ref(0);
const error = ref('');
const userConfirmed = ref(false);
const showOrientationTools = ref(false);
const activeUpAxis = ref('');
const upAxes: Array<{ label: string; value: Vector3 }> = [
  { label: '+X', value: [1, 0, 0] }, { label: '-X', value: [-1, 0, 0] },
  { label: '+Y', value: [0, 1, 0] }, { label: '-Y', value: [0, -1, 0] },
  { label: '+Z', value: [0, 0, 1] }, { label: '-Z', value: [0, 0, -1] },
];

let viewer: any = null;
let renderer: THREE.WebGLRenderer | null = null;
let camera: THREE.PerspectiveCamera | null = null;
let resizeObserver: ResizeObserver | null = null;
let generation = 0;
let sourceFrame: GaussianSourceFrame | null = null;
let userAdjustedCamera = false;
let unbindUserCameraInput: (() => void) | null = null;

function upAxisLabel(up: number[]): string {
  return upAxes.find((option) => option.value.every((value, axis) =>
    Math.abs(value - up[axis]) < 0.01))?.label || '';
}

function applyFrame(frame: ReturnType<typeof cameraFrame>) {
  if (!camera || !viewer?.controls) return;
  camera.position.fromArray(frame.position);
  camera.up.fromArray(frame.up);
  viewer.cameraUp?.copy(camera.up);
  camera.fov = frame.fov;
  camera.near = Math.max(0.01, frame.distance / 10000);
  camera.far = Math.max(10000, frame.distance * 20);
  camera.updateProjectionMatrix();
  viewer.controls.target.fromArray(frame.center);
  camera.lookAt(new THREE.Vector3().fromArray(frame.center));
  viewer.controls.update();
  viewer.forceRenderNextFrame?.();
}

function captureView(): ResourcePreviewConfig | null {
  if (status.value !== 'ready' || !camera || !viewer?.controls) return null;
  return {
    cameraPosition: camera.position.toArray(),
    cameraTarget: viewer.controls.target.toArray(),
    cameraUp: camera.up.toArray(),
    cameraFov: camera.fov,
    backgroundColor: previewBackground(props.previewConfig, '#111827'),
  };
}

function restoreSavedView() {
  if (!camera || !viewer?.controls) return;
  const frame = cameraFrame();
  applyFrame(frame);
  activeUpAxis.value = upAxisLabel(frame.up);
  renderer?.setClearColor(previewBackground(props.previewConfig, '#111827'), 1);
}

function setUpAxis(up: Vector3, label: string) {
  if (!camera || !viewer?.controls) return;
  userAdjustedCamera = true;
  const target = viewer.controls.target;
  const offset = camera.position.clone().sub(target);
  const vertical = new THREE.Vector3().fromArray(up);
  if (Math.abs(offset.clone().normalize().dot(vertical)) > 0.98) {
    const side = new THREE.Vector3(Math.abs(up[0]) > 0.9 ? 0 : 1, Math.abs(up[0]) > 0.9 ? 1 : 0, 0)
      .projectOnPlane(vertical).normalize();
    camera.position.copy(target).addScaledVector(side, offset.length() * 0.5)
      .addScaledVector(vertical, offset.dot(vertical) >= 0 ? offset.length() * 0.86 : -offset.length() * 0.86);
  }
  camera.up.fromArray(up);
  viewer.cameraUp?.copy(camera.up);
  camera.lookAt(viewer.controls.target);
  viewer.controls.update();
  viewer.forceRenderNextFrame?.();
  activeUpAxis.value = label;
}

function rollCamera(degrees: number) {
  if (!camera || !viewer?.controls) return;
  userAdjustedCamera = true;
  const direction = camera.getWorldDirection(new THREE.Vector3()).normalize();
  camera.up.applyAxisAngle(direction, THREE.MathUtils.degToRad(degrees)).normalize();
  viewer.cameraUp?.copy(camera.up);
  camera.lookAt(viewer.controls.target);
  viewer.controls.update();
  viewer.forceRenderNextFrame?.();
  activeUpAxis.value = '';
}

function restoreSourceFrame() {
  userAdjustedCamera = true;
  const frame = cameraFrame(false);
  applyFrame(frame);
  activeUpAxis.value = upAxisLabel(frame.up);
}

defineExpose({ captureView, restoreSavedView });

const splatCount = computed(() => Number(props.descriptor?.vertexCount || 0));
const sphericalHarmonicsDegree = computed(() => Math.min(2, Math.max(0, props.descriptor?.sphericalHarmonicsDegree || 0)));
const recommendedMaxSplatCount = computed(() => [15_500_000, 10_500_000, 7_500_000][sphericalHarmonicsDegree.value]);
const largeScene = computed(() =>
  Number(props.fileSize || 0) >= LARGE_FILE_BYTES || splatCount.value >= LARGE_SPLAT_COUNT,
);
const awaitingConfirmation = computed(() => status.value === 'idle' && largeScene.value && !userConfirmed.value);
const resourceSummary = computed(() => {
  const parts: string[] = [];
  if (splatCount.value > 0) parts.push(`${(splatCount.value / 10_000).toFixed(1)} 万个高斯点`);
  if (props.fileSize) parts.push(`${(props.fileSize / 1024 / 1024).toFixed(1)} MiB`);
  return parts.join('，') || '该资源体积较大';
});

function cameraFrame(useSavedView = true) {
  const original = sourceFrame || gaussianFrameFromBounds(props.descriptor?.boundsMin, props.descriptor?.boundsMax);
  const configured = useSavedView ? configuredCameraView(props.previewConfig) : null;
  if (configured) {
    return {
      center: configured.target,
      position: configured.position,
      up: resolveOrbitUp(configured.position, configured.target, configured.up,
        original?.boundsMin, original?.boundsMax, original?.up || [0, -1, 0]),
      fov: configured.fov || 60,
      distance: Math.max(1, Math.hypot(
        configured.position[0] - configured.target[0],
        configured.position[1] - configured.target[1],
        configured.position[2] - configured.target[2],
      )),
    };
  }
  if (!original) {
    return { center: [0, 0, 0], position: [5, -5, 3], up: [0, -1, 0], fov: 60, distance: 8 };
  }
  const distance = Math.max(8, original.diagonal * 1.15);
  return {
    center: original.center,
    position: gaussianCameraPosition(original.center, distance, original.up),
    up: original.up,
    fov: 60,
    distance,
  };
}

function decodedSourceFrame(): GaussianSourceFrame | null {
  const splatMesh = viewer?.splatMesh;
  const count = Number(splatMesh?.getSplatCount?.() || 0);
  if (count < 16 || !splatMesh?.getSplatCenter) return null;
  const center = new THREE.Vector3();
  const sampleCount = Math.min(4096, count);
  const points: Vector3[] = [];
  for (let sample = 0; sample < sampleCount; sample += 1) {
    const index = Math.floor(sample * (count - 1) / Math.max(1, sampleCount - 1));
    splatMesh.getSplatCenter(index, center, true);
    points.push(center.toArray() as Vector3);
  }
  return gaussianFrameFromPoints(points);
}

function requestHeaders() {
  try {
    const target = new URL(props.src, window.location.href);
    if (target.origin !== window.location.origin) return undefined;
    return getAuthenticationHeaders(authStore.getToken, authStore.authorizationName);
  } catch {
    return undefined;
  }
}

async function disposeViewer() {
  unbindUserCameraInput?.();
  unbindUserCameraInput = null;
  resizeObserver?.disconnect();
  resizeObserver = null;
  const oldViewer = viewer;
  const oldRenderer = renderer;
  viewer = null;
  renderer = null;
  camera = null;
  if (oldViewer) {
    oldViewer.stop?.();
    try {
      await oldViewer.dispose?.();
    } catch {
      // 关闭抽屉时即使网络请求已被浏览器取消，也要继续释放 WebGL 资源。
    }
  }
  if (oldRenderer) {
    const canvas = oldRenderer.domElement;
    oldRenderer.dispose();
    canvas.parentElement?.removeChild(canvas);
  }
}

async function startLoad() {
  if (!props.src || !containerRef.value) return;
  if (splatCount.value > recommendedMaxSplatCount.value) {
    status.value = 'error';
    error.value = `该场景超过 SH${sphericalHarmonicsDegree.value} 数据的浏览器渲染建议上限，请先生成轻量预览版本。`;
    return;
  }

  const currentGeneration = ++generation;
  sourceFrame = null;
  userAdjustedCamera = false;
  showOrientationTools.value = false;
  userConfirmed.value = true;
  status.value = 'loading';
  progress.value = 0;
  error.value = '';
  await disposeViewer();
  if (currentGeneration !== generation || !containerRef.value) return;

  const root = containerRef.value;
  const width = Math.max(root.clientWidth, 1);
  const height = Math.max(root.clientHeight, 1);
  const frame = cameraFrame();
  activeUpAxis.value = upAxisLabel(frame.up);

  try {
    renderer = new THREE.WebGLRenderer({ antialias: false, powerPreference: 'high-performance', preserveDrawingBuffer: true });
    renderer.setPixelRatio(largeScene.value ? 1 : Math.min(window.devicePixelRatio || 1, 1.5));
    renderer.setSize(width, height, false);
    renderer.setClearColor(previewBackground(props.previewConfig, '#111827'), 1);
    root.appendChild(renderer.domElement);
    const onUserCameraInput = () => { userAdjustedCamera = true; };
    renderer.domElement.addEventListener('pointerdown', onUserCameraInput);
    renderer.domElement.addEventListener('wheel', onUserCameraInput);
    unbindUserCameraInput = () => {
      renderer?.domElement.removeEventListener('pointerdown', onUserCameraInput);
      renderer?.domElement.removeEventListener('wheel', onUserCameraInput);
    };

    camera = new THREE.PerspectiveCamera(
      frame.fov,
      width / height,
      Math.max(0.01, frame.distance / 10000),
      Math.max(10000, frame.distance * 20),
    );
    camera.position.fromArray(frame.position);
    camera.up.fromArray(frame.up);
    camera.lookAt(new THREE.Vector3().fromArray(frame.center));

    const sharedMemory = window.crossOriginIsolated === true;
    const boundsMin = props.descriptor?.boundsMin;
    const boundsMax = props.descriptor?.boundsMax;
    const maxExtent = boundsMin?.length === 3 && boundsMax?.length === 3
      ? Math.max(...boundsMax.map((value, index) => Math.abs(value - boundsMin[index])))
      : 0;

    viewer = new GaussianSplats3D.Viewer({
      rootElement: root,
      renderer,
      camera,
      cameraUp: frame.up,
      initialCameraPosition: frame.position,
      initialCameraLookAt: frame.center,
      sharedMemoryForWorkers: sharedMemory,
      gpuAcceleratedSort: sharedMemory,
      integerBasedSort: maxExtent <= 200,
      halfPrecisionCovariancesOnGPU: true,
      freeIntermediateSplatData: true,
      ignoreDevicePixelRatio: largeScene.value,
      renderMode: GaussianSplats3D.RenderMode.OnChange,
      sphericalHarmonicsDegree: sphericalHarmonicsDegree.value,
    });

    resizeObserver = new ResizeObserver(() => {
      if (!renderer || !camera || !containerRef.value) return;
      const nextWidth = containerRef.value.clientWidth;
      const nextHeight = containerRef.value.clientHeight;
      if (nextWidth <= 0 || nextHeight <= 0) return;
      renderer.setSize(nextWidth, nextHeight, false);
      camera.aspect = nextWidth / nextHeight;
      camera.updateProjectionMatrix();
      viewer?.forceRenderNextFrame?.();
    });
    resizeObserver.observe(root);

    await viewer.addSplatScene(props.src, {
      format: props.format === 'splat' ? GaussianSplats3D.SceneFormat.Splat
        : props.format === 'ksplat' ? GaussianSplats3D.SceneFormat.KSplat : GaussianSplats3D.SceneFormat.Ply,
      progressiveLoad: true,
      showLoadingUI: false,
      splatAlphaRemovalThreshold: 5,
      headers: requestHeaders(),
      onProgress: (value: number) => {
        if (currentGeneration !== generation || !Number.isFinite(value)) return;
        progress.value = Math.max(progress.value, Math.min(100, Math.round(value)));
      },
    });
    if (currentGeneration !== generation) return;
    // progressiveLoad 在首批 splat 就 resolve；方向判定必须等完整下载/构建结束，
    // 否则只抽到一小片空间，可能把场景误判为平面。
    const loadedViewer = viewer;
    const completed = loadedViewer.splatSceneDownloadAndBuildPromise || Promise.resolve();
    void Promise.resolve(completed).then(() => {
      if (currentGeneration !== generation || viewer !== loadedViewer) return;
      try {
        sourceFrame = decodedSourceFrame();
        if (sourceFrame && !configuredCameraView(props.previewConfig) && !userAdjustedCamera) {
          const targetDistance = loadedViewer.controls?.target?.distanceTo(new THREE.Vector3().fromArray(sourceFrame.center)) || 0;
          // 无明确平面时不因几何中心微调而换到高斯重建较差的另一侧。
          if (sourceFrame.inferredUp || targetDistance > Math.max(2, sourceFrame.diagonal * 0.25)) {
            const frame = cameraFrame(false);
            applyFrame(frame);
            activeUpAxis.value = upAxisLabel(frame.up);
          }
        }
      } catch {
        // 坐标抽样失败不影响已显示的预览。
      }
    }).catch(() => { /* 背景加载失败时保留已显示的首批画面。 */ });
    viewer.start();
    status.value = 'ready';
  } catch (failure: any) {
    if (currentGeneration !== generation) return;
    await disposeViewer();
    status.value = 'error';
    error.value = failure?.message || '浏览器无法初始化该高斯场景';
  }
}

async function reset() {
  generation += 1;
  sourceFrame = null;
  userAdjustedCamera = false;
  showOrientationTools.value = false;
  userConfirmed.value = false;
  status.value = 'idle';
  progress.value = 0;
  error.value = '';
  await disposeViewer();
  if (!largeScene.value) void startLoad();
}

watch(() => [props.src, props.format, props.fileSize, props.descriptor?.vertexCount], () => void reset());
// 就绪状态供外部视角保存工具使用；资源页面未监听该事件时保持原有行为。
watch(status, (value) => emit('ready', value === 'ready'));
watch(() => props.previewConfig, restoreSavedView);

onMounted(() => {
  if (!largeScene.value) void startLoad();
});

onBeforeUnmount(() => {
  generation += 1;
  void disposeViewer();
});
</script>

<style scoped>
.gaussian-viewer,
.gaussian-canvas {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 480px;
  overflow: hidden;
  border-radius: 6px;
  background: #111827;
}

.gaussian-canvas :deep(canvas) {
  display: block;
}

.gaussian-overlay {
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

.gaussian-title {
  margin-top: 12px;
  font-size: 16px;
  font-weight: 600;
}

.gaussian-description {
  max-width: 520px;
  margin: 10px 0 18px;
  color: #9ca3af;
  font-size: 13px;
  line-height: 1.7;
}

.gaussian-progress {
  width: min(420px, 80%);
  margin-top: 16px;
}

.gaussian-download-progress {
  position: absolute;
  top: 12px;
  right: 12px;
  z-index: 4;
  width: 220px;
  padding: 8px 10px;
  border-radius: 6px;
  color: #e5e7eb;
  font-size: 12px;
  background: rgba(17, 24, 39, 0.82);
}

.gaussian-orientation {
  position: absolute;
  top: 12px;
  left: 12px;
  z-index: 6;
  color: #e5e7eb;
  font-size: 12px;
}

.gaussian-orientation button {
  border: 1px solid rgba(148, 163, 184, 0.35);
  border-radius: 7px;
  color: inherit;
  background: rgba(22, 31, 49, 0.92);
  cursor: pointer;
}

.gaussian-orientation button:hover,
.gaussian-orientation button:focus-visible,
.gaussian-orientation button.active {
  border-color: #69b1ff;
  background: rgba(33, 82, 139, 0.86);
}

.gaussian-orientation-toggle {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 7px 10px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.25);
}

.gaussian-orientation-panel {
  width: 224px;
  margin-top: 7px;
  padding: 11px;
  border: 1px solid rgba(148, 163, 184, 0.28);
  border-radius: 10px;
  background: rgba(17, 24, 39, 0.96);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.35);
}

.gaussian-orientation-title {
  margin-bottom: 9px;
  font-weight: 600;
}

.gaussian-axis-buttons {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 5px;
}

.gaussian-axis-buttons button,
.gaussian-orientation-actions button {
  min-height: 30px;
}

.gaussian-orientation-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 5px;
  margin-top: 7px;
}

.gaussian-reset-direction {
  width: 100%;
  min-height: 30px;
  margin-top: 7px;
}

.gaussian-orientation-panel small {
  display: block;
  margin-top: 9px;
  line-height: 1.5;
  color: #b7c2d2;
}
</style>
