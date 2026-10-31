<template>
  <canvas ref="canvasRef" style="width: 100%; height: 100%; outline: none; box-sizing: border-box"></canvas>
</template>

<script lang="ts" setup>
import { getAuthenticationHeaders } from '@/utils/auth';
import {
  AppendSceneAsync,
  ArcRotateCamera,
  Color4,
  Engine,
  HemisphericLight,
  ILoadingScreen,
  Scene,
  Vector3,
  WebGPUEngine,
  WebRequest
} from '@babylonjs/core';
import '@babylonjs/loaders';
import { AdvancedDynamicTexture, Control, Slider, StackPanel, TextBlock } from '@babylonjs/gui';
import { onMounted, onUnmounted, ref, shallowRef, watch } from 'vue';


const props = defineProps({
  url: { type: String },
});

/**
 * 支持格式
 * */
const supportFormat = ref(true);

/**
 * 自定义加载样式
 * */
class CustomLoadingScreen implements ILoadingScreen {
  //optional, but needed due to interface definitions
  public loadingUIBackgroundColor: string = '';

  constructor(public loadingUIText: string) {}

  public displayLoadingUI() {}

  public hideLoadingUI() {}
}

//增加授权信息
WebRequest.CustomRequestHeaders = getAuthenticationHeaders();

const canvasRef = ref();
const engineRef = shallowRef<Engine | WebGPUEngine>();

const emits = defineEmits(['load']);

const loadingScreen = new CustomLoadingScreen('');

async function previewModel() {
  const { url } = props;
  if (!url) {
    emits('load', false);
    return;
  }
  if (engineRef.value) {
    engineRef.value.dispose();
  }
  let engine: Engine | WebGPUEngine;
  if (navigator.gpu) {
    engine = new WebGPUEngine(canvasRef.value);
    engine.loadingScreen = loadingScreen;
    engine.hideLoadingUI();
    await engine.initAsync();
  } else {
    engine = new Engine(canvasRef.value, true);
    engine.loadingScreen = loadingScreen;
    engine.hideLoadingUI();
  }

  engineRef.value = engine;

  const scene = new Scene(engine);

  scene.clearColor = new Color4(0, 0, 0, 0); // 完全透明

  try {
    await AppendSceneAsync(url, scene);
    emits('load', true);
  } catch (e) {
    console.log(22,e)
    emits('load', false);
  }

  scene.createDefaultCameraOrLight(true, true, true);

  const light = new HemisphericLight('hemiLight', new Vector3(0, 1, 0), scene);
  light.intensity = 1.2;

  const camera = scene.activeCamera as ArcRotateCamera;
  camera.alpha = Math.PI / 2;

  buildControlGui(light);

  engine.runRenderLoop(() => {
    scene.render();
  });
}

/**
 * 控制面板
 * */
function buildControlGui(light: HemisphericLight) {
  // 创建 GUI
  const advancedTexture = AdvancedDynamicTexture.CreateFullscreenUI('UI');

  // 标签：文字显示当前亮度
  const label = new TextBlock();
  label.text = '光源亮度: ' + light.intensity;
  label.color = 'black';
  label.fontSize = 14;
  advancedTexture.addControl(label);

  // 滑动条
  const slider = new Slider();
  slider.minimum = 0;
  slider.maximum = 10;
  slider.value = light.intensity;
  slider.height = '20px';
  slider.width = '200px';
  slider.color = 'black';
  slider.background = 'gray';
  slider.onValueChangedObservable.add(function (value) {
    light.intensity = value;
    label.text = `光源亮度: ${value.toFixed(1)}`;
  });

  // 垂直布局容器
  const panel = new StackPanel();
  panel.width = '200px';
  panel.height = '80px';
  panel.verticalAlignment = Control.VERTICAL_ALIGNMENT_TOP;
  panel.horizontalAlignment = Control.HORIZONTAL_ALIGNMENT_LEFT;
  panel.paddingTop = '5px';
  panel.paddingLeft = '5px';

  panel.addControl(label);
  panel.addControl(slider);
  advancedTexture.addControl(panel);
}

watch(
  () => props.url,
  (url) => {
    previewModel();
  },
);

onMounted(async () => {
  await previewModel();
});
onUnmounted(() => {
  engineRef.value?.dispose();
});
</script>
