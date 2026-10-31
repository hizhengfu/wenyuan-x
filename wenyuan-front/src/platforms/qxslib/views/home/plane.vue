<template>
  <h-leaflet-image
    :url="imageUrl"
    @ready="handleReady"
    @click="handleMapClick"
    :min-zoom="-3" :max-zoom="5">
    <l-marker v-for="marker in cListMarker"
              :options="{id:marker.id}"
              :lat-lng="[marker.x,marker.y]">
      <l-icon :icon-url="marker.type===0?cameraMarker:(marker.alarm?rasterRedMarker:rasterMarker)"></l-icon>
      <l-tooltip :options="{closeButton:false,keepInView:true}">
        <n-thing>
          <template #header>
            {{ marker.name }}
          </template>
          <template #description v-if="marker.type!==0">
            最新状态：{{ marker.status || '--' }}<br>
            <span v-if="marker.dateTime">
              更新时间：{{ fromTimeNow(marker.dateTime) }}
            </span>
          </template>
        </n-thing>
      </l-tooltip>
    </l-marker>
    <l-marker v-for="(marker,index) in peopleList"
              :lat-lng="physics2px(marker.x,marker.y)">
      <l-icon :icon-url="peopleMarker"></l-icon>
      <l-tooltip>
        <div class="title">游客#{{ index }}</div>
      </l-tooltip>
    </l-marker>
    <l-marker v-for="robot in robotList" :lat-lng="physics2px(robot.position.x,robot.position.y)">
      <l-icon :icon-url="robotMarker"></l-icon>
      <l-tooltip>
        <div class="title">编号：{{ robot.robot_sn }} x:{{ robot.position.x }},y:{{ robot.position.y }}</div>
        <div>状态：{{ robot.state?.state_msg || '--' }}</div>
        <div>电量：{{ robot.battery && (robot.battery?.value + '%') || '--' }}</div>
        <div>更新时间：{{ fromTimeNow(robot.created) }}</div>
        <template v-if="robot.task_list?.length>0">
          <div>待执行任务：{{ robot.task_list?.length || '0' }}</div>
          <n-table size="small">
            <thead>
            <tr>
              <th>任务状态</th>
              <th>计划执行时间</th>
            </tr>
            </thead>
            <tbody>
            <tr v-for="task in robot.task_list">
              <td>{{ stateMsg(task.task_state) }}({{ task.task_state }})</td>
              <td>{{ formatDateTime(task.task_time) }}</td>
            </tr>
            </tbody>
          </n-table>
        </template>
      </l-tooltip>
    </l-marker>
    <l-polyline v-for="(key,index) in cachePoint.keys()"
                :lat-lngs="cachePoint.get(key)"
                :weight="5"
                color="green">
      <l-tooltip>{{ key }}</l-tooltip>
    </l-polyline>
  </h-leaflet-image>
</template>
<script lang="ts" setup>
import HLeafletImage from '@/components/basic/leaflet/LeafletImage.vue'
//@ts-ignore
import {LIcon, LMarker, LPolyline, LTooltip} from "@vue-leaflet/vue-leaflet";
import {computed, ref} from "vue";
import {get, listPoint} from '../../api/layer'
import robotMarker from '../../assets/image/robot-marker.png'
import cameraMarker from '../../assets/image/camera-marker.png'
import rasterMarker from '../../assets/image/raster-marker.png'
import peopleMarker from '../../assets/image/people-marker.png'

import rasterRedMarker from '../../assets/image/raster-red-marker.png'
import {useEventPointStore} from '../../store/visualOperate'
import {useRoomStatusStore} from '../../store/roomStatus'
import {physics2px} from '../../utils'
import {formatDateTime, fromTimeNow} from '@/utils'
// @ts-ignore
// import x from 'leaflet-heatmap'

const props = defineProps({
  zoom: {
    type: Number,
    default: 1
  }
})
const layerId = '1557520203656261632'

const listMarker = ref<any>([])
const imageUrl = ref()

const roomStatusStore = useRoomStatusStore()


const cachePoint = ref(new Map())

const robotList = computed(() => {
  return roomStatusStore.robot?.filter((item) => {
    // console.log(111, item, cachePoint.value)
    if (!cachePoint.value?.has(item.robot_sn)) {
      cachePoint.value?.set(item.robot_sn, [])
    }
    let temp = cachePoint.value?.get(item.robot_sn)
    if (temp) {
      if (temp.length > 50) {
        temp.shift()
      }
      let p = physics2px(item.position.x, item.position.y)
      if (temp.length > 0) {
        if (temp[temp.length - 1] != p) {
          temp.push(p)
        }
      } else {
        temp.push(p)
      }
    }
    return item.position
  })
})

const peopleList = computed(() => {
  return roomStatusStore.people
})


const msg = {
  700: '任务异常',
  701: '等待执行',
  702: '任务暂停',
  703: '正在执行',
  704: '执行完成',
  705: '任务取消',
}

function stateMsg(code: number): string {
  // @ts-ignore
  return msg[code] || '--'
}


/**
 * 根据返回状态信息获取设备状态
 * */
function getDeviceStatus(type: number, addr: string) {
  if (type === 1) {
    return roomStatusStore.raster?.find((item) => {
      return item.raster_id === addr
    })
  }
  return null
}

/**
 * 地图点击事件
 * */
const eventPointStore = useEventPointStore()

function handleMapClick(event: any) {
  if (event.latlng) {
    eventPointStore.type = 'PLANE'
    // eventPoint.pagePoint = {x: event.containerPoint.x, y: event.containerPoint.y}
    //页面点击位置
    eventPointStore.pagePoint = {x: event.originalEvent.clientX, y: event.originalEvent.clientY}
    //地图实际位置
    eventPointStore.mapPoint = {y: event.latlng.lat, x: event.latlng.lng}
  }
}

function handleReady(instance: any) {
}

function fetchData(layerId: string) {
  listPoint(layerId).then((res) => {
    listMarker.value = res.data || []
  })
}

/**
 * 处理设备状态
 * */
const cListMarker = computed(() => {
  return listMarker.value.map((item: any) => {
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
    }
    return item
  })
})


/**
 * 获取图层信息
 * */
function fetchLayer(layerId: string) {
  get(layerId).then((res) => {
    if (res.data) {
      imageUrl.value = res.data.path
      fetchData(layerId)
    }
  })
}

fetchLayer(layerId)
</script>
