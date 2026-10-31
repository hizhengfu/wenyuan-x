<template><NSpace vertical :size="18"><NAlert type="info">核对法定行政文书、双方总账和档案交接方案后，由指定责任人本人填写意见。双方确认通过后，还须登记真实签署交接并办理过户。</NAlert><TransferEvidenceView :data="formData" /></NSpace></template>
<script setup lang="ts">
import TransferEvidenceView from '../components/TransferEvidenceView.vue';
defineProps<{formData:Record<string,any>}>();
</script>
