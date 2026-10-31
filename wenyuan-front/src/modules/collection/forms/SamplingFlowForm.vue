<template>
  <NSpace vertical>
    <NAlert type="info">
      保护修复和保管人员须本人核对特殊需要、替代评估与最小干预方案。共同确认不代替真实行政批准，实际实施在有损取样页面办理。
    </NAlert>
    <NText>{{ formData.title }}</NText>
    <CollectionEvidenceReadView
      v-if="formData.plan"
      :fields="samplingFields.plan"
      :data="formData.plan"
      :archive-files="(formData.archives || []).filter((archive: any) => archive.phase === 'PLAN').flatMap((archive: any) => archive.files || [])" :original-url="phaseOriginalUrl('PLAN')"
    />
    <CollectionEvidenceReadView
      v-for="a in formData.administrative || []"
      :key="a.id"
      :fields="samplingFields.administrative"
      :data="a"
      :archive-files="(formData.archives || []).filter((archive: any) => archive.phase === `ADMIN_${a.id}`).flatMap((archive: any) => archive.files || [])" :original-url="phaseOriginalUrl(`ADMIN_${a.id}`)"
    />
  </NSpace>
</template>
<script setup lang="ts">
import CollectionEvidenceReadView from '../components/CollectionEvidenceReadView.vue';
import { samplingFields } from '../utils/samplingFields';

const props = defineProps<{ formData: Record<string, any> }>();
function phaseOriginalUrl(phase: string) {
  return (fileId: string): string => {
    if (!props.formData.id || !/^\d{15,20}$/.test(fileId)) return '';
    const archive = (props.formData.archives || []).find(
      (item: any) => item.phase === phase && (item.files || []).some((file: any) => file.fileId === fileId),
    );
    return archive
      ? `/collection/sampling/${encodeURIComponent(props.formData.id)}/original/${encodeURIComponent(archive.id)}/${encodeURIComponent(fileId)}`
      : '';
  };
}
</script>
