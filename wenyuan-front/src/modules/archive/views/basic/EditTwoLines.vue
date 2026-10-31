<template>
  <EditTwoLines v-if="basicId" :basicId="basicId"></EditTwoLines>
</template>
<script lang="ts" setup>
import EditTwoLines from '../../components/basic/EditTwoLines.vue'
import { useRoute } from 'vue-router';
import { computed } from 'vue';

const route = useRoute();

const basicId = computed(() => {
  return route.query.basicId as string;
});
</script>
