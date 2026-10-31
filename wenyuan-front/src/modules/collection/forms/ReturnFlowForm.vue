<template>
  <NSpace vertical
    ><NAlert type="info"
      >请核对双方真实签署、每件真实性及保存现状，以及异常件的保管措施。确认后由经办人登记实际接收，异常调查和外部报告须另外办理。</NAlert
    ><ReturnEvidenceView :data="formData"
  /></NSpace>
</template>
<script setup lang="ts">
import ReturnEvidenceView from '../components/ReturnEvidenceView.vue';
defineProps<{ formData: Record<string, any> }>();
</script>
