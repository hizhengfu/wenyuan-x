<template>
  <h-krpano v-if="xmlUrl" :xml-url="xmlUrl" ref="krpanoRef"
            @click:hotspot="handleClickHotspot"
            @load:scene="handleLoadScene"
            @click:scene="handleClickScene"
            @ready="krpanoReady"></h-krpano>
  <div v-else class="flex-center">
    <n-empty description="暂无数据"></n-empty>
  </div>
</template>
<script lang="ts" setup>
import HKrpano from '@/components/basic/krpano/krpano.vue'
import {computed, ref, watchEffect} from "vue";
import {get, listPoint} from "@/platforms/qxslib/api/layer";
import {useEventPointStore} from "@/platforms/qxslib/store/visualOperate";
import {useRoomStatusStore} from "@/platforms/qxslib/store/roomStatus";

const layerId = '1558412292580016128'

const listMarker = ref([])
const xmlUrl = ref()
const sceneName = ref()

function fetchData(layerId: string) {
  listPoint(layerId).then((res) => {
    listMarker.value = res.data || []
  })
}

/**
 * 获取图层信息
 * */
function fetchLayer(layerId: string) {
  get(layerId).then((res) => {
    if (res.data) {
      xmlUrl.value = res.data.path
      fetchData(layerId)
    }
  })
}

fetchLayer(layerId)

const krpanoRef = ref()
const krpanoLoading = ref(false)

/**
 * 全景加载完毕
 * */
function krpanoReady(instance: any) {
  setTimeout(() => {
    krpanoLoading.value = true
  }, 500)
}

watchEffect(() => {
  if (listMarker.value.length > 0 && krpanoLoading.value && sceneName.value) {
    listMarker.value.forEach((item: any) => {
      if (sceneName.value === item.scene) {
        let title = item.name
        let t = roomStatusStore.raster?.find((it) => {
          return it.raster_id === item.addr
        })
        if (t) {
          item.status = t.raster_state?.state_msg || '--'
          item.dateTime = t.raster_state?.created
          if (t.raster_state && t.raster_state.state === '302') {
            item.alarm = true
          } else {
            item.alarm = false
          }
          title = `${title}「${item.status}」`
        }

        if (item.alarm) {
          krpanoRef.value.addHotspot(item.id, item.x, item.y, item.type === 0 ? 'camera.svg' : 'raster-red.svg', title)
        } else {
          krpanoRef.value.addHotspot(item.id, item.x, item.y, item.type === 0 ? 'camera.svg' : 'raster.svg', title)
        }
      }
    })
  }
})

/**
 * 点击热点
 * */
function handleClickHotspot(id: string) {
  console.log('你点击了我', id)
}

/**
 * 场景点击事件
 * */
const eventPoint = useEventPointStore()

function handleClickScene(event: any) {
  eventPoint.type = 'PANORAMA'
  eventPoint.scene = event.scene
  eventPoint.pagePoint = {x: event.pagePoint.x, y: event.pagePoint.y + 72}
  //地图实际位置
  eventPoint.mapPoint = {x: event.mapPoint.x, y: event.mapPoint.y}
}

/**
 * 场景加载事件
 * */
function handleLoadScene(scene: string) {
  sceneName.value = scene
  eventPoint.type = 'PANORAMA'
  eventPoint.scene = scene
  eventPoint.pagePoint = undefined
  eventPoint.mapPoint = undefined
}

const roomStatusStore = useRoomStatusStore()

const robotList = computed(() => {
  return roomStatusStore.robot?.filter((item) => {
    return item.position
  })
})
</script>
