<template>
  <div class="panorama">
    <div
      ref="host"
      class="panorama-canvas"
      tabindex="0"
      aria-label="360度全景，拖动或方向键环视，滚轮缩放"
      @keydown="key"
    />
    <div class="panorama-controls">
      <n-button size="small" @click="reset">重置视角</n-button><span>拖动或方向键环视 · 滚轮缩放</span>
    </div>
    <n-alert v-if="error" type="error" class="panorama-error">{{ error }}</n-alert
    ><n-spin v-if="loading" class="panorama-error" />
  </div>
</template>
<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
import * as THREE from 'three';
const props = defineProps<{ src: string }>();
const host = ref<HTMLElement>();
const error = ref('');
const loading = ref(false);
let renderer: THREE.WebGLRenderer | undefined,
  camera: THREE.PerspectiveCamera,
  scene: THREE.Scene,
  mesh: THREE.Mesh<THREE.SphereGeometry, THREE.MeshBasicMaterial> | undefined,
  observer: ResizeObserver | undefined;
let lon = 0,
  lat = 0,
  frame = 0,
  generation = 0,
  pointer: { x: number; y: number } | undefined;
function reset() {
  lon = 0;
  lat = 0;
  if (camera) {
    camera.fov = 75;
    camera.updateProjectionMatrix();
  }
}
function key(e: KeyboardEvent) {
  if (!['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown'].includes(e.key)) return;
  e.preventDefault();
  lon += e.key === 'ArrowRight' ? 5 : e.key === 'ArrowLeft' ? -5 : 0;
  lat += e.key === 'ArrowUp' ? 5 : e.key === 'ArrowDown' ? -5 : 0;
}
function down(e: PointerEvent) {
  pointer = { x: e.clientX, y: e.clientY };
  host.value?.setPointerCapture(e.pointerId);
}
function move(e: PointerEvent) {
  if (!pointer) return;
  lon -= (e.clientX - pointer.x) * 0.15;
  lat += (e.clientY - pointer.y) * 0.15;
  pointer = { x: e.clientX, y: e.clientY };
}
function up() {
  pointer = undefined;
}
function wheel(e: WheelEvent) {
  e.preventDefault();
  camera.fov = THREE.MathUtils.clamp(camera.fov + e.deltaY * 0.03, 30, 100);
  camera.updateProjectionMatrix();
}
function draw() {
  frame = requestAnimationFrame(draw);
  if (!renderer) return;
  lat = THREE.MathUtils.clamp(lat, -85, 85);
  const phi = THREE.MathUtils.degToRad(90 - lat),
    theta = THREE.MathUtils.degToRad(lon);
  camera.lookAt(Math.sin(phi) * Math.cos(theta), Math.cos(phi), Math.sin(phi) * Math.sin(theta));
  renderer.render(scene, camera);
}
function load() {
  if (!renderer) return;
  const own = ++generation;
  loading.value = true;
  error.value = '';
  new THREE.TextureLoader().setCrossOrigin('use-credentials').load(
    props.src,
    (texture) => {
      if (own !== generation) {
        texture.dispose();
        return;
      }
      const img = texture.image;
      if (Math.abs(img.width / img.height - 2) > 0.04) {
        texture.dispose();
        loading.value = false;
        error.value = '请使用宽高比2:1的等距柱状投影全景图片';
        return;
      }
      texture.colorSpace = THREE.SRGBColorSpace;
      if (mesh) {
        scene.remove(mesh);
        mesh.geometry.dispose();
        mesh.material.map?.dispose();
        mesh.material.dispose();
      }
      const geometry = new THREE.SphereGeometry(10, 64, 40);
      geometry.scale(-1, 1, 1);
      mesh = new THREE.Mesh(geometry, new THREE.MeshBasicMaterial({ map: texture }));
      scene.add(mesh);
      reset();
      loading.value = false;
    },
    undefined,
    () => {
      if (own === generation) {
        error.value = '全景图片加载失败，请检查资源访问权限或重试';
        loading.value = false;
      }
    },
  );
}
onMounted(() => {
  try {
    renderer = new THREE.WebGLRenderer({ antialias: true });
    renderer.setPixelRatio(Math.min(devicePixelRatio, 2));
    host.value!.appendChild(renderer.domElement);
    scene = new THREE.Scene();
    camera = new THREE.PerspectiveCamera(75, 1, 0.1, 100);
    observer = new ResizeObserver(() => {
      const w = host.value!.clientWidth,
        h = host.value!.clientHeight;
      renderer!.setSize(w, h);
      camera.aspect = w / Math.max(h, 1);
      camera.updateProjectionMatrix();
    });
    observer.observe(host.value!);
    host.value!.addEventListener('pointerdown', down);
    host.value!.addEventListener('pointermove', move);
    host.value!.addEventListener('pointerup', up);
    host.value!.addEventListener('pointercancel', up);
    host.value!.addEventListener('wheel', wheel, { passive: false });
    load();
    draw();
  } catch {
    error.value = '当前浏览器无法创建全景视图';
  }
});
watch(() => props.src, load);
onBeforeUnmount(() => {
  generation++;
  cancelAnimationFrame(frame);
  observer?.disconnect();
  host.value?.removeEventListener('pointerdown', down);
  host.value?.removeEventListener('pointermove', move);
  host.value?.removeEventListener('pointerup', up);
  host.value?.removeEventListener('pointercancel', up);
  host.value?.removeEventListener('wheel', wheel);
  mesh?.geometry.dispose();
  mesh?.material.map?.dispose();
  mesh?.material.dispose();
  renderer?.dispose();
});
</script>
<style scoped>
.panorama {
  position: relative;
  width: 100%;
  min-height: 420px;
  background: #16212b;
  color: white;
}
.panorama-canvas {
  height: min(65vh, 650px);
  min-height: 380px;
  touch-action: none;
}
.panorama-controls {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 10px;
}
.panorama-error {
  position: absolute;
  top: 20px;
  left: 20px;
  right: 20px;
}
</style>
