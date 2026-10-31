<template>
  <DomainMonitor
    title="消防安防"
    profile="INCIDENT"
    :tabs="tabs"
  />
</template>

<script setup lang="ts">
import DomainMonitor from '../components/DomainMonitor.vue';
const tabs = [
  { label: '消防', domain: 'FIRE' as const },
  { label: '安防', domain: 'SECURITY' as const },
];
</script>
