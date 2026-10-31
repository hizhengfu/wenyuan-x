<template>
  <div class="container" id="drawer-target">
    <div class="logo"><img width="300" src="../../assets/images/logo.png" alt="" /></div>
    <HMapLocationPath
      class="location-path"
      :list="areaList"
      @click="handleClickLocation"
      v-if="!searchShow"
    ></HMapLocationPath>
    <n-drawer
      v-model:show="searchShow"
      placement="left"
      :show-mask="false"
      :mask-closable="false"
      closable
      to="#drawer-target"
      :resizable="true"
      default-width="500"
    >
      <n-drawer-content closable>
        <template #header>
          <HMapLocationPath class="location-path2" :list="areaList" @click="handleClickLocation"></HMapLocationPath>
        </template>
        <HMapSearch class="search" :searchParams="searchParams" @search="search"></HMapSearch>
        <HMapList :markerData="listData" @clickItem="clickItem" @loadMore="loadMore" :dataFlag="dataFlag"></HMapList>
      </n-drawer-content>
    </n-drawer>
    <div
      @click="closeSearch"
      class="back-btn"
      style="background: #a51a1a; color: #fff; cursor: pointer"
      v-if="!searchShow && zooms == 7"
    >
      筛选
    </div>
    <div class="map-conatiner">
      <LeafletTianditu :center="center" :zoom="zoom" :minZoom="4" @ready="handleMapReady">
        <template v-if="isStat">
          <template v-for="(marker, index) in markerData">
            <l-marker
              v-if="marker.latlng || (marker.area && marker.area.latlng)"
              :lat-lng="marker.area ? marker.area.latlng.split(',') : marker.latlng.split(',')"
              @click="handleSelectMarker(marker)"
            >
              <l-icon :icon-anchor="iconAnchor" :icon-size="iconSize" class="custom-marker-icon">
                <div style="display: flex; justify-content: center">
                  <img :src="marker.icon" alt="" class="marker-icon-img" />
                </div>
                <div
                  class="marker-title"
                  v-html="handleMarkerLabel(marker.area ? marker.area.name : (marker.publishName || marker.name), marker.value)"
                ></div>
              </l-icon>
            </l-marker>
          </template>
        </template>
        <LMarkerClusterGroup v-else>
          <template v-for="(marker, index) in markerData">
            <l-marker
              v-if="marker.latlng || (marker.area && marker.area.latlng)"
              :lat-lng="marker.area ? marker.area.latlng.split(',') : marker.latlng.split(',')"
              @click="handleSelectMarker(marker)"
            >
              <l-icon :icon-anchor="iconAnchor" :icon-size="iconSize" class="custom-marker-icon">
                <div style="display: flex; justify-content: center">
                  <img :src="marker.icon" alt="" class="marker-icon-img" />
                </div>
                <div
                  class="marker-title"
                  v-html="handleMarkerLabel(marker.area ? marker.area.name : marker.publishName, marker.value)"
                ></div>
              </l-icon>
            </l-marker>
          </template>
        </LMarkerClusterGroup>
        <LMarkerClusterGroup >
          <template v-for="(marker, index) in heritageMarker">
            <l-marker
              v-if="marker.latlng"
              :lat-lng="marker.latlng.split(',')"
             
            >
              <l-icon :icon-anchor="iconAnchorHeritage" :icon-size="iconSize" >
                <div style="display: flex; justify-content: center">
                  <img :src="marker.icon" alt="" class="marker-icon-img" />
                </div>
                <div
                  class="marker-title"
                  v-html="handleMarkerLabel(marker.name, marker.value)"
                ></div>
              </l-icon>
            </l-marker>
          </template>
        </LMarkerClusterGroup>
        <LeafletTiandituGeoJson @click="handleClickGeoJson"></LeafletTiandituGeoJson>
        <!--        <l-geo-json ref="geoJsonRef" :geojson="areaJson" :options="geoJsonOptions"></l-geo-json>-->

        <l-control-layers position="topright"></l-control-layers>
        <l-control-zoom position="topright"></l-control-zoom>
      </LeafletTianditu>
    </div>
    <!-- 图例 -->
    <HMapLegend class="legend"></HMapLegend>
    <HMapDetail
      v-model:show="showDetail"
      :currentInfo="currentInfo"
      style="width: 1000px; height: auto; z-index: 999"
      v-if="showDetail"
    ></HMapDetail>
  </div>
</template>
<script lang="ts" setup>
import { ref, onMounted, watch, toRef } from 'vue';
import { last } from 'lodash-es';
import L, { point, PointExpression } from 'leaflet';
import { LMarker, LControlZoom, LControlLayers, LTooltip, LGeoJson, LPopup, LIcon } from '@vue-leaflet/vue-leaflet';
import { LeafletTianditu, LeafletTiandituGeoJson, useLeafletStore } from '@/components/basic/leaflet';
import { LMarkerClusterGroup } from 'vue-leaflet-markercluster';
import 'vue-leaflet-markercluster/dist/style.css';
import { hexToRgb, rgbToHex } from '@/utils';
import HMapLegend from '../../components/basic/MapLegend.vue';
import HMapLocationPath from '../../components/basic/MapLocationPath.vue';
import HMapSearch from '../../components/basic/MapSearch.vue';
import HMapDetail from '../../components/basic/MapDetail.vue';
import HMapList from '../../components/basic/MapList.vue';
import { list, culturalList } from '../../api/basic/map';
import png1 from '../../assets/images/location1.png';
import png2 from '../../assets/images/location2.png';
import png3 from '../../assets/images/location3.png';
import png4 from '../../assets/images/location4.png';
import png5 from '../../assets/images/location5.png';
import { list as heritageList } from '../../api/heritage/wordHeritage';
import { list as composeList } from '../../api/heritage/compositionOfHeritage';

const useLeaflet = useLeafletStore();
//地图实例
const mapRef = ref();
const drewLayersRef = ref();
const center = ref(['35.164846', '108.411794']);
const zoom = ref(5);
const zooms = ref();
const markerData: any = ref([]);
const defaultmarkerData: any = ref([]);
const showDetail = ref(false);
const currentInfo = ref({});
const iconSize: PointExpression = [30, 40];
const iconAnchor: PointExpression = [25, 28];
const iconAnchorHeritage: PointExpression = [16, 28];
const searchShow = ref(false);

const searchParams = ref({});
const listData: any = ref([]);
const colors: any = ref({});
const dataFlag = ref(false);

//是否为统计数据 详细数据的情况下需要支持点位的聚合
const isStat = ref(true);

function handleMapReady(instance: any) {
  mapRef.value = instance;
  let editableLayers = new L.FeatureGroup();
  instance.addLayer(editableLayers);
  drewLayersRef.value = editableLayers;
  fetchWorldHeritage()
  // let geojsonLayer = L.geoJSON(areaJson as any, {
  //   style: (feature: any) => ({
  //     weight: 0.5,
  //     color: '#a21f1f',
  //     fillColor: getadcode(feature),
  //     fillOpacity: 0.8,
  //   }),
  // });
  // drewLayersRef.value.addLayer(geojsonLayer);
  // mapRef.value.fitBounds(geojsonLayer.getBounds());
}

function createMarkerIcon(item: any) {
  let icon = '';
  let str = '';
  if (item.area) {
    icon = png5;
    str = `${item.area.name}`;
  } else {
    if (item.publishLevel == '0') {
      icon = png1;
    } else if (item.publishLevel == '1') {
      icon = png2;
    } else if (item.publishLevel == '2') {
      icon = png3;
    } else {
      icon = png4;
    }
  }

  return { ...item, icon: icon };
}

function handleMarkerLabel(name: any, value: any) {
  if (!value) {
    return `<div>${name}</div>`;
  } else {
    return `<div>${name}:${value}</div>`;
  }
}

function closeSearch() {
  if (searchShow.value == false) {
    searchShow.value = true;
  }
}

function clickItem(item: any) {
  markerData.value = [];
  markerData.value.push(item);
  // center.value = item.latlng.split(',');
  let flyto = item.latlng.split(',');
  mapRef.value.flyTo(flyto, 7);
}

/**
 * 统计数据
 * */
function fetchStat(areaId: string) {
  markerData.value = [];
  list({ parentId: areaId, pageSize: -1 }).then((res) => {
    let data = res.data;
    if (data) {
      data.forEach((item: any, index: number) => {
        markerData.value.push(createMarkerIcon(item));
      });
      defaultmarkerData.value = markerData.value;
    }
  });
}

/**
 * 列表数据
 * */
function fetchList(areaId: string) {
  markerData.value = [];
  culturalList({ areaId: areaId, pageSize: -1 }).then((res: any) => {
    if (res.data && res.data.list) {
      let data = res.data.list;
      data.forEach((item: any, index: number) => {
        markerData.value.push(createMarkerIcon(item));
      });
      defaultmarkerData.value = markerData.value;
      listData.value = markerData.value;
    }
  });
}

function handleSelectMarker(item: any) {
  if (item.area) {
    if (item.area.type == 4) {
      searchShow.value = true;
      searchParams.value = { areaId: item.areaId, pageSize: -1 };
    } else if (item.area.type == 3) {
      searchShow.value = false;
    } else if (item.area.type == 2) {
      searchShow.value = false;
    } else if (item.area.type == 1) {
      searchShow.value = false;
    }
  }
  useLeaflet.updateAreaCode('156' + item.areaId);
  // handleClickGeoJson({ gb: '156' + item.areaId, name: item.area?.name });

  useLeaflet.addAreaHistory({ gb: '156' + item.areaId, name: item.area?.name });

  if (item.area && item.area?.type < 4) {
    markerData.value = [];
    list({ parentId: item.areaId, pageSize: -1 }).then((res) => {
      let data = res.data;
      if (data) {
        data.forEach((item: any, index: number) => {
          markerData.value.push(createMarkerIcon(item));
        });
        defaultmarkerData.value = markerData.value;
      }
    });
  } else if (item.area && item.area?.type == 4) {
    markerData.value = [];
    culturalList({ areaId: item.areaId, pageSize: -1 }).then((res: any) => {
      if (res.data) {
        let data = res.data.list;
        data.forEach((item: any, index: number) => {
          markerData.value.push(createMarkerIcon(item));
        });
        defaultmarkerData.value = markerData.value;
        listData.value = markerData.value;
      }
    });
  } else {
    showDetail.value = true;
    currentInfo.value = item;
  }
}

function search(params: any) {
  searchParams.value = params;
  if (!params.publishName && !params.publishLevel && !params.areaId && !params.category) {
    markerData.value = defaultmarkerData.value;
  } else {
    culturalList(params).then((res) => {
      listData.value = [];
      if (res.data && res.data.list) {
        let data = res.data.list;
        data.forEach((item: any, index: number) => {
          listData.value.push(createMarkerIcon(item));
        });
      }
    });
  }
}

function loadMore(data: any) {
  searchParams.value = { ...searchParams.value, pageNum: data, pageSize: 30 };
  if (!dataFlag.value) {
    culturalList(searchParams.value).then((res) => {
      if (res.data && res.data.list) {
        let data = res.data.list;
        data.forEach((item: any, index: number) => {
          listData.value.push(createMarkerIcon(item));
        });
        dataFlag.value = false;
      } else {
        dataFlag.value = true;
      }
    });
  }
}
function createGeoJsonLayer(geometry: any, title: string, layerStyle: any) {
  return useLeaflet.wgs84ToGeojson(geometry, layerStyle, { title });
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
      ...style, // 合并自定义样式
    },
  );
  mapRef.value?.addLayer(layer);
};
const heritageMarker:any =ref([])

function handleHeritageData(data: any, name: string) {
  data.latlng = `${data.lat},${data.lng}`;
  heritageMarker.value.push(createMarkerIcon(data));

  if (data.bhfwData || data.protectArea) {
    const bhfwJson = JSON.parse(data.bhfwData || data.protectArea);
    addAreaLayer(bhfwJson.features[0], name, {
      color: '#ff0000',
      fillColor: '#ff0000'
    });
  }

  if (data.jskzdzData || data.controlArea) {
    const jskzJson = JSON.parse(data.jskzdzData || data.controlArea);
    addAreaLayer(jskzJson.features[0], name, {
      color: '#16822f',
      fillColor: '#16822f'
    });
  }
}
//获取世界遗产数据
function fetchWorldHeritage() {
  heritageList({ pageSize: -1 }).then((res) => {
    if (res && res.data && res.data.list) {
      let data = res.data.list;

      data.forEach((item: any) => {
        composeList({ heritageId: item.id }).then((resp: any) => {
          if (resp && resp.data && resp.data.list) {
            let composeList = resp.data.list;
            composeList.forEach((composeItem: any) => {
              handleHeritageData(composeItem, item.name);
            });
          } else {
            handleHeritageData(item, item.name);
          }
        });
      });
    }
  });
}


onMounted(() => {
  fetchStat('');

});
// watch(
//   () => zoom.value,
//   (newValue, oldValue) => {
//     if (newValue < 7) {
//       searchShow.value = false;
//     }
//   },
// );

const areaList = toRef(useLeaflet.areaList);

/**
 * 位置点击
 * */
function handleClickLocation(item: any) {
  useLeaflet.updateAreaCode(item.gb);
  // handleClickGeoJson(item);
}

/**
 * geojson点击事件
 * */
function handleClickGeoJson(properties: any) {
  let areaCode = properties.gb;

  if (/^156/.test(areaCode)) {
    areaCode = areaCode.slice(3);
  }
  if (areaCode === '000000') {
    isStat.value = true;
    fetchStat('');
  } else if (areaCode.slice(-2) === '00') {
    isStat.value = true;
    fetchStat(areaCode);
  } else {
    isStat.value = false;
    fetchList(areaCode);
  }
}
</script>
<style lang="scss" scoped>
.container {
  width: 100%;
  height: 100%;
  // position: relative;
}

.logo {
  position: absolute;
  top: 5%;
  left: 50%;
  transform: translateX(-50%);
  z-index: 999;
}

.map-conatiner {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
}

.legend {
  width: 200px;
  position: absolute;
  bottom: 40px;
  right: 10px;
  z-index: 999;
}

.location-path {
  position: absolute;
  left: 40px;
  top: 40px;
  z-index: 999;
}

.location-path2 {
  margin: 10px;
}

.search {
  // position: absolute;
  // z-index: 999;
  // top:18%;
  // left: 3%;
}

.custom-marker-icon {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
}

.marker-icon-img {
  height: 30px;
}

.marker-title {
  min-width: 100px;
  background-color: #fff;
  height: 20px;
  font-size: 14px;
  text-align: center;
  line-height: 16px;
  position: relative;
  right: 129%;
  top: -51px;
  display: inline-flex;
  white-space: nowrap;
  align-items: center;
  justify-content: center;
  text-align: center;
  border-radius: 6px;
  //   border: 1px solid black;
}

.back-btn {
  position: absolute;
  z-index: 999;
  width: 50px;
  height: 40px;
  text-align: center;
  display: flex;
  justify-content: center;
  align-items: center;
  border-radius: 5px;
  left: 40px;
  top: 120px;
}

:deep(.n-drawer .n-drawer-content .n-drawer-content-native-scrollbar .n-drawer-body-content-wrapper) {
  overflow: block;
}
</style>
