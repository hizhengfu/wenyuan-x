<template>
  <l-geo-json
    ref="geoJsonRef"
    :geojson="geoJson"
    :options="geoJsonOptions"
    attribution="<p>下钻: 双击要进入的下级行政区域,上卷: 双击空白区域或点击返回上一级</p>"
  ></l-geo-json>
</template>
<script lang="ts" setup>
import L from 'leaflet';
// @ts-ignore
import { LGeoJson } from '@vue-leaflet/vue-leaflet';
// import { parse } from 'wkt';
import { onMounted, ref, watch } from 'vue';
import axios, { AxiosResponse } from 'axios';
import pako from 'pako';
import { useLeafletStore } from './useLeaflet.ts';
import { find } from 'lodash-es';

const useLeaflet = useLeafletStore();

const props = defineProps({
  value: { type: String },
  keyword: { type: String },
  areaCode: { type: String, default: '156000000' }, //地区代码
  fillColor: { type: String, default: '#a21f1f' }, //填充颜色
  token: { type: String, default: 'c93ea43b61c76df411540db24f55bfd1' }, //token
});
const emits = defineEmits(['update:value', 'click']);

const geoJson = ref();
const geoJsonRef = ref();
const activeAreaCode = ref();

const geoJsonOptions = {
  fillOpacity: 0.3,
  weight: 1,
  fillColor: props.fillColor,
  onEachFeature: (feature: any, layer: any) => {
    layer.on({
      mouseover(e: any) {
        const layer = e.target;
        layer.setStyle({
          weight: 1,
          color: '#E6B79B',
          dashArray: '',
          fillOpacity: 0.7,
        });
        // layer.bringToFront();
      },
      mouseout(e: any) {
        geoJsonRef.value?.leafletObject.resetStyle(e.layer);
      },
      click(e: any) {
        let properties = e.target.feature.properties;
        let gb = properties.gb;
        activeAreaCode.value = gb;
        const bounds = e.target.getBounds();
        fetchGeojsonData(gb).then(() => {
          useLeaflet.instance?.fitBounds(bounds);
          useLeaflet.updateAreaCode(gb);
        });
        //阻止事件传播
        L.DomEvent.stopPropagation(e);
        useLeaflet.addAreaHistory(e.target.feature.properties);
        emits('click', e.target.feature.properties);
      },
    });
    // layer.bindTooltip(`${feature.properties.name}(1000)`, {
    //   direction: 'center',
    //   permanent: true,
    //   interactive: true,
    // });
  },
};

/**
 * 获取行政区划信息
 * 参数参考 https://api.tianditu.gov.cn/v2/administrative
 * */
function fetchDataAdministrative(keyword: string | undefined) {
  const { token } = props;
  axios
    .get('https://api.tianditu.gov.cn/v2/administrative', {
      params: {
        keyword: keyword, //行政区划名称、行政区划编码
        childLevel: 1, //置显示下级行政区级数（行政区级别包括：国家、省/直辖市、市、区/县多级数据
        extensions: true, //是否需要轮廓数据
        tk: token,
      },
    })
    .then((resp: AxiosResponse) => {
      let data = resp.data.data;
      let district = data.district;

      if (district && district.length > 0) {
        let boundary = district[0].boundary;
        // geoJson.value = parse(boundary);
      }
    });
}

const MUNICIPALITIES = new Set(['11', '12', '31', '50']);
/**
 * 直辖市判断
 * */
function isMunicipality(code: string): boolean {
  if (/^\d{9}$/.test(code)) {
    return MUNICIPALITIES.has(code.slice(3, 5));
  } else if (/^\d{6}$/.test(code)) {
    return MUNICIPALITIES.has(code.slice(0, 2));
  } else {
    return false;
  }
}

function fetchGeojsonData(areaCode: string) {
  let level = 0;
  let tempAreaCode = areaCode;
  if (areaCode.slice(-6) === '000000') {
    level = 2;
  } else if (areaCode.slice(-4) === '0000') {
    level = 1;
  } else if (areaCode.slice(-2) === '00') {
    level = 0;
  } else {
    tempAreaCode = areaCode.slice(0, -2) + '00';
  }

  //直辖市
  if (isMunicipality(areaCode)) {
    level = 1;
    tempAreaCode = areaCode.slice(0, -4) + '0000';
  }

  let url = ['./geojson'];
  if (level != 2) {
    url.push(`${level}`);
  }
  url.push(`${tempAreaCode}.gzip`);

  return axios
    .get(url.join('/'), {
      responseType: 'arraybuffer',
    })
    .then((resp: AxiosResponse) => {
      let e = new Int8Array(pako.inflate(resp.data));

      let r = [];
      for (let i = 0; i < e.length; i += 4) {
        let n = 0;
        for (let o = i; o <= i + 3; o++) {
          n += (255 & e[o]) << (8 * (3 - (o - i)));
        }
        n >>= 2;
        r.push(n);
      }
      const a = new TextDecoder('utf-8');

      let json = JSON.parse(a.decode(new Int8Array(r)));
      if (json.features.length > 0) {
        if (tempAreaCode != areaCode) {
          json = find(json.features, ['properties.gb', areaCode]);
        }
        geoJson.value = json;
      }
      return json;
    });
}

onMounted(() => {
  fetchGeojsonData(props.areaCode);
  useLeaflet.addAreaHistory({ name: '全国', gb: props.areaCode });
});

watch(
  () => useLeaflet.activeAreaCode,
  (newCode) => {
    fetchGeojsonData(newCode).then((json) => {
      let bounds = geoJsonRef.value?.leafletObject.getBounds();
      if (bounds) {
        useLeaflet.instance?.fitBounds(bounds);
      }
      emits('click', { gb: newCode });
    });
  },
);
</script>
