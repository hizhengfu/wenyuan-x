<template>
  <SafetyPageGuard><SafetyDocumentManager title="安全制度" :allowed-types="['SAFETY_POLICY']" /></SafetyPageGuard>
</template>
<script setup lang="ts">
import SafetyDocumentManager from '../components/SafetyDocumentManager.vue';
import SafetyPageGuard from '../components/SafetyPageGuard.vue';
</script>
