<template>
  <n-layout position="absolute">
    <n-layout-header style="height: 64px; padding: 18px" bordered>
      <n-space justify="space-between">
        <n-space align="center">
          <n-icon size="22px" style="display: flex; justify-self: center">
            <IconMdiViewCarousel />
          </n-icon>
          <span style="font-size: 18px; font-weight: bold">图像地图编辑器<i>（坐标系为像素）</i></span>
        </n-space>
        <n-form inline label-placement="left" :show-feedback="false">
          <n-form-item label="">
            <HUpload v-model:value="imageUrl" accept="image/*" label="上传图像"></HUpload>
          </n-form-item>
          <n-form-item label="">
            <n-upload :show-file-list="false" @change="handleLoadGeoJson">
              <n-button type="info">加载GeoJson</n-button>
            </n-upload>
          </n-form-item>
          <n-form-item>
            <n-input-group>
              <n-button type="success" @click="handleDownloadGeoJson">导出GeoJson</n-button>
              <n-button type="warning" @click="handleCopyGeoJson">复制GeoJson</n-button>
            </n-input-group>
          </n-form-item>
        </n-form>
      </n-space>
    </n-layout-header>
    <n-layout has-sider position="absolute" style="top: 64px">
      <n-layout content-style="padding: 3px;">
        <LeafletImage
          :center="center"
          :zoom="zoom"
          :min-zoom="-3"
          :max-zoom="5"
          :url="imageUrl"
          @ready="handleMapReady"
        >
          <l-control-layers position="topright"></l-control-layers>
        </LeafletImage>
      </n-layout>
    </n-layout>
  </n-layout>
</template>
<script lang="ts" setup>
import L from 'leaflet';
import { useClipboard, useStorage } from '@vueuse/core';
//@ts-ignore
import { LControlLayers, LControlZoom, LGeoJson, LMarker, LTooltip } from '@vue-leaflet/vue-leaflet';
import LeafletImage from './LeafletImage.vue';
import { ref } from 'vue';
import { NButton, NIcon, NSpace, UploadFileInfo } from 'naive-ui';

import { saveAs } from 'file-saver';
import { notification } from '@/utils';
import area from '@turf/area';

import 'leaflet-draw';
import 'leaflet-draw/dist/leaflet.draw.css';

const imageUrl = useStorage('LEAFLET-IMAGE-STORE', '');

//地图实例
const mapRef = ref();
const drawLayersRef = ref();

const center = ref([0, 0]);
const zoom = ref(1);

/**
 * 地图加载完成事件
 * */
function handleMapReady(instance: any) {
  mapRef.value = instance;

  let editableLayers = new L.FeatureGroup();
  instance.addLayer(editableLayers);

  drawLayersRef.value = editableLayers;

  let options = {
    position: 'topleft',
    draw: {
      polyline: { title: '测试' },
    },
    edit: {
      featureGroup: editableLayers, //REQUIRED!!
      remove: true,
    },
  };

  // @ts-ignore
  let drawControl = new L.Control.Draw(options);
  instance.addControl(drawControl);

  // @ts-ignore
  instance.on(L.Draw.Event.CREATED, function (event) {
    let layer = event.layer;
    editableLayers.addLayer(layer);
  });
}

/**
 * 加载本地geojson文件
 * */
function handleLoadGeoJson(options: { fileList: UploadFileInfo[]; file: UploadFileInfo }) {
  let reader = new FileReader();
  reader.onload = (event: ProgressEvent<FileReader>) => {
    //读取文件并解析为json
    //@ts-ignore
    let geojson = JSON.parse(event.target?.result as string);
    if (geojson) {
      //加载geojson到地图
      let geojsonLayer = L.geoJSON(geojson, {
        style: {
          weight: 2,
          color: 'red',
          fillColor: 'red',
        },
        onEachFeature: (feature, layer: any) => {
          // drawLayersRef.value.addLayer(layer)
          let center = layer.getBounds().getCenter();

          let tooltip = `中心经纬度：${center}<br/>面积：${area(feature) / 1000000}平方公里`;
          // layer.bindPopup(`面积：${area(feature) / 1000000}平方公里`)
        },
      });
      drawLayersRef.value.addLayer(geojsonLayer);
      // let geojsonLayer = L.geoJSON(geojson).addTo(mapRef.value)
      //移动位置到geojson图层
      mapRef.value.fitBounds(geojsonLayer.getBounds());
    }
  };
  reader.readAsText(options.file.file as Blob, 'utf-8');
}

/**
 * 下载geojson文件
 * */
function handleDownloadGeoJson() {
  if (drawLayersRef.value) {
    let geojson = drawLayersRef.value.toGeoJSON();
    let file = new Blob([JSON.stringify(geojson)], {
      type: 'text/plain;charset=utf-8',
    });
    saveAs(file, `geojson-${new Date().getSeconds()}.geojson`);
  } else {
    notification({ title: '无编辑图层', type: 'error' });
  }
}

const { copy } = useClipboard({ legacy: true });

/**
 * 复制geojson文件
 * */
function handleCopyGeoJson() {
  if (drawLayersRef.value) {
    let geojson = drawLayersRef.value.toGeoJSON();
    copy(JSON.stringify(geojson))
      .then(() => {
        notification({ title: '复制成功', type: 'success' });
      })
      .catch(() => {
        notification({ title: '复制失败', type: 'error' });
      });
  } else {
    notification({ title: '无编辑图层', type: 'error' });
  }
}
</script>
