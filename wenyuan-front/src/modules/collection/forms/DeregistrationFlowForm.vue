<template>
  <NSpace vertical :size="16">
    <NAlert type="info">请核对逐件退出理由、全部专家独立意见、公示期限和行政文书原件。馆内确认不会直接退出，批准后仍须签署并执行。</NAlert>
    <NDescriptions bordered :column="1">
      <NDescriptionsItem label="藏品"><CollectionSelect :value="formData.collectionId" disabled /></NDescriptionsItem>
      <NDescriptionsItem label="法定退出情形"><HDictSelect :value="formData.reason" type="col_dereg_reason" disabled /></NDescriptionsItem>
      <NDescriptionsItem label="逐件退出理由">{{ formData.detail }}</NDescriptionsItem>
      <NDescriptionsItem label="方案形成日期" content-style="white-space: pre-wrap; overflow-wrap: anywhere;">{{ formatLocalDateValue(dossier.proposedOn) }}</NDescriptionsItem>
      <NDescriptionsItem label="处置去向">{{ destinations[dossier.destinationType] || dossier.destinationType }} {{ dossier.recipient }}</NDescriptionsItem>
      <NDescriptionsItem v-if="dossier.destinationType==='AUXILIARY_ACCOUNT'" label="辅助账编号及位置">{{ dossier.auxiliaryRecordNo }} / {{ dossier.auxiliaryLocation }}</NDescriptionsItem>
      <NDescriptionsItem label="处置方案原件"><CollectionCaseOriginalFiles case-type="deregistration" :archive-files="proposalFiles" :case-id="businessKey" :value="dossier.dispositionPlanFileIds" /></NDescriptionsItem>
      <NDescriptionsItem label="法律法规依据"><CollectionCaseOriginalFiles case-type="deregistration" :archive-files="proposalFiles" :case-id="businessKey" :value="dossier.legalBasisFileIds" /></NDescriptionsItem>
      <NDescriptionsItem label="接收单位国有性质依据"><CollectionCaseOriginalFiles case-type="deregistration" :archive-files="proposalFiles" :case-id="businessKey" :value="dossier.recipientStateEvidenceFileIds" /></NDescriptionsItem>
      <NDescriptionsItem label="保管部门负责人"><UserSelect :value="dossier.keeperUserId" disabled /></NDescriptionsItem>
      <NDescriptionsItem label="法定代表人或主要负责人"><UserSelect :value="dossier.leaderUserId" disabled /></NDescriptionsItem>
      <NDescriptionsItem label="其他原始资料"><CollectionCaseOriginalFiles case-type="deregistration" :archive-files="proposalFiles" :case-id="businessKey" :value="formData.attachmentIds" /></NDescriptionsItem>
    </NDescriptions>
    <DeregistrationEvidenceList :archives="archives" :case-id="businessKey" :experts="formData.experts" :notices="formData.notices" :administrative-evidence="formData.administrativeEvidence" />
  </NSpace>
</template>
<script setup lang="ts">
import { ref } from 'vue';
import { useCurrentImageLifecycle } from '@/utils/currentImage';
import { deregistrationApi } from '../api/deregistration';
import type { CollectionReadonlyFile } from '../utils/readonlyFiles';
import { computed } from 'vue';
import { formatLocalDateValue } from '@/utils';
import UserSelect from '@/modules/sys/components/form/field/UserSelect.vue';
import CollectionSelect from '../components/CollectionSelect.vue';
import DeregistrationEvidenceList from '../components/DeregistrationEvidenceList.vue';
import CollectionCaseOriginalFiles from '../components/CollectionCaseOriginalFiles.vue';
const props=defineProps<{formData:Record<string,any>; businessKey?: string}>();
const archives=ref<{id:string;phase:string;files?:CollectionReadonlyFile[]}[]>([]);
const proposalFiles=computed(() => archives.value.filter(archive=>archive.phase==='SUBMITTED').flatMap(archive=>archive.files || []));
let archiveEpoch=0, archiveAbort:AbortController|undefined;
function clearArchives(){ archiveEpoch++; archiveAbort?.abort(); archiveAbort=undefined; archives.value=[]; }
const {eligible}=useCurrentImageLifecycle(() => [props.businessKey, props.formData], clearArchives, () => { void readArchives(); });
async function readArchives(){
  const id=props.businessKey, epoch=archiveEpoch;
  if(!id || !eligible.value) return;
  archiveAbort=new AbortController();
  try {
    const result:any=await deregistrationApi.archive(id,archiveAbort.signal);
    if(epoch!==archiveEpoch || !eligible.value || props.businessKey!==id) return;
    if(Array.isArray(result.data)) archives.value=result.data;
  } catch { if(epoch===archiveEpoch) archives.value=[]; }
}
const dossier=computed(()=>props.formData.dossier || {});
const destinations:Record<string,string>={AUXILIARY_ACCOUNT:'纳入辅助账册',STATE_TEACHING_RESEARCH:'交国有教学及科研单位保管使用',STATE_MUSEUM:'国有馆间交换调拨',LOST:'不可抗力灭失',OTHER_LEGAL:'其他法律法规允许的处置'};
</script>
