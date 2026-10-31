<template>
  <div :id="mars3dId" class="mars3d-container"></div>
</template>
<script setup lang="ts">
import {markRaw, nextTick, onMounted, onUnmounted, watchEffect} from 'vue'
import * as mars3d from 'mars3d'
import * as Cesium from 'mars3d-cesium'
import 'mars3d/mars3d.css'
import 'mars3d-cesium/Build/Cesium/Widgets/widgets.css';
import {uuid} from '@/utils/uuid'
import {useMars3dStore} from './useMars3d'
import './style.css'

// const Cesium = mars3d.Cesium

// if (!ENABLE_MARS3D) {
//   throw Error('请在config配置文件中设置enable_mars3d为true')
// }

const useMars3d = useMars3dStore()

const props = defineProps({
  autoRotate: {type: Boolean, default: false},
  token: {type: String, default: '6a555e89f2a149a3d2158b3210f348c3'},
  showTDT: {type: Boolean, default: true},//是否显示天地图
  showEarth: {type: Boolean, default: true},//是否显示地球
  showContextmenu: {type: Boolean, default: true},//是否默认右键菜单
  center: {type: Object}
})

// 用于存放地球组件实例
let mapInstance: mars3d.Map | null // 地图对象
let rotatePoint: mars3d.thing.RotatePoint | null // 地图旋转

// 使用用户传入的 mapKey 拼接生成 withKeyId 作为当前显示容器的id
const mars3dId = `mars3d-container-${uuid()}`

onMounted(() => {
  const {token, showTDT, showEarth, showContextmenu} = props

  let options = {
    fixedLight: true,//固定光照
    scene: {
      shadows: false, //是否启用日照阴影
      showSun: false,//是否显示太阳
      showMoon: false,//是否显示月亮
      showSkyBox: false,//是否显示天空盒
      showSkyAtmosphere: false, //是否显示地球大气层外光圈
      loadSiblings: true,
      dynamicScreenSpaceError: true,
      sceneMode: 3,//3等价于Cesium.SceneMode.SCENE3D,
      requestRenderMode: true,//启用性能较好
      preferLeaves: true,
      fog: true,//是否启用雾化效果
      fxaa: true,//是否启用抗锯齿
      highDynamicRange: true,//是否关闭高动态范围渲染(不关闭时地图会变暗)
      globe: {
        show: showEarth,//是否显示地球
        depthTestAgainstTerrain: true, //是否启用深度监测
        showGroundAtmosphere: true, //是否在地球上绘制的地面大气
        enableLighting: false, //是否显示昼夜区域
        baseColor: '#1C3020'
      },
      cameraController: {
        zoomFactor: 3.0,//鼠标滚轮放大的步长参数
        minimumZoomDistance: 1,//地球放大的最小值（以米为单位）
        maximumZoomDistance: 50000000,//地球缩小的最大值（以米为单位）
        enableTranslate: true, //2D和哥伦布视图下，是否允许用户平移地图
        enableTilt: true, // 3D和哥伦布视图下，是否允许用户倾斜相机
        enableZoom: true, // 是否允许 用户放大和缩小视图
        enableCollisionDetection: true //是否允许 地形相机的碰撞检测
      },
      contextOptions: {
        webgl: {
          alpha: true,//是否透明
          preserveDrawingBuffer: true
        }
      },
      mapProjection: mars3d.CRS.EPSG3857, // 2D下展示墨卡托投影
    },
    token: {
      tianditu: token,
    },
    control: {
      zoom: true,
      baseLayerPicker: false, // basemaps底图切换按钮
      homeButton: false, // 视角复位按钮
      sceneModePicker: false, // 二三维切换按钮
      navigationHelpButton: false, // 帮助按钮
      fullscreenButton: false, // 全屏按钮
      compass: {bottom: '3px', left: '5px'}, //指南针
      contextmenu: {hasDefault: showContextmenu} // 右键菜单
    },
    // terrain: {
    //   type: 'tdt',
    //   url: 'https://t{s}.tianditu.gov.cn/mapservice/swdx',
    //   key: props.token,
    //   subdomains: '01234567',
    //   show: true,
    // },
    basemaps: []
  }
  if (showTDT) {
    options.basemaps = [
      //@ts-ignore
      {
        name: '天地图影像',
        // icon: 'img/basemaps/tdt_img.png',
        type: 'tdt',
        layer: 'img_d',
        show: true
      }
    ]
  }
  // 获取配置
  nextTick(() => {
    initMars3d(markRaw(options))
  })
})

onUnmounted(() => {
  if (mapInstance) {
    mapInstance.destroy()
    mapInstance = null
  }
})

// onload事件将在地图渲染后触发
const emit = defineEmits(['onload'])
const initMars3d = (option: any) => {
  mapInstance = markRaw(new mars3d.Map(mars3dId, option))
  useMars3d.mars3dInit(mapInstance)

  // 开场动画
  // mapInstance.openFlyAnimation();

  // 针对不同终端的优化配置
  if (mars3d.Util.isPCBroswer()) {
    mapInstance.zoomFactor = 2.0 // 鼠标滚轮放大的步长参数

    // IE浏览器优化
    if (window.navigator.userAgent.toLowerCase().indexOf('msie') >= 0) {
      mapInstance.viewer.targetFrameRate = 20 // 限制帧率
      mapInstance.scene.requestRenderMode = false // 取消实时渲染
    }
  } else {
    mapInstance.zoomFactor = 5.0 // 鼠标滚轮放大的步长参数

    // 移动设备上禁掉以下几个选项，可以相对更加流畅
    mapInstance.scene.requestRenderMode = false // 取消实时渲染
    mapInstance.scene.fog.enabled = false
    mapInstance.scene.skyAtmosphere.show = false
    mapInstance.scene.globe.showGroundAtmosphere = false
  }

  // //二三维切换不用动画
  if (mapInstance.viewer.sceneModePicker) {
    mapInstance.viewer.sceneModePicker.viewModel.duration = 0.0
  }

  // webgl渲染失败后，刷新页面
  // map.on(mars3d.EventType.renderError, async () => {
  //   await $alert("程序内存消耗过大，请重启浏览器")
  //   window.location.reload()
  // })

  // map构造完成后的一些处理
  onMapLoad()

  emit('onload', mapInstance)
}

// map构造完成后的一些处理
function onMapLoad() {
  const {center, autoRotate} = props

  // const homeButton = new mars3d.control.HomeButton({
  //   title: '复位',
  //   //@ts-ignore
  //   click: function (event: any) {
  //     //@ts-ignore
  //     mapInstance.setCameraView(center)
  //   }
  // })
  // mapInstance.addControl(homeButton)

  // 构造效果
  // let bloomEffect = new mars3d.effect.BloomEffect()
  // mapInstance.addEffect(bloomEffect)
  if (center) {
    //@ts-ignore
    mapInstance?.setCameraView(center).then(() => {
      if (autoRotate) {
        rotatePoint = markRaw(new mars3d.thing.RotatePoint({
          direction: false, // 方向 true逆时针，false顺时针
          time: 100 // 给定飞行一周所需时间(单位 秒)，控制速度
          // autoStopAngle: 360, //到达指定角度后自动停止
        }))
        mapInstance?.addThing(rotatePoint)
        rotatePoint.start()
      }
    })
  }
}

watchEffect(() => {
      if (props.center && mapInstance) {
        //@ts-ignore
        mapInstance?.setCameraView(props.center)
      }
    }
)

/**
 * 开始旋转
 * */
function startRotate() {
  if (mapInstance) {
    // 获取当前视角
    const point = mapInstance.getCenter()
    rotatePoint?.start(point) // 可以传指定的中心点坐标
  }
}


/**
 * 停止旋转
 * */
function stopRotate() {
  rotatePoint?.stop()
}


/**
 * 获取镜头参数
 * */
function printCameraView() {
  if (mapInstance) {
    let cameraView = mapInstance.getCameraView()
    console.log('获取当前镜头参数', JSON.stringify(cameraView))
  }
}

defineExpose({startRotate, stopRotate, printCameraView})
</script>
<style lang="scss">
.mars3d-container {
  height: 100%;
  width: 100%;

  .cesium-viewer {
    overflow: hidden;
    display: block;
    position: relative;
    top: 0;
    left: 0;
  }

  .cesium-viewer,
  .cesium-viewer-cesiumWidgetContainer,
  .cesium-widget,
  canvas {
    height: 100%;
    width: 100%;
  }

  canvas {
    transform: translate3d(0, 0, 0);
    outline: none;
    display: block;
  }
}
</style>
