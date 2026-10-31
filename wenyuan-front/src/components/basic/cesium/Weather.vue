<template>
  <VcPostProcessStage ref="stage" v-if="shader" :fragmentShader="shader" :uniforms="options"></VcPostProcessStage>
</template>
<script lang="ts" setup>
import FogShader from './shader/weather/FogShader.glsl?raw';
import SnowShader from './shader/weather/SnowShader.glsl?raw';
import RainShader from './shader/weather/RainShader.glsl?raw';
//@ts-ignore
import { VcPostProcessStage } from 'vue-cesium';
import { computed, PropType } from 'vue';

const props = defineProps({
  type: { type: String as PropType<'rain' | 'snow' | 'fog' | 'sunny' | null> },
  speed: { type: Number, default: 10 }, //速度
});

const shader = computed(() => {
  const { type } = props;
  if (type === 'rain') {
    return RainShader;
  } else if (type === 'snow') {
    return SnowShader;
  } else if (type === 'fog') {
    return FogShader;
  } else {
    return null;
  }
});

const options = computed(() => {
  const { type, speed } = props;
  if (type === 'rain') {
    return { speed: speed, mixNum: 0.5 };
  } else if (type === 'snow') {
    return { speed: speed };
  } else if (type === 'fog') {
    return { fogColor: 'rgba(255,255,255,0.3)', fogByDistance: new Cesium.Cartesian4(1.0, 0.0, 200.0, 1.0) };
  }
});
</script>
