<template>
  <n-button type="primary" @click="show=true">
    选择坐标
  </n-button>
  <n-modal v-model:show="show">
    <n-card
      style="width: 800px;height: 600px"
      title="选择坐标"
      role="dialog"
      :segmented="{content: 'soft',footer: 'soft'}"
      aria-modal="true">
      <template #header-extra><span v-if="marker">当前坐标：</span>{{ marker }}</template>
      <leaflet-image v-bind="$attrs"
                     :min-zoom="-3" :max-zoom="5"
                     :url="imageUrl" @click="handleClick">
        <l-marker v-if="marker"
                  :lat-lng="marker"
                  @moveend="moveMarker"
                  draggable>
          <l-tooltip>拖动改变位置</l-tooltip>
        </l-marker>
      </leaflet-image>
      <template #footer>
        <n-space justify="space-around">
          <n-button type="success" @click="handleSubmit">点击确定</n-button>
        </n-space>
      </template>
    </n-card>
  </n-modal>
</template>
<script lang="ts" setup>
// @ts-ignore
import {LMarker, LTooltip} from "@vue-leaflet/vue-leaflet";
import LeafletImage from './LeafletImage.vue'
import {ref, watchEffect} from "vue";
import {isNumber} from 'lodash-es'

const emits = defineEmits(['update:value'])

const props = defineProps({
  imageUrl: {type: String},
  x: {type: Number},
  y: {type: Number},
})

const show = ref(false)

const marker = ref()

watchEffect(() => {
  if (isNumber(props.x) && isNumber(props.y)) {
    marker.value = [props.x, props.y]
  }
})

/**
 * 第一次根据点击点位置创建
 * */
function handleClick(event: any) {
  if (!marker.value && event.latlng) {
    marker.value = [event.latlng.lat, event.latlng.lng]
  }
}

/**
 * 移动
 * */
function moveMarker(event: any) {
  let latLng = event.target.getLatLng()
  marker.value = [latLng.lat, latLng.lng]
}

/**
 * 提交事件
 * */
function handleSubmit() {
  show.value = false
  if (marker.value) {
    emits('update:value', marker.value)
  }
}

</script>
