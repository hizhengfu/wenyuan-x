<template>
  <n-image-group show-toolbar-tooltip>
    <n-space>
      <n-image v-for="item in data" :src="item" />
    </n-space>
  </n-image-group>
</template>
<script setup lang="ts">
const props = defineProps({
  data: { type: Array as () => Array<string> },
});
const emits = defineEmits(['load']);

function handleLoad() {
  emits('load');
}
</script>
