<template>
 <HPageForm content-only :enable-cancel="false" v-model:show="show" title="征集专项档案及法定材料">
  <NSpace vertical :size="18">
      <CollectionApprovalProgress v-if="show && record.acquisition?.id" :business-id="record.acquisition?.id" business-type="COLLECTION_ACQUISITION" :fetch-flow="acquisitionApi.flow" :revision="record" />
      <CollectionArchiveIssues :data="record" />
   <NAlert v-if="record.vetoOn" type="error">有专家不同意征集，本次程序已终止。原始评估意见永久保留。</NAlert>
   <NAlert v-else type="info">{{ record.acquisition?.name }}：先保存调查依据，再登记每名专家的独立意见，最后登记估价、集体决策、协议及实际取得材料。登记第一份独立意见后，征集对象和调查资料不能覆盖。</NAlert>
   <NAlert v-if="record.entryDeadline" :type="record.acquisition?.collectionId?'success':'warning'">实际取得后三个月内应办理入藏。本单期限：{{ formatLocalDateValue(record.entryDeadline) }}。{{ record.acquisition?.collectionId?'已办理入馆。':'请及时完成入馆凭证；逾期仍须补办并记录原因。' }}</NAlert>
   <NSpace v-if="collecting">
    <NButton v-if="!record.experts?.length && !record.completion" @click="edit('dossier')">登记入藏标准和来源调查</NButton>
    <NButton v-if="record.dossier && !record.completion" @click="edit('expert')">登记一名专家独立意见</NButton>
    <NButton v-if="record.dossier && !record.completion" @click="edit('completion')">登记估价、协议和实际取得</NButton>
    <NButton v-if="record.completion" type="primary" @click="submit">材料齐备，提交征集确认</NButton>
   </NSpace>
   <NSpace v-if="record.acquisition">
    <NButton v-if="record.acquisition?.source==='DONATE' && record.completion" @click="recognitionRef?.open(record.acquisition.id)">捐赠鼓励记录</NButton>
    <NButton :disabled="!record.experts?.length" :loading="printingExperts" @click="printExperts">打印专家鉴定意见表</NButton>
   </NSpace>
   <NText v-if="record.expertAveragePriceCents!==null && record.expertAveragePriceCents!==undefined">专家独立估价均值：{{ formatYuan(record.expertAveragePriceCents) }}。请结合本馆同类藏品和市场成交参照形成保密估价建议。</NText>
   <AcquisitionEvidenceView :source="record.acquisition?.source" :dossier="record.dossier" :experts="record.experts" :completion="record.completion" :archives="record.archives" :original-url="archivedOriginalUrl" />
   <NDivider>永久归档原件</NDivider>
   <NTimeline v-if="record.archives?.length">
    <NTimelineItem v-for="archive in record.archives" :key="archive.id" :title="archivePhase(archive.phase)" :time="formatLocalDateValue(archive.archivedAt,'YYYY-MM-DD HH:mm:ss')">
     <NSpace v-if="archive.files?.length" :size="8">
      <HFileList :files="archive.files" :original-url="(fileId: string) => archiveOriginalUrl(record.acquisition.id,archive.id,fileId)" :revision="[archive?.id, archive?.digest]" show-download-btn />
     </NSpace>
     <NText v-else depth="3">该阶段无附件</NText>
    </NTimelineItem>
   </NTimeline>
   <NEmpty v-else description="暂无永久归档阶段" />
  </NSpace>
 </HPageForm>
 <DonationRecognitionDrawer ref="recognitionRef" />
 <CollectionEvidenceForm ref="formRef" :title="title" :fields="fields" :save="save" @saved="reload" />
</template>
<script setup lang="ts">
import CollectionApprovalProgress from './CollectionApprovalProgress.vue';
import CollectionArchiveIssues from './CollectionArchiveIssues.vue';
import {computed,nextTick,ref,watch,onBeforeUnmount,onDeactivated} from 'vue';
import {useMessage} from 'naive-ui';
import {formatLocalDateValue} from '@/utils';
import {useAuthStore} from '@/store/modules/auth';
import {acquisitionApi} from '../api/acquisition';
import {acquisitionComplianceApi} from '../api/acquisitionCompliance';
import {acquisitionDossierFields,acquisitionExpertFields,acquisitionCompletionFields,acquisitionEvidencePayload} from '../utils/acquisitionEvidenceFields';
import { formatYuan } from '../utils/moneyYuan';
import CollectionEvidenceForm from './CollectionEvidenceForm.vue';
import AcquisitionEvidenceView from './AcquisitionEvidenceView.vue';
import DonationRecognitionDrawer from './DonationRecognitionDrawer.vue';
import {printAcquisitionExpertOpinion} from '../utils/acquisitionExpertOpinionPrint';
const recognitionRef=ref<InstanceType<typeof DonationRecognitionDrawer>>();
const emit=defineEmits<{(e:'saved'):void}>(),message=useMessage();
const show=ref(false),record=ref<Record<string,any>>({}),formRef=ref<any>(),kind=ref<'dossier'|'expert'|'completion'>('dossier');
const auth=useAuthStore();
const printingExperts=ref(false);
const collecting=computed(()=>auth.hasPermission('COLLECTION_ADMIN,COLLECTION_ACQUIRE') && record.value.acquisition?.flowStatus==='DRAFT' && !record.value.acquisition?.flowInstanceId && !record.value.vetoOn);
const archivePhase=(phase:string)=>({PROPOSAL:'征集调查与来源依据',AGREEMENT:'估价决策、协议与实际取得',SUBMITTED:'提交审批',ENTERED:'办理入馆',VETO:'专家否决原件'} as Record<string,string>)[phase] || (phase.startsWith('EXPERT_')?'专家独立意见':phase.startsWith('APPROVED_')?'审批通过':phase.startsWith('FILE_ACCESS_')?'历史原件身份留存':phase.startsWith('DONOR_RECOGNITION_')?'捐赠鼓励记录':phase);
const archiveOriginalUrl=(id:string,archiveId:string,fileId:string)=>`/collection/acquisition/${encodeURIComponent(id)}/original/${encodeURIComponent(archiveId)}/${encodeURIComponent(fileId)}`;
const archivedOriginalUrl=(phase:string,fileId:string):string=>{
 const id=record.value.acquisition?.id;
 const archive=record.value.archives?.find((a:any)=>a.phase===phase && a.files?.some((f:any)=>f.fileId===fileId));
 return id && archive ? archiveOriginalUrl(id,archive.id,fileId) : '';
};
const title=computed(()=>({dossier:'登记征集调查及回避依据',expert:'登记逐件独立鉴定评估',completion:'归档估价决策、协议及实际取得'})[kind.value]);
const fields=computed(()=>kind.value==='dossier'?acquisitionDossierFields(record.value.acquisition?.source==='PURCHASE'):kind.value==='expert'?acquisitionExpertFields(record.value.acquisition?.source==='PURCHASE'):acquisitionCompletionFields(record.value.acquisition?.source));
let generation=0;
let editing:{id:string;kind:typeof kind.value;generation:number}|null=null;
function invalidate(){++generation;editing=null;formRef.value?.close();recognitionRef.value?.close();}
watch(show,value=>{if(!value)invalidate();},{flush:'sync'});
onBeforeUnmount(invalidate);
onDeactivated(()=>{show.value=false;invalidate();record.value={};});
async function load(id:string,ticket:number){
 try {
 const response:any=await acquisitionComplianceApi.get(id);
 if(ticket!==generation || !show.value)return;
 if(response?.data?.acquisition?.id!==id)throw new Error('征集材料与当前申请不一致，请重新读取');
 record.value=response.data;
 } catch(error){if(ticket===generation && show.value)throw error;}
}
async function open(id:string){invalidate();record.value={};show.value=true;await load(id,generation);}
async function reload(){const context=editing;if(!context || context.generation!==generation || !show.value)return;await load(context.id,context.generation);if(context.generation===generation && show.value)emit('saved');}
async function edit(value:typeof kind.value){
 const id=record.value.acquisition?.id;if(!id || !show.value)return;
 const context={id,kind:value,generation};editing=context;kind.value=value;
 const initial=value==='dossier'?{...record.value.dossier,lawfulSource:record.value.dossier?.lawfulSource?'YES':undefined}:{};
 await nextTick();if(context!==editing || context.generation!==generation || !show.value)return;
 formRef.value?.open(initial);
}
async function save(value:Record<string,any>){
 const context=editing;if(!context || context.generation!==generation || !show.value)return false;
 await acquisitionComplianceApi[context.kind](context.id,acquisitionEvidencePayload(value));
 if(context.generation!==generation || context!==editing || !show.value)return false;
 message.success('原始资料已登记');
}
async function submit(){
 const id=record.value.acquisition?.id,ticket=generation;if(!id || !show.value)return;
 await acquisitionApi.submit(id);
 if(ticket!==generation || !show.value)return;
 message.success('征集已提交确认');await load(id,ticket);if(ticket===generation && show.value)emit('saved');
}
async function printExperts(){
 const id=record.value.acquisition?.id;if(!id || printingExperts.value)return;
 printingExperts.value=true;
 try {
  let data=record.value;
  if(!Array.isArray(data.experts)){
   const response:any=await acquisitionComplianceApi.get(id);
   if(response?.data?.acquisition?.id!==id)throw new Error('征集材料与当前申请不一致，请重新读取');
   data=response.data;
  }
  if(!data.experts?.length){message.warning('暂无专家独立意见');return;}
  let flowHistory:any[]=[];let flowLoadFailed=false;
  try{const flow:any=await acquisitionApi.flow(id);flowHistory=Array.isArray(flow?.data?.history)?flow.data.history:[];}catch{flowHistory=[];flowLoadFailed=true;}
  if(flowLoadFailed)message.warning('流程记录读取失败，鉴定委员会意见和结论已留空');
  await printAcquisitionExpertOpinion(data,{flowHistory,flowLoadFailed});
 } catch(error:any){message.error(error?.message || '专家鉴定意见表生成失败');}
 finally{printingExperts.value=false;}
}
defineExpose({open});
</script>
