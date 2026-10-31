<template>
  <div class="heritageMap">
    <div class="mapLegend">
        <n-flex vertical>
            <div class="yichan">
               遗产区
            </div>
            <div class="huanchong">
                缓冲区
            </div>
        </n-flex>
    </div>
    <LeafletLocal
      :center="center"
      :zoom="zoom"
      :minZoom="7"
      @ready="handleMapReady"
      :tiles="tiles"
    >
      <LMarkerClusterGroup :showCoverageOnHover="false" :icon-create-function="drawRedCluster">
        <!-- 如果存在 composeList，遍历显示所有点 -->
        <template v-if="form.composeList && form.composeList.length > 0">
          <LMarker
            v-for="item in form.composeList"
            :key="item.id"
            :lat-lng="getLatLng(item)"
          >
            <LIcon
              :icon-size="[40, 40]"
              :icon-anchor="[6, 6]"
              class-name="yellow-marker"
            >
              <div class="marker-dot"></div>
            </LIcon>
            <LTooltip>{{ item.heritageName || item.name || '遗产点' }}</LTooltip>
          </LMarker>
        </template>

        <!-- 如果不存在 composeList，显示单个点 -->
        <template v-else-if="form.latlng">
          <LMarker :lat-lng="center">
            <LIcon
              :icon-size="[40, 40]"
              :icon-anchor="[6, 6]"
              class-name="yellow-marker"
            >
              <div class="marker-dot"></div>
            </LIcon>
            <LTooltip>{{ form.name || '遗产点' }}</LTooltip>
          </LMarker>
        </template>
      </LMarkerClusterGroup>
    </LeafletLocal>
  </div>
</template>

<script lang="ts" setup>
import { ref, watch, computed } from 'vue'
import L, { PointExpression, point, divIcon } from 'leaflet'
import { LeafletLocal } from '@/components/basic/leaflet'
import { LMarker, LControlLayers, LIcon, LTooltip } from '@vue-leaflet/vue-leaflet'
import 'vue-leaflet-markercluster/dist/style.css'
import { LMarkerClusterGroup } from 'vue-leaflet-markercluster'
import { useLeafletStore } from '@/components/basic/leaflet/useLeaflet';
import { log } from 'console'
const useLeaflet = useLeafletStore();
const props = defineProps({
  form: {
    type: Object,
    default: () => ({})
  }
})
const drawRedCluster = (cluster: any) => {
  const html = `<div><span>${cluster.getChildCount()}</span></div>`;
  const icon = divIcon({

    html: html,
    iconSize: point(0, 0),
  });
  return icon;
};
const center = computed(() => {
  if (props.form.latlng) {
    return props.form.latlng.split(',').map(Number)
  }
  return [39.9042, 116.4074]
})

const zoom = ref(10)
const mapRef:any = ref()
const  mapLyer:any =ref([])
function handleMapReady(instance: any) {
  mapRef.value = instance
  if(  mapRef.value.length>0){
    mapRef.value.forEach((item:any)=>{
  mapRef.value.removeLayer(item)
    })

  }


   console.log(props.form,'-----9999');

  if(props.form.composeList){
    console.log('1111');

 props.form.composeList.map((item:any)=>{
    if(item.bhfwData){
        console.log(JSON.parse(item.bhfwData));
        const data = JSON.parse(item.bhfwData)
        data.features.forEach((item:any)=>{
          addAreaLayer(item,  props.form.name, { color: '#ff0000' })
        })

    }
    if(item.jskzdzData){
        const data = JSON.parse(item.jskzdzData)
        data.features.forEach((item:any)=>{
          addAreaLayer(item,  props.form.name, { color: '#34947e' })
        })

    }
 })

  }else{
    console.log('2222255');

    if(props.form.protectArea){
        const data = JSON.parse(props.form.protectArea)
        data.features.forEach((item:any)=>{
          addAreaLayer(item,  props.form.name, { color: '#ff0000' })
        })

    }
    if(props.form.controlArea){
        const data = JSON.parse(props.form.controlArea)
        data.features.forEach((item:any)=>{
          addAreaLayer(item,  props.form.name, { color: '#34947e' })
        })
  }
}

}
const addAreaLayer = (area: any, name: string, style: L.PathOptions) => {
  if (!area) return;
  const geometry = area.geometry;
  const layer = createGeoJsonLayer(
    geometry.coordinates[0][0].length > 0 ? geometry.coordinates[0] : geometry.coordinates,
    name,
    {
      weight: 2,
      fillOpacity: 0.0,
      ...style,
    },
  );
mapLyer.value.push(layer)
  mapRef.value!.addLayer(layer);
};
function createGeoJsonLayer(geometry: any, title: string, layerStyle: any) {
  return useLeaflet.wgs84ToGeojson(geometry, layerStyle, { title });
}

// 获取坐标点
function getLatLng(item: any) {
  if (item.latlng) {
    return item.latlng.split(',')
  } else if (item.lat && item.lng) {
    return [item.lat, item.lng]
  }
  return [0, 0]
}

// 地图图层配置
const tiles = ref([
  {
    name: '地图',
    visible: true,
    type: 'base',
    url: 'http://t{s}.tianditu.gov.cn/vec_w/wmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=vec&STYLE=default&TILEMATRIXSET=w&FORMAT=tiles&TILECOL={x}&TILEROW={y}&TILEMATRIX={z}&tk=c93ea43b61c76df411540db24f55bfd1',
  },
])

// 监听form变化
watch(() => props.form, (val) => {
//     if(mapRef.value){
// if(props.form.composeList){
//  props.form.composeList.map((item:any)=>{
//     if(item.bhfwData){
//         const data = JSON.parse(item.bhfwData)
//         data.features.forEach((item:any)=>{
//           addAreaLayer(item,  props.form.name, { color: '#ff0000' })
//         })

//     }
//     if(item.jskzdzData){
//         const data = JSON.parse(item.jskzdzData)
//         data.features.forEach((item:any)=>{
//           addAreaLayer(item,  props.form.name, { color: '#34947e' })
//         })

//     }
//  })

//   }else{

//     if(props.form.protectArea){
//         const data = JSON.parse(props.form.protectArea)
//         data.features.forEach((item:any)=>{
//           addAreaLayer(item,  props.form.name, { color: '#ff0000' })
//         })

//     }
//     if(props.form.controlArea){
//         const data = JSON.parse(props.form.controlArea)
//         data.features.forEach((item:any)=>{
//           addAreaLayer(item,  props.form.name, { color: '#34947e' })
//         })
//   }
// }
//     }

}, { deep: true, immediate: true })
</script>

<style lang="scss" scoped>
.heritageMap {
  width: 100%;
  height: 500px;
  position:relative
}
.mapLegend {
  position: absolute;
  bottom: 20px;
  right: 20px;
  z-index: 99999;
  width: 120px;
  padding: 8px 0;
  border: 1px solid #ccc;
  background: #fff;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  border-radius: 5px;

  .yichan,
  .huanchong {
    position: relative;
    padding-left: 45px;
    line-height: 20px;
    display: flex;
    align-items: center;
    justify-content: center;

    &::before {
      content: "";
      position: absolute;
      left: 6px;
      top: 50%;
      transform: translateY(-50%);
      width: 30px;
      height: 4px;
      border-radius: 2px;
    }
  }

  .yichan::before {
    background-color: #ff0000; // 红色
  }

  .huanchong::before {
    background-color: #34947e; // 绿色
  }
}
:deep(.yellow-marker) {
  .marker-dot {
    width: 12px;
    height: 12px;
    background-color: #ffd700;
    border: 2px solid #ffaa00;
    border-radius: 50%;
    box-shadow: 0 2px 4px rgba(0, 0, 0, 0.3);
  }
}
</style>
