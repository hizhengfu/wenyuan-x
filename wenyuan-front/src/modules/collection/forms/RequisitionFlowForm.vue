<template>
  <NSpace vertical :size="18"
    ><NAlert type="info"
      >请核对完整藏品清单、用途、安全方案和出归库时间，由本单指定的提用部门负责人、保管负责人、主要负责人依次本人填写意见。批准后仍须完成逐件点交及双方签署，方能执行出库。</NAlert
    ><RequisitionEvidenceView :data="formData"
  /></NSpace>
</template>
<script setup lang="ts">
import RequisitionEvidenceView from '../components/RequisitionEvidenceView.vue';
defineProps<{ formData: Record<string, any> }>();
</script>
