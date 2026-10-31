import { ref, markRaw } from 'vue';
import { defineStore } from 'pinia';
import * as mars3d from 'mars3d';
import * as Cesium from 'mars3d-cesium';

import { envelope, featureCollection } from '@turf/turf';
import { fileUrl } from '@/utils';
import { PointAlarmData } from '@/modules/monitor/typings/monitor';

export const useMars3dStore = defineStore('mars3d-store', () => {
  /**
   * 是否初始化
   * */
  const init = ref(false);

  /**
   * 地图示例
   * */
  let mapInstance: mars3d.Map | null;

  /**
   * 切换主视角
   * */
  function changeCameraView(view: any) {
    return mapInstance?.setCameraView(view);
  }

  function getInstance(): mars3d.Map | null {
    return mapInstance;
  }

  /**
   * 初始化mars3d
   * */
  function mars3dInit(instance: mars3d.Map) {
    init.value = true;
    mapInstance = markRaw(instance);
  }

  /**
   * 销毁函数
   * */
  function mars3dDestroy() {
    init.value = false;
    mapInstance?.destroy();
    mapInstance = null;
  }

  /**
   * 添加图层
   * */
  function addTilesetLayer(layer: mars3d.layer.TilesetLayer) {
    return new Promise((resolve, reject) => {
      if (!layer) {
        reject(false);
        return;
      }
      if (mapInstance) {
        mapInstance.addLayer(layer).then(() => {
          // layer.flyTo()
          resolve(layer);
        });
      }
    });
  }

  /**
   * 移除图层
   * */
  function removeTilesetLayer(layer?: mars3d.layer.TilesetLayer) {
    if (layer) {
      mapInstance?.removeLayer(layer);
    }
  }

  /**
   * 对图层进行挖洞
   * */
  function tilesetPlanClip(features: any) {
    let layer = mapInstance?.getLayerById('sanya-build');
    let turfFeatureCollection = featureCollection(features);
    if (layer) {
      // 计算外边缘
      let envelope1 = envelope(turfFeatureCollection);

      // 获取外边缘的边界框信息
      let boundingBox = envelope1.bbox;
      console.log(111222, envelope1, boundingBox);

      new mars3d.thing.TilesetClip({ layer: layer, area: boundingBox, clipOutSide: true });
    }
  }

  /**
   * 增加规划信息
   * */
  function addGeoJson(layer?: mars3d.layer.GeoJsonLayer) {
    if (layer) {
      mapInstance?.addLayer(layer);
    }
  }

  /**
   * 清空geojson图层
   * */
  function removeGeoJson(layer: mars3d.layer.GeoJsonLayer) {
    if (layer) {
      mapInstance?.removeLayer(layer);
    }
  }

  /**
   * 增加矢量数据图层
   * */
  function addGraphicLayer(layer?: mars3d.layer.GraphicLayer) {
    if (layer && mapInstance) {
      mapInstance.addLayer(layer);
      return true;
    }
    return false;
  }

  /**
   * 添加传感器图标
   * */
  function addSensorMarker(point: mars3d.LngLatPoint, attr: object, alarm?: PointAlarmData) {
    const graphic = new mars3d.graphic.BillboardEntity({
      position: point,
      style: {
        image: `./sensor/marker-${alarm?.maxAlarmLevel || 0}.svg`,
        horizontalOrigin: Cesium.HorizontalOrigin.CENTER,
        verticalOrigin: Cesium.VerticalOrigin.BOTTOM,
        scale: 0.3,
        visibleDepth: true,
        clampToGround: false,
      },
      attr: attr || {},
    });
    return graphic;
  }

  /**
   *  增加动态编辑点位的
   * */
  function moveMarker(graphic: mars3d.graphic.BasePointEntity) {
    //@ts-ignore
    const pointEdit = new mars3d.thing.MatrixMove({
     //@ts-ignore
      position: graphic.position,
    });
    mapInstance?.addThing(pointEdit);

    pointEdit.on(mars3d.EventType.change, (event) => {
      graphic.position = event.position;
    });
    return pointEdit;
  }

  /**
   *  删除动态编辑点位的
   * */
  function removeEditMarker(thing: mars3d.thing.MatrixMove) {
    mapInstance?.removeThing(thing);
  }

  /**
   * 移除矢量数据图层
   * */
  function removeGraphicLayer(layer: mars3d.layer.GraphicLayer | null) {
    if (layer) {
      mapInstance?.removeLayer(layer);
    }
  }

  function destroyGraphicLayer(layer?: mars3d.layer.GraphicLayer) {
    if (layer) {
      layer.destroy();
      mapInstance?.removeLayer(layer);
    }
  }

  function addImageLayer(layer?: mars3d.layer.ImageLayer) {
    if (layer) {
      mapInstance?.addLayer(layer);
    }
  }

  function removeImageLayer(layer?: mars3d.layer.ImageLayer) {
    if (layer) {
      mapInstance?.removeLayer(layer);
    }
  }

  let effectList: mars3d.effect.BaseEffect[] = [];

  /**
   * 清除特效
   * */
  function clearEffect() {
    for (let i = effectList.length - 1; i >= 0; i--) {
      if (effectList[i]) {
        // 如果元素是偶数，则删除
        // let effect = effectList.splice(i, 1)[0];
        mapInstance?.removeEffect(effectList[i], true);
      }
    }
    effectList = [];
  }

  /***
   添加下雨效果
   */
  function addRainEffect() {
    let rainEffect = new mars3d.effect.Rain({
      speed: 10,
      size: 20,
      direction: 10,
    });
    mapInstance?.addEffect(rainEffect);
    effectList.push(rainEffect);
  }

  /**
   * 积雪效果
   * */
  function addSnowEffect(layer?: mars3d.layer.TilesetLayer | null) {
    let snowEffect = new mars3d.effect.Snow({
      speed: 20,
      maxHeight: 8000, // 大于此高度后不显示
    });
    effectList.push(snowEffect);
    mapInstance?.addEffect(snowEffect);

    if (layer) {
      let snowCover = new mars3d.effect.SnowCover({
        layer: layer, // 如果传值3dtiles图层，只对该模型生效
        alpha: 0.6,
        maxHeight: 8000, // 大于此高度后不显示
      });
      effectList.push(snowCover);
      mapInstance?.addEffect(snowCover);
    }
  }

  /**
   * 雾效果
   * */
  function addFogEffect() {
    // 雾效果
    let fogEffect = new mars3d.effect.Fog({
      maxHeight: 20000, // 大于此高度后不显示
      fogByDistance: new Cesium.Cartesian4(100, 0.0, 9000, 0.9),
      color: Cesium.Color.WHITE,
    });
    effectList.push(fogEffect);
    mapInstance?.addEffect(fogEffect);
  }

  /**
   * 土地规划名称与颜色
   * */
  const tdghColor = {
    '070102': { color: '#fdfd03', name: '二类住宅用地' },
    '070102/090101': { color: '#ff7f07', name: '二类城镇住宅/零售商业用地' },
    '0801': { color: '#ff80a0', name: '机关团体用地' },
    '080301/080302/080501': { color: '#fea07f', name: '图书与展览/文化活动/体育场馆用地' },
    '080403': { color: '#fe7ffe', name: '中小学用地' },
    '080404': { color: '#ffe081', name: '幼儿园用地' },

    '080601': { color: '#fea09f', name: '医院用地' },
    '080701': { color: '#dc6f8a', name: '老年人社会福利用地' },
    '090101': { color: '#fd023e', name: '零售商业用地' },
    '090101/090103': { color: '#c95757', name: '零售商业/餐饮用地零' },
    '090104': { color: '#fd5655', name: '旅馆用地' },
    '090105': { color: '#05a1c9', name: '公用设施营业网点用地' },

    '0902/090101': { color: '#dd0000', name: '商务金融/零售商业用地' },
    '0902/090104': { color: '#fda085', name: '商务金融/旅馆用地' },
    '1303': { color: '#038bb8', name: '供电用地' },
    '1310': { color: '#038bb7', name: '消防用地' },
    '1401': { color: '#88cd1d', name: '公园绿地' },
    '1402': { color: '#07b804', name: '防护绿地' },

    '1501': { color: '#598755', name: '军事设施用地' },
    // '16': { color: '#598755', name: '留白用地' },
    '16': { color: '#D9EEEF', name: '留白用地' },

    '01/090101': { color: '#FBA550', name: '暂无' }, //淡黄色
    '02/090101': { color: '#FD3F03', name: '暂无' }, //橙红色
    '03/090101': { color: '#FFBE00', name: '暂无' }, //泥黄色
    '01/080403': { color: '#E213B9', name: '暂无' }, //深紫色
    '01/080301': { color: '#F0E0D0', name: '暂无' }, //淡肉色

    other: { color: '#70a740', name: '非建设用地' },
  };

  /**
   * 获取土地规划颜色定义
   * */
  function getTdghDefine(code: string) {
    //@ts-ignore
    return tdghColor[code] || tdghColor.other;
  }

  /**
   * 根据类型和属性获取名称
   * */
  function tdghLabel(type: number, attr: any) {
    if (type === 3) {
      return attr.kzheight;
    } else if (type == 2) {
      return attr.rjl;
    } else if (type == 4) {
      return attr.jzmd;
    } else {
      return attr.type;
    }
  }

  function convertToNumber(value: any) {
    if (typeof value === 'number') {
      // 如果值已经是数值类型，直接返回
      return value;
    }

    // 去除字符串中的特殊符号（如百分号）
    const numericValue = (value || '0').replace('%', '');

    // 尝试将字符串转换为数值
    const number = parseFloat(numericValue);

    return number;
  }

  /**
   * @param type 规划类型
   * @param attr 规划值
   * */
  function tdghStyle(type: number, attr: any, tdghColors: any) {
    let opacity = 6.0;
    let usage = attr.properties?.toLowerCase();
    //建筑控制高度
    if (type === 3) {
      let kzheight = convertToNumber(attr.heightLimit);
      let color = '#ff0000';
      if (kzheight <= 18) {
        color = '#D8D6FD';
      } else if (kzheight > 18 && kzheight <= 24) {
        color = '#9097E2';
      } else if (kzheight > 24 && kzheight <= 36) {
        color = '#556DC9';
      } else if (kzheight > 36 && kzheight <= 54) {
        color = '#234EAD';
      } else if (kzheight > 54 && kzheight <= 90) {
        color = '#003993';
      } else if (kzheight > 90) {
        color = '#002a6b';
      } else {
        opacity = 0.0;
      }
      return { color: color, height: 0, diffHeight: 1 * (kzheight || 10), opacity: opacity };
    } else if (type === 2) {
      //容积率
      let rjl = convertToNumber(attr.areaRatio);
      let color = '#ff0000';
      if (rjl <= 1.0) {
        color = '#FFEBD7';
      } else if (rjl > 1.0 && rjl <= 1.5) {
        color = '#F0BC9E';
      } else if (rjl > 1.5 && rjl <= 2.0) {
        color = '#E28566';
      } else if (rjl > 2.0 && rjl <= 2.5) {
        color = '#D34834';
      } else if (rjl > 2.5 && rjl <= 3.0) {
        color = '#C40A0C';
      } else if (rjl > 3.0) {
        color = '#9e0203';
      } else {
        opacity = 0.0;
      }
      return { color: color, height: 0, diffHeight: 10 * (rjl || 1), opacity: opacity };
    } else if (type === 4) {
      //建筑密度控制图
      opacity = 0.0;
      let md = convertToNumber(attr.densityBuildings);
      let color = '#D8ECB7';
      if (md > 0 && md < 20) {
        color = '#D8ECB7';
        opacity = 1.0;
      } else if (md >= 20 && md < 25) {
        color = '#FDDAF8';
        opacity = 1.0;
      } else if (md >= 25 && md < 30) {
        color = '#D8ACF3';
        opacity = 1.0;
      } else if (md >= 30 && md < 35) {
        color = '#B08CFE';
        opacity = 1.0;
      } else if (md >= 35 && md < 40) {
        color = '#8E58FB';
        opacity = 1.0;
      } else if (md >= 40) {
        color = '#6c23fb';
        opacity = 1.0;
      }
      return { color: color, height: 0, diffHeight: 1 * (md || 1), opacity: opacity };
    } else if (type === 0) {
      //居住用地规划图
      opacity = 0;
      if (attr.type === '070102' || attr.type === '070102/090101') {
        opacity = 1.0;
      }
      // let colorItem = getTdghDefine(attr.type)
      let color = tdghColors[usage] || '#70a740';
      return { color: color, height: 0, diffHeight: 10, opacity: opacity };
    }
    // let colorItem = getTdghDefine(attr.type)
    let color = tdghColors[usage] || '#70a740';
    return { color: color, height: 0, diffHeight: 10, opacity: opacity };
  }

  /**
   * 创建建筑规划图层
   * @param id 图层id
   * @param type 图层类型 1 控制高度，2 容积率
   * @param url 图层路径
   * */
  function addJzghLayer(id: string, name: string, type: number, url: string) {
    let layer = new mars3d.layer.GeoJsonLayer({
      name: name,
      id: 'jzgh-' + id,
      url: fileUrl(url),
      zIndex: 1000,
      symbol: {
        type: 'polygonC', // 大数据面类型，效率高
        styleOptions: {
          color: '#00ff00',
          opacity: 1.0,
          outline: false,
        },
        callback: function (attr, styleOpt) {},
      },
      popup: 'all',
    });
    mapInstance?.addLayer(layer);
    return layer;
  }

  // onMounted(() => {
  //
  // })
  //
  // onUnmounted(() => {
  //   mars3dDestroy()
  // })

  return {
    init,
    getInstance,
    tdghStyle,
    tdghLabel,
    mars3dInit,
    mars3dDestroy,
    addGeoJson,
    removeGeoJson,
    changeCameraView,
    addGraphicLayer,
    removeGraphicLayer,
    addSensorMarker,
    moveMarker,
    removeEditMarker,
    addImageLayer,
    removeImageLayer,
    addTilesetLayer,
    removeTilesetLayer,
    addRainEffect,
    addSnowEffect,
    addFogEffect,
    clearEffect,
    addJzghLayer,
  };
});
