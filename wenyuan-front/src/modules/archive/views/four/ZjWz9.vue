<template>
  <PageZjWz9 v-if="fourCulturalRecordId" :fourCulturalRecordId="fourCulturalRecordId"></PageZjWz9>
</template>
<script lang="ts" setup>
import PageZjWz9 from '../../components/four/page/ZjWz9.vue';
import { useRoute } from 'vue-router';
import { computed } from 'vue';

const route = useRoute();

const fourCulturalRecordId = computed(() => {
  return route.query.fourCulturalRecordId as string;
});
</script>
