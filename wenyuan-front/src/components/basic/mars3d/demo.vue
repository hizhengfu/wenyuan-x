<template>
  <HMars3d :center="center" :show-t-d-t="false" :show-earth="false" @onload="handleMapLoad"></HMars3d>
  <n-spin v-if="showSpin" size="large" class="loading">
  </n-spin>
  <div class="demo-operate">
    <n-input-group>
      <n-input-group-label>模型地址</n-input-group-label>
      <n-input v-model:value="tilesetUrl" placeholder="请输入模型地址"></n-input>
      <n-button type="primary" @click="handleLoadTileset">切换</n-button>
    </n-input-group>
  </div>
</template>
<script setup lang="ts">
import {HMars3d, useMars3dStore} from './index';
import * as mars3d from 'mars3d';
import {ref, markRaw} from 'vue'
import {notification} from '@/utils'

const useMars3d = useMars3dStore()

const center = {'lat': 39.90688, 'lng': 116.391544, 'alt': 97.9, 'heading': 354.5, 'pitch': -84.3}
const tilesetUrl = ref('http://116.226.248.15:8082/staticFiles/tiles/huangyanpei/tileset.json')
const showSpin = ref(false)

let tiles3dLayer: mars3d.layer.TilesetLayer | null = null

function handleLoadTileset() {
  if (!tilesetUrl.value) {
    notification({title: '地址不能为空', type: 'error'})
    return
  }
  if (tiles3dLayer) {
    useMars3d.removeTilesetLayer(tiles3dLayer)
  }
  tiles3dLayer = markRaw(new mars3d.layer.TilesetLayer({
    url: tilesetUrl.value,
    maximumScreenSpaceError: 16,
    cacheBytes: 1024 * 1024 * 4000,//2个g的显存
    maximumCacheOverflowBytes: 1024 * 1024 * 8000,
    marsJzwStyle: false,
    dynamicScreenSpaceError: true,
    cullWithChildrenBounds: false,
    skipLevelOfDetail: true,
    preferLeaves: true,
    clampToGround: true,
    style: {
      color: {
        conditions: [['true', 'color(\'rgba(255, 255, 255, 1)\')']]
      }
    },
    luminanceAtZenith: 0.5,
    highlight: {type: 'click', color: '#FF0000'},
    show: true
  }))
  showSpin.value = true
  tiles3dLayer.readyPromise.then((layer: mars3d.layer.TilesetLayer) => {
    showSpin.value = false
  })
  useMars3d.addTilesetLayer(tiles3dLayer)
}

function handleMapLoad() {
  if (tilesetUrl.value) {
    handleLoadTileset()
  }
}
</script>
<style lang="scss" scoped>
.demo-operate {
  position: absolute;
  top: 10px;
  left: 10px;
  z-index: 100;
  background-color: #ffffff;
}

.loading {
  position: absolute;
  left: 0;
  top: 0;
  width: 100%;
  height: 100%;
  z-index: 1000;
}
</style>
