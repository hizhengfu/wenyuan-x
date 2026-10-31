<template>
 <HPageForm content-only :enable-cancel="false" v-model:show="show" title="捐赠鼓励记录">
  <NSpace vertical>
      <CollectionArchiveIssues :data="rows" />
   <NAlert type="info">记录已实际给予的精神鼓励；确有必要的物质奖励另附依据和领取凭证。更正会保留原记录。没有记录不代表已经给予鼓励。</NAlert>
   <NSpace><NButton :disabled="loading" @click="edit('SPIRITUAL')">登记精神鼓励</NButton><NButton :disabled="loading" @click="edit('MATERIAL')">登记物质奖励</NButton></NSpace>
   <NSpin :show="loading"><NEmpty v-if="!loading && !rows.length" description="尚无鼓励记录" />
    <NCard v-for="row in rows" :key="row.id" :title="row.content.kind==='SPIRITUAL'?'精神鼓励':'物质奖励'">
     <CollectionEvidenceReadView :fields="fieldsFor(row.content.kind)" :data="row.content" :archive-files="row.files" :original-url="fileId => originalUrl(row.id, fileId)" />
     <NText v-if="row.content.correctsArchiveId">更正原记录：{{row.content.correctsArchiveId}}；原因：{{row.content.correctionReason}}</NText>
     <NText v-if="corrected.has(row.id)">已有后续更正，原记录永久保留。</NText>
     <NButton v-else text @click="edit(row.content.kind,row)">追加更正</NButton>
    </NCard>
   </NSpin>
  </NSpace>
 </HPageForm>
 <CollectionEvidenceForm ref="formRef" title="登记捐赠鼓励事实" :fields="fields" :save="saveRecord" />
</template>
<script setup lang="ts">
import CollectionArchiveIssues from './CollectionArchiveIssues.vue';
import type { CollectionReadonlyFile } from '../utils/readonlyFiles';
import {computed,onBeforeUnmount,onDeactivated,ref,watch,nextTick} from 'vue';
import request from '@/utils/request';
import {formatLocalDateValue} from '@/utils';
import type {EvidenceField} from '../utils/evidenceFields';
import CollectionEvidenceForm from './CollectionEvidenceForm.vue';
import CollectionEvidenceReadView from './CollectionEvidenceReadView.vue';
type Kind='SPIRITUAL'|'MATERIAL';
interface Row {id:string;content:Record<string,any>;files?:CollectionReadonlyFile[]}
const show=ref(false),loading=ref(false),rows=ref<Row[]>([]),formRef=ref<any>(),fields=ref<EvidenceField[]>([]);
let generation=0, acquisitionId='',editing:{id:string;generation:number;kind:Kind;requestId:string;correctsArchiveId?:string}|null=null;
const corrected=computed(()=>new Set(rows.value.map(r=>r.content.correctsArchiveId).filter(Boolean)));
function originalUrl(archiveId:string,fileId:string){return `/collection/acquisition/${encodeURIComponent(acquisitionId)}/original/${encodeURIComponent(archiveId)}/${encodeURIComponent(fileId)}`;}
function fieldsFor(kind:Kind):EvidenceField[]{return [
 {key:'recipient',label:'鼓励对象',required:true},{key:'occurredOn',label:'实际给予日期',type:'date',required:true},
 {key:'description',label:'实际鼓励内容',type:'textarea',required:true},{key:'evidenceFileIds',label:'颁发或实施原件',type:'file',required:true},
 ...(kind==='MATERIAL'?[
 {key:'necessity',label:'奖励必要性',type:'textarea',required:true},{key:'materialDescription',label:'奖励内容（含非现金）',type:'textarea',required:true},
 {key:'amountCents',label:'现金金额（元，无现金可留空）',type:'number',unit:'yuan'},{key:'approvalFileIds',label:'奖励批准依据',type:'file',required:true},{key:'receiptFileIds',label:'实际领取凭证',type:'file',required:true}
 ] as EvidenceField[]:[])];}
function invalidate(){++generation;editing=null;formRef.value?.close();rows.value=[];loading.value=false;}
watch(show,v=>{if(!v)invalidate();},{flush:'sync'});
onBeforeUnmount(invalidate);
onDeactivated(()=>{show.value=false;invalidate();});
async function load(id:string,ticket:number){
 loading.value=true;
 try{const r:any=await request({url:`/collection/acquisition/${id}/recognitions`,method:'get'});if(ticket!==generation || !show.value)return;if(!Array.isArray(r.data))throw new Error('鼓励记录格式异常');rows.value=r.data;}
 catch(error){if(ticket===generation && show.value)throw error;}
 finally{if(ticket===generation)loading.value=false;}
}
async function open(id:string){invalidate();acquisitionId=id;show.value=true;await load(id,generation);}
async function edit(kind:Kind,row?:Row){
 if(loading.value || !show.value)return;
 const ticket=generation;
 const context={id:acquisitionId,generation:ticket,kind,requestId:crypto.randomUUID(),correctsArchiveId:row?.id};editing=context;
 fields.value=[...fieldsFor(kind),...(row?[{key:'correctionReason',label:'更正原因',type:'textarea',required:true} as EvidenceField]:[])];
 await nextTick();if(ticket!==generation || editing!==context || !show.value)return;
 const initial={...row?.content};delete initial.requestId;delete initial.correctsArchiveId;delete initial.correctionReason;
 if(initial.occurredOn)initial.occurredOn=new Date(`${initial.occurredOn}T00:00:00`).getTime();
 formRef.value?.open(initial);
}
async function saveRecord(value:Record<string,any>){
 const context=editing;if(!context || context.generation!==generation || !show.value)return false;
 const data={...value,requestId:context.requestId,kind:context.kind,correctsArchiveId:context.correctsArchiveId,occurredOn:formatLocalDateValue(value.occurredOn)};
 await request({url:`/collection/acquisition/${context.id}/recognitions`,method:'post',data});
 if(context.generation!==generation || !show.value)return false;
 await load(context.id,context.generation);
}
defineExpose({open,close:()=>{show.value=false;invalidate();}});
</script>
