<template>
  <NSpace vertical
    ><NAlert type="info"
      >请核对实际集体研究、签署协议、逐件风险和行政原件，由指定本人确认。馆内审批不能代替行政批准；批准后仍须由双方各不少于两人实际点交。</NAlert
    ><LoanEvidenceView :data="formData"
  /></NSpace>
</template>
<script setup lang="ts">
import LoanEvidenceView from '../components/LoanEvidenceView.vue';
defineProps<{ formData: Record<string, any> }>();
</script>
