<script setup lang="ts">
import Three3dTilesLoader from './3dTilesLoader.vue';
import ThreeGltfLoader from './GltfLoader.vue';
import { computed } from 'vue';

const props = defineProps({
  path: { type: String, required: true },
});

const modelLoad = computed(() => {
  const { path } = props;

  if (/\.json/.test(path)) {
    return Three3dTilesLoader;
  } else {
    return ThreeGltfLoader;
  }
});
</script>

<template>
  <component v-if="path" :is="modelLoad" :path="path"></component>
</template>
