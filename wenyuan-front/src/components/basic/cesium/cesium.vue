<template>
  <vc-config-provider v-if="runtimeReady" :cesium-path="vcConfig.cesiumPath" :locale="zhHans">
    <slot></slot>
  </vc-config-provider>
</template>
<script lang="ts" setup>
import { onMounted, reactive, ref } from 'vue';
import 'vue-cesium/dist/index.css';
import 'cesium/Build/Cesium/Widgets/widgets.css';
import { loadPreviewCesiumRuntime } from './previewCesiumRuntime';
//@ts-ignore
import { VcConfigProvider } from 'vue-cesium';
//@ts-ignore
import zhHans from 'vue-cesium/es/locale/lang/zh-hans';

const vcConfig = reactive({
  // cesiumPath: 'https://unpkg.com/cesium@1.121.1/Build/Cesium/Cesium.js',
  cesiumPath: './cesium/Cesium.js',
});

const props = defineProps({});

const runtimeReady = ref(false);
onMounted(async () => {
  await loadPreviewCesiumRuntime();
  runtimeReady.value = true;
});

if (!ENABLE_CESIUM) {
  throw Error('请在config配置文件中设置enable_cesium为true');
}
</script>
