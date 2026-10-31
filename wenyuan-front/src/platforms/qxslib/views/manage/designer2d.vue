<template>
  <n-layout has-sider>
    <n-layout-sider
      :width="240"
      bordered>
      <n-layout-header bordered>
        <div style="padding: 5px 15px">可选设备</div>
      </n-layout-header>
      <n-list bordered style="margin: 10px">
        <n-list-item
          draggable="true"
          v-for="(element,index) in deviceList" @click="addMarker(element,index)">
          {{ element.name }}
          <template #suffix>
            <n-button>
              <template #icon>
                <n-icon>
                  <Icon icon="mdi-plus"></Icon>
                </n-icon>
              </template>
            </n-button>
          </template>
        </n-list-item>
      </n-list>
      <n-empty v-if="deviceList.length===0" description="所有设备已经放置到地图上">
      </n-empty>
    </n-layout-sider>
    <n-layout-content>
      <leaflet-image
        ref="leafletContent"
        :url="imageUrl" :options="{zoomControl: false}" :min-zoom="-3" :max-zoom="5">
        <l-control-zoom position="bottomright"/>
        <l-marker v-for="(marker,index) in placeList"
                  :options="{id:marker.id}"
                  @moveend="moveMarker"
                  :lat-lng="[marker.x,marker.y]"
                  draggable>
          <l-tooltip>
            {{ marker.name }}
            <div>
              平面坐标：x:{{marker.x}},y:{{marker.y}}<br>
              场馆坐标：{{px2physics(marker.y,marker.x)}}
            </div>
          </l-tooltip>
          <l-popup :options="{closeButton:false}">
            <n-button @click="deleteMarker(marker.id,index)">
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
</template>
<script lang="ts" setup>
// @ts-ignore
import {LControlZoom, LMarker, LPopup, LTooltip} from '@vue-leaflet/vue-leaflet';
import {LeafletImage} from '@/components/basic/leaflet'
import {addPoint, get, listPoint, updatePoint, removePoint} from '../../api/layer'
import {list} from '../../api/device'
import {ref, watchEffect} from 'vue';
import {useRoute} from 'vue-router';
import {Icon} from '@iconify/vue';
import {px2physics} from '../../utils'

const route = useRoute()

const layerId = ref(route.query.layerId as string)
const imageUrl = ref()
const deviceList = ref<any>([])
/**
 * 已经放置到地图上的点
 * */
const placeList = ref<any>([])

/**
 * 获取展厅信息
 * */
function fetchLayer(layerId: string) {
  get(layerId).then((res) => {
    if (res.data) {
      imageUrl.value = res.data.path
    }
  })
}

function fetchDeviceList(layerId: string) {
  listPoint(layerId, true).then((res) => {
    if (res.data) {
      let list = res.data
      list.forEach((item: any) => {
        if (item.id) {
          placeList.value.push(item)
        } else {
          deviceList.value.push(item)
        }
      })
    }
  })
}

/**
 * 获取设备
 * */
function fetchDevice(rootId: string) {
  list({rootId: rootId}).then((res) => {
    if (res.data) {
      deviceList.value = res.data.list
    }
  })
}

watchEffect(() => {
  if (layerId.value) {
    fetchLayer(layerId.value)
    fetchDeviceList(layerId.value)
  }
})

const leafletContent = ref()

/**
 * 添加marker
 * */
function addMarker(item: any, index: number) {
  let center = leafletContent.value?.mapInstance?.getCenter()
  addPoint({layerId: layerId.value, deviceId: item.deviceId, x: center.lat, y: center.lng}).then((res: any) => {
    placeList.value.push({x: center.lat, y: center.lng, id: res.data, name: item.name})
    deviceList.value.splice(index, 1)
  })
}

/**
 * 删除点位
 * */
function deleteMarker(id: string, index: number) {
  removePoint(id).then(() => {
    let item = placeList.value.splice(index, 1)[0]
    deviceList.value.push(item)
  })
}

function moveMarker(item: any) {
  let option = item.target.options.options
  let latLng = item.target.getLatLng()
  let point = placeList.value.find((item: any) => {
    return item.id === option.id
  })
  point.x = latLng.lat
  point.y = latLng.lng
  updatePoint({id: option.id, x: latLng.lat, y: latLng.lng})
}
</script>
