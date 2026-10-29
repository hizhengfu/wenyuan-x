<template>
  <div :id="id" style="position: absolute;top: 0;bottom: 0;right: 0;left: 0"></div>
</template>
<script lang="ts" setup>
import {transformToTreeFormat, uuid} from '@/utils'
import {useScriptTag} from '@vueuse/core'
import {onUnmounted, ref, watchEffect} from 'vue';

const emits = defineEmits(['ready', 'move:hotspot', 'click:hotspot', 'dblclick:hotspot', 'load:scene', 'click:scene'])
const props = defineProps({
  xmlUrl: {type: String, required: true},
  isDesign: {type: Boolean, default: false},
})
/**
 * 容器id
 * */
const id = 'pano-' + uuid()
const scriptLoad = ref(false)
const krpanoInstance = ref()

/**
 * 加载krpano文件
 * */
useScriptTag(
  './tour.js',
  (el: HTMLScriptElement) => {
    scriptLoad.value = true
  },
)
/**
 * 脚本与xml文件都存在即开始加载全景内容
 * */
watchEffect(() => {
  if (scriptLoad.value && props.xmlUrl) {
    createPano()
  }
})

onUnmounted(() => {
  window.removepano && window.removepano(id)
})

/**
 * 创建全景图
 * */
function createPano() {
  window.embedpano({
    xml: props.xmlUrl,
    target: id,
    html5: 'only',
    mobilescale: 1.0,
    onready: function (instance: any) {
      krpanoInstance.value = instance

      let krpano = krpanoInstance.value.get('global');
      /**
       * 绑定场景点击事件
       * */
      krpano.events.onclick = function () {
        let mx = krpano.get('mouse.x');
        let my = krpano.get('mouse.y');
        let pnt = krpano.screentosphere(mx, my);
        let h = pnt.x;
        let v = pnt.y;
        emits('click:scene', {
          scene: krpano.get('xml.scene'),
          pagePoint: {x: mx, y: my},
          mapPoint: {x: pnt.x, y: pnt.y}
        })
      }

      /**
       * 场景加载完成事件
       * */
      krpano.events.onloadcomplete = function () {
        let krpano = krpanoInstance.value.get('global');
        emits('load:scene', krpano.get('xml.scene'))
        // getAllScene()
        emits('ready', instance)
      }
    }
  });
}

/**
 * 添加点位
 * */
function addHotspot(id: string, h: number, v: number, image: string, title: string) {
  let krpano = krpanoInstance.value.get('global');
  if (krpano) {
    let hs = krpano.get('hotspot[id-' + id + ']')
    if (hs) {
      removeHotspot(id)
    }
    hs = krpano.addhotspot('id-' + id)
    hs.url = image
    hs.width = 32
    hs.height = 'prop'
    hs.ath = h
    hs.atv = v
    hs.polyline = true
    hs.distorted = true
    hs.tooltip = title

    hs.onloaded = 'add_all_the_time_tooltip()'
    hs.onclick = function (hs: string) {
      emits('click:hotspot', id)
    }

    if (props.isDesign) {
      hs.ondoubleclick = function (hs: string) {
        console.log(123, hs)
        emits('dblclick:hotspot', id)
      }

      hs.dragging = true
      hs.ondown = 'draghotspot()'
      hs.onup = function () {
        emits('move:hotspot', id, hs.ath, hs.atv, krpano.get('xml.scene'))
      }
    }
  }
}

/**
 * 更新位置点
 * */
function updateHotspot(id: string, content: string) {
  let krpano = krpanoInstance.value.get('global');
  if (krpano) {
    let hs = krpano.get('id-' + id)
    if (hs) {
      hs.tooltip = content
    }
  }
}

function addTextHotspot(id: string, h: number, v: number, html: string) {
  let krpano = krpanoInstance.value.get('global');
  if (krpano) {
    let hs = krpano.addhotspot('id-text-' + id)
    hs.type = 'text'
    hs.ath = h
    hs.atv = v - 5
    hs.edge = 'center'
    hs.distorted = 'true'
    hs.html = html
    if (props.isDesign) {
      hs.dragging = true
      hs.ondown = 'draghotspot()'
    }
  }
}


/**
 * 删除坐标点
 * */
function removeHotspot(id: string) {
  let krpano = krpanoInstance.value.get('global');
  if (krpano) {
    krpano.call('removeHotspot(id-' + id + ')');
  }
}

/**
 * 添加图层
 * */
function addLayer() {

}

/**
 * 获取场景名称
 * */
function getSceneName(): string {
  let krpano = krpanoInstance.value.get('global');
  return krpano?.get('xml.scene')
}

function getAllScene() {
  let krpano = krpanoInstance.value.get('global');
  let count = krpano.get('scene.count')
  for (let i = 0; i < count; i++) {
    let obj = krpano.get('scene[' + i + ']')
    console.log(11, obj)
  }
}

function loadScene(sceneName: string) {
  let krpano = krpanoInstance.value.get('global');
  // krpano.call("loadpano(" + sceneName + ")");
  if (krpano) {
    krpano.actions.loadscene(sceneName)
  }
}

defineExpose({addHotspot, removeHotspot, getSceneName, getAllScene, loadScene})
</script>
