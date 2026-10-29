<template>
  <h-leaflet v-bind="$attrs" @ready="handleReady" :crs="crs" :options="{ zoomControl: false, preferCanvas: true }">
    <slot></slot>
  </h-leaflet>
</template>
<script lang="ts" setup>
//@ts-nocheck
import {TiledMapLayer} from './supermap/TiledSuperMapLayer.js';
// @ts-ignore
import HLeaflet from './Leaflet.vue'
// @ts-ignore
import {CRS} from 'leaflet';

const crs = CRS.EPSG4326

function handleReady(instance: any) {
  new TiledMapLayer(
    //@ts-ignored
    'http://10.248.223.223:8090/iserver/services/map-ugcv5-FengXianJiChuDiTuNew/rest/maps/FengXianJiChuDiTuNew',
    {tileSize: 256, attribution: null}).addTo(instance);
}


</script>
