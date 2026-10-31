<template>
  <LMap style="flex: 1; background-color: transparent" @ready="mapReady" @click="handleClick" v-bind="$attrs">
    <slot />
    <LControl v-if="showReset && attrs.center" position="bottomright">
      <n-tooltip trigger="hover">
        <template #trigger>
          <n-button
            @click="handleResetView"
            :circle="!resetText"
            type="primary"
            class="leaflet-reset-button"
            :class="{ 'leaflet-reset-button--icon-only': !resetText }"
          >
            <template #icon>
              <n-icon size="26px">
                <icon-mdi-home-map-marker />
              </n-icon>
            </template>
            <span v-if="resetText">{{ resetText }}</span>
          </n-button>
        </template>
        视角重置
      </n-tooltip>
    </LControl>
  </LMap>
</template>
<script lang="ts" setup>
import 'leaflet/dist/leaflet.css';
import 'leaflet-compass/dist/leaflet-compass.min.css';
import L from 'leaflet';
import 'leaflet-compass';
//@ts-ignore
import { LControl, LMap } from '@vue-leaflet/vue-leaflet';
import { nextTick, onActivated, onBeforeUnmount, onDeactivated, shallowRef, useAttrs } from 'vue';
import { useLeafletStore } from './useLeaflet';

const useLeaflet = useLeafletStore();

const props = defineProps({
  showReset: { type: Boolean, default: true },
  showCompass: { type: Boolean, default: false },
  resetText: { type: String, default: '' },
});

const attrs = useAttrs();

const emits = defineEmits(['ready', 'click']);

/**
 * leaflet 实例
 * */
const mapInstance = shallowRef();
let resizeObserver: ResizeObserver | null = null;
let refreshTimer: ReturnType<typeof setTimeout> | null = null;
let refreshFrameA: number | null = null;
let refreshFrameB: number | null = null;

function clearRefreshTask() {
  if (refreshTimer) {
    clearTimeout(refreshTimer);
    refreshTimer = null;
  }
  if (refreshFrameA !== null) {
    cancelAnimationFrame(refreshFrameA);
    refreshFrameA = null;
  }
  if (refreshFrameB !== null) {
    cancelAnimationFrame(refreshFrameB);
    refreshFrameB = null;
  }
}

function doRefreshMapSize(attempt = 0) {
  const map = mapInstance.value;
  const container = map?.getContainer?.();

  if (!map || !container) {
    return;
  }

  const rect = container.getBoundingClientRect();
  const width = rect.width || container.clientWidth || 0;
  const height = rect.height || container.clientHeight || 0;

  if ((!width || !height) && attempt < 5) {
    scheduleRefreshMapSize(attempt + 1, 120);
    return;
  }

  const currentCenter = map.getCenter?.();
  const currentZoom = map.getZoom?.();

  map.invalidateSize?.({ pan: false, debounceMoveend: true });

  if (currentCenter && typeof currentZoom === 'number') {
    map.setView?.(currentCenter, currentZoom, { animate: false });
  }
}

function scheduleRefreshMapSize(attempt = 0, delay = 0) {
  clearRefreshTask();

  nextTick(() => {
    refreshFrameA = requestAnimationFrame(() => {
      refreshFrameB = requestAnimationFrame(() => {
        if (delay > 0) {
          refreshTimer = setTimeout(() => {
            refreshTimer = null;
            doRefreshMapSize(attempt);
          }, delay);
          return;
        }

        doRefreshMapSize(attempt);
      });
    });
  });
}

function observeMapContainer(leafletObject: any) {
  resizeObserver?.disconnect();

  if (typeof ResizeObserver === 'undefined') {
    return;
  }

  const container = leafletObject?.getContainer?.();
  if (!container) {
    return;
  }

  resizeObserver = new ResizeObserver((entries) => {
    const hasVisibleEntry = entries.some((entry) => entry.contentRect.width > 0 && entry.contentRect.height > 0);

    if (hasVisibleEntry) {
      scheduleRefreshMapSize();
    }
  });

  resizeObserver.observe(container);
  if (container.parentElement) {
    resizeObserver.observe(container.parentElement);
  }
}

/**
 * 地图加载完毕
 * */
function mapReady(leafletObject: any) {
  //去除版权信息
  leafletObject.attributionControl?.setPrefix('');
  mapInstance.value = leafletObject;
  useLeaflet.init(leafletObject);
  if (props.showCompass) {
    //@ts-ignore
    leafletObject.addControl(new L.Control.Compass());
  }

  observeMapContainer(leafletObject);
  scheduleRefreshMapSize(0, 120);
  emits('ready', leafletObject);
}

/**
 * 点击事件
 * */
function handleClick(e: any) {
  let code = useLeaflet.activeAreaCode;
  useLeaflet.upAreaCode();
  if (code != useLeaflet.activeAreaCode) {
    emits('click', e);
  }
}

/**upAreaCode
 * 返回到默认位置
 * */
function handleResetView() {
  if (attrs.center) {
    mapInstance.value?.flyTo(attrs.center, attrs.zoom);
  }
}

onActivated(() => {
  scheduleRefreshMapSize(0, 120);
});

onDeactivated(() => {
  clearRefreshTask();
});

onBeforeUnmount(() => {
  clearRefreshTask();
  resizeObserver?.disconnect();
  resizeObserver = null;
});

defineExpose({ mapInstance, handleResetView, refreshSize: scheduleRefreshMapSize });
</script>

<style>
.leaflet-div-icon {
  background-color: transparent;
  border: none;
}
</style>
