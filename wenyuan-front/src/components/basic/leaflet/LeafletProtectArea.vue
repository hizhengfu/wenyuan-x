<template>
  <LeafletTianditu @ready="handleMapReady" layerType="satellite">
    <LControlLayers position="topright"></LControlLayers>
    <LControlZoom position="topleft"></LControlZoom>
    <LControl position="bottomleft">
      <div class="legend">
        图例
        <div class="item bhqy">
          <span></span>
          文物本体区域
        </div>
        <div class="item bhq">
          <span></span>
          文物保护范围
        </div>
        <div class="item hcq">
          <span></span>
          文物控制地带
        </div>
      </div>
    </LControl>
    <LControl position="bottomright">
      <slot name="description"></slot>
    </LControl>
    <slot></slot>
  </LeafletTianditu>
</template>
<script lang="ts" setup>
/**
 * 文保单位两划线
 * */
//@ts-ignore
import LeafletTianditu from './LeafletTianditu.vue';
import { useLeafletStore } from './useLeaflet';
import { PropType } from 'vue';
import { LControlLayers, LControlZoom, LControl, LControlAttribution } from '@vue-leaflet/vue-leaflet';

const useLeaflet = useLeafletStore();

const props = defineProps({
  bhq: {
    //保护区
    type: Array as PropType<[number, number][]>,
    required: true,
  },
  hcq: {
    //缓冲区
    type: Array as PropType<[number, number][]>,
    required: true,
  },
  btqy: {
    //本地区域
    type: Array as PropType<[number, number][]>,
    required: true,
  },
  title: {
    type: String,
    default: '文物本体',
  },
});

/**
 * 地图加载完成
 * */
function handleMapReady() {
  const { bhq, hcq, btqy, title } = props;
  useLeaflet.addCgcs2000ArrLhx(bhq, hcq, btqy, title);
}
</script>
<style lang="scss" scoped>
.legend {
  margin-bottom: 80px;
  color: #000000;
  padding: 6px;
  background-color: #ffffff66;

  .item {
    display: flex;
    flex-direction: row;
    justify-content: center;
    align-items: center;

    span {
      display: inline-block;
      width: 20px;
      height: 6px;
      margin-right: 6px;
    }
  }

  .bhqy {
    span {
      background-color: green;
    }
  }

  .bhq {
    span {
      background-color: #ff0000;
    }
  }

  .hcq {
    span {
      background-color: #0000ff;
    }
  }
}
</style>
