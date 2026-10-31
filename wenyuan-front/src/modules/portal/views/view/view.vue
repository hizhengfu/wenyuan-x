<template>
  <PortalView :portalId="portalId"></PortalView>
</template>
<script setup lang="ts">
import PortalView from '../../components/PortalView.vue';
import { useRoute } from 'vue-router';
import { computed } from 'vue';

const route = useRoute();

const portalId = computed(() => {
  return route.query.id as string;
});
</script>
