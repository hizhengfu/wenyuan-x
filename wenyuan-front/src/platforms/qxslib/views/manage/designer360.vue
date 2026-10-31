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
      <h-krpano ref="krpanoRef" v-if="pathUrl" :xml-url="pathUrl" isDesign @ready="krpanoReady"
                @load:scene="handleLoadScene"
                @dblclick:hotspot="removeHotspot"
                @move:hotspot="moveHotspot"></h-krpano>
      <n-empty v-else description="暂无数据"></n-empty>
    </n-layout-content>
  </n-layout>
</template>
<script lang="ts" setup>
import HKrpano from '@/components/basic/krpano/krpano.vue'
import {addPoint, get, listPoint, updatePoint} from '../../api/layer'
import {ref, watchEffect} from "vue";
import {useRoute} from "vue-router";
import {Icon} from '@iconify/vue';

const route = useRoute()

const layerId = ref(route.query.layerId as string)
const pathUrl = ref()


const pointList = ref<any>([])

const deviceList = ref<any>([])
/**
 * 已经放置到地图上的点
 * */
const placeList = ref<any>([])
const sceneName = ref()

/**
 * 获取展厅信息
 * */
function fetchLayer(layerId: string) {
  get(layerId).then((res) => {
    if (res.data) {
      pathUrl.value = res.data.path
    }
  })
}

function fetchDeviceList(layerId: string) {
  listPoint(layerId, true).then((res) => {
    if (res.data) {
      pointList.value = res.data
      processPoint()
    }
  })
}

function processPoint() {
  pointList.value.forEach((item: any) => {
    if (item.id) {
      placeList.value.push(item)
    } else {
      deviceList.value.push(item)
    }
  })
}


watchEffect(() => {
  if (layerId.value) {
    fetchLayer(layerId.value)
    fetchDeviceList(layerId.value)
  }
})

const krpanoRef = ref()
const krpanoLoading = ref(false)

/**
 * 全景加载完毕
 * */
function krpanoReady(instance: any) {
  setTimeout(() => {
    krpanoLoading.value = true
  }, 1000)
}

watchEffect(() => {
  if (placeList.value.length > 0 && krpanoLoading.value && sceneName.value) {
    placeList.value.forEach((item: any) => {
      if (sceneName.value === item.scene) {
        krpanoRef.value.addHotspot(item.id, item.x, item.y, item.type === 0 ? 'camera.svg' : 'raster.svg', item.name)
      }
    })
  }
})

/**
 * 添加marker
 * */
function addMarker(item: any, index: number) {
  if (krpanoRef.value) {
    let sceneName = krpanoRef.value.getSceneName()
    addPoint({layerId: layerId.value, deviceId: item.deviceId, x: 0, y: 0, scene: sceneName}).then((res: any) => {
      krpanoRef.value.addHotspot(res.data, 0, 0, item.type === 0 ? 'camera.svg' : 'raster.svg', item.name)
    })
  }

  /*addPoint({layerId: layerId.value, deviceId: item.deviceId, x: 0, y: 0}).then((res: any) => {
    placeList.value.push({x: 0, y: 0, id: res.data, title: item.name})
    deviceList.value.splice(index, 1)
  })*/
}

function moveHotspot(id: string, ath: number, atv: number, scene: string | undefined) {
  updatePoint({id: id, x: ath, y: atv, scene: scene})
}

function removeHotspot(id: string) {
  console.log(22, id)
  krpanoRef.value.removeHotspot(id)
}

function handleLoadScene(scene: string) {
  sceneName.value = scene
  processPoint()
}
</script>
