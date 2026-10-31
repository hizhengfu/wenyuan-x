<template>
  <n-button type="primary" @click="show = true">
    <slot> 选择坐标</slot>
  </n-button>
  <n-modal
    v-model:show="show"
    style="width: 800px; height: 600px"
    preset="card"
    :content-style="{ padding: 0 }"
    :segmented="{ content: true, action: true }"
  >
    <template #header>
      <n-form inline :show-feedback="false" label-placement="left">
        <n-form-item label="地址查找" :show-label="false" :show-feedback="false" style="width: 400px">
          <n-input-group>
            <n-input v-model:value="keywords" placeholder="请输入地址关键字查找"></n-input>
            <n-button type="primary" @click="handleSearch(undefined)">
              <n-icon size="20px">
                <IconMdiSearch />
              </n-icon>
            </n-button>
          </n-input-group>
        </n-form-item>
      </n-form>
    </template>
    <template #header-extra>
      <span v-if="marker">选择坐标：{{ marker.latlng }}</span>
    </template>
    <n-layout has-sider class="wh-full">
      <n-layout-sider :width="300" bordered v-if="poiResult">
        <n-layout-header bordered>
          <div style="padding: 10px">
            <template v-if="poiResult.prompt">在「{{ (last(poiResult.prompt) as any).admins[0].adminName }}」</template>
            为您搜到「{{ poiResult?.count }}」条记录
          </div>
        </n-layout-header>
        <n-layout-content position="absolute" style="top: 50px; padding: 10px">
          <div v-if="poiResult">
            <n-flex v-if="poiResult.statistics" justify="space-between">
              <n-tag
                v-for="priorityCity in poiResult.statistics.priorityCitys"
                size="small"
                @click="handleSearch(priorityCity.adminCode)"
                type="info"
              >
                {{ priorityCity.adminName }}({{ priorityCity.count }})
              </n-tag>
            </n-flex>
            <n-list>
              <template v-if="poiResult.statistics" #header>
                其他地区({{ poiResult.statistics.adminCount }})有更多相关地点：
              </template>
              <template v-if="poiResult.statistics">
                <n-list-item v-for="item in poiResult.statistics.allAdmins" @click="handleSearch(item.adminCode)">
                  {{ item.adminName }}({{ item.count }})
                </n-list-item>
              </template>
              <template v-if="markers">
                <n-list-item v-for="marker in markers" @click="handleSelectMarker(marker)">
                  <n-thing :title="marker.title"> 地址：{{ marker.address }}</n-thing>
                </n-list-item>
              </template>
            </n-list>
          </div>
        </n-layout-content>
      </n-layout-sider>
      <n-layout-content content-style="padding: 24px;">
        <leaflet-tianditu :center="center" :min-zoom="5" :zoom="12" :max-zoom="20" @ready="handleMapReady">
          <l-control-layers position="topright"></l-control-layers>
          <l-control-zoom position="topleft"></l-control-zoom>
          <l-marker
            v-for="marker in markers"
            :lat-lng="marker.latlng"
            :draggable="marker.draggable"
            @moveend="handleMarkerMove"
            @click="handleSelectMarker(marker)"
          >
            <l-tooltip>
              <div>
                <p>名称:{{ marker.title }}</p>
                <p v-if="marker.address">地址:{{ marker.address }}</p>
              </div>
            </l-tooltip>
          </l-marker>
          <slot></slot>
        </leaflet-tianditu>
      </n-layout-content>
    </n-layout>
    <template #action>
      <n-space justify="space-around">
        <n-button type="success" @click="handleSubmit">
          <template #icon>
            <n-icon>
              <IconMdiCheck></IconMdiCheck>
            </n-icon>
          </template>
          点击选择
        </n-button>
      </n-space>
    </template>
  </n-modal>
</template>
<script lang="ts" setup>
// @ts-ignore
import { LControlLayers, LControlZoom, LMarker, LTooltip } from '@vue-leaflet/vue-leaflet';
import LeafletTianditu from './LeafletTianditu.vue';
import { ref, watch } from 'vue';
import axios, { AxiosResponse } from 'axios';
import { LatLngExpression } from 'leaflet';
import { last } from 'lodash-es';

const props = defineProps({
  value: { type: String },
  specify: { type: String }, //默认城市 具体查看http://lbs.tianditu.gov.cn/server/search2.html
  token: { type: String }, //token
  // center: {
  //   type: Array, default() {
  //     return ['35.164846', '108.411794']
  //   }
  // },//中心点
});
const emits = defineEmits(['update:value', 'mapReady']);

type Poi = {
  title: string;
  address?: string;
  draggable: boolean;
  latlng: LatLngExpression;
};

const keywords = ref('');
const show = ref(false);
const markers = ref<any>([]);
const marker = ref<Poi>();

const poiResult = ref<any>();

// 默认中心点
const center = ref(['35.164846', '108.411794']);

watch(
  () => props.value,
  (newVal) => {
    if (!newVal) return;
    let point = String(newVal)?.split(',');
    let poi = { title: '上次选择位置', draggable: true, latlng: point };
    //@ts-ignore
    markers.value.push(poi);
    //@ts-ignore
    marker.value = poi;
    center.value = point;
  },
  { immediate: true },
);

/**
 * 选择标记点
 * */
function handleSelectMarker(poi: Poi) {
  marker.value = poi;
  //@ts-ignore
  center.value = poi.latlng;
}

/**
 * marker移动
 * */
function handleMarkerMove(item: any) {
  let latLng = item.target.getLatLng();
  //获取当前点位的经纬度
  if (marker.value) {
    marker.value.latlng = [latLng.lat, latLng.lng];
  }
}

/**
 * 根据地址搜索
 * 参数参考 http://lbs.tianditu.gov.cn/server/search2.html
 * */
function handleSearch(code: string | undefined) {
  if (!keywords.value) return;
  const { token, specify } = props;
  markers.value = [];
  axios
    .get('https://api.tianditu.gov.cn/v2/search', {
      params: {
        postStr: JSON.stringify({
          keyWord: keywords.value,
          specify: code || specify,
          level: 3,
          mapBound: '31.560546874998437,1.3322253117867007,178.68945312499847,49.84700189675334',
          queryType: 1,
          start: 0,
          count: 10,
        }),
        type: 'query',
        tk: token,
      },
    })
    .then((resp: AxiosResponse) => {
      if (resp.data.pois) {
        let pois: Poi[] = [];
        resp.data.pois.forEach((poi: any) => {
          let pos = poi.lonlat.split(',');
          pois.push({ title: poi.name, draggable: true, address: poi.address, latlng: [pos[1], pos[0]] });
        });
        if (pois.length > 0) {
          //@ts-ignore
          center.value = pois[0].latlng;
          markers.value = markers.value.concat(pois);
        }
      }
      poiResult.value = resp.data;
    });
}

function handleSubmit() {
  //@ts-ignore
  emits('update:value', marker.value?.latlng.join(','));
  // emits('update:value', marker.value?.latlng.toString())
  show.value = false;
}

/**
 * 地图加载完成
 * */
function handleMapReady(instance: any) {
  emits('mapReady', instance);
}
</script>
<style lang="scss" scoped>
.map-wrap {
  position: relative;
  height: 100%;
}

.search-results {
  position: absolute;
  left: 0;
  top: 0;
  z-index: 1000;
  width: 200px;
}
</style>
