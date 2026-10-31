<template>
  <div ref="refContainer" style="height: 100%; width: 100%"></div>
</template>
<script lang="ts" setup>
import { onMounted, ref } from 'vue';
import * as THREE from 'three';
//@ts-ignore
import * as GaussianSplats3D from '@mkkellogg/gaussian-splats-3d';

const props = defineProps({ path: { type: String } });

const refContainer = ref();

function init() {
  // 创建渲染器
  const renderer = new THREE.WebGLRenderer();
  renderer.setSize(refContainer.value.clientWidth, refContainer.value.clientHeight);
  refContainer.value.appendChild(renderer.domElement);

  const viewer = new GaussianSplats3D.Viewer({
    renderer: renderer,
    cameraUp: [0, -1, -0.6],
    initialCameraPosition: [-1, -4, 6],
    initialCameraLookAt: [0, 4, 0],
  });

  viewer
    .addSplatScene(props.path, {
      splatAlphaRemovalThreshold: 5,
      showLoadingUI: true,
      position: [0, 1, 0],
      rotation: [0, 0, 0, 1],
      scale: [1.5, 1.5, 1.5],
    })
    .then(() => {
      viewer.start();
    });
}

onMounted(() => {
  init();
});
</script>
