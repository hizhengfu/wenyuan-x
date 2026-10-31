<template>
  <div ref="containerRef" class="three-viewer">
    <div v-if="loading" class="three-loading">
      <n-spin size="large" />
      <div class="mt-3 text-sm text-gray-500">正在加载 3D 模型…</div>
    </div>
    <div v-if="error" class="three-error">
      <n-icon size="48" depth="3"><IconMdiAlertCircleOutline /></n-icon>
      <div class="mt-2 text-sm text-red-500">{{ error }}</div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { ref, onMounted, onBeforeUnmount, watch } from 'vue';
import * as THREE from 'three';
// @ts-ignore
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls';
// @ts-ignore
import { STLLoader } from 'three/examples/jsm/loaders/STLLoader';
// @ts-ignore
import { OBJLoader } from 'three/examples/jsm/loaders/OBJLoader';
// @ts-ignore
import { MTLLoader } from 'three/examples/jsm/loaders/MTLLoader';
// @ts-ignore
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader';
// @ts-ignore
import { PLYLoader } from 'three/examples/jsm/loaders/PLYLoader';
// @ts-ignore
import { FBXLoader } from 'three/examples/jsm/loaders/FBXLoader';
// @ts-ignore
import { RoomEnvironment } from 'three/examples/jsm/environments/RoomEnvironment';
import { fileUrl } from '@/utils/path';
import { configuredCameraView, previewBackground, type ResourcePreviewConfig } from '../utils/previewConfig';
import { resolveOrbitUp } from '../utils/cameraOrientation';

const props = defineProps<{
  src: string;
  ext: string;
  packageFiles?: Array<{
    relativePath?: string;
    originalName?: string;
    filePath?: string;
    role?: string;
    fileExt?: string;
  }>;
  previewConfig?: ResourcePreviewConfig | null;
}>();

const containerRef = ref<HTMLDivElement>();
const loading = ref(false);
const error = ref('');

let renderer: THREE.WebGLRenderer | null = null;
let scene: THREE.Scene | null = null;
let camera: THREE.PerspectiveCamera | null = null;
let controls: OrbitControls | null = null;
let rafId = 0;
let resizeObs: ResizeObserver | null = null;
let currentObject: THREE.Object3D | null = null;
let environmentTexture: THREE.Texture | null = null;
let keyLight: THREE.DirectionalLight | null = null;

function initScene() {
  if (!containerRef.value) return;
  const el = containerRef.value;
  const width = el.clientWidth || 600;
  const height = el.clientHeight || 420;

  scene = new THREE.Scene();
  scene.background = new THREE.Color(previewBackground(props.previewConfig, '#f5f7fa'));

  camera = new THREE.PerspectiveCamera(props.previewConfig?.cameraFov || 45, width / height, 0.1, 10000);
  camera.position.set(5, 5, 5);
  scene.add(camera);

  renderer = new THREE.WebGLRenderer({ antialias: true, preserveDrawingBuffer: true });
  renderer.setPixelRatio(window.devicePixelRatio || 1);
  renderer.setSize(width, height);
  // 色调映射：让 PBR 材质（GLTF/GLB）显示正常亮度
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure = 1.6;
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  el.appendChild(renderer.domElement);

  setupEnvironment();
  setupLights();

  resetOrbitControls(new THREE.Vector3());

  const animate = () => {
    rafId = requestAnimationFrame(animate);
    controls?.update();
    if (renderer && scene && camera) renderer.render(scene, camera);
  };
  animate();

  resizeObs = new ResizeObserver(() => {
    if (!containerRef.value || !renderer || !camera) return;
    const w = containerRef.value.clientWidth;
    const h = containerRef.value.clientHeight;
    if (w === 0 || h === 0) return;
    renderer.setSize(w, h);
    camera.aspect = w / h;
    camera.updateProjectionMatrix();
  });
  resizeObs.observe(el);
}

function setupEnvironment() {
  if (!scene || !renderer) return;
  const pmremGenerator = new THREE.PMREMGenerator(renderer);
  const roomEnvironment = new RoomEnvironment();
  environmentTexture?.dispose();
  environmentTexture = pmremGenerator.fromScene(roomEnvironment).texture;
  scene.environment = environmentTexture;
  roomEnvironment.dispose();
  pmremGenerator.dispose();
}

function setupLights() {
  if (!scene || !camera) return;

  const ambient = new THREE.AmbientLight(0xffffff, 0.45);
  scene.add(ambient);

  const hemisphere = new THREE.HemisphereLight(0xffffff, 0xd7dee8, 1.25);
  hemisphere.position.set(0, 1, 0);
  scene.add(hemisphere);

  keyLight = new THREE.DirectionalLight(0xffffff, 1.75);
  keyLight.position.set(2.5, 3.5, 4);
  camera.add(keyLight);
}

function enhanceGltfMaterials(obj: THREE.Object3D) {
  obj.traverse((node: any) => {
    if (!node.isMesh) return;

    const materials = Array.isArray(node.material) ? node.material : [node.material];
    materials.forEach((material: any) => {
      if (!material?.isMeshStandardMaterial && !material?.isMeshPhysicalMaterial) return;

      material.envMapIntensity = Math.max(material.envMapIntensity ?? 1, 1.45);
      material.needsUpdate = true;
    });
  });
}

function resetOrbitControls(target: THREE.Vector3) {
  if (!camera || !renderer) return;
  controls?.dispose();
  controls = new OrbitControls(camera, renderer.domElement);
  controls.enableDamping = true;
  controls.dampingFactor = 0.1;
  controls.target.copy(target);
  controls.update();
}

function fitToObject(obj: THREE.Object3D) {
  if (!camera || !controls) return;
  const box = new THREE.Box3().setFromObject(obj);
  if (box.isEmpty()) return;
  const size = box.getSize(new THREE.Vector3());
  const center = box.getCenter(new THREE.Vector3());
  const maxDim = Math.max(size.x, size.y, size.z) || 1;
  const configured = configuredCameraView(props.previewConfig);
  scene!.background = new THREE.Color(previewBackground(props.previewConfig, '#f5f7fa'));
  if (configured) {
    camera.fov = configured.fov || camera.fov;
    camera.position.fromArray(configured.position);
    camera.up.fromArray(resolveOrbitUp(configured.position, configured.target, configured.up, box.min.toArray(), box.max.toArray()));
    const target = new THREE.Vector3().fromArray(configured.target);
    const distance = Math.max(camera.position.distanceTo(target), maxDim);
    camera.near = Math.max(0.001, distance / 10000);
    camera.far = Math.max(distance * 100, maxDim * 100);
    camera.updateProjectionMatrix();
    camera.lookAt(target);
    resetOrbitControls(target);
    return;
  }

  // 始终保留模型坐标；自动取景与保存的视角必须处于同一坐标系。
  const fov = (camera.fov * Math.PI) / 180;
  const distance = maxDim / (2 * Math.tan(fov / 2)) * 1.6;
  camera.position.set(center.x + distance, center.y + distance, center.z + distance);
  camera.near = maxDim / 100;
  camera.far = maxDim * 100;
  camera.updateProjectionMatrix();
  camera.up.set(0, 1, 0);
  camera.lookAt(center);
  resetOrbitControls(center);
}

function captureView(): ResourcePreviewConfig | null {
  if (!camera || !controls || !currentObject || loading.value) return null;
  return {
    cameraPosition: camera.position.toArray(),
    cameraTarget: controls.target.toArray(),
    cameraUp: camera.up.toArray(),
    cameraFov: camera.fov,
    backgroundColor: previewBackground(props.previewConfig, '#f5f7fa'),
  };
}

function restoreSavedView() {
  if (currentObject) fitToObject(currentObject);
}

defineExpose({ captureView, restoreSavedView });

function clearCurrent() {
  if (currentObject && scene) {
    scene.remove(currentObject);
    currentObject.traverse((o: any) => {
      if (o.geometry) o.geometry.dispose?.();
      if (o.material) {
        if (Array.isArray(o.material)) o.material.forEach((m: any) => m.dispose?.());
        else o.material.dispose?.();
      }
    });
    currentObject = null;
  }
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

function resolvePackageAssetUrl(requestUrl: string, mainRelativePath?: string) {
  if (!requestUrl || !props.packageFiles?.length) return requestUrl;
  if (/^(data:|blob:)/i.test(requestUrl)) return requestUrl;

  const assetMap = buildPackageAssetMap();
  const cleanUrl = normalizePackagePath(
    /^(https?:\/\/|\/\/)/i.test(requestUrl) ? new URL(requestUrl, window.location.href).pathname : requestUrl,
  );
  const mainDir = mainRelativePath?.includes('/') ? mainRelativePath.substring(0, mainRelativePath.lastIndexOf('/')) : '';
  const candidates = [
    cleanUrl,
    mainDir ? normalizePackagePath(`${mainDir}/${cleanUrl}`) : cleanUrl,
    cleanUrl.substring(cleanUrl.lastIndexOf('/') + 1),
  ];
  for (const candidate of candidates) {
    const mapped = assetMap.get(candidate);
    if (mapped) return mapped;
  }
  return requestUrl;
}

function createPackageLoadingManager(mainRelativePath?: string) {
  const manager = new THREE.LoadingManager();
  manager.setURLModifier((url) => resolvePackageAssetUrl(url, mainRelativePath));
  return manager;
}

function findMaterialPackageFile(mainRelativePath?: string) {
  const files = props.packageFiles || [];
  const mainDir = mainRelativePath?.includes('/') ? mainRelativePath.substring(0, mainRelativePath.lastIndexOf('/')) : '';
  const mainStem = mainRelativePath?.substring(mainRelativePath.lastIndexOf('/') + 1).replace(/\.[^.]+$/, '').toLowerCase();
  return files.find((item) => {
    const relativePath = normalizePackagePath(item.relativePath || '');
    const ext = relativePath.substring(relativePath.lastIndexOf('.') + 1).toLowerCase();
    const dir = relativePath.includes('/') ? relativePath.substring(0, relativePath.lastIndexOf('/')) : '';
    const stem = relativePath.substring(relativePath.lastIndexOf('/') + 1).replace(/\.[^.]+$/, '').toLowerCase();
    return ext === 'mtl' && dir === mainDir && stem === mainStem;
  }) || files.find((item) => {
    const relativePath = normalizePackagePath(item.relativePath || '');
    return relativePath.toLowerCase().endsWith('.mtl') && (!mainDir || relativePath.startsWith(`${mainDir}/`));
  }) || files.find((item) => normalizePackagePath(item.relativePath || '').toLowerCase().endsWith('.mtl'));
}

async function load() {
  if (!props.src || !scene) return;
  loading.value = true;
  error.value = '';
  clearCurrent();

  const ext = (props.ext || '').toLowerCase();
  const mainFile = mainPackageFile();
  const mainRelativePath = normalizePackagePath(mainFile?.relativePath || '');

  try {
    if (ext === 'stl') {
      const loader = new STLLoader();
      const geom: THREE.BufferGeometry = await new Promise((resolve, reject) => {
        loader.load(props.src, resolve, undefined, reject);
      });
      const material = new THREE.MeshPhongMaterial({ color: 0x4a90e2, specular: 0x111111, shininess: 200 });
      const mesh = new THREE.Mesh(geom, material);
      scene.add(mesh);
      currentObject = mesh;
      fitToObject(mesh);
    } else if (ext === 'obj') {
      const manager = createPackageLoadingManager(mainRelativePath);
      const loader = new OBJLoader(manager);
      const materialFile = findMaterialPackageFile(mainRelativePath);
      if (materialFile?.filePath) {
        const mtlLoader = new MTLLoader(manager);
        const materials: any = await new Promise((resolve, reject) => {
          mtlLoader.load(packageFilePublicUrl(materialFile), resolve, undefined, reject);
        });
        materials.preload();
        loader.setMaterials(materials);
      }
      const obj: THREE.Group = await new Promise((resolve, reject) => {
        loader.load(props.src, resolve, undefined, reject);
      });
      // 无材质时给一个默认材质
      obj.traverse((c: any) => {
        if (c.isMesh && !c.material) {
          c.material = new THREE.MeshPhongMaterial({ color: 0xcccccc });
        }
      });
      scene.add(obj);
      currentObject = obj;
      fitToObject(obj);
    } else if (ext === 'glb' || ext === 'gltf') {
      const loader = new GLTFLoader(createPackageLoadingManager(mainRelativePath));
      const gltf: any = await new Promise((resolve, reject) => {
        loader.load(props.src, resolve, undefined, reject);
      });
      enhanceGltfMaterials(gltf.scene);
      scene.add(gltf.scene);
      currentObject = gltf.scene;
      fitToObject(gltf.scene);
    } else if (ext === 'ply') {
      const loader = new PLYLoader();
      const geom: THREE.BufferGeometry = await new Promise((resolve, reject) => {
        loader.load(props.src, resolve, undefined, reject);
      });
      // PLY 同时可能是网格或普通点云；Gaussian PLY 已在 ResourcePreview 中分流。
      if (geom.index) {
        if (!geom.getAttribute('normal')) geom.computeVertexNormals();
        const material = new THREE.MeshPhongMaterial({
          color: geom.getAttribute('color') ? 0xffffff : 0x4a90e2,
          vertexColors: Boolean(geom.getAttribute('color')),
          flatShading: true,
        });
        const mesh = new THREE.Mesh(geom, material);
        scene.add(mesh);
        currentObject = mesh;
        fitToObject(mesh);
      } else {
        geom.computeBoundingBox();
        const size = geom.boundingBox?.getSize(new THREE.Vector3()) || new THREE.Vector3(1, 1, 1);
        const pointSize = Math.max(size.length() / 800, 0.001);
        const points = new THREE.Points(
          geom,
          new THREE.PointsMaterial({
            color: geom.getAttribute('color') ? 0xffffff : 0x4a90e2,
            vertexColors: Boolean(geom.getAttribute('color')),
            size: pointSize,
            sizeAttenuation: true,
          }),
        );
        scene.add(points);
        currentObject = points;
        fitToObject(points);
      }
    } else if (ext === 'fbx') {
      const loader = new FBXLoader(createPackageLoadingManager(mainRelativePath));
      const obj: THREE.Group = await new Promise((resolve, reject) => {
        loader.load(props.src, resolve, undefined, reject);
      });
      // FBX 模型无材质时补一个默认材质，保证可见
      obj.traverse((c: any) => {
        if (c.isMesh) {
          if (!c.material || (Array.isArray(c.material) && c.material.length === 0)) {
            c.material = new THREE.MeshPhongMaterial({ color: 0xcccccc });
          }
          c.castShadow = true;
          c.receiveShadow = true;
        }
      });
      scene.add(obj);
      currentObject = obj;
      fitToObject(obj);
    } else {
      error.value = `暂不支持 .${ext} 格式的在线预览`;
    }
  } catch (e: any) {
    console.error('[ThreeViewer] load error', e);
    error.value = `模型加载失败：${e?.message || e?.type || '未知错误'}`;
  } finally {
    loading.value = false;
  }
}

onMounted(async () => {
  initScene();
  await load();
});

watch(
  () => [props.src, props.ext, props.packageFiles],
  () => {
    if (scene) load();
  },
);
watch(() => props.previewConfig, restoreSavedView);

onBeforeUnmount(() => {
  cancelAnimationFrame(rafId);
  resizeObs?.disconnect();
  controls?.dispose();
  clearCurrent();
  if (renderer) {
    renderer.dispose();
    renderer.domElement.parentElement?.removeChild(renderer.domElement);
    renderer = null;
  }
  if (scene) {
    scene.environment = null;
  }
  environmentTexture?.dispose();
  environmentTexture = null;
  keyLight = null;
  scene = null;
  camera = null;
  controls = null;
});
</script>

<style scoped>
.three-viewer {
  position: relative;
  width: 100%;
  height: 100%;
  background: #f5f7fa;
  border-radius: 6px;
  overflow: hidden;
}
.three-loading,
.three-error {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  pointer-events: none;
  background: rgba(245, 247, 250, 0.85);
  z-index: 10;
}
</style>
