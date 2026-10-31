<template>
  <div class="udStreamWrap">
    <canvas id="canvas" oncontextmenu="return false"></canvas>
  </div>
</template>
<script lang="ts" setup>
import {onMounted, ref, shallowRef, watchEffect} from 'vue'
import {uuid} from '@/utils';
import {
  Instance,
  getFirstSelectedNode,
  getSettings,
  saveSettings,
  takeScreenshot,
  registerGetScreenshotDataCallback,
  removeProjectNode,
  getProjectSettings,
  moveToWhenPossible
} from './lib/udStream'
import {ProjectNode} from './lib/ProjectNode';

const props = defineProps({
  modelUrl: {type: String, required: true}//模型地址
})
/**
 * 引擎实例
 * */
const instance = shallowRef()

const canvasId = uuid()
onMounted(async () => {
  instance.value = await new Instance({
    id: 'canvas',
    args: ['--domain'],
    clickedItemCallback: (uuid: string) => {
      moveToWhenPossible(uuid)
    }
  })


  // 加载模型
  loadScene(props.modelUrl)

  setTimeout(() => {
    getSettings().then((settings) => {
      // console.log(11, JSON.stringify(settings))
      //控制模式
      settings.camera.cameraMouseBindings = [2, 1, 0]
      saveSettings(settings)
    })

    registerGetScreenshotDataCallback(handleScreenshotDownload);
  }, 1000)


  // instance.loadSceneItem('https://dev.wenwuyun.cn:8060/staticFiles/uds0/ZJD.uds', true)
  // 加载地图
  /*await instance.value.createMapTileNode({
    address: 'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{0}/{2}/{1}',
    height: 0,
    blendMode: 2,
    opacity: 100,
    maxZoom: 19
  })*/
})

/**
 * 加载场景
 * */
function loadScene(modelUrl: string) {
  instance.value?.loadSceneItem(modelUrl, true)
}

function info() {
  getFirstSelectedNode().then((nodePtr: number) => {
    // 注意返回的是节点的指针值，通过new class来获取节点
    let node = new ProjectNode(nodePtr)
    console.log(1122, node)
  })
}

/**
 * 截图图像下载
 * */
function handleScreenshotDownload(data: string) {
  const a = document.createElement('a');
  a.href = 'data:image/png;base64,' + data;
  a.download = 'Screenshot.png';
  a.click();
}

/**
 * 截图事件
 * */
function handleTakeScreenshot() {
  takeScreenshot()
}

/**
 *  调整饱和度
 * */
function handleChangeSature(value: number) {
  getSettings().then((settings) => {
    settings.saturation = value
    saveSettings(settings)
  })
}

function handleUpdateSetting(setting: object) {
  getSettings().then((settings) => {
    // console.log(112233,settings)
    console.log('handleUpdateSetting', setting)
    Object.assign(settings, setting)
    saveSettings(settings)
  })
}

function handleClearAll() {
  // removeProjectNode()
}

defineExpose({handleTakeScreenshot, handleChangeSature, handleUpdateSetting})

watchEffect(() => {
  loadScene(props.modelUrl)
})
</script>
<style scoped>
.udStreamWrap, .udStreamWrap canvas {
  height: 100%;
  width: 100%;
}

.udStreamWrap {
  overflow: hidden;
}
</style>
