<template>
  <n-card class="h-full" :content-style="{ padding: 0 }">
    <n-layout class="h-full" has-sider>
      <n-layout-sider :width="240" bordered>
        <n-layout-header bordered>
          <div style="padding: 5px 15px">「{{ layerName }}」可选设备</div>
        </n-layout-header>
        <n-layout-content position="absolute" style="top: 32px">
          <n-input-group>
            <n-input v-model:value="keywords" placeholder="请输入快速检索"></n-input>
          </n-input-group>
          <n-list bordered style="margin: 10px">
            <n-list-item draggable="true" v-for="element in filterResourcesList" @click="addMarker(element)">
              {{ element.name }}
              <template #suffix>
                <n-button>
                  <template #icon>
                    <n-icon>
                      <HIcon icon="c:plus"></HIcon>
                    </n-icon>
                  </template>
                </n-button>
              </template>
            </n-list-item>
          </n-list>
          <n-empty v-if="deviceList.length === 0" description="所有设备已经放置到地图上"> </n-empty>
        </n-layout-content>
      </n-layout-sider>
      <n-layout-content v-if="loading">
        <leaflet-image
          ref="leafletContent"
          v-if="imageUrl"
          :url="imageUrl"
          :options="{ zoomControl: false }"
          :min-zoom="-3"
          :max-zoom="5"
        >
          <l-control-zoom position="bottomright" />
          <l-marker
            v-for="(marker, index) in placeList"
            :options="{ id: marker.id }"
            @moveend="moveMarker"
            :lat-lng="[marker.x, marker.y]"
            draggable
          >
            <l-tooltip>
              {{ marker.name }}
              <div>坐标：x:{{ marker.x }},y:{{ marker.y }}<br /></div>
            </l-tooltip>
            <l-popup :options="{ closeButton: false }">
              <n-button @click="deleteMarker(marker.id)">
                <template #icon>
                  <n-icon>
                    <Icon icon="mdi:delete"></Icon>
                  </n-icon>
                </template>
                点击删除
              </n-button>
            </l-popup>
          </l-marker>
        </leaflet-image>
      </n-layout-content>
    </n-layout>
  </n-card>
</template>
<script lang="ts" setup>
// @ts-ignore
import { LControlZoom, LMarker, LPopup, LTooltip } from '@vue-leaflet/vue-leaflet';
import { LeafletImage } from '@/components/basic/leaflet';
import { addPoint, get, listPoint, updatePoint, removePoint } from '../../api/layer';
import { list } from '../../api/device';
import { computed, ref, watch, watchEffect } from 'vue';
import { useRoute } from 'vue-router';
import Icon from '@/components/basic/DynamicIcon.vue';

const route = useRoute();

const layerId = ref(route.query.layerId as string);
const loading = ref(false);
const layerName = ref();
const imageUrl = ref();
const deviceList = ref<any>([]);

/**
 * 快速检索关键字
 * */
const keywords = ref();
/**
 * 已经放置到地图上的点
 * */
const placeList = ref<any>([]);

/**
 * 过滤资源数据
 * */
const filterResourcesList = computed(() => {
  if (keywords.value) {
    return deviceList.value.filter((item: any) => {
      return item.name.toLowerCase().indexOf(keywords.value.toLowerCase()) > -1;
    });
  } else {
    return deviceList.value;
  }
});

/**
 * 获取展厅信息
 * */
function fetchLayer(layerId: string) {
  loading.value = false;
  get(layerId).then((res) => {
    if (res.data) {
      layerName.value = res.data.name;
      imageUrl.value = res.data.path;
      loading.value = true;
    }
  });
}

function fetchDeviceList(layerId: string) {
  listPoint(layerId, true).then((res) => {
    if (res.data) {
      let list = res.data;
      list.forEach((item: any) => {
        if (item.id) {
          placeList.value.push(item);
        } else {
          deviceList.value.push(item);
        }
      });
    }
  });
}

/**
 * 获取设备
 * */
function fetchDevice(rootId: string) {
  list({ rootId: rootId }).then((res) => {
    if (res.data) {
      deviceList.value = res.data.list;
    }
  });
}

// watchEffect(() => {
//
//   if (layerId.value) {
//     fetchLayer(layerId.value)
//     fetchDeviceList(layerId.value)
//   }
// })

const leafletContent = ref();

/**
 * 添加marker
 * */
function addMarker(item: any) {
  let center = leafletContent.value?.mapInstance?.getCenter();
  addPoint({ layerId: layerId.value, deviceId: item.deviceId, x: center.lat, y: center.lng }).then((res: any) => {
    placeList.value.push({ x: center.lat, y: center.lng, id: res.data, name: item.name });
    const deviceIndex = deviceList.value.findIndex((device: any) => {
      return device.deviceId === item.deviceId || device.id === item.id;
    });
    if (deviceIndex !== -1) {
      deviceList.value.splice(deviceIndex, 1);
    }
  });
}

/**
 * 删除点位
 * */
function deleteMarker(id: string) {
  removePoint(id).then(() => {
    const markerIndex = placeList.value.findIndex((item: any) => item.id === id);
    if (markerIndex === -1) return;

    let item = placeList.value.splice(markerIndex, 1)[0];
    deviceList.value.push(item);
  });
}

/**
 * 移动点位
 * */
function moveMarker(item: any) {
  let option = item.target.options;
  let latLng = item.target.getLatLng();
  let point = placeList.value.find((item: any) => {
    return item.id === option.id;
  });
  point.x = latLng.lat;
  point.y = latLng.lng;
  updatePoint({ id: option.id, x: latLng.lat, y: latLng.lng });
}

watch(
  () => route.query.layerId,
  (newValue, oldValue) => {
    if (newValue) {
      layerId.value = newValue as string;
      fetchLayer(layerId.value);
      fetchDeviceList(layerId.value);
    }
  },
  { immediate: true },
);
</script>
