<template>
  <KnowledgeGraph :nodeId="$route.query.nodeId as string"></KnowledgeGraph>
</template>
<script lang="ts" setup>
import { useRoute } from 'vue-router';

const $route = useRoute();
import KnowledgeGraph from '../components/graph.vue'
</script>
