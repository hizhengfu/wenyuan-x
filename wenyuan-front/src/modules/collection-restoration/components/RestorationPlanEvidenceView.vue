<template>
  <NSpace vertical>
    <NAlert v-if="sourceError" type="error">方案来源快照缺失或格式异常，请核对本版封存原件；不能据此判断当时资质或审批要求。</NAlert>
    <template v-else>
    <NText depth="3">以下为本版方案封存时的资料，不代表单位目前仍持有有效资质。</NText>
    <CollectionEvidenceReadView
      :fields="restorationPlanFields(usesRelicRules(source.catalog?.nature), !!planEvidence.replacementReason)"
      :data="planEvidence" :original-url="projectId ? originalUrl : undefined" :archive-files="archiveFiles"
    />
    <NDescriptions :column="1" bordered>
      <NDescriptionsItem label="方案对应的藏品">{{
        source.catalog?.formalName || source.catalog?.totalName
      }}</NDescriptionsItem>
      <NDescriptionsItem label="承担单位">{{ source.unitName || '未核实' }}</NDescriptionsItem>
      <NDescriptionsItem label="资质证书号与业务范围"
        >{{ source.qualification?.certNo || '未提供' }} ·
        {{ source.qualification?.scope || '未核实' }}</NDescriptionsItem
      >
      <NDescriptionsItem label="本版资质类别/技能等级">{{ qualificationLevelLabel(source.qualification?.certLevel) }}</NDescriptionsItem>
      <NDescriptionsItem label="本版证书持有方">{{ source.qualification?.holderType === 'ORG' ? '单位' : source.qualification?.holderType === 'PERSON' ? '个人' : '未核实' }} · {{ source.qualification?.holderId || '未核实编号' }}</NDescriptionsItem>
      <NDescriptionsItem label="本版发证日期">{{ formatDateValue(source.qualification?.issueDate) }}</NDescriptionsItem>
      <NDescriptionsItem label="本版到期日期">{{ source.qualification?.expireDate == null ? '未记载期限，须据原件核实' : formatDateValue(source.qualification.expireDate) }}</NDescriptionsItem>
      <NDescriptionsItem label="发证机关">{{ source.qualification?.issuer || '未核实' }}</NDescriptionsItem>
      <NDescriptionsItem label="原方案及资质原件">
        <template v-if="projectId">
          <CollectionCaseOriginalFiles
            v-for="file in sourceFiles"
            :key="file"
            :case-type="restrictedOriginals ? 'restoration-restricted' : 'restoration'"
            :case-id="projectId"
            :value="file" :archive-files="archiveFiles"
          />
        </template>
        <template v-else>
          <HFileList :value="sourceFiles" :revision="sourceSnapshot" />
        </template>
      </NDescriptionsItem>
    </NDescriptions>
    </template>
    <DeregistrationEvidenceList :case-type="restrictedOriginals ? 'restoration-restricted' : 'restoration'" :case-id="projectId" :administrative-evidence="administrativeEvidence || []" :archives="archives" />
  </NSpace>
</template>
<script setup lang="ts">
import { usesRelicRules } from '@/modules/collection/utils/collectionNature';
import { computed } from 'vue';
import { NAlert, NText } from 'naive-ui';
import { formatDateValue } from '@/utils/datetime';
import { qualificationLevelLabel } from '../utils/qualificationLevels';
import CollectionEvidenceReadView from '@/modules/collection/components/CollectionEvidenceReadView.vue';
import CollectionCaseOriginalFiles from '@/modules/collection/components/CollectionCaseOriginalFiles.vue';
import DeregistrationEvidenceList from '@/modules/collection/components/DeregistrationEvidenceList.vue';
import { restorationPlanFields } from '../utils/restorationComplianceFields';
const props = defineProps<{
  projectId?: string;
  restrictedOriginals?: boolean;
  planEvidence: Record<string, any>;
  sourceSnapshot?: string;
  administrativeEvidence?: any[];
  archiveFiles?: any[];
  archives?: { id: string; phase: string; files?: any[] }[];
}>();
function originalUrl(fileId: string) {
  const root = '/collection/restoration/' + encodeURIComponent(props.projectId || '');
  return root + (props.restrictedOriginals ? '/restricted/original/' : '/original/') + encodeURIComponent(fileId);
}
const record = (value: unknown): value is Record<string, any> => !!value && typeof value === 'object' && !Array.isArray(value);
const parsedSource = computed(() => {
  try {
    const value: unknown = JSON.parse(props.sourceSnapshot || 'null');
    if (!record(value) || !record(value.catalog) || (value.qualification != null && !record(value.qualification))) return null;
    return value;
  } catch {
    return null;
  }
});
const sourceError = computed(() => parsedSource.value === null);
const source = computed(() => parsedSource.value || {});
const sourceFiles = computed(() => [source.value.planFileIds, source.value.beforeImages, source.value.qualification?.attachmentFileId].filter(Boolean));
</script>
