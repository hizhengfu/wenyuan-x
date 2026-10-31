import { defineStore } from 'pinia';
import { shallowRef, ref } from 'vue';
import L from 'leaflet';
import area from '@turf/area';
import { cGCS2000ToWgs84, polygonGeoJSON } from './coordinate';

export const useLeafletStore = defineStore('leaflet-store', () => {
  //地图实例
  let instance = shallowRef();
  //当前的地区代码
  const activeAreaCode = ref();

  const areaList = ref<any>([]);

  /**
   * 初始化mars3d
   * */
  function init(map: L.Map) {
    instance.value = map;
  }

  function fitBounds(bounds: any[]) {
    instance.value?.fitBounds(bounds);
  }

  /**
   * 设置地区代码
   * */
  function updateAreaCode(code: string) {
    activeAreaCode.value = code;
  }

  /**
   * 添加地区信息
   * */
  function addAreaHistory(item: any) {
    areaList.value.push(item);
  }

  /**
   * 上钻地区代码
   * */
  function upAreaCode() {
    let code = activeAreaCode.value;
    //code存在时才处理
    if (!code) return;

    let tempCode = '156000000';
    if (code.slice(-4) === '0000') {
      tempCode = code.slice(0, 3) + '000000';
    } else if (code.slice(-2) === '00') {
      tempCode = code.slice(0, 5) + '0000';
    } else {
      tempCode = code.slice(0, 7) + '00';
    }
    if (tempCode != activeAreaCode.value) {
      activeAreaCode.value = tempCode;
    }

    if (areaList.value.length > 1) {
      areaList.value.pop();
    }
  }

  /**
   * 转换为保护区geojson文件
   * */
  function cgcs2000ToGeojson(
    arr: { x: number; y: number }[],
    style = {
      weight: 2,
      color: 'red',
      fillColor: 'red',
      fillOpacity: 0.5,
    },
    properties: object = {},
  ) {
    let geoJson = polygonGeoJSON([arr.map((item) => cGCS2000ToWgs84(item.x, item.y))], properties);

    //@ts-ignore
    return L.geoJSON(geoJson, {
      style: style,
      onEachFeature: (feature, layer) => {
        layer.bindTooltip(
          `${feature.properties.title || ''}<br/>面积：${(area(feature) / 1000000).toFixed(4)}平方公里`,
        );
      },
    });
  }

  /**
   * 转换为保护区geojson文件
   * */
  function wgs84GeoJson(
    geoJson: any,
    style = {
      weight: 2,
      color: 'red',
      fillColor: 'red',
      fillOpacity: 0.5,
    },
    properties: any = {},
  ) {
    //@ts-ignore
    return L.geoJSON(geoJson, {
      style: style,
      onEachFeature: (feature, layer) => {
        // 创建一个tooltip用于显示经纬度
        const tooltip = L.tooltip({
          permanent: false,
          sticky: true, // tooltip跟随鼠标
          className: 'coordinate-tooltip',
        });

        // 鼠标移动事件 - 显示当前鼠标位置的经纬度
        layer.on('mousemove', (e: L.LeafletMouseEvent) => {
          const lat = e.latlng.lat.toFixed(6);
          const lng = e.latlng.lng.toFixed(6);
          tooltip.setContent(`经度: ${lng}<br/>纬度: ${lat}`);
          tooltip.setLatLng(e.latlng);
          if (!tooltip.isOpen()) {
            tooltip.addTo(instance.value);
          }
        });

        // 鼠标移出事件 - 移除tooltip
        layer.on('mouseout', () => {
          if (tooltip.isOpen()) {
            tooltip.remove();
          }
        });

        // 保留原有的面积tooltip（点击或悬停时显示）
        layer.bindPopup(
          `${feature.properties.title || properties.title || ''}<br/>面积：${(area(feature) / 1000000).toFixed(4)}平方公里`,
        );
      },
    });
  }

  /**
   * 转换为保护区geojson文件
   * */
  function wgs84ToGeojson(
    arr: [number, number][],
    style = {
      weight: 2,
      color: 'red',
      fillColor: 'red',
      fillOpacity: 0.5,
    },
    properties: object = {},
  ) {
    let geoJson = polygonGeoJSON([arr], properties);

    //@ts-ignore
    return L.geoJSON(geoJson, {
      style: style,
      onEachFeature: (feature, layer) => {
        layer.bindTooltip(
          `${feature.properties.title || ''}<br/>面积：${(area(feature) / 1000000).toFixed(4)}平方公里`,
          {
            className: 'custom-tooltip',
          },
        );
      },
    });
  }

  /**
   * cgcs 2000数组处理
   * */
  function addCgcs2000ArrLhx(
    bhq: [number, number][],
    hcq: [number, number][],
    bhqy: [number, number][],
    title: string = '文物本体',
  ) {
    addCgcs2000Lhx(
      bhq.map((item) => {
        return { x: item[0], y: item[1] };
      }),
      hcq.map((item) => {
        return { x: item[0], y: item[1] };
      }),
      bhqy.map((item) => {
        return { x: item[0], y: item[1] };
      }),
      title,
    );
  }

  /**
   * 增加cgcs2000两划线
   * @param bhq 保护区
   * @param hcq 缓冲区
   * */
  function addCgcs2000Lhx(
    bhq: { x: number; y: number }[],
    hcq: { x: number; y: number }[],
    bhqy: {
      x: number;
      y: number;
    }[],
    title: string = '文物本体',
  ) {
    let bhqLayer = cgcs2000ToGeojson(
      bhq,
      {
        weight: 2,
        color: 'red',
        fillColor: 'red',
        fillOpacity: 0.0,
      },
      { title: '保护区' },
    );

    let hcqLayer = cgcs2000ToGeojson(
      hcq,
      {
        weight: 2,
        color: 'blue',
        fillColor: 'blue',
        fillOpacity: 0.0,
      },
      { title: '缓冲区' },
    );

    let btLayer = cgcs2000ToGeojson(
      bhqy,
      {
        weight: 2,
        color: 'green',
        fillColor: 'green',
        fillOpacity: 0.0,
      },
      { title: '本体' },
    );
    instance.value?.addLayer(hcqLayer);
    instance.value?.addLayer(bhqLayer);
    instance.value?.addLayer(btLayer);
    //获取中心点
    let center = btLayer.getBounds().getCenter();

    //添加点位
    instance.value?.addLayer(L.marker(center, { title: title }).bindTooltip(title));

    instance.value?.fitBounds(hcqLayer.getBounds());
  }

  /**
   * 增加wgs84两划线
   * @param bhq 保护区
   * @param hcq 缓冲区
   * @param btqy 本体区
   * @param  autoFitBounds 是否控制边界在视口内
   * */
  function addWgs84GeoJsonLhx(bhq: any, hcq: any, btqy: any, autoFitBounds: boolean = true) {
    if (bhq) {
      let bhqLayer = wgs84GeoJson(
        bhq,
        {
          weight: 2,
          color: 'red',
          fillColor: 'red',
          fillOpacity: 0.0,
        },
        { title: '保护区' },
      );
      instance.value?.addLayer(bhqLayer);
      
      if(autoFitBounds){
      instance.value?.fitBounds(bhqLayer.getBounds());
      }

    }

    if (hcq) {
      let hcqLayer = wgs84GeoJson(
        hcq,
        {
          weight: 2,
          color: 'blue',
          fillColor: 'blue',
          fillOpacity: 0.0,
        },
        { title: '缓冲区' },
      );
      instance.value?.addLayer(hcqLayer);
      if(autoFitBounds){
      instance.value?.fitBounds(hcqLayer.getBounds());
      }

    }

    if (btqy) {
      let btLayer = wgs84GeoJson(
        btqy,
        {
          weight: 2,
          color: 'green',
          fillColor: 'green',
          fillOpacity: 0.0,
        },
        { title: '本体' },
      );

      instance.value?.addLayer(btLayer);
     if(autoFitBounds){
      instance.value?.fitBounds(btLayer.getBounds());
      }
    }
  }

  /**
   * 增加geoJson到地图
   * */
  function addWgs84GeoJson(geoJson: any, title: string = '区域') {
    let btLayer = wgs84GeoJson(
      geoJson,
      {
        weight: 2,
        color: '#c6a16e',
        fillColor: '#c6a16e',
        fillOpacity: 0.0,
      },
      { title: title },
    );

    instance.value?.addLayer(btLayer);
    instance.value?.fitBounds(btLayer.getBounds());
    return btLayer;
  }

  return {
    init,
    instance,
    fitBounds,
    activeAreaCode,
    areaList,
    addAreaHistory,
    upAreaCode,
    updateAreaCode,
    cgcs2000ToGeojson,
    addCgcs2000Lhx,
    addCgcs2000ArrLhx,
    addWgs84GeoJsonLhx,
    addWgs84GeoJson,
    wgs84ToGeojson,
  };
});
