<template><n-spin show /></template>
<script lang="ts" setup>
import { onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
const route = useRoute();
const router = useRouter();
onMounted(() => router.replace({ path: '/video/bridgeMonitoring', query: { ...route.query, tab: 'settings' } }));
</script>
