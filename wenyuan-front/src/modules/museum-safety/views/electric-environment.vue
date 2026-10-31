<template>
  <DomainMonitor
    title="用电与建筑环境"
    profile="ELECTRIC"
    :tabs="tabs"
  />
</template>

<script setup lang="ts">
import DomainMonitor from '../components/DomainMonitor.vue';
const tabs = [
  { label: '用电安全', domain: 'ELECTRICITY' as const },
  { label: '建筑运行环境', domain: 'BUILDING_ENVIRONMENT' as const },
];
</script>
