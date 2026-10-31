<template>
  <LControl class="leaflet-bar" v-bind="$attrs" @ready="handleReady">
    <a href="#" @click="handleToggle">
      <NIcon size="29">
        <ScreenToggleIcon></ScreenToggleIcon>
      </NIcon>
    </a>
  </LControl>
</template>
<script lang="ts" setup>
import L from 'leaflet';
import { LControl } from '@vue-leaflet/vue-leaflet';
import { useFullscreen } from '@vueuse/core';
import { computed, ref } from 'vue';
import Fullscreen from '~icons/mdi/fullscreen';
import FullscreenExit from '~icons/mdi/fullscreen-exit';

const mapContainerRef = ref();

const { isFullscreen, toggle } = useFullscreen(mapContainerRef);
const ScreenToggleIcon = computed(() => {
  return isFullscreen.value ? FullscreenExit : Fullscreen;
});

const leafletObjectMap = ref();

function handleToggle(e: any) {
  L.DomEvent.stopPropagation(e);
  L.DomEvent.preventDefault(e);
  toggle();
  leafletObjectMap.value?.invalidateSize;
}

function handleReady(leafletObject: any) {
  leafletObjectMap.value = leafletObject._map;
  //@ts-ignore
  mapContainerRef.value = leafletObjectMap.value.getContainer();
}
</script>
