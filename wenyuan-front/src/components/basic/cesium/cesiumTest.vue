<template>
  <vc-config-provider :cesium-path="vcConfig.cesiumPath">
    <vc-viewer :sky-box="false" @ready="onViewerReady">
      <vc-primitive-tileset
        ref="primitive"
        :url="layerUrl"
        @ready="onTilesetReady"
        @all-tiles-loaded="allTilesLoaded($event)"
      >
      </vc-primitive-tileset>
      <!-- 图标布告板集合 -->
      <vc-collection-billboard
        ref="billboardCollectionRef"
        :billboards="billboards"
        @ready="onBillboardReady"
        @click="onClickBillboard"
        @mouseover="onMouseover"
        @mouseout="onMouseout"
      >
      </vc-collection-billboard>
    </vc-viewer>
  </vc-config-provider>
</template>
<script setup lang="ts">
import { reactive, ref, markRaw, nextTick, onMounted } from 'vue';
import {
  VcConfigProvider,
  VcViewer,
  VcPrimitiveTileset,
  VcCollectionBillboard,
} from 'vue-cesium';
import 'vue-cesium/dist/index.css';

import { formatPointAlarm, formatValue } from '../../../modules/monitor/utils/monitor';
import type { VcReadyObject } from 'vue-cesium/es/utils/types';

const props = defineProps({
  layerUrl: {
    //图像路径
    type: String,
  },
  placeList: {
    type: Array,
    default: [],
  },
});

/**
 * 地图示例
 * */
let mapInstance: any = null;

const vcConfig = reactive({
  cesiumPath: 'https://unpkg.com/cesium@latest/Build/Cesium/Cesium.js',
});

const billboards = ref<Array<any>>([]);

const onViewerReady = ({ Cesium, viewer, vm }: VcReadyObject) => {
  vm?.vcMitt.on('destroyed', (e: any) => {
    console.log('viewer is destroyed', e);
  });
  //@ts-ignore
  viewer.scene.terrainProvider = Cesium.EllipsoidTerrainProvider({}); //除去自带的地形图
  viewer.scene.globe.depthTestAgainstTerrain = true; //是否启用深度监测
  viewer.scene.globe.enableLighting = false; //是否显示昼夜区域
  // viewer.scene.fog.enabled = false; // 禁用雾效
  // viewer.scene.requestRenderMode = true; // 启用请求渲染模式，优化性能
  // viewer.scene.maximumRenderTimeChange = Infinity; // 仅当场景变化时才渲染
  // viewer.scene.debugShowFramesPerSecond = true; // 显示帧率，便于调试

  // const controller = viewer.scene.screenSpaceCameraController;

  // // 设置相机的缩放限制
  // controller.minimumZoomDistance = 100; // 最小缩放距离
  // controller.maximumZoomDistance = 5000; // 最大缩放距离

  // // 禁用倾斜和旋转
  // controller.enableTilt = false;
  // controller.enableLook = false;
};
const onBillboardReady = ({ cesiumObject: tileset, viewer }: VcReadyObject) => {
  //@ts-ignore
  // tileset.modelMatrix = Cesium.Matrix4.IDENTITY; // 使用默认矩阵
};

const onTilesetReady = ({ cesiumObject: tileset, viewer }: VcReadyObject) => {
  mapInstance = markRaw(viewer);
  //@ts-ignore
  tileset.modelMatrix = Cesium.Matrix4.IDENTITY; // 使用默认矩阵
  viewer.zoomTo(tileset as any);
  addMarker();
};

// cesium 加载点位
const addMarker = () => {
  // 清除上一次加载的点位
  mapInstance.entities.removeAll();
  nextTick(() => {
    // foreach循环加载点位
    props.placeList.forEach((pointObj: any) => {
      const alarm = formatPointAlarm(pointObj.lines);
      const billboard: any = {};
      billboard.position = Cesium.Cartesian3.fromDegrees(pointObj.x, pointObj.y, pointObj.z);
      billboard.image = `./sensor/marker-${alarm?.maxAlarmLevel || 0}.svg`;
      billboard.scale = 0.3; //放缩
      billboard.id = pointObj.id;
      billboard.disableDepthTestDistance = 0; //禁用深度监测
      billboard.horizontalOrigin = 0; //水平对齐方式
      billboard.verticalOrigin = 1; //竖直对齐方式
      billboards.value.push(billboard);
    });
  });
};

const onClickBillboard = (e: any) => {
  console.log(e);
  // const pickedObject = mapInstance.scene.pick(e.windowPosition);
  // if (Cesium.defined(pickedObject)) {
  //   console.log(pickedObject);
  // }
};

const onMouseover = (e: any) => {
  // console.log('over', e);
  if (e.cesiumObject instanceof Cesium.Billboard) {
    e.cesiumObject.scale = 0.5;
    e.pickedFeature.primitive.scale = 0.5;
  } else if (e.cesiumObject instanceof Cesium.BillboardCollection) {
    e.pickedFeature.primitive.scale = 0.5;
  }
};

const onMouseout = (e: any) => {
  // console.log('out', e);
  if (e.cesiumObject instanceof Cesium.Billboard) {
    e.cesiumObject.scale = 0.25;
  } else if (e.cesiumObject instanceof Cesium.BillboardCollection) {
    e.pickedFeature.primitive.scale = 0.25;
  }
};

const allTilesLoaded = (e: any): any => {
  console.log('allTilesLoaded');
};

onMounted(() => {});
</script>
<style scoped lang="scss"></style>
