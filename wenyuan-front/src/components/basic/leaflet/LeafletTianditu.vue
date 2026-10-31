<template>
  <HLeaflet v-bind="$attrs" @ready="mapReady" :options="{ zoomControl: false, preferCanvas: true }">
    <LTileLayer
      v-for="tileProvider in tileProviders"
      :url="tileProvider.url"
      :key="tileProvider.name"
      :name="tileProvider.name"
      :options="options"
      subdomains="01234567"
      :visible="tileProvider.visible"
      :layer-type="tileProvider.type as LayerType"
    ></LTileLayer>
    <slot></slot>
  </HLeaflet>
</template>
<script lang="ts" setup>
//@ts-ignore
import { LLayerGroup, LTileLayer } from '@vue-leaflet/vue-leaflet';
import HLeaflet from './Leaflet.vue';
import { computed, PropType, ref, watch } from 'vue';
import { LayerType } from '@vue-leaflet/vue-leaflet/dist/src/types/enums/LayerType';

const props = defineProps({
  token: {
    type: String,
    default: 'c93ea43b61c76df411540db24f55bfd1',
  },
  layerType: {
    type: String as PropType<'map' | 'satellite'>,
    default: 'map',
  },
  markerShow: {
    type: Boolean,
    default: true,
  },
});

const options = {
  key: props.token,
  maxNativeZoom: 18,
  minZoom: 5,
  maxZoom: 22,
};

const tileProviders = computed(() => {
  const { layerType, markerShow } = props;
  return [
    {
      name: '地图',
      visible: layerType === 'map',
      type: 'base',
      url: '//t{s}.tianditu.gov.cn/vec_w/wmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=vec&STYLE=default&TILEMATRIXSET=w&FORMAT=tiles&TILECOL={x}&TILEROW={y}&TILEMATRIX={z}&tk={key}',
    },
    {
      name: '卫星',
      visible: layerType === 'satellite',
      type: 'base',
      url: '//t{s}.tianditu.gov.cn/img_w/wmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=img&STYLE=default&TILEMATRIXSET=w&FORMAT=tiles&TILECOL={x}&TILEROW={y}&TILEMATRIX={z}&tk={key}',
    },
    {
      name: '标记',
      visible: markerShow,
      type: 'overlay',
      url: '//t{s}.tianditu.gov.cn/cva_w/wmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=cva&STYLE=default&TILEMATRIXSET=w&FORMAT=tiles&TILECOL={x}&TILEROW={y}&TILEMATRIX={z}&tk={key}',
    },
  ];
});

const mapInstance = ref();

/**
 * 地图加载完成
 * */
function mapReady(instance: any) {
  mapInstance.value = instance;
}

defineExpose({ mapInstance });
</script>
