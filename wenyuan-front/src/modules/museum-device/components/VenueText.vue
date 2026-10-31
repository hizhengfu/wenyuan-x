<template>
  <span>{{ label }}</span>
</template>

<script setup lang="ts">
import { computed, onBeforeMount, ref } from 'vue';
import type { MuseumVenueVo } from '../typings';
import { loadVenueCatalog } from '../utils/venueCatalog';

const props = defineProps<{ value?: string | null }>();
const venues = ref<MuseumVenueVo[]>([]);
const label = computed(() => venues.value.find((item) => item.id === props.value)?.name || props.value || '--');

onBeforeMount(async () => {
  venues.value = await loadVenueCatalog();
});
</script>
