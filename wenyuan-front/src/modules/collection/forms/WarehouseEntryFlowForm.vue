<template><WarehouseEntryEvidenceView :data="formData" /></template>
<script setup lang="ts">
import WarehouseEntryEvidenceView from '../components/WarehouseEntryEvidenceView.vue';
defineProps<{ formData: Record<string, any> }>();
</script>
