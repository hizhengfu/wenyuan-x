<template>
  <TresGroup>
    <primitive :object="tiles.group" />
    <!-- 主要定向光 -->
    <TresDirectionalLight color="#ffffff" :position="[100, 100, 100]" :intensity="10" :cast-shadow="false" />
    <!-- 补充定向光 - 从相反方向照射 -->
    <TresDirectionalLight color="#ffffff" :position="[-100, -100, -100]" :intensity="10" :cast-shadow="false" />
    <!-- 环境光 - 提供基础照明 -->
    <TresAmbientLight color="#ffffff" :intensity="0.8" />
  </TresGroup>
</template>

<script lang="ts" setup>
import { getAuthenticationHeaders } from '@/utils/auth';
import { useRenderLoop, useTresContext } from '@tresjs/core';
//@ts-ignore
import {TilesRenderer} from '3d-tiles-renderer';
//@ts-ignore
import { UpdateOnChangePlugin } from '3d-tiles-renderer/plugins';
import { onMounted } from 'vue';
import { Box3, DoubleSide, Sphere } from 'three';
//@ts-ignore
import { DRACOLoader } from 'three/examples/jsm/loaders/DRACOLoader';
//@ts-ignore
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader';


if (!ENABLE_DRACO) {
  throw Error('请在config配置文件中设置enable_draco为true');
}

const props = defineProps({ path: String });

const tiles: TilesRenderer = new TilesRenderer(props.path);
tiles.errorTarget = 10;

const { camera, renderer, sizes, controls } = useTresContext();
const dracoLoader = new DRACOLoader();

dracoLoader.setDecoderPath('./draco/gltf/');

const loader = new GLTFLoader(tiles.manager);
loader.setRequestHeader(getAuthenticationHeaders());
loader.setWithCredentials(true);
tiles.fetchOptions = { credentials: 'include', headers: getAuthenticationHeaders() };
loader.setDRACOLoader(dracoLoader);

tiles.manager.addHandler(/\.(gltf|glb)$/g, loader);
tiles.registerPlugin(new UpdateOnChangePlugin());
tiles.addEventListener('load-tile-set', () => {
  const box = new Box3();
  const sphere = new Sphere();
  if (tiles.getBoundingBox(box)) {
    box.getCenter(tiles.group.position);
    tiles.group.position.multiplyScalar(-1);
  } else if (tiles.getBoundingSphere(sphere)) {
    tiles.group.position.copy(sphere.center);
    tiles.group.position.multiplyScalar(-1);
  }
});
//@ts-ignore
tiles.addEventListener('load-model', ({ scene }) => {
  scene.traverse((c: any) => {
    if (c.isMesh) {
      if (c.material) {
        c.material.side = 2;
      }
      c.receiveShadow = false;
      c.castShadow = false;
    }
  });

  const box = new Box3();
  const sphere = new Sphere();
  if (tiles.getBoundingBox(box)) {
    box.getCenter(tiles.group.position);
    tiles.group.position.multiplyScalar(-1);
  } else if (tiles.getBoundingSphere(sphere)) {
    tiles.group.position.copy(sphere.center);
    tiles.group.position.multiplyScalar(-1);
  }
  //@ts-ignore
  tiles.setResolutionFromRenderer(camera.value, renderer.value);
});

function init() {
  //@ts-ignore
  tiles.setCamera(camera.value);
  //@ts-ignore
  tiles.setResolutionFromRenderer(camera.value, renderer.value);
  //@ts-ignore
  tiles.setResolution(camera.value, sizes.width, sizes.height);
  tiles.update();
}

onMounted(() => {
  init();
});

const { onBeforeLoop } = useRenderLoop();
onBeforeLoop(() => {
  if (camera.value && sizes.width.value) {
    camera.value.updateMatrixWorld(true);

    tiles.update();
  }
});
</script>
