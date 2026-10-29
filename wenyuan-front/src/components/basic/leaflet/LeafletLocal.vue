<template>
  <HLeaflet v-bind="$attrs" @ready="mapReady" :options="{ zoomControl: false, preferCanvas: true }">
    <LTileLayer
      v-for="tileProvider in tileProviders"
      :url="tileProvider.url"
      :key="tileProvider.name"
      :name="tileProvider.name"
      :options="options"
      :tileSize="256"
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
import { computed, ref ,watch} from 'vue';
import { LayerType } from '@vue-leaflet/vue-leaflet/dist/src/types/enums/LayerType';

const props = defineProps({
  tiles: {
    type: Object,
    default: () => {
      const baseUrl = APP_BASE_URL + APP_API_FRONT_PATH;
      return [
        {
          name: '地图',
          visible: true,
          type: 'base',
          url: `${baseUrl}/map/tiles/image/{z}/{x}/{y}.png`,
        },
        {
          name: '标记',
          visible: true,
          type: 'overlay',
          url: `${baseUrl}/map/tiles/cia/{z}/{x}/{y}.png`,
        },
      ];
    },
  },
});


const options = {};

const tileProviders = computed(() => {
  const { tiles } = props;
  return tiles;
});

const mapInstance = ref();



watch(()=>tileProviders.value,(newValue)=>{
console.log(newValue,'----------------1111111')
},{deep:true,immediate:true})


/**
 * 地图加载完成
 * */
function mapReady(instance: any) {
  mapInstance.value = instance;
}

defineExpose({ mapInstance });
</script>
