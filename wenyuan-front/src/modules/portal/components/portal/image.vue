<template>
  <n-image v-if="url" @load="handleLoad" :src="url" />
</template>
<script setup lang="ts">
const props = defineProps({
  url: { type: String },
});
const emits = defineEmits(['load']);

function handleLoad() {
  emits('load');
}
</script>
