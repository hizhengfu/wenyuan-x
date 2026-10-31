<template>
  <h-leaflet
    v-if="!loading"
    v-element-size="onResize"
    :crs="crs"
    :center="center"
    :options="mapOptions"
    v-bind="$attrs"
    ref="refMap"
    @ready="mapReady"
  >
    <l-image-overlay :url="imageUrl" :bounds="imageBounds" ref="imageOverlay"></l-image-overlay>
    <slot />
  </h-leaflet>
</template>
<script lang="ts" setup>
import HLeaflet from './Leaflet.vue';
import { LImageOverlay } from '@vue-leaflet/vue-leaflet';
import { computed, ref, useAttrs, watch, PropType, nextTick } from 'vue';
import { CRS, LatLngBounds, type Map as LeafletMap } from 'leaflet';
import { fileUrl } from '@/utils';
import { vElementSize } from '@vueuse/components';

const props = defineProps({
  url: { type: String },
  // auto: 包含（显示全部）
  // width: 宽度适配
  // height: 高度适配
  fitType: { type: String as PropType<'auto' | 'width' | 'height' | 'cover'>, default: 'height' },
});

const loading = ref(false);
const imageUrl = ref('');
const imageWidth = ref(1920);
const imageHeight = ref(1080);

const crs = CRS.Simple;
const mapInstance = ref<LeafletMap>();

// 基本地图配置，不限制缩放区间
const mapOptions = {
  minZoom: -5,
  maxZoom: 10,
  zoomSnap: 0,
  attributionControl: false,
};

// 图像真实 bounds：保持原比例，不要改动
const imageBounds = computed(() => {
  return new LatLngBounds([0, 0], [imageHeight.value, imageWidth.value]);
});

// 初始化中心点
const center = computed(() => {
  return [imageHeight.value / 2, imageWidth.value / 2];
});

function loadImage(url: string): Promise<HTMLImageElement> {
  const tempUrl = fileUrl(url);
  imageUrl.value = '';
  loading.value = true;

  return new Promise((resolve, reject) => {
    const image = new Image();
    image.src = tempUrl;

    image.onload = function () {
      loading.value = false;
      imageUrl.value = tempUrl;
      resolve(this as HTMLImageElement);
    };

    image.onerror = () => {
      loading.value = false;
      reject(`${url} 加载失败`);
    };
  });
}

/**
 * 监听 URL 更换
 */
watch(
  () => props.url,
  (newUrl) => {
    if (!newUrl) return;
    loadImage(newUrl).then((el) => {
      imageWidth.value = el.width;
      imageHeight.value = el.height;

      nextTick(() => fitView());
    });
  },
  { immediate: true },
);

/**
 * 地图初始化完成
 */
function mapReady(instance: LeafletMap) {
  mapInstance.value = instance;
  nextTick(() => fitView());
}

/**
 * 容器变化时重新适配
 */
function onResize() {
  nextTick(() => fitView());
}

/**
 * ⭐ 核心：正确不失真的自适应方法
 */
function fitView() {
  const map = mapInstance.value;
  if (!map) return;

  const container = map.getContainer();
  if (!container || container.clientWidth === 0 || container.clientHeight === 0) return;

  const bounds = imageBounds.value;
  const imgW = imageWidth.value;
  const imgH = imageHeight.value;
  const centerPoint = [imgH / 2, imgW / 2];

  // 1. Auto 模式：包含整张图（Leaflet 默认逻辑）
  if (props.fitType === 'auto') {
    map.fitBounds(bounds, { padding: [0, 0], animate: false });
    map.setMaxBounds(bounds.pad(0.2));
    return;
  }

  // 2. 计算基础 zoom 值（以包含图片宽/高）
  const baseZoom = map.getBoundsZoom(bounds, false);

  let targetZoom = baseZoom;

  // 3. 宽度适配
  if (props.fitType === 'width') {
    const containerWidth = container.clientWidth;
    const scale = containerWidth / imgW;

    // Leaflet zoom = log2(scale)
    targetZoom = Math.log2(scale);
  }

  // 4. 高度适配
  else if (props.fitType === 'height') {
    const containerHeight = container.clientHeight;
    const scale = containerHeight / imgH;

    targetZoom = Math.log2(scale);
  }

  // 5. 设置 zoom + 中心点（不变形）
  else if (props.fitType === 'cover') {
    const containerWidth = container.clientWidth;
    const containerHeight = container.clientHeight;
    const scale = Math.max(containerWidth / imgW, containerHeight / imgH);

    targetZoom = Math.log2(scale);
  }

  map.setView(centerPoint as any, targetZoom, { animate: false });

  // 6. 保持拖拽合理范围
  map.setMaxBounds(bounds.pad(0.2));
}

defineExpose({ mapInstance });
</script>
