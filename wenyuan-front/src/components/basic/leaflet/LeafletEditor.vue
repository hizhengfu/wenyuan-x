<template>
  <n-layout position="absolute">
    <n-layout-header style="height: 64px; padding: 24px;" bordered>
      <n-space justify="space-between">
        <n-space align="center">
          <n-icon size="22px" style="display: flex;justify-self: center;">
            <IconMdiViewCarousel/>
          </n-icon>
          <span style="font-size: 18px;font-weight: bold;">地图编辑器<i>（坐标系为WGS84）</i></span>
        </n-space>
        <n-form inline label-placement="left" :show-feedback="false">
          <n-form-item>
            <CopyExcelForm @update:value="handleExcelConvert"></CopyExcelForm>
          </n-form-item>
          <n-form-item label="">
            <n-upload :show-file-list="false" @change="handleLoadGeoJson">
              <n-button type="info">加载GeoJson</n-button>
            </n-upload>
          </n-form-item>
          <n-form-item>
            <n-input-group>
              <n-input-group-label>转换坐标</n-input-group-label>
              <n-select style="width: 160px;" v-model:value="coodTo" :options="coodOptions"
                        placeholder="请选择转换坐标系"/>
              <n-button type="success" @click="handleDownloadGeoJson">导出GeoJson</n-button>
              <n-button type="warning" @click="handleCopyGeoJson">复制GeoJson</n-button>
            </n-input-group>
          </n-form-item>
        </n-form>
      </n-space>
    </n-layout-header>
    <n-layout has-sider position="absolute" style="top: 64px; bottom: 32px;">
      <n-layout-sider bordered content-style="padding: 24px;">
        <n-form label-placement="left" label-align="right">
          <n-card :content-style="{ padding: '0' }" :segmented="{ content: true, action: true }">
            <template #header>
              大地2000坐标
            </template>
            <template #header-extra>
              <n-button circle @click="handleCreate">
                <template #icon>
                  <IconMdiPlus></IconMdiPlus>
                </template>
              </n-button>
            </template>
            <n-data-table
                size="small"
                :style="{ height: '200px' }"
                flex-height
                :single-line="false"
                :bordered="false"
                striped
                :columns="cgcs2000Columns"
                :data="cgcs2000Form.list">
            </n-data-table>
            <template #action>
              <n-space align="center" justify="center">
                <n-button type="success" @click="handleTransformGeoJson(cgcs2000Form.list)">实时查看</n-button>
              </n-space>
            </template>
          </n-card>
          <n-form-item class="mt-5" label="带号计算" label-placement="top" feedback="请输入经度值">
            <n-input-group>
              <n-input-number v-model:value="form.x" :show-button="false"></n-input-number>
              <n-input-group-label>
                {{ bandNumCalc(form.x) }}
              </n-input-group-label>
            </n-input-group>
          </n-form-item>
        </n-form>
      </n-layout-sider>
      <n-layout content-style="padding: 3px;">
        <LeafletTianditu :center="center" :zoom="zoom" @ready="handleMapReady">
          <l-control-layers position="topright"></l-control-layers>
          <l-control-zoom position="topleft"></l-control-zoom>
        </LeafletTianditu>
      </n-layout>
    </n-layout>
    <n-layout-footer bordered position="absolute" style="height: 32px;padding: 5px;text-align: center;">
      &copy; {{ appTitle }} 版权所有
    </n-layout-footer>
  </n-layout>
</template>
<script lang="ts" setup>
import L from 'leaflet'
//@ts-ignore
import {LMarker, LControlZoom, LControlLayers, LTooltip, LGeoJson} from '@vue-leaflet/vue-leaflet';
import LeafletTianditu from './LeafletTianditu.vue'
import CopyExcelForm from './CopyExcelForm.vue'
import {ref, h} from 'vue';
import {
  UploadFileInfo,
  DataTableColumns,
  NInput,
  NInputNumber,
  NButton,
  NIcon,
  NSpace
} from 'naive-ui';

import {saveAs} from 'file-saver'
import {notification} from '@/utils'
import {
  cGCS2000ToWgs84,
  xian80ToWgs84,
  wgs84ToBd09,
  wgs84ToGCJ02,
  polygonGeoJSON,
  geoJsonTransform,
  bandNumCalc
} from './coordinate'
import area from '@turf/area'
import Icon from '@/components/basic/DynamicIcon.vue'
import {useClipboard} from '@vueuse/core'

import 'leaflet-draw'
import 'leaflet-draw/dist/leaflet.draw.css'

const appTitle = APP_TITLE

//地图实例
const mapRef = ref()
const drawLayersRef = ref()

const center = ref([34.33, 108.96])
const zoom = ref(5)

const geoJsonRef = ref()
// 转换的坐标值
const coodTo = ref()
const coodOptions = [
  {label: 'WGS84To高德', value: 'GCJ02'},
  {label: 'WGS84To百度', value: 'BMap'},
  {label: '高德ToWGS84', value: 'GCJ02ToWGS84'},
  {label: '百度ToWGS84', value: 'BMapToWGS84'},
]

const form = ref({list: [{x: 0, y: 0}], x: 0})

const cgcs2000Columns: DataTableColumns<any> = [
  {
    title: 'X',
    key: 'x',
    align: 'center',
    render(row: any, index: number) {
      return h(NInputNumber, {
        value: row.x,
        size: 'tiny',
        showButton: false,
        placeholder: '请输入X坐标',
        onUpdateValue(v) {
          if (typeof v === 'number') {
            cgcs2000Form.value.list[index].x = v
          }
        }
      })
    }
  },
  {
    title: 'Y',
    key: 'y',
    align: 'center',
    render(row: any, index: number) {
      return h(NInputNumber, {
        value: row.y,
        size: 'tiny',
        showButton: false,
        placeholder: '请输入Y坐标',
        onUpdateValue(v) {
          if (typeof v === 'number') {
            cgcs2000Form.value.list[index].y = v
          }
        }
      })
    }
  },
  {
    title: 'O',
    key: 'O',
    align: 'center',
    fixed: 'right',
    width: '38px',
    render(row: any, index: number) {
      return h(
          NButton,
          {
            size: 'tiny',
            quaternary: true,
            renderIcon() {
              return h(Icon, {icon: 'mdi-delete'})
            },
            onClick() {
              cgcs2000Form.value.list.splice(index, 1)
            }
          }
      )
    }
  }
]


const cgcs2000Form = ref({
  list: [
    {x: 38639626.842, y: 2782934.726},
    {x: 38639714.760, y: 2782961.748},
    {x: 38639782.409, y: 2782898.470},
    {x: 38639760.743, y: 2782809.374},
    {x: 38639673.402, y: 2782782.436},
    {x: 38639606.147, y: 2782845.291}
  ]
})


let t1 = [
  ['496353.8380', '3156299.1170'],
  ['496328.6400', '3156367.0960'],
  ['496284.4650', '3156350.7570'],
  ['496313.0430', '3156279.9930']
]

let t2 = [
  ['496.336', '3156.375'],
  ['496.366', '3156.294'],
  ['496.279', '3156.354'],
]

console.log(JSON.stringify(t1.map((item) => {
  return [parseFloat('38' + item[0]), parseFloat(item[1])];
})))


cgcs2000Form.value.list = t1.map((item) => {
  return {x: parseFloat('39' + item[0]), y: parseFloat(item[1])}
})


function handleExcelConvert(list: { x: number, y: number }[]) {
  cgcs2000Form.value.list = list;
}


function handleCreate() {
  cgcs2000Form.value.list.push({x: 0, y: 0})
}


// let hcq: [number, number][] = [
//   [38639626.842, 2782934.726],
//   [38639714.760, 2782961.748],
//   [38639782.409, 2782898.470],
//   [38639760.743, 2782809.374],
//   [38639673.402, 2782782.436],
//   [38639606.147, 2782845.291]
// ]
//
// let bhq: [number, number][] = [
//   [38639643.730, 2782918.993],
//   [38639709.560, 2782939.226],
//   [38639760.215, 2782891.845],
//   [38639743.994, 2782825.138],
//   [38639678.587, 2782804.965],
//   [38639628.234, 2782852.024]
// ]

// let wgs84 = cGCS2000ToWgs84(38639626.842, 2782934.726)
//
// console.log('wgs84', wgs84)
// console.log('bd09', wgs84ToBd09(...wgs84))
// console.log('gcj02', wgs84ToGCJ02(...wgs84))
//
// // let json = polygonGeoJSON([hcq.map((item) => cGCS2000ToWgs84(...item)), bhq.map((item) => cGCS2000ToWgs84(...item))])
// let json = polygonGeoJSON([hcq.map((item) => cGCS2000ToWgs84(...item))])
// console.log('json', JSON.stringify(json))

/**
 * 地图加载完成事件
 * */
function handleMapReady(instance: any) {
  mapRef.value = instance

  let editableLayers = new L.FeatureGroup();
  instance.addLayer(editableLayers);

  drawLayersRef.value = editableLayers

  let options = {
    position: 'topleft',
    draw: {
      polyline: {title: '测试'}
    },
    edit: {
      featureGroup: editableLayers, //REQUIRED!!
      remove: true
    }
  };

  // @ts-ignore
  let drawControl = new L.Control.Draw(options);
  instance.addControl(drawControl);

  // @ts-ignore
  instance.on(L.Draw.Event.CREATED, function (event) {
    let layer = event.layer;
    editableLayers.addLayer(layer);
  });
}


/**
 * 转换为geojson文件
 * */
function handleTransformGeoJson(arr: { x: number, y: number }[],
                                style = {
                                  weight: 2,
                                  color: 'red',
                                  fillColor: 'red',
                                }, isClear: boolean = true,
) {
  let geoJson = polygonGeoJSON([arr.map((item) => cGCS2000ToWgs84(item.x, item.y))])
  if (isClear) {
    //清除所有的图层
    drawLayersRef.value.clearLayers()
  }

  //@ts-ignore
  let geojsonLayer = L.geoJSON(geoJson, {
    style: style,
    onEachFeature: (feature, layer: any) => {
      let center = layer.getBounds().getCenter()

      let tooltip = `中心经纬度：${center}<br/>面积：${area(feature) / 1000000}平方公里`

      // drawLayersRef.value.addLayer(layer)
      layer.bindTooltip(tooltip)
      // layer.bindPopup(`面积：${area(feature) / 1000000}平方公里`)
    }
  })
  drawLayersRef.value.addLayer(geojsonLayer)
  // let geojsonLayer = L.geoJSON(geojson).addTo(mapRef.value)
  //移动位置到geojson图层
  mapRef.value.fitBounds(geojsonLayer.getBounds());
}

/**
 * 加载本地geojson文件
 * */
function handleLoadGeoJson(options: { fileList: UploadFileInfo[], file: UploadFileInfo; }) {
  let reader = new FileReader()
  reader.onload = (event: ProgressEvent<FileReader>) => {
    //读取文件并解析为json
    //@ts-ignore
    let geojson = JSON.parse(event.target?.result as string)
    if (geojson) {
      //加载geojson到地图
      let geojsonLayer = L.geoJSON(geojson, {
        style: {
          weight: 2,
          color: 'red',
          fillColor: 'red',
        },
        onEachFeature: (feature, layer: any) => {
          // drawLayersRef.value.addLayer(layer)
          let center = layer.getBounds().getCenter()

          let tooltip = `中心经纬度：${center}<br/>面积：${area(feature) / 1000000}平方公里`
          // layer.bindPopup(`面积：${area(feature) / 1000000}平方公里`)
        }
      })
      drawLayersRef.value.addLayer(geojsonLayer)
      // let geojsonLayer = L.geoJSON(geojson).addTo(mapRef.value)
      //移动位置到geojson图层
      mapRef.value.fitBounds(geojsonLayer.getBounds());
    }
  }
  reader.readAsText(options.file.file as Blob, 'utf-8')
}

/**
 * 下载geojson文件
 * */
function handleDownloadGeoJson() {
  if (drawLayersRef.value) {
    let geojson = drawLayersRef.value.toGeoJSON()
    geoJsonTransform(geojson, coodTo.value)
    let file = new Blob([JSON.stringify(geojson)], {
      type: 'text/plain;charset=utf-8'
    })
    saveAs(file, `geojson-${new Date().getSeconds()}.geojson`)
  } else {
    notification({title: '无编辑图层', type: 'error'})
  }
}

const {copy} = useClipboard({legacy: true})

/**
 * 复制geojson文件
 * */
function handleCopyGeoJson() {
  if (drawLayersRef.value) {
    let geojson = drawLayersRef.value.toGeoJSON()
    geoJsonTransform(geojson, coodTo.value)
    copy(JSON.stringify(geojson)).then(() => {
      notification({title: '复制成功', type: 'success'})
    }).catch(() => {
      notification({title: '复制失败', type: 'error'})
    })
  } else {
    notification({title: '无编辑图层', type: 'error'})
  }
}
</script>
