<template>
  <n-layout has-sider>
    <n-layout-sider
      :width="500"
      bordered>
      <n-layout style="height: 100%">
        <n-layout-header bordered position="absolute" style="height: 64px">
          <div style="padding: 5px 15px">可选设备</div>
        </n-layout-header>
        <n-layout position="absolute" style="top:32px;bottom: 300px;">
          <n-list bordered style="margin: 10px;">
            <n-list-item
              v-for="(element,index) in optionalSceneList"
              draggable="true" @click="addMarker(element,index)">
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
          <n-empty v-if="optionalSceneList.length===0" description="所有拍摄位置已经放置到地图上">
          </n-empty>
        </n-layout>
        <n-layout-footer
          bordered
          position="absolute"
          style="height: 300px;"
        >
          <h-krpano
v-if="krpanoUrl"
                    ref="krpanoRef"
                    :xml-url="krpanoUrl"
                    @ready="krpanoReady"></h-krpano>
          <n-empty v-else description="暂无数据"></n-empty>
        </n-layout-footer>
      </n-layout>
    </n-layout-sider>
    <n-layout-content>
      <leaflet-image
        ref="leafletContent"
        :url="imageUrl" :options="{zoomControl: false}" :min-zoom="-3" :max-zoom="5">
        <l-control-zoom position="bottomright"/>
        <l-marker
v-for="(marker,index) in placeList"
                  :options="{id:marker.id}"
                  :lat-lng="[marker.x,marker.y]"
                  draggable
                  @moveend="moveMarker"
                  @click="clickMarker(marker)">
          <l-tooltip>
            {{ marker.name }}
            <div>
              x:{{ marker.x }},y:{{ marker.y }}
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
import {get as getRoom} from '../../api/room'
import {computed, ref, watchEffect} from 'vue';
import {useRoute} from 'vue-router';
import {Icon} from '@iconify/vue';
import HKrpano from '@/components/basic/krpano/krpano.vue'
import {nthArg} from 'lodash-es';
const route = useRoute()

const layerId = ref(route.query.layerId as string)
const krpanoUrl = ref()
const imageUrl = ref()
const krpanoRef = ref()
const sceneList = ref<any[]>([])

/**
 * 已经放置到地图上的点
 * */
const placeList = ref<any>([])

//可选场景列表
const optionalSceneList = computed<any>(() => {
  let temp: any[] = []
  sceneList.value.forEach((item: any) => {
    if (!placeList.value.find((p: any) => {
      return p.name === item.name
    })) {
      temp.push(item)
    }
  })
  return temp
})

/**
 * 获取展厅信息
 * */
function fetchRoom(roomId: string) {
  getRoom(roomId).then((res) => {
    if (res.data) {
      imageUrl.value = res.data.image
    }
  })
}

function fetchLayer(layerId: string) {
  get(layerId).then((res) => {
    if (res.data) {
      fetchRoom(res.data.roomId)
      krpanoUrl.value = res.data.path
    }
  })
}

function fetchPositionList(layerId: string) {
  listPoint(layerId, true).then((res) => {
    if (res.data) {
      let list = res.data
      list.forEach((item: any) => {
        if (item.id) {
          placeList.value.push(item)
        }
      })
    }
  })
}

watchEffect(() => {
  if (layerId.value) {
    fetchLayer(layerId.value)
    fetchPositionList(layerId.value)
  }
})

const leafletContent = ref()

/**
 * 添加marker
 * */
function addMarker(item: any, index: number) {
  let center = leafletContent.value?.mapInstance?.getCenter()
  addPoint({
    layerId: layerId.value,
    deviceId: item.name,
    scene: item.name,
    x: center.lat,
    y: center.lng
  }).then((res: any) => {
    placeList.value.push({x: center.lat, y: center.lng, id: res.data, name: item.name})
    /*let idx = sceneList.value.findIndex((item: any) => {
      return item.name === item.name
    })
    if (idx > -1) {
      sceneList.value.splice(idx, 1)
    }*/
  })
}

/**
 * 删除点位
 * */
function deleteMarker(id: string, index: number) {
  removePoint(id).then(() => {
    let item = placeList.value.splice(index, 1)[0]
    sceneList.value.push(item)
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

function clickMarker(item: any) {
  if (item && item.name) {
    krpanoRef.value?.loadScene(item.name)
  }
}

function krpanoReady(instance: any) {
  if (sceneList.value.length === 0) {
    let krpano = instance.get('global');
    let count = krpano.get('scene.count')
    for (let i = 0; i < count; i++) {
      let obj = krpano.get('scene[' + i + ']')
      // console.log(11, obj, obj.name)
      sceneList.value.push({name: obj.name, title: obj.title})
    }
  }
}
</script>
