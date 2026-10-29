<template>
  <n-tooltip trigger="hover">
    <template #trigger>
      <n-button @click="toggle" v-bind="$attrs">
        <template #icon>
          <n-icon :size="iconSize">
            <ScreenToggleIcon></ScreenToggleIcon>
          </n-icon>
        </template>
      </n-button>
    </template>
    点击切换全屏
  </n-tooltip>
</template>
<script lang="ts" setup>
import { useFullscreen } from '@vueuse/core';
import { computed, ref, watch, watchEffect } from 'vue';
import Fullscreen from '~icons/mdi/fullscreen';
import FullscreenExit from '~icons/mdi/fullscreen-exit';
import { useAppStore } from '@/store';

const appStore = useAppStore();

const props = defineProps({
  target: {}, //需要全屏的区域
  iconSize: { type: String, default: '26px' },
});

const refTarget = ref();

watchEffect(() => {
  if (props.target) {
    refTarget.value = props.target;
  } else {
    refTarget.value = document.querySelector('body');
  }
});

const { isFullscreen, toggle } = useFullscreen(refTarget);
const ScreenToggleIcon = computed(() => {
  return isFullscreen.value ? FullscreenExit : Fullscreen;
});
watch(isFullscreen, (val) => {
  appStore.fullscreen = val;
});
</script>
