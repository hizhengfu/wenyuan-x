<template>
  <vc-primitive :appearance="appearance" @ready="handleReady">
    <vc-geometry-instance>
      <vc-geometry-polygon
        ref="geometryRef"
        :polygonHierarchy="polygonHierarchy"
        :vertexFormat="vertexFormat"
        :height="1"
        :extrudedHeight="30"
      ></vc-geometry-polygon>
    </vc-geometry-instance>
  </vc-primitive>
</template>
<script lang="ts" setup>
//@ts-ignore
import { VcGeometryInstance, VcGeometryPolygon, VcPrimitive } from 'vue-cesium';
import { PropType, ref } from 'vue';
//@ts-ignore
import type { VcReadyObject } from 'vue-cesium/es/utils/types';

const props = defineProps({
  type: { type: String as PropType<'rain' | 'snow' | 'fog' | null> },
  speed: { type: Number, default: 10 }, //速度
});

const appearance = ref();
const vertexFormat = ref();

const polygonHierarchy = ref([
  [104.00082823745913, 30.603372527303605],
  [103.95115517406572, 30.58611486675747],
  [103.93203151043531, 30.502081094964822],
  [104.04397571527119, 30.43366262018482],
  [104.1838202763418, 30.451367907420234],
  [104.14415004271933, 30.605602488376448],
  [104.00082823745913, 30.603372527303605],
]);

function handleReady({ Cesium, viewer }: VcReadyObject) {
  vertexFormat.value = Cesium.EllipsoidSurfaceAppearance.VERTEX_FORMAT;
  appearance.value = new Cesium.EllipsoidSurfaceAppearance({
    material: new Cesium.Material({
      fabric: {
        type: 'Water',
        uniforms: {
          normalMap: Cesium.buildModuleUrl('Assets/Textures/waterNormals.jpg'),
          frequency: 10.0,
          animationSpeed: 0.05,
          amplitude: 10.0,
        },
      },
    }),
  });
}
</script>
