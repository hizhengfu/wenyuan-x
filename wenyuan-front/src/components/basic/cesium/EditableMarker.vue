<template>
  <VcEntity :position="position" :point="pointStyle" />
</template>

<script setup>
import { ref, watch, onMounted, onUnmounted } from 'vue';

defineProps({
  modelValue: {
    type: Array,
    required: true,
  },
  pointStyle: {
    type: Object,
    default: () => ({
      pixelSize: 12,
      color: 'red',
      outlineColor: 'white',
      outlineWidth: 2,
    }),
  },
});

const emit = defineEmits(['update:modelValue', 'dragstart', 'drag', 'dragend']);

const position = ref([...props.modelValue]);

watch(
  () => props.modelValue,
  (newVal) => {
    position.value = [...newVal];
  },
);

let viewer = null;
let Cesium = null;
let dragging = false;
let dragMode = 'flat';
let handler = null;

onMounted(() => {
  const viewerEl = document.querySelector('.vc-container')?.__vue_app__?.config.globalProperties?.$vcViewer;
  if (!viewerEl) {
    console.warn('Vue-Cesium viewer not found');
    return;
  }

  viewer = viewerEl.viewer;
  Cesium = viewerEl.Cesium;
  handler = new Cesium.ScreenSpaceEventHandler(viewer.canvas);

  handler.setInputAction(onMouseDown, Cesium.ScreenSpaceEventType.LEFT_DOWN);
  handler.setInputAction(onMouseMove, Cesium.ScreenSpaceEventType.MOUSE_MOVE);
  handler.setInputAction(onMouseUp, Cesium.ScreenSpaceEventType.LEFT_UP);
});

onUnmounted(() => {
  if (handler) {
    handler.destroy();
  }
});

function onMouseDown(e) {
  const picked = viewer.scene.pick(e.position);
  if (picked && picked.id && picked.id.point) {
    dragging = true;
    dragMode = e.shiftKey ? 'height' : 'flat';
    emit('dragstart', [...position.value]);
  }
}

function onMouseMove(e) {
  if (!dragging) return;

  const ellipsoid = viewer.scene.globe.ellipsoid;
  const cartesian = viewer.camera.pickEllipsoid(e.endPosition, ellipsoid);
  if (!cartesian) return;

  const cartographic = Cesium.Cartographic.fromCartesian(cartesian);
  let lon = Cesium.Math.toDegrees(cartographic.longitude);
  let lat = Cesium.Math.toDegrees(cartographic.latitude);
  let height = position.value[2];

  if (dragMode === 'flat') {
    position.value = [lon, lat, height];
  } else if (dragMode === 'height') {
    const deltaY = e.startPosition.y - e.endPosition.y;
    const deltaHeight = deltaY * 2;
    height = Math.max(0, height + deltaHeight);
    position.value = [position.value[0], position.value[1], height];
  }

  emit('update:modelValue', [...position.value]);
  emit('drag', [...position.value]);
}

function onMouseUp() {
  if (dragging) {
    dragging = false;
    emit('dragend', [...position.value]);
  }
}
</script>
