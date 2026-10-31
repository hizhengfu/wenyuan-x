<template>
 <NSpace vertical :size="18">
  <NDescriptions bordered :column="2">
   <NDescriptionsItem label="藏品">{{ data.collectionName || data.baseline?.formalName || data.collectionId }}</NDescriptionsItem>
   <NDescriptionsItem label="总登记号">{{ data.collectionTotalRegNo || '见原馆总账' }}</NDescriptionsItem>
   <NDescriptionsItem label="源馆">{{ partyName(data.fromOrgId || data.orgId) }}</NDescriptionsItem>
   <NDescriptionsItem label="接收馆">{{ partyName(data.toOrgId) }}</NDescriptionsItem>
   <NDescriptionsItem label="转移类别">{{ transferKindOptions.find(o=>o.value===data.dossier?.kind)?.label || '旧记录，待补齐新规方案' }}</NDescriptionsItem>
   <NDescriptionsItem label="逐件事由" :span="2" content-style="white-space: pre-wrap; overflow-wrap: anywhere;">{{ data.reason }}</NDescriptionsItem>
  </NDescriptions>
  <NText v-if="designationNotApplicable" depth="3">本件基线性质或权属不属于国家所有可移动文物，省级文物指定决定字段不适用；性质、权属有变更时仍须重新核实。</NText>
  <NAlert v-if="transportEvidenceIncomplete" type="warning" title="已执行案卷的包装运输材料待核实">
   本案已记账，但现存方案或交接记录缺少适用标准、承运能力或逐件包装核验原件。请核对当时实际材料并如实留存，不得补造历史签署；已交接状态不能证明包装运输材料齐备。
  </NAlert>
  <CollectionEvidenceReadView v-if="data.dossier" :fields="dossierFields" :data="data.dossier" :archive-files="stageFiles(data.execution ? 'EXECUTED' : 'SUBMITTED')" :original-url="data.id ? originalUrl : undefined" />
  <DeregistrationEvidenceList :archives="archives" case-type="transfer" :case-id="data.id" :administrative-evidence="data.administrativeEvidence" :notices="data.notices" />
  <template v-if="data.execution"><NText strong>实际交接签署记录</NText><CollectionEvidenceReadView :fields="transferExecutionFields" :data="data.execution" :archive-files="stageFiles('EXECUTED')" :original-url="data.id ? originalUrl : undefined" /></template>
 </NSpace>
</template>
<script setup lang="ts">
import { useCurrentImageLifecycle } from '@/utils/currentImage';
import type { CollectionReadonlyFile } from '../utils/readonlyFiles';
import {computed,onMounted,ref} from 'vue';
import { NAlert } from 'naive-ui';
import CollectionEvidenceReadView from './CollectionEvidenceReadView.vue';
import DeregistrationEvidenceList from './DeregistrationEvidenceList.vue';
import {transferApi} from '../api/transfer';
import {transferDossierFields,transferExecutionFields,transferKindOptions} from '../utils/transferEvidenceFields';
import {transferTransportEvidenceIncomplete} from '../utils/transferStatus';
const props=defineProps<{data:Record<string,any>}>();
const archives=ref<{id:string;phase:string;files?:CollectionReadonlyFile[]}[]>([]);
const stageFiles=(phase:string)=>archives.value.filter(archive=>archive.phase===phase).flatMap(archive=>archive.files || []);
let archiveEpoch=0, archiveAbort:AbortController|undefined;
function clearArchives(){archiveEpoch++;archiveAbort?.abort();archiveAbort=undefined;archives.value=[];}
const {eligible}=useCurrentImageLifecycle(()=>props.data,clearArchives,()=>{void readArchives();});
async function readArchives(){
  const id=props.data.id,epoch=archiveEpoch;
  if(!id || !eligible.value)return;
  archiveAbort=new AbortController();
  try { const result:any=await transferApi.archive(id,archiveAbort.signal);
    if(epoch!==archiveEpoch || !eligible.value || props.data.id!==id)return;
    if(Array.isArray(result.data))archives.value=result.data;
  } catch {if(epoch===archiveEpoch)archives.value=[];}
}
function originalUrl(fileId: string) {
  return `/collection/transfer/${encodeURIComponent(props.data.id || '')}/original/${encodeURIComponent(fileId)}`;
}
const parties=ref<{value:string;label:string}[]>([]),locations=ref<{value:string;label:string}[]>([]);
const partyName=(id:string)=>parties.value.find(p=>p.value===id)?.label||id;
const designationNotApplicable=computed(()=>props.data.dossier?.kind==='INSTITUTION_SUCCESSION' &&
  (props.data.dossier?.succession?.ownership==='NON_STATE' || props.data.baseline?.nature==='OTHER'));
const dossierFields=computed(()=>{
  const fields=transferDossierFields(props.data.dossier?.kind,props.data.baseline?.source,locations.value);
  return designationNotApplicable.value ? fields.filter(field=>!field.key.startsWith('succession.designation.')) : fields;
});
const transportEvidenceIncomplete=computed(()=>transferTransportEvidenceIncomplete(props.data));
onMounted(async()=>{const response:any=await transferApi.parties();parties.value=response.data||[];if(props.data.id){const places:any=await transferApi.receivingLocations(props.data.id);locations.value=places.data||[];}});
</script>
