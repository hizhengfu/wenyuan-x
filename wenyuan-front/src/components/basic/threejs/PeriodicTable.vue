<template>
  <div class="container-wrap">
    <div id="container" class="container" ref="container"></div>
    <div class="menu">
      <button @click="transformTo('table')">表格</button>
      <button @click="transformTo('sphere')">球体</button>
      <button @click="transformTo('helix')">螺旋</button>
      <button @click="transformTo('grid')">网格</button>
    </div>
    <transition>
      <div class="show_info animated" v-show="showInfo">
        <div class="info_my">
          <img :src="'/images/zl.jpg'" />
          <div class="info_mem">
            <div class="title">{{ activeData.title }}</div>
            <div class="code">{{ activeData.id }}</div>
          </div>
        </div>
        <div class="intro">
          {{ activeData.description }}
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, ref } from 'vue';
import * as THREE from 'three';
import TWEEN from 'three/addons/libs/tween.module.js';
import { TrackballControls } from 'three/addons/controls/TrackballControls.js';
import { CSS3DObject, CSS3DRenderer } from 'three/addons/renderers/CSS3DRenderer.js';

const container = ref(null);

let camera, scene, renderer, controls;
const objects = [];
const targets = { table: [], sphere: [], helix: [], grid: [] };

const props = defineProps({
  list: {
    type: Array,
    default() {
      return [];
    },
  },
});

let activeIndex = 0;

/**
 * 是否显示
 *
 */
const showInfo = ref(false);
const activeData = ref({});
let effectInterval = -1;
/**
 * 定时器
 * */
const infoInterval = setInterval(() => {
  //无数的时候不处理
  if (props.list.length == 0) return;
  if (activeIndex >= props.list.length) {
    activeIndex = 0;
  }
  activeData.value = props.list[activeIndex];
  showInfo.value = true;

  setTimeout(() => {
    ++activeIndex;
    showInfo.value = false;
  }, 8000);
}, 10000);

/**
 * 初始化
 * */
function init() {
  const { list } = props;

  camera = new THREE.PerspectiveCamera(40, window.innerWidth / window.innerHeight, 1, 10000);
  camera.position.z = 3000;

  scene = new THREE.Scene();

  // table
  for (let i = 0; i < list.length; i++) {
    const d = list[i];
    const element = document.createElement('div');

    element.className = 'element';
    element.style.backgroundColor = 'rgba(0,127,127,' + (Math.random() * 0.5 + 0.25) + ')';

    // if (d.image) {
    const img = document.createElement('img');
    // img.src = fileUrl(d.image) + '?w=120&h=160';
    img.src = '/images/zl.jpg';

    element.appendChild(img);
    // }

    // const symbol = document.createElement('div');
    // symbol.className = 'symbol';
    // symbol.textContent = d.title;
    // element.appendChild(symbol);

    // const details = document.createElement('div');
    // details.className = 'details';
    // details.innerHTML = d.title;
    // element.appendChild(details);

    const objectCSS = new CSS3DObject(element);
    objectCSS.position.x = Math.random() * 4000 - 2000;
    objectCSS.position.y = Math.random() * 4000 - 2000;
    objectCSS.position.z = Math.random() * 4000 - 2000;
    scene.add(objectCSS);

    objects.push(objectCSS);

    //
    const object = new THREE.Object3D();
    object.position.x = (i % 20) * 140 - 1330;
    object.position.y = -Math.floor(i / 20) * 180 + 990;

    targets.table.push(object);
  }

  // sphere
  const vector = new THREE.Vector3();

  for (let i = 0, l = objects.length; i < l; i++) {
    const phi = Math.acos(-1 + (2 * i) / l);
    const theta = Math.sqrt(l * Math.PI) * phi;

    const object = new THREE.Object3D();

    object.position.setFromSphericalCoords(800, phi, theta);

    vector.copy(object.position).multiplyScalar(2);

    object.lookAt(vector);

    targets.sphere.push(object);
  }

  // helix
  for (let i = 0, l = objects.length; i < l; i++) {
    const theta = i * 0.175 + Math.PI;
    const y = -(i * 8) + 450;

    const object = new THREE.Object3D();

    object.position.setFromCylindricalCoords(900, theta, y);

    vector.x = object.position.x * 2;
    vector.y = object.position.y;
    vector.z = object.position.z * 2;

    object.lookAt(vector);

    targets.helix.push(object);
  }

  // grid
  for (let i = 0; i < objects.length; i++) {
    const object = new THREE.Object3D();

    object.position.x = (i % 5) * 400 - 800;
    object.position.y = -(Math.floor(i / 5) % 5) * 400 + 800;
    object.position.z = Math.floor(i / 25) * 1000 - 2000;

    targets.grid.push(object);
  }

  //渲染
  renderer = new CSS3DRenderer();
  renderer.setSize(window.innerWidth, window.innerHeight);
  container.value.appendChild(renderer.domElement);

  //鼠标控制
  controls = new TrackballControls(camera, renderer.domElement);
  controls.minDistance = 500;
  controls.maxDistance = 6000;
  controls.addEventListener('change', render);

  // 自动更换
  let effectIndex = 0;
  effectInterval = setInterval(() => {
    effectIndex = effectIndex >= 3 ? 0 : effectIndex;
    ++effectIndex;
    switch (effectIndex) {
      case 1:
        transform(targets.sphere, 2000);
        break;
      case 2:
        transform(targets.helix, 2000);
        break;
      case 3:
        transform(targets.grid, 2000);
        break;
    }
  }, 8000);

  transform(targets.table, 2000);

  window.addEventListener('resize', onWindowResize);
}

function transform(targets, duration) {
  TWEEN.removeAll();

  for (let i = 0; i < objects.length; i++) {
    const object = objects[i];
    const target = targets[i];

    new TWEEN.Tween(object.position)
      .to({ x: target.position.x, y: target.position.y, z: target.position.z }, Math.random() * duration + duration)
      .easing(TWEEN.Easing.Exponential.InOut)
      .start();

    new TWEEN.Tween(object.rotation)
      .to({ x: target.rotation.x, y: target.rotation.y, z: target.rotation.z }, Math.random() * duration + duration)
      .easing(TWEEN.Easing.Exponential.InOut)
      .start();
  }

  new TWEEN.Tween(this)
    .to({}, duration * 2)
    .onUpdate(render)
    .start();
}

function onWindowResize() {
  camera.aspect = window.innerWidth / window.innerHeight;
  camera.updateProjectionMatrix();

  renderer.setSize(window.innerWidth, window.innerHeight);

  render();
}

function animate() {
  //改变y轴
  scene.rotation.y += 0.003;
  requestAnimationFrame(animate);
  TWEEN.update();
  controls.update();
  //循环渲染
  render();
}

function render() {
  renderer.render(scene, camera);
}

const transformTo = (targetName) => {
  transform(targets[targetName], 2000);
};

onMounted(() => {
  init();
  animate();
});

onUnmounted(() => {
  window.removeEventListener('resize', onWindowResize);
  // Clean up Three.js scene and renderer if necessary to prevent memory leaks
  if (renderer && renderer.domElement) {
    renderer.domElement.remove();
  }
  clearInterval(effectInterval);
  clearInterval(infoInterval);
});
</script>

<style lang="scss">
a {
  color: #8ff;
}

.container-wrap {
  margin: 0;
  background-color: #000;
  color: #fff;
  font-family: Monospace;
  font-size: 13px;
  line-height: 24px;
  overscroll-behavior: none;
}

.menu {
  position: absolute;
  bottom: 20px;
  width: 100%;
  text-align: center;
  z-index: 1; /* Ensure menu is above the canvas */
}

.element {
  width: 120px;
  height: 160px;
  box-shadow: 0px 0px 12px rgba(0, 255, 255, 0.5);
  border: 1px solid rgba(127, 255, 255, 0.25);
  font-family: Helvetica, sans-serif;
  text-align: center;
  line-height: normal;
  cursor: default;
}

.element:hover {
  box-shadow: 0px 0px 12px rgba(0, 255, 255, 0.75);
  border: 1px solid rgba(127, 255, 255, 0.75);
}

.element .number {
  position: absolute;
  top: 20px;
  right: 20px;
  font-size: 12px;
  color: rgba(127, 255, 255, 0.75);
}

.element .symbol {
  position: absolute;
  top: 40px;
  left: 0px;
  right: 0px;
  font-size: 60px;
  font-weight: bold;
  color: rgba(255, 255, 255, 0.75);
  text-shadow: 0 0 10px rgba(0, 255, 255, 0.95);
}

.element .details {
  position: absolute;
  bottom: 15px;
  left: 0px;
  right: 0px;
  font-size: 12px;
  color: rgba(127, 255, 255, 0.75);
}

.element img {
  width: 120px;
  height: 160px;
}

button {
  color: rgba(127, 255, 255, 0.75);
  background: transparent;
  outline: 1px solid rgba(127, 255, 255, 0.75);
  border: 0px;
  padding: 5px 10px;
  cursor: pointer;
  margin: 0 5px; /* Add some spacing between buttons */
}

button:hover {
  background-color: rgba(0, 255, 255, 0.5);
}

button:active {
  color: #000000;
  background-color: rgba(0, 255, 255, 0.75);
}

.show_info {
  position: fixed;
  background-color: rgba(0, 0, 0, 0.6);
  padding: 10px;
  width: 300px;
  margin: 0 auto;
  left: 0;
  right: 0;
  border-radius: 5px;
  box-shadow: 0 0 10px 0 #fff;
  top: 10%;
}

.show_info img {
  display: block;
  margin: auto;
  border-radius: 5px;
  height: 400px;
  width: 300px;
  box-shadow: 0 0 10px 0 #888;
}

.show_info .intro {
  color: #fff;
  text-indent: 20px;
  margin-top: 10px;
  height: 65px;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
}

.show_info .info_my {
  text-align: center;
}

.show_info .info_my > * {
  display: inline-block !important;
  vertical-align: middle;
}

.show_info .info_my .info_mem {
  color: #fff;
  max-width: 290px;
}

.show_info .info_my .info_mem > div {
  text-align: left;
}

.show_info .info_my .info_mem > div.code,
.show_info .info_my .info_mem > div.title {
  text-align: center;
  max-width: 290px;
  text-overflow: ellipsis;
  overflow: hidden;
  white-space: nowrap;
}
</style>
