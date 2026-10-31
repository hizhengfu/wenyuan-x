<template>
  <div ref="containerRef" class="tiles3d-viewer">
    <div v-if="state === 'loading'" class="tiles3d-overlay">
      <n-spin size="large" />
      <div class="mt-3 text-sm text-gray-500">正在加载 3D Tiles…</div>
    </div>
    <div v-if="state === 'error'" class="tiles3d-overlay">
      <n-icon size="48" depth="3"><IconMdiAlertCircleOutline /></n-icon>
      <div class="mt-2 text-sm text-red-500">{{ errorMsg }}</div>
    </div>
    <!-- 控制提示 -->
    <div v-if="state === 'ready'" class="tiles3d-hint">
      鼠标左键旋转 · 右键平移 · 滚轮缩放
    </div>
  </div>
</template>

<script lang="ts" setup>
import { ref, onMounted, onBeforeUnmount, watch } from 'vue';
import * as THREE from 'three';
// @ts-ignore
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls';
// @ts-ignore
import { TilesRenderer } from '3d-tiles-renderer';
import { fileUrl } from '@/utils/path';
import { configuredCameraView, previewBackground, type ResourcePreviewConfig } from '../utils/previewConfig';
import { resolveOrbitUp } from '../utils/cameraOrientation';

const props = defineProps<{
  src: string; // tileset.json 的完整 URL
  packageFiles?: Array<{
    relativePath?: string;
    originalName?: string;
    filePath?: string;
    role?: string;
    fileExt?: string;
  }>;
  previewConfig?: ResourcePreviewConfig | null;
}>();

type ViewerState = 'loading' | 'ready' | 'error';

const containerRef = ref<HTMLDivElement>();
const state = ref<ViewerState>('loading');
const errorMsg = ref('');

let renderer: THREE.WebGLRenderer | null = null;
let scene: THREE.Scene | null = null;
let camera: THREE.PerspectiveCamera | null = null;
let controls: InstanceType<typeof OrbitControls> | null = null;
let tilesRenderer: InstanceType<typeof TilesRenderer> | null = null;
let rafId = 0;
let resizeObs: ResizeObserver | null = null;

function initScene() {
  if (!containerRef.value) return;
  const el = containerRef.value;
  const width = el.clientWidth || 600;
  const height = el.clientHeight || 480;

  scene = new THREE.Scene();
  scene.background = new THREE.Color(previewBackground(props.previewConfig, '#1a1a2e'));

  camera = new THREE.PerspectiveCamera(props.previewConfig?.cameraFov || 60, width / height, 0.1, 100000);
  camera.position.set(0, 100, 200);

  renderer = new THREE.WebGLRenderer({ antialias: true, preserveDrawingBuffer: true });
  renderer.setPixelRatio(window.devicePixelRatio || 1);
  renderer.setSize(width, height);
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure = 1.2;
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  el.appendChild(renderer.domElement);

  // 灯光
  scene.add(new THREE.AmbientLight(0xffffff, 1.2));
  const sun = new THREE.DirectionalLight(0xffffff, 2.0);
  sun.position.set(100, 200, 100);
  scene.add(sun);

  resetOrbitControls(new THREE.Vector3());

  resizeObs = new ResizeObserver(() => {
    if (!containerRef.value || !renderer || !camera) return;
    const w = containerRef.value.clientWidth;
    const h = containerRef.value.clientHeight;
    if (w === 0 || h === 0) return;
    renderer.setSize(w, h);
    camera.aspect = w / h;
    camera.updateProjectionMatrix();
    tilesRenderer?.setResolutionFromRenderer(camera, renderer);
  });
  resizeObs.observe(el);
}

function loadTiles(url: string) {
  if (!scene || !camera || !renderer) return;

  // 清理旧的 tilesRenderer
  disposeRenderer();

  state.value = 'loading';
  errorMsg.value = '';

  tilesRenderer = new TilesRenderer(url);
  const mainFile = mainPackageFile();
  const mainRelativePath = normalizePackagePath(mainFile?.relativePath || '');
  const urlPlugin = createPackageUrlPlugin(mainRelativePath);
  tilesRenderer.registerPlugin(urlPlugin);
  tilesRenderer.manager.setURLModifier((assetUrl: string) => resolvePackageAssetUrl(assetUrl, mainRelativePath));
  tilesRenderer.setCamera(camera);
  tilesRenderer.setResolutionFromRenderer(camera, renderer);

  // 瓦片集加载完成：自动适应相机位置
  tilesRenderer.addEventListener('load-tile-set', () => {
    state.value = 'ready';
    const box = new THREE.Box3();
    const hasBound = tilesRenderer!.getBoundingBox(box);
    if (hasBound && !box.isEmpty()) {
      fitCameraToBox(box);
    }
  });

  tilesRenderer.addEventListener('load-error', (e: any) => {
    state.value = 'error';
    errorMsg.value = `加载失败：${e?.error?.message || e?.url || '未知错误'}`;
  });

  scene.add(tilesRenderer.group);
}

function normalizePackagePath(path: string) {
  const cleanPath = String(path || '').split(/[?#]/)[0];
  const decoded = (() => {
    try {
      return decodeURIComponent(cleanPath);
    } catch {
      return cleanPath;
    }
  })();
  return decoded
    .replace(/\\/g, '/')
    .replace(/^\/+/, '')
    .replace(/^\.\//, '');
}

function mainPackageFile() {
  const files = props.packageFiles || [];
  return files.find((item) => item.role === 'MAIN') || files.find((item) => fileUrl(item.filePath || '') === props.src);
}

function packageFilePublicUrl(item: any) {
  return fileUrl(item?.filePath || '');
}

function buildPackageAssetMap() {
  const map = new Map<string, string>();
  const nameBuckets = new Map<string, string[]>();
  for (const item of props.packageFiles || []) {
    if (!item?.relativePath || !item?.filePath) continue;
    const relativePath = normalizePackagePath(item.relativePath);
    const url = packageFilePublicUrl(item);
    map.set(relativePath, url);
    const fileName = relativePath.substring(relativePath.lastIndexOf('/') + 1);
    if (!nameBuckets.has(fileName)) nameBuckets.set(fileName, []);
    nameBuckets.get(fileName)!.push(url);
  }
  for (const [fileName, urls] of nameBuckets.entries()) {
    if (urls.length === 1) map.set(fileName, urls[0]);
  }
  return map;
}

function urlPath(value: string) {
  const rawUrl = String(value || '');
  if (/^(data:|blob:)/i.test(rawUrl)) return rawUrl;
  try {
    return /^(https?:\/\/|\/\/)/i.test(rawUrl)
      ? new URL(rawUrl, window.location.href).pathname
      : rawUrl;
  } catch {
    return rawUrl;
  }
}

function resolvePackageAssetUrl(requestUrl: string, mainRelativePath?: string) {
  if (!requestUrl || !props.packageFiles?.length) return requestUrl;
  if (/^(data:|blob:)/i.test(requestUrl)) return requestUrl;

  const assetMap = buildPackageAssetMap();
  const cleanUrl = normalizePackagePath(urlPath(requestUrl));
  const mainDir = mainRelativePath?.includes('/') ? mainRelativePath.substring(0, mainRelativePath.lastIndexOf('/')) : '';
  const mainFile = mainPackageFile();
  const mainPublicPath = normalizePackagePath(urlPath(packageFilePublicUrl(mainFile)));
  const mainPublicDir = mainPublicPath.includes('/') ? mainPublicPath.substring(0, mainPublicPath.lastIndexOf('/')) : '';
  const relativeToStoredMain = mainPublicDir && cleanUrl.startsWith(`${mainPublicDir}/`)
    ? cleanUrl.substring(mainPublicDir.length + 1)
    : '';
  const candidates = [
    cleanUrl,
    relativeToStoredMain,
    mainDir && relativeToStoredMain ? normalizePackagePath(`${mainDir}/${relativeToStoredMain}`) : '',
    mainDir ? normalizePackagePath(`${mainDir}/${cleanUrl}`) : cleanUrl,
    cleanUrl.substring(cleanUrl.lastIndexOf('/') + 1),
  ].filter(Boolean);
  for (const candidate of candidates) {
    const mapped = assetMap.get(candidate);
    if (mapped) return mapped;
  }
  return requestUrl;
}

function createPackageUrlPlugin(mainRelativePath?: string) {
  return {
    name: 'ResourcePackageUrlPlugin',
    preprocessURL(url: string | URL) {
      return resolvePackageAssetUrl(String(url), mainRelativePath);
    },
  };
}

function fitCameraToBox(box: THREE.Box3) {
  if (!camera || !controls) return;
  const size = box.getSize(new THREE.Vector3());
  const center = box.getCenter(new THREE.Vector3());
  const maxDim = Math.max(size.x, size.y, size.z) || 1;
  const configured = configuredCameraView(props.previewConfig);
  scene!.background = new THREE.Color(previewBackground(props.previewConfig, '#1a1a2e'));
  if (configured) {
    camera.fov = configured.fov || camera.fov;
    camera.position.fromArray(configured.position);
    camera.up.fromArray(resolveOrbitUp(configured.position, configured.target, configured.up));
    const target = new THREE.Vector3().fromArray(configured.target);
    const distance = Math.max(camera.position.distanceTo(target), maxDim);
    camera.near = Math.max(0.001, distance / 10000);
    camera.far = Math.max(distance * 100, maxDim * 100);
    camera.updateProjectionMatrix();
    camera.lookAt(target);
    resetOrbitControls(target);
    return;
  }
  const fov = (camera.fov * Math.PI) / 180;
  const dist = maxDim / (2 * Math.tan(fov / 2)) * 1.8;

  camera.position.set(center.x, center.y + dist * 0.4, center.z + dist);
  camera.near = maxDim / 1000;
  camera.far  = maxDim * 100;
  camera.updateProjectionMatrix();
  camera.up.set(0, 1, 0);
  camera.lookAt(center);
  resetOrbitControls(center);
}

function resetOrbitControls(target: THREE.Vector3) {
  if (!camera || !renderer) return;
  controls?.dispose();
  controls = new OrbitControls(camera, renderer.domElement);
  controls.enableDamping = true;
  controls.dampingFactor = 0.08;
  controls.screenSpacePanning = true;
  controls.target.copy(target);
  controls.update();
}

function captureView(): ResourcePreviewConfig | null {
  if (!camera || !controls || state.value !== 'ready') return null;
  return {
    cameraPosition: camera.position.toArray(),
    cameraTarget: controls.target.toArray(),
    cameraUp: camera.up.toArray(),
    cameraFov: camera.fov,
    backgroundColor: previewBackground(props.previewConfig, '#1a1a2e'),
  };
}

function restoreSavedView() {
  if (!tilesRenderer) return;
  const box = new THREE.Box3();
  if (tilesRenderer.getBoundingBox(box) && !box.isEmpty()) fitCameraToBox(box);
}

defineExpose({ captureView, restoreSavedView });

function disposeRenderer() {
  if (tilesRenderer && scene) {
    scene.remove(tilesRenderer.group);
    tilesRenderer.dispose?.();
    tilesRenderer = null;
  }
}

function animate() {
  rafId = requestAnimationFrame(animate);
  controls?.update();
  if (tilesRenderer && camera && renderer) {
    tilesRenderer.update();
    renderer.render(scene!, camera);
  } else if (renderer && scene && camera) {
    renderer.render(scene, camera);
  }
}

onMounted(() => {
  initScene();
  if (props.src) loadTiles(props.src);
  animate();
});

watch(() => ({ src: props.src, packageFiles: props.packageFiles }), ({ src }) => {
  if (src) loadTiles(src);
}, { deep: true });
watch(() => props.previewConfig, restoreSavedView);

onBeforeUnmount(() => {
  cancelAnimationFrame(rafId);
  resizeObs?.disconnect();
  disposeRenderer();
  controls?.dispose();
  if (renderer) {
    renderer.dispose();
    renderer.domElement.parentElement?.removeChild(renderer.domElement);
    renderer = null;
  }
  scene = null;
  camera = null;
  controls = null;
});
</script>

<style scoped>
.tiles3d-viewer {
  position: relative;
  width: 100%;
  height: 100%;
  background: #1a1a2e;
  border-radius: 6px;
  overflow: hidden;
}
.tiles3d-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: rgba(26, 26, 46, 0.85);
  z-index: 10;
  color: #ccc;
}
.tiles3d-hint {
  position: absolute;
  bottom: 10px;
  left: 50%;
  transform: translateX(-50%);
  font-size: 12px;
  color: rgba(255, 255, 255, 0.5);
  pointer-events: none;
  z-index: 5;
}
</style>
